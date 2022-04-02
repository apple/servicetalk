/*
 * Copyright © 2018, 2021 Apple Inc. and the ServiceTalk project authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.servicetalk.http.netty;

import io.servicetalk.buffer.api.Buffer;
import io.servicetalk.concurrent.Cancellable;
import io.servicetalk.concurrent.CompletableSource;
import io.servicetalk.concurrent.PublisherSource.Subscriber;
import io.servicetalk.concurrent.PublisherSource.Subscription;
import io.servicetalk.concurrent.SingleSource;
import io.servicetalk.concurrent.api.AsyncCloseable;
import io.servicetalk.concurrent.api.Completable;
import io.servicetalk.concurrent.api.CompositeCloseable;
import io.servicetalk.concurrent.api.Publisher;
import io.servicetalk.concurrent.api.Single;
import io.servicetalk.http.api.HttpExecutionStrategies;
import io.servicetalk.http.api.HttpExecutionStrategy;
import io.servicetalk.http.api.HttpServerBuilder;
import io.servicetalk.http.api.HttpServerContext;
import io.servicetalk.http.api.HttpServiceContext;
import io.servicetalk.http.api.ReservedStreamingHttpConnection;
import io.servicetalk.http.api.SingleAddressHttpClientBuilder;
import io.servicetalk.http.api.StreamingHttpClient;
import io.servicetalk.http.api.StreamingHttpConnection;
import io.servicetalk.http.api.StreamingHttpRequest;
import io.servicetalk.http.api.StreamingHttpResponse;
import io.servicetalk.http.api.StreamingHttpResponseFactory;
import io.servicetalk.http.api.StreamingHttpService;
import io.servicetalk.http.api.StreamingHttpServiceFilter;
import io.servicetalk.http.api.StreamingHttpServiceFilterFactory;
import io.servicetalk.transport.api.HostAndPort;
import io.servicetalk.transport.api.IoThreadFactory;
import io.servicetalk.transport.netty.internal.ExecutionContextExtension;
import io.servicetalk.transport.netty.internal.NettyIoThreadFactory;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.net.InetSocketAddress;
import java.util.Collection;
import java.util.Objects;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;
import java.util.function.Predicate;
import java.util.function.UnaryOperator;
import java.util.stream.Stream;
import javax.annotation.Nullable;

import static io.servicetalk.concurrent.api.AsyncCloseables.newCompositeCloseable;
import static io.servicetalk.concurrent.api.BlockingTestUtils.awaitIndefinitelyNonNull;
import static io.servicetalk.concurrent.api.Publisher.from;
import static io.servicetalk.concurrent.api.Single.succeeded;
import static io.servicetalk.concurrent.api.SourceAdapters.toSource;
import static io.servicetalk.http.api.HttpEventKey.MAX_CONCURRENCY;
import static io.servicetalk.http.api.HttpExecutionStrategies.customStrategyBuilder;
import static io.servicetalk.http.api.HttpExecutionStrategies.defaultStrategy;
import static io.servicetalk.http.api.HttpExecutionStrategies.offloadAll;
import static io.servicetalk.http.api.HttpExecutionStrategies.offloadNone;
import static io.servicetalk.http.api.HttpResponseStatus.OK;
import static io.servicetalk.http.netty.HttpClients.forSingleAddress;
import static io.servicetalk.http.netty.HttpServers.forAddress;
import static io.servicetalk.test.resources.TestUtils.assertNoAsyncErrors;
import static io.servicetalk.transport.api.IoThreadFactory.IoThread.currentThreadIsIoThread;
import static io.servicetalk.transport.netty.internal.AddressUtils.localAddress;
import static io.servicetalk.transport.netty.internal.AddressUtils.serverHostAndPort;
import static io.servicetalk.utils.internal.PlatformDependent.throwException;
import static java.lang.Long.MAX_VALUE;
import static java.lang.Thread.currentThread;

class HttpOffloadingTest {
    private static final HttpExecutionStrategy ALL_BUT_CLOSE_OFFLOAD = HttpExecutionStrategies.customStrategyBuilder()
            .offloadReceiveMetadata().offloadReceiveData().offloadSend().offloadEvent().build();
    private static final HttpExecutionStrategy OFFLOAD_CLOSE = HttpExecutionStrategies.customStrategyBuilder()
            .offloadClose().build();

    private static final String IO_EXECUTOR_NAME_PREFIX = "io-executor";

    @RegisterExtension
    static final ExecutionContextExtension CLIENT_CTX =
            ExecutionContextExtension.cached(new NettyIoThreadFactory(IO_EXECUTOR_NAME_PREFIX))
                    .setClassLevel(true);
    @RegisterExtension
    static final ExecutionContextExtension SERVER_CTX =
            ExecutionContextExtension.cached(new NettyIoThreadFactory(IO_EXECUTOR_NAME_PREFIX))
                    .setClassLevel(true);
    private final Queue<Throwable> errors = new ConcurrentLinkedQueue<>();
    private final CountDownLatch terminated = new CountDownLatch(1);
    @Nullable
    private StreamingHttpConnection httpConnection;
    @Nullable
    private HttpServerContext serverContext;
    @Nullable
    private OffloadingVerifyingServiceStreaming service;
    @Nullable
    private StreamingHttpClient client;

    @Nullable
    private Predicate<Thread> wrongServerPublishThread;
    @Nullable
    private Predicate<Thread> wrongClientPublishThread;

    void setup() throws Exception {
        setup(true);
    }

    void setup(boolean offloadClose) throws Exception {
        HttpExecutionStrategy serverStrategy = offloadClose ? defaultStrategy() : ALL_BUT_CLOSE_OFFLOAD;
        HttpExecutionStrategy clientStrategy = offloadClose ? defaultStrategy() : ALL_BUT_CLOSE_OFFLOAD;
        setup(offloadAll(), offloadAll(), serverStrategy, offloadClose ? OFFLOAD_CLOSE : offloadNone(),
                clientStrategy, offloadClose ? OFFLOAD_CLOSE : offloadNone(),
                hsb -> hsb, sahcb -> sahcb);
    }

    void setup(final HttpExecutionStrategy serviceStrategy, final HttpExecutionStrategy expectedServerStrategy,
               final HttpExecutionStrategy serverStrategy, final HttpExecutionStrategy serverOffload,
               final HttpExecutionStrategy clientStrategy, final HttpExecutionStrategy clientOffload,
               UnaryOperator<HttpServerBuilder> serverInitializer,
               UnaryOperator<SingleAddressHttpClientBuilder<HostAndPort, InetSocketAddress>> clientInitializer)
            throws Exception {
        Thread testThread = Thread.currentThread();
        wrongServerPublishThread = checkThread(serverStrategy, serverOffload, testThread);
        wrongClientPublishThread = checkThread(clientStrategy, clientOffload, testThread);

        service = new OffloadingVerifyingServiceStreaming(serviceStrategy, expectedServerStrategy);
        serverContext = serverInitializer.apply(forAddress(localAddress(0))
                        .ioExecutor(SERVER_CTX.ioExecutor())
                        .executor(SERVER_CTX.executor())
                        .executionStrategy(serverStrategy))
                .listenStreamingAndAwait(service);

        client = clientInitializer.apply(forSingleAddress(serverHostAndPort(serverContext))
                        .ioExecutor(CLIENT_CTX.ioExecutor())
                        .executor(CLIENT_CTX.executor())
                        .executionStrategy(clientStrategy))
                .buildStreaming();
        httpConnection = awaitIndefinitelyNonNull(client.reserveConnection(client.get("/")));
    }

    private Predicate<Thread> checkThread(final HttpExecutionStrategy strategy,
                                          final HttpExecutionStrategy offloads,
                                          final Thread testThread) {
        return offloads.hasOffloads() ?
                strategy.missing(offloads).hasOffloads() ?
                        (Thread thread) -> testThread != thread && !IoThreadFactory.IoThread.isIoThread(thread) :
                        IoThreadFactory.IoThread::isIoThread :
                t -> false; // don't care what thread is used.
    }

    @AfterEach
    void afterTest() throws Exception {
        CompositeCloseable closeables = newCompositeCloseable();
        Stream.of(httpConnection, client, serverContext)
                .filter(Objects::nonNull)
                .map(AsyncCloseable.class::cast)
                .forEach(closeables::append);
        closeables.close();
    }

    @Test
    void requestResponseIsOffloaded() throws Exception {
        setup();
        final Publisher<Buffer> reqPayload =
                from(httpConnection.connectionContext().executionContext().bufferAllocator()
                        .fromAscii("Hello"))
                        .beforeRequest(n -> {
                            if (wrongClientPublishThread.test(currentThread())) {
                                errors.add(new AssertionError(
                                        "Server response: request-n has incorrect offloading. Thread: "
                                        + currentThread().getName()));
                            }
                        });
        final SingleSource<StreamingHttpResponse> resp = toSource(httpConnection.request(
                httpConnection.post("/").payloadBody(reqPayload)));
        responseSubscribe(resp);
        terminated.await();
        assertNoAsyncErrors("Unexpected client errors.", errors);
        assertNoAsyncErrors("Unexpected server errors.", service.errors);
    }

    @Test
    void reserveConnectionIsOffloaded() throws Exception {
        setup();
        toSource(client.reserveConnection(client.get("/")).afterFinally(terminated::countDown))
                .subscribe(new SingleSource.Subscriber<ReservedStreamingHttpConnection>() {
                    @Override
                    public void onSubscribe(final Cancellable cancellable) {
                        if (wrongClientPublishThread.test(currentThread())) {
                            errors.add(new AssertionError("onSubscribe has incorrect offloading. Thread: "
                                    + currentThread().getName()));
                        }
                    }

                    @Override
                    public void onSuccess(@Nullable final ReservedStreamingHttpConnection result) {
                        if (result == null) {
                            errors.add(new AssertionError("Reserved connection is null."));
                            return;
                        }
                        if (wrongClientPublishThread.test(currentThread())) {
                            errors.add(new AssertionError("onSuccess has incorrect offloading. Thread: "
                                    + currentThread().getName()));
                        }
                    }

                    @Override
                    public void onError(final Throwable t) {
                        if (wrongClientPublishThread.test(currentThread())) {
                            errors.add(new AssertionError("onError has incorrect offloading. Thread: "
                                    + currentThread().getName()));
                        }
                        errors.add(new AssertionError("Unexpected error.", t));
                    }
                });
        terminated.await();
        assertNoAsyncErrors(errors);
    }

    @ParameterizedTest(name = "offloadClose={0}")
    @ValueSource(booleans = {false, true})
    void serverCloseAsyncIsOffloaded(boolean offloadClose) throws Exception {
        setup(offloadClose);
        subscribeTo(serverContext.closeAsync());
        terminated.await();
        assertNoAsyncErrors(errors);
    }

    @ParameterizedTest(name = "offloadClose={0}")
    @ValueSource(booleans = {false, true})
    void serverCloseAsyncGracefullyIsOffloaded(boolean offloadClose) throws Exception {
        setup(offloadClose);
        subscribeTo(serverContext.closeAsyncGracefully());
        terminated.await();
        assertNoAsyncErrors(errors);
    }

    @ParameterizedTest(name = "offloadClose={0}")
    @ValueSource(booleans = {false, true})
    void serverOnCloseIsOffloaded(boolean offloadClose) throws Exception {
        setup(offloadClose);
        subscribeTo(serverContext.onClose());
        serverContext.closeAsync().toFuture().get();
        terminated.await();
        assertNoAsyncErrors(errors);
    }

    @Test
    void clientSettingsStreamIsOffloaded() throws Exception {
        setup();
        subscribeTo(errors,
                httpConnection.transportEventStream(MAX_CONCURRENCY).afterFinally(terminated::countDown),
                "Client settings stream: ");
        httpConnection.closeAsyncGracefully().toFuture().get();
        terminated.await();
        assertNoAsyncErrors(errors);
    }

    @ParameterizedTest(name = "offloadClose={0}")
    @ValueSource(booleans = {false, true})
    void clientCloseAsyncIsOffloaded(boolean offloadClose) throws Exception {
        setup(offloadClose);
        subscribeTo(httpConnection.closeAsync());
        terminated.await();
        assertNoAsyncErrors(errors);
    }

    @ParameterizedTest(name = "offloadClose={0}")
    @ValueSource(booleans = {false, true})
    void clientCloseAsyncGracefullyIsOffloaded(boolean offloadClose) throws Exception {
        setup(offloadClose);
        subscribeTo(httpConnection.closeAsyncGracefully());
        terminated.await();
        assertNoAsyncErrors(errors);
    }

    @ParameterizedTest(name = "offloadClose={0}")
    @ValueSource(booleans = {false, true})
    void clientOnCloseIsOffloaded(boolean noOffloads) throws Exception {
        setup(noOffloads);
        subscribeTo(httpConnection.onClose());
        httpConnection.closeAsync().toFuture().get();
        terminated.await();
        assertNoAsyncErrors(errors);
    }

    @Test
    void testStreamingEffectiveStrategy() throws Exception {
        setup(customStrategyBuilder().offloadReceiveMetadata().build(),
                customStrategyBuilder()
                        .offloadReceiveData().offloadReceiveMetadata().offloadClose().offloadEvent().build(),
                customStrategyBuilder().offloadClose().offloadEvent().build(),
                offloadNone(),
                defaultStrategy(), offloadNone(),
                hsb -> hsb.appendServiceFilter(new StreamingHttpServiceFilterFactory() {
                    @Override
                    public StreamingHttpServiceFilter create(final StreamingHttpService service) {
                        return new StreamingHttpServiceFilter(service);
                    }

                    @Override
                    public HttpExecutionStrategy requiredOffloads() {
                        return customStrategyBuilder().offloadReceiveData().build();
                    }
                }),
                sahcb -> sahcb);
        final Publisher<Buffer> reqPayload =
                from(httpConnection.connectionContext().executionContext().bufferAllocator()
                        .fromAscii("Hello"));
        final SingleSource<StreamingHttpResponse> response = toSource(httpConnection.request(
                httpConnection.post("/").payloadBody(reqPayload)));
        responseSubscribe(response);
        terminated.await();
        assertNoAsyncErrors("Unexpected client errors.", errors);
        assertNoAsyncErrors("Unexpected server errors.", service.errors);
    }

    void responseSubscribe(SingleSource<StreamingHttpResponse> response) {
        response.subscribe(new SingleSource.Subscriber<StreamingHttpResponse>() {
            @Override
            public void onSubscribe(final Cancellable cancellable) {
                if (wrongClientPublishThread.test(currentThread())) {
                    errors.add(new AssertionError(
                            "Client response single: onSubscribe has incorrect offloading. Thread: "
                                    + currentThread().getName()));
                }
            }

            @Override
            public void onSuccess(@Nullable final StreamingHttpResponse result) {
                if (wrongClientPublishThread.test(currentThread())) {
                    errors.add(new AssertionError(
                            "Client response single: onSuccess has incorrect offloading. Thread: "
                                    + currentThread().getName()));
                }
                if (result == null) {
                    errors.add(new AssertionError("Client response is null."));
                    terminated.countDown();
                    return;
                }
                if (!OK.equals(result.status())) {
                    errors.add(new AssertionError("Invalid response status: " + result.status()));
                    terminated.countDown();
                    return;
                }

                subscribeTo(errors,
                        result.payloadBody().afterFinally(terminated::countDown), "Client response payload: ");
            }

            @Override
            public void onError(final Throwable t) {
                if (wrongClientPublishThread.test(currentThread())) {
                    errors.add(new AssertionError("Client response single: onError has incorrect offloading. Thread: "
                            + currentThread().getName()));
                }
                errors.add(new AssertionError("Client response single: Unexpected error.", t));
                terminated.countDown();
            }
        });
    }

    private void subscribeTo(final Completable source) {
        subscribeTo(errors, source.afterFinally(terminated::countDown), t -> false, wrongClientPublishThread);
    }

    private static void subscribeTo(final Collection<Throwable> errors,
                                    final Completable source,
                                    final Predicate<Thread> wrongSubscribeThread,
                                    final Predicate<Thread> wrongPublishThread) {
        toSource(source).subscribe(new CompletableSource.Subscriber() {
            @Override
            public void onSubscribe(final Cancellable cancellable) {
                if (wrongSubscribeThread.test(currentThread())) {
                    errors.add(new AssertionError("onSubscribe has incorrect offloading. Thread: "
                            + currentThread().getName()));
                }
            }

            @Override
            public void onComplete() {
                if (wrongPublishThread.test(currentThread())) {
                    errors.add(new AssertionError("onComplete has incorrect offloading. Thread: "
                            + currentThread().getName()));
                }
            }

            @Override
            public void onError(final Throwable t) {
                if (wrongPublishThread.test(currentThread())) {
                    errors.add(new AssertionError("onError has incorrect offloading. Thread: "
                            + currentThread().getName()));
                }
                errors.add(new AssertionError("Unexpected error.", t));
            }
        });
    }

    private static <T> void subscribeTo(Collection<Throwable> errors, Publisher<T> source, String msgPrefix) {
        toSource(source).subscribe(new Subscriber<T>() {
            @Override
            public void onSubscribe(final Subscription s) {
                if (currentThreadIsIoThread()) {
                    errors.add(new AssertionError(msgPrefix + " onSubscribe was not offloaded. Thread: "
                            + currentThread().getName()));
                }
                s.request(MAX_VALUE);
            }

            @Override
            public void onNext(final T integer) {
                if (currentThreadIsIoThread()) {
                    errors.add(new AssertionError(msgPrefix + " onNext was not offloaded for value: " + integer
                            + ". Thread: " + currentThread().getName()));
                }
            }

            @Override
            public void onError(final Throwable t) {
                if (currentThreadIsIoThread()) {
                    errors.add(new AssertionError(msgPrefix + " onError was not offloaded. Thread: "
                            + currentThread().getName()));
                }
                errors.add(new AssertionError(msgPrefix + " Unexpected error.", t));
            }

            @Override
            public void onComplete() {
                if (currentThreadIsIoThread()) {
                    errors.add(new AssertionError(msgPrefix + " onComplete was not offloaded. Thread: "
                            + currentThread().getName()));
                }
            }
        });
    }

    private static final class OffloadingVerifyingServiceStreaming implements StreamingHttpService {

        private final Queue<Throwable> errors = new ConcurrentLinkedQueue<>();
        private final HttpExecutionStrategy requiredStrategy;
        private final HttpExecutionStrategy expectStrategy;

        OffloadingVerifyingServiceStreaming() {
            this(offloadAll(), offloadAll()); // safe default
        }

        OffloadingVerifyingServiceStreaming(final HttpExecutionStrategy requiredStrategy,
                                            final HttpExecutionStrategy expectStrategy) {
            this.requiredStrategy = requiredStrategy;
            this.expectStrategy = expectStrategy;
        }

        @Override
        public Single<StreamingHttpResponse> handle(final HttpServiceContext ctx,
                                                    final StreamingHttpRequest request,
                                                    final StreamingHttpResponseFactory factory) {
            HttpExecutionStrategy notOffloaded = expectStrategy.missing(ctx.executionContext().executionStrategy());
            if (notOffloaded.hasOffloads()) {
                errors.add(new AssertionError("Expected: " + expectStrategy +
                        " Found: " + ctx.executionContext().executionStrategy()));
            }

            if (currentThreadIsIoThread()) {
                Error failure = new AssertionError("Request: " + request + " received on the eventloop.");
                errors.add(failure);
                throw failure;
            }
            CountDownLatch latch = new CountDownLatch(1);
            subscribeTo(errors,
                    request.payloadBody().afterFinally(latch::countDown), "Server request: ");
            try {
                latch.await();
            } catch (InterruptedException e) {
                errors.add(e);
                Thread.currentThread().interrupt();
                throwException(e);
            }
            Publisher<Buffer> responsePayload =
                    from(ctx.executionContext().bufferAllocator().fromAscii("Hello"))
                            .beforeRequest(n -> {
                                if (currentThreadIsIoThread()) {
                                    errors.add(
                                            new AssertionError("Server response: request-n was not offloaded. Thread: "
                                                    + currentThread().getName()));
                                }
                            });
            return succeeded(factory.ok().payloadBody(responsePayload));
        }

        @Override
        public HttpExecutionStrategy requiredOffloads() {
            return requiredStrategy;
        }
    }
}

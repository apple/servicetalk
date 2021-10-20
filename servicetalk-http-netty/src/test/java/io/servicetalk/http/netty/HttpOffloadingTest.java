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
import io.servicetalk.concurrent.api.AsyncContext;
import io.servicetalk.concurrent.api.Completable;
import io.servicetalk.concurrent.api.Publisher;
import io.servicetalk.concurrent.api.Single;
import io.servicetalk.http.api.HttpServiceContext;
import io.servicetalk.http.api.ReservedStreamingHttpConnection;
import io.servicetalk.http.api.StreamingHttpClient;
import io.servicetalk.http.api.StreamingHttpConnection;
import io.servicetalk.http.api.StreamingHttpRequest;
import io.servicetalk.http.api.StreamingHttpResponse;
import io.servicetalk.http.api.StreamingHttpResponseFactory;
import io.servicetalk.http.api.StreamingHttpService;
import io.servicetalk.transport.api.ServerContext;
import io.servicetalk.transport.netty.internal.ExecutionContextExtension;
import io.servicetalk.transport.netty.internal.NettyIoThreadFactory;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import java.util.Collection;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;
import javax.annotation.Nullable;

import static io.servicetalk.concurrent.api.AsyncCloseables.newCompositeCloseable;
import static io.servicetalk.concurrent.api.BlockingTestUtils.awaitIndefinitelyNonNull;
import static io.servicetalk.concurrent.api.Publisher.from;
import static io.servicetalk.concurrent.api.Single.succeeded;
import static io.servicetalk.concurrent.api.SourceAdapters.toSource;
import static io.servicetalk.http.api.HttpEventKey.MAX_CONCURRENCY;
import static io.servicetalk.http.api.HttpExecutionStrategies.defaultStrategy;
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

    static {
        AsyncContext.disable();
    }

    private static final String IO_EXECUTOR_NAME_PREFIX = "io-executor";

    @RegisterExtension
    static final ExecutionContextExtension CLIENT_CTX =
        ExecutionContextExtension.cached(new NettyIoThreadFactory(IO_EXECUTOR_NAME_PREFIX));
    @RegisterExtension
    static final ExecutionContextExtension SERVER_CTX =
        ExecutionContextExtension.cached(new NettyIoThreadFactory(IO_EXECUTOR_NAME_PREFIX));

    private StreamingHttpConnection httpConnection;
    private Queue<Throwable> errors;
    private CountDownLatch terminated;
    private ServerContext serverContext;
    private OffloadingVerifyingServiceStreaming service;
    private StreamingHttpClient client;

    @BeforeEach
    void beforeTest() throws Exception {
        service = new OffloadingVerifyingServiceStreaming();
        serverContext = forAddress(localAddress(0))
            .ioExecutor(SERVER_CTX.ioExecutor())
            .executor(SERVER_CTX.executor())
            .executionStrategy(defaultStrategy())
            .listenStreamingAndAwait(service);

        errors = new ConcurrentLinkedQueue<>();
        terminated = new CountDownLatch(1);
        client = forSingleAddress(serverHostAndPort(serverContext))
            .ioExecutor(CLIENT_CTX.ioExecutor())
            .executor(CLIENT_CTX.executor())
            .executionStrategy(defaultStrategy())
            .buildStreaming();
        httpConnection = awaitIndefinitelyNonNull(client.reserveConnection(client.get("/")));
    }

    @AfterEach
    void afterTest() throws Exception {
        newCompositeCloseable().appendAll(httpConnection, client, serverContext).close();
    }

    @Test
    void requestResponseIsOffloaded() throws Exception {
        final Publisher<Buffer> reqPayload =
            from(httpConnection.connectionContext().executionContext().bufferAllocator()
                     .fromAscii("Hello"))
                .beforeRequest(n -> {
                    if (currentThreadIsIoThread()) {
                        errors.add(new AssertionError("Server response: request-n was not offloaded. Thread: "
                                                      + currentThread().getName()));
                    }
                });
        final SingleSource<StreamingHttpResponse> resp = toSource(httpConnection.request(
            httpConnection.get("/").payloadBody(reqPayload)));
        resp.subscribe(new SingleSource.Subscriber<StreamingHttpResponse>() {
            @Override
            public void onSubscribe(final Cancellable cancellable) {
                if (currentThreadIsIoThread()) {
                    errors.add(new AssertionError("Client response single: onSubscribe not offloaded. Thread: "
                                                  + currentThread().getName()));
                }
            }

            @Override
            public void onSuccess(@Nullable final StreamingHttpResponse result) {
                if (currentThreadIsIoThread()) {
                    errors.add(new AssertionError("Client response single: onSuccess not offloaded. Thread: "
                                                  + currentThread().getName()));
                }
                if (result == null) {
                    errors.add(new AssertionError("Client response is null."));
                    return;
                }
                if (!OK.equals(result.status())) {
                    errors.add(new AssertionError("Invalid response status: " + result.status()));
                    return;
                }

                subscribeTo(errors,
                            result.payloadBody().afterFinally(terminated::countDown), "Client response payload: ");
            }

            @Override
            public void onError(final Throwable t) {
                if (currentThreadIsIoThread()) {
                    errors.add(new AssertionError("Client response single: onError was not offloaded. Thread: "
                                                  + currentThread().getName()));
                }
                errors.add(new AssertionError("Client response single: Unexpected error.", t));
                terminated.countDown();
            }
        });
        terminated.await();
        assertNoAsyncErrors("Unexpected client errors.", errors);
        assertNoAsyncErrors("Unexpected server errors.", service.errors);
    }

    @Test
    void reserveConnectionIsOffloaded() throws Exception {
        toSource(client.reserveConnection(client.get("/")).afterFinally(terminated::countDown))
            .subscribe(new SingleSource.Subscriber<ReservedStreamingHttpConnection>() {
                @Override
                public void onSubscribe(final Cancellable cancellable) {
                    if (currentThreadIsIoThread()) {
                        errors.add(new AssertionError("onSubscribe not offloaded. Thread: "
                                                      + currentThread().getName()));
                    }
                }

                @Override
                public void onSuccess(@Nullable final ReservedStreamingHttpConnection result) {
                    if (result == null) {
                        errors.add(new AssertionError("Reserved connection is null."));
                        return;
                    }
                    if (currentThreadIsIoThread()) {
                        errors.add(new AssertionError("onSuccess not offloaded. Thread: "
                                                      + currentThread().getName()));
                    }
                }

                @Override
                public void onError(final Throwable t) {
                    if (currentThreadIsIoThread()) {
                        errors.add(new AssertionError("onError was not offloaded. Thread: "
                                                      + currentThread().getName()));
                    }
                    errors.add(new AssertionError("Unexpected error.", t));
                }
            });
        terminated.await();
        assertNoAsyncErrors(errors);
    }

    @Test
    void serverCloseAsyncIsOffloaded() throws Exception {
        subscribeTo(errors, serverContext.closeAsync());
        terminated.await();
        assertNoAsyncErrors(errors);
    }

    @Test
    void serverCloseAsyncGracefullyIsOffloaded() throws Exception {
        subscribeTo(errors, serverContext.closeAsyncGracefully());
        terminated.await();
        assertNoAsyncErrors(errors);
    }

    @Test
    void serverOnCloseIsOffloaded() throws Exception {
        serverContext.closeAsync().toFuture().get();
        subscribeTo(errors, serverContext.onClose());
        terminated.await();
        assertNoAsyncErrors(errors);
    }

    @Test
    void clientSettingsStreamIsOffloaded() throws Exception {
        subscribeTo(errors,
                    httpConnection.transportEventStream(MAX_CONCURRENCY).afterFinally(terminated::countDown),
                    "Client settings stream: ");
        httpConnection.closeAsyncGracefully().toFuture().get();
        terminated.await();
        assertNoAsyncErrors(errors);
    }

    @Test
    void clientCloseAsyncIsOffloaded() throws Exception {
        subscribeTo(errors, httpConnection.closeAsync());
        terminated.await();
        assertNoAsyncErrors(errors);
    }

    @Test
    void clientCloseAsyncGracefullyIsOffloaded() throws Exception {
        subscribeTo(errors, httpConnection.closeAsyncGracefully());
        terminated.await();
        assertNoAsyncErrors(errors);
    }

    @Test
    void clientOnCloseIsOffloaded() throws Exception {
        httpConnection.closeAsync().toFuture().get();
        subscribeTo(errors, httpConnection.onClose());
        terminated.await();
        assertNoAsyncErrors(errors);
    }

    private void subscribeTo(Collection<Throwable> errors, Completable source) {
        toSource(source.afterFinally(terminated::countDown)).subscribe(new CompletableSource.Subscriber() {
            @Override
            public void onSubscribe(final Cancellable cancellable) {
                if (currentThreadIsIoThread()) {
                    errors.add(new AssertionError("onSubscribe was not offloaded. Thread: "
                                                  + currentThread().getName()));
                }
            }

            @Override
            public void onComplete() {
                if (currentThreadIsIoThread()) {
                    errors.add(new AssertionError("onComplete was not offloaded. Thread: "
                                                  + currentThread().getName()));
                }
            }

            @Override
            public void onError(final Throwable t) {
                if (currentThreadIsIoThread()) {
                    errors.add(new AssertionError("onError was not offloaded. Thread: "
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

        @Override
        public Single<StreamingHttpResponse> handle(final HttpServiceContext ctx,
                                                    final StreamingHttpRequest request,
                                                    final StreamingHttpResponseFactory factory) {
            if (currentThreadIsIoThread()) {
                errors.add(new AssertionError("Request: " + request + " received on the eventloop."));
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
    }
}

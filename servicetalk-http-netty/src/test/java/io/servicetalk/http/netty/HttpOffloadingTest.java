/*
 * Copyright © 2018 Apple Inc. and the ServiceTalk project authors
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

import io.servicetalk.concurrent.Cancellable;
import io.servicetalk.concurrent.api.Completable;
import io.servicetalk.concurrent.api.Publisher;
import io.servicetalk.concurrent.api.Single;
import io.servicetalk.concurrent.internal.ServiceTalkTestTimeout;
import io.servicetalk.http.api.HttpClient;
import io.servicetalk.http.api.HttpConnection;
import io.servicetalk.http.api.HttpPayloadChunk;
import io.servicetalk.http.api.HttpRequest;
import io.servicetalk.http.api.HttpResponse;
import io.servicetalk.http.api.HttpService;
import io.servicetalk.transport.api.ConnectionContext;
import io.servicetalk.transport.api.HostAndPort;
import io.servicetalk.transport.api.ServerContext;
import io.servicetalk.transport.netty.IoThreadFactory;
import io.servicetalk.transport.netty.internal.ExecutionContextRule;

import org.junit.After;
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.Timeout;
import org.reactivestreams.Subscriber;
import org.reactivestreams.Subscription;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.util.Collection;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;
import java.util.function.Predicate;
import javax.annotation.Nullable;

import static io.servicetalk.concurrent.api.AsyncCloseables.newCompositeCloseable;
import static io.servicetalk.concurrent.api.Publisher.just;
import static io.servicetalk.concurrent.api.Single.success;
import static io.servicetalk.concurrent.internal.Await.awaitIndefinitely;
import static io.servicetalk.concurrent.internal.Await.awaitIndefinitelyNonNull;
import static io.servicetalk.http.api.HttpConnection.SettingKey.MAX_CONCURRENCY;
import static io.servicetalk.http.api.HttpPayloadChunks.newPayloadChunk;
import static io.servicetalk.http.api.HttpRequestMethods.GET;
import static io.servicetalk.http.api.HttpRequests.newRequest;
import static io.servicetalk.http.api.HttpResponseStatuses.OK;
import static io.servicetalk.http.api.HttpResponses.newResponse;
import static io.servicetalk.http.netty.HttpClients.forSingleAddress;
import static io.servicetalk.transport.netty.internal.ExecutionContextRule.cached;
import static java.lang.Long.MAX_VALUE;
import static java.lang.Thread.currentThread;
import static java.net.InetAddress.getLoopbackAddress;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.is;

public class HttpOffloadingTest {

    @Rule
    public final Timeout timeout = new ServiceTalkTestTimeout();

    private static final String IO_EXECUTOR_NAME_PREFIX = "io-executor";

    private static final InetAddress LOOPBACK_ADDRESS = getLoopbackAddress();

    @ClassRule
    public static final ExecutionContextRule CLIENT_CTX = cached(new IoThreadFactory(IO_EXECUTOR_NAME_PREFIX));
    @ClassRule
    public static final ExecutionContextRule SERVER_CTX = cached(new IoThreadFactory(IO_EXECUTOR_NAME_PREFIX));

    private HttpConnection httpConnection;
    private Thread testThread;
    private Queue<Throwable> errors;
    private CountDownLatch terminated;
    private ConnectionContext connectionContext;
    private ServerContext serverContext;
    private OffloadingVerifyingService service;
    private HttpClient client;

    @Before
    public void beforeTest() throws Exception {
        final InetSocketAddress bindAddress = new InetSocketAddress(LOOPBACK_ADDRESS, 0);
        service = new OffloadingVerifyingService();
        serverContext = awaitIndefinitelyNonNull(
                new DefaultHttpServerStarter().start(SERVER_CTX, bindAddress, service));

        final InetSocketAddress socketAddress = (InetSocketAddress) serverContext.getListenAddress();

        testThread = currentThread();
        errors = new ConcurrentLinkedQueue<>();
        terminated = new CountDownLatch(1);
        client = forSingleAddress(HostAndPort.of(LOOPBACK_ADDRESS.getHostName(), socketAddress.getPort()))
                .build(CLIENT_CTX);
        httpConnection = awaitIndefinitelyNonNull(client.reserveConnection(newRequest(GET, "/")));
        connectionContext = httpConnection.getConnectionContext();
    }

    @After
    public void afterTest() throws Exception {
        awaitIndefinitely(newCompositeCloseable().appendAll(httpConnection, client, serverContext).closeAsync());
    }

    @Test
    public void requestResponseIsOffloaded() throws Exception {
        final Publisher<HttpPayloadChunk> reqPayload =
                just(newPayloadChunk(httpConnection.getConnectionContext().getExecutionContext().getBufferAllocator()
                        .fromAscii("Hello")))
                        .doBeforeRequest(n -> {
                            if (inEventLoopOrTestThread().test(currentThread())) {
                                errors.add(new AssertionError("Server response: request-n was not offloaded. Thread: "
                                        + currentThread().getName()));
                            }
                        });
        final Single<HttpResponse<HttpPayloadChunk>> resp = httpConnection.request(newRequest(GET, "/", reqPayload));
        resp.subscribe(new Single.Subscriber<HttpResponse<HttpPayloadChunk>>() {
            @Override
            public void onSubscribe(final Cancellable cancellable) {
                if (inEventLoopOrTestThread().test(currentThread())) {
                    errors.add(new AssertionError("Client response single: onSubscribe not offloaded. Thread: "
                            + currentThread().getName()));
                }
            }

            @Override
            public void onSuccess(@Nullable final HttpResponse<HttpPayloadChunk> result) {
                if (inEventLoopOrTestThread().test(currentThread())) {
                    errors.add(new AssertionError("Client response single: onSuccess not offloaded. Thread: "
                            + currentThread().getName()));
                }
                if (result == null) {
                    errors.add(new AssertionError("Client response is null."));
                    return;
                }
                if (result.getStatus() != OK) {
                    errors.add(new AssertionError("Invalid response status: " + result.getStatus()));
                    return;
                }

                subscribeTo(inEventLoopOrTestThread(), errors,
                        result.getPayloadBody().doAfterFinally(terminated::countDown), "Client response payload: ");
            }

            @Override
            public void onError(final Throwable t) {
                if (inEventLoopOrTestThread().test(currentThread())) {
                    errors.add(new AssertionError("Client response single: onError was not offloaded. Thread: "
                            + currentThread().getName()));
                }
                errors.add(new AssertionError("Client response single: Unexpected error.", t));
                terminated.countDown();
            }
        });
        terminated.await();
        assertThat("Unexpected client errors.", errors, is(empty()));
        assertThat("Unexpected server errors.", service.errors, is(empty()));
    }

    @Test
    public void reserveConnectionIsOffloaded() throws Exception {
        client.reserveConnection(newRequest(GET, "/")).doAfterFinally(terminated::countDown)
                .subscribe(new Single.Subscriber<HttpClient.ReservedHttpConnection>() {
                    @Override
                    public void onSubscribe(final Cancellable cancellable) {
                        if (inEventLoopOrTestThread().test(currentThread())) {
                            errors.add(new AssertionError("onSubscribe not offloaded. Thread: "
                                    + currentThread().getName()));
                        }
                    }

                    @Override
                    public void onSuccess(@Nullable final HttpClient.ReservedHttpConnection result) {
                        if (result == null) {
                            errors.add(new AssertionError("Reserved connection is null."));
                            return;
                        }
                        if (inEventLoopOrTestThread().test(currentThread())) {
                            errors.add(new AssertionError("onSuccess not offloaded. Thread: "
                                    + currentThread().getName()));
                        }
                    }

                    @Override
                    public void onError(final Throwable t) {
                        if (inEventLoopOrTestThread().test(currentThread())) {
                            errors.add(new AssertionError("onError was not offloaded. Thread: "
                                    + currentThread().getName()));
                        }
                        errors.add(new AssertionError("Unexpected error.", t));
                    }
                });
        terminated.await();
        assertThat("Unexpected errors.", errors, is(empty()));
    }

    @Test
    public void serverCloseAsyncIsOffloaded() throws Exception {
        subscribeTo(inEventLoop(), errors, serverContext.closeAsync());
        terminated.await();
        assertThat("Unexpected errors.", errors, is(empty()));
    }

    @Test
    public void serverCloseAsyncGracefullyIsOffloaded() throws Exception {
        subscribeTo(inEventLoop(), errors, serverContext.closeAsyncGracefully());
        terminated.await();
        assertThat("Unexpected errors.", errors, is(empty()));
    }

    @Test
    public void serverOnCloseIsOffloaded() throws Exception {
        awaitIndefinitely(serverContext.closeAsync());
        subscribeTo(inEventLoop(), errors, serverContext.onClose());
        terminated.await();
        assertThat("Unexpected errors.", errors, is(empty()));
    }

    @Test
    public void clientSettingsStreamIsOffloaded() throws Exception {
        subscribeTo(inEventLoop(), errors,
                httpConnection.getSettingStream(MAX_CONCURRENCY).doAfterFinally(terminated::countDown),
                "Client settings stream: ");
        awaitIndefinitely(httpConnection.closeAsyncGracefully());
        terminated.await();
        assertThat("Unexpected errors.", errors, is(empty()));
    }

    @Test
    public void clientCloseAsyncIsOffloaded() throws Exception {
        subscribeTo(inEventLoop(), errors, connectionContext.closeAsync());
        terminated.await();
        assertThat("Unexpected errors.", errors, is(empty()));
    }

    @Test
    public void clientCloseAsyncGracefullyIsOffloaded() throws Exception {
        subscribeTo(inEventLoop(), errors, connectionContext.closeAsyncGracefully());
        terminated.await();
        assertThat("Unexpected errors.", errors, is(empty()));
    }

    @Test
    public void clientOnCloseIsOffloaded() throws Exception {
        awaitIndefinitely(connectionContext.closeAsync());
        subscribeTo(inEventLoop(), errors, connectionContext.onClose());
        terminated.await();
        assertThat("Unexpected errors.", errors, is(empty()));
    }

    private void subscribeTo(Predicate<Thread> notExpectedThread, Collection<Throwable> errors, Completable source) {
        source.doAfterFinally(terminated::countDown).subscribe(new Completable.Subscriber() {
            @Override
            public void onSubscribe(final Cancellable cancellable) {
                if (notExpectedThread.test(currentThread())) {
                    errors.add(new AssertionError("onSubscribe was not offloaded. Thread: "
                            + currentThread().getName()));
                }
            }

            @Override
            public void onComplete() {
                if (notExpectedThread.test(currentThread())) {
                    errors.add(new AssertionError("onComplete was not offloaded. Thread: "
                            + currentThread().getName()));
                }
            }

            @Override
            public void onError(final Throwable t) {
                if (notExpectedThread.test(currentThread())) {
                    errors.add(new AssertionError("onError was not offloaded. Thread: "
                            + currentThread().getName()));
                }
                errors.add(new AssertionError("Unexpected error.", t));
            }
        });
    }

    private static Predicate<Thread> inEventLoop() {
        return thread -> thread.getName().startsWith(IO_EXECUTOR_NAME_PREFIX);
    }

    private Predicate<Thread> inEventLoopOrTestThread() {
        return inEventLoop().or(thread -> thread == testThread);
    }

    private static <T> void subscribeTo(Predicate<Thread> notExpectedThread, Collection<Throwable> errors,
                                        Publisher<T> source, String msgPrefix) {
        source.subscribe(new Subscriber<T>() {
            @Override
            public void onSubscribe(final Subscription s) {
                if (notExpectedThread.test(currentThread())) {
                    errors.add(new AssertionError(msgPrefix + " onSubscribe was not offloaded. Thread: "
                            + currentThread().getName()));
                }
                s.request(MAX_VALUE);
            }

            @Override
            public void onNext(final T integer) {
                if (notExpectedThread.test(currentThread())) {
                    errors.add(new AssertionError(msgPrefix + " onNext was not offloaded for value: " + integer
                            + ". Thread: " + currentThread().getName()));
                }
            }

            @Override
            public void onError(final Throwable t) {
                if (notExpectedThread.test(currentThread())) {
                    errors.add(new AssertionError(msgPrefix + " onError was not offloaded. Thread: "
                            + currentThread().getName()));
                }
                errors.add(new AssertionError(msgPrefix + " Unexpected error.", t));
            }

            @Override
            public void onComplete() {
                if (notExpectedThread.test(currentThread())) {
                    errors.add(new AssertionError(msgPrefix + " onComplete was not offloaded. Thread: "
                            + currentThread().getName()));
                }
            }
        });
    }

    private final class OffloadingVerifyingService extends HttpService {

        private final Collection<Throwable> errors = new ConcurrentLinkedQueue<>();

        @Override
        public Single<HttpResponse<HttpPayloadChunk>> handle(final ConnectionContext ctx,
                                                             final HttpRequest<HttpPayloadChunk> request) {
            if (inEventLoopOrTestThread().test(currentThread())) {
                errors.add(new AssertionError("Request: " + request + " received on the eventloop."));
            }
            CountDownLatch latch = new CountDownLatch(1);
            subscribeTo(inEventLoopOrTestThread(), errors,
                    request.getPayloadBody().doAfterFinally(latch::countDown), "Server request: ");
            try {
                latch.await();
            } catch (InterruptedException e) {
                errors.add(e);
            }
            Publisher<HttpPayloadChunk> responsePayload =
                    just(newPayloadChunk(ctx.getExecutionContext().getBufferAllocator().fromAscii("Hello")))
                            .doBeforeRequest(n -> {
                                if (inEventLoopOrTestThread().test(currentThread())) {
                                    errors.add(new AssertionError("Server response: request-n was not offloaded. Thread: "
                                            + currentThread().getName()));
                                }
                            });
            return success(newResponse(OK, responsePayload));
        }
    }
}

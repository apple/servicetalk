/*
 * Copyright © 2020-2022 Apple Inc. and the ServiceTalk project authors
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

import io.servicetalk.buffer.api.CompositeBuffer;
import io.servicetalk.client.api.ConnectionFactory;
import io.servicetalk.client.api.ConnectionFactoryFilter;
import io.servicetalk.client.api.DelegatingConnectionFactory;
import io.servicetalk.concurrent.Cancellable;
import io.servicetalk.concurrent.SingleSource.Processor;
import io.servicetalk.concurrent.SingleSource.Subscriber;
import io.servicetalk.concurrent.api.Completable;
import io.servicetalk.concurrent.api.Publisher;
import io.servicetalk.concurrent.api.Single;
import io.servicetalk.context.api.ContextMap;
import io.servicetalk.http.api.FilterableStreamingHttpConnection;
import io.servicetalk.http.api.HttpClient;
import io.servicetalk.http.api.HttpConnection;
import io.servicetalk.http.api.HttpExecutionStrategies;
import io.servicetalk.http.api.HttpRequester;
import io.servicetalk.http.api.StreamingHttpConnectionFilter;
import io.servicetalk.http.api.StreamingHttpRequest;
import io.servicetalk.http.api.StreamingHttpResponse;
import io.servicetalk.transport.api.ConnectionContext;
import io.servicetalk.transport.api.DelegatingConnectionAcceptor;
import io.servicetalk.transport.api.ServerContext;
import io.servicetalk.transport.api.TransportObserver;
import io.servicetalk.transport.netty.internal.ExecutionContextExtension;

import org.hamcrest.Matcher;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.net.InetSocketAddress;
import java.nio.channels.ClosedChannelException;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicInteger;
import javax.annotation.Nullable;

import static io.servicetalk.concurrent.api.AsyncCloseables.newCompositeCloseable;
import static io.servicetalk.concurrent.api.Completable.completed;
import static io.servicetalk.concurrent.api.Processors.newSingleProcessor;
import static io.servicetalk.concurrent.api.Single.defer;
import static io.servicetalk.concurrent.api.SourceAdapters.fromSource;
import static io.servicetalk.http.netty.HttpClients.forSingleAddress;
import static io.servicetalk.http.netty.HttpServers.forAddress;
import static io.servicetalk.logging.api.LogLevel.TRACE;
import static io.servicetalk.transport.netty.internal.AddressUtils.localAddress;
import static io.servicetalk.transport.netty.internal.AddressUtils.serverHostAndPort;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.instanceOf;
import static org.hamcrest.Matchers.is;

@Timeout(3)
class ResponseCancelTest {

    @RegisterExtension
    static final ExecutionContextExtension SERVER_CTX =
            ExecutionContextExtension.cached("server-io", "server-executor")
                    .setClassLevel(true);
    @RegisterExtension
    static final ExecutionContextExtension CLIENT_CTX =
            ExecutionContextExtension.cached("client-io", "client-executor")
                    .setClassLevel(true);

    private final BlockingQueue<Processor<StreamingHttpResponse, StreamingHttpResponse>> serverResponses;
    private final BlockingQueue<Cancellable> delayedClientCancels;
    private final BlockingQueue<ClientTerminationSignal> delayedClientTermination;
    private final ServerContext ctx;
    private final HttpClient client;
    private final AtomicInteger connectionCount = new AtomicInteger();
    private final CountDownLatch clientConnectionClosed = new CountDownLatch(1);
    private final CountDownLatch serverConnectionClosed = new CountDownLatch(1);

    ResponseCancelTest() throws Exception {
        serverResponses = new LinkedBlockingQueue<>();
        delayedClientCancels = new LinkedBlockingQueue<>();
        delayedClientTermination = new LinkedBlockingQueue<>();
        ctx = forAddress(localAddress(0))
                .ioExecutor(SERVER_CTX.ioExecutor())
                .executor(SERVER_CTX.executor())
                .enableWireLogging("servicetalk-tests-wire-logger", TRACE, () -> true)
                .appendConnectionAcceptorFilter(original -> new DelegatingConnectionAcceptor(original) {
                    @Override
                    public Completable accept(final ConnectionContext context) {
                        context.onClose().whenFinally(serverConnectionClosed::countDown).subscribe();
                        return completed();
                    }
                })
                .listenStreamingAndAwait((__, ___, factory) -> {
                    Processor<StreamingHttpResponse, StreamingHttpResponse> resp = newSingleProcessor();
                    serverResponses.add(resp);
                    return fromSource(resp);
                });
        client = forSingleAddress(serverHostAndPort(ctx))
                .ioExecutor(CLIENT_CTX.ioExecutor())
                .executor(CLIENT_CTX.executor())
                .enableWireLogging("servicetalk-tests-wire-logger", TRACE, () -> true)
                .appendConnectionFilter(connection -> new StreamingHttpConnectionFilter(connection) {
                    @Override
                    public Single<StreamingHttpResponse> request(final StreamingHttpRequest request) {
                        return delegate().request(request)
                                .liftSync(target -> new Subscriber<StreamingHttpResponse>() {
                                    @Override
                                    public void onSubscribe(final Cancellable cancellable) {
                                        target.onSubscribe(() -> delayedClientCancels.add(cancellable));
                                    }

                                    @Override
                                    public void onSuccess(final StreamingHttpResponse result) {
                                        delayedClientTermination.add(new ClientTerminationSignal(target, result));
                                    }

                                    @Override
                                    public void onError(final Throwable t) {
                                        delayedClientTermination.add(new ClientTerminationSignal(target, t));
                                    }
                                });
                    }
                })
                .appendConnectionFactoryFilter(ConnectionFactoryFilter.withStrategy(
                        original -> new CountingConnectionFactory(original, connectionCount, clientConnectionClosed),
                        HttpExecutionStrategies.offloadNone()))
                .build();
    }

    @AfterEach
    void tearDown() throws Exception {
        // Do not use graceful close as we are not finishing responses.
        newCompositeCloseable().appendAll(ctx, client).closeAsync().toFuture().get();
    }

    @Test
    void clientCancel() throws Throwable {
        CountDownLatch latch1 = new CountDownLatch(1);
        Cancellable cancellable = sendRequest(client, latch1);
        // wait for server to receive request.
        serverResponses.take();
        assertThat("Unexpected connections count.", connectionCount.get(), is(1));
        cancellable.cancel();
        // wait for cancel to be observed but don't send cancel to the transport so that transport does not close the
        // connection which will then be ambiguous.
        delayedClientCancels.take();
        // We do not let cancel propagate to the transport so the concurrency controller should close the connection
        // and hence fail the response.
        ClientTerminationSignal.resumeExpectFailure(delayedClientTermination, latch1,
                instanceOf(ClosedChannelException.class));
        clientConnectionClosed.await();
        // serverConnectionClosed.await();  server doesn't close the connection bcz it hasn't finished the response yet

        sendSecondRequest();
    }

    @Test
    void clientCancelAfterSuccessOnTransport() throws Throwable {
        CountDownLatch latch1 = new CountDownLatch(1);
        Cancellable cancellable = sendRequest(client, latch1);
        // wait for server to receive request.
        Processor<StreamingHttpResponse, StreamingHttpResponse> serverResp = serverResponses.take();
        assertThat("Unexpected connections count.", connectionCount.get(), is(1));

        serverResp.onSuccess(client.asStreamingClient().httpResponseFactory().ok());
        cancellable.cancel();
        // wait for cancel to be observed but don't send cancel to the transport so that transport does not close the
        // connection which will then be ambiguous.
        delayedClientCancels.take();
        // As there is a race between completion and cancellation, we may get a success or failure, so just wait for
        // any termination.
        delayedClientTermination.take().resume();
        latch1.await();
        clientConnectionClosed.await();
        serverConnectionClosed.await();

        sendSecondRequest();
    }

    @Test
    void connectionCancel() throws Throwable {
        HttpConnection connection = client.reserveConnection(client.get("/")).toFuture().get();
        Cancellable cancellable = sendRequest(connection, null);
        // wait for server to receive request.
        serverResponses.take();
        assertThat("Unexpected connections count.", connectionCount.get(), is(1));
        cancellable.cancel();
        // wait for cancel to be observed and propagate it to the transport to initiate connection closure.
        delayedClientCancels.take().cancel();
        // Transport should close the connection, the response terminal signal is not guaranteed after cancellation.
        clientConnectionClosed.await();
        // serverConnectionClosed.await();  server doesn't close the connection bcz it hasn't finished the response yet

        sendSecondRequest();
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void connectionCancelWaitingForPayloadBody(boolean finishRequest) throws Throwable {
        HttpConnection connection = client.reserveConnection(client.get("/")).toFuture().get();
        Cancellable cancellable = finishRequest ? sendRequest(connection, null) :
                connection.asStreamingConnection().request(connection.asStreamingConnection().post("/")
                                .payloadBody(Publisher.never())).flatMapPublisher(StreamingHttpResponse::payloadBody)
                        .collect(() -> connection.executionContext().bufferAllocator().newCompositeBuffer(),
                                CompositeBuffer::addBuffer)
                        .subscribe(__ -> { });
        // wait for server to receive request.
        Processor<StreamingHttpResponse, StreamingHttpResponse> serverResp = serverResponses.take();
        assertThat("Unexpected connections count.", connectionCount.get(), is(1));

        serverResp.onSuccess(connection.asStreamingConnection().httpResponseFactory().ok()
                .payloadBody(Publisher.never()));
        // wait for response meta-data to be received.
        delayedClientTermination.take().resume();
        // cancel payload body.
        cancellable.cancel();
        // Transport should close the connection, the response terminal signal is not guaranteed after cancellation.
        clientConnectionClosed.await();
        // serverConnectionClosed.await();  server doesn't close the connection bcz it hasn't finished the response yet

        sendSecondRequest();
    }

    private void sendSecondRequest() throws Throwable {
        // Validate client can still communicate with a server using a new connection.
        CountDownLatch latch2 = new CountDownLatch(1);
        sendRequest(client, latch2);
        serverResponses.take().onSuccess(client.asStreamingClient().httpResponseFactory().ok());
        ClientTerminationSignal.resume(delayedClientTermination, latch2);
        assertThat("Unexpected connections count.", connectionCount.get(), is(2));
    }

    private static Cancellable sendRequest(final HttpRequester requester, @Nullable final CountDownLatch latch) {
        return (latch == null ? requester.request(requester.get("/")) :
                requester.request(requester.get("/"))
                        .afterOnSuccess(__ -> latch.countDown())
                        .afterOnError(__ -> latch.countDown())
        ).subscribe(__ -> { });
    }

    private static class CountingConnectionFactory
            extends DelegatingConnectionFactory<InetSocketAddress, FilterableStreamingHttpConnection> {
        private final AtomicInteger connectionCount;
        private final CountDownLatch clientConnectionClosed;

        CountingConnectionFactory(
                final ConnectionFactory<InetSocketAddress, FilterableStreamingHttpConnection> delegate,
                final AtomicInteger connectionCount,
                final CountDownLatch clientConnectionClosed) {
            super(delegate);
            this.connectionCount = connectionCount;
            this.clientConnectionClosed = clientConnectionClosed;
        }

        @Override
        public Single<FilterableStreamingHttpConnection> newConnection(final InetSocketAddress inetSocketAddress,
                                                                       @Nullable final ContextMap context,
                                                                       @Nullable final TransportObserver observer) {
            return defer(() -> {
                connectionCount.incrementAndGet();
                return delegate().newConnection(inetSocketAddress, context, observer).whenOnSuccess(c -> {
                    c.onClose().whenFinally(clientConnectionClosed::countDown).subscribe();
                });
            });
        }
    }

    private static final class ClientTerminationSignal {
        @SuppressWarnings("rawtypes")
        private final Subscriber subscriber;
        @Nullable
        private final Throwable err;
        @Nullable
        private final StreamingHttpResponse response;

        ClientTerminationSignal(@SuppressWarnings("rawtypes") final Subscriber subscriber, final Throwable err) {
            this.subscriber = subscriber;
            this.err = err;
            response = null;
        }

        ClientTerminationSignal(@SuppressWarnings("rawtypes") final Subscriber subscriber,
                                final StreamingHttpResponse response) {
            this.subscriber = subscriber;
            err = null;
            this.response = response;
        }

        @SuppressWarnings("unchecked")
        void resume() {
            if (err != null) {
                subscriber.onError(err);
            } else {
                subscriber.onSuccess(response);
            }
        }

        @SuppressWarnings("unchecked")
        static void resume(BlockingQueue<ClientTerminationSignal> signals,
                           final CountDownLatch latch) throws Throwable {
            ClientTerminationSignal signal = signals.take();
            if (signal.err != null) {
                signal.subscriber.onError(signal.err);
                throw signal.err;
            } else {
                signal.subscriber.onSuccess(signal.response);
            }
            latch.await();
        }

        @SuppressWarnings("unchecked")
        static void resumeExpectFailure(BlockingQueue<ClientTerminationSignal> signals,
                                        final CountDownLatch latch,
                                        final Matcher<Throwable> exceptionMatcher) throws Throwable {
            ClientTerminationSignal signal = signals.take();
            if (signal.err != null) {
                signal.subscriber.onError(signal.err);
                if (!exceptionMatcher.matches(signal.err)) {
                    throw signal.err;
                }
            } else {
                signal.subscriber.onSuccess(signal.response);
                assertThat("Unexpected response success.", null, exceptionMatcher);
            }
            latch.await();
        }
    }
}

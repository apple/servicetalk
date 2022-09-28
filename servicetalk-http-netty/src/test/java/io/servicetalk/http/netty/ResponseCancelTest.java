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

import io.servicetalk.buffer.api.Buffer;
import io.servicetalk.buffer.api.CompositeBuffer;
import io.servicetalk.client.api.ConnectionFactory;
import io.servicetalk.client.api.ConnectionFactoryFilter;
import io.servicetalk.client.api.DelegatingConnectionFactory;
import io.servicetalk.concurrent.Cancellable;
import io.servicetalk.concurrent.SingleSource.Processor;
import io.servicetalk.concurrent.SingleSource.Subscriber;
import io.servicetalk.concurrent.api.Completable;
import io.servicetalk.concurrent.api.Single;
import io.servicetalk.concurrent.api.TestPublisher;
import io.servicetalk.context.api.ContextMap;
import io.servicetalk.context.api.ContextMap.Key;
import io.servicetalk.http.api.FilterableStreamingHttpConnection;
import io.servicetalk.http.api.HttpClient;
import io.servicetalk.http.api.HttpConnection;
import io.servicetalk.http.api.HttpExecutionStrategies;
import io.servicetalk.http.api.HttpRequest;
import io.servicetalk.http.api.HttpRequester;
import io.servicetalk.http.api.StreamingHttpConnection;
import io.servicetalk.http.api.StreamingHttpConnectionFilter;
import io.servicetalk.http.api.StreamingHttpRequest;
import io.servicetalk.http.api.StreamingHttpResponse;
import io.servicetalk.transport.api.ConnectionContext;
import io.servicetalk.transport.api.DelegatingConnectionAcceptor;
import io.servicetalk.transport.api.ServerContext;
import io.servicetalk.transport.api.TransportObserver;
import io.servicetalk.transport.netty.internal.ExecutionContextExtension;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.net.InetSocketAddress;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicInteger;
import javax.annotation.Nullable;

import static io.servicetalk.concurrent.api.AsyncCloseables.newCompositeCloseable;
import static io.servicetalk.concurrent.api.Completable.completed;
import static io.servicetalk.concurrent.api.Processors.newSingleProcessor;
import static io.servicetalk.concurrent.api.Publisher.never;
import static io.servicetalk.concurrent.api.SourceAdapters.fromSource;
import static io.servicetalk.context.api.ContextMap.Key.newKey;
import static io.servicetalk.http.netty.HttpClients.forSingleAddress;
import static io.servicetalk.http.netty.HttpServers.forAddress;
import static io.servicetalk.logging.api.LogLevel.TRACE;
import static io.servicetalk.transport.netty.internal.AddressUtils.localAddress;
import static io.servicetalk.transport.netty.internal.AddressUtils.serverHostAndPort;
import static java.util.Objects.requireNonNull;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.hasSize;

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

    private static final Key<Integer> REQUEST_ID = newKey("REQUEST_ID", Integer.class);
    private static final AtomicInteger REQUEST_ID_GENERATOR = new AtomicInteger();

    private final BlockingQueue<Processor<StreamingHttpResponse, StreamingHttpResponse>> serverResponses;
    private final BlockingQueue<Cancellable> delayedClientCancels;
    private final BlockingQueue<ClientTerminationSignal> delayedClientTermination;
    private final ServerContext ctx;
    private final HttpClient client;
    private final BlockingQueue<CountDownLatch> clientConnectionsClosedStates = new LinkedBlockingQueue<>();
    private final BlockingQueue<CountDownLatch> serverConnectionsClosedStates = new LinkedBlockingQueue<>();

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
                        CountDownLatch serverConnectionClosed = new CountDownLatch(1);
                        serverConnectionsClosedStates.add(serverConnectionClosed);
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
                        final Integer requestId = request.context().get(REQUEST_ID);
                        assert requestId != null;
                        return delegate().request(request)
                                .liftSync(target -> new Subscriber<StreamingHttpResponse>() {
                                    @Override
                                    public void onSubscribe(final Cancellable cancellable) {
                                        target.onSubscribe(() -> delayedClientCancels.add(cancellable));
                                    }

                                    @Override
                                    public void onSuccess(final StreamingHttpResponse result) {
                                        delayedClientTermination.add(
                                                new ClientTerminationSignal(requestId, target, result));
                                    }

                                    @Override
                                    public void onError(final Throwable t) {
                                        delayedClientTermination.add(new ClientTerminationSignal(requestId, target, t));
                                    }
                                });
                    }
                })
                .appendConnectionFactoryFilter(ConnectionFactoryFilter.withStrategy(
                        original -> new CountingConnectionFactory(original, clientConnectionsClosedStates),
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
        Cancellable cancellable = sendRequest(client, null);
        // wait for server to receive request.
        Processor<StreamingHttpResponse, StreamingHttpResponse> serverResp = serverResponses.take();
        assertActiveConnectionsCount(1);
        cancellable.cancel();
        // wait for cancel to be observed, then send it to the transport so that transport and/or concurrency controller
        // inside AbstractStreamingHttpConnection closes the connection.
        delayedClientCancels.take().cancel();
        // The response terminal signal is not guaranteed after cancellation, just wait for the connection to close.
        clientConnectionsClosedStates.take().await();
        // Let the server write the response to fail the write and close the connection
        serverResp.onSuccess(client.asStreamingClient().httpResponseFactory().ok());
        serverConnectionsClosedStates.take().await();

        sendSecondRequestUsingClient();
    }

    @Test
    void clientCancelAfterSuccessOnTransport() throws Throwable {
        Cancellable cancellable = sendRequest(client, null);
        // wait for server to receive request.
        Processor<StreamingHttpResponse, StreamingHttpResponse> serverResp = serverResponses.take();
        assertActiveConnectionsCount(1);

        TestPublisher<Buffer> payload = new TestPublisher<>();
        serverResp.onSuccess(client.asStreamingClient().httpResponseFactory().ok().payloadBody(payload));
        // wait for response meta-data to be received.
        delayedClientTermination.take().resume();
        // cancel payload body.
        cancellable.cancel();
        // The response terminal signal is not guaranteed after cancellation, just wait for the connection to close.
        clientConnectionsClosedStates.take().await();
        // Finish server response to let server close the connection too.
        payload.onComplete();
        serverConnectionsClosedStates.take().await();

        sendSecondRequestUsingClient();
    }

    @Test
    void connectionCancel() throws Throwable {
        HttpConnection connection = client.reserveConnection(client.get("/")).toFuture().get();
        Cancellable cancellable = sendRequest(connection, null);
        // wait for server to receive request.
        Processor<StreamingHttpResponse, StreamingHttpResponse> serverResp = serverResponses.take();
        assertActiveConnectionsCount(1);
        cancellable.cancel();
        // wait for cancel to be observed and propagate it to the transport to initiate connection closure.
        delayedClientCancels.take().cancel();
        // The response terminal signal is not guaranteed after cancellation, just wait for the connection to close.
        clientConnectionsClosedStates.take().await();
        // Let the server write the response to fail the write and close the connection too.
        serverResp.onSuccess(client.asStreamingClient().httpResponseFactory().ok());
        serverConnectionsClosedStates.take().await();

        sendSecondRequestUsingClient();
    }

    @ParameterizedTest(name = "{displayName} [{index}] finishRequest={0}")
    @ValueSource(booleans = {false, true})
    void connectionCancelWaitingForPayloadBody(boolean finishRequest) throws Throwable {
        HttpConnection connection = client.reserveConnection(client.get("/")).toFuture().get();
        Cancellable cancellable;
        if (finishRequest) {
            cancellable = sendRequest(connection, null);
        } else {
            StreamingHttpConnection streamingConnection = connection.asStreamingConnection();
            StreamingHttpRequest request = streamingConnection.post("/").payloadBody(never());
            request.context().put(REQUEST_ID, REQUEST_ID_GENERATOR.incrementAndGet());
            cancellable = streamingConnection.request(request).flatMapPublisher(StreamingHttpResponse::payloadBody)
                    .collect(() -> connection.executionContext().bufferAllocator().newCompositeBuffer(),
                            CompositeBuffer::addBuffer)
                    .subscribe(__ -> { });
        }
        // wait for server to receive request.
        Processor<StreamingHttpResponse, StreamingHttpResponse> serverResp = serverResponses.take();
        assertActiveConnectionsCount(1);

        TestPublisher<Buffer> payload = new TestPublisher<>();
        serverResp.onSuccess(connection.asStreamingConnection().httpResponseFactory().ok()
                .payloadBody(payload));
        // wait for response meta-data to be received.
        delayedClientTermination.take().resume();
        // cancel payload body.
        cancellable.cancel();
        // The response terminal signal is not guaranteed after cancellation, just wait for the connection to close.
        clientConnectionsClosedStates.take().await();
        // Finish server response to let server close the connection too.
        payload.onComplete();
        serverConnectionsClosedStates.take().await();

        sendSecondRequestUsingClient();
    }

    private void sendSecondRequestUsingClient() throws Throwable {
        assertActiveConnectionsCount(0);
        // Validate client can still communicate with a server using a new connection.
        int requestId = REQUEST_ID_GENERATOR.incrementAndGet();
        CountDownLatch latch = new CountDownLatch(1);
        sendRequest(client, requestId, latch);
        serverResponses.take().onSuccess(client.asStreamingClient().httpResponseFactory().ok());
        ClientTerminationSignal.resume(delayedClientTermination, requestId, latch);
        assertActiveConnectionsCount(1);
    }

    private static Cancellable sendRequest(HttpRequester requester,
                                           @Nullable CountDownLatch latch) {
        return sendRequest(requester, REQUEST_ID_GENERATOR.incrementAndGet(), latch);
    }

    private static Cancellable sendRequest(HttpRequester requester,
                                           int requestId,
                                           @Nullable CountDownLatch latch) {
        HttpRequest request = requester.get("/");
        request.context().put(REQUEST_ID, requestId);
        return (latch == null ? requester.request(request) :
                requester.request(request)
                        .afterOnSuccess(__ -> latch.countDown())
                        .afterOnError(__ -> latch.countDown())
        ).subscribe(__ -> { });
    }

    private void assertActiveConnectionsCount(int expected) {
        assertThat("Unexpected client connections count.", clientConnectionsClosedStates, hasSize(expected));
        assertThat("Unexpected server connections count.", serverConnectionsClosedStates, hasSize(expected));
    }

    private static class CountingConnectionFactory
            extends DelegatingConnectionFactory<InetSocketAddress, FilterableStreamingHttpConnection> {

        private final BlockingQueue<CountDownLatch> clientConnectionsClosedStates;

        CountingConnectionFactory(
                final ConnectionFactory<InetSocketAddress, FilterableStreamingHttpConnection> delegate,
                final BlockingQueue<CountDownLatch> clientConnectionsClosedStates) {
            super(delegate);
            this.clientConnectionsClosedStates = clientConnectionsClosedStates;
        }

        @Override
        public Single<FilterableStreamingHttpConnection> newConnection(final InetSocketAddress inetSocketAddress,
                                                                       @Nullable final ContextMap context,
                                                                       @Nullable final TransportObserver observer) {
            return delegate().newConnection(inetSocketAddress, context, observer).whenOnSuccess(c -> {
                CountDownLatch clientConnectionClosed = new CountDownLatch(1);
                clientConnectionsClosedStates.add(clientConnectionClosed);
                c.onClose().whenFinally(clientConnectionClosed::countDown).subscribe();
            });
        }
    }

    private static final class ClientTerminationSignal {
        private final int requestId;
        private final Subscriber<? super StreamingHttpResponse> subscriber;
        @Nullable
        private final Throwable err;
        @Nullable
        private final StreamingHttpResponse response;

        ClientTerminationSignal(int requestId,
                                Subscriber<? super StreamingHttpResponse> subscriber,
                                Throwable err) {
            this.requestId = requestId;
            this.subscriber = requireNonNull(subscriber);
            this.err = requireNonNull(err);
            response = null;
        }

        ClientTerminationSignal(int requestId,
                                Subscriber<? super StreamingHttpResponse> subscriber,
                                StreamingHttpResponse response) {
            this.requestId = requestId;
            this.subscriber = requireNonNull(subscriber);
            err = null;
            this.response = requireNonNull(response);
        }

        void resume() {
            if (err != null) {
                subscriber.onError(err);
            } else {
                subscriber.onSuccess(response);
            }
        }

        static void resume(BlockingQueue<ClientTerminationSignal> signals,
                           int requestId,
                           CountDownLatch latch) throws Throwable {
            ClientTerminationSignal signal;
            do {
                // In case of cancel, a terminal signal may or may not arrive to the subscriber. The requestId helps
                // to make sure we discard optional signals of all previous requests and resuming only for the current
                // request.
                signal = signals.take();
            } while (signal.requestId != requestId);
            if (signal.err != null) {
                signal.subscriber.onError(signal.err);
                throw new AssertionError("Response terminated with an error", signal.err);
            } else {
                signal.subscriber.onSuccess(signal.response);
            }
            latch.await();
        }
    }
}

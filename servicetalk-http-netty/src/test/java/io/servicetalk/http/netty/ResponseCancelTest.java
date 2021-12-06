/*
 * Copyright © 2020-2021 Apple Inc. and the ServiceTalk project authors
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

import io.servicetalk.client.api.ConnectionFactory;
import io.servicetalk.client.api.ConnectionFactoryFilter;
import io.servicetalk.client.api.DelegatingConnectionFactory;
import io.servicetalk.concurrent.Cancellable;
import io.servicetalk.concurrent.SingleSource.Processor;
import io.servicetalk.concurrent.SingleSource.Subscriber;
import io.servicetalk.concurrent.api.Single;
import io.servicetalk.http.api.FilterableStreamingHttpConnection;
import io.servicetalk.http.api.HttpClient;
import io.servicetalk.http.api.HttpExecutionStrategies;
import io.servicetalk.http.api.HttpResponse;
import io.servicetalk.http.api.StreamingHttpConnectionFilter;
import io.servicetalk.http.api.StreamingHttpRequest;
import io.servicetalk.http.api.StreamingHttpResponse;
import io.servicetalk.transport.api.ServerContext;
import io.servicetalk.transport.api.TransportObserver;

import org.hamcrest.Matcher;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.net.InetSocketAddress;
import java.nio.channels.ClosedChannelException;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicInteger;
import javax.annotation.Nullable;

import static io.servicetalk.concurrent.api.AsyncCloseables.newCompositeCloseable;
import static io.servicetalk.concurrent.api.Processors.newSingleProcessor;
import static io.servicetalk.concurrent.api.Single.defer;
import static io.servicetalk.concurrent.api.SourceAdapters.fromSource;
import static io.servicetalk.http.netty.HttpClients.forSingleAddress;
import static io.servicetalk.http.netty.HttpServers.forAddress;
import static io.servicetalk.transport.netty.internal.AddressUtils.localAddress;
import static io.servicetalk.transport.netty.internal.AddressUtils.serverHostAndPort;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.instanceOf;
import static org.hamcrest.Matchers.is;

class ResponseCancelTest {

    private final BlockingQueue<Processor<HttpResponse, HttpResponse>> serverResponses;
    private final BlockingQueue<Cancellable> delayedClientCancels;
    private final BlockingQueue<ClientTerminationSignal> delayedClientTermination;
    private final ServerContext ctx;
    private final HttpClient client;
    private final AtomicInteger connectionCount = new AtomicInteger();

    ResponseCancelTest() throws Exception {
        serverResponses = new LinkedBlockingQueue<>();
        delayedClientCancels = new LinkedBlockingQueue<>();
        delayedClientTermination = new LinkedBlockingQueue<>();
        ctx = forAddress(localAddress(0))
                .listenAndAwait((__, ___, factory) -> {
                    Processor<HttpResponse, HttpResponse> resp = newSingleProcessor();
                    serverResponses.add(resp);
                    return fromSource(resp);
                });
        client = forSingleAddress(serverHostAndPort(ctx))
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
                        original -> new CountingConnectionFactory(original, connectionCount),
                        HttpExecutionStrategies.offloadNone()))
                .build();
    }

    @AfterEach
    void tearDown() throws Exception {
        // Do not use graceful close as we are not finishing responses.
        newCompositeCloseable().appendAll(ctx, client).closeAsync().toFuture().get();
    }

    @Test
    void cancel() throws Throwable {
        CountDownLatch latch1 = new CountDownLatch(1);
        Cancellable cancellable = sendRequest(latch1);
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

        CountDownLatch latch2 = new CountDownLatch(1);
        sendRequest(latch2);
        serverResponses.take().onSuccess(client.httpResponseFactory().ok());
        ClientTerminationSignal.resume(delayedClientTermination, latch2);
        assertThat("Unexpected connections count.", connectionCount.get(), is(2));
    }

    @Test
    void cancelAfterSuccessOnTransport() throws Throwable {
        CountDownLatch latch1 = new CountDownLatch(1);
        Cancellable cancellable = sendRequest(latch1);
        // wait for server to receive request.
        Processor<HttpResponse, HttpResponse> serverResp = serverResponses.take();
        assertThat("Unexpected connections count.", connectionCount.get(), is(1));

        serverResp.onSuccess(client.httpResponseFactory().ok());
        cancellable.cancel();
        // wait for cancel to be observed but don't send cancel to the transport so that transport does not close the
        // connection which will then be ambiguous.
        delayedClientCancels.take();
        // As there is a race between completion and cancellation, we may get a success or failure, so just wait for
        // any termination.
        delayedClientTermination.take().resume();

        CountDownLatch latch2 = new CountDownLatch(1);
        sendRequest(latch2);
        serverResponses.take().onSuccess(client.httpResponseFactory().ok());
        ClientTerminationSignal.resume(delayedClientTermination, latch2);
        assertThat("Unexpected connections count.", connectionCount.get(), is(2));
    }

    private Cancellable sendRequest(final CountDownLatch latch) {
        return client.request(client.get("/"))
                    .afterOnSuccess(__ -> latch.countDown())
                    .afterOnError(__ -> latch.countDown())
                    .subscribe(__ -> { });
    }

    private static class CountingConnectionFactory
            extends DelegatingConnectionFactory<InetSocketAddress, FilterableStreamingHttpConnection> {
        private final AtomicInteger connectionCount;

        CountingConnectionFactory(
                final ConnectionFactory<InetSocketAddress, FilterableStreamingHttpConnection> delegate,
                final AtomicInteger connectionCount) {
            super(delegate);
            this.connectionCount = connectionCount;
        }

        @Override
        public Single<FilterableStreamingHttpConnection> newConnection(final InetSocketAddress inetSocketAddress,
                                                                       @Nullable final TransportObserver observer) {
            return defer(() -> {
                connectionCount.incrementAndGet();
                return delegate().newConnection(inetSocketAddress, observer);
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

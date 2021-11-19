/*
 * Copyright © 2021 Apple Inc. and the ServiceTalk project authors
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
import io.servicetalk.client.api.DelegatingConnectionFactory;
import io.servicetalk.concurrent.Cancellable;
import io.servicetalk.concurrent.SingleSource.Subscriber;
import io.servicetalk.concurrent.api.Single;
import io.servicetalk.http.api.FilterableStreamingHttpConnection;
import io.servicetalk.http.api.HttpClient;
import io.servicetalk.http.api.HttpResponse;
import io.servicetalk.http.api.HttpResponseStatus;
import io.servicetalk.http.api.StreamingHttpClientFilter;
import io.servicetalk.http.api.StreamingHttpRequest;
import io.servicetalk.http.api.StreamingHttpRequester;
import io.servicetalk.http.api.StreamingHttpResponse;
import io.servicetalk.http.utils.TimeoutHttpRequesterFilter;
import io.servicetalk.http.utils.TimeoutHttpServiceFilter;
import io.servicetalk.transport.api.ServerContext;
import io.servicetalk.transport.api.TransportObserver;

import org.hamcrest.Matcher;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.net.InetSocketAddress;
import java.time.Duration;
import java.util.Arrays;
import java.util.Collection;
import java.util.Objects;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;
import javax.annotation.Nullable;

import static io.servicetalk.concurrent.api.AsyncCloseables.newCompositeCloseable;
import static io.servicetalk.concurrent.api.Single.defer;
import static io.servicetalk.concurrent.api.Single.failed;
import static io.servicetalk.concurrent.api.Single.succeeded;
import static io.servicetalk.http.api.HttpResponseStatus.OK;
import static io.servicetalk.http.netty.HttpClients.forSingleAddress;
import static io.servicetalk.http.netty.HttpServers.forAddress;
import static io.servicetalk.transport.netty.internal.AddressUtils.localAddress;
import static io.servicetalk.transport.netty.internal.AddressUtils.serverHostAndPort;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.instanceOf;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.fail;

class ResponseTimeoutTest {

    /**
     * multiplier for timeouts. Should be sufficiently small to ensure that test completes in reasonable time and large
     * enough to ensure that scheduling jitter doesn't cause spurious failures.
     */
    private static final long MILLIS_MULTIPLIER = 100L;

    private final BlockingQueue<Single<HttpResponse>> serverResponses = new LinkedBlockingQueue<>();
    private final BlockingQueue<Cancellable> delayedClientCancels = new LinkedBlockingQueue<>();
    private final BlockingQueue<ClientTerminationSignal> delayedClientTermination = new LinkedBlockingQueue<>();
    private ServerContext ctx;
    private HttpClient client;
    private final AtomicInteger connectionCount = new AtomicInteger();

    private void setUp(Duration clientTimeout,
                       Duration serverTimeout) throws Exception {
        ctx = forAddress(localAddress(0))
                .appendServiceFilter(new TimeoutHttpServiceFilter(__ -> serverTimeout, true))
                .listenAndAwait((__, ___, factory) -> {
                    Single<HttpResponse> resp = Single.never();
                    serverResponses.add(resp);
                    return resp;
                });
        client = forSingleAddress(serverHostAndPort(ctx))
                .appendClientFilter(client -> new StreamingHttpClientFilter(client) {
                    @Override
                    protected Single<StreamingHttpResponse> request(final StreamingHttpRequester delegate,
                                                                    final StreamingHttpRequest request) {
                        return Single.succeeded(null)
                                .afterOnSubscribe(delayedClientCancels::add)
                                .concat(delegate().request(request)
                                        .liftSync(target -> new Subscriber<StreamingHttpResponse>() {
                                            @Override
                                            public void onSubscribe(final Cancellable cancellable) {
                                                target.onSubscribe(() -> {
                                                    delayedClientCancels.add(cancellable);
                                                    cancellable.cancel();
                                                });
                                            }

                                            @Override
                                            public void onSuccess(final StreamingHttpResponse result) {
                                                ClientTerminationSignal signal = OK.equals(result.status()) ?
                                                        new ClientTerminationSignal(target, result)
                                                        : new ClientTerminationSignal(target,
                                                            new HttpResponseStatusException(result.status()));
                                                delayedClientTermination.add(signal);
                                                target.onSuccess(result);
                                            }

                                            @Override
                                            public void onError(final Throwable t) {
                                                delayedClientTermination.add(new ClientTerminationSignal(target, t));
                                                target.onError(t);
                                            }
                                        }))
                                .filter(Objects::nonNull).firstOrError()
                                .map(thing -> (StreamingHttpResponse) thing);
                    }
                })
                .appendConnectionFactoryFilter(original -> new CountingConnectionFactory(original, connectionCount))
                .appendClientFilter(new TimeoutHttpRequesterFilter(__ -> clientTimeout, true))
                .build();
    }

    static Collection<Object> data() {
        return Arrays.asList(
                new Object[]{Duration.ofMillis(2 * MILLIS_MULTIPLIER), null, TimeoutException.class},
                new Object[]{null, Duration.ofMillis(2 * MILLIS_MULTIPLIER), HttpResponseStatusException.class}
        );
    }

    @AfterEach
    void tearDown() throws Exception {
        // Do not use graceful close as we are not finishing responses.
        newCompositeCloseable().appendAll(ctx, client).closeAsync().toFuture().get();
    }

    @ParameterizedTest(name = "{index}: client = {0} server = {1}")
    @MethodSource("data")
    void timeout(Duration clientTimeout,
                 Duration serverTimeout,
                 Class<? extends Throwable> expectThrowableClazz) throws Throwable {
        setUp(clientTimeout, serverTimeout);

        try {
            CountDownLatch latch = new CountDownLatch(1);
            sendRequest(latch);
            // wait for server to receive request.
            serverResponses.take();
            assertThat("Unexpected connections count.", connectionCount.get(), is(1));

            // wait for timeout to be observed but don't send cancel to the transport so that transport does not close
            // the connection which will then be ambiguous.
            delayedClientCancels.take();
            // We do not let cancel propagate to the transport so the concurrency controller should close the connection
            // and hence fail the response.
            ClientTerminationSignal.resumeExpectFailure(delayedClientTermination, latch,
                    instanceOf(expectThrowableClazz));
        } catch (Throwable all) {
            StringWriter sw = new StringWriter();
            all.printStackTrace(new PrintWriter(sw));
            fail("Unexpected exception\n" + sw);
        }
    }

    private Cancellable sendRequest(final CountDownLatch latch) {
        return client.request(client.get("/"))
                .flatMap(response ->
                    OK.equals(response.status()) ?
                            succeeded(response)
                            : failed(new HttpResponseStatusException(response.status()))
                )
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

        @SuppressWarnings("unchecked")
        void resume() {
            if (err != null) {
                subscriber.onError(err);
            } else {
                subscriber.onSuccess(response);
            }
        }
    }

    private static final class HttpResponseStatusException extends RuntimeException {
        private static final long serialVersionUID = -1;

        private final HttpResponseStatus status;

        HttpResponseStatusException(HttpResponseStatus status) {
            this.status = status;
        }

        @Override
        public String getMessage() {
            return status + (null != super.getMessage() ? " : " + super.getMessage() : "");
        }

        public HttpResponseStatus getStatus() {
            return status;
        }
    }
}

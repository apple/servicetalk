/*
 * Copyright © 2024 Apple Inc. and the ServiceTalk project authors
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

import io.servicetalk.client.api.DelegatingConnectionFactory;
import io.servicetalk.client.api.RequestTracker;
import io.servicetalk.concurrent.api.Single;
import io.servicetalk.context.api.ContextMap;
import io.servicetalk.http.api.FilterableStreamingHttpConnection;
import io.servicetalk.http.api.HttpClient;
import io.servicetalk.http.api.HttpResponse;
import io.servicetalk.http.api.HttpService;
import io.servicetalk.http.api.SingleAddressHttpClientBuilder;
import io.servicetalk.transport.api.HostAndPort;
import io.servicetalk.transport.api.ServerContext;
import io.servicetalk.transport.api.TransportObserver;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.net.InetSocketAddress;
import java.util.concurrent.CancellationException;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;
import javax.annotation.Nullable;

import static io.netty.handler.codec.http.HttpStatusClass.SERVER_ERROR;
import static io.netty.handler.codec.http.HttpStatusClass.SUCCESS;
import static io.servicetalk.client.api.RequestTracker.REQUEST_TRACKER_KEY;
import static io.servicetalk.transport.netty.internal.AddressUtils.localAddress;
import static io.servicetalk.transport.netty.internal.AddressUtils.serverHostAndPort;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.notNullValue;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.testng.Assert.assertThrows;

class HttpRequestTrackerTest {

    @Nullable
    private ServerContext serverContext;
    @Nullable
    private HttpClient client;
    @Nullable
    TestRequestTracker testRequestTracker;

    @AfterEach
    void teardown() throws Exception {
        if (serverContext != null) {
            serverContext.close();
        }
        if (client != null) {
            client.close();
        }
    }

    void setup(HttpService service) throws Exception {
        serverContext = HttpServers.forAddress(localAddress(0))
                .listenAndAwait(service);
        SingleAddressHttpClientBuilder<HostAndPort, InetSocketAddress> clientBuilder =
                HttpClients.forSingleAddress(serverHostAndPort(serverContext))
                        .appendConnectionFactoryFilter(connectionFactory ->
                                new DelegatingConnectionFactory<InetSocketAddress,
                                        FilterableStreamingHttpConnection>(connectionFactory) {

                                    @Override
                                    public Single<FilterableStreamingHttpConnection> newConnection(
                                            InetSocketAddress address, @Nullable ContextMap context,
                                            @Nullable TransportObserver observer) {
                                        assert context != null;
                                        RequestTracker requestTracker = context.get(REQUEST_TRACKER_KEY);
                                        assert requestTracker == null;
                                        testRequestTracker = new TestRequestTracker();
                                        context.put(REQUEST_TRACKER_KEY, testRequestTracker);

                                        return super.newConnection(address, context, observer);
                                    }
                                });
        client = clientBuilder.build();
    }

    @Test
    void classifiesResponses() throws Exception {
        setup((ctx, req, factory) -> {
            switch (req.path()) {
                case "/sad":
                    return Single.succeeded(factory.internalServerError());
                case "/slow":
                    return Single.never();
                default:
                    return Single.succeeded(factory.ok());
                }
            });

        HttpResponse response = client.request(client.get("/sad")).toFuture().get();
        assertThat(testRequestTracker, notNullValue());
        assertTrue(SERVER_ERROR.contains(response.status().code()));
        assertThat(testRequestTracker.successes.get(), equalTo(0));
        assertThat(testRequestTracker.failures.get(), equalTo(1));
        assertThat(testRequestTracker.cancellations.get(), equalTo(0));

        // test a success
        response = client.request(client.get("/success")).toFuture().get();
        assertTrue(SUCCESS.contains(response.status().code()));
        assertThat(testRequestTracker.successes.get(), equalTo(1));
        assertThat(testRequestTracker.failures.get(), equalTo(1));
        assertThat(testRequestTracker.cancellations.get(), equalTo(0));

        // cancel: must come last because we'll kill the connection.
        Future<HttpResponse> fResponse = client.request(client.get("/slow")).toFuture();
        fResponse.cancel(true);
        assertThrows(CancellationException.class, () -> fResponse.get());
        assertThat(testRequestTracker.successes.get(), equalTo(1));
        assertThat(testRequestTracker.failures.get(), equalTo(1));
        assertThat(testRequestTracker.cancellations.get(), equalTo(1));
    }

    private static final class TestRequestTracker implements RequestTracker {

        final AtomicInteger successes = new AtomicInteger();
        final AtomicInteger failures = new AtomicInteger();
        final AtomicInteger cancellations = new AtomicInteger();

        @Override
        public synchronized long beforeRequestStart() {
            return 0;
        }

        @Override
        public synchronized void onRequestSuccess(long beforeStartTimeNs) {
            successes.incrementAndGet();
        }

        @Override
        public synchronized void onRequestError(long beforeStartTimeNs, ErrorClass errorClass) {
            if (errorClass == ErrorClass.CANCELLED) {
                cancellations.incrementAndGet();
            } else {
                failures.incrementAndGet();
            }
        }
    }
}

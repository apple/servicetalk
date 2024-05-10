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
package io.servicetalk.traffic.resilience.http;

import io.servicetalk.capacity.limiter.api.CapacityLimiter;
import io.servicetalk.capacity.limiter.api.RequestDroppedException;
import io.servicetalk.concurrent.api.Single;
import io.servicetalk.concurrent.api.TestSingle;
import io.servicetalk.concurrent.test.internal.TestSingleSubscriber;
import io.servicetalk.http.api.HttpClient;
import io.servicetalk.http.api.HttpResponse;
import io.servicetalk.http.api.HttpServerBuilder;
import io.servicetalk.http.api.HttpServiceContext;
import io.servicetalk.http.api.SingleAddressHttpClientBuilder;
import io.servicetalk.http.api.StreamingHttpRequest;
import io.servicetalk.http.api.StreamingHttpResponse;
import io.servicetalk.http.api.StreamingHttpResponseFactory;
import io.servicetalk.http.api.StreamingHttpServiceFilter;
import io.servicetalk.transport.api.HostAndPort;
import io.servicetalk.transport.api.ServerContext;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.net.InetSocketAddress;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.function.Supplier;
import java.util.stream.Stream;

import static io.servicetalk.capacity.limiter.api.CapacityLimiters.fixedCapacity;
import static io.servicetalk.concurrent.api.AsyncCloseables.newCompositeCloseable;
import static io.servicetalk.concurrent.api.SourceAdapters.toSource;
import static io.servicetalk.http.api.HttpResponseStatus.OK;
import static io.servicetalk.http.api.HttpResponseStatus.SERVICE_UNAVAILABLE;
import static io.servicetalk.http.netty.HttpClients.forSingleAddress;
import static io.servicetalk.http.netty.HttpServers.forAddress;
import static io.servicetalk.transport.netty.internal.AddressUtils.localAddress;
import static io.servicetalk.transport.netty.internal.AddressUtils.serverHostAndPort;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.junit.jupiter.api.Assertions.assertThrows;

class CapacityClientServerTest {

    private final BlockingQueue<TestSingle<HttpResponse>> serverResponseQueue = new LinkedBlockingQueue<>();
    private ServerContext ctx;
    private HttpClient client;

    private void setUp(final boolean applyOnClient,
                       final Supplier<CapacityLimiter> limiterSupplier)
        throws Exception {
        final HttpServerBuilder serverBuilder = forAddress(localAddress(0));
        serverBuilder.appendServiceFilter(original -> new StreamingHttpServiceFilter(original) {
            @Override
            public Single<StreamingHttpResponse> handle(final HttpServiceContext ctx,
                                                        final StreamingHttpRequest request,
                                                        final StreamingHttpResponseFactory responseFactory) {
                return delegate().handle(ctx, request, responseFactory);
            }
        });
        if (!applyOnClient) {
            TrafficResilienceHttpServiceFilter serviceFilter =
                    new TrafficResilienceHttpServiceFilter.Builder(limiterSupplier)
                            .onRejectionPolicy(new ServiceRejectionPolicy.Builder()
                                    .onLimitResponseBuilder(ServiceRejectionPolicy.serviceUnavailable()).build())
                            .build();

            serverBuilder.appendServiceFilter(serviceFilter);
        }
        ctx = serverBuilder.listenAndAwait((__, ___, responseFactory) -> {
            TestSingle<HttpResponse> resp = new TestSingle<>();
            serverResponseQueue.add(resp);
            return resp;
        });
        final SingleAddressHttpClientBuilder<HostAndPort, InetSocketAddress> clientBuilder =
                forSingleAddress(serverHostAndPort(ctx));
        if (applyOnClient) {
            final TrafficResilienceHttpClientFilter clientFilter =
                    new TrafficResilienceHttpClientFilter.Builder(() -> {
                        final CapacityLimiter limiter = limiterSupplier.get();
                        return __ -> limiter;
                    }, true).build();

            clientBuilder.appendClientFilter(clientFilter);
        }
        client = clientBuilder.build();
    }

    @AfterEach
    void tearDown() throws Exception {
        newCompositeCloseable().appendAll(client, ctx).close();
    }

    static Stream<Arguments> data() {
        return Stream.of(newParam(true, () -> fixedCapacity(1).build()),
                newParam(false, () -> fixedCapacity(1).build()));
    }

    private static Arguments newParam(
            final boolean applyOnClient,
            final Supplier<CapacityLimiter> capacityProvider) {
        return Arguments.of(applyOnClient, capacityProvider);
    }

    @ParameterizedTest(name = "Apply on client? {0}")
    @MethodSource("data")
    void underCapacity(final boolean applyOnClient,
                       final Supplier<CapacityLimiter> capacityProvider) throws Exception {
        setUp(applyOnClient, capacityProvider);
        TestSingleSubscriber<HttpResponse> responseSub = new TestSingleSubscriber<>();
        CountDownLatch latch = new CountDownLatch(1);
        toSource(client.request(client.get("/")).afterFinally(latch::countDown)).subscribe(responseSub);
        serverResponseQueue.take().onSuccess(client.httpResponseFactory().ok());
        latch.await();
        final HttpResponse response = responseSub.awaitOnSuccess();
        assertThat("Unexpected result.", response, is(notNullValue()));
        assertThat("Unexpected result.", response.status(), is(OK));
    }

    @ParameterizedTest(name = "Apply on client? {0}")
    @MethodSource("data")
    void overCapacity(final boolean applyOnClient,
                      final Supplier<CapacityLimiter> capacityProvider) throws Exception {
        setUp(applyOnClient, capacityProvider);
        TestSingleSubscriber<HttpResponse> response1Sub = new TestSingleSubscriber<>();
        toSource(client.request(client.get("/"))).subscribe(response1Sub); // never completes
        serverResponseQueue.take(); // Ensure the request reaches the server.

        if (applyOnClient) {
            assertThrows(RequestDroppedException.class, () -> client.asBlockingClient().request(client.get("/")));
        } else {
            final HttpResponse response2 = client.asBlockingClient().request(client.get("/"));
            assertThat("Unexpected result.", response2.status(), is(SERVICE_UNAVAILABLE));
        }
    }
}

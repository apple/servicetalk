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

import io.servicetalk.concurrent.api.Completable;
import io.servicetalk.concurrent.api.Single;
import io.servicetalk.http.api.BlockingHttpClient;
import io.servicetalk.http.api.BlockingHttpRequests;
import io.servicetalk.http.api.HttpClient;
import io.servicetalk.http.api.HttpPayloadChunk;
import io.servicetalk.http.api.HttpRequest;
import io.servicetalk.http.api.HttpRequestMethods;
import io.servicetalk.http.api.HttpResponse;
import io.servicetalk.transport.api.DefaultExecutionContext;
import io.servicetalk.transport.api.ExecutionContext;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import javax.annotation.Nullable;

import static io.servicetalk.buffer.netty.BufferAllocators.DEFAULT_ALLOCATOR;
import static io.servicetalk.concurrent.api.Executors.immediate;
import static io.servicetalk.concurrent.api.Publisher.empty;
import static io.servicetalk.concurrent.api.Single.success;
import static io.servicetalk.concurrent.internal.Await.awaitIndefinitely;
import static io.servicetalk.http.api.HttpResponseStatuses.OK;
import static io.servicetalk.http.api.HttpResponses.newResponse;
import static io.servicetalk.loadbalancer.RoundRobinLoadBalancer.newRoundRobinFactory;
import static io.servicetalk.transport.netty.NettyIoExecutors.createIoExecutor;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;

public class ClientFilterFactoryTest {

    private List<Integer> filterOrder;
    private ExecutionContext executionContext;
    private DefaultHttpClientBuilder<InetSocketAddress> builder;
    @Nullable
    private BlockingHttpClient client;

    @Before
    public void setUp() {
        filterOrder = new ArrayList<>();
        executionContext = new DefaultExecutionContext(DEFAULT_ALLOCATOR, createIoExecutor(), immediate());
        builder = new DefaultHttpClientBuilder<>(newRoundRobinFactory());
        builder.addClientFilterFactory(httpClient -> new DelegatingHttpClient(httpClient, request -> success(newResponse(OK))));
    }

    @After
    public void tearDown() throws Exception {
        if (client != null) {
            client.close();
        }
        awaitIndefinitely(executionContext.getIoExecutor().closeAsync());
        filterOrder.clear();
    }

    @Test
    public void appendClientFilterFactoryOrder() throws Exception {
        builder.addClientFilterFactory(httpClient -> new DelegatingHttpClient(httpClient, request -> {
            filterOrder.add(1);
            return httpClient.request(request);
        }));
        builder.addClientFilterFactory(httpClient -> new DelegatingHttpClient(httpClient, request -> {
            filterOrder.add(2);
            return httpClient.request(request);
        }));
        builder.addClientFilterFactory(httpClient -> new DelegatingHttpClient(httpClient, request -> {
            filterOrder.add(3);
            return httpClient.request(request);
        }));
        buildClientAndSendRequest();
        // Filters are applied in the reverse order, i.e. the last added filter is closest to the user.
        assertThat("Unexpected order.", filterOrder, contains(3, 2, 1));
    }

    @Test
    public void appendClientFilterFactoryBiFunctionOrder() throws Exception {
        builder.addClientFilterFactory((httpClient, lbStream) -> new DelegatingHttpClient(httpClient, request -> {
            filterOrder.add(1);
            return httpClient.request(request);
        }));
        builder.addClientFilterFactory((httpClient, lbStream) -> new DelegatingHttpClient(httpClient, request -> {
            filterOrder.add(2);
            return httpClient.request(request);
        }));
        builder.addClientFilterFactory((httpClient, lbStream) -> new DelegatingHttpClient(httpClient, request -> {
            filterOrder.add(3);
            return httpClient.request(request);
        }));
        buildClientAndSendRequest();
        // Filters are applied in the reverse order, i.e. the last added filter is closest to the user.
        assertThat("Unexpected order.", filterOrder, contains(3, 2, 1));
    }

    private void buildClientAndSendRequest() throws Exception {
        client = builder.buildBlocking(executionContext, empty());
        client.request(BlockingHttpRequests.newRequest(HttpRequestMethods.GET, "/"));
    }

    private static final class DelegatingHttpClient extends HttpClient {

        private final HttpClient httpClient;
        private final Function<HttpRequest<HttpPayloadChunk>, Single<HttpResponse<HttpPayloadChunk>>> requester;

        DelegatingHttpClient(final HttpClient httpClient, Function<HttpRequest<HttpPayloadChunk>, Single<HttpResponse<HttpPayloadChunk>>> requester) {
            this.httpClient = httpClient;
            this.requester = requester;
        }

        @Override
        public Single<? extends ReservedHttpConnection> reserveConnection(final HttpRequest<HttpPayloadChunk> request) {
            return httpClient.reserveConnection(request);
        }

        @Override
        public Single<? extends UpgradableHttpResponse<HttpPayloadChunk>> upgradeConnection(final HttpRequest<HttpPayloadChunk> request) {
            return httpClient.upgradeConnection(request);
        }

        @Override
        public Single<HttpResponse<HttpPayloadChunk>> request(final HttpRequest<HttpPayloadChunk> request) {
            return requester.apply(request);
        }

        @Override
        public ExecutionContext getExecutionContext() {
            return httpClient.getExecutionContext();
        }

        @Override
        public Completable onClose() {
            return httpClient.onClose();
        }

        @Override
        public Completable closeAsync() {
            return httpClient.closeAsync();
        }
    }
}

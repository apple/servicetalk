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

import io.servicetalk.http.api.BlockingStreamingHttpClient;
import io.servicetalk.http.api.ClientFilterFunction;
import io.servicetalk.http.utils.StreamingHttpClientFunctionFilter;
import io.servicetalk.transport.api.DefaultExecutionContext;
import io.servicetalk.transport.api.ExecutionContext;
import io.servicetalk.transport.api.HostAndPort;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.List;
import javax.annotation.Nullable;

import static io.servicetalk.buffer.netty.BufferAllocators.DEFAULT_ALLOCATOR;
import static io.servicetalk.concurrent.api.Executors.immediate;
import static io.servicetalk.concurrent.api.Single.success;
import static io.servicetalk.concurrent.internal.Await.awaitIndefinitely;
import static io.servicetalk.http.api.ClientFilterFunction.from;
import static io.servicetalk.http.api.DefaultHttpHeadersFactory.INSTANCE;
import static io.servicetalk.http.api.HttpProtocolVersions.HTTP_1_1;
import static io.servicetalk.http.api.HttpResponseStatuses.OK;
import static io.servicetalk.http.api.StreamingHttpResponses.newResponse;
import static io.servicetalk.transport.netty.NettyIoExecutors.createIoExecutor;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;

public class ClientFilterFunctionTest {

    private List<Integer> filterOrder;
    private ExecutionContext executionContext;
    private SingleAddressHttpClientBuilder<HostAndPort, InetSocketAddress> builder;
    @Nullable
    private BlockingStreamingHttpClient client;
    private ClientFilterFunction mockClientFilter;
    private ClientFilterFunction filter1;
    private ClientFilterFunction filter2;
    private ClientFilterFunction filter3;

    @Before
    public void setUp() {
        filterOrder = new ArrayList<>();
        executionContext = new DefaultExecutionContext(DEFAULT_ALLOCATOR, createIoExecutor(), immediate());
        builder = HttpClients.forSingleAddress("localhost", 0);
        mockClientFilter = from(httpClient ->
                new StreamingHttpClientFunctionFilter((client, req) -> success(newResponse(OK, HTTP_1_1,
                        INSTANCE.newHeaders(), INSTANCE.newEmptyTrailers(),
                        executionContext.getBufferAllocator())), httpClient));

        filter1 = from(httpClient -> new StreamingHttpClientFunctionFilter((client, request) -> {
            filterOrder.add(1);
            return client.request(request);
        }, httpClient));
        filter2 = from(httpClient -> new StreamingHttpClientFunctionFilter((client, request) -> {
            filterOrder.add(2);
            return client.request(request);
        }, httpClient));
        filter3 = from(httpClient -> new StreamingHttpClientFunctionFilter((client, request) -> {
            filterOrder.add(3);
            return client.request(request);
        }, httpClient));
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
    public void appendClientFilterExecutesInAppendOrder() throws Exception {
        builder.appendClientFilter(filter1.append(filter2).append(filter3));
        builder.appendClientFilter(mockClientFilter);
        buildClientAndSendRequest();
        // Filters are applied in order, i.e. the first added filter is closest to the user.
        assertThat("Unexpected order.", filterOrder, contains(1, 2, 3));
    }

    private void buildClientAndSendRequest() throws Exception {
        client = builder.buildBlockingStreaming(executionContext);
        client.request(client.get("/"));
    }
}

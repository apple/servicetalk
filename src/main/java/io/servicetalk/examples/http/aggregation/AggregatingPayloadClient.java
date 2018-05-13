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
package io.servicetalk.examples.http.aggregation;

import io.servicetalk.buffer.api.Buffer;
import io.servicetalk.buffer.api.CompositeBuffer;
import io.servicetalk.client.api.ServiceDiscoverer;
import io.servicetalk.client.internal.DefaultHostAndPort;
import io.servicetalk.client.internal.HostAndPort;
import io.servicetalk.dns.discovery.netty.DefaultDnsServiceDiscoverer;
import io.servicetalk.http.api.AggregatedHttpClient;
import io.servicetalk.http.api.FullHttpRequest;
import io.servicetalk.http.netty.DefaultHttpClientBuilder;
import io.servicetalk.transport.api.DefaultExecutionContext;
import io.servicetalk.transport.api.ExecutionContext;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.InetSocketAddress;
import java.util.concurrent.CountDownLatch;

import static io.servicetalk.buffer.netty.BufferAllocators.DEFAULT_ALLOCATOR;
import static io.servicetalk.concurrent.api.Executors.newCachedThreadExecutor;
import static io.servicetalk.concurrent.internal.Await.awaitIndefinitely;
import static io.servicetalk.http.api.FullHttpRequests.newRequest;
import static io.servicetalk.http.api.HttpHeaderNames.TRANSFER_ENCODING;
import static io.servicetalk.http.api.HttpHeaderValues.CHUNKED;
import static io.servicetalk.http.api.HttpRequestMethods.GET;
import static io.servicetalk.loadbalancer.RoundRobinLoadBalancer.newRoundRobinFactory;
import static io.servicetalk.transport.netty.NettyIoExecutors.createIoExecutor;
import static java.nio.charset.StandardCharsets.US_ASCII;

public final class AggregatingPayloadClient {
    private static final Logger LOGGER = LoggerFactory.getLogger(AggregatingPayloadClient.class);

    public static void main(String[] args) throws Exception {
        // Setup the ExecutionContext to offload user code onto a cached Executor.
        ExecutionContext executionContext =
                new DefaultExecutionContext(DEFAULT_ALLOCATOR, createIoExecutor(), newCachedThreadExecutor());

        // In this example we will use DNS as our Service Discovery system.
        ServiceDiscoverer<HostAndPort, InetSocketAddress> dnsDiscoverer =
                new DefaultDnsServiceDiscoverer.Builder(executionContext).build().toHostAndPortDiscoverer();

        // Create a ClientBuilder and use round robin load balancing.
        DefaultHttpClientBuilder<InetSocketAddress> clientBuilder =
                new DefaultHttpClientBuilder<>(newRoundRobinFactory());

        // Build the client, and register for DNS discovery events.
        final AggregatedHttpClient client = clientBuilder.buildAggregated(
                executionContext, dnsDiscoverer.discover(new DefaultHostAndPort("localhost", 8080)));

        // This example is demonstrating asynchronous execution, but needs to prevent the main thread from exiting
        // before the response has been processed. This isn't typical usage for a streaming API but is useful for
        // demonstration purposes.
        CountDownLatch responseProcessedLatch = new CountDownLatch(1);

        Buffer data = executionContext.getBufferAllocator().fromAscii("lorem ipsum dolor sit amet \n");
        // Create a big buffer so that we can leverage aggregation on the response which is the request payload
        // echoed back.
        final CompositeBuffer payload = executionContext.getBufferAllocator().newCompositeBuffer(10);
        for (int i = 0; i < 10; i++) {
            payload.addBuffer(data);
        }
        FullHttpRequest request = newRequest(GET, "/sayHello", payload);
        // This is required at the moment since HttpClient does not add a transfer-encoding header.
        request.getHeaders().add(TRANSFER_ENCODING, CHUNKED);
        client.request(request)
                .doAfterError(cause -> LOGGER.error("request failed!", cause))
                .doAfterFinally(responseProcessedLatch::countDown)
                .subscribe(response -> {
                    LOGGER.info("got response \n{}", response.toString((name, value) -> value));
                    LOGGER.info("Response content: \n{}", response.getPayloadBody().toString(US_ASCII));
                });

        // Don't exit the main thread until after the response is completely processed.
        responseProcessedLatch.await();

        // cleanup the HttpClient, ServiceDiscoverer, and IoExecutor
        awaitIndefinitely(client.closeAsync().mergeDelayError(dnsDiscoverer.closeAsync(),
                executionContext.getIoExecutor().closeAsync()));
    }
}

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
package io.servicetalk.examples.http.helloworld;

import io.servicetalk.client.api.ServiceDiscoverer;
import io.servicetalk.client.internal.DefaultHostAndPort;
import io.servicetalk.client.internal.HostAndPort;
import io.servicetalk.concurrent.api.AsyncCloseables;
import io.servicetalk.concurrent.api.CompositeCloseable;
import io.servicetalk.dns.discovery.netty.DefaultDnsServiceDiscoverer.Builder;
import io.servicetalk.http.api.HttpClient;
import io.servicetalk.http.api.HttpPayloadChunk;
import io.servicetalk.http.netty.DefaultHttpClientBuilder;
import io.servicetalk.transport.api.DefaultExecutionContext;
import io.servicetalk.transport.api.ExecutionContext;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.InetSocketAddress;
import java.util.concurrent.CountDownLatch;

import static io.servicetalk.buffer.netty.BufferAllocators.DEFAULT_ALLOCATOR;
import static io.servicetalk.concurrent.api.Executors.newCachedThreadExecutor;
import static io.servicetalk.http.api.HttpRequestMethods.GET;
import static io.servicetalk.http.api.HttpRequests.newRequest;
import static io.servicetalk.loadbalancer.RoundRobinLoadBalancer.newRoundRobinFactory;
import static io.servicetalk.transport.netty.NettyIoExecutors.createIoExecutor;
import static java.nio.charset.StandardCharsets.US_ASCII;

public final class HelloWorldClient {
    private static final Logger LOGGER = LoggerFactory.getLogger(HelloWorldClient.class);

    public static void main(String[] args) throws Exception {
        // Collection of all resources in this test that can be closed together at the end.
        try (CompositeCloseable resources = AsyncCloseables.newCompositeCloseable()) {
            // Setup the ExecutionContext to offload user code onto a cached Executor.
            ExecutionContext executionContext =
                    new DefaultExecutionContext(DEFAULT_ALLOCATOR, createIoExecutor(), newCachedThreadExecutor());
            // In this example we will use DNS as our Service Discovery system.
            ServiceDiscoverer<HostAndPort, InetSocketAddress> dnsDiscoverer =
                    new Builder(executionContext).build().toHostAndPortDiscoverer();

            // Create a ClientBuilder and use round robin load balancing.
            DefaultHttpClientBuilder<InetSocketAddress> clientBuilder =
                    new DefaultHttpClientBuilder<>(newRoundRobinFactory());

            // Build the client, and register for DNS discovery events.
            HttpClient client = clientBuilder.build(
                    executionContext, dnsDiscoverer.discover(new DefaultHostAndPort("localhost", 8080)));

            // This example is demonstrating asynchronous execution, but needs to prevent the main thread from exiting
            // before the response has been processed. This isn't typical usage for a streaming API but is useful for
            // demonstration purposes.
            CountDownLatch responseProcessedLatch = new CountDownLatch(1);

            // Create a request, send the request, convert each chunk to a string, and log it out.
            client.request(newRequest(GET, "/sayHello")).flatMapPublisher(response -> {
                // Log the response meta data and headers, by default the header values will be filtered for
                // security reasons, however here we override the filter and print every value.
                LOGGER.info("got response {}", response.toString((name, value) -> value));

                // Map each chunk of the response payload from a Buffer to a String.
                return response.getPayloadBody().map(chunk -> chunk.getContent().toString(US_ASCII));
            }).doAfterError(cause -> LOGGER.error("request failed!", cause))
                    .doAfterFinally(responseProcessedLatch::countDown)
                    .forEach(stringPayloadChunk -> LOGGER.info("converted string chunk '{}'", stringPayloadChunk));

            // Don't exit the main thread until after the response is completely processed.
            responseProcessedLatch.await();

            // cleanup the HttpClient, ServiceDiscoverer, and IoExecutor
            resources.concat(client, dnsDiscoverer, executionContext.getExecutor(), executionContext.getIoExecutor());
        }
    }
}

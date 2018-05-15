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
import io.servicetalk.http.api.BlockingHttpClient;
import io.servicetalk.http.api.BlockingHttpResponse;
import io.servicetalk.http.api.HttpPayloadChunk;
import io.servicetalk.http.netty.DefaultHttpClientBuilder;
import io.servicetalk.transport.api.DefaultExecutionContext;
import io.servicetalk.transport.api.ExecutionContext;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.InetSocketAddress;

import static io.servicetalk.buffer.netty.BufferAllocators.DEFAULT_ALLOCATOR;
import static io.servicetalk.concurrent.api.Executors.newCachedThreadExecutor;
import static io.servicetalk.http.api.BlockingHttpRequests.newRequest;
import static io.servicetalk.http.api.HttpRequestMethods.GET;
import static io.servicetalk.loadbalancer.RoundRobinLoadBalancer.newRoundRobinFactory;
import static io.servicetalk.transport.netty.NettyIoExecutors.createIoExecutor;
import static java.nio.charset.StandardCharsets.US_ASCII;

public final class HelloWorldBlockingClient {
    private static final Logger LOGGER = LoggerFactory.getLogger(HelloWorldBlockingClient.class);

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
            BlockingHttpClient<HttpPayloadChunk, HttpPayloadChunk> client = clientBuilder.buildBlocking(
                    executionContext, dnsDiscoverer.discover(new DefaultHostAndPort("localhost", 8080)));

            // Create a request, send the request, and wait for the response.
            BlockingHttpResponse<HttpPayloadChunk> response = client.request(newRequest(GET, "/sayHello"));

            // Log the response meta data and headers, by default the header values will be filtered for
            // security reasons, however here we override the filter and print every value.
            LOGGER.info("got response {}", response.toString((name, value) -> value));

            // Iterate through all the response payload chunks. Note that data is streaming in the background so this
            // may be synchronous as opposed to blocking, but if data is not available the APIs will have to block.
            for (HttpPayloadChunk responseChunk : response.getPayloadBody()) {
                LOGGER.info("converted string chunk '{}'", responseChunk.getContent().toString(US_ASCII));
            }

            // cleanup the BlockingHttpClient, ServiceDiscoverer, and IoExecutor
            client.close();
            resources.concat(dnsDiscoverer, executionContext);
        }
    }
}

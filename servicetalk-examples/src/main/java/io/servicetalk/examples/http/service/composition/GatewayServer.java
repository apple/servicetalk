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
package io.servicetalk.examples.http.service.composition;

import io.servicetalk.concurrent.api.CompositeCloseable;
import io.servicetalk.data.jackson.JacksonSerializationProvider;
import io.servicetalk.http.api.AggregatedHttpClient;
import io.servicetalk.http.api.DefaultHttpSerializer;
import io.servicetalk.http.api.HttpClient;
import io.servicetalk.http.api.HttpSerializer;
import io.servicetalk.http.api.HttpService;
import io.servicetalk.http.netty.DefaultHttpClientBuilder;
import io.servicetalk.http.netty.DefaultHttpServerStarter;
import io.servicetalk.http.router.predicate.HttpPredicateRouterBuilder;
import io.servicetalk.http.utils.HttpClientFunctionFilter;
import io.servicetalk.transport.api.DefaultExecutionContext;
import io.servicetalk.transport.api.ExecutionContext;
import io.servicetalk.transport.api.HostAndPort;
import io.servicetalk.transport.api.IoExecutor;
import io.servicetalk.transport.api.ServerContext;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static io.servicetalk.buffer.netty.BufferAllocators.DEFAULT_ALLOCATOR;
import static io.servicetalk.concurrent.api.AsyncCloseables.newCompositeCloseable;
import static io.servicetalk.concurrent.api.Executors.newCachedThreadExecutor;
import static io.servicetalk.concurrent.internal.Await.awaitIndefinitely;
import static io.servicetalk.concurrent.internal.Await.awaitIndefinitelyNonNull;
import static io.servicetalk.examples.http.service.composition.backends.PortRegistry.METADATA_BACKEND_ADDRESS;
import static io.servicetalk.examples.http.service.composition.backends.PortRegistry.RATINGS_BACKEND_ADDRESS;
import static io.servicetalk.examples.http.service.composition.backends.PortRegistry.RECOMMENDATIONS_BACKEND_ADDRESS;
import static io.servicetalk.examples.http.service.composition.backends.PortRegistry.USER_BACKEND_ADDRESS;
import static io.servicetalk.transport.netty.NettyIoExecutors.createIoExecutor;
import static java.time.Duration.ofMillis;

/**
 * A server starter for gateway to all backends.
 */
public final class GatewayServer {
    private static final Logger LOGGER = LoggerFactory.getLogger(GatewayServer.class);

    private GatewayServer() {
        // No instances.
    }

    /**
     * Starts this server.
     *
     * @param args Program arguments, none supported yet.
     * @throws Exception If the server could not be started.
     */
    public static void main(String[] args) throws Exception {
        // Create an AutoCloseable representing all resources used in this example.
        try (CompositeCloseable resources = newCompositeCloseable()) {
            // Shared IoExecutor for the application.
            IoExecutor ioExecutor = resources.prepend(createIoExecutor());

            // ExecutionContext for the server.
            ExecutionContext executionContext = new DefaultExecutionContext(DEFAULT_ALLOCATOR,
                    ioExecutor, resources.prepend(newCachedThreadExecutor()));

            // Create clients for the different backends we are going to use in the gateway.
            HttpClient recommendationsClient =
                    newClient(ioExecutor, RECOMMENDATIONS_BACKEND_ADDRESS, resources);
            AggregatedHttpClient metadataClient =
                    newClient(ioExecutor, METADATA_BACKEND_ADDRESS, resources).asAggregatedClient();
            AggregatedHttpClient userClient =
                    newClient(ioExecutor, USER_BACKEND_ADDRESS, resources).asAggregatedClient();
            AggregatedHttpClient ratingsClient =
                    newClient(ioExecutor, RATINGS_BACKEND_ADDRESS, resources).asAggregatedClient();

            // Use Jackson for serialization and deserialization.
            // HttpSerializer validates HTTP metadata for serialization/deserialization and also provides higher level
            // HTTP focused serialization APIs.
            HttpSerializer httpSerializer = DefaultHttpSerializer.forJson(new JacksonSerializationProvider());

            // Gateway supports different endpoints for blocking, streaming or aggregated implementations.
            // We create a router to express these endpoints.
            HttpPredicateRouterBuilder routerBuilder = new HttpPredicateRouterBuilder();
            final HttpService gatewayService =
                    routerBuilder.whenPathStartsWith("/recommendations/stream")
                            .thenRouteTo(new GatewayService(recommendationsClient, metadataClient, ratingsClient,
                                    userClient, httpSerializer))
                            .whenPathStartsWith("/recommendations/aggregated")
                            .thenRouteTo(new AggregatedGatewayService(recommendationsClient.asAggregatedClient(),
                                    metadataClient, ratingsClient, userClient, httpSerializer).asService())
                            .whenPathStartsWith("/recommendations/blocking")
                            .thenRouteTo(new BlockingGatewayService(recommendationsClient.asBlockingAggregatedClient(),
                                    metadataClient.asBlockingAggregatedClient(),
                                    ratingsClient.asBlockingAggregatedClient(),
                                    userClient.asBlockingAggregatedClient(), httpSerializer).asService())
                            .build();

            // Create configurable starter for HTTP server.
            DefaultHttpServerStarter starter = new DefaultHttpServerStarter();
            // Starting the server will start listening for incoming client requests.
            ServerContext serverContext = awaitIndefinitelyNonNull(
                    starter.start(executionContext, 8080, gatewayService));

            LOGGER.info("listening on {}", serverContext.getListenAddress());

            // Stop listening/accepting more sockets and gracefully shutdown all open sockets.
            awaitIndefinitely(serverContext.onClose());
        }
    }

    private static HttpClient newClient(final IoExecutor ioExecutor,
                                        final HostAndPort serviceAddress,
                                        final CompositeCloseable resources) {

        // Setup the ExecutionContext to offload user code onto a cached Executor.
        DefaultExecutionContext executionContext = new DefaultExecutionContext(DEFAULT_ALLOCATOR, ioExecutor,
                resources.prepend(newCachedThreadExecutor()));

        return resources.prepend(
                DefaultHttpClientBuilder.forSingleAddress(serviceAddress)
                        // Set retry and timeout filters for all clients.
                        .setClientFilterFactory((client, lbEventStream) -> {
                            // Apply a timeout filter for the client to guard against extremely latent clients.
                            return new HttpClientFunctionFilter((requester, request) ->
                                    requester.request(request).timeout(ofMillis(100),
                                            requester.getExecutionContext().getExecutor()), client);
                        })
                        .build(executionContext));
    }
}

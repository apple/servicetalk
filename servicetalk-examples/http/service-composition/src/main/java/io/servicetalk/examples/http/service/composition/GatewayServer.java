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
import io.servicetalk.http.api.HttpClient;
import io.servicetalk.http.api.StreamingHttpClient;
import io.servicetalk.http.api.StreamingHttpService;
import io.servicetalk.http.netty.HttpClients;
import io.servicetalk.http.netty.HttpServers;
import io.servicetalk.http.router.predicate.HttpPredicateRouterBuilder;
import io.servicetalk.http.utils.RetryingHttpRequesterFilter;
import io.servicetalk.http.utils.TimeoutHttpRequesterFilter;
import io.servicetalk.transport.api.HostAndPort;
import io.servicetalk.transport.api.IoExecutor;
import io.servicetalk.transport.api.ServerContext;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static io.servicetalk.concurrent.api.AsyncCloseables.newCompositeCloseable;
import static io.servicetalk.examples.http.service.composition.backends.PortRegistry.METADATA_BACKEND_ADDRESS;
import static io.servicetalk.examples.http.service.composition.backends.PortRegistry.RATINGS_BACKEND_ADDRESS;
import static io.servicetalk.examples.http.service.composition.backends.PortRegistry.RECOMMENDATIONS_BACKEND_ADDRESS;
import static io.servicetalk.examples.http.service.composition.backends.PortRegistry.USER_BACKEND_ADDRESS;
import static io.servicetalk.transport.netty.NettyIoExecutors.createIoExecutor;
import static java.time.Duration.ofMillis;
import static java.time.Duration.ofSeconds;

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

            // Create clients for the different backends we are going to use in the gateway.
            StreamingHttpClient recommendationsClient =
                    newClient(ioExecutor, RECOMMENDATIONS_BACKEND_ADDRESS, resources, "recommendations backend");
            HttpClient metadataClient =
                    newClient(ioExecutor, METADATA_BACKEND_ADDRESS, resources, "metadata backend").asClient();
            HttpClient userClient =
                    newClient(ioExecutor, USER_BACKEND_ADDRESS, resources, "user backend").asClient();
            HttpClient ratingsClient =
                    newClient(ioExecutor, RATINGS_BACKEND_ADDRESS, resources, "ratings backend").asClient();

            // Gateway supports different endpoints for blocking, streaming or aggregated implementations.
            // We create a router to express these endpoints.
            HttpPredicateRouterBuilder routerBuilder = new HttpPredicateRouterBuilder();
            final StreamingHttpService gatewayService =
                    routerBuilder.whenPathStartsWith("/recommendations/stream")
                            .thenRouteTo(new StreamingGatewayService(recommendationsClient, metadataClient,
                                    ratingsClient, userClient))
                            .whenPathStartsWith("/recommendations/aggregated")
                            .thenRouteTo(new GatewayService(recommendationsClient.asClient(),
                                    metadataClient, ratingsClient, userClient))
                            .whenPathStartsWith("/recommendations/blocking")
                            .thenRouteTo(new BlockingGatewayService(recommendationsClient.asBlockingClient(),
                                    metadataClient.asBlockingClient(),
                                    ratingsClient.asBlockingClient(),
                                    userClient.asBlockingClient()))
                            .buildStreaming();

            // Create configurable starter for HTTP server.
            // Starting the server will start listening for incoming client requests.
            ServerContext serverContext = HttpServers.forPort(8080)
                    .ioExecutor(ioExecutor)
                    .appendServiceFilter(new BadResponseHandlingServiceFilter())
                    .listenStreamingAndAwait(gatewayService);

            LOGGER.info("Listening on {}", serverContext.listenAddress());

            // Blocks and awaits shutdown of the server this ServerContext represents.
            serverContext.awaitShutdown();
        }
    }

    private static StreamingHttpClient newClient(final IoExecutor ioExecutor,
                                                 final HostAndPort serviceAddress,
                                                 final CompositeCloseable resources,
                                                 final String backendName) {
        return resources.prepend(
                HttpClients.forSingleAddress(serviceAddress)
                        // Set retry and timeout filters for all clients.
                        .appendClientFilter(new RetryingHttpRequesterFilter.Builder()
                                .maxRetries(3)
                                .buildWithExponentialBackoffDeltaJitter(ofMillis(100), ofMillis(50), ofSeconds(30)))
                        // Apply a timeout filter for the client to guard against latent clients.
                        .appendClientFilter(new TimeoutHttpRequesterFilter(ofMillis(500), false))
                        // Apply a filter that returns an error if any response status code is not 200 OK
                        .appendClientFilter(new ResponseCheckingClientFilter(backendName))
                        .ioExecutor(ioExecutor)
                        .buildStreaming());
    }
}

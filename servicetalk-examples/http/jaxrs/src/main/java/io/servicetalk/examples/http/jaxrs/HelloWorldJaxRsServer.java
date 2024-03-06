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
package io.servicetalk.examples.http.jaxrs;

import io.servicetalk.encoding.api.BufferDecoderGroupBuilder;
import io.servicetalk.http.api.ContentEncodingHttpServiceFilter;
import io.servicetalk.http.netty.HttpServers;
import io.servicetalk.http.router.jersey.HttpJerseyRouterBuilder;
import io.servicetalk.logging.api.LogLevel;
import io.servicetalk.transport.api.ServerContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static io.servicetalk.encoding.api.Identity.identityEncoder;
import static io.servicetalk.encoding.netty.NettyBufferEncoders.deflateDefault;
import static io.servicetalk.encoding.netty.NettyBufferEncoders.gzipDefault;
import static java.util.Arrays.asList;

/**
 * A hello world JAX-RS server starter.
 */
public final class HelloWorldJaxRsServer {
    private static final Logger LOGGER = LoggerFactory.getLogger(HelloWorldJaxRsServer.class);

    private HelloWorldJaxRsServer() {
        // No instances.
    }

    /**
     * Starts this server.
     *
     * @param args Program arguments, none supported yet.
     * @throws Exception If the server could not be started.
     */
    public static void main(String[] args) throws Exception {
        // Create configurable starter for HTTP server.
        ServerContext serverContext = HttpServers.forPort(8080)
            .enableWireLogging("SERVER", LogLevel.WARN, () -> true)
            .appendServiceFilter(new ContentEncodingHttpServiceFilter(
                asList(gzipDefault(), deflateDefault(), identityEncoder()),
                new BufferDecoderGroupBuilder()
                    .add(gzipDefault())
                    .add(deflateDefault())
                    .add(identityEncoder(), false).build()))
                .listenStreamingAndAwait(new HttpJerseyRouterBuilder()
                        .buildStreaming(new HelloWorldJaxRsApplication()));

        LOGGER.info("Listening on {}", serverContext.listenAddress());

        // Blocks and awaits shutdown of the server this ServerContext represents.
        serverContext.awaitShutdown();
    }
}

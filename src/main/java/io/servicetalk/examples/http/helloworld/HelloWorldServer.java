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

import io.servicetalk.http.api.HttpServerStarter;
import io.servicetalk.http.netty.DefaultHttpServerStarter;
import io.servicetalk.transport.api.IoExecutor;
import io.servicetalk.transport.api.ServerContext;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static io.servicetalk.concurrent.internal.Await.awaitIndefinitely;
import static io.servicetalk.concurrent.internal.Await.awaitIndefinitelyNonNull;
import static io.servicetalk.transport.netty.NettyIoExecutors.createIoExecutor;

/**
 * A hello world server starter.
 */
public final class HelloWorldServer {
    private static final Logger LOGGER = LoggerFactory.getLogger(HelloWorldServer.class);

    private HelloWorldServer() {
        // No instances.
    }

    /**
     * Starts this server.
     *
     * @param args Program arguments, none supported yet.
     * @throws Exception If the server could not be started.
     */
    public static void main(String[] args) throws Exception {
        // Shared IoExecutor for the application.
        IoExecutor ioExecutor = createIoExecutor();
        try {
            HttpServerStarter starter = new DefaultHttpServerStarter(ioExecutor);

            // Starting the server will start listening for incoming client requests.
            ServerContext serverContext = awaitIndefinitelyNonNull(starter.start(8080, new HelloWorldService()));

            LOGGER.info("listening on {}", serverContext.getListenAddress());

            // Stop listening/accepting more sockets and gracefully shutdown all open sockets.
            awaitIndefinitely(serverContext.onClose());
        } finally {
            awaitIndefinitely(ioExecutor.closeAsync());
        }
    }
}

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
package io.servicetalk.examples.http.helloworld.async.streaming;

import io.servicetalk.http.netty.DefaultHttpServerStarter;
import io.servicetalk.transport.api.ServerContext;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static io.servicetalk.concurrent.internal.Await.awaitIndefinitely;
import static io.servicetalk.concurrent.internal.Await.awaitIndefinitelyNonNull;

/**
 * A hello world server starter.
 */
public final class HelloWorldStreamingServer {
    private static final Logger LOGGER = LoggerFactory.getLogger(HelloWorldStreamingServer.class);

    private HelloWorldStreamingServer() {
        // No instances.
    }

    /**
     * Starts this server.
     *
     * @param args Program arguments, none supported yet.
     * @throws Exception If the server could not be started.
     */
    public static void main(String[] args) throws Exception {
        // Starting the server will start listening for incoming client requests.
        ServerContext serverContext = awaitIndefinitelyNonNull(
                new DefaultHttpServerStarter().start(8080, new HelloWorldStreamingService()));

        LOGGER.info("listening on {}", serverContext.getListenAddress());

        // Stop listening/accepting more sockets and gracefully shutdown all open sockets.
        awaitIndefinitely(serverContext.onClose());
    }
}

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
import io.servicetalk.http.netty.NettyHttpServerStarter;
import io.servicetalk.transport.api.IoExecutor;
import io.servicetalk.transport.api.ServerContext;
import io.servicetalk.transport.netty.NettyIoExecutors;

import java.net.InetSocketAddress;

import static io.servicetalk.concurrent.internal.Await.awaitIndefinitely;

/**
 * A hello world server starter using a {@link HelloWorldBlockingService}.
 */
public final class HelloWorldBlockingServer {

    private HelloWorldBlockingServer() {
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
        IoExecutor ioExecutor = NettyIoExecutors.createExecutor();
        try {
            HttpServerStarter starter = new NettyHttpServerStarter(ioExecutor);
            // Note that ServiceTalk is safe to block by default. An Application Executor is created by default and is
            // used to execute user code. The Executor can be manually created and shared if desirable too.
            ServerContext serverContext = awaitIndefinitely(starter.start(
                    new InetSocketAddress(8080),
                    new HelloWorldBlockingService().asAsynchronousService()));
            assert serverContext != null;
            awaitIndefinitely(serverContext.onClose());
        } finally {
            awaitIndefinitely(ioExecutor.closeAsync());
        }
    }
}

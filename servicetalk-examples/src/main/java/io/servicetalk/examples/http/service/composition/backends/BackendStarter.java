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
package io.servicetalk.examples.http.service.composition.backends;

import io.servicetalk.concurrent.api.CompositeCloseable;
import io.servicetalk.http.api.HttpService;
import io.servicetalk.http.api.StreamingHttpService;
import io.servicetalk.transport.api.IoExecutor;
import io.servicetalk.transport.api.ServerContext;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static io.servicetalk.concurrent.api.Executors.newCachedThreadExecutor;
import static io.servicetalk.http.netty.HttpServers.newHttpServerBuilder;
import static java.util.Objects.requireNonNull;

/**
 * A simple class that starts an HTTP server for this example using an {@link HttpService}.
 */
final class BackendStarter {

    private static final Logger LOGGER = LoggerFactory.getLogger(BackendStarter.class);

    private final IoExecutor ioExecutor;
    private final CompositeCloseable resources;

    BackendStarter(IoExecutor ioExecutor, CompositeCloseable resources) {
        this.ioExecutor = requireNonNull(ioExecutor);
        this.resources = requireNonNull(resources);
    }

    ServerContext start(int listenPort, String name, StreamingHttpService service) throws Exception {
        // Starting the server will start listening for incoming client requests.
        final ServerContext ctx = newHttpServerBuilder(listenPort)
                .ioExecutor(ioExecutor)
                .executor(newCachedThreadExecutor())
                .listenStreamingAndAwait(service);
        LOGGER.info("Started {} listening on {}.", name, ctx.listenAddress());
        return ctx;
    }

    ServerContext start(int listenPort, String name, HttpService service) throws Exception {
        return start(listenPort, name, service.asStreamingService());
    }
}

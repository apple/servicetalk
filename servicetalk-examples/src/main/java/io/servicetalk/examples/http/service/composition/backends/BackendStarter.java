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
import io.servicetalk.http.api.AggregatedHttpService;
import io.servicetalk.http.api.HttpService;
import io.servicetalk.http.netty.DefaultHttpServerStarter;
import io.servicetalk.transport.api.DefaultExecutionContext;
import io.servicetalk.transport.api.ExecutionContext;
import io.servicetalk.transport.api.IoExecutor;
import io.servicetalk.transport.api.ServerContext;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.ExecutionException;

import static io.servicetalk.buffer.netty.BufferAllocators.DEFAULT_ALLOCATOR;
import static io.servicetalk.concurrent.api.Executors.newCachedThreadExecutor;
import static io.servicetalk.concurrent.internal.Await.awaitIndefinitelyNonNull;
import static java.util.Objects.requireNonNull;

/**
 * A simple class that starts an HTTP server for this example using an {@link AggregatedHttpService}.
 */
final class BackendStarter {

    private static final Logger LOGGER = LoggerFactory.getLogger(BackendStarter.class);

    private final IoExecutor ioExecutor;
    private final CompositeCloseable resources;
    private final DefaultHttpServerStarter starter;

    BackendStarter(IoExecutor ioExecutor, CompositeCloseable resources) {
        this.ioExecutor = requireNonNull(ioExecutor);
        this.resources = requireNonNull(resources);
        // Create configurable starter for HTTP server.
        starter = new DefaultHttpServerStarter();
    }

    ServerContext start(int listenPort, String name, HttpService service)
            throws ExecutionException, InterruptedException {
        // Create ExecutionContext for this ServerContext with new Executor.
        final ExecutionContext executionContext = new DefaultExecutionContext(DEFAULT_ALLOCATOR,
                ioExecutor, resources.prepend(newCachedThreadExecutor()));
        // Starting the server will start listening for incoming client requests.
        final ServerContext ctx = awaitIndefinitelyNonNull(starter.start(executionContext, listenPort, service));
        LOGGER.info("Started {} listening on {}.", name, ctx.getListenAddress());
        return ctx;
    }

    ServerContext start(int listenPort, String name, AggregatedHttpService service)
            throws ExecutionException, InterruptedException {
        return start(listenPort, name, service.asService());
    }
}

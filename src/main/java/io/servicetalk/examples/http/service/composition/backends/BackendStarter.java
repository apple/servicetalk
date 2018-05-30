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

import io.servicetalk.http.api.AggregatedHttpService;
import io.servicetalk.http.api.HttpService;
import io.servicetalk.http.netty.DefaultHttpServerStarter;
import io.servicetalk.transport.api.IoExecutor;
import io.servicetalk.transport.api.ServerContext;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.ExecutionException;

import static io.servicetalk.concurrent.internal.Await.awaitIndefinitelyNonNull;

/**
 * A simple class that starts an HTTP server for this example using an {@link AggregatedHttpService}.
 */
final class BackendStarter {

    private static final Logger LOGGER = LoggerFactory.getLogger(BackendStarter.class);

    private final DefaultHttpServerStarter starter;

    BackendStarter(IoExecutor ioExecutor) {
        starter = new DefaultHttpServerStarter(ioExecutor);
    }

    ServerContext start(int listenPort, String name, HttpService service)
            throws ExecutionException, InterruptedException {
        starter.setWireLoggerName(name);
        final ServerContext ctx = awaitIndefinitelyNonNull(starter.start(listenPort, service));
        LOGGER.info("Started {} listening on {}.", name, ctx.getListenAddress());
        return ctx;
    }

    ServerContext start(int listenPort, String name, AggregatedHttpService service)
            throws ExecutionException, InterruptedException {
        return start(listenPort, name, service.asService());
    }
}

/*
 * Copyright © 2021 Apple Inc. and the ServiceTalk project authors
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
package io.servicetalk.examples.http.opentracing;

import io.servicetalk.http.netty.HttpClients;
import io.servicetalk.http.netty.HttpServers;
import io.servicetalk.opentracing.http.TracingHttpServiceFilter;
import io.servicetalk.opentracing.inmemory.DefaultInMemoryTracer;
import io.servicetalk.opentracing.zipkin.publisher.ZipkinPublisher;
import io.servicetalk.opentracing.zipkin.publisher.reporter.HttpReporter;

import io.opentracing.Tracer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.InetSocketAddress;

import static io.servicetalk.opentracing.asynccontext.AsyncContextInMemoryScopeManager.SCOPE_MANAGER;

/**
 * A server that does distributed tracing.
 */
public final class OpenTracingServer {
    private static final Logger LOGGER = LoggerFactory.getLogger(OpenTracingServer.class);

    public static void main(String[] args) throws Exception {
        // Publishing to Zipkin is optional, but demonstrated for completeness.
        final InetSocketAddress bindAddress = new InetSocketAddress(8080);
        final String serviceName = "servicetalk-test-server";
        try (ZipkinPublisher zipkinPublisher = new ZipkinPublisher.Builder(serviceName,
                // Use ServiceTalk's HTTP client to publish spans to Zipkin's HTTP API (run ZipkinServerSimulator).
                new HttpReporter.Builder(HttpClients.forSingleAddress("localhost", 8081)).build())
                .localAddress(bindAddress)
                .build();
             Tracer tracer = new DefaultInMemoryTracer.Builder(SCOPE_MANAGER).addListener(zipkinPublisher).build()) {
            HttpServers.forAddress(bindAddress)
                    .appendServiceFilter(new TracingHttpServiceFilter(tracer, serviceName))
                    .listenBlockingAndAwait((ctx, request, responseFactory) -> {
                        LOGGER.info("processed request {}", request.toString((name, value) -> value));
                        return responseFactory.ok();
                    }).awaitShutdown();
        }
    }
}

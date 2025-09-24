/*
 * Copyright © 2025 Apple Inc. and the ServiceTalk project authors
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
package io.servicetalk.examples.http.opentelemetry;

import io.servicetalk.http.netty.HttpServers;
import io.servicetalk.http.utils.HttpRequestAutoDrainingServiceFilter;
import io.servicetalk.opentelemetry.http.OpenTelemetryHttpServiceFilter;

import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.exporter.logging.LoggingSpanExporter;
import io.opentelemetry.sdk.OpenTelemetrySdk;
import io.opentelemetry.sdk.trace.SdkTracerProvider;
import io.opentelemetry.sdk.trace.export.BatchSpanProcessor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A server that demonstrates OpenTelemetry distributed tracing with ServiceTalk.
 * This example shows how to:
 * <ul>
 *   <li>Configure OpenTelemetry SDK with logging exporter</li>
 *   <li>Set up ServiceTalk HTTP server with OpenTelemetry tracing filter</li>
 *   <li>Demonstrate proper filter ordering for accurate tracing</li>
 *   <li>Automatically capture HTTP request/response spans</li>
 * </ul>
 */
public final class OpenTelemetryServer {
    private static final Logger LOGGER = LoggerFactory.getLogger(OpenTelemetryServer.class);

    public static void main(String[] args) throws Exception {
        // Configure OpenTelemetry SDK
        final OpenTelemetry openTelemetry = OpenTelemetrySdk.builder()
                .setTracerProvider(SdkTracerProvider.builder()
                        .addSpanProcessor(BatchSpanProcessor.builder(LoggingSpanExporter.create()).build())
                        .build())
                .build();

        // Set the global OpenTelemetry instance
        GlobalOpenTelemetry.set(openTelemetry);

        HttpServers.forPort(8080)
                // CRITICAL: OpenTelemetry filter MUST be first for proper context propagation
                // Use non-offloading filter to maintain async context across threads
                .appendNonOffloadingServiceFilter(new OpenTelemetryHttpServiceFilter.Builder()
                        .build())

                // IMPORTANT: Request draining filter MUST come after OpenTelemetry filter
                // This ensures tracing information is captured for auto-drained requests (e.g., GET requests)
                // If auto-draining occurs before OpenTelemetry filter processes the request,
                // tracing information may be incomplete or incorrect
                .appendNonOffloadingServiceFilter(HttpRequestAutoDrainingServiceFilter.INSTANCE)

                // Other filters can be added after the critical ordering above
                // Exception mapping, logging, etc. should come after OpenTelemetry and request draining
                .listenBlockingAndAwait((ctx, request, responseFactory) -> {
                    LOGGER.info("Processing request: {} {}", request.method(), request.requestTarget());

                    // Simulate some processing work
                    Thread.sleep(50);

                    return responseFactory.ok()
                            .addHeader("content-type", "text/plain")
                            .payloadBody(ctx.executionContext().bufferAllocator()
                                    .fromAscii("Hello from OpenTelemetry server!"));
                }).awaitShutdown();
    }
}

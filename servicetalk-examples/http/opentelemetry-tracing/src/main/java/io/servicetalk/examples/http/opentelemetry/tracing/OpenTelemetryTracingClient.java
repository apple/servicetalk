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
package io.servicetalk.examples.http.opentelemetry.tracing;

import io.servicetalk.http.api.BlockingHttpClient;
import io.servicetalk.http.api.HttpResponse;
import io.servicetalk.http.netty.HttpClients;
import io.servicetalk.opentelemetry.http.OpenTelemetryHttpRequesterFilter;

import java.nio.charset.StandardCharsets;
import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.exporter.logging.LoggingSpanExporter;
import io.opentelemetry.sdk.OpenTelemetrySdk;
import io.opentelemetry.sdk.trace.SdkTracerProvider;
import io.opentelemetry.sdk.trace.export.BatchSpanProcessor;

/**
 * A client that demonstrates OpenTelemetry distributed tracing with ServiceTalk.
 * This example shows how to:
 * <ul>
 *   <li>Configure OpenTelemetry SDK with logging exporter</li>
 *   <li>Set up ServiceTalk HTTP client with OpenTelemetry tracing filter</li>
 *   <li>Demonstrate proper client-side filter ordering</li>
 *   <li>Automatically capture HTTP request/response spans</li>
 *   <li>Propagate trace context to downstream services</li>
 * </ul>
 */
public final class OpenTelemetryTracingClient {
    public static void main(String[] args) throws Exception {
        // Configure OpenTelemetry SDK
        final String serviceName = "servicetalk-example-client";

        final OpenTelemetry openTelemetry = OpenTelemetrySdk.builder()
                .setTracerProvider(SdkTracerProvider.builder()
                        .addSpanProcessor(BatchSpanProcessor.builder(LoggingSpanExporter.create()).build())
                        .build())
                .build();

        // Set the global OpenTelemetry instance
        GlobalOpenTelemetry.set(openTelemetry);

        OpenTelemetryHttpRequesterFilter filter = new OpenTelemetryHttpRequesterFilter.Builder()
                .componentName(serviceName)
                .build();

        try (BlockingHttpClient client = HttpClients.forSingleAddress("localhost", 8080)
                // IMPORTANT: OpenTelemetry filter should be placed EARLY in the filter chain
                // This ensures proper span creation and context propagation for downstream filters
                .appendClientFilter(filter)
                // Optional: adding the filter a second time as a connection filter will enable 'physical spans'
                // where there will be a higher level 'logical' span and a sub-physical span for each
                // request sent over the wire.
                .appendConnectionFilter(filter)
                .buildBlocking()) {

            System.out.println("Making first request...");
            HttpResponse response1 = client.request(client.get("/hello"));
            System.out.println("Response 1: " + response1.toString((name, value) -> value));
            System.out.println("Body 1: " + response1.payloadBody().toString(StandardCharsets.UTF_8));

            System.out.println("\nMaking second request...");
            HttpResponse response2 = client.request(client.get("/world"));
            System.out.println("Response 2: " + response2.toString((name, value) -> value));
            System.out.println("Body 2: " + response2.payloadBody().toString(StandardCharsets.UTF_8));

        }
    }
}

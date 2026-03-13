/*
 * Copyright © 2026 Apple Inc. and the ServiceTalk project authors
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
package io.servicetalk.examples.http.opentelemetry.jaeger;

import io.servicetalk.http.api.BlockingHttpClient;
import io.servicetalk.http.api.HttpResponse;
import io.servicetalk.http.api.HttpServerContext;
import io.servicetalk.http.netty.HttpClients;
import io.servicetalk.http.netty.HttpServers;
import io.servicetalk.opentelemetry.http.OpenTelemetryHttpRequesterFilter;
import io.servicetalk.opentelemetry.http.OpenTelemetryHttpServiceFilter;
import io.servicetalk.test.resources.DefaultTestCerts;

import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.exporter.otlp.http.trace.OtlpHttpSpanExporter;
import io.opentelemetry.exporter.otlp.http.trace.OtlpHttpSpanExporterBuilder;
import io.opentelemetry.exporter.otlp.trace.OtlpGrpcSpanExporter;
import io.opentelemetry.exporter.otlp.trace.OtlpGrpcSpanExporterBuilder;
import io.opentelemetry.sdk.OpenTelemetrySdk;
import io.opentelemetry.sdk.resources.Resource;
import io.opentelemetry.sdk.trace.SdkTracerProvider;
import io.opentelemetry.sdk.trace.export.BatchSpanProcessor;
import io.opentelemetry.sdk.trace.export.SpanExporter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.security.KeyStore;
import java.time.Duration;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManagerFactory;
import javax.net.ssl.X509TrustManager;

import static io.servicetalk.http.api.HttpSerializationProviders.textDeserializer;
import static io.servicetalk.http.api.HttpSerializationProviders.textSerializer;

/**
 * End-to-end example demonstrating ServiceTalk's OpenTelemetry integration with Jaeger.
 * <p>
 * This example shows:
 * <ul>
 *   <li><strong>Automatic SPI Discovery</strong>: ServiceTalk's HttpSender implementation is
 *       automatically discovered and used by OpenTelemetry SDK without explicit configuration</li>
 *   <li><strong>OTLP over HTTP</strong>: Traces are exported to Jaeger using OTLP/HTTP protocol
 *       with ServiceTalk as the underlying transport</li>
 *   <li><strong>mTLS Security</strong>: Demonstrates mutual TLS authentication with client certificates</li>
 *   <li><strong>Distributed Tracing</strong>: Client and server spans with proper parent-child relationships</li>
 *   <li><strong>Manual Instrumentation</strong>: Creating custom spans alongside automatic HTTP spans</li>
 * </ul>
 * <p>
 * <strong>Prerequisites:</strong>
 * <ol>
 *   <li>Generate TLS certificates (run from example directory):
 *   <pre>
 *   ./generate-certs.sh
 *   </pre>
 *   </li>
 *   <li>Start Jaeger and OpenTelemetry Collector with mTLS:
 *   <pre>
 *   docker-compose up -d
 *   </pre>
 *   </li>
 *   <li>Run this example</li>
 *   <li>View traces in Jaeger UI: <a href="http://localhost:16686">http://localhost:16686</a></li>
 *   <li>Stop services:
 *   <pre>
 *   docker-compose down
 *   </pre>
 *   </li>
 * </ol>
 * <p>
 * <strong>Note:</strong> Set {@code USE_MTLS = false} to run without mTLS (requires plain HTTP collector).
 */
public final class JaegerOtlpExample {

    private static final Logger LOGGER = LoggerFactory.getLogger(JaegerOtlpExample.class);
    private static final char[] PASSWORD = "changeit".toCharArray();
    private static final String SERVICE_NAME = "servicetalk-jaeger-example";
    private static final boolean USE_GRPC = true;
    private static final boolean USE_MTLS = false;  // Set to false for plain HTTP
    private static final String JAEGER_OTLP_HTTP_ENDPOINT = (USE_MTLS ? "https" : "http") + "://localhost:4318/v1/traces";

    private JaegerOtlpExample() {
        // no instances.
    }

    public static void main(String[] args) throws Exception {
        // Configure OpenTelemetry SDK with OTLP exporter
        // The ServiceTalk HTTP transport will be automatically discovered via SPI
        LOGGER.info("Configuring OpenTelemetry with OTLP exporter...");
        LOGGER.info("ServiceTalk HTTP transport will be automatically discovered via SPI");

        GlobalOpenTelemetry.set(configureOpenTelemetry());

        // Start a simple HTTP server
        LOGGER.info("Starting HTTP server on port 8080...");
        HttpServerContext serverContext = startServer();

        try {
            LOGGER.info("Server started successfully");
            LOGGER.info("======================================================================");
            LOGGER.info("Making HTTP requests to generate trace data...");
            LOGGER.info("======================================================================");
            // Create HTTP client and make requests
            makeRequestsWithTracing();

            // Give time for spans to be exported
            LOGGER.info("Waiting for spans to be exported to Jaeger...");
            Thread.sleep(3000);

            LOGGER.info("======================================================================");
            LOGGER.info("Example completed successfully!");
            LOGGER.info("View traces in Jaeger UI: http://localhost:16686");
            LOGGER.info("Search for service: {}", SERVICE_NAME);
            LOGGER.info("======================================================================");
        } finally {
            LOGGER.debug("Closing server context");
            serverContext.close();
            // Shutdown OpenTelemetry to flush remaining spans
            OpenTelemetry openTelemetry = GlobalOpenTelemetry.get();
            if (openTelemetry instanceof OpenTelemetrySdk) {
                LOGGER.debug("Shutting down OpenTelemetry SDK");
                ((OpenTelemetrySdk) openTelemetry).close();
            }
        }
    }

    private static OpenTelemetry configureOpenTelemetry() throws Exception {
        LOGGER.debug("Creating OpenTelemetry resource with service name: {}", SERVICE_NAME);
        // Create resource with service name
        Resource resource = Resource.getDefault()
                .merge(Resource.create(Attributes.of(
                        AttributeKey.stringKey("service.name"), SERVICE_NAME,
                        AttributeKey.stringKey("service.version"), "1.0.0"
                )));

        SpanExporter spanExporter = buildSpanExporter();

        // Create tracer provider with batch span processor
        LOGGER.debug("Creating tracer provider with batch span processor");
        SdkTracerProvider tracerProvider = SdkTracerProvider.builder()
                .setResource(resource)
                .addSpanProcessor(BatchSpanProcessor.builder(spanExporter)
                        .setScheduleDelay(Duration.ofMillis(500))
                        .build())
                .build();

        // Build and return OpenTelemetry SDK
        LOGGER.debug("Building OpenTelemetry SDK");
        return OpenTelemetrySdk.builder()
                .setTracerProvider(tracerProvider)
                .build();
    }

    private static SpanExporter buildSpanExporter() throws Exception {
        return USE_GRPC ? buildGrpcExporter() : buildHttpExporter();
    }

    private static SpanExporter buildGrpcExporter() throws Exception {
        OtlpGrpcSpanExporterBuilder exporterBuilder = OtlpGrpcSpanExporter.builder()
                .setTimeout(Duration.ofSeconds(10));

        // Configure mTLS if enabled
        if (USE_MTLS) {
            LOGGER.info("Configuring mTLS for OTLP exporter...");
            SSLContext sslContext = createMtlsSslContext();
            exporterBuilder.setSslContext(sslContext, createTrustManager());
            LOGGER.info("mTLS configuration complete");
        }

        return exporterBuilder.build();
    }

    private static SpanExporter buildHttpExporter() throws Exception {
        OtlpHttpSpanExporterBuilder exporterBuilder = OtlpHttpSpanExporter.builder()
                .setEndpoint(JAEGER_OTLP_HTTP_ENDPOINT)
                .setTimeout(Duration.ofSeconds(10));

        // Configure mTLS if enabled
        if (USE_MTLS) {
            LOGGER.info("Configuring mTLS for OTLP exporter...");
            SSLContext sslContext = createMtlsSslContext();
            exporterBuilder.setSslContext(sslContext, createTrustManager());
            LOGGER.info("mTLS configuration complete");
        }

        return exporterBuilder.build();
    }

    private static SSLContext createMtlsSslContext() throws Exception {
        KeyStore trustStore = KeyStore.getInstance(KeyStore.getDefaultType());
        trustStore.load(DefaultTestCerts.loadTruststoreP12(), PASSWORD);

        TrustManagerFactory trustManagerFactory = TrustManagerFactory.getInstance(
                TrustManagerFactory.getDefaultAlgorithm());
        trustManagerFactory.init(trustStore);
        LOGGER.debug("Trust store configured with CA certificate");

        // Create key store with client certificate and private key
        final KeyStore keyStore = KeyStore.getInstance("pkcs12");
        keyStore.load(DefaultTestCerts.loadClientP12(), PASSWORD);

        KeyManagerFactory keyManagerFactory = KeyManagerFactory.getInstance(
                KeyManagerFactory.getDefaultAlgorithm());
        keyManagerFactory.init(keyStore, PASSWORD);
        LOGGER.debug("Key store configured with client certificate and private key");

        // Create SSL context
        SSLContext sslContext = SSLContext.getInstance("TLS");
        sslContext.init(keyManagerFactory.getKeyManagers(),
                trustManagerFactory.getTrustManagers(), null);
        LOGGER.debug("SSL context created with mTLS configuration");
        return sslContext;
    }

    /**
     * Creates a TrustManager for the CA certificate.
     */
    private static X509TrustManager createTrustManager() throws Exception {
        TrustManagerFactory trustManagerFactory = TrustManagerFactory.getInstance(
                TrustManagerFactory.getDefaultAlgorithm());
        KeyStore trustStore = KeyStore.getInstance(KeyStore.getDefaultType());
        trustStore.load(DefaultTestCerts.loadTruststoreP12(), PASSWORD);
        trustManagerFactory.init(trustStore);
        return (X509TrustManager) trustManagerFactory.getTrustManagers()[0];
    }

    private static HttpServerContext startServer() throws Exception {
        LOGGER.debug("Creating OpenTelemetry HTTP service filter");
        OpenTelemetryHttpServiceFilter serverFilter = new OpenTelemetryHttpServiceFilter.Builder()
                .build();

        LOGGER.debug("Building HTTP server for port 8080");
        return HttpServers.forPort(8080)
                .appendServiceFilter(serverFilter)
                .listenBlockingAndAwait((ctx, request, responseFactory) -> {
                    String path = request.path();
                    LOGGER.debug("Server received request: {}", path);

                    // Add some processing delay to make spans more visible
                    Thread.sleep(50);

                    if ("/hello".equals(path)) {
                        return responseFactory.ok()
                                .payloadBody("Hello from ServiceTalk!", textSerializer());
                    } else if ("/world".equals(path)) {
                        return responseFactory.ok()
                                .payloadBody("World response from ServiceTalk!", textSerializer());
                    } else {
                        return responseFactory.ok()
                                .payloadBody("Default response", textSerializer());
                    }
                });
    }

    private static void makeRequestsWithTracing() throws Exception {
        LOGGER.debug("Getting tracer for service: {}", SERVICE_NAME);
        Tracer tracer = GlobalOpenTelemetry.get().getTracer(SERVICE_NAME);

        LOGGER.debug("Creating OpenTelemetry HTTP requester filter");
        OpenTelemetryHttpRequesterFilter clientFilter = new OpenTelemetryHttpRequesterFilter.Builder()
                .componentName(SERVICE_NAME + "-client")
                .build();

        LOGGER.debug("Building HTTP client");
        try (BlockingHttpClient client = HttpClients.forSingleAddress("localhost", 8080)
                .appendClientFilter(clientFilter)
                .buildBlocking()) {

            // Request 1: Simple GET to /hello
            LOGGER.info("1. Making GET request to /hello...");
            Span span1 = tracer.spanBuilder("request-1-workflow")
                    .setAttribute("workflow.step", "initial")
                    .startSpan();

            try {
                HttpResponse response1 = client.request(client.get("/hello"));
                String body1 = response1.payloadBody(textDeserializer());
                LOGGER.info("   Response: {} - {}", response1.status(), body1);
                span1.addEvent("received-response");
            } finally {
                span1.end();
            }

            Thread.sleep(100);

            // Request 2: GET to /world with custom span
            LOGGER.info("2. Making GET request to /world...");
            Span span2 = tracer.spanBuilder("request-2-workflow")
                    .setAttribute("workflow.step", "secondary")
                    .startSpan();

            try {
                HttpResponse response2 = client.request(client.get("/world"));
                String body2 = response2.payloadBody(textDeserializer());
                LOGGER.info("   Response: {} - {}", response2.status(), body2);
                span2.addEvent("received-response");
            } finally {
                span2.end();
            }

            Thread.sleep(100);

            // Request 3: Demonstrate nested spans
            LOGGER.info("3. Making request with nested spans...");
            Span parentSpan = tracer.spanBuilder("complex-workflow")
                    .setAttribute("workflow.type", "multi-step")
                    .startSpan();

            try {
                // Child span for preparation
                LOGGER.debug("Creating preparation span");
                Span prepSpan = tracer.spanBuilder("prepare-request")
                        .setParent(io.opentelemetry.context.Context.current().with(parentSpan))
                        .startSpan();
                try {
                    Thread.sleep(50);
                    prepSpan.addEvent("preparation-complete");
                } finally {
                    prepSpan.end();
                }

                // Make the actual request
                HttpResponse response3 = client.request(client.get("/hello"));
                String body3 = response3.payloadBody(textDeserializer());
                LOGGER.info("   Response: {} - {}", response3.status(), body3);

                // Child span for post-processing
                LOGGER.debug("Creating post-processing span");
                Span postSpan = tracer.spanBuilder("process-response")
                        .setParent(io.opentelemetry.context.Context.current().with(parentSpan))
                        .startSpan();
                try {
                    Thread.sleep(50);
                    postSpan.setAttribute("response.length", body3.length());
                    postSpan.addEvent("processing-complete");
                } finally {
                    postSpan.end();
                }
            } finally {
                parentSpan.end();
            }

            LOGGER.info("✓ All requests completed successfully");
            LOGGER.info("  Generated spans:");
            LOGGER.info("  - 3 workflow spans (custom instrumentation)");
            LOGGER.info("  - 3 HTTP client spans (automatic)");
            LOGGER.info("  - 3 HTTP server spans (automatic)");
            LOGGER.info("  - 2 nested preparation/processing spans");
        }
    }
}

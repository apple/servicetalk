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
package io.servicetalk.opentelemetry.client;

import io.servicetalk.test.resources.DefaultTestCerts;

import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.exporter.otlp.trace.OtlpGrpcSpanExporter;
import io.opentelemetry.proto.trace.v1.ResourceSpans;
import io.opentelemetry.sdk.OpenTelemetrySdk;
import io.opentelemetry.sdk.common.export.GrpcSenderProvider;
import io.opentelemetry.sdk.trace.export.SpanExporter;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.ServiceLoader;
import java.util.concurrent.TimeUnit;
import javax.annotation.Nullable;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManagerFactory;
import javax.net.ssl.X509TrustManager;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.isA;
import static org.hamcrest.Matchers.notNullValue;

final class GrpcSenderIntegrationTest {

    @Nullable
    private MockOtlpCollector collector;
    @Nullable
    private OpenTelemetrySdk openTelemetry;

    @AfterEach
    void tearDown() throws Exception {
        if (openTelemetry != null) {
            openTelemetry.close();
        }
        if (collector != null) {
            collector.close();
        }
    }

    @Test
    void onlyServiceTalkGrpcProvidersOnClassPath() {
        // Verify ServiceTalk GrpcSender is available
        ServiceLoader<GrpcSenderProvider> loader = ServiceLoader.load(GrpcSenderProvider.class);
        List<GrpcSenderProvider> results = new ArrayList<>();
        for (GrpcSenderProvider spi : loader) {
            results.add(spi);
        }
        assertThat(results, hasSize(1));
        assertThat(results.get(0), isA(ServiceTalkGrpcSenderProvider.class));
    }

    @Test
    void cleartextGrpcSendsSpansToCollector() throws Exception {
        collector = new MockOtlpCollector.Builder()
                .protocolMode(MockOtlpCollector.ProtocolMode.GRPC)
                .build();

        OtlpGrpcSpanExporter spanExporter = OtlpGrpcSpanExporter.builder()
                .setEndpoint("http://localhost:" + collector.getPort())
                .build();

        exportSpan(spanExporter, "cleartext-grpc-test-span");

        assertGrpcSpanReceived();
    }

    @Test
    void tlsGrpcSendsSpansToCollector() throws Exception {
        collector = new MockOtlpCollector.Builder()
                .protocolMode(MockOtlpCollector.ProtocolMode.GRPC)
                .securityMode(MockOtlpCollector.SecurityMode.TLS)
                .build();

        TrustManagerFactory tmf = TestUtils.createTrustManagerFactory(DefaultTestCerts::loadServerCAPem);
        SSLContext sslContext = TestUtils.createTlsSslContext(tmf);
        X509TrustManager trustManager = TestUtils.extractTrustManager(tmf);

        OtlpGrpcSpanExporter spanExporter = OtlpGrpcSpanExporter.builder()
                .setEndpoint("https://" + DefaultTestCerts.serverPemHostname() + ":" + collector.getPort())
                .setSslContext(sslContext, trustManager)
                .build();

        exportSpan(spanExporter, "tls-grpc-test-span");

        assertGrpcSpanReceived();
    }

    @Test
    void mutualTlsGrpcSendsSpansToCollector() throws Exception {
        collector = new MockOtlpCollector.Builder()
                .protocolMode(MockOtlpCollector.ProtocolMode.GRPC)
                .securityMode(MockOtlpCollector.SecurityMode.MUTUAL_TLS)
                .build();

        TrustManagerFactory tmf = TestUtils.createTrustManagerFactory(DefaultTestCerts::loadServerCAPem);
        KeyManagerFactory kmf = TestUtils.createKeyManagerFactory(
                DefaultTestCerts::loadClientPem, DefaultTestCerts::loadClientKey);
        SSLContext sslContext = TestUtils.createMutualTlsSslContext(tmf, kmf);
        X509TrustManager trustManager = TestUtils.extractTrustManager(tmf);

        OtlpGrpcSpanExporter spanExporter = OtlpGrpcSpanExporter.builder()
                .setEndpoint("https://" + DefaultTestCerts.serverPemHostname() + ":" + collector.getPort())
                .setSslContext(sslContext, trustManager)
                .build();

        exportSpan(spanExporter, "mtls-grpc-test-span");

        assertGrpcSpanReceived();
    }

    @Test
    void mutualTlsRejectsClientWithoutCertificate() throws Exception {
        collector = new MockOtlpCollector.Builder()
                .protocolMode(MockOtlpCollector.ProtocolMode.GRPC)
                .securityMode(MockOtlpCollector.SecurityMode.MUTUAL_TLS)
                .build();

        // Client configured with server trust only — no client certificate presented.
        TrustManagerFactory tmf = TestUtils.createTrustManagerFactory(DefaultTestCerts::loadServerCAPem);
        SSLContext sslContext = TestUtils.createTlsSslContext(tmf);
        X509TrustManager trustManager = TestUtils.extractTrustManager(tmf);

        OtlpGrpcSpanExporter spanExporter = OtlpGrpcSpanExporter.builder()
                .setEndpoint("https://" + DefaultTestCerts.serverPemHostname() + ":" + collector.getPort())
                .setSslContext(sslContext, trustManager)
                .build();

        exportSpan(spanExporter, "rejected-span");

        assertThat("mTLS server should reject a client that presents no certificate",
                collector.getRequestCount(), is(0));
    }

    @Test
    void grpcMessageIsProperlyFramedAndDecoded() throws Exception {
        collector = new MockOtlpCollector.Builder()
                .protocolMode(MockOtlpCollector.ProtocolMode.GRPC)
                .build();

        OtlpGrpcSpanExporter spanExporter = OtlpGrpcSpanExporter.builder()
                .setEndpoint("http://localhost:" + collector.getPort())
                .build();

        String spanName = "test-span-for-framing-and-decoding";
        exportSpan(spanExporter, spanName);

        // Wait for request to be received
        boolean received = TestUtils.waitFor(() -> collector.getRequestCount() > 0, 5_000, 100);
        assertThat("Collector should have received at least one request", received, is(true));

        MockOtlpCollector.ReceivedRequest request = collector.getReceivedRequests().get(0);

        // Verify it's a gRPC request
        assertThat("Request should be identified as gRPC", request.isGrpc(), is(true));

        // Verify gRPC frame metadata is present and valid
        MockOtlpCollector.GrpcFrameMetadata metadata = request.getGrpcMetadata();
        assertThat("gRPC metadata should be present", metadata, notNullValue());
        assertThat("Message should not be compressed by default", metadata.isCompressed(), is(false));
        assertThat("Message length should be positive", metadata.getMessageLength(), greaterThan(0));
        assertThat("Decoded message should be present", request.getGrpcMessage(), notNullValue());

        assertThat("Export request should have resource spans",
                request.getGrpcMessage().getResourceSpansCount(), greaterThan(0));

        ResourceSpans resourceSpans = request.getGrpcMessage().getResourceSpans(0);
        assertThat("Resource spans should have scope spans",
                resourceSpans.getScopeSpansCount(), greaterThan(0));

        io.opentelemetry.proto.trace.v1.ScopeSpans scopeSpans = resourceSpans.getScopeSpans(0);
        assertThat("Scope spans should have spans",
                scopeSpans.getSpansCount(), greaterThan(0));

        io.opentelemetry.proto.trace.v1.Span span = scopeSpans.getSpans(0);
        assertThat("Span name should match what was sent", span.getName(), equalTo(spanName));
    }

    @Test
    void grpcPathIsCorrect() throws Exception {
        collector = new MockOtlpCollector.Builder()
                .protocolMode(MockOtlpCollector.ProtocolMode.GRPC)
                .build();

        OtlpGrpcSpanExporter spanExporter = OtlpGrpcSpanExporter.builder()
                .setEndpoint("http://localhost:" + collector.getPort())
                .build();

        exportSpan(spanExporter, "path-test-span");

        boolean received = TestUtils.waitFor(() -> collector.getRequestCount() > 0, 5_000, 100);
        assertThat("Collector should have received at least one request", received, is(true));

        MockOtlpCollector.ReceivedRequest request = collector.getReceivedRequests().get(0);
        // The gRPC path for OTLP traces should be the full method name
        assertThat("Request path should be the gRPC method path",
                request.getPath(), containsString("TraceService"));
    }

    @Test
    void grpcHeadersAreCorrect() throws Exception {
        collector = new MockOtlpCollector.Builder()
                .protocolMode(MockOtlpCollector.ProtocolMode.GRPC)
                .build();

        OtlpGrpcSpanExporter spanExporter = OtlpGrpcSpanExporter.builder()
                .setEndpoint("http://localhost:" + collector.getPort())
                .build();

        exportSpan(spanExporter, "headers-test-span");

        boolean received = TestUtils.waitFor(() -> collector.getRequestCount() > 0, 5_000, 100);
        assertThat("Collector should have received at least one request", received, is(true));

        MockOtlpCollector.ReceivedRequest request = collector.getReceivedRequests().get(0);

        // Verify gRPC-specific headers
        assertThat("Content-Type should be gRPC",
                request.getHeaders().get("content-type").toString(),
                containsString("application/grpc"));
        assertThat("TE header should be present",
                request.getHeaders().contains("te"), is(true));
    }

    private void exportSpan(SpanExporter spanExporter, String spanName) {
        openTelemetry = TestUtils.createOpenTelemetry(spanExporter);
        Tracer tracer = openTelemetry.getTracer("test");
        Span span = TestUtils.createTestSpan(tracer, spanName);
        span.end();
        openTelemetry.getSdkTracerProvider()
                .forceFlush()
                .join(10, TimeUnit.SECONDS);
    }

    private void assertGrpcSpanReceived() throws InterruptedException {
        boolean received = TestUtils.waitFor(() -> collector.getRequestCount() > 0, 5_000, 100);

        assertThat("Collector should have received at least one request", received, is(true));

        MockOtlpCollector.ReceivedRequest request = collector.getReceivedRequests().get(0);
        assertThat("Request should be gRPC", request.isGrpc(), is(true));
        assertThat("Request should have gRPC metadata", request.getGrpcMetadata(), notNullValue());
        assertThat("Request payload should not be empty", request.getGrpcMessage(), is(notNullValue()));
    }
}

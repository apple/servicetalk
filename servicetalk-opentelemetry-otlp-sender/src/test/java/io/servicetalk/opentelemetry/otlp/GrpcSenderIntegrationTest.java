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
package io.servicetalk.opentelemetry.otlp;

import io.servicetalk.http.api.HttpResponseStatus;
import io.servicetalk.test.resources.DefaultTestCerts;

import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.exporter.otlp.trace.OtlpGrpcSpanExporter;
import io.opentelemetry.proto.trace.v1.ResourceSpans;
import io.opentelemetry.sdk.OpenTelemetrySdk;
import io.opentelemetry.sdk.common.export.GrpcSenderProvider;
import io.opentelemetry.sdk.common.export.RetryPolicy;
import io.opentelemetry.sdk.trace.export.SpanExporter;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.ServiceLoader;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
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

    @Test
    void grpcServerReturnsRetryableStatusFailsExport() throws Exception {
        collector = new MockOtlpCollector.Builder()
                .protocolMode(MockOtlpCollector.ProtocolMode.GRPC)
                .respondWithGrpcStatus(14, "collector unavailable") // 14 = UNAVAILABLE
                .build();

        try (OtlpGrpcSpanExporter spanExporter = OtlpGrpcSpanExporter.builder()
                .setEndpoint("http://localhost:" + collector.getPort())
                .setRetryPolicy(fastRetry())
                .build()) {

            io.opentelemetry.sdk.common.CompletableResultCode result = spanExporter.export(
                    Collections.singletonList(TestUtils.fakeSpanData("retryable-status-span")));
            result.join(10, TimeUnit.SECONDS);

            assertThat("Export should fail when collector returns non-OK gRPC status",
                    result.isSuccess(), is(false));
            assertThat("Collector should still have observed the request",
                    collector.getRequestCount(), greaterThan(0));
        }
    }

    @Test
    void grpcServerReturnsHttpErrorFailsExport() throws Exception {
        collector = new MockOtlpCollector.Builder()
                .protocolMode(MockOtlpCollector.ProtocolMode.GRPC)
                .failFirstNRequests(Integer.MAX_VALUE, HttpResponseStatus.BAD_GATEWAY)
                .build();

        try (OtlpGrpcSpanExporter spanExporter = OtlpGrpcSpanExporter.builder()
                .setEndpoint("http://localhost:" + collector.getPort())
                .setRetryPolicy(fastRetry())
                .build()) {

            io.opentelemetry.sdk.common.CompletableResultCode result = spanExporter.export(
                    Collections.singletonList(TestUtils.fakeSpanData("http-error-span")));
            result.join(10, TimeUnit.SECONDS);

            assertThat("Export should fail when collector returns HTTP 5xx",
                    result.isSuccess(), is(false));
        }
    }

    @Test
    void grpcRetriesRetryableTrailersOnlyStatus() throws Exception {
        // First request fails with UNAVAILABLE; subsequent succeed.
        collector = new MockOtlpCollector.Builder()
                .protocolMode(MockOtlpCollector.ProtocolMode.GRPC)
                .respondWithGrpcStatusFirstN(1, 14, "first attempt down")
                .build();

        try (OtlpGrpcSpanExporter spanExporter = OtlpGrpcSpanExporter.builder()
                .setEndpoint("http://localhost:" + collector.getPort())
                .setRetryPolicy(fastRetry())
                .build()) {

            io.opentelemetry.sdk.common.CompletableResultCode result = spanExporter.export(
                    Collections.singletonList(TestUtils.fakeSpanData("retry-span")));
            result.join(10, TimeUnit.SECONDS);

            assertThat("Retry should have fired (collector should have observed at least 2 attempts); "
                            + "result.isSuccess=" + result.isSuccess(),
                    collector.getRequestCount(), greaterThan(1));
            assertThat("Export should succeed after retry", result.isSuccess(), is(true));
        }
    }

    /**
     * Retry policy for failure-path tests: fast enough to keep tests quick, but with enough attempts that a
     * transient connection hiccup under load still leaves budget beyond the retryable-status retry being tested.
     */
    private static RetryPolicy fastRetry() {
        return RetryPolicy.builder()
                .setMaxAttempts(3)
                .setInitialBackoff(java.time.Duration.ofMillis(100))
                .setMaxBackoff(java.time.Duration.ofMillis(200))
                .setBackoffMultiplier(1.0)
                .build();
    }

    @Test
    void grpcGzipCompressedRequestIsFramed() throws Exception {
        collector = new MockOtlpCollector.Builder()
                .protocolMode(MockOtlpCollector.ProtocolMode.GRPC)
                .build();

        OtlpGrpcSpanExporter spanExporter = OtlpGrpcSpanExporter.builder()
                .setEndpoint("http://localhost:" + collector.getPort())
                .setCompression("gzip")
                .build();

        exportSpan(spanExporter, "gzip-compressed-span");

        boolean received = TestUtils.waitFor(() -> collector.getRequestCount() > 0, 5_000, 100);
        assertThat("Collector should have received at least one request", received, is(true));

        MockOtlpCollector.ReceivedRequest request = collector.getReceivedRequests().get(0);
        MockOtlpCollector.GrpcFrameMetadata metadata = request.getGrpcMetadata();
        assertThat("gRPC frame metadata should be present", metadata, notNullValue());
        assertThat("Frame should set the compressed flag", metadata.isCompressed(), is(true));
        assertThat("grpc-encoding header should advertise gzip",
                request.getHeaders().get("grpc-encoding").toString(), equalTo("gzip"));
    }

    @Test
    void grpcDynamicHeadersAppliedPerRequest() throws Exception {
        collector = new MockOtlpCollector.Builder()
                .protocolMode(MockOtlpCollector.ProtocolMode.GRPC)
                .build();

        AtomicInteger counter = new AtomicInteger();
        OtlpGrpcSpanExporter spanExporter = OtlpGrpcSpanExporter.builder()
                .setEndpoint("http://localhost:" + collector.getPort())
                .setHeaders(() -> Collections.singletonMap("x-test-token",
                        Integer.toString(counter.incrementAndGet())))
                .build();

        openTelemetry = TestUtils.createOpenTelemetry(spanExporter);
        Tracer tracer = openTelemetry.getTracer("test");

        TestUtils.createTestSpan(tracer, "first").end();
        openTelemetry.getSdkTracerProvider().forceFlush().join(10, TimeUnit.SECONDS);

        TestUtils.createTestSpan(tracer, "second").end();
        openTelemetry.getSdkTracerProvider().forceFlush().join(10, TimeUnit.SECONDS);

        boolean received = TestUtils.waitFor(() -> collector.getRequestCount() >= 2, 5_000, 100);
        assertThat("Collector should have received both requests", received, is(true));

        List<MockOtlpCollector.ReceivedRequest> requests = collector.getReceivedRequests();
        assertThat("First request token", requests.get(0).getHeaders().get("x-test-token").toString(),
                equalTo("1"));
        assertThat("Second request token", requests.get(1).getHeaders().get("x-test-token").toString(),
                equalTo("2"));
        // Framing headers must coexist alongside dynamic headers.
        assertThat("Framing content-type must remain on second request",
                requests.get(1).getHeaders().get("content-type").toString(),
                containsString("application/grpc"));
    }

    @Test
    void grpcDynamicHeadersNotDuplicatedAcrossRetries() throws Exception {
        collector = new MockOtlpCollector.Builder()
                .protocolMode(MockOtlpCollector.ProtocolMode.GRPC)
                .respondWithGrpcStatusFirstN(1, 14, "first attempt down")
                .build();

        try (OtlpGrpcSpanExporter spanExporter = OtlpGrpcSpanExporter.builder()
                .setEndpoint("http://localhost:" + collector.getPort())
                .setRetryPolicy(fastRetry())
                .setHeaders(() -> Collections.singletonMap("x-test-token", "tok"))
                .build()) {

            io.opentelemetry.sdk.common.CompletableResultCode result = spanExporter.export(
                    Collections.singletonList(TestUtils.fakeSpanData("retry-header-span")));
            result.join(10, TimeUnit.SECONDS);

            assertThat("Export should succeed after retry", result.isSuccess(), is(true));
            assertThat("Retry should have produced a second attempt",
                    collector.getRequestCount(), greaterThan(1));

            for (MockOtlpCollector.ReceivedRequest request : collector.getReceivedRequests()) {
                assertThat("x-test-token must appear exactly once per attempt, not accumulate across retries",
                        headerValues(request.getHeaders(), "x-test-token"), hasSize(1));
            }
        }
    }

    private static List<CharSequence> headerValues(io.servicetalk.http.api.HttpHeaders headers, CharSequence name) {
        List<CharSequence> values = new ArrayList<>();
        headers.values(name).forEach(values::add);
        return values;
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

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
import io.opentelemetry.exporter.otlp.http.trace.OtlpHttpSpanExporter;
import io.opentelemetry.sdk.OpenTelemetrySdk;
import io.opentelemetry.sdk.common.export.HttpSenderProvider;
import io.opentelemetry.sdk.trace.export.SpanExporter;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
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
import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.isA;

final class HttpSenderIntegrationTest {

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
    void onlyServiceTalkProvidersOnClassPath() {
        // It's tough to verify the underlying transport, but if the ServiceTalk sender is the only one
        // available, then that has got to be the right one.
        ServiceLoader<HttpSenderProvider> loader = ServiceLoader.load(HttpSenderProvider.class);
        List<HttpSenderProvider> results = new ArrayList<>();
        for (HttpSenderProvider spi : loader) {
            results.add(spi);
        }
        assertThat(results, hasSize(1));
        assertThat(results.get(0), isA(ServiceTalkHttpSenderProvider.class));
    }

    @Test
    void cleartextHttpSendsSpansToCollector() throws Exception {
        collector = new MockOtlpCollector.Builder().build();

        OtlpHttpSpanExporter spanExporter = OtlpHttpSpanExporter.builder()
                .setEndpoint("http://localhost:" + collector.getPort() + "/v1/traces")
                .setTimeout(Duration.ofSeconds(10))
                .build();

        exportSpan(spanExporter, "cleartext-test-span");

        assertSpanReceived();
    }

    @Test
    void tlsHttpSendsSpansToCollector() throws Exception {
        collector = new MockOtlpCollector.Builder()
                .securityMode(MockOtlpCollector.SecurityMode.TLS)
                .build();

        TrustManagerFactory tmf = TestUtils.createTrustManagerFactory(DefaultTestCerts::loadServerCAPem);
        SSLContext sslContext = TestUtils.createTlsSslContext(tmf);
        X509TrustManager trustManager = TestUtils.extractTrustManager(tmf);

        OtlpHttpSpanExporter spanExporter = OtlpHttpSpanExporter.builder()
                .setEndpoint("https://" + DefaultTestCerts.serverPemHostname() + ":" +
                        collector.getPort() + "/v1/traces")
                .setTimeout(Duration.ofSeconds(10))
                .setSslContext(sslContext, trustManager)
                .build();

        exportSpan(spanExporter, "tls-test-span");

        assertSpanReceived();
    }

    @Test
    void mutualTlsHttpSendsSpansToCollector() throws Exception {
        collector = new MockOtlpCollector.Builder()
                .securityMode(MockOtlpCollector.SecurityMode.MUTUAL_TLS)
                .build();

        TrustManagerFactory tmf = TestUtils.createTrustManagerFactory(DefaultTestCerts::loadServerCAPem);
        KeyManagerFactory kmf = TestUtils.createKeyManagerFactory(
                DefaultTestCerts::loadClientPem, DefaultTestCerts::loadClientKey);
        SSLContext sslContext = TestUtils.createMutualTlsSslContext(tmf, kmf);
        X509TrustManager trustManager = TestUtils.extractTrustManager(tmf);

        OtlpHttpSpanExporter spanExporter = OtlpHttpSpanExporter.builder()
                .setEndpoint("https://" + DefaultTestCerts.serverPemHostname() + ":" +
                        collector.getPort() + "/v1/traces")
                .setTimeout(Duration.ofSeconds(10))
                .setSslContext(sslContext, trustManager)
                .build();

        exportSpan(spanExporter, "mtls-test-span");

        assertSpanReceived();
    }

    @Test
    void mutualTlsRejectsClientWithoutCertificate() throws Exception {
        collector = new MockOtlpCollector.Builder()
                .securityMode(MockOtlpCollector.SecurityMode.MUTUAL_TLS)
                .build();

        // Client configured with server trust only — no client certificate presented.
        TrustManagerFactory tmf = TestUtils.createTrustManagerFactory(DefaultTestCerts::loadServerCAPem);
        SSLContext sslContext = TestUtils.createTlsSslContext(tmf);
        X509TrustManager trustManager = TestUtils.extractTrustManager(tmf);

        OtlpHttpSpanExporter spanExporter = OtlpHttpSpanExporter.builder()
                .setEndpoint("https://" + DefaultTestCerts.serverPemHostname() + ":" +
                        collector.getPort() + "/v1/traces")
                .setTimeout(Duration.ofSeconds(5))
                .setSslContext(sslContext, trustManager)
                .build();

        exportSpan(spanExporter, "rejected-span");

        assertThat("mTLS server should reject a client that presents no certificate",
                collector.getRequestCount(), is(0));
    }

    private void exportSpan(SpanExporter spanExporter, String spanName) throws Exception {
        openTelemetry = TestUtils.createOpenTelemetry(spanExporter);
        Tracer tracer = openTelemetry.getTracer("test");
        Span span = TestUtils.createTestSpan(tracer, spanName);
        span.end();
        openTelemetry.getSdkTracerProvider()
                .forceFlush()
                .join(10, TimeUnit.SECONDS);
    }

    private void assertSpanReceived() throws InterruptedException {
        boolean received = TestUtils.waitFor(() -> collector.getRequestCount() > 0, 5_000, 100);

        assertThat("Collector should have received at least one request", received, is(true));

        MockOtlpCollector.ReceivedRequest request = collector.getReceivedRequests().get(0);
        assertThat("Request path should be /v1/traces", request.getPath(), is("/v1/traces"));
        assertThat("Request payload should not be empty", request.getPayload().length, is(greaterThan(0)));
    }
}

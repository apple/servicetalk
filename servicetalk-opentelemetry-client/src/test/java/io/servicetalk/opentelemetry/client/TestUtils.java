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

import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.sdk.OpenTelemetrySdk;
import io.opentelemetry.sdk.trace.SdkTracerProvider;
import io.opentelemetry.sdk.trace.export.SpanExporter;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.KeyStore;
import java.security.PrivateKey;
import java.security.SecureRandom;
import java.security.cert.Certificate;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.security.spec.PKCS8EncodedKeySpec;
import java.util.Base64;
import java.util.Collection;
import java.util.concurrent.TimeUnit;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.TrustManagerFactory;
import javax.net.ssl.X509TrustManager;

/**
 * Utility methods for ServiceTalk OpenTelemetry integration tests.
 */
final class TestUtils {

    private TestUtils() {
        // Utility class
    }

    /**
     * Create an OpenTelemetry SDK instance configured with the given span exporter.
     *
     * @param spanExporter the span exporter to use
     * @return configured OpenTelemetrySdk instance
     */
    static OpenTelemetrySdk createOpenTelemetry(SpanExporter spanExporter) {
        SdkTracerProvider tracerProvider = SdkTracerProvider.builder()
                .addSpanProcessor(io.opentelemetry.sdk.trace.export.BatchSpanProcessor.builder(spanExporter)
                        .setScheduleDelay(100, TimeUnit.MILLISECONDS)
                        .build())
                .build();

        return OpenTelemetrySdk.builder()
                .setTracerProvider(tracerProvider)
                .build();
    }

    /**
     * Create a test span with the given tracer.
     *
     * @param tracer the tracer to use
     * @param spanName the span name
     * @return the created span
     */
    static Span createTestSpan(Tracer tracer, String spanName) {
        return tracer.spanBuilder(spanName)
                .setSpanKind(SpanKind.CLIENT)
                .setAttribute("test.key", "test.value")
                .setAttribute("service.name", "test-service")
                .startSpan();
    }

    /**
     * End a span and wait for it to be exported.
     *
     * @param span the span to end
     * @throws InterruptedException if interrupted while waiting
     */
    static void endSpanAndWait(Span span) throws InterruptedException {
        span.end();
        // Give some time for the batch processor to export
        Thread.sleep(500);
    }

    /**
     * Create a TrustManagerFactory from PEM-encoded certificate.
     *
     * @param pemSupplier supplier of PEM-encoded certificate
     * @return configured TrustManagerFactory
     * @throws Exception if trust manager creation fails
     */
    static TrustManagerFactory createTrustManagerFactory(
            java.util.function.Supplier<InputStream> pemSupplier) throws Exception {
        try (InputStream pemStream = pemSupplier.get()) {
            CertificateFactory certFactory = CertificateFactory.getInstance("X.509");
            Collection<? extends Certificate> certificates = certFactory.generateCertificates(pemStream);

            KeyStore keyStore = KeyStore.getInstance(KeyStore.getDefaultType());
            keyStore.load(null, null);

            int i = 0;
            for (Certificate cert : certificates) {
                keyStore.setCertificateEntry("cert-" + i++, cert);
            }

            TrustManagerFactory trustManagerFactory =
                    TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
            trustManagerFactory.init(keyStore);

            return trustManagerFactory;
        }
    }

    /**
     * Create a KeyManagerFactory from PEM-encoded certificate and private key.
     *
     * @param certPemSupplier supplier of PEM-encoded certificate
     * @param keyPemSupplier supplier of PEM-encoded private key
     * @return configured KeyManagerFactory
     * @throws Exception if key manager creation fails
     */
    static KeyManagerFactory createKeyManagerFactory(
            java.util.function.Supplier<InputStream> certPemSupplier,
            java.util.function.Supplier<InputStream> keyPemSupplier) throws Exception {

        // Load certificate
        X509Certificate cert;
        try (InputStream certStream = certPemSupplier.get()) {
            CertificateFactory certFactory = CertificateFactory.getInstance("X.509");
            cert = (X509Certificate) certFactory.generateCertificate(certStream);
        }

        // Load private key
        PrivateKey privateKey = loadPrivateKey(keyPemSupplier.get());

        // Create KeyStore
        KeyStore keyStore = KeyStore.getInstance(KeyStore.getDefaultType());
        keyStore.load(null, null);
        keyStore.setKeyEntry("client-key", privateKey, new char[0], new Certificate[]{cert});

        // Create KeyManagerFactory
        KeyManagerFactory keyManagerFactory =
                KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
        keyManagerFactory.init(keyStore, new char[0]);

        return keyManagerFactory;
    }

    /**
     * Load a private key from PEM-encoded input stream.
     *
     * @param pemStream PEM-encoded private key stream
     * @return the private key
     * @throws Exception if key loading fails
     */
    private static PrivateKey loadPrivateKey(InputStream pemStream) throws Exception {
        // Read all bytes from stream
        byte[] pemBytes = readAllBytes(pemStream);
        String pemContent = new String(pemBytes, StandardCharsets.US_ASCII);
        // Remove PEM headers and footers
        pemContent = pemContent
                .replaceAll("-----BEGIN PRIVATE KEY-----", "")
                .replaceAll("-----END PRIVATE KEY-----", "")
                .replaceAll("-----BEGIN RSA PRIVATE KEY-----", "")
                .replaceAll("-----END RSA PRIVATE KEY-----", "")
                .replaceAll("\\s", "");

        byte[] keyBytes = Base64.getDecoder().decode(pemContent);
        PKCS8EncodedKeySpec keySpec = new PKCS8EncodedKeySpec(keyBytes);
        KeyFactory keyFactory = KeyFactory.getInstance("RSA");
        return keyFactory.generatePrivate(keySpec);
    }

    /**
     * Read all bytes from an InputStream.
     *
     * @param inputStream the input stream
     * @return all bytes from the stream
     * @throws IOException if reading fails
     */
    static byte[] readAllBytes(InputStream inputStream) throws IOException {
        byte[] buffer = new byte[8192];
        int bytesRead;
        java.io.ByteArrayOutputStream output = new java.io.ByteArrayOutputStream();
        while ((bytesRead = inputStream.read(buffer)) != -1) {
            output.write(buffer, 0, bytesRead);
        }
        return output.toByteArray();
    }

    /**
     * Create an SSLContext for client authentication (mutual TLS).
     *
     * @param trustManagerFactory trust manager for server certificate validation
     * @param keyManagerFactory key manager for client certificate
     * @return configured SSLContext
     * @throws Exception if SSL context creation fails
     */
    static SSLContext createMutualTlsSslContext(
            TrustManagerFactory trustManagerFactory,
            KeyManagerFactory keyManagerFactory) throws Exception {
        SSLContext sslContext = SSLContext.getInstance("TLS");
        sslContext.init(
                keyManagerFactory.getKeyManagers(),
                trustManagerFactory.getTrustManagers(),
                new SecureRandom());
        return sslContext;
    }

    /**
     * Create an SSLContext for server authentication only (TLS).
     *
     * @param trustManagerFactory trust manager for server certificate validation
     * @return configured SSLContext
     * @throws Exception if SSL context creation fails
     */
    static SSLContext createTlsSslContext(TrustManagerFactory trustManagerFactory) throws Exception {
        SSLContext sslContext = SSLContext.getInstance("TLS");
        sslContext.init(null, trustManagerFactory.getTrustManagers(), new SecureRandom());
        return sslContext;
    }

    /**
     * Extract the X509TrustManager from a TrustManagerFactory.
     *
     * @param trustManagerFactory the trust manager factory
     * @return the X509TrustManager
     * @throws IllegalStateException if no X509TrustManager is found
     */
    static X509TrustManager extractTrustManager(TrustManagerFactory trustManagerFactory) {
        for (TrustManager tm : trustManagerFactory.getTrustManagers()) {
            if (tm instanceof X509TrustManager) {
                return (X509TrustManager) tm;
            }
        }
        throw new IllegalStateException("No X509TrustManager found in TrustManagerFactory");
    }

    static boolean waitFor(java.util.function.BooleanSupplier condition,
                          long timeoutMs,
                          long checkIntervalMs) throws InterruptedException {
        long endTime = System.currentTimeMillis() + timeoutMs;
        while (System.currentTimeMillis() < endTime) {
            if (condition.getAsBoolean()) {
                return true;
            }
            Thread.sleep(checkIntervalMs);
        }
        return false;
    }
}

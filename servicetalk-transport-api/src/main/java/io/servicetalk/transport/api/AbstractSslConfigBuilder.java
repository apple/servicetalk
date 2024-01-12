/*
 * Copyright © 2021, 2023 Apple Inc. and the ServiceTalk project authors
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
package io.servicetalk.transport.api;

import io.servicetalk.transport.api.SslConfig.CipherSuiteFilter;

import java.io.InputStream;
import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.function.Supplier;
import javax.annotation.Nullable;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLEngine;
import javax.net.ssl.SSLSessionContext;
import javax.net.ssl.TrustManagerFactory;

import static io.servicetalk.utils.internal.DurationUtils.ensureNonNegative;
import static java.time.Duration.ofSeconds;
import static java.util.Arrays.asList;
import static java.util.Objects.requireNonNull;

/**
 * Abstract base class for building {@link SslConfig} objects.
 * @param <T> The type of {@link AbstractSslConfigBuilder} which is returned by setter methods.
 */
abstract class AbstractSslConfigBuilder<T extends AbstractSslConfigBuilder<T>> {

    // FIXME: 0.43 - make DEFAULT_HANDSHAKE_TIMEOUT constant private
    static final Duration DEFAULT_HANDSHAKE_TIMEOUT = ofSeconds(10);    // same as default in Netty SslHandler
    private static final int DEFAULT_MAX_CERTIFICATE_LIST_BYTES = 32 * 1024;    // 32Kb

    @Nullable
    private TrustManagerFactory trustManagerFactory;
    @Nullable
    private Supplier<InputStream> trustCertChainSupplier;
    @Nullable
    private KeyManagerFactory keyManagerFactory;
    @Nullable
    private Supplier<InputStream> keyCertChainSupplier;
    @Nullable
    private Supplier<InputStream> keySupplier;
    @Nullable
    private String keyPassword;
    @Nullable
    private List<String> sslProtocols;
    @Nullable
    private List<String> alpnProtocols;
    @Nullable
    private List<String> ciphers;
    private CipherSuiteFilter cipherSuiteFilter = CipherSuiteFilter.PROVIDED;
    private long sessionCacheSize;
    private long sessionTimeout;
    private int maxCertificateListBytes = DEFAULT_MAX_CERTIFICATE_LIST_BYTES;
    @Nullable
    private SslProvider provider;
    @Nullable
    private List<CertificateCompressionAlgorithm> certificateCompressionAlgorithms;
    private Duration handshakeTimeout = DEFAULT_HANDSHAKE_TIMEOUT;

    /**
     * Set the {@link TrustManagerFactory} used for verifying the remote endpoint's certificate.
     *
     * @param tmf the {@link TrustManagerFactory} used for verifying the remote endpoint's certificate.
     * @return {@code this}.
     * @see SslConfig#trustManagerFactory()
     */
    public final T trustManager(TrustManagerFactory tmf) {
        this.trustManagerFactory = requireNonNull(tmf);
        trustCertChainSupplier = null;
        return thisT();
    }

    @Nullable
    final TrustManagerFactory trustManager() {
        return trustManagerFactory;
    }

    /**
     * Set the trusted certificates for verifying the remote endpoint's certificate. The input stream should
     * contain an {@code X.509} certificate chain in {@code PEM} format.
     *
     * @param trustCertChainSupplier the trusted certificates for verifying the remote endpoint's certificate. The input
     * stream should contain an {@code X.509} certificate chain in {@code PEM} format.
     * <p>
     * Each invocation of the {@link Supplier} should provide an independent instance of {@link InputStream} and the
     * caller is responsible for invoking {@link InputStream#close()}.
     * @return {@code this}.
     * @see SslConfig#trustCertChainSupplier()
     */
    public final T trustManager(Supplier<InputStream> trustCertChainSupplier) {
        this.trustCertChainSupplier = requireNonNull(trustCertChainSupplier);
        trustManagerFactory = null;
        return thisT();
    }

    @Nullable
    final Supplier<InputStream> trustCertChainSupplier() {
        return trustCertChainSupplier;
    }

    /**
     * Set the {@link KeyManagerFactory} to use for the SSL/TLS handshake.
     *
     * @param kmf the {@link KeyManagerFactory} to use for the SSL/TLS handshake.
     * @return {@code this}.
     * @see SslConfig#keyManagerFactory()
     */
    public final T keyManager(KeyManagerFactory kmf) {
        this.keyManagerFactory = requireNonNull(kmf);
        keyCertChainSupplier = null;
        keySupplier = null;
        keyPassword = null;
        return thisT();
    }

    @Nullable
    final KeyManagerFactory keyManager() {
        return keyManagerFactory;
    }

    /**
     * Set a {@link InputStream} which provides {@code X.509} certificate chain in {@code PEM} format and
     * a {@code PKCS#8} private key in {@code PEM} format.
     *
     * @param keyCertChainSupplier the {@code X.509} certificate chain in {@code PEM} format.
     * <p>
     * Each invocation of the {@link Supplier} should provide an independent instance of {@link InputStream} and the
     * caller is responsible for invoking {@link InputStream#close()}.
     * @param keySupplier a {@link InputStream} which provides a {@code PKCS#8} private key in {@code PEM} format
     * associated with.
     * <p>
     * Each invocation of the {@link Supplier} should provide an independent instance of {@link InputStream} and the
     * caller is responsible for invoking {@link InputStream#close()}.
     * @return {@code this}.
     * @see SslConfig#keyCertChainSupplier()
     * @see SslConfig#keySupplier()
     */
    public final T keyManager(Supplier<InputStream> keyCertChainSupplier, Supplier<InputStream> keySupplier) {
        this.keyCertChainSupplier = requireNonNull(keyCertChainSupplier);
        this.keySupplier = requireNonNull(keySupplier);
        keyPassword = null;
        keyManagerFactory = null;
        return thisT();
    }

    /**
     * Set a {@link InputStream} which provides {@code X.509} certificate chain in {@code PEM} format and
     * a {@code PKCS#8} private key in {@code PEM} format protected by a password.
     *
     * @param keyCertChainSupplier the {@code X.509} certificate chain in {@code PEM} format.
     * <p>
     * Each invocation of the {@link Supplier} should provide an independent instance of {@link InputStream} and the
     * caller is responsible for invoking {@link InputStream#close()}.
     * @param keySupplier a {@link InputStream} which provides a {@code PKCS#8} private key in {@code PEM} format
     * associated with.
     * <p>
     * Each invocation of the {@link Supplier} should provide an independent instance of {@link InputStream} and the
     * caller is responsible for invoking {@link InputStream#close()}.
     * @param keyPassword the password required to access the key material from {@code keySupplier}.
     * @return {@code this}.
     * @see SslConfig#keyCertChainSupplier()
     * @see SslConfig#keySupplier()
     * @see SslConfig#keyPassword()
     */
    public final T keyManager(Supplier<InputStream> keyCertChainSupplier, Supplier<InputStream> keySupplier,
                 @Nullable String keyPassword) {
        this.keyCertChainSupplier = requireNonNull(keyCertChainSupplier);
        this.keySupplier = requireNonNull(keySupplier);
        this.keyPassword = keyPassword;
        keyManagerFactory = null;
        return thisT();
    }

    @Nullable
    final Supplier<InputStream> keyCertChainSupplier() {
        return keyCertChainSupplier;
    }

    @Nullable
    final Supplier<InputStream> keySupplier() {
        return keySupplier;
    }

    @Nullable
    final String keyPassword() {
        return keyPassword;
    }

    /**
     * Set the TLS protocols to enable, in the order of preference.
     *
     * @param protocols the TLS protocols to enable, in the order of preference.
     * @return {@code this}.
     * @see SslConfig#sslProtocols()
     * @see SSLEngine#setEnabledProtocols(String[])
     */
    public final T sslProtocols(List<String> protocols) {
        if (protocols.isEmpty()) {
            throw new IllegalArgumentException("protocols cannot be empty");
        }
        this.sslProtocols = protocols;
        return thisT();
    }

    /**
     * Set the TLS protocols to enable, in the order of preference.
     *
     * @param protocols the TLS protocols to enable, in the order of preference.
     * @return {@code this}.
     * @see SslConfig#sslProtocols()
     * @see SSLEngine#setEnabledProtocols(String[])
     */
    public final T sslProtocols(final String... protocols) {
        return sslProtocols(asList(protocols));
    }

    @Nullable
    final List<String> sslProtocols() {
        return sslProtocols;
    }

    /**
     * Set the TLS <a href="https://tools.ietf.org/html/rfc7301">ALPN</a> protocols.
     * <p>
     * Note that each ALPN protocol typically requires corresponding configuration at the protocol layer and as a result
     * maybe inferred and overridden by the protocol layer.
     *
     * @param protocols the TLS <a href="https://tools.ietf.org/html/rfc7301">ALPN</a> protocols.
     * @return {@code this}.
     * @see SslConfig#alpnProtocols()
     */
    public final T alpnProtocols(final List<String> protocols) {
        if (protocols.isEmpty()) {
            throw new IllegalArgumentException("protocols cannot be empty");
        }
        this.alpnProtocols = protocols;
        return thisT();
    }

    /**
     * Set the TLS <a href="https://tools.ietf.org/html/rfc7301">ALPN</a> protocols.
     * <p>
     * Note that each ALPN protocol typically requires corresponding configuration at the protocol layer and as a result
     * maybe inferred and overridden by the protocol layer.
     *
     * @param protocols the TLS <a href="https://tools.ietf.org/html/rfc7301">ALPN</a> protocols.
     * @return {@code this}.
     * @see SslConfig#alpnProtocols()
     */
    public final T alpnProtocols(final String... protocols) {
        return alpnProtocols(asList(protocols));
    }

    @Nullable
    final List<String> alpnProtocols() {
        return alpnProtocols;
    }

    /**
     * Set the cipher suites to enable, in the order of preference.
     *
     * @param ciphers the ciphers to use.
     * @return {@code this}.
     * @see SslConfig#ciphers()
     * @see #cipherSuiteFilter(CipherSuiteFilter)
     */
    public final T ciphers(final List<String> ciphers) {
        if (ciphers.isEmpty()) {
            throw new IllegalArgumentException("ciphers cannot be empty");
        }
        this.ciphers = ciphers;
        return thisT();
    }

    /**
     * Set the cipher suites to enable, in the order of preference.
     *
     * @param ciphers the ciphers to use.
     * @return {@code this}.
     * @see SslConfig#ciphers()
     * @see #cipherSuiteFilter(CipherSuiteFilter)
     */
    public final T ciphers(final String... ciphers) {
        return ciphers(asList(ciphers));
    }

    @Nullable
    final List<String> ciphers() {
        return ciphers;
    }

    /**
     * Set the filtering behavior for ciphers suites.
     *
     * @param cipherSuiteFilter {@link CipherSuiteFilter} to use.
     * @return {@code this}.
     * @see SslConfig#cipherSuiteFilter()
     * @see #ciphers(String...)
     * @see #ciphers(List)
     */
    public final T cipherSuiteFilter(final CipherSuiteFilter cipherSuiteFilter) {
        this.cipherSuiteFilter = requireNonNull(cipherSuiteFilter);
        return thisT();
    }

    final CipherSuiteFilter cipherSuiteFilter() {
        return cipherSuiteFilter;
    }

    /**
     * Get the size of the cache used for storing SSL session objects.
     *
     * @param sessionCacheSize the size of the cache used for storing SSL session objects.
     * @return {@code this}.
     * @see SslConfig#sessionCacheSize()
     * @see SSLSessionContext#setSessionCacheSize(int)
     */
    public final T sessionCacheSize(long sessionCacheSize) {
        if (sessionCacheSize < 0) {
            throw new IllegalArgumentException("sessionCacheSize: " + sessionCacheSize + " (expected >=0)");
        }
        this.sessionCacheSize = sessionCacheSize;
        return thisT();
    }

    final long sessionCacheSize() {
        return sessionCacheSize;
    }

    /**
     * Get the timeout for the cached SSL session objects, in seconds.
     *
     * @param sessionTimeout the timeout for the cached SSL session objects, in seconds.
     * @return {@code this}.
     * @see SslConfig#sessionTimeout()
     * @see SSLSessionContext#setSessionTimeout(int)
     */
    public final T sessionTimeout(long sessionTimeout) {
        if (sessionTimeout < 0) {
            throw new IllegalArgumentException("sessionTimeout: " + sessionTimeout + " (expected >=0)");
        }
        this.sessionTimeout = sessionTimeout;
        return thisT();
    }

    final long sessionTimeout() {
        return sessionTimeout;
    }

    /**
     * Get the {@link SslProvider} to use.
     *
     * @param provider the {@link SslProvider} to use.
     * @return {@code this}.
     * @see SslConfig#provider()
     */
    public final T provider(SslProvider provider) {
        this.provider = requireNonNull(provider);
        return thisT();
    }

    @Nullable
    final SslProvider provider() {
        return provider;
    }

    /**
     * Sets the certificate compression algorithms to advertise if the feature is supported at runtime.
     *
     * @param algorithms the certificate compression algorithms to use.
     * @return {@code this}.
     * @see SslConfig#certificateCompressionAlgorithms()
     * @see CertificateCompressionAlgorithms
     */
    public final T certificateCompressionAlgorithms(final List<CertificateCompressionAlgorithm> algorithms) {
        if (algorithms.isEmpty()) {
            throw new IllegalArgumentException("algorithms cannot be empty");
        }
        this.certificateCompressionAlgorithms = algorithms;
        return thisT();
    }

    /**
     * Sets the certificate compression algorithms to advertise if the feature is supported at runtime.
     *
     * @param algorithms the certificate compression algorithms to use.
     * @return {@code this}.
     * @see SslConfig#certificateCompressionAlgorithms()
     * @see CertificateCompressionAlgorithms
     */
    public final T certificateCompressionAlgorithms(final CertificateCompressionAlgorithm... algorithms) {
        return certificateCompressionAlgorithms(Arrays.asList(algorithms));
    }

    @Nullable
    final List<CertificateCompressionAlgorithm> certificateCompressionAlgorithms() {
        return certificateCompressionAlgorithms;
    }

    /**
     * Sets the timeout for the handshake process.
     * <p>
     * Implementations can round the returned {@link Duration} to full time units, depending on their time granularity.
     * {@link Duration#ZERO Zero duration} disables the timeout.
     *
     * @param handshakeTimeout the timeout for the handshake process or {@link Duration#ZERO} to disable it.
     * @return {@code this}.
     * @see SslConfig#handshakeTimeout()
     */
    public final T handshakeTimeout(final Duration handshakeTimeout) {
        this.handshakeTimeout = ensureNonNegative(handshakeTimeout, "handshakeTimeout");
        return thisT();
    }

    final Duration handshakeTimeout() {
        return handshakeTimeout;
    }

    /**
     * Set the preferred maximum allowed size of the certificate chain in bytes. This may not be respected
     * and depends on if the {@link SSLEngine} supports this feature.
     * @param maxBytes Number of bytes for the certificate chain. {@code 0} may mean "use the default limit".
     * @return {@code this}.
     */
    public final T maxCertificateListBytes(int maxBytes) {
        if (maxBytes < 0) {
            throw new IllegalArgumentException("maxBytes: " + maxBytes + " (expected >=0)");
        }
        this.maxCertificateListBytes = maxBytes;
        return thisT();
    }

    final int maxCertificateListBytes() {
        return maxCertificateListBytes;
    }

    abstract T thisT();
}

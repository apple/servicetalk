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
package io.servicetalk.transport.api;

import java.io.InputStream;
import java.util.List;
import java.util.function.Supplier;
import javax.annotation.Nullable;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLEngine;
import javax.net.ssl.TrustManagerFactory;

import static java.util.Arrays.asList;
import static java.util.Objects.requireNonNull;

/**
 * Abstract base class for building {@link SslConfig} objects.
 * @param <T> The type of {@link AbstractSslConfigBuilder} which is returned by setter methods.
 */
abstract class AbstractSslConfigBuilder<T extends AbstractSslConfigBuilder<T>> {
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
    private Iterable<String> ciphers;
    private long sessionCacheSize;
    private long sessionTimeout;
    @Nullable
    private SslProvider provider;

    /**
     * Set the {@link TrustManagerFactory} used for verifying the remote endpoint's certificate.
     * @param tmf the {@link TrustManagerFactory} used for verifying the remote endpoint's certificate.
     * @return {@code this}.
     */
    T trustManager(TrustManagerFactory tmf) {
        this.trustManagerFactory = requireNonNull(tmf);
        return thisT();
    }

    @Nullable
    final TrustManagerFactory trustManager() {
        return trustManagerFactory;
    }

    /**
     * Set the trusted certificates for verifying the remote endpoint's certificate. The input stream should
     * contain an {@code X.509} certificate chain in {@code PEM} format.
     * @param trustCertChainSupplier the trusted certificates for verifying the remote endpoint's certificate. The input
     * stream should contain an {@code X.509} certificate chain in {@code PEM} format.
     * The responsibility to call {@link InputStream#close()} is transferred to callers of the {@link Supplier}.
     * If this is not the desired behavior then wrap the {@link InputStream} and override {@link InputStream#close()}.
     * @return {@code this}.
     */
    T trustManager(Supplier<InputStream> trustCertChainSupplier) {
        this.trustCertChainSupplier = requireNonNull(trustCertChainSupplier);
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
     */
    T keyManager(KeyManagerFactory kmf) {
        this.keyManagerFactory = requireNonNull(kmf);
        return thisT();
    }

    @Nullable
    final KeyManagerFactory keyManager() {
        return keyManagerFactory;
    }

    /**
     * Set a {@link InputStream} which provides {@code X.509} certificate chain in {@code PEM} format and
     * a {@code PKCS#8} private key in {@code PEM} format.
     * @param keyCertChainSupplier the {@code X.509} certificate chain in {@code PEM} format.
     * The responsibility to call {@link InputStream#close()} is transferred to callers of the {@link Supplier}.
     * If this is not the desired behavior then wrap the {@link InputStream} and override {@link InputStream#close()}.
     * @param keySupplier a {@link InputStream} which provides a {@code PKCS#8} private key in {@code PEM} format
     * associated with. The responsibility to call {@link InputStream#close()} is transferred to callers of the
     * {@link Supplier}. If this is not the desired behavior then wrap the {@link InputStream} and override
     * {@link InputStream#close()}.
     * @return {@code this}.
     */
    T keyManager(Supplier<InputStream> keyCertChainSupplier, Supplier<InputStream> keySupplier) {
        this.keyCertChainSupplier = requireNonNull(keyCertChainSupplier);
        this.keySupplier = requireNonNull(keySupplier);
        return thisT();
    }

    /**
     * Set a {@link InputStream} which provides {@code X.509} certificate chain in {@code PEM} format and
     * a {@code PKCS#8} private key in {@code PEM} format protected by a password.
     * @param keyCertChainSupplier the {@code X.509} certificate chain in {@code PEM} format.
     * The responsibility to call {@link InputStream#close()} is transferred to callers of the {@link Supplier}.
     * If this is not the desired behavior then wrap the {@link InputStream} and override {@link InputStream#close()}.
     * @param keySupplier a {@link InputStream} which provides a {@code PKCS#8} private key in {@code PEM} format
     * associated with. The responsibility to call {@link InputStream#close()} is transferred to callers of the
     * {@link Supplier}. If this is not the desired behavior then wrap the {@link InputStream} and override
     * {@link InputStream#close()}.
     * @param keyPassword the password required to access the key material from {@code keySupplier}.
     * @return {@code this}.
     */
    T keyManager(Supplier<InputStream> keyCertChainSupplier, Supplier<InputStream> keySupplier,
                 @Nullable String keyPassword) {
        this.keyCertChainSupplier = requireNonNull(keyCertChainSupplier);
        this.keySupplier = requireNonNull(keySupplier);
        this.keyPassword = keyPassword;
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
     * @see SSLEngine#setEnabledProtocols(String[])
     */
    public T sslProtocols(List<String> protocols) {
        this.sslProtocols = requireNonNull(protocols);
        return thisT();
    }

    /**
     * Set the TLS protocols to enable, in the order of preference.
     *
     * @param protocols the TLS protocols to enable, in the order of preference.
     * @return {@code this}.
     * @see SSLEngine#setEnabledProtocols(String[])
     */
    public T sslProtocols(final String... protocols) {
        return sslProtocols(asList(protocols));
    }

    @Nullable
    final List<String> sslProtocols() {
        return sslProtocols;
    }

    /**
     * Set the TLS <a href="https://tools.ietf.org/html/rfc7301">ALPN</a> protocols.
     * @param protocols the TLS <a href="https://tools.ietf.org/html/rfc7301">ALPN</a> protocols.
     * @return {@code this}.
     */
    public T alpnProtocols(final List<String> protocols) {
        this.alpnProtocols = requireNonNull(protocols);
        return thisT();
    }

    /**
     * Set the TLS <a href="https://tools.ietf.org/html/rfc7301">ALPN</a> protocols.
     * @param protocols the TLS <a href="https://tools.ietf.org/html/rfc7301">ALPN</a> protocols.
     * @return {@code this}.
     */
    public T alpnProtocols(final String... protocols) {
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
     */
    public T ciphers(final Iterable<String> ciphers) {
        this.ciphers = requireNonNull(ciphers);
        return thisT();
    }

    /**
     * Set the cipher suites to enable, in the order of preference.
     *
     * @param ciphers the ciphers to use.
     * @return {@code this}.
     */
    public T ciphers(final String... ciphers) {
        return ciphers(asList(ciphers));
    }

    @Nullable
    final Iterable<String> ciphers() {
        return ciphers;
    }

    /**
     * Get the size of the cache used for storing SSL session objects.
     *
     * @param sessionCacheSize the size of the cache used for storing SSL session objects.
     * @return {@code this}.
     */
    public T sessionCacheSize(long sessionCacheSize) {
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
     */
    public T sessionTimeout(long sessionTimeout) {
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
     */
    public T provider(SslProvider provider) {
        this.provider = provider;
        return thisT();
    }

    @Nullable
    final SslProvider provider() {
        return provider;
    }

    abstract T thisT();
}

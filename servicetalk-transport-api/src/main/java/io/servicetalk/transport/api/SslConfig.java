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

/**
 * Specifies the configuration for TLS/SSL.
 */
public interface SslConfig {
    /**
     * Get the {@link TrustManagerFactory} used for verifying the remote endpoint's certificate.
     *
     * @return the {@link TrustManagerFactory} used for verifying the remote endpoint's certificate.
     */
    @Nullable
    TrustManagerFactory trustManagerFactory();

    /**
     * Get the trusted certificates for verifying the remote endpoint's certificate. The input stream should
     * contain an {@code X.509} certificate chain in {@code PEM} format.
     * @return the trusted certificates for verifying the remote endpoint's certificate. The input stream should
     * contain an {@code X.509} certificate chain in {@code PEM} format.
     * <p>
     * Each invocation of the {@link Supplier} should provide an independent instance of {@link InputStream} and the
     * caller is responsible for invoking {@link InputStream#close()}.
     */
    @Nullable
    Supplier<InputStream> trustCertChainSupplier();

    /**
     * Get the {@link KeyManagerFactory} to use for the SSL/TLS handshake.
     *
     * @return the {@link KeyManagerFactory} to use for the SSL/TLS handshake.
     */
    @Nullable
    KeyManagerFactory keyManagerFactory();

    /**
     * Get a {@link InputStream} which provides {@code X.509} certificate chain in {@code PEM} format associated with
     * {@link #keySupplier()}.
     * @return the certificate chain associated with {@link #keySupplier()}.
     * <p>
     * Each invocation of the {@link Supplier} should provide an independent instance of {@link InputStream} and the
     * caller is responsible for invoking {@link InputStream#close()}.
     */
    @Nullable
    Supplier<InputStream> keyCertChainSupplier();

    /**
     * Get a {@link InputStream} which provides a {@code PKCS#8} private key in {@code PEM} format associated with
     * {@link #keyCertChainSupplier()}.
     * @return a {@link InputStream} which provides a {@code PKCS#8} private key in {@code PEM} format associated with
     * {@link #keyCertChainSupplier()}.
     * <p>
     * Each invocation of the {@link Supplier} should provide an independent instance of {@link InputStream} and the
     * caller is responsible for invoking {@link InputStream#close()}.
     */
    @Nullable
    Supplier<InputStream> keySupplier();

    /**
     * Get the password required to access the key material (e.g. from {@link #keySupplier()}).
     * @return the password required to access the key material (e.g. from {@link #keySupplier()}).
     */
    @Nullable
    String keyPassword();

    /**
     * Get the TLS protocols to enable, in the order of preference.
     *
     * @return the TLS protocols to enable, in the order of preference.
     * @see SSLEngine#setEnabledProtocols(String[])
     */
    @Nullable
    List<String> sslProtocols();

    /**
     * Get the TLS <a href="https://tools.ietf.org/html/rfc7301">ALPN</a> protocols.
     * @return the TLS <a href="https://tools.ietf.org/html/rfc7301">ALPN</a> protocols.
     */
    @Nullable
    List<String> alpnProtocols();

    /**
     * Get the cipher suites to enable, in the order of preference.
     *
     * @return the cipher suites to enable, in the order of preference.
     */
    @Nullable
    Iterable<String> ciphers();

    /**
     * Get the size of the cache used for storing SSL session objects.
     *
     * @return the size of the cache used for storing SSL session objects.
     */
    long sessionCacheSize();

    /**
     * Get the timeout for the cached SSL session objects, in seconds.
     *
     * @return the timeout for the cached SSL session objects, in seconds.
     */
    long sessionTimeout();

    /**
     * Get the {@link SslProvider} to use.
     *
     * @return the {@link SslProvider} to use.
     */
    @Nullable
    SslProvider provider();
}

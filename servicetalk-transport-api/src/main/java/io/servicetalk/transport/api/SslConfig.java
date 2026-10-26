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

import java.io.InputStream;
import java.time.Duration;
import java.util.List;
import java.util.function.Supplier;
import javax.annotation.Nullable;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLEngine;
import javax.net.ssl.SSLSessionContext;
import javax.net.ssl.TrustManagerFactory;

import static io.servicetalk.transport.api.AbstractSslConfigBuilder.DEFAULT_HANDSHAKE_TIMEOUT;

/**
 * Specifies the configuration for TLS/SSL.
 */
public interface SslConfig {

    /**
     * Get the javax {@link SSLContext} to use for transport security.
     * <p>
     * If this method return a non-null value, only the {@link SslProvider#JDK} provider will be used and the following
     * methods will be ignored:
     * <ul>
     *   <li>{@link #trustManagerFactory()}</li>
     *   <li>{@link #trustCertChainSupplier()}</li>
     *   <li>{@link #keyManagerFactory()}</li>
     *   <li>{@link #keyCertChainSupplier()}</li>
     *   <li>{@link #keySupplier()}</li>
     *   <li>{@link #keyPassword()}</li>
     * </ul>
     *
     * @return the javax {@link SSLContext} to use for transport security.
     */
    @Nullable
    default SSLContext sslContext() {
        return null;
    }

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
     * <p>
     * Note that each ALPN protocol typically requires corresponding configuration at the protocol layer and as a result
     * maybe inferred and overridden by the protocol layer.
     * @return the TLS <a href="https://tools.ietf.org/html/rfc7301">ALPN</a> protocols.
     */
    @Nullable
    List<String> alpnProtocols();

    /**
     * Get the cipher suites to enable, in the order of preference.
     *
     * @return the cipher suites to enable, in the order of preference.
     * @see #cipherSuiteFilter()
     */
    @Nullable
    List<String> ciphers();

    /**
     * Defines filtering behavior for ciphers suites.
     *
     * @return filtering behavior for ciphers suites.
     * @see #ciphers()
     */
    default CipherSuiteFilter cipherSuiteFilter() {
        return CipherSuiteFilter.PROVIDED;
    }

    /**
     * Get the size of the cache used for storing SSL session objects.
     *
     * @return the size of the cache used for storing SSL session objects.
     * @see SSLSessionContext#setSessionCacheSize(int)
     */
    long sessionCacheSize();

    /**
     * Get the timeout for the cached SSL session objects, in seconds.
     *
     * @return the timeout for the cached SSL session objects, in seconds.
     * @see SSLSessionContext#setSessionTimeout(int)
     */
    long sessionTimeout();

    /**
     * Get the {@link SslProvider} to use.
     *
     * @return the {@link SslProvider} to use.
     */
    @Nullable
    SslProvider provider();

    /**
     * Get the list of usable {@link CertificateCompressionAlgorithm CertificateCompressionAlgorithms} to advertise.
     * <p>
     * If this method returns {@code null} (by default), the default behavior of the underlying TLS implementation is
     * used, which may apply compression by default. If this method returns an empty list, certificate compression is
     * disabled and no algorithms are advertised during the TLS handshake. Note that even though they are advertised,
     * the other side is not required per RFC to compress so certificates might still be sent uncompressed.
     * <p>
     * Also note that this configuration is only applied with:
     * <ul>
     *     <li><a href="https://netty.io/wiki/forked-tomcat-native.html">BoringSSL</a> or AWS-LC implementation of
     *     {@link SslProvider#OPENSSL}.</li>
     *     <li>TLSv1.3 or above.</li>
     * </ul>
     * <p>
     * The returned value (including an empty list) is ignored when the {@link SslProvider#JDK} is used. JDK 27+
     * enables ZLIB certificate compression by default, which can only be disabled for the whole JVM by adding
     * {@code compress_certificate} to the {@code jdk.tls.client.disableExtensions} and
     * {@code jdk.tls.server.disableExtensions} system properties. Newer Netty versions also respect these properties
     * when this method returns {@code null}.
     *
     * @return the list of certificate compression algorithms to advertise, an empty list to disable certificate
     * compression, or {@code null} to use the default behavior of the underlying TLS implementation.
     * @see <a href="https://www.rfc-editor.org/info/rfc8879/">RFC8879 - TLS Certificate Compression</a>
     * @see <a href="https://bugs.openjdk.org/browse/JDK-8372526">JDK-8372526 - Add support for ZLIB TLS Certificate
     * Compression (JDK27)</a>
     * @see <a href="https://github.com/netty/netty/pull/17548">Netty 4.2.20 - Add consistent TLS certificate
     * compression support</a>
     * @see CertificateCompressionAlgorithms
     */
    @Nullable // FIXME 0.43 - remove default implementation
    default List<CertificateCompressionAlgorithm> certificateCompressionAlgorithms() {
        return null;
    }

    /**
     * Get the timeout for the handshake process.
     * <p>
     * Implementations can round the returned {@link Duration} to full time units, depending on their time granularity.
     * {@link Duration#ZERO Zero duration} disables the timeout.
     *
     * @return the timeout for the handshake process or {@link Duration#ZERO} to disable it.
     */
    default Duration handshakeTimeout() {   // FIXME 0.43 - remove default implementation
        return DEFAULT_HANDSHAKE_TIMEOUT;
    }

    /**
     * Get the preferred maximum allowed size of the certificate chain in bytes. This may not be respected
     * and depends on if the {@link SSLEngine} supports this feature.
     * @return Maximum number of bytes for the certificate chain, or {@code <=0} to use the default limit.
     */
    // FIXME 0.43 - remove default implementation
    default int maxCertificateListBytes() {
        return 0;
    }

    /**
     * Defines filtering logic for ciphers suites.
     *
     * @see #ciphers()
     */
    enum CipherSuiteFilter {
        /**
         * Will take all provided ciphers suites as-is without any filtering.
         */
        PROVIDED,

        /**
         * Will filter all provided ciphers suites out that are not supported by the current {@link SSLEngine}.
         */
        SUPPORTED
    }
}

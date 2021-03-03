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
import javax.net.ssl.SSLParameters;
import javax.net.ssl.TrustManagerFactory;

import static java.util.Objects.requireNonNull;

/**
 * Default builder for {@link ClientSslConfig} objects.
 */
public final class ClientSslConfigBuilder extends AbstractSslConfigBuilder<ClientSslConfigBuilder> {
    /**
     * See <a href="https://docs.oracle.com/javase/8/docs/technotes/guides/security/StandardNames.html#jssenames">
     * Endpoint Identification Algorithm Name</a>.
     */
    @Nullable
    private String hostnameVerificationAlgorithm = "HTTPS";
    @Nullable
    private String peerHost;
    private int peerPort = -1;
    @Nullable
    private String sniHostname;

    /**
     * Create a new instance using this JVM's {@link TrustManagerFactory#getDefaultAlgorithm()} and default
     * {@link TrustManagerFactory}.
     */
    public ClientSslConfigBuilder() {
    }

    /**
     * Create a new instance using {@code tmf} to verify trusted servers.
     * @param tmf The {@link TrustManagerFactory} used to verify trusted servers.
     */
    public ClientSslConfigBuilder(TrustManagerFactory tmf) {
        trustManager(requireNonNull(tmf));
    }

    /**
     * Create a new instance using {@code trustCertChainSupplier} to verify trusted servers.
     * @param trustCertChainSupplier the trusted certificates for verifying the remote endpoint's certificate. The input
     * stream should contain an {@code X.509} certificate chain in {@code PEM} format.
     * <p>
     * Each invocation of the {@link Supplier} should provide an independent instance of {@link InputStream} and the
     * caller is responsible for invoking {@link InputStream#close()}.
     */
    public ClientSslConfigBuilder(Supplier<InputStream> trustCertChainSupplier) {
        trustManager(trustCertChainSupplier);
    }

    @Override
    public ClientSslConfigBuilder keyManager(KeyManagerFactory kmf) {
        return super.keyManager(kmf);
    }

    @Override
    public ClientSslConfigBuilder keyManager(Supplier<InputStream> keyCertChainSupplier,
                                             Supplier<InputStream> keySupplier) {
        return super.keyManager(keyCertChainSupplier, keySupplier);
    }

    @Override
    public ClientSslConfigBuilder keyManager(Supplier<InputStream> keyCertChainSupplier,
                                             Supplier<InputStream> keySupplier,
                                             @Nullable String keyPassword) {
        return super.keyManager(keyCertChainSupplier, keySupplier, keyPassword);
    }

    /**
     * Set the algorithm to use for hostname verification to verify the
     * <a href="https://tools.ietf.org/search/rfc2818#section-3.1">server identity</a>.
     *
     * @param algorithm The algorithm to use when verifying the host name.
     * See <a href="https://docs.oracle.com/javase/8/docs/technotes/guides/security/StandardNames.html#jssenames">
     * Endpoint Identification Algorithm Name</a>
     * @return {@code this}.
     * @see SSLParameters#setEndpointIdentificationAlgorithm(String)
     */
    public ClientSslConfigBuilder hostnameVerificationAlgorithm(String algorithm) {
        this.hostnameVerificationAlgorithm = requireNonNull(algorithm);
        return this;
    }

    /**
     * Disable host name verification.
     * @deprecated Disabling hostname verification may leave you vulnerable to man-in-the-middle attacks. See
     * <a href="https://tools.ietf.org/search/rfc2818#section-3.1">server identity</a> on the risks of disabling.
     * If the expected value isn't automatically inferred use {@link #peerHost(String)} to set the expected value.
     * @return {@code this}.
     * @see SSLParameters#setEndpointIdentificationAlgorithm(String)
     */
    @Deprecated
    public ClientSslConfigBuilder disableHostnameVerification() {
        hostnameVerificationAlgorithm = null;
        return this;
    }

    /**
     * Set the non-authoritative name of the peer.
     * @param peerHost the non-authoritative name of the peer.
     * @return {@code this}.
     */
    public ClientSslConfigBuilder peerHost(String peerHost) {
        if (peerHost.isEmpty()) {
            throw new IllegalArgumentException("peerHost cannot be empty");
        }
        this.peerHost = peerHost;
        return this;
    }

    /**
     * Set the non-authoritative port of the peer.
     * @param peerPort the non-authoritative port of the peer, or {@code -1} if unavailable (which may prevent
     * <a href="https://tools.ietf.org/html/rfc5077">session resumption</a>).
     * @return {@code this}.
     */
    public ClientSslConfigBuilder peerPort(int peerPort) {
        if (peerPort < -1) {
            throw new IllegalArgumentException("peerPort: " + peerPort + "(expected >=-1)");
        }
        this.peerPort = peerPort;
        return this;
    }

    /**
     * Set the <a href="https://tools.ietf.org/html/rfc6066#section-3">SNI</a> host name.
     * @param sniHostname <a href="https://tools.ietf.org/html/rfc6066#section-3">SNI</a> host name.
     * @return {@code this}.
     */
    public ClientSslConfigBuilder sniHostname(String sniHostname) {
        if (sniHostname.isEmpty()) {
            throw new IllegalArgumentException("sniHostname cannot be empty");
        }
        this.sniHostname = sniHostname;
        return this;
    }

    /**
     * Build a new {@link ClientSslConfig}.
     * @return a new {@link ClientSslConfig}.
     */
    public ClientSslConfig build() {
        return new DefaultClientSslConfig(hostnameVerificationAlgorithm, peerHost, peerPort, sniHostname,
                trustManager(), trustCertChainSupplier(), keyManager(), keyCertChainSupplier(), keySupplier(),
                keyPassword(), sslProtocols(), alpnProtocols(), ciphers(), sessionCacheSize(), sessionTimeout(),
                provider());
    }

    @Override
    protected ClientSslConfigBuilder thisT() {
        return this;
    }

    private static final class DefaultClientSslConfig extends AbstractSslConfig implements ClientSslConfig {
        @Nullable
        private final String hostnameVerificationAlgorithm;
        @Nullable
        private final String peerHost;
        private final int peerPort;
        @Nullable
        private final String sniHostname;

        DefaultClientSslConfig(@Nullable final String hostnameVerificationAlgorithm, @Nullable final String peerHost,
                               final int peerPort, @Nullable final String sniHostname,
                               @Nullable final TrustManagerFactory trustManagerFactory,
                               @Nullable final Supplier<InputStream> trustCertChainSupplier,
                               @Nullable final KeyManagerFactory keyManagerFactory,
                               @Nullable final Supplier<InputStream> keyCertChainSupplier,
                               @Nullable final Supplier<InputStream> keySupplier, @Nullable final String keyPassword,
                               @Nullable final List<String> sslProtocols, @Nullable final List<String> alpnProtocols,
                               @Nullable final List<String> ciphers, final long sessionCacheSize,
                               final long sessionTimeout, @Nullable final SslProvider provider) {
            super(trustManagerFactory, trustCertChainSupplier, keyManagerFactory, keyCertChainSupplier, keySupplier,
                    keyPassword, sslProtocols, alpnProtocols, ciphers, sessionCacheSize, sessionTimeout, provider);
            this.hostnameVerificationAlgorithm = hostnameVerificationAlgorithm;
            this.peerHost = peerHost;
            this.peerPort = peerPort;
            this.sniHostname = sniHostname;
        }

        @Nullable
        @Override
        public String hostnameVerificationAlgorithm() {
            return hostnameVerificationAlgorithm;
        }

        @Nullable
        @Override
        public String peerHost() {
            return peerHost;
        }

        @Override
        public int peerPort() {
            return peerPort;
        }

        @Nullable
        @Override
        public String sniHostname() {
            return sniHostname;
        }
    }
}

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
public final class DefaultClientSslConfigBuilder extends AbstractSslConfigBuilder<DefaultClientSslConfigBuilder> {
    /**
     * See <a href="https://docs.oracle.com/javase/8/docs/technotes/guides/security/StandardNames.html#jssenames">
     * Endpoint Identification Algorithm Name</a>.
     */
    @Nullable
    private String hostnameVerificationAlgorithm = "HTTPS";
    private String peerHost = "";
    private int peerPort = -1;
    @Nullable
    private String sniHostname;

    /**
     * Create a new instance using this JVM's {@link TrustManagerFactory#getDefaultAlgorithm()} and default
     * {@link TrustManagerFactory}.
     */
    public DefaultClientSslConfigBuilder() {
    }

    /**
     * Create a new instance using {@code tmf} to verify trusted servers.
     * @param tmf The {@link TrustManagerFactory} used to verify trusted servers.
     */
    public DefaultClientSslConfigBuilder(TrustManagerFactory tmf) {
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
    public DefaultClientSslConfigBuilder(Supplier<InputStream> trustCertChainSupplier) {
        trustManager(trustCertChainSupplier);
    }

    @Override
    public DefaultClientSslConfigBuilder keyManager(KeyManagerFactory kmf) {
        return super.keyManager(kmf);
    }

    @Override
    public DefaultClientSslConfigBuilder keyManager(Supplier<InputStream> keyCertChainSupplier,
                                                    Supplier<InputStream> keySupplier) {
        return super.keyManager(keyCertChainSupplier, keySupplier);
    }

    @Override
    public DefaultClientSslConfigBuilder keyManager(Supplier<InputStream> keyCertChainSupplier,
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
    public DefaultClientSslConfigBuilder hostnameVerificationAlgorithm(String algorithm) {
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
    public DefaultClientSslConfigBuilder disableHostnameVerification() {
        hostnameVerificationAlgorithm = null;
        return this;
    }

    /**
     * Set the non-authoritative name of the peer, will be used for host name verification.
     * @param peerHost the non-authoritative name of the peer, will be used for host name verification.
     * @return {@code this}.
     */
    public DefaultClientSslConfigBuilder peerHost(String peerHost) {
        this.peerHost = requireNonNull(peerHost);
        return this;
    }

    /**
     * Set the non-authoritative port of the peer.
     * @param peerPort the non-authoritative port of the peer.
     * @return {@code this}.
     */
    public DefaultClientSslConfigBuilder peerPort(int peerPort) {
        this.peerPort = peerPort;
        return this;
    }

    /**
     * set the <a href="https://tools.ietf.org/html/rfc6066#section-3">SNI</a> host name.
     *
     * @param sniHostname <a href="https://tools.ietf.org/html/rfc6066#section-3">SNI</a> host name.
     * @return {@code this}.
     */
    public DefaultClientSslConfigBuilder sniHostname(String sniHostname) {
        this.sniHostname = requireNonNull(sniHostname);
        return this;
    }

    /**
     * Build a new {@link ClientSslConfig}.
     * @return a new {@link ClientSslConfig}.
     */
    public ClientSslConfig build() {
        return new AbstractClientSslConfig(hostnameVerificationAlgorithm, peerHost, peerPort, sniHostname,
                trustManager(), trustCertChainSupplier(), keyManager(), keyCertChainSupplier(), keySupplier(),
                keyPassword(), sslProtocols(), alpnProtocols(), ciphers(), sessionCacheSize(), sessionTimeout(),
                provider());
    }

    @Override
    protected DefaultClientSslConfigBuilder thisT() {
        return this;
    }

    private static final class AbstractClientSslConfig extends AbstractSslConfig implements ClientSslConfig {
        @Nullable
        private final String hostnameVerificationAlgorithm;
        private final String peerHost;
        private final int peerPort;
        @Nullable
        private final String sniHostname;

        AbstractClientSslConfig(@Nullable final String hostnameVerificationAlgorithm, final String peerHost,
                                final int peerPort, @Nullable final String sniHostname,
                                @Nullable final TrustManagerFactory trustManagerFactory,
                                @Nullable final Supplier<InputStream> trustCertChainSupplier,
                                @Nullable final KeyManagerFactory keyManagerFactory,
                                @Nullable final Supplier<InputStream> keyCertChainSupplier,
                                @Nullable final Supplier<InputStream> keySupplier, @Nullable final String keyPassword,
                                @Nullable final List<String> sslProtocols, @Nullable final List<String> alpnProtocols,
                                @Nullable final Iterable<String> ciphers, final long sessionCacheSize,
                                final long sessionTimeout, @Nullable final SslProvider provider) {
            super(trustManagerFactory, trustCertChainSupplier, keyManagerFactory, keyCertChainSupplier, keySupplier,
                    keyPassword, sslProtocols, alpnProtocols, ciphers, sessionCacheSize, sessionTimeout, provider);
            this.hostnameVerificationAlgorithm = hostnameVerificationAlgorithm;
            this.peerHost = requireNonNull(peerHost);
            this.peerPort = peerPort;
            this.sniHostname = sniHostname;
        }

        @Nullable
        @Override
        public String hostnameVerificationAlgorithm() {
            return hostnameVerificationAlgorithm;
        }

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

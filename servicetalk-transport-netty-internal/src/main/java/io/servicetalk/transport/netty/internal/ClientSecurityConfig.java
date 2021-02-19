/*
 * Copyright © 2019 Apple Inc. and the ServiceTalk project authors
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
package io.servicetalk.transport.netty.internal;

import io.servicetalk.transport.api.SecurityConfigurator.SslProvider;

import java.io.InputStream;
import java.util.function.Supplier;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.TrustManagerFactory;

import static java.util.Arrays.asList;
import static java.util.Objects.requireNonNull;

/**
 * Client security configuration.
 */
public class ClientSecurityConfig extends ReadOnlyClientSecurityConfig {
    /**
     * Creates new instance.
     *
     * @param peerHost the non-authoritative name of the peer, will be used for host name verification (if enabled).
     * @param peerPort the non-authoritative port of the peer.
     */
    public ClientSecurityConfig(final String peerHost, final int peerPort) {
        super(peerHost, peerPort);
    }

    /**
     * Determines what algorithm to use for hostname verification.
     * See <a href="https://docs.oracle.com/javase/8/docs/technotes/guides/security/StandardNames.html#jssenames">
     * Endpoint Identification Algorithm Name</a>
     *
     * @param hostNameVerificationAlgorithm The algorithm to use when verifying the host name.
     */
    public void hostNameVerificationAlgorithm(final String hostNameVerificationAlgorithm) {
        this.hostnameVerificationAlgorithm = requireNonNull(hostNameVerificationAlgorithm);
    }

    /**
     * Set the non-authoritative name of the peer, will be used for host name verification (if enabled).
     * @param peerHost the non-authoritative name of the peer, will be used for host name verification (if enabled).
     */
    public void peerHost(final String peerHost) {
        this.peerHost = requireNonNull(peerHost);
    }

    /**
     * Set the non-authoritative port of the peer.
     * @param peerPort the non-authoritative port of the peer.
     */
    public void peerPort(final int peerPort) {
        this.peerPort = peerPort;
    }

    /**
     * Set the <a href="https://tools.ietf.org/html/rfc6066#section-3">SNI</a> host name.
     *
     * @param sniHostname The <a href="https://tools.ietf.org/html/rfc6066#section-3">SNI</a> host name.
     */
    public void sniHostname(final String sniHostname) {
        this.sniHostname = requireNonNull(sniHostname);
    }

    /**
     * Disable verification of the <a href="https://tools.ietf.org/search/rfc2818#section-3.1">server identity</a>.
     */
    public void disableHostnameVerification() {
        hostnameVerificationAlgorithm = null;
    }

    /**
     * Trusted certificates for verifying the remote endpoint's certificate. The input stream should
     * contain an {@code X.509} certificate chain in {@code PEM} format.
     *
     * @param trustCertChainSupplier a supplier for the certificate chain input stream.
     */
    public void trustManager(final Supplier<InputStream> trustCertChainSupplier) {
        this.trustCertChainSupplier = requireNonNull(trustCertChainSupplier);
    }

    /**
     * Trust manager for verifying the remote endpoint's certificate.
     * The {@link TrustManagerFactory} which take preference over any configured {@link Supplier}.
     *
     * @param trustManagerFactory the {@link TrustManagerFactory} to use.
     */
    public void trustManager(final TrustManagerFactory trustManagerFactory) {
        this.trustManagerFactory = requireNonNull(trustManagerFactory);
    }

    /**
     * The SSL protocols to enable, in the order of preference.
     *
     * @param protocols the protocols to use.
     */
    public void protocols(final String... protocols) {
        this.protocols = asList(protocols);
    }

    /**
     * The cipher suites to enable, in the order of preference.
     *
     * @param ciphers the ciphers to use.
     */
    public void ciphers(final Iterable<String> ciphers) {
        this.ciphers = requireNonNull(ciphers);
    }

    /**
     * Set the size of the cache used for storing SSL session objects.
     *
     * @param sessionCacheSize the cache size.
     */
    public void sessionCacheSize(final long sessionCacheSize) {
        this.sessionCacheSize = sessionCacheSize;
    }

    /**
     * Set the timeout for the cached SSL session objects, in seconds.
     *
     * @param sessionTimeout the session timeout.
     */
    public void sessionTimeout(final long sessionTimeout) {
        this.sessionTimeout = sessionTimeout;
    }

    /**
     * Sets the {@link SslProvider} to use.
     *
     * @param provider the provider.
     */
    public void provider(final SslProvider provider) {
        this.provider = requireNonNull(provider);
    }

    /**
     * Identifying certificate for this host. {@code keyManagerFactory} may be {@code null}, which disables mutual
     * authentication. The {@link KeyManagerFactory} which take preference over any configured {@link Supplier}.
     *
     * @param keyManagerFactory an {@link KeyManagerFactory}.
     */
    public void keyManager(final KeyManagerFactory keyManagerFactory) {
        this.keyManagerFactory = requireNonNull(keyManagerFactory);
    }

    /**
     * Identifying certificate for this host. {@code keyCertChainInputStream} and {@code keyInputStream} may
     * be {@code null}, which disables mutual authentication.
     *
     * @param keyCertChainSupplier a {@link Supplier} that will provide an input stream for a {@code X.509} certificate
     * chain in {@code PEM} format.
     * @param keySupplier an {@link Supplier} that will provide an input stream for a PKCS#8 private key in PEM format.
     */
    public void keyManager(final Supplier<InputStream> keyCertChainSupplier, final Supplier<InputStream> keySupplier) {
        this.keyCertChainSupplier = requireNonNull(keyCertChainSupplier);
        this.keySupplier = requireNonNull(keySupplier);
        this.keyPassword = null;
    }

    /**
     * Identifying certificate for this host. {@code keyCertChainInputStream} and {@code keyInputStream} may
     * be {@code null}, which disables mutual authentication.
     *
     * @param keyCertChainSupplier an {@link Supplier} that will provide an input stream for a {@code X.509} certificate
     * chain in {@code PEM} format.
     * @param keySupplier an {@link Supplier} that will provide an input stream for a PKCS#8 private key in PEM format.
     * @param keyPassword the password of the {@code keyInputStream}.
     */
    public void keyManager(final Supplier<InputStream> keyCertChainSupplier, final Supplier<InputStream> keySupplier,
                           final String keyPassword) {
        this.keyCertChainSupplier = requireNonNull(keyCertChainSupplier);
        this.keySupplier = requireNonNull(keySupplier);
        this.keyPassword = requireNonNull(keyPassword);
    }

    /**
     * Returns this config as a {@link ReadOnlyClientSecurityConfig}.
     *
     * @return This config as a {@link ReadOnlyClientSecurityConfig}.
     */
    public ReadOnlyClientSecurityConfig asReadOnly() {
        if (trustManagerFactory == null && trustCertChainSupplier == null) {
            throw new IllegalStateException("Client security config requires trust material!");
        }
        return new ReadOnlyClientSecurityConfig(this);
    }
}

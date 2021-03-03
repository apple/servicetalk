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

import io.servicetalk.transport.api.ClientSslConfig;
import io.servicetalk.transport.api.DefaultClientSslConfigBuilder;
import io.servicetalk.transport.api.SecurityConfigurator.SslProvider;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.TrustManagerFactory;

import static java.util.Arrays.asList;
import static java.util.Objects.requireNonNull;

/**
 * Client security configuration.
 * @deprecated Use {@link ClientSslConfig}.
 */
@Deprecated
public class ClientSecurityConfig extends ReadOnlyClientSecurityConfig {
    /**
     * Determines what algorithm to use for hostname verification.
     *
     * @param hostNameVerificationAlgorithm The algorithm to use when verifying the host name.
     */
    public void hostNameVerificationAlgorithm(final String hostNameVerificationAlgorithm) {
        this.hostnameVerificationAlgorithm = requireNonNull(hostNameVerificationAlgorithm);
    }

    /**
     * Determines what algorithm to use for hostname verification.
     *
     * @param hostNameVerificationAlgorithm The algorithm to use when verifying the host name.
     * @param hostNameVerificationHost the host name used to verify the
     * <a href="https://tools.ietf.org/search/rfc2818#section-3.1">server identity</a>.
     */
    public void hostNameVerification(final String hostNameVerificationAlgorithm,
                                     final String hostNameVerificationHost) {
        this.hostnameVerificationAlgorithm = requireNonNull(hostNameVerificationAlgorithm);
        this.hostNameVerificationHost = hostNameVerificationHost;
    }

    /**
     * Determines what algorithm to use for hostname verification.
     *
     * @param hostNameVerificationAlgorithm The algorithm to use when verifying the host name.
     * @param hostNameVerificationHost the host name used to verify the
     * <a href="https://tools.ietf.org/search/rfc2818#section-3.1">server identity</a>.
     * @param hostNameVerificationPort The port which maybe used to verify the
     * <a href="https://tools.ietf.org/search/rfc2818#section-3.1">server identity</a>.
     */
    public void hostNameVerification(final String hostNameVerificationAlgorithm,
                                     final String hostNameVerificationHost, final int hostNameVerificationPort) {
        this.hostnameVerificationAlgorithm = requireNonNull(hostNameVerificationAlgorithm);
        this.hostNameVerificationHost = hostNameVerificationHost;
        this.hostNameVerificationPort = hostNameVerificationPort;
    }

    /**
     * Set the host name used to verify the <a href="https://tools.ietf.org/search/rfc2818#section-3.1">server
     * identity</a>.
     *
     * @param hostNameVerificationHost the host name used to verify the
     * <a href="https://tools.ietf.org/search/rfc2818#section-3.1">server identity</a>.
     */
    public void hostNameVerification(final String hostNameVerificationHost) {
        this.hostNameVerificationHost = hostNameVerificationHost;
    }

    /**
     * Set the host name and port used to verify the <a href="https://tools.ietf.org/search/rfc2818#section-3.1">server
     * identity</a>.
     *
     * @param hostNameVerificationHost the host name used to verify the
     * <a href="https://tools.ietf.org/search/rfc2818#section-3.1">server identity</a>.
     * @param hostNameVerificationPort The port which maybe used to verify the
     * <a href="https://tools.ietf.org/search/rfc2818#section-3.1">server identity</a>.
     */
    public void hostNameVerification(final String hostNameVerificationHost, final int hostNameVerificationPort) {
        this.hostNameVerificationHost = hostNameVerificationHost;
        this.hostNameVerificationPort = hostNameVerificationPort;
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
        hostNameVerificationHost = null;
        hostNameVerificationPort = -1;
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
        this.ciphers = toList(ciphers);
    }

    static <T> List<T> toList(final Iterable<T> ciphers) {
        final List<T> list;
        if (ciphers instanceof List) {
            list = (List<T>) ciphers;
        } else {
            list = new ArrayList<>();
            ciphers.forEach(list::add);
        }
        return list;
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
     * @param keySupplier an {@link Supplier} that will provide an input stream for a KCS#8 private key in PEM format.
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
     * @param keySupplier an {@link Supplier} that will provide an input stream for a KCS#8 private key in PEM format.
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
        return new ReadOnlyClientSecurityConfig(this);
    }

    /**
     * Build a new {@link ClientSslConfig}.
     * @return a new {@link ClientSslConfig}.
     */
    public ClientSslConfig asSslConfig() {
        final DefaultClientSslConfigBuilder builder;
        if (trustManagerFactory != null) {
            builder = new DefaultClientSslConfigBuilder(trustManagerFactory);
        } else if (trustCertChainSupplier != null) {
            builder = new DefaultClientSslConfigBuilder(trustCertChainSupplier);
        } else {
            throw new IllegalStateException("required trust material not set");
        }

        if (hostnameVerificationAlgorithm == null) {
            builder.disableHostnameVerification();
        } else {
            builder.hostnameVerificationAlgorithm(hostnameVerificationAlgorithm);
        }
        if (hostNameVerificationHost != null) {
            builder.peerHost(hostNameVerificationHost);
            builder.peerPort(hostNameVerificationPort);
        }
        if (sniHostname != null) {
            builder.sniHostname(sniHostname);
        }

        if (keyManagerFactory != null) {
            builder.keyManager(keyManagerFactory);
        } else if (keyCertChainSupplier != null) {
            assert keySupplier != null;
            builder.keyManager(keyCertChainSupplier, keySupplier, keyPassword);
        }
        if (protocols != null) {
            builder.sslProtocols(protocols);
        }
        if (ciphers != null) {
            builder.ciphers(ciphers);
        }
        builder.sessionCacheSize(sessionCacheSize);
        builder.sessionTimeout(sessionTimeout);

        if (provider == SslProvider.JDK) {
            builder.provider(io.servicetalk.transport.api.SslProvider.JDK);
        } else if (provider == SslProvider.OPENSSL) {
            builder.provider(io.servicetalk.transport.api.SslProvider.OPENSSL);
        }
        return builder.build();
    }
}

/*
 * Copyright © 2018 Apple Inc. and the ServiceTalk project authors
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
import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.Supplier;
import javax.annotation.Nullable;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLParameters;
import javax.net.ssl.TrustManagerFactory;

import static java.util.Collections.unmodifiableList;
import static java.util.Objects.requireNonNull;

/**
 * Builder for configuring a new SslContext for creation.
 */
public final class SslConfigBuilder {

    @SuppressWarnings("rawtypes")
    private static final Supplier NULL_SUPPLIER = () -> null;

    private static final String DEFAULT_HOSTNAME_VERIFICATION_ALGORITHM = "HTTPS";

    private final boolean forServer;
    @Nullable
    private TrustManagerFactory trustManagerFactory;
    @Nullable
    private KeyManagerFactory keyManagerFactory;
    @Nullable
    private String keyPassword;
    @Nullable
    private Iterable<String> ciphers;
    private long sessionCacheSize;
    private long sessionTimeout;
    private SslConfig.ClientAuth clientAuth = SslConfig.ClientAuth.NONE;
    private Supplier<InputStream> trustCertChainSupplier = nullSupplier();
    private Supplier<InputStream> keyCertChainSupplier = nullSupplier();
    private Supplier<InputStream> keySupplier = nullSupplier();
    private ApplicationProtocolConfig apn = ApplicationProtocolConfig.DISABLED;
    private SslConfig.SslProvider provider = SslConfig.SslProvider.AUTO;
    @Nullable
    private List<String> protocols;
    @Nullable
    private String hostNameVerificationAlgorithm = DEFAULT_HOSTNAME_VERIFICATION_ALGORITHM;
    @Nullable
    private String hostNameVerificationHost;
    /**
     * Only valid if {@link #hostNameVerificationHost} is valid;
     */
    private int hostNameVerificationPort = -1;

    private SslConfigBuilder(boolean forServer) {
        this.forServer = forServer;
    }

    private SslConfigBuilder(String hostName, int port) {
        hostNameVerificationHost = requireNonNull(hostName);
        hostNameVerificationPort = port;
        forServer = false; // host name verification currently only support on the client
    }

    /**
     * Creates a builder for new client-side {@link SslConfig}.
     * <p>
     * This method does not have enough information to ensure
     * <a href="https://tools.ietf.org/search/rfc2818#section-3.1">server identity</a> is verified. Use
     * {@link #forClient(String, int)} instead for
     * <a href="https://tools.ietf.org/search/rfc2818#section-3.1">server identity</a> verification.
     *
     * @return a new {@link SslConfigBuilder} for clients.
     */
    public static SslConfigBuilder forClientWithoutServerIdentity() {
        return new SslConfigBuilder(false);
    }

    /**
     * Creates a builder for new client-side {@link SslConfig} which verifies the
     * <a href="https://tools.ietf.org/search/rfc2818#section-3.1">server identity</a>.
     *
     * @param hostName The non-authoritative name of the host. This is used to verify the
     * <a href="https://tools.ietf.org/search/rfc2818#section-3.1">server identity</a>.
     * @param port The non-authoritative port. This maybe used to verify
     * <a href="https://tools.ietf.org/search/rfc2818#section-3.1">server identity</a>.
     * @return a new {@link SslConfigBuilder} for clients.
     */
    public static SslConfigBuilder forClient(String hostName, int port) {
        return new SslConfigBuilder(hostName, port);
    }

    /**
     * Creates a builder for new client-side {@link SslConfig} which verifies the
     * <a href="https://tools.ietf.org/search/rfc2818#section-3.1">server identity</a>.
     *
     * @param hostAndPort The non-authoritative host name and port used to verify the
     * <a href="https://tools.ietf.org/search/rfc2818#section-3.1">server identity</a>.
     * @return a new {@link SslConfigBuilder} for clients.
     */
    public static SslConfigBuilder forClient(HostAndPort hostAndPort) {
        return forClient(hostAndPort.hostName(), hostAndPort.port());
    }

    /**
     * Creates a builder for new client-side {@link SslConfig} which verifies the
     * <a href="https://tools.ietf.org/search/rfc2818#section-3.1">server identity</a>.
     *
     * @param address Provides the non-authoritative host name and port used to verify the
     * <a href="https://tools.ietf.org/search/rfc2818#section-3.1">server identity</a>.
     * @return a new {@link SslConfigBuilder} for clients.
     */
    public static SslConfigBuilder forClient(InetSocketAddress address) {
        return forClient(address.getHostString(), address.getPort());
    }

    /**
     * Creates a builder for new server-side {@link SslConfig}.
     *
     * @param keyCertChainSupplier an {@link Supplier} that will provide an input stream for a X.509 certificate chain
     * in PEM format.
     * <p>
     * The responsibility to call {@link InputStream#close()} is transferred to callers of the {@link Supplier}.
     * If this is not the desired behavior then wrap the {@link InputStream} and override {@link InputStream#close()}.
     * @param keySupplier an {@link Supplier} that will provide an input stream for a KCS#8 private key in PEM format.
     * <p>
     * The responsibility to call {@link InputStream#close()} is transferred to callers of the {@link Supplier}.
     * If this is not the desired behavior then wrap the {@link InputStream} and override {@link InputStream#close()}.
     * @return a new {@link SslConfigBuilder} for servers.
     */
    public static SslConfigBuilder forServer(Supplier<InputStream> keyCertChainSupplier, Supplier<InputStream> keySupplier) {
        return new SslConfigBuilder(true).keyManager(keyCertChainSupplier, keySupplier);
    }

    /**
     * Creates a builder for new server-side {@link SslConfig}.
     **
     * @param keyCertChainSupplier an {@link Supplier} that will provide an input stream for a X.509 certificate chain in PEM format.
     * <p>
     * The responsibility to call {@link InputStream#close()} is transferred to callers of the returned {@link Supplier}.
     * If this is not the desired behavior then wrap the {@link InputStream} and override {@link InputStream#close()}.
     * @param keySupplier an {@link Supplier} that will provide an input stream for a KCS#8 private key in PEM format.
     * <p>
     * The responsibility to call {@link InputStream#close()} is transferred to callers of the {@link Supplier}.
     * If this is not the desired behavior then wrap the {@link InputStream} and override {@link InputStream#close()}.
     * @param keyPassword the password of the {@code keyFile}, or {@code null} if it's not
     *     password-protected
     * @return a new {@link SslConfigBuilder} for servers.
     */
    public static SslConfigBuilder forServer(
            Supplier<InputStream> keyCertChainSupplier, Supplier<InputStream> keySupplier, String keyPassword) {
        return new SslConfigBuilder(true).keyManager(keyCertChainSupplier, keySupplier, keyPassword);
    }

    /**
     * Creates a builder for new server-side {@link SslConfig}.
     **
     * @param keyManagerFactory a {@link KeyManagerFactory}.
     * @return a new {@link SslConfigBuilder} for servers.
     */
    public static SslConfigBuilder forServer(KeyManagerFactory keyManagerFactory) {
        return new SslConfigBuilder(true).keyManager(keyManagerFactory);
    }

    /**
     * Trusted certificates for verifying the remote endpoint's certificate. The input stream should
     * contain an X.509 certificate chain in PEM format. {@code null} uses the system default.
     *
     * @param trustCertChainSupplier a supplier for the certificate chain input stream.
     * <p>
     * The responsibility to call {@link InputStream#close()} is transferred to callers of the returned {@link Supplier}.
     * If this is not the desired behavior then wrap the {@link InputStream} and override {@link InputStream#close()}.
     * @return self.
     */
    public SslConfigBuilder trustManager(Supplier<InputStream> trustCertChainSupplier) {
        this.trustCertChainSupplier = trustCertChainSupplier;
        trustManagerFactory = null;
        return this;
    }

    /**
     * Trusted manager for verifying the remote endpoint's certificate. {@code null} uses the system default.
     * The {@link TrustManagerFactory} which take preference over any configured {@link Supplier}.
     *
     * @param trustManagerFactory the {@link TrustManagerFactory} to use.
     * @return self.
     */
    public SslConfigBuilder trustManager(TrustManagerFactory trustManagerFactory) {
        trustCertChainSupplier = nullSupplier();
        this.trustManagerFactory = trustManagerFactory;
        return this;
    }

    /**
     * Identifying certificate for this host. {@code keyManagerFactory} may
     * be {@code null} for client contexts, which disables mutual authentication.
     * The {@link KeyManagerFactory} which take preference over any configured {@link Supplier}.
     *
     * @param keyManagerFactory an {@link KeyManagerFactory}.
     * @return self.
     */
    public SslConfigBuilder keyManager(KeyManagerFactory keyManagerFactory) {
        this.keyCertChainSupplier = nullSupplier();
        this.keySupplier = nullSupplier();
        this.keyPassword = null;
        this.keyManagerFactory = keyManagerFactory;
        return this;
    }

    /**
     * Identifying certificate for this host. {@code keyCertChainInputStream} and {@code keyInputStream} may
     * be {@code null} for client contexts, which disables mutual authentication.
     *
     * @param keyCertChainSupplier an {@link Supplier} that will provide an input stream for a X.509 certificate chain in PEM format.
     * <p>
     * The responsibility to call {@link InputStream#close()} is transferred to callers of the {@link Supplier}.
     * If this is not the desired behavior then wrap the {@link InputStream} and override {@link InputStream#close()}.
     * @param keySupplier an {@link Supplier} that will provide an input stream for a KCS#8 private key in PEM format.
     * <p>
     * The responsibility to call {@link InputStream#close()} is transferred to callers of the {@link Supplier}.
     * If this is not the desired behavior then wrap the {@link InputStream} and override {@link InputStream#close()}.
     * @return self.
     */
    public SslConfigBuilder keyManager(Supplier<InputStream> keyCertChainSupplier, Supplier<InputStream> keySupplier) {
        return keyManager(keyCertChainSupplier, keySupplier, null);
    }

    /**
     * Identifying certificate for this host. {@code keyCertChainInputStream} and {@code keyInputStream} may
     * be {@code null} for client contexts, which disables mutual authentication.
     *
     * @param keyCertChainSupplier an {@link Supplier} that will provide an input stream for a X.509 certificate chain in PEM format.
     * <p>
     * The responsibility to call {@link InputStream#close()} is transferred to callers of the {@link Supplier}.
     * If this is not the desired behavior then wrap the {@link InputStream} and override {@link InputStream#close()}.
     * @param keySupplier an {@link Supplier} that will provide an input stream for a KCS#8 private key in PEM format.
     * <p>
     * The responsibility to call {@link InputStream#close()} is transferred to callers of the {@link Supplier}.
     * If this is not the desired behavior then wrap the {@link InputStream} and override {@link InputStream#close()}.
     * @param keyPassword the password of the {@code keyInputStream}, or {@code null} if it's not
     *     password-protected
     * @return self.
     */
    public SslConfigBuilder keyManager(Supplier<InputStream> keyCertChainSupplier, Supplier<InputStream> keySupplier,
                                       @Nullable String keyPassword) {
        keyManagerFactory = null;
        this.keyCertChainSupplier = keyCertChainSupplier;
        this.keySupplier = keySupplier;
        this.keyPassword = keyPassword;
        return this;
    }

    /**
     * The protocols to enable, in the order of preference. {@code null} to use default protocols.
     *
     * @param protocols the protocols to use.
     * @return self.
     */
    public SslConfigBuilder protocols(@Nullable String... protocols) {
        if (protocols == null) {
            this.protocols = null;
        } else if (protocols.length == 0) {
            throw new IllegalArgumentException("protocols must contain at least one element");
        } else {
            this.protocols = Arrays.asList(protocols);
        }
        return this;
    }

    /**
     * Application protocol negotiation configuration.
     *
     * @param apn the configuration to use.
     * @return self.
     */
    public SslConfigBuilder applicationProtocolConfig(ApplicationProtocolConfig apn) {
        this.apn = apn;
        return this;
    }

    /**
     * The cipher suites to enable, in the order of preference. {@code null} to use default
     * cipher suites.
     *
     * @param ciphers the ciphers to use.
     * @return self.
     */
    public SslConfigBuilder ciphers(@Nullable Iterable<String> ciphers) {
        this.ciphers = ciphers;
        return this;
    }

    /**
     * Set the size of the cache used for storing SSL session objects. {@code 0} to use the
     * default value.
     *
     * @param sessionCacheSize the cache size.
     * @return self.
     */
    public SslConfigBuilder sessionCacheSize(long sessionCacheSize) {
        this.sessionCacheSize = sessionCacheSize;
        return this;
    }

    /**
     * Set the timeout for the cached SSL session objects, in seconds. {@code 0} to use the
     * default value.
     *
     * @param sessionTimeout the session timeout.
     * @return self.
     */
    public SslConfigBuilder sessionTimeout(long sessionTimeout) {
        this.sessionTimeout = sessionTimeout;
        return this;
    }

    /**
     * Sets the client authentication mode. Only supported when configured for server mode.
     *
     * @param clientAuth the auth configuration to use.
     * @return self.
     */
    public SslConfigBuilder clientAuth(SslConfig.ClientAuth clientAuth) {
        if (!forServer) {
            throw new UnsupportedOperationException("Only supported in server mode");
        }
        this.clientAuth = requireNonNull(clientAuth);
        return this;
    }

    /**
     * Sets the {@link SslConfig.SslProvider} to use.
     *
     * @param provider the provider.
     * @return self.
     */
    public SslConfigBuilder provider(SslConfig.SslProvider provider) {
        this.provider = requireNonNull(provider);
        return this;
    }

    /**
     * Determines what algorithm to use for hostname verification.
     * <p>
     * Disable at your own risk! Disabling this will leave you vulnerable to MITM attacks.
     * <p>
     * Currently only supported for client use cases.
     * @param hostNameVerificationAlgorithm The algorithm to use when verifying the host name.
     * @return the algorithm to use for hostname verification.
     * @see SSLParameters#setEndpointIdentificationAlgorithm(String)
     */
    public SslConfigBuilder hostNameVerificationAlgorithm(@Nullable String hostNameVerificationAlgorithm) {
        if (forServer) {
            throw new UnsupportedOperationException("only supported for client mode");
        }
        if (hostNameVerificationAlgorithm == null && hostNameVerificationHost != null) {
            throw new IllegalArgumentException(
                    "hostNameVerificationAlgorithm cannot be null while hostNameVerificationHost is non-null");
        }
        this.hostNameVerificationAlgorithm = hostNameVerificationAlgorithm;
        return this;
    }

    /**
     * Build and return a new {@link SslConfig}.
     *
     * @return a new {@link SslConfig}.
     */
    public SslConfig build() {
        return new SslConfigImpl(forServer, trustManagerFactory, trustCertChainSupplier, keyManagerFactory,
                keyCertChainSupplier, keySupplier, keyPassword, ciphers, sessionCacheSize, sessionTimeout, clientAuth,
                apn, provider, protocols, hostNameVerificationAlgorithm, hostNameVerificationHost,
                hostNameVerificationPort);
    }

    @SuppressWarnings("unchecked")
    private static <T> Supplier<T> nullSupplier() {
        return NULL_SUPPLIER;
    }

    /**
     * A configuration for SSL/TLS.
     */
    private static final class SslConfigImpl implements SslConfig {
        private final boolean forServer;
        @Nullable
        private final TrustManagerFactory trustManagerFactory;
        @Nullable
        private final KeyManagerFactory keyManagerFactory;
        @Nullable
        private final String keyPassword;
        @Nullable
        private final Iterable<String> ciphers;
        private final long sessionCacheSize;
        private final long sessionTimeout;
        private final ClientAuth clientAuth;
        private final Supplier<InputStream> trustCertChainSupplier;
        private final Supplier<InputStream> keyCertChainSupplier;
        private final Supplier<InputStream> keySupplier;
        private final ApplicationProtocolConfig apn;
        private final SslProvider provider;
        @Nullable
        private final List<String> protocols;
        @Nullable
        private final String hostnameVerificationAlgorithm;
        @Nullable
        private final String hostNameVerificationHost;
        /**
         * Only valid if {@link #hostNameVerificationHost} is valid.
         */
        private final int hostNameVerificationPort;

        SslConfigImpl(boolean forServer, @Nullable TrustManagerFactory trustManagerFactory,
                      Supplier<InputStream> trustCertChainSupplier, @Nullable KeyManagerFactory keyManagerFactory,
                      Supplier<InputStream> keyCertChainSupplier, Supplier<InputStream> keySupplier,
                      @Nullable String keyPassword, @Nullable Iterable<String> ciphers, long sessionCacheSize,
                      long sessionTimeout, ClientAuth clientAuth, ApplicationProtocolConfig apn, SslProvider provider,
                      @Nullable List<String> protocols, @Nullable String hostnameVerificationAlgorithm,
                      @Nullable String hostNameVerificationHost, int hostNameVerificationPort) {
            this.forServer = forServer;
            this.trustManagerFactory = trustManagerFactory;
            this.keyManagerFactory = keyManagerFactory;
            this.keyPassword = keyPassword;
            this.ciphers = ciphers;
            this.sessionCacheSize = sessionCacheSize;
            this.sessionTimeout = sessionTimeout;
            this.trustCertChainSupplier = trustCertChainSupplier;
            this.keyCertChainSupplier = keyCertChainSupplier;
            this.keySupplier = keySupplier;
            this.clientAuth = clientAuth;
            this.apn = apn;
            this.provider = provider;
            this.protocols = protocols == null ? null : unmodifiableList(new ArrayList<>(protocols));
            this.hostnameVerificationAlgorithm = hostnameVerificationAlgorithm;
            this.hostNameVerificationHost = hostNameVerificationHost;
            this.hostNameVerificationPort = hostNameVerificationPort;
        }

        @Override
        public boolean forServer() {
            return forServer;
        }

        @Override
        public TrustManagerFactory trustManagerFactory() {
            return trustManagerFactory;
        }

        @Override
        public KeyManagerFactory keyManagerFactory() {
            return keyManagerFactory;
        }

        @Override
        public String keyPassword() {
            return keyPassword;
        }

        @Override
        public Iterable<String> ciphers() {
            return ciphers;
        }

        @Override
        public long sessionCacheSize() {
            return sessionCacheSize;
        }

        @Override
        public long sessionTimeout() {
            return sessionTimeout;
        }

        @Override
        public ClientAuth clientAuth() {
            return clientAuth;
        }

        @Override
        public Supplier<InputStream> trustCertChainSupplier() {
            return trustCertChainSupplier;
        }

        @Override
        public Supplier<InputStream> keyCertChainSupplier() {
            return keyCertChainSupplier;
        }

        @Override
        public Supplier<InputStream> keySupplier() {
            return keySupplier;
        }

        @Override
        public ApplicationProtocolConfig apn() {
            return apn;
        }

        @Override
        public SslProvider provider() {
            return provider;
        }

        @Override
        public List<String> protocols() {
            return protocols;
        }

        @Override
        public String hostnameVerificationAlgorithm() {
            return hostnameVerificationAlgorithm;
        }

        @Override
        public String hostnameVerificationHost() {
            return hostNameVerificationHost;
        }

        @Override
        public int hostnameVerificationPort() {
            return hostNameVerificationPort;
        }
    }
}

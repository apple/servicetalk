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
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.function.Supplier;

import javax.annotation.Nullable;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLParameters;
import javax.net.ssl.TrustManagerFactory;

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

    private SslConfigBuilder(boolean forServer) {
        this.forServer = forServer;
    }

    /**
     * Creates a builder for new client-side {@link SslConfig}.
     *
     * @return a new {@link SslConfigBuilder} for clients.
     */
    public static SslConfigBuilder forClient() {
        return new SslConfigBuilder(false);
    }

    /**
     * Creates a builder for new server-side {@link SslConfig}.
     *
     * @param keyCertChainSupplier an {@link Supplier} that will provide an input stream for a X.509 certificate chain in PEM format.
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
    public SslConfigBuilder setApplicationProtocolConfig(ApplicationProtocolConfig apn) {
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
    public SslConfigBuilder setCiphers(@Nullable Iterable<String> ciphers) {
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
    public SslConfigBuilder setSessionCacheSize(long sessionCacheSize) {
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
    public SslConfigBuilder setSessionTimeout(long sessionTimeout) {
        this.sessionTimeout = sessionTimeout;
        return this;
    }

    /**
     * Sets the client authentication mode. Only supported when configured for server mode.
     *
     * @param clientAuth the auth configuration to use.
     * @return self.
     */
    public SslConfigBuilder setClientAuth(SslConfig.ClientAuth clientAuth) {
        if (!forServer) {
            throw new UnsupportedOperationException("Only supported in server mode");
        }
        this.clientAuth = Objects.requireNonNull(clientAuth);
        return this;
    }

    /**
     * Sets the {@link SslConfig.SslProvider} to use.
     *
     * @param provider the provider.
     * @return self.
     */
    public SslConfigBuilder setProvider(SslConfig.SslProvider provider) {
        this.provider = Objects.requireNonNull(provider);
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
    public SslConfigBuilder setHostNameVerificationAlgorithm(@Nullable String hostNameVerificationAlgorithm) {
        if (forServer) {
            throw new UnsupportedOperationException("only supported for client mode");
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
        return new SslConfigImpl(forServer, trustManagerFactory, trustCertChainSupplier, keyManagerFactory, keyCertChainSupplier, keySupplier,
                keyPassword, ciphers, sessionCacheSize, sessionTimeout, clientAuth, apn, provider, protocols, hostNameVerificationAlgorithm);
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

        SslConfigImpl(boolean forServer, @Nullable TrustManagerFactory trustManagerFactory, Supplier<InputStream> trustCertChainSupplier,
                      @Nullable KeyManagerFactory keyManagerFactory, Supplier<InputStream> keyCertChainSupplier, Supplier<InputStream> keySupplier, @Nullable String keyPassword,
                      @Nullable Iterable<String> ciphers, long sessionCacheSize, long sessionTimeout, ClientAuth clientAuth, ApplicationProtocolConfig apn,
                      SslProvider provider, @Nullable List<String> protocols, @Nullable String hostnameVerificationAlgorithm) {
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
            this.protocols = protocols;
            this.hostnameVerificationAlgorithm = hostnameVerificationAlgorithm;
        }

        @Override
        public boolean isServer() {
            return forServer;
        }

        @Override
        public TrustManagerFactory getTrustManagerFactory() {
            return trustManagerFactory;
        }

        @Override
        public KeyManagerFactory getKeyManagerFactory() {
            return keyManagerFactory;
        }

        @Override
        public String getKeyPassword() {
            return keyPassword;
        }

        @Override
        public Iterable<String> getCiphers() {
            return ciphers;
        }

        @Override
        public long getSessionCacheSize() {
            return sessionCacheSize;
        }

        @Override
        public long getSessionTimeout() {
            return sessionTimeout;
        }

        @Override
        public ClientAuth getClientAuth() {
            return clientAuth;
        }

        @Override
        public Supplier<InputStream> getTrustCertChainSupplier() {
            return trustCertChainSupplier;
        }

        @Override
        public Supplier<InputStream> getKeyCertChainSupplier() {
            return keyCertChainSupplier;
        }

        @Override
        public Supplier<InputStream> getKeySupplier() {
            return keySupplier;
        }

        @Override
        public ApplicationProtocolConfig getApn() {
            return apn;
        }

        @Override
        public SslProvider getProvider() {
            return provider;
        }

        @Override
        public List<String> getProtocols() {
            return protocols;
        }

        @Override
        public String getHostnameVerificationAlgorithm() {
            return hostnameVerificationAlgorithm;
        }
    }
}

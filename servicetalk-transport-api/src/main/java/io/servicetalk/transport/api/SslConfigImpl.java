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
package io.servicetalk.transport.api;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;
import javax.annotation.Nullable;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.TrustManagerFactory;

import static java.util.Collections.unmodifiableList;

/**
 * A configuration for SSL/TLS.
 */
final class SslConfigImpl implements SslConfig {
    private final boolean server;
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
    @Nullable
    private final String sniHostname;

    SslConfigImpl(boolean server, @Nullable TrustManagerFactory trustManagerFactory,
                  Supplier<InputStream> trustCertChainSupplier, @Nullable KeyManagerFactory keyManagerFactory,
                  Supplier<InputStream> keyCertChainSupplier, Supplier<InputStream> keySupplier,
                  @Nullable String keyPassword, @Nullable Iterable<String> ciphers, long sessionCacheSize,
                  long sessionTimeout, ClientAuth clientAuth, ApplicationProtocolConfig apn, SslProvider provider,
                  @Nullable List<String> protocols, @Nullable String hostnameVerificationAlgorithm,
                  @Nullable String hostNameVerificationHost, int hostNameVerificationPort,
                  @Nullable String sniHostname) {
        this.server = server;
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
        this.sniHostname = sniHostname;
    }

    @Override
    public boolean isServer() {
        return server;
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

    @Override
    public String sniHostname() {
        return sniHostname;
    }
}

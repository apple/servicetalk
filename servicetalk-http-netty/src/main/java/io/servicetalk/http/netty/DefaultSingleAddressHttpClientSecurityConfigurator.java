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
package io.servicetalk.http.netty;

import io.servicetalk.http.api.SingleAddressHttpClientBuilder;
import io.servicetalk.http.api.SingleAddressHttpClientSecurityConfigurator;
import io.servicetalk.transport.api.ClientSslConfig;
import io.servicetalk.transport.netty.internal.ClientSecurityConfig;

import java.io.InputStream;
import java.util.function.Function;
import java.util.function.Supplier;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.TrustManagerFactory;

@Deprecated
final class DefaultSingleAddressHttpClientSecurityConfigurator<U, R>
        implements SingleAddressHttpClientSecurityConfigurator<U, R> {
    private final ClientSecurityConfig config;
    private final Function<ClientSslConfig, SingleAddressHttpClientBuilder<U, R>> configConsumer;

    DefaultSingleAddressHttpClientSecurityConfigurator(
            final Function<ClientSslConfig, SingleAddressHttpClientBuilder<U, R>> configConsumer) {
        config = new ClientSecurityConfig();
        this.configConsumer = configConsumer;
    }

    @Override
    public SingleAddressHttpClientBuilder<U, R> commit() {
        return configConsumer.apply(config.asSslConfig());
    }

    @Override
    public SingleAddressHttpClientSecurityConfigurator<U, R> trustManager(
            final Supplier<InputStream> trustCertChainSupplier) {
        config.trustManager(trustCertChainSupplier);
        return this;
    }

    @Override
    public SingleAddressHttpClientSecurityConfigurator<U, R> trustManager(
            final TrustManagerFactory trustManagerFactory) {
        config.trustManager(trustManagerFactory);
        return this;
    }

    @Override
    public SingleAddressHttpClientSecurityConfigurator<U, R> protocols(final String... protocols) {
        config.protocols(protocols);
        return this;
    }

    @Override
    public SingleAddressHttpClientSecurityConfigurator<U, R> ciphers(final Iterable<String> ciphers) {
        config.ciphers(ciphers);
        return this;
    }

    @Override
    public SingleAddressHttpClientSecurityConfigurator<U, R> sessionCacheSize(final long sessionCacheSize) {
        config.sessionCacheSize(sessionCacheSize);
        return this;
    }

    @Override
    public SingleAddressHttpClientSecurityConfigurator<U, R> sessionTimeout(final long sessionTimeout) {
        config.sessionTimeout(sessionTimeout);
        return this;
    }

    @Override
    public SingleAddressHttpClientSecurityConfigurator<U, R> provider(final SslProvider provider) {
        config.provider(provider);
        return this;
    }

    @Override
    public SingleAddressHttpClientSecurityConfigurator<U, R> hostnameVerificationAlgorithm(
            final String hostNameVerificationAlgorithm) {
        config.hostNameVerificationAlgorithm(hostNameVerificationAlgorithm);
        return this;
    }

    @Override
    public SingleAddressHttpClientSecurityConfigurator<U, R> hostnameVerification(
            final String hostNameVerificationAlgorithm, final String hostNameVerificationHost) {
        config.hostNameVerification(hostNameVerificationAlgorithm, hostNameVerificationHost);
        return this;
    }

    @Override
    public SingleAddressHttpClientSecurityConfigurator<U, R> hostnameVerification(
            final String hostNameVerificationAlgorithm, final String hostNameVerificationHost,
            final int hostNameVerificationPort) {
        config.hostNameVerification(hostNameVerificationAlgorithm, hostNameVerificationHost, hostNameVerificationPort);
        return this;
    }

    @Override
    public SingleAddressHttpClientSecurityConfigurator<U, R> hostnameVerification(
            final String hostNameVerificationHost) {
        config.hostNameVerification(hostNameVerificationHost);
        return this;
    }

    @Override
    public SingleAddressHttpClientSecurityConfigurator<U, R> hostnameVerification(
            final String hostNameVerificationHost, final int hostNameVerificationPort) {
        config.hostNameVerification(hostNameVerificationHost, hostNameVerificationPort);
        return this;
    }

    @Override
    public SingleAddressHttpClientSecurityConfigurator<U, R> sniHostname(final String sniHostname) {
        config.sniHostname(sniHostname);
        return this;
    }

    @Override
    public SingleAddressHttpClientSecurityConfigurator<U, R> disableHostnameVerification() {
        config.disableHostnameVerification();
        return this;
    }

    @Override
    public SingleAddressHttpClientSecurityConfigurator<U, R> keyManager(final KeyManagerFactory keyManagerFactory) {
        config.keyManager(keyManagerFactory);
        return this;
    }

    @Override
    public SingleAddressHttpClientSecurityConfigurator<U, R> keyManager(
            final Supplier<InputStream> keyCertChainSupplier, final Supplier<InputStream> keySupplier) {
        config.keyManager(keyCertChainSupplier, keySupplier);
        return this;
    }

    @Override
    public SingleAddressHttpClientSecurityConfigurator<U, R> keyManager(
            final Supplier<InputStream> keyCertChainSupplier, final Supplier<InputStream> keySupplier,
            final String keyPassword) {
        config.keyManager(keyCertChainSupplier, keySupplier, keyPassword);
        return this;
    }
}

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
package io.servicetalk.grpc.netty;

import io.servicetalk.grpc.api.GrpcClientBuilder;
import io.servicetalk.grpc.api.GrpcClientSecurityConfigurator;
import io.servicetalk.http.api.SingleAddressHttpClientSecurityConfigurator;

import java.io.InputStream;
import java.util.function.Supplier;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.TrustManagerFactory;

@Deprecated
final class DefaultGrpcClientSecurityConfigurator<U, R> implements GrpcClientSecurityConfigurator<U, R> {
    private final SingleAddressHttpClientSecurityConfigurator<U, R> delegate;
    private final GrpcClientBuilder<U, R> original;

    DefaultGrpcClientSecurityConfigurator(final SingleAddressHttpClientSecurityConfigurator<U, R> delegate,
                                          final GrpcClientBuilder<U, R> original) {
        this.delegate = delegate;
        this.original = original;
    }

    @Override
    public GrpcClientBuilder<U, R> commit() {
        delegate.commit();
        return original;
    }

    @Override
    public GrpcClientSecurityConfigurator<U, R> trustManager(final Supplier<InputStream> trustCertChainSupplier) {
        delegate.trustManager(trustCertChainSupplier);
        return this;
    }

    @Override
    public GrpcClientSecurityConfigurator<U, R> trustManager(final TrustManagerFactory trustManagerFactory) {
        delegate.trustManager(trustManagerFactory);
        return this;
    }

    @Override
    public GrpcClientSecurityConfigurator<U, R> protocols(final String... protocols) {
        delegate.protocols(protocols);
        return this;
    }

    @Override
    public GrpcClientSecurityConfigurator<U, R> ciphers(final Iterable<String> ciphers) {
        delegate.ciphers(ciphers);
        return this;
    }

    @Override
    public GrpcClientSecurityConfigurator<U, R> sessionCacheSize(final long sessionCacheSize) {
        delegate.sessionCacheSize(sessionCacheSize);
        return this;
    }

    @Override
    public GrpcClientSecurityConfigurator<U, R> sessionTimeout(final long sessionTimeout) {
        delegate.sessionTimeout(sessionTimeout);
        return this;
    }

    @Override
    public GrpcClientSecurityConfigurator<U, R> provider(final SslProvider provider) {
        delegate.provider(provider);
        return this;
    }

    @Override
    public GrpcClientSecurityConfigurator<U, R> hostnameVerificationAlgorithm(
            final String hostNameVerificationAlgorithm) {
        delegate.hostnameVerificationAlgorithm(hostNameVerificationAlgorithm);
        return this;
    }

    @Override
    public GrpcClientSecurityConfigurator<U, R> hostnameVerification(final String hostNameVerificationAlgorithm,
                                                                     final String hostNameVerificationHost) {
        delegate.hostnameVerification(hostNameVerificationAlgorithm, hostNameVerificationHost);
        return this;
    }

    @Override
    public GrpcClientSecurityConfigurator<U, R> hostnameVerification(final String hostNameVerificationAlgorithm,
                                                                     final String hostNameVerificationHost,
                                                                     final int hostNameVerificationPort) {
        delegate.hostnameVerification(hostNameVerificationAlgorithm, hostNameVerificationHost,
                hostNameVerificationPort);
        return this;
    }

    @Override
    public GrpcClientSecurityConfigurator<U, R> hostnameVerification(final String hostNameVerificationHost) {
        delegate.hostnameVerification(hostNameVerificationHost);
        return this;
    }

    @Override
    public GrpcClientSecurityConfigurator<U, R> hostnameVerification(final String hostNameVerificationHost,
                                                                     final int hostNameVerificationPort) {
        delegate.hostnameVerification(hostNameVerificationHost, hostNameVerificationPort);
        return this;
    }

    @Override
    public GrpcClientSecurityConfigurator<U, R> sniHostname(final String sniHostname) {
        delegate.sniHostname(sniHostname);
        return this;
    }

    @Override
    public GrpcClientSecurityConfigurator<U, R> disableHostnameVerification() {
        delegate.disableHostnameVerification();
        return this;
    }

    @Override
    public GrpcClientSecurityConfigurator<U, R> keyManager(final KeyManagerFactory keyManagerFactory) {
        delegate.keyManager(keyManagerFactory);
        return this;
    }

    @Override
    public GrpcClientSecurityConfigurator<U, R> keyManager(final Supplier<InputStream> keyCertChainSupplier,
                                                           final Supplier<InputStream> keySupplier) {
        delegate.keyManager(keyCertChainSupplier, keySupplier);
        return this;
    }

    @Override
    public GrpcClientSecurityConfigurator<U, R> keyManager(final Supplier<InputStream> keyCertChainSupplier,
                                                           final Supplier<InputStream> keySupplier,
                                                           final String keyPassword) {
        delegate.keyManager(keyCertChainSupplier, keySupplier, keyPassword);
        return this;
    }
}

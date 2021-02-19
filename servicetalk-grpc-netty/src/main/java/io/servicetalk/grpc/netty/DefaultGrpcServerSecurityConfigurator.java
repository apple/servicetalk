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

import io.servicetalk.grpc.api.GrpcServerBuilder;
import io.servicetalk.grpc.api.GrpcServerSecurityConfigurator;
import io.servicetalk.http.api.HttpServerSecurityConfigurator;

import java.io.InputStream;
import java.util.function.Supplier;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.TrustManagerFactory;

final class DefaultGrpcServerSecurityConfigurator implements GrpcServerSecurityConfigurator {
    private final HttpServerSecurityConfigurator delegate;
    private final GrpcServerBuilder original;

    DefaultGrpcServerSecurityConfigurator(final HttpServerSecurityConfigurator delegate,
                                          final GrpcServerBuilder original) {
        this.delegate = delegate;
        this.original = original;
    }

    @Override
    public GrpcServerSecurityConfigurator trustManager(final Supplier<InputStream> trustCertChainSupplier) {
        delegate.trustManager(trustCertChainSupplier);
        return this;
    }

    @Override
    public GrpcServerSecurityConfigurator trustManager(final TrustManagerFactory trustManagerFactory) {
        delegate.trustManager(trustManagerFactory);
        return this;
    }

    @Override
    public GrpcServerSecurityConfigurator keyManager(final KeyManagerFactory keyManagerFactory) {
        delegate.keyManager(keyManagerFactory);
        return this;
    }

    @Override
    public GrpcServerSecurityConfigurator keyManager(final Supplier<InputStream> keyCertChainSupplier,
                                                     final Supplier<InputStream> keySupplier) {
        delegate.keyManager(keyCertChainSupplier, keySupplier);
        return this;
    }

    @Override
    public GrpcServerSecurityConfigurator keyManager(final Supplier<InputStream> keyCertChainSupplier,
                                                     final Supplier<InputStream> keySupplier,
                                                     final String keyPassword) {
        delegate.keyManager(keyCertChainSupplier, keySupplier, keyPassword);
        return this;
    }

    @Override
    public GrpcServerSecurityConfigurator protocols(final String... protocols) {
        delegate.protocols(protocols);
        return this;
    }

    @Override
    public GrpcServerSecurityConfigurator ciphers(final Iterable<String> ciphers) {
        delegate.ciphers(ciphers);
        return this;
    }

    @Override
    public GrpcServerSecurityConfigurator sessionCacheSize(final long sessionCacheSize) {
        delegate.sessionCacheSize(sessionCacheSize);
        return this;
    }

    @Override
    public GrpcServerSecurityConfigurator sessionTimeout(final long sessionTimeout) {
        delegate.sessionTimeout(sessionTimeout);
        return this;
    }

    @Override
    public GrpcServerSecurityConfigurator provider(final SslProvider provider) {
        delegate.provider(provider);
        return this;
    }

    @Override
    public GrpcServerSecurityConfigurator clientAuth(final ClientAuth clientAuth) {
        delegate.clientAuth(clientAuth);
        return this;
    }

    @Override
    public GrpcServerSecurityConfigurator newSniConfig(final String sniHostname) {
        return new DefaultGrpcServerSecurityConfigurator(delegate.newSniConfig(sniHostname), original);
    }

    @Override
    public GrpcServerBuilder commit() {
        delegate.commit();
        return original;
    }
}

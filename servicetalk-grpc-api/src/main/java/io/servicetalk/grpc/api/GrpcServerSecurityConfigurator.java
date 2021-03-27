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
package io.servicetalk.grpc.api;

import io.servicetalk.transport.api.ServerSecurityConfigurator;
import io.servicetalk.transport.api.ServerSslConfig;

import java.io.InputStream;
import java.util.function.Supplier;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.TrustManagerFactory;

/**
 * A {@link ServerSecurityConfigurator} for {@link GrpcServerBuilder}.
 * @deprecated Use {@link GrpcServerBuilder#sslConfig(ServerSslConfig)}.
 */
@Deprecated
public interface GrpcServerSecurityConfigurator extends ServerSecurityConfigurator {
    @Override
    GrpcServerSecurityConfigurator trustManager(Supplier<InputStream> trustCertChainSupplier);

    @Override
    GrpcServerSecurityConfigurator trustManager(TrustManagerFactory trustManagerFactory);

    @Override
    GrpcServerSecurityConfigurator protocols(String... protocols);

    @Override
    GrpcServerSecurityConfigurator ciphers(Iterable<String> ciphers);

    @Override
    GrpcServerSecurityConfigurator sessionCacheSize(long sessionCacheSize);

    @Override
    GrpcServerSecurityConfigurator sessionTimeout(long sessionTimeout);

    @Override
    GrpcServerSecurityConfigurator provider(SslProvider provider);

    @Override
    GrpcServerSecurityConfigurator clientAuth(ClientAuth clientAuth);

    /**
     * Commit configuring server security.
     *
     * @param keyManagerFactory an {@link KeyManagerFactory}.
     * @return Original {@link GrpcServerBuilder} that initiated the security configuration process.
     */
    GrpcServerBuilder commit(KeyManagerFactory keyManagerFactory);

    /**
     * Commit configuring server security.
     *
     * @param keyCertChainSupplier an {@link Supplier} that will provide an input stream for a {@code X.509} certificate
     * chain in {@code PEM} format.
     * <p>
     * The responsibility to call {@link InputStream#close()} is transferred to callers of the {@link Supplier}.
     * If this is not the desired behavior then wrap the {@link InputStream} and override {@link InputStream#close()}.
     * @param keySupplier an {@link Supplier} that will provide an input stream for a {@code KCS#8} private key in
     * {@code PEM} format.
     * <p>
     * The responsibility to call {@link InputStream#close()} is transferred to callers of the {@link Supplier}.
     * If this is not the desired behavior then wrap the {@link InputStream} and override {@link InputStream#close()}.
     * @return Original {@link GrpcServerBuilder} that initiated the security configuration process.
     */
    GrpcServerBuilder commit(Supplier<InputStream> keyCertChainSupplier, Supplier<InputStream> keySupplier);

    /**
     * Commit configuring server security.
     *
     * @param keyCertChainSupplier an {@link Supplier} that will provide an input stream for a {@code X.509} certificate
     * chain in {@code PEM} format.
     * <p>
     * The responsibility to call {@link InputStream#close()} is transferred to callers of the {@link Supplier}.
     * If this is not the desired behavior then wrap the {@link InputStream} and override {@link InputStream#close()}.
     * @param keySupplier an {@link Supplier} that will provide an input stream for a {@code KCS#8} private key in
     * {@code PEM} format.
     * <p>
     * The responsibility to call {@link InputStream#close()} is transferred to callers of the {@link Supplier}.
     * If this is not the desired behavior then wrap the {@link InputStream} and override {@link InputStream#close()}.
     * @param keyPassword the password of the {@code keyFile}.
     * @return Original {@link GrpcServerBuilder} that initiated the security configuration process.
     */
    GrpcServerBuilder commit(Supplier<InputStream> keyCertChainSupplier, Supplier<InputStream> keySupplier,
                             String keyPassword);
}

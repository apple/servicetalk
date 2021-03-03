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

import io.servicetalk.transport.api.ClientSecurityConfigurator;
import io.servicetalk.transport.api.ClientSslConfig;

import java.io.InputStream;
import java.util.function.Supplier;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.TrustManagerFactory;

/**
 * A {@link ClientSecurityConfigurator} for {@link SingleAddressGrpcClientBuilder}.
 * @deprecated Use {@link GrpcClientBuilder#sslConfig(ClientSslConfig)}.
 * @param <U> the type of address before resolution (unresolved address)
 * @param <R> the type of address after resolution (resolved address)
 */
@Deprecated
public interface GrpcClientSecurityConfigurator<U, R> extends ClientSecurityConfigurator {
    /**
     * Commit configuring client security.
     *
     * @return Original {@link GrpcClientBuilder} that initiated the security configuration process.
     */
    GrpcClientBuilder<U, R> commit();

    @Override
    GrpcClientSecurityConfigurator<U, R> trustManager(Supplier<InputStream> trustCertChainSupplier);

    @Override
    GrpcClientSecurityConfigurator<U, R> trustManager(TrustManagerFactory trustManagerFactory);

    @Override
    GrpcClientSecurityConfigurator<U, R> protocols(String... protocols);

    @Override
    GrpcClientSecurityConfigurator<U, R> ciphers(Iterable<String> ciphers);

    @Override
    GrpcClientSecurityConfigurator<U, R> sessionCacheSize(long sessionCacheSize);

    @Override
    GrpcClientSecurityConfigurator<U, R> sessionTimeout(long sessionTimeout);

    @Override
    GrpcClientSecurityConfigurator<U, R> provider(SslProvider provider);

    @Override
    GrpcClientSecurityConfigurator<U, R> hostnameVerificationAlgorithm(
            String hostNameVerificationAlgorithm);

    @Override
    GrpcClientSecurityConfigurator<U, R> hostnameVerification(String hostNameVerificationAlgorithm,
                                                              String hostNameVerificationHost);

    @Override
    GrpcClientSecurityConfigurator<U, R> hostnameVerification(String hostNameVerificationAlgorithm,
                                                              String hostNameVerificationHost,
                                                              int hostNameVerificationPort);

    @Override
    GrpcClientSecurityConfigurator<U, R> hostnameVerification(String hostNameVerificationHost);

    @Override
    GrpcClientSecurityConfigurator<U, R> hostnameVerification(String hostNameVerificationHost,
                                                              int hostNameVerificationPort);

    @Override
    GrpcClientSecurityConfigurator<U, R> sniHostname(String sniHostname);

    @Override
    GrpcClientSecurityConfigurator<U, R> disableHostnameVerification();

    @Override
    GrpcClientSecurityConfigurator<U, R> keyManager(KeyManagerFactory keyManagerFactory);

    @Override
    GrpcClientSecurityConfigurator<U, R> keyManager(Supplier<InputStream> keyCertChainSupplier,
                                                    Supplier<InputStream> keySupplier);

    @Override
    GrpcClientSecurityConfigurator<U, R> keyManager(Supplier<InputStream> keyCertChainSupplier,
                                                    Supplier<InputStream> keySupplier, String keyPassword);
}

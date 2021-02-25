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
package io.servicetalk.http.api;

import io.servicetalk.transport.api.ClientSecurityConfigurator;

import java.io.InputStream;
import java.util.function.Supplier;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.TrustManagerFactory;

/**
 * A {@link ClientSecurityConfigurator} for {@link PartitionedHttpClientSecurityConfigurator}.
 * @deprecated TODO
 * @param <U> the type of address before resolution (unresolved address)
 * @param <R> the type of address after resolution (resolved address)
 */
@Deprecated
public interface PartitionedHttpClientSecurityConfigurator<U, R> extends ClientSecurityConfigurator {
    /**
     * Commit configuring client security.
     *
     * @return Original {@link HttpServerBuilder} that initiated the security configuration process.
     */
    PartitionedHttpClientBuilder<U, R> commit();

    @Override
    PartitionedHttpClientSecurityConfigurator<U, R> trustManager(Supplier<InputStream> trustCertChainSupplier);

    @Override
    PartitionedHttpClientSecurityConfigurator<U, R> trustManager(TrustManagerFactory trustManagerFactory);

    @Override
    PartitionedHttpClientSecurityConfigurator<U, R> protocols(String... protocols);

    @Override
    PartitionedHttpClientSecurityConfigurator<U, R> ciphers(Iterable<String> ciphers);

    @Override
    PartitionedHttpClientSecurityConfigurator<U, R> sessionCacheSize(long sessionCacheSize);

    @Override
    PartitionedHttpClientSecurityConfigurator<U, R> sessionTimeout(long sessionTimeout);

    @Override
    PartitionedHttpClientSecurityConfigurator<U, R> provider(SslProvider provider);

    @Override
    PartitionedHttpClientSecurityConfigurator<U, R> hostnameVerificationAlgorithm(
            String hostNameVerificationAlgorithm);

    @Override
    PartitionedHttpClientSecurityConfigurator<U, R> hostnameVerification(String hostNameVerificationAlgorithm,
                                                                         String hostNameVerificationHost);

    @Override
    PartitionedHttpClientSecurityConfigurator<U, R> hostnameVerification(String hostNameVerificationAlgorithm,
                                                                         String hostNameVerificationHost,
                                                                         int hostNameVerificationPort);

    @Override
    PartitionedHttpClientSecurityConfigurator<U, R> hostnameVerification(String hostNameVerificationHost);

    @Override
    PartitionedHttpClientSecurityConfigurator<U, R> hostnameVerification(String hostNameVerificationHost,
                                                                         int hostNameVerificationPort);

    @Override
    PartitionedHttpClientSecurityConfigurator<U, R> sniHostname(String sniHostname);

    @Override
    PartitionedHttpClientSecurityConfigurator<U, R> disableHostnameVerification();

    @Override
    PartitionedHttpClientSecurityConfigurator<U, R> keyManager(KeyManagerFactory keyManagerFactory);

    @Override
    PartitionedHttpClientSecurityConfigurator<U, R> keyManager(Supplier<InputStream> keyCertChainSupplier,
                                                               Supplier<InputStream> keySupplier);

    @Override
    PartitionedHttpClientSecurityConfigurator<U, R> keyManager(Supplier<InputStream> keyCertChainSupplier,
                                                               Supplier<InputStream> keySupplier, String keyPassword);
}

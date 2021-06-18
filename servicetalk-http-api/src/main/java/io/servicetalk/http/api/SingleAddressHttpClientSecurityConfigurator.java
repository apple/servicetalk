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
import io.servicetalk.transport.api.ClientSslConfig;

import java.io.InputStream;
import java.util.function.Supplier;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.TrustManagerFactory;

/**
 * A {@link ClientSecurityConfigurator} for {@link SingleAddressHttpClientBuilder}.
 * @deprecated Use {@link SingleAddressHttpClientBuilder#sslConfig(ClientSslConfig)}.
 * @param <U> the type of address before resolution (unresolved address)
 * @param <R> the type of address after resolution (resolved address)
 */
@Deprecated
public interface SingleAddressHttpClientSecurityConfigurator<U, R> extends ClientSecurityConfigurator {
    /**
     * Commit configuring client security.
     *
     * @return Original {@link HttpServerBuilder} that initiated the security configuration process.
     */
    SingleAddressHttpClientBuilder<U, R> commit();

    @Override
    SingleAddressHttpClientSecurityConfigurator<U, R> trustManager(Supplier<InputStream> trustCertChainSupplier);

    @Override
    SingleAddressHttpClientSecurityConfigurator<U, R> trustManager(TrustManagerFactory trustManagerFactory);

    @Override
    SingleAddressHttpClientSecurityConfigurator<U, R> protocols(String... protocols);

    @Override
    SingleAddressHttpClientSecurityConfigurator<U, R> ciphers(Iterable<String> ciphers);

    @Override
    SingleAddressHttpClientSecurityConfigurator<U, R> sessionCacheSize(long sessionCacheSize);

    @Override
    SingleAddressHttpClientSecurityConfigurator<U, R> sessionTimeout(long sessionTimeout);

    @Override
    SingleAddressHttpClientSecurityConfigurator<U, R> provider(SslProvider provider);

    @Override
    SingleAddressHttpClientSecurityConfigurator<U, R> hostnameVerificationAlgorithm(
            String hostNameVerificationAlgorithm);

    @Override
    SingleAddressHttpClientSecurityConfigurator<U, R> hostnameVerification(String hostNameVerificationAlgorithm,
                                                                           String hostNameVerificationHost);

    @Override
    SingleAddressHttpClientSecurityConfigurator<U, R> hostnameVerification(String hostNameVerificationAlgorithm,
                                                                           String hostNameVerificationHost,
                                                                           int hostNameVerificationPort);

    @Override
    SingleAddressHttpClientSecurityConfigurator<U, R> hostnameVerification(String hostNameVerificationHost);

    @Override
    SingleAddressHttpClientSecurityConfigurator<U, R> hostnameVerification(String hostNameVerificationHost,
                                                                           int hostNameVerificationPort);

    @Override
    SingleAddressHttpClientSecurityConfigurator<U, R> sniHostname(String sniHostname);

    @Override
    SingleAddressHttpClientSecurityConfigurator<U, R> disableHostnameVerification();

    @Override
    SingleAddressHttpClientSecurityConfigurator<U, R> keyManager(KeyManagerFactory keyManagerFactory);

    @Override
    SingleAddressHttpClientSecurityConfigurator<U, R> keyManager(Supplier<InputStream> keyCertChainSupplier,
                                                                 Supplier<InputStream> keySupplier);

    @Override
    SingleAddressHttpClientSecurityConfigurator<U, R> keyManager(Supplier<InputStream> keyCertChainSupplier,
                                                                 Supplier<InputStream> keySupplier, String keyPassword);
}

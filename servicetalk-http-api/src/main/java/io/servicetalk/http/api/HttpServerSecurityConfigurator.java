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

import io.servicetalk.transport.api.ServerSecurityConfigurator;

import java.io.InputStream;
import java.util.function.Supplier;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.TrustManagerFactory;

/**
 * A {@link ServerSecurityConfigurator} for {@link HttpServerBuilder}.
 */
public interface HttpServerSecurityConfigurator extends ServerSecurityConfigurator {
    @Override
    HttpServerSecurityConfigurator trustManager(Supplier<InputStream> trustCertChainSupplier);

    @Override
    HttpServerSecurityConfigurator trustManager(TrustManagerFactory trustManagerFactory);

    @Override
    HttpServerSecurityConfigurator keyManager(KeyManagerFactory keyManagerFactory);

    @Override
    HttpServerSecurityConfigurator keyManager(Supplier<InputStream> keyCertChainSupplier,
                                              Supplier<InputStream> keySupplier);

    @Override
    HttpServerSecurityConfigurator keyManager(Supplier<InputStream> keyCertChainSupplier,
                                              Supplier<InputStream> keySupplier, String keyPassword);

    @Override
    HttpServerSecurityConfigurator protocols(String... protocols);

    @Override
    HttpServerSecurityConfigurator ciphers(Iterable<String> ciphers);

    @Override
    HttpServerSecurityConfigurator sessionCacheSize(long sessionCacheSize);

    @Override
    HttpServerSecurityConfigurator sessionTimeout(long sessionTimeout);

    @Override
    HttpServerSecurityConfigurator provider(SslProvider provider);

    @Override
    HttpServerSecurityConfigurator clientAuth(ClientAuth clientAuth);

    @Override
    HttpServerSecurityConfigurator newSniConfig(String sniHostname);

    /**
     * Commit configuring server security.
     * @return Original {@link HttpServerBuilder} that initiated the security configuration process.
     */
    HttpServerBuilder commit();
}

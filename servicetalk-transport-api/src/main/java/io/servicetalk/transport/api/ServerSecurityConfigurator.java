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
import java.util.function.Supplier;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLEngine;
import javax.net.ssl.TrustManagerFactory;

/**
 * A {@link SecurityConfigurator} contract for servers.
 */
public interface ServerSecurityConfigurator extends SecurityConfigurator {
    /**
     * Indicates the state of the {@link SSLEngine} with respect to client authentication.
     */
    enum ClientAuth {
        /**
         * Indicates that the {@link SSLEngine} will not request client authentication.
         */
        NONE,

        /**
         * Indicates that the {@link SSLEngine} will request client authentication.
         */
        OPTIONAL,

        /**
         * Indicates that the {@link SSLEngine} will <strong>require</strong> client authentication.
         */
        REQUIRE
    }

    @Override
    ServerSecurityConfigurator trustManager(Supplier<InputStream> trustCertChainSupplier);

    @Override
    ServerSecurityConfigurator trustManager(TrustManagerFactory trustManagerFactory);

    @Override
    ServerSecurityConfigurator keyManager(KeyManagerFactory keyManagerFactory);

    @Override
    ServerSecurityConfigurator keyManager(Supplier<InputStream> keyCertChainSupplier,
                                          Supplier<InputStream> keySupplier);

    @Override
    ServerSecurityConfigurator keyManager(Supplier<InputStream> keyCertChainSupplier, Supplier<InputStream> keySupplier,
                                          String keyPassword);

    @Override
    ServerSecurityConfigurator protocols(String... protocols);

    @Override
    ServerSecurityConfigurator ciphers(Iterable<String> ciphers);

    @Override
    ServerSecurityConfigurator sessionCacheSize(long sessionCacheSize);

    @Override
    ServerSecurityConfigurator sessionTimeout(long sessionTimeout);

    @Override
    ServerSecurityConfigurator provider(SslProvider provider);

    /**
     * Sets the client authentication mode.
     *
     * @param clientAuth the auth configuration to use.
     * @return {@code this}.
     */
    ServerSecurityConfigurator clientAuth(ClientAuth clientAuth);

    /**
     * Create a new {@link ServerSecurityConfigurator} which is used when the client requests
     * <a href="https://tools.ietf.org/html/rfc6066#section-3">SNI</a> for {@code sniHostname}.
     * @param sniHostname The hostname to match in the TLS client_hello SNI extension.
     * @return a new {@link ServerSecurityConfigurator} which is used when the client requests
     * <a href="https://tools.ietf.org/html/rfc6066#section-3">SNI</a> for {@code sniHostname}.
     */
    ServerSecurityConfigurator newSniConfig(String sniHostname);
}

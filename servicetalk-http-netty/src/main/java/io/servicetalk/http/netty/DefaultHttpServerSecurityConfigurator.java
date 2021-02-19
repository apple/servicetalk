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

import io.servicetalk.http.api.HttpServerBuilder;
import io.servicetalk.http.api.HttpServerSecurityConfigurator;
import io.servicetalk.transport.netty.internal.ReadOnlyServerSecurityConfig;
import io.servicetalk.transport.netty.internal.ServerSecurityConfig;

import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;
import java.util.function.BiFunction;
import java.util.function.Supplier;
import javax.annotation.Nullable;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.TrustManagerFactory;

import static java.util.Objects.requireNonNull;

final class DefaultHttpServerSecurityConfigurator implements HttpServerSecurityConfigurator {
    private final ServerSecurityConfig securityConfig = new ServerSecurityConfig();
    private final BiFunction<ReadOnlyServerSecurityConfig, Map<String, ReadOnlyServerSecurityConfig>,
            HttpServerBuilder> configConsumer;
    @Nullable
    private Map<String, ReadOnlyServerSecurityConfig> sniMap;
    @Nullable
    private String sniHostname;

    DefaultHttpServerSecurityConfigurator(
            final BiFunction<ReadOnlyServerSecurityConfig, Map<String, ReadOnlyServerSecurityConfig>,
                                HttpServerBuilder> configConsumer) {
        this.configConsumer = requireNonNull(configConsumer);
    }

    private DefaultHttpServerSecurityConfigurator(DefaultHttpServerSecurityConfigurator configurator,
                                                  String sniHostname) {
        this.configConsumer = configurator.configConsumer;
        this.sniHostname = requireNonNull(sniHostname);
        this.sniMap = requireNonNull(configurator.sniMap);
    }

    @Override
    public HttpServerSecurityConfigurator trustManager(final Supplier<InputStream> trustCertChainSupplier) {
        securityConfig.trustManager(trustCertChainSupplier);
        return this;
    }

    @Override
    public HttpServerSecurityConfigurator trustManager(final TrustManagerFactory trustManagerFactory) {
        securityConfig.trustManager(trustManagerFactory);
        return this;
    }

    @Override
    public HttpServerSecurityConfigurator keyManager(final KeyManagerFactory keyManagerFactory) {
        securityConfig.keyManager(keyManagerFactory);
        return this;
    }

    @Override
    public HttpServerSecurityConfigurator keyManager(final Supplier<InputStream> keyCertChainSupplier,
                                                     final Supplier<InputStream> keySupplier) {
        securityConfig.keyManager(keyCertChainSupplier, keySupplier);
        return this;
    }

    @Override
    public HttpServerSecurityConfigurator keyManager(final Supplier<InputStream> keyCertChainSupplier,
                                                     final Supplier<InputStream> keySupplier,
                                                     final String keyPassword) {
        securityConfig.keyManager(keyCertChainSupplier, keySupplier, keyPassword);
        return this;
    }

    @Override
    public HttpServerSecurityConfigurator protocols(final String... protocols) {
        securityConfig.protocols(protocols);
        return this;
    }

    @Override
    public HttpServerSecurityConfigurator ciphers(final Iterable<String> ciphers) {
        securityConfig.ciphers(ciphers);
        return this;
    }

    @Override
    public HttpServerSecurityConfigurator sessionCacheSize(final long sessionCacheSize) {
        securityConfig.sessionCacheSize(sessionCacheSize);
        return this;
    }

    @Override
    public HttpServerSecurityConfigurator sessionTimeout(final long sessionTimeout) {
        securityConfig.sessionTimeout(sessionTimeout);
        return this;
    }

    @Override
    public HttpServerSecurityConfigurator provider(final SslProvider provider) {
        securityConfig.provider(provider);
        return this;
    }

    @Override
    public HttpServerSecurityConfigurator clientAuth(final ClientAuth clientAuth) {
        securityConfig.clientAuth(clientAuth);
        return this;
    }

    @Override
    public HttpServerSecurityConfigurator newSniConfig(final String sniHostname) {
        requireNonNull(sniHostname);
        if (this.sniHostname == null) {
            assert sniMap == null;
            sniMap = new HashMap<>(8);
            // put the defaultConfig in null key slot, retrieve it later below.
            sniMap.put(null, securityConfig.asReadOnly());
        } else {
            assert sniMap != null;
            sniMap.put(sniHostname, securityConfig.asReadOnly());
        }
        return new DefaultHttpServerSecurityConfigurator(this, sniHostname);
    }

    @Override
    public HttpServerBuilder commit() {
        if (sniHostname == null) {
            assert sniMap == null;
            return configConsumer.apply(securityConfig.asReadOnly(), null);
        } else {
            assert sniMap != null;
            sniMap.put(sniHostname, securityConfig.asReadOnly());
            return configConsumer.apply(sniMap.remove(null), sniMap); // retrieve defaultConfig from null key
        }
    }
}

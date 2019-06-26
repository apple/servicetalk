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
import java.util.function.Consumer;
import java.util.function.Supplier;
import javax.annotation.Nullable;
import javax.net.ssl.KeyManagerFactory;

import static java.util.Objects.requireNonNull;

class BaseServerSslConfigBuilder<B extends BaseServerSslConfigBuilder, F> extends BaseSslConfigBuilder<B, F> {

    private SslConfig.ClientAuth clientAuth = SslConfig.ClientAuth.NONE;

    private Supplier<InputStream> keyCertChainSupplier = nullSupplier();
    private Supplier<InputStream> keySupplier = nullSupplier();
    @Nullable
    private KeyManagerFactory keyManagerFactory;
    @Nullable
    private String keyPassword;

    BaseServerSslConfigBuilder(final Supplier<F> finisher, final Consumer<SslConfig> configConsumer,
                               KeyManagerFactory keyManagerFactory) {
        super(finisher, configConsumer);
        this.keyManagerFactory = keyManagerFactory;
    }

    BaseServerSslConfigBuilder(final Supplier<F> finisher, final Consumer<SslConfig> configConsumer,
                               Supplier<InputStream> keyCertChainSupplier, Supplier<InputStream> keySupplier) {
        super(finisher, configConsumer);
        this.keyCertChainSupplier = keyCertChainSupplier;
        this.keySupplier = keySupplier;
    }

    BaseServerSslConfigBuilder(final Supplier<F> finisher, final Consumer<SslConfig> configConsumer,
                               Supplier<InputStream> keyCertChainSupplier, Supplier<InputStream> keySupplier,
                               @Nullable String keyPassword) {
        super(finisher, configConsumer);
        this.keyCertChainSupplier = keyCertChainSupplier;
        this.keySupplier = keySupplier;
        this.keyPassword = keyPassword;
    }

    /**
     * Sets the client authentication mode.
     *
     * @param clientAuth the auth configuration to use.
     * @return self.
     */
    public B clientAuth(SslConfig.ClientAuth clientAuth) {
        this.clientAuth = requireNonNull(clientAuth);
        return castAsB();
    }

    /**
     * /**
     * Build and return a new {@link SslConfig}.
     *
     * @return a new {@link SslConfig}.
     */
    @Override
    SslConfig buildInternal() {
        return new SslConfigImpl(true, trustManagerFactory, trustCertChainSupplier, keyManagerFactory,
                keyCertChainSupplier, keySupplier, keyPassword, ciphers, sessionCacheSize, sessionTimeout, clientAuth,
                apn, provider, protocols, null, null,
                -1, null);
    }
}

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

/**
 * Builder for {@link SslConfig} for servers.
 *
 * @see SslConfigBuilders for static factory methods.
 */
public final class StandaloneServerSslConfigBuilder
        extends BaseServerSslConfigBuilder<StandaloneServerSslConfigBuilder, SslConfig> {

    StandaloneServerSslConfigBuilder(final Supplier<SslConfig> finisher, final Consumer<SslConfig> configConsumer,
                                     final KeyManagerFactory keyManagerFactory) {
        super(finisher, configConsumer, keyManagerFactory);
    }

    StandaloneServerSslConfigBuilder(final Supplier<SslConfig> finisher, final Consumer<SslConfig> configConsumer,
                                     final Supplier<InputStream> keyCertChainSupplier,
                                     final Supplier<InputStream> keySupplier) {
        super(finisher, configConsumer, keyCertChainSupplier, keySupplier);
    }

    StandaloneServerSslConfigBuilder(final Supplier<SslConfig> finisher, final Consumer<SslConfig> configConsumer,
                                     final Supplier<InputStream> keyCertChainSupplier,
                                     final Supplier<InputStream> keySupplier, @Nullable final String keyPassword) {
        super(finisher, configConsumer, keyCertChainSupplier, keySupplier, keyPassword);
    }

    /**
     * Build and return a new {@link SslConfig}.
     *
     * @return a new {@link SslConfig}.
     */
    public SslConfig build() {
        return finishInternal();
    }
}

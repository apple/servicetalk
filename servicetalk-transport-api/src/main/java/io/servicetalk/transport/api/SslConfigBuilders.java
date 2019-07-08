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
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;
import javax.net.ssl.KeyManagerFactory;

/**
 * Factory methods for building {@link ServerSslConfigBuilder}.
 */
public final class SslConfigBuilders {

    private SslConfigBuilders() {
        // No instances
    }

    /**
     * Creates a builder for new server-side {@link SslConfig}.
     *
     * @param keyManagerFactory an {@link KeyManagerFactory}.
     * @return a new {@link StandaloneServerSslConfigBuilder}.
     */
    public static StandaloneServerSslConfigBuilder forServer(final KeyManagerFactory keyManagerFactory) {
        AtomicReference<SslConfig> configRef = new AtomicReference<>();
        return new StandaloneServerSslConfigBuilder(configRef::get, configRef::set, keyManagerFactory);
    }

    /**
     * Creates a builder for new server-side {@link SslConfig}.
     *
     * @param keyCertChainSupplier an {@link Supplier} that will provide an input stream for a X.509 certificate chain
     * in PEM format.
     * <p>
     * The responsibility to call {@link InputStream#close()} is transferred to callers of the {@link Supplier}.
     * If this is not the desired behavior then wrap the {@link InputStream} and override {@link InputStream#close()}.
     * @param keySupplier an {@link Supplier} that will provide an input stream for a KCS#8 private key in PEM format.
     * <p>
     * The responsibility to call {@link InputStream#close()} is transferred to callers of the {@link Supplier}.
     * If this is not the desired behavior then wrap the {@link InputStream} and override {@link InputStream#close()}.
     * @return a new {@link StandaloneServerSslConfigBuilder}.
     */
    public static StandaloneServerSslConfigBuilder forServer(final Supplier<InputStream> keyCertChainSupplier,
                                                             final Supplier<InputStream> keySupplier) {
        AtomicReference<SslConfig> configRef = new AtomicReference<>();
        return new StandaloneServerSslConfigBuilder(configRef::get, configRef::set, keyCertChainSupplier, keySupplier);
    }

    /**
     * Creates a builder for new server-side {@link SslConfig}.
     *
     * @param keyCertChainSupplier an {@link Supplier} that will provide an input stream for a X.509 certificate chain
     * in PEM format.
     * <p>
     * The responsibility to call {@link InputStream#close()} is transferred to callers of the {@link Supplier}.
     * If this is not the desired behavior then wrap the {@link InputStream} and override {@link InputStream#close()}.
     * @param keySupplier an {@link Supplier} that will provide an input stream for a KCS#8 private key in PEM format.
     * <p>
     * The responsibility to call {@link InputStream#close()} is transferred to callers of the {@link Supplier}.
     * If this is not the desired behavior then wrap the {@link InputStream} and override {@link InputStream#close()}.
     * @param keyPassword the password of the {@code keyFile} if it's password-protected.
     * @return a new {@link StandaloneServerSslConfigBuilder}.
     */
    public static StandaloneServerSslConfigBuilder forServer(final Supplier<InputStream> keyCertChainSupplier,
                                                             final Supplier<InputStream> keySupplier,
                                                             final String keyPassword) {
        AtomicReference<SslConfig> configRef = new AtomicReference<>();
        return new StandaloneServerSslConfigBuilder(configRef::get, configRef::set,
                keyCertChainSupplier, keySupplier, keyPassword);
    }
}

/*
 * Copyright © 2021 Apple Inc. and the ServiceTalk project authors
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
import java.util.List;
import java.util.function.Supplier;
import javax.annotation.Nullable;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.TrustManagerFactory;

abstract class AbstractSslConfig implements SslConfig {
    @Nullable
    private final TrustManagerFactory trustManagerFactory;
    @Nullable
    private final Supplier<InputStream> trustCertChainSupplier;
    @Nullable
    private final KeyManagerFactory keyManagerFactory;
    @Nullable
    private final Supplier<InputStream> keyCertChainSupplier;
    @Nullable
    private final Supplier<InputStream> keySupplier;
    @Nullable
    private final String keyPassword;
    @Nullable
    private final List<String> sslProtocols;
    @Nullable
    private final List<String> alpnProtocols;
    @Nullable
    private final Iterable<String> ciphers;
    private final long sessionCacheSize;
    private final long sessionTimeout;
    @Nullable
    private final SslProvider provider;

    AbstractSslConfig(@Nullable final TrustManagerFactory trustManagerFactory,
                      @Nullable final Supplier<InputStream> trustCertChainSupplier,
                      @Nullable final KeyManagerFactory keyManagerFactory,
                      @Nullable final Supplier<InputStream> keyCertChainSupplier,
                      @Nullable final Supplier<InputStream> keySupplier,
                      @Nullable final String keyPassword, @Nullable final List<String> sslProtocols,
                      @Nullable final List<String> alpnProtocols,
                      @Nullable final Iterable<String> ciphers, final long sessionCacheSize,
                      final long sessionTimeout, @Nullable final SslProvider provider) {
        this.trustManagerFactory = trustManagerFactory;
        this.trustCertChainSupplier = trustCertChainSupplier;
        this.keyManagerFactory = keyManagerFactory;
        this.keyCertChainSupplier = keyCertChainSupplier;
        this.keySupplier = keySupplier;
        this.keyPassword = keyPassword;
        this.sslProtocols = sslProtocols;
        this.alpnProtocols = alpnProtocols;
        this.ciphers = ciphers;
        this.sessionCacheSize = sessionCacheSize;
        this.sessionTimeout = sessionTimeout;
        this.provider = provider;
    }

    @Nullable
    @Override
    public final TrustManagerFactory trustManagerFactory() {
        return trustManagerFactory;
    }

    @Nullable
    @Override
    public final Supplier<InputStream> trustCertChainSupplier() {
        return trustCertChainSupplier;
    }

    @Nullable
    @Override
    public final KeyManagerFactory keyManagerFactory() {
        return keyManagerFactory;
    }

    @Nullable
    @Override
    public final Supplier<InputStream> keyCertChainSupplier() {
        return keyCertChainSupplier;
    }

    @Nullable
    @Override
    public final Supplier<InputStream> keySupplier() {
        return keySupplier;
    }

    @Nullable
    @Override
    public final String keyPassword() {
        return keyPassword;
    }

    @Nullable
    @Override
    public final List<String> sslProtocols() {
        return sslProtocols;
    }

    @Nullable
    @Override
    public final List<String> alpnProtocols() {
        return alpnProtocols;
    }

    @Nullable
    @Override
    public final Iterable<String> ciphers() {
        return ciphers;
    }

    @Override
    public long sessionCacheSize() {
        return sessionCacheSize;
    }

    @Override
    public final long sessionTimeout() {
        return sessionTimeout;
    }

    @Nullable
    @Override
    public final SslProvider provider() {
        return provider;
    }
}

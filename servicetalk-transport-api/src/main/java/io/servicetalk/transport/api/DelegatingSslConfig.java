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

import static java.util.Objects.requireNonNull;

/**
 * Wrap a {@link SslConfig} and delegate all methods to it.
 * @param <T> The type of {@link SslConfig} to delegate to.
 */
public abstract class DelegatingSslConfig<T extends SslConfig> implements SslConfig {
    private final T delegate;

    /**
     * Create a new instance.
     * @param delegate The instance to delegate to.
     */
    protected DelegatingSslConfig(final T delegate) {
        this.delegate = requireNonNull(delegate);
    }

    /**
     * Get the {@link T} to delegate to.
     * @return the {@link T} to delegate to.
     */
    protected T delegate() {
        return delegate;
    }

    @Nullable
    @Override
    public TrustManagerFactory trustManagerFactory() {
        return delegate.trustManagerFactory();
    }

    @Nullable
    @Override
    public Supplier<InputStream> trustCertChainSupplier() {
        return delegate.trustCertChainSupplier();
    }

    @Nullable
    @Override
    public KeyManagerFactory keyManagerFactory() {
        return delegate.keyManagerFactory();
    }

    @Nullable
    @Override
    public Supplier<InputStream> keyCertChainSupplier() {
        return delegate.keyCertChainSupplier();
    }

    @Nullable
    @Override
    public Supplier<InputStream> keySupplier() {
        return delegate.keySupplier();
    }

    @Nullable
    @Override
    public String keyPassword() {
        return delegate.keyPassword();
    }

    @Nullable
    @Override
    public List<String> sslProtocols() {
        return delegate.sslProtocols();
    }

    @Nullable
    @Override
    public List<String> alpnProtocols() {
        return delegate.alpnProtocols();
    }

    @Nullable
    @Override
    public List<String> ciphers() {
        return delegate.ciphers();
    }

    @Override
    public long sessionCacheSize() {
        return delegate.sessionCacheSize();
    }

    @Override
    public long sessionTimeout() {
        return delegate.sessionTimeout();
    }

    @Nullable
    @Override
    public SslProvider provider() {
        return delegate.provider();
    }
}

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
package io.servicetalk.transport.netty.internal;

import io.servicetalk.transport.api.SecurityConfigurator.SslProvider;

import java.io.InputStream;
import java.util.List;
import java.util.function.Supplier;
import javax.annotation.Nullable;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.TrustManagerFactory;

import static java.util.Collections.unmodifiableList;

/**
 * A base security config for both client and server.
 */
class ReadOnlySecurityConfig {
    @Nullable
    Supplier<InputStream> trustCertChainSupplier;
    @Nullable
    TrustManagerFactory trustManagerFactory;
    @Nullable
    List<String> protocols;
    @Nullable
    Iterable<String> ciphers;
    long sessionCacheSize;
    long sessionTimeout;
    SslProvider provider = SslProvider.AUTO;

    @Nullable
    protected KeyManagerFactory keyManagerFactory;
    @Nullable
    protected Supplier<InputStream> keyCertChainSupplier;
    @Nullable
    protected Supplier<InputStream> keySupplier;
    @Nullable
    protected String keyPassword;

    ReadOnlySecurityConfig() {
    }

    ReadOnlySecurityConfig(ReadOnlySecurityConfig from) {
        trustCertChainSupplier = from.trustCertChainSupplier;
        trustManagerFactory = from.trustManagerFactory;
        protocols = from.protocols == null ? null : unmodifiableList(from.protocols);
        ciphers = from.ciphers;
        sessionCacheSize = from.sessionCacheSize;
        sessionTimeout = from.sessionTimeout;
        provider = from.provider;
        keyManagerFactory = from.keyManagerFactory;
        keyCertChainSupplier = from.keyCertChainSupplier;
        keySupplier = from.keySupplier;
        keyPassword = from.keyPassword;
    }

    @Nullable
    Supplier<InputStream> trustCertChainSupplier() {
        return trustCertChainSupplier;
    }

    @Nullable
    TrustManagerFactory trustManagerFactory() {
        return trustManagerFactory;
    }

    @Nullable
    List<String> protocols() {
        return protocols;
    }

    @Nullable
    Iterable<String> ciphers() {
        return ciphers;
    }

    long sessionCacheSize() {
        return sessionCacheSize;
    }

    long sessionTimeout() {
        return sessionTimeout;
    }

    SslProvider provider() {
        return provider;
    }

    @Nullable
    KeyManagerFactory keyManagerFactory() {
        return keyManagerFactory;
    }

    @Nullable
    Supplier<InputStream> keyCertChainSupplier() {
        return keyCertChainSupplier;
    }

    @Nullable
    Supplier<InputStream> keySupplier() {
        return keySupplier;
    }

    @Nullable
    String keyPassword() {
        return keyPassword;
    }
}

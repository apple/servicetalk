/*
 * Copyright © 2018 Apple Inc. and the ServiceTalk project authors
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

import io.servicetalk.transport.api.SslConfig;

import io.netty.handler.ssl.ClientAuth;
import io.netty.handler.ssl.SslContext;
import io.netty.handler.ssl.SslContextBuilder;

import java.io.InputStream;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLException;

import static io.servicetalk.transport.netty.internal.BuilderUtils.closeAndRethrowUnchecked;
import static java.util.Objects.requireNonNull;

/**
 * A factory for creating {@link SslContext}s.
 */
public final class SSLContextFactory {

    private SSLContextFactory() {
        // No instances.
    }

    /**
     * A new context for a client using the passed {@code config}.
     *
     * @param config SSL config.
     * @return A new {@link SslContext} for a client.
     */
    public static SslContext forClient(SslConfig config) {
        requireNonNull(config);
        if (config.isServer()) {
            throw new IllegalArgumentException("SslConfig was built for server");
        }

        SslContextBuilder builder = SslContextBuilder.forClient()
                .sessionCacheSize(config.getSessionCacheSize()).sessionTimeout(config.getSessionTimeout());
        configureTrustManager(config, builder);
        KeyManagerFactory keyManagerFactory = config.getKeyManagerFactory();
        if (keyManagerFactory != null) {
            builder.keyManager(keyManagerFactory);
        } else {
            InputStream keyCertChainSupplier = null;
            InputStream keySupplier = null;
            try {
                keyCertChainSupplier = config.getKeyCertChainSupplier().get();
                keySupplier = config.getKeySupplier().get();
                builder.keyManager(keyCertChainSupplier, keySupplier, config.getKeyPassword());
            } finally {
                try {
                    closeAndRethrowUnchecked(keyCertChainSupplier);
                } finally {
                    closeAndRethrowUnchecked(keySupplier);
                }
            }
            builder.sslProvider(SslUtils.toNettySslProvider(config.getProvider()));
        }
        builder.ciphers(config.getCiphers());
        builder.applicationProtocolConfig(SslUtils.toNettyApplicationProtocol(config.getApn()));
        try {
            return new WrappingSslContext(builder.build(), config.getProtocols());
        } catch (SSLException e) {
            throw new IllegalArgumentException(e);
        }
    }

    /**
     * A new context for a server using the passed {@code config}.
     *
     * @param config SSL config.
     * @return A new {@link SslContext} for a server.
     */
    public static SslContext forServer(SslConfig config) {
        requireNonNull(config);
        if (!config.isServer()) {
            throw new IllegalArgumentException("SslConfig for clients cannot be used when building a server");
        }
        SslContextBuilder builder;

        KeyManagerFactory keyManagerFactory = config.getKeyManagerFactory();
        if (keyManagerFactory != null) {
            builder = SslContextBuilder.forServer(keyManagerFactory);
        } else {
            InputStream keyCertChainSupplier = null;
            InputStream keySupplier = null;
            try {
                keyCertChainSupplier = config.getKeyCertChainSupplier().get();
                keySupplier = config.getKeySupplier().get();
                builder = SslContextBuilder.forServer(keyCertChainSupplier, keySupplier, config.getKeyPassword());
            } finally {
                try {
                    closeAndRethrowUnchecked(keyCertChainSupplier);
                } finally {
                    closeAndRethrowUnchecked(keySupplier);
                }
            }
            builder.sslProvider(SslUtils.toNettySslProvider(config.getProvider()));
        }

        builder.sessionCacheSize(config.getSessionCacheSize()).sessionTimeout(config.getSessionTimeout())
                .applicationProtocolConfig(SslUtils.toNettyApplicationProtocol(config.getApn()));

        switch (config.getClientAuth()) {
            case NONE:
                builder.clientAuth(ClientAuth.NONE);
                break;
            case OPTIONAL:
                builder.clientAuth(ClientAuth.OPTIONAL);
                break;
            case REQUIRE:
                builder.clientAuth(ClientAuth.REQUIRE);
                break;
            default:
                throw new IllegalArgumentException("Unsupported ClientAuth value: " + config.getClientAuth());
        }
        configureTrustManager(config, builder);
        builder.ciphers(config.getCiphers());

        builder.sslProvider(SslUtils.toNettySslProvider(config.getProvider()));
        try {
            return new WrappingSslContext(builder.build(), config.getProtocols());
        } catch (SSLException e) {
            throw new IllegalArgumentException(e);
        }
    }

    private static void configureTrustManager(SslConfig config, SslContextBuilder builder) {
        if (config.getTrustManagerFactory() != null) {
            builder.trustManager(config.getTrustManagerFactory());
        } else {
            InputStream trustManagerStream = config.getTrustCertChainSupplier().get();
            try {
                builder.trustManager(trustManagerStream);
            } finally {
                closeAndRethrowUnchecked(trustManagerStream);
            }
        }
    }
}

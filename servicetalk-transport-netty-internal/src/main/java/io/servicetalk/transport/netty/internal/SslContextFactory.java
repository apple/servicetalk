/*
 * Copyright © 2018-2020 Apple Inc. and the ServiceTalk project authors
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

import io.servicetalk.transport.api.ClientSslConfig;
import io.servicetalk.transport.api.ServerSslConfig;
import io.servicetalk.transport.api.SslConfig;

import io.netty.handler.ssl.ClientAuth;
import io.netty.handler.ssl.SslContext;
import io.netty.handler.ssl.SslContextBuilder;

import java.io.InputStream;
import java.util.List;
import java.util.function.Supplier;
import javax.annotation.Nullable;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLException;

import static io.servicetalk.transport.netty.internal.BuilderUtils.closeAndRethrowUnchecked;
import static io.servicetalk.transport.netty.internal.SslUtils.nettyApplicationProtocol;
import static io.servicetalk.transport.netty.internal.SslUtils.toNettySslProvider;

/**
 * A factory for creating {@link SslContext}s.
 */
public final class SslContextFactory {

    private SslContextFactory() {
        // No instances.
    }

    /**
     * A new context for a client using the passed {@code config}.
     *
     * @param config SSL config.
     * @return A new {@link SslContext} for a client.
     */
    public static SslContext forClient(ClientSslConfig config) {
        final SslContextBuilder builder = SslContextBuilder.forClient()
                .sessionCacheSize(config.sessionCacheSize()).sessionTimeout(config.sessionTimeout());
        configureTrustManager(config, builder);
        KeyManagerFactory keyManagerFactory = config.keyManagerFactory();
        if (keyManagerFactory != null) {
            builder.keyManager(keyManagerFactory);
        } else {
            InputStream keyCertChainSupplier = null;
            InputStream keySupplier = null;
            try {
                keyCertChainSupplier = supplierNullSafe(config.keyCertChainSupplier());
                keySupplier = supplierNullSafe(config.keySupplier());
                builder.keyManager(keyCertChainSupplier, keySupplier, config.keyPassword());
            } finally {
                try {
                    closeAndRethrowUnchecked(keyCertChainSupplier);
                } finally {
                    closeAndRethrowUnchecked(keySupplier);
                }
            }
        }
        List<String> alpnProtocols = config.alpnProtocols();
        builder.sslProvider(toNettySslProvider(config.provider(), alpnProtocols != null && !alpnProtocols.isEmpty()));

        builder.protocols(config.sslProtocols());
        builder.ciphers(config.ciphers());
        builder.applicationProtocolConfig(nettyApplicationProtocol(alpnProtocols));
        try {
            return builder.build();
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
    public static SslContext forServer(ServerSslConfig config) {
        final SslContextBuilder builder;
        KeyManagerFactory keyManagerFactory = config.keyManagerFactory();
        if (keyManagerFactory != null) {
            builder = SslContextBuilder.forServer(keyManagerFactory);
        } else {
            InputStream keyCertChainSupplier = null;
            InputStream keySupplier = null;
            try {
                keyCertChainSupplier = supplierNullSafe(config.keyCertChainSupplier());
                keySupplier = supplierNullSafe(config.keySupplier());
                builder = SslContextBuilder.forServer(keyCertChainSupplier, keySupplier, config.keyPassword());
            } finally {
                try {
                    closeAndRethrowUnchecked(keyCertChainSupplier);
                } finally {
                    closeAndRethrowUnchecked(keySupplier);
                }
            }
        }

        List<String> alpnProtocols = config.alpnProtocols();
        builder.sessionCacheSize(config.sessionCacheSize()).sessionTimeout(config.sessionTimeout())
                .applicationProtocolConfig(nettyApplicationProtocol(alpnProtocols));

        switch (config.clientAuthMode()) {
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
                throw new IllegalArgumentException("Unsupported: " + config.clientAuthMode());
        }
        configureTrustManager(config, builder);
        builder.protocols(config.sslProtocols());
        builder.ciphers(config.ciphers());

        builder.sslProvider(toNettySslProvider(config.provider(), alpnProtocols != null && !alpnProtocols.isEmpty()));
        try {
            return builder.build();
        } catch (SSLException e) {
            throw new IllegalArgumentException(e);
        }
    }

    private static void configureTrustManager(SslConfig config, SslContextBuilder builder) {
        if (config.trustManagerFactory() != null) {
            builder.trustManager(config.trustManagerFactory());
        } else {
            InputStream trustManagerStream = supplierNullSafe(config.trustCertChainSupplier());
            try {
                builder.trustManager(trustManagerStream);
            } finally {
                closeAndRethrowUnchecked(trustManagerStream);
            }
        }
    }

    @Nullable
    private static <T> T supplierNullSafe(@Nullable Supplier<T> supplier) {
        return supplier == null ? null : supplier.get();
    }
}

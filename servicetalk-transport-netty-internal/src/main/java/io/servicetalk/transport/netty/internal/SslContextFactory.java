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

import io.servicetalk.transport.api.CertificateCompressionAlgorithm;
import io.servicetalk.transport.api.CertificateCompressionAlgorithms;
import io.servicetalk.transport.api.ClientSslConfig;
import io.servicetalk.transport.api.ServerSslConfig;
import io.servicetalk.transport.api.SslConfig;

import io.netty.handler.ssl.ClientAuth;
import io.netty.handler.ssl.OpenSslCertificateCompressionConfig;
import io.netty.handler.ssl.OpenSslContextOption;
import io.netty.handler.ssl.SslContext;
import io.netty.handler.ssl.SslContextBuilder;
import io.netty.handler.ssl.SslProvider;

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
        SslProvider nettySslProvider =
                toNettySslProvider(config.provider(), alpnProtocols != null && !alpnProtocols.isEmpty());
        builder.sslProvider(nettySslProvider);

        builder.protocols(config.sslProtocols());
        builder.ciphers(config.ciphers());
        builder.applicationProtocolConfig(nettyApplicationProtocol(alpnProtocols));

        configureCertificateCompression(config, builder, nettySslProvider, false);

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

        io.netty.handler.ssl.SslProvider nettySslProvider =
                toNettySslProvider(config.provider(), alpnProtocols != null && !alpnProtocols.isEmpty());
        builder.sslProvider(nettySslProvider);

        configureCertificateCompression(config, builder, nettySslProvider, true);

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

    /**
     * Configures TLS Certificate Compression if enabled and available.
     * <p>
     * Note that in addition to the application actually enabling and configuring TLS certificate compression on the
     * {@link SslConfig}, it must also be available in the environment. Right now it is only supported through
     * BoringSSL and as such the code checks if the {@link OpenSslContextOption} is available at runtime. If it is not,
     * no error is raised but the feature is not enabled. This is by design, since certificate compression is a pure
     * optimization instead of a security feature.
     *
     * @param config the ServiceTalk TLS config which enables and configures cert compression.
     * @param builder netty's builder for the SSL context.
     * @param nettySslProvider the (potentially null) SSL provider used with netty.
     * @param forServer if this is for a server or client context.
     */
    private static void configureCertificateCompression(SslConfig config, SslContextBuilder builder,
                                                        @Nullable io.netty.handler.ssl.SslProvider nettySslProvider,
                                                        boolean forServer) {
        final List<CertificateCompressionAlgorithm> algorithms = config.certificateCompressionAlgorithms();
        if (algorithms == null || algorithms.isEmpty()) {
            return;
        }

        if (nettySslProvider == null) {
            nettySslProvider = forServer ? SslContext.defaultServerProvider() : SslContext.defaultClientProvider();
        }

        if (!SslProvider.isOptionSupported(nettySslProvider, OpenSslContextOption.CERTIFICATE_COMPRESSION_ALGORITHMS)) {
            return;
        }

        final OpenSslCertificateCompressionConfig.Builder configBuilder =
                OpenSslCertificateCompressionConfig.newBuilder();
        for (CertificateCompressionAlgorithm algorithm : algorithms) {
            // Right now all we support is ZLIB - and it is not possible for the user to extend the list. Once
            // the API is opened up this logic needs to change and take user-fed algorithms into account.
            if (algorithm.algorithmId() == CertificateCompressionAlgorithms.ZLIB_ALGORITHM_ID) {
                configBuilder.addAlgorithm(
                        ZlibOpenSslCertificateCompressionAlgorithm.INSTANCE,
                        OpenSslCertificateCompressionConfig.AlgorithmMode.Both
                );
            } else {
                throw new IllegalArgumentException("Unsupported: " + algorithm);
            }
        }
        builder.option(OpenSslContextOption.CERTIFICATE_COMPRESSION_ALGORITHMS, configBuilder.build());
    }

    @Nullable
    private static <T> T supplierNullSafe(@Nullable Supplier<T> supplier) {
        return supplier == null ? null : supplier.get();
    }
}

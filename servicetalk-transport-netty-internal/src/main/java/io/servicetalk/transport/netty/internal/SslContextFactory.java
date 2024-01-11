/*
 * Copyright © 2018-2021, 2023 Apple Inc. and the ServiceTalk project authors
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
import io.servicetalk.transport.api.SslConfig.CipherSuiteFilter;

import io.netty.handler.ssl.ClientAuth;
import io.netty.handler.ssl.IdentityCipherSuiteFilter;
import io.netty.handler.ssl.OpenSslCertificateCompressionConfig;
import io.netty.handler.ssl.OpenSslContextOption;
import io.netty.handler.ssl.SslContext;
import io.netty.handler.ssl.SslContextBuilder;
import io.netty.handler.ssl.SslContextOption;
import io.netty.handler.ssl.SslProvider;
import io.netty.handler.ssl.SupportedCipherSuiteFilter;
import io.netty.util.AttributeKey;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.InputStream;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.util.List;
import java.util.function.Supplier;
import javax.annotation.Nullable;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLException;

import static io.netty.handler.ssl.OpenSslContextOption.MAX_CERTIFICATE_LIST_BYTES;
import static io.netty.util.AttributeKey.newInstance;
import static io.servicetalk.transport.netty.internal.BuilderUtils.closeAndRethrowUnchecked;
import static io.servicetalk.transport.netty.internal.SslUtils.nettyApplicationProtocol;
import static io.servicetalk.transport.netty.internal.SslUtils.toNettySslProvider;
import static io.servicetalk.utils.internal.ThrowableUtils.throwException;

/**
 * A factory for creating {@link SslContext}s.
 */
public final class SslContextFactory {

    static final AttributeKey<Long> HANDSHAKE_TIMEOUT_MILLIS = newInstance("HANDSHAKE_TIMEOUT_MILLIS");

    private static final Logger LOGGER = LoggerFactory.getLogger(SslContextFactory.class);

    @Nullable
    private static final MethodHandle SSL_PROVIDER_OPTION_SUPPORTED;

    static {
        MethodHandle sslProviderOptionSupported;
        try {
            sslProviderOptionSupported = MethodHandles.publicLookup().findStatic(
                    SslProvider.class, "isOptionSupported",
                    MethodType.methodType(boolean.class, SslProvider.class, SslContextOption.class));
        } catch (Throwable cause) {
            LOGGER.debug("SSLProvider#isOptionSupported(SslProvider, SslContextOption) is available only " +
                    "starting from Netty 4.1.88.Final. Detected Netty version: {}",
                    SslProvider.class.getPackage().getImplementationVersion(), cause);
            sslProviderOptionSupported = null;
        }
        SSL_PROVIDER_OPTION_SUPPORTED = sslProviderOptionSupported;
    }

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
        final SslContextBuilder builder = SslContextBuilder.forClient();
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

        return configureBuilder(config, builder, false);
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
                throw new IllegalArgumentException("Unsupported SslClientAuthMode: " + config.clientAuthMode());
        }

        return configureBuilder(config, builder, true);
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
                                                        @Nullable SslProvider nettySslProvider,
                                                        boolean forServer) {
        final List<CertificateCompressionAlgorithm> algorithms = config.certificateCompressionAlgorithms();
        if (algorithms == null || algorithms.isEmpty()) {
            return;
        }

        if (nettySslProvider == null) {
            nettySslProvider = forServer ? SslContext.defaultServerProvider() : SslContext.defaultClientProvider();
        }

        try {
            // If the newer SSL_PROVIDER_OPTION_SUPPORTED is available through the MethodHandle, use this option
            // to check since it more targeted/narrow to what we need.
            if (SSL_PROVIDER_OPTION_SUPPORTED != null) {
                if (!((boolean) SSL_PROVIDER_OPTION_SUPPORTED.invokeExact(nettySslProvider,
                        (SslContextOption<?>) OpenSslContextOption.CERTIFICATE_COMPRESSION_ALGORITHMS))) {
                    return;
                }
            // Otherwise fall-back to just checking if OpenSSL is used which is good enough as a fallback.
            } else if (nettySslProvider != SslProvider.OPENSSL) {
                return;
            }
        } catch (Throwable throwable) {
            throwException(throwable);
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

    private static void configureNettyOptions(SslConfig config, SslContextBuilder builder) {
        final int maxCertificateListBytes = config.maxCertificateListBytes();
        if (maxCertificateListBytes > 0) {
            builder.option(MAX_CERTIFICATE_LIST_BYTES, maxCertificateListBytes);
        }
    }

    private static SslContext configureBuilder(SslConfig config, SslContextBuilder builder, boolean forServer) {
        configureTrustManager(config, builder);
        List<String> alpnProtocols = config.alpnProtocols();
        SslProvider nettySslProvider =
                toNettySslProvider(config.provider(), alpnProtocols != null && !alpnProtocols.isEmpty());
        builder.sessionCacheSize(config.sessionCacheSize())
                .sessionTimeout(config.sessionTimeout())
                .applicationProtocolConfig(nettyApplicationProtocol(alpnProtocols))
                .sslProvider(toNettySslProvider(config.provider(), alpnProtocols != null && !alpnProtocols.isEmpty()))
                .protocols(config.sslProtocols())
                .ciphers(config.ciphers(), toNettyCipherSuiteFilter(config.cipherSuiteFilter()));

        configureCertificateCompression(config, builder, nettySslProvider, forServer);
        configureNettyOptions(config, builder);

        final SslContext sslContext;
        try {
            sslContext = builder.build();
        } catch (SSLException e) {
            throw new IllegalArgumentException("Failed to build SslContext", e);
        }
        sslContext.attributes().attr(HANDSHAKE_TIMEOUT_MILLIS).set(config.handshakeTimeout().toMillis());
        return sslContext;
    }

    @Nullable
    private static <T> T supplierNullSafe(@Nullable Supplier<T> supplier) {
        return supplier == null ? null : supplier.get();
    }

    private static io.netty.handler.ssl.CipherSuiteFilter toNettyCipherSuiteFilter(
            final CipherSuiteFilter cipherSuiteFilter) {
        switch (cipherSuiteFilter) {
            case IDENTITY:
                return IdentityCipherSuiteFilter.INSTANCE;
            case SUPPORTED:
                return SupportedCipherSuiteFilter.INSTANCE;
            default:
                throw new IllegalArgumentException("Unsupported CipherSuiteFilter: " + cipherSuiteFilter);
        }
    }
}

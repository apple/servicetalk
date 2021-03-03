/*
 * Copyright © 2018-2019 Apple Inc. and the ServiceTalk project authors
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

import io.netty.buffer.ByteBufAllocator;
import io.netty.handler.ssl.ApplicationProtocolConfig;
import io.netty.handler.ssl.OpenSsl;
import io.netty.handler.ssl.SslContext;
import io.netty.handler.ssl.SslHandler;
import io.netty.handler.ssl.SslProvider;
import io.netty.util.ReferenceCountUtil;

import java.net.InetSocketAddress;
import java.util.List;
import javax.annotation.Nullable;
import javax.net.ssl.SNIHostName;
import javax.net.ssl.SSLEngine;
import javax.net.ssl.SSLParameters;

import static io.netty.handler.ssl.ApplicationProtocolConfig.Protocol.ALPN;
import static io.netty.handler.ssl.ApplicationProtocolConfig.SelectedListenerFailureBehavior.ACCEPT;
import static io.netty.handler.ssl.ApplicationProtocolConfig.SelectorFailureBehavior.NO_ADVERTISE;
import static io.netty.handler.ssl.SslProvider.isAlpnSupported;
import static java.util.Collections.singletonList;

/**
 * Utility for SSL.
 */
final class SslUtils {
    private SslUtils() {
        // Utility class
    }

    /**
     * Creates a new {@link SslHandler} which will supports SNI if the {@link InetSocketAddress} was created from
     * a hostname.
     *
     * @param context the {@link SslContext} which will be used to create the {@link SslHandler}
     * @param allocator the {@link ByteBufAllocator} which will be used to allocate direct memory if required for
     * {@link SSLEngine}
     * @param sslConfig used to obtain configuration for the {@link SslHandler}.
     * @return a {@link SslHandler}
     */
    static SslHandler newHandler(SslContext context, ByteBufAllocator allocator, ClientSslConfig sslConfig) {
        SslHandler handler = context.newHandler(allocator, sslConfig.peerHost(), sslConfig.peerPort());
        SSLEngine engine = handler.engine();
        try {
            String hostnameVerificationAlgorithm = sslConfig.hostnameVerificationAlgorithm();
            String sniHostname = sslConfig.sniHostname();
            SSLParameters parameters = engine.getSSLParameters();
            if (hostnameVerificationAlgorithm != null) {
                parameters.setEndpointIdentificationAlgorithm(hostnameVerificationAlgorithm);
            }
            if (sniHostname != null) {
                // https://tools.ietf.org/html/rfc6066#section-3
                // Multiple names of the same name_type are therefore now prohibited.
                parameters.setServerNames(singletonList(new SNIHostName(sniHostname)));
            }
            engine.setSSLParameters(parameters);
        } catch (Throwable cause) {
            ReferenceCountUtil.release(engine);
            throw cause;
        }
        return handler;
    }

    /**
     * Creates a new {@link SslHandler}.
     *
     * @param context the {@link SslContext} which will be used to create the {@link SslHandler}
     * @param allocator the {@link ByteBufAllocator} which will be used
     * @return a {@link SslHandler}
     */
    static SslHandler newHandler(SslContext context, ByteBufAllocator allocator) {
        return context.newHandler(allocator);
    }

    /**
     * Create netty's {@link ApplicationProtocolConfig}.
     *
     * @param supportedAlpnProtocols the list of supported ALPN protocols.
     * @return the new {@link ApplicationProtocolConfig}.
     */
    static ApplicationProtocolConfig nettyApplicationProtocol(@Nullable List<String> supportedAlpnProtocols) {
        if (supportedAlpnProtocols == null || supportedAlpnProtocols.isEmpty()) {
            return ApplicationProtocolConfig.DISABLED;
        }
        return new ApplicationProtocolConfig(ALPN, NO_ADVERTISE, ACCEPT, supportedAlpnProtocols);
    }

    /**
     * Convert to netty type.
     *
     * @param provider the provider to convert.
     * @param alpn if {@code true} ALPN should be supported.
     * @return the netty provider.
     */
    @Nullable
    static SslProvider toNettySslProvider(@Nullable io.servicetalk.transport.api.SslProvider provider, boolean alpn) {
        if (provider == null) {
            if (alpn) {
                if (isAlpnSupported(SslProvider.OPENSSL)) {
                    return SslProvider.OPENSSL;
                } else if (isAlpnSupported(SslProvider.JDK)) {
                    return SslProvider.JDK;
                } else {
                    throw new IllegalStateException("ALPN configured but not supported by the current classpath: " +
                            "add OPENSSL support (https://netty.io/wiki/forked-tomcat-native.html) or configure " +
                            "ALPN for JDK (https://www.eclipse.org/jetty/documentation/current/alpn-chapter.html)");
                }
            }
            return null;
        }
        switch (provider) {
            case JDK:
                if (alpn && !isAlpnSupported(SslProvider.JDK)) {
                    throw new IllegalStateException(
                            "ALPN configured but not supported by the current classpath. For more information, " +
                                    "see https://www.eclipse.org/jetty/documentation/current/alpn-chapter.html");
                }
                return SslProvider.JDK;
            case OPENSSL:
                OpenSsl.ensureAvailability();
                if (alpn && !isAlpnSupported(SslProvider.OPENSSL)) {
                    throw new IllegalStateException(
                            "ALPN configured but not supported by installed version of OpenSSL");
                }
                return SslProvider.OPENSSL;
            default:
                throw new Error("Unknown SSL provider specified: " + provider);
        }
    }
}

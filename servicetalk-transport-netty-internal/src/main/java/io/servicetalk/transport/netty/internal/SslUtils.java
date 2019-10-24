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

import io.servicetalk.transport.api.SecurityConfigurator;

import io.netty.buffer.ByteBufAllocator;
import io.netty.handler.ssl.ApplicationProtocolConfig;
import io.netty.handler.ssl.OpenSsl;
import io.netty.handler.ssl.SslContext;
import io.netty.handler.ssl.SslHandler;
import io.netty.handler.ssl.SslProvider;
import io.netty.util.NetUtil;
import io.netty.util.ReferenceCountUtil;

import java.net.InetSocketAddress;
import java.util.Collections;
import java.util.List;
import javax.annotation.Nullable;
import javax.net.ssl.SNIHostName;
import javax.net.ssl.SSLEngine;
import javax.net.ssl.SSLParameters;

import static io.netty.handler.ssl.ApplicationProtocolConfig.Protocol.ALPN;
import static io.netty.handler.ssl.ApplicationProtocolConfig.SelectedListenerFailureBehavior.ACCEPT;
import static io.netty.handler.ssl.ApplicationProtocolConfig.SelectorFailureBehavior.NO_ADVERTISE;
import static io.netty.handler.ssl.SslProvider.isAlpnSupported;

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
     * @param hostnameVerificationAlgorithm see {@link SSLParameters#setEndpointIdentificationAlgorithm(String)}.
     * If this is {@code null} or empty then you will be vulnerable to a MITM attack.
     * @param hostnameVerificationHost the non-authoritative name of the host.
     * @param hostnameVerificationPort the non-authoritative port.
     * @return a {@link SslHandler}
     */
    static SslHandler newHandler(SslContext context, ByteBufAllocator allocator,
                                 @Nullable String hostnameVerificationAlgorithm,
                                 @Nullable String hostnameVerificationHost,
                                 int hostnameVerificationPort) {
        if (hostnameVerificationHost == null) {
            return newHandler(context, allocator);
        }

        SslHandler handler = context.newHandler(allocator, hostnameVerificationHost, hostnameVerificationPort);
        SSLEngine engine = handler.engine();
        try {
            SSLParameters parameters = engine.getSSLParameters();
            parameters.setEndpointIdentificationAlgorithm(hostnameVerificationAlgorithm);
            if (!NetUtil.isValidIpV4Address(hostnameVerificationHost) &&
                    !NetUtil.isValidIpV6Address(hostnameVerificationHost)) {
                // SNI doesn't permit IP addresses!
                // https://tools.ietf.org/html/rfc6066#section-3
                // Literal IPv4 and IPv6 addresses are not permitted in "HostName".
                parameters.setServerNames(Collections.singletonList(new SNIHostName(hostnameVerificationHost)));
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
    static ApplicationProtocolConfig nettyApplicationProtocol(List<String> supportedAlpnProtocols) {
        if (supportedAlpnProtocols.isEmpty()) {
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
    static SslProvider toNettySslProvider(SecurityConfigurator.SslProvider provider, boolean alpn) {
        switch (provider) {
            case AUTO:
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

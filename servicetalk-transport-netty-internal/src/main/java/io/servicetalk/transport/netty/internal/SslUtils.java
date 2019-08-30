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
import io.netty.handler.ssl.SslContext;
import io.netty.handler.ssl.SslHandler;
import io.netty.handler.ssl.SslProvider;
import io.netty.util.NetUtil;
import io.netty.util.ReferenceCountUtil;
import io.netty.util.concurrent.ImmediateExecutor;

import java.net.InetSocketAddress;
import java.util.Collections;
import javax.annotation.Nullable;
import javax.net.ssl.SNIHostName;
import javax.net.ssl.SSLEngine;
import javax.net.ssl.SSLParameters;

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

        SslHandler handler = context.newHandler(allocator, hostnameVerificationHost, hostnameVerificationPort,
                ImmediateExecutor.INSTANCE);
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
        return context.newHandler(allocator, ImmediateExecutor.INSTANCE);
    }

    /**
     * Convert to netty type.
     *
     * @param config the config to convert.
     * @return the new config.
     */
    static ApplicationProtocolConfig toNettyApplicationProtocol(ReadOnlySecurityConfig config) {
        SecurityConfigurator.ApplicationProtocolNegotiation apn = config.applicationProtocolNegotiation();
        if (apn == null) {
            return ApplicationProtocolConfig.DISABLED;
        }

        final ApplicationProtocolConfig.Protocol protocol;
        switch (apn) {
            case ALPN:
                protocol = ApplicationProtocolConfig.Protocol.ALPN;
                break;
            case NONE:
                protocol = ApplicationProtocolConfig.Protocol.NONE;
                break;
            case NPN:
                protocol = ApplicationProtocolConfig.Protocol.NPN;
                break;
            case NPN_AND_ALPN:
                protocol = ApplicationProtocolConfig.Protocol.NPN_AND_ALPN;
                break;
            default:
                throw new Error();
        }

        final ApplicationProtocolConfig.SelectedListenerFailureBehavior selectedListenerFailureBehavior;
        SecurityConfigurator.SelectedListenerFailureBehavior stSelectedBehavior = config.selectedBehavior();
        assert stSelectedBehavior != null;
        switch (stSelectedBehavior) {
            case ACCEPT:
                selectedListenerFailureBehavior = ApplicationProtocolConfig.SelectedListenerFailureBehavior.ACCEPT;
                break;
            case CHOOSE_MY_LAST_PROTOCOL:
                selectedListenerFailureBehavior =
                        ApplicationProtocolConfig.SelectedListenerFailureBehavior.CHOOSE_MY_LAST_PROTOCOL;
                break;
            case FATAL_ALERT:
                selectedListenerFailureBehavior = ApplicationProtocolConfig.SelectedListenerFailureBehavior.FATAL_ALERT;
                break;
            default:
                throw new Error();
        }

        final ApplicationProtocolConfig.SelectorFailureBehavior selectorFailureBehavior;
        SecurityConfigurator.SelectorFailureBehavior stSelectorBehavior = config.selectorBehavior();
        assert stSelectorBehavior != null;
        switch (stSelectorBehavior) {
            case CHOOSE_MY_LAST_PROTOCOL:
                selectorFailureBehavior = ApplicationProtocolConfig.SelectorFailureBehavior.CHOOSE_MY_LAST_PROTOCOL;
                break;
            case FATAL_ALERT:
                selectorFailureBehavior = ApplicationProtocolConfig.SelectorFailureBehavior.FATAL_ALERT;
                break;
            case NO_ADVERTISE:
                selectorFailureBehavior = ApplicationProtocolConfig.SelectorFailureBehavior.NO_ADVERTISE;
                break;
            default:
                throw new Error();
        }
        return new ApplicationProtocolConfig(protocol, selectorFailureBehavior, selectedListenerFailureBehavior,
                config.supportedProtocols());
    }

    /**
     * Convert to netty type.
     *
     * @param provider the provider to convert.
     * @return the netty provider.
     */
    @Nullable
    static SslProvider toNettySslProvider(SecurityConfigurator.SslProvider provider) {
        switch (provider) {
            case AUTO:
                return null;
            case JDK:
                return SslProvider.JDK;
            case OPENSSL:
                return SslProvider.OPENSSL;
            default:
                throw new Error();
        }
    }
}

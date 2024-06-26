/*
 * Copyright © 2018-2019, 2021, 2023 Apple Inc. and the ServiceTalk project authors
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
import io.servicetalk.transport.api.ConnectionObserver.SecurityHandshakeObserver;
import io.servicetalk.transport.netty.internal.ConnectionObserverInitializer.ConnectionObserverHandler;
import io.servicetalk.transport.netty.internal.NoopTransportObserver.NoopSecurityHandshakeObserver;

import io.netty.channel.Channel;
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
import static io.servicetalk.transport.netty.internal.ChannelCloseUtils.assignConnectionError;
import static io.servicetalk.transport.netty.internal.CopyByteBufHandlerChannelInitializer.POOLED_ALLOCATOR;
import static io.servicetalk.transport.netty.internal.SslContextFactory.HANDSHAKE_TIMEOUT_MILLIS;
import static java.util.Collections.singletonList;

/**
 * Utility for SSL.
 */
final class SslUtils {
    private SslUtils() {
        // Utility class
    }

    /**
     * Creates a new {@link SslHandler} for the client-side which will support SNI if the {@link InetSocketAddress} was
     * created from a hostname. It will use {@link CopyByteBufHandlerChannelInitializer#POOLED_ALLOCATOR} if required.
     *
     * @param context the {@link SslContext} which will be used to create the {@link SslHandler}
     * @param sslConfig used to obtain configuration for the {@link SslHandler}.
     * @param channel the {@link Channel} if there is a need to report to {@link SecurityHandshakeObserver}
     * @return a {@link SslHandler}
     */
    static SslHandler newClientSslHandler(final SslContext context, final ClientSslConfig sslConfig,
                                          final Channel channel) {
        SslHandler handler = context.newHandler(POOLED_ALLOCATOR, sslConfig.peerHost(), sslConfig.peerPort());
        observeHandshakeCompletion(handler, channel);
        setHandshakeTimeout(handler, context);
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
     * Creates a new {@link SslHandler} for the server-side.
     * It will use {@link CopyByteBufHandlerChannelInitializer#POOLED_ALLOCATOR} if required.
     *
     * @param context the {@link SslContext} which will be used to create the {@link SslHandler}
     * @param channel the {@link Channel} if there is a need to report to {@link SecurityHandshakeObserver}
     * @return a {@link SslHandler}
     */
    static SslHandler newServerSslHandler(final SslContext context, final Channel channel) {
        SslHandler handler = context.newHandler(POOLED_ALLOCATOR);
        observeHandshakeCompletion(handler, channel);
        setHandshakeTimeout(handler, context);
        return handler;
    }

    private static void observeHandshakeCompletion(final SslHandler sslHandler, final Channel channel) {
        final ConnectionObserverHandler observerHandler = channel.pipeline().get(ConnectionObserverHandler.class);
        if (observerHandler == null) {
            return;
        }
        sslHandler.handshakeFuture().addListener(f -> {
            SecurityHandshakeObserver handshakeObserver = getHandshakeObserver(observerHandler);
            final Throwable cause = f.cause();
            if (cause == null) {
                handshakeObserver.handshakeComplete(sslHandler.engine().getSession());
            } else {
                assignConnectionError(channel, cause);
                handshakeObserver.handshakeFailed(cause);
            }
        });
    }

    private static SecurityHandshakeObserver getHandshakeObserver(final ConnectionObserverHandler handler) {
        final SecurityHandshakeObserver handshakeObserver = handler.handshakeObserver();
        if (handshakeObserver == null) {
            // Fallback to NOOP variant because any issues with observability should not break users runtime.
            // Correctness of reporting is verified by our tests.
            return NoopSecurityHandshakeObserver.INSTANCE;
        }
        return handshakeObserver;
    }

    private static void setHandshakeTimeout(SslHandler handler, SslContext context) {
        handler.setHandshakeTimeoutMillis(context.attributes().attr(HANDSHAKE_TIMEOUT_MILLIS).get());
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

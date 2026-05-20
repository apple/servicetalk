/*
 * Copyright © 2019-2020 Apple Inc. and the ServiceTalk project authors
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
import io.servicetalk.transport.api.ConnectionObserver;
import io.servicetalk.transport.api.ConnectionObserver.SecurityHandshakeObserver;
import io.servicetalk.transport.api.SslConfig;
import io.servicetalk.transport.api.SslHandshakeTimeoutException;
import io.servicetalk.transport.netty.internal.ConnectionObserverInitializer.ConnectionObserverHandler;
import io.servicetalk.transport.netty.internal.NoopTransportObserver.NoopConnectionObserver;

import io.netty.channel.Channel;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelPipeline;
import io.netty.handler.ssl.SniHandler;
import io.netty.handler.ssl.SslContext;
import io.netty.handler.ssl.SslHandler;
import io.netty.handler.ssl.SslHandshakeCompletionEvent;
import io.netty.util.concurrent.Future;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.channels.ClosedChannelException;
import java.util.Map;
import java.util.function.Consumer;
import javax.annotation.Nullable;
import javax.net.ssl.SSLHandshakeException;
import javax.net.ssl.SSLSession;

import static io.servicetalk.utils.internal.ThrowableUtils.throwException;

/**
 * Utilities for {@link ChannelPipeline} and SSL/TLS.
 */
public final class NettyPipelineSslUtils {

    // Pipeline handler name for the outer (proxy) {@link SslHandler} in a layered-TLS / proxy-CONNECT setup.
    static final String PROXY_SSL_HANDLER_NAME = "proxySsl";

    private static final Logger LOGGER = LoggerFactory.getLogger(NettyPipelineSslUtils.class);

    private NettyPipelineSslUtils() {
        // no instances.
    }

    /**
     * Determine if the {@link ChannelPipeline} is configured for SSL/TLS.
     *
     * @param pipeline The pipeline to check.
     * @return {@code true} if the pipeline is configured to use SSL/TLS.
     *
     * @deprecated not required anymore, will be removed in the future releases, see
     * {@link #extractSslSessionAndReport(SslConfig, ChannelPipeline, ConnectionObserver)} for an alternative approach
     */
    @Deprecated
    public static boolean isSslEnabled(ChannelPipeline pipeline) {  // FIXME: 0.43 - remove deprecated method
        return pipeline.get(SslHandler.class) != null || pipeline.get(SniHandler.class) != null;
    }

    /**
     * Extracts the {@link SSLSession} from the {@link ChannelPipeline} if the handshake is already done
     * and reports the result to {@link SecurityHandshakeObserver} if available. If it's done but failed,
     * it rethrows the result.
     *
     * @param sslConfig {@link SslConfig} if SSL/TLS is expected
     * @param pipeline {@link ChannelPipeline} which contains a handler containing the {@link SSLSession}
     * @param connectionObserver {@link ConnectionObserver} in case the handshake status should be reported
     * @return The {@link SSLSession} or {@code null} if none can be found
     * @throws IllegalStateException if {@link SslHandler} can not be found in the {@link ChannelPipeline}
     * @deprecated Use {@link #extractSslSession(SslConfig, ChannelPipeline)} instead,
     * reporting to {@link SecurityHandshakeObserver} is handled automatically for all {@link SslHandler}s initialized
     * by {@link SslClientChannelInitializer} or {@link SslServerChannelInitializer}
     */
    @Nullable
    @Deprecated // FIXME: 0.43 - remove deprecated method
    public static SSLSession extractSslSessionAndReport(@Nullable final SslConfig sslConfig,
                                                        final ChannelPipeline pipeline,
                                                        final ConnectionObserver connectionObserver) {
        if (sslConfig == null) {
            assert noOriginSslHandlers(pipeline) :
                    "No origin SslConfig configured but origin-stage SSL handler found in the pipeline";
            return null;
        }
        // DeferSslHandler still in pipeline → inner handshake not done. Even if a proxy SslHandler is realized,
        // its session isn't what callers of ConnectionInfo#sslSession() want.
        if (pipeline.get(DeferSslHandler.class) != null) {
            return null;
        }
        final SslHandler sslHandler = innermostSslHandler(pipeline);
        if (sslHandler == null) {
            throw unableToFindSslHandler();
        }
        final Future<Channel> handshakeFuture = sslHandler.handshakeFuture();
        if (handshakeFuture.isDone()) {
            final SecurityHandshakeObserver observer = lookForHandshakeObserver(pipeline,
                    connectionObserver != NoopConnectionObserver.INSTANCE);
            final Throwable cause = wrapIfRetryable(handshakeFuture.cause(), sslConfig instanceof ClientSslConfig);
            if (cause != null) {
                if (observer != null) {
                    observer.handshakeFailed(cause);
                }
                return throwException(cause);
            }
            return getSslSession(sslHandler, observer);
        }
        return null;
    }

    /**
     * Extracts the {@link SSLSession} from the {@link ChannelPipeline} if the handshake is already done. If it's done
     * but failed, it rethrows the result.
     *
     * @param sslConfig {@link SslConfig} if SSL/TLS is expected
     * @param pipeline {@link ChannelPipeline} which contains a handler containing the {@link SSLSession}
     * @return The {@link SSLSession} or {@code null} if none can be found
     * @throws IllegalStateException if {@link SslHandler} can not be found in the {@link ChannelPipeline}
     */
    @Nullable
    public static SSLSession extractSslSession(@Nullable final SslConfig sslConfig,
                                               final ChannelPipeline pipeline) {
        return extractSslSessionAndReport(sslConfig, pipeline, NoopConnectionObserver.INSTANCE);
    }

    /**
     * Extracts the {@link SSLSession} from the {@link ChannelPipeline} if the {@link SslHandshakeCompletionEvent}
     * is successful and reports the result to {@link SecurityHandshakeObserver} if available.
     *
     * @param pipeline the {@link ChannelPipeline} which contains handler containing the {@link SSLSession}.
     * @param sslEvent the event indicating a SSL/TLS handshake completed.
     * @param failureConsumer invoked if a failure is encountered.
     * @param shouldReport {@code true} if the handshake status should be reported to {@link SecurityHandshakeObserver}.
     * @return The {@link SSLSession} or {@code null} if none can be found.
     * @deprecated Use {@link #extractSslSession(ChannelPipeline, SslHandshakeCompletionEvent, Consumer)} instead,
     * reporting to {@link SecurityHandshakeObserver} is handled automatically for all {@link SslHandler}s initialized
     * by {@link SslClientChannelInitializer} or {@link SslServerChannelInitializer}.
     */
    @Nullable
    @Deprecated // FIXME: 0.43 - remove deprecated method
    public static SSLSession extractSslSessionAndReport(ChannelPipeline pipeline,
                                                        SslHandshakeCompletionEvent sslEvent,
                                                        Consumer<Throwable> failureConsumer,
                                                        boolean shouldReport) {
        final SecurityHandshakeObserver observer = lookForHandshakeObserver(pipeline, shouldReport);
        final Throwable cause = wrapIfRetryable(sslEvent.cause(), pipeline.channel().parent() == null);
        if (cause == null) {
            final SslHandler sslHandler = innermostSslHandler(pipeline);
            if (sslHandler != null) {
                return getSslSession(sslHandler, observer);
            } else {
                deliverFailureCause(failureConsumer, unableToFindSslHandler(), observer);
            }
        } else {
            deliverFailureCause(failureConsumer, cause, observer);
        }
        return null;
    }

    @Nullable
    static Throwable wrapIfRetryable(@Nullable Throwable cause, boolean isClient) {
        if (isClient) {
            if (cause instanceof io.netty.handler.ssl.SslHandshakeTimeoutException) {
                return new SslHandshakeTimeoutException((SSLHandshakeException) cause);
            }
            if (cause instanceof ClosedChannelException) {
                final Throwable[] suppressed = cause.getSuppressed();
                if (suppressed.length > 0 && suppressed[0] instanceof SSLHandshakeException) {
                    return new RetryableClosedChannelException((ClosedChannelException) cause);
                }
            }
        }
        return cause;
    }

    /**
     * Extracts the {@link SSLSession} from the {@link ChannelPipeline} if the {@link SslHandshakeCompletionEvent}
     * is successful or propagate the failure to {@code failureConsumer}.
     *
     * @param pipeline the {@link ChannelPipeline} which contains handler containing the {@link SSLSession}.
     * @param sslEvent the event indicating a SSL/TLS handshake completed.
     * @param failureConsumer invoked if a failure is encountered.
     * @return The {@link SSLSession} or {@code null} if none can be found.
     */
    @Nullable
    public static SSLSession extractSslSession(final ChannelPipeline pipeline,
                                               final SslHandshakeCompletionEvent sslEvent,
                                               final Consumer<Throwable> failureConsumer) {
        return extractSslSessionAndReport(pipeline, sslEvent, failureConsumer, false);
    }

    private static boolean noOriginSslHandlers(final ChannelPipeline pipeline) {
        // The proxy SslHandler may be present without an origin SslConfig (TLS-to-proxy + plaintext origin
        // configuration), so allow it by name. Any other SslHandler, DeferSslHandler, or SniHandler is illegal
        // when origin SSL is not configured.
        for (Map.Entry<String, ChannelHandler> entry : pipeline) {
            final ChannelHandler h = entry.getValue();
            if (h instanceof SslHandler) {
                if (!PROXY_SSL_HANDLER_NAME.equals(entry.getKey())) {
                    return false;
                }
            } else if (h instanceof DeferSslHandler || h instanceof SniHandler) {
                return false;
            }
        }
        return true;
    }

    /**
     * Returns the innermost (last in pipeline order) {@link SslHandler}, or {@code null} if none is present.
     * <p>
     * For a single TLS connection this is the same as {@code pipeline.get(SslHandler.class)}. With layered TLS
     * (e.g. an outer proxy TLS stage plus an inner origin TLS stage on the same {@link Channel}) the origin
     * handler is added later and is the one whose {@link SSLSession} represents the application-visible session.
     * Callers that operate on the application-level session (e.g. {@code closeOutbound()} for graceful close,
     * {@link SSLSession} extraction) should use this helper instead of {@code pipeline.get(SslHandler.class)}.
     *
     * @param pipeline the pipeline to inspect for a {@link SslHandler}.
     * @return the innermost (last in pipeline order) {@link SslHandler}, or {@code null} if none is present.
     */
    @Nullable
    public static SslHandler innermostSslHandler(final ChannelPipeline pipeline) {
        SslHandler innermost = null;
        for (Map.Entry<String, ChannelHandler> entry : pipeline) {
            final ChannelHandler h = entry.getValue();
            if (h instanceof SslHandler) {
                innermost = (SslHandler) h;
            }
        }
        return innermost;
    }

    /**
     * Install the outer (proxy) TLS stage of a layered-TLS pipeline. Adds an eager proxy {@link SslHandler}
     * named {@link #PROXY_SSL_HANDLER_NAME} followed by an internal isolator that drops outer-stage
     * {@link SslHandshakeCompletionEvent} and {@link io.netty.handler.ssl.SslCloseCompletionEvent} from
     * reaching handlers downstream (which expect inner-stage events). The proxy stage's handshake is
     * intentionally not reported to the connection-level {@code SecurityHandshakeObserver}.
     *
     * @param channel the channel whose pipeline to install on
     * @param sslContext the {@link SslContext} for the proxy TLS stage
     * @param sslConfig the {@link ClientSslConfig} for the proxy TLS stage
     */
    public static void installProxyTlsStage(final Channel channel, final SslContext sslContext,
                                            final ClientSslConfig sslConfig) {
        final SslHandler sslHandler = SslUtils.newClientSslHandler(sslContext, sslConfig, channel, false);
        channel.pipeline()
                .addLast(PROXY_SSL_HANDLER_NAME, sslHandler)
                .addLast(OuterTlsEventIsolator.HANDLER_NAME, OuterTlsEventIsolator.INSTANCE);
    }

    // FIXME: 0.43 - remove method that won't be used after deprecations removed
    private static SSLSession getSslSession(final SslHandler sslHandler,
                                            @Nullable final SecurityHandshakeObserver observer) {
        final SSLSession session = sslHandler.engine().getSession();
        if (observer != null) {
            observer.handshakeComplete(session);
        }
        return session;
    }

    // FIXME: 0.43 - remove method that won't be used after deprecations removed
    private static void deliverFailureCause(final Consumer<Throwable> failureConsumer, final Throwable cause,
                                            @Nullable final SecurityHandshakeObserver securityObserver) {
        if (securityObserver != null) {
            securityObserver.handshakeFailed(cause);
        }
        failureConsumer.accept(cause);
    }

    // FIXME: 0.43 - remove method that won't be used after deprecations removed
    @Nullable
    private static SecurityHandshakeObserver lookForHandshakeObserver(final ChannelPipeline pipeline,
                                                                      final boolean shouldReport) {
        if (!shouldReport) {
            return null;
        }
        final ConnectionObserverHandler handler = pipeline.get(ConnectionObserverHandler.class);
        if (handler == null) {
            LOGGER.warn("Expected to report the handshake completion event, but unable to find {} in the pipeline.",
                    ConnectionObserverHandler.class);
            return null;
        }
        final SecurityHandshakeObserver handshakeObserver = handler.handshakeObserver();
        if (handshakeObserver == null) {
            LOGGER.warn("Expected to report the handshake completion event, but {} was not initialized.",
                    SecurityHandshakeObserver.class);
            return null;
        }
        return handshakeObserver;
    }

    private static IllegalStateException unableToFindSslHandler() {
        return new IllegalStateException("Unable to find " + SslHandler.class.getName() + " in the pipeline.");
    }
}

/*
 * Copyright © 2021-2026 Apple Inc. and the ServiceTalk project authors
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
package io.servicetalk.http.netty;

import io.servicetalk.http.api.HttpHeadersFactory;

import io.netty.handler.codec.http2.DefaultHttp2Connection;
import io.netty.handler.codec.http2.DefaultHttp2ConnectionDecoder;
import io.netty.handler.codec.http2.DefaultHttp2FrameReader;
import io.netty.handler.codec.http2.DefaultHttp2RemoteFlowController;
import io.netty.handler.codec.http2.Http2Connection;
import io.netty.handler.codec.http2.Http2ConnectionDecoder;
import io.netty.handler.codec.http2.Http2ConnectionEncoder;
import io.netty.handler.codec.http2.Http2FrameCodec;
import io.netty.handler.codec.http2.Http2FrameCodecBuilder;
import io.netty.handler.codec.http2.Http2FrameReader;
import io.netty.handler.codec.http2.Http2InboundFrameLogger;
import io.netty.handler.codec.http2.Http2RemoteFlowController;
import io.netty.handler.codec.http2.Http2Settings;
import io.netty.handler.codec.http2.UniformStreamByteDistributor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.reflect.Constructor;
import javax.annotation.Nullable;

import static io.servicetalk.utils.internal.ThrowableUtils.throwException;
import static java.lang.invoke.MethodType.methodType;

/**
 * Optimized variant of {@link Http2FrameCodecBuilder} that allows us to use {@link UniformStreamByteDistributor}
 * for {@link Http2RemoteFlowController}.
 */
final class OptimizedHttp2FrameCodecBuilder extends Http2FrameCodecBuilder {

    private static final Logger LOGGER = LoggerFactory.getLogger(OptimizedHttp2FrameCodecBuilder.class);

    // FIXME: 0.43 - reconsider system properties for netty-codec-http2
    // These properties are introduced temporarily in case users need to disable or re-configure default values set by
    // Netty. For the next major release we should either remove these properties or promote them to public API.
    // These properties are applicable only for the server-side, client-side does not need this DDoS protection.
    private static final String MAX_CONSECUTIVE_EMPTY_FRAMES_PROPERTY_NAME =
            "io.servicetalk.http.netty.http2.decoderEnforceMaxRstFramesPerWindow.maxConsecutiveEmptyFrames";
    private static final String MAX_RST_FRAMES_PER_WINDOW_PROPERTY_NAME =
            "io.servicetalk.http.netty.http2.decoderEnforceMaxRstFramesPerWindow.maxRstFramesPerWindow";
    private static final String SECONDS_PER_WINDOW_PROPERTY_NAME =
            "io.servicetalk.http.netty.http2.decoderEnforceMaxRstFramesPerWindow.secondsPerWindow";

    // Default values are taken from Netty's AbstractHttp2ConnectionHandlerBuilder
    private static final int MAX_RST_FRAMES_PER_WINDOW = parseProperty(MAX_RST_FRAMES_PER_WINDOW_PROPERTY_NAME,
            parseProperty(MAX_CONSECUTIVE_EMPTY_FRAMES_PROPERTY_NAME, 200));
    private static final int SECONDS_PER_WINDOW = parseProperty(SECONDS_PER_WINDOW_PROPERTY_NAME, 30);

    @Nullable
    private static final MethodHandle FLUSH_PREFACE;

    @Nullable
    private static final MethodHandle DECODER_ENFORCE_MAX_RST_FRAMES_PER_WINDOW;

    @Nullable
    private static final Constructor<? extends Http2ConnectionDecoder> EMPTY_DATA_FRAME_DECODER_CTOR;

    @Nullable
    private static final Constructor<? extends Http2ConnectionDecoder> MAX_RST_FRAME_DECODER_CTOR;

    static {
        final Http2FrameCodecBuilder builder = forServer();

        MethodHandle flushPreface;
        try {
            // Find a new method that exists only in Netty starting from 4.1.78.Final:
            flushPreface = MethodHandles.publicLookup()
                    .findVirtual(Http2FrameCodecBuilder.class, "flushPreface",
                            methodType(Http2FrameCodecBuilder.class, boolean.class));
            // Verify the method is working as expected:
            disableFlushPreface(flushPreface, builder);
        } catch (Throwable cause) {
            LOGGER.debug("Http2FrameCodecBuilder#flushPreface(boolean) is available only starting from " +
                            "Netty 4.1.78.Final. Detected Netty version: {}",
                    Http2FrameCodecBuilder.class.getPackage().getImplementationVersion(), cause);
            flushPreface = null;
        }
        FLUSH_PREFACE = flushPreface;

        MethodHandle decoderEnforceMaxRstFramesPerWindow;
        try {
            // Find a new method that exists only in Netty starting from 4.1.100.Final:
            decoderEnforceMaxRstFramesPerWindow = MethodHandles.publicLookup()
                    .findVirtual(Http2FrameCodecBuilder.class, "decoderEnforceMaxRstFramesPerWindow",
                            methodType(Http2FrameCodecBuilder.class, int.class, int.class));
            // Verify the method is working as expected:
            decoderEnforceMaxRstFramesPerWindow(decoderEnforceMaxRstFramesPerWindow, builder, builder.isServer());
        } catch (Throwable cause) {
            LOGGER.debug("Http2FrameCodecBuilder#decoderEnforceMaxRstFramesPerWindow(int, int) is available only " +
                            "starting from Netty 4.1.100.Final. Detected Netty version: {}",
                    Http2FrameCodecBuilder.class.getPackage().getImplementationVersion(), cause);
            decoderEnforceMaxRstFramesPerWindow = null;
        }
        DECODER_ENFORCE_MAX_RST_FRAMES_PER_WINDOW = decoderEnforceMaxRstFramesPerWindow;

        EMPTY_DATA_FRAME_DECODER_CTOR = resolveDecoratingDecoderCtor(
                "io.netty.handler.codec.http2.Http2EmptyDataFrameConnectionDecoder", int.class);
        MAX_RST_FRAME_DECODER_CTOR = resolveDecoratingDecoderCtor(
                "io.netty.handler.codec.http2.Http2MaxRstFrameDecoder", int.class, int.class);
    }

    private final boolean server;
    private final HttpHeadersFactory headersFactory;
    private final int flowControlQuantum;

    private OptimizedHttp2FrameCodecBuilder(final boolean server,
                                    HttpHeadersFactory headersFactory, int flowControlQuantum) {
        this.server = server;
        this.headersFactory = headersFactory;
        this.flowControlQuantum = flowControlQuantum;
        disableFlushPreface(FLUSH_PREFACE, this);
        decoderEnforceMaxRstFramesPerWindow(DECODER_ENFORCE_MAX_RST_FRAMES_PER_WINDOW, this, server);
    }

    @Override
    public boolean isServer() {
        return server;
    }

    @Override
    public Http2FrameCodec build() {
        final DefaultHttp2Connection connection = new DefaultHttp2Connection(isServer(), maxReservedStreams());
        UniformStreamByteDistributor distributor = new UniformStreamByteDistributor(connection);
        distributor.minAllocationChunk(flowControlQuantum);
        connection.remote().flowController(new DefaultHttp2RemoteFlowController(connection, distributor));
        connection(connection);
        return super.build();
    }

    @Override
    protected Http2FrameCodec build(Http2ConnectionDecoder ignoredDecoder, Http2ConnectionEncoder encoder,
                                    Http2Settings initialSettings) {
        // This is the best way to override the default http2 headers decoder newHeaders() method.
        Http2Connection connection = ignoredDecoder.connection();
        Long maxHeaderListSize = initialSettings.maxHeaderListSize();
        Http2FrameReader frameReader = new DefaultHttp2FrameReader(maxHeaderListSize == null ?
                new ServiceTalkHttp2HeadersDecoder(headersFactory, isValidateHeaders()) :
                new ServiceTalkHttp2HeadersDecoder(headersFactory, isValidateHeaders(), maxHeaderListSize));

        if (frameLogger() != null) {
            frameReader = new Http2InboundFrameLogger(frameReader, frameLogger());
        }

        Http2ConnectionDecoder decoder = new DefaultHttp2ConnectionDecoder(connection, encoder, frameReader,
                promisedRequestVerifier(), isAutoAckSettingsFrame(), isAutoAckPingFrame(), isValidateHeaders());
        decoder = applyDdosDecoders(decoder);

        return super.build(decoder, encoder, initialSettings);
    }

    // Netty wraps the decoder it passes to us (ignoredDecoder) with its DDoS-protection decorators inside
    // AbstractHttp2ConnectionHandlerBuilder#buildFromCodec(...). Because we replace that decoder with our own (to
    // install ServiceTalkHttp2HeadersDecoder), we must re-apply the same decorators here or the protections are
    // silently lost. We mirror Netty's order and configuration: empty-DATA-frame guard first (inner), then
    // RST_STREAM rate-limiting (outer).
    private Http2ConnectionDecoder applyDdosDecoders(Http2ConnectionDecoder decoder) {
        // Matches AbstractHttp2ConnectionHandlerBuilder#buildFromCodec: empty-DATA guard is applied for both client and
        // server whenever the configured count is > 0 (Netty's default is 2; ServiceTalk never disables it).
        final int maxConsecutiveEmptyDataFrames = decoderEnforceMaxConsecutiveEmptyDataFrames();
        if (maxConsecutiveEmptyDataFrames > 0) {
            decoder = newDecoratingDecoder(EMPTY_DATA_FRAME_DECODER_CTOR, decoder, maxConsecutiveEmptyDataFrames);
        }
        // RST_STREAM rate-limiting is server-only; maxRstFramesPerWindow(server)/rstSecondsPerWindow(server) return 0
        // for the client, which disables the guard (matching the values passed to Netty's builder).
        final int maxRst = maxRstFramesPerWindow(server);
        final int rstWindowSeconds = rstSecondsPerWindow(server);
        if (maxRst > 0 && rstWindowSeconds > 0) {
            decoder = newDecoratingDecoder(MAX_RST_FRAME_DECODER_CTOR, decoder, maxRst, rstWindowSeconds);
        }
        return decoder;
    }

    private static Http2ConnectionDecoder newDecoratingDecoder(
            @Nullable final Constructor<? extends Http2ConnectionDecoder> ctor,
            final Http2ConnectionDecoder delegate, final Object... extraArgs) {
        if (ctor == null) {
            // Reflection resolution failed and already logged in the static initializer. Fall back to the undecorated
            // decoder rather than failing the connection; this matches the pre-existing behavior on older Netty.
            return delegate;
        }
        final Object[] args = new Object[extraArgs.length + 1];
        args[0] = delegate;
        System.arraycopy(extraArgs, 0, args, 1, extraArgs.length);
        try {
            return ctor.newInstance(args);
        } catch (Throwable cause) {
            // A constructor that resolved at class-init but fails to instantiate is unexpected: fail the  connection
            // loudly rather than silently degrading a security control.
            throwException(cause);
            return delegate;
        }
    }

    /**
     * Creates a new instance.
     *
     * @param server {@code true} if for server, {@code false} otherwise
     * @param config the HTTP/2 protocol configuration.
     */

    static Http2FrameCodecBuilder newBuilder(final boolean server, H2ProtocolConfig config) {
        // We do not want close to trigger graceful closure (go away), instead when user triggers a graceful
        // close, we do the appropriate go away handling.
        return new OptimizedHttp2FrameCodecBuilder(server, config.headersFactory(), config.flowControlQuantum())
                // We don't want to rely upon Netty to manage the graceful close timeout, because we expect
                // the user to apply their own timeout at the call site.
                .gracefulShutdownTimeoutMillis(-1)
                // We do not want close to trigger graceful closure (go away), instead when user triggers a graceful
                // close, we do the appropriate go away handling.
                .decoupleCloseAndGoAway(true)
                //  For the client, the max concurrent streams is made available via a publisher and may be consumed
                //  asynchronously e.g. when offloading is enabled), so we manually control the SETTINGS ACK frames.
                // For the server, H2ServerParentConnectionContext.ackSettings(...) expects Netty to ack settings frame.
                // While the default value is `true`, set this explicitly to avoid any unexpected changes.
                .autoAckSettingsFrame(server)
                // We ack PING frames in KeepAliveManager#pingReceived.
                .autoAckPingFrame(false)
                // Inherit headers validation setting from the HttpHeadersFactory.
                .validateHeaders(config.headersFactory().validateNames())
                .headerSensitivityDetector(config.headersSensitivityDetector()::test);
    }

    /**
     * We manage flushes at ST level and don't want netty to flush the preface & settings only. Instead, we write
     * headers or entire message and flush them all together. Netty changed the default flush behavior starting from
     * 4.1.78.Final. To avoid a strict dependency on Netty 4.1.78.Final in the classpath, we use {@link MethodHandle} to
     * check if the new method is available or not.
     *
     * @param flushPrefaceMethod {@link MethodHandle} for {@link Http2FrameCodecBuilder#flushPreface(boolean)}
     * @param builderInstance an instance of {@link Http2FrameCodecBuilder} where the flush behavior should be disabled
     * @return {@link Http2FrameCodecBuilder} or {@code null} if {@code flushPrefaceMethod == null}
     * @see <a href="https://github.com/netty/netty/pull/12349">Netty PR#12349</a>
     */
    private static Http2FrameCodecBuilder disableFlushPreface(@Nullable final MethodHandle flushPrefaceMethod,
                                                              final Http2FrameCodecBuilder builderInstance) {
        if (flushPrefaceMethod == null) {
            return builderInstance;
        }
        try {
            // invokeExact requires return type cast to match the type signature
            return (Http2FrameCodecBuilder) flushPrefaceMethod.invokeExact(builderInstance, false);
        } catch (Throwable t) {
            throwException(t);
            return builderInstance;
        }
    }

    // To avoid a strict dependency on Netty 4.1.100.Final in the classpath, we use {@link MethodHandle} to check if
    // the new method is available or not.
    private static Http2FrameCodecBuilder decoderEnforceMaxRstFramesPerWindow(
            @Nullable final MethodHandle methodHandle, final Http2FrameCodecBuilder builderInstance,
            final boolean isServer) {
        if (methodHandle == null) {
            return builderInstance;
        }
        try {
            // invokeExact requires return type cast to match the type signature
            return (Http2FrameCodecBuilder) methodHandle.invokeExact(builderInstance,
                    maxRstFramesPerWindow(isServer), rstSecondsPerWindow(isServer));
        } catch (Throwable t) {
            throwException(t);
            return builderInstance;
        }
    }

    // RST_STREAM rate-limiting is only enabled on the server; the client doesn't need this protection.
    private static int maxRstFramesPerWindow(final boolean isServer) {
        return isServer ? MAX_RST_FRAMES_PER_WINDOW : 0;
    }

    // RST_STREAM rate-limiting is only enabled on the server; the client doesn't need this protection.
    private static int rstSecondsPerWindow(final boolean isServer) {
        return isServer ? SECONDS_PER_WINDOW : 0;
    }

    @Nullable
    @SuppressWarnings("unchecked")
    private static Constructor<? extends Http2ConnectionDecoder> resolveDecoratingDecoderCtor(
            final String className, final Class<?>... extraParamTypes) {
        try {
            final Class<?> clazz = Class.forName(className, false,
                    OptimizedHttp2FrameCodecBuilder.class.getClassLoader());
            final Class<?>[] paramTypes = new Class<?>[extraParamTypes.length + 1];
            paramTypes[0] = Http2ConnectionDecoder.class;
            System.arraycopy(extraParamTypes, 0, paramTypes, 1, extraParamTypes.length);
            final Constructor<?> ctor = clazz.getDeclaredConstructor(paramTypes);
            ctor.setAccessible(true);
            return (Constructor<? extends Http2ConnectionDecoder>) ctor;
        } catch (Throwable cause) {
            LOGGER.warn("Unable to resolve Netty's package-private {} via reflection. HTTP/2 connections will " +
                            "not re-apply this DDoS protection decorator on top of ServiceTalk's custom decoder. " +
                            "Detected Netty version: {}", className,
                    Http2FrameCodecBuilder.class.getPackage().getImplementationVersion(), cause);
            return null;
        }
    }

    private static int parseProperty(final String name, final int defaultValue) {
        final String value = System.getProperty(name);
        final int intValue;
        if (value == null || value.isEmpty()) {
            intValue = defaultValue;
        } else {
            try {
                intValue = Integer.parseInt(value);
                if (intValue < 0) {
                    LOGGER.error("Found invalid value -D{}={} (expected >= 0), using fallback value={}",
                            name, value, defaultValue);
                    return defaultValue;
                }
            } catch (NumberFormatException e) {
                LOGGER.error("Could not parse -D{}={} (expected int >= 0), using fallback value={}",
                        name, value, defaultValue, e);
                return defaultValue;
            }
        }
        LOGGER.debug("-D{}: {}", name, intValue);
        return intValue;
    }
}

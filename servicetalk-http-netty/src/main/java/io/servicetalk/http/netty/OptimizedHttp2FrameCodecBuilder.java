/*
 * Copyright © 2021-2023 Apple Inc. and the ServiceTalk project authors
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

import io.netty.handler.codec.http2.DefaultHttp2Connection;
import io.netty.handler.codec.http2.DefaultHttp2RemoteFlowController;
import io.netty.handler.codec.http2.Http2FrameCodec;
import io.netty.handler.codec.http2.Http2FrameCodecBuilder;
import io.netty.handler.codec.http2.Http2RemoteFlowController;
import io.netty.handler.codec.http2.UniformStreamByteDistributor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
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

    static {
        final Http2FrameCodecBuilder builder = Http2FrameCodecBuilder.forServer();

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
    }

    private final boolean server;
    private final int flowControlQuantum;

    /**
     * Creates a new instance.
     *
     * @param server {@code true} if for server, {@code false} otherwise
     * @param flowControlQuantum a hint on the number of bytes that the flow controller will attempt to give to a
     * stream for each allocation.
     */
    OptimizedHttp2FrameCodecBuilder(final boolean server, final int flowControlQuantum) {
        this.server = server;
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
        final int maxRstFramesPerWindow;
        final int secondsPerWindow;
        if (isServer) {
            maxRstFramesPerWindow = MAX_RST_FRAMES_PER_WINDOW;
            secondsPerWindow = SECONDS_PER_WINDOW;
        } else {
            // Client doesn't need this protection
            maxRstFramesPerWindow = 0;
            secondsPerWindow = 0;
        }
        try {
            // invokeExact requires return type cast to match the type signature
            return (Http2FrameCodecBuilder) methodHandle.invokeExact(builderInstance,
                    maxRstFramesPerWindow, secondsPerWindow);
        } catch (Throwable t) {
            throwException(t);
            return builderInstance;
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
        LOGGER.debug("-D{}={}", name, intValue);
        return intValue;
    }
}

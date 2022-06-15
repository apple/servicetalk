/*
 * Copyright © 2021-2022 Apple Inc. and the ServiceTalk project authors
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

import static io.servicetalk.utils.internal.PlatformDependent.throwException;
import static java.lang.invoke.MethodType.methodType;

/**
 * Optimized variant of {@link Http2FrameCodecBuilder} that allows us to use {@link UniformStreamByteDistributor}
 * for {@link Http2RemoteFlowController}.
 */
final class OptimizedHttp2FrameCodecBuilder extends Http2FrameCodecBuilder {

    private static final Logger LOGGER = LoggerFactory.getLogger(OptimizedHttp2FrameCodecBuilder.class);

    @Nullable
    private static final MethodHandle FLUSH_PREFACE;

    static {
        MethodHandle flushPreface;
        try {
            // Find a new method that exists only in Netty starting from 4.1.78.Final:
            flushPreface = MethodHandles.publicLookup()
                    .findVirtual(Http2FrameCodecBuilder.class, "flushPreface",
                            methodType(Http2FrameCodecBuilder.class, boolean.class));
            // Verify the method is working as expected:
            disableFlushPreface(flushPreface, Http2FrameCodecBuilder.forClient());
        } catch (Throwable cause) {
            LOGGER.debug("Http2FrameCodecBuilder#flushPreface(boolean) is available only starting from " +
                            "Netty 4.1.78.Final. Detected Netty version: {}",
                    Http2FrameCodecBuilder.class.getPackage().getImplementationVersion(), cause);
            flushPreface = null;
        }
        FLUSH_PREFACE = flushPreface;
    }

    private final boolean server;

    /**
     * Creates a new instance.
     *
     * @param server {@code true} if for server, {@code false} otherwise
     */
    OptimizedHttp2FrameCodecBuilder(final boolean server) {
        this.server = server;
        disableFlushPreface(FLUSH_PREFACE, this);
    }

    @Override
    public boolean isServer() {
        return server;
    }

    @Override
    public Http2FrameCodec build() {
        final DefaultHttp2Connection connection = new DefaultHttp2Connection(isServer(), maxReservedStreams());
        connection.remote().flowController(new DefaultHttp2RemoteFlowController(connection,
                new UniformStreamByteDistributor(connection)));
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
    @Nullable
    private static Http2FrameCodecBuilder disableFlushPreface(@Nullable final MethodHandle flushPrefaceMethod,
                                                              final Http2FrameCodecBuilder builderInstance) {
        if (flushPrefaceMethod == null) {
            return null;
        }
        try {
            // invokeExact requires return type cast to match the type signature
            return (Http2FrameCodecBuilder) flushPrefaceMethod.invokeExact(builderInstance, false);
        } catch (Throwable t) {
            throwException(t);
            return null;
        }
    }
}

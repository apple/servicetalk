/*
 * Copyright © 2021 Apple Inc. and the ServiceTalk project authors
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

/**
 * Optimized variant of {@link Http2FrameCodecBuilder} that allows us to use {@link UniformStreamByteDistributor}
 * for {@link Http2RemoteFlowController}.
 */
final class OptimizedHttp2FrameCodecBuilder extends Http2FrameCodecBuilder {

    private final boolean server;

    /**
     * Creates a new instance.
     *
     * @param server {@code true} if for server, {@code false} otherwise
     */
    OptimizedHttp2FrameCodecBuilder(final boolean server) {
        this.server = server;
        // We manage flushes at ST level and don't want netty to flush the preface & settings only. Instead, we write
        // headers or entire message and flush them all together. Netty changed the default flush behavior starting from
        // 4.1.78.Final. For context, see https://github.com/netty/netty/pull/12349.
        flushPreface(false);
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
}

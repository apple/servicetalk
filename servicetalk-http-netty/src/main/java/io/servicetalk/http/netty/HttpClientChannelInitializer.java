/*
 * Copyright © 2018-2020 Apple Inc. and the ServiceTalk project authors
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

import io.servicetalk.http.api.HttpRequestMethod;
import io.servicetalk.http.netty.HttpResponseDecoder.Signal;
import io.servicetalk.transport.netty.internal.ChannelInitializer;
import io.servicetalk.transport.netty.internal.CloseHandler;
import io.servicetalk.transport.netty.internal.CopyByteBufHandlerChannelInitializer;

import io.netty.buffer.ByteBufAllocator;
import io.netty.channel.Channel;
import io.netty.channel.ChannelPipeline;

import java.util.ArrayDeque;
import java.util.Queue;

import static java.lang.Math.min;

final class HttpClientChannelInitializer implements ChannelInitializer {

    private final ChannelInitializer delegate;

    /**
     * Creates a new instance.
     * @param config {@link H1ProtocolConfig}
     * @param closeHandler observes protocol state events
     */
    HttpClientChannelInitializer(final ByteBufAllocator alloc, final H1ProtocolConfig config,
                                 final CloseHandler closeHandler) {
        // H1 slices passed memory chunks into headers and payload body without copying and will emit them to the
        // user-code. Therefore, ByteBufs must be copied to unpooled memory before HttpObjectDecoder.
        this.delegate = new CopyByteBufHandlerChannelInitializer(alloc).andThen(channel -> {
            final int minPipelinedRequests = min(8, config.maxPipelinedRequests());
            final Queue<HttpRequestMethod> methodQueue = new ArrayDeque<>(minPipelinedRequests);
            final ArrayDeque<Signal> signalsQueue = new ArrayDeque<>(minPipelinedRequests);
            final ChannelPipeline pipeline = channel.pipeline();
            pipeline.addLast(new HttpResponseDecoder(methodQueue, signalsQueue, alloc, config.headersFactory(),
                    config.maxStartLineLength(), config.maxHeaderFieldLength(),
                    config.specExceptions().allowPrematureClosureBeforePayloadBody(),
                    config.specExceptions().allowLFWithoutCR(), closeHandler));
            pipeline.addLast(new HttpRequestEncoder(methodQueue, signalsQueue,
                    config.headersEncodedSizeEstimate(), config.trailersEncodedSizeEstimate(), closeHandler));
        });
    }

    @Override
    public void init(final Channel channel) {
        delegate.init(channel);
    }
}

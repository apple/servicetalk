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
package io.servicetalk.http.netty;

import io.servicetalk.http.api.HttpRequestMethod;
import io.servicetalk.transport.netty.internal.ChannelInitializer;
import io.servicetalk.transport.netty.internal.CloseHandler;

import io.netty.channel.Channel;
import io.netty.channel.ChannelPipeline;

import java.util.ArrayDeque;
import java.util.Queue;

import static java.lang.Math.min;

final class HttpClientChannelInitializer implements ChannelInitializer {

    private final H1ProtocolConfig config;
    private final CloseHandler closeHandler;

    /**
     * Creates a new instance.
     * @param config {@link H1ProtocolConfig}
     * @param closeHandler observes protocol state events
     */
    HttpClientChannelInitializer(H1ProtocolConfig config, CloseHandler closeHandler) {
        this.config = config;
        this.closeHandler = closeHandler;
    }

    @Override
    public void init(final Channel channel) {
        Queue<HttpRequestMethod> methodQueue = new ArrayDeque<>(min(8, config.maxPipelinedRequests()));
        final ChannelPipeline pipeline = channel.pipeline();
        pipeline.addLast(new HttpResponseDecoder(methodQueue, config.headersFactory(),
                config.maxStartLineLength(), config.maxHeaderFieldLength(), closeHandler));
        pipeline.addLast(new HttpRequestEncoder(methodQueue,
                config.headersEncodedSizeEstimate(), config.trailersEncodedSizeEstimate(), closeHandler));
    }
}

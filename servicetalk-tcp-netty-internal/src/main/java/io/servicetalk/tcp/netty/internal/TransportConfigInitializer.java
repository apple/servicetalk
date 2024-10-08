/*
 * Copyright © 2024 Apple Inc. and the ServiceTalk project authors
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
package io.servicetalk.tcp.netty.internal;

import io.servicetalk.transport.api.TransportConfig;
import io.servicetalk.transport.netty.internal.ChannelInitializer;

import io.netty.channel.AdaptiveRecvByteBufAllocator;
import io.netty.channel.Channel;

import static java.lang.Math.min;

final class TransportConfigInitializer implements ChannelInitializer {

    private final TransportConfig config;

    TransportConfigInitializer(final TransportConfig config) {
        this.config = config;
    }

    @Override
    public void init(final Channel channel) {
        channel.config().setRecvByteBufAllocator(new AdaptiveRecvByteBufAllocator(
                min(512, config.maxBytesPerRead()), min(32_768, config.maxBytesPerRead()), config.maxBytesPerRead())
                .respectMaybeMoreData(false)
                .maxMessagesPerRead(config.maxReadAttemptsPerSelect()));
    }
}

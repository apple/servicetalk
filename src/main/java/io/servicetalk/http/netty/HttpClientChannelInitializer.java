/*
 * Copyright © 2018 Apple Inc. and the ServiceTalk project authors
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

import io.servicetalk.transport.api.ConnectionContext;
import io.servicetalk.transport.netty.internal.ChannelInitializer;

import io.netty.channel.Channel;
import io.netty.channel.ChannelPipeline;

final class HttpClientChannelInitializer implements ChannelInitializer {

    private final ReadOnlyHttpClientConfig roConfig;

    /**
     * Creates a new instance.
     * @param roConfig read-only {@link HttpClientConfig}
     */
    HttpClientChannelInitializer(ReadOnlyHttpClientConfig roConfig) {
        this.roConfig = roConfig;
    }

    @Override
    public ConnectionContext init(final Channel channel, final ConnectionContext ctx) {
        final ChannelPipeline pipeline = channel.pipeline();
        pipeline.addLast(new HttpResponseDecoder(roConfig.getHeadersFactory(), roConfig.getMaxInitialLineLength(),
                roConfig.getMaxHeaderSize(), roConfig.getMaxChunkSize(), true));
        pipeline.addLast(new HttpRequestEncoder(
                roConfig.getHeadersEncodedSizeEstimate(), roConfig.getTrailersEncodedSizeEstimate()));
        return ctx;
    }
}

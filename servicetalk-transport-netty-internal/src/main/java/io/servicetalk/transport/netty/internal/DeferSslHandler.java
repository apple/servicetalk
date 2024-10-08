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

import io.servicetalk.transport.api.SslConfig;
import io.servicetalk.transport.netty.internal.ConnectionObserverInitializer.ConnectionObserverHandler;

import io.netty.channel.Channel;
import io.netty.channel.ChannelDuplexHandler;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelPipeline;
import io.netty.handler.ssl.SslHandler;

/**
 * A {@link ChannelHandler} that holds a place in a pipeline, allowing us to defer adding the {@link SslHandler}.
 */
public class DeferSslHandler extends ChannelDuplexHandler {
    private final Channel channel;
    private final SslHandler handler;
    private final SslConfig sslConfig;

    DeferSslHandler(final Channel channel, final SslHandler handler, final SslConfig sslConfig) {
        this.channel = channel;
        this.handler = handler;
        this.sslConfig = sslConfig;
    }

    /**
     * Indicates that we are ready to stop deferring, and add the deferred {@link SslHandler}.
     */
    public void ready() {
        final ChannelPipeline pipeline = channel.pipeline();
        final ConnectionObserverHandler observerHandler = pipeline.get(ConnectionObserverHandler.class);
        if (observerHandler != null) {
            observerHandler.reportSecurityHandshakeStarting(sslConfig);
        }
        pipeline.replace(this, null, handler);
    }
}

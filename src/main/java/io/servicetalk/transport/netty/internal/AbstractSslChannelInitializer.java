/**
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
package io.servicetalk.transport.netty.internal;

import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.ChannelPipeline;
import io.netty.handler.ssl.SslHandler;
import io.netty.handler.ssl.SslHandshakeCompletionEvent;
import io.servicetalk.transport.api.ConnectionContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;

public abstract class AbstractSslChannelInitializer implements ChannelInitializer {

    private static final Logger LOGGER = LoggerFactory.getLogger(SslServerChannelInitializer.class);

    @Override
    public ConnectionContext init(Channel channel, ConnectionContext context) {
        final NettyConnectionContext nettyServiceContext = (NettyConnectionContext) context;
        ChannelPipeline pipeline = channel.pipeline();
        @Nullable SslHandler sslHandler = addNettySslHandler(channel, context);
        pipeline.addLast(new SslSessionExtractor(nettyServiceContext, sslHandler));
        return nettyServiceContext;
    }

    @Nullable
    protected abstract SslHandler addNettySslHandler(Channel channel, ConnectionContext context);

    private static class SslSessionExtractor extends ChannelInboundHandlerAdapter {

        private final NettyConnectionContext nettyServiceContext;
        @Nullable
        private final SslHandler sslHandler;

        SslSessionExtractor(NettyConnectionContext nettyServiceContext, @Nullable SslHandler sslHandler) {
            this.nettyServiceContext = nettyServiceContext;
            this.sslHandler = sslHandler;
        }

        @Override
        public void userEventTriggered(ChannelHandlerContext ctx, Object evt) {
            if (evt instanceof SslHandshakeCompletionEvent) {
                final ChannelPipeline pipeline = ctx.pipeline();
                pipeline.remove(this);
                SslHandshakeCompletionEvent handshakeCompletionEvent = (SslHandshakeCompletionEvent) evt;
                if (handshakeCompletionEvent.isSuccess()) {
                    if (sslHandler != null) {
                        nettyServiceContext.setSslSession(sslHandler.engine().getSession());
                    } else {
                        final SslHandler sslHandlerFromSni = pipeline.get(SslHandler.class);
                        if (sslHandlerFromSni == null) {
                            LOGGER.error("Unable to find " + SslHandler.class.getName() + " in the pipeline.");
                        } else {
                            nettyServiceContext.setSslSession(sslHandlerFromSni.engine().getSession());
                        }
                    }
                }
            }
            ctx.fireUserEventTriggered(evt);
        }
    }
}

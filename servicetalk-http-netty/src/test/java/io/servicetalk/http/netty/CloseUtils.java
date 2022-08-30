/*
 * Copyright © 2022 Apple Inc. and the ServiceTalk project authors
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
import io.servicetalk.transport.netty.internal.CloseHandler.CloseEventObservedException;
import io.servicetalk.transport.netty.internal.NettyConnectionContext;

import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelOutboundHandlerAdapter;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.ChannelPromise;
import io.netty.handler.codec.http2.Http2GoAwayFrame;

import java.util.concurrent.CountDownLatch;

import static io.servicetalk.http.api.HttpProtocolVersion.HTTP_1_1;
import static io.servicetalk.http.api.HttpProtocolVersion.HTTP_2_0;
import static io.servicetalk.transport.netty.internal.CloseHandler.CloseEvent.GRACEFUL_USER_CLOSING;

final class CloseUtils {

    private CloseUtils() {
        // No instances
    }

    /**
     * A utility that helps intercept when a graceful closure begins its closing sequence on the event loop.
     *
     * @param cc {@link ConnectionContext} to monitor
     * @param closingStarted a {@link CountDownLatch} to notify
     */
    static void onGracefulClosureStarted(ConnectionContext cc, CountDownLatch closingStarted) {
        NettyConnectionContext nettyCtx = (NettyConnectionContext) cc;
        if (cc.protocol() == HTTP_1_1) {
            nettyCtx.transportError().subscribe(t -> {
                if (t instanceof CloseEventObservedException &&
                        ((CloseEventObservedException) t).event() == GRACEFUL_USER_CLOSING) {
                    assert nettyCtx.nettyChannel().eventLoop().inEventLoop();
                    closingStarted.countDown();
                }
            });
        } else if (cc.protocol() == HTTP_2_0) {
            ChannelPipeline pipeline = nettyCtx.nettyChannel().pipeline();
            pipeline.addLast(new ChannelOutboundHandlerAdapter() {
                @Override
                public void write(ChannelHandlerContext ctx, Object msg, ChannelPromise promise) {
                    if (msg instanceof Http2GoAwayFrame) {
                        assert nettyCtx.nettyChannel().eventLoop().inEventLoop();
                        closingStarted.countDown();
                    }
                    ctx.write(msg, promise);
                }
            });
        } else {
            throw new IllegalArgumentException("Unexpected protocol: " + cc.protocol());
        }
    }
}

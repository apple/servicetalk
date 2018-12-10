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
package io.servicetalk.tcp.netty.internal;

import io.servicetalk.concurrent.api.Executor;
import io.servicetalk.transport.api.ConnectionAcceptor;
import io.servicetalk.transport.api.ConnectionContext;

import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.ssl.SniHandler;
import io.netty.handler.ssl.SslHandler;
import io.netty.handler.ssl.SslHandshakeCompletionEvent;

import static io.netty.handler.ssl.SslHandshakeCompletionEvent.SUCCESS;

/**
 * This class depends on the {@link SslHandler} or {@link SniHandler} having been added to the pipeline before it, so
 * that it can intercept the SSL handshake completion {@link SslHandshakeCompletionEvent#SUCCESS} event, in order to
 * execute the {@link ConnectionAcceptor}.
 */
final class SslContextFilterChannelHandler extends AbstractContextFilterChannelHandler {

    SslContextFilterChannelHandler(final ConnectionContext context, final ConnectionAcceptor connectionAcceptor,
                                   final Executor executor) {
        super(context, connectionAcceptor, executor);
    }

    @Override
    public void channelActive(final ChannelHandlerContext ctx) {
        ctx.fireChannelActive();

        // Since we are delaying Connection propagation with ConnectionAcceptor until SSL handshake is completed, user code
        // will not generate a read. We need to generate a read() to initiate the handshake.
        ctx.read();
    }

    @Override
    public void userEventTriggered(final ChannelHandlerContext ctx, final Object evt) {
        if (evt == SUCCESS) {
            executeContextFilter(ctx);
        } else {
            ctx.fireUserEventTriggered(evt);
        }
    }

    @Override
    void onContextFilterSuccessful(final ChannelHandlerContext ctx) {
        ctx.fireUserEventTriggered(SUCCESS);
    }
}

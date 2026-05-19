/*
 * Copyright © 2026 Apple Inc. and the ServiceTalk project authors
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

import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.handler.ssl.SslCloseCompletionEvent;
import io.netty.handler.ssl.SslHandshakeCompletionEvent;

/**
 * Inbound handler installed immediately after the outer (proxy) {@link io.netty.handler.ssl.SslHandler} in a
 * layered-TLS pipeline. Drops outer-stage {@link SslHandshakeCompletionEvent} and {@link SslCloseCompletionEvent}
 * so they never reach handlers downstream of the proxy stage. Without this, the outer events reach handlers
 * intended to react to inner-stage events — e.g. {@code AlpnChannelHandler} or {@code RequestResponseCloseHandler}.
 * <p>
 * On a failed outer-handshake event the cause is attached to the channel via
 * {@link ChannelCloseUtils#assignConnectionError} before the event is dropped, so the original {@code SSLException}
 * surfaces through {@code observer.connectionClosed} and downstream subscriber failures.
 * <p>
 * Stateless and {@link Sharable}; install via {@link #INSTANCE}. Only user events are isolated here;
 * {@code exceptionCaught} from the outer stage propagates so failures still tear the channel down through the
 * standard error path.
 */
final class OuterTlsEventIsolator extends ChannelInboundHandlerAdapter {

    static final String HANDLER_NAME = "outerTlsEventIsolator";

    static final OuterTlsEventIsolator INSTANCE = new OuterTlsEventIsolator();

    private OuterTlsEventIsolator() {
    }

    @Override
    public void userEventTriggered(final ChannelHandlerContext ctx, final Object evt) throws Exception {
        if (evt instanceof SslHandshakeCompletionEvent) {
            // On failure, attach the cause to the channel so observer.connectionClosed and downstream subscriber
            // failures can surface the original SSLException instead of a generic "connection closed" error.
            final SslHandshakeCompletionEvent e = (SslHandshakeCompletionEvent) evt;
            if (!e.isSuccess()) {
                ChannelCloseUtils.assignConnectionError(ctx.channel(), e.cause());
            }
            return;
        }
        if (evt instanceof SslCloseCompletionEvent) {
            return;
        }
        ctx.fireUserEventTriggered(evt);
    }

    @Override
    public boolean isSharable() {
        return true;
    }
}

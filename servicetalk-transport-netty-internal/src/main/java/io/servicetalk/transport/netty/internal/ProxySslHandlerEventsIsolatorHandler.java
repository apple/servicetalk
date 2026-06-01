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
 * Inbound handler installed immediately after the proxy {@link io.netty.handler.ssl.SslHandler} in a
 * layered-TLS pipeline. Drops proxy-stage {@link SslHandshakeCompletionEvent} and {@link SslCloseCompletionEvent}
 * so they never reach handlers downstream of the proxy stage. Without this, the proxy events reach handlers
 * intended to react to origin-stage events — e.g. {@code AlpnChannelHandler} or {@code RequestResponseCloseHandler}.
 * <p>
 * On a failed proxy-handshake event the cause is attached to the channel via
 * {@link ChannelCloseUtils#assignConnectionError} before the event is dropped, so the original {@code SSLException}
 * surfaces through {@code observer.connectionClosed} and downstream subscriber failures.
 * <p>
 * Stateless; install via {@link #INSTANCE}. Only user events are isolated here; {@code exceptionCaught} from the
 * proxy stage propagates so failures still tear the channel down through the standard error path.
 */
final class ProxySslHandlerEventsIsolatorHandler extends ChannelInboundHandlerAdapter {

    static final String HANDLER_NAME = "proxySslHandlerEventsIsolatorHandler";

    static final ProxySslHandlerEventsIsolatorHandler INSTANCE = new ProxySslHandlerEventsIsolatorHandler();

    private ProxySslHandlerEventsIsolatorHandler() {
    }

    @Override
    public void userEventTriggered(final ChannelHandlerContext ctx, final Object evt) throws Exception {
        if (evt instanceof SslHandshakeCompletionEvent) {
            // On failure, attach the cause so observer.connectionClosed and downstream subscribers see the real
            // SSLException instead of a generic "connection closed". SslHandler closes the channel on failure
            // itself; we don't need to.
            final SslHandshakeCompletionEvent e = (SslHandshakeCompletionEvent) evt;
            if (!e.isSuccess()) {
                ChannelCloseUtils.assignConnectionError(ctx.channel(), e.cause());
            }
            return;
        }
        if (evt instanceof SslCloseCompletionEvent) {
            // Proxy-stage close_notify is invariably followed by the proxy server closing TCP, which drives
            // channelInactive. Nothing to do here.
            return;
        }
        ctx.fireUserEventTriggered(evt);
    }

    @Override
    public boolean isSharable() {
        return true;
    }
}

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

import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.handler.codec.DecoderException;
import io.netty.handler.ssl.SslCloseCompletionEvent;
import io.netty.handler.ssl.SslContext;
import io.netty.handler.ssl.SslContextBuilder;
import io.netty.handler.ssl.SslHandler;
import io.netty.handler.ssl.SslHandshakeCompletionEvent;
import io.netty.handler.ssl.util.InsecureTrustManagerFactory;
import io.netty.handler.timeout.IdleStateEvent;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.jupiter.api.Assertions.assertThrows;

final class ProxySslHandlerEventsIsolatorHandlerTest {

    @Test
    void dropsSslHandshakeCompletionEvents() {
        final List<Object> received = new ArrayList<>();
        final EmbeddedChannel ch = new EmbeddedChannel(
                ProxySslHandlerEventsIsolatorHandler.INSTANCE, capture(received));
        try {
            ch.pipeline().fireUserEventTriggered(SslHandshakeCompletionEvent.SUCCESS);
            ch.pipeline().fireUserEventTriggered(new SslHandshakeCompletionEvent(new RuntimeException("boom")));
            assertThat(received, is(empty()));
        } finally {
            ch.close();
        }
    }

    @Test
    void dropsSslCloseCompletionEvents() {
        final List<Object> received = new ArrayList<>();
        final EmbeddedChannel ch = new EmbeddedChannel(
                ProxySslHandlerEventsIsolatorHandler.INSTANCE, capture(received));
        try {
            ch.pipeline().fireUserEventTriggered(SslCloseCompletionEvent.SUCCESS);
            assertThat(received, is(empty()));
        } finally {
            ch.close();
        }
    }

    @Test
    void otherUserEventsPassThrough() {
        final List<Object> received = new ArrayList<>();
        final EmbeddedChannel ch = new EmbeddedChannel(
                ProxySslHandlerEventsIsolatorHandler.INSTANCE, capture(received));
        try {
            ch.pipeline().fireUserEventTriggered(IdleStateEvent.ALL_IDLE_STATE_EVENT);
            final Object marker = new Object();
            ch.pipeline().fireUserEventTriggered(marker);
            assertThat(received, contains(IdleStateEvent.ALL_IDLE_STATE_EVENT, marker));
        } finally {
            ch.close();
        }
    }

    @Test
    void failedHandshakeAttachesCauseToChannel() {
        final EmbeddedChannel ch = new EmbeddedChannel(ProxySslHandlerEventsIsolatorHandler.INSTANCE);
        try {
            final RuntimeException boom = new RuntimeException("handshake failed");
            ch.pipeline().fireUserEventTriggered(new SslHandshakeCompletionEvent(boom));
            assertThat(ChannelCloseUtils.channelError(ch), is(boom));
        } finally {
            ch.close();
        }
    }

    @Test
    void successfulHandshakeDoesNotAttachCause() {
        final EmbeddedChannel ch = new EmbeddedChannel(ProxySslHandlerEventsIsolatorHandler.INSTANCE);
        try {
            ch.pipeline().fireUserEventTriggered(SslHandshakeCompletionEvent.SUCCESS);
            assertThat(ChannelCloseUtils.channelError(ch), is(nullValue()));
        } finally {
            ch.close();
        }
    }

    @Test
    void channelClosesOnHandshakeFailureEvenThoughIsolatorDropsTheEvent() throws Exception {
        // SslHandler closes the channel itself on handshake failure, so dropping the failed event in the isolator
        // doesn't strand the channel.
        final SslContext sslCtx = SslContextBuilder.forClient()
                .trustManager(InsecureTrustManagerFactory.INSTANCE)
                .build();
        final EmbeddedChannel ch = new EmbeddedChannel();
        final SslHandler sslHandler = sslCtx.newHandler(ch.alloc(), "host", 443);
        ch.pipeline().addLast(sslHandler);
        ch.pipeline().addLast(ProxySslHandlerEventsIsolatorHandler.INSTANCE);
        try {
            while (ch.readOutbound() != null) {
                // drain the ClientHello
            }
            assertThrows(DecoderException.class, () ->
                    ch.writeInbound(Unpooled.wrappedBuffer(new byte[]{0, 0, 0, 0, 0, 0, 0, 0})));
            ch.runPendingTasks();
            assertThat(ch.isOpen(), is(false));
        } finally {
            if (ch.isOpen()) {
                ch.close();
            }
        }
    }

    private static ChannelInboundHandlerAdapter capture(final List<Object> sink) {
        return new ChannelInboundHandlerAdapter() {
            @Override
            public void userEventTriggered(final ChannelHandlerContext ctx, final Object evt) {
                sink.add(evt);
            }
        };
    }
}

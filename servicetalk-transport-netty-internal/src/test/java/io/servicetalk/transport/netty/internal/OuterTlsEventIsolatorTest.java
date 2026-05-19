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
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.handler.ssl.SslCloseCompletionEvent;
import io.netty.handler.ssl.SslHandshakeCompletionEvent;
import io.netty.handler.timeout.IdleStateEvent;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;

final class OuterTlsEventIsolatorTest {

    @Test
    void dropsSslHandshakeCompletionEvents() {
        final List<Object> received = new ArrayList<>();
        final EmbeddedChannel ch = new EmbeddedChannel(OuterTlsEventIsolator.INSTANCE, capture(received));
        ch.pipeline().fireUserEventTriggered(SslHandshakeCompletionEvent.SUCCESS);
        ch.pipeline().fireUserEventTriggered(new SslHandshakeCompletionEvent(new RuntimeException("boom")));
        assertThat(received, is(empty()));
    }

    @Test
    void dropsSslCloseCompletionEvents() {
        final List<Object> received = new ArrayList<>();
        final EmbeddedChannel ch = new EmbeddedChannel(OuterTlsEventIsolator.INSTANCE, capture(received));
        ch.pipeline().fireUserEventTriggered(SslCloseCompletionEvent.SUCCESS);
        assertThat(received, is(empty()));
    }

    @Test
    void otherUserEventsPassThrough() {
        // Anything that isn't an SSL-stage event must propagate. IdleStateEvent is a representative user event
        // the channel would normally fire and downstream handlers would consume.
        final List<Object> received = new ArrayList<>();
        final EmbeddedChannel ch = new EmbeddedChannel(OuterTlsEventIsolator.INSTANCE, capture(received));

        ch.pipeline().fireUserEventTriggered(IdleStateEvent.ALL_IDLE_STATE_EVENT);
        final Object marker = new Object();
        ch.pipeline().fireUserEventTriggered(marker);
        assertThat(received, contains(IdleStateEvent.ALL_IDLE_STATE_EVENT, marker));
    }

    @Test
    void failedHandshakeAttachesCauseToChannel() {
        // Failure-cause bridging: outer-stage handshake failure should attach the SSLException to the channel's
        // error attribute so observer.connectionClosed and downstream subscriber failures can surface the real
        // cause rather than a generic "connection closed" message.
        final EmbeddedChannel ch = new EmbeddedChannel(OuterTlsEventIsolator.INSTANCE);
        final RuntimeException boom = new RuntimeException("handshake failed");
        ch.pipeline().fireUserEventTriggered(new SslHandshakeCompletionEvent(boom));
        assertThat(ChannelCloseUtils.channelError(ch), is(boom));
    }

    @Test
    void successfulHandshakeDoesNotAttachCause() {
        final EmbeddedChannel ch = new EmbeddedChannel(OuterTlsEventIsolator.INSTANCE);
        ch.pipeline().fireUserEventTriggered(SslHandshakeCompletionEvent.SUCCESS);
        assertThat(ChannelCloseUtils.channelError(ch), is(nullValue()));
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

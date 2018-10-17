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
package io.servicetalk.transport.netty.internal;

import io.servicetalk.concurrent.api.MockedSubscriberRule;
import io.servicetalk.concurrent.api.Publisher;
import io.servicetalk.concurrent.internal.ServiceTalkTestTimeout;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.embedded.EmbeddedChannel;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.Timeout;

import java.util.concurrent.TimeUnit;

import static io.servicetalk.concurrent.internal.ServiceTalkTestTimeout.DEFAULT_TIMEOUT_SECONDS;
import static io.servicetalk.transport.netty.internal.CloseHandler.UNSUPPORTED_PROTOCOL_CLOSE_HANDLER;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

public class NettyChannelPublisherRefCountTest {
    @Rule
    public final Timeout timeout = new ServiceTalkTestTimeout();
    @Rule
    public final MockedSubscriberRule<Object> subscriber = new MockedSubscriberRule<>();

    private Publisher<Object> publisher;
    private EmbeddedChannel channel;
    private AbstractChannelReadHandler<Object> handler;
    private ChannelHandlerContext handlerCtx;

    @Before
    public void setUp() {
        handler = new AbstractChannelReadHandler<Object>(integer -> true, UNSUPPORTED_PROTOCOL_CLOSE_HANDLER) {
            @Override
            protected void onPublisherCreation(ChannelHandlerContext ctx, Publisher<Object> newPublisher) {
                publisher = newPublisher;
            }
        };
        channel = new EmbeddedChannel(handler);
        handlerCtx = channel.pipeline().context(handler);
    }

    @After
    public void tearDown() throws Exception {
        if (!channel.close().await(DEFAULT_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
            throw new IllegalStateException("Channel close not finished in 1 second.");
        }
    }

    @Test
    public void testRefCountedLeaked() {
        subscriber.subscribe(publisher).request(3);
        ByteBuf buffer = handlerCtx.alloc().buffer();
        handler.channelRead(handlerCtx, buffer);
        subscriber.verifyFailure(IllegalStateException.class);
        assertThat("Buffer not released.", buffer.refCnt(), is(0));
        assertThat("Channel not closed post ref count leaked.", channel.closeFuture().isDone(), is(true));
    }
}

/*
 * Copyright © 2018-2019 Apple Inc. and the ServiceTalk project authors
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

import io.servicetalk.concurrent.api.Publisher;
import io.servicetalk.concurrent.api.TestPublisherSubscriber;
import io.servicetalk.concurrent.internal.ServiceTalkTestTimeout;
import io.servicetalk.transport.api.ConnectionInfo.Protocol;
import io.servicetalk.transport.netty.internal.NoopTransportObserver.NoopConnectionObserver;

import io.netty.buffer.ByteBuf;
import io.netty.channel.embedded.EmbeddedChannel;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.Timeout;

import java.util.concurrent.TimeUnit;

import static io.servicetalk.buffer.netty.BufferAllocators.DEFAULT_ALLOCATOR;
import static io.servicetalk.concurrent.api.Executors.immediate;
import static io.servicetalk.concurrent.api.SourceAdapters.toSource;
import static io.servicetalk.concurrent.internal.ServiceTalkTestTimeout.DEFAULT_TIMEOUT_SECONDS;
import static io.servicetalk.transport.netty.internal.CloseHandler.UNSUPPORTED_PROTOCOL_CLOSE_HANDLER;
import static io.servicetalk.transport.netty.internal.FlushStrategies.defaultFlushStrategy;
import static io.servicetalk.transport.netty.internal.OffloadAllExecutionStrategy.OFFLOAD_ALL_STRATEGY;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.instanceOf;
import static org.hamcrest.Matchers.is;
import static org.mockito.Mockito.mock;

public class NettyChannelPublisherRefCountTest {
    @Rule
    public final Timeout timeout = new ServiceTalkTestTimeout();

    private final TestPublisherSubscriber<Object> subscriber = new TestPublisherSubscriber<>();
    private Publisher<Object> publisher;
    private EmbeddedChannel channel;

    @Before
    public void setUp() throws Exception {
        channel = new EmbeddedChannel();
        publisher = DefaultNettyConnection.initChannel(channel, DEFAULT_ALLOCATOR, immediate(), x -> false,
                UNSUPPORTED_PROTOCOL_CLOSE_HANDLER, defaultFlushStrategy(), null, channel2 -> { },
                OFFLOAD_ALL_STRATEGY, mock(Protocol.class), NoopConnectionObserver.INSTANCE).toFuture().get().read();
    }

    @After
    public void tearDown() throws Exception {
        if (!channel.close().await(DEFAULT_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
            throw new IllegalStateException("Channel close not finished in 1 second.");
        }
    }

    @Test
    public void testRefCountedLeaked() {
        toSource(publisher).subscribe(subscriber);
        subscriber.request(3);
        ByteBuf buffer = channel.alloc().buffer();
        channel.writeInbound(buffer);
        assertThat(subscriber.takeError(), instanceOf(IllegalArgumentException.class));
        assertThat("Buffer not released.", buffer.refCnt(), is(0));
        assertThat("Channel not closed post ref count leaked.", channel.closeFuture().isDone(), is(true));
    }
}

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
import io.servicetalk.concurrent.test.internal.TestPublisherSubscriber;
import io.servicetalk.transport.api.ConnectionInfo.Protocol;
import io.servicetalk.transport.netty.internal.NoopTransportObserver.NoopConnectionObserver;

import io.netty.buffer.ByteBuf;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.concurrent.TimeUnit;

import static io.servicetalk.buffer.netty.BufferAllocators.DEFAULT_ALLOCATOR;
import static io.servicetalk.concurrent.api.Executors.immediate;
import static io.servicetalk.concurrent.api.SourceAdapters.toSource;
import static io.servicetalk.concurrent.internal.TestTimeoutConstants.DEFAULT_TIMEOUT_SECONDS;
import static io.servicetalk.transport.api.ExecutionStrategy.offloadAll;
import static io.servicetalk.transport.netty.internal.CloseHandler.UNSUPPORTED_PROTOCOL_CLOSE_HANDLER;
import static io.servicetalk.transport.netty.internal.FlushStrategies.defaultFlushStrategy;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.instanceOf;
import static org.hamcrest.Matchers.is;
import static org.mockito.Mockito.mock;

class NettyChannelPublisherRefCountTest {

    private final TestPublisherSubscriber<Object> subscriber = new TestPublisherSubscriber<>();
    private Publisher<Object> publisher;
    private EmbeddedDuplexChannel channel;

    @BeforeEach
    public void setUp() throws Exception {
        channel = new EmbeddedDuplexChannel(false);
        publisher = DefaultNettyConnection.initChannel(channel,
                        DEFAULT_ALLOCATOR, immediate(), null, UNSUPPORTED_PROTOCOL_CLOSE_HANDLER,
                        defaultFlushStrategy(), null, channel2 -> { }, offloadAll(), mock(Protocol.class),
                        NoopConnectionObserver.INSTANCE, true).toFuture().get()
                .read();
    }

    @AfterEach
    public void tearDown() throws Exception {
        if (!channel.close().await(DEFAULT_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
            throw new IllegalStateException("Channel close not finished in 1 second.");
        }
    }

    @Test
    void testRefCountedLeaked() {
        toSource(publisher).subscribe(subscriber);
        subscriber.awaitSubscription().request(3);
        ByteBuf buffer = channel.alloc().buffer();
        channel.writeInbound(buffer);
        assertThat(subscriber.awaitOnError(), instanceOf(IllegalArgumentException.class));
        assertThat("Buffer not released.", buffer.refCnt(), is(0));
        assertThat("Channel not closed post ref count leaked.", channel.closeFuture().isDone(), is(true));
    }
}

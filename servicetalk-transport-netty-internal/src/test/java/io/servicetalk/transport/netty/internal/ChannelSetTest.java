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

import io.servicetalk.concurrent.CompletableSource.Processor;
import io.servicetalk.concurrent.api.Completable;
import io.servicetalk.concurrent.test.internal.TestCompletableSubscriber;

import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelId;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.DefaultChannelId;
import io.netty.util.Attribute;
import io.netty.util.concurrent.GenericFutureListener;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static io.servicetalk.concurrent.api.AsyncCloseables.closeAsyncGracefully;
import static io.servicetalk.concurrent.api.Executors.immediate;
import static io.servicetalk.concurrent.api.Processors.newCompletableProcessor;
import static io.servicetalk.concurrent.api.SourceAdapters.fromSource;
import static io.servicetalk.concurrent.api.SourceAdapters.toSource;
import static io.servicetalk.transport.netty.internal.ChannelSet.CHANNEL_CLOSEABLE_KEY;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ChannelSetTest {

    @Mock
    private Channel channel;
    @Mock
    private ChannelFuture channelCloseFuture;
    @Mock
    private ChannelPipeline channelPipeline;
    @Mock
    private PrivilegedListenableAsyncCloseable nettyConnection;
    @Mock
    private Attribute<PrivilegedListenableAsyncCloseable> mockClosableAttribute;

    private final ChannelId channelId = DefaultChannelId.newInstance();
    private final ChannelSet fixture = new ChannelSet(immediate());
    private final Processor closeAsyncGracefullyCompletable = newCompletableProcessor();
    private final Processor closeAsyncCompletable = newCompletableProcessor();
    private GenericFutureListener<ChannelFuture> listener;

    @BeforeEach
    public void setupMocks() {
        lenient().when(channel.id()).thenReturn(channelId);
        lenient().when(channel.closeFuture()).thenReturn(channelCloseFuture);
        lenient().when(channel.close()).then(invocation -> {
            listener.operationComplete(channelCloseFuture);
            return channelCloseFuture;
        });
        lenient().when(channelCloseFuture.channel()).thenReturn(channel);
        lenient().when(channel.pipeline()).thenReturn(channelPipeline);
        lenient().when(channel.attr(eq(CHANNEL_CLOSEABLE_KEY))).thenReturn(mockClosableAttribute);
        lenient().when(mockClosableAttribute.getAndSet(any())).thenReturn(nettyConnection);
        lenient().when(nettyConnection.onClose()).thenReturn(fromSource(closeAsyncCompletable));
        lenient().when(nettyConnection.closeAsync()).thenReturn(fromSource(closeAsyncCompletable));
        lenient().when(nettyConnection.closeAsyncNoOffload()).thenReturn(fromSource(closeAsyncCompletable));
        lenient().when(nettyConnection.closeAsyncGracefully()).thenReturn(fromSource(closeAsyncGracefullyCompletable));
        lenient().when(nettyConnection.closeAsyncGracefullyNoOffload())
                .thenReturn(fromSource(closeAsyncGracefullyCompletable));
        when(channelCloseFuture.addListener(any())).then((invocation) -> {
            listener = invocation.getArgument(0);
            return channelCloseFuture;
        });
        fixture.addIfAbsent(channel);
    }

    @Test
    void closeAsync() {
        Completable completable = fixture.closeAsync();
        verify(channel, never()).close();
        TestCompletableSubscriber subscriber = new TestCompletableSubscriber();
        toSource(completable).subscribe(subscriber);
        verify(channel).close();
        subscriber.awaitOnComplete();
    }

    @Test
    void closeAsyncGracefullyWithNettyConnectionChannelHandler() throws Exception {
        Completable completable = closeAsyncGracefully(fixture, 100, SECONDS);
        verify(nettyConnection, never()).closeAsyncGracefully();
        TestCompletableSubscriber subscriber = new TestCompletableSubscriber();
        toSource(completable).subscribe(subscriber);
        verify(nettyConnection).closeAsyncGracefullyNoOffload();
        verify(channel, never()).close();
        assertThat(subscriber.pollTerminal(10, MILLISECONDS), is(nullValue()));
        closeAsyncGracefullyCompletable.onComplete();
        assertThat(subscriber.pollTerminal(10, MILLISECONDS), is(nullValue()));
        listener.operationComplete(channelCloseFuture);
        subscriber.awaitOnComplete();
    }

    @Test
    void closeAsyncGracefullyWithoutNettyConnectionChannelHandler() {
        when(mockClosableAttribute.getAndSet(any())).thenReturn(null);
        Completable completable = closeAsyncGracefully(fixture, 100, SECONDS);
        verify(channel, never()).close();
        TestCompletableSubscriber subscriber = new TestCompletableSubscriber();
        toSource(completable).subscribe(subscriber);
        verify(channel).close();
        subscriber.awaitOnComplete();
    }

    @Test
    void testCloseAsyncGracefullyThenCloseAsync() throws Exception {
        Completable gracefulCompletable = closeAsyncGracefully(fixture, 100, SECONDS);
        Completable closeCompletable = fixture.closeAsync();

        TestCompletableSubscriber subscriber = new TestCompletableSubscriber();
        toSource(gracefulCompletable).subscribe(subscriber);
        verify(nettyConnection).closeAsyncGracefullyNoOffload();

        TestCompletableSubscriber subscriber2 = new TestCompletableSubscriber();
        toSource(closeCompletable).subscribe(subscriber2);
        verify(channel).close();
        // once closeCompletable being subscribed to closes the channel, the Completable returned from
        // closeAsyncGracefully must complete.
        closeAsyncGracefullyCompletable.onComplete();

        fixture.onClose().toFuture().get();

        subscriber.awaitOnComplete();
        subscriber2.awaitOnComplete();
    }

    @Test
    void testCloseAsyncThenCloseAsyncGracefully() throws Exception {
        Completable closeCompletable = fixture.closeAsync();
        Completable gracefulCompletable = closeAsyncGracefully(fixture, 100, SECONDS);

        TestCompletableSubscriber subscriber = new TestCompletableSubscriber();
        toSource(closeCompletable).subscribe(subscriber);
        verify(channel).close();

        TestCompletableSubscriber subscriber2 = new TestCompletableSubscriber();
        toSource(gracefulCompletable).subscribe(subscriber2);
        verify(nettyConnection, never()).closeAsyncGracefully();

        fixture.onClose().toFuture().get();

        subscriber.awaitOnComplete();
        subscriber2.awaitOnComplete();
    }

    @Test
    void testCloseAsyncGracefullyTwice() throws Exception {
        Completable gracefulCompletable1 = closeAsyncGracefully(fixture, 60, SECONDS);
        Completable gracefulCompletable2 = closeAsyncGracefully(fixture, 60, SECONDS);

        TestCompletableSubscriber subscriber = new TestCompletableSubscriber();
        toSource(gracefulCompletable1).subscribe(subscriber);
        verify(nettyConnection).closeAsyncGracefullyNoOffload();

        TestCompletableSubscriber subscriber2 = new TestCompletableSubscriber();
        toSource(gracefulCompletable2).subscribe(subscriber2);
        verify(nettyConnection, times(1)).closeAsyncGracefullyNoOffload();

        assertThat(subscriber.pollTerminal(10, MILLISECONDS), is(nullValue()));
        closeAsyncGracefullyCompletable.onComplete();
        assertThat(subscriber.pollTerminal(10, MILLISECONDS), is(nullValue()));

        listener.operationComplete(channelCloseFuture);

        fixture.onClose().toFuture().get();

        subscriber.awaitOnComplete();
        subscriber2.awaitOnComplete();
    }

    @Test
    void testCloseAsyncGracefullyTwiceTimesOut() throws Exception {
        Completable gracefulCompletable1 = closeAsyncGracefully(fixture, 100, MILLISECONDS);
        Completable gracefulCompletable2 = closeAsyncGracefully(fixture, 1000, MILLISECONDS);

        TestCompletableSubscriber subscriber = new TestCompletableSubscriber();
        toSource(gracefulCompletable1).subscribe(subscriber);
        verify(nettyConnection).closeAsyncGracefullyNoOffload();

        TestCompletableSubscriber subscriber2 = new TestCompletableSubscriber();
        toSource(gracefulCompletable2).subscribe(subscriber2);
        verify(nettyConnection, times(1)).closeAsyncGracefullyNoOffload();

        gracefulCompletable1.toFuture().get();
        subscriber.awaitOnComplete();
        subscriber2.awaitOnComplete();
        verify(channel).close();
    }

    @Test
    void testCloseAsyncTwice() throws Exception {
        Completable closeCompletable1 = fixture.closeAsync();
        Completable closeCompletable2 = fixture.closeAsync();

        TestCompletableSubscriber subscriber = new TestCompletableSubscriber();
        toSource(closeCompletable1).subscribe(subscriber);
        verify(channel).close();

        TestCompletableSubscriber subscriber2 = new TestCompletableSubscriber();
        toSource(closeCompletable2).subscribe(subscriber2);
        verify(channel, times(1)).close();

        fixture.onClose().toFuture().get();

        subscriber.awaitOnComplete();
        subscriber2.awaitOnComplete();
    }

    @Test
    void closeAsyncGracefullyClosesAfterTimeout() throws Exception {
        Completable completable = closeAsyncGracefully(fixture, 100, MILLISECONDS);
        TestCompletableSubscriber subscriber = new TestCompletableSubscriber();
        toSource(completable).subscribe(subscriber);
        verify(nettyConnection).closeAsyncGracefullyNoOffload();
        fixture.onClose().toFuture().get();
        verify(channel).close();
        subscriber.awaitOnComplete();
    }
}

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
import io.servicetalk.concurrent.api.AsyncCloseable;
import io.servicetalk.concurrent.api.Completable;
import io.servicetalk.concurrent.api.LegacyMockedCompletableListenerRule;
import io.servicetalk.concurrent.internal.ServiceTalkTestTimeout;

import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelId;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.DefaultChannelId;
import io.netty.util.Attribute;
import io.netty.util.concurrent.GenericFutureListener;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.junit.rules.Timeout;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

import static io.servicetalk.concurrent.api.AsyncCloseables.closeAsyncGracefully;
import static io.servicetalk.concurrent.api.Executors.immediate;
import static io.servicetalk.concurrent.api.Processors.newCompletableProcessor;
import static io.servicetalk.concurrent.api.SourceAdapters.fromSource;
import static io.servicetalk.transport.netty.internal.ChannelSet.CHANNEL_CLOSABLE_KEY;
import static java.lang.Boolean.FALSE;
import static java.lang.Boolean.parseBoolean;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.hamcrest.Matchers.is;
import static org.junit.Assume.assumeThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class ChannelSetTest {

    @Rule
    public final Timeout timeout = new ServiceTalkTestTimeout();
    @Rule
    public final ExpectedException thrown = ExpectedException.none();
    @Rule
    public final MockitoRule rule = MockitoJUnit.rule();
    @Rule
    public final LegacyMockedCompletableListenerRule subscriberRule1 = new LegacyMockedCompletableListenerRule();
    @Rule
    public final LegacyMockedCompletableListenerRule subscriberRule2 = new LegacyMockedCompletableListenerRule();
    @Rule
    public final LegacyMockedCompletableListenerRule subscriberRule3 = new LegacyMockedCompletableListenerRule();

    @Mock
    private Channel channel;
    @Mock
    private ChannelFuture channelCloseFuture;
    @Mock
    private ChannelPipeline channelPipeline;
    @Mock
    private NettyConnection nettyConnection;
    @Mock
    private Attribute<AsyncCloseable> mockClosableAttribute;

    private final ChannelId channelId = DefaultChannelId.newInstance();
    private final ChannelSet fixture = new ChannelSet(immediate());
    private final Processor closeAsyncGracefullyCompletable = newCompletableProcessor();
    private final Processor closeAsyncCompletable = newCompletableProcessor();
    private GenericFutureListener<ChannelFuture> listener;

    @Before
    public void setupMocks() {
        when(channel.id()).thenReturn(channelId);
        when(channel.closeFuture()).thenReturn(channelCloseFuture);
        when(channel.close()).then(invocation -> {
            listener.operationComplete(channelCloseFuture);
            return channelCloseFuture;
        });
        when(channelCloseFuture.channel()).thenReturn(channel);
        when(channel.pipeline()).thenReturn(channelPipeline);
        when(channel.attr(eq(CHANNEL_CLOSABLE_KEY))).thenReturn(mockClosableAttribute);
        when(mockClosableAttribute.getAndSet(any())).thenReturn(nettyConnection);
        when(nettyConnection.closeAsync()).thenReturn(fromSource(closeAsyncCompletable));
        when(nettyConnection.closeAsyncGracefully()).thenReturn(fromSource(closeAsyncGracefullyCompletable));
        when(channelCloseFuture.addListener(any())).then((invocation) -> {
            listener = invocation.getArgument(0);
            return channelCloseFuture;
        });
        fixture.addIfAbsent(channel);
    }

    @Test
    public void closeAsync() {
        Completable completable = fixture.closeAsync();
        verify(channel, never()).close();
        subscriberRule1.listen(completable);
        verify(channel).close();
        subscriberRule1.verifyCompletion();
    }

    @Test
    public void closeAsyncGracefullyWithNettyConnectionChannelHandler() throws Exception {
        Completable completable = closeAsyncGracefully(fixture, 100, SECONDS);
        verify(nettyConnection, never()).closeAsyncGracefully();
        subscriberRule1.listen(completable);
        verify(nettyConnection).closeAsyncGracefully();
        verify(channel, never()).close();
        subscriberRule1.verifyNoEmissions();
        closeAsyncGracefullyCompletable.onComplete();
        subscriberRule1.verifyNoEmissions();
        listener.operationComplete(channelCloseFuture);
        subscriberRule1.verifyCompletion();
    }

    @Test
    public void closeAsyncGracefullyWithoutNettyConnectionChannelHandler() {
        when(mockClosableAttribute.getAndSet(any())).thenReturn(null);
        Completable completable = closeAsyncGracefully(fixture, 100, SECONDS);
        verify(channel, never()).close();
        subscriberRule1.listen(completable);
        verify(channel).close();
        subscriberRule1.verifyCompletion();
    }

    @Test
    public void testCloseAsyncGracefullyThenCloseAsync() throws Exception {
        Completable gracefulCompletable = closeAsyncGracefully(fixture, 100, SECONDS);
        Completable closeCompletable = fixture.closeAsync();

        subscriberRule1.listen(gracefulCompletable);
        verify(nettyConnection).closeAsyncGracefully();

        subscriberRule2.listen(closeCompletable);
        verify(channel).close();
        // once closeCompletable being subscribed to closes the channel, the Completable returned from
        // closeAsyncGracefully must complete.
        closeAsyncGracefullyCompletable.onComplete();

        fixture.onClose().toFuture().get();

        subscriberRule1.verifyCompletion();
        subscriberRule2.verifyCompletion();
    }

    @Test
    public void testCloseAsyncThenCloseAsyncGracefully() throws Exception {
        Completable closeCompletable = fixture.closeAsync();
        Completable gracefulCompletable = closeAsyncGracefully(fixture, 100, SECONDS);

        subscriberRule2.listen(closeCompletable);
        verify(channel).close();

        subscriberRule1.listen(gracefulCompletable);
        verify(nettyConnection, never()).closeAsyncGracefully();

        fixture.onClose().toFuture().get();

        subscriberRule1.verifyCompletion();
        subscriberRule2.verifyCompletion();
    }

    @Test
    public void testCloseAsyncGracefullyTwice() throws Exception {
        Completable gracefulCompletable1 = closeAsyncGracefully(fixture, 60, SECONDS);
        Completable gracefulCompletable2 = closeAsyncGracefully(fixture, 60, SECONDS);

        subscriberRule1.listen(gracefulCompletable1);
        verify(nettyConnection).closeAsyncGracefully();

        subscriberRule2.listen(gracefulCompletable2);
        verify(nettyConnection, times(1)).closeAsyncGracefully();

        subscriberRule1.verifyNoEmissions();
        closeAsyncGracefullyCompletable.onComplete();
        subscriberRule1.verifyNoEmissions();

        listener.operationComplete(channelCloseFuture);

        fixture.onClose().toFuture().get();

        subscriberRule1.verifyCompletion();
        subscriberRule2.verifyCompletion();
    }

    @Test
    public void testCloseAsyncGracefullyTwiceTimesOut() throws Exception {
        Completable gracefulCompletable1 = closeAsyncGracefully(fixture, 100, MILLISECONDS);
        Completable gracefulCompletable2 = closeAsyncGracefully(fixture, 1000, MILLISECONDS);

        subscriberRule1.listen(gracefulCompletable1);
        verify(nettyConnection).closeAsyncGracefully();

        subscriberRule2.listen(gracefulCompletable2);
        verify(nettyConnection, times(1)).closeAsyncGracefully();

        gracefulCompletable1.toFuture().get();
        subscriberRule2.verifyCompletion();
        verify(channel).close();
    }

    @Test
    public void testCloseAsyncTwice() throws Exception {
        Completable closeCompletable1 = fixture.closeAsync();
        Completable closeCompletable2 = fixture.closeAsync();

        subscriberRule1.listen(closeCompletable1);
        verify(channel).close();

        subscriberRule2.listen(closeCompletable2);
        verify(channel, times(1)).close();

        fixture.onClose().toFuture().get();

        subscriberRule1.verifyCompletion();
        subscriberRule2.verifyCompletion();
    }

    @Test
    public void closeAsyncGracefullyClosesAfterTimeout() throws Exception {
        assumeThat("Ignored flaky test", parseBoolean(System.getenv("CI")), is(FALSE));
        Completable completable = closeAsyncGracefully(fixture, 100, MILLISECONDS);
        subscriberRule1.listen(completable);
        verify(nettyConnection).closeAsyncGracefully();
        fixture.onClose().toFuture().get();
        verify(channel).close();
        subscriberRule1.verifyCompletion();
    }
}

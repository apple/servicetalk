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

import io.servicetalk.concurrent.api.AsyncCloseable;
import io.servicetalk.concurrent.api.AsyncCloseables;
import io.servicetalk.concurrent.api.Completable;
import io.servicetalk.concurrent.api.ListenableAsyncCloseable;
import io.servicetalk.concurrent.api.MockedCompletableListenerRule;
import io.servicetalk.concurrent.api.TestCompletable;
import io.servicetalk.transport.api.ServerContext;

import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelPipeline;
import io.netty.util.concurrent.GenericFutureListener;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

import java.util.ArrayList;
import java.util.List;

import static io.servicetalk.concurrent.api.AsyncCloseables.closeAsyncGracefully;
import static io.servicetalk.concurrent.api.Executors.immediate;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class NettyServerContextTest {
    @Rule
    public final MockitoRule rule = MockitoJUnit.rule();
    @Rule
    public final MockedCompletableListenerRule subscriberRule = new MockedCompletableListenerRule();

    TestCompletable closeBeforeCloseAsyncCompletable = new TestCompletable();
    TestCompletable closeBeforeCloseAsyncGracefulCompletable = new TestCompletable();
    AsyncCloseable closeBefore = new AsyncCloseable() {
        @Override
        public Completable closeAsync() {
            return closeBeforeCloseAsyncCompletable;
        }

        @Override
        public Completable closeAsyncGracefully() {
            return closeBeforeCloseAsyncGracefulCompletable;
        }
    };
    TestCompletable closeBeforeBeforeCloseAsyncCompletable = new TestCompletable();
    TestCompletable closeBeforeBeforeCloseAsyncGracefulCompletable = new TestCompletable();
    AsyncCloseable closeBeforeBefore = new AsyncCloseable() {
        @Override
        public Completable closeAsync() {
            return closeBeforeBeforeCloseAsyncCompletable;
        }

        @Override
        public Completable closeAsyncGracefully() {
            return closeBeforeBeforeCloseAsyncGracefulCompletable;
        }
    };
    TestCompletable channelSetCloseAsyncCompletable = new TestCompletable();
    TestCompletable channelSetCloseAsyncGracefulCompletable = new TestCompletable();
    @Mock
    Channel channel;
    @Mock
    private ChannelFuture channelCloseFuture;
    private final List<GenericFutureListener<ChannelFuture>> listeners = new ArrayList<>();
    @Mock
    private ChannelPipeline channelPipeline;
    @Mock
    private ConnectionHolderChannelHandler connectionHolderChannelHandler;

    ListenableAsyncCloseable channelSetCloseable = AsyncCloseables.toListenableAsyncCloseable(new AsyncCloseable() {
        @Override
        public Completable closeAsync() {
            return channelSetCloseAsyncCompletable;
        }

        @Override
        public Completable closeAsyncGracefully() {
            return channelSetCloseAsyncGracefulCompletable;
        }
    });

    private volatile boolean closed;
    private ServerContext fixture;

    @Before
    public void setupMocks() {
        when(channel.closeFuture()).thenReturn(channelCloseFuture);
        when(channel.close()).then(invocation -> {
            for (GenericFutureListener<ChannelFuture> listener : listeners) {
                listener.operationComplete(channelCloseFuture);
            }
            closed = true;
            return channelCloseFuture;
        });
        when(channelCloseFuture.channel()).thenReturn(channel);
        when(channelCloseFuture.addListener(any())).then((invocation) -> {
            GenericFutureListener<ChannelFuture> listener = invocation.getArgument(0);
            if (closed) {
                listener.operationComplete(channelCloseFuture);
            } else {
                listeners.add(listener);
            }
            return channelCloseFuture;
        });
        when(channel.pipeline()).thenReturn(channelPipeline);
        when(channelPipeline.get(ConnectionHolderChannelHandler.class)).thenReturn(connectionHolderChannelHandler);

        fixture = NettyServerContext.wrap(channel, channelSetCloseable, closeBefore, immediate());
    }

    @Test
    public void testCloseAsyncOrdering() {
        closeBeforeCloseAsyncCompletable.verifyListenNotCalled();
        fixture.closeAsync().subscribe();
        closeBeforeCloseAsyncCompletable.verifyListenCalled();

        channelSetCloseAsyncCompletable.verifyListenNotCalled();
        verify(channel, never()).close();
        closeBeforeCloseAsyncCompletable.onComplete();
        verify(channel).close();
        channelSetCloseAsyncCompletable.verifyListenCalled();

        // ensure we're not calling the graceful one
        channelSetCloseAsyncGracefulCompletable.verifyListenNotCalled();

        subscriberRule.listen(fixture.onClose());
        subscriberRule.verifyNoEmissions();

        channelSetCloseAsyncCompletable.onComplete();
        subscriberRule.verifyCompletion();
    }

    @Test
    public void testCloseAsyncGracefulOrdering() {
        closeBeforeCloseAsyncGracefulCompletable.verifyListenNotCalled();
        closeAsyncGracefully(fixture, 100, SECONDS).subscribe();
        closeBeforeCloseAsyncGracefulCompletable.verifyListenCalled();

        channelSetCloseAsyncGracefulCompletable.verifyListenNotCalled();
        verify(channel, never()).close();
        closeBeforeCloseAsyncGracefulCompletable.onComplete();
        channelSetCloseAsyncGracefulCompletable.verifyListenCalled();
        verify(channel).close();

        channelSetCloseAsyncCompletable.verifyListenNotCalled();

        subscriberRule.listen(fixture.onClose());
        subscriberRule.verifyNoEmissions();

        channelSetCloseAsyncGracefulCompletable.onComplete();
        subscriberRule.verifyCompletion();
    }

    @Test
    public void testCloseAsyncOrderingExtraWrap() {

        fixture = NettyServerContext.wrap((NettyServerContext) fixture, closeBeforeBefore);

        closeBeforeBeforeCloseAsyncCompletable.verifyListenNotCalled();
        closeBeforeCloseAsyncCompletable.verifyListenNotCalled();
        fixture.closeAsync().subscribe();
        closeBeforeBeforeCloseAsyncCompletable.verifyListenCalled();

        closeBeforeCloseAsyncCompletable.verifyListenNotCalled();
        closeBeforeBeforeCloseAsyncCompletable.onComplete();
        closeBeforeCloseAsyncCompletable.verifyListenCalled();

        channelSetCloseAsyncCompletable.verifyListenNotCalled();
        verify(channel, never()).close();
        closeBeforeCloseAsyncCompletable.onComplete();
        channelSetCloseAsyncCompletable.verifyListenCalled();
        verify(channel).close();

        channelSetCloseAsyncGracefulCompletable.verifyListenNotCalled();

        subscriberRule.listen(fixture.onClose());
        subscriberRule.verifyNoEmissions();

        channelSetCloseAsyncCompletable.onComplete();
        subscriberRule.verifyCompletion();
    }
}

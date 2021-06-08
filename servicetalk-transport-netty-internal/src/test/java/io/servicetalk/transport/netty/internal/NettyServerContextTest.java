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
import io.servicetalk.concurrent.api.LegacyTestCompletable;
import io.servicetalk.concurrent.api.ListenableAsyncCloseable;
import io.servicetalk.concurrent.test.internal.TestCompletableSubscriber;
import io.servicetalk.transport.api.ServerContext;

import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelPipeline;
import io.netty.util.Attribute;
import io.netty.util.concurrent.GenericFutureListener;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.List;

import static io.servicetalk.concurrent.api.AsyncCloseables.closeAsyncGracefully;
import static io.servicetalk.concurrent.api.Executors.immediate;
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
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NettyServerContextTest {
    private final TestCompletableSubscriber subscriberRule = new TestCompletableSubscriber();

    LegacyTestCompletable closeBeforeCloseAsyncCompletable = new LegacyTestCompletable();
    LegacyTestCompletable closeBeforeCloseAsyncGracefulCompletable = new LegacyTestCompletable();
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
    LegacyTestCompletable closeBeforeBeforeCloseAsyncCompletable = new LegacyTestCompletable();
    LegacyTestCompletable closeBeforeBeforeCloseAsyncGracefulCompletable = new LegacyTestCompletable();
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
    LegacyTestCompletable channelSetCloseAsyncCompletable = new LegacyTestCompletable();
    LegacyTestCompletable channelSetCloseAsyncGracefulCompletable = new LegacyTestCompletable();
    @Mock
    Channel channel;
    @Mock
    private ChannelFuture channelCloseFuture;
    private final List<GenericFutureListener<ChannelFuture>> listeners = new ArrayList<>();
    @Mock
    private ChannelPipeline channelPipeline;
    @Mock
    private Attribute<AsyncCloseable> mockClosableAttribute;

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

    @BeforeEach
    public void setupMocks() {
        when(channel.closeFuture()).thenReturn(channelCloseFuture);
        when(channel.close()).then(invocation -> {
            for (GenericFutureListener<ChannelFuture> listener : listeners) {
                listener.operationComplete(channelCloseFuture);
            }
            closed = true;
            return channelCloseFuture;
        });
        lenient().when(channelCloseFuture.channel()).thenReturn(channel);
        lenient().when(channelCloseFuture.addListener(any())).then((invocation) -> {
            GenericFutureListener<ChannelFuture> listener = invocation.getArgument(0);
            if (closed) {
                listener.operationComplete(channelCloseFuture);
            } else {
                listeners.add(listener);
            }
            return channelCloseFuture;
        });
        lenient().when(channel.pipeline()).thenReturn(channelPipeline);
        lenient().when(channel.attr(eq(CHANNEL_CLOSEABLE_KEY))).thenReturn(mockClosableAttribute);
        lenient().when(mockClosableAttribute.getAndSet(any())).thenReturn(null);
        fixture = NettyServerContext.wrap(channel, channelSetCloseable, closeBefore,
                new ExecutionContextBuilder().executor(immediate()).build());
    }

    @Test
    void testCloseAsyncOrdering() {
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

        toSource(fixture.onClose()).subscribe(subscriberRule);
        assertThat(subscriberRule.pollTerminal(10, MILLISECONDS), is(nullValue()));

        channelSetCloseAsyncCompletable.onComplete();
        subscriberRule.awaitOnComplete();
    }

    @Test
    void testCloseAsyncGracefulOrdering() {
        closeBeforeCloseAsyncGracefulCompletable.verifyListenNotCalled();
        closeAsyncGracefully(fixture, 100, SECONDS).subscribe();
        closeBeforeCloseAsyncGracefulCompletable.verifyListenCalled();

        channelSetCloseAsyncGracefulCompletable.verifyListenNotCalled();
        verify(channel, never()).close();
        closeBeforeCloseAsyncGracefulCompletable.onComplete();
        channelSetCloseAsyncGracefulCompletable.verifyListenCalled();
        verify(channel).close();

        channelSetCloseAsyncCompletable.verifyListenNotCalled();

        toSource(fixture.onClose()).subscribe(subscriberRule);
        assertThat(subscriberRule.pollTerminal(10, MILLISECONDS), is(nullValue()));

        channelSetCloseAsyncGracefulCompletable.onComplete();
        subscriberRule.awaitOnComplete();
    }

    @Test
    void testCloseAsyncOrderingExtraWrap() {

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

        toSource(fixture.onClose()).subscribe(subscriberRule);
        assertThat(subscriberRule.pollTerminal(10, MILLISECONDS), is(nullValue()));

        channelSetCloseAsyncCompletable.onComplete();
        subscriberRule.awaitOnComplete();
    }
}

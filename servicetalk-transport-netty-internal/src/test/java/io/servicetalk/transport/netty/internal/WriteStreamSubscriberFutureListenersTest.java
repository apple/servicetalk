/*
 * Copyright © 2020 Apple Inc. and the ServiceTalk project authors
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

import io.servicetalk.concurrent.api.TestSubscription;
import io.servicetalk.concurrent.test.internal.TestCompletableSubscriber;
import io.servicetalk.transport.netty.internal.NoopTransportObserver.NoopWriteObserver;

import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelOutboundHandlerAdapter;
import io.netty.channel.ChannelPromise;
import io.netty.channel.EventLoop;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.util.ReferenceCountUtil;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.stubbing.Answer;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

import static io.servicetalk.concurrent.internal.DeliberateException.DELIBERATE_EXCEPTION;
import static io.servicetalk.transport.netty.internal.CloseHandler.UNSUPPORTED_PROTOCOL_CLOSE_HANDLER;
import static java.util.function.UnaryOperator.identity;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.is;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyZeroInteractions;
import static org.mockito.Mockito.when;

class WriteStreamSubscriberFutureListenersTest {
    private final BlockingQueue<ChannelFutureListener> listeners;
    private final EmbeddedChannel channel;
    private final WriteStreamSubscriber subscriber;

    WriteStreamSubscriberFutureListenersTest() {
        listeners = new LinkedBlockingQueue<>();
        channel = new EmbeddedChannel(new ChannelOutboundHandlerAdapter() {
            @Override
            public void write(final ChannelHandlerContext ctx, final Object msg, final ChannelPromise promise)
                    throws Exception {
                ChannelFutureListener listener = mock(ChannelFutureListener.class);
                listeners.add(listener);
                promise.addListener(listener);
                super.write(ctx, msg, promise);
            }
        });
        WriteDemandEstimator estimator = WriteDemandEstimators.newDefaultEstimator();
        TestCompletableSubscriber completableSubscriber = new TestCompletableSubscriber();
        subscriber = new WriteStreamSubscriber(channel, estimator, completableSubscriber,
                UNSUPPORTED_PROTOCOL_CLOSE_HANDLER, NoopWriteObserver.INSTANCE, identity(), false);
        TestSubscription subscription = new TestSubscription();
        subscriber.onSubscribe(subscription);
        assertThat("No items requested.", subscription.requested(), greaterThan(0L));
    }

    @AfterEach
    public void tearDown() throws Exception {
        channel.close().sync().await();
    }

    @Test
    void singleWriteAndFlush() throws Exception {
        ChannelFutureListener listener1 = doWrite();
        channel.flush();
        verifyListenerInvokedWithSuccess(listener1);
    }

    @Test
    void multipleWritesThenFlush() throws Exception {
        ChannelFutureListener listener1 = doWrite();
        ChannelFutureListener listener2 = doWrite();
        channel.flush();

        verifyListenerInvokedWithSuccess(listener1);
        verifyListenerInvokedWithSuccess(listener2);
    }

    @Test
    void multipleWritesMultipleFlushes() throws Exception {
        ChannelFutureListener listener1 = doWrite();
        channel.flush();
        verifyListenerInvokedWithSuccess(listener1);

        ChannelFutureListener listener2 = doWrite();
        channel.flush();
        verifyListenerInvokedWithSuccess(listener2);
    }

    @Test
    void singleWriteThenSourceComplete() throws Exception {
        ChannelFutureListener listener1 = doWrite();
        subscriber.onComplete();
        verifyZeroInteractions(listener1);

        channel.flush();
        verifyListenerInvokedWithSuccess(listener1);
    }

    @Test
    void singleWriteThenSourceFail() throws Exception {
        ChannelFutureListener listener1 = doWrite();
        subscriber.onError(DELIBERATE_EXCEPTION);
        verifyZeroInteractions(listener1);

        channel.flush();
        verifyListenerInvokedWithFailure(listener1);
    }

    @Test
    void multipleWriteThenSourceComplete() throws Exception {
        ChannelFutureListener listener1 = doWrite();
        ChannelFutureListener listener2 = doWrite();
        subscriber.onComplete();
        verifyZeroInteractions(listener1);
        verifyZeroInteractions(listener2);

        channel.flush();
        verifyListenerInvokedWithSuccess(listener1);
        verifyListenerInvokedWithSuccess(listener2);
    }

    @Test
    void multipleWriteThenSourceFail() throws Exception {
        ChannelFutureListener listener1 = doWrite();
        ChannelFutureListener listener2 = doWrite();
        subscriber.onError(DELIBERATE_EXCEPTION);
        verifyZeroInteractions(listener1);
        verifyZeroInteractions(listener2);

        channel.flush();
        verifyListenerInvokedWithFailure(listener1);
        verifyListenerInvokedWithFailure(listener2);
    }

    @Test
    void synchronousCompleteWrite() throws Exception {
        Channel mockChannel = mock(Channel.class);
        EventLoop mockEventLoop = mock(EventLoop.class);
        when(mockEventLoop.inEventLoop()).thenReturn(true);
        when(mockChannel.eventLoop()).thenReturn(mockEventLoop);
        when(mockChannel.newSucceededFuture()).thenReturn(channel.newSucceededFuture());
        doAnswer((Answer<Void>) invocation -> {
            ReferenceCountUtil.release(invocation.getArgument(0));
            ChannelFutureListener listener = mock(ChannelFutureListener.class);
            listeners.add(listener);
            ChannelPromise promise = invocation.getArgument(1);
            promise.addListener(listener);
            promise.setSuccess();
            return null;
        }).when(mockChannel).write(any(), any());
        WriteDemandEstimator estimator = WriteDemandEstimators.newDefaultEstimator();
        TestCompletableSubscriber completableSubscriber = new TestCompletableSubscriber();
        WriteStreamSubscriber subscriber = new WriteStreamSubscriber(mockChannel, estimator, completableSubscriber,
                UNSUPPORTED_PROTOCOL_CLOSE_HANDLER, NoopWriteObserver.INSTANCE, identity(), false);
        subscriber.onNext(1);
        verifyListenerInvokedWithSuccess(listeners.take());
        subscriber.onNext(2);
        verifyListenerInvokedWithSuccess(listeners.take());
    }

    private ChannelFutureListener doWrite() throws InterruptedException {
        subscriber.onNext(1);
        ChannelFutureListener listener = listeners.take();
        verifyZeroInteractions(listener);
        return listener;
    }

    private void verifyListenerInvokedWithSuccess(final ChannelFutureListener listener) throws Exception {
        ArgumentCaptor<ChannelFuture> future = ArgumentCaptor.forClass(ChannelFuture.class);
        verify(listener).operationComplete(future.capture());
        assertThat("Unexpected future result.", future.getValue().isSuccess(), is(true));
    }

    private void verifyListenerInvokedWithFailure(final ChannelFutureListener listener) throws Exception {
        ArgumentCaptor<ChannelFuture> future = ArgumentCaptor.forClass(ChannelFuture.class);
        verify(listener).operationComplete(future.capture());
        assertThat("Unexpected future result.", future.getValue().isSuccess(), is(false));
        assertThat("Unexpected future result.", future.getValue().cause(), is(DELIBERATE_EXCEPTION));
    }
}

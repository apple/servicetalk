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

import io.servicetalk.concurrent.Cancellable;
import io.servicetalk.concurrent.api.MockedSubscriberRule;
import io.servicetalk.concurrent.api.Publisher;
import io.servicetalk.concurrent.api.Single;
import io.servicetalk.concurrent.internal.ServiceTalkTestTimeout;

import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelOutboundHandlerAdapter;
import io.netty.channel.embedded.EmbeddedChannel;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.Timeout;
import org.mockito.Mockito;
import org.reactivestreams.Subscriber;
import org.reactivestreams.Subscription;

import java.nio.channels.ClosedChannelException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import javax.annotation.Nullable;

import static io.servicetalk.concurrent.api.DeliberateException.DELIBERATE_EXCEPTION;
import static io.servicetalk.concurrent.internal.ServiceTalkTestTimeout.DEFAULT_TIMEOUT_SECONDS;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;

public class NettyChannelPublisherTest {
    @Rule
    public final Timeout timeout = new ServiceTalkTestTimeout();
    @Rule
    public final MockedSubscriberRule<Integer> subscriber = new MockedSubscriberRule<>();

    private Publisher<Integer> publisher;
    private EmbeddedChannel channel;
    private boolean nextItemTerminal;
    private AbstractChannelReadHandler<Integer> handler;
    private ChannelHandlerContext handlerCtx;
    private boolean readRequested;

    @Before
    public void setUp() {
        handler = new AbstractChannelReadHandler<Integer>(integer -> nextItemTerminal) {
            @Override
            protected void onPublisherCreation(ChannelHandlerContext ctx, Publisher<Integer> newPublisher) {
                publisher = newPublisher;
            }
        };
        channel = new EmbeddedChannel(handler, new ChannelOutboundHandlerAdapter() {
            @Override
            public void read(ChannelHandlerContext ctx) throws Exception {
                readRequested = true;
                super.read(ctx);
            }
        });
        handlerCtx = channel.pipeline().context(handler);
    }

    @After
    public void tearDown() throws Exception {
        if (!channel.close().await(DEFAULT_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
            throw new IllegalStateException("Channel close not finished in 1 second.");
        }
    }

    @Test
    public void testSupplyEqualsDemand() throws Exception {
        subscriber.subscribe(publisher).request(3);
        fireChannelRead(1, 2);
        subscriber.verifyItems(1, 2);
        nextItemTerminal = true;
        fireChannelReadToBuffer(3);
        subscriber.verifySuccessNoRequestN(1, 2, 3);
    }

    @Test
    public void testSupplyLessThanDemand() throws Exception {
        subscriber.subscribe(publisher).request(3);
        fireChannelRead(1, 2);
        subscriber.verifyItems(1, 2);
        nextItemTerminal = true;
        fireChannelRead(3);
        subscriber.verifySuccessNoRequestN(1, 2, 3);
    }

    @Test
    public void testDemandLessThanSupply() throws Exception {
        subscriber.subscribe(publisher).request(1);
        fireChannelRead(1, 2);
        subscriber.verifyItems(1).request(1);
        nextItemTerminal = true;
        fireChannelReadToBuffer(3);
        subscriber.request(1).verifySuccessNoRequestN(1, 2, 3);
    }

    @Test
    public void testBufferDrainOnClose() throws Exception {
        subscriber.subscribe(publisher);
        fireChannelRead(1, 2);
        channel.close().await();
        subscriber.request(2);
        subscriber.verifyItems(1, 2).verifyFailure(ClosedChannelException.class);
    }

    @Test
    public void testErrorBufferedWithExactDemand() throws Exception {
        subscriber.subscribe(publisher).request(1);
        fireChannelRead(1, 2);
        handler.exceptionCaught(handlerCtx, DELIBERATE_EXCEPTION);
        subscriber.verifyItems(1).request(1).verifyItems(1, 2).verifyFailure(DELIBERATE_EXCEPTION);
    }

    @Test
    public void testErrorBufferedWithMoreDemand() throws Exception {
        subscriber.subscribe(publisher).request(1);
        fireChannelRead(1, 2);
        handler.exceptionCaught(handlerCtx, DELIBERATE_EXCEPTION);
        subscriber.verifyItems(1).request(2).verifyItems(1, 2).verifyFailure(DELIBERATE_EXCEPTION);
    }

    @Test
    public void testErrorWithNoDemandNoBuffer() {
        subscriber.subscribe(publisher);
        handler.exceptionCaught(handlerCtx, DELIBERATE_EXCEPTION);
        subscriber.verifyFailure(DELIBERATE_EXCEPTION);
    }

    @Test
    public void testErrorWithNoDemandAndBuffer() throws Exception {
        subscriber.subscribe(publisher);
        fireChannelRead(1);
        handler.exceptionCaught(handlerCtx, DELIBERATE_EXCEPTION);
        subscriber.request(1).verifyItems(1).verifyFailure(DELIBERATE_EXCEPTION);
    }

    @Test
    public void testErrorNoEmission() {
        subscriber.subscribe(publisher).request(1);
        handler.exceptionCaught(handlerCtx, DELIBERATE_EXCEPTION);
        subscriber.verifyFailure(DELIBERATE_EXCEPTION);
    }

    @Test
    public void testConcurrentSubscribers() throws Exception {
        subscriber.subscribe(publisher).request(1);
        fireChannelRead(1);
        subscriber.verifyItems(1);

        @SuppressWarnings("unchecked")
        org.reactivestreams.Subscriber<Integer> sub2 = mock(org.reactivestreams.Subscriber.class);
        publisher.subscribe(sub2);
        verify(sub2).onSubscribe(any(Subscription.class));
        verify(sub2).onError(any(IllegalStateException.class));
        verifyNoMoreInteractions(sub2);

        nextItemTerminal = true;
        fireChannelReadToBuffer(2);
        subscriber.request(1).verifySuccess(1, 2);
    }

    @Test
    public void testSequentialSubscriptionsNoCarryOverDemand() throws Exception {
        nextItemTerminal = true;
        subscriber.subscribe(publisher).request(1);
        fireChannelRead(1);
        subscriber.verifySuccessNoRequestN(1);

        subscriber.subscribe(publisher).request(1);
        fireChannelRead(1);
        subscriber.verifySuccessNoRequestN(1);
    }

    @Test
    public void testSequentialSubscriptionsCarryOverDemand() throws Exception {
        nextItemTerminal = true;
        subscriber.subscribe(publisher).request(3);
        fireChannelRead(1);
        subscriber.verifySuccessNoRequestN(1);

        nextItemTerminal = false;
        assertThat("Unexpected read requested from the channel.", readRequested, is(false));
        subscriber.subscribe(publisher).request(2);
        fireChannelRead(2);
        nextItemTerminal = true;
        fireChannelRead(3);
        subscriber.verifySuccessNoRequestN(2, 3);
    }

    @Test
    public void testBufferBetweenSubscriptions() throws Exception {
        nextItemTerminal = true;
        subscriber.subscribe(publisher).request(3);
        fireChannelRead(1);
        subscriber.verifySuccessNoRequestN(1);

        fireChannelReadToBuffer(2);
        subscriber.subscribe(publisher).request(3).verifySuccess(2);
    }

    @Test
    public void testCancelBeforeTerminal() throws Exception {
        subscriber.subscribe(publisher).request(3);
        fireChannelRead(1);
        subscriber.verifyItems(1).cancel();
        assertThat("Channel not closed post cancel.", channel.closeFuture().isDone(), is(true));
    }

    @Test
    public void testCancelAfterTerminal() throws Exception {
        subscriber.subscribe(publisher).request(3);
        nextItemTerminal = true;
        fireChannelRead(1);
        subscriber.verifySuccess(1).cancel();
        assertThat("Channel closed on cancel post terminate.", channel.closeFuture().isDone(), is(false));
    }

    @Test
    public void testDelayedCancel() throws Exception {
        nextItemTerminal = true;
        subscriber.subscribe(publisher).request(3);
        final Subscription firstSubscription = subscriber.getSubscription();
        fireChannelRead(1);
        subscriber.verifySuccessNoRequestN(1);

        nextItemTerminal = false;
        fireChannelReadToBuffer(2);
        subscriber.subscribe(publisher).request(3).verifyItems(2);

        //noinspection ConstantConditions
        firstSubscription.cancel();

        nextItemTerminal = true;
        fireChannelReadToBuffer(3);
        subscriber.verifyItems(2, 3);
    }

    @Test
    public void testDelayedRequestN() throws Exception {
        nextItemTerminal = true;
        subscriber.subscribe(publisher).request(3);
        final Subscription firstSubscription = subscriber.getSubscription();
        fireChannelRead(1);
        subscriber.verifySuccessNoRequestN(1);

        fireChannelReadToBuffer(2);
        subscriber.subscribe(publisher).verifyNoEmissions();

        //noinspection ConstantConditions
        firstSubscription.request(3);

        subscriber.verifyNoEmissions();
        subscriber.request(1).verifySuccess(2);
    }

    @Test
    public void testEmitItemsWithNoSubscriber() throws Exception {
        nextItemTerminal = true;
        fireChannelReadToBuffer(1);
        subscriber.subscribe(publisher).request(1).verifySuccess(1);
    }

    @Test
    public void testCancelFromWithinComplete() throws Exception {
        final AtomicReference<Object> resultRef = new AtomicReference<>();
        fireChannelReadToBuffer(1);
        nextItemTerminal = true;
        publisher.first().subscribe(new Single.Subscriber<Integer>() {
            @Override
            public void onSubscribe(Cancellable cancellable) {
                //noop
            }

            @Override
            public void onSuccess(@Nullable Integer result) {
                resultRef.set(result);
            }

            @Override
            public void onError(Throwable t) {
                resultRef.set(t);
            }
        });
        assertThat("Unexpected value.", resultRef.get(), is(1));
        assertThat("Channel closed.", channel.closeFuture().isDone(), is(false));
    }

    @Test
    public void testSubscribePostChannelClose() throws Exception {
        channel.close().await();
        subscriber.subscribe(publisher);
        subscriber.verifyFailure(ClosedChannelException.class);
    }

    @Test
    public void testTwoSubscribersPostChannelClose() throws Exception {
        channel.close().await();
        subscriber.subscribe(publisher);
        subscriber.verifyFailure(ClosedChannelException.class);
        @SuppressWarnings("unchecked")
        org.reactivestreams.Subscriber<Integer> mock = Mockito.mock(org.reactivestreams.Subscriber.class);
        publisher.subscribe(mock);
        verify(mock).onSubscribe(any());
        verify(mock).onError(any(ClosedChannelException.class));
        verifyNoMoreInteractions(mock);
    }

    @Test
    public void testQueuedCompleteAndFatalErrorExactDemand() throws Exception {
        subscriber.subscribe(publisher);
        nextItemTerminal = true;
        fireChannelRead(1);
        channel.close();
        subscriber.request(1).verifySuccess(1);
    }

    @Test
    public void testQueuedCompleteAndFatalErrorMoreDemand() throws Exception {
        subscriber.subscribe(publisher);
        nextItemTerminal = true;
        fireChannelRead(1);
        channel.close();
        subscriber.request(2).verifySuccess(1);
    }

    @Test
    public void testQueuedErrorAndFatalErrorExactDemand() throws Exception {
        subscriber.subscribe(publisher);
        fireChannelRead(1);
        channel.pipeline().fireExceptionCaught(DELIBERATE_EXCEPTION);
        channel.close();
        subscriber.request(1).verifyItems(1).verifyFailure(DELIBERATE_EXCEPTION);
    }

    @Test
    public void testQueuedErrorAndFatalErrorMoreDemand() throws Exception {
        subscriber.subscribe(publisher);
        fireChannelRead(1);
        channel.pipeline().fireExceptionCaught(DELIBERATE_EXCEPTION);
        channel.close();
        subscriber.request(2).verifyItems(1).verifyFailure(DELIBERATE_EXCEPTION);
    }

    @Test
    public void invalidRequestNDoesCancel() throws Exception {
        subscriber.subscribe(publisher);
        fireChannelRead(1, 2, 3);
        subscriber.request(1).verifyItems(1);
        subscriber.request(-1).verifyFailure(IllegalArgumentException.class);
        subscriber.request(2).verifyNoEmissions();
        assertFalse(channel.isActive());
        assertFalse(channel.isOpen());
    }

    @Test
    public void testChannelReadThrowsRequestBeforeChannelRead() {
        testChannelReadThrows(false);
    }

    @Test
    public void testChannelReadThrowsRequestAfterChannelRead() {
        testChannelReadThrows(true);
    }

    private void testChannelReadThrows(boolean requestLate) {
        final AtomicBoolean onErrorCalled = new AtomicBoolean();
        final AtomicReference<Subscription> subRef = new AtomicReference<>();
        final AtomicReference<AssertionError> assertErrorRef = new AtomicReference<>();

        publisher.subscribe(new Subscriber<Integer>() {
            private boolean onNextCalled;

            @Override
            public void onSubscribe(Subscription s) {
                assertTrue(subRef.compareAndSet(null, s));
            }

            @Override
            public void onNext(Integer value) {
                try {
                    assertFalse(onNextCalled);
                    assertFalse(onErrorCalled.get());
                } catch (AssertionError e) {
                    assertErrorRef.compareAndSet(null, e);
                }

                onNextCalled = true;
                throw DELIBERATE_EXCEPTION;
            }

            @Override
            public void onError(Throwable t) {
                try {
                    assertTrue(onNextCalled);
                    assertFalse(onErrorCalled.get());
                    assertSame(DELIBERATE_EXCEPTION, t);
                } catch (AssertionError e) {
                    assertErrorRef.compareAndSet(null, e);
                }
                onErrorCalled.set(true);
            }

            @Override
            public void onComplete() {
                try {
                    fail();
                } catch (AssertionError e) {
                    assertErrorRef.compareAndSet(null, e);
                }
            }
        });

        Subscription subscription = subRef.get();
        assertNotNull(subscription);
        if (!requestLate) {
            subscription.request(1);
        }
        assertTrue(channel.writeInbound(1));
        if (requestLate) {
            subscription.request(1);
        }
        assertTrue(channel.writeInbound(2));

        AssertionError err = assertErrorRef.get();
        if (err != null) {
            throw err;
        }
        assertTrue(onErrorCalled.get());
    }

    private void fireChannelRead(boolean checkReadRequested, int... items) {
        if (checkReadRequested) {
            assertThat("No more reads requested by the handler.", readRequested, is(true));
        }
        for (int item : items) {
            handler.channelRead(handlerCtx, item);
        }
        readRequested = false;
        handler.channelReadComplete(handlerCtx);
    }

    private void fireChannelRead(int... items) {
        fireChannelRead(true, items);
    }

    private void fireChannelReadToBuffer(int... items) {
        fireChannelRead(false, items);
    }
}

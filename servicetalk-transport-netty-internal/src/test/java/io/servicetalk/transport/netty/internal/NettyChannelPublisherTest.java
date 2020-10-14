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

import io.servicetalk.concurrent.Cancellable;
import io.servicetalk.concurrent.PublisherSource.Subscriber;
import io.servicetalk.concurrent.PublisherSource.Subscription;
import io.servicetalk.concurrent.SingleSource;
import io.servicetalk.concurrent.api.Publisher;
import io.servicetalk.concurrent.api.TestCollectingPublisherSubscriber;
import io.servicetalk.concurrent.api.TestPublisherSubscriber;
import io.servicetalk.concurrent.internal.DuplicateSubscribeException;
import io.servicetalk.concurrent.internal.ServiceTalkTestTimeout;
import io.servicetalk.transport.api.ConnectionInfo.Protocol;
import io.servicetalk.transport.netty.internal.NoopTransportObserver.NoopConnectionObserver;

import io.netty.channel.ChannelDuplexHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelOutboundHandlerAdapter;
import io.netty.channel.ChannelPromise;
import io.netty.channel.socket.ChannelInputShutdownReadComplete;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.Timeout;
import org.mockito.Mockito;

import java.nio.channels.ClosedChannelException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Predicate;
import java.util.stream.IntStream;
import javax.annotation.Nullable;

import static io.servicetalk.buffer.netty.BufferAllocators.DEFAULT_ALLOCATOR;
import static io.servicetalk.concurrent.api.Executors.immediate;
import static io.servicetalk.concurrent.api.SourceAdapters.toSource;
import static io.servicetalk.concurrent.internal.DeliberateException.DELIBERATE_EXCEPTION;
import static io.servicetalk.concurrent.internal.ServiceTalkTestTimeout.DEFAULT_TIMEOUT_SECONDS;
import static io.servicetalk.concurrent.internal.TerminalNotification.complete;
import static io.servicetalk.transport.netty.internal.CloseHandler.UNSUPPORTED_PROTOCOL_CLOSE_HANDLER;
import static io.servicetalk.transport.netty.internal.FlushStrategies.defaultFlushStrategy;
import static io.servicetalk.transport.netty.internal.OffloadAllExecutionStrategy.OFFLOAD_ALL_STRATEGY;
import static java.util.Objects.requireNonNull;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.instanceOf;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;
import static org.hamcrest.Matchers.sameInstance;
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

    private final TestPublisherSubscriber<Integer> subscriber = new TestPublisherSubscriber<>();
    private final TestPublisherSubscriber<Integer> subscriber2 = new TestPublisherSubscriber<>();
    private Publisher<Integer> publisher;
    private EmbeddedDuplexChannel channel;
    private boolean nextItemTerminal;
    private boolean readRequested;

    @Before
    public void setUp() throws Exception {
        setUp(integer -> nextItemTerminal);
    }

    public void setUp(Predicate<Integer> terminalPredicate) throws Exception {
        channel = new EmbeddedDuplexChannel(false);
        NettyConnection<Integer, Object> connection =
                DefaultNettyConnection.<Integer, Object>initChannel(channel, DEFAULT_ALLOCATOR,
            immediate(), terminalPredicate, UNSUPPORTED_PROTOCOL_CLOSE_HANDLER, defaultFlushStrategy(), null, channel ->
                                channel.pipeline().addLast(new ChannelOutboundHandlerAdapter() {
                @Override
                public void read(ChannelHandlerContext ctx) throws Exception {
                    readRequested = true;
                    super.read(ctx);
                }
            }), OFFLOAD_ALL_STRATEGY, mock(Protocol.class), NoopConnectionObserver.INSTANCE, true).toFuture().get();
        publisher = connection.read();
        channel.config().setAutoRead(false);
    }

    @After
    public void tearDown() throws Exception {
        if (!channel.close().await(DEFAULT_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
            throw new IllegalStateException("Channel close not finished in 1 second.");
        }
    }

    private void setupFireReadOnCloseEvents() throws Exception {
        if (channel != null) {
            channel.close();
        }
        channel = new EmbeddedDuplexChannel(false);
        NettyConnection<Integer, Object> connection = DefaultNettyConnection.initChannel(channel, DEFAULT_ALLOCATOR,
                immediate(), (Integer obj) -> false, UNSUPPORTED_PROTOCOL_CLOSE_HANDLER,
                defaultFlushStrategy(), null, channel -> {
                    channel.pipeline().addLast(new ChannelDuplexHandler() {
                        @Override
                        public void userEventTriggered(ChannelHandlerContext ctx, Object evt) throws Exception {
                            if (evt == ChannelInputShutdownReadComplete.INSTANCE) {
                                ctx.fireChannelRead(10);
                            }
                            ctx.fireUserEventTriggered(evt);
                        }

                        @Override
                        public void channelInactive(ChannelHandlerContext ctx) throws Exception {
                            ctx.fireChannelRead(11);
                            ctx.fireChannelInactive();
                        }

                        @Override
                        public void close(ChannelHandlerContext ctx, ChannelPromise promise) throws Exception {
                            ctx.fireChannelRead(12);
                            ctx.close(promise);
                        }
                    });
                    channel.pipeline().addLast(new ChannelOutboundHandlerAdapter() {
                        @Override
                        public void read(ChannelHandlerContext ctx) throws Exception {
                            readRequested = true;
                            super.read(ctx);
                        }
                    });
                }, OFFLOAD_ALL_STRATEGY, mock(Protocol.class), NoopConnectionObserver.INSTANCE, true).toFuture().get();
        publisher = connection.read();
        channel.config().setAutoRead(false);
    }

    @Test
    public void testNettyHandlerSendsQueuedDataOnShutdownInputFromCancel() throws Exception {
        testCancelThenResubscribeDeliversErrorAndNotQueuedData(false, true);
    }

    @Test
    public void testNettyHandlerSendsQueuedDataOnShutdownInputFromClose() throws Exception {
        testCancelThenResubscribeDeliversErrorAndNotQueuedData(true, true);
    }

    @Test
    public void testCancelThenReadThenResubscribeDeliversErrorAndNotQueuedData() throws Exception {
        testCancelThenResubscribeDeliversErrorAndNotQueuedData(true, false);
    }

    @Test
    public void testCancelThenResubscribeDeliversErrorAndNotQueuedData() throws Exception {
        testCancelThenResubscribeDeliversErrorAndNotQueuedData(false, false);
    }

    private void testCancelThenResubscribeDeliversErrorAndNotQueuedData(boolean doChannelRead,
                                                                        boolean setupFireReadOnClose) throws Exception {
        if (setupFireReadOnClose) {
            setupFireReadOnCloseEvents();
        }
        TestCollectingPublisherSubscriber<Integer> subscriber1 = new TestCollectingPublisherSubscriber<>();
        TestCollectingPublisherSubscriber<Integer> subscriber2 = new TestCollectingPublisherSubscriber<>();
        toSource(publisher).subscribe(subscriber1);
        Subscription subscription1 = subscriber1.awaitSubscription();
        subscription1.request(1);

        assertFalse(channel.writeInbound(1));
        Integer next = subscriber1.takeOnNext();
        assertThat(next, is(1));
        assertFalse(channel.writeInbound(2)); // this write should be queued, because there isn't any requestN demand.

        subscription1.cancel(); // cancel of active subscription should clear the queue and fail future Subscribers.

        if (doChannelRead) {
            try {
                assertFalse(channel.writeInbound(3));
            } catch (Exception e) {
                assertThat(e, instanceOf(ClosedChannelException.class));
                return;
            }
        }

        toSource(publisher).subscribe(subscriber2);
        subscriber2.awaitSubscription().request(Long.MAX_VALUE);
        assertThat(subscriber2.pollAllOnNext(), is(empty()));
        assertThat(subscriber2.awaitOnError(), is(instanceOf(ClosedChannelException.class)));
    }

    @Test
    public void testSupplyEqualsDemand() {
        toSource(publisher).subscribe(subscriber);
        subscriber.request(3);
        fireChannelRead(1, 2);
        assertThat(subscriber.takeItems(), contains(1, 2));
        nextItemTerminal = true;
        fireChannelRead(false, 3);
        assertThat(subscriber.takeItems(), contains(3));
        assertThat(subscriber.takeTerminal(), is(complete()));
    }

    @Test
    public void testSupplyLessThanDemand() {
        toSource(publisher).subscribe(subscriber);
        subscriber.request(4);
        fireChannelRead(1, 2);
        assertThat(subscriber.takeItems(), contains(1, 2));
        nextItemTerminal = true;
        fireChannelRead(false, 3);
        assertThat(subscriber.takeItems(), contains(3));
        assertThat(subscriber.takeTerminal(), is(complete()));
    }

    @Test
    public void testDemandLessThanSupply() {
        toSource(publisher).subscribe(subscriber);
        subscriber.request(1);
        fireChannelRead(1, 2);
        assertThat(subscriber.takeItems(), contains(1));
        subscriber.request(1);
        nextItemTerminal = true;
        fireChannelReadToBuffer(3);
        subscriber.request(1);
        assertThat(subscriber.takeItems(), contains(2, 3));
        assertThat(subscriber.takeTerminal(), is(complete()));
    }

    @Test
    public void testBufferDrainOnClose() throws Exception {
        toSource(publisher).subscribe(subscriber);
        fireChannelReadToBuffer(1, 2);
        channel.close().await();
        subscriber.request(2);
        assertThat(subscriber.takeItems(), contains(1, 2));
        assertThat(subscriber.takeError(), instanceOf(ClosedChannelException.class));
    }

    @Test
    public void testErrorBufferedWithExactDemand() {
        toSource(publisher).subscribe(subscriber);
        subscriber.request(1);
        fireChannelRead(1, 2);
        channel.pipeline().fireExceptionCaught(DELIBERATE_EXCEPTION);
        assertThat(subscriber.takeItems(), contains(1));
        subscriber.request(1);
        assertThat(subscriber.takeItems(), contains(2));
        assertThat(subscriber.takeError(), sameInstance(DELIBERATE_EXCEPTION));
    }

    @Test
    public void testErrorBufferedWithMoreDemand() {
        toSource(publisher).subscribe(subscriber);
        subscriber.request(1);
        fireChannelRead(1, 2);
        channel.pipeline().fireExceptionCaught(DELIBERATE_EXCEPTION);
        assertThat(subscriber.takeItems(), contains(1));
        subscriber.request(2);
        assertThat(subscriber.takeItems(), contains(2));
        assertThat(subscriber.takeError(), sameInstance(DELIBERATE_EXCEPTION));
    }

    @Test
    public void testErrorWithNoDemandNoBuffer() {
        toSource(publisher).subscribe(subscriber);
        channel.pipeline().fireExceptionCaught(DELIBERATE_EXCEPTION);
        assertThat(subscriber.takeError(), sameInstance(DELIBERATE_EXCEPTION));
    }

    @Test
    public void testErrorWithNoDemandAndBuffer() {
        toSource(publisher).subscribe(subscriber);
        fireChannelReadToBuffer(1);
        channel.pipeline().fireExceptionCaught(DELIBERATE_EXCEPTION);
        subscriber.request(1);
        assertThat(subscriber.takeItems(), contains(1));
        assertThat(subscriber.takeError(), sameInstance(DELIBERATE_EXCEPTION));
    }

    @Test
    public void testErrorNoEmission() {
        toSource(publisher).subscribe(subscriber);
        subscriber.request(1);
        channel.pipeline().fireExceptionCaught(DELIBERATE_EXCEPTION);
        assertThat(subscriber.takeError(), sameInstance(DELIBERATE_EXCEPTION));
    }

    @Test
    public void testConcurrentSubscribers() {
        toSource(publisher).subscribe(subscriber);
        subscriber.request(1);
        fireChannelRead(1);
        assertThat(subscriber.takeItems(), contains(1));

        @SuppressWarnings("unchecked")
        Subscriber<Integer> sub2 = mock(Subscriber.class);
        toSource(publisher).subscribe(sub2);
        verify(sub2).onSubscribe(any(Subscription.class));
        verify(sub2).onError(any(DuplicateSubscribeException.class));
        verifyNoMoreInteractions(sub2);

        nextItemTerminal = true;
        fireChannelReadToBuffer(2);
        subscriber.request(1);
        assertThat(subscriber.takeItems(), contains(2));
        assertThat(subscriber.takeTerminal(), is(complete()));
    }

    @Test
    public void testSequentialSubscriptionsNoCarryOverDemand() {
        nextItemTerminal = true;
        toSource(publisher).subscribe(subscriber);
        subscriber.request(1);
        fireChannelRead(1);
        assertThat(subscriber.takeItems(), contains(1));
        assertThat(subscriber.takeTerminal(), is(complete()));

        toSource(publisher).subscribe(subscriber2);
        subscriber2.request(1);
        fireChannelRead(2);
        assertThat(subscriber2.takeItems(), contains(2));
        assertThat(subscriber2.takeTerminal(), is(complete()));
    }

    @Test
    public void testSequentialSubscriptionsCarryOverDemand() {
        nextItemTerminal = true;
        toSource(publisher).subscribe(subscriber);
        subscriber.request(3);
        fireChannelRead(1);
        assertThat(subscriber.takeItems(), contains(1));
        assertThat(subscriber.takeTerminal(), is(complete()));

        nextItemTerminal = false;
        assertThat("Unexpected read requested from the channel.", readRequested, is(false));
        toSource(publisher).subscribe(subscriber2);
        subscriber2.request(2);
        fireChannelRead(2);
        nextItemTerminal = true;
        fireChannelRead(false, 3);
        assertThat(subscriber2.takeItems(), contains(2, 3));
        assertThat(subscriber2.takeTerminal(), is(complete()));
    }

    @Test
    public void testBufferBetweenSubscriptions() {
        nextItemTerminal = true;
        toSource(publisher).subscribe(subscriber);
        subscriber.request(3);
        fireChannelRead(1);
        assertThat(subscriber.takeItems(), contains(1));
        assertThat(subscriber.takeTerminal(), is(complete()));

        fireChannelReadToBuffer(2);
        toSource(publisher).subscribe(subscriber2);
        subscriber2.request(3);
        assertThat(subscriber2.takeItems(), contains(2));
        assertThat(subscriber2.takeTerminal(), is(complete()));
    }

    @Test
    public void testCancelBeforeTerminal() {
        toSource(publisher).subscribe(subscriber);
        subscriber.request(3);
        fireChannelRead(1);
        assertThat(subscriber.takeItems(), contains(1));
        subscriber.cancel();
        assertThat("Channel not closed post cancel.", channel.closeFuture().isDone(), is(true));
    }

    @Test
    public void testCancelAfterTerminal() {
        toSource(publisher).subscribe(subscriber);
        subscriber.request(3);
        nextItemTerminal = true;
        fireChannelRead(1);
        assertThat(subscriber.takeItems(), contains(1));
        assertThat(subscriber.takeTerminal(), is(complete()));
        subscriber.cancel();
        assertThat("Channel closed on cancel post terminate.", channel.closeFuture().isDone(), is(false));
    }

    @Test
    public void testDelayedCancel() {
        nextItemTerminal = true;
        toSource(publisher).subscribe(subscriber);
        subscriber.request(3);
        final Subscription firstSubscription = requireNonNull(subscriber.subscription());
        fireChannelRead(1);
        assertThat(subscriber.takeItems(), contains(1));
        assertThat(subscriber.takeTerminal(), is(complete()));

        nextItemTerminal = false;
        fireChannelReadToBuffer(2);
        toSource(publisher).subscribe(subscriber2);
        subscriber2.request(3);
        assertThat(subscriber2.takeItems(), contains(2));

        firstSubscription.cancel();

        nextItemTerminal = true;
        fireChannelRead(3);
        assertThat(subscriber2.takeItems(), contains(3));
    }

    @Test
    public void testDelayedRequestN() {
        nextItemTerminal = true;
        toSource(publisher).subscribe(subscriber);
        subscriber.request(3);
        final Subscription firstSubscription = subscriber.subscription();
        fireChannelRead(1);
        assertThat(subscriber.takeItems(), contains(1));
        assertThat(subscriber.takeTerminal(), is(complete()));

        fireChannelReadToBuffer(2);
        toSource(publisher).subscribe(subscriber2);
        assertTrue(subscriber2.subscriptionReceived());
        assertThat(subscriber2.takeItems(), hasSize(0));
        assertThat(subscriber2.takeTerminal(), nullValue());

        //noinspection ConstantConditions
        firstSubscription.request(3);

        assertTrue(subscriber2.subscriptionReceived());
        assertThat(subscriber2.takeItems(), hasSize(0));
        assertThat(subscriber2.takeTerminal(), nullValue());
        subscriber2.request(1);
        assertThat(subscriber2.takeItems(), contains(2));
        assertThat(subscriber2.takeTerminal(), is(complete()));
    }

    @Test
    public void testEmitItemsWithNoSubscriber() {
        nextItemTerminal = true;
        fireChannelReadToBuffer(1);
        toSource(publisher).subscribe(subscriber);
        subscriber.request(1);
        assertThat(subscriber.takeItems(), contains(1));
        assertThat(subscriber.takeTerminal(), is(complete()));
    }

    @Test
    public void testCancelFromWithinComplete() {
        final AtomicReference<Object> resultRef = new AtomicReference<>();
        fireChannelReadToBuffer(1);
        nextItemTerminal = true;
        toSource(publisher.firstOrElse(() -> null)).subscribe(new SingleSource.Subscriber<Integer>() {
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
        toSource(publisher).subscribe(subscriber);
        assertThat(subscriber.takeError(), instanceOf(ClosedChannelException.class));
    }

    @Test
    public void testTwoSubscribersPostChannelClose() throws Exception {
        channel.close().await();
        toSource(publisher).subscribe(subscriber);
        assertThat(subscriber.takeError(), instanceOf(ClosedChannelException.class));
        @SuppressWarnings("unchecked")
        Subscriber<Integer> mock = Mockito.mock(Subscriber.class);
        toSource(publisher).subscribe(mock);
        verify(mock).onSubscribe(any());
        verify(mock).onError(any(ClosedChannelException.class));
        verifyNoMoreInteractions(mock);
    }

    @Test
    public void testQueuedCompleteAndFatalErrorExactDemand() {
        toSource(publisher).subscribe(subscriber);
        nextItemTerminal = true;
        fireChannelReadToBuffer(1);
        channel.close();
        subscriber.request(1);
        assertThat(subscriber.takeItems(), contains(1));
        assertThat(subscriber.takeTerminal(), is(complete()));
    }

    @Test
    public void testQueuedCompleteAndFatalErrorMoreDemand() {
        toSource(publisher).subscribe(subscriber);
        nextItemTerminal = true;
        fireChannelReadToBuffer(1);
        channel.close();
        subscriber.request(2);
        assertThat(subscriber.takeItems(), contains(1));
        assertThat(subscriber.takeTerminal(), is(complete()));
    }

    @Test
    public void testQueuedErrorAndFatalErrorExactDemand() {
        toSource(publisher).subscribe(subscriber);
        fireChannelReadToBuffer(1);
        channel.pipeline().fireExceptionCaught(DELIBERATE_EXCEPTION);
        channel.close();
        subscriber.request(1);
        assertThat(subscriber.takeItems(), contains(1));
        assertThat(subscriber.takeError(), sameInstance(DELIBERATE_EXCEPTION));
    }

    @Test
    public void testQueuedErrorAndFatalErrorMoreDemand() {
        toSource(publisher).subscribe(subscriber);
        fireChannelReadToBuffer(1);
        channel.pipeline().fireExceptionCaught(DELIBERATE_EXCEPTION);
        channel.close();
        subscriber.request(2);
        assertThat(subscriber.takeItems(), contains(1));
        assertThat(subscriber.takeError(), sameInstance(DELIBERATE_EXCEPTION));
    }

    @Test
    public void invalidRequestNDoesCancel() {
        toSource(publisher).subscribe(subscriber);
        fireChannelReadToBuffer(1, 2, 3);
        subscriber.request(1);
        assertThat(subscriber.takeItems(), contains(1));
        subscriber.request(-1);
        assertThat(subscriber.takeError(), instanceOf(IllegalArgumentException.class));
        subscriber.request(2);
        assertTrue(subscriber.subscriptionReceived());
        assertThat(subscriber.takeItems(), hasSize(0));
        assertThat(subscriber.takeTerminal(), nullValue());
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

    @Test
    public void resubscribePostErrorEmitsError() {
        toSource(publisher).subscribe(subscriber);
        fireChannelReadToBuffer(1, 2, 3);
        subscriber.request(3);
        assertThat(subscriber.takeItems(), contains(1, 2, 3));
        channel.pipeline().fireExceptionCaught(DELIBERATE_EXCEPTION);
        assertThat(subscriber.takeError(), sameInstance(DELIBERATE_EXCEPTION));
        assertFalse(channel.isActive());
        assertFalse(channel.isOpen());

        AtomicReference<Throwable> exRef = new AtomicReference<>();
        publisher.beforeOnError(exRef::set).forEach(__ -> { });
        assertThat("Subscriber active post channel error.", exRef.get(),
                is(instanceOf(ClosedChannelException.class)));
    }

    @Test
    public void testSubscribeAndRequestWithinOnCompleteWithPendingData() throws Exception {
        // With data queued up for multiple payloads in NettyChannelPublisher, when an existing subscriber terminates
        // and a new Subscriber requests data synchronously from the previous terminal signal, the demand should be
        // fulfilled synchronously using the pending data.

        setUp(i -> i == 2 || i == 4); // rewire pipeline, terminal for 2 and 4

        // queue up payloads
        fireChannelReadToBuffer(1, 2);
        fireChannelReadToBuffer(3, 4);

        toSource(publisher.
                afterFinally(() -> {
                    // re-subscribing from the previous completion event
                    toSource(publisher).subscribe(subscriber2);
                    subscriber2.request(2);
                })).subscribe(subscriber);
        subscriber.request(2);

        assertThat(subscriber.takeItems(), contains(1, 2));
        assertThat(subscriber.takeTerminal(), is(complete()));
        assertThat(subscriber2.takeItems(), contains(3, 4));
        assertThat(subscriber2.takeTerminal(), is(complete()));
    }

    @Test
    public void testSubscribeAndRequestWithinOnErrorWithPendingData() {
        // With data and a fatal error queued up in NettyChannelPublisher, when an existing subscriber terminates and a
        // new Subscriber requests data synchronously from the previous terminal signal, the demand should trigger
        // observing a ClosedChannelException.

        // queue up data and trigger terminal failure
        fireChannelReadToBuffer(1);
        channel.pipeline().fireExceptionCaught(DELIBERATE_EXCEPTION);

        toSource(publisher
                .afterFinally(() -> {
                    // re-subscribing from the previous completion event
                    toSource(publisher).subscribe(subscriber2);
                    subscriber2.request(1);
                })).subscribe(subscriber);
        subscriber.request(2);

        assertThat(subscriber.takeItems(), contains(1));
        assertThat(subscriber.takeError(), sameInstance(DELIBERATE_EXCEPTION));
        // only the active subscriber sees the initial exception, subsequent subscribers will observe a closed channel
        assertThat(subscriber2.takeError(), instanceOf(ClosedChannelException.class));
    }

    private void testChannelReadThrows(boolean requestLate) {
        final AtomicBoolean onErrorCalled = new AtomicBoolean();
        final AtomicReference<Subscription> subRef = new AtomicReference<>();
        final AtomicReference<AssertionError> assertErrorRef = new AtomicReference<>();

        toSource(publisher).subscribe(new Subscriber<Integer>() {
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
        assertFalse(channel.writeInbound(1));
        if (requestLate) {
            subscription.request(1);
        }
        assertFalse(channel.isActive());
        assertFalse(channel.isOpen());

        AssertionError err = assertErrorRef.get();
        if (err != null) {
            throw err;
        }
        assertTrue(onErrorCalled.get());
    }

    private void fireChannelRead(boolean readRequestExpected, int... items) {
        if (readRequestExpected) {
            assertThat("Expected but did not receive a channel read() after the last invocation of " +
                    "channelReadComplete().", readRequested, is(true));
        } else {
            assertThat("Received an unexpected channel read() after the last invocation of channelReadComplete().",
                    readRequested, is(false));
        }
        channel.writeInbound(IntStream.of(items).boxed().toArray(Object[]::new));
        readRequested = false;
    }

    private void fireChannelRead(int... items) {
        fireChannelRead(true, items);
    }

    private void fireChannelReadToBuffer(int... items) {
        fireChannelRead(false, items);
    }
}

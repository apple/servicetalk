/*
 * Copyright © 2020-2021 Apple Inc. and the ServiceTalk project authors
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

import io.servicetalk.concurrent.CompletableSource;
import io.servicetalk.concurrent.PublisherSource;
import io.servicetalk.concurrent.PublisherSource.Subscription;
import io.servicetalk.concurrent.api.Completable;
import io.servicetalk.concurrent.api.Executor;
import io.servicetalk.concurrent.api.Executors;
import io.servicetalk.concurrent.api.Publisher;
import io.servicetalk.concurrent.api.Single;
import io.servicetalk.concurrent.api.TestCompletable;
import io.servicetalk.concurrent.api.TestPublisher;
import io.servicetalk.concurrent.api.TestSubscription;
import io.servicetalk.concurrent.test.internal.TestPublisherSubscriber;
import io.servicetalk.transport.api.ConnectionInfo.Protocol;
import io.servicetalk.transport.api.DefaultExecutionContext;
import io.servicetalk.transport.api.ExecutionContext;
import io.servicetalk.transport.api.ExecutionStrategy;
import io.servicetalk.transport.api.RetryableException;
import io.servicetalk.transport.netty.internal.NoopTransportObserver.NoopConnectionObserver;

import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.stubbing.Answer;

import java.nio.channels.ClosedChannelException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static io.servicetalk.buffer.netty.BufferAllocators.DEFAULT_ALLOCATOR;
import static io.servicetalk.concurrent.api.Completable.completed;
import static io.servicetalk.concurrent.api.Executors.immediate;
import static io.servicetalk.concurrent.api.SourceAdapters.toSource;
import static io.servicetalk.concurrent.internal.DeliberateException.DELIBERATE_EXCEPTION;
import static io.servicetalk.concurrent.internal.SubscriberUtils.deliverCompleteFromSource;
import static io.servicetalk.transport.netty.internal.CloseHandler.UNSUPPORTED_PROTOCOL_CLOSE_HANDLER;
import static io.servicetalk.transport.netty.internal.FlushStrategies.defaultFlushStrategy;
import static io.servicetalk.transport.netty.internal.NettyIoExecutors.fromNettyEventLoop;
import static java.lang.Integer.MAX_VALUE;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.instanceOf;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class NettyPipelinedConnectionTest {
    private final TestPublisherSubscriber<Integer> readSubscriber = new TestPublisherSubscriber<>();
    private final TestPublisherSubscriber<Integer> readSubscriber2 = new TestPublisherSubscriber<>();
    private TestPublisher<Integer> writePublisher1;
    private TestPublisher<Integer> writePublisher2;
    private NettyPipelinedConnection<Integer, Integer> requester;
    private EmbeddedDuplexChannel channel;

    @BeforeEach
    void setUp() throws Exception {
        ExecutionContextUtils.clearThreadLocal();
        channel = new EmbeddedDuplexChannel(false);
        WriteDemandEstimator demandEstimator = mock(WriteDemandEstimator.class);
        writePublisher1 = new TestPublisher<>();
        writePublisher2 = new TestPublisher<>();
        when(demandEstimator.estimateRequestN(anyLong())).then(invocation1 -> MAX_VALUE);
        CloseHandler closeHandler = UNSUPPORTED_PROTOCOL_CLOSE_HANDLER;
        ExecutionStrategy executionStrategy = () -> true;
        ExecutionContext<?> executionContext = new DefaultExecutionContext<>(DEFAULT_ALLOCATOR,
                fromNettyEventLoop(channel.eventLoop(), false), immediate(), executionStrategy);
        final DefaultNettyConnection<Integer, Integer> connection =
                DefaultNettyConnection.<Integer, Integer>initChannel(channel, executionContext,
                closeHandler, defaultFlushStrategy(), 0L, null, channel2 -> {
                    channel2.pipeline().addLast(new ChannelInboundHandlerAdapter() {
                        @Override
                        public void channelRead(ChannelHandlerContext ctx, Object msg) {
                            ctx.fireChannelRead(msg);
                            closeHandler.protocolPayloadEndInbound(ctx);
                        }
                    });
                }, mock(Protocol.class), NoopConnectionObserver.INSTANCE, true, __ -> false)
                        .toFuture().get();
        requester = new NettyPipelinedConnection<>(connection, 8);
    }

    @Test
    void pipelinedWriteAndReadCompleteSequential() {
        toSource(requester.write(writePublisher1)).subscribe(readSubscriber);
        readSubscriber.awaitSubscription().request(1);
        toSource(requester.write(writePublisher2)).subscribe(readSubscriber2);
        assertTrue(writePublisher1.isSubscribed());
        assertFalse(writePublisher2.isSubscribed());
        writePublisher1.onNext(1);
        writePublisher1.onComplete();
        channel.writeInbound(1);
        Integer next = readSubscriber.takeOnNext();
        assertNotNull(next);
        assertEquals(1, next.intValue());
        readSubscriber.awaitOnComplete();

        readSubscriber2.awaitSubscription().request(1);
        writePublisher2.onNext(1);
        writePublisher2.onComplete();
        channel.writeInbound(2);
        next = readSubscriber2.takeOnNext();
        assertNotNull(next);
        assertEquals(2, next.intValue());
        readSubscriber2.awaitOnComplete();
    }

    @Test
    void pipelinedWritesCompleteBeforeReads() {
        toSource(requester.write(writePublisher1)).subscribe(readSubscriber);
        readSubscriber.awaitSubscription().request(1);
        toSource(requester.write(writePublisher2)).subscribe(readSubscriber2);
        assertTrue(writePublisher1.isSubscribed());
        assertFalse(writePublisher2.isSubscribed());
        writePublisher1.onNext(1);
        writePublisher1.onComplete();
        Integer channelWrite = channel.readOutbound();
        assertNotNull(channelWrite);
        assertEquals(1, channelWrite.intValue());

        assertTrue(writePublisher2.isSubscribed());
        writePublisher2.onNext(2);
        writePublisher2.onComplete();
        channelWrite = channel.readOutbound();
        assertNotNull(channelWrite);
        assertEquals(2, channelWrite.intValue());

        channel.writeInbound(1);
        channel.writeInbound(2); // write before the second subscribe to test queuing works properly
        Integer next = readSubscriber.takeOnNext();
        assertNotNull(next);
        assertEquals(1, next.intValue());
        readSubscriber.awaitOnComplete();

        Subscription subscription2 = readSubscriber2.awaitSubscription();
        subscription2.request(1);
        next = readSubscriber2.takeOnNext();
        assertNotNull(next);
        assertEquals(2, next.intValue());
        readSubscriber2.awaitOnComplete();
    }

    @Test
    void fourPipelinedWritesCompleteBeforeAnyRead() {
        final int requests = 4;
        final List<TestPublisher<Integer>> writePublishers = new ArrayList<>(requests);
        final List<TestPublisherSubscriber<Integer>> readSubscribers = new ArrayList<>(requests);
        for (int i = 0; i < requests; i++) {
            TestPublisher<Integer> writePublisher = new TestPublisher<>();
            TestPublisherSubscriber<Integer> subscriber = new TestPublisherSubscriber<>();
            writePublishers.add(writePublisher);
            readSubscribers.add(subscriber);
            toSource(requester.write(writePublisher)).subscribe(subscriber);
        }
        // Only the first write task runs on subscribe; the rest are handed a Subscription as the preceding write
        // completes.
        assertTrue(writePublishers.get(0).isSubscribed());
        for (int i = 1; i < requests; i++) {
            assertFalse(writePublishers.get(i).isSubscribed());
        }

        for (int i = 0; i < requests; i++) {
            TestPublisher<Integer> writePublisher = writePublishers.get(i);
            assertTrue(writePublisher.isSubscribed());
            readSubscribers.get(i).awaitSubscription().request(1);
            writePublisher.onNext(i);
            writePublisher.onComplete();
            Integer written = channel.readOutbound();
            assertNotNull(written);
            assertEquals(i, written.intValue());
        }

        for (int i = 0; i < requests; i++) {
            channel.writeInbound(i);
        }
        for (int i = 0; i < requests; i++) {
            TestPublisherSubscriber<Integer> subscriber = readSubscribers.get(i);
            Integer next = subscriber.takeOnNext();
            assertNotNull(next);
            assertEquals(i, next.intValue());
            subscriber.awaitOnComplete();
        }
    }

    @Test
    void responsesMatchRequestsWhenWritesCompleteOnSubscribe() {
        // Holding the first write open lets the rest queue behind it, so releasing it drains them in one nested
        // cascade. Responses must still match request order, not the order the reads get subscribed.
        final int requests = 4;
        final List<TestPublisherSubscriber<Integer>> readSubscribers = new ArrayList<>(requests);
        final TestPublisher<Integer> heldWrite = new TestPublisher<>();
        final TestPublisherSubscriber<Integer> firstSubscriber = new TestPublisherSubscriber<>();
        readSubscribers.add(firstSubscriber);
        toSource(requester.write(heldWrite)).subscribe(firstSubscriber);
        for (int i = 1; i < requests; i++) {
            TestPublisherSubscriber<Integer> subscriber = new TestPublisherSubscriber<>();
            readSubscribers.add(subscriber);
            toSource(requester.write(Publisher.from(i))).subscribe(subscriber);
        }

        firstSubscriber.awaitSubscription().request(1);
        heldWrite.onNext(0);
        heldWrite.onComplete();

        for (int i = 0; i < requests; i++) {
            Integer written = channel.readOutbound();
            assertNotNull(written);
            assertEquals(i, written.intValue());
        }
        for (int i = 1; i < requests; i++) {
            readSubscribers.get(i).awaitSubscription().request(1);
        }
        for (int i = 0; i < requests; i++) {
            channel.writeInbound(i);
        }
        for (int i = 0; i < requests; i++) {
            TestPublisherSubscriber<Integer> subscriber = readSubscribers.get(i);
            Integer next = subscriber.takeOnNext();
            assertNotNull(next);
            assertEquals(i, next.intValue());
            subscriber.awaitOnComplete();
        }
    }

    @Test
    void pipelinedReadsCompleteBeforeWrites() {
        toSource(requester.write(writePublisher1)).subscribe(readSubscriber);
        readSubscriber.awaitSubscription().request(1);
        toSource(requester.write(writePublisher2)).subscribe(readSubscriber2);

        channel.writeInbound(1);
        channel.writeInbound(2); // write before the second subscribe to test queuing works properly
        Integer next = readSubscriber.takeOnNext();
        assertNotNull(next);
        assertEquals(1, next.intValue());
        // technically the read has completed here, but see the comment below about the merge operator.

        assertTrue(writePublisher1.isSubscribed());
        assertFalse(writePublisher2.isSubscribed());
        writePublisher1.onNext(1);
        writePublisher1.onComplete();
        // from a "full duplex" perspective this could be verified earlier after the first element is read because
        // the underlying transport read stream completes. however in order to provide visibility into write errors
        // to the user there is a merge operation which delays the completion of the returned response Publisher.
        readSubscriber.awaitOnComplete();

        // for pipelining we need to wait until the previous write completes before we can see anything on the next
        // read stream.
        readSubscriber2.awaitSubscription().request(1);
        next = readSubscriber2.takeOnNext();
        assertNotNull(next);
        assertEquals(2, next.intValue());
        // technically the read has completed here, but see the comment below about the merge operator.

        Integer channelWrite = channel.readOutbound();
        assertNotNull(channelWrite);
        assertEquals(1, channelWrite.intValue());

        assertTrue(writePublisher2.isSubscribed());
        writePublisher2.onNext(2);
        writePublisher2.onComplete();
        // from a "full duplex" perspective this could be verified earlier after the first element is read because
        // the underlying transport read stream completes. however in order to provide visibility into write errors
        // to the user there is a merge operation which delays the completion of the returned response Publisher.
        readSubscriber2.awaitOnComplete();

        channelWrite = channel.readOutbound();
        assertNotNull(channelWrite);
        assertEquals(2, channelWrite.intValue());
    }

    @Test
    void flushStrategy() {
        FlushStrategy flushStrategy1 = mock(FlushStrategy.class);
        FlushStrategy.WriteEventsListener writeEventsListener1 = mock(FlushStrategy.WriteEventsListener.class);
        AtomicReference<FlushStrategy.FlushSender> sender1Ref = new AtomicReference<>();
        doAnswer((Answer<FlushStrategy.WriteEventsListener>) invocation -> {
            sender1Ref.compareAndSet(null, invocation.getArgument(0, FlushStrategy.FlushSender.class));
            return writeEventsListener1;
        }).when(flushStrategy1).apply(any());

        FlushStrategy flushStrategy2 = mock(FlushStrategy.class);
        FlushStrategy.WriteEventsListener writeEventsListener2 = mock(FlushStrategy.WriteEventsListener.class);
        AtomicReference<FlushStrategy.FlushSender> sender2Ref = new AtomicReference<>();
        doAnswer((Answer<FlushStrategy.WriteEventsListener>) invocation -> {
            sender2Ref.compareAndSet(null, invocation.getArgument(0, FlushStrategy.FlushSender.class));
            return writeEventsListener2;
        }).when(flushStrategy2).apply(any());

        toSource(requester.write(writePublisher1, () -> flushStrategy1, WriteDemandEstimators::newDefaultEstimator))
                .subscribe(readSubscriber);
        toSource(requester.write(writePublisher2, () -> flushStrategy2, WriteDemandEstimators::newDefaultEstimator))
                .subscribe(readSubscriber2);
        readSubscriber.awaitSubscription().request(1);
        assertTrue(writePublisher1.isSubscribed());
        assertFalse(writePublisher2.isSubscribed());

        verify(writeEventsListener1).writeStarted();
        FlushStrategy.FlushSender sender1 = sender1Ref.get();
        assertNotNull(sender1);
        writePublisher1.onNext(1);
        sender1.flush();
        verify(writeEventsListener1).itemWritten(eq(1));
        writePublisher1.onComplete();
        verify(writeEventsListener1).writeTerminated();

        assertTrue(writePublisher2.isSubscribed());
        verify(writeEventsListener2).writeStarted();
        FlushStrategy.FlushSender sender2 = sender1Ref.get();
        assertNotNull(sender2);
        writePublisher2.onNext(2);
        sender2.flush();
        verify(writeEventsListener2).itemWritten(eq(2));
        writePublisher2.onComplete();
        verify(writeEventsListener2).writeTerminated();
    }

    @Test
    void readCancelErrorsPendingReadCancelsPendingWrite() throws Exception {
        TestSubscription writePublisher1Subscription = new TestSubscription();
        toSource(requester.write(writePublisher1
                .afterSubscription(() -> writePublisher1Subscription))).subscribe(readSubscriber);
        Subscription readSubscription = readSubscriber.awaitSubscription();
        readSubscription.request(1);
        toSource(requester.write(writePublisher2)).subscribe(readSubscriber2);

        assertTrue(writePublisher1.isSubscribed());
        readSubscription.cancel(); // cancelling an active read will close the connection.

        // readSubscriber was cancelled, so it may or may not terminate, but other sources that have not terminated
        // should be terminated, cancelled, or not subscribed.

        assertThat(readSubscriber2.awaitOnError(), is(instanceOf(ClosedChannelException.class)));
        writePublisher1Subscription.awaitCancelled();
        assertFalse(writePublisher2.isSubscribed());
        assertFalse(channel.isOpen());
    }

    @Test
    void cancellingQueuedResponseClosesOnlyAfterTheEarlierResponseFinishes() {
        // A response cancelled before its turn was never read, so the connection has to close before anything reads
        // again, but an earlier response that is still reading is healthy and must be allowed to finish.
        toSource(requester.write(writePublisher1)).subscribe(readSubscriber);
        readSubscriber.awaitSubscription().request(1);
        toSource(requester.write(writePublisher2)).subscribe(readSubscriber2);

        writePublisher1.onNext(1);
        writePublisher1.onComplete();
        assertTrue(writePublisher2.isSubscribed());

        readSubscriber2.awaitSubscription().cancel();
        assertTrue(channel.isOpen());

        channel.writeInbound(1);
        Integer next = readSubscriber.takeOnNext();
        assertNotNull(next);
        assertEquals(1, next.intValue());
        readSubscriber.awaitOnComplete();
        assertFalse(channel.isOpen());
    }

    @Test
    void cancellingQueuedResponseDoesNotStartALaterRead() {
        // Reads never terminate, so the read() count is the number of concurrently active read subscribers.
        AtomicInteger readSubscribes = new AtomicInteger();
        @SuppressWarnings("unchecked")
        NettyConnection<Integer, Integer> mockConnection = mock(NettyConnection.class);
        doAnswer((Answer<Publisher<Integer>>) invocation -> {
            readSubscribes.incrementAndGet();
            return Publisher.never();
        }).when(mockConnection).read();
        doAnswer((Answer<Completable>) invocation -> {
            Publisher<Integer> writePub = invocation.getArgument(0);
            return writePub.ignoreElements();
        }).when(mockConnection).write(any(), any(), any());
        // Completed, so the turn is the only thing that can hold the successor back.
        when(mockConnection.closeAsync()).thenReturn(completed());
        requester = new NettyPipelinedConnection<>(mockConnection, 8);

        List<TestPublisherSubscriber<Integer>> subscribers = new ArrayList<>(3);
        for (int i = 0; i < 3; i++) {
            TestPublisherSubscriber<Integer> subscriber = new TestPublisherSubscriber<>();
            subscribers.add(subscriber);
            toSource(requester.write(Publisher.empty())).subscribe(subscriber);
            subscriber.awaitSubscription().request(1);
        }
        assertThat("only the first response should be reading", readSubscribes.get(), is(1));

        subscribers.get(1).awaitSubscription().cancel();

        assertThat("a later response started reading over an earlier one", readSubscribes.get(), is(1));
    }

    @Test
    void cancellingQueuedResponseWaitsForTheCloseBeforeReleasingTheNextRead() {
        // The abandoned bytes are still on the wire, so the successor must wait for the close to complete, not just
        // to be requested.
        List<TestPublisher<Integer>> reads = new ArrayList<>();
        TestCompletable closeCompletable = new TestCompletable();
        @SuppressWarnings("unchecked")
        NettyConnection<Integer, Integer> mockConnection = mock(NettyConnection.class);
        doAnswer((Answer<Publisher<Integer>>) invocation -> {
            TestPublisher<Integer> read = new TestPublisher<>();
            reads.add(read);
            return read;
        }).when(mockConnection).read();
        doAnswer((Answer<Completable>) invocation -> {
            Publisher<Integer> writePub = invocation.getArgument(0);
            return writePub.ignoreElements();
        }).when(mockConnection).write(any(), any(), any());
        when(mockConnection.closeAsync()).thenReturn(closeCompletable);
        requester = new NettyPipelinedConnection<>(mockConnection, 8);

        List<TestPublisherSubscriber<Integer>> subscribers = new ArrayList<>(3);
        for (int i = 0; i < 3; i++) {
            TestPublisherSubscriber<Integer> subscriber = new TestPublisherSubscriber<>();
            subscribers.add(subscriber);
            toSource(requester.write(Publisher.empty())).subscribe(subscriber);
            subscriber.awaitSubscription().request(1);
        }
        assertThat(reads, hasSize(1));

        subscribers.get(1).awaitSubscription().cancel();
        reads.get(0).onComplete(); // the turn now belongs to the cancelled exchange
        assertThat("a later read started before the close completed", reads, hasSize(1));

        closeCompletable.onComplete();
        assertThat("the next read was not released once the close completed", reads, hasSize(2));
    }

    @Test
    void failedWriteSetupDoesNotReleaseTheNextReadEarly() {
        // The second exchange's composition throws while the first is still reading; the third must wait for the
        // first.
        List<TestPublisher<Integer>> reads = new ArrayList<>();
        AtomicInteger writes = new AtomicInteger();
        @SuppressWarnings("unchecked")
        NettyConnection<Integer, Integer> mockConnection = mock(NettyConnection.class);
        doAnswer((Answer<Publisher<Integer>>) invocation -> {
            TestPublisher<Integer> read = new TestPublisher<>();
            reads.add(read);
            return read;
        }).when(mockConnection).read();
        doAnswer((Answer<Completable>) invocation -> {
            if (writes.incrementAndGet() == 2) {
                throw DELIBERATE_EXCEPTION;
            }
            Publisher<Integer> writePub = invocation.getArgument(0);
            return writePub.ignoreElements();
        }).when(mockConnection).write(any(), any(), any());
        when(mockConnection.closeAsync()).thenReturn(completed());
        requester = new NettyPipelinedConnection<>(mockConnection, 8);

        TestPublisherSubscriber<Integer> first = new TestPublisherSubscriber<>();
        toSource(requester.write(Publisher.empty())).subscribe(first);
        first.awaitSubscription().request(1);
        assertThat(reads, hasSize(1));

        TestPublisherSubscriber<Integer> failed = new TestPublisherSubscriber<>();
        toSource(requester.write(Publisher.empty())).subscribe(failed);
        assertThat(failed.awaitOnError(), is(DELIBERATE_EXCEPTION));

        TestPublisherSubscriber<Integer> third = new TestPublisherSubscriber<>();
        toSource(requester.write(Publisher.empty())).subscribe(third);
        third.awaitSubscription().request(1);

        assertThat("a later read started while the first was still reading", reads, hasSize(1));
    }

    @Test
    void channelCloseErrorsPendingReadCancelsPendingWrite() throws Exception {
        TestSubscription writePublisher1Subscription = new TestSubscription();
        toSource(requester.write(writePublisher1
                .afterSubscription(() -> writePublisher1Subscription))).subscribe(readSubscriber);
        Subscription readSubscription = readSubscriber.awaitSubscription();
        readSubscription.request(1);
        toSource(requester.write(writePublisher2)).subscribe(readSubscriber2);

        assertTrue(writePublisher1.isSubscribed());
        assertFalse(writePublisher2.isSubscribed());

        channel.close();

        assertThat(readSubscriber.awaitOnError(), is(instanceOf(ClosedChannelException.class)));
        assertThat(readSubscriber2.awaitOnError(), is(instanceOf(ClosedChannelException.class)));
        writePublisher1Subscription.awaitCancelled();
        assertFalse(writePublisher2.isSubscribed());
    }

    @Test
    void readCancelClosesConnectionThenWriteDoesNotSubscribe() throws Exception {
        TestSubscription writePublisher1Subscription = new TestSubscription();
        toSource(requester.write(writePublisher1
                .afterSubscription(() -> writePublisher1Subscription))).subscribe(readSubscriber);
        Subscription readSubscription = readSubscriber.awaitSubscription();
        readSubscription.request(1);

        assertTrue(writePublisher1.isSubscribed());
        readSubscription.cancel(); // cancelling an active read will close the connection.

        // readSubscriber was cancelled, so it may or may not terminate, but other sources that have not terminated
        // should be terminated, cancelled, or not subscribed.

        writePublisher1Subscription.awaitCancelled();
        assertFalse(channel.isOpen());

        toSource(requester.write(writePublisher2)).subscribe(readSubscriber2);
        assertThat(readSubscriber2.awaitOnError(), is(instanceOf(ClosedChannelException.class)));
        assertFalse(writePublisher2.isSubscribed());
    }

    @Test
    void writeErrorFailsPendingReadsDoesNotSubscribeToPendingWrites() {
        toSource(requester.write(writePublisher1)).subscribe(readSubscriber);
        assertTrue(writePublisher1.isSubscribed());
        Subscription readSubscription = readSubscriber.awaitSubscription();
        readSubscription.request(1);
        toSource(requester.write(writePublisher2)).subscribe(readSubscriber2);

        writePublisher1.onError(DELIBERATE_EXCEPTION);
        final Throwable firstError = readSubscriber.awaitOnError();
        assertThat(firstError, instanceOf(RetryableException.class));
        assertThat(firstError.getCause(), is(DELIBERATE_EXCEPTION));
        final Throwable secondError = readSubscriber2.awaitOnError();
        assertThat(secondError, instanceOf(RetryableException.class));
        assertThat(secondError, instanceOf(ClosedChannelException.class));
        assertThat(secondError.getCause(), instanceOf(ClosedChannelException.class));
        assertTrue(writePublisher2.isSubscribed());
        assertFalse(channel.isOpen());
    }

    @Test
    void writeSubscribeThrowsLetsSubsequentRequestsThrough() {
        AtomicBoolean firstReadOperation = new AtomicBoolean();
        TestPublisher<Integer> mockReadPublisher1 = new TestPublisher<>();
        TestPublisher<Integer> mockReadPublisher2 = new TestPublisher<>();
        @SuppressWarnings("unchecked")
        NettyConnection<Integer, Integer> mockConnection = mock(NettyConnection.class);
        doAnswer((Answer<Publisher<Integer>>) invocation ->
                firstReadOperation.compareAndSet(false, true) ? mockReadPublisher1 : mockReadPublisher2
        ).when(mockConnection).read();
        doAnswer((Answer<Completable>) invocation -> new Completable() {
            @Override
            protected void handleSubscribe(final CompletableSource.Subscriber subscriber) {
                throw DELIBERATE_EXCEPTION;
            }
        }).when(mockConnection).write(eq(writePublisher1), any(), any());
        doAnswer((Answer<Completable>) invocation -> {
            Publisher<Integer> writePub = invocation.getArgument(0);
            return writePub.ignoreElements();
        }).when(mockConnection).write(eq(writePublisher2), any(), any());
        requester = new NettyPipelinedConnection<>(mockConnection, 2);
        toSource(requester.write(writePublisher1)).subscribe(readSubscriber);
        toSource(requester.write(writePublisher2)).subscribe(readSubscriber2);
        Subscription readSubscription = readSubscriber.awaitSubscription();
        readSubscription.request(1);

        assertTrue(mockReadPublisher1.isSubscribed());
        mockReadPublisher1.onError(newSecondException());
        assertThat(readSubscriber.awaitOnError(), is(DELIBERATE_EXCEPTION));
        assertFalse(writePublisher1.isSubscribed());

        verifySecondRequestProcessed(mockReadPublisher2, mockConnection);
    }

    @Test
    void readSubscribeThrowsWritesStillProcessed() {
        AtomicBoolean thrownError = new AtomicBoolean();
        Publisher<Integer> mockReadPublisher = new Publisher<Integer>() {
            @Override
            protected void handleSubscribe(final PublisherSource.Subscriber<? super Integer> subscriber) {
                if (thrownError.compareAndSet(false, true)) {
                    throw DELIBERATE_EXCEPTION;
                } else {
                    deliverCompleteFromSource(subscriber);
                }
            }
        };
        @SuppressWarnings("unchecked")
        NettyConnection<Integer, Integer> mockConnection = mock(NettyConnection.class);
        when(mockConnection.read()).thenReturn(mockReadPublisher);
        doAnswer((Answer<Completable>) invocation -> {
            Publisher<Integer> writePub = invocation.getArgument(0);
            return writePub.ignoreElements();
        }).when(mockConnection).write(any(), any(), any());
        requester = new NettyPipelinedConnection<>(mockConnection, 2);
        toSource(requester.write(writePublisher1)).subscribe(readSubscriber);
        toSource(requester.write(writePublisher2)).subscribe(readSubscriber2);
        Subscription readSubscription = readSubscriber.awaitSubscription();
        readSubscription.request(1);

        assertTrue(writePublisher1.isSubscribed());
        writePublisher1.onError(newSecondException());
        assertThat(readSubscriber.awaitOnError(), is(DELIBERATE_EXCEPTION));

        readSubscriber2.awaitSubscription();
        assertTrue(writePublisher2.isSubscribed());
        writePublisher2.onComplete();
        readSubscriber2.awaitOnComplete();
        verify(mockConnection, never()).closeAsync();
    }

    private static IllegalStateException newSecondException() {
        return new IllegalStateException("second exception shouldn't propagate");
    }

    @Test
    void writeThrowsClosesConnection() {
        TestPublisher<Integer> mockReadPublisher2 = new TestPublisher<>();
        @SuppressWarnings("unchecked")
        NettyConnection<Integer, Integer> mockConnection = mock(NettyConnection.class);
        doAnswer((Answer<Publisher<Integer>>) invocation -> mockReadPublisher2).when(mockConnection).read();
        doAnswer((Answer<Completable>) invocation -> {
            throw DELIBERATE_EXCEPTION;
        }).when(mockConnection).write(eq(writePublisher1), any(), any());
        doAnswer((Answer<Completable>) invocation -> {
            Publisher<Integer> writePub = invocation.getArgument(0);
            return writePub.ignoreElements();
        }).when(mockConnection).write(eq(writePublisher2), any(), any());
        when(mockConnection.closeAsync()).thenReturn(completed());
        requester = new NettyPipelinedConnection<>(mockConnection, 2);
        toSource(requester.write(writePublisher1)).subscribe(readSubscriber);
        toSource(requester.write(writePublisher2)).subscribe(readSubscriber2);
        Subscription readSubscription = readSubscriber.awaitSubscription();
        readSubscription.request(1);

        assertThat(readSubscriber.awaitOnError(), is(DELIBERATE_EXCEPTION));
        assertFalse(writePublisher1.isSubscribed());
        verify(mockConnection).closeAsync();
    }

    @Test
    void readThrowsClosesConnection() {
        @SuppressWarnings("unchecked")
        NettyConnection<Integer, Integer> mockConnection = mock(NettyConnection.class);
        doAnswer((Answer<Publisher<Integer>>) invocation -> {
            throw DELIBERATE_EXCEPTION;
        }).when(mockConnection).read();
        doAnswer((Answer<Completable>) invocation -> {
            Publisher<Integer> writePub = invocation.getArgument(0);
            return writePub.ignoreElements();
        }).when(mockConnection).write(any(), any(), any());
        when(mockConnection.closeAsync()).thenReturn(completed());
        requester = new NettyPipelinedConnection<>(mockConnection, 2);
        toSource(requester.write(writePublisher1)).subscribe(readSubscriber);
        Subscription readSubscription = readSubscriber.awaitSubscription();
        readSubscription.request(1);

        writePublisher1.onError(newSecondException());
        assertThat(readSubscriber.awaitOnError(), is(DELIBERATE_EXCEPTION));
        assertTrue(writePublisher1.isSubscribed());
        verify(mockConnection).closeAsync();
    }

    private void verifySecondRequestProcessed(TestPublisher<Integer> mockReadPublisher2,
                                              NettyConnection<Integer, Integer> mockConnection) {
        Subscription readSubscription2 = readSubscriber2.awaitSubscription();
        readSubscription2.request(1);
        assertTrue(writePublisher2.isSubscribed());
        writePublisher2.onComplete();
        mockReadPublisher2.onNext(2);
        mockReadPublisher2.onComplete();
        assertThat(readSubscriber2.takeOnNext(), is(2));
        readSubscriber2.awaitOnComplete();
        verify(mockConnection, never()).closeAsync();
    }

    @Test
    void cancelAfterResponseCompletesDoesNotCloseConnection() {
        // A cancel after the terminal signal must be a no-op. Cancelling from inside onComplete delivers it before
        // the read's own completion is recorded, which is the window a blocking iterator closing on another thread
        // hits after it saw the end of the response.
        AtomicReference<Subscription> subscriptionRef = new AtomicReference<>();
        AtomicBoolean completed = new AtomicBoolean();
        toSource(requester.write(writePublisher1)).subscribe(new PublisherSource.Subscriber<Integer>() {
            @Override
            public void onSubscribe(final Subscription subscription) {
                subscriptionRef.set(subscription);
                subscription.request(1);
            }

            @Override
            public void onNext(final Integer integer) {
            }

            @Override
            public void onError(final Throwable t) {
            }

            @Override
            public void onComplete() {
                completed.set(true);
                subscriptionRef.get().cancel();
            }
        });
        writePublisher1.onNext(1);
        writePublisher1.onComplete();
        channel.writeInbound(1);
        assertTrue(completed.get());
        assertTrue(channel.isOpen());

        toSource(requester.write(writePublisher2)).subscribe(readSubscriber2);
        readSubscriber2.awaitSubscription().request(1);
        writePublisher2.onNext(2);
        writePublisher2.onComplete();
        channel.writeInbound(2);
        assertThat(readSubscriber2.takeOnNext(), is(2));
        readSubscriber2.awaitOnComplete();
        assertTrue(channel.isOpen());
    }

    @Test
    void multiThreadedWritesAllComplete() throws Exception {
        // Avoid using EmbeddedChannel because it is not thread safe. This test writes/reads from multiple threads.
        @SuppressWarnings("unchecked")
        NettyConnection<Integer, Integer> connection = mock(NettyConnection.class);
        Executor connectionExecutor = Executors.newCachedThreadExecutor();
        try {
            doAnswer((Answer<Completable>) invocation -> {
                Publisher<Integer> writeStream = invocation.getArgument(0);
                return writeStream.ignoreElements().concat(connectionExecutor.submit(() -> { }));
            }).when(connection).write(any(), any(), any());
            doAnswer((Answer<Publisher<Integer>>) invocation -> connectionExecutor.submit(() -> { })
                    .concat(Publisher.from(1))).when(connection).read();

            final int concurrentRequestCount = 300;
            NettyPipelinedConnection<Integer, Integer> pipelinedConnection =
                    new NettyPipelinedConnection<>(connection, concurrentRequestCount);
            CyclicBarrier requestStartBarrier = new CyclicBarrier(concurrentRequestCount);
            List<Future<Collection<Integer>>> futures = new ArrayList<>(concurrentRequestCount);
            ExecutorService executor = new ThreadPoolExecutor(0, concurrentRequestCount, 1, SECONDS,
                    new SynchronousQueue<>());
            try {
                for (int i = 0; i < concurrentRequestCount; ++i) {
                    final int finalI = i;
                    futures.add(executor.submit(() -> {
                        try {
                            requestStartBarrier.await();
                        } catch (Exception e) {
                            return Single.<Collection<Integer>>failed(
                                    new AssertionError("failure during request " + finalI, e)).toFuture().get();
                        }
                        return pipelinedConnection.write(Publisher.from(finalI)).toFuture().get();
                    }));
                }

                for (Future<Collection<Integer>> future : futures) {
                    assertThat(future.get(), hasSize(1));
                }
            } finally {
                executor.shutdown();
            }
        } finally {
            connectionExecutor.closeAsync().subscribe();
        }
    }
}

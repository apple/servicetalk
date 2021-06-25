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
package io.servicetalk.http.netty;

import io.servicetalk.concurrent.CompletableSource;
import io.servicetalk.concurrent.PublisherSource;
import io.servicetalk.concurrent.PublisherSource.Subscription;
import io.servicetalk.concurrent.api.Completable;
import io.servicetalk.concurrent.api.Executor;
import io.servicetalk.concurrent.api.Executors;
import io.servicetalk.concurrent.api.Publisher;
import io.servicetalk.concurrent.api.Single;
import io.servicetalk.concurrent.api.TestPublisher;
import io.servicetalk.concurrent.api.TestSubscription;
import io.servicetalk.concurrent.test.internal.TestPublisherSubscriber;
import io.servicetalk.transport.api.ConnectionInfo.Protocol;
import io.servicetalk.transport.api.RetryableException;
import io.servicetalk.transport.netty.internal.DefaultNettyConnection;
import io.servicetalk.transport.netty.internal.EmbeddedDuplexChannel;
import io.servicetalk.transport.netty.internal.FlushStrategy;
import io.servicetalk.transport.netty.internal.NettyConnection;
import io.servicetalk.transport.netty.internal.NoopTransportObserver.NoopConnectionObserver;
import io.servicetalk.transport.netty.internal.WriteDemandEstimator;
import io.servicetalk.transport.netty.internal.WriteDemandEstimators;

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
import java.util.concurrent.atomic.AtomicReference;

import static io.servicetalk.buffer.netty.BufferAllocators.DEFAULT_ALLOCATOR;
import static io.servicetalk.concurrent.api.Completable.completed;
import static io.servicetalk.concurrent.api.Executors.immediate;
import static io.servicetalk.concurrent.api.SourceAdapters.toSource;
import static io.servicetalk.concurrent.internal.DeliberateException.DELIBERATE_EXCEPTION;
import static io.servicetalk.concurrent.internal.SubscriberUtils.deliverCompleteFromSource;
import static io.servicetalk.http.api.HttpExecutionStrategies.defaultStrategy;
import static io.servicetalk.transport.netty.internal.CloseHandler.UNSUPPORTED_PROTOCOL_CLOSE_HANDLER;
import static io.servicetalk.transport.netty.internal.FlushStrategies.defaultFlushStrategy;
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
        channel = new EmbeddedDuplexChannel(false);
        WriteDemandEstimator demandEstimator = mock(WriteDemandEstimator.class);
        writePublisher1 = new TestPublisher<>();
        writePublisher2 = new TestPublisher<>();
        when(demandEstimator.estimateRequestN(anyLong())).then(invocation1 -> MAX_VALUE);
        final DefaultNettyConnection<Integer, Integer> connection =
                DefaultNettyConnection.<Integer, Integer>initChannel(channel, DEFAULT_ALLOCATOR,
                immediate(), obj -> true, UNSUPPORTED_PROTOCOL_CLOSE_HANDLER, defaultFlushStrategy(), null,
                channel2 -> { }, defaultStrategy(), mock(Protocol.class), NoopConnectionObserver.INSTANCE, true)
                        .toFuture().get();
        requester = new NettyPipelinedConnection<>(connection);
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
    void readCancelErrorsPendingReadCancelsPendingWrite() {
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
        writePublisher1Subscription.awaitCancelledUninterruptibly();
        assertFalse(writePublisher2.isSubscribed());
        assertFalse(channel.isOpen());
    }

    @Test
    void channelCloseErrorsPendingReadCancelsPendingWrite() {
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
        writePublisher1Subscription.awaitCancelledUninterruptibly();
        assertFalse(writePublisher2.isSubscribed());
    }

    @Test
    void readCancelClosesConnectionThenWriteDoesNotSubscribe() {
        TestSubscription writePublisher1Subscription = new TestSubscription();
        toSource(requester.write(writePublisher1
                .afterSubscription(() -> writePublisher1Subscription))).subscribe(readSubscriber);
        Subscription readSubscription = readSubscriber.awaitSubscription();
        readSubscription.request(1);

        assertTrue(writePublisher1.isSubscribed());
        readSubscription.cancel(); // cancelling an active read will close the connection.

        // readSubscriber was cancelled, so it may or may not terminate, but other sources that have not terminated
        // should be terminated, cancelled, or not subscribed.

        writePublisher1Subscription.awaitCancelledUninterruptibly();
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
        requester = new NettyPipelinedConnection<>(mockConnection);
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
        requester = new NettyPipelinedConnection<>(mockConnection);
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
        requester = new NettyPipelinedConnection<>(mockConnection);
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
        requester = new NettyPipelinedConnection<>(mockConnection);
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
            NettyPipelinedConnection<Integer, Integer> pipelinedConnection = new NettyPipelinedConnection<>(connection);
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

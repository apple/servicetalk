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
package io.servicetalk.http.netty;

import io.servicetalk.concurrent.PublisherSource;
import io.servicetalk.concurrent.api.Completable;
import io.servicetalk.concurrent.api.Publisher;
import io.servicetalk.concurrent.api.Single;
import io.servicetalk.concurrent.api.TestCollectingPublisherSubscriber;
import io.servicetalk.concurrent.api.TestPublisher;
import io.servicetalk.concurrent.api.TestSubscription;
import io.servicetalk.concurrent.internal.ServiceTalkTestTimeout;
import io.servicetalk.transport.api.ConnectionContext.Protocol;
import io.servicetalk.transport.netty.internal.DefaultNettyConnection;
import io.servicetalk.transport.netty.internal.FlushStrategies;
import io.servicetalk.transport.netty.internal.FlushStrategy;
import io.servicetalk.transport.netty.internal.NettyConnection;
import io.servicetalk.transport.netty.internal.WriteDemandEstimator;
import io.servicetalk.transport.netty.internal.WriteDemandEstimators;

import io.netty.channel.embedded.EmbeddedChannel;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.Timeout;
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
import java.util.concurrent.atomic.AtomicReference;

import static io.servicetalk.buffer.netty.BufferAllocators.DEFAULT_ALLOCATOR;
import static io.servicetalk.concurrent.api.Executors.immediate;
import static io.servicetalk.concurrent.api.SourceAdapters.toSource;
import static io.servicetalk.concurrent.internal.DeliberateException.DELIBERATE_EXCEPTION;
import static io.servicetalk.http.api.HttpExecutionStrategies.defaultStrategy;
import static io.servicetalk.transport.netty.internal.CloseHandler.UNSUPPORTED_PROTOCOL_CLOSE_HANDLER;
import static io.servicetalk.transport.netty.internal.FlushStrategies.defaultFlushStrategy;
import static java.lang.Integer.MAX_VALUE;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.instanceOf;
import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class NettyPipelinedConnectionTest {
    @Rule
    public final Timeout timeout = new ServiceTalkTestTimeout();

    private final TestCollectingPublisherSubscriber<Integer> readSubscriber = new TestCollectingPublisherSubscriber<>();
    private final TestCollectingPublisherSubscriber<Integer> secondReadSubscriber =
            new TestCollectingPublisherSubscriber<>();
    private TestPublisher<Integer> writePublisher1;
    private TestPublisher<Integer> writePublisher2;
    private int requestNext = MAX_VALUE;
    private NettyPipelinedConnection<Integer, Integer> requester;
    private EmbeddedChannel channel;

    @Before
    public void setUp() throws Exception {
        channel = new EmbeddedChannel();
        WriteDemandEstimator demandEstimator = mock(WriteDemandEstimator.class);
        writePublisher1 = new TestPublisher<>();
        writePublisher2 = new TestPublisher<>();
        when(demandEstimator.estimateRequestN(anyLong())).then(invocation1 -> requestNext);
        final DefaultNettyConnection<Integer, Integer> connection =
                DefaultNettyConnection.<Integer, Integer>initChannel(channel, DEFAULT_ALLOCATOR,
                immediate(), obj -> true, UNSUPPORTED_PROTOCOL_CLOSE_HANDLER, defaultFlushStrategy(), null,
                channel2 -> { }, defaultStrategy(), mock(Protocol.class)).toFuture().get();
        requester = new NettyPipelinedConnection<>(connection);
    }

    private Publisher<Integer> write(Publisher<Integer> publisher) {
        return requester.write(publisher, FlushStrategies::defaultFlushStrategy,
                WriteDemandEstimators::newDefaultEstimator);
    }

    @Test
    public void pipelinedWriteAndReadCompleteSequential() throws InterruptedException {
        toSource(write(writePublisher1)).subscribe(readSubscriber);
        readSubscriber.awaitSubscription().request(1);
        toSource(write(writePublisher2)).subscribe(secondReadSubscriber);
        assertTrue(writePublisher1.isSubscribed());
        assertFalse(writePublisher2.isSubscribed());
        writePublisher1.onNext(1);
        writePublisher1.onComplete();
        channel.writeInbound(1);
        Integer next = readSubscriber.takeOnNext();
        assertNotNull(next);
        assertEquals(next.intValue(), 1);
        readSubscriber.awaitOnComplete();

        secondReadSubscriber.awaitSubscription().request(1);
        writePublisher2.onNext(1);
        writePublisher2.onComplete();
        channel.writeInbound(2);
        next = secondReadSubscriber.takeOnNext();
        assertNotNull(next);
        assertEquals(next.intValue(), 2);
        secondReadSubscriber.awaitOnComplete();
    }

    @Test
    public void pipelinedWritesCompleteBeforeReads() throws InterruptedException {
        toSource(write(writePublisher1)).subscribe(readSubscriber);
        readSubscriber.awaitSubscription().request(1);
        toSource(write(writePublisher2)).subscribe(secondReadSubscriber);
        assertTrue(writePublisher1.isSubscribed());
        assertFalse(writePublisher2.isSubscribed());
        writePublisher1.onNext(1);
        writePublisher1.onComplete();
        Integer channelWrite = channel.readOutbound();
        assertNotNull(channelWrite);
        assertEquals(channelWrite.intValue(), 1);

        assertTrue(writePublisher2.isSubscribed());
        writePublisher2.onNext(2);
        writePublisher2.onComplete();
        channelWrite = channel.readOutbound();
        assertNotNull(channelWrite);
        assertEquals(channelWrite.intValue(), 2);

        channel.writeInbound(1);
        channel.writeInbound(2); // write before the second subscribe to test queuing works properly
        Integer next = readSubscriber.takeOnNext();
        assertNotNull(next);
        assertEquals(next.intValue(), 1);
        readSubscriber.awaitOnComplete();

        PublisherSource.Subscription subscription2 = secondReadSubscriber.awaitSubscription();
        subscription2.request(1);
        next = secondReadSubscriber.takeOnNext();
        assertNotNull(next);
        assertEquals(next.intValue(), 2);
        secondReadSubscriber.awaitOnComplete();
    }

    @Test
    public void pipelinedReadsCompleteBeforeWrites() throws InterruptedException {
        toSource(write(writePublisher1)).subscribe(readSubscriber);
        readSubscriber.awaitSubscription().request(1);
        toSource(write(writePublisher2)).subscribe(secondReadSubscriber);

        channel.writeInbound(1);
        channel.writeInbound(2); // write before the second subscribe to test queuing works properly
        Integer next = readSubscriber.takeOnNext();
        assertNotNull(next);
        assertEquals(next.intValue(), 1);
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
        secondReadSubscriber.awaitSubscription().request(1);
        next = secondReadSubscriber.takeOnNext();
        assertNotNull(next);
        assertEquals(next.intValue(), 2);
        // technically the read has completed here, but see the comment below about the merge operator.

        Integer channelWrite = channel.readOutbound();
        assertNotNull(channelWrite);
        assertEquals(channelWrite.intValue(), 1);

        assertTrue(writePublisher2.isSubscribed());
        writePublisher2.onNext(2);
        writePublisher2.onComplete();
        // from a "full duplex" perspective this could be verified earlier after the first element is read because
        // the underlying transport read stream completes. however in order to provide visibility into write errors
        // to the user there is a merge operation which delays the completion of the returned response Publisher.
        secondReadSubscriber.awaitOnComplete();

        channelWrite = channel.readOutbound();
        assertNotNull(channelWrite);
        assertEquals(channelWrite.intValue(), 2);
    }

    @Test
    public void flushStrategy() throws InterruptedException {
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
                .subscribe(secondReadSubscriber);
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
    public void readCancelErrorsPendingReadCancelsPendingWrite() throws InterruptedException {
        TestSubscription writePublisher1Subscription = new TestSubscription();
        toSource(write(writePublisher1
                .afterSubscription(() -> writePublisher1Subscription))).subscribe(readSubscriber);
        PublisherSource.Subscription readSubscription = readSubscriber.awaitSubscription();
        readSubscription.request(1);
        toSource(write(writePublisher2)).subscribe(secondReadSubscriber);

        assertTrue(writePublisher1.isSubscribed());
        readSubscription.cancel(); // cancelling an active read will close the connection.

        // readSubscriber was cancelled, so it may or may not terminate, but other sources that have not terminated
        // should be terminated, cancelled, or not subscribed.

        assertThat(secondReadSubscriber.awaitOnError(), is(instanceOf(ClosedChannelException.class)));
        writePublisher1Subscription.awaitCancelledUninterruptibly();
        assertFalse(writePublisher2.isSubscribed());
        assertFalse(channel.isOpen());
    }

    @Test
    public void channelCloseErrorsPendingReadCancelsPendingWrite() throws InterruptedException {
        TestSubscription writePublisher1Subscription = new TestSubscription();
        toSource(write(writePublisher1
                .afterSubscription(() -> writePublisher1Subscription))).subscribe(readSubscriber);
        PublisherSource.Subscription readSubscription = readSubscriber.awaitSubscription();
        readSubscription.request(1);
        toSource(write(writePublisher2)).subscribe(secondReadSubscriber);

        assertTrue(writePublisher1.isSubscribed());
        assertFalse(writePublisher2.isSubscribed());

        channel.close();

        assertThat(readSubscriber.awaitOnError(), is(instanceOf(ClosedChannelException.class)));
        assertThat(secondReadSubscriber.awaitOnError(), is(instanceOf(ClosedChannelException.class)));
        writePublisher1Subscription.awaitCancelledUninterruptibly();
        assertFalse(writePublisher2.isSubscribed());
    }

    @Test
    public void readCancelClosesConnectionThenWriteDoesNotSubscribe() throws InterruptedException {
        TestSubscription writePublisher1Subscription = new TestSubscription();
        toSource(write(writePublisher1
                .afterSubscription(() -> writePublisher1Subscription))).subscribe(readSubscriber);
        PublisherSource.Subscription readSubscription = readSubscriber.awaitSubscription();
        readSubscription.request(1);

        assertTrue(writePublisher1.isSubscribed());
        readSubscription.cancel(); // cancelling an active read will close the connection.

        // readSubscriber was cancelled, so it may or may not terminate, but other sources that have not terminated
        // should be terminated, cancelled, or not subscribed.

        writePublisher1Subscription.awaitCancelledUninterruptibly();
        assertFalse(channel.isOpen());

        toSource(write(writePublisher2)).subscribe(secondReadSubscriber);
        assertThat(secondReadSubscriber.awaitOnError(), is(instanceOf(ClosedChannelException.class)));
        assertFalse(writePublisher2.isSubscribed());
    }

    @Test
    public void writeErrorFailsPendingReadsDoesNotSubscribeToPendingWrites() throws InterruptedException {
        toSource(write(writePublisher1)).subscribe(readSubscriber);
        PublisherSource.Subscription readSubscription = readSubscriber.awaitSubscription();
        readSubscription.request(1);
        toSource(write(writePublisher2)).subscribe(secondReadSubscriber);

        writePublisher1.onError(DELIBERATE_EXCEPTION);
        assertThat(readSubscriber.awaitOnError(), is(instanceOf(ClosedChannelException.class)));
        assertThat(secondReadSubscriber.awaitOnError(), is(instanceOf(ClosedChannelException.class)));
        assertFalse(writePublisher2.isSubscribed());
        assertFalse(channel.isOpen());
    }

    @Test
    public void multiThreadedWritesAllComplete() throws Exception {
        // Avoid using EmbeddedChannel because it is not thread safe. This test writes/reads from multiple threads.
        @SuppressWarnings("unchecked")
        NettyConnection<Integer, Integer> connection = mock(NettyConnection.class);
        doAnswer((Answer<Completable>) invocation -> {
            Publisher<Integer> writeStream = invocation.getArgument(0);
            return writeStream.ignoreElements().concat(Completable.completed());
        }).when(connection).write(any(), any(), any());
        doAnswer((Answer<Publisher<Integer>>) invocation -> Publisher.from(1)).when(connection).read();

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
                    return pipelinedConnection.write(Publisher.from(finalI),
                            FlushStrategies::defaultFlushStrategy,
                            WriteDemandEstimators::newDefaultEstimator).toFuture().get();
                }));
            }

            for (Future<Collection<Integer>> future : futures) {
                assertThat(future.get(), hasSize(1));
            }
        } finally {
            executor.shutdown();
        }
    }
}

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

import io.servicetalk.concurrent.api.Completable;
import io.servicetalk.concurrent.api.TestPublisher;
import io.servicetalk.concurrent.api.TestPublisherSubscriber;
import io.servicetalk.concurrent.api.TestSubscription;
import io.servicetalk.concurrent.internal.ServiceTalkTestTimeout;
import io.servicetalk.transport.netty.internal.NettyConnection.TerminalPredicate;

import io.netty.channel.embedded.EmbeddedChannel;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.Timeout;
import org.mockito.stubbing.Answer;

import java.nio.channels.ClosedChannelException;

import static io.servicetalk.buffer.netty.BufferAllocators.DEFAULT_ALLOCATOR;
import static io.servicetalk.concurrent.api.Executors.immediate;
import static io.servicetalk.concurrent.api.Publisher.just;
import static io.servicetalk.concurrent.api.Single.success;
import static io.servicetalk.concurrent.api.SourceAdapters.toSource;
import static io.servicetalk.transport.netty.internal.CloseHandler.UNSUPPORTED_PROTOCOL_CLOSE_HANDLER;
import static io.servicetalk.transport.netty.internal.FlushStrategies.defaultFlushStrategy;
import static java.lang.Integer.MAX_VALUE;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.instanceOf;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class DefaultNettyPipelinedConnectionTest {
    @Rule
    public final Timeout timeout = new ServiceTalkTestTimeout();

    public static final int MAX_PENDING_REQUESTS = 2;
    private final TestPublisherSubscriber<Integer> readSubscriber = new TestPublisherSubscriber<>();
    private final TestPublisherSubscriber<Integer> secondReadSubscriber = new TestPublisherSubscriber<>();
    private TestPublisher<Integer> writePublisher1;
    private TestPublisher<Integer> writePublisher2;
    private int requestNext = MAX_VALUE;
    private DefaultNettyPipelinedConnection<Integer, Integer> requester;
    private DefaultNettyConnection<Integer, Integer> connection;
    private EmbeddedChannel channel;

    @Before
    public void setUp() throws Exception {
        channel = new EmbeddedChannel();
        NettyConnection.RequestNSupplier requestNSupplier = mock(NettyConnection.RequestNSupplier.class);
        writePublisher1 = new TestPublisher<>();
        writePublisher2 = new TestPublisher<>();
        when(requestNSupplier.requestNFor(anyLong())).then(invocation1 -> requestNext);
        connection = DefaultNettyConnection.<Integer, Integer>initChannel(channel, DEFAULT_ALLOCATOR,
                immediate(), new TerminalPredicate<>(integer -> true),
                UNSUPPORTED_PROTOCOL_CLOSE_HANDLER, defaultFlushStrategy(),
                (channel2, context2) -> context2).toFuture().get();
        requester = new DefaultNettyPipelinedConnection<>(connection, MAX_PENDING_REQUESTS);
    }

    @Test
    public void testSequencing() {
        toSource(requester.request(writePublisher1)).subscribe(readSubscriber);
        readSubscriber.request(1);
        toSource(requester.request(writePublisher2)).subscribe(secondReadSubscriber);
        secondReadSubscriber.request(1);
        assertTrue(writePublisher1.isSubscribed());
        assertFalse(writePublisher2.isSubscribed());
        writePublisher1.onNext(1);
        writePublisher1.onComplete();
        assertTrue(readSubscriber.subscriptionReceived());
        channel.writeInbound(1);
        assertThat(readSubscriber.items(), contains(1));
        assertTrue(readSubscriber.isCompleted());
        assertTrue(writePublisher2.isSubscribed());
        writePublisher2.onNext(1);
        writePublisher2.onComplete();
        assertTrue(secondReadSubscriber.subscriptionReceived());
        channel.writeInbound(2);
        assertThat(secondReadSubscriber.items(), contains(2));
        assertTrue(secondReadSubscriber.isCompleted());
    }

    @Test
    public void testWriteCancelAndPreviousRead() {
        toSource(requester.request(writePublisher1)).subscribe(readSubscriber);
        readSubscriber.request(1);
        toSource(requester.request(writePublisher2)).subscribe(secondReadSubscriber);
        secondReadSubscriber.request(1);
        assertTrue(writePublisher1.isSubscribed());
        assertFalse(writePublisher2.isSubscribed());
        writePublisher1.onNext(1);
        writePublisher1.onComplete();
        assertTrue(readSubscriber.subscriptionReceived()); // Keep the first read active.

        assertTrue(writePublisher2.isSubscribed());
        writePublisher2.onComplete();
        // First read active, second write active. Cancel of request() should not start second read. However when we
        // complete the first read this will propagate the cancel and close the connection.
        secondReadSubscriber.cancel();

        forceReadSubscriberComplete();

        // The first read completed successfully (after the second read was cancelled). After the first read completes
        // the second read subscriber is swapped in and the cancel state is propagated up which closes the channel.
        assertThat(secondReadSubscriber.error(), instanceOf(ClosedChannelException.class));
    }

    private void forceReadSubscriberComplete() {
        // We have to simulate some read event in order to complete the first read Subscriber because the
        // NettyChannelPublisher needs to read data to invoke the TerminalPredicate.
        channel.writeInbound(1);
        assertThat(readSubscriber.items(), contains(1));
        assertTrue(readSubscriber.isCompleted());
    }

    @Test
    public void testItemWriteAndFlush() {
        toSource(requester.request(1)).subscribe(readSubscriber);
        readSubscriber.request(1);
        forceReadSubscriberComplete();
    }

    @Test
    public void testSingleWriteAndFlush() {
        toSource(requester.request(success(1))).subscribe(readSubscriber);
        readSubscriber.request(1);
        forceReadSubscriberComplete();
    }

    @Test
    public void testPublisherWrite() {
        toSource(requester.request(just(1))).subscribe(readSubscriber);
        readSubscriber.request(1);
        forceReadSubscriberComplete();
    }

    @Test
    public void testPipelinedRequests() {
        toSource(requester.request(writePublisher1)).subscribe(readSubscriber);
        readSubscriber.request(1);
        toSource(requester.request(writePublisher2)).subscribe(secondReadSubscriber);
        secondReadSubscriber.request(1);
        assertTrue(writePublisher1.isSubscribed());
        writePublisher1.onNext(1);
        writePublisher1.onComplete();
        channel.writeInbound(1);
        assertTrue(readSubscriber.isCompleted());
        writePublisher2.onNext(1);
        writePublisher2.onComplete();
        channel.writeInbound(2);
        assertThat(secondReadSubscriber.items(), contains(2));
        assertTrue(secondReadSubscriber.isCompleted());
    }

    @Test
    public void testWriteCancelAndThenWrite() {
        toSource(requester.request(writePublisher1)).subscribe(readSubscriber);
        final TestSubscription subscription = new TestSubscription();
        writePublisher1.onSubscribe(subscription);
        readSubscriber.request(1);
        toSource(requester.request(writePublisher2)).subscribe(secondReadSubscriber);
        secondReadSubscriber.request(1);
        readSubscriber.cancel();
        assertTrue(subscription.isCancelled());
        assertTrue(writePublisher2.isSubscribed());
        writePublisher2.onNext(1);
        writePublisher2.onComplete();
        channel.writeInbound(1);
        assertThat(secondReadSubscriber.items(), contains(1));
        assertTrue(secondReadSubscriber.isCompleted());
    }

    @Test
    public void testReadCancelAndThenWrite() {
        toSource(requester.request(writePublisher1)).subscribe(readSubscriber);
        readSubscriber.request(1);
        toSource(requester.request(writePublisher2)).subscribe(secondReadSubscriber);
        secondReadSubscriber.request(1);
        assertTrue(writePublisher1.isSubscribed());
        writePublisher1.onNext(1);
        writePublisher1.onComplete();
        readSubscriber.cancel();

        // The active read subscriber cancelled, this should close the connection and stop all subsequent operations.
        assertThat(readSubscriber.items(), hasSize(0));
        assertFalse(readSubscriber.isTerminated());
        assertTrue(secondReadSubscriber.isTerminated());
        assertFalse(channel.isOpen());
    }

    @Test
    public void testWriter() {
        NettyPipelinedConnection.Writer writer = mock(NettyPipelinedConnection.Writer.class);
        when(writer.write()).then((Answer<Completable>) invocation -> connection.writeAndFlush(1));
        toSource(requester.request(writer)).subscribe(readSubscriber);
        readSubscriber.request(1);
        verify(writer).write();
        channel.writeInbound(1);
        assertThat(readSubscriber.items(), contains(1));
        assertTrue(readSubscriber.isCompleted());
    }
}

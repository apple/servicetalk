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

import io.servicetalk.concurrent.api.Completable;
import io.servicetalk.concurrent.api.MockedSubscriberRule;
import io.servicetalk.concurrent.api.TestPublisher;
import io.servicetalk.concurrent.internal.ServiceTalkTestTimeout;
import io.servicetalk.transport.api.ConnectionContext;
import io.servicetalk.transport.api.ExecutionContext;

import io.netty.channel.embedded.EmbeddedChannel;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.Timeout;
import org.mockito.stubbing.Answer;

import static io.servicetalk.concurrent.api.Executors.immediate;
import static io.servicetalk.concurrent.api.Publisher.just;
import static io.servicetalk.concurrent.api.Single.success;
import static io.servicetalk.transport.netty.internal.CloseHandler.UNSUPPORTED_PROTOCOL_CLOSE_HANDLER;
import static io.servicetalk.transport.netty.internal.FlushStrategies.defaultFlushStrategy;
import static java.lang.Integer.MAX_VALUE;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class DefaultNettyPipelinedConnectionTest {
    @Rule
    public final Timeout timeout = new ServiceTalkTestTimeout();
    @Rule
    public final MockedSubscriberRule<Integer> readSubscriber = new MockedSubscriberRule<>();
    @Rule
    public final MockedSubscriberRule<Integer> secondReadSubscriber = new MockedSubscriberRule<>();

    public static final int MAX_PENDING_REQUESTS = 2;
    private TestPublisher<Integer> readPublisher;
    private TestPublisher<Integer> writePublisher1;
    private TestPublisher<Integer> writePublisher2;
    private int requestNext = MAX_VALUE;
    private DefaultNettyPipelinedConnection<Integer, Integer> requester;
    private DefaultNettyConnection<Integer, Integer> connection;

    @Before
    public void setUp() {
        EmbeddedChannel channel = new EmbeddedChannel();
        ExecutionContext executionContext = mock(ExecutionContext.class);
        ConnectionContext context = mock(ConnectionContext.class);
        when(context.closeAsync()).thenReturn(new NettyFutureCompletable(channel::close));
        when(context.executionContext()).thenReturn(executionContext);
        when(context.executionContext().executor()).thenReturn(immediate());
        when(executionContext.executor()).thenReturn(immediate());
        NettyConnection.RequestNSupplier requestNSupplier = mock(NettyConnection.RequestNSupplier.class);
        readPublisher = new TestPublisher<>(false, false);
        readPublisher.sendOnSubscribe();
        writePublisher1 = new TestPublisher<>();
        writePublisher1.sendOnSubscribe();
        writePublisher2 = new TestPublisher<>();
        writePublisher2.sendOnSubscribe();
        when(requestNSupplier.requestNFor(anyLong())).then(invocation1 -> requestNext);
        connection = new DefaultNettyConnection<>(channel, context, readPublisher,
                new NettyConnection.TerminalPredicate<>(integer -> true), UNSUPPORTED_PROTOCOL_CLOSE_HANDLER,
                defaultFlushStrategy());
        requester = new DefaultNettyPipelinedConnection<>(connection, MAX_PENDING_REQUESTS);
    }

    @Test
    public void testSequencing() {
        readSubscriber.subscribe(requester.request(writePublisher1)).request(1);
        secondReadSubscriber.subscribe(requester.request(writePublisher2)).request(1);
        writePublisher1.verifySubscribed();
        readPublisher.verifyNotSubscribed();
        writePublisher2.verifyNotSubscribed();
        writePublisher1.sendItems(1).onComplete();
        readPublisher.verifySubscribed().sendItems(1).onComplete();
        readSubscriber.verifySuccess(1);
        readPublisher.verifyNotSubscribed();
        writePublisher2.verifySubscribed().sendItems(1).onComplete();
        readPublisher.verifySubscribed();
        readPublisher.sendItems(1).onComplete();
        secondReadSubscriber.verifySuccess(1);
    }

    @Test
    public void testWriteCancelAndPreviousRead() {
        readSubscriber.subscribe(requester.request(writePublisher1)).request(1);
        secondReadSubscriber.subscribe(requester.request(writePublisher2)).request(1);
        writePublisher1.verifySubscribed();
        readPublisher.verifyNotSubscribed();
        writePublisher2.verifyNotSubscribed();
        writePublisher1.sendItems(1).onComplete();
        readPublisher.verifySubscribed(); // Keep the first read active.

        writePublisher2.verifySubscribed().onComplete();
        // First read active, second write active. Cancel of request() should not start
        // second read.
        secondReadSubscriber.cancel();

        readPublisher.verifySubscribed().onComplete();
        readSubscriber.verifySuccess();
        secondReadSubscriber.verifyNoEmissions();
    }

    @Test
    public void testItemWriteAndFlush() {
        readSubscriber.subscribe(requester.request(1)).request(1);
        readPublisher.onComplete();
        readSubscriber.verifySuccess();
    }

    @Test
    public void testSingleWriteAndFlush() {
        readSubscriber.subscribe(requester.request(success(1))).request(1);
        readPublisher.onComplete();
        readSubscriber.verifySuccess();
    }

    @Test
    public void testPublisherWrite() {
        readSubscriber.subscribe(requester.request(just(1))).request(1);
        readPublisher.onComplete();
        readSubscriber.verifySuccess();
    }

    @Test
    public void testPipelinedRequests() {
        readSubscriber.subscribe(requester.request(writePublisher1)).request(1);
        secondReadSubscriber.subscribe(requester.request(writePublisher2)).request(1);
        writePublisher1.verifySubscribed().sendItems(1).onComplete();
        readPublisher.onComplete();
        readSubscriber.verifySuccess();
        writePublisher2.sendItems(1).onComplete();
        readPublisher.onComplete();
        secondReadSubscriber.verifySuccess();
    }

    @Test
    public void testWriteCancelAndThenWrite() {
        readSubscriber.subscribe(requester.request(writePublisher1)).request(1);
        secondReadSubscriber.subscribe(requester.request(writePublisher2)).request(1);
        readSubscriber.cancel();
        writePublisher1.verifyCancelled();
        writePublisher2.verifySubscribed().sendItems(1).onComplete();
        readPublisher.onComplete();
        secondReadSubscriber.verifySuccess();
    }

    @Test
    public void testReadCancelAndThenWrite() {
        readSubscriber.subscribe(requester.request(writePublisher1)).request(1);
        secondReadSubscriber.subscribe(requester.request(writePublisher2)).request(1);
        writePublisher1.verifySubscribed().sendItems(1).onComplete();
        readSubscriber.cancel();
        writePublisher2.verifySubscribed().sendItems(1).onComplete();
        readPublisher.onComplete();
        secondReadSubscriber.verifySuccess();
    }

    @Test
    public void testWriter() {
        NettyPipelinedConnection.Writer writer = mock(NettyPipelinedConnection.Writer.class);
        when(writer.write()).then((Answer<Completable>) invocation -> connection.writeAndFlush(1));
        readSubscriber.subscribe(requester.request(writer)).request(1);
        verify(writer).write();
        readPublisher.onComplete();
        readSubscriber.verifySuccess();
    }
}

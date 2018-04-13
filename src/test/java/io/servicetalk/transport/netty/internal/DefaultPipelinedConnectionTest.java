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
import io.servicetalk.concurrent.Completable.Subscriber;
import io.servicetalk.concurrent.api.Completable;
import io.servicetalk.concurrent.api.MockedCompletableListenerRule;
import io.servicetalk.concurrent.api.MockedSubscriberRule;
import io.servicetalk.concurrent.api.QueueFullException;
import io.servicetalk.concurrent.api.Single;
import io.servicetalk.concurrent.api.TestPublisher;
import io.servicetalk.concurrent.internal.ServiceTalkTestTimeout;
import io.servicetalk.transport.api.ConnectionContext;

import io.netty.channel.embedded.EmbeddedChannel;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.Timeout;
import org.mockito.stubbing.Answer;

import static io.servicetalk.concurrent.api.Publisher.just;
import static io.servicetalk.concurrent.api.Single.never;
import static io.servicetalk.concurrent.api.Single.success;
import static io.servicetalk.transport.api.FlushStrategy.defaultFlushStrategy;
import static java.lang.Integer.MAX_VALUE;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class DefaultPipelinedConnectionTest {
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
    private DefaultPipelinedConnection<Integer, Integer> requester;
    private NettyConnection<Integer, Integer> connection;

    @Before
    public void setUp() {
        EmbeddedChannel channel = new EmbeddedChannel();
        ConnectionContext context = mock(ConnectionContext.class);
        when(context.closeAsync()).thenReturn(new NettyFutureCompletable(channel::close));
        Connection.RequestNSupplier requestNSupplier = mock(Connection.RequestNSupplier.class);
        readPublisher = new TestPublisher<>();
        readPublisher.sendOnSubscribe();
        writePublisher1 = new TestPublisher<>();
        writePublisher1.sendOnSubscribe();
        writePublisher2 = new TestPublisher<>();
        writePublisher2.sendOnSubscribe();
        when(requestNSupplier.getRequestNFor(anyLong())).then(invocation1 -> requestNext);
        connection = new NettyConnection<>(channel, context, readPublisher, new Connection.TerminalPredicate<>(integer -> true));
        requester = new DefaultPipelinedConnection<>(connection, MAX_PENDING_REQUESTS);
    }

    @Test
    public void testSequencing() {
        readSubscriber.subscribe(requester.request(writePublisher1, defaultFlushStrategy())).request(1);
        secondReadSubscriber.subscribe(requester.request(writePublisher2, defaultFlushStrategy())).request(1);
        writePublisher1.verifySubscribed();
        readPublisher.verifySubscribed();
        writePublisher2.verifyNotSubscribed();
        writePublisher1.sendItems(1).onComplete();
        writePublisher2.verifySubscribed();
        readPublisher.sendItems(1).onComplete();
        writePublisher2.verifySubscribed();
        readSubscriber.verifySuccess(1);
        writePublisher2.sendItems(1).onComplete();
        readPublisher.verifySubscribed();
        readPublisher.sendItems(1).onComplete();
        readPublisher.verifyNotSubscribed();
        secondReadSubscriber.verifySuccess(1);
    }

    @Test
    public void testItemWriteAndFlush() {
        readSubscriber.subscribe(requester.request(1)).request(1);
        readPublisher.onComplete();
        readSubscriber.verifySuccess();
        dispose();
    }

    @Test
    public void testSingleWriteAndFlush() {
        readSubscriber.subscribe(requester.request(success(1))).request(1);
        readPublisher.onComplete();
        readSubscriber.verifySuccess();
        dispose();
    }

    @Test
    public void testPublisherWrite() {
        readSubscriber.subscribe(requester.request(just(1), defaultFlushStrategy())).request(1);
        readPublisher.onComplete();
        readSubscriber.verifySuccess();
        dispose();
    }

    @Test
    public void testPipelinedRequests() {
        readSubscriber.subscribe(requester.request(writePublisher1, defaultFlushStrategy())).request(1);
        secondReadSubscriber.subscribe(requester.request(writePublisher2, defaultFlushStrategy())).request(1);
        writePublisher1.verifySubscribed().sendItems(1).onComplete();
        readPublisher.onComplete();
        readSubscriber.verifySuccess();
        writePublisher2.sendItems(1).onComplete();
        readPublisher.onComplete();
        secondReadSubscriber.verifySuccess();
        dispose();
    }

    @Test
    public void testPipelinedFullDuplexRequests() {
        assertEquals(0, requester.getPendingRequestsCount());
        readSubscriber.subscribe(requester.request(writePublisher1, defaultFlushStrategy())).request(2);
        assertEquals(1, requester.getPendingRequestsCount());
        secondReadSubscriber.subscribe(requester.request(writePublisher2, defaultFlushStrategy())).request(1);
        writePublisher1.verifySubscribed().sendItems(1); // 1st write starting
        writePublisher2.verifyNotSubscribed();
        readPublisher.verifySubscribed().sendItems(1); // 1st read starting - 1st write ongoing
        readSubscriber.verifyItems(1);
        writePublisher1.onComplete();
        writePublisher1.verifyNotSubscribed();
        writePublisher2.verifySubscribed().sendItems(1); // 2nd write starting - 1st read ongoing
        secondReadSubscriber.verifyNoEmissions();
        readPublisher.sendItems(2).onComplete(); // 1st read complete
        readPublisher.verifySubscribed().sendItems(3); // 2nd read starting
        readSubscriber.verifySuccess(2);
        secondReadSubscriber.verifyItems(3);
        readPublisher.onComplete(); // 2nd read complete
        assertEquals(1, requester.getPendingRequestsCount());
        writePublisher2.onComplete(); // 2nd write complete
        writePublisher2.verifyNotSubscribed();
        secondReadSubscriber.verifySuccess();
        readPublisher.verifyNotSubscribed();
        assertEquals(0, requester.getPendingRequestsCount());
        dispose();
    }

    @Test
    public void testWriteCancelAndThenWrite() {
        readSubscriber.subscribe(requester.request(writePublisher1, defaultFlushStrategy())).request(1);
        secondReadSubscriber.subscribe(requester.request(writePublisher2, defaultFlushStrategy())).request(1);
        readSubscriber.cancel();
        writePublisher1.verifyCancelled();
        writePublisher2.verifySubscribed().sendItems(1).onComplete();
        readPublisher.onComplete();
        secondReadSubscriber.verifySuccess();
        dispose();
    }

    @Test
    public void testReadCancelAndThenWrite() {
        readSubscriber.subscribe(requester.request(writePublisher1, defaultFlushStrategy())).request(1);
        secondReadSubscriber.subscribe(requester.request(writePublisher2, defaultFlushStrategy())).request(1);
        writePublisher1.verifySubscribed().sendItems(1).onComplete();
        readSubscriber.cancel();
        writePublisher2.verifySubscribed().sendItems(1).onComplete();
        readPublisher.onComplete();
        secondReadSubscriber.verifySuccess();
        dispose();
    }

    @Test
    public void testEnforceLimitWithWritesPending() {
        MockedCompletableListenerRule[] subs = new MockedCompletableListenerRule[MAX_PENDING_REQUESTS];
        for (int i = 0; i < MAX_PENDING_REQUESTS; i++) {
            MockedCompletableListenerRule sub = new MockedCompletableListenerRule();
            subs[i] = sub;
            sub.listen(requester.request(never()).ignoreElements());
            sub.verifyNoEmissions();
        }
        MockedCompletableListenerRule sub = new MockedCompletableListenerRule();
        sub.listen(requester.request(never()).ignoreElements());
        sub.verifyFailure(QueueFullException.class);
        for (MockedCompletableListenerRule aSub : subs) {
            aSub.verifyNoEmissions();
        }
    }

    @Test
    public void testEnforceLimitWithReadsPending() {
        enforceLimitWhenReadsPending();
    }

    @Test
    public void testEnsureRequestCountUpdatedBeforeTerminalEvents() {
        MockedCompletableListenerRule sub = new MockedCompletableListenerRule();
        Completable completable1 = requester.request(never()).ignoreElements();
        Completable completable2 = requester.request(success(1)).ignoreElements();
        Subscriber subscriber = new Subscriber() {
            @Override
            public void onSubscribe(Cancellable subCancel) {
                // NOOP
            }

            @Override
            public void onComplete() {
                sub.listen(requester.request(Single.success(1)).ignoreElements());
                sub.verifyNoEmissions();
                readPublisher.onComplete();
            }

            @Override
            public void onError(Throwable t) {
                fail(t.getMessage());
            }
        };
        MockedCompletableListenerRule listener = new MockedCompletableListenerRule().listen(completable1);
        completable2.subscribe(subscriber);
        readPublisher.onComplete(); // allow 1st pending request to get canceled
        assertEquals(MAX_PENDING_REQUESTS, requester.getPendingRequestsCount());
        readPublisher.onComplete(); // completes 2nd req & triggers 3nd req from onComplete() event
        listener.cancel(); // unblocks write queue for 2nd and 3rd req
        sub.verifyCompletion(); // ensures 3rd req completed successfully after requestCount updated
    }

    @Test
    public void testPipelineLimitExceededFailureDoesNotDecrementPendingCount() {
        MockedCompletableListenerRule[] subs = enforceLimitWhenReadsPending();

        MockedCompletableListenerRule sub = new MockedCompletableListenerRule();
        sub.listen(requester.request(never()).ignoreElements());
        sub.verifyFailure(QueueFullException.class);
        for (MockedCompletableListenerRule aSub : subs) {
            aSub.verifyNoEmissions();
        }
    }

    @Test
    public void testReusePostLimitExceeded() {
        MockedCompletableListenerRule[] subs = enforceLimitWhenReadsPending();
        readPublisher.verifySubscribed().sendItems(1);
        subs[0].verifyCompletion();
        // Since we completed previous requests, this should pass.
        MockedCompletableListenerRule sub = new MockedCompletableListenerRule();
        sub.listen(requester.request(never()).ignoreElements());
    }

    @Test
    public void testWriter() {
        PipelinedConnection.Writer writer = mock(PipelinedConnection.Writer.class);
        when(writer.write()).then((Answer<Completable>) invocation -> connection.writeAndFlush(1));
        readSubscriber.subscribe(requester.request(writer)).request(1);
        verify(writer).write();
        readPublisher.onComplete();
        readSubscriber.verifySuccess();
        dispose();
    }

    private void dispose() {
        assertThat("Active requests found on the requester.", requester.getPendingRequestsCount(), is(0));
    }

    private MockedCompletableListenerRule[] enforceLimitWhenReadsPending() {
        MockedCompletableListenerRule[] subs = new MockedCompletableListenerRule[MAX_PENDING_REQUESTS];
        for (int i = 0; i < MAX_PENDING_REQUESTS; i++) {
            MockedCompletableListenerRule sub = new MockedCompletableListenerRule();
            subs[i] = sub;
            sub.listen(requester.request(success(1)).take(1).ignoreElements());
            sub.verifyNoEmissions();
        }
        MockedCompletableListenerRule sub = new MockedCompletableListenerRule();
        sub.listen(requester.request(never()).ignoreElements());
        sub.verifyFailure(QueueFullException.class);
        for (MockedCompletableListenerRule aSub : subs) {
            aSub.verifyNoEmissions();
        }
        return subs;
    }
}

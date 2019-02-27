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

import io.servicetalk.concurrent.api.TestPublisher;
import io.servicetalk.concurrent.api.TestSubscription;
import io.servicetalk.transport.netty.internal.FlushStrategy.FlushSender;
import io.servicetalk.transport.netty.internal.FlushStrategy.WriteEventsListener;

import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;

import static io.servicetalk.concurrent.api.TestPublisher.newTestPublisher;
import static io.servicetalk.concurrent.internal.DeliberateException.DELIBERATE_EXCEPTION;
import static io.servicetalk.transport.netty.internal.FlushStrategies.flushOnEach;
import static io.servicetalk.transport.netty.internal.FlushStrategies.flushOnEnd;
import static io.servicetalk.transport.netty.internal.FlushStrategies.flushWith;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyZeroInteractions;

public class FlushStrategiesTest {

    private FlushSender flushSender;
    private TestPublisher<String> durationSource;
    private WriteEventsListener listener;
    private TestSubscription subscription = new TestSubscription();

    @Before
    public void setUp() {
        flushSender = mock(FlushSender.class);
        durationSource = newTestPublisher();
    }

    @Test
    public void testFlushOnEach() {
        setupFor(flushOnEach());
        listener.itemWritten();
        listener.itemWritten();
        verifyFlush(2);
    }

    @Test
    public void testBatchFlush() {
        setupForBatch(2);
        testBatch(2, 2);
    }

    @Test
    public void testNoFlushForBatch() {
        setupForBatch(3);
        testBatch(3, 2);
    }

    @Test
    public void testFlushOnDuration() {
        setupForBatch(5);
        testBatch(5, 2);
        durationSource.onNext("Flush");
        verifyFlush(1);
    }

    @Test
    public void testBatchFlushWriteCancel() {
        setupForBatch(5);
        durationSource.onSubscribe(subscription);
        testBatch(5, 2);
        listener.writeCancelled();
        assertTrue(subscription.isCancelled());
    }

    @Test
    public void testSourceEmitErrorForFlushOnEach() {
        setupFor(flushOnEach());
        listener.writeTerminated();
    }

    @Test
    public void testSourceEmitErrorForBatchFlush() {
        setupForBatch(2);
        durationSource.onSubscribe(subscription);
        listener.writeTerminated();
        assertTrue(subscription.isCancelled());
    }

    @Test
    public void testDurationComplete() {
        setupForBatch(2);
        durationSource.onComplete();
        listener.itemWritten();
        verifyZeroInteractions(flushSender);
        listener.writeTerminated();
        verifyFlush(1);
    }

    @Test
    public void testDurationEmitError() {
        setupForBatch(2);
        durationSource.onError(DELIBERATE_EXCEPTION);
        verifyZeroInteractions(flushSender);
        listener.itemWritten();
        listener.itemWritten();
        verify(flushSender).flush();
    }

    @Test
    public void testDurationEmitErrorPostComplete() {
        setupForBatch(2);
        listener.writeTerminated();
        durationSource.onError(DELIBERATE_EXCEPTION);
        verifyZeroInteractions(flushSender);
    }

    @Test
    public void testDurationCompletePostComplete() {
        setupForBatch(2);
        listener.itemWritten();
        listener.writeTerminated();
        durationSource.onComplete();
        verify(flushSender).flush();
    }

    @Test
    public void testFlushOnEndComplete() {
        setupFor(flushOnEnd());
        listener.itemWritten();
        verifyFlush(0);
        listener.writeTerminated();
        verifyFlush(1);
    }

    @Test
    public void testFlushWith() {
        setupFor(flushWith(durationSource));
        durationSource.onSubscribe(subscription);
        listener.itemWritten();
        verifyFlush(0);
        assertTrue(durationSource.isSubscribed());
        durationSource.onNext("1");
        verifyFlush(1);
        listener.writeTerminated();
        assertTrue(subscription.isCancelled());
    }

    @Test
    public void testFlushWithWriteCancelCancelsSource() {
        setupFor(flushWith(durationSource));
        durationSource.onSubscribe(subscription);
        listener.writeCancelled();
        assertTrue(subscription.isCancelled());
    }

    private void setupFor(FlushStrategy strategy) {
        listener = strategy.apply(flushSender);
        listener.writeStarted();
    }

    private void setupForBatch(int batchSize) {
        setupFor(FlushStrategies.batchFlush(batchSize, durationSource.map(s -> 1L)));
    }

    private void testBatch(int batchSize, int sendItemCount) {
        for (int i = 0; i < sendItemCount; i++) {
            listener.itemWritten();
        }
        verifyFlush(sendItemCount / batchSize);
    }

    private void verifyFlush(int flushCount) {
        if (flushCount > 0) {
            Mockito.verify(flushSender, Mockito.times(flushCount)).flush();
        } else {
            verifyZeroInteractions(flushSender);
        }
    }
}

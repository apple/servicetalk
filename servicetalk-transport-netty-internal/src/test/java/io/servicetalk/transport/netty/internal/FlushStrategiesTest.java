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

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import static io.servicetalk.concurrent.internal.DeliberateException.DELIBERATE_EXCEPTION;
import static io.servicetalk.transport.netty.internal.FlushStrategies.flushOnEach;
import static io.servicetalk.transport.netty.internal.FlushStrategies.flushOnEnd;
import static io.servicetalk.transport.netty.internal.FlushStrategies.flushWith;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyZeroInteractions;

class FlushStrategiesTest {

    private FlushSender flushSender;
    private TestPublisher<String> durationSource;
    private WriteEventsListener listener;
    private TestSubscription subscription = new TestSubscription();

    @BeforeEach
    public void setUp() {
        flushSender = mock(FlushSender.class);
        durationSource = new TestPublisher<>();
    }

    @Test
    void testFlushOnEach() {
        setupFor(flushOnEach());
        listener.itemWritten(1);
        listener.itemWritten(2);
        verifyFlush(2);
    }

    @Test
    void testBatchFlush() {
        setupForBatch(2);
        testBatch(2, 2);
    }

    @Test
    void testNoFlushForBatch() {
        setupForBatch(3);
        testBatch(3, 2);
    }

    @Test
    void testFlushOnDuration() {
        setupForBatch(5);
        testBatch(5, 2);
        durationSource.onNext("Flush");
        verifyFlush(1);
    }

    @Test
    void testBatchFlushWriteCancel() {
        setupForBatch(5);
        durationSource.onSubscribe(subscription);
        testBatch(5, 2);
        listener.writeCancelled();
        assertTrue(subscription.isCancelled());
    }

    @Test
    void testSourceEmitErrorForFlushOnEach() {
        setupFor(flushOnEach());
        listener.writeTerminated();
    }

    @Test
    void testSourceEmitErrorForBatchFlush() {
        setupForBatch(2);
        durationSource.onSubscribe(subscription);
        listener.writeTerminated();
        assertTrue(subscription.isCancelled());
    }

    @Test
    void testDurationComplete() {
        setupForBatch(2);
        durationSource.onComplete();
        listener.itemWritten(1);
        verifyZeroInteractions(flushSender);
        listener.writeTerminated();
        verifyFlush(1);
    }

    @Test
    void testDurationEmitError() {
        setupForBatch(2);
        durationSource.onError(DELIBERATE_EXCEPTION);
        verifyZeroInteractions(flushSender);
        listener.itemWritten(1);
        listener.itemWritten(2);
        verify(flushSender).flush();
    }

    @Test
    void testDurationEmitErrorPostComplete() {
        setupForBatch(2);
        listener.writeTerminated();
        durationSource.onError(DELIBERATE_EXCEPTION);
        verifyZeroInteractions(flushSender);
    }

    @Test
    void testDurationCompletePostComplete() {
        setupForBatch(2);
        listener.itemWritten(1);
        listener.writeTerminated();
        durationSource.onComplete();
        verify(flushSender).flush();
    }

    @Test
    void testFlushOnEndComplete() {
        setupFor(flushOnEnd());
        listener.itemWritten(1);
        verifyFlush(0);
        listener.writeTerminated();
        verifyFlush(1);
    }

    @Test
    void testFlushWith() {
        setupFor(flushWith(durationSource));
        durationSource.onSubscribe(subscription);
        listener.itemWritten(1);
        verifyFlush(0);
        assertTrue(durationSource.isSubscribed());
        durationSource.onNext("1");
        verifyFlush(1);
        listener.writeTerminated();
        assertTrue(subscription.isCancelled());
    }

    @Test
    void testFlushWithWriteCancelCancelsSource() {
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
            listener.itemWritten(i);
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

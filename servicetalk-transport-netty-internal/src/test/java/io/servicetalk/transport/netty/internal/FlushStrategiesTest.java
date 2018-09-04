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

import io.servicetalk.concurrent.api.TestPublisher;
import io.servicetalk.transport.netty.internal.FlushStrategy.FlushSender;
import io.servicetalk.transport.netty.internal.FlushStrategy.WriteEventsListener;

import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;

import static io.servicetalk.transport.netty.internal.FlushStrategy.flushOnEach;
import static io.servicetalk.transport.netty.internal.FlushStrategy.flushOnEnd;
import static io.servicetalk.transport.netty.internal.FlushStrategy.flushWith;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyZeroInteractions;

public class FlushStrategiesTest {

    private FlushSender flushSender;
    private TestPublisher<String> durationSource;
    private WriteEventsListener listener;

    @Before
    public void setUp() {
        flushSender = mock(FlushSender.class);
        durationSource = new TestPublisher<String>().sendOnSubscribe();
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
        durationSource.sendItems("Flush");
        verifyFlush(1);
    }

    @Test
    public void testBatchFlushWriteCancel() {
        setupForBatch(5);
        testBatch(5, 2);
        listener.writeCancelled();
        durationSource.verifyCancelled();
    }

    @Test
    public void testSourceEmitErrorForFlushOnEach() {
        setupFor(flushOnEach());
        listener.writeTerminated();
    }

    @Test
    public void testSourceEmitErrorForBatchFlush() {
        setupForBatch(2);
        listener.writeTerminated();
        durationSource.verifyCancelled();
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
        durationSource.fail();
        verifyZeroInteractions(flushSender);
        listener.itemWritten();
        listener.itemWritten();
        verify(flushSender).flush();
    }

    @Test
    public void testDurationEmitErrorPostComplete() {
        setupForBatch(2);
        listener.writeTerminated();
        durationSource.failIfSubscriberActive();
        verifyZeroInteractions(flushSender);
    }

    @Test
    public void testDurationCompletePostComplete() {
        setupForBatch(2);
        listener.itemWritten();
        listener.writeTerminated();
        durationSource.completeIfSubscriberActive();
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
        listener.itemWritten();
        verifyFlush(0);
        durationSource.verifySubscribed().sendItems("1");
        verifyFlush(1);
        listener.writeTerminated();
        durationSource.verifyCancelled();
    }

    @Test
    public void testFlushWithWriteCancelCancelsSource() {
        setupFor(flushWith(durationSource));
        listener.writeCancelled();
        durationSource.verifyCancelled();
    }

    private void setupFor(FlushStrategy strategy) {
        listener = strategy.apply(flushSender);
        listener.writeStarted();
    }

    private void setupForBatch(int batchSize) {
        setupFor(FlushStrategy.batchFlush(batchSize, durationSource.map(s -> 1L)));
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

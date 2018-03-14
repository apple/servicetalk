/**
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
package io.servicetalk.transport;

import io.servicetalk.concurrent.api.MockedSubscriberRule;
import io.servicetalk.concurrent.api.TestPublisher;
import io.servicetalk.transport.api.FlushStrategy;
import io.servicetalk.transport.api.FlushStrategyHolder;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.Mockito;

import static io.servicetalk.concurrent.api.DeliberateException.DELIBERATE_EXCEPTION;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyZeroInteractions;

public class FlushStrategiesTest {

    @Rule public final MockedSubscriberRule<String> sourceSub = new MockedSubscriberRule<>();

    private Runnable flushListener;
    private TestPublisher<String> durationSource;
    private TestPublisher<String> source;

    @Before
    public void setUp() {
        flushListener = mock(Runnable.class);
        durationSource = new TestPublisher<String>().sendOnSubscribe();
        source = new TestPublisher<String>().sendOnSubscribe();
    }

    @Test
    public void testFlushOnEach() {
        setupForEach();
        source.sendItems("Hello1", "Hello2");

        sourceSub.verifyItems("Hello1", "Hello2");
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
    public void testSourceEmitErrorForFlushOnEach() {
        setupForEach();
        source.fail();
        sourceSub.verifyFailure(DELIBERATE_EXCEPTION);
    }

    @Test
    public void testSourceEmitErrorForBatchFlush() {
        setupForBatch(2);
        source.fail();
        sourceSub.verifyFailure(DELIBERATE_EXCEPTION);
        durationSource.verifyCancelled();
    }

    @Test
    public void testDurationComplete() {
        setupForBatch(2);
        durationSource.onComplete();
        source.sendItems("Hello1");
        sourceSub.verifyItems("Hello1");
        verifyZeroInteractions(flushListener);
        source.onComplete();
        sourceSub.verifySuccess();
        verifyFlush(1);
    }

    @Test
    public void testDurationEmitError() {
        setupForBatch(2);
        durationSource.fail();
        sourceSub.verifyFailure(DELIBERATE_EXCEPTION);
        source.verifyCancelled();
    }

    @Test
    public void testDurationEmitErrorPostComplete() {
        setupForBatch(2);
        source.onComplete();
        durationSource.failIfSubscriberActive();
        sourceSub.verifySuccess();
        source.verifyNotCancelled();
    }

    @Test
    public void testDurationCompletePostComplete() {
        setupForBatch(2);
        source.onComplete();
        durationSource.completeIfSubscriberActive();
        sourceSub.verifySuccess();
        source.verifyNotCancelled();
    }

    @Test
    public void testFlushBeforeEndComplete() {
        setupForEnd();
        source.onNext("Hello1");
        verifyFlush(0);
        source.onComplete();
        verifyFlush(1);
    }

    @Test
    public void testFlushBeforeEndFailure() {
        setupForEnd();
        source.onNext("Hello1");
        verifyFlush(0);
        source.fail();
        verifyFlush(1);
    }

    private void setupForEach() {
        setupFor(FlushStrategy.<String>flushOnEach().apply(source));
    }

    private void setupForBatch(int batchSize) {
        setupFor(FlushStrategy.<String>batchFlush(batchSize, durationSource.map(s -> 1L)).apply(source));
    }

    private void setupForEnd() {
        setupFor(FlushStrategy.<String>flushBeforeEnd().apply(source));
    }

    private void setupFor(FlushStrategyHolder<String> strategy) {
        sourceSub.subscribe(strategy.getSource()).request(Long.MAX_VALUE);
        strategy.getFlushSignals().listen(flushListener);
    }

    private void testBatch(int batchSize, int sendItemCount) {
        String[] items = new String[sendItemCount];
        for (int i = 0; i < sendItemCount; i++) {
            items[i] = "Hello" + i;
        }
        source.sendItems(items);

        sourceSub.verifyItems(items);

        verifyFlush(sendItemCount / batchSize);
    }

    private void verifyFlush(int flushCount) {
        if (flushCount > 0) {
            Mockito.verify(flushListener, Mockito.times(flushCount)).run();
        } else {
            verifyZeroInteractions(flushListener);
        }
    }
}

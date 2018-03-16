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

import io.servicetalk.concurrent.api.MockedSubscriberRule;
import io.servicetalk.concurrent.api.TestPublisher;
import io.servicetalk.transport.api.FlushStrategyHolder;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.Mockito;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyZeroInteractions;

public class ReadAwareFlushStrategiesTest {

    @Rule
    public final MockedSubscriberRule<String> sourceSub = new MockedSubscriberRule<>();

    private Runnable flushListener;
    private TestPublisher<String> source;
    private volatile boolean readInProgress;

    @Before
    public void setUp() {
        flushListener = mock(Runnable.class);
        source = new TestPublisher<String>().sendOnSubscribe();
    }

    @Test
    public void testFlushOnReadCompleteReadActive() {
        ReadAwareFlushStrategyHolder<String> holder = setupForReadOnly(10);
        readInProgress = true;
        source.sendItems("Hello1", "Hello2");
        verifyFlush(0);
        readInProgress = false;
        holder.readComplete();
        verifyFlush(1);
    }

    @Test
    public void testFlushOnReadCompleteReadActiveMaxPending() {
        setupForReadOnly(2);
        readInProgress = true;
        source.onNext("Hello1");
        verifyFlush(0);
        source.onNext("Hello2");
        verifyFlush(1);
    }

    @Test
    public void testFlushOnReadCompleteReadInactive() {
        setupForReadOnly(2);
        source.sendItems("Hello1", "Hello2");
        verifyFlush(2);
    }

    private ReadAwareFlushStrategyHolder<String> setupForReadOnly(int maxPendingWrite) {
        ReadAwareFlushStrategyHolder<String> holder = (ReadAwareFlushStrategyHolder<String>) ReadAwareFlushStrategyHolder.<String>flushOnReadComplete(maxPendingWrite).apply(source);
        setupFor(holder);
        holder.setReadInProgressSupplier(() -> readInProgress);
        return holder;
    }

    private void setupFor(FlushStrategyHolder<String> strategy) {
        sourceSub.subscribe(strategy.getSource()).request(Long.MAX_VALUE);
        strategy.getFlushSignals().listen(flushListener);
    }

    private void verifyFlush(int flushCount) {
        if (flushCount > 0) {
            verify(flushListener, Mockito.times(flushCount)).run();
        } else {
            verifyZeroInteractions(flushListener);
        }
    }
}

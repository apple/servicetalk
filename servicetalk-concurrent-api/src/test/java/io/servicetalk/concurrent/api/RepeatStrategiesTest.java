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
package io.servicetalk.concurrent.api;

import org.junit.Test;

import java.time.Duration;
import java.util.function.IntFunction;

import static io.servicetalk.concurrent.api.RepeatStrategies.TerminateRepeatException;
import static io.servicetalk.concurrent.api.RepeatStrategies.repeatWithConstantBackoffDeltaJitter;
import static io.servicetalk.concurrent.api.RepeatStrategies.repeatWithConstantBackoffFullJitter;
import static io.servicetalk.concurrent.api.RepeatStrategies.repeatWithExponentialBackoffDeltaJitter;
import static io.servicetalk.concurrent.api.SourceAdapters.toSource;
import static java.time.Duration.ofDays;
import static java.time.Duration.ofMillis;
import static java.time.Duration.ofNanos;
import static java.time.Duration.ofSeconds;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.instanceOf;
import static org.mockito.Mockito.verifyNoMoreInteractions;

public class RepeatStrategiesTest extends RedoStrategiesTest {

    @Test
    public void testBackoff() throws Exception {
        Duration backoff = ofSeconds(1);
        RepeatStrategy strategy = new RepeatStrategy(repeatWithConstantBackoffDeltaJitter(2, backoff, ofNanos(1),
                timerExecutor));
        TestCollectingCompletableSubscriber subscriber = strategy.invokeAndListen();
        verifyDelayWithDeltaJitter(backoff.toNanos(), 1, 1);
        timers.take().verifyListenCalled().onComplete();
        subscriber.awaitOnComplete();
        verifyNoMoreInteractions(timerExecutor);
    }

    @Test
    public void testBackoffMaxRepeats() throws Exception {
        Duration backoff = ofSeconds(1);
        testMaxRepeats(repeatWithConstantBackoffFullJitter(1, backoff, timerExecutor), backoff);
    }

    @Test
    public void testExpBackoff() throws Exception {
        Duration initialDelay = ofSeconds(1);
        RepeatStrategy strategy = new RepeatStrategy(repeatWithConstantBackoffFullJitter(2, initialDelay,
                timerExecutor));
        TestCollectingCompletableSubscriber subscriber = strategy.invokeAndListen();
        verifyDelayWithFullJitter(initialDelay.toNanos(), 1);
        timers.take().verifyListenCalled().onComplete();
        subscriber.awaitOnComplete();
        verifyNoMoreInteractions(timerExecutor);

        subscriber = strategy.invokeAndListen();
        verifyDelayWithFullJitter(initialDelay.toNanos() << 1, 2);
        timers.take().verifyListenCalled().onComplete();
        subscriber.awaitOnComplete();
        verifyNoMoreInteractions(timerExecutor);
    }

    @Test
    public void testExpBackoffMaxRepeats() throws Exception {
        Duration backoff = ofSeconds(1);
        testMaxRepeats(repeatWithConstantBackoffFullJitter(1, backoff, timerExecutor), backoff);
    }

    @Test
    public void testExpBackoffWithJitter() throws Exception {
        Duration initialDelay = ofSeconds(1);
        Duration jitter = ofMillis(500);
        RepeatStrategy strategy = new RepeatStrategy(repeatWithExponentialBackoffDeltaJitter(2, initialDelay, jitter,
                ofDays(10), timerExecutor));
        TestCollectingCompletableSubscriber subscriber = strategy.invokeAndListen();
        verifyDelayWithDeltaJitter(initialDelay.toNanos(), jitter.toNanos(), 1);

        timers.take().verifyListenCalled().onComplete();
        subscriber.awaitOnComplete();
        verifyNoMoreInteractions(timerExecutor);

        subscriber = strategy.invokeAndListen();
        long nextDelay = initialDelay.toNanos() << 1;
        verifyDelayWithDeltaJitter(nextDelay, jitter.toNanos(), 2);
        timers.take().verifyListenCalled().onComplete();
        subscriber.awaitOnComplete();
        verifyNoMoreInteractions(timerExecutor);
    }

    @Test
    public void testExpBackoffWithJitterMaxRepeats() throws Exception {
        Duration backoff = ofSeconds(1);
        Duration jitter = ofMillis(500);
        testMaxRepeats(repeatWithExponentialBackoffDeltaJitter(1, backoff, jitter, ofDays(10), timerExecutor),
                () -> verifyDelayWithDeltaJitter(backoff.toNanos(), jitter.toNanos(), 1));
    }

    private void testMaxRepeats(IntFunction<Completable> actualStrategy, Duration backoff) throws Exception {
        testMaxRepeats(actualStrategy, () -> verifyDelayWithFullJitter(backoff.toNanos(), 1));
    }

    private void testMaxRepeats(IntFunction<Completable> actualStrategy, Runnable verifyTimerProvider)
            throws Exception {
        RepeatStrategy strategy = new RepeatStrategy(actualStrategy);
        TestCollectingCompletableSubscriber subscriber = strategy.invokeAndListen();
        verifyTimerProvider.run();
        timers.take().verifyListenCalled().onComplete();
        subscriber.awaitOnComplete();
        verifyNoMoreInteractions(timerExecutor);

        subscriber = strategy.invokeAndListen();
        verifyNoMoreInteractions(timerExecutor);
        assertThat(subscriber.awaitOnError(), instanceOf(TerminateRepeatException.class));
    }

    private static final class RepeatStrategy {

        private int count;
        private final IntFunction<Completable> actual;

        RepeatStrategy(IntFunction<Completable> actual) {
            this.actual = actual;
        }

        TestCollectingCompletableSubscriber invokeAndListen() {
            TestCollectingCompletableSubscriber subscriber = new TestCollectingCompletableSubscriber();
            toSource(actual.apply(++count)).subscribe(subscriber);
            return subscriber;
        }
    }
}

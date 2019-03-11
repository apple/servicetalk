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
import static io.servicetalk.concurrent.api.RepeatStrategies.repeatWithConstantBackoff;
import static io.servicetalk.concurrent.api.RepeatStrategies.repeatWithExponentialBackoff;
import static io.servicetalk.concurrent.api.RepeatStrategies.repeatWithExponentialBackoffAndJitter;
import static java.time.Duration.ofSeconds;
import static java.util.concurrent.TimeUnit.NANOSECONDS;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;

public class RepeatStrategiesTest extends RedoStrategiesTest {

    @Test
    public void testBackoff() throws Exception {
        Duration backoff = ofSeconds(1);
        RepeatStrategy strategy = new RepeatStrategy(repeatWithConstantBackoff(2, backoff, timerExecutor));
        LegacyMockedCompletableListenerRule signalListener = strategy.invokeAndListen();
        verify(timerExecutor).timer(backoff.toNanos(), NANOSECONDS);
        timers.take().verifyListenCalled().onComplete();
        signalListener.verifyCompletion();
        verifyNoMoreInteractions(timerExecutor);
    }

    @Test
    public void testBackoffMaxRepeats() throws Exception {
        Duration backoff = ofSeconds(1);
        testMaxRepeats(repeatWithConstantBackoff(1, backoff, timerExecutor), backoff);
    }

    @Test
    public void testExpBackoff() throws Exception {
        Duration initialDelay = ofSeconds(1);
        RepeatStrategy strategy = new RepeatStrategy(repeatWithExponentialBackoff(2, initialDelay, timerExecutor));
        LegacyMockedCompletableListenerRule signalListener = strategy.invokeAndListen();
        verify(timerExecutor).timer(initialDelay.toNanos(), NANOSECONDS);
        timers.take().verifyListenCalled().onComplete();
        signalListener.verifyCompletion();
        verifyNoMoreInteractions(timerExecutor);

        signalListener = strategy.invokeAndListen();
        verify(timerExecutor).timer(initialDelay.toNanos() << 1, NANOSECONDS);
        timers.take().verifyListenCalled().onComplete();
        signalListener.verifyCompletion();
        verifyNoMoreInteractions(timerExecutor);
    }

    @Test
    public void testExpBackoffMaxRepeats() throws Exception {
        Duration backoff = ofSeconds(1);
        testMaxRepeats(repeatWithExponentialBackoff(1, backoff, timerExecutor), backoff);
    }

    @Test
    public void testExpBackoffWithJitter() throws Exception {
        Duration initialDelay = ofSeconds(1);
        RepeatStrategy strategy = new RepeatStrategy(repeatWithExponentialBackoffAndJitter(2, initialDelay,
                timerExecutor));
        LegacyMockedCompletableListenerRule signalListener = strategy.invokeAndListen();
        verifyDelayWithJitter(initialDelay.toNanos(), 1);

        timers.take().verifyListenCalled().onComplete();
        signalListener.verifyCompletion();
        verifyNoMoreInteractions(timerExecutor);

        signalListener = strategy.invokeAndListen();
        long nextDelay = initialDelay.toNanos() << 1;
        verifyDelayWithJitter(nextDelay, 2);
        timers.take().verifyListenCalled().onComplete();
        signalListener.verifyCompletion();
        verifyNoMoreInteractions(timerExecutor);
    }

    @Test
    public void testExpBackoffWithJitterMaxRepeats() throws Exception {
        Duration backoff = ofSeconds(1);
        testMaxRepeats(repeatWithExponentialBackoffAndJitter(1, backoff, timerExecutor),
                () -> verifyDelayWithJitter(backoff.toNanos(), 1));
    }

    private void testMaxRepeats(IntFunction<Completable> actualStrategy, Duration backoff) throws Exception {
        testMaxRepeats(actualStrategy, () -> verify(timerExecutor).timer(backoff.toNanos(), NANOSECONDS));
    }

    private void testMaxRepeats(IntFunction<Completable> actualStrategy, Runnable verifyTimerProvider)
            throws Exception {
        RepeatStrategy strategy = new RepeatStrategy(actualStrategy);
        LegacyMockedCompletableListenerRule signalListener = strategy.invokeAndListen();
        verifyTimerProvider.run();
        timers.take().verifyListenCalled().onComplete();
        signalListener.verifyCompletion();
        verifyNoMoreInteractions(timerExecutor);

        signalListener = strategy.invokeAndListen();
        verifyNoMoreInteractions(timerExecutor);
        signalListener.verifyFailure(TerminateRepeatException.class);
    }

    private static final class RepeatStrategy {

        private int count;
        private final IntFunction<Completable> actual;

        RepeatStrategy(IntFunction<Completable> actual) {
            this.actual = actual;
        }

        LegacyMockedCompletableListenerRule invokeAndListen() {
            LegacyMockedCompletableListenerRule listenerRule = new LegacyMockedCompletableListenerRule();
            listenerRule.listen(actual.apply(++count));
            return listenerRule;
        }
    }
}

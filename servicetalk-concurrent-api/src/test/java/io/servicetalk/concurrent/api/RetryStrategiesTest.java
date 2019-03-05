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

import io.servicetalk.concurrent.internal.DeliberateException;

import org.junit.Test;

import java.time.Duration;

import static io.servicetalk.concurrent.api.RetryStrategies.retryWithConstantBackoff;
import static io.servicetalk.concurrent.api.RetryStrategies.retryWithConstantBackoffAndJitter;
import static io.servicetalk.concurrent.api.RetryStrategies.retryWithExponentialBackoff;
import static io.servicetalk.concurrent.api.RetryStrategies.retryWithExponentialBackoffAndJitter;
import static io.servicetalk.concurrent.internal.DeliberateException.DELIBERATE_EXCEPTION;
import static java.time.Duration.ofSeconds;
import static java.util.concurrent.TimeUnit.NANOSECONDS;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;

public class RetryStrategiesTest extends RedoStrategiesTest {

    @Test
    public void testBackoff() throws Exception {
        Duration backoff = ofSeconds(1);
        RetryStrategy strategy = new RetryStrategy(retryWithConstantBackoff(2, cause -> true, backoff,
                timerExecutor));
        LegacyMockedCompletableListenerRule signalListener = strategy.invokeAndListen(DELIBERATE_EXCEPTION);
        verify(timerExecutor).timer(backoff.toNanos(), NANOSECONDS);
        timers.take().verifyListenCalled().onComplete();
        signalListener.verifyCompletion();
        verifyNoMoreInteractions(timerExecutor);
    }

    @Test
    public void testBackoffWithJitter() throws Exception {
        Duration backoff = ofSeconds(1);
        RetryStrategy strategy = new RetryStrategy(retryWithConstantBackoffAndJitter(2, cause -> true,
                backoff, timerExecutor));
        LegacyMockedCompletableListenerRule signalListener = strategy.invokeAndListen(DELIBERATE_EXCEPTION);
        verifyDelayWithJitter(backoff.toNanos(), 1);
        timers.take().verifyListenCalled().onComplete();
        signalListener.verifyCompletion();
        verifyNoMoreInteractions(timerExecutor);
    }

    @Test
    public void testBackoffMaxRetries() throws Exception {
        Duration backoff = ofSeconds(1);
        testMaxRetries(retryWithExponentialBackoff(1, cause -> true, backoff, timerExecutor), backoff);
    }

    @Test
    public void testBackoffCauseFilter() {
        testCauseFilter(retryWithConstantBackoff(1, cause -> cause instanceof IllegalStateException,
                ofSeconds(1), timerExecutor));
    }

    @Test
    public void testExpBackoff() throws Exception {
        Duration initialDelay = ofSeconds(1);
        RetryStrategy strategy = new RetryStrategy(retryWithExponentialBackoff(2, cause -> true, initialDelay,
                timerExecutor));
        LegacyMockedCompletableListenerRule signalListener = strategy.invokeAndListen(DELIBERATE_EXCEPTION);
        verify(timerExecutor).timer(initialDelay.toNanos(), NANOSECONDS);
        timers.take().verifyListenCalled().onComplete();
        signalListener.verifyCompletion();
        verifyNoMoreInteractions(timerExecutor);

        signalListener = strategy.invokeAndListen(DELIBERATE_EXCEPTION);
        verify(timerExecutor).timer(initialDelay.toNanos() << 1, NANOSECONDS);
        timers.take().verifyListenCalled().onComplete();
        signalListener.verifyCompletion();
        verifyNoMoreInteractions(timerExecutor);
    }

    @Test
    public void testExpBackoffMaxRetries() throws Exception {
        Duration backoff = ofSeconds(1);
        testMaxRetries(retryWithExponentialBackoff(1, cause -> true, backoff, timerExecutor), backoff);
    }

    @Test
    public void testExpBackoffCauseFilter() {
        testCauseFilter(retryWithExponentialBackoff(1, cause -> cause instanceof IllegalStateException,
                ofSeconds(1), timerExecutor));
    }

    @Test
    public void testExpBackoffWithJitter() throws Exception {
        Duration initialDelay = ofSeconds(1);
        RetryStrategy strategy = new RetryStrategy(retryWithExponentialBackoffAndJitter(2, cause -> true,
                initialDelay, timerExecutor));
        LegacyMockedCompletableListenerRule signalListener = strategy.invokeAndListen(DELIBERATE_EXCEPTION);
        verifyDelayWithJitter(initialDelay.toNanos(), 1);

        timers.take().verifyListenCalled().onComplete();
        signalListener.verifyCompletion();
        verifyNoMoreInteractions(timerExecutor);

        signalListener = strategy.invokeAndListen(DELIBERATE_EXCEPTION);
        long nextDelay = initialDelay.toNanos() << 1;
        verifyDelayWithJitter(nextDelay, 2);
        timers.take().verifyListenCalled().onComplete();
        signalListener.verifyCompletion();
        verifyNoMoreInteractions(timerExecutor);
    }

    @Test
    public void testExpBackoffWithJitterMaxRetries() throws Exception {
        Duration backoff = ofSeconds(1);
        testMaxRetries(retryWithExponentialBackoffAndJitter(1, cause -> true, backoff, timerExecutor),
                () -> verifyDelayWithJitter(backoff.toNanos(), 1));
    }

    @Test
    public void testExpBackoffWithJitterCauseFilter() {
        testCauseFilter(retryWithExponentialBackoffAndJitter(1,
                cause -> cause instanceof IllegalStateException, ofSeconds(1), timerExecutor));
    }

    private void testCauseFilter(BiIntFunction<Throwable, Completable> actualStrategy) {
        RetryStrategy strategy = new RetryStrategy(actualStrategy);
        LegacyMockedCompletableListenerRule signalListener = strategy.invokeAndListen(DELIBERATE_EXCEPTION);
        verifyNoMoreInteractions(timerExecutor);
        signalListener.verifyFailure(DELIBERATE_EXCEPTION);
    }

    private void testMaxRetries(BiIntFunction<Throwable, Completable> actualStrategy, Duration backoff)
            throws Exception {
        testMaxRetries(actualStrategy, () -> verify(timerExecutor).timer(backoff.toNanos(), NANOSECONDS));
    }

    private void testMaxRetries(BiIntFunction<Throwable, Completable> actualStrategy, Runnable verifyTimerProvider)
            throws Exception {
        RetryStrategy strategy = new RetryStrategy(actualStrategy);
        LegacyMockedCompletableListenerRule signalListener = strategy.invokeAndListen(DELIBERATE_EXCEPTION);
        verifyTimerProvider.run();
        timers.take().verifyListenCalled().onComplete();
        signalListener.verifyCompletion();
        verifyNoMoreInteractions(timerExecutor);

        DeliberateException de = new DeliberateException();
        signalListener = strategy.invokeAndListen(de);
        verifyNoMoreInteractions(timerExecutor);
        signalListener.verifyFailure(de);
    }

    private static final class RetryStrategy {

        private int count;
        private final BiIntFunction<Throwable, Completable> actual;

        RetryStrategy(BiIntFunction<Throwable, Completable> actual) {
            this.actual = actual;
        }

        LegacyMockedCompletableListenerRule invokeAndListen(Throwable cause) {
            LegacyMockedCompletableListenerRule listenerRule = new LegacyMockedCompletableListenerRule();
            listenerRule.listen(actual.apply(++count, cause));
            return listenerRule;
        }
    }
}

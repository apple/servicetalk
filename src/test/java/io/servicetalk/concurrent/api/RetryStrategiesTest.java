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
package io.servicetalk.concurrent.api;

import org.junit.Test;

import java.time.Duration;

import static io.servicetalk.concurrent.api.DeliberateException.DELIBERATE_EXCEPTION;
import static io.servicetalk.concurrent.api.RetryStrategies.retryWithConstantBackoff;
import static io.servicetalk.concurrent.api.RetryStrategies.retryWithExponentialBackoff;
import static io.servicetalk.concurrent.api.RetryStrategies.retryWithExponentialBackoffAndJitter;
import static java.time.Duration.ofSeconds;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;

public class RetryStrategiesTest extends RedoStrategiesTest {

    @Test
    public void testBackoff() throws Exception {
        Duration backoff = ofSeconds(1);
        RetryStrategy strategy = new RetryStrategy(retryWithConstantBackoff(2, cause -> true, backoff, timerProvider));
        MockedCompletableListenerRule signalListener = strategy.invokeAndListen(DELIBERATE_EXCEPTION);
        verify(timerProvider).apply(backoff.toNanos());
        timers.take().verifyListenCalled().onComplete();
        signalListener.verifyCompletion();
        verifyNoMoreInteractions(timerProvider);
    }

    @Test
    public void testBackoffMaxRetries() throws Exception {
        Duration backoff = ofSeconds(1);
        testMaxRetries(retryWithExponentialBackoff(1, cause -> true, backoff, timerProvider), backoff);
    }

    @Test
    public void testBackoffCauseFilter() {
        testCauseFilter(retryWithConstantBackoff(1, cause -> cause instanceof IllegalStateException, ofSeconds(1), timerProvider));
    }

    @Test
    public void testExpBackoff() throws Exception {
        Duration initialDelay = ofSeconds(1);
        RetryStrategy strategy = new RetryStrategy(retryWithExponentialBackoff(2, cause -> true, initialDelay, timerProvider));
        MockedCompletableListenerRule signalListener = strategy.invokeAndListen(DELIBERATE_EXCEPTION);
        verify(timerProvider).apply(initialDelay.toNanos());
        timers.take().verifyListenCalled().onComplete();
        signalListener.verifyCompletion();
        verifyNoMoreInteractions(timerProvider);

        signalListener = strategy.invokeAndListen(DELIBERATE_EXCEPTION);
        verify(timerProvider).apply(initialDelay.toNanos() << 1);
        timers.take().verifyListenCalled().onComplete();
        signalListener.verifyCompletion();
        verifyNoMoreInteractions(timerProvider);
    }

    @Test
    public void testExpBackoffMaxRetries() throws Exception {
        Duration backoff = ofSeconds(1);
        testMaxRetries(retryWithExponentialBackoff(1, cause -> true, backoff, timerProvider), backoff);
    }

    @Test
    public void testExpBackoffCauseFilter() {
        testCauseFilter(retryWithExponentialBackoff(1, cause -> cause instanceof IllegalStateException, ofSeconds(1), timerProvider));
    }

    @Test
    public void testExpBackoffWithJitter() throws Exception {
        Duration initialDelay = ofSeconds(1);
        RetryStrategy strategy = new RetryStrategy(retryWithExponentialBackoffAndJitter(2, cause -> true, initialDelay, timerProvider));
        MockedCompletableListenerRule signalListener = strategy.invokeAndListen(DELIBERATE_EXCEPTION);
        verifyDelayWithJitter(initialDelay.toNanos(), 1);

        timers.take().verifyListenCalled().onComplete();
        signalListener.verifyCompletion();
        verifyNoMoreInteractions(timerProvider);

        signalListener = strategy.invokeAndListen(DELIBERATE_EXCEPTION);
        long nextDelay = initialDelay.toNanos() << 1;
        verifyDelayWithJitter(nextDelay, 2);
        timers.take().verifyListenCalled().onComplete();
        signalListener.verifyCompletion();
        verifyNoMoreInteractions(timerProvider);
    }

    @Test
    public void testExpBackoffWithJitterMaxRetries() throws Exception {
        Duration backoff = ofSeconds(1);
        testMaxRetries(retryWithExponentialBackoffAndJitter(1, cause -> true, backoff, timerProvider),
                () -> verifyDelayWithJitter(backoff.toNanos(), 1));
    }

    @Test
    public void testExpBackoffWithJitterCauseFilter() {
        testCauseFilter(retryWithExponentialBackoffAndJitter(1, cause -> cause instanceof IllegalStateException, ofSeconds(1), timerProvider));
    }

    private void testCauseFilter(BiIntFunction<Throwable, Completable> actualStrategy) {
        RetryStrategy strategy = new RetryStrategy(actualStrategy);
        MockedCompletableListenerRule signalListener = strategy.invokeAndListen(DELIBERATE_EXCEPTION);
        verifyNoMoreInteractions(timerProvider);
        signalListener.verifyFailure(DELIBERATE_EXCEPTION);
    }

    private void testMaxRetries(BiIntFunction<Throwable, Completable> actualStrategy, Duration backoff) throws Exception {
        testMaxRetries(actualStrategy, () -> verify(timerProvider).apply(backoff.toNanos()));
    }

    private void testMaxRetries(BiIntFunction<Throwable, Completable> actualStrategy, Runnable verifyTimerProvider) throws Exception {
        RetryStrategy strategy = new RetryStrategy(actualStrategy);
        MockedCompletableListenerRule signalListener = strategy.invokeAndListen(DELIBERATE_EXCEPTION);
        verifyTimerProvider.run();
        timers.take().verifyListenCalled().onComplete();
        signalListener.verifyCompletion();
        verifyNoMoreInteractions(timerProvider);

        DeliberateException de = new DeliberateException();
        signalListener = strategy.invokeAndListen(de);
        verifyNoMoreInteractions(timerProvider);
        signalListener.verifyFailure(de);
    }

    private static final class RetryStrategy {

        private int count;
        private final BiIntFunction<Throwable, Completable> actual;

        RetryStrategy(BiIntFunction<Throwable, Completable> actual) {
            this.actual = actual;
        }

        MockedCompletableListenerRule invokeAndListen(Throwable cause) {
            MockedCompletableListenerRule listenerRule = new MockedCompletableListenerRule();
            listenerRule.listen(actual.apply(++count, cause));
            return listenerRule;
        }
    }
}

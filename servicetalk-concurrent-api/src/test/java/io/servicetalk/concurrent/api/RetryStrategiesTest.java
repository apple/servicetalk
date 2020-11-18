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

import static io.servicetalk.concurrent.api.RetryStrategies.retryWithConstantBackoffDeltaJitter;
import static io.servicetalk.concurrent.api.RetryStrategies.retryWithExponentialBackoffDeltaJitter;
import static io.servicetalk.concurrent.api.RetryStrategies.retryWithExponentialBackoffFullJitter;
import static io.servicetalk.concurrent.api.SourceAdapters.toSource;
import static io.servicetalk.concurrent.internal.DeliberateException.DELIBERATE_EXCEPTION;
import static java.time.Duration.ofDays;
import static java.time.Duration.ofMillis;
import static java.time.Duration.ofNanos;
import static java.time.Duration.ofSeconds;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.mockito.Mockito.verifyNoMoreInteractions;

public class RetryStrategiesTest extends RedoStrategiesTest {

    @Test
    public void testBackoff() throws Exception {
        Duration backoff = ofSeconds(1);
        RetryStrategy strategy = new RetryStrategy(retryWithConstantBackoffDeltaJitter(2, cause -> true, backoff,
                ofNanos(1), timerExecutor));
        TestCollectingCompletableSubscriber subscriber = strategy.invokeAndListen(DELIBERATE_EXCEPTION);
        verifyDelayWithDeltaJitter(backoff.toNanos(), 1, 1);
        timers.take().verifyListenCalled().onComplete();
        subscriber.awaitOnComplete();
        verifyNoMoreInteractions(timerExecutor);
    }

    @Test
    public void testBackoffWithJitter() throws Exception {
        Duration backoff = ofSeconds(1);
        Duration jitter = ofMillis(10);
        RetryStrategy strategy = new RetryStrategy(retryWithConstantBackoffDeltaJitter(2, cause -> true,
                backoff, jitter, timerExecutor));
        TestCollectingCompletableSubscriber subscriber = strategy.invokeAndListen(DELIBERATE_EXCEPTION);
        verifyDelayWithDeltaJitter(backoff.toNanos(), jitter.toNanos(), 1);
        timers.take().verifyListenCalled().onComplete();
        subscriber.awaitOnComplete();
        verifyNoMoreInteractions(timerExecutor);
    }

    @Test
    public void testBackoffMaxRetries() throws Exception {
        Duration backoff = ofSeconds(1);
        testMaxRetries(retryWithExponentialBackoffFullJitter(1, cause -> true, backoff, ofDays(10), timerExecutor),
                backoff);
    }

    @Test
    public void testBackoffCauseFilter() throws Exception {
        testCauseFilter(retryWithExponentialBackoffFullJitter(1, cause -> cause instanceof IllegalStateException,
                ofSeconds(1), ofDays(10), timerExecutor));
    }

    @Test
    public void testExpBackoff() throws Exception {
        Duration initialDelay = ofSeconds(1);
        RetryStrategy strategy = new RetryStrategy(retryWithExponentialBackoffFullJitter(2, cause -> true, initialDelay,
                ofDays(10), timerExecutor));
        TestCollectingCompletableSubscriber subscriber = strategy.invokeAndListen(DELIBERATE_EXCEPTION);
        verifyDelayWithFullJitter(initialDelay.toNanos(), 1);
        timers.take().verifyListenCalled().onComplete();
        subscriber.awaitOnComplete();
        verifyNoMoreInteractions(timerExecutor);

        subscriber = strategy.invokeAndListen(DELIBERATE_EXCEPTION);
        verifyDelayWithFullJitter(initialDelay.toNanos() << 1, 2);
        timers.take().verifyListenCalled().onComplete();
        subscriber.awaitOnComplete();
        verifyNoMoreInteractions(timerExecutor);
    }

    @Test
    public void testExpBackoffMaxRetries() throws Exception {
        Duration backoff = ofSeconds(1);
        testMaxRetries(retryWithExponentialBackoffFullJitter(1, cause -> true, backoff, ofDays(10), timerExecutor),
                backoff);
    }

    @Test
    public void testExpBackoffCauseFilter() throws Exception {
        testCauseFilter(retryWithExponentialBackoffFullJitter(1, cause -> cause instanceof IllegalStateException,
                ofSeconds(1), ofDays(10), timerExecutor));
    }

    @Test
    public void testExpBackoffWithJitter() throws Exception {
        Duration initialDelay = ofSeconds(1);
        Duration jitter = ofMillis(10);
        RetryStrategy strategy = new RetryStrategy(retryWithExponentialBackoffDeltaJitter(2, cause -> true,
                initialDelay, jitter, ofDays(10), timerExecutor));
        TestCollectingCompletableSubscriber subscriber = strategy.invokeAndListen(DELIBERATE_EXCEPTION);
        verifyDelayWithDeltaJitter(initialDelay.toNanos(), jitter.toNanos(), 1);

        timers.take().verifyListenCalled().onComplete();
        subscriber.awaitOnComplete();
        verifyNoMoreInteractions(timerExecutor);

        subscriber = strategy.invokeAndListen(DELIBERATE_EXCEPTION);
        long nextDelay = initialDelay.toNanos() << 1;
        verifyDelayWithDeltaJitter(nextDelay, jitter.toNanos(), 2);
        timers.take().verifyListenCalled().onComplete();
        subscriber.awaitOnComplete();
        verifyNoMoreInteractions(timerExecutor);
    }

    @Test
    public void testExpBackoffWithJitterMaxRetries() throws Exception {
        Duration backoff = ofSeconds(1);
        Duration jitter = ofMillis(10);
        testMaxRetries(retryWithExponentialBackoffDeltaJitter(1, cause -> true, backoff, jitter, ofDays(10),
                timerExecutor), () -> verifyDelayWithDeltaJitter(backoff.toNanos(), jitter.toNanos(), 1));
    }

    @Test
    public void testExpBackoffWithJitterCauseFilter() throws Exception {
        testCauseFilter(retryWithExponentialBackoffDeltaJitter(1, cause -> cause instanceof IllegalStateException,
                ofSeconds(1), ofMillis(10), ofDays(10), timerExecutor));
    }

    private void testCauseFilter(BiIntFunction<Throwable, Completable> actualStrategy) throws Exception {
        RetryStrategy strategy = new RetryStrategy(actualStrategy);
        TestCollectingCompletableSubscriber subscriber = strategy.invokeAndListen(DELIBERATE_EXCEPTION);
        verifyNoMoreInteractions(timerExecutor);
        assertThat(subscriber.awaitOnError(), is(DELIBERATE_EXCEPTION));
    }

    private void testMaxRetries(BiIntFunction<Throwable, Completable> actualStrategy, Duration backoff)
            throws Exception {
        testMaxRetries(actualStrategy, () -> verifyDelayWithFullJitter(backoff.toNanos(), 1));
    }

    private void testMaxRetries(BiIntFunction<Throwable, Completable> actualStrategy, Runnable verifyTimerProvider)
            throws Exception {
        RetryStrategy strategy = new RetryStrategy(actualStrategy);
        TestCollectingCompletableSubscriber subscriber = strategy.invokeAndListen(DELIBERATE_EXCEPTION);
        verifyTimerProvider.run();
        timers.take().verifyListenCalled().onComplete();
        subscriber.awaitOnComplete();
        verifyNoMoreInteractions(timerExecutor);

        DeliberateException de = new DeliberateException();
        subscriber = strategy.invokeAndListen(de);
        verifyNoMoreInteractions(timerExecutor);
        assertThat(subscriber.awaitOnError(), is(de));
    }

    private static final class RetryStrategy {

        private int count;
        private final BiIntFunction<Throwable, Completable> actual;

        RetryStrategy(BiIntFunction<Throwable, Completable> actual) {
            this.actual = actual;
        }

        TestCollectingCompletableSubscriber invokeAndListen(Throwable cause) {
            TestCollectingCompletableSubscriber subscriber = new TestCollectingCompletableSubscriber();
            toSource(actual.apply(++count, cause)).subscribe(subscriber);
            return subscriber;
        }
    }
}

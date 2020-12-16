/*
 * Copyright © 2020 Apple Inc. and the ServiceTalk project authors
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
package io.servicetalk.concurrent.api.test;

import io.servicetalk.concurrent.CompletableSource;
import io.servicetalk.concurrent.api.AsyncContext;
import io.servicetalk.concurrent.api.AsyncContextMap;
import io.servicetalk.concurrent.api.Executor;
import io.servicetalk.concurrent.api.ExecutorRule;
import io.servicetalk.concurrent.internal.DeliberateException;

import org.junit.ClassRule;
import org.junit.Test;

import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.function.Supplier;

import static io.servicetalk.concurrent.api.Completable.completed;
import static io.servicetalk.concurrent.api.Completable.failed;
import static io.servicetalk.concurrent.api.Completable.never;
import static io.servicetalk.concurrent.api.Processors.newCompletableProcessor;
import static io.servicetalk.concurrent.api.SourceAdapters.fromSource;
import static io.servicetalk.concurrent.api.test.FirstSteps.create;
import static io.servicetalk.concurrent.internal.DeliberateException.DELIBERATE_EXCEPTION;
import static java.time.Duration.ofDays;
import static java.time.Duration.ofMillis;
import static java.time.Duration.ofNanos;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

public class CompletableStepVerifierTest {
    private static final AsyncContextMap.Key<Integer> ASYNC_KEY = AsyncContextMap.Key.newKey();
    @ClassRule
    public static final ExecutorRule<Executor> EXECUTOR_RULE = ExecutorRule.newRule();

    @Test
    public void expectCancellable() {
        create(completed())
                .expectCancellable()
                .expectComplete()
                .verify();
    }

    @Test
    public void expectCancellableTimeout() {
        CountDownLatch latch = new CountDownLatch(1);
        try {
            verifyException(() -> create(completed().publishAndSubscribeOn(EXECUTOR_RULE.executor()))
                    .expectCancellableConsumed(cancellable -> {
                        try {
                            latch.await();
                        } catch (InterruptedException e) {
                            throw new RuntimeException(e);
                        }
                    })
                    .expectComplete()
                    .verify(ofNanos(10)), "expectCancellableConsumed");
        } finally {
            latch.countDown();
        }
    }

    @Test
    public void onComplete() {
        assertNotNull(create(completed())
                .expectComplete()
                .verify());
    }

    @Test
    public void onCompleteDuplicateVerify() throws InterruptedException {
        CountDownLatch latch = new CountDownLatch(2);
        StepVerifier verifier = create(completed())
                .expectCancellableConsumed(cancellable -> {
                    assertNotNull(cancellable);
                    latch.countDown();
                })
                .expectComplete();
        verifier.verify();
        verifier.verify();
        assertTrue(latch.await(10, SECONDS));
    }

    @Test
    public void onCompleteLargeTimeout() {
        assertNotNull(create(completed())
                .expectComplete()
                .verify(ofDays(1)));
    }

    @Test
    public void onCompleteTimeout() {
        verifyException(() -> create(never())
                .expectComplete()
                .verify(ofNanos(10)), "expectComplete");
    }

    @Test
    public void onError() {
        create(failed(DELIBERATE_EXCEPTION))
                .expectError()
                .verify();
    }

    @Test
    public void onErrorFail() {
        verifyException(() -> create(completed())
                .expectError()
                .verify(), "expectError");
    }

    @Test
    public void onErrorClass() {
        create(failed(DELIBERATE_EXCEPTION))
                .expectError(DeliberateException.class)
                .verify();
    }

    @Test
    public void onErrorClassFail() {
        verifyException(() -> create(completed())
                .expectError(DeliberateException.class)
                .verify(), "expectError");
    }

    @Test
    public void onErrorPredicate() {
        create(failed(DELIBERATE_EXCEPTION))
                .expectErrorMatches(error -> error instanceof DeliberateException)
                .verify();
    }

    @Test
    public void onErrorPredicateFail() {
        verifyException(() -> create(completed())
                .expectErrorMatches(error -> error instanceof DeliberateException)
                .verify(), "expectErrorMatches");
    }

    @Test
    public void onErrorConsumer() {
        create(failed(DELIBERATE_EXCEPTION))
                .expectErrorConsumed(error -> assertThat(error, is(DELIBERATE_EXCEPTION)))
                .verify();
    }

    @Test
    public void onErrorConsumerFail() {
        verifyException(() -> create(completed())
                .expectErrorConsumed(error -> assertThat(error, is(DELIBERATE_EXCEPTION)))
                .verify(), "expectErrorConsumed");
    }

    @Test
    public void expectOnSuccessWhenOnError() {
        verifyException(() -> create(failed(DELIBERATE_EXCEPTION))
                    .expectComplete()
                    .verify(), "expectComplete");
    }

    @Test
    public void noSignalsSubscriptionCancelSucceeds() {
        // expectNoSignals and subscription event are dequeued/processed sequentially on the Subscriber thread
        // and the scenario isn't instructed to expect the subscription so we pass the test.
        create(never())
                .expectNoSignals(ofDays(1))
                .thenCancel()
                .verify();
    }

    @Test
    public void noSignalsCompleteFail() {
        verifyException(() -> create(completed())
                .expectCancellable()
                .expectNoSignals(ofDays(1))
                .expectComplete()
                .verify(), "expectNoSignals");
    }

    @Test
    public void noSignalsErrorFails() {
        verifyException(() -> create(failed(DELIBERATE_EXCEPTION))
                .expectCancellable()
                .expectNoSignals(ofDays(1))
                .expectError(DeliberateException.class)
                .verify(), "expectNoSignals");
    }

    @Test
    public void noSignalsAfterSubscriptionSucceeds() {
        create(never())
                .expectCancellable()
                .expectNoSignals(ofMillis(100))
                .thenCancel()
                .verify();
    }

    @Test
    public void thenCancelCompleted() {
        create(completed())
                .thenCancel()
                .verify();
    }

    @Test
    public void thenCancelFailed() {
        create(failed(DELIBERATE_EXCEPTION))
                .thenCancel()
                .verify();
    }

    @Test
    public void thenRun() {
        CompletableSource.Processor processor = newCompletableProcessor();
        create(fromSource(processor))
                .then(processor::onComplete)
                .expectComplete()
                .verify();
    }

    @Test
    public void asyncContextOnError() {
        create(failed(DELIBERATE_EXCEPTION).publishAndSubscribeOn(EXECUTOR_RULE.executor()))
                .expectCancellableConsumed(s -> {
                    assertNotNull(s);
                    AsyncContext.put(ASYNC_KEY, 10);
                })
                .expectErrorConsumed(error -> {
                    assertSame(DELIBERATE_EXCEPTION, error);
                    assertThat(AsyncContext.get(ASYNC_KEY), is(10));
                })
                .verify();
    }

    @Test(expected = DeliberateException.class)
    public void thenRunThrows() {
        create(completed())
                .then(() -> {
                    throw DELIBERATE_EXCEPTION;
                })
                .expectComplete()
                .verify();
    }

    @Test
    public void thenAwaitRespectsDelaysComplete() {
        CompletableSource.Processor processor = newCompletableProcessor();
        new InlineCompletableFirstStep(processor, new DefaultModifiableTimeSource())
                .expectCancellable()
                .expectNoSignals(ofDays(500))
                .thenAwait(ofDays(1000))
                .then(processor::onComplete)
                .expectComplete()
                .verify();
    }

    @Test
    public void thenAwaitRespectsDelaysEqualsFail() {
        thenAwaitRespectsDelaysFail(true);
    }

    @Test
    public void thenAwaitRespectsDelaysGTFail() {
        thenAwaitRespectsDelaysFail(false);
    }

    private static void thenAwaitRespectsDelaysFail(boolean equals) {
        CompletableSource.Processor processor = newCompletableProcessor();
        verifyException(() -> new InlineCompletableFirstStep(processor, new DefaultModifiableTimeSource())
                .expectCancellable()
                .expectNoSignals(ofDays(equals ? 1000 : 1001))
                .thenAwait(ofDays(1000))
                .then(processor::onComplete)
                .expectComplete()
                .verify(), "expectNoSignals");
    }

    private static void verifyException(Supplier<Duration> verifier, String failedTestMethod) {
        PublisherStepVerifierTest.verifyException(verifier, CompletableStepVerifierTest.class.getName(),
                failedTestMethod);
    }
}

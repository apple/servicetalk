/*
 * Copyright © 2020-2021 Apple Inc. and the ServiceTalk project authors
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

import io.servicetalk.concurrent.SingleSource;
import io.servicetalk.concurrent.api.AsyncContext;
import io.servicetalk.concurrent.api.AsyncContextMap;
import io.servicetalk.concurrent.api.Completable;
import io.servicetalk.concurrent.api.Executor;
import io.servicetalk.concurrent.api.ExecutorExtension;
import io.servicetalk.concurrent.api.Single;
import io.servicetalk.concurrent.internal.DeliberateException;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.function.Supplier;

import static io.servicetalk.concurrent.api.Processors.newSingleProcessor;
import static io.servicetalk.concurrent.api.Single.failed;
import static io.servicetalk.concurrent.api.Single.never;
import static io.servicetalk.concurrent.api.Single.succeeded;
import static io.servicetalk.concurrent.api.SourceAdapters.fromSource;
import static io.servicetalk.concurrent.internal.DeliberateException.DELIBERATE_EXCEPTION;
import static java.time.Duration.ofDays;
import static java.time.Duration.ofMillis;
import static java.time.Duration.ofNanos;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SingleStepVerifierTest {
    private static final AsyncContextMap.Key<Integer> ASYNC_KEY = AsyncContextMap.Key.newKey();
    @RegisterExtension
    static final ExecutorExtension<Executor> EXECUTOR_RULE = ExecutorExtension.withCachedExecutor();

    @Test
    void expectCancellable() {
        StepVerifiers.create(succeeded("foo"))
                .expectCancellable()
                .expectSuccess("foo")
                .verify();
    }

    @Test
    void expectCancellableTimeout() {
        CountDownLatch latch = new CountDownLatch(1);
        try {
            verifyException(() -> StepVerifiers.create(succeeded("foo").subscribeOn(EXECUTOR_RULE.executor()))
                    .expectCancellableConsumed(cancellable -> {
                        try {
                            latch.await();
                        } catch (InterruptedException e) {
                            throw new RuntimeException(e);
                        }
                    })
                    .expectSuccess("foo")
                    .verify(ofNanos(10)), "expectCancellableConsumed");
        } finally {
            latch.countDown();
        }
    }

    @Test
    void onSuccessDuplicateVerify() throws InterruptedException {
        CountDownLatch latch = new CountDownLatch(2);
        StepVerifier verifier = StepVerifiers.create(succeeded("foo"))
                .expectCancellableConsumed(cancellable -> {
                    assertNotNull(cancellable);
                    latch.countDown();
                })
                .expectSuccess("foo");
        verifier.verify();
        verifier.verify();
        assertTrue(latch.await(10, SECONDS));
    }

    @Test
    void onSuccessIgnore() {
        StepVerifiers.create(succeeded("foo"))
                .expectSuccess()
                .verify();
    }

    @Test
    void onSuccessIgnoreFail() {
        verifyException(() -> StepVerifiers.create(failed(DELIBERATE_EXCEPTION))
                .expectSuccess()
                .verify(), "expectSuccess");
    }

    @Test
    void onSuccess() {
        assertNotNull(StepVerifiers.create(succeeded("foo"))
                .expectSuccess("foo")
                .verify());
    }

    @Test
    void onSuccessFail() {
        verifyException(() -> StepVerifiers.create(succeeded("foo"))
                .expectSuccess("bar")
                .verify(), "expectSuccess");
    }

    @Test
    void onSuccessPredicate() {
        StepVerifiers.create(succeeded("foo"))
                .expectSuccessMatches("foo"::equals)
                .verify();
    }

    @Test
    void onSuccessPredicateFail() {
        verifyException(() -> StepVerifiers.create(succeeded("foo"))
                .expectSuccessMatches("bar"::equals)
                .verify(), "expectSuccessMatches");
    }

    @Test
    void onSuccessConsumer() {
        StepVerifiers.create(succeeded("foo"))
                .expectSuccessConsumed(t -> assertEquals("foo", t))
                .verify();
    }

    @Test
    void onSuccessConsumerFail() {
        verifyException(() -> StepVerifiers.create(succeeded("foo"))
                .expectSuccessConsumed(t -> assertEquals("bar", t))
                .verify(), "expectSuccessConsumed");
    }

    @Test
    void onSuccessNull() {
        assertNotNull(StepVerifiers.create(succeeded(null))
                .expectSuccess(null)
                .verify());
    }

    @Test
    void onSuccessLargeTimeout() {
        assertNotNull(StepVerifiers.create(succeeded("foo"))
                .expectSuccess("foo")
                .verify(ofDays(1)));
    }

    @Test
    void onSuccessTimeout() {
        verifyException(() -> StepVerifiers.create(never())
                .expectSuccess("foo")
                .verify(ofNanos(10)), "expectSuccess");
    }

    @Test
    void onError() {
        StepVerifiers.create(failed(DELIBERATE_EXCEPTION))
                .expectError()
                .verify();
    }

    @Test
    void onErrorFail() {
        verifyException(() -> StepVerifiers.create(succeeded("foo"))
                .expectError()
                .verify(), "expectError");
    }

    @Test
    void onErrorClass() {
        StepVerifiers.create(failed(DELIBERATE_EXCEPTION))
                .expectError(DeliberateException.class)
                .verify();
    }

    @Test
    void onErrorClassFail() {
        verifyException(() -> StepVerifiers.create(succeeded("foo"))
                .expectError(DeliberateException.class)
                .verify(), "expectError");
    }

    @Test
    void onErrorPredicate() {
        StepVerifiers.create(failed(DELIBERATE_EXCEPTION))
                .expectErrorMatches(error -> error instanceof DeliberateException)
                .verify();
    }

    @Test
    void onErrorPredicateFail() {
        verifyException(() -> StepVerifiers.create(succeeded("foo"))
                .expectErrorMatches(error -> error instanceof DeliberateException)
                .verify(), "expectErrorMatches");
    }

    @Test
    void onErrorConsumer() {
        StepVerifiers.create(failed(DELIBERATE_EXCEPTION))
                .expectErrorConsumed(error -> assertThat(error, is(DELIBERATE_EXCEPTION)))
                .verify();
    }

    @Test
    void onErrorConsumerFail() {
        verifyException(() -> StepVerifiers.create(succeeded("foo"))
                .expectErrorConsumed(error -> assertThat(error, is(DELIBERATE_EXCEPTION)))
                .verify(), "expectErrorConsumed");
    }

    @Test
    void expectOnSuccessWhenOnError() {
        verifyException(() -> StepVerifiers.create(failed(DELIBERATE_EXCEPTION))
                    .expectSuccess("foo")
                    .verify(), "expectSuccess");
    }

    @Test
    void noSignalsSubscriptionCancelSucceeds() {
        // expectNoSignals and subscription event are dequeued/processed sequentially on the Subscriber thread
        // and the scenario isn't instructed to expect the subscription so we pass the test.
        StepVerifiers.create(never())
                .expectNoSignals(ofDays(1))
                .thenCancel()
                .verify();
    }

    @Test
    void noSignalsSuccessFail() {
        verifyException(() -> StepVerifiers.create(succeeded("foo"))
                .expectCancellable()
                .expectNoSignals(ofDays(1))
                .expectSuccess("foo")
                .verify(), "expectNoSignals");
    }

    @Test
    void noSignalsErrorFails() {
        verifyException(() -> StepVerifiers.create(failed(DELIBERATE_EXCEPTION))
                .expectCancellable()
                .expectNoSignals(ofDays(1))
                .expectError(DeliberateException.class)
                .verify(), "expectNoSignals");
    }

    @Test
    void noSignalsAfterSubscriptionSucceeds() {
        StepVerifiers.create(Single.never())
                .expectCancellable()
                .expectNoSignals(ofMillis(100))
                .thenCancel()
                .verify();
    }

    @Test
    void thenCancelSucceeded() {
        StepVerifiers.create(succeeded("foo"))
                .thenCancel()
                .verify();
    }

    @Test
    void thenCancelFailed() {
        StepVerifiers.create(Completable.failed(DELIBERATE_EXCEPTION))
                .thenCancel()
                .verify();
    }

    @Test
    void thenRun() {
        SingleSource.Processor<String, String> processor = newSingleProcessor();
        StepVerifiers.create(fromSource(processor))
                .then(() -> processor.onSuccess("foo"))
                .expectSuccess("foo")
                .verify();
    }

    @Test
    void thenRunThrows() {
        assertThrows(DeliberateException.class, () -> StepVerifiers.create(succeeded("foo"))
                .then(() -> {
                    throw DELIBERATE_EXCEPTION;
                })
                .expectSuccess("foo")
                .verify());
    }

    @Test
    void asyncContextOnSuccess() {
        StepVerifiers.create(succeeded("foo").subscribeOn(EXECUTOR_RULE.executor()))
                .expectCancellableConsumed(s -> {
                    assertNotNull(s);
                    AsyncContext.put(ASYNC_KEY, 10);
                })
                .expectSuccessConsumed(next -> {
                    assertEquals("foo", next);
                    assertThat(AsyncContext.get(ASYNC_KEY), is(10));
                })
                .verify();
    }

    @Test
    void asyncContextOnError() {
        StepVerifiers.create(failed(DELIBERATE_EXCEPTION).subscribeOn(EXECUTOR_RULE.executor()))
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

    @Test
    void thenAwaitRespectsDelaysComplete() {
        SingleSource.Processor<String, String> processor = newSingleProcessor();
        new InlineSingleFirstStep<>(processor, new DefaultModifiableTimeSource())
                .expectCancellable()
                .expectNoSignals(ofDays(500))
                .thenAwait(ofDays(1000))
                .then(() -> processor.onSuccess("foo"))
                .expectSuccess("foo")
                .verify();
    }

    @Test
    void thenAwaitRespectsDelaysEqualsFail() {
        thenAwaitRespectsDelaysFail(true);
    }

    @Test
    void thenAwaitRespectsDelaysGTFail() {
        thenAwaitRespectsDelaysFail(false);
    }

    private static void thenAwaitRespectsDelaysFail(boolean equals) {
        SingleSource.Processor<String, String> processor = newSingleProcessor();
        verifyException(() -> new InlineSingleFirstStep<>(processor, new DefaultModifiableTimeSource())
                .expectCancellable()
                .expectNoSignals(ofDays(equals ? 1000 : 1001))
                .thenAwait(ofDays(1000))
                .then(() -> processor.onSuccess("foo"))
                .expectSuccess("foo")
                .verify(), "expectNoSignals");
    }

    private static void verifyException(Supplier<Duration> verifier, String failedTestMethod) {
        PublisherStepVerifierTest.verifyException(verifier, SingleStepVerifierTest.class.getName(), failedTestMethod);
    }
}

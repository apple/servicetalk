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

import io.servicetalk.concurrent.PublisherSource;
import io.servicetalk.concurrent.api.AsyncContext;
import io.servicetalk.concurrent.api.AsyncContextMap;
import io.servicetalk.concurrent.api.Executor;
import io.servicetalk.concurrent.api.ExecutorRule;
import io.servicetalk.concurrent.api.TestPublisher;
import io.servicetalk.concurrent.api.TestSubscription;
import io.servicetalk.concurrent.api.test.InlineStepVerifier.PublisherEvent.StepAssertionError;
import io.servicetalk.concurrent.internal.DeliberateException;

import org.junit.ClassRule;
import org.junit.Test;

import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.function.Consumer;
import java.util.function.Supplier;

import static io.servicetalk.concurrent.api.Processors.newPublisherProcessor;
import static io.servicetalk.concurrent.api.Publisher.empty;
import static io.servicetalk.concurrent.api.Publisher.failed;
import static io.servicetalk.concurrent.api.Publisher.from;
import static io.servicetalk.concurrent.api.Publisher.never;
import static io.servicetalk.concurrent.api.test.Verifiers.stepVerifier;
import static io.servicetalk.concurrent.api.test.Verifiers.stepVerifierForSource;
import static io.servicetalk.concurrent.internal.DeliberateException.DELIBERATE_EXCEPTION;
import static java.time.Duration.ofDays;
import static java.time.Duration.ofMillis;
import static java.time.Duration.ofNanos;
import static java.util.Arrays.asList;
import static java.util.Collections.emptyList;
import static java.util.Collections.singletonList;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.emptyIterable;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.instanceOf;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.startsWith;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

public class PublisherStepVerifierTest {
    private static final AsyncContextMap.Key<Integer> ASYNC_KEY = AsyncContextMap.Key.newKey();
    @ClassRule
    public static final ExecutorRule<Executor> EXECUTOR_RULE = ExecutorRule.newRule();

    @Test
    public void expectSubscription() {
        stepVerifier(from("foo"))
                .expectSubscription()
                .expectNext("foo")
                .expectComplete()
                .verify();
    }

    @Test
    public void expectSubscriptionTimeout() {
        CountDownLatch latch = new CountDownLatch(1);
        try {
            verifyException(() -> stepVerifier(from("foo").publishAndSubscribeOn(EXECUTOR_RULE.executor()))
                    .expectSubscriptionConsumed(subscription -> {
                        try {
                            latch.await();
                        } catch (InterruptedException e) {
                            throw new RuntimeException(e);
                        }
                    })
                    .expectNext("foo")
                    .expectComplete()
                    .verify(ofNanos(10)), "expectSubscriptionConsumed");
        } finally {
            latch.countDown();
        }
    }

    @Test
    public void emptyItemsNonEmptyPublisher() {
        verifyException(() -> stepVerifier(from("foo"))
                .expectNextSequence(emptyList())
                .expectComplete()
                .verify(), "expectComplete");
    }

    @Test
    public void emptyItemsEmptyPublisher() {
        stepVerifier(empty())
                .expectNextSequence(emptyList())
                .expectComplete()
                .verify();
    }

    @Test
    public void singleItem() {
        assertNotNull(stepVerifier(from("foo"))
                .expectNext("foo")
                .expectComplete()
                .verify());
    }

    @Test
    public void singleItemNull() {
        assertNotNull(stepVerifier(from((String) null))
                .expectNext((String) null)
                .expectComplete()
                .verify());
    }

    @Test
    public void singleItemDuplicateVerify() throws InterruptedException {
        CountDownLatch latch = new CountDownLatch(2);
        StepVerifier verifier = stepVerifier(from("foo"))
                .expectNextConsumed(next -> {
                    assertEquals("foo", next);
                    latch.countDown();
                })
                .expectComplete();
        verifier.verify();
        verifier.verify();
        assertTrue(latch.await(10, SECONDS));
    }

    @Test
    public void singleItemLargeTimeout() {
        assertNotNull(stepVerifier(from("foo"))
                .expectNext("foo")
                .expectComplete()
                .verify(ofDays(1)));
    }

    @Test
    public void twoItems() {
        stepVerifier(from("foo", "bar"))
                .expectNext("foo", "bar")
                .expectComplete()
                .verify();
    }

    @Test
    public void twoItemsIterable() {
        stepVerifier(from("foo", "bar"))
                .expectNextSequence(() -> asList("foo", "bar").iterator())
                .expectComplete()
                .verify();
    }

    @Test
    public void justError() {
        stepVerifier(failed(DELIBERATE_EXCEPTION))
                .expectError()
                .verify();
    }

    @Test
    public void justErrorFail() {
        verifyException(() -> stepVerifier(from("foo"))
                .expectError()
                .verify(), "expectError");
    }

    @Test
    public void justErrorClass() {
        stepVerifier(failed(DELIBERATE_EXCEPTION))
                .expectError(DeliberateException.class)
                .verify();
    }

    @Test
    public void justErrorClassFail() {
        verifyException(() -> stepVerifier(from("foo"))
                .expectError(DeliberateException.class)
                .verify(), "expectError");
    }

    @Test
    public void justErrorPredicate() {
        stepVerifier(failed(DELIBERATE_EXCEPTION))
                .expectErrorMatches(cause -> cause instanceof DeliberateException)
                .verify();
    }

    @Test
    public void justErrorPredicateFail() {
        verifyException(() -> stepVerifier(from("foo"))
                .expectErrorMatches(cause -> cause instanceof DeliberateException)
                .verify(), "expectErrorMatches");
    }

    @Test
    public void justErrorConsumer() {
        stepVerifier(failed(DELIBERATE_EXCEPTION))
                .expectErrorConsumed(cause -> assertThat(cause, instanceOf(DeliberateException.class)))
                .verify();
    }

    @Test
    public void justErrorConsumerFail() {
        verifyException(() -> stepVerifier(from("foo"))
                .expectErrorConsumed(cause -> assertThat(cause, instanceOf(DeliberateException.class)))
                .verify(), "expectErrorConsumed");
    }

    @Test
    public void errorIgnoreNextFails() {
        verifyException(() -> stepVerifier(from("foo").concat(failed(DELIBERATE_EXCEPTION)))
                .expectErrorMatches(cause -> cause instanceof DeliberateException)
                .verify(), "expectErrorMatches");
    }

    @Test
    public void completeIgnoreNextConsumer() {
        verifyException(() -> stepVerifier(from("foo"))
                .expectComplete()
                .verify(), "expectComplete");
    }

    @Test
    public void justErrorTimeout() {
        verifyException(() -> stepVerifier(never())
                .expectError(DeliberateException.class)
                .verify(ofNanos(10)), "expectError");
    }

    @Test
    public void onNextThenError() {
        stepVerifier(from("foo").concat(failed(DELIBERATE_EXCEPTION)))
                .expectNext("foo")
                .expectError(DeliberateException.class)
                .verify();
    }

    @Test
    public void incorrectOnNextExpectation() {
        verifyException(() -> stepVerifier(from("foo"))
                    .expectNext("bar")
                    .expectComplete()
                    .verify(), "expectNext");
    }

    @Test
    public void timeoutOnNever() {
        verifyException(() -> stepVerifier(never())
            .expectComplete()
            .verify(ofNanos(10)), "expectComplete");
    }

    @Test
    public void expectNextCount() {
        stepVerifier(from("foo", "bar"))
                .expectNextCount(2)
                .expectComplete()
                .verify();
    }

    @Test(expected = IllegalArgumentException.class)
    public void expectNextCountZeroInvalid() {
        stepVerifier(empty()).expectNextCount(0);
    }

    @Test
    public void expectNextCountExpectAfter() {
        stepVerifier(from("foo", "bar"))
                .expectNextCount(1)
                .expectNext("bar")
                .expectComplete()
                .verify();
    }

    @Test
    public void expectNextCountFail() {
        verifyException(() -> stepVerifier(from("foo"))
                .expectNextCount(2)
                .expectComplete()
                .verify(), "expectNextCount");
    }

    @Test
    public void expectNextCountRange() {
        stepVerifier(from("foo", "bar"))
                .expectNextCount(0, 2)
                .expectComplete()
                .verify();
    }

    @Test
    public void expectNextCountZeroRange() {
        stepVerifier(empty())
                .expectNextCount(0, 2)
                .expectComplete()
                .verify();
    }

    @Test
    public void expectNextCountRangeNotEnoughFail() {
        verifyException(() -> stepVerifier(from("foo", "bar"))
                .expectNextCount(3, 4)
                .expectComplete()
                .verify(), "expectNextCount");
    }

    @Test
    public void expectNextCountRangeItemsAfterFail() {
        verifyException(() -> stepVerifier(from("foo", "bar", "baz"))
                .expectNextCount(0, 2)
                .expectComplete()
                .verify(), "expectComplete");
    }

    @Test
    public void expectNextPredicate() {
        stepVerifier(from("foo"))
                .expectNextMatches("foo"::equals)
                .expectComplete()
                .verify();
    }

    @Test
    public void expectNextPredicateFail() {
        verifyException(() -> stepVerifier(from("foo"))
                .expectNextMatches("bar"::equals)
                .expectComplete()
                .verify(), "expectNextMatches");
    }

    @Test
    public void expectNextConsumer() {
        stepVerifier(from("foo"))
                .expectNextConsumed(next -> assertEquals("foo", next))
                .expectComplete()
                .verify();
    }

    @Test
    public void expectNextConsumerTimeout() {
        verifyException(() -> stepVerifier(from("foo").concat(never()))
                .expectNextConsumed(next -> assertEquals("foo", next))
                .expectComplete()
                .verify(ofNanos(10)), "expectComplete");
    }

    @Test
    public void expectNextArrayMoreThanActual() {
        verifyException(() -> stepVerifier(from("foo"))
                .expectNext("foo", "bar")
                .expectComplete()
                .verify(), "expectNext");
    }

    @Test
    public void expectNextIterableMoreThanActual() {
        verifyException(() -> stepVerifier(from("foo"))
                .expectNextSequence(asList("foo", "bar"))
                .expectComplete()
                .verify(), "expectNextSequence");
    }

    @Test
    public void expectNextArrayLessThanActual() {
        verifyException(() ->
                stepVerifier(from("foo", "bar"))
                .expectNext("foo")
                .expectComplete()
                .verify(), "expectComplete");
    }

    @Test
    public void expectNextIterableLessThanActual() {
        verifyException(() -> stepVerifier(from("foo", "bar"))
                        .expectNextSequence(singletonList("foo"))
                        .expectComplete()
                        .verify(), "expectComplete");
    }

    @Test
    public void expectNextConsumerFail() {
        verifyException(() -> stepVerifier(from("foo"))
                .expectNextConsumed(next -> assertEquals("bar", next))
                .expectComplete()
                .verify(), "expectNextConsumed");
    }

    @Test
    public void expectNextConsumerTypeNonAmbiguousA() {
        Consumer<String> item = n -> { };
        stepVerifier(from(item))
                .expectNext(item)
                .expectComplete()
                .verify();
    }

    @Test
    public void expectNextConsumerTypeNonAmbiguousB() {
        Consumer<String> item = n -> { };
        stepVerifier(from(item))
                .expectNextConsumed(t -> assertSame(item, t))
                .expectComplete()
                .verify();
    }

    @Test
    public void expectNextMultiConsumer() {
        stepVerifier(from("foo", "bar"))
                .expectNext(2, nextIterable -> assertThat(nextIterable, contains("foo", "bar")))
                .expectComplete()
                .verify();
    }

    @Test
    public void expectNextMultiConsumerFail() {
        verifyException(() -> stepVerifier(from("foo", "bar"))
                .expectNext(2, nextIterable -> assertThat(nextIterable, contains("foo")))
                .expectComplete()
                .verify(), "expectNext");
    }

    @Test
    public void expectNextMultiConsumerMinComplete() {
        stepVerifier(from("foo"))
                .expectNext(1, 2, nextIterable -> assertThat(nextIterable, contains("foo")))
                .expectComplete()
                .verify();
    }

    @Test
    public void expectNextMultiConsumerMinFail() {
        stepVerifier(from("foo").concat(failed(DELIBERATE_EXCEPTION)))
                .expectNext(1, 2, nextIterable -> assertThat(nextIterable, contains("foo")))
                .expectError(DeliberateException.class)
                .verify();
    }

    @Test
    public void expectNextMultiConsumerZero() {
        stepVerifier(empty())
                .expectNext(0, 2, nextIterable -> assertThat(nextIterable, emptyIterable()))
                .expectComplete()
                .verify();
    }

    @Test
    public void expectNextMultiConsumerTimeout() {
        verifyException(() -> stepVerifier(from("foo", "bar").concat(never()))
                .expectNext(2, nextIterable -> assertThat(nextIterable, contains("foo", "bar")))
                .expectComplete()
                .verify(ofNanos(10)), "expectComplete");
    }

    @Test
    public void expectOnErrorWhenOnComplete() {
        verifyException(() -> stepVerifier(from("foo"))
                .expectNext("foo")
                .expectError(DeliberateException.class)
                .verify(), "expectError");
    }

    @Test
    public void expectOnCompleteWhenOnError() {
        verifyException(() -> stepVerifier(failed(DELIBERATE_EXCEPTION))
                .expectComplete()
                .verify(), "expectComplete");
    }

    @Test
    public void noSignalsAfterNextThenCancelSucceeds() {
        stepVerifier(from("foo").concat(never()))
                .expectNext("foo")
                .expectNoSignals(ofMillis(100))
                .thenCancel()
                .verify();
    }

    @Test
    public void noSignalsSubscriptionCompleteFail() {
        verifyException(() -> stepVerifier(from("foo"))
                .expectNoSignals(ofDays(1))
                .expectComplete()
                .verify(), "expectNoSignals");
    }

    @Test
    public void noSignalsSubscriptionCancelSucceeds() {
        // expectNoSignals and subscription event are dequeued/processed sequentially on the Subscriber thread
        // and the scenario isn't instructed to expect the subscription so we pass the test.
        TestSubscription subscription = new TestSubscription();
        TestPublisher<String> publisher = new TestPublisher.Builder<String>()
                .disableAutoOnSubscribe().build();
        stepVerifier(publisher)
                .then(() -> publisher.onSubscribe(subscription))
                .expectNoSignals(ofDays(1))
                .thenCancel()
                .verify();
        assertTrue(subscription.isCancelled());
    }

    @Test
    public void noSignalsCompleteFail() {
        verifyException(() -> stepVerifier(from("foo"))
                .expectNext("foo")
                .expectNoSignals(ofDays(1))
                .expectComplete()
                .verify(), "expectNoSignals");
    }

    @Test
    public void noSignalsErrorFails() {
        verifyException(() -> stepVerifier(failed(DELIBERATE_EXCEPTION))
                .expectSubscription()
                .expectNoSignals(ofDays(1))
                .expectError(DeliberateException.class)
                .verify(), "expectNoSignals");
    }

    @Test
    public void noSignalsAfterSubscriptionSucceeds() {
        stepVerifier(never())
                .expectSubscription()
                .expectNoSignals(ofMillis(100))
                .thenCancel()
                .verify();
    }

    @Test
    public void thenCancelCompleted() {
        stepVerifier(from("foo", "bar"))
                .expectNext("foo")
                .thenCancel()
                .verify();
    }

    @Test
    public void thenCancelFailed() {
        stepVerifier(failed(DELIBERATE_EXCEPTION))
                .thenCancel()
                .verify();
    }

    @Test
    public void thenRequest() {
        stepVerifier(from("foo", "bar"))
                .thenRequest(Long.MAX_VALUE)
                .expectNext("foo", "bar")
                .expectComplete()
                .verify();
    }

    @Test
    public void thenRequestInvalidZero() {
        thenRequestInvalid(0);
    }

    @Test
    public void thenRequestInvalidMin() {
        thenRequestInvalid(Long.MIN_VALUE);
    }

    private static void thenRequestInvalid(long invalidN) {
        stepVerifier(from("foo", "bar"))
                .thenRequest(invalidN)
                .expectError(IllegalArgumentException.class)
                .verify();
    }

    @Test
    public void multipleRequests() {
        TestSubscription subscription = new TestSubscription();
        TestPublisher<String> publisher = new TestPublisher.Builder<String>()
                .disableAutoOnSubscribe().build();
        stepVerifier(publisher)
                .then(() -> publisher.onSubscribe(subscription))
                .thenRequest(1)
                .thenRequest(2)
                .then(() -> {
                    assertThat(subscription.requested(), greaterThanOrEqualTo(3L));
                    publisher.onNext("foo", "bar");
                    publisher.onComplete();
                })
                .expectNext("foo", "bar")
                .expectComplete()
                .verify();
        assertThat(subscription.requested(), greaterThanOrEqualTo(5L));
    }

    @Test
    public void requestThenCancel() {
        TestSubscription subscription = new TestSubscription();
        TestPublisher<String> publisher = new TestPublisher.Builder<String>()
                .disableAutoOnSubscribe().build();
        stepVerifier(publisher)
                .then(() -> publisher.onSubscribe(subscription))
                .thenRequest(100)
                .then(() -> {
                    assertThat(subscription.requested(), greaterThanOrEqualTo(100L));
                    publisher.onNext("foo", "bar");
                })
                .expectNext("foo", "bar")
                .thenCancel()
                .verify();
        assertTrue(subscription.isCancelled());
    }

    @Test
    public void thenRun() {
        PublisherSource.Processor<String, String> processor = newPublisherProcessor();
        stepVerifierForSource(processor)
                .then(() -> {
                    processor.onNext("foo");
                    processor.onNext("bar");
                })
                .expectNext("foo", "bar")
                .then(processor::onComplete)
                .expectComplete()
                .verify();
    }

    @Test(expected = DeliberateException.class)
    public void thenRunThrows() {
        stepVerifier(from("foo"))
                .then(() -> {
                    throw DELIBERATE_EXCEPTION;
                })
                .expectComplete()
                .verify();
    }

    @Test
    public void asyncContextOnComplete() {
        stepVerifier(from("foo").publishAndSubscribeOn(EXECUTOR_RULE.executor()))
                .expectSubscriptionConsumed(s -> {
                    assertNotNull(s);
                    AsyncContext.put(ASYNC_KEY, 10);
                })
                .expectNextConsumed(next -> {
                    assertEquals("foo", next);
                    assertThat(AsyncContext.get(ASYNC_KEY), is(10));
                })
                .expectComplete()
                .verify();
    }

    @Test
    public void asyncContextOnError() {
        stepVerifier(from("foo").concat(failed(DELIBERATE_EXCEPTION)).publishAndSubscribeOn(EXECUTOR_RULE.executor()))
                .expectSubscriptionConsumed(s -> {
                    assertNotNull(s);
                    AsyncContext.put(ASYNC_KEY, 10);
                })
                .expectNextConsumed(next -> {
                    assertEquals("foo", next);
                    assertThat(AsyncContext.get(ASYNC_KEY), is(10));
                    AsyncContext.put(ASYNC_KEY, 15);
                })
                .expectErrorConsumed(error -> {
                    assertSame(DELIBERATE_EXCEPTION, error);
                    assertThat(AsyncContext.get(ASYNC_KEY), is(15));
                })
                .verify();
    }

    @Test
    public void thenAwaitExitsWhenVerifyComplete() {
        stepVerifier(from("foo"))
                .thenAwait(ofDays(1))
                .expectNext("foo")
                .expectComplete()
                .verify();
    }

    @Test
    public void thenAwaitRespectsDelaysComplete() {
        PublisherSource.Processor<String, String> processor = newPublisherProcessor();
        new InlinePublisherFirstStep<>(processor, new DefaultModifiableTimeSource())
                .expectSubscription()
                .expectNoSignals(ofDays(500))
                .thenAwait(ofDays(1000))
                .then(() -> processor.onNext("foo"))
                .expectNext("foo")
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
        PublisherSource.Processor<String, String> processor = newPublisherProcessor();
        verifyException(() -> new InlinePublisherFirstStep<>(processor, new DefaultModifiableTimeSource())
                .expectSubscription()
                .expectNoSignals(ofDays(equals ? 1000 : 1001))
                .thenAwait(ofDays(1000))
                .then(() -> processor.onNext("foo"))
                .expectNext("foo")
                .then(processor::onComplete)
                .expectComplete()
                .verify(), "expectNoSignals");
    }

    private static void verifyException(Supplier<Duration> verifier, String failedTestMethod) {
        verifyException(verifier, PublisherStepVerifierTest.class.getName(), failedTestMethod);
    }

    static void verifyException(Supplier<Duration> verifier, String classNamePrefix, String failedTestMethod) {
        StepAssertionError error = assertThrows(StepAssertionError.class, verifier::get);
        StackTraceElement[] stackTraceElements = error.getStackTrace();
        assertThat(stackTraceElements.length, greaterThanOrEqualTo(1));
        assertThat("first stacktrace element expected <class: " + classNamePrefix +
                        "> actual: " + stackTraceElements[0] + " error: " + error,
                stackTraceElements[0].getClassName(), startsWith(classNamePrefix));
        StackTraceElement testMethodStackTrace = error.testMethodStackTrace();
        assertEquals("unexpected test method failure: " + testMethodStackTrace, failedTestMethod,
                testMethodStackTrace.getMethodName());
    }
}

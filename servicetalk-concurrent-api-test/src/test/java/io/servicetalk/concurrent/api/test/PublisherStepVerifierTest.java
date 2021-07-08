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

import io.servicetalk.concurrent.PublisherSource;
import io.servicetalk.concurrent.api.AsyncContext;
import io.servicetalk.concurrent.api.AsyncContextMap;
import io.servicetalk.concurrent.api.Executor;
import io.servicetalk.concurrent.api.ExecutorExtension;
import io.servicetalk.concurrent.api.TestPublisher;
import io.servicetalk.concurrent.api.TestSubscription;
import io.servicetalk.concurrent.api.test.InlineStepVerifier.PublisherEvent.StepAssertionError;
import io.servicetalk.concurrent.internal.DeliberateException;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.function.Consumer;
import java.util.function.Supplier;

import static io.servicetalk.concurrent.api.Processors.newPublisherProcessor;
import static io.servicetalk.concurrent.api.Publisher.empty;
import static io.servicetalk.concurrent.api.Publisher.failed;
import static io.servicetalk.concurrent.api.Publisher.from;
import static io.servicetalk.concurrent.api.Publisher.never;
import static io.servicetalk.concurrent.api.SourceAdapters.fromSource;
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
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PublisherStepVerifierTest {
    private static final AsyncContextMap.Key<Integer> ASYNC_KEY = AsyncContextMap.Key.newKey();
    @RegisterExtension
    static final ExecutorExtension<Executor> EXECUTOR_RULE = ExecutorExtension.withCachedExecutor();

    @Test
    void expectSubscription() {
        StepVerifiers.create(from("foo"))
                .expectSubscription()
                .expectNext("foo")
                .expectComplete()
                .verify();
    }

    @Test
    void expectSubscriptionTimeout() {
        CountDownLatch latch = new CountDownLatch(1);
        try {
            verifyException(() ->
                    StepVerifiers.create(from("foo").subscribeOn(EXECUTOR_RULE.executor()))
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
    void emptyItemsNonEmptyPublisher() {
        verifyException(() -> StepVerifiers.create(from("foo"))
                .expectNextSequence(emptyList())
                .expectComplete()
                .verify(), "expectComplete");
    }

    @Test
    void emptyItemsEmptyPublisher() {
        StepVerifiers.create(empty())
                .expectNextSequence(emptyList())
                .expectComplete()
                .verify();
    }

    @Test
    void singleItem() {
        assertNotNull(StepVerifiers.create(from("foo"))
                .expectNext("foo")
                .expectComplete()
                .verify());
    }

    @Test
    void singleItemNull() {
        assertNotNull(StepVerifiers.create(from((String) null))
                .expectNext((String) null)
                .expectComplete()
                .verify());
    }

    @Test
    void singleItemDuplicateVerify() throws InterruptedException {
        CountDownLatch latch = new CountDownLatch(2);
        StepVerifier verifier = StepVerifiers.create(from("foo"))
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
    void singleItemLargeTimeout() {
        assertNotNull(StepVerifiers.create(from("foo"))
                .expectNext("foo")
                .expectComplete()
                .verify(ofDays(1)));
    }

    @Test
    void twoItems() {
        StepVerifiers.create(from("foo", "bar"))
                .expectNext("foo", "bar")
                .expectComplete()
                .verify();
    }

    @Test
    void twoItemsIterable() {
        StepVerifiers.create(from("foo", "bar"))
                .expectNextSequence(() -> asList("foo", "bar").iterator())
                .expectComplete()
                .verify();
    }

    @Test
    void justError() {
        StepVerifiers.create(failed(DELIBERATE_EXCEPTION))
                .expectError()
                .verify();
    }

    @Test
    void justErrorFail() {
        verifyException(() -> StepVerifiers.create(from("foo"))
                .expectError()
                .verify(), "expectError");
    }

    @Test
    void justErrorClass() {
        StepVerifiers.create(failed(DELIBERATE_EXCEPTION))
                .expectError(DeliberateException.class)
                .verify();
    }

    @Test
    void justErrorClassFail() {
        verifyException(() -> StepVerifiers.create(from("foo"))
                .expectError(DeliberateException.class)
                .verify(), "expectError");
    }

    @Test
    void justErrorPredicate() {
        StepVerifiers.create(failed(DELIBERATE_EXCEPTION))
                .expectErrorMatches(cause -> cause instanceof DeliberateException)
                .verify();
    }

    @Test
    void justErrorPredicateFail() {
        verifyException(() -> StepVerifiers.create(from("foo"))
                .expectErrorMatches(cause -> cause instanceof DeliberateException)
                .verify(), "expectErrorMatches");
    }

    @Test
    void justErrorConsumer() {
        StepVerifiers.create(failed(DELIBERATE_EXCEPTION))
                .expectErrorConsumed(cause -> assertThat(cause, instanceOf(DeliberateException.class)))
                .verify();
    }

    @Test
    void justErrorConsumerFail() {
        verifyException(() -> StepVerifiers.create(from("foo"))
                .expectErrorConsumed(cause -> assertThat(cause, instanceOf(DeliberateException.class)))
                .verify(), "expectErrorConsumed");
    }

    @Test
    void errorIgnoreNextFails() {
        verifyException(() -> StepVerifiers.create(from("foo").concat(failed(DELIBERATE_EXCEPTION)))
                .expectErrorMatches(cause -> cause instanceof DeliberateException)
                .verify(), "expectErrorMatches");
    }

    @Test
    void completeIgnoreNextConsumer() {
        verifyException(() -> StepVerifiers.create(from("foo"))
                .expectComplete()
                .verify(), "expectComplete");
    }

    @Test
    void justErrorTimeout() {
        verifyException(() -> StepVerifiers.create(never())
                .expectError(DeliberateException.class)
                .verify(ofNanos(10)), "expectError");
    }

    @Test
    void onNextThenError() {
        StepVerifiers.create(from("foo").concat(failed(DELIBERATE_EXCEPTION)))
                .expectNext("foo")
                .expectError(DeliberateException.class)
                .verify();
    }

    @Test
    void incorrectOnNextExpectation() {
        verifyException(() -> StepVerifiers.create(from("foo"))
                    .expectNext("bar")
                    .expectComplete()
                    .verify(), "expectNext");
    }

    @Test
    void timeoutOnNever() {
        verifyException(() -> StepVerifiers.create(never())
            .expectComplete()
            .verify(ofNanos(10)), "expectComplete");
    }

    @Test
    void expectNextCount() {
        StepVerifiers.create(from("foo", "bar"))
                .expectNextCount(2)
                .expectComplete()
                .verify();
    }

    @Test
    void expectNextCountZeroInvalid() {
        assertThrows(IllegalArgumentException.class, () -> StepVerifiers.create(empty()).expectNextCount(0));
    }

    @Test
    void expectNextCountExpectAfter() {
        StepVerifiers.create(from("foo", "bar"))
                .expectNextCount(1)
                .expectNext("bar")
                .expectComplete()
                .verify();
    }

    @Test
    void expectNextCountFail() {
        verifyException(() -> StepVerifiers.create(from("foo"))
                .expectNextCount(2)
                .expectComplete()
                .verify(), "expectNextCount");
    }

    @Test
    void expectNextCountRange() {
        StepVerifiers.create(from("foo", "bar"))
                .expectNextCount(0, 2)
                .expectComplete()
                .verify();
    }

    @Test
    void expectNextCountZeroRange() {
        StepVerifiers.create(empty())
                .expectNextCount(0, 2)
                .expectComplete()
                .verify();
    }

    @Test
    void expectNextCountRangeNotEnoughFail() {
        verifyException(() -> StepVerifiers.create(from("foo", "bar"))
                .expectNextCount(3, 4)
                .expectComplete()
                .verify(), "expectNextCount");
    }

    @Test
    void expectNextCountRangeItemsAfterFail() {
        verifyException(() -> StepVerifiers.create(from("foo", "bar", "baz"))
                .expectNextCount(0, 2)
                .expectComplete()
                .verify(), "expectComplete");
    }

    @Test
    void expectNextPredicate() {
        StepVerifiers.create(from("foo"))
                .expectNextMatches("foo"::equals)
                .expectComplete()
                .verify();
    }

    @Test
    void expectNextPredicateFail() {
        verifyException(() -> StepVerifiers.create(from("foo"))
                .expectNextMatches("bar"::equals)
                .expectComplete()
                .verify(), "expectNextMatches");
    }

    @Test
    void expectNextConsumer() {
        StepVerifiers.create(from("foo"))
                .expectNextConsumed(next -> assertEquals("foo", next))
                .expectComplete()
                .verify();
    }

    @Test
    void expectNextConsumerTimeout() {
        verifyException(() -> StepVerifiers.create(from("foo").concat(never()))
                .expectNextConsumed(next -> assertEquals("foo", next))
                .expectComplete()
                .verify(ofNanos(10)), "expectComplete");
    }

    @Test
    void expectNextArrayMoreThanActual() {
        verifyException(() -> StepVerifiers.create(from("foo"))
                .expectNext("foo", "bar")
                .expectComplete()
                .verify(), "expectNext");
    }

    @Test
    void expectNextIterableMoreThanActual() {
        verifyException(() -> StepVerifiers.create(from("foo"))
                .expectNextSequence(asList("foo", "bar"))
                .expectComplete()
                .verify(), "expectNextSequence");
    }

    @Test
    void expectNextArrayLessThanActual() {
        verifyException(() ->
                StepVerifiers.create(from("foo", "bar"))
                .expectNext("foo")
                .expectComplete()
                .verify(), "expectComplete");
    }

    @Test
    void expectNextIterableLessThanActual() {
        verifyException(() -> StepVerifiers.create(from("foo", "bar"))
                        .expectNextSequence(singletonList("foo"))
                        .expectComplete()
                        .verify(), "expectComplete");
    }

    @Test
    void expectNextConsumerFail() {
        verifyException(() -> StepVerifiers.create(from("foo"))
                .expectNextConsumed(next -> assertEquals("bar", next))
                .expectComplete()
                .verify(), "expectNextConsumed");
    }

    @Test
    void expectNextConsumerTypeNonAmbiguousA() {
        Consumer<String> item = n -> { };
        StepVerifiers.create(from(item))
                .expectNext(item)
                .expectComplete()
                .verify();
    }

    @Test
    void expectNextConsumerTypeNonAmbiguousB() {
        Consumer<String> item = n -> { };
        StepVerifiers.create(from(item))
                .expectNextConsumed(t -> assertSame(item, t))
                .expectComplete()
                .verify();
    }

    @Test
    void expectNextMultiConsumer() {
        StepVerifiers.create(from("foo", "bar"))
                .expectNext(2, nextIterable -> assertThat(nextIterable, contains("foo", "bar")))
                .expectComplete()
                .verify();
    }

    @Test
    void expectNextMultiConsumerFail() {
        verifyException(() -> StepVerifiers.create(from("foo", "bar"))
                .expectNext(2, nextIterable -> assertThat(nextIterable, contains("foo")))
                .expectComplete()
                .verify(), "expectNext");
    }

    @Test
    void expectNextMultiConsumerMinComplete() {
        StepVerifiers.create(from("foo"))
                .expectNext(1, 2, nextIterable -> assertThat(nextIterable, contains("foo")))
                .expectComplete()
                .verify();
    }

    @Test
    void expectNextMultiConsumerMinFail() {
        StepVerifiers.create(from("foo").concat(failed(DELIBERATE_EXCEPTION)))
                .expectNext(1, 2, nextIterable -> assertThat(nextIterable, contains("foo")))
                .expectError(DeliberateException.class)
                .verify();
    }

    @Test
    void expectNextMultiConsumerZero() {
        StepVerifiers.create(empty())
                .expectNext(0, 2, nextIterable -> assertThat(nextIterable, emptyIterable()))
                .expectComplete()
                .verify();
    }

    @Test
    void expectNextMultiConsumerTimeout() {
        verifyException(() -> StepVerifiers.create(from("foo", "bar").concat(never()))
                .expectNext(2, nextIterable -> assertThat(nextIterable, contains("foo", "bar")))
                .expectComplete()
                .verify(ofNanos(10)), "expectComplete");
    }

    @Test
    void expectOnErrorWhenOnComplete() {
        verifyException(() -> StepVerifiers.create(from("foo"))
                .expectNext("foo")
                .expectError(DeliberateException.class)
                .verify(), "expectError");
    }

    @Test
    void expectOnCompleteWhenOnError() {
        verifyException(() -> StepVerifiers.create(failed(DELIBERATE_EXCEPTION))
                .expectComplete()
                .verify(), "expectComplete");
    }

    @Test
    void noSignalsAfterNextThenCancelSucceeds() {
        StepVerifiers.create(from("foo").concat(never()))
                .expectNext("foo")
                .expectNoSignals(ofMillis(100))
                .thenCancel()
                .verify();
    }

    @Test
    void noSignalsSubscriptionCompleteFail() {
        verifyException(() -> StepVerifiers.create(from("foo"))
                .expectNoSignals(ofDays(1))
                .expectComplete()
                .verify(), "expectNoSignals");
    }

    @Test
    void noSignalsSubscriptionCancelSucceeds() {
        // expectNoSignals and subscription event are dequeued/processed sequentially on the Subscriber thread
        // and the scenario isn't instructed to expect the subscription so we pass the test.
        TestSubscription subscription = new TestSubscription();
        TestPublisher<String> publisher = new TestPublisher.Builder<String>()
                .disableAutoOnSubscribe().build();
        StepVerifiers.create(publisher)
                .then(() -> publisher.onSubscribe(subscription))
                .expectNoSignals(ofDays(1))
                .thenCancel()
                .verify();
        assertTrue(subscription.isCancelled());
    }

    @Test
    void noSignalsCompleteFail() {
        verifyException(() -> StepVerifiers.create(from("foo"))
                .expectNext("foo")
                .expectNoSignals(ofDays(1))
                .expectComplete()
                .verify(), "expectNoSignals");
    }

    @Test
    void noSignalsErrorFails() {
        verifyException(() -> StepVerifiers.create(failed(DELIBERATE_EXCEPTION))
                .expectSubscription()
                .expectNoSignals(ofDays(1))
                .expectError(DeliberateException.class)
                .verify(), "expectNoSignals");
    }

    @Test
    void noSignalsAfterSubscriptionSucceeds() {
        StepVerifiers.create(never())
                .expectSubscription()
                .expectNoSignals(ofMillis(100))
                .thenCancel()
                .verify();
    }

    @Test
    void thenCancelCompleted() {
        StepVerifiers.create(from("foo", "bar"))
                .expectNext("foo")
                .thenCancel()
                .verify();
    }

    @Test
    void thenCancelFailed() {
        StepVerifiers.create(failed(DELIBERATE_EXCEPTION))
                .thenCancel()
                .verify();
    }

    @Test
    void thenRequest() {
        StepVerifiers.create(from("foo", "bar"))
                .thenRequest(Long.MAX_VALUE)
                .expectNext("foo", "bar")
                .expectComplete()
                .verify();
    }

    @Test
    void thenRequestInvalidZero() {
        thenRequestInvalid(0);
    }

    @Test
    void thenRequestInvalidMin() {
        thenRequestInvalid(Long.MIN_VALUE);
    }

    private static void thenRequestInvalid(long invalidN) {
        StepVerifiers.create(from("foo", "bar"))
                .thenRequest(invalidN)
                .expectError(IllegalArgumentException.class)
                .verify();
    }

    @Test
    void multipleRequests() {
        TestSubscription subscription = new TestSubscription();
        TestPublisher<String> publisher = new TestPublisher.Builder<String>()
                .disableAutoOnSubscribe().build();
        StepVerifiers.create(publisher)
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
    void requestThenCancel() {
        TestSubscription subscription = new TestSubscription();
        TestPublisher<String> publisher = new TestPublisher.Builder<String>()
                .disableAutoOnSubscribe().build();
        StepVerifiers.create(publisher)
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
    void thenRun() {
        PublisherSource.Processor<String, String> processor = newPublisherProcessor();
        StepVerifiers.create(fromSource(processor))
                .then(() -> {
                    processor.onNext("foo");
                    processor.onNext("bar");
                })
                .expectNext("foo", "bar")
                .then(processor::onComplete)
                .expectComplete()
                .verify();
    }

    void thenRunThrows() {
        assertThrows(DeliberateException.class, () -> StepVerifiers.create(from("foo"))
                .then(() -> {
                    throw DELIBERATE_EXCEPTION;
                })
                .expectComplete()
                .verify());
    }

    @Test
    void asyncContextOnComplete() {
        StepVerifiers.create(from("foo").subscribeOn(EXECUTOR_RULE.executor()))
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
    void asyncContextOnError() {
        StepVerifiers.create(from("foo").concat(failed(DELIBERATE_EXCEPTION))
                        .subscribeOn(EXECUTOR_RULE.executor()))
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
    void thenAwaitExitsWhenVerifyComplete() {
        StepVerifiers.create(from("foo"))
                .thenAwait(ofDays(1))
                .expectNext("foo")
                .expectComplete()
                .verify();
    }

    @Test
    void thenAwaitRespectsDelaysComplete() {
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
    void thenAwaitRespectsDelaysEqualsFail() {
        thenAwaitRespectsDelaysFail(true);
    }

    @Test
    void thenAwaitRespectsDelaysGTFail() {
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
        assertThat("first stacktrace element expected <class: " + classNamePrefix + "> actual: " +
                        stackTraceElements[0] + " error: " + error,
                stackTraceElements[0].getClassName(), startsWith(classNamePrefix));
        StackTraceElement testMethodStackTrace = error.testMethodStackTrace();
        assertEquals(failedTestMethod,
                testMethodStackTrace.getMethodName(), "unexpected test method failure: " + testMethodStackTrace);
    }
}

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
import io.servicetalk.concurrent.api.Executors;
import io.servicetalk.concurrent.api.TestPublisher;
import io.servicetalk.concurrent.api.TestSubscription;
import io.servicetalk.concurrent.internal.DeliberateException;

import org.hamcrest.Matchers;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;

import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.function.Supplier;
import javax.annotation.Nullable;

import static io.servicetalk.concurrent.api.Processors.newPublisherProcessor;
import static io.servicetalk.concurrent.api.Publisher.empty;
import static io.servicetalk.concurrent.api.Publisher.failed;
import static io.servicetalk.concurrent.api.Publisher.from;
import static io.servicetalk.concurrent.api.Publisher.never;
import static io.servicetalk.concurrent.api.SourceAdapters.fromSource;
import static io.servicetalk.concurrent.api.test.StepVerifiers.create;
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
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.instanceOf;
import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

public class PublisherStepVerifierTest {
    private static final AsyncContextMap.Key<Integer> ASYNC_KEY = AsyncContextMap.Key.newKey();
    @Nullable
    private static Executor executor;

    @BeforeClass
    public static void beforeClass() {
        executor = Executors.newCachedThreadExecutor();
    }

    @AfterClass
    public static void afterClass() throws ExecutionException, InterruptedException {
        if (executor != null) {
            executor.closeAsync().toFuture().get();
        }
    }

    @Test
    public void expectSubscription() {
        create(from("foo"))
                .expectSubscription(Assert::assertNotNull)
                .expectNext("foo")
                .expectComplete()
                .verify();
    }

    @Test(expected = AssertionError.class)
    public void expectSubscriptionTimeout() {
        assert executor != null;
        CountDownLatch latch = new CountDownLatch(1);
        try {
            verifyException(() -> create(from("foo").publishAndSubscribeOn(executor))
                    .expectSubscription(subscription -> {
                        try {
                            latch.await();
                        } catch (InterruptedException e) {
                            throw new RuntimeException(e);
                        }
                    })
                    .expectNext("foo")
                    .expectComplete()
                    .verify(ofNanos(10)));
        } finally {
            latch.countDown();
        }
    }

    @Test(expected = AssertionError.class)
    public void emptyItemsNonEmptyPublisher() {
        verifyException(() -> create(from("foo"))
                .expectNext(emptyList())
                .expectComplete()
                .verify());
    }

    @Test
    public void emptyItemsEmptyPublisher() {
        create(empty())
                .expectNext(emptyList())
                .expectComplete()
                .verify();
    }

    @Test
    public void singleItem() {
        assertNotNull(create(from("foo"))
                .expectNext("foo")
                .expectComplete()
                .verify());
    }

    @Test
    public void singleItemNull() {
        assertNotNull(create(from((String) null))
                .expectNext((String) null)
                .expectComplete()
                .verify());
    }

    @Test
    public void singleItemDuplicateVerify() throws InterruptedException {
        CountDownLatch latch = new CountDownLatch(2);
        StepVerifier verifier = create(from("foo"))
                .expectNext(next -> {
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
        assertNotNull(create(from("foo"))
                .expectNext("foo")
                .expectComplete()
                .verify(ofDays(1)));
    }

    @Test
    public void twoItems() {
        create(from("foo", "bar"))
                .expectNext("foo", "bar")
                .expectComplete()
                .verify();
    }

    @Test
    public void twoItemsIterable() {
        create(from("foo", "bar"))
                .expectNext(() -> asList("foo", "bar").iterator())
                .expectComplete()
                .verify();
    }

    @Test
    public void justError() {
        create(failed(DELIBERATE_EXCEPTION))
                .expectError(DeliberateException.class)
                .verify();
    }

    @Test
    public void justErrorPredicate() {
        create(failed(DELIBERATE_EXCEPTION))
                .expectError(cause -> cause instanceof DeliberateException)
                .verify();
    }

    @Test
    public void justErrorConsumer() {
        create(failed(DELIBERATE_EXCEPTION))
                .expectError(cause -> {
                    assertThat(cause, instanceOf(DeliberateException.class));
                })
                .verify();
    }

    @Test(expected = AssertionError.class)
    public void errorIgnoreNextFails() {
        verifyException(() -> create(from("foo").concat(failed(DELIBERATE_EXCEPTION)))
                .expectError(cause -> cause instanceof DeliberateException)
                .verify());
    }

    @Test(expected = AssertionError.class)
    public void completeIgnoreNextConsumer() {
        verifyException(() -> create(from("foo"))
                .expectComplete()
                .verify());
    }

    @Test(expected = AssertionError.class)
    public void justErrorTimeout() {
        verifyException(() -> create(never())
                .expectError(DeliberateException.class)
                .verify(ofNanos(10)));
    }

    @Test
    public void onNextThenError() {
        create(from("foo").concat(failed(DELIBERATE_EXCEPTION)))
                .expectNext("foo")
                .expectError(DeliberateException.class)
                .verify();
    }

    @Test(expected = AssertionError.class)
    public void incorrectOnNextExpectation() {
        verifyException(() -> create(from("foo"))
                    .expectNext("bar")
                    .expectComplete()
                    .verify());
    }

    @Test(expected = AssertionError.class)
    public void timeoutOnNever() {
        verifyException(() -> create(never())
            .expectComplete()
            .verify(ofNanos(10)));
    }

    @Test
    public void expectNextCount() {
        create(from("foo", "bar"))
                .expectNextCount(2)
                .expectComplete()
                .verify();
    }

    @Test
    public void expectNextConsumer() {
        create(from("foo"))
                .expectNext(next -> assertEquals("foo", next))
                .expectComplete()
                .verify();
    }

    @Test(expected = AssertionError.class)
    public void expectNextConsumerTimeout() {
        verifyException(() -> create(from("foo").concat(never()))
                .expectNext(next -> assertEquals("foo", next))
                .expectComplete()
                .verify(ofNanos(10)));
    }

    @Test(expected = AssertionError.class)
    public void expectNextArrayMoreThanActual() {
        verifyException(() -> create(from("foo"))
                .expectNext("foo", "bar")
                .expectComplete()
                .verify());
    }

    @Test(expected = AssertionError.class)
    public void expectNextCollectionMoreThanActual() {
        verifyException(() -> create(from("foo"))
                .expectNext(asList("foo", "bar"))
                .expectComplete()
                .verify());
    }

    @Test(expected = AssertionError.class)
    public void expectNextIterableMoreThanActual() {
        verifyException(() -> create(from("foo"))
                .expectNext((Iterable<String>) asList("foo", "bar"))
                .expectComplete()
                .verify());
    }

    @Test(expected = AssertionError.class)
    public void expectNextArrayLessThanActual() {
        verifyException(() ->
                create(from("foo", "bar"))
                .expectNext("foo")
                .expectComplete()
                .verify());
    }

    @Test(expected = AssertionError.class)
    public void expectNextCollectionLessThanActual() {
        verifyException(() ->
                create(from("foo", "bar"))
                        .expectNext(singletonList("foo"))
                        .expectComplete()
                        .verify());
    }

    @Test(expected = AssertionError.class)
    public void expectNextIterableLessThanActual() {
        verifyException(() ->
                create(from("foo", "bar"))
                        .expectNext((Iterable<String>) singletonList("foo"))
                        .expectComplete()
                        .verify());
    }

    @Test(expected = AssertionError.class)
    public void expectNextConsumerFail() {
        verifyException(() -> create(from("foo"))
                .expectNext(next -> assertEquals("bar", next))
                .expectComplete()
                .verify());
    }

    @Test
    public void expectNextMultiConsumer() {
        create(from("foo", "bar"))
                .expectNext(2, nextIterable -> assertThat(nextIterable, contains("foo", "bar")))
                .expectComplete()
                .verify();
    }

    @Test(expected = AssertionError.class)
    public void expectNextMultiConsumerFail() {
        verifyException(() -> create(from("foo", "bar"))
                .expectNext(2, nextIterable -> assertThat(nextIterable, contains("foo")))
                .expectComplete()
                .verify());
    }

    @Test
    public void expectNextMultiConsumerMinComplete() {
        create(from("foo"))
                .expectNext(1, 2, nextIterable -> assertThat(nextIterable, contains("foo")))
                .expectComplete()
                .verify();
    }

    @Test
    public void expectNextMultiConsumerMinFail() {
        create(from("foo").concat(failed(DELIBERATE_EXCEPTION)))
                .expectNext(1, 2, nextIterable -> assertThat(nextIterable, contains("foo")))
                .expectError(DeliberateException.class)
                .verify();
    }

    @Test
    public void expectNextMultiConsumerZero() {
        create(empty())
                .expectNext(0, 2, nextIterable -> assertThat(nextIterable, Matchers.empty()))
                .expectComplete()
                .verify();
    }

    @Test(expected = AssertionError.class)
    public void expectNextMultiConsumerTimeout() {
        verifyException(() -> create(from("foo", "bar").concat(never()))
                .expectNext(2, nextIterable -> assertThat(nextIterable, contains("foo", "bar")))
                .expectComplete()
                .verify(ofNanos(10)));
    }

    @Test(expected = AssertionError.class)
    public void expectOnErrorWhenOnComplete() {
        verifyException(() -> create(from("foo"))
                .expectNext("foo")
                .expectError(DeliberateException.class)
                .verify());
    }

    @Test(expected = AssertionError.class)
    public void expectOnCompleteWhenOnError() {
        verifyException(() -> create(failed(DELIBERATE_EXCEPTION))
                .expectComplete()
                .verify());
    }

    @Test
    public void noSignalsAfterNextThenCancelSucceeds() {
        create(from("foo").concat(never()))
                .expectNext("foo")
                .expectNoSignals(ofMillis(100))
                .thenCancel()
                .verify();
    }

    @Test(expected = AssertionError.class)
    public void noSignalsSubscriptionCompleteFail() {
        verifyException(() -> create(from("foo"))
                .expectNoSignals(ofDays(1))
                .expectComplete()
                .verify());
    }

    @Test
    public void noSignalsSubscriptionCancelSucceeds() {
        // expectNoSignals and subscription event are dequeued/processed sequentially on the Subscriber thread
        // and the scenario isn't instructed to expect the subscription so we pass the test.
        TestSubscription subscription = new TestSubscription();
        TestPublisher<String> publisher = new TestPublisher.Builder<String>()
                .disableAutoOnSubscribe().build();
        create(publisher)
                .then(() -> publisher.onSubscribe(subscription))
                .expectNoSignals(ofDays(1))
                .thenCancel()
                .verify();
        assertTrue(subscription.isCancelled());
    }

    @Test(expected = AssertionError.class)
    public void noSignalsCompleteFail() {
        verifyException(() -> create(from("foo"))
                .expectNext("foo")
                .expectNoSignals(ofDays(1))
                .expectComplete()
                .verify());
    }

    @Test(expected = AssertionError.class)
    public void noSignalsErrorFails() {
        verifyException(() -> create(failed(DELIBERATE_EXCEPTION))
                .expectSubscription(s -> { })
                .expectNoSignals(ofDays(1))
                .expectError(DeliberateException.class)
                .verify());
    }

    @Test
    public void noSignalsAfterSubscriptionSucceeds() {
        create(never())
                .expectSubscription(s -> { })
                .expectNoSignals(ofMillis(100))
                .thenCancel()
                .verify();
    }

    @Test
    public void thenCancel() {
        create(from("foo", "bar"))
                .expectNext("foo")
                .thenCancel()
                .verify();
    }

    @Test
    public void thenRequest() {
        create(from("foo", "bar"))
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
        create(from("foo", "bar"))
                .thenRequest(invalidN)
                .expectError(IllegalArgumentException.class)
                .verify();
    }

    @Test
    public void multipleRequests() {
        TestSubscription subscription = new TestSubscription();
        TestPublisher<String> publisher = new TestPublisher.Builder<String>()
                .disableAutoOnSubscribe().build();
        create(publisher)
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
        create(publisher)
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
        create(fromSource(processor))
                .then(() -> {
                    processor.onNext("foo");
                    processor.onNext("bar");
                })
                .expectNext("foo", "bar")
                .then(processor::onComplete)
                .expectComplete()
                .verify();
    }

    @Test(expected = IllegalStateException.class)
    public void thenRunThrows() {
        create(from("foo"))
                .then(() -> {
                    throw new IllegalStateException();
                })
                .expectComplete()
                .verify();
    }

    @Test
    public void asyncContextOnComplete() {
        assert executor != null;
        create(from("foo").publishAndSubscribeOn(executor))
                .expectSubscription(s -> {
                    assertNotNull(s);
                    AsyncContext.put(ASYNC_KEY, 10);
                })
                .expectNext(next -> {
                    assertEquals("foo", next);
                    assertThat(AsyncContext.get(ASYNC_KEY), is(10));
                })
                .expectComplete()
                .verify();
    }

    @Test
    public void asyncContextOnError() {
        assert executor != null;
        create(from("foo").concat(failed(DELIBERATE_EXCEPTION)).publishAndSubscribeOn(executor))
                .expectSubscription(s -> {
                    assertNotNull(s);
                    AsyncContext.put(ASYNC_KEY, 10);
                })
                .expectNext(next -> {
                    assertEquals("foo", next);
                    assertThat(AsyncContext.get(ASYNC_KEY), is(10));
                    AsyncContext.put(ASYNC_KEY, 15);
                })
                .expectError(error -> {
                    assertSame(DELIBERATE_EXCEPTION, error);
                    assertThat(AsyncContext.get(ASYNC_KEY), is(15));
                })
                .verify();
    }

    @Test
    public void thenAwaitExitsWhenVerifyComplete() {
        create(from("foo"))
                .thenAwait(ofDays(1))
                .expectNext("foo")
                .expectComplete()
                .verify();
    }

    @Test
    public void thenAwaitRespectsDelaysComplete() {
        PublisherSource.Processor<String, String> processor = newPublisherProcessor();
        new InlinePublisherFirstStep<>(processor, new DefaultModifiableTimeSource())
                .expectSubscription(s -> { })
                .expectNoSignals(ofDays(500))
                .thenAwait(ofDays(1000))
                .then(() -> processor.onNext("foo"))
                .expectNext("foo")
                .then(processor::onComplete)
                .expectComplete()
                .verify();
    }

    @Test(expected = AssertionError.class)
    public void thenAwaitRespectsDelaysEqualsFail() {
        thenAwaitRespectsDelaysFail(true);
    }

    @Test(expected = AssertionError.class)
    public void thenAwaitRespectsDelaysGTFail() {
        thenAwaitRespectsDelaysFail(false);
    }

    private static void thenAwaitRespectsDelaysFail(boolean equals) {
        PublisherSource.Processor<String, String> processor = newPublisherProcessor();
        verifyException(() -> new InlinePublisherFirstStep<>(processor, new DefaultModifiableTimeSource())
                .expectSubscription(s -> { })
                .expectNoSignals(ofDays(equals ? 1000 : 1001))
                .thenAwait(ofDays(1000))
                .then(() -> processor.onNext("foo"))
                .expectNext("foo")
                .then(processor::onComplete)
                .expectComplete()
                .verify());
    }

    private static void verifyException(Supplier<Duration> verifier) {
        verifyException(verifier, PublisherStepVerifierTest.class.getName());
    }

    static void verifyException(Supplier<Duration> verifier, String classNamePrefix) {
        try {
            verifier.get();
        } catch (AssertionError error) {
            StackTraceElement[] stackTraceElements = error.getStackTrace();
            if (stackTraceElements.length == 0 || !stackTraceElements[0].getClassName().startsWith(classNamePrefix)) {
                // the tests expect AssertionError, we need to throw a different type of exception!
                throw new IllegalStateException("stacktrace does not start with classNamePrefix: " + classNamePrefix,
                        error);
            }
            throw error;
        }
        throw new IllegalStateException("expected test to fail, but it didn't");
    }
}

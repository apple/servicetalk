/*
 * Copyright © 2018-2019 Apple Inc. and the ServiceTalk project authors
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

import io.servicetalk.concurrent.BlockingIterable;
import io.servicetalk.concurrent.PublisherSource.Processor;
import io.servicetalk.concurrent.PublisherSource.Subscriber;
import io.servicetalk.concurrent.PublisherSource.Subscription;
import io.servicetalk.concurrent.internal.DeliberateException;
import io.servicetalk.concurrent.internal.ServiceTalkTestTimeout;
import io.servicetalk.concurrent.test.internal.TestPublisherSubscriber;

import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.junit.rules.Timeout;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;

import static io.servicetalk.concurrent.api.Processors.newPublisherProcessor;
import static io.servicetalk.concurrent.api.Publisher.failed;
import static io.servicetalk.concurrent.api.Publisher.from;
import static io.servicetalk.concurrent.api.SourceAdapters.fromSource;
import static io.servicetalk.concurrent.api.SourceAdapters.toSource;
import static io.servicetalk.concurrent.internal.DeliberateException.DELIBERATE_EXCEPTION;
import static io.servicetalk.utils.internal.PlatformDependent.throwException;
import static java.util.Arrays.asList;
import static java.util.Collections.singletonList;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static java.util.function.Function.identity;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;
import static org.hamcrest.Matchers.sameInstance;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;

public class PublisherConcatMapIterableTest {
    @ClassRule
    public static final ExecutorRule<Executor> EXECUTOR_RULE = ExecutorRule.newRule();
    @Rule
    public final ExpectedException expectedException = ExpectedException.none();
    @Rule
    public final Timeout timeout = new ServiceTalkTestTimeout();

    private final TestPublisher<List<String>> publisher = new TestPublisher<>();
    private final TestPublisher<BlockingIterable<String>> cancellablePublisher = new TestPublisher<>();
    private final TestPublisherSubscriber<String> subscriber = new TestPublisherSubscriber<>();
    private final TestSubscription subscription = new TestSubscription();

    @Test
    public void upstreamRecoverWithMakesProgress() throws Exception {
        @SuppressWarnings("unchecked")
        Subscriber<String> mockSubscriber = mock(Subscriber.class);
        CountDownLatch latchOnSubscribe = new CountDownLatch(1);
        CountDownLatch latchOnError = new CountDownLatch(1);
        AtomicReference<Throwable> causeRef = new AtomicReference<>();
        AtomicInteger nextCount = new AtomicInteger();
        List<String> results = new ArrayList<>();
        doAnswer(a -> {
            Subscription s = a.getArgument(0);
            s.request(Long.MAX_VALUE);
            latchOnSubscribe.countDown();
            return null;
        }).when(mockSubscriber).onSubscribe(any(Subscription.class));
        doAnswer(a -> {
            causeRef.set(a.getArgument(0));
            latchOnError.countDown();
            return null;
        }).when(mockSubscriber).onError(eq(DELIBERATE_EXCEPTION));
        doAnswer(a -> {
            results.add(a.getArgument(0));
            if (nextCount.getAndIncrement() == 0) {
                throw new DeliberateException();
            }
            throw DELIBERATE_EXCEPTION; // final exception
        }).when(mockSubscriber).onNext(any());

        Processor<List<String>, List<String>> processor = newPublisherProcessor();
        toSource(fromSource(processor).recoverWith(cause -> {
            if (cause != DELIBERATE_EXCEPTION) { // recover!
                return from(singletonList("two"));
            }
            return failed(cause);
        }).flatMapConcatIterable(identity())).subscribe(mockSubscriber);

        latchOnSubscribe.await();
        processor.onNext(asList("one", "ignored!"));
        latchOnError.await();
        assertThat(results, contains("one", "two"));
        assertThat(causeRef.get(), is(DELIBERATE_EXCEPTION));
    }

    @Test
    public void cancellableIterableIsCancelled() {
        toSource(cancellablePublisher.flatMapConcatIterable(identity())).subscribe(subscriber);
        cancellablePublisher.onSubscribe(subscription);
        subscriber.awaitSubscription().request(1);
        AtomicBoolean cancelled = new AtomicBoolean();
        cancellablePublisher.onNext(new TestIterableToBlockingIterable<>(asList("one", "two"),
                (time, unit) -> { }, (time, unit) -> { }, () -> cancelled.set(true)));
        assertThat(subscriber.takeOnNext(), is("one"));
        assertThat(subscriber.pollOnNext(10, MILLISECONDS), is(nullValue()));
        assertThat(subscriber.pollTerminal(10, MILLISECONDS), is(nullValue()));
        subscriber.awaitSubscription().cancel();
        assertTrue(cancelled.get());
    }

    @Test
    public void justComplete() {
        toSource(publisher.flatMapConcatIterable(identity())).subscribe(subscriber);
        publisher.onSubscribe(subscription);
        subscriber.awaitSubscription();
        verifyTermination(true);
    }

    @Test
    public void justFail() {
        toSource(publisher.flatMapConcatIterable(identity())).subscribe(subscriber);
        publisher.onSubscribe(subscription);
        subscriber.awaitSubscription();
        verifyTermination(false);
    }

    @Test
    public void singleElementSingleValueThenSuccess() {
        singleElementSingleValue(true);
    }

    @Test
    public void singleElementSingleValueThenFail() {
        singleElementSingleValue(false);
    }

    private void singleElementSingleValue(boolean success) {
        toSource(publisher.flatMapConcatIterable(identity())).subscribe(subscriber);
        publisher.onSubscribe(subscription);
        subscriber.awaitSubscription().request(1);
        publisher.onNext(singletonList("one"));
        assertThat(subscriber.takeOnNext(), is("one"));
        assertThat(subscriber.pollOnNext(10, MILLISECONDS), is(nullValue()));
        assertThat(subscriber.pollTerminal(10, MILLISECONDS), is(nullValue()));

        verifyTermination(success);
    }

    @Test
    public void singleElementMultipleValuesDelayedRequestThenSuccess() {
        singleElementMultipleValuesDelayedRequest(true);
    }

    @Test
    public void singleElementMultipleValuesDelayedRequestThenFail() {
        singleElementMultipleValuesDelayedRequest(false);
    }

    private void singleElementMultipleValuesDelayedRequest(boolean success) {
        toSource(publisher.flatMapConcatIterable(identity())).subscribe(subscriber);
        publisher.onSubscribe(subscription);
        subscriber.awaitSubscription().request(1);
        publisher.onNext(asList("one", "two"));
        assertThat(subscriber.takeOnNext(), is("one"));

        if (success) {
            publisher.onComplete();
        } else {
            publisher.onError(DELIBERATE_EXCEPTION);
        }

        subscriber.awaitSubscription().request(1);
        assertThat(subscriber.takeOnNext(), is("two"));

        if (success) {
            subscriber.awaitOnComplete();
        } else {
            assertThat(subscriber.awaitOnError(), sameInstance(DELIBERATE_EXCEPTION));
        }
        assertFalse(subscription.isCancelled());
    }

    @Test
    public void multipleElementsSingleValueThenSuccess() {
        multipleElementsSingleValue(true);
    }

    @Test
    public void multipleElementsSingleValueThenFail() {
        multipleElementsSingleValue(false);
    }

    private void multipleElementsSingleValue(boolean success) {
        toSource(publisher.flatMapConcatIterable(identity())).subscribe(subscriber);
        publisher.onSubscribe(subscription);
        subscriber.awaitSubscription().request(1);
        publisher.onNext(singletonList("one"));
        assertThat(subscriber.takeOnNext(), is("one"));

        subscriber.awaitSubscription().request(1);
        publisher.onNext(singletonList("two"));
        assertThat(subscriber.takeOnNext(), is("two"));

        verifyTermination(success);
    }

    @Test
    public void multipleElementsMultipleValuesThenSuccess() {
        multipleElementsMultipleValues(true);
    }

    @Test
    public void multipleElementsMultipleValuesThenFail() {
        multipleElementsMultipleValues(false);
    }

    private void multipleElementsMultipleValues(boolean success) {
        toSource(publisher.flatMapConcatIterable(identity())).subscribe(subscriber);
        publisher.onSubscribe(subscription);
        subscriber.awaitSubscription().request(1);
        publisher.onNext(asList("one", "two"));
        assertThat(subscriber.takeOnNext(), is("one"));

        subscriber.awaitSubscription().request(1);
        assertThat(subscriber.takeOnNext(), is("two"));

        subscriber.awaitSubscription().request(1);
        publisher.onNext(asList("three", "four"));
        assertThat(subscriber.takeOnNext(), is("three"));

        subscriber.awaitSubscription().request(1);
        assertThat(subscriber.takeOnNext(), is("four"));

        verifyTermination(success);
    }

    @Test
    public void cancelIsPropagated() {
        toSource(publisher.flatMapConcatIterable(identity())).subscribe(subscriber);
        publisher.onSubscribe(subscription);
        subscriber.awaitSubscription().request(1);
        publisher.onNext(asList("one", "two"));
        assertThat(subscriber.takeOnNext(), is("one"));
        subscriber.awaitSubscription().cancel();
        assertTrue(subscription.isCancelled());
    }

    @Test
    public void requestWithEmptyIterableThenSuccess() {
        requestWithEmptyIterable(true);
    }

    @Test
    public void requestWithEmptyIterableThenFail() {
        requestWithEmptyIterable(false);
    }

    private void requestWithEmptyIterable(boolean success) {
        toSource(publisher.flatMapConcatIterable(identity())).subscribe(subscriber);
        publisher.onSubscribe(subscription);
        subscriber.awaitSubscription().request(1);
        subscriber.awaitSubscription().request(1);

        publisher.onNext(asList("one", "two", "three"));
        assertThat(subscriber.takeOnNext(2), contains("one", "two"));

        subscriber.awaitSubscription().request(1);
        assertThat(subscriber.takeOnNext(), is("three"));
        subscriber.awaitSubscription().request(1);
        publisher.onNext(singletonList("four"));
        assertThat(subscriber.takeOnNext(), is("four"));
        verifyTermination(success);
    }

    @Test
    public void exceptionFromOnErrorIsPropagated() {
        toSource(publisher.flatMapConcatIterable(identity())
                .afterOnError(t -> {
                    throw DELIBERATE_EXCEPTION;
                })).subscribe(subscriber);
        subscriber.awaitSubscription();
        expectedException.expect(is(DELIBERATE_EXCEPTION));
        publisher.onError(DELIBERATE_EXCEPTION);
    }

    @Test
    public void testExceptionFromBufferedOnNextThenTerminalIsPropagated() {
        final DeliberateException ex2 = new DeliberateException();
        final AtomicBoolean errored = new AtomicBoolean();
        toSource(publisher.flatMapConcatIterable(identity())
                .map((Function<String, String>) s -> {
                    if (!errored.getAndSet(true)) {
                        publisher.onError(DELIBERATE_EXCEPTION);
                    }
                    throw ex2;
                })).subscribe(subscriber);
        subscriber.awaitSubscription().request(3);
        publisher.onNext(asList("one", "two", "three"));
        assertThat(subscriber.awaitOnError(), is(ex2));
    }

    @Test
    public void exceptionFromOnCompleteIsPropagated() {
        toSource(publisher.flatMapConcatIterable(identity())
                .afterOnComplete(() -> {
                    throw DELIBERATE_EXCEPTION;
                })).subscribe(subscriber);
        subscriber.awaitSubscription();
        expectedException.expect(is(DELIBERATE_EXCEPTION));
        publisher.onComplete();
    }

    @Test
    public void exceptionInsideOnNextWhenOnCompleteRacesRequestNIsPropagated() throws Exception {
        CyclicBarrier barrier = new CyclicBarrier(2);
        toSource(publisher.flatMapConcatIterable(identity())
                .map(new Function<String, String>() {
                    private int count;

                    @Override
                    public String apply(final String s) {
                        if (++count == 2) {
                            throw DELIBERATE_EXCEPTION;
                        }
                        return s;
                    }
                })).subscribe(subscriber);
        final Subscription s = subscriber.awaitSubscription();
        s.request(1);
        publisher.onNext(asList("one", "two", "three"));
        assertThat(subscriber.takeOnNext(), is("one"));
        Future<Void> future = EXECUTOR_RULE.executor().submit(() -> {
            try {
                barrier.await();
            } catch (Exception e) {
                throwException(e);
            }
            s.request(2);
        }).toFuture();
        barrier.await();
        publisher.onComplete();
        future.get();
        assertThat(subscriber.awaitOnError(), is(DELIBERATE_EXCEPTION));
    }

    @Test
    public void exceptionFromOnNextIsPropagated() {
        toSource(publisher.flatMapConcatIterable(identity())
                .map((Function<String, String>) s -> {
                    throw DELIBERATE_EXCEPTION;
                })).subscribe(subscriber);
        subscriber.awaitSubscription().request(1);
        publisher.onNext(asList("one", "two", "three"));
        assertThat(subscriber.awaitOnError(), is(DELIBERATE_EXCEPTION));
    }

    @Test
    public void exceptionFromOnNextIsPropagatedAndDoesNotCancel() {
        TestPublisher<List<String>> localPublisher = new TestPublisher.Builder<List<String>>().disableAutoOnSubscribe()
                .build(subscriber1 -> {
                    subscriber1.onSubscribe(subscription);
                    return subscriber1;
                });
        toSource(localPublisher.flatMapConcatIterable(identity())
                .map((Function<String, String>) s -> {
                    subscriber.awaitSubscription().cancel();
                    throw DELIBERATE_EXCEPTION;
                })).subscribe(subscriber);
        subscriber.awaitSubscription().request(1);
        localPublisher.onNext(asList("one", "two", "three"));
        assertFalse(subscription.isCancelled());
        assertThat(subscriber.awaitOnError(), is(DELIBERATE_EXCEPTION));
    }

    @Test
    public void exceptionFromSubscriptionRequestNIsPropagated() {
        toSource(publisher.flatMapConcatIterable(identity())
                .map((Function<String, String>) s -> {
                    throw DELIBERATE_EXCEPTION;
                })).subscribe(subscriber);
        publisher.onSubscribe(subscription);
        subscriber.awaitSubscription().request(1);
        publisher.onNext(asList("one", "two", "three"));
        assertThat(subscriber.awaitOnError(), is(DELIBERATE_EXCEPTION));
    }

    private void verifyTermination(boolean success) {
        if (success) {
            publisher.onComplete();
            subscriber.awaitOnComplete();
        } else {
            publisher.onError(DELIBERATE_EXCEPTION);
            assertThat(subscriber.awaitOnError(), sameInstance(DELIBERATE_EXCEPTION));
        }
        assertFalse(subscription.isCancelled());
    }
}

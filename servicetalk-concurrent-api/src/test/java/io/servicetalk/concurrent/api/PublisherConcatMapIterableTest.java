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

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.function.Function;

import static io.servicetalk.concurrent.api.Processors.newPublisherProcessor;
import static io.servicetalk.concurrent.api.Publisher.failed;
import static io.servicetalk.concurrent.api.Publisher.from;
import static io.servicetalk.concurrent.api.SourceAdapters.fromSource;
import static io.servicetalk.concurrent.api.SourceAdapters.toSource;
import static io.servicetalk.concurrent.internal.DeliberateException.DELIBERATE_EXCEPTION;
import static io.servicetalk.concurrent.internal.TerminalNotification.complete;
import static java.util.Arrays.asList;
import static java.util.Collections.singletonList;
import static java.util.function.Function.identity;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;
import static org.hamcrest.Matchers.sameInstance;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;

public class PublisherConcatMapIterableTest {
    @Rule
    public final ExpectedException expectedException = ExpectedException.none();

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
        assertTrue(subscriber.subscriptionReceived());
        subscriber.request(1);
        AtomicBoolean cancelled = new AtomicBoolean();
        cancellablePublisher.onNext(new TestIterableToBlockingIterable<>(asList("one", "two"),
                (time, unit) -> { }, (time, unit) -> { }, () -> cancelled.set(true)));
        assertThat(subscriber.takeItems(), contains("one"));
        assertTrue(subscriber.subscriptionReceived());
        assertThat(subscriber.takeItems(), hasSize(0));
        assertThat(subscriber.takeTerminal(), nullValue());
        subscriber.cancel();
        assertTrue(cancelled.get());
    }

    @Test
    public void justComplete() {
        toSource(publisher.flatMapConcatIterable(identity())).subscribe(subscriber);
        publisher.onSubscribe(subscription);
        assertTrue(subscriber.subscriptionReceived());
        verifyTermination(true);
    }

    @Test
    public void justFail() {
        toSource(publisher.flatMapConcatIterable(identity())).subscribe(subscriber);
        publisher.onSubscribe(subscription);
        assertTrue(subscriber.subscriptionReceived());
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
        assertTrue(subscriber.subscriptionReceived());
        subscriber.request(1);
        publisher.onNext(singletonList("one"));
        assertThat(subscriber.takeItems(), contains("one"));
        assertTrue(subscriber.subscriptionReceived());
        assertThat(subscriber.takeItems(), hasSize(0));
        assertThat(subscriber.takeTerminal(), nullValue());

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
        assertTrue(subscriber.subscriptionReceived());
        subscriber.request(1);
        publisher.onNext(asList("one", "two"));
        assertThat(subscriber.takeItems(), contains("one"));

        if (success) {
            publisher.onComplete();
        } else {
            publisher.onError(DELIBERATE_EXCEPTION);
        }

        subscriber.request(1);
        assertThat(subscriber.takeItems(), contains("two"));

        if (success) {
            assertThat(subscriber.takeTerminal(), is(complete()));
        } else {
            assertThat(subscriber.takeError(), sameInstance(DELIBERATE_EXCEPTION));
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
        assertTrue(subscriber.subscriptionReceived());
        subscriber.request(1);
        publisher.onNext(singletonList("one"));
        assertThat(subscriber.takeItems(), contains("one"));

        subscriber.request(1);
        publisher.onNext(singletonList("two"));
        assertThat(subscriber.takeItems(), contains("two"));

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
        assertTrue(subscriber.subscriptionReceived());
        subscriber.request(1);
        publisher.onNext(asList("one", "two"));
        assertThat(subscriber.takeItems(), contains("one"));

        subscriber.request(1);
        assertThat(subscriber.takeItems(), contains("two"));

        subscriber.request(1);
        publisher.onNext(asList("three", "four"));
        assertThat(subscriber.takeItems(), contains("three"));

        subscriber.request(1);
        assertThat(subscriber.takeItems(), contains("four"));

        verifyTermination(success);
    }

    @Test
    public void cancelIsPropagated() {
        toSource(publisher.flatMapConcatIterable(identity())).subscribe(subscriber);
        publisher.onSubscribe(subscription);
        assertTrue(subscriber.subscriptionReceived());
        subscriber.request(1);
        publisher.onNext(asList("one", "two"));
        assertThat(subscriber.takeItems(), contains("one"));
        subscriber.cancel();
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
        assertTrue(subscriber.subscriptionReceived());
        subscriber.request(1);
        subscriber.request(1);

        publisher.onNext(asList("one", "two", "three"));
        assertThat(subscriber.takeItems(), contains("one", "two"));

        subscriber.request(1);
        assertThat(subscriber.takeItems(), contains("three"));
        subscriber.request(1);
        publisher.onNext(singletonList("four"));
        assertThat(subscriber.takeItems(), contains("four"));
        verifyTermination(success);
    }

    @Test
    public void exceptionFromOnErrorIsPropagated() {
        toSource(publisher.flatMapConcatIterable(identity())
                .afterOnError(t -> {
                    throw DELIBERATE_EXCEPTION;
                })).subscribe(subscriber);
        assertTrue(subscriber.subscriptionReceived());
        expectedException.expect(is(DELIBERATE_EXCEPTION));
        publisher.onError(DELIBERATE_EXCEPTION);
    }

    @Test
    public void exceptionFromBufferedOnNextThenOnErrorIsPropagated() {
        testExceptionFromBufferedOnNextThenTerminalIsPropagated(publisher::onError);
    }

    @Test
    public void exceptionFromBufferedOnNextThenOnCompleteIsPropagated() {
        testExceptionFromBufferedOnNextThenTerminalIsPropagated(__ -> publisher.onComplete());
    }

    private void testExceptionFromBufferedOnNextThenTerminalIsPropagated(Consumer<DeliberateException> emitTerminal) {
        final DeliberateException ex2 = new DeliberateException();
        final AtomicBoolean errored = new AtomicBoolean();
        toSource(publisher.flatMapConcatIterable(identity())
                .map((Function<String, String>) s -> {
                    if (!errored.getAndSet(true)) {
                        publisher.onError(DELIBERATE_EXCEPTION);
                    }
                    throw ex2;
                })).subscribe(subscriber);
        subscriber.request(3);
        try {
            publisher.onNext(asList("one", "two", "three"));
            fail("Failure not propagated from onNext");
        } catch (DeliberateException de) {
            emitTerminal.accept(de);
            assertThat(subscriber.takeError(), is(de));
        }
    }

    @Test
    public void exceptionFromOnCompleteIsPropagated() {
        toSource(publisher.flatMapConcatIterable(identity())
                .afterOnComplete(() -> {
                    throw DELIBERATE_EXCEPTION;
                })).subscribe(subscriber);
        assertTrue(subscriber.subscriptionReceived());
        expectedException.expect(is(DELIBERATE_EXCEPTION));
        publisher.onComplete();
    }

    @Test
    public void exceptionFromOnNextIsPropagated() {
        toSource(publisher.flatMapConcatIterable(identity())
                .map((Function<String, String>) s -> {
                    throw DELIBERATE_EXCEPTION;
                })).subscribe(subscriber);
        subscriber.request(1);
        expectedException.expect(is(DELIBERATE_EXCEPTION));
        publisher.onNext(asList("one", "two", "three"));
    }

    @Test
    public void exceptionFromSubscriptionRequestNIsPropagated() {
        toSource(publisher.flatMapConcatIterable(identity())
                .map((Function<String, String>) s -> {
                    throw DELIBERATE_EXCEPTION;
                })).subscribe(subscriber);
        publisher.onSubscribe(subscription);
        assertTrue(subscriber.subscriptionReceived());
        publisher.onNext(asList("one", "two", "three"));
        subscriber.request(1);
        assertThat(subscriber.takeError(), is(DELIBERATE_EXCEPTION));
        assertThat("Subscription was not cancelled.", subscription.isCancelled(), is(true));
    }

    @Test
    public void exceptionFromSubscriptionRequestNIsPropagatedAndNoMoreEventsDelivered() {
        AtomicBoolean shouldThrow = new AtomicBoolean();
        toSource(publisher.flatMapConcatIterable(identity())
                .map(s -> {
                    // Only throw on the first call to map(). If the operator propagates events
                    // after the terminal we want to let them pass through and fail the test.
                    if (shouldThrow.compareAndSet(false, true)) {
                        throw DELIBERATE_EXCEPTION;
                    } else {
                        return s;
                    }
                })).subscribe(subscriber);
        publisher.onNext(singletonList("one"));
        subscriber.request(1);
        assertThat(subscriber.takeError(), is(DELIBERATE_EXCEPTION));
        subscriber.request(1);
        publisher.onNext(asList("two", "three"));
        assertThat(subscriber.takeItems(), is(empty()));
    }

    private void verifyTermination(boolean success) {
        if (success) {
            publisher.onComplete();
            assertThat(subscriber.takeTerminal(), is(complete()));
        } else {
            publisher.onError(DELIBERATE_EXCEPTION);
            assertThat(subscriber.takeError(), sameInstance(DELIBERATE_EXCEPTION));
        }
        assertFalse(subscription.isCancelled());
    }
}

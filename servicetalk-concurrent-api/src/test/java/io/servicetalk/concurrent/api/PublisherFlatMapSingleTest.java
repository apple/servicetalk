/*
 * Copyright © 2018-2019, 2021 Apple Inc. and the ServiceTalk project authors
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

import io.servicetalk.concurrent.PublisherSource;
import io.servicetalk.concurrent.PublisherSource.Subscriber;
import io.servicetalk.concurrent.PublisherSource.Subscription;
import io.servicetalk.concurrent.SingleSource;
import io.servicetalk.concurrent.internal.DeliberateException;
import io.servicetalk.concurrent.test.internal.TestPublisherSubscriber;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;

import static io.servicetalk.concurrent.Cancellable.IGNORE_CANCEL;
import static io.servicetalk.concurrent.api.Publisher.fromIterable;
import static io.servicetalk.concurrent.api.Single.failed;
import static io.servicetalk.concurrent.api.Single.never;
import static io.servicetalk.concurrent.api.Single.succeeded;
import static io.servicetalk.concurrent.api.SourceAdapters.fromSource;
import static io.servicetalk.concurrent.api.SourceAdapters.toSource;
import static io.servicetalk.concurrent.internal.DeliberateException.DELIBERATE_EXCEPTION;
import static io.servicetalk.utils.internal.PlatformDependent.throwException;
import static java.time.Duration.ofMillis;
import static java.util.Arrays.asList;
import static java.util.concurrent.ThreadLocalRandom.current;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static java.util.function.Function.identity;
import static java.util.stream.Collectors.toList;
import static java.util.stream.IntStream.range;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.instanceOf;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;
import static org.hamcrest.Matchers.sameInstance;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class PublisherFlatMapSingleTest {
    private final TestPublisherSubscriber<Integer> subscriber = new TestPublisherSubscriber<>();
    private TestPublisher<Integer> source = new TestPublisher<>();
    private final TestSubscription subscription = new TestSubscription();
    private static ExecutorService executorService;
    private static Executor executor;

    @BeforeAll
    static void beforeClass() {
        executorService = Executors.newFixedThreadPool(10);
        executor = io.servicetalk.concurrent.api.Executors.from(executorService);
    }

    @AfterAll
    static void afterClass() throws Exception {
        executor.closeAsync().toFuture().get();
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void onNextErrorPropagated(boolean delayError) {
        onNextErrorPropagated(x -> executor.submit(() -> x), delayError);
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void succeededSingleOnNextErrorPropagated(boolean delayError) {
        onNextErrorPropagated(Single::succeeded, delayError);
    }

    private void onNextErrorPropagated(Function<? super Integer, ? extends Single<? extends Integer>> func,
                                       boolean delayError) {
        toSource((delayError ? source.flatMapMergeSingleDelayError(func, 2) : source.flatMapMergeSingle(func, 2))
                .<Integer>map(y -> {
                    throw DELIBERATE_EXCEPTION;
                })).subscribe(subscriber);
        subscriber.awaitSubscription().request(2);
        source.onNext(1, 2);
        source.onComplete();
        assertThat(subscriber.awaitOnError(), is(DELIBERATE_EXCEPTION));
    }

    @Test
    void concurrentSingleAndPublisherTermination() throws Exception {
        final List<String> elements = range(0, 1000).mapToObj(Integer::toString).collect(toList());
        final Publisher<String> publisher = fromIterable(elements);
        final Single<List<String>> single = publisher.flatMapMergeSingle(x -> executor.submit(() -> x), 1024)
                .collect(ArrayList::new, (strings, s) -> {
                    strings.add(s);
                    return strings;
                });
        for (int i = 0; i < 10; i++) {
            List<String> list = single.toFuture().get();
            assertThat("Unexpected items received", list, hasSize(1000));
        }
    }

    @Test
    void concurrentSingleErrorAndPublisherTermination() throws Exception {
        final Publisher<Integer> publisher = fromIterable(() -> range(0, 1000).iterator());
        AtomicReference<Throwable> error = new AtomicReference<>();
        final Single<List<Integer>> single = publisher.flatMapMergeSingleDelayError(x -> executor.submit(() -> {
            if (x % 2 == 0) {
                return x;
            }
            throw new DeliberateException();
        }), 1024, 500).onErrorResume(t -> {
            error.set(t);
            return Publisher.empty();
        }).collect(ArrayList::new, (ints, s) -> {
            ints.add(s);
            return ints;
        });

        for (int i = 0; i < 10; i++) {
            List<Integer> list = single.toFuture().get();
            assertThat("Unexpected items received", list, hasSize(500));
            Throwable cause = error.get();
            assertThat(cause, instanceOf(DeliberateException.class));
            // everything but the first error is suppressed
            assertThat("Unexpected exception.", asList(cause.getSuppressed()), hasSize(499));
        }
    }

    @Test
    void testSingleItemSyncSingle() {
        toSource(source.flatMapMergeSingle(integer1 -> succeeded(2), 2)).subscribe(subscriber);
        subscriber.awaitSubscription().request(1);
        source.onNext(1);
        source.onComplete();
        assertThat(subscriber.takeOnNext(), is(2));
        subscriber.awaitOnComplete();
    }

    @Test
    void testSingleItemCompletesWithNull() {
        toSource(source.<Integer>flatMapMergeSingle(integer1 -> succeeded(null), 2)).subscribe(subscriber);
        subscriber.awaitSubscription().request(1);
        source.onNext(1);
        source.onComplete();
        assertThat(subscriber.takeOnNext(), is(nullValue()));
        subscriber.awaitOnComplete();
    }

    @Test
    void testSingleItemSourceCompleteFirst() {
        LegacyTestSingle<Integer> single = new LegacyTestSingle<>();
        toSource(source.flatMapMergeSingle(integer1 -> single, 2)).subscribe(subscriber);
        subscriber.awaitSubscription().request(1);
        source.onNext(1);
        source.onComplete();
        single.onSuccess(2);
        assertThat(subscriber.takeOnNext(), is(2));
        subscriber.awaitOnComplete();
    }

    @Test
    void testSingleItemSingleCompleteFirst() {
        LegacyTestSingle<Integer> single = new LegacyTestSingle<>();
        toSource(source.flatMapMergeSingle(integer1 -> single, 2)).subscribe(subscriber);
        subscriber.awaitSubscription().request(1);
        source.onNext(1);
        single.onSuccess(2);
        source.onComplete();
        assertThat(subscriber.takeOnNext(), is(2));
        subscriber.awaitOnComplete();
    }

    @Test
    void testSingleItemSingleError() {
        toSource(source.<Integer>flatMapMergeSingle(integer1 -> failed(DELIBERATE_EXCEPTION), 2))
                .subscribe(subscriber);
        subscriber.awaitSubscription().request(1);
        source.onNext(1);
        assertThat(subscriber.awaitOnError(), sameInstance(DELIBERATE_EXCEPTION));
    }

    @Test
    void testSingleErrorPostSourceComplete() {
        LegacyTestSingle<Integer> single = new LegacyTestSingle<>();
        toSource(source.flatMapMergeSingle(integer1 -> single, 2)).subscribe(subscriber);
        subscriber.awaitSubscription().request(1);
        source.onNext(1);
        source.onComplete();
        single.onError(DELIBERATE_EXCEPTION);
        assertThat(subscriber.awaitOnError(), sameInstance(DELIBERATE_EXCEPTION));
    }

    @ParameterizedTest(name = "{displayName} [{index}] errorFirst={0} errorSecond={1}")
    @CsvSource(value = {"true,true", "true,false", "false,true", "false,false"})
    void testDuplicateTerminal(boolean errorFirst, boolean errorSecond) {
        SingleSource<Integer> single = subscriber -> {
            subscriber.onSubscribe(IGNORE_CANCEL);
            if (errorFirst) {
                subscriber.onError(DELIBERATE_EXCEPTION);
            } else {
                subscriber.onSuccess(2);
            }

            // intentionally violate the RS spec to verify the operator's behavior.
            // [1] https://github.com/reactive-streams/reactive-streams-jvm/blob/v1.0.3/README.md#1.7
            if (errorSecond) {
                subscriber.onError(new IllegalStateException("duplicate terminal should be discarded!"));
            } else {
                subscriber.onSuccess(3);
            }
        };
        @SuppressWarnings("unchecked")
        Subscriber<Integer> mockSubscriber = mock(Subscriber.class);
        toSource(source.flatMapMergeSingle(integer1 -> fromSource(single), 2)).subscribe(mockSubscriber);
        source.onNext(1);

        if (errorFirst) {
            verify(mockSubscriber).onError(DELIBERATE_EXCEPTION);
        } else {
            source.onComplete();
            verify(mockSubscriber).onComplete();
        }
    }

    @ParameterizedTest(name = "{displayName} [{index}] delayError={0}")
    @ValueSource(booleans = {true, false})
    void mappedSourceIsSubscribedAfterCancel(boolean delayError) throws InterruptedException {
        final int item1 = 1;
        final int item2 = 2;
        TestSubscription upstreamSubscription = new TestSubscription();
        source = new TestPublisher.Builder<Integer>()
                .disableAutoOnSubscribe().build(subscriber1 -> {
                    subscriber1.onSubscribe(upstreamSubscription);
                    return subscriber1;
                });
        CountDownLatch latch = new CountDownLatch(1);
        TestCancellable mappedCancellable = new TestCancellable();
        TestSingle<Integer> mappedSingle = new TestSingle.Builder<Integer>()
                .disableAutoOnSubscribe().build(subscriber1 -> {
                    subscriber1.onSubscribe(mappedCancellable);
                    latch.countDown();
                    return subscriber1;
                });
        TestCancellable mappedCancellable2 = new TestCancellable();
        TestSingle<Integer> mappedSingle2 = new TestSingle.Builder<Integer>()
                .disableAutoOnSubscribe().build(subscriber1 -> {
                    subscriber1.onSubscribe(mappedCancellable2);
                    return subscriber1;
                });
        Function<Integer, Single<Integer>> mapper =
                i -> i == item1 ? mappedSingle : i == item2 ? mappedSingle2 : never();
        toSource(delayError ? source.flatMapMergeSingleDelayError(mapper, 2) : source.flatMapMergeSingle(mapper, 2))
                .subscribe(subscriber);
        Subscription subscription = subscriber.awaitSubscription();
        subscription.request(2);
        upstreamSubscription.awaitRequestN(2);
        source.onNext(item1);

        // Wait for the first mapped publisher to be subscribed to, then cancel.
        latch.await();
        subscription.cancel();
        source.onNext(item2);
        mappedCancellable2.awaitCancelled();
    }

    @ParameterizedTest(name = "{displayName} [{index}] onError={0} delayError={1}")
    @CsvSource(value = {"true,true", "true,false", "false,true", "false,false"})
    void onNextAfterTerminalThrows(boolean onError, boolean delayError) {
        PublisherSource<Single<Integer>> mappedPublisher = subscriber -> subscriber.onSubscribe(new Subscription() {
            private boolean terminated;
            @Override
            public void request(final long n) {
                if (n > 0 && !terminated) {
                    terminated = true;
                    subscriber.onNext(never());

                    if (onError) {
                        subscriber.onError(DELIBERATE_EXCEPTION);
                    } else {
                        subscriber.onComplete();
                    }

                    // intentionally violate the RS spec to verify the operator's behavior.
                    subscriber.onNext(succeeded(2));
                }
            }

            @Override
            public void cancel() {
                // noop
            }
        });
        Publisher<Single<Integer>> publisher = fromSource(mappedPublisher);
        toSource(delayError ?
                publisher.flatMapMergeSingleDelayError(identity()) :
                publisher.flatMapMergeSingle(identity()))
                .subscribe(subscriber);
        if (onError) {
            // If an error has already been delivered flatMap internal state doesn't need to be invalidated to track
            // the last publisher that terminates, so we expect the original exception to be propagated.
            subscriber.awaitSubscription().request(1);
            assertThat(subscriber.awaitOnError(), is(DELIBERATE_EXCEPTION));
        } else {
            assertThrows(IllegalStateException.class, () -> subscriber.awaitSubscription().request(1));
        }
    }

    @Test
    void cancelPropagatedBeforeErrorButOriginalErrorPreserved() {
        CountDownLatch cancelledLatch = new CountDownLatch(1);
        source = new TestPublisher.Builder<Integer>().disableAutoOnSubscribe().build(subscriber1 -> {
            subscriber1.onSubscribe(new Subscription() {
                @Override
                public void request(final long n) {
                }

                @Override
                public void cancel() {
                    try {
                        cancelledLatch.await();
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        throwException(e);
                    }
                    subscriber1.onError(new IllegalStateException("shouldn't reach the Subscriber!"));
                }
            });
            return subscriber1;
        });
        TestSingle<Integer> mappedSingle = new TestSingle<>();
        toSource(source.flatMapMergeSingle(i -> mappedSingle, 1)).subscribe(subscriber);
        subscriber.awaitSubscription().request(1);
        source.onNext(1);

        executor.execute(() -> mappedSingle.onError(DELIBERATE_EXCEPTION));
        // Verify that cancel happens before terminal. This ensures that sources which allow for multiple sequential
        // Subscribers can clear out there current subscriber in preparation for the next Subscriber and avoid duplicate
        // subscribe related errors.
        assertThat(subscriber.pollTerminal(10, MILLISECONDS), is(nullValue()));
        cancelledLatch.countDown();
        assertThat(subscriber.awaitOnError(), sameInstance(DELIBERATE_EXCEPTION));
    }

    @Test
    void testSourceEmitsErrorNoOnNexts() {
        toSource(source.flatMapMergeSingle(integer1 -> succeeded(2), 2)).subscribe(subscriber);
        subscriber.awaitSubscription().request(1);
        source.onError(DELIBERATE_EXCEPTION);
        assertThat(subscriber.awaitOnError(), sameInstance(DELIBERATE_EXCEPTION));
    }

    @Test
    void testSourceEmitsErrorPostOnNexts() {
        toSource(source.flatMapMergeSingle(integer1 -> succeeded(2), 2)).subscribe(subscriber);
        subscriber.awaitSubscription().request(1);
        source.onNext(1);
        source.onError(DELIBERATE_EXCEPTION);
        assertThat(subscriber.takeOnNext(), is(2));
        assertThat(subscriber.awaitOnError(), sameInstance(DELIBERATE_EXCEPTION));
    }

    @Test
    void testSourceEmitsErrorPostOnNextsSingleNotCompleted() {
        LegacyTestSingle<Integer> single = new LegacyTestSingle<>(true);
        toSource(source.flatMapMergeSingle(integer1 -> single, 2)).subscribe(subscriber);
        subscriber.awaitSubscription().request(1);
        source.onNext(1);
        source.onError(DELIBERATE_EXCEPTION);
        assertThat(subscriber.awaitOnError(), sameInstance(DELIBERATE_EXCEPTION));
        single.verifyCancelled();
        single.onError(new DeliberateException());
    }

    @Test
    void testSubscriberCancel() {
        LegacyTestSingle<Integer> single = new LegacyTestSingle<>();
        toSource(source.flatMapMergeSingle(integer1 -> single, 2)).subscribe(subscriber);
        subscriber.awaitSubscription().request(1);
        source.onNext(1);
        subscriber.awaitSubscription().cancel();
        single.verifyCancelled();
        assertThat(subscriber.pollOnNext(10, MILLISECONDS), is(nullValue()));
        assertThat(subscriber.pollTerminal(10, MILLISECONDS), is(nullValue()));
    }

    @Test
    void testSingleCompletePostCancel() {
        final TestPublisherSubscriber<Integer> subscriber = new TestPublisherSubscriber<>();
        LegacyTestSingle<Integer> single = new LegacyTestSingle<>(true);
        toSource(source.flatMapMergeSingle(integer1 -> single, 2)).subscribe(subscriber);
        subscriber.awaitSubscription().request(1);
        source.onNext(1);
        subscriber.awaitSubscription().cancel();
        single.verifyCancelled();
        assertThat(subscriber.pollOnNext(10, MILLISECONDS), is(nullValue()));
        assertThat(subscriber.pollTerminal(10, MILLISECONDS), is(nullValue()));
        single.onSuccess(4);
        assertThat(subscriber.takeOnNext(), is(4));
        source.onComplete();
        subscriber.awaitOnComplete();
    }

    @Test
    void testSingleErrorPostCancel() {
        LegacyTestSingle<Integer> single = new LegacyTestSingle<>(true);
        toSource(source.flatMapMergeSingle(integer1 -> single, 2)).subscribe(subscriber);
        subscriber.awaitSubscription().request(1);
        source.onNext(1);
        subscriber.awaitSubscription().cancel();
        single.verifyCancelled();
        assertThat(subscriber.pollOnNext(10, MILLISECONDS), is(nullValue()));
        assertThat(subscriber.pollTerminal(10, MILLISECONDS), is(nullValue()));
        single.onError(DELIBERATE_EXCEPTION);
        assertThat(subscriber.awaitOnError(), sameInstance(DELIBERATE_EXCEPTION));
    }

    @Test
    void testMaxConcurrency() {
        List<LegacyTestSingle<Integer>> emittedSingles = new ArrayList<>();
        toSource(source.flatMapMergeSingle(integer -> {
            LegacyTestSingle<Integer> s = new LegacyTestSingle<>();
            emittedSingles.add(s);
            return s;
        }, 2)).subscribe(subscriber);
        source.onSubscribe(subscription);
        subscriber.awaitSubscription().request(3);
        // Should not request more than max concurrency.
        assertThat(subscription.requested(), is(2L));

        source.onNext(1, 1);
        assertThat("Unexpected number of Singles emitted.", emittedSingles, hasSize(2));
        emittedSingles.remove(0).onSuccess(2);
        assertThat(subscriber.takeOnNext(), is(2));

        // Total requested must equal actual requested.
        assertThat(subscription.requested(), is(3L));

        emittedSingles.remove(0).onSuccess(3);
        assertThat(subscriber.takeOnNext(), is(3));
        source.onNext(1);
        source.onComplete();
        assertThat("Unexpected number of Singles emitted.", emittedSingles, hasSize(1));
        emittedSingles.remove(0).onSuccess(4);
        assertThat(subscriber.takeOnNext(), is(4));
        subscriber.awaitOnComplete();

        // Total requested must equal actual requested.
        assertThat(subscription.requested(), is(3L));
    }

    @Test
    void testMapperThrows() {
        toSource(source.<Integer>flatMapMergeSingle(integer1 -> {
            throw DELIBERATE_EXCEPTION;
        }, 2)).subscribe(subscriber);
        source.onSubscribe(subscription);
        subscriber.awaitSubscription().request(1);

        source.onNext(1);
        assertFalse(subscription.isCancelled());
        assertThat(subscriber.awaitOnError(), sameInstance(DELIBERATE_EXCEPTION));
    }

    @Test
    void testNoFlowControl() {
        List<LegacyTestSingle<Integer>> emittedSingles = new ArrayList<>();
        toSource(source.flatMapMergeSingle(integer1 -> {
            LegacyTestSingle<Integer> s1 = new LegacyTestSingle<>();
            emittedSingles.add(s1);
            return s1;
        }, 2)).subscribe(subscriber);
        source.onSubscribe(subscription);
        subscriber.awaitSubscription().request(Long.MAX_VALUE);
        // Should not request more than max concurrency.
        assertThat(subscription.requested(), is(2L));

        source.onNext(1, 1);
        assertThat("Unexpected number of Singles emitted.", emittedSingles, hasSize(2));
        emittedSingles.remove(0).onSuccess(2);
        assertThat(subscriber.takeOnNext(), is(2));

        // Request enough on completion to reach max concurrency.
        assertThat(subscription.requested(), is(3L));

        emittedSingles.remove(0).onSuccess(3);
        // Request enough on completion to reach max concurrency.
        assertThat(subscription.requested(), is(4L));
        assertThat(subscriber.takeOnNext(), is(3));

        source.onNext(1);
        source.onComplete();
        assertThat("Unexpected number of Singles emitted.", emittedSingles, hasSize(1));
        emittedSingles.remove(0).onSuccess(4);
        assertThat(subscriber.takeOnNext(), is(4));
        subscriber.awaitOnComplete();

        // Request enough on completion to reach max concurrency.
        assertThat(subscription.requested(), is(6L));
    }

    @Test
    void testRequestPostSingleError() {
        toSource(source.<Integer>flatMapMergeSingleDelayError(integer1 -> failed(DELIBERATE_EXCEPTION), 2))
                .subscribe(subscriber);
        source.onSubscribe(subscription);
        subscriber.awaitSubscription().request(3);
        assertThat(subscription.requested(), is(2L));
        source.onNext(1); // Request no more than max concurrency.
        assertThat(subscriber.pollOnNext(10, MILLISECONDS), is(nullValue()));
        assertThat(subscriber.pollTerminal(10, MILLISECONDS), is(nullValue()));
        assertThat(subscription.requested(), is(3L));
        source.onNext(1); // Request more with 1 single completion.
        assertThat(subscription.requested(), is(3L));
        source.onComplete(); // Stop requesting more.
        Throwable cause = subscriber.awaitOnError();
        assertThat(cause, is(DELIBERATE_EXCEPTION));
    }

    @Test
    void testRequestMultipleTimes() {
        toSource(source.flatMapMergeSingle(integer1 -> succeeded(2), 10)).subscribe(subscriber);
        source.onSubscribe(subscription);
        subscriber.awaitSubscription().request(2);
        assertThat(subscription.requested(), is(2L));
        source.onNext(1, 1);
        assertThat(subscriber.takeOnNext(2), contains(2, 2));
        subscriber.awaitSubscription().request(2);
        source.onNext(1, 1);
        assertThat(subscriber.takeOnNext(2), contains(2, 2));
    }

    @Test
    void testRequestMultipleTimesBreachMaxConcurrency() {
        toSource(source.flatMapMergeSingle(integer -> succeeded(2), 2)).subscribe(subscriber);
        source.onSubscribe(subscription);
        subscriber.awaitSubscription().request(2);
        subscriber.awaitSubscription().request(2);
        assertThat(subscription.requested(), is(2L));
        source.onNext(1, 1);
        source.onNext(1, 1);
        source.onComplete();
        assertThat(subscriber.takeOnNext(4), contains(2, 2, 2, 2));
        subscriber.awaitOnComplete();
    }

    @Test
    void testMultipleSingleErrors() {
        Queue<DeliberateException> errors = new ArrayDeque<>();
        toSource(source.flatMapMergeSingleDelayError(integer -> {
            DeliberateException de = new DeliberateException();
            errors.add(de);
            return Single.<Integer>failed(de);
        }, 2, 2)).subscribe(subscriber);
        subscriber.awaitSubscription().request(3);
        source.onNext(1, 1);
        source.onComplete();
        Throwable cause = subscriber.awaitOnError();
        assertThat(errors, hasSize(2));
        assertThat(asList(cause.getSuppressed()), hasSize(1));
        assertThat(cause, is(errors.poll()));
        assertThat(cause.getSuppressed()[0], is(errors.poll()));
    }

    @Test
    void testRequestLongMaxValue() {
        int maxConcurrency = 2;
        toSource(source.flatMapMergeSingle(integer1 -> succeeded(2), maxConcurrency)).subscribe(subscriber);
        source.onSubscribe(subscription);
        subscriber.awaitSubscription().request(Long.MAX_VALUE);
        assertThat(subscription.requested(), is((long) maxConcurrency));
        source.onNext(2);
        assertThat(subscription.requested(), is((long) (maxConcurrency + 1)));
        subscriber.awaitSubscription().request(Long.MAX_VALUE);
        assertThat(subscription.requested(), is((long) (maxConcurrency + 1)));
    }

    @Test
    void testAccumulateToLongMaxValue() {
        int maxConcurrency = 2;
        toSource(source.flatMapMergeSingle(integer1 -> succeeded(2), maxConcurrency)).subscribe(subscriber);
        source.onSubscribe(subscription);
        subscriber.awaitSubscription().request(Long.MAX_VALUE - 1);
        assertThat(subscription.requested(), is((long) maxConcurrency));
        subscriber.awaitSubscription().request(2);
        assertThat(subscription.requested(), is((long) maxConcurrency));
        source.onNext(1, 2);
        assertThat(subscription.requested(), is((long) (maxConcurrency + 2)));
    }

    @Test
    void testAccumulateToIntMaxValue() {
        int maxConcurrency = 2;
        toSource(source.flatMapMergeSingle(integer1 -> succeeded(2), maxConcurrency)).subscribe(subscriber);
        source.onSubscribe(subscription);
        subscriber.awaitSubscription().request(Integer.MAX_VALUE - 1);
        assertThat(subscription.requested(), is((long) maxConcurrency));
        subscriber.awaitSubscription().request(2);
        assertThat(subscription.requested(), is((long) maxConcurrency));
        source.onNext(1, 2);
        assertThat(subscription.requested(), is((long) (maxConcurrency + 2)));
    }

    @Test
    void testReentry() throws InterruptedException {
        Queue<String> resultsQueue = new ConcurrentLinkedQueue<>();
        AtomicReference<Throwable> causeRef = new AtomicReference<>();
        CountDownLatch latch = new CountDownLatch(1);
        final Integer[] expectedNumbers = new Integer[1000000];
        // big enough to trigger stack overflow if we are not careful.
        for (int i = 0; i < expectedNumbers.length; ++i) {
            expectedNumbers[i] = i;
        }
        PublisherFlatMapSingle<Integer, String> pub = new PublisherFlatMapSingle<>(Publisher.from(expectedNumbers),
                value -> succeeded(Integer.toString(value)),
                false, 1);
        toSource(pub).subscribe(new Subscriber<String>() {
            private Subscription subscription;

            @Override
            public void onSubscribe(Subscription s) {
                subscription = s;
                s.request(1);
            }

            @Override
            public void onNext(String s) {
                resultsQueue.add(s);
                subscription.request(1);
            }

            @Override
            public void onError(Throwable t) {
                causeRef.set(t);
                latch.countDown();
            }

            @Override
            public void onComplete() {
                latch.countDown();
            }
        });

        latch.await();
        assertNull(causeRef.get());
        for (Integer expectedNumber : expectedNumbers) {
            assertEquals(expectedNumber.toString(), resultsQueue.poll());
        }
        assertTrue(resultsQueue.isEmpty());
    }

    @Test
    void testEmitFromQueue() {
        List<TestSingle<Integer>> emittedSingles = new ArrayList<>();
        io.servicetalk.concurrent.test.internal.TestPublisherSubscriber<Integer> subscriber =
                new io.servicetalk.concurrent.test.internal.TestPublisherSubscriber<>();
        TestSubscription upstreamSubscription = new TestSubscription();
        source = new TestPublisher.Builder<Integer>().disableAutoOnSubscribe().build(subscriber1 -> {
            subscriber1.onSubscribe(upstreamSubscription);
            return subscriber1;
        });
        toSource(source.flatMapMergeSingle(integer -> {
            TestSingle<Integer> s = new TestSingle<>();
            emittedSingles.add(s);
            return s;
        }, 2)).subscribe(subscriber);
        subscriber.awaitSubscription().request(Long.MAX_VALUE);
        source.onNext(1, 1);
        assertThat("Unexpected number of Singles emitted.", emittedSingles, hasSize(2));

        TestSingle<Integer> single1 = emittedSingles.remove(0);
        TestSingle<Integer> single2 = emittedSingles.remove(0);

        executorService.execute(() -> {
            single1.onSuccess(2);
            single2.onSuccess(3);
        });

        Integer nextItem = subscriber.takeOnNext();
        assertNotNull(nextItem);
        assertEquals(2, nextItem.intValue());
        nextItem = subscriber.takeOnNext();
        assertNotNull(nextItem);
        assertEquals(3, nextItem.intValue());
        assertThat(subscriber.pollTerminal(200, MILLISECONDS), is(nullValue()));

        source.onComplete();
        subscriber.awaitOnComplete();
    }

    @Test
    void testRequestAndEmitConcurrency() throws Exception {
        int totalToRequest = 1000;
        Set<Integer> received = new LinkedHashSet<>(totalToRequest);
        toSource(source.flatMapMergeSingle(Single::succeeded, 2).beforeOnNext(received::add)).subscribe(subscriber);
        source.onSubscribe(subscription);
        CountDownLatch requestingStarting = new CountDownLatch(1);
        Future<?> submitFuture = executorService.submit(() -> {
            requestingStarting.countDown();
            for (int i = 0; i < totalToRequest; i++) {
                subscriber.awaitSubscription().request(1);
            }
        });
        // Just to make sure we have both threads running concurrently.
        requestingStarting.await();
        for (int i = 0; i < totalToRequest; i++) {
            subscription.awaitRequestN(i + 1);
            source.onNext(i);
        }
        submitFuture.get(); // Await everything requested.
        assertThat("Unexpected items emitted.", received, hasSize(totalToRequest));
        assertThat(received, containsInAnyOrder(range(0, totalToRequest).boxed().toArray()));
    }

    @Test
    void testConcurrentEmissions() throws Exception {
        final int maxSingles = 1_000;
        final int expected = range(1, maxSingles).reduce(0, Integer::sum);

        final int actual = Publisher.range(1, maxSingles)
                .flatMapMergeSingle(key -> executor.timer(ofMillis(current().nextInt(10)))
                                                   .toSingle().map(ignored -> key), 10)
                .collect(() -> 0, Integer::sum)
                .toFuture().get();

        assertThat(actual, is(equalTo(expected)));
    }
}

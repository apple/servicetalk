/*
 * Copyright © 2021 Apple Inc. and the ServiceTalk project authors
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

import io.servicetalk.concurrent.PublisherSource.Subscription;
import io.servicetalk.concurrent.internal.DeliberateException;
import io.servicetalk.concurrent.test.internal.TestPublisherSubscriber;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;

import static io.servicetalk.concurrent.api.SourceAdapters.fromSource;
import static io.servicetalk.concurrent.api.SourceAdapters.toSource;
import static io.servicetalk.concurrent.internal.DeliberateException.DELIBERATE_EXCEPTION;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.lessThanOrEqualTo;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.jupiter.api.Assertions.assertThrows;

class PublisherGroupByTest {
    private TestPublisher<Integer> source;
    private TestPublisherSubscriber<GroupedPublisher<Integer, Integer>> groupSub;
    private TestPublisherSubscriber<Integer> group1Sub;
    private TestPublisherSubscriber<Integer> group2Sub;
    private TestPublisherSubscriber<Integer> group3Sub;
    private TestSubscription subscription;

    @BeforeEach
    void setUp() {
        subscription = new TestSubscription();
        source = new TestPublisher.Builder<Integer>().disableAutoOnSubscribe().build(subscriber1 -> {
            subscriber1.onSubscribe(subscription);
            return subscriber1;
        });
        groupSub = new TestPublisherSubscriber<>();
        group1Sub = new TestPublisherSubscriber<>();
        group2Sub = new TestPublisherSubscriber<>();
        group3Sub = new TestPublisherSubscriber<>();
    }

    private Subscription initGroup1() throws InterruptedException {
        return initGroup1(1);
    }

    private Subscription initGroup1(long initialRequest) throws InterruptedException {
        return initGroup1(initialRequest, 16);
    }

    private Subscription initGroup1(long initialRequest, int maxQueue) throws InterruptedException {
        toSource(source.groupBy(integer -> integer == null ? -1 : integer / 100, maxQueue)).subscribe(groupSub);
        Subscription groupSubscription = groupSub.awaitSubscription();
        groupSubscription.request(initialRequest);
        subscription.awaitRequestN(1);
        source.onNext(0);
        GroupedPublisher<Integer, Integer> group1 = groupSub.takeOnNext();
        assertThat(group1, notNullValue());
        assertThat(group1.key(), is(0));
        toSource(group1).subscribe(group1Sub);
        return groupSubscription;
    }

    private Subscription takeGroup2() {
        GroupedPublisher<Integer, Integer> group2 = groupSub.takeOnNext();
        assertThat(group2, notNullValue());
        assertThat(group2.key(), is(1));

        toSource(group2).subscribe(group2Sub);
        return group2Sub.awaitSubscription();
    }

    private Subscription takeGroup3() {
        GroupedPublisher<Integer, Integer> group3 = groupSub.takeOnNext();
        assertThat(group3, notNullValue());
        assertThat(group3.key(), is(2));

        toSource(group3).subscribe(group3Sub);
        return group3Sub.awaitSubscription();
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void groupEmissionsWaitForDemand(boolean onError) throws InterruptedException {
        initGroup1(9, 9);
        Subscription subscription1 = group1Sub.awaitSubscription();
        subscription.awaitRequestN(9);
        assertThat(group1Sub.pollOnNext(10, MILLISECONDS), is(nullValue()));

        source.onNext(100);
        source.onNext(200);
        source.onNext(1);
        source.onNext(101);
        source.onNext(201);
        source.onNext(2);
        source.onNext(102);
        source.onNext(202);

        Subscription subscription2 = takeGroup2();
        assertThat(group2Sub.pollOnNext(10, MILLISECONDS), is(nullValue()));
        Subscription subscription3 = takeGroup3();
        assertThat(group3Sub.pollOnNext(10, MILLISECONDS), is(nullValue()));

        subscription1.request(3);
        subscription.awaitRequestN(12);
        assertThat(group1Sub.takeOnNext(3), contains(0, 1, 2));

        subscription2.request(3);
        subscription.awaitRequestN(15);
        assertThat(group2Sub.takeOnNext(3), contains(100, 101, 102));

        subscription3.request(3);
        subscription.awaitRequestN(18);
        assertThat(group3Sub.takeOnNext(3), contains(200, 201, 202));

        terminate3(onError);
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void rootEmissionsWaitForDemand(boolean onError) throws InterruptedException {
        Subscription groupSubscription = initGroup1(1, 2);
        Subscription subscription1 = group1Sub.awaitSubscription();
        subscription1.request(2);
        subscription.awaitRequestN(3);
        assertThat(group1Sub.takeOnNext(), is(0));

        source.onNext(100);
        // no demand on the groupSub yet, no emissions expected.
        assertThat(groupSub.pollOnNext(10, MILLISECONDS), is(nullValue()));
        source.onNext(200);
        // no demand on the groupSub yet, no emissions expected.
        assertThat(groupSub.pollOnNext(10, MILLISECONDS), is(nullValue()));

        groupSubscription.request(1);
        subscription.awaitRequestN(4);
        Subscription subscription2 = takeGroup2();
        assertThat(group2Sub.pollOnNext(10, MILLISECONDS), is(nullValue()));

        groupSubscription.request(1);
        subscription.awaitRequestN(5);
        Subscription subscription3 = takeGroup3();
        assertThat(group3Sub.pollOnNext(10, MILLISECONDS), is(nullValue()));

        subscription2.request(1);
        assertThat(group2Sub.takeOnNext(), is(100));

        subscription3.request(1);
        assertThat(group3Sub.takeOnNext(), is(200));

        terminate3(onError);
    }

    private void terminate3(boolean onError) {
        if (onError) {
            source.onError(DELIBERATE_EXCEPTION);
            assertThat(groupSub.awaitOnError(), is(DELIBERATE_EXCEPTION));
            assertThat(group1Sub.awaitOnError(), is(DELIBERATE_EXCEPTION));
            assertThat(group2Sub.awaitOnError(), is(DELIBERATE_EXCEPTION));
            assertThat(group3Sub.awaitOnError(), is(DELIBERATE_EXCEPTION));
        } else {
            source.onComplete();
            groupSub.awaitOnComplete();
            group1Sub.awaitOnComplete();
            group2Sub.awaitOnComplete();
            group3Sub.awaitOnComplete();
        }
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void terminalEventIsQueued(boolean onError) throws InterruptedException {
        Subscription groupSubscription = initGroup1(1, 2);
        Subscription subscription1 = group1Sub.awaitSubscription();
        subscription1.request(2);
        subscription.awaitRequestN(3);
        assertThat(group1Sub.takeOnNext(), is(0));

        source.onNext(100);
        source.onNext(101);

        // no demand on the groupSub yet, no emissions expected.
        assertThat(groupSub.pollOnNext(10, MILLISECONDS), is(nullValue()));

        if (onError) {
            source.onError(DELIBERATE_EXCEPTION);
            assertThat(group1Sub.awaitOnError(), is(DELIBERATE_EXCEPTION));
        } else {
            source.onComplete();
            group1Sub.awaitOnComplete();
        }

        // groupSub still has an item queued, terminal should be delayed.
        assertThat(groupSub.pollTerminal(10, MILLISECONDS), is(nullValue()));

        groupSubscription.request(1);
        Subscription subscription2 = takeGroup2();
        if (onError) {
            assertThat(groupSub.awaitOnError(), is(DELIBERATE_EXCEPTION));
        } else {
            groupSub.awaitOnComplete();
        }

        subscription2.request(2);
        assertThat(group2Sub.takeOnNext(2), contains(100, 101));
        if (onError) {
            assertThat(group2Sub.awaitOnError(), is(DELIBERATE_EXCEPTION));
        } else {
            group2Sub.awaitOnComplete();
        }
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void groupOnNextThrowsFromSubscription(boolean onError) throws InterruptedException {
        initGroup1(10);
        Subscription subscription1 = group1Sub.awaitSubscription();
        subscription.awaitRequestN(10);

        source.onNext(105, 110);
        GroupedPublisher<Integer, Integer> group2 = groupSub.takeOnNext();
        assertThat(group2, notNullValue());
        assertThat(group2.key(), is(1));

        AtomicBoolean shouldThrow = new AtomicBoolean();
        toSource(group2.afterOnNext(i -> {
            if (shouldThrow.compareAndSet(false, true)) {
                throw DELIBERATE_EXCEPTION;
            }
        })).subscribe(group2Sub);
        Subscription subscription2 = group2Sub.awaitSubscription();

        assertThrows(DeliberateException.class, () -> subscription2.request(2));

        assertThat(group2Sub.takeOnNext(2), contains(105, 110));

        if (onError) {
            source.onError(DELIBERATE_EXCEPTION);
            assertThat(groupSub.awaitOnError(), is(DELIBERATE_EXCEPTION));
            assertThat(group2Sub.awaitOnError(), is(DELIBERATE_EXCEPTION));
        } else {
            source.onComplete();
            groupSub.awaitOnComplete();
            group2Sub.awaitOnComplete();
        }
        subscription1.request(1);
        assertThat(group1Sub.takeOnNext(), is(0));
        if (onError) {
            assertThat(group1Sub.awaitOnError(), is(DELIBERATE_EXCEPTION));
        } else {
            group1Sub.awaitOnComplete();
        }
    }

    @Test
    void groupOnNextThrowsFromSubscriber() throws InterruptedException {
        initGroup1(10);
        Subscription subscription1 = group1Sub.awaitSubscription();
        subscription.awaitRequestN(10);

        source.onNext(105);
        GroupedPublisher<Integer, Integer> group2 = groupSub.takeOnNext();
        assertThat(group2, notNullValue());
        assertThat(group2.key(), is(1));

        AtomicInteger onNextCount = new AtomicInteger();
        toSource(group2.afterOnNext(i -> {
            if (onNextCount.incrementAndGet() == 2) {
                throw DELIBERATE_EXCEPTION;
            }
        })).subscribe(group2Sub);
        Subscription subscription2 = group2Sub.awaitSubscription();

        subscription2.request(2);
        assertThat(group2Sub.takeOnNext(), is(105));
        source.onNext(110);

        assertThat(groupSub.awaitOnError(), is(DELIBERATE_EXCEPTION));

        assertThat(group2Sub.takeOnNext(), is(110));
        assertThat(group2Sub.awaitOnError(), is(DELIBERATE_EXCEPTION));

        subscription1.request(1);
        assertThat(group1Sub.takeOnNext(), is(0));
        assertThat(group1Sub.awaitOnError(), is(DELIBERATE_EXCEPTION));
    }

    @Test
    void keySelectorThrows() throws InterruptedException {
        toSource(source.<Integer>groupBy(integer -> {
            throw DELIBERATE_EXCEPTION;
        }, 16)).subscribe(groupSub);
        Subscription groupSubscription = groupSub.awaitSubscription();
        groupSubscription.request(1);
        subscription.awaitRequestN(1);
        source.onNext(0);
        assertThat(groupSub.awaitOnError(), is(DELIBERATE_EXCEPTION));
    }

    @Test
    void nullValueIsSupported() throws InterruptedException {
        initGroup1(2);
        Subscription subscription1 = group1Sub.awaitSubscription();
        subscription1.request(2);
        subscription.awaitRequestN(2);

        assertThat(group1Sub.takeOnNext(), is(0));

        source.onNext((Integer) null);
        source.onComplete();

        GroupedPublisher<Integer, Integer> group2 = groupSub.takeOnNext();
        assertThat(group2, notNullValue());
        assertThat(group2.key(), is(-1));
        toSource(group2).subscribe(group2Sub);
        Subscription subscription2 = group2Sub.awaitSubscription();
        subscription2.request(1);
        assertThat(group2Sub.takeOnNext(), is(nullValue()));

        groupSub.awaitOnComplete();
        group1Sub.awaitOnComplete();
        group2Sub.awaitOnComplete();
    }

    @Test
    void rootCancellation() throws InterruptedException {
        Subscription groupSubscription = initGroup1();
        Subscription subscription1 = group1Sub.awaitSubscription();
        subscription1.request(1);
        groupSubscription.cancel();
        assertThat(subscription.isCancelled(), is(false));
        subscription1.cancel();
        subscription.awaitCancelled();
    }

    @Test
    void groupCancellation() throws InterruptedException {
        Subscription groupSubscription = initGroup1();
        Subscription subscription1 = group1Sub.awaitSubscription();
        subscription1.cancel();
        assertThat(subscription.isCancelled(), is(false));
        groupSubscription.cancel();
        subscription.awaitCancelled();
    }

    @Test
    void rootInvalidRequestN() throws InterruptedException {
        Subscription groupSubscription = initGroup1();
        group1Sub.awaitSubscription();
        groupSubscription.request(-1);
        assertThat(subscription.requested(), is(lessThanOrEqualTo(0L)));
    }

    @Test
    void subscriberCancelThenRequestIsNoop() throws InterruptedException {
        initGroup1(3);
        Subscription subscription1 = group1Sub.awaitSubscription();
        subscription1.request(1);
        subscription.awaitRequestN(3);

        assertThat(group1Sub.takeOnNext(), is(0));

        source.onNext(1);
        subscription1.cancel();
        subscription1.request(1);
        assertThat(group1Sub.pollOnNext(10, MILLISECONDS), nullValue());
    }

    @Test
    void groupInvalidRequestN() throws InterruptedException {
        initGroup1();
        Subscription subscription1 = group1Sub.awaitSubscription();
        subscription1.request(-1);
        assertThat(subscription.requested(), is(lessThanOrEqualTo(0L)));
    }

    @ParameterizedTest
    @MethodSource("reentrySubscriberRequestCountIsCorrectParams")
    void reentrySubscriberOrderingCorrect(boolean firstReentry, boolean secondReentry) {
        toSource(fromSource(new ReentryPublisher(0, 4)).groupBy(integer -> (integer & 0x1) == 0 ? 0 : 1, 10)
                .beforeOnNext(n -> {
                    if (firstReentry) {
                        groupSub.awaitSubscription().request(1);
                    }
                })
        ).subscribe(groupSub);
        Subscription groupSubscription = groupSub.awaitSubscription();
        if (firstReentry) {
            groupSubscription.request(1);
        } else {
            groupSubscription.request(2);
        }
        GroupedPublisher<Integer, Integer> group1 = groupSub.takeOnNext();
        assertThat(group1, notNullValue());
        assertThat(group1.key(), is(0));
        toSource(group1.beforeOnNext(n -> {
            if (secondReentry) {
                group1Sub.awaitSubscription().request(1);
            }
        })).subscribe(group1Sub);
        Subscription subscription1 = group1Sub.awaitSubscription();
        if (secondReentry) {
            subscription1.request(1);
        } else {
            subscription1.request(4);
        }

        GroupedPublisher<Integer, Integer> group2 = groupSub.takeOnNext();
        assertThat(group2, notNullValue());
        assertThat(group2.key(), is(1));
        toSource(group2).subscribe(group2Sub);
        Subscription subscription2 = group2Sub.awaitSubscription();
        subscription2.request(4);

        assertThat(group1Sub.takeOnNext(2), contains(0, 2));
        assertThat(group2Sub.takeOnNext(2), contains(1, 3));
    }

    private static Stream<Arguments> reentrySubscriberRequestCountIsCorrectParams() {
        return Stream.of(
                Arguments.of(true, false),
                Arguments.of(false, true),
                Arguments.of(true, true)
        );
    }

    @Test
    void concurrentDrain() throws Exception {
        final int expectedSignals = 1000;
        initGroup1(2, expectedSignals);
        subscription.awaitRequestN(2);
        Subscription subscription1 = group1Sub.awaitSubscription();
        source.onNext(100);
        Subscription subscription2 = takeGroup2();
        ExecutorService executorService = new ThreadPoolExecutor(0, 2, 1, SECONDS, new SynchronousQueue<>());
        try {
            CyclicBarrier barrier = new CyclicBarrier(3);
            Future<Void> f1 = executorService.submit(() -> {
                try {
                    barrier.await();
                } catch (Exception e) {
                    throw new AssertionError(e);
                }
                for (int i = 0; i < expectedSignals; ++i) {
                    subscription1.request(1);
                }
                return null;
            });
            Future<Void> f2 = executorService.submit(() -> {
                try {
                    barrier.await();
                } catch (Exception e) {
                    throw new AssertionError(e);
                }

                for (int i = 0; i < expectedSignals; ++i) {
                    subscription2.request(1);
                }
                return null;
            });

            barrier.await();
            for (int i = 1; i < expectedSignals; ++i) {
                subscription.awaitRequestN(2 + i * 2);
                source.onNext(i % 100);
                source.onNext(100 + (i % 100));
            }
            source.onComplete();

            f1.get();
            f2.get();
            Integer[] group1Expected = new Integer[expectedSignals];
            Integer[] group2Expected = new Integer[expectedSignals];
            for (int i = 0; i < expectedSignals; ++i) {
                group1Expected[i] = i % 100;
                group2Expected[i] = 100 + (i % 100);
            }
            assertThat(subscription.requested(), is(2L + expectedSignals * 2));
            assertThat(group1Sub.pollAllOnNext(), contains(group1Expected));
            assertThat(group2Sub.pollAllOnNext(), contains(group2Expected));
            groupSub.awaitOnComplete();
            group1Sub.awaitOnComplete();
            group2Sub.awaitOnComplete();
        } finally {
            executorService.shutdown();
        }
    }

    @Test
    void concurrentSequentialDrain() throws Exception {
        final int expectedSignals = 1000;
        initGroup1(2, expectedSignals);
        subscription.awaitRequestN(2);
        Subscription subscription1 = group1Sub.awaitSubscription();
        source.onNext(100);
        Subscription subscription2 = takeGroup2();
        ExecutorService executorService = new ThreadPoolExecutor(0, 2, 1, SECONDS, new SynchronousQueue<>());
        try {
            CyclicBarrier barrier1 = new CyclicBarrier(2);
            CyclicBarrier barrier2 = new CyclicBarrier(2);
            Future<Void> f1 = executorService.submit(() -> {
                try {
                    barrier1.await();
                } catch (Exception e) {
                    throw new AssertionError(e);
                }
                for (int i = 0; i < expectedSignals; ++i) {
                    subscription1.request(1);
                }
                return null;
            });
            Future<Void> f2 = executorService.submit(() -> {
                try {
                    barrier2.await();
                } catch (Exception e) {
                    throw new AssertionError(e);
                }

                for (int i = 0; i < expectedSignals; ++i) {
                    subscription2.request(1);
                }
                return null;
            });

            barrier1.await();
            long awaitDemand = 2;
            for (int i = 1; i < expectedSignals; ++i) {
                subscription.awaitRequestN(++awaitDemand);
                source.onNext(i % 100);
            }
            barrier2.await();
            for (int i = 1; i < expectedSignals; ++i) {
                subscription.awaitRequestN(++awaitDemand);
                source.onNext(100 + (i % 100));
            }
            source.onComplete();

            f1.get();
            f2.get();
            Integer[] group1Expected = new Integer[expectedSignals];
            Integer[] group2Expected = new Integer[expectedSignals];
            for (int i = 0; i < expectedSignals; ++i) {
                group1Expected[i] = i % 100;
                group2Expected[i] = 100 + (i % 100);
            }
            assertThat(subscription.requested(), is(2L + expectedSignals * 2));
            assertThat(group1Sub.pollAllOnNext(), contains(group1Expected));
            assertThat(group2Sub.pollAllOnNext(), contains(group2Expected));
            groupSub.awaitOnComplete();
            group1Sub.awaitOnComplete();
            group2Sub.awaitOnComplete();
        } finally {
            executorService.shutdown();
        }
    }
}

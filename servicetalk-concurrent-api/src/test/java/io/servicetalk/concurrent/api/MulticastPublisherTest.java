/*
 * Copyright © 2021-2022 Apple Inc. and the ServiceTalk project authors
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

import io.servicetalk.concurrent.PublisherSource.Subscriber;
import io.servicetalk.concurrent.PublisherSource.Subscription;
import io.servicetalk.concurrent.internal.TerminalNotification;
import io.servicetalk.concurrent.test.internal.TestPublisherSubscriber;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.stubbing.Answer;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.BrokenBarrierException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;
import java.util.stream.Stream;

import static io.servicetalk.concurrent.api.Completable.never;
import static io.servicetalk.concurrent.api.Publisher.range;
import static io.servicetalk.concurrent.api.SourceAdapters.fromSource;
import static io.servicetalk.concurrent.api.SourceAdapters.toSource;
import static io.servicetalk.concurrent.internal.DeliberateException.DELIBERATE_EXCEPTION;
import static io.servicetalk.concurrent.internal.TerminalNotification.complete;
import static io.servicetalk.concurrent.internal.TerminalNotification.error;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.instanceOf;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class MulticastPublisherTest {
    private TestPublisher<Integer> source;
    private TestPublisherSubscriber<Integer> subscriber1;
    private TestPublisherSubscriber<Integer> subscriber2;
    private TestPublisherSubscriber<Integer> subscriber3;
    private TestSubscription subscription;

    @BeforeEach
    void setUp() {
        subscription = new TestSubscription();
        source = new TestPublisher.Builder<Integer>().disableAutoOnSubscribe().build(subscriber -> {
            subscriber.onSubscribe(subscription);
            return subscriber;
        });
        subscriber1 = new TestPublisherSubscriber<>();
        subscriber2 = new TestPublisherSubscriber<>();
        subscriber3 = new TestPublisherSubscriber<>();
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void singleSubscriber(boolean onError) {
        toSource(source.multicast(1)).subscribe(subscriber1);
        subscriber1.awaitSubscription();
        singleSourceTerminate(onError);
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void singleSubscriberData(boolean onError) throws InterruptedException {
        toSource(source.multicast(1)).subscribe(subscriber1);
        subscriber1.awaitSubscription().request(1);
        subscription.awaitRequestN(1);
        source.onNext(1);
        assertThat(subscriber1.takeOnNext(), is(1));
        singleSourceTerminate(onError);
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void singleSubscriberMultipleData(boolean onError) throws InterruptedException {
        toSource(source.multicast(1)).subscribe(subscriber1);
        Subscription localSubscription = subscriber1.awaitSubscription();
        localSubscription.request(1);
        subscription.awaitRequestN(1);
        source.onNext(1);
        assertThat(subscriber1.takeOnNext(), is(1));
        localSubscription.request(10);
        subscription.awaitRequestN(11);
        source.onNext(2, 3, 4, 5);
        assertThat(subscriber1.takeOnNext(4), contains(2, 3, 4, 5));
        singleSourceTerminate(onError);
    }

    private void singleSourceTerminate(boolean onError) {
        if (onError) {
            source.onError(DELIBERATE_EXCEPTION);
            assertThat(subscriber1.awaitOnError(), is(DELIBERATE_EXCEPTION));
        } else {
            source.onComplete();
            subscriber1.awaitOnComplete();
        }
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void singleSubscriberCancel(boolean cancelUpstream) throws InterruptedException {
        toSource(source.multicast(1, cancelUpstream)).subscribe(subscriber1);
        Subscription subscription1 = subscriber1.awaitSubscription();
        subscription1.cancel();
        subscription1.cancel(); // multiple cancels should be safe.
        if (cancelUpstream) {
            subscription.awaitCancelled();
        } else {
            assertThat(subscription.isCancelled(), is(false));
        }
    }

    @Test
    void singleSubscriberCancelStillDeliversData() throws InterruptedException {
        Publisher<Integer> publisher = source.multicast(1, false);
        toSource(publisher).subscribe(subscriber1);
        Subscription subscription1 = subscriber1.awaitSubscription();
        subscription1.request(1);
        subscription.awaitRequestN(1);
        subscription1.cancel();
        assertThat(subscription.isCancelled(), is(false));

        toSource(publisher).subscribe(subscriber2);
        Subscription subscription2 = subscriber2.awaitSubscription();

        source.onNext(1); // first subscriber already issued demand, we can deliver and signal must be queued.
        assertThat(subscriber2.pollOnNext(10, MILLISECONDS), nullValue());

        subscription2.request(1);
        assertThat(subscriber1.pollOnNext(10, MILLISECONDS), nullValue());
        assertThat(subscriber2.takeOnNext(), is(1));

        subscription2.cancel();
        assertThat(subscription.isCancelled(), is(false));
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void subscriberCancelThenRequestIsNoop(boolean cancelUpstream) throws InterruptedException {
        Publisher<Integer> publisher = source.multicast(2, cancelUpstream);
        toSource(publisher).subscribe(subscriber1);
        Subscription subscription1 = subscriber1.awaitSubscription();
        assertThat(subscription.requested(), is(0L));
        toSource(publisher).subscribe(subscriber2);
        Subscription subscription2 = subscriber2.awaitSubscription();

        subscription1.request(1);
        subscription2.request(1);
        subscription.awaitRequestN(1);

        source.onNext(1);
        assertThat(subscriber1.takeOnNext(), is(1));
        assertThat(subscriber2.takeOnNext(), is(1));

        subscription1.cancel();
        subscription1.request(1);
        assertThat(subscription.requested(), is(1L));

        subscription2.request(1);
        subscription.awaitRequestN(2);
        source.onNext(2);
        assertThat(subscriber1.pollOnNext(10, MILLISECONDS), nullValue());
        assertThat(subscriber2.takeOnNext(), is(2));

        subscription2.cancel();
        if (cancelUpstream) {
            subscription.awaitCancelled();
        } else {
            assertThat(subscription.isCancelled(), is(false));
        }
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void twoSubscribersNoData(boolean onError) {
        Publisher<Integer> publisher = source.multicast(2);
        toSource(publisher).subscribe(subscriber1);
        subscriber1.awaitSubscription();
        assertThat(subscription.requested(), is(0L));
        toSource(publisher).subscribe(subscriber2);
        subscriber2.awaitSubscription();
        twoSubscribersTerminate(onError);
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void twoSubscribersData(boolean onError) throws InterruptedException {
        Publisher<Integer> publisher = source.multicast(2);
        toSource(publisher).subscribe(subscriber1);
        subscriber1.awaitSubscription().request(1);
        assertThat(subscription.requested(), is(0L));
        toSource(publisher).subscribe(subscriber2);
        subscriber2.awaitSubscription().request(1);
        subscription.awaitRequestN(1);
        source.onNext(1);
        assertThat(subscriber1.takeOnNext(), is(1));
        assertThat(subscriber2.takeOnNext(), is(1));
        twoSubscribersTerminate(onError);
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void twoSubscribersMultipleData(boolean onError) throws InterruptedException {
        Publisher<Integer> publisher = source.multicast(2);
        toSource(publisher).subscribe(subscriber1);
        Subscription localSubscription1 = subscriber1.awaitSubscription();
        localSubscription1.request(1);
        assertThat(subscription.requested(), is(0L));
        toSource(publisher).subscribe(subscriber2);
        Subscription localSubscription2 = subscriber2.awaitSubscription();
        localSubscription2.request(1);
        subscription.awaitRequestN(1);
        source.onNext(1);
        assertThat(subscriber1.takeOnNext(), is(1));
        assertThat(subscriber2.takeOnNext(), is(1));
        localSubscription1.request(10);
        localSubscription2.request(5);
        subscription.awaitRequestN(6);
        source.onNext(2, 3, 4, 5);
        assertThat(subscriber1.takeOnNext(4), contains(2, 3, 4, 5));
        assertThat(subscriber2.takeOnNext(4), contains(2, 3, 4, 5));
        twoSubscribersTerminate(onError);
    }

    private void twoSubscribersTerminate(boolean onError) {
        if (onError) {
            source.onError(DELIBERATE_EXCEPTION);
            assertThat(subscriber1.awaitOnError(), is(DELIBERATE_EXCEPTION));
            assertThat(subscriber2.awaitOnError(), is(DELIBERATE_EXCEPTION));
        } else {
            source.onComplete();
            subscriber1.awaitOnComplete();
            subscriber2.awaitOnComplete();
        }
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void twoSubscribersAfterTerminalData(boolean onError) throws InterruptedException {
        Publisher<Integer> publisher = source.multicast(1, 10, t -> never());
        toSource(publisher).subscribe(subscriber1);
        subscriber1.awaitSubscription().request(1);
        subscription.awaitRequestN(1);
        source.onNext(1);
        assertThat(subscriber1.takeOnNext(), is(1));
        if (onError) {
            source.onError(DELIBERATE_EXCEPTION);
            assertThat(subscriber1.awaitOnError(), is(DELIBERATE_EXCEPTION));
        } else {
            source.onComplete();
            subscriber1.awaitOnComplete();
        }
        toSource(publisher).subscribe(subscriber2);
        subscriber2.awaitSubscription().request(1);
        if (onError) {
            assertThat(subscriber2.awaitOnError(), is(DELIBERATE_EXCEPTION));
        } else {
            subscriber2.awaitOnComplete();
        }
    }

    @ParameterizedTest
    @MethodSource("twoSubscribersInvalidRequestNParams")
    void twoSubscribersInvalidRequestN(long invalidN, boolean firstSubscription) {
        Publisher<Integer> publisher = Publisher.from(1).multicast(2);
        toSource(publisher).subscribe(subscriber1);
        Subscription subscription1 = subscriber1.awaitSubscription();
        toSource(publisher).subscribe(subscriber2);
        Subscription subscription2 = subscriber2.awaitSubscription();
        if (firstSubscription) {
            subscription1.request(invalidN);
        } else {
            subscription2.request(invalidN);
        }
        assertThat(subscriber1.awaitOnError(), instanceOf(IllegalArgumentException.class));
        assertThat(subscriber2.awaitOnError(), instanceOf(IllegalArgumentException.class));
    }

    private static Stream<Arguments> twoSubscribersInvalidRequestNParams() {
        return Stream.of(
                Arguments.of(Long.MIN_VALUE, true),
                Arguments.of(-1, true),
                Arguments.of(0, true),
                Arguments.of(Long.MIN_VALUE, false),
                Arguments.of(-1, false),
                Arguments.of(0, false));
    }

    @ParameterizedTest
    @MethodSource("trueFalseStream")
    void twoSubscribersCancel(boolean firstSubscription, boolean cancelUpstream) throws InterruptedException {
        Publisher<Integer> publisher = source.multicast(2, cancelUpstream);
        toSource(publisher).subscribe(subscriber1);
        Subscription subscription1 = subscriber1.awaitSubscription();
        toSource(publisher).subscribe(subscriber2);
        Subscription subscription2 = subscriber2.awaitSubscription();
        if (firstSubscription) {
            subscription1.cancel();
            subscription2.request(1);
        } else {
            subscription2.cancel();
            subscription1.request(1);
        }
        subscription.awaitRequestN(1);

        // Add another subscriber after one of the subscribers has cancelled and we have pre-existing demand.
        toSource(publisher).subscribe(subscriber3);
        Subscription subscription3 = subscriber3.awaitSubscription();
        source.onNext(1);
        source.onComplete();

        // subscriber3 has no demand yet, it shouldn't have any data delivered.
        assertThat(subscriber3.pollOnNext(10, MILLISECONDS), is(nullValue()));

        if (firstSubscription) {
            assertThat(subscriber2.takeOnNext(), is(1));
            subscriber2.awaitOnComplete();
            assertThat(subscriber1.pollOnNext(10, MILLISECONDS), is(nullValue()));
            assertThat(subscriber1.pollTerminal(10, MILLISECONDS), is(nullValue()));
        } else {
            assertThat(subscriber1.takeOnNext(), is(1));
            subscriber1.awaitOnComplete();
            assertThat(subscriber2.pollOnNext(10, MILLISECONDS), is(nullValue()));
            assertThat(subscriber2.pollTerminal(10, MILLISECONDS), is(nullValue()));
        }

        subscription3.request(1);
        assertThat(subscriber3.takeOnNext(), is(1));
        subscriber3.awaitOnComplete();
    }

    private static Stream<Arguments> trueFalseStream() {
        return Stream.of(
                Arguments.of(true, true),
                Arguments.of(true, false),
                Arguments.of(false, true),
                Arguments.of(false, false)
        );
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void threeSubscribersOneLateNoQueueData(boolean onError) throws InterruptedException {
        Publisher<Integer> publisher = source.multicast(2);
        toSource(publisher).subscribe(subscriber1);
        Subscription localSubscription1 = subscriber1.awaitSubscription();
        localSubscription1.request(1);
        assertThat(subscription.requested(), is(0L));
        toSource(publisher).subscribe(subscriber2);
        Subscription localSubscription2 = subscriber2.awaitSubscription();
        localSubscription2.request(1);
        subscription.awaitRequestN(1);
        source.onNext(1);
        assertThat(subscriber1.takeOnNext(), is(1));
        assertThat(subscriber2.takeOnNext(), is(1));
        toSource(publisher).subscribe(subscriber3);
        Subscription localSubscription3 = subscriber3.awaitSubscription();
        localSubscription1.request(10);
        localSubscription2.request(5);
        localSubscription3.request(4);
        subscription.awaitRequestN(5);
        source.onNext(2, 3, 4, 5);
        assertThat(subscriber1.takeOnNext(4), contains(2, 3, 4, 5));
        assertThat(subscriber2.takeOnNext(4), contains(2, 3, 4, 5));
        assertThat(subscriber3.takeOnNext(4), contains(2, 3, 4, 5));
        threeSubscribersTerminate(onError);
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void threeSubscribersOneLateQueueData(boolean onError) throws InterruptedException {
        Publisher<Integer> publisher = source.multicast(2);
        toSource(publisher).subscribe(subscriber1);
        toSource(publisher).subscribe(subscriber2);
        Subscription localSubscription1 = subscriber1.awaitSubscription();
        Subscription localSubscription2 = subscriber2.awaitSubscription();
        localSubscription1.request(5);
        localSubscription2.request(5);
        subscription.awaitRequestN(5);
        source.onNext(1);
        assertThat(subscriber1.takeOnNext(), is(1));
        assertThat(subscriber2.takeOnNext(), is(1));
        toSource(publisher).subscribe(subscriber3);
        Subscription localSubscription3 = subscriber3.awaitSubscription();
        source.onNext(2, 3, 4, 5);
        assertThat(subscriber1.takeOnNext(4), contains(2, 3, 4, 5));
        assertThat(subscriber2.takeOnNext(4), contains(2, 3, 4, 5));
        assertThat(subscriber3.pollOnNext(10, MILLISECONDS), is(nullValue()));
        localSubscription3.request(4);
        assertThat(subscriber3.takeOnNext(4), contains(2, 3, 4, 5));
        threeSubscribersTerminate(onError);
    }

    @ParameterizedTest
    @MethodSource("trueFalseStream")
    void threeSubscribersOneLateAfterCancel(boolean cancelMax, boolean cancelUpstream) throws InterruptedException {
        Publisher<Integer> publisher = source.multicast(2, cancelUpstream);
        toSource(publisher).subscribe(subscriber1);
        Subscription localSubscription1 = subscriber1.awaitSubscription();
        localSubscription1.request(5);
        assertThat(subscription.requested(), is(0L));
        toSource(publisher).subscribe(subscriber2);
        Subscription localSubscription2 = subscriber2.awaitSubscription();
        localSubscription2.request(10);
        subscription.awaitRequestN(5);
        source.onNext(1);
        assertThat(subscriber1.takeOnNext(), is(1));
        assertThat(subscriber2.takeOnNext(), is(1));

        if (cancelMax) {
            localSubscription2.cancel();
        } else {
            localSubscription1.cancel();
        }

        toSource(publisher).subscribe(subscriber3);
        Subscription localSubscription3 = subscriber3.awaitSubscription();
        localSubscription3.request(4);
        assertThat(subscription.requested(), is(5L));
        source.onNext(2, 3, 4, 5);
        source.onComplete();
        if (cancelMax) {
            assertThat(subscriber1.takeOnNext(4), contains(2, 3, 4, 5));
            subscriber1.awaitOnComplete();
        } else {
            assertThat(subscriber2.takeOnNext(4), contains(2, 3, 4, 5));
            subscriber2.awaitOnComplete();
        }
        assertThat(subscriber3.takeOnNext(4), contains(2, 3, 4, 5));
        subscriber3.awaitOnComplete();
    }

    private void threeSubscribersTerminate(boolean onError) {
        if (onError) {
            source.onError(DELIBERATE_EXCEPTION);
            assertThat(subscriber1.awaitOnError(), is(DELIBERATE_EXCEPTION));
            assertThat(subscriber2.awaitOnError(), is(DELIBERATE_EXCEPTION));
            assertThat(subscriber3.awaitOnError(), is(DELIBERATE_EXCEPTION));
        } else {
            source.onComplete();
            subscriber1.awaitOnComplete();
            subscriber2.awaitOnComplete();
            subscriber3.awaitOnComplete();
        }
    }

    @Test
    void inlineRequestFromOnSubscribeToMultipleSubscribers() {
        Publisher<Integer> publisher = Publisher.from(1).multicast(2);
        @SuppressWarnings("unchecked")
        Subscriber<Integer> sub1 = mock(Subscriber.class);
        @SuppressWarnings("unchecked")
        Subscriber<Integer> sub2 = mock(Subscriber.class);
        doAnswer((Answer<Void>) invocation -> {
            Subscription s = invocation.getArgument(0);
            s.request(Long.MAX_VALUE);
            return null;
        }).when(sub1).onSubscribe(any());
        doAnswer((Answer<Void>) invocation -> {
            Subscription s = invocation.getArgument(0);
            s.request(Long.MAX_VALUE);
            return null;
        }).when(sub2).onSubscribe(any());
        toSource(publisher).subscribe(sub1);
        toSource(publisher).subscribe(sub2);

        verify(sub1).onNext(eq(1));
        verify(sub2).onNext(eq(1));
        verify(sub1).onComplete();
        verify(sub2).onComplete();
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void onErrorFromSubscriptionRequestToMultipleSubscribers(boolean onError) {
        Publisher<Integer> multicast = new TerminateFromOnSubscribePublisher(onError ?
                error(DELIBERATE_EXCEPTION) : complete()).multicast(2);
        toSource(multicast).subscribe(subscriber1);
        toSource(multicast).subscribe(subscriber2);
        subscriber1.awaitSubscription().request(1);
        subscriber2.awaitSubscription().request(1);
        if (onError) {
            assertThat(subscriber1.awaitOnError(), is(DELIBERATE_EXCEPTION));
            assertThat(subscriber2.awaitOnError(), is(DELIBERATE_EXCEPTION));
        } else {
            subscriber1.awaitOnComplete();
            subscriber2.awaitOnComplete();
        }
    }

    @ParameterizedTest
    @MethodSource("reentrySubscriberRequestCountIsCorrectParams")
    void reentrySubscriberOrderingCorrect(boolean firstReentry, boolean secondReentry) {
        Publisher<Integer> multicast = fromSource(new ReentryPublisher(0, 4)).multicast(2);
        toSource(multicast.beforeOnNext(n -> {
            if (firstReentry) {
                subscriber1.awaitSubscription().request(1);
            }
        })).subscribe(subscriber1);
        toSource(multicast.beforeOnNext(n -> {
            if (secondReentry) {
                subscriber2.awaitSubscription().request(1);
            }
        })).subscribe(subscriber2);

        Subscription subscription1 = subscriber1.awaitSubscription();
        Subscription subscription2 = subscriber2.awaitSubscription();

        if (firstReentry && secondReentry) {
            subscription1.request(1);
            subscription2.request(1);
        } else if (firstReentry) {
            subscription1.request(1);
            subscription2.request(4);
        } else {
            subscription1.request(4);
            subscription2.request(1);
        }

        assertThat(subscriber1.takeOnNext(4), contains(0, 1, 2, 3));
        assertThat(subscriber2.takeOnNext(4), contains(0, 1, 2, 3));
    }

    @ParameterizedTest
    @MethodSource("reentrySubscriberRequestCountIsCorrectParams")
    void reentrySubscriberRequestCountIsCorrect(boolean firstReentry, boolean secondReentry) {
        Publisher<Integer> multicast = source.multicast(2);
        toSource(multicast.whenOnNext(n -> {
            if (firstReentry) {
                subscriber1.awaitSubscription().request(1);
            }
        })).subscribe(subscriber1);
        toSource(multicast.whenOnNext(n -> {
            if (secondReentry) {
                subscriber2.awaitSubscription().request(1);
            }
        })).subscribe(subscriber2);

        Subscription subscription1 = subscriber1.awaitSubscription();
        Subscription subscription2 = subscriber2.awaitSubscription();

        if (firstReentry && secondReentry) {
            subscription1.request(2);
            subscription2.request(2);
        } else if (firstReentry) {
            subscription1.request(2);
            subscription2.request(3);
        } else {
            subscription1.request(3);
            subscription2.request(2);
        }
        assertThat(subscription.requested(), is(2L));
        source.onNext(1, 2);
        assertThat(subscription.requested(), is((long) (firstReentry && secondReentry ? 4 : 3)));
        assertThat(subscriber1.takeOnNext(2), contains(1, 2));
        assertThat(subscriber2.takeOnNext(2), contains(1, 2));
    }

    private static Stream<Arguments> reentrySubscriberRequestCountIsCorrectParams() {
        return Stream.of(
                Arguments.of(true, false),
                Arguments.of(false, true),
                Arguments.of(true, true)
        );
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void reentryAndMultiQueueSupportsNull(boolean requestReentry) throws InterruptedException {
        Publisher<Integer> multicast = source.multicast(1);
        AtomicBoolean onNextCalled = new AtomicBoolean();
        toSource(multicast).subscribe(subscriber1);
        subscriber1.awaitSubscription().request(3);
        subscription.awaitRequestN(3);

        toSource(multicast.whenOnNext(n -> {
            if (requestReentry && onNextCalled.compareAndSet(false, true)) {
                subscriber2.awaitSubscription().request(2);
            }
        })).subscribe(subscriber2);
        subscriber2.awaitSubscription().request(1);

        source.onNext(1, null, 3);
        // Deliver an item, which will trigger a re-entry null delivery.
        assertThat(subscriber1.takeOnNext(3), contains(1, null, 3));
        assertThat(subscriber2.takeOnNext(), is(1));

        // We now test that the queue can handle null items.
        if (!requestReentry) {
            subscriber2.awaitSubscription().request(2);
        }
        assertThat(subscriber2.takeOnNext(2), contains(null, 3));
    }

    @Test
    void reentryAsyncData() throws Exception {
        Executor executor = Executors.newCachedThreadExecutor();
        try {
            Publisher<Integer> multicast = Publisher.from(1, 2, 3).publishOn(executor).multicast(2);
            AtomicBoolean onNextCalled = new AtomicBoolean();
            toSource(multicast.afterOnNext(n -> {
                if (onNextCalled.compareAndSet(false, true)) {
                    subscriber1.awaitSubscription().request(2);
                }
            })).subscribe(subscriber1);

            toSource(multicast).subscribe(subscriber2);
            subscriber1.awaitSubscription().request(1);
            subscriber2.awaitSubscription().request(3);

            assertThat(subscriber1.takeOnNext(3), contains(1, 2, 3));
            assertThat(subscriber2.takeOnNext(3), contains(1, 2, 3));
            subscriber1.awaitOnComplete();
            subscriber2.awaitOnComplete();
        } finally {
            executor.closeAsync().toFuture().get();
        }
    }

    @Test
    void replenishRequestNInMaxQueueIncrementsLongMax() {
        Publisher<Integer> multicast = source.multicast(2, 3);
        toSource(multicast).subscribe(subscriber1);
        toSource(multicast).subscribe(subscriber2);

        subscriber1.awaitSubscription().request(Long.MAX_VALUE);
        subscriber2.awaitSubscription().request(Long.MAX_VALUE);
        assertThat(subscription.requested(), is(3L));
        source.onNext(1, 2, 3);
        assertThat(subscription.requested(), is(6L));
        assertThat(subscriber1.takeOnNext(3), contains(1, 2, 3));
        assertThat(subscriber2.takeOnNext(3), contains(1, 2, 3));
    }

    @Test
    void replenishRequestNInMaxQueueIncrementsRange() throws Exception {
        Publisher<Integer> original = range(1, 10);
        ArrayList<Integer> items = original.collect((Supplier<ArrayList<Integer>>) ArrayList::new, (list, integer) -> {
            list.add(integer);
            return list;
        }).toFuture().get();

        Publisher<Integer> multi = original.multicast(2, 5);
        List<Integer> first = new ArrayList<>();
        List<Integer> second = new ArrayList<>();
        multi.forEach(first::add);
        multi.forEach(second::add);

        assertThat(first, contains(items.toArray()));
        assertThat(second, equalTo(first));
    }

    @Test
    void concurrentRequestN() throws InterruptedException {
        final int expectedSubscribers = 50;
        Publisher<Integer> multicast = source.multicast(expectedSubscribers);
        @SuppressWarnings("unchecked")
        TestPublisherSubscriber<Integer>[] subscribers = (TestPublisherSubscriber<Integer>[])
                new TestPublisherSubscriber[expectedSubscribers];

        final int expectedSubscribersMinus1 = expectedSubscribers - 1;
        for (int i = 0; i < expectedSubscribersMinus1; ++i) {
            subscribers[i] = new TestPublisherSubscriber<>();
            toSource(multicast).subscribe(subscribers[i]);
        }
        subscribers[expectedSubscribersMinus1] = new TestPublisherSubscriber<>();
        toSource(multicast).subscribe(subscribers[expectedSubscribersMinus1]);
        for (int i = 0; i < expectedSubscribersMinus1; ++i) {
            subscribers[i].awaitSubscription();
        }

        ExecutorService executorService = new ThreadPoolExecutor(0, expectedSubscribers, 1, SECONDS,
                new SynchronousQueue<>());
        try {
            CyclicBarrier barrier = new CyclicBarrier(expectedSubscribers);
            CountDownLatch doneLatch = new CountDownLatch(expectedSubscribers);
            AtomicReference<Throwable> throwableRef = new AtomicReference<>();
            for (int i = 1; i <= expectedSubscribers; ++i) {
                executorService.execute(requestIRunnable(subscribers, i, i, barrier, throwableRef, doneLatch));
            }

            doneLatch.await();
            assertThat(throwableRef.get(), is(nullValue()));
            assertThat(subscription.requested(), is(1L));
            assertThat(subscription.isCancelled(), is(false));
        } finally {
            executorService.shutdown();
        }
    }

    @Test
    void concurrentRequestNAndOnNext() throws BrokenBarrierException, InterruptedException {
        final int expectedSubscribers = 400;
        Publisher<Integer> multicast = source.multicast(expectedSubscribers);
        @SuppressWarnings("unchecked")
        TestPublisherSubscriber<Integer>[] subscribers = (TestPublisherSubscriber<Integer>[])
                new TestPublisherSubscriber[expectedSubscribers];

        final int expectedSubscribersMinus1 = expectedSubscribers - 1;
        for (int i = 0; i < expectedSubscribersMinus1; ++i) {
            subscribers[i] = new TestPublisherSubscriber<>();
            toSource(multicast).subscribe(subscribers[i]);
        }
        subscribers[expectedSubscribersMinus1] = new TestPublisherSubscriber<>();
        toSource(multicast).subscribe(subscribers[expectedSubscribersMinus1]);
        for (int i = 0; i < expectedSubscribersMinus1; ++i) {
            subscribers[i].awaitSubscription();
        }

        ExecutorService executorService = new ThreadPoolExecutor(0, expectedSubscribers, 1, SECONDS,
                new SynchronousQueue<>());
        try {
            CyclicBarrier barrier = new CyclicBarrier(expectedSubscribers + 1);
            CountDownLatch doneLatch = new CountDownLatch(expectedSubscribers);
            AtomicReference<Throwable> throwableRef = new AtomicReference<>();
            for (int i = 1; i <= expectedSubscribers; ++i) {
                executorService.execute(
                        requestIRunnable(subscribers, i, expectedSubscribers, barrier, throwableRef, doneLatch));
            }

            barrier.await();

            for (int i = 0; i < expectedSubscribers; ++i) {
                subscription.awaitRequestN(i + 1);
                source.onNext(i);
            }

            doneLatch.await();
            assertThat(throwableRef.get(), is(nullValue()));
            List<Integer> expectedItems = new ArrayList<>(expectedSubscribers);
            for (int x = 0; x < expectedSubscribers; ++x) {
                expectedItems.add(x);
            }
            for (int i = 0; i < expectedSubscribers; ++i) {
                final Integer[] expectedSubset = expectedItems.subList(0, i).toArray(new Integer[0]);
                List<Integer> actual = subscribers[i].takeOnNext(i);
                if (expectedSubset.length == 0) {
                    assertThat(actual.isEmpty(), is(true));
                } else {
                    assertThat(actual, contains(expectedSubset));
                }
            }
            assertThat(subscription.requested(), is((long) expectedSubscribers));
            assertThat(subscription.isCancelled(), is(false));
        } finally {
            executorService.shutdown();
        }
    }

    @ParameterizedTest
    @MethodSource("trueFalseStream")
    void threeConcurrentLateSubscriber(boolean cancelEarlySub, boolean cancelUpstream) throws Exception {
        final int expectedSignals = 1000;
        Publisher<Integer> publisher = source.multicast(2, expectedSignals, cancelUpstream);
        toSource(publisher).subscribe(subscriber1);
        Subscription subscription1 = subscriber1.awaitSubscription();
        subscription1.request(expectedSignals);
        toSource(publisher).subscribe(subscriber2);
        Subscription subscription2 = subscriber2.awaitSubscription();
        subscription2.request(expectedSignals);
        subscription.awaitRequestN(expectedSignals);
        if (cancelEarlySub) {
            subscription1.cancel();
        }

        // subscriber3 is our late subscriber.
        toSource(publisher).subscribe(subscriber3);
        Subscription subscription3 = subscriber3.awaitSubscription();

        ExecutorService executorService = new ThreadPoolExecutor(0, 1, 1, SECONDS, new SynchronousQueue<>());
        try {
            CyclicBarrier barrier = new CyclicBarrier(2);
            Future<Void> f = executorService.submit(() -> {
                try {
                    barrier.await();
                } catch (Exception e) {
                    throw new AssertionError(e);
                }

                for (int i = 0; i < expectedSignals; ++i) {
                    subscription3.request(1);
                }

                return null;
            });

            barrier.await();
            for (int i = 0; i < expectedSignals; ++i) {
                source.onNext(i);
            }
            source.onComplete();

            f.get();
            Integer[] expectedItems = new Integer[expectedSignals];
            for (int i = 0; i < expectedSignals; ++i) {
                expectedItems[i] = i;
            }

            assertThat(subscription.requested(), is((long) expectedSignals));
            if (!cancelEarlySub) {
                assertThat(subscriber1.pollAllOnNext(), contains(expectedItems));
                subscriber1.awaitOnComplete();
            }
            assertThat(subscriber2.pollAllOnNext(), contains(expectedItems));
            subscriber2.awaitOnComplete();
            assertThat(subscriber3.pollAllOnNext(), contains(expectedItems));
            subscriber3.awaitOnComplete();
        } finally {
            executorService.shutdown();
        }
    }

    @Test
    void twoConcurrentSubscriptions() throws Exception {
        final int expectedSignals = 1000;
        Publisher<Integer> publisher = source.multicast(2, expectedSignals);
        toSource(publisher).subscribe(subscriber1);
        Subscription subscription1 = subscriber1.awaitSubscription();
        toSource(publisher).subscribe(subscriber2);
        Subscription subscription2 = subscriber2.awaitSubscription();

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
            for (int i = 0; i < expectedSignals; ++i) {
                subscription.awaitRequestN(i + 1);
                source.onNext(i);
            }
            source.onComplete();

            f1.get();
            f2.get();
            Integer[] expectedItems = new Integer[expectedSignals];
            for (int i = 0; i < expectedSignals; ++i) {
                expectedItems[i] = i;
            }

            assertThat(subscription.requested(), is((long) expectedSignals));
            assertThat(subscriber1.pollAllOnNext(), contains(expectedItems));
            assertThat(subscriber2.pollAllOnNext(), contains(expectedItems));
            subscriber1.awaitOnComplete();
            subscriber2.awaitOnComplete();
        } finally {
            executorService.shutdown();
        }
    }

    private static Runnable requestIRunnable(TestPublisherSubscriber<Integer>[] subscribers,
                                             int finalI,
                                             int requestAmount,
                                             CyclicBarrier barrier,
                                             AtomicReference<Throwable> throwableRef,
                                             CountDownLatch doneLatch) {
        return () -> {
            try {
                TestPublisherSubscriber<Integer> subscriber = subscribers[finalI - 1];
                barrier.await();
                subscriber.awaitSubscription().request(requestAmount);
            } catch (Throwable cause) {
                throwableRef.set(cause);
            } finally {
                doneLatch.countDown();
            }
        };
    }

    private static class TerminateFromOnSubscribePublisher extends Publisher<Integer> {
        private final TerminalNotification terminalNotification;

        TerminateFromOnSubscribePublisher(TerminalNotification terminalNotification) {
            this.terminalNotification = terminalNotification;
        }

        @Override
        protected void handleSubscribe(Subscriber<? super Integer> subscriber) {
            subscriber.onSubscribe(new Subscription() {
                @Override
                public void request(long n) {
                    terminalNotification.terminate(subscriber);
                }

                @Override
                public void cancel() {
                    // noop
                }
            });
        }
    }
}

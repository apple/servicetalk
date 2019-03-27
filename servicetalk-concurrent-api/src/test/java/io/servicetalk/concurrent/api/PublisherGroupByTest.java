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

import io.servicetalk.concurrent.PublisherSource.Subscriber;
import io.servicetalk.concurrent.PublisherSource.Subscription;
import io.servicetalk.concurrent.internal.DeliberateException;
import io.servicetalk.concurrent.internal.QueueFullException;
import io.servicetalk.concurrent.internal.ServiceTalkTestTimeout;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.Timeout;
import org.mockito.ArgumentCaptor;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.LockSupport;
import java.util.function.Function;
import java.util.function.Supplier;

import static io.servicetalk.concurrent.api.SourceAdapters.toSource;
import static io.servicetalk.concurrent.internal.DeliberateException.DELIBERATE_EXCEPTION;
import static io.servicetalk.concurrent.internal.TerminalNotification.complete;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.instanceOf;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;
import static org.hamcrest.Matchers.sameInstance;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentCaptor.forClass;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

public class PublisherGroupByTest {
    @Rule
    public final Timeout timeout = new ServiceTalkTestTimeout();

    private TestPublisher<Integer> source;
    private TestPublisherSubscriber<Boolean> subscriber;
    private TestSubscription subscription = new TestSubscription();
    private List<TestPublisherSubscriber<Integer>> groupSubs = new ArrayList<>();

    @Before
    public void setUp() {
        source = new TestPublisher<>();
        subscriber = new TestPublisherSubscriber<>();
    }

    @Test
    public void testGroupOnNextAndCompleteWithGroupQueue() {
        toSource(subscribeToAllGroups(10)).subscribe(subscriber);
        testGroupOnNextAndComplete(groupSubs);
    }

    @Test
    public void testGroupOnNextAndErrorWithGroupQueue() {
        toSource(subscribeToAllGroups(10)).subscribe(subscriber);
        testGroupOnNextAndError(groupSubs);
    }

    @Test
    public void testGroupOnNextAndCompleteNoQueue() {
        toSource(subscribeToAllGroups(10, s -> {
            s.request(1);
            return s;
        })).subscribe(subscriber);
        testGroupOnNextAndComplete(groupSubs);
    }

    @Test
    public void testGroupOnNextAndErrorNoQueue() {
        toSource(subscribeToAllGroups(10, s -> {
            s.request(1);
            return s;
        })).subscribe(subscriber);
        testGroupOnNextAndError(groupSubs);
    }

    @Test
    public void testGroupOnNextThrowsNoQueue() {
        testGroupOnNextThrows(1);
    }

    @Test
    public void testGroupOnNextThrowsWithQueue() {
        testGroupOnNextThrows(0);
    }

    @Test
    public void testGroupSubscriberCancelNoQueue() {
        testGroupSubscriberCancel(1);
    }

    @Test
    public void testGroupSubscriberCancelWithQueue() {
        testGroupSubscriberCancel(0);
    }

    @Test
    public void testHighDemandWithQueue() {
        toSource(subscribeToAllGroups(10)).subscribe(subscriber);
        source.onNext(1);
        assertTrue(subscriber.subscriptionReceived());
        assertThat(subscriber.takeItems(), hasSize(0));
        assertThat(subscriber.takeTerminal(), nullValue());
        subscriber.request(1);
        assertThat("Unexpected group subscribers.", groupSubs, hasSize(1));
        assertTrue(groupSubs.get(0).subscriptionReceived());
        assertThat(groupSubs.get(0).takeItems(), hasSize(0));
        assertThat(groupSubs.get(0).takeTerminal(), nullValue());
        groupSubs.get(0).request(1);
        assertThat(groupSubs.get(0).takeItems(), contains(1));
        assertThat(subscriber.takeItems(), contains(Boolean.FALSE));
        source.onNext(2);
        assertTrue(subscriber.subscriptionReceived());
        assertThat(subscriber.takeTerminal(), nullValue());
        subscriber.request(Long.MAX_VALUE);
        assertThat("Unexpected group subscribers.", groupSubs, hasSize(2));
        assertThat(subscriber.takeItems(), contains(Boolean.TRUE));
        assertTrue(groupSubs.get(1).subscriptionReceived());
        assertThat(groupSubs.get(1).takeItems(), hasSize(0));
        assertThat(groupSubs.get(1).takeTerminal(), nullValue());
        groupSubs.get(1).request(1);
        assertThat(groupSubs.get(1).takeItems(), contains(2));
    }

    @Test
    public void testIndividualGroupSubscriptionRequestQueuesGroups() {
        toSource(subscribeToAllGroups(10)).subscribe(subscriber);
        source.onNext(1);
        assertTrue(subscriber.subscriptionReceived());
        assertThat(subscriber.takeItems(), hasSize(0));
        assertThat(subscriber.takeTerminal(), nullValue());
        subscriber.request(1);
        assertThat("Unexpected group subscribers.", groupSubs, hasSize(1));
        assertTrue(groupSubs.get(0).subscriptionReceived());
        assertThat(groupSubs.get(0).takeItems(), hasSize(0));
        assertThat(groupSubs.get(0).takeTerminal(), nullValue());
        groupSubs.get(0).request(Long.MAX_VALUE);
        assertThat(groupSubs.get(0).takeItems(), contains(1));
        assertThat(subscriber.takeItems(), contains(Boolean.FALSE));
        source.onNext(2);
        assertTrue(subscriber.subscriptionReceived());
        assertThat(subscriber.takeTerminal(), nullValue());
        assertThat("Unexpected group subscribers.", groupSubs, hasSize(1));
        source.onNext(3);
        assertThat(groupSubs.get(0).takeItems(), contains(3));
        subscriber.request(1);
        assertThat(subscriber.takeItems(), contains(Boolean.TRUE));
        assertThat("Unexpected group subscribers.", groupSubs, hasSize(2));
        assertTrue(groupSubs.get(1).subscriptionReceived());
        assertThat(groupSubs.get(1).takeItems(), hasSize(0));
        assertThat(groupSubs.get(1).takeTerminal(), nullValue());
        groupSubs.get(1).request(1);
        assertThat(groupSubs.get(1).takeItems(), contains(2));
    }

    @Test
    public void groupEnqueueOnComplete() {
        toSource(subscribeToAllGroups(10)).subscribe(subscriber);
        source.onNext(1);
        assertTrue(subscriber.subscriptionReceived());
        assertThat(subscriber.takeItems(), hasSize(0));
        assertThat(subscriber.takeTerminal(), nullValue());
        subscriber.request(1);
        assertThat("Unexpected group subscribers.", groupSubs, hasSize(1));
        assertTrue(groupSubs.get(0).subscriptionReceived());
        assertThat(groupSubs.get(0).takeItems(), hasSize(0));
        assertThat(groupSubs.get(0).takeTerminal(), nullValue());
        groupSubs.get(0).request(1);
        assertThat(groupSubs.get(0).takeItems(), contains(1));
        assertThat(subscriber.takeItems(), contains(Boolean.FALSE));
        source.onNext(2);
        source.onNext(3);
        assertTrue(subscriber.subscriptionReceived());
        assertThat(subscriber.takeTerminal(), nullValue());
        assertThat("Unexpected group subscribers.", groupSubs, hasSize(1));
        source.onComplete();
        assertTrue(groupSubs.get(0).subscriptionReceived());
        assertThat(groupSubs.get(0).takeTerminal(), nullValue());
        groupSubs.get(0).request(1);
        assertThat(groupSubs.get(0).takeItems(), contains(3));
        subscriber.request(1);
        assertThat(subscriber.takeItems(), contains(Boolean.TRUE));
        assertThat(subscriber.takeTerminal(), is(complete()));
        assertThat("Unexpected group subscribers.", groupSubs, hasSize(2));
        assertTrue(groupSubs.get(1).subscriptionReceived());
        assertThat(groupSubs.get(1).takeItems(), hasSize(0));
        assertThat(groupSubs.get(1).takeTerminal(), nullValue());
        groupSubs.get(1).request(1);
        assertThat(groupSubs.get(1).takeItems(), contains(2));
        assertThat(groupSubs.get(1).takeTerminal(), is(complete()));
        assertThat(groupSubs.get(0).takeTerminal(), is(complete()));
    }

    @Test
    public void groupEnqueueOnError() {
        toSource(subscribeToAllGroups(10)).subscribe(subscriber);
        source.onNext(1);
        assertTrue(subscriber.subscriptionReceived());
        assertThat(subscriber.takeItems(), hasSize(0));
        assertThat(subscriber.takeTerminal(), nullValue());
        subscriber.request(1);
        assertThat("Unexpected group subscribers.", groupSubs, hasSize(1));
        assertTrue(groupSubs.get(0).subscriptionReceived());
        assertThat(groupSubs.get(0).takeItems(), hasSize(0));
        assertThat(groupSubs.get(0).takeTerminal(), nullValue());
        groupSubs.get(0).request(1);
        assertThat(groupSubs.get(0).takeItems(), contains(1));
        assertThat(subscriber.takeItems(), contains(Boolean.FALSE));
        source.onNext(2);
        source.onNext(3);
        assertTrue(subscriber.subscriptionReceived());
        assertThat(subscriber.takeTerminal(), nullValue());
        assertThat("Unexpected group subscribers.", groupSubs, hasSize(1));
        source.onError(DELIBERATE_EXCEPTION);
        assertTrue(groupSubs.get(0).subscriptionReceived());
        assertThat(groupSubs.get(0).takeTerminal(), nullValue());
        groupSubs.get(0).request(1);
        assertThat(groupSubs.get(0).takeItems(), contains(3));
        subscriber.request(1);
        assertThat(subscriber.takeItems(), contains(Boolean.TRUE));
        assertThat("Unexpected group subscribers.", groupSubs, hasSize(2));
        assertTrue(groupSubs.get(1).subscriptionReceived());
        assertThat(groupSubs.get(1).takeItems(), hasSize(0));
        assertThat(groupSubs.get(1).takeTerminal(), nullValue());
        groupSubs.get(1).request(1);
        assertThat(groupSubs.get(1).takeItems(), contains(2));
        assertThat(groupSubs.get(1).takeError(), sameInstance(DELIBERATE_EXCEPTION));
        assertThat(groupSubs.get(0).takeError(), sameInstance(DELIBERATE_EXCEPTION));
    }

    @Test
    public void testOnNextThrows() {
        toSource(subscribeToAllGroups(10, s -> s).doAfterOnNext(i -> {
            throw DELIBERATE_EXCEPTION;
        })).subscribe(subscriber);
        source.onSubscribe(subscription);
        source.onNext(1);
        assertTrue(subscriber.subscriptionReceived());
        assertThat(subscriber.takeItems(), hasSize(0));
        assertThat(subscriber.takeTerminal(), nullValue());
        subscriber.request(1);
        assertThat(subscriber.takeItems(), contains(Boolean.FALSE));
        assertThat("Unexpected group subscribers.", groupSubs, hasSize(1));
        assertTrue(groupSubs.get(0).subscriptionReceived());
        assertThat(groupSubs.get(0).takeItems(), hasSize(0));
        assertThat(groupSubs.get(0).takeTerminal(), nullValue());
        groupSubs.get(0).request(1);
        assertThat(groupSubs.get(0).takeItems(), contains(1));
        assertTrue(subscription.isCancelled());
        assertThat(groupSubs.get(0).takeError(), sameInstance(DELIBERATE_EXCEPTION));
    }

    @Test
    public void testIndividualGroupOnNextThrows() {
        AtomicInteger subscriberCount = new AtomicInteger();
        AtomicBoolean failOnNext = new AtomicBoolean();
        toSource(subscribeToAllGroups(10, s -> {
            if (subscriberCount.getAndIncrement() == 0) {
                return new DelegatingPublisherSubscriber<Integer>(s) {
                    @Override
                    public void onNext(final Integer integer) {
                        super.onNext(integer);
                        if (failOnNext.get()) {
                            throw DELIBERATE_EXCEPTION;
                        }
                    }
                };
            } else {
                return s;
            }
        })).subscribe(subscriber);
        source.onNext(1);
        assertTrue(subscriber.subscriptionReceived());
        assertThat(subscriber.takeItems(), hasSize(0));
        assertThat(subscriber.takeTerminal(), nullValue());
        subscriber.request(1);
        assertThat(subscriber.takeItems(), contains(Boolean.FALSE));
        assertThat("Unexpected group subscribers.", groupSubs, hasSize(1));
        source.onNext(2);
        assertTrue(subscriber.subscriptionReceived());
        assertThat(subscriber.takeTerminal(), nullValue());
        subscriber.request(1);
        assertThat(subscriber.takeItems(), contains(Boolean.TRUE));

        assertTrue(groupSubs.get(1).subscriptionReceived());
        assertThat(groupSubs.get(1).takeItems(), hasSize(0));
        assertThat(groupSubs.get(1).takeTerminal(), nullValue());
        groupSubs.get(1).request(1);
        assertThat(groupSubs.get(1).takeItems(), contains(2));

        assertTrue(groupSubs.get(0).subscriptionReceived());
        assertThat(groupSubs.get(0).takeItems(), hasSize(0));
        assertThat(groupSubs.get(0).takeTerminal(), nullValue());
        failOnNext.set(true);
        groupSubs.get(0).request(1);
        assertThat(groupSubs.get(0).takeItems(), contains(1));

        // We make a best effort to deliver a terminal event to the groups.
        assertThat(groupSubs.get(0).takeError(), sameInstance(DELIBERATE_EXCEPTION));
        assertThat(groupSubs.get(1).takeError(), sameInstance(DELIBERATE_EXCEPTION));
        assertTrue(subscriber.subscriptionReceived());
        assertThat(subscriber.takeItems(), hasSize(0));
        assertThat(subscriber.takeTerminal(), nullValue());
    }

    @Test
    public void testConcurrentDrain() throws Exception {
        ExecutorService executorService = Executors.newFixedThreadPool(1);
        try {
            final int totalData = 10000;
            final Thread writerThread = Thread.currentThread();
            final AtomicInteger pendingDemand = new AtomicInteger();
            toSource(subscribeToAllGroups(totalData,
                    TestPublisherSubscriber::new,
                    s -> s)).subscribe(subscriber);
            CountDownLatch latch1 = new CountDownLatch(1);
            CountDownLatch latch2 = new CountDownLatch(1);
            AtomicReference<TestPublisherSubscriber<Integer>> groupSubRef = new AtomicReference<>();
            Future<?> f = executorService.submit(() -> {
                latch1.countDown();
                try {
                    latch2.await();
                    TestPublisherSubscriber<Integer> groupSub = groupSubRef.get();
                    for (int i = 0; i < totalData; ++i) {
                        groupSub.request(1);
                        pendingDemand.incrementAndGet();
                        LockSupport.unpark(writerThread);
                    }
                } catch (InterruptedException e) {
                    throw new RuntimeException(e);
                }
            });

            latch1.await();
            source.onNext(1);
            assertTrue(subscriber.subscriptionReceived());
            assertThat(subscriber.takeItems(), hasSize(0));
            assertThat(subscriber.takeTerminal(), nullValue());
            subscriber.request(1);
            assertThat(subscriber.takeItems(), contains(Boolean.FALSE));

            TestPublisherSubscriber<Integer> groupSub = groupSubs.get(0);
            groupSubRef.set(groupSub);
            assertTrue(groupSub.subscriptionReceived());
            assertThat(groupSub.takeItems(), hasSize(0));
            assertThat(groupSub.takeTerminal(), nullValue());
            latch2.countDown();

            // writerThread
            final int endIndex = totalData - 1;
            int totalDelivered = 0;
            while (totalDelivered < endIndex) {
                LockSupport.park();
                final int currPendingDemand = pendingDemand.getAndSet(0);
                totalDelivered += currPendingDemand;
                for (int x = 0; x < currPendingDemand; ++x) {
                    source.onNext(1);
                }
            }

            f.get();

            List<Integer> items = groupSub.takeItems();
            assertThat(items.size(), is(totalData));
            for (Integer item : items) {
                assertThat(item, is(1));
            }
        } finally {
            executorService.shutdown();
        }
    }

    private void testGroupSubscriberCancel(int requestFromGroupOnSubscribe) {
        toSource(subscribeToAllGroups(10, s -> {
            if (requestFromGroupOnSubscribe > 0) {
                s.request(requestFromGroupOnSubscribe);
            }
            return s;
        })).subscribe(subscriber);
        subscriber.request(5);
        source.onNext(1, 3, 5, 7, 9);
        assertThat(subscriber.takeItems(), contains(Boolean.FALSE));
        assertThat("Unexpected group subscribers.", groupSubs, hasSize(1));
        TestPublisherSubscriber<Integer> sub = groupSubs.remove(0);
        if (requestFromGroupOnSubscribe <= 0) {
            sub.request(1);
        }
        assertThat(sub.takeItems(), contains(1));
        sub.cancel();
        assertTrue(sub.subscriptionReceived());
        assertThat(sub.takeTerminal(), nullValue());
    }

    @Test
    public void testGroupsSubscriberCancelled() {
        toSource(subscribeToAllGroups(10)).subscribe(subscriber);
        subscriber.request(5);
        source.onNext(1, 3, 5, 7, 9);
        assertThat(subscriber.takeItems(), contains(Boolean.FALSE));
        subscriber.cancel();
        assertThat("Unexpected group subscribers.", groupSubs, hasSize(1));
        TestPublisherSubscriber<Integer> sub = groupSubs.remove(0);
        sub.request(5);
        assertThat(sub.takeItems(), contains(1, 3, 5, 7, 9));
        assertThat(sub.takeError(), instanceOf(CancellationException.class));
    }

    @Test
    public void testDelaySubscriptionToGroup() {
        List<GroupedPublisher<Boolean, Integer>> groups = subscribe(8);
        subscriber.request(1);
        source.onNext(1);
        source.onComplete();
        assertThat(subscriber.takeItems(), contains(Boolean.TRUE));
        assertThat(subscriber.takeTerminal(), is(complete()));
        assertThat("Unexpected groups.", groups, hasSize(1));
        GroupedPublisher<Boolean, Integer> grp = groups.remove(0);
        TestPublisherSubscriber<Integer> subscriber = new TestPublisherSubscriber<>();
        toSource(grp).subscribe(subscriber);
        subscriber.request(1);
        assertThat(subscriber.takeItems(), contains(1));
        assertThat(subscriber.takeTerminal(), is(complete()));
    }

    @Test
    public void testGroupLevelQueueBreachWhenNotSubscribed() {
        List<GroupedPublisher<Boolean, Integer>> groups = subscribe(16);
        source.onSubscribe(subscription);
        subscriber.request(1);
        for (int i = 0; i <= 16; i++) {
            source.onNext(i);
        }
        assertTrue(subscription.isCancelled());
        assertThat(subscriber.takeItems(), contains(Boolean.TRUE));
        assertThat(subscriber.takeError(), instanceOf(QueueFullException.class));
        assertThat("Unexpected groups.", groups, hasSize(1));
        GroupedPublisher<Boolean, Integer> grp = groups.remove(0);
        TestPublisherSubscriber<Integer> subscriber = new TestPublisherSubscriber<>();
        toSource(grp).subscribe(subscriber);
        subscriber.request(16);
        assertThat(subscriber.takeItems(), contains(0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15));
        assertThat(subscriber.takeError(), instanceOf(QueueFullException.class));
    }

    @Test
    public void testGroupLevelQueueBreachWhenNotRequested() {
        toSource(subscribeToAllGroups(integer -> Boolean.TRUE, 16, s -> s)).subscribe(subscriber);
        source.onSubscribe(subscription);
        subscriber.request(1);
        for (int i = 0; i <= 16; i++) {
            source.onNext(i);
        }
        assertTrue(subscription.isCancelled());
        assertThat(subscriber.takeItems(), contains(Boolean.TRUE));
        assertThat(subscriber.takeError(), instanceOf(QueueFullException.class));
        assertThat("Unexpected groups.", groupSubs, hasSize(1));
        TestPublisherSubscriber<Integer> subscriber = groupSubs.remove(0);
        subscriber.request(16);
        assertThat(subscriber.takeItems(), contains(0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15));
        assertThat(subscriber.takeError(), instanceOf(QueueFullException.class));
    }

    @Test
    public void testKeySelectorThrowsWithQueue() {
        toSource(subscribeToAllGroups(integer -> {
            if (integer % 2 == 0) {
                throw DELIBERATE_EXCEPTION;
            }
            return Boolean.TRUE;
        }, 10, s -> s)).subscribe(subscriber);
        source.onSubscribe(subscription);
        source.onNext(1, 2);
        assertTrue(subscription.isCancelled());
        subscriber.request(1);
        assertThat(subscriber.takeItems(), contains(Boolean.TRUE));
        assertThat(subscriber.takeError(), sameInstance(DELIBERATE_EXCEPTION));
        assertThat("Unexpected group subscribers.", groupSubs, hasSize(1));
        groupSubs.get(0).request(1);
        assertThat(groupSubs.get(0).takeItems(), contains(1));
        assertThat(groupSubs.get(0).takeError(), sameInstance(DELIBERATE_EXCEPTION));
    }

    @Test
    public void testKeySelectorThrowsNoQueue() {
        toSource(subscribeToAllGroups(integer -> {
            if (integer % 2 == 0) {
                throw DELIBERATE_EXCEPTION;
            }
            return Boolean.TRUE;
        }, 10, s -> s)).subscribe(subscriber);
        source.onSubscribe(subscription);
        subscriber.request(1);
        source.onNext(1, 2);
        assertTrue(subscription.isCancelled());
        assertThat(subscriber.takeItems(), contains(Boolean.TRUE));
        assertThat(subscriber.takeError(), sameInstance(DELIBERATE_EXCEPTION));
        assertThat("Unexpected group subscribers.", groupSubs, hasSize(1));
        groupSubs.get(0).request(1);
        assertThat(groupSubs.get(0).takeItems(), contains(1));
        assertThat(groupSubs.get(0).takeError(), sameInstance(DELIBERATE_EXCEPTION));
    }

    @Test
    public void testPendingGroupsQueueBreach() {
        @SuppressWarnings("unchecked")
        Subscriber<GroupedPublisher<Integer, Integer>> subscriber = mock(Subscriber.class);
        toSource(source.groupBy(integer -> integer, 16)).subscribe(subscriber);
        ArgumentCaptor<Subscription> subscriptionCaptor = forClass(Subscription.class);
        verify(subscriber).onSubscribe(subscriptionCaptor.capture());
        Subscription subscription = subscriptionCaptor.getValue();
        for (int i = 0; i <= 16; i++) {
            source.onNext(i);
        }
        subscription.request(16);
        verify(subscriber).onError(any(QueueFullException.class));
    }

    @Test
    public void testMaxBufferRequestNAndThenRequestMore() {
        toSource(subscribeToAllGroups(2)).subscribe(subscriber);
        source.onSubscribe(subscription);
        subscriber.request(1);
        assertThat(subscription.requested(), is(1L));
        source.onNext(1);
        assertThat(subscriber.takeItems(), contains(Boolean.FALSE));
        assertThat("Unexpected group subscribers.", groupSubs, hasSize(1));
        final TestPublisherSubscriber<Integer> grpSub = groupSubs.remove(0);
        grpSub.request(3);
        assertThat(subscription.requested(), is(3L));
        source.onNext(3, 5);
        assertThat(subscription.requested(), is(3L));
        source.onComplete();
        assertThat(grpSub.takeItems(), contains(1, 3, 5));
        assertThat(grpSub.takeTerminal(), is(complete()));
    }

    @Test
    public void nullValueIsSupported() {
        toSource(subscribeToAllGroups(2)).subscribe(subscriber);
        subscriber.request(1);
        source.onNext(1, null);
        source.onComplete();
        assertThat(subscriber.takeItems(), contains(Boolean.FALSE));
        assertThat(subscriber.takeTerminal(), is(complete()));
        assertThat("Unexpected group subscribers.", groupSubs, hasSize(1));
        groupSubs.get(0).request(2);
        assertThat(groupSubs.get(0).takeItems(), contains(1, null));
        assertThat(groupSubs.get(0).takeTerminal(), is(complete()));
    }

    private void testGroupOnNextThrows(int requestFromGroupOnSubscribe) {
        toSource(subscribeToAllGroups(10, s -> {
            if (requestFromGroupOnSubscribe > 0) {
                s.request(requestFromGroupOnSubscribe);
            }
            return new DelegatingPublisherSubscriber<Integer>(s) {
                @Override
                public void onNext(final Integer i) {
                    super.onNext(i);
                    throw DELIBERATE_EXCEPTION;
                }

                @Override
                public void onError(final Throwable t) {
                    super.onError(t);
                    throw new DeliberateException();
                }
            };
        })).subscribe(subscriber);
        subscriber.request(1);
        source.onNext(1);
        source.onComplete();
        assertThat("Unexpected group subscribers.", groupSubs, hasSize(1));
        groupSubs.get(0).request(1);
        assertThat(groupSubs.get(0).takeItems(), contains(1));
    }

    private void testGroupOnNextAndError(List<TestPublisherSubscriber<Integer>> groupSubs) {
        subscriber.request(1);
        source.onNext(1, 3);
        source.onError(DELIBERATE_EXCEPTION);
        assertThat(subscriber.takeItems(), contains(Boolean.FALSE));
        assertThat(subscriber.takeError(), sameInstance(DELIBERATE_EXCEPTION));
        assertThat("Unexpected group subscribers.", groupSubs, hasSize(1));
        groupSubs.get(0).request(2);
        assertThat(groupSubs.get(0).takeItems(), contains(1, 3));
        assertThat(groupSubs.get(0).takeError(), sameInstance(DELIBERATE_EXCEPTION));
    }

    private void testGroupOnNextAndComplete(List<TestPublisherSubscriber<Integer>> groupSubs) {
        subscriber.request(1);
        source.onNext(1, 3);
        source.onComplete();
        assertThat(subscriber.takeItems(), contains(Boolean.FALSE));
        assertThat(subscriber.takeTerminal(), is(complete()));
        assertThat("Unexpected group subscribers.", groupSubs, hasSize(1));
        groupSubs.get(0).request(2);
        assertThat(groupSubs.get(0).takeItems(), contains(1, 3));
        assertThat(groupSubs.get(0).takeTerminal(), is(complete()));
    }

    private List<GroupedPublisher<Boolean, Integer>> subscribe(int maxBufferPerGroup) {
        List<GroupedPublisher<Boolean, Integer>> groups = new ArrayList<>();
        toSource(source.groupBy(integer -> Boolean.TRUE, maxBufferPerGroup).map(grp -> {
            groups.add(grp);
            return grp.key();
        })).subscribe(subscriber);
        return groups;
    }

    private Publisher<Boolean> subscribeToAllGroups(int maxBufferPerGroup) {
        return subscribeToAllGroups(maxBufferPerGroup, s -> s);
    }

    private Publisher<Boolean> subscribeToAllGroups(
            Function<Integer, Boolean> keySelector, int maxBufferPerGroup,
            Function<TestPublisherSubscriber<Integer>, Subscriber<Integer>> subscriberFunction) {
        return subscribeToAllGroups(keySelector, maxBufferPerGroup,
                TestPublisherSubscriber::new, subscriberFunction);
    }

    private Publisher<Boolean> subscribeToAllGroups(
            Function<Integer, Boolean> keySelector, int maxBufferPerGroup,
            final Supplier<TestPublisherSubscriber<Integer>> subscriberSupplier,
            Function<TestPublisherSubscriber<Integer>, Subscriber<Integer>> subscriberFunction) {
        return source.groupBy(keySelector, maxBufferPerGroup).map(grp -> {
            TestPublisherSubscriber<Integer> subscriber = subscriberSupplier.get();
            toSource(grp).subscribe(subscriberFunction.apply(subscriber));
            groupSubs.add(subscriber);
            return grp.key();
        });
    }

    private Publisher<Boolean> subscribeToAllGroups(
            int maxBufferPerGroup,
            Function<TestPublisherSubscriber<Integer>, Subscriber<Integer>> subscriberFunction) {
        return subscribeToAllGroups(maxBufferPerGroup, TestPublisherSubscriber::new,
                subscriberFunction);
    }

    private Publisher<Boolean> subscribeToAllGroups(
            int maxBufferPerGroup,
            final Supplier<TestPublisherSubscriber<Integer>> subscriberSupplier,
            Function<TestPublisherSubscriber<Integer>, Subscriber<Integer>> subscriberFunction) {
        return subscribeToAllGroups(integer -> integer != null && integer % 2 == 0, maxBufferPerGroup,
                subscriberSupplier, subscriberFunction);
    }
}

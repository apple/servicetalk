/*
 * Copyright © 2018 Apple Inc. and the ServiceTalk project authors
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

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.reactivestreams.Subscriber;
import org.reactivestreams.Subscription;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.function.Function;

import static io.servicetalk.concurrent.api.DeliberateException.DELIBERATE_EXCEPTION;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.mockito.ArgumentCaptor.forClass;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

public class PublisherGroupByTest {

    private TestPublisher<Integer> source;
    @Rule public final MockedSubscriberRule<Boolean> subscriber = new MockedSubscriberRule<>();

    @Before
    public void setUp() throws Exception {
        source = new TestPublisher<>(true);
        source.sendOnSubscribe();
    }

    @Test
    public void testGroupOnNextAndCompleteWithGroupQueue() throws Exception {
        List<MockedSubscriberRule<Integer>> groupSubs = subscribeToAllGroups(10);
        testGroupOnNextAndComplete(groupSubs);
    }

    @Test
    public void testGroupOnNextAndErrorWithGroupQueue() throws Exception {
        List<MockedSubscriberRule<Integer>> groupSubs = subscribeToAllGroups(10);
        testGroupOnNextAndError(groupSubs);
    }

    @Test
    public void testGroupOnNextAndCompleteNoQueue() throws Exception {
        List<MockedSubscriberRule<Integer>> groupSubs = subscribeToAllGroups(10, s -> s.request(1));
        testGroupOnNextAndComplete(groupSubs);
    }

    @Test
    public void testGroupOnNextAndErrorNoQueue() throws Exception {
        List<MockedSubscriberRule<Integer>> groupSubs = subscribeToAllGroups(10, s -> s.request(1));
        testGroupOnNextAndError(groupSubs);
    }

    @Test
    public void testGroupOnNextThrowsNoQueue() throws Exception {
        testGroupOnNextThrows(1);
    }

    @Test
    public void testGroupOnNextThrowsWithQueue() throws Exception {
        testGroupOnNextThrows(0);
    }

    @Test
    public void testGroupSubscriberCancelNoQueue() throws Exception {
        testGroupSubscriberCancel(1);
    }

    @Test
    public void testGroupSubscriberCancelWithQueue() throws Exception {
        testGroupSubscriberCancel(0);
    }

    @Test
    public void testHighDemandWithQueue() {
        List<MockedSubscriberRule<Integer>> groupSubs = subscribeToAllGroups(10);
        source.sendItemsNoDemandCheck(1);
        subscriber.verifyNoEmissions();
        subscriber.request(1);
        assertThat("Unexpected group subscribers.", groupSubs, hasSize(1));
        groupSubs.get(0).verifyNoEmissions();
        groupSubs.get(0).request(1);
        groupSubs.get(0).verifyItems(1);
        subscriber.verifyItems(Boolean.FALSE);
        source.sendItemsNoDemandCheck(2);
        subscriber.verifyNoEmissions();
        subscriber.request(Long.MAX_VALUE);
        assertThat("Unexpected group subscribers.", groupSubs, hasSize(2));
        subscriber.verifyItems(Boolean.TRUE);
        groupSubs.get(1).verifyNoEmissions();
        groupSubs.get(1).request(1);
        groupSubs.get(1).verifyItems(2);
    }

    @Test
    public void testIndividualGroupSubscriptionRequestQueuesGroups() {
        List<MockedSubscriberRule<Integer>> groupSubs = subscribeToAllGroups(10);
        source.sendItemsNoDemandCheck(1);
        subscriber.verifyNoEmissions();
        subscriber.request(1);
        assertThat("Unexpected group subscribers.", groupSubs, hasSize(1));
        groupSubs.get(0).verifyNoEmissions();
        groupSubs.get(0).request(Long.MAX_VALUE);
        groupSubs.get(0).verifyItems(1);
        subscriber.verifyItems(Boolean.FALSE);
        source.sendItemsNoDemandCheck(2);
        subscriber.verifyNoEmissions();
        assertThat("Unexpected group subscribers.", groupSubs, hasSize(1));
        source.sendItemsNoDemandCheck(3);
        groupSubs.get(0).verifyItems(3);
        subscriber.request(1);
        subscriber.verifyItems(Boolean.TRUE);
        assertThat("Unexpected group subscribers.", groupSubs, hasSize(2));
        groupSubs.get(1).verifyNoEmissions();
        groupSubs.get(1).request(1);
        groupSubs.get(1).verifyItems(2);
    }

    @Test
    public void groupEnqueueOnComplete() {
        List<MockedSubscriberRule<Integer>> groupSubs = subscribeToAllGroups(10);
        source.sendItemsNoDemandCheck(1);
        subscriber.verifyNoEmissions();
        subscriber.request(1);
        assertThat("Unexpected group subscribers.", groupSubs, hasSize(1));
        groupSubs.get(0).verifyNoEmissions();
        groupSubs.get(0).request(1);
        groupSubs.get(0).verifyItems(1);
        subscriber.verifyItems(Boolean.FALSE);
        source.sendItemsNoDemandCheck(2);
        source.sendItemsNoDemandCheck(3);
        subscriber.verifyNoEmissions();
        assertThat("Unexpected group subscribers.", groupSubs, hasSize(1));
        source.onComplete();
        groupSubs.get(0).verifyNoEmissions();
        groupSubs.get(0).request(1);
        groupSubs.get(0).verifyItems(3);
        subscriber.request(1);
        subscriber.verifySuccess(Boolean.TRUE);
        assertThat("Unexpected group subscribers.", groupSubs, hasSize(2));
        groupSubs.get(1).verifyNoEmissions();
        groupSubs.get(1).request(1);
        groupSubs.get(1).verifySuccess(2);
        groupSubs.get(0).verifySuccess();
    }

    @Test
    public void groupEnqueueOnError() {
        List<MockedSubscriberRule<Integer>> groupSubs = subscribeToAllGroups(10);
        source.sendItemsNoDemandCheck(1);
        subscriber.verifyNoEmissions();
        subscriber.request(1);
        assertThat("Unexpected group subscribers.", groupSubs, hasSize(1));
        groupSubs.get(0).verifyNoEmissions();
        groupSubs.get(0).request(1);
        groupSubs.get(0).verifyItems(1);
        subscriber.verifyItems(Boolean.FALSE);
        source.sendItemsNoDemandCheck(2);
        source.sendItemsNoDemandCheck(3);
        subscriber.verifyNoEmissions();
        assertThat("Unexpected group subscribers.", groupSubs, hasSize(1));
        source.onError(DELIBERATE_EXCEPTION);
        groupSubs.get(0).verifyNoEmissions();
        groupSubs.get(0).request(1);
        groupSubs.get(0).verifyItems(3);
        subscriber.request(1);
        subscriber.verifyItems(Boolean.TRUE);
        assertThat("Unexpected group subscribers.", groupSubs, hasSize(2));
        groupSubs.get(1).verifyNoEmissions();
        groupSubs.get(1).request(1);
        groupSubs.get(1).verifyItems(2);
        groupSubs.get(1).verifyFailure(DELIBERATE_EXCEPTION);
        groupSubs.get(0).verifyFailure(DELIBERATE_EXCEPTION);
    }

    @Test
    public void testOnNextThrows() {
        List<MockedSubscriberRule<Integer>> groupSubs = subscribeToAllGroups(10);
        doThrow(DELIBERATE_EXCEPTION).when(subscriber.getSubscriber()).onNext(anyBoolean());
        source.sendItemsNoDemandCheck(1);
        subscriber.verifyNoEmissions();
        subscriber.request(1);
        subscriber.verifyItems(Boolean.FALSE);
        assertThat("Unexpected group subscribers.", groupSubs, hasSize(1));
        groupSubs.get(0).verifyNoEmissions();
        groupSubs.get(0).request(1);
        groupSubs.get(0).verifyItems(1);
        source.verifyCancelled();
        groupSubs.get(0).verifyFailure(DELIBERATE_EXCEPTION);
    }

    @Test
    public void testIndividualGroupOnNextThrows() {
        List<MockedSubscriberRule<Integer>> groupSubs = subscribeToAllGroups(10);
        source.sendItemsNoDemandCheck(1);
        subscriber.verifyNoEmissions();
        subscriber.request(1);
        subscriber.verifyItems(Boolean.FALSE);
        assertThat("Unexpected group subscribers.", groupSubs, hasSize(1));
        source.sendItemsNoDemandCheck(2);
        subscriber.verifyNoEmissions();
        subscriber.request(1);
        subscriber.verifyItems(Boolean.TRUE);

        groupSubs.get(1).verifyNoEmissions();
        groupSubs.get(1).request(1);
        groupSubs.get(1).verifyItems(2);

        groupSubs.get(0).verifyNoEmissions();
        doThrow(DELIBERATE_EXCEPTION).when(groupSubs.get(0).getSubscriber()).onNext(anyInt());
        groupSubs.get(0).request(1);
        groupSubs.get(0).verifyItems(1);

        // We make a best effort to deliver a terminal event to the groups.
        groupSubs.get(0).verifyFailure(DELIBERATE_EXCEPTION);
        groupSubs.get(1).verifyFailure(DELIBERATE_EXCEPTION);
        subscriber.verifyNoEmissions();
    }

    @Test
    public void testConcurrentDrain() throws ExecutionException, InterruptedException {
        ExecutorService executorService = Executors.newFixedThreadPool(1);
        try {
            final int totalData = 10000;
            final List<MockedSubscriberRule<Integer>> groupSubs = subscribeToAllGroups(totalData);
            CountDownLatch latch1 = new CountDownLatch(1);
            CountDownLatch latch2 = new CountDownLatch(1);
            AtomicReference<MockedSubscriberRule<Integer>> groupSubRef = new AtomicReference<>();
            Future<?> f = executorService.submit(() -> {
                latch1.countDown();
                try {
                    latch2.await();
                    MockedSubscriberRule<Integer> groupSub = groupSubRef.get();
                    for (int i = 0; i < totalData; ++i) {
                        groupSub.request(1);
                        Thread.yield();
                    }
                } catch (InterruptedException e) {
                    throw new RuntimeException(e);
                }
            });

            latch1.await();
            source.sendItemsNoDemandCheck(1);
            subscriber.verifyNoEmissions();
            subscriber.request(1);
            subscriber.verifyItems(Boolean.FALSE);

            MockedSubscriberRule<Integer> groupSub = groupSubs.get(0);
            groupSubRef.set(groupSub);
            groupSub.verifyNoEmissions();
            latch2.countDown();

            final int endIndex = totalData - 1;
            for (int i = 0; i < endIndex; ++i) {
                source.sendItemsNoDemandCheck(1);
                Thread.yield();
            }

            f.get();

            groupSub.verifyItems(sub -> verify(sub, times(totalData)));
        } finally {
            executorService.shutdown();
        }
    }

    private void testGroupSubscriberCancel(int requestFromGroupOnSubscribe) throws Exception {
        List<MockedSubscriberRule<Integer>> groupSubs = subscribeToAllGroups(10, s -> {
            if (requestFromGroupOnSubscribe > 0) {
                s.request(requestFromGroupOnSubscribe);
            }
        });
        subscriber.request(5);
        source.sendItems(1, 3, 5, 7, 9);
        subscriber.verifyItems(Boolean.FALSE);
        assertThat("Unexpected group subscribers.", groupSubs, hasSize(1));
        MockedSubscriberRule<Integer> sub = groupSubs.remove(0);
        if (requestFromGroupOnSubscribe <= 0) {
            sub.request(1);
        }
        sub.verifyItems(1).cancel().verifyNoEmissions();
    }

    @Test
    public void testGroupsSubscriberCancelled() throws Exception {
        List<MockedSubscriberRule<Integer>> groupSubs = subscribeToAllGroups(10);
        subscriber.request(5);
        source.sendItems(1, 3, 5, 7, 9);
        subscriber.verifyItems(Boolean.FALSE).cancel();
        assertThat("Unexpected group subscribers.", groupSubs, hasSize(1));
        MockedSubscriberRule<Integer> sub = groupSubs.remove(0);
        sub.request(5).verifyItems(1, 3, 5, 7, 9).verifyFailure(CancellationException.class);
    }

    @Test
    public void testDelaySubscriptionToGroup() throws Exception {
        List<Publisher.Group<Boolean, Integer>> groups = subscribe(8);
        subscriber.request(1);
        source.sendItemsNoDemandCheck(1).onComplete();
        subscriber.verifySuccess(Boolean.TRUE);
        assertThat("Unexpected groups.", groups, hasSize(1));
        Publisher.Group<Boolean, Integer> grp = groups.remove(0);
        MockedSubscriberRule<Integer> subscriber = new MockedSubscriberRule<>();
        subscriber.subscribe(grp.getPublisher()).verifySuccess(1);
    }

    @Test
    public void testGroupLevelQueueBreachWhenNotSubscribed() throws Exception {
        List<Publisher.Group<Boolean, Integer>> groups = subscribe(16);
        subscriber.request(1);
        for (int i = 0; i <= 16; i++) {
            source.sendItemsNoDemandCheck(i);
        }
        source.verifyCancelled();
        subscriber.verifyItems(Boolean.TRUE).verifyFailure(QueueFullException.class);
        assertThat("Unexpected groups.", groups, hasSize(1));
        Publisher.Group<Boolean, Integer> grp = groups.remove(0);
        MockedSubscriberRule<Integer> subscriber = new MockedSubscriberRule<>();
        subscriber.subscribe(grp.getPublisher());
        subscriber.request(16);
        for (int i = 0; i < 16; i++) {
            subscriber.verifyItems(i);
        }
        subscriber.verifyFailure(QueueFullException.class);
    }

    @Test
    public void testGroupLevelQueueBreachWhenNotRequested() throws Exception {
        List<MockedSubscriberRule<Integer>> groupSubs = subscribeToAllGroups(integer -> Boolean.TRUE, 16, s -> { });
        subscriber.request(1);
        for (int i = 0; i <= 16; i++) {
            source.sendItemsNoDemandCheck(i);
        }
        source.verifyCancelled();
        subscriber.verifyItems(Boolean.TRUE).verifyFailure(QueueFullException.class);
        assertThat("Unexpected groups.", groupSubs, hasSize(1));
        MockedSubscriberRule<Integer> subscriber = groupSubs.remove(0);
        subscriber.request(16);
        for (int i = 0; i < 16; i++) {
            subscriber.verifyItems(i);
        }
        subscriber.verifyFailure(QueueFullException.class);
    }

    @Test
    public void testKeySelectorThrowsWithQueue() throws Exception {
        List<MockedSubscriberRule<Integer>> subs = subscribeToAllGroups(integer -> {
            if (integer % 2 == 0) {
                throw DELIBERATE_EXCEPTION;
            }
            return Boolean.TRUE;
        }, 10, s -> { });
        source.sendItemsNoDemandCheck(1, 2).verifyCancelled();
        subscriber.request(1).verifyItems(Boolean.TRUE).verifyFailure(DELIBERATE_EXCEPTION);
        assertThat("Unexpected group subscribers.", subs, hasSize(1));
        subs.remove(0).request(1).verifyItems(1).verifyFailure(DELIBERATE_EXCEPTION);
    }

    @Test
    public void testKeySelectorThrowsNoQueue() throws Exception {
        List<MockedSubscriberRule<Integer>> subs = subscribeToAllGroups(integer -> {
            if (integer % 2 == 0) {
                throw DELIBERATE_EXCEPTION;
            }
            return Boolean.TRUE;
        }, 10, s -> { });
        subscriber.request(1);
        source.sendItemsNoDemandCheck(1, 2).verifyCancelled();
        subscriber.verifyItems(Boolean.TRUE).verifyFailure(DELIBERATE_EXCEPTION);
        assertThat("Unexpected group subscribers.", subs, hasSize(1));
        subs.remove(0).request(1).verifyItems(1).verifyFailure(DELIBERATE_EXCEPTION);
    }

    @Test
    public void testPendingGroupsQueueBreach() throws Exception {
        @SuppressWarnings("unchecked") Subscriber<Publisher.Group<Integer, Integer>> subscriber = mock(Subscriber.class);
        source.groupBy(integer -> integer, 16).subscribe(subscriber);
        ArgumentCaptor<Subscription> subscriptionCaptor = forClass(Subscription.class);
        verify(subscriber).onSubscribe(subscriptionCaptor.capture());
        Subscription subscription = subscriptionCaptor.getValue();
        for (int i = 0; i <= 16; i++) {
            source.sendItemsNoDemandCheck(i);
        }
        subscription.request(16);
        verify(subscriber).onError(any(QueueFullException.class));
    }

    @Test
    public void testMaxBufferRequestNAndThenRequestMore() throws Exception {
        final List<MockedSubscriberRule<Integer>> groupSubs = subscribeToAllGroups(2);
        subscriber.request(1);
        source.verifyRequested(1).sendItems(1);
        subscriber.verifyItems(Boolean.FALSE);
        assertThat("Unexpected group subscribers.", groupSubs, hasSize(1));
        final MockedSubscriberRule<Integer> grpSub = groupSubs.remove(0);
        grpSub.request(3);
        source.verifyRequested(3).verifyOutstanding(2).sendItems(3, 5).verifyRequested(3).verifyOutstanding(0).onComplete();
        grpSub.verifySuccess(1, 3, 5);
    }

    @Test
    public void nullValueIsSupported() {
        final List<MockedSubscriberRule<Integer>> groupSubs = subscribeToAllGroups(2);
        subscriber.request(1);
        source.sendItemsNoDemandCheck(1, null).onComplete();
        subscriber.verifySuccess(Boolean.FALSE);
        assertThat("Unexpected group subscribers.", groupSubs, hasSize(1));
        groupSubs.remove(0).request(2).verifySuccess(1, null);
    }

    private void testGroupOnNextThrows(int requestFromGroupOnSubscribe) {
        List<MockedSubscriberRule<Integer>> groupSubs = subscribeToAllGroups(10, s -> {
            doThrow(DELIBERATE_EXCEPTION).when(s.getSubscriber()).onNext(anyInt());
            doThrow(new DeliberateException()).when(s.getSubscriber()).onError(any());
            if (requestFromGroupOnSubscribe > 0) {
                s.request(requestFromGroupOnSubscribe);
            }
        });
        subscriber.request(1);
        source.sendItemsNoDemandCheck(1).onComplete();
        assertThat("Unexpected group subscribers.", groupSubs, hasSize(1));
        groupSubs.remove(0).request(1).verifyItems(1);
    }

    private void testGroupOnNextAndError(List<MockedSubscriberRule<Integer>> groupSubs) {
        subscriber.request(1);
        source.sendItemsNoDemandCheck(1, 3).fail();
        subscriber.verifyItems(Boolean.FALSE).verifyFailure(DELIBERATE_EXCEPTION);
        assertThat("Unexpected group subscribers.", groupSubs, hasSize(1));
        groupSubs.remove(0).request(2).verifyItems(1, 3).verifyFailure(DELIBERATE_EXCEPTION);
    }

    private void testGroupOnNextAndComplete(List<MockedSubscriberRule<Integer>> groupSubs) {
        subscriber.request(1);
        source.sendItemsNoDemandCheck(1, 3).onComplete();
        subscriber.verifySuccess(Boolean.FALSE);
        assertThat("Unexpected group subscribers.", groupSubs, hasSize(1));
        groupSubs.remove(0).request(2).verifySuccess(1, 3);
    }

    private List<Publisher.Group<Boolean, Integer>> subscribe(int maxBufferPerGroup) {
        List<Publisher.Group<Boolean, Integer>> groups = new ArrayList<>();
        subscriber.subscribe(source.groupBy(integer -> Boolean.TRUE, maxBufferPerGroup).map(grp -> {
            groups.add(grp);
            return grp.getKey();
        }));
        return groups;
    }

    private List<MockedSubscriberRule<Integer>> subscribeToAllGroups(int maxBufferPerGroup) {
        return subscribeToAllGroups(maxBufferPerGroup, s -> { });
    }

    private List<MockedSubscriberRule<Integer>> subscribeToAllGroups(Function<Integer, Boolean> keySelector, int maxBufferPerGroup,
                                                                     Consumer<MockedSubscriberRule<Integer>> newSubscriberConsumer) {
        List<MockedSubscriberRule<Integer>> subscribers = new ArrayList<>();
        subscriber.subscribe(source.groupBy(keySelector, maxBufferPerGroup).map(grp -> {
            MockedSubscriberRule<Integer> subscriber = new MockedSubscriberRule<>();
            subscriber.subscribe(grp.getPublisher());
            newSubscriberConsumer.accept(subscriber);
            subscribers.add(subscriber);
            return grp.getKey();
        }));
        return subscribers;
    }

    private List<MockedSubscriberRule<Integer>> subscribeToAllGroups(int maxBufferPerGroup, Consumer<MockedSubscriberRule<Integer>> newSubscriberConsumer) {
        return subscribeToAllGroups(integer -> integer != null && integer % 2 == 0, maxBufferPerGroup, newSubscriberConsumer);
    }
}

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

import io.servicetalk.concurrent.BlockingIterator;
import io.servicetalk.concurrent.internal.DeliberateException;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Spliterator;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;

import static io.servicetalk.concurrent.api.Publisher.from;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static java.util.stream.Collectors.toList;
import static java.util.stream.StreamSupport.stream;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;
import static org.hamcrest.Matchers.sameInstance;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

final class PublisherAsBlockingIterableTest {

    private final TestPublisher<Integer> source = new TestPublisher<>();

    @Test
    void subscribeDelayedTillIterator() {
        Iterable<Integer> iterable = source.toIterable();
        assertFalse(source.isSubscribed());
        iterable.iterator();
        assertTrue(source.isSubscribed());
    }

    @Test
    void removeNotSupported() {
        assertThrows(UnsupportedOperationException.class, () -> source.toIterable().iterator().remove());
    }

    @Test
    void allItemsAreReturned() {
        Spliterator<Integer> iterator = from(1, 2, 3, 4).toIterable().spliterator();
        List<Integer> result = stream(iterator, false).collect(toList());
        assertThat("Unexpected result.", result, contains(1, 2, 3, 4));
    }

    @Test
    void errorEmittedIsThrown() {
        DeliberateException de = new DeliberateException();
        Iterator<Integer> iterator = Publisher.<Integer>failed(de).toIterable().iterator();
        assertThat("Item expected but not found.", iterator.hasNext(), is(true));
        assertSame(de, assertThrows(DeliberateException.class, () -> iterator.next()));
    }

    @Test
    void doubleHashNextWithError() {
        DeliberateException de = new DeliberateException();
        Iterator<Integer> iterator = Publisher.<Integer>failed(de).toIterable().iterator();
        assertThat("Item expected but not found.", iterator.hasNext(), is(true));
        assertThat("Second hasNext inconsistent with first.", iterator.hasNext(), is(true));
        assertSame(de, assertThrows(DeliberateException.class, () -> iterator.next()));
    }

    @Test
    void hasNextWithEmpty() {
        Iterator<Integer> iterator = Publisher.<Integer>empty().toIterable().iterator();
        assertThat("Item not expected but found.", iterator.hasNext(), is(false));
    }

    @Test
    void nextWithEmpty() {
        Iterator<Integer> iterator = Publisher.<Integer>empty().toIterable().iterator();
        assertThat("Item not expected but found.", iterator.hasNext(), is(false));
        assertThrows(NoSuchElementException.class, () -> iterator.next());
    }

    @Test
    void hasNextWithTimeout() throws Exception {
        BlockingIterator<Integer> iterator = source.toIterable().iterator();
        assertTrue(source.isSubscribed());
        TestSubscription subscription = new TestSubscription();
        source.onSubscribe(subscription);
        source.onNext(1, 2);
        assertThat("hasNext timed out.", iterator.hasNext(-1, MILLISECONDS), is(true));
        assertThat("Unexpected item found.", iterator.next(-1, MILLISECONDS), is(1));
        assertThat("Unexpected item found.", iterator.next(-1, MILLISECONDS), is(2));

        assertThrows(TimeoutException.class, () -> iterator.hasNext(10, MILLISECONDS));
        assertThat("Unexpected item found.", iterator.hasNext(-1, MILLISECONDS), is(false));
        assertTrue(subscription.isCancelled());
    }

    @Test
    void nextWithTimeout() throws Exception {
        BlockingIterator<Integer> iterator = source.toIterable().iterator();
        assertTrue(source.isSubscribed());
        TestSubscription subscription = new TestSubscription();
        source.onSubscribe(subscription);
        source.onNext(1, 2);
        assertThat("hasNext timed out.", iterator.hasNext(-1, MILLISECONDS), is(true));
        assertThat("Unexpected item found.", iterator.next(-1, MILLISECONDS), is(1));
        assertThat("Unexpected item found.", iterator.next(-1, MILLISECONDS), is(2));

        assertThrows(TimeoutException.class, () -> iterator.next(10, MILLISECONDS));

        assertThat("Unexpected item found.", iterator.hasNext(-1, MILLISECONDS), is(false));
        assertTrue(subscription.isCancelled());
    }

    @Test
    void cancelShouldTerminatePostDrain() throws Exception {
        BlockingIterator<Integer> iterator = source.toIterable().iterator();
        assertTrue(source.isSubscribed());
        source.onNext(1, 2);
        iterator.close();
        assertThat("Unexpected item found.", iterator.next(), is(1));
        assertThat("Unexpected item found.", iterator.next(), is(2));
        assertThat("Item not expected but found.", iterator.hasNext(), is(false));
    }

    @Test
    void cancelShouldTerminatePostDrainAndRejectSubsequentItems() throws Exception {
        BlockingIterator<Integer> iterator = source.toIterable().iterator();
        assertTrue(source.isSubscribed());
        source.onNext(1, 2);
        iterator.close();
        source.onNext(1); // Additional item, must be ignored.
        assertThat("Unexpected item found.", iterator.next(), is(1));
        assertThat("Unexpected item found.", iterator.next(), is(2));
        assertThat("Item not expected but found.", iterator.hasNext(), is(false));
    }

    @Test
    void nextWithoutHasNext() {
        Iterator<Integer> iterator = source.toIterable().iterator();
        assertTrue(source.isSubscribed());
        source.onNext(1);
        assertThat("Unexpected item found.", iterator.next(), is(1));
        source.onNext(2);
        assertThat("Unexpected item found.", iterator.next(), is(2));
        source.onComplete();
        assertThat("Item not expected but found.", iterator.hasNext(), is(false));
    }

    @Test
    void nextWithoutHasNextAndTerminal() {
        Iterator<Integer> iterator = source.toIterable().iterator();
        assertTrue(source.isSubscribed());
        source.onNext(1);
        assertThat("Unexpected item found.", iterator.next(), is(1));
        source.onNext(2);
        assertThat("Unexpected item found.", iterator.next(), is(2));
        source.onComplete();
        assertThrows(NoSuchElementException.class, () -> iterator.next());
    }

    @Test
    void nextWithTimeoutWithoutHasNextAndTerminal() {
        BlockingIterator<Integer> iterator = source.toIterable().iterator();
        assertTrue(source.isSubscribed());
        source.onNext(1);
        assertThat("Unexpected item found.", iterator.next(), is(1));
        source.onNext(2);
        assertThat("Unexpected item found.", iterator.next(), is(2));
        source.onComplete();
        assertThrows(NoSuchElementException.class, () -> iterator.next(10, MILLISECONDS));
    }

    @Test
    void errorEmittedIsThrownAfterEmittingAllItems() {
        DeliberateException de = new DeliberateException();
        Iterator<Integer> iterator = from(1, 2, 3, 4)
                .concat(Publisher.failed(de)).toIterable().iterator();
        List<Integer> result = new ArrayList<>(4);
        try {
            while (iterator.hasNext()) {
                result.add(iterator.next());
            }
            fail("Exception expected but not thrown.");
        } catch (DeliberateException e) {
            assertThat("Unexpected exception.", e, sameInstance(de));
            assertThat("Unexpected result.", result, contains(1, 2, 3, 4));
        }
    }

    @Test
    void delayOnNextThenComplete() {
        Iterator<Integer> iterator = source.toIterable(2).iterator();
        TestSubscription subscription = new TestSubscription();
        source.onSubscribe(subscription);
        assertTrue(source.isSubscribed());
        assertThat(subscription.requested(), is((long) 2));
        source.onNext(1);
        verifyNextIs(iterator, 1);
        source.onNext(2);
        verifyNextIs(iterator, 2);
        source.onComplete();
        assertThat("Item not expected but found.", iterator.hasNext(), is(false));
    }

    @Test
    void delayOnNextThenError() {
        Iterator<Integer> iterator = source.toIterable(2).iterator();
        TestSubscription subscription = new TestSubscription();
        source.onSubscribe(subscription);
        assertTrue(source.isSubscribed());
        assertThat(subscription.requested(), is((long) 2));
        source.onNext(1);
        verifyNextIs(iterator, 1);
        source.onNext(2);
        verifyNextIs(iterator, 2);
        DeliberateException de = new DeliberateException();
        source.onError(de);
        assertThat("Item not expected but found.", iterator.hasNext(), is(true));
        Exception e = assertThrows(DeliberateException.class, () -> iterator.next());
        assertThat(e, is(de));
    }

    @Test
    void verifyRequestedReplenishedCapacityAs1() {
        Iterator<Integer> iterator = source.toIterable(1).iterator();
        TestSubscription subscription = new TestSubscription();
        source.onSubscribe(subscription);
        assertTrue(source.isSubscribed());
        assertThat(subscription.requested(), is((long) 1));
        source.onNext(1);
        verifyNextIs(iterator, 1);
        assertThat(subscription.requested(), is((long) 2));
        source.onNext(2);
        verifyNextIs(iterator, 2);
    }

    @Test
    void verifyRequestedReplenishedOddQueueCapacity() {
        Iterator<Integer> iterator = source.toIterable(5).iterator();
        TestSubscription subscription = new TestSubscription();
        source.onSubscribe(subscription);
        assertTrue(source.isSubscribed());
        assertThat(subscription.requested(), is((long) 5));
        source.onNext(1, 2);
        verifyNextIs(iterator, 1);
        verifyNextIs(iterator, 2);
        source.onNext(3);
        verifyNextIs(iterator, 3);
        assertThat(subscription.requested(), is((long) 8));
    }

    @Test
    void verifyRequestedReplenishedEvenQueueCapacity() {
        Iterator<Integer> iterator = source.toIterable(4).iterator();
        TestSubscription subscription = new TestSubscription();
        source.onSubscribe(subscription);
        assertTrue(source.isSubscribed());
        assertThat(subscription.requested(), is((long) 4));
        source.onNext(1);
        verifyNextIs(iterator, 1);
        assertThat(subscription.requested(), is((long) 4));
        source.onNext(2);
        verifyNextIs(iterator, 2);
        assertThat(subscription.requested(), is((long) 6)); /*4 + 2*/
    }

    @Test
    void verifyFullRequestedSatisfiedAndRequestMore() {
        Iterator<Integer> iterator = source.toIterable(2).iterator();
        TestSubscription subscription = new TestSubscription();
        source.onSubscribe(subscription);
        assertTrue(source.isSubscribed());
        assertThat(subscription.requested(), is((long) 2));
        source.onNext(1, 2);
        verifyNextIs(iterator, 1);
        verifyNextIs(iterator, 2);
        assertThat(subscription.requested(), is((long) 4));
        source.onNext(2);
        verifyNextIs(iterator, 2);
        assertThat(subscription.requested(), is((long) 5)); /*4 + 1*/
    }

    @Test
    void queueFullButAccommodatesOnError() {
        Iterator<Integer> iterator = source.toIterable(1).iterator();
        TestSubscription subscription = new TestSubscription();
        source.onSubscribe(subscription);
        assertTrue(source.isSubscribed());
        assertThat(subscription.requested(), is((long) 1));
        source.onNext(1);
        DeliberateException de = new DeliberateException();
        source.onError(de);
        verifyNextIs(iterator, 1);
        assertThat("Item expected but not found.", iterator.hasNext(), is(true));
        Exception e = assertThrows(DeliberateException.class, () -> iterator.next());
        assertThat(e, sameInstance(de));
    }

    @Test
    void queueFullButAccommodatesOnComplete() {
        Iterator<Integer> iterator = source.toIterable(1).iterator();
        TestSubscription subscription = new TestSubscription();
        source.onSubscribe(subscription);
        assertTrue(source.isSubscribed());
        assertThat(subscription.requested(), is((long) 1));
        source.onNext(1);
        source.onComplete();
        verifyNextIs(iterator, 1);
        assertThat("Item expected but not found.", iterator.hasNext(), is(false));
    }

    @Test
    void queueFullButAccommodatesCancel() throws Exception {
        BlockingIterator<Integer> iterator = source.toIterable(1).iterator();
        TestSubscription subscription = new TestSubscription();
        source.onSubscribe(subscription);
        assertTrue(source.isSubscribed());
        assertThat(subscription.requested(), is((long) 1));
        source.onNext(1);
        iterator.close();
        verifyNextIs(iterator, 1);
        assertThat("Item expected but not found.", iterator.hasNext(), is(false));
    }

    @Test
    void replenishingRequestedShouldHonourQueueContents() {
        Iterator<Integer> iterator = source.toIterable(2).iterator();
        TestSubscription subscription = new TestSubscription();
        source.onSubscribe(subscription);
        assertTrue(source.isSubscribed());
        assertThat(subscription.requested(), is((long) 2));
        source.onNext(1);
        // Since, we have not consumed any item, we should not be requesting more.
        assertThat(subscription.requested(), is((long) 2));
        // Check the next item, it should be dequeued and prepared for the next().
        assertThat("Item expected but not found.", iterator.hasNext(), is(true));
        // Queue is half full, we should now be requesting more.
        assertThat(subscription.requested(), is((long) 3));
        // Deliver all requested items.
        source.onNext(2, 3);
        // Now take already dequeued item.
        assertThat("Unexpected item found.", iterator.next(), is(1));
        // Verify all other items.
        verifyNextIs(iterator, 2);
        verifyNextIs(iterator, 3);
        assertThat(subscription.requested(), is((long) 5));
        source.onComplete();
        assertThat("Item not expected but found.", iterator.hasNext(), is(false));
    }

    @Test
    void spscThreads() throws ExecutionException, InterruptedException {
        Executor executor = Executors.newCachedThreadExecutor();
        try {
            int nextExpected = 0;
            for (Integer integer : Publisher.range(0, 1000000)
                    .publishOn(executor)
                    .toIterable(Integer.MAX_VALUE)) {
                assertThat(integer, equalTo(nextExpected++));
            }
        } finally {
            executor.closeAsync().toFuture().get();
        }
    }

    @Test
    void nullShouldBeEmitted() {
        Iterator<Void> iterator = Publisher.from((Void) null).toIterable().iterator();
        assertThat("Item expected but not found.", iterator.hasNext(), is(true));
        assertThat("Unexpected item found.", iterator.next(), is(nullValue()));
    }

    private void verifyNextIs(final Iterator<Integer> iterator, final int expected) {
        assertThat("Item expected but not found.", iterator.hasNext(), is(true));
        assertThat("Unexpected item found.", iterator.next(), is(expected));
    }
}

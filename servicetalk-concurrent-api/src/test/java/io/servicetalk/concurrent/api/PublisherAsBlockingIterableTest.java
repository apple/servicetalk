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

import io.servicetalk.concurrent.BlockingIterator;
import io.servicetalk.concurrent.internal.DeliberateException;
import io.servicetalk.concurrent.internal.QueueFullException;
import io.servicetalk.concurrent.internal.ServiceTalkTestTimeout;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Spliterator;
import java.util.concurrent.TimeoutException;

import static io.servicetalk.concurrent.api.Publisher.from;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static java.util.stream.Collectors.toList;
import static java.util.stream.StreamSupport.stream;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.instanceOf;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;
import static org.hamcrest.Matchers.sameInstance;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.junit.rules.ExpectedException.none;

public final class PublisherAsBlockingIterableTest {

    @Rule
    public final ExpectedException expected = none();
    @Rule
    public final ServiceTalkTestTimeout timeout = new ServiceTalkTestTimeout();

    private final TestPublisher<Integer> source = new TestPublisher<>();

    @Test
    public void subscribeDelayedTillIterator() {
        Iterable<Integer> iterable = source.toIterable();
        assertFalse(source.isSubscribed());
        iterable.iterator();
        assertTrue(source.isSubscribed());
    }

    @Test
    public void removeNotSupported() {
        expected.expect(instanceOf(UnsupportedOperationException.class));
        source.toIterable().iterator().remove();
    }

    @Test
    public void allItemsAreReturned() {
        Spliterator<Integer> iterator = from(1, 2, 3, 4).toIterable().spliterator();
        List<Integer> result = stream(iterator, false).collect(toList());
        assertThat("Unexpected result.", result, contains(1, 2, 3, 4));
    }

    @Test
    public void errorEmittedIsThrown() {
        DeliberateException de = new DeliberateException();
        Iterator<Integer> iterator = Publisher.<Integer>failed(de).toIterable().iterator();
        assertThat("Item expected but not found.", iterator.hasNext(), is(true));
        expected.expect(sameInstance(de));
        iterator.next();
    }

    @Test
    public void doubleHashNextWithError() {
        DeliberateException de = new DeliberateException();
        Iterator<Integer> iterator = Publisher.<Integer>failed(de)
                .toIterable().iterator();
        assertThat("Item expected but not found.", iterator.hasNext(), is(true));
        assertThat("Second hasNext inconsistent with first.", iterator.hasNext(), is(true));
        expected.expect(sameInstance(de));
        iterator.next();
    }

    @Test
    public void hasNextWithEmpty() {
        Iterator<Integer> iterator = Publisher.<Integer>empty().toIterable().iterator();
        assertThat("Item not expected but found.", iterator.hasNext(), is(false));
    }

    @Test
    public void nextWithEmpty() {
        Iterator<Integer> iterator = Publisher.<Integer>empty().toIterable().iterator();
        assertThat("Item not expected but found.", iterator.hasNext(), is(false));
        expected.expect(instanceOf(NoSuchElementException.class));
        iterator.next();
    }

    @Test
    public void hasNextWithTimeout() throws Exception {
        BlockingIterator<Integer> iterator = source.toIterable().iterator();
        assertTrue(source.isSubscribed());
        TestSubscription subscription = new TestSubscription();
        source.onSubscribe(subscription);
        source.onNext(1, 2);
        assertThat("hasNext timed out.", iterator.hasNext(-1, MILLISECONDS), is(true));
        assertThat("Unexpected item found.", iterator.next(-1, MILLISECONDS), is(1));
        assertThat("Unexpected item found.", iterator.next(-1, MILLISECONDS), is(2));
        expected.expect(instanceOf(TimeoutException.class));
        try {
            iterator.hasNext(10, MILLISECONDS);
            fail("expected exception");
        } catch (TimeoutException e) {
            assertThat("Unexpected item found.", iterator.hasNext(-1, MILLISECONDS), is(false));
            assertTrue(subscription.isCancelled());
            throw e;
        }
    }

    @Test
    public void nextWithTimeout() throws Exception {
        BlockingIterator<Integer> iterator = source.toIterable().iterator();
        assertTrue(source.isSubscribed());
        TestSubscription subscription = new TestSubscription();
        source.onSubscribe(subscription);
        source.onNext(1, 2);
        assertThat("hasNext timed out.", iterator.hasNext(-1, MILLISECONDS), is(true));
        assertThat("Unexpected item found.", iterator.next(-1, MILLISECONDS), is(1));
        assertThat("Unexpected item found.", iterator.next(-1, MILLISECONDS), is(2));
        expected.expect(instanceOf(TimeoutException.class));
        try {
            iterator.next(10, MILLISECONDS);
        } catch (TimeoutException e) {
            assertThat("Unexpected item found.", iterator.hasNext(-1, MILLISECONDS), is(false));
            assertTrue(subscription.isCancelled());
            throw e;
        }
    }

    @Test
    public void cancelShouldTerminatePostDrain() throws Exception {
        BlockingIterator<Integer> iterator = source.toIterable().iterator();
        assertTrue(source.isSubscribed());
        source.onNext(1, 2);
        iterator.close();
        assertThat("Unexpected item found.", iterator.next(), is(1));
        assertThat("Unexpected item found.", iterator.next(), is(2));
        assertThat("Item not expected but found.", iterator.hasNext(), is(false));
    }

    @Test
    public void cancelShouldTerminatePostDrainAndRejectSubsequentItems() throws Exception {
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
    public void nextWithoutHasNext() {
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
    public void nextWithoutHasNextAndTerminal() {
        Iterator<Integer> iterator = source.toIterable().iterator();
        assertTrue(source.isSubscribed());
        source.onNext(1);
        assertThat("Unexpected item found.", iterator.next(), is(1));
        source.onNext(2);
        assertThat("Unexpected item found.", iterator.next(), is(2));
        source.onComplete();
        expected.expect(instanceOf(NoSuchElementException.class));
        iterator.next();
    }

    @Test
    public void nextWithTimeoutWithoutHasNextAndTerminal() throws TimeoutException {
        BlockingIterator<Integer> iterator = source.toIterable().iterator();
        assertTrue(source.isSubscribed());
        source.onNext(1);
        assertThat("Unexpected item found.", iterator.next(), is(1));
        source.onNext(2);
        assertThat("Unexpected item found.", iterator.next(), is(2));
        source.onComplete();
        expected.expect(instanceOf(NoSuchElementException.class));
        iterator.next(10, MILLISECONDS);
    }

    @Test
    public void errorEmittedIsThrownAfterEmittingAllItems() {
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
    public void delayOnNextThenComplete() {
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
    public void delayOnNextThenError() {
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
        expected.expect(is(de));
        iterator.next();
    }

    @Test
    public void verifyRequestedReplenishedCapacityAs1() {
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
    public void verifyRequestedReplenishedOddQueueCapacity() {
        Iterator<Integer> iterator = source.toIterable(5).iterator();
        TestSubscription subscription = new TestSubscription();
        source.onSubscribe(subscription);
        assertTrue(source.isSubscribed());
        assertThat(subscription.requested(), is((long) 5));
        source.onNext(1, 2);
        verifyNextIs(iterator, 1);
        verifyNextIs(iterator, 2);
        assertThat(subscription.requested(), is((long) 7)); /*5 + 2*/
        source.onNext(3);
        verifyNextIs(iterator, 3);
        assertThat(subscription.requested(), is((long) 7));
    }

    @Test
    public void verifyRequestedReplenishedEvenQueueCapacity() {
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
    public void verifyFullRequestedSatisfiedAndRequestMore() {
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
    public void queueFullButAccommodatesOnError() {
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
        expected.expect(sameInstance(de));
        iterator.next();
    }

    @Test
    public void queueFullButAccommodatesOnComplete() {
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
    public void queueFullButAccommodatesCancel() throws Exception {
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
    public void replenishingRequestedShouldHonourQueueContents() {
        Iterator<Integer> iterator = source.toIterable(2).iterator();
        TestSubscription subscription = new TestSubscription();
        source.onSubscribe(subscription);
        assertTrue(source.isSubscribed());
        assertThat(subscription.requested(), is((long) 2));
        source.onNext(1);
        // Since, we have not consumed any item, we should not be requesting more.
        assertThat(subscription.requested(), is((long) 2));
        verifyNextIs(iterator, 1); // Take next item, queue is half full, we should now be requesting more.
        assertThat(subscription.requested(), is((long) 3));
        source.onNext(2, 3);
        verifyNextIs(iterator, 2);
        verifyNextIs(iterator, 3);
        assertThat(subscription.requested(), is((long) 5));
    }

    @Test
    public void onNextMoreThanRequested() {
        Iterator<Integer> itr = source.toIterable(1).iterator();
        TestSubscription subscription = new TestSubscription();
        source.onSubscribe(subscription);
        assertTrue(source.isSubscribed());
        assertThat(subscription.requested(), is((long) 1));
        source.onNext(1);
        source.onNext(2, 3, 4); // queue is capacity + 2
        expected.expect(instanceOf(QueueFullException.class));
        while (itr.hasNext()) {
            itr.next();
        }
    }

    @Test
    public void nullShouldBeEmitted() {
        Iterator<Void> iterator = Publisher.from((Void) null).toIterable().iterator();
        assertThat("Item expected but not found.", iterator.hasNext(), is(true));
        assertThat("Unexpected item found.", iterator.next(), is(nullValue()));
    }

    private void verifyNextIs(final Iterator<Integer> iterator, final int expected) {
        assertThat("Item expected but not found.", iterator.hasNext(), is(true));
        assertThat("Unexpected item found.", iterator.next(), is(expected));
    }
}

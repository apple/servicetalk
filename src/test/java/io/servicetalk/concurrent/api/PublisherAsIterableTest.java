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

import io.servicetalk.concurrent.api.PublisherAsIterable.CancellableIterator;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Spliterator;

import static io.servicetalk.concurrent.api.Executors.immediate;
import static io.servicetalk.concurrent.api.Publisher.from;
import static java.util.stream.Collectors.toList;
import static java.util.stream.StreamSupport.stream;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.instanceOf;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;
import static org.hamcrest.Matchers.sameInstance;
import static org.junit.Assert.fail;
import static org.junit.rules.ExpectedException.none;

public final class PublisherAsIterableTest {

    @Rule
    public final ExpectedException expected = none();

    @Test
    public void subscribeDelayedTillIterator() {
        TestPublisher<Integer> source = new TestPublisher<>();
        Iterable<Integer> iterable = source.toIterable();
        source.verifyNotSubscribed();
        iterable.iterator();
        source.verifySubscribed();
    }

    @Test
    public void removeNotSupported() {
        TestPublisher<Integer> source = new TestPublisher<>();
        expected.expect(instanceOf(UnsupportedOperationException.class));
        source.toIterable().iterator().remove();
    }

    @Test
    public void allItemsAreReturned() {
        Spliterator<Integer> iterator = from(immediate(), 1, 2, 3, 4).toIterable().spliterator();
        List<Integer> result = stream(iterator, false).collect(toList());
        assertThat("Unexpected result.", result, contains(1, 2, 3, 4));
    }

    @Test
    public void errorEmittedIsThrown() {
        DeliberateException de = new DeliberateException();
        Iterator<Integer> iterator = Publisher.<Integer>error(de, immediate()).toIterable().iterator();
        assertThat("Item expected but not found.", iterator.hasNext(), is(true));
        expected.expect(sameInstance(de));
        iterator.next();
    }

    @Test
    public void doubleHashNextWithError() {
        DeliberateException de = new DeliberateException();
        Iterator<Integer> iterator = Publisher.<Integer>error(de, immediate())
                .toIterable().iterator();
        assertThat("Item expected but not found.", iterator.hasNext(), is(true));
        assertThat("Second hasNext inconsistent with first.", iterator.hasNext(), is(true));
        expected.expect(sameInstance(de));
        iterator.next();
    }

    @Test
    public void hasNextWithEmpty() {
        Iterator<Integer> iterator = Publisher.<Integer>empty(immediate()).toIterable().iterator();
        assertThat("Item not expected but found.", iterator.hasNext(), is(false));
    }

    @Test
    public void nextWithEmpty() {
        Iterator<Integer> iterator = Publisher.<Integer>empty(immediate()).toIterable().iterator();
        assertThat("Item not expected but found.", iterator.hasNext(), is(false));
        expected.expect(instanceOf(NoSuchElementException.class));
        iterator.next();
    }

    @Test
    public void cancelShouldTerminatePostDrain() {
        TestPublisher<Integer> source = new TestPublisher<>();
        CancellableIterator<Integer> iterator = (CancellableIterator<Integer>) source.toIterable().iterator();
        source.verifySubscribed().sendOnSubscribe();
        source.sendItems(1, 2);
        iterator.cancel();
        assertThat("Unexpected item found.", iterator.next(), is(1));
        assertThat("Unexpected item found.", iterator.next(), is(2));
        assertThat("Item not expected but found.", iterator.hasNext(), is(false));
    }

    @Test
    public void cancelShouldTerminatePostDrainAndRejectSubsequentItems() {
        TestPublisher<Integer> source = new TestPublisher<>(true);
        CancellableIterator<Integer> iterator = (CancellableIterator<Integer>) source.toIterable().iterator();
        source.verifySubscribed().sendOnSubscribe();
        source.sendItems(1, 2);
        iterator.cancel();
        source.sendItems(1); // Additional item, must be ignored.
        assertThat("Unexpected item found.", iterator.next(), is(1));
        assertThat("Unexpected item found.", iterator.next(), is(2));
        assertThat("Item not expected but found.", iterator.hasNext(), is(false));
    }

    @Test
    public void nextWithoutHasNext() {
        TestPublisher<Integer> source = new TestPublisher<>();
        Iterator<Integer> iterator = source.toIterable().iterator();
        source.verifySubscribed().sendOnSubscribe();
        source.onNext(1);
        assertThat("Unexpected item found.", iterator.next(), is(1));
        source.onNext(2);
        assertThat("Unexpected item found.", iterator.next(), is(2));
        source.onComplete();
        assertThat("Item not expected but found.", iterator.hasNext(), is(false));
    }

    @Test
    public void nextWithoutHasNextAndTerminal() {
        TestPublisher<Integer> source = new TestPublisher<>();
        Iterator<Integer> iterator = source.toIterable().iterator();
        source.verifySubscribed().sendOnSubscribe();
        source.onNext(1);
        assertThat("Unexpected item found.", iterator.next(), is(1));
        source.onNext(2);
        assertThat("Unexpected item found.", iterator.next(), is(2));
        source.onComplete();
        expected.expect(instanceOf(NoSuchElementException.class));
        iterator.next();
    }

    @Test
    public void errorEmittedIsThrownAfterEmittingAllItems() {
        DeliberateException de = new DeliberateException();
        Iterator<Integer> iterator = from(immediate(), 1, 2, 3, 4)
                .concatWith(Publisher.error(de, immediate())).toIterable().iterator();
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
        TestPublisher<Integer> source = new TestPublisher<>();
        Iterator<Integer> iterator = source.toIterable(2).iterator();
        source.verifySubscribed().sendOnSubscribe().verifyRequested(2);
        source.onNext(1);
        verifyNextIs(iterator, 1);
        source.onNext(2);
        verifyNextIs(iterator, 2);
        source.onComplete();
        assertThat("Item not expected but found.", iterator.hasNext(), is(false));
    }

    @Test
    public void delayOnNextThenError() {
        TestPublisher<Integer> source = new TestPublisher<>();
        Iterator<Integer> iterator = source.toIterable(2).iterator();
        source.verifySubscribed().sendOnSubscribe().verifyRequested(2);
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
        TestPublisher<Integer> source = new TestPublisher<>();
        Iterator<Integer> iterator = source.toIterable(1).iterator();
        source.verifySubscribed().sendOnSubscribe().verifyRequested(1);
        source.sendItems(1).verifyRequested(2).sendItems(2);
        verifyNextIs(iterator, 1);
        verifyNextIs(iterator, 2);
    }

    @Test
    public void verifyRequestedReplenishedOddQueueCapacity() {
        TestPublisher<Integer> source = new TestPublisher<>();
        Iterator<Integer> iterator = source.toIterable(5).iterator();
        source.verifySubscribed().sendOnSubscribe().verifyRequested(5);
        source.sendItems(1, 2);
        verifyNextIs(iterator, 1);
        verifyNextIs(iterator, 2);
        source.verifyRequested(5)
                .verifyOutstanding(3)
                .sendItems(1)
                .verifyRequested(8) /*5 + 3*/;
    }

    @Test
    public void verifyRequestedReplenishedEvenQueueCapacity() {
        TestPublisher<Integer> source = new TestPublisher<>();
        Iterator<Integer> iterator = source.toIterable(4).iterator();
        source.verifySubscribed().sendOnSubscribe().verifyRequested(4);
        source.sendItems(1);
        verifyNextIs(iterator, 1);
        source.verifyRequested(4)
                .verifyOutstanding(3)
                .sendItems(2)
                .verifyRequested(6)/*4 + 2*/;
        verifyNextIs(iterator, 2);
    }

    @Test
    public void verifyFullRequestedSatisfiedAndRequestMore() {
        TestPublisher<Integer> source = new TestPublisher<>();
        Iterator<Integer> iterator = source.toIterable(2).iterator();
        source.verifySubscribed().sendOnSubscribe().verifyRequested(2);
        source.sendItems(1, 2);
        verifyNextIs(iterator, 1);
        verifyNextIs(iterator, 2);
        source.verifyRequested(4)
                .verifyOutstanding(2)
                .sendItems(2)
                .verifyRequested(5)/*4 + 2*/;
        verifyNextIs(iterator, 2);
    }

    @Test
    public void queueFullButAccommodatesOnError() {
        TestPublisher<Integer> source = new TestPublisher<>();
        Iterator<Integer> iterator = source.toIterable(1).iterator();
        DeliberateException de = new DeliberateException();
        source.verifySubscribed().sendOnSubscribe().verifyRequested(1).sendItems(1).onError(de);
        verifyNextIs(iterator, 1);
        assertThat("Item expected but not found.", iterator.hasNext(), is(true));
        expected.expect(sameInstance(de));
        iterator.next();
    }

    @Test
    public void queueFullButAccommodatesOnComplete() {
        TestPublisher<Integer> source = new TestPublisher<>();
        Iterator<Integer> iterator = source.toIterable(1).iterator();
        source.verifySubscribed().sendOnSubscribe().verifyRequested(1).sendItems(1).onComplete();
        verifyNextIs(iterator, 1);
        assertThat("Item expected but not found.", iterator.hasNext(), is(false));
    }

    @Test
    public void queueFullButAccommodatesCancel() {
        TestPublisher<Integer> source = new TestPublisher<>();
        CancellableIterator<Integer> iterator = (CancellableIterator<Integer>) source.toIterable(1)
                .iterator();
        source.verifySubscribed().sendOnSubscribe().verifyRequested(1).sendItems(1);
        iterator.cancel();
        verifyNextIs(iterator, 1);
        assertThat("Item expected but not found.", iterator.hasNext(), is(false));
    }

    @Test
    public void onNextMoreThanRequested() {
        TestPublisher<Integer> source = new TestPublisher<>();
        source.toIterable(1).iterator();
        source.verifySubscribed().sendOnSubscribe().verifyRequested(1).sendItems(1);
        expected.expect(instanceOf(QueueFullException.class));
        source.sendItemsNoDemandCheck(2, 3, 4); // queue is capacity + 2
    }

    @Test
    public void nullShouldBeEmitted() {
        @SuppressWarnings("ConstantConditions")
        Iterator<Void> iterator = Publisher.<Void>just(null, immediate()).toIterable().iterator();
        assertThat("Item expected but not found.", iterator.hasNext(), is(true));
        assertThat("Unexpected item found.", iterator.next(), is(nullValue()));
    }

    private void verifyNextIs(final Iterator<Integer> iterator, final int expected) {
        assertThat("Item expected but not found.", iterator.hasNext(), is(true));
        assertThat("Unexpected item found.", iterator.next(), is(expected));
    }
}

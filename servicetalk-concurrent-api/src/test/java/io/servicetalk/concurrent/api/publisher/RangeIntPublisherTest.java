/*
 * Copyright © 2020-2021 Apple Inc. and the ServiceTalk project authors
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
package io.servicetalk.concurrent.api.publisher;

import io.servicetalk.concurrent.test.internal.TestPublisherSubscriber;

import org.junit.jupiter.api.Test;

import static io.servicetalk.concurrent.api.Publisher.range;
import static io.servicetalk.concurrent.api.SourceAdapters.toSource;
import static io.servicetalk.concurrent.internal.DeliberateException.DELIBERATE_EXCEPTION;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;
import static org.hamcrest.Matchers.sameInstance;

class RangeIntPublisherTest {
    final TestPublisherSubscriber<Integer> subscriber = new TestPublisherSubscriber<>();

    @Test
    void zeroElements() {
        toSource(range(Integer.MAX_VALUE, Integer.MAX_VALUE)).subscribe(subscriber);
        subscriber.awaitSubscription().request(1);
        subscriber.awaitOnComplete();
    }

    @Test
    void zeroElementsStride() {
        toSource(range(-1, -1, 2)).subscribe(subscriber);
        subscriber.awaitSubscription().request(1);
        subscriber.awaitOnComplete();
    }

    @Test
    void singleElement() {
        toSource(range(-1, 0)).subscribe(subscriber);
        subscriber.awaitSubscription().request(1);
        assertThat(subscriber.takeOnNext(), is(-1));
        subscriber.awaitOnComplete();
    }

    @Test
    void singleElementStride() {
        toSource(range(0, 1, 2)).subscribe(subscriber);
        subscriber.awaitSubscription().request(1);
        assertThat(subscriber.takeOnNext(), is(0));
        subscriber.awaitOnComplete();
    }

    @Test
    void multipleElements() {
        toSource(range(0, 5)).subscribe(subscriber);
        subscriber.awaitSubscription().request(5);
        assertThat(subscriber.takeOnNext(5), contains(0, 1, 2, 3, 4));
        subscriber.awaitOnComplete();
    }

    @Test
    void multipleElementsStride() {
        toSource(range(0, 10, 3)).subscribe(subscriber);
        subscriber.awaitSubscription().request(4);
        assertThat(subscriber.takeOnNext(4), contains(0, 3, 6, 9));
        subscriber.awaitOnComplete();
    }

    @Test
    void overflowStride() {
        int begin = Integer.MAX_VALUE - 1;
        toSource(range(begin, Integer.MAX_VALUE, 10)).subscribe(subscriber);
        subscriber.awaitSubscription().request(1);
        assertThat(subscriber.takeOnNext(), is(begin));
        subscriber.awaitOnComplete();
    }

    @Test
    void negativeToPositive() {
        toSource(range(-2, 3)).subscribe(subscriber);
        subscriber.awaitSubscription().request(5);
        assertThat(subscriber.takeOnNext(5), contains(-2, -1, 0, 1, 2));
        subscriber.awaitOnComplete();
    }

    @Test
    void negativeToPositiveStride() {
        toSource(range(-1, Integer.MAX_VALUE, Integer.MAX_VALUE)).subscribe(subscriber);
        subscriber.awaitSubscription().request(2);
        assertThat(subscriber.takeOnNext(2), contains(-1, Integer.MAX_VALUE - 1));
        subscriber.awaitOnComplete();
    }

    @Test
    void allNegative() {
        toSource(range(-10, -5)).subscribe(subscriber);
        subscriber.awaitSubscription().request(5);
        assertThat(subscriber.takeOnNext(5), contains(-10, -9, -8, -7, -6));
        subscriber.awaitOnComplete();
    }

    @Test
    void allNegativeStride() {
        toSource(range(-20, -10, 2)).subscribe(subscriber);
        subscriber.awaitSubscription().request(5);
        assertThat(subscriber.takeOnNext(5), contains(-20, -18, -16, -14, -12));
        subscriber.awaitOnComplete();
    }

    @Test
    void thrownExceptionTerminatesOnError() {
        thrownExceptionTerminatesOnError(false);
    }

    @Test
    void thrownExceptionTerminatesOnErrorStride() {
        thrownExceptionTerminatesOnError(true);
    }

    private void thrownExceptionTerminatesOnError(boolean stride) {
        toSource((stride ? range(0, 5, 2) : range(0, 5)).whenOnNext(n -> {
            throw DELIBERATE_EXCEPTION;
        })).subscribe(subscriber);
        subscriber.awaitSubscription().request(1);
        assertThat(subscriber.awaitOnError(), sameInstance(DELIBERATE_EXCEPTION));
    }

    @Test
    void reentrantDeliversInOrder() {
        toSource(range(0, 5).whenOnNext(n -> subscriber.awaitSubscription().request(1))).subscribe(subscriber);
        subscriber.awaitSubscription().request(1);
        assertThat(subscriber.takeOnNext(5), contains(0, 1, 2, 3, 4));
        subscriber.awaitOnComplete();
    }

    @Test
    void reentrantDeliversInOrderStride() {
        toSource(range(5, 15, 4).whenOnNext(n -> subscriber.awaitSubscription().request(1))).subscribe(subscriber);
        subscriber.awaitSubscription().request(1);
        assertThat(subscriber.takeOnNext(3), contains(5, 9, 13));
        subscriber.awaitOnComplete();
    }

    @Test
    void reentrantCancelStopsDelivery() {
        reentrantCancelStopsDelivery(true);
    }

    @Test
    void reentrantCancelStopsDeliveryStride() {
        reentrantCancelStopsDelivery(false);
    }

    private void reentrantCancelStopsDelivery(boolean stride) {
        toSource((stride ? range(0, 5, 2) : range(0, 5)).whenOnNext(n -> {
            if (n != null && n == 0) {
                subscriber.awaitSubscription().cancel();
            }
        })).subscribe(subscriber);
        subscriber.awaitSubscription().request(5);
        assertThat(subscriber.takeOnNext(), is(0));
        assertThat(subscriber.pollTerminal(10, MILLISECONDS), is(nullValue()));
    }

    @Test
    void requestNAfterCancelIsNoop() {
        requestNAfterCancelIsNoop(false);
    }

    @Test
    void requestNAfterCancelIsNoopStride() {
        requestNAfterCancelIsNoop(true);
    }

    private void requestNAfterCancelIsNoop(boolean stride) {
        toSource((stride ? range(0, 5, 2) : range(0, 5)).whenOnNext(n -> {
            if (n != null && n == 0) {
                subscriber.awaitSubscription().cancel();
            }
        })).subscribe(subscriber);
        subscriber.awaitSubscription().request(1);
        assertThat(subscriber.takeOnNext(), is(0));
        subscriber.awaitSubscription().request(Long.MAX_VALUE);
        assertThat(subscriber.pollOnNext(10, MILLISECONDS), is(nullValue()));
    }
}

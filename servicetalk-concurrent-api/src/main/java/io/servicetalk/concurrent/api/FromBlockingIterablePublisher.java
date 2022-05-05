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

import io.servicetalk.concurrent.BlockingIterable;
import io.servicetalk.concurrent.BlockingIterator;
import io.servicetalk.concurrent.api.FromIterablePublisher.FromIterableSubscription;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.LongSupplier;
import javax.annotation.Nullable;

import static io.servicetalk.concurrent.internal.SubscriberUtils.handleExceptionFromOnSubscribe;
import static java.util.Objects.requireNonNull;

final class FromBlockingIterablePublisher<T> extends AbstractSynchronousPublisher<T> {

    private final BlockingIterable<? extends T> iterable;
    private final LongSupplier timeoutSupplier;
    private final TimeUnit unit;

    @SuppressWarnings("unchecked")
    static <T> Publisher<T> fromBlockingIterable0(
            BlockingIterable<? extends T> iterable, LongSupplier timeoutSupplier, TimeUnit unit) {
        // Unwrap and grab the Publisher directly if possible to avoid conversion layers.
        return iterable instanceof PublisherAsBlockingIterable ? ((PublisherAsBlockingIterable<T>) iterable).original :
                new FromBlockingIterablePublisher<>(iterable, timeoutSupplier, unit);
    }

    private FromBlockingIterablePublisher(
            final BlockingIterable<? extends T> iterable, final LongSupplier timeoutSupplier, final TimeUnit unit) {
        this.iterable = requireNonNull(iterable);
        this.timeoutSupplier = requireNonNull(timeoutSupplier);
        this.unit = requireNonNull(unit);
    }

    @Override
    void doSubscribe(final Subscriber<? super T> subscriber) {
        try {
            subscriber.onSubscribe(new FromBlockingIterableSubscription<>(iterable.iterator(), subscriber, this));
        } catch (Throwable t) {
            handleExceptionFromOnSubscribe(subscriber, t);
        }
    }

    private static final class FromBlockingIterableSubscription<T> extends
                                                         FromIterableSubscription<T, BlockingIterator<? extends T>> {
        private final FromBlockingIterablePublisher<T> iterablePublisher;

        FromBlockingIterableSubscription(final BlockingIterator<? extends T> iterator,
                                         final Subscriber<? super T> subscriber,
                                         final FromBlockingIterablePublisher<T> iterablePublisher) {
            super(iterator, subscriber);
            this.iterablePublisher = iterablePublisher;
        }

        @Override
        boolean hasNext(final BlockingIterator<? extends T> iterator) throws TimeoutException {
            return iterator.hasNext(iterablePublisher.timeoutSupplier.getAsLong(), iterablePublisher.unit);
        }

        @Nullable
        @Override
        T next(final BlockingIterator<? extends T> iterator) throws TimeoutException {
            return iterator.next(iterablePublisher.timeoutSupplier.getAsLong(), iterablePublisher.unit);
        }
    }
}

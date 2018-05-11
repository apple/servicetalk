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

import org.reactivestreams.Subscriber;
import org.reactivestreams.Subscription;

import java.util.Iterator;
import java.util.concurrent.TimeoutException;

import static io.servicetalk.concurrent.api.AutoClosableUtils.closeAndReThrowUnchecked;
import static io.servicetalk.concurrent.internal.FlowControlUtil.addWithOverflowProtection;
import static io.servicetalk.concurrent.internal.SubscriberUtils.isRequestNValid;
import static io.servicetalk.concurrent.internal.SubscriberUtils.newExceptionForInvalidRequestN;
import static java.util.Objects.requireNonNull;

final class FromIterablePublisher<T> extends AbstractSynchronousPublisher<T> {

    private final Iterable<T> iterable;

    FromIterablePublisher(Iterable<T> iterable) {
        this.iterable = requireNonNull(iterable);
    }

    @Override
    void doSubscribe(final Subscriber<? super T> subscriber) {
        subscriber.onSubscribe(new FromIterableSubscription<>(iterable.iterator(), subscriber));
    }

    static class FromIterableSubscription<T, I extends Iterator<T>> implements Subscription {
        private final I iterator;
        private final Subscriber<? super T> subscriber;
        private long requestN;
        private boolean ignoreRequests;

        FromIterableSubscription(I iterator, Subscriber<? super T> subscriber) {
            this.iterator = requireNonNull(iterator);
            this.subscriber = subscriber;
        }

        boolean hasNext(I iterator) throws TimeoutException {
            return iterator.hasNext();
        }

        T next(I iterator) throws TimeoutException {
            return iterator.next();
        }

        @Override
        public final void request(final long n) {
            if (!isRequestNValid(n) && requestN >= 0) {
                sendOnError(newExceptionForInvalidRequestN(n));
                return;
            }
            requestN = addWithOverflowProtection(requestN, n);
            if (ignoreRequests) {
                return;
            }
            ignoreRequests = true;
            boolean lastHasNext;
            try {
                do {
                    lastHasNext = hasNext(iterator);
                    if (!lastHasNext) {
                        break;
                    }
                    subscriber.onNext(next(iterator));
                } while (--requestN > 0);
                // We attempt to minimize the calls to hashNext because it may block, but if we have met requestN
                // demand we check to see if we can end this source immediately.
                if (requestN == 0 && lastHasNext) {
                    lastHasNext = hasNext(iterator);
                }
            } catch (Throwable cause) {
                sendOnError(cause);
                return;
            }
            if (requestN >= 0) {
                ignoreRequests = false;
            }
            if (!lastHasNext) {
                sendOnComplete();
            }
        }

        @Override
        public final void cancel() {
            cleanupForCancel();
            if (iterator instanceof AutoCloseable) {
                closeAndReThrowUnchecked((AutoCloseable) iterator);
            }
        }

        private void cleanupForCancel() {
            requestN = -1;
            ignoreRequests = true;
        }

        private void sendOnError(Throwable cause) {
            cancel();
            subscriber.onError(cause);
        }

        private void sendOnComplete() {
            cleanupForCancel();
            subscriber.onComplete();
        }
    }
}

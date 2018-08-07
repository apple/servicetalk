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

import static io.servicetalk.concurrent.internal.EmptySubscription.EMPTY_SUBSCRIPTION;
import static io.servicetalk.concurrent.internal.FlowControlUtil.addWithOverflowProtection;
import static io.servicetalk.concurrent.internal.SubscriberUtils.isRequestNValid;
import static io.servicetalk.concurrent.internal.SubscriberUtils.newExceptionForInvalidRequestN;
import static java.lang.Math.min;
import static java.util.Objects.requireNonNull;

/**
 * As returned by {@link Publisher#from(Object[])}.
 * @param <T> Type of items emitted by this {@link Publisher}.
 */
final class FromArrayPublisher<T> extends AbstractSynchronousPublisher<T> {
    private final T[] values;

    @SafeVarargs
    FromArrayPublisher(T... values) {
        this.values = requireNonNull(values);
    }

    @Override
    void doSubscribe(Subscriber<? super T> s) {
        if (values.length != 0) {
            s.onSubscribe(new FromArraySubscription<>(values, s));
        } else {
            s.onSubscribe(EMPTY_SUBSCRIPTION);
            s.onComplete();
        }
    }

    private static final class FromArraySubscription<T> implements Subscription {
        private final Subscriber<? super T> subscriber;
        private final T[] values;
        private int beginOffset;
        private int endOffset;
        private boolean ignoreRequests;

        FromArraySubscription(T[] values, Subscriber<? super T> subscriber) {
            this.values = values;
            this.subscriber = subscriber;
        }

        @Override
        public void request(long n) {
            if (!isRequestNValid(n) && endOffset >= 0) {
                sendOnError(newExceptionForInvalidRequestN(n));
                return;
            }
            endOffset = min((int) min(Integer.MAX_VALUE, addWithOverflowProtection(endOffset, n)), values.length);
            if (ignoreRequests) {
                return;
            }
            ignoreRequests = true;
            for (; beginOffset < endOffset; ++beginOffset) {
                try {
                    subscriber.onNext(values[beginOffset]);
                } catch (Throwable cause) {
                    sendOnError(cause);
                    return;
                }
            }
            if (endOffset >= 0) {
                ignoreRequests = false;
            }
            if (beginOffset == values.length) {
                sendComplete();
            }
        }

        @Override
        public void cancel() {
            // Resetting the begin/end offset to -1 prevents delivering any more data if a subscriber cancels while in
            // onNext, and also to prevent a duplicate terminal event onError if we have already terminated.
            beginOffset = endOffset = -1;
            ignoreRequests = true;
        }

        private void sendOnError(Throwable cause) {
            cancel();
            subscriber.onError(cause);
        }

        private void sendComplete() {
            cancel();
            subscriber.onComplete();
        }
    }
}

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
    FromArrayPublisher(Executor executor, T... values) {
        super(executor);
        this.values = requireNonNull(values);
    }

    @Override
    void doSubscribe(Subscriber<? super T> s) {
        s.onSubscribe(new SubscriptionImpl(s));
    }

    private final class SubscriptionImpl implements Subscription {
        private final Subscriber<? super T> subscriber;
        private int beginOffset;
        private int endOffset;
        private boolean ignoreRequests;

        SubscriptionImpl(Subscriber<? super T> s) {
            subscriber = s;
        }

        @Override
        public void request(long n) {
            if (!isRequestNValid(n) && endOffset >= 0) {
                fail(newExceptionForInvalidRequestN(n));
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
                    fail(cause);
                    return;
                }
            }
            ignoreRequests = false;
            if (beginOffset == values.length) {
                ignoreRequests = true;
                subscriber.onComplete();
            }
        }

        @Override
        public void cancel() {
            // Resetting the begin/end offset to -1 prevents delivering any more data if a subscriber cancels while in onNext.
            beginOffset = endOffset = -1;
            ignoreRequests = true;
        }

        private void fail(Throwable cause) {
            cancel();
            subscriber.onError(cause);
        }
    }
}

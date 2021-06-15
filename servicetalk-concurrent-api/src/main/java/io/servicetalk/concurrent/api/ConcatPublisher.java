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

import static java.util.Objects.requireNonNull;

/**
 * As returned by {@link Publisher#concat(Publisher)}.
 *
 * @param <T> Type of items emitted by this {@link Publisher}.
 */
final class ConcatPublisher<T> extends AbstractAsynchronousPublisherOperator<T, T> {
    private final Publisher<? extends T> next;

    ConcatPublisher(Publisher<T> original, Publisher<? extends T> next) {
        super(original);
        this.next = requireNonNull(next);
    }

    @Override
    public Subscriber<? super T> apply(Subscriber<? super T> subscriber) {
        return new ConcatSubscriber<>(subscriber, next);
    }

    private static final class ConcatSubscriber<T> implements Subscriber<T> {
        private final Subscriber<? super T> target;
        private final Publisher<? extends T> next;
        private final SequentialSubscription subscription = new SequentialSubscription();
        private boolean nextSubscribed;

        ConcatSubscriber(Subscriber<? super T> target, Publisher<? extends T> next) {
            this.target = target;
            this.next = requireNonNull(next);
        }

        @Override
        public void onSubscribe(Subscription s) {
            subscription.switchTo(s);
            if (!nextSubscribed) {
                // First onSubscribe, pass it to target.
                target.onSubscribe(subscription);
            }
        }

        @Override
        public void onNext(T t) {
            subscription.itemReceived();
            target.onNext(t);
        }

        @Override
        public void onError(Throwable t) {
            target.onError(t);
        }

        @Override
        public void onComplete() {
            if (nextSubscribed) {
                target.onComplete();
            } else {
                nextSubscribed = true;
                next.subscribeInternal(this);
            }
        }
    }
}

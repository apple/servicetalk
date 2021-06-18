/*
 * Copyright © 2021 Apple Inc. and the ServiceTalk project authors
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

import java.util.function.Predicate;
import javax.annotation.Nullable;

import static java.util.Objects.requireNonNull;

final class OnErrorCompletePublisher<T> extends AbstractSynchronousPublisherOperator<T, T> {
    private final Predicate<? super Throwable> predicate;

    OnErrorCompletePublisher(final Publisher<T> original, Predicate<? super Throwable> predicate) {
        super(original);
        this.predicate = requireNonNull(predicate);
    }

    @Override
    public Subscriber<? super T> apply(final Subscriber<? super T> subscriber) {
        return new OnErrorCompleteSubscriber<>(subscriber, predicate);
    }

    private static final class OnErrorCompleteSubscriber<T> implements Subscriber<T> {
        private final Subscriber<? super T> subscriber;
        private final Predicate<? super Throwable> predicate;

        private OnErrorCompleteSubscriber(final Subscriber<? super T> subscriber,
                                          final Predicate<? super Throwable> predicate) {
            this.subscriber = subscriber;
            this.predicate = predicate;
        }

        @Override
        public void onSubscribe(final Subscription subscription) {
            subscriber.onSubscribe(subscription);
        }

        @Override
        public void onNext(@Nullable final T t) {
            subscriber.onNext(t);
        }

        @Override
        public void onError(final Throwable t) {
            final boolean predicateResult;
            try {
                predicateResult = predicate.test(t);
            } catch (Throwable cause) {
                subscriber.onError(cause);
                return;
            }

            if (predicateResult) {
                subscriber.onComplete();
            } else {
                subscriber.onError(t);
            }
        }

        @Override
        public void onComplete() {
            subscriber.onComplete();
        }
    }
}

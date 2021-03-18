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

import io.servicetalk.concurrent.Cancellable;

import java.util.function.Predicate;

import static java.util.Objects.requireNonNull;

final class OnErrorCompleteCompletable extends AbstractSynchronousCompletableOperator {
    private final Predicate<? super Throwable> predicate;

    OnErrorCompleteCompletable(final Completable original, Predicate<? super Throwable> predicate,
                               final Executor executor) {
        super(original, executor);
        this.predicate = requireNonNull(predicate);
    }

    @Override
    public Subscriber apply(final Subscriber subscriber) {
        return new OnErrorCompleteSubscriber(subscriber, predicate);
    }

    private static final class OnErrorCompleteSubscriber implements Subscriber {
        private final Subscriber subscriber;
        private final Predicate<? super Throwable> predicate;

        private OnErrorCompleteSubscriber(final Subscriber subscriber, final Predicate<? super Throwable> predicate) {
            this.subscriber = subscriber;
            this.predicate = predicate;
        }

        @Override
        public void onSubscribe(final Cancellable cancellable) {
            subscriber.onSubscribe(cancellable);
        }

        @Override
        public void onComplete() {
            subscriber.onComplete();
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
    }
}

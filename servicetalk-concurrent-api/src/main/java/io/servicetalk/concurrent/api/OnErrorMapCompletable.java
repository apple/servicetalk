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

import java.util.function.Function;
import java.util.function.Predicate;

import static java.util.Objects.requireNonNull;

final class OnErrorMapCompletable extends AbstractSynchronousCompletableOperator {
    private final Predicate<? super Throwable> predicate;
    private final Function<? super Throwable, ? extends Throwable> mapper;

    OnErrorMapCompletable(final Completable original, Predicate<? super Throwable> predicate,
                          Function<? super Throwable, ? extends Throwable> mapper) {
        super(original);
        this.predicate = requireNonNull(predicate);
        this.mapper = requireNonNull(mapper);
    }

    @Override
    public Subscriber apply(final Subscriber subscriber) {
        return new ErrorMapSubscriber(subscriber);
    }

    private final class ErrorMapSubscriber implements Subscriber {
        private final Subscriber subscriber;

        private ErrorMapSubscriber(final Subscriber subscriber) {
            this.subscriber = subscriber;
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
                final Throwable mappedCause;
                try {
                    mappedCause = requireNonNull(mapper.apply(t));
                } catch (Throwable cause) {
                    subscriber.onError(cause);
                    return;
                }
                subscriber.onError(mappedCause);
            } else {
                subscriber.onError(t);
            }
        }
    }
}

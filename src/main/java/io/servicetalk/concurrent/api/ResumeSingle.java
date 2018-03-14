/**
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

import io.servicetalk.concurrent.Cancellable;
import io.servicetalk.concurrent.internal.SequentialCancellable;

import java.util.function.Function;

import javax.annotation.Nullable;

import static java.util.Objects.requireNonNull;

/**
 * {@link Single} as returned by {@link Single#onErrorResume(Function)}.
 */
final class ResumeSingle<T> extends Single<T> {
    private final Single<T> first;
    private final Function<Throwable, Single<T>> nextFactory;

    /**
     * New instance.
     *
     * @param first Source.
     * @param nextFactory For creating the next {@link Single}.
     */
    ResumeSingle(Single<T> first, Function<Throwable, Single<T>> nextFactory) {
        this.first = requireNonNull(first);
        this.nextFactory = requireNonNull(nextFactory);
    }

    @Override
    protected void handleSubscribe(Subscriber<? super T> subscriber) {
        first.subscribe(new SubscriberImpl<>(subscriber, nextFactory));
    }

    private static final class SubscriberImpl<T> implements Subscriber<T> {
        private final Subscriber<? super T> subscriber;
        @Nullable
        private volatile Function<Throwable, Single<T>> nextFactory;
        @Nullable
        private volatile SequentialCancellable sequentialCancellable;

        SubscriberImpl(Subscriber<? super T> subscriber, Function<Throwable, Single<T>> nextFactory) {
            this.subscriber = subscriber;
            this.nextFactory = nextFactory;
        }

        @Override
        public void onSubscribe(Cancellable cancellable) {
            SequentialCancellable sequentialCancellable = this.sequentialCancellable;
            if (sequentialCancellable == null) {
                this.sequentialCancellable = sequentialCancellable = new SequentialCancellable(cancellable);
                subscriber.onSubscribe(sequentialCancellable);
            } else {
                // Only a single re-subscribe is allowed.
                nextFactory = null;
                sequentialCancellable.setNextCancellable(cancellable);
            }
        }

        @Override
        public void onSuccess(@Nullable T result) {
            subscriber.onSuccess(result);
        }

        @Override
        public void onError(Throwable throwable) {
            final Function<Throwable, Single<T>> nextFactory = this.nextFactory;
            if (nextFactory == null) {
                subscriber.onError(throwable);
                return;
            }

            Single<T> next;
            try {
                next = requireNonNull(nextFactory.apply(throwable));
            } catch (Throwable t) {
                t.addSuppressed(throwable);
                subscriber.onError(t);
                return;
            }
            next.subscribe(this);
        }
    }
}

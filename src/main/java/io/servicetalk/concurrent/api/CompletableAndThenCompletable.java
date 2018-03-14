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

import io.servicetalk.concurrent.Cancellable;
import io.servicetalk.concurrent.internal.SequentialCancellable;

import java.util.concurrent.atomic.AtomicIntegerFieldUpdater;

import javax.annotation.Nullable;

import static java.util.Objects.requireNonNull;

/**
 * As returned by {@link Completable#andThen(Completable)}.
 */
final class CompletableAndThenCompletable extends Completable {

    private final Completable original;
    private final Completable next;

    CompletableAndThenCompletable(Completable original, Completable next) {
        this.original = requireNonNull(original);
        this.next = requireNonNull(next);
    }

    @Override
    protected void handleSubscribe(Subscriber subscriber) {
        original.subscribe(new AndThenSubscriber(subscriber, next));
    }

    private static final class AndThenSubscriber implements Subscriber {
        private static final AtomicIntegerFieldUpdater<AndThenSubscriber> subscribedToNextUpdater =
                AtomicIntegerFieldUpdater.newUpdater(AndThenSubscriber.class, "subscribedToNext");
        private final Subscriber target;
        private final Completable next;
        @Nullable
        private volatile SequentialCancellable sequentialCancellable;
        @SuppressWarnings("unused")
        private volatile int subscribedToNext;

        AndThenSubscriber(Subscriber target, Completable next) {
            this.target = target;
            this.next = next;
        }

        @Override
        public void onSubscribe(Cancellable cancellable) {
            SequentialCancellable sequentialCancellable = this.sequentialCancellable;
            if (sequentialCancellable == null) {
                this.sequentialCancellable = sequentialCancellable = new SequentialCancellable(cancellable);
                target.onSubscribe(sequentialCancellable);
            } else {
                sequentialCancellable.setNextCancellable(cancellable);
            }
        }

        @Override
        public void onComplete() {
            if (subscribedToNextUpdater.compareAndSet(this, 0, 1)) {
                next.subscribe(this);
            } else {
                target.onComplete();
            }
        }

        @Override
        public void onError(Throwable t) {
            target.onError(t);
        }
    }
}

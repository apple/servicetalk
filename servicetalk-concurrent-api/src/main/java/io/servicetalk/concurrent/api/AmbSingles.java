/*
 * Copyright © 2020 Apple Inc. and the ServiceTalk project authors
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
import io.servicetalk.concurrent.SingleSource.Subscriber;
import io.servicetalk.concurrent.internal.DelayedCancellable;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicIntegerFieldUpdater;
import javax.annotation.Nullable;

import static io.servicetalk.concurrent.internal.SubscriberUtils.handleExceptionFromOnSubscribe;
import static java.util.Objects.requireNonNull;
import static java.util.concurrent.atomic.AtomicIntegerFieldUpdater.newUpdater;

final class AmbSingles<T> extends Single<T> {
    private final Single<? extends T>[] singles;

    @SafeVarargs
    AmbSingles(final Single<? extends T>... singles) {
        for (Single<? extends T> single : singles) {
            requireNonNull(single);
        }
        this.singles = singles;
    }

    AmbSingles(final Iterable<Single<? extends T>> singles) {
        List<Single<? extends T>> allSingles = new ArrayList<>();
        for (Single<? extends T> single : singles) {
            allSingles.add(requireNonNull(single));
        }
        @SuppressWarnings({"unchecked", "SuspiciousToArrayCall"})
        Single<? extends T>[] singlesArr = (Single<? extends T>[]) allSingles.toArray(new Single[0]);
        this.singles = singlesArr;
    }

    @Override
    protected void handleSubscribe(final Subscriber<? super T> subscriber) {
        final Cancellable[] cancellables = new Cancellable[singles.length];
        final State<T> state = new State<>(subscriber);
        try {
            subscriber.onSubscribe(state);
        } catch (Throwable t) {
            handleExceptionFromOnSubscribe(subscriber, t);
            return;
        }
        try {
            for (int i = 0; i < singles.length; i++) {
                AmbSubscriber<T> sub = new AmbSubscriber<>(state);
                cancellables[i] = sub;
                singles[i].subscribeInternal(sub);
            }
        } catch (Throwable t) {
            try {
                state.delayedCancellable(CompositeCancellable.create(cancellables));
            } finally {
                state.tryError(t);
            }
            return;
        }
        state.delayedCancellable(CompositeCancellable.create(cancellables));
    }

    static final class AmbSubscriber<T> extends DelayedCancellable implements Subscriber<T> {
        private final State<T> state;

        AmbSubscriber(final State<T> state) {
            this.state = state;
        }

        @Override
        public void onSubscribe(final Cancellable cancellable) {
            delayedCancellable(cancellable);
        }

        @Override
        public void onSuccess(@Nullable final T result) {
            state.trySuccess(result);
        }

        @Override
        public void onError(final Throwable t) {
            state.tryError(t);
        }
    }

    static final class State<T> extends DelayedCancellable {
        @SuppressWarnings("rawtypes")
        private static final AtomicIntegerFieldUpdater<State> doneUpdater = newUpdater(State.class, "done");
        private final Subscriber<? super T> target;

        private volatile int done;

        State(final Subscriber<? super T> target) {
            this.target = target;
        }

        void trySuccess(@Nullable final T result) {
            if (doneUpdater.compareAndSet(this, 0, 1)) {
                // Cancel other as we got a result.
                try {
                    cancel();
                } catch (Throwable t) {
                    target.onError(t);
                    return;
                }
                target.onSuccess(result);
            }
        }

        void tryError(final Throwable t) {
            if (doneUpdater.compareAndSet(this, 0, 1)) {
                // Cancel other as we got a result.
                try {
                    cancel();
                } catch (Throwable tt) {
                    target.onError(tt);
                    return;
                }
                target.onError(t);
            }
        }
    }
}

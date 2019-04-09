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

import java.util.concurrent.atomic.AtomicIntegerFieldUpdater;

import static java.util.Objects.requireNonNull;

final class AfterFinallySingle<T> extends AbstractSynchronousSingleOperator<T, T> {

    private final Runnable runnable;

    AfterFinallySingle(Single<T> original, Runnable runnable, Executor executor) {
        super(original, executor);
        this.runnable = requireNonNull(runnable);
    }

    @Override
    public Subscriber<? super T> apply(final Subscriber<? super T> subscriber) {
        return new AfterFinallySingleSubscriber<>(subscriber, runnable);
    }

    private static final class AfterFinallySingleSubscriber<T> implements Subscriber<T> {
        private final Subscriber<? super T> original;
        private final Runnable runnable;

        private static final AtomicIntegerFieldUpdater<AfterFinallySingleSubscriber> completeUpdater =
                AtomicIntegerFieldUpdater.newUpdater(AfterFinallySingleSubscriber.class, "complete");
        @SuppressWarnings("unused")
        private volatile int complete;

        AfterFinallySingleSubscriber(Subscriber<? super T> original, Runnable runnable) {
            this.original = original;
            this.runnable = runnable;
        }

        @Override
        public void onSubscribe(Cancellable originalCancellable) {
            original.onSubscribe(new BeforeCancellable(originalCancellable, this::afterFinally));
        }

        @Override
        public void onSuccess(T value) {
            try {
                original.onSuccess(value);
            } finally {
                afterFinally();
            }
        }

        @Override
        public void onError(Throwable cause) {
            try {
                original.onError(cause);
            } finally {
                afterFinally();
            }
        }

        private void afterFinally() {
            if (completeUpdater.compareAndSet(this, 0, 1)) {
                runnable.run();
            }
        }
    }
}

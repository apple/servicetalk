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

final class DoBeforeFinallySingle<T> extends Single<T> {

    private final Single<T> original;
    private final Runnable runnable;

    DoBeforeFinallySingle(Single<T> original, Runnable runnable) {
        this.original = requireNonNull(original);
        this.runnable = requireNonNull(runnable);
    }

    @Override
    protected void handleSubscribe(Subscriber<? super T> subscriber) {
        original.subscribe(new DoFinallySubscriber<>(subscriber, runnable));
    }

    private static final class DoFinallySubscriber<T> implements Subscriber<T> {
        private final Subscriber<? super T> original;
        private final Runnable runnable;

        private static final AtomicIntegerFieldUpdater<DoFinallySubscriber> completeUpdater =
                AtomicIntegerFieldUpdater.newUpdater(DoFinallySubscriber.class, "complete");
        @SuppressWarnings("unused")
        private volatile int complete;

        DoFinallySubscriber(Subscriber<? super T> original, Runnable runnable) {
            this.original = original;
            this.runnable = runnable;
        }

        @Override
        public void onSubscribe(Cancellable originalCancellabe) {
            original.onSubscribe(new DoBeforeCancellable(this::doFinally, originalCancellabe));
        }

        @Override
        public void onSuccess(T value) {
            try {
                doFinally();
            } catch (Throwable error) {
                original.onError(error);
                return;
            }
            original.onSuccess(value);
        }

        @Override
        public void onError(Throwable cause) {
            try {
                doFinally();
            } catch (Throwable err) {
                err.addSuppressed(cause);
                original.onError(err);
                return;
            }
            original.onError(cause);
        }

        private void doFinally() {
            if (completeUpdater.compareAndSet(this, 0, 1)) {
                runnable.run();
            }
        }
    }
}

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

final class DoAfterFinallyCompletable extends Completable {

    private final Completable original;
    private final Runnable runnable;

    DoAfterFinallyCompletable(Completable original, Runnable runnable) {
        this.original = requireNonNull(original);
        this.runnable = requireNonNull(runnable);
    }

    @Override
    protected void handleSubscribe(Subscriber subscriber) {
        original.subscribe(new DoAfterFinallyCompletableSubscriber(subscriber, runnable));
    }

    private static final class DoAfterFinallyCompletableSubscriber implements Subscriber {
        private final Subscriber original;
        private final Runnable runnable;

        private static final AtomicIntegerFieldUpdater<DoAfterFinallyCompletableSubscriber> completeUpdater =
                AtomicIntegerFieldUpdater.newUpdater(DoAfterFinallyCompletableSubscriber.class, "complete");
        @SuppressWarnings("unused")
        private volatile int complete;

        DoAfterFinallyCompletableSubscriber(Subscriber original, Runnable runnable) {
            this.original = original;
            this.runnable = runnable;
        }

        @Override
        public void onSubscribe(Cancellable originalCancellable) {
            original.onSubscribe(new DoBeforeCancellable(originalCancellable, this::doAfterFinally));
        }

        @Override
        public void onComplete() {
            try {
                original.onComplete();
            } finally {
                doAfterFinally();
            }
        }

        @Override
        public void onError(Throwable cause) {
            try {
                original.onError(cause);
            } finally {
                doAfterFinally();
            }
        }

        private void doAfterFinally() {
            if (completeUpdater.compareAndSet(this, 0, 1)) {
                runnable.run();
            }
        }
    }
}

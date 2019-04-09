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

final class BeforeFinallyCompletable extends AbstractSynchronousCompletableOperator {

    private final Runnable runnable;

    BeforeFinallyCompletable(Completable original, Runnable runnable, Executor executor) {
        super(original, executor);
        this.runnable = requireNonNull(runnable);
    }

    @Override
    public Subscriber apply(Subscriber subscriber) {
        return new BeforeFinallyCompletableSubscriber(subscriber, runnable);
    }

    private static final class BeforeFinallyCompletableSubscriber implements Subscriber {
        private final Subscriber original;
        private final Runnable runnable;

        private static final AtomicIntegerFieldUpdater<BeforeFinallyCompletableSubscriber> completeUpdater =
                AtomicIntegerFieldUpdater.newUpdater(BeforeFinallyCompletableSubscriber.class, "complete");
        @SuppressWarnings("unused")
        private volatile int complete;

        BeforeFinallyCompletableSubscriber(Subscriber original, Runnable runnable) {
            this.original = original;
            this.runnable = runnable;
        }

        @Override
        public void onSubscribe(Cancellable originalCancellable) {
            original.onSubscribe(new BeforeCancellable(this::beforeFinally, originalCancellable));
        }

        @Override
        public void onComplete() {
            try {
                beforeFinally();
            } catch (Throwable error) {
                original.onError(error);
                return;
            }
            original.onComplete();
        }

        @Override
        public void onError(Throwable cause) {
            try {
                beforeFinally();
            } catch (Throwable error) {
                error.addSuppressed(cause);
                original.onError(error);
                return;
            }
            original.onError(cause);
        }

        private void beforeFinally() {
            if (completeUpdater.compareAndSet(this, 0, 1)) {
                runnable.run();
            }
        }
    }
}

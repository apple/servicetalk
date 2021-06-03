/*
 * Copyright © 2018, 2020 Apple Inc. and the ServiceTalk project authors
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

final class AfterFinallyCompletable extends AbstractSynchronousCompletableOperator {

    private final TerminalSignalConsumer doFinally;

    AfterFinallyCompletable(Completable original, TerminalSignalConsumer doFinally) {
        super(original);
        this.doFinally = requireNonNull(doFinally);
    }

    @Override
    public Subscriber apply(final Subscriber subscriber) {
        return new AfterFinallyCompletableSubscriber(subscriber, doFinally);
    }

    private static final class AfterFinallyCompletableSubscriber implements Subscriber {
        private final Subscriber original;
        private final TerminalSignalConsumer doFinally;

        private static final AtomicIntegerFieldUpdater<AfterFinallyCompletableSubscriber> doneUpdater =
                AtomicIntegerFieldUpdater.newUpdater(AfterFinallyCompletableSubscriber.class, "done");
        @SuppressWarnings("unused")
        private volatile int done;

        AfterFinallyCompletableSubscriber(Subscriber original, TerminalSignalConsumer doFinally) {
            this.original = original;
            this.doFinally = doFinally;
        }

        @Override
        public void onSubscribe(Cancellable originalCancellable) {
            original.onSubscribe(new ComposedCancellable(originalCancellable, () -> {
                if (doneUpdater.compareAndSet(this, 0, 1)) {
                    doFinally.cancel();
                }
            }));
        }

        @Override
        public void onComplete() {
            try {
                original.onComplete();
            } finally {
                if (doneUpdater.compareAndSet(this, 0, 1)) {
                    doFinally.onComplete();
                }
            }
        }

        @Override
        public void onError(Throwable cause) {
            try {
                original.onError(cause);
            } finally {
                if (doneUpdater.compareAndSet(this, 0, 1)) {
                    doFinally.onError(cause);
                }
            }
        }
    }
}

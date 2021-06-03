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

final class BeforeFinallyCompletable extends AbstractSynchronousCompletableOperator {

    private final TerminalSignalConsumer doFinally;

    BeforeFinallyCompletable(Completable original, TerminalSignalConsumer doFinally) {
        super(original);
        this.doFinally = requireNonNull(doFinally);
    }

    @Override
    public Subscriber apply(Subscriber subscriber) {
        return new BeforeFinallyCompletableSubscriber(subscriber, doFinally);
    }

    private static final class BeforeFinallyCompletableSubscriber implements Subscriber {
        private final Subscriber original;
        private final TerminalSignalConsumer doFinally;

        private static final AtomicIntegerFieldUpdater<BeforeFinallyCompletableSubscriber> doneUpdater =
                AtomicIntegerFieldUpdater.newUpdater(BeforeFinallyCompletableSubscriber.class, "done");
        @SuppressWarnings("unused")
        private volatile int done;

        BeforeFinallyCompletableSubscriber(Subscriber original, TerminalSignalConsumer doFinally) {
            this.original = original;
            this.doFinally = doFinally;
        }

        @Override
        public void onSubscribe(Cancellable originalCancellable) {
            original.onSubscribe(new ComposedCancellable(() -> {
                if (doneUpdater.compareAndSet(this, 0, 1)) {
                    doFinally.cancel();
                }
            }, originalCancellable));
        }

        @Override
        public void onComplete() {
            try {
                if (doneUpdater.compareAndSet(this, 0, 1)) {
                    doFinally.onComplete();
                }
            } catch (Throwable error) {
                original.onError(error);
                return;
            }
            original.onComplete();
        }

        @Override
        public void onError(Throwable cause) {
            try {
                if (doneUpdater.compareAndSet(this, 0, 1)) {
                    doFinally.onError(cause);
                }
            } catch (Throwable error) {
                error.addSuppressed(cause);
                original.onError(error);
                return;
            }
            original.onError(cause);
        }
    }
}

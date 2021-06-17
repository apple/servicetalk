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

final class AfterFinallySingle<T> extends AbstractSynchronousSingleOperator<T, T> {

    private final SingleTerminalSignalConsumer<? super T> doFinally;

    AfterFinallySingle(Single<T> original, SingleTerminalSignalConsumer<? super T> doFinally) {
        super(original);
        this.doFinally = requireNonNull(doFinally);
    }

    @Override
    public Subscriber<? super T> apply(final Subscriber<? super T> subscriber) {
        return new AfterFinallySingleSubscriber<T>(subscriber, doFinally);
    }

    private static final class AfterFinallySingleSubscriber<T> implements Subscriber<T> {
        private final Subscriber<? super T> original;
        private final SingleTerminalSignalConsumer<? super T> doFinally;

        private static final AtomicIntegerFieldUpdater<AfterFinallySingleSubscriber> doneUpdater =
                AtomicIntegerFieldUpdater.newUpdater(AfterFinallySingleSubscriber.class, "done");
        @SuppressWarnings("unused")
        private volatile int done;

        AfterFinallySingleSubscriber(Subscriber<? super T> original,
                                     SingleTerminalSignalConsumer<? super T> doFinally) {
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
        public void onSuccess(T value) {
            try {
                original.onSuccess(value);
            } finally {
                if (doneUpdater.compareAndSet(this, 0, 1)) {
                    doFinally.onSuccess(value);
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

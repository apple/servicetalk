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

final class BeforeFinallySingle<T> extends AbstractSynchronousSingleOperator<T, T> {

    private final SingleTerminalSignalConsumer<? super T> doFinally;

    BeforeFinallySingle(Single<T> original, SingleTerminalSignalConsumer<? super T> doFinally) {
        super(original);
        this.doFinally = requireNonNull(doFinally);
    }

    @Override
    public Subscriber<? super T> apply(final Subscriber<? super T> subscriber) {
        return new BeforeFinallySingleSubscriber<T>(subscriber, doFinally);
    }

    private static final class BeforeFinallySingleSubscriber<T> implements Subscriber<T> {
        private final Subscriber<? super T> original;
        private final SingleTerminalSignalConsumer<? super T> doFinally;

        private static final AtomicIntegerFieldUpdater<BeforeFinallySingleSubscriber> doneUpdater =
                AtomicIntegerFieldUpdater.newUpdater(BeforeFinallySingleSubscriber.class, "done");
        @SuppressWarnings("unused")
        private volatile int done;

        BeforeFinallySingleSubscriber(Subscriber<? super T> original,
                                      SingleTerminalSignalConsumer<? super T> doFinally) {
            this.original = original;
            this.doFinally = doFinally;
        }

        @Override
        public void onSubscribe(Cancellable originalCancellabe) {
            original.onSubscribe(new ComposedCancellable(() -> {
                if (doneUpdater.compareAndSet(this, 0, 1)) {
                    doFinally.cancel();
                }
            }, originalCancellabe));
        }

        @Override
        public void onSuccess(T value) {
            try {
                if (doneUpdater.compareAndSet(this, 0, 1)) {
                    doFinally.onSuccess(value);
                }
            } catch (Throwable error) {
                original.onError(error);
                return;
            }
            original.onSuccess(value);
        }

        @Override
        public void onError(Throwable cause) {
            try {
                if (doneUpdater.compareAndSet(this, 0, 1)) {
                    doFinally.onError(cause);
                }
            } catch (Throwable err) {
                err.addSuppressed(cause);
                original.onError(err);
                return;
            }
            original.onError(cause);
        }
    }
}

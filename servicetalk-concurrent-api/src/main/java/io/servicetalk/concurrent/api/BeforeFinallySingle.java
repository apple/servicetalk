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

import static io.servicetalk.concurrent.api.TerminalSignalConsumers.atomic;

final class BeforeFinallySingle<T> extends AbstractSynchronousSingleOperator<T, T> {

    private final TerminalSignalConsumer doFinally;

    BeforeFinallySingle(Single<T> original, TerminalSignalConsumer doFinally, Executor executor) {
        super(original, executor);
        this.doFinally = atomic(doFinally);
    }

    @Override
    public Subscriber<? super T> apply(final Subscriber<? super T> subscriber) {
        return new BeforeFinallySingleSubscriber<>(subscriber, doFinally);
    }

    private static final class BeforeFinallySingleSubscriber<T> implements Subscriber<T> {
        private final Subscriber<? super T> original;
        private final TerminalSignalConsumer doFinally;

        BeforeFinallySingleSubscriber(Subscriber<? super T> original, TerminalSignalConsumer doFinally) {
            this.original = original;
            this.doFinally = doFinally;
        }

        @Override
        public void onSubscribe(Cancellable originalCancellabe) {
            original.onSubscribe(new BeforeCancellable(doFinally::onCancel, originalCancellabe));
        }

        @Override
        public void onSuccess(T value) {
            try {
                doFinally.onComplete();
            } catch (Throwable error) {
                original.onError(error);
                return;
            }
            original.onSuccess(value);
        }

        @Override
        public void onError(Throwable cause) {
            try {
                doFinally.onError(cause);
            } catch (Throwable err) {
                err.addSuppressed(cause);
                original.onError(err);
                return;
            }
            original.onError(cause);
        }
    }
}

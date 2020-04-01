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

final class AfterFinallySingle<T> extends AbstractSynchronousSingleOperator<T, T> {

    private final TerminalSignalConsumer doFinally;

    AfterFinallySingle(Single<T> original, TerminalSignalConsumer doFinally, Executor executor) {
        super(original, executor);
        this.doFinally = atomic(doFinally);
    }

    @Override
    public Subscriber<? super T> apply(final Subscriber<? super T> subscriber) {
        return new AfterFinallySingleSubscriber<>(subscriber, doFinally);
    }

    private static final class AfterFinallySingleSubscriber<T> implements Subscriber<T> {
        private final Subscriber<? super T> original;
        private final TerminalSignalConsumer doFinally;

        AfterFinallySingleSubscriber(Subscriber<? super T> original, TerminalSignalConsumer doFinally) {
            this.original = original;
            this.doFinally = doFinally;
        }

        @Override
        public void onSubscribe(Cancellable originalCancellable) {
            original.onSubscribe(new BeforeCancellable(originalCancellable, doFinally::onCancel));
        }

        @Override
        public void onSuccess(T value) {
            try {
                original.onSuccess(value);
            } finally {
                doFinally.onComplete();
            }
        }

        @Override
        public void onError(Throwable cause) {
            try {
                original.onError(cause);
            } finally {
                doFinally.onError(cause);
            }
        }
    }
}

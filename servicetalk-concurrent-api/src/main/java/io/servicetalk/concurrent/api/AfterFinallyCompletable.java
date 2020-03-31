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
import io.servicetalk.concurrent.api.TerminalSignalConsumers.CompleteTerminalSignalConsumer;

final class AfterFinallyCompletable extends AbstractSynchronousCompletableOperator {

    private final TerminalSignalConsumer doFinally;

    AfterFinallyCompletable(Completable original, TerminalSignalConsumer doFinally, Executor executor) {
        super(original, executor);
        this.doFinally = new CompleteTerminalSignalConsumer(doFinally);
    }

    @Override
    public Subscriber apply(final Subscriber subscriber) {
        return new AfterFinallyCompletableSubscriber(subscriber, doFinally);
    }

    private static final class AfterFinallyCompletableSubscriber implements Subscriber {
        private final Subscriber original;
        private final TerminalSignalConsumer doFinally;

        AfterFinallyCompletableSubscriber(Subscriber original, TerminalSignalConsumer doFinally) {
            this.original = original;
            this.doFinally = doFinally;
        }

        @Override
        public void onSubscribe(Cancellable originalCancellable) {
            original.onSubscribe(new BeforeCancellable(originalCancellable, doFinally::onCancel));
        }

        @Override
        public void onComplete() {
            try {
                original.onComplete();
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

/*
 * Copyright © 2020 Apple Inc. and the ServiceTalk project authors
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

import java.util.concurrent.atomic.AtomicIntegerFieldUpdater;

import static java.util.Objects.requireNonNull;

final class TerminalSignalConsumers implements TerminalSignalConsumer {

    static final class RunnableTerminalSignalConsumer implements TerminalSignalConsumer {

        private final Runnable onFinally;

        RunnableTerminalSignalConsumer(Runnable onFinally) {
            this.onFinally = requireNonNull(onFinally);
        }

        @Override
        public void onComplete() {
            onFinally.run();
        }

        @Override
        public void onError(final Throwable throwable) {
            onFinally.run();
        }

        @Override
        public void onCancel() {
            onFinally.run();
        }
    }

    static final class CompleteTerminalSignalConsumer implements TerminalSignalConsumer {

        private static final AtomicIntegerFieldUpdater<TerminalSignalConsumers.CompleteTerminalSignalConsumer>
                completeUpdater = AtomicIntegerFieldUpdater.newUpdater(
                        TerminalSignalConsumers.CompleteTerminalSignalConsumer.class, "complete");
        @SuppressWarnings("unused")
        private volatile int complete;

        private final TerminalSignalConsumer deletage;

        CompleteTerminalSignalConsumer(final TerminalSignalConsumer deletage) {
            this.deletage = requireNonNull(deletage);
        }

        @Override
        public void onComplete() {
            if (completeUpdater.compareAndSet(this, 0, 1)) {
                deletage.onComplete();
            }
        }

        @Override
        public void onError(final Throwable throwable) {
            if (completeUpdater.compareAndSet(this, 0, 1)) {
                deletage.onError(throwable);
            }
        }

        @Override
        public void onCancel() {
            if (completeUpdater.compareAndSet(this, 0, 1)) {
                deletage.onCancel();
            }
        }
    }
}

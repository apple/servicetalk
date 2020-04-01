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

final class TerminalSignalConsumers {

    private TerminalSignalConsumers() {
        // No instances
    }

    private static final class RunnableTerminalSignalConsumer implements TerminalSignalConsumer {

        private final Runnable onFinally;

        RunnableTerminalSignalConsumer(final Runnable onFinally) {
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

    private static final class AtomicTerminalSignalConsumer implements TerminalSignalConsumer {

        private static final AtomicIntegerFieldUpdater<AtomicTerminalSignalConsumer> doneUpdater =
                AtomicIntegerFieldUpdater.newUpdater(AtomicTerminalSignalConsumer.class, "done");
        @SuppressWarnings("unused")
        private volatile int done;

        private final TerminalSignalConsumer delegate;

        AtomicTerminalSignalConsumer(final TerminalSignalConsumer delegate) {
            this.delegate = requireNonNull(delegate);
        }

        @Override
        public void onComplete() {
            if (doneUpdater.compareAndSet(this, 0, 1)) {
                delegate.onComplete();
            }
        }

        @Override
        public void onError(final Throwable throwable) {
            if (doneUpdater.compareAndSet(this, 0, 1)) {
                delegate.onError(throwable);
            }
        }

        @Override
        public void onCancel() {
            if (doneUpdater.compareAndSet(this, 0, 1)) {
                delegate.onCancel();
            }
        }
    }

    static TerminalSignalConsumer from(final Runnable runnable) {
        return new RunnableTerminalSignalConsumer(runnable);
    }

    static TerminalSignalConsumer atomic(final TerminalSignalConsumer delegate) {
        return new AtomicTerminalSignalConsumer(delegate);
    }
}

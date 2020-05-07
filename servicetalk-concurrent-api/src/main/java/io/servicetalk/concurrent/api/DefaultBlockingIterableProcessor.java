/*
 * Copyright © 2019 Apple Inc. and the ServiceTalk project authors
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

import io.servicetalk.concurrent.BlockingIterable.Processor;
import io.servicetalk.concurrent.BlockingIterator;
import io.servicetalk.concurrent.internal.TerminalNotification;

import java.util.NoSuchElementException;
import java.util.concurrent.CancellationException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import javax.annotation.Nullable;

import static io.servicetalk.utils.internal.PlatformDependent.throwException;
import static java.util.Objects.requireNonNull;

final class DefaultBlockingIterableProcessor<T> implements Processor<T> {
    private final BlockingProcessorBuffer<T> buffer;

    DefaultBlockingIterableProcessor(final BlockingProcessorBuffer<T> buffer) {
        this.buffer = requireNonNull(buffer);
    }

    @Override
    public BlockingIterator<T> iterator() {
        return new PollingBlockingIterator<>(buffer);
    }

    @Override
    public void next(@Nullable final T nextItem) throws InterruptedException {
        buffer.add(nextItem);
    }

    @Override
    public void fail(final Throwable cause) throws InterruptedException {
        buffer.terminate(cause);
    }

    @Override
    public void close() throws InterruptedException {
        buffer.terminate();
    }

    private static final class PollingBlockingIterator<T> implements BlockingIterator<T>, BufferConsumer<T> {
        @Nullable
        private T next;
        @Nullable
        private TerminalNotification terminal;
        private final BlockingProcessorBuffer<T> buffer;

        PollingBlockingIterator(final BlockingProcessorBuffer<T> buffer) {
            this.buffer = buffer;
        }

        @Override
        public boolean hasNext(final long timeout, final TimeUnit unit) throws TimeoutException {
            if (terminal != null) {
                return hasNextWhenTerminated();
            }
            if (next != null) {
                return true;
            }
            final boolean consumed;
            try {
                consumed = buffer.consume(this, timeout, unit);
            } catch (InterruptedException e) {
                return throwException(e);
            }
            return terminal == null ? consumed : hasNextWhenTerminated();
        }

        @Nullable
        @Override
        public T next(final long timeout, final TimeUnit unit) throws TimeoutException {
            if (!hasNext(timeout, unit)) {
                throw new NoSuchElementException();
            }
            return processNext();
        }

        @Nullable
        @Override
        public T next() {
            if (!hasNext()) {
                throw new NoSuchElementException();
            }
            return processNext();
        }

        @Override
        public void close() {
            terminal = TerminalNotification.error(new CancellationException());
        }

        @Override
        public boolean hasNext() {
            if (terminal != null) {
                return hasNextWhenTerminated();
            }
            if (next != null) {
                return true;
            }

            final boolean consumed;
            try {
                consumed = buffer.consume(this);
            } catch (InterruptedException e) {
                return throwException(e);
            }
            return terminal == null ? consumed : hasNextWhenTerminated();
        }

        private boolean hasNextWhenTerminated() {
            assert terminal != null;
            Throwable cause = terminal.cause();
            if (cause != null) {
                throwException(cause);
            }
            return false;
        }

        @Nullable
        private T processNext() {
            T next = this.next;
            this.next = null;
            return next;
        }

        @Override
        public void consumeItem(@Nullable final T item) {
            this.next = item;
        }

        @Override
        public void consumeTerminal(final Throwable cause) {
            this.terminal = TerminalNotification.error(cause);
        }

        @Override
        public void consumeTerminal() {
            this.terminal = TerminalNotification.complete();
        }
    }
}

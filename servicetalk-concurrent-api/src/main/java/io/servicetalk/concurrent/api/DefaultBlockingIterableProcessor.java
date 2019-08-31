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

import io.servicetalk.concurrent.BlockingIterable;
import io.servicetalk.concurrent.BlockingIterator;
import io.servicetalk.concurrent.internal.TerminalNotification;

import java.util.NoSuchElementException;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CancellationException;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import javax.annotation.Nullable;

import static io.servicetalk.concurrent.internal.PlatformDependent.throwException;

final class DefaultBlockingIterableProcessor<T> implements BlockingIterable.Processor<T> {
    private static final Object NULL_MASK = new Object();
    private final BlockingQueue<Object> buffer;
    @Nullable
    private TerminalNotification terminationReason;

    DefaultBlockingIterableProcessor(int maxBufferSize) {
        buffer = new LinkedBlockingQueue<>(maxBufferSize);
    }

    @Override
    public BlockingIterator<T> iterator() {
        return new PollingBlockingIterator();
    }

    @Override
    public void emit(@Nullable final T nextItem) throws Exception {
        verifyOpen("Can not emit items to a closed iterable.");

        buffer.put(maskNull(nextItem));
    }

    @Override
    public void fail(final Throwable cause) throws Exception {
        verifyOpen("Iterable already closed.");

        terminationReason = TerminalNotification.error(cause);
        buffer.put(terminationReason);
    }

    @Override
    public void close() throws Exception {
        verifyOpen("Iterable already closed.");

        terminationReason = TerminalNotification.complete();
        buffer.put(terminationReason);
    }

    private void verifyOpen(final String s) {
        if (terminationReason != null) {
            if (terminationReason.cause() == null) {
                throw new IllegalStateException(s);
            } else {
                throwException(terminationReason.cause());
            }
        }
    }

    private static Object maskNull(@Nullable Object nextItem) {
        return nextItem == null ? NULL_MASK : nextItem;
    }

    private final class PollingBlockingIterator implements BlockingIterator<T> {
        @Nullable
        private Object next;
        @Nullable
        private TerminalNotification terminal;

        @Override
        public boolean hasNext(final long timeout, final TimeUnit unit) throws TimeoutException {
            if (terminal != null) {
                return hasNextWhenTerminated();
            }
            if (next != null) {
                return true;
            }
            final Object next;
            try {
                next = buffer.poll(timeout, unit);
            } catch (InterruptedException e) {
                return throwException(e);
            }
            if (next == null) {
                throw new TimeoutException("Timed out waiting for an item.");
            }
            return processHasNext(next);
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

            final Object next;
            try {
                next = buffer.take();
            } catch (InterruptedException e) {
                return throwException(e);
            }
            return processHasNext(next);
        }

        @Nullable
        private T processNext() {
            Object next = this.next;
            this.next = null;
            if (next == NULL_MASK) {
                return null;
            } else {
                @SuppressWarnings("unchecked")
                T t = (T) next;
                return t;
            }
        }

        private boolean processHasNext(final Object next) {
            if (next instanceof TerminalNotification) {
                terminal = (TerminalNotification) next;
                if (terminal.cause() == null) {
                    return false;
                }
                return throwException(terminal.cause());
            }
            this.next = next;
            return true;
        }

        private boolean hasNextWhenTerminated() {
            assert terminal != null;
            return terminal.cause() != null && (boolean) throwException(terminal.cause());
        }
    }
}

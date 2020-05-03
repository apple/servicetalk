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

import io.servicetalk.concurrent.internal.TerminalNotification;

import java.util.concurrent.atomic.AtomicReferenceFieldUpdater;
import javax.annotation.Nullable;

import static io.servicetalk.concurrent.internal.TerminalNotification.complete;
import static io.servicetalk.concurrent.internal.TerminalNotification.error;
import static java.util.concurrent.atomic.AtomicReferenceFieldUpdater.newUpdater;

abstract class AbstractProcessorBuffer<T> implements ProcessorBuffer<T> {
    @SuppressWarnings("rawtypes")
    private static final AtomicReferenceFieldUpdater<AbstractProcessorBuffer,
            TerminalNotification> terminalUpdater = newUpdater(AbstractProcessorBuffer.class,
            TerminalNotification.class, "terminal");
    private static final Object NULL_ITEM = new Object();

    @Nullable
    private volatile TerminalNotification terminal;

    @Override
    public final void add(@Nullable final T item) {
        final TerminalNotification terminalNotification = terminal;
        if (terminalNotification != null) {
            throw new IllegalStateException("Buffer " + this + " is already terminated: " + terminal);
        }
        addItem(item == null ? NULL_ITEM : item);
    }

    @Override
    public final void terminate() {
        if (terminalUpdater.compareAndSet(this, null, complete())) {
            addTerminal(complete());
        }
    }

    @Override
    public final void terminate(final Throwable cause) {
        TerminalNotification notification = error(cause);
        if (terminalUpdater.compareAndSet(this, null, notification)) {
            addTerminal(notification);
        }
    }

    protected abstract void addItem(Object item);

    protected abstract void addTerminal(TerminalNotification terminalNotification);

    protected boolean consumeIfTerminal(final BufferConsumer<T> consumer, @Nullable final Object signal) {
        if (signal instanceof TerminalNotification) {
            TerminalNotification terminalNotification = (TerminalNotification) signal;
            if (terminalNotification.cause() != null) {
                consumer.consumeTerminal(terminalNotification.cause());
            } else {
                consumer.consumeTerminal();
            }
            return true;
        }
        return false;
    }

    protected boolean consumeNextItem(final BufferConsumer<T> consumer, @Nullable final Object nextItem) {
        if (nextItem == null) {
            return false;
        }
        if (nextItem == NULL_ITEM) {
            consumer.consumeItem(null);
        } else {
            @SuppressWarnings("unchecked")
            T t = (T) nextItem;
            consumer.consumeItem(t);
        }
        return true;
    }
}

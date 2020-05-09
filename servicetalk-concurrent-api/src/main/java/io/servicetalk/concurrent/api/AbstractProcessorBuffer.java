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

import static java.util.concurrent.atomic.AtomicReferenceFieldUpdater.newUpdater;

abstract class AbstractProcessorBuffer {
    private static final AtomicReferenceFieldUpdater<AbstractProcessorBuffer,
            TerminalNotification> terminalUpdater = newUpdater(AbstractProcessorBuffer.class,
            TerminalNotification.class, "terminal");
    private static final Object NULL_ITEM = new Object();

    @Nullable
    private volatile TerminalNotification terminal;

    final boolean tryTerminate(final TerminalNotification notification) {
        return terminalUpdater.compareAndSet(this, null, notification);
    }

    static <T> Object maskNull(@Nullable final T item) {
        return item == null ? NULL_ITEM : item;
    }

    @SuppressWarnings("unchecked")
    @Nullable
    private static <T> T unmaskNull(final Object item) {
        return item == NULL_ITEM ? null : (T) item;
    }

    /**
     * Invokes {@link ProcessorSignalsConsumer#consumeTerminal(Throwable)} if the passed {@code signal} is a
     * {@link TerminalNotification} representing an error termination. Invokes
     * {@link ProcessorSignalsConsumer#consumeTerminal()} if the passed {@code signal} is a {@link TerminalNotification}
     * representing a successful termination. If the passed {@code signal} is not a {@link TerminalNotification} then
     * does nothing.
     *
     * @param consumer {@link ProcessorSignalsConsumer} to consume the terminal.
     * @param signal which may be a {@link TerminalNotification}.
     * @return {@code true} if any method was invoked on the passed {@link ProcessorSignalsConsumer}.
     */
    static boolean consumeIfTerminal(final ProcessorSignalsConsumer<?> consumer, @Nullable final Object signal) {
        if (signal instanceof TerminalNotification) {
            Throwable cause = ((TerminalNotification) signal).cause();
            if (cause != null) {
                consumer.consumeTerminal(cause);
            } else {
                consumer.consumeTerminal();
            }
            return true;
        }
        return false;
    }

    /**
     * Invokes {@link ProcessorSignalsConsumer#consumeItem(Object)} if the passed {@code signal} is not {@code null}.
     *
     * @param consumer {@link ProcessorSignalsConsumer} to consume the item.
     * @param nextItem which either can be {@code null} or an item of type {@link T}.
     * @param <T> Type of items consumed by {@link ProcessorSignalsConsumer}.
     * @return {@code true} if any method was invoked on the passed {@link ProcessorSignalsConsumer}.
     */
    static <T> boolean consumeNextItem(final ProcessorSignalsConsumer<T> consumer, @Nullable final Object nextItem) {
        if (nextItem == null) {
            return false;
        }
        T t = unmaskNull(nextItem);
        consumer.consumeItem(t);
        return true;
    }
}

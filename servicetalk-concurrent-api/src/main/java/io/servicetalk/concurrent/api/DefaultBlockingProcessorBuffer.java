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

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static io.servicetalk.utils.internal.PlatformDependent.throwException;

final class DefaultBlockingProcessorBuffer<T> extends AbstractProcessorBuffer<T> implements BlockingProcessorBuffer<T> {
    private final BlockingQueue<Object> signals;

    DefaultBlockingProcessorBuffer(final int maxBuffer) {
        this.signals = new LinkedBlockingQueue<>(maxBuffer);
    }

    @Override
    protected void addItem(final Object item) {
        putSignal(item);
    }

    @Override
    protected void addTerminal(final TerminalNotification terminalNotification) {
        putSignal(terminalNotification);
    }

    @Override
    public boolean consume(final BufferConsumer<T> consumer) {
        if (consumeIfTerminal(consumer, signals.peek())) {
            return true;
        }

        return consumeNextItem(consumer, signals.poll());
    }

    @Override
    public boolean consume(final BufferConsumer<T> consumer, final long waitFor, final TimeUnit waitForUnit)
            throws TimeoutException, InterruptedException {
        if (consumeIfTerminal(consumer, signals.peek())) {
            return true;
        }

        Object nextItem = signals.poll(waitFor, waitForUnit);
        if (nextItem == null) {
            throw new TimeoutException("Timed out after " + waitFor + "(" + waitForUnit + ") waiting for an item.");
        }
        return consumeNextItem(consumer, nextItem);
    }

    private void putSignal(final Object signal) {
        try {
            signals.put(signal);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throwException(e);
        }
    }
}

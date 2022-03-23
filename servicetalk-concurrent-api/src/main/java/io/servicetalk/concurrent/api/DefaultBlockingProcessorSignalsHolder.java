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

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import javax.annotation.Nullable;

import static io.servicetalk.concurrent.api.ProcessorBufferUtils.consumeIfTerminal;
import static io.servicetalk.concurrent.api.ProcessorBufferUtils.consumeNextItem;
import static io.servicetalk.concurrent.api.SubscriberApiUtils.wrapNull;
import static io.servicetalk.concurrent.internal.TerminalNotification.complete;
import static io.servicetalk.concurrent.internal.TerminalNotification.error;

final class DefaultBlockingProcessorSignalsHolder<T>
        implements BlockingProcessorSignalsHolder<T> {
    private final BlockingQueue<Object> signals;

    DefaultBlockingProcessorSignalsHolder(final int maxBuffer) {
        this.signals = new LinkedBlockingQueue<>(maxBuffer);
    }

    @Override
    public void add(@Nullable final T item) throws InterruptedException {
        signals.put(wrapNull(item));
    }

    @Override
    public void terminate() throws InterruptedException {
        signals.put(complete());
    }

    @Override
    public void terminate(final Throwable cause) throws InterruptedException {
        signals.put(error(cause));
    }

    @Override
    public boolean consume(final ProcessorSignalsConsumer<T> consumer) throws InterruptedException {
        Object signal = signals.take();
        return consumeIfTerminal(consumer, signal) || consumeNextItem(consumer, signal);
    }

    @Override
    public boolean consume(final ProcessorSignalsConsumer<T> consumer, final long waitFor, final TimeUnit waitForUnit)
            throws TimeoutException, InterruptedException {
        Object signal = signals.poll(waitFor, waitForUnit);
        if (signal == null) {
            throw new TimeoutException("Timed out after " + waitFor + "(" + waitForUnit + ") waiting for an item.");
        }

        return consumeIfTerminal(consumer, signal) || consumeNextItem(consumer, signal);
    }
}

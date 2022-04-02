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

import io.servicetalk.concurrent.internal.QueueFullException;

import java.util.Queue;
import java.util.concurrent.atomic.AtomicIntegerFieldUpdater;
import javax.annotation.Nullable;

import static io.servicetalk.concurrent.api.ProcessorBufferUtils.consumeIfTerminal;
import static io.servicetalk.concurrent.api.ProcessorBufferUtils.consumeNextItem;
import static io.servicetalk.concurrent.api.SubscriberApiUtils.wrapNull;
import static io.servicetalk.concurrent.internal.TerminalNotification.complete;
import static io.servicetalk.concurrent.internal.TerminalNotification.error;
import static java.util.Objects.requireNonNull;
import static java.util.concurrent.atomic.AtomicIntegerFieldUpdater.newUpdater;

abstract class AbstractPublisherProcessorSignalsHolder<T, Q extends Queue<Object>>
        implements PublisherProcessorSignalsHolder<T> {
    @SuppressWarnings("rawtypes")
    private static final AtomicIntegerFieldUpdater<AbstractPublisherProcessorSignalsHolder> bufferedUpdater =
            newUpdater(AbstractPublisherProcessorSignalsHolder.class, "buffered");

    private final int maxBuffer;
    private final Q signals;

    private volatile int buffered;

    AbstractPublisherProcessorSignalsHolder(final int maxBuffer, final Q signals) {
        if (maxBuffer <= 0) {
            throw new IllegalArgumentException("maxBuffer: " + maxBuffer + " (expected > 0)");
        }
        this.maxBuffer = maxBuffer;
        this.signals = requireNonNull(signals);
    }

    @Override
    public void add(@Nullable final T item) {
        if (bufferedUpdater.getAndAccumulate(this, 1,
                (prev, next) -> prev == maxBuffer ? maxBuffer : (prev + next)) == maxBuffer) {
            offerPastBufferSize(wrapNull(item), signals);
        } else {
            offerSignal(wrapNull(item));
        }
    }

    @Override
    public void terminate() {
        offerSignal(complete());
    }

    @Override
    public void terminate(final Throwable cause) {
        offerSignal(error(cause));
    }

    @Override
    public boolean tryConsume(final ProcessorSignalsConsumer<T> consumer) {
        Object signal = signals.poll();
        if (consumeIfTerminal(consumer, signal)) {
            return true;
        }

        if (consumeNextItem(consumer, signal)) {
            bufferedUpdater.decrementAndGet(this);
            return true;
        }
        return false;
    }

    @Override
    public boolean tryConsumeTerminal(final ProcessorSignalsConsumer<T> consumer) {
        return consumeIfTerminal(consumer, signals.peek());
    }

    abstract void offerPastBufferSize(Object signal, Q queue);

    private void offerSignal(final Object signal) {
        if (!signals.offer(signal)) {
            throw new QueueFullException("publisher-processor-signals");
        }
    }
}

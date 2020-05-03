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
import io.servicetalk.concurrent.internal.TerminalNotification;

import java.util.Queue;
import java.util.concurrent.atomic.AtomicIntegerFieldUpdater;

import static io.servicetalk.concurrent.internal.FlowControlUtils.addWithOverflowProtection;
import static java.util.Objects.requireNonNull;
import static java.util.concurrent.atomic.AtomicIntegerFieldUpdater.newUpdater;

abstract class AbstractPublisherProcessorBuffer<T, Q extends Queue<Object>> extends AbstractProcessorBuffer<T>
        implements PublisherProcessorBuffer<T> {
    @SuppressWarnings("rawtypes")
    private static final AtomicIntegerFieldUpdater<AbstractPublisherProcessorBuffer> bufferedUpdater =
            newUpdater(AbstractPublisherProcessorBuffer.class, "buffered");

    private final int maxBuffer;
    private final Q signals;

    private volatile int buffered;

    AbstractPublisherProcessorBuffer(final int maxBuffer, final Q signals) {
        if (maxBuffer <= 0) {
            throw new IllegalArgumentException("maxBuffer: " + maxBuffer + " (expected > 0)");
        }
        this.maxBuffer = maxBuffer;
        this.signals = requireNonNull(signals);
    }

    @Override
    protected void addItem(final Object item) {
        if (bufferedUpdater.getAndAccumulate(this, 1,
                (prev, next) -> prev == maxBuffer ? maxBuffer :
                        addWithOverflowProtection(prev, next)) == maxBuffer) {
            offerPastBufferSize(item, signals);
        } else {
            offerSignal(item);
        }
    }

    @Override
    protected void addTerminal(final TerminalNotification terminalNotification) {
        offerSignal(terminalNotification);
    }

    @Override
    public boolean tryConsume(final BufferConsumer<T> consumer) {
        if (consumeIfTerminal(consumer, signals.peek())) {
            return true;
        }

        if (consumeNextItem(consumer, signals.poll())) {
            bufferedUpdater.decrementAndGet(this);
            return true;
        }
        return false;
    }

    @Override
    public boolean tryConsumeTerminal(final BufferConsumer<T> consumer) {
        return consumeIfTerminal(consumer, signals.peek());
    }

    abstract void offerPastBufferSize(Object signal, Q queue);

    private void offerSignal(final Object signal) {
        if (!signals.offer(signal)) {
            throw new QueueFullException("publisher-processor-signals");
        }
    }
}

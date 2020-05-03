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

import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static io.servicetalk.concurrent.BlockingIterable.Processor;

/**
 * A buffer to store items for a {@link Processor}.
 *
 * @param <T>  Type of items stored in this buffer.
 */
public interface BlockingProcessorBuffer<T> extends ProcessorBuffer<T> {

    /**
     * Consumes the next item stored in this buffer. If there are no items stored in the buffer and the buffer has
     * terminated {@link #terminate() successfully} or with an {@link #terminate(Throwable) error} then consume that
     * {@link BufferConsumer#consumeTerminal() successful} or {@link BufferConsumer#consumeTerminal(Throwable) failed}
     * termination.
     * <p>
     * This method will block till an item or a terminal event is available in the buffer.
     *
     * @param consumer {@link BufferConsumer} to consume the next item or termination in this buffer
     * @return {@code true} if any method was called on the passed {@link BufferConsumer}.
     * @throws InterruptedException If the thread was interrupted while waiting for an item or terminal event.
     */
    boolean consume(BufferConsumer<T> consumer) throws InterruptedException;

    /**
     * Consumes the next item stored in this buffer. If there are no items stored in the buffer and the buffer has
     * terminated {@link #terminate() successfully} or with an {@link #terminate(Throwable) error} then consume that
     * {@link BufferConsumer#consumeTerminal() successful} or {@link BufferConsumer#consumeTerminal(Throwable) failed}
     * termination.
     * <p>
     * This method will block till an item or a terminal event is available in the buffer or the passed {@code waitFor}
     * duration has elapsed.
     *
     * @param consumer {@link BufferConsumer} to consume the next item or termination in this buffer
     * @param waitFor Duration to wait for an item or termination to be available.
     * @param waitForUnit {@link TimeUnit} for {@code waitFor}.
     * @return {@code true} if any method was called on the passed {@link BufferConsumer}.
     * @throws TimeoutException If there was no item or termination available in the buffer for the passed
     * {@code waitFor} duration
     * @throws InterruptedException If the thread was interrupted while waiting for an item or terminal event.
     */
    boolean consume(BufferConsumer<T> consumer, long waitFor, TimeUnit waitForUnit)
            throws TimeoutException, InterruptedException;
}

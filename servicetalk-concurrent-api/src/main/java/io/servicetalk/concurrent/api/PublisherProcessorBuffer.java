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

import io.servicetalk.concurrent.PublisherSource.Processor;

import javax.annotation.Nullable;

/**
 * A buffer to store items for a {@link Processor}.
 *
 * @param <T>  Type of items stored in this buffer.
 */
public interface PublisherProcessorBuffer<T> {

    /**
     * Adds an item to this buffer.
     *
     * @param item to add.
     */
    void add(@Nullable T item);

    /**
     * Terminates this buffer, such that no further modifications of this buffer are allowed. Subsequent
     * {@link BufferConsumer consumptions} must first consume all previously {@link #add(Object) added} items and then
     * {@link BufferConsumer#consumeTerminal()} consume termination}.
     */
    void terminate();

    /**
     * Terminates this buffer, such that no further modifications of this buffer are allowed. Subsequent
     * {@link BufferConsumer consumptions} must first consume all previously {@link #add(Object) added} items and then
     * {@link BufferConsumer#consumeTerminal()} consume termination}.
     *
     * @param cause {@link Throwable} as a cause for termination.
     */
    void terminate(Throwable cause);

    /**
     * Try to consume the next item stored in this buffer. If there are no items stored in the buffer and the buffer has
     * terminated {@link #terminate() successfully} or with an {@link #terminate(Throwable) error} then consume that
     * {@link BufferConsumer#consumeTerminal() successful} or
     * {@link BufferConsumer#consumeTerminal(Throwable) failed} termination.
     *
     * @param consumer {@link BufferConsumer} to consume the next item or termination in this buffer
     * @return {@code true} if any method was called on the passed {@link BufferConsumer}.
     */
    boolean tryConsume(BufferConsumer<T> consumer);

    /**
     * If there are no items stored in the buffer and the buffer has terminated {@link #terminate() successfully} or
     * with an {@link #terminate(Throwable) error} then consume that {@link BufferConsumer#consumeTerminal() successful}
     * or {@link BufferConsumer#consumeTerminal(Throwable) failed} termination. If there are items in the buffer then
     * this method does nothing.
     *
     * @param consumer {@link BufferConsumer} to consume the next item or termination in this buffer
     * @return {@code true} if a terminal event was consumed by the passed {@link BufferConsumer}.
     */
    boolean tryConsumeTerminal(BufferConsumer<T> consumer);
}

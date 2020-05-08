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
import javax.annotation.Nullable;

import static io.servicetalk.concurrent.BlockingIterable.Processor;

/**
 * A holder of items for a {@link Processor}.
 * <h2>Multi-threaded access</h2>
 * Implementations may assume that the consumption of the items (methods {@link #consume(ProcessorSignalsConsumer)}
 * and {@link #consume(ProcessorSignalsConsumer, long, TimeUnit)}) is always done serially however the production
 * (methods {@link #add(Object)}, {@link #terminate(Throwable)} and {@link #terminate()}) may be done concurrently.
 *
 * @param <T>  Type of items stored in this holder.
 */
public interface BlockingProcessorSignalsHolder<T> {

    /**
     * Adds an item to this holder.
     *
     * @param item to add.
     * @throws InterruptedException If the add was interrupted.
     */
    void add(@Nullable T item) throws InterruptedException;

    /**
     * Terminates this holder, such that no further modifications of this holder are expected. Subsequent
     * {@link ProcessorSignalsConsumer consumptions} must first consume all previously {@link #add(Object) added} items
     * and then {@link ProcessorSignalsConsumer#consumeTerminal()} consume termination}.
     * @throws InterruptedException If termination was interrupted.
     */
    void terminate() throws InterruptedException;

    /**
     * Terminates this holder, such that no further modifications of this holder are expected. Subsequent
     * {@link ProcessorSignalsConsumer consumptions} must first consume all previously {@link #add(Object) added}
     * items and then {@link ProcessorSignalsConsumer#consumeTerminal()} consume termination}.
     *
     * @param cause {@link Throwable} as a cause for termination.
     * @throws InterruptedException If termination was interrupted.
     */
    void terminate(Throwable cause) throws InterruptedException;

    /**
     * Consumes the next item stored in this holder. If there are no items stored in the holder and the holder has
     * terminated {@link #terminate() successfully} or with an {@link #terminate(Throwable) error} then consume that
     * {@link ProcessorSignalsConsumer#consumeTerminal() successful} or
     * {@link ProcessorSignalsConsumer#consumeTerminal(Throwable) failed} termination.
     * <p>
     * This method will block till an item or a terminal event is available in the holder.
     *
     * @param consumer {@link ProcessorSignalsConsumer} to consume the next item or termination in this holder
     * @return {@code true} if any method was called on the passed {@link ProcessorSignalsConsumer}.
     * @throws InterruptedException If the thread was interrupted while waiting for an item or terminal event.
     */
    boolean consume(ProcessorSignalsConsumer<T> consumer) throws InterruptedException;

    /**
     * Consumes the next item stored in this holder. If there are no items stored in the holder and the holder has
     * terminated {@link #terminate() successfully} or with an {@link #terminate(Throwable) error} then consume that
     * {@link ProcessorSignalsConsumer#consumeTerminal() successful} or
     * {@link ProcessorSignalsConsumer#consumeTerminal(Throwable) failed} termination.
     * <p>
     * This method will block till an item or a terminal event is available in the holder or the passed {@code waitFor}
     * duration has elapsed.
     *
     * @param consumer {@link ProcessorSignalsConsumer} to consume the next item or termination in this holder
     * @param waitFor Duration to wait for an item or termination to be available.
     * @param waitForUnit {@link TimeUnit} for {@code waitFor}.
     * @return {@code true} if any method was called on the passed {@link ProcessorSignalsConsumer}.
     * @throws TimeoutException If there was no item or termination available in the holder for the passed
     * {@code waitFor} duration
     * @throws InterruptedException If the thread was interrupted while waiting for an item or terminal event.
     */
    boolean consume(ProcessorSignalsConsumer<T> consumer, long waitFor, TimeUnit waitForUnit)
            throws TimeoutException, InterruptedException;
}

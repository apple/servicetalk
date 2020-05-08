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
import io.servicetalk.concurrent.PublisherSource.Subscriber;

import javax.annotation.Nullable;

/**
 * A holder of items for a {@link Processor}. A {@link Processor} decouples {@link Subscriber production of data} from
 * the {@link Processor#subscribe(Subscriber) consumption of data} and this holder acts as the implementation for that
 * decoupling by using an intermediate in-memory storage. This in-memory storage layer can be used in different ways,
 * some of which are enumerated below:
 * <ul>
 *     <li>Implement a custom signal rejection strategy when some signals can be dropped in favor of others.</li>
 *     <li>Store reduced set of signals when intermediary signals can either be discarded or coalesced.</li>
 *     <li>Reverse order of consumption of items when stored signals reach a threshold.</li>
 * </ul>
 *
 * <h2>Multi-threaded access</h2>
 * Implementations may assume that the consumption of the holder (methods {@link #tryConsume(ProcessorSignalsConsumer)}
 * and {@link #tryConsumeTerminal(ProcessorSignalsConsumer)}) is always done serially however the production (methods
 * {@link #add(Object)}, {@link #terminate(Throwable)} and {@link #terminate()}) can be done concurrently.
 *
 * @param <T>  Type of items stored in this holder.
 */
public interface PublisherProcessorSignalsHolder<T> {

    /**
     * Adds an item to this holder.
     *
     * @param item to add.
     */
    void add(@Nullable T item);

    /**
     * Terminates this holder, such that no further modifications of this holder are allowed. Subsequent
     * {@link ProcessorSignalsConsumer consumptions} must first consume all previously {@link #add(Object) added}
     * items and then {@link ProcessorSignalsConsumer#consumeTerminal()} consume termination}.
     */
    void terminate();

    /**
     * Terminates this holder, such that no further modifications of this holder are allowed. Subsequent
     * {@link ProcessorSignalsConsumer consumptions} must first consume all previously {@link #add(Object) added} items
     * and then {@link ProcessorSignalsConsumer#consumeTerminal()} consume termination}.
     *
     * @param cause {@link Throwable} as a cause for termination.
     */
    void terminate(Throwable cause);

    /**
     * Try to consume the next item stored in this holder. If there are no items stored in the holder and the holder has
     * terminated {@link #terminate() successfully} or with an {@link #terminate(Throwable) error} then consume that
     * {@link ProcessorSignalsConsumer#consumeTerminal() successful} or
     * {@link ProcessorSignalsConsumer#consumeTerminal(Throwable) failed} termination.
     *
     * @param consumer {@link ProcessorSignalsConsumer} to consume the next item or termination in this holder
     * @return {@code true} if any method was called on the passed {@link ProcessorSignalsConsumer}.
     */
    boolean tryConsume(ProcessorSignalsConsumer<T> consumer);

    /**
     * If there are no items stored in the holder and the holder has terminated {@link #terminate() successfully} or
     * with an {@link #terminate(Throwable) error} then consume that
     * {@link ProcessorSignalsConsumer#consumeTerminal() successful} or
     * {@link ProcessorSignalsConsumer#consumeTerminal(Throwable) failed} termination. If there are items in the holder
     * then this method does nothing.
     *
     * @param consumer {@link ProcessorSignalsConsumer} to consume the next item or termination in this holder
     * @return {@code true} if a terminal event was consumed by the passed {@link ProcessorSignalsConsumer}.
     */
    boolean tryConsumeTerminal(ProcessorSignalsConsumer<T> consumer);
}

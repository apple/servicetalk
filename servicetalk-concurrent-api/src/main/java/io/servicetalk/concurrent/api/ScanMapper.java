/*
 * Copyright © 2023 Apple Inc. and the ServiceTalk project authors
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

import io.servicetalk.concurrent.PublisherSource.Subscriber;

import java.util.function.Supplier;
import javax.annotation.Nullable;

/**
 * Provides the ability to transform (aka map) signals emitted via the {@link Publisher#scanWithMapper(Supplier)}
 * operator.
 * @param <T> Type of items emitted by the {@link Publisher} this operator is applied.
 * @param <R> Type of items emitted by this operator.
 */
public interface ScanMapper<T, R> {
    /**
     * Invoked on each {@link Subscriber#onNext(Object)} signal and maps from type {@link T} to type {@link R}.
     * @param next The next element emitted from {@link Subscriber#onNext(Object)}.
     * @return The result of mapping {@code next}.
     */
    @Nullable
    R mapOnNext(@Nullable T next);

    /**
     * Invoked when a {@link Subscriber#onError(Throwable)} signal is received and can map the current state into an
     * object of type {@link R} which will be emitted downstream as {@link Subscriber#onNext(Object)}, followed by
     * a terminal signal.
     * <p>
     * If this method throws the exception will be propagated downstream via {@link Subscriber#onError(Throwable)}.
     * @param cause The cause from upstream {@link Subscriber#onError(Throwable)}.
     * @return
     * <ul>
     *     <li>{@code null} if no mapping is required and {@code cause} is propagated to
     *     {@link Subscriber#onError(Throwable)}</li>
     *     <li>non-{@code null} will propagate {@link MappedTerminal#onNext()} to {@link Subscriber#onNext(Object)}
     *     then will terminate with {@link MappedTerminal#terminal()}</li>
     * </ul>
     * @throws Throwable If an exception occurs, which will be propagated downstream via
     * {@link Subscriber#onError(Throwable)}.
     */
    @Nullable
    MappedTerminal<R> mapOnError(Throwable cause) throws Throwable;

    /**
     * Invoked when a {@link Subscriber#onComplete()} signal is received and can map the current state into an
     * object of type {@link R} which will be emitted downstream as {@link Subscriber#onNext(Object)}, followed by
     * a terminal signal.
     * <p>
     * If this method throws the exception will be propagated downstream via {@link Subscriber#onError(Throwable)}.
     * @return
     * <ul>
     *     <li>{@code null} if no mapping is required and {@code cause} is propagated to
     *     {@link Subscriber#onError(Throwable)}</li>
     *     <li>non-{@code null} will propagate {@link MappedTerminal#onNext()} to {@link Subscriber#onNext(Object)}
     *     then will terminate with {@link MappedTerminal#terminal()}</li>
     * </ul>
     * @throws Throwable If an exception occurs, which will be propagated downstream via
     * {@link Subscriber#onError(Throwable)}.
     */
    @Nullable
    MappedTerminal<R> mapOnComplete() throws Throwable;

    /**
     * Result of a mapping operation of a terminal signal.
     * @param <R> The mapped result type.
     */
    interface MappedTerminal<R> {
        /**
         * Get the signal to be delivered to {@link Subscriber#onNext(Object)} if {@link #onNextValid()}.
         * @return the signal to be delivered to {@link Subscriber#onNext(Object)} if {@link #onNextValid()}.
         */
        @Nullable
        R onNext();

        /**
         * Determine if {@link #onNext()} is valid and should be propagated downstream.
         * @return {@code true} to propagate {@link #onNext()}, {@code false} will only propagate {@link #terminal()}.
         */
        boolean onNextValid();

        /**
         * The terminal event to propagate.
         * @return
         * <ul>
         *     <li>{@code null} means {@link Subscriber#onComplete()}</li>
         *     <li>non-{@code null} will propagate as {@link Subscriber#onError(Throwable)}</li>
         * </ul>
         */
        @Nullable
        Throwable terminal();
    }
}

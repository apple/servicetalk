/*
 * Copyright © 2021 Apple Inc. and the ServiceTalk project authors
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
 * Provides the ability to transform (aka map) signals emitted via the {@link Publisher#scanWith(Supplier)} operator.
 * @param <T> Type of items emitted by the {@link Publisher} this operator is applied.
 * @param <R> Type of items emitted by this operator.
 */
public interface ScanWithMapper<T, R> {
    /**
     * Invoked on each {@link Subscriber#onNext(Object)} signal and maps from type {@link T} to type {@link R}.
     * @param next The next element emitted from {@link Subscriber#onNext(Object)}.
     * @return The result of mapping {@code next}.
     */
    @Nullable
    R mapOnNext(@Nullable T next);

    /**
     * Invoked when a {@link Subscriber#onError(Throwable)} is signal received, and maps the current state into an
     * object of type {@link R} which will be emitted downstream as {@link Subscriber#onNext(Object)}, followed by
     * {@link Subscriber#onComplete()}.
     * <p>
     * If this method throws the exception will be propagated downstream via {@link Subscriber#onError(Throwable)}.
     * <p>
     * This method is only invoked if {@link #mapTerminal()} returned {@code true}.
     * @param cause The cause from upstream {@link Subscriber#onError(Throwable)}.
     * @return An object of type {@link R} which will be emitted downstream as {@link Subscriber#onNext(Object)}.
     * @throws Throwable If an exception occurs, which will be propagated downstream via
     * {@link Subscriber#onError(Throwable)}.
     */
    @Nullable
    R mapOnError(Throwable cause) throws Throwable;

    /**
     * Invoked when a {@link Subscriber#onComplete()} is signal received, and maps the current state into an object of
     * type {@link R} which will be emitted downstream as {@link Subscriber#onNext(Object)}, followed by
     * {@link Subscriber#onComplete()}.
     * <p>
     * If this method throws the exception will be propagated downstream via {@link Subscriber#onError(Throwable)}.
     * <p>
     * This method is only invoked if {@link #mapTerminal()} returned {@code true}.
     * @return An object of type {@link R} which will be emitted downstream as {@link Subscriber#onNext(Object)}.
     */
    @Nullable
    R mapOnComplete();

    /**
     * Invoked when a terminal signal is received and determines if an additional {@link Subscriber#onNext(Object)}
     * signal will be emitted downstream.
     * @return If this method returns {@code true} then the {@link #mapOnError(Throwable)} or {@link #mapOnComplete()}
     * will be invoked when their respective terminal signals are received. Otherwise neither
     * {@link #mapOnError(Throwable)} nor {@link #mapOnComplete()} will be invoked and any terminal signal will be
     * passed through directly (no mapping, no additional {@link Subscriber#onNext(Object)}).
     */
    boolean mapTerminal();
}

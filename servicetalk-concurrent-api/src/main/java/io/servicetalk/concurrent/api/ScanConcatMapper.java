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
 * Provides the ability to transform (aka map) elements emitted in the {@link Publisher#scanConcat(Supplier)} operator.
 * @param <T> Type of items emitted by the {@link Publisher} this operator is applied.
 * @param <R> Type of items emitted by this operator.
 */
public interface ScanConcatMapper<T, R> {
    /**
     * Invoked in {@link Subscriber#onNext(Object)}, and the return value will be passed to the
     * downstream {@link Subscriber}.
     * @param next An element in {@link Subscriber#onNext(Object)}.
     * @return The {@link Subscriber#onNext(Object)} signal that is given to the downstream
     * {@link Subscriber}.
     */
    @Nullable
    R onNext(@Nullable T next);

    /**
     * Map an {@link Subscriber#onError(Throwable)} signal into an
     * {@link Subscriber#onNext(Object)} signal.
     * <p>
     * Only invoked if {@link #mapTerminalSignal()} returns {@code true}. If {@link #mapTerminalSignal()} returns
     * {@code false} then {@link Subscriber#onError(Throwable)} will be immediately propagated
     * downstream (without invoking this method).
     * @param t The error from {@link Subscriber#onError(Throwable)}.
     * @return The {@link Subscriber#onNext(Object)} signal that is given to the downstream
     * {@link Subscriber}.
     * @throws Throwable If any exception is thrown it will be propagated to the downstream
     * {@link Subscriber}'s {@link Subscriber#onError(Throwable)}
     */
    @Nullable
    R onError(Throwable t) throws Throwable;

    /**
     * Map an {@link Subscriber#onComplete()} signal into an
     * {@link Subscriber#onNext(Object)} signal.
     * <p>
     * Only invoked if {@link #mapTerminalSignal()} returns {@code true}. If {@link #mapTerminalSignal()} returns
     * {@code false} then {@link Subscriber#onComplete()} signal will be immediately propagated
     * downstream (without invoking this method).
     * @return The {@link Subscriber#onNext(Object)} signal that is given to the downstream
     * {@link Subscriber}.
     */
    @Nullable
    R onComplete();

    /**
     * Determines if {@link #onError(Throwable)} or {@link #onComplete()} should be called. Invoked when a terminal
     * signal is received.
     * @return {@code true} if {@link #onError(Throwable)} or {@link #onComplete()} should be called. {@code false}
     * if the terminal signal should be immediately propagated without any mapping.
     */
    boolean mapTerminalSignal();
}

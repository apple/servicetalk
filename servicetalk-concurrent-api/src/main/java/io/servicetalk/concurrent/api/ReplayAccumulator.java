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

import java.util.function.Consumer;
import javax.annotation.Nullable;

/**
 * Accumulates signals for the {@link Publisher} replay operator.
 * @param <T> The type of data to accumulate.
 */
public interface ReplayAccumulator<T> {
    /**
     * Called on each {@link Subscriber#onNext(Object)} and intended to accumulate the signal so that new
     * {@link Subscriber}s will see this value via {@link #deliverAccumulation(Consumer)}.
     * <p>
     * This method won't be called concurrently, but should return quickly to minimize performance impacts.
     * @param t An {@link Subscriber#onNext(Object)} to accumulate.
     */
    void accumulate(@Nullable T t);

    /**
     * Called to deliver the signals from {@link #accumulate(Object)} to new {@code consumer}.
     * @param consumer The consumer of the signals previously aggregated via {@link #accumulate(Object)}.
     */
    void deliverAccumulation(Consumer<T> consumer);

    /**
     * Called if the accumulation can be cancelled and any asynchronous resources can be cleaned up (e.g. timers).
     */
    default void cancelAccumulation() {
    }
}

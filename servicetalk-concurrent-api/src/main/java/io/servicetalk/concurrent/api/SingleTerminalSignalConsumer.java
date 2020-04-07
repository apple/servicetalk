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

import io.servicetalk.concurrent.Cancellable;
import io.servicetalk.concurrent.SingleSource;

import javax.annotation.Nullable;

/**
 * A contract that provides discrete callbacks for various ways in which a {@link SingleSource.Subscriber} can
 * terminate.
 *
 * @param <T> Type of the result of the {@link Single}.
 */
public interface SingleTerminalSignalConsumer<T> {
    /**
     * Callback to indicate termination via {@link SingleSource.Subscriber#onSuccess(Object)}.
     *
     * @param result the observed result of type {@link T}.
     */
    void onSuccess(@Nullable T result);

    /**
     * Callback to indicate termination via {@link SingleSource.Subscriber#onError(Throwable)}.
     *
     * @param throwable the observed {@link Throwable}.
     */
    void onError(Throwable throwable);

    /**
     * Callback to indicate termination via {@link Cancellable#cancel()}.
     */
    void cancel();

    /**
     * Create a {@link SingleTerminalSignalConsumer} where each method executes a {@link Runnable#run()}.
     * @param runnable The {@link Runnable} which is invoked in each method of the returned
     * {@link SingleTerminalSignalConsumer}.
     * @param <X> The type of {@link SingleTerminalSignalConsumer}.
     * @return a {@link SingleTerminalSignalConsumer} where each method executes a {@link Runnable#run()}.
     */
    static <X> SingleTerminalSignalConsumer<X> from(Runnable runnable) {
        return new RunnableSingleTerminalSignalConsumer<>(runnable);
    }
}

/*
 * Copyright © 2018 Apple Inc. and the ServiceTalk project authors
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
package io.servicetalk.transport.api;

import io.servicetalk.concurrent.api.Executor;
import io.servicetalk.concurrent.api.Publisher;
import io.servicetalk.concurrent.api.Single;

import javax.annotation.Nullable;

/**
 * An execution strategy for all transports.
 */
public interface ExecutionStrategy {

    /**
     * Offloads the {@code original} {@link Single} for sending data on the transport.
     *
     * @param fallback {@link Executor} to use as a fallback if this {@link ExecutionStrategy} does not define an
     * {@link Executor}.
     * @param original {@link Single} to offload.
     * @param <T> Type of result of the {@code original} {@link Single}.
     * @return Offloaded {@link Single}.
     */
    <T> Single<T> offloadSend(Executor fallback, Single<T> original);

    /**
     * Offloads the {@code original} {@link Single} for receiving data from the transport.
     *
     * @param fallback {@link Executor} to use as a fallback if this {@link ExecutionStrategy} does not define an
     * {@link Executor}.
     * @param original {@link Single} to offload.
     * @param <T> Type of result of the {@code original} {@link Single}.
     * @return Offloaded {@link Single}.
     */
    <T> Single<T> offloadReceive(Executor fallback, Single<T> original);

    /**
     * Offloads the {@code original} {@link Publisher} for sending data on the transport.
     *
     * @param fallback {@link Executor} to use as a fallback if this {@link ExecutionStrategy} does not define an
     * {@link Executor}.
     * @param original {@link Publisher} to offload.
     * @param <T> Type of items emitted from the {@code original} {@link Publisher}.
     * @return Offloaded {@link Publisher}.
     */
    <T> Publisher<T> offloadSend(Executor fallback, Publisher<T> original);

    /**
     * Offloads the {@code original} {@link Publisher} for receiving data from the transport.
     *
     * @param fallback {@link Executor} to use as a fallback if this {@link ExecutionStrategy} does not define an
     * {@link Executor}.
     * @param original {@link Publisher} to offload.
     * @param <T> Type of items emitted from the {@code original} {@link Publisher}.
     * @return Offloaded {@link Publisher}.
     */
    <T> Publisher<T> offloadReceive(Executor fallback, Publisher<T> original);

    /**
     * Returns the {@link Executor}, if any, for this {@link ExecutionStrategy}.
     *
     * @return {@link Executor} for this {@link ExecutionStrategy}. {@code null} if none specified.
     * @deprecated Take the {@link Executor} from the {@link io.servicetalk.transport.api.ExecutionContext}.
     */
    @Deprecated
    @Nullable
    Executor executor();
}

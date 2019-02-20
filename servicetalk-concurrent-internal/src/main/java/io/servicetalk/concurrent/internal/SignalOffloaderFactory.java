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
package io.servicetalk.concurrent.internal;

import io.servicetalk.concurrent.Executor;

/**
 * A factory to create new instances of {@link SignalOffloader}.
 */
public interface SignalOffloaderFactory {

    /**
     * Creates a new {@link SignalOffloader}.
     *
     * @param executor {@link Executor} to be used by the returned {@link SignalOffloader}.
     * @return A new {@link SignalOffloader}.
     */
    SignalOffloader newSignalOffloader(Executor executor);

    /**
     * Returns {@code true} if and only if all {@link SignalOffloader} instances will always provide thread affinity.
     * A {@link SignalOffloader} providing thread affinity will offload all signals using a single thread.
     *
     * @return {@code true} if and only if all {@link SignalOffloader} instances will always provide thread affinity.
     */
    boolean hasThreadAffinity();
}

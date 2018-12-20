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
 * A factory for creating different {@link SignalOffloader}s.
 */
public final class SignalOffloaders {

    private SignalOffloaders() {
        // No instances
    }

    /**
     * Create a new instance of {@link SignalOffloader} using the passed {@code executor}.
     *
     * @param executor {@link Executor} to be used by the returned {@link SignalOffloader} to offload signals.
     * @return Newly created {@link SignalOffloader}.
     */
    public static SignalOffloader newOffloaderFor(Executor executor) {
        return new TaskBasedOffloader(executor);
    }

    /**
     * Create a new instance of {@link SignalOffloader} using the passed {@code executor} that uses granular tasks for
     * sending signals.
     *
     * @param executor {@link Executor} to be used by the returned {@link SignalOffloader} to offload signals.
     * @return Newly created {@link SignalOffloader}.
     */
    public static SignalOffloader newTaskBasedOffloader(Executor executor) {
        return new TaskBasedOffloader(executor);
    }

    /**
     * Create a new instance of {@link SignalOffloader} using the passed {@code executor} that captures a thread for
     * its lifetime.
     *
     * @param executor {@link Executor} to be used by the returned {@link SignalOffloader} to offload signals.
     * @return Newly created {@link SignalOffloader}.
     */
    public static SignalOffloader newThreadBasedOffloader(Executor executor) {
        return new ThreadBasedSignalOffloader(executor);
    }
}

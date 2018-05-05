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
package io.servicetalk.transport.netty.internal;

import io.servicetalk.transport.api.IoExecutor;

import static java.util.Objects.requireNonNull;

/**
 * A static factory to create or convert to {@link EventLoopAwareNettyIoExecutor}.
 */
public final class EventLoopAwareNettyIoExecutors {

    private EventLoopAwareNettyIoExecutors() {
        // No instances.
    }

    /**
     * Attempts to convert the passed {@link IoExecutor} to a {@link EventLoopAwareNettyIoExecutor}.
     *
     * @param executor {@link IoExecutor} to convert.
     * @return {@link EventLoopAwareNettyIoExecutor} corresponding to the passed {@link IoExecutor}.
     * @throws IllegalArgumentException If {@link IoExecutor} is not of type {@link EventLoopAwareNettyIoExecutor}.
     */
    public static EventLoopAwareNettyIoExecutor toEventLoopAwareNettyIoExecutor(IoExecutor executor) {
        requireNonNull(executor);
        if (executor instanceof EventLoopAwareNettyIoExecutor) {
            return (EventLoopAwareNettyIoExecutor) executor;
        }
        throw new IllegalArgumentException("Incompatible IoExecutor: " + executor + ". Not a netty based IoExecutor.");
    }
}

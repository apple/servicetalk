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

import io.servicetalk.buffer.api.Buffer;
import io.servicetalk.buffer.api.BufferAllocator;
import io.servicetalk.concurrent.api.Executor;

/**
 * Context related to execution and allocation.
 */
public interface ExecutionContext {
    /**
     * Return the {@link BufferAllocator} that can be used to allocate {@link Buffer}s if needed.
     * @return the {@link BufferAllocator} to use
     */
    BufferAllocator bufferAllocator();

    /**
     * Get the {@link IoExecutor} that is used to handle the IO.
     * @return The {@link IoExecutor} that is used to handle the.
     */
    IoExecutor ioExecutor();

    /**
     * Get the {@link Executor} that is used to create any asynchronous sources.
     * @return The {@link Executor} that is used to create any asynchronous sources.
     */
    Executor executor();

    /**
     * Returns the {@link ExecutionStrategy} associated with this context.
     *
     * @return The {@link ExecutionStrategy} associated with this context.
     */
    ExecutionStrategy executionStrategy();
}

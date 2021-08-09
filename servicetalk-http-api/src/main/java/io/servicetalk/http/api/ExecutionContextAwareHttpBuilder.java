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
package io.servicetalk.http.api;

import io.servicetalk.buffer.api.BufferAllocator;
import io.servicetalk.transport.api.IoExecutor;

interface ExecutionContextAwareHttpBuilder<ResolvedAddress> {

    /**
     * Sets the {@link IoExecutor} for all connections created from this builder.
     *
     * @param ioExecutor {@link IoExecutor} to use.
     * @return {@code this}.
     */
    ExecutionContextAwareHttpBuilder<ResolvedAddress> ioExecutor(IoExecutor ioExecutor);

    /**
     * Sets the {@link BufferAllocator} for all connections created from this builder.
     *
     * @param allocator {@link BufferAllocator} to use.
     * @return {@code this}.
     */
    ExecutionContextAwareHttpBuilder<ResolvedAddress> bufferAllocator(BufferAllocator allocator);

    /**
     * Sets the {@link HttpExecutionStrategy} for all connections created from this builder.
     *
     * @param strategy {@link HttpExecutionStrategy} to use.
     * @return {@code this}.
     */
    ExecutionContextAwareHttpBuilder<ResolvedAddress> executionStrategy(HttpExecutionStrategy strategy);
}

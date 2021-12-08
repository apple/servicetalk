/*
 * Copyright © 2019 Apple Inc. and the ServiceTalk project authors
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
import io.servicetalk.concurrent.api.Executor;
import io.servicetalk.transport.api.DefaultExecutionContext;
import io.servicetalk.transport.api.IoExecutor;

/**
 * Default implementation of {@link HttpExecutionContext}.
 */
public final class DefaultHttpExecutionContext extends DefaultExecutionContext<HttpExecutionStrategy>
        implements HttpExecutionContext {

    /**
     * Create a new instance.
     *
     * @param bufferAllocator The {@link BufferAllocator} to use for {@link #bufferAllocator()}.
     * @param ioExecutor The {@link IoExecutor} to use for {@link #ioExecutor()}.
     * @param executor The {@link Executor} to use for {@link #executor()}.
     * @param executionStrategy {@link HttpExecutionStrategy} to use for {@link #executionStrategy()}.
     */
    public DefaultHttpExecutionContext(final BufferAllocator bufferAllocator,
                                       final IoExecutor ioExecutor,
                                       final Executor executor,
                                       final HttpExecutionStrategy executionStrategy) {
        super(bufferAllocator, ioExecutor, executor, executionStrategy);
    }
}

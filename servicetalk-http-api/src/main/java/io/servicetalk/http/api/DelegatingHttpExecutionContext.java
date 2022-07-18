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
import io.servicetalk.transport.api.IoExecutor;

import static java.util.Objects.requireNonNull;

/**
 * An implementation of {@link HttpExecutionContext} that delegates all method calls to another
 * {@link HttpExecutionContext}.
 */
public class DelegatingHttpExecutionContext implements HttpExecutionContext {
    private final HttpExecutionContext delegate;

    /**
     * Creates a new instance.
     *
     * @param delegate {@link HttpExecutionContext} to delegate all calls.
     */
    public DelegatingHttpExecutionContext(final HttpExecutionContext delegate) {
        this.delegate = requireNonNull(delegate);
    }

    /**
     * Get the {@link HttpExecutionContext} that this class delegates to.
     *
     * @return the {@link HttpExecutionContext} that this class delegates to.
     */
    protected final HttpExecutionContext delegate() {
        return delegate;
    }

    @Override
    public HttpExecutionStrategy executionStrategy() {
        return delegate.executionStrategy();
    }

    @Override
    public BufferAllocator bufferAllocator() {
        return delegate.bufferAllocator();
    }

    @Override
    public IoExecutor ioExecutor() {
        return delegate.ioExecutor();
    }

    @Override
    public Executor executor() {
        return delegate.executor();
    }
}

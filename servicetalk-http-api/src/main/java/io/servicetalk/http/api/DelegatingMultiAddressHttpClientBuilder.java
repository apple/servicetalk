/*
 * Copyright © 2022 Apple Inc. and the ServiceTalk project authors
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
 * A {@link MultiAddressHttpClientBuilder} that delegates all methods to another {@link MultiAddressHttpClientBuilder}.
 *
 * @param <U> the type of address before resolution (unresolved address)
 * @param <R> the type of address after resolution (resolved address)
 */
public class DelegatingMultiAddressHttpClientBuilder<U, R> implements MultiAddressHttpClientBuilder<U, R> {

    private final MultiAddressHttpClientBuilder<U, R> delegate;

    /**
     * Create a new instance.
     *
     * @param delegate {@link MultiAddressHttpClientBuilder} to which all methods are delegated.
     */
    public DelegatingMultiAddressHttpClientBuilder(final MultiAddressHttpClientBuilder<U, R> delegate) {
        this.delegate = requireNonNull(delegate);
    }

    /**
     * Returns the {@link MultiAddressHttpClientBuilder} delegate.
     *
     * @return Delegate {@link MultiAddressHttpClientBuilder}.
     */
    protected final MultiAddressHttpClientBuilder<U, R> delegate() {
        return delegate;
    }

    @Override
    public String toString() {
        return this.getClass().getSimpleName() + "{delegate=" + delegate() + '}';
    }

    @Override
    public MultiAddressHttpClientBuilder<U, R> ioExecutor(final IoExecutor ioExecutor) {
        delegate.ioExecutor(ioExecutor);
        return this;
    }

    @Override
    public MultiAddressHttpClientBuilder<U, R> executor(final Executor executor) {
        delegate.executor(executor);
        return this;
    }

    @Override
    public MultiAddressHttpClientBuilder<U, R> executionStrategy(final HttpExecutionStrategy strategy) {
        delegate.executionStrategy(strategy);
        return this;
    }

    @Override
    public MultiAddressHttpClientBuilder<U, R> bufferAllocator(final BufferAllocator allocator) {
        delegate.bufferAllocator(allocator);
        return this;
    }

    @Override
    public MultiAddressHttpClientBuilder<U, R> initializer(final SingleAddressInitializer<U, R> initializer) {
        delegate.initializer(initializer);
        return this;
    }

    @Override
    public MultiAddressHttpClientBuilder<U, R> followRedirects(final RedirectConfig config) {
        delegate.followRedirects(config);
        return this;
    }

    @Override
    public HttpClient build() {
        return delegate.build();
    }

    @Override
    public StreamingHttpClient buildStreaming() {
        return delegate.buildStreaming();
    }

    @Override
    public BlockingHttpClient buildBlocking() {
        return delegate.buildBlocking();
    }

    @Override
    public BlockingStreamingHttpClient buildBlockingStreaming() {
        return delegate.buildBlockingStreaming();
    }
}

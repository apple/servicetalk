/*
 * Copyright © 2018-2019, 2021 Apple Inc. and the ServiceTalk project authors
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
import io.servicetalk.client.api.ServiceDiscovererEvent;
import io.servicetalk.concurrent.api.Executor;
import io.servicetalk.transport.api.IoExecutor;

/**
 * A builder of {@link HttpClient} objects.
 *
 * @param <U> the type of address before resolution (unresolved address)
 * @param <R> the type of address after resolution (resolved address)
 * @param <SDE> the type of {@link ServiceDiscovererEvent}
 */
interface HttpClientBuilder<U, R, SDE extends ServiceDiscovererEvent<R>> {

    /**
     * Sets the {@link IoExecutor} for all connections created from this builder.
     *
     * @param ioExecutor {@link IoExecutor} to use.
     * @return {@code this}.
     */
    HttpClientBuilder<U, R, SDE> ioExecutor(IoExecutor ioExecutor);

    /**
     * Sets the {@link Executor} for all connections created from this builder.
     *
     * @param executor {@link Executor} to use.
     * @return {@code this}.
     */
    HttpClientBuilder<U, R, SDE> executor(Executor executor);

    /**
     * Sets the {@link BufferAllocator} for all connections created from this builder.
     *
     * @param allocator {@link BufferAllocator} to use.
     * @return {@code this}.
     */
    HttpClientBuilder<U, R, SDE> bufferAllocator(BufferAllocator allocator);

    /**
     * Sets the {@link HttpExecutionStrategy} for all connections created from this builder.
     *
     * @param strategy {@link HttpExecutionStrategy} to use.
     * @return {@code this}.
     * @see HttpExecutionStrategies
     */
    HttpClientBuilder<U, R, SDE> executionStrategy(HttpExecutionStrategy strategy);

    /**
     * Builds a new {@link StreamingHttpClient}.
     *
     * @return A new {@link StreamingHttpClient}
     */
    StreamingHttpClient buildStreaming();

    /**
     * Builds a new {@link HttpClient}.
     *
     * @return A new {@link HttpClient}
     */
    default HttpClient build() {
        return buildStreaming().asClient();
    }

    /**
     * Creates a new {@link BlockingStreamingHttpClient}.
     *
     * @return {@link BlockingStreamingHttpClient}
     */
    default BlockingStreamingHttpClient buildBlockingStreaming() {
        return buildStreaming().asBlockingStreamingClient();
    }

    /**
     * Creates a new {@link BlockingHttpClient}.
     *
     * @return {@link BlockingHttpClient}
     */
    default BlockingHttpClient buildBlocking() {
        return buildStreaming().asBlockingClient();
    }
}

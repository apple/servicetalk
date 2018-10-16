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
package io.servicetalk.redis.api;

import io.servicetalk.buffer.api.BufferAllocator;
import io.servicetalk.client.api.ConnectionFactory;
import io.servicetalk.concurrent.api.Completable;
import io.servicetalk.concurrent.api.Executor;
import io.servicetalk.concurrent.api.ListenableAsyncCloseable;
import io.servicetalk.concurrent.api.Single;
import io.servicetalk.transport.api.IoExecutor;

import static io.servicetalk.concurrent.api.AsyncCloseables.emptyAsyncCloseable;

/**
 * A builder for {@link RedisConnection} objects.
 *
 * @param <ResolvedAddress> The type of a resolved address that can be used for connecting.
 */
public interface RedisConnectionBuilder<ResolvedAddress> {

    /**
     * Sets the {@link IoExecutor} for all connections created from this {@link RedisConnectionBuilder}.
     *
     * @param ioExecutor {@link IoExecutor} to use.
     * @return {@code this}.
     */
    RedisConnectionBuilder<ResolvedAddress> ioExecutor(IoExecutor ioExecutor);

    /**
     * Sets the {@link Executor} for all connections created from this {@link RedisConnectionBuilder}.
     *
     * @param executor {@link Executor} to use.
     * @return {@code this}.
     */
    RedisConnectionBuilder<ResolvedAddress> executor(Executor executor);

    /**
     * Sets the {@link BufferAllocator} for all connections created from this {@link RedisConnectionBuilder}.
     *
     * @param allocator {@link BufferAllocator} to use.
     * @return {@code this}.
     */
    RedisConnectionBuilder<ResolvedAddress> bufferAllocator(BufferAllocator allocator);

    /**
     * Create a new {@link RedisConnection}.
     *
     * @param resolvedAddress a resolved address to use when connecting.
     * @return A single that will complete with the {@link RedisConnection}.
     */
    Single<RedisConnection> build(ResolvedAddress resolvedAddress);

    /**
     * Convert this {@link RedisConnectionBuilder} to a {@link ConnectionFactory}. This can be useful to take advantage
     * of connection filters targeted at the {@link ConnectionFactory} API.
     *
     * @return A {@link ConnectionFactory} that will use the {@link #build(Object)} method to
     * create new {@link RedisConnection} objects.
     */
    default ConnectionFactory<ResolvedAddress, RedisConnection> asConnectionFactory() {
        return new ConnectionFactory<ResolvedAddress, RedisConnection>() {
            private final ListenableAsyncCloseable close = emptyAsyncCloseable();
            @Override
            public Single<RedisConnection> newConnection(ResolvedAddress resolvedAddress) {
                return build(resolvedAddress);
            }

            @Override
            public Completable onClose() {
                return close.onClose();
            }

            @Override
            public Completable closeAsync() {
                return close.closeAsync();
            }

            @Override
            public Completable closeAsyncGracefully() {
                return close.closeAsyncGracefully();
            }
        };
    }
}

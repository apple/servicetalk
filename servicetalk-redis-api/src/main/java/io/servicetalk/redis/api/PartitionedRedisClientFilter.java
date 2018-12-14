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

import io.servicetalk.client.api.partition.PartitionAttributes;
import io.servicetalk.concurrent.api.Completable;
import io.servicetalk.concurrent.api.Publisher;
import io.servicetalk.concurrent.api.Single;
import io.servicetalk.transport.api.ExecutionContext;

import java.util.function.Function;

import static java.util.Objects.requireNonNull;

/**
 * A {@link PartitionedRedisClient} that delegates all calls to another {@link PartitionedRedisClient}.
 */
public class PartitionedRedisClientFilter extends PartitionedRedisClient {

    private final PartitionedRedisClient delegate;
    private final RedisExecutionStrategy defaultStrategy;

    /**
     * New instance.
     *
     * @param delegate {@link PartitionedRedisClient} to delegate.
     */
    public PartitionedRedisClientFilter(final PartitionedRedisClient delegate) {
        this.delegate = requireNonNull(delegate);
        this.defaultStrategy = executionStrategy();
    }

    /**
     * New instance.
     *
     * @param delegate {@link PartitionedRedisClient} to delegate.
     * @param defaultStrategy Default {@link RedisExecutionStrategy} to use.
     */
    public PartitionedRedisClientFilter(final PartitionedRedisClient delegate,
                                        final RedisExecutionStrategy defaultStrategy) {
        this.delegate = delegate;
        this.defaultStrategy = defaultStrategy;
    }

    @Override
    public Publisher<RedisData> request(final RedisExecutionStrategy strategy,
                                        final PartitionAttributes partitionSelector, final RedisRequest request) {
        return delegate.request(partitionSelector, request);
    }

    @Override
    public final Publisher<RedisData> request(final PartitionAttributes partitionSelector, final RedisRequest request) {
        return request(defaultStrategy, partitionSelector, request);
    }

    @Override
    public <R> Single<R> request(final RedisExecutionStrategy strategy, final PartitionAttributes partitionSelector,
                                 final RedisRequest request, final Class<R> responseType) {
        return delegate.request(strategy, partitionSelector, request, responseType);
    }

    @Override
    public final <R> Single<R> request(final PartitionAttributes partitionSelector, final RedisRequest request,
                                       final Class<R> responseType) {
        return request(defaultStrategy, partitionSelector, request, responseType);
    }

    @Override
    public Single<? extends RedisClient.ReservedRedisConnection> reserveConnection(
            final PartitionAttributes partitionSelector, final RedisProtocolSupport.Command command) {
        return reserveConnection(defaultStrategy, partitionSelector, command);
    }

    @Override
    public Single<? extends RedisClient.ReservedRedisConnection> reserveConnection(
            final RedisExecutionStrategy strategy, final PartitionAttributes partitionSelector,
            final RedisProtocolSupport.Command command) {
        return delegate.reserveConnection(strategy, partitionSelector, command);
    }

    @Override
    public ExecutionContext executionContext() {
        return delegate.executionContext();
    }

    @Override
    public Function<RedisProtocolSupport.Command, RedisPartitionAttributesBuilder>
    redisPartitionAttributesBuilderFunction() {
        return delegate.redisPartitionAttributesBuilderFunction();
    }

    @Override
    public Completable onClose() {
        return delegate.onClose();
    }

    @Override
    public Completable closeAsync() {
        return delegate.closeAsync();
    }

    @Override
    public Completable closeAsyncGracefully() {
        return delegate.closeAsyncGracefully();
    }

    /**
     * The {@link PartitionedRedisClient} to which all calls are delegated to.
     *
     * @return {@link PartitionedRedisClient} to which all calls are delegated.
     */
    protected final PartitionedRedisClient delegate() {
        return delegate;
    }
}

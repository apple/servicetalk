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

import io.servicetalk.buffer.api.Buffer;
import io.servicetalk.client.api.partition.PartitionAttributes;
import io.servicetalk.concurrent.api.ListenableAsyncCloseable;
import io.servicetalk.concurrent.api.Publisher;
import io.servicetalk.concurrent.api.Single;
import io.servicetalk.redis.api.RedisClient.ReservedRedisConnection;
import io.servicetalk.redis.api.RedisProtocolSupport.Command;
import io.servicetalk.transport.api.ExecutionContext;

import java.util.concurrent.atomic.AtomicReferenceFieldUpdater;
import java.util.function.Function;
import javax.annotation.Nullable;

import static java.util.concurrent.atomic.AtomicReferenceFieldUpdater.newUpdater;

/**
 * A variant of {@link RedisClient} which supports partitioning on the server side.
 */
public abstract class PartitionedRedisClient implements ListenableAsyncCloseable {
    private static final AtomicReferenceFieldUpdater<PartitionedRedisClient, RedisCommander> redisCommanderUpdater =
        newUpdater(PartitionedRedisClient.class, RedisCommander.class, "redisCommander");
    private static final AtomicReferenceFieldUpdater<PartitionedRedisClient, BufferRedisCommander>
            redisBufferCommanderUpdater = newUpdater(
                    PartitionedRedisClient.class, BufferRedisCommander.class, "redisBufferCommander");

    @SuppressWarnings("unused")
    @Nullable
    private volatile RedisCommander redisCommander;
    @SuppressWarnings("unused")
    @Nullable
    private volatile BufferRedisCommander redisBufferCommander;

    /**
     * Send a {@code request}.
     *
     * @param request the {@link RedisRequest} to send.
     * @param partitionSelector Defines the partition(s) that {@code request} can be sent to.
     * @return the response as a {@link Publisher}.
     */
    public abstract Publisher<RedisData> request(PartitionAttributes partitionSelector, RedisRequest request);

    /**
     * Send a {@code request} which expects the specified response type.
     *
     * @param partitionSelector Defines the partition(s) that {@code request} can be sent to.
     * @param request      the {@link RedisRequest} to send.
     * @param responseType the {@link Class} to coerce the response to.
     * @param <R>          the type of the response.
     * @return the response as a {@link Single}.
     */
    public abstract <R> Single<R> request(PartitionAttributes partitionSelector, RedisRequest request,
                                          Class<R> responseType);

    /**
     * Reserve a {@link RedisConnection} for exclusive use. Caller is responsible for invoking
     * {@link ReservedRedisConnection#releaseAsync()} after done using the return value.
     * @param partitionSelector Defines the partition(s) that are valid for the returned {@link RedisConnection}.
     * @param command A command representing how the returned {@link ReservedRedisConnection} will be used. It is
     * possible that this {@link RedisClient} will return different types of {@link ReservedRedisConnection} depending
     * on usage. For example {@link Command#SUBSCRIBE} and {@link Command#MONITOR} may be treated differently than other
     * request/response based commands.
     * @return a {@link RedisConnection}.
     */
    public abstract Single<? extends ReservedRedisConnection> reserveConnection(PartitionAttributes partitionSelector,
                                                                                Command command);

    /**
     * Get the {@link ExecutionContext} used during construction of this object.
     * <p>
     * Note that the {@link ExecutionContext#ioExecutor()} will not necessarily be associated with a specific thread
     * unless that was how this object was built.
     * @return the {@link ExecutionContext} used during construction of this object.
     */
    public abstract ExecutionContext executionContext();

    /**
     * Get the {@link Function} that is responsible for generating a {@link RedisPartitionAttributesBuilder} for each
     * {@link Command}.
     * @return the {@link Function} that is responsible for generating a {@link RedisPartitionAttributesBuilder} for
     * each {@link Command}.
     */
    protected abstract Function<Command, RedisPartitionAttributesBuilder> redisPartitionAttributesBuilderFunction();

    /**
     * Provides an alternative java API to this {@link PartitionedRedisClient}. The {@link RedisCommander} return value
     * has equivalent networking semantics and lifetime as this {@link PartitionedRedisClient}, and exists primarily to
     * provide a more expressive java API targeted at the Redis protocol which favors {@link CharSequence} and
     * {@link String}.
     * <p>
     * Calling {@link RedisCommander#closeAsync()} will also close this {@link PartitionedRedisClient}!
     * @return an alternative java API to this {@link PartitionedRedisClient}.
     */
    public final RedisCommander asCommander() {
        RedisCommander redisCommander = this.redisCommander;
        if (redisCommander == null) {
            redisCommander = new DefaultPartitionedRedisCommander(this, redisPartitionAttributesBuilderFunction());
            if (!redisCommanderUpdater.compareAndSet(this, null, redisCommander)) {
                redisCommander = this.redisCommander;
                assert redisCommander != null : "RedisCommander can not be null.";
            }
        }
        return redisCommander;
    }

    /**
     * Provides an alternative java API to this {@link PartitionedRedisClient}. The {@link BufferRedisCommander} return\
     * value has equivalent networking semantics and lifetime as this {@link PartitionedRedisClient}, and exists
     * primarily to provide a more expressive java API targeted at the Redis protocol which favors {@link Buffer}.
     * <p>
     * Calling {@link BufferRedisCommander#closeAsync()} will also close this {@link PartitionedRedisClient}!
     *
     * @return an alternative java API to this {@link PartitionedRedisClient}.
     */
    public final BufferRedisCommander asBufferCommander() {
        BufferRedisCommander redisBufferCommander = this.redisBufferCommander;
        if (redisBufferCommander == null) {
            redisBufferCommander = new DefaultPartitionedBufferRedisCommander(this,
                    redisPartitionAttributesBuilderFunction());
            if (!redisBufferCommanderUpdater.compareAndSet(this, null, redisBufferCommander)) {
                redisBufferCommander = this.redisBufferCommander;
                assert redisBufferCommander != null : "BufferRedisCommander can not be null.";
            }
        }
        return redisBufferCommander;
    }

    /**
     * Provides an alternative java API to this {@link PartitionedRedisClient}. The {@link BlockingRedisCommander} API
     * is provided for convenience for a more familiar sequential programming model. The return value has equivalent
     * networking semantics and lifetime as this {@link PartitionedRedisClient}, and exists primarily to provide a more
     * expressive java API targeted at the Redis protocol which favors {@link CharSequence} and {@link String}.
     * <p>
     * Calling {@link RedisCommander#closeAsync()} will also close this {@link PartitionedRedisClient}!
     *
     * @return an alternative java API to this {@link PartitionedRedisClient}.
     */
    public final BlockingRedisCommander asBlockingCommander() {
        return asCommander().asBlockingCommander();
    }

    /**
     * Provides an alternative java API to this {@link PartitionedRedisClient}. The
     * {@link BlockingBufferRedisCommander} API is provided for convenience for a more familiar sequential programming
     * model. The return value has equivalent networking semantics and lifetime as this {@link PartitionedRedisClient},
     * and exists primarily to provide a more expressive java API targeted at the Redis protocol which favors
     * {@link Buffer}.
     * <p>
     * Calling {@link BufferRedisCommander#closeAsync()} will also close this {@link PartitionedRedisClient}!
     * @return an alternative java API to this {@link PartitionedRedisClient}.
     */
    public final BlockingBufferRedisCommander asBlockingBufferCommander() {
        return asBufferCommander().asBlockingBufferCommander();
    }
}

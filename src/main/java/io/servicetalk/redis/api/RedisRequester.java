/**
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

import io.servicetalk.buffer.Buffer;
import io.servicetalk.buffer.BufferAllocator;
import io.servicetalk.concurrent.api.ListenableAsyncCloseable;
import io.servicetalk.concurrent.api.Publisher;
import io.servicetalk.concurrent.api.Single;
import io.servicetalk.redis.api.RedisRequesterUtils.ToBufferSingle;
import io.servicetalk.redis.api.RedisRequesterUtils.ToListSingle;
import io.servicetalk.redis.api.RedisRequesterUtils.ToLongSingle;
import io.servicetalk.redis.api.RedisRequesterUtils.ToStringSingle;
import io.servicetalk.redis.internal.RedisUtils.ListWithBuffersCoercedToCharSequences;

import java.util.List;
import java.util.concurrent.atomic.AtomicReferenceFieldUpdater;

import javax.annotation.Nullable;

import static io.servicetalk.concurrent.api.Single.error;

/**
 * Provides a means to make a redis request.
 */
public abstract class RedisRequester implements ListenableAsyncCloseable {
    private static final AtomicReferenceFieldUpdater<RedisRequester, RedisCommander> redisCommanderUpdater =
            AtomicReferenceFieldUpdater.newUpdater(RedisRequester.class, RedisCommander.class, "redisCommander");
    private static final AtomicReferenceFieldUpdater<RedisRequester, BufferRedisCommander> redisBufferCommanderUpdater =
            AtomicReferenceFieldUpdater.newUpdater(RedisRequester.class, BufferRedisCommander.class, "redisBufferCommander");

    @SuppressWarnings("unused")
    @Nullable
    private volatile RedisCommander redisCommander;
    @SuppressWarnings("unused")
    @Nullable
    private volatile BufferRedisCommander redisBufferCommander;

    /**
     * @return the {@link BufferAllocator} used by this requestor.
     */
    public abstract BufferAllocator getBufferAllocator();

    /**
     * @param request the {@link RedisRequest} to send.
     * @return the response as a {@link Publisher}.
     */
    public abstract Publisher<RedisData> request(RedisRequest request);

    /**
     * @param request      the {@link RedisRequest} to send.
     * @param responseType the {@link Class} to coerce the response to.
     * @param <R>          the type of the response.
     * @return the response as a {@link Single}.
     */
    public <R> Single<R> request(final RedisRequest request, final Class<R> responseType) {
        if (CharSequence.class.isAssignableFrom(responseType)) {
            return new ToStringSingle<>(this, request);
        }
        if (Buffer.class.isAssignableFrom(responseType)) {
            return new ToBufferSingle<>(this, request);
        }
        if (Long.class.isAssignableFrom(responseType)) {
            return new ToLongSingle<>(this, request);
        }
        if (ListWithBuffersCoercedToCharSequences.class.isAssignableFrom(responseType)) {
            return new ToListSingle<>(this, request, true);
        }
        if (List.class.isAssignableFrom(responseType)) {
            return new ToListSingle<>(this, request, false);
        }
        return error(new IllegalArgumentException("Unsupported type: " + responseType));
    }

    /**
     * Provides an alternative java API to this {@link RedisRequester}. The {@link RedisCommander} return value has
     * equivalent networking semantics and lifetime as this {@link RedisRequester}, and exists primarily to provide a more
     * expressive java API targeted at the Redis protocol which favors {@link CharSequence} and {@link String}.
     * <p>
     * Calling {@link RedisCommander#closeAsync()} will also close this {@link RedisRequester}!
     *
     * @return an alternative java API to this {@link RedisRequester}.
     */
    public final RedisCommander asCommander() {
        RedisCommander redisCommander = this.redisCommander;
        if (redisCommander == null) {
            redisCommander = new DefaultRedisCommander(this);
            if (!redisCommanderUpdater.compareAndSet(this, null, redisCommander)) {
                redisCommander = this.redisCommander;
                assert redisCommander != null : "RedisCommander can not be null.";
            }
        }
        return redisCommander;
    }

    /**
     * Provides an alternative java API to this {@link RedisRequester}. The {@link BufferRedisCommander} return value has
     * equivalent networking semantics and lifetime as this {@link RedisRequester}, and exists primarily to provide a more
     * expressive java API targeted at the Redis protocol which favors {@link Buffer}.
     * <p>
     * Calling {@link BufferRedisCommander#closeAsync()} will also close this {@link RedisRequester}!
     *
     * @return an alternative java API to this {@link RedisRequester}.
     */
    public final BufferRedisCommander asBufferCommander() {
        BufferRedisCommander redisBufferCommander = this.redisBufferCommander;
        if (redisBufferCommander == null) {
            redisBufferCommander = new DefaultBufferRedisCommander(this);
            if (!redisBufferCommanderUpdater.compareAndSet(this, null, redisBufferCommander)) {
                redisBufferCommander = this.redisBufferCommander;
                assert redisBufferCommander != null : "BufferRedisCommander can not be null.";
            }
        }
        return redisBufferCommander;
    }
}

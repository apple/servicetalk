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

import io.servicetalk.buffer.BufferAllocator;
import io.servicetalk.buffer.CompositeBuffer;
import io.servicetalk.concurrent.api.Completable;
import io.servicetalk.concurrent.api.Publisher;
import io.servicetalk.concurrent.api.Single;

import javax.annotation.Generated;

import static io.servicetalk.redis.api.RedisCoercions.toPubSubPongMessages;
import static io.servicetalk.redis.api.RedisRequests.addRequestArgument;
import static io.servicetalk.redis.api.RedisRequests.newConnectedClient;
import static io.servicetalk.redis.api.RedisRequests.newRequest;
import static io.servicetalk.redis.api.RedisRequests.newRequestCompositeBuffer;
import static java.util.Objects.requireNonNull;

@Generated({})
@SuppressWarnings("unchecked")
final class DefaultPubSubRedisConnection implements PubSubRedisConnection {

    protected final RedisClient.ReservedRedisConnection reservedCnx;

    protected final Publisher<PubSubRedisMessage> publisher;

    DefaultPubSubRedisConnection(final RedisClient.ReservedRedisConnection reservedCnx,
                final Publisher<PubSubRedisMessage> publisher) {
        this.reservedCnx = requireNonNull(reservedCnx);
        this.publisher = requireNonNull(publisher);
    }

    @Override
    public Completable closeAsync() {
        return reservedCnx.closeAsync();
    }

    @Override
    public Publisher<PubSubRedisMessage> getMessages() {
        return publisher;
    }

    @Override
    public Single<PubSubRedisConnection> psubscribe(final CharSequence pattern) {
        requireNonNull(pattern);
        final BufferAllocator allocator = reservedCnx.getBufferAllocator();
        // Compute the number of request arguments, accounting for nullable ones
        int len = 2;
        final CompositeBuffer cb = newRequestCompositeBuffer(len, RedisProtocolSupport.Command.PSUBSCRIBE, allocator);
        addRequestArgument(pattern, cb, allocator);
        final RedisRequest request = newRequest(RedisProtocolSupport.Command.PSUBSCRIBE, cb);
        return newConnectedClient(reservedCnx, request,
                    (rcnx, pub) -> new DefaultPubSubRedisConnection(rcnx, pub.map(msg -> (PubSubRedisMessage) msg)));
    }

    @Override
    public Single<PubSubRedisConnection> subscribe(final CharSequence channel) {
        requireNonNull(channel);
        final BufferAllocator allocator = reservedCnx.getBufferAllocator();
        // Compute the number of request arguments, accounting for nullable ones
        int len = 2;
        final CompositeBuffer cb = newRequestCompositeBuffer(len, RedisProtocolSupport.Command.SUBSCRIBE, allocator);
        addRequestArgument(channel, cb, allocator);
        final RedisRequest request = newRequest(RedisProtocolSupport.Command.SUBSCRIBE, cb);
        return newConnectedClient(reservedCnx, request,
                    (rcnx, pub) -> new DefaultPubSubRedisConnection(rcnx, pub.map(msg -> (PubSubRedisMessage) msg)));
    }

    @Override
    public Single<PubSubRedisMessage.Pong<String>> ping() {
        final BufferAllocator allocator = reservedCnx.getBufferAllocator();
        // Compute the number of request arguments, accounting for nullable ones
        int len = 1;
        final CompositeBuffer cb = newRequestCompositeBuffer(len, RedisProtocolSupport.Command.PING, allocator);
        final RedisRequest request = newRequest(RedisProtocolSupport.Command.PING, cb);
        return toPubSubPongMessages(reservedCnx.request(request), String.class).first();
    }

    @Override
    public Single<PubSubRedisMessage.Pong<String>> ping(final CharSequence message) {
        requireNonNull(message);
        final BufferAllocator allocator = reservedCnx.getBufferAllocator();
        // Compute the number of request arguments, accounting for nullable ones
        int len = 2;
        final CompositeBuffer cb = newRequestCompositeBuffer(len, RedisProtocolSupport.Command.PING, allocator);
        addRequestArgument(message, cb, allocator);
        final RedisRequest request = newRequest(RedisProtocolSupport.Command.PING, cb);
        return toPubSubPongMessages(reservedCnx.request(request), String.class).first();
    }
}

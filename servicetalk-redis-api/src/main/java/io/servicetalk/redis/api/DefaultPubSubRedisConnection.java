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
import io.servicetalk.concurrent.api.Completable;
import io.servicetalk.concurrent.api.Publisher;
import io.servicetalk.concurrent.api.Single;

import java.nio.charset.StandardCharsets;
import javax.annotation.Generated;

import static io.servicetalk.redis.api.RedisCoercions.toPubSubPongMessages;
import static io.servicetalk.redis.api.RedisRequests.calculateInitialCommandBufferSize;
import static io.servicetalk.redis.api.RedisRequests.calculateRequestArgumentSize;
import static io.servicetalk.redis.api.RedisRequests.newRequest;
import static io.servicetalk.redis.api.RedisRequests.reserveConnection;
import static io.servicetalk.redis.api.RedisRequests.writeRequestArgument;
import static io.servicetalk.redis.api.RedisRequests.writeRequestArraySize;
import static java.util.Objects.requireNonNull;

@Generated({})
@SuppressWarnings("unchecked")
final class DefaultPubSubRedisConnection extends PubSubRedisConnection {

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
    public Completable closeAsyncGracefully() {
        return reservedCnx.closeAsyncGracefully();
    }

    @Override
    public Publisher<PubSubRedisMessage> getMessages() {
        return publisher;
    }

    @Override
    public Single<PubSubRedisMessage.Pong<String>> ping() {
        final int len = 1;
        final int capacity = calculateInitialCommandBufferSize(len, RedisProtocolSupport.Command.PING);
        Buffer buffer = reservedCnx.executionContext().bufferAllocator().newBuffer(capacity);
        writeRequestArraySize(buffer, len);
        RedisProtocolSupport.Command.PING.encodeTo(buffer);
        final RedisRequest request = newRequest(RedisProtocolSupport.Command.PING, buffer);
        return toPubSubPongMessages(reservedCnx.request(request), String.class).first();
    }

    @Override
    public Single<PubSubRedisMessage.Pong<String>> ping(final CharSequence message) {
        requireNonNull(message);
        final int len = 2;
        final byte[] messageBytes = message.toString().getBytes(StandardCharsets.UTF_8);
        final int capacity = calculateInitialCommandBufferSize(len, RedisProtocolSupport.Command.PING) +
                    calculateRequestArgumentSize(messageBytes);
        Buffer buffer = reservedCnx.executionContext().bufferAllocator().newBuffer(capacity);
        writeRequestArraySize(buffer, len);
        RedisProtocolSupport.Command.PING.encodeTo(buffer);
        writeRequestArgument(buffer, messageBytes);
        final RedisRequest request = newRequest(RedisProtocolSupport.Command.PING, buffer);
        return toPubSubPongMessages(reservedCnx.request(request), String.class).first();
    }

    @Override
    public Single<PubSubRedisConnection> psubscribe(final CharSequence pattern) {
        requireNonNull(pattern);
        final int len = 2;
        final byte[] patternBytes = pattern.toString().getBytes(StandardCharsets.UTF_8);
        final int capacity = calculateInitialCommandBufferSize(len, RedisProtocolSupport.Command.PSUBSCRIBE) +
                    calculateRequestArgumentSize(patternBytes);
        Buffer buffer = reservedCnx.executionContext().bufferAllocator().newBuffer(capacity);
        writeRequestArraySize(buffer, len);
        RedisProtocolSupport.Command.PSUBSCRIBE.encodeTo(buffer);
        writeRequestArgument(buffer, patternBytes);
        final RedisRequest request = newRequest(RedisProtocolSupport.Command.PSUBSCRIBE, buffer);
        return reserveConnection(reservedCnx, request,
                    (rcnx, pub) -> new DefaultPubSubRedisConnection(rcnx, pub.map(msg -> (PubSubRedisMessage) msg)));
    }

    @Override
    public Single<PubSubRedisConnection> subscribe(final CharSequence channel) {
        requireNonNull(channel);
        final int len = 2;
        final byte[] channelBytes = channel.toString().getBytes(StandardCharsets.UTF_8);
        final int capacity = calculateInitialCommandBufferSize(len, RedisProtocolSupport.Command.SUBSCRIBE) +
                    calculateRequestArgumentSize(channelBytes);
        Buffer buffer = reservedCnx.executionContext().bufferAllocator().newBuffer(capacity);
        writeRequestArraySize(buffer, len);
        RedisProtocolSupport.Command.SUBSCRIBE.encodeTo(buffer);
        writeRequestArgument(buffer, channelBytes);
        final RedisRequest request = newRequest(RedisProtocolSupport.Command.SUBSCRIBE, buffer);
        return reserveConnection(reservedCnx, request,
                    (rcnx, pub) -> new DefaultPubSubRedisConnection(rcnx, pub.map(msg -> (PubSubRedisMessage) msg)));
    }
}

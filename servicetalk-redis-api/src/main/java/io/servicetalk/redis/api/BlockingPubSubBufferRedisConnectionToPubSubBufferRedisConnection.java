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

import java.util.Objects;
import javax.annotation.Generated;

import static io.servicetalk.redis.api.BlockingUtils.blockingToCompletable;
import static io.servicetalk.redis.api.BlockingUtils.blockingToPublisher;
import static io.servicetalk.redis.api.BlockingUtils.blockingToSingle;

@Generated({})
@SuppressWarnings("unchecked")
final class BlockingPubSubBufferRedisConnectionToPubSubBufferRedisConnection extends PubSubBufferRedisConnection {

    private final BlockingPubSubBufferRedisConnection reservedCnx;

    BlockingPubSubBufferRedisConnectionToPubSubBufferRedisConnection(
                final BlockingPubSubBufferRedisConnection reservedCnx) {
        this.reservedCnx = Objects.requireNonNull(reservedCnx);
    }

    @Override
    public Completable closeAsync() {
        return blockingToCompletable(reservedCnx::close);
    }

    @Override
    public Completable closeAsyncGracefully() {
        return closeAsync();
    }

    @Override
    public Publisher<PubSubRedisMessage> getMessages() {
        return blockingToPublisher(() -> reservedCnx.getMessages());
    }

    @Override
    public Single<PubSubRedisMessage.Pong<Buffer>> ping() {
        return blockingToSingle(() -> reservedCnx.ping());
    }

    @Override
    public Single<PubSubRedisMessage.Pong<Buffer>> ping(final Buffer message) {
        return blockingToSingle(() -> reservedCnx.ping(message));
    }

    @Override
    public Single<PubSubBufferRedisConnection> psubscribe(final Buffer pattern) {
        return blockingToSingle(() -> new BlockingPubSubBufferRedisConnectionToPubSubBufferRedisConnection(
                    reservedCnx.psubscribe(pattern)));
    }

    @Override
    public Single<PubSubBufferRedisConnection> subscribe(final Buffer channel) {
        return blockingToSingle(() -> new BlockingPubSubBufferRedisConnectionToPubSubBufferRedisConnection(
                    reservedCnx.subscribe(channel)));
    }
}

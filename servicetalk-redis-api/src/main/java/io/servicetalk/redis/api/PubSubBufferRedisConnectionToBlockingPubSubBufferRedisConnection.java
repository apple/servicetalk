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
import io.servicetalk.concurrent.BlockingIterable;

import java.util.Objects;
import javax.annotation.Generated;

import static io.servicetalk.redis.api.BlockingUtils.blockingInvocation;

@Generated({})
@SuppressWarnings("unchecked")
final class PubSubBufferRedisConnectionToBlockingPubSubBufferRedisConnection extends BlockingPubSubBufferRedisConnection {

    private final PubSubBufferRedisConnection pubSubConnection;

    PubSubBufferRedisConnectionToBlockingPubSubBufferRedisConnection(
                final PubSubBufferRedisConnection pubSubConnection) {
        this.pubSubConnection = Objects.requireNonNull(pubSubConnection);
    }

    @Override
    public void close() throws Exception {
        blockingInvocation(pubSubConnection.closeAsync());
    }

    public BlockingIterable<PubSubRedisMessage> getMessages() {
        return pubSubConnection.getMessages().toIterable();
    }

    @Override
    public PubSubRedisMessage.Pong<Buffer> ping() throws Exception {
        return blockingInvocation(pubSubConnection.ping());
    }

    @Override
    public PubSubRedisMessage.Pong<Buffer> ping(final Buffer message) throws Exception {
        return blockingInvocation(pubSubConnection.ping(message));
    }

    @Override
    public BlockingPubSubBufferRedisConnection psubscribe(final Buffer pattern) throws Exception {
        return blockingInvocation(pubSubConnection.psubscribe(pattern)
                    .map(PubSubBufferRedisConnectionToBlockingPubSubBufferRedisConnection::new));
    }

    @Override
    public BlockingPubSubBufferRedisConnection subscribe(final Buffer channel) throws Exception {
        return blockingInvocation(pubSubConnection.subscribe(channel)
                    .map(PubSubBufferRedisConnectionToBlockingPubSubBufferRedisConnection::new));
    }
}

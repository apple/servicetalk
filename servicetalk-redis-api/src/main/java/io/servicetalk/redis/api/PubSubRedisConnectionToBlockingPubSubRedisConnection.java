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

import io.servicetalk.concurrent.BlockingIterable;

import java.util.Objects;
import javax.annotation.Generated;

import static io.servicetalk.redis.api.BlockingUtils.blockingInvocation;

@Generated({})
@SuppressWarnings("unchecked")
final class PubSubRedisConnectionToBlockingPubSubRedisConnection extends BlockingPubSubRedisConnection {

    private final PubSubRedisConnection pubSubConnection;

    PubSubRedisConnectionToBlockingPubSubRedisConnection(final PubSubRedisConnection pubSubConnection) {
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
    public PubSubRedisMessage.Pong<String> ping() throws Exception {
        return blockingInvocation(pubSubConnection.ping());
    }

    @Override
    public PubSubRedisMessage.Pong<String> ping(final CharSequence message) throws Exception {
        return blockingInvocation(pubSubConnection.ping(message));
    }

    @Override
    public BlockingPubSubRedisConnection psubscribe(final CharSequence pattern) throws Exception {
        return blockingInvocation(pubSubConnection.psubscribe(pattern)
                    .map(PubSubRedisConnectionToBlockingPubSubRedisConnection::new));
    }

    @Override
    public BlockingPubSubRedisConnection subscribe(final CharSequence channel) throws Exception {
        return blockingInvocation(
                    pubSubConnection.subscribe(channel).map(PubSubRedisConnectionToBlockingPubSubRedisConnection::new));
    }
}

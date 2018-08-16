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

import javax.annotation.Generated;

/**
 * A Redis command client that is subscribed to one channel. This API is provided for convenience for a more familiar
 * sequential programming model.
 */
@Generated({})
public abstract class BlockingPubSubRedisConnection implements AutoCloseable {

    /**
     * {@inheritDoc}
     * <p>
     * This will close the underlying {@link RedisRequester}!
     */
    @Override
    public abstract void close() throws Exception;

    /**
     * Messages that are received by this client.
     *
     * @return a {@link BlockingIterable} of messages
     */
    public abstract BlockingIterable<PubSubRedisMessage> getMessages();

    /**
     * Ping the server.
     *
     * @return a {@link PubSubRedisMessage.Pong} result
     * @throws Exception if an exception occurs during the request processing.
     */
    @RedisProtocolSupport.Cmd(RedisProtocolSupport.Command.PING)
    public abstract PubSubRedisMessage.Pong<String> ping() throws Exception;

    /**
     * Ping the server.
     *
     * @param message the message
     * @return a {@link PubSubRedisMessage.Pong} result
     * @throws Exception if an exception occurs during the request processing.
     */
    @RedisProtocolSupport.Cmd(RedisProtocolSupport.Command.PING)
    public abstract PubSubRedisMessage.Pong<String> ping(CharSequence message) throws Exception;

    /**
     * Listen for messages published to channels matching the given patterns.
     *
     * @param pattern the pattern
     * @return a {@link BlockingPubSubRedisConnection} result
     * @throws Exception if an exception occurs during the request processing.
     */
    @RedisProtocolSupport.Cmd(RedisProtocolSupport.Command.PSUBSCRIBE)
    public abstract BlockingPubSubRedisConnection psubscribe(CharSequence pattern) throws Exception;

    /**
     * Listen for messages published to the given channels.
     *
     * @param channel the channel
     * @return a {@link BlockingPubSubRedisConnection} result
     * @throws Exception if an exception occurs during the request processing.
     */
    @RedisProtocolSupport.Cmd(RedisProtocolSupport.Command.SUBSCRIBE)
    public abstract BlockingPubSubRedisConnection subscribe(CharSequence channel) throws Exception;
}

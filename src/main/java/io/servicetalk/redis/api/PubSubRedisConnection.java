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

import io.servicetalk.concurrent.api.AsyncCloseable;
import io.servicetalk.concurrent.api.Publisher;
import io.servicetalk.concurrent.api.Single;

import javax.annotation.Generated;

/**
 * A Redis command client that is subscribed to one channel.
 */
@Generated({})
public interface PubSubRedisConnection extends AsyncCloseable {

    /**
     * Messages that are received by this client.
     * @return a {@link Publisher} of messages
     */
    Publisher<PubSubRedisMessage> getMessages();

    /**
     * Listen for messages published to channels matching the given patterns.
     * @param pattern the pattern
     * @return a {@link Single} result
     */
    @RedisProtocolSupport.Cmd(RedisProtocolSupport.Command.PSUBSCRIBE)
    Single<PubSubRedisConnection> psubscribe(CharSequence pattern);

    /**
     * Listen for messages published to the given channels.
     * @param channel the channel
     * @return a {@link Single} result
     */
    @RedisProtocolSupport.Cmd(RedisProtocolSupport.Command.SUBSCRIBE)
    Single<PubSubRedisConnection> subscribe(CharSequence channel);

    /**
     * Ping the server.
     * @return a {@link Single} result
     */
    @RedisProtocolSupport.Cmd(RedisProtocolSupport.Command.PING)
    Single<PubSubRedisMessage.Pong<String>> ping();

    /**
     * Ping the server.
     * @param message the message
     * @return a {@link Single} result
     */
    @RedisProtocolSupport.Cmd(RedisProtocolSupport.Command.PING)
    Single<PubSubRedisMessage.Pong<String>> ping(CharSequence message);
}

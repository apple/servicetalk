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

import io.servicetalk.buffer.Buffer;
import io.servicetalk.redis.api.RedisData.CompleteRedisData;
import io.servicetalk.redis.api.RedisProtocolSupport.Command;

/**
 * A message delivered by the Redis server when the client is in subscribed mode.
 *
 * @see <a href="https://redis.io/topics/pubsub">Redis Pub/Sub</a>
 */
public interface PubSubRedisMessage extends CompleteRedisData {
    /**
     * A {@link PubSubRedisMessage} for responses of {@link Command#SUBSCRIBE} commands.
     */
    interface ChannelPubSubRedisMessage extends PubSubRedisMessage {
        /**
         * @return the channel on which the message was received
         */
        String getChannel();
    }

    /**
     * A {@link PubSubRedisMessage} for responses of {@link Command#PSUBSCRIBE} commands.
     */
    interface PatternPubSubRedisMessage extends ChannelPubSubRedisMessage {
        /**
         * @return the pattern that matched the channel on which the message was received
         */
        String getPattern();
    }

    /**
     * @param <T> the type of content stored in this message.
     */
    abstract class BasePubSubRedisMessage<T> extends DefaultBaseRedisData<T> {
        BasePubSubRedisMessage(final T content) {
            super(content);
        }

        @Override
        public Buffer getBufferValue() {
            T value = getValue();
            return value instanceof Buffer ? (Buffer) value : super.getBufferValue();
        }

        @Override
        public CharSequence getCharSequenceValue() {
            T value = getValue();
            return value instanceof CharSequence ? (CharSequence) value : super.getCharSequenceValue();
        }
    }

    /**
     * @param <T> the type of used for characters ({@link String} or {@link Buffer}).
     */
    final class Pong<T> extends BasePubSubRedisMessage<T> implements PubSubRedisMessage {
        Pong(final T content) {
            super(content);
        }
    }

    /**
     * Response for a {@link Command#QUIT} command.
     */
    final class QUIT extends BasePubSubRedisMessage<SimpleString> implements PubSubRedisMessage {
        public static final PubSubRedisMessage QUIT_PUB_SUB_MSG = new QUIT();

        private QUIT() {
            super(RedisData.OK);
        }
    }
}

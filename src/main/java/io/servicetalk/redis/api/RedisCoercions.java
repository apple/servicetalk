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
import io.servicetalk.concurrent.api.Publisher;
import io.servicetalk.redis.api.RedisData.CompleteBulkString;
import io.servicetalk.redis.api.RedisData.CompleteRedisData;
import io.servicetalk.redis.internal.CoercionException;

import javax.annotation.Nullable;

import static java.nio.charset.StandardCharsets.US_ASCII;
import static java.nio.charset.StandardCharsets.UTF_8;

/**
 * <strong>Important:</strong>
 * We realize the restrictions of <a href="https://github.com/reactive-streams/reactive-streams-jvm/blob/v1.0.1/README.md#2.13">Reactive Streams 2.13</a>,
 * but in this case the "source publisher" is internal and known to handle exceptions in such a way that the exception
 * will be pushed to {@link org.reactivestreams.Subscriber#onError(Throwable)} and untimely to the user.
 * This is decided to be a better alternative than just cancelling the {@link org.reactivestreams.Subscription} and logging an
 * error because it provides more visibility and direct feedback for users.
 */
final class RedisCoercions {

    private RedisCoercions() {
        // no instances
    }

    static String simpleStringToString(final RedisData data) {
        return data.toString();
    }

    @SuppressWarnings("unchecked")
    static <V> Publisher<PubSubRedisMessage.Pong<V>> toPubSubPongMessages(final Publisher<RedisData> original, final Class<V> valueType) {
        return original.map(msg -> {
            V v;
            if (Buffer.class.equals(valueType)) {
                v = (V) msg.getBufferValue();
            } else if (String.class.equals(valueType)) {
                v = (V) msg.getCharSequenceValue().toString();
            } else {
                throw new CoercionException(msg, valueType);
            }
            return new PubSubRedisMessage.Pong(v);
        });
    }

    @Nullable
    static String toString(final CompleteRedisData data) throws CoercionException {
        if (data instanceof RedisData.Null) {
            return null;
        }
        if (data instanceof RedisData.SimpleString) {
            return data.getCharSequenceValue().toString();
        }
        if (data instanceof CompleteBulkString) {
            return data.getBufferValue().toString(UTF_8);
        }

        throw new CoercionException(data, String.class);
    }

    static byte[] toAsciiBytes(final Number n) {
        return n.toString().getBytes(US_ASCII);
    }

    @Nullable
    static Buffer toBuffer(final CompleteRedisData data) throws CoercionException {
        if (data instanceof RedisData.Null) {
            return null;
        }
        if (data instanceof CompleteBulkString) {
            return data.getBufferValue();
        }

        throw new CoercionException(data, Buffer.class);
    }

    @Nullable
    private static <V> V toValue(final CompleteRedisData data, final Class<V> valueType) throws CoercionException {
        if (Buffer.class.equals(valueType)) {
            return valueType.cast(toBuffer(data));
        }
        if (String.class.equals(valueType)) {
            return valueType.cast(toString(data));
        }

        throw new CoercionException(data, valueType);
    }
}

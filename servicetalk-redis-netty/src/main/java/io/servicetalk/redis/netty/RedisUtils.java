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
package io.servicetalk.redis.netty;

import io.servicetalk.buffer.api.BufferAllocator;
import io.servicetalk.concurrent.api.Publisher;
import io.servicetalk.redis.api.CoercionException;
import io.servicetalk.redis.api.RedisData;
import io.servicetalk.redis.api.RedisProtocolSupport.Command;
import io.servicetalk.redis.api.RedisRequest;

import io.netty.buffer.ByteBuf;

import javax.annotation.Nullable;

import static io.servicetalk.buffer.netty.BufferUtil.toByteBuf;
import static java.nio.charset.StandardCharsets.UTF_8;

/**
 * A set of utility functions for redis.
 */
final class RedisUtils {

    private RedisUtils() {
        // no instances
    }

    /**
     * Checks if the passed {@link Command} is valid for a connection created to be used for Redis Subscribe mode.
     *
     * @param command To check.
     * @return {@code true} if the passed command is valid for Redis subscribe mode.
     */
    static boolean isSubscribeModeCommand(Command command) {
        // PING and QUIT are allowed for both modes, hence they are handled specially, if required from the caller.
        return command == Command.SUBSCRIBE || command == Command.PSUBSCRIBE || command == Command.UNSUBSCRIBE || command == Command.PUNSUBSCRIBE;
    }

    static Publisher<ByteBuf> encodeRequestContent(final RedisRequest request, final BufferAllocator allocator) {
        return request.getContent().map(data -> toByteBuf(data.toRESPArgument(allocator)));
    }

    @Nullable
    static String convertToString(final RedisData.CompleteRedisData data) throws CoercionException {
        if (data instanceof RedisData.Null) {
            return null;
        }
        if (data instanceof RedisData.SimpleString) {
            return data.getCharSequenceValue().toString();
        }
        if (data instanceof RedisData.CompleteBulkString) {
            return data.getBufferValue().toString(UTF_8);
        }

        throw new CoercionException(data, String.class);
    }
}

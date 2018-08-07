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

import io.netty.handler.codec.CodecException;

/**
 * An {@link Exception} which is thrown while encoding / decoding Redis protocol.
 */
public final class RedisCodecException extends CodecException {

    private static final long serialVersionUID = 5570454251549268063L;

    /**
     * Creates a new instance.
     *
     * @param message of the exception.
     */
    public RedisCodecException(String message) {
        super(message);
    }

    /**
     * Creates a new instance.
     *
     * @param cause the {@link Throwable} that caused the exception.
     */
    public RedisCodecException(Throwable cause) {
        super(cause);
    }
}

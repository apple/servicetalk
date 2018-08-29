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

/**
 * An exception that represents an error reported by the Redis client.
 */
public class RedisClientException extends RuntimeException {
    private static final long serialVersionUID = 1971082412459546325L;

    /**
     * Instantiates a new {@link RedisClientException}.
     *
     * @param message the message containing the details of the error.
     */
    public RedisClientException(final String message) {
        super(message);
    }
}

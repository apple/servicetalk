/**
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
package io.servicetalk.redis.utils;

/**
 * Used to indicate the <a href="https://redis.io/commands/auth">AUTH</a> command completed, but was not successful.
 */
public final class RedisAuthorizationException extends Exception {
    private static final long serialVersionUID = 4677774049867268519L;

    RedisAuthorizationException(String message) {
        super(message);
    }

    RedisAuthorizationException(String message, Throwable cause) {
        super(message, cause);
    }
}

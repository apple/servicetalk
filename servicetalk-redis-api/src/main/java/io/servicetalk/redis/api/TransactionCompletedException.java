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

import io.servicetalk.concurrent.api.Single;

/**
 * Indicates that a transaction was completed (via {@link TransactedRedisCommander#discard()} or
 * {@link TransactedRedisCommander#exec()}, preventing a command from being executed.
 */
public class TransactionCompletedException extends RedisClientException {

    /**
     * Instantiates a new {@link TransactionCompletedException}.
     */
    public TransactionCompletedException() {
        super(Single.class.getSimpleName() + " cannot be subscribed to after the transaction has completed.");
    }

    TransactionCompletedException(final String message) {
        super(message);
    }
}

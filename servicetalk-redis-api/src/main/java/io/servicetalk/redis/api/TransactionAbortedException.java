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
 * Indicates that a transaction was aborted via {@link TransactedRedisCommander#discard()}, preventing a command from
 * being executed.
 */
public final class TransactionAbortedException extends RedisClientException {
    private static final long serialVersionUID = 2625374006820886896L;

    /**
     * Instantiates a new {@link TransactionAbortedException}.
     */
    TransactionAbortedException() {
        super("Transaction was aborted by discard()");
    }
}

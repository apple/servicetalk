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
import io.servicetalk.client.api.partition.DuplicateAttributeException;
import io.servicetalk.client.api.partition.PartitionAttributes;
import io.servicetalk.client.api.partition.PartitionAttributes.Key;
import io.servicetalk.client.api.partition.PartitionAttributesBuilder;

/**
 * A specialization of {@link PartitionAttributesBuilder} for the Redis use case.
 */
public interface RedisPartitionAttributesBuilder {
    /**
     * Add a key corresponding to a request which may impact the partition.
     * @param key a key corresponding to a request which may impact the partition.
     * @return {@code this}.
     * @throws DuplicateAttributeException A best effort will be made to detect adding duplicate keys, and if a duplicate
     * key is detected this exception will be thrown.
     */
    RedisPartitionAttributesBuilder addKey(CharSequence key);

    /**
     * Add a key corresponding to a request which may impact the partition.
     * @param key a key corresponding to a request which may impact the partition.
     * @return {@code this}.
     * @throws DuplicateAttributeException A best effort will be made to detect adding duplicate keys, and if a duplicate
     * key is detected this exception will be thrown.
     */
    RedisPartitionAttributesBuilder addKey(Buffer key);

    /**
     * Build the {@link PartitionAttributes} which is used to select the partition.
     * @return the {@link PartitionAttributes} which is used to select the partition.
     * @throws DuplicateAttributeException If there are any duplicate {@link Key}s detected.
     */
    PartitionAttributes build();
}

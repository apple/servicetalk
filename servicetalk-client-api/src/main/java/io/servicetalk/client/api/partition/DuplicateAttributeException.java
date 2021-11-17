/*
 * Copyright © 2018, 2021 Apple Inc. and the ServiceTalk project authors
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
package io.servicetalk.client.api.partition;

import io.servicetalk.client.api.partition.PartitionAttributes.Key;

import static java.util.Objects.requireNonNull;

/**
 * Indicates that a duplicate value was added while constructing a {@link PartitionAttributes}.
 */
public final class DuplicateAttributeException extends IllegalStateException {
    private static final long serialVersionUID = 8374128121212690894L;

    private final Key key;

    /**
     * Create a new instance.
     * @param key The key for which the duplicate was detected.
     * @param msg The textual description of the exception.
     */
    public DuplicateAttributeException(Key key, String msg) {
        super(msg);
        this.key = requireNonNull(key);
    }

    /**
     * Get the {@link Key} which corresponds to the exception.
     * @return The key.
     */
    public Key getKey() {
        return key;
    }
}

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
package io.servicetalk.client.api.partition;

import io.servicetalk.client.api.ClientGroup;

/**
 * A builder for {@link PartitionAttributes} objects. Duplicates are not necessarily detected/enforced via the
 * {@link #add(PartitionAttributes.Key, Object)} method.
 *
 * @deprecated We are unaware of anyone using "partition" feature and plan to remove it in future releases.
 * If you depend on it, consider using {@link ClientGroup} as an alternative or reach out to the maintainers describing
 * the use-case.
 */
@Deprecated
public interface PartitionAttributesBuilder {   // FIXME: 0.43 - remove deprecated interface
    /**
     * Add a key/value pair to this builder.
     * <p>
     * Duplicate keys are not necessarily detected, and this will instead just add.
     * @param key The key.
     * @param value The value.
     * @param <T> The value type.
     * @return {@code this}.
     * @throws DuplicateAttributeException A best effort will be made to detect adding duplicate keys, and if a
     * duplicate key is detected this exception will be thrown.
     */
    <T> PartitionAttributesBuilder add(PartitionAttributes.Key<T> key, T value);

    /**
     * Build a {@link PartitionAttributes} corresponding to the previous {@link #add(PartitionAttributes.Key, Object)}
     * calls.
     * @return a {@link PartitionAttributes} corresponding to the previous {@link #add(PartitionAttributes.Key, Object)}
     * calls.
     * @throws DuplicateAttributeException If a duplicate key is detected this exception will be thrown.
     */
    PartitionAttributes build();
}

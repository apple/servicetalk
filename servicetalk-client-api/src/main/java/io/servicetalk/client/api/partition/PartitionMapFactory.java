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
import io.servicetalk.concurrent.api.AsyncCloseable;

import java.util.function.Function;

/**
 * A generic factory for {@link PartitionMap} objects.
 *
 * @deprecated We are unaware of anyone using "partition" feature and plan to remove it in future releases.
 * If you depend on it, consider using {@link ClientGroup} as an alternative or reach out to the maintainers describing
 * the use-case.
 */
@Deprecated
public interface PartitionMapFactory {  // FIXME: 0.43 - remove deprecated interface
    /**
     * Create a new {@link PartitionMap} object.
     * @param valueFactory The factory used to create new partitions.
     * @param <T> The value type of a partition.
     * @return a new {@link PartitionMap} object.
     */
    <T extends AsyncCloseable> PartitionMap<T> newPartitionMap(Function<PartitionAttributes, T> valueFactory);
}

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
import io.servicetalk.client.api.ServiceDiscoverer;
import io.servicetalk.concurrent.api.AsyncCloseable;
import io.servicetalk.concurrent.api.ListenableAsyncCloseable;

import java.util.List;
import javax.annotation.Nullable;

/**
 * A map like interface which manages {@link PartitionAttributes} that are fully specified and returns the corresponding
 * partitions those {@link PartitionAttributes} belong to.
 *
 * @param <T> The value object which represents the partition.
 * @deprecated We are unaware of anyone using "partition" feature and plan to remove it in future releases.
 * If you depend on it, consider using {@link ClientGroup} as an alternative or reach out to the maintainers describing
 * the use-case.
 */
@Deprecated
public interface PartitionMap<T extends AsyncCloseable> // FIXME: 0.43 - remove deprecated interface
        extends ListenableAsyncCloseable {
    /**
     * Add a new {@link PartitionAttributes} that is absolutely specified. This may create new partitions.
     * <p>
     * The terminology "absolutely"/"fully" here is meant to clarify that {@code partition} contains all the attributes
     * to uniquely identify a single host. This is typically called when notification is received from
     * {@link ServiceDiscoverer} and that address is translated to the corresponding {@link PartitionAttributes}.
     * <p>
     * This method is not guaranteed to provide any thread safety or visibility with respect to calls to this method or
     * {@link #remove(PartitionAttributes)}. If these methods are called from multiple threads you may need to
     * provide external synchronization.
     * @param partition A fully specified {@link PartitionAttributes}.
     * @return The partitions that {@code partition} belongs to. These may (or may not) be new partitions.
     */
    List<T> add(PartitionAttributes partition);

    /**
     * Remove a {@link PartitionAttributes} that was previously added via {@link #add(PartitionAttributes)}.
     * <p>
     * New partitions typically are not created as a result of this method call.
     * <p>
     * This method is not guaranteed to provide any thread safety or visibility with respect to calls to this method or
     * {@link #add(PartitionAttributes)}. If these methods are called from multiple threads you may need to
     * provide external synchronization.
     * @param partition A fully specified {@link PartitionAttributes}.
     * @return The partitions that {@code partition} belongs to.
     */
    List<T> remove(PartitionAttributes partition);

    /**
     * Get the partition value corresponding to the {@link PartitionAttributes} parameter.
     * <p>
     * This may be called from any thread.
     * @param wildCardAttributes A {@link PartitionAttributes} which identifies the partition. Note that this may not be
     *                           fully specified, and this is where the "wild card" terminology comes from.
     * @return the partition value corresponding to the {@link PartitionAttributes} parameter.
     */
    @Nullable
    T get(@Nullable PartitionAttributes wildCardAttributes);
}

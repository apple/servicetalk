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

import static java.util.Objects.requireNonNull;

/**
 * Thrown when a request is issued against a valid partition that was closed after selection.
 *
 * @deprecated We are unaware of anyone using "partition" feature and plan to remove it in future releases.
 * If you depend on it, consider using {@link ClientGroup} as an alternative or reach out to the maintainers describing
 * the use-case.
 */
@Deprecated
public final class ClosedPartitionException extends IllegalStateException { // FIXME: 0.43 - remove deprecated class

    private static final long serialVersionUID = 9006673188565077317L;

    private final PartitionAttributes partitionSelector;

    /**
     * Create a new instance.
     * @param partitionSelector The {@link PartitionAttributes} that selected the partition.
     * @param msg The descriptive message.
     */
    public ClosedPartitionException(PartitionAttributes partitionSelector, String msg) {
        super(msg);
        this.partitionSelector = requireNonNull(partitionSelector);
    }

    /**
     * Get the {@link PartitionAttributes} that was used to select the partition.
     * @return the {@link PartitionAttributes} that was used to select the partition.
     */
    public PartitionAttributes getPartitionSelector() {
        return partitionSelector;
    }
}

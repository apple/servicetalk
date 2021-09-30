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
package io.servicetalk.http.api;

import io.servicetalk.client.api.partition.PartitionAttributes;

/**
 * If different clients used by a partitioned client created by a {@link PartitionedHttpClientBuilder} have different
 * builder configuration, this configurator helps to configure them differently.
 *
 * @deprecated Use {@link PartitionedHttpClientBuilder.SingleAddressInitializer}.
 * @param <U> the type of address before resolution (unresolved address)
 * @param <R> the type of address after resolution (resolved address)
 */
@Deprecated
@FunctionalInterface
public interface PartitionHttpClientBuilderConfigurator<U, R> {

    /**
     * Configures the passed {@link SingleAddressHttpClientBuilder} for a given set of {@link PartitionAttributes}.
     *
     * @param attr the {@link PartitionAttributes} for the partition
     * @param builder {@link SingleAddressHttpClientBuilder} to configure for the given {@link PartitionAttributes}
     */
    void configureForPartition(PartitionAttributes attr, SingleAddressHttpClientBuilder<U, R> builder);

    /**
     * Appends the passed {@link PartitionHttpClientBuilderConfigurator} to this
     * {@link PartitionHttpClientBuilderConfigurator} such that this {@link PartitionHttpClientBuilderConfigurator} is
     * applied first and then the passed {@link PartitionHttpClientBuilderConfigurator}.
     *
     * @param toAppend {@link PartitionHttpClientBuilderConfigurator} to append
     * @return A composite {@link PartitionHttpClientBuilderConfigurator} after the append operation.
     */
    default PartitionHttpClientBuilderConfigurator<U, R> append(PartitionHttpClientBuilderConfigurator<U, R> toAppend) {
        return (attr, builder) -> {
            configureForPartition(attr, builder);
            toAppend.configureForPartition(attr, builder);
        };
    }
}

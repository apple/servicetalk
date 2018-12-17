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
 * Factory which filters builders for partitioned {@link HttpClient}s.
 * <p>
 * @param <U> the type of address before resolution (unresolved address)
 * @param <R> the type of address after resolution (resolved address)
 */
@FunctionalInterface
public interface PartitionHttpClientBuilderFilterFunction<U, R> {

    /**
     * Function that allows to filter or modify the {@link SingleAddressHttpClientBuilder} for a given set of
     * {@link PartitionAttributes}.
     * @param attr the {@link PartitionAttributes} for the {@link HttpClient}
     * @param builder the prepared {@link SingleAddressHttpClientBuilder} for the given {@link PartitionAttributes}
     * @return a filtered or modified {@link SingleAddressHttpClientBuilder}
     */
    SingleAddressHttpClientBuilder<U, R> apply(PartitionAttributes attr,
                                               SingleAddressHttpClientBuilder<U, R> builder);

    /**
     * Returns a composed function that first applies itself to its input, and the applies the {@code after} function to
     * the result.
     * @param after the function to apply after this function is applied
     * @return a composed function that first applies itself to its input, and the applies the {@code after} function to
     * the result.
     */
    default PartitionHttpClientBuilderFilterFunction<U, R> append(
            PartitionHttpClientBuilderFilterFunction<U, R> after) {
        return (attr, builder) -> after.apply(attr, apply(attr, builder));
    }
}

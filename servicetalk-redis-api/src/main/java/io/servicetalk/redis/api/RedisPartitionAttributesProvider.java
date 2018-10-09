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

import io.servicetalk.client.api.ServiceDiscovererEvent;
import io.servicetalk.client.api.partition.PartitionAttributes;
import io.servicetalk.client.api.partition.PartitionedServiceDiscovererEvent;
import io.servicetalk.redis.api.RedisProtocolSupport.Command;

import java.util.function.Function;

/**
 * A provider for {@link PartitionAttributes} for redis.
 *
 * @param <R> the type of address after resolution (resolved address)
 */
public interface RedisPartitionAttributesProvider<R> {

    /**
     * Converts the passed {@link ServiceDiscovererEvent} to a {@link PartitionedServiceDiscovererEvent} that contains
     * the {@link PartitionAttributes} as returned by {@link PartitionedServiceDiscovererEvent#partitionAddress()}.
     *
     * @param resolvedAddressEvent {@link ServiceDiscovererEvent} for which the {@link PartitionAttributes} are to be
     * created.
     * @return {@link PartitionedServiceDiscovererEvent} containing the relevant {@link PartitionAttributes}.
     */
    PartitionedServiceDiscovererEvent<R> forAddress(ServiceDiscovererEvent<R> resolvedAddressEvent);

    /**
     * Provides a {@link RedisPartitionAttributesBuilder} to build {@link PartitionAttributes} for the passed
     * {@link Command}.
     *
     * @param command {@link Command} for which the {@link PartitionAttributes} are to be built using the returned
     * {@link RedisPartitionAttributesBuilder}.
     * @return {@link RedisPartitionAttributesBuilder} to build {@link PartitionAttributes} for the passed
     * {@link Command}.
     */
    RedisPartitionAttributesBuilder forCommand(Command command);

    /**
     * Creates a new {@link RedisPartitionAttributesProvider}.
     *
     * @param forAddress {@link Function} to use.
     * @param forCommand {@link Function} to use.
     * @param <R> the type of address after resolution (resolved address)
     * @return A new {@link RedisPartitionAttributesProvider}.
     */
    static <R> RedisPartitionAttributesProvider<R> from(
            Function<ServiceDiscovererEvent<R>, PartitionedServiceDiscovererEvent<R>> forAddress,
            Function<Command, RedisPartitionAttributesBuilder> forCommand) {
        return new RedisPartitionAttributesProvider<R>() {
            @Override
            public PartitionedServiceDiscovererEvent<R> forAddress(
                    final ServiceDiscovererEvent<R> resolvedAddressEvent) {
                return forAddress.apply(resolvedAddressEvent);
            }

            @Override
            public RedisPartitionAttributesBuilder forCommand(final Command command) {
                return forCommand.apply(command);
            }
        };
    }
}

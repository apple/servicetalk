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
package io.servicetalk.redis.netty;

import io.servicetalk.client.api.LoadBalancer;
import io.servicetalk.client.api.ServiceDiscoverer;
import io.servicetalk.client.api.ServiceDiscovererEvent;
import io.servicetalk.client.api.partition.PartitionAttributesBuilder;
import io.servicetalk.client.api.partition.PartitionedServiceDiscovererEvent;
import io.servicetalk.redis.api.PartitionedRedisClientBuilder;
import io.servicetalk.redis.api.RedisClient;
import io.servicetalk.redis.api.RedisClientBuilder;
import io.servicetalk.redis.api.RedisPartitionAttributesBuilder;
import io.servicetalk.redis.api.RedisProtocolSupport.Command;
import io.servicetalk.transport.api.HostAndPort;

import java.net.InetSocketAddress;
import java.util.function.Function;

/**
 * Factory methods for building {@link RedisClient} (and other API variations) instances.
 */
public final class RedisClients {

    private RedisClients() {
        // No instances.
    }

    /**
     * Creates a {@link RedisClientBuilder} for an address with default {@link LoadBalancer} and DNS
     * {@link ServiceDiscoverer}.
     *
     * @param host host to connect to, resolved by default using a DNS {@link ServiceDiscoverer}.
     * @param port port to connect to
     * @return new builder for the address
     */
    public static RedisClientBuilder<HostAndPort, InetSocketAddress> forAddress(final String host, final int port) {
        return forAddress(HostAndPort.of(host, port));
    }

    /**
     * Creates a {@link RedisClientBuilder} for an address with default {@link LoadBalancer} and DNS
     * {@link ServiceDiscoverer}.
     *
     * @param address the {@code UnresolvedAddress} to connect to, resolved by default using a DNS
     * {@link ServiceDiscoverer}.
     * @return new builder for the address
     */
    public static RedisClientBuilder<HostAndPort, InetSocketAddress> forAddress(final HostAndPort address) {
        return DefaultRedisClientBuilder.forHostAndPort(address);
    }

    /**
     * Creates a {@link RedisClientBuilder} for a custom address type with default {@link LoadBalancer} and
     * user provided {@link ServiceDiscoverer}.
     *
     * @param serviceDiscoverer The {@link ServiceDiscoverer} to resolve addresses of remote servers to connect to.
     * The lifecycle of the provided {@link ServiceDiscoverer} should be managed by the caller.
     * @param address the {@code UnresolvedAddress} to connect to resolved using the provided {@code serviceDiscoverer}.
     * @param <U> the type of address before resolution (unresolved address)
     * @param <R> the type of address after resolution (resolved address)
     * @return new builder with provided configuration
     */
    public static <U, R> RedisClientBuilder<U, R> forAddress(
            final ServiceDiscoverer<U, R, ServiceDiscovererEvent<R>> serviceDiscoverer, final U address) {
        return new DefaultRedisClientBuilder<>(serviceDiscoverer, address);
    }

    /**
     * Creates a {@link PartitionedRedisClientBuilder} for a custom address type with default {@link LoadBalancer} and
     * user provided {@link ServiceDiscoverer}.
     *
     * @param serviceDiscoverer The {@link ServiceDiscoverer} to resolve addresses of remote servers to connect to.
     * The lifecycle of the provided {@link ServiceDiscoverer} should be managed by the caller.
     * @param address the {@code UnresolvedAddress} to connect to resolved using the provided {@code serviceDiscoverer}.
     * @param partitionAttributesBuilderFactory A {@link Function} to provide a {@link PartitionAttributesBuilder} for
     * a {@link Command}.
     * @param <U> the type of address before resolution (unresolved address)
     * @param <R> the type of address after resolution (resolved address)
     * @return new builder with provided configuration
     */
    public static <U, R> PartitionedRedisClientBuilder<U, R> forPartitionedAddress(
            final ServiceDiscoverer<U, R, ? extends PartitionedServiceDiscovererEvent<R>> serviceDiscoverer,
            final U address, Function<Command, RedisPartitionAttributesBuilder> partitionAttributesBuilderFactory) {
        return DefaultPartitionedRedisClientBuilder.forAddress(address, serviceDiscoverer,
                partitionAttributesBuilderFactory);
    }
}

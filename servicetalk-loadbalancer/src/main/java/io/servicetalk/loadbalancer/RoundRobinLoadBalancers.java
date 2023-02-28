/*
 * Copyright © 2023 Apple Inc. and the ServiceTalk project authors
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
package io.servicetalk.loadbalancer;

import io.servicetalk.client.api.LoadBalancedConnection;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

import static io.servicetalk.utils.internal.ServiceLoaderUtils.loadProviders;

/**
 * A factory to create {@link RoundRobinLoadBalancer RoundRobinLoadBalancers}.
 */
public final class RoundRobinLoadBalancers {
    private static final Logger LOGGER = LoggerFactory.getLogger(RoundRobinLoadBalancers.class);

    private static final List<RoundRobinLoadBalancerBuilderProvider> PROVIDERS;

    static {
        final ClassLoader classLoader = RoundRobinLoadBalancers.class.getClassLoader();
        PROVIDERS = loadProviders(RoundRobinLoadBalancerBuilderProvider.class, classLoader, LOGGER);
    }

    private RoundRobinLoadBalancers() {
        // No instances.
    }

    private static <ResolvedAddress, C extends LoadBalancedConnection>
    RoundRobinLoadBalancerBuilder<ResolvedAddress, C> applyProviders(
            String id, RoundRobinLoadBalancerBuilder<ResolvedAddress, C> builder) {
        for (RoundRobinLoadBalancerBuilderProvider provider : PROVIDERS) {
            builder = provider.newBuilder(id, builder);
        }
        return builder;
    }

    /**
     * A new {@link RoundRobinLoadBalancerBuilder} instance.
     * <p>
     * The returned builder can be customized using {@link RoundRobinLoadBalancerBuilderProvider}.
     *
     * @param id a (unique) ID to identify the created {@link RoundRobinLoadBalancer}.
     * @param <ResolvedAddress> The resolved address type.
     * @param <C> The type of connection.
     * @return a new {@link RoundRobinLoadBalancerBuilder}.
     */
    @SuppressWarnings("deprecation")
    public static <ResolvedAddress, C extends LoadBalancedConnection> RoundRobinLoadBalancerBuilder<ResolvedAddress, C>
    builder(final String id) {
        return applyProviders(id, new RoundRobinLoadBalancerFactory.Builder<>(id));
    }
}

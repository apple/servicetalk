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
 * A factory to create {@link DefaultLoadBalancer DefaultLoadBalancers}.
 */
public final class LoadBalancers {

    private static final Logger LOGGER = LoggerFactory.getLogger(LoadBalancers.class);
    private static final List<LoadBalancerBuilderProvider> PROVIDERS;

    static {
        final ClassLoader classLoader = LoadBalancers.class.getClassLoader();
        PROVIDERS = loadProviders(LoadBalancerBuilderProvider.class, classLoader, LOGGER);
    }

    private LoadBalancers() {
        // no instances.
    }

    private static <ResolvedAddress, C extends LoadBalancedConnection>
    LoadBalancerBuilder<ResolvedAddress, C> applyProviders(String id, LoadBalancerBuilder<ResolvedAddress, C> builder) {
        for (LoadBalancerBuilderProvider provider : PROVIDERS) {
            builder = provider.newBuilder(id, builder);
        }
        return builder;
    }

    /**
     * A new {@link LoadBalancerBuilder} instance.
     * <p>
     * The returned builder can be customized using {@link LoadBalancerBuilderProvider}.
     *
     * @param id a (unique) ID to identify the created {@link LoadBalancerBuilder}.
     * @param <ResolvedAddress> The resolved address type.
     * @param <C> The type of connection.
     * @return a new {@link LoadBalancerBuilder}.
     */
    public static <ResolvedAddress, C extends LoadBalancedConnection>
    LoadBalancerBuilder<ResolvedAddress, C> builder(final String id) {
        return applyProviders(id, new DefaultLoadBalancerBuilder<>(id));
    }
}

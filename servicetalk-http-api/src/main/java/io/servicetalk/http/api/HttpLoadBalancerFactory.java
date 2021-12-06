/*
 * Copyright © 2020 Apple Inc. and the ServiceTalk project authors
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

import io.servicetalk.client.api.LoadBalancerFactory;

/**
 * A {@link LoadBalancerFactory} for HTTP clients.
 *
 * @param <ResolvedAddress> The type of address after resolution.
 */
public interface HttpLoadBalancerFactory<ResolvedAddress>
        extends LoadBalancerFactory<ResolvedAddress, FilterableStreamingHttpLoadBalancedConnection> {

    /**
     * Converts the passed {@link FilterableStreamingHttpConnection} to a
     * {@link FilterableStreamingHttpLoadBalancedConnection}.
     *
     * @param connection {@link FilterableStreamingHttpConnection} to convert.
     * @return {@link FilterableStreamingHttpLoadBalancedConnection} for the passed
     * {@link FilterableStreamingHttpConnection}.
     */
    FilterableStreamingHttpLoadBalancedConnection toLoadBalancedConnection(
            FilterableStreamingHttpConnection connection);

    @Override
    default HttpExecutionStrategy requiredOffloads() {
        // "safe" default -- implementations are expected to override
        return HttpExecutionStrategies.offloadAll();
    }
}

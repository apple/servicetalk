/*
 * Copyright © 2019 Apple Inc. and the ServiceTalk project authors
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
package io.servicetalk.dns.discovery.netty;

import io.servicetalk.client.api.ServiceDiscoverer;
import io.servicetalk.client.api.ServiceDiscovererEvent;

import static java.util.Objects.requireNonNull;

/**
 * A factory for {@link ServiceDiscovererFilter}.
 *
 * @param <UnresolvedAddress> The type of address before resolution.
 * @param <ResolvedAddress> The type of address after resolution.
 * @param <E> Type of {@link ServiceDiscovererEvent}s published from {@link ServiceDiscoverer#discover(Object)}.
 */
@FunctionalInterface
public interface ServiceDiscovererFilterFactory<UnresolvedAddress, ResolvedAddress,
        E extends ServiceDiscovererEvent<ResolvedAddress>> {

    /**
     * Create a {@link ServiceDiscovererFilter} using the provided {@link ServiceDiscoverer}.
     *
     * @param client {@link ServiceDiscoverer} to filter
     * @return {@link ServiceDiscovererFilter} using the provided {@link ServiceDiscoverer}.
     */
    ServiceDiscovererFilter<UnresolvedAddress, ResolvedAddress, E> create(ServiceDiscoverer<UnresolvedAddress, ResolvedAddress, E> client);

    /**
     * Returns a composed function that first applies the {@code before} function to its input, and then applies
     * this function to the result.
     * <p>
     * The order of execution of these filters are in order of append. If 3 filters are added as follows:
     * <pre>
     *     filter1.append(filter2).append(filter3)
     * </pre>
     * making a request to a client wrapped by this filter chain the order of invocation of these filters will be:
     * <pre>
     *     filter1 =&gt; filter2 =&gt; filter3 =&gt; client
     * </pre>
     *
     * @param before the function to apply before this function is applied
     * @return a composed function that first applies the {@code before}
     * function and then applies this function
     */
    default ServiceDiscovererFilterFactory<UnresolvedAddress, ResolvedAddress, E> append(ServiceDiscovererFilterFactory<UnresolvedAddress, ResolvedAddress, E> before) {
        requireNonNull(before);
        return client -> create(before.create(client));
    }

    /**
     * Returns a function that always returns its input {@link ServiceDiscoverer}.
     *
     * @param <UnresolvedAddress> The type of address before resolution.
     * @param <ResolvedAddress> The type of address after resolution.
     * @param <E> Type of {@link ServiceDiscovererEvent}s published from {@link ServiceDiscoverer#discover(Object)}.
     * @return a function that always returns its input {@link ServiceDiscoverer}.
     */
    static <UnresolvedAddress, ResolvedAddress, E extends ServiceDiscovererEvent<ResolvedAddress>> ServiceDiscovererFilterFactory<UnresolvedAddress, ResolvedAddress, E> identity() {
        return ServiceDiscovererFilter::new;
    }
}

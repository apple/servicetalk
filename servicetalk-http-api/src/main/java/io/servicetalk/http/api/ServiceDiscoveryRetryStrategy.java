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

import io.servicetalk.client.api.ServiceDiscoverer;
import io.servicetalk.client.api.ServiceDiscovererEvent;
import io.servicetalk.concurrent.api.Publisher;

import java.util.List;

/**
 * A retry strategy for errors emitted from {@link ServiceDiscoverer#discover(Object)}.
 *
 * @param <ResolvedAddress> The type of address after resolution.
 * @param <E> Type of {@link ServiceDiscovererEvent}s published from {@link ServiceDiscoverer#discover(Object)}.
 */
@FunctionalInterface
public interface ServiceDiscoveryRetryStrategy<ResolvedAddress, E extends ServiceDiscovererEvent<ResolvedAddress>> {

    /**
     * Applies this strategy on the passed {@link Publisher}.
     *
     * @param sdEvents {@link Publisher} of {@link ServiceDiscovererEvent} on which this strategy is to be applied.
     * @return {@link Publisher} after applying this retry strategy on the passed {@code sdEvents} {@link Publisher}.
     */
    Publisher<List<E>> apply(Publisher<List<E>> sdEvents);
}

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
import io.servicetalk.concurrent.api.BiIntFunction;
import io.servicetalk.concurrent.api.Publisher;

import java.util.Collection;

/**
 * A retry strategy for errors emitted from {@link ServiceDiscoverer#discover(Object)}.
 *
 * @param <ResolvedAddress> The type of address after resolution.
 * @param <E> Type of {@link ServiceDiscovererEvent}s published from {@link ServiceDiscoverer#discover(Object)}.
 * @see io.servicetalk.concurrent.api.RetryStrategies
 * @see SingleAddressHttpClientBuilder#retryServiceDiscoveryErrors(BiIntFunction)
 * @deprecated Use standard mechanisms for retrying using
 * {@link io.servicetalk.concurrent.api.Completable#retryWhen(BiIntFunction)} on the {@link Publisher} returned from
 * {@link ServiceDiscoverer#discover(Object)} instead.
 */
@Deprecated
@FunctionalInterface
public interface ServiceDiscoveryRetryStrategy<ResolvedAddress, E extends ServiceDiscovererEvent<ResolvedAddress>> {

    /**
     * Applies this strategy on the passed {@link Publisher}.
     *
     * @param sdEvents {@link Publisher} of {@link ServiceDiscovererEvent} on which this strategy is to be applied.
     * @return {@link Publisher} after applying this retry strategy on the passed {@code sdEvents} {@link Publisher}.
     */
    Publisher<Collection<E>> apply(Publisher<Collection<E>> sdEvents);
}

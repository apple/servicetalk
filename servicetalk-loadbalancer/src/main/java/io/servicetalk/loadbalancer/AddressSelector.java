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
package io.servicetalk.loadbalancer;

import io.servicetalk.client.api.LoadBalancedAddress;
import io.servicetalk.client.api.LoadBalancedConnection;
import io.servicetalk.client.api.LoadBalancer;
import io.servicetalk.concurrent.api.ListenableAsyncCloseable;
import io.servicetalk.concurrent.api.Single;

/**
 * Contract for a {@link LoadBalancer} entity capable of maintaining and selecting the preferred entity from a set of
 * {@link LoadBalancedAddress}es.
 * @param <C> the type of {@link LoadBalancedConnection}.
 */
public interface AddressSelector<C extends LoadBalancedConnection> extends ListenableAsyncCloseable {

    /**
     * Returns an asynchronously produced {@link LoadBalancedAddress}.
     *
     * @return an asynchronously produced {@link LoadBalancedAddress}.
     */
    Single<LoadBalancedAddress<C>> select();

    /**
     * Announces availability of a {@link LoadBalancedAddress}, eg. from Service Discovery.
     *
     * @param address the {@link LoadBalancedAddress}.
     */
    void add(LoadBalancedAddress<C> address);

    /**
     * Announces unavailability of a {@link LoadBalancedAddress}, eg. from Service Discovery.
     *
     * @param address the {@link LoadBalancedAddress}.
     */
    void remove(LoadBalancedAddress<C> address);

    /**
     * TODO(jayv): not convinced this is a good API
     *
     * Returns {@code TRUE} if this entity is likely to be able to select a {@link LoadBalancedAddress} immediately.
     *
     * @return {@code TRUE} if this entity is likely to be able to select a {@link LoadBalancedAddress} immediately.
     */
    boolean isReady();
}

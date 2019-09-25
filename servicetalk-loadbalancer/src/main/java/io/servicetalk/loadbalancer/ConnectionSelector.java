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

import io.servicetalk.client.api.LoadBalancedConnection;
import io.servicetalk.client.api.LoadBalancer;
import io.servicetalk.concurrent.api.ListenableAsyncCloseable;
import io.servicetalk.concurrent.api.Single;

import java.util.function.Predicate;

/**
 * Contract for a {@link LoadBalancer} entity capable of maintaining and selecting the preferred entity from a set of
 * {@link LoadBalancedConnection}s.
 * @param <C> type of LoadBalancedConnection.
 */
interface ConnectionSelector<C extends LoadBalancedConnection> extends ListenableAsyncCloseable {

    /**
     * Returns an asynchronously produced {@link LoadBalancedConnection} matching a given {@link Predicate}.
     *
     * @param predicate the {@link Predicate} used to select a {@link LoadBalancedConnection}.
     * @return an asynchronously produced {@link LoadBalancedConnection} matching a given {@link Predicate}.
     */
    Single<C> select(Predicate<C> predicate);

    /**
     * Announces availability of a new {@link LoadBalancedConnection}.
     *
     * @param connection the {@link LoadBalancedConnection}.
     */
    void add(C connection);

    /**
     * Announces unavailability of a {@link LoadBalancedConnection}, eg. from Service Discovery.
     *
     * @param connection the {@link LoadBalancedConnection}.
     */
    void remove(C connection);
}

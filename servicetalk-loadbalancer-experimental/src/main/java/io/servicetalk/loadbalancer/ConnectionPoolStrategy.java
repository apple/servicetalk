/*
 * Copyright © 2024 Apple Inc. and the ServiceTalk project authors
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

import java.util.List;
import java.util.function.Predicate;
import javax.annotation.Nullable;

/**
 * A strategy for selecting connections at the {@link Host} level connection pool.
 * @param <C> the concrete type of the connection.
 */
interface ConnectionPoolStrategy<C extends LoadBalancedConnection> {

    /**
     * Select a connection from the ordered list of connections.
     * @param connections the list of connections to pick from.
     * @param selector a predicate used to test a connection.
     * @return the selected connection, or {@code null} if no existing connection was selected.
     */
    @Nullable
    C select(List<C> connections, Predicate<C> selector);
}

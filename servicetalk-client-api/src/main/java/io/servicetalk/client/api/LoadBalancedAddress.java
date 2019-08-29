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
package io.servicetalk.client.api;

import io.servicetalk.concurrent.api.ListenableAsyncCloseable;
import io.servicetalk.concurrent.api.Single;

/**
 * Minimal contract required by a load balancer managing addresses.
 * <p>
 * Note: {@link ScoreSupplier} contract provides this resource's availability, where 0.0 means lowest availability, 1.0
 * highest availability.
 * @param <C> type of {@link LoadBalancedConnection}.
 */
public interface LoadBalancedAddress<C extends LoadBalancedConnection> extends ListenableAsyncCloseable, ScoreSupplier {

    /**
     * Creates and asynchronously returns a connection for this address.
     *
     * @return {@link Single} that emits the created {@link LoadBalancedConnection}.
     */
    Single<C> newConnection();
}

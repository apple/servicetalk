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

/**
 * Configuration of the policy for selecting connections from a pool to the same endpoint.
 * @param <C> the concrete type of the {@link LoadBalancedConnection}
 */
public abstract class ConnectionSelectorPolicy<C extends LoadBalancedConnection> {

    ConnectionSelectorPolicy() {
        // package private constructor to control proliferation
    }

    /**
     * Provide an instance of the {@link ConnectionSelector} to use with a {@link Host}.
     * @param lbDescription description of the resource, used for logging purposes.
     * @return an instance of the {@link ConnectionSelector} to use with a {@link Host}.
     */
    abstract ConnectionSelector<C> buildConnectionSelector(String lbDescription);
}

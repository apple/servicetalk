/*
 * Copyright © 2022 Apple Inc. and the ServiceTalk project authors
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

/**
 * Used to define a {@link LoadBalancedConnection} that is owened by an {@link LoadBalancedAddress}.
 * It creates this association by calling the {@link #parent(LoadBalancedAddress)} during connection creation.
 */
public interface AddressOwenedConnection {

    /**
     * Associates the {@link LoadBalancedAddress} as the parent of this {@link LoadBalancedConnection}.
     * @param parent the {@link LoadBalancedAddress} the parent of this {@link LoadBalancedConnection}.
     */
    void parent(LoadBalancedAddress<?> parent);
}

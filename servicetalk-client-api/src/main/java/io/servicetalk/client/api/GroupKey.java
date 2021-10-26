/*
 * Copyright © 2018 Apple Inc. and the ServiceTalk project authors
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

import io.servicetalk.transport.api.ExecutionContext;

/**
 * Identifies a client within a group of clients, and provides enough information to create a client if non exist.
 *
 * @param <Address> The type of address used by clients (typically this is unresolved address).
 */
public interface GroupKey<Address> {
    /**
     * Get the address to use when looking for or creating a new client. This address is typically unresolved, but
     * may not be a requirement depending upon configuration.
     *
     * @return the address to use when looking for or creating a new client. This address is typically unresolved, but
     * may not be a requirement depending upon configuration.
     */
    Address address();

    /**
     * Get the {@link ExecutionContext} to use when looking for or creating a new client.
     * @return the {@link ExecutionContext} to use when looking for or creating a new client.
     */
    ExecutionContext<?> executionContext();
}

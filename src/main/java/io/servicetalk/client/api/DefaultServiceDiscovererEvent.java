/**
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

import static java.util.Objects.requireNonNull;

/**
 * Default implementation of {@link ServiceDiscoverer.Event}.
 * @param <T> The type of resolved address.
 */
public final class DefaultServiceDiscovererEvent<T> implements ServiceDiscoverer.Event<T> {
    private final T address;
    private final boolean isAvailable;

    /**
     * Create a new instance.
     * @param address The address returned by {@link #getAddress()}.
     * @param isAvailable Value returned by {@link #isAvailable}.
     */
    public DefaultServiceDiscovererEvent(T address, boolean isAvailable) {
        this.address = requireNonNull(address);
        this.isAvailable = isAvailable;
    }

    @Override
    public T getAddress() {
        return address;
    }

    @Override
    public boolean isAvailable() {
        return isAvailable;
    }
}

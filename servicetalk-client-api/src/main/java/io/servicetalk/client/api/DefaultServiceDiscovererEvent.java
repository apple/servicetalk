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

import static java.util.Objects.requireNonNull;

/**
 * Default implementation of {@link ServiceDiscovererEvent}.
 * @param <T> The type of resolved address.
 */
public final class DefaultServiceDiscovererEvent<T> implements ServiceDiscovererEvent<T> {
    private final T address;
    private final boolean available;

    /**
     * Create a new instance.
     * @param address The address returned by {@link #address()}.
     * @param available Value returned by {@link #available}.
     */
    public DefaultServiceDiscovererEvent(T address, boolean available) {
        this.address = requireNonNull(address);
        this.available = available;
    }

    @Override
    public T address() {
        return address;
    }

    @Override
    public boolean available() {
        return available;
    }

    @Override
    public String toString() {
        return "DefaultServiceDiscovererEvent{" +
                "address=" + address +
                ", available=" + available +
                '}';
    }
}

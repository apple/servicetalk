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

import static io.servicetalk.client.api.ServiceDiscovererEvent.Status.AVAILABLE;
import static io.servicetalk.client.api.ServiceDiscovererEvent.Status.UNAVAILABLE;
import static java.util.Objects.requireNonNull;

/**
 * Default implementation of {@link ServiceDiscovererEvent}.
 * @param <T> The type of resolved address.
 */
public final class DefaultServiceDiscovererEvent<T> implements ServiceDiscovererEvent<T> {
    private final T address;
    private final Status status;

    /**
     * Create a new instance.
     * @param address The address returned by {@link #address()}.
     * @param available Value used to determine {@link #status()}.
     * @deprecated Use {@link #DefaultServiceDiscovererEvent(Object, Status)}.
     */
    @Deprecated
    public DefaultServiceDiscovererEvent(T address, boolean available) {
        this.address = requireNonNull(address);
        this.status = available ? AVAILABLE : UNAVAILABLE;
    }

    /**
     * Create a new instance.
     * @param address The address returned by {@link #address()}.
     * @param status Value returned by {@link #status()}.
     */
    public DefaultServiceDiscovererEvent(T address, Status status) {
        this.address = requireNonNull(address);
        this.status = requireNonNull(status);
    }

    @Override
    public T address() {
        return address;
    }

    @Override
    public Status status() {
        return status;
    }

    @Override
    public boolean isAvailable() {
        return AVAILABLE.equals(status);
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final DefaultServiceDiscovererEvent<?> that = (DefaultServiceDiscovererEvent<?>) o;
        return status.equals(that.status) && address.equals(that.address);
    }

    @Override
    public int hashCode() {
        int result = address.hashCode();
        result = 31 * result + status.hashCode();
        return result;
    }

    @Override
    public String toString() {
        return "DefaultServiceDiscovererEvent{" +
                "address=" + address +
                ", status=" + status +
                ", available=" + isAvailable() +
                '}';
    }
}

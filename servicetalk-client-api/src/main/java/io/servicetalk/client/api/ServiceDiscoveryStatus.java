/*
 * Copyright © 2021 Apple Inc. and the ServiceTalk project authors
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

import java.util.Objects;

/**
 * Status provided by the Service Discovery system that guides the actions of {@link ServiceDiscoverer} upon the
 * bound {@link ServiceDiscovererEvent#address()} (via {@link ServiceDiscovererEvent}).
 */
public final class ServiceDiscoveryStatus {

    /**
     * Signifies the {@link ServiceDiscovererEvent#address()} is available for use in connection establishment.
     */
    public static final ServiceDiscoveryStatus AVAILABLE = new ServiceDiscoveryStatus("available");

    /**
     * Signifies the {@link ServiceDiscovererEvent#address()} is not available for use in connection establishment.
     */
    public static final ServiceDiscoveryStatus UNAVAILABLE = new ServiceDiscoveryStatus("unavailable");

    /**
     * Signifies the {@link ServiceDiscovererEvent#address()} is expired and should not be used
     * for connection establishment. It doesn't necessarily mean that the host should not be used in traffic routing
     * over already established connections. The implementations can have different policies in that regard.
     */
    public static final ServiceDiscoveryStatus EXPIRED = new ServiceDiscoveryStatus("expired");

    private final String name;

    private ServiceDiscoveryStatus(final String name) {
        if (name.isEmpty()) {
            throw new IllegalArgumentException("ServiceDiscoveryStatus name cannot be empty");
        }
        this.name = name;
    }

    /**
     * Returns an {@link ServiceDiscoveryStatus} for the specified name.
     * @param name the status name.
     * @return {@link ServiceDiscoveryStatus} representing the status for given name.
     */
    public static ServiceDiscoveryStatus of(final String name) {
        switch (name) {
            case "available":
                return AVAILABLE;
            case "unavailable":
                return UNAVAILABLE;
            case "expired":
                return EXPIRED;
            default:
                return new ServiceDiscoveryStatus(name);
        }
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof ServiceDiscoveryStatus)) {
            return false;
        }
        final ServiceDiscoveryStatus that = (ServiceDiscoveryStatus) o;
        return name.equals(that.name);
    }

    @Override
    public int hashCode() {
        return Objects.hash(name);
    }
}

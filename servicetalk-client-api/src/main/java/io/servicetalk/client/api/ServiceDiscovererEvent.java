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

import java.util.Locale;

import static io.servicetalk.client.api.ServiceDiscovererEvent.Status.AVAILABLE;
import static io.servicetalk.client.api.ServiceDiscovererEvent.Status.UNAVAILABLE;

/**
 * Notification from the Service Discovery system that availability for an address has changed.
 * @param <ResolvedAddress> the type of address after resolution.
 */
public interface ServiceDiscovererEvent<ResolvedAddress> {
    /**
     * Get the resolved address which is the subject of this event.
     * @return a resolved address that can be used for connecting.
     */
    ResolvedAddress address();

    /**
     * {@link Status Status} of the event instructing the {@link ServiceDiscoverer} what actions
     * to take upon the associated {@link #address() address}.
     * <p>
     * Note, the default implementation calls {@link #isAvailable()} to allow frictionless adoption, but once the
     * implementing class removes the override for the deprecated method {@link #isAvailable()},
     * it will be also necessary to override {@link #status()}.
     * @return {@link Status Status} of the associated {@link #address()}.
     */
    default Status status() {
        return isAvailable() ? AVAILABLE : UNAVAILABLE;
    }

    /**
     * Determine if {@link #address()} is now available or unavailable.
     * @return {@code true} if {@link #address()} is now available or false if the {@link #address()} is now
     * unavailable.
     * @deprecated Implement and use {@link #status()}. This method will be removed.
     */
    @Deprecated
    default boolean isAvailable() {
        throw new UnsupportedOperationException("Migrate to status() method. This method may be implemented" +
                " temporarily until migration to status() is complete.");
    }

    /**
     * Status provided by the {@link ServiceDiscoverer} system that guides the actions of {@link LoadBalancer} upon the
     * bound {@link ServiceDiscovererEvent#address()} (via {@link ServiceDiscovererEvent}).
     */
    final class Status {

        /**
         * Signifies the {@link ServiceDiscovererEvent#address()} is available for use in connection establishment.
         */
        public static final Status AVAILABLE = new Status("available");

        /**
         * Signifies the {@link ServiceDiscovererEvent#address()} is not available for use and all currently established
         * connections should be closed.
         */
        public static final Status UNAVAILABLE = new Status("unavailable");

        /**
         * Signifies the {@link ServiceDiscovererEvent#address()} is expired and should not be used for establishing
         * new connections. It doesn't necessarily mean that the host should not be used in traffic routing over already
         * established connections as long as they are kept open by the remote peer. The implementations can have
         * different policies in that regard.
         */
        public static final Status EXPIRED = new Status("expired");

        private final String name;

        private Status(final String name) {
            if (name.isEmpty()) {
                throw new IllegalArgumentException("Status name cannot be empty");
            }
            this.name = name.toLowerCase(Locale.ENGLISH);
        }

        /**
         * Returns an {@link Status} for the specified name.
         * @param name the status name.
         * @return {@link Status} representing the status for given name.
         */
        public static Status of(final String name) {
            switch (name.toLowerCase(Locale.ENGLISH)) {
                case "available":
                    return AVAILABLE;
                case "unavailable":
                    return UNAVAILABLE;
                case "expired":
                    return EXPIRED;
                default:
                    return new Status(name);
            }
        }

        /**
         * Returns the name of this status.
         * @return name of this status.
         */
        public String getName() {
            return name;
        }

        @Override
        public boolean equals(final Object o) {
            if (this == o) {
                return true;
            }
            if (!(o instanceof ServiceDiscovererEvent.Status)) {
                return false;
            }
            final Status that = (Status) o;
            return name.equals(that.name);
        }

        @Override
        public int hashCode() {
            return name.hashCode();
        }

        @Override
        public String toString() {
            return "Status{" +
                    "name='" + name + '\'' +
                    '}';
        }
    }
}

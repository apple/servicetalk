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

import static io.servicetalk.client.api.ServiceDiscoveryStatus.AVAILABLE;

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
     * {@link ServiceDiscoveryStatus Status} of the event instructing the {@link ServiceDiscoverer} what actions
     * to take upon the associated {@link #address() address}.
     * @return {@link ServiceDiscoveryStatus Status} of the associated {@link #address()}.
     */
    default ServiceDiscoveryStatus status() {
        throw new UnsupportedOperationException("Method status is not supported by " + getClass().getName());
    }

    /**
     * Determine if {@link #address()} is now available or unavailable.
     * @return {@code true} if {@link #address()} is now available or false if the {@link #address()} is now
     * unavailable.
     * @deprecated Use {@link #status()}. This method will be removed, but in the transition period its default
     * implementation calls {{@link #status()}} to determine availability – implementors of this interface need to
     * override {@link #status()}.
     */
    @Deprecated
    default boolean isAvailable() {
        return status() == AVAILABLE;
    }
}

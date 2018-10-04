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

import io.servicetalk.concurrent.api.ListenableAsyncCloseable;
import io.servicetalk.concurrent.api.Publisher;

import org.reactivestreams.Subscription;

/**
 * Represents the interaction pattern with a service discovery system. It is assumed that once {@link #discover(Object)}
 * is called that the service discovery system will push data updates or implementations of this interface will poll for
 * data updates. Changes in the available hosts will be communicated via the resulting {@link Publisher}.
 * @param <UnresolvedAddress> The type of address before resolution.
 * @param <ResolvedAddress> The type of address after resolution.
 */
public interface ServiceDiscoverer<UnresolvedAddress, ResolvedAddress> extends ListenableAsyncCloseable {
    /**
     * Subscribe to the service discovery system for changes in the available {@link ResolvedAddress} associated with
     * {@code address}.
     * <p>
     * In general a call to this method will continue to discover changes related to {@code address} until the
     * {@link Subscription}
     * corresponding to the return value is cancelled via {@link Subscription#cancel()} or there are no more changes to
     * publish.
     * @param address the service address to discover. Examples of what this address maybe are:
     * <ul>
     * <li>hostname/port (e.g. InetAddress)</li>
     * <li>service name</li>
     * <li>it may be a list of attributes which describe the service attributes to resolve</li>
     * <li>something else</li>
     * </ul>
     * @return a {@link Publisher} that represents a stream of events from the service discovery system.
     */
    Publisher<Event<ResolvedAddress>> discover(UnresolvedAddress address);

    /**
     * Notification from the Service Discovery system that availability for an address has changed.
     * @param <ResolvedAddress> the type of address after resolution.
     */
    interface Event<ResolvedAddress> {
        /**
         * Get the resolved address which is the subject of this event.
         * @return a resolved address that can be used for connecting.
         */
        ResolvedAddress address();

        /**
         * Determine if {@link #address()} is now available or unavailable.
         * @return {@code true} if {@link #address()} is now available or false if the {@link #address()} is now
         * unavailable.
         */
        boolean available();
    }
}

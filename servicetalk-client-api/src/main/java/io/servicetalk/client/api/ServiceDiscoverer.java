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

import io.servicetalk.concurrent.PublisherSource.Subscriber;
import io.servicetalk.concurrent.PublisherSource.Subscription;
import io.servicetalk.concurrent.api.ListenableAsyncCloseable;
import io.servicetalk.concurrent.api.Publisher;

import java.util.Collection;

/**
 * Represents the interaction pattern with a service discovery system. It is assumed that once {@link #discover(Object)}
 * is called that the service discovery system will push data updates or implementations of this interface will poll for
 * data updates. Changes in the available addresses will be communicated via the resulting {@link Publisher}.
 * <p>
 * Because typically a {@link ServiceDiscoverer} implementation runs in the background and doesn't require many compute
 * resources, it's recommended (but not required) to run it and deliver updates on a single thread either for all
 * discoveries or at least for all {@link Subscriber Subscribers} to the same {@link Publisher}. One possible advantage
 * of a single-threaded model is that it will make debugging easier as discovery events and the logs they generate will
 * be less susceptible to reordering.
 * <p>
 * See {@link ServiceDiscovererEvent} for documentation regarding the interpretation of events.
 *
 * @param <UnresolvedAddress> The type of address before resolution.
 * @param <ResolvedAddress> The type of address after resolution.
 * @param <E> Type of {@link ServiceDiscovererEvent}s published from {@link #discover(Object)}.
 */
public interface ServiceDiscoverer<UnresolvedAddress, ResolvedAddress,
        E extends ServiceDiscovererEvent<ResolvedAddress>> extends ListenableAsyncCloseable {
    /**
     * Subscribe to the service discovery system for changes in the available {@link ResolvedAddress} associated with
     * {@code address}.
     * <p>
     * In general a call to this method will continue to discover changes related to {@code address} until the
     * {@link Subscription Subscription} corresponding to the return value is {@link Subscription#cancel() cancelled} or
     * {@link Publisher} fails with an error. The returned {@link Publisher} should never
     * {@link Subscriber#onComplete() complete} because underlying system may run for a long period of time and updates
     * may be required at any time in the future. The returned {@link Publisher} MUST support re-subscribes to allow
     * underlying systems retry failures or re-subscribe after {@link Subscription#cancel() cancellation}.
     *
     * @param address the service address to discover. Examples of what this address maybe are:
     * <ul>
     * <li>hostname/port (e.g. InetAddress)</li>
     * <li>service name</li>
     * <li>it may be a list of attributes which describe the service attributes to resolve</li>
     * <li>something else</li>
     * </ul>
     * @return a {@link Publisher} that represents a stream of events from the service discovery system.
     */
    Publisher<Collection<E>> discover(UnresolvedAddress address);
}

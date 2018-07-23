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
package io.servicetalk.http.api;

import io.servicetalk.client.api.ServiceDiscoverer;
import io.servicetalk.client.api.ServiceDiscoverer.Event;
import io.servicetalk.concurrent.api.Publisher;
import io.servicetalk.transport.api.ExecutionContext;

/**
 * A builder of {@link HttpClient} objects.
 *
 * @param <ResolvedAddress> The type of resolved address that can be used to establish new {@link HttpConnection}s.
 * @param <EventType> The type of {@link Event} which communicates address changes.
 */
public interface HttpClientBuilder<ResolvedAddress, EventType extends Event<ResolvedAddress>> {

    /**
     * Build a new {@link HttpClient}.
     *
     * @param executionContext {@link ExecutionContext} used for {@link HttpClient#getExecutionContext()} and to build
     * new {@link HttpConnection}s.
     * @param addressEventStream A stream of events (typically from a {@link ServiceDiscoverer#discover(Object)}) that
     *                           provides the addresses used to create new {@link HttpConnection}s
     * @return A new {@link HttpClient}
     */
    HttpClient build(ExecutionContext executionContext, Publisher<EventType> addressEventStream);

    /**
     * Build a new {@link HttpClient}, using a default {@link ExecutionContext}.
     *
     * @param addressEventStream A stream of events (typically from a {@link ServiceDiscoverer#discover(Object)}) that
     *                           provides the addresses used to create new {@link HttpConnection}s
     * @return A new {@link HttpClient}
     * @see #build(ExecutionContext, Publisher)
     */
    HttpClient build(Publisher<EventType> addressEventStream);

    /**
     * Build a new {@link AggregatedHttpClient}.
     *
     * @param executionContext {@link ExecutionContext} used for {@link AggregatedHttpClient#getExecutionContext()} and
     * to build new {@link HttpConnection}s.
     * @param addressEventStream A stream of events (typically from a {@link ServiceDiscoverer#discover(Object)}) that
     *                           provides the addresses used to create new {@link HttpConnection}s
     * @return A new {@link AggregatedHttpClient}
     */
    default AggregatedHttpClient buildAggregated(ExecutionContext executionContext,
                                                 Publisher<EventType> addressEventStream) {
        return build(executionContext, addressEventStream).asAggregatedClient();
    }

    /**
     * Build a new {@link AggregatedHttpClient}, using a default {@link ExecutionContext}.
     *
     * @param addressEventStream A stream of events (typically from a {@link ServiceDiscoverer#discover(Object)}) that
     *                           provides the addresses used to create new {@link HttpConnection}s
     * @return A new {@link AggregatedHttpClient}
     * @see #buildAggregated(ExecutionContext, Publisher)
     */
    default AggregatedHttpClient buildAggregated(Publisher<EventType> addressEventStream) {
        return build(addressEventStream).asAggregatedClient();
    }

    /**
     * Create a new {@link BlockingHttpClient}.
     *
     * @param executionContext {@link ExecutionContext} when building {@link BlockingHttpConnection}s.
     * @param addressEventStream A stream of events (typically from a {@link ServiceDiscoverer#discover(Object)}) that
     *                           provides the addresses used to create new {@link HttpConnection}s
     * @return {@link BlockingHttpClient}
     */
    default BlockingHttpClient buildBlocking(ExecutionContext executionContext,
                                             Publisher<EventType> addressEventStream) {
        return build(executionContext, addressEventStream).asBlockingClient();
    }

    /**
     * Create a new {@link BlockingHttpClient}, using a default {@link ExecutionContext}.
     *
     * @param addressEventStream A stream of events (typically from a {@link ServiceDiscoverer#discover(Object)}) that
     *                           provides the addresses used to create new {@link HttpConnection}s
     * @return {@link BlockingHttpClient}
     * @see #buildBlocking(ExecutionContext, Publisher)
     */
    default BlockingHttpClient buildBlocking(Publisher<EventType> addressEventStream) {
        return build(addressEventStream).asBlockingClient();
    }

    /**
     * Create a new {@link BlockingAggregatedHttpClient}.
     *
     * @param executionContext {@link ExecutionContext} when building {@link BlockingAggregatedHttpConnection}s.
     * @param addressEventStream A stream of events (typically from a {@link ServiceDiscoverer#discover(Object)}) that
     *                           provides the addresses used to create new {@link HttpConnection}s
     * @return {@link BlockingAggregatedHttpClient}
     */
    default BlockingAggregatedHttpClient buildBlockingAggregated(ExecutionContext executionContext,
                                                                 Publisher<EventType> addressEventStream) {
        return build(executionContext, addressEventStream).asBlockingAggregatedClient();
    }

    /**
     * Create a new {@link BlockingAggregatedHttpClient}, using a default {@link ExecutionContext}.
     *
     * @param addressEventStream A stream of events (typically from a {@link ServiceDiscoverer#discover(Object)}) that
     *                           provides the addresses used to create new {@link HttpConnection}s
     * @return {@link BlockingAggregatedHttpClient}
     * @see #buildBlockingAggregated(ExecutionContext, Publisher)
     */
    default BlockingAggregatedHttpClient buildBlockingAggregated(Publisher<EventType> addressEventStream) {
        return build(addressEventStream).asBlockingAggregatedClient();
    }
}

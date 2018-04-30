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
import io.servicetalk.concurrent.api.Executor;
import io.servicetalk.concurrent.api.Publisher;
import io.servicetalk.transport.api.IoExecutor;

/**
 * A builder of {@link HttpClient} objects.
 *
 * @param <ResolvedAddress> A resolved address that can be used to establish new {@link HttpConnection}s
 * @param <EventType> The type of {@link Event} which communicates address changes
 * @param <I> The type of content of the request
 * @param <O> The type of content of the response
 */
@FunctionalInterface
public interface HttpClientBuilder<ResolvedAddress, EventType extends Event<ResolvedAddress>, I, O> {

    /**
     * Build a new {@link HttpClient}.
     *
     * @param ioExecutor The {@link IoExecutor} to use for I/O
     * @param executor {@link Executor} to use for any asynchronous source created by the returned {@link HttpClient}
     * @param addressEventStream A stream of events (typically from a {@link ServiceDiscoverer#discover(Object)}) that
     *                           provides the addresses used to create new {@link HttpConnection}s
     * @return A new {@link HttpClient}
     */
    HttpClient<I, O> build(IoExecutor ioExecutor, Executor executor, Publisher<EventType> addressEventStream);
}

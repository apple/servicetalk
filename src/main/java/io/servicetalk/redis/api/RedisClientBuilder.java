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
package io.servicetalk.redis.api;

import io.servicetalk.client.api.ServiceDiscoverer;
import io.servicetalk.client.api.ServiceDiscoverer.Event;
import io.servicetalk.concurrent.api.Publisher;
import io.servicetalk.transport.api.ExecutionContext;

/**
 * A builder of {@link RedisClient} objects.
 * @param <ResolvedAddress> An resolved address that can be used to establish new {@link RedisConnection}s.
 * @param <EventType> The type of {@link Event} which communicates address changes.
 */
@FunctionalInterface
public interface RedisClientBuilder<ResolvedAddress, EventType extends Event<ResolvedAddress>> {

    /**
     * Build a new {@link RedisClient}.
     *
     * @param executionContext {@link ExecutionContext} used for {@link RedisConnection#getExecutionContext()} and to
     * build new {@link RedisConnection}s.
     * @param addressEventStream A stream of events (typically from a {@link ServiceDiscoverer#discover(Object)}) that
     *                           provides the addresses used to create new {@link RedisConnection}s.
     * @return A new {@link RedisClient}.
     */
    RedisClient build(ExecutionContext executionContext, Publisher<EventType> addressEventStream);
}

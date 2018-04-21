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
import io.servicetalk.concurrent.api.Executor;
import io.servicetalk.concurrent.api.Publisher;
import io.servicetalk.transport.api.IoExecutor;

import static io.servicetalk.concurrent.api.Executors.newCachedThreadExecutor;

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
     * @param ioExecutor The {@link IoExecutor} to use for I/O.
     * @param addressEventStream A stream of events (typically from a {@link ServiceDiscoverer#discover(Object)}) that
     *                           provides the addresses used to create new {@link RedisConnection}s.
     * @return A new {@link RedisClient}.
     */
    default RedisClient build(IoExecutor ioExecutor, Publisher<EventType> addressEventStream) {
        return build(ioExecutor, newCachedThreadExecutor(), addressEventStream);
    }

    /**
     * Build a new {@link RedisClient}.
     *
     * @param ioExecutor The {@link IoExecutor} to use for I/O.
     * @param executor {@link Executor} to use for any asynchronous source created by the returned {@link RedisClient}.
     * @param addressEventStream A stream of events (typically from a {@link ServiceDiscoverer#discover(Object)}) that
     *                           provides the addresses used to create new {@link RedisConnection}s.
     * @return A new {@link RedisClient}.
     */
    RedisClient build(IoExecutor ioExecutor, Executor executor, Publisher<EventType> addressEventStream);
}

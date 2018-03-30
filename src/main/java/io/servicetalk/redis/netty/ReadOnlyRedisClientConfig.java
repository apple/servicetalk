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
package io.servicetalk.redis.netty;

import io.servicetalk.redis.api.RedisConnection;
import io.servicetalk.tcp.netty.internal.ReadOnlyTcpClientConfig;
import io.servicetalk.tcp.netty.internal.TcpClientConfig;

import java.time.Duration;
import javax.annotation.Nullable;

import static java.util.Objects.requireNonNull;

class ReadOnlyRedisClientConfig {
    protected final ReadOnlyTcpClientConfig tcpClientConfig;
    protected int maxPipelinedRequests = 2; // one user request and one ping
    @Nullable
    protected Duration idleConnectionTimeout; // default to no idle timeout
    @Nullable
    protected Duration pingPeriod = Duration.ofSeconds(30);
    protected boolean deferSubscribeTillConnect;

    ReadOnlyRedisClientConfig(final ReadOnlyTcpClientConfig tcpClientConfig) {
        this.tcpClientConfig = requireNonNull(tcpClientConfig);
    }

    /**
     * Copy constructor.
     *
     * @param from Source to copy from.
     */
    ReadOnlyRedisClientConfig(final RedisClientConfig from) {
        tcpClientConfig = from.getTcpClientConfig().asReadOnly();
        maxPipelinedRequests = from.maxPipelinedRequests;
        idleConnectionTimeout = from.idleConnectionTimeout;
        pingPeriod = from.pingPeriod;
        deferSubscribeTillConnect = from.deferSubscribeTillConnect;
    }

    /**
     * Get maximum requests that can be pipelined on a connection created by this builder.
     *
     * @return Maximum number of pipelined requests per {@link RedisConnection}.
     */
    int getMaxPipelinedRequests() {
        return maxPipelinedRequests;
    }

    /**
     * Get the idle timeout for connections created by this builder.
     *
     * @return the timeout {@link Duration} or {@code null} if no timeout configured.
     */
    @Nullable
    Duration getIdleConnectionTimeout() {
        return idleConnectionTimeout;
    }

    /**
     * Get the ping period to keep alive connections created by this builder.
     *
     * @return the {@link Duration} between keep-alive pings or {@code null} if disabled.
     */
    @Nullable
    Duration getPingPeriod() {
        return pingPeriod;
    }

    /**
     * Get the {@link TcpClientConfig}.
     *
     * @return the {@link TcpClientConfig}.
     */
    ReadOnlyTcpClientConfig getTcpClientConfig() {
        return tcpClientConfig;
    }

    /**
     * WARNING: internal API not to be exposed in the {@link DefaultRedisClientBuilder}.
     *
     * @return {@code true} when subscribe signal needs to be deferred until the Redis PubSub subscribe ack
     */
    boolean isDeferSubscribeTillConnect() {
        return deferSubscribeTillConnect;
    }
}

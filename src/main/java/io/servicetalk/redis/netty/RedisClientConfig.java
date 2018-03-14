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
import io.servicetalk.tcp.netty.internal.TcpClientConfig;

import java.time.Duration;

import javax.annotation.Nullable;

final class RedisClientConfig extends ReadOnlyRedisClientConfig {
    /**
     * New instance.
     *
     * @param tcpClientConfig the {@link TcpClientConfig} to use.
     */
    RedisClientConfig(final TcpClientConfig tcpClientConfig) {
        super(tcpClientConfig);
    }

    /**
     * Sets maximum requests that can be pipelined on a connection created by this builder.
     *
     * @param maxPipelinedRequests Maximum number of pipelined requests per {@link RedisConnection}.
     * @return {@code this}.
     */
    RedisClientConfig setMaxPipelinedRequests(final int maxPipelinedRequests) {
        if (maxPipelinedRequests <= 0) {
            throw new IllegalArgumentException("maxPipelinedRequests: " + maxPipelinedRequests + " (expected >0)");
        }
        this.maxPipelinedRequests = maxPipelinedRequests;
        return this;
    }

    /**
     * Sets the idle timeout for connections created by this builder.
     *
     * @param idleConnectionTimeout the timeout {@link Duration} or {@code null} if no timeout configured.
     * @return {@code this}.
     */
    RedisClientConfig setIdleConnectionTimeout(@Nullable final Duration idleConnectionTimeout) {
        this.idleConnectionTimeout = idleConnectionTimeout;
        return this;
    }

    /**
     * Sets the ping period to keep alive connections created by this builder.
     *
     * @param pingPeriod the {@link Duration} between keep-alive pings or {@code null} to disable pings.
     * @return {@code this}.
     */
    RedisClientConfig setPingPeriod(@Nullable final Duration pingPeriod) {
        this.pingPeriod = pingPeriod;
        return this;
    }

    /**
     * Get the {@link TcpClientConfig}.
     *
     * @return the {@link TcpClientConfig}.
     */
    @Override
    TcpClientConfig getTcpClientConfig() {
        return (TcpClientConfig) tcpClientConfig;
    }

    /**
     * Returns an immutable view of this config, any changes to this config will not alter the returned view.
     *
     * @return {@link ReadOnlyRedisClientConfig}.
     */
    public ReadOnlyRedisClientConfig asReadOnly() {
        return new ReadOnlyRedisClientConfig(this);
    }
}

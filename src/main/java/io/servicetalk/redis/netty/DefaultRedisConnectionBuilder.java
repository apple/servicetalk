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

import io.servicetalk.concurrent.api.Single;
import io.servicetalk.redis.api.RedisConnection;
import io.servicetalk.redis.api.RedisConnectionBuilder;
import io.servicetalk.redis.api.RedisData;
import io.servicetalk.tcp.netty.internal.ReadOnlyTcpClientConfig;
import io.servicetalk.tcp.netty.internal.TcpClientChannelInitializer;
import io.servicetalk.tcp.netty.internal.TcpClientConfig;
import io.servicetalk.tcp.netty.internal.TcpConnector;
import io.servicetalk.transport.api.ExecutionContext;
import io.servicetalk.transport.api.SslConfig;
import io.servicetalk.transport.netty.internal.ChannelInitializer;
import io.servicetalk.transport.netty.internal.Connection;

import io.netty.buffer.ByteBuf;

import java.io.InputStream;
import java.net.SocketOption;
import java.time.Duration;
import java.util.function.Function;
import javax.annotation.Nullable;

import static io.servicetalk.redis.netty.InternalSubscribedRedisConnection.newSubscribedConnection;
import static io.servicetalk.redis.netty.PipelinedRedisConnection.newPipelinedConnection;
import static java.util.Objects.requireNonNull;
import static java.util.function.Function.identity;

/**
 * A builder for instances of {@link RedisConnection}.
 *
 * @param <ResolvedAddress> the type of address after resolution.
 */
public final class DefaultRedisConnectionBuilder<ResolvedAddress> implements RedisConnectionBuilder<ResolvedAddress> {
    private final RedisClientConfig config;
    private Function<RedisConnection, RedisConnection> connectionFilterFactory = identity();
    private final boolean forSubscribe;

    private DefaultRedisConnectionBuilder(boolean forSubscribe) {
        this(forSubscribe, new RedisClientConfig(new TcpClientConfig(false)));
    }

    private DefaultRedisConnectionBuilder(boolean forSubscribe, RedisClientConfig config) {
        this.forSubscribe = forSubscribe;
        this.config = requireNonNull(config);
    }

    /**
     * Enable SSL/TLS using the provided {@link SslConfig}. To disable SSL pass in {@code null}.
     *
     * @param config the {@link SslConfig}.
     * @return {@code this}.
     * @throws IllegalStateException if accessing the cert/key throws when {@link InputStream#close()} is called.
     */
    public DefaultRedisConnectionBuilder<ResolvedAddress> setSsl(@Nullable SslConfig config) {
        this.config.getTcpClientConfig().setSslConfig(config);
        return this;
    }

    /**
     * Add a {@link SocketOption} for all connections created by this builder.
     *
     * @param <T>    the type of the value.
     * @param option the option to apply.
     * @param value  the value.
     * @return {@code this}.
     */
    public <T> DefaultRedisConnectionBuilder<ResolvedAddress> setOption(SocketOption<T> option, T value) {
        config.getTcpClientConfig().setOption(option, value);
        return this;
    }

    /**
     * Enables wire-logging for the connections created by this builder.
     *
     * @param loggerName Name of the logger.
     * @return {@code this}.
     */
    public DefaultRedisConnectionBuilder<ResolvedAddress> enableWireLog(String loggerName) {
        config.getTcpClientConfig().setWireLoggerName(loggerName);
        return this;
    }

    /**
     * Disabled wire-logging for the connections created by this builder.
     *
     * @return {@code this}.
     */
    public DefaultRedisConnectionBuilder<ResolvedAddress> disableWireLog() {
        config.getTcpClientConfig().disableWireLog();
        return this;
    }

    /**
     * Sets maximum requests that can be pipelined on a connection created by this builder.
     *
     * @param maxPipelinedRequests Maximum number of pipelined requests per {@link RedisConnection}.
     * @return {@code this}.
     */
    public DefaultRedisConnectionBuilder<ResolvedAddress> setMaxPipelinedRequests(int maxPipelinedRequests) {
        config.setMaxPipelinedRequests(maxPipelinedRequests);
        return this;
    }

    /**
     * Sets the idle timeout for connections created by this builder.
     *
     * @param idleConnectionTimeout the timeout {@link Duration} or {@code null} if no timeout configured.
     * @return {@code this}.
     */
    public DefaultRedisConnectionBuilder<ResolvedAddress> setIdleConnectionTimeout(@Nullable Duration idleConnectionTimeout) {
        config.setIdleConnectionTimeout(idleConnectionTimeout);
        return this;
    }

    /**
     * Sets the ping period to keep alive connections created by this builder.
     *
     * @param pingPeriod the {@link Duration} between keep-alive pings or {@code null} to disable pings.
     * @return {@code this}.
     */
    public DefaultRedisConnectionBuilder<ResolvedAddress> setPingPeriod(@Nullable final Duration pingPeriod) {
        config.setPingPeriod(pingPeriod);
        return this;
    }

    /**
     * Set the filter factory that is used to decorate {@link RedisConnection} created by this builder.
     * <p>
     * Note this method will be used to decorate the result of {@link #build(ExecutionContext, Object)} before it is
     * returned to the user.
     * @param connectionFilterFactory {@link Function} to decorate a {@link RedisConnection} for the purpose of
     * filtering
     * @return {@code this}
     */
    public DefaultRedisConnectionBuilder<ResolvedAddress> setConnectionFilterFactory(
            Function<RedisConnection, RedisConnection> connectionFilterFactory) {
        this.connectionFilterFactory = requireNonNull(connectionFilterFactory);
        return this;
    }

    /**
     * Creates a new {@link DefaultRedisConnectionBuilder} to build connections only for
     * <a href="https://redis.io/topics/pubsub">Redis Subscribe mode</a>.
     *
     * @param <ResolvedAddress> the type of address after resolution.
     * @return A new instance of {@link DefaultRedisConnectionBuilder} that will build connections only for Redis Subscriber mode.
     */
    public static <ResolvedAddress> DefaultRedisConnectionBuilder<ResolvedAddress> forSubscribe() {
        return new DefaultRedisConnectionBuilder<>(true);
    }

    /**
     * Creates a new {@link DefaultRedisConnectionBuilder} to build connections only for
     * <a href="https://redis.io/topics/pubsub">Redis Subscribe mode</a>.
     *
     * WARNING: Internal API used by Unit tests.
     *
     * @param <ResolvedAddress> the type of address after resolution.
     * @param config the {@link RedisClientConfig} to provide config values not exposed on the builder
     * @return A new instance of {@link DefaultRedisConnectionBuilder} that will build connections only for Redis
     * Subscriber mode.
     */
    static <ResolvedAddress> DefaultRedisConnectionBuilder<ResolvedAddress> forSubscribe(RedisClientConfig config) {
        return new DefaultRedisConnectionBuilder<>(true, config);
    }

    /**
     * Creates a new {@link DefaultRedisConnectionBuilder} to build connections that will always pipeline requests
     * and hence a <a href="https://redis.io/topics/pubsub">Subscribe request</a> may indefinitely delay any request
     * pipelined after that. Thus, it is advised not to use connections created by this builder for subscribe requests.
     *
     * @param <ResolvedAddress> the type of address after resolution.
     * @return A new instance of {@link DefaultRedisConnectionBuilder} that will build connections only for Redis
     * Subscriber mode.
     */
    public static <ResolvedAddress> DefaultRedisConnectionBuilder<ResolvedAddress> forPipeline() {
        return new DefaultRedisConnectionBuilder<>(false);
    }

    @Override
    public Single<RedisConnection> build(final ExecutionContext executionContext,
                                         final ResolvedAddress resolvedAddress) {
        final ReadOnlyRedisClientConfig roConfig = config.asReadOnly();
        // ConcurrencyFilter -> User Filters -> IdleReaper -> Connection
        return forSubscribe ?
                buildForSubscribe(executionContext, resolvedAddress, roConfig, connectionFilterFactory)
                        .map(RedisSubscribedConcurrencyLimitingFilter::new) :
                buildForPipelined(executionContext, resolvedAddress, roConfig, connectionFilterFactory)
                        .map(filteredConnection -> new RedisConnectionConcurrentRequestsFilter(filteredConnection,
                                roConfig.getMaxPipelinedRequests()));
    }

    static <ResolvedAddress> Single<RedisConnection> buildForSubscribe(ExecutionContext executionContext,
                                                                       ResolvedAddress resolvedAddress,
                                                                       ReadOnlyRedisClientConfig roConfig,
                                               Function<RedisConnection, RedisConnection> connectionFilterFactory) {
        return roConfig.getIdleConnectionTimeout() == null ? build(executionContext, resolvedAddress, roConfig, conn ->
                connectionFilterFactory.apply(newSubscribedConnection(conn, executionContext, roConfig))) :
                // User Filters -> IdleReaper -> Connection
                build(executionContext, resolvedAddress, roConfig, conn -> connectionFilterFactory.apply(
                        new RedisIdleConnectionReaper(roConfig.getIdleConnectionTimeout()).apply(
                                newSubscribedConnection(conn, executionContext, roConfig))));
    }

    static <ResolvedAddress> Single<RedisConnection> buildForPipelined(ExecutionContext executionContext,
                                                                       ResolvedAddress resolvedAddress,
                                                                       ReadOnlyRedisClientConfig roConfig,
                                               Function<RedisConnection, RedisConnection> connectionFilterFactory) {
        return roConfig.getIdleConnectionTimeout() == null ? build(executionContext, resolvedAddress, roConfig, conn ->
                connectionFilterFactory.apply(newPipelinedConnection(conn, executionContext, roConfig))) :
                // User Filters -> IdleReaper -> Connection
                build(executionContext, resolvedAddress, roConfig, conn -> connectionFilterFactory.apply(
                        new RedisIdleConnectionReaper(roConfig.getIdleConnectionTimeout()).apply(
                                newPipelinedConnection(conn, executionContext, roConfig))));
    }

    private static <ResolvedAddress> Single<RedisConnection> build(ExecutionContext executionContext,
                                                 ResolvedAddress resolvedAddress,
                                                 ReadOnlyRedisClientConfig roConfig,
                                   Function<Connection<RedisData, ByteBuf>, RedisConnection> mapper) {
        return new Single<RedisConnection>() {
            @Override
            protected void handleSubscribe(final Subscriber<? super RedisConnection> subscriber) {
                final ReadOnlyTcpClientConfig roTcpConfig = roConfig.getTcpClientConfig();
                final ChannelInitializer initializer = new TcpClientChannelInitializer(roTcpConfig)
                        .andThen(new RedisClientChannelInitializer());

                final TcpConnector<RedisData, ByteBuf> connector =
                        new TcpConnector<>(roTcpConfig, initializer, () -> o -> false);
                connector.connect(executionContext, resolvedAddress).map(mapper).subscribe(subscriber);
            }
        };
    }
}

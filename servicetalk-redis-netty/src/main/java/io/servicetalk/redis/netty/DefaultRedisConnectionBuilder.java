/*
 * Copyright © 2018-2019 Apple Inc. and the ServiceTalk project authors
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

import io.servicetalk.buffer.api.BufferAllocator;
import io.servicetalk.concurrent.api.Executor;
import io.servicetalk.concurrent.api.Single;
import io.servicetalk.redis.api.RedisConnection;
import io.servicetalk.redis.api.RedisConnectionBuilder;
import io.servicetalk.redis.api.RedisConnectionFilterFactory;
import io.servicetalk.redis.api.RedisData;
import io.servicetalk.tcp.netty.internal.ReadOnlyTcpClientConfig;
import io.servicetalk.tcp.netty.internal.TcpClientChannelInitializer;
import io.servicetalk.tcp.netty.internal.TcpClientConfig;
import io.servicetalk.tcp.netty.internal.TcpConnector;
import io.servicetalk.transport.api.ExecutionContext;
import io.servicetalk.transport.api.IoExecutor;
import io.servicetalk.transport.api.SslConfig;
import io.servicetalk.transport.netty.internal.CloseHandler;
import io.servicetalk.transport.netty.internal.DefaultNettyConnection;
import io.servicetalk.transport.netty.internal.ExecutionContextBuilder;
import io.servicetalk.transport.netty.internal.NettyConnection;
import io.servicetalk.transport.netty.internal.NettyConnection.TerminalPredicate;

import io.netty.buffer.ByteBuf;

import java.io.InputStream;
import java.net.SocketOption;
import java.time.Duration;
import java.util.function.UnaryOperator;
import javax.annotation.Nullable;

import static io.servicetalk.redis.api.RedisConnectionFilterFactory.identity;
import static io.servicetalk.redis.netty.InternalSubscribedRedisConnection.newSubscribedConnection;
import static io.servicetalk.redis.netty.PipelinedRedisConnection.newPipelinedConnection;
import static io.servicetalk.transport.netty.internal.CloseHandler.forPipelinedRequestResponse;
import static java.util.Objects.requireNonNull;

/**
 * A builder for instances of {@link RedisConnection}.
 *
 * @param <ResolvedAddress> the type of address after resolution.
 */
public final class DefaultRedisConnectionBuilder<ResolvedAddress> implements RedisConnectionBuilder<ResolvedAddress> {
    private final RedisClientConfig config;
    private final boolean forSubscribe;
    private final ExecutionContextBuilder executionContextBuilder = new ExecutionContextBuilder();
    private RedisConnectionFilterFactory connectionFilterFactory = identity();

    private DefaultRedisConnectionBuilder(boolean forSubscribe) {
        this(forSubscribe, new RedisClientConfig(new TcpClientConfig(false)));
    }

    private DefaultRedisConnectionBuilder(boolean forSubscribe, RedisClientConfig config) {
        this.forSubscribe = forSubscribe;
        this.config = requireNonNull(config);
    }

    @Override
    public RedisConnectionBuilder<ResolvedAddress> ioExecutor(final IoExecutor ioExecutor) {
        executionContextBuilder.ioExecutor(ioExecutor);
        return this;
    }

    @Override
    public RedisConnectionBuilder<ResolvedAddress> executor(final Executor executor) {
        executionContextBuilder.executor(executor);
        return this;
    }

    @Override
    public RedisConnectionBuilder<ResolvedAddress> bufferAllocator(final BufferAllocator allocator) {
        executionContextBuilder.bufferAllocator(allocator);
        return this;
    }

    /**
     * Enable SSL/TLS using the provided {@link SslConfig}. To disable SSL pass in {@code null}.
     *
     * @param config the {@link SslConfig}.
     * @return {@code this}.
     * @throws IllegalStateException if accessing the cert/key throws when {@link InputStream#close()} is called.
     */
    public DefaultRedisConnectionBuilder<ResolvedAddress> ssl(@Nullable SslConfig config) {
        this.config.tcpClientConfig().sslConfig(config);
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
    public <T> DefaultRedisConnectionBuilder<ResolvedAddress> socketOption(SocketOption<T> option, T value) {
        config.tcpClientConfig().socketOption(option, value);
        return this;
    }

    /**
     * Enable wire-logging for connections created by this builder. All wire events will be logged at trace level.
     *
     * @param loggerName The name of the logger to log wire events.
     * @return {@code this}.
     */
    public DefaultRedisConnectionBuilder<ResolvedAddress> enableWireLogging(String loggerName) {
        config.tcpClientConfig().enableWireLogging(loggerName);
        return this;
    }

    /**
     * Disable previously configured wire-logging for connections created by this builder.
     * If wire-logging has not been configured before, this method has no effect.
     *
     * @return {@code this}.
     * @see #enableWireLogging(String)
     */
    public DefaultRedisConnectionBuilder<ResolvedAddress> disableWireLogging() {
        config.tcpClientConfig().disableWireLogging();
        return this;
    }

    /**
     * Sets maximum requests that can be pipelined on a connection created by this builder.
     *
     * @param maxPipelinedRequests Maximum number of pipelined requests per {@link RedisConnection}.
     * @return {@code this}.
     */
    public DefaultRedisConnectionBuilder<ResolvedAddress> maxPipelinedRequests(int maxPipelinedRequests) {
        config.maxPipelinedRequests(maxPipelinedRequests);
        return this;
    }

    /**
     * Sets the idle timeout for connections created by this builder.
     *
     * @param idleConnectionTimeout the timeout {@link Duration} or {@code null} if no timeout configured.
     * @return {@code this}.
     */
    public DefaultRedisConnectionBuilder<ResolvedAddress> idleConnectionTimeout(
            @Nullable Duration idleConnectionTimeout) {
        config.idleConnectionTimeout(idleConnectionTimeout);
        return this;
    }

    /**
     * Sets the ping period to keep alive connections created by this builder.
     *
     * @param pingPeriod the {@link Duration} between keep-alive pings or {@code null} to disable pings.
     * @return {@code this}.
     */
    public DefaultRedisConnectionBuilder<ResolvedAddress> pingPeriod(@Nullable final Duration pingPeriod) {
        config.pingPeriod(pingPeriod);
        return this;
    }

    /**
     * Set the filter factory that is used to decorate {@link RedisConnection} created by this builder.
     * <p>
     * Note this method will be used to decorate the result of {@link #build(Object)} before it is
     * returned to the user.
     * @param connectionFilterFactory {@link UnaryOperator} to decorate a {@link RedisConnection} for the purpose of
     * filtering.
     * @return {@code this}
     */
    public DefaultRedisConnectionBuilder<ResolvedAddress> appendConnectionFilter(
            RedisConnectionFilterFactory connectionFilterFactory) {
        this.connectionFilterFactory = requireNonNull(connectionFilterFactory);
        return this;
    }

    /**
     * Creates a new {@link DefaultRedisConnectionBuilder} to build connections only for
     * <a href="https://redis.io/topics/pubsub">Redis Subscribe mode</a>.
     *
     * @param <ResolvedAddress> the type of address after resolution.
     * @return A new instance of {@link DefaultRedisConnectionBuilder} that will build connections only for Redis
     * Subscriber mode.
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
    public Single<RedisConnection> build(final ResolvedAddress resolvedAddress) {
        final ReadOnlyRedisClientConfig roConfig = config.asReadOnly();
        ExecutionContext context = executionContextBuilder.build();
        // ConcurrencyFilter -> User Filters -> IdleReaper -> Connection
        return forSubscribe ?
                buildForSubscribe(context, resolvedAddress, roConfig, connectionFilterFactory)
                        .map(RedisSubscribedConcurrencyLimitingFilter::new) :
                buildForPipelined(context, resolvedAddress, roConfig, connectionFilterFactory)
                        .map(filteredConnection -> new RedisConnectionConcurrentRequestsFilter(filteredConnection,
                                roConfig.maxPipelinedRequests()));
    }

    static <ResolvedAddress> Single<RedisConnection> buildForSubscribe(ExecutionContext executionContext,
                                                                       ResolvedAddress resolvedAddress,
                                                                       ReadOnlyRedisClientConfig roConfig,
                                                               RedisConnectionFilterFactory connectionFilterFactory) {
        return roConfig.idleConnectionTimeout() == null ? build(executionContext, resolvedAddress, roConfig).map(
                conn -> connectionFilterFactory.create(newSubscribedConnection(conn, executionContext, roConfig))) :
                // User Filters -> IdleReaper -> Connection
                build(executionContext, resolvedAddress, roConfig).map(conn -> connectionFilterFactory.create(
                        new RedisIdleConnectionReaper(roConfig.idleConnectionTimeout()).apply(
                                newSubscribedConnection(conn, executionContext, roConfig))));
    }

    static <ResolvedAddress> Single<RedisConnection> buildForPipelined(ExecutionContext executionContext,
                                                                       ResolvedAddress resolvedAddress,
                                                                       ReadOnlyRedisClientConfig roConfig,
                                                               RedisConnectionFilterFactory connectionFilterFactory) {
        return roConfig.idleConnectionTimeout() == null ? build(executionContext, resolvedAddress, roConfig).map(
                conn -> connectionFilterFactory.create(newPipelinedConnection(conn, executionContext, roConfig))) :
                // User Filters -> IdleReaper -> Connection
                build(executionContext, resolvedAddress, roConfig).map(conn -> connectionFilterFactory.create(
                        new RedisIdleConnectionReaper(roConfig.idleConnectionTimeout()).apply(
                                newPipelinedConnection(conn, executionContext, roConfig))));
    }

    private static <ResolvedAddress> Single<? extends NettyConnection<RedisData, ByteBuf>> build(
            ExecutionContext executionContext, ResolvedAddress resolvedAddress, ReadOnlyRedisClientConfig roConfig) {
        // This state is read only, so safe to keep a copy across Subscribers
        final ReadOnlyTcpClientConfig tcpClientConfig = roConfig.tcpClientConfig();
        return TcpConnector.connect(null, resolvedAddress, tcpClientConfig, executionContext)
                .flatMap(channel -> {
                    CloseHandler closeHandler = forPipelinedRequestResponse(true, channel.config());
                    return DefaultNettyConnection.initChannel(channel, executionContext.bufferAllocator(),
                            executionContext.executor(), new TerminalPredicate<>(o -> false), closeHandler,
                            tcpClientConfig.flushStrategy(), new TcpClientChannelInitializer(
                                    tcpClientConfig).andThen(new RedisClientChannelInitializer()));
                });
    }
}

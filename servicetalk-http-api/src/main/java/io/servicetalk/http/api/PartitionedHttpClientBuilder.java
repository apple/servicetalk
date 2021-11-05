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
package io.servicetalk.http.api;

import io.servicetalk.buffer.api.BufferAllocator;
import io.servicetalk.client.api.AutoRetryStrategyProvider;
import io.servicetalk.client.api.ConnectionFactoryFilter;
import io.servicetalk.client.api.ServiceDiscoverer;
import io.servicetalk.client.api.ServiceDiscovererEvent;
import io.servicetalk.client.api.partition.PartitionAttributes;
import io.servicetalk.client.api.partition.PartitionMapFactory;
import io.servicetalk.client.api.partition.PartitionedServiceDiscovererEvent;
import io.servicetalk.concurrent.PublisherSource.Subscriber;
import io.servicetalk.concurrent.api.Executor;
import io.servicetalk.logging.api.LogLevel;
import io.servicetalk.transport.api.ClientSslConfig;
import io.servicetalk.transport.api.IoExecutor;

import java.net.SocketOption;
import java.util.function.BiFunction;
import java.util.function.BooleanSupplier;
import java.util.function.Function;
import java.util.function.Predicate;

/**
 * A builder of homogeneous {@link StreamingHttpClient} instances which call the server associated with a partition
 * selected from a set of {@link PartitionedServiceDiscovererEvent}s resolved from a single unresolved address.
 * <p>
 * Partition selection uses a function to infer {@link PartitionAttributes} from the {@link HttpRequestMetaData}.
 * It also provides a good set of default settings and configurations, which could be used by most users as-is or
 * could be overridden to address specific use cases.
 * @param <U> the type of address before resolution (unresolved address)
 * @param <R> the type of address after resolution (resolved address)
 */
public abstract class PartitionedHttpClientBuilder<U, R>
        extends BaseSingleAddressHttpClientBuilder<U, R, PartitionedServiceDiscovererEvent<R>> {
    /**
     * Initializes the {@link SingleAddressHttpClientBuilder} for each new client.
     * @param <U> the type of address before resolution (unresolved address)
     * @param <R> the type of address after resolution (resolved address)
     */
    @FunctionalInterface
    public interface SingleAddressInitializer<U, R> {
        /**
         * Configures the passed {@link SingleAddressHttpClientBuilder} for a given set of {@link PartitionAttributes}.
         *
         * @param attr the {@link PartitionAttributes} for the partition
         * @param builder {@link SingleAddressHttpClientBuilder} to configure for the given {@link PartitionAttributes}
         */
        void initialize(PartitionAttributes attr, SingleAddressHttpClientBuilder<U, R> builder);

        /**
         * Appends the passed {@link SingleAddressInitializer} to this
         * {@link SingleAddressInitializer} such that this {@link SingleAddressInitializer} is
         * applied first and then the passed {@link SingleAddressInitializer}.
         *
         * @param toAppend {@link SingleAddressInitializer} to append
         * @return A composite {@link SingleAddressInitializer} after the append operation.
         */
        default SingleAddressInitializer<U, R> append(SingleAddressInitializer<U, R> toAppend) {
            return (attr, builder) -> {
                initialize(attr, builder);
                toAppend.initialize(attr, builder);
            };
        }
    }

    /**
     * {@inheritDoc}
     * @deprecated {@link #initializer(SingleAddressInitializer)} and
     * {@link SingleAddressHttpClientBuilder#ioExecutor(IoExecutor)} on the last argument of
     * {@link SingleAddressInitializer#initialize(PartitionAttributes, SingleAddressHttpClientBuilder)}.
     */
    @Deprecated
    @Override
    public abstract PartitionedHttpClientBuilder<U, R> ioExecutor(IoExecutor ioExecutor);

    @Override
    public PartitionedHttpClientBuilder<U, R> executor(Executor executor) {
        throw new UnsupportedOperationException("Setting Executor not yet supported by " + getClass().getSimpleName());
    }

    /**
     * {@inheritDoc}
     * @deprecated {@link #initializer(SingleAddressInitializer)} and
     * {@link SingleAddressHttpClientBuilder#executionStrategy(HttpExecutionStrategy)} on the last argument of
     * {@link SingleAddressInitializer#initialize(PartitionAttributes, SingleAddressHttpClientBuilder)}.
     */
    @Deprecated
    @Override
    public abstract PartitionedHttpClientBuilder<U, R> executionStrategy(HttpExecutionStrategy strategy);

    /**
     * {@inheritDoc}
     * @deprecated {@link #initializer(SingleAddressInitializer)} and
     * {@link SingleAddressHttpClientBuilder#bufferAllocator(BufferAllocator)} on the last argument of
     * {@link SingleAddressInitializer#initialize(PartitionAttributes, SingleAddressHttpClientBuilder)}.
     */
    @Deprecated
    @Override
    public abstract PartitionedHttpClientBuilder<U, R> bufferAllocator(BufferAllocator allocator);

    /**
     * {@inheritDoc}
     * @deprecated {@link #initializer(SingleAddressInitializer)} and
     * {@link SingleAddressHttpClientBuilder#socketOption(SocketOption, Object)} on the last argument of
     * {@link SingleAddressInitializer#initialize(PartitionAttributes, SingleAddressHttpClientBuilder)}.
     */
    @Deprecated
    @Override
    public abstract <T> PartitionedHttpClientBuilder<U, R> socketOption(SocketOption<T> option, T value);

    @Deprecated
    @Override
    public abstract PartitionedHttpClientBuilder<U, R> enableWireLogging(String loggerName);

    /**
     * {@inheritDoc}
     * @deprecated {@link #initializer(SingleAddressInitializer)} and
     * {@link SingleAddressHttpClientBuilder#enableWireLogging(String, LogLevel, BooleanSupplier)} on the last argument
     * of {@link SingleAddressInitializer#initialize(PartitionAttributes, SingleAddressHttpClientBuilder)}.
     */
    @Deprecated
    @Override
    public abstract PartitionedHttpClientBuilder<U, R> enableWireLogging(String loggerName,
                                                                         LogLevel logLevel,
                                                                         BooleanSupplier logUserData);

    /**
     * {@inheritDoc}
     * @deprecated {@link #initializer(SingleAddressInitializer)} and
     * {@link SingleAddressHttpClientBuilder#protocols(HttpProtocolConfig...)} on the last argument of
     * {@link SingleAddressInitializer#initialize(PartitionAttributes, SingleAddressHttpClientBuilder)}.
     */
    @Deprecated
    @Override
    public abstract PartitionedHttpClientBuilder<U, R> protocols(HttpProtocolConfig... protocols);

    /**
     * {@inheritDoc}
     * @deprecated {@link #initializer(SingleAddressInitializer)} and
     * {@link SingleAddressHttpClientBuilder#appendConnectionFilter(StreamingHttpConnectionFilterFactory)} on the last
     * argument of {@link SingleAddressInitializer#initialize(PartitionAttributes, SingleAddressHttpClientBuilder)}.
     */
    @Deprecated
    @Override
    public abstract PartitionedHttpClientBuilder<U, R> appendConnectionFilter(
            StreamingHttpConnectionFilterFactory factory);

    /**
     * {@inheritDoc}
     * @deprecated {@link #initializer(SingleAddressInitializer)} and
     * {@link SingleAddressHttpClientBuilder#appendConnectionFilter(Predicate, StreamingHttpConnectionFilterFactory)} on
     * the last argument of {@link SingleAddressInitializer#initialize(PartitionAttributes,
     * SingleAddressHttpClientBuilder)}.
     */
    @Deprecated
    @Override
    public PartitionedHttpClientBuilder<U, R> appendConnectionFilter(Predicate<StreamingHttpRequest> predicate,
                                                                     StreamingHttpConnectionFilterFactory factory) {
        return (PartitionedHttpClientBuilder<U, R>)
                super.appendConnectionFilter(predicate, factory);
    }

    /**
     * {@inheritDoc}
     * @deprecated {@link #initializer(SingleAddressInitializer)} and
     * {@link SingleAddressHttpClientBuilder#appendConnectionFactoryFilter(ConnectionFactoryFilter)} on the last
     * argument of {@link SingleAddressInitializer#initialize(PartitionAttributes, SingleAddressHttpClientBuilder)}.
     */
    @Deprecated
    @Override
    public abstract PartitionedHttpClientBuilder<U, R> appendConnectionFactoryFilter(
            ConnectionFactoryFilter<R, FilterableStreamingHttpConnection> factory);

    /**
     * {@inheritDoc}
     * @deprecated {@link #initializer(SingleAddressInitializer)} and
     * {@link SingleAddressHttpClientBuilder#disableHostHeaderFallback()} on the last argument of
     * {@link SingleAddressInitializer#initialize(PartitionAttributes, SingleAddressHttpClientBuilder)}.
     */
    @Deprecated
    @Override
    public abstract PartitionedHttpClientBuilder<U, R> disableHostHeaderFallback();

    /**
     * {@inheritDoc}
     * @deprecated {@link #initializer(SingleAddressInitializer)} and
     * {@link SingleAddressHttpClientBuilder#allowDropResponseTrailers(boolean)} on the last argument of
     * {@link SingleAddressInitializer#initialize(PartitionAttributes, SingleAddressHttpClientBuilder)}.
     */
    @Deprecated
    @Override
    public abstract PartitionedHttpClientBuilder<U, R> allowDropResponseTrailers(boolean allowDrop);

    /**
     * {@inheritDoc}
     * @deprecated {@link #initializer(SingleAddressInitializer)} and
     * {@link SingleAddressHttpClientBuilder#autoRetryStrategy(AutoRetryStrategyProvider)} on the last argument of
     * {@link SingleAddressInitializer#initialize(PartitionAttributes, SingleAddressHttpClientBuilder)}.
     */
    @Deprecated
    @Override
    public abstract PartitionedHttpClientBuilder<U, R> autoRetryStrategy(
            AutoRetryStrategyProvider autoRetryStrategyProvider);

    @Override
    public abstract PartitionedHttpClientBuilder<U, R> serviceDiscoverer(
            ServiceDiscoverer<U, R, PartitionedServiceDiscovererEvent<R>> serviceDiscoverer);

    /**
     * {@inheritDoc}
     * @deprecated {@link #initializer(SingleAddressInitializer)} and
     * {@link SingleAddressHttpClientBuilder#loadBalancerFactory(HttpLoadBalancerFactory)} on the last argument of
     * {@link SingleAddressInitializer#initialize(PartitionAttributes, SingleAddressHttpClientBuilder)}.
     */
    @Deprecated
    @Override
    public abstract PartitionedHttpClientBuilder<U, R> loadBalancerFactory(
            HttpLoadBalancerFactory<R> loadBalancerFactory);

    /**
     * {@inheritDoc}
     * @deprecated {@link #initializer(SingleAddressInitializer)} and
     * {@link SingleAddressHttpClientBuilder#unresolvedAddressToHost(Function)} on the last argument of
     * {@link SingleAddressInitializer#initialize(PartitionAttributes, SingleAddressHttpClientBuilder)}.
     */
    @Deprecated
    @Override
    public abstract PartitionedHttpClientBuilder<U, R> unresolvedAddressToHost(
            Function<U, CharSequence> unresolvedAddressToHostFunction);

    /**
     * {@inheritDoc}
     * @deprecated {@link #initializer(SingleAddressInitializer)} and
     * {@link SingleAddressHttpClientBuilder#appendClientFilter(StreamingHttpClientFilterFactory)} on the last argument
     * of {@link SingleAddressInitializer#initialize(PartitionAttributes, SingleAddressHttpClientBuilder)}.
     */
    @Deprecated
    @Override
    public abstract PartitionedHttpClientBuilder<U, R> appendClientFilter(StreamingHttpClientFilterFactory function);

    /**
     * {@inheritDoc}
     * @deprecated {@link #initializer(SingleAddressInitializer)} and
     * {@link SingleAddressHttpClientBuilder#appendClientFilter(Predicate, StreamingHttpClientFilterFactory)} on the
     * last argument of {@link SingleAddressInitializer#initialize(PartitionAttributes,
     * SingleAddressHttpClientBuilder)}.
     */
    @Deprecated
    @Override
    public PartitionedHttpClientBuilder<U, R> appendClientFilter(Predicate<StreamingHttpRequest> predicate,
                                                                 StreamingHttpClientFilterFactory factory) {
        return (PartitionedHttpClientBuilder<U, R>)
                super.appendClientFilter(predicate, factory);
    }

    /**
     * Initiates security configuration for this client. Calling
     * {@link PartitionedHttpClientSecurityConfigurator#commit()} on the returned
     * {@link PartitionedHttpClientSecurityConfigurator} will commit the configuration.
     * @deprecated Use {@link #initializer(SingleAddressInitializer)} and create a {@link SingleAddressInitializer} that
     * invokes {@link SingleAddressHttpClientBuilder#sslConfig(ClientSslConfig)}.
     * @return {@link PartitionHttpClientBuilderConfigurator} to configure security for this client. It is
     * mandatory to call {@link PartitionedHttpClientSecurityConfigurator#commit() commit} after all configuration is
     * done.
     */
    @Deprecated
    public abstract PartitionedHttpClientSecurityConfigurator<U, R> secure();

    /**
     * Sets the maximum amount of {@link ServiceDiscovererEvent} objects that will be queued for each partition.
     * <p>
     * It is assumed that the {@link Subscriber}s will process events in a timely manner (typically synchronously)
     * so this typically doesn't need to be very large.
     *
     * @param serviceDiscoveryMaxQueueSize the maximum amount of {@link ServiceDiscovererEvent} objects that will be
     * queued for each partition.
     * @return {@code this}.
     */
    public abstract PartitionedHttpClientBuilder<U, R> serviceDiscoveryMaxQueueSize(int serviceDiscoveryMaxQueueSize);

    /**
     * Sets {@link PartitionMapFactory} to use by all {@link StreamingHttpClient}s created by this builder.
     *
     * @param partitionMapFactory {@link PartitionMapFactory} to use.
     * @return {@code this}.
     */
    public abstract PartitionedHttpClientBuilder<U, R> partitionMapFactory(PartitionMapFactory partitionMapFactory);

    /**
     * Sets a function that allows customizing the {@link SingleAddressHttpClientBuilder} used to create the client for
     * a given partition based on its {@link PartitionAttributes}.
     * @deprecated Use {@link #initializer(SingleAddressInitializer)}.
     * @param clientFilterFunction {@link BiFunction} used to customize the {@link SingleAddressHttpClientBuilder}
     * before creating the client for the partition
     * @return {@code this}
     */
    @Deprecated
    public abstract PartitionedHttpClientBuilder<U, R> appendClientBuilderFilter(
            PartitionHttpClientBuilderConfigurator<U, R> clientFilterFunction);

    /**
     * Set a function which can customize options for each {@link StreamingHttpClient} that is built.
     * @param initializer Initializes the {@link SingleAddressHttpClientBuilder} used to build new
     * {@link StreamingHttpClient}s.
     * @return {@code this}
     */
    public abstract PartitionedHttpClientBuilder<U, R> initializer(SingleAddressInitializer<U, R> initializer);
}

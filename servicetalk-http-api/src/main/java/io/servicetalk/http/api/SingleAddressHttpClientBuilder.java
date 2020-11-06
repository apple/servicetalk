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
import io.servicetalk.logging.api.LogLevel;
import io.servicetalk.transport.api.IoExecutor;

import java.net.SocketOption;
import java.util.function.BooleanSupplier;
import java.util.List;
import java.util.function.Function;
import java.util.function.Predicate;
import javax.annotation.Nullable;

import static java.util.Arrays.asList;
import static java.util.Collections.unmodifiableList;

/**
 * A builder of {@link StreamingHttpClient} instances which call a single server based on the provided unresolved
 * address.
 * <p>
 * It also provides a good set of default settings and configurations, which could be used by most users as-is or
 * could be overridden to address specific use cases.
 * @param <U> the type of address before resolution (unresolved address)
 * @param <R> the type of address after resolution (resolved address)
 */
public abstract class SingleAddressHttpClientBuilder<U, R>
        extends BaseSingleAddressHttpClientBuilder<U, R, ServiceDiscovererEvent<R>> {

    @Nullable
    protected List<StreamingContentCodec> supportedEncodings;

    @Override
    public abstract SingleAddressHttpClientBuilder<U, R> ioExecutor(IoExecutor ioExecutor);

    @Override
    public abstract SingleAddressHttpClientBuilder<U, R> executionStrategy(HttpExecutionStrategy strategy);

    @Override
    public abstract SingleAddressHttpClientBuilder<U, R> bufferAllocator(BufferAllocator allocator);

    @Override
    public abstract <T> SingleAddressHttpClientBuilder<U, R> socketOption(SocketOption<T> option, T value);

    @Deprecated
    @Override
    public abstract SingleAddressHttpClientBuilder<U, R> enableWireLogging(String loggerName);

    @Override
    public abstract SingleAddressHttpClientBuilder<U, R> enableWireLogging(String loggerName,
                                                                           LogLevel logLevel,
                                                                           BooleanSupplier logUserData);

    @Override
    public abstract SingleAddressHttpClientBuilder<U, R> protocols(HttpProtocolConfig... protocols);

    @Override
    public abstract SingleAddressHttpClientBuilder<U, R> appendConnectionFilter(
            StreamingHttpConnectionFilterFactory factory);

    @Override
    public SingleAddressHttpClientBuilder<U, R> appendConnectionFilter(Predicate<StreamingHttpRequest> predicate,
                                                                       StreamingHttpConnectionFilterFactory factory) {
        return (SingleAddressHttpClientBuilder<U, R>)
                super.appendConnectionFilter(predicate, factory);
    }

    @Override
    public abstract SingleAddressHttpClientBuilder<U, R> appendConnectionFactoryFilter(
            ConnectionFactoryFilter<R, FilterableStreamingHttpConnection> factory);

    @Override
    public abstract SingleAddressHttpClientBuilder<U, R> disableHostHeaderFallback();

    @Override
    public abstract SingleAddressHttpClientBuilder<U, R> autoRetryStrategy(
            AutoRetryStrategyProvider autoRetryStrategyProvider);

    @Override
    public abstract SingleAddressHttpClientBuilder<U, R> serviceDiscoverer(
            ServiceDiscoverer<U, R, ServiceDiscovererEvent<R>> serviceDiscoverer);

    @Override
    public abstract SingleAddressHttpClientBuilder<U, R> retryServiceDiscoveryErrors(
            ServiceDiscoveryRetryStrategy<R, ServiceDiscovererEvent<R>> retryStrategy);

    @Override
    public abstract SingleAddressHttpClientBuilder<U, R> loadBalancerFactory(
            HttpLoadBalancerFactory<R> loadBalancerFactory);

    @Override
    public abstract SingleAddressHttpClientBuilder<U, R> unresolvedAddressToHost(
            Function<U, CharSequence> unresolvedAddressToHostFunction);

    @Override
    public abstract SingleAddressHttpClientBuilder<U, R> appendClientFilter(StreamingHttpClientFilterFactory factory);

    @Override
    public SingleAddressHttpClientBuilder<U, R> supportedEncodings(StreamingContentCodec... codings) {
        this.supportedEncodings = unmodifiableList(asList(codings));
        return this;
    }

    @Override
    public SingleAddressHttpClientBuilder<U, R> appendClientFilter(Predicate<StreamingHttpRequest> predicate,
                                                                   StreamingHttpClientFilterFactory factory) {
        return (SingleAddressHttpClientBuilder<U, R>)
                super.appendClientFilter(predicate, factory);
    }

    /**
     * Terminal filter chain amendments hook.
     * @param currClientFilterFactory Current filter factory to append new filters if needed.
     * @return {@code this}
     */
    @Nullable
    protected StreamingHttpClientFilterFactory terminalFilterAmendment(
            @Nullable final StreamingHttpClientFilterFactory currClientFilterFactory) {
        if (supportedEncodings != null) {
            if (currClientFilterFactory == null) {
                return new ContentCodingHttpClientFilter(supportedEncodings);
            } else {
                return currClientFilterFactory.append(new ContentCodingHttpClientFilter(supportedEncodings));
            }
        }

        return currClientFilterFactory;
    }

    /**
     * Initiates security configuration for this client. Calling
     * {@link SingleAddressHttpClientSecurityConfigurator#commit()} on the returned
     * {@link SingleAddressHttpClientSecurityConfigurator} will commit the configuration.
     *
     * @return {@link SingleAddressHttpClientSecurityConfigurator} to configure security for this client. It is
     * mandatory to call {@link SingleAddressHttpClientSecurityConfigurator#commit() commit} after all configuration is
     * done.
     */
    public abstract SingleAddressHttpClientSecurityConfigurator<U, R> secure();
}

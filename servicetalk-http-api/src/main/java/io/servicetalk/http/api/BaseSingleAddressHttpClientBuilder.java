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
import java.util.function.Function;
import java.util.function.Predicate;

abstract class BaseSingleAddressHttpClientBuilder<U, R, SDE extends ServiceDiscovererEvent<R>>
        extends HttpClientBuilder<U, R, SDE> {

    @Override
    public abstract BaseSingleAddressHttpClientBuilder<U, R, SDE> ioExecutor(IoExecutor ioExecutor);

    @Override
    public abstract BaseSingleAddressHttpClientBuilder<U, R, SDE> executionStrategy(HttpExecutionStrategy strategy);

    @Override
    public abstract BaseSingleAddressHttpClientBuilder<U, R, SDE> bufferAllocator(BufferAllocator allocator);

    @Override
    public abstract <T> BaseSingleAddressHttpClientBuilder<U, R, SDE> socketOption(SocketOption<T> option, T value);

    @Override
    @Deprecated
    public abstract BaseSingleAddressHttpClientBuilder<U, R, SDE> enableWireLogging(String loggerName);

    @Override
    public abstract BaseSingleAddressHttpClientBuilder<U, R, SDE> enableWireLogging(String loggerName,
                                                                                    LogLevel logLevel,
                                                                                    BooleanSupplier logUserData);

    @Override
    public abstract BaseSingleAddressHttpClientBuilder<U, R, SDE> protocols(HttpProtocolConfig... protocols);

    @Override
    public abstract BaseSingleAddressHttpClientBuilder<U, R, SDE> appendConnectionFilter(
            StreamingHttpConnectionFilterFactory factory);

    @Override
    public BaseSingleAddressHttpClientBuilder<U, R, SDE> appendConnectionFilter(
            Predicate<StreamingHttpRequest> predicate,
            StreamingHttpConnectionFilterFactory factory) {
        return (BaseSingleAddressHttpClientBuilder<U, R, SDE>) super.appendConnectionFilter(predicate, factory);
    }

    @Override
    public abstract BaseSingleAddressHttpClientBuilder<U, R, SDE> appendConnectionFactoryFilter(
            ConnectionFactoryFilter<R, FilterableStreamingHttpConnection> factory);

    @Override
    public abstract BaseSingleAddressHttpClientBuilder<U, R, SDE> appendClientFilter(
            StreamingHttpClientFilterFactory factory);

    @Override
    public BaseSingleAddressHttpClientBuilder<U, R, SDE> appendClientFilter(Predicate<StreamingHttpRequest> predicate,
                                                                            StreamingHttpClientFilterFactory factory) {
        return (BaseSingleAddressHttpClientBuilder<U, R, SDE>) super.appendClientFilter(predicate, factory);
    }

    @Override
    public abstract BaseSingleAddressHttpClientBuilder<U, R, SDE> disableHostHeaderFallback();

    @Override
    public abstract BaseSingleAddressHttpClientBuilder<U, R, SDE> allowDropResponseTrailers(boolean allowDrop);

    @Override
    public abstract BaseSingleAddressHttpClientBuilder<U, R, SDE> autoRetryStrategy(
            AutoRetryStrategyProvider autoRetryStrategyProvider);

    @Override
    public abstract BaseSingleAddressHttpClientBuilder<U, R, SDE> serviceDiscoverer(
            ServiceDiscoverer<U, R, SDE> serviceDiscoverer);

    @Override
    public abstract BaseSingleAddressHttpClientBuilder<U, R, SDE> loadBalancerFactory(
            HttpLoadBalancerFactory<R> loadBalancerFactory);

    @Override
    public abstract BaseSingleAddressHttpClientBuilder<U, R, SDE> unresolvedAddressToHost(
            Function<U, CharSequence> unresolvedAddressToHostFunction);
}

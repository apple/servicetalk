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
package io.servicetalk.http.netty;

import io.servicetalk.buffer.BufferAllocator;
import io.servicetalk.client.api.ConnectionFactory;
import io.servicetalk.client.api.LoadBalancer;
import io.servicetalk.client.api.LoadBalancerFactory;
import io.servicetalk.client.api.ServiceDiscoverer;
import io.servicetalk.concurrent.api.Completable;
import io.servicetalk.concurrent.api.Executor;
import io.servicetalk.concurrent.api.Publisher;
import io.servicetalk.concurrent.api.Single;
import io.servicetalk.http.api.HttpClient;
import io.servicetalk.http.api.HttpConnection;
import io.servicetalk.http.api.HttpConnectionBuilder;
import io.servicetalk.http.api.HttpPayloadChunk;
import io.servicetalk.http.api.HttpRequest;
import io.servicetalk.http.api.HttpResponse;
import io.servicetalk.transport.api.ExecutionContext;
import io.servicetalk.transport.api.IoExecutor;

import java.util.function.Function;

import static java.util.Objects.requireNonNull;

final class DefaultHttpClient<ResolvedAddress, EventType extends ServiceDiscoverer.Event<ResolvedAddress>>
        extends HttpClient<HttpPayloadChunk, HttpPayloadChunk> implements ExecutionContext {

    private static final Function<LoadBalancedHttpConnection, LoadBalancedHttpConnection> SELECTOR_FOR_REQUEST =
            conn -> conn.tryRequest() ? conn : null;
    private static final Function<LoadBalancedHttpConnection, LoadBalancedHttpConnection> SELECTOR_FOR_RESERVE =
            conn -> conn.tryReserve() ? conn : null;

    // TODO Proto specific LB after upgrade and worry about SSL
    private final LoadBalancer<LoadBalancedHttpConnection> loadBalancer;
    private final Executor executor;
    private final IoExecutor ioExecutor;
    private final BufferAllocator allocator;

    @SuppressWarnings("unchecked")
    DefaultHttpClient(IoExecutor ioExecutor, Executor executor, BufferAllocator allocator,
                         HttpConnectionBuilder<ResolvedAddress, HttpPayloadChunk, HttpPayloadChunk> connectionBuilder,
                         Publisher<EventType> addressEventStream,
                         Function<HttpConnection<HttpPayloadChunk, HttpPayloadChunk>,
                                 HttpConnection<HttpPayloadChunk, HttpPayloadChunk>> connectionFilter,
                         LoadBalancerFactory<ResolvedAddress, HttpConnection<HttpPayloadChunk, HttpPayloadChunk>>
                                 loadBalancerFactory) {
        this.executor = requireNonNull(executor);
        this.ioExecutor = requireNonNull(ioExecutor);
        this.allocator = requireNonNull(allocator);
        ConnectionFactory<ResolvedAddress, LoadBalancedHttpConnection> connectionFactory =
                connectionBuilder.asConnectionFactory(ioExecutor, executor,
                                connectionFilter.andThen(LoadBalancedHttpConnection::new));

        // TODO addressEventStream.multicast(x) when feeding into multiple LBs
        // TODO we should revisit generics on LoadBalancerFactory to avoid casts
        LoadBalancer<? extends HttpConnection<HttpPayloadChunk, HttpPayloadChunk>> lbfUntypedForCast =
                loadBalancerFactory.newLoadBalancer(addressEventStream, connectionFactory);
        loadBalancer = (LoadBalancer<LoadBalancedHttpConnection>) lbfUntypedForCast;
    }

    @Override
    public Single<? extends ReservedHttpConnection<HttpPayloadChunk, HttpPayloadChunk>> reserveConnection(
            final HttpRequest<HttpPayloadChunk> request) {
        return loadBalancer.selectConnection(SELECTOR_FOR_RESERVE);
    }

    @Override
    public Single<? extends UpgradableHttpResponse<HttpPayloadChunk, HttpPayloadChunk>> upgradeConnection(
            final HttpRequest<HttpPayloadChunk> request) {
        return Single.error(new UnsupportedOperationException("Protocol upgrades not yet implemented"));
    }

    @Override
    public Single<HttpResponse<HttpPayloadChunk>> request(final HttpRequest<HttpPayloadChunk> request) {
        // TODO should we do smart things here, add encoding headers etc. ?
        return loadBalancer.selectConnection(SELECTOR_FOR_REQUEST).flatMap(c -> c.request(request));
    }

    @Override
    public ExecutionContext getExecutionContext() {
        return this;
    }

    @Override
    public Completable onClose() {
        return loadBalancer.onClose();
    }

    @Override
    public Completable closeAsync() {
        return loadBalancer.closeAsync();
    }

    @Override
    public BufferAllocator getBufferAllocator() {
        return allocator;
    }

    @Override
    public IoExecutor getIoExecutor() {
        return ioExecutor;
    }

    @Override
    public Executor getExecutor() {
        return executor;
    }
}

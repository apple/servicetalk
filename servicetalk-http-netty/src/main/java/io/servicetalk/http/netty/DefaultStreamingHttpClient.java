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

import io.servicetalk.client.api.LoadBalancer;
import io.servicetalk.concurrent.api.Completable;
import io.servicetalk.concurrent.api.Single;
import io.servicetalk.http.api.HttpExecutionStrategy;
import io.servicetalk.http.api.StreamingHttpClient;
import io.servicetalk.http.api.StreamingHttpRequest;
import io.servicetalk.http.api.StreamingHttpRequestResponseFactory;
import io.servicetalk.http.api.StreamingHttpResponse;
import io.servicetalk.transport.api.ExecutionContext;

import java.util.function.Function;

import static io.servicetalk.concurrent.api.Single.error;
import static java.util.Objects.requireNonNull;

final class DefaultStreamingHttpClient extends StreamingHttpClient {

    private static final Function<LoadBalancedStreamingHttpConnection, LoadBalancedStreamingHttpConnection>
            SELECTOR_FOR_REQUEST = conn -> conn.tryRequest() ? conn : null;
    private static final Function<LoadBalancedStreamingHttpConnection, LoadBalancedStreamingHttpConnection>
            SELECTOR_FOR_RESERVE = conn -> conn.tryReserve() ? conn : null;

    // TODO Proto specific LB after upgrade and worry about SSL
    private final ExecutionContext executionContext;
    private final HttpExecutionStrategy executionStrategy;
    private final LoadBalancer<LoadBalancedStreamingHttpConnection> loadBalancer;

    @SuppressWarnings("unchecked")
    DefaultStreamingHttpClient(final ExecutionContext executionContext, final HttpExecutionStrategy executionStrategy,
                               final LoadBalancer<LoadBalancedStreamingHttpConnection> loadBalancer,
                               final StreamingHttpRequestResponseFactory reqRespFactory) {
        super(reqRespFactory);
        this.executionContext = requireNonNull(executionContext);
        this.executionStrategy = executionStrategy;
        this.loadBalancer = requireNonNull(loadBalancer);
    }

    @Override
    public Single<? extends ReservedStreamingHttpConnection> reserveConnection(final HttpExecutionStrategy strategy,
                                                                               final StreamingHttpRequest request) {
        return strategy.offloadReceive(executionContext.executor(),
                loadBalancer.selectConnection(SELECTOR_FOR_RESERVE));
    }

    @Override
    public Single<? extends UpgradableStreamingHttpResponse> upgradeConnection(final StreamingHttpRequest request) {
        return error(new UnsupportedOperationException("Protocol upgrades not yet implemented"));
    }

    @Override
    public Single<StreamingHttpResponse> request(final StreamingHttpRequest request) {
        return request(executionStrategy, request);
    }

    @Override
    public Single<StreamingHttpResponse> request(final HttpExecutionStrategy strategy,
                                                 final StreamingHttpRequest request) {
        // We have to do the incrementing/decrementing in the Client instead of LoadBalancedStreamingHttpConnection
        // because it is possible that someone can use the ConnectionFactory exported by this Client before the
        // LoadBalancer takes ownership of it (e.g. connection initialization) and in that case they will not be
        // following the LoadBalancer API which this Client depends upon to ensure the concurrent request count state is
        // correct.
        return loadBalancer.selectConnection(SELECTOR_FOR_REQUEST)
                .flatMap(c -> c.request(strategy, request).liftSynchronous(new ConcurrencyControlSingleOperator(c)));
    }

    @Override
    public ExecutionContext executionContext() {
        return executionContext;
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
    public Completable closeAsyncGracefully() {
        return loadBalancer.closeAsyncGracefully();
    }
}

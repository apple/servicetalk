/*
 * Copyright © 2018, 2021 Apple Inc. and the ServiceTalk project authors
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
import io.servicetalk.concurrent.api.AsyncContext;
import io.servicetalk.concurrent.api.Completable;
import io.servicetalk.concurrent.api.Single;
import io.servicetalk.concurrent.api.TerminalSignalConsumer;
import io.servicetalk.http.api.FilterableStreamingHttpClient;
import io.servicetalk.http.api.HttpExecutionContext;
import io.servicetalk.http.api.HttpExecutionStrategy;
import io.servicetalk.http.api.HttpRequestMetaData;
import io.servicetalk.http.api.HttpRequestMethod;
import io.servicetalk.http.api.ReservedStreamingHttpConnection;
import io.servicetalk.http.api.StreamingHttpRequest;
import io.servicetalk.http.api.StreamingHttpRequestResponseFactory;
import io.servicetalk.http.api.StreamingHttpResponse;
import io.servicetalk.http.api.StreamingHttpResponseFactory;
import io.servicetalk.http.utils.BeforeFinallyHttpOperator;
import io.servicetalk.transport.api.ConnectionInfo;
import io.servicetalk.transport.api.IoThreadFactory;

import java.util.concurrent.atomic.AtomicIntegerFieldUpdater;
import java.util.function.Consumer;
import java.util.function.Predicate;

import static io.servicetalk.client.api.internal.RequestConcurrencyController.Result.Accepted;
import static io.servicetalk.http.netty.AbstractLifecycleObserverHttpFilter.ON_CONNECTION_SELECTED_CONSUMER;
import static java.util.Objects.requireNonNull;
import static java.util.concurrent.atomic.AtomicIntegerFieldUpdater.newUpdater;
import static java.util.function.Function.identity;

final class LoadBalancedStreamingHttpClient implements FilterableStreamingHttpClient {

    private static final Predicate<LoadBalancedStreamingHttpConnection>
            SELECTOR_FOR_REQUEST = conn -> conn.tryRequest() == Accepted;
    private static final Predicate<LoadBalancedStreamingHttpConnection>
            SELECTOR_FOR_RESERVE = LoadBalancedStreamingHttpConnection::tryReserve;

    // TODO Proto specific LB after upgrade and worry about SSL
    private final HttpExecutionContext executionContext;
    private final LoadBalancer<LoadBalancedStreamingHttpConnection> loadBalancer;
    private final StreamingHttpRequestResponseFactory reqRespFactory;

    LoadBalancedStreamingHttpClient(final HttpExecutionContext executionContext,
                                    final LoadBalancer<LoadBalancedStreamingHttpConnection> loadBalancer,
                                    final StreamingHttpRequestResponseFactory reqRespFactory) {
        this.executionContext = requireNonNull(executionContext);
        this.loadBalancer = requireNonNull(loadBalancer);
        this.reqRespFactory = requireNonNull(reqRespFactory);
    }

    @Override
    public Single<StreamingHttpResponse> request(final HttpExecutionStrategy strategy,
                                                 final StreamingHttpRequest request) {
        // We have to do the incrementing/decrementing in the Client instead of LoadBalancedStreamingHttpConnection
        // because it is possible that someone can use the ConnectionFactory exported by this Client before the
        // LoadBalancer takes ownership of it (e.g. connection initialization) and in that case they will not be
        // following the LoadBalancer API which this Client depends upon to ensure the concurrent request count state is
        // correct.
        return loadBalancer.selectConnection(SELECTOR_FOR_REQUEST).flatMap(c -> {
                final Consumer<ConnectionInfo> onConnectionSelected = AsyncContext.get(ON_CONNECTION_SELECTED_CONSUMER);
                if (onConnectionSelected != null) {
                    onConnectionSelected.accept(c.connectionContext());
                }
                final OwnedRunnable ownedRunnable = c.connectionContext().protocol().major() <= 1 ? null :
                        new OwnedRunnable(c::requestFinished);
                return c.request(strategy, ownedRunnable == null ? request :
                                new StreamingHttpRequestWithContext(request, ownedRunnable))
                        .liftSync(new BeforeFinallyHttpOperator(new TerminalSignalConsumer() {
                            @Override
                            public void onComplete() {
                                if (ownedRunnable == null || ownedRunnable.own()) {
                                    c.requestFinished();
                                }
                            }

                            @Override
                            public void onError(final Throwable throwable) {
                                if (ownedRunnable == null || ownedRunnable.own()) {
                                    c.requestFinished();
                                }
                            }

                            @Override
                            public void cancel() {
                                // If the request gets cancelled, we pessimistically assume that the transport will
                                // close the connection since the Subscriber did not read the entire response and
                                // cancelled. This reduces the time window during which a connection is eligible for
                                // selection by the load balancer post cancel and the connection being closed by the
                                // transport.
                                // Transport MAY not close the connection if cancel raced with completion and completion
                                // was seen by the transport before cancel. We have no way of knowing at this layer
                                // if this indeed happen.
                                //
                                // For H2 and above, connection are multiplexed and use virtual streams for each
                                // request-response exchange. Because we don't have access to the stream at this level
                                // we cannot close it. Instead, we use a Runnable which will be registered for the
                                // stream and executed when it closes. However, cancellation can happen before transport
                                // created a stream. We check the ownership of the Runnable and if it was not owned by
                                // the transport, we mark request as finished immediately.
                                if (ownedRunnable == null) {
                                    c.closeAsync().subscribe();
                                } else if (ownedRunnable.own()) {
                                    c.requestFinished();
                                }
                            }
                        }))
                        // subscribeShareContext is used because otherwise the AsyncContext modified during response
                        // meta data processing will not be visible during processing of the response payload for
                        // ConnectionFilters (it already is visible on ClientFilters).
                        .subscribeShareContext();
            });
    }

    @Override
    public Single<ReservedStreamingHttpConnection> reserveConnection(final HttpExecutionStrategy strategy,
                                                                     final HttpRequestMetaData metaData) {
        Single<ReservedStreamingHttpConnection> connection =
                loadBalancer.selectConnection(SELECTOR_FOR_RESERVE).map(identity());
        return strategy.isMetadataReceiveOffloaded() || strategy.isDataReceiveOffloaded() ?
                connection.publishOn(executionContext.executor(), IoThreadFactory.IoThread::currentThreadIsIoThread) :
                connection;
    }

    @Override
    public HttpExecutionContext executionContext() {
        return executionContext;
    }

    @Override
    public StreamingHttpResponseFactory httpResponseFactory() {
        return reqRespFactory;
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

    @Override
    public StreamingHttpRequest newRequest(final HttpRequestMethod method, final String requestTarget) {
        return reqRespFactory.newRequest(method, requestTarget);
    }

    static final class OwnedRunnable implements Runnable {
        private static final AtomicIntegerFieldUpdater<OwnedRunnable> ownedUpdater =
                newUpdater(OwnedRunnable.class, "owned");
        @SuppressWarnings("unused")
        private volatile int owned;

        private final Runnable runnable;

        OwnedRunnable(final Runnable runnable) {
            this.runnable = runnable;
        }

        @Override
        public void run() {
            runnable.run();
        }

        boolean own() {
            return ownedUpdater.compareAndSet(this, 0, 1);
        }
    }
}

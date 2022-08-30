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
import io.servicetalk.concurrent.api.Completable;
import io.servicetalk.concurrent.api.Single;
import io.servicetalk.concurrent.api.TerminalSignalConsumer;
import io.servicetalk.context.api.ContextMap.Key;
import io.servicetalk.http.api.FilterableStreamingHttpClient;
import io.servicetalk.http.api.FilterableStreamingHttpLoadBalancedConnection;
import io.servicetalk.http.api.HttpConnectionContext;
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
import io.servicetalk.transport.api.IoThreadFactory.IoThread;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.atomic.AtomicIntegerFieldUpdater;
import java.util.function.Consumer;
import java.util.function.Predicate;

import static io.servicetalk.client.api.RequestConcurrencyController.Result.Accepted;
import static io.servicetalk.context.api.ContextMap.Key.newKey;
import static io.servicetalk.http.netty.AbstractLifecycleObserverHttpFilter.ON_CONNECTION_SELECTED_CONSUMER;
import static io.servicetalk.http.netty.AbstractStreamingHttpConnection.requestExecutionStrategy;
import static io.servicetalk.http.netty.LoadBalancedStreamingHttpClient.OnStreamClosedRunnable.areStreamsSupported;
import static java.util.Objects.requireNonNull;
import static java.util.concurrent.atomic.AtomicIntegerFieldUpdater.newUpdater;
import static java.util.function.Function.identity;

final class LoadBalancedStreamingHttpClient implements FilterableStreamingHttpClient {

    private static final Logger LOGGER = LoggerFactory.getLogger(LoadBalancedStreamingHttpClient.class);

    private static final Predicate<FilterableStreamingHttpLoadBalancedConnection>
            SELECTOR_FOR_REQUEST = conn -> conn.tryRequest() == Accepted;
    private static final Predicate<FilterableStreamingHttpLoadBalancedConnection>
            SELECTOR_FOR_RESERVE = FilterableStreamingHttpLoadBalancedConnection::tryReserve;

    private static boolean onStreamClosedWarningLogged;

    // TODO Proto specific LB after upgrade and worry about SSL
    private final HttpExecutionContext executionContext;
    private final LoadBalancer<FilterableStreamingHttpLoadBalancedConnection> loadBalancer;
    private final StreamingHttpRequestResponseFactory reqRespFactory;

    LoadBalancedStreamingHttpClient(final HttpExecutionContext executionContext,
                                    final LoadBalancer<FilterableStreamingHttpLoadBalancedConnection> loadBalancer,
                                    final StreamingHttpRequestResponseFactory reqRespFactory) {
        this.executionContext = requireNonNull(executionContext);
        this.loadBalancer = requireNonNull(loadBalancer);
        this.reqRespFactory = requireNonNull(reqRespFactory);
    }

    @Override
    public Single<StreamingHttpResponse> request(final StreamingHttpRequest request) {
        // We have to do the incrementing/decrementing in the Client instead of LoadBalancedStreamingHttpConnection
        // because it is possible that someone can use the ConnectionFactory exported by this Client before the
        // LoadBalancer takes ownership of it (e.g. connection initialization) and in that case they will not be
        // following the LoadBalancer API which this Client depends upon to ensure the concurrent request count state is
        // correct.
        return loadBalancer.selectConnection(SELECTOR_FOR_REQUEST, request.context()).flatMap(c -> {
            notifyConnectionSelected(request, c);
            final OnStreamClosedRunnable onStreamClosed = areStreamsSupported(c.connectionContext()) ?
                        new OnStreamClosedRunnable(c::requestFinished) : null;
                if (onStreamClosed != null) {
                    request.context().put(OnStreamClosedRunnable.KEY, onStreamClosed);
                }
                return c.request(request)
                        .liftSync(new BeforeFinallyHttpOperator(new TerminalSignalConsumer() {
                            // Still check ownership of the `onStreamClosed` inside all terminal events to mitigate
                            // scenarios when users didn't let it propagate down to HTTP/2 layer (cleared the request
                            // context or incorrectly wrapped the request).
                            @Override
                            public void onComplete() {
                                if (onStreamClosed == null || onStreamClosed.own()) {
                                    c.requestFinished();
                                }
                            }

                            @Override
                            public void onError(final Throwable throwable) {
                                if (onStreamClosed == null || onStreamClosed.own()) {
                                    c.requestFinished();
                                }
                            }

                            @Override
                            @SuppressWarnings("AssignmentToStaticFieldFromInstanceMethod")
                            public void cancel() {
                                // For HTTP/1.x cancellation is handled in AbstractStreamingHttpConnection.
                                // For HTTP/2 cancellation is handled by OnStreamClosedRunnable owned by the actual
                                // Stream. To avoid leaking the resource in case users wiped/modified the
                                // request.context() before OnStreamClosedRunnable was propagated to the
                                // H2ClientParentConnectionContext, we double-check ownership here and mark the request
                                // as "finished". In normal circumstances, ownership will be taken by
                                // H2ClientParentConnectionContext prior propagation of the Cancellable down.
                                if (onStreamClosed != null && onStreamClosed.own()) {
                                    if (!onStreamClosedWarningLogged) {
                                        onStreamClosedWarningLogged = true;
                                        LOGGER.warn("HttpRequestMetaData#context() was cleared by one of the " +
                                                "user-defined connection filters. This may result in incorrect " +
                                                "control of the maximum concurrent streams. Double-check that none " +
                                                "of the custom filters clear the request.context() or contact " +
                                                "support for assistance.");
                                    }
                                    c.requestFinished();
                                }
                            }
                        }))
                        // shareContextOnSubscribe is used because otherwise the AsyncContext modified during response
                        // meta data processing will not be visible during processing of the response payload for
                        // ConnectionFilters (it already is visible on ClientFilters).
                        .shareContextOnSubscribe();
            });
    }

    private static void notifyConnectionSelected(final HttpRequestMetaData requestMetaData,
                                                 final FilterableStreamingHttpLoadBalancedConnection c) {
        // Do not remove ON_CONNECTION_SELECTED_CONSUMER from the context to let it observe new connection selections
        // for retries and redirects.
        final Consumer<ConnectionInfo> onConnectionSelected = requestMetaData.context()
                .get(ON_CONNECTION_SELECTED_CONSUMER);
        if (onConnectionSelected != null) {
            onConnectionSelected.accept(c.connectionContext());
        }
    }

    @Override
    public Single<ReservedStreamingHttpConnection> reserveConnection(final HttpRequestMetaData metaData) {
        return Single.defer(() -> {
            Single<ReservedStreamingHttpConnection> connection =
                    loadBalancer.selectConnection(SELECTOR_FOR_RESERVE, metaData.context()).map(identity());
            final HttpExecutionStrategy strategy = requestExecutionStrategy(metaData,
                    executionContext().executionStrategy());
            return (strategy.isMetadataReceiveOffloaded() || strategy.isDataReceiveOffloaded() ?
                    connection.publishOn(executionContext.executor(), IoThread::currentThreadIsIoThread) : connection)
                    .shareContextOnSubscribe();
        });
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

    /**
     * Special {@link Runnable} to correctly handle cancellation of HTTP/2 streams without closing the entire TCP
     * connection.
     */
    static final class OnStreamClosedRunnable implements Runnable {

        static final Key<OnStreamClosedRunnable> KEY = newKey(OnStreamClosedRunnable.class.getName(),
                OnStreamClosedRunnable.class);

        private static final AtomicIntegerFieldUpdater<OnStreamClosedRunnable> ownedUpdater =
                newUpdater(OnStreamClosedRunnable.class, "owned");
        @SuppressWarnings("unused")
        private volatile int owned;

        private final Runnable runnable;

        OnStreamClosedRunnable(final Runnable runnable) {
            this.runnable = runnable;
        }

        @Override
        public void run() {
            runnable.run();
        }

        /**
         * Owns execution of this {@link Runnable}.
         *
         * @return {@code true} if the caller is allowed to invoke {@link #run()} method, {@code false} otherwise
         */
        boolean own() {
            return ownedUpdater.compareAndSet(this, 0, 1);
        }

        /**
         * Verified if the current connection supports streams or not.
         *
         * @param ctx {@link HttpExecutionContext} to verify
         * @return {@code true} if the current connection supports streams, {@code false} otherwise
         */
        static boolean areStreamsSupported(final HttpConnectionContext ctx) {
            return ctx.protocol().major() >= 2;
        }
    }
}

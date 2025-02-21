/*
 * Copyright © 2019 Apple Inc. and the ServiceTalk project authors
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

import io.servicetalk.concurrent.api.Completable;
import io.servicetalk.concurrent.api.Publisher;
import io.servicetalk.concurrent.api.Single;
import io.servicetalk.concurrent.internal.RejectedSubscribeException;
import io.servicetalk.context.api.ContextMap;
import io.servicetalk.http.api.BlockingHttpClient;
import io.servicetalk.http.api.BlockingStreamingHttpClient;
import io.servicetalk.http.api.FilterableStreamingHttpClient;
import io.servicetalk.http.api.HttpClient;
import io.servicetalk.http.api.HttpConnectionContext;
import io.servicetalk.http.api.HttpEventKey;
import io.servicetalk.http.api.HttpExecutionContext;
import io.servicetalk.http.api.HttpExecutionStrategy;
import io.servicetalk.http.api.HttpRequestMetaData;
import io.servicetalk.http.api.HttpRequestMethod;
import io.servicetalk.http.api.ReservedBlockingHttpConnection;
import io.servicetalk.http.api.ReservedBlockingStreamingHttpConnection;
import io.servicetalk.http.api.ReservedHttpConnection;
import io.servicetalk.http.api.ReservedStreamingHttpConnection;
import io.servicetalk.http.api.StreamingHttpClient;
import io.servicetalk.http.api.StreamingHttpRequest;
import io.servicetalk.http.api.StreamingHttpRequester;
import io.servicetalk.http.api.StreamingHttpResponse;
import io.servicetalk.http.api.StreamingHttpResponseFactory;

import static io.servicetalk.context.api.ContextMap.Key.newKey;
import static io.servicetalk.http.api.HttpApiConversions.toBlockingClient;
import static io.servicetalk.http.api.HttpApiConversions.toBlockingStreamingClient;
import static io.servicetalk.http.api.HttpApiConversions.toClient;
import static io.servicetalk.http.api.HttpApiConversions.toReservedBlockingConnection;
import static io.servicetalk.http.api.HttpApiConversions.toReservedBlockingStreamingConnection;
import static io.servicetalk.http.api.HttpApiConversions.toReservedConnection;
import static io.servicetalk.http.api.HttpContextKeys.HTTP_EXECUTION_STRATEGY_KEY;
import static java.lang.Boolean.getBoolean;

final class FilterableClientToClient implements StreamingHttpClient {

    // FIXME: 0.43 - remove this temporary system property
    private static final boolean SKIP_CONCURRENT_REQUEST_CHECK =
            getBoolean("io.servicetalk.http.netty.skipConcurrentRequestCheck");
    private static final ContextMap.Key<Object> HTTP_IN_FLIGHT_REQUEST =
            newKey("HTTP_IN_FLIGHT_REQUEST", Object.class);

    private final Object lock = new Object();
    private final FilterableStreamingHttpClient client;
    private final HttpExecutionContext executionContext;

    FilterableClientToClient(FilterableStreamingHttpClient filteredClient, HttpExecutionContext executionContext) {
        client = filteredClient;
        this.executionContext = executionContext;
    }

    @Override
    public HttpClient asClient() {
        return toClient(this, executionContext.executionStrategy());
    }

    @Override
    public BlockingStreamingHttpClient asBlockingStreamingClient() {
        return toBlockingStreamingClient(this, executionContext.executionStrategy());
    }

    @Override
    public BlockingHttpClient asBlockingClient() {
        return toBlockingClient(this, executionContext.executionStrategy());
    }

    @Override
    public Single<StreamingHttpResponse> request(final StreamingHttpRequest request) {
        return executeRequest(client, request, executionContext().executionStrategy(), lock);
    }

    @Override
    public Single<ReservedStreamingHttpConnection> reserveConnection(final HttpRequestMetaData metaData) {
        return Single.defer(() -> {
            HttpExecutionStrategy clientstrategy = executionContext().executionStrategy();
            setExecutionStrategy(metaData.context(), clientstrategy);
            return client.reserveConnection(metaData).map(rc -> new ReservedStreamingHttpConnection() {
                @Override
                public ReservedHttpConnection asConnection() {
                    return toReservedConnection(this, clientstrategy);
                }

                @Override
                public ReservedBlockingStreamingHttpConnection asBlockingStreamingConnection() {
                    return toReservedBlockingStreamingConnection(this, clientstrategy);
                }

                @Override
                public ReservedBlockingHttpConnection asBlockingConnection() {
                    return toReservedBlockingConnection(this, clientstrategy);
                }

                @Override
                public Completable releaseAsync() {
                    return rc.releaseAsync();
                }

                @Override
                public Single<StreamingHttpResponse> request(final StreamingHttpRequest request) {
                    // Use the strategy from the client as the underlying ReservedStreamingHttpConnection may be user
                    // created and hence could have an incorrect default strategy. Doing this makes sure we never call
                    // the method without strategy just as we do for the regular connection.
                    return executeRequest(rc, request, clientstrategy, lock);
                }

                @Override
                public HttpConnectionContext connectionContext() {
                    return rc.connectionContext();
                }

                @Override
                public <T> Publisher<? extends T> transportEventStream(final HttpEventKey<T> eventKey) {
                    return rc.transportEventStream(eventKey);
                }

                @Override
                public HttpExecutionContext executionContext() {
                    return rc.executionContext();
                }

                @Override
                public StreamingHttpResponseFactory httpResponseFactory() {
                    return rc.httpResponseFactory();
                }

                @Override
                public Completable onClose() {
                    return rc.onClose();
                }

                @Override
                public Completable onClosing() {
                    return rc.onClosing();
                }

                @Override
                public Completable closeAsync() {
                    return rc.closeAsync();
                }

                @Override
                public Completable closeAsyncGracefully() {
                    return rc.closeAsyncGracefully();
                }

                @Override
                public StreamingHttpRequest newRequest(final HttpRequestMethod method, final String requestTarget) {
                    return rc.newRequest(method, requestTarget);
                }
            }).shareContextOnSubscribe();
        });
    }

    @Override
    public HttpExecutionContext executionContext() {
        return executionContext;
    }

    @Override
    public StreamingHttpResponseFactory httpResponseFactory() {
        return client.httpResponseFactory();
    }

    @Override
    public Completable onClose() {
        return client.onClose();
    }

    @Override
    public Completable onClosing() {
        return client.onClosing();
    }

    @Override
    public Completable closeAsync() {
        return client.closeAsync();
    }

    @Override
    public Completable closeAsyncGracefully() {
        return client.closeAsyncGracefully();
    }

    @Override
    public StreamingHttpRequest newRequest(final HttpRequestMethod method, final String requestTarget) {
        return client.newRequest(method, requestTarget);
    }

    private static Single<StreamingHttpResponse> executeRequest(final StreamingHttpRequester requester,
                                                                final StreamingHttpRequest request,
                                                                final HttpExecutionStrategy strategy,
                                                                final Object lock) {
        return Single.defer(() -> {
            final ContextMap context = request.context();

            if (SKIP_CONCURRENT_REQUEST_CHECK) {
                return executeRequest(requester, request, context, strategy).shareContextOnSubscribe();
            }

            // Prevent concurrent execution of the same request through the same layer.
            // In general, we do not expect users to execute the same mutable request concurrently. Therefore, the cost
            // of synchronized block should be negligible for most requests, unless they messed up with reactive streams
            // chain and accidentally subscribed to the same request concurrently. This protection helps them avoid
            // ambiguous runtime behavior caused by a corrupted mutable request state.
            final Object inFlight;
            synchronized (context) {
                // We do not override lock because other layers may already set their own one.
                inFlight = context.putIfAbsent(HTTP_IN_FLIGHT_REQUEST, lock);
            }
            if (lock.equals(inFlight)) {
                return Single.<StreamingHttpResponse>failed(new RejectedSubscribeException(
                        "Concurrent execution is detected for the same mutable request. Only a single execution is " +
                                "allowed at any point of time. Otherwise, request data structures can be corrupted. " +
                                "To avoid this error, create a new request for every execution or wrap every request " +
                                "creation with Single.defer() operator."))
                        .shareContextOnSubscribe();
            }

            Single<StreamingHttpResponse> response = executeRequest(requester, request, context, strategy);
            if (inFlight == null) {
                // Remove only if we are the one who set the lock.
                response = response.beforeFinally(() -> {
                    synchronized (context) {
                        context.remove(HTTP_IN_FLIGHT_REQUEST);
                    }
                });
            }
            return response.shareContextOnSubscribe();
        });
    }

    private static Single<StreamingHttpResponse> executeRequest(final StreamingHttpRequester requester,
                                                                final StreamingHttpRequest request,
                                                                final ContextMap context,
                                                                final HttpExecutionStrategy strategy) {
        setExecutionStrategy(context, strategy);
        return requester.request(request);
    }

    private static void setExecutionStrategy(final ContextMap context, final HttpExecutionStrategy strategy) {
        // We do not override HttpExecutionStrategy because users may prefer to use their own.
        context.putIfAbsent(HTTP_EXECUTION_STRATEGY_KEY, strategy);
    }
}

/*
 * Copyright © 2018-2019, 2021 Apple Inc. and the ServiceTalk project authors
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

import io.servicetalk.client.api.ConsumableEvent;
import io.servicetalk.client.api.internal.IgnoreConsumedEvent;
import io.servicetalk.concurrent.api.Completable;
import io.servicetalk.concurrent.api.Publisher;
import io.servicetalk.concurrent.api.Single;
import io.servicetalk.http.api.ClientInvoker;
import io.servicetalk.http.api.FilterableStreamingHttpConnection;
import io.servicetalk.http.api.HttpConnectionContext;
import io.servicetalk.http.api.HttpEventKey;
import io.servicetalk.http.api.HttpExecutionContext;
import io.servicetalk.http.api.HttpExecutionStrategy;
import io.servicetalk.http.api.HttpHeadersFactory;
import io.servicetalk.http.api.HttpMetaData;
import io.servicetalk.http.api.HttpRequestMethod;
import io.servicetalk.http.api.HttpResponseMetaData;
import io.servicetalk.http.api.StreamingHttpRequest;
import io.servicetalk.http.api.StreamingHttpRequestResponseFactory;
import io.servicetalk.http.api.StreamingHttpResponse;
import io.servicetalk.http.api.StreamingHttpResponseFactory;
import io.servicetalk.transport.netty.internal.FlushStrategy;
import io.servicetalk.transport.netty.internal.NettyConnectionContext;

import javax.annotation.Nullable;

import static io.servicetalk.concurrent.api.Publisher.failed;
import static io.servicetalk.concurrent.api.Publisher.from;
import static io.servicetalk.concurrent.api.Single.defer;
import static io.servicetalk.concurrent.api.Single.succeeded;
import static io.servicetalk.http.api.HttpApiConversions.isPayloadEmpty;
import static io.servicetalk.http.api.HttpApiConversions.isSafeToAggregate;
import static io.servicetalk.http.api.StreamingHttpResponses.newTransportResponse;
import static io.servicetalk.http.netty.HeaderUtils.addRequestTransferEncodingIfNecessary;
import static io.servicetalk.http.netty.HeaderUtils.canAddRequestContentLength;
import static io.servicetalk.http.netty.HeaderUtils.setRequestContentLength;
import static io.servicetalk.transport.netty.internal.FlushStrategies.flushOnEnd;
import static java.util.Objects.requireNonNull;

abstract class AbstractStreamingHttpConnection<CC extends NettyConnectionContext>
        implements FilterableStreamingHttpConnection, ClientInvoker<FlushStrategy> {

    private static final IgnoreConsumedEvent<Integer> ZERO_MAX_CONCURRECNY_EVENT = new IgnoreConsumedEvent<>(0);

    final CC connection;
    private final HttpConnectionContext connectionContext;
    private final HttpExecutionContext executionContext;
    private final Publisher<? extends ConsumableEvent<Integer>> maxConcurrencySetting;
    private final StreamingHttpRequestResponseFactory reqRespFactory;
    private final HttpHeadersFactory headersFactory;
    private final boolean allowDropTrailersReadFromTransport;

    AbstractStreamingHttpConnection(final CC conn, final int maxPipelinedRequests,
                                    final HttpExecutionContext executionContext,
                                    final StreamingHttpRequestResponseFactory reqRespFactory,
                                    final HttpHeadersFactory headersFactory,
                                    final boolean allowDropTrailersReadFromTransport) {
        this.connection = requireNonNull(conn);
        this.connectionContext = new DefaultNettyHttpConnectionContext(conn, executionContext);
        this.executionContext = requireNonNull(executionContext);
        this.reqRespFactory = requireNonNull(reqRespFactory);
        maxConcurrencySetting = from(new IgnoreConsumedEvent<>(maxPipelinedRequests))
                .concat(connection.onClosing().publishOn(executionContext.executor()))
                .concat(succeeded(ZERO_MAX_CONCURRECNY_EVENT));
        this.headersFactory = headersFactory;
        this.allowDropTrailersReadFromTransport = allowDropTrailersReadFromTransport;
    }

    @Override
    public final HttpConnectionContext connectionContext() {
        return connectionContext;
    }

    @SuppressWarnings("unchecked")
    @Override
    public final <T> Publisher<? extends T> transportEventStream(final HttpEventKey<T> eventKey) {
        return eventKey == HttpEventKey.MAX_CONCURRENCY ? (Publisher<? extends T>) maxConcurrencySetting :
                failed(new IllegalArgumentException("Unknown key: " + eventKey));
    }

    @Override
    public final Single<StreamingHttpResponse> invokeClient(final Publisher<Object> flattenedRequest,
                                                            @Nullable final FlushStrategy flushStrategy) {
        return writeAndRead(flattenedRequest, flushStrategy).liftSyncToSingle(new SpliceFlatStreamToMetaSingle<>(
                this::newSplicedResponse));
    }

    @Override
    public Single<StreamingHttpResponse> request(final HttpExecutionStrategy strategy,
                                                 final StreamingHttpRequest request) {
        return defer(() -> {
            final Publisher<Object> flatRequest;
            // See https://tools.ietf.org/html/rfc7230#section-3.3.3
            if (canAddRequestContentLength(request)) {
                flatRequest = setRequestContentLength(request);
            } else {
                flatRequest = Single.<Object>succeeded(request).concat(request.messageBody(), true)
                        .scanWith(HeaderUtils::insertTrailersMapper);
                addRequestTransferEncodingIfNecessary(request);
            }
            return strategy.invokeClient(executionContext.executor(), flatRequest,
                    determineFlushStrategyForApi(request), this).subscribeShareContext();
        });
    }

    @Nullable
    static FlushStrategy determineFlushStrategyForApi(final HttpMetaData request) {
        // For non-aggregated, don't change the flush strategy, keep the default.
        return isPayloadEmpty(request) || isSafeToAggregate(request) ? flushOnEnd() : null;
    }

    @Override
    public final HttpExecutionContext executionContext() {
        return executionContext;
    }

    protected abstract Publisher<Object> writeAndRead(Publisher<Object> stream,
                                                      @Nullable FlushStrategy flushStrategy);

    private StreamingHttpResponse newSplicedResponse(HttpResponseMetaData meta, Publisher<Object> pub) {
        return newTransportResponse(meta.status(), meta.version(), meta.headers(),
                executionContext.bufferAllocator(), pub, allowDropTrailersReadFromTransport, headersFactory);
    }

    @Override
    public final StreamingHttpRequest newRequest(HttpRequestMethod method, String requestTarget) {
        return reqRespFactory.newRequest(method, requestTarget);
    }

    @Override
    public final StreamingHttpResponseFactory httpResponseFactory() {
        return reqRespFactory;
    }

    @Override
    public final Completable onClose() {
        return connectionContext.onClose();
    }

    @Override
    public final Completable closeAsync() {
        return connectionContext.closeAsync();
    }

    @Override
    public final Completable closeAsyncGracefully() {
        return connectionContext.closeAsyncGracefully();
    }

    @Override
    public String toString() {
        return getClass().getName() + '(' + connectionContext + ')';
    }
}

/*
 * Copyright © 2018-2019, 2021-2022 Apple Inc. and the ServiceTalk project authors
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
import io.servicetalk.concurrent.api.Completable;
import io.servicetalk.concurrent.api.Publisher;
import io.servicetalk.concurrent.api.Single;
import io.servicetalk.concurrent.api.TerminalSignalConsumer;
import io.servicetalk.http.api.FilterableStreamingHttpConnection;
import io.servicetalk.http.api.HttpConnectionContext;
import io.servicetalk.http.api.HttpEventKey;
import io.servicetalk.http.api.HttpExecutionContext;
import io.servicetalk.http.api.HttpExecutionStrategy;
import io.servicetalk.http.api.HttpHeadersFactory;
import io.servicetalk.http.api.HttpMetaData;
import io.servicetalk.http.api.HttpRequestMetaData;
import io.servicetalk.http.api.HttpRequestMethod;
import io.servicetalk.http.api.HttpResponseMetaData;
import io.servicetalk.http.api.StreamingHttpRequest;
import io.servicetalk.http.api.StreamingHttpRequestResponseFactory;
import io.servicetalk.http.api.StreamingHttpResponse;
import io.servicetalk.http.api.StreamingHttpResponseFactory;
import io.servicetalk.http.netty.ReservableRequestConcurrencyControllers.IgnoreConsumedEvent;
import io.servicetalk.transport.api.IoThreadFactory;
import io.servicetalk.transport.netty.internal.FlushStrategy;
import io.servicetalk.transport.netty.internal.NettyConnectionContext;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;

import static io.servicetalk.concurrent.api.Executors.immediate;
import static io.servicetalk.concurrent.api.Publisher.failed;
import static io.servicetalk.concurrent.api.Publisher.from;
import static io.servicetalk.concurrent.api.Single.defer;
import static io.servicetalk.concurrent.api.Single.succeeded;
import static io.servicetalk.http.api.HttpApiConversions.isPayloadEmpty;
import static io.servicetalk.http.api.HttpApiConversions.isSafeToAggregate;
import static io.servicetalk.http.api.HttpContextKeys.HTTP_EXECUTION_STRATEGY_KEY;
import static io.servicetalk.http.api.HttpEventKey.MAX_CONCURRENCY;
import static io.servicetalk.http.api.HttpEventKey.newKey;
import static io.servicetalk.http.api.StreamingHttpResponses.newTransportResponse;
import static io.servicetalk.http.netty.HeaderUtils.REQ_EXPECT_CONTINUE;
import static io.servicetalk.http.netty.HeaderUtils.addRequestTransferEncodingIfNecessary;
import static io.servicetalk.http.netty.HeaderUtils.canAddRequestContentLength;
import static io.servicetalk.http.netty.HeaderUtils.emptyMessageBody;
import static io.servicetalk.http.netty.HeaderUtils.flatEmptyMessage;
import static io.servicetalk.http.netty.HeaderUtils.setRequestContentLength;
import static io.servicetalk.http.netty.HeaderUtils.shouldAppendTrailers;
import static io.servicetalk.transport.netty.internal.FlushStrategies.flushOnEnd;
import static java.util.Objects.requireNonNull;

abstract class AbstractStreamingHttpConnection<CC extends NettyConnectionContext>
        implements FilterableStreamingHttpConnection {

    private static final Logger LOGGER = LoggerFactory.getLogger(AbstractStreamingHttpConnection.class);
    static final IgnoreConsumedEvent<Integer> ZERO_MAX_CONCURRENCY_EVENT = new IgnoreConsumedEvent<>(0);
    static final HttpEventKey<ConsumableEvent<Integer>> MAX_CONCURRENCY_NO_OFFLOADING =
            newKey("max-concurrency-no-offloading");

    final CC connection;
    private final HttpConnectionContext connectionContext;
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
        this.reqRespFactory = requireNonNull(reqRespFactory);
        maxConcurrencySetting = from(new IgnoreConsumedEvent<>(maxPipelinedRequests))
                .concat(connection.onClosing())
                .concat(succeeded(ZERO_MAX_CONCURRENCY_EVENT))
                .multicast(1); // Allows multiple Subscribers to consume the event stream.
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
        if (eventKey == MAX_CONCURRENCY_NO_OFFLOADING) {
            return (Publisher<? extends T>) maxConcurrencySetting;
        } else if (eventKey == MAX_CONCURRENCY) {
            return (Publisher<? extends T>) maxConcurrencySetting
                    .publishOn(executionContext().executionStrategy().isEventOffloaded() ?
                                    executionContext().executor() : immediate(),
                            IoThreadFactory.IoThread::currentThreadIsIoThread);
        } else {
            return failed(new IllegalArgumentException("Unknown key: " + eventKey));
        }
    }

    private Single<StreamingHttpResponse> makeRequest(final HttpRequestMetaData requestMetaData,
                                                      final Publisher<Object> flattenedRequest,
                                                      @Nullable final FlushStrategy flushStrategy) {
        return writeAndRead(flattenedRequest, flushStrategy)
                // Handle cancellation for LoadBalancedStreamingHttpClient. We do it here for several reasons:
                //  1. Intercepting cancel next to the transport layer (after all user-defined filters and internal HTTP
                //     logic) helps to capture all possible sources of cancellation.
                //  2. Intercepting cancel on the caller thread before jumping to the event-loop thread helps to notify
                //     concurrency controller that the channel is going to close before potentially delivering a
                //     terminal event back to the response Subscriber (e.g. TimeoutHttpRequesterFilter emits "onError"
                //     right after propagating "cancel").
                //  3. Doing it before SpliceFlatStreamToMetaSingle helps to avoid the need for
                //     BeforeFinallyHttpOperator.
                //  4. Doing it before offloading of terminal signals helps to reduce the risk of closing a connection
                //     after response terminates.
                // We use beforeFinally instead of beforeCancel to avoid closing connection after response terminates.
                .beforeFinally(new TerminalSignalConsumer() {
                    @Override
                    public void onComplete() {
                        // noop
                    }

                    @Override
                    public void onError(final Throwable throwable) {
                        // noop
                    }

                    @Override
                    public void cancel() {
                        // If the HTTP/1.X request gets cancelled before termination, we pessimistically assume that the
                        // transport will close the connection since the Subscriber did not read the entire response and
                        // cancelled. This reduces the time window during which a connection is eligible for selection
                        // by the load balancer post cancel and the connection being closed by the transport.
                        // Transport MAY not close the connection if cancel raced with completion and completion was
                        // seen by the transport before cancel. We have no way of knowing at this layer if this indeed
                        // happen. Therefore, we close the connection manually to guarantee closure.
                        //
                        // For H2 and above, connection are multiplexed and use virtual streams for each
                        // request-response exchange. At the time users own a Cancellable, the stream already owns
                        // OnStreamClosedRunnable in H2ClientParentConnectionContext. It will update the concurrency
                        // controller state if cancellation results in stream closure instead of completion.
                        if (connectionContext().protocol().major() < 2) {
                            LOGGER.debug("{} {} request was cancelled before receiving the full response, " +
                                            "closing this {} connection to stop receiving more data",
                                    connectionContext, requestMetaData, connectionContext.protocol());
                            closeAsync().subscribe();
                        }
                    }
                })
                .liftSyncToSingle(new SpliceFlatStreamToMetaSingle<>(this::newSplicedResponse));
    }

    @Override
    public Single<StreamingHttpResponse> request(final StreamingHttpRequest request) {
        return defer(() -> {
            Publisher<Object> flatRequest;
            // See https://tools.ietf.org/html/rfc7230#section-3.3.3
            if (canAddRequestContentLength(request)) {
                flatRequest = setRequestContentLength(connectionContext().protocol(), request);
            } else {
                final Publisher<Object> messageBody = request.messageBody();
                // Do not propagate cancel to the messageBody if cancel arrives before meta-data completes. Client-side
                // state machine does not depend on termination of the messageBody until after transport subscribes to
                // it. It's preferable to avoid subscribe to the messageBody in case of cancellation to allow requests
                // with non-replayable messageBody to retry.
                if (emptyMessageBody(request, messageBody)) {
                    flatRequest = flatEmptyMessage(connectionContext().protocol(), request, messageBody,
                            /* propagateCancel */ false);
                } else {
                    // Defer subscribe to the messageBody until transport requests it to allow clients retry failed
                    // requests with non-replayable messageBody
                    flatRequest = Single.<Object>succeeded(request).concatDeferSubscribe(messageBody);
                    if (shouldAppendTrailers(connectionContext().protocol(), request)) {
                        flatRequest = flatRequest.scanWith(HeaderUtils::appendTrailersMapper);
                    }
                }
                addRequestTransferEncodingIfNecessary(request);
            }

            final HttpExecutionStrategy strategy = requestExecutionStrategy(request,
                    executionContext().executionStrategy());
            if (strategy.isSendOffloaded()) {
                flatRequest = flatRequest.subscribeOn(connectionContext.executionContext().executor(),
                        IoThreadFactory.IoThread::currentThreadIsIoThread);
            }
            Single<StreamingHttpResponse> resp = makeRequest(request, flatRequest,
                    determineFlushStrategyForApi(request));

            if (strategy.isMetadataReceiveOffloaded()) {
                resp = resp.publishOn(
                        connectionContext.executionContext().executor(),
                        IoThreadFactory.IoThread::currentThreadIsIoThread);
            }
            if (strategy.isDataReceiveOffloaded()) {
                resp = resp.map(response ->
                        response.transformMessageBody(payload -> payload.publishOn(
                                connectionContext.executionContext().executor(),
                                IoThreadFactory.IoThread::currentThreadIsIoThread)));
            }

            return resp.shareContextOnSubscribe();
        });
    }

    static HttpExecutionStrategy requestExecutionStrategy(final HttpRequestMetaData metaData,
                                                          final HttpExecutionStrategy fallback) {
        final HttpExecutionStrategy strategy = metaData.context().get(HTTP_EXECUTION_STRATEGY_KEY);
        return strategy != null ? strategy : fallback;
    }

    @Nullable
    private static FlushStrategy determineFlushStrategyForApi(final HttpRequestMetaData request) {
        // For non-aggregated requests or when "Expect: 100-continue" is detected, don't change the flush strategy,
        // keep the default.
        return isSafeToAggregateOrEmpty(request) && !REQ_EXPECT_CONTINUE.test(request) ? flushOnEnd() : null;
    }

    static boolean isSafeToAggregateOrEmpty(final HttpMetaData request) {
        return isPayloadEmpty(request) || isSafeToAggregate(request);
    }

    @Override
    public final HttpExecutionContext executionContext() {
        return connectionContext.executionContext();
    }

    protected abstract Publisher<Object> writeAndRead(Publisher<Object> stream,
                                                      @Nullable FlushStrategy flushStrategy);

    private StreamingHttpResponse newSplicedResponse(HttpResponseMetaData meta, Publisher<Object> pub) {
        return newTransportResponse(meta.status(), meta.version(), meta.headers(),
                connectionContext.executionContext().bufferAllocator(), pub,
                allowDropTrailersReadFromTransport, headersFactory);
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
    public final Completable onClosing() {
        return connectionContext.onClosing();
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
    public final String toString() {
        return getClass().getName() + '(' + connectionContext + ')';
    }
}

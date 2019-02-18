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
package io.servicetalk.http.netty;

import io.servicetalk.client.api.ConnectionClosedException;
import io.servicetalk.client.internal.MaxRequestLimitExceededRejectedSubscribeException;
import io.servicetalk.client.internal.RequestConcurrencyController;
import io.servicetalk.concurrent.api.Single;
import io.servicetalk.concurrent.api.internal.SubscribableSingle;
import io.servicetalk.concurrent.internal.LatestValueSubscriber;
import io.servicetalk.http.api.HttpConnectionFilterFactory;
import io.servicetalk.http.api.HttpExecutionStrategy;
import io.servicetalk.http.api.StreamingHttpConnectionFilter;
import io.servicetalk.http.api.StreamingHttpRequest;
import io.servicetalk.http.api.StreamingHttpResponse;
import io.servicetalk.http.utils.DoBeforeOnFinallyOnHttpResponseOperator;
import io.servicetalk.transport.netty.internal.NettyConnectionContext;

import static io.servicetalk.client.internal.RequestConcurrencyControllers.newController;
import static io.servicetalk.client.internal.RequestConcurrencyControllers.newSingleController;
import static io.servicetalk.concurrent.Cancellable.IGNORE_CANCEL;
import static io.servicetalk.concurrent.api.SourceAdapters.toSource;
import static io.servicetalk.http.api.StreamingHttpConnection.SettingKey.MAX_CONCURRENCY;

final class ConcurrentRequestsHttpConnectionFilter implements HttpConnectionFilterFactory {

    private final int defaultMaxPipelinedRequests;

    ConcurrentRequestsHttpConnectionFilter(final int defaultMaxPipelinedRequests) {
        this.defaultMaxPipelinedRequests = defaultMaxPipelinedRequests;
    }

    @Override
    public StreamingHttpConnectionFilter create(final StreamingHttpConnectionFilter connection) {
        return new ConcurrentRequestsFilter(connection, defaultMaxPipelinedRequests);
    }

    private static final class ConcurrentRequestsFilter extends StreamingHttpConnectionFilter {

        private final RequestConcurrencyController limiter;
        private static final Throwable NONE = new Throwable();

        private final LatestValueSubscriber<Throwable> transportError = new LatestValueSubscriber<>();

        private ConcurrentRequestsFilter(final StreamingHttpConnectionFilter next,
                                         final int defaultMaxPipelinedRequests) {
            super(next);

            if (next.connectionContext() instanceof NettyConnectionContext) {
                toSource(((NettyConnectionContext) next.connectionContext())
                        .transportError().toPublisher()).subscribe(transportError);
            }

            limiter = defaultMaxPipelinedRequests == 1 ?
                    newSingleController(next.settingStream(MAX_CONCURRENCY), next.onClose()) :
                    newController(next.settingStream(MAX_CONCURRENCY), next.onClose(), defaultMaxPipelinedRequests);
        }

        @Override
        protected Single<StreamingHttpResponse> request(final StreamingHttpConnectionFilter delegate,
                                                        final HttpExecutionStrategy strategy,
                                                        final StreamingHttpRequest request) {
            return new SubscribableSingle<StreamingHttpResponse>() {
                @Override
                protected void handleSubscribe(final Subscriber<? super StreamingHttpResponse> subscriber) {
                    RequestConcurrencyController.Result result = limiter.tryRequest();
                    Throwable reportedError;
                    switch (result) {
                        case Accepted:
                            toSource(delegate.request(strategy, request)
                                    .liftSynchronous(new DoBeforeOnFinallyOnHttpResponseOperator(
                                            limiter::requestFinished)))
                                    .subscribe(subscriber);
                            return;
                        case RejectedTemporary:
                            reportedError = new MaxRequestLimitExceededRejectedSubscribeException(
                                    "Max concurrent requests saturated for: " +
                                            ConcurrentRequestsFilter.this);
                            break;
                        case RejectedPermanently:
                            reportedError = ConcurrentRequestsFilter.this.transportError.lastSeenValue(NONE);
                            if (reportedError == NONE) {
                                reportedError = new ConnectionClosedException(
                                        "Connection Closed: " + ConcurrentRequestsFilter.this);
                            } else {
                                reportedError = new ConnectionClosedException(
                                        "Connection Closed: " + ConcurrentRequestsFilter.this, reportedError);
                            }
                            break;
                        default:
                            reportedError = new AssertionError("Unexpected result: " + result +
                                    " determining concurrency limit for the connection " +
                                    ConcurrentRequestsFilter.this);
                            break;
                    }
                    subscriber.onSubscribe(IGNORE_CANCEL);
                    subscriber.onError(reportedError);
                }
            };
        }

        @Override
        protected HttpExecutionStrategy mergeForEffectiveStrategy(final HttpExecutionStrategy mergeWith) {
            // Since this filter does not have any blocking code, we do not need to alter the effective strategy.
            return mergeWith;
        }
    }
}

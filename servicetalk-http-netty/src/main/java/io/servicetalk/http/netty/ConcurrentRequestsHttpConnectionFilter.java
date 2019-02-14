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

import io.servicetalk.client.api.ConnectionClosedException;
import io.servicetalk.client.internal.MaxRequestLimitExceededRejectedSubscribeException;
import io.servicetalk.client.internal.RequestConcurrencyController;
import io.servicetalk.concurrent.api.Single;
import io.servicetalk.concurrent.internal.LatestValueSubscriber;
import io.servicetalk.http.api.HttpExecutionStrategy;
import io.servicetalk.http.api.StreamingHttpConnection;
import io.servicetalk.http.api.StreamingHttpConnectionFilter;
import io.servicetalk.http.api.StreamingHttpRequest;
import io.servicetalk.http.api.StreamingHttpResponse;
import io.servicetalk.http.utils.DoBeforeFinallyOnHttpResponseOperator;
import io.servicetalk.transport.netty.internal.NettyConnectionContext;

import static io.servicetalk.client.internal.RequestConcurrencyControllers.newController;
import static io.servicetalk.client.internal.RequestConcurrencyControllers.newSingleController;
import static io.servicetalk.concurrent.Cancellable.IGNORE_CANCEL;
import static io.servicetalk.http.api.StreamingHttpConnection.SettingKey.MAX_CONCURRENCY;

final class ConcurrentRequestsHttpConnectionFilter extends StreamingHttpConnectionFilter {
    private static final Throwable NONE = new Throwable();

    private final RequestConcurrencyController limiter;
    private final LatestValueSubscriber<Throwable> transportError = new LatestValueSubscriber<>();

    ConcurrentRequestsHttpConnectionFilter(final StreamingHttpConnection next, final int defaultMaxPipelinedRequests) {
        super(next);

        if (next.connectionContext() instanceof NettyConnectionContext) {
            ((NettyConnectionContext) next.connectionContext())
                    .transportError().toPublisher().subscribe(transportError);
        }

        limiter = defaultMaxPipelinedRequests == 1 ?
                newSingleController(next.settingStream(MAX_CONCURRENCY), next.onClose()) :
                newController(next.settingStream(MAX_CONCURRENCY), next.onClose(), defaultMaxPipelinedRequests);
    }

    @Override
    public Single<StreamingHttpResponse> request(final HttpExecutionStrategy strategy,
                                                 final StreamingHttpRequest request) {
        return new Single<StreamingHttpResponse>() {
            @Override
            protected void handleSubscribe(final Subscriber<? super StreamingHttpResponse> subscriber) {
                RequestConcurrencyController.Result result = limiter.tryRequest();
                Throwable reportedError;
                switch (result) {
                    case Accepted:
                        delegate().request(strategy, request)
                                .liftSynchronous(new DoBeforeFinallyOnHttpResponseOperator(limiter::requestFinished))
                                .subscribe(subscriber);
                        return;
                    case RejectedTemporary:
                        reportedError = new MaxRequestLimitExceededRejectedSubscribeException(
                                        "Max concurrent requests saturated for: " +
                                                ConcurrentRequestsHttpConnectionFilter.this);
                        break;
                    case RejectedPermanently:
                        reportedError = ConcurrentRequestsHttpConnectionFilter.this.transportError
                                .getLastSeenValue(NONE);
                        if (reportedError == NONE) {
                            reportedError = new ConnectionClosedException(
                                    "Connection Closed: " + ConcurrentRequestsHttpConnectionFilter.this);
                        } else {
                            reportedError = new ConnectionClosedException(
                                    "Connection Closed: " + ConcurrentRequestsHttpConnectionFilter.this, reportedError);
                        }
                        break;
                    default:
                        reportedError = new AssertionError("Unexpected result: " + result +
                                " determining concurrency limit for the connection " +
                                ConcurrentRequestsHttpConnectionFilter.this);
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

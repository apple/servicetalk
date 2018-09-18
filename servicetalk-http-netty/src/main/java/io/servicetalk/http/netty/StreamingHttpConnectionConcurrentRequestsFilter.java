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

import io.servicetalk.client.internal.MaxRequestLimitExceededRejectedSubscribeException;
import io.servicetalk.client.internal.RequestConcurrencyController;
import io.servicetalk.concurrent.api.Single;
import io.servicetalk.http.api.StreamingHttpConnection;
import io.servicetalk.http.api.StreamingHttpConnectionAdapter;
import io.servicetalk.http.api.StreamingHttpRequest;
import io.servicetalk.http.api.StreamingHttpResponse;

import static io.servicetalk.client.internal.RequestConcurrencyControllers.newController;
import static io.servicetalk.client.internal.RequestConcurrencyControllers.newSingleController;
import static io.servicetalk.concurrent.Cancellable.IGNORE_CANCEL;
import static io.servicetalk.http.api.StreamingHttpConnection.SettingKey.MAX_CONCURRENCY;

final class StreamingHttpConnectionConcurrentRequestsFilter extends StreamingHttpConnectionAdapter {
    private final RequestConcurrencyController limiter;

    StreamingHttpConnectionConcurrentRequestsFilter(StreamingHttpConnection next,
                                                    int defaultMaxPipelinedRequests) {
        super(next);
        limiter = defaultMaxPipelinedRequests == 1 ?
                newSingleController(next.getSettingStream(MAX_CONCURRENCY), next.onClose()) :
                newController(next.getSettingStream(MAX_CONCURRENCY), next.onClose(), defaultMaxPipelinedRequests);
    }

    @Override
    public Single<StreamingHttpResponse> request(final StreamingHttpRequest request) {
        return new Single<StreamingHttpResponse>() {
            @Override
            protected void handleSubscribe(final Subscriber<? super StreamingHttpResponse> subscriber) {
                if (limiter.tryRequest()) {
                    getDelegate().request(request).liftSynchronous(new ConcurrencyControlSingleOperator(limiter))
                            .subscribe(subscriber);
                } else {
                    subscriber.onSubscribe(IGNORE_CANCEL);
                    subscriber.onError(new MaxRequestLimitExceededRejectedSubscribeException(
                            "Max concurrent requests saturated for: " +
                                    StreamingHttpConnectionConcurrentRequestsFilter.this));
                }
            }
        };
    }
}

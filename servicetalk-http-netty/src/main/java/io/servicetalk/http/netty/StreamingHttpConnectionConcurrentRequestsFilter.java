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
import io.servicetalk.concurrent.api.Completable;
import io.servicetalk.concurrent.api.Publisher;
import io.servicetalk.concurrent.api.Single;
import io.servicetalk.http.api.HttpPayloadChunk;
import io.servicetalk.http.api.StreamingHttpConnection;
import io.servicetalk.http.api.StreamingHttpRequest;
import io.servicetalk.http.api.StreamingHttpResponse;
import io.servicetalk.transport.api.ConnectionContext;
import io.servicetalk.transport.api.ExecutionContext;

import static io.servicetalk.client.internal.RequestConcurrencyControllers.newController;
import static io.servicetalk.client.internal.RequestConcurrencyControllers.newSingleController;
import static io.servicetalk.concurrent.Cancellable.IGNORE_CANCEL;
import static io.servicetalk.http.api.StreamingHttpConnection.SettingKey.MAX_CONCURRENCY;
import static java.util.Objects.requireNonNull;

final class StreamingHttpConnectionConcurrentRequestsFilter extends StreamingHttpConnection {
    private final StreamingHttpConnection next;
    private final RequestConcurrencyController limiter;

    StreamingHttpConnectionConcurrentRequestsFilter(StreamingHttpConnection next,
                                                    int defaultMaxPipelinedRequests) {
        this.next = requireNonNull(next);
        limiter = defaultMaxPipelinedRequests == 1 ?
                newSingleController(next.getSettingStream(MAX_CONCURRENCY), next.onClose()) :
                newController(next.getSettingStream(MAX_CONCURRENCY), next.onClose(), defaultMaxPipelinedRequests);
    }

    @Override
    public ConnectionContext getConnectionContext() {
        return next.getConnectionContext();
    }

    @Override
    public <T> Publisher<T> getSettingStream(final SettingKey<T> settingKey) {
        return next.getSettingStream(settingKey);
    }

    @Override
    public Single<StreamingHttpResponse<HttpPayloadChunk>> request(final StreamingHttpRequest<HttpPayloadChunk> request) {
        return new Single<StreamingHttpResponse<HttpPayloadChunk>>() {
            @Override
            protected void handleSubscribe(final Subscriber<? super StreamingHttpResponse<HttpPayloadChunk>> subscriber) {
                if (limiter.tryRequest()) {
                    next.request(request).liftSynchronous(new ConcurrencyControlSingleOperator(limiter)).subscribe(subscriber);
                } else {
                    subscriber.onSubscribe(IGNORE_CANCEL);
                    subscriber.onError(new MaxRequestLimitExceededRejectedSubscribeException(
                            "Max concurrent requests saturated for: " +
                                    StreamingHttpConnectionConcurrentRequestsFilter.this));
                }
            }
        };
    }

    @Override
    public ExecutionContext getExecutionContext() {
        return next.getExecutionContext();
    }

    @Override
    public Completable onClose() {
        return next.onClose();
    }

    @Override
    public Completable closeAsync() {
        return next.closeAsync();
    }

    @Override
    public Completable closeAsyncGracefully() {
        return next.closeAsyncGracefully();
    }

    @Override
    public String toString() {
        return StreamingHttpConnectionConcurrentRequestsFilter.class.getSimpleName() + "(" + next + ")";
    }
}

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

import io.servicetalk.client.api.MaxRequestLimitExceededException;
import io.servicetalk.client.internal.RequestConcurrencyController;
import io.servicetalk.concurrent.api.Completable;
import io.servicetalk.concurrent.api.Publisher;
import io.servicetalk.concurrent.api.Single;
import io.servicetalk.http.api.HttpConnection;
import io.servicetalk.http.api.HttpPayloadChunk;
import io.servicetalk.http.api.HttpRequest;
import io.servicetalk.http.api.HttpResponse;
import io.servicetalk.transport.api.ConnectionContext;
import io.servicetalk.transport.api.ExecutionContext;

import static io.servicetalk.client.internal.RequestConcurrencyControllers.newController;
import static io.servicetalk.client.internal.RequestConcurrencyControllers.newSingleController;
import static io.servicetalk.concurrent.Cancellable.IGNORE_CANCEL;
import static io.servicetalk.http.api.HttpConnection.SettingKey.MAX_CONCURRENCY;
import static java.util.Objects.requireNonNull;

final class HttpConnectionConcurrentRequestsFilter extends HttpConnection<HttpPayloadChunk, HttpPayloadChunk> {
    private final HttpConnection<HttpPayloadChunk, HttpPayloadChunk> next;
    private final RequestConcurrencyController limiter;

    HttpConnectionConcurrentRequestsFilter(HttpConnection<HttpPayloadChunk, HttpPayloadChunk> next,
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
    public Single<HttpResponse<HttpPayloadChunk>> request(final HttpRequest<HttpPayloadChunk> request) {
        return new Single<HttpResponse<HttpPayloadChunk>>() {
            @Override
            protected void handleSubscribe(final Subscriber<? super HttpResponse<HttpPayloadChunk>> subscriber) {
                if (limiter.tryRequest()) {
                    next.request(request).doBeforeFinally(limiter::requestFinished).subscribe(subscriber);
                } else {
                    subscriber.onSubscribe(IGNORE_CANCEL);
                    subscriber.onError(new MaxRequestLimitExceededException("Max concurrent requests saturated for: " +
                            HttpConnectionConcurrentRequestsFilter.this));
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
    public String toString() {
        return HttpConnectionConcurrentRequestsFilter.class.getSimpleName() + "(" + next + ")";
    }
}

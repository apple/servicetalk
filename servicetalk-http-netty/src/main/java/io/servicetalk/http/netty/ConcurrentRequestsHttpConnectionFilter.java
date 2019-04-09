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
import io.servicetalk.client.api.internal.MaxRequestLimitExceededRejectedSubscribeException;
import io.servicetalk.client.api.internal.RequestConcurrencyController;
import io.servicetalk.concurrent.api.Completable;
import io.servicetalk.concurrent.api.Publisher;
import io.servicetalk.concurrent.api.Single;
import io.servicetalk.concurrent.api.internal.SubscribableSingle;
import io.servicetalk.concurrent.internal.LatestValueSubscriber;
import io.servicetalk.http.api.FilterableStreamingHttpConnection;
import io.servicetalk.http.api.HttpExecutionStrategy;
import io.servicetalk.http.api.HttpRequestMethod;
import io.servicetalk.http.api.StreamingHttpRequest;
import io.servicetalk.http.api.StreamingHttpResponse;
import io.servicetalk.http.api.StreamingHttpResponseFactory;
import io.servicetalk.http.utils.DoBeforeFinallyOnHttpResponseOperator;
import io.servicetalk.transport.api.ConnectionContext;
import io.servicetalk.transport.api.ExecutionContext;

import static io.servicetalk.client.api.internal.RequestConcurrencyControllers.newController;
import static io.servicetalk.client.api.internal.RequestConcurrencyControllers.newSingleController;
import static io.servicetalk.concurrent.Cancellable.IGNORE_CANCEL;
import static io.servicetalk.concurrent.api.Executors.immediate;
import static io.servicetalk.concurrent.api.SourceAdapters.toSource;
import static io.servicetalk.http.api.FilterableStreamingHttpConnection.SettingKey.MAX_CONCURRENCY;

final class ConcurrentRequestsHttpConnectionFilter implements FilterableStreamingHttpConnection {
    private static final Throwable NONE = new Throwable() {
        @Override
        public Throwable fillInStackTrace() {
            return this;
        }
    };
    private final FilterableStreamingHttpConnection delegate;
    private final RequestConcurrencyController limiter;
    private final LatestValueSubscriber<Throwable> transportError = new LatestValueSubscriber<>();

    ConcurrentRequestsHttpConnectionFilter(final AbstractStreamingHttpConnection<?> delegate,
                                           final int defaultMaxPipelinedRequests) {
        this.delegate = delegate;
        toSource(delegate.connectionContext().transportError()
                .publishAndSubscribeOnOverride(immediate()).toPublisher()).subscribe(transportError);

        limiter = defaultMaxPipelinedRequests == 1 ?
                newSingleController(delegate.settingStream(MAX_CONCURRENCY), delegate.connectionContext().onClosing()) :
                newController(delegate.settingStream(MAX_CONCURRENCY), delegate.connectionContext().onClosing(),
                        defaultMaxPipelinedRequests);
    }

    @Override
    public ConnectionContext connectionContext() {
        return delegate.connectionContext();
    }

    @Override
    public <T> Publisher<T> settingStream(final SettingKey<T> settingKey) {
        return delegate.settingStream(settingKey);
    }

    @Override
    public Single<StreamingHttpResponse> request(final HttpExecutionStrategy strategy,
                                                 final StreamingHttpRequest request) {
        return new SubscribableSingle<StreamingHttpResponse>() {
            @Override
            protected void handleSubscribe(final Subscriber<? super StreamingHttpResponse> subscriber) {
                RequestConcurrencyController.Result result = limiter.tryRequest();
                Throwable reportedError;
                switch (result) {
                    case Accepted:
                        toSource(delegate.request(strategy, request)
                                .liftSync(new DoBeforeFinallyOnHttpResponseOperator(
                                        limiter::requestFinished)))
                                .subscribe(subscriber);
                        return;
                    case RejectedTemporary:
                        reportedError = new MaxRequestLimitExceededRejectedSubscribeException(
                                "Max concurrent requests saturated for: " +
                                        this);
                        break;
                    case RejectedPermanently:
                        reportedError = transportError.lastSeenValue(NONE);
                        if (reportedError == NONE) {
                            reportedError = new ConnectionClosedException(
                                    "Connection Closed: " + this);
                        } else {
                            reportedError = new ConnectionClosedException(
                                    "Connection Closed: " + this, reportedError);
                        }
                        break;
                    default:
                        reportedError = new AssertionError("Unexpected result: " + result +
                                " determining concurrency limit for the connection " +
                                this);
                        break;
                }
                subscriber.onSubscribe(IGNORE_CANCEL);
                subscriber.onError(reportedError);
            }
        };
    }

    @Override
    public ExecutionContext executionContext() {
        return delegate.executionContext();
    }

    @Override
    public StreamingHttpResponseFactory httpResponseFactory() {
        return delegate.httpResponseFactory();
    }

    @Override
    public void close() throws Exception {
        delegate.close();
    }

    @Override
    public HttpExecutionStrategy computeExecutionStrategy(final HttpExecutionStrategy other) {
        return delegate.computeExecutionStrategy(other);
    }

    @Override
    public Completable onClose() {
        return delegate.onClose();
    }

    @Override
    public Completable closeAsync() {
        return delegate.closeAsync();
    }

    @Override
    public Completable closeAsyncGracefully() {
        return delegate.closeAsyncGracefully();
    }

    @Override
    public StreamingHttpRequest newRequest(final HttpRequestMethod method, final String requestTarget) {
        return delegate.newRequest(method, requestTarget);
    }
}

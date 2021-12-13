/*
 * Copyright © 2021 Apple Inc. and the ServiceTalk project authors
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
import io.servicetalk.http.api.FilterableReservedStreamingHttpConnection;
import io.servicetalk.http.api.FilterableStreamingHttpClient;
import io.servicetalk.http.api.HttpExecutionContext;
import io.servicetalk.http.api.HttpRequestMetaData;
import io.servicetalk.http.api.HttpRequestMethod;
import io.servicetalk.http.api.StreamingHttpRequest;
import io.servicetalk.http.api.StreamingHttpResponse;
import io.servicetalk.http.api.StreamingHttpResponseFactory;

import javax.annotation.Nullable;

final class ContextAwareDelegateStreamingHttpClient implements FilterableStreamingHttpClient {

    private final FilterableStreamingHttpClient delegate;
    @Nullable
    private final Publisher<Object> lbEventStream;
    @Nullable
    private final Completable sdStatus;

    ContextAwareDelegateStreamingHttpClient(final FilterableStreamingHttpClient delegate,
                                            @Nullable final Publisher<Object> lbEventStream,
                                            @Nullable final Completable sdStatus) {
        this.delegate = delegate;
        this.lbEventStream = lbEventStream;
        this.sdStatus = sdStatus;
    }

    @Override
    public Single<? extends FilterableReservedStreamingHttpConnection> reserveConnection(
            final HttpRequestMetaData metaData) {
        return delegate.reserveConnection(metaData);
    }

    @Override
    public Completable closeAsync() {
        return delegate.closeAsync();
    }

    @Override
    public Completable onClose() {
        return delegate.onClose();
    }

    @Override
    public StreamingHttpRequest newRequest(final HttpRequestMethod method, final String requestTarget) {
        return delegate.newRequest(method, requestTarget);
    }

    @Override
    public Single<StreamingHttpResponse> request(final StreamingHttpRequest request) {
        return delegate.request(request);
    }

    @Override
    public HttpExecutionContext executionContext() {
        return delegate.executionContext();
    }

    @Override
    public StreamingHttpResponseFactory httpResponseFactory() {
        return delegate.httpResponseFactory();
    }

    @Nullable
    Publisher<Object> lbEventStream() {
        return lbEventStream;
    }

    @Nullable
    Completable sdStatus() {
        return sdStatus;
    }
}

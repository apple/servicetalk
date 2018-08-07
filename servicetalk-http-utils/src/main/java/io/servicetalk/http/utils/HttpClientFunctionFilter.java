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
package io.servicetalk.http.utils;

import io.servicetalk.concurrent.api.Completable;
import io.servicetalk.concurrent.api.Single;
import io.servicetalk.http.api.HttpClient;
import io.servicetalk.http.api.HttpPayloadChunk;
import io.servicetalk.http.api.HttpRequest;
import io.servicetalk.http.api.HttpRequester;
import io.servicetalk.http.api.HttpResponse;
import io.servicetalk.transport.api.ExecutionContext;

import java.util.function.BiFunction;

import static java.util.Objects.requireNonNull;

/**
 * A {@link HttpClient} filter that delegates to a {@link BiFunction} in {@link HttpClient#request(HttpRequest)}.
 */
public final class HttpClientFunctionFilter extends HttpClient {
    private final BiFunction<HttpRequester,
            HttpRequest<HttpPayloadChunk>, Single<HttpResponse<HttpPayloadChunk>>> filter;
    private final HttpClient next;

    /**
     * Create a new instance.
     * @param filter The {@link BiFunction} which provides the filtering for the
     * {@link HttpClient#request(HttpRequest)} method.
     * @param next The next {@link HttpClient} in the filter chain.
     */
    public HttpClientFunctionFilter(BiFunction<HttpRequester,
                                               HttpRequest<HttpPayloadChunk>,
                                               Single<HttpResponse<HttpPayloadChunk>>> filter,
                                    HttpClient next) {
        this.filter = requireNonNull(filter);
        this.next = requireNonNull(next);
    }

    @Override
    public Single<? extends ReservedHttpConnection> reserveConnection(final HttpRequest<HttpPayloadChunk> request) {
        return next.reserveConnection(request);
    }

    @Override
    public Single<? extends UpgradableHttpResponse<HttpPayloadChunk>> upgradeConnection(
            final HttpRequest<HttpPayloadChunk> request) {
        return next.upgradeConnection(request);
    }

    @Override
    public Single<HttpResponse<HttpPayloadChunk>> request(final HttpRequest<HttpPayloadChunk> request) {
        return filter.apply(next, request);
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
}

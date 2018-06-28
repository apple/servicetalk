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
import io.servicetalk.concurrent.api.Publisher;
import io.servicetalk.concurrent.api.Single;
import io.servicetalk.http.api.HttpConnection;
import io.servicetalk.http.api.HttpPayloadChunk;
import io.servicetalk.http.api.HttpRequest;
import io.servicetalk.http.api.HttpRequester;
import io.servicetalk.http.api.HttpResponse;
import io.servicetalk.transport.api.ConnectionContext;
import io.servicetalk.transport.api.ExecutionContext;

import java.util.function.BiFunction;

import static java.util.Objects.requireNonNull;

/**
 * A {@link HttpConnection} filter that delegates to a {@link BiFunction} in
 * {@link HttpConnection#request(HttpRequest)}.
 */
public final class HttpConnectionFunctionFilter extends HttpConnection {
    private final BiFunction<HttpRequester,
            HttpRequest<HttpPayloadChunk>, Single<HttpResponse<HttpPayloadChunk>>> filter;
    private final HttpConnection next;

    /**
     * Create a new instance.
     * @param filter The {@link BiFunction} which provides the filtering for the
     * {@link HttpConnection#request(HttpRequest)} method.
     * @param next The next {@link HttpConnection} in the filter chain.
     */
    public HttpConnectionFunctionFilter(BiFunction<HttpRequester,
                                                   HttpRequest<HttpPayloadChunk>,
                                                   Single<HttpResponse<HttpPayloadChunk>>> filter,
                                 HttpConnection next) {
        this.filter = requireNonNull(filter);
        this.next = requireNonNull(next);
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

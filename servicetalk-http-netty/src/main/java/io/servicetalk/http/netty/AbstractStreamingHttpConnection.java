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

import io.servicetalk.concurrent.api.Completable;
import io.servicetalk.concurrent.api.Publisher;
import io.servicetalk.concurrent.api.Single;
import io.servicetalk.http.api.FilterableStreamingHttpConnection;
import io.servicetalk.http.api.HttpExecutionContext;
import io.servicetalk.http.api.HttpExecutionStrategy;
import io.servicetalk.http.api.HttpRequestMethod;
import io.servicetalk.http.api.HttpResponseMetaData;
import io.servicetalk.http.api.StreamingHttpRequest;
import io.servicetalk.http.api.StreamingHttpRequestResponseFactory;
import io.servicetalk.http.api.StreamingHttpResponse;
import io.servicetalk.http.api.StreamingHttpResponseFactory;
import io.servicetalk.transport.netty.internal.NettyConnectionContext;

import java.util.function.Function;

import static io.servicetalk.concurrent.api.Publisher.failed;
import static io.servicetalk.concurrent.api.Publisher.from;
import static io.servicetalk.http.api.StreamingHttpResponses.newResponseWithTrailers;
import static io.servicetalk.http.netty.HeaderUtils.addRequestTransferEncodingIfNecessary;
import static io.servicetalk.http.netty.HeaderUtils.canAddRequestContentLength;
import static io.servicetalk.http.netty.HeaderUtils.setRequestContentLength;
import static java.util.Objects.requireNonNull;

abstract class AbstractStreamingHttpConnection<CC extends NettyConnectionContext> implements
                       FilterableStreamingHttpConnection, Function<Publisher<Object>, Single<StreamingHttpResponse>> {

    final CC connection;
    final HttpExecutionContext executionContext;
    private final Publisher<Integer> maxConcurrencySetting;
    private final StreamingHttpRequestResponseFactory reqRespFactory;

    AbstractStreamingHttpConnection(
            CC conn, ReadOnlyHttpClientConfig config, HttpExecutionContext executionContext,
            StreamingHttpRequestResponseFactory reqRespFactory) {
        this.connection = requireNonNull(conn);
        this.executionContext = requireNonNull(executionContext);
        this.reqRespFactory = requireNonNull(reqRespFactory);
        maxConcurrencySetting = from(config.maxPipelinedRequests())
                .concat(connection.onClosing()).concat(Single.succeeded(0));
    }

    @Override
    public final NettyConnectionContext connectionContext() {
        return connection;
    }

    @SuppressWarnings("unchecked")
    @Override
    public final <T> Publisher<T> settingStream(final SettingKey<T> settingKey) {
        if (settingKey == SettingKey.MAX_CONCURRENCY) {
            return (Publisher<T>) maxConcurrencySetting;
        }
        return failed(new IllegalArgumentException("Unknown setting: " + settingKey));
    }

    @Override
    public final Single<StreamingHttpResponse> apply(final Publisher<Object> flattenedRequest) {
        return new SpliceFlatStreamToMetaSingle<>(writeAndRead(flattenedRequest), this::newSplicedResponse);
    }

    @Override
    public Single<StreamingHttpResponse> request(final HttpExecutionStrategy strategy,
                                                 final StreamingHttpRequest request) {
        // See https://tools.ietf.org/html/rfc7230#section-3.3.3
        if (canAddRequestContentLength(request)) {
            return setRequestContentLength(request).flatMap(r ->
                    strategy.invokeClient(executionContext.executor(), r, this));
        }
        addRequestTransferEncodingIfNecessary(request);
        return strategy.invokeClient(executionContext.executor(), request, this);
    }

    @Override
    public final HttpExecutionContext executionContext() {
        return executionContext;
    }

    protected abstract Publisher<Object> writeAndRead(Publisher<Object> stream);

    private StreamingHttpResponse newSplicedResponse(HttpResponseMetaData meta, Publisher<Object> pub) {
        return newResponseWithTrailers(meta.status(), meta.version(), meta.headers(),
                executionContext.bufferAllocator(), pub);
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
        return connection.onClose();
    }

    @Override
    public final Completable closeAsync() {
        return connection.closeAsync();
    }

    @Override
    public final Completable closeAsyncGracefully() {
        return connection.closeAsyncGracefully();
    }

    @Override
    public String toString() {
        return getClass().getName() + '(' + connection + ')';
    }
}

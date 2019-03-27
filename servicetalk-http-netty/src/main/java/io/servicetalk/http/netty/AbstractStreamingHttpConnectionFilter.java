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

import io.servicetalk.concurrent.api.Completable;
import io.servicetalk.concurrent.api.Publisher;
import io.servicetalk.concurrent.api.Single;
import io.servicetalk.http.api.HttpExecutionStrategy;
import io.servicetalk.http.api.HttpResponseMetaData;
import io.servicetalk.http.api.StreamingHttpConnection.SettingKey;
import io.servicetalk.http.api.StreamingHttpConnectionFilter;
import io.servicetalk.http.api.StreamingHttpRequest;
import io.servicetalk.http.api.StreamingHttpRequestResponseFactory;
import io.servicetalk.http.api.StreamingHttpRequester;
import io.servicetalk.http.api.StreamingHttpResponse;
import io.servicetalk.transport.api.ConnectionContext;
import io.servicetalk.transport.api.ExecutionContext;

import java.util.function.Function;

import static io.servicetalk.concurrent.api.Publisher.error;
import static io.servicetalk.concurrent.api.Publisher.from;
import static io.servicetalk.http.api.StreamingHttpResponses.newResponseWithTrailers;
import static io.servicetalk.http.netty.HeaderUtils.addRequestTransferEncodingIfNecessary;
import static java.util.Objects.requireNonNull;

abstract class AbstractStreamingHttpConnectionFilter<CC extends ConnectionContext> extends StreamingHttpConnectionFilter
        implements Function<Publisher<Object>, Single<StreamingHttpResponse>> {

    protected final CC connection;
    protected final ExecutionContext executionContext;
    private final Publisher<Integer> maxConcurrencySetting;

    protected AbstractStreamingHttpConnectionFilter(
            CC conn, ReadOnlyHttpClientConfig config, ExecutionContext executionContext,
            StreamingHttpRequestResponseFactory reqRespFactory) {
        super(terminal(reqRespFactory));
        this.connection = requireNonNull(conn);
        this.executionContext = requireNonNull(executionContext);
        // TODO(jayv) we should concat with NettyConnectionContext.onClosing() once it's exposed such that both
        // this class and ConcurrentRequestsHttpConnectionFilter can listen to the same event to reduce ambiguity
        maxConcurrencySetting = from(config.maxPipelinedRequests())
                .concat(connection.onClose()).concat(Single.success(0));
    }

    @Override
    public ConnectionContext connectionContext() {
        return connection;
    }

    @SuppressWarnings("unchecked")
    @Override
    public final <T> Publisher<T> settingStream(final SettingKey<T> settingKey) {
        if (settingKey == SettingKey.MAX_CONCURRENCY) {
            return (Publisher<T>) maxConcurrencySetting;
        }
        return error(new IllegalArgumentException("Unknown setting: " + settingKey));
    }

    @Override
    public final Single<StreamingHttpResponse> apply(final Publisher<Object> flattenedRequest) {
        return new SpliceFlatStreamToMetaSingle<>(writeAndRead(flattenedRequest), this::newResponse);
    }

    @Override
    protected Single<StreamingHttpResponse> request(final StreamingHttpRequester terminalDelegate,
                                                    final HttpExecutionStrategy strategy,
                                                    final StreamingHttpRequest request) {
        addRequestTransferEncodingIfNecessary(request); // See https://tools.ietf.org/html/rfc7230#section-3.3.3
        // Don't call the terminal delegate!
        return strategy.invokeClient(executionContext.executor(), request, this);
    }

    @Override
    protected HttpExecutionStrategy mergeForEffectiveStrategy(final HttpExecutionStrategy mergeWith) {
        // Since this filter does not have any blocking code, we do not need to alter the effective strategy.
        return mergeWith;
    }

    @Override
    public ExecutionContext executionContext() {
        return executionContext;
    }

    protected abstract Publisher<Object> writeAndRead(Publisher<Object> stream);

    private StreamingHttpResponse newResponse(HttpResponseMetaData meta, Publisher<Object> pub) {
        return newResponseWithTrailers(meta.status(), meta.version(), meta.headers(),
                executionContext.bufferAllocator(), pub);
    }

    @Override
    public Completable onClose() {
        return connection.onClose();
    }

    @Override
    public Completable closeAsync() {
        return connection.closeAsync();
    }

    @Override
    public Completable closeAsyncGracefully() {
        return connection.closeAsyncGracefully();
    }

    @Override
    public String toString() {
        return getClass().getName() + '(' + connection + ')';
    }
}

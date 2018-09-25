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
import io.servicetalk.http.api.HttpResponseMetaData;
import io.servicetalk.http.api.StreamingHttpConnection;
import io.servicetalk.http.api.StreamingHttpRequest;
import io.servicetalk.http.api.StreamingHttpRequestResponseFactory;
import io.servicetalk.http.api.StreamingHttpResponse;
import io.servicetalk.http.api.StreamingHttpResponses;
import io.servicetalk.transport.api.ConnectionContext;
import io.servicetalk.transport.api.ExecutionContext;

import static io.servicetalk.concurrent.api.Publisher.error;
import static io.servicetalk.concurrent.api.Publisher.just;
import static io.servicetalk.concurrent.api.Single.success;
import static io.servicetalk.http.netty.HeaderUtils.addRequestTransferEncodingIfNecessary;
import static io.servicetalk.http.netty.SpliceFlatStreamToMetaSingle.flatten;
import static java.util.Objects.requireNonNull;

abstract class AbstractStreamingHttpConnection<CC extends ConnectionContext> extends StreamingHttpConnection {

    protected final CC connection;
    protected final ExecutionContext executionContext;
    private final Publisher<Integer> maxConcurrencySetting;

    protected AbstractStreamingHttpConnection(
            CC conn, Completable onClosing, ReadOnlyHttpClientConfig config, ExecutionContext executionContext,
            StreamingHttpRequestResponseFactory reqRespFactory) {
        super(reqRespFactory);
        this.connection = requireNonNull(conn);
        this.executionContext = requireNonNull(executionContext);
        maxConcurrencySetting = just(config.getMaxPipelinedRequests()).concatWith(onClosing.andThen(success(0)));
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
    public Single<StreamingHttpResponse> request(StreamingHttpRequest request) {
        addRequestTransferEncodingIfNecessary(request); // See https://tools.ietf.org/html/rfc7230#section-3.3.3
        final Publisher<Object> requestAsPublisher = flatten(request, StreamingHttpRequest::payloadBodyAndTrailers)
                // We will write this stream to the connection, which will request more data from the EventLoop.
                // Offload control path to avoid blocking the EventLoop
                .subscribeOn(executionContext.executor());
        return new SpliceFlatStreamToMetaSingle<>(writeAndRead(requestAsPublisher), this::newResponse)
                // Headers will be emitted from the EventLoop, so offload those signals to avoid blocking the EventLoop.
                .publishOn(executionContext.executor());
    }

    @Override
    public ExecutionContext executionContext() {
        return executionContext;
    }

    protected abstract Publisher<Object> writeAndRead(Publisher<Object> stream);

    private StreamingHttpResponse newResponse(HttpResponseMetaData meta, Publisher<Object> pub) {
        return StreamingHttpResponses.newResponseWithTrailers(meta.status(), meta.version(), meta.headers(),
                executionContext.bufferAllocator(),
                // Payload will be emitted from the EventLoop, so offload those signals to avoid blocking the EventLoop.
                pub.publishOn(executionContext.executor()));
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
        return getClass().getSimpleName() + "(" + connection + ")";
    }
}

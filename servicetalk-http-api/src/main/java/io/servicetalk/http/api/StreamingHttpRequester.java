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
package io.servicetalk.http.api;

import io.servicetalk.concurrent.api.ListenableAsyncCloseable;
import io.servicetalk.concurrent.api.Single;
import io.servicetalk.transport.api.ExecutionContext;

import static io.servicetalk.concurrent.internal.FutureUtils.awaitTermination;
import static java.util.Objects.requireNonNull;

/**
 * The equivalent of {@link HttpRequester} but that accepts {@link StreamingHttpRequest} and returns
 * {@link StreamingHttpResponse}.
 */
public abstract class StreamingHttpRequester
        implements StreamingHttpRequestFactory, ListenableAsyncCloseable, AutoCloseable {
    final StreamingHttpRequestResponseFactory reqRespFactory;
    private final HttpExecutionStrategy strategy;

    /**
     * Create a new instance.
     *
     * @param reqRespFactory The {@link StreamingHttpRequestResponseFactory} used to
     * {@link #newRequest(HttpRequestMethod, String) create new requests}.
     * @param strategy Default {@link HttpExecutionStrategy} to use.
     */
    protected StreamingHttpRequester(final StreamingHttpRequestResponseFactory reqRespFactory,
                                     final HttpExecutionStrategy strategy) {
        this.reqRespFactory = requireNonNull(reqRespFactory);
        this.strategy = requireNonNull(strategy);
    }

    /**
     * Send a {@code request}.
     *
     * @param request the request to send.
     * @return The response.
     */
    public final Single<StreamingHttpResponse> request(StreamingHttpRequest request) {
        return request(strategy, request);
    }

    /**
     * Send a {@code request} using the specified {@link HttpExecutionStrategy strategy}.
     *
     * @param strategy {@link HttpExecutionStrategy} to use for executing the request.
     * @param request the request to send.
     * @return The response.
     */
    public abstract Single<StreamingHttpResponse> request(HttpExecutionStrategy strategy, StreamingHttpRequest request);

    /**
     * Get the {@link ExecutionContext} used during construction of this object.
     * <p>
     * Note that the {@link ExecutionContext#ioExecutor()} will not necessarily be associated with a specific thread
     * unless that was how this object was built.
     *
     * @return the {@link ExecutionContext} used during construction of this object.
     */
    public abstract ExecutionContext executionContext();

    /**
     * Get the {@link ExecutionContext} used during construction of this object.
     * <p>
     * Note that the {@link ExecutionContext#ioExecutor()} will not necessarily be associated with a specific thread
     * unless that was how this object was built.
     *
     * @return the {@link ExecutionContext} used during construction of this object.
     */
    final HttpExecutionStrategy executionStrategy() {
        return strategy;
    }

    @Override
    public final StreamingHttpRequest newRequest(HttpRequestMethod method, String requestTarget) {
        return reqRespFactory.newRequest(method, requestTarget);
    }

    @Override
    public final void close() {
        awaitTermination(closeAsyncGracefully().toFuture());
    }
}

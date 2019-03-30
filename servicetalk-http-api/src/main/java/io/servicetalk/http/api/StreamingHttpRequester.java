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
import static io.servicetalk.http.api.HttpExecutionStrategies.OFFLOAD_ALL_STRATEGY;

/**
 * The equivalent of {@link HttpRequester} but that accepts {@link StreamingHttpRequest} and returns
 * {@link StreamingHttpResponse}.
 */
public interface StreamingHttpRequester extends StreamingHttpRequestFactory, ListenableAsyncCloseable, AutoCloseable {
    /**
     * Send a {@code request} using the specified {@link HttpExecutionStrategy strategy}.
     *
     * @param strategy {@link HttpExecutionStrategy} to use for executing the request.
     * @param request the request to send.
     * @return The response.
     */
    Single<StreamingHttpResponse> request(HttpExecutionStrategy strategy, StreamingHttpRequest request);

    /**
     * Get the {@link ExecutionContext} used during construction of this object.
     * <p>
     * Note that the {@link ExecutionContext#ioExecutor()} will not necessarily be associated with a specific thread
     * unless that was how this object was built.
     *
     * @return the {@link ExecutionContext} used during construction of this object.
     */
    ExecutionContext executionContext();

    /**
     * Get a {@link StreamingHttpResponseFactory}.
     *
     * @return a {@link StreamingHttpResponseFactory}.
     */
    StreamingHttpResponseFactory httpResponseFactory();

    @Override
    default void close() throws Exception {
        awaitTermination(closeAsyncGracefully().toFuture());
    }

    /**
     * Compute the {@link HttpExecutionStrategy} to be used for this {@link StreamingHttpRequester} considering the
     * passed {@link HttpExecutionStrategy}. The passed {@link HttpExecutionStrategy} is the strategy that the caller
     * intends to use if this {@link StreamingHttpRequester} does not modify it.
     *
     * @param other The other {@link HttpExecutionStrategy} to consider during the computation.
     * @return The {@link HttpExecutionStrategy} for this {@link StreamingHttpRequester}.
     */
    default HttpExecutionStrategy computeExecutionStrategy(HttpExecutionStrategy other) {
        return other.merge(OFFLOAD_ALL_STRATEGY);
    }
}

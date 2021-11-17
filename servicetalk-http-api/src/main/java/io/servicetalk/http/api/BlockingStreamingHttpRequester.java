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

import io.servicetalk.concurrent.GracefulAutoCloseable;

/**
 * The equivalent of {@link StreamingHttpRequester} but with synchronous/blocking APIs instead of asynchronous APIs.
 */
public interface BlockingStreamingHttpRequester extends BlockingStreamingHttpRequestFactory, GracefulAutoCloseable {
    /**
     * Send a {@code request}.
     *
     * @param request the request to send.
     * @return The response.
     */
    default BlockingStreamingHttpResponse request(BlockingStreamingHttpRequest request) throws Exception {
        // FIXME: 0.42 - remove default impl
        throw new UnsupportedOperationException("Method request(HttpRequest) is not supported by " +
                getClass().getName());
    }

    /**
     * Send a {@code request} using the passed {@link HttpExecutionStrategy strategy}.
     *
     * @param strategy {@link HttpExecutionStrategy} to use.
     * @param request the request to send.
     * @return The response.
     * @throws Exception if an exception occurs during the request processing.
     * @deprecated Use {@link #request(BlockingStreamingHttpRequest)}. If an {@link HttpExecutionStrategy} needs to be
     * altered, provide a value for {@link HttpContextKeys#HTTP_EXECUTION_STRATEGY_KEY} in the
     * {@link HttpRequestMetaData#context() request context}.
     */
    @Deprecated
    BlockingStreamingHttpResponse request(HttpExecutionStrategy strategy,
                                          BlockingStreamingHttpRequest request) throws Exception;

    /**
     * Get the {@link HttpExecutionContext} used during construction of this object.
     * <p>
     * Note that the {@link HttpExecutionContext#ioExecutor()} will not necessarily be associated with a specific thread
     * unless that was how this object was built.
     *
     * @return the {@link HttpExecutionContext} used during construction of this object.
     */
    HttpExecutionContext executionContext();

    /**
     * Get a {@link BlockingStreamingHttpResponseFactory}.
     *
     * @return a {@link BlockingStreamingHttpResponseFactory}.
     */
    BlockingStreamingHttpResponseFactory httpResponseFactory();

    @Override
    default void close() throws Exception {
        // noop
    }
}

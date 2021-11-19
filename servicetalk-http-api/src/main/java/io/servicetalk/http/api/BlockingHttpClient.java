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

import static io.servicetalk.http.api.HttpContextKeys.HTTP_EXECUTION_STRATEGY_KEY;

/**
 * The equivalent of {@link HttpClient} but with synchronous/blocking APIs instead of asynchronous APIs.
 */
public interface BlockingHttpClient extends BlockingHttpRequester {
    /**
     * Send a {@code request}.
     *
     * @param request the request to send.
     * @return The response.
     * @throws Exception if an exception occurs during the request processing.
     */
    @Override   // FIXME: 0.42 - remove, this method is defined in BlockingHttpRequester
    HttpResponse request(HttpRequest request) throws Exception;

    /**
     * Reserve a {@link BlockingHttpConnection} based on provided {@link HttpRequestMetaData}.
     *
     * @param metaData Allows the underlying layers to know what {@link BlockingHttpConnection}s are valid to
     * reserve for future {@link HttpRequest requests} with the same {@link HttpRequestMetaData}.
     * For example this may provide some insight into shard or other info.
     * @return a {@link ReservedBlockingHttpConnection}.
     * @throws Exception if a exception occurs during the reservation process.
     */
    ReservedBlockingHttpConnection reserveConnection(HttpRequestMetaData metaData) throws Exception;

    /**
     * Reserve a {@link BlockingHttpConnection} based on provided {@link HttpRequestMetaData}.
     *
     * @param strategy {@link HttpExecutionStrategy} to use.
     * @param metaData Allows the underlying layers to know what {@link BlockingHttpConnection}s are valid to
     * reserve for future {@link HttpRequest requests} with the same {@link HttpRequestMetaData}.
     * For example this may provide some insight into shard or other info.
     * @return a {@link ReservedBlockingHttpConnection}.
     * @throws Exception if an exception occurs during the reservation process.
     * @deprecated Use {@link #reserveConnection(HttpRequestMetaData)}. If an {@link HttpExecutionStrategy} needs to be
     * altered, provide a value for {@link HttpContextKeys#HTTP_EXECUTION_STRATEGY_KEY} in the
     * {@link HttpRequestMetaData#context() request context}.
     */
    @Deprecated
    default ReservedBlockingHttpConnection reserveConnection(HttpExecutionStrategy strategy,
                                                             HttpRequestMetaData metaData) throws Exception {
        metaData.context().put(HTTP_EXECUTION_STRATEGY_KEY, strategy);
        return reserveConnection(metaData);
    }

    /**
     * Convert this {@link BlockingHttpClient} to the {@link StreamingHttpClient} API.
     *
     * @return a {@link StreamingHttpClient} representation of this {@link BlockingHttpClient}.
     */
    StreamingHttpClient asStreamingClient();

    /**
     * Convert this {@link BlockingHttpClient} to the {@link HttpClient} API.
     *
     * @return a {@link HttpClient} representation of this {@link BlockingHttpClient}.
     */
    default HttpClient asClient() {
        return asStreamingClient().asClient();
    }

    /**
     * Convert this {@link BlockingHttpClient} to the {@link BlockingStreamingHttpClient} API.
     *
     * @return a {@link BlockingStreamingHttpClient} representation of this {@link BlockingHttpClient}.
     */
    default BlockingStreamingHttpClient asBlockingStreamingClient() {
        return asStreamingClient().asBlockingStreamingClient();
    }
}

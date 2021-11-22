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

import static io.servicetalk.concurrent.internal.FutureUtils.awaitTermination;

/**
 * The equivalent of {@link HttpConnection} but that accepts {@link StreamingHttpRequest} and returns
 * {@link StreamingHttpResponse}.
 */
public interface StreamingHttpConnection extends FilterableStreamingHttpConnection, GracefulAutoCloseable {
    /**
     * Convert this {@link StreamingHttpConnection} to the {@link HttpConnection} API.
     * <p>
     * This API is provided for convenience. It is recommended that
     * filters are implemented using the {@link StreamingHttpConnection} asynchronous API for maximum portability.
     * @return a {@link HttpConnection} representation of this {@link StreamingHttpConnection}.
     */
    HttpConnection asConnection();

    /**
     * Convert this {@link StreamingHttpConnection} to the {@link BlockingStreamingHttpConnection} API.
     * <p>
     * This API is provided for convenience for a more familiar sequential programming model. It is recommended that
     * filters are implemented using the {@link StreamingHttpConnection} asynchronous API for maximum portability.
     * @return a {@link BlockingStreamingHttpConnection} representation of this {@link StreamingHttpConnection}.
     */
    BlockingStreamingHttpConnection asBlockingStreamingConnection();

    /**
     * Convert this {@link StreamingHttpConnection} to the {@link BlockingHttpConnection} API.
     * <p>
     * This API is provided for convenience for a more familiar sequential programming model. It is recommended that
     * filters are implemented using the {@link StreamingHttpConnection} asynchronous API for maximum portability.
     * @return a {@link BlockingHttpConnection} representation of this {@link StreamingHttpConnection}.
     */
    BlockingHttpConnection asBlockingConnection();

    @Override
    default void close() throws Exception {
        awaitTermination(closeAsync().toFuture());
    }

    @Override
    default void closeGracefully() throws Exception {
        awaitTermination(closeAsyncGracefully().toFuture());
    }
}

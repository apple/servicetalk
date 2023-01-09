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
import io.servicetalk.concurrent.api.Single;

import static io.servicetalk.concurrent.internal.FutureUtils.awaitTermination;

/**
 * Provides a means to issue requests against HTTP service. The implementation is free to maintain a collection of
 * {@link HttpConnection} instances and distribute calls to {@link #request(HttpRequest)} amongst this collection.
 */
public interface HttpClient extends HttpRequester, GracefulAutoCloseable {
    /**
     * Reserve an {@link HttpConnection} based on provided {@link HttpRequestMetaData}.
     * <p>
     * If a new connection should be opened instead of potentially reusing an already established one, the
     * {@link HttpContextKeys#HTTP_FORCE_NEW_CONNECTION} must be set.
     *
     * @param metaData Allows the underlying layers to know what {@link HttpConnection}s are valid to
     * reserve for future {@link HttpRequest requests} with the same {@link HttpRequestMetaData}.
     * For example this may provide some insight into shard or other info.
     * @return a {@link Single} that provides the {@link ReservedHttpConnection} upon completion.
     * @see HttpContextKeys#HTTP_FORCE_NEW_CONNECTION
     */
    Single<ReservedHttpConnection> reserveConnection(HttpRequestMetaData metaData);

    /**
     * Convert this {@link HttpClient} to the {@link StreamingHttpClient} API.
     *
     * @return a {@link StreamingHttpClient} representation of this {@link HttpClient}.
     */
    StreamingHttpClient asStreamingClient();

    /**
     * Convert this {@link HttpClient} to the {@link BlockingStreamingHttpClient} API.
     *
     * @return a {@link BlockingStreamingHttpClient} representation of this {@link HttpClient}.
     */
    default BlockingStreamingHttpClient asBlockingStreamingClient() {
        return asStreamingClient().asBlockingStreamingClient();
    }

    /**
     * Convert this {@link HttpClient} to the {@link BlockingHttpClient} API.
     *
     * @return a {@link BlockingHttpClient} representation of this {@link HttpClient}.
     */
    default BlockingHttpClient asBlockingClient() {
        return asStreamingClient().asBlockingClient();
    }

    @Override
    default void close() throws Exception {
        awaitTermination(closeAsync().toFuture());
    }

    @Override
    default void closeGracefully() throws Exception {
        awaitTermination(closeAsyncGracefully().toFuture());
    }
}

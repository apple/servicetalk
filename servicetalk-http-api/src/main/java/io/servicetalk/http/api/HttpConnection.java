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
import io.servicetalk.concurrent.PublisherSource;
import io.servicetalk.concurrent.api.Publisher;
import io.servicetalk.concurrent.api.Single;

import static io.servicetalk.concurrent.internal.FutureUtils.awaitTermination;

/**
 * Represents a single fixed connection to a HTTP server.
 */
public interface HttpConnection extends HttpRequester, GracefulAutoCloseable {
    /**
     * Send a {@code request}.
     *
     * @param request the request to send.
     * @return The response.
     */
    @Override   // FIXME: 0.42 - remove, this method is defined in HttpRequester
    Single<HttpResponse> request(HttpRequest request);

    /**
     * Get the {@link HttpConnectionContext}.
     *
     * @return the {@link HttpConnectionContext}.
     */
    HttpConnectionContext connectionContext();

    /**
     * Returns a {@link Publisher} that gives the current value of a transport event as well as subsequent changes to
     * the event value as long as the {@link PublisherSource.Subscriber} has expressed enough demand.
     * <p>
     * This is designed for events produced by the transport, and consumed by filters interested in transport behavior
     * which is not directly involved in the data path.
     *
     * @param eventKey Name of the event to fetch.
     * @param <T> Type of the event value.
     * @return {@link Publisher} for the event values.
     */
    <T> Publisher<? extends T> transportEventStream(HttpEventKey<T> eventKey);

    /**
     * Convert this {@link HttpConnection} to the {@link StreamingHttpConnection} API.
     *
     * @return a {@link StreamingHttpConnection} representation of this {@link HttpConnection}.
     */
    StreamingHttpConnection asStreamingConnection();

    /**
     * Convert this {@link HttpConnection} to the {@link BlockingStreamingHttpConnection} API.
     *
     * @return a {@link BlockingStreamingHttpConnection} representation of this {@link HttpConnection}.
     */
    default BlockingStreamingHttpConnection asBlockingStreamingConnection() {
        return asStreamingConnection().asBlockingStreamingConnection();
    }

    /**
     * Convert this {@link HttpConnection} to the {@link BlockingHttpConnection} API.
     *
     * @return a {@link BlockingHttpConnection} representation of this {@link HttpConnection}.
     */
    default BlockingHttpConnection asBlockingConnection() {
        return asStreamingConnection().asBlockingConnection();
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

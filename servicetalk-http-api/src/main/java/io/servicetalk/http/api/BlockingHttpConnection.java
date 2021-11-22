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

import io.servicetalk.concurrent.BlockingIterable;
import io.servicetalk.concurrent.PublisherSource.Subscriber;

/**
 * The equivalent of {@link HttpConnection} but with synchronous/blocking APIs instead of asynchronous APIs.
 */
public interface BlockingHttpConnection extends BlockingHttpRequester {
    /**
     * Get the {@link HttpConnectionContext}.
     *
     * @return the {@link HttpConnectionContext}.
     */
    HttpConnectionContext connectionContext();

    /**
     * Returns a {@link BlockingIterable} that gives the current value of the setting as well as subsequent changes to
     * the setting value as long as the {@link Subscriber} has expressed enough demand.
     * <p>
     * This is designed for events produced by the transport, and consumed by filters interested in transport behavior
     * which is not directly involved in the data path.
     *
     * @param eventKey Name of the event to fetch.
     * @param <T> Type of the setting value.
     * @return {@link BlockingIterable} for the setting values.
     */
    <T> BlockingIterable<? extends T> transportEventIterable(HttpEventKey<T> eventKey);

    /**
     * Convert this {@link BlockingHttpConnection} to the {@link StreamingHttpConnection} API.
     * <p>
     * Note that the resulting {@link StreamingHttpConnection} may still be subject to any blocking, in memory
     * aggregation, and other behavior as this {@link BlockingHttpConnection}.
     *
     * @return a {@link StreamingHttpConnection} representation of this {@link BlockingHttpConnection}.
     */
    StreamingHttpConnection asStreamingConnection();

    /**
     * Convert this {@link BlockingHttpConnection} to the {@link HttpConnection} API.
     * <p>
     * Note that the resulting {@link HttpConnection} may still be subject to any blocking, in memory
     * aggregation, and other behavior as this {@link BlockingHttpConnection}.
     *
     * @return a {@link HttpConnection} representation of this {@link BlockingHttpConnection}.
     */
    default HttpConnection asConnection() {
        return asStreamingConnection().asConnection();
    }

    /**
     * Convert this {@link BlockingHttpConnection} to the {@link BlockingStreamingHttpConnection} API.
     * <p>
     * Note that the resulting {@link BlockingStreamingHttpConnection} may still be subject to in memory
     * aggregation and other behavior as this {@link BlockingHttpConnection}.
     *
     * @return a {@link BlockingStreamingHttpConnection} representation of this {@link BlockingHttpConnection}.
     */
    default BlockingStreamingHttpConnection asBlockingStreamingConnection() {
        return asStreamingConnection().asBlockingStreamingConnection();
    }
}

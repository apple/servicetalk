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

import io.servicetalk.transport.api.ExecutionContext;

/**
 * A builder of {@link HttpClient} objects.
 */
public interface HttpClientBuilder {

    /**
     * Sets an {@link ExecutionContext} for all clients created from this {@link HttpClientBuilder}.
     *
     * @param context {@link ExecutionContext} to use.
     * @return {@code this}.
     */
    HttpClientBuilder executionContext(ExecutionContext context);

    /**
     * Build a new {@link StreamingHttpClient}, using a default {@link ExecutionContext}.
     *
     * @return A new {@link StreamingHttpClient}
     */
    StreamingHttpClient buildStreaming();

    /**
     * Build a new {@link HttpClient}, using a default {@link ExecutionContext}.
     *
     * @return A new {@link HttpClient}
     */
    default HttpClient build() {
        return buildStreaming().asClient();
    }

    /**
     * Create a new {@link BlockingStreamingHttpClient}, using a default {@link ExecutionContext}.
     *
     * @return {@link BlockingStreamingHttpClient}
     */
    default BlockingStreamingHttpClient buildBlockingStreaming() {
        return buildStreaming().asBlockingStreamingClient();
    }

    /**
     * Create a new {@link BlockingHttpClient}, using a default {@link ExecutionContext}.
     *
     * @return {@link BlockingHttpClient}
     */
    default BlockingHttpClient buildBlocking() {
        return buildStreaming().asBlockingClient();
    }
}

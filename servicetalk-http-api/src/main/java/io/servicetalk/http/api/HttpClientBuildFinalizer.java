/*
 * Copyright © 2021 Apple Inc. and the ServiceTalk project authors
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

@FunctionalInterface
interface HttpClientBuildFinalizer {

    /**
     * Builds a new {@link StreamingHttpClient}.
     *
     * @return A new {@link StreamingHttpClient}
     */
    StreamingHttpClient buildStreaming();

    /**
     * Builds a new {@link HttpClient}.
     *
     * @return A new {@link HttpClient}
     */
    default HttpClient build() {
        return buildStreaming().asClient();
    }

    /**
     * Creates a new {@link BlockingStreamingHttpClient}.
     *
     * @return {@link BlockingStreamingHttpClient}
     */
    default BlockingStreamingHttpClient buildBlockingStreaming() {
        return buildStreaming().asBlockingStreamingClient();
    }

    /**
     * Creates a new {@link BlockingHttpClient}.
     *
     * @return {@link BlockingHttpClient}
     */
    default BlockingHttpClient buildBlocking() {
        return buildStreaming().asBlockingClient();
    }
}

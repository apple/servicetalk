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

import static io.servicetalk.http.api.HttpRequestMethods.GET;
import static io.servicetalk.http.api.HttpRequestMethods.POST;

/**
 * A factory for creating {@link StreamingHttpRequest}s.
 */
public interface StreamingHttpRequestFactory {
    /**
     * Create a new {@link HttpRequestFactory}.
     * @param method The {@link HttpRequestMethod}.
     * @param requestTarget The <a herf="https://tools.ietf.org/html/rfc7230#section-5.3">request target</a>.
     * @return a new {@link HttpRequestFactory}.
     */
    StreamingHttpRequest newRequest(HttpRequestMethod method, String requestTarget);

    // TODO(scott): if the new source is provided at creation, then we don't have to pay the costs of set(Publisher<>)
    // to manage flow control of both streams.
    // default StreamingHttpRequest newRequest(HttpRequestMethod method, String requestTarget) {
    //     return newRequest(method, requestTarget, empty());
    // }
    // StreamingHttpRequest newRequest(HttpRequestMethod method, String requestTarget, Publisher<Buffer> payloadBody);

    /**
     * Create a new {@link HttpRequestMethods#GET} request.
     * @param requestTarget The <a herf="https://tools.ietf.org/html/rfc7230#section-5.3">request target</a>.
     * @return a new {@link HttpRequestMethods#GET} request.
     */
    default StreamingHttpRequest get(String requestTarget) {
        return newRequest(GET, requestTarget);
    }

    /**
     * Create a new {@link HttpRequestMethods#POST} request.
     * @param requestTarget The <a herf="https://tools.ietf.org/html/rfc7230#section-5.3">request target</a>.
     * @return a new {@link HttpRequestMethods#POST} request.
     */
    default StreamingHttpRequest post(String requestTarget) {
        return newRequest(POST, requestTarget);
    }
}

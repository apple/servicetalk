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

import static io.servicetalk.http.api.HttpRequestMethod.CONNECT;
import static io.servicetalk.http.api.HttpRequestMethod.DELETE;
import static io.servicetalk.http.api.HttpRequestMethod.GET;
import static io.servicetalk.http.api.HttpRequestMethod.HEAD;
import static io.servicetalk.http.api.HttpRequestMethod.OPTIONS;
import static io.servicetalk.http.api.HttpRequestMethod.PATCH;
import static io.servicetalk.http.api.HttpRequestMethod.POST;
import static io.servicetalk.http.api.HttpRequestMethod.PUT;
import static io.servicetalk.http.api.HttpRequestMethod.TRACE;

/**
 * A factory for creating {@link BlockingStreamingHttpRequest}s.
 */
public interface BlockingStreamingHttpRequestFactory {
    /**
     * Create a new {@link HttpRequestFactory}.
     * @param method The {@link HttpRequestMethod}.
     * @param requestTarget The <a href="https://tools.ietf.org/html/rfc7230#section-5.3">request target</a>.
     * @return a new {@link HttpRequestFactory}.
     */
    BlockingStreamingHttpRequest newRequest(HttpRequestMethod method, String requestTarget);

    /**
     * Create a new {@link HttpRequestMethod#GET} request.
     * @param requestTarget The <a href="https://tools.ietf.org/html/rfc7230#section-5.3">request target</a>.
     * @return a new {@link HttpRequestMethod#GET} request.
     */
    default BlockingStreamingHttpRequest get(String requestTarget) {
        return newRequest(GET, requestTarget);
    }

    /**
     * Create a new {@link HttpRequestMethod#POST} request.
     * @param requestTarget The <a href="https://tools.ietf.org/html/rfc7230#section-5.3">request target</a>.
     * @return a new {@link HttpRequestMethod#POST} request.
     */
    default BlockingStreamingHttpRequest post(String requestTarget) {
        return newRequest(POST, requestTarget);
    }

    /**
     * Create a new {@link HttpRequestMethod#PUT} request.
     * @param requestTarget The <a href="https://tools.ietf.org/html/rfc7230#section-5.3">request target</a>.
     * @return a new {@link HttpRequestMethod#PUT} request.
     */
    default BlockingStreamingHttpRequest put(String requestTarget) {
        return newRequest(PUT, requestTarget);
    }

    /**
     * Create a new {@link HttpRequestMethod#OPTIONS} request.
     * @param requestTarget The <a href="https://tools.ietf.org/html/rfc7230#section-5.3">request target</a>.
     * @return a new {@link HttpRequestMethod#OPTIONS} request.
     */
    default BlockingStreamingHttpRequest options(String requestTarget) {
        return newRequest(OPTIONS, requestTarget);
    }

    /**
     * Create a new {@link HttpRequestMethod#HEAD} request.
     * @param requestTarget The <a href="https://tools.ietf.org/html/rfc7230#section-5.3">request target</a>.
     * @return a new {@link HttpRequestMethod#HEAD} request.
     */
    default BlockingStreamingHttpRequest head(String requestTarget) {
        return newRequest(HEAD, requestTarget);
    }

    /**
     * Create a new {@link HttpRequestMethod#TRACE} request.
     * @param requestTarget The <a href="https://tools.ietf.org/html/rfc7230#section-5.3">request target</a>.
     * @return a new {@link HttpRequestMethod#TRACE} request.
     */
    default BlockingStreamingHttpRequest trace(String requestTarget) {
        return newRequest(TRACE, requestTarget);
    }

    /**
     * Create a new {@link HttpRequestMethod#DELETE} request.
     * @param requestTarget The <a href="https://tools.ietf.org/html/rfc7230#section-5.3">request target</a>.
     * @return a new {@link HttpRequestMethod#DELETE} request.
     */
    default BlockingStreamingHttpRequest delete(String requestTarget) {
        return newRequest(DELETE, requestTarget);
    }

    /**
     * Create a new {@link HttpRequestMethod#PATCH} request.
     * @param requestTarget The <a href="https://tools.ietf.org/html/rfc7230#section-5.3">request target</a>.
     * @return a new {@link HttpRequestMethod#PATCH} request.
     */
    default BlockingStreamingHttpRequest patch(String requestTarget) {
        return newRequest(PATCH, requestTarget);
    }

    /**
     * Create a new {@link HttpRequestMethod#CONNECT} request.
     * @param requestTarget The <a href="https://tools.ietf.org/html/rfc7230#section-5.3">request target</a>.
     * @return a new {@link HttpRequestMethod#CONNECT} request.
     */
    default BlockingStreamingHttpRequest connect(String requestTarget) {
        return newRequest(CONNECT, requestTarget);
    }
}

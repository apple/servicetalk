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

import io.servicetalk.concurrent.api.Publisher;

import static io.servicetalk.concurrent.api.Executors.immediate;
import static io.servicetalk.concurrent.api.Publisher.empty;
import static io.servicetalk.concurrent.api.Publisher.just;
import static io.servicetalk.http.api.HttpProtocolVersions.HTTP_1_1;

/**
 * Factory methods for creating {@link HttpRequest}s.
 */
public final class HttpRequests {

    private HttpRequests() {
        // No instances
    }

    /**
     * Create a new instance using HTTP 1.1 with empty message body and headers.
     *
     * @param method the {@link HttpRequestMethod} of the request.
     * @param requestTarget the <a href="https://tools.ietf.org/html/rfc7230#section-3.1.1">request-target</a> of the request.
     * @param <I> Type of the content of the request.
     * @return a new {@link HttpRequest}.
     */
    public static <I> HttpRequest<I> newRequest(final HttpRequestMethod method, final String requestTarget) {
        return newRequest(HTTP_1_1, method, requestTarget);
    }

    /**
     * Create a new instance with empty message body and headers.
     *
     * @param version the {@link HttpProtocolVersion} of the request.
     * @param method the {@link HttpRequestMethod} of the request.
     * @param requestTarget the <a href="https://tools.ietf.org/html/rfc7230#section-3.1.1">request-target</a> of the request.
     * @param <I> Type of the content of the request.
     * @return a new {@link HttpRequest}.
     */
    public static <I> HttpRequest<I> newRequest(final HttpProtocolVersion version, final HttpRequestMethod method, final String requestTarget) {
        return newRequest(version, method, requestTarget, empty(immediate()));
    }

    /**
     * Create a new instance using HTTP 1.1 with empty headers.
     *
     * @param method the {@link HttpRequestMethod} of the request.
     * @param requestTarget the <a href="https://tools.ietf.org/html/rfc7230#section-3.1.1">request-target</a> of the request.
     * @param messageBody the message body of the request.
     * @param <I> Type of the content of the request.
     * @return a new {@link HttpRequest}.
     */
    public static <I> HttpRequest<I> newRequest(final HttpRequestMethod method, final String requestTarget, final I messageBody) {
        return newRequest(HTTP_1_1, method, requestTarget, messageBody);
    }

    /**
     * Create a new instance with empty headers.
     *
     * @param version the {@link HttpProtocolVersion} of the request.
     * @param method the {@link HttpRequestMethod} of the request.
     * @param requestTarget the <a href="https://tools.ietf.org/html/rfc7230#section-3.1.1">request-target</a> of the request.
     * @param messageBody the message body of the request.
     * @param <I> Type of the content of the request.
     * @return a new {@link HttpRequest}.
     */
    public static <I> HttpRequest<I> newRequest(final HttpProtocolVersion version, final HttpRequestMethod method, final String requestTarget, final I messageBody) {
        return newRequest(version, method, requestTarget, just(messageBody, immediate()));
    }

    /**
     * Create a new instance using HTTP 1.1 with empty headers.
     *
     * @param method the {@link HttpRequestMethod} of the request.
     * @param requestTarget the <a href="https://tools.ietf.org/html/rfc7230#section-3.1.1">request-target</a> of the request.
     * @param messageBody a {@link Publisher} of the message body of the request.
     * @param <I> Type of the content of the request.
     * @return a new {@link HttpRequest}.
     */
    public static <I> HttpRequest<I> newRequest(final HttpRequestMethod method, final String requestTarget, final Publisher<I> messageBody) {
        return newRequest(HTTP_1_1, method, requestTarget, messageBody);
    }

    /**
     * Create a new instance with empty headers.
     *
     * @param version the {@link HttpProtocolVersion} of the request.
     * @param method the {@link HttpRequestMethod} of the request.
     * @param requestTarget the <a href="https://tools.ietf.org/html/rfc7230#section-3.1.1">request-target</a> of the request.
     * @param messageBody a {@link Publisher} of the message body of the request.
     * @param <I> Type of the content of the request.
     * @return a new {@link HttpRequest}.
     */
    public static <I> HttpRequest<I> newRequest(final HttpProtocolVersion version, final HttpRequestMethod method, final String requestTarget, final Publisher<I> messageBody) {
        return newRequest(version, method, requestTarget, messageBody, DefaultHttpHeadersFactory.INSTANCE.newHeaders());
    }

    /**
     * Create a new instance.
     *
     * @param version the {@link HttpProtocolVersion} of the request.
     * @param method the {@link HttpRequestMethod} of the request.
     * @param requestTarget the <a href="https://tools.ietf.org/html/rfc7230#section-3.1.1">request-target</a> of the request.
     * @param messageBody a {@link Publisher} of the message body of the request.
     * @param headers the {@link HttpHeaders} of the request.
     * @param <I> Type of the content of the request.
     * @return a new {@link HttpRequest}.
     */
    public static <I> HttpRequest<I> newRequest(final HttpProtocolVersion version, final HttpRequestMethod method, final String requestTarget, final Publisher<I> messageBody, final HttpHeaders headers) {
        return new DefaultHttpRequest<>(method, requestTarget, version, messageBody, headers);
    }
}

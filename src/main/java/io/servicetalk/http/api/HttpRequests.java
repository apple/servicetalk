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
import io.servicetalk.concurrent.api.Single;

import static io.servicetalk.concurrent.api.Publisher.empty;
import static io.servicetalk.concurrent.api.Publisher.from;
import static io.servicetalk.concurrent.api.Publisher.just;
import static io.servicetalk.http.api.DefaultHttpHeadersFactory.INSTANCE;
import static io.servicetalk.http.api.HttpProtocolVersions.HTTP_1_1;

/**
 * Factory methods for creating {@link HttpRequest}s.
 */
public final class HttpRequests {

    private HttpRequests() {
        // No instances
    }

    /**
     * Create a new instance using HTTP 1.1 with empty payload body and headers.
     *
     * @param <I> Type of the payload of the request.
     * @param method the {@link HttpRequestMethod} of the request.
     * @param requestTarget the <a href="https://tools.ietf.org/html/rfc7230#section-3.1.1">request-target</a> of the
     * request.
     * @return a new {@link HttpRequest}.
     */
    public static <I> HttpRequest<I> newRequest(final HttpRequestMethod method,
                                                final String requestTarget) {
        return newRequest(HTTP_1_1, method, requestTarget);
    }

    /**
     * Create a new instance using HTTP 1.1 with empty payload body.
     *
     * @param <I> Type of the payload of the request.
     * @param method the {@link HttpRequestMethod} of the request.
     * @param requestTarget the <a href="https://tools.ietf.org/html/rfc7230#section-3.1.1">request-target</a> of the
     * request.
     * @param headers the {@link HttpHeaders} of the request.
     * @return a new {@link HttpRequest}.
     */
    public static <I> HttpRequest<I> newRequest(final HttpRequestMethod method,
                                                final String requestTarget,
                                                final HttpHeaders headers) {
        return newRequest(HTTP_1_1, method, requestTarget, headers);
    }

    /**
     * Create a new instance with empty payload body and headers.
     *
     * @param <I> Type of the payload of the request.
     * @param version the {@link HttpProtocolVersion} of the request.
     * @param method the {@link HttpRequestMethod} of the request.
     * @param requestTarget the <a href="https://tools.ietf.org/html/rfc7230#section-3.1.1">request-target</a> of the
     * request.
     * @return a new {@link HttpRequest}.
     */
    public static <I> HttpRequest<I> newRequest(final HttpProtocolVersion version,
                                                final HttpRequestMethod method,
                                                final String requestTarget) {
        return newRequest(version, method, requestTarget, empty());
    }

    /**
     * Create a new instance with empty payload body.
     *
     * @param <I> Type of the payload of the request.
     * @param version the {@link HttpProtocolVersion} of the request.
     * @param method the {@link HttpRequestMethod} of the request.
     * @param requestTarget the <a href="https://tools.ietf.org/html/rfc7230#section-3.1.1">request-target</a> of the
     * request.
     * @param headers the {@link HttpHeaders} of the request.
     * @return a new {@link HttpRequest}.
     */
    public static <I> HttpRequest<I> newRequest(final HttpProtocolVersion version,
                                                final HttpRequestMethod method,
                                                final String requestTarget,
                                                final HttpHeaders headers) {
        return newRequest(version, method, requestTarget, empty(), headers);
    }

    /**
     * Create a new instance using HTTP 1.1 with empty headers.
     *
     * @param <I> Type of the payload of the request.
     * @param method the {@link HttpRequestMethod} of the request.
     * @param requestTarget the <a href="https://tools.ietf.org/html/rfc7230#section-3.1.1">request-target</a> of the
     * request.
     * @param payloadBody the payload body of the request.
     * @return a new {@link HttpRequest}.
     */
    public static <I> HttpRequest<I> newRequest(final HttpRequestMethod method,
                                                final String requestTarget,
                                                final I payloadBody) {
        return newRequest(HTTP_1_1, method, requestTarget, payloadBody);
    }

    /**
     * Create a new instance using HTTP 1.1.
     *
     * @param <I> Type of the payload of the request.
     * @param method the {@link HttpRequestMethod} of the request.
     * @param requestTarget the <a href="https://tools.ietf.org/html/rfc7230#section-3.1.1">request-target</a> of the
     * request.
     * @param payloadBody the payload body of the request.
     * @param headers the {@link HttpHeaders} of the request.
     * @return a new {@link HttpRequest}.
     */
    public static <I> HttpRequest<I> newRequest(final HttpRequestMethod method,
                                                final String requestTarget,
                                                final I payloadBody,
                                                final HttpHeaders headers) {
        return newRequest(HTTP_1_1, method, requestTarget, payloadBody, headers);
    }

    /**
     * Create a new instance with empty headers.
     *
     * @param <I> Type of the payload of the request.
     * @param version the {@link HttpProtocolVersion} of the request.
     * @param method the {@link HttpRequestMethod} of the request.
     * @param requestTarget the <a href="https://tools.ietf.org/html/rfc7230#section-3.1.1">request-target</a> of the
     * request.
     * @param payloadBody the payload body of the request.
     * @return a new {@link HttpRequest}.
     */
    public static <I> HttpRequest<I> newRequest(final HttpProtocolVersion version,
                                                final HttpRequestMethod method,
                                                final String requestTarget,
                                                final I payloadBody) {
        return newRequest(version, method, requestTarget, just(payloadBody));
    }


    /**
     * Create a new instance.
     *
     * @param <I> Type of the payload of the request.
     * @param version the {@link HttpProtocolVersion} of the request.
     * @param method the {@link HttpRequestMethod} of the request.
     * @param requestTarget the <a href="https://tools.ietf.org/html/rfc7230#section-3.1.1">request-target</a> of the
     * request.
     * @param payloadBody the payload body of the request.
     * @param headers the {@link HttpHeaders} of the request.
     * @return a new {@link HttpRequest}.
     */
    public static <I> HttpRequest<I> newRequest(final HttpProtocolVersion version,
                                                final HttpRequestMethod method,
                                                final String requestTarget,
                                                final I payloadBody,
                                                final HttpHeaders headers) {
        return newRequest(version, method, requestTarget, just(payloadBody), headers);
    }

    /**
     * Create a new instance using HTTP 1.1 with empty headers.
     *
     * @param method the {@link HttpRequestMethod} of the request.
     * @param requestTarget the <a href="https://tools.ietf.org/html/rfc7230#section-3.1.1">request-target</a> of the
     * request.
     * @param payloadBody a {@link Single} of the payload body of the request.
     * @param <I> Type of the payload of the request.
     * @return a new {@link HttpRequest}.
     */
    public static <I> HttpRequest<I> newRequest(final HttpRequestMethod method,
                                                final String requestTarget,
                                                final Single<I> payloadBody) {
        return newRequest(HTTP_1_1, method, requestTarget, payloadBody);
    }


    /**
     * Create a new instance using HTTP 1.1.
     *
     * @param method the {@link HttpRequestMethod} of the request.
     * @param requestTarget the <a href="https://tools.ietf.org/html/rfc7230#section-3.1.1">request-target</a> of the
     * request.
     * @param payloadBody a {@link Single} of the payload body of the request.
     * @param headers the {@link HttpHeaders} of the request.
     * @param <I> Type of the payload of the request.
     * @return a new {@link HttpRequest}.
     */
    public static <I> HttpRequest<I> newRequest(final HttpRequestMethod method,
                                                final String requestTarget,
                                                final Single<I> payloadBody,
                                                final HttpHeaders headers) {
        return newRequest(HTTP_1_1, method, requestTarget, payloadBody, headers);
    }

    /**
     * Create a new instance with empty headers.
     *
     * @param version the {@link HttpProtocolVersion} of the request.
     * @param method the {@link HttpRequestMethod} of the request.
     * @param requestTarget the <a href="https://tools.ietf.org/html/rfc7230#section-3.1.1">request-target</a> of the
     * request.
     * @param payloadBody a {@link Single} of the payload body of the request.
     * @param <I> Type of the payload of the request.
     * @return a new {@link HttpRequest}.
     */
    public static <I> HttpRequest<I> newRequest(final HttpProtocolVersion version,
                                                final HttpRequestMethod method,
                                                final String requestTarget,
                                                final Single<I> payloadBody) {
        return newRequest(version, method, requestTarget, payloadBody, INSTANCE.newHeaders());
    }

    /**
     * Create a new instance.
     *
     * @param version the {@link HttpProtocolVersion} of the request.
     * @param method the {@link HttpRequestMethod} of the request.
     * @param requestTarget the <a href="https://tools.ietf.org/html/rfc7230#section-3.1.1">request-target</a> of the
     * request.
     * @param payloadBody a {@link Single} of the payload body of the request.
     * @param headers the {@link HttpHeaders} of the request.
     * @param <I> Type of the payload of the request.
     * @return a new {@link HttpRequest}.
     */
    public static <I> HttpRequest<I> newRequest(final HttpProtocolVersion version,
                                                final HttpRequestMethod method,
                                                final String requestTarget,
                                                final Single<I> payloadBody,
                                                final HttpHeaders headers) {
        return new DefaultHttpRequest<>(method, requestTarget, version, payloadBody.toPublisher(), headers);
    }

    /**
     * Create a new instance using HTTP 1.1 with empty headers.
     *
     * @param method the {@link HttpRequestMethod} of the request.
     * @param requestTarget the <a href="https://tools.ietf.org/html/rfc7230#section-3.1.1">request-target</a> of the
     * request.
     * @param payloadBody a {@link Publisher} of the payload body of the request.
     * @param <I> Type of the payload of the request.
     * @return a new {@link HttpRequest}.
     */
    public static <I> HttpRequest<I> newRequest(final HttpRequestMethod method,
                                                final String requestTarget,
                                                final Publisher<I> payloadBody) {
        return newRequest(HTTP_1_1, method, requestTarget, payloadBody);
    }


    /**
     * Create a new instance using HTTP 1.1.
     *
     * @param method the {@link HttpRequestMethod} of the request.
     * @param requestTarget the <a href="https://tools.ietf.org/html/rfc7230#section-3.1.1">request-target</a> of the
     * request.
     * @param payloadBody a {@link Publisher} of the payload body of the request.
     * @param headers the {@link HttpHeaders} of the request.
     * @param <I> Type of the payload of the request.
     * @return a new {@link HttpRequest}.
     */
    public static <I> HttpRequest<I> newRequest(final HttpRequestMethod method,
                                                final String requestTarget,
                                                final Publisher<I> payloadBody,
                                                final HttpHeaders headers) {
        return newRequest(HTTP_1_1, method, requestTarget, payloadBody, headers);
    }

    /**
     * Create a new instance with empty headers.
     *
     * @param version the {@link HttpProtocolVersion} of the request.
     * @param method the {@link HttpRequestMethod} of the request.
     * @param requestTarget the <a href="https://tools.ietf.org/html/rfc7230#section-3.1.1">request-target</a> of the
     * request.
     * @param payloadBody a {@link Publisher} of the payload body of the request.
     * @param <I> Type of the payload of the request.
     * @return a new {@link HttpRequest}.
     */
    public static <I> HttpRequest<I> newRequest(final HttpProtocolVersion version,
                                                final HttpRequestMethod method,
                                                final String requestTarget,
                                                final Publisher<I> payloadBody) {
        return newRequest(version, method, requestTarget, payloadBody, INSTANCE.newHeaders());
    }

    /**
     * Create a new instance.
     *
     * @param version the {@link HttpProtocolVersion} of the request.
     * @param method the {@link HttpRequestMethod} of the request.
     * @param requestTarget the <a href="https://tools.ietf.org/html/rfc7230#section-3.1.1">request-target</a> of the
     * request.
     * @param payloadBody a {@link Publisher} of the payload body of the request.
     * @param headers the {@link HttpHeaders} of the request.
     * @param <I> Type of the payload of the request.
     * @return a new {@link HttpRequest}.
     */
    public static <I> HttpRequest<I> newRequest(final HttpProtocolVersion version,
                                                final HttpRequestMethod method,
                                                final String requestTarget,
                                                final Publisher<I> payloadBody,
                                                final HttpHeaders headers) {
        return new DefaultHttpRequest<>(method, requestTarget, version, payloadBody, headers);
    }

    static <I> HttpRequest<I> fromBlockingRequest(BlockingHttpRequest<I> request) {
        return new DefaultHttpRequest<>(request.getMethod(),
                request.getRequestTarget(), request.getVersion(),
                // The from(..) operator will take care of propagating cancel.
                from(request.getPayloadBody()),
                request.getHeaders());
    }
}

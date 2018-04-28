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

import io.servicetalk.concurrent.api.Executor;
import io.servicetalk.concurrent.api.Executors;
import io.servicetalk.concurrent.api.Publisher;

import static io.servicetalk.concurrent.api.Publisher.empty;
import static io.servicetalk.concurrent.api.Publisher.from;
import static io.servicetalk.concurrent.api.Publisher.just;
import static io.servicetalk.http.api.DefaultHttpHeadersFactory.INSTANCE;
import static io.servicetalk.http.api.HttpProtocolVersions.HTTP_1_1;

/**
 * Factory methods for creating {@link BlockingHttpRequest}s.
 */
public final class BlockingHttpRequests {
    private BlockingHttpRequests() {
        // no instances
    }

    /**
     * Create a new instance using HTTP 1.1 with empty payload body and headers.
     *
     * @param method the {@link HttpRequestMethod} of the request.
     * @param requestTarget the <a href="https://tools.ietf.org/html/rfc7230#section-3.1.1">request-target</a> of the
     * request.
     * @param executor The {@link Executor} used to consume the empty payload. Note this is typically
     * consumed by ServiceTalk so if there are any blocking transformations (e.g. filters) and you are unsure if
     * ServiceTalk is consuming {@code payloadBody} it is wise to avoid {@link Executors#immediate()}.
     * @param <I> Type of the payload of the request.
     * @return a new {@link BlockingHttpRequest}.
     */
    public static <I> BlockingHttpRequest<I> newRequest(final HttpRequestMethod method,
                                                        final String requestTarget,
                                                        final Executor executor) {
        return newRequest(HTTP_1_1, method, requestTarget, executor);
    }

    /**
     * Create a new instance with empty payload body and headers.
     *
     * @param version the {@link HttpProtocolVersion} of the request.
     * @param method the {@link HttpRequestMethod} of the request.
     * @param requestTarget the <a href="https://tools.ietf.org/html/rfc7230#section-3.1.1">request-target</a> of the
     * request.
     * @param executor The {@link Executor} used to consume the empty payload. Note this is typically
     * consumed by ServiceTalk so if there are any blocking transformations (e.g. filters) and you are unsure if
     * ServiceTalk is consuming {@code payloadBody} it is wise to avoid {@link Executors#immediate()}.
     * @param <I> Type of the payload of the request.
     * @return a new {@link BlockingHttpResponse}.
     */
    public static <I> BlockingHttpRequest<I> newRequest(final HttpProtocolVersion version,
                                                        final HttpRequestMethod method,
                                                        final String requestTarget,
                                                        final Executor executor) {
        return newRequest(version, method, requestTarget, empty(executor));
    }

    /**
     * Create a new instance using HTTP 1.1 with empty headers.
     *
     * @param method the {@link HttpRequestMethod} of the request.
     * @param requestTarget the <a href="https://tools.ietf.org/html/rfc7230#section-3.1.1">request-target</a> of the
     * request.
     * @param payloadBody the payload body of the request.
     * @param executor The {@link Executor} used to consume data from {@code payloadBody}. Note this is typically
     * consumed by ServiceTalk so if there are any blocking transformations (e.g. filters) and you are unsure if
     * ServiceTalk is consuming {@code payloadBody} it is wise to avoid {@link Executors#immediate()}.
     * @param <I> Type of the payload of the request.
     * @return a new {@link BlockingHttpResponse}.
     */
    public static <I> BlockingHttpRequest<I> newRequest(final HttpRequestMethod method,
                                                        final String requestTarget,
                                                        final I payloadBody,
                                                        final Executor executor) {
        return newRequest(HTTP_1_1, method, requestTarget, payloadBody, executor);
    }

    /**
     * Create a new instance with empty headers.
     *
     * @param version the {@link HttpProtocolVersion} of the request.
     * @param method the {@link HttpRequestMethod} of the request.
     * @param requestTarget the <a href="https://tools.ietf.org/html/rfc7230#section-3.1.1">request-target</a> of the
     * request.
     * @param payloadBody the payload body of the request.
     * @param executor The {@link Executor} used to consume data from {@code payloadBody}. Note this is typically
     * consumed by ServiceTalk so if there are any blocking transformations (e.g. filters) and you are unsure if
     * ServiceTalk is consuming {@code payloadBody} it is wise to avoid {@link Executors#immediate()}.
     * @param <I> Type of the payload of the request.
     * @return a new {@link BlockingHttpResponse}.
     */
    public static <I> BlockingHttpRequest<I> newRequest(final HttpProtocolVersion version,
                                                        final HttpRequestMethod method,
                                                        final String requestTarget,
                                                        final I payloadBody,
                                                        final Executor executor) {
        return newRequest(version, method, requestTarget, just(payloadBody, executor));
    }

    /**
     * Create a new instance using HTTP 1.1 with empty headers.
     *
     * @param method the {@link HttpRequestMethod} of the request.
     * @param requestTarget the <a href="https://tools.ietf.org/html/rfc7230#section-3.1.1">request-target</a> of the
     * request.
     * @param payloadBody a {@link Iterable} of the payload body of the request.
     * @param executor The {@link Executor} used to consume data from {@code payloadBody}. Note this is typically
     * consumed by ServiceTalk so if there are any blocking transformations (e.g. filters) and you are unsure if
     * ServiceTalk is consuming {@code payloadBody} it is wise to avoid {@link Executors#immediate()}.
     * @param <I> Type of the payload of the request.
     * @return a new {@link BlockingHttpResponse}.
     */
    public static <I> BlockingHttpRequest<I> newRequest(final HttpRequestMethod method,
                                                        final String requestTarget,
                                                        final Iterable<I> payloadBody,
                                                        final Executor executor) {
        return newRequest(HTTP_1_1, method, requestTarget, payloadBody, executor);
    }

    /**
     * Create a new instance with empty headers.
     *
     * @param version the {@link HttpProtocolVersion} of the request.
     * @param method the {@link HttpRequestMethod} of the request.
     * @param requestTarget the <a href="https://tools.ietf.org/html/rfc7230#section-3.1.1">request-target</a> of the request.
     * @param payloadBody a {@link Iterable} of the payload body of the request.
     * @param executor The {@link Executor} used to consume data from {@code payloadBody}. Note this is typically
     * consumed by ServiceTalk so if there are any blocking transformations (e.g. filters) and you are unsure if
     * ServiceTalk is consuming {@code payloadBody} it is wise to avoid {@link Executors#immediate()}.
     * @param <I> Type of the payload of the request.
     * @return a new {@link BlockingHttpResponse}.
     */
    public static <I> BlockingHttpRequest<I> newRequest(final HttpProtocolVersion version,
                                                        final HttpRequestMethod method,
                                                        final String requestTarget,
                                                        final Iterable<I> payloadBody,
                                                        final Executor executor) {
        return newRequest(version, method, requestTarget, payloadBody, executor, INSTANCE.newHeaders());
    }

    /**
     * Create a new instance.
     *
     * @param version the {@link HttpProtocolVersion} of the request.
     * @param method the {@link HttpRequestMethod} of the request.
     * @param requestTarget the <a href="https://tools.ietf.org/html/rfc7230#section-3.1.1">request-target</a> of the
     * request.
     * @param payloadBody a {@link Iterable} of the payload body of the request.
     * @param executor The {@link Executor} used to consume data from {@code payloadBody}. Note this is typically
     * consumed by ServiceTalk so if there are any blocking transformations (e.g. filters) and you are unsure if
     * ServiceTalk is consuming {@code payloadBody} it is wise to avoid {@link Executors#immediate()}.
     * @param headers the {@link HttpHeaders} of the request.
     * @param <I> Type of the payload of the request.
     * @return a new {@link BlockingHttpResponse}.
     */
    public static <I> BlockingHttpRequest<I> newRequest(final HttpProtocolVersion version,
                                                        final HttpRequestMethod method,
                                                        final String requestTarget,
                                                        final Iterable<I> payloadBody,
                                                        final Executor executor,
                                                        final HttpHeaders headers) {
        return newRequest(version, method, requestTarget, from(executor, payloadBody), headers);
    }

    private static <I> BlockingHttpRequest<I> newRequest(final HttpProtocolVersion version,
                                                         final HttpRequestMethod method,
                                                         final String requestTarget,
                                                         final Publisher<I> payloadBody) {
        return newRequest(version, method, requestTarget, payloadBody, INSTANCE.newHeaders());
    }

    private static <I> BlockingHttpRequest<I> newRequest(final HttpProtocolVersion version,
                                                         final HttpRequestMethod method,
                                                         final String requestTarget,
                                                         final Publisher<I> payloadBody,
                                                         final HttpHeaders headers) {
        return new DefaultBlockingHttpRequest<>(HttpRequests.newRequest(
                version, method, requestTarget, payloadBody, headers));
    }
}

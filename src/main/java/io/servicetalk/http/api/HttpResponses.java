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
 * Factory methods for creating {@link HttpResponse}s.
 */
public final class HttpResponses {

    private HttpResponses() {
        // No instances
    }

    /**
     * Create a new instance using HTTP 1.1 with empty message body and headers.
     *
     * @param status the {@link HttpResponseStatus} of the response.
     * @param <O> Type of the content of the response.
     * @return a new {@link HttpResponse}.
     */
    public static <O> HttpResponse<O> newResponse(final HttpResponseStatus status) {
        return newResponse(HTTP_1_1, status);
    }

    /**
     * Create a new instance with empty message body and headers.
     *
     * @param version the {@link HttpProtocolVersion} of the response.
     * @param status the {@link HttpResponseStatus} of the response.
     * @param <O> Type of the content of the response.
     * @return a new {@link HttpResponse}.
     */
    public static <O> HttpResponse<O> newResponse(final HttpProtocolVersion version, final HttpResponseStatus status) {
        return newResponse(version, status, empty(immediate()));
    }

    /**
     * Create a new instance using HTTP 1.1 with empty headers.
     *
     * @param status the {@link HttpResponseStatus} of the response.
     * @param messageBody the message body of the response.
     * @param <O> Type of the content of the response.
     * @return a new {@link HttpResponse}.
     */
    public static <O> HttpResponse<O> newResponse(final HttpResponseStatus status, final O messageBody) {
        return newResponse(HTTP_1_1, status, messageBody);
    }

    /**
     * Create a new instance with empty headers.
     *
     * @param version the {@link HttpProtocolVersion} of the response.
     * @param status the {@link HttpResponseStatus} of the response.
     * @param messageBody the message body of the response.
     * @param <O> Type of the content of the response.
     * @return a new {@link HttpResponse}.
     */
    public static <O> HttpResponse<O> newResponse(final HttpProtocolVersion version, final HttpResponseStatus status, final O messageBody) {
        return newResponse(version, status, just(messageBody, immediate()));
    }

    /**
     * Create a new instance using HTTP 1.1 with empty headers.
     *
     * @param status the {@link HttpResponseStatus} of the response.
     * @param messageBody a {@link Publisher} of the message body of the response.
     * @param <O> Type of the content of the response.
     * @return a new {@link HttpResponse}.
     */
    public static <O> HttpResponse<O> newResponse(final HttpResponseStatus status, final Publisher<O> messageBody) {
        return newResponse(HTTP_1_1, status, messageBody);
    }

    /**
     * Create a new instance with empty headers.
     *
     * @param status the {@link HttpResponseStatus} of the response.
     * @param version the {@link HttpProtocolVersion} of the response.
     * @param messageBody a {@link Publisher} of the message body of the response.
     * @param <O> Type of the content of the response.
     * @return a new {@link HttpResponse}.
     */
    public static <O> HttpResponse<O> newResponse(final HttpProtocolVersion version, final HttpResponseStatus status, final Publisher<O> messageBody) {
        return newResponse(version, status, messageBody, DefaultHttpHeadersFactory.INSTANCE.newHeaders());
    }

    /**
     * Create a new instance.
     *
     * @param version the {@link HttpProtocolVersion} of the response.
     * @param status the {@link HttpResponseStatus} of the response.
     * @param messageBody a {@link Publisher} of the message body of the response.
     * @param headers the {@link HttpHeaders} of the response.
     * @param <O> Type of the content of the response.
     * @return a new {@link HttpResponse}.
     */
    public static <O> HttpResponse<O> newResponse(final HttpProtocolVersion version, final HttpResponseStatus status, final Publisher<O> messageBody, final HttpHeaders headers) {
        return new DefaultHttpResponse<>(status, version, headers, messageBody);
    }
}

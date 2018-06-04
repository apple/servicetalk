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

import java.util.function.Function;

/**
 * The equivalent of {@link HttpResponse} but with an aggregated content instead of a {@link Publisher} as returned by
 * {@link HttpResponse#getPayloadBody()}.
 *
 * @param <T> Type of payload.
 */
public interface AggregatedHttpResponse<T> extends HttpResponseMetaData, LastHttpMetaData {
    /**
     * The <a href="https://tools.ietf.org/html/rfc7230.html#section-3.3">HTTP Payload Body</a>.
     *
     * @return The <a href="https://tools.ietf.org/html/rfc7230.html#section-3.3">HTTP Payload Body</a> of this
     * response.
     */
    T getPayloadBody();

    /**
     * To modify the {@link #getPayloadBody()} of the request and preserving the containing request object.
     *
     * @param transformer {@link Function} which converts the payload body to another type.
     * @param <R> Type of the resulting payload body.
     * @return New {@link AggregatedHttpResponse} with the altered {@link #getPayloadBody()}.
     */
    <R> AggregatedHttpResponse<R> transformPayloadBody(Function<T, R> transformer);

    @Override
    AggregatedHttpResponse<T> setVersion(HttpProtocolVersion version);

    @Override
    AggregatedHttpResponse<T> setStatus(HttpResponseStatus status);
}

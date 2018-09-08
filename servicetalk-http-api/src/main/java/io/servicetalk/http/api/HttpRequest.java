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

import java.util.function.Function;

/**
 * An HTTP request. Note that the entire payload will be in memory.
 *
 * @param <T> Type of payload.
 */
public interface HttpRequest<T> extends HttpRequestMetaData, LastHttpMetaData {
    /**
     * The <a href="https://tools.ietf.org/html/rfc7230.html#section-3.3">HTTP Payload Body</a>.
     *
     * @return The <a href="https://tools.ietf.org/html/rfc7230.html#section-3.3">HTTP Payload Body</a> of this request.
     */
    T getPayloadBody();

    /**
     * To modify the {@link #getPayloadBody()} of the request and preserving the containing request object.
     *
     * @param transformer {@link Function} which converts the payload body to another type.
     * @param <R> Type of the resulting payload body.
     * @return New {@link HttpRequest} with the altered {@link #getPayloadBody()}.
     */
    <R> HttpRequest<R> transformPayloadBody(Function<T, R> transformer);

    @Override
    HttpRequest<T> setRawPath(String path);

    @Override
    HttpRequest<T> setPath(String path);

    @Override
    HttpRequest<T> setRawQuery(String query);

    @Override
    HttpRequest<T> setVersion(HttpProtocolVersion version);

    @Override
    HttpRequest<T> setMethod(HttpRequestMethod method);

    @Override
    HttpRequest<T> setRequestTarget(String requestTarget);
}

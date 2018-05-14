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

import org.reactivestreams.Subscriber;

import java.util.function.Function;

/**
 * HTTP request that provides the payload as a {@link Publisher}.
 *
 * <h2>Trailing headers</h2>
 * Trailing headers can be obtained from a request if the type of the payload is {@link HttpPayloadChunk}.
 * In such a case, the last element in the stream would be {@link LastHttpPayloadChunk} which contains the trailing
 * headers, if any.
 *
 * @param <T> Type of payload.
 */
public interface HttpRequest<T> extends HttpRequestMetaData {
    /**
     * The <a href="https://tools.ietf.org/html/rfc7230.html#section-3.3">HTTP Payload Body</a>.
     * <p>
     * By default the returned {@link Publisher} only supports a single call to {@link Publisher#subscribe(Subscriber)}.
     * This is because the payload is typically not all available in memory at any given time. If you need multiple
     * calls to {@link Publisher#subscribe(Subscriber)} you should add support for multiple {@link Subscriber}s and
     * consider adding support for caching data in memory. See the {@link Publisher#multicast(int) Multicast Operator}
     * and the <a href="http://reactivex.io/documentation/operators/replay.html">Replay Operator</a> for more details.
     * @return {@link Publisher} that emits the
     * <a href="https://tools.ietf.org/html/rfc7230.html#section-3.3">HTTP Payload Body</a> of this request.
     */
    Publisher<T> getPayloadBody();

    /**
     * To modify the {@link #getPayloadBody()} of the request and preserving the containing request object.
     *
     * @param transformer {@link Function} which converts the payload body to another type.
     * @param <R> Type of the resulting payload body.
     * @return New {@code HttpRequest} with the altered {@link #getPayloadBody()}.
     */
    <R> HttpRequest<R> transformPayloadBody(Function<Publisher<T>, Publisher<R>> transformer);

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

/**
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
 * HTTP response that provides the content as a stream.
 *
 * <h2>Trailing headers</h2>
 * Trailing headers can be obtained from a response if the type of the content is {@link HttpPayloadChunk}.
 * In such a case, the last element in the stream would be {@link LastHttpPayloadChunk} which contains the trailing headers, if any.
 *
 * @param <T> Type of content.
 */
public interface HttpResponse<T> extends HttpResponseMetaData {
    /**
     * The <a href="https://tools.ietf.org/html/rfc7230.html#section-3.3">HTTP Message Body</a>.
     *
     * @return {@link Publisher} that emits the
     * <a href="https://tools.ietf.org/html/rfc7230.html#section-3.3">HTTP Message Body</a> of this request.
     */
    Publisher<T> getMessageBody();

    /**
     * To modify the {@link #getMessageBody()} of the response and preserving the containing request object.
     *
     * @param transformer {@link Function} which converts the message body to another type.
     * @param <R> Type of the resulting message body.
     * @return New {@code HttpResponse} with the altered {@link #getMessageBody()}.
     */
    <R> HttpResponse<R> transformMessageBody(Function<Publisher<T>, Publisher<R>> transformer);
}

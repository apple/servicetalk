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

import io.servicetalk.buffer.api.Buffer;
import io.servicetalk.concurrent.api.Publisher;

/**
 * The equivalent of {@link HttpResponse} but with an aggregated content instead of a {@link Publisher} as returned by
 * {@link HttpResponse#getPayloadBody()}.
 */
public interface FullHttpResponse extends HttpResponseMetaData, LastHttpPayloadChunk {
    /**
     * The <a href="https://tools.ietf.org/html/rfc7230.html#section-3.3">HTTP Payload Body</a>.
     *
     * @return The <a href="https://tools.ietf.org/html/rfc7230.html#section-3.3">HTTP Payload Body</a> of this
     * response.
     */
    Buffer getPayloadBody();

    /**
     * Get the <a href="https://tools.ietf.org/html/rfc7230#section-4.4">trailer headers</a>.
     *
     * @return the <a href="https://tools.ietf.org/html/rfc7230#section-4.4">trailer headers</a>.
     */
    @Override
    HttpHeaders getTrailers();

    @Override
    default Buffer getContent() {
        return getPayloadBody();
    }

    /**
     * Duplicates this {@link FullHttpResponse}.
     *
     * @return Duplicates this {@link FullHttpResponse}.
     */
    @Override
    FullHttpResponse duplicate();

    /**
     * Returns a new {@link FullHttpResponse} which contains the specified {@code content}.
     *
     * @param content The {@link Buffer} to replace what is currently returned by {@link #getContent()}.
     * @return a new {@link FullHttpResponse} which contains the specified {@code content}.
     */
    @Override
    FullHttpResponse replace(Buffer content);

    @Override
    FullHttpResponse setVersion(HttpProtocolVersion version);

    @Override
    FullHttpResponse setStatus(HttpResponseStatus status);
}

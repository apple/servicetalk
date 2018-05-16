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
 * The equivalent of {@link HttpRequest} but with an aggregated content instead of a {@link Publisher} as returned by
 * {@link HttpRequest#getPayloadBody()}.
 */
public interface FullHttpRequest extends HttpRequestMetaData, LastHttpPayloadChunk {
    /**
     * The <a href="https://tools.ietf.org/html/rfc7230.html#section-3.3">HTTP Payload Body</a>.
     *
     * @return The <a href="https://tools.ietf.org/html/rfc7230.html#section-3.3">HTTP Payload Body</a> of this request.
     */
    Buffer getPayloadBody();

    @Override
    default Buffer getContent() {
        return getPayloadBody();
    }

    /**
     * Get the <a href="https://tools.ietf.org/html/rfc7230#section-4.4">trailer headers</a>.
     *
     * @return the <a href="https://tools.ietf.org/html/rfc7230#section-4.4">trailer headers</a>.
     */
    @Override
    HttpHeaders getTrailers();

    /**
     * Duplicates this {@link FullHttpRequest}.
     *
     * @return Duplicates this {@link FullHttpRequest}.
     */
    @Override
    FullHttpRequest duplicate();

    /**
     * Returns a new {@link FullHttpRequest} which contains the specified {@code content}.
     *
     * @param content The {@link Buffer} to replace what is currently returned by {@link #getContent()}.
     * @return a new {@link FullHttpRequest} which contains the specified {@code content}.
     */
    @Override
    FullHttpRequest replace(Buffer content);

    @Override
    FullHttpRequest setRawPath(String path);

    @Override
    FullHttpRequest setPath(String path);

    @Override
    FullHttpRequest setRawQuery(String query);

    @Override
    FullHttpRequest setVersion(HttpProtocolVersion version);

    @Override
    FullHttpRequest setMethod(HttpRequestMethod method);

    @Override
    FullHttpRequest setRequestTarget(String requestTarget);
}

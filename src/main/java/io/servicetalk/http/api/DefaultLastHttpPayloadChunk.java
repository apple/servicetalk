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

import io.servicetalk.buffer.Buffer;

import static java.util.Objects.requireNonNull;

/**
 * The default implementation of {@link LastHttpPayloadChunk}.
 */
final class DefaultLastHttpPayloadChunk extends DefaultHttpPayloadChunk implements LastHttpPayloadChunk {

    private final HttpHeaders trailers;

    /**
     * Create a {@link DefaultHttpPayloadChunk} which has contents that are the specified {@link Buffer} and the
     * specified the <a href="https://tools.ietf.org/html/rfc7230#section-4.4">trailer headers</a>.
     *
     * @param content {@link Buffer} payload.
     * @param trailers the <a href="https://tools.ietf.org/html/rfc7230#section-4.4">trailer headers</a>.
     */
    DefaultLastHttpPayloadChunk(final Buffer content, final HttpHeaders trailers) {
        super(content);
        this.trailers = requireNonNull(trailers);
    }

    @Override
    public HttpHeaders getTrailers() {
        return trailers;
    }

    @Override
    public LastHttpPayloadChunk duplicate() {
        return new DefaultLastHttpPayloadChunk(getContent().duplicate(), trailers);
    }

    @Override
    public LastHttpPayloadChunk replace(final Buffer content) {
        return new DefaultLastHttpPayloadChunk(content, trailers);
    }
}

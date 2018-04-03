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
import io.servicetalk.buffer.BufferHolder;

/**
 * A chunk of the <a href="https://tools.ietf.org/html/rfc7230.html#section-3.3">HTTP Message Body</a>.
 */
public interface HttpPayloadChunk extends BufferHolder {
    /**
     * Returns the content of this chunk.
     *
     * @return Buffer containing the content of the chunk.
     */
    Buffer getContent();

    /**
     * Duplicates the content of this {@link HttpPayloadChunk} as returned by {@link #getContent()}.
     * <p>
     * Typically the buffer is duplicated using {@link Buffer#duplicate()}.
     * @return a new instance of {@link HttpPayloadChunk} with duplicated content.
     */
    HttpPayloadChunk duplicate();

    /**
     * Returns a new {@link HttpPayloadChunk} which contains the specified {@code content}.
     * @param content The {@link Buffer} to replace what is currently returned by {@link #getContent()}.
     * @return a new {@link HttpPayloadChunk} which contains the specified {@code content}.
     */
    HttpPayloadChunk replace(Buffer content);
}

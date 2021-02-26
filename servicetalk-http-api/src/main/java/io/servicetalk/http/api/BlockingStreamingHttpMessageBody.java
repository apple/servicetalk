/*
 * Copyright © 2021 Apple Inc. and the ServiceTalk project authors
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

import io.servicetalk.concurrent.BlockingIterator;

import javax.annotation.Nullable;

/**
 * Get the <a href="https://tools.ietf.org/html/rfc7230#section-3.3">message-body</a>.
 * @param <T> The type of the payload body.
 */
public interface BlockingStreamingHttpMessageBody<T> {
    /**
     * Get the payload body associated with this message.
     * @return the payload body associated with this message.
     */
    BlockingIterator<T> payloadBody();

    /**
     * Get the <a href="https://tools.ietf.org/html/rfc7230#section-4.1.2">trailers</a> associated with this message
     * body. Will return {@code null} until {@link #payloadBody()} has been completely consumed and
     * {@link BlockingIterator#hasNext()} returns {@code false}. Even after consumption of {@link #payloadBody()} this
     * method may still return {@code null} if there are no trailers.
     * @return the <a href="https://tools.ietf.org/html/rfc7230#section-4.1.2">trailer</a> associated with this message
     * body. Will return {@code null} until {@link #payloadBody()} has been completely consumed and
     * {@link BlockingIterator#hasNext()} returns {@code false}. Even after consumption of {@link #payloadBody()} this
     * method may still return {@code null} if there are no trailers.
     */
    @Nullable
    HttpHeaders trailers();
}

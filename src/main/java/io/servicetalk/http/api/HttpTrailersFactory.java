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

/**
 * Factory methods for creating {@link HttpHeaders} for <a href="https://tools.ietf.org/html/rfc7230#section-4.4">trailer headers</a>.
 */
public interface HttpTrailersFactory {
    /**
     * Create an {@link HttpHeaders} instance.
     *
     * @return an {@link HttpHeaders} instance.
     */
    HttpHeaders newTrailers();

    /**
     * Create an {@link HttpHeaders} instance, possibly optimized for being empty.
     * <p>
     * Note: this should not return an immutable instance unless it is known that no code will need to mutate the
     * trailers.
     *
     * @return an {@link HttpHeaders} instance.
     */
    default HttpHeaders newEmptyTrailers() {
        return newTrailers();
    }
}

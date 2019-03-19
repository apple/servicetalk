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
 * An object which holds HTTP <a href="https://tools.ietf.org/html/rfc7230#section-4.4">trailers</a>.
 */
interface TrailersHolder {

    /**
     * Gets the <a href="https://tools.ietf.org/html/rfc7230#section-4.4">trailers</a>.
     *
     * @return the <a href="https://tools.ietf.org/html/rfc7230#section-4.4">trailers</a>.
     */
    HttpHeaders trailers();

    /**
     * Adds a new trailer with the specified {@code name} and {@code value}.
     *
     * @param name the name of the trailer.
     * @param value the value of the trailer.
     * @return {@code this}.
     */
    default TrailersHolder addTrailer(final CharSequence name, final CharSequence value) {
        trailers().add(name, value);
        return this;
    }

    /**
     * Adds all trailer names and values of {@code trailer} object.
     *
     * @param trailers the trailers to add.
     * @return {@code this}.
     */
    default TrailersHolder addTrailers(final HttpHeaders trailers) {
        trailers().add(trailers);
        return this;
    }

    /**
     * Sets a trailer with the specified {@code name} and {@code value}. Any existing trailers with the same name are
     * overwritten.
     *
     * @param name the name of the trailer.
     * @param value the value of the trailer.
     * @return {@code this}.
     */
    default TrailersHolder setTrailer(final CharSequence name, final CharSequence value) {
        trailers().set(name, value);
        return this;
    }

    /**
     * Clears the current trailer entries and copies all trailer entries of the specified {@code trailers} object.
     *
     * @param trailers the trailers object which contains new values.
     * @return {@code this}.
     */
    default TrailersHolder setTrailers(final HttpHeaders trailers) {
        trailers().set(trailers);
        return this;
    }
}

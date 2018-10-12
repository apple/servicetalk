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

import java.util.function.BiFunction;

/**
 * Meta data shared between requests and responses.
 */
public interface HttpMetaData {

    /**
     * Returns the protocol version of this {@link HttpMetaData}.
     *
     * @return the version.
     */
    HttpProtocolVersion version();

    /**
     * Set the protocol version of this {@link HttpMetaData}.
     *
     * @param version the protocol version to set.
     * @return {@code this}.
     */
    HttpMetaData version(HttpProtocolVersion version);

    /**
     * Returns the headers of this message.
     *
     * @return the headers.
     */
    HttpHeaders headers();

    /**
     * Adds a new header with the specified {@code name} and {@code value}.
     *
     * @param name the name of the header.
     * @param value the value of the header.
     * @return {@code this}.
     */
    HttpMetaData addHeaderField(CharSequence name, CharSequence value);

    /**
     * Sets a header with the specified {@code name} and {@code value}. Any existing headers with the same name are
     * overwritten.
     *
     * @param name the header name.
     * @param value the value of the header.
     * @return {@code this}.
     */
    HttpMetaData setHeaderField(CharSequence name, CharSequence value);

    /**
     * Returns a string representation of the message. To avoid accidentally logging sensitive information,
     * implementations should not return any header or content.
     *
     * @return a string representation of the message
     */
    @Override
    String toString();

    /**
     * Returns a string representation of the message and headers.
     *
     * @param headerFilter a function that accepts the header name and value and returns the filtered value
     * @return string representation of the message and headers
     */
    String toString(BiFunction<? super CharSequence, ? super CharSequence, CharSequence> headerFilter);
}

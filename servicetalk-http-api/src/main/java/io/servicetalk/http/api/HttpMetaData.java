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
import javax.annotation.Nullable;

import static java.lang.System.lineSeparator;

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
     * The {@link StreamingContentCodec} used to encode the payload of a request or a response.
     * If the endpoint is setup with {@link HttpServerBuilder#supportedResponseEncodings(StreamingContentCodec...)} ()}
     * or {@link HttpClientBuilder#supportedEncodings(StreamingContentCodec...)}, the server will
     * auto-establish the accepted encoding for the response, unless the caller provides a specific encoding
     * calling this method.
     *
     * Any encoding passed here, takes precedence. In other words, a compressed response, can
     * be disabled by passing {@link ContentCodings#identity()}.
     *
     * @param encoding The {@link StreamingContentCodec} used for the encoding of the payload.
     * @return {@code this}.
     * @see <a href="https://tools.ietf.org/html/rfc7231#section-3.1.2.2">Content-Encoding</a>
     */
    HttpMetaData encoding(StreamingContentCodec encoding);

    /**
     * Returns the {@link StreamingContentCodec} used to encode the payload of a request or a response.
     *
     * @return The {@link StreamingContentCodec} used for the encoding of the payload.
     * @see <a href="https://tools.ietf.org/html/rfc7231#section-3.1.2.2">Content-Encoding</a>
     */
    @Nullable
    StreamingContentCodec encoding();

    /**
     * Adds a new header with the specified {@code name} and {@code value}.
     *
     * @param name the name of the header.
     * @param value the value of the header.
     * @return {@code this}.
     */
    default HttpMetaData addHeader(final CharSequence name, final CharSequence value) {
        headers().add(name, value);
        return this;
    }

    /**
     * Adds all header names and values of {@code headers} object.
     *
     * @param headers the headers to add.
     * @return {@code this}.
     */
    default HttpMetaData addHeaders(final HttpHeaders headers) {
        headers().add(headers);
        return this;
    }

    /**
     * Sets a header with the specified {@code name} and {@code value}. Any existing headers with the same name are
     * overwritten.
     *
     * @param name the name of the header.
     * @param value the value of the header.
     * @return {@code this}.
     */
    default HttpMetaData setHeader(final CharSequence name, final CharSequence value) {
        headers().set(name, value);
        return this;
    }

    /**
     * Clears the current header entries and copies all header entries of the specified {@code headers} object.
     *
     * @param headers the headers object which contains new values.
     * @return {@code this}.
     */
    default HttpMetaData setHeaders(final HttpHeaders headers) {
        headers().set(headers);
        return this;
    }

    /**
     * Adds a <a href="https://tools.ietf.org/html/rfc6265#section-4.2">cookie</a>.
     * <p>
     * This may result in multiple {@link HttpCookiePair}s with same name.
     *
     * @param cookie the cookie to add.
     * @return {@code this}.
     */
    default HttpMetaData addCookie(final HttpCookiePair cookie) {
        headers().addCookie(cookie);
        return this;
    }

    /**
     * Adds a <a href="https://tools.ietf.org/html/rfc6265#section-4.2">cookie</a> with the specified {@code name} and
     * {@code value}.
     * <p>
     * This may result in multiple {@link HttpSetCookie}s with same name. Added cookie will not be wrapped, not secure,
     * and not HTTP-only, with no path, domain, expire date and maximum age.
     *
     * @param name the name of the cookie.
     * @param value the value of the cookie.
     * @return {@code this}.
     */
    default HttpMetaData addCookie(final CharSequence name, final CharSequence value) {
        headers().addCookie(name, value);
        return this;
    }

    /**
     * Adds a <a href="https://tools.ietf.org/html/rfc6265#section-4.1">set-cookie</a>.
     * <p>
     * This may result in multiple {@link HttpSetCookie}s with same name.
     *
     * @param cookie the cookie to add.
     * @return {@code this}.
     */
    default HttpMetaData addSetCookie(final HttpSetCookie cookie) {
        headers().addSetCookie(cookie);
        return this;
    }

    /**
     * Adds a <a href="https://tools.ietf.org/html/rfc6265#section-4.1">set-cookie</a> with the specified {@code name}
     * and {@code value}.
     * <p>
     * This may result in multiple {@link HttpSetCookie}s with same name. Added cookie will not be wrapped, not secure,
     * and not HTTP-only, with no path, domain, expire date and maximum age.
     *
     * @param name the name of the cookie.
     * @param value the value of the cookie.
     * @return {@code this}.
     */
    default HttpMetaData addSetCookie(final CharSequence name, final CharSequence value) {
        headers().addSetCookie(name, value);
        return this;
    }

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
    default String toString(BiFunction<? super CharSequence, ? super CharSequence, CharSequence> headerFilter) {
        return toString() + lineSeparator()
                + headers().toString(headerFilter);
    }
}

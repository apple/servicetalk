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
 * Meta data associated with an HTTP response.
 * This includes pieces form the <a href="https://tools.ietf.org/html/rfc7230.html#section-3.1.2">status line</a> and
 * other meta data from {@link HttpMetaData}.
 */
public interface HttpResponseMetaData extends HttpMetaData {
    /**
     * Returns the status of this {@link StreamingHttpResponse}.
     *
     * @return The {@link HttpResponseStatus} of this {@link StreamingHttpResponse}
     */
    HttpResponseStatus status();

    /**
     * Set the status of this {@link StreamingHttpResponse}.
     *
     * @param status The {@link HttpResponseStatus} to set.
     * @return {@code this}.
     */
    HttpResponseMetaData status(HttpResponseStatus status);

    @Override
    HttpResponseMetaData version(HttpProtocolVersion version);

    @Override
    HttpResponseMetaData addHeader(CharSequence name, CharSequence value);

    @Override
    HttpResponseMetaData addHeaders(HttpHeaders headers);

    @Override
    HttpResponseMetaData setHeader(CharSequence name, CharSequence value);

    @Override
    HttpResponseMetaData setHeaders(HttpHeaders headers);

    @Override
    HttpResponseMetaData addCookie(HttpCookie cookie);

    @Override
    HttpResponseMetaData addCookie(CharSequence name, CharSequence value);

    @Override
    HttpResponseMetaData addSetCookie(HttpCookie cookie);

    @Override
    HttpResponseMetaData addSetCookie(CharSequence name, CharSequence value);
}

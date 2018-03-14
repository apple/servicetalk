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

import java.util.Iterator;

public interface HttpRequestMetaData extends HttpMetaData {
    /**
     * Returns the {@link HttpRequestMethod} of this {@link HttpRequest}.
     *
     * @return The {@link HttpRequestMethod} of this {@link HttpRequest}
     */
    HttpRequestMethod getMethod();

    /**
     * The <a href="https://tools.ietf.org/html/rfc7230#section-3.1.1">request-target</a>.
     * <p>
     * No decoding has been done on the request-target.
     * @return The <a href="https://tools.ietf.org/html/rfc7230#section-3.1.1">request-target</a>.
     * <p>
     * No decoding has been done on the request-target.
     */
    String getRequestTarget();

    /**
     * The <a href="https://tools.ietf.org/html/rfc3986#section-3.3">path component</a> derived from {@link #getRequestTarget()}.
     * @return The <a href="https://tools.ietf.org/html/rfc3986#section-3.3">path</a> derived from {@link #getRequestTarget()}.
     */
    String getRawPath();

    /**
     * Get an equivalent value as {@link #getRawPath()} but decoded according to <a href="https://tools.ietf.org/html/rfc3986#section-2.1">percent-encoding</a>.
     * @return an equivalent value as {@link #getRawPath()} but decoded according to <a href="https://tools.ietf.org/html/rfc3986#section-2.1">percent-encoding</a>.
     */
    String getPath();

    /**
     * The <a href="https://tools.ietf.org/html/rfc3986#section-3.4">query component</a> derived from {@link #getRequestTarget()}.
     * <p>
     * No decoding has been done on the query component.
     * @return The <a href="https://tools.ietf.org/html/rfc3986#section-3.4">query component</a> derived from {@link #getRequestTarget()}.
     * <p>
     * No decoding has been done on the query component.
     */
    String getRawQuery();

    /**
     * The <a href="https://tools.ietf.org/html/rfc3986#section-3.4">query component</a> for {@code key} derived from {@link #getRequestTarget()}.
     * The values are decoded according to <a href="https://tools.ietf.org/html/rfc3986#section-2.1">percent-encoding</a>.
     * @param key The key which may identify a value in the <a href="https://tools.ietf.org/html/rfc3986#section-3.4">query component</a>.
     * @return An {@link Iterator} over all the values identified by {@code key} in the <a href="https://tools.ietf.org/html/rfc3986#section-3.4">query component</a>.
     * The values are decoded according to <a href="https://tools.ietf.org/html/rfc3986#section-2.1">percent-encoding</a>.
     */
    Iterator<String> getQueryValues(String key);
}

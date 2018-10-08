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

import javax.annotation.Nullable;

/**
 * Meta data associated with an HTTP request.
 */
public interface HttpRequestMetaData extends HttpMetaData {
    /**
     * Returns the {@link HttpRequestMethod} of this {@link StreamingHttpRequest}.
     *
     * @return The {@link HttpRequestMethod} of this {@link StreamingHttpRequest}
     */
    HttpRequestMethod method();

    /**
     * Set the {@link HttpRequestMethod} of this {@link StreamingHttpRequest}.
     *
     * @param method the {@link HttpRequestMethod} to set.
     * @return {@code this}.
     */
    HttpRequestMetaData method(HttpRequestMethod method);

    /**
     * The <a href="https://tools.ietf.org/html/rfc7230#section-3.1.1">request-target</a>.
     * <p>
     * No decoding has been done on the request-target.
     *
     * @return The <a href="https://tools.ietf.org/html/rfc7230#section-3.1.1">request-target</a>.
     * <p>
     * No decoding has been done on the request-target.
     */
    String requestTarget();

    /**
     * Set the <a href="https://tools.ietf.org/html/rfc7230#section-3.1.1">request-target</a>.
     * <p>
     * This will be treated as encoded according to
     * <a href="https://tools.ietf.org/html/rfc3986#section-2.1">percent-encoding</a>.
     * <p>
     * This may result in clearing of internal caches used by methods that are derived from the {@code request-target},
     * such as {@link #path()}, {@link #rawQuery()}, etc.
     *
     * @param requestTarget the <a href="https://tools.ietf.org/html/rfc3986#section-2.1">percent-encoded</a>
     * <a href="https://tools.ietf.org/html/rfc7230#section-3.1.1">request-target</a> to set.
     * @return {@code this}.
     */
    HttpRequestMetaData requestTarget(String requestTarget);

    /**
     * The <a href="https://tools.ietf.org/html/rfc3986#section-3.1">scheme component</a> derived
     * from {@link #requestTarget()}.
     *
     * @return The <a href="https://tools.ietf.org/html/rfc3986#section-3.1">scheme component</a> derived
     * from {@link #requestTarget()}, or {@code null} if none can be derived.
     */
    @Nullable
    String scheme();

    /**
     * The <a href="https://tools.ietf.org/html/rfc3986#section-3.2.1">user information component</a> derived
     * from {@link #requestTarget()}.
     *
     * @return The <a href="https://tools.ietf.org/html/rfc3986#section-3.2.1">user information component</a> derived
     * from {@link #requestTarget()}, or {@code null} if none can be derived.
     */
    @Nullable
    String userInfo();

    /**
     * The <a href="https://tools.ietf.org/html/rfc3986#section-3.2.2">host component</a> derived
     * from {@link #requestTarget()}.
     *
     * @return The <a href="https://tools.ietf.org/html/rfc3986#section-3.2.2">host component</a> derived
     * from {@link #requestTarget()}, or {@code null} if none can be derived.
     */
    @Nullable
    String host();

    /**
     * The <a href="https://tools.ietf.org/html/rfc3986#section-3.2.3">port component</a> derived
     * from {@link #requestTarget()}.
     *
     * @return The <a href="https://tools.ietf.org/html/rfc3986#section-3.2.3">port component</a> derived
     * from {@link #requestTarget()},
     * or {@code <0} if none can be derived.
     */
    int port();

    /**
     * The <a href="https://tools.ietf.org/html/rfc3986#section-3.3">path component</a> derived
     * from {@link #requestTarget()}.
     * <p>
     * No decoding has been done on the query component: the value is provided as specified in the request target.
     *
     * @return The <a href="https://tools.ietf.org/html/rfc3986#section-3.3">path component</a> derived
     * from {@link #requestTarget()}.
     * <p>
     * No decoding has been done on the query component: the value is provided as specified in the request target.
     */
    String rawPath();

    /**
     * Get an equivalent value as {@link #rawPath()} but decoded according
     * to <a href="https://tools.ietf.org/html/rfc3986#section-2.1">percent-encoding</a>.
     *
     * @return an equivalent value as {@link #rawPath()} but decoded according
     * to <a href="https://tools.ietf.org/html/rfc3986#section-2.1">percent-encoding</a>.
     */
    String path();

    /**
     * Sets the path to {@code path}, without any encoding performed. This assumes that any characters that require
     * encoding have been encoded according
     * to <a href="https://tools.ietf.org/html/rfc3986#section-2.1">percent-encoding</a> by the caller.
     * <p>
     * Because this modifies the request target, this may result in the clearing of internal caches.
     * See {@link #requestTarget(String)}.
     *
     * @param path the encoded path to set.
     * @return {@code this}.
     */
    HttpRequestMetaData rawPath(String path);

    /**
     * Sets the path, performing encoding according
     * to <a href="https://tools.ietf.org/html/rfc3986#section-2.1">percent-encoding</a>, except for forward-slash
     * ({@code '/'}) characters. This allows for {@code path("/abc")} without it turning into
     * {@code '%2Fabc'}.
     *
     * @param path the un-encoded path to set.
     * @return {@code this}.
     */
    HttpRequestMetaData path(String path);

    /**
     * Parses the <a href="https://tools.ietf.org/html/rfc3986#section-3.4">query component</a> of the request target,
     * returning an {@link HttpQuery} that may be used for reading and manipulating the query component. Modifications
     * to the {@link HttpQuery} will only be reflected in the request after {@link HttpQuery#encodeToRequestTarget()} is
     * called. If the underlying request is modified, the returned {@link HttpQuery} may become stale.
     *
     * @return an {@link HttpQuery} that reflects the current state of the query component.
     */
    HttpQuery parseQuery();

    /**
     * The <a href="https://tools.ietf.org/html/rfc3986#section-3.4">query component</a> derived
     * from {@link #requestTarget()}.
     * <p>
     * No decoding has been done on the query component: the value is provided as specified in the request target.
     *
     * @return The <a href="https://tools.ietf.org/html/rfc3986#section-3.4">query component</a> derived
     * from {@link #requestTarget()}.
     * <p>
     * No decoding has been done on the query component: the value is provided as specified in the request target.
     */
    String rawQuery();

    /**
     * Sets the <a href="https://tools.ietf.org/html/rfc3986#section-3.4">query component</a> to {@code query}, without
     * any encoding performed. This assumes that any characters that require encoding have been encoded according to
     * <a href="https://tools.ietf.org/html/rfc3986#section-2.1">percent-encoding</a> by the caller.
     * <p>
     * Because this modifies the request target, this may result in the clearing of internal caches.
     * See {@link #requestTarget(String)}.
     *
     * @param query the encoded query to set.
     * @return {@code this}.
     */
    HttpRequestMetaData rawQuery(String query);

    /**
     * The <a href="https://tools.ietf.org/html/rfc3986#section-3.2.2">host component</a> derived
     * from {@link #requestTarget()} and the {@code Host} header field value. This is the scheme component to use
     * when computing an <a href="https://tools.ietf.org/html/rfc7230#section-5.5">effective request URI</a>.
     *
     * @return The <a href="https://tools.ietf.org/html/rfc3986#section-3.2.2">host component</a> derived
     * from {@link #requestTarget()} and the {@code Host} header field value, or {@code null} if none can be derived.
     */
    @Nullable
    String effectiveHost();

    /**
     * The <a href="https://tools.ietf.org/html/rfc3986#section-3.2.3">port component</a> derived
     * from {@link #requestTarget()} and the {@code Host} header field value. This is the scheme component to use
     * when computing an <a href="https://tools.ietf.org/html/rfc7230#section-5.5">effective request URI</a>.
     *
     * @return The <a href="https://tools.ietf.org/html/rfc3986#section-3.2.3">port component</a> derived
     * from {@link #requestTarget()}, and the {@code Host} header field value, or {@code <0} if none can be derived.
     */
    int effectivePort();

    @Override
    HttpRequestMetaData version(HttpProtocolVersion version);

    @Override
    HttpRequestMetaData addHeader(CharSequence name, CharSequence value);

    @Override
    HttpRequestMetaData addHeaders(CharSequence name, Iterable<? extends CharSequence> values);

    @Override
    HttpRequestMetaData addHeaders(CharSequence name, CharSequence... values);

    @Override
    HttpRequestMetaData addHeaders(HttpHeaders headers);

    @Override
    HttpRequestMetaData setHeader(CharSequence name, CharSequence value);

    @Override
    HttpRequestMetaData setHeaders(CharSequence name, Iterable<? extends CharSequence> values);

    @Override
    HttpRequestMetaData setHeaders(CharSequence name, CharSequence... values);

    @Override
    HttpRequestMetaData setHeaders(HttpHeaders headers);

    @Override
    HttpRequestMetaData addCookie(HttpCookie cookie);

    @Override
    HttpRequestMetaData addCookie(CharSequence name, CharSequence value);

    @Override
    HttpRequestMetaData addSetCookie(HttpCookie cookie);

    @Override
    HttpRequestMetaData addSetCookie(CharSequence name, CharSequence value);
}

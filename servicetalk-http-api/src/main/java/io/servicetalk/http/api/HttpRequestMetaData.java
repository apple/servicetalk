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

import io.servicetalk.encoding.api.BufferEncoder;
import io.servicetalk.transport.api.HostAndPort;

import java.nio.charset.Charset;
import java.util.Iterator;
import java.util.Map.Entry;
import java.util.Set;
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
     * The <a href="https://tools.ietf.org/html/rfc7230#section-3.1.1">request-target</a>.
     *
     * @param encoding the {@link Charset} to use to
     * <a href="https://tools.ietf.org/html/rfc3986#section-2.1">decode</a> the
     * <a href="https://tools.ietf.org/html/rfc7230#section-3.1.1">request-target</a>.
     * @return The <a href="https://tools.ietf.org/html/rfc7230#section-3.1.1">request-target</a>.
     */
    String requestTarget(Charset encoding);

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
     * Set the <a href="https://tools.ietf.org/html/rfc7230#section-3.1.1">request-target</a>.
     * <p>
     * This may result in clearing of internal caches used by methods that are derived from the {@code request-target},
     * such as {@link #path()}, {@link #rawQuery()}, etc.
     *
     * @param requestTarget the
     * <a href="https://tools.ietf.org/html/rfc7230#section-3.1.1">request-target</a> to set.
     * @param encoding the {@link Charset} to use to
     * <a href="https://tools.ietf.org/html/rfc3986#section-2.1">encode</a> {@code requestTarget} before setting the
     * value.
     * @return {@code this}.
     */
    HttpRequestMetaData requestTarget(String requestTarget, Charset encoding);

    /**
     * The <a href="https://tools.ietf.org/html/rfc3986#section-3.1">scheme component</a> derived
     * from {@link #requestTarget()} in lower case.
     *
     * @return The <a href="https://tools.ietf.org/html/rfc3986#section-3.1">scheme component</a> derived
     * from {@link #requestTarget()} in lower case, or {@code null} if none can be derived.
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
     * Appends segments to the current {@link #path()}, performing encoding of each segment
     * (including ({@code '/'}) characters) according
     * to <a href="https://tools.ietf.org/html/rfc3986#section-2.1">percent-encoding</a>.
     * A {@code /} is used to separate each segment and between the current {@link #path()} and the following segments.
     *
     * @param segments the un-encoded path to set.
     * @return {@code this}.
     */
    HttpRequestMetaData appendPathSegments(String... segments);

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
    @Nullable
    String rawQuery();

    /**
     * Get an equivalent value as {@link #rawQuery()} but decoded according
     * to <a href="https://tools.ietf.org/html/rfc3986#section-3.4">rfc3986, Query</a>.
     *
     * @return an equivalent value as {@link #rawQuery()} but decoded according
     * to <a href="https://tools.ietf.org/html/rfc3986#section-3.4">rfc3986, Query</a>.
     */
    @Nullable
    String query();

    /**
     * Sets the <a href="https://tools.ietf.org/html/rfc3986#section-3.4">query component</a> to {@code query}, without
     * any encoding performed. This assumes that any characters that require encoding have been encoded according to
     * <a href="https://tools.ietf.org/html/rfc3986#section-2.1">percent-encoding</a> by the caller.
     * <p>
     * Because this modifies the request target, this may result in the clearing of internal caches.
     * See {@link #requestTarget(String)}.
     *
     * @param query the encoded query to set. {@code null} will clear the query value (e.g. no {@code ?} present in
     * {@link #requestTarget()}.
     * @return {@code this}.
     */
    HttpRequestMetaData rawQuery(@Nullable String query);

    /**
     * Sets the path, performing encoding according
     * to <a href="https://tools.ietf.org/html/rfc3986#section-3.4">rfc3986, Query</a>.
     *
     * @param query the un-encoded query to set. {@code null} will clear the query value (e.g. no {@code ?} present in
     * {@link #requestTarget()}.
     * @return {@code this}.
     */
    HttpRequestMetaData query(@Nullable String query);

    /**
     * Returns the value of a query parameter with the specified key. If there is more than one value for the specified
     * key, the first value in insertion order is returned.

     * @param key the key of the query parameter to retrieve.
     * @return the first query parameter value if the key is found. {@code null} if there's no such key.
     */
    @Nullable
    String queryParameter(String key);

    /**
     * Returns all query parameters as key/value pairs.
     *
     * @return an {@link Iterable} of query parameter key/value pairs or an empty {@link Iterable} if none present.
     */
    Iterable<Entry<String, String>> queryParameters();

    /**
     * Returns all values for the query parameter with the specified key.
     *
     * @param key the key of the query parameter to retrieve.
     * @return an {@link Iterable} of query parameter values or an empty {@link Iterable} if no values are found.
     */
    Iterable<String> queryParameters(String key);

    /**
     * Returns all values for the query parameter with the specified key.
     *
     * @param key the key of the query parameter to retrieve.
     * @return an {@link Iterator} of query parameter values or an empty {@link Iterator} if no values are found.
     */
    Iterator<String> queryParametersIterator(String key);

    /**
     * Returns a {@link Set} of all query parameter keys. The returned {@link Set} cannot be modified.
     * @return a {@link Set} of all query parameter keys. The returned {@link Set} cannot be modified.
     */
    Set<String> queryParametersKeys();

    /**
     * Returns {@code true} if a query parameter with the {@code key} exists, {@code false} otherwise.
     *
     * @param key the query parameter name.
     * @return {@code true} if {@code key} exists.
     */
    default boolean hasQueryParameter(final String key) {
        return queryParameter(key) != null;
    }

    /**
     * Returns {@code true} if a query parameter with the {@code key} and {@code value} exists, {@code false} otherwise.
     *
     * @param key the query parameter key.
     * @param value the query parameter value of the query parameter to find.
     * @return {@code true} if a {@code key}, {@code value} pair exists.
     */
    boolean hasQueryParameter(String key, String value);

    /**
     * Returns the number of query parameters.
     * @return the number of query parameters.
     */
    int queryParametersSize();

    /**
     * Adds a new query parameter with the specified {@code key} and {@code value}, which will be
     * <a href="https://tools.ietf.org/html/rfc3986#section-2.1">percent-encoded</a> if needed.
     *
     * @param key the query parameter key.
     * @param value the query parameter value.
     * @return {@code this}.
     */
    HttpRequestMetaData addQueryParameter(String key, String value);

    /**
     * Adds new query parameters with the specified {@code key} and {@code values}. This method is semantically
     * equivalent to:
     *
     * <pre>
     * for (T value : values) {
     *     addQueryParameter(key, value);
     * }
     * </pre>
     *
     * @param key the query parameter key.
     * @param values the query parameter values.
     * @return {@code this}.
     */
    HttpRequestMetaData addQueryParameters(String key, Iterable<String> values);

    /**
     * Adds new query parameters with the specified {@code key} and {@code values}. This method is semantically
     * equivalent to:
     *
     * <pre>
     * for (T value : values) {
     *     query.addQueryParameter(key, value);
     * }
     * </pre>
     *
     * @param key the query parameter key.
     * @param values the query parameter values.
     * @return {@code this}.
     */
    HttpRequestMetaData addQueryParameters(String key, String... values);

    /**
     * Sets a query parameter with the specified {@code key} and {@code value}, which will be
     * <a href="https://tools.ietf.org/html/rfc3986#section-2.1">percent-encoded</a> if needed.
     * Any existing query parameters with the same key are overwritten.
     *
     * @param key the query parameter key.
     * @param value the query parameter value.
     * @return {@code this}.
     */
    HttpRequestMetaData setQueryParameter(String key, String value);

    /**
     * Sets new query parameters with the specified {@code key} and {@code values}. This method is equivalent to:
     *
     * <pre>
     * removeQueryParameter(key);
     * for (T value : values) {
     *     query.addQueryParameter(key, value);
     * }
     * </pre>
     *
     * @param key the query parameter key.
     * @param values the query parameter values.
     * @return {@code this}.
     */
    HttpRequestMetaData setQueryParameters(String key, Iterable<String> values);

    /**
     * Sets new query parameters with the specified {@code key} and {@code values}. This method is equivalent to:
     *
     * <pre>
     * removeQueryParameter(key);
     * for (T value : values) {
     *     query.addQueryParameter(key, value);
     * }
     * </pre>
     *
     * @param key the query parameter key.
     * @param values the query parameter values.
     * @return {@code this}.
     */
    HttpRequestMetaData setQueryParameters(String key, String... values);

    /**
     * Removes all query parameters with the specified {@code key}.
     *
     * @param key the query parameter key.
     * @return {@code true} if at least one entry has been removed.
     */
    boolean removeQueryParameters(String key);

    /**
     * Removes all query parameters with the specified {@code key} and {@code value}.
     *
     * @param key the query parameter key.
     * @param value the query parameter value.
     * @return {@code true} if at least one entry has been removed.
     */
    boolean removeQueryParameters(String key, String value);

    /**
     * Get the <a href="https://tools.ietf.org/html/rfc3986#section-3.2.2">host</a> and
     * <a href="https://tools.ietf.org/html/rfc3986#section-3.2.3">port</a> components
     * of the <a href="https://tools.ietf.org/html/rfc7230#section-5.5">effective request URI</a>.
     * The port component will be {@code <0} if none can be derived. This method typically pulls information from
     * {@link #requestTarget()} and {@link HttpHeaderNames#HOST} header.
     *
     * @return The <a href="https://tools.ietf.org/html/rfc3986#section-3.2.2">host</a> and
     * <a href="https://tools.ietf.org/html/rfc3986#section-3.2.3">port</a> components
     * of the <a href="https://tools.ietf.org/html/rfc7230#section-5.5">effective request URI</a>. {@code null} if the
     * request doesn't provide enough info to derive the host/port.
     */
    @Nullable
    HostAndPort effectiveHostAndPort();

    /**
     * Get the {@link BufferEncoder} to use for this request. The value can be used by filters
     * (such as {@link ContentEncodingHttpRequesterFilter}) to apply {@link HttpHeaderNames#CONTENT_ENCODING} to the
     * request.
     *
     * @return the {@link BufferEncoder} to use for this request.
     */
    @Nullable
    BufferEncoder contentEncoding();

    /**
     * Set the {@link BufferEncoder} to use for this request. The value can be used by filters
     * (such as {@link ContentEncodingHttpRequesterFilter}) to apply {@link HttpHeaderNames#CONTENT_ENCODING} to the
     * request.
     * @param encoder {@link BufferEncoder} to use for this request.
     * @return {@code this}.
     */
    HttpRequestMetaData contentEncoding(@Nullable BufferEncoder encoder);

    @Override
    HttpRequestMetaData version(HttpProtocolVersion version);

    @Override
    default HttpRequestMetaData addHeader(final CharSequence name, final CharSequence value) {
        HttpMetaData.super.addHeader(name, value);
        return this;
    }

    @Override
    default HttpRequestMetaData addHeaders(final HttpHeaders headers) {
        HttpMetaData.super.addHeaders(headers);
        return this;
    }

    @Override
    default HttpRequestMetaData setHeader(final CharSequence name, final CharSequence value) {
        HttpMetaData.super.setHeader(name, value);
        return this;
    }

    @Override
    default HttpRequestMetaData setHeaders(final HttpHeaders headers) {
        HttpMetaData.super.setHeaders(headers);
        return this;
    }

    @Override
    default HttpRequestMetaData addCookie(final HttpCookiePair cookie) {
        HttpMetaData.super.addCookie(cookie);
        return this;
    }

    @Override
    default HttpRequestMetaData addCookie(final CharSequence name, final CharSequence value) {
        HttpMetaData.super.addCookie(name, value);
        return this;
    }

    @Override
    default HttpRequestMetaData addSetCookie(final HttpSetCookie cookie) {
        HttpMetaData.super.addSetCookie(cookie);
        return this;
    }

    @Override
    default HttpRequestMetaData addSetCookie(final CharSequence name, final CharSequence value) {
        HttpMetaData.super.addSetCookie(name, value);
        return this;
    }
}

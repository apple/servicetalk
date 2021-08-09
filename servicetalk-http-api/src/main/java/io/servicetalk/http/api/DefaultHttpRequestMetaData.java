/*
 * Copyright © 2018-2019 Apple Inc. and the ServiceTalk project authors
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
import io.servicetalk.encoding.api.ContentCodec;
import io.servicetalk.transport.api.HostAndPort;

import java.nio.charset.Charset;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import javax.annotation.Nullable;

import static io.servicetalk.http.api.HttpHeaderNames.HOST;
import static io.servicetalk.http.api.HttpRequestMethod.CONNECT;
import static io.servicetalk.http.api.UriComponentType.PATH;
import static io.servicetalk.http.api.UriComponentType.PATH_SEGMENT;
import static io.servicetalk.http.api.UriComponentType.QUERY;
import static io.servicetalk.http.api.UriComponentType.QUERY_VALUE;
import static io.servicetalk.http.api.UriUtils.decodeQueryParams;
import static io.servicetalk.http.api.UriUtils.encodeComponent;
import static io.servicetalk.http.api.UriUtils.parsePort;
import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.Objects.requireNonNull;

/**
 * Default implementation of {@link HttpRequestMetaData}.
 */
class DefaultHttpRequestMetaData extends AbstractHttpMetaData implements HttpRequestMetaData {
    private static final Charset REQUEST_TARGET_CHARSET = UTF_8;
    static final int DEFAULT_MAX_QUERY_PARAMS = 1024;

    private HttpRequestMethod method;
    private String requestTarget;

    @Nullable
    private HttpQuery httpQuery;
    @Nullable
    private Uri requestTargetUri;
    @Nullable
    private String pathDecoded;
    @Nullable
    private String queryDecoded;
    @Nullable
    private BufferEncoder encoder;

    DefaultHttpRequestMetaData(final HttpRequestMethod method, final String requestTarget,
                               final HttpProtocolVersion version, final HttpHeaders headers) {
        super(version, headers);
        this.method = requireNonNull(method);
        this.requestTarget = requireNonNull(requestTarget);
    }

    @Override
    public HttpRequestMetaData version(final HttpProtocolVersion version) {
        super.version(version);
        return this;
    }

    @Deprecated
    @Override
    public HttpMetaData encoding(final ContentCodec encoding) {
        super.encoding(encoding);
        return this;
    }

    @Nullable
    @Override
    public BufferEncoder contentEncoding() {
        return encoder;
    }

    @Override
    public HttpRequestMetaData contentEncoding(@Nullable final BufferEncoder encoder) {
        this.encoder = encoder;
        return this;
    }

    @Override
    public final HttpRequestMethod method() {
        return method;
    }

    @Override
    public HttpRequestMetaData method(final HttpRequestMethod method) {
        this.method = requireNonNull(method);
        return this;
    }

    @Override
    public final String requestTarget() {
        checkDirtyQuery();
        return requestTarget;
    }

    @Override
    public String requestTarget(final Charset encoding) {
        return CONNECT.equals(method) ? HttpAuthorityFormUri.decode(requestTarget(), encoding) :
                Uri3986.decode(requestTarget(), encoding);
    }

    @Override
    public HttpRequestMetaData requestTarget(final String requestTarget) {
        this.requestTarget = requireNonNull(requestTarget);
        invalidateParsedUri();
        return this;
    }

    @Override
    public HttpRequestMetaData requestTarget(final String requestTarget, final Charset encoding) {
         return requestTarget(CONNECT.equals(method) ? HttpAuthorityFormUri.encode(requestTarget, encoding) :
                 Uri3986.encode(requestTarget, encoding, true));
    }

    @Nullable
    @Override
    public final String scheme() {
        return lazyParseRequestTarget().scheme();
    }

    @Nullable
    @Override
    public final String userInfo() {
        return lazyParseRequestTarget().userInfo();
    }

    @Nullable
    @Override
    public final String host() {
        return lazyParseRequestTarget().host();
    }

    @Override
    public final int port() {
        return lazyParseRequestTarget().port();
    }

    @Override
    public final String rawPath() {
        return lazyParseRequestTarget().path();
    }

    @Override
    public HttpRequestMetaData rawPath(final String path) {
        Uri httpUri = lazyParseRequestTarget();
        validateFirstPathSegment(httpUri, path);

        // Potentially over estimate the size of the URL to avoid resize/copy
        StringBuilder sb = new StringBuilder(httpUri.uri().length() + path.length());
        appendScheme(sb, httpUri);
        appendAuthority(sb, httpUri);

        // https://tools.ietf.org/html/rfc3986#section-3.3
        // If a URI contains an authority component, then the path component must either be empty or begin with a
        // slash ("/") character.
        final String host = httpUri.host();
        if ((host != null && !host.isEmpty()) && (!path.isEmpty() && path.charAt(0) != '/')) {
            sb.append('/');
        }
        sb.append(path);

        appendQuery(sb, httpUri);
        appendFragment(sb, httpUri);

        return requestTarget(sb.toString());
    }

    @Override
    public final String path() {
        if (pathDecoded != null) {
            return pathDecoded;
        }
        pathDecoded = lazyParseRequestTarget().path(REQUEST_TARGET_CHARSET);
        return pathDecoded;
    }

    @Override
    public HttpRequestMetaData path(String path) {
        return rawPath(encodeComponent(PATH, path, REQUEST_TARGET_CHARSET, true));
    }

    @Override
    public HttpRequestMetaData appendPathSegments(String... segments) {
        if (segments.length == 0) {
            throw new IllegalArgumentException("At least one path segment must be provided");
        }
        Uri httpUri = lazyParseRequestTarget();
        StringBuilder sb = new StringBuilder(httpUri.uri().length() + segments.length * 8);

        // Append everything up to and including the path
        appendScheme(sb, httpUri);
        appendAuthority(sb, httpUri);

        // Append the new path segments
        String path = httpUri.path();
        final String segment = encodeComponent(PATH_SEGMENT, segments[0], REQUEST_TARGET_CHARSET, false);
        if (path.isEmpty() || path.length() == 1 && path.charAt(0) == '/') {
            validateFirstPathSegment(httpUri, segment);
        }
        sb.append(path);
        // relative-path is valid so don't append a '/' in case there is nothing appended thus far.
        // https://tools.ietf.org/html/rfc3986#section-4.2
        if ((path.isEmpty() || path.charAt(path.length() - 1) != '/') && sb.length() != 0) {
            sb.append('/');
        }
        sb.append(segment);
        for (int i = 1; i < segments.length; i++) {
            sb.append('/').append(encodeComponent(PATH_SEGMENT, segments[i], REQUEST_TARGET_CHARSET, false));
        }

        // Append the query string
        appendQuery(sb, httpUri);
        appendFragment(sb, httpUri);

        return requestTarget(sb.toString());
    }

    @Override
    public final String rawQuery() {
        checkDirtyQuery();
        return lazyParseRequestTarget().query();
    }

    @Override
    public HttpRequestMetaData rawQuery(@Nullable final String query) {
        Uri httpUri = lazyParseRequestTarget();
        // Potentially over estimate the size of the URL to avoid resize/copy
        StringBuilder sb = query != null ? new StringBuilder(httpUri.uri().length() + query.length() + 1) :
                                           new StringBuilder(httpUri.uri().length());
        appendScheme(sb, httpUri);
        appendAuthority(sb, httpUri);
        sb.append(httpUri.path());

        if (query != null) {
            sb.append('?').append(query);
        }

        appendFragment(sb, httpUri);

        return requestTarget(sb.toString());
    }

    @Override
    public String query() {
        checkDirtyQuery();
        if (queryDecoded != null) {
            return queryDecoded;
        }
        queryDecoded = lazyParseRequestTarget().query(REQUEST_TARGET_CHARSET);
        return queryDecoded;
    }

    @Override
    public HttpRequestMetaData query(@Nullable final String query) {
        return rawQuery(query == null ? null : encodeComponent(QUERY, query, REQUEST_TARGET_CHARSET, true));
    }

    @Nullable
    @Override
    public String queryParameter(final String key) {
        return lazyParseQueryString().get(key);
    }

    @Override
    public Iterable<Entry<String, String>> queryParameters() {
        return lazyParseQueryString();
    }

    @Override
    public Iterable<String> queryParameters(final String key) {
        return lazyParseQueryString().values(key);
    }

    @Override
    public Iterator<String> queryParametersIterator(final String key) {
        return lazyParseQueryString().valuesIterator(key);
    }

    @Override
    public Set<String> queryParametersKeys() {
        return lazyParseQueryString().keys();
    }

    @Override
    public boolean hasQueryParameter(final String key, final String value) {
        return lazyParseQueryString().contains(key, value);
    }

    @Override
    public int queryParametersSize() {
        return lazyParseQueryString().size();
    }

    @Override
    public HttpRequestMetaData addQueryParameter(final String key, final String value) {
        lazyParseQueryString().add(key, value);
        return this;
    }

    @Override
    public HttpRequestMetaData addQueryParameters(final String key, final Iterable<String> values) {
        lazyParseQueryString().add(key, values);
        return this;
    }

    @Override
    public HttpRequestMetaData addQueryParameters(final String key, final String... values) {
        lazyParseQueryString().add(key, values);
        return this;
    }

    @Override
    public HttpRequestMetaData setQueryParameter(final String key, final String value) {
        lazyParseQueryString().set(key, value);
        return this;
    }

    @Override
    public HttpRequestMetaData setQueryParameters(final String key, final Iterable<String> values) {
        lazyParseQueryString().set(key, values);
        return this;
    }

    @Override
    public HttpRequestMetaData setQueryParameters(final String key, final String... values) {
        lazyParseQueryString().set(key, values);
        return this;
    }

    @Override
    public boolean removeQueryParameters(final String key) {
        return lazyParseQueryString().remove(key);
    }

    @Override
    public boolean removeQueryParameters(final String key, final String value) {
        return lazyParseQueryString().remove(key, value);
    }

    @Override
    public HostAndPort effectiveHostAndPort() {
        final CharSequence hostHeader;
        final Uri effectiveRequestUri = lazyParseRequestTarget();
        final String effectiveRequestUriHost = effectiveRequestUri.host();
        if (effectiveRequestUriHost != null) {
            return HostAndPort.of(effectiveRequestUriHost, effectiveRequestUri.port());
        } else if ((hostHeader = headers().get(HOST)) != null) {
            return parseHostHeader(hostHeader.toString());
        }
        return null;
    }

    private void checkDirtyQuery() {
        if (httpQuery != null && httpQuery.isDirty()) {
            httpQuery.resetDirty();
            query(httpQuery.queryParameters());
        }
    }

    private HttpQuery lazyParseQueryString() {
        if (httpQuery == null) {
            httpQuery = new HttpQuery(decodeQueryParams(lazyParseRequestTarget().query(),
                    REQUEST_TARGET_CHARSET, DEFAULT_MAX_QUERY_PARAMS));
        }
        return httpQuery;
    }

    private Uri lazyParseRequestTarget() {
        if (requestTargetUri == null) {
            requestTargetUri = CONNECT.equals(method) ? new HttpAuthorityFormUri(requestTarget()) :
                    new Uri3986(requestTarget());
        }
        return requestTargetUri;
    }

    private void query(final Map<String, List<String>> params) {
        Uri httpUri = lazyParseRequestTarget();
        StringBuilder sb = new StringBuilder(httpUri.uri().length() + params.size() * 8);

        appendScheme(sb, httpUri);
        appendAuthority(sb, httpUri);
        sb.append(httpUri.path());

        // Append query params
        Iterator<Entry<String, List<String>>> itr = params.entrySet().iterator();
        char prefixChar = '?';
        while (itr.hasNext()) {
            Entry<String, List<String>> next = itr.next();
            String encodedKey = encodeComponent(QUERY, next.getKey(), REQUEST_TARGET_CHARSET, true);
            sb.append(prefixChar).append(encodedKey);
            List<String> values = next.getValue();
            if (values != null) {
                Iterator<String> valuesItr = values.iterator();
                if (valuesItr.hasNext()) {
                    String value = valuesItr.next();
                    sb.append('=').append(encodeComponent(QUERY_VALUE, value, REQUEST_TARGET_CHARSET, true));
                    while (valuesItr.hasNext()) {
                        value = valuesItr.next();
                        sb.append('&').append(encodedKey).append('=')
                                .append(encodeComponent(QUERY_VALUE, value, REQUEST_TARGET_CHARSET, true));
                    }
                }
            }
            prefixChar = '&';
        }

        appendFragment(sb, httpUri);

        requestTarget(sb.toString());
    }

    private void invalidateParsedUri() {
        requestTargetUri = null;
        httpQuery = null;
        pathDecoded = null;
        queryDecoded = null;
    }

    @Override
    public final String toString() {
        return method().toString() + " " + requestTarget() + " " + version();
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        if (!super.equals(o)) {
            return false;
        }

        final DefaultHttpRequestMetaData that = (DefaultHttpRequestMetaData) o;

        return method.equals(that.method) && requestTarget().equals(that.requestTarget());
    }

    @Override
    public int hashCode() {
        int result = super.hashCode();
        result = 31 * result + method.hashCode();
        result = 31 * result + requestTarget().hashCode();
        return result;
    }

    private static void validateFirstPathSegment(final Uri httpUri, final String path) {
        int i = 0;
        final String scheme = httpUri.scheme();
        if (scheme == null || scheme.isEmpty()) {
            // https://tools.ietf.org/html/rfc3986#section-3.3
            // In addition, a URI reference (Section 4.1) may be a relative-path reference, in which case the first path
            // segment cannot contain a colon (":") character.
            for (; i < path.length(); ++i) {
                final char c = path.charAt(i);
                if (c == '/') {
                    break;
                } else if (c == ':') {
                    throw new IllegalArgumentException("relative-path cannot contain `:` in first segment");
                }
            }
        }
        // https://tools.ietf.org/html/rfc3986#section-3.3
        // If a URI does not contain an authority component, then the path cannot begin with two slash characters
        // ("//").
        if (httpUri.host() == null && path.length() >= 2 && path.charAt(0) == '/' && path.charAt(1) == '/') {
            throw new IllegalArgumentException("No authority component, path cannot start with '//'");
        }
        // It is assumed '?'/'#' characters that delimit query/fragment components have been escaped and are therefore
        // not validated.
    }

    @Nullable
    private static HostAndPort parseHostHeader(String parsedHostHeader) {
        if (parsedHostHeader.isEmpty()) {
            return null;
        }

        String parsedHost;
        int parsedPort = -1;
        if (parsedHostHeader.charAt(0) == '[') {
            // https://tools.ietf.org/html/rfc3986#section-3.2.2
            // A host identified by an Internet Protocol literal address, version 6 [RFC3513] or later, is distinguished
            // by enclosing the IP literal within square brackets ("[" and "]").  This is the only place where square
            // bracket characters are allowed in the URI syntax.
            final int x = parsedHostHeader.lastIndexOf(']');
            if (x <= 0) {
                throw new IllegalArgumentException("IPv6 address should be in square brackets, and not empty");
            }
            parsedHost = parsedHostHeader.substring(0, x + 1);
            if (parsedHostHeader.length() - 1 > x) {
                if (parsedHostHeader.charAt(x + 1) == ':') {
                    parsedPort = parsePort(parsedHostHeader, x + 2, parsedHostHeader.length());
                } else {
                    throw new IllegalArgumentException("Unexpected content after IPv6 address");
                }
            }
        } else {
            // IPv4 or literal host with port number
            final int x = parsedHostHeader.lastIndexOf(':');
            if (x < 0) {
                parsedHost = parsedHostHeader;
            } else {
                parsedHost = parsedHostHeader.substring(0, x);
                parsedPort = parsePort(parsedHostHeader, x + 1, parsedHostHeader.length());
            }
        }
        return HostAndPort.of(parsedHost, parsedPort);
    }

    private static void appendScheme(StringBuilder sb, Uri httpUri) {
        if (httpUri.scheme() != null) {
            sb.append(httpUri.scheme()).append(':');
        }
    }

    private static void appendAuthority(StringBuilder sb, Uri httpUri) {
        if (httpUri.host() != null) {
            // The authority component is preceded by a double slash ("//")
            // authority   = [ userinfo "@" ] host [ ":" port ]
            sb.append("//");
            if (httpUri.userInfo() != null) {
                sb.append(httpUri.userInfo()).append('@');
            }

            sb.append(httpUri.host());
            if (httpUri.port() >= 0) {
                sb.append(':').append(httpUri.port());
            }
        }
    }

    private static void appendQuery(StringBuilder sb, Uri httpUri) {
        String query = httpUri.query();
        if (query != null) {
            sb.append('?').append(query);
        }
    }

    private static void appendFragment(StringBuilder sb, Uri httpUri) {
        String fragment = httpUri.fragment();
        if (fragment != null) {
            sb.append('#').append(fragment);
        }
    }
}

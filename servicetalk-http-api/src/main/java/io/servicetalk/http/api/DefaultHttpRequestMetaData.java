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

import java.io.UnsupportedEncodingException;
import java.nio.charset.Charset;
import java.nio.charset.UnsupportedCharsetException;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import javax.annotation.Nullable;

import static io.servicetalk.http.api.HttpHeaderNames.HOST;
import static io.servicetalk.http.api.HttpUri.buildRequestTarget;
import static io.servicetalk.http.api.QueryStringDecoder.decodeParams;
import static java.net.URLEncoder.encode;
import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.Objects.requireNonNull;

/**
 * Default implementation of {@link HttpRequestMetaData}.
 */
class DefaultHttpRequestMetaData extends AbstractHttpMetaData implements HttpRequestMetaData {

    private static final Charset REQUEST_TARGET_CHARSET = UTF_8;
    private static final int PORT_NOT_ASSIGNED = -2;

    private HttpRequestMethod method;
    private String requestTarget;

    @Nullable
    private HttpQuery httpQuery;
    @Nullable
    private HttpUri requestTargetUri;
    @Nullable
    private String effectiveRequestHost;
    private int effectiveRequestPort = PORT_NOT_ASSIGNED;
    @Nullable
    private CharSequence effectiveRequestHostHeader;

    DefaultHttpRequestMetaData(final HttpRequestMethod method, final String requestTarget,
                               final HttpProtocolVersion version, final HttpHeaders headers) {
        super(version, headers);
        this.method = requireNonNull(method);
        this.requestTarget = requireNonNull(requestTarget);
    }

    DefaultHttpRequestMetaData(final DefaultHttpRequestMetaData requestMetaData) {
        super(requestMetaData);
        this.method = requestMetaData.method;
        this.requestTarget = requestMetaData.requestTarget;
        this.httpQuery = requestMetaData.httpQuery;
        this.requestTargetUri = requestMetaData.requestTargetUri;
        this.effectiveRequestHost = requestMetaData.effectiveRequestHost;
        this.effectiveRequestPort = requestMetaData.effectiveRequestPort;
        this.effectiveRequestHostHeader = requestMetaData.effectiveRequestHostHeader;
    }

    @Override
    public HttpRequestMetaData version(final HttpProtocolVersion version) {
        super.version(version);
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
        return requestTarget;
    }

    @Override
    public HttpRequestMetaData requestTarget(final String requestTarget) {
        this.requestTarget = requireNonNull(requestTarget);
        invalidateParsedUri();
        return this;
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
        return lazyParseRequestTarget().explicitPort();
    }

    @Override
    public final String rawPath() {
        return lazyParseRequestTarget().rawPath();
    }

    @Override
    public HttpRequestMetaData rawPath(final String path) {
        if (!path.isEmpty() && path.charAt(0) != '/') {
            throw new IllegalArgumentException("Path must be empty or start with '/'");
        }
        requestTarget(encodeRequestTarget(path, rawQuery(), null));
        return this;
    }

    @Override
    public final String path() {
        return lazyParseRequestTarget().path();
    }

    @Override
    public HttpRequestMetaData path(String path) {
        if (!path.isEmpty() && path.charAt(0) != '/') {
            path = "/" + path;
        }
        // TODO This is an ugly hack!
        final String encodedPath = urlEncode(path).replaceAll("%2F", "/");
        requestTarget(encodeRequestTarget(encodedPath, rawQuery(), null));
        return this;
    }

    @Override
    public HttpRequestMetaData appendPathSegments(String... segments) {
        if (segments.length == 0) {
            throw new IllegalArgumentException("At least one path segment must be provided");
        }

        final String path = path();
        final StringBuilder builder = new StringBuilder(path.length() + 8 * segments.length).append(path);
        if (!path.isEmpty() && !path.endsWith("/")) {
            builder.append('/');
        }
        for (int i = 0; i < segments.length; i++) {
            builder.append(urlEncode(segments[i]));

            if (i < (segments.length - 1)) {
                builder.append('/');
            }
        }
        requestTarget(encodeRequestTarget(builder.toString(), rawQuery(), null));
        return this;
    }

    @Override
    public final String rawQuery() {
        return lazyParseRequestTarget().rawQuery();
    }

    @Override
    public HttpRequestMetaData rawQuery(final String query) {
        requestTarget(encodeRequestTarget(rawPath(), requireNonNull(query), null));
        return this;
    }

    @Nullable
    @Override
    public String queryParameter(final String key) {
        return lazyParseQueryString().get(key);
    }

    @Override
    public Iterable<Map.Entry<String, String>> queryParameters() {
        return lazyParseQueryString();
    }

    @Override
    public Iterator<String> queryParameters(final String key) {
        return lazyParseQueryString().values(key);
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

    @Nullable
    @Override
    public final String effectiveHost() {
        lazyParseEffectiveRequest();
        return effectiveRequestHost;
    }

    @Override
    public final int effectivePort() {
        lazyParseEffectiveRequest();
        return effectiveRequestPort;
    }

    private HttpQuery lazyParseQueryString() {
        if (httpQuery == null) {
            httpQuery = new HttpQuery(decodeParams(lazyParseRequestTarget().rawQuery()), this::setQueryParams);
        }
        return httpQuery;
    }

    private HttpUri lazyParseRequestTarget() {
        if (requestTargetUri == null) {
            requestTargetUri = new HttpUri(requestTarget());
        }
        return requestTargetUri;
    }

    private void lazyParseEffectiveRequest() {
        final CharSequence hostHeader = headers().get(HOST);

        if (effectiveRequestPort == PORT_NOT_ASSIGNED || !Objects.equals(hostHeader, effectiveRequestHostHeader)) {
            final HttpUri effectiveRequestUri = new HttpUri(requestTarget(), () -> StringUtil.toString(hostHeader));
            effectiveRequestHost = effectiveRequestUri.host();
            effectiveRequestPort = effectiveRequestUri.explicitPort();
            effectiveRequestHostHeader = hostHeader;
        }
    }

    // package-private for testing.
    void setQueryParams(final Map<String, List<String>> params) {
        final QueryStringEncoder encoder = new QueryStringEncoder(rawPath());

        for (final Map.Entry<String, List<String>> entry : params.entrySet()) {
            for (final String value : entry.getValue()) {
                encoder.addParam(entry.getKey(), value);
            }
        }

        requestTarget(encodeRequestTarget(null, null, encoder.toString()));
    }

    private String encodeRequestTarget(@Nullable final String path,
                                       @Nullable final String query,
                                       @Nullable final String relativeReference) {
        final HttpUri uri = lazyParseRequestTarget();
        final String scheme = uri.scheme();
        return buildRequestTarget(
                scheme != null ? scheme : "http",
                uri.host(),
                uri.explicitPort(),
                path,
                query,
                relativeReference);
    }

    private void invalidateParsedUri() {
        requestTargetUri = null;
        effectiveRequestPort = PORT_NOT_ASSIGNED;
        effectiveRequestHost = null;
        effectiveRequestHostHeader = null;
        httpQuery = null;
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

        return method.equals(that.method) && requestTarget.equals(that.requestTarget);
    }

    @Override
    public int hashCode() {
        int result = super.hashCode();
        result = 31 * result + method.hashCode();
        result = 31 * result + requestTarget.hashCode();
        return result;
    }

    private static String urlEncode(String s) {
        try {
            return encode(s, REQUEST_TARGET_CHARSET.name());
        } catch (final UnsupportedEncodingException e) {
            throw new UnsupportedCharsetException(REQUEST_TARGET_CHARSET.name());
        }
    }
}

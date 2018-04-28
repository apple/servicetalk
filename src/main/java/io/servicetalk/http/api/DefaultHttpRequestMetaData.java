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

import java.io.UnsupportedEncodingException;
import java.nio.charset.Charset;
import java.nio.charset.UnsupportedCharsetException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiFunction;
import javax.annotation.Nullable;

import static io.servicetalk.http.api.HttpUri.buildRequestTarget;
import static java.lang.System.lineSeparator;
import static java.net.URLEncoder.encode;
import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.Objects.requireNonNull;

/**
 * Default implementation of {@link HttpRequestMetaData}.
 */
class DefaultHttpRequestMetaData extends AbstractHttpMetaData implements HttpRequestMetaData {

    private static final Charset REQUEST_TARGET_CHARSET = UTF_8;

    private HttpRequestMethod method;
    private String requestTarget;

    @Nullable
    private QueryStringDecoder queryStringDecoder;
    @Nullable
    private Map<String, List<String>> queryString;
    @Nullable
    private HttpUri uri;

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
        this.queryStringDecoder = requestMetaData.queryStringDecoder;
        this.queryString = requestMetaData.queryString;
        this.uri = requestMetaData.uri;
    }

    @Override
    public HttpRequestMetaData setVersion(final HttpProtocolVersion version) {
        super.setVersion(version);
        return this;
    }

    @Override
    public final HttpRequestMethod getMethod() {
        return method;
    }

    @Override
    public HttpRequestMetaData setMethod(final HttpRequestMethod method) {
        this.method = requireNonNull(method);
        return this;
    }

    @Override
    public final String getRequestTarget() {
        return requestTarget;
    }

    @Override
    public HttpRequestMetaData setRequestTarget(final String requestTarget) {
        this.requestTarget = requireNonNull(requestTarget);
        invalidateParsedUri();
        return this;
    }

    @Override
    public final String getRawPath() {
        return lazyDecodeQueryString().rawPath();
    }

    @Override
    public final String getPath() {
        return lazyDecodeQueryString().path();
    }

    @Override
    public HttpRequestMetaData setPath(String path) {
        if (!path.isEmpty() && path.charAt(0) != '/') {
            path = "/" + path;
        }
        final String encodedPath;
        try {
            // TODO This is an ugly hack!
            encodedPath = encode(path, REQUEST_TARGET_CHARSET.name()).replaceAll("%2F", "/");
        } catch (final UnsupportedEncodingException e) {
            throw new UnsupportedCharsetException(REQUEST_TARGET_CHARSET.name());
        }
        setRequestTarget(encodeRequestTarget(encodedPath, getRawQuery(), null));
        return this;
    }

    @Override
    public HttpRequestMetaData setRawPath(final String path) {
        if (!path.isEmpty() && path.charAt(0) != '/') {
            throw new IllegalArgumentException("Path must be empty or start with '/'");
        }
        setRequestTarget(encodeRequestTarget(path, getRawQuery(), null));
        return this;
    }

    @Override
    public final HttpQuery parseQuery() {
        return new DefaultHttpQuery(lazyParseQueryString(), this::setQueryParams);
    }

    @Override
    public final String getRawQuery() {
        return lazyDecodeQueryString().rawQuery();
    }

    @Override
    public HttpRequestMetaData setRawQuery(final String query) {
        setRequestTarget(encodeRequestTarget(getRawPath(), requireNonNull(query), null));
        return this;
    }

    private Map<String, List<String>> lazyParseQueryString() {
        if (queryString == null) {
            queryString = new HashMap<>(lazyDecodeQueryString().parameters());
        }
        return queryString;
    }

    private QueryStringDecoder lazyDecodeQueryString() {
        if (queryStringDecoder == null) {
            queryStringDecoder = new QueryStringDecoder(lazyParseRequestTarget().getRequestTarget());
        }
        return queryStringDecoder;
    }

    private HttpUri lazyParseRequestTarget() {
        if (uri == null) {
            uri = new HttpUri(getRequestTarget());
        }
        return uri;
    }

    // package-private for testing.
    void setQueryParams(final Map<String, List<String>> params) {
        final QueryStringEncoder encoder = new QueryStringEncoder(getRawPath());

        for (final Map.Entry<String, List<String>> entry : params.entrySet()) {
            for (final String value : entry.getValue()) {
                encoder.addParam(entry.getKey(), value);
            }
        }

        setRequestTarget(encodeRequestTarget(null, null, encoder.toString()));
    }

    private String encodeRequestTarget(@Nullable final String path, @Nullable final String query,
                                       @Nullable final String file) {
        final HttpUri uri = lazyParseRequestTarget();
        return buildRequestTarget(
                uri.isSsl() ? "https" : "http",
                uri.getHost(),
                uri.hasExplicitPort() ? uri.getPort() : null,
                path,
                query,
                file);
    }

    private void invalidateParsedUri() {
        this.uri = null;
        this.queryString = null;
        this.queryStringDecoder = null;
    }

    @Override
    public final String toString(
            final BiFunction<? super CharSequence, ? super CharSequence, CharSequence> headerFilter) {
        return getMethod().toString() + " " + getRequestTarget() + " " + getVersion() + lineSeparator()
                + getHeaders().toString(headerFilter);
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
}

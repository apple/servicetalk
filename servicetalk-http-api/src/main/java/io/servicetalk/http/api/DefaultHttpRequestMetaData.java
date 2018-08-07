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
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.BiFunction;
import javax.annotation.Nullable;

import static io.servicetalk.http.api.HttpHeaderNames.HOST;
import static io.servicetalk.http.api.HttpUri.buildRequestTarget;
import static io.servicetalk.http.api.QueryStringDecoder.decodeParams;
import static java.lang.System.lineSeparator;
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
    private Map<String, List<String>> queryString;
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
        this.queryString = requestMetaData.queryString;
        this.requestTargetUri = requestMetaData.requestTargetUri;
        this.effectiveRequestHost = requestMetaData.effectiveRequestHost;
        this.effectiveRequestPort = requestMetaData.effectiveRequestPort;
        this.effectiveRequestHostHeader = requestMetaData.effectiveRequestHostHeader;
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

    @Nullable
    @Override
    public String getScheme() {
        return lazyParseRequestTarget().getScheme();
    }

    @Nullable
    @Override
    public String getUserInfo() {
        return lazyParseRequestTarget().getUserInfo();
    }

    @Nullable
    @Override
    public String getHost() {
        return lazyParseRequestTarget().getHost();
    }

    @Override
    public int getPort() {
        return lazyParseRequestTarget().getExplicitPort();
    }

    @Override
    public final String getRawPath() {
        return lazyParseRequestTarget().getRawPath();
    }

    @Override
    public final String getPath() {
        return lazyParseRequestTarget().getPath();
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
        return lazyParseRequestTarget().getRawQuery();
    }

    @Override
    public HttpRequestMetaData setRawQuery(final String query) {
        setRequestTarget(encodeRequestTarget(getRawPath(), requireNonNull(query), null));
        return this;
    }

    @Nullable
    @Override
    public String getEffectiveHost() {
        lazyParseEffectiveRequest();
        return effectiveRequestHost;
    }

    @Override
    public int getEffectivePort() {
        lazyParseEffectiveRequest();
        return effectiveRequestPort;
    }

    private Map<String, List<String>> lazyParseQueryString() {
        if (queryString == null) {
            queryString = decodeParams(lazyParseRequestTarget().getRawQuery());
        }
        return queryString;
    }

    private HttpUri lazyParseRequestTarget() {
        if (requestTargetUri == null) {
            requestTargetUri = new HttpUri(getRequestTarget());
        }
        return requestTargetUri;
    }

    private void lazyParseEffectiveRequest() {
        final CharSequence hostHeader = getHeaders().get(HOST);

        if (effectiveRequestPort == PORT_NOT_ASSIGNED || !Objects.equals(hostHeader, effectiveRequestHostHeader)) {
            final HttpUri effectiveRequestUri = new HttpUri(getRequestTarget(), () -> StringUtil.toString(hostHeader));
            effectiveRequestHost = effectiveRequestUri.getHost();
            effectiveRequestPort = effectiveRequestUri.getExplicitPort();
            effectiveRequestHostHeader = hostHeader;
        }
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

    private String encodeRequestTarget(@Nullable final String path,
                                       @Nullable final String query,
                                       @Nullable final String relativeReference) {
        final HttpUri uri = lazyParseRequestTarget();
        final String scheme = uri.getScheme();
        return buildRequestTarget(
                scheme != null ? scheme : "http",
                uri.getHost(),
                uri.getExplicitPort(),
                path,
                query,
                relativeReference);
    }

    private void invalidateParsedUri() {
        requestTargetUri = null;
        effectiveRequestPort = PORT_NOT_ASSIGNED;
        effectiveRequestHost = null;
        effectiveRequestHostHeader = null;
        queryString = null;
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

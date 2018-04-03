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
package io.servicetalk.http.netty;

import io.servicetalk.http.api.HttpHeaders;
import io.servicetalk.http.api.HttpProtocolVersion;
import io.servicetalk.http.api.HttpQuery;
import io.servicetalk.http.api.HttpRequestMetaData;
import io.servicetalk.http.api.HttpRequestMethod;

import io.netty.handler.codec.http.HttpRequest;
import io.netty.handler.codec.http.QueryStringDecoder;

import java.io.UnsupportedEncodingException;
import java.nio.charset.Charset;
import java.nio.charset.UnsupportedCharsetException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiFunction;
import javax.annotation.Nullable;

import static io.servicetalk.http.netty.HttpUri.buildRequestTarget;
import static io.servicetalk.http.netty.NettyHttpProtocolVersionConverter.fromNettyHttpVersion;
import static io.servicetalk.http.netty.NettyHttpProtocolVersionConverter.toNettyHttpVersion;
import static io.servicetalk.http.netty.NettyHttpRequestMethodConverter.fromNettyHttpMethod;
import static io.servicetalk.http.netty.NettyHttpRequestMethodConverter.toNettyHttpMethod;
import static java.lang.System.lineSeparator;
import static java.net.URLEncoder.encode;
import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.Objects.requireNonNull;

class NettyToServiceTalkHttpRequestMetaData extends NettyToServiceTalkHttpHeaders implements HttpRequestMetaData {

    private static final Charset REQUEST_TARGET_CHARSET = UTF_8;

    private final HttpRequest nettyHttpRequest;
    @Nullable
    private QueryStringDecoder queryStringDecoder;
    @Nullable
    private Map<String, List<String>> queryString;
    @Nullable
    private HttpUri uri;

    NettyToServiceTalkHttpRequestMetaData(final HttpRequest nettyHttpRequest) {
        super(nettyHttpRequest.headers());
        this.nettyHttpRequest = nettyHttpRequest;
    }

    @Override
    public HttpProtocolVersion getVersion() {
        return fromNettyHttpVersion(nettyHttpRequest.protocolVersion());
    }

    @Override
    public HttpRequestMetaData setVersion(final HttpProtocolVersion version) {
        nettyHttpRequest.setProtocolVersion(toNettyHttpVersion(version));
        return this;
    }

    @Override
    public HttpRequestMethod getMethod() {
        return fromNettyHttpMethod(nettyHttpRequest.method());
    }

    @Override
    public HttpRequestMetaData setMethod(final HttpRequestMethod method) {
        nettyHttpRequest.setMethod(toNettyHttpMethod(method));
        return this;
    }

    @Override
    public String getRequestTarget() {
        return nettyHttpRequest.uri();
    }

    @Override
    public HttpRequestMetaData setRequestTarget(final String requestTarget) {
        nettyHttpRequest.setUri(requestTarget);
        invalidateParsedUri();
        return this;
    }

    @Override
    public String getRawPath() {
        return lazyDecodeQueryString().rawPath();
    }

    @Override
    public String getPath() {
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
        setRequestTarget(encodeRequestTargetWithNewPath(encodedPath));
        return this;
    }

    @Override
    public HttpRequestMetaData setRawPath(final String path) {
        if (!path.isEmpty() && path.charAt(0) != '/') {
            throw new IllegalArgumentException("Path must be empty or start with '/'");
        }
        setRequestTarget(encodeRequestTargetWithNewPath(path));
        return this;
    }

    @Override
    public HttpQuery parseQuery() {
        return new DefaultHttpQuery(lazyParseQueryString(), this::getRawPath, this::setRequestTarget);
    }

    @Override
    public String getRawQuery() {
        return lazyDecodeQueryString().rawQuery();
    }

    @Override
    public HttpRequestMetaData setRawQuery(final String query) {
        setRequestTarget(encodeRequestTargetWithNewQuery(requireNonNull(query)));
        return this;
    }

    @Override
    public HttpHeaders getHeaders() {
        return this;
    }

    private void invalidateParsedUri() {
        this.uri = null;
        this.queryString = null;
        this.queryStringDecoder = null;
    }

    @Override
    public String toString(final BiFunction<? super CharSequence, ? super CharSequence, CharSequence> headerFilter) {
        return getMethod().toString() + " " + getRequestTarget() + " " + getVersion() + lineSeparator()
                + getHeaders().toString(headerFilter);
    }

    private Map<String, List<String>> lazyParseQueryString() {
        if (queryString == null) {
            queryString = new HashMap<>(lazyDecodeQueryString().parameters());
        }
        return queryString;
    }

    private QueryStringDecoder lazyDecodeQueryString() {
        if (queryStringDecoder == null) {
            queryStringDecoder = new QueryStringDecoder(lazyParseRequestTarget().getRequestTarget(), REQUEST_TARGET_CHARSET);
        }
        return queryStringDecoder;
    }

    private HttpUri lazyParseRequestTarget() {
        if (uri == null) {
            uri = new HttpUri(getRequestTarget());
        }
        return uri;
    }

    private String encodeRequestTargetWithNewPath(final String path) {
        final HttpUri uri = lazyParseRequestTarget();
        return buildRequestTarget(
                uri.isSsl() ? "https" : "http",
                uri.getHost(),
                uri.hasExplicitPort() ? uri.getPort() : null,
                path,
                getRawQuery());
    }

    private String encodeRequestTargetWithNewQuery(final String query) {
        final HttpUri uri = lazyParseRequestTarget();
        return buildRequestTarget(
                uri.isSsl() ? "https" : "http",
                uri.getHost(),
                uri.hasExplicitPort() ? uri.getPort() : null,
                getRawPath(),
                query);
    }

    final HttpRequest getNettyHttpRequest() {
        return nettyHttpRequest;
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

        final NettyToServiceTalkHttpRequestMetaData entries = (NettyToServiceTalkHttpRequestMetaData) o;

        return nettyHttpRequest.equals(entries.nettyHttpRequest);
    }

    @Override
    public int hashCode() {
        int result = super.hashCode();
        result = 31 * result + nettyHttpRequest.hashCode();
        return result;
    }
}

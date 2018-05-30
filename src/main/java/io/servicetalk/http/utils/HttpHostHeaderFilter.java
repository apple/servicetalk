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
package io.servicetalk.http.utils;

import io.servicetalk.concurrent.api.Single;
import io.servicetalk.http.api.HttpClient;
import io.servicetalk.http.api.HttpConnection;
import io.servicetalk.http.api.HttpHeaderNames;
import io.servicetalk.http.api.HttpPayloadChunk;
import io.servicetalk.http.api.HttpRequest;
import io.servicetalk.http.api.HttpRequester;
import io.servicetalk.http.api.HttpResponse;
import io.servicetalk.transport.api.HostAndPort;

import java.net.InetSocketAddress;
import java.util.function.BiFunction;

import static io.servicetalk.http.api.CharSequences.newAsciiString;
import static io.servicetalk.http.api.HttpHeaderNames.HOST;
import static io.servicetalk.http.api.HttpProtocolVersions.HTTP_1_1;
import static java.util.Objects.requireNonNull;

/**
 * A filter which will apply a fallback value for the {@link HttpHeaderNames#HOST} header if one is not present.
 */
public final class HttpHostHeaderFilter
        implements BiFunction<HttpRequester, HttpRequest<HttpPayloadChunk>, Single<HttpResponse<HttpPayloadChunk>>> {
    private final CharSequence fallbackHost;

    private HttpHostHeaderFilter(CharSequence fallbackHost) {
        this.fallbackHost = requireNonNull(fallbackHost);
    }

    /**
     * Create a new instance.
     * @param fallbackHost The address to use as a fallback if a {@link HttpHeaderNames#HOST} header is not present.
     * @param next The next {@link HttpConnection} in the filter chain.
     * @return {@link HttpConnection} filter that will apply the {@code fallbackHost} if a {@link HttpHeaderNames#HOST}
     * header is not present.
     */
    public static HttpConnection newHostHeaderFilter(InetSocketAddress fallbackHost, HttpConnection next) {
        return newHostHeaderFilter(fallbackHost.getHostString(), fallbackHost.getPort(), next);
    }

    /**
     * Create a new instance.
     * @param fallbackHost The address to use as a fallback if a {@link HttpHeaderNames#HOST} header is not present.
     * @param next The next {@link HttpConnection} in the filter chain.
     * @return {@link HttpConnection} filter that will apply the {@code fallbackHost} if a {@link HttpHeaderNames#HOST}
     * header is not present.
     */
    public static HttpConnection newHostHeaderFilter(HostAndPort fallbackHost, HttpConnection next) {
        return newHostHeaderFilter(fallbackHost.getHostName(), fallbackHost.getPort(), next);
    }

    /**
     * Create a new instance.
     * @param fallbackHostName The host name to use as a fallback if a {@link HttpHeaderNames#HOST} header is not
     * present.
     * @param fallbackPort The port to use as a fallback if a {@link HttpHeaderNames#HOST} header is not present.
     * @param next The next {@link HttpConnection} in the filter chain.
     * @return {@link HttpConnection} filter that will apply the {@code fallbackHost} if a {@link HttpHeaderNames#HOST}
     * header is not present.
     */
    public static HttpConnection newHostHeaderFilter(String fallbackHostName, int fallbackPort, HttpConnection next) {
        return newHostHeaderFilter(newAsciiString(fallbackHostName + ':' + fallbackPort), next);
    }

    /**
     * Create a new instance.
     * @param fallbackHost The address to use as a fallback if a {@link HttpHeaderNames#HOST} header is not present.
     * @param next The next {@link HttpConnection} in the filter chain.
     * @return {@link HttpConnection} filter that will apply the {@code fallbackHost} if a {@link HttpHeaderNames#HOST}
     * header is not present.
     */
    public static HttpConnection newHostHeaderFilter(String fallbackHost, HttpConnection next) {
        return newHostHeaderFilter(newAsciiString(fallbackHost), next);
    }

    /**
     * Create a new instance.
     * @param fallbackHost The address to use as a fallback if a {@link HttpHeaderNames#HOST} header is not present.
     * @param next The next {@link HttpConnection} in the filter chain.
     * @return {@link HttpConnection} filter that will apply the {@code fallbackHost} if a {@link HttpHeaderNames#HOST}
     * header is not present.
     */
    public static HttpConnection newHostHeaderFilter(CharSequence fallbackHost, HttpConnection next) {
        return new HttpConnectionFunctionFilter(new HttpHostHeaderFilter(fallbackHost), next);
    }

    /**
     * Create a new instance.
     * @param fallbackHost The address to use as a fallback if a {@link HttpHeaderNames#HOST} header is not present.
     * @param next The next {@link HttpClient} in the filter chain.
     * @return {@link HttpClient} filter that will apply the {@code fallbackHost} if a {@link HttpHeaderNames#HOST}
     * header is not present.
     */
    public static HttpClient newHostHeaderFilter(InetSocketAddress fallbackHost, HttpClient next) {
        return newHostHeaderFilter(fallbackHost.getHostString(), fallbackHost.getPort(), next);
    }

    /**
     * Create a new instance.
     * @param fallbackHost The address to use as a fallback if a {@link HttpHeaderNames#HOST} header is not present.
     * @param next The next {@link HttpClient} in the filter chain.
     * @return {@link HttpClient} filter that will apply the {@code fallbackHost} if a {@link HttpHeaderNames#HOST}
     * header is not present.
     */
    public static HttpClient newHostHeaderFilter(HostAndPort fallbackHost, HttpClient next) {
        return newHostHeaderFilter(fallbackHost.getHostName(), fallbackHost.getPort(), next);
    }

    /**
     * Create a new instance.
     * @param fallbackHostName The host name to use as a fallback if a {@link HttpHeaderNames#HOST} header is not
     * present.
     * @param fallbackPort The port to use as a fallback if a {@link HttpHeaderNames#HOST} header is not present.
     * @param next The next {@link HttpClient} in the filter chain.
     * @return {@link HttpClient} filter that will apply the {@code fallbackHost} if a {@link HttpHeaderNames#HOST}
     * header is not present.
     */
    public static HttpClient newHostHeaderFilter(String fallbackHostName, int fallbackPort, HttpClient next) {
        return newHostHeaderFilter(newAsciiString(fallbackHostName + ':' + fallbackPort), next);
    }

    /**
     * Create a new instance.
     * @param fallbackHost The address to use as a fallback if a {@link HttpHeaderNames#HOST} header is not present.
     * @param next The next {@link HttpClient} in the filter chain.
     * @return {@link HttpClient} filter that will apply the {@code fallbackHost} if a {@link HttpHeaderNames#HOST}
     * header is not present.
     */
    public static HttpClient newHostHeaderFilter(String fallbackHost, HttpClient next) {
        return newHostHeaderFilter(newAsciiString(fallbackHost), next);
    }

    /**
     * Create a new instance.
     * @param fallbackHost The address to use as a fallback if a {@link HttpHeaderNames#HOST} header is not present.
     * @param next The next {@link HttpClient} in the filter chain.
     * @return {@link HttpClient} filter that will apply the {@code fallbackHost} if a {@link HttpHeaderNames#HOST}
     * header is not present.
     */
    public static HttpClient newHostHeaderFilter(CharSequence fallbackHost, HttpClient next) {
        return new HttpClientFunctionFilter(new HttpHostHeaderFilter(fallbackHost), next);
    }

    @Override
    public Single<HttpResponse<HttpPayloadChunk>> apply(final HttpRequester requester,
                                                        final HttpRequest<HttpPayloadChunk> request) {
        if (request.getVersion() == HTTP_1_1 && !request.getHeaders().contains(HOST)) {
            request.getHeaders().set(HOST, fallbackHost);
        }
        return requester.request(request);
    }
}

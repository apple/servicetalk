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
import io.servicetalk.http.api.HttpHeaderNames;
import io.servicetalk.http.api.HttpPayloadChunk;
import io.servicetalk.http.api.StreamingHttpClient;
import io.servicetalk.http.api.StreamingHttpConnection;
import io.servicetalk.http.api.StreamingHttpRequest;
import io.servicetalk.http.api.StreamingHttpRequester;
import io.servicetalk.http.api.StreamingHttpResponse;
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
        implements BiFunction<StreamingHttpRequester, StreamingHttpRequest<HttpPayloadChunk>, Single<StreamingHttpResponse<HttpPayloadChunk>>> {
    private final CharSequence fallbackHost;

    private HttpHostHeaderFilter(CharSequence fallbackHost) {
        this.fallbackHost = requireNonNull(fallbackHost);
    }

    /**
     * Create a new instance.
     * @param fallbackHost The address to use as a fallback if a {@link HttpHeaderNames#HOST} header is not present.
     * @param next The next {@link StreamingHttpConnection} in the filter chain.
     * @return {@link StreamingHttpConnection} filter that will apply the {@code fallbackHost} if a {@link HttpHeaderNames#HOST}
     * header is not present.
     */
    public static StreamingHttpConnection newHostHeaderFilter(InetSocketAddress fallbackHost, StreamingHttpConnection next) {
        return newHostHeaderFilter(fallbackHost.getHostString(), fallbackHost.getPort(), next);
    }

    /**
     * Create a new instance.
     * @param fallbackHost The address to use as a fallback if a {@link HttpHeaderNames#HOST} header is not present.
     * @param next The next {@link StreamingHttpConnection} in the filter chain.
     * @return {@link StreamingHttpConnection} filter that will apply the {@code fallbackHost} if a {@link HttpHeaderNames#HOST}
     * header is not present.
     */
    public static StreamingHttpConnection newHostHeaderFilter(HostAndPort fallbackHost, StreamingHttpConnection next) {
        return newHostHeaderFilter(fallbackHost.getHostName(), fallbackHost.getPort(), next);
    }

    /**
     * Create a new instance.
     * @param fallbackHostName The host name to use as a fallback if a {@link HttpHeaderNames#HOST} header is not
     * present.
     * @param fallbackPort The port to use as a fallback if a {@link HttpHeaderNames#HOST} header is not present.
     * @param next The next {@link StreamingHttpConnection} in the filter chain.
     * @return {@link StreamingHttpConnection} filter that will apply the {@code fallbackHost} if a {@link HttpHeaderNames#HOST}
     * header is not present.
     */
    public static StreamingHttpConnection newHostHeaderFilter(String fallbackHostName, int fallbackPort, StreamingHttpConnection next) {
        return newHostHeaderFilter(newAsciiString(fallbackHostName + ':' + fallbackPort), next);
    }

    /**
     * Create a new instance.
     * @param fallbackHost The address to use as a fallback if a {@link HttpHeaderNames#HOST} header is not present.
     * @param next The next {@link StreamingHttpConnection} in the filter chain.
     * @return {@link StreamingHttpConnection} filter that will apply the {@code fallbackHost} if a {@link HttpHeaderNames#HOST}
     * header is not present.
     */
    public static StreamingHttpConnection newHostHeaderFilter(String fallbackHost, StreamingHttpConnection next) {
        return newHostHeaderFilter(newAsciiString(fallbackHost), next);
    }

    /**
     * Create a new instance.
     * @param fallbackHost The address to use as a fallback if a {@link HttpHeaderNames#HOST} header is not present.
     * @param next The next {@link StreamingHttpConnection} in the filter chain.
     * @return {@link StreamingHttpConnection} filter that will apply the {@code fallbackHost} if a {@link HttpHeaderNames#HOST}
     * header is not present.
     */
    public static StreamingHttpConnection newHostHeaderFilter(CharSequence fallbackHost, StreamingHttpConnection next) {
        return new StreamingHttpConnectionFunctionFilter(new HttpHostHeaderFilter(fallbackHost), next);
    }

    /**
     * Create a new instance.
     * @param fallbackHost The address to use as a fallback if a {@link HttpHeaderNames#HOST} header is not present.
     * @param next The next {@link StreamingHttpClient} in the filter chain.
     * @return {@link StreamingHttpClient} filter that will apply the {@code fallbackHost} if a {@link HttpHeaderNames#HOST}
     * header is not present.
     */
    public static StreamingHttpClient newHostHeaderFilter(InetSocketAddress fallbackHost, StreamingHttpClient next) {
        return newHostHeaderFilter(fallbackHost.getHostString(), fallbackHost.getPort(), next);
    }

    /**
     * Create a new instance.
     * @param fallbackHost The address to use as a fallback if a {@link HttpHeaderNames#HOST} header is not present.
     * @param next The next {@link StreamingHttpClient} in the filter chain.
     * @return {@link StreamingHttpClient} filter that will apply the {@code fallbackHost} if a {@link HttpHeaderNames#HOST}
     * header is not present.
     */
    public static StreamingHttpClient newHostHeaderFilter(HostAndPort fallbackHost, StreamingHttpClient next) {
        return newHostHeaderFilter(fallbackHost.getHostName(), fallbackHost.getPort(), next);
    }

    /**
     * Create a new instance.
     * @param fallbackHostName The host name to use as a fallback if a {@link HttpHeaderNames#HOST} header is not
     * present.
     * @param fallbackPort The port to use as a fallback if a {@link HttpHeaderNames#HOST} header is not present.
     * @param next The next {@link StreamingHttpClient} in the filter chain.
     * @return {@link StreamingHttpClient} filter that will apply the {@code fallbackHost} if a {@link HttpHeaderNames#HOST}
     * header is not present.
     */
    public static StreamingHttpClient newHostHeaderFilter(String fallbackHostName, int fallbackPort, StreamingHttpClient next) {
        return newHostHeaderFilter(newAsciiString(fallbackHostName + ':' + fallbackPort), next);
    }

    /**
     * Create a new instance.
     * @param fallbackHost The address to use as a fallback if a {@link HttpHeaderNames#HOST} header is not present.
     * @param next The next {@link StreamingHttpClient} in the filter chain.
     * @return {@link StreamingHttpClient} filter that will apply the {@code fallbackHost} if a {@link HttpHeaderNames#HOST}
     * header is not present.
     */
    public static StreamingHttpClient newHostHeaderFilter(String fallbackHost, StreamingHttpClient next) {
        return newHostHeaderFilter(newAsciiString(fallbackHost), next);
    }

    /**
     * Create a new instance.
     * @param fallbackHost The address to use as a fallback if a {@link HttpHeaderNames#HOST} header is not present.
     * @param next The next {@link StreamingHttpClient} in the filter chain.
     * @return {@link StreamingHttpClient} filter that will apply the {@code fallbackHost} if a {@link HttpHeaderNames#HOST}
     * header is not present.
     */
    public static StreamingHttpClient newHostHeaderFilter(CharSequence fallbackHost, StreamingHttpClient next) {
        return new StreamingHttpClientFunctionFilter(new HttpHostHeaderFilter(fallbackHost), next);
    }

    @Override
    public Single<StreamingHttpResponse<HttpPayloadChunk>> apply(final StreamingHttpRequester requester,
                                                                 final StreamingHttpRequest<HttpPayloadChunk> request) {
        if (request.getVersion() == HTTP_1_1 && !request.getHeaders().contains(HOST)) {
            request.getHeaders().set(HOST, fallbackHost);
        }
        return requester.request(request);
    }
}

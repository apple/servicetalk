/*
 * Copyright © 2018-2022 Apple Inc. and the ServiceTalk project authors
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
import io.servicetalk.context.api.ContextMap;
import io.servicetalk.http.api.FilterableStreamingHttpClient;
import io.servicetalk.http.api.FilterableStreamingHttpConnection;
import io.servicetalk.http.api.HttpExecutionStrategies;
import io.servicetalk.http.api.HttpExecutionStrategy;
import io.servicetalk.http.api.HttpHeaderNames;
import io.servicetalk.http.api.HttpRequestMetaData;
import io.servicetalk.http.api.StreamingHttpClientFilter;
import io.servicetalk.http.api.StreamingHttpClientFilterFactory;
import io.servicetalk.http.api.StreamingHttpConnectionFilter;
import io.servicetalk.http.api.StreamingHttpConnectionFilterFactory;
import io.servicetalk.http.api.StreamingHttpRequest;
import io.servicetalk.http.api.StreamingHttpRequester;
import io.servicetalk.http.api.StreamingHttpResponse;

import static io.servicetalk.buffer.api.CharSequences.newAsciiString;
import static io.servicetalk.concurrent.api.Single.defer;
import static io.servicetalk.http.api.HttpHeaderNames.HOST;
import static io.servicetalk.http.api.HttpProtocolVersion.HTTP_1_0;
import static io.servicetalk.utils.internal.NetworkUtils.isValidIpV6Address;
import static java.util.Objects.requireNonNull;

/**
 * A filter which will set a {@link HttpHeaderNames#HOST} header with the fallback value if the header is not already
 * present in {@link HttpRequestMetaData}.
 */
public final class HostHeaderHttpRequesterFilter implements StreamingHttpClientFilterFactory,
                                                            StreamingHttpConnectionFilterFactory {

    // TODO: docs, and this feels hacky.
    public static final ContextMap.Key<CharSequence> AUTHORITY_KEY =
            ContextMap.Key.newKey("RequestTarget", CharSequence.class);

    private final CharSequence fallbackHost;
    private final boolean preferSynthesizeFromRequest;

    /**
     * Create a new instance.
     *
     * @param fallbackHost The address to use as a fallback if a {@link HttpHeaderNames#HOST} header is not present.
     */
    public HostHeaderHttpRequesterFilter(final CharSequence fallbackHost) {
        this(fallbackHost, false);
    }

    public HostHeaderHttpRequesterFilter(final CharSequence fallbackHost,
                                         final boolean preferSynthesizeFromRequest) {
        this.fallbackHost = newAsciiString(toHostHeader(fallbackHost));
        this.preferSynthesizeFromRequest = preferSynthesizeFromRequest;
    }



    @Override
    public StreamingHttpClientFilter create(final FilterableStreamingHttpClient client) {
        return new StreamingHttpClientFilter(client) {

            @Override
            protected Single<StreamingHttpResponse> request(final StreamingHttpRequester delegate,
                                                            final StreamingHttpRequest request) {
                return HostHeaderHttpRequesterFilter.this.request(delegate, request);
            }
        };
    }

    @Override
    public StreamingHttpConnectionFilter create(final FilterableStreamingHttpConnection connection) {
        return new StreamingHttpConnectionFilter(connection) {
            @Override
            public Single<StreamingHttpResponse> request(final StreamingHttpRequest request) {
                return HostHeaderHttpRequesterFilter.this.request(delegate(), request);
            }
        };
    }

    @Override
    public HttpExecutionStrategy requiredOffloads() {
        // No influence since we do not block.
        return HttpExecutionStrategies.offloadNone();
    }

    private Single<StreamingHttpResponse> request(final StreamingHttpRequester delegate,
                                                  final StreamingHttpRequest request) {
        return defer(() -> {
            // "Host" header is not required for HTTP/1.0
            if (!HTTP_1_0.equals(request.version()) && !request.headers().contains(HOST)) {
                setHostHeader(request);
            }
            return delegate.request(request).shareContextOnSubscribe();
        });
    }

    private void setHostHeader(final HttpRequestMetaData request) {
        CharSequence hostHeaderValue;
        if (preferSynthesizeFromRequest) {
            CharSequence requestAuthority = request.context().get(AUTHORITY_KEY);
            hostHeaderValue = requestAuthority != null ? requestAuthority : fallbackHost;
        } else {
            hostHeaderValue = fallbackHost;
        }
        request.setHeader(HOST, hostHeaderValue);
    }

    private static CharSequence toHostHeader(CharSequence host) {
        requireNonNull(host, "fallbackHost");
        if (isValidIpV6Address(host) && host.charAt(0) != '[') {
            host = "[" + host + "]";
        }
        return host;
    }
}

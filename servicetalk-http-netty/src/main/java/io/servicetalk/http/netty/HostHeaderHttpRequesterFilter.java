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

import io.servicetalk.concurrent.api.Publisher;
import io.servicetalk.concurrent.api.Single;
import io.servicetalk.http.api.HttpClientFilterFactory;
import io.servicetalk.http.api.HttpConnectionFilterFactory;
import io.servicetalk.http.api.HttpExecutionStrategy;
import io.servicetalk.http.api.HttpHeaderNames;
import io.servicetalk.http.api.StreamingHttpClientFilter;
import io.servicetalk.http.api.StreamingHttpConnectionFilter;
import io.servicetalk.http.api.StreamingHttpRequest;
import io.servicetalk.http.api.StreamingHttpRequestFunction;
import io.servicetalk.http.api.StreamingHttpResponse;
import io.servicetalk.transport.api.HostAndPort;

import static io.netty.util.NetUtil.isValidIpV6Address;
import static io.netty.util.NetUtil.toSocketAddressString;
import static io.servicetalk.http.api.CharSequences.newAsciiString;
import static io.servicetalk.http.api.HttpHeaderNames.HOST;
import static io.servicetalk.http.api.HttpProtocolVersion.HTTP_1_1;
import static java.util.Objects.requireNonNull;

/**
 * A filter which will apply a fallback value for the {@link HttpHeaderNames#HOST} header if one is not present.
 */
final class HostHeaderHttpRequesterFilter implements HttpClientFilterFactory,
                                                     HttpConnectionFilterFactory {
    private final CharSequence fallbackHost;

    /**
     * Create a new instance.
     * @param fallbackHost The address to use as a fallback if a {@link HttpHeaderNames#HOST} header is not present.
     */
    HostHeaderHttpRequesterFilter(HostAndPort fallbackHost) {
        this(fallbackHost.hostName(), fallbackHost.port());
    }

    /**
     * Create a new instance.
     * @param fallbackHostName The host name to use as a fallback if a {@link HttpHeaderNames#HOST} header is not
     * present.
     * @param fallbackPort The port to use as a fallback if a {@link HttpHeaderNames#HOST} header is not present.
     */
    HostHeaderHttpRequesterFilter(String fallbackHostName, int fallbackPort) {
        this.fallbackHost = requireNonNull(newAsciiString(toSocketAddressString(fallbackHostName, fallbackPort)));
    }

    /**
     * Create a new instance.
     * @param fallbackHost The address to use as a fallback if a {@link HttpHeaderNames#HOST} header is not present.
     */
    HostHeaderHttpRequesterFilter(CharSequence fallbackHost) {
        this.fallbackHost = newAsciiString(isValidIpV6Address(fallbackHost) && fallbackHost.charAt(0) != '[' ?
                "[" + fallbackHost + "]" : fallbackHost.toString());
    }

    @Override
    public StreamingHttpClientFilter create(final StreamingHttpClientFilter client, final Publisher<Object> lbEvents) {
        return new StreamingHttpClientFilter(client) {

            @Override
            protected Single<StreamingHttpResponse> request(final StreamingHttpRequestFunction delegate,
                                                            final HttpExecutionStrategy strategy,
                                                            final StreamingHttpRequest request) {
                return HostHeaderHttpRequesterFilter.this.request(delegate, strategy, request);
            }

            @Override
            protected HttpExecutionStrategy mergeForEffectiveStrategy(final HttpExecutionStrategy mergeWith) {
                // Since this filter does not have any blocking code, we do not need to alter the effective strategy.
                return mergeWith;
            }
        };
    }

    @Override
    public StreamingHttpConnectionFilter create(final StreamingHttpConnectionFilter connection) {
        return new StreamingHttpConnectionFilter(connection) {
            @Override
            protected Single<StreamingHttpResponse> request(final StreamingHttpConnectionFilter delegate,
                                                            final HttpExecutionStrategy strategy,
                                                            final StreamingHttpRequest request) {
                return HostHeaderHttpRequesterFilter.this.request(delegate, strategy, request);
            }

            @Override
            protected HttpExecutionStrategy mergeForEffectiveStrategy(final HttpExecutionStrategy mergeWith) {
                // Since this filter does not have any blocking code, we do not need to alter the effective strategy.
                return mergeWith;
            }
        };
    }

    private Single<StreamingHttpResponse> request(final StreamingHttpRequestFunction delegate,
                                                  final HttpExecutionStrategy strategy,
                                                  final StreamingHttpRequest request) {
        if (HTTP_1_1.equals(request.version()) && !request.headers().contains(HOST)) {
            request.headers().set(HOST, fallbackHost);
        }
        return delegate.request(strategy, request);
    }
}

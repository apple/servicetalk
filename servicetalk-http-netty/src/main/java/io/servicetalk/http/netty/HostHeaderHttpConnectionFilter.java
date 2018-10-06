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

import io.servicetalk.concurrent.api.Single;
import io.servicetalk.http.api.HttpHeaderNames;
import io.servicetalk.http.api.StreamingHttpConnection;
import io.servicetalk.http.api.StreamingHttpConnectionAdapter;
import io.servicetalk.http.api.StreamingHttpRequest;
import io.servicetalk.http.api.StreamingHttpResponse;
import io.servicetalk.transport.api.HostAndPort;

import static io.netty.util.NetUtil.isValidIpV6Address;
import static io.netty.util.NetUtil.toSocketAddressString;
import static io.servicetalk.http.api.CharSequences.newAsciiString;
import static io.servicetalk.http.api.HttpHeaderNames.HOST;
import static io.servicetalk.http.api.HttpProtocolVersions.HTTP_1_1;
import static java.util.Objects.requireNonNull;

/**
 * A filter which will apply a fallback value for the {@link HttpHeaderNames#HOST} header if one is not present.
 */
final class HostHeaderHttpConnectionFilter extends StreamingHttpConnectionAdapter {
    private final CharSequence fallbackHost;

    /**
     * Create a new instance.
     * @param fallbackHost The address to use as a fallback if a {@link HttpHeaderNames#HOST} header is not present.
     * @param next The next {@link StreamingHttpConnection} in the filter chain.
     */
    HostHeaderHttpConnectionFilter(HostAndPort fallbackHost, StreamingHttpConnection next) {
        this(fallbackHost.getHostName(), fallbackHost.getPort(), next);
    }

    /**
     * Create a new instance.
     * @param fallbackHostName The host name to use as a fallback if a {@link HttpHeaderNames#HOST} header is not
     * present.
     * @param fallbackPort The port to use as a fallback if a {@link HttpHeaderNames#HOST} header is not present.
     * @param next The next {@link StreamingHttpConnection} in the filter chain.
     */
    HostHeaderHttpConnectionFilter(String fallbackHostName, int fallbackPort,
                                   StreamingHttpConnection next) {
        super(next);
        this.fallbackHost = requireNonNull(newAsciiString(toSocketAddressString(fallbackHostName, fallbackPort)));
    }

    /**
     * Create a new instance.
     * @param fallbackHost The address to use as a fallback if a {@link HttpHeaderNames#HOST} header is not present.
     * @param next The next {@link StreamingHttpConnection} in the filter chain.
     */
    HostHeaderHttpConnectionFilter(CharSequence fallbackHost, StreamingHttpConnection next) {
        super(next);
        this.fallbackHost = newAsciiString(isValidIpV6Address(fallbackHost) && fallbackHost.charAt(0) != '[' ?
                "[" + fallbackHost + "]" : fallbackHost.toString());
    }

    @Override
    public Single<StreamingHttpResponse> request(final StreamingHttpRequest request) {
        if (request.version() == HTTP_1_1 && !request.headers().contains(HOST)) {
            request.headers().set(HOST, fallbackHost);
        }
        return delegate().request(request);
    }
}

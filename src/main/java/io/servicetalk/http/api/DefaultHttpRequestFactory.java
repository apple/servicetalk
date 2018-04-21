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

import static java.util.Objects.requireNonNull;

/**
 * Factory methods for creating {@link DefaultHttpRequest}s, for use by protocol decoders.
 */
public final class DefaultHttpRequestFactory implements HttpRequestFactory {

    public static final HttpRequestFactory INSTANCE = new DefaultHttpRequestFactory();
    private final HttpHeadersFactory httpHeadersFactory;
    private final HttpTrailersFactory httpTrailersFactory;

    /**
     * Create an instance of the factory with the default {@link DefaultHttpHeadersFactory}.
     */
    private DefaultHttpRequestFactory() {
        this(DefaultHttpHeadersFactory.INSTANCE);
    }

    /**
     * Create an instance of the factory.
     *
     * @param httpHeadersFactory the {@link HttpHeadersFactory} to use when creating requests.
     */
    public DefaultHttpRequestFactory(final HttpHeadersFactory httpHeadersFactory) {
        this(httpHeadersFactory, DefaultHttpTrailersFactory.INSTANCE);
    }

    /**
     * Create an instance of the factory.
     *
     * @param httpHeadersFactory  the {@link HttpHeadersFactory} to use when creating requests, if the {@code headers}
     *                            are not specified.
     * @param httpTrailersFactory the {@link HttpTrailersFactory} to use when creating <a href="https://tools.ietf.org/html/rfc7230#section-4.4">trailer headers</a>.
     */
    public DefaultHttpRequestFactory(final HttpHeadersFactory httpHeadersFactory, final HttpTrailersFactory httpTrailersFactory) {
        this.httpHeadersFactory = requireNonNull(httpHeadersFactory);
        this.httpTrailersFactory = requireNonNull(httpTrailersFactory);
    }

    @Override
    public HttpRequestMetaData newRequestMetaData(final HttpProtocolVersion version, final HttpRequestMethod method, final String requestTarget) {
        return new DefaultHttpRequestMetaData(method, requestTarget, version, httpHeadersFactory.newHeaders());
    }

    @Override
    public HttpHeaders newTrailers() {
        return httpTrailersFactory.newTrailers();
    }
}

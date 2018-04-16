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

import io.servicetalk.concurrent.api.Publisher;

import static java.util.Objects.requireNonNull;

/**
 * Factory methods for creating {@link DefaultHttpResponse}s, for use by protocol decoders.
 */
public final class DefaultHttpResponseFactory implements HttpResponseFactory {

    private final HttpHeadersFactory httpHeadersFactory;
    private final HttpTrailersFactory httpTrailersFactory;

    /**
     * Create an instance of the factory with the default {@link DefaultHttpHeadersFactory}.
     */
    public DefaultHttpResponseFactory() {
        this(DefaultHttpHeadersFactory.INSTANCE);
    }

    /**
     * Create an instance of the factory.
     *
     * @param httpHeadersFactory the {@link HttpHeadersFactory} to use when creating requests.
     */
    public DefaultHttpResponseFactory(final HttpHeadersFactory httpHeadersFactory) {
        this(httpHeadersFactory, DefaultHttpTrailersFactory.INSTANCE);
    }

    /**
     * Create an instance of the factory.
     *
     * @param httpHeadersFactory  the {@link HttpHeadersFactory} to use when creating requests, if the {@code headers}
     *                            are not specified.
     * @param httpTrailersFactory the {@link HttpTrailersFactory} to use when creating <a href="https://tools.ietf.org/html/rfc7230#section-4.4">trailer headers</a>.
     */
    public DefaultHttpResponseFactory(final HttpHeadersFactory httpHeadersFactory, final HttpTrailersFactory httpTrailersFactory) {
        this.httpHeadersFactory = requireNonNull(httpHeadersFactory);
        this.httpTrailersFactory = requireNonNull(httpTrailersFactory);
    }

    @Override
    public <I> HttpResponse<I> newResponse(final HttpProtocolVersion version, final HttpResponseStatus status, final Publisher<I> messageBody) {
        return new DefaultHttpResponse<>(status, version, httpHeadersFactory.newHeaders(), messageBody);
    }

    @Override
    public HttpHeaders newTrailers() {
        return httpTrailersFactory.newTrailers();
    }
}

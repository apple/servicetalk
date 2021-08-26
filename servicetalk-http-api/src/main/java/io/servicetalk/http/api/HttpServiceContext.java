/*
 * Copyright © 2018-2019 Apple Inc. and the ServiceTalk project authors
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

import io.servicetalk.transport.api.ServerListenContext;

import static java.util.Objects.requireNonNull;

/**
 * A {@link HttpConnectionContext} for use in the {@link HttpService} context.
 */
public abstract class HttpServiceContext implements HttpConnectionContext, ServerListenContext {
    private final HttpHeadersFactory headersFactory;
    private final HttpResponseFactory factory;
    private final StreamingHttpResponseFactory streamingFactory;
    private final BlockingStreamingHttpResponseFactory blockingFactory;

    /**
     * Create a new instance.
     *
     * @param headersFactory The {@link HttpHeadersFactory} used for API conversions
     * @param factory The {@link HttpResponseFactory} used for API conversions
     * @param streamingFactory The {@link StreamingHttpResponseFactory} used for API conversions
     * @param blockingFactory The {@link BlockingStreamingHttpResponseFactory} used for API conversions
     */
    protected HttpServiceContext(final HttpHeadersFactory headersFactory,
                                 final HttpResponseFactory factory,
                                 final StreamingHttpResponseFactory streamingFactory,
                                 final BlockingStreamingHttpResponseFactory blockingFactory) {
        this.headersFactory = requireNonNull(headersFactory);
        this.factory = requireNonNull(factory);
        this.streamingFactory = requireNonNull(streamingFactory);
        this.blockingFactory = requireNonNull(blockingFactory);
    }

    /**
     * Copy constructor.
     *
     * @param other {@link HttpServiceContext} to copy from.
     */
    protected HttpServiceContext(final HttpServiceContext other) {
        this(other.headersFactory(), other.responseFactory(), other.streamingResponseFactory(),
                other.blockingStreamingResponseFactory());
    }

    /**
     * Returns the {@link HttpHeadersFactory} associated with this {@link HttpServiceContext}.
     *
     * @return {@link HttpHeadersFactory} associated with this {@link HttpServiceContext}.
     */
    protected HttpHeadersFactory headersFactory() {
        return headersFactory;
    }

    /**
     * Returns the {@link HttpResponseFactory} associated with this {@link HttpServiceContext}.
     *
     * @return {@link HttpResponseFactory} associated with this {@link HttpServiceContext}.
     */
    protected HttpResponseFactory responseFactory() {
        return factory;
    }

    /**
     * Returns the {@link StreamingHttpResponseFactory} associated with this {@link HttpServiceContext}.
     *
     * @return {@link StreamingHttpResponseFactory} associated with this {@link HttpServiceContext}.
     */
    protected StreamingHttpResponseFactory streamingResponseFactory() {
        return streamingFactory;
    }

    /**
     * Returns the {@link BlockingStreamingHttpResponseFactory} associated with this {@link HttpServiceContext}.
     *
     * @return {@link BlockingStreamingHttpResponseFactory} associated with this {@link HttpServiceContext}.
     */
    protected BlockingStreamingHttpResponseFactory blockingStreamingResponseFactory() {
        return blockingFactory;
    }
}

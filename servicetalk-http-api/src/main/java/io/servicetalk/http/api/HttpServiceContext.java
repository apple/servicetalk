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

import io.servicetalk.transport.api.ConnectionContext;

import static java.util.Objects.requireNonNull;

/**
 * A {@link ConnectionContext} for use in the {@link HttpService} context.
 */
public abstract class HttpServiceContext implements ConnectionContext {
    private final HttpResponseFactory factory;
    private final StreamingHttpResponseFactory streamingFactory;
    private final BlockingStreamingHttpResponseFactory blockingFactory;

    /**
     * Create a new instance.
     * @param factory The {@link HttpResponseFactory} used for API conversions.
     * @param streamingFactory The {@link StreamingHttpResponseFactory} used for API conversions.
     * @param blockingFactory The {@link BlockingStreamingHttpResponseFactory} used for API conversions.
     */
    protected HttpServiceContext(HttpResponseFactory factory, StreamingHttpResponseFactory streamingFactory,
                                 BlockingStreamingHttpResponseFactory blockingFactory) {
        this.factory = requireNonNull(factory);
        this.streamingFactory = requireNonNull(streamingFactory);
        this.blockingFactory = requireNonNull(blockingFactory);
    }

    /**
     * Copy constructor.
     *
     * @param other {@link HttpServiceContext} to copy from.
     */
    protected HttpServiceContext(HttpServiceContext other) {
        this(other.factory, other.streamingFactory, other.blockingFactory);
    }

    final HttpResponseFactory getResponseFactory() {
        return factory;
    }

    final StreamingHttpResponseFactory getStreamingResponseFactory() {
        return streamingFactory;
    }

    final BlockingStreamingHttpResponseFactory getStreamingBlockingResponseFactory() {
        return blockingFactory;
    }
}

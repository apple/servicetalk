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

import io.servicetalk.concurrent.api.AsyncCloseable;
import io.servicetalk.concurrent.api.Completable;

import static io.servicetalk.concurrent.api.Completable.completed;
import static io.servicetalk.http.api.HttpExecutionStrategies.OFFLOAD_ALL_STRATEGY;

/**
 * A service contract for the HTTP protocol.
 */
public abstract class StreamingHttpService implements AsyncCloseable, StreamingHttpRequestHandler {
    /**
     * Closes this {@link StreamingHttpService} asynchronously.
     *
     * @return {@link Completable} that when subscribed will close this {@link StreamingHttpService}.
     */
    @Override
    public Completable closeAsync() {
        return completed();
    }

    @Override
    public final StreamingHttpService asStreamingService() {
        return this;
    }

    /**
     * Convert this {@link StreamingHttpService} to the {@link HttpService} API.
     *
     * @return a {@link HttpService} representation of this {@link StreamingHttpService}.
     */
    public final HttpService asService() {
        return asServiceInternal();
    }

    /**
     * Convert this {@link StreamingHttpService} to the {@link BlockingStreamingHttpService} API.
     * <p>
     * This API is provided for convenience for a more familiar sequential programming model. It is recommended that
     * filters are implemented using the {@link StreamingHttpService} asynchronous API for maximum portability.
     * @return a {@link BlockingStreamingHttpService} representation of this {@link StreamingHttpService}.
     */
    public final BlockingStreamingHttpService asBlockingStreamingService() {
        return asBlockingStreamingServiceInternal();
    }

    /**
     * Convert this {@link StreamingHttpService} to the {@link BlockingHttpService} API.
     * <p>
     * This API is provided for convenience for a more familiar sequential programming model. It is recommended that
     * filters are implemented using the {@link StreamingHttpService} asynchronous API for maximum portability.
     * @return a {@link BlockingHttpService} representation of this {@link StreamingHttpService}.
     */
    public final BlockingHttpService asBlockingService() {
        return asBlockingServiceInternal();
    }

    /**
     * Returns the {@link HttpExecutionStrategy} for this {@link StreamingHttpService}.
     *
     * @return The {@link HttpExecutionStrategy} for this {@link StreamingHttpService}.
     */
    public HttpExecutionStrategy executionStrategy() {
        return OFFLOAD_ALL_STRATEGY;
    }

    HttpService asServiceInternal() {
        return StreamingHttpServiceToHttpService.transform(this);
    }

    BlockingStreamingHttpService asBlockingStreamingServiceInternal() {
        return StreamingHttpServiceToBlockingStreamingHttpService.transform(this);
    }

    BlockingHttpService asBlockingServiceInternal() {
        return StreamingHttpServiceToBlockingHttpService.transform(this);
    }
}

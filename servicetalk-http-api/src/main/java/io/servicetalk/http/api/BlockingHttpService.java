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

import static io.servicetalk.http.api.HttpExecutionStrategies.OFFLOAD_RECEIVE_META_STRATEGY;

/**
 * The equivalent of {@link HttpService} but with synchronous/blocking APIs instead of asynchronous APIs.
 */
public abstract class BlockingHttpService implements AutoCloseable, BlockingHttpRequestHandler {

    @Override
    public void close() throws Exception {
        // noop
    }

    @Override
    public final BlockingHttpService asBlockingService() {
        return this;
    }

    /**
     * Convert this {@link BlockingHttpService} to the {@link StreamingHttpService} API.
     * <p>
     * This API is provided for convenience for a more familiar sequential programming model. It is recommended that
     * filters are implemented using the {@link StreamingHttpService} asynchronous API for maximum portability.
     *
     * @return a {@link StreamingHttpService} representation of this {@link BlockingHttpService}.
     */
    public final StreamingHttpService asStreamingService() {
        return asStreamingServiceInternal();
    }

    /**
     * Convert this {@link BlockingHttpService} to the {@link HttpService} API.
     * <p>
     * This API is provided for convenience for a more familiar sequential programming model. It is recommended that
     * filters are implemented using the {@link StreamingHttpService} asynchronous API for maximum portability.
     *
     * @return a {@link HttpService} representation of this {@link BlockingHttpService}.
     */
    public final HttpService asService() {
        return asServiceInternal();
    }

    /**
     * Returns the {@link HttpExecutionStrategy} for this {@link BlockingHttpService}.
     *
     * @return The {@link HttpExecutionStrategy} for this {@link BlockingHttpService}.
     */
    public HttpExecutionStrategy executionStrategy() {
        return OFFLOAD_RECEIVE_META_STRATEGY;
    }

    StreamingHttpService asStreamingServiceInternal() {
        return BlockingHttpServiceToStreamingHttpService.transform(this);
    }

    HttpService asServiceInternal() {
        return BlockingHttpServiceToHttpService.transform(this);
    }
}

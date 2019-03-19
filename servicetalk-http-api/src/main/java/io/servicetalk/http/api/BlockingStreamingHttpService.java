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

import static io.servicetalk.http.api.HttpExecutionStrategies.OFFLOAD_RECEIVE_META_AND_SEND_STRATEGY;

/**
 * The equivalent of {@link StreamingHttpService} but with synchronous/blocking APIs instead of asynchronous APIs.
 */
public abstract class BlockingStreamingHttpService implements AutoCloseable, BlockingStreamingHttpRequestHandler {
    @Override
    public void close() throws Exception {
        // noop
    }

    @Override
    public final BlockingStreamingHttpService asBlockingStreamingService() {
        return this;
    }

    /**
     * Convert this {@link BlockingStreamingHttpService} to the {@link StreamingHttpService} asynchronous API.
     * <p>
     * Note that the resulting {@link StreamingHttpService} may still be subject to any blocking, in memory aggregation,
     * and other behavior as this {@link BlockingStreamingHttpService}.
     *
     * @return a {@link StreamingHttpService} representation of this {@link BlockingStreamingHttpService}.
     */
    public final StreamingHttpService asStreamingService() {
        return asStreamingServiceInternal();
    }

    /**
     * Returns the {@link HttpExecutionStrategy} for this {@link BlockingStreamingHttpService}.
     *
     * @return The {@link HttpExecutionStrategy} for this {@link BlockingStreamingHttpService}.
     */
    public HttpExecutionStrategy executionStrategy() {
        return OFFLOAD_RECEIVE_META_AND_SEND_STRATEGY;
    }

    /**
     * Provides a means to override the behavior of {@link #asStreamingService()} for internal classes.
     *
     * @return a {@link StreamingHttpService} representation of this {@link BlockingStreamingHttpService}.
     */
    StreamingHttpService asStreamingServiceInternal() {
        return BlockingStreamingHttpServiceToStreamingHttpService.transform(this);
    }
}

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

import io.servicetalk.concurrent.api.Single;

import static io.servicetalk.http.api.HttpExecutionStrategies.OFFLOAD_RECEIVE_META_STRATEGY;

/**
 * The equivalent of {@link HttpService} but with synchronous/blocking APIs instead of asynchronous APIs.
 */
@FunctionalInterface
public interface BlockingHttpService extends AutoCloseable {
    /**
     * Handles a single HTTP request.
     *
     * @param ctx Context of the service.
     * @param request to handle.
     * @param responseFactory used to create {@link HttpResponse} objects.
     * @return {@link Single} of HTTP response.
     * @throws Exception If an exception occurs during request processing.
     */
    HttpResponse handle(HttpServiceContext ctx, HttpRequest request, HttpResponseFactory responseFactory)
            throws Exception;

    @Override
    default void close() throws Exception {
        // noop
    }

    /**
     * Compute a {@link HttpExecutionStrategy} given the programming model constraints of this
     * {@link BlockingStreamingHttpService} in combination with another {@link HttpExecutionStrategy}. This may involve
     * a merge operation between two {@link BlockingStreamingHttpService}.
     *
     * @param other The other {@link HttpExecutionStrategy} to consider during the computation.
     * @return The {@link HttpExecutionStrategy} for this {@link BlockingStreamingHttpService}.
     */
    default HttpExecutionStrategy computeExecutionStrategy(HttpExecutionStrategy other) {
        return other.merge(OFFLOAD_RECEIVE_META_STRATEGY);
    }
}

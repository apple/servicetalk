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

import io.servicetalk.transport.api.ExecutionContext;

/**
 * The equivalent of {@link HttpRequester} but with synchronous/blocking APIs instead of asynchronous APIs.
 * @param <I> Type of payload of a request handled by this {@link BlockingHttpRequester}.
 * @param <O> Type of payload of a response handled by this {@link BlockingHttpRequester}.
 */
public abstract class BlockingHttpRequester<I, O> implements AutoCloseable {
    /**
     * Send a {@code request}.
     * @param request the request to send.
     * @return The response.
     * @throws Exception if an exception occurs during the request processing.
     */
    public abstract BlockingHttpResponse<O> request(BlockingHttpRequest<I> request) throws Exception;

    /**
     * Get the {@link ExecutionContext} associated with this object.
     * @return the {@link ExecutionContext} associated with this object.
     */
    public abstract ExecutionContext getExecutionContext();

    /**
     * Convert this {@link BlockingHttpRequester} to the {@link HttpRequester} asynchronous API.
     * <p>
     * Note that the resulting {@link HttpRequester} will still be subject to any blocking, in memory aggregation, and
     * other behavior as this {@link BlockingHttpRequester}.
     * @return a {@link HttpRequester} representation of this {@link BlockingHttpRequester}.
     */
    public final HttpRequester<I, O> asAsynchronousRequester() {
        return asAsynchronousRequesterInternal();
    }

    HttpRequester<I, O> asAsynchronousRequesterInternal() {
        return new BlockingHttpRequesterToHttpRequester<>(this);
    }
}

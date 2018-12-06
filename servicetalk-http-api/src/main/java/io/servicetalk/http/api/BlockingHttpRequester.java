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

import static io.servicetalk.http.api.HttpExecutionStrategies.defaultStrategy;
import static java.util.Objects.requireNonNull;

/**
 * The equivalent of {@link HttpRequester} with synchronous/blocking APIs instead of asynchronous APIs.
 */
public abstract class BlockingHttpRequester implements HttpRequestFactory, AutoCloseable {
    final HttpRequestResponseFactory reqRespFactory;

    /**
     * Create a new instance.
     * @param reqRespFactory The {@link HttpRequestResponseFactory} used to
     * {@link #newRequest(HttpRequestMethod, String) create new requests} and {@link #httpResponseFactory()}.
     */
    protected BlockingHttpRequester(final HttpRequestResponseFactory reqRespFactory) {
        this.reqRespFactory = requireNonNull(reqRespFactory);
    }

    /**
     * Send a {@code request}.
     *
     * @param request the request to send.
     * @return The response.
     * @throws Exception if an exception occurs during the request processing.
     */
    public HttpResponse request(HttpRequest request) throws Exception {
        return request(executionStrategy(), request);
    }

    /**
     * Send a {@code request} using the passed {@link HttpExecutionStrategy strategy}.
     *
     * @param strategy {@link HttpExecutionStrategy} to use.
     * @param request the request to send.
     * @return The response.
     * @throws Exception if an exception occurs during the request processing.
     */
    public abstract HttpResponse request(HttpExecutionStrategy strategy, HttpRequest request) throws Exception;

    /**
     * Get the {@link ExecutionContext} used during construction of this object.
     * <p>
     * Note that the {@link ExecutionContext#ioExecutor()} will not necessarily be associated with a specific thread
     * unless that was how this object was built.
     *
     * @return the {@link ExecutionContext} used during construction of this object.
     */
    public abstract ExecutionContext executionContext();

    @Override
    public final HttpRequest newRequest(HttpRequestMethod method, String requestTarget) {
        return reqRespFactory.newRequest(method, requestTarget);
    }

    /**
     * Get a {@link HttpResponseFactory}.
     * @return a {@link HttpResponseFactory}.
     */
    public final HttpResponseFactory httpResponseFactory() {
        return reqRespFactory;
    }

    /**
     * Convert this {@link BlockingHttpRequester} to the {@link StreamingHttpRequester} API.
     * <p>
     * Note that the resulting {@link StreamingHttpRequester} may still be subject to any blocking, in memory
     * aggregation, and other behavior as this {@link BlockingStreamingHttpRequester}.
     *
     * @return a {@link StreamingHttpRequester} representation of this {@link BlockingStreamingHttpRequester}.
     */
    public final StreamingHttpRequester asStreamingRequester() {
        return asStreamingRequesterInternal();
    }

    /**
     * Convert this {@link BlockingHttpRequester} to the {@link HttpRequester} API.
     * <p>
     * Note that the resulting {@link HttpRequester} may still be subject to any blocking, in memory
     * aggregation, and other behavior as this {@link BlockingStreamingHttpRequester}.
     *
     * @return a {@link HttpRequester} representation of this {@link BlockingStreamingHttpRequester}.
     */
    public final HttpRequester asRequester() {
        return asRequesterInternal();
    }

    /**
     * Convert this {@link BlockingHttpRequester} to the {@link BlockingStreamingHttpRequester} API.
     * <p>
     * Note that the resulting {@link BlockingStreamingHttpRequester} may still be subject to in memory
     * aggregation and other behavior as this {@link BlockingStreamingHttpRequester}.
     *
     * @return a {@link BlockingStreamingHttpRequester} representation of this {@link BlockingStreamingHttpRequester}.
     */
    public final BlockingStreamingHttpRequester asBlockingStreamingRequester() {
        return asStreamingRequester().asBlockingStreamingRequester();
    }

    /**
     * Returns the default {@link HttpExecutionStrategy} for this {@link BlockingHttpRequester}.
     *
     * @return Default {@link HttpExecutionStrategy} for this {@link BlockingHttpRequester}.
     */
    final HttpExecutionStrategy executionStrategy() {
        return defaultStrategy();
    }

    StreamingHttpRequester asStreamingRequesterInternal() {
        return new BlockingHttpRequesterToStreamingHttpRequester(this);
    }

    HttpRequester asRequesterInternal() {
        return new BlockingHttpRequesterToHttpRequester(this);
    }
}

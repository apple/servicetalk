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

import io.servicetalk.concurrent.api.ListenableAsyncCloseable;
import io.servicetalk.concurrent.api.Single;
import io.servicetalk.transport.api.ExecutionContext;

/**
 * The equivalent of {@link HttpRequester} but that accepts {@link StreamingHttpRequest} and returns
 * {@link StreamingHttpResponse}.
 */
public abstract class StreamingHttpRequester implements StreamingHttpRequestFactory, ListenableAsyncCloseable {
    /**
     * Send a {@code request}.
     * @param request the request to send.
     * @return The response.
     */
    public abstract Single<StreamingHttpResponse> request(StreamingHttpRequest request);

    /**
     * Get the {@link ExecutionContext} used during construction of this object.
     * <p>
     * Note that the {@link ExecutionContext#getIoExecutor()} will not necessarily be associated with a specific thread
     * unless that was how this object was built.
     * @return the {@link ExecutionContext} used during construction of this object.
     */
    public abstract ExecutionContext getExecutionContext();

    /**
     * Convert this {@link StreamingHttpRequester} to the {@link BlockingStreamingHttpRequester} API.
     * <p>
     * This API is provided for convenience for a more familiar sequential programming model. It is recommended that
     * filters are implemented using the {@link StreamingHttpRequester} asynchronous API for maximum portability.
     * @return a {@link BlockingStreamingHttpRequester} representation of this {@link StreamingHttpRequester}.
     */
    public final BlockingStreamingHttpRequester asBlockingStreamingRequester() {
        return asBlockingStreamingRequesterInternal();
    }

    /**
     * Convert this {@link StreamingHttpRequester} to the {@link HttpRequester} API.
     * <p>
     * This API is provided for convenience. It is recommended that
     * filters are implemented using the {@link StreamingHttpRequester} asynchronous API for maximum portability.
     * @return a {@link HttpRequester} representation of this {@link StreamingHttpRequester}.
     */
    public final HttpRequester asRequester() {
        return asRequesterInternal();
    }

    /**
     * Convert this {@link StreamingHttpRequester} to the {@link BlockingHttpRequester} API.
     * <p>
     * This API is provided for convenience. It is recommended that
     * filters are implemented using the {@link StreamingHttpRequester} asynchronous API for maximum portability.
     * @return a {@link BlockingHttpRequester} representation of this {@link StreamingHttpRequester}.
     */
    public final BlockingHttpRequester asBlockingRequester() {
        return asBlockingRequesterInternal();
    }

    HttpRequester asRequesterInternal() {
        return new StreamingHttpRequesterToHttpRequester(this);
    }

    BlockingStreamingHttpRequester asBlockingStreamingRequesterInternal() {
        return new StreamingHttpRequesterToBlockingStreamingHttpRequester(this);
    }

    BlockingHttpRequester asBlockingRequesterInternal() {
        return new StreamingHttpRequesterToBlockingHttpRequester(this);
    }
}

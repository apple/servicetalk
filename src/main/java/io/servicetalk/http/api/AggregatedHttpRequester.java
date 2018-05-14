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
 * The equivalent of {@link HttpRequester} but that accepts {@link FullHttpRequest} and returns {@link FullHttpResponse}.
 */
public abstract class AggregatedHttpRequester implements ListenableAsyncCloseable {
    /**
     * Send a {@code request}.
     * @param request the request to send.
     * @return The response.
     */
    public abstract Single<FullHttpResponse> request(FullHttpRequest request);

    /**
     * Get the {@link ExecutionContext} used during construction of this object.
     * <p>
     * Note that the {@link ExecutionContext#getIoExecutor()} will not necessarily be associated with a specific thread
     * unless that was how this object was built.
     * @return the {@link ExecutionContext} used during construction of this object.
     */
    public abstract ExecutionContext getExecutionContext();

    /**
     * Convert this {@link AggregatedHttpRequester} to the {@link HttpRequester} asynchronous API.
     * @return a {@link HttpRequester} representation of this {@link AggregatedHttpRequester}.
     */
    public final HttpRequester<HttpPayloadChunk, HttpPayloadChunk> asRequester() {
        return asRequesterInternal();
    }

    HttpRequester<HttpPayloadChunk, HttpPayloadChunk> asRequesterInternal() {
        return new AggregatedHttpRequesterToHttpRequester(this);
    }
}

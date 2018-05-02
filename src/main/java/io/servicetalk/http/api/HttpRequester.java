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

import io.servicetalk.concurrent.api.Executor;
import io.servicetalk.concurrent.api.ListenableAsyncCloseable;
import io.servicetalk.concurrent.api.Single;

/**
 * Provides a means to make a HTTP request.
 * @param <I> The type of payload of the request.
 * @param <O> The type of payload of the response.
 */
public abstract class HttpRequester<I, O> implements ListenableAsyncCloseable {
    /**
     * Send a {@code request}.
     * @param request the request to send.
     * @return The response.
     */
    public abstract Single<HttpResponse<O>> request(HttpRequest<I> request);

    /**
     * Get the {@link Executor} associated with this object.
     * @return the {@link Executor} associated with this object.
     */
    public abstract Executor getExecutor();

    /**
     * Convert this {@link HttpRequester} to the {@link BlockingHttpRequester} API.
     * <p>
     * This API is provided for convenience for a more familiar sequential programming model. It is recommended that
     * filters are implemented using the {@link HttpRequester} asynchronous API for maximum portability.
     * @return a {@link BlockingHttpRequester} representation of this {@link HttpRequester}.
     */
    public final BlockingHttpRequester<I, O> asBlockingRequester() {
        return asBlockingRequesterInternal();
    }

    BlockingHttpRequester<I, O> asBlockingRequesterInternal() {
        return new HttpRequesterToBlockingHttpRequester<>(this);
    }
}

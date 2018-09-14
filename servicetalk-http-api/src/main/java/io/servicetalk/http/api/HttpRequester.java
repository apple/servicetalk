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

import static java.util.Objects.requireNonNull;

/**
 * Provides a means to make a HTTP request.
 */
public abstract class HttpRequester implements HttpRequestFactory, ListenableAsyncCloseable {
    private final HttpRequestFactory requestFactory;

    /**
     * Create a new instance.
     * @param requestFactory The {@link HttpRequestFactory} used to
     * {@link #newRequest(HttpRequestMethod, String) create new requests}.
     */
    protected HttpRequester(HttpRequestFactory requestFactory) {
        this.requestFactory = requireNonNull(requestFactory);
    }

    /**
     * Send a {@code request}.
     *
     * @param request the request to send.
     * @return The response.
     */
    public abstract Single<? extends HttpResponse> request(HttpRequest request);

    /**
     * Get the {@link ExecutionContext} used during construction of this object.
     * <p>
     * Note that the {@link ExecutionContext#getIoExecutor()} will not necessarily be associated with a specific thread
     * unless that was how this object was built.
     *
     * @return the {@link ExecutionContext} used during construction of this object.
     */
    public abstract ExecutionContext getExecutionContext();

    @Override
    public final HttpRequest newRequest(HttpRequestMethod method, String requestTarget) {
        return requestFactory.newRequest(method, requestTarget);
    }

    @Override
    public final HttpResponseFactory getHttpResponseFactory() {
        return requestFactory.getHttpResponseFactory();
    }

    /**
     * Convert this {@link HttpRequester} to the {@link StreamingHttpRequester} API.
     *
     * @return a {@link StreamingHttpRequester} representation of this {@link HttpRequester}.
     */
    public final StreamingHttpRequester asStreamingRequester() {
        return asStreamingRequesterInternal();
    }

    /**
     * Convert this {@link HttpRequester} to the {@link BlockingStreamingHttpRequester} API.
     *
     * @return a {@link BlockingStreamingHttpRequester} representation of this {@link HttpRequester}.
     */
    public final BlockingStreamingHttpRequester asBlockingStreamingRequester() {
        return asStreamingRequester().asBlockingStreamingRequester();
    }

    /**
     * Convert this {@link HttpRequester} to the {@link BlockingHttpRequester} API.
     *
     * @return a {@link BlockingHttpRequester} representation of this {@link HttpRequester}.
     */
    public final BlockingHttpRequester asBlockingRequester() {
        return asBlockingRequesterInternal();
    }

    StreamingHttpRequester asStreamingRequesterInternal() {
        return new HttpRequesterToStreamingHttpRequester(this);
    }

    BlockingHttpRequester asBlockingRequesterInternal() {
        return new HttpRequesterToBlockingHttpRequester(this);
    }
}

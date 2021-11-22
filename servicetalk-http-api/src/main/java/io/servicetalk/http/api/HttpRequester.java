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

/**
 * Provides a means to make a HTTP request.
 */
public interface HttpRequester extends HttpRequestFactory, ListenableAsyncCloseable {
    /**
     * Send a {@code request}.
     *
     * @param request the request to send.
     * @return The response.
     */
    Single<HttpResponse> request(HttpRequest request);

    /**
     * Get the {@link HttpExecutionContext} used during construction of this object.
     * <p>
     * Note that the {@link HttpExecutionContext#ioExecutor()} will not necessarily be associated with a specific thread
     * unless that was how this object was built.
     *
     * @return the {@link HttpExecutionContext} used during construction of this object.
     */
    HttpExecutionContext executionContext();

    /**
     * Get a {@link HttpResponseFactory}.
     *
     * @return a {@link HttpResponseFactory}.
     */
    HttpResponseFactory httpResponseFactory();
}

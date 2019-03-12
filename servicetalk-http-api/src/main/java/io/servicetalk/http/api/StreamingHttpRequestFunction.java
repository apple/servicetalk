/*
 * Copyright © 2019 Apple Inc. and the ServiceTalk project authors
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

/**
 * Common {@code request()} interface between {@link StreamingHttpClientFilter}, {@link
 * ReservedStreamingHttpConnectionFilter} and {@link StreamingHttpConnectionFilter}.
 */
// TODO(jayv) merge with StreamingHttpRequester
public interface StreamingHttpRequestFunction extends StreamingHttpRequestFactory {

    /**
     * Send a {@code request} using the specified {@link HttpExecutionStrategy strategy}.
     *
     * @param strategy {@link HttpExecutionStrategy} to use for executing the request.
     * @param request the request to send.
     * @return The response.
     */
    Single<StreamingHttpResponse> request(HttpExecutionStrategy strategy, StreamingHttpRequest request);

    /**
     * Get a {@link StreamingHttpResponseFactory}.
     *
     * @return a {@link StreamingHttpResponseFactory}.
     */
    StreamingHttpResponseFactory httpResponseFactory();
}

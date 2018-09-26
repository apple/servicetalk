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

/**
 * A handler of {@link StreamingHttpRequest}.
 * <p>
 * This is a simpler version of {@link StreamingHttpService} without lifecycle constructs and other higher level
 * concerns.
 */
@FunctionalInterface
public interface StreamingHttpRequestHandler {

    /**
     * Handles a single HTTP request.
     *
     * @param ctx Context of the service.
     * @param request to handle.
     * @param responseFactory used to create {@link StreamingHttpResponse} objects.
     * @return {@link Single} of HTTP response.
     */
    Single<StreamingHttpResponse> handle(HttpServiceContext ctx, StreamingHttpRequest request,
                                         StreamingHttpResponseFactory responseFactory);

    /**
     * Convert this {@link StreamingHttpRequestHandler} to a {@link StreamingHttpService}.
     * @return a {@link StreamingHttpService}.
     */
    default StreamingHttpService asStreamingService() {
        return new StreamingHttpService() {
            @Override
            public Single<StreamingHttpResponse> handle(
                    final HttpServiceContext ctx, final StreamingHttpRequest request,
                    final StreamingHttpResponseFactory responseFactory) {
                return StreamingHttpRequestHandler.this.handle(ctx, request, responseFactory);
            }
        };
    }
}

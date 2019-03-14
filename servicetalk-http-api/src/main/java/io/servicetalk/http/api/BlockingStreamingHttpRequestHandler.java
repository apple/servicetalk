/*
 * Copyright © 2018-2019 Apple Inc. and the ServiceTalk project authors
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

/**
 * A handler of {@link BlockingStreamingHttpRequest}.
 * <p>
 * This is a simpler version of {@link BlockingStreamingHttpService} without lifecycle constructs and other higher
 * level concerns.
 */
@FunctionalInterface
public interface BlockingStreamingHttpRequestHandler {
    /**
     * Handles a single HTTP request.
     *
     * @param ctx Context of the service.
     * @param request to handle.
     * @param response to send to the client.
     * @throws Exception If an exception occurs during request processing.
     */
    void handle(HttpServiceContext ctx, BlockingStreamingHttpRequest request,
                BlockingStreamingHttpServerResponse response) throws Exception;

    /**
     * Convert this {@link BlockingStreamingHttpRequestHandler} to a {@link BlockingStreamingHttpService}.
     *
     * @return a {@link BlockingStreamingHttpService}.
     */
    default BlockingStreamingHttpService asBlockingStreamingService() {
        return new BlockingStreamingHttpService() {
            @Override
            public void handle(final HttpServiceContext ctx,
                               final BlockingStreamingHttpRequest request,
                               final BlockingStreamingHttpServerResponse response) throws Exception {
                BlockingStreamingHttpRequestHandler.this.handle(ctx, request, response);
            }
        };
    }
}

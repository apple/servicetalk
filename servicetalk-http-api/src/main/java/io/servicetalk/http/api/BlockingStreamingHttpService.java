/*
 * Copyright © 2018, 2021 Apple Inc. and the ServiceTalk project authors
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

import io.servicetalk.concurrent.GracefulCloseable;
import io.servicetalk.oio.api.PayloadWriter;

import java.io.IOException;

/**
 * The equivalent of {@link StreamingHttpService} but with synchronous/blocking APIs instead of asynchronous APIs.
 */
@FunctionalInterface
public interface BlockingStreamingHttpService extends GracefulCloseable {
    /**
     * Handles a single HTTP request.
     *
     * @param ctx Context of the service.
     * @param request to handle.
     * @param response to send to the client. The implementation of this method is responsible for calling
     * {@link PayloadWriter#close()} on the {@link BlockingStreamingHttpServerResponse#payloadWriter()}.
     * @throws Exception If an exception occurs during request processing.
     */
    void handle(HttpServiceContext ctx, BlockingStreamingHttpRequest request,
                BlockingStreamingHttpServerResponse response) throws Exception;

    @Override
    default void close() throws IOException {
        // noop
    }
}

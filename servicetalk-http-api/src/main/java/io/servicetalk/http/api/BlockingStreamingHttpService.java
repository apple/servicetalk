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

/**
 * The equivalent of {@link StreamingHttpService} but with synchronous/blocking APIs instead of asynchronous APIs.
 */
public abstract class BlockingStreamingHttpService implements AutoCloseable {
    /**
     * Handles a single HTTP request.
     *
     * @param ctx Context of the service.
     * @param request to handle.
     * @param factory used to create {@link BlockingStreamingHttpResponse} objects.
     * @return a {@link BlockingStreamingHttpResponse} which represents the HTTP response.
     * @throws Exception If an exception occurs during request processing.
     */
    public abstract BlockingStreamingHttpResponse handle(
            HttpServiceContext ctx, BlockingStreamingHttpRequest request,
            BlockingStreamingHttpResponseFactory factory) throws Exception;

    @Override
    public void close() throws Exception {
        // noop
    }

    /**
     * Convert this {@link BlockingStreamingHttpService} to the {@link StreamingHttpService} asynchronous API.
     * <p>
     * Note that the resulting {@link StreamingHttpService} may still be subject to any blocking, in memory aggregation, and
     * other behavior as this {@link BlockingStreamingHttpService}.
     *
     * @return a {@link StreamingHttpService} representation of this {@link BlockingStreamingHttpService}.
     */
    public final StreamingHttpService asStreamingService() {
        return asStreamingServiceInternal();
    }

    /**
     * Convert this {@link BlockingStreamingHttpService} to the {@link HttpService} asynchronous API.
     * <p>
     * Note that the resulting {@link HttpService} may still be subject to any blocking, in memory
     * aggregation, and other behavior as this {@link BlockingStreamingHttpService}.
     *
     * @return a {@link HttpService} representation of this {@link BlockingStreamingHttpService}.
     */
    public final HttpService asService() {
        return asStreamingService().asService();
    }

    /**
     * Convert this {@link BlockingStreamingHttpService} to the {@link BlockingHttpService} asynchronous API.
     * <p>
     * Note that the resulting {@link BlockingHttpService} may still be subject to any blocking, in memory
     * aggregation, and other behavior as this {@link BlockingStreamingHttpService}.
     *
     * @return a {@link BlockingHttpService} representation of this {@link BlockingStreamingHttpService}.
     */
    public final BlockingHttpService asBlockingService() {
        return asStreamingService().asBlockingService();
    }

    /**
     * Provides a means to override the behavior of {@link #asStreamingService()} for internal classes.
     *
     * @return a {@link StreamingHttpService} representation of this {@link BlockingStreamingHttpService}.
     */
    StreamingHttpService asStreamingServiceInternal() {
        return new BlockingStreamingHttpServiceToStreamingHttpService(this);
    }
}

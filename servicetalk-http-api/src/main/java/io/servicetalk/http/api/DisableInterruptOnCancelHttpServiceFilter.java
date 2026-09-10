/*
 * Copyright © 2026 Apple Inc. and the ServiceTalk project authors
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
import io.servicetalk.context.api.ContextMap.Key;

import static io.servicetalk.context.api.ContextMap.Key.newKey;
import static io.servicetalk.http.api.HttpExecutionStrategies.offloadNone;
import static java.lang.Boolean.FALSE;

/**
 * Stops the thread running a blocking service from being {@link Thread#interrupt() interrupted} when the response is
 * cancelled. Without this filter the thread is interrupted.
 * <p>
 * Applies to {@link HttpServerBuilder#listenBlocking(BlockingHttpService)} and
 * {@link HttpServerBuilder#listenBlockingStreaming(BlockingStreamingHttpService)}, including routers and gRPC
 * services built on the same adapters. Append it ahead of the service it governs.
 * <p>
 * Cancellation is still observable by a {@link BlockingStreamingHttpService}, because its payload writer is
 * terminated and the next {@link HttpPayloadWriter#write(Object) write} throws {@link java.io.IOException}. A
 * {@link BlockingHttpService} has no such signal and runs to completion, so nothing releases a thread that blocks
 * indefinitely.
 */
public final class DisableInterruptOnCancelHttpServiceFilter implements StreamingHttpServiceFilterFactory {

    /**
     * Instance of {@link DisableInterruptOnCancelHttpServiceFilter}.
     */
    public static final StreamingHttpServiceFilterFactory INSTANCE = new DisableInterruptOnCancelHttpServiceFilter();

    static final Key<Boolean> INTERRUPT_ON_CANCEL = newKey("INTERRUPT_ON_CANCEL", Boolean.class);

    private DisableInterruptOnCancelHttpServiceFilter() {
        // Singleton
    }

    @Override
    public StreamingHttpServiceFilter create(final StreamingHttpService service) {
        return new StreamingHttpServiceFilter(service) {
            @Override
            public Single<StreamingHttpResponse> handle(final HttpServiceContext ctx,
                                                        final StreamingHttpRequest request,
                                                        final StreamingHttpResponseFactory responseFactory) {
                request.context().put(INTERRUPT_ON_CANCEL, FALSE);
                return delegate().handle(ctx, request, responseFactory);
            }
        };
    }

    @Override
    public HttpExecutionStrategy requiredOffloads() {
        return offloadNone();
    }

    /**
     * Resolves the value for a request. Must be invoked on the request thread, because the request context is not
     * thread-safe and is written to elsewhere on the response path.
     *
     * @param request the request whose context carries the value
     * @return {@code true} if the service thread should be interrupted on cancellation
     */
    static boolean interruptOnCancel(final HttpRequestMetaData request) {
        final Boolean interrupt = request.context().get(INTERRUPT_ON_CANCEL);
        return interrupt == null || interrupt;
    }
}

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

import static io.servicetalk.http.api.BlockingStreamingToStreamingService.INTERRUPT_ON_CANCEL;
import static io.servicetalk.http.api.HttpExecutionStrategies.offloadNone;
import static java.lang.Boolean.FALSE;

/**
 * Stops the thread running a {@link BlockingStreamingHttpService} from being {@link Thread#interrupt() interrupted}
 * when the response is cancelled.
 * <p>
 * Applies to {@link HttpServerBuilder#listenBlockingStreaming(BlockingStreamingHttpService)}, also behind a router,
 * in any filter position. No effect on {@link HttpServerBuilder#listenBlocking(BlockingHttpService)} or asynchronous
 * services.
 * <p>
 * The service sees the cancel only as an {@link java.io.IOException} from the next
 * {@link HttpPayloadWriter#write(Object) write} or {@link HttpPayloadWriter#flush() flush}. Other blocking calls,
 * including reads of the request payload, are not woken up.
 */
public final class DisableInterruptOnCancelHttpServiceFilter implements StreamingHttpServiceFilterFactory {

    /**
     * Instance of {@link DisableInterruptOnCancelHttpServiceFilter}.
     */
    public static final StreamingHttpServiceFilterFactory INSTANCE = new DisableInterruptOnCancelHttpServiceFilter();

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
}

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
package io.servicetalk.http.utils;

import io.servicetalk.concurrent.api.Single;
import io.servicetalk.http.api.HttpExecutionStrategy;
import io.servicetalk.http.api.HttpServiceContext;
import io.servicetalk.http.api.StreamingHttpRequest;
import io.servicetalk.http.api.StreamingHttpResponse;
import io.servicetalk.http.api.StreamingHttpResponseFactory;
import io.servicetalk.http.api.StreamingHttpService;
import io.servicetalk.http.api.StreamingHttpServiceFilter;
import io.servicetalk.http.api.StreamingHttpServiceFilterFactory;

import static io.servicetalk.http.api.HttpContextKeys.INTERRUPT_BLOCKING_SERVICE_ON_CANCEL;
import static io.servicetalk.http.api.HttpExecutionStrategies.offloadNone;

/**
 * Sets {@link io.servicetalk.http.api.HttpContextKeys#INTERRUPT_BLOCKING_SERVICE_ON_CANCEL} on every request that
 * passes through, which controls whether the thread running a blocking service is
 * {@link Thread#interrupt() interrupted} when the response is cancelled.
 * <p>
 * Append ahead of the service it should govern. For gRPC, append it on the underlying HTTP server builder via
 * {@code GrpcServerBuilder#initializeHttp}.
 *
 * @see io.servicetalk.http.api.HttpContextKeys#INTERRUPT_BLOCKING_SERVICE_ON_CANCEL
 */
public final class InterruptBlockingServiceOnCancelHttpServiceFilter implements StreamingHttpServiceFilterFactory {

    private final boolean interrupt;

    /**
     * Create a new instance.
     *
     * @param interrupt {@code true} to interrupt the service thread on cancellation, {@code false} to only observe
     * cancellation cooperatively. See
     * {@link io.servicetalk.http.api.HttpContextKeys#INTERRUPT_BLOCKING_SERVICE_ON_CANCEL} for the caveats of
     * {@code false}.
     */
    public InterruptBlockingServiceOnCancelHttpServiceFilter(final boolean interrupt) {
        this.interrupt = interrupt;
    }

    @Override
    public StreamingHttpServiceFilter create(final StreamingHttpService service) {
        return new StreamingHttpServiceFilter(service) {
            @Override
            public Single<StreamingHttpResponse> handle(final HttpServiceContext ctx,
                                                        final StreamingHttpRequest request,
                                                        final StreamingHttpResponseFactory responseFactory) {
                request.context().put(INTERRUPT_BLOCKING_SERVICE_ON_CANCEL, interrupt);
                return delegate().handle(ctx, request, responseFactory);
            }
        };
    }

    @Override
    public HttpExecutionStrategy requiredOffloads() {
        return offloadNone();
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + "{interrupt=" + interrupt + '}';
    }
}

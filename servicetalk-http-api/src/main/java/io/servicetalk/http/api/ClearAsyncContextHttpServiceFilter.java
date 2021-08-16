/*
 * Copyright © 2020 Apple Inc. and the ServiceTalk project authors
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

import io.servicetalk.concurrent.api.AsyncContext;
import io.servicetalk.concurrent.api.Single;

/**
 * Clears the {@link AsyncContext} for a new request.
 */
final class ClearAsyncContextHttpServiceFilter implements
                                               StreamingHttpServiceFilterFactory, HttpExecutionStrategyInfluencer {

    static final ClearAsyncContextHttpServiceFilter CLEAR_ASYNC_CONTEXT_HTTP_SERVICE_FILTER =
            new ClearAsyncContextHttpServiceFilter();

    private ClearAsyncContextHttpServiceFilter() {
        // singleton
    }

    @Override
    public StreamingHttpServiceFilter create(final StreamingHttpService service) {
        return new StreamingHttpServiceFilter(service) {
            @Override
            public Single<StreamingHttpResponse> handle(final HttpServiceContext ctx,
                                                        final StreamingHttpRequest request,
                                                        final StreamingHttpResponseFactory responseFactory) {
                AsyncContext.clear();
                return delegate().handle(ctx, request, responseFactory);
            }
        };
    }

    @Override
    public HttpExecutionStrategy influenceStrategy(final HttpExecutionStrategy strategy) {
        // No influence since we do not block.
        return strategy;
    }
}

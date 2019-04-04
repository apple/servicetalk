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

import io.servicetalk.concurrent.api.Completable;
import io.servicetalk.concurrent.api.Single;
import io.servicetalk.http.api.HttpApiConversions.StreamingServiceAdapter;

import static io.servicetalk.http.api.BlockingUtils.blockingToCompletable;
import static io.servicetalk.http.api.BlockingUtils.blockingToSingle;
import static io.servicetalk.http.api.HttpApiConversions.DEFAULT_BLOCKING_SERVICE_STRATEGY;
import static java.util.Objects.requireNonNull;

final class BlockingToStreamingService implements StreamingServiceAdapter {
    private final BlockingHttpService original;
    private final HttpExecutionStrategy strategy;

    BlockingToStreamingService(final BlockingHttpService original) {
        this.original = requireNonNull(original);
        strategy = DEFAULT_BLOCKING_SERVICE_STRATEGY;
    }

    @Override
    public Single<StreamingHttpResponse> handle(final HttpServiceContext ctx,
                                                final StreamingHttpRequest request,
                                                final StreamingHttpResponseFactory responseFactory) {
        return request.toRequest().flatMap(req -> blockingToSingle(() -> original.handle(
                ctx, req, ctx.responseFactory())).map(HttpResponse::toStreamingResponse));
    }

    @Override
    public Completable closeAsync() {
        return blockingToCompletable(original::close);
    }

    @Override
    public HttpExecutionStrategy influenceStrategy(final HttpExecutionStrategy strategy) {
        return original instanceof HttpExecutionStrategyInfluencer ?
                ((HttpExecutionStrategyInfluencer) original).influenceStrategy(strategy) : strategy;
    }

    @Override
    public HttpExecutionStrategy executionStrategy() {
        return strategy;
    }
}

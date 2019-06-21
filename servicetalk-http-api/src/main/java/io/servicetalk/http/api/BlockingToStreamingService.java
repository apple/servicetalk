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

import static io.servicetalk.http.api.BlockingUtils.blockingToCompletable;
import static io.servicetalk.http.api.BlockingUtils.blockingToSingle;
import static java.util.Objects.requireNonNull;

final class BlockingToStreamingService extends AbstractServiceAdapterHolder {
    static final HttpExecutionStrategy DEFAULT_STRATEGY = HttpExecutionStrategies.noOffloadsStrategy();
    private final BlockingHttpService original;

    private BlockingToStreamingService(final BlockingHttpService original,
                                       final HttpExecutionStrategy executionStrategy) {
        super(executionStrategy);
        this.original = requireNonNull(original);
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

    static AbstractServiceAdapterHolder newAdapter(final BlockingHttpService original,
                                                   final HttpExecutionStrategyInfluencer influencer) {
        HttpExecutionStrategy influencedStrategy = influencer.influenceStrategy(DEFAULT_STRATEGY);
        // Due to lack of more specialized offloading strategies we provide a localized optimization in case user didn't
        // override the strategy at the server builder. Future enhancements to HttpExecutionStrategy may unlock more
        // appropriate offloading modes for this conversion.
        // The current manual offloading workaround may result in double offloading if the router (eg. Jersey) opts in
        // to more offloading at runtime (eg via annotation on the JAX-RS endpoint). We accept this negative as a
        // temporary situation where we expected it less likely for folks to use this initially. It allows for a
        // considerable performance increase while we work on supporting more strategies.
        if (!influencedStrategy.isDataReceiveOffloaded()) {
            return new ManuallyOffloadingBlockingToStreamingService(original, influencedStrategy);
        }
        return new BlockingToStreamingService(original, influencedStrategy);
    }

    private static class ManuallyOffloadingBlockingToStreamingService extends AbstractServiceAdapterHolder {

        private final BlockingHttpService original;

        ManuallyOffloadingBlockingToStreamingService(final BlockingHttpService original,
                                                     final HttpExecutionStrategy serviceInvocationStrategy) {
            super(serviceInvocationStrategy);
            this.original = requireNonNull(original);
        }

        @Override
        public Single<StreamingHttpResponse> handle(final HttpServiceContext ctx,
                                                    final StreamingHttpRequest request,
                                                    final StreamingHttpResponseFactory responseFactory) {
            return request.toRequest().flatMap(req ->
                    ctx.executionContext().executor().submit(() ->
                            original.handle(ctx, req, ctx.responseFactory())
                    ))
                    .map(HttpResponse::toStreamingResponse);
        }

        @Override
        public Completable closeAsync() {
            return blockingToCompletable(original::close);
        }
    }
}

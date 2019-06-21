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

import static io.servicetalk.http.api.HttpExecutionStrategies.OFFLOAD_SEND_STRATEGY;
import static java.util.Objects.requireNonNull;

final class ServiceToStreamingService extends AbstractServiceAdapterHolder {
    /**
     * For aggregation, we invoke the service after the payload is completed, hence we need to offload data.
     */
    private static final HttpExecutionStrategy DEFAULT_STRATEGY = OFFLOAD_SEND_STRATEGY;
    private final HttpService original;

    private ServiceToStreamingService(final HttpService original, HttpExecutionStrategy influencer) {
        super(influencer);
        this.original = requireNonNull(original);
    }

    @Override
    public Single<StreamingHttpResponse> handle(final HttpServiceContext ctx,
                                                final StreamingHttpRequest request,
                                                final StreamingHttpResponseFactory responseFactory) {
        return request.toRequest().flatMap(req -> original.handle(ctx, req, ctx.responseFactory()))
                .map(HttpResponse::toStreamingResponse);
    }

    @Override
    public Completable closeAsync() {
        return original.closeAsync();
    }

    @Override
    public Completable closeAsyncGracefully() {
        return original.closeAsyncGracefully();
    }

    public static HttpApiConversions.ServiceAdapterHolder newAdapter(final HttpService original,
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
            return new ManuallyOffloadingAggregatedToStreamingService(original, influencedStrategy);
        }
        return new ServiceToStreamingService(original, influencedStrategy);
    }

    private static class ManuallyOffloadingAggregatedToStreamingService extends AbstractServiceAdapterHolder {

        private final HttpService original;

        ManuallyOffloadingAggregatedToStreamingService(final HttpService original,
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
                            original.handle(ctx, req, ctx.responseFactory()))
                            .flatMap(sr -> sr.map(HttpResponse::toStreamingResponse)));
        }

        @Override
        public Completable closeAsync() {
            return original.closeAsync();
        }
    }
}

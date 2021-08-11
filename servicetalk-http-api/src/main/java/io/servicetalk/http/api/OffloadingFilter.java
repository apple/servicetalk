/*
 * Copyright © 2021 Apple Inc. and the ServiceTalk project authors
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

import io.servicetalk.concurrent.api.Executor;
import io.servicetalk.concurrent.api.Single;

import static java.util.function.Function.identity;

/**
 * An {@link StreamingHttpServiceFilterFactory} implementation which offloads filters using a provided strategy.
 */
final class OffloadingFilter implements StreamingHttpServiceFilterFactory, HttpExecutionStrategyInfluencer {

    private final HttpExecutionStrategy strategy;
    private final StreamingHttpServiceFilterFactory offloaded;

    /**
     * @param strategy Execution strategy for the offloaded filters
     * @param offloaded Filters to be offloaded
     */
    OffloadingFilter(HttpExecutionStrategy strategy, StreamingHttpServiceFilterFactory offloaded) {
        this.strategy = strategy;
        this.offloaded = offloaded;
    }

    @Override
    public StreamingHttpServiceFilter create(StreamingHttpService service) {
        return new StreamingHttpServiceFilter(offloaded.create(service)) {

            @Override
            public Single<StreamingHttpResponse> handle(final HttpServiceContext ctx,
                                                        StreamingHttpRequest request,
                                                        final StreamingHttpResponseFactory responseFactory) {
                Executor se = strategy.executor();
                Executor e = null != se ? se : ctx.executionContext().executor();

                // The service should see our ExecutionStrategy inside the ExecutionContext:
                final HttpServiceContext wrappedCtx =
                        new ExecutionContextOverridingServiceContext(ctx, strategy, e);

                if (strategy.isDataReceiveOffloaded()) {
                    request = request.transformMessageBody(p -> p.publishOn(e));
                }
                final Single<StreamingHttpResponse> resp;
                if (strategy.isMetadataReceiveOffloaded()) {
                    final StreamingHttpRequest r = request;
                    resp = e.submit(() -> delegate().handle(wrappedCtx, r, responseFactory).subscribeShareContext())
                            // exec.submit() returns a Single<Single<response>>, so flatten the nested Single.
                            .flatMap(identity());
                } else {
                    resp = delegate().handle(wrappedCtx, request, responseFactory);
                }
                return strategy.isSendOffloaded() ?
                        // This is different from invokeService() where we just offload once on the  flattened
                        // (meta + data) stream. In this case, we need to preserve the service contract and hence
                        // have to offload both meta and data separately.
                        resp.map(r -> r.transformMessageBody(p -> p.subscribeOn(e))).subscribeOn(e) : resp;
            }
        };
    }

    @Override
    public HttpExecutionStrategy influenceStrategy(final HttpExecutionStrategy strategy) {
        // no influence
        return strategy;
    }
}

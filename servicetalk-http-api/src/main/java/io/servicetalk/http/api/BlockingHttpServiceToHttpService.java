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

final class BlockingHttpServiceToHttpService extends HttpService {
    private final BlockingHttpService service;
    private final HttpExecutionStrategy effectiveStrategy;

    private BlockingHttpServiceToHttpService(final BlockingHttpService service,
                                             final HttpExecutionStrategy effectiveStrategy) {
        this.service = requireNonNull(service);
        this.effectiveStrategy = requireNonNull(effectiveStrategy);
    }

    @Override
    public Single<HttpResponse> handle(final HttpServiceContext ctx, final HttpRequest request,
                                       final HttpResponseFactory responseFactory) {
        return blockingToSingle(() -> service.handle(ctx, request, responseFactory));
    }

    @Override
    public Completable closeAsync() {
        return blockingToCompletable(service::close);
    }

    @Override
    BlockingHttpService asBlockingServiceInternal() {
        return service;
    }

    @Override
    public HttpExecutionStrategy executionStrategy() {
        return effectiveStrategy;
    }

    static HttpService transform(final BlockingHttpService service) {
        // The recommended approach for filtering is using the filter factories which forces people to use the
        // StreamingHttpServiceFilter API and use the effective strategy. When that path is used, then we will not get
        // here as the intermediate transitions take care of returning the original StreamingHttpService.
        // If we are here, it is for a user implemented BlockingStreamingHttpService, so we assume the strategy provided
        // by the passed service is the effective strategy.
        return new BlockingHttpServiceToHttpService(service, service.executionStrategy());
    }
}

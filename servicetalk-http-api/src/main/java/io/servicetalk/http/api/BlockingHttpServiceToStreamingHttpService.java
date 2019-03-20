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
import static io.servicetalk.http.api.HttpExecutionStrategies.OFFLOAD_RECEIVE_META_STRATEGY;
import static java.util.Objects.requireNonNull;

final class BlockingHttpServiceToStreamingHttpService implements StreamingHttpService {
    private final BlockingHttpService service;

    BlockingHttpServiceToStreamingHttpService(final BlockingHttpService service) {
        this.service = requireNonNull(service);
    }

    @Override
    public Single<StreamingHttpResponse> handle(final HttpServiceContext ctx,
                                                final StreamingHttpRequest request,
                                                final StreamingHttpResponseFactory responseFactory) {
        return request.toRequest().flatMap(req -> blockingToSingle(() -> service.handle(
                ctx, req, ctx.responseFactory())).map(HttpResponse::toStreamingResponse));
    }

    @Override
    public Completable closeAsync() {
        return blockingToCompletable(service::close);
    }

    @Override
    public HttpExecutionStrategy computeExecutionStrategy(HttpExecutionStrategy other) {
        // Since we are converting to a different programming model, try altering the strategy for the returned service
        // to contain an appropriate default. We achieve this by merging the expected strategy with the provided
        // service strategy.
        return service.computeExecutionStrategy(other.merge(OFFLOAD_RECEIVE_META_STRATEGY));
    }
}

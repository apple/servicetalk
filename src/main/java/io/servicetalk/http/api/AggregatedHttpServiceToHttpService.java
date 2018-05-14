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
import io.servicetalk.transport.api.ConnectionContext;

import java.util.function.Function;

import static io.servicetalk.http.api.DefaultFullHttpRequest.from;
import static java.util.Objects.requireNonNull;

final class AggregatedHttpServiceToHttpService extends HttpService<HttpPayloadChunk, HttpPayloadChunk> {
    private final AggregatedHttpService aggregatedService;

    AggregatedHttpServiceToHttpService(final AggregatedHttpService aggregatedService) {
        this.aggregatedService = requireNonNull(aggregatedService);
    }

    @Override
    public Single<HttpResponse<HttpPayloadChunk>> handle(final ConnectionContext ctx,
                                                         final HttpRequest<HttpPayloadChunk> request) {
        return from(request, ctx.getBufferAllocator())
                .flatMap(req -> aggregatedService.handle(ctx, req)
                        .map(DefaultFullHttpResponse::toHttpResponse));
    }

    @Override
    public Completable closeAsync() {
        return aggregatedService.closeAsync();
    }

    @Override
    AggregatedHttpService asAggregatedServiceInternal(
                                Function<HttpPayloadChunk, HttpPayloadChunk> requestPayloadTransformer,
                                Function<HttpPayloadChunk, HttpPayloadChunk> responsePayloadTransformer) {
        return aggregatedService;
    }
}

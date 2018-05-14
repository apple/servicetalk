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

import static io.servicetalk.http.api.DefaultFullHttpRequest.toHttpRequest;
import static io.servicetalk.http.api.DefaultFullHttpResponse.from;
import static java.util.Objects.requireNonNull;

public class HttpServiceToAggregatedHttpService<I, O> extends AggregatedHttpService {
    private final HttpPayloadChunkService chunkService;

    HttpServiceToAggregatedHttpService(final HttpService<I, O> service,
                                       final Function<HttpPayloadChunk, I> requestPayloadTransformer,
                                       final Function<O, HttpPayloadChunk> responsePayloadTransformer) {
        this.chunkService = new HttpPayloadChunkService(service, requestPayloadTransformer, responsePayloadTransformer);
    }

    @Override
    public Single<FullHttpResponse> handle(final ConnectionContext ctx, final FullHttpRequest request) {
        return chunkService.handle(ctx, toHttpRequest(request))
                .flatMap(response -> from(response, ctx.getExecutionContext().getBufferAllocator()));
    }

    @Override
    public Completable closeAsync() {
        return chunkService.closeAsync();
    }

    @Override
    HttpService<HttpPayloadChunk, HttpPayloadChunk> asServiceInternal() {
        return chunkService;
    }

    private final class HttpPayloadChunkService extends HttpService<HttpPayloadChunk, HttpPayloadChunk> {
        private final HttpService<I, O> service;
        private final Function<HttpPayloadChunk, I> requestPayloadTransformer;
        private final Function<O, HttpPayloadChunk> responsePayloadTransformer;

        HttpPayloadChunkService(final HttpService<I, O> service,
                                final Function<HttpPayloadChunk, I> requestPayloadTransformer,
                                final Function<O, HttpPayloadChunk> responsePayloadTransformer) {
            this.service = requireNonNull(service);
            this.requestPayloadTransformer = requireNonNull(requestPayloadTransformer);
            this.responsePayloadTransformer = requireNonNull(responsePayloadTransformer);
        }

        @Override
        public Single<HttpResponse<HttpPayloadChunk>> handle(final ConnectionContext ctx,
                                                             final HttpRequest<HttpPayloadChunk> request) {
            return service.handle(ctx, request.transformPayloadBody(
                            requestPayload -> requestPayload.map(requestPayloadTransformer)))
                    .map(response -> response.transformPayloadBody(
                            responsePayload -> responsePayload.map(responsePayloadTransformer)));
        }

        @Override
        public Completable closeAsync() {
            return service.closeAsync();
        }

        @Override
        AggregatedHttpService asAggregatedServiceInternal(
                                        Function<HttpPayloadChunk, HttpPayloadChunk> requestPayloadTransformer,
                                        Function<HttpPayloadChunk, HttpPayloadChunk> responsePayloadTransformer) {
            return HttpServiceToAggregatedHttpService.this;
        }
    }
}

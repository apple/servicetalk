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
import io.servicetalk.transport.api.ExecutionContext;

import java.util.function.Function;

import static io.servicetalk.http.api.DefaultFullHttpRequest.toHttpRequest;
import static io.servicetalk.http.api.DefaultFullHttpResponse.from;
import static java.util.Objects.requireNonNull;

final class HttpRequesterToAggregatedHttpRequester<I, O> extends AggregatedHttpRequester {
    private final HttpPayloadChunkRequester payloadRequester;

    HttpRequesterToAggregatedHttpRequester(final HttpRequester<I, O> requester,
                                           final Function<HttpPayloadChunk, I> requestPayloadTransformer,
                                           final Function<O, HttpPayloadChunk> responsePayloadTransformer) {
        payloadRequester = new HttpPayloadChunkRequester(
                requester, requestPayloadTransformer, responsePayloadTransformer);
    }

    @Override
    public Single<FullHttpResponse> request(final FullHttpRequest request) {
        return payloadRequester.request(toHttpRequest(request)).flatMap(response ->
                from(response, payloadRequester.getExecutionContext().getBufferAllocator()));
    }

    @Override
    public ExecutionContext getExecutionContext() {
        return payloadRequester.getExecutionContext();
    }

    @Override
    public Completable onClose() {
        return payloadRequester.onClose();
    }

    @Override
    public Completable closeAsync() {
        return payloadRequester.closeAsync();
    }

    @Override
    HttpRequester<HttpPayloadChunk, HttpPayloadChunk> asRequesterInternal() {
        return payloadRequester;
    }

    private final class HttpPayloadChunkRequester extends HttpRequester<HttpPayloadChunk, HttpPayloadChunk> {
        private final HttpRequester<I, O> requester;
        private final Function<HttpPayloadChunk, I> requestPayloadTransformer;
        private final Function<O, HttpPayloadChunk> responsePayloadTransformer;

        HttpPayloadChunkRequester(final HttpRequester<I, O> requester,
                                  final Function<HttpPayloadChunk, I> requestPayloadTransformer,
                                  final Function<O, HttpPayloadChunk> responsePayloadTransformer) {
            this.requester = requireNonNull(requester);
            this.requestPayloadTransformer = requireNonNull(requestPayloadTransformer);
            this.responsePayloadTransformer = requireNonNull(responsePayloadTransformer);
        }

        @Override
        public Single<HttpResponse<HttpPayloadChunk>> request(final HttpRequest<HttpPayloadChunk> request) {
            return requester.request(request.transformPayloadBody(
                        requestPayload -> requestPayload.map(requestPayloadTransformer)))
                    .map(response -> response.transformPayloadBody(
                        responsePayload -> responsePayload.map(responsePayloadTransformer)));
        }

        @Override
        public ExecutionContext getExecutionContext() {
            return requester.getExecutionContext();
        }

        @Override
        public Completable onClose() {
            return requester.onClose();
        }

        @Override
        public Completable closeAsync() {
            return requester.closeAsync();
        }

        @Override
        AggregatedHttpRequester asAggregatedRequesterInternal(
                                Function<HttpPayloadChunk, HttpPayloadChunk> requestPayloadTransformer,
                                Function<HttpPayloadChunk, HttpPayloadChunk> responsePayloadTransformer) {
            return HttpRequesterToAggregatedHttpRequester.this;
        }
    }
}

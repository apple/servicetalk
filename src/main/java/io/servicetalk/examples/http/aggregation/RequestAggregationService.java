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
package io.servicetalk.examples.http.aggregation;

import io.servicetalk.concurrent.api.Single;
import io.servicetalk.http.api.HttpPayloadChunk;
import io.servicetalk.http.api.HttpRequest;
import io.servicetalk.http.api.HttpResponse;
import io.servicetalk.http.api.HttpService;
import io.servicetalk.http.api.LastHttpPayloadChunk;
import io.servicetalk.transport.api.ConnectionContext;

import static io.servicetalk.concurrent.api.Single.success;
import static io.servicetalk.http.api.HttpPayloadChunks.aggregateChunks;
import static io.servicetalk.http.api.HttpResponseStatuses.OK;
import static io.servicetalk.http.api.HttpResponses.newResponse;

final class RequestAggregationService extends HttpService<HttpPayloadChunk, HttpPayloadChunk> {

    @Override
    public Single<HttpResponse<HttpPayloadChunk>> handle(final ConnectionContext ctx,
                                                         final HttpRequest<HttpPayloadChunk> request) {
        // ServiceTalk by default delivers content as multiple payload chunks.
        // If required, users can aggregate potential multiple chunks into a single chunk.
        Single<LastHttpPayloadChunk> aggregatedPayload = aggregateChunks(request.getPayloadBody(), ctx.getBufferAllocator());
        return success(newResponse(OK, aggregatedPayload.toPublisher().map(chunk -> (HttpPayloadChunk) chunk)));
    }
}

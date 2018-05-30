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
package io.servicetalk.examples.http.service.composition.backends;

import io.servicetalk.buffer.api.Buffer;
import io.servicetalk.concurrent.api.Single;
import io.servicetalk.examples.http.service.composition.pojo.Rating;
import io.servicetalk.http.api.AggregatedHttpRequest;
import io.servicetalk.http.api.AggregatedHttpResponse;
import io.servicetalk.http.api.AggregatedHttpService;
import io.servicetalk.http.api.HttpPayloadChunk;
import io.servicetalk.http.router.predicate.HttpPredicateRouterBuilder;
import io.servicetalk.transport.api.ConnectionContext;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Function;

import static io.servicetalk.concurrent.api.Single.success;
import static io.servicetalk.data.jackson.JacksonSerializers.serializer;
import static io.servicetalk.http.api.AggregatedHttpResponses.newResponse;
import static io.servicetalk.http.api.HttpHeaderNames.CONTENT_TYPE;
import static io.servicetalk.http.api.HttpHeaderValues.APPLICATION_JSON;
import static io.servicetalk.http.api.HttpResponseStatuses.BAD_REQUEST;
import static io.servicetalk.http.api.HttpResponseStatuses.OK;

/**
 * A service that returns {@link Rating}s for an entity.
 */
final class RatingBackend extends AggregatedHttpService {

    private static final String ENTITY_ID_QP_NAME = "entityId";
    private final ObjectMapper objectMapper;

    private RatingBackend(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public Single<AggregatedHttpResponse<HttpPayloadChunk>> handle(final ConnectionContext ctx,
                                                                   final AggregatedHttpRequest<HttpPayloadChunk> request) {
        final String entityId = request.parseQuery().get(ENTITY_ID_QP_NAME);
        if (entityId == null) {
            return success(newResponse(BAD_REQUEST));
        }

        // Create a random rating
        Rating rating = new Rating(entityId, ThreadLocalRandom.current().nextInt(1, 6));
        final Function<Rating, Buffer> serializer = serializer(objectMapper, Rating.class, ctx.getBufferAllocator());
        final AggregatedHttpResponse<HttpPayloadChunk> response = newResponse(OK, serializer.apply(rating));
        response.getHeaders().set(CONTENT_TYPE, APPLICATION_JSON);
        return success(response);
    }

    static AggregatedHttpService newRatingService(ObjectMapper objectMapper) {
        HttpPredicateRouterBuilder routerBuilder = new HttpPredicateRouterBuilder();
        return routerBuilder.whenPathStartsWith("/rating")
                .thenRouteTo(new RatingBackend(objectMapper))
                .buildAggregated();
    }
}

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

import io.servicetalk.concurrent.api.Single;
import io.servicetalk.examples.http.service.composition.pojo.Rating;
import io.servicetalk.http.api.HttpRequest;
import io.servicetalk.http.api.HttpResponse;
import io.servicetalk.http.api.HttpResponseFactory;
import io.servicetalk.http.api.HttpSerializationProvider;
import io.servicetalk.http.api.HttpService;
import io.servicetalk.http.api.HttpServiceContext;
import io.servicetalk.http.router.predicate.HttpPredicateRouterBuilder;

import java.util.concurrent.ThreadLocalRandom;

import static io.servicetalk.concurrent.api.Single.success;
import static io.servicetalk.http.api.HttpResponseStatuses.BAD_REQUEST;

/**
 * A service that returns {@link Rating}s for an entity.
 */
final class RatingBackend extends HttpService {

    private static final String ENTITY_ID_QP_NAME = "entityId";
    private final HttpSerializationProvider serializer;

    private RatingBackend(HttpSerializationProvider serializer) {
        this.serializer = serializer;
    }

    @Override
    public Single<? extends HttpResponse> handle(HttpServiceContext ctx, HttpRequest request,
                                                 HttpResponseFactory responseFactory) {
        final String entityId = request.parseQuery().get(ENTITY_ID_QP_NAME);
        if (entityId == null) {
            return success(responseFactory.newResponse(BAD_REQUEST));
        }

        // Create a random rating
        Rating rating = new Rating(entityId, ThreadLocalRandom.current().nextInt(1, 6));
        return success(responseFactory.ok().setPayloadBody(rating, serializer.serializerFor(Rating.class)));
    }

    static HttpService newRatingService(HttpSerializationProvider serializer) {
        HttpPredicateRouterBuilder routerBuilder = new HttpPredicateRouterBuilder();
        return routerBuilder.whenPathStartsWith("/rating")
                .thenRouteTo(new RatingBackend(serializer))
                .build();
    }
}

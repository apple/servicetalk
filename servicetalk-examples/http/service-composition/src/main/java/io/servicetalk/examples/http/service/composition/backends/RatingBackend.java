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
import io.servicetalk.http.api.HttpService;
import io.servicetalk.http.api.HttpServiceContext;
import io.servicetalk.http.api.StreamingHttpService;
import io.servicetalk.http.router.predicate.HttpPredicateRouterBuilder;

import java.util.concurrent.ThreadLocalRandom;

import static io.servicetalk.concurrent.api.Single.succeeded;
import static io.servicetalk.examples.http.service.composition.SerializerUtils.ENTITY_ID_QP_NAME;
import static io.servicetalk.examples.http.service.composition.SerializerUtils.RATING_SERIALIZER;

/**
 * A service that returns {@link Rating}s for an entity.
 */
final class RatingBackend implements HttpService {
    @Override
    public Single<HttpResponse> handle(HttpServiceContext ctx, HttpRequest request,
                                       HttpResponseFactory responseFactory) {
        final String entityId = request.queryParameter(ENTITY_ID_QP_NAME);
        if (entityId == null) {
            return succeeded(responseFactory.badRequest());
        }

        // Create a random rating
        Rating rating = new Rating(entityId, ThreadLocalRandom.current().nextInt(1, 6));
        return succeeded(responseFactory.ok().payloadBody(rating, RATING_SERIALIZER));
    }

    static StreamingHttpService newRatingService() {
        HttpPredicateRouterBuilder routerBuilder = new HttpPredicateRouterBuilder();
        return routerBuilder.whenPathStartsWith("/rating")
                .thenRouteTo(new RatingBackend())
                .buildStreaming();
    }
}

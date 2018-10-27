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

import io.servicetalk.concurrent.api.Publisher;
import io.servicetalk.concurrent.api.Single;
import io.servicetalk.examples.http.service.composition.pojo.Recommendation;
import io.servicetalk.http.api.HttpRequest;
import io.servicetalk.http.api.HttpResponse;
import io.servicetalk.http.api.HttpResponseFactory;
import io.servicetalk.http.api.HttpSerializationProvider;
import io.servicetalk.http.api.HttpService;
import io.servicetalk.http.api.HttpServiceContext;
import io.servicetalk.http.api.StreamingHttpRequest;
import io.servicetalk.http.api.StreamingHttpResponse;
import io.servicetalk.http.api.StreamingHttpResponseFactory;
import io.servicetalk.http.api.StreamingHttpService;
import io.servicetalk.http.router.predicate.HttpPredicateRouterBuilder;
import io.servicetalk.serialization.api.TypeHolder;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

import static io.servicetalk.concurrent.api.Single.defer;
import static io.servicetalk.concurrent.api.Single.success;
import static java.lang.Integer.parseInt;
import static java.lang.String.valueOf;
import static java.util.concurrent.TimeUnit.SECONDS;

/**
 * A service that generates {@link Recommendation}s for a user.
 */
final class RecommendationBackend {

    private static final TypeHolder<List<Recommendation>> typeOfRecommendation =
            new TypeHolder<List<Recommendation>>() { };
    private static final String USER_ID_QP_NAME = "userId";
    private static final String EXPECTED_ENTITY_COUNT_QP_NAME = "expectedEntityCount";

    private RecommendationBackend() {
        // No instances.
    }

    static StreamingHttpService newRecommendationsService(HttpSerializationProvider serializer) {
        HttpPredicateRouterBuilder routerBuilder = new HttpPredicateRouterBuilder();
        routerBuilder.whenPathStartsWith("/recommendations/stream")
                .thenRouteTo(new StreamingService(serializer));
        routerBuilder.whenPathStartsWith("/recommendations/aggregated")
                .thenRouteTo(new AggregatedService(serializer));
        return routerBuilder.buildStreaming();
    }

    private static Recommendation newRecommendation() {
        final ThreadLocalRandom random = ThreadLocalRandom.current();
        // Generate random IDs for recommended entity IDs
        final int entityId = random.nextInt();
        // Generate random ID for recommended by user Id.
        return new Recommendation(valueOf(entityId), valueOf(random.nextInt(1000)));
    }

    private static final class StreamingService extends StreamingHttpService {

        private final HttpSerializationProvider serializer;

        StreamingService(final HttpSerializationProvider serializer) {
            this.serializer = serializer;
        }

        @Override
        public Single<StreamingHttpResponse> handle(HttpServiceContext ctx, StreamingHttpRequest request,
                                                    StreamingHttpResponseFactory responseFactory) {
            final String userId = request.queryParameter(USER_ID_QP_NAME);
            if (userId == null) {
                return success(responseFactory.badRequest());
            }

            // Create a new random recommendation every 1 SECOND.
            Publisher<Recommendation> recommendations = ctx.executionContext().executor().timer(1, SECONDS)
                    // We use defer() here so that we do not eagerly create a Recommendation which will get emitted for
                    // every schedule. defer() helps us lazily create a new Recommendation object every time we the
                    // scheduler emits a tick.
                    .andThen(defer(() -> success(newRecommendation())))
                    // Since schedule() only schedules a single tick, we repeat the ticks to generate infinite
                    // recommendations. This simulates a push based API which pushes new recommendations as and when
                    // they are available.
                    .repeat(count -> true);

            return success(responseFactory.ok()
                    .payloadBody(recommendations, serializer.serializerFor(Recommendation.class)));
        }
    }

    private static final class AggregatedService extends HttpService {

        private final HttpSerializationProvider serializer;

        AggregatedService(final HttpSerializationProvider serializer) {
            this.serializer = serializer;
        }

        @Override
        public Single<? extends HttpResponse> handle(HttpServiceContext ctx,
                                                     HttpRequest request, HttpResponseFactory responseFactory) {
            final String userId = request.queryParameter(USER_ID_QP_NAME);
            if (userId == null) {
                return success(responseFactory.badRequest());
            }
            int expectedEntitiesCount = 10;
            final String expectedEntitiesCountStr = request.queryParameter(EXPECTED_ENTITY_COUNT_QP_NAME);
            if (expectedEntitiesCountStr != null) {
                expectedEntitiesCount = parseInt(expectedEntitiesCountStr);
            }
            List<Recommendation> recommendations = new ArrayList<>(expectedEntitiesCount);
            for (int i = 0; i < expectedEntitiesCount; i++) {
                recommendations.add(newRecommendation());
            }

            // Serialize the Recommendation list to a single Buffer containing JSON and use it as the response payload.
            return success(responseFactory.ok()
                    .payloadBody(recommendations, serializer.serializerFor(typeOfRecommendation)));
        }
    }
}

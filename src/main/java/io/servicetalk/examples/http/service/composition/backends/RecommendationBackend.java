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
import io.servicetalk.http.api.AggregatedHttpRequest;
import io.servicetalk.http.api.AggregatedHttpResponse;
import io.servicetalk.http.api.AggregatedHttpResponses;
import io.servicetalk.http.api.AggregatedHttpService;
import io.servicetalk.http.api.HttpPayloadChunk;
import io.servicetalk.http.api.HttpRequest;
import io.servicetalk.http.api.HttpResponse;
import io.servicetalk.http.api.HttpResponses;
import io.servicetalk.http.api.HttpSerializer;
import io.servicetalk.http.api.HttpService;
import io.servicetalk.http.router.predicate.HttpPredicateRouterBuilder;
import io.servicetalk.transport.api.ConnectionContext;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import javax.annotation.Nonnull;

import static io.servicetalk.concurrent.api.Single.defer;
import static io.servicetalk.concurrent.api.Single.success;
import static io.servicetalk.http.api.AggregatedHttpResponses.newResponse;
import static io.servicetalk.http.api.HttpResponseStatuses.BAD_REQUEST;
import static io.servicetalk.http.api.HttpResponseStatuses.OK;
import static java.lang.Integer.parseInt;
import static java.lang.String.valueOf;
import static java.util.concurrent.TimeUnit.SECONDS;

/**
 * A service that generates {@link Recommendation}s for a user.
 */
final class RecommendationBackend {

    private static final String USER_ID_QP_NAME = "userId";
    private static final String EXPECTED_ENTITY_COUNT_QP_NAME = "expectedEntityCount";

    static HttpService newRecommendationsService(HttpSerializer serializer) {
        HttpPredicateRouterBuilder routerBuilder = new HttpPredicateRouterBuilder();
        routerBuilder.whenPathStartsWith("/recommendations/stream")
                .thenRouteTo(new StreamingService(serializer));
        routerBuilder.whenPathStartsWith("/recommendations/aggregated")
                .thenRouteTo(new AggregatedService(serializer));
        return routerBuilder.build();
    }

    @Nonnull
    private static Recommendation newRecommendation(final ThreadLocalRandom random) {
        // Generate random IDs for recommended entity IDs
        final int entityId = random.nextInt();
        // Generate random ID for recommended by user Id.
        return new Recommendation(valueOf(entityId), valueOf(random.nextInt()));
    }

    private static final class StreamingService extends HttpService {

        private final HttpSerializer serializer;

        public StreamingService(final HttpSerializer serializer) {
            this.serializer = serializer;
        }

        @Override
        public Single<HttpResponse<HttpPayloadChunk>> handle(final ConnectionContext ctx, final HttpRequest<HttpPayloadChunk> request) {
            final String userId = request.parseQuery().get(USER_ID_QP_NAME);
            if (userId == null) {
                return success(HttpResponses.newResponse(BAD_REQUEST));
            }

            // Create a new random recommendation every 1 SECOND.
            Publisher<Recommendation> recommendations = ctx.getExecutor().timer(1, SECONDS)
                    // We use defer() here so that we do not eagerly create a Recommendation which will get emitted for
                    // every schedule. defer() helps us lazily create a new Recommendation object every time we the
                    // scheduler emits a tick.
                    .andThen(defer(() -> success(newRecommendation(ThreadLocalRandom.current()))))
                    // Since schedule() only schedules a single tick, we repeat the ticks to generate infinite
                    // recommendations. This simulates a push based API which pushes new recommendations as and when
                    // they are available.
                    .repeat(count -> true);

            return success(serializer.serialize(HttpResponses.newResponse(OK, recommendations), ctx.getBufferAllocator(), Recommendation.class));
        }
    }

    private static final class AggregatedService extends AggregatedHttpService {

        private final HttpSerializer serializer;

        public AggregatedService(final HttpSerializer serializer) {
            this.serializer = serializer;
        }

        @Override
        public Single<AggregatedHttpResponse<HttpPayloadChunk>> handle(final ConnectionContext ctx,
                                                                       final AggregatedHttpRequest<HttpPayloadChunk> request) {
            final String userId = request.parseQuery().get(USER_ID_QP_NAME);
            if (userId == null) {
                return success(AggregatedHttpResponses.newResponse(BAD_REQUEST));
            }
            int expectedEntitiesCount = 10;
            final String expectedEntitiesCountStr = request.parseQuery().get(EXPECTED_ENTITY_COUNT_QP_NAME);
            if (expectedEntitiesCountStr != null) {
                expectedEntitiesCount = parseInt(expectedEntitiesCountStr);
            }
            List<Recommendation> recommendations = new ArrayList<>(expectedEntitiesCount);
            final ThreadLocalRandom random = ThreadLocalRandom.current();
            for (int i = 0; i < expectedEntitiesCount; i++) {
                recommendations.add(newRecommendation(random));
            }

            // Serialize the Recommendation list to a single Buffer containing JSON and use it as the response payload.
            return success(serializer.serialize(newResponse(OK, recommendations), ctx.getBufferAllocator()));
        }
    }
}

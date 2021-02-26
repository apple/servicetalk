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
package io.servicetalk.examples.http.service.composition;

import io.servicetalk.examples.http.service.composition.pojo.FullRecommendation;
import io.servicetalk.examples.http.service.composition.pojo.Metadata;
import io.servicetalk.examples.http.service.composition.pojo.Rating;
import io.servicetalk.examples.http.service.composition.pojo.Recommendation;
import io.servicetalk.examples.http.service.composition.pojo.User;
import io.servicetalk.http.api.BlockingHttpClient;
import io.servicetalk.http.api.BlockingHttpService;
import io.servicetalk.http.api.HttpRequest;
import io.servicetalk.http.api.HttpResponse;
import io.servicetalk.http.api.HttpResponseFactory;
import io.servicetalk.http.api.HttpServiceContext;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

import static io.servicetalk.examples.http.service.composition.SerializerUtils.ENTITY_ID_QP_NAME;
import static io.servicetalk.examples.http.service.composition.SerializerUtils.FULL_RECOMMEND_LIST_SERIALIZER;
import static io.servicetalk.examples.http.service.composition.SerializerUtils.METADATA_SERIALIZER;
import static io.servicetalk.examples.http.service.composition.SerializerUtils.RATING_SERIALIZER;
import static io.servicetalk.examples.http.service.composition.SerializerUtils.RECOMMEND_LIST_SERIALIZER;
import static io.servicetalk.examples.http.service.composition.SerializerUtils.USER_ID_QP_NAME;
import static io.servicetalk.examples.http.service.composition.SerializerUtils.USER_SERIALIZER;
import static io.servicetalk.examples.http.service.composition.backends.ErrorResponseGeneratingServiceFilter.SIMULATE_ERROR_QP_NAME;

/**
 * This service provides an API that fetches recommendations serially using blocking APIs. Returned response is a single
 * JSON array containing all {@link FullRecommendation}s.
 */
final class BlockingGatewayService implements BlockingHttpService {
    private static final Logger LOGGER = LoggerFactory.getLogger(BlockingGatewayService.class);

    private final BlockingHttpClient recommendationClient;
    private final BlockingHttpClient metadataClient;
    private final BlockingHttpClient ratingClient;
    private final BlockingHttpClient userClient;

    BlockingGatewayService(final BlockingHttpClient recommendationClient,
                           final BlockingHttpClient metadataClient,
                           final BlockingHttpClient ratingClient,
                           final BlockingHttpClient userClient) {
        this.recommendationClient = recommendationClient;
        this.metadataClient = metadataClient;
        this.ratingClient = ratingClient;
        this.userClient = userClient;
    }

    @Override
    public HttpResponse handle(final HttpServiceContext ctx, final HttpRequest request,
                               final HttpResponseFactory responseFactory) throws Exception {
        final String userId = request.queryParameter(USER_ID_QP_NAME);
        if (userId == null) {
            return responseFactory.badRequest();
        }

        final Iterable<String> errorQpValues = () -> request.queryParametersIterator(SIMULATE_ERROR_QP_NAME);
        List<Recommendation> recommendations =
                recommendationClient.request(recommendationClient.get("/recommendations/aggregated")
                        .addQueryParameter(USER_ID_QP_NAME, userId)
                        .addQueryParameters(SIMULATE_ERROR_QP_NAME, errorQpValues))
                        .payloadBody(RECOMMEND_LIST_SERIALIZER);

        List<FullRecommendation> fullRecommendations = new ArrayList<>(recommendations.size());
        for (Recommendation recommendation : recommendations) {
            // For each recommendation, fetch the details.
            final Metadata metadata =
                    metadataClient.request(metadataClient.get("/metadata")
                            .addQueryParameter(ENTITY_ID_QP_NAME, recommendation.getEntityId())
                            .addQueryParameters(SIMULATE_ERROR_QP_NAME, errorQpValues))
                            .payloadBody(METADATA_SERIALIZER);

            final User user =
                    userClient.request(userClient.get("/user")
                            .addQueryParameter(USER_ID_QP_NAME, recommendation.getEntityId())
                            .addQueryParameters(SIMULATE_ERROR_QP_NAME, errorQpValues))
                            .payloadBody(USER_SERIALIZER);

            Rating rating;
            try {
                rating = ratingClient.request(ratingClient.get("/rating")
                        .addQueryParameter(ENTITY_ID_QP_NAME, recommendation.getEntityId())
                        .addQueryParameters(SIMULATE_ERROR_QP_NAME, errorQpValues))
                        .payloadBody(RATING_SERIALIZER);
            } catch (Exception cause) {
                // We consider ratings to be a non-critical data and hence we substitute the response
                // with a static "unavailable" rating when the rating service is unavailable or provides
                // a bad response. This is typically referred to as a "fallback".
                LOGGER.error("Error querying ratings service. Ignoring and providing a fallback.", cause);
                rating = new Rating(recommendation.getEntityId(), -1);
            }

            fullRecommendations.add(new FullRecommendation(metadata, user, rating));
        }

        return responseFactory.ok().payloadBody(fullRecommendations, FULL_RECOMMEND_LIST_SERIALIZER);
    }
}

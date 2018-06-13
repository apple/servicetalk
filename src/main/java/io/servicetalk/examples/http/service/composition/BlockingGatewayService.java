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
import io.servicetalk.http.api.AggregatedHttpRequest;
import io.servicetalk.http.api.AggregatedHttpResponse;
import io.servicetalk.http.api.BlockingAggregatedHttpClient;
import io.servicetalk.http.api.BlockingAggregatedHttpService;
import io.servicetalk.http.api.HttpPayloadChunk;
import io.servicetalk.http.api.HttpSerializer;
import io.servicetalk.serialization.api.TypeHolder;
import io.servicetalk.transport.api.ConnectionContext;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

import static io.servicetalk.http.api.AggregatedHttpRequests.newRequest;
import static io.servicetalk.http.api.AggregatedHttpResponses.newResponse;
import static io.servicetalk.http.api.HttpRequestMethods.GET;
import static io.servicetalk.http.api.HttpResponseStatuses.BAD_REQUEST;
import static io.servicetalk.http.api.HttpResponseStatuses.OK;

/**
 * This service provides an API that fetches recommendations serially using blocking APIs. Returned response is a single
 * JSON array containing all {@link FullRecommendation}s.
 */
final class BlockingGatewayService extends BlockingAggregatedHttpService {

    private static final Logger LOGGER = LoggerFactory.getLogger(BlockingGatewayService.class);

    private static final TypeHolder<List<Recommendation>> typeOfRecommendation = new TypeHolder<List<Recommendation>>(){};
    private static final String USER_ID_QP_NAME = "userId";

    private final HttpSerializer serializer;

    // Currently we do not have an aggregated Blocking Client variant in ServiceTalk. So, we use the HttpPayloadChunk
    // variant.
    private final BlockingAggregatedHttpClient recommendationClient;
    private final BlockingAggregatedHttpClient metadataClient;
    private final BlockingAggregatedHttpClient ratingClient;
    private final BlockingAggregatedHttpClient userClient;

    public BlockingGatewayService(final BlockingAggregatedHttpClient recommendationClient,
                                  final BlockingAggregatedHttpClient metadataClient,
                                  final BlockingAggregatedHttpClient ratingClient,
                                  final BlockingAggregatedHttpClient userClient,
                                  final HttpSerializer serializer) {
        this.recommendationClient = recommendationClient;
        this.metadataClient = metadataClient;
        this.ratingClient = ratingClient;
        this.userClient = userClient;
        this.serializer = serializer;
    }

    @Override
    public AggregatedHttpResponse<HttpPayloadChunk> handle(final ConnectionContext ctx,
                                                           final AggregatedHttpRequest<HttpPayloadChunk> request)
            throws Exception {
        final String userId = request.parseQuery().get(USER_ID_QP_NAME);
        if (userId == null) {
            return newResponse(BAD_REQUEST);
        }

        List<Recommendation> recommendations = serializer.deserialize(recommendationClient.request(newRequest(GET,
                "/recommendations/aggregated?userId=" + userId)), typeOfRecommendation).getPayloadBody();

        List<FullRecommendation> fullRecommendations = new ArrayList<>(recommendations.size());
        for (Recommendation recommendation : recommendations) {
            // For each recommendation, fetch the details.
            final Metadata metadata = serializer.deserialize(metadataClient.request(newRequest(GET,
                    "/metadata?entityId=" + recommendation.getEntityId())), Metadata.class).getPayloadBody();

            final User user = serializer.deserialize(userClient.request(newRequest(GET,
                    "/user?userId=" + recommendation.getEntityId())), User.class).getPayloadBody();

            Rating rating;
            try {
                rating = serializer.deserialize(ratingClient.request(newRequest(GET,
                        "/rating?entityId=" + recommendation.getEntityId())), Rating.class).getPayloadBody();
            } catch (Exception cause) {
                // We consider ratings to be a non-critical data and hence we substitute the response
                // with a static "unavailable" rating when the rating service is unavailable or provides
                // a bad response. This is typically referred to as a "fallback".
                LOGGER.error("Error querying ratings service. Ignoring and providing a fallback.", cause);
                rating = new Rating(recommendation.getEntityId(), -1);
            }

            fullRecommendations.add(new FullRecommendation(metadata, user, rating));
        }

        return serializer.serialize(newResponse(OK, fullRecommendations), ctx.getBufferAllocator());
    }
}

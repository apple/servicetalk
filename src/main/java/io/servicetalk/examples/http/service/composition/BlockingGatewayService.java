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
import io.servicetalk.transport.api.ConnectionContext;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import javax.annotation.Nullable;

import static io.servicetalk.buffer.api.Buffer.asInputStream;
import static io.servicetalk.data.jackson.JacksonSerializers.serialize;
import static io.servicetalk.http.api.AggregatedHttpRequests.newRequest;
import static io.servicetalk.http.api.AggregatedHttpResponses.newResponse;
import static io.servicetalk.http.api.HttpHeaderNames.CONTENT_TYPE;
import static io.servicetalk.http.api.HttpHeaderValues.APPLICATION_JSON;
import static io.servicetalk.http.api.HttpRequestMethods.GET;
import static io.servicetalk.http.api.HttpResponseStatuses.BAD_REQUEST;
import static io.servicetalk.http.api.HttpResponseStatuses.OK;

/**
 * This service provides an API that fetches recommendations serially using blocking APIs. Returned response is a single
 * JSON array containing all {@link FullRecommendation}s.
 */
final class BlockingGatewayService extends BlockingAggregatedHttpService {

    private static final Logger LOGGER = LoggerFactory.getLogger(BlockingGatewayService.class);

    private static final TypeReference<List<Recommendation>> typeOfRecommendation = new TypeReference<List<Recommendation>>(){};
    private static final String USER_ID_QP_NAME = "userId";

    private final ObjectMapper objectMapper;

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
                                  final ObjectMapper objectMapper) {
        this.recommendationClient = recommendationClient;
        this.metadataClient = metadataClient;
        this.ratingClient = ratingClient;
        this.userClient = userClient;
        this.objectMapper = objectMapper;
    }

    @Override
    public AggregatedHttpResponse<HttpPayloadChunk> handle(final ConnectionContext ctx,
                                                           final AggregatedHttpRequest<HttpPayloadChunk> request)
            throws Exception {
        final String userId = request.parseQuery().get(USER_ID_QP_NAME);
        if (userId == null) {
            return newResponse(BAD_REQUEST);
        }

        List<Recommendation> recommendations =
                deserializeRecommendationPayload(recommendationClient.request(newRequest(GET, "/recommendations/aggregated?userId=" + userId)));

        List<FullRecommendation> fullRecommendations = new ArrayList<>(recommendations.size());
        for (Recommendation recommendation : recommendations) {
            // For each recommendation, fetch the details.
            final Metadata metadata =
                    deserializePayload(metadataClient.request(newRequest(GET, "/metadata?entityId=" + recommendation.getEntityId())), Metadata.class);

            final User user =
                    deserializePayload(userClient.request(newRequest(GET, "/user?userId=" + recommendation.getEntityId())), User.class);

            Rating rating;
            try {
                rating = deserializePayload(ratingClient.request(newRequest(GET, "/rating?entityId=" + recommendation.getEntityId())), Rating.class);
            } catch (Exception cause) {
                // We consider ratings to be a non-critical data and hence we substitute the response
                // with a static "unavailable" rating when the rating service is unavailable or provides
                // a bad response. This is typically referred to as a "fallback".
                LOGGER.error("Error querying ratings service. Ignoring and providing a fallback.", cause);
                rating = new Rating(recommendation.getEntityId(), -1);
            }

            fullRecommendations.add(new FullRecommendation(metadata, user, rating));
        }

        return newResponse(OK, serialize(objectMapper.writer(), fullRecommendations, ctx.getBufferAllocator()));
    }

    private <T> T deserializeRecommendationPayload(AggregatedHttpResponse<HttpPayloadChunk> response) throws IOException {
        validateResponse(response);
        return objectMapper.readerFor(typeOfRecommendation).readValue(asInputStream(response.getPayloadBody().getContent()));
    }

    private <T> T deserializePayload(AggregatedHttpResponse<HttpPayloadChunk> response, Class<T> targetType) throws IOException {
        validateResponse(response);
        return objectMapper.readerFor(targetType).readValue(asInputStream(response.getPayloadBody().getContent()));
    }

    @Nullable
    private void validateResponse(AggregatedHttpResponse<HttpPayloadChunk> response) {
        if (response.getStatus() != OK) {
            throw  new IllegalArgumentException("Invalid response, HTTP status: " + response.getStatus());
        }

        if (!response.getHeaders().contains(CONTENT_TYPE, APPLICATION_JSON)) {
            final Iterator<? extends CharSequence> encodings = response.getHeaders().getAll(CONTENT_TYPE);
            StringBuilder builder = new StringBuilder();
            while (encodings.hasNext()) {
                CharSequence next = encodings.next();
                builder.append(next).append(", ");
            }
            throw  new IllegalArgumentException("Invalid response, Content type: " + builder.toString());
        }
    }
}

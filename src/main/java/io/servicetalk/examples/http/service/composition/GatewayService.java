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

import io.servicetalk.concurrent.api.Single;
import io.servicetalk.examples.http.service.composition.pojo.FullRecommendation;
import io.servicetalk.examples.http.service.composition.pojo.Metadata;
import io.servicetalk.examples.http.service.composition.pojo.Rating;
import io.servicetalk.examples.http.service.composition.pojo.Recommendation;
import io.servicetalk.examples.http.service.composition.pojo.User;
import io.servicetalk.http.api.AggregatedHttpClient;
import io.servicetalk.http.api.HttpClient;
import io.servicetalk.http.api.HttpPayloadChunk;
import io.servicetalk.http.api.HttpRequest;
import io.servicetalk.http.api.HttpRequests;
import io.servicetalk.http.api.HttpResponse;
import io.servicetalk.http.api.HttpSerializer;
import io.servicetalk.http.api.HttpService;
import io.servicetalk.transport.api.ConnectionContext;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static io.servicetalk.concurrent.api.Single.success;
import static io.servicetalk.examples.http.service.composition.AsyncUtil.zip;
import static io.servicetalk.http.api.AggregatedHttpRequests.newRequest;
import static io.servicetalk.http.api.HttpRequestMethods.GET;
import static io.servicetalk.http.api.HttpResponseStatuses.BAD_REQUEST;
import static io.servicetalk.http.api.HttpResponses.newResponse;

/**
 * This service provides an API that fetches recommendations in parallel and responds with a stream of
 * {@link FullRecommendation} objects as JSON.
 */
final class GatewayService extends HttpService {

    private static final Logger LOGGER = LoggerFactory.getLogger(GatewayService.class);

    private static final String USER_ID_QP_NAME = "userId";

    private final HttpClient recommendationsClient;
    private final AggregatedHttpClient metadataClient;
    private final AggregatedHttpClient ratingsClient;
    private final AggregatedHttpClient userClient;
    private final HttpSerializer serializer;

    GatewayService(final HttpClient recommendationsClient, final AggregatedHttpClient metadataClient,
                   final AggregatedHttpClient ratingsClient, final AggregatedHttpClient userClient,
                   HttpSerializer serializer) {
        this.recommendationsClient = recommendationsClient;
        this.metadataClient = metadataClient;
        this.ratingsClient = ratingsClient;
        this.userClient = userClient;
        this.serializer = serializer;
    }

    @Override
    public Single<HttpResponse<HttpPayloadChunk>> handle(final ConnectionContext ctx,
                                                         final HttpRequest<HttpPayloadChunk> request) {
        final String userId = request.parseQuery().get(USER_ID_QP_NAME);
        if (userId == null) {
            return success(newResponse(BAD_REQUEST));
        }

        return recommendationsClient.request(HttpRequests.newRequest(GET, "/recommendations/stream?userId=" + userId))
                // Post validation we get an HttpResponse with the correct status code but a payload of Buffer.
                // So, now we transform it into a HttpResponse<HttpPayloadChunk> with the correct JSON in it.
                .map(bufferResponse -> serializer.deserialize(bufferResponse, Recommendation.class))
                .map(recommendationResponse -> recommendationResponse.transformPayloadBody(payload -> payload.flatMapSingle(recommendation -> {
                        final Single<Metadata> metadata = metadataClient.request(newRequest(GET, "/metadata?entityId=" + recommendation.getEntityId()))
                                // Since HTTP payload is a buffer, we deserialize into Metadata.
                                .map(response -> serializer.deserialize(response, Metadata.class).getPayloadBody());

                        final Single<User> user = userClient.request(newRequest(GET, "/user?userId=" + recommendation.getEntityId()))
                                // Since HTTP payload is a buffer, we deserialize into User.
                                .map(response -> serializer.deserialize(response, User.class).getPayloadBody());

                        final Single<Rating> rating = ratingsClient.request(newRequest(GET, "/rating?entityId=" + recommendation.getEntityId()))
                                // Since HTTP payload is a buffer, we deserialize into Rating.
                                .map(response -> serializer.deserialize(response, Rating.class).getPayloadBody())
                                // We consider ratings to be a non-critical data and hence we substitute the response
                                // with a static "unavailable" rating when the rating service is unavailable or provides
                                // a bad response. This is typically referred to as a "fallback".
                                .onErrorResume(cause -> {
                                    LOGGER.error("Error querying ratings service. Ignoring and providing a fallback.", cause);
                                    return success(new Rating(recommendation.getEntityId(), -1));
                                });

                        // The below asynchronously queries metadata, user and rating backends and zips them into a single
                        // FullRecommendation instance.
                        // This helps us query multiple recommendations in parallel hence achieving better throughput as
                        // opposed to querying recommendation sequentially using the blocking API.
                        return zip(metadata, user, rating, FullRecommendation::new);
                    })))
                    // Serialize each FullRecommendation in a JSON object.
                    .map(fullRecommendationResponse -> serializer.serialize(fullRecommendationResponse, ctx.getBufferAllocator(), FullRecommendation.class));
    }
}

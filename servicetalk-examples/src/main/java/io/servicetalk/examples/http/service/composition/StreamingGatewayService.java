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

import io.servicetalk.concurrent.api.Publisher;
import io.servicetalk.concurrent.api.Single;
import io.servicetalk.examples.http.service.composition.pojo.FullRecommendation;
import io.servicetalk.examples.http.service.composition.pojo.Metadata;
import io.servicetalk.examples.http.service.composition.pojo.Rating;
import io.servicetalk.examples.http.service.composition.pojo.Recommendation;
import io.servicetalk.examples.http.service.composition.pojo.User;
import io.servicetalk.http.api.HttpClient;
import io.servicetalk.http.api.HttpSerializationProvider;
import io.servicetalk.http.api.HttpServiceContext;
import io.servicetalk.http.api.StreamingHttpClient;
import io.servicetalk.http.api.StreamingHttpRequest;
import io.servicetalk.http.api.StreamingHttpResponse;
import io.servicetalk.http.api.StreamingHttpResponseFactory;
import io.servicetalk.http.api.StreamingHttpService;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static io.servicetalk.concurrent.api.Single.success;
import static io.servicetalk.examples.http.service.composition.AsyncUtils.zip;

/**
 * This service provides an API that fetches recommendations in parallel and responds with a stream of
 * {@link FullRecommendation} objects as JSON.
 */
final class StreamingGatewayService extends StreamingHttpService {

    private static final Logger LOGGER = LoggerFactory.getLogger(StreamingGatewayService.class);

    private static final String USER_ID_QP_NAME = "userId";

    private final StreamingHttpClient recommendationsClient;
    private final HttpClient metadataClient;
    private final HttpClient ratingsClient;
    private final HttpClient userClient;
    private final HttpSerializationProvider serializers;

    StreamingGatewayService(final StreamingHttpClient recommendationsClient, final HttpClient metadataClient,
                            final HttpClient ratingsClient, final HttpClient userClient,
                            HttpSerializationProvider serializers) {
        this.recommendationsClient = recommendationsClient;
        this.metadataClient = metadataClient;
        this.ratingsClient = ratingsClient;
        this.userClient = userClient;
        this.serializers = serializers;
    }

    @Override
    public Single<StreamingHttpResponse> handle(final HttpServiceContext ctx, final StreamingHttpRequest request,
                                                final StreamingHttpResponseFactory responseFactory) {
        final String userId = request.queryParameter(USER_ID_QP_NAME);
        if (userId == null) {
            return success(responseFactory.badRequest());
        }

        return recommendationsClient.request(recommendationsClient.get("/recommendations/stream?userId=" + userId))
                .map(recommendations -> recommendations.transformPayloadBody(this::queryRecommendationDetails,
                        serializers.deserializerFor(Recommendation.class),
                        serializers.serializerFor(FullRecommendation.class)));
    }

    private Publisher<FullRecommendation> queryRecommendationDetails(Publisher<Recommendation> recommendations) {
        return recommendations.flatMapSingle(recommendation -> {
            Single<Metadata> metadata =
                    metadataClient.request(metadataClient.get("/metadata?entityId=" + recommendation.getEntityId()))
                            // Since HTTP payload is a buffer, we deserialize into Metadata.
                            .map(resp -> resp.payloadBody(serializers.deserializerFor(Metadata.class)));

            Single<User> user =
                    userClient.request(userClient.get("/user?userId=" + recommendation.getEntityId()))
                            // Since HTTP payload is a buffer, we deserialize into User.
                            .map(resp -> resp.payloadBody(serializers.deserializerFor(User.class)));

            Single<Rating> rating =
                    ratingsClient.request(ratingsClient.get("/rating?entityId=" + recommendation.getEntityId()))
                            // Since HTTP payload is a buffer, we deserialize into Rating.
                            .map(resp -> resp.payloadBody(serializers.deserializerFor(Rating.class)))
                            // We consider ratings to be a non-critical data and hence we substitute the response
                            // with a static "unavailable" rating when the rating service is unavailable or provides
                            // a bad response. This is typically referred to as a "fallback".
                            .recoverWith(cause -> {
                                LOGGER.error("Error querying ratings service. Ignoring and providing a fallback.",
                                        cause);
                                return success(new Rating(recommendation.getEntityId(), -1));
                            });

            // The below asynchronously queries metadata, user and rating backends and zips them into a single
            // FullRecommendation instance.
            // This helps us query multiple recommendations in parallel hence achieving better throughput as
            // opposed to querying recommendation sequentially using the blocking API.
            return zip(metadata, user, rating, FullRecommendation::new);
        });
    }
}

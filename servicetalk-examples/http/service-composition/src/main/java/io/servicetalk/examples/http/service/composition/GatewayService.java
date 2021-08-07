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
import io.servicetalk.http.api.HttpClient;
import io.servicetalk.http.api.HttpRequest;
import io.servicetalk.http.api.HttpResponse;
import io.servicetalk.http.api.HttpResponseFactory;
import io.servicetalk.http.api.HttpService;
import io.servicetalk.http.api.HttpServiceContext;

import java.util.ArrayList;
import java.util.List;

import static io.servicetalk.concurrent.api.Publisher.fromIterable;
import static io.servicetalk.concurrent.api.Single.succeeded;
import static io.servicetalk.concurrent.api.Single.zip;
import static io.servicetalk.examples.http.service.composition.SerializerUtils.ENTITY_ID_QP_NAME;
import static io.servicetalk.examples.http.service.composition.SerializerUtils.FULL_RECOMMEND_LIST_SERIALIZER;
import static io.servicetalk.examples.http.service.composition.SerializerUtils.METADATA_SERIALIZER;
import static io.servicetalk.examples.http.service.composition.SerializerUtils.RATING_SERIALIZER;
import static io.servicetalk.examples.http.service.composition.SerializerUtils.RECOMMEND_LIST_SERIALIZER;
import static io.servicetalk.examples.http.service.composition.SerializerUtils.USER_ID_QP_NAME;
import static io.servicetalk.examples.http.service.composition.SerializerUtils.USER_SERIALIZER;
import static io.servicetalk.examples.http.service.composition.backends.ErrorResponseGeneratingServiceFilter.SIMULATE_ERROR_QP_NAME;

/**
 * This service provides an API that fetches recommendations in parallel but provides an aggregated JSON array as a
 * response.
 */
final class GatewayService implements HttpService {
    private final HttpClient recommendationsClient;
    private final HttpClient metadataClient;
    private final HttpClient ratingsClient;
    private final HttpClient userClient;

    GatewayService(final HttpClient recommendationsClient, final HttpClient metadataClient,
                   final HttpClient ratingsClient, final HttpClient userClient) {
        this.recommendationsClient = recommendationsClient;
        this.metadataClient = metadataClient;
        this.ratingsClient = ratingsClient;
        this.userClient = userClient;
    }

    @Override
    public Single<HttpResponse> handle(final HttpServiceContext ctx,
                                       final HttpRequest request,
                                       final HttpResponseFactory responseFactory) {
        final String userId = request.queryParameter(USER_ID_QP_NAME);
        if (userId == null) {
            return succeeded(responseFactory.badRequest());
        }

        final Iterable<String> errorQpValues = () -> request.queryParametersIterator(SIMULATE_ERROR_QP_NAME);
        return recommendationsClient.request(recommendationsClient.get("/recommendations/aggregated")
                .addQueryParameter(USER_ID_QP_NAME, userId)
                .addQueryParameters(SIMULATE_ERROR_QP_NAME, errorQpValues))
                // Since HTTP payload is a buffer, we deserialize into List<Recommendation>>.
                .map(response -> response.payloadBody(RECOMMEND_LIST_SERIALIZER))
                .flatMap(recommendations -> queryRecommendationDetails(recommendations, errorQpValues))
                .map(fullRecommendations -> responseFactory.ok()
                        .payloadBody(fullRecommendations, FULL_RECOMMEND_LIST_SERIALIZER));
    }

    private Single<List<FullRecommendation>> queryRecommendationDetails(List<Recommendation> recommendations,
                                                                        Iterable<String> errorQpValues) {
        // Recommendations are a List and we want to query details for each recommendation in parallel.
        // Turning the List into a Publisher helps us use relevant operators to do so.
        return fromIterable(recommendations)
                .flatMapMergeSingle(recommendation -> {
                    Single<Metadata> metadata =
                            metadataClient.request(metadataClient.get("/metadata")
                                    .addQueryParameter(ENTITY_ID_QP_NAME, recommendation.getEntityId())
                                    .addQueryParameters(SIMULATE_ERROR_QP_NAME, errorQpValues))
                                    // Since HTTP payload is a buffer, we deserialize into Metadata.
                                    .map(response -> response.payloadBody(METADATA_SERIALIZER));

                    Single<User> user =
                            userClient.request(userClient.get("/user")
                                    .addQueryParameter(USER_ID_QP_NAME, recommendation.getEntityId())
                                    .addQueryParameters(SIMULATE_ERROR_QP_NAME, errorQpValues))
                                    // Since HTTP payload is a buffer, we deserialize into User.
                                    .map(response -> response.payloadBody(USER_SERIALIZER));

                    Single<Rating> rating =
                            ratingsClient.request(ratingsClient.get("/rating")
                                    .addQueryParameter(ENTITY_ID_QP_NAME, recommendation.getEntityId())
                                    .addQueryParameters(SIMULATE_ERROR_QP_NAME, errorQpValues))
                                    // Since HTTP payload is a buffer, we deserialize into Rating.
                                    .map(response -> response.payloadBody(RATING_SERIALIZER))
                                    // We consider ratings to be a non-critical data and hence we substitute the
                                    // response with a static "unavailable" rating when the rating service is
                                    // unavailable or provides a bad response. This is typically referred to as a
                                    // "fallback".
                                    .onErrorReturn(cause -> new Rating(recommendation.getEntityId(), -1));

                    // The below asynchronously queries metadata, user and rating backends and zips them into a single
                    // FullRecommendation instance.
                    // This helps us query multiple recommendations in parallel hence achieving better throughput as
                    // opposed to querying recommendation sequentially using the blocking API.
                    return zip(metadata, user, rating, FullRecommendation::new);
                })
                // FullRecommendation objects are generated asynchronously and we are responding with a single JSON
                // array. Thus, we collect the asynchronously generated FullRecommendation objects into a single
                // List which is be converted to a single JSON array.
                .collect(ArrayList::new, (list, fullRecommendation) -> {
                    list.add(fullRecommendation);
                    return list;
                });
    }
}

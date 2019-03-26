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
import io.servicetalk.http.api.HttpSerializationProvider;
import io.servicetalk.http.api.HttpService;
import io.servicetalk.http.api.HttpServiceContext;
import io.servicetalk.serialization.api.TypeHolder;

import java.util.ArrayList;
import java.util.List;

import static io.servicetalk.concurrent.api.Publisher.from;
import static io.servicetalk.concurrent.api.Single.success;
import static io.servicetalk.examples.http.service.composition.AsyncUtils.zip;

/**
 * This service provides an API that fetches recommendations in parallel but provides an aggregated JSON array as a
 * response.
 */
final class GatewayService implements HttpService {

    private static final TypeHolder<List<Recommendation>> typeOfRecommendation =
            new TypeHolder<List<Recommendation>>() { };
    private static final TypeHolder<List<FullRecommendation>> typeOfFullRecommendation =
            new TypeHolder<List<FullRecommendation>>() { };

    private static final String USER_ID_QP_NAME = "userId";

    private final HttpClient recommendationsClient;
    private final HttpClient metadataClient;
    private final HttpClient ratingsClient;
    private final HttpClient userClient;
    private final HttpSerializationProvider serializers;

    GatewayService(final HttpClient recommendationsClient, final HttpClient metadataClient,
                   final HttpClient ratingsClient, final HttpClient userClient,
                   HttpSerializationProvider serializers) {
        this.recommendationsClient = recommendationsClient;
        this.metadataClient = metadataClient;
        this.ratingsClient = ratingsClient;
        this.userClient = userClient;
        this.serializers = serializers;
    }

    @Override
    public Single<HttpResponse> handle(final HttpServiceContext ctx,
                                       final HttpRequest request,
                                       final HttpResponseFactory responseFactory) {
        final String userId = request.queryParameter(USER_ID_QP_NAME);
        if (userId == null) {
            return success(responseFactory.badRequest());
        }

        return recommendationsClient.request(recommendationsClient.get("/recommendations/aggregated?userId=" + userId))
                // Since HTTP payload is a buffer, we deserialize into List<Recommendation>>.
                .map(response -> response.payloadBody(serializers.deserializerFor(typeOfRecommendation)))
                .flatMap(this::queryRecommendationDetails)
                .map(fullRecommendations -> responseFactory.ok()
                        .payloadBody(fullRecommendations, serializers.serializerFor(typeOfFullRecommendation)));
    }

    private Single<List<FullRecommendation>> queryRecommendationDetails(List<Recommendation> recommendations) {
        // Recommendations are a List and we want to query details for each recommendation in parallel.
        // Turning the List into a Publisher helps us use relevant operators to do so.
        return from(recommendations)
                .flatMapMergeSingle(reco -> {
                    Single<Metadata> metadata =
                            metadataClient.request(metadataClient.get("/metadata?entityId=" + reco.getEntityId()))
                                    // Since HTTP payload is a buffer, we deserialize into Metadata.
                                    .map(response -> response.payloadBody(serializers.deserializerFor(Metadata.class)));

                    Single<User> user =
                            userClient.request(userClient.get("/user?userId=" + reco.getEntityId()))
                                    // Since HTTP payload is a buffer, we deserialize into User.
                                    .map(response -> response.payloadBody(serializers.deserializerFor(User.class)));

                    Single<Rating> rating =
                            ratingsClient.request(ratingsClient.get("/rating?entityId=" + reco.getEntityId()))
                                    // Since HTTP payload is a buffer, we deserialize into Rating.
                                    .map(response -> response.payloadBody(serializers.deserializerFor(Rating.class)))
                                    // We consider ratings to be a non-critical data and hence we substitute the
                                    // response with a static "unavailable" rating when the rating service is
                                    // unavailable or provides a bad response. This is typically referred to as a
                                    // "fallback".
                                    .recoverWith(cause -> success(new Rating(reco.getEntityId(), -1)));

                    // The below asynchronously queries metadata, user and rating backends and zips them into a single
                    // FullRecommendation instance.
                    // This helps us query multiple recommendations in parallel hence achieving better throughput as
                    // opposed to querying recommendation sequentially using the blocking API.
                    return zip(metadata, user, rating, FullRecommendation::new);
                })
                // FullRecommendation objects are generated asynchronously and we are responding with a single JSON
                // array. Thus, we reduce the asynchronously generated FullRecommendation objects into a single
                // List which is be converted to a single JSON array.
                .reduce(ArrayList::new, (list, fullRecommendation) -> {
                    list.add(fullRecommendation);
                    return list;
                });
    }
}

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
import io.servicetalk.http.api.AggregatedHttpClient;
import io.servicetalk.http.api.AggregatedHttpRequest;
import io.servicetalk.http.api.AggregatedHttpResponse;
import io.servicetalk.http.api.AggregatedHttpService;
import io.servicetalk.http.api.HttpPayloadChunk;
import io.servicetalk.http.api.HttpSerializer;
import io.servicetalk.serialization.api.TypeHolder;
import io.servicetalk.transport.api.ConnectionContext;

import java.util.ArrayList;
import java.util.List;

import static io.servicetalk.concurrent.api.Single.success;
import static io.servicetalk.examples.http.service.composition.AsyncUtil.zip;
import static io.servicetalk.http.api.AggregatedHttpRequests.newRequest;
import static io.servicetalk.http.api.AggregatedHttpResponses.newResponse;
import static io.servicetalk.http.api.HttpRequestMethods.GET;
import static io.servicetalk.http.api.HttpResponseStatuses.BAD_REQUEST;
import static io.servicetalk.http.api.HttpResponseStatuses.OK;

/**
 * This service provides an API that fetches recommendations in parallel but provides an aggregated JSON array as a
 * response.
 */
final class AggregatedGatewayService extends AggregatedHttpService {

    private static final TypeHolder<List<Recommendation>> typeOfRecommendation = new TypeHolder<List<Recommendation>>(){};

    private static final String USER_ID_QP_NAME = "userId";

    private final AggregatedHttpClient recommendationsClient;
    private final AggregatedHttpClient metadataClient;
    private final AggregatedHttpClient ratingsClient;
    private final AggregatedHttpClient userClient;
    private final HttpSerializer serializer;

    AggregatedGatewayService(final AggregatedHttpClient recommendationsClient, final AggregatedHttpClient metadataClient,
                             final AggregatedHttpClient ratingsClient, final AggregatedHttpClient userClient,
                             HttpSerializer serializer) {
        this.recommendationsClient = recommendationsClient;
        this.metadataClient = metadataClient;
        this.ratingsClient = ratingsClient;
        this.userClient = userClient;
        this.serializer = serializer;
    }

    @Override
    public Single<AggregatedHttpResponse<HttpPayloadChunk>> handle(final ConnectionContext ctx,
                                                                   final AggregatedHttpRequest<HttpPayloadChunk> request) {
        final String userId = request.parseQuery().get(USER_ID_QP_NAME);
        if (userId == null) {
            return success(newResponse(BAD_REQUEST));
        }

        return recommendationsClient.request(newRequest(GET, "/recommendations/aggregated?userId=" + userId))
                // Since HTTP payload is a buffer, we deserialize into List<Recommendation>>.
                .map(response -> serializer.deserialize(response, typeOfRecommendation).getPayloadBody())
                // Recommendations are a List and we want to query details for each recommendation in parallel.
                // Turning the List into a Publisher helps us use relevant operators to do so.
                .flatMapPublisher(Publisher::from)
                .flatMapSingle(recommendation -> {
                    Single<Metadata> metadata = metadataClient.request(newRequest(GET, "/metadata?entityId=" + recommendation.getEntityId()))
                            // Since HTTP payload is a buffer, we deserialize into Metadata.
                            .map(response -> serializer.deserialize(response, Metadata.class).getPayloadBody());

                    Single<User> user = userClient.request(newRequest(GET, "/user?userId=" + recommendation.getEntityId()))
                            // Since HTTP payload is a buffer, we deserialize into User.
                            .map(response -> serializer.deserialize(response, User.class).getPayloadBody());

                    Single<Rating> rating = ratingsClient.request(newRequest(GET, "/rating?entityId=" + recommendation.getEntityId()))
                            // Since HTTP payload is a buffer, we deserialize into Rating.
                            .map(response -> serializer.deserialize(response, Rating.class).getPayloadBody())
                            // We consider ratings to be a non-critical data and hence we substitute the response
                            // with a static "unavailable" rating when the rating service is unavailable or provides
                            // a bad response. This is typically referred to as a "fallback".
                            .onErrorResume(cause -> success(new Rating(recommendation.getEntityId(), -1)));

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
                })
                // Convert the payload to a FullHttpResponse.
                .map(allRecosJson -> serializer.serialize(newResponse(OK, allRecosJson), ctx.getBufferAllocator()));
    }
}

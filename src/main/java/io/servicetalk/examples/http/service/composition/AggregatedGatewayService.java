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
import io.servicetalk.transport.api.ConnectionContext;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.List;

import static io.servicetalk.buffer.api.Buffer.asInputStream;
import static io.servicetalk.concurrent.api.Single.error;
import static io.servicetalk.concurrent.api.Single.success;
import static io.servicetalk.data.jackson.JacksonSerializers.serialize;
import static io.servicetalk.examples.http.service.composition.AsyncUtil.zip;
import static io.servicetalk.http.api.AggregatedHttpRequests.newRequest;
import static io.servicetalk.http.api.AggregatedHttpResponses.newResponse;
import static io.servicetalk.http.api.HttpHeaderNames.CONTENT_TYPE;
import static io.servicetalk.http.api.HttpHeaderValues.APPLICATION_JSON;
import static io.servicetalk.http.api.HttpRequestMethods.GET;
import static io.servicetalk.http.api.HttpResponseStatuses.BAD_REQUEST;
import static io.servicetalk.http.api.HttpResponseStatuses.OK;
import static java.util.Spliterators.spliteratorUnknownSize;
import static java.util.stream.Collectors.joining;
import static java.util.stream.StreamSupport.stream;

/**
 * This service provides an API that fetches recommendations in parallel but provides an aggregated JSON array as a
 * response.
 */
final class AggregatedGatewayService extends AggregatedHttpService {

    private static final TypeReference<List<Recommendation>> typeOfRecommendation = new TypeReference<List<Recommendation>>(){};

    private static final String USER_ID_QP_NAME = "userId";

    private final AggregatedHttpClient recommendationsClient;
    private final AggregatedHttpClient metadataClient;
    private final AggregatedHttpClient ratingsClient;
    private final AggregatedHttpClient userClient;
    private final ObjectMapper objectMapper;

    AggregatedGatewayService(final AggregatedHttpClient recommendationsClient, final AggregatedHttpClient metadataClient,
                             final AggregatedHttpClient ratingsClient, final AggregatedHttpClient userClient,
                             ObjectMapper objectMapper) {
        this.recommendationsClient = recommendationsClient;
        this.metadataClient = metadataClient;
        this.ratingsClient = ratingsClient;
        this.userClient = userClient;
        this.objectMapper = objectMapper;
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
                .flatMap(this::deserializeRecommendationPayload)
                // Recommendations are a List and we want to query details for each recommendation in parallel.
                // Turning the List into a Publisher helps us use relevant operators to do so.
                .flatMapPublisher(Publisher::from)
                .flatMapSingle(recommendation -> {
                    Single<Metadata> metadata = metadataClient.request(newRequest(GET, "/metadata?entityId=" + recommendation.getEntityId()))
                            // Since HTTP payload is a buffer, we deserialize into Metadata.
                            .flatMap(response -> deserializePayload(response, Metadata.class));

                    Single<User> user = userClient.request(newRequest(GET, "/user?userId=" + recommendation.getEntityId()))
                            // Since HTTP payload is a buffer, we deserialize into User.
                            .flatMap(response -> deserializePayload(response, User.class));

                    Single<Rating> rating = ratingsClient.request(newRequest(GET, "/rating?entityId=" + recommendation.getEntityId()))
                            // Since HTTP payload is a buffer, we deserialize into Rating.
                            .flatMap(response -> deserializePayload(response, Rating.class))
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
                // Serialize the entire List into a JSON array.
                .map(allRecos -> serialize(objectMapper.writer(), allRecos, ctx.getBufferAllocator()))
                // Convert the payload to a FullHttpResponse.
                .map(allRecosJson -> newResponse(OK, allRecosJson));
    }

    private Single<List<Recommendation>> deserializeRecommendationPayload(AggregatedHttpResponse<HttpPayloadChunk> response) {
        try {
            validateResponse(response);
            return success(objectMapper.readerFor(typeOfRecommendation).readValue(asInputStream(response.getPayloadBody().getContent())));
        } catch (Exception e) {
            return error(e);
        }
    }

    private <T> Single<T> deserializePayload(AggregatedHttpResponse<HttpPayloadChunk> response, Class<T> targetType) {
        try {
            validateResponse(response);
            return success(objectMapper.readerFor(targetType).readValue(asInputStream(response.getPayloadBody().getContent())));
        } catch (Exception e) {
            return error(e);
        }
    }

    private static void validateResponse(final AggregatedHttpResponse<HttpPayloadChunk> response) {
        if (response.getStatus() != OK) {
            throw new IllegalArgumentException("Invalid response, HTTP status: " + response.getStatus());
        }

        if (!response.getHeaders().contains(CONTENT_TYPE, APPLICATION_JSON)) {
            throw new IllegalArgumentException("Invalid response, content type: " +
                    stream(spliteratorUnknownSize(response.getHeaders().getAll(CONTENT_TYPE), 0), false)
                            .collect(joining(", ")));
        }
    }
}

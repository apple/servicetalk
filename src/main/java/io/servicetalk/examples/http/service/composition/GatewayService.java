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
import io.servicetalk.http.api.AggregatedHttpResponse;
import io.servicetalk.http.api.HttpClient;
import io.servicetalk.http.api.HttpPayloadChunk;
import io.servicetalk.http.api.HttpPayloadChunks;
import io.servicetalk.http.api.HttpRequest;
import io.servicetalk.http.api.HttpRequests;
import io.servicetalk.http.api.HttpResponse;
import io.servicetalk.http.api.HttpResponseMetaData;
import io.servicetalk.http.api.HttpService;
import io.servicetalk.transport.api.ConnectionContext;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;

import static io.servicetalk.buffer.api.Buffer.asInputStream;
import static io.servicetalk.concurrent.api.Single.error;
import static io.servicetalk.concurrent.api.Single.success;
import static io.servicetalk.data.jackson.JacksonSerializers.deserializer;
import static io.servicetalk.data.jackson.JacksonSerializers.serializer;
import static io.servicetalk.examples.http.service.composition.AsyncUtil.zip;
import static io.servicetalk.http.api.AggregatedHttpRequests.newRequest;
import static io.servicetalk.http.api.HttpHeaderNames.CONTENT_TYPE;
import static io.servicetalk.http.api.HttpHeaderValues.APPLICATION_JSON;
import static io.servicetalk.http.api.HttpRequestMethods.GET;
import static io.servicetalk.http.api.HttpResponseStatuses.BAD_REQUEST;
import static io.servicetalk.http.api.HttpResponseStatuses.INTERNAL_SERVER_ERROR;
import static io.servicetalk.http.api.HttpResponseStatuses.OK;
import static io.servicetalk.http.api.HttpResponses.newResponse;
import static java.util.Spliterators.spliteratorUnknownSize;
import static java.util.stream.Collectors.joining;
import static java.util.stream.StreamSupport.stream;

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
    private final ObjectMapper objectMapper;

    GatewayService(final HttpClient recommendationsClient, final AggregatedHttpClient metadataClient,
                   final AggregatedHttpClient ratingsClient, final AggregatedHttpClient userClient,
                   ObjectMapper objectMapper) {
        this.recommendationsClient = recommendationsClient;
        this.metadataClient = metadataClient;
        this.ratingsClient = ratingsClient;
        this.userClient = userClient;
        this.objectMapper = objectMapper;
    }

    @Override
    public Single<HttpResponse<HttpPayloadChunk>> handle(final ConnectionContext ctx,
                                                         final HttpRequest<HttpPayloadChunk> request) {
        final String userId = request.parseQuery().get(USER_ID_QP_NAME);
        if (userId == null) {
            return success(newResponse(BAD_REQUEST));
        }

        return recommendationsClient.request(HttpRequests.newRequest(GET, "/recommendations/stream?userId=" + userId))
                .flatMap(this::validateRecommendationResponse)
                // Post validation we get an HttpResponse with the correct status code but a payload of Buffer.
                // So, now we transform it into a HttpResponse<HttpPayloadChunk> with the correct JSON in it.
                .map(bufferResponse ->
                        bufferResponse.transformPayloadBody(payload -> deserializer(objectMapper, Recommendation.class)
                                .apply(payload.map(HttpPayloadChunk::getContent))
                    .flatMapSingle(recommendation -> {
                        final Single<Metadata> metadata = metadataClient.request(newRequest(GET, "/metadata?entityId=" + recommendation.getEntityId()))
                                // Since HTTP payload is a buffer, we deserialize into Metadata.
                                .flatMap(response -> deserializePayload(response, Metadata.class));

                        final Single<User> user = userClient.request(newRequest(GET, "/user?userId=" + recommendation.getEntityId()))
                                // Since HTTP payload is a buffer, we deserialize into User.
                                .flatMap(response -> deserializePayload(response, User.class));

                        final Single<Rating> rating = ratingsClient.request(newRequest(GET, "/rating?entityId=" + recommendation.getEntityId()))
                                // Since HTTP payload is a buffer, we deserialize into Rating.
                                .flatMap(response -> deserializePayload(response, Rating.class))
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
                    })
                    // Serialize each FullRecommendation in a JSON object.
                    .map(serializer(objectMapper, FullRecommendation.class, ctx.getBufferAllocator()))
                    .map(HttpPayloadChunks::newPayloadChunk))
                );
    }

    private Single<HttpResponse<HttpPayloadChunk>> validateRecommendationResponse(HttpResponse<HttpPayloadChunk> recommendationResponse) {
        try {
            if (recommendationResponse.getStatus() != OK) {
                return success(newResponse(recommendationResponse.getStatus()));
            }
            validateContentType(recommendationResponse);
            return success(newResponse(OK, recommendationResponse.getPayloadBody()));
        } catch (Exception e) {
            return success(newResponse(INTERNAL_SERVER_ERROR));
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

    @Nullable
    private void validateResponse(final AggregatedHttpResponse<HttpPayloadChunk> response) {
        if (response.getStatus() != OK) {
            throw new IllegalArgumentException("Invalid response, HTTP status: " + response.getStatus());
        }

        validateContentType(response);
    }

    private void validateContentType(final HttpResponseMetaData response) {
        if (!response.getHeaders().contains(CONTENT_TYPE, APPLICATION_JSON)) {
            throw new IllegalArgumentException("Invalid response, content type: " +
                    stream(spliteratorUnknownSize(response.getHeaders().getAll(CONTENT_TYPE), 0), false)
                            .collect(joining(", ")));
        }
    }
}

/*
 * Copyright © 2019 Apple Inc. and the ServiceTalk project authors
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
package io.servicetalk.http.api;

import io.servicetalk.http.api.StreamingHttpClientToBlockingHttpClient.ReservedStreamingHttpConnectionToReservedBlockingHttpConnection;
import io.servicetalk.http.api.StreamingHttpClientToBlockingStreamingHttpClient.ReservedStreamingHttpConnectionToBlockingStreaming;
import io.servicetalk.http.api.StreamingHttpClientToHttpClient.ReservedStreamingHttpConnectionToReservedHttpConnection;

import static io.servicetalk.http.api.HttpContextKeys.HTTP_EXECUTION_STRATEGY_KEY;

/**
 * Conversion routines to {@link StreamingHttpService}.
 */
public final class HttpApiConversions {

    private HttpApiConversions() {
        // no instances
    }

    /**
     * Convert from {@link ReservedStreamingHttpConnection} to {@link ReservedHttpConnection}.
     *
     * @param original {@link ReservedStreamingHttpConnection} to convert.
     * @param influencer {@link HttpExecutionStrategyInfluencer} to use to derive the strategy of the returned
     * {@link ReservedHttpConnection}
     * @return The conversion result.
     */
    public static ReservedHttpConnection toReservedConnection(ReservedStreamingHttpConnection original,
                                                              HttpExecutionStrategyInfluencer influencer) {
        return new ReservedStreamingHttpConnectionToReservedHttpConnection(original, influencer);
    }

    /**
     * Convert from {@link ReservedStreamingHttpConnection} to {@link ReservedBlockingHttpConnection}.
     *
     * @param original {@link ReservedStreamingHttpConnection} to convert.
     * @param influencer {@link HttpExecutionStrategyInfluencer} to use to derive the strategy of the returned
     * {@link ReservedBlockingHttpConnection}
     * @return The conversion result.
     */
    public static ReservedBlockingHttpConnection toReservedBlockingConnection(
            ReservedStreamingHttpConnection original, HttpExecutionStrategyInfluencer influencer) {
        return new ReservedStreamingHttpConnectionToReservedBlockingHttpConnection(original, influencer);
    }

    /**
     * Convert from {@link ReservedStreamingHttpConnection} to {@link ReservedBlockingStreamingHttpConnection}.
     *
     * @param original {@link ReservedStreamingHttpConnection} to convert.
     * @param influencer {@link HttpExecutionStrategyInfluencer} to use to derive the strategy of the returned
     * {@link ReservedBlockingStreamingHttpConnection}
     * @return The conversion result.
     */
    public static ReservedBlockingStreamingHttpConnection toReservedBlockingStreamingConnection(
            ReservedStreamingHttpConnection original, HttpExecutionStrategyInfluencer influencer) {
        return new ReservedStreamingHttpConnectionToBlockingStreaming(original, influencer);
    }

    /**
     * Convert from {@link StreamingHttpConnection} to {@link HttpConnection}.
     *
     * @param original {@link StreamingHttpConnection} to convert.
     * @param influencer {@link HttpExecutionStrategyInfluencer} to use to derive the strategy of the returned
     * {@link HttpConnection}
     * @return The conversion result.
     */
    public static HttpConnection toConnection(StreamingHttpConnection original,
                                              HttpExecutionStrategyInfluencer influencer) {
        return new StreamingHttpConnectionToHttpConnection(original, influencer);
    }

    /**
     * Convert from {@link StreamingHttpConnection} to {@link BlockingHttpConnection}.
     *
     * @param original {@link StreamingHttpConnection} to convert.
     * @param influencer {@link HttpExecutionStrategyInfluencer} to use to derive the strategy of the returned
     * {@link BlockingHttpConnection}
     * @return The conversion result.
     */
    public static BlockingHttpConnection toBlockingConnection(
            StreamingHttpConnection original, HttpExecutionStrategyInfluencer influencer) {
        return new StreamingHttpConnectionToBlockingHttpConnection(original, influencer);
    }

    /**
     * Convert from {@link StreamingHttpConnection} to {@link BlockingStreamingHttpConnection}.
     *
     * @param original {@link StreamingHttpConnection} to convert.
     * @param influencer {@link HttpExecutionStrategyInfluencer} to use to derive the strategy of the returned
     * {@link BlockingStreamingHttpConnection}
     * @return The conversion result.
     */
    public static BlockingStreamingHttpConnection toBlockingStreamingConnection(
            StreamingHttpConnection original, HttpExecutionStrategyInfluencer influencer) {
        return new StreamingHttpConnectionToBlockingStreamingHttpConnection(original, influencer);
    }

    /**
     * Convert from {@link StreamingHttpClient} to {@link HttpClient}.
     *
     * @param original {@link StreamingHttpClient} to convert.
     * @param influencer {@link HttpExecutionStrategyInfluencer} to use to derive the strategy of the returned
     * {@link HttpClient}
     * @return The conversion result.
     */
    public static HttpClient toClient(StreamingHttpClient original, HttpExecutionStrategyInfluencer influencer) {
        return new StreamingHttpClientToHttpClient(original, influencer);
    }

    /**
     * Convert from {@link StreamingHttpClient} to {@link BlockingHttpClient}.
     *
     * @param original {@link StreamingHttpClient} to convert.
     * @param influencer {@link HttpExecutionStrategyInfluencer} to use to derive the strategy of the returned
     * {@link BlockingHttpClient}
     * @return The conversion result.
     */
    public static BlockingHttpClient toBlockingClient(StreamingHttpClient original,
                                                      HttpExecutionStrategyInfluencer influencer) {
        return new StreamingHttpClientToBlockingHttpClient(original, influencer);
    }

    /**
     * Convert from {@link StreamingHttpClient} to {@link BlockingStreamingHttpClient}.
     *
     * @param original {@link StreamingHttpClient} to convert.
     * @param influencer {@link HttpExecutionStrategyInfluencer} to use to derive the strategy of the returned
     * {@link BlockingStreamingHttpClient}
     * @return The conversion result.
     */
    public static BlockingStreamingHttpClient toBlockingStreamingClient(StreamingHttpClient original,
                                                                        HttpExecutionStrategyInfluencer influencer) {
        return new StreamingHttpClientToBlockingStreamingHttpClient(original, influencer);
    }

    /**
     * Convert from a {@link HttpService} to a {@link StreamingHttpService}.
     *
     * @param service The {@link HttpService} to convert.
     * @param influencer {@link HttpExecutionStrategyInfluencer} to influence the strategy for invoking the resulting
     * {@link StreamingHttpService}.
     * @return {@link ServiceAdapterHolder} containing the service adapted to the streaming programming model.
     */
    public static ServiceAdapterHolder toStreamingHttpService(HttpService service,
                                                              HttpExecutionStrategyInfluencer influencer) {
        return new ServiceToStreamingService(service, influencer);
    }

    /**
     * Convert from a {@link BlockingStreamingHttpService} to a {@link StreamingHttpService}.
     *
     * @param service The {@link BlockingStreamingHttpService} to convert.
     * @param influencer {@link HttpExecutionStrategyInfluencer} to influence the strategy for invoking the resulting
     * {@link StreamingHttpService}.
     * @return {@link ServiceAdapterHolder} containing the service adapted to the streaming programming model.
     */
    public static ServiceAdapterHolder toStreamingHttpService(BlockingStreamingHttpService service,
                                                              HttpExecutionStrategyInfluencer influencer) {
        return new BlockingStreamingToStreamingService(service, influencer);
    }

    /**
     * Convert from a {@link BlockingStreamingHttpService} to a {@link StreamingHttpService}.
     *
     * @param service The {@link BlockingStreamingHttpService} to convert.
     * @param influencer {@link HttpExecutionStrategyInfluencer} to influence the strategy for invoking the resulting
     * {@link StreamingHttpService}.
     * @return {@link ServiceAdapterHolder} containing the service adapted to the streaming programming model.
     */
    public static ServiceAdapterHolder toStreamingHttpService(BlockingHttpService service,
                                                              HttpExecutionStrategyInfluencer influencer) {
        return new BlockingToStreamingService(service, influencer);
    }

    /**
     * Checks whether a request/response payload body is empty.
     *
     * @param metadata The request/response to check.
     * @return {@code true} is the request/response payload body is empty, {@code false} otherwise.
     */
    public static boolean isPayloadEmpty(HttpMetaData metadata) {
        return metadata instanceof PayloadInfo && ((PayloadInfo) metadata).isEmpty();
    }

    /**
     * Checks whether a request/response payload is safe to aggregate, which may allow for writing a `content-length`
     * header.
     *
     * @param metadata The request/response to check.
     * @return {@code true} is the request/response payload is safe to aggregate, {@code false} otherwise.
     */
    public static boolean isSafeToAggregate(HttpMetaData metadata) {
        return metadata instanceof PayloadInfo && ((PayloadInfo) metadata).isSafeToAggregate();
    }

    /**
     * Checks whether a request/response payload may have trailers.
     *
     * @param metadata The request/response to check.
     * @return {@code true} is the request/response payload may have trailers, {@code false} otherwise.
     */
    public static boolean mayHaveTrailers(HttpMetaData metadata) {
        return metadata instanceof PayloadInfo && ((PayloadInfo) metadata).mayHaveTrailers();
    }

    /**
     * A holder for {@link StreamingHttpService} that adapts another {@code service} to the streaming programming model.
     */
    public interface ServiceAdapterHolder {

        /**
         * {@link StreamingHttpService} that adapts another {@code service} to the streaming programming model. This
         * {@link StreamingHttpService} should only be invoked using the {@link HttpExecutionStrategy} returned from
         * {@link #serviceInvocationStrategy()}.
         *
         * @return {@link StreamingHttpService} that adapts another {@code service} to the streaming programming model.
         */
        StreamingHttpService adaptor();

        /**
         * {@link HttpExecutionStrategy} that should be used to invoke the service returned by {@link #adaptor()}.
         *
         * @return {@link HttpExecutionStrategy} for this adapter.
         */
        HttpExecutionStrategy serviceInvocationStrategy();
    }

    /**
     * Convert from a {@link StreamingHttpService} to a {@link BlockingHttpService}.
     *
     * @param service The {@link StreamingHttpService} to convert.
     * @return The conversion result.
     */
    public static BlockingHttpService toBlockingHttpService(StreamingHttpService service) {
        return new StreamingHttpServiceToBlockingHttpService(service);
    }

    /**
     * Convert from a {@link StreamingHttpService} to a {@link HttpService}.
     *
     * @param service The {@link StreamingHttpService} to convert.
     * @return The conversion result.
     */
    public static HttpService toHttpService(StreamingHttpService service) {
        return new StreamingHttpServiceToHttpService(service);
    }

    /**
     * Convert from a {@link StreamingHttpService} to a {@link BlockingStreamingHttpService}.
     *
     * @param service The {@link StreamingHttpService} to convert.
     * @return The conversion result.
     */
    public static BlockingStreamingHttpService toBlockingStreamingHttpService(StreamingHttpService service) {
        return new StreamingHttpServiceToBlockingStreamingHttpService(service);
    }

    static HttpExecutionStrategy requestStrategy(HttpRequestMetaData metaData, HttpExecutionStrategy fallback) {
        final HttpExecutionStrategy strategy = metaData.context().get(HTTP_EXECUTION_STRATEGY_KEY);
        return strategy != null ? strategy : fallback;
    }
}

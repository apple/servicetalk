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
     * @deprecated Use overload with {@link HttpExecutionStrategy} rather than {@link HttpExecutionStrategyInfluencer}
     */
    // FIXME: 0.43 - remove deprecated method
    @Deprecated
    public static ReservedHttpConnection toReservedConnection(ReservedStreamingHttpConnection original,
                                                              HttpExecutionStrategyInfluencer influencer) {
        return toReservedConnection(original, influencer.requiredOffloads());
    }

    /**
     * Convert from {@link ReservedStreamingHttpConnection} to {@link ReservedHttpConnection}.
     *
     * @param original {@link ReservedStreamingHttpConnection} to convert.
     * @param strategy required strategy for the service when invoking the resulting {@link ReservedHttpConnection}
     * @return The conversion result.
     */
    public static ReservedHttpConnection toReservedConnection(ReservedStreamingHttpConnection original,
                                                              HttpExecutionStrategy strategy) {
        return new ReservedStreamingHttpConnectionToReservedHttpConnection(original, strategy);
    }

    /**
     * Convert from {@link ReservedStreamingHttpConnection} to {@link ReservedBlockingHttpConnection}.
     *
     * @param original {@link ReservedStreamingHttpConnection} to convert.
     * @param influencer {@link HttpExecutionStrategyInfluencer} to use to derive the strategy of the returned
     * {@link ReservedBlockingHttpConnection}
     * @return The conversion result.
     * @deprecated Use overload with {@link HttpExecutionStrategy} rather than {@link HttpExecutionStrategyInfluencer}
     */
    // FIXME: 0.43 - remove deprecated method
    @Deprecated
    public static ReservedBlockingHttpConnection toReservedBlockingConnection(
            ReservedStreamingHttpConnection original, HttpExecutionStrategyInfluencer influencer) {
        return toReservedBlockingConnection(original, influencer.requiredOffloads());
    }

    /**
     * Convert from {@link ReservedStreamingHttpConnection} to {@link ReservedBlockingHttpConnection}.
     *
     * @param original {@link ReservedStreamingHttpConnection} to convert.
     * @param strategy required strategy for the service when invoking the resulting
     * {@link ReservedBlockingHttpConnection}
     * @return The conversion result.
     */
    public static ReservedBlockingHttpConnection toReservedBlockingConnection(
            ReservedStreamingHttpConnection original, HttpExecutionStrategy strategy) {
        return new ReservedStreamingHttpConnectionToReservedBlockingHttpConnection(original, strategy);
    }

    /**
     * Convert from {@link ReservedStreamingHttpConnection} to {@link ReservedBlockingStreamingHttpConnection}.
     *
     * @param original {@link ReservedStreamingHttpConnection} to convert.
     * @param influencer {@link HttpExecutionStrategyInfluencer} to use to derive the strategy of the returned
     * {@link ReservedBlockingStreamingHttpConnection}
     * @return The conversion result.
     * @deprecated Use overload with {@link HttpExecutionStrategy} rather than {@link HttpExecutionStrategyInfluencer}
     */
    // FIXME: 0.43 - remove deprecated method
    @Deprecated
    public static ReservedBlockingStreamingHttpConnection toReservedBlockingStreamingConnection(
            ReservedStreamingHttpConnection original, HttpExecutionStrategyInfluencer influencer) {
        return toReservedBlockingStreamingConnection(original, influencer.requiredOffloads());
    }

    /**
     * Convert from {@link ReservedStreamingHttpConnection} to {@link ReservedBlockingStreamingHttpConnection}.
     *
     * @param original {@link ReservedStreamingHttpConnection} to convert.
     * @param strategy required strategy for the service when invoking the resulting
     * {@link ReservedBlockingStreamingHttpConnection}
     * @return The conversion result.
     */
    public static ReservedBlockingStreamingHttpConnection toReservedBlockingStreamingConnection(
            ReservedStreamingHttpConnection original, HttpExecutionStrategy strategy) {
        return new ReservedStreamingHttpConnectionToBlockingStreaming(original, strategy);
    }

    /**
     * Convert from {@link StreamingHttpConnection} to {@link HttpConnection}.
     *
     * @param original {@link StreamingHttpConnection} to convert.
     * @param influencer {@link HttpExecutionStrategyInfluencer} to use to derive the strategy of the returned
     * {@link HttpConnection}
     * @return The conversion result.
     * @deprecated Use overload with {@link HttpExecutionStrategy} rather than {@link HttpExecutionStrategyInfluencer}
     */
    // FIXME: 0.43 - remove deprecated method
    @Deprecated
    public static HttpConnection toConnection(StreamingHttpConnection original,
                                              HttpExecutionStrategyInfluencer influencer) {
        return toConnection(original, influencer.requiredOffloads());
    }

    /**
     * Convert from {@link StreamingHttpConnection} to {@link HttpConnection}.
     *
     * @param original {@link StreamingHttpConnection} to convert.
     * @param strategy required strategy for the service when invoking the resulting {@link HttpConnection}
     * @return The conversion result.
     */
    public static HttpConnection toConnection(StreamingHttpConnection original, HttpExecutionStrategy strategy) {
        return new StreamingHttpConnectionToHttpConnection(original, strategy);
    }

    /**
     * Convert from {@link StreamingHttpConnection} to {@link BlockingHttpConnection}.
     *
     * @param original {@link StreamingHttpConnection} to convert.
     * @param influencer {@link HttpExecutionStrategyInfluencer} to use to derive the strategy of the returned
     * {@link BlockingHttpConnection}
     * @return The conversion result.
     * @deprecated Use overload with {@link HttpExecutionStrategy} rather than {@link HttpExecutionStrategyInfluencer}
     */
    // FIXME: 0.43 - remove deprecated method
    @Deprecated
    public static BlockingHttpConnection toBlockingConnection(StreamingHttpConnection original,
                                                              HttpExecutionStrategyInfluencer influencer) {
        return toBlockingConnection(original, influencer.requiredOffloads());
    }

    /**
     * Convert from {@link StreamingHttpConnection} to {@link BlockingHttpConnection}.
     *
     * @param original {@link StreamingHttpConnection} to convert.
     * @param strategy required strategy for the service when invoking the resulting {@link BlockingHttpConnection}
     * @return The conversion result.
     */
    public static BlockingHttpConnection toBlockingConnection(StreamingHttpConnection original,
                                                              HttpExecutionStrategy strategy) {
        return new StreamingHttpConnectionToBlockingHttpConnection(original, strategy);
    }

    /**
     * Convert from {@link StreamingHttpConnection} to {@link BlockingStreamingHttpConnection}.
     *
     * @param original {@link StreamingHttpConnection} to convert.
     * @param influencer {@link HttpExecutionStrategyInfluencer} to use to derive the strategy of the returned
     * {@link BlockingStreamingHttpConnection}
     * @return The conversion result.
     * @deprecated Use overload with {@link HttpExecutionStrategy} rather than {@link HttpExecutionStrategyInfluencer}
     */
    // FIXME: 0.43 - remove deprecated method
    @Deprecated
    public static BlockingStreamingHttpConnection toBlockingStreamingConnection(
            StreamingHttpConnection original, HttpExecutionStrategyInfluencer influencer) {
        return toBlockingStreamingConnection(original, influencer.requiredOffloads());
    }

    /**
     * Convert from {@link StreamingHttpConnection} to {@link BlockingStreamingHttpConnection}.
     *
     * @param original {@link StreamingHttpConnection} to convert.
     * @param strategy required strategy for the service when invoking the resulting
     * {@link BlockingStreamingHttpConnection}
     * @return The conversion result.
     */
    public static BlockingStreamingHttpConnection toBlockingStreamingConnection(
            StreamingHttpConnection original, HttpExecutionStrategy strategy) {
        return new StreamingHttpConnectionToBlockingStreamingHttpConnection(original, strategy);
    }

    /**
     * Convert from {@link StreamingHttpClient} to {@link HttpClient}.
     *
     * @param original {@link StreamingHttpClient} to convert.
     * @param influencer {@link HttpExecutionStrategyInfluencer} to use to derive the strategy of the returned
     * {@link HttpClient}
     * @return The conversion result.
     * @deprecated Use overload with {@link HttpExecutionStrategy} rather than {@link HttpExecutionStrategyInfluencer}
     */
    // FIXME: 0.43 - remove deprecated method
    @Deprecated
    public static HttpClient toClient(StreamingHttpClient original, HttpExecutionStrategyInfluencer influencer) {
        return toClient(original, influencer.requiredOffloads());
    }

    /**
     * Convert from {@link StreamingHttpClient} to {@link HttpClient}.
     *
     * @param original {@link StreamingHttpClient} to convert.
     * @param strategy required strategy for the service when invoking the resulting {@link HttpClient}
     * @return The conversion result.
     */
    public static HttpClient toClient(StreamingHttpClient original, HttpExecutionStrategy strategy) {
        return new StreamingHttpClientToHttpClient(original, strategy);
    }

    /**
     * Convert from {@link StreamingHttpClient} to {@link BlockingHttpClient}.
     *
     * @param original {@link StreamingHttpClient} to convert.
     * @param influencer {@link HttpExecutionStrategyInfluencer} to use to derive the strategy of the returned
     * {@link BlockingHttpClient}
     * @return The conversion result.
     * @deprecated Use overload with {@link HttpExecutionStrategy} rather than {@link HttpExecutionStrategyInfluencer}
     */
    // FIXME: 0.43 - remove deprecated method
    @Deprecated
    public static BlockingHttpClient toBlockingClient(StreamingHttpClient original,
                                                      HttpExecutionStrategyInfluencer influencer) {
        return toBlockingClient(original, influencer.requiredOffloads());
    }

    /**
     * Convert from {@link StreamingHttpClient} to {@link BlockingHttpClient}.
     *
     * @param original {@link StreamingHttpClient} to convert.
     * @param strategy required strategy for the service when invoking the resulting {@link BlockingHttpClient}
     * @return The conversion result.
     */
    public static BlockingHttpClient toBlockingClient(StreamingHttpClient original, HttpExecutionStrategy strategy) {
        return new StreamingHttpClientToBlockingHttpClient(original, strategy);
    }

    /**
     * Convert from {@link StreamingHttpClient} to {@link BlockingStreamingHttpClient}.
     *
     * @param original {@link StreamingHttpClient} to convert.
     * @param influencer {@link HttpExecutionStrategyInfluencer} to use to derive the strategy of the returned
     * {@link BlockingStreamingHttpClient}
     * @return The conversion result.
     * @deprecated Use overload with {@link HttpExecutionStrategy} rather than {@link HttpExecutionStrategyInfluencer}
     */
    // FIXME: 0.43 - remove deprecated method
    @Deprecated
    public static BlockingStreamingHttpClient toBlockingStreamingClient(StreamingHttpClient original,
                                                                        HttpExecutionStrategyInfluencer influencer) {
        return toBlockingStreamingClient(original, influencer.requiredOffloads());
    }

    /**
     * Convert from {@link StreamingHttpClient} to {@link BlockingStreamingHttpClient}.
     *
     * @param original {@link StreamingHttpClient} to convert.
     * @param strategy required strategy for the service when invoking the resulting {@link BlockingStreamingHttpClient}
     * @return The conversion result.
     */
    public static BlockingStreamingHttpClient toBlockingStreamingClient(StreamingHttpClient original,
                                                                        HttpExecutionStrategy strategy) {
        return new StreamingHttpClientToBlockingStreamingHttpClient(original, strategy);
    }

    /**
     * Convert from a {@link HttpService} to a {@link StreamingHttpService}.
     *
     * @param service The {@link HttpService} to convert.
     * @param influencer {@link HttpExecutionStrategyInfluencer} to influence the strategy for invoking the resulting
     * {@link StreamingHttpService}.
     * @return {@link ServiceAdapterHolder} containing the service adapted to the streaming programming model.
     * @deprecated Use overload with {@link HttpExecutionStrategy} rather than {@link HttpExecutionStrategyInfluencer}
     */
    @Deprecated // FIXME: 0.43 - remove deprecated method
    public static ServiceAdapterHolder toStreamingHttpService(HttpService service,
                                                              HttpExecutionStrategyInfluencer influencer) {
        return toStreamingHttpService(service, influencer.requiredOffloads());
    }

    /**
     * Convert from a {@link HttpService} to a {@link ServiceAdapterHolder}.
     *
     * @param service The {@link HttpService} to convert.
     * @param strategy required strategy for the service when invoking the resulting {@link ServiceAdapterHolder}.
     * @return {@link ServiceAdapterHolder} containing the service adapted to the streaming programming model.
     * @deprecated use {@link #toStreamingHttpService(HttpExecutionStrategy, HttpService)} instead.
     */
    @Deprecated // FIXME: 0.43 - remove deprecated method
    public static ServiceAdapterHolder toStreamingHttpService(HttpService service, HttpExecutionStrategy strategy) {
        return new ServiceToStreamingService(service, strategy);
    }

    /**
     * Convert from a {@link HttpService} to a {@link StreamingHttpService}.
     *
     * @param service The {@link HttpService} to convert.
     * @param strategy required strategy for the service when invoking the resulting {@link StreamingHttpService}.
     * @return {@link StreamingHttpService} containing the service adapted to the streaming programming model.
     */
    public static StreamingHttpService toStreamingHttpService(HttpExecutionStrategy strategy, HttpService service) {
        return new ServiceToStreamingService(service, strategy);
    }

    /**
     * Convert from a {@link BlockingStreamingHttpService} to a {@link StreamingHttpService}.
     *
     * @param service The {@link BlockingStreamingHttpService} to convert.
     * @param influencer {@link HttpExecutionStrategyInfluencer} to influence the strategy for invoking the resulting
     * {@link StreamingHttpService}.
     * @return {@link ServiceAdapterHolder} containing the service adapted to the streaming programming model.
     * @deprecated Use overload with {@link HttpExecutionStrategy} rather than {@link HttpExecutionStrategyInfluencer}
     */
    @Deprecated // FIXME: 0.43 - remove deprecated method
    public static ServiceAdapterHolder toStreamingHttpService(BlockingStreamingHttpService service,
                                                              HttpExecutionStrategyInfluencer influencer) {
        return toStreamingHttpService(service, influencer.requiredOffloads());
    }

    /**
     * Convert from a {@link BlockingStreamingHttpService} to a {@link ServiceAdapterHolder}.
     *
     * @param service The {@link BlockingStreamingHttpService} to convert.
     * @param strategy required strategy for the service when invoking the resulting {@link StreamingHttpService}.
     * @return {@link ServiceAdapterHolder} containing the service adapted to the streaming programming model.
     * @deprecated use {@link #toStreamingHttpService(HttpExecutionStrategy, BlockingStreamingHttpService)} instead.
     */
    @Deprecated // FIXME: 0.43 - remove deprecated method
    public static ServiceAdapterHolder toStreamingHttpService(BlockingStreamingHttpService service,
                                                              HttpExecutionStrategy strategy) {
        return new BlockingStreamingToStreamingService(service, strategy);
    }

    /**
     * Convert from a {@link BlockingStreamingHttpService} to a {@link StreamingHttpService}.
     *
     * @param strategy required strategy for the service when invoking the resulting {@link StreamingHttpService}.
     * @param service The {@link BlockingStreamingHttpService} to convert.
     * @return {@link StreamingHttpService} containing the service adapted to the streaming programming model.
     */
    public static StreamingHttpService toStreamingHttpService(HttpExecutionStrategy strategy,
                                                              BlockingStreamingHttpService service) {
        return new BlockingStreamingToStreamingService(service, strategy);
    }

    /**
     * Convert from a {@link BlockingStreamingHttpService} to a {@link StreamingHttpService}.
     *
     * @param service The {@link BlockingStreamingHttpService} to convert.
     * @param influencer {@link HttpExecutionStrategyInfluencer} to influence the strategy for invoking the resulting
     * {@link StreamingHttpService}.
     * @return {@link ServiceAdapterHolder} containing the service adapted to the streaming programming model.
     * @deprecated Use overload with {@link HttpExecutionStrategy} rather than {@link HttpExecutionStrategyInfluencer}
     */
    // FIXME: 0.43 - remove deprecated method
    @Deprecated
    public static ServiceAdapterHolder toStreamingHttpService(BlockingHttpService service,
                                                              HttpExecutionStrategyInfluencer influencer) {
        return toStreamingHttpService(service, influencer.requiredOffloads());
    }

    /**
     * Convert from a {@link BlockingStreamingHttpService} to a {@link ServiceAdapterHolder}.
     *
     * @param service The {@link BlockingStreamingHttpService} to convert.
     * @param strategy required strategy for the service when invoking the resulting {@link ServiceAdapterHolder}.
     * @return {@link ServiceAdapterHolder} containing the service adapted to the streaming programming model.
     * @deprecated use {@link #toStreamingHttpService(HttpExecutionStrategy, BlockingHttpService)} instead.
     */
    @Deprecated // FIXME: 0.43 - Remove deprecation
    public static ServiceAdapterHolder toStreamingHttpService(BlockingHttpService service,
                                                              HttpExecutionStrategy strategy) {
        return new BlockingToStreamingService(service, strategy);
    }

    /**
     * Convert from a {@link BlockingStreamingHttpService} to a {@link StreamingHttpService}.
     *
     * @param strategy required strategy for the service when invoking the resulting {@link StreamingHttpService}.
     * @param service The {@link BlockingStreamingHttpService} to convert.
     * @return {@link ServiceAdapterHolder} containing the service adapted to the streaming programming model.
     */
    public static StreamingHttpService toStreamingHttpService(HttpExecutionStrategy strategy,
                                                              BlockingHttpService service) {
        return new BlockingToStreamingService(service, strategy);
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

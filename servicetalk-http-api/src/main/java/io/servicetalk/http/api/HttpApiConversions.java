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

import static io.servicetalk.http.api.HttpExecutionStrategies.OFFLOAD_ALL_STRATEGY;
import static io.servicetalk.http.api.HttpExecutionStrategies.OFFLOAD_RECEIVE_DATA_AND_SEND_STRATEGY;
import static io.servicetalk.http.api.HttpExecutionStrategies.OFFLOAD_RECEIVE_DATA_STRATEGY;
import static io.servicetalk.http.api.HttpExecutionStrategies.OFFLOAD_RECEIVE_META_STRATEGY;

/**
 * Conversion routines to {@link StreamingHttpService}.
 */
public final class HttpApiConversions {

    /**
     * For aggregation, we invoke the service after the payload is completed, hence we need to offload data.
     */
    static final HttpExecutionStrategy DEFAULT_SERVICE_STRATEGY = OFFLOAD_RECEIVE_DATA_AND_SEND_STRATEGY;
    static final HttpExecutionStrategy DEFAULT_STREAMING_SERVICE_STRATEGY = OFFLOAD_ALL_STRATEGY;
    static final HttpExecutionStrategy DEFAULT_BLOCKING_SERVICE_STRATEGY = OFFLOAD_RECEIVE_DATA_STRATEGY;
    static final HttpExecutionStrategy DEFAULT_BLOCKING_STREAMING_SERVICE_STRATEGY = OFFLOAD_RECEIVE_META_STRATEGY;

    private HttpApiConversions() {
        // no instances
    }

    /**
     * Convert from a {@link HttpService} to a {@link StreamingHttpService}.
     *
     * @param service The {@link HttpService} to convert.
     * @return The conversion result.
     */
    public static StreamingHttpService toStreamingHttpService(HttpService service) {
        return new ServiceToStreamingService(service);
    }

    /**
     * Convert from a {@link BlockingStreamingHttpService} to a {@link StreamingHttpService}.
     *
     * @param handler The {@link BlockingStreamingHttpService} to convert.
     * @return The conversion result.
     */
    public static StreamingHttpService toStreamingHttpService(BlockingStreamingHttpService handler) {
        return new BlockingStreamingToStreamingService(handler);
    }

    /**
     * Convert from a {@link BlockingHttpService} to a {@link StreamingHttpService}.
     *
     * @param handler The {@link BlockingHttpService} to convert.
     * @return The conversion result.
     */
    public static StreamingHttpService toStreamingHttpService(BlockingHttpService handler) {
        return new BlockingToStreamingService(handler);
    }
}

/*
 * Copyright © 2022 Apple Inc. and the ServiceTalk project authors
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
package io.servicetalk.http.utils;

import io.servicetalk.concurrent.api.Single;
import io.servicetalk.http.api.HttpExecutionStrategy;
import io.servicetalk.http.api.HttpServiceContext;
import io.servicetalk.http.api.StreamingHttpRequest;
import io.servicetalk.http.api.StreamingHttpResponse;
import io.servicetalk.http.api.StreamingHttpResponseFactory;
import io.servicetalk.http.api.StreamingHttpService;
import io.servicetalk.http.api.StreamingHttpServiceFilter;
import io.servicetalk.http.api.StreamingHttpServiceFilterFactory;

import static io.servicetalk.http.api.HttpExecutionStrategies.offloadNone;
import static io.servicetalk.http.utils.PayloadSizeLimitingHttpRequesterFilter.newLimiter;

/**
 * Limits the request payload size. The filter will throw an exception which may result in stream/connection closure.
 * A {@link PayloadTooLargeException} will be thrown when the maximum payload size is exceeded.
 */
public final class PayloadSizeLimitingHttpServiceFilter implements StreamingHttpServiceFilterFactory {
    private final int maxRequestPayloadSize;

    /**
     * Create a new instance.
     * @param maxRequestPayloadSize The maximum request payload size allowed.
     */
    public PayloadSizeLimitingHttpServiceFilter(int maxRequestPayloadSize) {
        if (maxRequestPayloadSize < 0) {
            throw new IllegalArgumentException("maxRequestPayloadSize: " + maxRequestPayloadSize + " (expected >=0)");
        }
        this.maxRequestPayloadSize = maxRequestPayloadSize;
    }

    @Override
    public StreamingHttpServiceFilter create(final StreamingHttpService service) {
        return new StreamingHttpServiceFilter(service) {
            @Override
            public Single<StreamingHttpResponse> handle(
                    final HttpServiceContext ctx, final StreamingHttpRequest request,
                    final StreamingHttpResponseFactory responseFactory) {
                return super.handle(ctx,
                        // We could use transformPayloadBody to convert into Buffers, but transformMessageBody has
                        // slightly less overhead. Since this implementation is internal to ServiceTalk we take the more
                        // advanced route.
                        request.transformMessageBody(newLimiter(maxRequestPayloadSize)), responseFactory);
            }
        };
    }

    @Override
    public HttpExecutionStrategy requiredOffloads() {
        return offloadNone();
    }
}

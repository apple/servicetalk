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

import io.servicetalk.concurrent.api.Publisher;
import io.servicetalk.concurrent.api.Single;
import io.servicetalk.http.api.FilterableStreamingHttpClient;
import io.servicetalk.http.api.FilterableStreamingHttpConnection;
import io.servicetalk.http.api.HttpExecutionStrategy;
import io.servicetalk.http.api.HttpExecutionStrategyInfluencer;
import io.servicetalk.http.api.StreamingHttpClientFilter;
import io.servicetalk.http.api.StreamingHttpClientFilterFactory;
import io.servicetalk.http.api.StreamingHttpConnectionFilter;
import io.servicetalk.http.api.StreamingHttpConnectionFilterFactory;
import io.servicetalk.http.api.StreamingHttpRequest;
import io.servicetalk.http.api.StreamingHttpRequester;
import io.servicetalk.http.api.StreamingHttpResponse;

import java.util.function.Function;

import static io.servicetalk.http.api.HttpExecutionStrategies.offloadNone;

/**
 * Limits the response payload size for a request. The filter will throw an exception which may result in
 * stream/connection closure.
 */
public final class PayloadSizeLimitingHttpRequesterFilter implements
                        StreamingHttpClientFilterFactory, StreamingHttpConnectionFilterFactory,
                        HttpExecutionStrategyInfluencer {
    private final int maxResponsePayloadSize;

    /**
     * Create a new instance.
     * @param maxResponsePayloadSize The maximum response payload size allowed.
     */
    public PayloadSizeLimitingHttpRequesterFilter(int maxResponsePayloadSize) {
        if (maxResponsePayloadSize < 0) {
            throw new IllegalArgumentException("maxResponsePayloadSize: " + maxResponsePayloadSize + " (expected >0)");
        }
        this.maxResponsePayloadSize = maxResponsePayloadSize;
    }

    @Override
    public StreamingHttpClientFilter create(final FilterableStreamingHttpClient client) {
        return new StreamingHttpClientFilter(client) {
            @Override
            protected Single<StreamingHttpResponse> request(final StreamingHttpRequester delegate,
                                                            final StreamingHttpRequest request) {
                return applyLimit(request, delegate::request);
            }
        };
    }

    @Override
    public StreamingHttpConnectionFilter create(final FilterableStreamingHttpConnection connection) {
        return new StreamingHttpConnectionFilter(connection) {
            @Override
            public Single<StreamingHttpResponse> request(final StreamingHttpRequest request) {
                return applyLimit(request, super::request);
            }
        };
    }

    @Override
    public HttpExecutionStrategy requiredOffloads() {
        return offloadNone();
    }

    private Single<StreamingHttpResponse> applyLimit(
            StreamingHttpRequest request, Function<StreamingHttpRequest, Single<StreamingHttpResponse>> delegator) {
        return delegator.apply(request).map(response -> response.transformPayloadBody(publisher ->
                Publisher.defer(() -> {
                    final MutableInt responsePayloadSize = new MutableInt();
                    return publisher.beforeOnNext(buff -> {
                        if (maxResponsePayloadSize - responsePayloadSize.value < buff.readableBytes()) {
                            throw new PayloadTooLongException("Maximum payload size=" + maxResponsePayloadSize +
                                    " current payload size=" + responsePayloadSize.value +
                                    " new buffer size=" + buff.readableBytes());
                        }
                        responsePayloadSize.value += buff.readableBytes();
                    }).shareContextOnSubscribe();
                })));
    }

    static final class PayloadTooLongException extends IllegalStateException {
        private static final long serialVersionUID = 7332745010452084915L;

        PayloadTooLongException(String message) {
            super(message);
        }
    }

    private static final class MutableInt {
        int value;
    }
}

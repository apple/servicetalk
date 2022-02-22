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

import io.servicetalk.buffer.api.Buffer;
import io.servicetalk.concurrent.api.Publisher;
import io.servicetalk.concurrent.api.Single;
import io.servicetalk.http.api.FilterableStreamingHttpClient;
import io.servicetalk.http.api.FilterableStreamingHttpConnection;
import io.servicetalk.http.api.HttpExecutionStrategy;
import io.servicetalk.http.api.PayloadTooLargeException;
import io.servicetalk.http.api.StreamingHttpClientFilter;
import io.servicetalk.http.api.StreamingHttpClientFilterFactory;
import io.servicetalk.http.api.StreamingHttpConnectionFilter;
import io.servicetalk.http.api.StreamingHttpConnectionFilterFactory;
import io.servicetalk.http.api.StreamingHttpRequest;
import io.servicetalk.http.api.StreamingHttpRequester;
import io.servicetalk.http.api.StreamingHttpResponse;

import java.util.function.Function;
import java.util.function.UnaryOperator;

import static io.servicetalk.http.api.HttpExecutionStrategies.offloadNone;

/**
 * Limits the response payload size. The filter will throw an exception which may result in stream/connection closure.
 * A {@link PayloadTooLargeException} will be thrown when the maximum payload size is exceeded.
 */
public final class PayloadSizeLimitingHttpRequesterFilter implements
                        StreamingHttpClientFilterFactory, StreamingHttpConnectionFilterFactory {
    private final int maxResponsePayloadSize;

    /**
     * Create a new instance.
     * @param maxResponsePayloadSize The maximum response payload size allowed.
     */
    public PayloadSizeLimitingHttpRequesterFilter(int maxResponsePayloadSize) {
        if (maxResponsePayloadSize < 0) {
            throw new IllegalArgumentException("maxResponsePayloadSize: " + maxResponsePayloadSize + " (expected >=0)");
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
        return delegator.apply(request).map(response ->
                // We could use transformPayloadBody to convert into Buffers, but transformMessageBody has slightly
                // less overhead. Since this implementation is internal to ServiceTalk we take the more advanced route.
                response.transformMessageBody(newLimiter(maxResponsePayloadSize)));
    }

    static UnaryOperator<Publisher<?>> newLimiter(int maxPayloadSize) {
        return publisher -> Publisher.defer(() -> {
            final MutableInt responsePayloadSize = new MutableInt();
            return publisher.beforeOnNext(obj -> {
                if (obj instanceof Buffer) {
                    final Buffer buff = (Buffer) obj;
                    if (maxPayloadSize - responsePayloadSize.value < buff.readableBytes()) {
                        throw new PayloadTooLargeException("Maximum payload size=" + maxPayloadSize +
                                " current payload size=" + responsePayloadSize.value + " new buffer size=" +
                                buff.readableBytes());
                    }
                    responsePayloadSize.value += buff.readableBytes();
                }
            }).shareContextOnSubscribe();
        });
    }
}

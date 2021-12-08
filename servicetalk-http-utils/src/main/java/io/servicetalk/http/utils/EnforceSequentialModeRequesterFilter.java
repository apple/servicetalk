/*
 * Copyright © 2021 Apple Inc. and the ServiceTalk project authors
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

import io.servicetalk.concurrent.CompletableSource;
import io.servicetalk.concurrent.api.Single;
import io.servicetalk.http.api.BlockingStreamingHttpRequester;
import io.servicetalk.http.api.FilterableStreamingHttpClient;
import io.servicetalk.http.api.FilterableStreamingHttpConnection;
import io.servicetalk.http.api.HttpExecutionStrategies;
import io.servicetalk.http.api.HttpExecutionStrategy;
import io.servicetalk.http.api.StreamingHttpClientFilter;
import io.servicetalk.http.api.StreamingHttpClientFilterFactory;
import io.servicetalk.http.api.StreamingHttpConnectionFilter;
import io.servicetalk.http.api.StreamingHttpConnectionFilterFactory;
import io.servicetalk.http.api.StreamingHttpRequest;
import io.servicetalk.http.api.StreamingHttpRequester;
import io.servicetalk.http.api.StreamingHttpResponse;

import static io.servicetalk.concurrent.api.Processors.newCompletableProcessor;
import static io.servicetalk.concurrent.api.SourceAdapters.fromSource;

/**
 * Enforces sequential behavior of the client, deferring return of the response until after the request payload body is
 * sent.
 * <p>
 * ServiceTalk transport is full-duplex, meaning that a {@link StreamingHttpRequester} or
 * {@link BlockingStreamingHttpRequester} can read the response before or while it is still sending a request payload
 * body. In some scenarios, and for backward compatibility with legacy HTTP clients, users may have expectations of a
 * sequential execution of the request and response. This filter helps to enforce that behavior.
 */
public final class EnforceSequentialModeRequesterFilter implements StreamingHttpClientFilterFactory,
                                                                   StreamingHttpConnectionFilterFactory {

    /**
     * Singleton instance of {@link EnforceSequentialModeRequesterFilter}.
     */
    public static final EnforceSequentialModeRequesterFilter INSTANCE = new EnforceSequentialModeRequesterFilter();

    private EnforceSequentialModeRequesterFilter() {
        // Singleton
    }

    private static Single<StreamingHttpResponse> request(final StreamingHttpRequester delegate,
                                                         final StreamingHttpRequest request) {
        return Single.defer(() -> {
            CompletableSource.Processor requestSent = newCompletableProcessor();
            StreamingHttpRequest r = request.transformMessageBody(messageBody -> messageBody
                    .whenFinally(requestSent::onComplete));
            return fromSource(requestSent).merge(delegate.request(r).toPublisher()).firstOrError()
                    .shareContextOnSubscribe();
        });
    }

    @Override
    public StreamingHttpClientFilter create(final FilterableStreamingHttpClient client) {
        return new StreamingHttpClientFilter(client) {
            @Override
            protected Single<StreamingHttpResponse> request(final StreamingHttpRequester delegate,
                                                            final StreamingHttpRequest request) {
                return EnforceSequentialModeRequesterFilter.request(delegate, request);
            }
        };
    }

    @Override
    public StreamingHttpConnectionFilter create(final FilterableStreamingHttpConnection connection) {
        return new StreamingHttpConnectionFilter(connection) {
            @Override
            public Single<StreamingHttpResponse> request(final StreamingHttpRequest request) {
                return EnforceSequentialModeRequesterFilter.request(delegate(), request);
            }
        };
    }

    @Override
    public HttpExecutionStrategy requiredOffloads() {
        // No influence since we do not block.
        return HttpExecutionStrategies.offloadNone();
    }
}

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
package io.servicetalk.http.api;

import io.servicetalk.concurrent.api.Completable;
import io.servicetalk.concurrent.api.ListenableAsyncCloseable;
import io.servicetalk.concurrent.api.Publisher;
import io.servicetalk.concurrent.api.Single;
import io.servicetalk.transport.api.ExecutionContext;

import static io.servicetalk.concurrent.api.AsyncCloseables.emptyAsyncCloseable;
import static io.servicetalk.concurrent.api.Single.error;
import static io.servicetalk.http.api.HttpExecutionStrategies.defaultStrategy;

public final class TestStreamingHttpClient {

    private TestStreamingHttpClient() {
        // no instances
    }

    public static StreamingHttpClient from(
            final StreamingHttpRequestResponseFactory reqRespFactory,
            final ExecutionContext executionContext,
            final HttpClientFilterFactory factory) {
        final StreamingHttpClientFilter filterChain = factory.
                create(new StreamingHttpClientFilter(StreamingHttpClientFilter.terminal(reqRespFactory)) {

                    private final ListenableAsyncCloseable closeable = emptyAsyncCloseable();

                    @Override
                    public Completable closeAsync() {
                        return closeable.closeAsync();
                    }

                    @Override
                    public Completable closeAsyncGracefully() {
                        return closeable.closeAsyncGracefully();
                    }

                    @Override
                    public Completable onClose() {
                        return closeable.onClose();
                    }

                    @Override
                    protected Single<StreamingHttpResponse> request(final StreamingHttpRequestFunction delegate,
                                                                    final HttpExecutionStrategy strategy,
                                                                    final StreamingHttpRequest request) {
                        return error(new UnsupportedOperationException());
                    }

                    @Override
                    public ExecutionContext executionContext() {
                        return executionContext;
                    }

                    @Override
                    protected Single<ReservedStreamingHttpConnectionFilter> reserveConnection(
                            final StreamingHttpClientFilter delegate,
                            final HttpExecutionStrategy strategy,
                            final HttpRequestMetaData metaData) {
                        return error(new UnsupportedOperationException());
                    }
                }, Publisher.empty());
        return new StreamingHttpClient(filterChain, defaultStrategy());
    }
}

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

import static io.servicetalk.concurrent.api.AsyncCloseables.emptyAsyncCloseable;
import static io.servicetalk.concurrent.api.Single.failed;
import static io.servicetalk.http.api.HttpApiConversions.toBlockingConnection;
import static io.servicetalk.http.api.HttpApiConversions.toBlockingStreamingConnection;
import static io.servicetalk.http.api.HttpApiConversions.toConnection;

public final class TestStreamingHttpConnection {

    private TestStreamingHttpConnection() {
        // No instances
    }

    public static StreamingHttpConnection from(
            final StreamingHttpRequestResponseFactory reqRespFactory,
            final HttpExecutionContext executionContext,
            final HttpConnectionContext connectionContext,
            final StreamingHttpConnectionFilterFactory factory) {
        final StreamingHttpConnectionFilter filterChain = factory
                .create(new FilterableStreamingHttpConnection() {
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
                    public Completable onClosing() {
                        return closeable.onClosing();
                    }

                    @Override
                    public Single<StreamingHttpResponse> request(final StreamingHttpRequest request) {
                        return failed(new UnsupportedOperationException());
                    }

                    @Override
                    public HttpExecutionContext executionContext() {
                        return executionContext;
                    }

                    @Override
                    public StreamingHttpResponseFactory httpResponseFactory() {
                        return reqRespFactory;
                    }

                    @Override
                    public HttpConnectionContext connectionContext() {
                        return connectionContext;
                    }

                    @Override
                    public <T> Publisher<? extends T> transportEventStream(final HttpEventKey<T> eventKey) {
                        return Publisher.failed(new UnsupportedOperationException());
                    }

                    @Override
                    public StreamingHttpRequest newRequest(final HttpRequestMethod method, final String requestTarget) {
                        return reqRespFactory.newRequest(method, requestTarget);
                    }
                });
        return from(filterChain);
    }

    public static StreamingHttpConnection from(FilterableStreamingHttpConnection filterChain) {
        return new StreamingHttpConnection() {
            @Override
            public StreamingHttpRequest newRequest(final HttpRequestMethod method, final String requestTarget) {
                return filterChain.newRequest(method, requestTarget);
            }

            @Override
            public Completable closeAsync() {
                return filterChain.closeAsync();
            }

            @Override
            public Completable closeAsyncGracefully() {
                return filterChain.closeAsyncGracefully();
            }

            @Override
            public Completable onClose() {
                return filterChain.onClose();
            }

            @Override
            public Completable onClosing() {
                return filterChain.onClosing();
            }

            @Override
            public Single<StreamingHttpResponse> request(final StreamingHttpRequest request) {
                return filterChain.request(request);
            }

            @Override
            public HttpExecutionContext executionContext() {
                return filterChain.executionContext();
            }

            @Override
            public StreamingHttpResponseFactory httpResponseFactory() {
                return filterChain.httpResponseFactory();
            }

            @Override
            public HttpConnectionContext connectionContext() {
                return filterChain.connectionContext();
            }

            @Override
            public <T> Publisher<? extends T> transportEventStream(final HttpEventKey<T> eventKey) {
                return filterChain.transportEventStream(eventKey);
            }

            @Override
            public HttpConnection asConnection() {
                return toConnection(this, HttpExecutionStrategies.offloadNone());
            }

            @Override
            public BlockingStreamingHttpConnection asBlockingStreamingConnection() {
                return toBlockingStreamingConnection(this, HttpExecutionStrategies.offloadNone());
            }

            @Override
            public BlockingHttpConnection asBlockingConnection() {
                return toBlockingConnection(this, HttpExecutionStrategies.offloadNone());
            }
        };
    }
}

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

import io.servicetalk.concurrent.CompletableSource;
import io.servicetalk.concurrent.api.Completable;
import io.servicetalk.concurrent.api.Single;
import io.servicetalk.transport.api.ExecutionContext;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BiFunction;

import static io.servicetalk.concurrent.api.Processors.newCompletableProcessor;
import static io.servicetalk.concurrent.api.Single.failed;
import static io.servicetalk.concurrent.api.SourceAdapters.fromSource;
import static io.servicetalk.http.api.HttpApiConversions.toBlockingClient;
import static io.servicetalk.http.api.HttpApiConversions.toBlockingStreamingClient;
import static io.servicetalk.http.api.HttpApiConversions.toClient;
import static io.servicetalk.http.api.HttpExecutionStrategies.noOffloadsStrategy;
import static java.util.Objects.requireNonNull;

public class BlockingStreamingHttpClientTest extends AbstractBlockingStreamingHttpRequesterTest {
    @SuppressWarnings("unchecked")
    @Override
    protected <T extends StreamingHttpRequester & TestHttpRequester> T newAsyncRequester(
            StreamingHttpRequestResponseFactory factory,
            final ExecutionContext ctx,
            final BiFunction<HttpExecutionStrategy, StreamingHttpRequest, Single<StreamingHttpResponse>> doRequest) {
        return (T) new TestStreamingHttpClient(factory, ctx) {
            @Override
            public Single<StreamingHttpResponse> request(final HttpExecutionStrategy strategy,
                                                         final StreamingHttpRequest request) {
                return doRequest.apply(strategy, request);
            }

            @Override
            public Single<ReservedStreamingHttpConnection> reserveConnection(
                    final HttpExecutionStrategy strategy, final HttpRequestMetaData metaData) {
                return failed(new UnsupportedOperationException());
            }
        };
    }

    @Override
    protected BlockingStreamingHttpRequester toBlockingStreamingRequester(final StreamingHttpRequester requester) {
        return ((StreamingHttpClient) requester).asBlockingStreamingClient();
    }

    private abstract static class TestStreamingHttpClient implements StreamingHttpClient, TestHttpRequester {
        private final AtomicBoolean closed = new AtomicBoolean();
        private final CompletableSource.Processor onClose = newCompletableProcessor();
        private final ExecutionContext executionContext;
        private final StreamingHttpRequestResponseFactory factory;

        TestStreamingHttpClient(StreamingHttpRequestResponseFactory factory,
                                ExecutionContext executionContext) {
            this.factory = factory;
            this.executionContext = requireNonNull(executionContext);
        }

        @Override
        public ExecutionContext executionContext() {
            return executionContext;
        }

        @Override
        public StreamingHttpResponseFactory httpResponseFactory() {
            return factory;
        }

        @Override
        public final Completable onClose() {
            return fromSource(onClose);
        }

        @Override
        public final Completable closeAsync() {
            return new Completable() {
                @Override
                protected void handleSubscribe(final CompletableSource.Subscriber subscriber) {
                    if (closed.compareAndSet(false, true)) {
                        onClose.onComplete();
                    }
                    onClose.subscribe(subscriber);
                }
            };
        }

        @Override
        public final boolean isClosed() {
            return closed.get();
        }

        @Override
        public Single<StreamingHttpResponse> request(final StreamingHttpRequest request) {
            return request(noOffloadsStrategy(), request);
        }

        @Override
        public Single<ReservedStreamingHttpConnection> reserveConnection(final HttpRequestMetaData metaData) {
            return reserveConnection(noOffloadsStrategy(), metaData);
        }

        @Override
        public StreamingHttpRequest newRequest(final HttpRequestMethod method, final String requestTarget) {
            return factory.newRequest(method, requestTarget);
        }

        @Override
        public HttpClient asClient() {
            return toClient(this, strategy -> strategy);
        }

        @Override
        public BlockingStreamingHttpClient asBlockingStreamingClient() {
            return toBlockingStreamingClient(this, strategy -> strategy);
        }

        @Override
        public BlockingHttpClient asBlockingClient() {
            return toBlockingClient(this, strategy -> strategy);
        }
    }
}

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
import io.servicetalk.concurrent.api.Publisher;
import io.servicetalk.concurrent.api.Single;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BiFunction;

import static io.servicetalk.concurrent.api.Processors.newCompletableProcessor;
import static io.servicetalk.concurrent.api.SourceAdapters.fromSource;
import static io.servicetalk.http.api.HttpApiConversions.toBlockingConnection;
import static io.servicetalk.http.api.HttpApiConversions.toBlockingStreamingConnection;
import static io.servicetalk.http.api.HttpApiConversions.toConnection;
import static io.servicetalk.http.api.HttpExecutionStrategies.offloadNever;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class BlockingStreamingHttpConnectionTest extends AbstractBlockingStreamingHttpRequesterTest {
    @SuppressWarnings("unchecked")
    @Override
    protected <T extends StreamingHttpRequester & TestHttpRequester> T newAsyncRequester(
            StreamingHttpRequestResponseFactory factory,
            final HttpExecutionContext ctx,
            final BiFunction<HttpExecutionStrategy, StreamingHttpRequest, Single<StreamingHttpResponse>> doRequest) {
        return (T) new TestStreamingHttpConnection(factory, ctx) {
            @Override
            public Single<StreamingHttpResponse> request(final HttpExecutionStrategy strategy,
                                                         final StreamingHttpRequest request) {
                return doRequest.apply(strategy, request);
            }
        };
    }

    @Override
    protected BlockingStreamingHttpRequester toBlockingStreamingRequester(final StreamingHttpRequester requester) {
        return ((StreamingHttpConnection) requester).asBlockingStreamingConnection();
    }

    private abstract static class TestStreamingHttpConnection implements StreamingHttpConnection, TestHttpRequester {
        private final AtomicBoolean closed = new AtomicBoolean();
        private final CompletableSource.Processor onClose = newCompletableProcessor();
        private final HttpExecutionContext executionContext;
        private final HttpConnectionContext connectionContext;
        private final StreamingHttpRequestResponseFactory factory;

        TestStreamingHttpConnection(StreamingHttpRequestResponseFactory factory,
                                    HttpExecutionContext executionContext) {
            this.factory = factory;
            this.executionContext = executionContext;
            this.connectionContext = mock(HttpConnectionContext.class);
            when(connectionContext.executionContext()).thenReturn(executionContext);
        }

        @Override
        public final HttpConnectionContext connectionContext() {
            return connectionContext;
        }

        @Override
        public StreamingHttpResponseFactory httpResponseFactory() {
            return factory;
        }

        @Override
        public final <T> Publisher<? extends T> transportEventStream(final HttpEventKey<T> eventKey) {
            return Publisher.failed(new IllegalStateException("unsupported"));
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
        public HttpExecutionContext executionContext() {
            return executionContext;
        }

        @Override
        public final boolean isClosed() {
            return closed.get();
        }

        @Override
        public StreamingHttpRequest newRequest(final HttpRequestMethod method, final String requestTarget) {
            return factory.newRequest(method, requestTarget);
        }

        @Override
        public Single<StreamingHttpResponse> request(final StreamingHttpRequest request) {
            return request(offloadNever(), request);
        }

        @Override
        public HttpConnection asConnection() {
            return toConnection(this, strategy -> strategy);
        }

        @Override
        public BlockingStreamingHttpConnection asBlockingStreamingConnection() {
            return toBlockingStreamingConnection(this, strategy -> strategy);
        }

        @Override
        public BlockingHttpConnection asBlockingConnection() {
            return toBlockingConnection(this, strategy -> strategy);
        }
    }
}

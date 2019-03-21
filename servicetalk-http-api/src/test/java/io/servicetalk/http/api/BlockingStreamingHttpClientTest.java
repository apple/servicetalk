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
import io.servicetalk.concurrent.CompletableSource.Processor;
import io.servicetalk.concurrent.api.Completable;
import io.servicetalk.concurrent.api.Publisher;
import io.servicetalk.concurrent.api.Single;
import io.servicetalk.transport.api.ConnectionContext;
import io.servicetalk.transport.api.ExecutionContext;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BiFunction;

import static io.servicetalk.concurrent.api.Processors.newCompletableProcessor;
import static io.servicetalk.concurrent.api.Single.error;
import static io.servicetalk.concurrent.api.SourceAdapters.fromSource;
import static io.servicetalk.http.api.HttpExecutionStrategies.defaultStrategy;
import static io.servicetalk.http.api.RequestResponseFactories.toStreaming;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class BlockingStreamingHttpClientTest extends AbstractBlockingStreamingHttpRequesterTest {

    @Override
    protected TestStreamingHttpRequester newAsyncRequester(
            StreamingHttpRequestResponseFactory reqRespFactory,
            final ExecutionContext ctx,
            final BiFunction<HttpExecutionStrategy, StreamingHttpRequest, Single<StreamingHttpResponse>> doRequest) {
        return new TestStreamingHttpClient(ctx, reqRespFactory, (client, __) ->
                new StreamingHttpClientFilter(client) {

                    @Override
                    protected Single<StreamingHttpResponse> request(final StreamingHttpRequester delegate,
                                                                    final HttpExecutionStrategy strategy,
                                                                    final StreamingHttpRequest request) {
                        return doRequest.apply(strategy, request);
                    }

                    @Override
                    protected Single<ReservedStreamingHttpConnectionFilter> reserveConnection(
                            final StreamingHttpClientFilter delegate,
                            final HttpExecutionStrategy strategy,
                            final HttpRequestMetaData metaData) {
                        return error(new UnsupportedOperationException());
                    }
                });
    }

    @Override
    protected TestBlockingStreamingHttpRequester newBlockingRequester(
            final BlockingStreamingHttpRequestResponseFactory reqRespFactory,
            final ExecutionContext ctx,
            final BiFunction<HttpExecutionStrategy, BlockingStreamingHttpRequest,
                    BlockingStreamingHttpResponse> doRequest) {
        return new TestBlockingStreamingHttpClient(ctx, reqRespFactory, doRequest);
    }

    private static final class TestStreamingHttpClient extends TestStreamingHttpRequester {
        private final AtomicBoolean closed = new AtomicBoolean();
        private final StreamingHttpClient client;

        private TestStreamingHttpClient(final ExecutionContext executionContext,
                                final StreamingHttpRequestResponseFactory reqRespFactory,
                                final HttpClientFilterFactory factory) {
            super(reqRespFactory, defaultStrategy());
            client = new StreamingHttpClient(factory.create(
                    new TestClientTransport(reqRespFactory, executionContext), Publisher.empty()), defaultStrategy());
        }

        @Override
        public Single<StreamingHttpResponse> request(final HttpExecutionStrategy strategy,
                                                     final StreamingHttpRequest request) {
            return client.request(strategy, request);
        }

        @Override
        public ExecutionContext executionContext() {
            return client.executionContext();
        }

        @Override
        public Completable onClose() {
            return client.onClose();
        }

        @Override
        public Completable closeAsync() {
            return client.closeAsync();
        }

        @Override
        public Completable closeAsyncGracefully() {
            return client.closeAsyncGracefully();
        }

        @Override
        public boolean isClosed() {
            return closed.get();
        }

        @Override
        public BlockingStreamingHttpRequester asBlockingStreaming() {
            return client.asBlockingStreamingClient();
        }

        private final class TestClientTransport extends StreamingHttpClientFilter {
            private final Processor onClose = newCompletableProcessor();
            private final ExecutionContext executionContext;
            private final ConnectionContext connectionContext;

            TestClientTransport(final StreamingHttpRequestResponseFactory reqRespFactory,
                                final ExecutionContext executionContext) {
                super(terminal(reqRespFactory));
                this.executionContext = executionContext;
                this.connectionContext = mock(ConnectionContext.class);
                when(connectionContext.executionContext()).thenReturn(executionContext);
            }

            @Override
            public Completable onClose() {
                return fromSource(onClose);
            }

            @Override
            public Completable closeAsync() {
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
            public ExecutionContext executionContext() {
                return executionContext;
            }
        }
    }

    private static final class TestBlockingStreamingHttpClient extends TestBlockingStreamingHttpRequester {

        private final TestStreamingHttpClient streamingHttpClient;
        private final BlockingStreamingHttpClient client;

        private TestBlockingStreamingHttpClient(ExecutionContext executionContext,
                                        BlockingStreamingHttpRequestResponseFactory requestResponseFactory,
                                        final BiFunction<HttpExecutionStrategy, BlockingStreamingHttpRequest,
                                                BlockingStreamingHttpResponse> doRequest) {
            super(requestResponseFactory, defaultStrategy());
            streamingHttpClient = new TestStreamingHttpClient(executionContext, toStreaming(requestResponseFactory),
                    new BlockingFilter(doRequest));
            client = streamingHttpClient.client.asBlockingStreamingClient();
        }

        @Override
        public BlockingStreamingHttpResponse request(final HttpExecutionStrategy strategy,
                                                     final BlockingStreamingHttpRequest request) throws Exception {
            return client.request(strategy, request);
        }

        @Override
        public void close() throws Exception {
            client.close();
        }

        @Override
        public boolean isClosed() {
            return streamingHttpClient.isClosed();
        }

        @Override
        public StreamingHttpRequester asStreaming() {
            return streamingHttpClient;
        }

        @Override
        public ExecutionContext executionContext() {
            return client.executionContext();
        }
    }
}

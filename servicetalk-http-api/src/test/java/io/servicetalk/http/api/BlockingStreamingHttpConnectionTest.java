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
import io.servicetalk.http.api.StreamingHttpConnection.SettingKey;
import io.servicetalk.transport.api.ConnectionContext;
import io.servicetalk.transport.api.ExecutionContext;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BiFunction;

import static io.servicetalk.concurrent.api.Processors.newCompletableProcessor;
import static io.servicetalk.concurrent.api.SourceAdapters.fromSource;
import static io.servicetalk.http.api.HttpExecutionStrategies.defaultStrategy;
import static io.servicetalk.http.api.RequestResponseFactories.toStreaming;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class BlockingStreamingHttpConnectionTest extends AbstractBlockingStreamingHttpRequesterTest {

    @Override
    protected TestStreamingHttpRequester newAsyncRequester(
            StreamingHttpRequestResponseFactory reqRespFactory,
            final ExecutionContext ctx,
            final BiFunction<HttpExecutionStrategy, StreamingHttpRequest, Single<StreamingHttpResponse>> doRequest) {
        return new TestStreamingHttpConnection(ctx, reqRespFactory, connection ->
                new StreamingHttpConnectionFilter(connection) {
                    @Override
                    protected Single<StreamingHttpResponse> request(final StreamingHttpConnectionFilter delegate,
                                                                    final HttpExecutionStrategy strategy,
                                                                    final StreamingHttpRequest request) {
                        return doRequest.apply(strategy, request);
                    }
                });
    }

    @Override
    protected TestBlockingStreamingHttpRequester newBlockingRequester(
            BlockingStreamingHttpRequestResponseFactory reqRespFactory,
            final ExecutionContext ctx,
            final BiFunction<HttpExecutionStrategy, BlockingStreamingHttpRequest,
                    BlockingStreamingHttpResponse> doRequest) {
        return new TestBlockingStreamingHttpConnection(ctx, reqRespFactory, doRequest);
    }

    private static final class TestStreamingHttpConnection extends TestStreamingHttpRequester {
        private final AtomicBoolean closed = new AtomicBoolean();
        private final StreamingHttpConnection conn;

        private TestStreamingHttpConnection(final ExecutionContext executionContext,
                                    final StreamingHttpRequestResponseFactory reqRespFactory,
                                    final HttpConnectionFilterFactory factory) {
            super(reqRespFactory, defaultStrategy());
            conn = new StreamingHttpConnection(factory.create(
                    new TestConnectionTransport(reqRespFactory, executionContext)), defaultStrategy());
        }

        @Override
        public Single<StreamingHttpResponse> request(final HttpExecutionStrategy strategy,
                                                     final StreamingHttpRequest request) {
            return conn.request(strategy, request);
        }

        @Override
        public ExecutionContext executionContext() {
            return conn.executionContext();
        }

        @Override
        public Completable onClose() {
            return conn.onClose();
        }

        @Override
        public Completable closeAsync() {
            return conn.closeAsync();
        }

        @Override
        public Completable closeAsyncGracefully() {
            return conn.closeAsyncGracefully();
        }

        @Override
        public boolean isClosed() {
            return closed.get();
        }

        @Override
        public BlockingStreamingHttpRequester asBlockingStreaming() {
            return conn.asBlockingStreamingConnection();
        }

        private final class TestConnectionTransport extends StreamingHttpConnectionFilter {
            private final Processor onClose = newCompletableProcessor();
            private final ExecutionContext executionContext;
            private final ConnectionContext connectionContext;

            private TestConnectionTransport(final StreamingHttpRequestResponseFactory reqRespFactory,
                                    final ExecutionContext executionContext) {
                super(terminal(reqRespFactory));
                this.executionContext = executionContext;
                this.connectionContext = mock(ConnectionContext.class);
                when(connectionContext.executionContext()).thenReturn(executionContext);
            }

            @Override
            public ConnectionContext connectionContext() {
                return connectionContext;
            }

            @Override
            public <T> Publisher<T> settingStream(final SettingKey<T> settingKey) {
                return Publisher.error(new IllegalStateException("unsupported"));
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

    private static final class TestBlockingStreamingHttpConnection extends TestBlockingStreamingHttpRequester {

        private final TestStreamingHttpConnection streamingConnection;
        private final BlockingStreamingHttpRequester conn;

        TestBlockingStreamingHttpConnection(final ExecutionContext executionContext,
                                            final BlockingStreamingHttpRequestResponseFactory reqRespFactory,
                                            final BiFunction<HttpExecutionStrategy, BlockingStreamingHttpRequest,
                                                    BlockingStreamingHttpResponse> doRequest) {
            super(reqRespFactory, defaultStrategy());
            StreamingHttpRequestResponseFactory blkReqRespFactory = toStreaming(reqRespFactory);
            streamingConnection = new TestStreamingHttpConnection(executionContext, blkReqRespFactory,
                    new BlockingFilter(doRequest));
            conn = streamingConnection.asBlockingStreaming();
        }

        @Override
        public BlockingStreamingHttpResponse request(final HttpExecutionStrategy strategy,
                                                     final BlockingStreamingHttpRequest request) throws Exception {
            return conn.request(strategy, request);
        }

        @Override
        public void close() throws Exception {
            conn.close();
        }

        @Override
        public boolean isClosed() {
            return streamingConnection.isClosed();
        }

        @Override
        public StreamingHttpRequester asStreaming() {
            return streamingConnection;
        }

        @Override
        public ExecutionContext executionContext() {
            return conn.executionContext();
        }
    }
}

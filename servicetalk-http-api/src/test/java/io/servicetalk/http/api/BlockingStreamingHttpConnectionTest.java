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

import io.servicetalk.concurrent.BlockingIterable;
import io.servicetalk.concurrent.api.Completable;
import io.servicetalk.concurrent.api.CompletableProcessor;
import io.servicetalk.concurrent.api.Publisher;
import io.servicetalk.concurrent.api.Single;
import io.servicetalk.transport.api.ConnectionContext;
import io.servicetalk.transport.api.ExecutionContext;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Function;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class BlockingStreamingHttpConnectionTest extends AbstractBlockingStreamingHttpRequesterTest {

    @SuppressWarnings("unchecked")
    @Override
    protected <T extends StreamingHttpRequester & TestHttpRequester> T newAsyncRequester(final ExecutionContext ctx,
                                                                                         final Function<StreamingHttpRequest<HttpPayloadChunk>, Single<StreamingHttpResponse<HttpPayloadChunk>>> doRequest) {
        return (T) new TestStreamingHttpConnection(ctx) {
            @Override
            public Single<StreamingHttpResponse<HttpPayloadChunk>> request(final StreamingHttpRequest<HttpPayloadChunk> request) {
                return doRequest.apply(request);
            }
        };
    }

    @SuppressWarnings("unchecked")
    @Override
    protected <T extends BlockingStreamingHttpRequester & TestHttpRequester> T newBlockingRequester(final ExecutionContext ctx,
                                                                                                    final Function<BlockingStreamingHttpRequest<HttpPayloadChunk>, BlockingStreamingHttpResponse<HttpPayloadChunk>> doRequest) {
        return (T) new TestBlockingStreamingHttpConnection(ctx) {
            @Override
            public BlockingStreamingHttpResponse<HttpPayloadChunk> request(final BlockingStreamingHttpRequest<HttpPayloadChunk> request) {
                return doRequest.apply(request);
            }
        };
    }

    private abstract static class TestStreamingHttpConnection extends StreamingHttpConnection implements TestHttpRequester {
        private final AtomicBoolean closed = new AtomicBoolean();
        private final CompletableProcessor onClose = new CompletableProcessor();
        private final ExecutionContext executionContext;
        private final ConnectionContext connectionContext;

        TestStreamingHttpConnection(ExecutionContext executionContext) {
            this.executionContext = executionContext;
            this.connectionContext = mock(ConnectionContext.class);
            when(connectionContext.getExecutionContext()).thenReturn(executionContext);
        }

        @Override
        public final ConnectionContext getConnectionContext() {
            return connectionContext;
        }

        @Override
        public final <T> Publisher<T> getSettingStream(final SettingKey<T> settingKey) {
            return Publisher.error(new IllegalStateException("unsupported"));
        }

        @Override
        public final Completable onClose() {
            return onClose;
        }

        @Override
        public final Completable closeAsync() {
            return new Completable() {
                @Override
                protected void handleSubscribe(final Subscriber subscriber) {
                    if (closed.compareAndSet(false, true)) {
                        onClose.onComplete();
                    }
                    onClose.subscribe(subscriber);
                }
            };
        }

        @Override
        public ExecutionContext getExecutionContext() {
            return executionContext;
        }

        @Override
        public final boolean isClosed() {
            return closed.get();
        }
    }

    private abstract static class TestBlockingStreamingHttpConnection extends BlockingStreamingHttpConnection
            implements TestHttpRequester {
        private final AtomicBoolean closed = new AtomicBoolean();
        private final ExecutionContext executionContext;
        private final ConnectionContext connectionContext;

        TestBlockingStreamingHttpConnection(ExecutionContext executionContext) {
            this.executionContext = executionContext;
            this.connectionContext = mock(ConnectionContext.class);
            when(connectionContext.getExecutionContext()).thenReturn(executionContext);
        }

        @Override
        public ConnectionContext getConnectionContext() {
            return connectionContext;
        }

        @Override
        public ExecutionContext getExecutionContext() {
            return executionContext;
        }

        @Override
        public <T> BlockingIterable<T> getSettingIterable(final StreamingHttpConnection.SettingKey<T> settingKey) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void close() {
            closed.set(true);
        }

        @Override
        public final boolean isClosed() {
            return closed.get();
        }
    }
}

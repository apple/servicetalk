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
import io.servicetalk.concurrent.api.CompletableProcessor;
import io.servicetalk.concurrent.api.Single;
import io.servicetalk.transport.api.ExecutionContext;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Function;

import static io.servicetalk.concurrent.api.Single.error;
import static java.util.Objects.requireNonNull;

public class BlockingStreamingHttpClientTest extends AbstractBlockingStreamingHttpRequesterTest {
    @SuppressWarnings("unchecked")
    @Override
    protected <T extends StreamingHttpRequester & TestHttpRequester> T newAsyncRequester(
            StreamingHttpRequestResponseFactory factory,
            final ExecutionContext ctx, final Function<StreamingHttpRequest, Single<StreamingHttpResponse>> doRequest) {
        return (T) new TestStreamingHttpClient(factory, ctx) {
            @Override
            public Single<StreamingHttpResponse> request(final StreamingHttpRequest request) {
                return doRequest.apply(request);
            }

            @Override
            public Single<? extends ReservedStreamingHttpConnection> reserveConnection(
                    final StreamingHttpRequest request) {
                return error(new UnsupportedOperationException());
            }

            @Override
            public Single<? extends UpgradableStreamingHttpResponse> upgradeConnection(
                    final StreamingHttpRequest request) {
                return error(new UnsupportedOperationException());
            }
        };
    }

    @SuppressWarnings("unchecked")
    @Override
    protected <T extends BlockingStreamingHttpRequester & TestHttpRequester> T newBlockingRequester(
            BlockingStreamingHttpRequestResponseFactory factory,
            final ExecutionContext ctx,
            final Function<BlockingStreamingHttpRequest, BlockingStreamingHttpResponse> doRequest) {
        return (T) new TestBlockingStreamingHttpClient(factory, ctx) {
            @Override
            public BlockingStreamingHttpResponse request(final BlockingStreamingHttpRequest request) {
                return doRequest.apply(request);
            }

            @Override
            public ReservedBlockingStreamingHttpConnection reserveConnection(
                    final BlockingStreamingHttpRequest request) {
                throw new UnsupportedOperationException();
            }

            @Override
            public UpgradableBlockingStreamingHttpResponse upgradeConnection(
                    final BlockingStreamingHttpRequest request) {
                throw new UnsupportedOperationException();
            }
        };
    }

    private abstract static class TestStreamingHttpClient extends StreamingHttpClient implements TestHttpRequester {
        private final AtomicBoolean closed = new AtomicBoolean();
        private final CompletableProcessor onClose = new CompletableProcessor();
        private final ExecutionContext executionContext;

        TestStreamingHttpClient(StreamingHttpRequestResponseFactory factory,
                                ExecutionContext executionContext) {
            super(factory);
            this.executionContext = requireNonNull(executionContext);
        }

        @Override
        public ExecutionContext executionContext() {
            return executionContext;
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
        public final boolean isClosed() {
            return closed.get();
        }
    }

    private abstract static class TestBlockingStreamingHttpClient extends BlockingStreamingHttpClient
                                                               implements TestHttpRequester {
        private final AtomicBoolean closed = new AtomicBoolean();
        private final ExecutionContext executionContext;

        TestBlockingStreamingHttpClient(BlockingStreamingHttpRequestResponseFactory factory,
                                        ExecutionContext executionContext) {
            super(factory);
            this.executionContext = requireNonNull(executionContext);
        }

        @Override
        public void close() {
            closed.set(true);
        }

        @Override
        public final boolean isClosed() {
            return closed.get();
        }

        @Override
        public ExecutionContext getExecutionContext() {
            return executionContext;
        }
    }
}

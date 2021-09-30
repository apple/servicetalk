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
package io.servicetalk.http.netty;

import io.servicetalk.concurrent.api.AsyncCloseable;
import io.servicetalk.concurrent.api.Completable;
import io.servicetalk.concurrent.api.ListenableAsyncCloseable;
import io.servicetalk.concurrent.api.Single;
import io.servicetalk.http.api.AbstractConditionalHttpFilterTest;
import io.servicetalk.http.api.HttpServiceContext;
import io.servicetalk.http.api.StreamingHttpRequest;
import io.servicetalk.http.api.StreamingHttpResponse;
import io.servicetalk.http.api.StreamingHttpResponseFactory;
import io.servicetalk.http.api.StreamingHttpService;
import io.servicetalk.http.api.StreamingHttpServiceFilter;
import io.servicetalk.http.api.StreamingHttpServiceFilterFactory;

import java.util.concurrent.atomic.AtomicBoolean;

import static io.servicetalk.concurrent.api.AsyncCloseables.emptyAsyncCloseable;
import static org.mockito.Mockito.mock;

public class ConditionalHttpServiceFilterTest extends AbstractConditionalHttpFilterTest {

    private static final class TestCondFilterFactory implements StreamingHttpServiceFilterFactory {
        private final AtomicBoolean closed;

        private TestCondFilterFactory(AtomicBoolean closed) {
            this.closed = closed;
        }

        @Override
        public StreamingHttpServiceFilter create(final StreamingHttpService service) {
            return new ConditionalHttpServiceFilter(TEST_REQ_PREDICATE,
                    new StreamingHttpServiceFilter(service) {
                        @Override
                        public Single<StreamingHttpResponse> handle(final HttpServiceContext ctx,
                                                                    final StreamingHttpRequest req,
                                                                    final StreamingHttpResponseFactory resFactory) {
                            return super.handle(ctx, markFiltered(req), resFactory);
                        }

                        @Override
                        public Completable closeAsync() {
                            return markClosed(closed, super.closeAsync());
                        }

                        @Override
                        public Completable closeAsyncGracefully() {
                            return markClosed(closed, super.closeAsyncGracefully());
                        }

                    }, service);
        }
    }

    private StreamingHttpService newService(AtomicBoolean closed) {
        return new TestStreamingHttpService(new TestCondFilterFactory(closed));
    }

    @Override
    protected Single<StreamingHttpResponse> sendTestRequest(final StreamingHttpRequest req) {
        return newService(new AtomicBoolean()).handle(mock(HttpServiceContext.class), req, REQ_RES_FACTORY);
    }

    @Override
    protected AsyncCloseable returnConditionallyFilteredResource(final AtomicBoolean closed) {
        return newService(closed);
    }

    private static final class TestStreamingHttpService implements StreamingHttpService {

        private final StreamingHttpServiceFilter filterChain;
        private final ListenableAsyncCloseable closeable = emptyAsyncCloseable();

        private TestStreamingHttpService(StreamingHttpServiceFilterFactory factory) {
            filterChain = factory.create(new StreamingHttpService() {
                @Override
                public Single<StreamingHttpResponse> handle(final HttpServiceContext ctx,
                                                            final StreamingHttpRequest request,
                                                            final StreamingHttpResponseFactory responseFactory) {
                    return TEST_REQ_HANDLER.apply(request, responseFactory);
                }

                @Override
                public Completable closeAsync() {
                    return closeable.closeAsync();
                }

                @Override
                public Completable closeAsyncGracefully() {
                    return closeable.closeAsyncGracefully();
                }
            });
        }

        @Override
        public Single<StreamingHttpResponse> handle(final HttpServiceContext ctx,
                                                    final StreamingHttpRequest req,
                                                    final StreamingHttpResponseFactory resFactory) {
            return filterChain.handle(ctx, req, resFactory);
        }

        @Override
        public Completable closeAsync() {
            return filterChain.closeAsync();
        }

        @Override
        public Completable closeAsyncGracefully() {
            return filterChain.closeAsyncGracefully();
        }
    }
}

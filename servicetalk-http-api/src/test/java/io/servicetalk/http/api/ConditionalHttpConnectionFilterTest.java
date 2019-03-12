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

import io.servicetalk.concurrent.api.AsyncCloseable;
import io.servicetalk.concurrent.api.Completable;
import io.servicetalk.concurrent.api.Single;
import io.servicetalk.transport.api.ConnectionContext;

import java.util.concurrent.atomic.AtomicBoolean;

import static org.mockito.Mockito.mock;

public class ConditionalHttpConnectionFilterTest extends AbstractConditionalHttpFilterTest {

    private static final HttpConnectionFilterFactory REQ_FILTER = conn -> new StreamingHttpConnectionFilter(conn) {
        @Override
        protected Single<StreamingHttpResponse> request(final StreamingHttpConnectionFilter delegate,
                                                        final HttpExecutionStrategy strategy,
                                                        final StreamingHttpRequest request) {
            return TEST_REQ_HANDLER.apply(request, httpResponseFactory());
        }
    };

    private static final class TestCondFilterFactory implements HttpConnectionFilterFactory {

        private final AtomicBoolean closed;

        private TestCondFilterFactory(AtomicBoolean closed) {
            this.closed = closed;
        }

        @Override
        public StreamingHttpConnectionFilter create(final StreamingHttpConnectionFilter connection) {
            return new ConditionalHttpConnectionFilter(TEST_REQ_PREDICATE,
                    new StreamingHttpConnectionFilter(connection) {
                @Override
                protected Single<StreamingHttpResponse> request(final StreamingHttpConnectionFilter delegate,
                                                                final HttpExecutionStrategy strategy,
                                                                final StreamingHttpRequest request) {
                    return delegate.request(strategy, markFiltered(request));
                }

                @Override
                public Completable closeAsync() {
                    return markClosed(closed, super.closeAsync());
                }

                @Override
                public Completable closeAsyncGracefully() {
                    return markClosed(closed, super.closeAsyncGracefully());
                }
            }, connection);
        }
    }

    private StreamingHttpConnection newConnection(AtomicBoolean closed) {
        return TestStreamingHttpConnection.from(REQ_RES_FACTORY, TEST_CTX, mock(ConnectionContext.class),
                new TestCondFilterFactory(closed).append(REQ_FILTER));
    }

    @Override
    protected Single<StreamingHttpResponse> sendTestRequest(final StreamingHttpRequest req) {
        return newConnection(new AtomicBoolean()).request(req);
    }

    @Override
    protected AsyncCloseable returnConditionallyFilteredResource(final AtomicBoolean closed) {
        return newConnection(closed);
    }
}

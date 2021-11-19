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
import io.servicetalk.concurrent.api.Single;
import io.servicetalk.http.api.FilterableStreamingHttpConnection;
import io.servicetalk.http.api.HttpConnectionContext;
import io.servicetalk.http.api.StreamingHttpConnection;
import io.servicetalk.http.api.StreamingHttpConnectionFilter;
import io.servicetalk.http.api.StreamingHttpConnectionFilterFactory;
import io.servicetalk.http.api.StreamingHttpRequest;
import io.servicetalk.http.api.StreamingHttpResponse;
import io.servicetalk.http.api.TestStreamingHttpConnection;

import java.util.concurrent.atomic.AtomicBoolean;

import static io.servicetalk.http.api.FilterFactoryUtils.appendConnectionFilterFactory;
import static org.mockito.Mockito.mock;

public class ConditionalHttpConnectionFilterTest extends AbstractConditionalHttpFilterTest {

    private static final StreamingHttpConnectionFilterFactory REQ_FILTER =
            conn -> new StreamingHttpConnectionFilter(conn) {
        @Override
        public Single<StreamingHttpResponse> request(final StreamingHttpRequest request) {
            return TEST_REQ_HANDLER.apply(request, delegate().httpResponseFactory());
        }
    };

    private static final class TestCondFilterFactory implements StreamingHttpConnectionFilterFactory {

        private final AtomicBoolean closed;

        private TestCondFilterFactory(AtomicBoolean closed) {
            this.closed = closed;
        }

        @Override
        public StreamingHttpConnectionFilter create(final FilterableStreamingHttpConnection connection) {
            return new ConditionalHttpConnectionFilter(TEST_REQ_PREDICATE,
                    new StreamingHttpConnectionFilter(connection) {
                @Override
                public Single<StreamingHttpResponse> request(final StreamingHttpRequest request) {
                    return delegate().request(markFiltered(request));
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

    private static StreamingHttpConnection newConnection(AtomicBoolean closed) {
        return TestStreamingHttpConnection.from(REQ_RES_FACTORY, testHttpExecutionContext(),
                mock(HttpConnectionContext.class),
                appendConnectionFilterFactory(new TestCondFilterFactory(closed), REQ_FILTER));
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

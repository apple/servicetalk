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

import io.servicetalk.concurrent.api.Single;
import io.servicetalk.transport.api.ConnectionContext;

import org.junit.Test;

import static org.mockito.Mockito.mock;

public class ConditionalHttpConnectionFilterTest extends AbstractConditionalHttpFilterTest {
    private final StreamingHttpConnection testConnection =
            new TestStreamingHttpConnection(REQ_RES_FACTORY, TEST_CTX, mock(ConnectionContext.class)) {
                @Override
                public Single<StreamingHttpResponse> request(final HttpExecutionStrategy __,
                                                             final StreamingHttpRequest req) {
                    return TEST_REQ_HANDLER.apply(req, httpResponseFactory());
                }
            };

    private final StreamingHttpConnectionFilter filter =
            new ConditionalHttpConnectionFilter(TEST_REQ_PREDICATE,
                    new StreamingHttpConnectionFilter(testConnection) {
                        @Override
                        public Single<StreamingHttpResponse> request(final HttpExecutionStrategy strategy,
                                                                     final StreamingHttpRequest req) {
                            return super.request(strategy, markFiltered(req));
                        }
                    },
                    testConnection);

    @Override
    protected Single<StreamingHttpResponse> sendTestRequest(final StreamingHttpRequest req) {
        return filter.request(req);
    }

    @Test
    public void closeAsyncImpactsBoth() throws Exception {
        StreamingHttpConnection predicateConnection =
                new TestStreamingHttpConnection(REQ_RES_FACTORY, TEST_CTX, mock(ConnectionContext.class));
        new ConditionalHttpConnectionFilter(req -> true, predicateConnection, testConnection)
                .closeAsync().toFuture().get();
        testConnection.onClose().toFuture().get();
        predicateConnection.onClose().toFuture().get();
    }

    @Test
    public void closeAsyncGracefullyImpactsBoth() throws Exception {
        StreamingHttpConnection predicateConnection =
                new TestStreamingHttpConnection(REQ_RES_FACTORY, TEST_CTX, mock(ConnectionContext.class));
        new ConditionalHttpConnectionFilter(req -> true, predicateConnection, testConnection)
                .closeAsyncGracefully().toFuture().get();
        testConnection.onClose().toFuture().get();
        predicateConnection.onClose().toFuture().get();
    }
}

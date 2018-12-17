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
package io.servicetalk.http.utils.filter;

import io.servicetalk.concurrent.api.Single;
import io.servicetalk.http.api.HttpExecutionStrategy;
import io.servicetalk.http.api.StreamingHttpConnection;
import io.servicetalk.http.api.StreamingHttpConnectionFilter;
import io.servicetalk.http.api.StreamingHttpRequest;
import io.servicetalk.http.api.StreamingHttpResponse;
import io.servicetalk.http.api.TestStreamingHttpConnection;
import io.servicetalk.transport.api.ConnectionContext;

import static org.mockito.Mockito.mock;

public class ConditionalHttpConnectionFilterTest extends AbstractConditionalHttpFilterTest {
    private static final StreamingHttpConnection TEST_CONNECTION =
            new TestStreamingHttpConnection(REQ_RES_FACTORY, TEST_CTX, mock(ConnectionContext.class)) {
                @Override
                public Single<StreamingHttpResponse> request(final HttpExecutionStrategy __,
                                                             final StreamingHttpRequest req) {
                    return TEST_REQ_HANDLER.apply(req, httpResponseFactory());
                }
            };

    private static final StreamingHttpConnectionFilter FILTER =
            new ConditionalHttpConnectionFilter(TEST_REQ_PREDICATE,
                    new StreamingHttpConnectionFilter(TEST_CONNECTION) {
                        @Override
                        public Single<StreamingHttpResponse> request(final HttpExecutionStrategy strategy,
                                                                     final StreamingHttpRequest req) {
                            return super.request(strategy, markFiltered(req));
                        }
                    },
                    TEST_CONNECTION);

    @Override
    protected Single<StreamingHttpResponse> sendTestRequest(final StreamingHttpRequest req) {
        return FILTER.request(req);
    }
}

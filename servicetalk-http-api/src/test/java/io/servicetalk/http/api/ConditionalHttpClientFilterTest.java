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

public class ConditionalHttpClientFilterTest extends AbstractConditionalHttpFilterTest {
    private static final StreamingHttpClient TEST_CLIENT = new TestStreamingHttpClient(REQ_RES_FACTORY, TEST_CTX) {
        @Override
        public Single<StreamingHttpResponse> request(final HttpExecutionStrategy __,
                                                     final StreamingHttpRequest req) {
            return TEST_REQ_HANDLER.apply(req, httpResponseFactory());
        }
    };

    private static final StreamingHttpClientFilter FILTER =
            new ConditionalHttpClientFilter(TEST_REQ_PREDICATE,
                    new StreamingHttpClientFilter(TEST_CLIENT) {
                        @Override
                        public Single<StreamingHttpResponse> request(final HttpExecutionStrategy strategy,
                                                                     final StreamingHttpRequest req) {
                            return super.request(strategy, markFiltered(req));
                        }
                    }, TEST_CLIENT);

    @Override
    protected Single<StreamingHttpResponse> sendTestRequest(final StreamingHttpRequest req) {
        return FILTER.request(req);
    }
}

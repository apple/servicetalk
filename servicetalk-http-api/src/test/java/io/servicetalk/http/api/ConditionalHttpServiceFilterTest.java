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

import static org.mockito.Mockito.mock;

public class ConditionalHttpServiceFilterTest extends AbstractConditionalHttpFilterTest {
    private static final StreamingHttpService TEST_SERVICE = new StreamingHttpService() {
        @Override
        public Single<StreamingHttpResponse> handle(final HttpServiceContext __,
                                                    final StreamingHttpRequest req,
                                                    final StreamingHttpResponseFactory resFactory) {
            return TEST_REQ_HANDLER.apply(req, resFactory);
        }
    };

    private static final StreamingHttpServiceFilter FILTER =
            new ConditionalHttpServiceFilter(TEST_REQ_PREDICATE,
                    service -> new StreamingHttpServiceFilter(service) {
                        @Override
                        public Single<StreamingHttpResponse> handle(final HttpServiceContext ctx,
                                                                    final StreamingHttpRequest req,
                                                                    final StreamingHttpResponseFactory resFactory) {
                            return super.handle(ctx, markFiltered(req), resFactory);
                        }
                    }, TEST_SERVICE);

    @Override
    protected Single<StreamingHttpResponse> sendTestRequest(final StreamingHttpRequest req) {
        return FILTER.handle(mock(HttpServiceContext.class), req, REQ_RES_FACTORY);
    }
}

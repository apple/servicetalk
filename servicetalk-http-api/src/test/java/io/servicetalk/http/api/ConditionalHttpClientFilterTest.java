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

import org.junit.Test;

public class ConditionalHttpClientFilterTest extends AbstractConditionalHttpFilterTest {
    private final StreamingHttpClient testClient = new TestStreamingHttpClient(REQ_RES_FACTORY, TEST_CTX) {
        @Override
        public Single<StreamingHttpResponse> request(final HttpExecutionStrategy __,
                                                     final StreamingHttpRequest req) {
            return TEST_REQ_HANDLER.apply(req, httpResponseFactory());
        }
    };

    private final StreamingHttpClientFilter filter =
            new ConditionalHttpClientFilter(TEST_REQ_PREDICATE,
                    new StreamingHttpClientFilter(testClient) {
                        @Override
                        protected Single<StreamingHttpResponse> request(final StreamingHttpRequester delegate,
                                                                        final HttpExecutionStrategy strategy,
                                                                        final StreamingHttpRequest request) {
                            return delegate.request(strategy, markFiltered(request));
                        }
                    }, testClient);

    @Override
    protected Single<StreamingHttpResponse> sendTestRequest(final StreamingHttpRequest req) {
        return filter.request(req);
    }

    @Test
    public void closeAsyncImpactsBoth() throws Exception {
        StreamingHttpClient predicateClient = new TestStreamingHttpClient(REQ_RES_FACTORY, TEST_CTX);
        new ConditionalHttpClientFilter(req -> true, predicateClient, testClient).closeAsync().toFuture().get();
        testClient.onClose().toFuture().get();
        predicateClient.onClose().toFuture().get();
    }

    @Test
    public void closeAsyncGracefullyImpactsBoth() throws Exception {
        StreamingHttpClient predicateClient = new TestStreamingHttpClient(REQ_RES_FACTORY, TEST_CTX);
        new ConditionalHttpClientFilter(req -> true, predicateClient, testClient)
                .closeAsyncGracefully().toFuture().get();
        testClient.onClose().toFuture().get();
        predicateClient.onClose().toFuture().get();
    }
}

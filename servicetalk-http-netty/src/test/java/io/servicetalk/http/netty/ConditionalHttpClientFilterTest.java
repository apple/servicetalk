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
import io.servicetalk.http.api.FilterableStreamingHttpClient;
import io.servicetalk.http.api.HttpExecutionStrategy;
import io.servicetalk.http.api.StreamingHttpClient;
import io.servicetalk.http.api.StreamingHttpClientFilter;
import io.servicetalk.http.api.StreamingHttpClientFilterFactory;
import io.servicetalk.http.api.StreamingHttpRequest;
import io.servicetalk.http.api.StreamingHttpRequester;
import io.servicetalk.http.api.StreamingHttpResponse;
import io.servicetalk.http.api.TestStreamingHttpClient;

import java.util.concurrent.atomic.AtomicBoolean;

import static io.servicetalk.http.api.FilterFactoryUtils.appendClientFilterFactory;

public class ConditionalHttpClientFilterTest extends AbstractConditionalHttpFilterTest {

    private static final StreamingHttpClientFilterFactory REQ_FILTER = client -> new StreamingHttpClientFilter(client) {
        @Override
        protected Single<StreamingHttpResponse> request(final StreamingHttpRequester delegate,
                                                        final HttpExecutionStrategy strategy,
                                                        final StreamingHttpRequest request) {
            return TEST_REQ_HANDLER.apply(request, delegate.httpResponseFactory());
        }
    };

    private static final class TestCondFilterFactory implements StreamingHttpClientFilterFactory {
        private final AtomicBoolean closed;

        private TestCondFilterFactory(AtomicBoolean closed) {
            this.closed = closed;
        }

        @Override
        public StreamingHttpClientFilter create(final FilterableStreamingHttpClient client) {
            return new ConditionalHttpClientFilter(TEST_REQ_PREDICATE, new StreamingHttpClientFilter(client) {
                @Override
                protected Single<StreamingHttpResponse> request(final StreamingHttpRequester delegate,
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
            }, client);
        }
    }

    public static StreamingHttpClient newClient(AtomicBoolean closed) {
        return TestStreamingHttpClient.from(REQ_RES_FACTORY, testHttpExecutionContext(),
                appendClientFilterFactory(new TestCondFilterFactory(closed), REQ_FILTER));
    }

    @Override
    protected Single<StreamingHttpResponse> sendTestRequest(final StreamingHttpRequest req) {
        return newClient(new AtomicBoolean()).request(req);
    }

    @Override
    protected AsyncCloseable returnConditionallyFilteredResource(final AtomicBoolean closed) {
        return newClient(closed);
    }
}

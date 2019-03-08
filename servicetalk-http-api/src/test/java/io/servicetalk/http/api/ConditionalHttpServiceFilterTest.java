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
import io.servicetalk.concurrent.api.ListenableAsyncCloseable;
import io.servicetalk.concurrent.api.Single;

import org.junit.Test;

import static io.servicetalk.concurrent.api.AsyncCloseables.emptyAsyncCloseable;
import static org.mockito.Mockito.mock;

public class ConditionalHttpServiceFilterTest extends AbstractConditionalHttpFilterTest {
    private final TestStreamingHttpService testService = new TestStreamingHttpService();

    private final StreamingHttpServiceFilter filter =
            new ConditionalHttpServiceFilter(TEST_REQ_PREDICATE,
                    new StreamingHttpServiceFilter(testService) {
                        @Override
                        public Single<StreamingHttpResponse> handle(final HttpServiceContext ctx,
                                                                    final StreamingHttpRequest req,
                                                                    final StreamingHttpResponseFactory resFactory) {
                            return super.handle(ctx, markFiltered(req), resFactory);
                        }
                    }, testService);

    @Override
    protected Single<StreamingHttpResponse> sendTestRequest(final StreamingHttpRequest req) {
        return filter.handle(mock(HttpServiceContext.class), req, REQ_RES_FACTORY);
    }

    @Test
    public void closeAsyncImpactsBoth() throws Exception {
        final TestStreamingHttpService predicateService = new TestStreamingHttpService();
        new ConditionalHttpServiceFilter(req -> true, predicateService, testService).closeAsync().toFuture().get();
        testService.onClose().toFuture().get();
        predicateService.onClose().toFuture().get();
    }

    @Test
    public void closeAsyncGracefullyImpactsBoth() throws Exception {
        final TestStreamingHttpService predicateService = new TestStreamingHttpService();
        new ConditionalHttpServiceFilter(req -> true, predicateService, testService)
                .closeAsyncGracefully().toFuture().get();
        testService.onClose().toFuture().get();
        predicateService.onClose().toFuture().get();
    }

    private static final class TestStreamingHttpService extends StreamingHttpService {
        private final ListenableAsyncCloseable closeable = emptyAsyncCloseable();

        @Override
        public Single<StreamingHttpResponse> handle(final HttpServiceContext __,
                                                    final StreamingHttpRequest req,
                                                    final StreamingHttpResponseFactory resFactory) {
            return TEST_REQ_HANDLER.apply(req, resFactory);
        }

        @Override
        public Completable closeAsync() {
            return closeable.closeAsync();
        }

        @Override
        public Completable closeAsyncGracefully() {
            return closeable.closeAsyncGracefully();
        }

        Completable onClose() {
            return closeable.onClose();
        }
    }
}

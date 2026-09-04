/*
 * Copyright © 2026 Apple Inc. and the ServiceTalk project authors
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
package io.servicetalk.http.utils;

import io.servicetalk.concurrent.api.Single;
import io.servicetalk.http.api.DefaultHttpHeadersFactory;
import io.servicetalk.http.api.DefaultStreamingHttpRequestResponseFactory;
import io.servicetalk.http.api.HttpExecutionContext;
import io.servicetalk.http.api.HttpServiceContext;
import io.servicetalk.http.api.StreamingHttpRequest;
import io.servicetalk.http.api.StreamingHttpRequestResponseFactory;
import io.servicetalk.http.api.StreamingHttpResponse;
import io.servicetalk.http.api.StreamingHttpResponseFactory;
import io.servicetalk.http.api.StreamingHttpService;
import io.servicetalk.http.api.TestHttpServiceContext;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.concurrent.atomic.AtomicReference;

import static io.servicetalk.buffer.netty.BufferAllocators.DEFAULT_ALLOCATOR;
import static io.servicetalk.http.api.HttpContextKeys.INTERRUPT_BLOCKING_SERVICE_ON_CANCEL;
import static io.servicetalk.http.api.HttpExecutionStrategies.offloadNone;
import static io.servicetalk.http.api.HttpProtocolVersion.HTTP_1_1;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.mockito.Mockito.lenient;

@ExtendWith(MockitoExtension.class)
class InterruptBlockingServiceOnCancelHttpServiceFilterTest {

    @Mock
    private HttpExecutionContext mockExecutionCtx;

    private final StreamingHttpRequestResponseFactory reqRespFactory = new DefaultStreamingHttpRequestResponseFactory(
            DEFAULT_ALLOCATOR, DefaultHttpHeadersFactory.INSTANCE, HTTP_1_1);
    private HttpServiceContext mockCtx;
    private final AtomicReference<Boolean> observed = new AtomicReference<>();
    private final StreamingHttpService delegate = new StreamingHttpService() {
        @Override
        public Single<StreamingHttpResponse> handle(final HttpServiceContext ctx,
                                                    final StreamingHttpRequest request,
                                                    final StreamingHttpResponseFactory responseFactory) {
            observed.set(request.context().get(INTERRUPT_BLOCKING_SERVICE_ON_CANCEL));
            return Single.succeeded(responseFactory.ok());
        }
    };

    @BeforeEach
    void setup() {
        lenient().when(mockExecutionCtx.bufferAllocator()).thenReturn(DEFAULT_ALLOCATOR);
        mockCtx = new TestHttpServiceContext(DefaultHttpHeadersFactory.INSTANCE, reqRespFactory, mockExecutionCtx);
    }

    @ParameterizedTest(name = "{displayName} [{index}] interrupt={0}")
    @ValueSource(booleans = {true, false})
    void setsValueOnRequestContext(boolean interrupt) throws Exception {
        new InterruptBlockingServiceOnCancelHttpServiceFilter(interrupt).create(delegate)
                .handle(mockCtx, reqRespFactory.get("/"), reqRespFactory).toFuture().get();

        assertThat(observed.get(), is(interrupt));
    }

    @Test
    void overwritesAValueAlreadyOnTheRequest() throws Exception {
        StreamingHttpRequest request = reqRespFactory.get("/");
        request.context().put(INTERRUPT_BLOCKING_SERVICE_ON_CANCEL, true);

        new InterruptBlockingServiceOnCancelHttpServiceFilter(false).create(delegate)
                .handle(mockCtx, request, reqRespFactory).toFuture().get();

        assertThat(observed.get(), is(false));
    }

    @Test
    void doesNotInfluenceOffloading() {
        assertThat(new InterruptBlockingServiceOnCancelHttpServiceFilter(false).requiredOffloads(),
                is(offloadNone()));
    }
}

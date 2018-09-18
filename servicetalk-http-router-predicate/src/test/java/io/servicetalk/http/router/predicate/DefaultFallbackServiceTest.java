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
package io.servicetalk.http.router.predicate;

import io.servicetalk.buffer.api.BufferAllocator;
import io.servicetalk.buffer.api.ReadOnlyBufferAllocators;
import io.servicetalk.concurrent.api.Single;
import io.servicetalk.http.api.DefaultHttpHeadersFactory;
import io.servicetalk.http.api.DefaultStreamingHttpRequestResponseFactory;
import io.servicetalk.http.api.HttpServiceContext;
import io.servicetalk.http.api.StreamingHttpRequest;
import io.servicetalk.http.api.StreamingHttpRequestResponseFactory;
import io.servicetalk.http.api.StreamingHttpResponse;
import io.servicetalk.http.api.StreamingHttpService;
import io.servicetalk.transport.api.ExecutionContext;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

import static io.servicetalk.concurrent.api.Executors.immediate;
import static io.servicetalk.concurrent.internal.Await.awaitIndefinitely;
import static io.servicetalk.http.api.HttpHeaderNames.CONTENT_LENGTH;
import static io.servicetalk.http.api.HttpHeaderNames.CONTENT_TYPE;
import static io.servicetalk.http.api.HttpHeaderValues.TEXT_PLAIN;
import static io.servicetalk.http.api.HttpHeaderValues.ZERO;
import static io.servicetalk.http.api.HttpProtocolVersions.HTTP_1_1;
import static io.servicetalk.http.api.HttpResponseStatuses.NOT_FOUND;
import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.when;

public class DefaultFallbackServiceTest {
    static final BufferAllocator allocator = ReadOnlyBufferAllocators.DEFAULT_RO_ALLOCATOR;
    static final StreamingHttpRequestResponseFactory reqRespFactory =
            new DefaultStreamingHttpRequestResponseFactory(allocator, DefaultHttpHeadersFactory.INSTANCE);
    @Rule
    public MockitoRule rule = MockitoJUnit.rule();

    @Mock
    private HttpServiceContext ctx;
    @Mock
    private ExecutionContext executionCtx;
    @Mock
    private StreamingHttpRequest request;

    @Before
    public void setUp() {
        when(request.getVersion()).thenReturn(HTTP_1_1);
        when(ctx.getExecutionContext()).thenReturn(executionCtx);
        when(executionCtx.getExecutor()).thenReturn(immediate());
    }

    @Test
    public void testDefaultFallbackService() throws Exception {
        final StreamingHttpService fixture = DefaultFallbackServiceStreaming.instance();

        final Single<StreamingHttpResponse> responseSingle = fixture.handle(ctx, request, reqRespFactory);

        final StreamingHttpResponse response = awaitIndefinitely(responseSingle);
        assert response != null;
        assertEquals(HTTP_1_1, response.getVersion());
        assertEquals(NOT_FOUND, response.getStatus());
        assertEquals(ZERO, response.getHeaders().get(CONTENT_LENGTH));
        assertEquals(TEXT_PLAIN, response.getHeaders().get(CONTENT_TYPE));
    }
}

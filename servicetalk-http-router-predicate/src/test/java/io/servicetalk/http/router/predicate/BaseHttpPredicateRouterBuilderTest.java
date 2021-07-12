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
import io.servicetalk.http.api.BlockingStreamingHttpResponseFactory;
import io.servicetalk.http.api.DefaultHttpHeadersFactory;
import io.servicetalk.http.api.DefaultStreamingHttpRequestResponseFactory;
import io.servicetalk.http.api.HttpExecutionContext;
import io.servicetalk.http.api.HttpHeaders;
import io.servicetalk.http.api.HttpResponseFactory;
import io.servicetalk.http.api.StreamingHttpRequest;
import io.servicetalk.http.api.StreamingHttpRequestResponseFactory;
import io.servicetalk.http.api.StreamingHttpResponse;
import io.servicetalk.http.api.StreamingHttpService;
import io.servicetalk.http.api.TestHttpServiceContext;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.mockito.stubbing.Answer;

import java.util.Iterator;
import java.util.Spliterator;

import static io.servicetalk.concurrent.api.Completable.completed;
import static io.servicetalk.concurrent.api.Executors.immediate;
import static io.servicetalk.http.api.HttpExecutionStrategies.defaultStrategy;
import static io.servicetalk.http.api.HttpProtocolVersion.HTTP_1_1;
import static java.util.Arrays.asList;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
public abstract class BaseHttpPredicateRouterBuilderTest {
    private static final BufferAllocator allocator = ReadOnlyBufferAllocators.DEFAULT_RO_ALLOCATOR;
    static final StreamingHttpRequestResponseFactory reqRespFactory =
            new DefaultStreamingHttpRequestResponseFactory(allocator, DefaultHttpHeadersFactory.INSTANCE, HTTP_1_1);

    @Mock(lenient = true)
    StreamingHttpService serviceA, serviceB, serviceC, serviceD, serviceE, fallbackService;
    @Mock(lenient = true)
    HttpExecutionContext executionCtx;
    @Mock(lenient = true)
    TestHttpServiceContext ctx;
    @Mock(lenient = true)
    StreamingHttpRequest request;
    @Mock
    HttpHeaders headers;
    @Mock
    Single<StreamingHttpResponse> responseA, responseB, responseC, responseD, responseE, fallbackResponse;

    @BeforeEach
    public void setUp() {
        when(executionCtx.executor()).thenReturn(immediate());
        when(executionCtx.executionStrategy()).thenReturn(defaultStrategy());

        when(ctx.executionContext()).thenReturn(executionCtx);
        when(ctx.headersFactory()).thenReturn(DefaultHttpHeadersFactory.INSTANCE);
        when(ctx.streamingResponseFactory()).thenReturn(reqRespFactory);
        when(ctx.responseFactory()).thenReturn(mock(HttpResponseFactory.class));
        when(ctx.blockingStreamingResponseFactory()).thenReturn(mock(BlockingStreamingHttpResponseFactory.class));

        when(request.version()).thenReturn(HTTP_1_1);
        when(request.headers()).thenReturn(headers);

        when(serviceA.handle(any(), eq(request), any())).thenReturn(responseA);
        when(serviceB.handle(any(), eq(request), any())).thenReturn(responseB);
        when(serviceC.handle(any(), eq(request), any())).thenReturn(responseC);
        when(serviceD.handle(any(), eq(request), any())).thenReturn(responseD);
        when(serviceE.handle(any(), eq(request), any())).thenReturn(responseE);
        when(fallbackService.handle(any(), eq(request), any())).thenReturn(fallbackResponse);

        when(serviceA.closeAsync()).thenReturn(completed());
        when(serviceB.closeAsync()).thenReturn(completed());
        when(serviceC.closeAsync()).thenReturn(completed());
        when(serviceD.closeAsync()).thenReturn(completed());
        when(serviceE.closeAsync()).thenReturn(completed());
        when(fallbackService.closeAsync()).thenReturn(completed());

        when(serviceA.closeAsyncGracefully()).thenReturn(completed());
        when(serviceB.closeAsyncGracefully()).thenReturn(completed());
        when(serviceC.closeAsyncGracefully()).thenReturn(completed());
        when(serviceD.closeAsyncGracefully()).thenReturn(completed());
        when(serviceE.closeAsyncGracefully()).thenReturn(completed());
        when(fallbackService.closeAsyncGracefully()).thenReturn(completed());
    }

    @SuppressWarnings("unchecked")
    <T> Answer<Iterator<T>> answerIteratorOf(final T... values) {
        return invocation -> asList(values).iterator();
    }

    @SuppressWarnings("unchecked")
    <T> Answer<Spliterator<T>> answerSpliteratorOf(final T... values) {
        return invocation -> asList(values).spliterator();
    }
}

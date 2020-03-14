/*
 * Copyright © 2020 Apple Inc. and the ServiceTalk project authors
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
package io.servicetalk.http.router.jaxrs;

import io.servicetalk.buffer.api.BufferAllocator;
import io.servicetalk.buffer.api.ReadOnlyBufferAllocators;
import io.servicetalk.concurrent.api.Single;
import io.servicetalk.concurrent.internal.ServiceTalkTestTimeout;
import io.servicetalk.http.api.BlockingStreamingHttpResponseFactory;
import io.servicetalk.http.api.DefaultHttpHeadersFactory;
import io.servicetalk.http.api.DefaultStreamingHttpRequestResponseFactory;
import io.servicetalk.http.api.HttpExecutionContext;
import io.servicetalk.http.api.HttpHeaders;
import io.servicetalk.http.api.HttpResponseFactory;
import io.servicetalk.http.api.HttpServiceContext;
import io.servicetalk.http.api.StreamingHttpRequest;
import io.servicetalk.http.api.StreamingHttpRequestResponseFactory;
import io.servicetalk.http.api.StreamingHttpResponse;
import io.servicetalk.http.api.StreamingHttpResponseFactory;
import io.servicetalk.http.api.StreamingHttpService;
import io.servicetalk.http.api.TestHttpServiceContext;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.junit.rules.Timeout;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;
import org.mockito.stubbing.Answer;

import java.util.Collections;
import java.util.Iterator;
import java.util.Set;
import java.util.Spliterator;
import javax.ws.rs.Path;
import javax.ws.rs.core.Application;

import static io.servicetalk.concurrent.api.Completable.completed;
import static io.servicetalk.concurrent.api.Executors.immediate;
import static io.servicetalk.http.api.HttpExecutionStrategies.defaultStrategy;
import static io.servicetalk.http.api.HttpProtocolVersion.HTTP_1_1;
import static java.util.Arrays.asList;
import static org.junit.Assert.assertSame;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class HttpJaxRsRouterBuilderTest {

    static final BufferAllocator allocator = ReadOnlyBufferAllocators.DEFAULT_RO_ALLOCATOR;
    static final StreamingHttpRequestResponseFactory reqRespFactory =
            new DefaultStreamingHttpRequestResponseFactory(allocator, DefaultHttpHeadersFactory.INSTANCE, HTTP_1_1);

    @Rule
    public final MockitoRule rule = MockitoJUnit.rule().silent();
    @Rule
    public final ExpectedException expected = ExpectedException.none();
    @Rule
    public final Timeout timeout = new ServiceTalkTestTimeout();

    @Mock
    StreamingHttpService serviceA, serviceB, serviceC, serviceD, serviceE, fallbackService;
    @Mock
    HttpExecutionContext executionCtx;
    @Mock
    TestHttpServiceContext ctx;
    @Mock
    StreamingHttpRequest request;
    @Mock
    HttpHeaders headers;
    @Mock
    Single<StreamingHttpResponse> responseA, responseB, responseC, responseD, responseE, fallbackResponse;

    @Before
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

    @Test
    public void test() {
        final TestResource resource = new TestResource();
        final Application application = new Application() {

            @Override
            public Set<Object> getSingletons() {

                return Collections.singleton(resource);
            }
        };

        final StreamingHttpService service = new HttpJaxRsRouterBuilder()
                .from(application);

        when(request.path("/all/a")).then(answerIteratorOf("a"));
        assertSame(responseA, service.handle(ctx, request, reqRespFactory));

        when(request.path("/all/b")).then(answerIteratorOf("b"));
        assertSame(responseB, service.handle(ctx, request, reqRespFactory));

        // when(request.queryParametersIterator("page")).thenReturn(emptyIterator());
        // assertSame(fallbackResponse, service.handle(ctx, request, reqRespFactory));
    }

    @Path("/all")
    public final class TestResource {

        @Path("/a")
        Single<StreamingHttpResponse> a(HttpServiceContext ctx, StreamingHttpRequest request,
                                             StreamingHttpResponseFactory responseFactory) {
            return responseA;
        }

        @Path("/b")
        Single<StreamingHttpResponse> b(HttpServiceContext ctx, StreamingHttpRequest request,
                                             StreamingHttpResponseFactory responseFactory) {
            return responseB;
        }
    }
}

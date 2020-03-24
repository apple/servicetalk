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

import io.servicetalk.buffer.api.Buffer;
import io.servicetalk.buffer.api.BufferAllocator;
import io.servicetalk.buffer.netty.BufferAllocators;
import io.servicetalk.concurrent.api.Publisher;
import io.servicetalk.concurrent.api.Single;
import io.servicetalk.concurrent.internal.ServiceTalkTestTimeout;
import io.servicetalk.data.jackson.JacksonSerializationProvider;
import io.servicetalk.http.api.BlockingStreamingHttpResponseFactory;
import io.servicetalk.http.api.DefaultHttpCookiePair;
import io.servicetalk.http.api.DefaultHttpHeadersFactory;
import io.servicetalk.http.api.DefaultStreamingHttpRequestResponseFactory;
import io.servicetalk.http.api.HttpExecutionContext;
import io.servicetalk.http.api.HttpHeaders;
import io.servicetalk.http.api.HttpRequestMethod;
import io.servicetalk.http.api.HttpResponseFactory;
import io.servicetalk.http.api.HttpSerializationProvider;
import io.servicetalk.http.api.HttpSerializationProviders;
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
import java.util.concurrent.ExecutionException;
import javax.ws.rs.CookieParam;
import javax.ws.rs.HeaderParam;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.core.Application;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.Cookie;

import static io.servicetalk.concurrent.api.Completable.completed;
import static io.servicetalk.concurrent.api.Executors.immediate;
import static io.servicetalk.concurrent.api.Single.succeeded;
import static io.servicetalk.http.api.HttpExecutionStrategies.defaultStrategy;
import static io.servicetalk.http.api.HttpHeaderNames.CONTENT_TYPE;
import static io.servicetalk.http.api.HttpProtocolVersion.HTTP_1_1;
import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.Arrays.asList;
import static javax.ws.rs.core.MediaType.APPLICATION_JSON;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.junit.Assert.assertSame;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class HttpJaxRsRouterBuilderTest {

    static final BufferAllocator allocator = BufferAllocators.DEFAULT_ALLOCATOR;
    static final StreamingHttpRequestResponseFactory reqRespFactory =
            new DefaultStreamingHttpRequestResponseFactory(allocator, DefaultHttpHeadersFactory.INSTANCE, HTTP_1_1);

    static final JacksonSerializationProvider jacksonSerializationProvider = new JacksonSerializationProvider();

    static HttpSerializationProvider jsonSerializer = HttpSerializationProviders
            .jsonSerializer(jacksonSerializationProvider);

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

    StreamingHttpService jaxRsRouter;

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

        final TestResource resource = new TestResource();
        final Application application = new Application() {

            @Override
            public Set<Object> getSingletons() {

                return Collections.singleton(resource);
            }
        };

        jaxRsRouter = new HttpJaxRsRouterBuilder()
                .from(application);
    }

    @SuppressWarnings("unchecked")
    <T> Answer<Iterator<T>> answerIteratorOf(final T... values) {
        return invocation -> asList(values).iterator();
    }

    @Test
    public void testPath() {

        when(request.path()).thenReturn("/all/a");
        when(request.method()).thenReturn(HttpRequestMethod.GET);
        assertSame(responseA, jaxRsRouter.handle(ctx, request, reqRespFactory));

        when(request.path()).thenReturn("/all/b");
        when(request.method()).thenReturn(HttpRequestMethod.GET);
        assertSame(responseB, jaxRsRouter.handle(ctx, request, reqRespFactory));
    }

    @Test
    public void testPathParam() {
        when(request.path()).thenReturn("/all/b/1");
        when(request.method()).thenReturn(HttpRequestMethod.GET);
        assertSame(responseC, jaxRsRouter.handle(ctx, request, reqRespFactory));

        when(request.path()).thenReturn("/all/c/1/abc");
        when(request.method()).thenReturn(HttpRequestMethod.GET);
        assertSame(responseD, jaxRsRouter.handle(ctx, request, reqRespFactory));
    }

    @Test
    public void testHeaderParam() {
        when(request.path()).thenReturn("/all/d");
        when(request.method()).thenReturn(HttpRequestMethod.GET);
        when(headers.valuesIterator("a")).then(answerIteratorOf("value"));
        assertSame(responseE, jaxRsRouter.handle(ctx, request, reqRespFactory));
    }

    @Test
    public void testCookieParam() {
        when(request.path()).thenReturn("/all/cookie");
        when(request.method()).thenReturn(HttpRequestMethod.GET);
        when(headers.getCookie(eq("a"))).thenReturn(new DefaultHttpCookiePair("a", "value"));
        assertSame(responseE, jaxRsRouter.handle(ctx, request, reqRespFactory));
    }

    @Test
    public void testBodyParam() throws InterruptedException, ExecutionException {
        when(request.path()).thenReturn("/all/e");
        when(request.method()).thenReturn(HttpRequestMethod.POST);
        when(headers.valuesIterator(CONTENT_TYPE)).then(answerIteratorOf(APPLICATION_JSON));
        when(headers.get(eq(CONTENT_TYPE))).thenReturn(APPLICATION_JSON);

        Model model = new Model();
        model.name = "abc";

        final Buffer buffer = allocator.newBuffer();
        jacksonSerializationProvider.getSerializer(Model.class).serialize(model, buffer);
        final CharSequence modelJson = buffer.toString(UTF_8);
        when(request.payloadBody()).thenReturn(succeeded(allocator.fromAscii(modelJson)).toPublisher());

        final Single<StreamingHttpResponse> responseSingle = jaxRsRouter.handle(ctx, request, reqRespFactory);

        final StreamingHttpResponse response = responseSingle.toFuture().get();

        final Model resultModel = response.payloadBody(jsonSerializer.deserializerFor(Model.class))
                .firstOrError().toFuture().get();

        assertThat(model.name, equalTo(resultModel.name));
    }

    @Path("/all")
    public final class TestResource {

        @Path("/a")
        Single<StreamingHttpResponse> a() {
            return responseA;
        }

        @Path("/b")
        Single<StreamingHttpResponse> b() {
            return responseB;
        }

        @Path("/b/{c}")
        Single<StreamingHttpResponse> param(@PathParam("c") String c) {
            return responseC;
        }

        @Path("/c/{a}/{b}")
        Single<StreamingHttpResponse> paramAb(@PathParam("a") String a, @PathParam("b") String b) {
            return responseD;
        }

        @Path("/d")
        Single<StreamingHttpResponse> header(@HeaderParam("a") String a) {
            return responseE;
        }

        @Path("/cookie")
        Single<StreamingHttpResponse> cookie(@CookieParam("a") Cookie cookie) {
            return responseE;
        }

        @POST
        @Path("/e")
        Single<StreamingHttpResponse> body(@Context StreamingHttpResponseFactory responseFactory,
                                           Publisher<Model> modelPublisher) {
            return succeeded(responseFactory.ok().payloadBody(modelPublisher,
                    jsonSerializer.serializerFor(Model.class)));
        }
    }

    public static class Model {
        private String name;

        public Model() {
        }

        public String getName() {
            return name;
        }
    }
}

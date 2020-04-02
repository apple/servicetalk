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
import io.servicetalk.http.api.HttpSerializer;
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
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import javax.ws.rs.Consumes;
import javax.ws.rs.CookieParam;
import javax.ws.rs.DefaultValue;
import javax.ws.rs.HeaderParam;
import javax.ws.rs.POST;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.Application;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.Cookie;

import static io.servicetalk.concurrent.api.Executors.immediate;
import static io.servicetalk.concurrent.api.Single.succeeded;
import static io.servicetalk.http.api.CharSequences.contentEqualsIgnoreCase;
import static io.servicetalk.http.api.HttpExecutionStrategies.defaultStrategy;
import static io.servicetalk.http.api.HttpHeaderNames.CONTENT_TYPE;
import static io.servicetalk.http.api.HttpProtocolVersion.HTTP_1_1;
import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.Arrays.asList;
import static java.util.Collections.emptyList;
import static javax.ws.rs.core.MediaType.APPLICATION_JSON;
import static javax.ws.rs.core.MediaType.TEXT_PLAIN;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.mockito.ArgumentMatchers.argThat;
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

    static HttpSerializer<String> textSerializer = HttpSerializationProviders
            .textSerializer();

    @Rule
    public final MockitoRule rule = MockitoJUnit.rule().silent();
    @Rule
    public final ExpectedException expected = ExpectedException.none();
    @Rule
    public final Timeout timeout = new ServiceTalkTestTimeout();
    @Mock
    HttpExecutionContext executionCtx;
    @Mock
    TestHttpServiceContext ctx;
    @Mock
    StreamingHttpRequest request;
    @Mock
    HttpHeaders headers;

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

    @Test
    public void testPath() {
        when(request.path()).thenReturn("/all/a");
        when(request.method()).thenReturn(HttpRequestMethod.GET);
        when(headers.values(argThat(argument -> contentEqualsIgnoreCase(argument, CONTENT_TYPE))))
                .thenAnswer(answerIterableOf(APPLICATION_JSON));

        assertThat("a", equalTo(makeRequest()));

        when(request.path()).thenReturn("/all/b");
        when(request.method()).thenReturn(HttpRequestMethod.GET);
        assertThat("b", equalTo(makeRequest()));
    }

    @Test
    public void testPathParam() {
        when(request.path()).thenReturn("/all/b/1");
        when(request.method()).thenReturn(HttpRequestMethod.GET);
        when(headers.values(argThat(argument -> contentEqualsIgnoreCase(argument, CONTENT_TYPE))))
                .thenAnswer(answerIterableOf(APPLICATION_JSON));

        assertThat("1", equalTo(makeRequest()));
    }

    @Test
    public void testPathParams() {
        when(request.path()).thenReturn("/all/c/1/abc");
        when(request.method()).thenReturn(HttpRequestMethod.GET);
        when(headers.values(argThat(argument -> contentEqualsIgnoreCase(argument, CONTENT_TYPE))))
                .thenAnswer(answerIterableOf(APPLICATION_JSON));

        assertThat("1abc", equalTo(makeRequest()));
    }

    @Test
    public void testIntPathParams() {
        when(request.path()).thenReturn("/all/h/1/2");
        when(request.method()).thenReturn(HttpRequestMethod.GET);
        when(headers.values(argThat(argument -> contentEqualsIgnoreCase(argument, CONTENT_TYPE))))
                .thenAnswer(answerIterableOf(APPLICATION_JSON));

        assertThat("12", equalTo(makeRequest()));
    }

    private String makeRequest() {
        final Single<StreamingHttpResponse> responseSingle = jaxRsRouter.handle(ctx, request, reqRespFactory);

        try {
            final StreamingHttpResponse response = responseSingle.toFuture().get();

            return response.payloadBody().map(buffer -> buffer.toString(UTF_8))
                    .firstOrError().toFuture().get();
        } catch (InterruptedException | ExecutionException e) {
            throw new RuntimeException(e);
        }
    }

    @Test
    public void testHeaderParam() {
        when(request.path()).thenReturn("/all/d");
        when(request.method()).thenReturn(HttpRequestMethod.GET);
        when(headers.valuesIterator(eq("a"))).then(answerIteratorOf("value"));
        when(headers.values(argThat(argument -> contentEqualsIgnoreCase(argument, CONTENT_TYPE))))
                .thenAnswer(answerIterableOf(APPLICATION_JSON));

        assertThat("value", equalTo(makeRequest()));
    }

    @Test
    public void testHeaderContentType() {
        when(request.path()).thenReturn("/all/text");
        when(request.method()).thenReturn(HttpRequestMethod.PUT);
        when(headers.values(argThat(argument -> contentEqualsIgnoreCase(argument, CONTENT_TYPE))))
                .thenAnswer(answerIterableOf(TEXT_PLAIN));

        assertThat("1", equalTo(makeRequest()));
    }

    @Test
    public void testCookieParam() {
        when(request.path()).thenReturn("/all/cookie");
        when(request.method()).thenReturn(HttpRequestMethod.GET);
        when(headers.getCookie(eq("a"))).thenReturn(new DefaultHttpCookiePair("a", "value"));
        when(headers.values(argThat(argument -> contentEqualsIgnoreCase(argument, CONTENT_TYPE))))
                .thenAnswer(answerIterableOf(APPLICATION_JSON));

        assertThat("value", equalTo(makeRequest()));
    }

    @Test
    public void testQueryParam() {
        when(request.path()).thenReturn("/all/query");
        when(headers.values(argThat(argument -> contentEqualsIgnoreCase(argument, CONTENT_TYPE))))
                .thenAnswer(answerIterableOf(APPLICATION_JSON));
        when(request.queryParameters(eq("a"))).thenAnswer(answerIterableOf("123"));
        when(request.queryParameters(eq("b"))).thenAnswer(answerIterableOf("1"));
        when(request.queryParameters()).thenAnswer(answerIterableMapOf("a", "123"));
        when(request.queryParameters()).thenAnswer(answerIterableMapOf("b", "1"));
        when(request.method()).thenReturn(HttpRequestMethod.GET);
        assertThat("1231", equalTo(makeRequest()));
    }

    @Test
    public void testQueryParamDefaultValue() {
        when(request.path()).thenReturn("/all/queryDefault");
        when(headers.values(argThat(argument -> contentEqualsIgnoreCase(argument, CONTENT_TYPE))))
                .thenAnswer(answerIterableOf(APPLICATION_JSON));
        when(request.queryParameters(eq("a"))).thenReturn(emptyList());
        when(request.queryParameters(eq("b"))).thenReturn(emptyList());
        when(request.queryParameters()).thenReturn(Collections.<String, String>emptyMap().entrySet());
        when(request.method()).thenReturn(HttpRequestMethod.GET);
        assertThat("01", equalTo(makeRequest()));
    }

    @Test
    public void testBodyParam() throws InterruptedException, ExecutionException {
        when(request.path()).thenReturn("/all/e");
        when(request.method()).thenReturn(HttpRequestMethod.POST);
        when(headers.values(CONTENT_TYPE)).then(answerIterableOf(APPLICATION_JSON));
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

    static <T> Answer<Iterable<T>> answerIterableOf(final T... values) {
        return invocation -> asList(values);
    }

    static <T> Answer<Iterator<T>> answerIteratorOf(final T... values) {
        return invocation -> asList(values).iterator();
    }

    static <T> Answer<Iterable<Map.Entry<T, T>>> answerIterableMapOf(T... keyValues) {
        return invocation -> {
            Map<T, T> queryMap = new HashMap<>();
            T key = null;
            for (int i = 0; i < keyValues.length; i++) {
                if (i % 2 == 0) {
                    key = keyValues[i];
                } else {
                    queryMap.put(key, keyValues[i]);
                    key = null;
                }
            }

            return queryMap.entrySet();
        };
    }

    @Path("/all")
    @Consumes(value = APPLICATION_JSON)
    public static final class TestResource {

        @Path("/a")
        Single<StreamingHttpResponse> a(@Context StreamingHttpResponseFactory responseFactory) {
            return buildStringResponse("a", responseFactory);
        }

        @Path("/b")
        Single<StreamingHttpResponse> b(@Context StreamingHttpResponseFactory responseFactory) {
            return buildStringResponse("b", responseFactory);
        }

        @Path("/b/{c}")
        Single<StreamingHttpResponse> param(@PathParam("c") String c,
                                            @Context StreamingHttpResponseFactory responseFactory) {
            return buildStringResponse(c, responseFactory);
        }

        @Path("/c/{a}/{b}")
        Single<StreamingHttpResponse> paramAb(@PathParam("a") String a, @PathParam("b") String b,
                                              @Context StreamingHttpResponseFactory responseFactory) {
            return buildStringResponse(a + b, responseFactory);
        }

        @Path("/h/{a}/{b}")
        Single<StreamingHttpResponse> paramAb(@PathParam("a") int a, @PathParam("b") int b,
                                              @Context StreamingHttpResponseFactory responseFactory) {
            return buildStringResponse("" + a + b, responseFactory);
        }

        @Path("/d")
        Single<StreamingHttpResponse> header(@HeaderParam("a") CharSequence a,
                                             @Context StreamingHttpResponseFactory responseFactory) {
            return buildStringResponse(a.toString(), responseFactory);
        }

        @Path("/cookie")
        Single<StreamingHttpResponse> cookie(@CookieParam("a") Cookie cookie,
                                             @Context StreamingHttpResponseFactory responseFactory) {
            return buildStringResponse(cookie.getValue(), responseFactory);
        }

        @Path("/query")
        Single<StreamingHttpResponse> query(@QueryParam("a") String a,
                                            @QueryParam("b") int b,
                                            @Context StreamingHttpResponseFactory responseFactory) {
            return buildStringResponse(a + b, responseFactory);
        }

        @Path("/queryDefault")
        Single<StreamingHttpResponse> queryDefault(@QueryParam("a") @DefaultValue("0") String a,
                                                   @QueryParam("b") @DefaultValue("1") int b,
                                                   @Context StreamingHttpResponseFactory responseFactory) {
            return buildStringResponse(a + b, responseFactory);
        }

        @Path("/text")
        Single<StreamingHttpResponse> text(@Context StreamingHttpResponseFactory responseFactory) {
            return buildStringResponse("1", responseFactory);
        }

        @POST
        @Path("/e")
        Single<StreamingHttpResponse> body(@Context StreamingHttpResponseFactory responseFactory,
                                           Publisher<Model> modelPublisher) {
            return succeeded(responseFactory.ok().payloadBody(modelPublisher,
                    jsonSerializer.serializerFor(Model.class)));
        }

        @PUT
        @Consumes(value = {TEXT_PLAIN})
        @Path("/text")
        Single<StreamingHttpResponse> contentType(@Context StreamingHttpResponseFactory responseFactory) {
            return succeeded(responseFactory.ok().payloadBody(succeeded("1").toPublisher(), textSerializer));
        }
    }

    private static Single<StreamingHttpResponse> buildStringResponse(
            final String c,
            final StreamingHttpResponseFactory responseFactory) {
        return succeeded(responseFactory.ok().payloadBody(succeeded(allocator.fromAscii(c)).toPublisher()));
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

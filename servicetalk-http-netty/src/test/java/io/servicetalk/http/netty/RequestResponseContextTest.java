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

import io.servicetalk.buffer.api.Buffer;
import io.servicetalk.concurrent.api.Single;
import io.servicetalk.context.api.ContextMap;
import io.servicetalk.http.api.BlockingHttpClient;
import io.servicetalk.http.api.BlockingHttpService;
import io.servicetalk.http.api.BlockingStreamingHttpClient;
import io.servicetalk.http.api.BlockingStreamingHttpRequest;
import io.servicetalk.http.api.BlockingStreamingHttpResponse;
import io.servicetalk.http.api.HttpClient;
import io.servicetalk.http.api.HttpExecutionStrategy;
import io.servicetalk.http.api.HttpPayloadWriter;
import io.servicetalk.http.api.HttpRequest;
import io.servicetalk.http.api.HttpRequestMetaData;
import io.servicetalk.http.api.HttpResponse;
import io.servicetalk.http.api.HttpResponseMetaData;
import io.servicetalk.http.api.HttpService;
import io.servicetalk.http.api.HttpServiceContext;
import io.servicetalk.http.api.StreamingHttpClient;
import io.servicetalk.http.api.StreamingHttpConnectionFilter;
import io.servicetalk.http.api.StreamingHttpRequest;
import io.servicetalk.http.api.StreamingHttpResponse;
import io.servicetalk.http.api.StreamingHttpResponseFactory;
import io.servicetalk.http.api.StreamingHttpService;
import io.servicetalk.http.api.StreamingHttpServiceFilter;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Queue;
import java.util.concurrent.LinkedBlockingQueue;
import javax.annotation.Nullable;

import static io.servicetalk.buffer.api.Matchers.contentEqualTo;
import static io.servicetalk.concurrent.api.Single.succeeded;
import static io.servicetalk.context.api.ContextMap.Key.newKey;
import static io.servicetalk.http.api.HttpApiConversions.toStreamingHttpService;
import static io.servicetalk.http.api.HttpExecutionStrategies.anyStrategy;
import static io.servicetalk.http.api.HttpProtocolVersion.HTTP_1_1;
import static io.servicetalk.http.api.HttpResponseStatus.OK;
import static io.servicetalk.http.netty.AbstractNettyHttpServerTest.ExecutorSupplier.CACHED;
import static io.servicetalk.http.netty.AbstractNettyHttpServerTest.ExecutorSupplier.CACHED_SERVER;
import static io.servicetalk.http.netty.TestServiceStreaming.SVC_ECHO;
import static io.servicetalk.test.resources.TestUtils.assertNoAsyncErrors;
import static java.lang.String.valueOf;
import static java.util.Collections.singletonList;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;

class RequestResponseContextTest extends AbstractNettyHttpServerTest {

    private static final ContextMap.Key<List<String>> CLIENT_REQUEST_KEY =
            newKey("CLIENT_REQUEST_KEY", generify(List.class));
    private static final ContextMap.Key<String> CLIENT_RESPONSE_KEY = newKey("CLIENT_RESPONSE_KEY", String.class);
    private static final ContextMap.Key<List<String>> SERVER_REQUEST_KEY =
            newKey("SERVER_REQUEST_KEY", generify(List.class));
    private static final ContextMap.Key<String> SERVER_RESPONSE_KEY = newKey("SERVER_RESPONSE_KEY", String.class);
    private static final String REQUEST_CONTEXT_VALUE = "REQUEST_CONTEXT_VALUE";
    private static final String RESPONSE_CONTEXT_VALUE = "RESPONSE_CONTEXT_VALUE";
    private static final String CONTEXT_HEADER = "context-header";

    private enum Api {
        AsyncAggregated,
        AsyncStreaming,
        BlockingAggregated,
        BlockingStreaming,
    }

    private final Queue<Throwable> asyncError = new LinkedBlockingQueue<>();
    @Nullable
    private Api api;

    void setUp(Api api) {
        this.api = api;
        connectionFilterFactory(connection -> new StreamingHttpConnectionFilter(connection) {
            @Override
            public Single<StreamingHttpResponse> request(HttpExecutionStrategy strategy,
                                                         StreamingHttpRequest request) {
                final List<String> requestCtxValue = request.context().get(CLIENT_REQUEST_KEY);
                try {
                    assertThat(requestCtxValue, is(notNullValue()));
                    assertThat(requestCtxValue, hasSize(1));
                    assertThat(requestCtxValue, contains(REQUEST_CONTEXT_VALUE));
                    request.addHeader(CONTEXT_HEADER, requestCtxValue.get(0));
                } catch (Throwable t) {
                    asyncError.add(t);
                }
                return delegate().request(strategy, request).map(response -> {
                    response.context().put(CLIENT_RESPONSE_KEY, valueOf(response.headers().get(CONTEXT_HEADER)));
                    return response;
                });
            }
        });
        serviceFilterFactory(service -> new StreamingHttpServiceFilter(service) {
            @Override
            public Single<StreamingHttpResponse> handle(HttpServiceContext ctx,
                                                        StreamingHttpRequest request,
                                                        StreamingHttpResponseFactory responseFactory) {
                CharSequence requestCtxValue = request.headers().get(CONTEXT_HEADER);
                try {
                    assertThat(requestCtxValue, is(notNullValue()));
                    assertThat(requestCtxValue, is(contentEqualTo(REQUEST_CONTEXT_VALUE)));
                    request.context().put(SERVER_REQUEST_KEY, singletonList(requestCtxValue.toString()));
                } catch (Throwable t) {
                    asyncError.add(t);
                }
                return delegate().handle(ctx, request, responseFactory).map(response -> {
                    response.headers().add(CONTEXT_HEADER, valueOf(response.context().get(SERVER_RESPONSE_KEY)));
                    return response;
                });
            }
        });
        setUp(CACHED, CACHED_SERVER);
    }

    @Override
    void service(final StreamingHttpService service) {
        final StreamingHttpService newService;
        assert api != null;
        switch (api) {
            case AsyncAggregated:
                newService = toStreamingHttpService((HttpService) (ctx, request, responseFactory) -> {
                    HttpResponse response = responseFactory.ok().payloadBody(request.payloadBody());
                    transferContext(request, response);
                    return succeeded(response);
                }, anyStrategy()).adaptor();
                break;
            case AsyncStreaming:
                newService = (ctx, request, responseFactory) -> {
                    StreamingHttpResponse response = responseFactory.ok().payloadBody(request.payloadBody());
                    transferContext(request, response);
                    return succeeded(response);
                };
                break;
            case BlockingAggregated:
                newService = toStreamingHttpService((BlockingHttpService) (ctx, request, responseFactory) -> {
                    HttpResponse response = responseFactory.ok().payloadBody(request.payloadBody());
                    transferContext(request, response);
                    return response;
                }, anyStrategy()).adaptor();
                break;
            case BlockingStreaming:
                newService = toStreamingHttpService((ctx, request, response) -> {
                    transferContext(request, response);
                    try (HttpPayloadWriter<Buffer> writer = response.sendMetaData()) {
                        for (Buffer chunk : request.payloadBody()) {
                            writer.write(chunk);
                        }
                    }
                }, anyStrategy()).adaptor();
                break;
            default:
                throw new IllegalStateException("Unknown api: " + api);
        }
        super.service(newService);
    }

    void transferContext(HttpRequestMetaData request, HttpResponseMetaData response) {
        try {
            List<String> requestCtxValue = request.context().get(SERVER_REQUEST_KEY);
            assertThat(requestCtxValue, is(notNullValue()));
            response.context().put(SERVER_RESPONSE_KEY, requestCtxValue.get(0) + '-' + RESPONSE_CONTEXT_VALUE);
        } catch (Throwable t) {
            asyncError.add(t);
        }
    }

    @Test
    void testAsyncAggregated() throws Exception {
        setUp(Api.AsyncAggregated);
        HttpClient client = streamingHttpClient().asClient();
        HttpRequest request = client.get(SVC_ECHO);
        request.context().put(CLIENT_REQUEST_KEY, singletonList(REQUEST_CONTEXT_VALUE));
        HttpResponse response = client.request(request).toFuture().get();
        assertResponse(response.toStreamingResponse(), HTTP_1_1, OK, 0);
        assertContext(request, response);
    }

    @Test
    void testAsyncStreaming() throws Exception {
        setUp(Api.AsyncStreaming);
        StreamingHttpClient client = streamingHttpClient();
        StreamingHttpRequest request = client.get(SVC_ECHO);
        request.context().put(CLIENT_REQUEST_KEY, singletonList(REQUEST_CONTEXT_VALUE));
        StreamingHttpResponse response = makeRequest(request);
        assertResponse(response, HTTP_1_1, OK, 0);
        assertContext(request, response);
    }

    @Test
    void testBlockingAggregated() throws Exception {
        setUp(Api.BlockingAggregated);
        BlockingHttpClient client = streamingHttpClient().asBlockingClient();
        HttpRequest request = client.get(SVC_ECHO);
        request.context().put(CLIENT_REQUEST_KEY, singletonList(REQUEST_CONTEXT_VALUE));
        HttpResponse response = client.request(request);
        assertResponse(response.toStreamingResponse(), HTTP_1_1, OK, 0);
        assertContext(request, response);
    }

    @Test
    void testBlockingStreaming() throws Exception {
        setUp(Api.BlockingStreaming);
        BlockingStreamingHttpClient client = streamingHttpClient().asBlockingStreamingClient();
        BlockingStreamingHttpRequest request = client.get(SVC_ECHO);
        request.context().put(CLIENT_REQUEST_KEY, singletonList(REQUEST_CONTEXT_VALUE));
        BlockingStreamingHttpResponse response = client.request(request);
        assertResponse(response.toStreamingResponse(), HTTP_1_1, OK, 0);
        assertContext(request, response);
    }

    void assertContext(HttpRequestMetaData request, HttpResponseMetaData response) {
        assertNoAsyncErrors("Unexpected context propagation", asyncError);
        assertThat("Unexpected request context after exchange: " + request.context(),
                request.context().get(CLIENT_REQUEST_KEY), contains(contentEqualTo(REQUEST_CONTEXT_VALUE)));
        assertThat("Unexpected response context after exchange: " + response.context(),
                response.context().get(CLIENT_RESPONSE_KEY),
                is(contentEqualTo(REQUEST_CONTEXT_VALUE + '-' + RESPONSE_CONTEXT_VALUE)));
    }

    @SuppressWarnings("unchecked")
    private static <T> Class<T> generify(Class<?> clazz) {
        return (Class<T>) clazz;
    }
}

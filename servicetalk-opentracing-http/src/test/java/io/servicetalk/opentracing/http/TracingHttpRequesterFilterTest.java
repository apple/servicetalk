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
package io.servicetalk.opentracing.http;

import io.servicetalk.concurrent.internal.ServiceTalkTestTimeout;
import io.servicetalk.data.jackson.JacksonSerializationProvider;
import io.servicetalk.http.api.HttpClient;
import io.servicetalk.http.api.HttpRequest;
import io.servicetalk.http.api.HttpResponse;
import io.servicetalk.http.api.HttpSerializationProvider;
import io.servicetalk.http.netty.HttpServers;
import io.servicetalk.opentracing.asynccontext.AsyncContextInMemoryScopeManager;
import io.servicetalk.opentracing.http.TestUtils.CountingInMemorySpanEventListener;
import io.servicetalk.opentracing.inmemory.DefaultInMemoryTracer;
import io.servicetalk.opentracing.inmemory.api.InMemoryScope;
import io.servicetalk.opentracing.inmemory.api.InMemorySpan;
import io.servicetalk.transport.api.ServerContext;

import io.opentracing.Tracer;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.junit.rules.Timeout;
import org.mockito.Mock;

import java.net.InetSocketAddress;
import java.util.concurrent.ExecutionException;
import javax.annotation.Nullable;

import static io.opentracing.tag.Tags.ERROR;
import static io.opentracing.tag.Tags.HTTP_METHOD;
import static io.opentracing.tag.Tags.HTTP_STATUS;
import static io.opentracing.tag.Tags.HTTP_URL;
import static io.opentracing.tag.Tags.SPAN_KIND;
import static io.opentracing.tag.Tags.SPAN_KIND_CLIENT;
import static io.servicetalk.concurrent.api.Publisher.just;
import static io.servicetalk.concurrent.api.Single.success;
import static io.servicetalk.concurrent.internal.DeliberateException.DELIBERATE_EXCEPTION;
import static io.servicetalk.http.api.HttpRequestMethods.GET;
import static io.servicetalk.http.api.HttpResponseStatuses.OK;
import static io.servicetalk.http.api.HttpSerializationProviders.jsonSerializer;
import static io.servicetalk.http.netty.HttpClients.forSingleAddress;
import static io.servicetalk.opentracing.http.TestUtils.isHexId;
import static io.servicetalk.opentracing.internal.utils.ZipkinHeaderNames.PARENT_SPAN_ID;
import static io.servicetalk.opentracing.internal.utils.ZipkinHeaderNames.SAMPLED;
import static io.servicetalk.opentracing.internal.utils.ZipkinHeaderNames.SPAN_ID;
import static io.servicetalk.opentracing.internal.utils.ZipkinHeaderNames.TRACE_ID;
import static io.servicetalk.transport.api.HostAndPort.of;
import static java.lang.String.valueOf;
import static org.hamcrest.Matchers.equalToIgnoringCase;
import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThat;
import static org.junit.rules.ExpectedException.none;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.mockito.MockitoAnnotations.initMocks;

public class TracingHttpRequesterFilterTest {
    private static final HttpSerializationProvider httpSerializer = jsonSerializer(new JacksonSerializationProvider());

    @Rule
    public final Timeout timeout = new ServiceTalkTestTimeout();
    @Rule
    public final ExpectedException expected = none();

    @Mock
    private Tracer mockTracer;

    @Before
    public void setup() {
        initMocks(this);
    }

    @Test
    public void testInjectWithNoParent() throws Exception {
        final String requestUrl = "/";
        CountingInMemorySpanEventListener spanListener = new CountingInMemorySpanEventListener();
        DefaultInMemoryTracer tracer = new DefaultInMemoryTracer.Builder(
                new AsyncContextInMemoryScopeManager()).addListener(spanListener).build();
        try (ServerContext context = buildServer()) {
            try (HttpClient client = forSingleAddress(of((InetSocketAddress) context.listenAddress()))
                    .appendConnectionFilter(new TracingHttpRequesterFilter(tracer, "testClient")).build()) {
                HttpResponse response = client.request(client.get(requestUrl)).toFuture().get();
                TestSpanState serverSpanState = response.payloadBody(httpSerializer.deserializerFor(
                        TestSpanState.class));

                assertThat(serverSpanState.traceId, isHexId());
                assertThat(serverSpanState.spanId, isHexId());
                assertNull(serverSpanState.parentSpanId);

                // don't mess with caller span state
                assertNull(tracer.activeSpan());

                assertEquals(1, spanListener.spanFinishedCount());
                InMemorySpan lastFinishedSpan = spanListener.lastFinishedSpan();
                assertNotNull(lastFinishedSpan);
                assertEquals(SPAN_KIND_CLIENT, lastFinishedSpan.tags().get(SPAN_KIND.getKey()));
                assertEquals(GET.methodName(), lastFinishedSpan.tags().get(HTTP_METHOD.getKey()));
                assertEquals(requestUrl, lastFinishedSpan.tags().get(HTTP_URL.getKey()));
                assertEquals(OK.code(), lastFinishedSpan.tags().get(HTTP_STATUS.getKey()));
                assertFalse(lastFinishedSpan.tags().containsKey(ERROR.getKey()));
            }
        }
    }

    @Test
    public void testInjectWithParent() throws Exception {
        final String requestUrl = "/foo";
        CountingInMemorySpanEventListener spanListener = new CountingInMemorySpanEventListener();
        DefaultInMemoryTracer tracer = new DefaultInMemoryTracer.Builder(
                new AsyncContextInMemoryScopeManager()).addListener(spanListener).build();
        try (ServerContext context = buildServer()) {
            try (HttpClient client = forSingleAddress(of((InetSocketAddress) context.listenAddress()))
                    .appendConnectionFilter(new TracingHttpRequesterFilter(tracer, "testClient")).build()) {
                try (InMemoryScope clientScope = tracer.buildSpan("test").startActive(true)) {
                    HttpResponse response = client.request(client.get(requestUrl)).toFuture().get();
                    TestSpanState serverSpanState = response.payloadBody(httpSerializer.deserializerFor(
                            TestSpanState.class));

                    assertThat(serverSpanState.traceId, isHexId());
                    assertThat(serverSpanState.spanId, isHexId());
                    assertThat(serverSpanState.parentSpanId, isHexId());

                    assertThat(serverSpanState.traceId, equalToIgnoringCase(clientScope.span().traceIdHex()));
                    assertThat(serverSpanState.parentSpanId, equalToIgnoringCase(clientScope.span().spanIdHex()));

                    // don't mess with caller span state
                    assertEquals(clientScope.span(), tracer.activeSpan());

                    assertEquals(1, spanListener.spanFinishedCount());
                    InMemorySpan lastFinishedSpan = spanListener.lastFinishedSpan();
                    assertNotNull(lastFinishedSpan);
                    assertEquals(SPAN_KIND_CLIENT, lastFinishedSpan.tags().get(SPAN_KIND.getKey()));
                    assertEquals(GET.methodName(), lastFinishedSpan.tags().get(HTTP_METHOD.getKey()));
                    assertEquals(requestUrl, lastFinishedSpan.tags().get(HTTP_URL.getKey()));
                    assertEquals(OK.code(), lastFinishedSpan.tags().get(HTTP_STATUS.getKey()));
                    assertFalse(lastFinishedSpan.tags().containsKey(ERROR.getKey()));
                }
            }
        }
    }

    @Test
    public void tracerThrowsReturnsErrorResponse() throws Exception {
        when(mockTracer.buildSpan(any())).thenThrow(DELIBERATE_EXCEPTION);
        try (ServerContext context = buildServer()) {
            try (HttpClient client = forSingleAddress(of((InetSocketAddress) context.listenAddress()))
                    .appendConnectionFilter(new TracingHttpRequesterFilter(mockTracer, "testClient")).build()) {
                HttpRequest request = client.get("/");
                expected.expect(ExecutionException.class);
                expected.expectCause(is(DELIBERATE_EXCEPTION));
                client.request(request).toFuture().get();
            }
        }
    }

    private static ServerContext buildServer() throws Exception {
        return HttpServers.forPort(0)
                .listenStreamingAndAwait((ctx, request, responseFactory) ->
                        success(responseFactory.ok().payloadBody(just(new TestSpanState(
                        valueOf(request.headers().get(TRACE_ID)),
                        valueOf(request.headers().get(SPAN_ID)),
                        toStringOrNull(request.headers().get(PARENT_SPAN_ID)),
                        "1".equals(valueOf(request.headers().get(SAMPLED))),
                        false
                )), httpSerializer.serializerFor(TestSpanState.class))));
    }

    @Nullable
    private static String toStringOrNull(@Nullable CharSequence cs) {
        return cs == null ? null : cs.toString();
    }
}

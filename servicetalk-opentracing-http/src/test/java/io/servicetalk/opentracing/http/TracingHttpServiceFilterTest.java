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
import io.servicetalk.http.api.StreamingHttpRequestHandler;
import io.servicetalk.http.netty.HttpServers;
import io.servicetalk.opentracing.core.internal.DefaultInMemoryTracer;
import io.servicetalk.opentracing.core.internal.InMemorySpan;
import io.servicetalk.transport.api.ServerContext;

import io.opentracing.Tracer;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.Timeout;
import org.mockito.Mock;

import java.net.InetSocketAddress;

import static io.servicetalk.concurrent.api.DeliberateException.DELIBERATE_EXCEPTION;
import static io.servicetalk.concurrent.api.Publisher.just;
import static io.servicetalk.concurrent.api.Single.success;
import static io.servicetalk.http.api.HttpResponseStatuses.INTERNAL_SERVER_ERROR;
import static io.servicetalk.http.api.HttpSerializationProviders.jsonSerializer;
import static io.servicetalk.http.api.HttpSerializationProviders.textSerializer;
import static io.servicetalk.http.netty.HttpClients.forSingleAddress;
import static io.servicetalk.opentracing.core.AsyncContextInMemoryScopeManager.SCOPE_MANAGER;
import static io.servicetalk.opentracing.core.internal.ZipkinHeaderNames.PARENT_SPAN_ID;
import static io.servicetalk.opentracing.core.internal.ZipkinHeaderNames.SAMPLED;
import static io.servicetalk.opentracing.core.internal.ZipkinHeaderNames.SPAN_ID;
import static io.servicetalk.opentracing.core.internal.ZipkinHeaderNames.TRACE_ID;
import static io.servicetalk.opentracing.http.TestUtils.isHexId;
import static io.servicetalk.opentracing.http.TestUtils.randomHexId;
import static io.servicetalk.transport.api.HostAndPort.of;
import static org.hamcrest.Matchers.equalToIgnoringCase;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.mockito.MockitoAnnotations.initMocks;

public class TracingHttpServiceFilterTest {
    private static final HttpSerializationProvider httpSerializer = jsonSerializer(new JacksonSerializationProvider());
    @Rule
    public final Timeout timeout = new ServiceTalkTestTimeout();
    @Mock
    private Tracer mockTracer;

    @Before
    public void setup() {
        initMocks(this);
    }

    private ServerContext buildServer() throws Exception {
        DefaultInMemoryTracer tracer = new DefaultInMemoryTracer.Builder(SCOPE_MANAGER).build();
        return HttpServers.forPort(0)
                .listenStreamingAndAwait(new TracingHttpServiceFilter(tracer, "testServer",
                        ((StreamingHttpRequestHandler) (ctx, request, responseFactory) -> {
                    InMemorySpan span = tracer.activeSpan();
                    if (span == null) {
                        return success(responseFactory.internalServerError().payloadBody(just("span not found"),
                                textSerializer()));
                    }
                    return success(responseFactory.ok().payloadBody(just(new TestSpanState(
                                    span.traceIdHex(),
                                    span.spanIdHex(),
                                    span.parentSpanIdHex(),
                                    span.isSampled())),
                            httpSerializer.serializerFor(TestSpanState.class)));
                }).asStreamingService()));
    }

    @Test
    public void testRequestWithTraceKey() throws Exception {
        try (ServerContext context = buildServer()) {
            try (HttpClient client = forSingleAddress(of((InetSocketAddress) context.listenAddress())).build()) {
                String traceId = randomHexId();
                String spanId = randomHexId();
                String parentSpanId = randomHexId();
                HttpRequest request = client.get("/");
                request.headers().set(TRACE_ID, traceId)
                        .set(SPAN_ID, spanId)
                        .set(PARENT_SPAN_ID, parentSpanId)
                        .set(SAMPLED, "0");
                HttpResponse response = client.request(request).toFuture().get();
                TestSpanState serverSpanState = response.payloadBody(httpSerializer.deserializerFor(
                        TestSpanState.class));
                assertThat(serverSpanState.traceId, equalToIgnoringCase(traceId));
                assertThat(serverSpanState.spanId, not(equalToIgnoringCase(spanId)));
                assertThat(serverSpanState.parentSpanId, equalToIgnoringCase(spanId));
                assertFalse(serverSpanState.sampled);
            }
        }
    }

    @Test
    public void testRequestWithoutTraceKey() throws Exception {
        try (ServerContext context = buildServer()) {
            try (HttpClient client = forSingleAddress(of((InetSocketAddress) context.listenAddress())).build()) {
                HttpRequest request = client.get("/");
                HttpResponse response = client.request(request).toFuture().get();
                TestSpanState serverSpanState = response.payloadBody(httpSerializer.deserializerFor(
                        TestSpanState.class));
                assertThat(serverSpanState.traceId, isHexId());
                assertThat(serverSpanState.spanId, isHexId());
                assertNull(serverSpanState.parentSpanId);
                assertTrue(serverSpanState.sampled);
            }
        }
    }

    @Test
    public void tracerThrowsReturnsErrorResponse() throws Exception {
        when(mockTracer.buildSpan(any())).thenThrow(DELIBERATE_EXCEPTION);
        try (ServerContext context = HttpServers.forPort(0)
                .listenStreamingAndAwait(new TracingHttpServiceFilter(mockTracer, "testServer",
                        ((StreamingHttpRequestHandler) (ctx, request, responseFactory) ->
                                success(responseFactory.forbidden())).asStreamingService()))) {
            try (HttpClient client = forSingleAddress(of((InetSocketAddress) context.listenAddress())).build()) {
                HttpRequest request = client.get("/");
                HttpResponse response = client.request(request).toFuture().get();
                assertThat(response.status(), is(INTERNAL_SERVER_ERROR));
            }
        }
    }
}

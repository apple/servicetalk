/*
 * Copyright © 2018-2019 Apple Inc. and the ServiceTalk project authors
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

import io.servicetalk.concurrent.PublisherSource;
import io.servicetalk.concurrent.api.Single;
import io.servicetalk.data.jackson.JacksonSerializationProvider;
import io.servicetalk.http.api.HttpClient;
import io.servicetalk.http.api.HttpRequest;
import io.servicetalk.http.api.HttpResponse;
import io.servicetalk.http.api.HttpSerializationProvider;
import io.servicetalk.http.api.HttpServiceContext;
import io.servicetalk.http.api.StreamingHttpRequest;
import io.servicetalk.http.api.StreamingHttpResponse;
import io.servicetalk.http.api.StreamingHttpResponseFactory;
import io.servicetalk.http.api.StreamingHttpService;
import io.servicetalk.http.api.StreamingHttpServiceFilter;
import io.servicetalk.http.api.StreamingHttpServiceFilterFactory;
import io.servicetalk.http.netty.HttpServers;
import io.servicetalk.log4j2.mdc.utils.LoggerStringWriter;
import io.servicetalk.opentracing.http.TestUtils.CountingInMemorySpanEventListener;
import io.servicetalk.opentracing.inmemory.DefaultInMemoryTracer;
import io.servicetalk.opentracing.inmemory.api.InMemorySpan;
import io.servicetalk.transport.api.ServerContext;

import io.opentracing.Tracer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;

import static io.opentracing.tag.Tags.ERROR;
import static io.opentracing.tag.Tags.HTTP_METHOD;
import static io.opentracing.tag.Tags.HTTP_STATUS;
import static io.opentracing.tag.Tags.HTTP_URL;
import static io.opentracing.tag.Tags.SPAN_KIND;
import static io.opentracing.tag.Tags.SPAN_KIND_SERVER;
import static io.servicetalk.concurrent.api.Publisher.from;
import static io.servicetalk.concurrent.api.Single.succeeded;
import static io.servicetalk.concurrent.internal.DeliberateException.DELIBERATE_EXCEPTION;
import static io.servicetalk.http.api.HttpRequestMethod.GET;
import static io.servicetalk.http.api.HttpResponseStatus.INTERNAL_SERVER_ERROR;
import static io.servicetalk.http.api.HttpResponseStatus.OK;
import static io.servicetalk.http.api.HttpSerializationProviders.jsonSerializer;
import static io.servicetalk.http.api.HttpSerializationProviders.textSerializer;
import static io.servicetalk.http.netty.AsyncContextHttpFilterVerifier.verifyServerFilterAsyncContextVisibility;
import static io.servicetalk.http.netty.HttpClients.forSingleAddress;
import static io.servicetalk.log4j2.mdc.utils.LoggerStringWriter.stableAccumulated;
import static io.servicetalk.opentracing.asynccontext.AsyncContextInMemoryScopeManager.SCOPE_MANAGER;
import static io.servicetalk.opentracing.http.TestUtils.TRACING_TEST_LOG_LINE_PREFIX;
import static io.servicetalk.opentracing.http.TestUtils.isHexId;
import static io.servicetalk.opentracing.http.TestUtils.randomHexId;
import static io.servicetalk.opentracing.http.TestUtils.verifyTraceIdPresentInLogs;
import static io.servicetalk.opentracing.internal.ZipkinHeaderNames.PARENT_SPAN_ID;
import static io.servicetalk.opentracing.internal.ZipkinHeaderNames.SAMPLED;
import static io.servicetalk.opentracing.internal.ZipkinHeaderNames.SPAN_ID;
import static io.servicetalk.opentracing.internal.ZipkinHeaderNames.TRACE_ID;
import static io.servicetalk.transport.netty.internal.AddressUtils.localAddress;
import static io.servicetalk.transport.netty.internal.AddressUtils.serverHostAndPort;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalToIgnoringCase;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.mockito.MockitoAnnotations.initMocks;

@ExtendWith(MockitoExtension.class)
class TracingHttpServiceFilterTest {
    private static final Logger LOGGER = LoggerFactory.getLogger(TracingHttpServiceFilterTest.class);
    private static final HttpSerializationProvider httpSerializer = jsonSerializer(new JacksonSerializationProvider());

    @Mock
    private Tracer mockTracer;

    @BeforeEach
    public void setup() {
        initMocks(this);
        LoggerStringWriter.reset();
    }

    @AfterEach
    public void tearDown() {
        LoggerStringWriter.remove();
    }

    private static ServerContext buildServer(CountingInMemorySpanEventListener spanListener) throws Exception {
        DefaultInMemoryTracer tracer = new DefaultInMemoryTracer.Builder(SCOPE_MANAGER)
                .addListener(spanListener).build();
        return HttpServers.forAddress(localAddress(0))
                .appendServiceFilter(new TracingHttpServiceFilter(tracer, "testServer"))
                .appendServiceFilter(new TestTracingLoggerFilter(TRACING_TEST_LOG_LINE_PREFIX))
                .listenStreamingAndAwait((ctx, request, responseFactory) -> {
                    InMemorySpan span = tracer.activeSpan();
                    if (span == null) {
                        return succeeded(responseFactory.internalServerError().payloadBody(from("span not found"),
                                textSerializer()));
                    }
                    return succeeded(responseFactory.ok().payloadBody(from(new TestSpanState(
                                    span.traceIdHex(),
                                    span.spanIdHex(),
                                    span.parentSpanIdHex(),
                                    span.isSampled(),
                                    span.tags().containsKey(ERROR.getKey()))),
                            httpSerializer.serializerFor(TestSpanState.class)));
                });
    }

    @Test
    void testRequestWithTraceKey() throws Exception {
        CountingInMemorySpanEventListener spanListener = new CountingInMemorySpanEventListener();
        try (ServerContext context = buildServer(spanListener)) {
            try (HttpClient client = forSingleAddress(serverHostAndPort(context)).build()) {
                String traceId = randomHexId();
                String spanId = randomHexId();
                String parentSpanId = randomHexId();
                String requestUrl = "/";
                HttpRequest request = client.get(requestUrl);
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
                assertFalse(serverSpanState.error);
                assertEquals(0, spanListener.spanFinishedCount()); // not sampled, so no finish

                InMemorySpan lastFinishedSpan = spanListener.lastFinishedSpan();
                assertNull(lastFinishedSpan);

                verifyTraceIdPresentInLogs(stableAccumulated(1000), requestUrl, serverSpanState.traceId,
                        serverSpanState.spanId, serverSpanState.parentSpanId, TRACING_TEST_LOG_LINE_PREFIX);
            }
        }
    }

    @Test
    void testRequestWithoutTraceKey() throws Exception {
        final String requestUrl = "/foo";
        CountingInMemorySpanEventListener spanListener = new CountingInMemorySpanEventListener();
        try (ServerContext context = buildServer(spanListener)) {
            try (HttpClient client = forSingleAddress(serverHostAndPort(context)).build()) {
                HttpRequest request = client.get(requestUrl);
                HttpResponse response = client.request(request).toFuture().get();
                TestSpanState serverSpanState = response.payloadBody(httpSerializer.deserializerFor(
                        TestSpanState.class));
                assertThat(serverSpanState.traceId, isHexId());
                assertThat(serverSpanState.spanId, isHexId());
                assertNull(serverSpanState.parentSpanId);
                assertTrue(serverSpanState.sampled);
                assertFalse(serverSpanState.error);
                assertEquals(1, spanListener.spanFinishedCount()); // sampled, so only finish once!

                InMemorySpan lastFinishedSpan = spanListener.lastFinishedSpan();
                assertNotNull(lastFinishedSpan);
                assertEquals(SPAN_KIND_SERVER, lastFinishedSpan.tags().get(SPAN_KIND.getKey()));
                assertEquals(GET.name(), lastFinishedSpan.tags().get(HTTP_METHOD.getKey()));
                assertEquals(requestUrl, lastFinishedSpan.tags().get(HTTP_URL.getKey()));
                assertEquals(OK.code(), lastFinishedSpan.tags().get(HTTP_STATUS.getKey()));
                assertFalse(lastFinishedSpan.tags().containsKey(ERROR.getKey()));

                verifyTraceIdPresentInLogs(stableAccumulated(1000), requestUrl, serverSpanState.traceId,
                        serverSpanState.spanId, serverSpanState.parentSpanId, TRACING_TEST_LOG_LINE_PREFIX);
            }
        }
    }

    @Test
    void tracerThrowsReturnsErrorResponse() throws Exception {
        when(mockTracer.buildSpan(any())).thenThrow(DELIBERATE_EXCEPTION);
        try (ServerContext context = HttpServers.forAddress(localAddress(0))
                .appendServiceFilter(new TracingHttpServiceFilter(mockTracer, "testServer"))
                .listenStreamingAndAwait(((ctx, request, responseFactory) -> succeeded(responseFactory.forbidden())))) {
            try (HttpClient client = forSingleAddress(serverHostAndPort(context)).build()) {
                HttpRequest request = client.get("/");
                HttpResponse response = client.request(request).toFuture().get();
                assertThat(response.status(), is(INTERNAL_SERVER_ERROR));
            }
        }
    }

    @Test
    void verifyAsyncContext() throws Exception {
        final DefaultInMemoryTracer tracer = new DefaultInMemoryTracer.Builder(SCOPE_MANAGER).build();
        verifyServerFilterAsyncContextVisibility(new TracingHttpServiceFilter(tracer, "testServer"));
    }

    private static final class TestTracingLoggerFilter implements StreamingHttpServiceFilterFactory {
        private final String[] logLinePrefix;

        private TestTracingLoggerFilter(final String[] logLinePrefix) {
            if (logLinePrefix.length < 6) {
                throw new IllegalArgumentException("logLinePrefix length must be >= 6");
            }
            this.logLinePrefix = logLinePrefix.clone();
        }

        @Override
        public StreamingHttpServiceFilter create(final StreamingHttpService service) {
            return new StreamingHttpServiceFilter(service) {
                @Override
                public Single<StreamingHttpResponse> handle(
                        final HttpServiceContext ctx, final StreamingHttpRequest request,
                        final StreamingHttpResponseFactory responseFactory) {
                    LOGGER.debug(logLinePrefix[0], request.path());
                    return delegate().handle(ctx, request, responseFactory).map(response -> {
                        LOGGER.debug(logLinePrefix[1], request.path());
                        return response.transformMessageBody(payload -> {
                            LOGGER.debug(logLinePrefix[2], request.path());
                            return payload.beforeSubscriber(() -> new PublisherSource.Subscriber<Object>() {
                                @Override
                                public void onSubscribe(final PublisherSource.Subscription subscription) {
                                    LOGGER.debug(logLinePrefix[3], request.path());
                                }

                                @Override
                                public void onNext(@Nullable final Object o) {
                                    LOGGER.debug(logLinePrefix[4], request.path());
                                }

                                @Override
                                public void onError(final Throwable t) {
                                    LOGGER.debug(logLinePrefix[5], request.path());
                                }

                                @Override
                                public void onComplete() {
                                    LOGGER.debug(logLinePrefix[5], request.path());
                                }
                            });
                        });
                    });
                }
            };
        }
    }
}

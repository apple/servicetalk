/*
 * Copyright © 2018-2019, 2021 Apple Inc. and the ServiceTalk project authors
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
import io.servicetalk.http.api.FilterableStreamingHttpClient;
import io.servicetalk.http.api.HttpClient;
import io.servicetalk.http.api.HttpExecutionStrategy;
import io.servicetalk.http.api.HttpRequest;
import io.servicetalk.http.api.HttpResponse;
import io.servicetalk.http.api.StreamingHttpClientFilter;
import io.servicetalk.http.api.StreamingHttpClientFilterFactory;
import io.servicetalk.http.api.StreamingHttpRequest;
import io.servicetalk.http.api.StreamingHttpRequester;
import io.servicetalk.http.api.StreamingHttpResponse;
import io.servicetalk.http.netty.HttpServers;
import io.servicetalk.log4j2.mdc.utils.LoggerStringWriter;
import io.servicetalk.opentracing.http.TestUtils.CountingInMemorySpanEventListener;
import io.servicetalk.opentracing.inmemory.DefaultInMemoryTracer;
import io.servicetalk.opentracing.inmemory.api.InMemorySpan;
import io.servicetalk.transport.api.ServerContext;

import io.opentracing.Scope;
import io.opentracing.Tracer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.ExecutionException;
import javax.annotation.Nullable;

import static io.opentracing.tag.Tags.ERROR;
import static io.opentracing.tag.Tags.HTTP_METHOD;
import static io.opentracing.tag.Tags.HTTP_STATUS;
import static io.opentracing.tag.Tags.HTTP_URL;
import static io.opentracing.tag.Tags.SPAN_KIND;
import static io.opentracing.tag.Tags.SPAN_KIND_CLIENT;
import static io.servicetalk.concurrent.api.Single.succeeded;
import static io.servicetalk.concurrent.internal.DeliberateException.DELIBERATE_EXCEPTION;
import static io.servicetalk.http.api.HttpRequestMethod.GET;
import static io.servicetalk.http.api.HttpResponseStatus.OK;
import static io.servicetalk.http.netty.HttpClients.forSingleAddress;
import static io.servicetalk.log4j2.mdc.utils.LoggerStringWriter.stableAccumulated;
import static io.servicetalk.opentracing.asynccontext.AsyncContextInMemoryScopeManager.SCOPE_MANAGER;
import static io.servicetalk.opentracing.http.TestUtils.SPAN_STATE_SERIALIZER;
import static io.servicetalk.opentracing.http.TestUtils.TRACING_TEST_LOG_LINE_PREFIX;
import static io.servicetalk.opentracing.http.TestUtils.isHexId;
import static io.servicetalk.opentracing.http.TestUtils.verifyTraceIdPresentInLogs;
import static io.servicetalk.opentracing.internal.ZipkinHeaderNames.PARENT_SPAN_ID;
import static io.servicetalk.opentracing.internal.ZipkinHeaderNames.SAMPLED;
import static io.servicetalk.opentracing.internal.ZipkinHeaderNames.SPAN_ID;
import static io.servicetalk.opentracing.internal.ZipkinHeaderNames.TRACE_ID;
import static io.servicetalk.transport.netty.internal.AddressUtils.localAddress;
import static io.servicetalk.transport.netty.internal.AddressUtils.serverHostAndPort;
import static java.lang.String.valueOf;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalToIgnoringCase;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.mockito.MockitoAnnotations.initMocks;

@ExtendWith(MockitoExtension.class)
class TracingHttpRequesterFilterTest {
    private static final Logger LOGGER = LoggerFactory.getLogger(TracingHttpRequesterFilterTest.class);

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

    @Test
    void testInjectWithNoParent() throws Exception {
        final String requestUrl = "/";
        CountingInMemorySpanEventListener spanListener = new CountingInMemorySpanEventListener();
        DefaultInMemoryTracer tracer = new DefaultInMemoryTracer.Builder(SCOPE_MANAGER)
                .addListener(spanListener).build();
        try (ServerContext context = buildServer()) {
            try (HttpClient client = forSingleAddress(serverHostAndPort(context))
                    .appendClientFilter(new TracingHttpRequesterFilter(tracer, "testClient"))
                    .appendClientFilter(new TestTracingLoggerFilter(TRACING_TEST_LOG_LINE_PREFIX)).build()) {
                HttpResponse response = client.request(client.get(requestUrl)).toFuture().get();
                TestSpanState serverSpanState = response.payloadBody(SPAN_STATE_SERIALIZER);

                assertThat(serverSpanState.traceId, isHexId());
                assertThat(serverSpanState.spanId, isHexId());
                assertNull(serverSpanState.parentSpanId);

                // don't mess with caller span state
                assertNull(tracer.activeSpan());

                assertEquals(1, spanListener.spanFinishedCount());
                InMemorySpan lastFinishedSpan = spanListener.lastFinishedSpan();
                assertNotNull(lastFinishedSpan);
                assertEquals(SPAN_KIND_CLIENT, lastFinishedSpan.tags().get(SPAN_KIND.getKey()));
                assertEquals(GET.name(), lastFinishedSpan.tags().get(HTTP_METHOD.getKey()));
                assertEquals(requestUrl, lastFinishedSpan.tags().get(HTTP_URL.getKey()));
                assertEquals(OK.code(), lastFinishedSpan.tags().get(HTTP_STATUS.getKey()));
                assertFalse(lastFinishedSpan.tags().containsKey(ERROR.getKey()));

                verifyTraceIdPresentInLogs(stableAccumulated(1000), requestUrl, serverSpanState.traceId,
                        serverSpanState.spanId, null, TRACING_TEST_LOG_LINE_PREFIX);
            }
        }
    }

    @Test
    void testInjectWithParent() throws Exception {
        final String requestUrl = "/foo";
        CountingInMemorySpanEventListener spanListener = new CountingInMemorySpanEventListener();
        DefaultInMemoryTracer tracer = new DefaultInMemoryTracer.Builder(SCOPE_MANAGER)
                .addListener(spanListener).build();
        try (ServerContext context = buildServer()) {
            try (HttpClient client = forSingleAddress(serverHostAndPort(context))
                    .appendClientFilter(new TracingHttpRequesterFilter(tracer, "testClient"))
                    .appendClientFilter(new TestTracingLoggerFilter(TRACING_TEST_LOG_LINE_PREFIX)).build()) {
                    InMemorySpan clientSpan = tracer.buildSpan("test").start();
                    try (Scope ignored = tracer.activateSpan(clientSpan)) {
                        HttpResponse response = client.request(client.get(requestUrl)).toFuture().get();
                        TestSpanState serverSpanState = response.payloadBody(SPAN_STATE_SERIALIZER);

                        assertThat(serverSpanState.traceId, isHexId());
                        assertThat(serverSpanState.spanId, isHexId());
                        assertThat(serverSpanState.parentSpanId, isHexId());

                        assertThat(serverSpanState.traceId, equalToIgnoringCase(
                                clientSpan.context().toTraceId()));
                        assertThat(serverSpanState.parentSpanId, equalToIgnoringCase(
                                clientSpan.context().toSpanId()));

                        // don't mess with caller span state
                        assertEquals(clientSpan, tracer.activeSpan());

                        assertEquals(1, spanListener.spanFinishedCount());
                        InMemorySpan lastFinishedSpan = spanListener.lastFinishedSpan();
                        assertNotNull(lastFinishedSpan);
                        assertEquals(SPAN_KIND_CLIENT, lastFinishedSpan.tags().get(SPAN_KIND.getKey()));
                        assertEquals(GET.name(), lastFinishedSpan.tags().get(HTTP_METHOD.getKey()));
                        assertEquals(requestUrl, lastFinishedSpan.tags().get(HTTP_URL.getKey()));
                        assertEquals(OK.code(), lastFinishedSpan.tags().get(HTTP_STATUS.getKey()));
                        assertFalse(lastFinishedSpan.tags().containsKey(ERROR.getKey()));

                        verifyTraceIdPresentInLogs(stableAccumulated(1000), requestUrl, serverSpanState.traceId,
                            serverSpanState.spanId, serverSpanState.parentSpanId, TRACING_TEST_LOG_LINE_PREFIX);
                    } finally {
                        clientSpan.finish();
                    }
            }
        }
    }

    @Test
    void tracerThrowsReturnsErrorResponse() throws Exception {
        when(mockTracer.buildSpan(any())).thenThrow(DELIBERATE_EXCEPTION);
        try (ServerContext context = buildServer()) {
            try (HttpClient client = forSingleAddress(serverHostAndPort(context))
                    .appendConnectionFilter(
                            new TracingHttpRequesterFilter(mockTracer, "testClient")).build()) {
                HttpRequest request = client.get("/");
                ExecutionException ex = assertThrows(ExecutionException.class,
                    () -> client.request(request).toFuture().get());
                assertThat(ex.getCause(), is(DELIBERATE_EXCEPTION));
            }
        }
    }

    private static ServerContext buildServer() throws Exception {
        return HttpServers.forAddress(localAddress(0))
                .listenAndAwait((ctx, request, responseFactory) ->
                        succeeded(responseFactory.ok().payloadBody(new TestSpanState(
                        valueOf(request.headers().get(TRACE_ID)),
                        valueOf(request.headers().get(SPAN_ID)),
                        toStringOrNull(request.headers().get(PARENT_SPAN_ID)),
                        "1".equals(valueOf(request.headers().get(SAMPLED))),
                        false
                ), SPAN_STATE_SERIALIZER)));
    }

    @Nullable
    private static String toStringOrNull(@Nullable CharSequence cs) {
        return cs == null ? null : cs.toString();
    }

    private static final class TestTracingLoggerFilter implements StreamingHttpClientFilterFactory {
        private final String[] logLinePrefix;

        TestTracingLoggerFilter(final String[] logLinePrefix) {
            if (logLinePrefix.length < 6) {
                throw new IllegalArgumentException("logLinePrefix length must be >= 6");
            }
            this.logLinePrefix = logLinePrefix.clone();
        }

        @Override
        public StreamingHttpClientFilter create(final FilterableStreamingHttpClient client) {
            return new StreamingHttpClientFilter(client) {
                @Override
                protected Single<StreamingHttpResponse> request(final StreamingHttpRequester delegate,
                                                                final HttpExecutionStrategy strategy,
                                                                final StreamingHttpRequest request) {
                    LOGGER.debug(logLinePrefix[0], request.path());
                    return delegate.request(strategy, request).map(response -> {
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

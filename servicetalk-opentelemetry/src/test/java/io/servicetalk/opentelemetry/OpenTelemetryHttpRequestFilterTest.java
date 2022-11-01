/*
 * Copyright © 2022 Apple Inc. and the ServiceTalk project authors
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

package io.servicetalk.opentelemetry;

import io.servicetalk.http.api.HttpClient;
import io.servicetalk.http.api.HttpResponse;
import io.servicetalk.http.api.HttpServerBuilder;
import io.servicetalk.http.netty.HttpServers;
import io.servicetalk.log4j2.mdc.utils.LoggerStringWriter;
import io.servicetalk.transport.api.ServerContext;

import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import io.opentelemetry.context.propagation.ContextPropagators;
import io.opentelemetry.sdk.testing.junit5.OpenTelemetryExtension;
import io.opentelemetry.sdk.trace.data.SpanData;
import io.opentelemetry.semconv.trace.attributes.SemanticAttributes;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.invoke.MethodHandles;

import static io.servicetalk.concurrent.api.Single.succeeded;
import static io.servicetalk.http.netty.HttpClients.forSingleAddress;
import static io.servicetalk.log4j2.mdc.utils.LoggerStringWriter.assertContainsMdcPair;
import static io.servicetalk.log4j2.mdc.utils.LoggerStringWriter.stableAccumulated;
import static io.servicetalk.opentelemetry.TestUtils.SPAN_STATE_SERIALIZER;
import static io.servicetalk.opentelemetry.TestUtils.TRACING_TEST_LOG_LINE_PREFIX;
import static io.servicetalk.opentelemetry.TestUtils.TestTracingClientLoggerFilter;
import static io.servicetalk.transport.netty.internal.AddressUtils.localAddress;
import static io.servicetalk.transport.netty.internal.AddressUtils.serverHostAndPort;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertTrue;

@ExtendWith(MockitoExtension.class)
class OpenTelemetryHttpRequestFilterTest {

    private static final Logger LOGGER = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

    @RegisterExtension
    private final OpenTelemetryExtension otelTesting = OpenTelemetryExtension.create();

    @BeforeAll
    static void beforeAll() {
    }

    @BeforeEach
    public void setup() {
        LoggerStringWriter.reset();
    }

    @AfterEach
    public void tearDown() {
        LoggerStringWriter.remove();
    }

    @Test
    void testInjectWithNoParent() throws Exception {
        final String requestUrl = "/";
        OpenTelemetry openTelemetry = otelTesting.getOpenTelemetry();
        try (ServerContext context = buildServer(openTelemetry, false)) {
            try (HttpClient client = forSingleAddress(serverHostAndPort(context))
                .appendClientFilter(new OpenTelemetryHttpRequestFilter(openTelemetry, "testClient"))
                .appendClientFilter(new TestTracingClientLoggerFilter(TRACING_TEST_LOG_LINE_PREFIX)).build()) {
                HttpResponse response = client.request(client.get(requestUrl)).toFuture().get();
                TestSpanState serverSpanState = response.payloadBody(SPAN_STATE_SERIALIZER);

                verifyTraceIdPresentInLogs(stableAccumulated(1000), requestUrl,
                    serverSpanState.getTraceId(), serverSpanState.getSpanId(),
                    TRACING_TEST_LOG_LINE_PREFIX);
                assertThat(otelTesting.getSpans()).hasSize(1);
                assertThat(otelTesting.getSpans()).extracting("traceId")
                    .containsExactly(serverSpanState.getTraceId());
                assertThat(otelTesting.getSpans()).extracting("spanId")
                    .containsAnyOf(serverSpanState.getSpanId());
                otelTesting.assertTraces()
                    .hasTracesSatisfyingExactly(ta -> ta.hasTraceId(serverSpanState.getTraceId()));

                otelTesting.assertTraces()
                    .hasTracesSatisfyingExactly(ta ->
                        assertThat(ta.getSpan(0).getAttributes().get(SemanticAttributes.HTTP_URL))
                            .startsWith("http://localhost:8080"));
            }
        }
    }

    @Test
    void testInjectWithAParent() throws Exception {
        final String requestUrl = "/";
        OpenTelemetry openTelemetry = otelTesting.getOpenTelemetry();
        try (ServerContext context = buildServer(openTelemetry, true)) {
            try (HttpClient client = forSingleAddress(serverHostAndPort(context))
                .appendClientFilter(new OpenTelemetryHttpRequestFilter(openTelemetry, "testClient"))
                .appendClientFilter(new TestTracingClientLoggerFilter(TRACING_TEST_LOG_LINE_PREFIX)).build()) {
                HttpResponse response = client.request(client.get(requestUrl)).toFuture().get();
                TestSpanState serverSpanState = response.payloadBody(SPAN_STATE_SERIALIZER);

                verifyTraceIdPresentInLogs(stableAccumulated(1000), requestUrl,
                    serverSpanState.getTraceId(), serverSpanState.getSpanId(),
                    TRACING_TEST_LOG_LINE_PREFIX);
                assertThat(otelTesting.getSpans()).hasSize(2);
                assertThat(otelTesting.getSpans()).extracting("traceId")
                    .containsExactly(serverSpanState.getTraceId(), serverSpanState.getTraceId());
                assertThat(otelTesting.getSpans()).extracting("spanId")
                    .containsAnyOf(serverSpanState.getSpanId());
                otelTesting.assertTraces()
                    .hasTracesSatisfyingExactly(ta ->
                        ta.hasTraceId(serverSpanState.getTraceId()));

                otelTesting.assertTraces()
                    .hasTracesSatisfyingExactly(ta ->
                        assertThat(ta.getSpan(0).getAttributes().get(SemanticAttributes.HTTP_URL))
                            .startsWith("http://localhost:8080"));
            }
        }
    }

    @Test
    void testInjectWithAParentCreated() throws Exception {
        final String requestUrl = "/";
        OpenTelemetry openTelemetry = otelTesting.getOpenTelemetry();
        try (ServerContext context = buildServer(openTelemetry, true)) {
            try (HttpClient client = forSingleAddress(serverHostAndPort(context))
                .appendClientFilter(new OpenTelemetryHttpRequestFilter(openTelemetry, "testClient"))
                .appendClientFilter(new TestTracingClientLoggerFilter(TRACING_TEST_LOG_LINE_PREFIX)).build()) {

                Span span = otelTesting.getOpenTelemetry().getTracer("io.serviceTalk").spanBuilder("/")
                    .setSpanKind(SpanKind.INTERNAL)
                    .setAttribute("component", "serviceTalk")
                    .startSpan();
                TestSpanState serverSpanState;
                try (Scope scope = span.makeCurrent()) {
                    LOGGER.info("making span={} current", span);
                    HttpResponse response = client.request(client.get(requestUrl)).toFuture().get();
                    serverSpanState = response.payloadBody(SPAN_STATE_SERIALIZER);
                } finally {
                    span.end();
                }
                    verifyTraceIdPresentInLogs(stableAccumulated(1000), requestUrl,
                        serverSpanState.getTraceId(), serverSpanState.getSpanId(),
                        TRACING_TEST_LOG_LINE_PREFIX);
                    assertThat(otelTesting.getSpans()).hasSize(3);
                    assertThat(otelTesting.getSpans()).extracting("traceId")
                        .containsExactly(serverSpanState.getTraceId(), serverSpanState.getTraceId(),
                            serverSpanState.getTraceId());
                    assertThat(otelTesting.getSpans()).extracting("spanId")
                        .containsAnyOf(serverSpanState.getSpanId());
                    otelTesting.assertTraces()
                        .hasTracesSatisfyingExactly(ta ->
                            ta.hasTraceId(serverSpanState.getTraceId()));

                    otelTesting.assertTraces()
                        .hasTracesSatisfyingExactly(ta ->
                            assertThat(ta.getSpan(1).getAttributes().get(SemanticAttributes.HTTP_URL))
                                .startsWith("http://localhost:8080"));
                        otelTesting.assertTraces()
                        .hasTracesSatisfyingExactly(ta ->
                            assertThat(ta.getSpan(0).getAttributes().get(AttributeKey.stringKey("component")))
                            .isEqualTo("serviceTalk"));
                SpanData firstSpan = otelTesting.getSpans().get(0);
                SpanData secondSpan = otelTesting.getSpans().get(1);
                SpanData thirdSpan = otelTesting.getSpans().get(2);
                assertThat(firstSpan.getParentSpanId()).isEqualTo(secondSpan.getSpanId());
                assertThat(secondSpan.getParentSpanId()).isEqualTo(thirdSpan.getSpanId());
            }
        }
    }

    private static ServerContext buildServer(OpenTelemetry givenOpentelemetry, boolean addFilter) throws Exception {
        HttpServerBuilder httpServerBuilder = HttpServers.forAddress(localAddress(0));
        if (addFilter) {
            httpServerBuilder =
                httpServerBuilder.appendServiceFilter(new OpenTelemetryHttpServerFilter(givenOpentelemetry));
        }
        return httpServerBuilder
            .listenAndAwait((ctx, request, responseFactory) -> {
                final ContextPropagators propagators = givenOpentelemetry.getPropagators();
                final Context context = Context.root();
                io.opentelemetry.context.Context tracingContext = propagators.getTextMapPropagator()
                    .extract(context, request.headers(), HeadersPropagatorGetter.INSTANCE);
                Span span = Span.fromContext(tracingContext);
                return succeeded(
                    responseFactory.ok().payloadBody(new TestSpanState(span.getSpanContext()), SPAN_STATE_SERIALIZER));
            });
    }

    static void verifyTraceIdPresentInLogs(String logs, String requestPath, String traceId, String spanId,
                                           String[] logLinePrefix) {
        String[] lines = logs.split("\\r?\\n");
        for (final String linePrefix : logLinePrefix) {
            String prefix = linePrefix.replaceFirst("\\{}", requestPath);
            boolean foundMatch = false;
            for (String line : lines) {
                int matchIndex = line.indexOf(prefix);
                if (matchIndex != -1) {
                    foundMatch = true;
                    try {
                        assertContainsMdcPair(line, "trace_id=", traceId);
                        assertContainsMdcPair(line, "span_id=", spanId);
                    } catch (Throwable cause) {
                        cause.addSuppressed(new AssertionError("failed on prefix: " + prefix));
                        throw cause;
                    }
                    break;
                }
            }
            assertTrue(foundMatch, "could not find log line with prefix: " + prefix);
        }
    }
}

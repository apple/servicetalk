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

package io.servicetalk.opentelemetry.http;

import io.servicetalk.http.api.HttpClient;
import io.servicetalk.http.api.HttpResponse;
import io.servicetalk.http.netty.HttpServers;
import io.servicetalk.log4j2.mdc.utils.LoggerStringWriter;
import io.servicetalk.opentelemetry.http.TestUtils.TestTracingServerLoggerFilter;
import io.servicetalk.transport.api.ServerContext;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.propagation.ContextPropagators;
import io.opentelemetry.context.propagation.TextMapPropagator;
import io.opentelemetry.context.propagation.TextMapSetter;
import io.opentelemetry.sdk.testing.junit5.OpenTelemetryExtension;
import io.opentelemetry.sdk.trace.data.SpanData;
import io.opentelemetry.semconv.SemanticAttributes;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;

import static io.servicetalk.concurrent.api.Single.succeeded;
import static io.servicetalk.http.netty.HttpClients.forSingleAddress;
import static io.servicetalk.opentelemetry.http.OpenTelemetryHttpRequestFilterTest.verifyTraceIdPresentInLogs;
import static io.servicetalk.opentelemetry.http.TestUtils.SPAN_STATE_SERIALIZER;
import static io.servicetalk.opentelemetry.http.TestUtils.TRACING_TEST_LOG_LINE_PREFIX;
import static io.servicetalk.transport.netty.internal.AddressUtils.localAddress;
import static io.servicetalk.transport.netty.internal.AddressUtils.serverHostAndPort;
import static java.util.Collections.singletonList;
import static org.assertj.core.api.Assertions.assertThat;

class OpenTelemetryHttpServerFilterTest {

    @RegisterExtension
    static final OpenTelemetryExtension otelTesting = OpenTelemetryExtension.create();

    private final LoggerStringWriter loggerStringWriter = new LoggerStringWriter();

    @BeforeEach
    public void setup() {
        loggerStringWriter.reset();
    }

    @AfterEach
    public void tearDown() {
        loggerStringWriter.remove();
    }

    @Test
    void testInjectWithNoParent() throws Exception {
        final String requestUrl = "/path";
        try (ServerContext context = buildServer(otelTesting.getOpenTelemetry())) {
            try (HttpClient client = forSingleAddress(serverHostAndPort(context)).build()) {
                HttpResponse response = client.request(client.get(requestUrl)).toFuture().get();
                TestSpanState serverSpanState = response.payloadBody(SPAN_STATE_SERIALIZER);

                verifyTraceIdPresentInLogs(loggerStringWriter.stableAccumulated(1000), requestUrl,
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
                    .hasTracesSatisfyingExactly(ta -> {
                        SpanData span = ta.getSpan(0);
                        assertThat(span.getAttributes().get(SemanticAttributes.HTTP_STATUS_CODE))
                            .isEqualTo(200);
                        assertThat(span.getAttributes().get(SemanticAttributes.HTTP_TARGET))
                            .isEqualTo("/path");
                        assertThat(span.getAttributes().get(SemanticAttributes.NET_PROTOCOL_NAME))
                            .isEqualTo("http");
                        assertThat(span.getAttributes().get(SemanticAttributes.NET_PROTOCOL_VERSION))
                            .isEqualTo("1.1");
                        assertThat(span.getAttributes().get(SemanticAttributes.HTTP_METHOD))
                            .isEqualTo("GET");
                        assertThat(span.getName()).isEqualTo("GET");
                        assertThat(span.getAttributes()
                            .get(AttributeKey.stringArrayKey("http.response.header.my_header")))
                            .isNull();
                        assertThat(span.getAttributes()
                            .get(AttributeKey.stringArrayKey("http.request.header.some_request_header")))
                            .isNull();
                    });
            }
        }
    }

    @Test
    void testInjectWithAParent() throws Exception {
        final String requestUrl = "/path";
        OpenTelemetry openTelemetry = otelTesting.getOpenTelemetry();
        try (ServerContext context = buildServer(openTelemetry)) {
            try (HttpClient client = forSingleAddress(serverHostAndPort(context))
                .appendClientFilter(new OpenTelemetryHttpRequestFilter(openTelemetry, "testClient"))
                .build()) {
                HttpResponse response = client.request(client.get(requestUrl)).toFuture().get();
                TestSpanState serverSpanState = response.payloadBody(SPAN_STATE_SERIALIZER);

                verifyTraceIdPresentInLogs(loggerStringWriter.stableAccumulated(1000), requestUrl,
                    serverSpanState.getTraceId(), serverSpanState.getSpanId(),
                    TRACING_TEST_LOG_LINE_PREFIX);
                assertThat(otelTesting.getSpans()).hasSize(2);
                assertThat(otelTesting.getSpans()).extracting("traceId")
                    .containsExactly(serverSpanState.getTraceId(), serverSpanState.getTraceId());
                assertThat(otelTesting.getSpans()).extracting("spanId")
                    .containsAnyOf(serverSpanState.getSpanId());
                otelTesting.assertTraces()
                    .hasTracesSatisfyingExactly(ta -> ta.hasTraceId(serverSpanState.getTraceId()));

                otelTesting.assertTraces()
                    .hasTracesSatisfyingExactly(ta -> {
                        assertThat(ta.getSpan(0).getAttributes().get(SemanticAttributes.NET_PROTOCOL_NAME))
                            .isEqualTo("http");
                        assertThat(ta.getSpan(0).getAttributes().get(SemanticAttributes.NET_PROTOCOL_VERSION))
                            .isEqualTo("1.1");
                    });
            }
        }
    }

    @Test
    void testInjectWithNewTrace() throws Exception {
        TextMapSetter<HttpURLConnection> setter = HttpURLConnection::setRequestProperty;
        TextMapPropagator textMapPropagator = otelTesting.getOpenTelemetry().getPropagators().getTextMapPropagator();
        try (ServerContext context = buildServer(otelTesting.getOpenTelemetry())) {

            URL url = new URL("http:/" + context.listenAddress() + "/path?query=this&foo=bar");
            Span span = otelTesting.getOpenTelemetry().getTracer("io.serviceTalk").spanBuilder("/")
                .setSpanKind(SpanKind.CLIENT)
                .setAttribute("component", "serviceTalk")
                .startSpan();
            TestSpanState serverSpanState;
            try {
                span
                    .setAttribute(SemanticAttributes.HTTP_METHOD, "GET")
                    .setAttribute(SemanticAttributes.HTTP_URL, url.toString());

                HttpURLConnection con = (HttpURLConnection) url.openConnection();
                textMapPropagator.inject(Context.root().with(span), con, setter);
                con.setRequestMethod("GET");

                int responseCode = con.getResponseCode();
                serverSpanState = new ObjectMapper().readValue(con.getInputStream(), TestSpanState.class);
                assertThat(responseCode).isEqualTo(200);
            } catch (IOException e) {
                throw new RuntimeException(e);
            } finally {
                span.end();
            }
            verifyTraceIdPresentInLogs(loggerStringWriter.stableAccumulated(1000), "/",
                serverSpanState.getTraceId(), serverSpanState.getSpanId(),
                TRACING_TEST_LOG_LINE_PREFIX);
            assertThat(otelTesting.getSpans()).hasSize(2);
            assertThat(otelTesting.getSpans()).extracting("traceId")
                .containsExactly(serverSpanState.getTraceId(), serverSpanState.getTraceId());

            otelTesting.assertTraces()
                .hasTracesSatisfyingExactly(ta -> {
                    assertThat(ta.getSpan(0).getAttributes().get(SemanticAttributes.HTTP_URL))
                        .endsWith(url.toString());
                    assertThat(ta.getSpan(1).getAttributes().get(SemanticAttributes.HTTP_METHOD))
                        .isEqualTo("GET");
                    assertThat(ta.getSpan(0).getAttributes().get(AttributeKey.stringKey("component")))
                        .isEqualTo("serviceTalk");
                });
        }
    }

    @Test
    void testCaptureHeaders() throws Exception {
        final String requestUrl = "/path";
        try (ServerContext context = buildServer(otelTesting.getOpenTelemetry(),
            new OpenTelemetryOptions.Builder()
                .capturedResponseHeaders(singletonList("my-header"))
                .capturedRequestHeaders(singletonList("some-request-header"))
                .build())) {
            try (HttpClient client = forSingleAddress(serverHostAndPort(context)).build()) {
                HttpResponse response = client.request(client.get(requestUrl)
                        .addHeader("some-request-header", "request-header-value"))
                    .toFuture().get();
                TestSpanState serverSpanState = response.payloadBody(SPAN_STATE_SERIALIZER);

                verifyTraceIdPresentInLogs(loggerStringWriter.stableAccumulated(1000), requestUrl,
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
                    .hasTracesSatisfyingExactly(ta -> {
                        SpanData span = ta.getSpan(0);
                        assertThat(
                            span.getAttributes().get(AttributeKey.stringArrayKey("http.response.header.my_header")))
                            .isEqualTo(singletonList("header-value"));
                        assertThat(span.getAttributes()
                            .get(AttributeKey.stringArrayKey("http.request.header.some_request_header")))
                            .isEqualTo(singletonList("request-header-value"));
                    });
            }
        }
    }

    private static ServerContext buildServer(OpenTelemetry givenOpentelemetry,
                                             OpenTelemetryOptions opentelemetryOptions) throws Exception {
        return HttpServers.forAddress(localAddress(0))
            .appendServiceFilter(new OpenTelemetryHttpServerFilter(givenOpentelemetry, opentelemetryOptions))
            .appendServiceFilter(new TestTracingServerLoggerFilter(TRACING_TEST_LOG_LINE_PREFIX))
            .listenAndAwait((ctx, request, responseFactory) -> {
                final ContextPropagators propagators = givenOpentelemetry.getPropagators();
                final Context context = Context.root();
                Context tracingContext = propagators.getTextMapPropagator()
                    .extract(context, request.headers(), HeadersPropagatorGetter.INSTANCE);
                Span span = Span.current();
                if (!span.getSpanContext().isValid()) {
                    span = Span.fromContext(tracingContext);
                }
                return succeeded(
                    responseFactory.ok()
                        .addHeader("my-header", "header-value")
                        .payloadBody(new TestSpanState(span.getSpanContext()), SPAN_STATE_SERIALIZER));
            });
    }

    private static ServerContext buildServer(OpenTelemetry givenOpentelemetry) throws Exception {
        return buildServer(givenOpentelemetry, new OpenTelemetryOptions.Builder().build());
    }
}

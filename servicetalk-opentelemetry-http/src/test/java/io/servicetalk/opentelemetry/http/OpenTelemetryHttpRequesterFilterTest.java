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

import io.servicetalk.client.api.TransportObserverConnectionFactoryFilter;
import io.servicetalk.http.api.HttpClient;
import io.servicetalk.http.api.HttpRequest;
import io.servicetalk.http.api.HttpResponse;
import io.servicetalk.http.api.HttpServerBuilder;
import io.servicetalk.http.netty.HttpServers;
import io.servicetalk.log4j2.mdc.utils.LoggerStringWriter;
import io.servicetalk.transport.api.ConnectionInfo;
import io.servicetalk.transport.api.ConnectionObserver;
import io.servicetalk.transport.api.HostAndPort;
import io.servicetalk.transport.api.ServerContext;
import io.servicetalk.transport.api.TransportObserver;
import io.servicetalk.transport.netty.internal.NoopTransportObserver;

import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import io.opentelemetry.context.propagation.ContextPropagators;
import io.opentelemetry.sdk.testing.junit5.OpenTelemetryExtension;
import io.opentelemetry.sdk.trace.data.EventData;
import io.opentelemetry.sdk.trace.data.ExceptionEventData;
import io.opentelemetry.sdk.trace.data.SpanData;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.invoke.MethodHandles;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicReference;
import javax.annotation.Nullable;

import static io.opentelemetry.semconv.HttpAttributes.HTTP_REQUEST_METHOD;
import static io.opentelemetry.semconv.NetworkAttributes.NETWORK_PROTOCOL_NAME;
import static io.opentelemetry.semconv.NetworkAttributes.NETWORK_PROTOCOL_VERSION;
import static io.opentelemetry.semconv.UrlAttributes.URL_FULL;
import static io.servicetalk.concurrent.api.Single.succeeded;
import static io.servicetalk.concurrent.internal.TestTimeoutConstants.CI;
import static io.servicetalk.http.api.HttpHeaderNames.HOST;
import static io.servicetalk.http.netty.HttpClients.forSingleAddress;
import static io.servicetalk.log4j2.mdc.utils.LoggerStringWriter.assertContainsMdcPair;
import static io.servicetalk.opentelemetry.http.AbstractOpenTelemetryFilter.DEFAULT_OPTIONS;
import static io.servicetalk.opentelemetry.http.OpenTelemetryHttpRequesterFilter.PEER_SERVICE;
import static io.servicetalk.opentelemetry.http.TestUtils.SPAN_STATE_SERIALIZER;
import static io.servicetalk.opentelemetry.http.TestUtils.TRACING_TEST_LOG_LINE_PREFIX;
import static io.servicetalk.opentelemetry.http.TestUtils.TestTracingClientLoggerFilter;
import static io.servicetalk.transport.netty.internal.AddressUtils.localAddress;
import static io.servicetalk.transport.netty.internal.AddressUtils.serverHostAndPort;
import static java.util.Collections.singletonList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@ExtendWith(MockitoExtension.class)
class OpenTelemetryHttpRequesterFilterTest {

    private static final Logger LOGGER = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());
    private static final int SLEEP_TIME = CI ? 500 : 100;

    private final LoggerStringWriter loggerStringWriter = new LoggerStringWriter();

    @RegisterExtension
    static final OpenTelemetryExtension otelTesting = OpenTelemetryExtension.create();

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
        final String requestUrl = "/";
        OpenTelemetry openTelemetry = otelTesting.getOpenTelemetry();
        try (ServerContext context = buildServer(openTelemetry, false)) {
            try (HttpClient client = forSingleAddress(serverHostAndPort(context))
                .appendClientFilter(new OpenTelemetryHttpRequesterFilter(openTelemetry, "testClient",
                        DEFAULT_OPTIONS))
                .appendClientFilter(new TestTracingClientLoggerFilter(TRACING_TEST_LOG_LINE_PREFIX)).build()) {
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
                    .hasTracesSatisfyingExactly(ta ->
                        assertThat(ta.getSpan(0).getAttributes().get(NETWORK_PROTOCOL_NAME))
                            .isNull()); // only needs to be set if != http
            }
        }
    }

    @Test
    void testInjectWithAParent() throws Exception {
        final String requestUrl = "/path";
        OpenTelemetry openTelemetry = otelTesting.getOpenTelemetry();
        try (ServerContext context = buildServer(openTelemetry, true)) {
            try (HttpClient client = forSingleAddress(serverHostAndPort(context))
                .appendClientFilter(new OpenTelemetryHttpRequesterFilter(openTelemetry, "testClient",
                        DEFAULT_OPTIONS))
                .appendClientFilter(new TestTracingClientLoggerFilter(TRACING_TEST_LOG_LINE_PREFIX)).build()) {
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
                    .hasTracesSatisfyingExactly(ta ->
                        ta.hasTraceId(serverSpanState.getTraceId()));

                otelTesting.assertTraces()
                    .hasTracesSatisfyingExactly(ta -> {
                        SpanData span = ta.getSpan(0);
                        assertThat(span.getAttributes().get(HTTP_REQUEST_METHOD))
                            .isEqualTo("GET");
                        // This is deprecated: the recommended thing to do is capture the header. The real intent is
                        // likely to use 'http.response.body.size' which is a 'development' stage attribute.
                        // These are in the instrumentation-api-incubator package, which is still considered alpha.
                        //  assertThat(span.getAttributes().get(SemanticAttributes.HTTP_RESPONSE_CONTENT_LENGTH))
                        //     .isGreaterThan(0);
                        assertThat(span.getAttributes().get(NETWORK_PROTOCOL_VERSION))
                            .isEqualTo("1.1");
                        assertThat(span.getAttributes().get(NETWORK_PROTOCOL_NAME))
                            .isNull(); // this attribute is optional unless it's something other than 'http'
                        assertThat(span.getAttributes().get(PEER_SERVICE))
                            .isEqualTo("testClient");
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

    @ParameterizedTest(name = "{displayName} [{index}]: absoluteForm={0}, withHostHeader={1}")
    @CsvSource({"false, false", "false, true", "true, false", "true, true"})
    void testInjectWithAParentCreated(boolean absoluteForm, boolean withHostHeader) throws Exception {
        OpenTelemetry openTelemetry = otelTesting.getOpenTelemetry();
        try (ServerContext context = buildServer(openTelemetry, true)) {
            HostAndPort serverHostAndPort = serverHostAndPort(context);
            final String requestPath = "/path/to/resource";
            final String requestUrl = absoluteForm ? fullUrl(serverHostAndPort, requestPath) : requestPath;
            try (HttpClient client = forSingleAddress(serverHostAndPort)
                    .appendClientFilter(new OpenTelemetryHttpRequesterFilter(openTelemetry, "testClient",
                            DEFAULT_OPTIONS))
                    .appendClientFilter(new TestTracingClientLoggerFilter(TRACING_TEST_LOG_LINE_PREFIX)).build()) {

                Span span = otelTesting.getOpenTelemetry().getTracer("io.serviceTalk").spanBuilder("/")
                        .setSpanKind(SpanKind.INTERNAL)
                        .setAttribute("component", "serviceTalk")
                        .startSpan();
                TestSpanState serverSpanState;
                try (Scope scope = span.makeCurrent()) {
                    LOGGER.info("making span={} current", span);
                    HttpRequest request = client.get(requestUrl);
                    if (withHostHeader) {
                        request.setHeader(HOST, serverHostAndPort.hostName() + ":" + serverHostAndPort.port());
                    }
                    HttpResponse response = client.request(request).toFuture().get();
                    serverSpanState = response.payloadBody(SPAN_STATE_SERIALIZER);
                } finally {
                    span.end();
                }
                verifyTraceIdPresentInLogs(loggerStringWriter.stableAccumulated(1000), requestPath,
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
                        .hasTracesSatisfyingExactly(ta -> {
                            assertThat(ta.getSpan(0).getAttributes().get(AttributeKey.stringKey("component")))
                                    .isEqualTo("serviceTalk");
                            assertThat(ta.getSpan(1).getParentSpanId()).isEqualTo(ta.getSpan(0).getSpanId());
                            if (absoluteForm || withHostHeader) {
                                assertThat(ta.getSpan(1).getAttributes().get(URL_FULL)).isEqualTo(
                                        fullUrl(serverHostAndPort, requestPath));
                            } else {
                                assertThat(ta.getSpan(1).getAttributes().get(URL_FULL)).isNull();
                            }
                            assertThat(ta.getSpan(1).getAttributes().get(NETWORK_PROTOCOL_NAME))
                                    .isNull(); // only needs to be set if != http
                            assertThat(ta.getSpan(2).getParentSpanId()).isEqualTo(ta.getSpan(1).getSpanId());
                        });
            }
        }
    }

    @Test
    void testCaptureHeader() throws Exception {
        final String requestUrl = "/";
        OpenTelemetry openTelemetry = otelTesting.getOpenTelemetry();
        try (ServerContext context = buildServer(openTelemetry, false)) {
            try (HttpClient client = forSingleAddress(serverHostAndPort(context))
                .appendClientFilter(new OpenTelemetryHttpRequesterFilter(openTelemetry, "testClient",
                    new OpenTelemetryOptions.Builder()
                        .capturedResponseHeaders(singletonList("my-header"))
                        .capturedRequestHeaders(singletonList("some-request-header"))
                        .build()))
                .appendClientFilter(new TestTracingClientLoggerFilter(TRACING_TEST_LOG_LINE_PREFIX)).build()) {
                HttpResponse response = client.request(client.get(requestUrl)
                    .addHeader("some-request-header", "request-header-value")).toFuture().get();
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
                        assertThat(span.getAttributes()
                            .get(AttributeKey.stringArrayKey("http.response.header.my-header")))
                            .isEqualTo(singletonList("header-value"));
                        assertThat(span.getAttributes()
                            .get(AttributeKey.stringArrayKey("http.request.header.some-request-header")))
                            .isEqualTo(singletonList("request-header-value"));
                    });
            }
        }
    }

    @ParameterizedTest(name = "{displayName} [{index}]: transportFailure={0}")
    @ValueSource(booleans = {true, false})
    void transportObserver(boolean transportFailure) throws Exception {
        final String requestUrl = "/";
        OpenTelemetry openTelemetry = otelTesting.getOpenTelemetry();
        BlockingQueue<Error> errors = new LinkedBlockingQueue<>();
        TransportObserver transportObserver = new TransportObserver() {

            final AtomicReference<Span> span = new AtomicReference<>();

            private void checkSpan(String eventName) {
                Span current = Span.current();
                if (!current.equals(span.get())) {
                    errors.add(new AssertionError("Unexpected span: " + current +
                            " (expected " + span.get() + ")."));
                }
                current.addEvent(eventName);
            }

            private void checkNoSpan() {
                Span current = Span.current();
                if (!current.equals(Span.getInvalid())) {
                    errors.add(new AssertionError("Unexpected span: " + current +
                            " (expected " + span.get() + ")."));
                }
            }

            @Override
            public ConnectionObserver onNewConnection(@Nullable Object localAddress, Object remoteAddress) {
                if (!span.compareAndSet(null, Span.current()) && !transportFailure) {
                    // If we expect failures then we expect retries, so it's fine.
                    errors.add(new AssertionError("onNewConnection called multiple times"));
                }
                return new ConnectionObserver() {
                    @Override
                    public void onDataRead(int size) {
                        checkNoSpan();
                    }

                    @Override
                    public void onDataWrite(int size) {
                        checkNoSpan();
                    }

                    @Override
                    public void onFlush() {
                        checkNoSpan();
                    }

                    @Override
                    public DataObserver connectionEstablished(ConnectionInfo info) {
                        if (transportFailure) {
                            errors.add(new AssertionError("Unexpected connection"));
                        } else {
                            checkSpan("connectionEstablished");
                        }
                        return NoopTransportObserver.NoopDataObserver.INSTANCE;
                    }

                    @Override
                    public MultiplexedObserver multiplexedConnectionEstablished(ConnectionInfo info) {
                        if (transportFailure) {
                            errors.add(new AssertionError("Unexpected connection"));
                        } else {
                            checkSpan("multiplexedConnectionEstablished");
                        }
                        return NoopTransportObserver.NoopMultiplexedObserver.INSTANCE;
                    }

                    @Override
                    public void connectionClosed(Throwable error) {
                        if (transportFailure) {
                            checkSpan("connectionClosed(error)");
                        } else {
                            checkNoSpan();
                        }
                    }

                    @Override
                    public void connectionClosed() {
                        if (transportFailure) {
                            checkSpan("connectionClosed()");
                        } else {
                            checkNoSpan();
                        }
                    }
                };
            }
        };

        ServerContext context = buildServer(openTelemetry, false);
        try (HttpClient client = forSingleAddress(serverHostAndPort(context))
                .appendClientFilter(new OpenTelemetryHttpRequestFilter(openTelemetry, "testClient"))
                .appendClientFilter(new TestTracingClientLoggerFilter(TRACING_TEST_LOG_LINE_PREFIX))
                .appendConnectionFactoryFilter(
                        new TransportObserverConnectionFactoryFilter<>(transportObserver)).build()) {
            // This is necessary to let the load balancer become ready
            Thread.sleep(SLEEP_TIME);

            final HttpResponse response;
            final TestSpanState serverSpanState;
            if (transportFailure) {
                context.close();
                context = null;
                assertThrows(Exception.class, () -> client.request(client.get(requestUrl)).toFuture().get());
                Thread.sleep(SLEEP_TIME);
                otelTesting.assertTraces().hasTracesSatisfyingExactly(ta ->
                    ta.hasSpansSatisfyingExactly(span ->
                        span.hasEventsSatisfying(eventData -> {
                            for (EventData data : eventData) {
                                if (!(data instanceof ExceptionEventData)) {
                                    assertThat(data.getName()).isEqualTo("connectionClosed(error)");
                                }
                            }
                        })));
            } else {
                response = client.request(client.get(requestUrl)).toFuture().get();
                serverSpanState = response.payloadBody(SPAN_STATE_SERIALIZER);

                verifyTraceIdPresentInLogs(loggerStringWriter.stableAccumulated(1000), requestUrl,
                        serverSpanState.getTraceId(), serverSpanState.getSpanId(),
                        TRACING_TEST_LOG_LINE_PREFIX);
                Thread.sleep(SLEEP_TIME);
                assertThat(otelTesting.getSpans()).hasSize(1);
                assertThat(otelTesting.getSpans()).extracting("traceId")
                        .containsExactly(serverSpanState.getTraceId());
                assertThat(otelTesting.getSpans()).extracting("spanId")
                        .containsAnyOf(serverSpanState.getSpanId());
                otelTesting.assertTraces()
                        .hasTracesSatisfyingExactly(ta -> {
                            ta.hasTraceId(serverSpanState.getTraceId());
                            ta.hasSpansSatisfyingExactly(span -> span.hasEventsSatisfying(eventData -> {
                                assertThat(eventData).hasSize(1);
                                assertThat(eventData.get(0).getName()).isEqualTo("connectionEstablished");
                            }));
                        });
            }
        } finally {
            if (context != null) {
                context.close();
            }
        }
        if (!errors.isEmpty()) {
            throw errors.poll();
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
                Context tracingContext = propagators.getTextMapPropagator()
                    .extract(context, request.headers(), HeadersPropagatorGetter.INSTANCE);
                Span span = Span.fromContext(tracingContext);
                return succeeded(
                    responseFactory.ok().addHeader("my-header", "header-value")
                        .payloadBody(new TestSpanState(span.getSpanContext()), SPAN_STATE_SERIALIZER));
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

    private static String fullUrl(HostAndPort serverHostAndPort, String requestTarget) {
        return "http://" + serverHostAndPort.hostName() + ":" + serverHostAndPort.port() + requestTarget;
    }
}

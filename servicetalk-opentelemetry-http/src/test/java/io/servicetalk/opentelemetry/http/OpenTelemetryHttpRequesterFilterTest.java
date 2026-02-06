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
import io.servicetalk.concurrent.api.Single;
import io.servicetalk.http.api.HttpClient;
import io.servicetalk.http.api.HttpRequest;
import io.servicetalk.http.api.HttpResponse;
import io.servicetalk.http.api.HttpServerBuilder;
import io.servicetalk.http.api.StreamingHttpClientFilter;
import io.servicetalk.http.api.StreamingHttpRequest;
import io.servicetalk.http.api.StreamingHttpRequester;
import io.servicetalk.http.api.StreamingHttpResponse;
import io.servicetalk.http.netty.HttpLifecycleObserverRequesterFilter;
import io.servicetalk.log4j2.mdc.utils.LoggerStringWriter;
import io.servicetalk.transport.api.ConnectionInfo;
import io.servicetalk.transport.api.ConnectionObserver;
import io.servicetalk.transport.api.HostAndPort;
import io.servicetalk.transport.api.ServerContext;
import io.servicetalk.transport.api.TransportObserver;

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
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.invoke.MethodHandles;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.Future;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Stream;
import javax.annotation.Nullable;

import static io.opentelemetry.api.internal.InstrumentationUtil.suppressInstrumentation;
import static io.opentelemetry.semconv.HttpAttributes.HTTP_REQUEST_METHOD;
import static io.opentelemetry.semconv.NetworkAttributes.NETWORK_PROTOCOL_NAME;
import static io.opentelemetry.semconv.NetworkAttributes.NETWORK_PROTOCOL_VERSION;
import static io.opentelemetry.semconv.ServerAttributes.SERVER_ADDRESS;
import static io.opentelemetry.semconv.ServerAttributes.SERVER_PORT;
import static io.opentelemetry.semconv.UrlAttributes.URL_FULL;
import static io.servicetalk.concurrent.api.Single.succeeded;
import static io.servicetalk.http.api.HttpHeaderNames.HOST;
import static io.servicetalk.log4j2.mdc.utils.LoggerStringWriter.assertContainsMdcPair;
import static io.servicetalk.opentelemetry.http.AbstractOpenTelemetryFilter.PEER_SERVICE;
import static io.servicetalk.opentelemetry.http.AbstractOpenTelemetryHttpRequesterFilter.SHOULD_INSTRUMENT_KEY;
import static io.servicetalk.opentelemetry.http.TestUtils.SPAN_STATE_SERIALIZER;
import static io.servicetalk.opentelemetry.http.TestUtils.TRACING_TEST_LOG_LINE_PREFIX;
import static io.servicetalk.opentelemetry.http.TestUtils.TestTracingClientLoggerFilter;
import static io.servicetalk.opentelemetry.http.TestUtils.clientBuilder;
import static io.servicetalk.opentelemetry.http.TestUtils.sleep;
import static io.servicetalk.transport.netty.internal.AddressUtils.serverHostAndPort;
import static java.util.Collections.singletonList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@ExtendWith(MockitoExtension.class)
class OpenTelemetryHttpRequesterFilterTest {

    private static final Logger LOGGER = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

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

    @ParameterizedTest(name = "{displayName} [{index}] useHttp2={0}")
    @ValueSource(booleans = {true, false})
    void testInjectWithNoParent(boolean useHttp2) throws Exception {
        final String requestUrl = "/";
        OpenTelemetry openTelemetry = otelTesting.getOpenTelemetry();
        try (ServerContext context = buildServer(useHttp2, false)) {
            try (HttpClient client = clientBuilder(useHttp2, context)
                .appendClientFilter(new OpenTelemetryHttpRequesterFilter.Builder().openTelemetry(openTelemetry)
                        .componentName("testClient").build())
                .appendClientFilter(new TestTracingClientLoggerFilter(TRACING_TEST_LOG_LINE_PREFIX)).build()) {
                HttpResponse response = client.request(client.get(requestUrl)).toFuture().get();
                TestSpanState serverSpanState = response.payloadBody(SPAN_STATE_SERIALIZER);

                // Allow span export to complete before assertions to avoid flakiness, especially on HTTP/2.
                sleep();

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

    @ParameterizedTest(name = "{displayName} [{index}] useHttp2={0}")
    @ValueSource(booleans = {true, false})
    void testInjectWithAParent(boolean useHttp2) throws Exception {
        final String requestUrl = "/path";
        OpenTelemetry openTelemetry = otelTesting.getOpenTelemetry();
        try (ServerContext context = buildServer(useHttp2, true)) {
            try (HttpClient client = clientBuilder(useHttp2, context)
                .appendClientFilter(new OpenTelemetryHttpRequesterFilter.Builder().openTelemetry(openTelemetry)
                        .componentName("testClient").build())
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
                            .isEqualTo(useHttp2 ? "2.0" : "1.1");
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

    static Stream<Arguments> testInjectWithAParentCreatedArguments() {
        return Stream.of(true, false).flatMap(absoluteForm ->
                Stream.of(true, false).flatMap(withHostHeader ->
                        Stream.of(Arguments.of(absoluteForm, withHostHeader, true),
                                Arguments.of(absoluteForm, withHostHeader, false))));
    }

    @ParameterizedTest(name = "{displayName} [{index}]: absoluteForm={0}, withHostHeader={1}, useHttp2={2}")
    @MethodSource("testInjectWithAParentCreatedArguments")
    void testInjectWithAParentCreated(boolean absoluteForm, boolean withHostHeader, boolean useHttp2) throws Exception {
        OpenTelemetry openTelemetry = otelTesting.getOpenTelemetry();
        try (ServerContext context = buildServer(useHttp2, true)) {
            HostAndPort serverHostAndPort = serverHostAndPort(context);
            final String requestPath = "/path/to/resource";
            final String requestUrl = absoluteForm ? fullUrl(serverHostAndPort, requestPath) : requestPath;
            try (HttpClient client = clientBuilder(useHttp2, context)
                    .appendClientFilter(new OpenTelemetryHttpRequesterFilter.Builder().openTelemetry(openTelemetry)
                            .componentName("testClient").build())
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
                            assertThat(ta.getSpan(1).getAttributes().get(URL_FULL)).isEqualTo(
                                    fullUrl(serverHostAndPort, requestPath));
                            assertThat(ta.getSpan(1).getAttributes().get(SERVER_ADDRESS)).isEqualTo(
                                    serverHostAndPort.hostName());
                            assertThat(ta.getSpan(1).getAttributes().get(SERVER_PORT)).isEqualTo(
                                    serverHostAndPort.port());
                            assertThat(ta.getSpan(1).getAttributes().get(NETWORK_PROTOCOL_NAME))
                                    .isNull(); // only needs to be set if != http
                            assertThat(ta.getSpan(2).getParentSpanId()).isEqualTo(ta.getSpan(1).getSpanId());
                        });
            }
        }
    }

    @ParameterizedTest(name = "{displayName} [{index}] useHttp2={0}")
    @ValueSource(booleans = {true, false})
    void testCaptureHeader(boolean useHttp2) throws Exception {
        final String requestUrl = "/";
        OpenTelemetry openTelemetry = otelTesting.getOpenTelemetry();
        try (ServerContext context = buildServer(useHttp2, false)) {
            try (HttpClient client = clientBuilder(useHttp2, context)
                .appendClientFilter(
                        new OpenTelemetryHttpRequesterFilter.Builder().openTelemetry(openTelemetry)
                                .componentName("testClient")
                                .capturedResponseHeaders(singletonList("my-header"))
                                .capturedRequestHeaders(singletonList("some-request-header"))
                                .build())
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

    private enum ResultType {
        SUCCESS,
        TRANSPORT_ERROR,
        CANCEL
    }

    static Stream<Arguments> transportObserverArguments() {
        return Stream.of(ResultType.values())
                .flatMap(resultType -> Stream.of(
                        Arguments.of(resultType, true),
                        Arguments.of(resultType, false)));
    }

    @ParameterizedTest(name = "{displayName} [{index}]: resultType={0}, useHttp2={1}")
    @MethodSource("transportObserverArguments")
    void transportObserver(final ResultType resultType, boolean useHttp2) throws Exception {
        final boolean transportFailure = resultType == ResultType.TRANSPORT_ERROR;
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
                        return ConnectionObserver.super.connectionEstablished(info);
                    }

                    @Override
                    public MultiplexedObserver multiplexedConnectionEstablished(ConnectionInfo info) {
                        if (transportFailure) {
                            errors.add(new AssertionError("Unexpected connection"));
                        } else {
                            checkSpan("multiplexedConnectionEstablished");
                        }
                        return ConnectionObserver.super.multiplexedConnectionEstablished(info);
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

        ServerContext context = buildServer(useHttp2, false);
        try (HttpClient client = clientBuilder(useHttp2, context)
                .appendClientFilter(new OpenTelemetryHttpRequesterFilter.Builder()
                        .openTelemetry(openTelemetry)
                        .componentName("testClient")
                        .build())
                .appendClientFilter(new TestTracingClientLoggerFilter(TRACING_TEST_LOG_LINE_PREFIX))
                .appendClientFilter(new HttpLifecycleObserverRequesterFilter(
                        new TestHttpLifecycleObserver(errors)))
                .appendConnectionFactoryFilter(
                        new TransportObserverConnectionFactoryFilter<>(transportObserver)).build()) {
           // This is necessary to let the load balancer become ready
           sleep();

            final HttpResponse response;
            final TestSpanState serverSpanState;
            switch (resultType) {
                case SUCCESS:
                    response = client.request(client.get("/")).toFuture().get();
                    serverSpanState = response.payloadBody(SPAN_STATE_SERIALIZER);

                    verifyTraceIdPresentInLogs(loggerStringWriter.stableAccumulated(1000), "/",
                            serverSpanState.getTraceId(), serverSpanState.getSpanId(),
                            TRACING_TEST_LOG_LINE_PREFIX);
                    sleep();
                    assertThat(otelTesting.getSpans()).hasSize(1);
                    assertThat(otelTesting.getSpans()).extracting("traceId")
                            .containsExactly(serverSpanState.getTraceId());
                    assertThat(otelTesting.getSpans()).extracting("spanId")
                            .containsAnyOf(serverSpanState.getSpanId());
                    otelTesting.assertTraces()
                            .hasTracesSatisfyingExactly(ta -> {
                                ta.hasTraceId(serverSpanState.getTraceId());
                                ta.hasSpansSatisfyingExactly(span -> {
                                    span.hasEventsSatisfying(eventData -> {
                                        assertThat(eventData).hasSize(1);
                                        assertThat(eventData.get(0).getName()).isEqualTo(useHttp2 ?
                                                "multiplexedConnectionEstablished" : "connectionEstablished");
                                    });
                                    span.hasAttribute(TestHttpLifecycleObserver.ON_NEW_EXCHANGE_KEY, "set");
                                    span.hasAttribute(TestHttpLifecycleObserver.ON_REQUEST_KEY, "set");
                                    span.hasAttribute(TestHttpLifecycleObserver.ON_RESPONSE_DATA_KEY, "set");
                                    span.hasAttribute(TestHttpLifecycleObserver.ON_RESPONSE_COMPLETE_KEY, "set");
                                    span.hasAttribute(TestHttpLifecycleObserver.ON_EXCHANGE_FINALLY_KEY, "set");
                                });
                            });
                    break;
                case CANCEL:
                    Future<HttpResponse> result = client.request(client.get("/slow")).toFuture();
                    TestUtils.sleep(20);
                    result.cancel(true);
                    sleep();
                    otelTesting.assertTraces().hasTracesSatisfyingExactly(ta ->
                            ta.hasSpansSatisfyingExactly(span -> {
                                span.hasAttribute(TestHttpLifecycleObserver.ON_NEW_EXCHANGE_KEY, "set");
                                span.hasAttribute(TestHttpLifecycleObserver.ON_REQUEST_KEY, "set");
                                span.hasAttribute(TestHttpLifecycleObserver.ON_RESPONSE_CANCEL_KEY, "set");
                                span.hasAttribute(TestHttpLifecycleObserver.ON_EXCHANGE_FINALLY_KEY, "set");
                            }));
                    break;
                case TRANSPORT_ERROR:
                    context.close();
                    context = null;
                    assertThrows(Exception.class, () -> client.request(client.get("/")).toFuture().get());
                    sleep();
                    otelTesting.assertTraces().hasTracesSatisfyingExactly(ta ->
                            ta.hasSpansSatisfyingExactly(span -> {
                                span.hasEventsSatisfying(eventData -> {
                                    for (EventData data : eventData) {
                                        if (!(data instanceof ExceptionEventData)) {
                                            assertThat(data.getName()).isEqualTo("connectionClosed(error)");
                                        }
                                    }
                                });
                                span.hasAttribute(TestHttpLifecycleObserver.ON_NEW_EXCHANGE_KEY, "set");
                                span.hasAttribute(TestHttpLifecycleObserver.ON_REQUEST_KEY, "set");
                                span.hasAttribute(TestHttpLifecycleObserver.ON_RESPONSE_ERROR_KEY, "set");
                                span.hasAttribute(TestHttpLifecycleObserver.ON_EXCHANGE_FINALLY_KEY, "set");
                            }));
                    break;
                default:
                    throw new IllegalStateException("Unexpected resultType: " + resultType);
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

    @ParameterizedTest(name = "{displayName} [{index}] useHttp2={0}")
    @ValueSource(booleans = {true, false})
    void stackingClientAndConnectionFilters(boolean useHttp2) throws Exception {
        final String requestUrl = "/client-span-test";
        OpenTelemetry openTelemetry = otelTesting.getOpenTelemetry();
        try (ServerContext context = buildServer(useHttp2, true)) {
            OpenTelemetryHttpRequesterFilter filter = new OpenTelemetryHttpRequesterFilter.Builder()
                    .openTelemetry(openTelemetry)
                    .componentName("testClient")
                    .build();
            try (HttpClient client = clientBuilder(useHttp2, context)
                    .appendClientFilter(filter)
                    .appendConnectionFilter(filter)
                    .build()) {
                    client.request(client.get(requestUrl)).toFuture().get();
                sleep();

                // Should have 3 spans: logical client span, physical client span, and server span
                assertThat(otelTesting.getSpans()).hasSize(3);
                otelTesting.assertTraces().hasTracesSatisfyingExactly(ta -> ta.hasSpansSatisfyingExactly(
                        span -> {
                            span.hasKind(SpanKind.CLIENT);
                            span.hasName("GET");
                        },
                        span -> {
                            span.hasKind(SpanKind.CLIENT);
                            span.hasName("Physical GET"); // we should detect that this is a second filter.
                        },
                        span -> {
                            span.hasKind(SpanKind.SERVER);
                            span.hasName("GET");
                        }
                ));
            }
        }
    }

    @ParameterizedTest(name = "{displayName} [{index}] useHttp2={0}")
    @ValueSource(booleans = {true, false})
    void clientFilterOnly(boolean useHttp2) throws Exception {
        final String requestUrl = "/client-span-test";
        OpenTelemetry openTelemetry = otelTesting.getOpenTelemetry();
        try (ServerContext context = buildServer(useHttp2, true)) {
            OpenTelemetryHttpRequesterFilter filter = new OpenTelemetryHttpRequesterFilter.Builder()
                    .openTelemetry(openTelemetry)
                    .componentName("testClient")
                    .build();
            try (HttpClient client = clientBuilder(useHttp2, context)
                    .appendClientFilter(filter)
                    .build()) {
                client.request(client.get(requestUrl)).toFuture().get();
                sleep();

                // Should have 2 spans: logical client span and server span
                otelTesting.assertTraces().hasTracesSatisfyingExactly(ta -> ta.hasSpansSatisfyingExactly(
                        span -> {
                            span.hasKind(SpanKind.CLIENT);
                            span.hasName("GET");
                        },
                        span -> {
                            span.hasKind(SpanKind.SERVER);
                            span.hasName("GET");
                        }
                ));
            }
        }
    }

    @ParameterizedTest(name = "{displayName} [{index}] useHttp2={0}")
    @ValueSource(booleans = {true, false})
    void connectionFilterOnly(boolean useHttp2) throws Exception {
        final String requestUrl = "/client-span-test";
        OpenTelemetry openTelemetry = otelTesting.getOpenTelemetry();
        try (ServerContext context = buildServer(useHttp2, true)) {
            OpenTelemetryHttpRequesterFilter filter = new OpenTelemetryHttpRequesterFilter.Builder()
                    .openTelemetry(openTelemetry)
                    .componentName("testClient")
                    .build();
            try (HttpClient client = clientBuilder(useHttp2, context)
                    .appendConnectionFilter(filter)
                    .build()) {
                client.request(client.get(requestUrl)).toFuture().get();
                sleep();

                // Should have 2 spans: physical client span and server span
                otelTesting.assertTraces().hasTracesSatisfyingExactly(ta -> ta.hasSpansSatisfyingExactly(
                        span -> {
                            span.hasKind(SpanKind.CLIENT);
                            span.hasName("GET");
                        },
                        span -> {
                            span.hasKind(SpanKind.SERVER);
                            span.hasName("GET");
                        }
                ));
            }
        }
    }

    @ParameterizedTest(name = "{displayName} [{index}] useHttp2={0}")
    @ValueSource(booleans = {true, false})
    void suppressionIsHonoredByFilterInAllPositions(boolean useHttp2) throws Exception {
        final String requestUrl = "/client-span-test";
        OpenTelemetry openTelemetry = otelTesting.getOpenTelemetry();
        try (ServerContext context = buildServer(useHttp2, true)) {
            OpenTelemetryHttpRequesterFilter filter = new OpenTelemetryHttpRequesterFilter.Builder()
                    .openTelemetry(openTelemetry)
                    .componentName("testClient")
                    .build();
            try (HttpClient client = clientBuilder(useHttp2, context)
                    // Add the filter in both positions.
                    .appendClientFilter(filter)
                    .appendConnectionFilter(filter)
                    .build()) {
                suppressInstrumentation(() -> {
                    try {
                        client.request(client.get(requestUrl)).toFuture().get();
                    } catch (Exception ex) {
                        throw new RuntimeException(ex);
                    }
                });
            }
            sleep();

            // Should have 1 span: server span
            assertThat(otelTesting.getSpans()).hasSize(1);
            assertThat(otelTesting.getSpans().get(0).getKind()).isEqualTo(SpanKind.SERVER);
        }
    }

    @ParameterizedTest(name = "{displayName} [{index}] useHttp2={0}")
    @ValueSource(booleans = {true, false})
    void suppressionContextKeyIsntLeaked(boolean useHttp2) throws Exception {
        final String requestUrl = "/client-span-test";
        OpenTelemetry openTelemetry = otelTesting.getOpenTelemetry();
        AtomicReference<Boolean> contextValueInPipeline = new AtomicReference<>();
        AtomicReference<Boolean> contextValueAtResponse = new AtomicReference<>();
        try (ServerContext context = buildServer(useHttp2, true)) {
            OpenTelemetryHttpRequesterFilter filter = new OpenTelemetryHttpRequesterFilter.Builder()
                    .openTelemetry(openTelemetry)
                    .componentName("testClient")
                    .build();
            try (HttpClient client = clientBuilder(useHttp2, context)
                    // Add the filter in both positions.
                    .appendClientFilter(filter)
                    .appendClientFilter(c -> new StreamingHttpClientFilter(c) {
                        @Override
                        protected Single<StreamingHttpResponse> request(StreamingHttpRequester delegate,
                                                                        StreamingHttpRequest request) {
                            contextValueInPipeline.set(Context.current().get(SHOULD_INSTRUMENT_KEY));
                            return delegate().request(request);
                        }
                    })
                    .appendConnectionFilter(filter)
                    .build()) {
                client.request(client.get(requestUrl)).map(resp -> {
                    contextValueAtResponse.set(Context.current().get(SHOULD_INSTRUMENT_KEY));
                    return resp;
                }).toFuture().get();
            }
            sleep();

            // Should have 3 spans: server span, logical client span, and physical client span.
            assertThat(otelTesting.getSpans()).hasSize(3);
            // Our context value should be present in the filter pipeline but shouldn't leak past the request call
            // or else it would affect sequenced client calls.
            assertThat(contextValueInPipeline).hasValue(true);
            assertThat(contextValueAtResponse).hasNullValue();
        }
    }

    private static ServerContext buildServer(boolean useHttp2, boolean addFilter) throws Exception {
        OpenTelemetry openTelemetry = otelTesting.getOpenTelemetry();
        HttpServerBuilder httpServerBuilder = TestUtils.httpServerBuilder(useHttp2);
        if (addFilter) {
            httpServerBuilder =
                httpServerBuilder.appendServiceFilter(new OpenTelemetryHttpServiceFilter.Builder()
                        .openTelemetry(openTelemetry)
                        .build());
        }
        return httpServerBuilder
            .listenAndAwait((ctx, request, responseFactory) -> {
                if ("/slow".equals(request.path())) {
                    return Single.never();
                }
                final ContextPropagators propagators = openTelemetry.getPropagators();
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

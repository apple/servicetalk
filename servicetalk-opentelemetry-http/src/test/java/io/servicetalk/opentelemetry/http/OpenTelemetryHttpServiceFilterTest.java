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

import io.servicetalk.buffer.api.Buffer;
import io.servicetalk.buffer.api.ReadOnlyBufferAllocators;
import io.servicetalk.concurrent.api.Publisher;
import io.servicetalk.concurrent.api.Single;
import io.servicetalk.http.api.Http2Exception;
import io.servicetalk.http.api.HttpClient;
import io.servicetalk.http.api.HttpExecutionStrategies;
import io.servicetalk.http.api.HttpExecutionStrategy;
import io.servicetalk.http.api.HttpHeaders;
import io.servicetalk.http.api.HttpProtocolConfig;
import io.servicetalk.http.api.HttpRequest;
import io.servicetalk.http.api.HttpResponse;
import io.servicetalk.http.api.HttpResponseStatus;
import io.servicetalk.http.api.HttpServerBuilder;
import io.servicetalk.http.api.HttpServiceContext;
import io.servicetalk.http.api.StatelessTrailersTransformer;
import io.servicetalk.http.api.StreamingHttpClient;
import io.servicetalk.http.api.StreamingHttpRequest;
import io.servicetalk.http.api.StreamingHttpResponse;
import io.servicetalk.http.api.StreamingHttpResponseFactory;
import io.servicetalk.http.api.StreamingHttpService;
import io.servicetalk.http.api.StreamingHttpServiceFilter;
import io.servicetalk.http.api.StreamingHttpServiceFilterFactory;
import io.servicetalk.http.netty.HttpLifecycleObserverServiceFilter;
import io.servicetalk.http.netty.HttpProtocolConfigs;
import io.servicetalk.http.netty.HttpServers;
import io.servicetalk.http.utils.HttpRequestAutoDrainingServiceFilter;
import io.servicetalk.log4j2.mdc.utils.LoggerStringWriter;
import io.servicetalk.opentelemetry.http.TestUtils.TestTracingServerLoggerFilter;
import io.servicetalk.transport.api.ServerContext;
import io.servicetalk.utils.internal.ThrowableUtils;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.propagation.ContextPropagators;
import io.opentelemetry.context.propagation.TextMapPropagator;
import io.opentelemetry.context.propagation.TextMapSetter;
import io.opentelemetry.sdk.testing.assertj.SpanDataAssert;
import io.opentelemetry.sdk.testing.junit5.OpenTelemetryExtension;
import io.opentelemetry.sdk.trace.data.SpanData;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.channels.ClosedChannelException;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicReference;

import static io.opentelemetry.api.internal.InstrumentationUtil.suppressInstrumentation;
import static io.opentelemetry.semconv.HttpAttributes.HTTP_REQUEST_METHOD;
import static io.opentelemetry.semconv.HttpAttributes.HTTP_RESPONSE_STATUS_CODE;
import static io.opentelemetry.semconv.NetworkAttributes.NETWORK_PEER_ADDRESS;
import static io.opentelemetry.semconv.NetworkAttributes.NETWORK_PEER_PORT;
import static io.opentelemetry.semconv.NetworkAttributes.NETWORK_PROTOCOL_NAME;
import static io.opentelemetry.semconv.NetworkAttributes.NETWORK_PROTOCOL_VERSION;
import static io.opentelemetry.semconv.UrlAttributes.URL_FULL;
import static io.opentelemetry.semconv.UrlAttributes.URL_PATH;
import static io.servicetalk.concurrent.api.Single.succeeded;
import static io.servicetalk.concurrent.internal.DeliberateException.DELIBERATE_EXCEPTION;
import static io.servicetalk.concurrent.internal.TestTimeoutConstants.CI;
import static io.servicetalk.http.netty.AsyncContextHttpFilterVerifier.verifyServerFilterAsyncContextVisibility;
import static io.servicetalk.http.netty.HttpClients.forSingleAddress;
import static io.servicetalk.opentelemetry.http.AbstractOpenTelemetryFilter.DEFAULT_OPTIONS;
import static io.servicetalk.opentelemetry.http.OpenTelemetryHttpRequesterFilterTest.verifyTraceIdPresentInLogs;
import static io.servicetalk.opentelemetry.http.TestUtils.SPAN_STATE_SERIALIZER;
import static io.servicetalk.opentelemetry.http.TestUtils.TRACING_TEST_LOG_LINE_PREFIX;
import static io.servicetalk.transport.netty.internal.AddressUtils.localAddress;
import static io.servicetalk.transport.netty.internal.AddressUtils.serverHostAndPort;
import static java.util.Collections.singletonList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.fail;

@SuppressWarnings("PMD.AvoidUsingHardCodedIP")
class OpenTelemetryHttpServiceFilterTest {

    private static final Publisher<Buffer> DEFAULT_BODY = Publisher.from(
            ReadOnlyBufferAllocators.DEFAULT_RO_ALLOCATOR.fromAscii("data"));

    @RegisterExtension
    static final OpenTelemetryExtension otelTesting = OpenTelemetryExtension.create();

    private final LoggerStringWriter loggerStringWriter = new LoggerStringWriter();

    @BeforeEach
    void setup() {
        loggerStringWriter.reset();
    }

    @AfterEach
    void tearDown() {
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
                        assertThat(span.getAttributes().get(HTTP_RESPONSE_STATUS_CODE))
                            .isEqualTo(200);
                        assertThat(span.getAttributes().get(URL_PATH))
                            .isEqualTo("/path");
                        assertThat(span.getAttributes().get(NETWORK_PROTOCOL_NAME))
                            .isNull(); // only required for != http
                        assertThat(span.getAttributes().get(NETWORK_PROTOCOL_VERSION))
                            .isEqualTo("1.1");
                        assertThat(span.getAttributes().get(NETWORK_PEER_ADDRESS))
                                .isEqualTo("127.0.0.1");
                        assertThat(span.getAttributes().get(NETWORK_PEER_PORT))
                                .isNotNull(); // We're not sure what it is, but it shouldn't be null.
                        assertThat(span.getAttributes().get(HTTP_REQUEST_METHOD))
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
                .appendClientFilter(new OpenTelemetryHttpRequesterFilter(
                        new OpenTelemetryOptions.Builder(DEFAULT_OPTIONS)
                                .openTelemetry(openTelemetry)
                                .componentName("testClient")
                                .build()))
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
                        assertThat(ta.getSpan(0).getAttributes().get(NETWORK_PROTOCOL_NAME))
                            .isNull(); // only required for != http
                        assertThat(ta.getSpan(0).getAttributes().get(NETWORK_PROTOCOL_VERSION))
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
                    .setAttribute(HTTP_REQUEST_METHOD, "GET")
                    .setAttribute(URL_FULL, url.toString());

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
                    assertThat(ta.getSpan(0).getAttributes().get(URL_FULL))
                        .endsWith(url.toString());
                    assertThat(ta.getSpan(1).getAttributes().get(HTTP_REQUEST_METHOD))
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
                            span.getAttributes().get(AttributeKey.stringArrayKey("http.response.header.my-header")))
                            .isEqualTo(singletonList("header-value"));
                        assertThat(span.getAttributes()
                            .get(AttributeKey.stringArrayKey("http.request.header.some-request-header")))
                            .isEqualTo(singletonList("request-header-value"));
                    });
            }
        }
    }

    @Test
    void customHttpServerSpanNameExtractor() throws Exception {
        final String requestUrl = "/custom-name-test";
        final String customPrefix = "CustomHttp-";

        // Configure server with custom span name extractor
        OpenTelemetryOptions options = new OpenTelemetryOptions.Builder()
                .spanNameExtractor(req -> customPrefix + req.method() + " " + req.path())
                .build();

        try (ServerContext context = buildServer(otelTesting.getOpenTelemetry(), options)) {
            try (HttpClient client = forSingleAddress(serverHostAndPort(context)).build()) {
                HttpResponse response = client.request(client.get(requestUrl)).toFuture().get();
                TestSpanState serverSpanState = response.payloadBody(SPAN_STATE_SERIALIZER);

                // Verify basic tracing still works
                verifyTraceIdPresentInLogs(loggerStringWriter.stableAccumulated(1000), requestUrl,
                    serverSpanState.getTraceId(), serverSpanState.getSpanId(),
                    TRACING_TEST_LOG_LINE_PREFIX);

                assertThat(otelTesting.getSpans()).hasSize(1);

                // Verify server span uses custom name
                SpanData serverSpan = otelTesting.getSpans().get(0);
                String expectedSpanName = customPrefix + "GET " + requestUrl;
                assertThat(serverSpan.getName()).isEqualTo(expectedSpanName);
            }
        }
    }

    @Test
    void customHttpClientAndServerSpanNameExtractor() throws Exception {
        final String requestUrl = "/client-server-custom-name-test";
        final String customPrefix = "CustomSpan-";

        // Configure both client and server with the same custom span name extractor
        OpenTelemetryOptions options = new OpenTelemetryOptions.Builder()
                .spanNameExtractor(req -> customPrefix + req.method() + " " + req.path())
                .build();

        OpenTelemetry openTelemetry = otelTesting.getOpenTelemetry();
        try (ServerContext context = buildServer(openTelemetry, options)) {
            try (HttpClient client = forSingleAddress(serverHostAndPort(context))
                .appendClientFilter(new OpenTelemetryHttpRequesterFilter(
                        new OpenTelemetryOptions.Builder(options)
                                .openTelemetry(openTelemetry)
                                .componentName("testClient")
                                .build()))
                .build()) {
                HttpResponse response = client.request(client.get(requestUrl)).toFuture().get();
                TestSpanState serverSpanState = response.payloadBody(SPAN_STATE_SERIALIZER);

                verifyTraceIdPresentInLogs(loggerStringWriter.stableAccumulated(1000), requestUrl,
                    serverSpanState.getTraceId(), serverSpanState.getSpanId(),
                    TRACING_TEST_LOG_LINE_PREFIX);
                assertThat(otelTesting.getSpans()).hasSize(2);
                assertThat(otelTesting.getSpans()).extracting("traceId")
                    .containsExactly(serverSpanState.getTraceId(), serverSpanState.getTraceId());

                // Find client and server spans
                SpanData clientSpan = otelTesting.getSpans().stream()
                    .filter(span -> span.getKind() == SpanKind.CLIENT)
                    .findFirst()
                    .orElseThrow(() -> new AssertionError("No client span found"));

                SpanData serverSpan = otelTesting.getSpans().stream()
                    .filter(span -> span.getKind() == SpanKind.SERVER)
                    .findFirst()
                    .orElseThrow(() -> new AssertionError("No server span found"));

                // Verify both spans use custom naming
                String expectedSpanName = customPrefix + "GET " + requestUrl;
                assertThat(clientSpan.getName()).isEqualTo(expectedSpanName);
                assertThat(serverSpan.getName()).isEqualTo(expectedSpanName);

                // Verify they're part of the same trace
                assertThat(clientSpan.getTraceId()).isEqualTo(serverSpan.getTraceId());
                assertThat(serverSpan.getParentSpanId()).isEqualTo(clientSpan.getSpanId());
            }
        }
    }

    @Test
    void verifyAsyncContextVisibility() throws Exception {
        verifyServerFilterAsyncContextVisibility(new OpenTelemetryHttpServiceFilter());
    }

    @ParameterizedTest(name = "{displayName} [{index}]: http2={0}, useOffloading={1}")
    @CsvSource({"true, true", "true, false", "false, true", "false,false"})
    void autoRequestDisposalOk(boolean http2, boolean useOffloading) throws Exception {
        Set<AttributeKey<String>> expected = new HashSet<>(Arrays.asList(
                TestHttpLifecycleObserver.ON_NEW_EXCHANGE_KEY,
                TestHttpLifecycleObserver.ON_REQUEST_KEY,
                TestHttpLifecycleObserver.ON_EXCHANGE_FINALLY_KEY,
                        TestHttpLifecycleObserver.ON_REQUEST_DATA_KEY,
                        TestHttpLifecycleObserver.ON_REQUEST_COMPLETE_KEY,
                        TestHttpLifecycleObserver.ON_REQUEST_TRAILERS_KEY,
                        TestHttpLifecycleObserver.ON_RESPONSE_DATA_KEY,
                        TestHttpLifecycleObserver.ON_RESPONSE_TRAILERS_KEY,
                        TestHttpLifecycleObserver.ON_RESPONSE_COMPLETE_KEY
        ));
        runWithClient(http2, useOffloading, client -> {
            HttpRequest request = client.get("/foo");
            request.trailers().set("x-request-trailer", "request-trailer");
            request.payloadBody().writeAscii("bar");
            client.request(request).toFuture().get();
            sleep();
            otelTesting.assertTraces()
                    .hasTracesSatisfyingExactly(ta ->
                            ta.hasSpansSatisfyingExactly(span -> checkAttributes(useOffloading, expected, span)));
        });
    }

    @ParameterizedTest(name = "{displayName} [{index}]: http2={0}, useOffloading={1}")
    @CsvSource({"true, true", "true, false", "false, true", "false,false"})
    void autoRequestDisposalErrorResponseBody(boolean http2, boolean useOffloading) throws Exception {
        Set<AttributeKey<String>> expected = new HashSet<>(Arrays.asList(
                TestHttpLifecycleObserver.ON_NEW_EXCHANGE_KEY,
                TestHttpLifecycleObserver.ON_REQUEST_KEY,
                TestHttpLifecycleObserver.ON_REQUEST_CANCEL_KEY,
                TestHttpLifecycleObserver.ON_EXCHANGE_FINALLY_KEY,
                TestHttpLifecycleObserver.ON_RESPONSE_DATA_KEY,
                TestHttpLifecycleObserver.ON_RESPONSE_BODY_ERROR_KEY
        ));
        runWithClient(http2, useOffloading, client -> {
            HttpRequest request = client.get("/responsebodyerror");
            request.payloadBody().writeAscii("bar");
            ExecutionException ex = assertThrows(ExecutionException.class,
                    () -> client.request(request).toFuture().get());
            assertThat(ex.getCause()).isInstanceOf(http2 ? Http2Exception.class : ClosedChannelException.class);

            sleep();
            otelTesting.assertTraces()
                    .hasTracesSatisfyingExactly(ta ->
                            ta.hasSpansSatisfyingExactly(span -> checkAttributes(useOffloading, expected, span)));
        });
    }

    @ParameterizedTest(name = "{displayName} [{index}]: http2={0}, useOffloading={1}")
    @CsvSource({"true, true", "true, false", "false, true", "false,false"})
    void autoRequestDisposalErrorResponse(boolean http2, boolean useOffloading) throws Exception {
        Set<AttributeKey<String>> expected = new HashSet<>(Arrays.asList(
                TestHttpLifecycleObserver.ON_NEW_EXCHANGE_KEY,
                TestHttpLifecycleObserver.ON_REQUEST_KEY,
                TestHttpLifecycleObserver.ON_EXCHANGE_FINALLY_KEY,
                TestHttpLifecycleObserver.ON_REQUEST_CANCEL_KEY,
                TestHttpLifecycleObserver.ON_RESPONSE_ERROR_KEY
        ));
        runWithClient(http2, useOffloading, client -> {
            HttpRequest request = client.get("/responseerror");
            request.payloadBody().writeAscii("bar");
            HttpResponse resp = client.request(request).toFuture().get();
            assertThat(resp.status()).isEqualTo(HttpResponseStatus.INTERNAL_SERVER_ERROR);

            sleep();
            otelTesting.assertTraces()
                    .hasTracesSatisfyingExactly(ta ->
                            ta.hasSpansSatisfyingExactly(span -> checkAttributes(useOffloading, expected, span)));
        });
    }

    @ParameterizedTest(name = "{displayName} [{index}]: http2={0}, useOffloading={1}")
    @CsvSource({"true, true", "true, false", "false, true", "false,false"})
    void autoRequestDisposalRequestBodyError(boolean http2, boolean useOffloading) throws Exception {
        Set<AttributeKey<String>> expected = new HashSet<>(Arrays.asList(
                TestHttpLifecycleObserver.ON_NEW_EXCHANGE_KEY,
                TestHttpLifecycleObserver.ON_REQUEST_KEY,
                TestHttpLifecycleObserver.ON_EXCHANGE_FINALLY_KEY
        ));
        runWithClient(http2, useOffloading, client -> {
            StreamingHttpClient streamingClient = client.asStreamingClient();
            // Most endpoints will do, but this one is less likely to be racy.
            StreamingHttpRequest request = streamingClient.post("/consumebodyinhandler");
            request.payloadBody(Publisher.from(client.executionContext().bufferAllocator().fromAscii("bar"))
                    .concat(Publisher.failed(DELIBERATE_EXCEPTION)));
            ExecutionException ex = assertThrows(ExecutionException.class, () -> streamingClient.request(request)
                    .flatMap(response -> response.toResponse()).toFuture().get());
            assertThat(ex.getCause()).isSameAs(DELIBERATE_EXCEPTION);
            sleep();
            otelTesting.assertTraces()
                    .hasTracesSatisfyingExactly(ta ->
                            ta.hasSpansSatisfyingExactly(span -> {
                                checkAttributes(useOffloading, expected, span);
                                Attributes attributes = span.actual().getAttributes();
                                if (useOffloading &&
                                        attributes.get(TestHttpLifecycleObserver.ON_REQUEST_ERROR_KEY) == null &&
                                        attributes.get(TestHttpLifecycleObserver.ON_REQUEST_CANCEL_KEY) == null) {
                                    fail("Failed to find request completion attribute");
                                }
                            }));
        });
    }

    @ParameterizedTest(name = "{displayName} [{index}]: http2={0}, useOffloading={1}")
    @CsvSource({"true, true", "true, false", "false, true", "false,false"})
    void autoRequestDisposalClientHangupAfterResponseHead(boolean http2, boolean useOffloading) throws Exception {
        Set<AttributeKey<String>> expected = new HashSet<>(Arrays.asList(
                TestHttpLifecycleObserver.ON_NEW_EXCHANGE_KEY,
                TestHttpLifecycleObserver.ON_REQUEST_KEY,
                TestHttpLifecycleObserver.ON_REQUEST_CANCEL_KEY,
                TestHttpLifecycleObserver.ON_RESPONSE_KEY,
                TestHttpLifecycleObserver.ON_EXCHANGE_FINALLY_KEY,
                TestHttpLifecycleObserver.ON_RESPONSE_BODY_CANCEL_KEY
        ));
        runWithClient(http2, useOffloading, client -> {
            StreamingHttpClient streamingClient = client.asStreamingClient();
            // Most endpoints will do, but this one is less likely to be racy.
            StreamingHttpRequest request = streamingClient.post("/slowbody");
            StreamingHttpResponse response = streamingClient.request(request).toFuture().get();
            response.payloadBody().ignoreElements().subscribe().cancel();
        });
        // For the HTTP/1.x server, we don't necessarily see the cancellation until we shutdown the server.
        sleep();
        otelTesting.assertTraces()
                .hasTracesSatisfyingExactly(ta ->
                        ta.hasSpansSatisfyingExactly(span -> checkAttributes(useOffloading, expected, span)));
    }

    @ParameterizedTest(name = "{displayName} [{index}]: http2={0}, useOffloading={1}")
    @CsvSource({"true, true", "true, false", "false, true", "false,false"})
    void autoRequestDisposalClientHangupBeforeResponseHead(boolean http2, boolean useOffloading) throws Exception {
        Set<AttributeKey<String>> expected = new HashSet<>(Arrays.asList(
                TestHttpLifecycleObserver.ON_NEW_EXCHANGE_KEY,
                TestHttpLifecycleObserver.ON_REQUEST_KEY,
                TestHttpLifecycleObserver.ON_REQUEST_CANCEL_KEY,
                TestHttpLifecycleObserver.ON_RESPONSE_CANCEL_KEY,
                TestHttpLifecycleObserver.ON_EXCHANGE_FINALLY_KEY
        ));
        runWithClient(http2, useOffloading, client -> {
            StreamingHttpClient streamingClient = client.asStreamingClient();
            // Most endpoints will do, but this one is less likely to be racy.
            StreamingHttpRequest request = streamingClient.post("/slowhead");
            Future<StreamingHttpResponse> response = streamingClient.request(request).toFuture();
            sleep();
            response.cancel(true);
        });
        // For the HTTP/1.x server, we don't necessarily see the cancellation until we shutdown the server.
        sleep();
        otelTesting.assertTraces()
                .hasTracesSatisfyingExactly(ta ->
                        ta.hasSpansSatisfyingExactly(span -> checkAttributes(useOffloading, expected, span)));
    }

    static void checkAttributes(boolean useOffloading, Set<AttributeKey<String>> expected, SpanDataAssert span) {
        span.hasKind(SpanKind.SERVER);
        // We're subject to race conditions when offloading so only check these when we're not.
        if (!useOffloading) {
            for (AttributeKey<String> key : expected) {
                span.hasAttribute(key, "set");
            }
        }
    }

    private static void runWithClient(boolean http2, boolean useOffloading, RunWithClient runWithClient)
            throws Exception {
        HttpProtocolConfig config = http2 ? HttpProtocolConfigs.h2Default() : HttpProtocolConfigs.h1Default();
        Queue<Error> errorQueue = new ConcurrentLinkedQueue<>();
        try (ServerContext context = buildStreamingServer(http2, otelTesting.getOpenTelemetry(),
                new OpenTelemetryOptions.Builder().build(), useOffloading, errorQueue);
             HttpClient client = forSingleAddress(serverHostAndPort(context)).protocols(config).build()) {
                runWithClient.run(client);
        }
        if (!errorQueue.isEmpty()) {
            AssertionError ex = new AssertionError("Async errors, see suppressed");
            for (Throwable t : errorQueue) {
                ThrowableUtils.addSuppressed(ex, t);
            }
            throw ex;
        }
    }

    private interface RunWithClient {
        void run(HttpClient client) throws Exception;
    }

    private static ServerContext buildServer(OpenTelemetry givenOpentelemetry,
                                             OpenTelemetryOptions opentelemetryOptions,
                                             boolean addSuppressionFilter) throws Exception {
        HttpServerBuilder serverBuilder = HttpServers.forAddress(localAddress(0));

        if (addSuppressionFilter) {
            // IMPORTANT: SuppressionTestServiceFilter must come BEFORE OpenTelemetryHttpServiceFilter
            // so that suppression context is established before OpenTelemetry tries to create spans
            serverBuilder = serverBuilder.appendServiceFilter(new SuppressionTestServiceFilter());
        }

        return serverBuilder
            .appendServiceFilter(new OpenTelemetryHttpServiceFilter(givenOpentelemetry, opentelemetryOptions))
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

    private static ServerContext buildServer(OpenTelemetry givenOpentelemetry,
                                             OpenTelemetryOptions opentelemetryOptions) throws Exception {
        return buildServer(givenOpentelemetry, opentelemetryOptions, false);
    }

    private static ServerContext buildServer(OpenTelemetry givenOpentelemetry) throws Exception {
        return buildServer(givenOpentelemetry, new OpenTelemetryOptions.Builder().build());
    }

    private static ServerContext buildStreamingServer(boolean http2, OpenTelemetry givenOpentelemetry,
                                                      OpenTelemetryOptions opentelemetryOptions, boolean useOffloading,
                                                      Queue<Error> errorQueue) throws Exception {
        HttpProtocolConfig config = http2 ? HttpProtocolConfigs.h2Default() : HttpProtocolConfigs.h1Default();
        HttpServerBuilder builder = HttpServers.forAddress(localAddress(0))
                .protocols(config)
                .drainRequestPayloadBody(false);
        if (useOffloading) {
            builder.appendServiceFilter(new OpenTelemetryHttpServiceFilter(givenOpentelemetry, opentelemetryOptions))
                    .appendServiceFilter(HttpRequestAutoDrainingServiceFilter.INSTANCE)
                    .appendServiceFilter(new HttpLifecycleObserverServiceFilter(
                            new TestHttpLifecycleObserver(errorQueue)));
        } else {
            builder.appendNonOffloadingServiceFilter(new OpenTelemetryHttpServiceFilter(
                    givenOpentelemetry, opentelemetryOptions))
                    .appendNonOffloadingServiceFilter(HttpRequestAutoDrainingServiceFilter.INSTANCE)
                    .appendNonOffloadingServiceFilter(new HttpLifecycleObserverServiceFilter(
                            new TestHttpLifecycleObserver(errorQueue)));
        }
            return builder.listenStreamingAndAwait(new StreamingHttpService() {
                    @Override
                    public Single<StreamingHttpResponse> handle(HttpServiceContext ctx, StreamingHttpRequest request,
                                                                StreamingHttpResponseFactory responseFactory) {
                        final StreamingHttpResponse response = responseFactory.ok();
                        response.payloadBody(DEFAULT_BODY);
                        response.transform(new StatelessTrailersTransformer<Buffer>() {
                            @Override
                            protected HttpHeaders payloadComplete(HttpHeaders trailers) {
                                return trailers.set("x-trailer", "trailer-value");
                            }
                        });

                        if ("/responseerror".equals(request.path())) {
                            return Single.failed(DELIBERATE_EXCEPTION);
                        } else if ("/consumebodyinhandler".equals(request.path())) {
                            return request.payloadBody().ignoreElements()
                                    .concat(Single.succeeded(response));
                        } else if ("/consumebodyasresponse".equals(request.path())) {
                            response.transformMessageBody(body ->
                                    request.payloadBody().ignoreElements().concat(body));
                            return Single.succeeded(response);
                        } else if ("/responsebodyerror".equals(request.path())) {
                            response.payloadBody(DEFAULT_BODY.concat(
                                    Publisher.failed(DELIBERATE_EXCEPTION)));
                            return Single.succeeded(response);
                        } else if ("/slowbody".equals(request.path())) {
                            response.transformPayloadBody(body -> Publisher.never());
                            return Single.succeeded(response);
                        } else if ("/slowhead".equals(request.path())) {
                            return Single.never();
                        } else {
                            return Single.succeeded(response);
                        }
                    }

                    @Override
                    public HttpExecutionStrategy requiredOffloads() {
                        // Without disabling offloading we surface some race conditions between cancel
                        // and response completion.
                        return useOffloading ?
                                HttpExecutionStrategies.offloadAll() : HttpExecutionStrategies.offloadNone();
                    }
                });
    }

    @Test
    void serverSuppressionCanBeSuppressed() throws Exception {
        final String requestUrl = "/server-suppression-test";
        OpenTelemetry openTelemetry = otelTesting.getOpenTelemetry();

        try (ServerContext context = buildServer(openTelemetry,
                new OpenTelemetryOptions.Builder().ignoreSpanSuppression(true).build(), true)) {
            try (HttpClient client = forSingleAddress(serverHostAndPort(context)).build()) {
                HttpResponse response = client.request(client.get(requestUrl)).toFuture().get();
                TestSpanState serverSpanState = response.payloadBody(SPAN_STATE_SERIALIZER);

                sleep();

                // Should have 1 server span even though suppression context was active
                // because ignoreSpanSuppression = true
                assertThat(otelTesting.getSpans()).hasSize(1);
                assertThat(otelTesting.getSpans()).extracting("traceId")
                        .containsExactly(serverSpanState.getTraceId());
            }
        }
    }

    @Test
    void serverSuppressionCanBeHonored() throws Exception {
        final String requestUrl = "/server-suppression-test";
        OpenTelemetry openTelemetry = otelTesting.getOpenTelemetry();

        try (ServerContext context = buildServer(openTelemetry,
                new OpenTelemetryOptions.Builder().ignoreSpanSuppression(false).build(), true)) {
            try (HttpClient client = forSingleAddress(serverHostAndPort(context)).build()) {
                client.request(client.get(requestUrl)).toFuture().get();
                sleep();

                // Should have 0 spans because suppression context was active
                // and ignoreSpanSuppression = false
                assertThat(otelTesting.getSpans()).hasSize(0);
            }
        }
    }

    private static class SuppressionTestServiceFilter implements StreamingHttpServiceFilterFactory {
        @Override
        public StreamingHttpServiceFilter create(StreamingHttpService service) {
            return new StreamingHttpServiceFilter(service) {
                @Override
                public Single<StreamingHttpResponse> handle(HttpServiceContext ctx, StreamingHttpRequest request,
                                                          StreamingHttpResponseFactory responseFactory) {
                    // Create suppression context and execute the rest of the filter chain within it
                    AtomicReference<Single<StreamingHttpResponse>> result = new AtomicReference<>();
                    suppressInstrumentation(() -> result.set(delegate().handle(ctx, request, responseFactory)));
                    assert result.get() != null;
                    return result.get();
                }
            };
        }
    }

    private static void sleep() {
        try {
            Thread.sleep(CI ? 500 : 100);
        } catch (InterruptedException ex) {
            throw new RuntimeException(ex);
        }
    }
}

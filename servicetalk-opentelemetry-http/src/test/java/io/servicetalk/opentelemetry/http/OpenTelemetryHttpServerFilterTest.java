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
import io.servicetalk.http.api.HttpHeaders;
import io.servicetalk.http.api.HttpLifecycleObserver;
import io.servicetalk.http.api.HttpProtocolConfig;
import io.servicetalk.http.api.HttpRequest;
import io.servicetalk.http.api.HttpRequestMetaData;
import io.servicetalk.http.api.HttpResponse;
import io.servicetalk.http.api.HttpResponseMetaData;
import io.servicetalk.http.api.HttpResponseStatus;
import io.servicetalk.http.api.StatelessTrailersTransformer;
import io.servicetalk.http.api.StreamingHttpClient;
import io.servicetalk.http.api.StreamingHttpRequest;
import io.servicetalk.http.api.StreamingHttpResponse;
import io.servicetalk.http.netty.HttpLifecycleObserverServiceFilter;
import io.servicetalk.http.netty.HttpProtocolConfigs;
import io.servicetalk.http.netty.HttpServers;
import io.servicetalk.log4j2.mdc.utils.LoggerStringWriter;
import io.servicetalk.opentelemetry.http.TestUtils.TestTracingServerLoggerFilter;
import io.servicetalk.transport.api.ConnectionInfo;
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
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.channels.ClosedChannelException;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;

import static io.servicetalk.concurrent.api.Single.succeeded;
import static io.servicetalk.http.netty.AsyncContextHttpFilterVerifier.verifyServerFilterAsyncContextVisibility;
import static io.servicetalk.http.netty.HttpClients.forSingleAddress;
import static io.servicetalk.opentelemetry.http.OpenTelemetryHttpRequestFilterTest.verifyTraceIdPresentInLogs;
import static io.servicetalk.opentelemetry.http.TestUtils.SPAN_STATE_SERIALIZER;
import static io.servicetalk.opentelemetry.http.TestUtils.TRACING_TEST_LOG_LINE_PREFIX;
import static io.servicetalk.transport.netty.internal.AddressUtils.localAddress;
import static io.servicetalk.transport.netty.internal.AddressUtils.serverHostAndPort;
import static java.util.Collections.singletonList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

class OpenTelemetryHttpServerFilterTest {

    private static final int SLEEP_DURATION = 1000;

    private static final Publisher<Buffer> DEFAULT_BODY = Publisher.from(
            ReadOnlyBufferAllocators.DEFAULT_RO_ALLOCATOR.fromAscii("data"));

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

    @Test
    void verifyAsyncContextVisibility() throws Exception {
        verifyServerFilterAsyncContextVisibility(new OpenTelemetryHttpServerFilter());
    }

    @ParameterizedTest(name = "{displayName} [{index}]: http2={0}")
    @ValueSource(booleans = {true, false})
    void autoRequestDisposalOk(boolean http2) throws Exception {
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
        runWithClient(http2, client -> {
            HttpRequest request = client.get("/foo");
            request.trailers().set("x-request-trailer", "request-trailer");
            request.payloadBody().writeAscii("bar");
            client.request(request).toFuture().get();
            Thread.sleep(SLEEP_DURATION);
            otelTesting.assertTraces()
                    .hasTracesSatisfyingExactly(ta ->
                            ta.hasSpansSatisfyingExactly(span -> {
                                span.hasKind(SpanKind.SERVER);
                                for (AttributeKey<String> key : expected) {
                                    span.hasAttribute(key, "set");
                                }
                            }));
        });
    }

    @ParameterizedTest(name = "{displayName} [{index}]: http2={0}")
    @ValueSource(booleans = {true, false})
    void autoRequestDisposalErrorResponseBody(boolean http2) throws Exception {
        Set<AttributeKey<String>> expected = new HashSet<>(Arrays.asList(
                TestHttpLifecycleObserver.ON_NEW_EXCHANGE_KEY,
                TestHttpLifecycleObserver.ON_REQUEST_KEY,
                TestHttpLifecycleObserver.ON_REQUEST_CANCEL_KEY,
                TestHttpLifecycleObserver.ON_EXCHANGE_FINALLY_KEY,
                TestHttpLifecycleObserver.ON_RESPONSE_DATA_KEY,
                TestHttpLifecycleObserver.ON_RESPONSE_BODY_ERROR_KEY
        ));
        runWithClient(http2, client -> {
            HttpRequest request = client.get("/responsebodyerror");
            request.payloadBody().writeAscii("bar");
            ExecutionException ex = assertThrows(ExecutionException.class,
                    () -> client.request(request).toFuture().get());
            assertThat(ex.getCause()).isInstanceOf(http2 ? Http2Exception.class : ClosedChannelException.class);

            Thread.sleep(SLEEP_DURATION);
            otelTesting.assertTraces()
                    .hasTracesSatisfyingExactly(ta ->
                            ta.hasSpansSatisfyingExactly(span -> {
                                span.hasKind(SpanKind.SERVER);
                                for (AttributeKey<String> key : expected) {
                                    span.hasAttribute(key, "set");
                                }
                            }));
        });
    }

    @ParameterizedTest(name = "{displayName} [{index}]: http2={0}")
    @ValueSource(booleans = {true, false})
    void autoRequestDisposalErrorResponse(boolean http2) throws Exception {
        Set<AttributeKey<String>> expected = new HashSet<>(Arrays.asList(
                TestHttpLifecycleObserver.ON_NEW_EXCHANGE_KEY,
                TestHttpLifecycleObserver.ON_REQUEST_KEY,
                TestHttpLifecycleObserver.ON_EXCHANGE_FINALLY_KEY,
                TestHttpLifecycleObserver.ON_REQUEST_DATA_KEY,
                TestHttpLifecycleObserver.ON_REQUEST_COMPLETE_KEY,
                TestHttpLifecycleObserver.ON_RESPONSE_ERROR_KEY
        ));
        runWithClient(http2, client -> {
            HttpRequest request = client.get("/responseerror");
            request.payloadBody().writeAscii("bar");
            HttpResponse resp = client.request(request).toFuture().get();
            assertThat(resp.status()).isEqualTo(HttpResponseStatus.INTERNAL_SERVER_ERROR);

            Thread.sleep(SLEEP_DURATION);
            otelTesting.assertTraces()
                    .hasTracesSatisfyingExactly(ta ->
                            ta.hasSpansSatisfyingExactly(span -> {
                                span.hasKind(SpanKind.SERVER);
                                for (AttributeKey<String> key : expected) {
                                    span.hasAttribute(key, "set");
                                }
                            }));
        });
    }

    @RepeatedTest(10)
    void autoRequestDisposalRequestBodyErrorRepro() throws Exception {
        autoRequestDisposalRequestBodyError(true);
    }

    @ParameterizedTest(name = "{displayName} [{index}]: http2={0}")
    @ValueSource(booleans = {true, false})
    void autoRequestDisposalRequestBodyError(boolean http2) throws Exception {
        Set<AttributeKey<String>> expected = new HashSet<>(Arrays.asList(
                TestHttpLifecycleObserver.ON_NEW_EXCHANGE_KEY,
                TestHttpLifecycleObserver.ON_REQUEST_KEY,
                TestHttpLifecycleObserver.ON_EXCHANGE_FINALLY_KEY, // Missing: https://github.com/apple/servicetalk/actions/runs/14274978738/job/40016246646?pr=3212#step:8:1640. It looks like completion is getting triggered via response:cancel after the span is marked complete.
//                TestHttpLifecycleObserver.ON_REQUEST_DATA_KEY, // Sometimes missing because of a race.
                TestHttpLifecycleObserver.ON_REQUEST_ERROR_KEY
        ));
        runWithClient(http2, client -> {
            StreamingHttpClient streamingClient = client.asStreamingClient();
            // Most endpoints will do, but this one is less likely to be racy.
            StreamingHttpRequest request = streamingClient.post("/consumebodyinhandler");
            request.payloadBody(Publisher.from(client.executionContext().bufferAllocator().fromAscii("bar"))
                    .concat(Publisher.failed(new Exception("request body failed"))));
            ExecutionException ex = assertThrows(ExecutionException.class, () -> streamingClient.request(request)
                    .flatMap(response -> response.toResponse()).toFuture().get());
            assertThat(ex.getCause().getMessage()).isEqualTo("request body failed");
            Thread.sleep(SLEEP_DURATION);
            otelTesting.assertTraces()
                    .hasTracesSatisfyingExactly(ta ->
                            ta.hasSpansSatisfyingExactly(span -> {
                                span.hasKind(SpanKind.SERVER);
                                for (AttributeKey<String> key : expected) {
                                    span.hasAttribute(key, "set");
                                }
                            }));
        });
    }

    // TODO: this is flaky due to an intrinsic race between cancellation and response making context-setting
    //  on the request body non-determinate during drains.
    @Disabled
    @ParameterizedTest(name = "{displayName} [{index}]: http2={0}")
    @ValueSource(booleans = {true, false})
    void autoRequestDisposalClientHangupAfterResponseHead(boolean http2) throws Exception {
        Set<AttributeKey<String>> expected = new HashSet<>(Arrays.asList(
                TestHttpLifecycleObserver.ON_NEW_EXCHANGE_KEY,
                TestHttpLifecycleObserver.ON_REQUEST_KEY,
                TestHttpLifecycleObserver.ON_REQUEST_COMPLETE_KEY,
                TestHttpLifecycleObserver.ON_RESPONSE_KEY,
                TestHttpLifecycleObserver.ON_EXCHANGE_FINALLY_KEY,
                TestHttpLifecycleObserver.ON_RESPONSE_BODY_CANCEL_KEY
        ));
        runWithClient(http2, client -> {
            StreamingHttpClient streamingClient = client.asStreamingClient();
            // Most endpoints will do, but this one is less likely to be racy.
            StreamingHttpRequest request = streamingClient.post("/slowbody");
            StreamingHttpResponse response = streamingClient.request(request).toFuture().get();
            response.payloadBody().ignoreElements().subscribe().cancel();
        });
        // For the HTTP/1.x server, we don't necessarily see the cancellation until we shutdown the server.
        Thread.sleep(SLEEP_DURATION);
        otelTesting.assertTraces()
                .hasTracesSatisfyingExactly(ta ->
                        ta.hasSpansSatisfyingExactly(span -> {
                            span.hasKind(SpanKind.SERVER);
                            for (AttributeKey<String> key : expected) {
                                span.hasAttribute(key, "set");
                            }
                        }));
    }

    // TODO: this is flaky due to an intrinsic race between cancellation and response making context-setting
    //  on the request body non-determinate during drains.
    @Disabled
    @ParameterizedTest(name = "{displayName} [{index}]: http2={0}")
    @ValueSource(booleans = {true, false})
    void autoRequestDisposalClientHangupBeforeResponseHead(boolean http2) throws Exception {
        Set<AttributeKey<String>> expected = new HashSet<>(Arrays.asList(
                TestHttpLifecycleObserver.ON_NEW_EXCHANGE_KEY,
                TestHttpLifecycleObserver.ON_REQUEST_KEY,
                TestHttpLifecycleObserver.ON_REQUEST_COMPLETE_KEY,
                TestHttpLifecycleObserver.ON_RESPONSE_CANCEL_KEY,
                TestHttpLifecycleObserver.ON_EXCHANGE_FINALLY_KEY
        ));
        runWithClient(http2, client -> {
            StreamingHttpClient streamingClient = client.asStreamingClient();
            // Most endpoints will do, but this one is less likely to be racy.
            StreamingHttpRequest request = streamingClient.post("/slowhead");
            Future<StreamingHttpResponse> response = streamingClient.request(request).toFuture();
            Thread.sleep(SLEEP_DURATION);
            response.cancel(true);
        });
        // For the HTTP/1.x server, we don't necessarily see the cancellation until we shutdown the server.
        Thread.sleep(SLEEP_DURATION);
        otelTesting.assertTraces()
                .hasTracesSatisfyingExactly(ta ->
                        ta.hasSpansSatisfyingExactly(span -> {
                            span.hasKind(SpanKind.SERVER);
                            for (AttributeKey<String> key : expected) {
                                span.hasAttribute(key, "set");
                            }
                        }));
    }

    private void runWithClient(boolean http2, RunWithClient runWithClient) throws Exception {
        try (ServerContext context = buildStreamingServer(http2, otelTesting.getOpenTelemetry(),
                new OpenTelemetryOptions.Builder().build())) {
            HttpProtocolConfig config = http2 ? HttpProtocolConfigs.h2Default() : HttpProtocolConfigs.h1Default();
            try (HttpClient client = forSingleAddress(serverHostAndPort(context))
                    .protocols(config).build()) {
                runWithClient.run(client);
            }
        }
    }

    private interface RunWithClient {
        void run(HttpClient client) throws Exception;
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

    private static ServerContext buildStreamingServer(boolean http2, OpenTelemetry givenOpentelemetry,
                                                      OpenTelemetryOptions opentelemetryOptions) throws Exception {
        HttpProtocolConfig config = http2 ? HttpProtocolConfigs.h2Default() : HttpProtocolConfigs.h1Default();
        return HttpServers.forAddress(localAddress(0))
                .protocols(config)
                .appendServiceFilter(new OpenTelemetryHttpServerFilter(givenOpentelemetry, opentelemetryOptions))
                .appendServiceFilter(new HttpLifecycleObserverServiceFilter(new TestHttpLifecycleObserver()))
                .listenStreamingAndAwait(
                        (ctx, request, responseFactory) -> Single.defer(() -> {
                            final StreamingHttpResponse response = responseFactory.ok();
                            response.payloadBody(DEFAULT_BODY);

                            response.transform(new StatelessTrailersTransformer<Buffer>() {
                                @Override
                                protected HttpHeaders payloadComplete(HttpHeaders trailers) {
                                    return trailers.set("x-trailer", "trailer-value");
                                }
                            });

                            if ("/responseerror".equals(request.path())) {
                                return Single.failed(new Exception("response failed"));
                            } else if ("/consumebodyinhandler".equals(request.path())) {
                                request.payloadBody().ignoreElements().subscribe();
                                return Single.succeeded(response);
                            } else if ("/consumebodyasresponse".equals(request.path())) {
                                response.transformMessageBody(body ->
                                        request.payloadBody().ignoreElements().concat(body));
                                return Single.succeeded(response);
                            } else if ("/responsebodyerror".equals(request.path())) {
                                response.payloadBody(DEFAULT_BODY.concat(
                                        Publisher.failed(new Exception("response body failed"))));
                                return Single.succeeded(response);
                            } else if ("/slowbody".equals(request.path())) {
                                response.transformPayloadBody(body -> Publisher.never());
                                return Single.succeeded(response);
                            } else if ("/slowhead".equals(request.path())) {
                                return Single.never();
                            } else {
                                return Single.succeeded(response);
                            }
                        }));
    }

    private static final class TestHttpLifecycleObserver implements HttpLifecycleObserver {

        static final AttributeKey<String> ON_NEW_EXCHANGE_KEY = AttributeKey.stringKey("onNewExchange");
        static final AttributeKey<String> ON_EXCHANGE_FINALLY_KEY = AttributeKey.stringKey("onExchangeFinally");

        static final AttributeKey<String> ON_REQUEST_KEY = AttributeKey.stringKey("onRequest");
        static final AttributeKey<String> ON_REQUEST_DATA_KEY = AttributeKey.stringKey("onRequestData");
        static final AttributeKey<String> ON_REQUEST_TRAILERS_KEY = AttributeKey.stringKey("onRequestTrailers");
        static final AttributeKey<String> ON_REQUEST_COMPLETE_KEY = AttributeKey.stringKey("onRequestComplete");
        static final AttributeKey<String> ON_REQUEST_ERROR_KEY = AttributeKey.stringKey("onRequestError");
        static final AttributeKey<String> ON_REQUEST_CANCEL_KEY = AttributeKey.stringKey("onRequestCancel");

        static final AttributeKey<String> ON_RESPONSE_KEY = AttributeKey.stringKey("onResponse");
        static final AttributeKey<String> ON_RESPONSE_ERROR_KEY = AttributeKey.stringKey("onResponseError");
        static final AttributeKey<String> ON_RESPONSE_CANCEL_KEY = AttributeKey.stringKey("onResponseCancel");
        static final AttributeKey<String> ON_RESPONSE_DATA_KEY = AttributeKey.stringKey("onResponseData");
        static final AttributeKey<String> ON_RESPONSE_TRAILERS_KEY = AttributeKey.stringKey("onResponseTrailers");
        static final AttributeKey<String> ON_RESPONSE_COMPLETE_KEY = AttributeKey.stringKey("onResponseComplete");
        static final AttributeKey<String> ON_RESPONSE_BODY_ERROR_KEY = AttributeKey.stringKey("onResponseBodyError");
        static final AttributeKey<String> ON_RESPONSE_BODY_CANCEL_KEY = AttributeKey.stringKey("onResponseBodyCancel");

        @Override
        public HttpExchangeObserver onNewExchange() {
            setKey(ON_NEW_EXCHANGE_KEY);
            return new HttpExchangeObserver() {
                @Override
                public void onConnectionSelected(ConnectionInfo info) {
                }

                @Override
                public HttpRequestObserver onRequest(HttpRequestMetaData requestMetaData) {
                    setKey(ON_REQUEST_KEY);
                    return new HttpRequestObserver() {
                        @Override
                        public void onRequestData(Buffer data) {
                            setKey(ON_REQUEST_DATA_KEY);
                        }

                        @Override
                        public void onRequestTrailers(HttpHeaders trailers) {
                             setKey(ON_REQUEST_TRAILERS_KEY);
                        }

                        @Override
                        public void onRequestComplete() {
                            setKey(ON_REQUEST_COMPLETE_KEY);
                        }

                        @Override
                        public void onRequestError(Throwable cause) {
                             setKey(ON_REQUEST_ERROR_KEY);
                        }

                        @Override
                        public void onRequestCancel() {
                             setKey(ON_REQUEST_CANCEL_KEY);
                        }
                    };
                }

                @Override
                public HttpResponseObserver onResponse(HttpResponseMetaData responseMetaData) {
                    setKey(ON_RESPONSE_KEY);
                    return new HttpResponseObserver() {
                        @Override
                        public void onResponseData(Buffer data) {
                            setKey(ON_RESPONSE_DATA_KEY);
                        }

                        @Override
                        public void onResponseTrailers(HttpHeaders trailers) {
                            setKey(ON_RESPONSE_TRAILERS_KEY);
                        }

                        @Override
                        public void onResponseComplete() {
                            setKey(ON_RESPONSE_COMPLETE_KEY);
                        }

                        @Override
                        public void onResponseError(Throwable cause) {
                             setKey(ON_RESPONSE_BODY_ERROR_KEY);
                        }

                        @Override
                        public void onResponseCancel() {
                            setKey(ON_RESPONSE_BODY_CANCEL_KEY);
                        }
                    };
                }

                @Override
                public void onResponseError(Throwable cause) {
                     setKey(ON_RESPONSE_ERROR_KEY);
                }

                @Override
                public void onResponseCancel() {
                    setKey(ON_RESPONSE_CANCEL_KEY);
                }

                @Override
                public void onExchangeFinally() {
                    setKey(ON_EXCHANGE_FINALLY_KEY);
                }
            };
        }
    }

    private static void setKey(AttributeKey<String> key) {
        Span.current().setAttribute(key, "set");
        System.out.println(key.getKey() + ": " + Span.current());
    }
}

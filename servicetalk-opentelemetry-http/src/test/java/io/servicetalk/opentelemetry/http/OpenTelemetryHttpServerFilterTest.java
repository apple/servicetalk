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
import io.servicetalk.concurrent.api.Publisher;
import io.servicetalk.concurrent.api.Single;
import io.servicetalk.http.api.HttpClient;
import io.servicetalk.http.api.HttpHeaders;
import io.servicetalk.http.api.HttpLifecycleObserver;
import io.servicetalk.http.api.HttpRequest;
import io.servicetalk.http.api.HttpRequestMetaData;
import io.servicetalk.http.api.HttpResponse;
import io.servicetalk.http.api.HttpResponseMetaData;
import io.servicetalk.http.api.HttpResponseStatus;
import io.servicetalk.http.api.StreamingHttpResponse;
import io.servicetalk.http.netty.HttpLifecycleObserverServiceFilter;
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
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.channels.ClosedChannelException;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ExecutionException;

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

    @Test
    void autoRequestDisposalOk() throws Exception {
        Set<AttributeKey<String>> expected = new HashSet<>(Arrays.asList(
                TestHttpLifecycleObserver.ON_NEW_EXCHANGE_KEY,
                TestHttpLifecycleObserver.ON_REQUEST_KEY,
                TestHttpLifecycleObserver.ON_EXCHANGE_FINALLY_KEY,
                        TestHttpLifecycleObserver.ON_REQUEST_DATA_KEY,
                        TestHttpLifecycleObserver.ON_REQUEST_COMPLETE_KEY,
                        TestHttpLifecycleObserver.ON_RESPONSE_DATA_KEY,
                        TestHttpLifecycleObserver.ON_RESPONSE_COMPLETE_KEY
        ));
        withClient(client -> {
            HttpRequest request = client.get("/foo");
            request.payloadBody().writeAscii("bar");
            client.request(request).toFuture().get();
            Thread.sleep(500);
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

    @Test
    void autoRequestDisposalErrorResponseBody() throws Exception {
        Set<AttributeKey<String>> expected = new HashSet<>(Arrays.asList(
                TestHttpLifecycleObserver.ON_NEW_EXCHANGE_KEY,
                TestHttpLifecycleObserver.ON_REQUEST_KEY,
                TestHttpLifecycleObserver.ON_EXCHANGE_FINALLY_KEY,
                TestHttpLifecycleObserver.ON_REQUEST_DATA_KEY,
                TestHttpLifecycleObserver.ON_REQUEST_COMPLETE_KEY,
                TestHttpLifecycleObserver.ON_RESPONSE_DATA_KEY,
                TestHttpLifecycleObserver.ON_RESPONSE_BODY_ERROR_KEY
        ));
        withClient(client -> {
            HttpRequest request = client.get("/responsebodyerror");
            request.payloadBody().writeAscii("bar");
            ExecutionException ex = assertThrows(ExecutionException.class, () -> client.request(request).toFuture().get());
            assertThat(ex.getCause()).isInstanceOf(ClosedChannelException.class);

            Thread.sleep(500);
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

    @Test
    void autoRequestDisposalErrorResponse() throws Exception {
        Set<AttributeKey<String>> expected = new HashSet<>(Arrays.asList(
                TestHttpLifecycleObserver.ON_NEW_EXCHANGE_KEY,
                TestHttpLifecycleObserver.ON_REQUEST_KEY,
                TestHttpLifecycleObserver.ON_EXCHANGE_FINALLY_KEY,
                TestHttpLifecycleObserver.ON_REQUEST_DATA_KEY,
                TestHttpLifecycleObserver.ON_REQUEST_COMPLETE_KEY,
                TestHttpLifecycleObserver.ON_RESPONSE_ERROR_KEY
        ));
        withClient(client -> {
            HttpRequest request = client.get("/responseerror");
            request.payloadBody().writeAscii("bar");
            HttpResponse resp = client.request(request).toFuture().get();
            assertThat(resp.status()).isEqualTo(HttpResponseStatus.INTERNAL_SERVER_ERROR);

            Thread.sleep(500);
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

    private void withClient(RunTest runTest) throws Exception {
        try (ServerContext context = buildStreamingServer(otelTesting.getOpenTelemetry(),
                new OpenTelemetryOptions.Builder().build())) {
            try (HttpClient client = forSingleAddress(serverHostAndPort(context)).build()) {
                runTest.run(client);
            }
        }
    }

    private static interface RunTest {
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

    private static ServerContext buildStreamingServer(OpenTelemetry givenOpentelemetry,
                                                      OpenTelemetryOptions opentelemetryOptions) throws Exception {
        return HttpServers.forAddress(localAddress(0))
                .appendServiceFilter(new OpenTelemetryHttpServerFilter(givenOpentelemetry, opentelemetryOptions))
                .appendServiceFilter(new HttpLifecycleObserverServiceFilter(new TestHttpLifecycleObserver()))
                .listenStreamingAndAwait(
                        (ctx, request, responseFactory) -> Single.defer(() -> {
                            final StreamingHttpResponse response = responseFactory.ok();
                            response.payloadBody(Publisher.from(ctx.executionContext().bufferAllocator()
                                    .fromAscii("done")));

                            if (request.path().equals("/responseerror")) {
                                return Single.failed(new Exception("response failed"));
                            }
                            if (request.path().startsWith("/consumebodyinhandler")) {
                                request.payloadBody().ignoreElements().subscribe();
                            } else if (request.path().startsWith("/consumebodyasresponse")) {
                                response.transformMessageBody(body ->
                                        request.payloadBody().ignoreElements().concat(body));
                            } else if (request.path().equals("/responsebodyerror")) {
                                response.transformMessageBody(body ->
                                        body.concat(Publisher.failed(new Exception("response body failed"))));
                            } else if (request.path().startsWith("/slow")) {
                                response.payloadBody(Publisher.never());
                            }
                            return Single.succeeded(response);
                        }));
    }

    private static final class TestHttpLifecycleObserver implements HttpLifecycleObserver {

        static final AttributeKey<String> ON_NEW_EXCHANGE_KEY = AttributeKey.stringKey("onNewExchange");
        // private static final AttributeKey<String> ON_CONNECTION_ACCEPTED_KEY =
        //        makeKey("onConnectionAccepted");

        static final AttributeKey<String> ON_REQUEST_KEY = AttributeKey.stringKey("onRequest");

        static final AttributeKey<String> ON_RESPONSE_KEY = AttributeKey.stringKey("onResponse");
        static final AttributeKey<String> ON_RESPONSE_ERROR_KEY = AttributeKey.stringKey("onResponseError");
        // private static final AttributeKey<String> ON_RESPONSE_CANCEL_KEY = makeKey("onResponseCancel");
        static final AttributeKey<String> ON_EXCHANGE_FINALLY_KEY = AttributeKey.stringKey("onExchangeFinally");

        static final AttributeKey<String> ON_REQUEST_DATA_KEY = AttributeKey.stringKey("onRequestData");
        // private static final AttributeKey<String> ON_REQUEST_TRAILERS_KEY = makeKey("onRequestTrailers");
        static final AttributeKey<String> ON_REQUEST_COMPLETE_KEY = AttributeKey.stringKey("onRequestComplete");
        // private static final AttributeKey<String> ON_REQUEST_ERROR_KEY = makeKey("onRequestError");
        // private static final AttributeKey<String> ON_REQUEST_CANCEL_KEY = makeKey("onRequestCancel");

        static final AttributeKey<String> ON_RESPONSE_DATA_KEY = AttributeKey.stringKey("onResponseData");
        // private static final AttributeKey<String> ON_RESPONSE_TRAILERS_KEY = makeKey("onResponseTrailers");
        static final AttributeKey<String> ON_RESPONSE_COMPLETE_KEY = AttributeKey.stringKey("onResponseComplete");
        static final AttributeKey<String> ON_RESPONSE_BODY_ERROR_KEY = AttributeKey.stringKey("onResponseBodyError");
        // private static final AttributeKey<String> ON_RESPONSE_CANCEL_2_KEY = makeKey("onResponseCancel-2");

        @Override
        public HttpExchangeObserver onNewExchange() {
            setKey(ON_NEW_EXCHANGE_KEY);
            return new HttpExchangeObserver() {
                @Override
                public void onConnectionSelected(ConnectionInfo info) {
                    // setKey(ON_CONNECTION_ACCEPTED_KEY);
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
                            // setKey(ON_REQUEST_TRAILERS_KEY);
                        }

                        @Override
                        public void onRequestComplete() {
                            setKey(ON_REQUEST_COMPLETE_KEY);
                        }

                        @Override
                        public void onRequestError(Throwable cause) {
                            // setKey(ON_REQUEST_ERROR_KEY, cause);
                        }

                        @Override
                        public void onRequestCancel() {
                            // setKey(ON_REQUEST_CANCEL_KEY);
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
                            // setKey(ON_RESPONSE_TRAILERS_KEY);
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
                            // setKey(ON_RESPONSE_CANCEL_2_KEY);
                        }
                    };
                }

                @Override
                public void onResponseError(Throwable cause) {
                     setKey(ON_RESPONSE_ERROR_KEY);
                }

                @Override
                public void onResponseCancel() {
                    // setKey(ON_RESPONSE_CANCEL_KEY);
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
    }
}

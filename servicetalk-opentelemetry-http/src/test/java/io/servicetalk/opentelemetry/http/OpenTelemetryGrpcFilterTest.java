/*
 * Copyright © 2025 Apple Inc. and the ServiceTalk project authors
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

import io.servicetalk.concurrent.api.Publisher;
import io.servicetalk.concurrent.api.Single;
import io.servicetalk.grpc.api.GrpcServiceContext;
import io.servicetalk.grpc.api.GrpcStatusCode;
import io.servicetalk.grpc.api.GrpcStatusException;
import io.servicetalk.grpc.netty.GrpcClients;
import io.servicetalk.grpc.netty.GrpcServers;
import io.servicetalk.transport.api.ServerContext;

import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.sdk.testing.junit5.OpenTelemetryExtension;
import io.opentelemetry.sdk.trace.data.SpanData;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;
import javax.annotation.Nullable;

import static io.servicetalk.concurrent.api.Single.succeeded;
import static io.servicetalk.concurrent.internal.DeliberateException.DELIBERATE_EXCEPTION;
import static io.servicetalk.opentelemetry.http.AbstractOpenTelemetryFilter.INSTRUMENTATION_SCOPE_NAME;
import static io.servicetalk.transport.netty.internal.AddressUtils.localAddress;
import static io.servicetalk.transport.netty.internal.AddressUtils.serverHostAndPort;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

class OpenTelemetryGrpcFilterTest {

    private static final String CONTENT = "test-content";

    @RegisterExtension
    static final OpenTelemetryExtension otelTesting = OpenTelemetryExtension.create();

    @Nullable
    private ServerContext serverContext;
    @Nullable
    private Tester.TesterClient client;

    @AfterEach
    void tearDown() throws Exception {
        try {
            if (client != null) {
                client.close();
            }
        } finally {
            if (serverContext != null) {
                serverContext.close();
            }
        }
    }

    @Test
    void testGrpcServiceFilterSuccess() throws Exception {
        setUp(false);

        TestResponse response = client.asBlockingClient().test(newRequest());

        assertThat(response.getMessage()).isEqualTo(CONTENT);
        assertTraceStructure();

        // Verify client span
        assertGRpcAttributes(SpanKind.CLIENT, "test", 0);
        // Note that we don't get address info on the client span because the filter is in the wrong location.

        // Verify server span
        assertGRpcAttributes(SpanKind.SERVER, "test", 0);
    }

    @Test
    void testGrpcServiceFilterError() throws Exception {
        setUp(true);

        GrpcStatusException exception = assertThrows(GrpcStatusException.class,
                () -> client.asBlockingClient().test(newRequest()));

        assertThat(exception.status().code()).isEqualTo(GrpcStatusCode.UNKNOWN);
        assertTraceStructure();

        // Verify server span shows error
        assertGRpcAttributes(SpanKind.SERVER, "test", 2);

        // Verify client span shows error
        assertGRpcAttributes(SpanKind.CLIENT, "test", 2);
    }

    @Test
    void testGrpcStreamingSuccess() throws Exception {
        setUp(false);

        Publisher<TestResponse> responses = client.testResponseStream(newRequest());
        TestResponse response = responses.firstOrError().toFuture().get();

        assertThat(response.getMessage()).isEqualTo(CONTENT);
        assertTraceStructure();

        // Verify spans are created for streaming calls too
        assertGRpcAttributes(SpanKind.SERVER, "testResponseStream", 0);
        assertGRpcAttributes(SpanKind.CLIENT, "testResponseStream", 0);
    }

    @Test
    void testGrpcBidirectionalStreaming() throws Exception {
        setUp(false);

        Publisher<TestRequest> requestStream = Publisher.from(newRequest(), newRequest());
        // Collect all responses
        List<TestResponse> responses = new ArrayList<>(client.testBiDiStream(requestStream).toFuture().get());

        for (TestResponse received : responses) {
            assertThat(received.getMessage()).isEqualTo(CONTENT);
        }

        assertTraceStructure();

        // Verify spans for bidirectional streaming
        SpanData serverSpan = findSpanByKind(SpanKind.SERVER);
        assertGRpcAttributes(SpanKind.SERVER, "testBiDiStream", 0);
        assertThat(serverSpan.getStatus().getStatusCode()).isEqualTo(StatusCode.UNSET);

        SpanData clientSpan = findSpanByKind(SpanKind.CLIENT);
        assertGRpcAttributes(SpanKind.CLIENT, "testBiDiStream", 0);
        assertThat(clientSpan.getStatus().getStatusCode()).isEqualTo(StatusCode.UNSET);
    }

    @Test
    void testGrpcBidirectionalStreamingError() throws Exception {
        setUp(true);

        Publisher<TestRequest> requestStream = Publisher.from(newRequest(), newRequest());
        ExecutionException exception = assertThrows(ExecutionException.class,
                () -> client.testBiDiStream(requestStream).toFuture().get());
        GrpcStatusException cause = (GrpcStatusException) exception.getCause();
        assertThat(cause.status().code()).isEqualTo(GrpcStatusCode.UNKNOWN);

        assertTraceStructure();

        // Verify spans for bidirectional streaming
        SpanData serverSpan = findSpanByKind(SpanKind.SERVER);
        assertGRpcAttributes(SpanKind.SERVER, "testBiDiStream", 2);
        assertThat(serverSpan.getStatus().getStatusCode()).isEqualTo(StatusCode.ERROR);

        SpanData clientSpan = findSpanByKind(SpanKind.CLIENT);
        assertGRpcAttributes(SpanKind.CLIENT, "testBiDiStream", 2);
        assertThat(clientSpan.getStatus().getStatusCode()).isEqualTo(StatusCode.ERROR);
    }

    private void setUp(boolean error) throws Exception {
        // Create gRPC server with unified OpenTelemetry HTTP service filter
        // The filter will automatically detect gRPC requests and handle them appropriately
        serverContext = GrpcServers.forAddress(localAddress(0))
                .initializeHttp(builder -> builder
                        .appendServiceFilter(new OpenTelemetryHttpServiceFilter.Builder()
                                        .openTelemetry(otelTesting.getOpenTelemetry()).build()))
                .listenAndAwait(new Tester.ServiceFactory(new TestTesterService(error)));

        // Create gRPC client with unified OpenTelemetry HTTP requester filter
        // The filter will automatically detect gRPC requests and handle them appropriately
        client = GrpcClients.forAddress(serverHostAndPort(serverContext))
                .initializeHttp(builder -> builder.appendClientFilter(new OpenTelemetryHttpRequesterFilter.Builder()
                                .openTelemetry(otelTesting.getOpenTelemetry())
                                .componentName("test-client").build()))
                .build(new Tester.ClientFactory());
    }

    private void assertTraceStructure() throws InterruptedException {
        Thread.sleep(500);
        assertThat(otelTesting.getSpans()).hasSize(2); // client + server spans
        // Verify they're part of the same trace
        assertThat(findSpanByKind(SpanKind.CLIENT).getTraceId())
                .isEqualTo(findSpanByKind(SpanKind.SERVER).getTraceId());
    }

    private void assertGRpcAttributes(SpanKind spanKind, String methodName, long statusCode) {
        SpanData spanData = findSpanByKind(spanKind);
        assertThat(spanData.getName()).isEqualTo("opentelemetry.grpc.Tester/" + methodName);
        assertThat(spanData.getInstrumentationScopeInfo().getName()).isEqualTo(INSTRUMENTATION_SCOPE_NAME);
        InetSocketAddress serverAddress = (InetSocketAddress) serverContext.listenAddress();
        if (spanKind == SpanKind.SERVER) {
            assertThat(spanData.getAttributes().get(AttributeKey.stringKey("client.address")))
                    .isEqualTo(serverAddress.getAddress().getHostAddress());
            assertThat(spanData.getAttributes().get(AttributeKey.longKey("client.port")))
                    .isNotNull(); // we don't know what it is for sure but it shouldn't be null.
        } else {
            assertThat(spanData.getAttributes().get(AttributeKey.stringKey("server.address")))
                    .isEqualTo(serverAddress.getAddress().getHostAddress());
            assertThat(spanData.getAttributes().get(AttributeKey.longKey("server.port")))
                    .isEqualTo(serverAddress.getPort());
        }
        assertThat(spanData.getAttributes().get(AttributeKey.stringKey("rpc.system")))
                .isEqualTo("grpc");
        assertThat(spanData.getAttributes().get(AttributeKey.stringKey("rpc.service")))
                .isEqualTo("opentelemetry.grpc.Tester");
        assertThat(spanData.getAttributes().get(AttributeKey.stringKey("rpc.method")))
                .isEqualTo(methodName);
        assertThat(spanData.getAttributes().get(AttributeKey.longKey("rpc.grpc.status_code")))
                .isEqualTo(statusCode);

        // TODO: right now we don't have a way to get the servers address from this filters position in the client.
        if (spanKind == SpanKind.SERVER) {
            assertThat(spanData.getAttributes().get(AttributeKey.stringKey("network.peer.address")))
                    .isEqualTo(serverAddress.getAddress().getHostAddress());
            // hard to tell what it is, but it shouldn't be null
            assertThat(spanData.getAttributes().get(AttributeKey.longKey("network.peer.port")))
                    .isNotNull();
        }
    }

    private static SpanData findSpanByKind(SpanKind kind) {
        return otelTesting.getSpans().stream()
                .filter(span -> span.getKind() == kind)
                .findFirst()
                .orElseThrow(() -> new AssertionError("No span found with kind: " + kind));
    }

    private static TestRequest newRequest() {
        return TestRequest.newBuilder().setName(CONTENT).build();
    }

    private static class TestTesterService implements Tester.TesterService {
        private final boolean error;

        TestTesterService(boolean error) {
            this.error = error;
        }

        @Override
        public Single<TestResponse> test(GrpcServiceContext ctx, TestRequest request) {
            if (error) {
                return Single.failed(DELIBERATE_EXCEPTION);
            }
            return succeeded(TestResponse.newBuilder().setMessage(request.getName()).build());
        }

        @Override
        public Single<TestResponse> testRequestStream(GrpcServiceContext ctx, Publisher<TestRequest> request) {
            if (error) {
                return request.ignoreElements().concat(Single.failed(DELIBERATE_EXCEPTION));
            }
            return request.collect(StringBuilder::new, (sb, req) -> sb.append(req.getName()))
                    .map(sb -> TestResponse.newBuilder().setMessage(sb.toString()).build());
        }

        @Override
        public Publisher<TestResponse> testResponseStream(GrpcServiceContext ctx, TestRequest request) {
            if (error) {
                return Publisher.failed(DELIBERATE_EXCEPTION);
            }
            return Publisher.from(TestResponse.newBuilder().setMessage(request.getName()).build());
        }

        @Override
        public Publisher<TestResponse> testBiDiStream(GrpcServiceContext ctx, Publisher<TestRequest> request) {
            if (error) {
                return request.ignoreElements().concat(Publisher.failed(DELIBERATE_EXCEPTION));
            }
            return request.map(req -> TestResponse.newBuilder().setMessage(req.getName()).build());
        }
    }
}

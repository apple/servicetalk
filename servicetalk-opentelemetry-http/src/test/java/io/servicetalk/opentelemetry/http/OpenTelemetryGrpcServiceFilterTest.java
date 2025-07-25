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
import io.servicetalk.opentelemetry.http.TesterProto.TestRequest;
import io.servicetalk.opentelemetry.http.TesterProto.TestResponse;
import io.servicetalk.opentelemetry.http.TesterProto.Tester.BlockingTesterClient;
import io.servicetalk.opentelemetry.http.TesterProto.Tester.ClientFactory;
import io.servicetalk.opentelemetry.http.TesterProto.Tester.ServiceFactory;
import io.servicetalk.opentelemetry.http.TesterProto.Tester.TesterClient;
import io.servicetalk.opentelemetry.http.TesterProto.Tester.TesterService;
import io.servicetalk.transport.api.ServerContext;

import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.sdk.testing.junit5.OpenTelemetryExtension;
import io.opentelemetry.sdk.trace.data.SpanData;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;
import javax.annotation.Nullable;

import static io.servicetalk.concurrent.api.Single.succeeded;
import static io.servicetalk.concurrent.internal.DeliberateException.DELIBERATE_EXCEPTION;
import static io.servicetalk.transport.netty.internal.AddressUtils.localAddress;
import static io.servicetalk.transport.netty.internal.AddressUtils.serverHostAndPort;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

class OpenTelemetryGrpcServiceFilterTest {

    private static final String CONTENT = "test-content";

    @RegisterExtension
    static final OpenTelemetryExtension otelTesting = OpenTelemetryExtension.create();

    @Nullable
    private ServerContext serverContext;
    @Nullable
    private TesterClient client;
    @Nullable
    private BlockingTesterClient blockingClient;

    @AfterEach
    void tearDown() throws Exception {
        try {
            if (client != null) {
                client.close();
            }
            if (blockingClient != null) {
                blockingClient.close();
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

        TestResponse response = blockingClient.test(newRequest());

        assertThat(response.getMessage()).isEqualTo(CONTENT);
        Thread.sleep(500);
        assertThat(otelTesting.getSpans()).hasSize(2); // client + server spans

        // Verify server span
        SpanData serverSpan = findSpanByKind(SpanKind.SERVER);
        assertThat(serverSpan.getName()).isEqualTo("opentelemetry.grpc.Tester/test");
        assertThat(serverSpan.getInstrumentationScopeInfo().getName()).isEqualTo("io.servicetalk");

        // Verify client span
        SpanData clientSpan = findSpanByKind(SpanKind.CLIENT);
        assertThat(clientSpan.getName()).isEqualTo("opentelemetry.grpc.Tester/test");
        assertThat(clientSpan.getInstrumentationScopeInfo().getName()).isEqualTo("io.servicetalk");

        // Verify they're part of the same trace
        assertThat(serverSpan.getTraceId()).isEqualTo(clientSpan.getTraceId());

        // Verify gRPC attributes are set correctly
        assertThat(serverSpan.getAttributes().get(AttributeKey.stringKey("rpc.system")))
                .isEqualTo("grpc");
        assertThat(serverSpan.getAttributes().get(AttributeKey.stringKey("rpc.service")))
                .isEqualTo("opentelemetry.grpc.Tester");
        assertThat(serverSpan.getAttributes().get(AttributeKey.stringKey("rpc.method")))
                .isEqualTo("test");
        assertThat(serverSpan.getAttributes().get(AttributeKey.longKey("rpc.grpc.status_code")))
                .isEqualTo(0L);
    }

    @Test
    void testGrpcServiceFilterError() throws Exception {
        setUp(true);

        GrpcStatusException exception = assertThrows(GrpcStatusException.class,
                () -> blockingClient.test(newRequest()));

        assertThat(exception.status().code()).isEqualTo(GrpcStatusCode.UNKNOWN);
        Thread.sleep(500);
        assertThat(otelTesting.getSpans()).hasSize(2); // client + server spans

        // Verify server span shows error
        SpanData serverSpan = findSpanByKind(SpanKind.SERVER);
        assertThat(serverSpan.getName()).isEqualTo("opentelemetry.grpc.Tester/test");
        assertThat(serverSpan.getStatus().getStatusCode()).isEqualTo(io.opentelemetry.api.trace.StatusCode.ERROR);
        assertThat(serverSpan.getAttributes().get(AttributeKey.longKey("rpc.grpc.status_code")))
                .isEqualTo(2L); // UNKNOWN

        // Verify client span shows error
        SpanData clientSpan = findSpanByKind(SpanKind.CLIENT);
        assertThat(clientSpan.getName()).isEqualTo("opentelemetry.grpc.Tester/test");
        assertThat(clientSpan.getStatus().getStatusCode()).isEqualTo(io.opentelemetry.api.trace.StatusCode.ERROR);
    }

    @Test
    void testGrpcStreamingSuccess() throws Exception {
        setUp(false);

        Publisher<TestResponse> responses = client.testResponseStream(newRequest());
        TestResponse response = responses.firstOrError().toFuture().get();

        assertThat(response.getMessage()).isEqualTo(CONTENT);
        Thread.sleep(500);
        assertThat(otelTesting.getSpans()).hasSize(2); // client + server spans

        // Verify spans are created for streaming calls too
        SpanData serverSpan = findSpanByKind(SpanKind.SERVER);
        assertThat(serverSpan.getName()).isEqualTo("opentelemetry.grpc.Tester/testResponseStream");

        SpanData clientSpan = findSpanByKind(SpanKind.CLIENT);
        assertThat(clientSpan.getName()).isEqualTo("opentelemetry.grpc.Tester/testResponseStream");
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

        Thread.sleep(500);
        assertThat(otelTesting.getSpans()).hasSize(2); // client + server spans

        // Verify spans for bidirectional streaming
        SpanData serverSpan = findSpanByKind(SpanKind.SERVER);
        assertThat(serverSpan.getName()).isEqualTo("opentelemetry.grpc.Tester/testBiDiStream");
        assertThat(serverSpan.getStatus().getStatusCode()).isEqualTo(StatusCode.UNSET);

        SpanData clientSpan = findSpanByKind(SpanKind.CLIENT);
        assertThat(clientSpan.getName()).isEqualTo("opentelemetry.grpc.Tester/testBiDiStream");
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

        Thread.sleep(500);
        assertThat(otelTesting.getSpans()).hasSize(2); // client + server spans

        // Verify spans for bidirectional streaming
        SpanData serverSpan = findSpanByKind(SpanKind.SERVER);
        assertThat(serverSpan.getName()).isEqualTo("opentelemetry.grpc.Tester/testBiDiStream");
        assertThat(serverSpan.getStatus().getStatusCode()).isEqualTo(StatusCode.ERROR);

        SpanData clientSpan = findSpanByKind(SpanKind.CLIENT);
        assertThat(clientSpan.getName()).isEqualTo("opentelemetry.grpc.Tester/testBiDiStream");
        assertThat(clientSpan.getStatus().getStatusCode()).isEqualTo(StatusCode.ERROR);
    }

    private void setUp(boolean error) throws Exception {
        // Create gRPC server with unified OpenTelemetry HTTP service filter
        // The filter will automatically detect gRPC requests and handle them appropriately
        serverContext = GrpcServers.forAddress(localAddress(0))
                .initializeHttp(builder -> builder
                        .appendServiceFilter(new OpenTelemetryHttpServiceFilter(
                                otelTesting.getOpenTelemetry(),
                                new OpenTelemetryOptions.Builder().build())))
                .listenAndAwait(new ServiceFactory(new TestTesterService(error)));

        // Create gRPC client with unified OpenTelemetry HTTP requester filter
        // The filter will automatically detect gRPC requests and handle them appropriately
        client = GrpcClients.forAddress(serverHostAndPort(serverContext))
                .initializeHttp(builder -> builder.appendClientFilter(new OpenTelemetryHttpRequesterFilter(
                        otelTesting.getOpenTelemetry(), "test-client",
                        new OpenTelemetryOptions.Builder().build())))
                .build(new ClientFactory());

        blockingClient = GrpcClients.forAddress(serverHostAndPort(serverContext))
                .initializeHttp(builder -> builder.appendClientFilter(new OpenTelemetryHttpRequesterFilter(
                        otelTesting.getOpenTelemetry(), "test-client",
                        new OpenTelemetryOptions.Builder().build())))
                .buildBlocking(new ClientFactory());
    }

    private SpanData findSpanByKind(SpanKind kind) {
        return otelTesting.getSpans().stream()
                .filter(span -> span.getKind() == kind)
                .findFirst()
                .orElseThrow(() -> new AssertionError("No span found with kind: " + kind));
    }

    private static TestRequest newRequest() {
        return TestRequest.newBuilder().setName(CONTENT).build();
    }

    private static class TestTesterService implements TesterService {
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

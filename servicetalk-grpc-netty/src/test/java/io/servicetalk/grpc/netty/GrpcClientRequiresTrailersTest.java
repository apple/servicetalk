/*
 * Copyright © 2020 Apple Inc. and the ServiceTalk project authors
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
package io.servicetalk.grpc.netty;

import io.servicetalk.buffer.api.Buffer;
import io.servicetalk.grpc.api.GrpcSerializationProvider;
import io.servicetalk.grpc.api.GrpcStatusCode;
import io.servicetalk.grpc.api.GrpcStatusException;
import io.servicetalk.grpc.netty.TesterProto.TestRequest;
import io.servicetalk.grpc.netty.TesterProto.TestResponse;
import io.servicetalk.grpc.netty.TesterProto.Tester.BlockingTesterClient;
import io.servicetalk.grpc.protobuf.ProtoBufSerializationProviderBuilder;
import io.servicetalk.http.api.HttpHeaders;
import io.servicetalk.http.api.HttpServerBuilder;
import io.servicetalk.http.api.StatelessTrailersTransformer;
import io.servicetalk.http.api.StreamingHttpResponse;
import io.servicetalk.http.api.StreamingHttpService;
import io.servicetalk.http.netty.HttpServers;
import io.servicetalk.transport.api.ServerContext;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.function.Executable;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.concurrent.ExecutionException;
import java.util.stream.Stream;
import javax.annotation.Nullable;

import static io.servicetalk.concurrent.api.Publisher.from;
import static io.servicetalk.concurrent.api.Single.succeeded;
import static io.servicetalk.encoding.api.Identity.identity;
import static io.servicetalk.grpc.api.GrpcExecutionStrategies.noOffloadsStrategy;
import static io.servicetalk.http.api.HttpApiConversions.toHttpService;
import static io.servicetalk.http.api.HttpHeaderNames.CONTENT_TYPE;
import static io.servicetalk.http.netty.HttpProtocolConfigs.h2Default;
import static io.servicetalk.transport.netty.internal.AddressUtils.localAddress;
import static io.servicetalk.transport.netty.internal.AddressUtils.serverHostAndPort;
import static java.util.Collections.singletonList;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.instanceOf;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertThrows;

class GrpcClientRequiresTrailersTest {

    private static final GrpcSerializationProvider SERIALIZATION_PROVIDER = new ProtoBufSerializationProviderBuilder()
            .registerMessageType(TestRequest.class, TestRequest.parser())
            .registerMessageType(TestResponse.class, TestResponse.parser())
            .build();

    @Nullable
    private ServerContext serverContext;
    @Nullable
    private BlockingTesterClient client;

    private void setUp(boolean streaming, boolean hasTrailers) throws Exception {
        // Emulate gRPC server that does not add grpc-status to the trailers after payload body:
        StreamingHttpService streamingService = (ctx, request, responseFactory) -> {
            final StreamingHttpResponse response = responseFactory.ok()
                    .version(request.version())
                    .setHeader(CONTENT_TYPE, "application/grpc")
                    .payloadBody(from(TestResponse.newBuilder().setMessage("response").build()),
                            SERIALIZATION_PROVIDER.serializerFor(identity(), TestResponse.class));

            if (hasTrailers) {
                response.transform(new StatelessTrailersTransformer<Buffer>() {
                    @Override
                    protected HttpHeaders payloadComplete(final HttpHeaders trailers) {
                        return trailers.set("some-trailer", "some-value");
                    }
                });
            }
            return succeeded(response);
        };
        HttpServerBuilder serverBuilder = HttpServers.forAddress(localAddress(0))
                .protocols(h2Default());
        serverContext = streaming ? serverBuilder.listenStreamingAndAwait(streamingService) :
                serverBuilder.listenAndAwait(toHttpService(streamingService));

        client = GrpcClients.forAddress(serverHostAndPort(serverContext))
                .executionStrategy(noOffloadsStrategy())
                .buildBlocking(new TesterProto.Tester.ClientFactory());
    }

    static Stream<Arguments> params() {
        return Stream.of(
                Arguments.of(false, false),
                Arguments.of(false, true),
                Arguments.of(true, false),
                Arguments.of(true, true));
    }

    @AfterEach
    void tearDown() throws Exception {
        try {
            client.close();
        } finally {
            serverContext.close();
        }
    }

    @ParameterizedTest(name = "streaming={0} has-trailers={1}")
    @MethodSource("params")
    void testBlockingAggregated(boolean streaming, boolean hasTrailers) throws Exception {
        setUp(streaming, hasTrailers);
        assertThrowsGrpcStatusException(() -> client.test(request()));
    }

    @ParameterizedTest(name = "streaming={0} has-trailers={1}")
    @MethodSource("params")
    void testBlockingRequestStreaming(boolean streaming, boolean hasTrailers) throws Exception {
        setUp(streaming, hasTrailers);
        assertThrowsGrpcStatusException(() -> client.testRequestStream(singletonList(request())));
    }

    @ParameterizedTest(name = "streaming={0} has-trailers={1}")
    @MethodSource("params")
    void testBlockingResponseStreaming(boolean streaming, boolean hasTrailers) throws Exception {
        setUp(streaming, hasTrailers);
        assertThrowsGrpcStatusException(() -> client.testResponseStream(request()).forEach(__ -> { /* noop */ }));
    }

    @ParameterizedTest(name = "streaming={0} has-trailers={1}")
    @MethodSource("params")
    void testBlockingBiDiStreaming(boolean streaming, boolean hasTrailers) throws Exception {
        setUp(streaming, hasTrailers);
        assertThrowsGrpcStatusException(() -> client.testBiDiStream(singletonList(request()))
                .forEach(__ -> { /* noop */ }));
    }

    @ParameterizedTest(name = "streaming={0} has-trailers={1}")
    @MethodSource("params")
    void testAggregated(boolean streaming, boolean hasTrailers) throws Exception {
        setUp(streaming, hasTrailers);
        assertThrowsExecutionException(() -> client.asClient().test(request()).toFuture().get());
    }

    @ParameterizedTest(name = "streaming={0} has-trailers={1}")
    @MethodSource("params")
    void testRequestStreaming(boolean streaming, boolean hasTrailers) throws Exception {
        setUp(streaming, hasTrailers);
        assertThrowsExecutionException(() -> client.asClient().testRequestStream(from(request())).toFuture().get());
    }

    @ParameterizedTest(name = "streaming={0} has-trailers={1}")
    @MethodSource("params")
    void testResponseStreaming(boolean streaming, boolean hasTrailers) throws Exception {
        setUp(streaming, hasTrailers);
        assertThrowsExecutionException(() -> client.asClient().testResponseStream(request()).toFuture().get());
    }

    @ParameterizedTest(name = "streaming={0} has-trailers={1}")
    @MethodSource("params")
    void testBiDiStreaming(boolean streaming, boolean hasTrailers) throws Exception {
        setUp(streaming, hasTrailers);
        assertThrowsExecutionException(() -> client.asClient().testBiDiStream(from(request())).toFuture().get());
    }

    private static TestRequest request() {
        return TestRequest.newBuilder().setName("request").build();
    }

    private static void assertThrowsExecutionException(Executable executable) {
        ExecutionException ex = assertThrows(ExecutionException.class, executable);
        assertThat(ex.getCause(), is(instanceOf(GrpcStatusException.class)));
        assertGrpcStatusException((GrpcStatusException) ex.getCause());
    }

    private static void assertThrowsGrpcStatusException(Executable executable) {
        assertGrpcStatusException(assertThrows(GrpcStatusException.class, executable));
    }

    private static void assertGrpcStatusException(GrpcStatusException grpcStatusException) {
        assertThat(grpcStatusException.status().code(), is(GrpcStatusCode.UNKNOWN));
        assertThat(grpcStatusException.status().description(),
                equalTo("Response does not contain grpc-status header or trailer"));
    }
}

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
import io.servicetalk.concurrent.internal.ServiceTalkTestTimeout;
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

import org.junit.After;
import org.junit.Rule;
import org.junit.Test;
import org.junit.function.ThrowingRunnable;
import org.junit.rules.Timeout;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

import java.util.concurrent.ExecutionException;

import static io.servicetalk.concurrent.api.Publisher.from;
import static io.servicetalk.concurrent.api.Single.succeeded;
import static io.servicetalk.grpc.api.GrpcExecutionStrategies.noOffloadsStrategy;
import static io.servicetalk.grpc.api.GrpcMessageEncodingRegistry.NONE;
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
import static org.junit.Assert.assertThrows;

@RunWith(Parameterized.class)
public class GrpcClientRequiresTrailersTest {

    private static final GrpcSerializationProvider SERIALIZATION_PROVIDER = new ProtoBufSerializationProviderBuilder()
            .registerMessageType(TestRequest.class, TestRequest.parser())
            .registerMessageType(TestResponse.class, TestResponse.parser())
            .build();

    @Rule
    public final Timeout timeout = new ServiceTalkTestTimeout();

    private final ServerContext serverContext;
    private final BlockingTesterClient client;

    public GrpcClientRequiresTrailersTest(boolean streaming, boolean hasTrailers) throws Exception {
        // Emulate gRPC server that does not add grpc-status to the trailers after payload body:
        StreamingHttpService streamingService = (ctx, request, responseFactory) -> {
            final StreamingHttpResponse response = responseFactory.ok()
                    .version(request.version())
                    .setHeader(CONTENT_TYPE, "application/grpc")
                    .payloadBody(from(TestResponse.newBuilder().setMessage("response").build()),
                            SERIALIZATION_PROVIDER.serializerFor(NONE, TestResponse.class));

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

    @Parameters(name = "streaming={0} has-trailers={1}")
    public static Object[][] params() {
        return new Object[][]{{false, false}, {false, true}, {true, false}, {true, true}};
    }

    @After
    public void tearDown() throws Exception {
        try {
            client.close();
        } finally {
            serverContext.close();
        }
    }

    @Test
    public void testBlockingAggregated() {
        assertThrowsGrpcStatusException(() -> client.test(request()));
    }

    @Test
    public void testBlockingRequestStreaming() {
        assertThrowsGrpcStatusException(() -> client.testRequestStream(singletonList(request())));
    }

    @Test
    public void testBlockingResponseStreaming() {
        assertThrowsGrpcStatusException(() -> client.testResponseStream(request()).forEach(__ -> { /* noop */ }));
    }

    @Test
    public void testBlockingBiDiStreaming() {
        assertThrowsGrpcStatusException(() -> client.testBiDiStream(singletonList(request()))
                .forEach(__ -> { /* noop */ }));
    }

    @Test
    public void testAggregated() {
        assertThrowsExecutionException(() -> client.asClient().test(request()).toFuture().get());
    }

    @Test
    public void testRequestStreaming() {
        assertThrowsExecutionException(() -> client.asClient().testRequestStream(from(request())).toFuture().get());
    }

    @Test
    public void testResponseStreaming() {
        assertThrowsExecutionException(() -> client.asClient().testResponseStream(request()).toFuture().get());
    }

    @Test
    public void testBiDiStreaming() {
        assertThrowsExecutionException(() -> client.asClient().testBiDiStream(from(request())).toFuture().get());
    }

    private static TestRequest request() {
        return TestRequest.newBuilder().setName("request").build();
    }

    private static void assertThrowsExecutionException(ThrowingRunnable runnable) {
        ExecutionException ex = assertThrows(ExecutionException.class, runnable);
        assertThat(ex.getCause(), is(instanceOf(GrpcStatusException.class)));
        assertGrpcStatusException((GrpcStatusException) ex.getCause());
    }

    private static void assertThrowsGrpcStatusException(ThrowingRunnable runnable) {
        assertGrpcStatusException(assertThrows(GrpcStatusException.class, runnable));
    }

    private static void assertGrpcStatusException(GrpcStatusException grpcStatusException) {
        assertThat(grpcStatusException.status().code(), is(GrpcStatusCode.INTERNAL));
        assertThat(grpcStatusException.status().description(),
                equalTo("Response does not contain grpc-status header or trailer"));
    }
}

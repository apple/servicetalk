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

import io.servicetalk.concurrent.BlockingIterable;
import io.servicetalk.concurrent.api.Publisher;
import io.servicetalk.concurrent.api.Single;
import io.servicetalk.grpc.api.GrpcClientBuilder;
import io.servicetalk.grpc.api.GrpcPayloadWriter;
import io.servicetalk.grpc.api.GrpcServiceContext;
import io.servicetalk.grpc.api.GrpcStatusException;
import io.servicetalk.grpc.netty.TesterProto.TestRequest;
import io.servicetalk.grpc.netty.TesterProto.TestResponse;
import io.servicetalk.grpc.netty.TesterProto.Tester.BlockingTestResponseStreamRpc;
import io.servicetalk.grpc.netty.TesterProto.Tester.BlockingTesterClient;
import io.servicetalk.grpc.netty.TesterProto.Tester.BlockingTesterService;
import io.servicetalk.grpc.netty.TesterProto.Tester.ClientFactory;
import io.servicetalk.grpc.netty.TesterProto.Tester.ServiceFactory;
import io.servicetalk.grpc.netty.TesterProto.Tester.TesterClient;
import io.servicetalk.grpc.netty.TesterProto.Tester.TesterService;
import io.servicetalk.http.api.HttpExecutionStrategy;
import io.servicetalk.http.api.StreamingHttpClientFilter;
import io.servicetalk.http.api.StreamingHttpRequest;
import io.servicetalk.http.api.StreamingHttpRequester;
import io.servicetalk.http.api.StreamingHttpResponse;
import io.servicetalk.transport.api.HostAndPort;
import io.servicetalk.transport.api.ServerContext;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.net.InetSocketAddress;
import java.util.NoSuchElementException;
import java.util.concurrent.ExecutionException;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import javax.annotation.Nullable;

import static io.servicetalk.concurrent.api.Publisher.from;
import static io.servicetalk.concurrent.api.Publisher.fromIterable;
import static io.servicetalk.concurrent.api.Single.defer;
import static io.servicetalk.grpc.api.GrpcStatusCode.INVALID_ARGUMENT;
import static io.servicetalk.transport.netty.internal.AddressUtils.localAddress;
import static io.servicetalk.transport.netty.internal.AddressUtils.serverHostAndPort;
import static java.util.Arrays.asList;
import static java.util.Collections.emptyList;
import static java.util.Collections.singletonList;
import static java.util.stream.Collectors.toList;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.instanceOf;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assumptions.assumeFalse;

class SingleRequestOrResponseApiTest {

    private boolean streamingService;
    private boolean streamingClient;
    @Nullable
    private ServerContext serverContext;
    @Nullable
    private GrpcClientBuilder<HostAndPort, InetSocketAddress> clientBuilder;

    private void setUp(boolean streamingService, boolean streamingClient) throws Exception {
        this.streamingService = streamingService;
        this.streamingClient = streamingClient;

        serverContext = GrpcServers.forAddress(localAddress(0)).listenAndAwait(streamingService ?
                new ServiceFactory(new TesterServiceImpl()) :
                new ServiceFactory(new BlockingTesterServiceImpl()));

        clientBuilder = GrpcClients.forAddress(serverHostAndPort(serverContext))
                // HTTP filter that modifies path to workaround gRPC API constraints:
                .appendHttpClientFilter(origin -> new StreamingHttpClientFilter(origin) {
                    @Override
                    protected Single<StreamingHttpResponse> request(StreamingHttpRequester delegate,
                                                                    HttpExecutionStrategy strategy,
                                                                    StreamingHttpRequest request) {
                        // Change path to send the request to the route API that expects only a single request item
                        // and generates requested number of response items:
                        return defer(() -> {
                            request.requestTarget(BlockingTestResponseStreamRpc.PATH);
                            return delegate.request(strategy, request).subscribeShareContext();
                        });
                    }
                });
    }

    private static Stream<Arguments> params() {
        return Stream.of(
            Arguments.of(false, false),
            Arguments.of(false, true),
            Arguments.of(true, false),
            Arguments.of(true, true));
    }

    @AfterEach
    void tearDown() throws Exception {
        serverContext.close();
    }

    @ParameterizedTest(name = "streamingService={0}, streamingClient={1}")
    @MethodSource("params")
    void serverResponseStreamingRouteFailsWithZeroRequestItems(boolean streamingService,
                                                               boolean streamingClient) throws Exception {
        setUp(streamingService, streamingClient);
        serverResponseStreamingRouteFailsWithInvalidArgument(emptyList(),
                "Single request message was expected, but none was received");
    }

    @ParameterizedTest(name = "streamingService={0}, streamingClient={1}")
    @MethodSource("params")
    void serverResponseStreamingRouteFailsOnSecondRequestItem(boolean streamingService,
                                                              boolean streamingClient) throws Exception {
        setUp(streamingService, streamingClient);
        serverResponseStreamingRouteFailsWithInvalidArgument(asList(newRequest(0), newRequest(0)),
                "More than one request message received");
    }

    private void serverResponseStreamingRouteFailsWithInvalidArgument(Iterable<TestRequest> requestItems,
                                                                      String expectedMsg) throws Exception {
        assumeFalse(streamingClient);  // No need to run the test with different client-side, always use blocking client
        try (BlockingTesterClient client = newBlockingClient()) {
            GrpcStatusException e = assertThrows(GrpcStatusException.class,
                    () -> client.testBiDiStream(requestItems).forEach(response -> { /* noop */ }));
            assertThat(e.status().code(), is(INVALID_ARGUMENT));
            assertThat(e.status().description(), equalTo(expectedMsg));
        }
    }

    @ParameterizedTest(name = "streamingService={0}, streamingClient={1}")
    @MethodSource("params")
    void clientRequestStreamingCallFailsWithZeroResponseItems(boolean streamingService,
                                                              boolean streamingClient) throws Exception {
        setUp(streamingService, streamingClient);
        clientRequestStreamingCallFailsOnInvalidResponse(0, NoSuchElementException.class);
    }

    @ParameterizedTest(name = "streamingService={0}, streamingClient={1}")
    @MethodSource("params")
    void clientRequestStreamingCallFailsOnSecondResponseItem(boolean streamingService,
                                                             boolean streamingClient) throws Exception {
        setUp(streamingService, streamingClient);
        clientRequestStreamingCallFailsOnInvalidResponse(2, IllegalArgumentException.class);
    }

    private <T extends Throwable> void clientRequestStreamingCallFailsOnInvalidResponse(
            int numberOfResponses, Class<T> exceptionClass) throws Exception {
        assumeFalse(streamingService);  // No need to run the test with different server-side
        if (streamingClient) {
            try (TesterClient client = newClient()) {
                ExecutionException e = assertThrows(ExecutionException.class,
                        () -> client.testRequestStream(from(newRequest(numberOfResponses))).toFuture().get());
                assertThat(e.getCause(), is(instanceOf(exceptionClass)));
            }
        } else {
            try (BlockingTesterClient client = newBlockingClient()) {
                assertThrows(exceptionClass,
                        () -> client.testRequestStream(singletonList(newRequest(numberOfResponses))));
            }
        }
    }

    private BlockingTesterClient newBlockingClient() {
        return clientBuilder.buildBlocking(new ClientFactory());
    }

    private TesterClient newClient() {
        return clientBuilder.build(new ClientFactory());
    }

    private static TestRequest newRequest(int number) {
        return TestRequest.newBuilder().setName(Integer.toString(number)).build();
    }

    private static TestResponse newResponse() {
        return TestResponse.newBuilder().setMessage("response").build();
    }

    private static class TesterServiceImpl implements TesterService {

        @Override
        public Single<TestResponse> test(GrpcServiceContext ctx, TestRequest request) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Publisher<TestResponse> testBiDiStream(GrpcServiceContext ctx, Publisher<TestRequest> request) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Publisher<TestResponse> testResponseStream(GrpcServiceContext ctx, TestRequest request) {
            return fromIterable(IntStream.range(0, Integer.parseInt(request.getName()))
                    .mapToObj(i -> newResponse())
                    .collect(toList()));
        }

        @Override
        public Single<TestResponse> testRequestStream(GrpcServiceContext ctx, Publisher<TestRequest> request) {
            throw new UnsupportedOperationException();
        }
    }

    private static class BlockingTesterServiceImpl implements BlockingTesterService {
        @Override
        public TestResponse test(GrpcServiceContext ctx, TestRequest request) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void testBiDiStream(GrpcServiceContext ctx, BlockingIterable<TestRequest> request,
                                   GrpcPayloadWriter<TestResponse> responseWriter) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void testResponseStream(GrpcServiceContext ctx, TestRequest request,
                                       GrpcPayloadWriter<TestResponse> responseWriter) throws Exception {
            int numberOfResponses = Integer.parseInt(request.getName());
            for (int i = 0; i < numberOfResponses; i++) {
                responseWriter.write(newResponse());
            }
            responseWriter.close();
        }

        @Override
        public TestResponse testRequestStream(GrpcServiceContext ctx,
                                              BlockingIterable<TestRequest> request) {
            throw new UnsupportedOperationException();
        }
    }
}

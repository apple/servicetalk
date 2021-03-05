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
import io.servicetalk.concurrent.internal.ServiceTalkTestTimeout;
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

import org.junit.After;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.Timeout;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

import java.net.InetSocketAddress;
import java.util.NoSuchElementException;
import java.util.concurrent.ExecutionException;
import java.util.stream.IntStream;

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
import static org.junit.Assert.assertThrows;
import static org.junit.Assume.assumeFalse;

@RunWith(Parameterized.class)
public class SingleRequestOrResponseApiTest {

    @Rule
    public final Timeout timeout = new ServiceTalkTestTimeout();

    private final boolean streamingService;
    private final boolean streamingClient;
    private final ServerContext serverContext;
    private final GrpcClientBuilder<HostAndPort, InetSocketAddress> clientBuilder;

    public SingleRequestOrResponseApiTest(boolean streamingService, boolean streamingClient) throws Exception {
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

    @Parameters(name = "streamingService={0}, streamingClient={1}")
    public static Object[][] params() {
        return new Object[][]{{false, false}, {false, true}, {true, false}};
    }

    @After
    public void tearDown() throws Exception {
        serverContext.close();
    }

    @Test
    public void serverResponseStreamingRouteFailsWithZeroRequestItems() throws Exception {
        serverResponseStreamingRouteFailsWithInvalidArgument(emptyList(),
                "Single request message was expected, but none was received");
    }

    @Test
    public void serverResponseStreamingRouteFailsOnSecondRequestItem() throws Exception {
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

    @Test
    public void clientRequestStreamingCallFailsWithZeroResponseItems() throws Exception {
        clientRequestStreamingCallFailsOnInvalidResponse(0, NoSuchElementException.class);
    }

    @Test
    public void clientRequestStreamingCallFailsOnSecondResponseItem() throws Exception {
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

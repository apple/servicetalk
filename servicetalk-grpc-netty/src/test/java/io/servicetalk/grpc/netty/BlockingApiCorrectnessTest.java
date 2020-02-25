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
import io.servicetalk.concurrent.api.Single;
import io.servicetalk.concurrent.internal.ServiceTalkTestTimeout;
import io.servicetalk.grpc.api.GrpcPayloadWriter;
import io.servicetalk.grpc.api.GrpcServiceContext;
import io.servicetalk.grpc.api.GrpcStatusException;
import io.servicetalk.grpc.netty.TesterProto.TestRequest;
import io.servicetalk.grpc.netty.TesterProto.TestResponse;
import io.servicetalk.grpc.netty.TesterProto.Tester.BlockingTestBiDiStreamRpc;
import io.servicetalk.grpc.netty.TesterProto.Tester.BlockingTestResponseStreamRpc;
import io.servicetalk.grpc.netty.TesterProto.Tester.BlockingTesterClient;
import io.servicetalk.grpc.netty.TesterProto.Tester.BlockingTesterService;
import io.servicetalk.grpc.netty.TesterProto.Tester.ClientFactory;
import io.servicetalk.grpc.netty.TesterProto.Tester.ServiceFactory;
import io.servicetalk.http.api.HttpExecutionStrategy;
import io.servicetalk.http.api.StreamingHttpClientFilter;
import io.servicetalk.http.api.StreamingHttpRequest;
import io.servicetalk.http.api.StreamingHttpRequester;
import io.servicetalk.http.api.StreamingHttpResponse;
import io.servicetalk.transport.api.ServerContext;

import org.junit.After;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.Timeout;

import static io.servicetalk.grpc.api.GrpcStatusCode.INVALID_ARGUMENT;
import static io.servicetalk.transport.netty.internal.AddressUtils.localAddress;
import static io.servicetalk.transport.netty.internal.AddressUtils.serverHostAndPort;
import static java.util.Arrays.asList;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertThrows;

public class BlockingApiCorrectnessTest {

    @Rule
    public final Timeout timeout = new ServiceTalkTestTimeout();

    private final ServerContext serverContext;

    public BlockingApiCorrectnessTest() throws Exception {
        serverContext = GrpcServers.forAddress(localAddress(0))
                .listenAndAwait(new ServiceFactory(new BlockingTesterService() {
                    @Override
                    public void testBiDiStream(GrpcServiceContext ctx, BlockingIterable<TestRequest> request,
                                               GrpcPayloadWriter<TestResponse> responseWriter) throws Exception {
                        responseWriter.write(newResponse());
                        responseWriter.write(newResponse());
                    }

                    @Override
                    public TestResponse testRequestStream(GrpcServiceContext ctx,
                                                          BlockingIterable<TestRequest> request) {
                        throw new UnsupportedOperationException();
                    }

                    @Override
                    public void testResponseStream(GrpcServiceContext ctx, TestRequest request,
                                                   GrpcPayloadWriter<TestResponse> responseWriter) throws Exception {
                        responseWriter.write(newResponse());
                    }

                    @Override
                    public TestResponse test(GrpcServiceContext ctx, TestRequest request) {
                        throw new UnsupportedOperationException();
                    }
                }));
    }

    @After
    public void tearDown() throws Exception {
        serverContext.close();
    }

    @Test
    public void serverBlockingResponseStreamingRouteFailsOnSecondResponseItem() throws Exception {
        try (BlockingTesterClient client = GrpcClients.forAddress(serverHostAndPort(serverContext))
                // HTTP filter that modifies path to workaround gRPC API constraints:
                .appendHttpClientFilter(origin -> new StreamingHttpClientFilter(origin) {
                    @Override
                    protected Single<StreamingHttpResponse> request(StreamingHttpRequester delegate,
                                                                    HttpExecutionStrategy strategy,
                                                                    StreamingHttpRequest request) {
                        // Change path to send multiple items to the route API that expects only a single request item:
                        request.requestTarget(BlockingTestResponseStreamRpc.PATH);
                        return super.request(delegate, strategy, request);
                    }
                }).buildBlocking(new ClientFactory())) {
            GrpcStatusException e = assertThrows(GrpcStatusException.class, () ->
                    client.testBiDiStream(asList(newRequest(), newRequest())).forEach(response -> { /* noop */ }));
            assertThat(e.status().code(), is(INVALID_ARGUMENT));
            assertThat(e.status().description(), equalTo("More than one request message received"));
        }
    }

    @Test
    public void clientBlockingRequestStreamingCallFailsOnSecondResponseItem() throws Exception {
        try (BlockingTesterClient client = GrpcClients.forAddress(serverHostAndPort(serverContext))
                // HTTP filter that modifies path to workaround gRPC API constraints:
                .appendHttpClientFilter(origin -> new StreamingHttpClientFilter(origin) {
                    @Override
                    protected Single<StreamingHttpResponse> request(StreamingHttpRequester delegate,
                                                                    HttpExecutionStrategy strategy,
                                                                    StreamingHttpRequest request) {
                        // Change path to send the request to the route API that generates multiple response items:
                        request.requestTarget(BlockingTestBiDiStreamRpc.PATH);
                        return super.request(delegate, strategy, request);
                    }
                }).buildBlocking(new ClientFactory())) {
            IllegalStateException e = assertThrows(IllegalStateException.class,
                    () -> client.testRequestStream(asList(newRequest(), newRequest())));
            assertThat(e.getMessage(), equalTo("More than one response message received"));
        }
    }

    private static TestRequest newRequest() {
        return TestRequest.newBuilder().setName("request").build();
    }

    private static TestResponse newResponse() {
        return TestResponse.newBuilder().setMessage("response").build();
    }
}

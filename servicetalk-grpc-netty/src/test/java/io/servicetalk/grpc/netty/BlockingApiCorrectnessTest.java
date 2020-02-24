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
import io.servicetalk.grpc.netty.TesterProto.Tester.BlockingTestRequestStreamRpc;
import io.servicetalk.grpc.netty.TesterProto.Tester.BlockingTestResponseStreamRpc;
import io.servicetalk.grpc.netty.TesterProto.Tester.BlockingTesterClient;
import io.servicetalk.grpc.netty.TesterProto.Tester.BlockingTesterService;
import io.servicetalk.grpc.netty.TesterProto.Tester.ClientFactory;
import io.servicetalk.grpc.netty.TesterProto.Tester.ServiceFactory;
import io.servicetalk.http.api.HttpServiceContext;
import io.servicetalk.http.api.StreamingHttpRequest;
import io.servicetalk.http.api.StreamingHttpResponse;
import io.servicetalk.http.api.StreamingHttpResponseFactory;
import io.servicetalk.http.api.StreamingHttpServiceFilter;
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
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.startsWith;
import static org.junit.Assert.assertThrows;

public class BlockingApiCorrectnessTest {

    @Rule
    public Timeout timeout = new ServiceTalkTestTimeout();

    private final ServerContext serverContext;

    public BlockingApiCorrectnessTest() throws Exception {
        serverContext = GrpcServers.forAddress(localAddress(0))
                // HTTP filter to modify paths to workaround API restrictions:
                .appendHttpServiceFilter(service -> new StreamingHttpServiceFilter(service) {

                    @Override
                    public Single<StreamingHttpResponse> handle(HttpServiceContext ctx, StreamingHttpRequest request,
                                                                StreamingHttpResponseFactory responseFactory) {
                        if (BlockingTestBiDiStreamRpc.PATH.equals(request.requestTarget())) {
                            // Forward multiple items to the API that expects only a single request item:
                            request.requestTarget(BlockingTestResponseStreamRpc.PATH);
                        } else if (BlockingTestRequestStreamRpc.PATH.equals(request.requestTarget())) {
                            // Forward request that expects a single response item to the API that generates multiple
                            // response items:
                            request.requestTarget(BlockingTestBiDiStreamRpc.PATH);
                        }
                        return delegate().handle(ctx, request, responseFactory);
                    }
                })
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
                .buildBlocking(new ClientFactory())) {
            GrpcStatusException e = assertThrows(GrpcStatusException.class, () ->
                    // Send multiple items that will be forwarded to BlockingTestResponseStreamRpc.PATH on the server:
                    client.testBiDiStream(asList(newRequest(), newRequest())).forEach(response -> { /* noop */ }));
            assertThat(e.status().code(), is(INVALID_ARGUMENT));
            assertThat(e.status().description(), startsWith("Only a single request item is expected"));
        }
    }

    @Test
    public void clientBlockingRequestStreamingCallFailsOnSecondResponseItem() throws Exception {
        try (BlockingTesterClient client = GrpcClients.forAddress(serverHostAndPort(serverContext))
                .buildBlocking(new ClientFactory())) {
            IllegalStateException e = assertThrows(IllegalStateException.class,
                    // This request will be forwarded to BlockingTestBiDiStreamRpc.PATH on the server:
                    () -> client.testRequestStream(asList(newRequest(), newRequest())));
            assertThat(e.getMessage(), startsWith("Only a single response item is expected"));
        }
    }

    private static TestRequest newRequest() {
        return TestRequest.newBuilder().setName("request").build();
    }

    private static TestResponse newResponse() {
        return TestResponse.newBuilder().setMessage("response").build();
    }
}

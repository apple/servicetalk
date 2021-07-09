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

import io.servicetalk.grpc.api.GrpcStatusCode;
import io.servicetalk.grpc.api.GrpcStatusException;
import io.servicetalk.grpc.netty.TesterProto.TestRequest;
import io.servicetalk.http.netty.HttpServers;
import io.servicetalk.transport.api.ServerContext;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

import java.util.concurrent.ExecutionException;

import static io.servicetalk.concurrent.api.Publisher.from;
import static io.servicetalk.concurrent.api.Single.succeeded;
import static io.servicetalk.http.api.HttpSerializationProviders.textSerializer;
import static io.servicetalk.http.netty.HttpProtocolConfigs.h2Default;
import static io.servicetalk.transport.netty.internal.AddressUtils.localAddress;
import static io.servicetalk.transport.netty.internal.AddressUtils.serverHostAndPort;
import static java.util.Collections.singletonList;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.instanceOf;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class HttpResponseUponGrpcRequestTest {

    private final TesterProto.Tester.BlockingTesterClient client;

    HttpResponseUponGrpcRequestTest() throws Exception {
        final String responsePayload = "non-grpc error!";
        ServerContext serverContext = HttpServers.forAddress(localAddress(0))
                .protocols(h2Default())
                .listenAndAwait((ctx, request, responseFactory) ->
                        succeeded(responseFactory.badRequest().payloadBody(responsePayload, textSerializer())));

        client = GrpcClients.forAddress(serverHostAndPort(serverContext))
                .buildBlocking(new TesterProto.Tester.ClientFactory());
    }

    @Test
    void testBlockingAggregated() {
        assertThrowsGrpcStatusException(() -> client.test(request()));
    }

    @Test
    void testBlockingRequestStreaming() {
        assertThrowsGrpcStatusException(() -> client.testRequestStream(singletonList(request())));
    }

    @Test
    void testBlockingResponseStreaming() {
        assertThrowsGrpcStatusException(() -> client.testResponseStream(request()).forEach(__ -> { /* noop */ }));
    }

    @Test
    void testBlockingBiDiStreaming() {
        assertThrowsGrpcStatusException(() -> client.testBiDiStream(singletonList(request()))
                .forEach(__ -> { /* noop */ }));
    }

    @Test
    void testAggregated() {
        assertThrowsExecutionException(() -> client.asClient().test(request()).toFuture().get());
    }

    @Test
    void testRequestStreaming() {
        assertThrowsExecutionException(() -> client.asClient().testRequestStream(from(request())).toFuture().get());
    }

    @Test
    void testResponseStreaming() {
        assertThrowsExecutionException(() -> client.asClient().testResponseStream(request()).toFuture().get());
    }

    @Test
    void testBiDiStreaming() {
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
        assertThat(grpcStatusException.status().code(), is(GrpcStatusCode.INTERNAL));
        assertThat(grpcStatusException.status().description(), notNullValue());
        assertTrue(grpcStatusException.status().description().contains("status code: 400 Bad Request"));
        assertTrue(grpcStatusException.status().description().contains("invalid content-type: text/plain;"));
        assertTrue(grpcStatusException.status().description().contains("headers:"));
    }
}

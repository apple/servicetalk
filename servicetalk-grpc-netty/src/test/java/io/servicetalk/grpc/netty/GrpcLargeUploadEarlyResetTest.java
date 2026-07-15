/*
 * Copyright © 2026 Apple Inc. and the ServiceTalk project authors
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

import io.servicetalk.grpc.api.GrpcStatusException;
import io.servicetalk.grpc.netty.CompatProto.Compat;
import io.servicetalk.grpc.netty.CompatProto.Compat.CompatClient;
import io.servicetalk.grpc.netty.CompatProto.RequestContainer.CompatRequest;
import io.servicetalk.grpc.netty.CompatProto.ResponseContainer.CompatResponse;

import com.google.protobuf.ByteString;
import io.grpc.Server;
import io.grpc.netty.NettyServerBuilder;
import io.grpc.stub.StreamObserver;
import org.junit.jupiter.api.Test;

import java.net.InetSocketAddress;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;

import static io.servicetalk.transport.netty.internal.AddressUtils.localAddress;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.instanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Reproduces a client-side hang: when a ServiceTalk gRPC client sends a unary request larger than the
 * initial HTTP/2 flow-control window (~64 KiB) and the server rejects it on the first message frame
 * (RST_STREAM before opening the window), the response {@code Single} never terminates instead of
 * surfacing the server's error.
 */
class GrpcLargeUploadEarlyResetTest {

    // Constrain the server's HTTP/2 receive window to the spec default (~64 KiB); this payload exceeds it
    // so the client's request write is flow-control-blocked before the whole message is delivered.
    private static final int FLOW_CONTROL_WINDOW = 65535;
    private static final int PAYLOAD_SIZE = 128 * 1024;

    @Test
    void serviceTalkClientSurfacesEarlyResetMidUpload() throws Exception {
        // grpc-java server with a tiny inbound limit rejects on the first message frame with
        // RESOURCE_EXHAUSTED and resets the stream without ever opening the flow-control window.
        final Server grpcServer = NettyServerBuilder.forAddress(localAddress(0))
                .flowControlWindow(FLOW_CONTROL_WINDOW)
                .maxInboundMessageSize(1024)
                .addService(new CompatGrpc.CompatImplBase() {
                    @Override
                    public void scalarCall(final CompatRequest request,
                                           final StreamObserver<CompatResponse> responseObserver) {
                        responseObserver.onNext(CompatResponse.newBuilder().setId(request.getId()).build());
                        responseObserver.onCompleted();
                    }
                })
                .build().start();
        try {
            final InetSocketAddress serverAddress = (InetSocketAddress) grpcServer.getListenSockets().get(0);
            try (CompatClient client = GrpcClients.forResolvedAddress(serverAddress)
                    .build(new Compat.ClientFactory())) {
                final CompatRequest request = CompatRequest.newBuilder()
                        .setId(1)
                        .setPayload(ByteString.copyFrom(new byte[PAYLOAD_SIZE]))
                        .build();

                final Future<CompatResponse> response = client.scalarCall(request).toFuture();

                // Bug: while the request write is flow-control-blocked, the inbound reset/trailers are not
                // surfaced and this get() blocks until the timeout instead of failing fast.
                final ExecutionException e =
                        assertThrows(ExecutionException.class, () -> response.get(10, SECONDS));
                assertThat(e.getCause(), instanceOf(GrpcStatusException.class));
            }
        } finally {
            grpcServer.shutdownNow().awaitTermination(10, SECONDS);
        }
    }
}

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

import io.servicetalk.concurrent.api.Publisher;
import io.servicetalk.concurrent.api.Single;
import io.servicetalk.encoding.api.BufferDecoderGroupBuilder;
import io.servicetalk.encoding.api.Identity;
import io.servicetalk.grpc.api.DefaultGrpcClientMetadata;
import io.servicetalk.grpc.api.GrpcServerContext;
import io.servicetalk.grpc.api.GrpcServiceContext;
import io.servicetalk.grpc.api.GrpcStatusException;

import io.grpc.examples.helloworld.Greeter;
import io.grpc.examples.helloworld.Greeter.BlockingGreeterClient;
import io.grpc.examples.helloworld.Greeter.BlockingGreeterService;
import io.grpc.examples.helloworld.Greeter.ClientFactory;
import io.grpc.examples.helloworld.HelloReply;
import io.grpc.examples.helloworld.HelloRequest;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.concurrent.ExecutionException;

import static io.servicetalk.encoding.netty.NettyBufferEncoders.gzipDefault;
import static io.servicetalk.grpc.api.GrpcStatusCode.RESOURCE_EXHAUSTED;
import static io.servicetalk.grpc.netty.GrpcClients.forAddress;
import static io.servicetalk.grpc.netty.GrpcServers.forAddress;
import static io.servicetalk.transport.netty.internal.AddressUtils.localAddress;
import static io.servicetalk.transport.netty.internal.AddressUtils.serverHostAndPort;
import static java.util.Collections.singletonList;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.instanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;

class GrpcLargeMessageTest {

    // Larger than both the HTTP-level default aggregated payload limit and the gRPC default inbound message limit
    // (both 4 MiB). Used with the gRPC limit disabled to prove neither default silently caps gRPC payloads.
    private static final int PAYLOAD_SIZE = 8 * 1024 * 1024;
    // A small inbound limit and a message comfortably above it, for the enforcement tests.
    private static final int SMALL_LIMIT = 1024;
    private static final int ABOVE_SMALL_LIMIT = 4 * 1024;

    private static BlockingGreeterService echoService() {
        return new BlockingGreeterService() {
            @Override
            public HelloReply sayHello(GrpcServiceContext ctx, HelloRequest request) {
                return HelloReply.newBuilder().setMessage(request.getName()).build();
            }
        };
    }

    private static String repeat(final int length) {
        final char[] chars = new char[length];
        Arrays.fill(chars, 'x');
        return new String(chars);
    }

    @Test
    void unaryMessageLargerThanDefaultLimitRoundTripsWhenLimitDisabled() throws Exception {
        final String large = repeat(PAYLOAD_SIZE);

        try (GrpcServerContext server = forAddress(localAddress(0))
                     .maxInboundMessageSize(0)
                     .listenAndAwait(echoService());
             BlockingGreeterClient client = forAddress(serverHostAndPort(server))
                     .maxInboundMessageSize(0)
                     .buildBlocking(new ClientFactory())) {
            HelloReply reply = client.sayHello(HelloRequest.newBuilder().setName(large).build());
            assertThat(reply.getMessage().length(), equalTo(PAYLOAD_SIZE));
        }
    }

    @Test
    void serverRejectsRequestExceedingMaxInboundMessageSize() throws Exception {
        try (GrpcServerContext server = forAddress(localAddress(0))
                     .maxInboundMessageSize(SMALL_LIMIT)
                     .listenAndAwait(echoService());
             BlockingGreeterClient client = forAddress(serverHostAndPort(server))
                     .buildBlocking(new ClientFactory())) {
            GrpcStatusException e = assertThrows(GrpcStatusException.class, () ->
                    client.sayHello(HelloRequest.newBuilder().setName(repeat(ABOVE_SMALL_LIMIT)).build()));
            assertThat(e.status().code(), equalTo(RESOURCE_EXHAUSTED));
        }
    }

    @Test
    void clientRejectsResponseExceedingMaxInboundMessageSize() throws Exception {
        try (GrpcServerContext server = forAddress(localAddress(0)).listenAndAwait(echoService());
             BlockingGreeterClient client = forAddress(serverHostAndPort(server))
                     .maxInboundMessageSize(SMALL_LIMIT)
                     .buildBlocking(new ClientFactory())) {
            GrpcStatusException e = assertThrows(GrpcStatusException.class, () ->
                    client.sayHello(HelloRequest.newBuilder().setName(repeat(ABOVE_SMALL_LIMIT)).build()));
            assertThat(e.status().code(), equalTo(RESOURCE_EXHAUSTED));
        }
    }

    @Test
    void messageWithinMaxInboundMessageSizeRoundTrips() throws Exception {
        final String small = repeat(SMALL_LIMIT / 2);

        try (GrpcServerContext server = forAddress(localAddress(0))
                     .maxInboundMessageSize(SMALL_LIMIT)
                     .listenAndAwait(echoService());
             BlockingGreeterClient client = forAddress(serverHostAndPort(server))
                     .maxInboundMessageSize(SMALL_LIMIT)
                     .buildBlocking(new ClientFactory())) {
            HelloReply reply = client.sayHello(HelloRequest.newBuilder().setName(small).build());
            assertThat(reply.getMessage(), equalTo(small));
        }
    }

    @Test
    void clientRejectsStreamingResponseExceedingMaxInboundMessageSize() throws Exception {
        final String large = repeat(ABOVE_SMALL_LIMIT);

        try (GrpcServerContext server = forAddress(localAddress(0)).listenAndAwait(
                     new TesterProto.Tester.ServiceFactory(new TesterProto.Tester.TesterService() {
                         @Override
                         public Publisher<TesterProto.TestResponse> testResponseStream(
                                 GrpcServiceContext ctx, TesterProto.TestRequest request) {
                             return Publisher.from(TesterProto.TestResponse.newBuilder().setMessage(large).build());
                         }

                         @Override
                         public Publisher<TesterProto.TestResponse> testBiDiStream(
                                 GrpcServiceContext ctx, Publisher<TesterProto.TestRequest> request) {
                             return Publisher.empty();
                         }

                         @Override
                         public Single<TesterProto.TestResponse> testRequestStream(
                                 GrpcServiceContext ctx, Publisher<TesterProto.TestRequest> request) {
                             return Single.succeeded(TesterProto.TestResponse.getDefaultInstance());
                         }

                         @Override
                         public Single<TesterProto.TestResponse> test(
                                 GrpcServiceContext ctx, TesterProto.TestRequest request) {
                             return Single.succeeded(TesterProto.TestResponse.getDefaultInstance());
                         }
                     }));
             TesterProto.Tester.TesterClient client = forAddress(serverHostAndPort(server))
                     .maxInboundMessageSize(SMALL_LIMIT)
                     .build(new TesterProto.Tester.ClientFactory())) {
            ExecutionException e = assertThrows(ExecutionException.class, () ->
                    client.testResponseStream(TesterProto.TestRequest.newBuilder().setName("x").build())
                            .toFuture().get());
            assertThat(e.getCause(), instanceOf(GrpcStatusException.class));
            assertThat(((GrpcStatusException) e.getCause()).status().code(), equalTo(RESOURCE_EXHAUSTED));
        }
    }

    @Test
    void serverRejectsStreamingRequestExceedingMaxInboundMessageSize() throws Exception {
        final String large = repeat(ABOVE_SMALL_LIMIT);

        try (GrpcServerContext server = forAddress(localAddress(0))
                     .maxInboundMessageSize(SMALL_LIMIT)
                     .listenAndAwait(new TesterProto.Tester.ServiceFactory(new TesterProto.Tester.TesterService() {
                         @Override
                         public Single<TesterProto.TestResponse> testRequestStream(
                                 GrpcServiceContext ctx, Publisher<TesterProto.TestRequest> request) {
                             return request.collect(StringBuilder::new, (sb, r) -> sb.append(r.getName()))
                                     .map(sb -> TesterProto.TestResponse.newBuilder().setMessage(sb.toString())
                                             .build());
                         }

                         @Override
                         public Publisher<TesterProto.TestResponse> testBiDiStream(
                                 GrpcServiceContext ctx, Publisher<TesterProto.TestRequest> request) {
                             return Publisher.empty();
                         }

                         @Override
                         public Publisher<TesterProto.TestResponse> testResponseStream(
                                 GrpcServiceContext ctx, TesterProto.TestRequest request) {
                             return Publisher.empty();
                         }

                         @Override
                         public Single<TesterProto.TestResponse> test(
                                 GrpcServiceContext ctx, TesterProto.TestRequest request) {
                             return Single.succeeded(TesterProto.TestResponse.getDefaultInstance());
                         }
                     }));
             TesterProto.Tester.TesterClient client = forAddress(serverHostAndPort(server))
                     .build(new TesterProto.Tester.ClientFactory())) {
            ExecutionException e = assertThrows(ExecutionException.class, () ->
                    client.testRequestStream(Publisher.from(
                            TesterProto.TestRequest.newBuilder().setName(large).build())).toFuture().get());
            assertThat(e.getCause(), instanceOf(GrpcStatusException.class));
            assertThat(((GrpcStatusException) e.getCause()).status().code(), equalTo(RESOURCE_EXHAUSTED));
        }
    }

    @Test
    void warnOnlyDeliversMessageExceedingLimit() throws Exception {
        // Warn-only warns at the (4 MiB) default threshold but still delivers, so a message above it must round-trip.
        final String large = repeat(PAYLOAD_SIZE);

        try (GrpcServerContext server = forAddress(localAddress(0))
                     .maxInboundMessageSize(-1)
                     .listenAndAwait(echoService());
             BlockingGreeterClient client = forAddress(serverHostAndPort(server))
                     .maxInboundMessageSize(-1)
                     .buildBlocking(new ClientFactory())) {
            HelloReply reply = client.sayHello(HelloRequest.newBuilder().setName(large).build());
            assertThat(reply.getMessage().length(), equalTo(PAYLOAD_SIZE));
        }
    }

    @Test
    void clientRejectsCompressedResponseDecompressingAboveLimit() throws Exception {
        // Highly compressible: small on the wire (under the limit) but large once decompressed (over the limit).
        // Exercises the decompressed-size enforcement, distinct from the compressed frame-length check.
        final String large = repeat(ABOVE_SMALL_LIMIT);

        try (GrpcServerContext server = forAddress(localAddress(0)).listenAndAwait(
                     new Greeter.ServiceFactory.Builder()
                             .bufferEncoders(singletonList(gzipDefault()))
                             .sayHelloBlocking((ctx, request) ->
                                     HelloReply.newBuilder().setMessage(large).build())
                             .build());
             BlockingGreeterClient client = forAddress(serverHostAndPort(server))
                     .maxInboundMessageSize(SMALL_LIMIT)
                     .buildBlocking(new ClientFactory().bufferDecoderGroup(
                             new BufferDecoderGroupBuilder().add(gzipDefault()).build()))) {
            GrpcStatusException e = assertThrows(GrpcStatusException.class, () ->
                    client.sayHello(HelloRequest.newBuilder().setName("x").build()));
            assertThat(e.status().code(), equalTo(RESOURCE_EXHAUSTED));
        }
    }

    // Disabled: client-side request compression trips a pre-existing JVM-wide serializer-cache collision in
    // DefaultGrpcClientCallFactory (its static serializerMap is keyed only by the BufferEncoder, so compressing a
    // HelloRequest with gzip poisons the shared cache for other message types). Re-enable once fixed in a follow-up PR.
    @Disabled("Pre-existing serializer-cache collision in DefaultGrpcClientCallFactory; to be fixed in a follow-up PR")
    @Test
    void serverRejectsCompressedRequestDecompressingAboveLimit() throws Exception {
        // Small on the wire (highly compressible) but large once the server decompresses it: exercises the
        // server-side decompressed-size enforcement, mirroring the client-side response case.
        final String large = repeat(ABOVE_SMALL_LIMIT);

        try (GrpcServerContext server = forAddress(localAddress(0))
                     .maxInboundMessageSize(SMALL_LIMIT)
                     .listenAndAwait(new Greeter.ServiceFactory.Builder()
                             .bufferDecoderGroup(new BufferDecoderGroupBuilder().add(gzipDefault()).build())
                             .sayHelloBlocking((ctx, request) ->
                                     HelloReply.newBuilder().setMessage("x").build())
                             .build());
             BlockingGreeterClient client = forAddress(serverHostAndPort(server))
                     .buildBlocking(new ClientFactory())) {
            GrpcStatusException e = assertThrows(GrpcStatusException.class, () ->
                    client.sayHello(new DefaultGrpcClientMetadata(gzipDefault()),
                            HelloRequest.newBuilder().setName(large).build()));
            assertThat(e.status().code(), equalTo(RESOURCE_EXHAUSTED));
        }
    }

    // Disabled: client-side request compression trips a pre-existing JVM-wide serializer-cache collision in
    // DefaultGrpcClientCallFactory (its static serializerMap is keyed only by the BufferEncoder, so compressing a
    // HelloRequest with gzip poisons the shared cache for other message types). Re-enable once fixed in a follow-up PR.
    @Disabled("Pre-existing serializer-cache collision in DefaultGrpcClientCallFactory; to be fixed in a follow-up PR")
    @Test
    void compressedRequestWithinMaxInboundMessageSizeRoundTrips() throws Exception {
        // A compressed request that decompresses to under the limit must round-trip, so the limit does not falsely
        // reject legitimate compressed messages.
        final String small = repeat(SMALL_LIMIT / 2);

        try (GrpcServerContext server = forAddress(localAddress(0))
                     .maxInboundMessageSize(SMALL_LIMIT)
                     .listenAndAwait(new Greeter.ServiceFactory.Builder()
                             .bufferDecoderGroup(new BufferDecoderGroupBuilder().add(gzipDefault()).build())
                             .sayHelloBlocking((ctx, request) ->
                                     HelloReply.newBuilder().setMessage(request.getName()).build())
                             .build());
             BlockingGreeterClient client = forAddress(serverHostAndPort(server))
                     .buildBlocking(new ClientFactory())) {
            HelloReply reply = client.sayHello(new DefaultGrpcClientMetadata(gzipDefault()),
                    HelloRequest.newBuilder().setName(small).build());
            assertThat(reply.getMessage(), equalTo(small));
        }
    }

    @Test
    @SuppressWarnings("deprecation")
    void deprecatedSerializationProviderPathEnforcesMaxInboundMessageSize() throws Exception {
        // A non-empty (deprecated) supportedMessageCodings routes the server through the GrpcSerializationProvider
        // (ProtoBufSerializationProviderBuilder) path. This unary request is HTTP-aggregated, so it is rejected at the
        // coordinated HTTP maxAggregatedPayloadSize bound (mapped to RESOURCE_EXHAUSTED) before the deserializer runs.
        try (GrpcServerContext server = forAddress(localAddress(0))
                     .maxInboundMessageSize(SMALL_LIMIT)
                     .listenAndAwait(new Greeter.ServiceFactory(echoService(), singletonList(Identity.identity())));
             BlockingGreeterClient client = forAddress(serverHostAndPort(server))
                     .buildBlocking(new ClientFactory())) {
            GrpcStatusException e = assertThrows(GrpcStatusException.class, () ->
                    client.sayHello(HelloRequest.newBuilder().setName(repeat(ABOVE_SMALL_LIMIT)).build()));
            assertThat(e.status().code(), equalTo(RESOURCE_EXHAUSTED));
        }
    }

    @Test
    @SuppressWarnings("deprecation")
    void deprecatedSerializationProviderStreamingPathEnforcesMaxInboundMessageSize() throws Exception {
        // Streaming is not HTTP-aggregated, so an oversized message reaches the GrpcSerializationProvider path's
        // deframer (GrpcStreamingDeserializer) and is rejected there by the message-size limiter.
        final String large = repeat(ABOVE_SMALL_LIMIT);

        try (GrpcServerContext server = forAddress(localAddress(0))
                     .maxInboundMessageSize(SMALL_LIMIT)
                     .listenAndAwait(new TesterProto.Tester.ServiceFactory(new TesterProto.Tester.TesterService() {
                         @Override
                         public Single<TesterProto.TestResponse> testRequestStream(
                                 GrpcServiceContext ctx, Publisher<TesterProto.TestRequest> request) {
                             return request.collect(StringBuilder::new, (sb, r) -> sb.append(r.getName()))
                                     .map(sb -> TesterProto.TestResponse.newBuilder().setMessage(sb.toString())
                                             .build());
                         }

                         @Override
                         public Publisher<TesterProto.TestResponse> testBiDiStream(
                                 GrpcServiceContext ctx, Publisher<TesterProto.TestRequest> request) {
                             return Publisher.empty();
                         }

                         @Override
                         public Publisher<TesterProto.TestResponse> testResponseStream(
                                 GrpcServiceContext ctx, TesterProto.TestRequest request) {
                             return Publisher.empty();
                         }

                         @Override
                         public Single<TesterProto.TestResponse> test(
                                 GrpcServiceContext ctx, TesterProto.TestRequest request) {
                             return Single.succeeded(TesterProto.TestResponse.getDefaultInstance());
                         }
                     }, singletonList(Identity.identity())));
             TesterProto.Tester.TesterClient client = forAddress(serverHostAndPort(server))
                     .build(new TesterProto.Tester.ClientFactory())) {
            ExecutionException e = assertThrows(ExecutionException.class, () ->
                    client.testRequestStream(Publisher.from(
                            TesterProto.TestRequest.newBuilder().setName(large).build())).toFuture().get());
            assertThat(e.getCause(), instanceOf(GrpcStatusException.class));
            assertThat(((GrpcStatusException) e.getCause()).status().code(), equalTo(RESOURCE_EXHAUSTED));
        }
    }
}

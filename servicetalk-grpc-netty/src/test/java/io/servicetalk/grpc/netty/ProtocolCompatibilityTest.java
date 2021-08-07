/*
 * Copyright © 2019-2021 Apple Inc. and the ServiceTalk project authors
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
import io.servicetalk.concurrent.PublisherSource;
import io.servicetalk.concurrent.SingleSource.Processor;
import io.servicetalk.concurrent.api.Completable;
import io.servicetalk.concurrent.api.Publisher;
import io.servicetalk.concurrent.api.Single;
import io.servicetalk.encoding.api.BufferDecoderGroup;
import io.servicetalk.encoding.api.BufferDecoderGroupBuilder;
import io.servicetalk.encoding.api.BufferEncoder;
import io.servicetalk.encoding.api.EmptyBufferDecoderGroup;
import io.servicetalk.encoding.api.Identity;
import io.servicetalk.encoding.netty.NettyBufferEncoders;
import io.servicetalk.grpc.api.DefaultGrpcClientMetadata;
import io.servicetalk.grpc.api.GrpcClientBuilder;
import io.servicetalk.grpc.api.GrpcClientMetadata;
import io.servicetalk.grpc.api.GrpcExecutionContext;
import io.servicetalk.grpc.api.GrpcExecutionStrategy;
import io.servicetalk.grpc.api.GrpcPayloadWriter;
import io.servicetalk.grpc.api.GrpcServerBuilder;
import io.servicetalk.grpc.api.GrpcServiceContext;
import io.servicetalk.grpc.api.GrpcStatus;
import io.servicetalk.grpc.api.GrpcStatusCode;
import io.servicetalk.grpc.api.GrpcStatusException;
import io.servicetalk.grpc.netty.CompatProto.Compat;
import io.servicetalk.grpc.netty.CompatProto.Compat.BidirectionalStreamingCallMetadata;
import io.servicetalk.grpc.netty.CompatProto.Compat.BlockingCompatClient;
import io.servicetalk.grpc.netty.CompatProto.Compat.BlockingCompatService;
import io.servicetalk.grpc.netty.CompatProto.Compat.ClientStreamingCallMetadata;
import io.servicetalk.grpc.netty.CompatProto.Compat.CompatClient;
import io.servicetalk.grpc.netty.CompatProto.Compat.ScalarCallMetadata;
import io.servicetalk.grpc.netty.CompatProto.Compat.ServerStreamingCallMetadata;
import io.servicetalk.grpc.netty.CompatProto.Compat.ServiceFactory;
import io.servicetalk.grpc.netty.CompatProto.RequestContainer.CompatRequest;
import io.servicetalk.grpc.netty.CompatProto.ResponseContainer.CompatResponse;
import io.servicetalk.http.api.HttpServiceContext;
import io.servicetalk.http.api.StreamingHttpRequest;
import io.servicetalk.http.api.StreamingHttpResponse;
import io.servicetalk.http.api.StreamingHttpResponseFactory;
import io.servicetalk.http.api.StreamingHttpServiceFilter;
import io.servicetalk.test.resources.DefaultTestCerts;
import io.servicetalk.transport.api.ClientSslConfigBuilder;
import io.servicetalk.transport.api.ServerContext;
import io.servicetalk.transport.api.ServerSslConfigBuilder;

import com.google.protobuf.Any;
import com.google.protobuf.InvalidProtocolBufferException;
import io.grpc.Codec;
import io.grpc.Compressor;
import io.grpc.CompressorRegistry;
import io.grpc.Decompressor;
import io.grpc.DecompressorRegistry;
import io.grpc.ManagedChannel;
import io.grpc.Server;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import io.grpc.netty.GrpcSslContexts;
import io.grpc.netty.NettyChannelBuilder;
import io.grpc.netty.NettyServerBuilder;
import io.grpc.protobuf.StatusProto;
import io.grpc.stub.StreamObserver;
import io.netty.handler.ssl.SslContext;
import io.netty.handler.ssl.util.InsecureTrustManagerFactory;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.stream.IntStream;
import javax.annotation.Nullable;

import static com.google.protobuf.Any.pack;
import static io.servicetalk.concurrent.api.Processors.newPublisherProcessor;
import static io.servicetalk.concurrent.api.Processors.newSingleProcessor;
import static io.servicetalk.concurrent.api.Single.succeeded;
import static io.servicetalk.concurrent.api.SourceAdapters.fromSource;
import static io.servicetalk.concurrent.internal.TestTimeoutConstants.DEFAULT_TIMEOUT_SECONDS;
import static io.servicetalk.grpc.api.GrpcExecutionStrategies.defaultStrategy;
import static io.servicetalk.grpc.api.GrpcExecutionStrategies.noOffloadsStrategy;
import static io.servicetalk.test.resources.DefaultTestCerts.loadServerKey;
import static io.servicetalk.test.resources.DefaultTestCerts.loadServerPem;
import static io.servicetalk.test.resources.DefaultTestCerts.serverPemHostname;
import static io.servicetalk.transport.api.SslProvider.OPENSSL;
import static io.servicetalk.transport.netty.internal.AddressUtils.localAddress;
import static java.util.Arrays.asList;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.fail;

class ProtocolCompatibilityTest {
    private interface TestServerContext extends AutoCloseable {
        SocketAddress listenAddress();

        static TestServerContext fromServiceTalkServerContext(final ServerContext serverContext) {
            return new TestServerContext() {
                @Override
                public void close() throws Exception {
                    // Internally this performs a graceful close (like for the grpc-java variant below)
                    serverContext.close();
                }

                @Override
                public SocketAddress listenAddress() {
                    return serverContext.listenAddress();
                }
            };
        }

        static TestServerContext fromGrpcJavaServer(final Server server) {
            return new TestServerContext() {
                @Override
                public void close() {
                    try {
                        if (!server.shutdown().awaitTermination(DEFAULT_TIMEOUT_SECONDS, SECONDS)) {
                            server.shutdownNow();
                        }
                    } catch (final InterruptedException e) {
                        throw new RuntimeException(e);
                    }
                }

                @Override
                public SocketAddress listenAddress() {
                    return server.getListenSockets().get(0);
                }
            };
        }
    }

    private static final String CUSTOM_ERROR_MESSAGE = "custom error message";

    private enum ErrorMode {
        NONE,
        SIMPLE,
        SIMPLE_IN_SERVER_FILTER,
        SIMPLE_IN_SERVICE_FILTER,
        SIMPLE_IN_RESPONSE,
        STATUS,
        STATUS_IN_SERVER_FILTER,
        STATUS_IN_SERVICE_FILTER,
        STATUS_IN_RESPONSE
    }

    private static final boolean[] SSL = {false, true};
    private static final boolean[] STREAMING = {false, true};
    private static final String[] COMPRESSION = {"gzip", "identity", null};

    private static Collection<Arguments> sslStreamingAndCompressionParams() {
        List<Arguments> args = new ArrayList<>();
        for (boolean ssl : SSL) {
            for (boolean streaming : STREAMING) {
                for (String compression : COMPRESSION) {
                    args.add(Arguments.of(ssl, streaming, compression));
                }
            }
        }
        return args;
    }

    private static Collection<Arguments> sslAndStreamingParams() {
        List<Arguments> args = new ArrayList<>();
        for (boolean ssl : SSL) {
            for (boolean streaming : STREAMING) {
                    args.add(Arguments.of(ssl, streaming));
                }
        }
        return args;
    }

    private static Collection<Arguments> sslAndCompressionParams() {
        List<Arguments> args = new ArrayList<>();
        for (boolean ssl : SSL) {
            for (String compression : COMPRESSION) {
                args.add(Arguments.of(ssl, compression));
            }
        }
        return args;
    }

    @ParameterizedTest
    @MethodSource("sslStreamingAndCompressionParams")
    void grpcJavaToGrpcJava(final boolean ssl,
                            final boolean streaming,
                            final String compression) throws Exception {
        final TestServerContext server = grpcJavaServer(ErrorMode.NONE, ssl, compression);
        final CompatClient client = grpcJavaClient(server.listenAddress(), compression, ssl, null);
        testRequestResponse(client, server, streaming, compression);
    }

    @ParameterizedTest
    @MethodSource("sslStreamingAndCompressionParams")
    void serviceTalkToGrpcJava(final boolean ssl,
                               final boolean streaming,
                               final String compression) throws Exception {
        final TestServerContext server = grpcJavaServer(ErrorMode.NONE, ssl, compression);
        final CompatClient client = serviceTalkClient(server.listenAddress(), ssl, compression, null);
        testRequestResponse(client, server, streaming, compression);
    }

    @ParameterizedTest
    @MethodSource("sslStreamingAndCompressionParams")
    void grpcJavaToServiceTalk(final boolean ssl,
                               final boolean streaming,
                               final String compression) throws Exception {
        final TestServerContext server = serviceTalkServer(ErrorMode.NONE, ssl, compression, null);
        final CompatClient client = grpcJavaClient(server.listenAddress(), compression, ssl, null);
        testRequestResponse(client, server, streaming, compression);
    }

    @ParameterizedTest
    @MethodSource("sslStreamingAndCompressionParams")
    void serviceTalkToServiceTalk(final boolean ssl,
                                  final boolean streaming,
                                  final String compression) throws Exception {
        final TestServerContext server = serviceTalkServer(ErrorMode.NONE, ssl, compression, null);
        final CompatClient client = serviceTalkClient(server.listenAddress(), ssl, compression, null);
        testRequestResponse(client, server, streaming, compression);
    }

    @ParameterizedTest
    @MethodSource("sslStreamingAndCompressionParams")
    void serviceTalkBlockingToServiceTalkBlocking(final boolean ssl,
                                                  final boolean streaming,
                                                  final String compression)
            throws Exception {
        final TestServerContext server = serviceTalkServerBlocking(ErrorMode.NONE, ssl, compression);
        final BlockingCompatClient client = serviceTalkClient(server.listenAddress(), ssl, compression, null)
                .asBlockingClient();
        testBlockingRequestResponse(client, server, streaming, compression);
    }

    @ParameterizedTest
    @MethodSource("sslAndStreamingParams")
    void grpcJavaToGrpcJavaCompressionError(final boolean ssl,
                                            final boolean streaming)
            throws Exception {
        final String clientCompression = "gzip";
        final TestServerContext server = grpcJavaServer(ErrorMode.NONE, ssl, null);
        final CompatClient client = grpcJavaClient(server.listenAddress(), clientCompression, ssl, null);
        testGrpcError(client, server, false, streaming, clientCompression, GrpcStatusCode.UNIMPLEMENTED, null);
    }

    @ParameterizedTest
    @MethodSource("sslAndStreamingParams")
    void grpcJavaToServiceTalkCompressionError(final boolean ssl,
                                               final boolean streaming)
            throws Exception {
        final String clientCompression = "gzip";
        final TestServerContext server = serviceTalkServer(ErrorMode.NONE, ssl, null, null);
        final CompatClient client = grpcJavaClient(server.listenAddress(), clientCompression, ssl, null);
        testGrpcError(client, server, false, streaming, clientCompression, GrpcStatusCode.UNIMPLEMENTED, null);
    }

    @ParameterizedTest
    @MethodSource("sslAndStreamingParams")
    void serviceTalkToGrpcJavaCompressionError(final boolean ssl,
                                               final boolean streaming)
            throws Exception {
        final String clientCompression = "gzip";
        final TestServerContext server = grpcJavaServer(ErrorMode.NONE, ssl, null);
        final CompatClient client = serviceTalkClient(server.listenAddress(), ssl, clientCompression, null);
        testGrpcError(client, server, false, streaming, clientCompression, GrpcStatusCode.UNIMPLEMENTED, null);
    }

    @ParameterizedTest
    @MethodSource("sslAndStreamingParams")
    void serviceTalkToServiceTalkCompressionError(final boolean ssl,
                                                  final boolean streaming)
            throws Exception {
        final String clientCompression = "gzip";
        final TestServerContext server = serviceTalkServer(ErrorMode.NONE, ssl, null, null);
        final CompatClient client = serviceTalkClient(server.listenAddress(), ssl, clientCompression, null);
        testGrpcError(client, server, false, streaming, clientCompression, GrpcStatusCode.UNIMPLEMENTED, null);
    }

    @ParameterizedTest
    @MethodSource("sslStreamingAndCompressionParams")
    void grpcJavaToGrpcJavaError(final boolean ssl,
                                 final boolean streaming,
                                 final String compression) throws Exception {
        final TestServerContext server = grpcJavaServer(ErrorMode.SIMPLE, ssl, compression);
        final CompatClient client = grpcJavaClient(server.listenAddress(), compression, ssl, null);
        testGrpcError(client, server, false, streaming, compression);
    }

    @ParameterizedTest
    @MethodSource("sslStreamingAndCompressionParams")
    void grpcJavaToGrpcJavaErrorWithStatus(final boolean ssl,
                                           final boolean streaming,
                                           final String compression)
            throws Exception {
        final TestServerContext server = grpcJavaServer(ErrorMode.STATUS, ssl, compression);
        final CompatClient client = grpcJavaClient(server.listenAddress(), compression, ssl, null);
        testGrpcError(client, server, true, streaming, compression);
    }

    @ParameterizedTest
    @MethodSource("sslStreamingAndCompressionParams")
    void serviceTalkToGrpcJavaError(final boolean ssl,
                                    final boolean streaming,
                                    final String compression) throws Exception {
        final TestServerContext server = grpcJavaServer(ErrorMode.SIMPLE, ssl, compression);
        final CompatClient client = serviceTalkClient(server.listenAddress(), ssl, compression, null);
        testGrpcError(client, server, false, streaming, compression);
    }

    @ParameterizedTest
    @MethodSource("sslStreamingAndCompressionParams")
    void serviceTalkToGrpcJavaErrorWithStatus(final boolean ssl,
                                              final boolean streaming,
                                              final String compression)
            throws Exception {
        final TestServerContext server = grpcJavaServer(ErrorMode.STATUS, ssl, compression);
        final CompatClient client = serviceTalkClient(server.listenAddress(), ssl, compression, null);
        testGrpcError(client, server, true, streaming, compression);
    }

    @ParameterizedTest
    @MethodSource("sslStreamingAndCompressionParams")
    void grpcJavaToServiceTalkError(final boolean ssl,
                                    final boolean streaming,
                                    final String compression) throws Exception {
        final TestServerContext server = serviceTalkServer(ErrorMode.SIMPLE, ssl, compression, null);
        final CompatClient client = grpcJavaClient(server.listenAddress(), compression, ssl, null);
        testGrpcError(client, server, false, streaming, compression);
    }

    @ParameterizedTest
    @MethodSource("sslAndCompressionParams")
    void grpcJavaToServiceTalkErrorInScalarResponse(final boolean ssl,
                                                    final String compression)
            throws Exception {
        final TestServerContext server = serviceTalkServer(ErrorMode.SIMPLE_IN_RESPONSE, ssl, compression, null);
        final CompatClient client = grpcJavaClient(server.listenAddress(), compression, ssl, null);
        testGrpcError(client, server, false, false, compression);
    }

    @ParameterizedTest
    @MethodSource("sslAndCompressionParams")
    void grpcJavaToServiceTalkErrorInStreamingResponse(final boolean ssl,
                                                       final String compression)
            throws Exception {
        final TestServerContext server = serviceTalkServer(ErrorMode.SIMPLE_IN_RESPONSE, ssl, compression, null);
        final CompatClient client = grpcJavaClient(server.listenAddress(), compression, ssl, null);
        testStreamResetOnUnexpectedErrorOnServiceTalkServer(client, server);
    }

    @ParameterizedTest
    @MethodSource("sslStreamingAndCompressionParams")
    void grpcJavaToServiceTalkErrorInResponseNoOffload(final boolean ssl,
                                                       final boolean streaming,
                                                       final String compression)
            throws Exception {
        final TestServerContext server = serviceTalkServer(ErrorMode.SIMPLE_IN_RESPONSE, ssl,
                noOffloadsStrategy(), compression, null);
        final CompatClient client = grpcJavaClient(server.listenAddress(), compression, ssl, null);
        testGrpcError(client, server, false, streaming, compression);
    }

    @ParameterizedTest
    @MethodSource("sslStreamingAndCompressionParams")
    void grpcJavaToServiceTalkErrorViaServiceFilter(final boolean ssl,
                                                    final boolean streaming,
                                                    final String compression)
            throws Exception {
        final TestServerContext server = serviceTalkServer(ErrorMode.SIMPLE_IN_SERVICE_FILTER, ssl, compression, null);
        final CompatClient client = grpcJavaClient(server.listenAddress(), compression, ssl, null);
        testGrpcError(client, server, false, streaming, compression);
    }

    @ParameterizedTest
    @MethodSource("sslStreamingAndCompressionParams")
    void grpcJavaToServiceTalkErrorViaServerFilter(final boolean ssl,
                                                   final boolean streaming,
                                                   final String compression)
            throws Exception {
        final TestServerContext server = serviceTalkServer(ErrorMode.SIMPLE_IN_SERVER_FILTER, ssl, compression, null);
        final CompatClient client = grpcJavaClient(server.listenAddress(), compression, ssl, null);
        testGrpcError(client, server, false, streaming, compression);
    }

    @ParameterizedTest
    @MethodSource("sslStreamingAndCompressionParams")
    void grpcJavaToServiceTalkErrorWithStatus(final boolean ssl,
                                              final boolean streaming,
                                              final String compression)
            throws Exception {
        final TestServerContext server = serviceTalkServer(ErrorMode.STATUS, ssl, compression, null);
        final CompatClient client = grpcJavaClient(server.listenAddress(), compression, ssl, null);
        testGrpcError(client, server, true, streaming, compression);
    }

    @ParameterizedTest
    @MethodSource("sslAndCompressionParams")
    void grpcJavaToServiceTalkErrorWithStatusInScalarResponse(
        final boolean ssl, final String compression)
            throws Exception {
        final TestServerContext server = serviceTalkServer(ErrorMode.STATUS_IN_RESPONSE, ssl, compression, null);
        final CompatClient client = grpcJavaClient(server.listenAddress(), compression, ssl, null);
        testGrpcError(client, server, true, false, compression);
    }

    @ParameterizedTest
    @MethodSource("sslAndCompressionParams")
    void grpcJavaToServiceTalkErrorWithStatusInStreamingResponse(
        final boolean ssl, final String compression)
            throws Exception {
        final TestServerContext server = serviceTalkServer(ErrorMode.STATUS_IN_RESPONSE, ssl, compression, null);
        final CompatClient client = grpcJavaClient(server.listenAddress(), compression, ssl, null);
        testStreamResetOnUnexpectedErrorOnServiceTalkServer(client, server);
    }

    @ParameterizedTest
    @MethodSource("sslStreamingAndCompressionParams")
    void grpcJavaToServiceTalkErrorWithStatusInResponseNoOffloads(
        final boolean ssl,
        final boolean streaming,
        final String compression) throws Exception {
        final TestServerContext server = serviceTalkServer(ErrorMode.STATUS_IN_RESPONSE, ssl,
                noOffloadsStrategy(), compression, null);
        final CompatClient client = grpcJavaClient(server.listenAddress(), compression, ssl, null);
        testGrpcError(client, server, true, streaming, compression);
    }

    @ParameterizedTest
    @MethodSource("sslStreamingAndCompressionParams")
    void grpcJavaToServiceTalkErrorWithStatusViaServiceFilter(
        final boolean ssl, final boolean streaming,
        final String compression)
            throws Exception {
        final TestServerContext server = serviceTalkServer(ErrorMode.STATUS_IN_SERVICE_FILTER, ssl, compression, null);
        final CompatClient client = grpcJavaClient(server.listenAddress(), compression, ssl, null);
        testGrpcError(client, server, true, streaming, compression);
    }

    @ParameterizedTest
    @MethodSource("sslStreamingAndCompressionParams")
    void grpcJavaToServiceTalkErrorWithStatusViaServerFilter(
        final boolean ssl, final boolean streaming,
        final String compression)
            throws Exception {
        final TestServerContext server = serviceTalkServer(ErrorMode.STATUS_IN_SERVER_FILTER, ssl, compression, null);
        final CompatClient client = grpcJavaClient(server.listenAddress(), compression, ssl, null);
        testGrpcError(client, server, true, streaming, compression);
    }

    @ParameterizedTest
    @MethodSource("sslStreamingAndCompressionParams")
    void grpcJavaToServiceTalkBlocking(
        final boolean ssl,
        final boolean streaming,
        final String compression) throws Exception {
        final TestServerContext server = serviceTalkServerBlocking(ErrorMode.NONE, ssl, compression);
        final CompatClient client = grpcJavaClient(server.listenAddress(), compression, ssl, null);
        testRequestResponse(client, server, streaming, compression);
    }

    @ParameterizedTest
    @MethodSource("sslStreamingAndCompressionParams")
    void grpcJavaToServiceTalkBlockingError(final boolean ssl,
                                            final boolean streaming,
                                            final String compression)
            throws Exception {
        final TestServerContext server = serviceTalkServerBlocking(ErrorMode.SIMPLE, ssl, compression);
        final CompatClient client = grpcJavaClient(server.listenAddress(), compression, ssl, null);
        testGrpcError(client, server, false, streaming, compression);
    }

    @ParameterizedTest
    @MethodSource("sslStreamingAndCompressionParams")
    void grpcJavaToServiceTalkBlockingErrorWithStatus(final boolean ssl,
                                                      final boolean streaming,
                                                      final String compression)
            throws Exception {
        final TestServerContext server = serviceTalkServerBlocking(ErrorMode.STATUS, ssl, compression);
        final CompatClient client = grpcJavaClient(server.listenAddress(), compression, ssl, null);
        testGrpcError(client, server, true, streaming, compression);
    }

    @ParameterizedTest
    @MethodSource("sslStreamingAndCompressionParams")
    void serviceTalkToServiceTalkError(final boolean ssl,
                                       final boolean streaming,
                                       final String compression)
            throws Exception {
        final TestServerContext server = serviceTalkServer(ErrorMode.SIMPLE, ssl, compression, null);
        final CompatClient client = serviceTalkClient(server.listenAddress(), ssl, compression, null);
        testGrpcError(client, server, false, streaming, compression);
    }

    @ParameterizedTest
    @MethodSource("sslStreamingAndCompressionParams")
    void serviceTalkToServiceTalkErrorViaServiceFilter(final boolean ssl,
                                                       final boolean streaming,
                                                       final String compression)
            throws Exception {
        final TestServerContext server = serviceTalkServer(ErrorMode.SIMPLE_IN_SERVICE_FILTER, ssl, compression, null);
        final CompatClient client = serviceTalkClient(server.listenAddress(), ssl, compression, null);
        testGrpcError(client, server, false, streaming, compression);
    }

    @ParameterizedTest
    @MethodSource("sslStreamingAndCompressionParams")
    void serviceTalkToServiceTalkErrorViaServerFilter(final boolean ssl,
                                                      final boolean streaming,
                                                      final String compression)
            throws Exception {
        final TestServerContext server = serviceTalkServer(ErrorMode.SIMPLE_IN_SERVER_FILTER, ssl, compression, null);
        final CompatClient client = serviceTalkClient(server.listenAddress(), ssl, compression, null);
        testGrpcError(client, server, false, streaming, compression);
    }

    @ParameterizedTest
    @MethodSource("sslStreamingAndCompressionParams")
    void serviceTalkToServiceTalkErrorWithStatus(final boolean ssl,
                                                 final boolean streaming,
                                                 final String compression)
            throws Exception {
        final TestServerContext server = serviceTalkServer(ErrorMode.STATUS, ssl, compression, null);
        final CompatClient client = serviceTalkClient(server.listenAddress(), ssl, compression, null);
        testGrpcError(client, server, true, streaming, compression);
    }

    @ParameterizedTest
    @MethodSource("sslStreamingAndCompressionParams")
    void serviceTalkToServiceTalkErrorWithStatusViaServiceFilter(
        final boolean ssl, final boolean streaming,
        final String compression)
            throws Exception {
        final TestServerContext server = serviceTalkServer(ErrorMode.STATUS_IN_SERVICE_FILTER, ssl, compression, null);
        final CompatClient client = serviceTalkClient(server.listenAddress(), ssl, compression, null);
        testGrpcError(client, server, true, streaming, compression);
    }

    @ParameterizedTest
    @MethodSource("sslStreamingAndCompressionParams")
    void serviceTalkToServiceTalkErrorWithStatusViaServerFilter(
        final boolean ssl, final boolean streaming,
        final String compression)
            throws Exception {
        final TestServerContext server = serviceTalkServer(ErrorMode.STATUS_IN_SERVER_FILTER, ssl, compression, null);
        final CompatClient client = serviceTalkClient(server.listenAddress(), ssl, compression, null);
        testGrpcError(client, server, true, streaming, compression);
    }

    @ParameterizedTest
    @MethodSource("sslStreamingAndCompressionParams")
    @Disabled("https://github.com/apple/servicetalk/issues/1489")
    void grpcJavaToGrpcJavaTimeout(final boolean ssl,
                                   final boolean streaming,
                                   final String compression) throws Exception {
        Duration timeout = Duration.ofNanos(1);
        final TestServerContext server = grpcJavaServer(ErrorMode.NONE, ssl, compression);
        final CompatClient client = grpcJavaClient(server.listenAddress(), compression, ssl, timeout);
        testGrpcError(client, server, false, streaming, compression, GrpcStatusCode.DEADLINE_EXCEEDED, null);
    }

    @ParameterizedTest
    @MethodSource("sslStreamingAndCompressionParams")
    @Disabled("https://github.com/apple/servicetalk/issues/1489")
    void serviceTalkToGrpcJavaTimeout(final boolean ssl,
                                      final boolean streaming,
                                      final String compression) throws Exception {
        Duration timeout = Duration.ofNanos(1);
        final TestServerContext server = grpcJavaServer(ErrorMode.NONE, ssl, compression);
        final CompatClient client = serviceTalkClient(server.listenAddress(), ssl, compression, timeout);
        testGrpcError(client, server, false, streaming, compression, GrpcStatusCode.DEADLINE_EXCEEDED, null);
    }

    @ParameterizedTest
    @MethodSource("sslStreamingAndCompressionParams")
    @Disabled("https://github.com/apple/servicetalk/issues/1489")
    void grpcJavaToServiceTalkTimeout(final boolean ssl,
                                      final boolean streaming,
                                      final String compression) throws Exception {
        Duration timeout = Duration.ofNanos(1);
        final TestServerContext server = serviceTalkServer(ErrorMode.NONE, ssl, compression, null);
        final CompatClient client = grpcJavaClient(server.listenAddress(), compression, ssl, timeout);
        testGrpcError(client, server, false, streaming, compression, GrpcStatusCode.DEADLINE_EXCEEDED, null);
    }

    @ParameterizedTest
    @MethodSource("sslStreamingAndCompressionParams")
    @Disabled("https://github.com/apple/servicetalk/issues/1489")
    void serviceTalkToServiceTalkTimeout(final boolean ssl,
                                         final boolean streaming,
                                         final String compression) throws Exception {
        Duration timeout = Duration.ofNanos(1);
        final TestServerContext server = serviceTalkServer(ErrorMode.NONE, ssl, compression, null);
        final CompatClient client = serviceTalkClient(server.listenAddress(), ssl, compression, timeout);
        testGrpcError(client, server, false, streaming, compression, GrpcStatusCode.DEADLINE_EXCEEDED, null);
    }
    // </editor-fold>

    private static void testBlockingRequestResponse(final BlockingCompatClient client, final TestServerContext server,
                                                    final boolean streaming,
                                                    @Nullable final String compression) throws Exception {
        try {
            final BufferEncoder compressor = serviceTalkCompression(compression);
            final GrpcClientMetadata metadata = compressor == null ? DefaultGrpcClientMetadata.INSTANCE :
                    new DefaultGrpcClientMetadata(compressor);
            if (!streaming) {
                final CompatResponse response1 = client.scalarCall(metadata,
                        CompatRequest.newBuilder().setId(1).build());
                assertEquals(1000001, response1.getSize());
            } else {
                // clientStreamingCall returns the "sum"
                final CompatResponse response2 = client.clientStreamingCall(metadata, asList(
                        CompatRequest.newBuilder().setId(1).build(),
                        CompatRequest.newBuilder().setId(2).build(),
                        CompatRequest.newBuilder().setId(3).build()
                ));
                assertEquals(1000006, response2.getSize());

                // serverStreamingCall returns a stream from 0 to N-1
                final BlockingIterable<CompatResponse> response3 =
                        client.serverStreamingCall(metadata, CompatRequest.newBuilder().setId(3).build());
                final List<CompatResponse> response3List = new ArrayList<>();
                response3.forEach(response3List::add);
                assertEquals(3, response3List.size());
                assertEquals(1000000, response3List.get(0).getSize());
                assertEquals(1000001, response3List.get(1).getSize());
                assertEquals(1000002, response3List.get(2).getSize());

                // bidirectionalStreamingCall basically echos also
                final BlockingIterable<CompatResponse> response4 = client.bidirectionalStreamingCall(metadata,
                        asList(CompatRequest.newBuilder().setId(3).build(),
                                CompatRequest.newBuilder().setId(4).build(),
                                CompatRequest.newBuilder().setId(5).build()
                        ));

                final List<CompatResponse> response4List = new ArrayList<>();
                response4.forEach(response4List::add);
                assertEquals(3, response4List.size());
                assertEquals(1000003, response4List.get(0).getSize());
                assertEquals(1000004, response4List.get(1).getSize());
                assertEquals(1000005, response4List.get(2).getSize());
            }
        } finally {
            closeAll(client, server);
        }
    }

    private static void testRequestResponse(final CompatClient client, final TestServerContext server,
                                            final boolean streaming,
                                            @Nullable final String compression) {
        try {
            final BufferEncoder compressor = serviceTalkCompression(compression);
            final GrpcClientMetadata metadata = compressor == null ? DefaultGrpcClientMetadata.INSTANCE :
                    new DefaultGrpcClientMetadata(compressor);

            if (!streaming) {
                // scalarCall basically echos
                final Single<CompatResponse> response1 =
                        client.scalarCall(metadata, CompatRequest.newBuilder().setId(1).build());
                assertEquals(1000001, response1.toFuture().get().getSize());
            } else {
                // clientStreamingCall returns the "sum"
                final Single<CompatResponse> response2 = client.clientStreamingCall(metadata, Publisher.from(
                        CompatRequest.newBuilder().setId(1).build(),
                        CompatRequest.newBuilder().setId(2).build(),
                        CompatRequest.newBuilder().setId(3).build()
                ));
                CompatResponse r = response2.toFuture().get();
                assertEquals(1000006, r.getSize());

                // serverStreamingCall returns a stream from 0 to N-1
                final Publisher<CompatResponse> response3 =
                        client.serverStreamingCall(metadata, CompatRequest.newBuilder().setId(3).build());
                final List<CompatResponse> response3List = new ArrayList<>(response3.toFuture().get());
                assertEquals(3, response3List.size());
                assertEquals(1000000, response3List.get(0).getSize());
                assertEquals(1000001, response3List.get(1).getSize());
                assertEquals(1000002, response3List.get(2).getSize());

                // bidirectionalStreamingCall basically echos also
                final Publisher<CompatResponse> response4 = client.bidirectionalStreamingCall(metadata,
                        Publisher.from(CompatRequest.newBuilder().setId(3).build(),
                                CompatRequest.newBuilder().setId(4).build(),
                                CompatRequest.newBuilder().setId(5).build()
                        ));

                final List<CompatResponse> response4List = new ArrayList<>(response4.toFuture().get());
                assertEquals(3, response4List.size());
                assertEquals(1000003, response4List.get(0).getSize());
                assertEquals(1000004, response4List.get(1).getSize());
                assertEquals(1000005, response4List.get(2).getSize());
            }
        } catch (Exception ex) {
            ex.printStackTrace();
        } finally {
            closeAll(client, server);
        }
    }

    private static void testStreamResetOnUnexpectedErrorOnServiceTalkServer(final CompatClient client,
                                                                            final TestServerContext server)
            throws Exception {
        try {
            final Publisher<CompatResponse> streamingResponse = client.bidirectionalStreamingCall(Publisher.from(
                    CompatRequest.newBuilder().setId(3).build(),
                    CompatRequest.newBuilder().setId(4).build(),
                    CompatRequest.newBuilder().setId(5).build()
            ));
            streamingResponse.toFuture().get();
        } catch (ExecutionException e) {
            final Throwable cause = e.getCause();
            if (cause instanceof StatusRuntimeException) {
                final StatusRuntimeException sre = (StatusRuntimeException) cause;
                final Status.Code code = sre.getStatus().getCode();
                assertThat(code, is(Status.Code.INVALID_ARGUMENT));
            } else {
                cause.printStackTrace();
                fail("Unexpected exception type: " + cause);
            }
        } finally {
            closeAll(client, server);
        }
    }

    private static void testGrpcError(final CompatClient client, final TestServerContext server,
                                      final boolean withStatus, final boolean streaming, final String compression)
            throws Exception {
        testGrpcError(client, server, withStatus, streaming, compression,
                GrpcStatusCode.INVALID_ARGUMENT, CUSTOM_ERROR_MESSAGE);
    }

    private static void testGrpcError(final CompatClient client, final TestServerContext server,
                                      final boolean withStatus, final boolean streaming, final String compression,
                                      final GrpcStatusCode expectCode,
                                      @Nullable final String expectMessage)
            throws Exception {
        if (streaming) {
            testGrpcErrorStreaming(client, server, withStatus, compression, expectCode, expectMessage);
        } else {
            testGrpcErrorScalar(client, server, withStatus, compression, expectCode, expectMessage);
        }
    }

    private static void testGrpcErrorStreaming(final CompatClient client, final TestServerContext server,
                                               final boolean withStatus, @Nullable final String compression,
                                               final GrpcStatusCode expectCode,
                                               @Nullable final String expectMessage)
            throws Exception {
        try {
            BufferEncoder encoder = serviceTalkCompression(compression);
            GrpcClientMetadata metadata = encoder == null ? DefaultGrpcClientMetadata.INSTANCE :
                    new DefaultGrpcClientMetadata(encoder);

            final Publisher<CompatResponse> streamingResponse = client.bidirectionalStreamingCall(metadata,
                    Publisher.from(CompatRequest.newBuilder().setId(3).build(),
                            CompatRequest.newBuilder().setId(4).build(),
                            CompatRequest.newBuilder().setId(5).build()
                    ));

            validateGrpcErrorInResponse(streamingResponse.toFuture(), withStatus, expectCode, expectMessage);
        } finally {
            closeAll(client, server);
        }
    }

    private static void testGrpcErrorScalar(final CompatClient client, final TestServerContext server,
                                            final boolean withStatus, @Nullable final String compression,
                                            final GrpcStatusCode expectCode,
                                            @Nullable final String expectMessage)
            throws Exception {
        try {
            BufferEncoder encoder = serviceTalkCompression(compression);
            GrpcClientMetadata metadata = encoder == null ? DefaultGrpcClientMetadata.INSTANCE :
                    new DefaultGrpcClientMetadata(encoder);

            final Single<CompatResponse> scalarResponse =
                    client.scalarCall(metadata, CompatRequest.newBuilder().setId(1).build());

            validateGrpcErrorInResponse(scalarResponse.toFuture(), withStatus, expectCode, expectMessage);
        } finally {
            closeAll(client, server);
        }
    }

    private static void validateGrpcErrorInResponse(final Future<?> future, final boolean withStatus,
                                                    final GrpcStatusCode expectCode,
                                                    @Nullable final String expectMessage)
            throws InvalidProtocolBufferException {
        try {
            future.get();
            fail("No error received");
        } catch (final Exception e) {
            final Throwable t = e.getCause();
            if (t instanceof StatusRuntimeException) {
                // underlying client is gRPC
                Status.Code codeExpected = Enum.valueOf(Status.Code.class, expectCode.toString());
                assertStatusRuntimeException((StatusRuntimeException) t, withStatus, codeExpected, expectMessage);
            } else if (t instanceof GrpcStatusException) {
                // underlying client is ServiceTalk
                assertGrpcStatusException((GrpcStatusException) t, withStatus, expectCode, expectMessage);
            } else {
                t.printStackTrace();
                fail("Unexpected exception type: " + t);
            }
        }
    }

    private static void assertGrpcStatusException(final GrpcStatusException statusException,
                                                  final boolean withStatus,
                                                  final GrpcStatusCode expectStatusCode,
                                                  @Nullable final String expectMessage)
            throws InvalidProtocolBufferException {
        final GrpcStatus grpcStatus = statusException.status();
        assertNotNull(grpcStatus);
        assertEquals(expectStatusCode, grpcStatus.code(), "grpcStatus: " + grpcStatus);
        if (null != expectMessage) {
            assertEquals(expectMessage, grpcStatus.description());
        }
        final com.google.rpc.Status status = statusException.applicationStatus();
        if (withStatus) {
            assertNotNull(status);
            assertEquals(grpcStatus.code().value(), status.getCode());
            assertStatus(status, expectStatusCode.value(), grpcStatus.description());
        } else {
            if (null != status) {
                assertFallbackStatus(status, expectStatusCode.value(), grpcStatus.description());
            }
        }
    }

    private static void assertStatusRuntimeException(final StatusRuntimeException statusException,
                                                     final boolean withStatus,
                                                     final Status.Code expectStatusCode,
                                                     @Nullable final String expectMessage)
            throws InvalidProtocolBufferException {
        final Status grpcStatus = statusException.getStatus();
        assertNotNull(grpcStatus);
        assertEquals(expectStatusCode.value(), grpcStatus.getCode().value());
        if (null != expectMessage) {
            assertEquals(expectMessage, grpcStatus.getDescription());
        }
        final com.google.rpc.Status status = StatusProto.fromThrowable(statusException);
        if (withStatus) {
            assertNotNull(status);
            assertEquals(grpcStatus.getCode().value(), status.getCode());
            assertStatus(status, expectStatusCode.value(), grpcStatus.getDescription());
        } else {
            if (null != status) {
                assertFallbackStatus(status, expectStatusCode.value(), grpcStatus.getDescription());
            }
        }
    }

    private static void assertStatus(final com.google.rpc.Status status,
                                     final int expectedCode,
                                     @Nullable final String expectMessage) throws InvalidProtocolBufferException {
        assertEquals(expectedCode, status.getCode());
        assertEquals(expectMessage, status.getMessage());
        final List<Any> anyList = status.getDetailsList();
        assertEquals(1, anyList.size());
        final CompatResponse detail = anyList.get(0).unpack(CompatResponse.class);
        assertEquals(999, detail.getId());
    }

    private static void assertFallbackStatus(final com.google.rpc.Status status, final int expectedCode,
                                             @Nullable final String expectMessage) {
        assertEquals(expectedCode, status.getCode());
        assertEquals(expectMessage, status.getMessage());
        final List<Any> anyList = status.getDetailsList();
        assertEquals(0, anyList.size());
    }

    private static com.google.rpc.Status newStatus() {
        // We just use CompatResponse as part of the status to keep it simple.
        return com.google.rpc.Status.newBuilder()
                .setCode(GrpcStatusCode.INVALID_ARGUMENT.value())
                .setMessage(CUSTOM_ERROR_MESSAGE)
                .addDetails(pack(computeResponse(999)))
                .build();
    }

    private static void closeAll(final AutoCloseable... acs) {
        RuntimeException re = null;

        for (final AutoCloseable ac : acs) {
            try {
                ac.close();
            } catch (final Throwable t) {
                if (re == null) {
                    re = new RuntimeException("Failure(s) when closing: " + Arrays.toString(acs));
                }
                re.addSuppressed(t);
            }
        }

        if (re != null) {
            throw re;
        }
    }

    private static CompatResponse computeResponse(final int value) {
        return CompatResponse.newBuilder()
                .setId(value)
                .setSize(1000000 + value)
                .setName("Response " + value)
                .build();
    }

    private static CompatClient serviceTalkClient(final SocketAddress serverAddress, final boolean ssl,
                                                  @Nullable final String compression,
                                                  @Nullable final Duration timeout) {
        final GrpcClientBuilder<InetSocketAddress, InetSocketAddress> builder =
                GrpcClients.forResolvedAddress((InetSocketAddress) serverAddress);
        if (ssl) {
            builder.sslConfig(new ClientSslConfigBuilder(DefaultTestCerts::loadServerCAPem)
                    .peerHost(serverPemHostname()).build());
        }
        if (null != timeout) {
            builder.defaultTimeout(timeout);
        }
        return builder.build(new Compat.ClientFactory()
                        .bufferDecoderGroup(serviceTalkDecompression(compression)));
    }

    private static GrpcServerBuilder serviceTalkServerBuilder(final ErrorMode errorMode,
                                                              final boolean ssl,
                                                              @Nullable final Duration timeout) {

        final GrpcServerBuilder serverBuilder = GrpcServers.forAddress(localAddress(0))
                .appendHttpServiceFilter(service -> new StreamingHttpServiceFilter(service) {
                    @Override
                    public Single<StreamingHttpResponse> handle(final HttpServiceContext ctx,
                                                                final StreamingHttpRequest req,
                                                                final StreamingHttpResponseFactory resFactory) {
                        if (errorMode == ErrorMode.SIMPLE_IN_SERVER_FILTER) {
                            throwGrpcStatusException();
                        } else if (errorMode == ErrorMode.STATUS_IN_SERVER_FILTER) {
                            throwGrpcStatusExceptionWithStatus();
                        }
                        return delegate().handle(ctx, req, resFactory);
                    }
                });
        if (null != timeout) {
            serverBuilder.defaultTimeout(timeout);
        }
        return ssl ?
                serverBuilder.sslConfig(new ServerSslConfigBuilder(DefaultTestCerts::loadServerPem,
                        DefaultTestCerts::loadServerKey).provider(OPENSSL).build()) :
                serverBuilder;
    }

    private static TestServerContext serviceTalkServerBlocking(final ErrorMode errorMode, final boolean ssl,
                                                               @Nullable final String compression) throws Exception {
        final ServerContext serverContext = serviceTalkServerBuilder(ErrorMode.NONE, ssl, null)
                .listenAndAwait(new ServiceFactory.Builder()
                        .bufferDecoderGroup(serviceTalkDecompression(compression))
                        .bufferEncoders(serviceTalkCompressions(compression))
                        .addService(new BlockingCompatService() {
                            @Override
                            public void bidirectionalStreamingCall(
                                    final GrpcServiceContext ctx, final BlockingIterable<CompatRequest> request,
                                    final GrpcPayloadWriter<CompatResponse> responseWriter) throws Exception {
                                maybeThrowFromRpc(errorMode);
                                for (CompatRequest requestItem : request) {
                                    responseWriter.write(computeResponse(requestItem.getId()));
                                }
                                responseWriter.close();
                            }

                            @Override
                            public CompatResponse clientStreamingCall(final GrpcServiceContext ctx,
                                                                      final BlockingIterable<CompatRequest> request) {
                                maybeThrowFromRpc(errorMode);
                                int sum = 0;
                                for (CompatRequest requestItem : request) {
                                    sum += requestItem.getId();
                                }
                                return computeResponse(sum);
                            }

                            @Override
                            public CompatResponse scalarCall(final GrpcServiceContext ctx,
                                                             final CompatRequest request) {
                                maybeThrowFromRpc(errorMode);
                                return computeResponse(request.getId());
                            }

                            @Override
                            public void serverStreamingCall(final GrpcServiceContext ctx, final CompatRequest request,
                                                            final GrpcPayloadWriter<CompatResponse> responseWriter)
                                    throws Exception {
                                maybeThrowFromRpc(errorMode);
                                for (int i = 0; i < request.getId(); i++) {
                                    responseWriter.write(computeResponse(i));
                                }
                                responseWriter.close();
                            }
                        }).build());
        return TestServerContext.fromServiceTalkServerContext(serverContext);
    }

    @Nullable
    private static BufferEncoder serviceTalkCompression(@Nullable final String compression) {
        if (compression == null) {
            return null;
        }

        if (compression.contentEquals(NettyBufferEncoders.gzipDefault().encodingName())) {
            return NettyBufferEncoders.gzipDefault();
        } else if (compression.contentEquals(Identity.identityEncoder().encodingName())) {
            return Identity.identityEncoder();
        }
        throw new UnsupportedOperationException("Unsupported compression " + compression);
    }

    private static BufferDecoderGroup serviceTalkDecompression(@Nullable final String compression) {
        if (compression == null) {
            return EmptyBufferDecoderGroup.INSTANCE;
        }
        BufferDecoderGroupBuilder builder = new BufferDecoderGroupBuilder(2);
        if (compression.contentEquals(NettyBufferEncoders.gzipDefault().encodingName())) {
            builder.add(NettyBufferEncoders.gzipDefault(), true);
        } else if (compression.contentEquals(Identity.identityEncoder().encodingName())) {
            builder.add(Identity.identityEncoder(), false);
        }
        return builder.build();
    }

    private static List<BufferEncoder> serviceTalkCompressions(@Nullable final String compression) {
        if (compression == null) {
            return Collections.emptyList();
        }
        List<BufferEncoder> encoders = new ArrayList<>(2);
        if (compression.contentEquals(NettyBufferEncoders.gzipDefault().encodingName())) {
            encoders.add(NettyBufferEncoders.gzipDefault());
        } else if (compression.contentEquals(Identity.identityEncoder().encodingName())) {
            encoders.add(Identity.identityEncoder());
        }
        return encoders;
    }

    private static void maybeThrowFromRpc(final ErrorMode errorMode) {
        if (errorMode == ErrorMode.SIMPLE) {
            throwGrpcStatusException();
        } else if (errorMode == ErrorMode.STATUS) {
            throwGrpcStatusExceptionWithStatus();
        }
    }

    private static void throwGrpcStatusException() {
        // INVALID_ARGUMENT is used because it can only be generated by application. ie. not generated by gRPC library
        throw new GrpcStatus(GrpcStatusCode.INVALID_ARGUMENT, null, CUSTOM_ERROR_MESSAGE).asException();
    }

    private static void throwGrpcStatusExceptionWithStatus() {
        throw GrpcStatusException.of(newStatus());
    }

    private static TestServerContext serviceTalkServer(final ErrorMode errorMode, final boolean ssl,
                                                       @Nullable final String compression,
                                                       @Nullable final Duration duration) throws Exception {
        return serviceTalkServer(errorMode, ssl, defaultStrategy(), compression, duration);
    }

    private static TestServerContext serviceTalkServer(final ErrorMode errorMode, final boolean ssl,
                                                       final GrpcExecutionStrategy strategy,
                                                       @Nullable final String compression,
                                                       @Nullable final Duration timeout) throws Exception {
        final Compat.CompatService compatService = new Compat.CompatService() {
            @Override
            public Publisher<CompatResponse> bidirectionalStreamingCall(final GrpcServiceContext ctx,
                                                                        final Publisher<CompatRequest> pub) {
                maybeThrowFromRpc(errorMode);
                return pub.map(req -> response(req.getId()));
            }

            @Override
            public Single<CompatResponse> clientStreamingCall(final GrpcServiceContext ctx,
                                                              final Publisher<CompatRequest> pub) {
                maybeThrowFromRpc(errorMode);
                return pub.collect(() -> 0, (sum, req) -> sum + req.getId()).map(this::response);
            }

            @Override
            public Single<CompatResponse> scalarCall(final GrpcServiceContext ctx, final CompatRequest req) {
                maybeThrowFromRpc(errorMode);
                return succeeded(response(req.getId()));
            }

            @Override
            public Publisher<CompatResponse> serverStreamingCall(final GrpcServiceContext ctx,
                                                                 final CompatRequest req) {
                maybeThrowFromRpc(errorMode);
                return Publisher.fromIterable(() -> IntStream.range(0, req.getId()).iterator()).map(this::response);
            }

            private CompatResponse response(final int value) {
                if (errorMode == ErrorMode.SIMPLE_IN_RESPONSE) {
                    throwGrpcStatusException();
                } else if (errorMode == ErrorMode.STATUS_IN_RESPONSE) {
                    throwGrpcStatusExceptionWithStatus();
                }
                return computeResponse(value);
            }
        };

        final ServiceFactory serviceFactory = new ServiceFactory.Builder()
                .bufferEncoders(serviceTalkCompressions(compression))
                .bufferDecoderGroup(serviceTalkDecompression(compression))
                .bidirectionalStreamingCall(strategy, compatService)
                .clientStreamingCall(strategy, compatService)
                .scalarCall(strategy, compatService)
                .serverStreamingCall(strategy, compatService)
                .build();

        serviceFactory.appendServiceFilter(delegate -> new Compat.CompatServiceFilter(delegate) {
            @Override
            public Publisher<CompatResponse> bidirectionalStreamingCall(final GrpcServiceContext ctx,
                                                                        final Publisher<CompatRequest> req) {
                maybeThrowFromFilter();
                return delegate().bidirectionalStreamingCall(ctx, req);
            }

            @Override
            public Single<CompatResponse> clientStreamingCall(final GrpcServiceContext ctx,
                                                              final Publisher<CompatRequest> req) {
                maybeThrowFromFilter();
                return delegate().clientStreamingCall(ctx, req);
            }

            @Override
            public Publisher<CompatResponse> serverStreamingCall(final GrpcServiceContext ctx,
                                                                 final CompatRequest req) {
                maybeThrowFromFilter();
                return delegate().serverStreamingCall(ctx, req);
            }

            @Override
            public Single<CompatResponse> scalarCall(final GrpcServiceContext ctx, final CompatRequest req) {
                maybeThrowFromFilter();
                return delegate().scalarCall(ctx, req);
            }

            private void maybeThrowFromFilter() {
                if (errorMode == ErrorMode.SIMPLE_IN_SERVICE_FILTER) {
                    throwGrpcStatusException();
                } else if (errorMode == ErrorMode.STATUS_IN_SERVICE_FILTER) {
                    throwGrpcStatusExceptionWithStatus();
                }
            }
        });

        final ServerContext serverContext = serviceTalkServerBuilder(errorMode, ssl, timeout)
                .executionStrategy(strategy)
                .listenAndAwait(serviceFactory);
        return TestServerContext.fromServiceTalkServerContext(serverContext);
    }

    // Wrap grpc client in our client interface to simplify test code
    private static CompatClient grpcJavaClient(final SocketAddress address, @Nullable final String compression,
                                               final boolean ssl,
                                               @Nullable Duration timeout) throws Exception {
        final NettyChannelBuilder builder = NettyChannelBuilder.forAddress(address);

        if (ssl) {
            final SslContext context = GrpcSslContexts.forClient()
                    .trustManager(InsecureTrustManagerFactory.INSTANCE)
                    .build();
            builder.sslContext(context);
        } else {
            builder.usePlaintext();
        }
        final ManagedChannel channel = builder.build();

        // stub is immutable and each builder step returns a new instance.
        CompatGrpc.CompatStub stub = CompatGrpc.newStub(channel);

        if (compression != null) {
            stub = stub.withCompression(compression);
        }

        if (null != timeout) {
            stub = stub.withDeadlineAfter(timeout.toNanos(), TimeUnit.NANOSECONDS);
        }

        final CompatGrpc.CompatStub finalStub = stub;
        return new CompatClient() {
            @Override
            public GrpcExecutionContext executionContext() {
                throw new UnsupportedOperationException();
            }

            @Override
            public Publisher<CompatResponse> bidirectionalStreamingCall(final Publisher<CompatRequest> request) {
                final PublisherSource.Processor<CompatResponse, CompatResponse> processor =
                        newPublisherProcessor(3);
                sendRequest(request, finalStub.bidirectionalStreamingCall(adaptResponse(processor)));
                return fromSource(processor);
            }

            @Override
            public Publisher<CompatResponse> bidirectionalStreamingCall(
                    final GrpcClientMetadata metadata, final Publisher<CompatRequest> request) {
                return bidirectionalStreamingCall(request);
            }

            @Deprecated
            @Override
            public Publisher<CompatResponse> bidirectionalStreamingCall(
                    final BidirectionalStreamingCallMetadata metadata, final Publisher<CompatRequest> request) {
                return bidirectionalStreamingCall(request);
            }

            @SuppressWarnings("unchecked")
            @Override
            public Single<CompatResponse> clientStreamingCall(final Publisher<CompatRequest> request) {
                final Processor<CompatResponse, CompatResponse> processor = newSingleProcessor();
                final StreamObserver<CompatRequest> requestObserver =
                        finalStub.clientStreamingCall(adaptResponse(processor));
                sendRequest(request, requestObserver);
                return (Single<CompatResponse>) processor;
            }

            @Deprecated
            @Override
            public Single<CompatResponse> clientStreamingCall(final ClientStreamingCallMetadata metadata,
                                                              final Publisher<CompatRequest> request) {
                return clientStreamingCall(request);
            }

            @Override
            public Single<CompatResponse> clientStreamingCall(final GrpcClientMetadata metadata,
                                                              final Publisher<CompatRequest> request) {
                return clientStreamingCall(request);
            }

            @SuppressWarnings("unchecked")
            @Override
            public Single<CompatResponse> scalarCall(final CompatRequest request) {
                final Processor<CompatResponse, CompatResponse> processor = newSingleProcessor();
                finalStub.scalarCall(request, adaptResponse(processor));
                return (Single<CompatResponse>) processor;
            }

            @Deprecated
            @Override
            public Single<CompatResponse> scalarCall(final ScalarCallMetadata metadata, final CompatRequest request) {
                return scalarCall(request);
            }

            @Override
            public Single<CompatResponse> scalarCall(final GrpcClientMetadata metadata, final CompatRequest request) {
                return scalarCall(request);
            }

            @Override
            public Publisher<CompatResponse> serverStreamingCall(final CompatRequest request) {
                final PublisherSource.Processor<CompatResponse, CompatResponse> processor =
                        newPublisherProcessor(3);
                finalStub.serverStreamingCall(request, adaptResponse(processor));
                return fromSource(processor);
            }

            @Deprecated
            @Override
            public Publisher<CompatResponse> serverStreamingCall(final ServerStreamingCallMetadata metadata,
                                                                 final CompatRequest request) {
                return serverStreamingCall(request);
            }

            @Override
            public Publisher<CompatResponse> serverStreamingCall(final GrpcClientMetadata metadata,
                                                                 final CompatRequest request) {
                return serverStreamingCall(request);
            }

            @Override
            public void close() throws Exception {
                channel.shutdown().awaitTermination(DEFAULT_TIMEOUT_SECONDS, SECONDS);
            }

            @Override
            public Completable closeAsync() {
                throw new UnsupportedOperationException();
            }

            @Override
            public Completable onClose() {
                throw new UnsupportedOperationException();
            }

            @Override
            public BlockingCompatClient asBlockingClient() {
                throw new UnsupportedOperationException();
            }

            private void sendRequest(final Publisher<CompatRequest> request,
                                     final StreamObserver<CompatRequest> requestObserver) {
                request.whenOnComplete(requestObserver::onCompleted)
                        .whenOnError(requestObserver::onError)
                        .forEach(requestObserver::onNext);
            }

            private StreamObserver<CompatResponse> adaptResponse(
                    final Processor<CompatResponse, CompatResponse> processor) {
                return new StreamObserver<CompatResponse>() {
                    @Override
                    public void onNext(final CompatResponse value) {
                        processor.onSuccess(value);
                    }

                    @Override
                    public void onError(final Throwable t) {
                        processor.onError(t);
                    }

                    @Override
                    public void onCompleted() {
                        // ignored
                    }
                };
            }

            private StreamObserver<CompatResponse> adaptResponse(
                    final PublisherSource.Processor<CompatResponse, CompatResponse> processor) {
                return new StreamObserver<CompatResponse>() {
                    @Override
                    public void onNext(final CompatResponse value) {
                        processor.onNext(value);
                    }

                    @Override
                    public void onError(final Throwable t) {
                        processor.onError(t);
                    }

                    @Override
                    public void onCompleted() {
                        processor.onComplete();
                    }
                };
            }
        };
    }

    private static TestServerContext grpcJavaServer(final ErrorMode errorMode, final boolean ssl,
                                                    @Nullable final String compression) throws Exception {
        final NettyServerBuilder builder = NettyServerBuilder.forAddress(localAddress(0));
        if (ssl) {
            builder.useTransportSecurity(loadServerPem(), loadServerKey());
        }
        if (compression != null) {
            DecompressorRegistry dRegistry = DecompressorRegistry.emptyInstance();
            CompressorRegistry cRegistry = CompressorRegistry.newEmptyInstance();
            Compressor gz = new Codec.Gzip();
            Compressor id = Codec.Identity.NONE;

            if (compression.equals(gz.getMessageEncoding())) {
                dRegistry = dRegistry.with((Decompressor) gz, true);
                cRegistry.register(gz);
            }

            // Always include identity otherwise it's not available
            dRegistry = dRegistry.with((Decompressor) id, false);
            cRegistry.register(id);

            builder.decompressorRegistry(dRegistry);
            builder.compressorRegistry(cRegistry);
        } else {
            builder.decompressorRegistry(DecompressorRegistry.emptyInstance());
            builder.compressorRegistry(CompressorRegistry.newEmptyInstance());
        }

        final Server server = builder
                .addService(new CompatGrpc.CompatImplBase() {
                    @Override
                    public void scalarCall(final CompatRequest request,
                                           final StreamObserver<CompatResponse> responseObserver) {
                        try {
                            responseObserver.onNext(response(request.getId()));
                            responseObserver.onCompleted();
                        } catch (final Throwable t) {
                            responseObserver.onError(t);
                        }
                    }

                    @Override
                    public StreamObserver<CompatRequest> clientStreamingCall(
                            final StreamObserver<CompatResponse> responseObserver) {
                        return new StreamObserver<CompatRequest>() {
                            int sum;

                            @Override
                            public void onNext(final CompatRequest value) {
                                sum += value.getId();
                            }

                            @Override
                            public void onError(final Throwable t) {
                                responseObserver.onError(t);
                            }

                            @Override
                            public void onCompleted() {
                                try {
                                    responseObserver.onNext(response(sum));
                                    responseObserver.onCompleted();
                                } catch (final Throwable t) {
                                    responseObserver.onError(t);
                                }
                            }
                        };
                    }

                    @Override
                    public void serverStreamingCall(final CompatRequest request,
                                                    final StreamObserver<CompatResponse> responseObserver) {
                        for (int i = 0; i < request.getId(); ++i) {
                            try {
                                responseObserver.onNext(response(i));
                            } catch (final Throwable t) {
                                responseObserver.onError(t);
                                return;
                            }
                        }
                        responseObserver.onCompleted();
                    }

                    @Override
                    public StreamObserver<CompatRequest> bidirectionalStreamingCall(
                            final StreamObserver<CompatResponse> responseObserver) {
                        return new StreamObserver<CompatRequest>() {
                            private boolean errored;

                            @Override
                            public void onNext(final CompatRequest demoRequest) {
                                try {
                                    responseObserver.onNext(response(demoRequest.getId()));
                                } catch (final Throwable t) {
                                    onError(t);
                                }
                            }

                            @Override
                            public void onError(final Throwable t) {
                                if (errored) {
                                    return;
                                }
                                errored = true;
                                responseObserver.onError(t);
                            }

                            @Override
                            public void onCompleted() {
                                if (errored) {
                                    return;
                                }
                                responseObserver.onCompleted();
                            }
                        };
                    }

                    private CompatResponse response(final int value) throws Exception {
                        if (errorMode == ErrorMode.SIMPLE) {
                            throw Status.INVALID_ARGUMENT.augmentDescription(CUSTOM_ERROR_MESSAGE).asException();
                        }
                        if (errorMode == ErrorMode.STATUS) {
                            throw StatusProto.toStatusException(newStatus());
                        }
                        return computeResponse(value);
                    }
                })
                .build().start();

        return TestServerContext.fromGrpcJavaServer(server);
    }
}

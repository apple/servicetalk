/*
 * Copyright © 2019 Apple Inc. and the ServiceTalk project authors
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

import io.servicetalk.concurrent.SingleSource;
import io.servicetalk.concurrent.api.Completable;
import io.servicetalk.concurrent.api.Publisher;
import io.servicetalk.concurrent.api.Single;
import io.servicetalk.concurrent.api.internal.SpScPublisherProcessor;
import io.servicetalk.concurrent.internal.ServiceTalkTestTimeout;
import io.servicetalk.grpc.api.GrpcClientBuilder;
import io.servicetalk.grpc.api.GrpcServerBuilder;
import io.servicetalk.grpc.api.GrpcServiceContext;
import io.servicetalk.grpc.api.GrpcStatus;
import io.servicetalk.grpc.api.GrpcStatusCode;
import io.servicetalk.grpc.api.GrpcStatusException;
import io.servicetalk.grpc.netty.CompatProto.Compat;
import io.servicetalk.grpc.netty.CompatProto.Compat.BidirectionalStreamingCallMetadata;
import io.servicetalk.grpc.netty.CompatProto.Compat.BlockingCompatClient;
import io.servicetalk.grpc.netty.CompatProto.Compat.ClientStreamingCallMetadata;
import io.servicetalk.grpc.netty.CompatProto.Compat.CompatClient;
import io.servicetalk.grpc.netty.CompatProto.Compat.ScalarCallMetadata;
import io.servicetalk.grpc.netty.CompatProto.Compat.ServerStreamingCallMetadata;
import io.servicetalk.grpc.netty.CompatProto.RequestContainer.CompatRequest;
import io.servicetalk.grpc.netty.CompatProto.ResponseContainer.CompatResponse;
import io.servicetalk.http.api.HttpServiceContext;
import io.servicetalk.http.api.StreamingHttpRequest;
import io.servicetalk.http.api.StreamingHttpResponse;
import io.servicetalk.http.api.StreamingHttpResponseFactory;
import io.servicetalk.http.api.StreamingHttpServiceFilter;
import io.servicetalk.test.resources.DefaultTestCerts;
import io.servicetalk.transport.api.ServerContext;

import com.google.protobuf.Any;
import com.google.protobuf.InvalidProtocolBufferException;
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
import org.junit.Ignore;
import org.junit.Rule;
import org.junit.experimental.theories.DataPoints;
import org.junit.experimental.theories.FromDataPoints;
import org.junit.experimental.theories.Theories;
import org.junit.experimental.theories.Theory;
import org.junit.rules.Timeout;
import org.junit.runner.RunWith;

import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.stream.IntStream;
import javax.annotation.Nullable;

import static com.google.protobuf.Any.pack;
import static io.servicetalk.concurrent.api.Processors.newSingleProcessor;
import static io.servicetalk.concurrent.api.Single.succeeded;
import static io.servicetalk.concurrent.internal.ServiceTalkTestTimeout.DEFAULT_TIMEOUT_SECONDS;
import static io.servicetalk.test.resources.DefaultTestCerts.loadServerKey;
import static io.servicetalk.test.resources.DefaultTestCerts.loadServerPem;
import static io.servicetalk.transport.api.SecurityConfigurator.ApplicationProtocolNegotiation.ALPN;
import static io.servicetalk.transport.api.SecurityConfigurator.SelectedListenerFailureBehavior.ACCEPT;
import static io.servicetalk.transport.api.SecurityConfigurator.SelectorFailureBehavior.NO_ADVERTISE;
import static io.servicetalk.transport.api.SecurityConfigurator.SslProvider.OPENSSL;
import static java.util.Collections.singleton;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.fail;

@RunWith(Theories.class)
public class ProtocolCompatibilityTest {
    private interface TestServerContext extends AutoCloseable {
        SocketAddress listenAddress();

        static TestServerContext fromServiceTalkServerContext(final ServerContext serverContext) {
            return new TestServerContext() {
                @Override
                public void close() throws Exception {
                    serverContext.close();
                }

                @Override
                public SocketAddress listenAddress() {
                    return serverContext.listenAddress();
                }
            };
        }

        static TestServerContext fromGrpcServer(final Server server) {
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
    private static final Collection<String> ALPN_SUPPORTED_PROTOCOLS = singleton("h2");

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

    @DataPoints("ssl")
    public static boolean[] ssl = new boolean[]{false, true};

    @Rule
    public final Timeout timeout = new ServiceTalkTestTimeout();

    @Theory
    public void grpcToGrpc(@FromDataPoints("ssl") final boolean ssl) throws Exception {
        final TestServerContext server = grpcServer(ErrorMode.NONE, ssl);
        final CompatClient client = grpcClient(server.listenAddress(), null, ssl);
        testRequestResponse(client, server);
    }

    @Theory
    public void serviceTalkToGrpc(@FromDataPoints("ssl") final boolean ssl) throws Exception {
        final TestServerContext server = grpcServer(ErrorMode.NONE, ssl);
        final CompatClient client = serviceTalkClient(server.listenAddress(), ssl);
        testRequestResponse(client, server);
    }

    @Theory
    public void grpcToServiceTalk(@FromDataPoints("ssl") final boolean ssl) throws Exception {
        final TestServerContext server = serviceTalkServer(ErrorMode.NONE, ssl);
        final CompatClient client = grpcClient(server.listenAddress(), null, ssl);
        testRequestResponse(client, server);
    }

    @Ignore("gRPC compression not supported yet")
    @Theory
    public void grpcToServiceTalkCompressedGzip(@FromDataPoints("ssl") final boolean ssl) throws Exception {
        final TestServerContext server = serviceTalkServer(ErrorMode.NONE, ssl);
        // Only gzip is supported by GRPC out of the box atm.
        final CompatClient client = grpcClient(server.listenAddress(), "gzip", ssl);
        testRequestResponse(client, server);
    }

    @Theory
    public void serviceTalkToServiceTalk(@FromDataPoints("ssl") final boolean ssl) throws Exception {
        final TestServerContext server = serviceTalkServer(ErrorMode.NONE, ssl);
        final CompatClient client = serviceTalkClient(server.listenAddress(), ssl);
        testRequestResponse(client, server);
    }

    @Theory
    public void serviceTalkToServiceTalkBlocking(@FromDataPoints("ssl") final boolean ssl) throws Exception {
        final TestServerContext server = serviceTalkServerBlocking(ssl);
        final BlockingCompatClient client = serviceTalkClient(server.listenAddress(), ssl).asBlockingClient();
        testRequestResponse(client, server);
    }

    @Theory
    public void grpcToGrpcError(@FromDataPoints("ssl") final boolean ssl) throws Exception {
        final TestServerContext server = grpcServer(ErrorMode.SIMPLE, ssl);
        final CompatClient client = grpcClient(server.listenAddress(), null, ssl);
        testError(client, server, false);
    }

    @Theory
    public void grpcToGrpcErrorWithStatus(@FromDataPoints("ssl") final boolean ssl) throws Exception {
        final TestServerContext server = grpcServer(ErrorMode.STATUS, ssl);
        final CompatClient client = grpcClient(server.listenAddress(), null, ssl);
        testError(client, server, true);
    }

    @Ignore("Trailers-Only responses are not currently handled")
    @Theory
    public void serviceTalkToGrpcError(@FromDataPoints("ssl") final boolean ssl) throws Exception {
        final TestServerContext server = grpcServer(ErrorMode.SIMPLE, ssl);
        final CompatClient client = serviceTalkClient(server.listenAddress(), ssl);
        testError(client, server, false);
    }

    @Ignore("Trailers-Only responses are not currently handled")
    @Theory
    public void serviceTalkToGrpcErrorWithStatus(@FromDataPoints("ssl") final boolean ssl) throws Exception {
        final TestServerContext server = grpcServer(ErrorMode.STATUS, ssl);
        final CompatClient client = serviceTalkClient(server.listenAddress(), ssl);
        testError(client, server, true);
    }

    @Theory
    public void grpcToServiceTalkError(@FromDataPoints("ssl") final boolean ssl) throws Exception {
        final TestServerContext server = serviceTalkServer(ErrorMode.SIMPLE, ssl);
        final CompatClient client = grpcClient(server.listenAddress(), null, ssl);
        testError(client, server, false);
    }

    @Ignore("GrpcStatusException thrown late (e.g. in a map operator) are not currently handled")
    @Theory
    public void grpcToServiceTalkErrorInResponse(@FromDataPoints("ssl") final boolean ssl) throws Exception {
        final TestServerContext server = serviceTalkServer(ErrorMode.SIMPLE_IN_RESPONSE, ssl);
        final CompatClient client = grpcClient(server.listenAddress(), null, ssl);
        testError(client, server, false);
    }

    @Theory
    public void grpcToServiceTalkErrorViaServiceFilter(@FromDataPoints("ssl") final boolean ssl) throws Exception {
        final TestServerContext server = serviceTalkServer(ErrorMode.SIMPLE_IN_SERVICE_FILTER, ssl);
        final CompatClient client = grpcClient(server.listenAddress(), null, ssl);
        testError(client, server, false);
    }

    @Ignore("GrpcStatusException thrown in server filters are not currently handled")
    @Theory
    public void grpcToServiceTalkErrorViaServerFilter(@FromDataPoints("ssl") final boolean ssl) throws Exception {
        final TestServerContext server = serviceTalkServer(ErrorMode.SIMPLE_IN_SERVER_FILTER, ssl);
        final CompatClient client = grpcClient(server.listenAddress(), null, ssl);
        testError(client, server, false);
    }

    @Theory
    public void grpcToServiceTalkErrorWithStatus(@FromDataPoints("ssl") final boolean ssl) throws Exception {
        final TestServerContext server = serviceTalkServer(ErrorMode.STATUS, ssl);
        final CompatClient client = grpcClient(server.listenAddress(), null, ssl);
        testError(client, server, true);
    }

    @Ignore("GrpcStatusException thrown late (e.g. in a map operator) are not currently handled")
    @Theory
    public void grpcToServiceTalkErrorWithStatusInResponse(@FromDataPoints("ssl") final boolean ssl) throws Exception {
        final TestServerContext server = serviceTalkServer(ErrorMode.STATUS_IN_RESPONSE, ssl);
        final CompatClient client = grpcClient(server.listenAddress(), null, ssl);
        testError(client, server, true);
    }

    @Theory
    public void grpcToServiceTalkErrorWithStatusViaServiceFilter(@FromDataPoints("ssl") final boolean ssl)
            throws Exception {
        final TestServerContext server = serviceTalkServer(ErrorMode.STATUS_IN_SERVICE_FILTER, ssl);
        final CompatClient client = grpcClient(server.listenAddress(), null, ssl);
        testError(client, server, true);
    }

    @Ignore("GrpcStatusException thrown in server filters are not currently handled")
    @Theory
    public void grpcToServiceTalkErrorWithStatusViaServerFilter(@FromDataPoints("ssl") final boolean ssl)
            throws Exception {
        final TestServerContext server = serviceTalkServer(ErrorMode.STATUS_IN_SERVER_FILTER, ssl);
        final CompatClient client = grpcClient(server.listenAddress(), null, ssl);
        testError(client, server, true);
    }

    @Theory
    public void serviceTalkToServiceTalkError(@FromDataPoints("ssl") final boolean ssl) throws Exception {
        final TestServerContext server = serviceTalkServer(ErrorMode.SIMPLE, ssl);
        final CompatClient client = serviceTalkClient(server.listenAddress(), ssl);
        testError(client, server, false);
    }

    @Theory
    public void serviceTalkToServiceTalkErrorViaServiceFilter(@FromDataPoints("ssl") final boolean ssl)
            throws Exception {
        final TestServerContext server = serviceTalkServer(ErrorMode.SIMPLE_IN_SERVICE_FILTER, ssl);
        final CompatClient client = serviceTalkClient(server.listenAddress(), ssl);
        testError(client, server, false);
    }

    @Ignore("GrpcStatusException thrown in server filters are not currently handled")
    @Theory
    public void serviceTalkToServiceTalkErrorViaServerFilter(@FromDataPoints("ssl") final boolean ssl)
            throws Exception {
        final TestServerContext server = serviceTalkServer(ErrorMode.SIMPLE_IN_SERVER_FILTER, ssl);
        final CompatClient client = serviceTalkClient(server.listenAddress(), ssl);
        testError(client, server, false);
    }

    @Theory
    public void serviceTalkToServiceTalkErrorWithStatus(@FromDataPoints("ssl") final boolean ssl) throws Exception {
        final TestServerContext server = serviceTalkServer(ErrorMode.STATUS, ssl);
        final CompatClient client = serviceTalkClient(server.listenAddress(), ssl);
        testError(client, server, true);
    }

    @Theory
    public void serviceTalkToServiceTalkErrorWithStatusViaServiceFilter(@FromDataPoints("ssl") final boolean ssl)
            throws Exception {
        final TestServerContext server = serviceTalkServer(ErrorMode.STATUS_IN_SERVICE_FILTER, ssl);
        final CompatClient client = serviceTalkClient(server.listenAddress(), ssl);
        testError(client, server, true);
    }

    @Ignore("GrpcStatusException thrown in server filters are not currently handled")
    @Theory
    public void serviceTalkToServiceTalkErrorWithStatusViaServerFilter(@FromDataPoints("ssl") final boolean ssl)
            throws Exception {
        final TestServerContext server = serviceTalkServer(ErrorMode.STATUS_IN_SERVER_FILTER, ssl);
        final CompatClient client = serviceTalkClient(server.listenAddress(), ssl);
        testError(client, server, true);
    }

    private static void testRequestResponse(final BlockingCompatClient client, final TestServerContext server)
            throws Exception {
        try {
            final CompatResponse response1 = client.scalarCall(CompatRequest.newBuilder().setId(1).build());
            assertEquals(response1.getSize(), 1000001);
        } finally {
            closeAll(client, server);
        }
    }

    private static void testRequestResponse(final CompatClient client, final TestServerContext server)
            throws Exception {
        try {
            // scalarCall basically echos
            final Single<CompatResponse> response1 = client.scalarCall(CompatRequest.newBuilder().setId(1).build());
            assertEquals(response1.toFuture().get().getSize(), 1000001);

            // clientStreamingCall returns the "sum"
            final Single<CompatResponse> response2 = client.clientStreamingCall(Publisher.from(
                    CompatRequest.newBuilder().setId(1).build(),
                    CompatRequest.newBuilder().setId(2).build(),
                    CompatRequest.newBuilder().setId(3).build()
            ));
            assertEquals(response2.toFuture().get().getSize(), 1000006);

            // serverStreamingCall returns a stream from 0 to N-1
            final Publisher<CompatResponse> response3 =
                    client.serverStreamingCall(CompatRequest.newBuilder().setId(3).build());
            final List<CompatResponse> response3List = new ArrayList<>(response3.toFuture().get());
            assertEquals(3, response3List.size());
            assertEquals(response3List.get(0).getSize(), 1000000);
            assertEquals(response3List.get(1).getSize(), 1000001);
            assertEquals(response3List.get(2).getSize(), 1000002);

            // bidirectionalStreamingCall basically echos also
            final Publisher<CompatResponse> response4 = client.bidirectionalStreamingCall(Publisher.from(
                    CompatRequest.newBuilder().setId(3).build(),
                    CompatRequest.newBuilder().setId(4).build(),
                    CompatRequest.newBuilder().setId(5).build()
            ));
            final List<CompatResponse> response4List = new ArrayList<>(response4.toFuture().get());
            assertEquals(3, response4List.size());
            assertEquals(response4List.get(0).getSize(), 1000003);
            assertEquals(response4List.get(1).getSize(), 1000004);
            assertEquals(response4List.get(2).getSize(), 1000005);
        } finally {
            closeAll(client, server);
        }
    }

    private static void testError(final CompatClient client, final TestServerContext server, final boolean withStatus)
            throws Exception {
        try {
            final Single<CompatResponse> scalarResponse =
                    client.scalarCall(CompatRequest.newBuilder().setId(1).build());

            try {
                scalarResponse.toFuture().get();
                fail("No error received");
            } catch (final Exception e) {
                final Throwable t = e.getCause();
                if (t instanceof StatusRuntimeException) {
                    // underlying client is gRPC
                    assertStatusRuntimeException((StatusRuntimeException) t, withStatus);
                } else if (t instanceof GrpcStatusException) {
                    // underlying client is ServiceTalk
                    assertGrpcStatusException((GrpcStatusException) t, withStatus);
                } else {
                    t.printStackTrace();
                    fail("Unexpected exception type: " + t);
                }
            }

            final Publisher<CompatResponse> streamingResponse = client.bidirectionalStreamingCall(Publisher.from(
                    CompatRequest.newBuilder().setId(3).build(),
                    CompatRequest.newBuilder().setId(4).build(),
                    CompatRequest.newBuilder().setId(5).build()
            ));
            try {
                streamingResponse.toFuture().get();
                fail("No error received");
            } catch (final Exception e) {
                final Throwable t = e.getCause();
                if (t instanceof StatusRuntimeException) {
                    // underlying client is gRPC
                    assertStatusRuntimeException((StatusRuntimeException) t, withStatus);
                } else if (t instanceof GrpcStatusException) {
                    // underlying client is ServiceTalk
                    assertGrpcStatusException((GrpcStatusException) t, withStatus);
                } else {
                    t.printStackTrace();
                    fail("Unexpected exception type: " + t);
                }
            }
        } finally {
            closeAll(client, server);
        }
    }

    private static void assertGrpcStatusException(final GrpcStatusException statusException, final boolean withStatus)
            throws InvalidProtocolBufferException {
        final GrpcStatus grpcStatus = statusException.status();
        assertEquals(CUSTOM_ERROR_MESSAGE, grpcStatus.description());
        final com.google.rpc.Status status = statusException.applicationStatus();
        if (withStatus) {
            assertNotNull(status);
            assertStatus(status, grpcStatus.code().value(), grpcStatus.description());
        } else {
            assertNull(status);
        }
    }

    private static void assertStatusRuntimeException(final StatusRuntimeException statusException,
                                                     final boolean withStatus)
            throws InvalidProtocolBufferException {
        final Status grpcStatus = statusException.getStatus();
        assertEquals(CUSTOM_ERROR_MESSAGE, grpcStatus.getDescription());
        final com.google.rpc.Status status = StatusProto.fromThrowable(statusException);
        if (withStatus) {
            assertNotNull(status);
            assertStatus(status, grpcStatus.getCode().value(), grpcStatus.getDescription());
        } else {
            assertNull(status);
        }
    }

    private static void assertStatus(final com.google.rpc.Status status,
                                     final int expectedCode,
                                     @Nullable final String expectedMessage) throws InvalidProtocolBufferException {
        assertEquals(expectedCode, status.getCode());
        assertEquals(expectedMessage, status.getMessage());
        final List<Any> anyList = status.getDetailsList();
        assertEquals(1, anyList.size());
        final CompatResponse detail = anyList.get(0).unpack(CompatResponse.class);
        assertEquals(999, detail.getId());
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
                    re = new RuntimeException("Failure(s) when to closing: " + Arrays.toString(acs));
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

    private static CompatClient serviceTalkClient(final SocketAddress serverAddress, final boolean ssl) {
        final GrpcClientBuilder<InetSocketAddress, InetSocketAddress> builder =
                GrpcClients.forResolvedAddress((InetSocketAddress) serverAddress).h2PriorKnowledge(true);
        if (ssl) {
            builder.secure().provider(OPENSSL)
                    .applicationProtocolNegotiation(ALPN, NO_ADVERTISE, ACCEPT, ALPN_SUPPORTED_PROTOCOLS)
                    .trustManager(DefaultTestCerts::loadServerPem).commit();
        }
        return builder.build(new Compat.ClientFactory());
    }

    private static GrpcServerBuilder serviceTalkServerBuilder(final ErrorMode errorMode, final boolean ssl) {
        final GrpcServerBuilder serverBuilder = GrpcServers.forPort(0).h2PriorKnowledge(true)
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
        return ssl ?
                serverBuilder.secure().provider(OPENSSL)
                        .applicationProtocolNegotiation(ALPN, NO_ADVERTISE, ACCEPT, ALPN_SUPPORTED_PROTOCOLS)
                        .commit(DefaultTestCerts::loadServerPem, DefaultTestCerts::loadServerKey) :
                serverBuilder;
    }

    private static TestServerContext serviceTalkServerBlocking(final boolean ssl) throws Exception {
        final ServerContext serverContext = serviceTalkServerBuilder(ErrorMode.NONE, ssl)
                .listenAndAwait(new Compat.ServiceFactory.Builder()
                        .scalarCallBlocking((ctx, request) -> computeResponse(request.getId()))
                        .build());

        return TestServerContext.fromServiceTalkServerContext(serverContext);
    }

    private static void throwGrpcStatusException() {
        throw new GrpcStatus(GrpcStatusCode.INVALID_ARGUMENT, null, CUSTOM_ERROR_MESSAGE).asException();
    }

    private static void throwGrpcStatusExceptionWithStatus() {
        throw GrpcStatusException.of(newStatus());
    }

    private static TestServerContext serviceTalkServer(final ErrorMode errorMode, final boolean ssl) throws Exception {
        final Compat.ServiceFactory.Builder serviceFactoryBuilder = new Compat.ServiceFactory.Builder();

        serviceFactoryBuilder.registerRoutes(new Compat.CompatService() {
            @Override
            public Publisher<CompatResponse> bidirectionalStreamingCall(final GrpcServiceContext ctx,
                                                                        final Publisher<CompatRequest> pub) {
                maybeThrowFromRpc();
                return pub.map(req -> response(req.getId()));
            }

            @Override
            public Single<CompatResponse> clientStreamingCall(final GrpcServiceContext ctx,
                                                              final Publisher<CompatRequest> pub) {
                maybeThrowFromRpc();
                return pub.collect(() -> 0, (sum, req) -> sum + req.getId()).map(this::response);
            }

            @Override
            public Single<CompatResponse> scalarCall(final GrpcServiceContext ctx, final CompatRequest req) {
                maybeThrowFromRpc();
                return succeeded(response(req.getId()));
            }

            @Override
            public Publisher<CompatResponse> serverStreamingCall(final GrpcServiceContext ctx,
                                                                 final CompatRequest req) {
                maybeThrowFromRpc();
                return Publisher.fromIterable(() -> IntStream.range(0, req.getId()).iterator()).map(this::response);
            }

            private void maybeThrowFromRpc() {
                if (errorMode == ErrorMode.SIMPLE) {
                    throwGrpcStatusException();
                } else if (errorMode == ErrorMode.STATUS) {
                    throwGrpcStatusExceptionWithStatus();
                }
            }

            private CompatResponse response(final int value) {
                if (errorMode == ErrorMode.SIMPLE_IN_RESPONSE) {
                    throwGrpcStatusException();
                } else if (errorMode == ErrorMode.STATUS_IN_RESPONSE) {
                    throwGrpcStatusExceptionWithStatus();
                }
                return computeResponse(value);
            }
        });

        final Compat.ServiceFactory serviceFactory = serviceFactoryBuilder.build()
                .appendServiceFilter(delegate -> new Compat.CompatServiceFilter(delegate) {
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

        final ServerContext serverContext = serviceTalkServerBuilder(errorMode, ssl).listenAndAwait(serviceFactory);
        return TestServerContext.fromServiceTalkServerContext(serverContext);
    }

    // Wrap grpc client in our client interface to simplify test code
    private static CompatClient grpcClient(final SocketAddress address, @Nullable final String compression,
                                           final boolean ssl) throws Exception {
        final NettyChannelBuilder builder = NettyChannelBuilder.forAddress(address);
        final ManagedChannel channel;
        if (ssl) {
            final SslContext context = GrpcSslContexts.forClient()
                    .trustManager(InsecureTrustManagerFactory.INSTANCE)
                    .build();
            channel = builder.sslContext(context).build();
        } else {
            channel = builder.usePlaintext().build();
        }

        final CompatGrpc.CompatStub stub = compression == null ?
                CompatGrpc.newStub(channel) : CompatGrpc.newStub(channel).withCompression(compression);

        return new CompatClient() {
            @Override
            public Publisher<CompatResponse> bidirectionalStreamingCall(final Publisher<CompatRequest> request) {
                final SpScPublisherProcessor<CompatResponse> processor = new SpScPublisherProcessor<>(3);
                sendRequest(request, stub.bidirectionalStreamingCall(adaptResponse(processor)));
                return processor;
            }

            @Override
            public Publisher<CompatResponse> bidirectionalStreamingCall(
                    final BidirectionalStreamingCallMetadata metadata, final Publisher<CompatRequest> request) {
                throw new UnsupportedOperationException();
            }

            @SuppressWarnings("unchecked")
            @Override
            public Single<CompatResponse> clientStreamingCall(final Publisher<CompatRequest> request) {
                final SingleSource.Processor<CompatResponse, CompatResponse> processor = newSingleProcessor();
                final StreamObserver<CompatRequest> requestObserver =
                        stub.clientStreamingCall(adaptResponse(processor));
                sendRequest(request, requestObserver);
                return (Single<CompatResponse>) processor;
            }

            @Override
            public Single<CompatResponse> clientStreamingCall(final ClientStreamingCallMetadata metadata,
                                                              final Publisher<CompatRequest> request) {
                throw new UnsupportedOperationException();
            }

            @SuppressWarnings("unchecked")
            @Override
            public Single<CompatResponse> scalarCall(final CompatRequest request) {
                final SingleSource.Processor<CompatResponse, CompatResponse> processor = newSingleProcessor();
                stub.scalarCall(request, adaptResponse(processor));
                return (Single<CompatResponse>) processor;
            }

            @Override
            public Single<CompatResponse> scalarCall(final ScalarCallMetadata metadata, final CompatRequest request) {
                throw new UnsupportedOperationException();
            }

            @Override
            public Publisher<CompatResponse> serverStreamingCall(final CompatRequest request) {
                final SpScPublisherProcessor<CompatResponse> processor = new SpScPublisherProcessor<>(3);
                stub.serverStreamingCall(request, adaptResponse(processor));
                return processor;
            }

            @Override
            public Publisher<CompatResponse> serverStreamingCall(final ServerStreamingCallMetadata metadata,
                                                                 final CompatRequest request) {
                throw new UnsupportedOperationException();
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
                    final SingleSource.Processor<CompatResponse, CompatResponse> processor) {
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
                    final SpScPublisherProcessor<CompatResponse> processor) {
                return new StreamObserver<CompatResponse>() {
                    @Override
                    public void onNext(final CompatResponse value) {
                        processor.sendOnNext(value);
                    }

                    @Override
                    public void onError(final Throwable t) {
                        processor.sendOnError(t);
                    }

                    @Override
                    public void onCompleted() {
                        processor.sendOnComplete();
                    }
                };
            }
        };
    }

    private static TestServerContext grpcServer(final ErrorMode errorMode, final boolean ssl) throws Exception {
        final NettyServerBuilder builder = NettyServerBuilder.forPort(0);
        if (ssl) {
            builder.useTransportSecurity(loadServerPem(), loadServerKey());
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
                        } else if (errorMode == ErrorMode.STATUS) {
                            throw StatusProto.toStatusException(newStatus());
                        }
                        return computeResponse(value);
                    }
                })
                .build().start();

        return TestServerContext.fromGrpcServer(server);
    }
}

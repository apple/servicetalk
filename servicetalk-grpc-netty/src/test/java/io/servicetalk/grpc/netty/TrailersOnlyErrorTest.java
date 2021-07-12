/*
 * Copyright © 2021 Apple Inc. and the ServiceTalk project authors
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
import io.servicetalk.grpc.api.GrpcClientBuilder;
import io.servicetalk.grpc.api.GrpcServerBuilder;
import io.servicetalk.grpc.api.GrpcStatusCode;
import io.servicetalk.grpc.api.GrpcStatusException;
import io.servicetalk.grpc.netty.TesterProto.TestRequest;
import io.servicetalk.grpc.netty.TesterProto.Tester;
import io.servicetalk.grpc.netty.TesterProto.Tester.TesterClient;
import io.servicetalk.grpc.netty.TesterProto.Tester.TesterService;
import io.servicetalk.http.api.FilterableStreamingHttpClient;
import io.servicetalk.http.api.HttpExecutionStrategy;
import io.servicetalk.http.api.HttpResponseMetaData;
import io.servicetalk.http.api.HttpServiceContext;
import io.servicetalk.http.api.StreamingHttpClientFilter;
import io.servicetalk.http.api.StreamingHttpClientFilterFactory;
import io.servicetalk.http.api.StreamingHttpRequest;
import io.servicetalk.http.api.StreamingHttpRequester;
import io.servicetalk.http.api.StreamingHttpResponse;
import io.servicetalk.http.api.StreamingHttpResponseFactory;
import io.servicetalk.http.api.StreamingHttpServiceFilter;
import io.servicetalk.transport.api.HostAndPort;
import io.servicetalk.transport.api.ServerContext;

import io.grpc.examples.helloworld.Greeter;
import io.grpc.examples.helloworld.Greeter.GreeterClient;
import io.grpc.examples.helloworld.HelloRequest;
import org.junit.jupiter.api.Test;

import java.net.InetSocketAddress;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.LinkedBlockingDeque;

import static io.servicetalk.buffer.api.CharSequences.newAsciiString;
import static io.servicetalk.concurrent.api.Completable.completed;
import static io.servicetalk.concurrent.api.Publisher.from;
import static io.servicetalk.concurrent.api.Publisher.never;
import static io.servicetalk.concurrent.internal.DeliberateException.DELIBERATE_EXCEPTION;
import static io.servicetalk.grpc.api.GrpcStatusCode.UNIMPLEMENTED;
import static io.servicetalk.grpc.api.GrpcStatusCode.UNKNOWN;
import static io.servicetalk.test.resources.TestUtils.assertNoAsyncErrors;
import static io.servicetalk.transport.netty.internal.AddressUtils.localAddress;
import static io.servicetalk.transport.netty.internal.AddressUtils.serverHostAndPort;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.notNullValue;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class TrailersOnlyErrorTest {

    private static final CharSequence GRPC_STATUS_HEADER = newAsciiString("grpc-status");

    @Test
    void testRouteThrows() throws Exception {
        final BlockingQueue<Throwable> asyncErrors = new LinkedBlockingDeque<>();
        try (ServerContext serverContext = GrpcServers.forAddress(localAddress(0))
                .listenAndAwait(new Tester.ServiceFactory(mockTesterService()))) {

            final GrpcClientBuilder<HostAndPort, InetSocketAddress> clientBuilder =
                    GrpcClients.forAddress(serverHostAndPort(serverContext))
                            .appendHttpClientFilter(__ -> true, setupResponseVerifierFilter(asyncErrors));

            // The server only binds on Tester service, but the client sends a HelloRequest (Greeter service),
            // thus no route is found and it should result in UNIMPLEMENTED.
            try (GreeterClient client = clientBuilder.build(new Greeter.ClientFactory())) {
                verifyException(client.sayHello(HelloRequest.newBuilder().build()).toFuture(), UNIMPLEMENTED);
                assertNoAsyncErrors(asyncErrors);
            }
        }
    }

    @Test
    void testServiceThrows() throws Exception {
        final BlockingQueue<Throwable> asyncErrors = new LinkedBlockingDeque<>();
        final TesterService service = mockTesterService();
        setupServiceThrows(service);

        try (ServerContext serverContext = GrpcServers.forAddress(localAddress(0))
                .listenAndAwait(new Tester.ServiceFactory(service))) {

            final GrpcClientBuilder<HostAndPort, InetSocketAddress> clientBuilder =
                    GrpcClients.forAddress(serverHostAndPort(serverContext))
                            .appendHttpClientFilter(__ -> true, setupResponseVerifierFilter(asyncErrors));

            try (TesterClient client = clientBuilder.build(new Tester.ClientFactory())) {
                verifyException(client.test(TestRequest.newBuilder()
                        .build()).toFuture(), UNKNOWN);
                assertNoAsyncErrors(asyncErrors);

                verifyException(client.testRequestStream(Publisher.from(TestRequest.newBuilder()
                        .build())).toFuture(), UNKNOWN);
                assertNoAsyncErrors(asyncErrors);

                verifyException(client.testBiDiStream(from(TestRequest.newBuilder()
                        .build()).concat(never())).toFuture(), UNKNOWN);
                assertNoAsyncErrors(asyncErrors);

                verifyException(client.testBiDiStream(from(TestRequest.newBuilder()
                        .build())).toFuture(), UNKNOWN);
                assertNoAsyncErrors(asyncErrors);
            }
        }
    }

    @Test
    void testServiceSingleThrows() throws Exception {
        final BlockingQueue<Throwable> asyncErrors = new LinkedBlockingDeque<>();
        final TesterService service = mockTesterService();
        setupServiceSingleThrows(service);

        try (ServerContext serverContext = GrpcServers.forAddress(localAddress(0))
                .listenAndAwait(new Tester.ServiceFactory(service))) {

            final GrpcClientBuilder<HostAndPort, InetSocketAddress> clientBuilder =
                    GrpcClients.forAddress(serverHostAndPort(serverContext))
                            .appendHttpClientFilter(__ -> true, setupResponseVerifierFilter(asyncErrors));

            try (TesterClient client = clientBuilder.build(new Tester.ClientFactory())) {
                verifyException(client.test(TestRequest.newBuilder().build()).toFuture(), UNKNOWN);
                assertNoAsyncErrors(asyncErrors);
            }
        }
    }

    @Test
    void testServiceFilterThrows() throws Exception {
        final BlockingQueue<Throwable> asyncErrors = new LinkedBlockingDeque<>();
        final TesterService service = mockTesterService();

        final GrpcServerBuilder serverBuilder = GrpcServers.forAddress(localAddress(0))
                .appendHttpServiceFilter(svc -> new StreamingHttpServiceFilter(svc) {
                    @Override
                    public Single<StreamingHttpResponse> handle(
                            final HttpServiceContext ctx, final StreamingHttpRequest request,
                            final StreamingHttpResponseFactory responseFactory) {
                        throw DELIBERATE_EXCEPTION;
                    }
                });

        try (ServerContext serverContext = serverBuilder.listenAndAwait(new Tester.ServiceFactory(service))) {

            final GrpcClientBuilder<HostAndPort, InetSocketAddress> clientBuilder =
                    GrpcClients.forAddress(serverHostAndPort(serverContext))
                            .appendHttpClientFilter(__ -> true, setupResponseVerifierFilter(asyncErrors));

            try (TesterClient client = clientBuilder.build(new Tester.ClientFactory())) {
                verifyException(client.test(TestRequest.newBuilder()
                        .build()).toFuture(), UNKNOWN);
                assertNoAsyncErrors(asyncErrors);

                verifyException(client.testRequestStream(Publisher.from(TestRequest.newBuilder().build())).toFuture(),
                        UNKNOWN);
                assertNoAsyncErrors(asyncErrors);

                verifyException(client.testResponseStream(TestRequest.newBuilder().build()).toFuture(), UNKNOWN);
                assertNoAsyncErrors(asyncErrors);

                verifyException(client.testBiDiStream(from(TestRequest.newBuilder().build())
                        .concat(never())).toFuture(), UNKNOWN);
                assertNoAsyncErrors(asyncErrors);

                verifyException(client.testBiDiStream(from(TestRequest.newBuilder()
                        .build())).toFuture(), UNKNOWN);
                assertNoAsyncErrors(asyncErrors);
            }
        }
    }

    private static void verifyException(final Future<?> result, final GrpcStatusCode expectedCode) {
        verifyException(assertThrows(ExecutionException.class, result::get).getCause(), expectedCode);
    }

    private static void verifyException(final Throwable cause, final GrpcStatusCode expectedCode) {
        assertNotNull(cause);
        assertThat(assertThrows(GrpcStatusException.class, () -> {
            throw cause;
        }).status().code(), equalTo(expectedCode));
    }

    private static TesterService mockTesterService() {
        TesterService filter = mock(TesterService.class);
        when(filter.closeAsync()).thenReturn(completed());
        when(filter.closeAsyncGracefully()).thenReturn(completed());
        return filter;
    }

    private static void setupServiceThrows(final TesterService service) {
        when(service.test(any(), any())).thenThrow(DELIBERATE_EXCEPTION);
        when(service.testBiDiStream(any(), any())).thenThrow(DELIBERATE_EXCEPTION);
        when(service.testRequestStream(any(), any())).thenThrow(DELIBERATE_EXCEPTION);
        when(service.testResponseStream(any(), any())).thenThrow(DELIBERATE_EXCEPTION);
    }

    private static void setupServiceSingleThrows(final TesterService service) {
        when(service.test(any(), any())).thenReturn(Single.failed(DELIBERATE_EXCEPTION));
    }

    private static StreamingHttpClientFilterFactory setupResponseVerifierFilter(final BlockingQueue<Throwable> errors) {
        return new StreamingHttpClientFilterFactory() {
            @Override
            public StreamingHttpClientFilter create(final FilterableStreamingHttpClient client) {
                return new StreamingHttpClientFilter(client) {
                    @Override
                    protected Single<StreamingHttpResponse> request(final StreamingHttpRequester delegate,
                                                                    final HttpExecutionStrategy strategy,
                                                                    final StreamingHttpRequest request) {
                        return super.request(delegate, strategy, request)
                                .map(response -> {
                                    assertGrpcStatusInHeaders(response, errors);
                                    return response;
                                });
                    }
                };
            }
        };
    }

    private static void assertGrpcStatusInHeaders(final HttpResponseMetaData metaData,
                                                  final BlockingQueue<Throwable> errors) {
        try {
            assertThat("GRPC_STATUS not present in headers.", metaData.headers().get(GRPC_STATUS_HEADER),
                    notNullValue());
        } catch (Throwable t) {
            errors.add(t);
        }
    }
}

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
import io.servicetalk.grpc.api.GrpcStatusCode;
import io.servicetalk.grpc.api.GrpcStatusException;
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
import io.grpc.examples.helloworld.HelloRequest;
import org.junit.Test;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.io.UnsupportedEncodingException;
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
import static io.servicetalk.transport.netty.internal.AddressUtils.localAddress;
import static io.servicetalk.transport.netty.internal.AddressUtils.serverHostAndPort;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.notNullValue;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class TrailersOnlyErrorTest {

    private static final CharSequence GRPC_STATUS_HEADER = newAsciiString("grpc-status");

    @Test
    public void testRouteThrows() throws Exception {
        final BlockingQueue<Throwable> asyncErrors = new LinkedBlockingDeque<>();
        final ServerContext serverContext = GrpcServers.forAddress(localAddress(0))
                .listenAndAwait(new TesterProto.Tester.ServiceFactory(mockTesterService()));

        final GrpcClientBuilder<HostAndPort, InetSocketAddress> clientBuilder =
                GrpcClients.forAddress(serverHostAndPort(serverContext))
                .appendHttpClientFilter(__ -> true, setupResponseVerifierFilter(asyncErrors));

        Greeter.GreeterClient client = clientBuilder.build(new Greeter.ClientFactory());
        assertNoErrors(asyncErrors);
        verifyException(client.sayHello(HelloRequest.newBuilder().build()).toFuture(), UNIMPLEMENTED);
    }

    @Test
    public void testServiceThrows() throws Exception {
        final BlockingQueue<Throwable> asyncErrors = new LinkedBlockingDeque<>();
        final TesterProto.Tester.TesterService service = mockTesterService();
        setupServiceThrows(service);

        final ServerContext serverContext = GrpcServers.forAddress(localAddress(0))
                .listenAndAwait(new TesterProto.Tester.ServiceFactory(service));

        final GrpcClientBuilder<HostAndPort, InetSocketAddress> clientBuilder =
                GrpcClients.forAddress(serverHostAndPort(serverContext))
                        .appendHttpClientFilter(__ -> true, setupResponseVerifierFilter(asyncErrors));

        TesterProto.Tester.TesterClient client = clientBuilder.build(new TesterProto.Tester.ClientFactory());
        verifyException(client.test(TesterProto.TestRequest.newBuilder()
                .build()).toFuture(), UNKNOWN);
        assertNoErrors(asyncErrors);

        verifyException(client.testRequestStream(Publisher.from(TesterProto.TestRequest.newBuilder()
                .build())).toFuture(), UNKNOWN);
        assertNoErrors(asyncErrors);

        verifyException(client.testBiDiStream(from(TesterProto.TestRequest.newBuilder()
                .build()).concat(never())).toFuture(), UNKNOWN);
        assertNoErrors(asyncErrors);

        verifyException(client.testBiDiStream(from(TesterProto.TestRequest.newBuilder()
                .build())).toFuture(), UNKNOWN);
        assertNoErrors(asyncErrors);
    }

    @Test
    public void testServiceSingleThrows() throws Exception {
        final BlockingQueue<Throwable> asyncErrors = new LinkedBlockingDeque<>();
        final TesterProto.Tester.TesterService service = mockTesterService();
        setupServiceSingleThrows(service);

        final ServerContext serverContext = GrpcServers.forAddress(localAddress(0))
                .listenAndAwait(new TesterProto.Tester.ServiceFactory(service));

        final GrpcClientBuilder<HostAndPort, InetSocketAddress> clientBuilder =
                GrpcClients.forAddress(serverHostAndPort(serverContext))
                        .appendHttpClientFilter(__ -> true, setupResponseVerifierFilter(asyncErrors));

        TesterProto.Tester.TesterClient client = clientBuilder.build(new TesterProto.Tester.ClientFactory());
        verifyException(client.test(TesterProto.TestRequest.newBuilder()
                .build()).toFuture(), UNKNOWN);
        assertNoErrors(asyncErrors);
    }

    @Test
    public void testServiceFilterThrows() throws Exception {
        final BlockingQueue<Throwable> asyncErrors = new LinkedBlockingDeque<>();
        final TesterProto.Tester.TesterService service = mockTesterService();

        final ServerContext serverContext = GrpcServers.forAddress(localAddress(0))
                .appendHttpServiceFilter(svc -> new StreamingHttpServiceFilter(svc) {
                    @Override
                    public Single<StreamingHttpResponse> handle(
                            final HttpServiceContext ctx, final StreamingHttpRequest request,
                            final StreamingHttpResponseFactory responseFactory) {
                        throw DELIBERATE_EXCEPTION;
                    }
                })
                .listenAndAwait(new TesterProto.Tester.ServiceFactory(service));

        final GrpcClientBuilder<HostAndPort, InetSocketAddress> clientBuilder =
                GrpcClients.forAddress(serverHostAndPort(serverContext))
                        .appendHttpClientFilter(__ -> true, setupResponseVerifierFilter(asyncErrors));

        TesterProto.Tester.TesterClient client = clientBuilder.build(new TesterProto.Tester.ClientFactory());
        verifyException(client.test(TesterProto.TestRequest.newBuilder()
                .build()).toFuture(), UNKNOWN);
        assertNoErrors(asyncErrors);

        verifyException(client.testRequestStream(Publisher.from(TesterProto.TestRequest.newBuilder()
                .build())).toFuture(), UNKNOWN);
        assertNoErrors(asyncErrors);

        verifyException(client.testResponseStream(TesterProto.TestRequest.newBuilder().build()).toFuture(), UNKNOWN);
        assertNoErrors(asyncErrors);

        verifyException(client.testBiDiStream(from(TesterProto.TestRequest.newBuilder()
                .build()).concat(never())).toFuture(), UNKNOWN);
        assertNoErrors(asyncErrors);

        verifyException(client.testBiDiStream(from(TesterProto.TestRequest.newBuilder()
                .build())).toFuture(), UNKNOWN);
        assertNoErrors(asyncErrors);
    }

    private static void assertNoErrors(final BlockingQueue<Throwable> errors) {
        final ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (PrintStream ps = new PrintStream(baos, true, UTF_8.name())) {
            Throwable t;
            while ((t = errors.poll()) != null) {
                t.printStackTrace(ps);
                ps.println();
            }
            String data = new String(baos.toByteArray(), 0, baos.size(), UTF_8);
            if (!data.isEmpty()) {
                throw new AssertionError(data);
            }
        } catch (UnsupportedEncodingException e) {
            throw new RuntimeException(e);
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

    private static TesterProto.Tester.TesterService mockTesterService() {
        TesterProto.Tester.TesterService filter = mock(TesterProto.Tester.TesterService.class);
        when(filter.closeAsync()).thenReturn(completed());
        when(filter.closeAsyncGracefully()).thenReturn(completed());
        return filter;
    }

    private static void setupServiceThrows(final TesterProto.Tester.TesterService service) {
        when(service.test(any(), any())).thenThrow(DELIBERATE_EXCEPTION);
        when(service.testBiDiStream(any(), any())).thenThrow(DELIBERATE_EXCEPTION);
        when(service.testRequestStream(any(), any())).thenThrow(DELIBERATE_EXCEPTION);
        when(service.testResponseStream(any(), any())).thenThrow(DELIBERATE_EXCEPTION);
    }

    private static void setupServiceSingleThrows(final TesterProto.Tester.TesterService service) {
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

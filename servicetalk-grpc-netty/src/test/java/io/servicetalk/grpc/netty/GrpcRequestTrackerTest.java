/*
 * Copyright © 2024 Apple Inc. and the ServiceTalk project authors
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

import io.servicetalk.client.api.DelegatingConnectionFactory;
import io.servicetalk.concurrent.api.Publisher;
import io.servicetalk.concurrent.api.Single;
import io.servicetalk.context.api.ContextMap;
import io.servicetalk.grpc.api.GrpcClientBuilder;
import io.servicetalk.grpc.api.GrpcServiceContext;
import io.servicetalk.grpc.api.GrpcStatus;
import io.servicetalk.grpc.api.GrpcStatusCode;
import io.servicetalk.grpc.api.GrpcStatusException;
import io.servicetalk.http.api.FilterableStreamingHttpConnection;
import io.servicetalk.loadbalancer.ErrorClass;
import io.servicetalk.loadbalancer.RequestTracker;
import io.servicetalk.transport.api.HostAndPort;
import io.servicetalk.transport.api.ServerContext;
import io.servicetalk.transport.api.TransportObserver;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.net.InetSocketAddress;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;
import javax.annotation.Nullable;

import static io.servicetalk.loadbalancer.RequestTracker.REQUEST_TRACKER_KEY;
import static io.servicetalk.transport.netty.internal.AddressUtils.localAddress;
import static io.servicetalk.transport.netty.internal.AddressUtils.serverHostAndPort;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.notNullValue;
import static org.junit.jupiter.api.Assertions.assertThrows;

class GrpcRequestTrackerTest {

    @Nullable
    private ServerContext serverContext;
    @Nullable
    private TesterProto.Tester.TesterClient client;
    @Nullable
    TestRequestTracker testRequestTracker;

    @AfterEach
    void teardown() throws Exception {
        if (serverContext != null) {
            serverContext.close();
        }
        if (client != null) {
            client.close();
        }
    }

    void setup(TesterProto.Tester.TesterService service) throws Exception {
        serverContext = GrpcServers.forAddress(localAddress(0))
                .listenAndAwait(new TesterProto.Tester.ServiceFactory(service));
        GrpcClientBuilder<HostAndPort, InetSocketAddress> clientBuilder =
                GrpcClients.forAddress(serverHostAndPort(serverContext))
                        .initializeHttp(builder -> builder
                                .appendConnectionFactoryFilter(connectionFactory ->
                            new DelegatingConnectionFactory<InetSocketAddress,
                                    FilterableStreamingHttpConnection>(connectionFactory) {

                                @Override
                                public Single<FilterableStreamingHttpConnection> newConnection(
                                        InetSocketAddress address, @Nullable ContextMap context,
                                        @Nullable TransportObserver observer) {
                                    assert context != null;
                                    RequestTracker requestTracker = context.get(REQUEST_TRACKER_KEY);
                                    assert requestTracker == null;
                                    testRequestTracker = new TestRequestTracker();
                                    context.put(REQUEST_TRACKER_KEY, testRequestTracker);

                                    return super.newConnection(address, context, observer);
                                }
                            }));
        client = clientBuilder.build(new TesterProto.Tester.ClientFactory());
    }

    @Test
    void classifiesErrors() throws Exception {
        setup(new TesterProto.Tester.TesterService() {
            @Override
            public Publisher<TesterProto.TestResponse> testBiDiStream(GrpcServiceContext ctx,
                                                                      Publisher<TesterProto.TestRequest> request) {
                throw new UnsupportedOperationException("not implemented");
            }

            @Override
            public Publisher<TesterProto.TestResponse> testResponseStream(GrpcServiceContext ctx,
                                                                          TesterProto.TestRequest request) {
                throw new UnsupportedOperationException("not implemented");
            }

            @Override
            public Single<TesterProto.TestResponse> testRequestStream(GrpcServiceContext ctx,
                                                                      Publisher<TesterProto.TestRequest> request) {
                throw new UnsupportedOperationException("not implemented");
            }

            @Override
            public Single<TesterProto.TestResponse> test(GrpcServiceContext ctx, TesterProto.TestRequest request) {
                switch (request.getName()) {
                    case "sad":
                        return Single.failed(new Exception("big boom"));
                    case "slow":
                        return Single.never();
                    case "cancel":
                        throw new GrpcStatusException(GrpcStatus.fromCodeValue(GrpcStatusCode.CANCELLED.value()));
                    default:
                        return Single.succeeded(TesterProto.TestResponse.newBuilder()
                                .setMessage(request.getName()).build());
                }
            }
        });

        ExecutionException ex = assertThrows(ExecutionException.class, () -> client.test(
                TesterProto.TestRequest.newBuilder().setName("sad").build()).toFuture().get());
        GrpcStatusException grpcStatusException = (GrpcStatusException) ex.getCause();
        assertThat(testRequestTracker, notNullValue());
        assertThat(grpcStatusException.status().code(), equalTo(GrpcStatusCode.UNKNOWN));
        assertThat(testRequestTracker.successes.get(), equalTo(0));
        assertThat(testRequestTracker.failures.get(), equalTo(1));
        assertThat(testRequestTracker.cancellations.get(), equalTo(0));

        // cancel from server side
        ex = assertThrows(ExecutionException.class, () -> client.test(
                TesterProto.TestRequest.newBuilder().setName("cancel").build()).toFuture().get());
        grpcStatusException = (GrpcStatusException) ex.getCause();
        assertThat(grpcStatusException.status().code(), equalTo(GrpcStatusCode.CANCELLED));
        assertThat(testRequestTracker.successes.get(), equalTo(0));
        assertThat(testRequestTracker.failures.get(), equalTo(1));
        assertThat(testRequestTracker.cancellations.get(), equalTo(1));

        // cancel from client side
        Future<TesterProto.TestResponse> fResponse = client.test(
                TesterProto.TestRequest.newBuilder().setName("slow").build()).toFuture();
        fResponse.cancel(true);
        assertThrows(CancellationException.class, () -> fResponse.get());
        assertThat(testRequestTracker.successes.get(), equalTo(0));
        assertThat(testRequestTracker.failures.get(), equalTo(1));
        assertThat(testRequestTracker.cancellations.get(), equalTo(2));

        // now test a success
        TesterProto.TestResponse response = client.test(
                TesterProto.TestRequest.newBuilder().setName("success").build()).toFuture().get();
        assertThat(response.getMessage(), equalTo("success"));
        assertThat(testRequestTracker.successes.get(), equalTo(1));
        assertThat(testRequestTracker.failures.get(), equalTo(1));
        assertThat(testRequestTracker.cancellations.get(), equalTo(2));
    }

    private static final class TestRequestTracker implements RequestTracker {

        final AtomicInteger successes = new AtomicInteger();
        final AtomicInteger failures = new AtomicInteger();
        final AtomicInteger cancellations = new AtomicInteger();

        @Override
        public long beforeRequestStart() {
            return 0;
        }

        @Override
        public void onRequestSuccess(long beforeStartTimeNs) {
            successes.incrementAndGet();
        }

        @Override
        public void onRequestError(long beforeStartTimeNs, ErrorClass errorClass) {
            if (errorClass == ErrorClass.CANCELLED) {
                cancellations.incrementAndGet();
            } else {
                failures.incrementAndGet();
            }
        }
    }
}

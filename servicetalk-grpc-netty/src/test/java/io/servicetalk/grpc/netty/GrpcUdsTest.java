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

import io.servicetalk.client.api.TransportObserverConnectionFactoryFilter;
import io.servicetalk.concurrent.api.Completable;
import io.servicetalk.concurrent.api.Single;
import io.servicetalk.grpc.api.GrpcServerContext;
import io.servicetalk.grpc.api.GrpcServiceContext;
import io.servicetalk.http.api.HttpConnectionContext;
import io.servicetalk.http.api.StreamingHttpConnectionFilter;
import io.servicetalk.http.api.StreamingHttpRequest;
import io.servicetalk.http.api.StreamingHttpResponse;
import io.servicetalk.transport.api.ConnectionContext;
import io.servicetalk.transport.api.ConnectionInfo;
import io.servicetalk.transport.api.ConnectionObserver;
import io.servicetalk.transport.api.DelegatingConnectionAcceptor;
import io.servicetalk.transport.api.DomainSocketAddress;
import io.servicetalk.transport.api.TransportObserver;
import io.servicetalk.transport.netty.internal.GlobalExecutionContext;

import io.grpc.examples.helloworld.Greeter.BlockingGreeterClient;
import io.grpc.examples.helloworld.Greeter.BlockingGreeterService;
import io.grpc.examples.helloworld.Greeter.ClientFactory;
import io.grpc.examples.helloworld.HelloReply;
import io.grpc.examples.helloworld.HelloRequest;
import org.junit.jupiter.api.Test;

import java.util.Queue;
import java.util.concurrent.LinkedBlockingQueue;
import javax.annotation.Nullable;

import static io.servicetalk.grpc.netty.GrpcClients.forResolvedAddress;
import static io.servicetalk.grpc.netty.GrpcServers.forAddress;
import static io.servicetalk.transport.netty.internal.AddressUtils.newSocketAddress;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.emptyString;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.instanceOf;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

class GrpcUdsTest {

    @Test
    void udsRoundTrip() throws Exception {
        assumeTrue(GlobalExecutionContext.globalExecutionContext().ioExecutor().isUnixDomainSocketSupported(),
                "Unix Domain Socket is not supported in this environment");

        Queue<Throwable> errors = new LinkedBlockingQueue<>();

        String greetingPrefix = "Hello ";
        String name = "foo";
        DomainSocketAddress domainSocketAddress = newSocketAddress();
        try (GrpcServerContext serverContext = forAddress(domainSocketAddress)
                .initializeHttp(builder -> builder
                        .appendEarlyConnectionAcceptor(ctx -> {
                            assertSameAddress(ctx.localAddress(), domainSocketAddress, errors);
                            assertSameAddressType(ctx.remoteAddress(), domainSocketAddress, errors);
                            return Completable.completed();
                        })
                        .appendLateConnectionAcceptor(ctx -> {
                            assertSameAddress(ctx.localAddress(), domainSocketAddress, errors);
                            assertSameAddressType(ctx.remoteAddress(), domainSocketAddress, errors);
                            return Completable.completed();
                        })
                        .appendConnectionAcceptorFilter(original -> new DelegatingConnectionAcceptor(original) {
                            @Override
                            public Completable accept(ConnectionContext ctx) {
                                assertSameAddress(ctx.localAddress(), domainSocketAddress, errors);
                                assertSameAddressType(ctx.remoteAddress(), domainSocketAddress, errors);
                                return Completable.completed();
                            }
                        })
                        .transportObserver(new AssertingTransportObserver(false, domainSocketAddress, errors)))
                .listenAndAwait(new BlockingGreeterService() {
                    @Override
                    public HelloReply sayHello(GrpcServiceContext ctx, HelloRequest request) {
                        assertSameAddress(ctx.localAddress(), domainSocketAddress, errors);
                        assertSameAddressType(ctx.remoteAddress(), domainSocketAddress, errors);
                        return HelloReply.newBuilder().setMessage(greetingPrefix + request.getName()).build();
                    }
                })) {

            assertSameAddress(serverContext.listenAddress(), domainSocketAddress, errors);
            try (BlockingGreeterClient client = forResolvedAddress(domainSocketAddress)
                    .initializeHttp(builder -> builder
                            .appendConnectionFactoryFilter(new TransportObserverConnectionFactoryFilter<>(
                                    new AssertingTransportObserver(true, domainSocketAddress, errors)))
                            .appendConnectionFilter(connection -> new StreamingHttpConnectionFilter(connection) {
                                @Override
                                public Single<StreamingHttpResponse> request(final StreamingHttpRequest request) {
                                    HttpConnectionContext ctx = connectionContext();
                                    assertSameAddressType(ctx.localAddress(), domainSocketAddress, errors);
                                    assertSameAddress(ctx.remoteAddress(), domainSocketAddress, errors);
                                    return delegate().request(request);
                                }
                            })).buildBlocking(new ClientFactory())) {

                HelloRequest request = HelloRequest.newBuilder().setName(name).build();
                HelloReply response = client.sayHello(request);
                assertThat(response.getMessage(), is(equalTo(greetingPrefix + name)));
            }
        }
    }

    private static void assertSameAddress(Object actual, DomainSocketAddress expected, Queue<Throwable> errors) {
        try {
            assertThat(actual, is(instanceOf(expected.getClass())));
            assertThat(((DomainSocketAddress) actual).path(), is(equalTo(expected.path())));
        } catch (Throwable t) {
            errors.add(t);
        }
    }

    private static void assertSameAddressType(Object actual, DomainSocketAddress expected, Queue<Throwable> errors) {
        try {
            assertThat(actual, is(instanceOf(expected.getClass())));
            assertThat(((DomainSocketAddress) actual).path(), is(emptyString()));
        } catch (Throwable t) {
            errors.add(t);
        }
    }

    private static final class AssertingTransportObserver implements TransportObserver, ConnectionObserver {
        private final boolean isClient;
        private final DomainSocketAddress domainSocketAddress;
        private final Queue<Throwable> errors;

        AssertingTransportObserver(boolean isClient, DomainSocketAddress domainSocketAddress,
                                   Queue<Throwable> errors) {
            this.isClient = isClient;
            this.domainSocketAddress = domainSocketAddress;
            this.errors = errors;
        }

        @Override
        public ConnectionObserver onNewConnection(@Nullable Object localAddress, Object remoteAddress) {
            try {
                if (isClient) {
                    assertThat(localAddress, is(nullValue()));
                    assertSameAddress(remoteAddress, domainSocketAddress, errors);
                } else {
                    assertThat(localAddress, is(notNullValue()));
                    assertSameAddress(localAddress, domainSocketAddress, errors);
                    assertSameAddressType(remoteAddress, domainSocketAddress, errors);
                }
            } catch (Throwable t) {
                errors.add(t);
            }
            return this;
        }

        @Override
        public void onTransportHandshakeComplete(ConnectionInfo info) {
            assertInfo(info);
        }

        @Override
        public DataObserver connectionEstablished(ConnectionInfo info) {
            assertInfo(info);
            return ConnectionObserver.super.connectionEstablished(info);
        }

        private void assertInfo(ConnectionInfo info) {
            if (isClient) {
                assertSameAddressType(info.localAddress(), domainSocketAddress, errors);
                assertSameAddress(info.remoteAddress(), domainSocketAddress, errors);
            } else {
                assertSameAddress(info.localAddress(), domainSocketAddress, errors);
                assertSameAddressType(info.remoteAddress(), domainSocketAddress, errors);
            }
        }
    }
}

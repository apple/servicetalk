/*
 * Copyright © 2022 Apple Inc. and the ServiceTalk project authors
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

import io.servicetalk.concurrent.api.Single;
import io.servicetalk.grpc.api.GrpcStatusException;
import io.servicetalk.http.api.FilterableStreamingHttpClient;
import io.servicetalk.http.api.HttpExecutionStrategy;
import io.servicetalk.http.api.HttpServiceContext;
import io.servicetalk.http.api.StreamingHttpClientFilter;
import io.servicetalk.http.api.StreamingHttpClientFilterFactory;
import io.servicetalk.http.api.StreamingHttpRequest;
import io.servicetalk.http.api.StreamingHttpRequester;
import io.servicetalk.http.api.StreamingHttpResponse;
import io.servicetalk.http.api.StreamingHttpResponseFactory;
import io.servicetalk.http.api.StreamingHttpService;
import io.servicetalk.http.api.StreamingHttpServiceFilter;
import io.servicetalk.http.api.StreamingHttpServiceFilterFactory;
import io.servicetalk.transport.api.ServerContext;

import io.grpc.examples.helloworld.Greeter;
import io.grpc.examples.helloworld.Greeter.BlockingGreeterClient;
import io.grpc.examples.helloworld.Greeter.GreeterService;
import io.grpc.examples.helloworld.HelloReply;
import io.grpc.examples.helloworld.HelloRequest;
import org.junit.jupiter.api.function.Executable;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.Duration;
import java.util.concurrent.TimeoutException;

import static io.servicetalk.concurrent.api.Single.succeeded;
import static io.servicetalk.grpc.api.GrpcFilters.newGrpcDeadlineClientFilterFactory;
import static io.servicetalk.grpc.api.GrpcFilters.newGrpcDeadlineServerFilterFactory;
import static io.servicetalk.grpc.api.GrpcStatusCode.DEADLINE_EXCEEDED;
import static io.servicetalk.grpc.netty.GrpcClients.forResolvedAddress;
import static io.servicetalk.grpc.netty.GrpcServers.forAddress;
import static io.servicetalk.grpc.netty.GrpcTimeoutOrderTest.NeverStreamingHttpClientFilterFactory.NEVER_CLIENT_FILTER;
import static io.servicetalk.grpc.netty.GrpcTimeoutOrderTest.NeverStreamingHttpServiceFilterFactory.NEVER_SERVER_FILTER;
import static io.servicetalk.http.api.HttpExecutionStrategies.offloadNone;
import static io.servicetalk.transport.netty.internal.AddressUtils.localAddress;
import static java.time.Duration.ofMillis;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.instanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;

final class GrpcTimeoutOrderTest {
    private static final Duration DEFAULT_TIMEOUT = ofMillis(5);

    @ParameterizedTest(name = "{displayName} [{index}] appendNonOffloading={0} serverBuilderAppendTimeout={1} " +
            "serverManualAppendTimeout={2}")
    @CsvSource(value = {"true,true,false", "false,true,false", "false,false,false", "true,false,true",
                        "true,false,false"})
    void serverFilterNeverRespondsAppliesDeadline(boolean appendNonOffloading, boolean serverBuilderAppendTimeout,
                                                  boolean serverManualAppendTimeout) throws Exception {
        final boolean clientAppliesTimeout = (!serverManualAppendTimeout && !serverBuilderAppendTimeout);
        try (ServerContext serverContext = forAddress(localAddress(0))
                .defaultTimeout(serverBuilderAppendTimeout ? DEFAULT_TIMEOUT : null, serverBuilderAppendTimeout)
                .initializeHttp(builder -> {
                    if (serverManualAppendTimeout) {
                        if (appendNonOffloading) {
                            builder.appendNonOffloadingServiceFilter(
                                    newGrpcDeadlineServerFilterFactory(DEFAULT_TIMEOUT));
                        } else {
                            builder.appendServiceFilter(newGrpcDeadlineServerFilterFactory(DEFAULT_TIMEOUT));
                        }
                    }
                    if (appendNonOffloading) {
                        builder.appendNonOffloadingServiceFilter(NEVER_SERVER_FILTER);
                    } else {
                        builder.appendServiceFilter(NEVER_SERVER_FILTER);
                    }
                })
                .listenAndAwait((GreeterService) (ctx, request) ->
                        succeeded(HelloReply.newBuilder().setMessage("hello " + request.getName()).build()));
             BlockingGreeterClient client = forResolvedAddress(serverContext.listenAddress())
                     .defaultTimeout(clientAppliesTimeout ? DEFAULT_TIMEOUT : null, clientAppliesTimeout)
                     .buildBlocking(new Greeter.ClientFactory())) {
            assertGrpcTimeout(() -> client.sayHello(HelloRequest.newBuilder().setName("world").build()),
                    clientAppliesTimeout);
        }
    }

    @ParameterizedTest(name = "{displayName} [{index}] builderEnableTimeout={0}")
    @ValueSource(booleans = {true, false})
    void clientFilterNeverRespondsAppliesDeadline(boolean builderEnableTimeout) throws Exception {
        try (ServerContext serverContext = forAddress(localAddress(0))
                .defaultTimeout(null, false)
                .listenAndAwait((GreeterService) (ctx, request) -> {
                    throw new IllegalStateException("client using never filter, server shouldn't read response");
                });
             BlockingGreeterClient client = forResolvedAddress(serverContext.listenAddress())
                     .defaultTimeout(DEFAULT_TIMEOUT, builderEnableTimeout)
                     .initializeHttp(builder -> {
                         if (!builderEnableTimeout) {
                             builder.appendClientFilter(newGrpcDeadlineClientFilterFactory());
                         }
                         builder.appendClientFilter(NEVER_CLIENT_FILTER);
                     })
                     .buildBlocking(new Greeter.ClientFactory())) {
            assertGrpcTimeout(() -> client.sayHello(HelloRequest.newBuilder().setName("world").build()), true);
        }
    }

    private static void assertGrpcTimeout(Executable executable, boolean clientSideTimeout) {
        GrpcStatusException e = assertThrows(GrpcStatusException.class, executable);
        assertThat(e.status().code(), equalTo(DEADLINE_EXCEEDED));
        if (clientSideTimeout) {
            assertThat(e.getCause(), instanceOf(TimeoutException.class));
        }
    }

    static final class NeverStreamingHttpClientFilterFactory implements StreamingHttpClientFilterFactory {
        static final StreamingHttpClientFilterFactory NEVER_CLIENT_FILTER = new NeverStreamingHttpClientFilterFactory();

        private NeverStreamingHttpClientFilterFactory() {
        }

        @Override
        public StreamingHttpClientFilter create(final FilterableStreamingHttpClient client) {
            return new StreamingHttpClientFilter(client) {
                @Override
                protected Single<StreamingHttpResponse> request(final StreamingHttpRequester delegate,
                                                                final StreamingHttpRequest request) {
                    return Single.never();
                }
            };
        }

        @Override
        public HttpExecutionStrategy requiredOffloads() {
            return offloadNone();
        }
    }

    static final class NeverStreamingHttpServiceFilterFactory implements StreamingHttpServiceFilterFactory {
        static final StreamingHttpServiceFilterFactory NEVER_SERVER_FILTER =
                new NeverStreamingHttpServiceFilterFactory();

        private NeverStreamingHttpServiceFilterFactory() {
        }

        @Override
        public StreamingHttpServiceFilter create(final StreamingHttpService service) {
            return new StreamingHttpServiceFilter(service) {
                @Override
                public Single<StreamingHttpResponse> handle(
                        final HttpServiceContext ctx, final StreamingHttpRequest request,
                        final StreamingHttpResponseFactory responseFactory) {
                    return Single.never();
                }
            };
        }

        @Override
        public HttpExecutionStrategy requiredOffloads() {
            return offloadNone();
        }
    }
}

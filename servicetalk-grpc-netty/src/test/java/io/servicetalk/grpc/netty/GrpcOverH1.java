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

import io.servicetalk.http.api.HttpProtocolConfig;
import io.servicetalk.test.resources.DefaultTestCerts;
import io.servicetalk.transport.api.ClientSslConfigBuilder;
import io.servicetalk.transport.api.ServerContext;
import io.servicetalk.transport.api.ServerSslConfigBuilder;

import io.grpc.examples.helloworld.Greeter;
import io.grpc.examples.helloworld.Greeter.BlockingGreeterClient;
import io.grpc.examples.helloworld.HelloReply;
import io.grpc.examples.helloworld.HelloRequest;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import static io.servicetalk.concurrent.api.Single.succeeded;
import static io.servicetalk.grpc.netty.GrpcClients.forResolvedAddress;
import static io.servicetalk.grpc.netty.GrpcServers.forAddress;
import static io.servicetalk.http.netty.HttpProtocolConfigs.h1Default;
import static io.servicetalk.http.netty.HttpProtocolConfigs.h2Default;
import static io.servicetalk.test.resources.DefaultTestCerts.serverPemHostname;
import static io.servicetalk.transport.netty.internal.AddressUtils.localAddress;
import static org.junit.jupiter.api.Assertions.assertEquals;

final class GrpcOverH1 {
    private static final HttpProtocolConfig[] H1 = new HttpProtocolConfig[] {h1Default()};
    private static final HttpProtocolConfig[] H1H2 = new HttpProtocolConfig[] {h1Default(), h2Default()};
    private static final HttpProtocolConfig[] H2H1 = new HttpProtocolConfig[] {h2Default(), h1Default()};

    private enum ProtocolTestMode {
        ServerH1_ClientH1H2(H1, H1H2),
        ServerH1_ClientH2H1(H1, H2H1),
        ServerH1H2_ClientH1(H1H2, H1),
        ServerH2H1_ClientH1(H2H1, H1);

        final HttpProtocolConfig[] serverConfigs;
        final HttpProtocolConfig[] clientConfigs;

        ProtocolTestMode(HttpProtocolConfig[] serverConfigs, HttpProtocolConfig[] clientConfigs) {
            this.serverConfigs = serverConfigs;
            this.clientConfigs = clientConfigs;
        }
    }

    @ParameterizedTest
    @EnumSource(ProtocolTestMode.class)
    void tlsNegotiated(ProtocolTestMode testMode) throws Exception {
        String greetingPrefix = "Hello ";
        String name = "foo";
        String expectedResponse = greetingPrefix + name;
        try (ServerContext serverContext = forAddress(localAddress(0))
                .initializeHttp(builder -> builder
                        .sslConfig(new ServerSslConfigBuilder(DefaultTestCerts::loadServerPem,
                                DefaultTestCerts::loadServerKey).build())
                        .protocols(testMode.serverConfigs))
                .listenAndAwait((Greeter.GreeterService) (ctx, request) ->
                        succeeded(HelloReply.newBuilder().setMessage(greetingPrefix + request.getName()).build()));
             BlockingGreeterClient client = forResolvedAddress(serverContext.listenAddress())
                     .initializeHttp(builder -> builder
                             .sslConfig(new ClientSslConfigBuilder(DefaultTestCerts::loadServerCAPem)
                                     .peerHost(serverPemHostname())
                                     .build())
                             .protocols(testMode.clientConfigs))
                     .buildBlocking(new Greeter.ClientFactory())) {
            assertEquals(expectedResponse,
                    client.sayHello(HelloRequest.newBuilder().setName(name).build()).getMessage());
        }
    }
}

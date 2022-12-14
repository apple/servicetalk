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

import io.servicetalk.grpc.api.GrpcExecutionStrategies;
import io.servicetalk.grpc.api.GrpcServiceContext;
import io.servicetalk.router.api.RouteExecutionStrategy;

import io.grpc.examples.helloworld.Greeter;
import io.grpc.examples.helloworld.HelloReply;
import io.grpc.examples.helloworld.HelloRequest;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicBoolean;

import static io.servicetalk.transport.netty.internal.AddressUtils.localAddress;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ServiceFactoryCustomizationTest {

    @Test
    void usesCustomRouteExecutionStrategyFactory() throws Exception {
        final AtomicBoolean customFactoryCalled = new AtomicBoolean(false);

        GrpcServers
                .forAddress(localAddress(0))
                .listenAndAwait(new Greeter.ServiceFactory.Builder()
                        .routeExecutionStrategyFactory(id -> {
                            customFactoryCalled.set(true);
                            return GrpcExecutionStrategies.defaultStrategy();
                        })
                        .sayHelloBlocking(new Greeter.BlockingSayHelloRpc() {
                            @Override
                            @RouteExecutionStrategy(id = "none")
                            public HelloReply sayHello(final GrpcServiceContext ctx, final HelloRequest request) {
                                return HelloReply.newBuilder().setMessage("OK").build();
                            }
                        })
                        .build());

        assertTrue(customFactoryCalled.get());
    }
}

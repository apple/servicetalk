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
package io.servicetalk.examples.grpc.health;

import io.servicetalk.grpc.api.GrpcStatusException;
import io.servicetalk.grpc.health.DefaultHealthService;
import io.servicetalk.grpc.netty.GrpcClients;
import io.servicetalk.health.v1.Health;
import io.servicetalk.health.v1.Health.BlockingHealthClient;
import io.servicetalk.health.v1.HealthCheckRequest;
import io.servicetalk.health.v1.HealthCheckResponse;

import io.grpc.examples.health.Greeter;
import io.grpc.examples.health.Greeter.BlockingGreeterClient;
import io.grpc.examples.health.HelloReply;
import io.grpc.examples.health.HelloRequest;

/**
 * Extends the async "Hello World" example to demonstrate {@link DefaultHealthService} usage.
 */
public final class HealthClientExample {
    public static void main(String... args) throws Exception {
        final String serviceName = "World";
        try (BlockingGreeterClient client = GrpcClients.forAddress("localhost", 8080)
                .buildBlocking(new Greeter.ClientFactory());
             BlockingHealthClient healthClient = GrpcClients.forAddress("localhost", 8080)
                 .buildBlocking(new Health.ClientFactory())) {
            // Check health before
            checkHealth(healthClient, serviceName);

            HelloReply reply = client.sayHello(HelloRequest.newBuilder().setName("World").build());
            System.out.println("HelloReply=" + reply.getMessage());

            // Check the health after to observe it changed.
            checkHealth(healthClient, serviceName);
        }
    }

    private static void checkHealth(BlockingHealthClient healthClient, String serviceName) throws Exception {
        try {
            HealthCheckResponse response = healthClient.check(
                    HealthCheckRequest.newBuilder().setService(serviceName).build());
            System.out.println("Service '" + serviceName + "' health=" + response.getStatus());
        } catch (GrpcStatusException e) {
            System.out.println("Service '" + serviceName + "' health exception=" + e);
        }
    }
}

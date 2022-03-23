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

import io.servicetalk.grpc.health.DefaultHealthService;
import io.servicetalk.grpc.netty.GrpcServers;

import io.grpc.examples.health.Greeter.GreeterService;
import io.grpc.examples.health.HelloReply;

import static io.servicetalk.concurrent.api.Single.succeeded;
import static io.servicetalk.health.v1.HealthCheckResponse.ServingStatus.SERVING;

/**
 * A simple extension of the gRPC "Hello World" example which demonstrates {@link DefaultHealthService}.
 */
public final class HealthServerExample {
    public static void main(String... args) throws Exception {
        DefaultHealthService healthService = new DefaultHealthService();
        GreeterService greeterService = (ctx, request) -> {
            // For demonstration purposes, just use the name as a service and mark it as SERVING.
            healthService.setStatus(request.getName(), SERVING);
            return succeeded(HelloReply.newBuilder().setMessage("Hello " + request.getName()).build());
        };
        GrpcServers.forPort(8080)
                .listenAndAwait(healthService, greeterService)
                .awaitShutdown();
    }
}

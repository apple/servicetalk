/*
 * Copyright © 2019, 2021 Apple Inc. and the ServiceTalk project authors
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
package io.servicetalk.examples.grpc.deadline;

import io.servicetalk.grpc.netty.GrpcServers;

import io.grpc.examples.deadline.Greeter.GreeterService;
import io.grpc.examples.deadline.HelloReply;

import static io.servicetalk.concurrent.api.Single.succeeded;
import static java.time.Duration.ofMinutes;
import static java.time.Duration.ofSeconds;

/**
 * Extends the async "Hello World!" example to demonstrate use of
 * <a href="https://grpc.io/docs/what-is-grpc/core-concepts/#deadlines">gRPC deadlines</a> aka timeout feature.
 * <p>
 * Start this server first and then run the {@link DeadlineClient}.
 *
 * @see <a href="https://grpc.io/blog/deadlines/">gRPC and Deadlines</a>
 */
public class DeadlineServer {
    public static void main(String... args) throws Exception {
        GrpcServers.forPort(8080)
                // Set default timeout for completion of RPC calls made to this server
                .defaultTimeout(ofMinutes(2))
                .listenAndAwait((GreeterService) (ctx, request) ->
                        // Force a 5 second delay in the response.
                        ctx.executionContext().executor().timer(ofSeconds(5)).concat(
                                succeeded(HelloReply.newBuilder().setMessage("Hello " + request.getName()).build())))
                .awaitShutdown();
    }
}

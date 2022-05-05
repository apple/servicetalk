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
package io.servicetalk.examples.grpc.keepalive;

import io.servicetalk.concurrent.api.Single;
import io.servicetalk.grpc.netty.GrpcServers;

import io.grpc.examples.helloworld.StreamingGreeter.StreamingGreeterService;

import static io.servicetalk.http.netty.H2KeepAlivePolicies.whenIdleFor;
import static io.servicetalk.http.netty.HttpProtocolConfigs.h2;
import static io.servicetalk.logging.api.LogLevel.TRACE;
import static java.time.Duration.ofSeconds;

/**
 * Example that demonstrates how to enable HTTP/2 keep alive for a gRPC server.
 */
public final class KeepAliveServer {
    public static void main(String... args) throws Exception {
        GrpcServers.forPort(8080)
                .initializeHttp(httpBuilder -> httpBuilder.protocols(
                    // 6 second timeout is typically much shorter than necessary, but demonstrates PING frame traffic.
                    // Using the default value is suitable in most scenarios, but if you want to customize the value
                    // consider how many resources (network traffic, CPU for local timer management) vs time to detect
                    // bad connection.
                    // By default, keep alive is only sent when no traffic is detected, so if both peers have keep alive
                    // the faster interval will be the primary sender.
                    h2().keepAlivePolicy(whenIdleFor(ofSeconds(6)))
                    // Enable frame logging so we can see the PING frames sent/received.
                        .enableFrameLogging("servicetalk-examples-h2-frame-logger", TRACE, () -> true)
                        .build()))
                .listenAndAwait((StreamingGreeterService) (ctx, request) ->
                        request.whenOnNext(item -> System.out.println("Got request: " + item)).ignoreElements()
                        // Never return a response so we can see keep alive in action.
                        .concat(Single.never()))
                .awaitShutdown();
    }
}

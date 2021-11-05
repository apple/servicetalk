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
package io.servicetalk.examples.grpc.observer;

import io.servicetalk.grpc.api.GrpcLifecycleObserver;
import io.servicetalk.grpc.netty.GrpcServers;
import io.servicetalk.grpc.utils.GrpcLifecycleObservers;

import io.grpc.examples.observer.Greeter.GreeterService;
import io.grpc.examples.observer.HelloReply;

import static io.servicetalk.concurrent.api.Single.succeeded;
import static io.servicetalk.logging.api.LogLevel.TRACE;

/**
 * An example server that shows {@link GrpcLifecycleObserver} usage.
 */
public final class LifecycleObserverServer {
    public static void main(String... args) throws Exception {
        GrpcServers.forPort(8080)
                .lifecycleObserver(GrpcLifecycleObservers.logging("servicetalk-examples-grpc-observer-logger", TRACE))
                .listenAndAwait((GreeterService) (ctx, request) ->
                        succeeded(HelloReply.newBuilder().setMessage("Hello " + request.getName()).build()))
                .awaitShutdown();
    }
}

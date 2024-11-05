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
        GrpcLifecycleObserver observer =
                GrpcLifecycleObservers.logging("servicetalk-examples-grpc-observer-logger", TRACE);
        GrpcServers.forPort(8080)
                // There are a few ways how to configure an observer depending on the desired scope of its visibility.
                // 1. Configuring it at the builder gives maximum visibility and captures entire request-response state,
                // including all filters and exception mappers.
                .lifecycleObserver(observer)
                // 2. Configuring it as a filter allows users to change the ordering of the observer compare to other
                // filters or make it conditional. This might be helpful in a few scenarios such as when the tracking
                // scope should be limited or when logging should include tracing/MDC context set by other preceding
                // filters. See javadoc of GrpcLifecycleObserverServiceFilter for more details.
                // .initializeHttp(builder -> builder
                        // 2.a. At any position compare to other filters before offloading:
                        // .appendNonOffloadingServiceFilter(new GrpcLifecycleObserverServiceFilter(observer))
                        // 2.b. At any position compare to other filters after offloading:
                        // .appendServiceFilter(new GrpcLifecycleObserverServiceFilter(observer)))
                .listenAndAwait((GreeterService) (ctx, request) ->
                        succeeded(HelloReply.newBuilder().setMessage("Hello " + request.getName()).build()))
                .awaitShutdown();
    }
}

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
import io.servicetalk.grpc.netty.GrpcClients;
import io.servicetalk.grpc.netty.GrpcLifecycleObserverRequesterFilter;
import io.servicetalk.grpc.utils.GrpcLifecycleObservers;

import io.grpc.examples.observer.Greeter.BlockingGreeterClient;
import io.grpc.examples.observer.Greeter.ClientFactory;
import io.grpc.examples.observer.HelloRequest;

import static io.servicetalk.logging.api.LogLevel.TRACE;

/**
 * An example client that shows {@link GrpcLifecycleObserver} usage.
 */
public final class LifecycleObserverClient {
    public static void main(String... args) throws Exception {
        try (BlockingGreeterClient client = GrpcClients.forAddress("localhost", 8080)
                // Append this filter first for most cases to maximize visibility!
                // See javadocs on GrpcLifecycleObserverRequesterFilter for more details on filter ordering.
                .initializeHttp(builder -> builder.appendClientFilter(new GrpcLifecycleObserverRequesterFilter(
                        GrpcLifecycleObservers.logging("servicetalk-examples-grpc-observer-logger", TRACE))))
                .buildBlocking(new ClientFactory())) {
            client.sayHello(HelloRequest.newBuilder().setName("LifecycleObserver").build());
            // Ignore the response for this example. See logs for GrpcLifecycleObserver results.
        }
    }
}

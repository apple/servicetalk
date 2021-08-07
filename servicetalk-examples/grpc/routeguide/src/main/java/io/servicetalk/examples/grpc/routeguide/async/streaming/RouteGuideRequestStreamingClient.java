/*
 * Copyright © 2019 Apple Inc. and the ServiceTalk project authors
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
package io.servicetalk.examples.grpc.routeguide.async.streaming;

import io.servicetalk.grpc.netty.GrpcClients;

import io.grpc.examples.routeguide.Point;
import io.grpc.examples.routeguide.RouteGuide.ClientFactory;
import io.grpc.examples.routeguide.RouteGuide.RouteGuideClient;

import static io.servicetalk.concurrent.api.Publisher.from;

public final class RouteGuideRequestStreamingClient {
    public static void main(String[] args) throws Exception {
        try (RouteGuideClient client = GrpcClients.forAddress("localhost", 8080).build(new ClientFactory())) {
            client.recordRoute(from(Point.newBuilder().setLatitude(123456).setLongitude(-123456).build(),
                            Point.newBuilder().setLatitude(789000).setLongitude(-789000).build()))
                    .whenOnSuccess(System.out::println)
            // This example is demonstrating asynchronous execution, but needs to prevent the main thread from exiting
            // before the response has been processed. This isn't typical usage for an asynchronous API but is useful
            // for demonstration purposes.
                    .toFuture().get();
        }
    }
}

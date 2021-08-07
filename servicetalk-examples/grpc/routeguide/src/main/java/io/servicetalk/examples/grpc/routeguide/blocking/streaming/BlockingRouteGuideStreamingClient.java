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
package io.servicetalk.examples.grpc.routeguide.blocking.streaming;

import io.servicetalk.concurrent.BlockingIterable;
import io.servicetalk.concurrent.BlockingIterator;
import io.servicetalk.grpc.netty.GrpcClients;

import io.grpc.examples.routeguide.Point;
import io.grpc.examples.routeguide.RouteGuide.BlockingRouteGuideClient;
import io.grpc.examples.routeguide.RouteGuide.ClientFactory;
import io.grpc.examples.routeguide.RouteNote;

import static io.servicetalk.concurrent.api.Processors.newBlockingIterableProcessor;

public final class BlockingRouteGuideStreamingClient {
    public static void main(String[] args) throws Exception {
        try (BlockingRouteGuideClient client = GrpcClients.forAddress("localhost", 8080)
                .buildBlocking(new ClientFactory())) {
            BlockingIterator<RouteNote> response;
            try (BlockingIterable.Processor<RouteNote> request = newBlockingIterableProcessor()){
                response = client.routeChat(request).iterator();
                request.next(RouteNote.newBuilder()
                        .setLocation(Point.newBuilder().setLatitude(123456).setLongitude(-123456).build())
                        .setMessage("First note.").build());
                request.next(RouteNote.newBuilder()
                        .setLocation(Point.newBuilder().setLatitude(123456).setLongitude(-123456).build())
                        .setMessage("Querying notes.").build());
            }
            while (response.hasNext()) {
                System.out.println(response.next());
            }
        }
    }
}

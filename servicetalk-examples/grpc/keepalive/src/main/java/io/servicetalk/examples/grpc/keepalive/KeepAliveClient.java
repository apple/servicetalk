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

import io.servicetalk.grpc.netty.GrpcClients;

import io.grpc.examples.keepalive.HelloReply;
import io.grpc.examples.keepalive.HelloRequest;
import io.grpc.examples.keepalive.StreamingGreeter.BlockingStreamingGreeterClient;
import io.grpc.examples.keepalive.StreamingGreeter.ClientFactory;

import java.io.IOException;
import java.util.Iterator;
import java.util.function.Supplier;

import static io.servicetalk.http.netty.H2KeepAlivePolicies.whenIdleFor;
import static io.servicetalk.http.netty.HttpProtocolConfigs.h2;
import static io.servicetalk.logging.api.LogLevel.TRACE;
import static java.time.Duration.ofSeconds;

/**
 * Example that demonstrates how to enable HTTP/2 keep alive for a gRPC client.
 */
public final class KeepAliveClient {
    public static void main(String... args) throws Exception {
        try (BlockingStreamingGreeterClient client = GrpcClients.forAddress("localhost", 8080)
                .initializeHttp(httpBuilder -> httpBuilder.protocols(
                    // 4 second timeout is typically much shorter than necessary, but demonstrates PING frame traffic.
                    // By default, keep alive is only sent when no traffic is detected, so if both peers have keep alive
                    // the faster interval will be the primary sender.
                    h2().keepAlivePolicy(whenIdleFor(ofSeconds(4)))
                    // Enable frame logging so we can see the PING frames sent/received.
                        .enableFrameLogging("servicetalk-examples-h2-frame-logger", TRACE, () -> true)
                        .build()))
                .buildBlocking(new ClientFactory())) {
            HelloReply reply = client.streamHello(new StdInIterable<>(() ->
                    HelloRequest.newBuilder().setName("World").build()));
            System.out.println("Got reply: " + reply);
        }
    }

    /**
     * Infinite input stream, each item is sent when data is read from std in.
     * @param <T> The type of items to iterate.
     */
    private static final class StdInIterable<T> implements Iterable<T> {
        private final Supplier<T> itemSupplier;

        private StdInIterable(final Supplier<T> itemSupplier) {
            this.itemSupplier = itemSupplier;
        }

        @Override
        public Iterator<T> iterator() {
            return new Iterator<T>() {
                @Override
                public boolean hasNext() {
                    System.out.println("Press any key to send next item...");
                    try {
                        @SuppressWarnings("unused")
                        int r = System.in.read();
                    } catch (IOException e) {
                        throw new RuntimeException("Unexpected exception waiting for input", e);
                    }
                    return true;
                }

                @Override
                public T next() {
                    return itemSupplier.get();
                }
            };
        }
    }
}

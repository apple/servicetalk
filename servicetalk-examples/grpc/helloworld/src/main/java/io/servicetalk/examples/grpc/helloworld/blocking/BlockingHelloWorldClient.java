/*
 * Copyright © 2018 Apple Inc. and the ServiceTalk project authors
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
package io.servicetalk.examples.grpc.helloworld.blocking;

import io.servicetalk.capacity.limiter.api.CapacityLimiter;
import io.servicetalk.capacity.limiter.api.CapacityLimiters;
import io.servicetalk.concurrent.api.Completable;
import io.servicetalk.grpc.api.GrpcServerContext;
import io.servicetalk.grpc.netty.GrpcClients;
import io.servicetalk.grpc.netty.GrpcServers;

import io.grpc.examples.helloworld.Greeter;
import io.grpc.examples.helloworld.Greeter.BlockingGreeterClient;
import io.grpc.examples.helloworld.Greeter.ClientFactory;
import io.grpc.examples.helloworld.HelloReply;
import io.grpc.examples.helloworld.HelloRequest;
import io.servicetalk.traffic.resilience.http.TrafficResilienceHttpClientFilter;

import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import static io.servicetalk.concurrent.api.Single.succeeded;

public final class BlockingHelloWorldClient {
    public static void main(String[] args) throws Exception {

        final double fraction = 0.1;

        GrpcServerContext serverContext = GrpcServers.forPort(8080)
                .listenAndAwait((Greeter.GreeterService) (ctx, request) -> {
                    Completable timer;
                    if (ThreadLocalRandom.current().nextDouble() < fraction) {
                        timer = io.servicetalk.concurrent.api.Executors.global()

                                .timer(Duration.ofMillis(50));
                    } else {
                        timer = Completable.completed();
                    }

                    return timer.concat(succeeded(HelloReply.newBuilder().setMessage("Hello " + request.getName()).build()));
                });

        // Share the limiter.
        CapacityLimiter limiter = CapacityLimiters.fixedCapacity(1).build();
        final TrafficResilienceHttpClientFilter resilienceHttpClientFilter =
                new TrafficResilienceHttpClientFilter.Builder(() -> limiter).build();

        final int numClients = 60;
        final CountDownLatch latch = new CountDownLatch(numClients);
        final AtomicBoolean finished = new AtomicBoolean();
        ExecutorService executor = Executors.newCachedThreadPool();
        AtomicLong counter = new AtomicLong();
        final AtomicInteger consecutiveFailures = new AtomicInteger();
        final int failureLimit = 600;

        for (int i = 0; i < numClients; i++) {
            executor.execute(() -> {
                try {
                    try (BlockingGreeterClient client = GrpcClients.forAddress("localhost", 8080)
                            .defaultTimeout(Duration.ofMillis(50))
                            .initializeHttp(http -> {
                                http.appendClientFilter(resilienceHttpClientFilter);
                            })
                            .buildBlocking(new ClientFactory())) {
                        while (!finished.get()) {
                            try {
                                HelloReply reply = client.sayHello(HelloRequest.newBuilder().setName("World").build());
                                reply.getMessage();
                                System.out.print(".");
                                consecutiveFailures.set(0);
                            } catch (Exception ex) {
                                System.out.print("!");
                                if (consecutiveFailures.incrementAndGet() >= failureLimit && finished.compareAndSet(false, true)) {
                                    System.gc(); // hopefully this will hit the finalizers and emit log statements.
                                    try {
                                        Thread.sleep(1000);
                                    } catch (InterruptedException ignored) {
                                        // noop
                                    }
                                    System.gc();
                                    System.out.printf("\nConsecutive failure threshold reached (%d). Terminating.\n", failureLimit);
                                }
                            }
                            if (counter.incrementAndGet() % 100 == 0) {
                                System.out.print('\n');
                            }
                            Thread.sleep(100);
                        }
                        latch.countDown();
                    }
                } catch (Exception ex) {
                    ex.printStackTrace();
                }
            });
        }

        latch.await();
        serverContext.close();
        executor.shutdown();
        System.out.println("Terminating.");
    }
}
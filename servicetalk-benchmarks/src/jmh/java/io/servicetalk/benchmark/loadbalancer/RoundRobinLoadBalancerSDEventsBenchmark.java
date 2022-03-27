/*
 * Copyright © 2020 Apple Inc. and the ServiceTalk project authors
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
package io.servicetalk.benchmark.loadbalancer;

import io.servicetalk.client.api.ConnectionFactory;
import io.servicetalk.client.api.DefaultServiceDiscovererEvent;
import io.servicetalk.client.api.LoadBalancedConnection;
import io.servicetalk.client.api.LoadBalancer;
import io.servicetalk.client.api.ServiceDiscovererEvent;
import io.servicetalk.concurrent.api.Completable;
import io.servicetalk.concurrent.api.Single;
import io.servicetalk.context.api.ContextMap;
import io.servicetalk.loadbalancer.RoundRobinLoadBalancerFactory;
import io.servicetalk.transport.api.TransportObserver;

import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Level;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Warmup;

import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.List;
import javax.annotation.Nullable;

import static io.servicetalk.client.api.ServiceDiscovererEvent.Status.AVAILABLE;
import static io.servicetalk.client.api.ServiceDiscovererEvent.Status.UNAVAILABLE;
import static io.servicetalk.concurrent.api.Completable.completed;
import static io.servicetalk.concurrent.api.Publisher.from;
import static io.servicetalk.concurrent.api.Single.succeeded;
import static java.net.InetSocketAddress.createUnresolved;

@Fork(1)
@State(Scope.Benchmark)
@Warmup(iterations = 5, time = 5)
@Measurement(iterations = 5, time = 5)
public class RoundRobinLoadBalancerSDEventsBenchmark {
    @Param({"5", "10", "100"})
    public int ops;

    private List<ServiceDiscovererEvent<InetSocketAddress>> availableEvents;
    private List<ServiceDiscovererEvent<InetSocketAddress>> mixedEvents;

    @Setup(Level.Trial)
    public void setup() {
        final int removalStride = 5;
        availableEvents = new ArrayList<>(ops);
        mixedEvents = new ArrayList<>(ops);
        for (int i = 1; i <= ops; ++i) {
            if (i % removalStride == 0) {
                mixedEvents.add(new DefaultServiceDiscovererEvent<>(
                        createUnresolved("127.0.0." + (i - 1), 0), UNAVAILABLE));
            } else {
                mixedEvents.add(new DefaultServiceDiscovererEvent<>(
                        createUnresolved("127.0.0." + i, 0), AVAILABLE));
            }
            availableEvents.add(new DefaultServiceDiscovererEvent<>(
                    createUnresolved("127.0.0." + i, 0), AVAILABLE));
        }
    }

    @Benchmark
    public LoadBalancer<LoadBalancedConnection> mixed() {
        // RR load balancer synchronously subscribes and will consume all events during construction.
        return new RoundRobinLoadBalancerFactory.Builder<InetSocketAddress, LoadBalancedConnection>().build()
                .newLoadBalancerTyped("benchmark", from(mixedEvents), ConnFactory.INSTANCE);
    }

    @Benchmark
    public LoadBalancer<LoadBalancedConnection> available() {
        // RR load balancer synchronously subscribes and will consume all events during construction.
        return new RoundRobinLoadBalancerFactory.Builder<InetSocketAddress, LoadBalancedConnection>().build()
                        .newLoadBalancerTyped("benchmark", from(availableEvents), ConnFactory.INSTANCE);
    }

    private static final class ConnFactory implements ConnectionFactory<InetSocketAddress, LoadBalancedConnection> {
        static final ConnFactory INSTANCE = new ConnFactory();

        private ConnFactory() {
        }

        @Override
        public Single<LoadBalancedConnection> newConnection(final InetSocketAddress inetSocketAddress,
                                                            @Nullable final ContextMap context,
                                                            @Nullable final TransportObserver observer) {
            return succeeded(new LoadBalancedConnection() {
                @Override
                public int score() {
                    return 0;
                }

                @Override
                public Completable onClose() {
                    return completed();
                }

                @Override
                public Completable closeAsync() {
                    return completed();
                }

                @Override
                public Completable closeAsyncGracefully() {
                    return completed();
                }
            });
        }

        @Override
        public Completable onClose() {
            return completed();
        }

        @Override
        public Completable closeAsync() {
            return completed();
        }
    }
}

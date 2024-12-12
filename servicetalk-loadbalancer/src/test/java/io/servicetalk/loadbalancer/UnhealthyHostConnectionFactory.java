/*
 * Copyright © 2023 Apple Inc. and the ServiceTalk project authors
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
package io.servicetalk.loadbalancer;

import io.servicetalk.concurrent.api.Single;
import io.servicetalk.concurrent.api.TestExecutor;
import io.servicetalk.concurrent.internal.DeliberateException;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static io.servicetalk.concurrent.api.Single.defer;
import static io.servicetalk.concurrent.api.Single.failed;
import static java.util.concurrent.TimeUnit.SECONDS;

class UnhealthyHostConnectionFactory {
    // Create a new instance of DeliberateException to avoid "Self-suppression not permitted"
    static final DeliberateException UNHEALTHY_HOST_EXCEPTION = new DeliberateException();
    private final String failingHost;
    private final AtomicInteger momentInTime = new AtomicInteger();
    final AtomicInteger requests = new AtomicInteger();
    final Single<TestLoadBalancedConnection> properConnection;
    final List<Single<TestLoadBalancedConnection>> connections;

    final Function<String, Single<TestLoadBalancedConnection>> factory =
            new Function<String, Single<TestLoadBalancedConnection>>() {

                @Override
                public Single<TestLoadBalancedConnection> apply(final String s) {
                    return defer(() -> {
                        if (s.equals(failingHost)) {
                            requests.incrementAndGet();
                            if (momentInTime.get() >= connections.size()) {
                                return properConnection;
                            }
                            return connections.get(momentInTime.get());
                        }
                        return properConnection;
                    });
                }
            };

    UnhealthyHostConnectionFactory(String failingHost, int timeAdvancementsTillHealthy,
                                   Single<TestLoadBalancedConnection> properConnection) {
        this(failingHost, timeAdvancementsTillHealthy, properConnection, UNHEALTHY_HOST_EXCEPTION);
    }

    UnhealthyHostConnectionFactory(String failingHost, int timeAdvancementsTillHealthy,
                                   Single<TestLoadBalancedConnection> properConnection, Throwable exception) {
        this.failingHost = failingHost;
        this.connections = IntStream.range(0, timeAdvancementsTillHealthy)
                .<Single<TestLoadBalancedConnection>>mapToObj(__ -> failed(exception))
                .collect(Collectors.toList());
        this.properConnection = properConnection;
    }

    TestConnectionFactory createFactory() {
        return new TestConnectionFactory(this.factory);
    }

    void advanceTime(TestExecutor executor) {
        momentInTime.incrementAndGet();
        executor.advanceTimeBy(1, SECONDS);
    }
}

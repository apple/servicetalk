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

import io.servicetalk.client.api.LoadBalancedConnection;

import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

class RoundRobinLoadBalancerBuilderProviderTest {

    private static final AtomicInteger buildCounter = new AtomicInteger();
    private static final AtomicLong linearSearchSpaceIntercept = new AtomicLong();

    @Test
    void appliesBuilderProvider() {
        final RoundRobinLoadBalancerFactory<Object, LoadBalancedConnection> loadBalancerFactory =
                RoundRobinLoadBalancers.builder().linearSearchSpace(1234).build();
        assertThat("TestRoundRobinLoadBalancerBuilderProvider not called", buildCounter.get(), is(1));
        assertThat("Builder method not intercepted", linearSearchSpaceIntercept.get(), is(1234L));
    }

    public static final class TestRoundRobinLoadBalancerBuilderProvider
            implements RoundRobinLoadBalancerBuilderProvider {
        @Override
        public <ResolvedAddress, C extends LoadBalancedConnection> RoundRobinLoadBalancerBuilder<ResolvedAddress, C>
        newBuilder(final RoundRobinLoadBalancerBuilder<ResolvedAddress, C> builder) {
            buildCounter.incrementAndGet();
            return new DelegatingRoundRobinLoadBalancerBuilder<ResolvedAddress, C>(builder) {
                @Override
                public RoundRobinLoadBalancerBuilder<ResolvedAddress, C> linearSearchSpace(
                        final int linearSearchSpace) {
                    linearSearchSpaceIntercept.set(linearSearchSpace);
                    delegate().linearSearchSpace(linearSearchSpace);
                    return this;
                }
            };
        }
    }
}

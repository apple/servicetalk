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
import io.servicetalk.client.api.LoadBalancerFactory;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

class RoundRobinLoadBalancerBuilderProviderTest {

    @BeforeEach
    void reset() {
        TestRoundRobinLoadBalancerBuilderProvider.reset();
    }

    @AfterEach
    void deactivate() {
        TestRoundRobinLoadBalancerBuilderProvider.activated.set(false);
    }

    @Test
    void appliesBuilderProvider() {
        RoundRobinLoadBalancers.builder(getClass().getSimpleName()).linearSearchSpace(1234).build();
        assertThat("TestRoundRobinLoadBalancerBuilderProvider not called",
                TestRoundRobinLoadBalancerBuilderProvider.buildCounter.get(), is(1));
        assertThat("Builder method not intercepted",
                TestRoundRobinLoadBalancerBuilderProvider.linearSearchSpaceIntercept.get(), is(1234));
        assertThat("Unexpected builder ID", TestRoundRobinLoadBalancerBuilderProvider.buildId.get(),
                is(getClass().getSimpleName()));
    }

    public static final class TestRoundRobinLoadBalancerBuilderProvider
            implements RoundRobinLoadBalancerBuilderProvider {

        // Used to prevent applying this provider for other test classes:
        static final AtomicBoolean activated = new AtomicBoolean();
        static final AtomicInteger buildCounter = new AtomicInteger();
        static final AtomicInteger linearSearchSpaceIntercept = new AtomicInteger();
        static final AtomicReference<String> buildId = new AtomicReference<>();

        static void reset() {
            activated.set(true);
            buildCounter.set(0);
            linearSearchSpaceIntercept.set(0);
            buildId.set(null);
        }

        @Override
        public <ResolvedAddress, C extends LoadBalancedConnection> RoundRobinLoadBalancerBuilder<ResolvedAddress, C>
        newBuilder(final String id, final RoundRobinLoadBalancerBuilder<ResolvedAddress, C> builder) {
            return activated.get() ? new DelegatingRoundRobinLoadBalancerBuilder<ResolvedAddress, C>(builder) {
                @Override
                public RoundRobinLoadBalancerBuilder<ResolvedAddress, C> linearSearchSpace(
                        final int linearSearchSpace) {
                    linearSearchSpaceIntercept.set(linearSearchSpace);
                    delegate().linearSearchSpace(linearSearchSpace);
                    return this;
                }

                @Override
                public LoadBalancerFactory<ResolvedAddress, C> build() {
                    buildId.set(id);
                    buildCounter.incrementAndGet();
                    return delegate().build();
                }
            } : builder;
        }
    }
}

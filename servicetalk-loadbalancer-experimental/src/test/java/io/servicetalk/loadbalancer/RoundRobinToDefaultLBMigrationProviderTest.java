/*
 * Copyright © 2024 Apple Inc. and the ServiceTalk project authors
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

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;

import static io.servicetalk.loadbalancer.RoundRobinToDefaultLBMigrationProvider.PROPERTY_NAME;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.instanceOf;
import static org.hamcrest.Matchers.sameInstance;

@Execution(ExecutionMode.SAME_THREAD)
final class RoundRobinToDefaultLBMigrationProviderTest {

    private final RoundRobinToDefaultLBMigrationProvider provider = new RoundRobinToDefaultLBMigrationProvider();

    @AfterEach
    void cleanup() {
        System.clearProperty(PROPERTY_NAME);
    }

    @Test
    void enabled() {
        System.setProperty(PROPERTY_NAME, "true");
        RoundRobinLoadBalancerBuilder<String, TestLoadBalancedConnection> builder =
                new RoundRobinLoadBalancerFactory.Builder<>();
        RoundRobinLoadBalancerBuilder<String, TestLoadBalancedConnection> result = provider.newBuilder(
                "builder", builder);
        assertThat(result.build(), instanceOf(DefaultLoadBalancerBuilder.DefaultLoadBalancerFactory.class));
    }

    @Test
    void disabled() {
        System.setProperty(PROPERTY_NAME, "false");
        RoundRobinLoadBalancerBuilder<String, TestLoadBalancedConnection> builder =
                new RoundRobinLoadBalancerFactory.Builder<>();
        RoundRobinLoadBalancerBuilder<String, TestLoadBalancedConnection> result = provider.newBuilder(
                "builder", builder);
        assertThat(result, sameInstance(builder));
    }

    @Test
    void defaultValue() {
        RoundRobinLoadBalancerBuilder<String, TestLoadBalancedConnection> builder =
                new RoundRobinLoadBalancerFactory.Builder<>();
        RoundRobinLoadBalancerBuilder<String, TestLoadBalancedConnection> result = provider.newBuilder(
                "builder", builder);
        assertThat(result, sameInstance(builder));
    }
}

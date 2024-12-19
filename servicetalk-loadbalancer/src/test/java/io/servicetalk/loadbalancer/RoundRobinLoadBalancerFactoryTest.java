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

import io.servicetalk.client.api.ConnectionFactory;
import io.servicetalk.client.api.LoadBalancer;
import io.servicetalk.concurrent.api.Publisher;
import io.servicetalk.concurrent.api.Single;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;

import static io.servicetalk.loadbalancer.RoundRobinLoadBalancerFactory.ROUND_ROBIN_USER_DEFAULT_LOAD_BALANCER;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.instanceOf;

@Execution(ExecutionMode.SAME_THREAD)
final class RoundRobinLoadBalancerFactoryTest {

    @AfterEach
    void cleanup() {
        System.clearProperty(ROUND_ROBIN_USER_DEFAULT_LOAD_BALANCER);
    }

    @Test
    void generateDefaultLoadBalancerIfEnabled() {
        System.setProperty(ROUND_ROBIN_USER_DEFAULT_LOAD_BALANCER, "true");
        assertThat(getLoadBalancer(), instanceOf(DefaultLoadBalancer.class));
    }

    @Test
    void doesNotGenerateDefaultLoadBalancerIfDisabled() {
        System.setProperty(ROUND_ROBIN_USER_DEFAULT_LOAD_BALANCER, "false");
        assertThat(getLoadBalancer(), instanceOf(RoundRobinLoadBalancer.class));
    }

    @Test
    void defaultResultIsDefaultLoadBalancer() {
        assertThat(getLoadBalancer(), instanceOf(DefaultLoadBalancer.class));
    }

    static LoadBalancer<TestLoadBalancedConnection> getLoadBalancer() {
        ConnectionFactory<String, TestLoadBalancedConnection> connectionFactory =
                new TestConnectionFactory(ignored -> Single.never());
        return RoundRobinLoadBalancers.<String, TestLoadBalancedConnection>builder("balancer").build()
                .newLoadBalancer(Publisher.never(), connectionFactory, "targetResource");
    }
}

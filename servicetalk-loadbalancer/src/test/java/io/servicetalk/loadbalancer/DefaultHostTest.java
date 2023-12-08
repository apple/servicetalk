/*
 * Copyright © 2021 Apple Inc. and the ServiceTalk project authors
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
import io.servicetalk.concurrent.api.ExecutorExtension;
import io.servicetalk.concurrent.api.TestExecutor;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import java.time.Duration;
import java.util.concurrent.ExecutionException;
import java.util.function.Predicate;

import static io.servicetalk.concurrent.api.Single.succeeded;
import static io.servicetalk.loadbalancer.HealthCheckConfig.DEFAULT_HEALTH_CHECK_FAILED_CONNECTIONS_THRESHOLD;
import static io.servicetalk.loadbalancer.HealthCheckConfig.DEFAULT_HEALTH_CHECK_INTERVAL;
import static io.servicetalk.loadbalancer.HealthCheckConfig.DEFAULT_HEALTH_CHECK_JITTER;
import static io.servicetalk.loadbalancer.HealthCheckConfig.DEFAULT_HEALTH_CHECK_RESUBSCRIBE_INTERVAL;
import static io.servicetalk.loadbalancer.UnhealthyHostConnectionFactory.UNHEALTHY_HOST_EXCEPTION;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;

class DefaultHostTest {

    private static final String DEFAULT_ADDRESS = "address";

    @RegisterExtension
    final ExecutorExtension<TestExecutor> executor = ExecutorExtension.withTestExecutor();

    private MockLoadBalancerObserver mockLoadBalancerObserver;
    private ConnectionFactory<String, TestLoadBalancedConnection> connectionFactory;
    private TestExecutor testExecutor;
    private HealthCheckConfig healthCheckConfig;
    private DefaultHost<String, TestLoadBalancedConnection> host;


    @BeforeEach
    void init() {
        mockLoadBalancerObserver = MockLoadBalancerObserver.mockObserver();
        connectionFactory = new TestConnectionFactory(address ->
                succeeded(TestLoadBalancedConnection.mockConnection(address)));
        testExecutor = executor.executor();
        healthCheckConfig = null;
    }

    @AfterEach
    void cleanup() {
        if (mockLoadBalancerObserver != null) {
            verifyNoMoreInteractions(mockLoadBalancerObserver.hostObserver());
            verifyNoMoreInteractions(mockLoadBalancerObserver.outlierEventObserver());
        }
    }

    void buildHost() {
        host = new DefaultHost<>("lbDescription", DEFAULT_ADDRESS, connectionFactory, Integer.MAX_VALUE,
                healthCheckConfig, mockLoadBalancerObserver);
    }

    @Test
    void hostCreatedEvents() {
        buildHost();
        verify(mockLoadBalancerObserver.hostObserver(), times(1)).hostCreated(DEFAULT_ADDRESS);
        // make another one, just for good measure.
        new DefaultHost<>("lbDescription", "address2", connectionFactory, Integer.MAX_VALUE,
                healthCheckConfig, mockLoadBalancerObserver);
        verify(mockLoadBalancerObserver.hostObserver(), times(1)).hostCreated("address2");
    }

    @Test
    void activeHostClosed() {
        buildHost();
        verify(mockLoadBalancerObserver.hostObserver(), times(1)).hostCreated(DEFAULT_ADDRESS);
        host.markClosed();
        verify(mockLoadBalancerObserver.hostObserver(), times(1))
                .activeHostRemoved(DEFAULT_ADDRESS,
                        0);
        // TODO: this is broken: our `markClosed` method doesn't trigger the host closing down.
        // assertTrue(host.onClose().toFuture().isDone());
    }

    @Test
    void activeHostExpires() {
        buildHost();
        verify(mockLoadBalancerObserver.hostObserver(), times(1)).hostCreated(DEFAULT_ADDRESS);
        host.markExpired();
        verify(mockLoadBalancerObserver.hostObserver(), times(1))
                .hostMarkedExpired(DEFAULT_ADDRESS, 0);
        assertTrue(host.onClose().toFuture().isDone());
    }

    @Test
    void outliersAreDetected() {
        TestLoadBalancedConnection testLoadBalancedConnection = TestLoadBalancedConnection.mockConnection(
                DEFAULT_ADDRESS);
        UnhealthyHostConnectionFactory unhealthyHostConnectionFactory = new UnhealthyHostConnectionFactory(
                DEFAULT_ADDRESS, 1, succeeded(testLoadBalancedConnection));
        connectionFactory = unhealthyHostConnectionFactory.createFactory();
        healthCheckConfig = new HealthCheckConfig(testExecutor,
                Duration.ofMillis(1),
                Duration.ofNanos(1),
                DEFAULT_HEALTH_CHECK_FAILED_CONNECTIONS_THRESHOLD,
                Duration.ofMillis(1),
                Duration.ZERO);
        buildHost();
        verify(mockLoadBalancerObserver.hostObserver(), times(1)).hostCreated(DEFAULT_ADDRESS);
        for (int i = 0; i < DEFAULT_HEALTH_CHECK_FAILED_CONNECTIONS_THRESHOLD; i++) {
            assertThrows(ExecutionException.class,
                    () -> host.newConnection(any(), false, null).toFuture().get());
        }
        verify(mockLoadBalancerObserver.outlierEventObserver(), times(1))
                .hostMarkedUnhealthy(DEFAULT_ADDRESS, UNHEALTHY_HOST_EXCEPTION);

        // now revive.
        unhealthyHostConnectionFactory.advanceTime(testExecutor);
        verify(mockLoadBalancerObserver.outlierEventObserver(), times(1))
                .hostRevived(DEFAULT_ADDRESS);
    }

    static <T> Predicate<T> any() {
        return __ -> true;
    }
}

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
import javax.annotation.Nullable;

import static io.servicetalk.concurrent.api.Single.failed;
import static io.servicetalk.concurrent.api.Single.succeeded;
import static io.servicetalk.concurrent.internal.DeliberateException.DELIBERATE_EXCEPTION;
import static io.servicetalk.loadbalancer.HealthCheckConfig.DEFAULT_HEALTH_CHECK_FAILED_CONNECTIONS_THRESHOLD;
import static io.servicetalk.loadbalancer.UnhealthyHostConnectionFactory.UNHEALTHY_HOST_EXCEPTION;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

class DefaultHostTest {

    private static final String DEFAULT_ADDRESS = "address";

    @RegisterExtension
    final ExecutorExtension<TestExecutor> executor = ExecutorExtension.withTestExecutor();

    private MockLoadBalancerObserver.MockHostObserver mockHostObserver;
    private ConnectionFactory<String, TestLoadBalancedConnection> connectionFactory;
    private TestExecutor testExecutor;
    @Nullable
    private HealthCheckConfig healthCheckConfig;
    private DefaultHost<String, TestLoadBalancedConnection> host;

    static <T> Predicate<T> any() {
        return __ -> true;
    }

    @BeforeEach
    void init() {
        mockHostObserver = MockLoadBalancerObserver.mockObserver().hostObserver();
        connectionFactory = new TestConnectionFactory(address ->
                succeeded(TestLoadBalancedConnection.mockConnection(address)));
        testExecutor = executor.executor();
        healthCheckConfig = null;
    }

    @AfterEach
    void cleanup() {
        if (mockHostObserver != null) {
            verifyNoMoreInteractions(mockHostObserver);
        }
    }

    private void buildHost(@Nullable HealthIndicator healthIndicator) {
        host = new DefaultHost<>("lbDescription", DEFAULT_ADDRESS, connectionFactory, Integer.MAX_VALUE,
                mockHostObserver, healthCheckConfig, healthIndicator);
    }

    private void buildHost() {
        buildHost(null);
    }

    @Test
    void hostCreatedEvents() {
        buildHost();
        verify(mockHostObserver, times(1)).onHostCreated(DEFAULT_ADDRESS);
        // make another one, just for good measure.
        new DefaultHost<>("lbDescription", "address2", connectionFactory, Integer.MAX_VALUE,
                mockHostObserver, healthCheckConfig, null);
        verify(mockHostObserver, times(1)).onHostCreated("address2");
    }

    @Test
    void activeHostClosed() throws Exception {
        buildHost();
        verify(mockHostObserver, times(1)).onHostCreated(DEFAULT_ADDRESS);
        host.closeAsyncGracefully().toFuture().get();
        verify(mockHostObserver, times(1)).onActiveHostRemoved(DEFAULT_ADDRESS, 0);
        assertThat(host.onClose().toFuture().isDone(), is(true));
    }

    @Test
    void activeHostExpires() {
        buildHost();
        verify(mockHostObserver, times(1)).onHostCreated(DEFAULT_ADDRESS);
        host.markExpired();
        verify(mockHostObserver, times(1)).onHostMarkedExpired(DEFAULT_ADDRESS, 0);
        verify(mockHostObserver, times(1)).onExpiredHostRemoved(DEFAULT_ADDRESS, 0);
        assertThat(host.onClose().toFuture().isDone(), is(true));
    }

    @Test
    void expiredHostClosesAfterLastConnectionClosed() throws Exception {
        buildHost();
        verify(mockHostObserver, times(1)).onHostCreated(DEFAULT_ADDRESS);
        TestLoadBalancedConnection cxn = host.newConnection(any(), false, null).toFuture().get();
        host.markExpired();
        verify(mockHostObserver, times(1)).onHostMarkedExpired(DEFAULT_ADDRESS, 1);
        assertThat(host.onClose().toFuture().isDone(), is(false));
        cxn.closeAsync().toFuture().get();
        assertThat(host.onClose().toFuture().isDone(), is(true));
        verify(mockHostObserver).onExpiredHostRemoved(DEFAULT_ADDRESS, 0);
        // shouldn't able to revive it.
        assertThat(host.markActiveIfNotClosed(), is(false));
    }

    @Test
    void expiredHostRevives() throws Exception {
        buildHost();
        verify(mockHostObserver, times(1)).onHostCreated(DEFAULT_ADDRESS);
        host.newConnection(any(), false, null).toFuture().get();
        host.markExpired();
        verify(mockHostObserver, times(1))
                .onHostMarkedExpired(DEFAULT_ADDRESS, 1);
        assertThat(host.onClose().toFuture().isDone(), is(false));
        assertThat(host.markActiveIfNotClosed(), is(true));
        verify(mockHostObserver).onExpiredHostRevived(DEFAULT_ADDRESS, 1);
    }

    @Test
    void l4ConsecutiveFailuresAreDetected() throws Exception {
        TestLoadBalancedConnection testLoadBalancedConnection = TestLoadBalancedConnection.mockConnection(
                DEFAULT_ADDRESS);
        UnhealthyHostConnectionFactory unhealthyHostConnectionFactory = new UnhealthyHostConnectionFactory(
                DEFAULT_ADDRESS, 1, succeeded(testLoadBalancedConnection));
        connectionFactory = unhealthyHostConnectionFactory.createFactory();
        healthCheckConfig = new HealthCheckConfig(testExecutor,
                Duration.ofSeconds(1),
                Duration.ZERO,
                DEFAULT_HEALTH_CHECK_FAILED_CONNECTIONS_THRESHOLD,
                Duration.ofMillis(1),
                Duration.ZERO);
        buildHost();
        verify(mockHostObserver, times(1)).onHostCreated(DEFAULT_ADDRESS);
        for (int i = 0; i < DEFAULT_HEALTH_CHECK_FAILED_CONNECTIONS_THRESHOLD; i++) {
            assertThrows(ExecutionException.class,
                    () -> host.newConnection(any(), false, null).toFuture().get());
        }
        verify(mockHostObserver, times(1)).onHostMarkedUnhealthy(DEFAULT_ADDRESS, UNHEALTHY_HOST_EXCEPTION);

        // now revive and we should see the event and be able to get the connection.
        unhealthyHostConnectionFactory.advanceTime(testExecutor);
        assertThat(host.newConnection(any(), false, null).toFuture().get(),
                is(testLoadBalancedConnection));
        verify(mockHostObserver, times(1)).onHostRevived(DEFAULT_ADDRESS);
    }

    @Test
    void hostStatus() throws Exception {
        TestLoadBalancedConnection testLoadBalancedConnection = TestLoadBalancedConnection.mockConnection(
                DEFAULT_ADDRESS);
        UnhealthyHostConnectionFactory unhealthyHostConnectionFactory = new UnhealthyHostConnectionFactory(
                DEFAULT_ADDRESS, 1, succeeded(testLoadBalancedConnection));
        connectionFactory = unhealthyHostConnectionFactory.createFactory();
        healthCheckConfig = new HealthCheckConfig(testExecutor,
                Duration.ofSeconds(1),
                Duration.ZERO,
                DEFAULT_HEALTH_CHECK_FAILED_CONNECTIONS_THRESHOLD,
                Duration.ofMillis(1),
                Duration.ZERO);
        buildHost();
        verify(mockHostObserver, times(1)).onHostCreated(DEFAULT_ADDRESS);

        assertThat(host.isHealthy(), is(true));
        assertThat(host.canMakeNewConnections(), is(true));

        for (int i = 0; i < DEFAULT_HEALTH_CHECK_FAILED_CONNECTIONS_THRESHOLD; i++) {
            assertThrows(ExecutionException.class,
                    () -> host.newConnection(any(), false, null).toFuture().get());
        }
        verify(mockHostObserver, times(1)).onHostMarkedUnhealthy(DEFAULT_ADDRESS, UNHEALTHY_HOST_EXCEPTION);
        assertThat(host.isHealthy(), is(false));
        assertThat(host.canMakeNewConnections(), is(true));

        // now revive and we should see the event and be able to get the connection.
        unhealthyHostConnectionFactory.advanceTime(testExecutor);
        verify(mockHostObserver, times(1)).onHostRevived(DEFAULT_ADDRESS);
        assertThat(host.isHealthy(), is(true));
        assertThat(host.canMakeNewConnections(), is(true));

        host.markExpired();
        verify(mockHostObserver, times(1)).onHostMarkedExpired(DEFAULT_ADDRESS, 1);
        assertThat(host.isHealthy(), is(true));
        assertThat(host.canMakeNewConnections(), is(false));

        host.closeAsync().toFuture().get();
        verify(mockHostObserver, times(1)).onExpiredHostRemoved(DEFAULT_ADDRESS, 1);
        assertThat(host.isHealthy(), is(false));
        assertThat(host.canMakeNewConnections(), is(false));
    }

    @Test
    void healthIndicatorInfluencesHealthStatus() {
        HealthIndicator healthIndicator = mock(HealthIndicator.class);
        when(healthIndicator.isHealthy()).thenReturn(true);
        buildHost(healthIndicator);
        verify(mockHostObserver, times(1)).onHostCreated("address");
        assertThat(host.isHealthy(), is(true));
        when(healthIndicator.isHealthy()).thenReturn(false);
        assertThat(host.isHealthy(), is(false));
    }

    @Test
    void forwardsHealthIndicatorScore() {
        HealthIndicator healthIndicator = mock(HealthIndicator.class);
        when(healthIndicator.score()).thenReturn(10);
        buildHost(healthIndicator);
        verify(mockHostObserver, times(1)).onHostCreated("address");
        assertThat(host.score(), is(10));
        verify(healthIndicator, times(1)).score();
    }

    @Test
    void connectFailuresAreForwardedToHealthIndicator() {
        connectionFactory = new TestConnectionFactory(address -> failed(DELIBERATE_EXCEPTION));
        HealthIndicator healthIndicator = mock(HealthIndicator.class);
        buildHost(healthIndicator);
        verify(mockHostObserver, times(1)).onHostCreated("address");
        Throwable underlying = assertThrows(ExecutionException.class, () ->
                host.newConnection(cxn -> true, false, null).toFuture().get()).getCause();
        assertEquals(DELIBERATE_EXCEPTION, underlying);
        verify(healthIndicator, times(1)).currentTimeNanos();
        verify(healthIndicator, times(1)).onConnectFailure(0L);
    }
}

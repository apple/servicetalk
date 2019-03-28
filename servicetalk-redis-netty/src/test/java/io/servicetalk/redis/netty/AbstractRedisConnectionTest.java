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
package io.servicetalk.redis.netty;

import io.servicetalk.concurrent.api.Completable;
import io.servicetalk.concurrent.api.Executor;
import io.servicetalk.concurrent.api.LegacyTestCompletable;
import io.servicetalk.concurrent.api.Publisher;
import io.servicetalk.redis.api.RedisData;
import io.servicetalk.redis.api.RedisRequest;
import io.servicetalk.tcp.netty.internal.TcpClientConfig;
import io.servicetalk.transport.api.ConnectionContext;
import io.servicetalk.transport.api.ExecutionContext;

import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentMatchers;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static io.servicetalk.buffer.netty.BufferAllocators.DEFAULT_ALLOCATOR;
import static io.servicetalk.concurrent.api.Completable.completed;
import static io.servicetalk.concurrent.api.Completable.never;
import static io.servicetalk.concurrent.api.Publisher.failed;
import static io.servicetalk.concurrent.internal.DeliberateException.DELIBERATE_EXCEPTION;
import static io.servicetalk.redis.api.RedisProtocolSupport.Command.AUTH;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.core.Is.is;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public final class AbstractRedisConnectionTest {

    private ConcurrentLinkedQueue<LegacyTestCompletable> timers;
    private Connection connection;

    @Before
    public void setUp() {
        ExecutionContext executionContext = mock(ExecutionContext.class);
        when(executionContext.bufferAllocator()).thenReturn(DEFAULT_ALLOCATOR);
        timers = new ConcurrentLinkedQueue<>();
        RedisClientConfig config = new RedisClientConfig(new TcpClientConfig(true));
        config.maxPipelinedRequests(10);
        Executor pingTimerProvider = mock(Executor.class);
        when(pingTimerProvider.timer(anyLong(), ArgumentMatchers.any(TimeUnit.class))).thenAnswer(inv -> {
            LegacyTestCompletable timer = new LegacyTestCompletable();
            timers.add(timer);
            return timer;
        });
        connection = new Connection(pingTimerProvider, executionContext, config.asReadOnly());
        connection.startPings();
        assertThat("Unexpected ping timers found.", timers, hasSize(1));
    }

    @Test
    public void testTimerTrigger() {
        timers.remove().onComplete();
        assertThat("Unexpected ping timers found post timer trigger.", timers, hasSize(1));
        assertThat("Ping not requested on timer complete.", connection.pings, hasSize(1));
        assertThat("Ping not sent on timer complete.", connection.pingCount.get(), is(1));
        connection.pings.remove().onComplete();
    }

    @Test
    public void testPingRejected() {
        timers.remove().onComplete();
        assertThat("Unexpected ping timers found post timer trigger.", timers, hasSize(1));
        assertThat("Ping not requested on timer complete.", connection.pings, hasSize(1));
        assertThat("Ping not sent on timer complete.", connection.pingCount.get(), is(1));
        connection.pings.remove().onError(new PingRejectedException(AUTH));
        timers.remove().onComplete();
        assertThat("Unexpected ping timers found post timer trigger.", timers, hasSize(1));
        assertThat("Ping not requested on timer complete.", connection.pings, hasSize(1));
        assertThat("Ping not sent on timer complete.", connection.pingCount.get(), is(2));
        connection.pings.remove().onComplete();
    }

    @Test
    public void testPingFail() {
        timers.remove().onComplete();
        assertThat("Unexpected ping timers found post timer trigger.", timers, hasSize(1));
        assertThat("Ping not requested on timer complete.", connection.pings, hasSize(1));
        assertThat("Ping not sent on timer complete.", connection.pingCount.get(), is(1));
        connection.pings.remove().onError(DELIBERATE_EXCEPTION);
        timers.remove().verifyCancelled();
        assertThat("Fatal ping error did not close connection.", connection.closed.get(), is(true));
        assertThat("Unexpected ping requests.", connection.pings, hasSize(0));
    }

    @Test
    public void testPingPendingTimerTriggers() {
        timers.remove().onComplete();
        assertThat("Unexpected ping timers found post timer trigger.", timers, hasSize(1));
        assertThat("Ping not requested on timer complete.", connection.pings, hasSize(1));
        assertThat("Ping not sent on timer complete.", connection.pingCount.get(), is(1));
        timers.remove().onComplete();
        assertThat("Missing ping response did not close connection.", connection.closed.get(), is(true));
        assertThat("Unexpected ping timers found post timer trigger.", timers, hasSize(0));
        connection.pings.remove().verifyCancelled();
    }

    private static final class Connection extends AbstractRedisConnection {

        private static final Logger LOGGER = LoggerFactory.getLogger(Connection.class);

        private final ConnectionContext context = mock(ConnectionContext.class);
        private final AtomicInteger pingCount = new AtomicInteger();
        private final AtomicBoolean closed = new AtomicBoolean();
        private final ConcurrentLinkedQueue<LegacyTestCompletable> pings = new ConcurrentLinkedQueue<>();

        protected Connection(Executor pingTimerProvider,
                             ExecutionContext executionContext,
                             ReadOnlyRedisClientConfig roConfig) {
            super(pingTimerProvider, never(), executionContext, roConfig);
        }

        @Override
        Completable doClose() {
            return completed().beforeOnSubscribe(cancellable -> closed.set(true));
        }

        @Override
        Completable sendPing() {
            LegacyTestCompletable response = new LegacyTestCompletable();
            pings.add(response);
            return response.beforeOnSubscribe(cancellable -> pingCount.incrementAndGet());
        }

        @Override
        Logger logger() {
            return LOGGER;
        }

        @Override
        public ConnectionContext connectionContext() {
            return context;
        }

        @Override
        public Publisher<RedisData> handleRequest(RedisRequest request) {
            return failed(new UnsupportedOperationException("Not implemented."));
        }

        @Override
        public Completable onClose() {
            return completed();
        }
    }
}

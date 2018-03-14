/**
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
import io.servicetalk.concurrent.api.Publisher;
import io.servicetalk.concurrent.api.TestCompletable;
import io.servicetalk.redis.api.RedisData;
import io.servicetalk.redis.api.RedisProtocolSupport;
import io.servicetalk.redis.api.RedisRequest;
import io.servicetalk.tcp.netty.internal.TcpClientConfig;
import io.servicetalk.transport.api.ConnectionContext;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.LongFunction;

import static io.servicetalk.concurrent.api.Completable.completed;
import static io.servicetalk.concurrent.api.DeliberateException.DELIBERATE_EXCEPTION;
import static io.servicetalk.concurrent.api.Publisher.error;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.core.Is.is;

public final class AbstractRedisConnectionTest {

    private ConcurrentLinkedQueue<TestCompletable> timers;
    private Connection connection;

    @Before
    public void setUp() {
        timers = new ConcurrentLinkedQueue<>();
        RedisClientConfig config = new RedisClientConfig(new TcpClientConfig(true));
        config.setMaxPipelinedRequests(10);
        connection = new Connection(aLong -> {
            TestCompletable timer = new TestCompletable();
            timers.add(timer);
            return timer;
        }, config.asReadOnly());
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
        connection.pings.remove().onError(new PingRejectedException(RedisProtocolSupport.Command.AUTH));
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

        private final ConnectionContext context = Mockito.mock(ConnectionContext.class);
        private final AtomicInteger pingCount = new AtomicInteger();
        private final AtomicBoolean closed = new AtomicBoolean();
        private final ConcurrentLinkedQueue<TestCompletable> pings = new ConcurrentLinkedQueue<>();

        protected Connection(LongFunction<Completable> timer, ReadOnlyRedisClientConfig roConfig) {
            super(timer, roConfig);
        }

        @Override
        Completable doClose() {
            return completed().doBeforeSubscribe(cancellable -> closed.set(true));
        }

        @Override
        Completable sendPing() {
            TestCompletable response = new TestCompletable();
            pings.add(response);
            return response.doBeforeSubscribe(cancellable -> pingCount.incrementAndGet());
        }

        @Override
        Logger getLogger() {
            return LOGGER;
        }

        @Override
        public ConnectionContext getConnectionContext() {
            return context;
        }

        @Override
        public Publisher<RedisData> request(RedisRequest request) {
            return error(new UnsupportedOperationException("Not implemented."));
        }

        @Override
        public Completable onClose() {
            return completed();
        }
    }
}

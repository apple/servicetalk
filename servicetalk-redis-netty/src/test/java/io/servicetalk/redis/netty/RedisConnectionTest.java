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

import io.servicetalk.buffer.api.Buffer;
import io.servicetalk.concurrent.Cancellable;
import io.servicetalk.concurrent.CompletableSource;
import io.servicetalk.concurrent.PublisherSource.Subscription;
import io.servicetalk.concurrent.api.Completable;
import io.servicetalk.concurrent.api.Publisher;
import io.servicetalk.redis.api.RedisClient;
import io.servicetalk.redis.api.RedisCommander;
import io.servicetalk.redis.api.RedisConnection;
import io.servicetalk.redis.api.RedisData;
import io.servicetalk.redis.api.RedisData.ArraySize;
import io.servicetalk.redis.api.RedisData.CompleteBulkString;
import io.servicetalk.redis.api.RedisExecutionStrategy;
import io.servicetalk.redis.api.RedisRequest;
import io.servicetalk.redis.api.RedisServerException;
import io.servicetalk.transport.api.ConnectionContext;
import io.servicetalk.transport.api.ExecutionContext;

import org.junit.Test;

import java.net.InetSocketAddress;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static io.servicetalk.concurrent.api.BlockingTestUtils.awaitIndefinitely;
import static io.servicetalk.concurrent.api.Publisher.just;
import static io.servicetalk.concurrent.internal.ServiceTalkTestTimeout.DEFAULT_TIMEOUT_SECONDS;
import static io.servicetalk.redis.api.RedisData.NULL;
import static io.servicetalk.redis.api.RedisData.OK;
import static io.servicetalk.redis.api.RedisData.PONG;
import static io.servicetalk.redis.api.RedisData.QUEUED;
import static io.servicetalk.redis.api.RedisProtocolSupport.Command.DISCARD;
import static io.servicetalk.redis.api.RedisProtocolSupport.Command.ECHO;
import static io.servicetalk.redis.api.RedisProtocolSupport.Command.EVAL;
import static io.servicetalk.redis.api.RedisProtocolSupport.Command.EXEC;
import static io.servicetalk.redis.api.RedisProtocolSupport.Command.HGETALL;
import static io.servicetalk.redis.api.RedisProtocolSupport.Command.HMSET;
import static io.servicetalk.redis.api.RedisProtocolSupport.Command.LPOP;
import static io.servicetalk.redis.api.RedisProtocolSupport.Command.MULTI;
import static io.servicetalk.redis.api.RedisProtocolSupport.Command.PING;
import static io.servicetalk.redis.api.RedisProtocolSupport.Command.SET;
import static io.servicetalk.redis.api.RedisRequests.newRequest;
import static io.servicetalk.redis.netty.RedisDataMatcher.redisArraySize;
import static io.servicetalk.redis.netty.RedisDataMatcher.redisCompleteBulkString;
import static io.servicetalk.redis.netty.RedisDataMatcher.redisError;
import static io.servicetalk.redis.netty.RedisDataMatcher.redisNull;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.instanceOf;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;
import static org.hamcrest.Matchers.startsWith;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;

public class RedisConnectionTest extends BaseRedisClientTest {
    @Test
    public void recoverableError() throws Exception {
        final RedisRequest evalRequest = newRequest(EVAL);

        assertThat(awaitIndefinitely(getEnv().client.reserveConnection(EVAL)
                        .flatMapPublisher(cnx -> cnx.request(evalRequest).concatWith(cnx.request(newRequest(PING))))),
                contains(redisError(startsWith("ERR")), is(PONG)));
    }

    @Test
    public void unrecoverableError() throws Exception {
        final Buffer reqBuf = getEnv().client.executionContext().bufferAllocator().fromAscii("*1\r\n+PING\r\n");

        thrown.expect(ExecutionException.class);
        thrown.expectCause(is(instanceOf(RedisServerException.class)));
        awaitIndefinitely(getEnv().client.reserveConnection(PING).flatMap(cnx ->
                cnx.request(newRequest(PING, reqBuf), Buffer.class)));
    }

    @Test
    public void singleCancel() throws Exception {
        assertThat(awaitIndefinitely(getEnv().client.reserveConnection(PING)
                        .flatMap(cnx -> cnx.request(newRequest(PING)).first())),
                is(PONG));
    }

    @Test
    public void internalCancel() throws Exception {
        final RedisRequest pingRequest = newRequest(PING);

        assertThat(awaitIndefinitely(getEnv().client.reserveConnection(PING)
                        .flatMapPublisher(cnx -> cnx.request(pingRequest)
                                // concatWith triggers an internal cancel when switching publishers
                                .concatWith(cnx.request(newRequest(PING, new CompleteBulkString(buf("my-pong"))))))),
                contains(is(PONG), redisCompleteBulkString(buf("my-pong"))));
    }

    @Test
    public void userCancel() throws Exception {
        getEnv().client.request(newRequest(HMSET,
                new CompleteBulkString(buf("hmkey")),
                new CompleteBulkString(buf("name")),
                new CompleteBulkString(buf("value"))
        )).toFuture().get();

        final RedisRequest getRequest = newRequest(HGETALL, new CompleteBulkString(buf("hmkey")));

        final CountDownLatch cnxClosedLatch = new CountDownLatch(1);
        final AtomicReference<Throwable> cnxCloseError = new AtomicReference<>();

        getEnv().client.reserveConnection(PING)
                .flatMapPublisher(cnx -> {
                    cnx.connectionContext().onClose().subscribe(new CompletableSource.Subscriber() {
                        @Override
                        public void onSubscribe(final Cancellable cancellable) {
                        }

                        @Override
                        public void onComplete() {
                            cnxClosedLatch.countDown();
                        }

                        @Override
                        public void onError(final Throwable t) {
                            cnxCloseError.set(t);
                            cnxClosedLatch.countDown();
                        }
                    });
                    return cnx.request(getRequest);
                }).subscribe(
                new io.servicetalk.concurrent.PublisherSource.Subscriber<RedisData>() {
                    private Subscription s;

                    @Override
                    public void onSubscribe(final Subscription s) {
                        this.s = s;
                        s.request(1L);
                    }

                    @Override
                    public void onNext(final RedisData redisData) {
                        s.cancel();
                    }

                    @Override
                    public void onError(final Throwable t) {
                        // NOOP
                    }

                    @Override
                    public void onComplete() {
                        // NOOP
                    }
                });

        assertThat(cnxClosedLatch.await(DEFAULT_TIMEOUT_SECONDS, SECONDS), is(true));
        assertThat(cnxCloseError.get(), is(nullValue()));
    }

    @Test
    public void transactionEmpty() throws Exception {
        final RedisRequest multiRequest = newRequest(MULTI);

        final List<RedisData> results = awaitIndefinitely(getEnv().client.reserveConnection(MULTI)
                .flatMapPublisher(cnx -> cnx.request(multiRequest)
                        .concatWith(cnx.request(newRequest(EXEC)))));

        assertThat(results, contains(is(OK), redisArraySize(0L)));
    }

    @Test
    public void transactionExec() throws Exception {
        final RedisRequest multiRequest = newRequest(MULTI);

        final List<RedisData> results = awaitIndefinitely(getEnv().client.reserveConnection(MULTI)
                .flatMapPublisher(cnx -> cnx.request(multiRequest)
                        .concatWith(cnx.request(newRequest(ECHO, new CompleteBulkString(buf("foo")))))
                        .concatWith(Publisher.defer(() -> {
                            // We suspend twice longer than the ping period to ensure at least one ping would make its
                            // way on the connection if the Pinger suspend/resume mechanism for MULTI failed
                            try {
                                Thread.sleep(SECONDS.toMillis(2 * PING_PERIOD_SECONDS));
                            } catch (InterruptedException e) {
                                throw new RuntimeException(e);
                            }
                            return just(NULL);
                        }))
                        .concatWith(cnx.request(newRequest(EXEC)))));

        assertThat(results, contains(is(OK), is(QUEUED), is(redisNull()), redisArraySize(1L),
                is(redisCompleteBulkString(buf("foo")))));
    }

    @Test
    public void transactionDiscard() throws Exception {
        final RedisRequest multiRequest = newRequest(MULTI);

        final List<RedisData> results = awaitIndefinitely(getEnv().client.reserveConnection(MULTI)
                .flatMapPublisher(cnx -> cnx.request(multiRequest)
                        .concatWith(cnx.request(newRequest(PING)))
                        .concatWith(cnx.request(newRequest(DISCARD)))));

        assertThat(results, contains(is(OK), is(QUEUED), is(OK)));
    }

    @Test
    public void transactionCompleteFailure() throws Exception {
        final RedisRequest multiRequest = newRequest(MULTI);

        final List<RedisData> results = awaitIndefinitely(getEnv().client.reserveConnection(MULTI)
                .flatMapPublisher(cnx -> cnx.request(multiRequest)
                        .concatWith(cnx.request(newRequest(EVAL)))
                        .concatWith(cnx.request(newRequest(PING)))
                        .concatWith(cnx.request(newRequest(EXEC)))));

        assertThat(results, contains(is(OK), redisError(startsWith("ERR")), is(QUEUED),
                redisError(startsWith("EXECABORT"))));
    }

    @Test
    public void transactionPartialFailure() throws Exception {
        final RedisRequest multiRequest = newRequest(MULTI);

        final List<RedisData> results = awaitIndefinitely(getEnv().client.reserveConnection(MULTI)
                .flatMapPublisher(cnx -> cnx.request(multiRequest)
                        .concatWith(cnx.request(newRequest(SET,
                                Publisher.from(
                                        new ArraySize(3L),
                                        SET,
                                        new CompleteBulkString(buf("ptf-rccsk")),
                                        new CompleteBulkString(buf("foo"))))))
                        .concatWith(cnx.request(newRequest(LPOP, new CompleteBulkString(buf("ptf-rccsk")))))
                        .concatWith(cnx.request(newRequest(EXEC)))));

        assertThat(results, contains(is(OK), is(QUEUED), is(QUEUED), redisArraySize(2L), is(OK),
                redisError(startsWith("WRONGTYPE"))));
    }

    @Test
    public void reserveAndRelease() throws Exception {
        awaitIndefinitely(getEnv().client.reserveConnection(PING)
                .flatMapCompletable(RedisClient.ReservedRedisConnection::releaseAsync));
        awaitIndefinitely(getEnv().client.reserveConnection(PING));
    }

    @Test
    public void redisCommanderUsesFilters() throws Exception {
        final RedisConnection delegate = awaitIndefinitely(getEnv().client.reserveConnection(PING));
        final AtomicBoolean requestCalled = new AtomicBoolean();
        final AtomicBoolean closeCalled = new AtomicBoolean();
        RedisConnection filteredConnection = new TestFilterRedisConnection(delegate, requestCalled, closeCalled);

        RedisCommander commander = filteredConnection.asCommander();

        assertThat(awaitIndefinitely(commander.ping()), is("PONG"));
        assertTrue(requestCalled.get());

        // Don't subscribe because we don't actually do the close, but instead just verify the method was called.
        commander.closeAsync();
        assertTrue(closeCalled.get());
    }

    @Test
    public void rawConnectionToCommanderWithFilterAndMonitorDoesNotThrowClassCast() throws Exception {
        rawConnectionToCommanderWithFilterDoesNotThrowClassCast(true);
    }

    @Test
    public void rawConnectionToCommanderWithFilterAndMultiDoesNotThrowClassCast() throws Exception {
        rawConnectionToCommanderWithFilterDoesNotThrowClassCast(false);
    }

    private void rawConnectionToCommanderWithFilterDoesNotThrowClassCast(boolean monitor)
            throws ExecutionException, InterruptedException {
        final RedisTestEnvironment env = getEnv();
        RedisConnection rawConnection =
                awaitIndefinitely(DefaultRedisConnectionBuilder.<InetSocketAddress>forPipeline()
                        .ioExecutor(env.ioExecutor)
                        .executor(env.executor)
                        .build(new InetSocketAddress(env.redisHost, env.redisPort)));
        try {
            final AtomicBoolean requestCalled = new AtomicBoolean();
            final AtomicBoolean closeCalled = new AtomicBoolean();
            RedisConnection filteredConnection = new TestFilterRedisConnection(rawConnection, requestCalled,
                    closeCalled);

            RedisCommander commander = filteredConnection.asCommander();

            if (monitor) {
                assertNotNull(awaitIndefinitely(commander.monitor().first()));
            } else {
                assertNotNull(awaitIndefinitely(commander.multi()));
            }
            assertTrue(requestCalled.get());

            // Don't subscribe because we don't actually do the close, but instead just verify the method was called.
            commander.closeAsync();
            assertTrue(closeCalled.get());
        } finally {
            rawConnection.closeAsync().subscribe();
        }
    }

    private static final class TestFilterRedisConnection extends RedisConnection {
        private final RedisConnection delegate;
        private final AtomicBoolean requestCalled;
        private final AtomicBoolean closeCalled;

        TestFilterRedisConnection(RedisConnection delegate,
                                  AtomicBoolean requestCalled,
                                  AtomicBoolean closeCalled) {
            this.delegate = delegate;
            this.requestCalled = requestCalled;
            this.closeCalled = closeCalled;
        }

        @Override
        public ConnectionContext connectionContext() {
            return delegate.connectionContext();
        }

        @Override
        public <T> Publisher<T> settingStream(SettingKey<T> settingKey) {
            return delegate.settingStream(settingKey);
        }

        @Override
        public Publisher<RedisData> request(RedisExecutionStrategy strategy, RedisRequest request) {
            requestCalled.set(true);
            return delegate.request(strategy, request);
        }

        @Override
        public ExecutionContext executionContext() {
            return delegate.executionContext();
        }

        @Override
        public Completable onClose() {
            return delegate.onClose();
        }

        @Override
        public Completable closeAsync() {
            closeCalled.set(true);
            return delegate.closeAsync();
        }

        @Override
        public Completable closeAsyncGracefully() {
            closeCalled.set(true);
            return delegate.closeAsyncGracefully();
        }
    }
}

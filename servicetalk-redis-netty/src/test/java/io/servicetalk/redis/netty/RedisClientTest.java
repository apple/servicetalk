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
import io.servicetalk.concurrent.api.Completable;
import io.servicetalk.concurrent.api.Publisher;
import io.servicetalk.concurrent.api.Single;
import io.servicetalk.redis.api.BufferRedisCommander;
import io.servicetalk.redis.api.RedisClient;
import io.servicetalk.redis.api.RedisClient.ReservedRedisConnection;
import io.servicetalk.redis.api.RedisCommander;
import io.servicetalk.redis.api.RedisData;
import io.servicetalk.redis.api.RedisData.ArraySize;
import io.servicetalk.redis.api.RedisData.CompleteBulkString;
import io.servicetalk.redis.api.RedisData.DefaultBulkStringChunk;
import io.servicetalk.redis.api.RedisData.DefaultFirstBulkStringChunk;
import io.servicetalk.redis.api.RedisData.FirstBulkStringChunk;
import io.servicetalk.redis.api.RedisData.RequestRedisData;
import io.servicetalk.redis.api.RedisExecutionStrategy;
import io.servicetalk.redis.api.RedisProtocolSupport.Command;
import io.servicetalk.redis.api.RedisRequest;
import io.servicetalk.redis.api.RedisServerException;
import io.servicetalk.redis.internal.RedisUtils.ListWithBuffersCoercedToCharSequences;
import io.servicetalk.transport.api.ExecutionContext;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.atomic.AtomicBoolean;

import static io.servicetalk.buffer.api.EmptyBuffer.EMPTY_BUFFER;
import static io.servicetalk.concurrent.internal.Await.awaitIndefinitely;
import static io.servicetalk.concurrent.internal.Await.awaitIndefinitelyNonNull;
import static io.servicetalk.redis.api.RedisProtocolSupport.Command.CLIENT;
import static io.servicetalk.redis.api.RedisProtocolSupport.Command.COMMAND;
import static io.servicetalk.redis.api.RedisProtocolSupport.Command.GET;
import static io.servicetalk.redis.api.RedisProtocolSupport.Command.OBJECT;
import static io.servicetalk.redis.api.RedisProtocolSupport.Command.PING;
import static io.servicetalk.redis.api.RedisProtocolSupport.Command.QUIT;
import static io.servicetalk.redis.api.RedisProtocolSupport.Command.SET;
import static io.servicetalk.redis.api.RedisProtocolSupport.SetCondition.NX;
import static io.servicetalk.redis.api.RedisProtocolSupport.SetExpire.EX;
import static io.servicetalk.redis.api.RedisProtocolSupport.SubCommand.ENCODING;
import static io.servicetalk.redis.api.RedisProtocolSupport.SubCommand.INFO;
import static io.servicetalk.redis.api.RedisProtocolSupport.SubCommand.LIST;
import static io.servicetalk.redis.api.RedisRequests.newRequest;
import static io.servicetalk.redis.netty.RedisDataMatcher.redisError;
import static io.servicetalk.redis.netty.RedisDataMatcher.redisFirstBulkStringChunk;
import static io.servicetalk.redis.netty.RedisDataMatcher.redisFirstBulkStringChunkSize;
import static io.servicetalk.redis.netty.RedisDataMatcher.redisNull;
import static io.servicetalk.redis.netty.RedisDataMatcher.redisSimpleString;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.hamcrest.Matchers.anyOf;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.instanceOf;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.startsWith;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class RedisClientTest extends BaseRedisClientTest {
    @Test
    public void gracefulTerminationOnQuit() throws Exception {
        assertThat(awaitIndefinitely(getEnv().client.reserveConnection(PING).flatMapPublisher(conn ->
                conn.request(newRequest(QUIT)).doAfterFinally(() -> conn.releaseAsync()))),
                contains(redisSimpleString("OK")));
    }

    @Test
    public void closeAsync() throws Exception {
        ReservedRedisConnection connection = awaitIndefinitelyNonNull(getEnv().client.reserveConnection(PING));
        awaitIndefinitely(connection.closeAsync());
    }

    @Test
    public void closeAsyncViaContext() throws Exception {
        ReservedRedisConnection connection = awaitIndefinitelyNonNull(getEnv().client.reserveConnection(PING));
        awaitIndefinitely(connection.connectionContext().closeAsync());
    }

    @Test
    public void closeAsyncGracefully() throws Exception {
        ReservedRedisConnection connection = awaitIndefinitelyNonNull(getEnv().client.reserveConnection(PING));
        awaitIndefinitely(connection.closeAsyncGracefully());
    }

    @Test
    public void closeAsyncGracefullyViaContext() throws Exception {
        ReservedRedisConnection connection = awaitIndefinitelyNonNull(getEnv().client.reserveConnection(PING));
        awaitIndefinitely(connection.connectionContext().closeAsyncGracefully());
    }

    @Test
    public void requestResponse() throws Exception {
        assertThat(awaitIndefinitely(getEnv().client.request(newRequest(PING))), contains(redisSimpleString("PONG")));
        assertThat(awaitIndefinitely(getEnv().client.request(newRequest(PING, new CompleteBulkString(buf("my-pong"))))),
                contains(redisFirstBulkStringChunk(buf("my-pong"))));
        assertThat(awaitIndefinitely(getEnv().client.request(newRequest(PING, new CompleteBulkString(buf(""))))),
                contains(redisFirstBulkStringChunk(buf(""))));
        assertThat(awaitIndefinitely(getEnv().client.request(newRequest(GET,
                new CompleteBulkString(buf("missing-key"))))), contains(redisNull()));

        assertThat(awaitIndefinitely(getEnv().client.request(newRequest(SET,
                Publisher.from(
                        new ArraySize(6L),
                        SET,
                        new CompleteBulkString(buf("exp-key")),
                        new CompleteBulkString(buf("exp-value")),
                        EX, RedisData.Integer.newInstance(5L),
                        NX)))),
                contains(anyOf(redisNull(), redisSimpleString("OK"))));

        assertThat(awaitIndefinitely(getEnv().client.request(newRequest(new RedisData.Array<>(PING,
                        new CompleteBulkString(buf("my-pong")))))),
                contains(redisFirstBulkStringChunk(buf("my-pong"))));
    }

    @Test
    public void unicodeNotMangled() throws Exception {
        assertThat(awaitIndefinitely(getEnv().client.request(newRequest(SET,
                Publisher.from(
                        new ArraySize(3L),
                        SET,
                        new CompleteBulkString(buf("\u263A-rc")),
                        new CompleteBulkString(buf("\u263A-foo")))))), contains(redisSimpleString("OK")));

        assertThat(awaitIndefinitely(getEnv().client.request(newRequest(GET,
                new CompleteBulkString(buf("\u263A-rc"))))), contains(redisFirstBulkStringChunk(buf("\u263A-foo"))));
    }

    @Test
    public void chunkedRequestPing() throws Exception {
        final RequestRedisData[] args = new RequestRedisData[103];
        args[0] = new ArraySize(2L);
        args[1] = PING;
        final StringBuilder expected = new StringBuilder(1000);
        args[2] = new DefaultFirstBulkStringChunk(EMPTY_BUFFER, 1000);
        for (int i = 0; i < 99; i++) {
            expected.append("0123456789");
            args[3 + i] = new DefaultBulkStringChunk(buf("0123456789"));
        }
        expected.append("THISISEND!");
        args[102] = new DefaultBulkStringChunk(buf("THISISEND!"));

        final RedisRequest request = newRequest(PING, Publisher.from(args));

        final String responseData = awaitIndefinitelyNonNull(getEnv().client.request(request).reduce(StringBuilder::new,
          (r, d) -> {
              if (d instanceof FirstBulkStringChunk) {
                  assertThat(((FirstBulkStringChunk) d).bulkStringLength(), is(1000));
              }
              r.append(d.getBufferValue().toString(UTF_8));
            return r;
        })).toString();

        assertThat(responseData, is(expected.toString()));
    }

    @Test
    public void chunkedRequestSet() throws Exception {
        final RequestRedisData[] args = new RequestRedisData[104];
        args[0] = new ArraySize(3L);
        args[1] = SET;
        args[2] = new CompleteBulkString(buf("key"));
        final StringBuilder expected = new StringBuilder(1000);
        args[3] = new DefaultFirstBulkStringChunk(EMPTY_BUFFER, 1000);
        for (int i = 0; i < 99; i++) {
            expected.append("0123456789");
            args[4 + i] = new DefaultBulkStringChunk(buf("0123456789"));
        }
        expected.append("THISISEND!");
        args[103] = new DefaultBulkStringChunk(buf("THISISEND!"));

        final RedisRequest setRequest = newRequest(SET, Publisher.from(args));

        final Collection<RedisData> setResponse = getEnv().client.request(setRequest).toFuture().get();
        assertEquals(Collections.singletonList(new RedisData.SimpleString("OK")), setResponse);

        final RedisRequest getRequest = newRequest(SET, Publisher.from(new ArraySize(2L), GET,
                new CompleteBulkString(buf("key"))));
        final String responseData = getEnv().client.request(getRequest).reduce(StringBuilder::new,
                (r, d) -> {
                    if (d instanceof FirstBulkStringChunk) {
                        assertThat(((FirstBulkStringChunk) d).bulkStringLength(), is(1000));
                    }
                    r.append(d.getBufferValue().toString(UTF_8));
                    return r;
                }).toFuture().get().toString();

        assertThat(responseData, is(expected.toString()));
    }

    @Test
    public void commandWithSubCommand() throws Exception {
        final RedisData actual = awaitIndefinitely(getEnv().client.request(newRequest(CLIENT, LIST)).first());
        assertThat(actual,
                is(redisFirstBulkStringChunkSize(greaterThan(0))));
        assertThat(awaitIndefinitelyNonNull(getEnv().client.request(newRequest(COMMAND, INFO,
                new CompleteBulkString(buf("GET"))), List.class)).size(), is(1));
        assertThat(awaitIndefinitelyNonNull(getEnv().client.request(newRequest(COMMAND, INFO,
                new CompleteBulkString(buf("GET")), new CompleteBulkString(buf("SET"))), List.class)).size(), is(2));
        assertThat(awaitIndefinitelyNonNull(getEnv().client.request(newRequest(OBJECT, ENCODING,
                new CompleteBulkString(buf("missing-key")))).first()), is(redisNull()));
    }

    @Test
    public void bufferRequest() throws Exception {
        Buffer reqBuf = getEnv().client.executionContext().bufferAllocator().newBuffer(33);
        Buffer pingBuf = getEnv().client.executionContext().bufferAllocator().newBuffer(PING.encodedByteCount());
        PING.encodeTo(pingBuf);
        reqBuf.writeAscii("*2\r\n")
                .writeBytes(pingBuf.duplicate())
                .writeAscii("$12\r\nbufreq-pong1\r\n");

        assertThat(awaitIndefinitely(getEnv().client.request(newRequest(PING, reqBuf), Buffer.class)),
                is(buf("bufreq-pong1")));

        reqBuf = getEnv().client.executionContext().bufferAllocator().newBuffer(33);
        reqBuf.writeAscii("*2\r\n")
                .writeBytes(pingBuf.duplicate())
                .writeAscii("$12\r\nbufreq-pong2\r\n");

        assertThat(awaitIndefinitely(getEnv().client.request(newRequest(PING, reqBuf), CharSequence.class)),
                is("bufreq-pong2"));
    }

    @Test
    public void unknownCommandNoCoercion() throws Exception {
        Buffer reqBuf = getEnv().client.executionContext().bufferAllocator().newBuffer(33);
        reqBuf.writeAscii("*2\r\n$6\r\nFOOBAR\r\n$12\r\nbufreq-pong1\r\n");

        // We use PING to build the request object, which doesn't matter here: FOOBAR is the actual command sent on the wire
        assertThat(awaitIndefinitely(getEnv().client.request(newRequest(PING, reqBuf)).first()),
                is(redisError(startsWith("ERR"))));
    }

    @Test
    public void unknownCommandAnyCoercion() throws Exception {
        Buffer reqBuf = getEnv().client.executionContext().bufferAllocator().newBuffer(33);
        reqBuf.writeAscii("*2\r\n$6\r\nFOOBAR\r\n$12\r\nbufreq-pong1\r\n");

        Class<?>[] coercionTypes = {CharSequence.class, Buffer.class, Long.class,
                ListWithBuffersCoercedToCharSequences.class, List.class};

        for (Class<?> coercionType : coercionTypes) {
            try {
                awaitIndefinitely(getEnv().client.request(newRequest(PING, reqBuf), coercionType));
                fail();
            } catch (ExecutionException e) {
                assertThat(e.getCause(), is(instanceOf(RedisServerException.class)));
            }
        }
    }

    @Test
    public void redisCommanderUsesFilters() throws Exception {
        final RedisClient delegate = getEnv().client;
        final AtomicBoolean requestCalled = new AtomicBoolean();
        final AtomicBoolean closeCalled = new AtomicBoolean();
        RedisClient filteredClient = new RedisClient() {
            @Override
            public Single<? extends ReservedRedisConnection> reserveConnection(RedisExecutionStrategy strategy,
                                                                               Command command) {
                return delegate.reserveConnection(strategy, command);
            }

            @Override
            public Publisher<RedisData> request(final RedisExecutionStrategy strategy, final RedisRequest request) {
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
        };

        RedisCommander commander = filteredClient.asCommander();

        assertThat(awaitIndefinitely(commander.mset("key1", "val1", "key3", "val3", "key5", "val5")), is("OK"));
        assertTrue(requestCalled.get());

        // Don't subscribe because we don't actually do the close, but instead just verify the method was called.
        commander.closeAsync();
        assertTrue(closeCalled.get());
    }

    @Test
    public void requestSingleLongIsRepeatable() throws Exception {
        RedisCommander commander = getEnv().client.asCommander();
        final String key = "foo";
        awaitIndefinitely(commander.del(key));
        assertThat(awaitIndefinitely(commander.append(key, "bar").repeat(times -> times < 2)
                        .reduce(() -> new ArrayList<>(2), (list, value) -> {
                            list.add(value);
                            return list;
                        })),
                contains(3L, 6L));
    }

    @Test
    public void requestSingleStringIsRepeatable() throws Exception {
        RedisCommander commander = getEnv().client.asCommander();
        assertThat(awaitIndefinitely(commander.set("foo", "value").repeat(times -> times < 2)
                        .reduce(() -> new ArrayList<>(2), (list, value) -> {
                            list.add(value);
                            return list;
                        })),
                contains("OK", "OK"));
    }

    @Test
    public void requestSingleBufferIsRepeatable() throws Exception {
        BufferRedisCommander commander = getEnv().client.asBufferCommander();
        final Buffer key =
                getEnv().client.executionContext().bufferAllocator().newBuffer(4).writeInt(Integer.MAX_VALUE);
        final Buffer v1 = getEnv().client.executionContext().bufferAllocator().newBuffer(4).writeInt(Integer.MIN_VALUE);
        final Buffer v2 = getEnv().client.executionContext().bufferAllocator().newBuffer(4).writeInt(12345678);
        awaitIndefinitely(commander.del(key.slice()));
        assertThat(awaitIndefinitely(commander.sadd(key.slice(), v1.slice())), is(1L));
        assertThat(awaitIndefinitely(commander.sadd(key.slice(), v2.slice())), is(1L));
        assertThat(awaitIndefinitely(commander.spop(key.slice()).repeat(times -> times < 2)
                        .reduce(() -> new ArrayList<>(2), (list, value) -> {
                            list.add(value);
                            return list;
                        })),
                containsInAnyOrder(v1, v2));
    }

    @Test
    public void requestSingleListIsRepeatable() throws Exception {
        BufferRedisCommander commander = getEnv().client.asBufferCommander();
        final Buffer key1 =
                getEnv().client.executionContext().bufferAllocator().newBuffer(4).writeInt(Integer.MAX_VALUE);
        final Buffer v1 = getEnv().client.executionContext().bufferAllocator().newBuffer(4).writeInt(Integer.MIN_VALUE);
        final Buffer v2 = getEnv().client.executionContext().bufferAllocator().newBuffer(4).writeInt(12345678);
        final Buffer key2 = getEnv().client.executionContext().bufferAllocator().newBuffer(4).writeInt(77777);
        final Buffer v3 = getEnv().client.executionContext().bufferAllocator().newBuffer(4).writeInt(123);
        final Buffer v4 = getEnv().client.executionContext().bufferAllocator().newBuffer(4).writeInt(55667);
        awaitIndefinitely(commander.del(key1.slice()));
        awaitIndefinitely(commander.del(key2.slice()));
        assertThat(awaitIndefinitely(commander.sadd(key1.slice(), v1.slice())), is(1L));
        assertThat(awaitIndefinitely(commander.sadd(key1.slice(), v2.slice())), is(1L));
        assertThat(awaitIndefinitely(commander.sadd(key2.slice(), v3.slice())), is(1L));
        assertThat(awaitIndefinitely(commander.sadd(key2.slice(), v4.slice())), is(1L));
        assertThat(awaitIndefinitely(commander.sunion(key1.slice(), key2.slice()).repeat(times -> times < 2)
                        .reduce(() -> new ArrayList<Object>(4), (aggregator, value) -> {
                            aggregator.addAll(value);
                            return aggregator;
                        })),
                containsInAnyOrder(v1, v2, v3, v4, v1, v2, v3, v4));
    }
}

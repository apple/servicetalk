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
import io.servicetalk.concurrent.internal.RejectedSubscribeException;
import io.servicetalk.redis.api.BufferRedisCommander;
import io.servicetalk.redis.api.IllegalTransactionStateException;
import io.servicetalk.redis.api.PubSubBufferRedisConnection;
import io.servicetalk.redis.api.PubSubRedisMessage;
import io.servicetalk.redis.api.RedisProtocolSupport;
import io.servicetalk.redis.api.RedisProtocolSupport.BitfieldOperations.Get;
import io.servicetalk.redis.api.RedisProtocolSupport.BitfieldOperations.Incrby;
import io.servicetalk.redis.api.RedisProtocolSupport.BitfieldOperations.Overflow;
import io.servicetalk.redis.api.RedisProtocolSupport.BitfieldOperations.Set;
import io.servicetalk.redis.api.RedisProtocolSupport.BufferLongitudeLatitudeMember;
import io.servicetalk.redis.api.RedisProtocolSupport.ExpireDuration;
import io.servicetalk.redis.api.RedisServerException;
import io.servicetalk.redis.api.TransactedBufferRedisCommander;
import io.servicetalk.redis.api.TransactionAbortedException;
import io.servicetalk.redis.netty.SubscribedRedisClientTest.AccumulatingSubscriber;

import org.hamcrest.Matcher;
import org.junit.Before;
import org.junit.Test;

import java.nio.channels.ClosedChannelException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.stream.IntStream;

import static io.servicetalk.buffer.api.EmptyBuffer.EMPTY_BUFFER;
import static io.servicetalk.concurrent.internal.Await.awaitIndefinitely;
import static io.servicetalk.concurrent.internal.Await.awaitIndefinitelyNonNull;
import static io.servicetalk.concurrent.internal.Await.awaitIndefinitelyUnchecked;
import static io.servicetalk.concurrent.internal.ServiceTalkTestTimeout.DEFAULT_TIMEOUT_SECONDS;
import static io.servicetalk.redis.api.RedisProtocolSupport.BitfieldOverflow.FAIL;
import static io.servicetalk.redis.api.RedisProtocolSupport.BitfieldOverflow.SAT;
import static io.servicetalk.redis.api.RedisProtocolSupport.GeoradiusOrder.ASC;
import static io.servicetalk.redis.api.RedisProtocolSupport.GeoradiusUnit.KM;
import static io.servicetalk.redis.api.RedisProtocolSupport.GeoradiusWithcoord.WITHCOORD;
import static io.servicetalk.redis.api.RedisProtocolSupport.GeoradiusWithdist.WITHDIST;
import static io.servicetalk.redis.api.RedisProtocolSupport.IntegerType.I05;
import static io.servicetalk.redis.api.RedisProtocolSupport.IntegerType.U02;
import static io.servicetalk.redis.api.RedisProtocolSupport.IntegerType.U04;
import static io.servicetalk.redis.api.RedisProtocolSupport.IntegerType.U08;
import static io.servicetalk.redis.api.RedisProtocolSupport.SetCondition.NX;
import static io.servicetalk.redis.api.RedisProtocolSupport.SetExpire.EX;
import static io.servicetalk.transport.netty.internal.RandomDataUtils.randomCharSequenceOfByteLength;
import static java.lang.Boolean.FALSE;
import static java.lang.Boolean.parseBoolean;
import static java.util.Arrays.asList;
import static java.util.Collections.emptyList;
import static java.util.Collections.singletonList;
import static java.util.concurrent.TimeUnit.SECONDS;
import static java.util.stream.Collectors.toList;
import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.anyOf;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.either;
import static org.hamcrest.Matchers.endsWith;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.hasProperty;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.instanceOf;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.lessThanOrEqualTo;
import static org.hamcrest.Matchers.nullValue;
import static org.hamcrest.Matchers.startsWith;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.fail;
import static org.junit.Assume.assumeThat;

public class BufferRedisCommanderTest extends BaseRedisClientTest {
    protected BufferRedisCommander commandClient;

    @Before
    public void createCommandClient() {
        commandClient = getMockRedisClient().asBufferCommander();
    }

    @Test
    public void simpleResponseTypes() throws Exception {
        assertThat(awaitIndefinitely(commandClient.ping()), is("PONG"));
        assertThat(awaitIndefinitely(commandClient.ping(buf("my-pong"))), is(buf("my-pong")));
        assertThat(awaitIndefinitely(commandClient.ping(buf(""))), is(buf("")));

        assertThat(awaitIndefinitely(commandClient.del(key("a-key"))), is(greaterThanOrEqualTo(0L)));
        assertThat(awaitIndefinitely(commandClient.get(buf("missing-key"))), is(nullValue()));
        assertThat(awaitIndefinitely(commandClient.set(key("a-key"), buf("a-value1"))), is("OK"));
        assertThat(awaitIndefinitely(commandClient.get(key("a-key"))), is(buf("a-value1")));
        assertThat(awaitIndefinitely(commandClient.set(key("a-key"), buf("a-value2"), new ExpireDuration(EX, 10L), null)), is("OK"));
        assertThat(awaitIndefinitely(commandClient.get(key("a-key"))), is(buf("a-value2")));

        assertThat(awaitIndefinitely(commandClient.set(key("exp-key"), buf("exp-value"), null, NX)), is(anyOf(nullValue(), equalTo("OK"))));

        assertThat(awaitIndefinitely(commandClient.zadd(key("a-zset"), null, null, 1, buf("one"))), is(either(equalTo(0L)).or(equalTo(1L))));
        assertThat(awaitIndefinitely(commandClient.zrank(key("a-zset"), buf("one"))), is(0L));
        assertThat(awaitIndefinitely(commandClient.zrank(key("missing"), buf("missing-member"))), is(nullValue()));
        if (getEnv().serverVersion[0] >= 3) {
            assertThat(awaitIndefinitely(commandClient.zaddIncr(key("a-zset"), null, null, 1, buf("one"))), is(2.0));
        }
        if (getEnv().serverVersion[0] >= 5) {
            assertThat(awaitIndefinitely(commandClient.zpopmax(key("a-zset"))), contains(buf("2"), buf("one")));
        }

        assertThat(awaitIndefinitely(commandClient.blpop(singletonList(buf("missing-key")), 1)), is(nullValue()));

        assertThat(awaitIndefinitely(commandClient.sadd(key("a-set-1"), buf("a"), buf("b"), buf("c"))
                        .concatWith(commandClient.sadd(key("a-set-2"), buf("c"), buf("d"), buf("e")))),
                contains(greaterThanOrEqualTo(0L), greaterThanOrEqualTo(0L)));
        assertThat(awaitIndefinitely(commandClient.sdiff(key("a-set-1"), key("a-set-2"), buf("missing-key"))), containsInAnyOrder(buf("a"), buf("b")));
        assertThat(awaitIndefinitely(commandClient.sdiffstore(key("diff"), key("a-set-1"), key("a-set-2"), buf("missing-key"))), is(2L));
    }

    @Test
    public void dataSpreadAcrossMultipleSocketReadWriteOperations() throws Exception {
        Buffer expectedValue = buf(randomCharSequenceOfByteLength(5 * 1024 * 1024)); // 5 MB
        Buffer largeKey = key("a-set-large-buffer-1");
        assertThat(commandClient.set(largeKey, expectedValue).toFuture().get(), is("OK"));
        assertThat(commandClient.get(largeKey).toFuture().get(), is(expectedValue));
        commandClient.del(largeKey).toFuture().get();
    }

    @Test
    public void emptyGet() throws Exception {
        Buffer emptyKey = key("a-empty-key");
        assertThat(commandClient.set(emptyKey, buf("")).toFuture().get(), is("OK"));
        assertThat(commandClient.get(emptyKey).toFuture().get(), is(EMPTY_BUFFER));
        commandClient.del(emptyKey).toFuture().get();
    }

    @Test
    public void argumentVariants() throws Exception {
        assertThat(awaitIndefinitely(commandClient.mset(key("key0"), buf("val0"))), is("OK"));
        assertThat(awaitIndefinitely(commandClient.mset(key("key1"), buf("val1"), key("key2"), buf("val2"), key("key3"), buf("val3"))), is("OK"));

        final List<RedisProtocolSupport.BufferKeyValue> keyValues = IntStream.range(4, 10)
                .mapToObj(i -> new RedisProtocolSupport.BufferKeyValue(key("key" + i), buf("val" + i)))
                .collect(toList());
        assertThat(awaitIndefinitely(commandClient.mset(keyValues)), is("OK"));

        assertThat(awaitIndefinitely(commandClient.mget(key("key0"))), contains(buf("val0")));
        assertThat(awaitIndefinitely(commandClient.mget(key("key1"), key("key2"), key("key3"))), contains(buf("val1"), buf("val2"), buf("val3")));

        final List<Buffer> keys = keyValues.stream().map(kv -> kv.key).collect(toList());
        final List<Buffer> expectedValues = keyValues.stream().map(kv -> kv.value).collect(toList());
        assertThat(awaitIndefinitely(commandClient.mget(keys)), is(expectedValues));
    }

    @Test
    public void unicodeNotMangled() throws Exception {
        assertThat(awaitIndefinitely(commandClient.set(key("\u263A"), buf("\u263A-foo"))), is("OK"));
        assertThat(awaitIndefinitely(commandClient.get(key("\u263A"))), is(buf("\u263A-foo")));
    }

    @Test
    public void bitfieldOperations() throws Exception {
        // Disable these tests for Redis 2 and below
        assumeThat(getEnv().serverVersion[0], is(greaterThanOrEqualTo(3)));

        awaitIndefinitely(commandClient.del(key("bf")));

        List<Long> results = awaitIndefinitely(commandClient.bitfield(key("bf"), asList(new Incrby(I05, 100L, 1L), new Get(U04, 0L))));
        assertThat(results, contains(1L, 0L));

        results.clear();
        for (int i = 0; i < 4; i++) {
            results.addAll(awaitIndefinitely(commandClient.bitfield(key("bf"), asList(
                    new Incrby(U02, 100L, 1L),
                    new Overflow(SAT),
                    new Incrby(U02, 102L, 1L)))));
        }
        assertThat(results, contains(1L, 1L, 2L, 2L, 3L, 3L, 0L, 3L));

        results = awaitIndefinitely(commandClient.bitfield(key("bf"), asList(new Overflow(FAIL), new Incrby(U02, 102L, 1L))));
        assertThat(results, contains(nullValue()));

        awaitIndefinitely(commandClient.del(key("bf")));

        // bitfield doesn't support non-numeric offsets (which are used for bitsize-based offsets)
        // but it's possible to get the same behaviour by using the bit size provided by the type enum,
        // ie. the following is equivalent to:
        //     BITFIELD <key> SET u8 #2 200 GET u8 #2 GET u4 #4 GET u4 #5
        results = awaitIndefinitely(commandClient.bitfield(key("bf"), asList(
                new Set(U08, U08.getBitSize() * 2, 200L),
                new Get(U08, U08.getBitSize() * 2),
                new Get(U04, U04.getBitSize() * 4),
                new Get(U04, U04.getBitSize() * 5))));
        assertThat(results, contains(0L, 200L, 12L, 8L));
    }

    @Test
    public void commandWithSubCommand() throws Exception {
        assertThat(awaitIndefinitely(commandClient.commandInfo(buf("GET"))), hasSize(1));
        assertThat(awaitIndefinitely(commandClient.objectRefcount(buf("missing-key"))), is(nullValue()));
        assertThat(awaitIndefinitely(commandClient.objectEncoding(buf("missing-key"))), is(nullValue()));
        if (getEnv().serverVersion[0] >= 4) {
            assertThat(awaitIndefinitely(commandClient.objectHelp()), hasSize(greaterThanOrEqualTo(5)));
        }
    }

    @Test
    public void infoWithAggregationDoesNotThrow() throws Exception {
        assertThat(awaitIndefinitelyNonNull(commandClient.info()).readableBytes(), is(greaterThan(0)));
    }

    @SuppressWarnings("unchecked")
    @Test
    public void variableResponseTypes() throws Exception {
        // Disable these tests for Redis 2 and below
        assumeThat(getEnv().serverVersion[0], is(greaterThanOrEqualTo(3)));

        assertThat(awaitIndefinitely(commandClient.zrem(key("Sicily"), asList(buf("Palermo"), buf("Catania")))), is(lessThanOrEqualTo(2L)));

        final Long geoaddLong = awaitIndefinitely(commandClient.geoadd(key("Sicily"),
                asList(new BufferLongitudeLatitudeMember(13.361389d, 38.115556d, buf("Palermo")),
                        new BufferLongitudeLatitudeMember(15.087269d, 37.502669d, buf("Catania")))));
        assertThat(geoaddLong, is(2L));

        final List<Buffer> georadiusBuffers = awaitIndefinitely(commandClient.georadius(key("Sicily"), 15d, 37d, 200d, KM));
        assertThat(georadiusBuffers, contains(buf("Palermo"), buf("Catania")));

        final List<?> georadiusMixedList = awaitIndefinitely(commandClient.georadius(key("Sicily"), 15d, 37d, 200d, KM, WITHCOORD, WITHDIST, null, 5L, ASC, null, null));
        final Matcher georadiusResponseMatcher = contains(
                contains(is(buf("Catania")), bufStartingWith(buf("56.")), contains(bufStartingWith(buf("15.")), bufStartingWith(buf("37.")))),
                contains(is(buf("Palermo")), bufStartingWith(buf("190.")), contains(bufStartingWith(buf("13.")), bufStartingWith(buf("38.")))));
        assertThat(georadiusMixedList, georadiusResponseMatcher);

        assertThat(awaitIndefinitely(commandClient.geodist(key("Sicily"), buf("Palermo"), buf("Catania"))), is(greaterThan(0d)));
        assertThat(awaitIndefinitely(commandClient.geodist(key("Sicily"), buf("foo"), buf("bar"))), is(nullValue()));

        assertThat(awaitIndefinitely(commandClient.geopos(key("Sicily"), buf("Palermo"), buf("NonExisting"), buf("Catania"))), contains(
                contains(bufStartingWith(buf("13.")), bufStartingWith(buf("38."))), is(nullValue()), contains(bufStartingWith(buf("15.")), bufStartingWith(buf("37.")))));

        final List<Buffer> evalBuffers = awaitIndefinitely(commandClient.evalList(buf("return {KEYS[1],KEYS[2],ARGV[1],ARGV[2]}"),
                2L, asList(buf("key1"), buf("key2")), asList(buf("first"), buf("second"))));
        assertThat(evalBuffers, contains(buf("key1"), buf("key2"), buf("first"), buf("second")));

        final Buffer evalBuf = awaitIndefinitely(commandClient.eval(buf("return redis.call('set','foo','bar')"),
                0L, emptyList(), emptyList()));
        assertThat(evalBuf, is(buf("OK")));

        final Long evalLong = awaitIndefinitely(commandClient.evalLong(buf("return 10"), 0L, emptyList(), emptyList()));
        assertThat(evalLong, is(10L));

        final List<?> evalMixedList = awaitIndefinitely(commandClient.evalList(buf("return {1,2,{3,'four'}}"), 0L, emptyList(), emptyList()));
        assertThat(evalMixedList, contains(1L, 2L, asList(3L, buf("four"))));
    }

    @Test
    public void testHGetAll() throws Exception {
        String testKey = "key";
        commandClient.del(buf(testKey)).toFuture().get();
        List<RedisProtocolSupport.BufferFieldValue> fields = new ArrayList<>(3);
        fields.add(new RedisProtocolSupport.BufferFieldValue(buf("f"), buf("v")));
        fields.add(new RedisProtocolSupport.BufferFieldValue(buf("f1"), buf("v1")));
        fields.add(new RedisProtocolSupport.BufferFieldValue(buf("f2"), buf("v2")));
        awaitIndefinitelyNonNull(commandClient.hmset(buf(testKey), fields));
        final List<Buffer> result = awaitIndefinitelyNonNull(commandClient.hgetall(buf(testKey)));
        assertThat(new HashSet<>(toBufferFieldValues(result)), is(new HashSet<>(fields)));
        final List<Buffer> values = awaitIndefinitelyNonNull(commandClient.hmget(buf(testKey), buf("f"), buf("f1"), buf("f2")));
        assertThat(values, is(asList(buf("v"), buf("v1"), buf("v2"))));
    }

    @Test
    public void testHMGet() throws Exception {
        String testKey = "key";
        commandClient.del(buf(testKey)).toFuture().get();
        List<RedisProtocolSupport.BufferFieldValue> fields = new ArrayList<>(11);
        fields.add(new RedisProtocolSupport.BufferFieldValue(buf("f"), buf("v")));
        fields.add(new RedisProtocolSupport.BufferFieldValue(buf("f1"), buf("v1")));
        fields.add(new RedisProtocolSupport.BufferFieldValue(buf("f2"), buf("v2")));
        fields.add(new RedisProtocolSupport.BufferFieldValue(buf("f3"), buf("v3")));
        fields.add(new RedisProtocolSupport.BufferFieldValue(buf("f4"), buf("v4")));
        fields.add(new RedisProtocolSupport.BufferFieldValue(buf("f5"), buf("v5")));
        fields.add(new RedisProtocolSupport.BufferFieldValue(buf("f6"), buf("v6")));
        fields.add(new RedisProtocolSupport.BufferFieldValue(buf("f7"), buf("v7")));
        fields.add(new RedisProtocolSupport.BufferFieldValue(buf("f8"), buf("v8")));
        fields.add(new RedisProtocolSupport.BufferFieldValue(buf("f9"), buf("v9")));
        fields.add(new RedisProtocolSupport.BufferFieldValue(buf("f10"), buf("v10")));
        awaitIndefinitelyNonNull(commandClient.hmset(buf(testKey), fields));
        final List<Buffer> values = awaitIndefinitelyNonNull(commandClient.hmget(buf(testKey), asList(buf("f"), buf("f1"), buf("f2"), buf("f3"), buf("f4"), buf("f5"), buf("f6"), buf("f7"), buf("f8"), buf("f9"), buf("f10"))));
        assertThat(values, is(fields.stream().map(fv -> fv.value).collect(toList())));
    }

    @Test
    public void testSort() throws Exception {
        String testKey = "key";
        commandClient.del(buf(testKey)).toFuture().get();
        awaitIndefinitelyNonNull(commandClient.rpush(buf(testKey), buf("1")));
        awaitIndefinitelyNonNull(commandClient.set(buf("1-score"), buf("1")));
        awaitIndefinitelyNonNull(commandClient.set(buf("1-ᕈгø⨯у"), buf("proxy")));

        assertThat(awaitIndefinitelyNonNull(commandClient.sort(buf(testKey), buf("*-score"), new RedisProtocolSupport.OffsetCount(0, 1), singletonList(buf("*-ᕈгø⨯у")), RedisProtocolSupport.SortOrder.ASC, RedisProtocolSupport.SortSorting.ALPHA)),
                is(singletonList(buf("proxy"))));
        assertThat(awaitIndefinitelyNonNull(commandClient.sort(buf(testKey), null, new RedisProtocolSupport.OffsetCount(0, 1), singletonList(buf("*-ᕈгø⨯у")), RedisProtocolSupport.SortOrder.ASC, RedisProtocolSupport.SortSorting.ALPHA)),
                is(singletonList(buf("proxy"))));
        assertThat(awaitIndefinitelyNonNull(commandClient.sort(buf(testKey), null, null, singletonList(buf("*-ᕈгø⨯у")), RedisProtocolSupport.SortOrder.ASC, RedisProtocolSupport.SortSorting.ALPHA)),
                is(singletonList(buf("proxy"))));
        assertThat(awaitIndefinitelyNonNull(commandClient.sort(buf(testKey), null, null, singletonList(buf("*-ᕈгø⨯у")), null, RedisProtocolSupport.SortSorting.ALPHA)),
                is(singletonList(buf("proxy"))));
        assertThat(awaitIndefinitelyNonNull(commandClient.sort(buf(testKey), null, null, singletonList(buf("*-ᕈгø⨯у")), null, null)),
                is(singletonList(buf("proxy"))));
    }

    @Test
    public void recoverableError() throws Exception {
        try {
            awaitIndefinitely(commandClient.eval(buf("bad"), 0L, emptyList(), emptyList()));
            fail("Expected ExecutionException");
        } catch (final ExecutionException ee) {
            assertThat(ee.getCause(), is(instanceOf(RedisServerException.class)));
            assertThat(awaitIndefinitely(commandClient.ping()), is("PONG"));
        }
    }

    @Test
    public void transactionExec() throws Exception {
        final TransactedBufferRedisCommander tcc = awaitIndefinitelyNonNull(commandClient.multi());
        Future<Long> value1 = tcc.del(key("a-key"));
        Future<String> value2 = tcc.set(key("a-key"), buf("a-value3"));
        Future<Buffer> value3 = tcc.ping(buf("in-transac"));
        Future<Buffer> value4 = tcc.get(key("a-key"));
        tcc.exec().toFuture().get();
        postReleaseLatch.await();
        assertThat(value1.get(), is(1L));
        assertThat(value2.get(), is("OK"));
        assertThat(value3.get(), is(buf("in-transac")));
        assertThat(value4.get(), is(buf("a-value3")));
    }

    @Test
    public void transactionDiscard() throws Exception {
        final TransactedBufferRedisCommander tcc = awaitIndefinitelyNonNull(commandClient.multi());

        Future<Buffer> future = tcc.ping(buf("in-transac"));
        tcc.discard().toFuture().get();
        postReleaseLatch.await();

        thrown.expect(ExecutionException.class);
        thrown.expectCause(instanceOf(TransactionAbortedException.class));
        future.get();
    }

    @Test
    public void transactionCommandAfterExec() throws Exception {
        final TransactedBufferRedisCommander tcc = awaitIndefinitelyNonNull(commandClient.multi());
        awaitIndefinitely(tcc.exec());

        thrown.expect(IllegalTransactionStateException.class);
        tcc.ping(buf("in-transac"));
    }

    @Test
    public void transactionCommandAfterDiscard() throws Exception {
        final TransactedBufferRedisCommander tcc = awaitIndefinitelyNonNull(commandClient.multi());
        awaitIndefinitely(tcc.discard());

        thrown.expect(IllegalTransactionStateException.class);
        tcc.ping(buf("in-transac"));
    }

    @Test
    public void transactionPartialFailure() throws Exception {
        final TransactedBufferRedisCommander tcc = awaitIndefinitelyNonNull(commandClient.multi());

        Future<String> r1 = tcc.set(key("ptf"), buf("foo"));
        Future<Buffer> r2 = tcc.lpop(key("ptf"));

        awaitIndefinitely(tcc.exec());

        assertThat(r1.get(), is("OK"));

        thrown.expect(ExecutionException.class);
        thrown.expectCause(is(instanceOf(RedisServerException.class)));
        thrown.expectCause(hasProperty("message", startsWith("WRONGTYPE")));
        r2.get();
    }

    @Test
    public void transactionExecCancel() throws Exception {
        final TransactedBufferRedisCommander tcc = awaitIndefinitelyNonNull(commandClient.multi());

        // Keep Redis busy for approximately 1 second so that it can't finish the transaction before we cancel.
        Future<Long> longFuture = tcc.evalLong(buf(EVAL_SLEEP_SCRIPT), 0, emptyList(),
                asList(buf("1000"), buf("100000000")));

        Future<Buffer> pingFuture = tcc.ping(buf("in-transac"));
        tcc.exec().toFuture().cancel(true);
        postCloseLatch.await();

        assertThrowsClosedChannelException(pingFuture::get);

        // Wait for Redis to stop being busy.
        assertThrowsClosedChannelException(longFuture::get);
    }

    @Test
    public void transactionExecCancelBusyBackground() throws Exception {
        final TransactedBufferRedisCommander tcc = awaitIndefinitelyNonNull(commandClient.multi());

        // Keep Redis busy for approximately 1 second so that it can't actually execute the transaction. We want to
        // cancel before Redis can return any responses.
        Future<Long> longFuture = commandClient.evalLong(buf(EVAL_SLEEP_SCRIPT), 0, emptyList(),
                asList(buf("1000"), buf("100000000"))).toFuture();

        Future<Buffer> pingFuture = tcc.ping(buf("in-transac"));
        tcc.exec().toFuture().cancel(true);
        postCloseLatch.await();

        assertThrowsClosedChannelException(pingFuture::get);

        // Wait for Redis to stop being busy.
        longFuture.get();
    }

    @Test
    public void transactionDiscardCancel() throws Exception {
        final TransactedBufferRedisCommander tcc = awaitIndefinitelyNonNull(commandClient.multi());

        // Keep Redis busy for approximately 1 second so that it can't discard the transaction before we cancel.
        Future<Long> longFuture = tcc.evalLong(buf(EVAL_SLEEP_SCRIPT), 0, emptyList(),
                asList(buf("1000"), buf("100000000")));

        Future<Buffer> pingFuture = tcc.ping(buf("in-transac"));
        tcc.discard().toFuture().cancel(true);
        postCloseLatch.await();

        assertThrowsClosedChannelException(pingFuture::get);

        // Wait for Redis to stop being busy.
        assertThrowsClosedChannelException(longFuture::get);
    }

    @Test
    public void transactionDiscardCancelBusyBackground() throws Exception {
        final TransactedBufferRedisCommander tcc = awaitIndefinitelyNonNull(commandClient.multi());

        // Keep Redis busy for approximately 1 second so that it can't actually discard the transaction. We want to
        // cancel before Redis can return any responses.
        Future<Long> longFuture = commandClient.evalLong(buf(EVAL_SLEEP_SCRIPT), 0, emptyList(),
                asList(buf("1000"), buf("100000000"))).toFuture();

        Future<Buffer> pingFuture = tcc.ping(buf("in-transac"));
        tcc.discard().toFuture().cancel(true);
        postCloseLatch.await();

        assertThrowsClosedChannelException(pingFuture::get);

        // Wait for Redis to stop being busy.
        longFuture.get();
    }

    @Test
    public void transactionCloseAsync() throws Exception {
        final TransactedBufferRedisCommander tcc = awaitIndefinitelyNonNull(commandClient.multi());

        tcc.closeAsync().toFuture().get();

        thrown.expect(ExecutionException.class);
        thrown.expectCause(is(instanceOf(ClosedChannelException.class)));
        tcc.ping().get();
    }

    @Test
    public void monitor() throws Exception {
        final AccumulatingSubscriber<String> subscriber = new AccumulatingSubscriber<>();
        CountDownLatch cancelled = new CountDownLatch(1);

        assertThat(subscriber
                .subscribe(commandClient.monitor()
                        .doAfterNext(__ -> awaitIndefinitelyUnchecked(commandClient.ping().ignoreResult()))
                        .doAfterCancel(cancelled::countDown))
                .request(2)
                .awaitUntilAtLeastNReceived(2, DEFAULT_TIMEOUT_SECONDS, SECONDS), is(true));

        assertThat(subscriber.getReceived(), contains(is("OK"), endsWith("\"PING\"")));

        subscriber.cancel();
        assertThat(cancelled.await(DEFAULT_TIMEOUT_SECONDS, SECONDS), is(true));
    }

    @Test
    public void pubSubMultipleSubscribes() throws Exception {
        assumeThat("Ignored flaky test", parseBoolean(System.getenv("CI")), is(FALSE));
        final PubSubBufferRedisConnection pubSubClient1 = awaitIndefinitely(commandClient.subscribe(key("channel-1")));
        final AccumulatingSubscriber<PubSubRedisMessage> subscriber1 = new AccumulatingSubscriber<>();
        subscriber1.subscribe(pubSubClient1.getMessages());

        // Publish a test message
        publishTestMessage(key("channel-1"));

        // Check ping request get proper response
        assertThat(awaitIndefinitely(pubSubClient1.ping()).getBufferValue(), is(EMPTY_BUFFER));

        // Subscribe to a pattern on the same connection
        final PubSubBufferRedisConnection pubSubClient2 = awaitIndefinitely(pubSubClient1.psubscribe(key("channel-2*")));
        final AccumulatingSubscriber<PubSubRedisMessage> subscriber2 = new AccumulatingSubscriber<>();
        subscriber2.subscribe(pubSubClient2.getMessages());

        // Let's throw a wrench and psubscribe a second time to the same pattern
        final PubSubBufferRedisConnection pubSubClient3 = awaitIndefinitely(pubSubClient1.psubscribe(key("channel-2*")));
        final AccumulatingSubscriber<PubSubRedisMessage> subscriber3 =
                new AccumulatingSubscriber<PubSubRedisMessage>().subscribe(pubSubClient3.getMessages());
        assertThat(subscriber3.awaitTerminal(DEFAULT_TIMEOUT_SECONDS, SECONDS), is(true));
        assertThat(subscriber3.getTerminal().getCause(), is(instanceOf(RejectedSubscribeException.class)));

        // Publish another test message
        publishTestMessage(key("channel-202"));

        // Check ping request get proper response
        assertThat(awaitIndefinitely(pubSubClient1.ping(buf("my-pong"))).getBufferValue(), is(buf("my-pong")));

        assertThat(subscriber1.request(1).awaitUntilAtLeastNReceived(1, DEFAULT_TIMEOUT_SECONDS, SECONDS), is(true));
        assertThat(subscriber1.getReceived().poll(), allOf(
                hasProperty("channel", equalTo(keyStr("channel-1"))),
                hasProperty("bufferValue", equalTo(buf("test-message")))));
        // Cancel the subscriber, which issues an UNSUBSCRIBE behind the scenes
        subscriber1.cancel();

        assertThat(subscriber2.request(1).awaitUntilAtLeastNReceived(1, DEFAULT_TIMEOUT_SECONDS, SECONDS), is(true));
        assertThat(subscriber2.getReceived().poll(), allOf(
                hasProperty("channel", equalTo(keyStr("channel-202"))),
                hasProperty("pattern", equalTo(keyStr("channel-2*"))),
                hasProperty("bufferValue", equalTo(buf("test-message")))));
        // Cancel the subscriber, which issues an PUNSUBSCRIBE behind the scenes
        subscriber2.cancel();

        // It is an error to keep using the pubsub client after all subscriptions have been terminated
        try {
            awaitIndefinitely(pubSubClient1.ping());
            fail("Should have failed");
        } catch (final ExecutionException expected) {
            // Expected
        }
    }

    private Buffer key(CharSequence key) {
        return buf(key + "-brct");
    }

    private static String keyStr(CharSequence key) {
        return key + "-brct";
    }
}

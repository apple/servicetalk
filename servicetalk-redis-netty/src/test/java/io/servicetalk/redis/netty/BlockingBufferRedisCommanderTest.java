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
import io.servicetalk.concurrent.BlockingIterable;
import io.servicetalk.concurrent.BlockingIterator;
import io.servicetalk.concurrent.api.Executor;
import io.servicetalk.concurrent.api.Executors;
import io.servicetalk.concurrent.internal.RejectedSubscribeException;
import io.servicetalk.redis.api.BlockingBufferRedisCommander;
import io.servicetalk.redis.api.BlockingPubSubBufferRedisConnection;
import io.servicetalk.redis.api.BlockingTransactedBufferRedisCommander;
import io.servicetalk.redis.api.IllegalTransactionStateException;
import io.servicetalk.redis.api.PubSubRedisMessage;
import io.servicetalk.redis.api.PubSubRedisMessage.ChannelPubSubRedisMessage;
import io.servicetalk.redis.api.PubSubRedisMessage.PatternPubSubRedisMessage;
import io.servicetalk.redis.api.RedisProtocolSupport;
import io.servicetalk.redis.api.RedisProtocolSupport.BitfieldOperations.Get;
import io.servicetalk.redis.api.RedisProtocolSupport.BitfieldOperations.Incrby;
import io.servicetalk.redis.api.RedisProtocolSupport.BitfieldOperations.Overflow;
import io.servicetalk.redis.api.RedisProtocolSupport.BitfieldOperations.Set;
import io.servicetalk.redis.api.RedisProtocolSupport.BufferLongitudeLatitudeMember;
import io.servicetalk.redis.api.RedisProtocolSupport.ExpireDuration;
import io.servicetalk.redis.api.RedisServerException;
import io.servicetalk.redis.api.TransactionAbortedException;

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
import static io.servicetalk.concurrent.api.BlockingTestUtils.awaitIndefinitely;
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
import static java.util.stream.Collectors.toList;
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

public class BlockingBufferRedisCommanderTest extends BaseRedisClientTest {
    protected BlockingBufferRedisCommander commandClient;

    @Before
    public void createCommandClient() {
        commandClient = getMockRedisClient().asBlockingBufferCommander();
    }

    @Test
    public void simpleResponseTypes() throws Exception {
        assertThat(commandClient.ping(), is("PONG"));
        assertThat(commandClient.ping(buf("my-pong")), is(buf("my-pong")));
        assertThat(commandClient.ping(buf("")), is(buf("")));

        assertThat(commandClient.del(key("a-key")), is(greaterThanOrEqualTo(0L)));
        assertThat(commandClient.get(buf("missing-key")), is(nullValue()));
        assertThat(commandClient.set(key("a-key"), buf("a-value1")), is("OK"));
        assertThat(commandClient.get(key("a-key")), is(buf("a-value1")));
        assertThat(commandClient.set(key("a-key"), buf("a-value2"), new ExpireDuration(EX, 10L), null), is("OK"));
        assertThat(commandClient.get(key("a-key")), is(buf("a-value2")));

        assertThat(commandClient.set(key("exp-key"), buf("exp-value"), null, NX), is(anyOf(nullValue(), equalTo("OK"))));

        assertThat(commandClient.zadd(key("a-zset"), null, null, 1, buf("one")), is(either(equalTo(0L)).or(equalTo(1L))));
        assertThat(commandClient.zrank(key("a-zset"), buf("one")), is(0L));
        assertThat(commandClient.zrank(key("missing"), buf("missing-member")), is(nullValue()));
        if (getEnv().serverVersion[0] >= 3) {
            assertThat(commandClient.zaddIncr(key("a-zset"), null, null, 1, buf("one")), is(2.0));
        }
        if (getEnv().serverVersion[0] >= 5) {
            assertThat(commandClient.zpopmax(key("a-zset")), contains(buf("2"), buf("one")));
        }

        assertThat(commandClient.blpop(singletonList(buf("missing-key")), 1), is(nullValue()));

        assertThat(commandClient.sadd(key("a-set-1"), buf("a"), buf("b"), buf("c")), greaterThanOrEqualTo(0L));
        assertThat(commandClient.sadd(key("a-set-2"), buf("c"), buf("d"), buf("e")), greaterThanOrEqualTo(0L));
        assertThat(commandClient.sdiff(key("a-set-1"), key("a-set-2"), buf("missing-key")), containsInAnyOrder(buf("a"), buf("b")));
        assertThat(commandClient.sdiffstore(key("diff"), key("a-set-1"), key("a-set-2"), buf("missing-key")), is(2L));
    }

    @Test
    public void dataSpreadAcrossMultipleSocketReadWriteOperations() throws Exception {
        Buffer expectedValue = buf(randomCharSequenceOfByteLength(5 * 1024 * 1024)); // 5 MB
        Buffer largeKey = key("a-set-large-buffer-1");
        assertThat(commandClient.set(largeKey, expectedValue), is("OK"));
        assertThat(commandClient.get(largeKey), is(expectedValue));
        commandClient.del(largeKey);
    }

    @Test
    public void emptyGet() throws Exception {
        Buffer emptyKey = key("a-empty-key");
        assertThat(commandClient.set(emptyKey, buf("")), is("OK"));
        assertThat(commandClient.get(emptyKey), is(EMPTY_BUFFER));
        commandClient.del(emptyKey);
    }

    @Test
    public void argumentVariants() throws Exception {
        assertThat(commandClient.mset(key("key0"), buf("val0")), is("OK"));
        assertThat(commandClient.mset(key("key1"), buf("val1"), key("key2"), buf("val2"), key("key3"), buf("val3")), is("OK"));

        final List<RedisProtocolSupport.BufferKeyValue> keyValues = IntStream.range(4, 10)
                .mapToObj(i -> new RedisProtocolSupport.BufferKeyValue(key("key" + i), buf("val" + i)))
                .collect(toList());
        assertThat(commandClient.mset(keyValues), is("OK"));

        assertThat(commandClient.mget(key("key0")), contains(buf("val0")));
        assertThat(commandClient.mget(key("key1"), key("key2"), key("key3")), contains(buf("val1"), buf("val2"), buf("val3")));

        final List<Buffer> keys = keyValues.stream().map(kv -> kv.key).collect(toList());
        final List<Buffer> expectedValues = keyValues.stream().map(kv -> kv.value).collect(toList());
        assertThat(commandClient.mget(keys), is(expectedValues));
    }

    @Test
    public void unicodeNotMangled() throws Exception {
        assertThat(commandClient.set(key("\u263A"), buf("\u263A-foo")), is("OK"));
        assertThat(commandClient.get(key("\u263A")), is(buf("\u263A-foo")));
    }

    @Test
    public void bitfieldOperations() throws Exception {
        // Disable these tests for Redis 2 and below
        assumeThat(getEnv().serverVersion[0], is(greaterThanOrEqualTo(3)));

        commandClient.del(key("bf"));

        List<Long> results = commandClient.bitfield(key("bf"), asList(new Incrby(I05, 100L, 1L), new Get(U04, 0L)));
        assertThat(results, contains(1L, 0L));

        results.clear();
        for (int i = 0; i < 4; i++) {
            results.addAll(commandClient.bitfield(key("bf"), asList(
                    new Incrby(U02, 100L, 1L),
                    new Overflow(SAT),
                    new Incrby(U02, 102L, 1L))));
        }
        assertThat(results, contains(1L, 1L, 2L, 2L, 3L, 3L, 0L, 3L));

        results = commandClient.bitfield(key("bf"), asList(new Overflow(FAIL), new Incrby(U02, 102L, 1L)));
        assertThat(results, contains(nullValue()));

        commandClient.del(key("bf"));

        // bitfield doesn't support non-numeric offsets (which are used for bitsize-based offsets)
        // but it's possible to get the same behaviour by using the bit size provided by the type enum,
        // ie. the following is equivalent to:
        //     BITFIELD <key> SET u8 #2 200 GET u8 #2 GET u4 #4 GET u4 #5
        results = commandClient.bitfield(key("bf"), asList(
                new Set(U08, U08.size() * 2, 200L),
                new Get(U08, U08.size() * 2),
                new Get(U04, U04.size() * 4),
                new Get(U04, U04.size() * 5)));
        assertThat(results, contains(0L, 200L, 12L, 8L));
    }

    @Test
    public void commandWithSubCommand() throws Exception {
        assertThat(commandClient.commandInfo(buf("GET")), hasSize(1));
        assertThat(commandClient.objectRefcount(buf("missing-key")), is(nullValue()));
        assertThat(commandClient.objectEncoding(buf("missing-key")), is(nullValue()));
        if (getEnv().serverVersion[0] >= 4) {
            assertThat(commandClient.objectHelp(), hasSize(greaterThanOrEqualTo(5)));
        }
    }

    @Test
    public void infoWithAggregationDoesNotThrow() throws Exception {
        assertThat(commandClient.info().readableBytes(), is(greaterThan(0)));
    }

    @SuppressWarnings("unchecked")
    @Test
    public void variableResponseTypes() throws Exception {
        // Disable these tests for Redis 2 and below
        assumeThat(getEnv().serverVersion[0], is(greaterThanOrEqualTo(3)));

        assertThat(commandClient.zrem(key("Sicily"), asList(buf("Palermo"), buf("Catania"))), is(lessThanOrEqualTo(2L)));

        final Long geoaddLong = commandClient.geoadd(key("Sicily"),
                asList(new BufferLongitudeLatitudeMember(13.361389d, 38.115556d, buf("Palermo")),
                        new BufferLongitudeLatitudeMember(15.087269d, 37.502669d, buf("Catania"))));
        assertThat(geoaddLong, is(2L));

        final List<Buffer> georadiusBuffers = commandClient.georadius(key("Sicily"), 15d, 37d, 200d, KM);
        assertThat(georadiusBuffers, contains(buf("Palermo"), buf("Catania")));

        final List<?> georadiusMixedList = commandClient.georadius(key("Sicily"), 15d, 37d, 200d, KM, WITHCOORD, WITHDIST, null, 5L, ASC, null, null);
        final Matcher georadiusResponseMatcher = contains(
                contains(is(buf("Catania")), bufStartingWith(buf("56.")), contains(bufStartingWith(buf("15.")), bufStartingWith(buf("37.")))),
                contains(is(buf("Palermo")), bufStartingWith(buf("190.")), contains(bufStartingWith(buf("13.")), bufStartingWith(buf("38.")))));
        assertThat(georadiusMixedList, georadiusResponseMatcher);

        assertThat(commandClient.geodist(key("Sicily"), buf("Palermo"), buf("Catania")), is(greaterThan(0d)));
        assertThat(commandClient.geodist(key("Sicily"), buf("foo"), buf("bar")), is(nullValue()));

        assertThat(commandClient.geopos(key("Sicily"), buf("Palermo"), buf("NonExisting"), buf("Catania")), contains(
                contains(bufStartingWith(buf("13.")), bufStartingWith(buf("38."))), is(nullValue()), contains(bufStartingWith(buf("15.")), bufStartingWith(buf("37.")))));

        final List<Buffer> evalBuffers = commandClient.evalList(buf("return {KEYS[1],KEYS[2],ARGV[1],ARGV[2]}"),
                2L, asList(buf("key1"), buf("key2")), asList(buf("first"), buf("second")));
        assertThat(evalBuffers, contains(buf("key1"), buf("key2"), buf("first"), buf("second")));

        final Buffer evalBuf = commandClient.eval(buf("return redis.call('set','foo','bar')"),
                0L, emptyList(), emptyList());
        assertThat(evalBuf, is(buf("OK")));

        final Long evalLong = commandClient.evalLong(buf("return 10"), 0L, emptyList(), emptyList());
        assertThat(evalLong, is(10L));

        final List<?> evalMixedList = commandClient.evalList(buf("return {1,2,{3,'four'}}"), 0L, emptyList(), emptyList());
        assertThat(evalMixedList, contains(1L, 2L, asList(3L, buf("four"))));
    }

    @Test
    public void testHGetAll() throws Exception {
        String testKey = "key";
        commandClient.del(buf(testKey));
        List<RedisProtocolSupport.BufferFieldValue> fields = new ArrayList<>(3);
        fields.add(new RedisProtocolSupport.BufferFieldValue(buf("f"), buf("v")));
        fields.add(new RedisProtocolSupport.BufferFieldValue(buf("f1"), buf("v1")));
        fields.add(new RedisProtocolSupport.BufferFieldValue(buf("f2"), buf("v2")));
        commandClient.hmset(buf(testKey), fields);
        final List<Buffer> result = commandClient.hgetall(buf(testKey));
        assertThat(new HashSet<>(toBufferFieldValues(result)), is(new HashSet<>(fields)));
        final List<Buffer> values = commandClient.hmget(buf(testKey), buf("f"), buf("f1"), buf("f2"));
        assertThat(values, is(asList(buf("v"), buf("v1"), buf("v2"))));
    }

    @Test
    public void testHMGet() throws Exception {
        String testKey = "key";
        commandClient.del(buf(testKey));
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
        commandClient.hmset(buf(testKey), fields);
        final List<Buffer> values = commandClient.hmget(buf(testKey), asList(buf("f"), buf("f1"), buf("f2"), buf("f3"), buf("f4"), buf("f5"), buf("f6"), buf("f7"), buf("f8"), buf("f9"), buf("f10")));
        assertThat(values, is(fields.stream().map(fv -> fv.value).collect(toList())));
    }

    @Test
    public void testSort() throws Exception {
        String testKey = "key";
        commandClient.del(buf(testKey));
        commandClient.rpush(buf(testKey), buf("1"));
        commandClient.set(buf("1-score"), buf("1"));
        commandClient.set(buf("1-ᕈгø⨯у"), buf("proxy"));

        assertThat(commandClient.sort(buf(testKey), buf("*-score"), new RedisProtocolSupport.OffsetCount(0, 1), singletonList(buf("*-ᕈгø⨯у")), RedisProtocolSupport.SortOrder.ASC, RedisProtocolSupport.SortSorting.ALPHA),
                is(singletonList(buf("proxy"))));
        assertThat(commandClient.sort(buf(testKey), null, new RedisProtocolSupport.OffsetCount(0, 1), singletonList(buf("*-ᕈгø⨯у")), RedisProtocolSupport.SortOrder.ASC, RedisProtocolSupport.SortSorting.ALPHA),
                is(singletonList(buf("proxy"))));
        assertThat(commandClient.sort(buf(testKey), null, null, singletonList(buf("*-ᕈгø⨯у")), RedisProtocolSupport.SortOrder.ASC, RedisProtocolSupport.SortSorting.ALPHA),
                is(singletonList(buf("proxy"))));
        assertThat(commandClient.sort(buf(testKey), null, null, singletonList(buf("*-ᕈгø⨯у")), null, RedisProtocolSupport.SortSorting.ALPHA),
                is(singletonList(buf("proxy"))));
        assertThat(commandClient.sort(buf(testKey), null, null, singletonList(buf("*-ᕈгø⨯у")), null, null),
                is(singletonList(buf("proxy"))));
    }

    @Test
    public void recoverableError() throws Exception {
        try {
            commandClient.eval(buf("bad"), 0L, emptyList(), emptyList());
            fail("Expected ExecutionException");
        } catch (final RedisServerException ee) {
            assertThat(commandClient.ping(), is("PONG"));
        }
    }

    @Test
    public void transactionExec() throws Exception {
        BlockingTransactedBufferRedisCommander tcc = commandClient.multi();
        Future<Long> value1 = tcc.del(key("a-key"));
        Future<String> value2 = tcc.set(key("a-key"), buf("a-value3"));
        Future<Buffer> value3 = tcc.ping(buf("in-transac"));
        Future<Buffer> value4 = tcc.get(key("a-key"));
        tcc.exec();
        postReleaseLatch.await();
        assertThat(value1.get(), is(1L));
        assertThat(value2.get(), is("OK"));
        assertThat(value3.get(), is(buf("in-transac")));
        assertThat(value4.get(), is(buf("a-value3")));
    }

    @Test
    public void transactionDiscard() throws Exception {
        BlockingTransactedBufferRedisCommander tcc = commandClient.multi();
        Future<Buffer> future = tcc.ping(buf("in-transac"));
        final String result = tcc.discard();
        postReleaseLatch.await();

        assertThat(result, is("OK"));

        thrown.expect(ExecutionException.class);
        thrown.expectCause(instanceOf(TransactionAbortedException.class));
        future.get();
    }

    @Test
    public void transactionCommandAfterExec() throws Exception {
        final BlockingTransactedBufferRedisCommander tcc = commandClient.multi();
        tcc.exec();

        thrown.expect(IllegalTransactionStateException.class);
        tcc.ping(buf("in-transac"));
    }

    @Test
    public void transactionCommandAfterDiscard() throws Exception {
        final BlockingTransactedBufferRedisCommander tcc = commandClient.multi();
        tcc.discard();

        thrown.expect(IllegalTransactionStateException.class);
        tcc.ping(buf("in-transac"));
    }

    @Test
    public void transactionPartialFailure() throws Exception {
        BlockingTransactedBufferRedisCommander tcc = commandClient.multi();

        Future<String> r1 = tcc.set(key("ptf"), buf("foo"));
        Future<Buffer> r2 = tcc.lpop(key("ptf"));

        tcc.exec();

        assertThat(r1.get(), is("OK"));

        thrown.expect(ExecutionException.class);
        thrown.expectCause(instanceOf(RedisServerException.class));
        thrown.expectCause(hasProperty("message", startsWith("WRONGTYPE")));
        r2.get();
    }

    @Test
    public void transactionClose() throws Exception {
        BlockingTransactedBufferRedisCommander tcc = commandClient.multi();
        tcc.close();

        thrown.expect(ExecutionException.class);
        thrown.expectCause(instanceOf(ClosedChannelException.class));
        tcc.ping().get();
    }

    @Test
    public void monitor() throws Exception {
        Executor executor = Executors.newFixedSizeExecutor(1);
        try {
            BlockingIterable<String> iterable = commandClient.monitor();
            BlockingIterator<String> iterator = iterable.iterator();
            CountDownLatch latch = new CountDownLatch(1);

            executor.execute(() -> {
                try {
                    commandClient.ping();
                    latch.countDown();
                } catch (Exception e) {
                    e.printStackTrace();
                }
            });
            latch.await();

            assertThat(iterator.next(), is("OK"));
            assertThat(iterator.next(), endsWith("\"PING\""));
        } finally {
            awaitIndefinitely(executor.closeAsync());
        }
    }

    @Test
    public void pubSubMultipleSubscribes() throws Exception {
        assumeThat("Ignored flaky test", parseBoolean(System.getenv("CI")), is(FALSE));
        final BlockingPubSubBufferRedisConnection pubSubClient1 = commandClient.subscribe(key("channel-1"));
        BlockingIterable<PubSubRedisMessage> messages1 = pubSubClient1.messages();
        BlockingIterator<PubSubRedisMessage> iterator1 = messages1.iterator();

        // Publish a test message
        publishTestMessage(key("channel-1"));

        // Check ping request get proper response
        assertThat(pubSubClient1.ping().bufferValue(), is(EMPTY_BUFFER));

        // Subscribe to a pattern on the same connection
        final BlockingPubSubBufferRedisConnection pubSubClient2 = pubSubClient1.psubscribe(key("channel-2*"));
        BlockingIterable<PubSubRedisMessage> messages2 = pubSubClient2.messages();
        BlockingIterator<PubSubRedisMessage> iterator2 = messages2.iterator();

        // Let's throw a wrench and psubscribe a second time to the same pattern
        final BlockingPubSubBufferRedisConnection pubSubClient3 = pubSubClient1.psubscribe(key("channel-2*"));
        BlockingIterable<PubSubRedisMessage> messages3 = pubSubClient3.messages();
        BlockingIterator<PubSubRedisMessage> iterator3 = messages3.iterator();
        try {
            iterator3.next();
            fail("Should have failed");
        } catch (RejectedSubscribeException e) {
            // Expected
        }

        // Publish another test message
        publishTestMessage(key("channel-202"));

        // Check ping request get proper response
        assertThat(pubSubClient1.ping(buf("my-pong")).bufferValue(), is(buf("my-pong")));

        final PubSubRedisMessage next1 = iterator1.next();
        assertThat(next1, instanceOf(ChannelPubSubRedisMessage.class));
        final ChannelPubSubRedisMessage channelMessage = (ChannelPubSubRedisMessage) next1;
        assertThat(channelMessage.channel(), equalTo(keyStr("channel-1")));
        assertThat(channelMessage.bufferValue(), equalTo(buf("test-message")));
        // Cancel the subscriber, which issues an UNSUBSCRIBE behind the scenes
        pubSubClient1.close();

        final PubSubRedisMessage next2 = iterator2.next();
        assertThat(next2, instanceOf(PatternPubSubRedisMessage.class));
        final PatternPubSubRedisMessage patternMessage = (PatternPubSubRedisMessage) next2;
        assertThat(patternMessage.channel(), equalTo(keyStr("channel-202")));
        assertThat(patternMessage.pattern(), equalTo(keyStr("channel-2*")));
        assertThat(patternMessage.bufferValue(), equalTo(buf("test-message")));
        // Cancel the subscriber, which issues an PUNSUBSCRIBE behind the scenes
        iterator2.close();

        // It is an error to keep using the pubsub client after all subscriptions have been terminated
        try {
            pubSubClient1.ping();
            fail("Should have failed");
        } catch (RejectedSubscribeException e) {
            // Expected
        }
    }

    private Buffer key(CharSequence key) {
        return buf(key + "-bbrct");
    }

    private static String keyStr(CharSequence key) {
        return key + "-bbrct";
    }
}

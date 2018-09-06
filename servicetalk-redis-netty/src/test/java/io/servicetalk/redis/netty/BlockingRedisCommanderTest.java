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

import io.servicetalk.client.api.RetryableException;
import io.servicetalk.concurrent.BlockingIterable;
import io.servicetalk.concurrent.BlockingIterator;
import io.servicetalk.concurrent.api.Executor;
import io.servicetalk.concurrent.api.Executors;
import io.servicetalk.redis.api.BlockingPubSubRedisConnection;
import io.servicetalk.redis.api.BlockingRedisCommander;
import io.servicetalk.redis.api.BlockingTransactedRedisCommander;
import io.servicetalk.redis.api.PubSubRedisMessage;
import io.servicetalk.redis.api.RedisClientException;
import io.servicetalk.redis.api.RedisProtocolSupport;
import io.servicetalk.redis.api.RedisProtocolSupport.BitfieldOperations.Get;
import io.servicetalk.redis.api.RedisProtocolSupport.BitfieldOperations.Incrby;
import io.servicetalk.redis.api.RedisProtocolSupport.BitfieldOperations.Overflow;
import io.servicetalk.redis.api.RedisProtocolSupport.BitfieldOperations.Set;
import io.servicetalk.redis.api.RedisProtocolSupport.ExpireDuration;
import io.servicetalk.redis.api.RedisProtocolSupport.LongitudeLatitudeMember;
import io.servicetalk.redis.api.RedisServerException;
import io.servicetalk.redis.api.TransactionAbortedException;

import org.hamcrest.Matcher;
import org.junit.Before;
import org.junit.Test;

import java.nio.channels.ClosedChannelException;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.stream.IntStream;

import static io.servicetalk.concurrent.internal.Await.awaitIndefinitely;
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
import static io.servicetalk.redis.netty.SubscribedRedisClientTest.publishTestMessage;
import static java.util.Arrays.asList;
import static java.util.Collections.emptyList;
import static java.util.Collections.singletonList;
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

public class BlockingRedisCommanderTest extends BaseRedisClientTest {
    protected BlockingRedisCommander commandClient;

    @Before
    public void createCommandClient() {
        commandClient = getEnv().client.asBlockingCommander();
    }

    @Test
    public void simpleResponseTypes() throws Exception {
        assertThat(commandClient.ping(), is("PONG"));
        assertThat(commandClient.ping("my-pong"), is("my-pong"));
        assertThat(commandClient.ping(""), is(""));

        assertThat(commandClient.del(key("a-key")), is(greaterThanOrEqualTo(0L)));
        assertThat(commandClient.get("missing-key"), is(nullValue()));
        assertThat(commandClient.set(key("a-key"), "a-value1"), is("OK"));
        assertThat(commandClient.get(key("a-key")), is("a-value1"));
        assertThat(commandClient.set(key("a-key"), "a-value2", new ExpireDuration(EX, 10L), null), is("OK"));
        assertThat(commandClient.get(key("a-key")), is("a-value2"));

        assertThat(commandClient.set(key("exp-key"), "exp-value", null, NX), is(anyOf(nullValue(), equalTo("OK"))));

        assertThat(commandClient.zadd(key("a-zset"), null, null, 1, "one"), is(either(equalTo(0L)).or(equalTo(1L))));
        assertThat(commandClient.zrank(key("a-zset"), "one"), is(0L));
        assertThat(commandClient.zrank("missing-key", "missing-member"), is(nullValue()));
        if (getEnv().serverVersion[0] >= 3) {
            assertThat(commandClient.zaddIncr(key("a-zset"), null, null, 1, "one"), is(2.0));
        }
        if (getEnv().serverVersion[0] >= 5) {
            assertThat(commandClient.zpopmax(key("a-zset")), contains("2", "one"));
        }

        assertThat(commandClient.blpop(singletonList("missing-key"), 1), is(nullValue()));

        assertThat(commandClient.sadd(key("a-set-1"), "a", "b", "c"), is(greaterThanOrEqualTo(0L)));
        assertThat(commandClient.sadd(key("a-set-2"), "c", "d", "e"), is(greaterThanOrEqualTo(0L)));
        assertThat(commandClient.sdiff(key("a-set-1"), key("a-set-2"), "missing-key"), containsInAnyOrder("a", "b"));
        assertThat(commandClient.sdiffstore(key("diff"), key("a-set-1"), key("a-set-2"), "missing-key"), is(2L));
    }

    @Test
    public void argumentVariants() throws Exception {
        assertThat(commandClient.mset(key("key0"), "val0"), is("OK"));
        assertThat(commandClient.mset(key("key1"), "val1", key("key2"), "val2", key("key3"), "val3"), is("OK"));

        final List<RedisProtocolSupport.KeyValue> keyValues = IntStream.range(4, 10)
                .mapToObj(i -> new RedisProtocolSupport.KeyValue(key("key" + i), "val" + i))
                .collect(toList());
        assertThat(commandClient.mset(keyValues), is("OK"));

        assertThat(commandClient.mget(key("key0")), contains("val0"));
        assertThat(commandClient.mget(key("key1"), key("key2"), key("key3")), contains("val1", "val2", "val3"));

        final List<String> keys = keyValues.stream().map(kv -> kv.key.toString()).collect(toList());
        final List<String> expectedValues = keyValues.stream().map(kv -> kv.value.toString()).collect(toList());
        assertThat(commandClient.mget(keys), is(expectedValues));
    }

    @Test
    public void unicodeNotMangled() throws Exception {
        assertThat(commandClient.set(key("\u263A"), "\u263A-foo"), is("OK"));
        assertThat(commandClient.get(key("\u263A")), is("\u263A-foo"));
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
        //     BITFIELD bf-<key> SET u8 #2 200 GET u8 #2 GET u4 #4 GET u4 #5
        results = commandClient.bitfield(key("bf"), asList(
                new Set(U08, U08.getBitSize() * 2, 200L),
                new Get(U08, U08.getBitSize() * 2),
                new Get(U04, U04.getBitSize() * 4),
                new Get(U04, U04.getBitSize() * 5)));
        assertThat(results, contains(0L, 200L, 12L, 8L));
    }

    @Test
    public void commandWithSubCommand() throws Exception {
        assertThat(commandClient.commandInfo("GET"), hasSize(1));
        assertThat(commandClient.objectRefcount("missing-key"), is(nullValue()));
        assertThat(commandClient.objectEncoding("missing-key"), is(nullValue()));
        if (getEnv().serverVersion[0] >= 4) {
            assertThat(commandClient.objectHelp(), hasSize(greaterThanOrEqualTo(5)));
        }
    }

    @Test
    public void infoWithAggregationDoesNotThrow() throws Exception {
        assertThat(commandClient.info().isEmpty(), is(false));
    }

    @SuppressWarnings("unchecked")
    @Test
    public void variableResponseTypes() throws Exception {
        // Disable these tests for Redis 2 and below
        assumeThat(getEnv().serverVersion[0], is(greaterThanOrEqualTo(3)));

        assertThat(commandClient.zrem(key("Sicily"), asList("Palermo", "Catania")), is(lessThanOrEqualTo(2L)));

        final Long geoaddLong = commandClient.geoadd(key("Sicily"),
                asList(new LongitudeLatitudeMember(13.361389d, 38.115556d, "Palermo"),
                        new LongitudeLatitudeMember(15.087269d, 37.502669d, "Catania")));
        assertThat(geoaddLong, is(2L));

        final List<String> georadiusBuffers = commandClient.georadius(key("Sicily"), 15d, 37d, 200d, KM);
        assertThat(georadiusBuffers, contains("Palermo", "Catania"));

        final List georadiusMixedList = commandClient.georadius(key("Sicily"), 15d, 37d, 200d, KM, WITHCOORD, WITHDIST, null, 5L, ASC, null, null);
        final Matcher georadiusResponseMatcher = contains(
                contains(is("Catania"), startsWith("56."), contains(startsWith("15."), startsWith("37."))),
                contains(is("Palermo"), startsWith("190."), contains(startsWith("13."), startsWith("38."))));
        assertThat(georadiusMixedList, georadiusResponseMatcher);

        assertThat(commandClient.geodist(key("Sicily"), "Palermo", "Catania"), is(greaterThan(0d)));
        assertThat(commandClient.geodist(key("Sicily"), "foo", "bar"), is(nullValue()));

        assertThat(commandClient.geopos(key("Sicily"), "Palermo", "NonExisting", "Catania"), contains(
                contains(startsWith("13."), startsWith("38.")), is(nullValue()), contains(startsWith("15."), startsWith("37."))));

        final List<String> evalBuffers = commandClient.evalList("return {KEYS[1],KEYS[2],ARGV[1],ARGV[2]}",
                2L, asList("key1", "key2"), asList("first", "second"));
        assertThat(evalBuffers, contains("key1", "key2", "first", "second"));

        final String evalChars = commandClient.eval("return redis.call('set','foo','bar')",
                0L, emptyList(), emptyList());
        assertThat(evalChars, is("OK"));

        final Long evalLong = commandClient.evalLong("return 10", 0L, emptyList(), emptyList());
        assertThat(evalLong, is(10L));

        final List<?> evalMixedList = commandClient.evalList("return {1,2,{3,'four'}}", 0L, emptyList(), emptyList());
        assertThat(evalMixedList, contains(1L, 2L, asList(3L, "four")));
    }

    @Test
    public void transactionExec() throws Exception {
        BlockingTransactedRedisCommander tcc = commandClient.multi();
        Future<Long> value1 = tcc.del(key("a-key"));
        Future<String> value2 = tcc.set(key("a-key"), "a-value3");
        Future<String> value3 = tcc.ping("in-transac");
        Future<String> value4 = tcc.get(key("a-key"));
        tcc.exec();
        assertThat(value1.get(), is(1L));
        assertThat(value2.get(), is("OK"));
        assertThat(value3.get(), is("in-transac"));
        assertThat(value4.get(), is("a-value3"));
    }

    @Test
    public void transactionDiscard() throws Exception {
        BlockingTransactedRedisCommander tcc = commandClient.multi();
        Future<String> future = tcc.ping("in-transac");
        final String result = tcc.discard();

        assertThat(result, is("OK"));

        thrown.expect(ExecutionException.class);
        thrown.expectCause(instanceOf(TransactionAbortedException.class));
        future.get();
    }

    @Test
    public void transactionPartialFailure() throws Exception {
        BlockingTransactedRedisCommander tcc = commandClient.multi();

        final Future<String> r1 = tcc.set(key("ptf"), "foo");
        final Future<String> r2 = tcc.lpop(key("ptf"));

        tcc.exec();

        assertThat(r1.get(), is("OK"));

        thrown.expect(ExecutionException.class);
        thrown.expectCause(instanceOf(RedisServerException.class));
        thrown.expectCause(hasProperty("message", startsWith("WRONGTYPE")));
        r2.get();
    }

    @Test
    public void transactionClose() throws Exception {
        BlockingTransactedRedisCommander tcc = commandClient.multi();
        tcc.close();

        thrown.expect(RedisClientException.class);
        thrown.expectCause(instanceOf(ClosedChannelException.class));
        tcc.ping();
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
        final BlockingPubSubRedisConnection pubSubClient1 = commandClient.subscribe(key("channel-1"));
        BlockingIterable<PubSubRedisMessage> messages1 = pubSubClient1.getMessages();
        BlockingIterator<PubSubRedisMessage> iterator1 = messages1.iterator();

        // Publish a test message
        publishTestMessage(key("channel-1"));

        // Check ping request get proper response
        assertThat(pubSubClient1.ping().getCharSequenceValue(), is(""));

        // Subscribe to a pattern on the same connection
        final BlockingPubSubRedisConnection pubSubClient2 = pubSubClient1.psubscribe(key("channel-2*"));
        BlockingIterable<PubSubRedisMessage> messages2 = pubSubClient2.getMessages();
        BlockingIterator<PubSubRedisMessage> iterator2 = messages2.iterator();

        // Let's throw a wrench and psubscribe a second time to the same pattern
        final BlockingPubSubRedisConnection pubSubClient3 = pubSubClient1.psubscribe(key("channel-2*"));
        BlockingIterable<PubSubRedisMessage> messages3 = pubSubClient3.getMessages();
        BlockingIterator<PubSubRedisMessage> iterator3 = messages3.iterator();
        try {
            iterator3.next();
            fail("Should have failed");
        } catch (IllegalStateException e) {
            // Expected
        }

        // Publish another test message
        publishTestMessage(key("channel-202"));

        // Check ping request get proper response
        assertThat(pubSubClient1.ping("my-pong").getCharSequenceValue(), is("my-pong"));

        // Cancel the subscriber, which issues an UNSUBSCRIBE behind the scenes
        pubSubClient1.close();
        assertThat(iterator1.next(), allOf(
                hasProperty("channel", equalTo(key("channel-1"))),
                hasProperty("charSequenceValue", equalTo("test-message"))));

        // Check the second psubscribe subscription never got anything beyond the subscription confirmation
        iterator3.close();

        // Cancel the subscriber, which issues an PUNSUBSCRIBE behind the scenes
        iterator2.close();

        assertThat(iterator2.next(), allOf(
                hasProperty("channel", equalTo(key("channel-202"))),
                hasProperty("pattern", equalTo(key("channel-2*"))),
                hasProperty("charSequenceValue", equalTo("test-message"))));

        // It is an error to keep using the pubsub client after all subscriptions have been terminated
        try {
            pubSubClient1.ping();
            fail("Should have failed");
        } catch (final RetryableException expected) {
            // Expected
        }
    }

    private static String key(CharSequence key) {
        return key + "-brct";
    }
}

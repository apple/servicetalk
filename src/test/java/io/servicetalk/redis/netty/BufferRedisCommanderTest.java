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
import io.servicetalk.redis.api.BufferRedisCommander;
import io.servicetalk.redis.api.PubSubBufferRedisConnection;
import io.servicetalk.redis.api.PubSubRedisMessage;
import io.servicetalk.redis.api.RedisException;
import io.servicetalk.redis.api.RedisProtocolSupport;
import io.servicetalk.redis.api.RedisProtocolSupport.BitfieldOperations.Get;
import io.servicetalk.redis.api.RedisProtocolSupport.BitfieldOperations.Incrby;
import io.servicetalk.redis.api.RedisProtocolSupport.BitfieldOperations.Overflow;
import io.servicetalk.redis.api.RedisProtocolSupport.BitfieldOperations.Set;
import io.servicetalk.redis.api.RedisProtocolSupport.BufferLongitudeLatitudeMember;
import io.servicetalk.redis.api.RedisProtocolSupport.ExpireDuration;
import io.servicetalk.redis.api.TransactedBufferRedisCommander;
import io.servicetalk.redis.netty.SubscribedRedisClientTest.AccumulatingSubscriber;

import org.hamcrest.Matcher;
import org.junit.Before;
import org.junit.Test;

import java.nio.channels.ClosedChannelException;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.stream.IntStream;

import static io.servicetalk.buffer.api.EmptyBuffer.EMPTY_BUFFER;
import static io.servicetalk.concurrent.internal.Await.awaitIndefinitely;
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
import static io.servicetalk.redis.netty.SubscribedRedisClientTest.publishTestMessage;
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
import static org.hamcrest.Matchers.empty;
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
        commandClient = getEnv().client.asBufferCommander();
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
    public void infoWithAggregationDoesNotThrow() throws ExecutionException, InterruptedException {
        assertThat(awaitIndefinitely(commandClient.info()).getReadableBytes(), is(greaterThan(0)));
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
    public void recoverableError() throws Exception {
        try {
            awaitIndefinitely(commandClient.eval(buf("bad"), 0L, emptyList(), emptyList()));
            fail("Expected ExecutionException");
        } catch (final ExecutionException ee) {
            assertThat(ee.getCause(), is(instanceOf(RedisException.class)));
            assertThat(awaitIndefinitely(commandClient.ping()), is("PONG"));
        }
    }

    @Test
    public void transactionEmpty() throws Exception {
        final List<?> results = awaitIndefinitely(commandClient.multi().flatMap(TransactedBufferRedisCommander::exec));
        assertThat(results, is(empty()));
    }

    @Test
    public void transactionExec() throws Exception {
        final List<?> results = awaitIndefinitely(commandClient.multi()
                .flatMap(tcc -> tcc.del(key("a-key"))
                        .flatMap($ -> tcc.set(key("a-key"), buf("a-value3")))
                        .flatMap($ -> tcc.ping(buf("in-transac")))
                        .flatMap($ -> tcc.get(key("a-key")))
                        .flatMap($ -> tcc.exec())));

        assertThat(results, contains(1L, "OK", buf("in-transac"), buf("a-value3")));
    }

    @Test
    public void transactionDiscard() throws Exception {
        final String result = awaitIndefinitely(commandClient.multi()
                .flatMap(tcc -> tcc.ping(buf("in-transac"))
                        .flatMap($ -> tcc.discard())));

        assertThat(result, is("OK"));
    }

    @Test
    public void transactionPartialFailure() throws Exception {
        final List<?> results = awaitIndefinitely(commandClient.multi()
                .flatMap(tcc -> tcc.set(key("ptf"), buf("foo"))
                        .flatMap($ -> tcc.lpop(key("ptf")))
                        .flatMap($ -> tcc.exec())));

        assertThat(results, contains(is("OK"), instanceOf(RedisException.class)));
        assertThat(((RedisException) results.get(1)).getMessage(), startsWith("WRONGTYPE"));
    }

    @Test
    public void transactionCloseAsync() throws Exception {
        thrown.expect(ExecutionException.class);
        thrown.expectCause(is(instanceOf(ClosedChannelException.class)));

        awaitIndefinitely(commandClient.multi().flatMap(tcc -> tcc.closeAsync().andThen(tcc.ping())));
    }

    @Test
    public void monitor() throws Exception {
        final AccumulatingSubscriber<String> subscriber = new AccumulatingSubscriber<>();
        CountDownLatch cancelled = new CountDownLatch(1);

        assertThat(subscriber
                .subscribe(commandClient.monitor()
                        .doAfterNext($ -> awaitIndefinitelyUnchecked(commandClient.ping().ignoreResult()))
                        .doAfterCancel(cancelled::countDown))
                .request(2)
                .awaitUntilAtLeastNReceived(2, DEFAULT_TIMEOUT_SECONDS, SECONDS), is(true));

        assertThat(subscriber.getReceived(), contains(is("OK"), endsWith("\"PING\"")));

        subscriber.cancel();
        assertThat(cancelled.await(DEFAULT_TIMEOUT_SECONDS, SECONDS), is(true));
    }

    @Test
    public void pubSubMultipleSubscribes() throws Exception {
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
        assertThat(subscriber3.getTerminal().getCause(), is(instanceOf(IllegalStateException.class)));

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

    private static Buffer key(CharSequence key) {
        return buf(key + "-brct");
    }

    private static String keyStr(CharSequence key) {
        return key + "-brct";
    }
}

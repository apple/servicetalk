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
package io.servicetalk.redis.api;

import io.servicetalk.buffer.api.Buffer;
import io.servicetalk.redis.api.RedisData.ArraySize;
import io.servicetalk.redis.api.RedisData.BulkStringChunk;
import io.servicetalk.redis.api.RedisData.BulkStringSize;
import io.servicetalk.redis.api.RedisData.CompleteBulkString;
import io.servicetalk.redis.api.RedisData.LastBulkStringChunk;
import io.servicetalk.redis.api.RedisData.RequestRedisData;
import io.servicetalk.redis.api.RedisData.SimpleString;
import io.servicetalk.redis.api.RedisProtocolSupport.BitfieldOperations;
import io.servicetalk.redis.api.RedisProtocolSupport.BitfieldOverflow;
import io.servicetalk.redis.api.RedisProtocolSupport.BufferFieldValue;
import io.servicetalk.redis.api.RedisProtocolSupport.BufferGroupConsumer;
import io.servicetalk.redis.api.RedisProtocolSupport.BufferKeyValue;
import io.servicetalk.redis.api.RedisProtocolSupport.BufferLongitudeLatitudeMember;
import io.servicetalk.redis.api.RedisProtocolSupport.BufferScoreMember;
import io.servicetalk.redis.api.RedisProtocolSupport.ClientKillType;
import io.servicetalk.redis.api.RedisProtocolSupport.ClientReplyReplyMode;
import io.servicetalk.redis.api.RedisProtocolSupport.ClusterFailoverOptions;
import io.servicetalk.redis.api.RedisProtocolSupport.ClusterResetResetType;
import io.servicetalk.redis.api.RedisProtocolSupport.ClusterSetslotSubcommand;
import io.servicetalk.redis.api.RedisProtocolSupport.Command;
import io.servicetalk.redis.api.RedisProtocolSupport.ExpireDuration;
import io.servicetalk.redis.api.RedisProtocolSupport.FieldValue;
import io.servicetalk.redis.api.RedisProtocolSupport.FlushallAsync;
import io.servicetalk.redis.api.RedisProtocolSupport.FlushdbAsync;
import io.servicetalk.redis.api.RedisProtocolSupport.GeoradiusOrder;
import io.servicetalk.redis.api.RedisProtocolSupport.GeoradiusUnit;
import io.servicetalk.redis.api.RedisProtocolSupport.GeoradiusWithcoord;
import io.servicetalk.redis.api.RedisProtocolSupport.GeoradiusWithdist;
import io.servicetalk.redis.api.RedisProtocolSupport.GeoradiusWithhash;
import io.servicetalk.redis.api.RedisProtocolSupport.GeoradiusbymemberOrder;
import io.servicetalk.redis.api.RedisProtocolSupport.GeoradiusbymemberUnit;
import io.servicetalk.redis.api.RedisProtocolSupport.GeoradiusbymemberWithcoord;
import io.servicetalk.redis.api.RedisProtocolSupport.GeoradiusbymemberWithdist;
import io.servicetalk.redis.api.RedisProtocolSupport.GeoradiusbymemberWithhash;
import io.servicetalk.redis.api.RedisProtocolSupport.GroupConsumer;
import io.servicetalk.redis.api.RedisProtocolSupport.IntegerType;
import io.servicetalk.redis.api.RedisProtocolSupport.KeyValue;
import io.servicetalk.redis.api.RedisProtocolSupport.LinsertWhere;
import io.servicetalk.redis.api.RedisProtocolSupport.LongitudeLatitudeMember;
import io.servicetalk.redis.api.RedisProtocolSupport.OffsetCount;
import io.servicetalk.redis.api.RedisProtocolSupport.RestoreReplace;
import io.servicetalk.redis.api.RedisProtocolSupport.ScoreMember;
import io.servicetalk.redis.api.RedisProtocolSupport.ScriptDebugMode;
import io.servicetalk.redis.api.RedisProtocolSupport.SetCondition;
import io.servicetalk.redis.api.RedisProtocolSupport.SetExpire;
import io.servicetalk.redis.api.RedisProtocolSupport.ShutdownSaveMode;
import io.servicetalk.redis.api.RedisProtocolSupport.SortOrder;
import io.servicetalk.redis.api.RedisProtocolSupport.SortSorting;
import io.servicetalk.redis.api.RedisProtocolSupport.SubCommand;
import io.servicetalk.redis.api.RedisProtocolSupport.XreadStreams;
import io.servicetalk.redis.api.RedisProtocolSupport.XreadgroupStreams;
import io.servicetalk.redis.api.RedisProtocolSupport.ZaddChange;
import io.servicetalk.redis.api.RedisProtocolSupport.ZaddCondition;
import io.servicetalk.redis.api.RedisProtocolSupport.ZaddIncrement;
import io.servicetalk.redis.api.RedisProtocolSupport.ZinterstoreAggregate;
import io.servicetalk.redis.api.RedisProtocolSupport.ZrangeWithscores;
import io.servicetalk.redis.api.RedisProtocolSupport.ZrangebyscoreWithscores;
import io.servicetalk.redis.api.RedisProtocolSupport.ZrevrangeWithscores;
import io.servicetalk.redis.api.RedisProtocolSupport.ZrevrangebyscoreWithscores;
import io.servicetalk.redis.api.RedisProtocolSupport.ZunionstoreAggregate;

import org.junit.Test;

import static io.servicetalk.buffer.netty.BufferAllocators.DEFAULT_ALLOCATOR;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.hamcrest.Matchers.equalTo;
import static org.junit.Assert.assertThat;

public class RequestRedisDataTest {

    public static final Buffer BUFFER_ABCDE = DEFAULT_ALLOCATOR.fromUtf8("abcde");
    public static final Buffer BUFFER_FGHIJ = DEFAULT_ALLOCATOR.fromUtf8("fghij");

    @Test
    public void testSimpleString() {
        assertWritten(new SimpleString("abcde"), "$5\r\nabcde\r\n");
    }

    @Test
    public void testInteger() {
        assertWritten(RedisData.Integer.newInstance(12345), "$5\r\n12345\r\n");
    }

    @Test
    public void testBulkStringSize() {
        assertWritten(new BulkStringSize(12345), "$12345\r\n");
    }

    @Test
    public void testBulkStringChunk() {
        assertWritten(new BulkStringChunk(BUFFER_ABCDE.duplicate()), "abcde");
    }

    @Test
    public void testLastBulkStringChunk() {
        assertWritten(new LastBulkStringChunk(BUFFER_ABCDE.duplicate()), "abcde\r\n");
    }

    @Test
    public void testCompleteBulkString() {
        assertWritten(new CompleteBulkString(BUFFER_ABCDE.duplicate()), "$5\r\nabcde\r\n");
    }

    @Test
    public void testArraySize() {
        assertWritten(new ArraySize(12345), "*12345\r\n");
    }

    @Test
    public void testIntegerType() {
        assertWritten(IntegerType.U04, "$2\r\nu4\r\n");
        assertWritten(IntegerType.U32, "$3\r\nu32\r\n");
    }

    @Test
    public void testCommand() {
        assertWritten(Command.APPEND, "$6\r\nAPPEND\r\n");
    }

    @Test
    public void testSetExpire() {
        assertWritten(SetExpire.EX, "$2\r\nEX\r\n");
    }

    @Test
    public void testExpireDuration() {
        assertWritten(new ExpireDuration(SetExpire.EX, 12345), "$2\r\nEX\r\n$5\r\n12345\r\n");
    }

    @Test
    public void testSubCommand() {
        assertWritten(SubCommand.ADDR, "$4\r\nADDR\r\n");
    }

    @Test
    public void testBitfieldOverflow() {
        assertWritten(BitfieldOverflow.FAIL, "$4\r\nFAIL\r\n");
    }

    @Test
    public void testClientKillType() {
        assertWritten(ClientKillType.MASTER, "$6\r\nmaster\r\n");
    }

    @Test
    public void testClientReplyReplyMode() {
        assertWritten(ClientReplyReplyMode.OFF, "$3\r\nOFF\r\n");
    }

    @Test
    public void testClusterFailoverOptions() {
        assertWritten(ClusterFailoverOptions.FORCE, "$5\r\nFORCE\r\n");
    }

    @Test
    public void testClusterResetResetType() {
        assertWritten(ClusterResetResetType.HARD, "$4\r\nHARD\r\n");
    }

    @Test
    public void testClusterSetslotSubcommand() {
        assertWritten(ClusterSetslotSubcommand.IMPORTING, "$9\r\nIMPORTING\r\n");
    }

    @Test
    public void testFlushallAsync() {
        assertWritten(FlushallAsync.ASYNC, "$5\r\nASYNC\r\n");
    }

    @Test
    public void testFlushdbAsync() {
        assertWritten(FlushdbAsync.ASYNC, "$5\r\nASYNC\r\n");
    }

    @Test
    public void testGeoradiusOrder() {
        assertWritten(GeoradiusOrder.ASC, "$3\r\nASC\r\n");
    }

    @Test
    public void testGeoradiusUnit() {
        assertWritten(GeoradiusUnit.FT, "$2\r\nft\r\n");
    }

    @Test
    public void testGeoradiusWithcoord() {
        assertWritten(GeoradiusWithcoord.WITHCOORD, "$9\r\nWITHCOORD\r\n");
    }

    @Test
    public void testGeoradiusWithdist() {
        assertWritten(GeoradiusWithdist.WITHDIST, "$8\r\nWITHDIST\r\n");
    }

    @Test
    public void testGeoradiusWithhash() {
        assertWritten(GeoradiusWithhash.WITHHASH, "$8\r\nWITHHASH\r\n");
    }

    @Test
    public void testGeoradiusbymemberOrder() {
        assertWritten(GeoradiusbymemberOrder.ASC, "$3\r\nASC\r\n");
    }

    @Test
    public void testGeoradiusbymemberUnit() {
        assertWritten(GeoradiusbymemberUnit.FT, "$2\r\nft\r\n");
    }

    @Test
    public void testGeoradiusbymemberWithcoord() {
        assertWritten(GeoradiusbymemberWithcoord.WITHCOORD, "$9\r\nWITHCOORD\r\n");
    }

    @Test
    public void testGeoradiusbymemberWithdist() {
        assertWritten(GeoradiusbymemberWithdist.WITHDIST, "$8\r\nWITHDIST\r\n");
    }

    @Test
    public void testGeoradiusbymemberWithhash() {
        assertWritten(GeoradiusbymemberWithhash.WITHHASH, "$8\r\nWITHHASH\r\n");
    }

    @Test
    public void testLinsertWhere() {
        assertWritten(LinsertWhere.AFTER, "$5\r\nAFTER\r\n");
    }

    @Test
    public void testRestoreReplace() {
        assertWritten(RestoreReplace.REPLACE, "$7\r\nREPLACE\r\n");
    }

    @Test
    public void testScriptDebugMode() {
        assertWritten(ScriptDebugMode.NO, "$2\r\nNO\r\n");
    }

    @Test
    public void testSetCondition() {
        assertWritten(SetCondition.NX, "$2\r\nNX\r\n");
    }

    @Test
    public void testShutdownSaveMode() {
        assertWritten(ShutdownSaveMode.NOSAVE, "$6\r\nNOSAVE\r\n");
    }

    @Test
    public void testSortOrder() {
        assertWritten(SortOrder.ASC, "$3\r\nASC\r\n");
    }

    @Test
    public void testSortSorting() {
        assertWritten(SortSorting.ALPHA, "$5\r\nALPHA\r\n");
    }

    @Test
    public void testXreadStreams() {
        assertWritten(XreadStreams.STREAMS, "$7\r\nSTREAMS\r\n");
    }

    @Test
    public void testXreadgroupStreams() {
        assertWritten(XreadgroupStreams.STREAMS, "$7\r\nSTREAMS\r\n");
    }

    @Test
    public void testZaddChange() {
        assertWritten(ZaddChange.CH, "$2\r\nCH\r\n");
    }

    @Test
    public void testZaddCondition() {
        assertWritten(ZaddCondition.NX, "$2\r\nNX\r\n");
    }

    @Test
    public void testZaddIncrement() {
        assertWritten(ZaddIncrement.INCR, "$4\r\nINCR\r\n");
    }

    @Test
    public void testZinterstoreAggregate() {
        assertWritten(ZinterstoreAggregate.MAX, "$3\r\nMAX\r\n");
    }

    @Test
    public void testZrangeWithscores() {
        assertWritten(ZrangeWithscores.WITHSCORES, "$10\r\nWITHSCORES\r\n");
    }

    @Test
    public void testZrangebyscoreWithscores() {
        assertWritten(ZrangebyscoreWithscores.WITHSCORES, "$10\r\nWITHSCORES\r\n");
    }

    @Test
    public void testZrevrangeWithscores() {
        assertWritten(ZrevrangeWithscores.WITHSCORES, "$10\r\nWITHSCORES\r\n");
    }

    @Test
    public void testZrevrangebyscoreWithscores() {
        assertWritten(ZrevrangebyscoreWithscores.WITHSCORES, "$10\r\nWITHSCORES\r\n");
    }

    @Test
    public void testZunionstoreAggregate() {
        assertWritten(ZunionstoreAggregate.MAX, "$3\r\nMAX\r\n");
    }

    @Test
    public void testBufferFieldValue() {
        assertWritten(new BufferFieldValue(BUFFER_ABCDE, BUFFER_FGHIJ), "$5\r\nabcde\r\n$5\r\nfghij\r\n");
    }

    @Test
    public void testFieldValue() {
        assertWritten(new FieldValue("abcde", "fghij"), "$5\r\nabcde\r\n$5\r\nfghij\r\n");
    }

    @Test
    public void testBufferGroupConsumer() {
        assertWritten(new BufferGroupConsumer(BUFFER_ABCDE, BUFFER_FGHIJ), "$5\r\nGROUP\r\n$5\r\nabcde\r\n$5\r\nfghij\r\n");
    }

    @Test
    public void testGroupConsumer() {
        assertWritten(new GroupConsumer("abcde", "fghij"), "$5\r\nGROUP\r\n$5\r\nabcde\r\n$5\r\nfghij\r\n");
    }

    @Test
    public void testBufferKeyValue() {
        assertWritten(new BufferKeyValue(BUFFER_ABCDE, BUFFER_FGHIJ), "$5\r\nabcde\r\n$5\r\nfghij\r\n");
    }

    @Test
    public void testKeyValue() {
        assertWritten(new KeyValue("abcde", "fghij"), "$5\r\nabcde\r\n$5\r\nfghij\r\n");
    }

    @Test
    public void testBufferLongitudeLatitudeMember() {
        assertWritten(new BufferLongitudeLatitudeMember(-123.1, 49.25, BUFFER_ABCDE), "$6\r\n-123.1\r\n$5\r\n49.25\r\n$5\r\nabcde\r\n");
    }

    @Test
    public void testLongitudeLatitudeMember() {
        assertWritten(new LongitudeLatitudeMember(-123.1, 49.25, "abcde"), "$6\r\n-123.1\r\n$5\r\n49.25\r\n$5\r\nabcde\r\n");
    }

    @Test
    public void testBufferScoreMember() {
        assertWritten(new BufferScoreMember(123.45, BUFFER_ABCDE), "$6\r\n123.45\r\n$5\r\nabcde\r\n");
    }

    @Test
    public void testScoreMember() {
        assertWritten(new ScoreMember(123.45, "abcde"), "$6\r\n123.45\r\n$5\r\nabcde\r\n");
    }

    @Test
    public void testOffsetCount() {
        assertWritten(new OffsetCount(12345, 678), "$5\r\nLIMIT\r\n$5\r\n12345\r\n$3\r\n678\r\n");
    }

    @Test
    public void testBitfieldOperationsGet() {
        assertWritten(new BitfieldOperations.Get(IntegerType.U42, 12345), "$3\r\nGET\r\n$3\r\nu42\r\n$5\r\n12345\r\n");
    }

    @Test
    public void testBitfieldOperationsIncrby() {
        assertWritten(new BitfieldOperations.Incrby(IntegerType.U42, 12345, 678), "$6\r\nINCRBY\r\n$3\r\nu42\r\n$5\r\n12345\r\n$3\r\n678\r\n");
    }

    @Test
    public void testBitfieldOperationsSet() {
        assertWritten(new BitfieldOperations.Set(IntegerType.U42, 12345, 678), "$3\r\nSET\r\n$3\r\nu42\r\n$5\r\n12345\r\n$3\r\n678\r\n");
    }

    @Test
    public void testBitfieldOperationsOverflow() {
        assertWritten(new BitfieldOperations.Overflow(BitfieldOverflow.FAIL), "$8\r\nOVERFLOW\r\n$4\r\nFAIL\r\n");
    }

    private void assertWritten(final RequestRedisData data, final String expected) {
        Buffer buffer = data.asBuffer(DEFAULT_ALLOCATOR);
        assertThat(buffer.toString(UTF_8), equalTo(expected));
        int expectedLength = expected.length();
        assertThat("encodedByteCount() did not calculate correct length", data.encodedByteCount(), equalTo(expectedLength));
        assertThat("buffer.readableBytes() was not as expected", buffer.readableBytes(), equalTo(expectedLength));
        assertThat("buffer capacity was not as expected", buffer.capacity(), equalTo(expectedLength));
    }
}

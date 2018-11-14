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
import io.servicetalk.concurrent.BlockingIterable;

import java.util.Collection;
import java.util.List;
import java.util.Objects;
import javax.annotation.Generated;
import javax.annotation.Nullable;

import static io.servicetalk.redis.api.BlockingUtils.blockingInvocation;

@Generated({})
@SuppressWarnings("unchecked")
final class PartitionedBufferRedisCommanderToBlockingPartitionedBufferRedisCommander extends BlockingBufferRedisCommander {

    private final BufferRedisCommander commander;

    PartitionedBufferRedisCommanderToBlockingPartitionedBufferRedisCommander(final BufferRedisCommander commander) {
        this.commander = Objects.requireNonNull(commander);
    }

    @Override
    public void close() throws Exception {
        blockingInvocation(commander.closeAsync());
    }

    @Override
    public Long append(@RedisProtocolSupport.Key final Buffer key, final Buffer value) throws Exception {
        return blockingInvocation(commander.append(key, value));
    }

    @Override
    public String auth(final Buffer password) throws Exception {
        return blockingInvocation(commander.auth(password));
    }

    @Override
    public String bgrewriteaof() throws Exception {
        return blockingInvocation(commander.bgrewriteaof());
    }

    @Override
    public String bgsave() throws Exception {
        return blockingInvocation(commander.bgsave());
    }

    @Override
    public Long bitcount(@RedisProtocolSupport.Key final Buffer key) throws Exception {
        return blockingInvocation(commander.bitcount(key));
    }

    @Override
    public Long bitcount(@RedisProtocolSupport.Key final Buffer key, @Nullable final Long start,
                         @Nullable final Long end) throws Exception {
        return blockingInvocation(commander.bitcount(key, start, end));
    }

    @Override
    public List<Long> bitfield(@RedisProtocolSupport.Key final Buffer key,
                               final Collection<RedisProtocolSupport.BitfieldOperation> operations) throws Exception {
        return blockingInvocation(commander.bitfield(key, operations));
    }

    @Override
    public Long bitop(final Buffer operation, @RedisProtocolSupport.Key final Buffer destkey,
                      @RedisProtocolSupport.Key final Buffer key) throws Exception {
        return blockingInvocation(commander.bitop(operation, destkey, key));
    }

    @Override
    public Long bitop(final Buffer operation, @RedisProtocolSupport.Key final Buffer destkey,
                      @RedisProtocolSupport.Key final Buffer key1,
                      @RedisProtocolSupport.Key final Buffer key2) throws Exception {
        return blockingInvocation(commander.bitop(operation, destkey, key1, key2));
    }

    @Override
    public Long bitop(final Buffer operation, @RedisProtocolSupport.Key final Buffer destkey,
                      @RedisProtocolSupport.Key final Buffer key1, @RedisProtocolSupport.Key final Buffer key2,
                      @RedisProtocolSupport.Key final Buffer key3) throws Exception {
        return blockingInvocation(commander.bitop(operation, destkey, key1, key2, key3));
    }

    @Override
    public Long bitop(final Buffer operation, @RedisProtocolSupport.Key final Buffer destkey,
                      @RedisProtocolSupport.Key final Collection<Buffer> keys) throws Exception {
        return blockingInvocation(commander.bitop(operation, destkey, keys));
    }

    @Override
    public Long bitpos(@RedisProtocolSupport.Key final Buffer key, final long bit) throws Exception {
        return blockingInvocation(commander.bitpos(key, bit));
    }

    @Override
    public Long bitpos(@RedisProtocolSupport.Key final Buffer key, final long bit, @Nullable final Long start,
                       @Nullable final Long end) throws Exception {
        return blockingInvocation(commander.bitpos(key, bit, start, end));
    }

    @Override
    public <T> List<T> blpop(@RedisProtocolSupport.Key final Collection<Buffer> keys,
                             final long timeout) throws Exception {
        return blockingInvocation(commander.blpop(keys, timeout));
    }

    @Override
    public <T> List<T> brpop(@RedisProtocolSupport.Key final Collection<Buffer> keys,
                             final long timeout) throws Exception {
        return blockingInvocation(commander.brpop(keys, timeout));
    }

    @Override
    public Buffer brpoplpush(@RedisProtocolSupport.Key final Buffer source,
                             @RedisProtocolSupport.Key final Buffer destination, final long timeout) throws Exception {
        return blockingInvocation(commander.brpoplpush(source, destination, timeout));
    }

    @Override
    public <T> List<T> bzpopmax(@RedisProtocolSupport.Key final Collection<Buffer> keys,
                                final long timeout) throws Exception {
        return blockingInvocation(commander.bzpopmax(keys, timeout));
    }

    @Override
    public <T> List<T> bzpopmin(@RedisProtocolSupport.Key final Collection<Buffer> keys,
                                final long timeout) throws Exception {
        return blockingInvocation(commander.bzpopmin(keys, timeout));
    }

    @Override
    public Long clientKill(@Nullable final Long id, @Nullable final RedisProtocolSupport.ClientKillType type,
                           @Nullable final Buffer addrIpPort, @Nullable final Buffer skipmeYesNo) throws Exception {
        return blockingInvocation(commander.clientKill(id, type, addrIpPort, skipmeYesNo));
    }

    @Override
    public Buffer clientList() throws Exception {
        return blockingInvocation(commander.clientList());
    }

    @Override
    public Buffer clientGetname() throws Exception {
        return blockingInvocation(commander.clientGetname());
    }

    @Override
    public String clientPause(final long timeout) throws Exception {
        return blockingInvocation(commander.clientPause(timeout));
    }

    @Override
    public String clientReply(final RedisProtocolSupport.ClientReplyReplyMode replyMode) throws Exception {
        return blockingInvocation(commander.clientReply(replyMode));
    }

    @Override
    public String clientSetname(final Buffer connectionName) throws Exception {
        return blockingInvocation(commander.clientSetname(connectionName));
    }

    @Override
    public String clusterAddslots(final long slot) throws Exception {
        return blockingInvocation(commander.clusterAddslots(slot));
    }

    @Override
    public String clusterAddslots(final long slot1, final long slot2) throws Exception {
        return blockingInvocation(commander.clusterAddslots(slot1, slot2));
    }

    @Override
    public String clusterAddslots(final long slot1, final long slot2, final long slot3) throws Exception {
        return blockingInvocation(commander.clusterAddslots(slot1, slot2, slot3));
    }

    @Override
    public String clusterAddslots(final Collection<Long> slots) throws Exception {
        return blockingInvocation(commander.clusterAddslots(slots));
    }

    @Override
    public Long clusterCountFailureReports(final Buffer nodeId) throws Exception {
        return blockingInvocation(commander.clusterCountFailureReports(nodeId));
    }

    @Override
    public Long clusterCountkeysinslot(final long slot) throws Exception {
        return blockingInvocation(commander.clusterCountkeysinslot(slot));
    }

    @Override
    public String clusterDelslots(final long slot) throws Exception {
        return blockingInvocation(commander.clusterDelslots(slot));
    }

    @Override
    public String clusterDelslots(final long slot1, final long slot2) throws Exception {
        return blockingInvocation(commander.clusterDelslots(slot1, slot2));
    }

    @Override
    public String clusterDelslots(final long slot1, final long slot2, final long slot3) throws Exception {
        return blockingInvocation(commander.clusterDelslots(slot1, slot2, slot3));
    }

    @Override
    public String clusterDelslots(final Collection<Long> slots) throws Exception {
        return blockingInvocation(commander.clusterDelslots(slots));
    }

    @Override
    public String clusterFailover() throws Exception {
        return blockingInvocation(commander.clusterFailover());
    }

    @Override
    public String clusterFailover(@Nullable final RedisProtocolSupport.ClusterFailoverOptions options) throws Exception {
        return blockingInvocation(commander.clusterFailover(options));
    }

    @Override
    public String clusterForget(final Buffer nodeId) throws Exception {
        return blockingInvocation(commander.clusterForget(nodeId));
    }

    @Override
    public <T> List<T> clusterGetkeysinslot(final long slot, final long count) throws Exception {
        return blockingInvocation(commander.clusterGetkeysinslot(slot, count));
    }

    @Override
    public Buffer clusterInfo() throws Exception {
        return blockingInvocation(commander.clusterInfo());
    }

    @Override
    public Long clusterKeyslot(final Buffer key) throws Exception {
        return blockingInvocation(commander.clusterKeyslot(key));
    }

    @Override
    public String clusterMeet(final Buffer ip, final long port) throws Exception {
        return blockingInvocation(commander.clusterMeet(ip, port));
    }

    @Override
    public Buffer clusterNodes() throws Exception {
        return blockingInvocation(commander.clusterNodes());
    }

    @Override
    public String clusterReplicate(final Buffer nodeId) throws Exception {
        return blockingInvocation(commander.clusterReplicate(nodeId));
    }

    @Override
    public String clusterReset() throws Exception {
        return blockingInvocation(commander.clusterReset());
    }

    @Override
    public String clusterReset(@Nullable final RedisProtocolSupport.ClusterResetResetType resetType) throws Exception {
        return blockingInvocation(commander.clusterReset(resetType));
    }

    @Override
    public String clusterSaveconfig() throws Exception {
        return blockingInvocation(commander.clusterSaveconfig());
    }

    @Override
    public String clusterSetConfigEpoch(final long configEpoch) throws Exception {
        return blockingInvocation(commander.clusterSetConfigEpoch(configEpoch));
    }

    @Override
    public String clusterSetslot(final long slot,
                                 final RedisProtocolSupport.ClusterSetslotSubcommand subcommand) throws Exception {
        return blockingInvocation(commander.clusterSetslot(slot, subcommand));
    }

    @Override
    public String clusterSetslot(final long slot, final RedisProtocolSupport.ClusterSetslotSubcommand subcommand,
                                 @Nullable final Buffer nodeId) throws Exception {
        return blockingInvocation(commander.clusterSetslot(slot, subcommand, nodeId));
    }

    @Override
    public Buffer clusterSlaves(final Buffer nodeId) throws Exception {
        return blockingInvocation(commander.clusterSlaves(nodeId));
    }

    @Override
    public <T> List<T> clusterSlots() throws Exception {
        return blockingInvocation(commander.clusterSlots());
    }

    @Override
    public <T> List<T> command() throws Exception {
        return blockingInvocation(commander.command());
    }

    @Override
    public Long commandCount() throws Exception {
        return blockingInvocation(commander.commandCount());
    }

    @Override
    public <T> List<T> commandGetkeys() throws Exception {
        return blockingInvocation(commander.commandGetkeys());
    }

    @Override
    public <T> List<T> commandInfo(final Buffer commandName) throws Exception {
        return blockingInvocation(commander.commandInfo(commandName));
    }

    @Override
    public <T> List<T> commandInfo(final Buffer commandName1, final Buffer commandName2) throws Exception {
        return blockingInvocation(commander.commandInfo(commandName1, commandName2));
    }

    @Override
    public <T> List<T> commandInfo(final Buffer commandName1, final Buffer commandName2,
                                   final Buffer commandName3) throws Exception {
        return blockingInvocation(commander.commandInfo(commandName1, commandName2, commandName3));
    }

    @Override
    public <T> List<T> commandInfo(final Collection<Buffer> commandNames) throws Exception {
        return blockingInvocation(commander.commandInfo(commandNames));
    }

    @Override
    public <T> List<T> configGet(final Buffer parameter) throws Exception {
        return blockingInvocation(commander.configGet(parameter));
    }

    @Override
    public String configRewrite() throws Exception {
        return blockingInvocation(commander.configRewrite());
    }

    @Override
    public String configSet(final Buffer parameter, final Buffer value) throws Exception {
        return blockingInvocation(commander.configSet(parameter, value));
    }

    @Override
    public String configResetstat() throws Exception {
        return blockingInvocation(commander.configResetstat());
    }

    @Override
    public Long dbsize() throws Exception {
        return blockingInvocation(commander.dbsize());
    }

    @Override
    public String debugObject(@RedisProtocolSupport.Key final Buffer key) throws Exception {
        return blockingInvocation(commander.debugObject(key));
    }

    @Override
    public String debugSegfault() throws Exception {
        return blockingInvocation(commander.debugSegfault());
    }

    @Override
    public Long decr(@RedisProtocolSupport.Key final Buffer key) throws Exception {
        return blockingInvocation(commander.decr(key));
    }

    @Override
    public Long decrby(@RedisProtocolSupport.Key final Buffer key, final long decrement) throws Exception {
        return blockingInvocation(commander.decrby(key, decrement));
    }

    @Override
    public Long del(@RedisProtocolSupport.Key final Buffer key) throws Exception {
        return blockingInvocation(commander.del(key));
    }

    @Override
    public Long del(@RedisProtocolSupport.Key final Buffer key1,
                    @RedisProtocolSupport.Key final Buffer key2) throws Exception {
        return blockingInvocation(commander.del(key1, key2));
    }

    @Override
    public Long del(@RedisProtocolSupport.Key final Buffer key1, @RedisProtocolSupport.Key final Buffer key2,
                    @RedisProtocolSupport.Key final Buffer key3) throws Exception {
        return blockingInvocation(commander.del(key1, key2, key3));
    }

    @Override
    public Long del(@RedisProtocolSupport.Key final Collection<Buffer> keys) throws Exception {
        return blockingInvocation(commander.del(keys));
    }

    @Override
    public Buffer dump(@RedisProtocolSupport.Key final Buffer key) throws Exception {
        return blockingInvocation(commander.dump(key));
    }

    @Override
    public Buffer echo(final Buffer message) throws Exception {
        return blockingInvocation(commander.echo(message));
    }

    @Override
    public Buffer eval(final Buffer script, final long numkeys, @RedisProtocolSupport.Key final Collection<Buffer> keys,
                       final Collection<Buffer> args) throws Exception {
        return blockingInvocation(commander.eval(script, numkeys, keys, args));
    }

    @Override
    public <T> List<T> evalList(final Buffer script, final long numkeys,
                                @RedisProtocolSupport.Key final Collection<Buffer> keys,
                                final Collection<Buffer> args) throws Exception {
        return blockingInvocation(commander.evalList(script, numkeys, keys, args));
    }

    @Override
    public Long evalLong(final Buffer script, final long numkeys,
                         @RedisProtocolSupport.Key final Collection<Buffer> keys,
                         final Collection<Buffer> args) throws Exception {
        return blockingInvocation(commander.evalLong(script, numkeys, keys, args));
    }

    @Override
    public Buffer evalsha(final Buffer sha1, final long numkeys,
                          @RedisProtocolSupport.Key final Collection<Buffer> keys,
                          final Collection<Buffer> args) throws Exception {
        return blockingInvocation(commander.evalsha(sha1, numkeys, keys, args));
    }

    @Override
    public <T> List<T> evalshaList(final Buffer sha1, final long numkeys,
                                   @RedisProtocolSupport.Key final Collection<Buffer> keys,
                                   final Collection<Buffer> args) throws Exception {
        return blockingInvocation(commander.evalshaList(sha1, numkeys, keys, args));
    }

    @Override
    public Long evalshaLong(final Buffer sha1, final long numkeys,
                            @RedisProtocolSupport.Key final Collection<Buffer> keys,
                            final Collection<Buffer> args) throws Exception {
        return blockingInvocation(commander.evalshaLong(sha1, numkeys, keys, args));
    }

    @Override
    public Long exists(@RedisProtocolSupport.Key final Buffer key) throws Exception {
        return blockingInvocation(commander.exists(key));
    }

    @Override
    public Long exists(@RedisProtocolSupport.Key final Buffer key1,
                       @RedisProtocolSupport.Key final Buffer key2) throws Exception {
        return blockingInvocation(commander.exists(key1, key2));
    }

    @Override
    public Long exists(@RedisProtocolSupport.Key final Buffer key1, @RedisProtocolSupport.Key final Buffer key2,
                       @RedisProtocolSupport.Key final Buffer key3) throws Exception {
        return blockingInvocation(commander.exists(key1, key2, key3));
    }

    @Override
    public Long exists(@RedisProtocolSupport.Key final Collection<Buffer> keys) throws Exception {
        return blockingInvocation(commander.exists(keys));
    }

    @Override
    public Long expire(@RedisProtocolSupport.Key final Buffer key, final long seconds) throws Exception {
        return blockingInvocation(commander.expire(key, seconds));
    }

    @Override
    public Long expireat(@RedisProtocolSupport.Key final Buffer key, final long timestamp) throws Exception {
        return blockingInvocation(commander.expireat(key, timestamp));
    }

    @Override
    public String flushall() throws Exception {
        return blockingInvocation(commander.flushall());
    }

    @Override
    public String flushall(@Nullable final RedisProtocolSupport.FlushallAsync async) throws Exception {
        return blockingInvocation(commander.flushall(async));
    }

    @Override
    public String flushdb() throws Exception {
        return blockingInvocation(commander.flushdb());
    }

    @Override
    public String flushdb(@Nullable final RedisProtocolSupport.FlushdbAsync async) throws Exception {
        return blockingInvocation(commander.flushdb(async));
    }

    @Override
    public Long geoadd(@RedisProtocolSupport.Key final Buffer key, final double longitude, final double latitude,
                       final Buffer member) throws Exception {
        return blockingInvocation(commander.geoadd(key, longitude, latitude, member));
    }

    @Override
    public Long geoadd(@RedisProtocolSupport.Key final Buffer key, final double longitude1, final double latitude1,
                       final Buffer member1, final double longitude2, final double latitude2,
                       final Buffer member2) throws Exception {
        return blockingInvocation(
                    commander.geoadd(key, longitude1, latitude1, member1, longitude2, latitude2, member2));
    }

    @Override
    public Long geoadd(@RedisProtocolSupport.Key final Buffer key, final double longitude1, final double latitude1,
                       final Buffer member1, final double longitude2, final double latitude2, final Buffer member2,
                       final double longitude3, final double latitude3, final Buffer member3) throws Exception {
        return blockingInvocation(commander.geoadd(key, longitude1, latitude1, member1, longitude2, latitude2, member2,
                    longitude3, latitude3, member3));
    }

    @Override
    public Long geoadd(@RedisProtocolSupport.Key final Buffer key,
                       final Collection<RedisProtocolSupport.BufferLongitudeLatitudeMember> longitudeLatitudeMembers) throws Exception {
        return blockingInvocation(commander.geoadd(key, longitudeLatitudeMembers));
    }

    @Override
    public Double geodist(@RedisProtocolSupport.Key final Buffer key, final Buffer member1,
                          final Buffer member2) throws Exception {
        return blockingInvocation(commander.geodist(key, member1, member2));
    }

    @Override
    public Double geodist(@RedisProtocolSupport.Key final Buffer key, final Buffer member1, final Buffer member2,
                          @Nullable final Buffer unit) throws Exception {
        return blockingInvocation(commander.geodist(key, member1, member2, unit));
    }

    @Override
    public <T> List<T> geohash(@RedisProtocolSupport.Key final Buffer key, final Buffer member) throws Exception {
        return blockingInvocation(commander.geohash(key, member));
    }

    @Override
    public <T> List<T> geohash(@RedisProtocolSupport.Key final Buffer key, final Buffer member1,
                               final Buffer member2) throws Exception {
        return blockingInvocation(commander.geohash(key, member1, member2));
    }

    @Override
    public <T> List<T> geohash(@RedisProtocolSupport.Key final Buffer key, final Buffer member1, final Buffer member2,
                               final Buffer member3) throws Exception {
        return blockingInvocation(commander.geohash(key, member1, member2, member3));
    }

    @Override
    public <T> List<T> geohash(@RedisProtocolSupport.Key final Buffer key,
                               final Collection<Buffer> members) throws Exception {
        return blockingInvocation(commander.geohash(key, members));
    }

    @Override
    public <T> List<T> geopos(@RedisProtocolSupport.Key final Buffer key, final Buffer member) throws Exception {
        return blockingInvocation(commander.geopos(key, member));
    }

    @Override
    public <T> List<T> geopos(@RedisProtocolSupport.Key final Buffer key, final Buffer member1,
                              final Buffer member2) throws Exception {
        return blockingInvocation(commander.geopos(key, member1, member2));
    }

    @Override
    public <T> List<T> geopos(@RedisProtocolSupport.Key final Buffer key, final Buffer member1, final Buffer member2,
                              final Buffer member3) throws Exception {
        return blockingInvocation(commander.geopos(key, member1, member2, member3));
    }

    @Override
    public <T> List<T> geopos(@RedisProtocolSupport.Key final Buffer key,
                              final Collection<Buffer> members) throws Exception {
        return blockingInvocation(commander.geopos(key, members));
    }

    @Override
    public <T> List<T> georadius(@RedisProtocolSupport.Key final Buffer key, final double longitude,
                                 final double latitude, final double radius,
                                 final RedisProtocolSupport.GeoradiusUnit unit) throws Exception {
        return blockingInvocation(commander.georadius(key, longitude, latitude, radius, unit));
    }

    @Override
    public <T> List<T> georadius(@RedisProtocolSupport.Key final Buffer key, final double longitude,
                                 final double latitude, final double radius,
                                 final RedisProtocolSupport.GeoradiusUnit unit,
                                 @Nullable final RedisProtocolSupport.GeoradiusWithcoord withcoord,
                                 @Nullable final RedisProtocolSupport.GeoradiusWithdist withdist,
                                 @Nullable final RedisProtocolSupport.GeoradiusWithhash withhash,
                                 @Nullable final Long count, @Nullable final RedisProtocolSupport.GeoradiusOrder order,
                                 @Nullable @RedisProtocolSupport.Key final Buffer storeKey,
                                 @Nullable @RedisProtocolSupport.Key final Buffer storedistKey) throws Exception {
        return blockingInvocation(commander.georadius(key, longitude, latitude, radius, unit, withcoord, withdist,
                    withhash, count, order, storeKey, storedistKey));
    }

    @Override
    public <T> List<T> georadiusbymember(@RedisProtocolSupport.Key final Buffer key, final Buffer member,
                                         final double radius,
                                         final RedisProtocolSupport.GeoradiusbymemberUnit unit) throws Exception {
        return blockingInvocation(commander.georadiusbymember(key, member, radius, unit));
    }

    @Override
    public <T> List<T> georadiusbymember(@RedisProtocolSupport.Key final Buffer key, final Buffer member,
                                         final double radius, final RedisProtocolSupport.GeoradiusbymemberUnit unit,
                                         @Nullable final RedisProtocolSupport.GeoradiusbymemberWithcoord withcoord,
                                         @Nullable final RedisProtocolSupport.GeoradiusbymemberWithdist withdist,
                                         @Nullable final RedisProtocolSupport.GeoradiusbymemberWithhash withhash,
                                         @Nullable final Long count,
                                         @Nullable final RedisProtocolSupport.GeoradiusbymemberOrder order,
                                         @Nullable @RedisProtocolSupport.Key final Buffer storeKey,
                                         @Nullable @RedisProtocolSupport.Key final Buffer storedistKey) throws Exception {
        return blockingInvocation(commander.georadiusbymember(key, member, radius, unit, withcoord, withdist, withhash,
                    count, order, storeKey, storedistKey));
    }

    @Override
    public Buffer get(@RedisProtocolSupport.Key final Buffer key) throws Exception {
        return blockingInvocation(commander.get(key));
    }

    @Override
    public Long getbit(@RedisProtocolSupport.Key final Buffer key, final long offset) throws Exception {
        return blockingInvocation(commander.getbit(key, offset));
    }

    @Override
    public Buffer getrange(@RedisProtocolSupport.Key final Buffer key, final long start,
                           final long end) throws Exception {
        return blockingInvocation(commander.getrange(key, start, end));
    }

    @Override
    public Buffer getset(@RedisProtocolSupport.Key final Buffer key, final Buffer value) throws Exception {
        return blockingInvocation(commander.getset(key, value));
    }

    @Override
    public Long hdel(@RedisProtocolSupport.Key final Buffer key, final Buffer field) throws Exception {
        return blockingInvocation(commander.hdel(key, field));
    }

    @Override
    public Long hdel(@RedisProtocolSupport.Key final Buffer key, final Buffer field1,
                     final Buffer field2) throws Exception {
        return blockingInvocation(commander.hdel(key, field1, field2));
    }

    @Override
    public Long hdel(@RedisProtocolSupport.Key final Buffer key, final Buffer field1, final Buffer field2,
                     final Buffer field3) throws Exception {
        return blockingInvocation(commander.hdel(key, field1, field2, field3));
    }

    @Override
    public Long hdel(@RedisProtocolSupport.Key final Buffer key, final Collection<Buffer> fields) throws Exception {
        return blockingInvocation(commander.hdel(key, fields));
    }

    @Override
    public Long hexists(@RedisProtocolSupport.Key final Buffer key, final Buffer field) throws Exception {
        return blockingInvocation(commander.hexists(key, field));
    }

    @Override
    public Buffer hget(@RedisProtocolSupport.Key final Buffer key, final Buffer field) throws Exception {
        return blockingInvocation(commander.hget(key, field));
    }

    @Override
    public <T> List<T> hgetall(@RedisProtocolSupport.Key final Buffer key) throws Exception {
        return blockingInvocation(commander.hgetall(key));
    }

    @Override
    public Long hincrby(@RedisProtocolSupport.Key final Buffer key, final Buffer field,
                        final long increment) throws Exception {
        return blockingInvocation(commander.hincrby(key, field, increment));
    }

    @Override
    public Double hincrbyfloat(@RedisProtocolSupport.Key final Buffer key, final Buffer field,
                               final double increment) throws Exception {
        return blockingInvocation(commander.hincrbyfloat(key, field, increment));
    }

    @Override
    public <T> List<T> hkeys(@RedisProtocolSupport.Key final Buffer key) throws Exception {
        return blockingInvocation(commander.hkeys(key));
    }

    @Override
    public Long hlen(@RedisProtocolSupport.Key final Buffer key) throws Exception {
        return blockingInvocation(commander.hlen(key));
    }

    @Override
    public List<Buffer> hmget(@RedisProtocolSupport.Key final Buffer key, final Buffer field) throws Exception {
        return blockingInvocation(commander.hmget(key, field));
    }

    @Override
    public List<Buffer> hmget(@RedisProtocolSupport.Key final Buffer key, final Buffer field1,
                              final Buffer field2) throws Exception {
        return blockingInvocation(commander.hmget(key, field1, field2));
    }

    @Override
    public List<Buffer> hmget(@RedisProtocolSupport.Key final Buffer key, final Buffer field1, final Buffer field2,
                              final Buffer field3) throws Exception {
        return blockingInvocation(commander.hmget(key, field1, field2, field3));
    }

    @Override
    public List<Buffer> hmget(@RedisProtocolSupport.Key final Buffer key,
                              final Collection<Buffer> fields) throws Exception {
        return blockingInvocation(commander.hmget(key, fields));
    }

    @Override
    public String hmset(@RedisProtocolSupport.Key final Buffer key, final Buffer field,
                        final Buffer value) throws Exception {
        return blockingInvocation(commander.hmset(key, field, value));
    }

    @Override
    public String hmset(@RedisProtocolSupport.Key final Buffer key, final Buffer field1, final Buffer value1,
                        final Buffer field2, final Buffer value2) throws Exception {
        return blockingInvocation(commander.hmset(key, field1, value1, field2, value2));
    }

    @Override
    public String hmset(@RedisProtocolSupport.Key final Buffer key, final Buffer field1, final Buffer value1,
                        final Buffer field2, final Buffer value2, final Buffer field3,
                        final Buffer value3) throws Exception {
        return blockingInvocation(commander.hmset(key, field1, value1, field2, value2, field3, value3));
    }

    @Override
    public String hmset(@RedisProtocolSupport.Key final Buffer key,
                        final Collection<RedisProtocolSupport.BufferFieldValue> fieldValues) throws Exception {
        return blockingInvocation(commander.hmset(key, fieldValues));
    }

    @Override
    public <T> List<T> hscan(@RedisProtocolSupport.Key final Buffer key, final long cursor) throws Exception {
        return blockingInvocation(commander.hscan(key, cursor));
    }

    @Override
    public <T> List<T> hscan(@RedisProtocolSupport.Key final Buffer key, final long cursor,
                             @Nullable final Buffer matchPattern, @Nullable final Long count) throws Exception {
        return blockingInvocation(commander.hscan(key, cursor, matchPattern, count));
    }

    @Override
    public Long hset(@RedisProtocolSupport.Key final Buffer key, final Buffer field,
                     final Buffer value) throws Exception {
        return blockingInvocation(commander.hset(key, field, value));
    }

    @Override
    public Long hsetnx(@RedisProtocolSupport.Key final Buffer key, final Buffer field,
                       final Buffer value) throws Exception {
        return blockingInvocation(commander.hsetnx(key, field, value));
    }

    @Override
    public Long hstrlen(@RedisProtocolSupport.Key final Buffer key, final Buffer field) throws Exception {
        return blockingInvocation(commander.hstrlen(key, field));
    }

    @Override
    public <T> List<T> hvals(@RedisProtocolSupport.Key final Buffer key) throws Exception {
        return blockingInvocation(commander.hvals(key));
    }

    @Override
    public Long incr(@RedisProtocolSupport.Key final Buffer key) throws Exception {
        return blockingInvocation(commander.incr(key));
    }

    @Override
    public Long incrby(@RedisProtocolSupport.Key final Buffer key, final long increment) throws Exception {
        return blockingInvocation(commander.incrby(key, increment));
    }

    @Override
    public Double incrbyfloat(@RedisProtocolSupport.Key final Buffer key, final double increment) throws Exception {
        return blockingInvocation(commander.incrbyfloat(key, increment));
    }

    @Override
    public Buffer info() throws Exception {
        return blockingInvocation(commander.info());
    }

    @Override
    public Buffer info(@Nullable final Buffer section) throws Exception {
        return blockingInvocation(commander.info(section));
    }

    @Override
    public <T> List<T> keys(final Buffer pattern) throws Exception {
        return blockingInvocation(commander.keys(pattern));
    }

    @Override
    public Long lastsave() throws Exception {
        return blockingInvocation(commander.lastsave());
    }

    @Override
    public Buffer lindex(@RedisProtocolSupport.Key final Buffer key, final long index) throws Exception {
        return blockingInvocation(commander.lindex(key, index));
    }

    @Override
    public Long linsert(@RedisProtocolSupport.Key final Buffer key, final RedisProtocolSupport.LinsertWhere where,
                        final Buffer pivot, final Buffer value) throws Exception {
        return blockingInvocation(commander.linsert(key, where, pivot, value));
    }

    @Override
    public Long llen(@RedisProtocolSupport.Key final Buffer key) throws Exception {
        return blockingInvocation(commander.llen(key));
    }

    @Override
    public Buffer lpop(@RedisProtocolSupport.Key final Buffer key) throws Exception {
        return blockingInvocation(commander.lpop(key));
    }

    @Override
    public Long lpush(@RedisProtocolSupport.Key final Buffer key, final Buffer value) throws Exception {
        return blockingInvocation(commander.lpush(key, value));
    }

    @Override
    public Long lpush(@RedisProtocolSupport.Key final Buffer key, final Buffer value1,
                      final Buffer value2) throws Exception {
        return blockingInvocation(commander.lpush(key, value1, value2));
    }

    @Override
    public Long lpush(@RedisProtocolSupport.Key final Buffer key, final Buffer value1, final Buffer value2,
                      final Buffer value3) throws Exception {
        return blockingInvocation(commander.lpush(key, value1, value2, value3));
    }

    @Override
    public Long lpush(@RedisProtocolSupport.Key final Buffer key, final Collection<Buffer> values) throws Exception {
        return blockingInvocation(commander.lpush(key, values));
    }

    @Override
    public Long lpushx(@RedisProtocolSupport.Key final Buffer key, final Buffer value) throws Exception {
        return blockingInvocation(commander.lpushx(key, value));
    }

    @Override
    public <T> List<T> lrange(@RedisProtocolSupport.Key final Buffer key, final long start,
                              final long stop) throws Exception {
        return blockingInvocation(commander.lrange(key, start, stop));
    }

    @Override
    public Long lrem(@RedisProtocolSupport.Key final Buffer key, final long count,
                     final Buffer value) throws Exception {
        return blockingInvocation(commander.lrem(key, count, value));
    }

    @Override
    public String lset(@RedisProtocolSupport.Key final Buffer key, final long index,
                       final Buffer value) throws Exception {
        return blockingInvocation(commander.lset(key, index, value));
    }

    @Override
    public String ltrim(@RedisProtocolSupport.Key final Buffer key, final long start,
                        final long stop) throws Exception {
        return blockingInvocation(commander.ltrim(key, start, stop));
    }

    @Override
    public Buffer memoryDoctor() throws Exception {
        return blockingInvocation(commander.memoryDoctor());
    }

    @Override
    public <T> List<T> memoryHelp() throws Exception {
        return blockingInvocation(commander.memoryHelp());
    }

    @Override
    public Buffer memoryMallocStats() throws Exception {
        return blockingInvocation(commander.memoryMallocStats());
    }

    @Override
    public String memoryPurge() throws Exception {
        return blockingInvocation(commander.memoryPurge());
    }

    @Override
    public <T> List<T> memoryStats() throws Exception {
        return blockingInvocation(commander.memoryStats());
    }

    @Override
    public Long memoryUsage(@RedisProtocolSupport.Key final Buffer key) throws Exception {
        return blockingInvocation(commander.memoryUsage(key));
    }

    @Override
    public Long memoryUsage(@RedisProtocolSupport.Key final Buffer key,
                            @Nullable final Long samplesCount) throws Exception {
        return blockingInvocation(commander.memoryUsage(key, samplesCount));
    }

    @Override
    public List<Buffer> mget(@RedisProtocolSupport.Key final Buffer key) throws Exception {
        return blockingInvocation(commander.mget(key));
    }

    @Override
    public List<Buffer> mget(@RedisProtocolSupport.Key final Buffer key1,
                             @RedisProtocolSupport.Key final Buffer key2) throws Exception {
        return blockingInvocation(commander.mget(key1, key2));
    }

    @Override
    public List<Buffer> mget(@RedisProtocolSupport.Key final Buffer key1, @RedisProtocolSupport.Key final Buffer key2,
                             @RedisProtocolSupport.Key final Buffer key3) throws Exception {
        return blockingInvocation(commander.mget(key1, key2, key3));
    }

    @Override
    public List<Buffer> mget(@RedisProtocolSupport.Key final Collection<Buffer> keys) throws Exception {
        return blockingInvocation(commander.mget(keys));
    }

    @Override
    public BlockingIterable<String> monitor() throws Exception {
        return commander.monitor().toIterable();
    }

    @Override
    public Long move(@RedisProtocolSupport.Key final Buffer key, final long db) throws Exception {
        return blockingInvocation(commander.move(key, db));
    }

    @Override
    public String mset(@RedisProtocolSupport.Key final Buffer key, final Buffer value) throws Exception {
        return blockingInvocation(commander.mset(key, value));
    }

    @Override
    public String mset(@RedisProtocolSupport.Key final Buffer key1, final Buffer value1,
                       @RedisProtocolSupport.Key final Buffer key2, final Buffer value2) throws Exception {
        return blockingInvocation(commander.mset(key1, value1, key2, value2));
    }

    @Override
    public String mset(@RedisProtocolSupport.Key final Buffer key1, final Buffer value1,
                       @RedisProtocolSupport.Key final Buffer key2, final Buffer value2,
                       @RedisProtocolSupport.Key final Buffer key3, final Buffer value3) throws Exception {
        return blockingInvocation(commander.mset(key1, value1, key2, value2, key3, value3));
    }

    @Override
    public String mset(final Collection<RedisProtocolSupport.BufferKeyValue> keyValues) throws Exception {
        return blockingInvocation(commander.mset(keyValues));
    }

    @Override
    public Long msetnx(@RedisProtocolSupport.Key final Buffer key, final Buffer value) throws Exception {
        return blockingInvocation(commander.msetnx(key, value));
    }

    @Override
    public Long msetnx(@RedisProtocolSupport.Key final Buffer key1, final Buffer value1,
                       @RedisProtocolSupport.Key final Buffer key2, final Buffer value2) throws Exception {
        return blockingInvocation(commander.msetnx(key1, value1, key2, value2));
    }

    @Override
    public Long msetnx(@RedisProtocolSupport.Key final Buffer key1, final Buffer value1,
                       @RedisProtocolSupport.Key final Buffer key2, final Buffer value2,
                       @RedisProtocolSupport.Key final Buffer key3, final Buffer value3) throws Exception {
        return blockingInvocation(commander.msetnx(key1, value1, key2, value2, key3, value3));
    }

    @Override
    public Long msetnx(final Collection<RedisProtocolSupport.BufferKeyValue> keyValues) throws Exception {
        return blockingInvocation(commander.msetnx(keyValues));
    }

    @Override
    public BlockingTransactedBufferRedisCommander multi() throws Exception {
        return blockingInvocation(
                    commander.multi().map(TransactedBufferRedisCommanderToBlockingTransactedBufferRedisCommander::new));
    }

    @Override
    public Buffer objectEncoding(@RedisProtocolSupport.Key final Buffer key) throws Exception {
        return blockingInvocation(commander.objectEncoding(key));
    }

    @Override
    public Long objectFreq(@RedisProtocolSupport.Key final Buffer key) throws Exception {
        return blockingInvocation(commander.objectFreq(key));
    }

    @Override
    public List<String> objectHelp() throws Exception {
        return blockingInvocation(commander.objectHelp());
    }

    @Override
    public Long objectIdletime(@RedisProtocolSupport.Key final Buffer key) throws Exception {
        return blockingInvocation(commander.objectIdletime(key));
    }

    @Override
    public Long objectRefcount(@RedisProtocolSupport.Key final Buffer key) throws Exception {
        return blockingInvocation(commander.objectRefcount(key));
    }

    @Override
    public Long persist(@RedisProtocolSupport.Key final Buffer key) throws Exception {
        return blockingInvocation(commander.persist(key));
    }

    @Override
    public Long pexpire(@RedisProtocolSupport.Key final Buffer key, final long milliseconds) throws Exception {
        return blockingInvocation(commander.pexpire(key, milliseconds));
    }

    @Override
    public Long pexpireat(@RedisProtocolSupport.Key final Buffer key,
                          final long millisecondsTimestamp) throws Exception {
        return blockingInvocation(commander.pexpireat(key, millisecondsTimestamp));
    }

    @Override
    public Long pfadd(@RedisProtocolSupport.Key final Buffer key, final Buffer element) throws Exception {
        return blockingInvocation(commander.pfadd(key, element));
    }

    @Override
    public Long pfadd(@RedisProtocolSupport.Key final Buffer key, final Buffer element1,
                      final Buffer element2) throws Exception {
        return blockingInvocation(commander.pfadd(key, element1, element2));
    }

    @Override
    public Long pfadd(@RedisProtocolSupport.Key final Buffer key, final Buffer element1, final Buffer element2,
                      final Buffer element3) throws Exception {
        return blockingInvocation(commander.pfadd(key, element1, element2, element3));
    }

    @Override
    public Long pfadd(@RedisProtocolSupport.Key final Buffer key, final Collection<Buffer> elements) throws Exception {
        return blockingInvocation(commander.pfadd(key, elements));
    }

    @Override
    public Long pfcount(@RedisProtocolSupport.Key final Buffer key) throws Exception {
        return blockingInvocation(commander.pfcount(key));
    }

    @Override
    public Long pfcount(@RedisProtocolSupport.Key final Buffer key1,
                        @RedisProtocolSupport.Key final Buffer key2) throws Exception {
        return blockingInvocation(commander.pfcount(key1, key2));
    }

    @Override
    public Long pfcount(@RedisProtocolSupport.Key final Buffer key1, @RedisProtocolSupport.Key final Buffer key2,
                        @RedisProtocolSupport.Key final Buffer key3) throws Exception {
        return blockingInvocation(commander.pfcount(key1, key2, key3));
    }

    @Override
    public Long pfcount(@RedisProtocolSupport.Key final Collection<Buffer> keys) throws Exception {
        return blockingInvocation(commander.pfcount(keys));
    }

    @Override
    public String pfmerge(@RedisProtocolSupport.Key final Buffer destkey,
                          @RedisProtocolSupport.Key final Buffer sourcekey) throws Exception {
        return blockingInvocation(commander.pfmerge(destkey, sourcekey));
    }

    @Override
    public String pfmerge(@RedisProtocolSupport.Key final Buffer destkey,
                          @RedisProtocolSupport.Key final Buffer sourcekey1,
                          @RedisProtocolSupport.Key final Buffer sourcekey2) throws Exception {
        return blockingInvocation(commander.pfmerge(destkey, sourcekey1, sourcekey2));
    }

    @Override
    public String pfmerge(@RedisProtocolSupport.Key final Buffer destkey,
                          @RedisProtocolSupport.Key final Buffer sourcekey1,
                          @RedisProtocolSupport.Key final Buffer sourcekey2,
                          @RedisProtocolSupport.Key final Buffer sourcekey3) throws Exception {
        return blockingInvocation(commander.pfmerge(destkey, sourcekey1, sourcekey2, sourcekey3));
    }

    @Override
    public String pfmerge(@RedisProtocolSupport.Key final Buffer destkey,
                          @RedisProtocolSupport.Key final Collection<Buffer> sourcekeys) throws Exception {
        return blockingInvocation(commander.pfmerge(destkey, sourcekeys));
    }

    @Override
    public String ping() throws Exception {
        return blockingInvocation(commander.ping());
    }

    @Override
    public Buffer ping(final Buffer message) throws Exception {
        return blockingInvocation(commander.ping(message));
    }

    @Override
    public String psetex(@RedisProtocolSupport.Key final Buffer key, final long milliseconds,
                         final Buffer value) throws Exception {
        return blockingInvocation(commander.psetex(key, milliseconds, value));
    }

    @Override
    public BlockingPubSubBufferRedisConnection psubscribe(final Buffer pattern) throws Exception {
        return blockingInvocation(commander.psubscribe(pattern)
                    .map(PubSubBufferRedisConnectionToBlockingPubSubBufferRedisConnection::new));
    }

    @Override
    public Long pttl(@RedisProtocolSupport.Key final Buffer key) throws Exception {
        return blockingInvocation(commander.pttl(key));
    }

    @Override
    public Long publish(final Buffer channel, final Buffer message) throws Exception {
        return blockingInvocation(commander.publish(channel, message));
    }

    @Override
    public List<String> pubsubChannels() throws Exception {
        return blockingInvocation(commander.pubsubChannels());
    }

    @Override
    public List<String> pubsubChannels(@Nullable final Buffer pattern) throws Exception {
        return blockingInvocation(commander.pubsubChannels(pattern));
    }

    @Override
    public List<String> pubsubChannels(@Nullable final Buffer pattern1,
                                       @Nullable final Buffer pattern2) throws Exception {
        return blockingInvocation(commander.pubsubChannels(pattern1, pattern2));
    }

    @Override
    public List<String> pubsubChannels(@Nullable final Buffer pattern1, @Nullable final Buffer pattern2,
                                       @Nullable final Buffer pattern3) throws Exception {
        return blockingInvocation(commander.pubsubChannels(pattern1, pattern2, pattern3));
    }

    @Override
    public List<String> pubsubChannels(final Collection<Buffer> patterns) throws Exception {
        return blockingInvocation(commander.pubsubChannels(patterns));
    }

    @Override
    public <T> List<T> pubsubNumsub() throws Exception {
        return blockingInvocation(commander.pubsubNumsub());
    }

    @Override
    public <T> List<T> pubsubNumsub(@Nullable final Buffer channel) throws Exception {
        return blockingInvocation(commander.pubsubNumsub(channel));
    }

    @Override
    public <T> List<T> pubsubNumsub(@Nullable final Buffer channel1, @Nullable final Buffer channel2) throws Exception {
        return blockingInvocation(commander.pubsubNumsub(channel1, channel2));
    }

    @Override
    public <T> List<T> pubsubNumsub(@Nullable final Buffer channel1, @Nullable final Buffer channel2,
                                    @Nullable final Buffer channel3) throws Exception {
        return blockingInvocation(commander.pubsubNumsub(channel1, channel2, channel3));
    }

    @Override
    public <T> List<T> pubsubNumsub(final Collection<Buffer> channels) throws Exception {
        return blockingInvocation(commander.pubsubNumsub(channels));
    }

    @Override
    public Long pubsubNumpat() throws Exception {
        return blockingInvocation(commander.pubsubNumpat());
    }

    @Override
    public Buffer randomkey() throws Exception {
        return blockingInvocation(commander.randomkey());
    }

    @Override
    public String readonly() throws Exception {
        return blockingInvocation(commander.readonly());
    }

    @Override
    public String readwrite() throws Exception {
        return blockingInvocation(commander.readwrite());
    }

    @Override
    public String rename(@RedisProtocolSupport.Key final Buffer key,
                         @RedisProtocolSupport.Key final Buffer newkey) throws Exception {
        return blockingInvocation(commander.rename(key, newkey));
    }

    @Override
    public Long renamenx(@RedisProtocolSupport.Key final Buffer key,
                         @RedisProtocolSupport.Key final Buffer newkey) throws Exception {
        return blockingInvocation(commander.renamenx(key, newkey));
    }

    @Override
    public String restore(@RedisProtocolSupport.Key final Buffer key, final long ttl,
                          final Buffer serializedValue) throws Exception {
        return blockingInvocation(commander.restore(key, ttl, serializedValue));
    }

    @Override
    public String restore(@RedisProtocolSupport.Key final Buffer key, final long ttl, final Buffer serializedValue,
                          @Nullable final RedisProtocolSupport.RestoreReplace replace) throws Exception {
        return blockingInvocation(commander.restore(key, ttl, serializedValue, replace));
    }

    @Override
    public <T> List<T> role() throws Exception {
        return blockingInvocation(commander.role());
    }

    @Override
    public Buffer rpop(@RedisProtocolSupport.Key final Buffer key) throws Exception {
        return blockingInvocation(commander.rpop(key));
    }

    @Override
    public Buffer rpoplpush(@RedisProtocolSupport.Key final Buffer source,
                            @RedisProtocolSupport.Key final Buffer destination) throws Exception {
        return blockingInvocation(commander.rpoplpush(source, destination));
    }

    @Override
    public Long rpush(@RedisProtocolSupport.Key final Buffer key, final Buffer value) throws Exception {
        return blockingInvocation(commander.rpush(key, value));
    }

    @Override
    public Long rpush(@RedisProtocolSupport.Key final Buffer key, final Buffer value1,
                      final Buffer value2) throws Exception {
        return blockingInvocation(commander.rpush(key, value1, value2));
    }

    @Override
    public Long rpush(@RedisProtocolSupport.Key final Buffer key, final Buffer value1, final Buffer value2,
                      final Buffer value3) throws Exception {
        return blockingInvocation(commander.rpush(key, value1, value2, value3));
    }

    @Override
    public Long rpush(@RedisProtocolSupport.Key final Buffer key, final Collection<Buffer> values) throws Exception {
        return blockingInvocation(commander.rpush(key, values));
    }

    @Override
    public Long rpushx(@RedisProtocolSupport.Key final Buffer key, final Buffer value) throws Exception {
        return blockingInvocation(commander.rpushx(key, value));
    }

    @Override
    public Long sadd(@RedisProtocolSupport.Key final Buffer key, final Buffer member) throws Exception {
        return blockingInvocation(commander.sadd(key, member));
    }

    @Override
    public Long sadd(@RedisProtocolSupport.Key final Buffer key, final Buffer member1,
                     final Buffer member2) throws Exception {
        return blockingInvocation(commander.sadd(key, member1, member2));
    }

    @Override
    public Long sadd(@RedisProtocolSupport.Key final Buffer key, final Buffer member1, final Buffer member2,
                     final Buffer member3) throws Exception {
        return blockingInvocation(commander.sadd(key, member1, member2, member3));
    }

    @Override
    public Long sadd(@RedisProtocolSupport.Key final Buffer key, final Collection<Buffer> members) throws Exception {
        return blockingInvocation(commander.sadd(key, members));
    }

    @Override
    public String save() throws Exception {
        return blockingInvocation(commander.save());
    }

    @Override
    public <T> List<T> scan(final long cursor) throws Exception {
        return blockingInvocation(commander.scan(cursor));
    }

    @Override
    public <T> List<T> scan(final long cursor, @Nullable final Buffer matchPattern,
                            @Nullable final Long count) throws Exception {
        return blockingInvocation(commander.scan(cursor, matchPattern, count));
    }

    @Override
    public Long scard(@RedisProtocolSupport.Key final Buffer key) throws Exception {
        return blockingInvocation(commander.scard(key));
    }

    @Override
    public String scriptDebug(final RedisProtocolSupport.ScriptDebugMode mode) throws Exception {
        return blockingInvocation(commander.scriptDebug(mode));
    }

    @Override
    public <T> List<T> scriptExists(final Buffer sha1) throws Exception {
        return blockingInvocation(commander.scriptExists(sha1));
    }

    @Override
    public <T> List<T> scriptExists(final Buffer sha11, final Buffer sha12) throws Exception {
        return blockingInvocation(commander.scriptExists(sha11, sha12));
    }

    @Override
    public <T> List<T> scriptExists(final Buffer sha11, final Buffer sha12, final Buffer sha13) throws Exception {
        return blockingInvocation(commander.scriptExists(sha11, sha12, sha13));
    }

    @Override
    public <T> List<T> scriptExists(final Collection<Buffer> sha1s) throws Exception {
        return blockingInvocation(commander.scriptExists(sha1s));
    }

    @Override
    public String scriptFlush() throws Exception {
        return blockingInvocation(commander.scriptFlush());
    }

    @Override
    public String scriptKill() throws Exception {
        return blockingInvocation(commander.scriptKill());
    }

    @Override
    public Buffer scriptLoad(final Buffer script) throws Exception {
        return blockingInvocation(commander.scriptLoad(script));
    }

    @Override
    public <T> List<T> sdiff(@RedisProtocolSupport.Key final Buffer firstkey) throws Exception {
        return blockingInvocation(commander.sdiff(firstkey));
    }

    @Override
    public <T> List<T> sdiff(@RedisProtocolSupport.Key final Buffer firstkey,
                             @Nullable @RedisProtocolSupport.Key final Buffer otherkey) throws Exception {
        return blockingInvocation(commander.sdiff(firstkey, otherkey));
    }

    @Override
    public <T> List<T> sdiff(@RedisProtocolSupport.Key final Buffer firstkey,
                             @Nullable @RedisProtocolSupport.Key final Buffer otherkey1,
                             @Nullable @RedisProtocolSupport.Key final Buffer otherkey2) throws Exception {
        return blockingInvocation(commander.sdiff(firstkey, otherkey1, otherkey2));
    }

    @Override
    public <T> List<T> sdiff(@RedisProtocolSupport.Key final Buffer firstkey,
                             @Nullable @RedisProtocolSupport.Key final Buffer otherkey1,
                             @Nullable @RedisProtocolSupport.Key final Buffer otherkey2,
                             @Nullable @RedisProtocolSupport.Key final Buffer otherkey3) throws Exception {
        return blockingInvocation(commander.sdiff(firstkey, otherkey1, otherkey2, otherkey3));
    }

    @Override
    public <T> List<T> sdiff(@RedisProtocolSupport.Key final Buffer firstkey,
                             @RedisProtocolSupport.Key final Collection<Buffer> otherkeys) throws Exception {
        return blockingInvocation(commander.sdiff(firstkey, otherkeys));
    }

    @Override
    public Long sdiffstore(@RedisProtocolSupport.Key final Buffer destination,
                           @RedisProtocolSupport.Key final Buffer firstkey) throws Exception {
        return blockingInvocation(commander.sdiffstore(destination, firstkey));
    }

    @Override
    public Long sdiffstore(@RedisProtocolSupport.Key final Buffer destination,
                           @RedisProtocolSupport.Key final Buffer firstkey,
                           @Nullable @RedisProtocolSupport.Key final Buffer otherkey) throws Exception {
        return blockingInvocation(commander.sdiffstore(destination, firstkey, otherkey));
    }

    @Override
    public Long sdiffstore(@RedisProtocolSupport.Key final Buffer destination,
                           @RedisProtocolSupport.Key final Buffer firstkey,
                           @Nullable @RedisProtocolSupport.Key final Buffer otherkey1,
                           @Nullable @RedisProtocolSupport.Key final Buffer otherkey2) throws Exception {
        return blockingInvocation(commander.sdiffstore(destination, firstkey, otherkey1, otherkey2));
    }

    @Override
    public Long sdiffstore(@RedisProtocolSupport.Key final Buffer destination,
                           @RedisProtocolSupport.Key final Buffer firstkey,
                           @Nullable @RedisProtocolSupport.Key final Buffer otherkey1,
                           @Nullable @RedisProtocolSupport.Key final Buffer otherkey2,
                           @Nullable @RedisProtocolSupport.Key final Buffer otherkey3) throws Exception {
        return blockingInvocation(commander.sdiffstore(destination, firstkey, otherkey1, otherkey2, otherkey3));
    }

    @Override
    public Long sdiffstore(@RedisProtocolSupport.Key final Buffer destination,
                           @RedisProtocolSupport.Key final Buffer firstkey,
                           @RedisProtocolSupport.Key final Collection<Buffer> otherkeys) throws Exception {
        return blockingInvocation(commander.sdiffstore(destination, firstkey, otherkeys));
    }

    @Override
    public String select(final long index) throws Exception {
        return blockingInvocation(commander.select(index));
    }

    @Override
    public String set(@RedisProtocolSupport.Key final Buffer key, final Buffer value) throws Exception {
        return blockingInvocation(commander.set(key, value));
    }

    @Override
    public String set(@RedisProtocolSupport.Key final Buffer key, final Buffer value,
                      @Nullable final RedisProtocolSupport.ExpireDuration expireDuration,
                      @Nullable final RedisProtocolSupport.SetCondition condition) throws Exception {
        return blockingInvocation(commander.set(key, value, expireDuration, condition));
    }

    @Override
    public Long setbit(@RedisProtocolSupport.Key final Buffer key, final long offset,
                       final Buffer value) throws Exception {
        return blockingInvocation(commander.setbit(key, offset, value));
    }

    @Override
    public String setex(@RedisProtocolSupport.Key final Buffer key, final long seconds,
                        final Buffer value) throws Exception {
        return blockingInvocation(commander.setex(key, seconds, value));
    }

    @Override
    public Long setnx(@RedisProtocolSupport.Key final Buffer key, final Buffer value) throws Exception {
        return blockingInvocation(commander.setnx(key, value));
    }

    @Override
    public Long setrange(@RedisProtocolSupport.Key final Buffer key, final long offset,
                         final Buffer value) throws Exception {
        return blockingInvocation(commander.setrange(key, offset, value));
    }

    @Override
    public String shutdown() throws Exception {
        return blockingInvocation(commander.shutdown());
    }

    @Override
    public String shutdown(@Nullable final RedisProtocolSupport.ShutdownSaveMode saveMode) throws Exception {
        return blockingInvocation(commander.shutdown(saveMode));
    }

    @Override
    public <T> List<T> sinter(@RedisProtocolSupport.Key final Buffer key) throws Exception {
        return blockingInvocation(commander.sinter(key));
    }

    @Override
    public <T> List<T> sinter(@RedisProtocolSupport.Key final Buffer key1,
                              @RedisProtocolSupport.Key final Buffer key2) throws Exception {
        return blockingInvocation(commander.sinter(key1, key2));
    }

    @Override
    public <T> List<T> sinter(@RedisProtocolSupport.Key final Buffer key1, @RedisProtocolSupport.Key final Buffer key2,
                              @RedisProtocolSupport.Key final Buffer key3) throws Exception {
        return blockingInvocation(commander.sinter(key1, key2, key3));
    }

    @Override
    public <T> List<T> sinter(@RedisProtocolSupport.Key final Collection<Buffer> keys) throws Exception {
        return blockingInvocation(commander.sinter(keys));
    }

    @Override
    public Long sinterstore(@RedisProtocolSupport.Key final Buffer destination,
                            @RedisProtocolSupport.Key final Buffer key) throws Exception {
        return blockingInvocation(commander.sinterstore(destination, key));
    }

    @Override
    public Long sinterstore(@RedisProtocolSupport.Key final Buffer destination,
                            @RedisProtocolSupport.Key final Buffer key1,
                            @RedisProtocolSupport.Key final Buffer key2) throws Exception {
        return blockingInvocation(commander.sinterstore(destination, key1, key2));
    }

    @Override
    public Long sinterstore(@RedisProtocolSupport.Key final Buffer destination,
                            @RedisProtocolSupport.Key final Buffer key1, @RedisProtocolSupport.Key final Buffer key2,
                            @RedisProtocolSupport.Key final Buffer key3) throws Exception {
        return blockingInvocation(commander.sinterstore(destination, key1, key2, key3));
    }

    @Override
    public Long sinterstore(@RedisProtocolSupport.Key final Buffer destination,
                            @RedisProtocolSupport.Key final Collection<Buffer> keys) throws Exception {
        return blockingInvocation(commander.sinterstore(destination, keys));
    }

    @Override
    public Long sismember(@RedisProtocolSupport.Key final Buffer key, final Buffer member) throws Exception {
        return blockingInvocation(commander.sismember(key, member));
    }

    @Override
    public String slaveof(final Buffer host, final Buffer port) throws Exception {
        return blockingInvocation(commander.slaveof(host, port));
    }

    @Override
    public <T> List<T> slowlog(final Buffer subcommand) throws Exception {
        return blockingInvocation(commander.slowlog(subcommand));
    }

    @Override
    public <T> List<T> slowlog(final Buffer subcommand, @Nullable final Buffer argument) throws Exception {
        return blockingInvocation(commander.slowlog(subcommand, argument));
    }

    @Override
    public <T> List<T> smembers(@RedisProtocolSupport.Key final Buffer key) throws Exception {
        return blockingInvocation(commander.smembers(key));
    }

    @Override
    public Long smove(@RedisProtocolSupport.Key final Buffer source, @RedisProtocolSupport.Key final Buffer destination,
                      final Buffer member) throws Exception {
        return blockingInvocation(commander.smove(source, destination, member));
    }

    @Override
    public <T> List<T> sort(@RedisProtocolSupport.Key final Buffer key) throws Exception {
        return blockingInvocation(commander.sort(key));
    }

    @Override
    public <T> List<T> sort(@RedisProtocolSupport.Key final Buffer key, @Nullable final Buffer byPattern,
                            @Nullable final RedisProtocolSupport.OffsetCount offsetCount,
                            final Collection<Buffer> getPatterns, @Nullable final RedisProtocolSupport.SortOrder order,
                            @Nullable final RedisProtocolSupport.SortSorting sorting) throws Exception {
        return blockingInvocation(commander.sort(key, byPattern, offsetCount, getPatterns, order, sorting));
    }

    @Override
    public Long sort(@RedisProtocolSupport.Key final Buffer key,
                     @RedisProtocolSupport.Key final Buffer storeDestination) throws Exception {
        return blockingInvocation(commander.sort(key, storeDestination));
    }

    @Override
    public Long sort(@RedisProtocolSupport.Key final Buffer key,
                     @RedisProtocolSupport.Key final Buffer storeDestination, @Nullable final Buffer byPattern,
                     @Nullable final RedisProtocolSupport.OffsetCount offsetCount, final Collection<Buffer> getPatterns,
                     @Nullable final RedisProtocolSupport.SortOrder order,
                     @Nullable final RedisProtocolSupport.SortSorting sorting) throws Exception {
        return blockingInvocation(
                    commander.sort(key, storeDestination, byPattern, offsetCount, getPatterns, order, sorting));
    }

    @Override
    public Buffer spop(@RedisProtocolSupport.Key final Buffer key) throws Exception {
        return blockingInvocation(commander.spop(key));
    }

    @Override
    public Buffer spop(@RedisProtocolSupport.Key final Buffer key, @Nullable final Long count) throws Exception {
        return blockingInvocation(commander.spop(key, count));
    }

    @Override
    public Buffer srandmember(@RedisProtocolSupport.Key final Buffer key) throws Exception {
        return blockingInvocation(commander.srandmember(key));
    }

    @Override
    public List<Buffer> srandmember(@RedisProtocolSupport.Key final Buffer key, final long count) throws Exception {
        return blockingInvocation(commander.srandmember(key, count));
    }

    @Override
    public Long srem(@RedisProtocolSupport.Key final Buffer key, final Buffer member) throws Exception {
        return blockingInvocation(commander.srem(key, member));
    }

    @Override
    public Long srem(@RedisProtocolSupport.Key final Buffer key, final Buffer member1,
                     final Buffer member2) throws Exception {
        return blockingInvocation(commander.srem(key, member1, member2));
    }

    @Override
    public Long srem(@RedisProtocolSupport.Key final Buffer key, final Buffer member1, final Buffer member2,
                     final Buffer member3) throws Exception {
        return blockingInvocation(commander.srem(key, member1, member2, member3));
    }

    @Override
    public Long srem(@RedisProtocolSupport.Key final Buffer key, final Collection<Buffer> members) throws Exception {
        return blockingInvocation(commander.srem(key, members));
    }

    @Override
    public <T> List<T> sscan(@RedisProtocolSupport.Key final Buffer key, final long cursor) throws Exception {
        return blockingInvocation(commander.sscan(key, cursor));
    }

    @Override
    public <T> List<T> sscan(@RedisProtocolSupport.Key final Buffer key, final long cursor,
                             @Nullable final Buffer matchPattern, @Nullable final Long count) throws Exception {
        return blockingInvocation(commander.sscan(key, cursor, matchPattern, count));
    }

    @Override
    public Long strlen(@RedisProtocolSupport.Key final Buffer key) throws Exception {
        return blockingInvocation(commander.strlen(key));
    }

    @Override
    public BlockingPubSubBufferRedisConnection subscribe(final Buffer channel) throws Exception {
        return blockingInvocation(commander.subscribe(channel)
                    .map(PubSubBufferRedisConnectionToBlockingPubSubBufferRedisConnection::new));
    }

    @Override
    public <T> List<T> sunion(@RedisProtocolSupport.Key final Buffer key) throws Exception {
        return blockingInvocation(commander.sunion(key));
    }

    @Override
    public <T> List<T> sunion(@RedisProtocolSupport.Key final Buffer key1,
                              @RedisProtocolSupport.Key final Buffer key2) throws Exception {
        return blockingInvocation(commander.sunion(key1, key2));
    }

    @Override
    public <T> List<T> sunion(@RedisProtocolSupport.Key final Buffer key1, @RedisProtocolSupport.Key final Buffer key2,
                              @RedisProtocolSupport.Key final Buffer key3) throws Exception {
        return blockingInvocation(commander.sunion(key1, key2, key3));
    }

    @Override
    public <T> List<T> sunion(@RedisProtocolSupport.Key final Collection<Buffer> keys) throws Exception {
        return blockingInvocation(commander.sunion(keys));
    }

    @Override
    public Long sunionstore(@RedisProtocolSupport.Key final Buffer destination,
                            @RedisProtocolSupport.Key final Buffer key) throws Exception {
        return blockingInvocation(commander.sunionstore(destination, key));
    }

    @Override
    public Long sunionstore(@RedisProtocolSupport.Key final Buffer destination,
                            @RedisProtocolSupport.Key final Buffer key1,
                            @RedisProtocolSupport.Key final Buffer key2) throws Exception {
        return blockingInvocation(commander.sunionstore(destination, key1, key2));
    }

    @Override
    public Long sunionstore(@RedisProtocolSupport.Key final Buffer destination,
                            @RedisProtocolSupport.Key final Buffer key1, @RedisProtocolSupport.Key final Buffer key2,
                            @RedisProtocolSupport.Key final Buffer key3) throws Exception {
        return blockingInvocation(commander.sunionstore(destination, key1, key2, key3));
    }

    @Override
    public Long sunionstore(@RedisProtocolSupport.Key final Buffer destination,
                            @RedisProtocolSupport.Key final Collection<Buffer> keys) throws Exception {
        return blockingInvocation(commander.sunionstore(destination, keys));
    }

    @Override
    public String swapdb(final long index, final long index1) throws Exception {
        return blockingInvocation(commander.swapdb(index, index1));
    }

    @Override
    public <T> List<T> time() throws Exception {
        return blockingInvocation(commander.time());
    }

    @Override
    public Long touch(@RedisProtocolSupport.Key final Buffer key) throws Exception {
        return blockingInvocation(commander.touch(key));
    }

    @Override
    public Long touch(@RedisProtocolSupport.Key final Buffer key1,
                      @RedisProtocolSupport.Key final Buffer key2) throws Exception {
        return blockingInvocation(commander.touch(key1, key2));
    }

    @Override
    public Long touch(@RedisProtocolSupport.Key final Buffer key1, @RedisProtocolSupport.Key final Buffer key2,
                      @RedisProtocolSupport.Key final Buffer key3) throws Exception {
        return blockingInvocation(commander.touch(key1, key2, key3));
    }

    @Override
    public Long touch(@RedisProtocolSupport.Key final Collection<Buffer> keys) throws Exception {
        return blockingInvocation(commander.touch(keys));
    }

    @Override
    public Long ttl(@RedisProtocolSupport.Key final Buffer key) throws Exception {
        return blockingInvocation(commander.ttl(key));
    }

    @Override
    public String type(@RedisProtocolSupport.Key final Buffer key) throws Exception {
        return blockingInvocation(commander.type(key));
    }

    @Override
    public Long unlink(@RedisProtocolSupport.Key final Buffer key) throws Exception {
        return blockingInvocation(commander.unlink(key));
    }

    @Override
    public Long unlink(@RedisProtocolSupport.Key final Buffer key1,
                       @RedisProtocolSupport.Key final Buffer key2) throws Exception {
        return blockingInvocation(commander.unlink(key1, key2));
    }

    @Override
    public Long unlink(@RedisProtocolSupport.Key final Buffer key1, @RedisProtocolSupport.Key final Buffer key2,
                       @RedisProtocolSupport.Key final Buffer key3) throws Exception {
        return blockingInvocation(commander.unlink(key1, key2, key3));
    }

    @Override
    public Long unlink(@RedisProtocolSupport.Key final Collection<Buffer> keys) throws Exception {
        return blockingInvocation(commander.unlink(keys));
    }

    @Override
    public String unwatch() throws Exception {
        return blockingInvocation(commander.unwatch());
    }

    @Override
    public Long wait(final long numslaves, final long timeout) throws Exception {
        return blockingInvocation(commander.wait(numslaves, timeout));
    }

    @Override
    public String watch(@RedisProtocolSupport.Key final Buffer key) throws Exception {
        return blockingInvocation(commander.watch(key));
    }

    @Override
    public String watch(@RedisProtocolSupport.Key final Buffer key1,
                        @RedisProtocolSupport.Key final Buffer key2) throws Exception {
        return blockingInvocation(commander.watch(key1, key2));
    }

    @Override
    public String watch(@RedisProtocolSupport.Key final Buffer key1, @RedisProtocolSupport.Key final Buffer key2,
                        @RedisProtocolSupport.Key final Buffer key3) throws Exception {
        return blockingInvocation(commander.watch(key1, key2, key3));
    }

    @Override
    public String watch(@RedisProtocolSupport.Key final Collection<Buffer> keys) throws Exception {
        return blockingInvocation(commander.watch(keys));
    }

    @Override
    public Buffer xadd(@RedisProtocolSupport.Key final Buffer key, final Buffer id, final Buffer field,
                       final Buffer value) throws Exception {
        return blockingInvocation(commander.xadd(key, id, field, value));
    }

    @Override
    public Buffer xadd(@RedisProtocolSupport.Key final Buffer key, final Buffer id, final Buffer field1,
                       final Buffer value1, final Buffer field2, final Buffer value2) throws Exception {
        return blockingInvocation(commander.xadd(key, id, field1, value1, field2, value2));
    }

    @Override
    public Buffer xadd(@RedisProtocolSupport.Key final Buffer key, final Buffer id, final Buffer field1,
                       final Buffer value1, final Buffer field2, final Buffer value2, final Buffer field3,
                       final Buffer value3) throws Exception {
        return blockingInvocation(commander.xadd(key, id, field1, value1, field2, value2, field3, value3));
    }

    @Override
    public Buffer xadd(@RedisProtocolSupport.Key final Buffer key, final Buffer id,
                       final Collection<RedisProtocolSupport.BufferFieldValue> fieldValues) throws Exception {
        return blockingInvocation(commander.xadd(key, id, fieldValues));
    }

    @Override
    public Long xlen(@RedisProtocolSupport.Key final Buffer key) throws Exception {
        return blockingInvocation(commander.xlen(key));
    }

    @Override
    public <T> List<T> xpending(@RedisProtocolSupport.Key final Buffer key, final Buffer group) throws Exception {
        return blockingInvocation(commander.xpending(key, group));
    }

    @Override
    public <T> List<T> xpending(@RedisProtocolSupport.Key final Buffer key, final Buffer group,
                                @Nullable final Buffer start, @Nullable final Buffer end, @Nullable final Long count,
                                @Nullable final Buffer consumer) throws Exception {
        return blockingInvocation(commander.xpending(key, group, start, end, count, consumer));
    }

    @Override
    public <T> List<T> xrange(@RedisProtocolSupport.Key final Buffer key, final Buffer start,
                              final Buffer end) throws Exception {
        return blockingInvocation(commander.xrange(key, start, end));
    }

    @Override
    public <T> List<T> xrange(@RedisProtocolSupport.Key final Buffer key, final Buffer start, final Buffer end,
                              @Nullable final Long count) throws Exception {
        return blockingInvocation(commander.xrange(key, start, end, count));
    }

    @Override
    public <T> List<T> xread(@RedisProtocolSupport.Key final Collection<Buffer> keys,
                             final Collection<Buffer> ids) throws Exception {
        return blockingInvocation(commander.xread(keys, ids));
    }

    @Override
    public <T> List<T> xread(@Nullable final Long count, @Nullable final Long blockMilliseconds,
                             @RedisProtocolSupport.Key final Collection<Buffer> keys,
                             final Collection<Buffer> ids) throws Exception {
        return blockingInvocation(commander.xread(count, blockMilliseconds, keys, ids));
    }

    @Override
    public <T> List<T> xreadgroup(final RedisProtocolSupport.BufferGroupConsumer groupConsumer,
                                  @RedisProtocolSupport.Key final Collection<Buffer> keys,
                                  final Collection<Buffer> ids) throws Exception {
        return blockingInvocation(commander.xreadgroup(groupConsumer, keys, ids));
    }

    @Override
    public <T> List<T> xreadgroup(final RedisProtocolSupport.BufferGroupConsumer groupConsumer,
                                  @Nullable final Long count, @Nullable final Long blockMilliseconds,
                                  @RedisProtocolSupport.Key final Collection<Buffer> keys,
                                  final Collection<Buffer> ids) throws Exception {
        return blockingInvocation(commander.xreadgroup(groupConsumer, count, blockMilliseconds, keys, ids));
    }

    @Override
    public <T> List<T> xrevrange(@RedisProtocolSupport.Key final Buffer key, final Buffer end,
                                 final Buffer start) throws Exception {
        return blockingInvocation(commander.xrevrange(key, end, start));
    }

    @Override
    public <T> List<T> xrevrange(@RedisProtocolSupport.Key final Buffer key, final Buffer end, final Buffer start,
                                 @Nullable final Long count) throws Exception {
        return blockingInvocation(commander.xrevrange(key, end, start, count));
    }

    @Override
    public Long zadd(@RedisProtocolSupport.Key final Buffer key,
                     final Collection<RedisProtocolSupport.BufferScoreMember> scoreMembers) throws Exception {
        return blockingInvocation(commander.zadd(key, scoreMembers));
    }

    @Override
    public Long zadd(@RedisProtocolSupport.Key final Buffer key,
                     @Nullable final RedisProtocolSupport.ZaddCondition condition,
                     @Nullable final RedisProtocolSupport.ZaddChange change, final double score,
                     final Buffer member) throws Exception {
        return blockingInvocation(commander.zadd(key, condition, change, score, member));
    }

    @Override
    public Long zadd(@RedisProtocolSupport.Key final Buffer key,
                     @Nullable final RedisProtocolSupport.ZaddCondition condition,
                     @Nullable final RedisProtocolSupport.ZaddChange change, final double score1, final Buffer member1,
                     final double score2, final Buffer member2) throws Exception {
        return blockingInvocation(commander.zadd(key, condition, change, score1, member1, score2, member2));
    }

    @Override
    public Long zadd(@RedisProtocolSupport.Key final Buffer key,
                     @Nullable final RedisProtocolSupport.ZaddCondition condition,
                     @Nullable final RedisProtocolSupport.ZaddChange change, final double score1, final Buffer member1,
                     final double score2, final Buffer member2, final double score3,
                     final Buffer member3) throws Exception {
        return blockingInvocation(
                    commander.zadd(key, condition, change, score1, member1, score2, member2, score3, member3));
    }

    @Override
    public Long zadd(@RedisProtocolSupport.Key final Buffer key,
                     @Nullable final RedisProtocolSupport.ZaddCondition condition,
                     @Nullable final RedisProtocolSupport.ZaddChange change,
                     final Collection<RedisProtocolSupport.BufferScoreMember> scoreMembers) throws Exception {
        return blockingInvocation(commander.zadd(key, condition, change, scoreMembers));
    }

    @Override
    public Double zaddIncr(@RedisProtocolSupport.Key final Buffer key,
                           final Collection<RedisProtocolSupport.BufferScoreMember> scoreMembers) throws Exception {
        return blockingInvocation(commander.zaddIncr(key, scoreMembers));
    }

    @Override
    public Double zaddIncr(@RedisProtocolSupport.Key final Buffer key,
                           @Nullable final RedisProtocolSupport.ZaddCondition condition,
                           @Nullable final RedisProtocolSupport.ZaddChange change, final double score,
                           final Buffer member) throws Exception {
        return blockingInvocation(commander.zaddIncr(key, condition, change, score, member));
    }

    @Override
    public Double zaddIncr(@RedisProtocolSupport.Key final Buffer key,
                           @Nullable final RedisProtocolSupport.ZaddCondition condition,
                           @Nullable final RedisProtocolSupport.ZaddChange change, final double score1,
                           final Buffer member1, final double score2, final Buffer member2) throws Exception {
        return blockingInvocation(commander.zaddIncr(key, condition, change, score1, member1, score2, member2));
    }

    @Override
    public Double zaddIncr(@RedisProtocolSupport.Key final Buffer key,
                           @Nullable final RedisProtocolSupport.ZaddCondition condition,
                           @Nullable final RedisProtocolSupport.ZaddChange change, final double score1,
                           final Buffer member1, final double score2, final Buffer member2, final double score3,
                           final Buffer member3) throws Exception {
        return blockingInvocation(
                    commander.zaddIncr(key, condition, change, score1, member1, score2, member2, score3, member3));
    }

    @Override
    public Double zaddIncr(@RedisProtocolSupport.Key final Buffer key,
                           @Nullable final RedisProtocolSupport.ZaddCondition condition,
                           @Nullable final RedisProtocolSupport.ZaddChange change,
                           final Collection<RedisProtocolSupport.BufferScoreMember> scoreMembers) throws Exception {
        return blockingInvocation(commander.zaddIncr(key, condition, change, scoreMembers));
    }

    @Override
    public Long zcard(@RedisProtocolSupport.Key final Buffer key) throws Exception {
        return blockingInvocation(commander.zcard(key));
    }

    @Override
    public Long zcount(@RedisProtocolSupport.Key final Buffer key, final double min,
                       final double max) throws Exception {
        return blockingInvocation(commander.zcount(key, min, max));
    }

    @Override
    public Double zincrby(@RedisProtocolSupport.Key final Buffer key, final long increment,
                          final Buffer member) throws Exception {
        return blockingInvocation(commander.zincrby(key, increment, member));
    }

    @Override
    public Long zinterstore(@RedisProtocolSupport.Key final Buffer destination, final long numkeys,
                            @RedisProtocolSupport.Key final Collection<Buffer> keys) throws Exception {
        return blockingInvocation(commander.zinterstore(destination, numkeys, keys));
    }

    @Override
    public Long zinterstore(@RedisProtocolSupport.Key final Buffer destination, final long numkeys,
                            @RedisProtocolSupport.Key final Collection<Buffer> keys, final Collection<Long> weights,
                            @Nullable final RedisProtocolSupport.ZinterstoreAggregate aggregate) throws Exception {
        return blockingInvocation(commander.zinterstore(destination, numkeys, keys, weights, aggregate));
    }

    @Override
    public Long zlexcount(@RedisProtocolSupport.Key final Buffer key, final Buffer min,
                          final Buffer max) throws Exception {
        return blockingInvocation(commander.zlexcount(key, min, max));
    }

    @Override
    public <T> List<T> zpopmax(@RedisProtocolSupport.Key final Buffer key) throws Exception {
        return blockingInvocation(commander.zpopmax(key));
    }

    @Override
    public <T> List<T> zpopmax(@RedisProtocolSupport.Key final Buffer key,
                               @Nullable final Long count) throws Exception {
        return blockingInvocation(commander.zpopmax(key, count));
    }

    @Override
    public <T> List<T> zpopmin(@RedisProtocolSupport.Key final Buffer key) throws Exception {
        return blockingInvocation(commander.zpopmin(key));
    }

    @Override
    public <T> List<T> zpopmin(@RedisProtocolSupport.Key final Buffer key,
                               @Nullable final Long count) throws Exception {
        return blockingInvocation(commander.zpopmin(key, count));
    }

    @Override
    public <T> List<T> zrange(@RedisProtocolSupport.Key final Buffer key, final long start,
                              final long stop) throws Exception {
        return blockingInvocation(commander.zrange(key, start, stop));
    }

    @Override
    public <T> List<T> zrange(@RedisProtocolSupport.Key final Buffer key, final long start, final long stop,
                              @Nullable final RedisProtocolSupport.ZrangeWithscores withscores) throws Exception {
        return blockingInvocation(commander.zrange(key, start, stop, withscores));
    }

    @Override
    public <T> List<T> zrangebylex(@RedisProtocolSupport.Key final Buffer key, final Buffer min,
                                   final Buffer max) throws Exception {
        return blockingInvocation(commander.zrangebylex(key, min, max));
    }

    @Override
    public <T> List<T> zrangebylex(@RedisProtocolSupport.Key final Buffer key, final Buffer min, final Buffer max,
                                   @Nullable final RedisProtocolSupport.OffsetCount offsetCount) throws Exception {
        return blockingInvocation(commander.zrangebylex(key, min, max, offsetCount));
    }

    @Override
    public <T> List<T> zrangebyscore(@RedisProtocolSupport.Key final Buffer key, final double min,
                                     final double max) throws Exception {
        return blockingInvocation(commander.zrangebyscore(key, min, max));
    }

    @Override
    public <T> List<T> zrangebyscore(@RedisProtocolSupport.Key final Buffer key, final double min, final double max,
                                     @Nullable final RedisProtocolSupport.ZrangebyscoreWithscores withscores,
                                     @Nullable final RedisProtocolSupport.OffsetCount offsetCount) throws Exception {
        return blockingInvocation(commander.zrangebyscore(key, min, max, withscores, offsetCount));
    }

    @Override
    public Long zrank(@RedisProtocolSupport.Key final Buffer key, final Buffer member) throws Exception {
        return blockingInvocation(commander.zrank(key, member));
    }

    @Override
    public Long zrem(@RedisProtocolSupport.Key final Buffer key, final Buffer member) throws Exception {
        return blockingInvocation(commander.zrem(key, member));
    }

    @Override
    public Long zrem(@RedisProtocolSupport.Key final Buffer key, final Buffer member1,
                     final Buffer member2) throws Exception {
        return blockingInvocation(commander.zrem(key, member1, member2));
    }

    @Override
    public Long zrem(@RedisProtocolSupport.Key final Buffer key, final Buffer member1, final Buffer member2,
                     final Buffer member3) throws Exception {
        return blockingInvocation(commander.zrem(key, member1, member2, member3));
    }

    @Override
    public Long zrem(@RedisProtocolSupport.Key final Buffer key, final Collection<Buffer> members) throws Exception {
        return blockingInvocation(commander.zrem(key, members));
    }

    @Override
    public Long zremrangebylex(@RedisProtocolSupport.Key final Buffer key, final Buffer min,
                               final Buffer max) throws Exception {
        return blockingInvocation(commander.zremrangebylex(key, min, max));
    }

    @Override
    public Long zremrangebyrank(@RedisProtocolSupport.Key final Buffer key, final long start,
                                final long stop) throws Exception {
        return blockingInvocation(commander.zremrangebyrank(key, start, stop));
    }

    @Override
    public Long zremrangebyscore(@RedisProtocolSupport.Key final Buffer key, final double min,
                                 final double max) throws Exception {
        return blockingInvocation(commander.zremrangebyscore(key, min, max));
    }

    @Override
    public <T> List<T> zrevrange(@RedisProtocolSupport.Key final Buffer key, final long start,
                                 final long stop) throws Exception {
        return blockingInvocation(commander.zrevrange(key, start, stop));
    }

    @Override
    public <T> List<T> zrevrange(@RedisProtocolSupport.Key final Buffer key, final long start, final long stop,
                                 @Nullable final RedisProtocolSupport.ZrevrangeWithscores withscores) throws Exception {
        return blockingInvocation(commander.zrevrange(key, start, stop, withscores));
    }

    @Override
    public <T> List<T> zrevrangebylex(@RedisProtocolSupport.Key final Buffer key, final Buffer max,
                                      final Buffer min) throws Exception {
        return blockingInvocation(commander.zrevrangebylex(key, max, min));
    }

    @Override
    public <T> List<T> zrevrangebylex(@RedisProtocolSupport.Key final Buffer key, final Buffer max, final Buffer min,
                                      @Nullable final RedisProtocolSupport.OffsetCount offsetCount) throws Exception {
        return blockingInvocation(commander.zrevrangebylex(key, max, min, offsetCount));
    }

    @Override
    public <T> List<T> zrevrangebyscore(@RedisProtocolSupport.Key final Buffer key, final double max,
                                        final double min) throws Exception {
        return blockingInvocation(commander.zrevrangebyscore(key, max, min));
    }

    @Override
    public <T> List<T> zrevrangebyscore(@RedisProtocolSupport.Key final Buffer key, final double max, final double min,
                                        @Nullable final RedisProtocolSupport.ZrevrangebyscoreWithscores withscores,
                                        @Nullable final RedisProtocolSupport.OffsetCount offsetCount) throws Exception {
        return blockingInvocation(commander.zrevrangebyscore(key, max, min, withscores, offsetCount));
    }

    @Override
    public Long zrevrank(@RedisProtocolSupport.Key final Buffer key, final Buffer member) throws Exception {
        return blockingInvocation(commander.zrevrank(key, member));
    }

    @Override
    public <T> List<T> zscan(@RedisProtocolSupport.Key final Buffer key, final long cursor) throws Exception {
        return blockingInvocation(commander.zscan(key, cursor));
    }

    @Override
    public <T> List<T> zscan(@RedisProtocolSupport.Key final Buffer key, final long cursor,
                             @Nullable final Buffer matchPattern, @Nullable final Long count) throws Exception {
        return blockingInvocation(commander.zscan(key, cursor, matchPattern, count));
    }

    @Override
    public Double zscore(@RedisProtocolSupport.Key final Buffer key, final Buffer member) throws Exception {
        return blockingInvocation(commander.zscore(key, member));
    }

    @Override
    public Long zunionstore(@RedisProtocolSupport.Key final Buffer destination, final long numkeys,
                            @RedisProtocolSupport.Key final Collection<Buffer> keys) throws Exception {
        return blockingInvocation(commander.zunionstore(destination, numkeys, keys));
    }

    @Override
    public Long zunionstore(@RedisProtocolSupport.Key final Buffer destination, final long numkeys,
                            @RedisProtocolSupport.Key final Collection<Buffer> keys, final Collection<Long> weights,
                            @Nullable final RedisProtocolSupport.ZunionstoreAggregate aggregate) throws Exception {
        return blockingInvocation(commander.zunionstore(destination, numkeys, keys, weights, aggregate));
    }

    public BufferRedisCommander asBufferCommanderInternal() {
        return commander;
    }
}

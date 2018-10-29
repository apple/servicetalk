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

import io.servicetalk.concurrent.BlockingIterable;

import java.util.Collection;
import java.util.List;
import java.util.Objects;
import javax.annotation.Generated;
import javax.annotation.Nullable;

import static io.servicetalk.redis.api.BlockingUtils.blockingInvocation;

@Generated({})
@SuppressWarnings("unchecked")
final class PartitionedRedisCommanderToBlockingPartitionedRedisCommander extends BlockingRedisCommander {

    private final RedisCommander commander;

    PartitionedRedisCommanderToBlockingPartitionedRedisCommander(final RedisCommander commander) {
        this.commander = Objects.requireNonNull(commander);
    }

    @Override
    public void close() throws Exception {
        blockingInvocation(commander.closeAsync());
    }

    @Override
    public Long append(@RedisProtocolSupport.Key final CharSequence key, final CharSequence value) throws Exception {
        return blockingInvocation(commander.append(key, value));
    }

    @Override
    public String auth(final CharSequence password) throws Exception {
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
    public Long bitcount(@RedisProtocolSupport.Key final CharSequence key) throws Exception {
        return blockingInvocation(commander.bitcount(key));
    }

    @Override
    public Long bitcount(@RedisProtocolSupport.Key final CharSequence key, @Nullable final Long start,
                         @Nullable final Long end) throws Exception {
        return blockingInvocation(commander.bitcount(key, start, end));
    }

    @Override
    public List<Long> bitfield(@RedisProtocolSupport.Key final CharSequence key,
                               final Collection<RedisProtocolSupport.BitfieldOperation> operations) throws Exception {
        return blockingInvocation(commander.bitfield(key, operations));
    }

    @Override
    public Long bitop(final CharSequence operation, @RedisProtocolSupport.Key final CharSequence destkey,
                      @RedisProtocolSupport.Key final CharSequence key) throws Exception {
        return blockingInvocation(commander.bitop(operation, destkey, key));
    }

    @Override
    public Long bitop(final CharSequence operation, @RedisProtocolSupport.Key final CharSequence destkey,
                      @RedisProtocolSupport.Key final CharSequence key1,
                      @RedisProtocolSupport.Key final CharSequence key2) throws Exception {
        return blockingInvocation(commander.bitop(operation, destkey, key1, key2));
    }

    @Override
    public Long bitop(final CharSequence operation, @RedisProtocolSupport.Key final CharSequence destkey,
                      @RedisProtocolSupport.Key final CharSequence key1,
                      @RedisProtocolSupport.Key final CharSequence key2,
                      @RedisProtocolSupport.Key final CharSequence key3) throws Exception {
        return blockingInvocation(commander.bitop(operation, destkey, key1, key2, key3));
    }

    @Override
    public Long bitop(final CharSequence operation, @RedisProtocolSupport.Key final CharSequence destkey,
                      @RedisProtocolSupport.Key final Collection<? extends CharSequence> keys) throws Exception {
        return blockingInvocation(commander.bitop(operation, destkey, keys));
    }

    @Override
    public Long bitpos(@RedisProtocolSupport.Key final CharSequence key, final long bit) throws Exception {
        return blockingInvocation(commander.bitpos(key, bit));
    }

    @Override
    public Long bitpos(@RedisProtocolSupport.Key final CharSequence key, final long bit, @Nullable final Long start,
                       @Nullable final Long end) throws Exception {
        return blockingInvocation(commander.bitpos(key, bit, start, end));
    }

    @Override
    public <T> List<T> blpop(@RedisProtocolSupport.Key final Collection<? extends CharSequence> keys,
                             final long timeout) throws Exception {
        return blockingInvocation(commander.blpop(keys, timeout));
    }

    @Override
    public <T> List<T> brpop(@RedisProtocolSupport.Key final Collection<? extends CharSequence> keys,
                             final long timeout) throws Exception {
        return blockingInvocation(commander.brpop(keys, timeout));
    }

    @Override
    public String brpoplpush(@RedisProtocolSupport.Key final CharSequence source,
                             @RedisProtocolSupport.Key final CharSequence destination,
                             final long timeout) throws Exception {
        return blockingInvocation(commander.brpoplpush(source, destination, timeout));
    }

    @Override
    public <T> List<T> bzpopmax(@RedisProtocolSupport.Key final Collection<? extends CharSequence> keys,
                                final long timeout) throws Exception {
        return blockingInvocation(commander.bzpopmax(keys, timeout));
    }

    @Override
    public <T> List<T> bzpopmin(@RedisProtocolSupport.Key final Collection<? extends CharSequence> keys,
                                final long timeout) throws Exception {
        return blockingInvocation(commander.bzpopmin(keys, timeout));
    }

    @Override
    public Long clientKill(@Nullable final Long id, @Nullable final RedisProtocolSupport.ClientKillType type,
                           @Nullable final CharSequence addrIpPort,
                           @Nullable final CharSequence skipmeYesNo) throws Exception {
        return blockingInvocation(commander.clientKill(id, type, addrIpPort, skipmeYesNo));
    }

    @Override
    public String clientList() throws Exception {
        return blockingInvocation(commander.clientList());
    }

    @Override
    public String clientGetname() throws Exception {
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
    public String clientSetname(final CharSequence connectionName) throws Exception {
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
    public Long clusterCountFailureReports(final CharSequence nodeId) throws Exception {
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
    public String clusterForget(final CharSequence nodeId) throws Exception {
        return blockingInvocation(commander.clusterForget(nodeId));
    }

    @Override
    public <T> List<T> clusterGetkeysinslot(final long slot, final long count) throws Exception {
        return blockingInvocation(commander.clusterGetkeysinslot(slot, count));
    }

    @Override
    public String clusterInfo() throws Exception {
        return blockingInvocation(commander.clusterInfo());
    }

    @Override
    public Long clusterKeyslot(final CharSequence key) throws Exception {
        return blockingInvocation(commander.clusterKeyslot(key));
    }

    @Override
    public String clusterMeet(final CharSequence ip, final long port) throws Exception {
        return blockingInvocation(commander.clusterMeet(ip, port));
    }

    @Override
    public String clusterNodes() throws Exception {
        return blockingInvocation(commander.clusterNodes());
    }

    @Override
    public String clusterReplicate(final CharSequence nodeId) throws Exception {
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
                                 @Nullable final CharSequence nodeId) throws Exception {
        return blockingInvocation(commander.clusterSetslot(slot, subcommand, nodeId));
    }

    @Override
    public String clusterSlaves(final CharSequence nodeId) throws Exception {
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
    public <T> List<T> commandInfo(final CharSequence commandName) throws Exception {
        return blockingInvocation(commander.commandInfo(commandName));
    }

    @Override
    public <T> List<T> commandInfo(final CharSequence commandName1, final CharSequence commandName2) throws Exception {
        return blockingInvocation(commander.commandInfo(commandName1, commandName2));
    }

    @Override
    public <T> List<T> commandInfo(final CharSequence commandName1, final CharSequence commandName2,
                                   final CharSequence commandName3) throws Exception {
        return blockingInvocation(commander.commandInfo(commandName1, commandName2, commandName3));
    }

    @Override
    public <T> List<T> commandInfo(final Collection<? extends CharSequence> commandNames) throws Exception {
        return blockingInvocation(commander.commandInfo(commandNames));
    }

    @Override
    public <T> List<T> configGet(final CharSequence parameter) throws Exception {
        return blockingInvocation(commander.configGet(parameter));
    }

    @Override
    public String configRewrite() throws Exception {
        return blockingInvocation(commander.configRewrite());
    }

    @Override
    public String configSet(final CharSequence parameter, final CharSequence value) throws Exception {
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
    public String debugObject(@RedisProtocolSupport.Key final CharSequence key) throws Exception {
        return blockingInvocation(commander.debugObject(key));
    }

    @Override
    public String debugSegfault() throws Exception {
        return blockingInvocation(commander.debugSegfault());
    }

    @Override
    public Long decr(@RedisProtocolSupport.Key final CharSequence key) throws Exception {
        return blockingInvocation(commander.decr(key));
    }

    @Override
    public Long decrby(@RedisProtocolSupport.Key final CharSequence key, final long decrement) throws Exception {
        return blockingInvocation(commander.decrby(key, decrement));
    }

    @Override
    public Long del(@RedisProtocolSupport.Key final CharSequence key) throws Exception {
        return blockingInvocation(commander.del(key));
    }

    @Override
    public Long del(@RedisProtocolSupport.Key final CharSequence key1,
                    @RedisProtocolSupport.Key final CharSequence key2) throws Exception {
        return blockingInvocation(commander.del(key1, key2));
    }

    @Override
    public Long del(@RedisProtocolSupport.Key final CharSequence key1,
                    @RedisProtocolSupport.Key final CharSequence key2,
                    @RedisProtocolSupport.Key final CharSequence key3) throws Exception {
        return blockingInvocation(commander.del(key1, key2, key3));
    }

    @Override
    public Long del(@RedisProtocolSupport.Key final Collection<? extends CharSequence> keys) throws Exception {
        return blockingInvocation(commander.del(keys));
    }

    @Override
    public String dump(@RedisProtocolSupport.Key final CharSequence key) throws Exception {
        return blockingInvocation(commander.dump(key));
    }

    @Override
    public String echo(final CharSequence message) throws Exception {
        return blockingInvocation(commander.echo(message));
    }

    @Override
    public String eval(final CharSequence script, final long numkeys,
                       @RedisProtocolSupport.Key final Collection<? extends CharSequence> keys,
                       final Collection<? extends CharSequence> args) throws Exception {
        return blockingInvocation(commander.eval(script, numkeys, keys, args));
    }

    @Override
    public <T> List<T> evalList(final CharSequence script, final long numkeys,
                                @RedisProtocolSupport.Key final Collection<? extends CharSequence> keys,
                                final Collection<? extends CharSequence> args) throws Exception {
        return blockingInvocation(commander.evalList(script, numkeys, keys, args));
    }

    @Override
    public Long evalLong(final CharSequence script, final long numkeys,
                         @RedisProtocolSupport.Key final Collection<? extends CharSequence> keys,
                         final Collection<? extends CharSequence> args) throws Exception {
        return blockingInvocation(commander.evalLong(script, numkeys, keys, args));
    }

    @Override
    public String evalsha(final CharSequence sha1, final long numkeys,
                          @RedisProtocolSupport.Key final Collection<? extends CharSequence> keys,
                          final Collection<? extends CharSequence> args) throws Exception {
        return blockingInvocation(commander.evalsha(sha1, numkeys, keys, args));
    }

    @Override
    public <T> List<T> evalshaList(final CharSequence sha1, final long numkeys,
                                   @RedisProtocolSupport.Key final Collection<? extends CharSequence> keys,
                                   final Collection<? extends CharSequence> args) throws Exception {
        return blockingInvocation(commander.evalshaList(sha1, numkeys, keys, args));
    }

    @Override
    public Long evalshaLong(final CharSequence sha1, final long numkeys,
                            @RedisProtocolSupport.Key final Collection<? extends CharSequence> keys,
                            final Collection<? extends CharSequence> args) throws Exception {
        return blockingInvocation(commander.evalshaLong(sha1, numkeys, keys, args));
    }

    @Override
    public Long exists(@RedisProtocolSupport.Key final CharSequence key) throws Exception {
        return blockingInvocation(commander.exists(key));
    }

    @Override
    public Long exists(@RedisProtocolSupport.Key final CharSequence key1,
                       @RedisProtocolSupport.Key final CharSequence key2) throws Exception {
        return blockingInvocation(commander.exists(key1, key2));
    }

    @Override
    public Long exists(@RedisProtocolSupport.Key final CharSequence key1,
                       @RedisProtocolSupport.Key final CharSequence key2,
                       @RedisProtocolSupport.Key final CharSequence key3) throws Exception {
        return blockingInvocation(commander.exists(key1, key2, key3));
    }

    @Override
    public Long exists(@RedisProtocolSupport.Key final Collection<? extends CharSequence> keys) throws Exception {
        return blockingInvocation(commander.exists(keys));
    }

    @Override
    public Long expire(@RedisProtocolSupport.Key final CharSequence key, final long seconds) throws Exception {
        return blockingInvocation(commander.expire(key, seconds));
    }

    @Override
    public Long expireat(@RedisProtocolSupport.Key final CharSequence key, final long timestamp) throws Exception {
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
    public Long geoadd(@RedisProtocolSupport.Key final CharSequence key, final double longitude, final double latitude,
                       final CharSequence member) throws Exception {
        return blockingInvocation(commander.geoadd(key, longitude, latitude, member));
    }

    @Override
    public Long geoadd(@RedisProtocolSupport.Key final CharSequence key, final double longitude1,
                       final double latitude1, final CharSequence member1, final double longitude2,
                       final double latitude2, final CharSequence member2) throws Exception {
        return blockingInvocation(
                    commander.geoadd(key, longitude1, latitude1, member1, longitude2, latitude2, member2));
    }

    @Override
    public Long geoadd(@RedisProtocolSupport.Key final CharSequence key, final double longitude1,
                       final double latitude1, final CharSequence member1, final double longitude2,
                       final double latitude2, final CharSequence member2, final double longitude3,
                       final double latitude3, final CharSequence member3) throws Exception {
        return blockingInvocation(commander.geoadd(key, longitude1, latitude1, member1, longitude2, latitude2, member2,
                    longitude3, latitude3, member3));
    }

    @Override
    public Long geoadd(@RedisProtocolSupport.Key final CharSequence key,
                       final Collection<RedisProtocolSupport.LongitudeLatitudeMember> longitudeLatitudeMembers) throws Exception {
        return blockingInvocation(commander.geoadd(key, longitudeLatitudeMembers));
    }

    @Override
    public Double geodist(@RedisProtocolSupport.Key final CharSequence key, final CharSequence member1,
                          final CharSequence member2) throws Exception {
        return blockingInvocation(commander.geodist(key, member1, member2));
    }

    @Override
    public Double geodist(@RedisProtocolSupport.Key final CharSequence key, final CharSequence member1,
                          final CharSequence member2, @Nullable final CharSequence unit) throws Exception {
        return blockingInvocation(commander.geodist(key, member1, member2, unit));
    }

    @Override
    public <T> List<T> geohash(@RedisProtocolSupport.Key final CharSequence key,
                               final CharSequence member) throws Exception {
        return blockingInvocation(commander.geohash(key, member));
    }

    @Override
    public <T> List<T> geohash(@RedisProtocolSupport.Key final CharSequence key, final CharSequence member1,
                               final CharSequence member2) throws Exception {
        return blockingInvocation(commander.geohash(key, member1, member2));
    }

    @Override
    public <T> List<T> geohash(@RedisProtocolSupport.Key final CharSequence key, final CharSequence member1,
                               final CharSequence member2, final CharSequence member3) throws Exception {
        return blockingInvocation(commander.geohash(key, member1, member2, member3));
    }

    @Override
    public <T> List<T> geohash(@RedisProtocolSupport.Key final CharSequence key,
                               final Collection<? extends CharSequence> members) throws Exception {
        return blockingInvocation(commander.geohash(key, members));
    }

    @Override
    public <T> List<T> geopos(@RedisProtocolSupport.Key final CharSequence key,
                              final CharSequence member) throws Exception {
        return blockingInvocation(commander.geopos(key, member));
    }

    @Override
    public <T> List<T> geopos(@RedisProtocolSupport.Key final CharSequence key, final CharSequence member1,
                              final CharSequence member2) throws Exception {
        return blockingInvocation(commander.geopos(key, member1, member2));
    }

    @Override
    public <T> List<T> geopos(@RedisProtocolSupport.Key final CharSequence key, final CharSequence member1,
                              final CharSequence member2, final CharSequence member3) throws Exception {
        return blockingInvocation(commander.geopos(key, member1, member2, member3));
    }

    @Override
    public <T> List<T> geopos(@RedisProtocolSupport.Key final CharSequence key,
                              final Collection<? extends CharSequence> members) throws Exception {
        return blockingInvocation(commander.geopos(key, members));
    }

    @Override
    public <T> List<T> georadius(@RedisProtocolSupport.Key final CharSequence key, final double longitude,
                                 final double latitude, final double radius,
                                 final RedisProtocolSupport.GeoradiusUnit unit) throws Exception {
        return blockingInvocation(commander.georadius(key, longitude, latitude, radius, unit));
    }

    @Override
    public <T> List<T> georadius(@RedisProtocolSupport.Key final CharSequence key, final double longitude,
                                 final double latitude, final double radius,
                                 final RedisProtocolSupport.GeoradiusUnit unit,
                                 @Nullable final RedisProtocolSupport.GeoradiusWithcoord withcoord,
                                 @Nullable final RedisProtocolSupport.GeoradiusWithdist withdist,
                                 @Nullable final RedisProtocolSupport.GeoradiusWithhash withhash,
                                 @Nullable final Long count, @Nullable final RedisProtocolSupport.GeoradiusOrder order,
                                 @Nullable @RedisProtocolSupport.Key final CharSequence storeKey,
                                 @Nullable @RedisProtocolSupport.Key final CharSequence storedistKey) throws Exception {
        return blockingInvocation(commander.georadius(key, longitude, latitude, radius, unit, withcoord, withdist,
                    withhash, count, order, storeKey, storedistKey));
    }

    @Override
    public <T> List<T> georadiusbymember(@RedisProtocolSupport.Key final CharSequence key, final CharSequence member,
                                         final double radius,
                                         final RedisProtocolSupport.GeoradiusbymemberUnit unit) throws Exception {
        return blockingInvocation(commander.georadiusbymember(key, member, radius, unit));
    }

    @Override
    public <T> List<T> georadiusbymember(@RedisProtocolSupport.Key final CharSequence key, final CharSequence member,
                                         final double radius, final RedisProtocolSupport.GeoradiusbymemberUnit unit,
                                         @Nullable final RedisProtocolSupport.GeoradiusbymemberWithcoord withcoord,
                                         @Nullable final RedisProtocolSupport.GeoradiusbymemberWithdist withdist,
                                         @Nullable final RedisProtocolSupport.GeoradiusbymemberWithhash withhash,
                                         @Nullable final Long count,
                                         @Nullable final RedisProtocolSupport.GeoradiusbymemberOrder order,
                                         @Nullable @RedisProtocolSupport.Key final CharSequence storeKey,
                                         @Nullable @RedisProtocolSupport.Key final CharSequence storedistKey) throws Exception {
        return blockingInvocation(commander.georadiusbymember(key, member, radius, unit, withcoord, withdist, withhash,
                    count, order, storeKey, storedistKey));
    }

    @Override
    public String get(@RedisProtocolSupport.Key final CharSequence key) throws Exception {
        return blockingInvocation(commander.get(key));
    }

    @Override
    public Long getbit(@RedisProtocolSupport.Key final CharSequence key, final long offset) throws Exception {
        return blockingInvocation(commander.getbit(key, offset));
    }

    @Override
    public String getrange(@RedisProtocolSupport.Key final CharSequence key, final long start,
                           final long end) throws Exception {
        return blockingInvocation(commander.getrange(key, start, end));
    }

    @Override
    public String getset(@RedisProtocolSupport.Key final CharSequence key, final CharSequence value) throws Exception {
        return blockingInvocation(commander.getset(key, value));
    }

    @Override
    public Long hdel(@RedisProtocolSupport.Key final CharSequence key, final CharSequence field) throws Exception {
        return blockingInvocation(commander.hdel(key, field));
    }

    @Override
    public Long hdel(@RedisProtocolSupport.Key final CharSequence key, final CharSequence field1,
                     final CharSequence field2) throws Exception {
        return blockingInvocation(commander.hdel(key, field1, field2));
    }

    @Override
    public Long hdel(@RedisProtocolSupport.Key final CharSequence key, final CharSequence field1,
                     final CharSequence field2, final CharSequence field3) throws Exception {
        return blockingInvocation(commander.hdel(key, field1, field2, field3));
    }

    @Override
    public Long hdel(@RedisProtocolSupport.Key final CharSequence key,
                     final Collection<? extends CharSequence> fields) throws Exception {
        return blockingInvocation(commander.hdel(key, fields));
    }

    @Override
    public Long hexists(@RedisProtocolSupport.Key final CharSequence key, final CharSequence field) throws Exception {
        return blockingInvocation(commander.hexists(key, field));
    }

    @Override
    public String hget(@RedisProtocolSupport.Key final CharSequence key, final CharSequence field) throws Exception {
        return blockingInvocation(commander.hget(key, field));
    }

    @Override
    public <T> List<T> hgetall(@RedisProtocolSupport.Key final CharSequence key) throws Exception {
        return blockingInvocation(commander.hgetall(key));
    }

    @Override
    public Long hincrby(@RedisProtocolSupport.Key final CharSequence key, final CharSequence field,
                        final long increment) throws Exception {
        return blockingInvocation(commander.hincrby(key, field, increment));
    }

    @Override
    public Double hincrbyfloat(@RedisProtocolSupport.Key final CharSequence key, final CharSequence field,
                               final double increment) throws Exception {
        return blockingInvocation(commander.hincrbyfloat(key, field, increment));
    }

    @Override
    public <T> List<T> hkeys(@RedisProtocolSupport.Key final CharSequence key) throws Exception {
        return blockingInvocation(commander.hkeys(key));
    }

    @Override
    public Long hlen(@RedisProtocolSupport.Key final CharSequence key) throws Exception {
        return blockingInvocation(commander.hlen(key));
    }

    @Override
    public List<String> hmget(@RedisProtocolSupport.Key final CharSequence key,
                              final CharSequence field) throws Exception {
        return blockingInvocation(commander.hmget(key, field));
    }

    @Override
    public List<String> hmget(@RedisProtocolSupport.Key final CharSequence key, final CharSequence field1,
                              final CharSequence field2) throws Exception {
        return blockingInvocation(commander.hmget(key, field1, field2));
    }

    @Override
    public List<String> hmget(@RedisProtocolSupport.Key final CharSequence key, final CharSequence field1,
                              final CharSequence field2, final CharSequence field3) throws Exception {
        return blockingInvocation(commander.hmget(key, field1, field2, field3));
    }

    @Override
    public List<String> hmget(@RedisProtocolSupport.Key final CharSequence key,
                              final Collection<? extends CharSequence> fields) throws Exception {
        return blockingInvocation(commander.hmget(key, fields));
    }

    @Override
    public String hmset(@RedisProtocolSupport.Key final CharSequence key, final CharSequence field,
                        final CharSequence value) throws Exception {
        return blockingInvocation(commander.hmset(key, field, value));
    }

    @Override
    public String hmset(@RedisProtocolSupport.Key final CharSequence key, final CharSequence field1,
                        final CharSequence value1, final CharSequence field2,
                        final CharSequence value2) throws Exception {
        return blockingInvocation(commander.hmset(key, field1, value1, field2, value2));
    }

    @Override
    public String hmset(@RedisProtocolSupport.Key final CharSequence key, final CharSequence field1,
                        final CharSequence value1, final CharSequence field2, final CharSequence value2,
                        final CharSequence field3, final CharSequence value3) throws Exception {
        return blockingInvocation(commander.hmset(key, field1, value1, field2, value2, field3, value3));
    }

    @Override
    public String hmset(@RedisProtocolSupport.Key final CharSequence key,
                        final Collection<RedisProtocolSupport.FieldValue> fieldValues) throws Exception {
        return blockingInvocation(commander.hmset(key, fieldValues));
    }

    @Override
    public <T> List<T> hscan(@RedisProtocolSupport.Key final CharSequence key, final long cursor) throws Exception {
        return blockingInvocation(commander.hscan(key, cursor));
    }

    @Override
    public <T> List<T> hscan(@RedisProtocolSupport.Key final CharSequence key, final long cursor,
                             @Nullable final CharSequence matchPattern, @Nullable final Long count) throws Exception {
        return blockingInvocation(commander.hscan(key, cursor, matchPattern, count));
    }

    @Override
    public Long hset(@RedisProtocolSupport.Key final CharSequence key, final CharSequence field,
                     final CharSequence value) throws Exception {
        return blockingInvocation(commander.hset(key, field, value));
    }

    @Override
    public Long hsetnx(@RedisProtocolSupport.Key final CharSequence key, final CharSequence field,
                       final CharSequence value) throws Exception {
        return blockingInvocation(commander.hsetnx(key, field, value));
    }

    @Override
    public Long hstrlen(@RedisProtocolSupport.Key final CharSequence key, final CharSequence field) throws Exception {
        return blockingInvocation(commander.hstrlen(key, field));
    }

    @Override
    public <T> List<T> hvals(@RedisProtocolSupport.Key final CharSequence key) throws Exception {
        return blockingInvocation(commander.hvals(key));
    }

    @Override
    public Long incr(@RedisProtocolSupport.Key final CharSequence key) throws Exception {
        return blockingInvocation(commander.incr(key));
    }

    @Override
    public Long incrby(@RedisProtocolSupport.Key final CharSequence key, final long increment) throws Exception {
        return blockingInvocation(commander.incrby(key, increment));
    }

    @Override
    public Double incrbyfloat(@RedisProtocolSupport.Key final CharSequence key,
                              final double increment) throws Exception {
        return blockingInvocation(commander.incrbyfloat(key, increment));
    }

    @Override
    public String info() throws Exception {
        return blockingInvocation(commander.info());
    }

    @Override
    public String info(@Nullable final CharSequence section) throws Exception {
        return blockingInvocation(commander.info(section));
    }

    @Override
    public <T> List<T> keys(final CharSequence pattern) throws Exception {
        return blockingInvocation(commander.keys(pattern));
    }

    @Override
    public Long lastsave() throws Exception {
        return blockingInvocation(commander.lastsave());
    }

    @Override
    public String lindex(@RedisProtocolSupport.Key final CharSequence key, final long index) throws Exception {
        return blockingInvocation(commander.lindex(key, index));
    }

    @Override
    public Long linsert(@RedisProtocolSupport.Key final CharSequence key, final RedisProtocolSupport.LinsertWhere where,
                        final CharSequence pivot, final CharSequence value) throws Exception {
        return blockingInvocation(commander.linsert(key, where, pivot, value));
    }

    @Override
    public Long llen(@RedisProtocolSupport.Key final CharSequence key) throws Exception {
        return blockingInvocation(commander.llen(key));
    }

    @Override
    public String lpop(@RedisProtocolSupport.Key final CharSequence key) throws Exception {
        return blockingInvocation(commander.lpop(key));
    }

    @Override
    public Long lpush(@RedisProtocolSupport.Key final CharSequence key, final CharSequence value) throws Exception {
        return blockingInvocation(commander.lpush(key, value));
    }

    @Override
    public Long lpush(@RedisProtocolSupport.Key final CharSequence key, final CharSequence value1,
                      final CharSequence value2) throws Exception {
        return blockingInvocation(commander.lpush(key, value1, value2));
    }

    @Override
    public Long lpush(@RedisProtocolSupport.Key final CharSequence key, final CharSequence value1,
                      final CharSequence value2, final CharSequence value3) throws Exception {
        return blockingInvocation(commander.lpush(key, value1, value2, value3));
    }

    @Override
    public Long lpush(@RedisProtocolSupport.Key final CharSequence key,
                      final Collection<? extends CharSequence> values) throws Exception {
        return blockingInvocation(commander.lpush(key, values));
    }

    @Override
    public Long lpushx(@RedisProtocolSupport.Key final CharSequence key, final CharSequence value) throws Exception {
        return blockingInvocation(commander.lpushx(key, value));
    }

    @Override
    public <T> List<T> lrange(@RedisProtocolSupport.Key final CharSequence key, final long start,
                              final long stop) throws Exception {
        return blockingInvocation(commander.lrange(key, start, stop));
    }

    @Override
    public Long lrem(@RedisProtocolSupport.Key final CharSequence key, final long count,
                     final CharSequence value) throws Exception {
        return blockingInvocation(commander.lrem(key, count, value));
    }

    @Override
    public String lset(@RedisProtocolSupport.Key final CharSequence key, final long index,
                       final CharSequence value) throws Exception {
        return blockingInvocation(commander.lset(key, index, value));
    }

    @Override
    public String ltrim(@RedisProtocolSupport.Key final CharSequence key, final long start,
                        final long stop) throws Exception {
        return blockingInvocation(commander.ltrim(key, start, stop));
    }

    @Override
    public String memoryDoctor() throws Exception {
        return blockingInvocation(commander.memoryDoctor());
    }

    @Override
    public <T> List<T> memoryHelp() throws Exception {
        return blockingInvocation(commander.memoryHelp());
    }

    @Override
    public String memoryMallocStats() throws Exception {
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
    public Long memoryUsage(@RedisProtocolSupport.Key final CharSequence key) throws Exception {
        return blockingInvocation(commander.memoryUsage(key));
    }

    @Override
    public Long memoryUsage(@RedisProtocolSupport.Key final CharSequence key,
                            @Nullable final Long samplesCount) throws Exception {
        return blockingInvocation(commander.memoryUsage(key, samplesCount));
    }

    @Override
    public List<String> mget(@RedisProtocolSupport.Key final CharSequence key) throws Exception {
        return blockingInvocation(commander.mget(key));
    }

    @Override
    public List<String> mget(@RedisProtocolSupport.Key final CharSequence key1,
                             @RedisProtocolSupport.Key final CharSequence key2) throws Exception {
        return blockingInvocation(commander.mget(key1, key2));
    }

    @Override
    public List<String> mget(@RedisProtocolSupport.Key final CharSequence key1,
                             @RedisProtocolSupport.Key final CharSequence key2,
                             @RedisProtocolSupport.Key final CharSequence key3) throws Exception {
        return blockingInvocation(commander.mget(key1, key2, key3));
    }

    @Override
    public List<String> mget(@RedisProtocolSupport.Key final Collection<? extends CharSequence> keys) throws Exception {
        return blockingInvocation(commander.mget(keys));
    }

    @Override
    public BlockingIterable<String> monitor() throws Exception {
        return commander.monitor().toIterable();
    }

    @Override
    public Long move(@RedisProtocolSupport.Key final CharSequence key, final long db) throws Exception {
        return blockingInvocation(commander.move(key, db));
    }

    @Override
    public String mset(@RedisProtocolSupport.Key final CharSequence key, final CharSequence value) throws Exception {
        return blockingInvocation(commander.mset(key, value));
    }

    @Override
    public String mset(@RedisProtocolSupport.Key final CharSequence key1, final CharSequence value1,
                       @RedisProtocolSupport.Key final CharSequence key2, final CharSequence value2) throws Exception {
        return blockingInvocation(commander.mset(key1, value1, key2, value2));
    }

    @Override
    public String mset(@RedisProtocolSupport.Key final CharSequence key1, final CharSequence value1,
                       @RedisProtocolSupport.Key final CharSequence key2, final CharSequence value2,
                       @RedisProtocolSupport.Key final CharSequence key3, final CharSequence value3) throws Exception {
        return blockingInvocation(commander.mset(key1, value1, key2, value2, key3, value3));
    }

    @Override
    public String mset(final Collection<RedisProtocolSupport.KeyValue> keyValues) throws Exception {
        return blockingInvocation(commander.mset(keyValues));
    }

    @Override
    public Long msetnx(@RedisProtocolSupport.Key final CharSequence key, final CharSequence value) throws Exception {
        return blockingInvocation(commander.msetnx(key, value));
    }

    @Override
    public Long msetnx(@RedisProtocolSupport.Key final CharSequence key1, final CharSequence value1,
                       @RedisProtocolSupport.Key final CharSequence key2, final CharSequence value2) throws Exception {
        return blockingInvocation(commander.msetnx(key1, value1, key2, value2));
    }

    @Override
    public Long msetnx(@RedisProtocolSupport.Key final CharSequence key1, final CharSequence value1,
                       @RedisProtocolSupport.Key final CharSequence key2, final CharSequence value2,
                       @RedisProtocolSupport.Key final CharSequence key3, final CharSequence value3) throws Exception {
        return blockingInvocation(commander.msetnx(key1, value1, key2, value2, key3, value3));
    }

    @Override
    public Long msetnx(final Collection<RedisProtocolSupport.KeyValue> keyValues) throws Exception {
        return blockingInvocation(commander.msetnx(keyValues));
    }

    @Override
    public BlockingTransactedRedisCommander multi() throws Exception {
        return blockingInvocation(
                    commander.multi().map(TransactedRedisCommanderToBlockingTransactedRedisCommander::new));
    }

    @Override
    public String objectEncoding(@RedisProtocolSupport.Key final CharSequence key) throws Exception {
        return blockingInvocation(commander.objectEncoding(key));
    }

    @Override
    public Long objectFreq(@RedisProtocolSupport.Key final CharSequence key) throws Exception {
        return blockingInvocation(commander.objectFreq(key));
    }

    @Override
    public List<String> objectHelp() throws Exception {
        return blockingInvocation(commander.objectHelp());
    }

    @Override
    public Long objectIdletime(@RedisProtocolSupport.Key final CharSequence key) throws Exception {
        return blockingInvocation(commander.objectIdletime(key));
    }

    @Override
    public Long objectRefcount(@RedisProtocolSupport.Key final CharSequence key) throws Exception {
        return blockingInvocation(commander.objectRefcount(key));
    }

    @Override
    public Long persist(@RedisProtocolSupport.Key final CharSequence key) throws Exception {
        return blockingInvocation(commander.persist(key));
    }

    @Override
    public Long pexpire(@RedisProtocolSupport.Key final CharSequence key, final long milliseconds) throws Exception {
        return blockingInvocation(commander.pexpire(key, milliseconds));
    }

    @Override
    public Long pexpireat(@RedisProtocolSupport.Key final CharSequence key,
                          final long millisecondsTimestamp) throws Exception {
        return blockingInvocation(commander.pexpireat(key, millisecondsTimestamp));
    }

    @Override
    public Long pfadd(@RedisProtocolSupport.Key final CharSequence key, final CharSequence element) throws Exception {
        return blockingInvocation(commander.pfadd(key, element));
    }

    @Override
    public Long pfadd(@RedisProtocolSupport.Key final CharSequence key, final CharSequence element1,
                      final CharSequence element2) throws Exception {
        return blockingInvocation(commander.pfadd(key, element1, element2));
    }

    @Override
    public Long pfadd(@RedisProtocolSupport.Key final CharSequence key, final CharSequence element1,
                      final CharSequence element2, final CharSequence element3) throws Exception {
        return blockingInvocation(commander.pfadd(key, element1, element2, element3));
    }

    @Override
    public Long pfadd(@RedisProtocolSupport.Key final CharSequence key,
                      final Collection<? extends CharSequence> elements) throws Exception {
        return blockingInvocation(commander.pfadd(key, elements));
    }

    @Override
    public Long pfcount(@RedisProtocolSupport.Key final CharSequence key) throws Exception {
        return blockingInvocation(commander.pfcount(key));
    }

    @Override
    public Long pfcount(@RedisProtocolSupport.Key final CharSequence key1,
                        @RedisProtocolSupport.Key final CharSequence key2) throws Exception {
        return blockingInvocation(commander.pfcount(key1, key2));
    }

    @Override
    public Long pfcount(@RedisProtocolSupport.Key final CharSequence key1,
                        @RedisProtocolSupport.Key final CharSequence key2,
                        @RedisProtocolSupport.Key final CharSequence key3) throws Exception {
        return blockingInvocation(commander.pfcount(key1, key2, key3));
    }

    @Override
    public Long pfcount(@RedisProtocolSupport.Key final Collection<? extends CharSequence> keys) throws Exception {
        return blockingInvocation(commander.pfcount(keys));
    }

    @Override
    public String pfmerge(@RedisProtocolSupport.Key final CharSequence destkey,
                          @RedisProtocolSupport.Key final CharSequence sourcekey) throws Exception {
        return blockingInvocation(commander.pfmerge(destkey, sourcekey));
    }

    @Override
    public String pfmerge(@RedisProtocolSupport.Key final CharSequence destkey,
                          @RedisProtocolSupport.Key final CharSequence sourcekey1,
                          @RedisProtocolSupport.Key final CharSequence sourcekey2) throws Exception {
        return blockingInvocation(commander.pfmerge(destkey, sourcekey1, sourcekey2));
    }

    @Override
    public String pfmerge(@RedisProtocolSupport.Key final CharSequence destkey,
                          @RedisProtocolSupport.Key final CharSequence sourcekey1,
                          @RedisProtocolSupport.Key final CharSequence sourcekey2,
                          @RedisProtocolSupport.Key final CharSequence sourcekey3) throws Exception {
        return blockingInvocation(commander.pfmerge(destkey, sourcekey1, sourcekey2, sourcekey3));
    }

    @Override
    public String pfmerge(@RedisProtocolSupport.Key final CharSequence destkey,
                          @RedisProtocolSupport.Key final Collection<? extends CharSequence> sourcekeys) throws Exception {
        return blockingInvocation(commander.pfmerge(destkey, sourcekeys));
    }

    @Override
    public String ping() throws Exception {
        return blockingInvocation(commander.ping());
    }

    @Override
    public String ping(final CharSequence message) throws Exception {
        return blockingInvocation(commander.ping(message));
    }

    @Override
    public String psetex(@RedisProtocolSupport.Key final CharSequence key, final long milliseconds,
                         final CharSequence value) throws Exception {
        return blockingInvocation(commander.psetex(key, milliseconds, value));
    }

    @Override
    public BlockingPubSubRedisConnection psubscribe(final CharSequence pattern) throws Exception {
        return blockingInvocation(
                    commander.psubscribe(pattern).map(PubSubRedisConnectionToBlockingPubSubRedisConnection::new));
    }

    @Override
    public Long pttl(@RedisProtocolSupport.Key final CharSequence key) throws Exception {
        return blockingInvocation(commander.pttl(key));
    }

    @Override
    public Long publish(final CharSequence channel, final CharSequence message) throws Exception {
        return blockingInvocation(commander.publish(channel, message));
    }

    @Override
    public List<String> pubsubChannels() throws Exception {
        return blockingInvocation(commander.pubsubChannels());
    }

    @Override
    public List<String> pubsubChannels(@Nullable final CharSequence pattern) throws Exception {
        return blockingInvocation(commander.pubsubChannels(pattern));
    }

    @Override
    public List<String> pubsubChannels(@Nullable final CharSequence pattern1,
                                       @Nullable final CharSequence pattern2) throws Exception {
        return blockingInvocation(commander.pubsubChannels(pattern1, pattern2));
    }

    @Override
    public List<String> pubsubChannels(@Nullable final CharSequence pattern1, @Nullable final CharSequence pattern2,
                                       @Nullable final CharSequence pattern3) throws Exception {
        return blockingInvocation(commander.pubsubChannels(pattern1, pattern2, pattern3));
    }

    @Override
    public List<String> pubsubChannels(final Collection<? extends CharSequence> patterns) throws Exception {
        return blockingInvocation(commander.pubsubChannels(patterns));
    }

    @Override
    public <T> List<T> pubsubNumsub() throws Exception {
        return blockingInvocation(commander.pubsubNumsub());
    }

    @Override
    public <T> List<T> pubsubNumsub(@Nullable final CharSequence channel) throws Exception {
        return blockingInvocation(commander.pubsubNumsub(channel));
    }

    @Override
    public <T> List<T> pubsubNumsub(@Nullable final CharSequence channel1,
                                    @Nullable final CharSequence channel2) throws Exception {
        return blockingInvocation(commander.pubsubNumsub(channel1, channel2));
    }

    @Override
    public <T> List<T> pubsubNumsub(@Nullable final CharSequence channel1, @Nullable final CharSequence channel2,
                                    @Nullable final CharSequence channel3) throws Exception {
        return blockingInvocation(commander.pubsubNumsub(channel1, channel2, channel3));
    }

    @Override
    public <T> List<T> pubsubNumsub(final Collection<? extends CharSequence> channels) throws Exception {
        return blockingInvocation(commander.pubsubNumsub(channels));
    }

    @Override
    public Long pubsubNumpat() throws Exception {
        return blockingInvocation(commander.pubsubNumpat());
    }

    @Override
    public String randomkey() throws Exception {
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
    public String rename(@RedisProtocolSupport.Key final CharSequence key,
                         @RedisProtocolSupport.Key final CharSequence newkey) throws Exception {
        return blockingInvocation(commander.rename(key, newkey));
    }

    @Override
    public Long renamenx(@RedisProtocolSupport.Key final CharSequence key,
                         @RedisProtocolSupport.Key final CharSequence newkey) throws Exception {
        return blockingInvocation(commander.renamenx(key, newkey));
    }

    @Override
    public String restore(@RedisProtocolSupport.Key final CharSequence key, final long ttl,
                          final CharSequence serializedValue) throws Exception {
        return blockingInvocation(commander.restore(key, ttl, serializedValue));
    }

    @Override
    public String restore(@RedisProtocolSupport.Key final CharSequence key, final long ttl,
                          final CharSequence serializedValue,
                          @Nullable final RedisProtocolSupport.RestoreReplace replace) throws Exception {
        return blockingInvocation(commander.restore(key, ttl, serializedValue, replace));
    }

    @Override
    public <T> List<T> role() throws Exception {
        return blockingInvocation(commander.role());
    }

    @Override
    public String rpop(@RedisProtocolSupport.Key final CharSequence key) throws Exception {
        return blockingInvocation(commander.rpop(key));
    }

    @Override
    public String rpoplpush(@RedisProtocolSupport.Key final CharSequence source,
                            @RedisProtocolSupport.Key final CharSequence destination) throws Exception {
        return blockingInvocation(commander.rpoplpush(source, destination));
    }

    @Override
    public Long rpush(@RedisProtocolSupport.Key final CharSequence key, final CharSequence value) throws Exception {
        return blockingInvocation(commander.rpush(key, value));
    }

    @Override
    public Long rpush(@RedisProtocolSupport.Key final CharSequence key, final CharSequence value1,
                      final CharSequence value2) throws Exception {
        return blockingInvocation(commander.rpush(key, value1, value2));
    }

    @Override
    public Long rpush(@RedisProtocolSupport.Key final CharSequence key, final CharSequence value1,
                      final CharSequence value2, final CharSequence value3) throws Exception {
        return blockingInvocation(commander.rpush(key, value1, value2, value3));
    }

    @Override
    public Long rpush(@RedisProtocolSupport.Key final CharSequence key,
                      final Collection<? extends CharSequence> values) throws Exception {
        return blockingInvocation(commander.rpush(key, values));
    }

    @Override
    public Long rpushx(@RedisProtocolSupport.Key final CharSequence key, final CharSequence value) throws Exception {
        return blockingInvocation(commander.rpushx(key, value));
    }

    @Override
    public Long sadd(@RedisProtocolSupport.Key final CharSequence key, final CharSequence member) throws Exception {
        return blockingInvocation(commander.sadd(key, member));
    }

    @Override
    public Long sadd(@RedisProtocolSupport.Key final CharSequence key, final CharSequence member1,
                     final CharSequence member2) throws Exception {
        return blockingInvocation(commander.sadd(key, member1, member2));
    }

    @Override
    public Long sadd(@RedisProtocolSupport.Key final CharSequence key, final CharSequence member1,
                     final CharSequence member2, final CharSequence member3) throws Exception {
        return blockingInvocation(commander.sadd(key, member1, member2, member3));
    }

    @Override
    public Long sadd(@RedisProtocolSupport.Key final CharSequence key,
                     final Collection<? extends CharSequence> members) throws Exception {
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
    public <T> List<T> scan(final long cursor, @Nullable final CharSequence matchPattern,
                            @Nullable final Long count) throws Exception {
        return blockingInvocation(commander.scan(cursor, matchPattern, count));
    }

    @Override
    public Long scard(@RedisProtocolSupport.Key final CharSequence key) throws Exception {
        return blockingInvocation(commander.scard(key));
    }

    @Override
    public String scriptDebug(final RedisProtocolSupport.ScriptDebugMode mode) throws Exception {
        return blockingInvocation(commander.scriptDebug(mode));
    }

    @Override
    public <T> List<T> scriptExists(final CharSequence sha1) throws Exception {
        return blockingInvocation(commander.scriptExists(sha1));
    }

    @Override
    public <T> List<T> scriptExists(final CharSequence sha11, final CharSequence sha12) throws Exception {
        return blockingInvocation(commander.scriptExists(sha11, sha12));
    }

    @Override
    public <T> List<T> scriptExists(final CharSequence sha11, final CharSequence sha12,
                                    final CharSequence sha13) throws Exception {
        return blockingInvocation(commander.scriptExists(sha11, sha12, sha13));
    }

    @Override
    public <T> List<T> scriptExists(final Collection<? extends CharSequence> sha1s) throws Exception {
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
    public String scriptLoad(final CharSequence script) throws Exception {
        return blockingInvocation(commander.scriptLoad(script));
    }

    @Override
    public <T> List<T> sdiff(@RedisProtocolSupport.Key final CharSequence firstkey) throws Exception {
        return blockingInvocation(commander.sdiff(firstkey));
    }

    @Override
    public <T> List<T> sdiff(@RedisProtocolSupport.Key final CharSequence firstkey,
                             @Nullable @RedisProtocolSupport.Key final CharSequence otherkey) throws Exception {
        return blockingInvocation(commander.sdiff(firstkey, otherkey));
    }

    @Override
    public <T> List<T> sdiff(@RedisProtocolSupport.Key final CharSequence firstkey,
                             @Nullable @RedisProtocolSupport.Key final CharSequence otherkey1,
                             @Nullable @RedisProtocolSupport.Key final CharSequence otherkey2) throws Exception {
        return blockingInvocation(commander.sdiff(firstkey, otherkey1, otherkey2));
    }

    @Override
    public <T> List<T> sdiff(@RedisProtocolSupport.Key final CharSequence firstkey,
                             @Nullable @RedisProtocolSupport.Key final CharSequence otherkey1,
                             @Nullable @RedisProtocolSupport.Key final CharSequence otherkey2,
                             @Nullable @RedisProtocolSupport.Key final CharSequence otherkey3) throws Exception {
        return blockingInvocation(commander.sdiff(firstkey, otherkey1, otherkey2, otherkey3));
    }

    @Override
    public <T> List<T> sdiff(@RedisProtocolSupport.Key final CharSequence firstkey,
                             @RedisProtocolSupport.Key final Collection<? extends CharSequence> otherkeys) throws Exception {
        return blockingInvocation(commander.sdiff(firstkey, otherkeys));
    }

    @Override
    public Long sdiffstore(@RedisProtocolSupport.Key final CharSequence destination,
                           @RedisProtocolSupport.Key final CharSequence firstkey) throws Exception {
        return blockingInvocation(commander.sdiffstore(destination, firstkey));
    }

    @Override
    public Long sdiffstore(@RedisProtocolSupport.Key final CharSequence destination,
                           @RedisProtocolSupport.Key final CharSequence firstkey,
                           @Nullable @RedisProtocolSupport.Key final CharSequence otherkey) throws Exception {
        return blockingInvocation(commander.sdiffstore(destination, firstkey, otherkey));
    }

    @Override
    public Long sdiffstore(@RedisProtocolSupport.Key final CharSequence destination,
                           @RedisProtocolSupport.Key final CharSequence firstkey,
                           @Nullable @RedisProtocolSupport.Key final CharSequence otherkey1,
                           @Nullable @RedisProtocolSupport.Key final CharSequence otherkey2) throws Exception {
        return blockingInvocation(commander.sdiffstore(destination, firstkey, otherkey1, otherkey2));
    }

    @Override
    public Long sdiffstore(@RedisProtocolSupport.Key final CharSequence destination,
                           @RedisProtocolSupport.Key final CharSequence firstkey,
                           @Nullable @RedisProtocolSupport.Key final CharSequence otherkey1,
                           @Nullable @RedisProtocolSupport.Key final CharSequence otherkey2,
                           @Nullable @RedisProtocolSupport.Key final CharSequence otherkey3) throws Exception {
        return blockingInvocation(commander.sdiffstore(destination, firstkey, otherkey1, otherkey2, otherkey3));
    }

    @Override
    public Long sdiffstore(@RedisProtocolSupport.Key final CharSequence destination,
                           @RedisProtocolSupport.Key final CharSequence firstkey,
                           @RedisProtocolSupport.Key final Collection<? extends CharSequence> otherkeys) throws Exception {
        return blockingInvocation(commander.sdiffstore(destination, firstkey, otherkeys));
    }

    @Override
    public String select(final long index) throws Exception {
        return blockingInvocation(commander.select(index));
    }

    @Override
    public String set(@RedisProtocolSupport.Key final CharSequence key, final CharSequence value) throws Exception {
        return blockingInvocation(commander.set(key, value));
    }

    @Override
    public String set(@RedisProtocolSupport.Key final CharSequence key, final CharSequence value,
                      @Nullable final RedisProtocolSupport.ExpireDuration expireDuration,
                      @Nullable final RedisProtocolSupport.SetCondition condition) throws Exception {
        return blockingInvocation(commander.set(key, value, expireDuration, condition));
    }

    @Override
    public Long setbit(@RedisProtocolSupport.Key final CharSequence key, final long offset,
                       final CharSequence value) throws Exception {
        return blockingInvocation(commander.setbit(key, offset, value));
    }

    @Override
    public String setex(@RedisProtocolSupport.Key final CharSequence key, final long seconds,
                        final CharSequence value) throws Exception {
        return blockingInvocation(commander.setex(key, seconds, value));
    }

    @Override
    public Long setnx(@RedisProtocolSupport.Key final CharSequence key, final CharSequence value) throws Exception {
        return blockingInvocation(commander.setnx(key, value));
    }

    @Override
    public Long setrange(@RedisProtocolSupport.Key final CharSequence key, final long offset,
                         final CharSequence value) throws Exception {
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
    public <T> List<T> sinter(@RedisProtocolSupport.Key final CharSequence key) throws Exception {
        return blockingInvocation(commander.sinter(key));
    }

    @Override
    public <T> List<T> sinter(@RedisProtocolSupport.Key final CharSequence key1,
                              @RedisProtocolSupport.Key final CharSequence key2) throws Exception {
        return blockingInvocation(commander.sinter(key1, key2));
    }

    @Override
    public <T> List<T> sinter(@RedisProtocolSupport.Key final CharSequence key1,
                              @RedisProtocolSupport.Key final CharSequence key2,
                              @RedisProtocolSupport.Key final CharSequence key3) throws Exception {
        return blockingInvocation(commander.sinter(key1, key2, key3));
    }

    @Override
    public <T> List<T> sinter(@RedisProtocolSupport.Key final Collection<? extends CharSequence> keys) throws Exception {
        return blockingInvocation(commander.sinter(keys));
    }

    @Override
    public Long sinterstore(@RedisProtocolSupport.Key final CharSequence destination,
                            @RedisProtocolSupport.Key final CharSequence key) throws Exception {
        return blockingInvocation(commander.sinterstore(destination, key));
    }

    @Override
    public Long sinterstore(@RedisProtocolSupport.Key final CharSequence destination,
                            @RedisProtocolSupport.Key final CharSequence key1,
                            @RedisProtocolSupport.Key final CharSequence key2) throws Exception {
        return blockingInvocation(commander.sinterstore(destination, key1, key2));
    }

    @Override
    public Long sinterstore(@RedisProtocolSupport.Key final CharSequence destination,
                            @RedisProtocolSupport.Key final CharSequence key1,
                            @RedisProtocolSupport.Key final CharSequence key2,
                            @RedisProtocolSupport.Key final CharSequence key3) throws Exception {
        return blockingInvocation(commander.sinterstore(destination, key1, key2, key3));
    }

    @Override
    public Long sinterstore(@RedisProtocolSupport.Key final CharSequence destination,
                            @RedisProtocolSupport.Key final Collection<? extends CharSequence> keys) throws Exception {
        return blockingInvocation(commander.sinterstore(destination, keys));
    }

    @Override
    public Long sismember(@RedisProtocolSupport.Key final CharSequence key,
                          final CharSequence member) throws Exception {
        return blockingInvocation(commander.sismember(key, member));
    }

    @Override
    public String slaveof(final CharSequence host, final CharSequence port) throws Exception {
        return blockingInvocation(commander.slaveof(host, port));
    }

    @Override
    public <T> List<T> slowlog(final CharSequence subcommand) throws Exception {
        return blockingInvocation(commander.slowlog(subcommand));
    }

    @Override
    public <T> List<T> slowlog(final CharSequence subcommand, @Nullable final CharSequence argument) throws Exception {
        return blockingInvocation(commander.slowlog(subcommand, argument));
    }

    @Override
    public <T> List<T> smembers(@RedisProtocolSupport.Key final CharSequence key) throws Exception {
        return blockingInvocation(commander.smembers(key));
    }

    @Override
    public Long smove(@RedisProtocolSupport.Key final CharSequence source,
                      @RedisProtocolSupport.Key final CharSequence destination,
                      final CharSequence member) throws Exception {
        return blockingInvocation(commander.smove(source, destination, member));
    }

    @Override
    public <T> List<T> sort(@RedisProtocolSupport.Key final CharSequence key) throws Exception {
        return blockingInvocation(commander.sort(key));
    }

    @Override
    public <T> List<T> sort(@RedisProtocolSupport.Key final CharSequence key, @Nullable final CharSequence byPattern,
                            @Nullable final RedisProtocolSupport.OffsetCount offsetCount,
                            final Collection<? extends CharSequence> getPatterns,
                            @Nullable final RedisProtocolSupport.SortOrder order,
                            @Nullable final RedisProtocolSupport.SortSorting sorting) throws Exception {
        return blockingInvocation(commander.sort(key, byPattern, offsetCount, getPatterns, order, sorting));
    }

    @Override
    public Long sort(@RedisProtocolSupport.Key final CharSequence key,
                     @RedisProtocolSupport.Key final CharSequence storeDestination) throws Exception {
        return blockingInvocation(commander.sort(key, storeDestination));
    }

    @Override
    public Long sort(@RedisProtocolSupport.Key final CharSequence key,
                     @RedisProtocolSupport.Key final CharSequence storeDestination,
                     @Nullable final CharSequence byPattern,
                     @Nullable final RedisProtocolSupport.OffsetCount offsetCount,
                     final Collection<? extends CharSequence> getPatterns,
                     @Nullable final RedisProtocolSupport.SortOrder order,
                     @Nullable final RedisProtocolSupport.SortSorting sorting) throws Exception {
        return blockingInvocation(
                    commander.sort(key, storeDestination, byPattern, offsetCount, getPatterns, order, sorting));
    }

    @Override
    public String spop(@RedisProtocolSupport.Key final CharSequence key) throws Exception {
        return blockingInvocation(commander.spop(key));
    }

    @Override
    public String spop(@RedisProtocolSupport.Key final CharSequence key, @Nullable final Long count) throws Exception {
        return blockingInvocation(commander.spop(key, count));
    }

    @Override
    public String srandmember(@RedisProtocolSupport.Key final CharSequence key) throws Exception {
        return blockingInvocation(commander.srandmember(key));
    }

    @Override
    public List<String> srandmember(@RedisProtocolSupport.Key final CharSequence key,
                                    final long count) throws Exception {
        return blockingInvocation(commander.srandmember(key, count));
    }

    @Override
    public Long srem(@RedisProtocolSupport.Key final CharSequence key, final CharSequence member) throws Exception {
        return blockingInvocation(commander.srem(key, member));
    }

    @Override
    public Long srem(@RedisProtocolSupport.Key final CharSequence key, final CharSequence member1,
                     final CharSequence member2) throws Exception {
        return blockingInvocation(commander.srem(key, member1, member2));
    }

    @Override
    public Long srem(@RedisProtocolSupport.Key final CharSequence key, final CharSequence member1,
                     final CharSequence member2, final CharSequence member3) throws Exception {
        return blockingInvocation(commander.srem(key, member1, member2, member3));
    }

    @Override
    public Long srem(@RedisProtocolSupport.Key final CharSequence key,
                     final Collection<? extends CharSequence> members) throws Exception {
        return blockingInvocation(commander.srem(key, members));
    }

    @Override
    public <T> List<T> sscan(@RedisProtocolSupport.Key final CharSequence key, final long cursor) throws Exception {
        return blockingInvocation(commander.sscan(key, cursor));
    }

    @Override
    public <T> List<T> sscan(@RedisProtocolSupport.Key final CharSequence key, final long cursor,
                             @Nullable final CharSequence matchPattern, @Nullable final Long count) throws Exception {
        return blockingInvocation(commander.sscan(key, cursor, matchPattern, count));
    }

    @Override
    public Long strlen(@RedisProtocolSupport.Key final CharSequence key) throws Exception {
        return blockingInvocation(commander.strlen(key));
    }

    @Override
    public BlockingPubSubRedisConnection subscribe(final CharSequence channel) throws Exception {
        return blockingInvocation(
                    commander.subscribe(channel).map(PubSubRedisConnectionToBlockingPubSubRedisConnection::new));
    }

    @Override
    public <T> List<T> sunion(@RedisProtocolSupport.Key final CharSequence key) throws Exception {
        return blockingInvocation(commander.sunion(key));
    }

    @Override
    public <T> List<T> sunion(@RedisProtocolSupport.Key final CharSequence key1,
                              @RedisProtocolSupport.Key final CharSequence key2) throws Exception {
        return blockingInvocation(commander.sunion(key1, key2));
    }

    @Override
    public <T> List<T> sunion(@RedisProtocolSupport.Key final CharSequence key1,
                              @RedisProtocolSupport.Key final CharSequence key2,
                              @RedisProtocolSupport.Key final CharSequence key3) throws Exception {
        return blockingInvocation(commander.sunion(key1, key2, key3));
    }

    @Override
    public <T> List<T> sunion(@RedisProtocolSupport.Key final Collection<? extends CharSequence> keys) throws Exception {
        return blockingInvocation(commander.sunion(keys));
    }

    @Override
    public Long sunionstore(@RedisProtocolSupport.Key final CharSequence destination,
                            @RedisProtocolSupport.Key final CharSequence key) throws Exception {
        return blockingInvocation(commander.sunionstore(destination, key));
    }

    @Override
    public Long sunionstore(@RedisProtocolSupport.Key final CharSequence destination,
                            @RedisProtocolSupport.Key final CharSequence key1,
                            @RedisProtocolSupport.Key final CharSequence key2) throws Exception {
        return blockingInvocation(commander.sunionstore(destination, key1, key2));
    }

    @Override
    public Long sunionstore(@RedisProtocolSupport.Key final CharSequence destination,
                            @RedisProtocolSupport.Key final CharSequence key1,
                            @RedisProtocolSupport.Key final CharSequence key2,
                            @RedisProtocolSupport.Key final CharSequence key3) throws Exception {
        return blockingInvocation(commander.sunionstore(destination, key1, key2, key3));
    }

    @Override
    public Long sunionstore(@RedisProtocolSupport.Key final CharSequence destination,
                            @RedisProtocolSupport.Key final Collection<? extends CharSequence> keys) throws Exception {
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
    public Long touch(@RedisProtocolSupport.Key final CharSequence key) throws Exception {
        return blockingInvocation(commander.touch(key));
    }

    @Override
    public Long touch(@RedisProtocolSupport.Key final CharSequence key1,
                      @RedisProtocolSupport.Key final CharSequence key2) throws Exception {
        return blockingInvocation(commander.touch(key1, key2));
    }

    @Override
    public Long touch(@RedisProtocolSupport.Key final CharSequence key1,
                      @RedisProtocolSupport.Key final CharSequence key2,
                      @RedisProtocolSupport.Key final CharSequence key3) throws Exception {
        return blockingInvocation(commander.touch(key1, key2, key3));
    }

    @Override
    public Long touch(@RedisProtocolSupport.Key final Collection<? extends CharSequence> keys) throws Exception {
        return blockingInvocation(commander.touch(keys));
    }

    @Override
    public Long ttl(@RedisProtocolSupport.Key final CharSequence key) throws Exception {
        return blockingInvocation(commander.ttl(key));
    }

    @Override
    public String type(@RedisProtocolSupport.Key final CharSequence key) throws Exception {
        return blockingInvocation(commander.type(key));
    }

    @Override
    public Long unlink(@RedisProtocolSupport.Key final CharSequence key) throws Exception {
        return blockingInvocation(commander.unlink(key));
    }

    @Override
    public Long unlink(@RedisProtocolSupport.Key final CharSequence key1,
                       @RedisProtocolSupport.Key final CharSequence key2) throws Exception {
        return blockingInvocation(commander.unlink(key1, key2));
    }

    @Override
    public Long unlink(@RedisProtocolSupport.Key final CharSequence key1,
                       @RedisProtocolSupport.Key final CharSequence key2,
                       @RedisProtocolSupport.Key final CharSequence key3) throws Exception {
        return blockingInvocation(commander.unlink(key1, key2, key3));
    }

    @Override
    public Long unlink(@RedisProtocolSupport.Key final Collection<? extends CharSequence> keys) throws Exception {
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
    public String watch(@RedisProtocolSupport.Key final CharSequence key) throws Exception {
        return blockingInvocation(commander.watch(key));
    }

    @Override
    public String watch(@RedisProtocolSupport.Key final CharSequence key1,
                        @RedisProtocolSupport.Key final CharSequence key2) throws Exception {
        return blockingInvocation(commander.watch(key1, key2));
    }

    @Override
    public String watch(@RedisProtocolSupport.Key final CharSequence key1,
                        @RedisProtocolSupport.Key final CharSequence key2,
                        @RedisProtocolSupport.Key final CharSequence key3) throws Exception {
        return blockingInvocation(commander.watch(key1, key2, key3));
    }

    @Override
    public String watch(@RedisProtocolSupport.Key final Collection<? extends CharSequence> keys) throws Exception {
        return blockingInvocation(commander.watch(keys));
    }

    @Override
    public String xadd(@RedisProtocolSupport.Key final CharSequence key, final CharSequence id,
                       final CharSequence field, final CharSequence value) throws Exception {
        return blockingInvocation(commander.xadd(key, id, field, value));
    }

    @Override
    public String xadd(@RedisProtocolSupport.Key final CharSequence key, final CharSequence id,
                       final CharSequence field1, final CharSequence value1, final CharSequence field2,
                       final CharSequence value2) throws Exception {
        return blockingInvocation(commander.xadd(key, id, field1, value1, field2, value2));
    }

    @Override
    public String xadd(@RedisProtocolSupport.Key final CharSequence key, final CharSequence id,
                       final CharSequence field1, final CharSequence value1, final CharSequence field2,
                       final CharSequence value2, final CharSequence field3,
                       final CharSequence value3) throws Exception {
        return blockingInvocation(commander.xadd(key, id, field1, value1, field2, value2, field3, value3));
    }

    @Override
    public String xadd(@RedisProtocolSupport.Key final CharSequence key, final CharSequence id,
                       final Collection<RedisProtocolSupport.FieldValue> fieldValues) throws Exception {
        return blockingInvocation(commander.xadd(key, id, fieldValues));
    }

    @Override
    public Long xlen(@RedisProtocolSupport.Key final CharSequence key) throws Exception {
        return blockingInvocation(commander.xlen(key));
    }

    @Override
    public <T> List<T> xpending(@RedisProtocolSupport.Key final CharSequence key,
                                final CharSequence group) throws Exception {
        return blockingInvocation(commander.xpending(key, group));
    }

    @Override
    public <T> List<T> xpending(@RedisProtocolSupport.Key final CharSequence key, final CharSequence group,
                                @Nullable final CharSequence start, @Nullable final CharSequence end,
                                @Nullable final Long count, @Nullable final CharSequence consumer) throws Exception {
        return blockingInvocation(commander.xpending(key, group, start, end, count, consumer));
    }

    @Override
    public <T> List<T> xrange(@RedisProtocolSupport.Key final CharSequence key, final CharSequence start,
                              final CharSequence end) throws Exception {
        return blockingInvocation(commander.xrange(key, start, end));
    }

    @Override
    public <T> List<T> xrange(@RedisProtocolSupport.Key final CharSequence key, final CharSequence start,
                              final CharSequence end, @Nullable final Long count) throws Exception {
        return blockingInvocation(commander.xrange(key, start, end, count));
    }

    @Override
    public <T> List<T> xread(@RedisProtocolSupport.Key final Collection<? extends CharSequence> keys,
                             final Collection<? extends CharSequence> ids) throws Exception {
        return blockingInvocation(commander.xread(keys, ids));
    }

    @Override
    public <T> List<T> xread(@Nullable final Long count, @Nullable final Long blockMilliseconds,
                             @RedisProtocolSupport.Key final Collection<? extends CharSequence> keys,
                             final Collection<? extends CharSequence> ids) throws Exception {
        return blockingInvocation(commander.xread(count, blockMilliseconds, keys, ids));
    }

    @Override
    public <T> List<T> xreadgroup(final RedisProtocolSupport.GroupConsumer groupConsumer,
                                  @RedisProtocolSupport.Key final Collection<? extends CharSequence> keys,
                                  final Collection<? extends CharSequence> ids) throws Exception {
        return blockingInvocation(commander.xreadgroup(groupConsumer, keys, ids));
    }

    @Override
    public <T> List<T> xreadgroup(final RedisProtocolSupport.GroupConsumer groupConsumer, @Nullable final Long count,
                                  @Nullable final Long blockMilliseconds,
                                  @RedisProtocolSupport.Key final Collection<? extends CharSequence> keys,
                                  final Collection<? extends CharSequence> ids) throws Exception {
        return blockingInvocation(commander.xreadgroup(groupConsumer, count, blockMilliseconds, keys, ids));
    }

    @Override
    public <T> List<T> xrevrange(@RedisProtocolSupport.Key final CharSequence key, final CharSequence end,
                                 final CharSequence start) throws Exception {
        return blockingInvocation(commander.xrevrange(key, end, start));
    }

    @Override
    public <T> List<T> xrevrange(@RedisProtocolSupport.Key final CharSequence key, final CharSequence end,
                                 final CharSequence start, @Nullable final Long count) throws Exception {
        return blockingInvocation(commander.xrevrange(key, end, start, count));
    }

    @Override
    public Long zadd(@RedisProtocolSupport.Key final CharSequence key,
                     final Collection<RedisProtocolSupport.ScoreMember> scoreMembers) throws Exception {
        return blockingInvocation(commander.zadd(key, scoreMembers));
    }

    @Override
    public Long zadd(@RedisProtocolSupport.Key final CharSequence key,
                     @Nullable final RedisProtocolSupport.ZaddCondition condition,
                     @Nullable final RedisProtocolSupport.ZaddChange change, final double score,
                     final CharSequence member) throws Exception {
        return blockingInvocation(commander.zadd(key, condition, change, score, member));
    }

    @Override
    public Long zadd(@RedisProtocolSupport.Key final CharSequence key,
                     @Nullable final RedisProtocolSupport.ZaddCondition condition,
                     @Nullable final RedisProtocolSupport.ZaddChange change, final double score1,
                     final CharSequence member1, final double score2, final CharSequence member2) throws Exception {
        return blockingInvocation(commander.zadd(key, condition, change, score1, member1, score2, member2));
    }

    @Override
    public Long zadd(@RedisProtocolSupport.Key final CharSequence key,
                     @Nullable final RedisProtocolSupport.ZaddCondition condition,
                     @Nullable final RedisProtocolSupport.ZaddChange change, final double score1,
                     final CharSequence member1, final double score2, final CharSequence member2, final double score3,
                     final CharSequence member3) throws Exception {
        return blockingInvocation(
                    commander.zadd(key, condition, change, score1, member1, score2, member2, score3, member3));
    }

    @Override
    public Long zadd(@RedisProtocolSupport.Key final CharSequence key,
                     @Nullable final RedisProtocolSupport.ZaddCondition condition,
                     @Nullable final RedisProtocolSupport.ZaddChange change,
                     final Collection<RedisProtocolSupport.ScoreMember> scoreMembers) throws Exception {
        return blockingInvocation(commander.zadd(key, condition, change, scoreMembers));
    }

    @Override
    public Double zaddIncr(@RedisProtocolSupport.Key final CharSequence key,
                           final Collection<RedisProtocolSupport.ScoreMember> scoreMembers) throws Exception {
        return blockingInvocation(commander.zaddIncr(key, scoreMembers));
    }

    @Override
    public Double zaddIncr(@RedisProtocolSupport.Key final CharSequence key,
                           @Nullable final RedisProtocolSupport.ZaddCondition condition,
                           @Nullable final RedisProtocolSupport.ZaddChange change, final double score,
                           final CharSequence member) throws Exception {
        return blockingInvocation(commander.zaddIncr(key, condition, change, score, member));
    }

    @Override
    public Double zaddIncr(@RedisProtocolSupport.Key final CharSequence key,
                           @Nullable final RedisProtocolSupport.ZaddCondition condition,
                           @Nullable final RedisProtocolSupport.ZaddChange change, final double score1,
                           final CharSequence member1, final double score2,
                           final CharSequence member2) throws Exception {
        return blockingInvocation(commander.zaddIncr(key, condition, change, score1, member1, score2, member2));
    }

    @Override
    public Double zaddIncr(@RedisProtocolSupport.Key final CharSequence key,
                           @Nullable final RedisProtocolSupport.ZaddCondition condition,
                           @Nullable final RedisProtocolSupport.ZaddChange change, final double score1,
                           final CharSequence member1, final double score2, final CharSequence member2,
                           final double score3, final CharSequence member3) throws Exception {
        return blockingInvocation(
                    commander.zaddIncr(key, condition, change, score1, member1, score2, member2, score3, member3));
    }

    @Override
    public Double zaddIncr(@RedisProtocolSupport.Key final CharSequence key,
                           @Nullable final RedisProtocolSupport.ZaddCondition condition,
                           @Nullable final RedisProtocolSupport.ZaddChange change,
                           final Collection<RedisProtocolSupport.ScoreMember> scoreMembers) throws Exception {
        return blockingInvocation(commander.zaddIncr(key, condition, change, scoreMembers));
    }

    @Override
    public Long zcard(@RedisProtocolSupport.Key final CharSequence key) throws Exception {
        return blockingInvocation(commander.zcard(key));
    }

    @Override
    public Long zcount(@RedisProtocolSupport.Key final CharSequence key, final double min,
                       final double max) throws Exception {
        return blockingInvocation(commander.zcount(key, min, max));
    }

    @Override
    public Double zincrby(@RedisProtocolSupport.Key final CharSequence key, final long increment,
                          final CharSequence member) throws Exception {
        return blockingInvocation(commander.zincrby(key, increment, member));
    }

    @Override
    public Long zinterstore(@RedisProtocolSupport.Key final CharSequence destination, final long numkeys,
                            @RedisProtocolSupport.Key final Collection<? extends CharSequence> keys) throws Exception {
        return blockingInvocation(commander.zinterstore(destination, numkeys, keys));
    }

    @Override
    public Long zinterstore(@RedisProtocolSupport.Key final CharSequence destination, final long numkeys,
                            @RedisProtocolSupport.Key final Collection<? extends CharSequence> keys,
                            final Collection<Long> weightses,
                            @Nullable final RedisProtocolSupport.ZinterstoreAggregate aggregate) throws Exception {
        return blockingInvocation(commander.zinterstore(destination, numkeys, keys, weightses, aggregate));
    }

    @Override
    public Long zlexcount(@RedisProtocolSupport.Key final CharSequence key, final CharSequence min,
                          final CharSequence max) throws Exception {
        return blockingInvocation(commander.zlexcount(key, min, max));
    }

    @Override
    public <T> List<T> zpopmax(@RedisProtocolSupport.Key final CharSequence key) throws Exception {
        return blockingInvocation(commander.zpopmax(key));
    }

    @Override
    public <T> List<T> zpopmax(@RedisProtocolSupport.Key final CharSequence key,
                               @Nullable final Long count) throws Exception {
        return blockingInvocation(commander.zpopmax(key, count));
    }

    @Override
    public <T> List<T> zpopmin(@RedisProtocolSupport.Key final CharSequence key) throws Exception {
        return blockingInvocation(commander.zpopmin(key));
    }

    @Override
    public <T> List<T> zpopmin(@RedisProtocolSupport.Key final CharSequence key,
                               @Nullable final Long count) throws Exception {
        return blockingInvocation(commander.zpopmin(key, count));
    }

    @Override
    public <T> List<T> zrange(@RedisProtocolSupport.Key final CharSequence key, final long start,
                              final long stop) throws Exception {
        return blockingInvocation(commander.zrange(key, start, stop));
    }

    @Override
    public <T> List<T> zrange(@RedisProtocolSupport.Key final CharSequence key, final long start, final long stop,
                              @Nullable final RedisProtocolSupport.ZrangeWithscores withscores) throws Exception {
        return blockingInvocation(commander.zrange(key, start, stop, withscores));
    }

    @Override
    public <T> List<T> zrangebylex(@RedisProtocolSupport.Key final CharSequence key, final CharSequence min,
                                   final CharSequence max) throws Exception {
        return blockingInvocation(commander.zrangebylex(key, min, max));
    }

    @Override
    public <T> List<T> zrangebylex(@RedisProtocolSupport.Key final CharSequence key, final CharSequence min,
                                   final CharSequence max,
                                   @Nullable final RedisProtocolSupport.OffsetCount offsetCount) throws Exception {
        return blockingInvocation(commander.zrangebylex(key, min, max, offsetCount));
    }

    @Override
    public <T> List<T> zrangebyscore(@RedisProtocolSupport.Key final CharSequence key, final double min,
                                     final double max) throws Exception {
        return blockingInvocation(commander.zrangebyscore(key, min, max));
    }

    @Override
    public <T> List<T> zrangebyscore(@RedisProtocolSupport.Key final CharSequence key, final double min,
                                     final double max,
                                     @Nullable final RedisProtocolSupport.ZrangebyscoreWithscores withscores,
                                     @Nullable final RedisProtocolSupport.OffsetCount offsetCount) throws Exception {
        return blockingInvocation(commander.zrangebyscore(key, min, max, withscores, offsetCount));
    }

    @Override
    public Long zrank(@RedisProtocolSupport.Key final CharSequence key, final CharSequence member) throws Exception {
        return blockingInvocation(commander.zrank(key, member));
    }

    @Override
    public Long zrem(@RedisProtocolSupport.Key final CharSequence key, final CharSequence member) throws Exception {
        return blockingInvocation(commander.zrem(key, member));
    }

    @Override
    public Long zrem(@RedisProtocolSupport.Key final CharSequence key, final CharSequence member1,
                     final CharSequence member2) throws Exception {
        return blockingInvocation(commander.zrem(key, member1, member2));
    }

    @Override
    public Long zrem(@RedisProtocolSupport.Key final CharSequence key, final CharSequence member1,
                     final CharSequence member2, final CharSequence member3) throws Exception {
        return blockingInvocation(commander.zrem(key, member1, member2, member3));
    }

    @Override
    public Long zrem(@RedisProtocolSupport.Key final CharSequence key,
                     final Collection<? extends CharSequence> members) throws Exception {
        return blockingInvocation(commander.zrem(key, members));
    }

    @Override
    public Long zremrangebylex(@RedisProtocolSupport.Key final CharSequence key, final CharSequence min,
                               final CharSequence max) throws Exception {
        return blockingInvocation(commander.zremrangebylex(key, min, max));
    }

    @Override
    public Long zremrangebyrank(@RedisProtocolSupport.Key final CharSequence key, final long start,
                                final long stop) throws Exception {
        return blockingInvocation(commander.zremrangebyrank(key, start, stop));
    }

    @Override
    public Long zremrangebyscore(@RedisProtocolSupport.Key final CharSequence key, final double min,
                                 final double max) throws Exception {
        return blockingInvocation(commander.zremrangebyscore(key, min, max));
    }

    @Override
    public <T> List<T> zrevrange(@RedisProtocolSupport.Key final CharSequence key, final long start,
                                 final long stop) throws Exception {
        return blockingInvocation(commander.zrevrange(key, start, stop));
    }

    @Override
    public <T> List<T> zrevrange(@RedisProtocolSupport.Key final CharSequence key, final long start, final long stop,
                                 @Nullable final RedisProtocolSupport.ZrevrangeWithscores withscores) throws Exception {
        return blockingInvocation(commander.zrevrange(key, start, stop, withscores));
    }

    @Override
    public <T> List<T> zrevrangebylex(@RedisProtocolSupport.Key final CharSequence key, final CharSequence max,
                                      final CharSequence min) throws Exception {
        return blockingInvocation(commander.zrevrangebylex(key, max, min));
    }

    @Override
    public <T> List<T> zrevrangebylex(@RedisProtocolSupport.Key final CharSequence key, final CharSequence max,
                                      final CharSequence min,
                                      @Nullable final RedisProtocolSupport.OffsetCount offsetCount) throws Exception {
        return blockingInvocation(commander.zrevrangebylex(key, max, min, offsetCount));
    }

    @Override
    public <T> List<T> zrevrangebyscore(@RedisProtocolSupport.Key final CharSequence key, final double max,
                                        final double min) throws Exception {
        return blockingInvocation(commander.zrevrangebyscore(key, max, min));
    }

    @Override
    public <T> List<T> zrevrangebyscore(@RedisProtocolSupport.Key final CharSequence key, final double max,
                                        final double min,
                                        @Nullable final RedisProtocolSupport.ZrevrangebyscoreWithscores withscores,
                                        @Nullable final RedisProtocolSupport.OffsetCount offsetCount) throws Exception {
        return blockingInvocation(commander.zrevrangebyscore(key, max, min, withscores, offsetCount));
    }

    @Override
    public Long zrevrank(@RedisProtocolSupport.Key final CharSequence key, final CharSequence member) throws Exception {
        return blockingInvocation(commander.zrevrank(key, member));
    }

    @Override
    public <T> List<T> zscan(@RedisProtocolSupport.Key final CharSequence key, final long cursor) throws Exception {
        return blockingInvocation(commander.zscan(key, cursor));
    }

    @Override
    public <T> List<T> zscan(@RedisProtocolSupport.Key final CharSequence key, final long cursor,
                             @Nullable final CharSequence matchPattern, @Nullable final Long count) throws Exception {
        return blockingInvocation(commander.zscan(key, cursor, matchPattern, count));
    }

    @Override
    public Double zscore(@RedisProtocolSupport.Key final CharSequence key, final CharSequence member) throws Exception {
        return blockingInvocation(commander.zscore(key, member));
    }

    @Override
    public Long zunionstore(@RedisProtocolSupport.Key final CharSequence destination, final long numkeys,
                            @RedisProtocolSupport.Key final Collection<? extends CharSequence> keys) throws Exception {
        return blockingInvocation(commander.zunionstore(destination, numkeys, keys));
    }

    @Override
    public Long zunionstore(@RedisProtocolSupport.Key final CharSequence destination, final long numkeys,
                            @RedisProtocolSupport.Key final Collection<? extends CharSequence> keys,
                            final Collection<Long> weightses,
                            @Nullable final RedisProtocolSupport.ZunionstoreAggregate aggregate) throws Exception {
        return blockingInvocation(commander.zunionstore(destination, numkeys, keys, weightses, aggregate));
    }

    public RedisCommander asCommanderInternal() {
        return commander;
    }
}

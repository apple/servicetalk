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

import java.util.Collection;
import java.util.List;
import java.util.Objects;
import javax.annotation.Generated;
import javax.annotation.Nullable;

import static io.servicetalk.redis.api.BlockingUtils.blockingInvocation;

@Generated({})
@SuppressWarnings("unchecked")
final class TransactedRedisCommanderToBlockingTransactedRedisCommander extends BlockingTransactedRedisCommander {

    private final TransactedRedisCommander commander;

    TransactedRedisCommanderToBlockingTransactedRedisCommander(final TransactedRedisCommander commander) {
        this.commander = Objects.requireNonNull(commander);
    }

    @Override
    public void close() throws Exception {
        blockingInvocation(commander.closeAsync());
    }

    @Override
    public String append(@RedisProtocolSupport.Key final CharSequence key, final CharSequence value) throws Exception {
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
    public String bitcount(@RedisProtocolSupport.Key final CharSequence key) throws Exception {
        return blockingInvocation(commander.bitcount(key));
    }

    @Override
    public String bitcount(@RedisProtocolSupport.Key final CharSequence key, @Nullable final Long start,
                           @Nullable final Long end) throws Exception {
        return blockingInvocation(commander.bitcount(key, start, end));
    }

    @Override
    public String bitfield(@RedisProtocolSupport.Key final CharSequence key,
                           final Collection<RedisProtocolSupport.BitfieldOperation> operations) throws Exception {
        return blockingInvocation(commander.bitfield(key, operations));
    }

    @Override
    public String bitop(final CharSequence operation, @RedisProtocolSupport.Key final CharSequence destkey,
                        @RedisProtocolSupport.Key final CharSequence key) throws Exception {
        return blockingInvocation(commander.bitop(operation, destkey, key));
    }

    @Override
    public String bitop(final CharSequence operation, @RedisProtocolSupport.Key final CharSequence destkey,
                        @RedisProtocolSupport.Key final CharSequence key1,
                        @RedisProtocolSupport.Key final CharSequence key2) throws Exception {
        return blockingInvocation(commander.bitop(operation, destkey, key1, key2));
    }

    @Override
    public String bitop(final CharSequence operation, @RedisProtocolSupport.Key final CharSequence destkey,
                        @RedisProtocolSupport.Key final CharSequence key1,
                        @RedisProtocolSupport.Key final CharSequence key2,
                        @RedisProtocolSupport.Key final CharSequence key3) throws Exception {
        return blockingInvocation(commander.bitop(operation, destkey, key1, key2, key3));
    }

    @Override
    public String bitop(final CharSequence operation, @RedisProtocolSupport.Key final CharSequence destkey,
                        @RedisProtocolSupport.Key final Collection<? extends CharSequence> keys) throws Exception {
        return blockingInvocation(commander.bitop(operation, destkey, keys));
    }

    @Override
    public String bitpos(@RedisProtocolSupport.Key final CharSequence key, final long bit) throws Exception {
        return blockingInvocation(commander.bitpos(key, bit));
    }

    @Override
    public String bitpos(@RedisProtocolSupport.Key final CharSequence key, final long bit, @Nullable final Long start,
                         @Nullable final Long end) throws Exception {
        return blockingInvocation(commander.bitpos(key, bit, start, end));
    }

    @Override
    public String blpop(@RedisProtocolSupport.Key final Collection<? extends CharSequence> keys,
                        final long timeout) throws Exception {
        return blockingInvocation(commander.blpop(keys, timeout));
    }

    @Override
    public String brpop(@RedisProtocolSupport.Key final Collection<? extends CharSequence> keys,
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
    public String bzpopmax(@RedisProtocolSupport.Key final Collection<? extends CharSequence> keys,
                           final long timeout) throws Exception {
        return blockingInvocation(commander.bzpopmax(keys, timeout));
    }

    @Override
    public String bzpopmin(@RedisProtocolSupport.Key final Collection<? extends CharSequence> keys,
                           final long timeout) throws Exception {
        return blockingInvocation(commander.bzpopmin(keys, timeout));
    }

    @Override
    public String clientKill(@Nullable final Long id, @Nullable final RedisProtocolSupport.ClientKillType type,
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
    public String clusterCountFailureReports(final CharSequence nodeId) throws Exception {
        return blockingInvocation(commander.clusterCountFailureReports(nodeId));
    }

    @Override
    public String clusterCountkeysinslot(final long slot) throws Exception {
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
    public String clusterGetkeysinslot(final long slot, final long count) throws Exception {
        return blockingInvocation(commander.clusterGetkeysinslot(slot, count));
    }

    @Override
    public String clusterInfo() throws Exception {
        return blockingInvocation(commander.clusterInfo());
    }

    @Override
    public String clusterKeyslot(final CharSequence key) throws Exception {
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
    public String clusterSlots() throws Exception {
        return blockingInvocation(commander.clusterSlots());
    }

    @Override
    public String command() throws Exception {
        return blockingInvocation(commander.command());
    }

    @Override
    public String commandCount() throws Exception {
        return blockingInvocation(commander.commandCount());
    }

    @Override
    public String commandGetkeys() throws Exception {
        return blockingInvocation(commander.commandGetkeys());
    }

    @Override
    public String commandInfo(final CharSequence commandName) throws Exception {
        return blockingInvocation(commander.commandInfo(commandName));
    }

    @Override
    public String commandInfo(final CharSequence commandName1, final CharSequence commandName2) throws Exception {
        return blockingInvocation(commander.commandInfo(commandName1, commandName2));
    }

    @Override
    public String commandInfo(final CharSequence commandName1, final CharSequence commandName2,
                              final CharSequence commandName3) throws Exception {
        return blockingInvocation(commander.commandInfo(commandName1, commandName2, commandName3));
    }

    @Override
    public String commandInfo(final Collection<? extends CharSequence> commandNames) throws Exception {
        return blockingInvocation(commander.commandInfo(commandNames));
    }

    @Override
    public String configGet(final CharSequence parameter) throws Exception {
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
    public String dbsize() throws Exception {
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
    public String decr(@RedisProtocolSupport.Key final CharSequence key) throws Exception {
        return blockingInvocation(commander.decr(key));
    }

    @Override
    public String decrby(@RedisProtocolSupport.Key final CharSequence key, final long decrement) throws Exception {
        return blockingInvocation(commander.decrby(key, decrement));
    }

    @Override
    public String del(@RedisProtocolSupport.Key final CharSequence key) throws Exception {
        return blockingInvocation(commander.del(key));
    }

    @Override
    public String del(@RedisProtocolSupport.Key final CharSequence key1,
                      @RedisProtocolSupport.Key final CharSequence key2) throws Exception {
        return blockingInvocation(commander.del(key1, key2));
    }

    @Override
    public String del(@RedisProtocolSupport.Key final CharSequence key1,
                      @RedisProtocolSupport.Key final CharSequence key2,
                      @RedisProtocolSupport.Key final CharSequence key3) throws Exception {
        return blockingInvocation(commander.del(key1, key2, key3));
    }

    @Override
    public String del(@RedisProtocolSupport.Key final Collection<? extends CharSequence> keys) throws Exception {
        return blockingInvocation(commander.del(keys));
    }

    @Override
    public String discard() throws Exception {
        return blockingInvocation(commander.discard());
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
    public String evalList(final CharSequence script, final long numkeys,
                           @RedisProtocolSupport.Key final Collection<? extends CharSequence> keys,
                           final Collection<? extends CharSequence> args) throws Exception {
        return blockingInvocation(commander.evalList(script, numkeys, keys, args));
    }

    @Override
    public String evalLong(final CharSequence script, final long numkeys,
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
    public String evalshaList(final CharSequence sha1, final long numkeys,
                              @RedisProtocolSupport.Key final Collection<? extends CharSequence> keys,
                              final Collection<? extends CharSequence> args) throws Exception {
        return blockingInvocation(commander.evalshaList(sha1, numkeys, keys, args));
    }

    @Override
    public String evalshaLong(final CharSequence sha1, final long numkeys,
                              @RedisProtocolSupport.Key final Collection<? extends CharSequence> keys,
                              final Collection<? extends CharSequence> args) throws Exception {
        return blockingInvocation(commander.evalshaLong(sha1, numkeys, keys, args));
    }

    @Override
    public <T> List<T> exec() throws Exception {
        return blockingInvocation(commander.exec());
    }

    @Override
    public String exists(@RedisProtocolSupport.Key final CharSequence key) throws Exception {
        return blockingInvocation(commander.exists(key));
    }

    @Override
    public String exists(@RedisProtocolSupport.Key final CharSequence key1,
                         @RedisProtocolSupport.Key final CharSequence key2) throws Exception {
        return blockingInvocation(commander.exists(key1, key2));
    }

    @Override
    public String exists(@RedisProtocolSupport.Key final CharSequence key1,
                         @RedisProtocolSupport.Key final CharSequence key2,
                         @RedisProtocolSupport.Key final CharSequence key3) throws Exception {
        return blockingInvocation(commander.exists(key1, key2, key3));
    }

    @Override
    public String exists(@RedisProtocolSupport.Key final Collection<? extends CharSequence> keys) throws Exception {
        return blockingInvocation(commander.exists(keys));
    }

    @Override
    public String expire(@RedisProtocolSupport.Key final CharSequence key, final long seconds) throws Exception {
        return blockingInvocation(commander.expire(key, seconds));
    }

    @Override
    public String expireat(@RedisProtocolSupport.Key final CharSequence key, final long timestamp) throws Exception {
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
    public String geoadd(@RedisProtocolSupport.Key final CharSequence key, final double longitude,
                         final double latitude, final CharSequence member) throws Exception {
        return blockingInvocation(commander.geoadd(key, longitude, latitude, member));
    }

    @Override
    public String geoadd(@RedisProtocolSupport.Key final CharSequence key, final double longitude1,
                         final double latitude1, final CharSequence member1, final double longitude2,
                         final double latitude2, final CharSequence member2) throws Exception {
        return blockingInvocation(
                    commander.geoadd(key, longitude1, latitude1, member1, longitude2, latitude2, member2));
    }

    @Override
    public String geoadd(@RedisProtocolSupport.Key final CharSequence key, final double longitude1,
                         final double latitude1, final CharSequence member1, final double longitude2,
                         final double latitude2, final CharSequence member2, final double longitude3,
                         final double latitude3, final CharSequence member3) throws Exception {
        return blockingInvocation(commander.geoadd(key, longitude1, latitude1, member1, longitude2, latitude2, member2,
                    longitude3, latitude3, member3));
    }

    @Override
    public String geoadd(@RedisProtocolSupport.Key final CharSequence key,
                         final Collection<RedisProtocolSupport.LongitudeLatitudeMember> longitudeLatitudeMembers) throws Exception {
        return blockingInvocation(commander.geoadd(key, longitudeLatitudeMembers));
    }

    @Override
    public String geodist(@RedisProtocolSupport.Key final CharSequence key, final CharSequence member1,
                          final CharSequence member2) throws Exception {
        return blockingInvocation(commander.geodist(key, member1, member2));
    }

    @Override
    public String geodist(@RedisProtocolSupport.Key final CharSequence key, final CharSequence member1,
                          final CharSequence member2, @Nullable final CharSequence unit) throws Exception {
        return blockingInvocation(commander.geodist(key, member1, member2, unit));
    }

    @Override
    public String geohash(@RedisProtocolSupport.Key final CharSequence key,
                          final CharSequence member) throws Exception {
        return blockingInvocation(commander.geohash(key, member));
    }

    @Override
    public String geohash(@RedisProtocolSupport.Key final CharSequence key, final CharSequence member1,
                          final CharSequence member2) throws Exception {
        return blockingInvocation(commander.geohash(key, member1, member2));
    }

    @Override
    public String geohash(@RedisProtocolSupport.Key final CharSequence key, final CharSequence member1,
                          final CharSequence member2, final CharSequence member3) throws Exception {
        return blockingInvocation(commander.geohash(key, member1, member2, member3));
    }

    @Override
    public String geohash(@RedisProtocolSupport.Key final CharSequence key,
                          final Collection<? extends CharSequence> members) throws Exception {
        return blockingInvocation(commander.geohash(key, members));
    }

    @Override
    public String geopos(@RedisProtocolSupport.Key final CharSequence key, final CharSequence member) throws Exception {
        return blockingInvocation(commander.geopos(key, member));
    }

    @Override
    public String geopos(@RedisProtocolSupport.Key final CharSequence key, final CharSequence member1,
                         final CharSequence member2) throws Exception {
        return blockingInvocation(commander.geopos(key, member1, member2));
    }

    @Override
    public String geopos(@RedisProtocolSupport.Key final CharSequence key, final CharSequence member1,
                         final CharSequence member2, final CharSequence member3) throws Exception {
        return blockingInvocation(commander.geopos(key, member1, member2, member3));
    }

    @Override
    public String geopos(@RedisProtocolSupport.Key final CharSequence key,
                         final Collection<? extends CharSequence> members) throws Exception {
        return blockingInvocation(commander.geopos(key, members));
    }

    @Override
    public String georadius(@RedisProtocolSupport.Key final CharSequence key, final double longitude,
                            final double latitude, final double radius,
                            final RedisProtocolSupport.GeoradiusUnit unit) throws Exception {
        return blockingInvocation(commander.georadius(key, longitude, latitude, radius, unit));
    }

    @Override
    public String georadius(@RedisProtocolSupport.Key final CharSequence key, final double longitude,
                            final double latitude, final double radius, final RedisProtocolSupport.GeoradiusUnit unit,
                            @Nullable final RedisProtocolSupport.GeoradiusWithcoord withcoord,
                            @Nullable final RedisProtocolSupport.GeoradiusWithdist withdist,
                            @Nullable final RedisProtocolSupport.GeoradiusWithhash withhash, @Nullable final Long count,
                            @Nullable final RedisProtocolSupport.GeoradiusOrder order,
                            @Nullable @RedisProtocolSupport.Key final CharSequence storeKey,
                            @Nullable @RedisProtocolSupport.Key final CharSequence storedistKey) throws Exception {
        return blockingInvocation(commander.georadius(key, longitude, latitude, radius, unit, withcoord, withdist,
                    withhash, count, order, storeKey, storedistKey));
    }

    @Override
    public String georadiusbymember(@RedisProtocolSupport.Key final CharSequence key, final CharSequence member,
                                    final double radius,
                                    final RedisProtocolSupport.GeoradiusbymemberUnit unit) throws Exception {
        return blockingInvocation(commander.georadiusbymember(key, member, radius, unit));
    }

    @Override
    public String georadiusbymember(@RedisProtocolSupport.Key final CharSequence key, final CharSequence member,
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
    public String getbit(@RedisProtocolSupport.Key final CharSequence key, final long offset) throws Exception {
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
    public String hdel(@RedisProtocolSupport.Key final CharSequence key, final CharSequence field) throws Exception {
        return blockingInvocation(commander.hdel(key, field));
    }

    @Override
    public String hdel(@RedisProtocolSupport.Key final CharSequence key, final CharSequence field1,
                       final CharSequence field2) throws Exception {
        return blockingInvocation(commander.hdel(key, field1, field2));
    }

    @Override
    public String hdel(@RedisProtocolSupport.Key final CharSequence key, final CharSequence field1,
                       final CharSequence field2, final CharSequence field3) throws Exception {
        return blockingInvocation(commander.hdel(key, field1, field2, field3));
    }

    @Override
    public String hdel(@RedisProtocolSupport.Key final CharSequence key,
                       final Collection<? extends CharSequence> fields) throws Exception {
        return blockingInvocation(commander.hdel(key, fields));
    }

    @Override
    public String hexists(@RedisProtocolSupport.Key final CharSequence key, final CharSequence field) throws Exception {
        return blockingInvocation(commander.hexists(key, field));
    }

    @Override
    public String hget(@RedisProtocolSupport.Key final CharSequence key, final CharSequence field) throws Exception {
        return blockingInvocation(commander.hget(key, field));
    }

    @Override
    public String hgetall(@RedisProtocolSupport.Key final CharSequence key) throws Exception {
        return blockingInvocation(commander.hgetall(key));
    }

    @Override
    public String hincrby(@RedisProtocolSupport.Key final CharSequence key, final CharSequence field,
                          final long increment) throws Exception {
        return blockingInvocation(commander.hincrby(key, field, increment));
    }

    @Override
    public String hincrbyfloat(@RedisProtocolSupport.Key final CharSequence key, final CharSequence field,
                               final double increment) throws Exception {
        return blockingInvocation(commander.hincrbyfloat(key, field, increment));
    }

    @Override
    public String hkeys(@RedisProtocolSupport.Key final CharSequence key) throws Exception {
        return blockingInvocation(commander.hkeys(key));
    }

    @Override
    public String hlen(@RedisProtocolSupport.Key final CharSequence key) throws Exception {
        return blockingInvocation(commander.hlen(key));
    }

    @Override
    public String hmget(@RedisProtocolSupport.Key final CharSequence key, final CharSequence field) throws Exception {
        return blockingInvocation(commander.hmget(key, field));
    }

    @Override
    public String hmget(@RedisProtocolSupport.Key final CharSequence key, final CharSequence field1,
                        final CharSequence field2) throws Exception {
        return blockingInvocation(commander.hmget(key, field1, field2));
    }

    @Override
    public String hmget(@RedisProtocolSupport.Key final CharSequence key, final CharSequence field1,
                        final CharSequence field2, final CharSequence field3) throws Exception {
        return blockingInvocation(commander.hmget(key, field1, field2, field3));
    }

    @Override
    public String hmget(@RedisProtocolSupport.Key final CharSequence key,
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
    public String hscan(@RedisProtocolSupport.Key final CharSequence key, final long cursor) throws Exception {
        return blockingInvocation(commander.hscan(key, cursor));
    }

    @Override
    public String hscan(@RedisProtocolSupport.Key final CharSequence key, final long cursor,
                        @Nullable final CharSequence matchPattern, @Nullable final Long count) throws Exception {
        return blockingInvocation(commander.hscan(key, cursor, matchPattern, count));
    }

    @Override
    public String hset(@RedisProtocolSupport.Key final CharSequence key, final CharSequence field,
                       final CharSequence value) throws Exception {
        return blockingInvocation(commander.hset(key, field, value));
    }

    @Override
    public String hsetnx(@RedisProtocolSupport.Key final CharSequence key, final CharSequence field,
                         final CharSequence value) throws Exception {
        return blockingInvocation(commander.hsetnx(key, field, value));
    }

    @Override
    public String hstrlen(@RedisProtocolSupport.Key final CharSequence key, final CharSequence field) throws Exception {
        return blockingInvocation(commander.hstrlen(key, field));
    }

    @Override
    public String hvals(@RedisProtocolSupport.Key final CharSequence key) throws Exception {
        return blockingInvocation(commander.hvals(key));
    }

    @Override
    public String incr(@RedisProtocolSupport.Key final CharSequence key) throws Exception {
        return blockingInvocation(commander.incr(key));
    }

    @Override
    public String incrby(@RedisProtocolSupport.Key final CharSequence key, final long increment) throws Exception {
        return blockingInvocation(commander.incrby(key, increment));
    }

    @Override
    public String incrbyfloat(@RedisProtocolSupport.Key final CharSequence key,
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
    public String keys(final CharSequence pattern) throws Exception {
        return blockingInvocation(commander.keys(pattern));
    }

    @Override
    public String lastsave() throws Exception {
        return blockingInvocation(commander.lastsave());
    }

    @Override
    public String lindex(@RedisProtocolSupport.Key final CharSequence key, final long index) throws Exception {
        return blockingInvocation(commander.lindex(key, index));
    }

    @Override
    public String linsert(@RedisProtocolSupport.Key final CharSequence key,
                          final RedisProtocolSupport.LinsertWhere where, final CharSequence pivot,
                          final CharSequence value) throws Exception {
        return blockingInvocation(commander.linsert(key, where, pivot, value));
    }

    @Override
    public String llen(@RedisProtocolSupport.Key final CharSequence key) throws Exception {
        return blockingInvocation(commander.llen(key));
    }

    @Override
    public String lpop(@RedisProtocolSupport.Key final CharSequence key) throws Exception {
        return blockingInvocation(commander.lpop(key));
    }

    @Override
    public String lpush(@RedisProtocolSupport.Key final CharSequence key, final CharSequence value) throws Exception {
        return blockingInvocation(commander.lpush(key, value));
    }

    @Override
    public String lpush(@RedisProtocolSupport.Key final CharSequence key, final CharSequence value1,
                        final CharSequence value2) throws Exception {
        return blockingInvocation(commander.lpush(key, value1, value2));
    }

    @Override
    public String lpush(@RedisProtocolSupport.Key final CharSequence key, final CharSequence value1,
                        final CharSequence value2, final CharSequence value3) throws Exception {
        return blockingInvocation(commander.lpush(key, value1, value2, value3));
    }

    @Override
    public String lpush(@RedisProtocolSupport.Key final CharSequence key,
                        final Collection<? extends CharSequence> values) throws Exception {
        return blockingInvocation(commander.lpush(key, values));
    }

    @Override
    public String lpushx(@RedisProtocolSupport.Key final CharSequence key, final CharSequence value) throws Exception {
        return blockingInvocation(commander.lpushx(key, value));
    }

    @Override
    public String lrange(@RedisProtocolSupport.Key final CharSequence key, final long start,
                         final long stop) throws Exception {
        return blockingInvocation(commander.lrange(key, start, stop));
    }

    @Override
    public String lrem(@RedisProtocolSupport.Key final CharSequence key, final long count,
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
    public String memoryHelp() throws Exception {
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
    public String memoryStats() throws Exception {
        return blockingInvocation(commander.memoryStats());
    }

    @Override
    public String memoryUsage(@RedisProtocolSupport.Key final CharSequence key) throws Exception {
        return blockingInvocation(commander.memoryUsage(key));
    }

    @Override
    public String memoryUsage(@RedisProtocolSupport.Key final CharSequence key,
                              @Nullable final Long samplesCount) throws Exception {
        return blockingInvocation(commander.memoryUsage(key, samplesCount));
    }

    @Override
    public String mget(@RedisProtocolSupport.Key final CharSequence key) throws Exception {
        return blockingInvocation(commander.mget(key));
    }

    @Override
    public String mget(@RedisProtocolSupport.Key final CharSequence key1,
                       @RedisProtocolSupport.Key final CharSequence key2) throws Exception {
        return blockingInvocation(commander.mget(key1, key2));
    }

    @Override
    public String mget(@RedisProtocolSupport.Key final CharSequence key1,
                       @RedisProtocolSupport.Key final CharSequence key2,
                       @RedisProtocolSupport.Key final CharSequence key3) throws Exception {
        return blockingInvocation(commander.mget(key1, key2, key3));
    }

    @Override
    public String mget(@RedisProtocolSupport.Key final Collection<? extends CharSequence> keys) throws Exception {
        return blockingInvocation(commander.mget(keys));
    }

    @Override
    public String move(@RedisProtocolSupport.Key final CharSequence key, final long db) throws Exception {
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
    public String msetnx(@RedisProtocolSupport.Key final CharSequence key, final CharSequence value) throws Exception {
        return blockingInvocation(commander.msetnx(key, value));
    }

    @Override
    public String msetnx(@RedisProtocolSupport.Key final CharSequence key1, final CharSequence value1,
                         @RedisProtocolSupport.Key final CharSequence key2,
                         final CharSequence value2) throws Exception {
        return blockingInvocation(commander.msetnx(key1, value1, key2, value2));
    }

    @Override
    public String msetnx(@RedisProtocolSupport.Key final CharSequence key1, final CharSequence value1,
                         @RedisProtocolSupport.Key final CharSequence key2, final CharSequence value2,
                         @RedisProtocolSupport.Key final CharSequence key3,
                         final CharSequence value3) throws Exception {
        return blockingInvocation(commander.msetnx(key1, value1, key2, value2, key3, value3));
    }

    @Override
    public String msetnx(final Collection<RedisProtocolSupport.KeyValue> keyValues) throws Exception {
        return blockingInvocation(commander.msetnx(keyValues));
    }

    @Override
    public String objectEncoding(@RedisProtocolSupport.Key final CharSequence key) throws Exception {
        return blockingInvocation(commander.objectEncoding(key));
    }

    @Override
    public String objectFreq(@RedisProtocolSupport.Key final CharSequence key) throws Exception {
        return blockingInvocation(commander.objectFreq(key));
    }

    @Override
    public String objectHelp() throws Exception {
        return blockingInvocation(commander.objectHelp());
    }

    @Override
    public String objectIdletime(@RedisProtocolSupport.Key final CharSequence key) throws Exception {
        return blockingInvocation(commander.objectIdletime(key));
    }

    @Override
    public String objectRefcount(@RedisProtocolSupport.Key final CharSequence key) throws Exception {
        return blockingInvocation(commander.objectRefcount(key));
    }

    @Override
    public String persist(@RedisProtocolSupport.Key final CharSequence key) throws Exception {
        return blockingInvocation(commander.persist(key));
    }

    @Override
    public String pexpire(@RedisProtocolSupport.Key final CharSequence key, final long milliseconds) throws Exception {
        return blockingInvocation(commander.pexpire(key, milliseconds));
    }

    @Override
    public String pexpireat(@RedisProtocolSupport.Key final CharSequence key,
                            final long millisecondsTimestamp) throws Exception {
        return blockingInvocation(commander.pexpireat(key, millisecondsTimestamp));
    }

    @Override
    public String pfadd(@RedisProtocolSupport.Key final CharSequence key, final CharSequence element) throws Exception {
        return blockingInvocation(commander.pfadd(key, element));
    }

    @Override
    public String pfadd(@RedisProtocolSupport.Key final CharSequence key, final CharSequence element1,
                        final CharSequence element2) throws Exception {
        return blockingInvocation(commander.pfadd(key, element1, element2));
    }

    @Override
    public String pfadd(@RedisProtocolSupport.Key final CharSequence key, final CharSequence element1,
                        final CharSequence element2, final CharSequence element3) throws Exception {
        return blockingInvocation(commander.pfadd(key, element1, element2, element3));
    }

    @Override
    public String pfadd(@RedisProtocolSupport.Key final CharSequence key,
                        final Collection<? extends CharSequence> elements) throws Exception {
        return blockingInvocation(commander.pfadd(key, elements));
    }

    @Override
    public String pfcount(@RedisProtocolSupport.Key final CharSequence key) throws Exception {
        return blockingInvocation(commander.pfcount(key));
    }

    @Override
    public String pfcount(@RedisProtocolSupport.Key final CharSequence key1,
                          @RedisProtocolSupport.Key final CharSequence key2) throws Exception {
        return blockingInvocation(commander.pfcount(key1, key2));
    }

    @Override
    public String pfcount(@RedisProtocolSupport.Key final CharSequence key1,
                          @RedisProtocolSupport.Key final CharSequence key2,
                          @RedisProtocolSupport.Key final CharSequence key3) throws Exception {
        return blockingInvocation(commander.pfcount(key1, key2, key3));
    }

    @Override
    public String pfcount(@RedisProtocolSupport.Key final Collection<? extends CharSequence> keys) throws Exception {
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
    public String pttl(@RedisProtocolSupport.Key final CharSequence key) throws Exception {
        return blockingInvocation(commander.pttl(key));
    }

    @Override
    public String publish(final CharSequence channel, final CharSequence message) throws Exception {
        return blockingInvocation(commander.publish(channel, message));
    }

    @Override
    public String pubsubChannels() throws Exception {
        return blockingInvocation(commander.pubsubChannels());
    }

    @Override
    public String pubsubChannels(@Nullable final CharSequence pattern) throws Exception {
        return blockingInvocation(commander.pubsubChannels(pattern));
    }

    @Override
    public String pubsubChannels(@Nullable final CharSequence pattern1,
                                 @Nullable final CharSequence pattern2) throws Exception {
        return blockingInvocation(commander.pubsubChannels(pattern1, pattern2));
    }

    @Override
    public String pubsubChannels(@Nullable final CharSequence pattern1, @Nullable final CharSequence pattern2,
                                 @Nullable final CharSequence pattern3) throws Exception {
        return blockingInvocation(commander.pubsubChannels(pattern1, pattern2, pattern3));
    }

    @Override
    public String pubsubChannels(final Collection<? extends CharSequence> patterns) throws Exception {
        return blockingInvocation(commander.pubsubChannels(patterns));
    }

    @Override
    public String pubsubNumsub() throws Exception {
        return blockingInvocation(commander.pubsubNumsub());
    }

    @Override
    public String pubsubNumsub(@Nullable final CharSequence channel) throws Exception {
        return blockingInvocation(commander.pubsubNumsub(channel));
    }

    @Override
    public String pubsubNumsub(@Nullable final CharSequence channel1,
                               @Nullable final CharSequence channel2) throws Exception {
        return blockingInvocation(commander.pubsubNumsub(channel1, channel2));
    }

    @Override
    public String pubsubNumsub(@Nullable final CharSequence channel1, @Nullable final CharSequence channel2,
                               @Nullable final CharSequence channel3) throws Exception {
        return blockingInvocation(commander.pubsubNumsub(channel1, channel2, channel3));
    }

    @Override
    public String pubsubNumsub(final Collection<? extends CharSequence> channels) throws Exception {
        return blockingInvocation(commander.pubsubNumsub(channels));
    }

    @Override
    public String pubsubNumpat() throws Exception {
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
    public String renamenx(@RedisProtocolSupport.Key final CharSequence key,
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
    public String role() throws Exception {
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
    public String rpush(@RedisProtocolSupport.Key final CharSequence key, final CharSequence value) throws Exception {
        return blockingInvocation(commander.rpush(key, value));
    }

    @Override
    public String rpush(@RedisProtocolSupport.Key final CharSequence key, final CharSequence value1,
                        final CharSequence value2) throws Exception {
        return blockingInvocation(commander.rpush(key, value1, value2));
    }

    @Override
    public String rpush(@RedisProtocolSupport.Key final CharSequence key, final CharSequence value1,
                        final CharSequence value2, final CharSequence value3) throws Exception {
        return blockingInvocation(commander.rpush(key, value1, value2, value3));
    }

    @Override
    public String rpush(@RedisProtocolSupport.Key final CharSequence key,
                        final Collection<? extends CharSequence> values) throws Exception {
        return blockingInvocation(commander.rpush(key, values));
    }

    @Override
    public String rpushx(@RedisProtocolSupport.Key final CharSequence key, final CharSequence value) throws Exception {
        return blockingInvocation(commander.rpushx(key, value));
    }

    @Override
    public String sadd(@RedisProtocolSupport.Key final CharSequence key, final CharSequence member) throws Exception {
        return blockingInvocation(commander.sadd(key, member));
    }

    @Override
    public String sadd(@RedisProtocolSupport.Key final CharSequence key, final CharSequence member1,
                       final CharSequence member2) throws Exception {
        return blockingInvocation(commander.sadd(key, member1, member2));
    }

    @Override
    public String sadd(@RedisProtocolSupport.Key final CharSequence key, final CharSequence member1,
                       final CharSequence member2, final CharSequence member3) throws Exception {
        return blockingInvocation(commander.sadd(key, member1, member2, member3));
    }

    @Override
    public String sadd(@RedisProtocolSupport.Key final CharSequence key,
                       final Collection<? extends CharSequence> members) throws Exception {
        return blockingInvocation(commander.sadd(key, members));
    }

    @Override
    public String save() throws Exception {
        return blockingInvocation(commander.save());
    }

    @Override
    public String scan(final long cursor) throws Exception {
        return blockingInvocation(commander.scan(cursor));
    }

    @Override
    public String scan(final long cursor, @Nullable final CharSequence matchPattern,
                       @Nullable final Long count) throws Exception {
        return blockingInvocation(commander.scan(cursor, matchPattern, count));
    }

    @Override
    public String scard(@RedisProtocolSupport.Key final CharSequence key) throws Exception {
        return blockingInvocation(commander.scard(key));
    }

    @Override
    public String scriptDebug(final RedisProtocolSupport.ScriptDebugMode mode) throws Exception {
        return blockingInvocation(commander.scriptDebug(mode));
    }

    @Override
    public String scriptExists(final CharSequence sha1) throws Exception {
        return blockingInvocation(commander.scriptExists(sha1));
    }

    @Override
    public String scriptExists(final CharSequence sha11, final CharSequence sha12) throws Exception {
        return blockingInvocation(commander.scriptExists(sha11, sha12));
    }

    @Override
    public String scriptExists(final CharSequence sha11, final CharSequence sha12,
                               final CharSequence sha13) throws Exception {
        return blockingInvocation(commander.scriptExists(sha11, sha12, sha13));
    }

    @Override
    public String scriptExists(final Collection<? extends CharSequence> sha1s) throws Exception {
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
    public String sdiff(@RedisProtocolSupport.Key final CharSequence firstkey) throws Exception {
        return blockingInvocation(commander.sdiff(firstkey));
    }

    @Override
    public String sdiff(@RedisProtocolSupport.Key final CharSequence firstkey,
                        @Nullable @RedisProtocolSupport.Key final CharSequence otherkey) throws Exception {
        return blockingInvocation(commander.sdiff(firstkey, otherkey));
    }

    @Override
    public String sdiff(@RedisProtocolSupport.Key final CharSequence firstkey,
                        @Nullable @RedisProtocolSupport.Key final CharSequence otherkey1,
                        @Nullable @RedisProtocolSupport.Key final CharSequence otherkey2) throws Exception {
        return blockingInvocation(commander.sdiff(firstkey, otherkey1, otherkey2));
    }

    @Override
    public String sdiff(@RedisProtocolSupport.Key final CharSequence firstkey,
                        @Nullable @RedisProtocolSupport.Key final CharSequence otherkey1,
                        @Nullable @RedisProtocolSupport.Key final CharSequence otherkey2,
                        @Nullable @RedisProtocolSupport.Key final CharSequence otherkey3) throws Exception {
        return blockingInvocation(commander.sdiff(firstkey, otherkey1, otherkey2, otherkey3));
    }

    @Override
    public String sdiff(@RedisProtocolSupport.Key final CharSequence firstkey,
                        @RedisProtocolSupport.Key final Collection<? extends CharSequence> otherkeys) throws Exception {
        return blockingInvocation(commander.sdiff(firstkey, otherkeys));
    }

    @Override
    public String sdiffstore(@RedisProtocolSupport.Key final CharSequence destination,
                             @RedisProtocolSupport.Key final CharSequence firstkey) throws Exception {
        return blockingInvocation(commander.sdiffstore(destination, firstkey));
    }

    @Override
    public String sdiffstore(@RedisProtocolSupport.Key final CharSequence destination,
                             @RedisProtocolSupport.Key final CharSequence firstkey,
                             @Nullable @RedisProtocolSupport.Key final CharSequence otherkey) throws Exception {
        return blockingInvocation(commander.sdiffstore(destination, firstkey, otherkey));
    }

    @Override
    public String sdiffstore(@RedisProtocolSupport.Key final CharSequence destination,
                             @RedisProtocolSupport.Key final CharSequence firstkey,
                             @Nullable @RedisProtocolSupport.Key final CharSequence otherkey1,
                             @Nullable @RedisProtocolSupport.Key final CharSequence otherkey2) throws Exception {
        return blockingInvocation(commander.sdiffstore(destination, firstkey, otherkey1, otherkey2));
    }

    @Override
    public String sdiffstore(@RedisProtocolSupport.Key final CharSequence destination,
                             @RedisProtocolSupport.Key final CharSequence firstkey,
                             @Nullable @RedisProtocolSupport.Key final CharSequence otherkey1,
                             @Nullable @RedisProtocolSupport.Key final CharSequence otherkey2,
                             @Nullable @RedisProtocolSupport.Key final CharSequence otherkey3) throws Exception {
        return blockingInvocation(commander.sdiffstore(destination, firstkey, otherkey1, otherkey2, otherkey3));
    }

    @Override
    public String sdiffstore(@RedisProtocolSupport.Key final CharSequence destination,
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
    public String setbit(@RedisProtocolSupport.Key final CharSequence key, final long offset,
                         final CharSequence value) throws Exception {
        return blockingInvocation(commander.setbit(key, offset, value));
    }

    @Override
    public String setex(@RedisProtocolSupport.Key final CharSequence key, final long seconds,
                        final CharSequence value) throws Exception {
        return blockingInvocation(commander.setex(key, seconds, value));
    }

    @Override
    public String setnx(@RedisProtocolSupport.Key final CharSequence key, final CharSequence value) throws Exception {
        return blockingInvocation(commander.setnx(key, value));
    }

    @Override
    public String setrange(@RedisProtocolSupport.Key final CharSequence key, final long offset,
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
    public String sinter(@RedisProtocolSupport.Key final CharSequence key) throws Exception {
        return blockingInvocation(commander.sinter(key));
    }

    @Override
    public String sinter(@RedisProtocolSupport.Key final CharSequence key1,
                         @RedisProtocolSupport.Key final CharSequence key2) throws Exception {
        return blockingInvocation(commander.sinter(key1, key2));
    }

    @Override
    public String sinter(@RedisProtocolSupport.Key final CharSequence key1,
                         @RedisProtocolSupport.Key final CharSequence key2,
                         @RedisProtocolSupport.Key final CharSequence key3) throws Exception {
        return blockingInvocation(commander.sinter(key1, key2, key3));
    }

    @Override
    public String sinter(@RedisProtocolSupport.Key final Collection<? extends CharSequence> keys) throws Exception {
        return blockingInvocation(commander.sinter(keys));
    }

    @Override
    public String sinterstore(@RedisProtocolSupport.Key final CharSequence destination,
                              @RedisProtocolSupport.Key final CharSequence key) throws Exception {
        return blockingInvocation(commander.sinterstore(destination, key));
    }

    @Override
    public String sinterstore(@RedisProtocolSupport.Key final CharSequence destination,
                              @RedisProtocolSupport.Key final CharSequence key1,
                              @RedisProtocolSupport.Key final CharSequence key2) throws Exception {
        return blockingInvocation(commander.sinterstore(destination, key1, key2));
    }

    @Override
    public String sinterstore(@RedisProtocolSupport.Key final CharSequence destination,
                              @RedisProtocolSupport.Key final CharSequence key1,
                              @RedisProtocolSupport.Key final CharSequence key2,
                              @RedisProtocolSupport.Key final CharSequence key3) throws Exception {
        return blockingInvocation(commander.sinterstore(destination, key1, key2, key3));
    }

    @Override
    public String sinterstore(@RedisProtocolSupport.Key final CharSequence destination,
                              @RedisProtocolSupport.Key final Collection<? extends CharSequence> keys) throws Exception {
        return blockingInvocation(commander.sinterstore(destination, keys));
    }

    @Override
    public String sismember(@RedisProtocolSupport.Key final CharSequence key,
                            final CharSequence member) throws Exception {
        return blockingInvocation(commander.sismember(key, member));
    }

    @Override
    public String slaveof(final CharSequence host, final CharSequence port) throws Exception {
        return blockingInvocation(commander.slaveof(host, port));
    }

    @Override
    public String slowlog(final CharSequence subcommand) throws Exception {
        return blockingInvocation(commander.slowlog(subcommand));
    }

    @Override
    public String slowlog(final CharSequence subcommand, @Nullable final CharSequence argument) throws Exception {
        return blockingInvocation(commander.slowlog(subcommand, argument));
    }

    @Override
    public String smembers(@RedisProtocolSupport.Key final CharSequence key) throws Exception {
        return blockingInvocation(commander.smembers(key));
    }

    @Override
    public String smove(@RedisProtocolSupport.Key final CharSequence source,
                        @RedisProtocolSupport.Key final CharSequence destination,
                        final CharSequence member) throws Exception {
        return blockingInvocation(commander.smove(source, destination, member));
    }

    @Override
    public String sort(@RedisProtocolSupport.Key final CharSequence key) throws Exception {
        return blockingInvocation(commander.sort(key));
    }

    @Override
    public String sort(@RedisProtocolSupport.Key final CharSequence key, @Nullable final CharSequence byPattern,
                       @Nullable final RedisProtocolSupport.OffsetCount offsetCount,
                       final Collection<? extends CharSequence> getPatterns,
                       @Nullable final RedisProtocolSupport.SortOrder order,
                       @Nullable final RedisProtocolSupport.SortSorting sorting) throws Exception {
        return blockingInvocation(commander.sort(key, byPattern, offsetCount, getPatterns, order, sorting));
    }

    @Override
    public String sort(@RedisProtocolSupport.Key final CharSequence key,
                       @RedisProtocolSupport.Key final CharSequence storeDestination) throws Exception {
        return blockingInvocation(commander.sort(key, storeDestination));
    }

    @Override
    public String sort(@RedisProtocolSupport.Key final CharSequence key,
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
    public String srandmember(@RedisProtocolSupport.Key final CharSequence key, final long count) throws Exception {
        return blockingInvocation(commander.srandmember(key, count));
    }

    @Override
    public String srem(@RedisProtocolSupport.Key final CharSequence key, final CharSequence member) throws Exception {
        return blockingInvocation(commander.srem(key, member));
    }

    @Override
    public String srem(@RedisProtocolSupport.Key final CharSequence key, final CharSequence member1,
                       final CharSequence member2) throws Exception {
        return blockingInvocation(commander.srem(key, member1, member2));
    }

    @Override
    public String srem(@RedisProtocolSupport.Key final CharSequence key, final CharSequence member1,
                       final CharSequence member2, final CharSequence member3) throws Exception {
        return blockingInvocation(commander.srem(key, member1, member2, member3));
    }

    @Override
    public String srem(@RedisProtocolSupport.Key final CharSequence key,
                       final Collection<? extends CharSequence> members) throws Exception {
        return blockingInvocation(commander.srem(key, members));
    }

    @Override
    public String sscan(@RedisProtocolSupport.Key final CharSequence key, final long cursor) throws Exception {
        return blockingInvocation(commander.sscan(key, cursor));
    }

    @Override
    public String sscan(@RedisProtocolSupport.Key final CharSequence key, final long cursor,
                        @Nullable final CharSequence matchPattern, @Nullable final Long count) throws Exception {
        return blockingInvocation(commander.sscan(key, cursor, matchPattern, count));
    }

    @Override
    public String strlen(@RedisProtocolSupport.Key final CharSequence key) throws Exception {
        return blockingInvocation(commander.strlen(key));
    }

    @Override
    public String sunion(@RedisProtocolSupport.Key final CharSequence key) throws Exception {
        return blockingInvocation(commander.sunion(key));
    }

    @Override
    public String sunion(@RedisProtocolSupport.Key final CharSequence key1,
                         @RedisProtocolSupport.Key final CharSequence key2) throws Exception {
        return blockingInvocation(commander.sunion(key1, key2));
    }

    @Override
    public String sunion(@RedisProtocolSupport.Key final CharSequence key1,
                         @RedisProtocolSupport.Key final CharSequence key2,
                         @RedisProtocolSupport.Key final CharSequence key3) throws Exception {
        return blockingInvocation(commander.sunion(key1, key2, key3));
    }

    @Override
    public String sunion(@RedisProtocolSupport.Key final Collection<? extends CharSequence> keys) throws Exception {
        return blockingInvocation(commander.sunion(keys));
    }

    @Override
    public String sunionstore(@RedisProtocolSupport.Key final CharSequence destination,
                              @RedisProtocolSupport.Key final CharSequence key) throws Exception {
        return blockingInvocation(commander.sunionstore(destination, key));
    }

    @Override
    public String sunionstore(@RedisProtocolSupport.Key final CharSequence destination,
                              @RedisProtocolSupport.Key final CharSequence key1,
                              @RedisProtocolSupport.Key final CharSequence key2) throws Exception {
        return blockingInvocation(commander.sunionstore(destination, key1, key2));
    }

    @Override
    public String sunionstore(@RedisProtocolSupport.Key final CharSequence destination,
                              @RedisProtocolSupport.Key final CharSequence key1,
                              @RedisProtocolSupport.Key final CharSequence key2,
                              @RedisProtocolSupport.Key final CharSequence key3) throws Exception {
        return blockingInvocation(commander.sunionstore(destination, key1, key2, key3));
    }

    @Override
    public String sunionstore(@RedisProtocolSupport.Key final CharSequence destination,
                              @RedisProtocolSupport.Key final Collection<? extends CharSequence> keys) throws Exception {
        return blockingInvocation(commander.sunionstore(destination, keys));
    }

    @Override
    public String swapdb(final long index, final long index1) throws Exception {
        return blockingInvocation(commander.swapdb(index, index1));
    }

    @Override
    public String time() throws Exception {
        return blockingInvocation(commander.time());
    }

    @Override
    public String touch(@RedisProtocolSupport.Key final CharSequence key) throws Exception {
        return blockingInvocation(commander.touch(key));
    }

    @Override
    public String touch(@RedisProtocolSupport.Key final CharSequence key1,
                        @RedisProtocolSupport.Key final CharSequence key2) throws Exception {
        return blockingInvocation(commander.touch(key1, key2));
    }

    @Override
    public String touch(@RedisProtocolSupport.Key final CharSequence key1,
                        @RedisProtocolSupport.Key final CharSequence key2,
                        @RedisProtocolSupport.Key final CharSequence key3) throws Exception {
        return blockingInvocation(commander.touch(key1, key2, key3));
    }

    @Override
    public String touch(@RedisProtocolSupport.Key final Collection<? extends CharSequence> keys) throws Exception {
        return blockingInvocation(commander.touch(keys));
    }

    @Override
    public String ttl(@RedisProtocolSupport.Key final CharSequence key) throws Exception {
        return blockingInvocation(commander.ttl(key));
    }

    @Override
    public String type(@RedisProtocolSupport.Key final CharSequence key) throws Exception {
        return blockingInvocation(commander.type(key));
    }

    @Override
    public String unlink(@RedisProtocolSupport.Key final CharSequence key) throws Exception {
        return blockingInvocation(commander.unlink(key));
    }

    @Override
    public String unlink(@RedisProtocolSupport.Key final CharSequence key1,
                         @RedisProtocolSupport.Key final CharSequence key2) throws Exception {
        return blockingInvocation(commander.unlink(key1, key2));
    }

    @Override
    public String unlink(@RedisProtocolSupport.Key final CharSequence key1,
                         @RedisProtocolSupport.Key final CharSequence key2,
                         @RedisProtocolSupport.Key final CharSequence key3) throws Exception {
        return blockingInvocation(commander.unlink(key1, key2, key3));
    }

    @Override
    public String unlink(@RedisProtocolSupport.Key final Collection<? extends CharSequence> keys) throws Exception {
        return blockingInvocation(commander.unlink(keys));
    }

    @Override
    public String unwatch() throws Exception {
        return blockingInvocation(commander.unwatch());
    }

    @Override
    public String wait(final long numslaves, final long timeout) throws Exception {
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
    public String xlen(@RedisProtocolSupport.Key final CharSequence key) throws Exception {
        return blockingInvocation(commander.xlen(key));
    }

    @Override
    public String xpending(@RedisProtocolSupport.Key final CharSequence key,
                           final CharSequence group) throws Exception {
        return blockingInvocation(commander.xpending(key, group));
    }

    @Override
    public String xpending(@RedisProtocolSupport.Key final CharSequence key, final CharSequence group,
                           @Nullable final CharSequence start, @Nullable final CharSequence end,
                           @Nullable final Long count, @Nullable final CharSequence consumer) throws Exception {
        return blockingInvocation(commander.xpending(key, group, start, end, count, consumer));
    }

    @Override
    public String xrange(@RedisProtocolSupport.Key final CharSequence key, final CharSequence start,
                         final CharSequence end) throws Exception {
        return blockingInvocation(commander.xrange(key, start, end));
    }

    @Override
    public String xrange(@RedisProtocolSupport.Key final CharSequence key, final CharSequence start,
                         final CharSequence end, @Nullable final Long count) throws Exception {
        return blockingInvocation(commander.xrange(key, start, end, count));
    }

    @Override
    public String xread(@RedisProtocolSupport.Key final Collection<? extends CharSequence> keys,
                        final Collection<? extends CharSequence> ids) throws Exception {
        return blockingInvocation(commander.xread(keys, ids));
    }

    @Override
    public String xread(@Nullable final Long count, @Nullable final Long blockMilliseconds,
                        @RedisProtocolSupport.Key final Collection<? extends CharSequence> keys,
                        final Collection<? extends CharSequence> ids) throws Exception {
        return blockingInvocation(commander.xread(count, blockMilliseconds, keys, ids));
    }

    @Override
    public String xreadgroup(final RedisProtocolSupport.GroupConsumer groupConsumer,
                             @RedisProtocolSupport.Key final Collection<? extends CharSequence> keys,
                             final Collection<? extends CharSequence> ids) throws Exception {
        return blockingInvocation(commander.xreadgroup(groupConsumer, keys, ids));
    }

    @Override
    public String xreadgroup(final RedisProtocolSupport.GroupConsumer groupConsumer, @Nullable final Long count,
                             @Nullable final Long blockMilliseconds,
                             @RedisProtocolSupport.Key final Collection<? extends CharSequence> keys,
                             final Collection<? extends CharSequence> ids) throws Exception {
        return blockingInvocation(commander.xreadgroup(groupConsumer, count, blockMilliseconds, keys, ids));
    }

    @Override
    public String xrevrange(@RedisProtocolSupport.Key final CharSequence key, final CharSequence end,
                            final CharSequence start) throws Exception {
        return blockingInvocation(commander.xrevrange(key, end, start));
    }

    @Override
    public String xrevrange(@RedisProtocolSupport.Key final CharSequence key, final CharSequence end,
                            final CharSequence start, @Nullable final Long count) throws Exception {
        return blockingInvocation(commander.xrevrange(key, end, start, count));
    }

    @Override
    public String zadd(@RedisProtocolSupport.Key final CharSequence key,
                       final Collection<RedisProtocolSupport.ScoreMember> scoreMembers) throws Exception {
        return blockingInvocation(commander.zadd(key, scoreMembers));
    }

    @Override
    public String zadd(@RedisProtocolSupport.Key final CharSequence key,
                       @Nullable final RedisProtocolSupport.ZaddCondition condition,
                       @Nullable final RedisProtocolSupport.ZaddChange change, final double score,
                       final CharSequence member) throws Exception {
        return blockingInvocation(commander.zadd(key, condition, change, score, member));
    }

    @Override
    public String zadd(@RedisProtocolSupport.Key final CharSequence key,
                       @Nullable final RedisProtocolSupport.ZaddCondition condition,
                       @Nullable final RedisProtocolSupport.ZaddChange change, final double score1,
                       final CharSequence member1, final double score2, final CharSequence member2) throws Exception {
        return blockingInvocation(commander.zadd(key, condition, change, score1, member1, score2, member2));
    }

    @Override
    public String zadd(@RedisProtocolSupport.Key final CharSequence key,
                       @Nullable final RedisProtocolSupport.ZaddCondition condition,
                       @Nullable final RedisProtocolSupport.ZaddChange change, final double score1,
                       final CharSequence member1, final double score2, final CharSequence member2, final double score3,
                       final CharSequence member3) throws Exception {
        return blockingInvocation(
                    commander.zadd(key, condition, change, score1, member1, score2, member2, score3, member3));
    }

    @Override
    public String zadd(@RedisProtocolSupport.Key final CharSequence key,
                       @Nullable final RedisProtocolSupport.ZaddCondition condition,
                       @Nullable final RedisProtocolSupport.ZaddChange change,
                       final Collection<RedisProtocolSupport.ScoreMember> scoreMembers) throws Exception {
        return blockingInvocation(commander.zadd(key, condition, change, scoreMembers));
    }

    @Override
    public String zaddIncr(@RedisProtocolSupport.Key final CharSequence key,
                           final Collection<RedisProtocolSupport.ScoreMember> scoreMembers) throws Exception {
        return blockingInvocation(commander.zaddIncr(key, scoreMembers));
    }

    @Override
    public String zaddIncr(@RedisProtocolSupport.Key final CharSequence key,
                           @Nullable final RedisProtocolSupport.ZaddCondition condition,
                           @Nullable final RedisProtocolSupport.ZaddChange change, final double score,
                           final CharSequence member) throws Exception {
        return blockingInvocation(commander.zaddIncr(key, condition, change, score, member));
    }

    @Override
    public String zaddIncr(@RedisProtocolSupport.Key final CharSequence key,
                           @Nullable final RedisProtocolSupport.ZaddCondition condition,
                           @Nullable final RedisProtocolSupport.ZaddChange change, final double score1,
                           final CharSequence member1, final double score2,
                           final CharSequence member2) throws Exception {
        return blockingInvocation(commander.zaddIncr(key, condition, change, score1, member1, score2, member2));
    }

    @Override
    public String zaddIncr(@RedisProtocolSupport.Key final CharSequence key,
                           @Nullable final RedisProtocolSupport.ZaddCondition condition,
                           @Nullable final RedisProtocolSupport.ZaddChange change, final double score1,
                           final CharSequence member1, final double score2, final CharSequence member2,
                           final double score3, final CharSequence member3) throws Exception {
        return blockingInvocation(
                    commander.zaddIncr(key, condition, change, score1, member1, score2, member2, score3, member3));
    }

    @Override
    public String zaddIncr(@RedisProtocolSupport.Key final CharSequence key,
                           @Nullable final RedisProtocolSupport.ZaddCondition condition,
                           @Nullable final RedisProtocolSupport.ZaddChange change,
                           final Collection<RedisProtocolSupport.ScoreMember> scoreMembers) throws Exception {
        return blockingInvocation(commander.zaddIncr(key, condition, change, scoreMembers));
    }

    @Override
    public String zcard(@RedisProtocolSupport.Key final CharSequence key) throws Exception {
        return blockingInvocation(commander.zcard(key));
    }

    @Override
    public String zcount(@RedisProtocolSupport.Key final CharSequence key, final double min,
                         final double max) throws Exception {
        return blockingInvocation(commander.zcount(key, min, max));
    }

    @Override
    public String zincrby(@RedisProtocolSupport.Key final CharSequence key, final long increment,
                          final CharSequence member) throws Exception {
        return blockingInvocation(commander.zincrby(key, increment, member));
    }

    @Override
    public String zinterstore(@RedisProtocolSupport.Key final CharSequence destination, final long numkeys,
                              @RedisProtocolSupport.Key final Collection<? extends CharSequence> keys) throws Exception {
        return blockingInvocation(commander.zinterstore(destination, numkeys, keys));
    }

    @Override
    public String zinterstore(@RedisProtocolSupport.Key final CharSequence destination, final long numkeys,
                              @RedisProtocolSupport.Key final Collection<? extends CharSequence> keys,
                              final Collection<Long> weightses,
                              @Nullable final RedisProtocolSupport.ZinterstoreAggregate aggregate) throws Exception {
        return blockingInvocation(commander.zinterstore(destination, numkeys, keys, weightses, aggregate));
    }

    @Override
    public String zlexcount(@RedisProtocolSupport.Key final CharSequence key, final CharSequence min,
                            final CharSequence max) throws Exception {
        return blockingInvocation(commander.zlexcount(key, min, max));
    }

    @Override
    public String zpopmax(@RedisProtocolSupport.Key final CharSequence key) throws Exception {
        return blockingInvocation(commander.zpopmax(key));
    }

    @Override
    public String zpopmax(@RedisProtocolSupport.Key final CharSequence key,
                          @Nullable final Long count) throws Exception {
        return blockingInvocation(commander.zpopmax(key, count));
    }

    @Override
    public String zpopmin(@RedisProtocolSupport.Key final CharSequence key) throws Exception {
        return blockingInvocation(commander.zpopmin(key));
    }

    @Override
    public String zpopmin(@RedisProtocolSupport.Key final CharSequence key,
                          @Nullable final Long count) throws Exception {
        return blockingInvocation(commander.zpopmin(key, count));
    }

    @Override
    public String zrange(@RedisProtocolSupport.Key final CharSequence key, final long start,
                         final long stop) throws Exception {
        return blockingInvocation(commander.zrange(key, start, stop));
    }

    @Override
    public String zrange(@RedisProtocolSupport.Key final CharSequence key, final long start, final long stop,
                         @Nullable final RedisProtocolSupport.ZrangeWithscores withscores) throws Exception {
        return blockingInvocation(commander.zrange(key, start, stop, withscores));
    }

    @Override
    public String zrangebylex(@RedisProtocolSupport.Key final CharSequence key, final CharSequence min,
                              final CharSequence max) throws Exception {
        return blockingInvocation(commander.zrangebylex(key, min, max));
    }

    @Override
    public String zrangebylex(@RedisProtocolSupport.Key final CharSequence key, final CharSequence min,
                              final CharSequence max,
                              @Nullable final RedisProtocolSupport.OffsetCount offsetCount) throws Exception {
        return blockingInvocation(commander.zrangebylex(key, min, max, offsetCount));
    }

    @Override
    public String zrangebyscore(@RedisProtocolSupport.Key final CharSequence key, final double min,
                                final double max) throws Exception {
        return blockingInvocation(commander.zrangebyscore(key, min, max));
    }

    @Override
    public String zrangebyscore(@RedisProtocolSupport.Key final CharSequence key, final double min, final double max,
                                @Nullable final RedisProtocolSupport.ZrangebyscoreWithscores withscores,
                                @Nullable final RedisProtocolSupport.OffsetCount offsetCount) throws Exception {
        return blockingInvocation(commander.zrangebyscore(key, min, max, withscores, offsetCount));
    }

    @Override
    public String zrank(@RedisProtocolSupport.Key final CharSequence key, final CharSequence member) throws Exception {
        return blockingInvocation(commander.zrank(key, member));
    }

    @Override
    public String zrem(@RedisProtocolSupport.Key final CharSequence key, final CharSequence member) throws Exception {
        return blockingInvocation(commander.zrem(key, member));
    }

    @Override
    public String zrem(@RedisProtocolSupport.Key final CharSequence key, final CharSequence member1,
                       final CharSequence member2) throws Exception {
        return blockingInvocation(commander.zrem(key, member1, member2));
    }

    @Override
    public String zrem(@RedisProtocolSupport.Key final CharSequence key, final CharSequence member1,
                       final CharSequence member2, final CharSequence member3) throws Exception {
        return blockingInvocation(commander.zrem(key, member1, member2, member3));
    }

    @Override
    public String zrem(@RedisProtocolSupport.Key final CharSequence key,
                       final Collection<? extends CharSequence> members) throws Exception {
        return blockingInvocation(commander.zrem(key, members));
    }

    @Override
    public String zremrangebylex(@RedisProtocolSupport.Key final CharSequence key, final CharSequence min,
                                 final CharSequence max) throws Exception {
        return blockingInvocation(commander.zremrangebylex(key, min, max));
    }

    @Override
    public String zremrangebyrank(@RedisProtocolSupport.Key final CharSequence key, final long start,
                                  final long stop) throws Exception {
        return blockingInvocation(commander.zremrangebyrank(key, start, stop));
    }

    @Override
    public String zremrangebyscore(@RedisProtocolSupport.Key final CharSequence key, final double min,
                                   final double max) throws Exception {
        return blockingInvocation(commander.zremrangebyscore(key, min, max));
    }

    @Override
    public String zrevrange(@RedisProtocolSupport.Key final CharSequence key, final long start,
                            final long stop) throws Exception {
        return blockingInvocation(commander.zrevrange(key, start, stop));
    }

    @Override
    public String zrevrange(@RedisProtocolSupport.Key final CharSequence key, final long start, final long stop,
                            @Nullable final RedisProtocolSupport.ZrevrangeWithscores withscores) throws Exception {
        return blockingInvocation(commander.zrevrange(key, start, stop, withscores));
    }

    @Override
    public String zrevrangebylex(@RedisProtocolSupport.Key final CharSequence key, final CharSequence max,
                                 final CharSequence min) throws Exception {
        return blockingInvocation(commander.zrevrangebylex(key, max, min));
    }

    @Override
    public String zrevrangebylex(@RedisProtocolSupport.Key final CharSequence key, final CharSequence max,
                                 final CharSequence min,
                                 @Nullable final RedisProtocolSupport.OffsetCount offsetCount) throws Exception {
        return blockingInvocation(commander.zrevrangebylex(key, max, min, offsetCount));
    }

    @Override
    public String zrevrangebyscore(@RedisProtocolSupport.Key final CharSequence key, final double max,
                                   final double min) throws Exception {
        return blockingInvocation(commander.zrevrangebyscore(key, max, min));
    }

    @Override
    public String zrevrangebyscore(@RedisProtocolSupport.Key final CharSequence key, final double max, final double min,
                                   @Nullable final RedisProtocolSupport.ZrevrangebyscoreWithscores withscores,
                                   @Nullable final RedisProtocolSupport.OffsetCount offsetCount) throws Exception {
        return blockingInvocation(commander.zrevrangebyscore(key, max, min, withscores, offsetCount));
    }

    @Override
    public String zrevrank(@RedisProtocolSupport.Key final CharSequence key,
                           final CharSequence member) throws Exception {
        return blockingInvocation(commander.zrevrank(key, member));
    }

    @Override
    public String zscan(@RedisProtocolSupport.Key final CharSequence key, final long cursor) throws Exception {
        return blockingInvocation(commander.zscan(key, cursor));
    }

    @Override
    public String zscan(@RedisProtocolSupport.Key final CharSequence key, final long cursor,
                        @Nullable final CharSequence matchPattern, @Nullable final Long count) throws Exception {
        return blockingInvocation(commander.zscan(key, cursor, matchPattern, count));
    }

    @Override
    public String zscore(@RedisProtocolSupport.Key final CharSequence key, final CharSequence member) throws Exception {
        return blockingInvocation(commander.zscore(key, member));
    }

    @Override
    public String zunionstore(@RedisProtocolSupport.Key final CharSequence destination, final long numkeys,
                              @RedisProtocolSupport.Key final Collection<? extends CharSequence> keys) throws Exception {
        return blockingInvocation(commander.zunionstore(destination, numkeys, keys));
    }

    @Override
    public String zunionstore(@RedisProtocolSupport.Key final CharSequence destination, final long numkeys,
                              @RedisProtocolSupport.Key final Collection<? extends CharSequence> keys,
                              final Collection<Long> weightses,
                              @Nullable final RedisProtocolSupport.ZunionstoreAggregate aggregate) throws Exception {
        return blockingInvocation(commander.zunionstore(destination, numkeys, keys, weightses, aggregate));
    }
}

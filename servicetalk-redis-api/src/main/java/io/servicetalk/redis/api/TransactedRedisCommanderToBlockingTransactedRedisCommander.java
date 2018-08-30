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
import static io.servicetalk.redis.api.BlockingUtils.singleToDeferredValue;

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
    public Deferred<Long> append(@RedisProtocolSupport.Key final CharSequence key,
                                 final CharSequence value) throws Exception {
        return singleToDeferredValue(commander.append(key, value));
    }

    @Override
    public Deferred<String> auth(final CharSequence password) throws Exception {
        return singleToDeferredValue(commander.auth(password));
    }

    @Override
    public Deferred<String> bgrewriteaof() throws Exception {
        return singleToDeferredValue(commander.bgrewriteaof());
    }

    @Override
    public Deferred<String> bgsave() throws Exception {
        return singleToDeferredValue(commander.bgsave());
    }

    @Override
    public Deferred<Long> bitcount(@RedisProtocolSupport.Key final CharSequence key) throws Exception {
        return singleToDeferredValue(commander.bitcount(key));
    }

    @Override
    public Deferred<Long> bitcount(@RedisProtocolSupport.Key final CharSequence key, @Nullable final Long start,
                                   @Nullable final Long end) throws Exception {
        return singleToDeferredValue(commander.bitcount(key, start, end));
    }

    @Override
    public Deferred<List<Long>> bitfield(@RedisProtocolSupport.Key final CharSequence key,
                                         final Collection<RedisProtocolSupport.BitfieldOperation> operations) throws Exception {
        return singleToDeferredValue(commander.bitfield(key, operations));
    }

    @Override
    public Deferred<Long> bitop(final CharSequence operation, @RedisProtocolSupport.Key final CharSequence destkey,
                                @RedisProtocolSupport.Key final CharSequence key) throws Exception {
        return singleToDeferredValue(commander.bitop(operation, destkey, key));
    }

    @Override
    public Deferred<Long> bitop(final CharSequence operation, @RedisProtocolSupport.Key final CharSequence destkey,
                                @RedisProtocolSupport.Key final CharSequence key1,
                                @RedisProtocolSupport.Key final CharSequence key2) throws Exception {
        return singleToDeferredValue(commander.bitop(operation, destkey, key1, key2));
    }

    @Override
    public Deferred<Long> bitop(final CharSequence operation, @RedisProtocolSupport.Key final CharSequence destkey,
                                @RedisProtocolSupport.Key final CharSequence key1,
                                @RedisProtocolSupport.Key final CharSequence key2,
                                @RedisProtocolSupport.Key final CharSequence key3) throws Exception {
        return singleToDeferredValue(commander.bitop(operation, destkey, key1, key2, key3));
    }

    @Override
    public Deferred<Long> bitop(final CharSequence operation, @RedisProtocolSupport.Key final CharSequence destkey,
                                @RedisProtocolSupport.Key final Collection<? extends CharSequence> keys) throws Exception {
        return singleToDeferredValue(commander.bitop(operation, destkey, keys));
    }

    @Override
    public Deferred<Long> bitpos(@RedisProtocolSupport.Key final CharSequence key, final long bit) throws Exception {
        return singleToDeferredValue(commander.bitpos(key, bit));
    }

    @Override
    public Deferred<Long> bitpos(@RedisProtocolSupport.Key final CharSequence key, final long bit,
                                 @Nullable final Long start, @Nullable final Long end) throws Exception {
        return singleToDeferredValue(commander.bitpos(key, bit, start, end));
    }

    @Override
    public <T> Deferred<List<T>> blpop(@RedisProtocolSupport.Key final Collection<? extends CharSequence> keys,
                                       final long timeout) throws Exception {
        return singleToDeferredValue(commander.blpop(keys, timeout));
    }

    @Override
    public <T> Deferred<List<T>> brpop(@RedisProtocolSupport.Key final Collection<? extends CharSequence> keys,
                                       final long timeout) throws Exception {
        return singleToDeferredValue(commander.brpop(keys, timeout));
    }

    @Override
    public Deferred<String> brpoplpush(@RedisProtocolSupport.Key final CharSequence source,
                                       @RedisProtocolSupport.Key final CharSequence destination,
                                       final long timeout) throws Exception {
        return singleToDeferredValue(commander.brpoplpush(source, destination, timeout));
    }

    @Override
    public <T> Deferred<List<T>> bzpopmax(@RedisProtocolSupport.Key final Collection<? extends CharSequence> keys,
                                          final long timeout) throws Exception {
        return singleToDeferredValue(commander.bzpopmax(keys, timeout));
    }

    @Override
    public <T> Deferred<List<T>> bzpopmin(@RedisProtocolSupport.Key final Collection<? extends CharSequence> keys,
                                          final long timeout) throws Exception {
        return singleToDeferredValue(commander.bzpopmin(keys, timeout));
    }

    @Override
    public Deferred<Long> clientKill(@Nullable final Long id, @Nullable final RedisProtocolSupport.ClientKillType type,
                                     @Nullable final CharSequence addrIpPort,
                                     @Nullable final CharSequence skipmeYesNo) throws Exception {
        return singleToDeferredValue(commander.clientKill(id, type, addrIpPort, skipmeYesNo));
    }

    @Override
    public Deferred<String> clientList() throws Exception {
        return singleToDeferredValue(commander.clientList());
    }

    @Override
    public Deferred<String> clientGetname() throws Exception {
        return singleToDeferredValue(commander.clientGetname());
    }

    @Override
    public Deferred<String> clientPause(final long timeout) throws Exception {
        return singleToDeferredValue(commander.clientPause(timeout));
    }

    @Override
    public Deferred<String> clientReply(final RedisProtocolSupport.ClientReplyReplyMode replyMode) throws Exception {
        return singleToDeferredValue(commander.clientReply(replyMode));
    }

    @Override
    public Deferred<String> clientSetname(final CharSequence connectionName) throws Exception {
        return singleToDeferredValue(commander.clientSetname(connectionName));
    }

    @Override
    public Deferred<String> clusterAddslots(final long slot) throws Exception {
        return singleToDeferredValue(commander.clusterAddslots(slot));
    }

    @Override
    public Deferred<String> clusterAddslots(final long slot1, final long slot2) throws Exception {
        return singleToDeferredValue(commander.clusterAddslots(slot1, slot2));
    }

    @Override
    public Deferred<String> clusterAddslots(final long slot1, final long slot2, final long slot3) throws Exception {
        return singleToDeferredValue(commander.clusterAddslots(slot1, slot2, slot3));
    }

    @Override
    public Deferred<String> clusterAddslots(final Collection<Long> slots) throws Exception {
        return singleToDeferredValue(commander.clusterAddslots(slots));
    }

    @Override
    public Deferred<Long> clusterCountFailureReports(final CharSequence nodeId) throws Exception {
        return singleToDeferredValue(commander.clusterCountFailureReports(nodeId));
    }

    @Override
    public Deferred<Long> clusterCountkeysinslot(final long slot) throws Exception {
        return singleToDeferredValue(commander.clusterCountkeysinslot(slot));
    }

    @Override
    public Deferred<String> clusterDelslots(final long slot) throws Exception {
        return singleToDeferredValue(commander.clusterDelslots(slot));
    }

    @Override
    public Deferred<String> clusterDelslots(final long slot1, final long slot2) throws Exception {
        return singleToDeferredValue(commander.clusterDelslots(slot1, slot2));
    }

    @Override
    public Deferred<String> clusterDelslots(final long slot1, final long slot2, final long slot3) throws Exception {
        return singleToDeferredValue(commander.clusterDelslots(slot1, slot2, slot3));
    }

    @Override
    public Deferred<String> clusterDelslots(final Collection<Long> slots) throws Exception {
        return singleToDeferredValue(commander.clusterDelslots(slots));
    }

    @Override
    public Deferred<String> clusterFailover() throws Exception {
        return singleToDeferredValue(commander.clusterFailover());
    }

    @Override
    public Deferred<String> clusterFailover(@Nullable final RedisProtocolSupport.ClusterFailoverOptions options) throws Exception {
        return singleToDeferredValue(commander.clusterFailover(options));
    }

    @Override
    public Deferred<String> clusterForget(final CharSequence nodeId) throws Exception {
        return singleToDeferredValue(commander.clusterForget(nodeId));
    }

    @Override
    public <T> Deferred<List<T>> clusterGetkeysinslot(final long slot, final long count) throws Exception {
        return singleToDeferredValue(commander.clusterGetkeysinslot(slot, count));
    }

    @Override
    public Deferred<String> clusterInfo() throws Exception {
        return singleToDeferredValue(commander.clusterInfo());
    }

    @Override
    public Deferred<Long> clusterKeyslot(final CharSequence key) throws Exception {
        return singleToDeferredValue(commander.clusterKeyslot(key));
    }

    @Override
    public Deferred<String> clusterMeet(final CharSequence ip, final long port) throws Exception {
        return singleToDeferredValue(commander.clusterMeet(ip, port));
    }

    @Override
    public Deferred<String> clusterNodes() throws Exception {
        return singleToDeferredValue(commander.clusterNodes());
    }

    @Override
    public Deferred<String> clusterReplicate(final CharSequence nodeId) throws Exception {
        return singleToDeferredValue(commander.clusterReplicate(nodeId));
    }

    @Override
    public Deferred<String> clusterReset() throws Exception {
        return singleToDeferredValue(commander.clusterReset());
    }

    @Override
    public Deferred<String> clusterReset(@Nullable final RedisProtocolSupport.ClusterResetResetType resetType) throws Exception {
        return singleToDeferredValue(commander.clusterReset(resetType));
    }

    @Override
    public Deferred<String> clusterSaveconfig() throws Exception {
        return singleToDeferredValue(commander.clusterSaveconfig());
    }

    @Override
    public Deferred<String> clusterSetConfigEpoch(final long configEpoch) throws Exception {
        return singleToDeferredValue(commander.clusterSetConfigEpoch(configEpoch));
    }

    @Override
    public Deferred<String> clusterSetslot(final long slot,
                                           final RedisProtocolSupport.ClusterSetslotSubcommand subcommand) throws Exception {
        return singleToDeferredValue(commander.clusterSetslot(slot, subcommand));
    }

    @Override
    public Deferred<String> clusterSetslot(final long slot,
                                           final RedisProtocolSupport.ClusterSetslotSubcommand subcommand,
                                           @Nullable final CharSequence nodeId) throws Exception {
        return singleToDeferredValue(commander.clusterSetslot(slot, subcommand, nodeId));
    }

    @Override
    public Deferred<String> clusterSlaves(final CharSequence nodeId) throws Exception {
        return singleToDeferredValue(commander.clusterSlaves(nodeId));
    }

    @Override
    public <T> Deferred<List<T>> clusterSlots() throws Exception {
        return singleToDeferredValue(commander.clusterSlots());
    }

    @Override
    public <T> Deferred<List<T>> command() throws Exception {
        return singleToDeferredValue(commander.command());
    }

    @Override
    public Deferred<Long> commandCount() throws Exception {
        return singleToDeferredValue(commander.commandCount());
    }

    @Override
    public <T> Deferred<List<T>> commandGetkeys() throws Exception {
        return singleToDeferredValue(commander.commandGetkeys());
    }

    @Override
    public <T> Deferred<List<T>> commandInfo(final CharSequence commandName) throws Exception {
        return singleToDeferredValue(commander.commandInfo(commandName));
    }

    @Override
    public <T> Deferred<List<T>> commandInfo(final CharSequence commandName1,
                                             final CharSequence commandName2) throws Exception {
        return singleToDeferredValue(commander.commandInfo(commandName1, commandName2));
    }

    @Override
    public <T> Deferred<List<T>> commandInfo(final CharSequence commandName1, final CharSequence commandName2,
                                             final CharSequence commandName3) throws Exception {
        return singleToDeferredValue(commander.commandInfo(commandName1, commandName2, commandName3));
    }

    @Override
    public <T> Deferred<List<T>> commandInfo(final Collection<? extends CharSequence> commandNames) throws Exception {
        return singleToDeferredValue(commander.commandInfo(commandNames));
    }

    @Override
    public <T> Deferred<List<T>> configGet(final CharSequence parameter) throws Exception {
        return singleToDeferredValue(commander.configGet(parameter));
    }

    @Override
    public Deferred<String> configRewrite() throws Exception {
        return singleToDeferredValue(commander.configRewrite());
    }

    @Override
    public Deferred<String> configSet(final CharSequence parameter, final CharSequence value) throws Exception {
        return singleToDeferredValue(commander.configSet(parameter, value));
    }

    @Override
    public Deferred<String> configResetstat() throws Exception {
        return singleToDeferredValue(commander.configResetstat());
    }

    @Override
    public Deferred<Long> dbsize() throws Exception {
        return singleToDeferredValue(commander.dbsize());
    }

    @Override
    public Deferred<String> debugObject(@RedisProtocolSupport.Key final CharSequence key) throws Exception {
        return singleToDeferredValue(commander.debugObject(key));
    }

    @Override
    public Deferred<String> debugSegfault() throws Exception {
        return singleToDeferredValue(commander.debugSegfault());
    }

    @Override
    public Deferred<Long> decr(@RedisProtocolSupport.Key final CharSequence key) throws Exception {
        return singleToDeferredValue(commander.decr(key));
    }

    @Override
    public Deferred<Long> decrby(@RedisProtocolSupport.Key final CharSequence key,
                                 final long decrement) throws Exception {
        return singleToDeferredValue(commander.decrby(key, decrement));
    }

    @Override
    public Deferred<Long> del(@RedisProtocolSupport.Key final CharSequence key) throws Exception {
        return singleToDeferredValue(commander.del(key));
    }

    @Override
    public Deferred<Long> del(@RedisProtocolSupport.Key final CharSequence key1,
                              @RedisProtocolSupport.Key final CharSequence key2) throws Exception {
        return singleToDeferredValue(commander.del(key1, key2));
    }

    @Override
    public Deferred<Long> del(@RedisProtocolSupport.Key final CharSequence key1,
                              @RedisProtocolSupport.Key final CharSequence key2,
                              @RedisProtocolSupport.Key final CharSequence key3) throws Exception {
        return singleToDeferredValue(commander.del(key1, key2, key3));
    }

    @Override
    public Deferred<Long> del(@RedisProtocolSupport.Key final Collection<? extends CharSequence> keys) throws Exception {
        return singleToDeferredValue(commander.del(keys));
    }

    @Override
    public String discard() throws Exception {
        return blockingInvocation(commander.discard());
    }

    @Override
    public Deferred<String> dump(@RedisProtocolSupport.Key final CharSequence key) throws Exception {
        return singleToDeferredValue(commander.dump(key));
    }

    @Override
    public Deferred<String> echo(final CharSequence message) throws Exception {
        return singleToDeferredValue(commander.echo(message));
    }

    @Override
    public Deferred<String> eval(final CharSequence script, final long numkeys,
                                 @RedisProtocolSupport.Key final Collection<? extends CharSequence> keys,
                                 final Collection<? extends CharSequence> args) throws Exception {
        return singleToDeferredValue(commander.eval(script, numkeys, keys, args));
    }

    @Override
    public <T> Deferred<List<T>> evalList(final CharSequence script, final long numkeys,
                                          @RedisProtocolSupport.Key final Collection<? extends CharSequence> keys,
                                          final Collection<? extends CharSequence> args) throws Exception {
        return singleToDeferredValue(commander.evalList(script, numkeys, keys, args));
    }

    @Override
    public Deferred<Long> evalLong(final CharSequence script, final long numkeys,
                                   @RedisProtocolSupport.Key final Collection<? extends CharSequence> keys,
                                   final Collection<? extends CharSequence> args) throws Exception {
        return singleToDeferredValue(commander.evalLong(script, numkeys, keys, args));
    }

    @Override
    public Deferred<String> evalsha(final CharSequence sha1, final long numkeys,
                                    @RedisProtocolSupport.Key final Collection<? extends CharSequence> keys,
                                    final Collection<? extends CharSequence> args) throws Exception {
        return singleToDeferredValue(commander.evalsha(sha1, numkeys, keys, args));
    }

    @Override
    public <T> Deferred<List<T>> evalshaList(final CharSequence sha1, final long numkeys,
                                             @RedisProtocolSupport.Key final Collection<? extends CharSequence> keys,
                                             final Collection<? extends CharSequence> args) throws Exception {
        return singleToDeferredValue(commander.evalshaList(sha1, numkeys, keys, args));
    }

    @Override
    public Deferred<Long> evalshaLong(final CharSequence sha1, final long numkeys,
                                      @RedisProtocolSupport.Key final Collection<? extends CharSequence> keys,
                                      final Collection<? extends CharSequence> args) throws Exception {
        return singleToDeferredValue(commander.evalshaLong(sha1, numkeys, keys, args));
    }

    @Override
    public void exec() throws Exception {
        blockingInvocation(commander.exec());
    }

    @Override
    public Deferred<Long> exists(@RedisProtocolSupport.Key final CharSequence key) throws Exception {
        return singleToDeferredValue(commander.exists(key));
    }

    @Override
    public Deferred<Long> exists(@RedisProtocolSupport.Key final CharSequence key1,
                                 @RedisProtocolSupport.Key final CharSequence key2) throws Exception {
        return singleToDeferredValue(commander.exists(key1, key2));
    }

    @Override
    public Deferred<Long> exists(@RedisProtocolSupport.Key final CharSequence key1,
                                 @RedisProtocolSupport.Key final CharSequence key2,
                                 @RedisProtocolSupport.Key final CharSequence key3) throws Exception {
        return singleToDeferredValue(commander.exists(key1, key2, key3));
    }

    @Override
    public Deferred<Long> exists(@RedisProtocolSupport.Key final Collection<? extends CharSequence> keys) throws Exception {
        return singleToDeferredValue(commander.exists(keys));
    }

    @Override
    public Deferred<Long> expire(@RedisProtocolSupport.Key final CharSequence key,
                                 final long seconds) throws Exception {
        return singleToDeferredValue(commander.expire(key, seconds));
    }

    @Override
    public Deferred<Long> expireat(@RedisProtocolSupport.Key final CharSequence key,
                                   final long timestamp) throws Exception {
        return singleToDeferredValue(commander.expireat(key, timestamp));
    }

    @Override
    public Deferred<String> flushall() throws Exception {
        return singleToDeferredValue(commander.flushall());
    }

    @Override
    public Deferred<String> flushall(@Nullable final RedisProtocolSupport.FlushallAsync async) throws Exception {
        return singleToDeferredValue(commander.flushall(async));
    }

    @Override
    public Deferred<String> flushdb() throws Exception {
        return singleToDeferredValue(commander.flushdb());
    }

    @Override
    public Deferred<String> flushdb(@Nullable final RedisProtocolSupport.FlushdbAsync async) throws Exception {
        return singleToDeferredValue(commander.flushdb(async));
    }

    @Override
    public Deferred<Long> geoadd(@RedisProtocolSupport.Key final CharSequence key, final double longitude,
                                 final double latitude, final CharSequence member) throws Exception {
        return singleToDeferredValue(commander.geoadd(key, longitude, latitude, member));
    }

    @Override
    public Deferred<Long> geoadd(@RedisProtocolSupport.Key final CharSequence key, final double longitude1,
                                 final double latitude1, final CharSequence member1, final double longitude2,
                                 final double latitude2, final CharSequence member2) throws Exception {
        return singleToDeferredValue(
                    commander.geoadd(key, longitude1, latitude1, member1, longitude2, latitude2, member2));
    }

    @Override
    public Deferred<Long> geoadd(@RedisProtocolSupport.Key final CharSequence key, final double longitude1,
                                 final double latitude1, final CharSequence member1, final double longitude2,
                                 final double latitude2, final CharSequence member2, final double longitude3,
                                 final double latitude3, final CharSequence member3) throws Exception {
        return singleToDeferredValue(commander.geoadd(key, longitude1, latitude1, member1, longitude2, latitude2,
                    member2, longitude3, latitude3, member3));
    }

    @Override
    public Deferred<Long> geoadd(@RedisProtocolSupport.Key final CharSequence key,
                                 final Collection<RedisProtocolSupport.LongitudeLatitudeMember> longitudeLatitudeMembers) throws Exception {
        return singleToDeferredValue(commander.geoadd(key, longitudeLatitudeMembers));
    }

    @Override
    public Deferred<Double> geodist(@RedisProtocolSupport.Key final CharSequence key, final CharSequence member1,
                                    final CharSequence member2) throws Exception {
        return singleToDeferredValue(commander.geodist(key, member1, member2));
    }

    @Override
    public Deferred<Double> geodist(@RedisProtocolSupport.Key final CharSequence key, final CharSequence member1,
                                    final CharSequence member2, @Nullable final CharSequence unit) throws Exception {
        return singleToDeferredValue(commander.geodist(key, member1, member2, unit));
    }

    @Override
    public <T> Deferred<List<T>> geohash(@RedisProtocolSupport.Key final CharSequence key,
                                         final CharSequence member) throws Exception {
        return singleToDeferredValue(commander.geohash(key, member));
    }

    @Override
    public <T> Deferred<List<T>> geohash(@RedisProtocolSupport.Key final CharSequence key, final CharSequence member1,
                                         final CharSequence member2) throws Exception {
        return singleToDeferredValue(commander.geohash(key, member1, member2));
    }

    @Override
    public <T> Deferred<List<T>> geohash(@RedisProtocolSupport.Key final CharSequence key, final CharSequence member1,
                                         final CharSequence member2, final CharSequence member3) throws Exception {
        return singleToDeferredValue(commander.geohash(key, member1, member2, member3));
    }

    @Override
    public <T> Deferred<List<T>> geohash(@RedisProtocolSupport.Key final CharSequence key,
                                         final Collection<? extends CharSequence> members) throws Exception {
        return singleToDeferredValue(commander.geohash(key, members));
    }

    @Override
    public <T> Deferred<List<T>> geopos(@RedisProtocolSupport.Key final CharSequence key,
                                        final CharSequence member) throws Exception {
        return singleToDeferredValue(commander.geopos(key, member));
    }

    @Override
    public <T> Deferred<List<T>> geopos(@RedisProtocolSupport.Key final CharSequence key, final CharSequence member1,
                                        final CharSequence member2) throws Exception {
        return singleToDeferredValue(commander.geopos(key, member1, member2));
    }

    @Override
    public <T> Deferred<List<T>> geopos(@RedisProtocolSupport.Key final CharSequence key, final CharSequence member1,
                                        final CharSequence member2, final CharSequence member3) throws Exception {
        return singleToDeferredValue(commander.geopos(key, member1, member2, member3));
    }

    @Override
    public <T> Deferred<List<T>> geopos(@RedisProtocolSupport.Key final CharSequence key,
                                        final Collection<? extends CharSequence> members) throws Exception {
        return singleToDeferredValue(commander.geopos(key, members));
    }

    @Override
    public <T> Deferred<List<T>> georadius(@RedisProtocolSupport.Key final CharSequence key, final double longitude,
                                           final double latitude, final double radius,
                                           final RedisProtocolSupport.GeoradiusUnit unit) throws Exception {
        return singleToDeferredValue(commander.georadius(key, longitude, latitude, radius, unit));
    }

    @Override
    public <T> Deferred<List<T>> georadius(@RedisProtocolSupport.Key final CharSequence key, final double longitude,
                                           final double latitude, final double radius,
                                           final RedisProtocolSupport.GeoradiusUnit unit,
                                           @Nullable final RedisProtocolSupport.GeoradiusWithcoord withcoord,
                                           @Nullable final RedisProtocolSupport.GeoradiusWithdist withdist,
                                           @Nullable final RedisProtocolSupport.GeoradiusWithhash withhash,
                                           @Nullable final Long count,
                                           @Nullable final RedisProtocolSupport.GeoradiusOrder order,
                                           @Nullable @RedisProtocolSupport.Key final CharSequence storeKey,
                                           @Nullable @RedisProtocolSupport.Key final CharSequence storedistKey) throws Exception {
        return singleToDeferredValue(commander.georadius(key, longitude, latitude, radius, unit, withcoord, withdist,
                    withhash, count, order, storeKey, storedistKey));
    }

    @Override
    public <T> Deferred<List<T>> georadiusbymember(@RedisProtocolSupport.Key final CharSequence key,
                                                   final CharSequence member, final double radius,
                                                   final RedisProtocolSupport.GeoradiusbymemberUnit unit) throws Exception {
        return singleToDeferredValue(commander.georadiusbymember(key, member, radius, unit));
    }

    @Override
    public <T> Deferred<List<T>> georadiusbymember(@RedisProtocolSupport.Key final CharSequence key,
                                                   final CharSequence member, final double radius,
                                                   final RedisProtocolSupport.GeoradiusbymemberUnit unit,
                                                   @Nullable final RedisProtocolSupport.GeoradiusbymemberWithcoord withcoord,
                                                   @Nullable final RedisProtocolSupport.GeoradiusbymemberWithdist withdist,
                                                   @Nullable final RedisProtocolSupport.GeoradiusbymemberWithhash withhash,
                                                   @Nullable final Long count,
                                                   @Nullable final RedisProtocolSupport.GeoradiusbymemberOrder order,
                                                   @Nullable @RedisProtocolSupport.Key final CharSequence storeKey,
                                                   @Nullable @RedisProtocolSupport.Key final CharSequence storedistKey) throws Exception {
        return singleToDeferredValue(commander.georadiusbymember(key, member, radius, unit, withcoord, withdist,
                    withhash, count, order, storeKey, storedistKey));
    }

    @Override
    public Deferred<String> get(@RedisProtocolSupport.Key final CharSequence key) throws Exception {
        return singleToDeferredValue(commander.get(key));
    }

    @Override
    public Deferred<Long> getbit(@RedisProtocolSupport.Key final CharSequence key, final long offset) throws Exception {
        return singleToDeferredValue(commander.getbit(key, offset));
    }

    @Override
    public Deferred<String> getrange(@RedisProtocolSupport.Key final CharSequence key, final long start,
                                     final long end) throws Exception {
        return singleToDeferredValue(commander.getrange(key, start, end));
    }

    @Override
    public Deferred<String> getset(@RedisProtocolSupport.Key final CharSequence key,
                                   final CharSequence value) throws Exception {
        return singleToDeferredValue(commander.getset(key, value));
    }

    @Override
    public Deferred<Long> hdel(@RedisProtocolSupport.Key final CharSequence key,
                               final CharSequence field) throws Exception {
        return singleToDeferredValue(commander.hdel(key, field));
    }

    @Override
    public Deferred<Long> hdel(@RedisProtocolSupport.Key final CharSequence key, final CharSequence field1,
                               final CharSequence field2) throws Exception {
        return singleToDeferredValue(commander.hdel(key, field1, field2));
    }

    @Override
    public Deferred<Long> hdel(@RedisProtocolSupport.Key final CharSequence key, final CharSequence field1,
                               final CharSequence field2, final CharSequence field3) throws Exception {
        return singleToDeferredValue(commander.hdel(key, field1, field2, field3));
    }

    @Override
    public Deferred<Long> hdel(@RedisProtocolSupport.Key final CharSequence key,
                               final Collection<? extends CharSequence> fields) throws Exception {
        return singleToDeferredValue(commander.hdel(key, fields));
    }

    @Override
    public Deferred<Long> hexists(@RedisProtocolSupport.Key final CharSequence key,
                                  final CharSequence field) throws Exception {
        return singleToDeferredValue(commander.hexists(key, field));
    }

    @Override
    public Deferred<String> hget(@RedisProtocolSupport.Key final CharSequence key,
                                 final CharSequence field) throws Exception {
        return singleToDeferredValue(commander.hget(key, field));
    }

    @Override
    public <T> Deferred<List<T>> hgetall(@RedisProtocolSupport.Key final CharSequence key) throws Exception {
        return singleToDeferredValue(commander.hgetall(key));
    }

    @Override
    public Deferred<Long> hincrby(@RedisProtocolSupport.Key final CharSequence key, final CharSequence field,
                                  final long increment) throws Exception {
        return singleToDeferredValue(commander.hincrby(key, field, increment));
    }

    @Override
    public Deferred<Double> hincrbyfloat(@RedisProtocolSupport.Key final CharSequence key, final CharSequence field,
                                         final double increment) throws Exception {
        return singleToDeferredValue(commander.hincrbyfloat(key, field, increment));
    }

    @Override
    public <T> Deferred<List<T>> hkeys(@RedisProtocolSupport.Key final CharSequence key) throws Exception {
        return singleToDeferredValue(commander.hkeys(key));
    }

    @Override
    public Deferred<Long> hlen(@RedisProtocolSupport.Key final CharSequence key) throws Exception {
        return singleToDeferredValue(commander.hlen(key));
    }

    @Override
    public <T> Deferred<List<T>> hmget(@RedisProtocolSupport.Key final CharSequence key,
                                       final CharSequence field) throws Exception {
        return singleToDeferredValue(commander.hmget(key, field));
    }

    @Override
    public <T> Deferred<List<T>> hmget(@RedisProtocolSupport.Key final CharSequence key, final CharSequence field1,
                                       final CharSequence field2) throws Exception {
        return singleToDeferredValue(commander.hmget(key, field1, field2));
    }

    @Override
    public <T> Deferred<List<T>> hmget(@RedisProtocolSupport.Key final CharSequence key, final CharSequence field1,
                                       final CharSequence field2, final CharSequence field3) throws Exception {
        return singleToDeferredValue(commander.hmget(key, field1, field2, field3));
    }

    @Override
    public <T> Deferred<List<T>> hmget(@RedisProtocolSupport.Key final CharSequence key,
                                       final Collection<? extends CharSequence> fields) throws Exception {
        return singleToDeferredValue(commander.hmget(key, fields));
    }

    @Override
    public Deferred<String> hmset(@RedisProtocolSupport.Key final CharSequence key, final CharSequence field,
                                  final CharSequence value) throws Exception {
        return singleToDeferredValue(commander.hmset(key, field, value));
    }

    @Override
    public Deferred<String> hmset(@RedisProtocolSupport.Key final CharSequence key, final CharSequence field1,
                                  final CharSequence value1, final CharSequence field2,
                                  final CharSequence value2) throws Exception {
        return singleToDeferredValue(commander.hmset(key, field1, value1, field2, value2));
    }

    @Override
    public Deferred<String> hmset(@RedisProtocolSupport.Key final CharSequence key, final CharSequence field1,
                                  final CharSequence value1, final CharSequence field2, final CharSequence value2,
                                  final CharSequence field3, final CharSequence value3) throws Exception {
        return singleToDeferredValue(commander.hmset(key, field1, value1, field2, value2, field3, value3));
    }

    @Override
    public Deferred<String> hmset(@RedisProtocolSupport.Key final CharSequence key,
                                  final Collection<RedisProtocolSupport.FieldValue> fieldValues) throws Exception {
        return singleToDeferredValue(commander.hmset(key, fieldValues));
    }

    @Override
    public <T> Deferred<List<T>> hscan(@RedisProtocolSupport.Key final CharSequence key,
                                       final long cursor) throws Exception {
        return singleToDeferredValue(commander.hscan(key, cursor));
    }

    @Override
    public <T> Deferred<List<T>> hscan(@RedisProtocolSupport.Key final CharSequence key, final long cursor,
                                       @Nullable final CharSequence matchPattern,
                                       @Nullable final Long count) throws Exception {
        return singleToDeferredValue(commander.hscan(key, cursor, matchPattern, count));
    }

    @Override
    public Deferred<Long> hset(@RedisProtocolSupport.Key final CharSequence key, final CharSequence field,
                               final CharSequence value) throws Exception {
        return singleToDeferredValue(commander.hset(key, field, value));
    }

    @Override
    public Deferred<Long> hsetnx(@RedisProtocolSupport.Key final CharSequence key, final CharSequence field,
                                 final CharSequence value) throws Exception {
        return singleToDeferredValue(commander.hsetnx(key, field, value));
    }

    @Override
    public Deferred<Long> hstrlen(@RedisProtocolSupport.Key final CharSequence key,
                                  final CharSequence field) throws Exception {
        return singleToDeferredValue(commander.hstrlen(key, field));
    }

    @Override
    public <T> Deferred<List<T>> hvals(@RedisProtocolSupport.Key final CharSequence key) throws Exception {
        return singleToDeferredValue(commander.hvals(key));
    }

    @Override
    public Deferred<Long> incr(@RedisProtocolSupport.Key final CharSequence key) throws Exception {
        return singleToDeferredValue(commander.incr(key));
    }

    @Override
    public Deferred<Long> incrby(@RedisProtocolSupport.Key final CharSequence key,
                                 final long increment) throws Exception {
        return singleToDeferredValue(commander.incrby(key, increment));
    }

    @Override
    public Deferred<Double> incrbyfloat(@RedisProtocolSupport.Key final CharSequence key,
                                        final double increment) throws Exception {
        return singleToDeferredValue(commander.incrbyfloat(key, increment));
    }

    @Override
    public Deferred<String> info() throws Exception {
        return singleToDeferredValue(commander.info());
    }

    @Override
    public Deferred<String> info(@Nullable final CharSequence section) throws Exception {
        return singleToDeferredValue(commander.info(section));
    }

    @Override
    public <T> Deferred<List<T>> keys(final CharSequence pattern) throws Exception {
        return singleToDeferredValue(commander.keys(pattern));
    }

    @Override
    public Deferred<Long> lastsave() throws Exception {
        return singleToDeferredValue(commander.lastsave());
    }

    @Override
    public Deferred<String> lindex(@RedisProtocolSupport.Key final CharSequence key,
                                   final long index) throws Exception {
        return singleToDeferredValue(commander.lindex(key, index));
    }

    @Override
    public Deferred<Long> linsert(@RedisProtocolSupport.Key final CharSequence key,
                                  final RedisProtocolSupport.LinsertWhere where, final CharSequence pivot,
                                  final CharSequence value) throws Exception {
        return singleToDeferredValue(commander.linsert(key, where, pivot, value));
    }

    @Override
    public Deferred<Long> llen(@RedisProtocolSupport.Key final CharSequence key) throws Exception {
        return singleToDeferredValue(commander.llen(key));
    }

    @Override
    public Deferred<String> lpop(@RedisProtocolSupport.Key final CharSequence key) throws Exception {
        return singleToDeferredValue(commander.lpop(key));
    }

    @Override
    public Deferred<Long> lpush(@RedisProtocolSupport.Key final CharSequence key,
                                final CharSequence value) throws Exception {
        return singleToDeferredValue(commander.lpush(key, value));
    }

    @Override
    public Deferred<Long> lpush(@RedisProtocolSupport.Key final CharSequence key, final CharSequence value1,
                                final CharSequence value2) throws Exception {
        return singleToDeferredValue(commander.lpush(key, value1, value2));
    }

    @Override
    public Deferred<Long> lpush(@RedisProtocolSupport.Key final CharSequence key, final CharSequence value1,
                                final CharSequence value2, final CharSequence value3) throws Exception {
        return singleToDeferredValue(commander.lpush(key, value1, value2, value3));
    }

    @Override
    public Deferred<Long> lpush(@RedisProtocolSupport.Key final CharSequence key,
                                final Collection<? extends CharSequence> values) throws Exception {
        return singleToDeferredValue(commander.lpush(key, values));
    }

    @Override
    public Deferred<Long> lpushx(@RedisProtocolSupport.Key final CharSequence key,
                                 final CharSequence value) throws Exception {
        return singleToDeferredValue(commander.lpushx(key, value));
    }

    @Override
    public <T> Deferred<List<T>> lrange(@RedisProtocolSupport.Key final CharSequence key, final long start,
                                        final long stop) throws Exception {
        return singleToDeferredValue(commander.lrange(key, start, stop));
    }

    @Override
    public Deferred<Long> lrem(@RedisProtocolSupport.Key final CharSequence key, final long count,
                               final CharSequence value) throws Exception {
        return singleToDeferredValue(commander.lrem(key, count, value));
    }

    @Override
    public Deferred<String> lset(@RedisProtocolSupport.Key final CharSequence key, final long index,
                                 final CharSequence value) throws Exception {
        return singleToDeferredValue(commander.lset(key, index, value));
    }

    @Override
    public Deferred<String> ltrim(@RedisProtocolSupport.Key final CharSequence key, final long start,
                                  final long stop) throws Exception {
        return singleToDeferredValue(commander.ltrim(key, start, stop));
    }

    @Override
    public Deferred<String> memoryDoctor() throws Exception {
        return singleToDeferredValue(commander.memoryDoctor());
    }

    @Override
    public <T> Deferred<List<T>> memoryHelp() throws Exception {
        return singleToDeferredValue(commander.memoryHelp());
    }

    @Override
    public Deferred<String> memoryMallocStats() throws Exception {
        return singleToDeferredValue(commander.memoryMallocStats());
    }

    @Override
    public Deferred<String> memoryPurge() throws Exception {
        return singleToDeferredValue(commander.memoryPurge());
    }

    @Override
    public <T> Deferred<List<T>> memoryStats() throws Exception {
        return singleToDeferredValue(commander.memoryStats());
    }

    @Override
    public Deferred<Long> memoryUsage(@RedisProtocolSupport.Key final CharSequence key) throws Exception {
        return singleToDeferredValue(commander.memoryUsage(key));
    }

    @Override
    public Deferred<Long> memoryUsage(@RedisProtocolSupport.Key final CharSequence key,
                                      @Nullable final Long samplesCount) throws Exception {
        return singleToDeferredValue(commander.memoryUsage(key, samplesCount));
    }

    @Override
    public <T> Deferred<List<T>> mget(@RedisProtocolSupport.Key final CharSequence key) throws Exception {
        return singleToDeferredValue(commander.mget(key));
    }

    @Override
    public <T> Deferred<List<T>> mget(@RedisProtocolSupport.Key final CharSequence key1,
                                      @RedisProtocolSupport.Key final CharSequence key2) throws Exception {
        return singleToDeferredValue(commander.mget(key1, key2));
    }

    @Override
    public <T> Deferred<List<T>> mget(@RedisProtocolSupport.Key final CharSequence key1,
                                      @RedisProtocolSupport.Key final CharSequence key2,
                                      @RedisProtocolSupport.Key final CharSequence key3) throws Exception {
        return singleToDeferredValue(commander.mget(key1, key2, key3));
    }

    @Override
    public <T> Deferred<List<T>> mget(@RedisProtocolSupport.Key final Collection<? extends CharSequence> keys) throws Exception {
        return singleToDeferredValue(commander.mget(keys));
    }

    @Override
    public Deferred<Long> move(@RedisProtocolSupport.Key final CharSequence key, final long db) throws Exception {
        return singleToDeferredValue(commander.move(key, db));
    }

    @Override
    public Deferred<String> mset(@RedisProtocolSupport.Key final CharSequence key,
                                 final CharSequence value) throws Exception {
        return singleToDeferredValue(commander.mset(key, value));
    }

    @Override
    public Deferred<String> mset(@RedisProtocolSupport.Key final CharSequence key1, final CharSequence value1,
                                 @RedisProtocolSupport.Key final CharSequence key2,
                                 final CharSequence value2) throws Exception {
        return singleToDeferredValue(commander.mset(key1, value1, key2, value2));
    }

    @Override
    public Deferred<String> mset(@RedisProtocolSupport.Key final CharSequence key1, final CharSequence value1,
                                 @RedisProtocolSupport.Key final CharSequence key2, final CharSequence value2,
                                 @RedisProtocolSupport.Key final CharSequence key3,
                                 final CharSequence value3) throws Exception {
        return singleToDeferredValue(commander.mset(key1, value1, key2, value2, key3, value3));
    }

    @Override
    public Deferred<String> mset(final Collection<RedisProtocolSupport.KeyValue> keyValues) throws Exception {
        return singleToDeferredValue(commander.mset(keyValues));
    }

    @Override
    public Deferred<Long> msetnx(@RedisProtocolSupport.Key final CharSequence key,
                                 final CharSequence value) throws Exception {
        return singleToDeferredValue(commander.msetnx(key, value));
    }

    @Override
    public Deferred<Long> msetnx(@RedisProtocolSupport.Key final CharSequence key1, final CharSequence value1,
                                 @RedisProtocolSupport.Key final CharSequence key2,
                                 final CharSequence value2) throws Exception {
        return singleToDeferredValue(commander.msetnx(key1, value1, key2, value2));
    }

    @Override
    public Deferred<Long> msetnx(@RedisProtocolSupport.Key final CharSequence key1, final CharSequence value1,
                                 @RedisProtocolSupport.Key final CharSequence key2, final CharSequence value2,
                                 @RedisProtocolSupport.Key final CharSequence key3,
                                 final CharSequence value3) throws Exception {
        return singleToDeferredValue(commander.msetnx(key1, value1, key2, value2, key3, value3));
    }

    @Override
    public Deferred<Long> msetnx(final Collection<RedisProtocolSupport.KeyValue> keyValues) throws Exception {
        return singleToDeferredValue(commander.msetnx(keyValues));
    }

    @Override
    public Deferred<String> objectEncoding(@RedisProtocolSupport.Key final CharSequence key) throws Exception {
        return singleToDeferredValue(commander.objectEncoding(key));
    }

    @Override
    public Deferred<Long> objectFreq(@RedisProtocolSupport.Key final CharSequence key) throws Exception {
        return singleToDeferredValue(commander.objectFreq(key));
    }

    @Override
    public Deferred<List<String>> objectHelp() throws Exception {
        return singleToDeferredValue(commander.objectHelp());
    }

    @Override
    public Deferred<Long> objectIdletime(@RedisProtocolSupport.Key final CharSequence key) throws Exception {
        return singleToDeferredValue(commander.objectIdletime(key));
    }

    @Override
    public Deferred<Long> objectRefcount(@RedisProtocolSupport.Key final CharSequence key) throws Exception {
        return singleToDeferredValue(commander.objectRefcount(key));
    }

    @Override
    public Deferred<Long> persist(@RedisProtocolSupport.Key final CharSequence key) throws Exception {
        return singleToDeferredValue(commander.persist(key));
    }

    @Override
    public Deferred<Long> pexpire(@RedisProtocolSupport.Key final CharSequence key,
                                  final long milliseconds) throws Exception {
        return singleToDeferredValue(commander.pexpire(key, milliseconds));
    }

    @Override
    public Deferred<Long> pexpireat(@RedisProtocolSupport.Key final CharSequence key,
                                    final long millisecondsTimestamp) throws Exception {
        return singleToDeferredValue(commander.pexpireat(key, millisecondsTimestamp));
    }

    @Override
    public Deferred<Long> pfadd(@RedisProtocolSupport.Key final CharSequence key,
                                final CharSequence element) throws Exception {
        return singleToDeferredValue(commander.pfadd(key, element));
    }

    @Override
    public Deferred<Long> pfadd(@RedisProtocolSupport.Key final CharSequence key, final CharSequence element1,
                                final CharSequence element2) throws Exception {
        return singleToDeferredValue(commander.pfadd(key, element1, element2));
    }

    @Override
    public Deferred<Long> pfadd(@RedisProtocolSupport.Key final CharSequence key, final CharSequence element1,
                                final CharSequence element2, final CharSequence element3) throws Exception {
        return singleToDeferredValue(commander.pfadd(key, element1, element2, element3));
    }

    @Override
    public Deferred<Long> pfadd(@RedisProtocolSupport.Key final CharSequence key,
                                final Collection<? extends CharSequence> elements) throws Exception {
        return singleToDeferredValue(commander.pfadd(key, elements));
    }

    @Override
    public Deferred<Long> pfcount(@RedisProtocolSupport.Key final CharSequence key) throws Exception {
        return singleToDeferredValue(commander.pfcount(key));
    }

    @Override
    public Deferred<Long> pfcount(@RedisProtocolSupport.Key final CharSequence key1,
                                  @RedisProtocolSupport.Key final CharSequence key2) throws Exception {
        return singleToDeferredValue(commander.pfcount(key1, key2));
    }

    @Override
    public Deferred<Long> pfcount(@RedisProtocolSupport.Key final CharSequence key1,
                                  @RedisProtocolSupport.Key final CharSequence key2,
                                  @RedisProtocolSupport.Key final CharSequence key3) throws Exception {
        return singleToDeferredValue(commander.pfcount(key1, key2, key3));
    }

    @Override
    public Deferred<Long> pfcount(@RedisProtocolSupport.Key final Collection<? extends CharSequence> keys) throws Exception {
        return singleToDeferredValue(commander.pfcount(keys));
    }

    @Override
    public Deferred<String> pfmerge(@RedisProtocolSupport.Key final CharSequence destkey,
                                    @RedisProtocolSupport.Key final CharSequence sourcekey) throws Exception {
        return singleToDeferredValue(commander.pfmerge(destkey, sourcekey));
    }

    @Override
    public Deferred<String> pfmerge(@RedisProtocolSupport.Key final CharSequence destkey,
                                    @RedisProtocolSupport.Key final CharSequence sourcekey1,
                                    @RedisProtocolSupport.Key final CharSequence sourcekey2) throws Exception {
        return singleToDeferredValue(commander.pfmerge(destkey, sourcekey1, sourcekey2));
    }

    @Override
    public Deferred<String> pfmerge(@RedisProtocolSupport.Key final CharSequence destkey,
                                    @RedisProtocolSupport.Key final CharSequence sourcekey1,
                                    @RedisProtocolSupport.Key final CharSequence sourcekey2,
                                    @RedisProtocolSupport.Key final CharSequence sourcekey3) throws Exception {
        return singleToDeferredValue(commander.pfmerge(destkey, sourcekey1, sourcekey2, sourcekey3));
    }

    @Override
    public Deferred<String> pfmerge(@RedisProtocolSupport.Key final CharSequence destkey,
                                    @RedisProtocolSupport.Key final Collection<? extends CharSequence> sourcekeys) throws Exception {
        return singleToDeferredValue(commander.pfmerge(destkey, sourcekeys));
    }

    @Override
    public Deferred<String> ping() throws Exception {
        return singleToDeferredValue(commander.ping());
    }

    @Override
    public Deferred<String> ping(final CharSequence message) throws Exception {
        return singleToDeferredValue(commander.ping(message));
    }

    @Override
    public Deferred<String> psetex(@RedisProtocolSupport.Key final CharSequence key, final long milliseconds,
                                   final CharSequence value) throws Exception {
        return singleToDeferredValue(commander.psetex(key, milliseconds, value));
    }

    @Override
    public Deferred<Long> pttl(@RedisProtocolSupport.Key final CharSequence key) throws Exception {
        return singleToDeferredValue(commander.pttl(key));
    }

    @Override
    public Deferred<Long> publish(final CharSequence channel, final CharSequence message) throws Exception {
        return singleToDeferredValue(commander.publish(channel, message));
    }

    @Override
    public Deferred<List<String>> pubsubChannels() throws Exception {
        return singleToDeferredValue(commander.pubsubChannels());
    }

    @Override
    public Deferred<List<String>> pubsubChannels(@Nullable final CharSequence pattern) throws Exception {
        return singleToDeferredValue(commander.pubsubChannels(pattern));
    }

    @Override
    public Deferred<List<String>> pubsubChannels(@Nullable final CharSequence pattern1,
                                                 @Nullable final CharSequence pattern2) throws Exception {
        return singleToDeferredValue(commander.pubsubChannels(pattern1, pattern2));
    }

    @Override
    public Deferred<List<String>> pubsubChannels(@Nullable final CharSequence pattern1,
                                                 @Nullable final CharSequence pattern2,
                                                 @Nullable final CharSequence pattern3) throws Exception {
        return singleToDeferredValue(commander.pubsubChannels(pattern1, pattern2, pattern3));
    }

    @Override
    public Deferred<List<String>> pubsubChannels(final Collection<? extends CharSequence> patterns) throws Exception {
        return singleToDeferredValue(commander.pubsubChannels(patterns));
    }

    @Override
    public <T> Deferred<List<T>> pubsubNumsub() throws Exception {
        return singleToDeferredValue(commander.pubsubNumsub());
    }

    @Override
    public <T> Deferred<List<T>> pubsubNumsub(@Nullable final CharSequence channel) throws Exception {
        return singleToDeferredValue(commander.pubsubNumsub(channel));
    }

    @Override
    public <T> Deferred<List<T>> pubsubNumsub(@Nullable final CharSequence channel1,
                                              @Nullable final CharSequence channel2) throws Exception {
        return singleToDeferredValue(commander.pubsubNumsub(channel1, channel2));
    }

    @Override
    public <T> Deferred<List<T>> pubsubNumsub(@Nullable final CharSequence channel1,
                                              @Nullable final CharSequence channel2,
                                              @Nullable final CharSequence channel3) throws Exception {
        return singleToDeferredValue(commander.pubsubNumsub(channel1, channel2, channel3));
    }

    @Override
    public <T> Deferred<List<T>> pubsubNumsub(final Collection<? extends CharSequence> channels) throws Exception {
        return singleToDeferredValue(commander.pubsubNumsub(channels));
    }

    @Override
    public Deferred<Long> pubsubNumpat() throws Exception {
        return singleToDeferredValue(commander.pubsubNumpat());
    }

    @Override
    public Deferred<String> randomkey() throws Exception {
        return singleToDeferredValue(commander.randomkey());
    }

    @Override
    public Deferred<String> readonly() throws Exception {
        return singleToDeferredValue(commander.readonly());
    }

    @Override
    public Deferred<String> readwrite() throws Exception {
        return singleToDeferredValue(commander.readwrite());
    }

    @Override
    public Deferred<String> rename(@RedisProtocolSupport.Key final CharSequence key,
                                   @RedisProtocolSupport.Key final CharSequence newkey) throws Exception {
        return singleToDeferredValue(commander.rename(key, newkey));
    }

    @Override
    public Deferred<Long> renamenx(@RedisProtocolSupport.Key final CharSequence key,
                                   @RedisProtocolSupport.Key final CharSequence newkey) throws Exception {
        return singleToDeferredValue(commander.renamenx(key, newkey));
    }

    @Override
    public Deferred<String> restore(@RedisProtocolSupport.Key final CharSequence key, final long ttl,
                                    final CharSequence serializedValue) throws Exception {
        return singleToDeferredValue(commander.restore(key, ttl, serializedValue));
    }

    @Override
    public Deferred<String> restore(@RedisProtocolSupport.Key final CharSequence key, final long ttl,
                                    final CharSequence serializedValue,
                                    @Nullable final RedisProtocolSupport.RestoreReplace replace) throws Exception {
        return singleToDeferredValue(commander.restore(key, ttl, serializedValue, replace));
    }

    @Override
    public <T> Deferred<List<T>> role() throws Exception {
        return singleToDeferredValue(commander.role());
    }

    @Override
    public Deferred<String> rpop(@RedisProtocolSupport.Key final CharSequence key) throws Exception {
        return singleToDeferredValue(commander.rpop(key));
    }

    @Override
    public Deferred<String> rpoplpush(@RedisProtocolSupport.Key final CharSequence source,
                                      @RedisProtocolSupport.Key final CharSequence destination) throws Exception {
        return singleToDeferredValue(commander.rpoplpush(source, destination));
    }

    @Override
    public Deferred<Long> rpush(@RedisProtocolSupport.Key final CharSequence key,
                                final CharSequence value) throws Exception {
        return singleToDeferredValue(commander.rpush(key, value));
    }

    @Override
    public Deferred<Long> rpush(@RedisProtocolSupport.Key final CharSequence key, final CharSequence value1,
                                final CharSequence value2) throws Exception {
        return singleToDeferredValue(commander.rpush(key, value1, value2));
    }

    @Override
    public Deferred<Long> rpush(@RedisProtocolSupport.Key final CharSequence key, final CharSequence value1,
                                final CharSequence value2, final CharSequence value3) throws Exception {
        return singleToDeferredValue(commander.rpush(key, value1, value2, value3));
    }

    @Override
    public Deferred<Long> rpush(@RedisProtocolSupport.Key final CharSequence key,
                                final Collection<? extends CharSequence> values) throws Exception {
        return singleToDeferredValue(commander.rpush(key, values));
    }

    @Override
    public Deferred<Long> rpushx(@RedisProtocolSupport.Key final CharSequence key,
                                 final CharSequence value) throws Exception {
        return singleToDeferredValue(commander.rpushx(key, value));
    }

    @Override
    public Deferred<Long> sadd(@RedisProtocolSupport.Key final CharSequence key,
                               final CharSequence member) throws Exception {
        return singleToDeferredValue(commander.sadd(key, member));
    }

    @Override
    public Deferred<Long> sadd(@RedisProtocolSupport.Key final CharSequence key, final CharSequence member1,
                               final CharSequence member2) throws Exception {
        return singleToDeferredValue(commander.sadd(key, member1, member2));
    }

    @Override
    public Deferred<Long> sadd(@RedisProtocolSupport.Key final CharSequence key, final CharSequence member1,
                               final CharSequence member2, final CharSequence member3) throws Exception {
        return singleToDeferredValue(commander.sadd(key, member1, member2, member3));
    }

    @Override
    public Deferred<Long> sadd(@RedisProtocolSupport.Key final CharSequence key,
                               final Collection<? extends CharSequence> members) throws Exception {
        return singleToDeferredValue(commander.sadd(key, members));
    }

    @Override
    public Deferred<String> save() throws Exception {
        return singleToDeferredValue(commander.save());
    }

    @Override
    public <T> Deferred<List<T>> scan(final long cursor) throws Exception {
        return singleToDeferredValue(commander.scan(cursor));
    }

    @Override
    public <T> Deferred<List<T>> scan(final long cursor, @Nullable final CharSequence matchPattern,
                                      @Nullable final Long count) throws Exception {
        return singleToDeferredValue(commander.scan(cursor, matchPattern, count));
    }

    @Override
    public Deferred<Long> scard(@RedisProtocolSupport.Key final CharSequence key) throws Exception {
        return singleToDeferredValue(commander.scard(key));
    }

    @Override
    public Deferred<String> scriptDebug(final RedisProtocolSupport.ScriptDebugMode mode) throws Exception {
        return singleToDeferredValue(commander.scriptDebug(mode));
    }

    @Override
    public <T> Deferred<List<T>> scriptExists(final CharSequence sha1) throws Exception {
        return singleToDeferredValue(commander.scriptExists(sha1));
    }

    @Override
    public <T> Deferred<List<T>> scriptExists(final CharSequence sha11, final CharSequence sha12) throws Exception {
        return singleToDeferredValue(commander.scriptExists(sha11, sha12));
    }

    @Override
    public <T> Deferred<List<T>> scriptExists(final CharSequence sha11, final CharSequence sha12,
                                              final CharSequence sha13) throws Exception {
        return singleToDeferredValue(commander.scriptExists(sha11, sha12, sha13));
    }

    @Override
    public <T> Deferred<List<T>> scriptExists(final Collection<? extends CharSequence> sha1s) throws Exception {
        return singleToDeferredValue(commander.scriptExists(sha1s));
    }

    @Override
    public Deferred<String> scriptFlush() throws Exception {
        return singleToDeferredValue(commander.scriptFlush());
    }

    @Override
    public Deferred<String> scriptKill() throws Exception {
        return singleToDeferredValue(commander.scriptKill());
    }

    @Override
    public Deferred<String> scriptLoad(final CharSequence script) throws Exception {
        return singleToDeferredValue(commander.scriptLoad(script));
    }

    @Override
    public <T> Deferred<List<T>> sdiff(@RedisProtocolSupport.Key final CharSequence firstkey) throws Exception {
        return singleToDeferredValue(commander.sdiff(firstkey));
    }

    @Override
    public <T> Deferred<List<T>> sdiff(@RedisProtocolSupport.Key final CharSequence firstkey,
                                       @Nullable @RedisProtocolSupport.Key final CharSequence otherkey) throws Exception {
        return singleToDeferredValue(commander.sdiff(firstkey, otherkey));
    }

    @Override
    public <T> Deferred<List<T>> sdiff(@RedisProtocolSupport.Key final CharSequence firstkey,
                                       @Nullable @RedisProtocolSupport.Key final CharSequence otherkey1,
                                       @Nullable @RedisProtocolSupport.Key final CharSequence otherkey2) throws Exception {
        return singleToDeferredValue(commander.sdiff(firstkey, otherkey1, otherkey2));
    }

    @Override
    public <T> Deferred<List<T>> sdiff(@RedisProtocolSupport.Key final CharSequence firstkey,
                                       @Nullable @RedisProtocolSupport.Key final CharSequence otherkey1,
                                       @Nullable @RedisProtocolSupport.Key final CharSequence otherkey2,
                                       @Nullable @RedisProtocolSupport.Key final CharSequence otherkey3) throws Exception {
        return singleToDeferredValue(commander.sdiff(firstkey, otherkey1, otherkey2, otherkey3));
    }

    @Override
    public <T> Deferred<List<T>> sdiff(@RedisProtocolSupport.Key final CharSequence firstkey,
                                       @RedisProtocolSupport.Key final Collection<? extends CharSequence> otherkeys) throws Exception {
        return singleToDeferredValue(commander.sdiff(firstkey, otherkeys));
    }

    @Override
    public Deferred<Long> sdiffstore(@RedisProtocolSupport.Key final CharSequence destination,
                                     @RedisProtocolSupport.Key final CharSequence firstkey) throws Exception {
        return singleToDeferredValue(commander.sdiffstore(destination, firstkey));
    }

    @Override
    public Deferred<Long> sdiffstore(@RedisProtocolSupport.Key final CharSequence destination,
                                     @RedisProtocolSupport.Key final CharSequence firstkey,
                                     @Nullable @RedisProtocolSupport.Key final CharSequence otherkey) throws Exception {
        return singleToDeferredValue(commander.sdiffstore(destination, firstkey, otherkey));
    }

    @Override
    public Deferred<Long> sdiffstore(@RedisProtocolSupport.Key final CharSequence destination,
                                     @RedisProtocolSupport.Key final CharSequence firstkey,
                                     @Nullable @RedisProtocolSupport.Key final CharSequence otherkey1,
                                     @Nullable @RedisProtocolSupport.Key final CharSequence otherkey2) throws Exception {
        return singleToDeferredValue(commander.sdiffstore(destination, firstkey, otherkey1, otherkey2));
    }

    @Override
    public Deferred<Long> sdiffstore(@RedisProtocolSupport.Key final CharSequence destination,
                                     @RedisProtocolSupport.Key final CharSequence firstkey,
                                     @Nullable @RedisProtocolSupport.Key final CharSequence otherkey1,
                                     @Nullable @RedisProtocolSupport.Key final CharSequence otherkey2,
                                     @Nullable @RedisProtocolSupport.Key final CharSequence otherkey3) throws Exception {
        return singleToDeferredValue(commander.sdiffstore(destination, firstkey, otherkey1, otherkey2, otherkey3));
    }

    @Override
    public Deferred<Long> sdiffstore(@RedisProtocolSupport.Key final CharSequence destination,
                                     @RedisProtocolSupport.Key final CharSequence firstkey,
                                     @RedisProtocolSupport.Key final Collection<? extends CharSequence> otherkeys) throws Exception {
        return singleToDeferredValue(commander.sdiffstore(destination, firstkey, otherkeys));
    }

    @Override
    public Deferred<String> select(final long index) throws Exception {
        return singleToDeferredValue(commander.select(index));
    }

    @Override
    public Deferred<String> set(@RedisProtocolSupport.Key final CharSequence key,
                                final CharSequence value) throws Exception {
        return singleToDeferredValue(commander.set(key, value));
    }

    @Override
    public Deferred<String> set(@RedisProtocolSupport.Key final CharSequence key, final CharSequence value,
                                @Nullable final RedisProtocolSupport.ExpireDuration expireDuration,
                                @Nullable final RedisProtocolSupport.SetCondition condition) throws Exception {
        return singleToDeferredValue(commander.set(key, value, expireDuration, condition));
    }

    @Override
    public Deferred<Long> setbit(@RedisProtocolSupport.Key final CharSequence key, final long offset,
                                 final CharSequence value) throws Exception {
        return singleToDeferredValue(commander.setbit(key, offset, value));
    }

    @Override
    public Deferred<String> setex(@RedisProtocolSupport.Key final CharSequence key, final long seconds,
                                  final CharSequence value) throws Exception {
        return singleToDeferredValue(commander.setex(key, seconds, value));
    }

    @Override
    public Deferred<Long> setnx(@RedisProtocolSupport.Key final CharSequence key,
                                final CharSequence value) throws Exception {
        return singleToDeferredValue(commander.setnx(key, value));
    }

    @Override
    public Deferred<Long> setrange(@RedisProtocolSupport.Key final CharSequence key, final long offset,
                                   final CharSequence value) throws Exception {
        return singleToDeferredValue(commander.setrange(key, offset, value));
    }

    @Override
    public Deferred<String> shutdown() throws Exception {
        return singleToDeferredValue(commander.shutdown());
    }

    @Override
    public Deferred<String> shutdown(@Nullable final RedisProtocolSupport.ShutdownSaveMode saveMode) throws Exception {
        return singleToDeferredValue(commander.shutdown(saveMode));
    }

    @Override
    public <T> Deferred<List<T>> sinter(@RedisProtocolSupport.Key final CharSequence key) throws Exception {
        return singleToDeferredValue(commander.sinter(key));
    }

    @Override
    public <T> Deferred<List<T>> sinter(@RedisProtocolSupport.Key final CharSequence key1,
                                        @RedisProtocolSupport.Key final CharSequence key2) throws Exception {
        return singleToDeferredValue(commander.sinter(key1, key2));
    }

    @Override
    public <T> Deferred<List<T>> sinter(@RedisProtocolSupport.Key final CharSequence key1,
                                        @RedisProtocolSupport.Key final CharSequence key2,
                                        @RedisProtocolSupport.Key final CharSequence key3) throws Exception {
        return singleToDeferredValue(commander.sinter(key1, key2, key3));
    }

    @Override
    public <T> Deferred<List<T>> sinter(@RedisProtocolSupport.Key final Collection<? extends CharSequence> keys) throws Exception {
        return singleToDeferredValue(commander.sinter(keys));
    }

    @Override
    public Deferred<Long> sinterstore(@RedisProtocolSupport.Key final CharSequence destination,
                                      @RedisProtocolSupport.Key final CharSequence key) throws Exception {
        return singleToDeferredValue(commander.sinterstore(destination, key));
    }

    @Override
    public Deferred<Long> sinterstore(@RedisProtocolSupport.Key final CharSequence destination,
                                      @RedisProtocolSupport.Key final CharSequence key1,
                                      @RedisProtocolSupport.Key final CharSequence key2) throws Exception {
        return singleToDeferredValue(commander.sinterstore(destination, key1, key2));
    }

    @Override
    public Deferred<Long> sinterstore(@RedisProtocolSupport.Key final CharSequence destination,
                                      @RedisProtocolSupport.Key final CharSequence key1,
                                      @RedisProtocolSupport.Key final CharSequence key2,
                                      @RedisProtocolSupport.Key final CharSequence key3) throws Exception {
        return singleToDeferredValue(commander.sinterstore(destination, key1, key2, key3));
    }

    @Override
    public Deferred<Long> sinterstore(@RedisProtocolSupport.Key final CharSequence destination,
                                      @RedisProtocolSupport.Key final Collection<? extends CharSequence> keys) throws Exception {
        return singleToDeferredValue(commander.sinterstore(destination, keys));
    }

    @Override
    public Deferred<Long> sismember(@RedisProtocolSupport.Key final CharSequence key,
                                    final CharSequence member) throws Exception {
        return singleToDeferredValue(commander.sismember(key, member));
    }

    @Override
    public Deferred<String> slaveof(final CharSequence host, final CharSequence port) throws Exception {
        return singleToDeferredValue(commander.slaveof(host, port));
    }

    @Override
    public <T> Deferred<List<T>> slowlog(final CharSequence subcommand) throws Exception {
        return singleToDeferredValue(commander.slowlog(subcommand));
    }

    @Override
    public <T> Deferred<List<T>> slowlog(final CharSequence subcommand,
                                         @Nullable final CharSequence argument) throws Exception {
        return singleToDeferredValue(commander.slowlog(subcommand, argument));
    }

    @Override
    public <T> Deferred<List<T>> smembers(@RedisProtocolSupport.Key final CharSequence key) throws Exception {
        return singleToDeferredValue(commander.smembers(key));
    }

    @Override
    public Deferred<Long> smove(@RedisProtocolSupport.Key final CharSequence source,
                                @RedisProtocolSupport.Key final CharSequence destination,
                                final CharSequence member) throws Exception {
        return singleToDeferredValue(commander.smove(source, destination, member));
    }

    @Override
    public <T> Deferred<List<T>> sort(@RedisProtocolSupport.Key final CharSequence key) throws Exception {
        return singleToDeferredValue(commander.sort(key));
    }

    @Override
    public <T> Deferred<List<T>> sort(@RedisProtocolSupport.Key final CharSequence key,
                                      @Nullable final CharSequence byPattern,
                                      @Nullable final RedisProtocolSupport.OffsetCount offsetCount,
                                      final Collection<? extends CharSequence> getPatterns,
                                      @Nullable final RedisProtocolSupport.SortOrder order,
                                      @Nullable final RedisProtocolSupport.SortSorting sorting) throws Exception {
        return singleToDeferredValue(commander.sort(key, byPattern, offsetCount, getPatterns, order, sorting));
    }

    @Override
    public Deferred<Long> sort(@RedisProtocolSupport.Key final CharSequence key,
                               @RedisProtocolSupport.Key final CharSequence storeDestination) throws Exception {
        return singleToDeferredValue(commander.sort(key, storeDestination));
    }

    @Override
    public Deferred<Long> sort(@RedisProtocolSupport.Key final CharSequence key,
                               @RedisProtocolSupport.Key final CharSequence storeDestination,
                               @Nullable final CharSequence byPattern,
                               @Nullable final RedisProtocolSupport.OffsetCount offsetCount,
                               final Collection<? extends CharSequence> getPatterns,
                               @Nullable final RedisProtocolSupport.SortOrder order,
                               @Nullable final RedisProtocolSupport.SortSorting sorting) throws Exception {
        return singleToDeferredValue(
                    commander.sort(key, storeDestination, byPattern, offsetCount, getPatterns, order, sorting));
    }

    @Override
    public Deferred<String> spop(@RedisProtocolSupport.Key final CharSequence key) throws Exception {
        return singleToDeferredValue(commander.spop(key));
    }

    @Override
    public Deferred<String> spop(@RedisProtocolSupport.Key final CharSequence key,
                                 @Nullable final Long count) throws Exception {
        return singleToDeferredValue(commander.spop(key, count));
    }

    @Override
    public Deferred<String> srandmember(@RedisProtocolSupport.Key final CharSequence key) throws Exception {
        return singleToDeferredValue(commander.srandmember(key));
    }

    @Override
    public Deferred<List<String>> srandmember(@RedisProtocolSupport.Key final CharSequence key,
                                              final long count) throws Exception {
        return singleToDeferredValue(commander.srandmember(key, count));
    }

    @Override
    public Deferred<Long> srem(@RedisProtocolSupport.Key final CharSequence key,
                               final CharSequence member) throws Exception {
        return singleToDeferredValue(commander.srem(key, member));
    }

    @Override
    public Deferred<Long> srem(@RedisProtocolSupport.Key final CharSequence key, final CharSequence member1,
                               final CharSequence member2) throws Exception {
        return singleToDeferredValue(commander.srem(key, member1, member2));
    }

    @Override
    public Deferred<Long> srem(@RedisProtocolSupport.Key final CharSequence key, final CharSequence member1,
                               final CharSequence member2, final CharSequence member3) throws Exception {
        return singleToDeferredValue(commander.srem(key, member1, member2, member3));
    }

    @Override
    public Deferred<Long> srem(@RedisProtocolSupport.Key final CharSequence key,
                               final Collection<? extends CharSequence> members) throws Exception {
        return singleToDeferredValue(commander.srem(key, members));
    }

    @Override
    public <T> Deferred<List<T>> sscan(@RedisProtocolSupport.Key final CharSequence key,
                                       final long cursor) throws Exception {
        return singleToDeferredValue(commander.sscan(key, cursor));
    }

    @Override
    public <T> Deferred<List<T>> sscan(@RedisProtocolSupport.Key final CharSequence key, final long cursor,
                                       @Nullable final CharSequence matchPattern,
                                       @Nullable final Long count) throws Exception {
        return singleToDeferredValue(commander.sscan(key, cursor, matchPattern, count));
    }

    @Override
    public Deferred<Long> strlen(@RedisProtocolSupport.Key final CharSequence key) throws Exception {
        return singleToDeferredValue(commander.strlen(key));
    }

    @Override
    public <T> Deferred<List<T>> sunion(@RedisProtocolSupport.Key final CharSequence key) throws Exception {
        return singleToDeferredValue(commander.sunion(key));
    }

    @Override
    public <T> Deferred<List<T>> sunion(@RedisProtocolSupport.Key final CharSequence key1,
                                        @RedisProtocolSupport.Key final CharSequence key2) throws Exception {
        return singleToDeferredValue(commander.sunion(key1, key2));
    }

    @Override
    public <T> Deferred<List<T>> sunion(@RedisProtocolSupport.Key final CharSequence key1,
                                        @RedisProtocolSupport.Key final CharSequence key2,
                                        @RedisProtocolSupport.Key final CharSequence key3) throws Exception {
        return singleToDeferredValue(commander.sunion(key1, key2, key3));
    }

    @Override
    public <T> Deferred<List<T>> sunion(@RedisProtocolSupport.Key final Collection<? extends CharSequence> keys) throws Exception {
        return singleToDeferredValue(commander.sunion(keys));
    }

    @Override
    public Deferred<Long> sunionstore(@RedisProtocolSupport.Key final CharSequence destination,
                                      @RedisProtocolSupport.Key final CharSequence key) throws Exception {
        return singleToDeferredValue(commander.sunionstore(destination, key));
    }

    @Override
    public Deferred<Long> sunionstore(@RedisProtocolSupport.Key final CharSequence destination,
                                      @RedisProtocolSupport.Key final CharSequence key1,
                                      @RedisProtocolSupport.Key final CharSequence key2) throws Exception {
        return singleToDeferredValue(commander.sunionstore(destination, key1, key2));
    }

    @Override
    public Deferred<Long> sunionstore(@RedisProtocolSupport.Key final CharSequence destination,
                                      @RedisProtocolSupport.Key final CharSequence key1,
                                      @RedisProtocolSupport.Key final CharSequence key2,
                                      @RedisProtocolSupport.Key final CharSequence key3) throws Exception {
        return singleToDeferredValue(commander.sunionstore(destination, key1, key2, key3));
    }

    @Override
    public Deferred<Long> sunionstore(@RedisProtocolSupport.Key final CharSequence destination,
                                      @RedisProtocolSupport.Key final Collection<? extends CharSequence> keys) throws Exception {
        return singleToDeferredValue(commander.sunionstore(destination, keys));
    }

    @Override
    public Deferred<String> swapdb(final long index, final long index1) throws Exception {
        return singleToDeferredValue(commander.swapdb(index, index1));
    }

    @Override
    public <T> Deferred<List<T>> time() throws Exception {
        return singleToDeferredValue(commander.time());
    }

    @Override
    public Deferred<Long> touch(@RedisProtocolSupport.Key final CharSequence key) throws Exception {
        return singleToDeferredValue(commander.touch(key));
    }

    @Override
    public Deferred<Long> touch(@RedisProtocolSupport.Key final CharSequence key1,
                                @RedisProtocolSupport.Key final CharSequence key2) throws Exception {
        return singleToDeferredValue(commander.touch(key1, key2));
    }

    @Override
    public Deferred<Long> touch(@RedisProtocolSupport.Key final CharSequence key1,
                                @RedisProtocolSupport.Key final CharSequence key2,
                                @RedisProtocolSupport.Key final CharSequence key3) throws Exception {
        return singleToDeferredValue(commander.touch(key1, key2, key3));
    }

    @Override
    public Deferred<Long> touch(@RedisProtocolSupport.Key final Collection<? extends CharSequence> keys) throws Exception {
        return singleToDeferredValue(commander.touch(keys));
    }

    @Override
    public Deferred<Long> ttl(@RedisProtocolSupport.Key final CharSequence key) throws Exception {
        return singleToDeferredValue(commander.ttl(key));
    }

    @Override
    public Deferred<String> type(@RedisProtocolSupport.Key final CharSequence key) throws Exception {
        return singleToDeferredValue(commander.type(key));
    }

    @Override
    public Deferred<Long> unlink(@RedisProtocolSupport.Key final CharSequence key) throws Exception {
        return singleToDeferredValue(commander.unlink(key));
    }

    @Override
    public Deferred<Long> unlink(@RedisProtocolSupport.Key final CharSequence key1,
                                 @RedisProtocolSupport.Key final CharSequence key2) throws Exception {
        return singleToDeferredValue(commander.unlink(key1, key2));
    }

    @Override
    public Deferred<Long> unlink(@RedisProtocolSupport.Key final CharSequence key1,
                                 @RedisProtocolSupport.Key final CharSequence key2,
                                 @RedisProtocolSupport.Key final CharSequence key3) throws Exception {
        return singleToDeferredValue(commander.unlink(key1, key2, key3));
    }

    @Override
    public Deferred<Long> unlink(@RedisProtocolSupport.Key final Collection<? extends CharSequence> keys) throws Exception {
        return singleToDeferredValue(commander.unlink(keys));
    }

    @Override
    public Deferred<String> unwatch() throws Exception {
        return singleToDeferredValue(commander.unwatch());
    }

    @Override
    public Deferred<Long> wait(final long numslaves, final long timeout) throws Exception {
        return singleToDeferredValue(commander.wait(numslaves, timeout));
    }

    @Override
    public Deferred<String> watch(@RedisProtocolSupport.Key final CharSequence key) throws Exception {
        return singleToDeferredValue(commander.watch(key));
    }

    @Override
    public Deferred<String> watch(@RedisProtocolSupport.Key final CharSequence key1,
                                  @RedisProtocolSupport.Key final CharSequence key2) throws Exception {
        return singleToDeferredValue(commander.watch(key1, key2));
    }

    @Override
    public Deferred<String> watch(@RedisProtocolSupport.Key final CharSequence key1,
                                  @RedisProtocolSupport.Key final CharSequence key2,
                                  @RedisProtocolSupport.Key final CharSequence key3) throws Exception {
        return singleToDeferredValue(commander.watch(key1, key2, key3));
    }

    @Override
    public Deferred<String> watch(@RedisProtocolSupport.Key final Collection<? extends CharSequence> keys) throws Exception {
        return singleToDeferredValue(commander.watch(keys));
    }

    @Override
    public Deferred<String> xadd(@RedisProtocolSupport.Key final CharSequence key, final CharSequence id,
                                 final CharSequence field, final CharSequence value) throws Exception {
        return singleToDeferredValue(commander.xadd(key, id, field, value));
    }

    @Override
    public Deferred<String> xadd(@RedisProtocolSupport.Key final CharSequence key, final CharSequence id,
                                 final CharSequence field1, final CharSequence value1, final CharSequence field2,
                                 final CharSequence value2) throws Exception {
        return singleToDeferredValue(commander.xadd(key, id, field1, value1, field2, value2));
    }

    @Override
    public Deferred<String> xadd(@RedisProtocolSupport.Key final CharSequence key, final CharSequence id,
                                 final CharSequence field1, final CharSequence value1, final CharSequence field2,
                                 final CharSequence value2, final CharSequence field3,
                                 final CharSequence value3) throws Exception {
        return singleToDeferredValue(commander.xadd(key, id, field1, value1, field2, value2, field3, value3));
    }

    @Override
    public Deferred<String> xadd(@RedisProtocolSupport.Key final CharSequence key, final CharSequence id,
                                 final Collection<RedisProtocolSupport.FieldValue> fieldValues) throws Exception {
        return singleToDeferredValue(commander.xadd(key, id, fieldValues));
    }

    @Override
    public Deferred<Long> xlen(@RedisProtocolSupport.Key final CharSequence key) throws Exception {
        return singleToDeferredValue(commander.xlen(key));
    }

    @Override
    public <T> Deferred<List<T>> xpending(@RedisProtocolSupport.Key final CharSequence key,
                                          final CharSequence group) throws Exception {
        return singleToDeferredValue(commander.xpending(key, group));
    }

    @Override
    public <T> Deferred<List<T>> xpending(@RedisProtocolSupport.Key final CharSequence key, final CharSequence group,
                                          @Nullable final CharSequence start, @Nullable final CharSequence end,
                                          @Nullable final Long count,
                                          @Nullable final CharSequence consumer) throws Exception {
        return singleToDeferredValue(commander.xpending(key, group, start, end, count, consumer));
    }

    @Override
    public <T> Deferred<List<T>> xrange(@RedisProtocolSupport.Key final CharSequence key, final CharSequence start,
                                        final CharSequence end) throws Exception {
        return singleToDeferredValue(commander.xrange(key, start, end));
    }

    @Override
    public <T> Deferred<List<T>> xrange(@RedisProtocolSupport.Key final CharSequence key, final CharSequence start,
                                        final CharSequence end, @Nullable final Long count) throws Exception {
        return singleToDeferredValue(commander.xrange(key, start, end, count));
    }

    @Override
    public <T> Deferred<List<T>> xread(@RedisProtocolSupport.Key final Collection<? extends CharSequence> keys,
                                       final Collection<? extends CharSequence> ids) throws Exception {
        return singleToDeferredValue(commander.xread(keys, ids));
    }

    @Override
    public <T> Deferred<List<T>> xread(@Nullable final Long count, @Nullable final Long blockMilliseconds,
                                       @RedisProtocolSupport.Key final Collection<? extends CharSequence> keys,
                                       final Collection<? extends CharSequence> ids) throws Exception {
        return singleToDeferredValue(commander.xread(count, blockMilliseconds, keys, ids));
    }

    @Override
    public <T> Deferred<List<T>> xreadgroup(final RedisProtocolSupport.GroupConsumer groupConsumer,
                                            @RedisProtocolSupport.Key final Collection<? extends CharSequence> keys,
                                            final Collection<? extends CharSequence> ids) throws Exception {
        return singleToDeferredValue(commander.xreadgroup(groupConsumer, keys, ids));
    }

    @Override
    public <T> Deferred<List<T>> xreadgroup(final RedisProtocolSupport.GroupConsumer groupConsumer,
                                            @Nullable final Long count, @Nullable final Long blockMilliseconds,
                                            @RedisProtocolSupport.Key final Collection<? extends CharSequence> keys,
                                            final Collection<? extends CharSequence> ids) throws Exception {
        return singleToDeferredValue(commander.xreadgroup(groupConsumer, count, blockMilliseconds, keys, ids));
    }

    @Override
    public <T> Deferred<List<T>> xrevrange(@RedisProtocolSupport.Key final CharSequence key, final CharSequence end,
                                           final CharSequence start) throws Exception {
        return singleToDeferredValue(commander.xrevrange(key, end, start));
    }

    @Override
    public <T> Deferred<List<T>> xrevrange(@RedisProtocolSupport.Key final CharSequence key, final CharSequence end,
                                           final CharSequence start, @Nullable final Long count) throws Exception {
        return singleToDeferredValue(commander.xrevrange(key, end, start, count));
    }

    @Override
    public Deferred<Long> zadd(@RedisProtocolSupport.Key final CharSequence key,
                               final Collection<RedisProtocolSupport.ScoreMember> scoreMembers) throws Exception {
        return singleToDeferredValue(commander.zadd(key, scoreMembers));
    }

    @Override
    public Deferred<Long> zadd(@RedisProtocolSupport.Key final CharSequence key,
                               @Nullable final RedisProtocolSupport.ZaddCondition condition,
                               @Nullable final RedisProtocolSupport.ZaddChange change, final double score,
                               final CharSequence member) throws Exception {
        return singleToDeferredValue(commander.zadd(key, condition, change, score, member));
    }

    @Override
    public Deferred<Long> zadd(@RedisProtocolSupport.Key final CharSequence key,
                               @Nullable final RedisProtocolSupport.ZaddCondition condition,
                               @Nullable final RedisProtocolSupport.ZaddChange change, final double score1,
                               final CharSequence member1, final double score2,
                               final CharSequence member2) throws Exception {
        return singleToDeferredValue(commander.zadd(key, condition, change, score1, member1, score2, member2));
    }

    @Override
    public Deferred<Long> zadd(@RedisProtocolSupport.Key final CharSequence key,
                               @Nullable final RedisProtocolSupport.ZaddCondition condition,
                               @Nullable final RedisProtocolSupport.ZaddChange change, final double score1,
                               final CharSequence member1, final double score2, final CharSequence member2,
                               final double score3, final CharSequence member3) throws Exception {
        return singleToDeferredValue(
                    commander.zadd(key, condition, change, score1, member1, score2, member2, score3, member3));
    }

    @Override
    public Deferred<Long> zadd(@RedisProtocolSupport.Key final CharSequence key,
                               @Nullable final RedisProtocolSupport.ZaddCondition condition,
                               @Nullable final RedisProtocolSupport.ZaddChange change,
                               final Collection<RedisProtocolSupport.ScoreMember> scoreMembers) throws Exception {
        return singleToDeferredValue(commander.zadd(key, condition, change, scoreMembers));
    }

    @Override
    public Deferred<Double> zaddIncr(@RedisProtocolSupport.Key final CharSequence key,
                                     final Collection<RedisProtocolSupport.ScoreMember> scoreMembers) throws Exception {
        return singleToDeferredValue(commander.zaddIncr(key, scoreMembers));
    }

    @Override
    public Deferred<Double> zaddIncr(@RedisProtocolSupport.Key final CharSequence key,
                                     @Nullable final RedisProtocolSupport.ZaddCondition condition,
                                     @Nullable final RedisProtocolSupport.ZaddChange change, final double score,
                                     final CharSequence member) throws Exception {
        return singleToDeferredValue(commander.zaddIncr(key, condition, change, score, member));
    }

    @Override
    public Deferred<Double> zaddIncr(@RedisProtocolSupport.Key final CharSequence key,
                                     @Nullable final RedisProtocolSupport.ZaddCondition condition,
                                     @Nullable final RedisProtocolSupport.ZaddChange change, final double score1,
                                     final CharSequence member1, final double score2,
                                     final CharSequence member2) throws Exception {
        return singleToDeferredValue(commander.zaddIncr(key, condition, change, score1, member1, score2, member2));
    }

    @Override
    public Deferred<Double> zaddIncr(@RedisProtocolSupport.Key final CharSequence key,
                                     @Nullable final RedisProtocolSupport.ZaddCondition condition,
                                     @Nullable final RedisProtocolSupport.ZaddChange change, final double score1,
                                     final CharSequence member1, final double score2, final CharSequence member2,
                                     final double score3, final CharSequence member3) throws Exception {
        return singleToDeferredValue(
                    commander.zaddIncr(key, condition, change, score1, member1, score2, member2, score3, member3));
    }

    @Override
    public Deferred<Double> zaddIncr(@RedisProtocolSupport.Key final CharSequence key,
                                     @Nullable final RedisProtocolSupport.ZaddCondition condition,
                                     @Nullable final RedisProtocolSupport.ZaddChange change,
                                     final Collection<RedisProtocolSupport.ScoreMember> scoreMembers) throws Exception {
        return singleToDeferredValue(commander.zaddIncr(key, condition, change, scoreMembers));
    }

    @Override
    public Deferred<Long> zcard(@RedisProtocolSupport.Key final CharSequence key) throws Exception {
        return singleToDeferredValue(commander.zcard(key));
    }

    @Override
    public Deferred<Long> zcount(@RedisProtocolSupport.Key final CharSequence key, final double min,
                                 final double max) throws Exception {
        return singleToDeferredValue(commander.zcount(key, min, max));
    }

    @Override
    public Deferred<Double> zincrby(@RedisProtocolSupport.Key final CharSequence key, final long increment,
                                    final CharSequence member) throws Exception {
        return singleToDeferredValue(commander.zincrby(key, increment, member));
    }

    @Override
    public Deferred<Long> zinterstore(@RedisProtocolSupport.Key final CharSequence destination, final long numkeys,
                                      @RedisProtocolSupport.Key final Collection<? extends CharSequence> keys) throws Exception {
        return singleToDeferredValue(commander.zinterstore(destination, numkeys, keys));
    }

    @Override
    public Deferred<Long> zinterstore(@RedisProtocolSupport.Key final CharSequence destination, final long numkeys,
                                      @RedisProtocolSupport.Key final Collection<? extends CharSequence> keys,
                                      final Collection<Long> weightses,
                                      @Nullable final RedisProtocolSupport.ZinterstoreAggregate aggregate) throws Exception {
        return singleToDeferredValue(commander.zinterstore(destination, numkeys, keys, weightses, aggregate));
    }

    @Override
    public Deferred<Long> zlexcount(@RedisProtocolSupport.Key final CharSequence key, final CharSequence min,
                                    final CharSequence max) throws Exception {
        return singleToDeferredValue(commander.zlexcount(key, min, max));
    }

    @Override
    public <T> Deferred<List<T>> zpopmax(@RedisProtocolSupport.Key final CharSequence key) throws Exception {
        return singleToDeferredValue(commander.zpopmax(key));
    }

    @Override
    public <T> Deferred<List<T>> zpopmax(@RedisProtocolSupport.Key final CharSequence key,
                                         @Nullable final Long count) throws Exception {
        return singleToDeferredValue(commander.zpopmax(key, count));
    }

    @Override
    public <T> Deferred<List<T>> zpopmin(@RedisProtocolSupport.Key final CharSequence key) throws Exception {
        return singleToDeferredValue(commander.zpopmin(key));
    }

    @Override
    public <T> Deferred<List<T>> zpopmin(@RedisProtocolSupport.Key final CharSequence key,
                                         @Nullable final Long count) throws Exception {
        return singleToDeferredValue(commander.zpopmin(key, count));
    }

    @Override
    public <T> Deferred<List<T>> zrange(@RedisProtocolSupport.Key final CharSequence key, final long start,
                                        final long stop) throws Exception {
        return singleToDeferredValue(commander.zrange(key, start, stop));
    }

    @Override
    public <T> Deferred<List<T>> zrange(@RedisProtocolSupport.Key final CharSequence key, final long start,
                                        final long stop,
                                        @Nullable final RedisProtocolSupport.ZrangeWithscores withscores) throws Exception {
        return singleToDeferredValue(commander.zrange(key, start, stop, withscores));
    }

    @Override
    public <T> Deferred<List<T>> zrangebylex(@RedisProtocolSupport.Key final CharSequence key, final CharSequence min,
                                             final CharSequence max) throws Exception {
        return singleToDeferredValue(commander.zrangebylex(key, min, max));
    }

    @Override
    public <T> Deferred<List<T>> zrangebylex(@RedisProtocolSupport.Key final CharSequence key, final CharSequence min,
                                             final CharSequence max,
                                             @Nullable final RedisProtocolSupport.OffsetCount offsetCount) throws Exception {
        return singleToDeferredValue(commander.zrangebylex(key, min, max, offsetCount));
    }

    @Override
    public <T> Deferred<List<T>> zrangebyscore(@RedisProtocolSupport.Key final CharSequence key, final double min,
                                               final double max) throws Exception {
        return singleToDeferredValue(commander.zrangebyscore(key, min, max));
    }

    @Override
    public <T> Deferred<List<T>> zrangebyscore(@RedisProtocolSupport.Key final CharSequence key, final double min,
                                               final double max,
                                               @Nullable final RedisProtocolSupport.ZrangebyscoreWithscores withscores,
                                               @Nullable final RedisProtocolSupport.OffsetCount offsetCount) throws Exception {
        return singleToDeferredValue(commander.zrangebyscore(key, min, max, withscores, offsetCount));
    }

    @Override
    public Deferred<Long> zrank(@RedisProtocolSupport.Key final CharSequence key,
                                final CharSequence member) throws Exception {
        return singleToDeferredValue(commander.zrank(key, member));
    }

    @Override
    public Deferred<Long> zrem(@RedisProtocolSupport.Key final CharSequence key,
                               final CharSequence member) throws Exception {
        return singleToDeferredValue(commander.zrem(key, member));
    }

    @Override
    public Deferred<Long> zrem(@RedisProtocolSupport.Key final CharSequence key, final CharSequence member1,
                               final CharSequence member2) throws Exception {
        return singleToDeferredValue(commander.zrem(key, member1, member2));
    }

    @Override
    public Deferred<Long> zrem(@RedisProtocolSupport.Key final CharSequence key, final CharSequence member1,
                               final CharSequence member2, final CharSequence member3) throws Exception {
        return singleToDeferredValue(commander.zrem(key, member1, member2, member3));
    }

    @Override
    public Deferred<Long> zrem(@RedisProtocolSupport.Key final CharSequence key,
                               final Collection<? extends CharSequence> members) throws Exception {
        return singleToDeferredValue(commander.zrem(key, members));
    }

    @Override
    public Deferred<Long> zremrangebylex(@RedisProtocolSupport.Key final CharSequence key, final CharSequence min,
                                         final CharSequence max) throws Exception {
        return singleToDeferredValue(commander.zremrangebylex(key, min, max));
    }

    @Override
    public Deferred<Long> zremrangebyrank(@RedisProtocolSupport.Key final CharSequence key, final long start,
                                          final long stop) throws Exception {
        return singleToDeferredValue(commander.zremrangebyrank(key, start, stop));
    }

    @Override
    public Deferred<Long> zremrangebyscore(@RedisProtocolSupport.Key final CharSequence key, final double min,
                                           final double max) throws Exception {
        return singleToDeferredValue(commander.zremrangebyscore(key, min, max));
    }

    @Override
    public <T> Deferred<List<T>> zrevrange(@RedisProtocolSupport.Key final CharSequence key, final long start,
                                           final long stop) throws Exception {
        return singleToDeferredValue(commander.zrevrange(key, start, stop));
    }

    @Override
    public <T> Deferred<List<T>> zrevrange(@RedisProtocolSupport.Key final CharSequence key, final long start,
                                           final long stop,
                                           @Nullable final RedisProtocolSupport.ZrevrangeWithscores withscores) throws Exception {
        return singleToDeferredValue(commander.zrevrange(key, start, stop, withscores));
    }

    @Override
    public <T> Deferred<List<T>> zrevrangebylex(@RedisProtocolSupport.Key final CharSequence key,
                                                final CharSequence max, final CharSequence min) throws Exception {
        return singleToDeferredValue(commander.zrevrangebylex(key, max, min));
    }

    @Override
    public <T> Deferred<List<T>> zrevrangebylex(@RedisProtocolSupport.Key final CharSequence key,
                                                final CharSequence max, final CharSequence min,
                                                @Nullable final RedisProtocolSupport.OffsetCount offsetCount) throws Exception {
        return singleToDeferredValue(commander.zrevrangebylex(key, max, min, offsetCount));
    }

    @Override
    public <T> Deferred<List<T>> zrevrangebyscore(@RedisProtocolSupport.Key final CharSequence key, final double max,
                                                  final double min) throws Exception {
        return singleToDeferredValue(commander.zrevrangebyscore(key, max, min));
    }

    @Override
    public <T> Deferred<List<T>> zrevrangebyscore(@RedisProtocolSupport.Key final CharSequence key, final double max,
                                                  final double min,
                                                  @Nullable final RedisProtocolSupport.ZrevrangebyscoreWithscores withscores,
                                                  @Nullable final RedisProtocolSupport.OffsetCount offsetCount) throws Exception {
        return singleToDeferredValue(commander.zrevrangebyscore(key, max, min, withscores, offsetCount));
    }

    @Override
    public Deferred<Long> zrevrank(@RedisProtocolSupport.Key final CharSequence key,
                                   final CharSequence member) throws Exception {
        return singleToDeferredValue(commander.zrevrank(key, member));
    }

    @Override
    public <T> Deferred<List<T>> zscan(@RedisProtocolSupport.Key final CharSequence key,
                                       final long cursor) throws Exception {
        return singleToDeferredValue(commander.zscan(key, cursor));
    }

    @Override
    public <T> Deferred<List<T>> zscan(@RedisProtocolSupport.Key final CharSequence key, final long cursor,
                                       @Nullable final CharSequence matchPattern,
                                       @Nullable final Long count) throws Exception {
        return singleToDeferredValue(commander.zscan(key, cursor, matchPattern, count));
    }

    @Override
    public Deferred<Double> zscore(@RedisProtocolSupport.Key final CharSequence key,
                                   final CharSequence member) throws Exception {
        return singleToDeferredValue(commander.zscore(key, member));
    }

    @Override
    public Deferred<Long> zunionstore(@RedisProtocolSupport.Key final CharSequence destination, final long numkeys,
                                      @RedisProtocolSupport.Key final Collection<? extends CharSequence> keys) throws Exception {
        return singleToDeferredValue(commander.zunionstore(destination, numkeys, keys));
    }

    @Override
    public Deferred<Long> zunionstore(@RedisProtocolSupport.Key final CharSequence destination, final long numkeys,
                                      @RedisProtocolSupport.Key final Collection<? extends CharSequence> keys,
                                      final Collection<Long> weightses,
                                      @Nullable final RedisProtocolSupport.ZunionstoreAggregate aggregate) throws Exception {
        return singleToDeferredValue(commander.zunionstore(destination, numkeys, keys, weightses, aggregate));
    }
}

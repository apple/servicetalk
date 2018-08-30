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

import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.Future;
import javax.annotation.Generated;
import javax.annotation.Nullable;

import static io.servicetalk.redis.api.BlockingUtils.blockingInvocation;
import static io.servicetalk.redis.api.BlockingUtils.singleToFuture;

@Generated({})
@SuppressWarnings("unchecked")
final class TransactedBufferRedisCommanderToBlockingTransactedBufferRedisCommander extends BlockingTransactedBufferRedisCommander {

    private final TransactedBufferRedisCommander commander;

    TransactedBufferRedisCommanderToBlockingTransactedBufferRedisCommander(
                final TransactedBufferRedisCommander commander) {
        this.commander = Objects.requireNonNull(commander);
    }

    @Override
    public void close() throws Exception {
        blockingInvocation(commander.closeAsync());
    }

    @Override
    public Future<Long> append(@RedisProtocolSupport.Key final Buffer key, final Buffer value) throws Exception {
        return singleToFuture(commander.append(key, value));
    }

    @Override
    public Future<String> auth(final Buffer password) throws Exception {
        return singleToFuture(commander.auth(password));
    }

    @Override
    public Future<String> bgrewriteaof() throws Exception {
        return singleToFuture(commander.bgrewriteaof());
    }

    @Override
    public Future<String> bgsave() throws Exception {
        return singleToFuture(commander.bgsave());
    }

    @Override
    public Future<Long> bitcount(@RedisProtocolSupport.Key final Buffer key) throws Exception {
        return singleToFuture(commander.bitcount(key));
    }

    @Override
    public Future<Long> bitcount(@RedisProtocolSupport.Key final Buffer key, @Nullable final Long start,
                                 @Nullable final Long end) throws Exception {
        return singleToFuture(commander.bitcount(key, start, end));
    }

    @Override
    public Future<List<Long>> bitfield(@RedisProtocolSupport.Key final Buffer key,
                                       final Collection<RedisProtocolSupport.BitfieldOperation> operations) throws Exception {
        return singleToFuture(commander.bitfield(key, operations));
    }

    @Override
    public Future<Long> bitop(final Buffer operation, @RedisProtocolSupport.Key final Buffer destkey,
                              @RedisProtocolSupport.Key final Buffer key) throws Exception {
        return singleToFuture(commander.bitop(operation, destkey, key));
    }

    @Override
    public Future<Long> bitop(final Buffer operation, @RedisProtocolSupport.Key final Buffer destkey,
                              @RedisProtocolSupport.Key final Buffer key1,
                              @RedisProtocolSupport.Key final Buffer key2) throws Exception {
        return singleToFuture(commander.bitop(operation, destkey, key1, key2));
    }

    @Override
    public Future<Long> bitop(final Buffer operation, @RedisProtocolSupport.Key final Buffer destkey,
                              @RedisProtocolSupport.Key final Buffer key1, @RedisProtocolSupport.Key final Buffer key2,
                              @RedisProtocolSupport.Key final Buffer key3) throws Exception {
        return singleToFuture(commander.bitop(operation, destkey, key1, key2, key3));
    }

    @Override
    public Future<Long> bitop(final Buffer operation, @RedisProtocolSupport.Key final Buffer destkey,
                              @RedisProtocolSupport.Key final Collection<Buffer> keys) throws Exception {
        return singleToFuture(commander.bitop(operation, destkey, keys));
    }

    @Override
    public Future<Long> bitpos(@RedisProtocolSupport.Key final Buffer key, final long bit) throws Exception {
        return singleToFuture(commander.bitpos(key, bit));
    }

    @Override
    public Future<Long> bitpos(@RedisProtocolSupport.Key final Buffer key, final long bit, @Nullable final Long start,
                               @Nullable final Long end) throws Exception {
        return singleToFuture(commander.bitpos(key, bit, start, end));
    }

    @Override
    public <T> Future<List<T>> blpop(@RedisProtocolSupport.Key final Collection<Buffer> keys,
                                     final long timeout) throws Exception {
        return singleToFuture(commander.blpop(keys, timeout));
    }

    @Override
    public <T> Future<List<T>> brpop(@RedisProtocolSupport.Key final Collection<Buffer> keys,
                                     final long timeout) throws Exception {
        return singleToFuture(commander.brpop(keys, timeout));
    }

    @Override
    public Future<Buffer> brpoplpush(@RedisProtocolSupport.Key final Buffer source,
                                     @RedisProtocolSupport.Key final Buffer destination,
                                     final long timeout) throws Exception {
        return singleToFuture(commander.brpoplpush(source, destination, timeout));
    }

    @Override
    public <T> Future<List<T>> bzpopmax(@RedisProtocolSupport.Key final Collection<Buffer> keys,
                                        final long timeout) throws Exception {
        return singleToFuture(commander.bzpopmax(keys, timeout));
    }

    @Override
    public <T> Future<List<T>> bzpopmin(@RedisProtocolSupport.Key final Collection<Buffer> keys,
                                        final long timeout) throws Exception {
        return singleToFuture(commander.bzpopmin(keys, timeout));
    }

    @Override
    public Future<Long> clientKill(@Nullable final Long id, @Nullable final RedisProtocolSupport.ClientKillType type,
                                   @Nullable final Buffer addrIpPort,
                                   @Nullable final Buffer skipmeYesNo) throws Exception {
        return singleToFuture(commander.clientKill(id, type, addrIpPort, skipmeYesNo));
    }

    @Override
    public Future<Buffer> clientList() throws Exception {
        return singleToFuture(commander.clientList());
    }

    @Override
    public Future<Buffer> clientGetname() throws Exception {
        return singleToFuture(commander.clientGetname());
    }

    @Override
    public Future<String> clientPause(final long timeout) throws Exception {
        return singleToFuture(commander.clientPause(timeout));
    }

    @Override
    public Future<String> clientReply(final RedisProtocolSupport.ClientReplyReplyMode replyMode) throws Exception {
        return singleToFuture(commander.clientReply(replyMode));
    }

    @Override
    public Future<String> clientSetname(final Buffer connectionName) throws Exception {
        return singleToFuture(commander.clientSetname(connectionName));
    }

    @Override
    public Future<String> clusterAddslots(final long slot) throws Exception {
        return singleToFuture(commander.clusterAddslots(slot));
    }

    @Override
    public Future<String> clusterAddslots(final long slot1, final long slot2) throws Exception {
        return singleToFuture(commander.clusterAddslots(slot1, slot2));
    }

    @Override
    public Future<String> clusterAddslots(final long slot1, final long slot2, final long slot3) throws Exception {
        return singleToFuture(commander.clusterAddslots(slot1, slot2, slot3));
    }

    @Override
    public Future<String> clusterAddslots(final Collection<Long> slots) throws Exception {
        return singleToFuture(commander.clusterAddslots(slots));
    }

    @Override
    public Future<Long> clusterCountFailureReports(final Buffer nodeId) throws Exception {
        return singleToFuture(commander.clusterCountFailureReports(nodeId));
    }

    @Override
    public Future<Long> clusterCountkeysinslot(final long slot) throws Exception {
        return singleToFuture(commander.clusterCountkeysinslot(slot));
    }

    @Override
    public Future<String> clusterDelslots(final long slot) throws Exception {
        return singleToFuture(commander.clusterDelslots(slot));
    }

    @Override
    public Future<String> clusterDelslots(final long slot1, final long slot2) throws Exception {
        return singleToFuture(commander.clusterDelslots(slot1, slot2));
    }

    @Override
    public Future<String> clusterDelslots(final long slot1, final long slot2, final long slot3) throws Exception {
        return singleToFuture(commander.clusterDelslots(slot1, slot2, slot3));
    }

    @Override
    public Future<String> clusterDelslots(final Collection<Long> slots) throws Exception {
        return singleToFuture(commander.clusterDelslots(slots));
    }

    @Override
    public Future<String> clusterFailover() throws Exception {
        return singleToFuture(commander.clusterFailover());
    }

    @Override
    public Future<String> clusterFailover(@Nullable final RedisProtocolSupport.ClusterFailoverOptions options) throws Exception {
        return singleToFuture(commander.clusterFailover(options));
    }

    @Override
    public Future<String> clusterForget(final Buffer nodeId) throws Exception {
        return singleToFuture(commander.clusterForget(nodeId));
    }

    @Override
    public <T> Future<List<T>> clusterGetkeysinslot(final long slot, final long count) throws Exception {
        return singleToFuture(commander.clusterGetkeysinslot(slot, count));
    }

    @Override
    public Future<Buffer> clusterInfo() throws Exception {
        return singleToFuture(commander.clusterInfo());
    }

    @Override
    public Future<Long> clusterKeyslot(final Buffer key) throws Exception {
        return singleToFuture(commander.clusterKeyslot(key));
    }

    @Override
    public Future<String> clusterMeet(final Buffer ip, final long port) throws Exception {
        return singleToFuture(commander.clusterMeet(ip, port));
    }

    @Override
    public Future<Buffer> clusterNodes() throws Exception {
        return singleToFuture(commander.clusterNodes());
    }

    @Override
    public Future<String> clusterReplicate(final Buffer nodeId) throws Exception {
        return singleToFuture(commander.clusterReplicate(nodeId));
    }

    @Override
    public Future<String> clusterReset() throws Exception {
        return singleToFuture(commander.clusterReset());
    }

    @Override
    public Future<String> clusterReset(@Nullable final RedisProtocolSupport.ClusterResetResetType resetType) throws Exception {
        return singleToFuture(commander.clusterReset(resetType));
    }

    @Override
    public Future<String> clusterSaveconfig() throws Exception {
        return singleToFuture(commander.clusterSaveconfig());
    }

    @Override
    public Future<String> clusterSetConfigEpoch(final long configEpoch) throws Exception {
        return singleToFuture(commander.clusterSetConfigEpoch(configEpoch));
    }

    @Override
    public Future<String> clusterSetslot(final long slot,
                                         final RedisProtocolSupport.ClusterSetslotSubcommand subcommand) throws Exception {
        return singleToFuture(commander.clusterSetslot(slot, subcommand));
    }

    @Override
    public Future<String> clusterSetslot(final long slot,
                                         final RedisProtocolSupport.ClusterSetslotSubcommand subcommand,
                                         @Nullable final Buffer nodeId) throws Exception {
        return singleToFuture(commander.clusterSetslot(slot, subcommand, nodeId));
    }

    @Override
    public Future<Buffer> clusterSlaves(final Buffer nodeId) throws Exception {
        return singleToFuture(commander.clusterSlaves(nodeId));
    }

    @Override
    public <T> Future<List<T>> clusterSlots() throws Exception {
        return singleToFuture(commander.clusterSlots());
    }

    @Override
    public <T> Future<List<T>> command() throws Exception {
        return singleToFuture(commander.command());
    }

    @Override
    public Future<Long> commandCount() throws Exception {
        return singleToFuture(commander.commandCount());
    }

    @Override
    public <T> Future<List<T>> commandGetkeys() throws Exception {
        return singleToFuture(commander.commandGetkeys());
    }

    @Override
    public <T> Future<List<T>> commandInfo(final Buffer commandName) throws Exception {
        return singleToFuture(commander.commandInfo(commandName));
    }

    @Override
    public <T> Future<List<T>> commandInfo(final Buffer commandName1, final Buffer commandName2) throws Exception {
        return singleToFuture(commander.commandInfo(commandName1, commandName2));
    }

    @Override
    public <T> Future<List<T>> commandInfo(final Buffer commandName1, final Buffer commandName2,
                                           final Buffer commandName3) throws Exception {
        return singleToFuture(commander.commandInfo(commandName1, commandName2, commandName3));
    }

    @Override
    public <T> Future<List<T>> commandInfo(final Collection<Buffer> commandNames) throws Exception {
        return singleToFuture(commander.commandInfo(commandNames));
    }

    @Override
    public <T> Future<List<T>> configGet(final Buffer parameter) throws Exception {
        return singleToFuture(commander.configGet(parameter));
    }

    @Override
    public Future<String> configRewrite() throws Exception {
        return singleToFuture(commander.configRewrite());
    }

    @Override
    public Future<String> configSet(final Buffer parameter, final Buffer value) throws Exception {
        return singleToFuture(commander.configSet(parameter, value));
    }

    @Override
    public Future<String> configResetstat() throws Exception {
        return singleToFuture(commander.configResetstat());
    }

    @Override
    public Future<Long> dbsize() throws Exception {
        return singleToFuture(commander.dbsize());
    }

    @Override
    public Future<String> debugObject(@RedisProtocolSupport.Key final Buffer key) throws Exception {
        return singleToFuture(commander.debugObject(key));
    }

    @Override
    public Future<String> debugSegfault() throws Exception {
        return singleToFuture(commander.debugSegfault());
    }

    @Override
    public Future<Long> decr(@RedisProtocolSupport.Key final Buffer key) throws Exception {
        return singleToFuture(commander.decr(key));
    }

    @Override
    public Future<Long> decrby(@RedisProtocolSupport.Key final Buffer key, final long decrement) throws Exception {
        return singleToFuture(commander.decrby(key, decrement));
    }

    @Override
    public Future<Long> del(@RedisProtocolSupport.Key final Buffer key) throws Exception {
        return singleToFuture(commander.del(key));
    }

    @Override
    public Future<Long> del(@RedisProtocolSupport.Key final Buffer key1,
                            @RedisProtocolSupport.Key final Buffer key2) throws Exception {
        return singleToFuture(commander.del(key1, key2));
    }

    @Override
    public Future<Long> del(@RedisProtocolSupport.Key final Buffer key1, @RedisProtocolSupport.Key final Buffer key2,
                            @RedisProtocolSupport.Key final Buffer key3) throws Exception {
        return singleToFuture(commander.del(key1, key2, key3));
    }

    @Override
    public Future<Long> del(@RedisProtocolSupport.Key final Collection<Buffer> keys) throws Exception {
        return singleToFuture(commander.del(keys));
    }

    @Override
    public String discard() throws Exception {
        return blockingInvocation(commander.discard());
    }

    @Override
    public Future<Buffer> dump(@RedisProtocolSupport.Key final Buffer key) throws Exception {
        return singleToFuture(commander.dump(key));
    }

    @Override
    public Future<Buffer> echo(final Buffer message) throws Exception {
        return singleToFuture(commander.echo(message));
    }

    @Override
    public Future<Buffer> eval(final Buffer script, final long numkeys,
                               @RedisProtocolSupport.Key final Collection<Buffer> keys,
                               final Collection<Buffer> args) throws Exception {
        return singleToFuture(commander.eval(script, numkeys, keys, args));
    }

    @Override
    public <T> Future<List<T>> evalList(final Buffer script, final long numkeys,
                                        @RedisProtocolSupport.Key final Collection<Buffer> keys,
                                        final Collection<Buffer> args) throws Exception {
        return singleToFuture(commander.evalList(script, numkeys, keys, args));
    }

    @Override
    public Future<Long> evalLong(final Buffer script, final long numkeys,
                                 @RedisProtocolSupport.Key final Collection<Buffer> keys,
                                 final Collection<Buffer> args) throws Exception {
        return singleToFuture(commander.evalLong(script, numkeys, keys, args));
    }

    @Override
    public Future<Buffer> evalsha(final Buffer sha1, final long numkeys,
                                  @RedisProtocolSupport.Key final Collection<Buffer> keys,
                                  final Collection<Buffer> args) throws Exception {
        return singleToFuture(commander.evalsha(sha1, numkeys, keys, args));
    }

    @Override
    public <T> Future<List<T>> evalshaList(final Buffer sha1, final long numkeys,
                                           @RedisProtocolSupport.Key final Collection<Buffer> keys,
                                           final Collection<Buffer> args) throws Exception {
        return singleToFuture(commander.evalshaList(sha1, numkeys, keys, args));
    }

    @Override
    public Future<Long> evalshaLong(final Buffer sha1, final long numkeys,
                                    @RedisProtocolSupport.Key final Collection<Buffer> keys,
                                    final Collection<Buffer> args) throws Exception {
        return singleToFuture(commander.evalshaLong(sha1, numkeys, keys, args));
    }

    @Override
    public void exec() throws Exception {
        blockingInvocation(commander.exec());
    }

    @Override
    public Future<Long> exists(@RedisProtocolSupport.Key final Buffer key) throws Exception {
        return singleToFuture(commander.exists(key));
    }

    @Override
    public Future<Long> exists(@RedisProtocolSupport.Key final Buffer key1,
                               @RedisProtocolSupport.Key final Buffer key2) throws Exception {
        return singleToFuture(commander.exists(key1, key2));
    }

    @Override
    public Future<Long> exists(@RedisProtocolSupport.Key final Buffer key1, @RedisProtocolSupport.Key final Buffer key2,
                               @RedisProtocolSupport.Key final Buffer key3) throws Exception {
        return singleToFuture(commander.exists(key1, key2, key3));
    }

    @Override
    public Future<Long> exists(@RedisProtocolSupport.Key final Collection<Buffer> keys) throws Exception {
        return singleToFuture(commander.exists(keys));
    }

    @Override
    public Future<Long> expire(@RedisProtocolSupport.Key final Buffer key, final long seconds) throws Exception {
        return singleToFuture(commander.expire(key, seconds));
    }

    @Override
    public Future<Long> expireat(@RedisProtocolSupport.Key final Buffer key, final long timestamp) throws Exception {
        return singleToFuture(commander.expireat(key, timestamp));
    }

    @Override
    public Future<String> flushall() throws Exception {
        return singleToFuture(commander.flushall());
    }

    @Override
    public Future<String> flushall(@Nullable final RedisProtocolSupport.FlushallAsync async) throws Exception {
        return singleToFuture(commander.flushall(async));
    }

    @Override
    public Future<String> flushdb() throws Exception {
        return singleToFuture(commander.flushdb());
    }

    @Override
    public Future<String> flushdb(@Nullable final RedisProtocolSupport.FlushdbAsync async) throws Exception {
        return singleToFuture(commander.flushdb(async));
    }

    @Override
    public Future<Long> geoadd(@RedisProtocolSupport.Key final Buffer key, final double longitude,
                               final double latitude, final Buffer member) throws Exception {
        return singleToFuture(commander.geoadd(key, longitude, latitude, member));
    }

    @Override
    public Future<Long> geoadd(@RedisProtocolSupport.Key final Buffer key, final double longitude1,
                               final double latitude1, final Buffer member1, final double longitude2,
                               final double latitude2, final Buffer member2) throws Exception {
        return singleToFuture(commander.geoadd(key, longitude1, latitude1, member1, longitude2, latitude2, member2));
    }

    @Override
    public Future<Long> geoadd(@RedisProtocolSupport.Key final Buffer key, final double longitude1,
                               final double latitude1, final Buffer member1, final double longitude2,
                               final double latitude2, final Buffer member2, final double longitude3,
                               final double latitude3, final Buffer member3) throws Exception {
        return singleToFuture(commander.geoadd(key, longitude1, latitude1, member1, longitude2, latitude2, member2,
                    longitude3, latitude3, member3));
    }

    @Override
    public Future<Long> geoadd(@RedisProtocolSupport.Key final Buffer key,
                               final Collection<RedisProtocolSupport.BufferLongitudeLatitudeMember> longitudeLatitudeMembers) throws Exception {
        return singleToFuture(commander.geoadd(key, longitudeLatitudeMembers));
    }

    @Override
    public Future<Double> geodist(@RedisProtocolSupport.Key final Buffer key, final Buffer member1,
                                  final Buffer member2) throws Exception {
        return singleToFuture(commander.geodist(key, member1, member2));
    }

    @Override
    public Future<Double> geodist(@RedisProtocolSupport.Key final Buffer key, final Buffer member1,
                                  final Buffer member2, @Nullable final Buffer unit) throws Exception {
        return singleToFuture(commander.geodist(key, member1, member2, unit));
    }

    @Override
    public <T> Future<List<T>> geohash(@RedisProtocolSupport.Key final Buffer key,
                                       final Buffer member) throws Exception {
        return singleToFuture(commander.geohash(key, member));
    }

    @Override
    public <T> Future<List<T>> geohash(@RedisProtocolSupport.Key final Buffer key, final Buffer member1,
                                       final Buffer member2) throws Exception {
        return singleToFuture(commander.geohash(key, member1, member2));
    }

    @Override
    public <T> Future<List<T>> geohash(@RedisProtocolSupport.Key final Buffer key, final Buffer member1,
                                       final Buffer member2, final Buffer member3) throws Exception {
        return singleToFuture(commander.geohash(key, member1, member2, member3));
    }

    @Override
    public <T> Future<List<T>> geohash(@RedisProtocolSupport.Key final Buffer key,
                                       final Collection<Buffer> members) throws Exception {
        return singleToFuture(commander.geohash(key, members));
    }

    @Override
    public <T> Future<List<T>> geopos(@RedisProtocolSupport.Key final Buffer key,
                                      final Buffer member) throws Exception {
        return singleToFuture(commander.geopos(key, member));
    }

    @Override
    public <T> Future<List<T>> geopos(@RedisProtocolSupport.Key final Buffer key, final Buffer member1,
                                      final Buffer member2) throws Exception {
        return singleToFuture(commander.geopos(key, member1, member2));
    }

    @Override
    public <T> Future<List<T>> geopos(@RedisProtocolSupport.Key final Buffer key, final Buffer member1,
                                      final Buffer member2, final Buffer member3) throws Exception {
        return singleToFuture(commander.geopos(key, member1, member2, member3));
    }

    @Override
    public <T> Future<List<T>> geopos(@RedisProtocolSupport.Key final Buffer key,
                                      final Collection<Buffer> members) throws Exception {
        return singleToFuture(commander.geopos(key, members));
    }

    @Override
    public <T> Future<List<T>> georadius(@RedisProtocolSupport.Key final Buffer key, final double longitude,
                                         final double latitude, final double radius,
                                         final RedisProtocolSupport.GeoradiusUnit unit) throws Exception {
        return singleToFuture(commander.georadius(key, longitude, latitude, radius, unit));
    }

    @Override
    public <T> Future<List<T>> georadius(@RedisProtocolSupport.Key final Buffer key, final double longitude,
                                         final double latitude, final double radius,
                                         final RedisProtocolSupport.GeoradiusUnit unit,
                                         @Nullable final RedisProtocolSupport.GeoradiusWithcoord withcoord,
                                         @Nullable final RedisProtocolSupport.GeoradiusWithdist withdist,
                                         @Nullable final RedisProtocolSupport.GeoradiusWithhash withhash,
                                         @Nullable final Long count,
                                         @Nullable final RedisProtocolSupport.GeoradiusOrder order,
                                         @Nullable @RedisProtocolSupport.Key final Buffer storeKey,
                                         @Nullable @RedisProtocolSupport.Key final Buffer storedistKey) throws Exception {
        return singleToFuture(commander.georadius(key, longitude, latitude, radius, unit, withcoord, withdist, withhash,
                    count, order, storeKey, storedistKey));
    }

    @Override
    public <T> Future<List<T>> georadiusbymember(@RedisProtocolSupport.Key final Buffer key, final Buffer member,
                                                 final double radius,
                                                 final RedisProtocolSupport.GeoradiusbymemberUnit unit) throws Exception {
        return singleToFuture(commander.georadiusbymember(key, member, radius, unit));
    }

    @Override
    public <T> Future<List<T>> georadiusbymember(@RedisProtocolSupport.Key final Buffer key, final Buffer member,
                                                 final double radius,
                                                 final RedisProtocolSupport.GeoradiusbymemberUnit unit,
                                                 @Nullable final RedisProtocolSupport.GeoradiusbymemberWithcoord withcoord,
                                                 @Nullable final RedisProtocolSupport.GeoradiusbymemberWithdist withdist,
                                                 @Nullable final RedisProtocolSupport.GeoradiusbymemberWithhash withhash,
                                                 @Nullable final Long count,
                                                 @Nullable final RedisProtocolSupport.GeoradiusbymemberOrder order,
                                                 @Nullable @RedisProtocolSupport.Key final Buffer storeKey,
                                                 @Nullable @RedisProtocolSupport.Key final Buffer storedistKey) throws Exception {
        return singleToFuture(commander.georadiusbymember(key, member, radius, unit, withcoord, withdist, withhash,
                    count, order, storeKey, storedistKey));
    }

    @Override
    public Future<Buffer> get(@RedisProtocolSupport.Key final Buffer key) throws Exception {
        return singleToFuture(commander.get(key));
    }

    @Override
    public Future<Long> getbit(@RedisProtocolSupport.Key final Buffer key, final long offset) throws Exception {
        return singleToFuture(commander.getbit(key, offset));
    }

    @Override
    public Future<Buffer> getrange(@RedisProtocolSupport.Key final Buffer key, final long start,
                                   final long end) throws Exception {
        return singleToFuture(commander.getrange(key, start, end));
    }

    @Override
    public Future<Buffer> getset(@RedisProtocolSupport.Key final Buffer key, final Buffer value) throws Exception {
        return singleToFuture(commander.getset(key, value));
    }

    @Override
    public Future<Long> hdel(@RedisProtocolSupport.Key final Buffer key, final Buffer field) throws Exception {
        return singleToFuture(commander.hdel(key, field));
    }

    @Override
    public Future<Long> hdel(@RedisProtocolSupport.Key final Buffer key, final Buffer field1,
                             final Buffer field2) throws Exception {
        return singleToFuture(commander.hdel(key, field1, field2));
    }

    @Override
    public Future<Long> hdel(@RedisProtocolSupport.Key final Buffer key, final Buffer field1, final Buffer field2,
                             final Buffer field3) throws Exception {
        return singleToFuture(commander.hdel(key, field1, field2, field3));
    }

    @Override
    public Future<Long> hdel(@RedisProtocolSupport.Key final Buffer key,
                             final Collection<Buffer> fields) throws Exception {
        return singleToFuture(commander.hdel(key, fields));
    }

    @Override
    public Future<Long> hexists(@RedisProtocolSupport.Key final Buffer key, final Buffer field) throws Exception {
        return singleToFuture(commander.hexists(key, field));
    }

    @Override
    public Future<Buffer> hget(@RedisProtocolSupport.Key final Buffer key, final Buffer field) throws Exception {
        return singleToFuture(commander.hget(key, field));
    }

    @Override
    public <T> Future<List<T>> hgetall(@RedisProtocolSupport.Key final Buffer key) throws Exception {
        return singleToFuture(commander.hgetall(key));
    }

    @Override
    public Future<Long> hincrby(@RedisProtocolSupport.Key final Buffer key, final Buffer field,
                                final long increment) throws Exception {
        return singleToFuture(commander.hincrby(key, field, increment));
    }

    @Override
    public Future<Double> hincrbyfloat(@RedisProtocolSupport.Key final Buffer key, final Buffer field,
                                       final double increment) throws Exception {
        return singleToFuture(commander.hincrbyfloat(key, field, increment));
    }

    @Override
    public <T> Future<List<T>> hkeys(@RedisProtocolSupport.Key final Buffer key) throws Exception {
        return singleToFuture(commander.hkeys(key));
    }

    @Override
    public Future<Long> hlen(@RedisProtocolSupport.Key final Buffer key) throws Exception {
        return singleToFuture(commander.hlen(key));
    }

    @Override
    public <T> Future<List<T>> hmget(@RedisProtocolSupport.Key final Buffer key, final Buffer field) throws Exception {
        return singleToFuture(commander.hmget(key, field));
    }

    @Override
    public <T> Future<List<T>> hmget(@RedisProtocolSupport.Key final Buffer key, final Buffer field1,
                                     final Buffer field2) throws Exception {
        return singleToFuture(commander.hmget(key, field1, field2));
    }

    @Override
    public <T> Future<List<T>> hmget(@RedisProtocolSupport.Key final Buffer key, final Buffer field1,
                                     final Buffer field2, final Buffer field3) throws Exception {
        return singleToFuture(commander.hmget(key, field1, field2, field3));
    }

    @Override
    public <T> Future<List<T>> hmget(@RedisProtocolSupport.Key final Buffer key,
                                     final Collection<Buffer> fields) throws Exception {
        return singleToFuture(commander.hmget(key, fields));
    }

    @Override
    public Future<String> hmset(@RedisProtocolSupport.Key final Buffer key, final Buffer field,
                                final Buffer value) throws Exception {
        return singleToFuture(commander.hmset(key, field, value));
    }

    @Override
    public Future<String> hmset(@RedisProtocolSupport.Key final Buffer key, final Buffer field1, final Buffer value1,
                                final Buffer field2, final Buffer value2) throws Exception {
        return singleToFuture(commander.hmset(key, field1, value1, field2, value2));
    }

    @Override
    public Future<String> hmset(@RedisProtocolSupport.Key final Buffer key, final Buffer field1, final Buffer value1,
                                final Buffer field2, final Buffer value2, final Buffer field3,
                                final Buffer value3) throws Exception {
        return singleToFuture(commander.hmset(key, field1, value1, field2, value2, field3, value3));
    }

    @Override
    public Future<String> hmset(@RedisProtocolSupport.Key final Buffer key,
                                final Collection<RedisProtocolSupport.BufferFieldValue> fieldValues) throws Exception {
        return singleToFuture(commander.hmset(key, fieldValues));
    }

    @Override
    public <T> Future<List<T>> hscan(@RedisProtocolSupport.Key final Buffer key, final long cursor) throws Exception {
        return singleToFuture(commander.hscan(key, cursor));
    }

    @Override
    public <T> Future<List<T>> hscan(@RedisProtocolSupport.Key final Buffer key, final long cursor,
                                     @Nullable final Buffer matchPattern, @Nullable final Long count) throws Exception {
        return singleToFuture(commander.hscan(key, cursor, matchPattern, count));
    }

    @Override
    public Future<Long> hset(@RedisProtocolSupport.Key final Buffer key, final Buffer field,
                             final Buffer value) throws Exception {
        return singleToFuture(commander.hset(key, field, value));
    }

    @Override
    public Future<Long> hsetnx(@RedisProtocolSupport.Key final Buffer key, final Buffer field,
                               final Buffer value) throws Exception {
        return singleToFuture(commander.hsetnx(key, field, value));
    }

    @Override
    public Future<Long> hstrlen(@RedisProtocolSupport.Key final Buffer key, final Buffer field) throws Exception {
        return singleToFuture(commander.hstrlen(key, field));
    }

    @Override
    public <T> Future<List<T>> hvals(@RedisProtocolSupport.Key final Buffer key) throws Exception {
        return singleToFuture(commander.hvals(key));
    }

    @Override
    public Future<Long> incr(@RedisProtocolSupport.Key final Buffer key) throws Exception {
        return singleToFuture(commander.incr(key));
    }

    @Override
    public Future<Long> incrby(@RedisProtocolSupport.Key final Buffer key, final long increment) throws Exception {
        return singleToFuture(commander.incrby(key, increment));
    }

    @Override
    public Future<Double> incrbyfloat(@RedisProtocolSupport.Key final Buffer key,
                                      final double increment) throws Exception {
        return singleToFuture(commander.incrbyfloat(key, increment));
    }

    @Override
    public Future<Buffer> info() throws Exception {
        return singleToFuture(commander.info());
    }

    @Override
    public Future<Buffer> info(@Nullable final Buffer section) throws Exception {
        return singleToFuture(commander.info(section));
    }

    @Override
    public <T> Future<List<T>> keys(final Buffer pattern) throws Exception {
        return singleToFuture(commander.keys(pattern));
    }

    @Override
    public Future<Long> lastsave() throws Exception {
        return singleToFuture(commander.lastsave());
    }

    @Override
    public Future<Buffer> lindex(@RedisProtocolSupport.Key final Buffer key, final long index) throws Exception {
        return singleToFuture(commander.lindex(key, index));
    }

    @Override
    public Future<Long> linsert(@RedisProtocolSupport.Key final Buffer key,
                                final RedisProtocolSupport.LinsertWhere where, final Buffer pivot,
                                final Buffer value) throws Exception {
        return singleToFuture(commander.linsert(key, where, pivot, value));
    }

    @Override
    public Future<Long> llen(@RedisProtocolSupport.Key final Buffer key) throws Exception {
        return singleToFuture(commander.llen(key));
    }

    @Override
    public Future<Buffer> lpop(@RedisProtocolSupport.Key final Buffer key) throws Exception {
        return singleToFuture(commander.lpop(key));
    }

    @Override
    public Future<Long> lpush(@RedisProtocolSupport.Key final Buffer key, final Buffer value) throws Exception {
        return singleToFuture(commander.lpush(key, value));
    }

    @Override
    public Future<Long> lpush(@RedisProtocolSupport.Key final Buffer key, final Buffer value1,
                              final Buffer value2) throws Exception {
        return singleToFuture(commander.lpush(key, value1, value2));
    }

    @Override
    public Future<Long> lpush(@RedisProtocolSupport.Key final Buffer key, final Buffer value1, final Buffer value2,
                              final Buffer value3) throws Exception {
        return singleToFuture(commander.lpush(key, value1, value2, value3));
    }

    @Override
    public Future<Long> lpush(@RedisProtocolSupport.Key final Buffer key,
                              final Collection<Buffer> values) throws Exception {
        return singleToFuture(commander.lpush(key, values));
    }

    @Override
    public Future<Long> lpushx(@RedisProtocolSupport.Key final Buffer key, final Buffer value) throws Exception {
        return singleToFuture(commander.lpushx(key, value));
    }

    @Override
    public <T> Future<List<T>> lrange(@RedisProtocolSupport.Key final Buffer key, final long start,
                                      final long stop) throws Exception {
        return singleToFuture(commander.lrange(key, start, stop));
    }

    @Override
    public Future<Long> lrem(@RedisProtocolSupport.Key final Buffer key, final long count,
                             final Buffer value) throws Exception {
        return singleToFuture(commander.lrem(key, count, value));
    }

    @Override
    public Future<String> lset(@RedisProtocolSupport.Key final Buffer key, final long index,
                               final Buffer value) throws Exception {
        return singleToFuture(commander.lset(key, index, value));
    }

    @Override
    public Future<String> ltrim(@RedisProtocolSupport.Key final Buffer key, final long start,
                                final long stop) throws Exception {
        return singleToFuture(commander.ltrim(key, start, stop));
    }

    @Override
    public Future<Buffer> memoryDoctor() throws Exception {
        return singleToFuture(commander.memoryDoctor());
    }

    @Override
    public <T> Future<List<T>> memoryHelp() throws Exception {
        return singleToFuture(commander.memoryHelp());
    }

    @Override
    public Future<Buffer> memoryMallocStats() throws Exception {
        return singleToFuture(commander.memoryMallocStats());
    }

    @Override
    public Future<String> memoryPurge() throws Exception {
        return singleToFuture(commander.memoryPurge());
    }

    @Override
    public <T> Future<List<T>> memoryStats() throws Exception {
        return singleToFuture(commander.memoryStats());
    }

    @Override
    public Future<Long> memoryUsage(@RedisProtocolSupport.Key final Buffer key) throws Exception {
        return singleToFuture(commander.memoryUsage(key));
    }

    @Override
    public Future<Long> memoryUsage(@RedisProtocolSupport.Key final Buffer key,
                                    @Nullable final Long samplesCount) throws Exception {
        return singleToFuture(commander.memoryUsage(key, samplesCount));
    }

    @Override
    public <T> Future<List<T>> mget(@RedisProtocolSupport.Key final Buffer key) throws Exception {
        return singleToFuture(commander.mget(key));
    }

    @Override
    public <T> Future<List<T>> mget(@RedisProtocolSupport.Key final Buffer key1,
                                    @RedisProtocolSupport.Key final Buffer key2) throws Exception {
        return singleToFuture(commander.mget(key1, key2));
    }

    @Override
    public <T> Future<List<T>> mget(@RedisProtocolSupport.Key final Buffer key1,
                                    @RedisProtocolSupport.Key final Buffer key2,
                                    @RedisProtocolSupport.Key final Buffer key3) throws Exception {
        return singleToFuture(commander.mget(key1, key2, key3));
    }

    @Override
    public <T> Future<List<T>> mget(@RedisProtocolSupport.Key final Collection<Buffer> keys) throws Exception {
        return singleToFuture(commander.mget(keys));
    }

    @Override
    public Future<Long> move(@RedisProtocolSupport.Key final Buffer key, final long db) throws Exception {
        return singleToFuture(commander.move(key, db));
    }

    @Override
    public Future<String> mset(@RedisProtocolSupport.Key final Buffer key, final Buffer value) throws Exception {
        return singleToFuture(commander.mset(key, value));
    }

    @Override
    public Future<String> mset(@RedisProtocolSupport.Key final Buffer key1, final Buffer value1,
                               @RedisProtocolSupport.Key final Buffer key2, final Buffer value2) throws Exception {
        return singleToFuture(commander.mset(key1, value1, key2, value2));
    }

    @Override
    public Future<String> mset(@RedisProtocolSupport.Key final Buffer key1, final Buffer value1,
                               @RedisProtocolSupport.Key final Buffer key2, final Buffer value2,
                               @RedisProtocolSupport.Key final Buffer key3, final Buffer value3) throws Exception {
        return singleToFuture(commander.mset(key1, value1, key2, value2, key3, value3));
    }

    @Override
    public Future<String> mset(final Collection<RedisProtocolSupport.BufferKeyValue> keyValues) throws Exception {
        return singleToFuture(commander.mset(keyValues));
    }

    @Override
    public Future<Long> msetnx(@RedisProtocolSupport.Key final Buffer key, final Buffer value) throws Exception {
        return singleToFuture(commander.msetnx(key, value));
    }

    @Override
    public Future<Long> msetnx(@RedisProtocolSupport.Key final Buffer key1, final Buffer value1,
                               @RedisProtocolSupport.Key final Buffer key2, final Buffer value2) throws Exception {
        return singleToFuture(commander.msetnx(key1, value1, key2, value2));
    }

    @Override
    public Future<Long> msetnx(@RedisProtocolSupport.Key final Buffer key1, final Buffer value1,
                               @RedisProtocolSupport.Key final Buffer key2, final Buffer value2,
                               @RedisProtocolSupport.Key final Buffer key3, final Buffer value3) throws Exception {
        return singleToFuture(commander.msetnx(key1, value1, key2, value2, key3, value3));
    }

    @Override
    public Future<Long> msetnx(final Collection<RedisProtocolSupport.BufferKeyValue> keyValues) throws Exception {
        return singleToFuture(commander.msetnx(keyValues));
    }

    @Override
    public Future<Buffer> objectEncoding(@RedisProtocolSupport.Key final Buffer key) throws Exception {
        return singleToFuture(commander.objectEncoding(key));
    }

    @Override
    public Future<Long> objectFreq(@RedisProtocolSupport.Key final Buffer key) throws Exception {
        return singleToFuture(commander.objectFreq(key));
    }

    @Override
    public Future<List<String>> objectHelp() throws Exception {
        return singleToFuture(commander.objectHelp());
    }

    @Override
    public Future<Long> objectIdletime(@RedisProtocolSupport.Key final Buffer key) throws Exception {
        return singleToFuture(commander.objectIdletime(key));
    }

    @Override
    public Future<Long> objectRefcount(@RedisProtocolSupport.Key final Buffer key) throws Exception {
        return singleToFuture(commander.objectRefcount(key));
    }

    @Override
    public Future<Long> persist(@RedisProtocolSupport.Key final Buffer key) throws Exception {
        return singleToFuture(commander.persist(key));
    }

    @Override
    public Future<Long> pexpire(@RedisProtocolSupport.Key final Buffer key, final long milliseconds) throws Exception {
        return singleToFuture(commander.pexpire(key, milliseconds));
    }

    @Override
    public Future<Long> pexpireat(@RedisProtocolSupport.Key final Buffer key,
                                  final long millisecondsTimestamp) throws Exception {
        return singleToFuture(commander.pexpireat(key, millisecondsTimestamp));
    }

    @Override
    public Future<Long> pfadd(@RedisProtocolSupport.Key final Buffer key, final Buffer element) throws Exception {
        return singleToFuture(commander.pfadd(key, element));
    }

    @Override
    public Future<Long> pfadd(@RedisProtocolSupport.Key final Buffer key, final Buffer element1,
                              final Buffer element2) throws Exception {
        return singleToFuture(commander.pfadd(key, element1, element2));
    }

    @Override
    public Future<Long> pfadd(@RedisProtocolSupport.Key final Buffer key, final Buffer element1, final Buffer element2,
                              final Buffer element3) throws Exception {
        return singleToFuture(commander.pfadd(key, element1, element2, element3));
    }

    @Override
    public Future<Long> pfadd(@RedisProtocolSupport.Key final Buffer key,
                              final Collection<Buffer> elements) throws Exception {
        return singleToFuture(commander.pfadd(key, elements));
    }

    @Override
    public Future<Long> pfcount(@RedisProtocolSupport.Key final Buffer key) throws Exception {
        return singleToFuture(commander.pfcount(key));
    }

    @Override
    public Future<Long> pfcount(@RedisProtocolSupport.Key final Buffer key1,
                                @RedisProtocolSupport.Key final Buffer key2) throws Exception {
        return singleToFuture(commander.pfcount(key1, key2));
    }

    @Override
    public Future<Long> pfcount(@RedisProtocolSupport.Key final Buffer key1,
                                @RedisProtocolSupport.Key final Buffer key2,
                                @RedisProtocolSupport.Key final Buffer key3) throws Exception {
        return singleToFuture(commander.pfcount(key1, key2, key3));
    }

    @Override
    public Future<Long> pfcount(@RedisProtocolSupport.Key final Collection<Buffer> keys) throws Exception {
        return singleToFuture(commander.pfcount(keys));
    }

    @Override
    public Future<String> pfmerge(@RedisProtocolSupport.Key final Buffer destkey,
                                  @RedisProtocolSupport.Key final Buffer sourcekey) throws Exception {
        return singleToFuture(commander.pfmerge(destkey, sourcekey));
    }

    @Override
    public Future<String> pfmerge(@RedisProtocolSupport.Key final Buffer destkey,
                                  @RedisProtocolSupport.Key final Buffer sourcekey1,
                                  @RedisProtocolSupport.Key final Buffer sourcekey2) throws Exception {
        return singleToFuture(commander.pfmerge(destkey, sourcekey1, sourcekey2));
    }

    @Override
    public Future<String> pfmerge(@RedisProtocolSupport.Key final Buffer destkey,
                                  @RedisProtocolSupport.Key final Buffer sourcekey1,
                                  @RedisProtocolSupport.Key final Buffer sourcekey2,
                                  @RedisProtocolSupport.Key final Buffer sourcekey3) throws Exception {
        return singleToFuture(commander.pfmerge(destkey, sourcekey1, sourcekey2, sourcekey3));
    }

    @Override
    public Future<String> pfmerge(@RedisProtocolSupport.Key final Buffer destkey,
                                  @RedisProtocolSupport.Key final Collection<Buffer> sourcekeys) throws Exception {
        return singleToFuture(commander.pfmerge(destkey, sourcekeys));
    }

    @Override
    public Future<String> ping() throws Exception {
        return singleToFuture(commander.ping());
    }

    @Override
    public Future<Buffer> ping(final Buffer message) throws Exception {
        return singleToFuture(commander.ping(message));
    }

    @Override
    public Future<String> psetex(@RedisProtocolSupport.Key final Buffer key, final long milliseconds,
                                 final Buffer value) throws Exception {
        return singleToFuture(commander.psetex(key, milliseconds, value));
    }

    @Override
    public Future<Long> pttl(@RedisProtocolSupport.Key final Buffer key) throws Exception {
        return singleToFuture(commander.pttl(key));
    }

    @Override
    public Future<Long> publish(final Buffer channel, final Buffer message) throws Exception {
        return singleToFuture(commander.publish(channel, message));
    }

    @Override
    public Future<List<String>> pubsubChannels() throws Exception {
        return singleToFuture(commander.pubsubChannels());
    }

    @Override
    public Future<List<String>> pubsubChannels(@Nullable final Buffer pattern) throws Exception {
        return singleToFuture(commander.pubsubChannels(pattern));
    }

    @Override
    public Future<List<String>> pubsubChannels(@Nullable final Buffer pattern1,
                                               @Nullable final Buffer pattern2) throws Exception {
        return singleToFuture(commander.pubsubChannels(pattern1, pattern2));
    }

    @Override
    public Future<List<String>> pubsubChannels(@Nullable final Buffer pattern1, @Nullable final Buffer pattern2,
                                               @Nullable final Buffer pattern3) throws Exception {
        return singleToFuture(commander.pubsubChannels(pattern1, pattern2, pattern3));
    }

    @Override
    public Future<List<String>> pubsubChannels(final Collection<Buffer> patterns) throws Exception {
        return singleToFuture(commander.pubsubChannels(patterns));
    }

    @Override
    public <T> Future<List<T>> pubsubNumsub() throws Exception {
        return singleToFuture(commander.pubsubNumsub());
    }

    @Override
    public <T> Future<List<T>> pubsubNumsub(@Nullable final Buffer channel) throws Exception {
        return singleToFuture(commander.pubsubNumsub(channel));
    }

    @Override
    public <T> Future<List<T>> pubsubNumsub(@Nullable final Buffer channel1,
                                            @Nullable final Buffer channel2) throws Exception {
        return singleToFuture(commander.pubsubNumsub(channel1, channel2));
    }

    @Override
    public <T> Future<List<T>> pubsubNumsub(@Nullable final Buffer channel1, @Nullable final Buffer channel2,
                                            @Nullable final Buffer channel3) throws Exception {
        return singleToFuture(commander.pubsubNumsub(channel1, channel2, channel3));
    }

    @Override
    public <T> Future<List<T>> pubsubNumsub(final Collection<Buffer> channels) throws Exception {
        return singleToFuture(commander.pubsubNumsub(channels));
    }

    @Override
    public Future<Long> pubsubNumpat() throws Exception {
        return singleToFuture(commander.pubsubNumpat());
    }

    @Override
    public Future<Buffer> randomkey() throws Exception {
        return singleToFuture(commander.randomkey());
    }

    @Override
    public Future<String> readonly() throws Exception {
        return singleToFuture(commander.readonly());
    }

    @Override
    public Future<String> readwrite() throws Exception {
        return singleToFuture(commander.readwrite());
    }

    @Override
    public Future<String> rename(@RedisProtocolSupport.Key final Buffer key,
                                 @RedisProtocolSupport.Key final Buffer newkey) throws Exception {
        return singleToFuture(commander.rename(key, newkey));
    }

    @Override
    public Future<Long> renamenx(@RedisProtocolSupport.Key final Buffer key,
                                 @RedisProtocolSupport.Key final Buffer newkey) throws Exception {
        return singleToFuture(commander.renamenx(key, newkey));
    }

    @Override
    public Future<String> restore(@RedisProtocolSupport.Key final Buffer key, final long ttl,
                                  final Buffer serializedValue) throws Exception {
        return singleToFuture(commander.restore(key, ttl, serializedValue));
    }

    @Override
    public Future<String> restore(@RedisProtocolSupport.Key final Buffer key, final long ttl,
                                  final Buffer serializedValue,
                                  @Nullable final RedisProtocolSupport.RestoreReplace replace) throws Exception {
        return singleToFuture(commander.restore(key, ttl, serializedValue, replace));
    }

    @Override
    public <T> Future<List<T>> role() throws Exception {
        return singleToFuture(commander.role());
    }

    @Override
    public Future<Buffer> rpop(@RedisProtocolSupport.Key final Buffer key) throws Exception {
        return singleToFuture(commander.rpop(key));
    }

    @Override
    public Future<Buffer> rpoplpush(@RedisProtocolSupport.Key final Buffer source,
                                    @RedisProtocolSupport.Key final Buffer destination) throws Exception {
        return singleToFuture(commander.rpoplpush(source, destination));
    }

    @Override
    public Future<Long> rpush(@RedisProtocolSupport.Key final Buffer key, final Buffer value) throws Exception {
        return singleToFuture(commander.rpush(key, value));
    }

    @Override
    public Future<Long> rpush(@RedisProtocolSupport.Key final Buffer key, final Buffer value1,
                              final Buffer value2) throws Exception {
        return singleToFuture(commander.rpush(key, value1, value2));
    }

    @Override
    public Future<Long> rpush(@RedisProtocolSupport.Key final Buffer key, final Buffer value1, final Buffer value2,
                              final Buffer value3) throws Exception {
        return singleToFuture(commander.rpush(key, value1, value2, value3));
    }

    @Override
    public Future<Long> rpush(@RedisProtocolSupport.Key final Buffer key,
                              final Collection<Buffer> values) throws Exception {
        return singleToFuture(commander.rpush(key, values));
    }

    @Override
    public Future<Long> rpushx(@RedisProtocolSupport.Key final Buffer key, final Buffer value) throws Exception {
        return singleToFuture(commander.rpushx(key, value));
    }

    @Override
    public Future<Long> sadd(@RedisProtocolSupport.Key final Buffer key, final Buffer member) throws Exception {
        return singleToFuture(commander.sadd(key, member));
    }

    @Override
    public Future<Long> sadd(@RedisProtocolSupport.Key final Buffer key, final Buffer member1,
                             final Buffer member2) throws Exception {
        return singleToFuture(commander.sadd(key, member1, member2));
    }

    @Override
    public Future<Long> sadd(@RedisProtocolSupport.Key final Buffer key, final Buffer member1, final Buffer member2,
                             final Buffer member3) throws Exception {
        return singleToFuture(commander.sadd(key, member1, member2, member3));
    }

    @Override
    public Future<Long> sadd(@RedisProtocolSupport.Key final Buffer key,
                             final Collection<Buffer> members) throws Exception {
        return singleToFuture(commander.sadd(key, members));
    }

    @Override
    public Future<String> save() throws Exception {
        return singleToFuture(commander.save());
    }

    @Override
    public <T> Future<List<T>> scan(final long cursor) throws Exception {
        return singleToFuture(commander.scan(cursor));
    }

    @Override
    public <T> Future<List<T>> scan(final long cursor, @Nullable final Buffer matchPattern,
                                    @Nullable final Long count) throws Exception {
        return singleToFuture(commander.scan(cursor, matchPattern, count));
    }

    @Override
    public Future<Long> scard(@RedisProtocolSupport.Key final Buffer key) throws Exception {
        return singleToFuture(commander.scard(key));
    }

    @Override
    public Future<String> scriptDebug(final RedisProtocolSupport.ScriptDebugMode mode) throws Exception {
        return singleToFuture(commander.scriptDebug(mode));
    }

    @Override
    public <T> Future<List<T>> scriptExists(final Buffer sha1) throws Exception {
        return singleToFuture(commander.scriptExists(sha1));
    }

    @Override
    public <T> Future<List<T>> scriptExists(final Buffer sha11, final Buffer sha12) throws Exception {
        return singleToFuture(commander.scriptExists(sha11, sha12));
    }

    @Override
    public <T> Future<List<T>> scriptExists(final Buffer sha11, final Buffer sha12,
                                            final Buffer sha13) throws Exception {
        return singleToFuture(commander.scriptExists(sha11, sha12, sha13));
    }

    @Override
    public <T> Future<List<T>> scriptExists(final Collection<Buffer> sha1s) throws Exception {
        return singleToFuture(commander.scriptExists(sha1s));
    }

    @Override
    public Future<String> scriptFlush() throws Exception {
        return singleToFuture(commander.scriptFlush());
    }

    @Override
    public Future<String> scriptKill() throws Exception {
        return singleToFuture(commander.scriptKill());
    }

    @Override
    public Future<Buffer> scriptLoad(final Buffer script) throws Exception {
        return singleToFuture(commander.scriptLoad(script));
    }

    @Override
    public <T> Future<List<T>> sdiff(@RedisProtocolSupport.Key final Buffer firstkey) throws Exception {
        return singleToFuture(commander.sdiff(firstkey));
    }

    @Override
    public <T> Future<List<T>> sdiff(@RedisProtocolSupport.Key final Buffer firstkey,
                                     @Nullable @RedisProtocolSupport.Key final Buffer otherkey) throws Exception {
        return singleToFuture(commander.sdiff(firstkey, otherkey));
    }

    @Override
    public <T> Future<List<T>> sdiff(@RedisProtocolSupport.Key final Buffer firstkey,
                                     @Nullable @RedisProtocolSupport.Key final Buffer otherkey1,
                                     @Nullable @RedisProtocolSupport.Key final Buffer otherkey2) throws Exception {
        return singleToFuture(commander.sdiff(firstkey, otherkey1, otherkey2));
    }

    @Override
    public <T> Future<List<T>> sdiff(@RedisProtocolSupport.Key final Buffer firstkey,
                                     @Nullable @RedisProtocolSupport.Key final Buffer otherkey1,
                                     @Nullable @RedisProtocolSupport.Key final Buffer otherkey2,
                                     @Nullable @RedisProtocolSupport.Key final Buffer otherkey3) throws Exception {
        return singleToFuture(commander.sdiff(firstkey, otherkey1, otherkey2, otherkey3));
    }

    @Override
    public <T> Future<List<T>> sdiff(@RedisProtocolSupport.Key final Buffer firstkey,
                                     @RedisProtocolSupport.Key final Collection<Buffer> otherkeys) throws Exception {
        return singleToFuture(commander.sdiff(firstkey, otherkeys));
    }

    @Override
    public Future<Long> sdiffstore(@RedisProtocolSupport.Key final Buffer destination,
                                   @RedisProtocolSupport.Key final Buffer firstkey) throws Exception {
        return singleToFuture(commander.sdiffstore(destination, firstkey));
    }

    @Override
    public Future<Long> sdiffstore(@RedisProtocolSupport.Key final Buffer destination,
                                   @RedisProtocolSupport.Key final Buffer firstkey,
                                   @Nullable @RedisProtocolSupport.Key final Buffer otherkey) throws Exception {
        return singleToFuture(commander.sdiffstore(destination, firstkey, otherkey));
    }

    @Override
    public Future<Long> sdiffstore(@RedisProtocolSupport.Key final Buffer destination,
                                   @RedisProtocolSupport.Key final Buffer firstkey,
                                   @Nullable @RedisProtocolSupport.Key final Buffer otherkey1,
                                   @Nullable @RedisProtocolSupport.Key final Buffer otherkey2) throws Exception {
        return singleToFuture(commander.sdiffstore(destination, firstkey, otherkey1, otherkey2));
    }

    @Override
    public Future<Long> sdiffstore(@RedisProtocolSupport.Key final Buffer destination,
                                   @RedisProtocolSupport.Key final Buffer firstkey,
                                   @Nullable @RedisProtocolSupport.Key final Buffer otherkey1,
                                   @Nullable @RedisProtocolSupport.Key final Buffer otherkey2,
                                   @Nullable @RedisProtocolSupport.Key final Buffer otherkey3) throws Exception {
        return singleToFuture(commander.sdiffstore(destination, firstkey, otherkey1, otherkey2, otherkey3));
    }

    @Override
    public Future<Long> sdiffstore(@RedisProtocolSupport.Key final Buffer destination,
                                   @RedisProtocolSupport.Key final Buffer firstkey,
                                   @RedisProtocolSupport.Key final Collection<Buffer> otherkeys) throws Exception {
        return singleToFuture(commander.sdiffstore(destination, firstkey, otherkeys));
    }

    @Override
    public Future<String> select(final long index) throws Exception {
        return singleToFuture(commander.select(index));
    }

    @Override
    public Future<String> set(@RedisProtocolSupport.Key final Buffer key, final Buffer value) throws Exception {
        return singleToFuture(commander.set(key, value));
    }

    @Override
    public Future<String> set(@RedisProtocolSupport.Key final Buffer key, final Buffer value,
                              @Nullable final RedisProtocolSupport.ExpireDuration expireDuration,
                              @Nullable final RedisProtocolSupport.SetCondition condition) throws Exception {
        return singleToFuture(commander.set(key, value, expireDuration, condition));
    }

    @Override
    public Future<Long> setbit(@RedisProtocolSupport.Key final Buffer key, final long offset,
                               final Buffer value) throws Exception {
        return singleToFuture(commander.setbit(key, offset, value));
    }

    @Override
    public Future<String> setex(@RedisProtocolSupport.Key final Buffer key, final long seconds,
                                final Buffer value) throws Exception {
        return singleToFuture(commander.setex(key, seconds, value));
    }

    @Override
    public Future<Long> setnx(@RedisProtocolSupport.Key final Buffer key, final Buffer value) throws Exception {
        return singleToFuture(commander.setnx(key, value));
    }

    @Override
    public Future<Long> setrange(@RedisProtocolSupport.Key final Buffer key, final long offset,
                                 final Buffer value) throws Exception {
        return singleToFuture(commander.setrange(key, offset, value));
    }

    @Override
    public Future<String> shutdown() throws Exception {
        return singleToFuture(commander.shutdown());
    }

    @Override
    public Future<String> shutdown(@Nullable final RedisProtocolSupport.ShutdownSaveMode saveMode) throws Exception {
        return singleToFuture(commander.shutdown(saveMode));
    }

    @Override
    public <T> Future<List<T>> sinter(@RedisProtocolSupport.Key final Buffer key) throws Exception {
        return singleToFuture(commander.sinter(key));
    }

    @Override
    public <T> Future<List<T>> sinter(@RedisProtocolSupport.Key final Buffer key1,
                                      @RedisProtocolSupport.Key final Buffer key2) throws Exception {
        return singleToFuture(commander.sinter(key1, key2));
    }

    @Override
    public <T> Future<List<T>> sinter(@RedisProtocolSupport.Key final Buffer key1,
                                      @RedisProtocolSupport.Key final Buffer key2,
                                      @RedisProtocolSupport.Key final Buffer key3) throws Exception {
        return singleToFuture(commander.sinter(key1, key2, key3));
    }

    @Override
    public <T> Future<List<T>> sinter(@RedisProtocolSupport.Key final Collection<Buffer> keys) throws Exception {
        return singleToFuture(commander.sinter(keys));
    }

    @Override
    public Future<Long> sinterstore(@RedisProtocolSupport.Key final Buffer destination,
                                    @RedisProtocolSupport.Key final Buffer key) throws Exception {
        return singleToFuture(commander.sinterstore(destination, key));
    }

    @Override
    public Future<Long> sinterstore(@RedisProtocolSupport.Key final Buffer destination,
                                    @RedisProtocolSupport.Key final Buffer key1,
                                    @RedisProtocolSupport.Key final Buffer key2) throws Exception {
        return singleToFuture(commander.sinterstore(destination, key1, key2));
    }

    @Override
    public Future<Long> sinterstore(@RedisProtocolSupport.Key final Buffer destination,
                                    @RedisProtocolSupport.Key final Buffer key1,
                                    @RedisProtocolSupport.Key final Buffer key2,
                                    @RedisProtocolSupport.Key final Buffer key3) throws Exception {
        return singleToFuture(commander.sinterstore(destination, key1, key2, key3));
    }

    @Override
    public Future<Long> sinterstore(@RedisProtocolSupport.Key final Buffer destination,
                                    @RedisProtocolSupport.Key final Collection<Buffer> keys) throws Exception {
        return singleToFuture(commander.sinterstore(destination, keys));
    }

    @Override
    public Future<Long> sismember(@RedisProtocolSupport.Key final Buffer key, final Buffer member) throws Exception {
        return singleToFuture(commander.sismember(key, member));
    }

    @Override
    public Future<String> slaveof(final Buffer host, final Buffer port) throws Exception {
        return singleToFuture(commander.slaveof(host, port));
    }

    @Override
    public <T> Future<List<T>> slowlog(final Buffer subcommand) throws Exception {
        return singleToFuture(commander.slowlog(subcommand));
    }

    @Override
    public <T> Future<List<T>> slowlog(final Buffer subcommand, @Nullable final Buffer argument) throws Exception {
        return singleToFuture(commander.slowlog(subcommand, argument));
    }

    @Override
    public <T> Future<List<T>> smembers(@RedisProtocolSupport.Key final Buffer key) throws Exception {
        return singleToFuture(commander.smembers(key));
    }

    @Override
    public Future<Long> smove(@RedisProtocolSupport.Key final Buffer source,
                              @RedisProtocolSupport.Key final Buffer destination,
                              final Buffer member) throws Exception {
        return singleToFuture(commander.smove(source, destination, member));
    }

    @Override
    public <T> Future<List<T>> sort(@RedisProtocolSupport.Key final Buffer key) throws Exception {
        return singleToFuture(commander.sort(key));
    }

    @Override
    public <T> Future<List<T>> sort(@RedisProtocolSupport.Key final Buffer key, @Nullable final Buffer byPattern,
                                    @Nullable final RedisProtocolSupport.OffsetCount offsetCount,
                                    final Collection<Buffer> getPatterns,
                                    @Nullable final RedisProtocolSupport.SortOrder order,
                                    @Nullable final RedisProtocolSupport.SortSorting sorting) throws Exception {
        return singleToFuture(commander.sort(key, byPattern, offsetCount, getPatterns, order, sorting));
    }

    @Override
    public Future<Long> sort(@RedisProtocolSupport.Key final Buffer key,
                             @RedisProtocolSupport.Key final Buffer storeDestination) throws Exception {
        return singleToFuture(commander.sort(key, storeDestination));
    }

    @Override
    public Future<Long> sort(@RedisProtocolSupport.Key final Buffer key,
                             @RedisProtocolSupport.Key final Buffer storeDestination, @Nullable final Buffer byPattern,
                             @Nullable final RedisProtocolSupport.OffsetCount offsetCount,
                             final Collection<Buffer> getPatterns, @Nullable final RedisProtocolSupport.SortOrder order,
                             @Nullable final RedisProtocolSupport.SortSorting sorting) throws Exception {
        return singleToFuture(
                    commander.sort(key, storeDestination, byPattern, offsetCount, getPatterns, order, sorting));
    }

    @Override
    public Future<Buffer> spop(@RedisProtocolSupport.Key final Buffer key) throws Exception {
        return singleToFuture(commander.spop(key));
    }

    @Override
    public Future<Buffer> spop(@RedisProtocolSupport.Key final Buffer key,
                               @Nullable final Long count) throws Exception {
        return singleToFuture(commander.spop(key, count));
    }

    @Override
    public Future<Buffer> srandmember(@RedisProtocolSupport.Key final Buffer key) throws Exception {
        return singleToFuture(commander.srandmember(key));
    }

    @Override
    public Future<List<String>> srandmember(@RedisProtocolSupport.Key final Buffer key,
                                            final long count) throws Exception {
        return singleToFuture(commander.srandmember(key, count));
    }

    @Override
    public Future<Long> srem(@RedisProtocolSupport.Key final Buffer key, final Buffer member) throws Exception {
        return singleToFuture(commander.srem(key, member));
    }

    @Override
    public Future<Long> srem(@RedisProtocolSupport.Key final Buffer key, final Buffer member1,
                             final Buffer member2) throws Exception {
        return singleToFuture(commander.srem(key, member1, member2));
    }

    @Override
    public Future<Long> srem(@RedisProtocolSupport.Key final Buffer key, final Buffer member1, final Buffer member2,
                             final Buffer member3) throws Exception {
        return singleToFuture(commander.srem(key, member1, member2, member3));
    }

    @Override
    public Future<Long> srem(@RedisProtocolSupport.Key final Buffer key,
                             final Collection<Buffer> members) throws Exception {
        return singleToFuture(commander.srem(key, members));
    }

    @Override
    public <T> Future<List<T>> sscan(@RedisProtocolSupport.Key final Buffer key, final long cursor) throws Exception {
        return singleToFuture(commander.sscan(key, cursor));
    }

    @Override
    public <T> Future<List<T>> sscan(@RedisProtocolSupport.Key final Buffer key, final long cursor,
                                     @Nullable final Buffer matchPattern, @Nullable final Long count) throws Exception {
        return singleToFuture(commander.sscan(key, cursor, matchPattern, count));
    }

    @Override
    public Future<Long> strlen(@RedisProtocolSupport.Key final Buffer key) throws Exception {
        return singleToFuture(commander.strlen(key));
    }

    @Override
    public <T> Future<List<T>> sunion(@RedisProtocolSupport.Key final Buffer key) throws Exception {
        return singleToFuture(commander.sunion(key));
    }

    @Override
    public <T> Future<List<T>> sunion(@RedisProtocolSupport.Key final Buffer key1,
                                      @RedisProtocolSupport.Key final Buffer key2) throws Exception {
        return singleToFuture(commander.sunion(key1, key2));
    }

    @Override
    public <T> Future<List<T>> sunion(@RedisProtocolSupport.Key final Buffer key1,
                                      @RedisProtocolSupport.Key final Buffer key2,
                                      @RedisProtocolSupport.Key final Buffer key3) throws Exception {
        return singleToFuture(commander.sunion(key1, key2, key3));
    }

    @Override
    public <T> Future<List<T>> sunion(@RedisProtocolSupport.Key final Collection<Buffer> keys) throws Exception {
        return singleToFuture(commander.sunion(keys));
    }

    @Override
    public Future<Long> sunionstore(@RedisProtocolSupport.Key final Buffer destination,
                                    @RedisProtocolSupport.Key final Buffer key) throws Exception {
        return singleToFuture(commander.sunionstore(destination, key));
    }

    @Override
    public Future<Long> sunionstore(@RedisProtocolSupport.Key final Buffer destination,
                                    @RedisProtocolSupport.Key final Buffer key1,
                                    @RedisProtocolSupport.Key final Buffer key2) throws Exception {
        return singleToFuture(commander.sunionstore(destination, key1, key2));
    }

    @Override
    public Future<Long> sunionstore(@RedisProtocolSupport.Key final Buffer destination,
                                    @RedisProtocolSupport.Key final Buffer key1,
                                    @RedisProtocolSupport.Key final Buffer key2,
                                    @RedisProtocolSupport.Key final Buffer key3) throws Exception {
        return singleToFuture(commander.sunionstore(destination, key1, key2, key3));
    }

    @Override
    public Future<Long> sunionstore(@RedisProtocolSupport.Key final Buffer destination,
                                    @RedisProtocolSupport.Key final Collection<Buffer> keys) throws Exception {
        return singleToFuture(commander.sunionstore(destination, keys));
    }

    @Override
    public Future<String> swapdb(final long index, final long index1) throws Exception {
        return singleToFuture(commander.swapdb(index, index1));
    }

    @Override
    public <T> Future<List<T>> time() throws Exception {
        return singleToFuture(commander.time());
    }

    @Override
    public Future<Long> touch(@RedisProtocolSupport.Key final Buffer key) throws Exception {
        return singleToFuture(commander.touch(key));
    }

    @Override
    public Future<Long> touch(@RedisProtocolSupport.Key final Buffer key1,
                              @RedisProtocolSupport.Key final Buffer key2) throws Exception {
        return singleToFuture(commander.touch(key1, key2));
    }

    @Override
    public Future<Long> touch(@RedisProtocolSupport.Key final Buffer key1, @RedisProtocolSupport.Key final Buffer key2,
                              @RedisProtocolSupport.Key final Buffer key3) throws Exception {
        return singleToFuture(commander.touch(key1, key2, key3));
    }

    @Override
    public Future<Long> touch(@RedisProtocolSupport.Key final Collection<Buffer> keys) throws Exception {
        return singleToFuture(commander.touch(keys));
    }

    @Override
    public Future<Long> ttl(@RedisProtocolSupport.Key final Buffer key) throws Exception {
        return singleToFuture(commander.ttl(key));
    }

    @Override
    public Future<String> type(@RedisProtocolSupport.Key final Buffer key) throws Exception {
        return singleToFuture(commander.type(key));
    }

    @Override
    public Future<Long> unlink(@RedisProtocolSupport.Key final Buffer key) throws Exception {
        return singleToFuture(commander.unlink(key));
    }

    @Override
    public Future<Long> unlink(@RedisProtocolSupport.Key final Buffer key1,
                               @RedisProtocolSupport.Key final Buffer key2) throws Exception {
        return singleToFuture(commander.unlink(key1, key2));
    }

    @Override
    public Future<Long> unlink(@RedisProtocolSupport.Key final Buffer key1, @RedisProtocolSupport.Key final Buffer key2,
                               @RedisProtocolSupport.Key final Buffer key3) throws Exception {
        return singleToFuture(commander.unlink(key1, key2, key3));
    }

    @Override
    public Future<Long> unlink(@RedisProtocolSupport.Key final Collection<Buffer> keys) throws Exception {
        return singleToFuture(commander.unlink(keys));
    }

    @Override
    public Future<String> unwatch() throws Exception {
        return singleToFuture(commander.unwatch());
    }

    @Override
    public Future<Long> wait(final long numslaves, final long timeout) throws Exception {
        return singleToFuture(commander.wait(numslaves, timeout));
    }

    @Override
    public Future<String> watch(@RedisProtocolSupport.Key final Buffer key) throws Exception {
        return singleToFuture(commander.watch(key));
    }

    @Override
    public Future<String> watch(@RedisProtocolSupport.Key final Buffer key1,
                                @RedisProtocolSupport.Key final Buffer key2) throws Exception {
        return singleToFuture(commander.watch(key1, key2));
    }

    @Override
    public Future<String> watch(@RedisProtocolSupport.Key final Buffer key1,
                                @RedisProtocolSupport.Key final Buffer key2,
                                @RedisProtocolSupport.Key final Buffer key3) throws Exception {
        return singleToFuture(commander.watch(key1, key2, key3));
    }

    @Override
    public Future<String> watch(@RedisProtocolSupport.Key final Collection<Buffer> keys) throws Exception {
        return singleToFuture(commander.watch(keys));
    }

    @Override
    public Future<Buffer> xadd(@RedisProtocolSupport.Key final Buffer key, final Buffer id, final Buffer field,
                               final Buffer value) throws Exception {
        return singleToFuture(commander.xadd(key, id, field, value));
    }

    @Override
    public Future<Buffer> xadd(@RedisProtocolSupport.Key final Buffer key, final Buffer id, final Buffer field1,
                               final Buffer value1, final Buffer field2, final Buffer value2) throws Exception {
        return singleToFuture(commander.xadd(key, id, field1, value1, field2, value2));
    }

    @Override
    public Future<Buffer> xadd(@RedisProtocolSupport.Key final Buffer key, final Buffer id, final Buffer field1,
                               final Buffer value1, final Buffer field2, final Buffer value2, final Buffer field3,
                               final Buffer value3) throws Exception {
        return singleToFuture(commander.xadd(key, id, field1, value1, field2, value2, field3, value3));
    }

    @Override
    public Future<Buffer> xadd(@RedisProtocolSupport.Key final Buffer key, final Buffer id,
                               final Collection<RedisProtocolSupport.BufferFieldValue> fieldValues) throws Exception {
        return singleToFuture(commander.xadd(key, id, fieldValues));
    }

    @Override
    public Future<Long> xlen(@RedisProtocolSupport.Key final Buffer key) throws Exception {
        return singleToFuture(commander.xlen(key));
    }

    @Override
    public <T> Future<List<T>> xpending(@RedisProtocolSupport.Key final Buffer key,
                                        final Buffer group) throws Exception {
        return singleToFuture(commander.xpending(key, group));
    }

    @Override
    public <T> Future<List<T>> xpending(@RedisProtocolSupport.Key final Buffer key, final Buffer group,
                                        @Nullable final Buffer start, @Nullable final Buffer end,
                                        @Nullable final Long count, @Nullable final Buffer consumer) throws Exception {
        return singleToFuture(commander.xpending(key, group, start, end, count, consumer));
    }

    @Override
    public <T> Future<List<T>> xrange(@RedisProtocolSupport.Key final Buffer key, final Buffer start,
                                      final Buffer end) throws Exception {
        return singleToFuture(commander.xrange(key, start, end));
    }

    @Override
    public <T> Future<List<T>> xrange(@RedisProtocolSupport.Key final Buffer key, final Buffer start, final Buffer end,
                                      @Nullable final Long count) throws Exception {
        return singleToFuture(commander.xrange(key, start, end, count));
    }

    @Override
    public <T> Future<List<T>> xread(@RedisProtocolSupport.Key final Collection<Buffer> keys,
                                     final Collection<Buffer> ids) throws Exception {
        return singleToFuture(commander.xread(keys, ids));
    }

    @Override
    public <T> Future<List<T>> xread(@Nullable final Long count, @Nullable final Long blockMilliseconds,
                                     @RedisProtocolSupport.Key final Collection<Buffer> keys,
                                     final Collection<Buffer> ids) throws Exception {
        return singleToFuture(commander.xread(count, blockMilliseconds, keys, ids));
    }

    @Override
    public <T> Future<List<T>> xreadgroup(final RedisProtocolSupport.BufferGroupConsumer groupConsumer,
                                          @RedisProtocolSupport.Key final Collection<Buffer> keys,
                                          final Collection<Buffer> ids) throws Exception {
        return singleToFuture(commander.xreadgroup(groupConsumer, keys, ids));
    }

    @Override
    public <T> Future<List<T>> xreadgroup(final RedisProtocolSupport.BufferGroupConsumer groupConsumer,
                                          @Nullable final Long count, @Nullable final Long blockMilliseconds,
                                          @RedisProtocolSupport.Key final Collection<Buffer> keys,
                                          final Collection<Buffer> ids) throws Exception {
        return singleToFuture(commander.xreadgroup(groupConsumer, count, blockMilliseconds, keys, ids));
    }

    @Override
    public <T> Future<List<T>> xrevrange(@RedisProtocolSupport.Key final Buffer key, final Buffer end,
                                         final Buffer start) throws Exception {
        return singleToFuture(commander.xrevrange(key, end, start));
    }

    @Override
    public <T> Future<List<T>> xrevrange(@RedisProtocolSupport.Key final Buffer key, final Buffer end,
                                         final Buffer start, @Nullable final Long count) throws Exception {
        return singleToFuture(commander.xrevrange(key, end, start, count));
    }

    @Override
    public Future<Long> zadd(@RedisProtocolSupport.Key final Buffer key,
                             final Collection<RedisProtocolSupport.BufferScoreMember> scoreMembers) throws Exception {
        return singleToFuture(commander.zadd(key, scoreMembers));
    }

    @Override
    public Future<Long> zadd(@RedisProtocolSupport.Key final Buffer key,
                             @Nullable final RedisProtocolSupport.ZaddCondition condition,
                             @Nullable final RedisProtocolSupport.ZaddChange change, final double score,
                             final Buffer member) throws Exception {
        return singleToFuture(commander.zadd(key, condition, change, score, member));
    }

    @Override
    public Future<Long> zadd(@RedisProtocolSupport.Key final Buffer key,
                             @Nullable final RedisProtocolSupport.ZaddCondition condition,
                             @Nullable final RedisProtocolSupport.ZaddChange change, final double score1,
                             final Buffer member1, final double score2, final Buffer member2) throws Exception {
        return singleToFuture(commander.zadd(key, condition, change, score1, member1, score2, member2));
    }

    @Override
    public Future<Long> zadd(@RedisProtocolSupport.Key final Buffer key,
                             @Nullable final RedisProtocolSupport.ZaddCondition condition,
                             @Nullable final RedisProtocolSupport.ZaddChange change, final double score1,
                             final Buffer member1, final double score2, final Buffer member2, final double score3,
                             final Buffer member3) throws Exception {
        return singleToFuture(
                    commander.zadd(key, condition, change, score1, member1, score2, member2, score3, member3));
    }

    @Override
    public Future<Long> zadd(@RedisProtocolSupport.Key final Buffer key,
                             @Nullable final RedisProtocolSupport.ZaddCondition condition,
                             @Nullable final RedisProtocolSupport.ZaddChange change,
                             final Collection<RedisProtocolSupport.BufferScoreMember> scoreMembers) throws Exception {
        return singleToFuture(commander.zadd(key, condition, change, scoreMembers));
    }

    @Override
    public Future<Double> zaddIncr(@RedisProtocolSupport.Key final Buffer key,
                                   final Collection<RedisProtocolSupport.BufferScoreMember> scoreMembers) throws Exception {
        return singleToFuture(commander.zaddIncr(key, scoreMembers));
    }

    @Override
    public Future<Double> zaddIncr(@RedisProtocolSupport.Key final Buffer key,
                                   @Nullable final RedisProtocolSupport.ZaddCondition condition,
                                   @Nullable final RedisProtocolSupport.ZaddChange change, final double score,
                                   final Buffer member) throws Exception {
        return singleToFuture(commander.zaddIncr(key, condition, change, score, member));
    }

    @Override
    public Future<Double> zaddIncr(@RedisProtocolSupport.Key final Buffer key,
                                   @Nullable final RedisProtocolSupport.ZaddCondition condition,
                                   @Nullable final RedisProtocolSupport.ZaddChange change, final double score1,
                                   final Buffer member1, final double score2, final Buffer member2) throws Exception {
        return singleToFuture(commander.zaddIncr(key, condition, change, score1, member1, score2, member2));
    }

    @Override
    public Future<Double> zaddIncr(@RedisProtocolSupport.Key final Buffer key,
                                   @Nullable final RedisProtocolSupport.ZaddCondition condition,
                                   @Nullable final RedisProtocolSupport.ZaddChange change, final double score1,
                                   final Buffer member1, final double score2, final Buffer member2, final double score3,
                                   final Buffer member3) throws Exception {
        return singleToFuture(
                    commander.zaddIncr(key, condition, change, score1, member1, score2, member2, score3, member3));
    }

    @Override
    public Future<Double> zaddIncr(@RedisProtocolSupport.Key final Buffer key,
                                   @Nullable final RedisProtocolSupport.ZaddCondition condition,
                                   @Nullable final RedisProtocolSupport.ZaddChange change,
                                   final Collection<RedisProtocolSupport.BufferScoreMember> scoreMembers) throws Exception {
        return singleToFuture(commander.zaddIncr(key, condition, change, scoreMembers));
    }

    @Override
    public Future<Long> zcard(@RedisProtocolSupport.Key final Buffer key) throws Exception {
        return singleToFuture(commander.zcard(key));
    }

    @Override
    public Future<Long> zcount(@RedisProtocolSupport.Key final Buffer key, final double min,
                               final double max) throws Exception {
        return singleToFuture(commander.zcount(key, min, max));
    }

    @Override
    public Future<Double> zincrby(@RedisProtocolSupport.Key final Buffer key, final long increment,
                                  final Buffer member) throws Exception {
        return singleToFuture(commander.zincrby(key, increment, member));
    }

    @Override
    public Future<Long> zinterstore(@RedisProtocolSupport.Key final Buffer destination, final long numkeys,
                                    @RedisProtocolSupport.Key final Collection<Buffer> keys) throws Exception {
        return singleToFuture(commander.zinterstore(destination, numkeys, keys));
    }

    @Override
    public Future<Long> zinterstore(@RedisProtocolSupport.Key final Buffer destination, final long numkeys,
                                    @RedisProtocolSupport.Key final Collection<Buffer> keys,
                                    final Collection<Long> weightses,
                                    @Nullable final RedisProtocolSupport.ZinterstoreAggregate aggregate) throws Exception {
        return singleToFuture(commander.zinterstore(destination, numkeys, keys, weightses, aggregate));
    }

    @Override
    public Future<Long> zlexcount(@RedisProtocolSupport.Key final Buffer key, final Buffer min,
                                  final Buffer max) throws Exception {
        return singleToFuture(commander.zlexcount(key, min, max));
    }

    @Override
    public <T> Future<List<T>> zpopmax(@RedisProtocolSupport.Key final Buffer key) throws Exception {
        return singleToFuture(commander.zpopmax(key));
    }

    @Override
    public <T> Future<List<T>> zpopmax(@RedisProtocolSupport.Key final Buffer key,
                                       @Nullable final Long count) throws Exception {
        return singleToFuture(commander.zpopmax(key, count));
    }

    @Override
    public <T> Future<List<T>> zpopmin(@RedisProtocolSupport.Key final Buffer key) throws Exception {
        return singleToFuture(commander.zpopmin(key));
    }

    @Override
    public <T> Future<List<T>> zpopmin(@RedisProtocolSupport.Key final Buffer key,
                                       @Nullable final Long count) throws Exception {
        return singleToFuture(commander.zpopmin(key, count));
    }

    @Override
    public <T> Future<List<T>> zrange(@RedisProtocolSupport.Key final Buffer key, final long start,
                                      final long stop) throws Exception {
        return singleToFuture(commander.zrange(key, start, stop));
    }

    @Override
    public <T> Future<List<T>> zrange(@RedisProtocolSupport.Key final Buffer key, final long start, final long stop,
                                      @Nullable final RedisProtocolSupport.ZrangeWithscores withscores) throws Exception {
        return singleToFuture(commander.zrange(key, start, stop, withscores));
    }

    @Override
    public <T> Future<List<T>> zrangebylex(@RedisProtocolSupport.Key final Buffer key, final Buffer min,
                                           final Buffer max) throws Exception {
        return singleToFuture(commander.zrangebylex(key, min, max));
    }

    @Override
    public <T> Future<List<T>> zrangebylex(@RedisProtocolSupport.Key final Buffer key, final Buffer min,
                                           final Buffer max,
                                           @Nullable final RedisProtocolSupport.OffsetCount offsetCount) throws Exception {
        return singleToFuture(commander.zrangebylex(key, min, max, offsetCount));
    }

    @Override
    public <T> Future<List<T>> zrangebyscore(@RedisProtocolSupport.Key final Buffer key, final double min,
                                             final double max) throws Exception {
        return singleToFuture(commander.zrangebyscore(key, min, max));
    }

    @Override
    public <T> Future<List<T>> zrangebyscore(@RedisProtocolSupport.Key final Buffer key, final double min,
                                             final double max,
                                             @Nullable final RedisProtocolSupport.ZrangebyscoreWithscores withscores,
                                             @Nullable final RedisProtocolSupport.OffsetCount offsetCount) throws Exception {
        return singleToFuture(commander.zrangebyscore(key, min, max, withscores, offsetCount));
    }

    @Override
    public Future<Long> zrank(@RedisProtocolSupport.Key final Buffer key, final Buffer member) throws Exception {
        return singleToFuture(commander.zrank(key, member));
    }

    @Override
    public Future<Long> zrem(@RedisProtocolSupport.Key final Buffer key, final Buffer member) throws Exception {
        return singleToFuture(commander.zrem(key, member));
    }

    @Override
    public Future<Long> zrem(@RedisProtocolSupport.Key final Buffer key, final Buffer member1,
                             final Buffer member2) throws Exception {
        return singleToFuture(commander.zrem(key, member1, member2));
    }

    @Override
    public Future<Long> zrem(@RedisProtocolSupport.Key final Buffer key, final Buffer member1, final Buffer member2,
                             final Buffer member3) throws Exception {
        return singleToFuture(commander.zrem(key, member1, member2, member3));
    }

    @Override
    public Future<Long> zrem(@RedisProtocolSupport.Key final Buffer key,
                             final Collection<Buffer> members) throws Exception {
        return singleToFuture(commander.zrem(key, members));
    }

    @Override
    public Future<Long> zremrangebylex(@RedisProtocolSupport.Key final Buffer key, final Buffer min,
                                       final Buffer max) throws Exception {
        return singleToFuture(commander.zremrangebylex(key, min, max));
    }

    @Override
    public Future<Long> zremrangebyrank(@RedisProtocolSupport.Key final Buffer key, final long start,
                                        final long stop) throws Exception {
        return singleToFuture(commander.zremrangebyrank(key, start, stop));
    }

    @Override
    public Future<Long> zremrangebyscore(@RedisProtocolSupport.Key final Buffer key, final double min,
                                         final double max) throws Exception {
        return singleToFuture(commander.zremrangebyscore(key, min, max));
    }

    @Override
    public <T> Future<List<T>> zrevrange(@RedisProtocolSupport.Key final Buffer key, final long start,
                                         final long stop) throws Exception {
        return singleToFuture(commander.zrevrange(key, start, stop));
    }

    @Override
    public <T> Future<List<T>> zrevrange(@RedisProtocolSupport.Key final Buffer key, final long start, final long stop,
                                         @Nullable final RedisProtocolSupport.ZrevrangeWithscores withscores) throws Exception {
        return singleToFuture(commander.zrevrange(key, start, stop, withscores));
    }

    @Override
    public <T> Future<List<T>> zrevrangebylex(@RedisProtocolSupport.Key final Buffer key, final Buffer max,
                                              final Buffer min) throws Exception {
        return singleToFuture(commander.zrevrangebylex(key, max, min));
    }

    @Override
    public <T> Future<List<T>> zrevrangebylex(@RedisProtocolSupport.Key final Buffer key, final Buffer max,
                                              final Buffer min,
                                              @Nullable final RedisProtocolSupport.OffsetCount offsetCount) throws Exception {
        return singleToFuture(commander.zrevrangebylex(key, max, min, offsetCount));
    }

    @Override
    public <T> Future<List<T>> zrevrangebyscore(@RedisProtocolSupport.Key final Buffer key, final double max,
                                                final double min) throws Exception {
        return singleToFuture(commander.zrevrangebyscore(key, max, min));
    }

    @Override
    public <T> Future<List<T>> zrevrangebyscore(@RedisProtocolSupport.Key final Buffer key, final double max,
                                                final double min,
                                                @Nullable final RedisProtocolSupport.ZrevrangebyscoreWithscores withscores,
                                                @Nullable final RedisProtocolSupport.OffsetCount offsetCount) throws Exception {
        return singleToFuture(commander.zrevrangebyscore(key, max, min, withscores, offsetCount));
    }

    @Override
    public Future<Long> zrevrank(@RedisProtocolSupport.Key final Buffer key, final Buffer member) throws Exception {
        return singleToFuture(commander.zrevrank(key, member));
    }

    @Override
    public <T> Future<List<T>> zscan(@RedisProtocolSupport.Key final Buffer key, final long cursor) throws Exception {
        return singleToFuture(commander.zscan(key, cursor));
    }

    @Override
    public <T> Future<List<T>> zscan(@RedisProtocolSupport.Key final Buffer key, final long cursor,
                                     @Nullable final Buffer matchPattern, @Nullable final Long count) throws Exception {
        return singleToFuture(commander.zscan(key, cursor, matchPattern, count));
    }

    @Override
    public Future<Double> zscore(@RedisProtocolSupport.Key final Buffer key, final Buffer member) throws Exception {
        return singleToFuture(commander.zscore(key, member));
    }

    @Override
    public Future<Long> zunionstore(@RedisProtocolSupport.Key final Buffer destination, final long numkeys,
                                    @RedisProtocolSupport.Key final Collection<Buffer> keys) throws Exception {
        return singleToFuture(commander.zunionstore(destination, numkeys, keys));
    }

    @Override
    public Future<Long> zunionstore(@RedisProtocolSupport.Key final Buffer destination, final long numkeys,
                                    @RedisProtocolSupport.Key final Collection<Buffer> keys,
                                    final Collection<Long> weightses,
                                    @Nullable final RedisProtocolSupport.ZunionstoreAggregate aggregate) throws Exception {
        return singleToFuture(commander.zunionstore(destination, numkeys, keys, weightses, aggregate));
    }
}

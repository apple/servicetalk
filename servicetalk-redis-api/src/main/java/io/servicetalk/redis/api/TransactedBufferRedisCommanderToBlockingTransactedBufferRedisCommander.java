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
import javax.annotation.Generated;
import javax.annotation.Nullable;

import static io.servicetalk.redis.api.BlockingUtils.blockingInvocation;
import static io.servicetalk.redis.api.BlockingUtils.singleToDeferredValue;

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
    public DeferredValue<Long> append(@RedisProtocolSupport.Key final Buffer key, final Buffer value) throws Exception {
        return singleToDeferredValue(commander.append(key, value));
    }

    @Override
    public DeferredValue<String> auth(final Buffer password) throws Exception {
        return singleToDeferredValue(commander.auth(password));
    }

    @Override
    public DeferredValue<String> bgrewriteaof() throws Exception {
        return singleToDeferredValue(commander.bgrewriteaof());
    }

    @Override
    public DeferredValue<String> bgsave() throws Exception {
        return singleToDeferredValue(commander.bgsave());
    }

    @Override
    public DeferredValue<Long> bitcount(@RedisProtocolSupport.Key final Buffer key) throws Exception {
        return singleToDeferredValue(commander.bitcount(key));
    }

    @Override
    public DeferredValue<Long> bitcount(@RedisProtocolSupport.Key final Buffer key, @Nullable final Long start,
                                        @Nullable final Long end) throws Exception {
        return singleToDeferredValue(commander.bitcount(key, start, end));
    }

    @Override
    public DeferredValue<List<Long>> bitfield(@RedisProtocolSupport.Key final Buffer key,
                                              final Collection<RedisProtocolSupport.BitfieldOperation> operations) throws Exception {
        return singleToDeferredValue(commander.bitfield(key, operations));
    }

    @Override
    public DeferredValue<Long> bitop(final Buffer operation, @RedisProtocolSupport.Key final Buffer destkey,
                                     @RedisProtocolSupport.Key final Buffer key) throws Exception {
        return singleToDeferredValue(commander.bitop(operation, destkey, key));
    }

    @Override
    public DeferredValue<Long> bitop(final Buffer operation, @RedisProtocolSupport.Key final Buffer destkey,
                                     @RedisProtocolSupport.Key final Buffer key1,
                                     @RedisProtocolSupport.Key final Buffer key2) throws Exception {
        return singleToDeferredValue(commander.bitop(operation, destkey, key1, key2));
    }

    @Override
    public DeferredValue<Long> bitop(final Buffer operation, @RedisProtocolSupport.Key final Buffer destkey,
                                     @RedisProtocolSupport.Key final Buffer key1,
                                     @RedisProtocolSupport.Key final Buffer key2,
                                     @RedisProtocolSupport.Key final Buffer key3) throws Exception {
        return singleToDeferredValue(commander.bitop(operation, destkey, key1, key2, key3));
    }

    @Override
    public DeferredValue<Long> bitop(final Buffer operation, @RedisProtocolSupport.Key final Buffer destkey,
                                     @RedisProtocolSupport.Key final Collection<Buffer> keys) throws Exception {
        return singleToDeferredValue(commander.bitop(operation, destkey, keys));
    }

    @Override
    public DeferredValue<Long> bitpos(@RedisProtocolSupport.Key final Buffer key, final long bit) throws Exception {
        return singleToDeferredValue(commander.bitpos(key, bit));
    }

    @Override
    public DeferredValue<Long> bitpos(@RedisProtocolSupport.Key final Buffer key, final long bit,
                                      @Nullable final Long start, @Nullable final Long end) throws Exception {
        return singleToDeferredValue(commander.bitpos(key, bit, start, end));
    }

    @Override
    public <T> DeferredValue<List<T>> blpop(@RedisProtocolSupport.Key final Collection<Buffer> keys,
                                            final long timeout) throws Exception {
        return singleToDeferredValue(commander.blpop(keys, timeout));
    }

    @Override
    public <T> DeferredValue<List<T>> brpop(@RedisProtocolSupport.Key final Collection<Buffer> keys,
                                            final long timeout) throws Exception {
        return singleToDeferredValue(commander.brpop(keys, timeout));
    }

    @Override
    public DeferredValue<Buffer> brpoplpush(@RedisProtocolSupport.Key final Buffer source,
                                            @RedisProtocolSupport.Key final Buffer destination,
                                            final long timeout) throws Exception {
        return singleToDeferredValue(commander.brpoplpush(source, destination, timeout));
    }

    @Override
    public <T> DeferredValue<List<T>> bzpopmax(@RedisProtocolSupport.Key final Collection<Buffer> keys,
                                               final long timeout) throws Exception {
        return singleToDeferredValue(commander.bzpopmax(keys, timeout));
    }

    @Override
    public <T> DeferredValue<List<T>> bzpopmin(@RedisProtocolSupport.Key final Collection<Buffer> keys,
                                               final long timeout) throws Exception {
        return singleToDeferredValue(commander.bzpopmin(keys, timeout));
    }

    @Override
    public DeferredValue<Long> clientKill(@Nullable final Long id,
                                          @Nullable final RedisProtocolSupport.ClientKillType type,
                                          @Nullable final Buffer addrIpPort,
                                          @Nullable final Buffer skipmeYesNo) throws Exception {
        return singleToDeferredValue(commander.clientKill(id, type, addrIpPort, skipmeYesNo));
    }

    @Override
    public DeferredValue<Buffer> clientList() throws Exception {
        return singleToDeferredValue(commander.clientList());
    }

    @Override
    public DeferredValue<Buffer> clientGetname() throws Exception {
        return singleToDeferredValue(commander.clientGetname());
    }

    @Override
    public DeferredValue<String> clientPause(final long timeout) throws Exception {
        return singleToDeferredValue(commander.clientPause(timeout));
    }

    @Override
    public DeferredValue<String> clientReply(final RedisProtocolSupport.ClientReplyReplyMode replyMode) throws Exception {
        return singleToDeferredValue(commander.clientReply(replyMode));
    }

    @Override
    public DeferredValue<String> clientSetname(final Buffer connectionName) throws Exception {
        return singleToDeferredValue(commander.clientSetname(connectionName));
    }

    @Override
    public DeferredValue<String> clusterAddslots(final long slot) throws Exception {
        return singleToDeferredValue(commander.clusterAddslots(slot));
    }

    @Override
    public DeferredValue<String> clusterAddslots(final long slot1, final long slot2) throws Exception {
        return singleToDeferredValue(commander.clusterAddslots(slot1, slot2));
    }

    @Override
    public DeferredValue<String> clusterAddslots(final long slot1, final long slot2,
                                                 final long slot3) throws Exception {
        return singleToDeferredValue(commander.clusterAddslots(slot1, slot2, slot3));
    }

    @Override
    public DeferredValue<String> clusterAddslots(final Collection<Long> slots) throws Exception {
        return singleToDeferredValue(commander.clusterAddslots(slots));
    }

    @Override
    public DeferredValue<Long> clusterCountFailureReports(final Buffer nodeId) throws Exception {
        return singleToDeferredValue(commander.clusterCountFailureReports(nodeId));
    }

    @Override
    public DeferredValue<Long> clusterCountkeysinslot(final long slot) throws Exception {
        return singleToDeferredValue(commander.clusterCountkeysinslot(slot));
    }

    @Override
    public DeferredValue<String> clusterDelslots(final long slot) throws Exception {
        return singleToDeferredValue(commander.clusterDelslots(slot));
    }

    @Override
    public DeferredValue<String> clusterDelslots(final long slot1, final long slot2) throws Exception {
        return singleToDeferredValue(commander.clusterDelslots(slot1, slot2));
    }

    @Override
    public DeferredValue<String> clusterDelslots(final long slot1, final long slot2,
                                                 final long slot3) throws Exception {
        return singleToDeferredValue(commander.clusterDelslots(slot1, slot2, slot3));
    }

    @Override
    public DeferredValue<String> clusterDelslots(final Collection<Long> slots) throws Exception {
        return singleToDeferredValue(commander.clusterDelslots(slots));
    }

    @Override
    public DeferredValue<String> clusterFailover() throws Exception {
        return singleToDeferredValue(commander.clusterFailover());
    }

    @Override
    public DeferredValue<String> clusterFailover(@Nullable final RedisProtocolSupport.ClusterFailoverOptions options) throws Exception {
        return singleToDeferredValue(commander.clusterFailover(options));
    }

    @Override
    public DeferredValue<String> clusterForget(final Buffer nodeId) throws Exception {
        return singleToDeferredValue(commander.clusterForget(nodeId));
    }

    @Override
    public <T> DeferredValue<List<T>> clusterGetkeysinslot(final long slot, final long count) throws Exception {
        return singleToDeferredValue(commander.clusterGetkeysinslot(slot, count));
    }

    @Override
    public DeferredValue<Buffer> clusterInfo() throws Exception {
        return singleToDeferredValue(commander.clusterInfo());
    }

    @Override
    public DeferredValue<Long> clusterKeyslot(final Buffer key) throws Exception {
        return singleToDeferredValue(commander.clusterKeyslot(key));
    }

    @Override
    public DeferredValue<String> clusterMeet(final Buffer ip, final long port) throws Exception {
        return singleToDeferredValue(commander.clusterMeet(ip, port));
    }

    @Override
    public DeferredValue<Buffer> clusterNodes() throws Exception {
        return singleToDeferredValue(commander.clusterNodes());
    }

    @Override
    public DeferredValue<String> clusterReplicate(final Buffer nodeId) throws Exception {
        return singleToDeferredValue(commander.clusterReplicate(nodeId));
    }

    @Override
    public DeferredValue<String> clusterReset() throws Exception {
        return singleToDeferredValue(commander.clusterReset());
    }

    @Override
    public DeferredValue<String> clusterReset(@Nullable final RedisProtocolSupport.ClusterResetResetType resetType) throws Exception {
        return singleToDeferredValue(commander.clusterReset(resetType));
    }

    @Override
    public DeferredValue<String> clusterSaveconfig() throws Exception {
        return singleToDeferredValue(commander.clusterSaveconfig());
    }

    @Override
    public DeferredValue<String> clusterSetConfigEpoch(final long configEpoch) throws Exception {
        return singleToDeferredValue(commander.clusterSetConfigEpoch(configEpoch));
    }

    @Override
    public DeferredValue<String> clusterSetslot(final long slot,
                                                final RedisProtocolSupport.ClusterSetslotSubcommand subcommand) throws Exception {
        return singleToDeferredValue(commander.clusterSetslot(slot, subcommand));
    }

    @Override
    public DeferredValue<String> clusterSetslot(final long slot,
                                                final RedisProtocolSupport.ClusterSetslotSubcommand subcommand,
                                                @Nullable final Buffer nodeId) throws Exception {
        return singleToDeferredValue(commander.clusterSetslot(slot, subcommand, nodeId));
    }

    @Override
    public DeferredValue<Buffer> clusterSlaves(final Buffer nodeId) throws Exception {
        return singleToDeferredValue(commander.clusterSlaves(nodeId));
    }

    @Override
    public <T> DeferredValue<List<T>> clusterSlots() throws Exception {
        return singleToDeferredValue(commander.clusterSlots());
    }

    @Override
    public <T> DeferredValue<List<T>> command() throws Exception {
        return singleToDeferredValue(commander.command());
    }

    @Override
    public DeferredValue<Long> commandCount() throws Exception {
        return singleToDeferredValue(commander.commandCount());
    }

    @Override
    public <T> DeferredValue<List<T>> commandGetkeys() throws Exception {
        return singleToDeferredValue(commander.commandGetkeys());
    }

    @Override
    public <T> DeferredValue<List<T>> commandInfo(final Buffer commandName) throws Exception {
        return singleToDeferredValue(commander.commandInfo(commandName));
    }

    @Override
    public <T> DeferredValue<List<T>> commandInfo(final Buffer commandName1,
                                                  final Buffer commandName2) throws Exception {
        return singleToDeferredValue(commander.commandInfo(commandName1, commandName2));
    }

    @Override
    public <T> DeferredValue<List<T>> commandInfo(final Buffer commandName1, final Buffer commandName2,
                                                  final Buffer commandName3) throws Exception {
        return singleToDeferredValue(commander.commandInfo(commandName1, commandName2, commandName3));
    }

    @Override
    public <T> DeferredValue<List<T>> commandInfo(final Collection<Buffer> commandNames) throws Exception {
        return singleToDeferredValue(commander.commandInfo(commandNames));
    }

    @Override
    public <T> DeferredValue<List<T>> configGet(final Buffer parameter) throws Exception {
        return singleToDeferredValue(commander.configGet(parameter));
    }

    @Override
    public DeferredValue<String> configRewrite() throws Exception {
        return singleToDeferredValue(commander.configRewrite());
    }

    @Override
    public DeferredValue<String> configSet(final Buffer parameter, final Buffer value) throws Exception {
        return singleToDeferredValue(commander.configSet(parameter, value));
    }

    @Override
    public DeferredValue<String> configResetstat() throws Exception {
        return singleToDeferredValue(commander.configResetstat());
    }

    @Override
    public DeferredValue<Long> dbsize() throws Exception {
        return singleToDeferredValue(commander.dbsize());
    }

    @Override
    public DeferredValue<String> debugObject(@RedisProtocolSupport.Key final Buffer key) throws Exception {
        return singleToDeferredValue(commander.debugObject(key));
    }

    @Override
    public DeferredValue<String> debugSegfault() throws Exception {
        return singleToDeferredValue(commander.debugSegfault());
    }

    @Override
    public DeferredValue<Long> decr(@RedisProtocolSupport.Key final Buffer key) throws Exception {
        return singleToDeferredValue(commander.decr(key));
    }

    @Override
    public DeferredValue<Long> decrby(@RedisProtocolSupport.Key final Buffer key,
                                      final long decrement) throws Exception {
        return singleToDeferredValue(commander.decrby(key, decrement));
    }

    @Override
    public DeferredValue<Long> del(@RedisProtocolSupport.Key final Buffer key) throws Exception {
        return singleToDeferredValue(commander.del(key));
    }

    @Override
    public DeferredValue<Long> del(@RedisProtocolSupport.Key final Buffer key1,
                                   @RedisProtocolSupport.Key final Buffer key2) throws Exception {
        return singleToDeferredValue(commander.del(key1, key2));
    }

    @Override
    public DeferredValue<Long> del(@RedisProtocolSupport.Key final Buffer key1,
                                   @RedisProtocolSupport.Key final Buffer key2,
                                   @RedisProtocolSupport.Key final Buffer key3) throws Exception {
        return singleToDeferredValue(commander.del(key1, key2, key3));
    }

    @Override
    public DeferredValue<Long> del(@RedisProtocolSupport.Key final Collection<Buffer> keys) throws Exception {
        return singleToDeferredValue(commander.del(keys));
    }

    @Override
    public String discard() throws Exception {
        return blockingInvocation(commander.discard());
    }

    @Override
    public DeferredValue<Buffer> dump(@RedisProtocolSupport.Key final Buffer key) throws Exception {
        return singleToDeferredValue(commander.dump(key));
    }

    @Override
    public DeferredValue<Buffer> echo(final Buffer message) throws Exception {
        return singleToDeferredValue(commander.echo(message));
    }

    @Override
    public DeferredValue<Buffer> eval(final Buffer script, final long numkeys,
                                      @RedisProtocolSupport.Key final Collection<Buffer> keys,
                                      final Collection<Buffer> args) throws Exception {
        return singleToDeferredValue(commander.eval(script, numkeys, keys, args));
    }

    @Override
    public <T> DeferredValue<List<T>> evalList(final Buffer script, final long numkeys,
                                               @RedisProtocolSupport.Key final Collection<Buffer> keys,
                                               final Collection<Buffer> args) throws Exception {
        return singleToDeferredValue(commander.evalList(script, numkeys, keys, args));
    }

    @Override
    public DeferredValue<Long> evalLong(final Buffer script, final long numkeys,
                                        @RedisProtocolSupport.Key final Collection<Buffer> keys,
                                        final Collection<Buffer> args) throws Exception {
        return singleToDeferredValue(commander.evalLong(script, numkeys, keys, args));
    }

    @Override
    public DeferredValue<Buffer> evalsha(final Buffer sha1, final long numkeys,
                                         @RedisProtocolSupport.Key final Collection<Buffer> keys,
                                         final Collection<Buffer> args) throws Exception {
        return singleToDeferredValue(commander.evalsha(sha1, numkeys, keys, args));
    }

    @Override
    public <T> DeferredValue<List<T>> evalshaList(final Buffer sha1, final long numkeys,
                                                  @RedisProtocolSupport.Key final Collection<Buffer> keys,
                                                  final Collection<Buffer> args) throws Exception {
        return singleToDeferredValue(commander.evalshaList(sha1, numkeys, keys, args));
    }

    @Override
    public DeferredValue<Long> evalshaLong(final Buffer sha1, final long numkeys,
                                           @RedisProtocolSupport.Key final Collection<Buffer> keys,
                                           final Collection<Buffer> args) throws Exception {
        return singleToDeferredValue(commander.evalshaLong(sha1, numkeys, keys, args));
    }

    @Override
    public void exec() throws Exception {
        blockingInvocation(commander.exec());
    }

    @Override
    public DeferredValue<Long> exists(@RedisProtocolSupport.Key final Buffer key) throws Exception {
        return singleToDeferredValue(commander.exists(key));
    }

    @Override
    public DeferredValue<Long> exists(@RedisProtocolSupport.Key final Buffer key1,
                                      @RedisProtocolSupport.Key final Buffer key2) throws Exception {
        return singleToDeferredValue(commander.exists(key1, key2));
    }

    @Override
    public DeferredValue<Long> exists(@RedisProtocolSupport.Key final Buffer key1,
                                      @RedisProtocolSupport.Key final Buffer key2,
                                      @RedisProtocolSupport.Key final Buffer key3) throws Exception {
        return singleToDeferredValue(commander.exists(key1, key2, key3));
    }

    @Override
    public DeferredValue<Long> exists(@RedisProtocolSupport.Key final Collection<Buffer> keys) throws Exception {
        return singleToDeferredValue(commander.exists(keys));
    }

    @Override
    public DeferredValue<Long> expire(@RedisProtocolSupport.Key final Buffer key, final long seconds) throws Exception {
        return singleToDeferredValue(commander.expire(key, seconds));
    }

    @Override
    public DeferredValue<Long> expireat(@RedisProtocolSupport.Key final Buffer key,
                                        final long timestamp) throws Exception {
        return singleToDeferredValue(commander.expireat(key, timestamp));
    }

    @Override
    public DeferredValue<String> flushall() throws Exception {
        return singleToDeferredValue(commander.flushall());
    }

    @Override
    public DeferredValue<String> flushall(@Nullable final RedisProtocolSupport.FlushallAsync async) throws Exception {
        return singleToDeferredValue(commander.flushall(async));
    }

    @Override
    public DeferredValue<String> flushdb() throws Exception {
        return singleToDeferredValue(commander.flushdb());
    }

    @Override
    public DeferredValue<String> flushdb(@Nullable final RedisProtocolSupport.FlushdbAsync async) throws Exception {
        return singleToDeferredValue(commander.flushdb(async));
    }

    @Override
    public DeferredValue<Long> geoadd(@RedisProtocolSupport.Key final Buffer key, final double longitude,
                                      final double latitude, final Buffer member) throws Exception {
        return singleToDeferredValue(commander.geoadd(key, longitude, latitude, member));
    }

    @Override
    public DeferredValue<Long> geoadd(@RedisProtocolSupport.Key final Buffer key, final double longitude1,
                                      final double latitude1, final Buffer member1, final double longitude2,
                                      final double latitude2, final Buffer member2) throws Exception {
        return singleToDeferredValue(
                    commander.geoadd(key, longitude1, latitude1, member1, longitude2, latitude2, member2));
    }

    @Override
    public DeferredValue<Long> geoadd(@RedisProtocolSupport.Key final Buffer key, final double longitude1,
                                      final double latitude1, final Buffer member1, final double longitude2,
                                      final double latitude2, final Buffer member2, final double longitude3,
                                      final double latitude3, final Buffer member3) throws Exception {
        return singleToDeferredValue(commander.geoadd(key, longitude1, latitude1, member1, longitude2, latitude2,
                    member2, longitude3, latitude3, member3));
    }

    @Override
    public DeferredValue<Long> geoadd(@RedisProtocolSupport.Key final Buffer key,
                                      final Collection<RedisProtocolSupport.BufferLongitudeLatitudeMember> longitudeLatitudeMembers) throws Exception {
        return singleToDeferredValue(commander.geoadd(key, longitudeLatitudeMembers));
    }

    @Override
    public DeferredValue<Double> geodist(@RedisProtocolSupport.Key final Buffer key, final Buffer member1,
                                         final Buffer member2) throws Exception {
        return singleToDeferredValue(commander.geodist(key, member1, member2));
    }

    @Override
    public DeferredValue<Double> geodist(@RedisProtocolSupport.Key final Buffer key, final Buffer member1,
                                         final Buffer member2, @Nullable final Buffer unit) throws Exception {
        return singleToDeferredValue(commander.geodist(key, member1, member2, unit));
    }

    @Override
    public <T> DeferredValue<List<T>> geohash(@RedisProtocolSupport.Key final Buffer key,
                                              final Buffer member) throws Exception {
        return singleToDeferredValue(commander.geohash(key, member));
    }

    @Override
    public <T> DeferredValue<List<T>> geohash(@RedisProtocolSupport.Key final Buffer key, final Buffer member1,
                                              final Buffer member2) throws Exception {
        return singleToDeferredValue(commander.geohash(key, member1, member2));
    }

    @Override
    public <T> DeferredValue<List<T>> geohash(@RedisProtocolSupport.Key final Buffer key, final Buffer member1,
                                              final Buffer member2, final Buffer member3) throws Exception {
        return singleToDeferredValue(commander.geohash(key, member1, member2, member3));
    }

    @Override
    public <T> DeferredValue<List<T>> geohash(@RedisProtocolSupport.Key final Buffer key,
                                              final Collection<Buffer> members) throws Exception {
        return singleToDeferredValue(commander.geohash(key, members));
    }

    @Override
    public <T> DeferredValue<List<T>> geopos(@RedisProtocolSupport.Key final Buffer key,
                                             final Buffer member) throws Exception {
        return singleToDeferredValue(commander.geopos(key, member));
    }

    @Override
    public <T> DeferredValue<List<T>> geopos(@RedisProtocolSupport.Key final Buffer key, final Buffer member1,
                                             final Buffer member2) throws Exception {
        return singleToDeferredValue(commander.geopos(key, member1, member2));
    }

    @Override
    public <T> DeferredValue<List<T>> geopos(@RedisProtocolSupport.Key final Buffer key, final Buffer member1,
                                             final Buffer member2, final Buffer member3) throws Exception {
        return singleToDeferredValue(commander.geopos(key, member1, member2, member3));
    }

    @Override
    public <T> DeferredValue<List<T>> geopos(@RedisProtocolSupport.Key final Buffer key,
                                             final Collection<Buffer> members) throws Exception {
        return singleToDeferredValue(commander.geopos(key, members));
    }

    @Override
    public <T> DeferredValue<List<T>> georadius(@RedisProtocolSupport.Key final Buffer key, final double longitude,
                                                final double latitude, final double radius,
                                                final RedisProtocolSupport.GeoradiusUnit unit) throws Exception {
        return singleToDeferredValue(commander.georadius(key, longitude, latitude, radius, unit));
    }

    @Override
    public <T> DeferredValue<List<T>> georadius(@RedisProtocolSupport.Key final Buffer key, final double longitude,
                                                final double latitude, final double radius,
                                                final RedisProtocolSupport.GeoradiusUnit unit,
                                                @Nullable final RedisProtocolSupport.GeoradiusWithcoord withcoord,
                                                @Nullable final RedisProtocolSupport.GeoradiusWithdist withdist,
                                                @Nullable final RedisProtocolSupport.GeoradiusWithhash withhash,
                                                @Nullable final Long count,
                                                @Nullable final RedisProtocolSupport.GeoradiusOrder order,
                                                @Nullable @RedisProtocolSupport.Key final Buffer storeKey,
                                                @Nullable @RedisProtocolSupport.Key final Buffer storedistKey) throws Exception {
        return singleToDeferredValue(commander.georadius(key, longitude, latitude, radius, unit, withcoord, withdist,
                    withhash, count, order, storeKey, storedistKey));
    }

    @Override
    public <T> DeferredValue<List<T>> georadiusbymember(@RedisProtocolSupport.Key final Buffer key, final Buffer member,
                                                        final double radius,
                                                        final RedisProtocolSupport.GeoradiusbymemberUnit unit) throws Exception {
        return singleToDeferredValue(commander.georadiusbymember(key, member, radius, unit));
    }

    @Override
    public <T> DeferredValue<List<T>> georadiusbymember(@RedisProtocolSupport.Key final Buffer key, final Buffer member,
                                                        final double radius,
                                                        final RedisProtocolSupport.GeoradiusbymemberUnit unit,
                                                        @Nullable final RedisProtocolSupport.GeoradiusbymemberWithcoord withcoord,
                                                        @Nullable final RedisProtocolSupport.GeoradiusbymemberWithdist withdist,
                                                        @Nullable final RedisProtocolSupport.GeoradiusbymemberWithhash withhash,
                                                        @Nullable final Long count,
                                                        @Nullable final RedisProtocolSupport.GeoradiusbymemberOrder order,
                                                        @Nullable @RedisProtocolSupport.Key final Buffer storeKey,
                                                        @Nullable @RedisProtocolSupport.Key final Buffer storedistKey) throws Exception {
        return singleToDeferredValue(commander.georadiusbymember(key, member, radius, unit, withcoord, withdist,
                    withhash, count, order, storeKey, storedistKey));
    }

    @Override
    public DeferredValue<Buffer> get(@RedisProtocolSupport.Key final Buffer key) throws Exception {
        return singleToDeferredValue(commander.get(key));
    }

    @Override
    public DeferredValue<Long> getbit(@RedisProtocolSupport.Key final Buffer key, final long offset) throws Exception {
        return singleToDeferredValue(commander.getbit(key, offset));
    }

    @Override
    public DeferredValue<Buffer> getrange(@RedisProtocolSupport.Key final Buffer key, final long start,
                                          final long end) throws Exception {
        return singleToDeferredValue(commander.getrange(key, start, end));
    }

    @Override
    public DeferredValue<Buffer> getset(@RedisProtocolSupport.Key final Buffer key,
                                        final Buffer value) throws Exception {
        return singleToDeferredValue(commander.getset(key, value));
    }

    @Override
    public DeferredValue<Long> hdel(@RedisProtocolSupport.Key final Buffer key, final Buffer field) throws Exception {
        return singleToDeferredValue(commander.hdel(key, field));
    }

    @Override
    public DeferredValue<Long> hdel(@RedisProtocolSupport.Key final Buffer key, final Buffer field1,
                                    final Buffer field2) throws Exception {
        return singleToDeferredValue(commander.hdel(key, field1, field2));
    }

    @Override
    public DeferredValue<Long> hdel(@RedisProtocolSupport.Key final Buffer key, final Buffer field1,
                                    final Buffer field2, final Buffer field3) throws Exception {
        return singleToDeferredValue(commander.hdel(key, field1, field2, field3));
    }

    @Override
    public DeferredValue<Long> hdel(@RedisProtocolSupport.Key final Buffer key,
                                    final Collection<Buffer> fields) throws Exception {
        return singleToDeferredValue(commander.hdel(key, fields));
    }

    @Override
    public DeferredValue<Long> hexists(@RedisProtocolSupport.Key final Buffer key,
                                       final Buffer field) throws Exception {
        return singleToDeferredValue(commander.hexists(key, field));
    }

    @Override
    public DeferredValue<Buffer> hget(@RedisProtocolSupport.Key final Buffer key, final Buffer field) throws Exception {
        return singleToDeferredValue(commander.hget(key, field));
    }

    @Override
    public <T> DeferredValue<List<T>> hgetall(@RedisProtocolSupport.Key final Buffer key) throws Exception {
        return singleToDeferredValue(commander.hgetall(key));
    }

    @Override
    public DeferredValue<Long> hincrby(@RedisProtocolSupport.Key final Buffer key, final Buffer field,
                                       final long increment) throws Exception {
        return singleToDeferredValue(commander.hincrby(key, field, increment));
    }

    @Override
    public DeferredValue<Double> hincrbyfloat(@RedisProtocolSupport.Key final Buffer key, final Buffer field,
                                              final double increment) throws Exception {
        return singleToDeferredValue(commander.hincrbyfloat(key, field, increment));
    }

    @Override
    public <T> DeferredValue<List<T>> hkeys(@RedisProtocolSupport.Key final Buffer key) throws Exception {
        return singleToDeferredValue(commander.hkeys(key));
    }

    @Override
    public DeferredValue<Long> hlen(@RedisProtocolSupport.Key final Buffer key) throws Exception {
        return singleToDeferredValue(commander.hlen(key));
    }

    @Override
    public <T> DeferredValue<List<T>> hmget(@RedisProtocolSupport.Key final Buffer key,
                                            final Buffer field) throws Exception {
        return singleToDeferredValue(commander.hmget(key, field));
    }

    @Override
    public <T> DeferredValue<List<T>> hmget(@RedisProtocolSupport.Key final Buffer key, final Buffer field1,
                                            final Buffer field2) throws Exception {
        return singleToDeferredValue(commander.hmget(key, field1, field2));
    }

    @Override
    public <T> DeferredValue<List<T>> hmget(@RedisProtocolSupport.Key final Buffer key, final Buffer field1,
                                            final Buffer field2, final Buffer field3) throws Exception {
        return singleToDeferredValue(commander.hmget(key, field1, field2, field3));
    }

    @Override
    public <T> DeferredValue<List<T>> hmget(@RedisProtocolSupport.Key final Buffer key,
                                            final Collection<Buffer> fields) throws Exception {
        return singleToDeferredValue(commander.hmget(key, fields));
    }

    @Override
    public DeferredValue<String> hmset(@RedisProtocolSupport.Key final Buffer key, final Buffer field,
                                       final Buffer value) throws Exception {
        return singleToDeferredValue(commander.hmset(key, field, value));
    }

    @Override
    public DeferredValue<String> hmset(@RedisProtocolSupport.Key final Buffer key, final Buffer field1,
                                       final Buffer value1, final Buffer field2, final Buffer value2) throws Exception {
        return singleToDeferredValue(commander.hmset(key, field1, value1, field2, value2));
    }

    @Override
    public DeferredValue<String> hmset(@RedisProtocolSupport.Key final Buffer key, final Buffer field1,
                                       final Buffer value1, final Buffer field2, final Buffer value2,
                                       final Buffer field3, final Buffer value3) throws Exception {
        return singleToDeferredValue(commander.hmset(key, field1, value1, field2, value2, field3, value3));
    }

    @Override
    public DeferredValue<String> hmset(@RedisProtocolSupport.Key final Buffer key,
                                       final Collection<RedisProtocolSupport.BufferFieldValue> fieldValues) throws Exception {
        return singleToDeferredValue(commander.hmset(key, fieldValues));
    }

    @Override
    public <T> DeferredValue<List<T>> hscan(@RedisProtocolSupport.Key final Buffer key,
                                            final long cursor) throws Exception {
        return singleToDeferredValue(commander.hscan(key, cursor));
    }

    @Override
    public <T> DeferredValue<List<T>> hscan(@RedisProtocolSupport.Key final Buffer key, final long cursor,
                                            @Nullable final Buffer matchPattern,
                                            @Nullable final Long count) throws Exception {
        return singleToDeferredValue(commander.hscan(key, cursor, matchPattern, count));
    }

    @Override
    public DeferredValue<Long> hset(@RedisProtocolSupport.Key final Buffer key, final Buffer field,
                                    final Buffer value) throws Exception {
        return singleToDeferredValue(commander.hset(key, field, value));
    }

    @Override
    public DeferredValue<Long> hsetnx(@RedisProtocolSupport.Key final Buffer key, final Buffer field,
                                      final Buffer value) throws Exception {
        return singleToDeferredValue(commander.hsetnx(key, field, value));
    }

    @Override
    public DeferredValue<Long> hstrlen(@RedisProtocolSupport.Key final Buffer key,
                                       final Buffer field) throws Exception {
        return singleToDeferredValue(commander.hstrlen(key, field));
    }

    @Override
    public <T> DeferredValue<List<T>> hvals(@RedisProtocolSupport.Key final Buffer key) throws Exception {
        return singleToDeferredValue(commander.hvals(key));
    }

    @Override
    public DeferredValue<Long> incr(@RedisProtocolSupport.Key final Buffer key) throws Exception {
        return singleToDeferredValue(commander.incr(key));
    }

    @Override
    public DeferredValue<Long> incrby(@RedisProtocolSupport.Key final Buffer key,
                                      final long increment) throws Exception {
        return singleToDeferredValue(commander.incrby(key, increment));
    }

    @Override
    public DeferredValue<Double> incrbyfloat(@RedisProtocolSupport.Key final Buffer key,
                                             final double increment) throws Exception {
        return singleToDeferredValue(commander.incrbyfloat(key, increment));
    }

    @Override
    public DeferredValue<Buffer> info() throws Exception {
        return singleToDeferredValue(commander.info());
    }

    @Override
    public DeferredValue<Buffer> info(@Nullable final Buffer section) throws Exception {
        return singleToDeferredValue(commander.info(section));
    }

    @Override
    public <T> DeferredValue<List<T>> keys(final Buffer pattern) throws Exception {
        return singleToDeferredValue(commander.keys(pattern));
    }

    @Override
    public DeferredValue<Long> lastsave() throws Exception {
        return singleToDeferredValue(commander.lastsave());
    }

    @Override
    public DeferredValue<Buffer> lindex(@RedisProtocolSupport.Key final Buffer key, final long index) throws Exception {
        return singleToDeferredValue(commander.lindex(key, index));
    }

    @Override
    public DeferredValue<Long> linsert(@RedisProtocolSupport.Key final Buffer key,
                                       final RedisProtocolSupport.LinsertWhere where, final Buffer pivot,
                                       final Buffer value) throws Exception {
        return singleToDeferredValue(commander.linsert(key, where, pivot, value));
    }

    @Override
    public DeferredValue<Long> llen(@RedisProtocolSupport.Key final Buffer key) throws Exception {
        return singleToDeferredValue(commander.llen(key));
    }

    @Override
    public DeferredValue<Buffer> lpop(@RedisProtocolSupport.Key final Buffer key) throws Exception {
        return singleToDeferredValue(commander.lpop(key));
    }

    @Override
    public DeferredValue<Long> lpush(@RedisProtocolSupport.Key final Buffer key, final Buffer value) throws Exception {
        return singleToDeferredValue(commander.lpush(key, value));
    }

    @Override
    public DeferredValue<Long> lpush(@RedisProtocolSupport.Key final Buffer key, final Buffer value1,
                                     final Buffer value2) throws Exception {
        return singleToDeferredValue(commander.lpush(key, value1, value2));
    }

    @Override
    public DeferredValue<Long> lpush(@RedisProtocolSupport.Key final Buffer key, final Buffer value1,
                                     final Buffer value2, final Buffer value3) throws Exception {
        return singleToDeferredValue(commander.lpush(key, value1, value2, value3));
    }

    @Override
    public DeferredValue<Long> lpush(@RedisProtocolSupport.Key final Buffer key,
                                     final Collection<Buffer> values) throws Exception {
        return singleToDeferredValue(commander.lpush(key, values));
    }

    @Override
    public DeferredValue<Long> lpushx(@RedisProtocolSupport.Key final Buffer key, final Buffer value) throws Exception {
        return singleToDeferredValue(commander.lpushx(key, value));
    }

    @Override
    public <T> DeferredValue<List<T>> lrange(@RedisProtocolSupport.Key final Buffer key, final long start,
                                             final long stop) throws Exception {
        return singleToDeferredValue(commander.lrange(key, start, stop));
    }

    @Override
    public DeferredValue<Long> lrem(@RedisProtocolSupport.Key final Buffer key, final long count,
                                    final Buffer value) throws Exception {
        return singleToDeferredValue(commander.lrem(key, count, value));
    }

    @Override
    public DeferredValue<String> lset(@RedisProtocolSupport.Key final Buffer key, final long index,
                                      final Buffer value) throws Exception {
        return singleToDeferredValue(commander.lset(key, index, value));
    }

    @Override
    public DeferredValue<String> ltrim(@RedisProtocolSupport.Key final Buffer key, final long start,
                                       final long stop) throws Exception {
        return singleToDeferredValue(commander.ltrim(key, start, stop));
    }

    @Override
    public DeferredValue<Buffer> memoryDoctor() throws Exception {
        return singleToDeferredValue(commander.memoryDoctor());
    }

    @Override
    public <T> DeferredValue<List<T>> memoryHelp() throws Exception {
        return singleToDeferredValue(commander.memoryHelp());
    }

    @Override
    public DeferredValue<Buffer> memoryMallocStats() throws Exception {
        return singleToDeferredValue(commander.memoryMallocStats());
    }

    @Override
    public DeferredValue<String> memoryPurge() throws Exception {
        return singleToDeferredValue(commander.memoryPurge());
    }

    @Override
    public <T> DeferredValue<List<T>> memoryStats() throws Exception {
        return singleToDeferredValue(commander.memoryStats());
    }

    @Override
    public DeferredValue<Long> memoryUsage(@RedisProtocolSupport.Key final Buffer key) throws Exception {
        return singleToDeferredValue(commander.memoryUsage(key));
    }

    @Override
    public DeferredValue<Long> memoryUsage(@RedisProtocolSupport.Key final Buffer key,
                                           @Nullable final Long samplesCount) throws Exception {
        return singleToDeferredValue(commander.memoryUsage(key, samplesCount));
    }

    @Override
    public <T> DeferredValue<List<T>> mget(@RedisProtocolSupport.Key final Buffer key) throws Exception {
        return singleToDeferredValue(commander.mget(key));
    }

    @Override
    public <T> DeferredValue<List<T>> mget(@RedisProtocolSupport.Key final Buffer key1,
                                           @RedisProtocolSupport.Key final Buffer key2) throws Exception {
        return singleToDeferredValue(commander.mget(key1, key2));
    }

    @Override
    public <T> DeferredValue<List<T>> mget(@RedisProtocolSupport.Key final Buffer key1,
                                           @RedisProtocolSupport.Key final Buffer key2,
                                           @RedisProtocolSupport.Key final Buffer key3) throws Exception {
        return singleToDeferredValue(commander.mget(key1, key2, key3));
    }

    @Override
    public <T> DeferredValue<List<T>> mget(@RedisProtocolSupport.Key final Collection<Buffer> keys) throws Exception {
        return singleToDeferredValue(commander.mget(keys));
    }

    @Override
    public DeferredValue<Long> move(@RedisProtocolSupport.Key final Buffer key, final long db) throws Exception {
        return singleToDeferredValue(commander.move(key, db));
    }

    @Override
    public DeferredValue<String> mset(@RedisProtocolSupport.Key final Buffer key, final Buffer value) throws Exception {
        return singleToDeferredValue(commander.mset(key, value));
    }

    @Override
    public DeferredValue<String> mset(@RedisProtocolSupport.Key final Buffer key1, final Buffer value1,
                                      @RedisProtocolSupport.Key final Buffer key2,
                                      final Buffer value2) throws Exception {
        return singleToDeferredValue(commander.mset(key1, value1, key2, value2));
    }

    @Override
    public DeferredValue<String> mset(@RedisProtocolSupport.Key final Buffer key1, final Buffer value1,
                                      @RedisProtocolSupport.Key final Buffer key2, final Buffer value2,
                                      @RedisProtocolSupport.Key final Buffer key3,
                                      final Buffer value3) throws Exception {
        return singleToDeferredValue(commander.mset(key1, value1, key2, value2, key3, value3));
    }

    @Override
    public DeferredValue<String> mset(final Collection<RedisProtocolSupport.BufferKeyValue> keyValues) throws Exception {
        return singleToDeferredValue(commander.mset(keyValues));
    }

    @Override
    public DeferredValue<Long> msetnx(@RedisProtocolSupport.Key final Buffer key, final Buffer value) throws Exception {
        return singleToDeferredValue(commander.msetnx(key, value));
    }

    @Override
    public DeferredValue<Long> msetnx(@RedisProtocolSupport.Key final Buffer key1, final Buffer value1,
                                      @RedisProtocolSupport.Key final Buffer key2,
                                      final Buffer value2) throws Exception {
        return singleToDeferredValue(commander.msetnx(key1, value1, key2, value2));
    }

    @Override
    public DeferredValue<Long> msetnx(@RedisProtocolSupport.Key final Buffer key1, final Buffer value1,
                                      @RedisProtocolSupport.Key final Buffer key2, final Buffer value2,
                                      @RedisProtocolSupport.Key final Buffer key3,
                                      final Buffer value3) throws Exception {
        return singleToDeferredValue(commander.msetnx(key1, value1, key2, value2, key3, value3));
    }

    @Override
    public DeferredValue<Long> msetnx(final Collection<RedisProtocolSupport.BufferKeyValue> keyValues) throws Exception {
        return singleToDeferredValue(commander.msetnx(keyValues));
    }

    @Override
    public DeferredValue<Buffer> objectEncoding(@RedisProtocolSupport.Key final Buffer key) throws Exception {
        return singleToDeferredValue(commander.objectEncoding(key));
    }

    @Override
    public DeferredValue<Long> objectFreq(@RedisProtocolSupport.Key final Buffer key) throws Exception {
        return singleToDeferredValue(commander.objectFreq(key));
    }

    @Override
    public DeferredValue<List<String>> objectHelp() throws Exception {
        return singleToDeferredValue(commander.objectHelp());
    }

    @Override
    public DeferredValue<Long> objectIdletime(@RedisProtocolSupport.Key final Buffer key) throws Exception {
        return singleToDeferredValue(commander.objectIdletime(key));
    }

    @Override
    public DeferredValue<Long> objectRefcount(@RedisProtocolSupport.Key final Buffer key) throws Exception {
        return singleToDeferredValue(commander.objectRefcount(key));
    }

    @Override
    public DeferredValue<Long> persist(@RedisProtocolSupport.Key final Buffer key) throws Exception {
        return singleToDeferredValue(commander.persist(key));
    }

    @Override
    public DeferredValue<Long> pexpire(@RedisProtocolSupport.Key final Buffer key,
                                       final long milliseconds) throws Exception {
        return singleToDeferredValue(commander.pexpire(key, milliseconds));
    }

    @Override
    public DeferredValue<Long> pexpireat(@RedisProtocolSupport.Key final Buffer key,
                                         final long millisecondsTimestamp) throws Exception {
        return singleToDeferredValue(commander.pexpireat(key, millisecondsTimestamp));
    }

    @Override
    public DeferredValue<Long> pfadd(@RedisProtocolSupport.Key final Buffer key,
                                     final Buffer element) throws Exception {
        return singleToDeferredValue(commander.pfadd(key, element));
    }

    @Override
    public DeferredValue<Long> pfadd(@RedisProtocolSupport.Key final Buffer key, final Buffer element1,
                                     final Buffer element2) throws Exception {
        return singleToDeferredValue(commander.pfadd(key, element1, element2));
    }

    @Override
    public DeferredValue<Long> pfadd(@RedisProtocolSupport.Key final Buffer key, final Buffer element1,
                                     final Buffer element2, final Buffer element3) throws Exception {
        return singleToDeferredValue(commander.pfadd(key, element1, element2, element3));
    }

    @Override
    public DeferredValue<Long> pfadd(@RedisProtocolSupport.Key final Buffer key,
                                     final Collection<Buffer> elements) throws Exception {
        return singleToDeferredValue(commander.pfadd(key, elements));
    }

    @Override
    public DeferredValue<Long> pfcount(@RedisProtocolSupport.Key final Buffer key) throws Exception {
        return singleToDeferredValue(commander.pfcount(key));
    }

    @Override
    public DeferredValue<Long> pfcount(@RedisProtocolSupport.Key final Buffer key1,
                                       @RedisProtocolSupport.Key final Buffer key2) throws Exception {
        return singleToDeferredValue(commander.pfcount(key1, key2));
    }

    @Override
    public DeferredValue<Long> pfcount(@RedisProtocolSupport.Key final Buffer key1,
                                       @RedisProtocolSupport.Key final Buffer key2,
                                       @RedisProtocolSupport.Key final Buffer key3) throws Exception {
        return singleToDeferredValue(commander.pfcount(key1, key2, key3));
    }

    @Override
    public DeferredValue<Long> pfcount(@RedisProtocolSupport.Key final Collection<Buffer> keys) throws Exception {
        return singleToDeferredValue(commander.pfcount(keys));
    }

    @Override
    public DeferredValue<String> pfmerge(@RedisProtocolSupport.Key final Buffer destkey,
                                         @RedisProtocolSupport.Key final Buffer sourcekey) throws Exception {
        return singleToDeferredValue(commander.pfmerge(destkey, sourcekey));
    }

    @Override
    public DeferredValue<String> pfmerge(@RedisProtocolSupport.Key final Buffer destkey,
                                         @RedisProtocolSupport.Key final Buffer sourcekey1,
                                         @RedisProtocolSupport.Key final Buffer sourcekey2) throws Exception {
        return singleToDeferredValue(commander.pfmerge(destkey, sourcekey1, sourcekey2));
    }

    @Override
    public DeferredValue<String> pfmerge(@RedisProtocolSupport.Key final Buffer destkey,
                                         @RedisProtocolSupport.Key final Buffer sourcekey1,
                                         @RedisProtocolSupport.Key final Buffer sourcekey2,
                                         @RedisProtocolSupport.Key final Buffer sourcekey3) throws Exception {
        return singleToDeferredValue(commander.pfmerge(destkey, sourcekey1, sourcekey2, sourcekey3));
    }

    @Override
    public DeferredValue<String> pfmerge(@RedisProtocolSupport.Key final Buffer destkey,
                                         @RedisProtocolSupport.Key final Collection<Buffer> sourcekeys) throws Exception {
        return singleToDeferredValue(commander.pfmerge(destkey, sourcekeys));
    }

    @Override
    public DeferredValue<String> ping() throws Exception {
        return singleToDeferredValue(commander.ping());
    }

    @Override
    public DeferredValue<Buffer> ping(final Buffer message) throws Exception {
        return singleToDeferredValue(commander.ping(message));
    }

    @Override
    public DeferredValue<String> psetex(@RedisProtocolSupport.Key final Buffer key, final long milliseconds,
                                        final Buffer value) throws Exception {
        return singleToDeferredValue(commander.psetex(key, milliseconds, value));
    }

    @Override
    public DeferredValue<Long> pttl(@RedisProtocolSupport.Key final Buffer key) throws Exception {
        return singleToDeferredValue(commander.pttl(key));
    }

    @Override
    public DeferredValue<Long> publish(final Buffer channel, final Buffer message) throws Exception {
        return singleToDeferredValue(commander.publish(channel, message));
    }

    @Override
    public DeferredValue<List<String>> pubsubChannels() throws Exception {
        return singleToDeferredValue(commander.pubsubChannels());
    }

    @Override
    public DeferredValue<List<String>> pubsubChannels(@Nullable final Buffer pattern) throws Exception {
        return singleToDeferredValue(commander.pubsubChannels(pattern));
    }

    @Override
    public DeferredValue<List<String>> pubsubChannels(@Nullable final Buffer pattern1,
                                                      @Nullable final Buffer pattern2) throws Exception {
        return singleToDeferredValue(commander.pubsubChannels(pattern1, pattern2));
    }

    @Override
    public DeferredValue<List<String>> pubsubChannels(@Nullable final Buffer pattern1, @Nullable final Buffer pattern2,
                                                      @Nullable final Buffer pattern3) throws Exception {
        return singleToDeferredValue(commander.pubsubChannels(pattern1, pattern2, pattern3));
    }

    @Override
    public DeferredValue<List<String>> pubsubChannels(final Collection<Buffer> patterns) throws Exception {
        return singleToDeferredValue(commander.pubsubChannels(patterns));
    }

    @Override
    public <T> DeferredValue<List<T>> pubsubNumsub() throws Exception {
        return singleToDeferredValue(commander.pubsubNumsub());
    }

    @Override
    public <T> DeferredValue<List<T>> pubsubNumsub(@Nullable final Buffer channel) throws Exception {
        return singleToDeferredValue(commander.pubsubNumsub(channel));
    }

    @Override
    public <T> DeferredValue<List<T>> pubsubNumsub(@Nullable final Buffer channel1,
                                                   @Nullable final Buffer channel2) throws Exception {
        return singleToDeferredValue(commander.pubsubNumsub(channel1, channel2));
    }

    @Override
    public <T> DeferredValue<List<T>> pubsubNumsub(@Nullable final Buffer channel1, @Nullable final Buffer channel2,
                                                   @Nullable final Buffer channel3) throws Exception {
        return singleToDeferredValue(commander.pubsubNumsub(channel1, channel2, channel3));
    }

    @Override
    public <T> DeferredValue<List<T>> pubsubNumsub(final Collection<Buffer> channels) throws Exception {
        return singleToDeferredValue(commander.pubsubNumsub(channels));
    }

    @Override
    public DeferredValue<Long> pubsubNumpat() throws Exception {
        return singleToDeferredValue(commander.pubsubNumpat());
    }

    @Override
    public DeferredValue<Buffer> randomkey() throws Exception {
        return singleToDeferredValue(commander.randomkey());
    }

    @Override
    public DeferredValue<String> readonly() throws Exception {
        return singleToDeferredValue(commander.readonly());
    }

    @Override
    public DeferredValue<String> readwrite() throws Exception {
        return singleToDeferredValue(commander.readwrite());
    }

    @Override
    public DeferredValue<String> rename(@RedisProtocolSupport.Key final Buffer key,
                                        @RedisProtocolSupport.Key final Buffer newkey) throws Exception {
        return singleToDeferredValue(commander.rename(key, newkey));
    }

    @Override
    public DeferredValue<Long> renamenx(@RedisProtocolSupport.Key final Buffer key,
                                        @RedisProtocolSupport.Key final Buffer newkey) throws Exception {
        return singleToDeferredValue(commander.renamenx(key, newkey));
    }

    @Override
    public DeferredValue<String> restore(@RedisProtocolSupport.Key final Buffer key, final long ttl,
                                         final Buffer serializedValue) throws Exception {
        return singleToDeferredValue(commander.restore(key, ttl, serializedValue));
    }

    @Override
    public DeferredValue<String> restore(@RedisProtocolSupport.Key final Buffer key, final long ttl,
                                         final Buffer serializedValue,
                                         @Nullable final RedisProtocolSupport.RestoreReplace replace) throws Exception {
        return singleToDeferredValue(commander.restore(key, ttl, serializedValue, replace));
    }

    @Override
    public <T> DeferredValue<List<T>> role() throws Exception {
        return singleToDeferredValue(commander.role());
    }

    @Override
    public DeferredValue<Buffer> rpop(@RedisProtocolSupport.Key final Buffer key) throws Exception {
        return singleToDeferredValue(commander.rpop(key));
    }

    @Override
    public DeferredValue<Buffer> rpoplpush(@RedisProtocolSupport.Key final Buffer source,
                                           @RedisProtocolSupport.Key final Buffer destination) throws Exception {
        return singleToDeferredValue(commander.rpoplpush(source, destination));
    }

    @Override
    public DeferredValue<Long> rpush(@RedisProtocolSupport.Key final Buffer key, final Buffer value) throws Exception {
        return singleToDeferredValue(commander.rpush(key, value));
    }

    @Override
    public DeferredValue<Long> rpush(@RedisProtocolSupport.Key final Buffer key, final Buffer value1,
                                     final Buffer value2) throws Exception {
        return singleToDeferredValue(commander.rpush(key, value1, value2));
    }

    @Override
    public DeferredValue<Long> rpush(@RedisProtocolSupport.Key final Buffer key, final Buffer value1,
                                     final Buffer value2, final Buffer value3) throws Exception {
        return singleToDeferredValue(commander.rpush(key, value1, value2, value3));
    }

    @Override
    public DeferredValue<Long> rpush(@RedisProtocolSupport.Key final Buffer key,
                                     final Collection<Buffer> values) throws Exception {
        return singleToDeferredValue(commander.rpush(key, values));
    }

    @Override
    public DeferredValue<Long> rpushx(@RedisProtocolSupport.Key final Buffer key, final Buffer value) throws Exception {
        return singleToDeferredValue(commander.rpushx(key, value));
    }

    @Override
    public DeferredValue<Long> sadd(@RedisProtocolSupport.Key final Buffer key, final Buffer member) throws Exception {
        return singleToDeferredValue(commander.sadd(key, member));
    }

    @Override
    public DeferredValue<Long> sadd(@RedisProtocolSupport.Key final Buffer key, final Buffer member1,
                                    final Buffer member2) throws Exception {
        return singleToDeferredValue(commander.sadd(key, member1, member2));
    }

    @Override
    public DeferredValue<Long> sadd(@RedisProtocolSupport.Key final Buffer key, final Buffer member1,
                                    final Buffer member2, final Buffer member3) throws Exception {
        return singleToDeferredValue(commander.sadd(key, member1, member2, member3));
    }

    @Override
    public DeferredValue<Long> sadd(@RedisProtocolSupport.Key final Buffer key,
                                    final Collection<Buffer> members) throws Exception {
        return singleToDeferredValue(commander.sadd(key, members));
    }

    @Override
    public DeferredValue<String> save() throws Exception {
        return singleToDeferredValue(commander.save());
    }

    @Override
    public <T> DeferredValue<List<T>> scan(final long cursor) throws Exception {
        return singleToDeferredValue(commander.scan(cursor));
    }

    @Override
    public <T> DeferredValue<List<T>> scan(final long cursor, @Nullable final Buffer matchPattern,
                                           @Nullable final Long count) throws Exception {
        return singleToDeferredValue(commander.scan(cursor, matchPattern, count));
    }

    @Override
    public DeferredValue<Long> scard(@RedisProtocolSupport.Key final Buffer key) throws Exception {
        return singleToDeferredValue(commander.scard(key));
    }

    @Override
    public DeferredValue<String> scriptDebug(final RedisProtocolSupport.ScriptDebugMode mode) throws Exception {
        return singleToDeferredValue(commander.scriptDebug(mode));
    }

    @Override
    public <T> DeferredValue<List<T>> scriptExists(final Buffer sha1) throws Exception {
        return singleToDeferredValue(commander.scriptExists(sha1));
    }

    @Override
    public <T> DeferredValue<List<T>> scriptExists(final Buffer sha11, final Buffer sha12) throws Exception {
        return singleToDeferredValue(commander.scriptExists(sha11, sha12));
    }

    @Override
    public <T> DeferredValue<List<T>> scriptExists(final Buffer sha11, final Buffer sha12,
                                                   final Buffer sha13) throws Exception {
        return singleToDeferredValue(commander.scriptExists(sha11, sha12, sha13));
    }

    @Override
    public <T> DeferredValue<List<T>> scriptExists(final Collection<Buffer> sha1s) throws Exception {
        return singleToDeferredValue(commander.scriptExists(sha1s));
    }

    @Override
    public DeferredValue<String> scriptFlush() throws Exception {
        return singleToDeferredValue(commander.scriptFlush());
    }

    @Override
    public DeferredValue<String> scriptKill() throws Exception {
        return singleToDeferredValue(commander.scriptKill());
    }

    @Override
    public DeferredValue<Buffer> scriptLoad(final Buffer script) throws Exception {
        return singleToDeferredValue(commander.scriptLoad(script));
    }

    @Override
    public <T> DeferredValue<List<T>> sdiff(@RedisProtocolSupport.Key final Buffer firstkey) throws Exception {
        return singleToDeferredValue(commander.sdiff(firstkey));
    }

    @Override
    public <T> DeferredValue<List<T>> sdiff(@RedisProtocolSupport.Key final Buffer firstkey,
                                            @Nullable @RedisProtocolSupport.Key final Buffer otherkey) throws Exception {
        return singleToDeferredValue(commander.sdiff(firstkey, otherkey));
    }

    @Override
    public <T> DeferredValue<List<T>> sdiff(@RedisProtocolSupport.Key final Buffer firstkey,
                                            @Nullable @RedisProtocolSupport.Key final Buffer otherkey1,
                                            @Nullable @RedisProtocolSupport.Key final Buffer otherkey2) throws Exception {
        return singleToDeferredValue(commander.sdiff(firstkey, otherkey1, otherkey2));
    }

    @Override
    public <T> DeferredValue<List<T>> sdiff(@RedisProtocolSupport.Key final Buffer firstkey,
                                            @Nullable @RedisProtocolSupport.Key final Buffer otherkey1,
                                            @Nullable @RedisProtocolSupport.Key final Buffer otherkey2,
                                            @Nullable @RedisProtocolSupport.Key final Buffer otherkey3) throws Exception {
        return singleToDeferredValue(commander.sdiff(firstkey, otherkey1, otherkey2, otherkey3));
    }

    @Override
    public <T> DeferredValue<List<T>> sdiff(@RedisProtocolSupport.Key final Buffer firstkey,
                                            @RedisProtocolSupport.Key final Collection<Buffer> otherkeys) throws Exception {
        return singleToDeferredValue(commander.sdiff(firstkey, otherkeys));
    }

    @Override
    public DeferredValue<Long> sdiffstore(@RedisProtocolSupport.Key final Buffer destination,
                                          @RedisProtocolSupport.Key final Buffer firstkey) throws Exception {
        return singleToDeferredValue(commander.sdiffstore(destination, firstkey));
    }

    @Override
    public DeferredValue<Long> sdiffstore(@RedisProtocolSupport.Key final Buffer destination,
                                          @RedisProtocolSupport.Key final Buffer firstkey,
                                          @Nullable @RedisProtocolSupport.Key final Buffer otherkey) throws Exception {
        return singleToDeferredValue(commander.sdiffstore(destination, firstkey, otherkey));
    }

    @Override
    public DeferredValue<Long> sdiffstore(@RedisProtocolSupport.Key final Buffer destination,
                                          @RedisProtocolSupport.Key final Buffer firstkey,
                                          @Nullable @RedisProtocolSupport.Key final Buffer otherkey1,
                                          @Nullable @RedisProtocolSupport.Key final Buffer otherkey2) throws Exception {
        return singleToDeferredValue(commander.sdiffstore(destination, firstkey, otherkey1, otherkey2));
    }

    @Override
    public DeferredValue<Long> sdiffstore(@RedisProtocolSupport.Key final Buffer destination,
                                          @RedisProtocolSupport.Key final Buffer firstkey,
                                          @Nullable @RedisProtocolSupport.Key final Buffer otherkey1,
                                          @Nullable @RedisProtocolSupport.Key final Buffer otherkey2,
                                          @Nullable @RedisProtocolSupport.Key final Buffer otherkey3) throws Exception {
        return singleToDeferredValue(commander.sdiffstore(destination, firstkey, otherkey1, otherkey2, otherkey3));
    }

    @Override
    public DeferredValue<Long> sdiffstore(@RedisProtocolSupport.Key final Buffer destination,
                                          @RedisProtocolSupport.Key final Buffer firstkey,
                                          @RedisProtocolSupport.Key final Collection<Buffer> otherkeys) throws Exception {
        return singleToDeferredValue(commander.sdiffstore(destination, firstkey, otherkeys));
    }

    @Override
    public DeferredValue<String> select(final long index) throws Exception {
        return singleToDeferredValue(commander.select(index));
    }

    @Override
    public DeferredValue<String> set(@RedisProtocolSupport.Key final Buffer key, final Buffer value) throws Exception {
        return singleToDeferredValue(commander.set(key, value));
    }

    @Override
    public DeferredValue<String> set(@RedisProtocolSupport.Key final Buffer key, final Buffer value,
                                     @Nullable final RedisProtocolSupport.ExpireDuration expireDuration,
                                     @Nullable final RedisProtocolSupport.SetCondition condition) throws Exception {
        return singleToDeferredValue(commander.set(key, value, expireDuration, condition));
    }

    @Override
    public DeferredValue<Long> setbit(@RedisProtocolSupport.Key final Buffer key, final long offset,
                                      final Buffer value) throws Exception {
        return singleToDeferredValue(commander.setbit(key, offset, value));
    }

    @Override
    public DeferredValue<String> setex(@RedisProtocolSupport.Key final Buffer key, final long seconds,
                                       final Buffer value) throws Exception {
        return singleToDeferredValue(commander.setex(key, seconds, value));
    }

    @Override
    public DeferredValue<Long> setnx(@RedisProtocolSupport.Key final Buffer key, final Buffer value) throws Exception {
        return singleToDeferredValue(commander.setnx(key, value));
    }

    @Override
    public DeferredValue<Long> setrange(@RedisProtocolSupport.Key final Buffer key, final long offset,
                                        final Buffer value) throws Exception {
        return singleToDeferredValue(commander.setrange(key, offset, value));
    }

    @Override
    public DeferredValue<String> shutdown() throws Exception {
        return singleToDeferredValue(commander.shutdown());
    }

    @Override
    public DeferredValue<String> shutdown(@Nullable final RedisProtocolSupport.ShutdownSaveMode saveMode) throws Exception {
        return singleToDeferredValue(commander.shutdown(saveMode));
    }

    @Override
    public <T> DeferredValue<List<T>> sinter(@RedisProtocolSupport.Key final Buffer key) throws Exception {
        return singleToDeferredValue(commander.sinter(key));
    }

    @Override
    public <T> DeferredValue<List<T>> sinter(@RedisProtocolSupport.Key final Buffer key1,
                                             @RedisProtocolSupport.Key final Buffer key2) throws Exception {
        return singleToDeferredValue(commander.sinter(key1, key2));
    }

    @Override
    public <T> DeferredValue<List<T>> sinter(@RedisProtocolSupport.Key final Buffer key1,
                                             @RedisProtocolSupport.Key final Buffer key2,
                                             @RedisProtocolSupport.Key final Buffer key3) throws Exception {
        return singleToDeferredValue(commander.sinter(key1, key2, key3));
    }

    @Override
    public <T> DeferredValue<List<T>> sinter(@RedisProtocolSupport.Key final Collection<Buffer> keys) throws Exception {
        return singleToDeferredValue(commander.sinter(keys));
    }

    @Override
    public DeferredValue<Long> sinterstore(@RedisProtocolSupport.Key final Buffer destination,
                                           @RedisProtocolSupport.Key final Buffer key) throws Exception {
        return singleToDeferredValue(commander.sinterstore(destination, key));
    }

    @Override
    public DeferredValue<Long> sinterstore(@RedisProtocolSupport.Key final Buffer destination,
                                           @RedisProtocolSupport.Key final Buffer key1,
                                           @RedisProtocolSupport.Key final Buffer key2) throws Exception {
        return singleToDeferredValue(commander.sinterstore(destination, key1, key2));
    }

    @Override
    public DeferredValue<Long> sinterstore(@RedisProtocolSupport.Key final Buffer destination,
                                           @RedisProtocolSupport.Key final Buffer key1,
                                           @RedisProtocolSupport.Key final Buffer key2,
                                           @RedisProtocolSupport.Key final Buffer key3) throws Exception {
        return singleToDeferredValue(commander.sinterstore(destination, key1, key2, key3));
    }

    @Override
    public DeferredValue<Long> sinterstore(@RedisProtocolSupport.Key final Buffer destination,
                                           @RedisProtocolSupport.Key final Collection<Buffer> keys) throws Exception {
        return singleToDeferredValue(commander.sinterstore(destination, keys));
    }

    @Override
    public DeferredValue<Long> sismember(@RedisProtocolSupport.Key final Buffer key,
                                         final Buffer member) throws Exception {
        return singleToDeferredValue(commander.sismember(key, member));
    }

    @Override
    public DeferredValue<String> slaveof(final Buffer host, final Buffer port) throws Exception {
        return singleToDeferredValue(commander.slaveof(host, port));
    }

    @Override
    public <T> DeferredValue<List<T>> slowlog(final Buffer subcommand) throws Exception {
        return singleToDeferredValue(commander.slowlog(subcommand));
    }

    @Override
    public <T> DeferredValue<List<T>> slowlog(final Buffer subcommand,
                                              @Nullable final Buffer argument) throws Exception {
        return singleToDeferredValue(commander.slowlog(subcommand, argument));
    }

    @Override
    public <T> DeferredValue<List<T>> smembers(@RedisProtocolSupport.Key final Buffer key) throws Exception {
        return singleToDeferredValue(commander.smembers(key));
    }

    @Override
    public DeferredValue<Long> smove(@RedisProtocolSupport.Key final Buffer source,
                                     @RedisProtocolSupport.Key final Buffer destination,
                                     final Buffer member) throws Exception {
        return singleToDeferredValue(commander.smove(source, destination, member));
    }

    @Override
    public <T> DeferredValue<List<T>> sort(@RedisProtocolSupport.Key final Buffer key) throws Exception {
        return singleToDeferredValue(commander.sort(key));
    }

    @Override
    public <T> DeferredValue<List<T>> sort(@RedisProtocolSupport.Key final Buffer key, @Nullable final Buffer byPattern,
                                           @Nullable final RedisProtocolSupport.OffsetCount offsetCount,
                                           final Collection<Buffer> getPatterns,
                                           @Nullable final RedisProtocolSupport.SortOrder order,
                                           @Nullable final RedisProtocolSupport.SortSorting sorting) throws Exception {
        return singleToDeferredValue(commander.sort(key, byPattern, offsetCount, getPatterns, order, sorting));
    }

    @Override
    public DeferredValue<Long> sort(@RedisProtocolSupport.Key final Buffer key,
                                    @RedisProtocolSupport.Key final Buffer storeDestination) throws Exception {
        return singleToDeferredValue(commander.sort(key, storeDestination));
    }

    @Override
    public DeferredValue<Long> sort(@RedisProtocolSupport.Key final Buffer key,
                                    @RedisProtocolSupport.Key final Buffer storeDestination,
                                    @Nullable final Buffer byPattern,
                                    @Nullable final RedisProtocolSupport.OffsetCount offsetCount,
                                    final Collection<Buffer> getPatterns,
                                    @Nullable final RedisProtocolSupport.SortOrder order,
                                    @Nullable final RedisProtocolSupport.SortSorting sorting) throws Exception {
        return singleToDeferredValue(
                    commander.sort(key, storeDestination, byPattern, offsetCount, getPatterns, order, sorting));
    }

    @Override
    public DeferredValue<Buffer> spop(@RedisProtocolSupport.Key final Buffer key) throws Exception {
        return singleToDeferredValue(commander.spop(key));
    }

    @Override
    public DeferredValue<Buffer> spop(@RedisProtocolSupport.Key final Buffer key,
                                      @Nullable final Long count) throws Exception {
        return singleToDeferredValue(commander.spop(key, count));
    }

    @Override
    public DeferredValue<Buffer> srandmember(@RedisProtocolSupport.Key final Buffer key) throws Exception {
        return singleToDeferredValue(commander.srandmember(key));
    }

    @Override
    public DeferredValue<List<String>> srandmember(@RedisProtocolSupport.Key final Buffer key,
                                                   final long count) throws Exception {
        return singleToDeferredValue(commander.srandmember(key, count));
    }

    @Override
    public DeferredValue<Long> srem(@RedisProtocolSupport.Key final Buffer key, final Buffer member) throws Exception {
        return singleToDeferredValue(commander.srem(key, member));
    }

    @Override
    public DeferredValue<Long> srem(@RedisProtocolSupport.Key final Buffer key, final Buffer member1,
                                    final Buffer member2) throws Exception {
        return singleToDeferredValue(commander.srem(key, member1, member2));
    }

    @Override
    public DeferredValue<Long> srem(@RedisProtocolSupport.Key final Buffer key, final Buffer member1,
                                    final Buffer member2, final Buffer member3) throws Exception {
        return singleToDeferredValue(commander.srem(key, member1, member2, member3));
    }

    @Override
    public DeferredValue<Long> srem(@RedisProtocolSupport.Key final Buffer key,
                                    final Collection<Buffer> members) throws Exception {
        return singleToDeferredValue(commander.srem(key, members));
    }

    @Override
    public <T> DeferredValue<List<T>> sscan(@RedisProtocolSupport.Key final Buffer key,
                                            final long cursor) throws Exception {
        return singleToDeferredValue(commander.sscan(key, cursor));
    }

    @Override
    public <T> DeferredValue<List<T>> sscan(@RedisProtocolSupport.Key final Buffer key, final long cursor,
                                            @Nullable final Buffer matchPattern,
                                            @Nullable final Long count) throws Exception {
        return singleToDeferredValue(commander.sscan(key, cursor, matchPattern, count));
    }

    @Override
    public DeferredValue<Long> strlen(@RedisProtocolSupport.Key final Buffer key) throws Exception {
        return singleToDeferredValue(commander.strlen(key));
    }

    @Override
    public <T> DeferredValue<List<T>> sunion(@RedisProtocolSupport.Key final Buffer key) throws Exception {
        return singleToDeferredValue(commander.sunion(key));
    }

    @Override
    public <T> DeferredValue<List<T>> sunion(@RedisProtocolSupport.Key final Buffer key1,
                                             @RedisProtocolSupport.Key final Buffer key2) throws Exception {
        return singleToDeferredValue(commander.sunion(key1, key2));
    }

    @Override
    public <T> DeferredValue<List<T>> sunion(@RedisProtocolSupport.Key final Buffer key1,
                                             @RedisProtocolSupport.Key final Buffer key2,
                                             @RedisProtocolSupport.Key final Buffer key3) throws Exception {
        return singleToDeferredValue(commander.sunion(key1, key2, key3));
    }

    @Override
    public <T> DeferredValue<List<T>> sunion(@RedisProtocolSupport.Key final Collection<Buffer> keys) throws Exception {
        return singleToDeferredValue(commander.sunion(keys));
    }

    @Override
    public DeferredValue<Long> sunionstore(@RedisProtocolSupport.Key final Buffer destination,
                                           @RedisProtocolSupport.Key final Buffer key) throws Exception {
        return singleToDeferredValue(commander.sunionstore(destination, key));
    }

    @Override
    public DeferredValue<Long> sunionstore(@RedisProtocolSupport.Key final Buffer destination,
                                           @RedisProtocolSupport.Key final Buffer key1,
                                           @RedisProtocolSupport.Key final Buffer key2) throws Exception {
        return singleToDeferredValue(commander.sunionstore(destination, key1, key2));
    }

    @Override
    public DeferredValue<Long> sunionstore(@RedisProtocolSupport.Key final Buffer destination,
                                           @RedisProtocolSupport.Key final Buffer key1,
                                           @RedisProtocolSupport.Key final Buffer key2,
                                           @RedisProtocolSupport.Key final Buffer key3) throws Exception {
        return singleToDeferredValue(commander.sunionstore(destination, key1, key2, key3));
    }

    @Override
    public DeferredValue<Long> sunionstore(@RedisProtocolSupport.Key final Buffer destination,
                                           @RedisProtocolSupport.Key final Collection<Buffer> keys) throws Exception {
        return singleToDeferredValue(commander.sunionstore(destination, keys));
    }

    @Override
    public DeferredValue<String> swapdb(final long index, final long index1) throws Exception {
        return singleToDeferredValue(commander.swapdb(index, index1));
    }

    @Override
    public <T> DeferredValue<List<T>> time() throws Exception {
        return singleToDeferredValue(commander.time());
    }

    @Override
    public DeferredValue<Long> touch(@RedisProtocolSupport.Key final Buffer key) throws Exception {
        return singleToDeferredValue(commander.touch(key));
    }

    @Override
    public DeferredValue<Long> touch(@RedisProtocolSupport.Key final Buffer key1,
                                     @RedisProtocolSupport.Key final Buffer key2) throws Exception {
        return singleToDeferredValue(commander.touch(key1, key2));
    }

    @Override
    public DeferredValue<Long> touch(@RedisProtocolSupport.Key final Buffer key1,
                                     @RedisProtocolSupport.Key final Buffer key2,
                                     @RedisProtocolSupport.Key final Buffer key3) throws Exception {
        return singleToDeferredValue(commander.touch(key1, key2, key3));
    }

    @Override
    public DeferredValue<Long> touch(@RedisProtocolSupport.Key final Collection<Buffer> keys) throws Exception {
        return singleToDeferredValue(commander.touch(keys));
    }

    @Override
    public DeferredValue<Long> ttl(@RedisProtocolSupport.Key final Buffer key) throws Exception {
        return singleToDeferredValue(commander.ttl(key));
    }

    @Override
    public DeferredValue<String> type(@RedisProtocolSupport.Key final Buffer key) throws Exception {
        return singleToDeferredValue(commander.type(key));
    }

    @Override
    public DeferredValue<Long> unlink(@RedisProtocolSupport.Key final Buffer key) throws Exception {
        return singleToDeferredValue(commander.unlink(key));
    }

    @Override
    public DeferredValue<Long> unlink(@RedisProtocolSupport.Key final Buffer key1,
                                      @RedisProtocolSupport.Key final Buffer key2) throws Exception {
        return singleToDeferredValue(commander.unlink(key1, key2));
    }

    @Override
    public DeferredValue<Long> unlink(@RedisProtocolSupport.Key final Buffer key1,
                                      @RedisProtocolSupport.Key final Buffer key2,
                                      @RedisProtocolSupport.Key final Buffer key3) throws Exception {
        return singleToDeferredValue(commander.unlink(key1, key2, key3));
    }

    @Override
    public DeferredValue<Long> unlink(@RedisProtocolSupport.Key final Collection<Buffer> keys) throws Exception {
        return singleToDeferredValue(commander.unlink(keys));
    }

    @Override
    public DeferredValue<String> unwatch() throws Exception {
        return singleToDeferredValue(commander.unwatch());
    }

    @Override
    public DeferredValue<Long> wait(final long numslaves, final long timeout) throws Exception {
        return singleToDeferredValue(commander.wait(numslaves, timeout));
    }

    @Override
    public DeferredValue<String> watch(@RedisProtocolSupport.Key final Buffer key) throws Exception {
        return singleToDeferredValue(commander.watch(key));
    }

    @Override
    public DeferredValue<String> watch(@RedisProtocolSupport.Key final Buffer key1,
                                       @RedisProtocolSupport.Key final Buffer key2) throws Exception {
        return singleToDeferredValue(commander.watch(key1, key2));
    }

    @Override
    public DeferredValue<String> watch(@RedisProtocolSupport.Key final Buffer key1,
                                       @RedisProtocolSupport.Key final Buffer key2,
                                       @RedisProtocolSupport.Key final Buffer key3) throws Exception {
        return singleToDeferredValue(commander.watch(key1, key2, key3));
    }

    @Override
    public DeferredValue<String> watch(@RedisProtocolSupport.Key final Collection<Buffer> keys) throws Exception {
        return singleToDeferredValue(commander.watch(keys));
    }

    @Override
    public DeferredValue<Buffer> xadd(@RedisProtocolSupport.Key final Buffer key, final Buffer id, final Buffer field,
                                      final Buffer value) throws Exception {
        return singleToDeferredValue(commander.xadd(key, id, field, value));
    }

    @Override
    public DeferredValue<Buffer> xadd(@RedisProtocolSupport.Key final Buffer key, final Buffer id, final Buffer field1,
                                      final Buffer value1, final Buffer field2, final Buffer value2) throws Exception {
        return singleToDeferredValue(commander.xadd(key, id, field1, value1, field2, value2));
    }

    @Override
    public DeferredValue<Buffer> xadd(@RedisProtocolSupport.Key final Buffer key, final Buffer id, final Buffer field1,
                                      final Buffer value1, final Buffer field2, final Buffer value2,
                                      final Buffer field3, final Buffer value3) throws Exception {
        return singleToDeferredValue(commander.xadd(key, id, field1, value1, field2, value2, field3, value3));
    }

    @Override
    public DeferredValue<Buffer> xadd(@RedisProtocolSupport.Key final Buffer key, final Buffer id,
                                      final Collection<RedisProtocolSupport.BufferFieldValue> fieldValues) throws Exception {
        return singleToDeferredValue(commander.xadd(key, id, fieldValues));
    }

    @Override
    public DeferredValue<Long> xlen(@RedisProtocolSupport.Key final Buffer key) throws Exception {
        return singleToDeferredValue(commander.xlen(key));
    }

    @Override
    public <T> DeferredValue<List<T>> xpending(@RedisProtocolSupport.Key final Buffer key,
                                               final Buffer group) throws Exception {
        return singleToDeferredValue(commander.xpending(key, group));
    }

    @Override
    public <T> DeferredValue<List<T>> xpending(@RedisProtocolSupport.Key final Buffer key, final Buffer group,
                                               @Nullable final Buffer start, @Nullable final Buffer end,
                                               @Nullable final Long count,
                                               @Nullable final Buffer consumer) throws Exception {
        return singleToDeferredValue(commander.xpending(key, group, start, end, count, consumer));
    }

    @Override
    public <T> DeferredValue<List<T>> xrange(@RedisProtocolSupport.Key final Buffer key, final Buffer start,
                                             final Buffer end) throws Exception {
        return singleToDeferredValue(commander.xrange(key, start, end));
    }

    @Override
    public <T> DeferredValue<List<T>> xrange(@RedisProtocolSupport.Key final Buffer key, final Buffer start,
                                             final Buffer end, @Nullable final Long count) throws Exception {
        return singleToDeferredValue(commander.xrange(key, start, end, count));
    }

    @Override
    public <T> DeferredValue<List<T>> xread(@RedisProtocolSupport.Key final Collection<Buffer> keys,
                                            final Collection<Buffer> ids) throws Exception {
        return singleToDeferredValue(commander.xread(keys, ids));
    }

    @Override
    public <T> DeferredValue<List<T>> xread(@Nullable final Long count, @Nullable final Long blockMilliseconds,
                                            @RedisProtocolSupport.Key final Collection<Buffer> keys,
                                            final Collection<Buffer> ids) throws Exception {
        return singleToDeferredValue(commander.xread(count, blockMilliseconds, keys, ids));
    }

    @Override
    public <T> DeferredValue<List<T>> xreadgroup(final RedisProtocolSupport.BufferGroupConsumer groupConsumer,
                                                 @RedisProtocolSupport.Key final Collection<Buffer> keys,
                                                 final Collection<Buffer> ids) throws Exception {
        return singleToDeferredValue(commander.xreadgroup(groupConsumer, keys, ids));
    }

    @Override
    public <T> DeferredValue<List<T>> xreadgroup(final RedisProtocolSupport.BufferGroupConsumer groupConsumer,
                                                 @Nullable final Long count, @Nullable final Long blockMilliseconds,
                                                 @RedisProtocolSupport.Key final Collection<Buffer> keys,
                                                 final Collection<Buffer> ids) throws Exception {
        return singleToDeferredValue(commander.xreadgroup(groupConsumer, count, blockMilliseconds, keys, ids));
    }

    @Override
    public <T> DeferredValue<List<T>> xrevrange(@RedisProtocolSupport.Key final Buffer key, final Buffer end,
                                                final Buffer start) throws Exception {
        return singleToDeferredValue(commander.xrevrange(key, end, start));
    }

    @Override
    public <T> DeferredValue<List<T>> xrevrange(@RedisProtocolSupport.Key final Buffer key, final Buffer end,
                                                final Buffer start, @Nullable final Long count) throws Exception {
        return singleToDeferredValue(commander.xrevrange(key, end, start, count));
    }

    @Override
    public DeferredValue<Long> zadd(@RedisProtocolSupport.Key final Buffer key,
                                    final Collection<RedisProtocolSupport.BufferScoreMember> scoreMembers) throws Exception {
        return singleToDeferredValue(commander.zadd(key, scoreMembers));
    }

    @Override
    public DeferredValue<Long> zadd(@RedisProtocolSupport.Key final Buffer key,
                                    @Nullable final RedisProtocolSupport.ZaddCondition condition,
                                    @Nullable final RedisProtocolSupport.ZaddChange change, final double score,
                                    final Buffer member) throws Exception {
        return singleToDeferredValue(commander.zadd(key, condition, change, score, member));
    }

    @Override
    public DeferredValue<Long> zadd(@RedisProtocolSupport.Key final Buffer key,
                                    @Nullable final RedisProtocolSupport.ZaddCondition condition,
                                    @Nullable final RedisProtocolSupport.ZaddChange change, final double score1,
                                    final Buffer member1, final double score2, final Buffer member2) throws Exception {
        return singleToDeferredValue(commander.zadd(key, condition, change, score1, member1, score2, member2));
    }

    @Override
    public DeferredValue<Long> zadd(@RedisProtocolSupport.Key final Buffer key,
                                    @Nullable final RedisProtocolSupport.ZaddCondition condition,
                                    @Nullable final RedisProtocolSupport.ZaddChange change, final double score1,
                                    final Buffer member1, final double score2, final Buffer member2,
                                    final double score3, final Buffer member3) throws Exception {
        return singleToDeferredValue(
                    commander.zadd(key, condition, change, score1, member1, score2, member2, score3, member3));
    }

    @Override
    public DeferredValue<Long> zadd(@RedisProtocolSupport.Key final Buffer key,
                                    @Nullable final RedisProtocolSupport.ZaddCondition condition,
                                    @Nullable final RedisProtocolSupport.ZaddChange change,
                                    final Collection<RedisProtocolSupport.BufferScoreMember> scoreMembers) throws Exception {
        return singleToDeferredValue(commander.zadd(key, condition, change, scoreMembers));
    }

    @Override
    public DeferredValue<Double> zaddIncr(@RedisProtocolSupport.Key final Buffer key,
                                          final Collection<RedisProtocolSupport.BufferScoreMember> scoreMembers) throws Exception {
        return singleToDeferredValue(commander.zaddIncr(key, scoreMembers));
    }

    @Override
    public DeferredValue<Double> zaddIncr(@RedisProtocolSupport.Key final Buffer key,
                                          @Nullable final RedisProtocolSupport.ZaddCondition condition,
                                          @Nullable final RedisProtocolSupport.ZaddChange change, final double score,
                                          final Buffer member) throws Exception {
        return singleToDeferredValue(commander.zaddIncr(key, condition, change, score, member));
    }

    @Override
    public DeferredValue<Double> zaddIncr(@RedisProtocolSupport.Key final Buffer key,
                                          @Nullable final RedisProtocolSupport.ZaddCondition condition,
                                          @Nullable final RedisProtocolSupport.ZaddChange change, final double score1,
                                          final Buffer member1, final double score2,
                                          final Buffer member2) throws Exception {
        return singleToDeferredValue(commander.zaddIncr(key, condition, change, score1, member1, score2, member2));
    }

    @Override
    public DeferredValue<Double> zaddIncr(@RedisProtocolSupport.Key final Buffer key,
                                          @Nullable final RedisProtocolSupport.ZaddCondition condition,
                                          @Nullable final RedisProtocolSupport.ZaddChange change, final double score1,
                                          final Buffer member1, final double score2, final Buffer member2,
                                          final double score3, final Buffer member3) throws Exception {
        return singleToDeferredValue(
                    commander.zaddIncr(key, condition, change, score1, member1, score2, member2, score3, member3));
    }

    @Override
    public DeferredValue<Double> zaddIncr(@RedisProtocolSupport.Key final Buffer key,
                                          @Nullable final RedisProtocolSupport.ZaddCondition condition,
                                          @Nullable final RedisProtocolSupport.ZaddChange change,
                                          final Collection<RedisProtocolSupport.BufferScoreMember> scoreMembers) throws Exception {
        return singleToDeferredValue(commander.zaddIncr(key, condition, change, scoreMembers));
    }

    @Override
    public DeferredValue<Long> zcard(@RedisProtocolSupport.Key final Buffer key) throws Exception {
        return singleToDeferredValue(commander.zcard(key));
    }

    @Override
    public DeferredValue<Long> zcount(@RedisProtocolSupport.Key final Buffer key, final double min,
                                      final double max) throws Exception {
        return singleToDeferredValue(commander.zcount(key, min, max));
    }

    @Override
    public DeferredValue<Double> zincrby(@RedisProtocolSupport.Key final Buffer key, final long increment,
                                         final Buffer member) throws Exception {
        return singleToDeferredValue(commander.zincrby(key, increment, member));
    }

    @Override
    public DeferredValue<Long> zinterstore(@RedisProtocolSupport.Key final Buffer destination, final long numkeys,
                                           @RedisProtocolSupport.Key final Collection<Buffer> keys) throws Exception {
        return singleToDeferredValue(commander.zinterstore(destination, numkeys, keys));
    }

    @Override
    public DeferredValue<Long> zinterstore(@RedisProtocolSupport.Key final Buffer destination, final long numkeys,
                                           @RedisProtocolSupport.Key final Collection<Buffer> keys,
                                           final Collection<Long> weightses,
                                           @Nullable final RedisProtocolSupport.ZinterstoreAggregate aggregate) throws Exception {
        return singleToDeferredValue(commander.zinterstore(destination, numkeys, keys, weightses, aggregate));
    }

    @Override
    public DeferredValue<Long> zlexcount(@RedisProtocolSupport.Key final Buffer key, final Buffer min,
                                         final Buffer max) throws Exception {
        return singleToDeferredValue(commander.zlexcount(key, min, max));
    }

    @Override
    public <T> DeferredValue<List<T>> zpopmax(@RedisProtocolSupport.Key final Buffer key) throws Exception {
        return singleToDeferredValue(commander.zpopmax(key));
    }

    @Override
    public <T> DeferredValue<List<T>> zpopmax(@RedisProtocolSupport.Key final Buffer key,
                                              @Nullable final Long count) throws Exception {
        return singleToDeferredValue(commander.zpopmax(key, count));
    }

    @Override
    public <T> DeferredValue<List<T>> zpopmin(@RedisProtocolSupport.Key final Buffer key) throws Exception {
        return singleToDeferredValue(commander.zpopmin(key));
    }

    @Override
    public <T> DeferredValue<List<T>> zpopmin(@RedisProtocolSupport.Key final Buffer key,
                                              @Nullable final Long count) throws Exception {
        return singleToDeferredValue(commander.zpopmin(key, count));
    }

    @Override
    public <T> DeferredValue<List<T>> zrange(@RedisProtocolSupport.Key final Buffer key, final long start,
                                             final long stop) throws Exception {
        return singleToDeferredValue(commander.zrange(key, start, stop));
    }

    @Override
    public <T> DeferredValue<List<T>> zrange(@RedisProtocolSupport.Key final Buffer key, final long start,
                                             final long stop,
                                             @Nullable final RedisProtocolSupport.ZrangeWithscores withscores) throws Exception {
        return singleToDeferredValue(commander.zrange(key, start, stop, withscores));
    }

    @Override
    public <T> DeferredValue<List<T>> zrangebylex(@RedisProtocolSupport.Key final Buffer key, final Buffer min,
                                                  final Buffer max) throws Exception {
        return singleToDeferredValue(commander.zrangebylex(key, min, max));
    }

    @Override
    public <T> DeferredValue<List<T>> zrangebylex(@RedisProtocolSupport.Key final Buffer key, final Buffer min,
                                                  final Buffer max,
                                                  @Nullable final RedisProtocolSupport.OffsetCount offsetCount) throws Exception {
        return singleToDeferredValue(commander.zrangebylex(key, min, max, offsetCount));
    }

    @Override
    public <T> DeferredValue<List<T>> zrangebyscore(@RedisProtocolSupport.Key final Buffer key, final double min,
                                                    final double max) throws Exception {
        return singleToDeferredValue(commander.zrangebyscore(key, min, max));
    }

    @Override
    public <T> DeferredValue<List<T>> zrangebyscore(@RedisProtocolSupport.Key final Buffer key, final double min,
                                                    final double max,
                                                    @Nullable final RedisProtocolSupport.ZrangebyscoreWithscores withscores,
                                                    @Nullable final RedisProtocolSupport.OffsetCount offsetCount) throws Exception {
        return singleToDeferredValue(commander.zrangebyscore(key, min, max, withscores, offsetCount));
    }

    @Override
    public DeferredValue<Long> zrank(@RedisProtocolSupport.Key final Buffer key, final Buffer member) throws Exception {
        return singleToDeferredValue(commander.zrank(key, member));
    }

    @Override
    public DeferredValue<Long> zrem(@RedisProtocolSupport.Key final Buffer key, final Buffer member) throws Exception {
        return singleToDeferredValue(commander.zrem(key, member));
    }

    @Override
    public DeferredValue<Long> zrem(@RedisProtocolSupport.Key final Buffer key, final Buffer member1,
                                    final Buffer member2) throws Exception {
        return singleToDeferredValue(commander.zrem(key, member1, member2));
    }

    @Override
    public DeferredValue<Long> zrem(@RedisProtocolSupport.Key final Buffer key, final Buffer member1,
                                    final Buffer member2, final Buffer member3) throws Exception {
        return singleToDeferredValue(commander.zrem(key, member1, member2, member3));
    }

    @Override
    public DeferredValue<Long> zrem(@RedisProtocolSupport.Key final Buffer key,
                                    final Collection<Buffer> members) throws Exception {
        return singleToDeferredValue(commander.zrem(key, members));
    }

    @Override
    public DeferredValue<Long> zremrangebylex(@RedisProtocolSupport.Key final Buffer key, final Buffer min,
                                              final Buffer max) throws Exception {
        return singleToDeferredValue(commander.zremrangebylex(key, min, max));
    }

    @Override
    public DeferredValue<Long> zremrangebyrank(@RedisProtocolSupport.Key final Buffer key, final long start,
                                               final long stop) throws Exception {
        return singleToDeferredValue(commander.zremrangebyrank(key, start, stop));
    }

    @Override
    public DeferredValue<Long> zremrangebyscore(@RedisProtocolSupport.Key final Buffer key, final double min,
                                                final double max) throws Exception {
        return singleToDeferredValue(commander.zremrangebyscore(key, min, max));
    }

    @Override
    public <T> DeferredValue<List<T>> zrevrange(@RedisProtocolSupport.Key final Buffer key, final long start,
                                                final long stop) throws Exception {
        return singleToDeferredValue(commander.zrevrange(key, start, stop));
    }

    @Override
    public <T> DeferredValue<List<T>> zrevrange(@RedisProtocolSupport.Key final Buffer key, final long start,
                                                final long stop,
                                                @Nullable final RedisProtocolSupport.ZrevrangeWithscores withscores) throws Exception {
        return singleToDeferredValue(commander.zrevrange(key, start, stop, withscores));
    }

    @Override
    public <T> DeferredValue<List<T>> zrevrangebylex(@RedisProtocolSupport.Key final Buffer key, final Buffer max,
                                                     final Buffer min) throws Exception {
        return singleToDeferredValue(commander.zrevrangebylex(key, max, min));
    }

    @Override
    public <T> DeferredValue<List<T>> zrevrangebylex(@RedisProtocolSupport.Key final Buffer key, final Buffer max,
                                                     final Buffer min,
                                                     @Nullable final RedisProtocolSupport.OffsetCount offsetCount) throws Exception {
        return singleToDeferredValue(commander.zrevrangebylex(key, max, min, offsetCount));
    }

    @Override
    public <T> DeferredValue<List<T>> zrevrangebyscore(@RedisProtocolSupport.Key final Buffer key, final double max,
                                                       final double min) throws Exception {
        return singleToDeferredValue(commander.zrevrangebyscore(key, max, min));
    }

    @Override
    public <T> DeferredValue<List<T>> zrevrangebyscore(@RedisProtocolSupport.Key final Buffer key, final double max,
                                                       final double min,
                                                       @Nullable final RedisProtocolSupport.ZrevrangebyscoreWithscores withscores,
                                                       @Nullable final RedisProtocolSupport.OffsetCount offsetCount) throws Exception {
        return singleToDeferredValue(commander.zrevrangebyscore(key, max, min, withscores, offsetCount));
    }

    @Override
    public DeferredValue<Long> zrevrank(@RedisProtocolSupport.Key final Buffer key,
                                        final Buffer member) throws Exception {
        return singleToDeferredValue(commander.zrevrank(key, member));
    }

    @Override
    public <T> DeferredValue<List<T>> zscan(@RedisProtocolSupport.Key final Buffer key,
                                            final long cursor) throws Exception {
        return singleToDeferredValue(commander.zscan(key, cursor));
    }

    @Override
    public <T> DeferredValue<List<T>> zscan(@RedisProtocolSupport.Key final Buffer key, final long cursor,
                                            @Nullable final Buffer matchPattern,
                                            @Nullable final Long count) throws Exception {
        return singleToDeferredValue(commander.zscan(key, cursor, matchPattern, count));
    }

    @Override
    public DeferredValue<Double> zscore(@RedisProtocolSupport.Key final Buffer key,
                                        final Buffer member) throws Exception {
        return singleToDeferredValue(commander.zscore(key, member));
    }

    @Override
    public DeferredValue<Long> zunionstore(@RedisProtocolSupport.Key final Buffer destination, final long numkeys,
                                           @RedisProtocolSupport.Key final Collection<Buffer> keys) throws Exception {
        return singleToDeferredValue(commander.zunionstore(destination, numkeys, keys));
    }

    @Override
    public DeferredValue<Long> zunionstore(@RedisProtocolSupport.Key final Buffer destination, final long numkeys,
                                           @RedisProtocolSupport.Key final Collection<Buffer> keys,
                                           final Collection<Long> weightses,
                                           @Nullable final RedisProtocolSupport.ZunionstoreAggregate aggregate) throws Exception {
        return singleToDeferredValue(commander.zunionstore(destination, numkeys, keys, weightses, aggregate));
    }
}

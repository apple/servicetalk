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
import io.servicetalk.concurrent.api.Completable;
import io.servicetalk.concurrent.api.Single;

import java.util.Collection;
import java.util.List;
import java.util.Objects;
import javax.annotation.Generated;
import javax.annotation.Nullable;

import static io.servicetalk.redis.api.BlockingUtils.blockingToCompletable;
import static io.servicetalk.redis.api.BlockingUtils.blockingToSingle;

@Generated({})
@SuppressWarnings("unchecked")
final class BlockingTransactedBufferRedisCommanderToTransactedBufferRedisCommander extends TransactedBufferRedisCommander {

    private final BlockingTransactedBufferRedisCommander reservedCnx;

    BlockingTransactedBufferRedisCommanderToTransactedBufferRedisCommander(
                final BlockingTransactedBufferRedisCommander reservedCnx) {
        this.reservedCnx = Objects.requireNonNull(reservedCnx);
    }

    @Override
    public Completable closeAsync() {
        return blockingToCompletable(reservedCnx::close);
    }

    @Override
    public Completable closeAsyncGracefully() {
        return closeAsync();
    }

    @Override
    public Single<Long> append(@RedisProtocolSupport.Key final Buffer key, final Buffer value) {
        return blockingToSingle(() -> reservedCnx.append(key, value).blockingGet());
    }

    @Override
    public Single<String> auth(final Buffer password) {
        return blockingToSingle(() -> reservedCnx.auth(password).blockingGet());
    }

    @Override
    public Single<String> bgrewriteaof() {
        return blockingToSingle(() -> reservedCnx.bgrewriteaof().blockingGet());
    }

    @Override
    public Single<String> bgsave() {
        return blockingToSingle(() -> reservedCnx.bgsave().blockingGet());
    }

    @Override
    public Single<Long> bitcount(@RedisProtocolSupport.Key final Buffer key) {
        return blockingToSingle(() -> reservedCnx.bitcount(key).blockingGet());
    }

    @Override
    public Single<Long> bitcount(@RedisProtocolSupport.Key final Buffer key, @Nullable final Long start,
                                 @Nullable final Long end) {
        return blockingToSingle(() -> reservedCnx.bitcount(key, start, end).blockingGet());
    }

    @Override
    public Single<List<Long>> bitfield(@RedisProtocolSupport.Key final Buffer key,
                                       final Collection<RedisProtocolSupport.BitfieldOperation> operations) {
        return blockingToSingle(() -> reservedCnx.bitfield(key, operations).blockingGet());
    }

    @Override
    public Single<Long> bitop(final Buffer operation, @RedisProtocolSupport.Key final Buffer destkey,
                              @RedisProtocolSupport.Key final Buffer key) {
        return blockingToSingle(() -> reservedCnx.bitop(operation, destkey, key).blockingGet());
    }

    @Override
    public Single<Long> bitop(final Buffer operation, @RedisProtocolSupport.Key final Buffer destkey,
                              @RedisProtocolSupport.Key final Buffer key1,
                              @RedisProtocolSupport.Key final Buffer key2) {
        return blockingToSingle(() -> reservedCnx.bitop(operation, destkey, key1, key2).blockingGet());
    }

    @Override
    public Single<Long> bitop(final Buffer operation, @RedisProtocolSupport.Key final Buffer destkey,
                              @RedisProtocolSupport.Key final Buffer key1, @RedisProtocolSupport.Key final Buffer key2,
                              @RedisProtocolSupport.Key final Buffer key3) {
        return blockingToSingle(() -> reservedCnx.bitop(operation, destkey, key1, key2, key3).blockingGet());
    }

    @Override
    public Single<Long> bitop(final Buffer operation, @RedisProtocolSupport.Key final Buffer destkey,
                              @RedisProtocolSupport.Key final Collection<Buffer> keys) {
        return blockingToSingle(() -> reservedCnx.bitop(operation, destkey, keys).blockingGet());
    }

    @Override
    public Single<Long> bitpos(@RedisProtocolSupport.Key final Buffer key, final long bit) {
        return blockingToSingle(() -> reservedCnx.bitpos(key, bit).blockingGet());
    }

    @Override
    public Single<Long> bitpos(@RedisProtocolSupport.Key final Buffer key, final long bit, @Nullable final Long start,
                               @Nullable final Long end) {
        return blockingToSingle(() -> reservedCnx.bitpos(key, bit, start, end).blockingGet());
    }

    @Override
    public <T> Single<List<T>> blpop(@RedisProtocolSupport.Key final Collection<Buffer> keys, final long timeout) {
        return blockingToSingle(() -> reservedCnx.blpop(keys, timeout).blockingGet());
    }

    @Override
    public <T> Single<List<T>> brpop(@RedisProtocolSupport.Key final Collection<Buffer> keys, final long timeout) {
        return blockingToSingle(() -> reservedCnx.brpop(keys, timeout).blockingGet());
    }

    @Override
    public Single<Buffer> brpoplpush(@RedisProtocolSupport.Key final Buffer source,
                                     @RedisProtocolSupport.Key final Buffer destination, final long timeout) {
        return blockingToSingle(() -> reservedCnx.brpoplpush(source, destination, timeout).blockingGet());
    }

    @Override
    public <T> Single<List<T>> bzpopmax(@RedisProtocolSupport.Key final Collection<Buffer> keys, final long timeout) {
        return blockingToSingle(() -> reservedCnx.bzpopmax(keys, timeout).blockingGet());
    }

    @Override
    public <T> Single<List<T>> bzpopmin(@RedisProtocolSupport.Key final Collection<Buffer> keys, final long timeout) {
        return blockingToSingle(() -> reservedCnx.bzpopmin(keys, timeout).blockingGet());
    }

    @Override
    public Single<Long> clientKill(@Nullable final Long id, @Nullable final RedisProtocolSupport.ClientKillType type,
                                   @Nullable final Buffer addrIpPort, @Nullable final Buffer skipmeYesNo) {
        return blockingToSingle(() -> reservedCnx.clientKill(id, type, addrIpPort, skipmeYesNo).blockingGet());
    }

    @Override
    public Single<Buffer> clientList() {
        return blockingToSingle(() -> reservedCnx.clientList().blockingGet());
    }

    @Override
    public Single<Buffer> clientGetname() {
        return blockingToSingle(() -> reservedCnx.clientGetname().blockingGet());
    }

    @Override
    public Single<String> clientPause(final long timeout) {
        return blockingToSingle(() -> reservedCnx.clientPause(timeout).blockingGet());
    }

    @Override
    public Single<String> clientReply(final RedisProtocolSupport.ClientReplyReplyMode replyMode) {
        return blockingToSingle(() -> reservedCnx.clientReply(replyMode).blockingGet());
    }

    @Override
    public Single<String> clientSetname(final Buffer connectionName) {
        return blockingToSingle(() -> reservedCnx.clientSetname(connectionName).blockingGet());
    }

    @Override
    public Single<String> clusterAddslots(final long slot) {
        return blockingToSingle(() -> reservedCnx.clusterAddslots(slot).blockingGet());
    }

    @Override
    public Single<String> clusterAddslots(final long slot1, final long slot2) {
        return blockingToSingle(() -> reservedCnx.clusterAddslots(slot1, slot2).blockingGet());
    }

    @Override
    public Single<String> clusterAddslots(final long slot1, final long slot2, final long slot3) {
        return blockingToSingle(() -> reservedCnx.clusterAddslots(slot1, slot2, slot3).blockingGet());
    }

    @Override
    public Single<String> clusterAddslots(final Collection<Long> slots) {
        return blockingToSingle(() -> reservedCnx.clusterAddslots(slots).blockingGet());
    }

    @Override
    public Single<Long> clusterCountFailureReports(final Buffer nodeId) {
        return blockingToSingle(() -> reservedCnx.clusterCountFailureReports(nodeId).blockingGet());
    }

    @Override
    public Single<Long> clusterCountkeysinslot(final long slot) {
        return blockingToSingle(() -> reservedCnx.clusterCountkeysinslot(slot).blockingGet());
    }

    @Override
    public Single<String> clusterDelslots(final long slot) {
        return blockingToSingle(() -> reservedCnx.clusterDelslots(slot).blockingGet());
    }

    @Override
    public Single<String> clusterDelslots(final long slot1, final long slot2) {
        return blockingToSingle(() -> reservedCnx.clusterDelslots(slot1, slot2).blockingGet());
    }

    @Override
    public Single<String> clusterDelslots(final long slot1, final long slot2, final long slot3) {
        return blockingToSingle(() -> reservedCnx.clusterDelslots(slot1, slot2, slot3).blockingGet());
    }

    @Override
    public Single<String> clusterDelslots(final Collection<Long> slots) {
        return blockingToSingle(() -> reservedCnx.clusterDelslots(slots).blockingGet());
    }

    @Override
    public Single<String> clusterFailover() {
        return blockingToSingle(() -> reservedCnx.clusterFailover().blockingGet());
    }

    @Override
    public Single<String> clusterFailover(@Nullable final RedisProtocolSupport.ClusterFailoverOptions options) {
        return blockingToSingle(() -> reservedCnx.clusterFailover(options).blockingGet());
    }

    @Override
    public Single<String> clusterForget(final Buffer nodeId) {
        return blockingToSingle(() -> reservedCnx.clusterForget(nodeId).blockingGet());
    }

    @Override
    public <T> Single<List<T>> clusterGetkeysinslot(final long slot, final long count) {
        return blockingToSingle(() -> reservedCnx.clusterGetkeysinslot(slot, count).blockingGet());
    }

    @Override
    public Single<Buffer> clusterInfo() {
        return blockingToSingle(() -> reservedCnx.clusterInfo().blockingGet());
    }

    @Override
    public Single<Long> clusterKeyslot(final Buffer key) {
        return blockingToSingle(() -> reservedCnx.clusterKeyslot(key).blockingGet());
    }

    @Override
    public Single<String> clusterMeet(final Buffer ip, final long port) {
        return blockingToSingle(() -> reservedCnx.clusterMeet(ip, port).blockingGet());
    }

    @Override
    public Single<Buffer> clusterNodes() {
        return blockingToSingle(() -> reservedCnx.clusterNodes().blockingGet());
    }

    @Override
    public Single<String> clusterReplicate(final Buffer nodeId) {
        return blockingToSingle(() -> reservedCnx.clusterReplicate(nodeId).blockingGet());
    }

    @Override
    public Single<String> clusterReset() {
        return blockingToSingle(() -> reservedCnx.clusterReset().blockingGet());
    }

    @Override
    public Single<String> clusterReset(@Nullable final RedisProtocolSupport.ClusterResetResetType resetType) {
        return blockingToSingle(() -> reservedCnx.clusterReset(resetType).blockingGet());
    }

    @Override
    public Single<String> clusterSaveconfig() {
        return blockingToSingle(() -> reservedCnx.clusterSaveconfig().blockingGet());
    }

    @Override
    public Single<String> clusterSetConfigEpoch(final long configEpoch) {
        return blockingToSingle(() -> reservedCnx.clusterSetConfigEpoch(configEpoch).blockingGet());
    }

    @Override
    public Single<String> clusterSetslot(final long slot,
                                         final RedisProtocolSupport.ClusterSetslotSubcommand subcommand) {
        return blockingToSingle(() -> reservedCnx.clusterSetslot(slot, subcommand).blockingGet());
    }

    @Override
    public Single<String> clusterSetslot(final long slot,
                                         final RedisProtocolSupport.ClusterSetslotSubcommand subcommand,
                                         @Nullable final Buffer nodeId) {
        return blockingToSingle(() -> reservedCnx.clusterSetslot(slot, subcommand, nodeId).blockingGet());
    }

    @Override
    public Single<Buffer> clusterSlaves(final Buffer nodeId) {
        return blockingToSingle(() -> reservedCnx.clusterSlaves(nodeId).blockingGet());
    }

    @Override
    public <T> Single<List<T>> clusterSlots() {
        return blockingToSingle(() -> reservedCnx.clusterSlots().blockingGet());
    }

    @Override
    public <T> Single<List<T>> command() {
        return blockingToSingle(() -> reservedCnx.command().blockingGet());
    }

    @Override
    public Single<Long> commandCount() {
        return blockingToSingle(() -> reservedCnx.commandCount().blockingGet());
    }

    @Override
    public <T> Single<List<T>> commandGetkeys() {
        return blockingToSingle(() -> reservedCnx.commandGetkeys().blockingGet());
    }

    @Override
    public <T> Single<List<T>> commandInfo(final Buffer commandName) {
        return blockingToSingle(() -> reservedCnx.commandInfo(commandName).blockingGet());
    }

    @Override
    public <T> Single<List<T>> commandInfo(final Buffer commandName1, final Buffer commandName2) {
        return blockingToSingle(() -> reservedCnx.commandInfo(commandName1, commandName2).blockingGet());
    }

    @Override
    public <T> Single<List<T>> commandInfo(final Buffer commandName1, final Buffer commandName2,
                                           final Buffer commandName3) {
        return blockingToSingle(() -> reservedCnx.commandInfo(commandName1, commandName2, commandName3).blockingGet());
    }

    @Override
    public <T> Single<List<T>> commandInfo(final Collection<Buffer> commandNames) {
        return blockingToSingle(() -> reservedCnx.commandInfo(commandNames).blockingGet());
    }

    @Override
    public <T> Single<List<T>> configGet(final Buffer parameter) {
        return blockingToSingle(() -> reservedCnx.configGet(parameter).blockingGet());
    }

    @Override
    public Single<String> configRewrite() {
        return blockingToSingle(() -> reservedCnx.configRewrite().blockingGet());
    }

    @Override
    public Single<String> configSet(final Buffer parameter, final Buffer value) {
        return blockingToSingle(() -> reservedCnx.configSet(parameter, value).blockingGet());
    }

    @Override
    public Single<String> configResetstat() {
        return blockingToSingle(() -> reservedCnx.configResetstat().blockingGet());
    }

    @Override
    public Single<Long> dbsize() {
        return blockingToSingle(() -> reservedCnx.dbsize().blockingGet());
    }

    @Override
    public Single<String> debugObject(@RedisProtocolSupport.Key final Buffer key) {
        return blockingToSingle(() -> reservedCnx.debugObject(key).blockingGet());
    }

    @Override
    public Single<String> debugSegfault() {
        return blockingToSingle(() -> reservedCnx.debugSegfault().blockingGet());
    }

    @Override
    public Single<Long> decr(@RedisProtocolSupport.Key final Buffer key) {
        return blockingToSingle(() -> reservedCnx.decr(key).blockingGet());
    }

    @Override
    public Single<Long> decrby(@RedisProtocolSupport.Key final Buffer key, final long decrement) {
        return blockingToSingle(() -> reservedCnx.decrby(key, decrement).blockingGet());
    }

    @Override
    public Single<Long> del(@RedisProtocolSupport.Key final Buffer key) {
        return blockingToSingle(() -> reservedCnx.del(key).blockingGet());
    }

    @Override
    public Single<Long> del(@RedisProtocolSupport.Key final Buffer key1, @RedisProtocolSupport.Key final Buffer key2) {
        return blockingToSingle(() -> reservedCnx.del(key1, key2).blockingGet());
    }

    @Override
    public Single<Long> del(@RedisProtocolSupport.Key final Buffer key1, @RedisProtocolSupport.Key final Buffer key2,
                            @RedisProtocolSupport.Key final Buffer key3) {
        return blockingToSingle(() -> reservedCnx.del(key1, key2, key3).blockingGet());
    }

    @Override
    public Single<Long> del(@RedisProtocolSupport.Key final Collection<Buffer> keys) {
        return blockingToSingle(() -> reservedCnx.del(keys).blockingGet());
    }

    @Override
    public Single<String> discard() {
        return blockingToSingle(() -> reservedCnx.discard());
    }

    @Override
    public Single<Buffer> dump(@RedisProtocolSupport.Key final Buffer key) {
        return blockingToSingle(() -> reservedCnx.dump(key).blockingGet());
    }

    @Override
    public Single<Buffer> echo(final Buffer message) {
        return blockingToSingle(() -> reservedCnx.echo(message).blockingGet());
    }

    @Override
    public Single<Buffer> eval(final Buffer script, final long numkeys,
                               @RedisProtocolSupport.Key final Collection<Buffer> keys, final Collection<Buffer> args) {
        return blockingToSingle(() -> reservedCnx.eval(script, numkeys, keys, args).blockingGet());
    }

    @Override
    public <T> Single<List<T>> evalList(final Buffer script, final long numkeys,
                                        @RedisProtocolSupport.Key final Collection<Buffer> keys,
                                        final Collection<Buffer> args) {
        return blockingToSingle(() -> reservedCnx.evalList(script, numkeys, keys, args).blockingGet());
    }

    @Override
    public Single<Long> evalLong(final Buffer script, final long numkeys,
                                 @RedisProtocolSupport.Key final Collection<Buffer> keys,
                                 final Collection<Buffer> args) {
        return blockingToSingle(() -> reservedCnx.evalLong(script, numkeys, keys, args).blockingGet());
    }

    @Override
    public Single<Buffer> evalsha(final Buffer sha1, final long numkeys,
                                  @RedisProtocolSupport.Key final Collection<Buffer> keys,
                                  final Collection<Buffer> args) {
        return blockingToSingle(() -> reservedCnx.evalsha(sha1, numkeys, keys, args).blockingGet());
    }

    @Override
    public <T> Single<List<T>> evalshaList(final Buffer sha1, final long numkeys,
                                           @RedisProtocolSupport.Key final Collection<Buffer> keys,
                                           final Collection<Buffer> args) {
        return blockingToSingle(() -> reservedCnx.evalshaList(sha1, numkeys, keys, args).blockingGet());
    }

    @Override
    public Single<Long> evalshaLong(final Buffer sha1, final long numkeys,
                                    @RedisProtocolSupport.Key final Collection<Buffer> keys,
                                    final Collection<Buffer> args) {
        return blockingToSingle(() -> reservedCnx.evalshaLong(sha1, numkeys, keys, args).blockingGet());
    }

    @Override
    public Completable exec() {
        return blockingToCompletable(() -> reservedCnx.exec());
    }

    @Override
    public Single<Long> exists(@RedisProtocolSupport.Key final Buffer key) {
        return blockingToSingle(() -> reservedCnx.exists(key).blockingGet());
    }

    @Override
    public Single<Long> exists(@RedisProtocolSupport.Key final Buffer key1,
                               @RedisProtocolSupport.Key final Buffer key2) {
        return blockingToSingle(() -> reservedCnx.exists(key1, key2).blockingGet());
    }

    @Override
    public Single<Long> exists(@RedisProtocolSupport.Key final Buffer key1, @RedisProtocolSupport.Key final Buffer key2,
                               @RedisProtocolSupport.Key final Buffer key3) {
        return blockingToSingle(() -> reservedCnx.exists(key1, key2, key3).blockingGet());
    }

    @Override
    public Single<Long> exists(@RedisProtocolSupport.Key final Collection<Buffer> keys) {
        return blockingToSingle(() -> reservedCnx.exists(keys).blockingGet());
    }

    @Override
    public Single<Long> expire(@RedisProtocolSupport.Key final Buffer key, final long seconds) {
        return blockingToSingle(() -> reservedCnx.expire(key, seconds).blockingGet());
    }

    @Override
    public Single<Long> expireat(@RedisProtocolSupport.Key final Buffer key, final long timestamp) {
        return blockingToSingle(() -> reservedCnx.expireat(key, timestamp).blockingGet());
    }

    @Override
    public Single<String> flushall() {
        return blockingToSingle(() -> reservedCnx.flushall().blockingGet());
    }

    @Override
    public Single<String> flushall(@Nullable final RedisProtocolSupport.FlushallAsync async) {
        return blockingToSingle(() -> reservedCnx.flushall(async).blockingGet());
    }

    @Override
    public Single<String> flushdb() {
        return blockingToSingle(() -> reservedCnx.flushdb().blockingGet());
    }

    @Override
    public Single<String> flushdb(@Nullable final RedisProtocolSupport.FlushdbAsync async) {
        return blockingToSingle(() -> reservedCnx.flushdb(async).blockingGet());
    }

    @Override
    public Single<Long> geoadd(@RedisProtocolSupport.Key final Buffer key, final double longitude,
                               final double latitude, final Buffer member) {
        return blockingToSingle(() -> reservedCnx.geoadd(key, longitude, latitude, member).blockingGet());
    }

    @Override
    public Single<Long> geoadd(@RedisProtocolSupport.Key final Buffer key, final double longitude1,
                               final double latitude1, final Buffer member1, final double longitude2,
                               final double latitude2, final Buffer member2) {
        return blockingToSingle(() -> reservedCnx
                    .geoadd(key, longitude1, latitude1, member1, longitude2, latitude2, member2).blockingGet());
    }

    @Override
    public Single<Long> geoadd(@RedisProtocolSupport.Key final Buffer key, final double longitude1,
                               final double latitude1, final Buffer member1, final double longitude2,
                               final double latitude2, final Buffer member2, final double longitude3,
                               final double latitude3, final Buffer member3) {
        return blockingToSingle(() -> reservedCnx.geoadd(key, longitude1, latitude1, member1, longitude2, latitude2,
                    member2, longitude3, latitude3, member3).blockingGet());
    }

    @Override
    public Single<Long> geoadd(@RedisProtocolSupport.Key final Buffer key,
                               final Collection<RedisProtocolSupport.BufferLongitudeLatitudeMember> longitudeLatitudeMembers) {
        return blockingToSingle(() -> reservedCnx.geoadd(key, longitudeLatitudeMembers).blockingGet());
    }

    @Override
    public Single<Double> geodist(@RedisProtocolSupport.Key final Buffer key, final Buffer member1,
                                  final Buffer member2) {
        return blockingToSingle(() -> reservedCnx.geodist(key, member1, member2).blockingGet());
    }

    @Override
    public Single<Double> geodist(@RedisProtocolSupport.Key final Buffer key, final Buffer member1,
                                  final Buffer member2, @Nullable final Buffer unit) {
        return blockingToSingle(() -> reservedCnx.geodist(key, member1, member2, unit).blockingGet());
    }

    @Override
    public <T> Single<List<T>> geohash(@RedisProtocolSupport.Key final Buffer key, final Buffer member) {
        return blockingToSingle(() -> reservedCnx.geohash(key, member).blockingGet());
    }

    @Override
    public <T> Single<List<T>> geohash(@RedisProtocolSupport.Key final Buffer key, final Buffer member1,
                                       final Buffer member2) {
        return blockingToSingle(() -> reservedCnx.geohash(key, member1, member2).blockingGet());
    }

    @Override
    public <T> Single<List<T>> geohash(@RedisProtocolSupport.Key final Buffer key, final Buffer member1,
                                       final Buffer member2, final Buffer member3) {
        return blockingToSingle(() -> reservedCnx.geohash(key, member1, member2, member3).blockingGet());
    }

    @Override
    public <T> Single<List<T>> geohash(@RedisProtocolSupport.Key final Buffer key, final Collection<Buffer> members) {
        return blockingToSingle(() -> reservedCnx.geohash(key, members).blockingGet());
    }

    @Override
    public <T> Single<List<T>> geopos(@RedisProtocolSupport.Key final Buffer key, final Buffer member) {
        return blockingToSingle(() -> reservedCnx.geopos(key, member).blockingGet());
    }

    @Override
    public <T> Single<List<T>> geopos(@RedisProtocolSupport.Key final Buffer key, final Buffer member1,
                                      final Buffer member2) {
        return blockingToSingle(() -> reservedCnx.geopos(key, member1, member2).blockingGet());
    }

    @Override
    public <T> Single<List<T>> geopos(@RedisProtocolSupport.Key final Buffer key, final Buffer member1,
                                      final Buffer member2, final Buffer member3) {
        return blockingToSingle(() -> reservedCnx.geopos(key, member1, member2, member3).blockingGet());
    }

    @Override
    public <T> Single<List<T>> geopos(@RedisProtocolSupport.Key final Buffer key, final Collection<Buffer> members) {
        return blockingToSingle(() -> reservedCnx.geopos(key, members).blockingGet());
    }

    @Override
    public <T> Single<List<T>> georadius(@RedisProtocolSupport.Key final Buffer key, final double longitude,
                                         final double latitude, final double radius,
                                         final RedisProtocolSupport.GeoradiusUnit unit) {
        return blockingToSingle(() -> reservedCnx.georadius(key, longitude, latitude, radius, unit).blockingGet());
    }

    @Override
    public <T> Single<List<T>> georadius(@RedisProtocolSupport.Key final Buffer key, final double longitude,
                                         final double latitude, final double radius,
                                         final RedisProtocolSupport.GeoradiusUnit unit,
                                         @Nullable final RedisProtocolSupport.GeoradiusWithcoord withcoord,
                                         @Nullable final RedisProtocolSupport.GeoradiusWithdist withdist,
                                         @Nullable final RedisProtocolSupport.GeoradiusWithhash withhash,
                                         @Nullable final Long count,
                                         @Nullable final RedisProtocolSupport.GeoradiusOrder order,
                                         @Nullable @RedisProtocolSupport.Key final Buffer storeKey,
                                         @Nullable @RedisProtocolSupport.Key final Buffer storedistKey) {
        return blockingToSingle(() -> reservedCnx.georadius(key, longitude, latitude, radius, unit, withcoord, withdist,
                    withhash, count, order, storeKey, storedistKey).blockingGet());
    }

    @Override
    public <T> Single<List<T>> georadiusbymember(@RedisProtocolSupport.Key final Buffer key, final Buffer member,
                                                 final double radius,
                                                 final RedisProtocolSupport.GeoradiusbymemberUnit unit) {
        return blockingToSingle(() -> reservedCnx.georadiusbymember(key, member, radius, unit).blockingGet());
    }

    @Override
    public <T> Single<List<T>> georadiusbymember(@RedisProtocolSupport.Key final Buffer key, final Buffer member,
                                                 final double radius,
                                                 final RedisProtocolSupport.GeoradiusbymemberUnit unit,
                                                 @Nullable final RedisProtocolSupport.GeoradiusbymemberWithcoord withcoord,
                                                 @Nullable final RedisProtocolSupport.GeoradiusbymemberWithdist withdist,
                                                 @Nullable final RedisProtocolSupport.GeoradiusbymemberWithhash withhash,
                                                 @Nullable final Long count,
                                                 @Nullable final RedisProtocolSupport.GeoradiusbymemberOrder order,
                                                 @Nullable @RedisProtocolSupport.Key final Buffer storeKey,
                                                 @Nullable @RedisProtocolSupport.Key final Buffer storedistKey) {
        return blockingToSingle(() -> reservedCnx.georadiusbymember(key, member, radius, unit, withcoord, withdist,
                    withhash, count, order, storeKey, storedistKey).blockingGet());
    }

    @Override
    public Single<Buffer> get(@RedisProtocolSupport.Key final Buffer key) {
        return blockingToSingle(() -> reservedCnx.get(key).blockingGet());
    }

    @Override
    public Single<Long> getbit(@RedisProtocolSupport.Key final Buffer key, final long offset) {
        return blockingToSingle(() -> reservedCnx.getbit(key, offset).blockingGet());
    }

    @Override
    public Single<Buffer> getrange(@RedisProtocolSupport.Key final Buffer key, final long start, final long end) {
        return blockingToSingle(() -> reservedCnx.getrange(key, start, end).blockingGet());
    }

    @Override
    public Single<Buffer> getset(@RedisProtocolSupport.Key final Buffer key, final Buffer value) {
        return blockingToSingle(() -> reservedCnx.getset(key, value).blockingGet());
    }

    @Override
    public Single<Long> hdel(@RedisProtocolSupport.Key final Buffer key, final Buffer field) {
        return blockingToSingle(() -> reservedCnx.hdel(key, field).blockingGet());
    }

    @Override
    public Single<Long> hdel(@RedisProtocolSupport.Key final Buffer key, final Buffer field1, final Buffer field2) {
        return blockingToSingle(() -> reservedCnx.hdel(key, field1, field2).blockingGet());
    }

    @Override
    public Single<Long> hdel(@RedisProtocolSupport.Key final Buffer key, final Buffer field1, final Buffer field2,
                             final Buffer field3) {
        return blockingToSingle(() -> reservedCnx.hdel(key, field1, field2, field3).blockingGet());
    }

    @Override
    public Single<Long> hdel(@RedisProtocolSupport.Key final Buffer key, final Collection<Buffer> fields) {
        return blockingToSingle(() -> reservedCnx.hdel(key, fields).blockingGet());
    }

    @Override
    public Single<Long> hexists(@RedisProtocolSupport.Key final Buffer key, final Buffer field) {
        return blockingToSingle(() -> reservedCnx.hexists(key, field).blockingGet());
    }

    @Override
    public Single<Buffer> hget(@RedisProtocolSupport.Key final Buffer key, final Buffer field) {
        return blockingToSingle(() -> reservedCnx.hget(key, field).blockingGet());
    }

    @Override
    public <T> Single<List<T>> hgetall(@RedisProtocolSupport.Key final Buffer key) {
        return blockingToSingle(() -> reservedCnx.hgetall(key).blockingGet());
    }

    @Override
    public Single<Long> hincrby(@RedisProtocolSupport.Key final Buffer key, final Buffer field, final long increment) {
        return blockingToSingle(() -> reservedCnx.hincrby(key, field, increment).blockingGet());
    }

    @Override
    public Single<Double> hincrbyfloat(@RedisProtocolSupport.Key final Buffer key, final Buffer field,
                                       final double increment) {
        return blockingToSingle(() -> reservedCnx.hincrbyfloat(key, field, increment).blockingGet());
    }

    @Override
    public <T> Single<List<T>> hkeys(@RedisProtocolSupport.Key final Buffer key) {
        return blockingToSingle(() -> reservedCnx.hkeys(key).blockingGet());
    }

    @Override
    public Single<Long> hlen(@RedisProtocolSupport.Key final Buffer key) {
        return blockingToSingle(() -> reservedCnx.hlen(key).blockingGet());
    }

    @Override
    public <T> Single<List<T>> hmget(@RedisProtocolSupport.Key final Buffer key, final Buffer field) {
        return blockingToSingle(() -> reservedCnx.hmget(key, field).blockingGet());
    }

    @Override
    public <T> Single<List<T>> hmget(@RedisProtocolSupport.Key final Buffer key, final Buffer field1,
                                     final Buffer field2) {
        return blockingToSingle(() -> reservedCnx.hmget(key, field1, field2).blockingGet());
    }

    @Override
    public <T> Single<List<T>> hmget(@RedisProtocolSupport.Key final Buffer key, final Buffer field1,
                                     final Buffer field2, final Buffer field3) {
        return blockingToSingle(() -> reservedCnx.hmget(key, field1, field2, field3).blockingGet());
    }

    @Override
    public <T> Single<List<T>> hmget(@RedisProtocolSupport.Key final Buffer key, final Collection<Buffer> fields) {
        return blockingToSingle(() -> reservedCnx.hmget(key, fields).blockingGet());
    }

    @Override
    public Single<String> hmset(@RedisProtocolSupport.Key final Buffer key, final Buffer field, final Buffer value) {
        return blockingToSingle(() -> reservedCnx.hmset(key, field, value).blockingGet());
    }

    @Override
    public Single<String> hmset(@RedisProtocolSupport.Key final Buffer key, final Buffer field1, final Buffer value1,
                                final Buffer field2, final Buffer value2) {
        return blockingToSingle(() -> reservedCnx.hmset(key, field1, value1, field2, value2).blockingGet());
    }

    @Override
    public Single<String> hmset(@RedisProtocolSupport.Key final Buffer key, final Buffer field1, final Buffer value1,
                                final Buffer field2, final Buffer value2, final Buffer field3, final Buffer value3) {
        return blockingToSingle(
                    () -> reservedCnx.hmset(key, field1, value1, field2, value2, field3, value3).blockingGet());
    }

    @Override
    public Single<String> hmset(@RedisProtocolSupport.Key final Buffer key,
                                final Collection<RedisProtocolSupport.BufferFieldValue> fieldValues) {
        return blockingToSingle(() -> reservedCnx.hmset(key, fieldValues).blockingGet());
    }

    @Override
    public <T> Single<List<T>> hscan(@RedisProtocolSupport.Key final Buffer key, final long cursor) {
        return blockingToSingle(() -> reservedCnx.hscan(key, cursor).blockingGet());
    }

    @Override
    public <T> Single<List<T>> hscan(@RedisProtocolSupport.Key final Buffer key, final long cursor,
                                     @Nullable final Buffer matchPattern, @Nullable final Long count) {
        return blockingToSingle(() -> reservedCnx.hscan(key, cursor, matchPattern, count).blockingGet());
    }

    @Override
    public Single<Long> hset(@RedisProtocolSupport.Key final Buffer key, final Buffer field, final Buffer value) {
        return blockingToSingle(() -> reservedCnx.hset(key, field, value).blockingGet());
    }

    @Override
    public Single<Long> hsetnx(@RedisProtocolSupport.Key final Buffer key, final Buffer field, final Buffer value) {
        return blockingToSingle(() -> reservedCnx.hsetnx(key, field, value).blockingGet());
    }

    @Override
    public Single<Long> hstrlen(@RedisProtocolSupport.Key final Buffer key, final Buffer field) {
        return blockingToSingle(() -> reservedCnx.hstrlen(key, field).blockingGet());
    }

    @Override
    public <T> Single<List<T>> hvals(@RedisProtocolSupport.Key final Buffer key) {
        return blockingToSingle(() -> reservedCnx.hvals(key).blockingGet());
    }

    @Override
    public Single<Long> incr(@RedisProtocolSupport.Key final Buffer key) {
        return blockingToSingle(() -> reservedCnx.incr(key).blockingGet());
    }

    @Override
    public Single<Long> incrby(@RedisProtocolSupport.Key final Buffer key, final long increment) {
        return blockingToSingle(() -> reservedCnx.incrby(key, increment).blockingGet());
    }

    @Override
    public Single<Double> incrbyfloat(@RedisProtocolSupport.Key final Buffer key, final double increment) {
        return blockingToSingle(() -> reservedCnx.incrbyfloat(key, increment).blockingGet());
    }

    @Override
    public Single<Buffer> info() {
        return blockingToSingle(() -> reservedCnx.info().blockingGet());
    }

    @Override
    public Single<Buffer> info(@Nullable final Buffer section) {
        return blockingToSingle(() -> reservedCnx.info(section).blockingGet());
    }

    @Override
    public <T> Single<List<T>> keys(final Buffer pattern) {
        return blockingToSingle(() -> reservedCnx.keys(pattern).blockingGet());
    }

    @Override
    public Single<Long> lastsave() {
        return blockingToSingle(() -> reservedCnx.lastsave().blockingGet());
    }

    @Override
    public Single<Buffer> lindex(@RedisProtocolSupport.Key final Buffer key, final long index) {
        return blockingToSingle(() -> reservedCnx.lindex(key, index).blockingGet());
    }

    @Override
    public Single<Long> linsert(@RedisProtocolSupport.Key final Buffer key,
                                final RedisProtocolSupport.LinsertWhere where, final Buffer pivot, final Buffer value) {
        return blockingToSingle(() -> reservedCnx.linsert(key, where, pivot, value).blockingGet());
    }

    @Override
    public Single<Long> llen(@RedisProtocolSupport.Key final Buffer key) {
        return blockingToSingle(() -> reservedCnx.llen(key).blockingGet());
    }

    @Override
    public Single<Buffer> lpop(@RedisProtocolSupport.Key final Buffer key) {
        return blockingToSingle(() -> reservedCnx.lpop(key).blockingGet());
    }

    @Override
    public Single<Long> lpush(@RedisProtocolSupport.Key final Buffer key, final Buffer value) {
        return blockingToSingle(() -> reservedCnx.lpush(key, value).blockingGet());
    }

    @Override
    public Single<Long> lpush(@RedisProtocolSupport.Key final Buffer key, final Buffer value1, final Buffer value2) {
        return blockingToSingle(() -> reservedCnx.lpush(key, value1, value2).blockingGet());
    }

    @Override
    public Single<Long> lpush(@RedisProtocolSupport.Key final Buffer key, final Buffer value1, final Buffer value2,
                              final Buffer value3) {
        return blockingToSingle(() -> reservedCnx.lpush(key, value1, value2, value3).blockingGet());
    }

    @Override
    public Single<Long> lpush(@RedisProtocolSupport.Key final Buffer key, final Collection<Buffer> values) {
        return blockingToSingle(() -> reservedCnx.lpush(key, values).blockingGet());
    }

    @Override
    public Single<Long> lpushx(@RedisProtocolSupport.Key final Buffer key, final Buffer value) {
        return blockingToSingle(() -> reservedCnx.lpushx(key, value).blockingGet());
    }

    @Override
    public <T> Single<List<T>> lrange(@RedisProtocolSupport.Key final Buffer key, final long start, final long stop) {
        return blockingToSingle(() -> reservedCnx.lrange(key, start, stop).blockingGet());
    }

    @Override
    public Single<Long> lrem(@RedisProtocolSupport.Key final Buffer key, final long count, final Buffer value) {
        return blockingToSingle(() -> reservedCnx.lrem(key, count, value).blockingGet());
    }

    @Override
    public Single<String> lset(@RedisProtocolSupport.Key final Buffer key, final long index, final Buffer value) {
        return blockingToSingle(() -> reservedCnx.lset(key, index, value).blockingGet());
    }

    @Override
    public Single<String> ltrim(@RedisProtocolSupport.Key final Buffer key, final long start, final long stop) {
        return blockingToSingle(() -> reservedCnx.ltrim(key, start, stop).blockingGet());
    }

    @Override
    public Single<Buffer> memoryDoctor() {
        return blockingToSingle(() -> reservedCnx.memoryDoctor().blockingGet());
    }

    @Override
    public <T> Single<List<T>> memoryHelp() {
        return blockingToSingle(() -> reservedCnx.memoryHelp().blockingGet());
    }

    @Override
    public Single<Buffer> memoryMallocStats() {
        return blockingToSingle(() -> reservedCnx.memoryMallocStats().blockingGet());
    }

    @Override
    public Single<String> memoryPurge() {
        return blockingToSingle(() -> reservedCnx.memoryPurge().blockingGet());
    }

    @Override
    public <T> Single<List<T>> memoryStats() {
        return blockingToSingle(() -> reservedCnx.memoryStats().blockingGet());
    }

    @Override
    public Single<Long> memoryUsage(@RedisProtocolSupport.Key final Buffer key) {
        return blockingToSingle(() -> reservedCnx.memoryUsage(key).blockingGet());
    }

    @Override
    public Single<Long> memoryUsage(@RedisProtocolSupport.Key final Buffer key, @Nullable final Long samplesCount) {
        return blockingToSingle(() -> reservedCnx.memoryUsage(key, samplesCount).blockingGet());
    }

    @Override
    public <T> Single<List<T>> mget(@RedisProtocolSupport.Key final Buffer key) {
        return blockingToSingle(() -> reservedCnx.mget(key).blockingGet());
    }

    @Override
    public <T> Single<List<T>> mget(@RedisProtocolSupport.Key final Buffer key1,
                                    @RedisProtocolSupport.Key final Buffer key2) {
        return blockingToSingle(() -> reservedCnx.mget(key1, key2).blockingGet());
    }

    @Override
    public <T> Single<List<T>> mget(@RedisProtocolSupport.Key final Buffer key1,
                                    @RedisProtocolSupport.Key final Buffer key2,
                                    @RedisProtocolSupport.Key final Buffer key3) {
        return blockingToSingle(() -> reservedCnx.mget(key1, key2, key3).blockingGet());
    }

    @Override
    public <T> Single<List<T>> mget(@RedisProtocolSupport.Key final Collection<Buffer> keys) {
        return blockingToSingle(() -> reservedCnx.mget(keys).blockingGet());
    }

    @Override
    public Single<Long> move(@RedisProtocolSupport.Key final Buffer key, final long db) {
        return blockingToSingle(() -> reservedCnx.move(key, db).blockingGet());
    }

    @Override
    public Single<String> mset(@RedisProtocolSupport.Key final Buffer key, final Buffer value) {
        return blockingToSingle(() -> reservedCnx.mset(key, value).blockingGet());
    }

    @Override
    public Single<String> mset(@RedisProtocolSupport.Key final Buffer key1, final Buffer value1,
                               @RedisProtocolSupport.Key final Buffer key2, final Buffer value2) {
        return blockingToSingle(() -> reservedCnx.mset(key1, value1, key2, value2).blockingGet());
    }

    @Override
    public Single<String> mset(@RedisProtocolSupport.Key final Buffer key1, final Buffer value1,
                               @RedisProtocolSupport.Key final Buffer key2, final Buffer value2,
                               @RedisProtocolSupport.Key final Buffer key3, final Buffer value3) {
        return blockingToSingle(() -> reservedCnx.mset(key1, value1, key2, value2, key3, value3).blockingGet());
    }

    @Override
    public Single<String> mset(final Collection<RedisProtocolSupport.BufferKeyValue> keyValues) {
        return blockingToSingle(() -> reservedCnx.mset(keyValues).blockingGet());
    }

    @Override
    public Single<Long> msetnx(@RedisProtocolSupport.Key final Buffer key, final Buffer value) {
        return blockingToSingle(() -> reservedCnx.msetnx(key, value).blockingGet());
    }

    @Override
    public Single<Long> msetnx(@RedisProtocolSupport.Key final Buffer key1, final Buffer value1,
                               @RedisProtocolSupport.Key final Buffer key2, final Buffer value2) {
        return blockingToSingle(() -> reservedCnx.msetnx(key1, value1, key2, value2).blockingGet());
    }

    @Override
    public Single<Long> msetnx(@RedisProtocolSupport.Key final Buffer key1, final Buffer value1,
                               @RedisProtocolSupport.Key final Buffer key2, final Buffer value2,
                               @RedisProtocolSupport.Key final Buffer key3, final Buffer value3) {
        return blockingToSingle(() -> reservedCnx.msetnx(key1, value1, key2, value2, key3, value3).blockingGet());
    }

    @Override
    public Single<Long> msetnx(final Collection<RedisProtocolSupport.BufferKeyValue> keyValues) {
        return blockingToSingle(() -> reservedCnx.msetnx(keyValues).blockingGet());
    }

    @Override
    public Single<Buffer> objectEncoding(@RedisProtocolSupport.Key final Buffer key) {
        return blockingToSingle(() -> reservedCnx.objectEncoding(key).blockingGet());
    }

    @Override
    public Single<Long> objectFreq(@RedisProtocolSupport.Key final Buffer key) {
        return blockingToSingle(() -> reservedCnx.objectFreq(key).blockingGet());
    }

    @Override
    public Single<List<String>> objectHelp() {
        return blockingToSingle(() -> reservedCnx.objectHelp().blockingGet());
    }

    @Override
    public Single<Long> objectIdletime(@RedisProtocolSupport.Key final Buffer key) {
        return blockingToSingle(() -> reservedCnx.objectIdletime(key).blockingGet());
    }

    @Override
    public Single<Long> objectRefcount(@RedisProtocolSupport.Key final Buffer key) {
        return blockingToSingle(() -> reservedCnx.objectRefcount(key).blockingGet());
    }

    @Override
    public Single<Long> persist(@RedisProtocolSupport.Key final Buffer key) {
        return blockingToSingle(() -> reservedCnx.persist(key).blockingGet());
    }

    @Override
    public Single<Long> pexpire(@RedisProtocolSupport.Key final Buffer key, final long milliseconds) {
        return blockingToSingle(() -> reservedCnx.pexpire(key, milliseconds).blockingGet());
    }

    @Override
    public Single<Long> pexpireat(@RedisProtocolSupport.Key final Buffer key, final long millisecondsTimestamp) {
        return blockingToSingle(() -> reservedCnx.pexpireat(key, millisecondsTimestamp).blockingGet());
    }

    @Override
    public Single<Long> pfadd(@RedisProtocolSupport.Key final Buffer key, final Buffer element) {
        return blockingToSingle(() -> reservedCnx.pfadd(key, element).blockingGet());
    }

    @Override
    public Single<Long> pfadd(@RedisProtocolSupport.Key final Buffer key, final Buffer element1,
                              final Buffer element2) {
        return blockingToSingle(() -> reservedCnx.pfadd(key, element1, element2).blockingGet());
    }

    @Override
    public Single<Long> pfadd(@RedisProtocolSupport.Key final Buffer key, final Buffer element1, final Buffer element2,
                              final Buffer element3) {
        return blockingToSingle(() -> reservedCnx.pfadd(key, element1, element2, element3).blockingGet());
    }

    @Override
    public Single<Long> pfadd(@RedisProtocolSupport.Key final Buffer key, final Collection<Buffer> elements) {
        return blockingToSingle(() -> reservedCnx.pfadd(key, elements).blockingGet());
    }

    @Override
    public Single<Long> pfcount(@RedisProtocolSupport.Key final Buffer key) {
        return blockingToSingle(() -> reservedCnx.pfcount(key).blockingGet());
    }

    @Override
    public Single<Long> pfcount(@RedisProtocolSupport.Key final Buffer key1,
                                @RedisProtocolSupport.Key final Buffer key2) {
        return blockingToSingle(() -> reservedCnx.pfcount(key1, key2).blockingGet());
    }

    @Override
    public Single<Long> pfcount(@RedisProtocolSupport.Key final Buffer key1,
                                @RedisProtocolSupport.Key final Buffer key2,
                                @RedisProtocolSupport.Key final Buffer key3) {
        return blockingToSingle(() -> reservedCnx.pfcount(key1, key2, key3).blockingGet());
    }

    @Override
    public Single<Long> pfcount(@RedisProtocolSupport.Key final Collection<Buffer> keys) {
        return blockingToSingle(() -> reservedCnx.pfcount(keys).blockingGet());
    }

    @Override
    public Single<String> pfmerge(@RedisProtocolSupport.Key final Buffer destkey,
                                  @RedisProtocolSupport.Key final Buffer sourcekey) {
        return blockingToSingle(() -> reservedCnx.pfmerge(destkey, sourcekey).blockingGet());
    }

    @Override
    public Single<String> pfmerge(@RedisProtocolSupport.Key final Buffer destkey,
                                  @RedisProtocolSupport.Key final Buffer sourcekey1,
                                  @RedisProtocolSupport.Key final Buffer sourcekey2) {
        return blockingToSingle(() -> reservedCnx.pfmerge(destkey, sourcekey1, sourcekey2).blockingGet());
    }

    @Override
    public Single<String> pfmerge(@RedisProtocolSupport.Key final Buffer destkey,
                                  @RedisProtocolSupport.Key final Buffer sourcekey1,
                                  @RedisProtocolSupport.Key final Buffer sourcekey2,
                                  @RedisProtocolSupport.Key final Buffer sourcekey3) {
        return blockingToSingle(() -> reservedCnx.pfmerge(destkey, sourcekey1, sourcekey2, sourcekey3).blockingGet());
    }

    @Override
    public Single<String> pfmerge(@RedisProtocolSupport.Key final Buffer destkey,
                                  @RedisProtocolSupport.Key final Collection<Buffer> sourcekeys) {
        return blockingToSingle(() -> reservedCnx.pfmerge(destkey, sourcekeys).blockingGet());
    }

    @Override
    public Single<String> ping() {
        return blockingToSingle(() -> reservedCnx.ping().blockingGet());
    }

    @Override
    public Single<Buffer> ping(final Buffer message) {
        return blockingToSingle(() -> reservedCnx.ping(message).blockingGet());
    }

    @Override
    public Single<String> psetex(@RedisProtocolSupport.Key final Buffer key, final long milliseconds,
                                 final Buffer value) {
        return blockingToSingle(() -> reservedCnx.psetex(key, milliseconds, value).blockingGet());
    }

    @Override
    public Single<Long> pttl(@RedisProtocolSupport.Key final Buffer key) {
        return blockingToSingle(() -> reservedCnx.pttl(key).blockingGet());
    }

    @Override
    public Single<Long> publish(final Buffer channel, final Buffer message) {
        return blockingToSingle(() -> reservedCnx.publish(channel, message).blockingGet());
    }

    @Override
    public Single<List<String>> pubsubChannels() {
        return blockingToSingle(() -> reservedCnx.pubsubChannels().blockingGet());
    }

    @Override
    public Single<List<String>> pubsubChannels(@Nullable final Buffer pattern) {
        return blockingToSingle(() -> reservedCnx.pubsubChannels(pattern).blockingGet());
    }

    @Override
    public Single<List<String>> pubsubChannels(@Nullable final Buffer pattern1, @Nullable final Buffer pattern2) {
        return blockingToSingle(() -> reservedCnx.pubsubChannels(pattern1, pattern2).blockingGet());
    }

    @Override
    public Single<List<String>> pubsubChannels(@Nullable final Buffer pattern1, @Nullable final Buffer pattern2,
                                               @Nullable final Buffer pattern3) {
        return blockingToSingle(() -> reservedCnx.pubsubChannels(pattern1, pattern2, pattern3).blockingGet());
    }

    @Override
    public Single<List<String>> pubsubChannels(final Collection<Buffer> patterns) {
        return blockingToSingle(() -> reservedCnx.pubsubChannels(patterns).blockingGet());
    }

    @Override
    public <T> Single<List<T>> pubsubNumsub() {
        return blockingToSingle(() -> reservedCnx.pubsubNumsub().blockingGet());
    }

    @Override
    public <T> Single<List<T>> pubsubNumsub(@Nullable final Buffer channel) {
        return blockingToSingle(() -> reservedCnx.pubsubNumsub(channel).blockingGet());
    }

    @Override
    public <T> Single<List<T>> pubsubNumsub(@Nullable final Buffer channel1, @Nullable final Buffer channel2) {
        return blockingToSingle(() -> reservedCnx.pubsubNumsub(channel1, channel2).blockingGet());
    }

    @Override
    public <T> Single<List<T>> pubsubNumsub(@Nullable final Buffer channel1, @Nullable final Buffer channel2,
                                            @Nullable final Buffer channel3) {
        return blockingToSingle(() -> reservedCnx.pubsubNumsub(channel1, channel2, channel3).blockingGet());
    }

    @Override
    public <T> Single<List<T>> pubsubNumsub(final Collection<Buffer> channels) {
        return blockingToSingle(() -> reservedCnx.pubsubNumsub(channels).blockingGet());
    }

    @Override
    public Single<Long> pubsubNumpat() {
        return blockingToSingle(() -> reservedCnx.pubsubNumpat().blockingGet());
    }

    @Override
    public Single<Buffer> randomkey() {
        return blockingToSingle(() -> reservedCnx.randomkey().blockingGet());
    }

    @Override
    public Single<String> readonly() {
        return blockingToSingle(() -> reservedCnx.readonly().blockingGet());
    }

    @Override
    public Single<String> readwrite() {
        return blockingToSingle(() -> reservedCnx.readwrite().blockingGet());
    }

    @Override
    public Single<String> rename(@RedisProtocolSupport.Key final Buffer key,
                                 @RedisProtocolSupport.Key final Buffer newkey) {
        return blockingToSingle(() -> reservedCnx.rename(key, newkey).blockingGet());
    }

    @Override
    public Single<Long> renamenx(@RedisProtocolSupport.Key final Buffer key,
                                 @RedisProtocolSupport.Key final Buffer newkey) {
        return blockingToSingle(() -> reservedCnx.renamenx(key, newkey).blockingGet());
    }

    @Override
    public Single<String> restore(@RedisProtocolSupport.Key final Buffer key, final long ttl,
                                  final Buffer serializedValue) {
        return blockingToSingle(() -> reservedCnx.restore(key, ttl, serializedValue).blockingGet());
    }

    @Override
    public Single<String> restore(@RedisProtocolSupport.Key final Buffer key, final long ttl,
                                  final Buffer serializedValue,
                                  @Nullable final RedisProtocolSupport.RestoreReplace replace) {
        return blockingToSingle(() -> reservedCnx.restore(key, ttl, serializedValue, replace).blockingGet());
    }

    @Override
    public <T> Single<List<T>> role() {
        return blockingToSingle(() -> reservedCnx.role().blockingGet());
    }

    @Override
    public Single<Buffer> rpop(@RedisProtocolSupport.Key final Buffer key) {
        return blockingToSingle(() -> reservedCnx.rpop(key).blockingGet());
    }

    @Override
    public Single<Buffer> rpoplpush(@RedisProtocolSupport.Key final Buffer source,
                                    @RedisProtocolSupport.Key final Buffer destination) {
        return blockingToSingle(() -> reservedCnx.rpoplpush(source, destination).blockingGet());
    }

    @Override
    public Single<Long> rpush(@RedisProtocolSupport.Key final Buffer key, final Buffer value) {
        return blockingToSingle(() -> reservedCnx.rpush(key, value).blockingGet());
    }

    @Override
    public Single<Long> rpush(@RedisProtocolSupport.Key final Buffer key, final Buffer value1, final Buffer value2) {
        return blockingToSingle(() -> reservedCnx.rpush(key, value1, value2).blockingGet());
    }

    @Override
    public Single<Long> rpush(@RedisProtocolSupport.Key final Buffer key, final Buffer value1, final Buffer value2,
                              final Buffer value3) {
        return blockingToSingle(() -> reservedCnx.rpush(key, value1, value2, value3).blockingGet());
    }

    @Override
    public Single<Long> rpush(@RedisProtocolSupport.Key final Buffer key, final Collection<Buffer> values) {
        return blockingToSingle(() -> reservedCnx.rpush(key, values).blockingGet());
    }

    @Override
    public Single<Long> rpushx(@RedisProtocolSupport.Key final Buffer key, final Buffer value) {
        return blockingToSingle(() -> reservedCnx.rpushx(key, value).blockingGet());
    }

    @Override
    public Single<Long> sadd(@RedisProtocolSupport.Key final Buffer key, final Buffer member) {
        return blockingToSingle(() -> reservedCnx.sadd(key, member).blockingGet());
    }

    @Override
    public Single<Long> sadd(@RedisProtocolSupport.Key final Buffer key, final Buffer member1, final Buffer member2) {
        return blockingToSingle(() -> reservedCnx.sadd(key, member1, member2).blockingGet());
    }

    @Override
    public Single<Long> sadd(@RedisProtocolSupport.Key final Buffer key, final Buffer member1, final Buffer member2,
                             final Buffer member3) {
        return blockingToSingle(() -> reservedCnx.sadd(key, member1, member2, member3).blockingGet());
    }

    @Override
    public Single<Long> sadd(@RedisProtocolSupport.Key final Buffer key, final Collection<Buffer> members) {
        return blockingToSingle(() -> reservedCnx.sadd(key, members).blockingGet());
    }

    @Override
    public Single<String> save() {
        return blockingToSingle(() -> reservedCnx.save().blockingGet());
    }

    @Override
    public <T> Single<List<T>> scan(final long cursor) {
        return blockingToSingle(() -> reservedCnx.scan(cursor).blockingGet());
    }

    @Override
    public <T> Single<List<T>> scan(final long cursor, @Nullable final Buffer matchPattern,
                                    @Nullable final Long count) {
        return blockingToSingle(() -> reservedCnx.scan(cursor, matchPattern, count).blockingGet());
    }

    @Override
    public Single<Long> scard(@RedisProtocolSupport.Key final Buffer key) {
        return blockingToSingle(() -> reservedCnx.scard(key).blockingGet());
    }

    @Override
    public Single<String> scriptDebug(final RedisProtocolSupport.ScriptDebugMode mode) {
        return blockingToSingle(() -> reservedCnx.scriptDebug(mode).blockingGet());
    }

    @Override
    public <T> Single<List<T>> scriptExists(final Buffer sha1) {
        return blockingToSingle(() -> reservedCnx.scriptExists(sha1).blockingGet());
    }

    @Override
    public <T> Single<List<T>> scriptExists(final Buffer sha11, final Buffer sha12) {
        return blockingToSingle(() -> reservedCnx.scriptExists(sha11, sha12).blockingGet());
    }

    @Override
    public <T> Single<List<T>> scriptExists(final Buffer sha11, final Buffer sha12, final Buffer sha13) {
        return blockingToSingle(() -> reservedCnx.scriptExists(sha11, sha12, sha13).blockingGet());
    }

    @Override
    public <T> Single<List<T>> scriptExists(final Collection<Buffer> sha1s) {
        return blockingToSingle(() -> reservedCnx.scriptExists(sha1s).blockingGet());
    }

    @Override
    public Single<String> scriptFlush() {
        return blockingToSingle(() -> reservedCnx.scriptFlush().blockingGet());
    }

    @Override
    public Single<String> scriptKill() {
        return blockingToSingle(() -> reservedCnx.scriptKill().blockingGet());
    }

    @Override
    public Single<Buffer> scriptLoad(final Buffer script) {
        return blockingToSingle(() -> reservedCnx.scriptLoad(script).blockingGet());
    }

    @Override
    public <T> Single<List<T>> sdiff(@RedisProtocolSupport.Key final Buffer firstkey) {
        return blockingToSingle(() -> reservedCnx.sdiff(firstkey).blockingGet());
    }

    @Override
    public <T> Single<List<T>> sdiff(@RedisProtocolSupport.Key final Buffer firstkey,
                                     @Nullable @RedisProtocolSupport.Key final Buffer otherkey) {
        return blockingToSingle(() -> reservedCnx.sdiff(firstkey, otherkey).blockingGet());
    }

    @Override
    public <T> Single<List<T>> sdiff(@RedisProtocolSupport.Key final Buffer firstkey,
                                     @Nullable @RedisProtocolSupport.Key final Buffer otherkey1,
                                     @Nullable @RedisProtocolSupport.Key final Buffer otherkey2) {
        return blockingToSingle(() -> reservedCnx.sdiff(firstkey, otherkey1, otherkey2).blockingGet());
    }

    @Override
    public <T> Single<List<T>> sdiff(@RedisProtocolSupport.Key final Buffer firstkey,
                                     @Nullable @RedisProtocolSupport.Key final Buffer otherkey1,
                                     @Nullable @RedisProtocolSupport.Key final Buffer otherkey2,
                                     @Nullable @RedisProtocolSupport.Key final Buffer otherkey3) {
        return blockingToSingle(() -> reservedCnx.sdiff(firstkey, otherkey1, otherkey2, otherkey3).blockingGet());
    }

    @Override
    public <T> Single<List<T>> sdiff(@RedisProtocolSupport.Key final Buffer firstkey,
                                     @RedisProtocolSupport.Key final Collection<Buffer> otherkeys) {
        return blockingToSingle(() -> reservedCnx.sdiff(firstkey, otherkeys).blockingGet());
    }

    @Override
    public Single<Long> sdiffstore(@RedisProtocolSupport.Key final Buffer destination,
                                   @RedisProtocolSupport.Key final Buffer firstkey) {
        return blockingToSingle(() -> reservedCnx.sdiffstore(destination, firstkey).blockingGet());
    }

    @Override
    public Single<Long> sdiffstore(@RedisProtocolSupport.Key final Buffer destination,
                                   @RedisProtocolSupport.Key final Buffer firstkey,
                                   @Nullable @RedisProtocolSupport.Key final Buffer otherkey) {
        return blockingToSingle(() -> reservedCnx.sdiffstore(destination, firstkey, otherkey).blockingGet());
    }

    @Override
    public Single<Long> sdiffstore(@RedisProtocolSupport.Key final Buffer destination,
                                   @RedisProtocolSupport.Key final Buffer firstkey,
                                   @Nullable @RedisProtocolSupport.Key final Buffer otherkey1,
                                   @Nullable @RedisProtocolSupport.Key final Buffer otherkey2) {
        return blockingToSingle(
                    () -> reservedCnx.sdiffstore(destination, firstkey, otherkey1, otherkey2).blockingGet());
    }

    @Override
    public Single<Long> sdiffstore(@RedisProtocolSupport.Key final Buffer destination,
                                   @RedisProtocolSupport.Key final Buffer firstkey,
                                   @Nullable @RedisProtocolSupport.Key final Buffer otherkey1,
                                   @Nullable @RedisProtocolSupport.Key final Buffer otherkey2,
                                   @Nullable @RedisProtocolSupport.Key final Buffer otherkey3) {
        return blockingToSingle(
                    () -> reservedCnx.sdiffstore(destination, firstkey, otherkey1, otherkey2, otherkey3).blockingGet());
    }

    @Override
    public Single<Long> sdiffstore(@RedisProtocolSupport.Key final Buffer destination,
                                   @RedisProtocolSupport.Key final Buffer firstkey,
                                   @RedisProtocolSupport.Key final Collection<Buffer> otherkeys) {
        return blockingToSingle(() -> reservedCnx.sdiffstore(destination, firstkey, otherkeys).blockingGet());
    }

    @Override
    public Single<String> select(final long index) {
        return blockingToSingle(() -> reservedCnx.select(index).blockingGet());
    }

    @Override
    public Single<String> set(@RedisProtocolSupport.Key final Buffer key, final Buffer value) {
        return blockingToSingle(() -> reservedCnx.set(key, value).blockingGet());
    }

    @Override
    public Single<String> set(@RedisProtocolSupport.Key final Buffer key, final Buffer value,
                              @Nullable final RedisProtocolSupport.ExpireDuration expireDuration,
                              @Nullable final RedisProtocolSupport.SetCondition condition) {
        return blockingToSingle(() -> reservedCnx.set(key, value, expireDuration, condition).blockingGet());
    }

    @Override
    public Single<Long> setbit(@RedisProtocolSupport.Key final Buffer key, final long offset, final Buffer value) {
        return blockingToSingle(() -> reservedCnx.setbit(key, offset, value).blockingGet());
    }

    @Override
    public Single<String> setex(@RedisProtocolSupport.Key final Buffer key, final long seconds, final Buffer value) {
        return blockingToSingle(() -> reservedCnx.setex(key, seconds, value).blockingGet());
    }

    @Override
    public Single<Long> setnx(@RedisProtocolSupport.Key final Buffer key, final Buffer value) {
        return blockingToSingle(() -> reservedCnx.setnx(key, value).blockingGet());
    }

    @Override
    public Single<Long> setrange(@RedisProtocolSupport.Key final Buffer key, final long offset, final Buffer value) {
        return blockingToSingle(() -> reservedCnx.setrange(key, offset, value).blockingGet());
    }

    @Override
    public Single<String> shutdown() {
        return blockingToSingle(() -> reservedCnx.shutdown().blockingGet());
    }

    @Override
    public Single<String> shutdown(@Nullable final RedisProtocolSupport.ShutdownSaveMode saveMode) {
        return blockingToSingle(() -> reservedCnx.shutdown(saveMode).blockingGet());
    }

    @Override
    public <T> Single<List<T>> sinter(@RedisProtocolSupport.Key final Buffer key) {
        return blockingToSingle(() -> reservedCnx.sinter(key).blockingGet());
    }

    @Override
    public <T> Single<List<T>> sinter(@RedisProtocolSupport.Key final Buffer key1,
                                      @RedisProtocolSupport.Key final Buffer key2) {
        return blockingToSingle(() -> reservedCnx.sinter(key1, key2).blockingGet());
    }

    @Override
    public <T> Single<List<T>> sinter(@RedisProtocolSupport.Key final Buffer key1,
                                      @RedisProtocolSupport.Key final Buffer key2,
                                      @RedisProtocolSupport.Key final Buffer key3) {
        return blockingToSingle(() -> reservedCnx.sinter(key1, key2, key3).blockingGet());
    }

    @Override
    public <T> Single<List<T>> sinter(@RedisProtocolSupport.Key final Collection<Buffer> keys) {
        return blockingToSingle(() -> reservedCnx.sinter(keys).blockingGet());
    }

    @Override
    public Single<Long> sinterstore(@RedisProtocolSupport.Key final Buffer destination,
                                    @RedisProtocolSupport.Key final Buffer key) {
        return blockingToSingle(() -> reservedCnx.sinterstore(destination, key).blockingGet());
    }

    @Override
    public Single<Long> sinterstore(@RedisProtocolSupport.Key final Buffer destination,
                                    @RedisProtocolSupport.Key final Buffer key1,
                                    @RedisProtocolSupport.Key final Buffer key2) {
        return blockingToSingle(() -> reservedCnx.sinterstore(destination, key1, key2).blockingGet());
    }

    @Override
    public Single<Long> sinterstore(@RedisProtocolSupport.Key final Buffer destination,
                                    @RedisProtocolSupport.Key final Buffer key1,
                                    @RedisProtocolSupport.Key final Buffer key2,
                                    @RedisProtocolSupport.Key final Buffer key3) {
        return blockingToSingle(() -> reservedCnx.sinterstore(destination, key1, key2, key3).blockingGet());
    }

    @Override
    public Single<Long> sinterstore(@RedisProtocolSupport.Key final Buffer destination,
                                    @RedisProtocolSupport.Key final Collection<Buffer> keys) {
        return blockingToSingle(() -> reservedCnx.sinterstore(destination, keys).blockingGet());
    }

    @Override
    public Single<Long> sismember(@RedisProtocolSupport.Key final Buffer key, final Buffer member) {
        return blockingToSingle(() -> reservedCnx.sismember(key, member).blockingGet());
    }

    @Override
    public Single<String> slaveof(final Buffer host, final Buffer port) {
        return blockingToSingle(() -> reservedCnx.slaveof(host, port).blockingGet());
    }

    @Override
    public <T> Single<List<T>> slowlog(final Buffer subcommand) {
        return blockingToSingle(() -> reservedCnx.slowlog(subcommand).blockingGet());
    }

    @Override
    public <T> Single<List<T>> slowlog(final Buffer subcommand, @Nullable final Buffer argument) {
        return blockingToSingle(() -> reservedCnx.slowlog(subcommand, argument).blockingGet());
    }

    @Override
    public <T> Single<List<T>> smembers(@RedisProtocolSupport.Key final Buffer key) {
        return blockingToSingle(() -> reservedCnx.smembers(key).blockingGet());
    }

    @Override
    public Single<Long> smove(@RedisProtocolSupport.Key final Buffer source,
                              @RedisProtocolSupport.Key final Buffer destination, final Buffer member) {
        return blockingToSingle(() -> reservedCnx.smove(source, destination, member).blockingGet());
    }

    @Override
    public <T> Single<List<T>> sort(@RedisProtocolSupport.Key final Buffer key) {
        return blockingToSingle(() -> reservedCnx.sort(key).blockingGet());
    }

    @Override
    public <T> Single<List<T>> sort(@RedisProtocolSupport.Key final Buffer key, @Nullable final Buffer byPattern,
                                    @Nullable final RedisProtocolSupport.OffsetCount offsetCount,
                                    final Collection<Buffer> getPatterns,
                                    @Nullable final RedisProtocolSupport.SortOrder order,
                                    @Nullable final RedisProtocolSupport.SortSorting sorting) {
        return blockingToSingle(
                    () -> reservedCnx.sort(key, byPattern, offsetCount, getPatterns, order, sorting).blockingGet());
    }

    @Override
    public Single<Long> sort(@RedisProtocolSupport.Key final Buffer key,
                             @RedisProtocolSupport.Key final Buffer storeDestination) {
        return blockingToSingle(() -> reservedCnx.sort(key, storeDestination).blockingGet());
    }

    @Override
    public Single<Long> sort(@RedisProtocolSupport.Key final Buffer key,
                             @RedisProtocolSupport.Key final Buffer storeDestination, @Nullable final Buffer byPattern,
                             @Nullable final RedisProtocolSupport.OffsetCount offsetCount,
                             final Collection<Buffer> getPatterns, @Nullable final RedisProtocolSupport.SortOrder order,
                             @Nullable final RedisProtocolSupport.SortSorting sorting) {
        return blockingToSingle(() -> reservedCnx
                    .sort(key, storeDestination, byPattern, offsetCount, getPatterns, order, sorting).blockingGet());
    }

    @Override
    public Single<Buffer> spop(@RedisProtocolSupport.Key final Buffer key) {
        return blockingToSingle(() -> reservedCnx.spop(key).blockingGet());
    }

    @Override
    public Single<Buffer> spop(@RedisProtocolSupport.Key final Buffer key, @Nullable final Long count) {
        return blockingToSingle(() -> reservedCnx.spop(key, count).blockingGet());
    }

    @Override
    public Single<Buffer> srandmember(@RedisProtocolSupport.Key final Buffer key) {
        return blockingToSingle(() -> reservedCnx.srandmember(key).blockingGet());
    }

    @Override
    public Single<List<String>> srandmember(@RedisProtocolSupport.Key final Buffer key, final long count) {
        return blockingToSingle(() -> reservedCnx.srandmember(key, count).blockingGet());
    }

    @Override
    public Single<Long> srem(@RedisProtocolSupport.Key final Buffer key, final Buffer member) {
        return blockingToSingle(() -> reservedCnx.srem(key, member).blockingGet());
    }

    @Override
    public Single<Long> srem(@RedisProtocolSupport.Key final Buffer key, final Buffer member1, final Buffer member2) {
        return blockingToSingle(() -> reservedCnx.srem(key, member1, member2).blockingGet());
    }

    @Override
    public Single<Long> srem(@RedisProtocolSupport.Key final Buffer key, final Buffer member1, final Buffer member2,
                             final Buffer member3) {
        return blockingToSingle(() -> reservedCnx.srem(key, member1, member2, member3).blockingGet());
    }

    @Override
    public Single<Long> srem(@RedisProtocolSupport.Key final Buffer key, final Collection<Buffer> members) {
        return blockingToSingle(() -> reservedCnx.srem(key, members).blockingGet());
    }

    @Override
    public <T> Single<List<T>> sscan(@RedisProtocolSupport.Key final Buffer key, final long cursor) {
        return blockingToSingle(() -> reservedCnx.sscan(key, cursor).blockingGet());
    }

    @Override
    public <T> Single<List<T>> sscan(@RedisProtocolSupport.Key final Buffer key, final long cursor,
                                     @Nullable final Buffer matchPattern, @Nullable final Long count) {
        return blockingToSingle(() -> reservedCnx.sscan(key, cursor, matchPattern, count).blockingGet());
    }

    @Override
    public Single<Long> strlen(@RedisProtocolSupport.Key final Buffer key) {
        return blockingToSingle(() -> reservedCnx.strlen(key).blockingGet());
    }

    @Override
    public <T> Single<List<T>> sunion(@RedisProtocolSupport.Key final Buffer key) {
        return blockingToSingle(() -> reservedCnx.sunion(key).blockingGet());
    }

    @Override
    public <T> Single<List<T>> sunion(@RedisProtocolSupport.Key final Buffer key1,
                                      @RedisProtocolSupport.Key final Buffer key2) {
        return blockingToSingle(() -> reservedCnx.sunion(key1, key2).blockingGet());
    }

    @Override
    public <T> Single<List<T>> sunion(@RedisProtocolSupport.Key final Buffer key1,
                                      @RedisProtocolSupport.Key final Buffer key2,
                                      @RedisProtocolSupport.Key final Buffer key3) {
        return blockingToSingle(() -> reservedCnx.sunion(key1, key2, key3).blockingGet());
    }

    @Override
    public <T> Single<List<T>> sunion(@RedisProtocolSupport.Key final Collection<Buffer> keys) {
        return blockingToSingle(() -> reservedCnx.sunion(keys).blockingGet());
    }

    @Override
    public Single<Long> sunionstore(@RedisProtocolSupport.Key final Buffer destination,
                                    @RedisProtocolSupport.Key final Buffer key) {
        return blockingToSingle(() -> reservedCnx.sunionstore(destination, key).blockingGet());
    }

    @Override
    public Single<Long> sunionstore(@RedisProtocolSupport.Key final Buffer destination,
                                    @RedisProtocolSupport.Key final Buffer key1,
                                    @RedisProtocolSupport.Key final Buffer key2) {
        return blockingToSingle(() -> reservedCnx.sunionstore(destination, key1, key2).blockingGet());
    }

    @Override
    public Single<Long> sunionstore(@RedisProtocolSupport.Key final Buffer destination,
                                    @RedisProtocolSupport.Key final Buffer key1,
                                    @RedisProtocolSupport.Key final Buffer key2,
                                    @RedisProtocolSupport.Key final Buffer key3) {
        return blockingToSingle(() -> reservedCnx.sunionstore(destination, key1, key2, key3).blockingGet());
    }

    @Override
    public Single<Long> sunionstore(@RedisProtocolSupport.Key final Buffer destination,
                                    @RedisProtocolSupport.Key final Collection<Buffer> keys) {
        return blockingToSingle(() -> reservedCnx.sunionstore(destination, keys).blockingGet());
    }

    @Override
    public Single<String> swapdb(final long index, final long index1) {
        return blockingToSingle(() -> reservedCnx.swapdb(index, index1).blockingGet());
    }

    @Override
    public <T> Single<List<T>> time() {
        return blockingToSingle(() -> reservedCnx.time().blockingGet());
    }

    @Override
    public Single<Long> touch(@RedisProtocolSupport.Key final Buffer key) {
        return blockingToSingle(() -> reservedCnx.touch(key).blockingGet());
    }

    @Override
    public Single<Long> touch(@RedisProtocolSupport.Key final Buffer key1,
                              @RedisProtocolSupport.Key final Buffer key2) {
        return blockingToSingle(() -> reservedCnx.touch(key1, key2).blockingGet());
    }

    @Override
    public Single<Long> touch(@RedisProtocolSupport.Key final Buffer key1, @RedisProtocolSupport.Key final Buffer key2,
                              @RedisProtocolSupport.Key final Buffer key3) {
        return blockingToSingle(() -> reservedCnx.touch(key1, key2, key3).blockingGet());
    }

    @Override
    public Single<Long> touch(@RedisProtocolSupport.Key final Collection<Buffer> keys) {
        return blockingToSingle(() -> reservedCnx.touch(keys).blockingGet());
    }

    @Override
    public Single<Long> ttl(@RedisProtocolSupport.Key final Buffer key) {
        return blockingToSingle(() -> reservedCnx.ttl(key).blockingGet());
    }

    @Override
    public Single<String> type(@RedisProtocolSupport.Key final Buffer key) {
        return blockingToSingle(() -> reservedCnx.type(key).blockingGet());
    }

    @Override
    public Single<Long> unlink(@RedisProtocolSupport.Key final Buffer key) {
        return blockingToSingle(() -> reservedCnx.unlink(key).blockingGet());
    }

    @Override
    public Single<Long> unlink(@RedisProtocolSupport.Key final Buffer key1,
                               @RedisProtocolSupport.Key final Buffer key2) {
        return blockingToSingle(() -> reservedCnx.unlink(key1, key2).blockingGet());
    }

    @Override
    public Single<Long> unlink(@RedisProtocolSupport.Key final Buffer key1, @RedisProtocolSupport.Key final Buffer key2,
                               @RedisProtocolSupport.Key final Buffer key3) {
        return blockingToSingle(() -> reservedCnx.unlink(key1, key2, key3).blockingGet());
    }

    @Override
    public Single<Long> unlink(@RedisProtocolSupport.Key final Collection<Buffer> keys) {
        return blockingToSingle(() -> reservedCnx.unlink(keys).blockingGet());
    }

    @Override
    public Single<String> unwatch() {
        return blockingToSingle(() -> reservedCnx.unwatch().blockingGet());
    }

    @Override
    public Single<Long> wait(final long numslaves, final long timeout) {
        return blockingToSingle(() -> reservedCnx.wait(numslaves, timeout).blockingGet());
    }

    @Override
    public Single<String> watch(@RedisProtocolSupport.Key final Buffer key) {
        return blockingToSingle(() -> reservedCnx.watch(key).blockingGet());
    }

    @Override
    public Single<String> watch(@RedisProtocolSupport.Key final Buffer key1,
                                @RedisProtocolSupport.Key final Buffer key2) {
        return blockingToSingle(() -> reservedCnx.watch(key1, key2).blockingGet());
    }

    @Override
    public Single<String> watch(@RedisProtocolSupport.Key final Buffer key1,
                                @RedisProtocolSupport.Key final Buffer key2,
                                @RedisProtocolSupport.Key final Buffer key3) {
        return blockingToSingle(() -> reservedCnx.watch(key1, key2, key3).blockingGet());
    }

    @Override
    public Single<String> watch(@RedisProtocolSupport.Key final Collection<Buffer> keys) {
        return blockingToSingle(() -> reservedCnx.watch(keys).blockingGet());
    }

    @Override
    public Single<Buffer> xadd(@RedisProtocolSupport.Key final Buffer key, final Buffer id, final Buffer field,
                               final Buffer value) {
        return blockingToSingle(() -> reservedCnx.xadd(key, id, field, value).blockingGet());
    }

    @Override
    public Single<Buffer> xadd(@RedisProtocolSupport.Key final Buffer key, final Buffer id, final Buffer field1,
                               final Buffer value1, final Buffer field2, final Buffer value2) {
        return blockingToSingle(() -> reservedCnx.xadd(key, id, field1, value1, field2, value2).blockingGet());
    }

    @Override
    public Single<Buffer> xadd(@RedisProtocolSupport.Key final Buffer key, final Buffer id, final Buffer field1,
                               final Buffer value1, final Buffer field2, final Buffer value2, final Buffer field3,
                               final Buffer value3) {
        return blockingToSingle(
                    () -> reservedCnx.xadd(key, id, field1, value1, field2, value2, field3, value3).blockingGet());
    }

    @Override
    public Single<Buffer> xadd(@RedisProtocolSupport.Key final Buffer key, final Buffer id,
                               final Collection<RedisProtocolSupport.BufferFieldValue> fieldValues) {
        return blockingToSingle(() -> reservedCnx.xadd(key, id, fieldValues).blockingGet());
    }

    @Override
    public Single<Long> xlen(@RedisProtocolSupport.Key final Buffer key) {
        return blockingToSingle(() -> reservedCnx.xlen(key).blockingGet());
    }

    @Override
    public <T> Single<List<T>> xpending(@RedisProtocolSupport.Key final Buffer key, final Buffer group) {
        return blockingToSingle(() -> reservedCnx.xpending(key, group).blockingGet());
    }

    @Override
    public <T> Single<List<T>> xpending(@RedisProtocolSupport.Key final Buffer key, final Buffer group,
                                        @Nullable final Buffer start, @Nullable final Buffer end,
                                        @Nullable final Long count, @Nullable final Buffer consumer) {
        return blockingToSingle(() -> reservedCnx.xpending(key, group, start, end, count, consumer).blockingGet());
    }

    @Override
    public <T> Single<List<T>> xrange(@RedisProtocolSupport.Key final Buffer key, final Buffer start,
                                      final Buffer end) {
        return blockingToSingle(() -> reservedCnx.xrange(key, start, end).blockingGet());
    }

    @Override
    public <T> Single<List<T>> xrange(@RedisProtocolSupport.Key final Buffer key, final Buffer start, final Buffer end,
                                      @Nullable final Long count) {
        return blockingToSingle(() -> reservedCnx.xrange(key, start, end, count).blockingGet());
    }

    @Override
    public <T> Single<List<T>> xread(@RedisProtocolSupport.Key final Collection<Buffer> keys,
                                     final Collection<Buffer> ids) {
        return blockingToSingle(() -> reservedCnx.xread(keys, ids).blockingGet());
    }

    @Override
    public <T> Single<List<T>> xread(@Nullable final Long count, @Nullable final Long blockMilliseconds,
                                     @RedisProtocolSupport.Key final Collection<Buffer> keys,
                                     final Collection<Buffer> ids) {
        return blockingToSingle(() -> reservedCnx.xread(count, blockMilliseconds, keys, ids).blockingGet());
    }

    @Override
    public <T> Single<List<T>> xreadgroup(final RedisProtocolSupport.BufferGroupConsumer groupConsumer,
                                          @RedisProtocolSupport.Key final Collection<Buffer> keys,
                                          final Collection<Buffer> ids) {
        return blockingToSingle(() -> reservedCnx.xreadgroup(groupConsumer, keys, ids).blockingGet());
    }

    @Override
    public <T> Single<List<T>> xreadgroup(final RedisProtocolSupport.BufferGroupConsumer groupConsumer,
                                          @Nullable final Long count, @Nullable final Long blockMilliseconds,
                                          @RedisProtocolSupport.Key final Collection<Buffer> keys,
                                          final Collection<Buffer> ids) {
        return blockingToSingle(
                    () -> reservedCnx.xreadgroup(groupConsumer, count, blockMilliseconds, keys, ids).blockingGet());
    }

    @Override
    public <T> Single<List<T>> xrevrange(@RedisProtocolSupport.Key final Buffer key, final Buffer end,
                                         final Buffer start) {
        return blockingToSingle(() -> reservedCnx.xrevrange(key, end, start).blockingGet());
    }

    @Override
    public <T> Single<List<T>> xrevrange(@RedisProtocolSupport.Key final Buffer key, final Buffer end,
                                         final Buffer start, @Nullable final Long count) {
        return blockingToSingle(() -> reservedCnx.xrevrange(key, end, start, count).blockingGet());
    }

    @Override
    public Single<Long> zadd(@RedisProtocolSupport.Key final Buffer key,
                             final Collection<RedisProtocolSupport.BufferScoreMember> scoreMembers) {
        return blockingToSingle(() -> reservedCnx.zadd(key, scoreMembers).blockingGet());
    }

    @Override
    public Single<Long> zadd(@RedisProtocolSupport.Key final Buffer key,
                             @Nullable final RedisProtocolSupport.ZaddCondition condition,
                             @Nullable final RedisProtocolSupport.ZaddChange change, final double score,
                             final Buffer member) {
        return blockingToSingle(() -> reservedCnx.zadd(key, condition, change, score, member).blockingGet());
    }

    @Override
    public Single<Long> zadd(@RedisProtocolSupport.Key final Buffer key,
                             @Nullable final RedisProtocolSupport.ZaddCondition condition,
                             @Nullable final RedisProtocolSupport.ZaddChange change, final double score1,
                             final Buffer member1, final double score2, final Buffer member2) {
        return blockingToSingle(
                    () -> reservedCnx.zadd(key, condition, change, score1, member1, score2, member2).blockingGet());
    }

    @Override
    public Single<Long> zadd(@RedisProtocolSupport.Key final Buffer key,
                             @Nullable final RedisProtocolSupport.ZaddCondition condition,
                             @Nullable final RedisProtocolSupport.ZaddChange change, final double score1,
                             final Buffer member1, final double score2, final Buffer member2, final double score3,
                             final Buffer member3) {
        return blockingToSingle(() -> reservedCnx
                    .zadd(key, condition, change, score1, member1, score2, member2, score3, member3).blockingGet());
    }

    @Override
    public Single<Long> zadd(@RedisProtocolSupport.Key final Buffer key,
                             @Nullable final RedisProtocolSupport.ZaddCondition condition,
                             @Nullable final RedisProtocolSupport.ZaddChange change,
                             final Collection<RedisProtocolSupport.BufferScoreMember> scoreMembers) {
        return blockingToSingle(() -> reservedCnx.zadd(key, condition, change, scoreMembers).blockingGet());
    }

    @Override
    public Single<Double> zaddIncr(@RedisProtocolSupport.Key final Buffer key,
                                   final Collection<RedisProtocolSupport.BufferScoreMember> scoreMembers) {
        return blockingToSingle(() -> reservedCnx.zaddIncr(key, scoreMembers).blockingGet());
    }

    @Override
    public Single<Double> zaddIncr(@RedisProtocolSupport.Key final Buffer key,
                                   @Nullable final RedisProtocolSupport.ZaddCondition condition,
                                   @Nullable final RedisProtocolSupport.ZaddChange change, final double score,
                                   final Buffer member) {
        return blockingToSingle(() -> reservedCnx.zaddIncr(key, condition, change, score, member).blockingGet());
    }

    @Override
    public Single<Double> zaddIncr(@RedisProtocolSupport.Key final Buffer key,
                                   @Nullable final RedisProtocolSupport.ZaddCondition condition,
                                   @Nullable final RedisProtocolSupport.ZaddChange change, final double score1,
                                   final Buffer member1, final double score2, final Buffer member2) {
        return blockingToSingle(
                    () -> reservedCnx.zaddIncr(key, condition, change, score1, member1, score2, member2).blockingGet());
    }

    @Override
    public Single<Double> zaddIncr(@RedisProtocolSupport.Key final Buffer key,
                                   @Nullable final RedisProtocolSupport.ZaddCondition condition,
                                   @Nullable final RedisProtocolSupport.ZaddChange change, final double score1,
                                   final Buffer member1, final double score2, final Buffer member2, final double score3,
                                   final Buffer member3) {
        return blockingToSingle(() -> reservedCnx
                    .zaddIncr(key, condition, change, score1, member1, score2, member2, score3, member3).blockingGet());
    }

    @Override
    public Single<Double> zaddIncr(@RedisProtocolSupport.Key final Buffer key,
                                   @Nullable final RedisProtocolSupport.ZaddCondition condition,
                                   @Nullable final RedisProtocolSupport.ZaddChange change,
                                   final Collection<RedisProtocolSupport.BufferScoreMember> scoreMembers) {
        return blockingToSingle(() -> reservedCnx.zaddIncr(key, condition, change, scoreMembers).blockingGet());
    }

    @Override
    public Single<Long> zcard(@RedisProtocolSupport.Key final Buffer key) {
        return blockingToSingle(() -> reservedCnx.zcard(key).blockingGet());
    }

    @Override
    public Single<Long> zcount(@RedisProtocolSupport.Key final Buffer key, final double min, final double max) {
        return blockingToSingle(() -> reservedCnx.zcount(key, min, max).blockingGet());
    }

    @Override
    public Single<Double> zincrby(@RedisProtocolSupport.Key final Buffer key, final long increment,
                                  final Buffer member) {
        return blockingToSingle(() -> reservedCnx.zincrby(key, increment, member).blockingGet());
    }

    @Override
    public Single<Long> zinterstore(@RedisProtocolSupport.Key final Buffer destination, final long numkeys,
                                    @RedisProtocolSupport.Key final Collection<Buffer> keys) {
        return blockingToSingle(() -> reservedCnx.zinterstore(destination, numkeys, keys).blockingGet());
    }

    @Override
    public Single<Long> zinterstore(@RedisProtocolSupport.Key final Buffer destination, final long numkeys,
                                    @RedisProtocolSupport.Key final Collection<Buffer> keys,
                                    final Collection<Long> weightses,
                                    @Nullable final RedisProtocolSupport.ZinterstoreAggregate aggregate) {
        return blockingToSingle(
                    () -> reservedCnx.zinterstore(destination, numkeys, keys, weightses, aggregate).blockingGet());
    }

    @Override
    public Single<Long> zlexcount(@RedisProtocolSupport.Key final Buffer key, final Buffer min, final Buffer max) {
        return blockingToSingle(() -> reservedCnx.zlexcount(key, min, max).blockingGet());
    }

    @Override
    public <T> Single<List<T>> zpopmax(@RedisProtocolSupport.Key final Buffer key) {
        return blockingToSingle(() -> reservedCnx.zpopmax(key).blockingGet());
    }

    @Override
    public <T> Single<List<T>> zpopmax(@RedisProtocolSupport.Key final Buffer key, @Nullable final Long count) {
        return blockingToSingle(() -> reservedCnx.zpopmax(key, count).blockingGet());
    }

    @Override
    public <T> Single<List<T>> zpopmin(@RedisProtocolSupport.Key final Buffer key) {
        return blockingToSingle(() -> reservedCnx.zpopmin(key).blockingGet());
    }

    @Override
    public <T> Single<List<T>> zpopmin(@RedisProtocolSupport.Key final Buffer key, @Nullable final Long count) {
        return blockingToSingle(() -> reservedCnx.zpopmin(key, count).blockingGet());
    }

    @Override
    public <T> Single<List<T>> zrange(@RedisProtocolSupport.Key final Buffer key, final long start, final long stop) {
        return blockingToSingle(() -> reservedCnx.zrange(key, start, stop).blockingGet());
    }

    @Override
    public <T> Single<List<T>> zrange(@RedisProtocolSupport.Key final Buffer key, final long start, final long stop,
                                      @Nullable final RedisProtocolSupport.ZrangeWithscores withscores) {
        return blockingToSingle(() -> reservedCnx.zrange(key, start, stop, withscores).blockingGet());
    }

    @Override
    public <T> Single<List<T>> zrangebylex(@RedisProtocolSupport.Key final Buffer key, final Buffer min,
                                           final Buffer max) {
        return blockingToSingle(() -> reservedCnx.zrangebylex(key, min, max).blockingGet());
    }

    @Override
    public <T> Single<List<T>> zrangebylex(@RedisProtocolSupport.Key final Buffer key, final Buffer min,
                                           final Buffer max,
                                           @Nullable final RedisProtocolSupport.OffsetCount offsetCount) {
        return blockingToSingle(() -> reservedCnx.zrangebylex(key, min, max, offsetCount).blockingGet());
    }

    @Override
    public <T> Single<List<T>> zrangebyscore(@RedisProtocolSupport.Key final Buffer key, final double min,
                                             final double max) {
        return blockingToSingle(() -> reservedCnx.zrangebyscore(key, min, max).blockingGet());
    }

    @Override
    public <T> Single<List<T>> zrangebyscore(@RedisProtocolSupport.Key final Buffer key, final double min,
                                             final double max,
                                             @Nullable final RedisProtocolSupport.ZrangebyscoreWithscores withscores,
                                             @Nullable final RedisProtocolSupport.OffsetCount offsetCount) {
        return blockingToSingle(() -> reservedCnx.zrangebyscore(key, min, max, withscores, offsetCount).blockingGet());
    }

    @Override
    public Single<Long> zrank(@RedisProtocolSupport.Key final Buffer key, final Buffer member) {
        return blockingToSingle(() -> reservedCnx.zrank(key, member).blockingGet());
    }

    @Override
    public Single<Long> zrem(@RedisProtocolSupport.Key final Buffer key, final Buffer member) {
        return blockingToSingle(() -> reservedCnx.zrem(key, member).blockingGet());
    }

    @Override
    public Single<Long> zrem(@RedisProtocolSupport.Key final Buffer key, final Buffer member1, final Buffer member2) {
        return blockingToSingle(() -> reservedCnx.zrem(key, member1, member2).blockingGet());
    }

    @Override
    public Single<Long> zrem(@RedisProtocolSupport.Key final Buffer key, final Buffer member1, final Buffer member2,
                             final Buffer member3) {
        return blockingToSingle(() -> reservedCnx.zrem(key, member1, member2, member3).blockingGet());
    }

    @Override
    public Single<Long> zrem(@RedisProtocolSupport.Key final Buffer key, final Collection<Buffer> members) {
        return blockingToSingle(() -> reservedCnx.zrem(key, members).blockingGet());
    }

    @Override
    public Single<Long> zremrangebylex(@RedisProtocolSupport.Key final Buffer key, final Buffer min, final Buffer max) {
        return blockingToSingle(() -> reservedCnx.zremrangebylex(key, min, max).blockingGet());
    }

    @Override
    public Single<Long> zremrangebyrank(@RedisProtocolSupport.Key final Buffer key, final long start, final long stop) {
        return blockingToSingle(() -> reservedCnx.zremrangebyrank(key, start, stop).blockingGet());
    }

    @Override
    public Single<Long> zremrangebyscore(@RedisProtocolSupport.Key final Buffer key, final double min,
                                         final double max) {
        return blockingToSingle(() -> reservedCnx.zremrangebyscore(key, min, max).blockingGet());
    }

    @Override
    public <T> Single<List<T>> zrevrange(@RedisProtocolSupport.Key final Buffer key, final long start,
                                         final long stop) {
        return blockingToSingle(() -> reservedCnx.zrevrange(key, start, stop).blockingGet());
    }

    @Override
    public <T> Single<List<T>> zrevrange(@RedisProtocolSupport.Key final Buffer key, final long start, final long stop,
                                         @Nullable final RedisProtocolSupport.ZrevrangeWithscores withscores) {
        return blockingToSingle(() -> reservedCnx.zrevrange(key, start, stop, withscores).blockingGet());
    }

    @Override
    public <T> Single<List<T>> zrevrangebylex(@RedisProtocolSupport.Key final Buffer key, final Buffer max,
                                              final Buffer min) {
        return blockingToSingle(() -> reservedCnx.zrevrangebylex(key, max, min).blockingGet());
    }

    @Override
    public <T> Single<List<T>> zrevrangebylex(@RedisProtocolSupport.Key final Buffer key, final Buffer max,
                                              final Buffer min,
                                              @Nullable final RedisProtocolSupport.OffsetCount offsetCount) {
        return blockingToSingle(() -> reservedCnx.zrevrangebylex(key, max, min, offsetCount).blockingGet());
    }

    @Override
    public <T> Single<List<T>> zrevrangebyscore(@RedisProtocolSupport.Key final Buffer key, final double max,
                                                final double min) {
        return blockingToSingle(() -> reservedCnx.zrevrangebyscore(key, max, min).blockingGet());
    }

    @Override
    public <T> Single<List<T>> zrevrangebyscore(@RedisProtocolSupport.Key final Buffer key, final double max,
                                                final double min,
                                                @Nullable final RedisProtocolSupport.ZrevrangebyscoreWithscores withscores,
                                                @Nullable final RedisProtocolSupport.OffsetCount offsetCount) {
        return blockingToSingle(
                    () -> reservedCnx.zrevrangebyscore(key, max, min, withscores, offsetCount).blockingGet());
    }

    @Override
    public Single<Long> zrevrank(@RedisProtocolSupport.Key final Buffer key, final Buffer member) {
        return blockingToSingle(() -> reservedCnx.zrevrank(key, member).blockingGet());
    }

    @Override
    public <T> Single<List<T>> zscan(@RedisProtocolSupport.Key final Buffer key, final long cursor) {
        return blockingToSingle(() -> reservedCnx.zscan(key, cursor).blockingGet());
    }

    @Override
    public <T> Single<List<T>> zscan(@RedisProtocolSupport.Key final Buffer key, final long cursor,
                                     @Nullable final Buffer matchPattern, @Nullable final Long count) {
        return blockingToSingle(() -> reservedCnx.zscan(key, cursor, matchPattern, count).blockingGet());
    }

    @Override
    public Single<Double> zscore(@RedisProtocolSupport.Key final Buffer key, final Buffer member) {
        return blockingToSingle(() -> reservedCnx.zscore(key, member).blockingGet());
    }

    @Override
    public Single<Long> zunionstore(@RedisProtocolSupport.Key final Buffer destination, final long numkeys,
                                    @RedisProtocolSupport.Key final Collection<Buffer> keys) {
        return blockingToSingle(() -> reservedCnx.zunionstore(destination, numkeys, keys).blockingGet());
    }

    @Override
    public Single<Long> zunionstore(@RedisProtocolSupport.Key final Buffer destination, final long numkeys,
                                    @RedisProtocolSupport.Key final Collection<Buffer> keys,
                                    final Collection<Long> weightses,
                                    @Nullable final RedisProtocolSupport.ZunionstoreAggregate aggregate) {
        return blockingToSingle(
                    () -> reservedCnx.zunionstore(destination, numkeys, keys, weightses, aggregate).blockingGet());
    }
}

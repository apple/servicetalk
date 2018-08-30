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

import io.servicetalk.concurrent.api.Completable;
import io.servicetalk.concurrent.api.Single;

import java.util.Collection;
import java.util.List;
import java.util.Objects;
import javax.annotation.Generated;
import javax.annotation.Nullable;

import static io.servicetalk.redis.api.BlockingUtils.blockingToCompletable;
import static io.servicetalk.redis.api.BlockingUtils.blockingToSingle;
import static io.servicetalk.redis.api.BlockingUtils.futureToSingle;

@Generated({})
@SuppressWarnings("unchecked")
final class BlockingTransactedRedisCommanderToTransactedRedisCommander extends TransactedRedisCommander {

    private final BlockingTransactedRedisCommander reservedCnx;

    BlockingTransactedRedisCommanderToTransactedRedisCommander(final BlockingTransactedRedisCommander reservedCnx) {
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
    public Single<Long> append(@RedisProtocolSupport.Key final CharSequence key, final CharSequence value) {
        return futureToSingle(() -> reservedCnx.append(key, value));
    }

    @Override
    public Single<String> auth(final CharSequence password) {
        return futureToSingle(() -> reservedCnx.auth(password));
    }

    @Override
    public Single<String> bgrewriteaof() {
        return futureToSingle(() -> reservedCnx.bgrewriteaof());
    }

    @Override
    public Single<String> bgsave() {
        return futureToSingle(() -> reservedCnx.bgsave());
    }

    @Override
    public Single<Long> bitcount(@RedisProtocolSupport.Key final CharSequence key) {
        return futureToSingle(() -> reservedCnx.bitcount(key));
    }

    @Override
    public Single<Long> bitcount(@RedisProtocolSupport.Key final CharSequence key, @Nullable final Long start,
                                 @Nullable final Long end) {
        return futureToSingle(() -> reservedCnx.bitcount(key, start, end));
    }

    @Override
    public Single<List<Long>> bitfield(@RedisProtocolSupport.Key final CharSequence key,
                                       final Collection<RedisProtocolSupport.BitfieldOperation> operations) {
        return futureToSingle(() -> reservedCnx.bitfield(key, operations));
    }

    @Override
    public Single<Long> bitop(final CharSequence operation, @RedisProtocolSupport.Key final CharSequence destkey,
                              @RedisProtocolSupport.Key final CharSequence key) {
        return futureToSingle(() -> reservedCnx.bitop(operation, destkey, key));
    }

    @Override
    public Single<Long> bitop(final CharSequence operation, @RedisProtocolSupport.Key final CharSequence destkey,
                              @RedisProtocolSupport.Key final CharSequence key1,
                              @RedisProtocolSupport.Key final CharSequence key2) {
        return futureToSingle(() -> reservedCnx.bitop(operation, destkey, key1, key2));
    }

    @Override
    public Single<Long> bitop(final CharSequence operation, @RedisProtocolSupport.Key final CharSequence destkey,
                              @RedisProtocolSupport.Key final CharSequence key1,
                              @RedisProtocolSupport.Key final CharSequence key2,
                              @RedisProtocolSupport.Key final CharSequence key3) {
        return futureToSingle(() -> reservedCnx.bitop(operation, destkey, key1, key2, key3));
    }

    @Override
    public Single<Long> bitop(final CharSequence operation, @RedisProtocolSupport.Key final CharSequence destkey,
                              @RedisProtocolSupport.Key final Collection<? extends CharSequence> keys) {
        return futureToSingle(() -> reservedCnx.bitop(operation, destkey, keys));
    }

    @Override
    public Single<Long> bitpos(@RedisProtocolSupport.Key final CharSequence key, final long bit) {
        return futureToSingle(() -> reservedCnx.bitpos(key, bit));
    }

    @Override
    public Single<Long> bitpos(@RedisProtocolSupport.Key final CharSequence key, final long bit,
                               @Nullable final Long start, @Nullable final Long end) {
        return futureToSingle(() -> reservedCnx.bitpos(key, bit, start, end));
    }

    @Override
    public <T> Single<List<T>> blpop(@RedisProtocolSupport.Key final Collection<? extends CharSequence> keys,
                                     final long timeout) {
        return futureToSingle(() -> reservedCnx.blpop(keys, timeout));
    }

    @Override
    public <T> Single<List<T>> brpop(@RedisProtocolSupport.Key final Collection<? extends CharSequence> keys,
                                     final long timeout) {
        return futureToSingle(() -> reservedCnx.brpop(keys, timeout));
    }

    @Override
    public Single<String> brpoplpush(@RedisProtocolSupport.Key final CharSequence source,
                                     @RedisProtocolSupport.Key final CharSequence destination, final long timeout) {
        return futureToSingle(() -> reservedCnx.brpoplpush(source, destination, timeout));
    }

    @Override
    public <T> Single<List<T>> bzpopmax(@RedisProtocolSupport.Key final Collection<? extends CharSequence> keys,
                                        final long timeout) {
        return futureToSingle(() -> reservedCnx.bzpopmax(keys, timeout));
    }

    @Override
    public <T> Single<List<T>> bzpopmin(@RedisProtocolSupport.Key final Collection<? extends CharSequence> keys,
                                        final long timeout) {
        return futureToSingle(() -> reservedCnx.bzpopmin(keys, timeout));
    }

    @Override
    public Single<Long> clientKill(@Nullable final Long id, @Nullable final RedisProtocolSupport.ClientKillType type,
                                   @Nullable final CharSequence addrIpPort, @Nullable final CharSequence skipmeYesNo) {
        return futureToSingle(() -> reservedCnx.clientKill(id, type, addrIpPort, skipmeYesNo));
    }

    @Override
    public Single<String> clientList() {
        return futureToSingle(() -> reservedCnx.clientList());
    }

    @Override
    public Single<String> clientGetname() {
        return futureToSingle(() -> reservedCnx.clientGetname());
    }

    @Override
    public Single<String> clientPause(final long timeout) {
        return futureToSingle(() -> reservedCnx.clientPause(timeout));
    }

    @Override
    public Single<String> clientReply(final RedisProtocolSupport.ClientReplyReplyMode replyMode) {
        return futureToSingle(() -> reservedCnx.clientReply(replyMode));
    }

    @Override
    public Single<String> clientSetname(final CharSequence connectionName) {
        return futureToSingle(() -> reservedCnx.clientSetname(connectionName));
    }

    @Override
    public Single<String> clusterAddslots(final long slot) {
        return futureToSingle(() -> reservedCnx.clusterAddslots(slot));
    }

    @Override
    public Single<String> clusterAddslots(final long slot1, final long slot2) {
        return futureToSingle(() -> reservedCnx.clusterAddslots(slot1, slot2));
    }

    @Override
    public Single<String> clusterAddslots(final long slot1, final long slot2, final long slot3) {
        return futureToSingle(() -> reservedCnx.clusterAddslots(slot1, slot2, slot3));
    }

    @Override
    public Single<String> clusterAddslots(final Collection<Long> slots) {
        return futureToSingle(() -> reservedCnx.clusterAddslots(slots));
    }

    @Override
    public Single<Long> clusterCountFailureReports(final CharSequence nodeId) {
        return futureToSingle(() -> reservedCnx.clusterCountFailureReports(nodeId));
    }

    @Override
    public Single<Long> clusterCountkeysinslot(final long slot) {
        return futureToSingle(() -> reservedCnx.clusterCountkeysinslot(slot));
    }

    @Override
    public Single<String> clusterDelslots(final long slot) {
        return futureToSingle(() -> reservedCnx.clusterDelslots(slot));
    }

    @Override
    public Single<String> clusterDelslots(final long slot1, final long slot2) {
        return futureToSingle(() -> reservedCnx.clusterDelslots(slot1, slot2));
    }

    @Override
    public Single<String> clusterDelslots(final long slot1, final long slot2, final long slot3) {
        return futureToSingle(() -> reservedCnx.clusterDelslots(slot1, slot2, slot3));
    }

    @Override
    public Single<String> clusterDelslots(final Collection<Long> slots) {
        return futureToSingle(() -> reservedCnx.clusterDelslots(slots));
    }

    @Override
    public Single<String> clusterFailover() {
        return futureToSingle(() -> reservedCnx.clusterFailover());
    }

    @Override
    public Single<String> clusterFailover(@Nullable final RedisProtocolSupport.ClusterFailoverOptions options) {
        return futureToSingle(() -> reservedCnx.clusterFailover(options));
    }

    @Override
    public Single<String> clusterForget(final CharSequence nodeId) {
        return futureToSingle(() -> reservedCnx.clusterForget(nodeId));
    }

    @Override
    public <T> Single<List<T>> clusterGetkeysinslot(final long slot, final long count) {
        return futureToSingle(() -> reservedCnx.clusterGetkeysinslot(slot, count));
    }

    @Override
    public Single<String> clusterInfo() {
        return futureToSingle(() -> reservedCnx.clusterInfo());
    }

    @Override
    public Single<Long> clusterKeyslot(final CharSequence key) {
        return futureToSingle(() -> reservedCnx.clusterKeyslot(key));
    }

    @Override
    public Single<String> clusterMeet(final CharSequence ip, final long port) {
        return futureToSingle(() -> reservedCnx.clusterMeet(ip, port));
    }

    @Override
    public Single<String> clusterNodes() {
        return futureToSingle(() -> reservedCnx.clusterNodes());
    }

    @Override
    public Single<String> clusterReplicate(final CharSequence nodeId) {
        return futureToSingle(() -> reservedCnx.clusterReplicate(nodeId));
    }

    @Override
    public Single<String> clusterReset() {
        return futureToSingle(() -> reservedCnx.clusterReset());
    }

    @Override
    public Single<String> clusterReset(@Nullable final RedisProtocolSupport.ClusterResetResetType resetType) {
        return futureToSingle(() -> reservedCnx.clusterReset(resetType));
    }

    @Override
    public Single<String> clusterSaveconfig() {
        return futureToSingle(() -> reservedCnx.clusterSaveconfig());
    }

    @Override
    public Single<String> clusterSetConfigEpoch(final long configEpoch) {
        return futureToSingle(() -> reservedCnx.clusterSetConfigEpoch(configEpoch));
    }

    @Override
    public Single<String> clusterSetslot(final long slot,
                                         final RedisProtocolSupport.ClusterSetslotSubcommand subcommand) {
        return futureToSingle(() -> reservedCnx.clusterSetslot(slot, subcommand));
    }

    @Override
    public Single<String> clusterSetslot(final long slot,
                                         final RedisProtocolSupport.ClusterSetslotSubcommand subcommand,
                                         @Nullable final CharSequence nodeId) {
        return futureToSingle(() -> reservedCnx.clusterSetslot(slot, subcommand, nodeId));
    }

    @Override
    public Single<String> clusterSlaves(final CharSequence nodeId) {
        return futureToSingle(() -> reservedCnx.clusterSlaves(nodeId));
    }

    @Override
    public <T> Single<List<T>> clusterSlots() {
        return futureToSingle(() -> reservedCnx.clusterSlots());
    }

    @Override
    public <T> Single<List<T>> command() {
        return futureToSingle(() -> reservedCnx.command());
    }

    @Override
    public Single<Long> commandCount() {
        return futureToSingle(() -> reservedCnx.commandCount());
    }

    @Override
    public <T> Single<List<T>> commandGetkeys() {
        return futureToSingle(() -> reservedCnx.commandGetkeys());
    }

    @Override
    public <T> Single<List<T>> commandInfo(final CharSequence commandName) {
        return futureToSingle(() -> reservedCnx.commandInfo(commandName));
    }

    @Override
    public <T> Single<List<T>> commandInfo(final CharSequence commandName1, final CharSequence commandName2) {
        return futureToSingle(() -> reservedCnx.commandInfo(commandName1, commandName2));
    }

    @Override
    public <T> Single<List<T>> commandInfo(final CharSequence commandName1, final CharSequence commandName2,
                                           final CharSequence commandName3) {
        return futureToSingle(() -> reservedCnx.commandInfo(commandName1, commandName2, commandName3));
    }

    @Override
    public <T> Single<List<T>> commandInfo(final Collection<? extends CharSequence> commandNames) {
        return futureToSingle(() -> reservedCnx.commandInfo(commandNames));
    }

    @Override
    public <T> Single<List<T>> configGet(final CharSequence parameter) {
        return futureToSingle(() -> reservedCnx.configGet(parameter));
    }

    @Override
    public Single<String> configRewrite() {
        return futureToSingle(() -> reservedCnx.configRewrite());
    }

    @Override
    public Single<String> configSet(final CharSequence parameter, final CharSequence value) {
        return futureToSingle(() -> reservedCnx.configSet(parameter, value));
    }

    @Override
    public Single<String> configResetstat() {
        return futureToSingle(() -> reservedCnx.configResetstat());
    }

    @Override
    public Single<Long> dbsize() {
        return futureToSingle(() -> reservedCnx.dbsize());
    }

    @Override
    public Single<String> debugObject(@RedisProtocolSupport.Key final CharSequence key) {
        return futureToSingle(() -> reservedCnx.debugObject(key));
    }

    @Override
    public Single<String> debugSegfault() {
        return futureToSingle(() -> reservedCnx.debugSegfault());
    }

    @Override
    public Single<Long> decr(@RedisProtocolSupport.Key final CharSequence key) {
        return futureToSingle(() -> reservedCnx.decr(key));
    }

    @Override
    public Single<Long> decrby(@RedisProtocolSupport.Key final CharSequence key, final long decrement) {
        return futureToSingle(() -> reservedCnx.decrby(key, decrement));
    }

    @Override
    public Single<Long> del(@RedisProtocolSupport.Key final CharSequence key) {
        return futureToSingle(() -> reservedCnx.del(key));
    }

    @Override
    public Single<Long> del(@RedisProtocolSupport.Key final CharSequence key1,
                            @RedisProtocolSupport.Key final CharSequence key2) {
        return futureToSingle(() -> reservedCnx.del(key1, key2));
    }

    @Override
    public Single<Long> del(@RedisProtocolSupport.Key final CharSequence key1,
                            @RedisProtocolSupport.Key final CharSequence key2,
                            @RedisProtocolSupport.Key final CharSequence key3) {
        return futureToSingle(() -> reservedCnx.del(key1, key2, key3));
    }

    @Override
    public Single<Long> del(@RedisProtocolSupport.Key final Collection<? extends CharSequence> keys) {
        return futureToSingle(() -> reservedCnx.del(keys));
    }

    @Override
    public Single<String> discard() {
        return blockingToSingle(() -> reservedCnx.discard());
    }

    @Override
    public Single<String> dump(@RedisProtocolSupport.Key final CharSequence key) {
        return futureToSingle(() -> reservedCnx.dump(key));
    }

    @Override
    public Single<String> echo(final CharSequence message) {
        return futureToSingle(() -> reservedCnx.echo(message));
    }

    @Override
    public Single<String> eval(final CharSequence script, final long numkeys,
                               @RedisProtocolSupport.Key final Collection<? extends CharSequence> keys,
                               final Collection<? extends CharSequence> args) {
        return futureToSingle(() -> reservedCnx.eval(script, numkeys, keys, args));
    }

    @Override
    public <T> Single<List<T>> evalList(final CharSequence script, final long numkeys,
                                        @RedisProtocolSupport.Key final Collection<? extends CharSequence> keys,
                                        final Collection<? extends CharSequence> args) {
        return futureToSingle(() -> reservedCnx.evalList(script, numkeys, keys, args));
    }

    @Override
    public Single<Long> evalLong(final CharSequence script, final long numkeys,
                                 @RedisProtocolSupport.Key final Collection<? extends CharSequence> keys,
                                 final Collection<? extends CharSequence> args) {
        return futureToSingle(() -> reservedCnx.evalLong(script, numkeys, keys, args));
    }

    @Override
    public Single<String> evalsha(final CharSequence sha1, final long numkeys,
                                  @RedisProtocolSupport.Key final Collection<? extends CharSequence> keys,
                                  final Collection<? extends CharSequence> args) {
        return futureToSingle(() -> reservedCnx.evalsha(sha1, numkeys, keys, args));
    }

    @Override
    public <T> Single<List<T>> evalshaList(final CharSequence sha1, final long numkeys,
                                           @RedisProtocolSupport.Key final Collection<? extends CharSequence> keys,
                                           final Collection<? extends CharSequence> args) {
        return futureToSingle(() -> reservedCnx.evalshaList(sha1, numkeys, keys, args));
    }

    @Override
    public Single<Long> evalshaLong(final CharSequence sha1, final long numkeys,
                                    @RedisProtocolSupport.Key final Collection<? extends CharSequence> keys,
                                    final Collection<? extends CharSequence> args) {
        return futureToSingle(() -> reservedCnx.evalshaLong(sha1, numkeys, keys, args));
    }

    @Override
    public Completable exec() {
        return blockingToCompletable(() -> reservedCnx.exec());
    }

    @Override
    public Single<Long> exists(@RedisProtocolSupport.Key final CharSequence key) {
        return futureToSingle(() -> reservedCnx.exists(key));
    }

    @Override
    public Single<Long> exists(@RedisProtocolSupport.Key final CharSequence key1,
                               @RedisProtocolSupport.Key final CharSequence key2) {
        return futureToSingle(() -> reservedCnx.exists(key1, key2));
    }

    @Override
    public Single<Long> exists(@RedisProtocolSupport.Key final CharSequence key1,
                               @RedisProtocolSupport.Key final CharSequence key2,
                               @RedisProtocolSupport.Key final CharSequence key3) {
        return futureToSingle(() -> reservedCnx.exists(key1, key2, key3));
    }

    @Override
    public Single<Long> exists(@RedisProtocolSupport.Key final Collection<? extends CharSequence> keys) {
        return futureToSingle(() -> reservedCnx.exists(keys));
    }

    @Override
    public Single<Long> expire(@RedisProtocolSupport.Key final CharSequence key, final long seconds) {
        return futureToSingle(() -> reservedCnx.expire(key, seconds));
    }

    @Override
    public Single<Long> expireat(@RedisProtocolSupport.Key final CharSequence key, final long timestamp) {
        return futureToSingle(() -> reservedCnx.expireat(key, timestamp));
    }

    @Override
    public Single<String> flushall() {
        return futureToSingle(() -> reservedCnx.flushall());
    }

    @Override
    public Single<String> flushall(@Nullable final RedisProtocolSupport.FlushallAsync async) {
        return futureToSingle(() -> reservedCnx.flushall(async));
    }

    @Override
    public Single<String> flushdb() {
        return futureToSingle(() -> reservedCnx.flushdb());
    }

    @Override
    public Single<String> flushdb(@Nullable final RedisProtocolSupport.FlushdbAsync async) {
        return futureToSingle(() -> reservedCnx.flushdb(async));
    }

    @Override
    public Single<Long> geoadd(@RedisProtocolSupport.Key final CharSequence key, final double longitude,
                               final double latitude, final CharSequence member) {
        return futureToSingle(() -> reservedCnx.geoadd(key, longitude, latitude, member));
    }

    @Override
    public Single<Long> geoadd(@RedisProtocolSupport.Key final CharSequence key, final double longitude1,
                               final double latitude1, final CharSequence member1, final double longitude2,
                               final double latitude2, final CharSequence member2) {
        return futureToSingle(
                    () -> reservedCnx.geoadd(key, longitude1, latitude1, member1, longitude2, latitude2, member2));
    }

    @Override
    public Single<Long> geoadd(@RedisProtocolSupport.Key final CharSequence key, final double longitude1,
                               final double latitude1, final CharSequence member1, final double longitude2,
                               final double latitude2, final CharSequence member2, final double longitude3,
                               final double latitude3, final CharSequence member3) {
        return futureToSingle(() -> reservedCnx.geoadd(key, longitude1, latitude1, member1, longitude2, latitude2,
                    member2, longitude3, latitude3, member3));
    }

    @Override
    public Single<Long> geoadd(@RedisProtocolSupport.Key final CharSequence key,
                               final Collection<RedisProtocolSupport.LongitudeLatitudeMember> longitudeLatitudeMembers) {
        return futureToSingle(() -> reservedCnx.geoadd(key, longitudeLatitudeMembers));
    }

    @Override
    public Single<Double> geodist(@RedisProtocolSupport.Key final CharSequence key, final CharSequence member1,
                                  final CharSequence member2) {
        return futureToSingle(() -> reservedCnx.geodist(key, member1, member2));
    }

    @Override
    public Single<Double> geodist(@RedisProtocolSupport.Key final CharSequence key, final CharSequence member1,
                                  final CharSequence member2, @Nullable final CharSequence unit) {
        return futureToSingle(() -> reservedCnx.geodist(key, member1, member2, unit));
    }

    @Override
    public <T> Single<List<T>> geohash(@RedisProtocolSupport.Key final CharSequence key, final CharSequence member) {
        return futureToSingle(() -> reservedCnx.geohash(key, member));
    }

    @Override
    public <T> Single<List<T>> geohash(@RedisProtocolSupport.Key final CharSequence key, final CharSequence member1,
                                       final CharSequence member2) {
        return futureToSingle(() -> reservedCnx.geohash(key, member1, member2));
    }

    @Override
    public <T> Single<List<T>> geohash(@RedisProtocolSupport.Key final CharSequence key, final CharSequence member1,
                                       final CharSequence member2, final CharSequence member3) {
        return futureToSingle(() -> reservedCnx.geohash(key, member1, member2, member3));
    }

    @Override
    public <T> Single<List<T>> geohash(@RedisProtocolSupport.Key final CharSequence key,
                                       final Collection<? extends CharSequence> members) {
        return futureToSingle(() -> reservedCnx.geohash(key, members));
    }

    @Override
    public <T> Single<List<T>> geopos(@RedisProtocolSupport.Key final CharSequence key, final CharSequence member) {
        return futureToSingle(() -> reservedCnx.geopos(key, member));
    }

    @Override
    public <T> Single<List<T>> geopos(@RedisProtocolSupport.Key final CharSequence key, final CharSequence member1,
                                      final CharSequence member2) {
        return futureToSingle(() -> reservedCnx.geopos(key, member1, member2));
    }

    @Override
    public <T> Single<List<T>> geopos(@RedisProtocolSupport.Key final CharSequence key, final CharSequence member1,
                                      final CharSequence member2, final CharSequence member3) {
        return futureToSingle(() -> reservedCnx.geopos(key, member1, member2, member3));
    }

    @Override
    public <T> Single<List<T>> geopos(@RedisProtocolSupport.Key final CharSequence key,
                                      final Collection<? extends CharSequence> members) {
        return futureToSingle(() -> reservedCnx.geopos(key, members));
    }

    @Override
    public <T> Single<List<T>> georadius(@RedisProtocolSupport.Key final CharSequence key, final double longitude,
                                         final double latitude, final double radius,
                                         final RedisProtocolSupport.GeoradiusUnit unit) {
        return futureToSingle(() -> reservedCnx.georadius(key, longitude, latitude, radius, unit));
    }

    @Override
    public <T> Single<List<T>> georadius(@RedisProtocolSupport.Key final CharSequence key, final double longitude,
                                         final double latitude, final double radius,
                                         final RedisProtocolSupport.GeoradiusUnit unit,
                                         @Nullable final RedisProtocolSupport.GeoradiusWithcoord withcoord,
                                         @Nullable final RedisProtocolSupport.GeoradiusWithdist withdist,
                                         @Nullable final RedisProtocolSupport.GeoradiusWithhash withhash,
                                         @Nullable final Long count,
                                         @Nullable final RedisProtocolSupport.GeoradiusOrder order,
                                         @Nullable @RedisProtocolSupport.Key final CharSequence storeKey,
                                         @Nullable @RedisProtocolSupport.Key final CharSequence storedistKey) {
        return futureToSingle(() -> reservedCnx.georadius(key, longitude, latitude, radius, unit, withcoord, withdist,
                    withhash, count, order, storeKey, storedistKey));
    }

    @Override
    public <T> Single<List<T>> georadiusbymember(@RedisProtocolSupport.Key final CharSequence key,
                                                 final CharSequence member, final double radius,
                                                 final RedisProtocolSupport.GeoradiusbymemberUnit unit) {
        return futureToSingle(() -> reservedCnx.georadiusbymember(key, member, radius, unit));
    }

    @Override
    public <T> Single<List<T>> georadiusbymember(@RedisProtocolSupport.Key final CharSequence key,
                                                 final CharSequence member, final double radius,
                                                 final RedisProtocolSupport.GeoradiusbymemberUnit unit,
                                                 @Nullable final RedisProtocolSupport.GeoradiusbymemberWithcoord withcoord,
                                                 @Nullable final RedisProtocolSupport.GeoradiusbymemberWithdist withdist,
                                                 @Nullable final RedisProtocolSupport.GeoradiusbymemberWithhash withhash,
                                                 @Nullable final Long count,
                                                 @Nullable final RedisProtocolSupport.GeoradiusbymemberOrder order,
                                                 @Nullable @RedisProtocolSupport.Key final CharSequence storeKey,
                                                 @Nullable @RedisProtocolSupport.Key final CharSequence storedistKey) {
        return futureToSingle(() -> reservedCnx.georadiusbymember(key, member, radius, unit, withcoord, withdist,
                    withhash, count, order, storeKey, storedistKey));
    }

    @Override
    public Single<String> get(@RedisProtocolSupport.Key final CharSequence key) {
        return futureToSingle(() -> reservedCnx.get(key));
    }

    @Override
    public Single<Long> getbit(@RedisProtocolSupport.Key final CharSequence key, final long offset) {
        return futureToSingle(() -> reservedCnx.getbit(key, offset));
    }

    @Override
    public Single<String> getrange(@RedisProtocolSupport.Key final CharSequence key, final long start, final long end) {
        return futureToSingle(() -> reservedCnx.getrange(key, start, end));
    }

    @Override
    public Single<String> getset(@RedisProtocolSupport.Key final CharSequence key, final CharSequence value) {
        return futureToSingle(() -> reservedCnx.getset(key, value));
    }

    @Override
    public Single<Long> hdel(@RedisProtocolSupport.Key final CharSequence key, final CharSequence field) {
        return futureToSingle(() -> reservedCnx.hdel(key, field));
    }

    @Override
    public Single<Long> hdel(@RedisProtocolSupport.Key final CharSequence key, final CharSequence field1,
                             final CharSequence field2) {
        return futureToSingle(() -> reservedCnx.hdel(key, field1, field2));
    }

    @Override
    public Single<Long> hdel(@RedisProtocolSupport.Key final CharSequence key, final CharSequence field1,
                             final CharSequence field2, final CharSequence field3) {
        return futureToSingle(() -> reservedCnx.hdel(key, field1, field2, field3));
    }

    @Override
    public Single<Long> hdel(@RedisProtocolSupport.Key final CharSequence key,
                             final Collection<? extends CharSequence> fields) {
        return futureToSingle(() -> reservedCnx.hdel(key, fields));
    }

    @Override
    public Single<Long> hexists(@RedisProtocolSupport.Key final CharSequence key, final CharSequence field) {
        return futureToSingle(() -> reservedCnx.hexists(key, field));
    }

    @Override
    public Single<String> hget(@RedisProtocolSupport.Key final CharSequence key, final CharSequence field) {
        return futureToSingle(() -> reservedCnx.hget(key, field));
    }

    @Override
    public <T> Single<List<T>> hgetall(@RedisProtocolSupport.Key final CharSequence key) {
        return futureToSingle(() -> reservedCnx.hgetall(key));
    }

    @Override
    public Single<Long> hincrby(@RedisProtocolSupport.Key final CharSequence key, final CharSequence field,
                                final long increment) {
        return futureToSingle(() -> reservedCnx.hincrby(key, field, increment));
    }

    @Override
    public Single<Double> hincrbyfloat(@RedisProtocolSupport.Key final CharSequence key, final CharSequence field,
                                       final double increment) {
        return futureToSingle(() -> reservedCnx.hincrbyfloat(key, field, increment));
    }

    @Override
    public <T> Single<List<T>> hkeys(@RedisProtocolSupport.Key final CharSequence key) {
        return futureToSingle(() -> reservedCnx.hkeys(key));
    }

    @Override
    public Single<Long> hlen(@RedisProtocolSupport.Key final CharSequence key) {
        return futureToSingle(() -> reservedCnx.hlen(key));
    }

    @Override
    public <T> Single<List<T>> hmget(@RedisProtocolSupport.Key final CharSequence key, final CharSequence field) {
        return futureToSingle(() -> reservedCnx.hmget(key, field));
    }

    @Override
    public <T> Single<List<T>> hmget(@RedisProtocolSupport.Key final CharSequence key, final CharSequence field1,
                                     final CharSequence field2) {
        return futureToSingle(() -> reservedCnx.hmget(key, field1, field2));
    }

    @Override
    public <T> Single<List<T>> hmget(@RedisProtocolSupport.Key final CharSequence key, final CharSequence field1,
                                     final CharSequence field2, final CharSequence field3) {
        return futureToSingle(() -> reservedCnx.hmget(key, field1, field2, field3));
    }

    @Override
    public <T> Single<List<T>> hmget(@RedisProtocolSupport.Key final CharSequence key,
                                     final Collection<? extends CharSequence> fields) {
        return futureToSingle(() -> reservedCnx.hmget(key, fields));
    }

    @Override
    public Single<String> hmset(@RedisProtocolSupport.Key final CharSequence key, final CharSequence field,
                                final CharSequence value) {
        return futureToSingle(() -> reservedCnx.hmset(key, field, value));
    }

    @Override
    public Single<String> hmset(@RedisProtocolSupport.Key final CharSequence key, final CharSequence field1,
                                final CharSequence value1, final CharSequence field2, final CharSequence value2) {
        return futureToSingle(() -> reservedCnx.hmset(key, field1, value1, field2, value2));
    }

    @Override
    public Single<String> hmset(@RedisProtocolSupport.Key final CharSequence key, final CharSequence field1,
                                final CharSequence value1, final CharSequence field2, final CharSequence value2,
                                final CharSequence field3, final CharSequence value3) {
        return futureToSingle(() -> reservedCnx.hmset(key, field1, value1, field2, value2, field3, value3));
    }

    @Override
    public Single<String> hmset(@RedisProtocolSupport.Key final CharSequence key,
                                final Collection<RedisProtocolSupport.FieldValue> fieldValues) {
        return futureToSingle(() -> reservedCnx.hmset(key, fieldValues));
    }

    @Override
    public <T> Single<List<T>> hscan(@RedisProtocolSupport.Key final CharSequence key, final long cursor) {
        return futureToSingle(() -> reservedCnx.hscan(key, cursor));
    }

    @Override
    public <T> Single<List<T>> hscan(@RedisProtocolSupport.Key final CharSequence key, final long cursor,
                                     @Nullable final CharSequence matchPattern, @Nullable final Long count) {
        return futureToSingle(() -> reservedCnx.hscan(key, cursor, matchPattern, count));
    }

    @Override
    public Single<Long> hset(@RedisProtocolSupport.Key final CharSequence key, final CharSequence field,
                             final CharSequence value) {
        return futureToSingle(() -> reservedCnx.hset(key, field, value));
    }

    @Override
    public Single<Long> hsetnx(@RedisProtocolSupport.Key final CharSequence key, final CharSequence field,
                               final CharSequence value) {
        return futureToSingle(() -> reservedCnx.hsetnx(key, field, value));
    }

    @Override
    public Single<Long> hstrlen(@RedisProtocolSupport.Key final CharSequence key, final CharSequence field) {
        return futureToSingle(() -> reservedCnx.hstrlen(key, field));
    }

    @Override
    public <T> Single<List<T>> hvals(@RedisProtocolSupport.Key final CharSequence key) {
        return futureToSingle(() -> reservedCnx.hvals(key));
    }

    @Override
    public Single<Long> incr(@RedisProtocolSupport.Key final CharSequence key) {
        return futureToSingle(() -> reservedCnx.incr(key));
    }

    @Override
    public Single<Long> incrby(@RedisProtocolSupport.Key final CharSequence key, final long increment) {
        return futureToSingle(() -> reservedCnx.incrby(key, increment));
    }

    @Override
    public Single<Double> incrbyfloat(@RedisProtocolSupport.Key final CharSequence key, final double increment) {
        return futureToSingle(() -> reservedCnx.incrbyfloat(key, increment));
    }

    @Override
    public Single<String> info() {
        return futureToSingle(() -> reservedCnx.info());
    }

    @Override
    public Single<String> info(@Nullable final CharSequence section) {
        return futureToSingle(() -> reservedCnx.info(section));
    }

    @Override
    public <T> Single<List<T>> keys(final CharSequence pattern) {
        return futureToSingle(() -> reservedCnx.keys(pattern));
    }

    @Override
    public Single<Long> lastsave() {
        return futureToSingle(() -> reservedCnx.lastsave());
    }

    @Override
    public Single<String> lindex(@RedisProtocolSupport.Key final CharSequence key, final long index) {
        return futureToSingle(() -> reservedCnx.lindex(key, index));
    }

    @Override
    public Single<Long> linsert(@RedisProtocolSupport.Key final CharSequence key,
                                final RedisProtocolSupport.LinsertWhere where, final CharSequence pivot,
                                final CharSequence value) {
        return futureToSingle(() -> reservedCnx.linsert(key, where, pivot, value));
    }

    @Override
    public Single<Long> llen(@RedisProtocolSupport.Key final CharSequence key) {
        return futureToSingle(() -> reservedCnx.llen(key));
    }

    @Override
    public Single<String> lpop(@RedisProtocolSupport.Key final CharSequence key) {
        return futureToSingle(() -> reservedCnx.lpop(key));
    }

    @Override
    public Single<Long> lpush(@RedisProtocolSupport.Key final CharSequence key, final CharSequence value) {
        return futureToSingle(() -> reservedCnx.lpush(key, value));
    }

    @Override
    public Single<Long> lpush(@RedisProtocolSupport.Key final CharSequence key, final CharSequence value1,
                              final CharSequence value2) {
        return futureToSingle(() -> reservedCnx.lpush(key, value1, value2));
    }

    @Override
    public Single<Long> lpush(@RedisProtocolSupport.Key final CharSequence key, final CharSequence value1,
                              final CharSequence value2, final CharSequence value3) {
        return futureToSingle(() -> reservedCnx.lpush(key, value1, value2, value3));
    }

    @Override
    public Single<Long> lpush(@RedisProtocolSupport.Key final CharSequence key,
                              final Collection<? extends CharSequence> values) {
        return futureToSingle(() -> reservedCnx.lpush(key, values));
    }

    @Override
    public Single<Long> lpushx(@RedisProtocolSupport.Key final CharSequence key, final CharSequence value) {
        return futureToSingle(() -> reservedCnx.lpushx(key, value));
    }

    @Override
    public <T> Single<List<T>> lrange(@RedisProtocolSupport.Key final CharSequence key, final long start,
                                      final long stop) {
        return futureToSingle(() -> reservedCnx.lrange(key, start, stop));
    }

    @Override
    public Single<Long> lrem(@RedisProtocolSupport.Key final CharSequence key, final long count,
                             final CharSequence value) {
        return futureToSingle(() -> reservedCnx.lrem(key, count, value));
    }

    @Override
    public Single<String> lset(@RedisProtocolSupport.Key final CharSequence key, final long index,
                               final CharSequence value) {
        return futureToSingle(() -> reservedCnx.lset(key, index, value));
    }

    @Override
    public Single<String> ltrim(@RedisProtocolSupport.Key final CharSequence key, final long start, final long stop) {
        return futureToSingle(() -> reservedCnx.ltrim(key, start, stop));
    }

    @Override
    public Single<String> memoryDoctor() {
        return futureToSingle(() -> reservedCnx.memoryDoctor());
    }

    @Override
    public <T> Single<List<T>> memoryHelp() {
        return futureToSingle(() -> reservedCnx.memoryHelp());
    }

    @Override
    public Single<String> memoryMallocStats() {
        return futureToSingle(() -> reservedCnx.memoryMallocStats());
    }

    @Override
    public Single<String> memoryPurge() {
        return futureToSingle(() -> reservedCnx.memoryPurge());
    }

    @Override
    public <T> Single<List<T>> memoryStats() {
        return futureToSingle(() -> reservedCnx.memoryStats());
    }

    @Override
    public Single<Long> memoryUsage(@RedisProtocolSupport.Key final CharSequence key) {
        return futureToSingle(() -> reservedCnx.memoryUsage(key));
    }

    @Override
    public Single<Long> memoryUsage(@RedisProtocolSupport.Key final CharSequence key,
                                    @Nullable final Long samplesCount) {
        return futureToSingle(() -> reservedCnx.memoryUsage(key, samplesCount));
    }

    @Override
    public <T> Single<List<T>> mget(@RedisProtocolSupport.Key final CharSequence key) {
        return futureToSingle(() -> reservedCnx.mget(key));
    }

    @Override
    public <T> Single<List<T>> mget(@RedisProtocolSupport.Key final CharSequence key1,
                                    @RedisProtocolSupport.Key final CharSequence key2) {
        return futureToSingle(() -> reservedCnx.mget(key1, key2));
    }

    @Override
    public <T> Single<List<T>> mget(@RedisProtocolSupport.Key final CharSequence key1,
                                    @RedisProtocolSupport.Key final CharSequence key2,
                                    @RedisProtocolSupport.Key final CharSequence key3) {
        return futureToSingle(() -> reservedCnx.mget(key1, key2, key3));
    }

    @Override
    public <T> Single<List<T>> mget(@RedisProtocolSupport.Key final Collection<? extends CharSequence> keys) {
        return futureToSingle(() -> reservedCnx.mget(keys));
    }

    @Override
    public Single<Long> move(@RedisProtocolSupport.Key final CharSequence key, final long db) {
        return futureToSingle(() -> reservedCnx.move(key, db));
    }

    @Override
    public Single<String> mset(@RedisProtocolSupport.Key final CharSequence key, final CharSequence value) {
        return futureToSingle(() -> reservedCnx.mset(key, value));
    }

    @Override
    public Single<String> mset(@RedisProtocolSupport.Key final CharSequence key1, final CharSequence value1,
                               @RedisProtocolSupport.Key final CharSequence key2, final CharSequence value2) {
        return futureToSingle(() -> reservedCnx.mset(key1, value1, key2, value2));
    }

    @Override
    public Single<String> mset(@RedisProtocolSupport.Key final CharSequence key1, final CharSequence value1,
                               @RedisProtocolSupport.Key final CharSequence key2, final CharSequence value2,
                               @RedisProtocolSupport.Key final CharSequence key3, final CharSequence value3) {
        return futureToSingle(() -> reservedCnx.mset(key1, value1, key2, value2, key3, value3));
    }

    @Override
    public Single<String> mset(final Collection<RedisProtocolSupport.KeyValue> keyValues) {
        return futureToSingle(() -> reservedCnx.mset(keyValues));
    }

    @Override
    public Single<Long> msetnx(@RedisProtocolSupport.Key final CharSequence key, final CharSequence value) {
        return futureToSingle(() -> reservedCnx.msetnx(key, value));
    }

    @Override
    public Single<Long> msetnx(@RedisProtocolSupport.Key final CharSequence key1, final CharSequence value1,
                               @RedisProtocolSupport.Key final CharSequence key2, final CharSequence value2) {
        return futureToSingle(() -> reservedCnx.msetnx(key1, value1, key2, value2));
    }

    @Override
    public Single<Long> msetnx(@RedisProtocolSupport.Key final CharSequence key1, final CharSequence value1,
                               @RedisProtocolSupport.Key final CharSequence key2, final CharSequence value2,
                               @RedisProtocolSupport.Key final CharSequence key3, final CharSequence value3) {
        return futureToSingle(() -> reservedCnx.msetnx(key1, value1, key2, value2, key3, value3));
    }

    @Override
    public Single<Long> msetnx(final Collection<RedisProtocolSupport.KeyValue> keyValues) {
        return futureToSingle(() -> reservedCnx.msetnx(keyValues));
    }

    @Override
    public Single<String> objectEncoding(@RedisProtocolSupport.Key final CharSequence key) {
        return futureToSingle(() -> reservedCnx.objectEncoding(key));
    }

    @Override
    public Single<Long> objectFreq(@RedisProtocolSupport.Key final CharSequence key) {
        return futureToSingle(() -> reservedCnx.objectFreq(key));
    }

    @Override
    public Single<List<String>> objectHelp() {
        return futureToSingle(() -> reservedCnx.objectHelp());
    }

    @Override
    public Single<Long> objectIdletime(@RedisProtocolSupport.Key final CharSequence key) {
        return futureToSingle(() -> reservedCnx.objectIdletime(key));
    }

    @Override
    public Single<Long> objectRefcount(@RedisProtocolSupport.Key final CharSequence key) {
        return futureToSingle(() -> reservedCnx.objectRefcount(key));
    }

    @Override
    public Single<Long> persist(@RedisProtocolSupport.Key final CharSequence key) {
        return futureToSingle(() -> reservedCnx.persist(key));
    }

    @Override
    public Single<Long> pexpire(@RedisProtocolSupport.Key final CharSequence key, final long milliseconds) {
        return futureToSingle(() -> reservedCnx.pexpire(key, milliseconds));
    }

    @Override
    public Single<Long> pexpireat(@RedisProtocolSupport.Key final CharSequence key, final long millisecondsTimestamp) {
        return futureToSingle(() -> reservedCnx.pexpireat(key, millisecondsTimestamp));
    }

    @Override
    public Single<Long> pfadd(@RedisProtocolSupport.Key final CharSequence key, final CharSequence element) {
        return futureToSingle(() -> reservedCnx.pfadd(key, element));
    }

    @Override
    public Single<Long> pfadd(@RedisProtocolSupport.Key final CharSequence key, final CharSequence element1,
                              final CharSequence element2) {
        return futureToSingle(() -> reservedCnx.pfadd(key, element1, element2));
    }

    @Override
    public Single<Long> pfadd(@RedisProtocolSupport.Key final CharSequence key, final CharSequence element1,
                              final CharSequence element2, final CharSequence element3) {
        return futureToSingle(() -> reservedCnx.pfadd(key, element1, element2, element3));
    }

    @Override
    public Single<Long> pfadd(@RedisProtocolSupport.Key final CharSequence key,
                              final Collection<? extends CharSequence> elements) {
        return futureToSingle(() -> reservedCnx.pfadd(key, elements));
    }

    @Override
    public Single<Long> pfcount(@RedisProtocolSupport.Key final CharSequence key) {
        return futureToSingle(() -> reservedCnx.pfcount(key));
    }

    @Override
    public Single<Long> pfcount(@RedisProtocolSupport.Key final CharSequence key1,
                                @RedisProtocolSupport.Key final CharSequence key2) {
        return futureToSingle(() -> reservedCnx.pfcount(key1, key2));
    }

    @Override
    public Single<Long> pfcount(@RedisProtocolSupport.Key final CharSequence key1,
                                @RedisProtocolSupport.Key final CharSequence key2,
                                @RedisProtocolSupport.Key final CharSequence key3) {
        return futureToSingle(() -> reservedCnx.pfcount(key1, key2, key3));
    }

    @Override
    public Single<Long> pfcount(@RedisProtocolSupport.Key final Collection<? extends CharSequence> keys) {
        return futureToSingle(() -> reservedCnx.pfcount(keys));
    }

    @Override
    public Single<String> pfmerge(@RedisProtocolSupport.Key final CharSequence destkey,
                                  @RedisProtocolSupport.Key final CharSequence sourcekey) {
        return futureToSingle(() -> reservedCnx.pfmerge(destkey, sourcekey));
    }

    @Override
    public Single<String> pfmerge(@RedisProtocolSupport.Key final CharSequence destkey,
                                  @RedisProtocolSupport.Key final CharSequence sourcekey1,
                                  @RedisProtocolSupport.Key final CharSequence sourcekey2) {
        return futureToSingle(() -> reservedCnx.pfmerge(destkey, sourcekey1, sourcekey2));
    }

    @Override
    public Single<String> pfmerge(@RedisProtocolSupport.Key final CharSequence destkey,
                                  @RedisProtocolSupport.Key final CharSequence sourcekey1,
                                  @RedisProtocolSupport.Key final CharSequence sourcekey2,
                                  @RedisProtocolSupport.Key final CharSequence sourcekey3) {
        return futureToSingle(() -> reservedCnx.pfmerge(destkey, sourcekey1, sourcekey2, sourcekey3));
    }

    @Override
    public Single<String> pfmerge(@RedisProtocolSupport.Key final CharSequence destkey,
                                  @RedisProtocolSupport.Key final Collection<? extends CharSequence> sourcekeys) {
        return futureToSingle(() -> reservedCnx.pfmerge(destkey, sourcekeys));
    }

    @Override
    public Single<String> ping() {
        return futureToSingle(() -> reservedCnx.ping());
    }

    @Override
    public Single<String> ping(final CharSequence message) {
        return futureToSingle(() -> reservedCnx.ping(message));
    }

    @Override
    public Single<String> psetex(@RedisProtocolSupport.Key final CharSequence key, final long milliseconds,
                                 final CharSequence value) {
        return futureToSingle(() -> reservedCnx.psetex(key, milliseconds, value));
    }

    @Override
    public Single<Long> pttl(@RedisProtocolSupport.Key final CharSequence key) {
        return futureToSingle(() -> reservedCnx.pttl(key));
    }

    @Override
    public Single<Long> publish(final CharSequence channel, final CharSequence message) {
        return futureToSingle(() -> reservedCnx.publish(channel, message));
    }

    @Override
    public Single<List<String>> pubsubChannels() {
        return futureToSingle(() -> reservedCnx.pubsubChannels());
    }

    @Override
    public Single<List<String>> pubsubChannels(@Nullable final CharSequence pattern) {
        return futureToSingle(() -> reservedCnx.pubsubChannels(pattern));
    }

    @Override
    public Single<List<String>> pubsubChannels(@Nullable final CharSequence pattern1,
                                               @Nullable final CharSequence pattern2) {
        return futureToSingle(() -> reservedCnx.pubsubChannels(pattern1, pattern2));
    }

    @Override
    public Single<List<String>> pubsubChannels(@Nullable final CharSequence pattern1,
                                               @Nullable final CharSequence pattern2,
                                               @Nullable final CharSequence pattern3) {
        return futureToSingle(() -> reservedCnx.pubsubChannels(pattern1, pattern2, pattern3));
    }

    @Override
    public Single<List<String>> pubsubChannels(final Collection<? extends CharSequence> patterns) {
        return futureToSingle(() -> reservedCnx.pubsubChannels(patterns));
    }

    @Override
    public <T> Single<List<T>> pubsubNumsub() {
        return futureToSingle(() -> reservedCnx.pubsubNumsub());
    }

    @Override
    public <T> Single<List<T>> pubsubNumsub(@Nullable final CharSequence channel) {
        return futureToSingle(() -> reservedCnx.pubsubNumsub(channel));
    }

    @Override
    public <T> Single<List<T>> pubsubNumsub(@Nullable final CharSequence channel1,
                                            @Nullable final CharSequence channel2) {
        return futureToSingle(() -> reservedCnx.pubsubNumsub(channel1, channel2));
    }

    @Override
    public <T> Single<List<T>> pubsubNumsub(@Nullable final CharSequence channel1,
                                            @Nullable final CharSequence channel2,
                                            @Nullable final CharSequence channel3) {
        return futureToSingle(() -> reservedCnx.pubsubNumsub(channel1, channel2, channel3));
    }

    @Override
    public <T> Single<List<T>> pubsubNumsub(final Collection<? extends CharSequence> channels) {
        return futureToSingle(() -> reservedCnx.pubsubNumsub(channels));
    }

    @Override
    public Single<Long> pubsubNumpat() {
        return futureToSingle(() -> reservedCnx.pubsubNumpat());
    }

    @Override
    public Single<String> randomkey() {
        return futureToSingle(() -> reservedCnx.randomkey());
    }

    @Override
    public Single<String> readonly() {
        return futureToSingle(() -> reservedCnx.readonly());
    }

    @Override
    public Single<String> readwrite() {
        return futureToSingle(() -> reservedCnx.readwrite());
    }

    @Override
    public Single<String> rename(@RedisProtocolSupport.Key final CharSequence key,
                                 @RedisProtocolSupport.Key final CharSequence newkey) {
        return futureToSingle(() -> reservedCnx.rename(key, newkey));
    }

    @Override
    public Single<Long> renamenx(@RedisProtocolSupport.Key final CharSequence key,
                                 @RedisProtocolSupport.Key final CharSequence newkey) {
        return futureToSingle(() -> reservedCnx.renamenx(key, newkey));
    }

    @Override
    public Single<String> restore(@RedisProtocolSupport.Key final CharSequence key, final long ttl,
                                  final CharSequence serializedValue) {
        return futureToSingle(() -> reservedCnx.restore(key, ttl, serializedValue));
    }

    @Override
    public Single<String> restore(@RedisProtocolSupport.Key final CharSequence key, final long ttl,
                                  final CharSequence serializedValue,
                                  @Nullable final RedisProtocolSupport.RestoreReplace replace) {
        return futureToSingle(() -> reservedCnx.restore(key, ttl, serializedValue, replace));
    }

    @Override
    public <T> Single<List<T>> role() {
        return futureToSingle(() -> reservedCnx.role());
    }

    @Override
    public Single<String> rpop(@RedisProtocolSupport.Key final CharSequence key) {
        return futureToSingle(() -> reservedCnx.rpop(key));
    }

    @Override
    public Single<String> rpoplpush(@RedisProtocolSupport.Key final CharSequence source,
                                    @RedisProtocolSupport.Key final CharSequence destination) {
        return futureToSingle(() -> reservedCnx.rpoplpush(source, destination));
    }

    @Override
    public Single<Long> rpush(@RedisProtocolSupport.Key final CharSequence key, final CharSequence value) {
        return futureToSingle(() -> reservedCnx.rpush(key, value));
    }

    @Override
    public Single<Long> rpush(@RedisProtocolSupport.Key final CharSequence key, final CharSequence value1,
                              final CharSequence value2) {
        return futureToSingle(() -> reservedCnx.rpush(key, value1, value2));
    }

    @Override
    public Single<Long> rpush(@RedisProtocolSupport.Key final CharSequence key, final CharSequence value1,
                              final CharSequence value2, final CharSequence value3) {
        return futureToSingle(() -> reservedCnx.rpush(key, value1, value2, value3));
    }

    @Override
    public Single<Long> rpush(@RedisProtocolSupport.Key final CharSequence key,
                              final Collection<? extends CharSequence> values) {
        return futureToSingle(() -> reservedCnx.rpush(key, values));
    }

    @Override
    public Single<Long> rpushx(@RedisProtocolSupport.Key final CharSequence key, final CharSequence value) {
        return futureToSingle(() -> reservedCnx.rpushx(key, value));
    }

    @Override
    public Single<Long> sadd(@RedisProtocolSupport.Key final CharSequence key, final CharSequence member) {
        return futureToSingle(() -> reservedCnx.sadd(key, member));
    }

    @Override
    public Single<Long> sadd(@RedisProtocolSupport.Key final CharSequence key, final CharSequence member1,
                             final CharSequence member2) {
        return futureToSingle(() -> reservedCnx.sadd(key, member1, member2));
    }

    @Override
    public Single<Long> sadd(@RedisProtocolSupport.Key final CharSequence key, final CharSequence member1,
                             final CharSequence member2, final CharSequence member3) {
        return futureToSingle(() -> reservedCnx.sadd(key, member1, member2, member3));
    }

    @Override
    public Single<Long> sadd(@RedisProtocolSupport.Key final CharSequence key,
                             final Collection<? extends CharSequence> members) {
        return futureToSingle(() -> reservedCnx.sadd(key, members));
    }

    @Override
    public Single<String> save() {
        return futureToSingle(() -> reservedCnx.save());
    }

    @Override
    public <T> Single<List<T>> scan(final long cursor) {
        return futureToSingle(() -> reservedCnx.scan(cursor));
    }

    @Override
    public <T> Single<List<T>> scan(final long cursor, @Nullable final CharSequence matchPattern,
                                    @Nullable final Long count) {
        return futureToSingle(() -> reservedCnx.scan(cursor, matchPattern, count));
    }

    @Override
    public Single<Long> scard(@RedisProtocolSupport.Key final CharSequence key) {
        return futureToSingle(() -> reservedCnx.scard(key));
    }

    @Override
    public Single<String> scriptDebug(final RedisProtocolSupport.ScriptDebugMode mode) {
        return futureToSingle(() -> reservedCnx.scriptDebug(mode));
    }

    @Override
    public <T> Single<List<T>> scriptExists(final CharSequence sha1) {
        return futureToSingle(() -> reservedCnx.scriptExists(sha1));
    }

    @Override
    public <T> Single<List<T>> scriptExists(final CharSequence sha11, final CharSequence sha12) {
        return futureToSingle(() -> reservedCnx.scriptExists(sha11, sha12));
    }

    @Override
    public <T> Single<List<T>> scriptExists(final CharSequence sha11, final CharSequence sha12,
                                            final CharSequence sha13) {
        return futureToSingle(() -> reservedCnx.scriptExists(sha11, sha12, sha13));
    }

    @Override
    public <T> Single<List<T>> scriptExists(final Collection<? extends CharSequence> sha1s) {
        return futureToSingle(() -> reservedCnx.scriptExists(sha1s));
    }

    @Override
    public Single<String> scriptFlush() {
        return futureToSingle(() -> reservedCnx.scriptFlush());
    }

    @Override
    public Single<String> scriptKill() {
        return futureToSingle(() -> reservedCnx.scriptKill());
    }

    @Override
    public Single<String> scriptLoad(final CharSequence script) {
        return futureToSingle(() -> reservedCnx.scriptLoad(script));
    }

    @Override
    public <T> Single<List<T>> sdiff(@RedisProtocolSupport.Key final CharSequence firstkey) {
        return futureToSingle(() -> reservedCnx.sdiff(firstkey));
    }

    @Override
    public <T> Single<List<T>> sdiff(@RedisProtocolSupport.Key final CharSequence firstkey,
                                     @Nullable @RedisProtocolSupport.Key final CharSequence otherkey) {
        return futureToSingle(() -> reservedCnx.sdiff(firstkey, otherkey));
    }

    @Override
    public <T> Single<List<T>> sdiff(@RedisProtocolSupport.Key final CharSequence firstkey,
                                     @Nullable @RedisProtocolSupport.Key final CharSequence otherkey1,
                                     @Nullable @RedisProtocolSupport.Key final CharSequence otherkey2) {
        return futureToSingle(() -> reservedCnx.sdiff(firstkey, otherkey1, otherkey2));
    }

    @Override
    public <T> Single<List<T>> sdiff(@RedisProtocolSupport.Key final CharSequence firstkey,
                                     @Nullable @RedisProtocolSupport.Key final CharSequence otherkey1,
                                     @Nullable @RedisProtocolSupport.Key final CharSequence otherkey2,
                                     @Nullable @RedisProtocolSupport.Key final CharSequence otherkey3) {
        return futureToSingle(() -> reservedCnx.sdiff(firstkey, otherkey1, otherkey2, otherkey3));
    }

    @Override
    public <T> Single<List<T>> sdiff(@RedisProtocolSupport.Key final CharSequence firstkey,
                                     @RedisProtocolSupport.Key final Collection<? extends CharSequence> otherkeys) {
        return futureToSingle(() -> reservedCnx.sdiff(firstkey, otherkeys));
    }

    @Override
    public Single<Long> sdiffstore(@RedisProtocolSupport.Key final CharSequence destination,
                                   @RedisProtocolSupport.Key final CharSequence firstkey) {
        return futureToSingle(() -> reservedCnx.sdiffstore(destination, firstkey));
    }

    @Override
    public Single<Long> sdiffstore(@RedisProtocolSupport.Key final CharSequence destination,
                                   @RedisProtocolSupport.Key final CharSequence firstkey,
                                   @Nullable @RedisProtocolSupport.Key final CharSequence otherkey) {
        return futureToSingle(() -> reservedCnx.sdiffstore(destination, firstkey, otherkey));
    }

    @Override
    public Single<Long> sdiffstore(@RedisProtocolSupport.Key final CharSequence destination,
                                   @RedisProtocolSupport.Key final CharSequence firstkey,
                                   @Nullable @RedisProtocolSupport.Key final CharSequence otherkey1,
                                   @Nullable @RedisProtocolSupport.Key final CharSequence otherkey2) {
        return futureToSingle(() -> reservedCnx.sdiffstore(destination, firstkey, otherkey1, otherkey2));
    }

    @Override
    public Single<Long> sdiffstore(@RedisProtocolSupport.Key final CharSequence destination,
                                   @RedisProtocolSupport.Key final CharSequence firstkey,
                                   @Nullable @RedisProtocolSupport.Key final CharSequence otherkey1,
                                   @Nullable @RedisProtocolSupport.Key final CharSequence otherkey2,
                                   @Nullable @RedisProtocolSupport.Key final CharSequence otherkey3) {
        return futureToSingle(() -> reservedCnx.sdiffstore(destination, firstkey, otherkey1, otherkey2, otherkey3));
    }

    @Override
    public Single<Long> sdiffstore(@RedisProtocolSupport.Key final CharSequence destination,
                                   @RedisProtocolSupport.Key final CharSequence firstkey,
                                   @RedisProtocolSupport.Key final Collection<? extends CharSequence> otherkeys) {
        return futureToSingle(() -> reservedCnx.sdiffstore(destination, firstkey, otherkeys));
    }

    @Override
    public Single<String> select(final long index) {
        return futureToSingle(() -> reservedCnx.select(index));
    }

    @Override
    public Single<String> set(@RedisProtocolSupport.Key final CharSequence key, final CharSequence value) {
        return futureToSingle(() -> reservedCnx.set(key, value));
    }

    @Override
    public Single<String> set(@RedisProtocolSupport.Key final CharSequence key, final CharSequence value,
                              @Nullable final RedisProtocolSupport.ExpireDuration expireDuration,
                              @Nullable final RedisProtocolSupport.SetCondition condition) {
        return futureToSingle(() -> reservedCnx.set(key, value, expireDuration, condition));
    }

    @Override
    public Single<Long> setbit(@RedisProtocolSupport.Key final CharSequence key, final long offset,
                               final CharSequence value) {
        return futureToSingle(() -> reservedCnx.setbit(key, offset, value));
    }

    @Override
    public Single<String> setex(@RedisProtocolSupport.Key final CharSequence key, final long seconds,
                                final CharSequence value) {
        return futureToSingle(() -> reservedCnx.setex(key, seconds, value));
    }

    @Override
    public Single<Long> setnx(@RedisProtocolSupport.Key final CharSequence key, final CharSequence value) {
        return futureToSingle(() -> reservedCnx.setnx(key, value));
    }

    @Override
    public Single<Long> setrange(@RedisProtocolSupport.Key final CharSequence key, final long offset,
                                 final CharSequence value) {
        return futureToSingle(() -> reservedCnx.setrange(key, offset, value));
    }

    @Override
    public Single<String> shutdown() {
        return futureToSingle(() -> reservedCnx.shutdown());
    }

    @Override
    public Single<String> shutdown(@Nullable final RedisProtocolSupport.ShutdownSaveMode saveMode) {
        return futureToSingle(() -> reservedCnx.shutdown(saveMode));
    }

    @Override
    public <T> Single<List<T>> sinter(@RedisProtocolSupport.Key final CharSequence key) {
        return futureToSingle(() -> reservedCnx.sinter(key));
    }

    @Override
    public <T> Single<List<T>> sinter(@RedisProtocolSupport.Key final CharSequence key1,
                                      @RedisProtocolSupport.Key final CharSequence key2) {
        return futureToSingle(() -> reservedCnx.sinter(key1, key2));
    }

    @Override
    public <T> Single<List<T>> sinter(@RedisProtocolSupport.Key final CharSequence key1,
                                      @RedisProtocolSupport.Key final CharSequence key2,
                                      @RedisProtocolSupport.Key final CharSequence key3) {
        return futureToSingle(() -> reservedCnx.sinter(key1, key2, key3));
    }

    @Override
    public <T> Single<List<T>> sinter(@RedisProtocolSupport.Key final Collection<? extends CharSequence> keys) {
        return futureToSingle(() -> reservedCnx.sinter(keys));
    }

    @Override
    public Single<Long> sinterstore(@RedisProtocolSupport.Key final CharSequence destination,
                                    @RedisProtocolSupport.Key final CharSequence key) {
        return futureToSingle(() -> reservedCnx.sinterstore(destination, key));
    }

    @Override
    public Single<Long> sinterstore(@RedisProtocolSupport.Key final CharSequence destination,
                                    @RedisProtocolSupport.Key final CharSequence key1,
                                    @RedisProtocolSupport.Key final CharSequence key2) {
        return futureToSingle(() -> reservedCnx.sinterstore(destination, key1, key2));
    }

    @Override
    public Single<Long> sinterstore(@RedisProtocolSupport.Key final CharSequence destination,
                                    @RedisProtocolSupport.Key final CharSequence key1,
                                    @RedisProtocolSupport.Key final CharSequence key2,
                                    @RedisProtocolSupport.Key final CharSequence key3) {
        return futureToSingle(() -> reservedCnx.sinterstore(destination, key1, key2, key3));
    }

    @Override
    public Single<Long> sinterstore(@RedisProtocolSupport.Key final CharSequence destination,
                                    @RedisProtocolSupport.Key final Collection<? extends CharSequence> keys) {
        return futureToSingle(() -> reservedCnx.sinterstore(destination, keys));
    }

    @Override
    public Single<Long> sismember(@RedisProtocolSupport.Key final CharSequence key, final CharSequence member) {
        return futureToSingle(() -> reservedCnx.sismember(key, member));
    }

    @Override
    public Single<String> slaveof(final CharSequence host, final CharSequence port) {
        return futureToSingle(() -> reservedCnx.slaveof(host, port));
    }

    @Override
    public <T> Single<List<T>> slowlog(final CharSequence subcommand) {
        return futureToSingle(() -> reservedCnx.slowlog(subcommand));
    }

    @Override
    public <T> Single<List<T>> slowlog(final CharSequence subcommand, @Nullable final CharSequence argument) {
        return futureToSingle(() -> reservedCnx.slowlog(subcommand, argument));
    }

    @Override
    public <T> Single<List<T>> smembers(@RedisProtocolSupport.Key final CharSequence key) {
        return futureToSingle(() -> reservedCnx.smembers(key));
    }

    @Override
    public Single<Long> smove(@RedisProtocolSupport.Key final CharSequence source,
                              @RedisProtocolSupport.Key final CharSequence destination, final CharSequence member) {
        return futureToSingle(() -> reservedCnx.smove(source, destination, member));
    }

    @Override
    public <T> Single<List<T>> sort(@RedisProtocolSupport.Key final CharSequence key) {
        return futureToSingle(() -> reservedCnx.sort(key));
    }

    @Override
    public <T> Single<List<T>> sort(@RedisProtocolSupport.Key final CharSequence key,
                                    @Nullable final CharSequence byPattern,
                                    @Nullable final RedisProtocolSupport.OffsetCount offsetCount,
                                    final Collection<? extends CharSequence> getPatterns,
                                    @Nullable final RedisProtocolSupport.SortOrder order,
                                    @Nullable final RedisProtocolSupport.SortSorting sorting) {
        return futureToSingle(() -> reservedCnx.sort(key, byPattern, offsetCount, getPatterns, order, sorting));
    }

    @Override
    public Single<Long> sort(@RedisProtocolSupport.Key final CharSequence key,
                             @RedisProtocolSupport.Key final CharSequence storeDestination) {
        return futureToSingle(() -> reservedCnx.sort(key, storeDestination));
    }

    @Override
    public Single<Long> sort(@RedisProtocolSupport.Key final CharSequence key,
                             @RedisProtocolSupport.Key final CharSequence storeDestination,
                             @Nullable final CharSequence byPattern,
                             @Nullable final RedisProtocolSupport.OffsetCount offsetCount,
                             final Collection<? extends CharSequence> getPatterns,
                             @Nullable final RedisProtocolSupport.SortOrder order,
                             @Nullable final RedisProtocolSupport.SortSorting sorting) {
        return futureToSingle(
                    () -> reservedCnx.sort(key, storeDestination, byPattern, offsetCount, getPatterns, order, sorting));
    }

    @Override
    public Single<String> spop(@RedisProtocolSupport.Key final CharSequence key) {
        return futureToSingle(() -> reservedCnx.spop(key));
    }

    @Override
    public Single<String> spop(@RedisProtocolSupport.Key final CharSequence key, @Nullable final Long count) {
        return futureToSingle(() -> reservedCnx.spop(key, count));
    }

    @Override
    public Single<String> srandmember(@RedisProtocolSupport.Key final CharSequence key) {
        return futureToSingle(() -> reservedCnx.srandmember(key));
    }

    @Override
    public Single<List<String>> srandmember(@RedisProtocolSupport.Key final CharSequence key, final long count) {
        return futureToSingle(() -> reservedCnx.srandmember(key, count));
    }

    @Override
    public Single<Long> srem(@RedisProtocolSupport.Key final CharSequence key, final CharSequence member) {
        return futureToSingle(() -> reservedCnx.srem(key, member));
    }

    @Override
    public Single<Long> srem(@RedisProtocolSupport.Key final CharSequence key, final CharSequence member1,
                             final CharSequence member2) {
        return futureToSingle(() -> reservedCnx.srem(key, member1, member2));
    }

    @Override
    public Single<Long> srem(@RedisProtocolSupport.Key final CharSequence key, final CharSequence member1,
                             final CharSequence member2, final CharSequence member3) {
        return futureToSingle(() -> reservedCnx.srem(key, member1, member2, member3));
    }

    @Override
    public Single<Long> srem(@RedisProtocolSupport.Key final CharSequence key,
                             final Collection<? extends CharSequence> members) {
        return futureToSingle(() -> reservedCnx.srem(key, members));
    }

    @Override
    public <T> Single<List<T>> sscan(@RedisProtocolSupport.Key final CharSequence key, final long cursor) {
        return futureToSingle(() -> reservedCnx.sscan(key, cursor));
    }

    @Override
    public <T> Single<List<T>> sscan(@RedisProtocolSupport.Key final CharSequence key, final long cursor,
                                     @Nullable final CharSequence matchPattern, @Nullable final Long count) {
        return futureToSingle(() -> reservedCnx.sscan(key, cursor, matchPattern, count));
    }

    @Override
    public Single<Long> strlen(@RedisProtocolSupport.Key final CharSequence key) {
        return futureToSingle(() -> reservedCnx.strlen(key));
    }

    @Override
    public <T> Single<List<T>> sunion(@RedisProtocolSupport.Key final CharSequence key) {
        return futureToSingle(() -> reservedCnx.sunion(key));
    }

    @Override
    public <T> Single<List<T>> sunion(@RedisProtocolSupport.Key final CharSequence key1,
                                      @RedisProtocolSupport.Key final CharSequence key2) {
        return futureToSingle(() -> reservedCnx.sunion(key1, key2));
    }

    @Override
    public <T> Single<List<T>> sunion(@RedisProtocolSupport.Key final CharSequence key1,
                                      @RedisProtocolSupport.Key final CharSequence key2,
                                      @RedisProtocolSupport.Key final CharSequence key3) {
        return futureToSingle(() -> reservedCnx.sunion(key1, key2, key3));
    }

    @Override
    public <T> Single<List<T>> sunion(@RedisProtocolSupport.Key final Collection<? extends CharSequence> keys) {
        return futureToSingle(() -> reservedCnx.sunion(keys));
    }

    @Override
    public Single<Long> sunionstore(@RedisProtocolSupport.Key final CharSequence destination,
                                    @RedisProtocolSupport.Key final CharSequence key) {
        return futureToSingle(() -> reservedCnx.sunionstore(destination, key));
    }

    @Override
    public Single<Long> sunionstore(@RedisProtocolSupport.Key final CharSequence destination,
                                    @RedisProtocolSupport.Key final CharSequence key1,
                                    @RedisProtocolSupport.Key final CharSequence key2) {
        return futureToSingle(() -> reservedCnx.sunionstore(destination, key1, key2));
    }

    @Override
    public Single<Long> sunionstore(@RedisProtocolSupport.Key final CharSequence destination,
                                    @RedisProtocolSupport.Key final CharSequence key1,
                                    @RedisProtocolSupport.Key final CharSequence key2,
                                    @RedisProtocolSupport.Key final CharSequence key3) {
        return futureToSingle(() -> reservedCnx.sunionstore(destination, key1, key2, key3));
    }

    @Override
    public Single<Long> sunionstore(@RedisProtocolSupport.Key final CharSequence destination,
                                    @RedisProtocolSupport.Key final Collection<? extends CharSequence> keys) {
        return futureToSingle(() -> reservedCnx.sunionstore(destination, keys));
    }

    @Override
    public Single<String> swapdb(final long index, final long index1) {
        return futureToSingle(() -> reservedCnx.swapdb(index, index1));
    }

    @Override
    public <T> Single<List<T>> time() {
        return futureToSingle(() -> reservedCnx.time());
    }

    @Override
    public Single<Long> touch(@RedisProtocolSupport.Key final CharSequence key) {
        return futureToSingle(() -> reservedCnx.touch(key));
    }

    @Override
    public Single<Long> touch(@RedisProtocolSupport.Key final CharSequence key1,
                              @RedisProtocolSupport.Key final CharSequence key2) {
        return futureToSingle(() -> reservedCnx.touch(key1, key2));
    }

    @Override
    public Single<Long> touch(@RedisProtocolSupport.Key final CharSequence key1,
                              @RedisProtocolSupport.Key final CharSequence key2,
                              @RedisProtocolSupport.Key final CharSequence key3) {
        return futureToSingle(() -> reservedCnx.touch(key1, key2, key3));
    }

    @Override
    public Single<Long> touch(@RedisProtocolSupport.Key final Collection<? extends CharSequence> keys) {
        return futureToSingle(() -> reservedCnx.touch(keys));
    }

    @Override
    public Single<Long> ttl(@RedisProtocolSupport.Key final CharSequence key) {
        return futureToSingle(() -> reservedCnx.ttl(key));
    }

    @Override
    public Single<String> type(@RedisProtocolSupport.Key final CharSequence key) {
        return futureToSingle(() -> reservedCnx.type(key));
    }

    @Override
    public Single<Long> unlink(@RedisProtocolSupport.Key final CharSequence key) {
        return futureToSingle(() -> reservedCnx.unlink(key));
    }

    @Override
    public Single<Long> unlink(@RedisProtocolSupport.Key final CharSequence key1,
                               @RedisProtocolSupport.Key final CharSequence key2) {
        return futureToSingle(() -> reservedCnx.unlink(key1, key2));
    }

    @Override
    public Single<Long> unlink(@RedisProtocolSupport.Key final CharSequence key1,
                               @RedisProtocolSupport.Key final CharSequence key2,
                               @RedisProtocolSupport.Key final CharSequence key3) {
        return futureToSingle(() -> reservedCnx.unlink(key1, key2, key3));
    }

    @Override
    public Single<Long> unlink(@RedisProtocolSupport.Key final Collection<? extends CharSequence> keys) {
        return futureToSingle(() -> reservedCnx.unlink(keys));
    }

    @Override
    public Single<String> unwatch() {
        return futureToSingle(() -> reservedCnx.unwatch());
    }

    @Override
    public Single<Long> wait(final long numslaves, final long timeout) {
        return futureToSingle(() -> reservedCnx.wait(numslaves, timeout));
    }

    @Override
    public Single<String> watch(@RedisProtocolSupport.Key final CharSequence key) {
        return futureToSingle(() -> reservedCnx.watch(key));
    }

    @Override
    public Single<String> watch(@RedisProtocolSupport.Key final CharSequence key1,
                                @RedisProtocolSupport.Key final CharSequence key2) {
        return futureToSingle(() -> reservedCnx.watch(key1, key2));
    }

    @Override
    public Single<String> watch(@RedisProtocolSupport.Key final CharSequence key1,
                                @RedisProtocolSupport.Key final CharSequence key2,
                                @RedisProtocolSupport.Key final CharSequence key3) {
        return futureToSingle(() -> reservedCnx.watch(key1, key2, key3));
    }

    @Override
    public Single<String> watch(@RedisProtocolSupport.Key final Collection<? extends CharSequence> keys) {
        return futureToSingle(() -> reservedCnx.watch(keys));
    }

    @Override
    public Single<String> xadd(@RedisProtocolSupport.Key final CharSequence key, final CharSequence id,
                               final CharSequence field, final CharSequence value) {
        return futureToSingle(() -> reservedCnx.xadd(key, id, field, value));
    }

    @Override
    public Single<String> xadd(@RedisProtocolSupport.Key final CharSequence key, final CharSequence id,
                               final CharSequence field1, final CharSequence value1, final CharSequence field2,
                               final CharSequence value2) {
        return futureToSingle(() -> reservedCnx.xadd(key, id, field1, value1, field2, value2));
    }

    @Override
    public Single<String> xadd(@RedisProtocolSupport.Key final CharSequence key, final CharSequence id,
                               final CharSequence field1, final CharSequence value1, final CharSequence field2,
                               final CharSequence value2, final CharSequence field3, final CharSequence value3) {
        return futureToSingle(() -> reservedCnx.xadd(key, id, field1, value1, field2, value2, field3, value3));
    }

    @Override
    public Single<String> xadd(@RedisProtocolSupport.Key final CharSequence key, final CharSequence id,
                               final Collection<RedisProtocolSupport.FieldValue> fieldValues) {
        return futureToSingle(() -> reservedCnx.xadd(key, id, fieldValues));
    }

    @Override
    public Single<Long> xlen(@RedisProtocolSupport.Key final CharSequence key) {
        return futureToSingle(() -> reservedCnx.xlen(key));
    }

    @Override
    public <T> Single<List<T>> xpending(@RedisProtocolSupport.Key final CharSequence key, final CharSequence group) {
        return futureToSingle(() -> reservedCnx.xpending(key, group));
    }

    @Override
    public <T> Single<List<T>> xpending(@RedisProtocolSupport.Key final CharSequence key, final CharSequence group,
                                        @Nullable final CharSequence start, @Nullable final CharSequence end,
                                        @Nullable final Long count, @Nullable final CharSequence consumer) {
        return futureToSingle(() -> reservedCnx.xpending(key, group, start, end, count, consumer));
    }

    @Override
    public <T> Single<List<T>> xrange(@RedisProtocolSupport.Key final CharSequence key, final CharSequence start,
                                      final CharSequence end) {
        return futureToSingle(() -> reservedCnx.xrange(key, start, end));
    }

    @Override
    public <T> Single<List<T>> xrange(@RedisProtocolSupport.Key final CharSequence key, final CharSequence start,
                                      final CharSequence end, @Nullable final Long count) {
        return futureToSingle(() -> reservedCnx.xrange(key, start, end, count));
    }

    @Override
    public <T> Single<List<T>> xread(@RedisProtocolSupport.Key final Collection<? extends CharSequence> keys,
                                     final Collection<? extends CharSequence> ids) {
        return futureToSingle(() -> reservedCnx.xread(keys, ids));
    }

    @Override
    public <T> Single<List<T>> xread(@Nullable final Long count, @Nullable final Long blockMilliseconds,
                                     @RedisProtocolSupport.Key final Collection<? extends CharSequence> keys,
                                     final Collection<? extends CharSequence> ids) {
        return futureToSingle(() -> reservedCnx.xread(count, blockMilliseconds, keys, ids));
    }

    @Override
    public <T> Single<List<T>> xreadgroup(final RedisProtocolSupport.GroupConsumer groupConsumer,
                                          @RedisProtocolSupport.Key final Collection<? extends CharSequence> keys,
                                          final Collection<? extends CharSequence> ids) {
        return futureToSingle(() -> reservedCnx.xreadgroup(groupConsumer, keys, ids));
    }

    @Override
    public <T> Single<List<T>> xreadgroup(final RedisProtocolSupport.GroupConsumer groupConsumer,
                                          @Nullable final Long count, @Nullable final Long blockMilliseconds,
                                          @RedisProtocolSupport.Key final Collection<? extends CharSequence> keys,
                                          final Collection<? extends CharSequence> ids) {
        return futureToSingle(() -> reservedCnx.xreadgroup(groupConsumer, count, blockMilliseconds, keys, ids));
    }

    @Override
    public <T> Single<List<T>> xrevrange(@RedisProtocolSupport.Key final CharSequence key, final CharSequence end,
                                         final CharSequence start) {
        return futureToSingle(() -> reservedCnx.xrevrange(key, end, start));
    }

    @Override
    public <T> Single<List<T>> xrevrange(@RedisProtocolSupport.Key final CharSequence key, final CharSequence end,
                                         final CharSequence start, @Nullable final Long count) {
        return futureToSingle(() -> reservedCnx.xrevrange(key, end, start, count));
    }

    @Override
    public Single<Long> zadd(@RedisProtocolSupport.Key final CharSequence key,
                             final Collection<RedisProtocolSupport.ScoreMember> scoreMembers) {
        return futureToSingle(() -> reservedCnx.zadd(key, scoreMembers));
    }

    @Override
    public Single<Long> zadd(@RedisProtocolSupport.Key final CharSequence key,
                             @Nullable final RedisProtocolSupport.ZaddCondition condition,
                             @Nullable final RedisProtocolSupport.ZaddChange change, final double score,
                             final CharSequence member) {
        return futureToSingle(() -> reservedCnx.zadd(key, condition, change, score, member));
    }

    @Override
    public Single<Long> zadd(@RedisProtocolSupport.Key final CharSequence key,
                             @Nullable final RedisProtocolSupport.ZaddCondition condition,
                             @Nullable final RedisProtocolSupport.ZaddChange change, final double score1,
                             final CharSequence member1, final double score2, final CharSequence member2) {
        return futureToSingle(() -> reservedCnx.zadd(key, condition, change, score1, member1, score2, member2));
    }

    @Override
    public Single<Long> zadd(@RedisProtocolSupport.Key final CharSequence key,
                             @Nullable final RedisProtocolSupport.ZaddCondition condition,
                             @Nullable final RedisProtocolSupport.ZaddChange change, final double score1,
                             final CharSequence member1, final double score2, final CharSequence member2,
                             final double score3, final CharSequence member3) {
        return futureToSingle(
                    () -> reservedCnx.zadd(key, condition, change, score1, member1, score2, member2, score3, member3));
    }

    @Override
    public Single<Long> zadd(@RedisProtocolSupport.Key final CharSequence key,
                             @Nullable final RedisProtocolSupport.ZaddCondition condition,
                             @Nullable final RedisProtocolSupport.ZaddChange change,
                             final Collection<RedisProtocolSupport.ScoreMember> scoreMembers) {
        return futureToSingle(() -> reservedCnx.zadd(key, condition, change, scoreMembers));
    }

    @Override
    public Single<Double> zaddIncr(@RedisProtocolSupport.Key final CharSequence key,
                                   final Collection<RedisProtocolSupport.ScoreMember> scoreMembers) {
        return futureToSingle(() -> reservedCnx.zaddIncr(key, scoreMembers));
    }

    @Override
    public Single<Double> zaddIncr(@RedisProtocolSupport.Key final CharSequence key,
                                   @Nullable final RedisProtocolSupport.ZaddCondition condition,
                                   @Nullable final RedisProtocolSupport.ZaddChange change, final double score,
                                   final CharSequence member) {
        return futureToSingle(() -> reservedCnx.zaddIncr(key, condition, change, score, member));
    }

    @Override
    public Single<Double> zaddIncr(@RedisProtocolSupport.Key final CharSequence key,
                                   @Nullable final RedisProtocolSupport.ZaddCondition condition,
                                   @Nullable final RedisProtocolSupport.ZaddChange change, final double score1,
                                   final CharSequence member1, final double score2, final CharSequence member2) {
        return futureToSingle(() -> reservedCnx.zaddIncr(key, condition, change, score1, member1, score2, member2));
    }

    @Override
    public Single<Double> zaddIncr(@RedisProtocolSupport.Key final CharSequence key,
                                   @Nullable final RedisProtocolSupport.ZaddCondition condition,
                                   @Nullable final RedisProtocolSupport.ZaddChange change, final double score1,
                                   final CharSequence member1, final double score2, final CharSequence member2,
                                   final double score3, final CharSequence member3) {
        return futureToSingle(() -> reservedCnx.zaddIncr(key, condition, change, score1, member1, score2, member2,
                    score3, member3));
    }

    @Override
    public Single<Double> zaddIncr(@RedisProtocolSupport.Key final CharSequence key,
                                   @Nullable final RedisProtocolSupport.ZaddCondition condition,
                                   @Nullable final RedisProtocolSupport.ZaddChange change,
                                   final Collection<RedisProtocolSupport.ScoreMember> scoreMembers) {
        return futureToSingle(() -> reservedCnx.zaddIncr(key, condition, change, scoreMembers));
    }

    @Override
    public Single<Long> zcard(@RedisProtocolSupport.Key final CharSequence key) {
        return futureToSingle(() -> reservedCnx.zcard(key));
    }

    @Override
    public Single<Long> zcount(@RedisProtocolSupport.Key final CharSequence key, final double min, final double max) {
        return futureToSingle(() -> reservedCnx.zcount(key, min, max));
    }

    @Override
    public Single<Double> zincrby(@RedisProtocolSupport.Key final CharSequence key, final long increment,
                                  final CharSequence member) {
        return futureToSingle(() -> reservedCnx.zincrby(key, increment, member));
    }

    @Override
    public Single<Long> zinterstore(@RedisProtocolSupport.Key final CharSequence destination, final long numkeys,
                                    @RedisProtocolSupport.Key final Collection<? extends CharSequence> keys) {
        return futureToSingle(() -> reservedCnx.zinterstore(destination, numkeys, keys));
    }

    @Override
    public Single<Long> zinterstore(@RedisProtocolSupport.Key final CharSequence destination, final long numkeys,
                                    @RedisProtocolSupport.Key final Collection<? extends CharSequence> keys,
                                    final Collection<Long> weightses,
                                    @Nullable final RedisProtocolSupport.ZinterstoreAggregate aggregate) {
        return futureToSingle(() -> reservedCnx.zinterstore(destination, numkeys, keys, weightses, aggregate));
    }

    @Override
    public Single<Long> zlexcount(@RedisProtocolSupport.Key final CharSequence key, final CharSequence min,
                                  final CharSequence max) {
        return futureToSingle(() -> reservedCnx.zlexcount(key, min, max));
    }

    @Override
    public <T> Single<List<T>> zpopmax(@RedisProtocolSupport.Key final CharSequence key) {
        return futureToSingle(() -> reservedCnx.zpopmax(key));
    }

    @Override
    public <T> Single<List<T>> zpopmax(@RedisProtocolSupport.Key final CharSequence key, @Nullable final Long count) {
        return futureToSingle(() -> reservedCnx.zpopmax(key, count));
    }

    @Override
    public <T> Single<List<T>> zpopmin(@RedisProtocolSupport.Key final CharSequence key) {
        return futureToSingle(() -> reservedCnx.zpopmin(key));
    }

    @Override
    public <T> Single<List<T>> zpopmin(@RedisProtocolSupport.Key final CharSequence key, @Nullable final Long count) {
        return futureToSingle(() -> reservedCnx.zpopmin(key, count));
    }

    @Override
    public <T> Single<List<T>> zrange(@RedisProtocolSupport.Key final CharSequence key, final long start,
                                      final long stop) {
        return futureToSingle(() -> reservedCnx.zrange(key, start, stop));
    }

    @Override
    public <T> Single<List<T>> zrange(@RedisProtocolSupport.Key final CharSequence key, final long start,
                                      final long stop,
                                      @Nullable final RedisProtocolSupport.ZrangeWithscores withscores) {
        return futureToSingle(() -> reservedCnx.zrange(key, start, stop, withscores));
    }

    @Override
    public <T> Single<List<T>> zrangebylex(@RedisProtocolSupport.Key final CharSequence key, final CharSequence min,
                                           final CharSequence max) {
        return futureToSingle(() -> reservedCnx.zrangebylex(key, min, max));
    }

    @Override
    public <T> Single<List<T>> zrangebylex(@RedisProtocolSupport.Key final CharSequence key, final CharSequence min,
                                           final CharSequence max,
                                           @Nullable final RedisProtocolSupport.OffsetCount offsetCount) {
        return futureToSingle(() -> reservedCnx.zrangebylex(key, min, max, offsetCount));
    }

    @Override
    public <T> Single<List<T>> zrangebyscore(@RedisProtocolSupport.Key final CharSequence key, final double min,
                                             final double max) {
        return futureToSingle(() -> reservedCnx.zrangebyscore(key, min, max));
    }

    @Override
    public <T> Single<List<T>> zrangebyscore(@RedisProtocolSupport.Key final CharSequence key, final double min,
                                             final double max,
                                             @Nullable final RedisProtocolSupport.ZrangebyscoreWithscores withscores,
                                             @Nullable final RedisProtocolSupport.OffsetCount offsetCount) {
        return futureToSingle(() -> reservedCnx.zrangebyscore(key, min, max, withscores, offsetCount));
    }

    @Override
    public Single<Long> zrank(@RedisProtocolSupport.Key final CharSequence key, final CharSequence member) {
        return futureToSingle(() -> reservedCnx.zrank(key, member));
    }

    @Override
    public Single<Long> zrem(@RedisProtocolSupport.Key final CharSequence key, final CharSequence member) {
        return futureToSingle(() -> reservedCnx.zrem(key, member));
    }

    @Override
    public Single<Long> zrem(@RedisProtocolSupport.Key final CharSequence key, final CharSequence member1,
                             final CharSequence member2) {
        return futureToSingle(() -> reservedCnx.zrem(key, member1, member2));
    }

    @Override
    public Single<Long> zrem(@RedisProtocolSupport.Key final CharSequence key, final CharSequence member1,
                             final CharSequence member2, final CharSequence member3) {
        return futureToSingle(() -> reservedCnx.zrem(key, member1, member2, member3));
    }

    @Override
    public Single<Long> zrem(@RedisProtocolSupport.Key final CharSequence key,
                             final Collection<? extends CharSequence> members) {
        return futureToSingle(() -> reservedCnx.zrem(key, members));
    }

    @Override
    public Single<Long> zremrangebylex(@RedisProtocolSupport.Key final CharSequence key, final CharSequence min,
                                       final CharSequence max) {
        return futureToSingle(() -> reservedCnx.zremrangebylex(key, min, max));
    }

    @Override
    public Single<Long> zremrangebyrank(@RedisProtocolSupport.Key final CharSequence key, final long start,
                                        final long stop) {
        return futureToSingle(() -> reservedCnx.zremrangebyrank(key, start, stop));
    }

    @Override
    public Single<Long> zremrangebyscore(@RedisProtocolSupport.Key final CharSequence key, final double min,
                                         final double max) {
        return futureToSingle(() -> reservedCnx.zremrangebyscore(key, min, max));
    }

    @Override
    public <T> Single<List<T>> zrevrange(@RedisProtocolSupport.Key final CharSequence key, final long start,
                                         final long stop) {
        return futureToSingle(() -> reservedCnx.zrevrange(key, start, stop));
    }

    @Override
    public <T> Single<List<T>> zrevrange(@RedisProtocolSupport.Key final CharSequence key, final long start,
                                         final long stop,
                                         @Nullable final RedisProtocolSupport.ZrevrangeWithscores withscores) {
        return futureToSingle(() -> reservedCnx.zrevrange(key, start, stop, withscores));
    }

    @Override
    public <T> Single<List<T>> zrevrangebylex(@RedisProtocolSupport.Key final CharSequence key, final CharSequence max,
                                              final CharSequence min) {
        return futureToSingle(() -> reservedCnx.zrevrangebylex(key, max, min));
    }

    @Override
    public <T> Single<List<T>> zrevrangebylex(@RedisProtocolSupport.Key final CharSequence key, final CharSequence max,
                                              final CharSequence min,
                                              @Nullable final RedisProtocolSupport.OffsetCount offsetCount) {
        return futureToSingle(() -> reservedCnx.zrevrangebylex(key, max, min, offsetCount));
    }

    @Override
    public <T> Single<List<T>> zrevrangebyscore(@RedisProtocolSupport.Key final CharSequence key, final double max,
                                                final double min) {
        return futureToSingle(() -> reservedCnx.zrevrangebyscore(key, max, min));
    }

    @Override
    public <T> Single<List<T>> zrevrangebyscore(@RedisProtocolSupport.Key final CharSequence key, final double max,
                                                final double min,
                                                @Nullable final RedisProtocolSupport.ZrevrangebyscoreWithscores withscores,
                                                @Nullable final RedisProtocolSupport.OffsetCount offsetCount) {
        return futureToSingle(() -> reservedCnx.zrevrangebyscore(key, max, min, withscores, offsetCount));
    }

    @Override
    public Single<Long> zrevrank(@RedisProtocolSupport.Key final CharSequence key, final CharSequence member) {
        return futureToSingle(() -> reservedCnx.zrevrank(key, member));
    }

    @Override
    public <T> Single<List<T>> zscan(@RedisProtocolSupport.Key final CharSequence key, final long cursor) {
        return futureToSingle(() -> reservedCnx.zscan(key, cursor));
    }

    @Override
    public <T> Single<List<T>> zscan(@RedisProtocolSupport.Key final CharSequence key, final long cursor,
                                     @Nullable final CharSequence matchPattern, @Nullable final Long count) {
        return futureToSingle(() -> reservedCnx.zscan(key, cursor, matchPattern, count));
    }

    @Override
    public Single<Double> zscore(@RedisProtocolSupport.Key final CharSequence key, final CharSequence member) {
        return futureToSingle(() -> reservedCnx.zscore(key, member));
    }

    @Override
    public Single<Long> zunionstore(@RedisProtocolSupport.Key final CharSequence destination, final long numkeys,
                                    @RedisProtocolSupport.Key final Collection<? extends CharSequence> keys) {
        return futureToSingle(() -> reservedCnx.zunionstore(destination, numkeys, keys));
    }

    @Override
    public Single<Long> zunionstore(@RedisProtocolSupport.Key final CharSequence destination, final long numkeys,
                                    @RedisProtocolSupport.Key final Collection<? extends CharSequence> keys,
                                    final Collection<Long> weightses,
                                    @Nullable final RedisProtocolSupport.ZunionstoreAggregate aggregate) {
        return futureToSingle(() -> reservedCnx.zunionstore(destination, numkeys, keys, weightses, aggregate));
    }
}

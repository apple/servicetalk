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
    public Single<String> append(@RedisProtocolSupport.Key final CharSequence key, final CharSequence value) {
        return blockingToSingle(() -> reservedCnx.append(key, value));
    }

    @Override
    public Single<String> auth(final CharSequence password) {
        return blockingToSingle(() -> reservedCnx.auth(password));
    }

    @Override
    public Single<String> bgrewriteaof() {
        return blockingToSingle(() -> reservedCnx.bgrewriteaof());
    }

    @Override
    public Single<String> bgsave() {
        return blockingToSingle(() -> reservedCnx.bgsave());
    }

    @Override
    public Single<String> bitcount(@RedisProtocolSupport.Key final CharSequence key) {
        return blockingToSingle(() -> reservedCnx.bitcount(key));
    }

    @Override
    public Single<String> bitcount(@RedisProtocolSupport.Key final CharSequence key, @Nullable final Long start,
                                   @Nullable final Long end) {
        return blockingToSingle(() -> reservedCnx.bitcount(key, start, end));
    }

    @Override
    public Single<String> bitfield(@RedisProtocolSupport.Key final CharSequence key,
                                   final Collection<RedisProtocolSupport.BitfieldOperation> operations) {
        return blockingToSingle(() -> reservedCnx.bitfield(key, operations));
    }

    @Override
    public Single<String> bitop(final CharSequence operation, @RedisProtocolSupport.Key final CharSequence destkey,
                                @RedisProtocolSupport.Key final CharSequence key) {
        return blockingToSingle(() -> reservedCnx.bitop(operation, destkey, key));
    }

    @Override
    public Single<String> bitop(final CharSequence operation, @RedisProtocolSupport.Key final CharSequence destkey,
                                @RedisProtocolSupport.Key final CharSequence key1,
                                @RedisProtocolSupport.Key final CharSequence key2) {
        return blockingToSingle(() -> reservedCnx.bitop(operation, destkey, key1, key2));
    }

    @Override
    public Single<String> bitop(final CharSequence operation, @RedisProtocolSupport.Key final CharSequence destkey,
                                @RedisProtocolSupport.Key final CharSequence key1,
                                @RedisProtocolSupport.Key final CharSequence key2,
                                @RedisProtocolSupport.Key final CharSequence key3) {
        return blockingToSingle(() -> reservedCnx.bitop(operation, destkey, key1, key2, key3));
    }

    @Override
    public Single<String> bitop(final CharSequence operation, @RedisProtocolSupport.Key final CharSequence destkey,
                                @RedisProtocolSupport.Key final Collection<? extends CharSequence> keys) {
        return blockingToSingle(() -> reservedCnx.bitop(operation, destkey, keys));
    }

    @Override
    public Single<String> bitpos(@RedisProtocolSupport.Key final CharSequence key, final long bit) {
        return blockingToSingle(() -> reservedCnx.bitpos(key, bit));
    }

    @Override
    public Single<String> bitpos(@RedisProtocolSupport.Key final CharSequence key, final long bit,
                                 @Nullable final Long start, @Nullable final Long end) {
        return blockingToSingle(() -> reservedCnx.bitpos(key, bit, start, end));
    }

    @Override
    public Single<String> blpop(@RedisProtocolSupport.Key final Collection<? extends CharSequence> keys,
                                final long timeout) {
        return blockingToSingle(() -> reservedCnx.blpop(keys, timeout));
    }

    @Override
    public Single<String> brpop(@RedisProtocolSupport.Key final Collection<? extends CharSequence> keys,
                                final long timeout) {
        return blockingToSingle(() -> reservedCnx.brpop(keys, timeout));
    }

    @Override
    public Single<String> brpoplpush(@RedisProtocolSupport.Key final CharSequence source,
                                     @RedisProtocolSupport.Key final CharSequence destination, final long timeout) {
        return blockingToSingle(() -> reservedCnx.brpoplpush(source, destination, timeout));
    }

    @Override
    public Single<String> bzpopmax(@RedisProtocolSupport.Key final Collection<? extends CharSequence> keys,
                                   final long timeout) {
        return blockingToSingle(() -> reservedCnx.bzpopmax(keys, timeout));
    }

    @Override
    public Single<String> bzpopmin(@RedisProtocolSupport.Key final Collection<? extends CharSequence> keys,
                                   final long timeout) {
        return blockingToSingle(() -> reservedCnx.bzpopmin(keys, timeout));
    }

    @Override
    public Single<String> clientKill(@Nullable final Long id, @Nullable final RedisProtocolSupport.ClientKillType type,
                                     @Nullable final CharSequence addrIpPort,
                                     @Nullable final CharSequence skipmeYesNo) {
        return blockingToSingle(() -> reservedCnx.clientKill(id, type, addrIpPort, skipmeYesNo));
    }

    @Override
    public Single<String> clientList() {
        return blockingToSingle(() -> reservedCnx.clientList());
    }

    @Override
    public Single<String> clientGetname() {
        return blockingToSingle(() -> reservedCnx.clientGetname());
    }

    @Override
    public Single<String> clientPause(final long timeout) {
        return blockingToSingle(() -> reservedCnx.clientPause(timeout));
    }

    @Override
    public Single<String> clientReply(final RedisProtocolSupport.ClientReplyReplyMode replyMode) {
        return blockingToSingle(() -> reservedCnx.clientReply(replyMode));
    }

    @Override
    public Single<String> clientSetname(final CharSequence connectionName) {
        return blockingToSingle(() -> reservedCnx.clientSetname(connectionName));
    }

    @Override
    public Single<String> clusterAddslots(final long slot) {
        return blockingToSingle(() -> reservedCnx.clusterAddslots(slot));
    }

    @Override
    public Single<String> clusterAddslots(final long slot1, final long slot2) {
        return blockingToSingle(() -> reservedCnx.clusterAddslots(slot1, slot2));
    }

    @Override
    public Single<String> clusterAddslots(final long slot1, final long slot2, final long slot3) {
        return blockingToSingle(() -> reservedCnx.clusterAddslots(slot1, slot2, slot3));
    }

    @Override
    public Single<String> clusterAddslots(final Collection<Long> slots) {
        return blockingToSingle(() -> reservedCnx.clusterAddslots(slots));
    }

    @Override
    public Single<String> clusterCountFailureReports(final CharSequence nodeId) {
        return blockingToSingle(() -> reservedCnx.clusterCountFailureReports(nodeId));
    }

    @Override
    public Single<String> clusterCountkeysinslot(final long slot) {
        return blockingToSingle(() -> reservedCnx.clusterCountkeysinslot(slot));
    }

    @Override
    public Single<String> clusterDelslots(final long slot) {
        return blockingToSingle(() -> reservedCnx.clusterDelslots(slot));
    }

    @Override
    public Single<String> clusterDelslots(final long slot1, final long slot2) {
        return blockingToSingle(() -> reservedCnx.clusterDelslots(slot1, slot2));
    }

    @Override
    public Single<String> clusterDelslots(final long slot1, final long slot2, final long slot3) {
        return blockingToSingle(() -> reservedCnx.clusterDelslots(slot1, slot2, slot3));
    }

    @Override
    public Single<String> clusterDelslots(final Collection<Long> slots) {
        return blockingToSingle(() -> reservedCnx.clusterDelslots(slots));
    }

    @Override
    public Single<String> clusterFailover() {
        return blockingToSingle(() -> reservedCnx.clusterFailover());
    }

    @Override
    public Single<String> clusterFailover(@Nullable final RedisProtocolSupport.ClusterFailoverOptions options) {
        return blockingToSingle(() -> reservedCnx.clusterFailover(options));
    }

    @Override
    public Single<String> clusterForget(final CharSequence nodeId) {
        return blockingToSingle(() -> reservedCnx.clusterForget(nodeId));
    }

    @Override
    public Single<String> clusterGetkeysinslot(final long slot, final long count) {
        return blockingToSingle(() -> reservedCnx.clusterGetkeysinslot(slot, count));
    }

    @Override
    public Single<String> clusterInfo() {
        return blockingToSingle(() -> reservedCnx.clusterInfo());
    }

    @Override
    public Single<String> clusterKeyslot(final CharSequence key) {
        return blockingToSingle(() -> reservedCnx.clusterKeyslot(key));
    }

    @Override
    public Single<String> clusterMeet(final CharSequence ip, final long port) {
        return blockingToSingle(() -> reservedCnx.clusterMeet(ip, port));
    }

    @Override
    public Single<String> clusterNodes() {
        return blockingToSingle(() -> reservedCnx.clusterNodes());
    }

    @Override
    public Single<String> clusterReplicate(final CharSequence nodeId) {
        return blockingToSingle(() -> reservedCnx.clusterReplicate(nodeId));
    }

    @Override
    public Single<String> clusterReset() {
        return blockingToSingle(() -> reservedCnx.clusterReset());
    }

    @Override
    public Single<String> clusterReset(@Nullable final RedisProtocolSupport.ClusterResetResetType resetType) {
        return blockingToSingle(() -> reservedCnx.clusterReset(resetType));
    }

    @Override
    public Single<String> clusterSaveconfig() {
        return blockingToSingle(() -> reservedCnx.clusterSaveconfig());
    }

    @Override
    public Single<String> clusterSetConfigEpoch(final long configEpoch) {
        return blockingToSingle(() -> reservedCnx.clusterSetConfigEpoch(configEpoch));
    }

    @Override
    public Single<String> clusterSetslot(final long slot,
                                         final RedisProtocolSupport.ClusterSetslotSubcommand subcommand) {
        return blockingToSingle(() -> reservedCnx.clusterSetslot(slot, subcommand));
    }

    @Override
    public Single<String> clusterSetslot(final long slot,
                                         final RedisProtocolSupport.ClusterSetslotSubcommand subcommand,
                                         @Nullable final CharSequence nodeId) {
        return blockingToSingle(() -> reservedCnx.clusterSetslot(slot, subcommand, nodeId));
    }

    @Override
    public Single<String> clusterSlaves(final CharSequence nodeId) {
        return blockingToSingle(() -> reservedCnx.clusterSlaves(nodeId));
    }

    @Override
    public Single<String> clusterSlots() {
        return blockingToSingle(() -> reservedCnx.clusterSlots());
    }

    @Override
    public Single<String> command() {
        return blockingToSingle(() -> reservedCnx.command());
    }

    @Override
    public Single<String> commandCount() {
        return blockingToSingle(() -> reservedCnx.commandCount());
    }

    @Override
    public Single<String> commandGetkeys() {
        return blockingToSingle(() -> reservedCnx.commandGetkeys());
    }

    @Override
    public Single<String> commandInfo(final CharSequence commandName) {
        return blockingToSingle(() -> reservedCnx.commandInfo(commandName));
    }

    @Override
    public Single<String> commandInfo(final CharSequence commandName1, final CharSequence commandName2) {
        return blockingToSingle(() -> reservedCnx.commandInfo(commandName1, commandName2));
    }

    @Override
    public Single<String> commandInfo(final CharSequence commandName1, final CharSequence commandName2,
                                      final CharSequence commandName3) {
        return blockingToSingle(() -> reservedCnx.commandInfo(commandName1, commandName2, commandName3));
    }

    @Override
    public Single<String> commandInfo(final Collection<? extends CharSequence> commandNames) {
        return blockingToSingle(() -> reservedCnx.commandInfo(commandNames));
    }

    @Override
    public Single<String> configGet(final CharSequence parameter) {
        return blockingToSingle(() -> reservedCnx.configGet(parameter));
    }

    @Override
    public Single<String> configRewrite() {
        return blockingToSingle(() -> reservedCnx.configRewrite());
    }

    @Override
    public Single<String> configSet(final CharSequence parameter, final CharSequence value) {
        return blockingToSingle(() -> reservedCnx.configSet(parameter, value));
    }

    @Override
    public Single<String> configResetstat() {
        return blockingToSingle(() -> reservedCnx.configResetstat());
    }

    @Override
    public Single<String> dbsize() {
        return blockingToSingle(() -> reservedCnx.dbsize());
    }

    @Override
    public Single<String> debugObject(@RedisProtocolSupport.Key final CharSequence key) {
        return blockingToSingle(() -> reservedCnx.debugObject(key));
    }

    @Override
    public Single<String> debugSegfault() {
        return blockingToSingle(() -> reservedCnx.debugSegfault());
    }

    @Override
    public Single<String> decr(@RedisProtocolSupport.Key final CharSequence key) {
        return blockingToSingle(() -> reservedCnx.decr(key));
    }

    @Override
    public Single<String> decrby(@RedisProtocolSupport.Key final CharSequence key, final long decrement) {
        return blockingToSingle(() -> reservedCnx.decrby(key, decrement));
    }

    @Override
    public Single<String> del(@RedisProtocolSupport.Key final CharSequence key) {
        return blockingToSingle(() -> reservedCnx.del(key));
    }

    @Override
    public Single<String> del(@RedisProtocolSupport.Key final CharSequence key1,
                              @RedisProtocolSupport.Key final CharSequence key2) {
        return blockingToSingle(() -> reservedCnx.del(key1, key2));
    }

    @Override
    public Single<String> del(@RedisProtocolSupport.Key final CharSequence key1,
                              @RedisProtocolSupport.Key final CharSequence key2,
                              @RedisProtocolSupport.Key final CharSequence key3) {
        return blockingToSingle(() -> reservedCnx.del(key1, key2, key3));
    }

    @Override
    public Single<String> del(@RedisProtocolSupport.Key final Collection<? extends CharSequence> keys) {
        return blockingToSingle(() -> reservedCnx.del(keys));
    }

    @Override
    public Single<String> discard() {
        return blockingToSingle(() -> reservedCnx.discard());
    }

    @Override
    public Single<String> dump(@RedisProtocolSupport.Key final CharSequence key) {
        return blockingToSingle(() -> reservedCnx.dump(key));
    }

    @Override
    public Single<String> echo(final CharSequence message) {
        return blockingToSingle(() -> reservedCnx.echo(message));
    }

    @Override
    public Single<String> eval(final CharSequence script, final long numkeys,
                               @RedisProtocolSupport.Key final Collection<? extends CharSequence> keys,
                               final Collection<? extends CharSequence> args) {
        return blockingToSingle(() -> reservedCnx.eval(script, numkeys, keys, args));
    }

    @Override
    public Single<String> evalList(final CharSequence script, final long numkeys,
                                   @RedisProtocolSupport.Key final Collection<? extends CharSequence> keys,
                                   final Collection<? extends CharSequence> args) {
        return blockingToSingle(() -> reservedCnx.evalList(script, numkeys, keys, args));
    }

    @Override
    public Single<String> evalLong(final CharSequence script, final long numkeys,
                                   @RedisProtocolSupport.Key final Collection<? extends CharSequence> keys,
                                   final Collection<? extends CharSequence> args) {
        return blockingToSingle(() -> reservedCnx.evalLong(script, numkeys, keys, args));
    }

    @Override
    public Single<String> evalsha(final CharSequence sha1, final long numkeys,
                                  @RedisProtocolSupport.Key final Collection<? extends CharSequence> keys,
                                  final Collection<? extends CharSequence> args) {
        return blockingToSingle(() -> reservedCnx.evalsha(sha1, numkeys, keys, args));
    }

    @Override
    public Single<String> evalshaList(final CharSequence sha1, final long numkeys,
                                      @RedisProtocolSupport.Key final Collection<? extends CharSequence> keys,
                                      final Collection<? extends CharSequence> args) {
        return blockingToSingle(() -> reservedCnx.evalshaList(sha1, numkeys, keys, args));
    }

    @Override
    public Single<String> evalshaLong(final CharSequence sha1, final long numkeys,
                                      @RedisProtocolSupport.Key final Collection<? extends CharSequence> keys,
                                      final Collection<? extends CharSequence> args) {
        return blockingToSingle(() -> reservedCnx.evalshaLong(sha1, numkeys, keys, args));
    }

    @Override
    public <T> Single<List<T>> exec() {
        return blockingToSingle(() -> reservedCnx.exec());
    }

    @Override
    public Single<String> exists(@RedisProtocolSupport.Key final CharSequence key) {
        return blockingToSingle(() -> reservedCnx.exists(key));
    }

    @Override
    public Single<String> exists(@RedisProtocolSupport.Key final CharSequence key1,
                                 @RedisProtocolSupport.Key final CharSequence key2) {
        return blockingToSingle(() -> reservedCnx.exists(key1, key2));
    }

    @Override
    public Single<String> exists(@RedisProtocolSupport.Key final CharSequence key1,
                                 @RedisProtocolSupport.Key final CharSequence key2,
                                 @RedisProtocolSupport.Key final CharSequence key3) {
        return blockingToSingle(() -> reservedCnx.exists(key1, key2, key3));
    }

    @Override
    public Single<String> exists(@RedisProtocolSupport.Key final Collection<? extends CharSequence> keys) {
        return blockingToSingle(() -> reservedCnx.exists(keys));
    }

    @Override
    public Single<String> expire(@RedisProtocolSupport.Key final CharSequence key, final long seconds) {
        return blockingToSingle(() -> reservedCnx.expire(key, seconds));
    }

    @Override
    public Single<String> expireat(@RedisProtocolSupport.Key final CharSequence key, final long timestamp) {
        return blockingToSingle(() -> reservedCnx.expireat(key, timestamp));
    }

    @Override
    public Single<String> flushall() {
        return blockingToSingle(() -> reservedCnx.flushall());
    }

    @Override
    public Single<String> flushall(@Nullable final RedisProtocolSupport.FlushallAsync async) {
        return blockingToSingle(() -> reservedCnx.flushall(async));
    }

    @Override
    public Single<String> flushdb() {
        return blockingToSingle(() -> reservedCnx.flushdb());
    }

    @Override
    public Single<String> flushdb(@Nullable final RedisProtocolSupport.FlushdbAsync async) {
        return blockingToSingle(() -> reservedCnx.flushdb(async));
    }

    @Override
    public Single<String> geoadd(@RedisProtocolSupport.Key final CharSequence key, final double longitude,
                                 final double latitude, final CharSequence member) {
        return blockingToSingle(() -> reservedCnx.geoadd(key, longitude, latitude, member));
    }

    @Override
    public Single<String> geoadd(@RedisProtocolSupport.Key final CharSequence key, final double longitude1,
                                 final double latitude1, final CharSequence member1, final double longitude2,
                                 final double latitude2, final CharSequence member2) {
        return blockingToSingle(
                    () -> reservedCnx.geoadd(key, longitude1, latitude1, member1, longitude2, latitude2, member2));
    }

    @Override
    public Single<String> geoadd(@RedisProtocolSupport.Key final CharSequence key, final double longitude1,
                                 final double latitude1, final CharSequence member1, final double longitude2,
                                 final double latitude2, final CharSequence member2, final double longitude3,
                                 final double latitude3, final CharSequence member3) {
        return blockingToSingle(() -> reservedCnx.geoadd(key, longitude1, latitude1, member1, longitude2, latitude2,
                    member2, longitude3, latitude3, member3));
    }

    @Override
    public Single<String> geoadd(@RedisProtocolSupport.Key final CharSequence key,
                                 final Collection<RedisProtocolSupport.LongitudeLatitudeMember> longitudeLatitudeMembers) {
        return blockingToSingle(() -> reservedCnx.geoadd(key, longitudeLatitudeMembers));
    }

    @Override
    public Single<String> geodist(@RedisProtocolSupport.Key final CharSequence key, final CharSequence member1,
                                  final CharSequence member2) {
        return blockingToSingle(() -> reservedCnx.geodist(key, member1, member2));
    }

    @Override
    public Single<String> geodist(@RedisProtocolSupport.Key final CharSequence key, final CharSequence member1,
                                  final CharSequence member2, @Nullable final CharSequence unit) {
        return blockingToSingle(() -> reservedCnx.geodist(key, member1, member2, unit));
    }

    @Override
    public Single<String> geohash(@RedisProtocolSupport.Key final CharSequence key, final CharSequence member) {
        return blockingToSingle(() -> reservedCnx.geohash(key, member));
    }

    @Override
    public Single<String> geohash(@RedisProtocolSupport.Key final CharSequence key, final CharSequence member1,
                                  final CharSequence member2) {
        return blockingToSingle(() -> reservedCnx.geohash(key, member1, member2));
    }

    @Override
    public Single<String> geohash(@RedisProtocolSupport.Key final CharSequence key, final CharSequence member1,
                                  final CharSequence member2, final CharSequence member3) {
        return blockingToSingle(() -> reservedCnx.geohash(key, member1, member2, member3));
    }

    @Override
    public Single<String> geohash(@RedisProtocolSupport.Key final CharSequence key,
                                  final Collection<? extends CharSequence> members) {
        return blockingToSingle(() -> reservedCnx.geohash(key, members));
    }

    @Override
    public Single<String> geopos(@RedisProtocolSupport.Key final CharSequence key, final CharSequence member) {
        return blockingToSingle(() -> reservedCnx.geopos(key, member));
    }

    @Override
    public Single<String> geopos(@RedisProtocolSupport.Key final CharSequence key, final CharSequence member1,
                                 final CharSequence member2) {
        return blockingToSingle(() -> reservedCnx.geopos(key, member1, member2));
    }

    @Override
    public Single<String> geopos(@RedisProtocolSupport.Key final CharSequence key, final CharSequence member1,
                                 final CharSequence member2, final CharSequence member3) {
        return blockingToSingle(() -> reservedCnx.geopos(key, member1, member2, member3));
    }

    @Override
    public Single<String> geopos(@RedisProtocolSupport.Key final CharSequence key,
                                 final Collection<? extends CharSequence> members) {
        return blockingToSingle(() -> reservedCnx.geopos(key, members));
    }

    @Override
    public Single<String> georadius(@RedisProtocolSupport.Key final CharSequence key, final double longitude,
                                    final double latitude, final double radius,
                                    final RedisProtocolSupport.GeoradiusUnit unit) {
        return blockingToSingle(() -> reservedCnx.georadius(key, longitude, latitude, radius, unit));
    }

    @Override
    public Single<String> georadius(@RedisProtocolSupport.Key final CharSequence key, final double longitude,
                                    final double latitude, final double radius,
                                    final RedisProtocolSupport.GeoradiusUnit unit,
                                    @Nullable final RedisProtocolSupport.GeoradiusWithcoord withcoord,
                                    @Nullable final RedisProtocolSupport.GeoradiusWithdist withdist,
                                    @Nullable final RedisProtocolSupport.GeoradiusWithhash withhash,
                                    @Nullable final Long count,
                                    @Nullable final RedisProtocolSupport.GeoradiusOrder order,
                                    @Nullable @RedisProtocolSupport.Key final CharSequence storeKey,
                                    @Nullable @RedisProtocolSupport.Key final CharSequence storedistKey) {
        return blockingToSingle(() -> reservedCnx.georadius(key, longitude, latitude, radius, unit, withcoord, withdist,
                    withhash, count, order, storeKey, storedistKey));
    }

    @Override
    public Single<String> georadiusbymember(@RedisProtocolSupport.Key final CharSequence key, final CharSequence member,
                                            final double radius,
                                            final RedisProtocolSupport.GeoradiusbymemberUnit unit) {
        return blockingToSingle(() -> reservedCnx.georadiusbymember(key, member, radius, unit));
    }

    @Override
    public Single<String> georadiusbymember(@RedisProtocolSupport.Key final CharSequence key, final CharSequence member,
                                            final double radius, final RedisProtocolSupport.GeoradiusbymemberUnit unit,
                                            @Nullable final RedisProtocolSupport.GeoradiusbymemberWithcoord withcoord,
                                            @Nullable final RedisProtocolSupport.GeoradiusbymemberWithdist withdist,
                                            @Nullable final RedisProtocolSupport.GeoradiusbymemberWithhash withhash,
                                            @Nullable final Long count,
                                            @Nullable final RedisProtocolSupport.GeoradiusbymemberOrder order,
                                            @Nullable @RedisProtocolSupport.Key final CharSequence storeKey,
                                            @Nullable @RedisProtocolSupport.Key final CharSequence storedistKey) {
        return blockingToSingle(() -> reservedCnx.georadiusbymember(key, member, radius, unit, withcoord, withdist,
                    withhash, count, order, storeKey, storedistKey));
    }

    @Override
    public Single<String> get(@RedisProtocolSupport.Key final CharSequence key) {
        return blockingToSingle(() -> reservedCnx.get(key));
    }

    @Override
    public Single<String> getbit(@RedisProtocolSupport.Key final CharSequence key, final long offset) {
        return blockingToSingle(() -> reservedCnx.getbit(key, offset));
    }

    @Override
    public Single<String> getrange(@RedisProtocolSupport.Key final CharSequence key, final long start, final long end) {
        return blockingToSingle(() -> reservedCnx.getrange(key, start, end));
    }

    @Override
    public Single<String> getset(@RedisProtocolSupport.Key final CharSequence key, final CharSequence value) {
        return blockingToSingle(() -> reservedCnx.getset(key, value));
    }

    @Override
    public Single<String> hdel(@RedisProtocolSupport.Key final CharSequence key, final CharSequence field) {
        return blockingToSingle(() -> reservedCnx.hdel(key, field));
    }

    @Override
    public Single<String> hdel(@RedisProtocolSupport.Key final CharSequence key, final CharSequence field1,
                               final CharSequence field2) {
        return blockingToSingle(() -> reservedCnx.hdel(key, field1, field2));
    }

    @Override
    public Single<String> hdel(@RedisProtocolSupport.Key final CharSequence key, final CharSequence field1,
                               final CharSequence field2, final CharSequence field3) {
        return blockingToSingle(() -> reservedCnx.hdel(key, field1, field2, field3));
    }

    @Override
    public Single<String> hdel(@RedisProtocolSupport.Key final CharSequence key,
                               final Collection<? extends CharSequence> fields) {
        return blockingToSingle(() -> reservedCnx.hdel(key, fields));
    }

    @Override
    public Single<String> hexists(@RedisProtocolSupport.Key final CharSequence key, final CharSequence field) {
        return blockingToSingle(() -> reservedCnx.hexists(key, field));
    }

    @Override
    public Single<String> hget(@RedisProtocolSupport.Key final CharSequence key, final CharSequence field) {
        return blockingToSingle(() -> reservedCnx.hget(key, field));
    }

    @Override
    public Single<String> hgetall(@RedisProtocolSupport.Key final CharSequence key) {
        return blockingToSingle(() -> reservedCnx.hgetall(key));
    }

    @Override
    public Single<String> hincrby(@RedisProtocolSupport.Key final CharSequence key, final CharSequence field,
                                  final long increment) {
        return blockingToSingle(() -> reservedCnx.hincrby(key, field, increment));
    }

    @Override
    public Single<String> hincrbyfloat(@RedisProtocolSupport.Key final CharSequence key, final CharSequence field,
                                       final double increment) {
        return blockingToSingle(() -> reservedCnx.hincrbyfloat(key, field, increment));
    }

    @Override
    public Single<String> hkeys(@RedisProtocolSupport.Key final CharSequence key) {
        return blockingToSingle(() -> reservedCnx.hkeys(key));
    }

    @Override
    public Single<String> hlen(@RedisProtocolSupport.Key final CharSequence key) {
        return blockingToSingle(() -> reservedCnx.hlen(key));
    }

    @Override
    public Single<String> hmget(@RedisProtocolSupport.Key final CharSequence key, final CharSequence field) {
        return blockingToSingle(() -> reservedCnx.hmget(key, field));
    }

    @Override
    public Single<String> hmget(@RedisProtocolSupport.Key final CharSequence key, final CharSequence field1,
                                final CharSequence field2) {
        return blockingToSingle(() -> reservedCnx.hmget(key, field1, field2));
    }

    @Override
    public Single<String> hmget(@RedisProtocolSupport.Key final CharSequence key, final CharSequence field1,
                                final CharSequence field2, final CharSequence field3) {
        return blockingToSingle(() -> reservedCnx.hmget(key, field1, field2, field3));
    }

    @Override
    public Single<String> hmget(@RedisProtocolSupport.Key final CharSequence key,
                                final Collection<? extends CharSequence> fields) {
        return blockingToSingle(() -> reservedCnx.hmget(key, fields));
    }

    @Override
    public Single<String> hmset(@RedisProtocolSupport.Key final CharSequence key, final CharSequence field,
                                final CharSequence value) {
        return blockingToSingle(() -> reservedCnx.hmset(key, field, value));
    }

    @Override
    public Single<String> hmset(@RedisProtocolSupport.Key final CharSequence key, final CharSequence field1,
                                final CharSequence value1, final CharSequence field2, final CharSequence value2) {
        return blockingToSingle(() -> reservedCnx.hmset(key, field1, value1, field2, value2));
    }

    @Override
    public Single<String> hmset(@RedisProtocolSupport.Key final CharSequence key, final CharSequence field1,
                                final CharSequence value1, final CharSequence field2, final CharSequence value2,
                                final CharSequence field3, final CharSequence value3) {
        return blockingToSingle(() -> reservedCnx.hmset(key, field1, value1, field2, value2, field3, value3));
    }

    @Override
    public Single<String> hmset(@RedisProtocolSupport.Key final CharSequence key,
                                final Collection<RedisProtocolSupport.FieldValue> fieldValues) {
        return blockingToSingle(() -> reservedCnx.hmset(key, fieldValues));
    }

    @Override
    public Single<String> hscan(@RedisProtocolSupport.Key final CharSequence key, final long cursor) {
        return blockingToSingle(() -> reservedCnx.hscan(key, cursor));
    }

    @Override
    public Single<String> hscan(@RedisProtocolSupport.Key final CharSequence key, final long cursor,
                                @Nullable final CharSequence matchPattern, @Nullable final Long count) {
        return blockingToSingle(() -> reservedCnx.hscan(key, cursor, matchPattern, count));
    }

    @Override
    public Single<String> hset(@RedisProtocolSupport.Key final CharSequence key, final CharSequence field,
                               final CharSequence value) {
        return blockingToSingle(() -> reservedCnx.hset(key, field, value));
    }

    @Override
    public Single<String> hsetnx(@RedisProtocolSupport.Key final CharSequence key, final CharSequence field,
                                 final CharSequence value) {
        return blockingToSingle(() -> reservedCnx.hsetnx(key, field, value));
    }

    @Override
    public Single<String> hstrlen(@RedisProtocolSupport.Key final CharSequence key, final CharSequence field) {
        return blockingToSingle(() -> reservedCnx.hstrlen(key, field));
    }

    @Override
    public Single<String> hvals(@RedisProtocolSupport.Key final CharSequence key) {
        return blockingToSingle(() -> reservedCnx.hvals(key));
    }

    @Override
    public Single<String> incr(@RedisProtocolSupport.Key final CharSequence key) {
        return blockingToSingle(() -> reservedCnx.incr(key));
    }

    @Override
    public Single<String> incrby(@RedisProtocolSupport.Key final CharSequence key, final long increment) {
        return blockingToSingle(() -> reservedCnx.incrby(key, increment));
    }

    @Override
    public Single<String> incrbyfloat(@RedisProtocolSupport.Key final CharSequence key, final double increment) {
        return blockingToSingle(() -> reservedCnx.incrbyfloat(key, increment));
    }

    @Override
    public Single<String> info() {
        return blockingToSingle(() -> reservedCnx.info());
    }

    @Override
    public Single<String> info(@Nullable final CharSequence section) {
        return blockingToSingle(() -> reservedCnx.info(section));
    }

    @Override
    public Single<String> keys(final CharSequence pattern) {
        return blockingToSingle(() -> reservedCnx.keys(pattern));
    }

    @Override
    public Single<String> lastsave() {
        return blockingToSingle(() -> reservedCnx.lastsave());
    }

    @Override
    public Single<String> lindex(@RedisProtocolSupport.Key final CharSequence key, final long index) {
        return blockingToSingle(() -> reservedCnx.lindex(key, index));
    }

    @Override
    public Single<String> linsert(@RedisProtocolSupport.Key final CharSequence key,
                                  final RedisProtocolSupport.LinsertWhere where, final CharSequence pivot,
                                  final CharSequence value) {
        return blockingToSingle(() -> reservedCnx.linsert(key, where, pivot, value));
    }

    @Override
    public Single<String> llen(@RedisProtocolSupport.Key final CharSequence key) {
        return blockingToSingle(() -> reservedCnx.llen(key));
    }

    @Override
    public Single<String> lpop(@RedisProtocolSupport.Key final CharSequence key) {
        return blockingToSingle(() -> reservedCnx.lpop(key));
    }

    @Override
    public Single<String> lpush(@RedisProtocolSupport.Key final CharSequence key, final CharSequence value) {
        return blockingToSingle(() -> reservedCnx.lpush(key, value));
    }

    @Override
    public Single<String> lpush(@RedisProtocolSupport.Key final CharSequence key, final CharSequence value1,
                                final CharSequence value2) {
        return blockingToSingle(() -> reservedCnx.lpush(key, value1, value2));
    }

    @Override
    public Single<String> lpush(@RedisProtocolSupport.Key final CharSequence key, final CharSequence value1,
                                final CharSequence value2, final CharSequence value3) {
        return blockingToSingle(() -> reservedCnx.lpush(key, value1, value2, value3));
    }

    @Override
    public Single<String> lpush(@RedisProtocolSupport.Key final CharSequence key,
                                final Collection<? extends CharSequence> values) {
        return blockingToSingle(() -> reservedCnx.lpush(key, values));
    }

    @Override
    public Single<String> lpushx(@RedisProtocolSupport.Key final CharSequence key, final CharSequence value) {
        return blockingToSingle(() -> reservedCnx.lpushx(key, value));
    }

    @Override
    public Single<String> lrange(@RedisProtocolSupport.Key final CharSequence key, final long start, final long stop) {
        return blockingToSingle(() -> reservedCnx.lrange(key, start, stop));
    }

    @Override
    public Single<String> lrem(@RedisProtocolSupport.Key final CharSequence key, final long count,
                               final CharSequence value) {
        return blockingToSingle(() -> reservedCnx.lrem(key, count, value));
    }

    @Override
    public Single<String> lset(@RedisProtocolSupport.Key final CharSequence key, final long index,
                               final CharSequence value) {
        return blockingToSingle(() -> reservedCnx.lset(key, index, value));
    }

    @Override
    public Single<String> ltrim(@RedisProtocolSupport.Key final CharSequence key, final long start, final long stop) {
        return blockingToSingle(() -> reservedCnx.ltrim(key, start, stop));
    }

    @Override
    public Single<String> memoryDoctor() {
        return blockingToSingle(() -> reservedCnx.memoryDoctor());
    }

    @Override
    public Single<String> memoryHelp() {
        return blockingToSingle(() -> reservedCnx.memoryHelp());
    }

    @Override
    public Single<String> memoryMallocStats() {
        return blockingToSingle(() -> reservedCnx.memoryMallocStats());
    }

    @Override
    public Single<String> memoryPurge() {
        return blockingToSingle(() -> reservedCnx.memoryPurge());
    }

    @Override
    public Single<String> memoryStats() {
        return blockingToSingle(() -> reservedCnx.memoryStats());
    }

    @Override
    public Single<String> memoryUsage(@RedisProtocolSupport.Key final CharSequence key) {
        return blockingToSingle(() -> reservedCnx.memoryUsage(key));
    }

    @Override
    public Single<String> memoryUsage(@RedisProtocolSupport.Key final CharSequence key,
                                      @Nullable final Long samplesCount) {
        return blockingToSingle(() -> reservedCnx.memoryUsage(key, samplesCount));
    }

    @Override
    public Single<String> mget(@RedisProtocolSupport.Key final CharSequence key) {
        return blockingToSingle(() -> reservedCnx.mget(key));
    }

    @Override
    public Single<String> mget(@RedisProtocolSupport.Key final CharSequence key1,
                               @RedisProtocolSupport.Key final CharSequence key2) {
        return blockingToSingle(() -> reservedCnx.mget(key1, key2));
    }

    @Override
    public Single<String> mget(@RedisProtocolSupport.Key final CharSequence key1,
                               @RedisProtocolSupport.Key final CharSequence key2,
                               @RedisProtocolSupport.Key final CharSequence key3) {
        return blockingToSingle(() -> reservedCnx.mget(key1, key2, key3));
    }

    @Override
    public Single<String> mget(@RedisProtocolSupport.Key final Collection<? extends CharSequence> keys) {
        return blockingToSingle(() -> reservedCnx.mget(keys));
    }

    @Override
    public Single<String> move(@RedisProtocolSupport.Key final CharSequence key, final long db) {
        return blockingToSingle(() -> reservedCnx.move(key, db));
    }

    @Override
    public Single<String> mset(@RedisProtocolSupport.Key final CharSequence key, final CharSequence value) {
        return blockingToSingle(() -> reservedCnx.mset(key, value));
    }

    @Override
    public Single<String> mset(@RedisProtocolSupport.Key final CharSequence key1, final CharSequence value1,
                               @RedisProtocolSupport.Key final CharSequence key2, final CharSequence value2) {
        return blockingToSingle(() -> reservedCnx.mset(key1, value1, key2, value2));
    }

    @Override
    public Single<String> mset(@RedisProtocolSupport.Key final CharSequence key1, final CharSequence value1,
                               @RedisProtocolSupport.Key final CharSequence key2, final CharSequence value2,
                               @RedisProtocolSupport.Key final CharSequence key3, final CharSequence value3) {
        return blockingToSingle(() -> reservedCnx.mset(key1, value1, key2, value2, key3, value3));
    }

    @Override
    public Single<String> mset(final Collection<RedisProtocolSupport.KeyValue> keyValues) {
        return blockingToSingle(() -> reservedCnx.mset(keyValues));
    }

    @Override
    public Single<String> msetnx(@RedisProtocolSupport.Key final CharSequence key, final CharSequence value) {
        return blockingToSingle(() -> reservedCnx.msetnx(key, value));
    }

    @Override
    public Single<String> msetnx(@RedisProtocolSupport.Key final CharSequence key1, final CharSequence value1,
                                 @RedisProtocolSupport.Key final CharSequence key2, final CharSequence value2) {
        return blockingToSingle(() -> reservedCnx.msetnx(key1, value1, key2, value2));
    }

    @Override
    public Single<String> msetnx(@RedisProtocolSupport.Key final CharSequence key1, final CharSequence value1,
                                 @RedisProtocolSupport.Key final CharSequence key2, final CharSequence value2,
                                 @RedisProtocolSupport.Key final CharSequence key3, final CharSequence value3) {
        return blockingToSingle(() -> reservedCnx.msetnx(key1, value1, key2, value2, key3, value3));
    }

    @Override
    public Single<String> msetnx(final Collection<RedisProtocolSupport.KeyValue> keyValues) {
        return blockingToSingle(() -> reservedCnx.msetnx(keyValues));
    }

    @Override
    public Single<String> objectEncoding(@RedisProtocolSupport.Key final CharSequence key) {
        return blockingToSingle(() -> reservedCnx.objectEncoding(key));
    }

    @Override
    public Single<String> objectFreq(@RedisProtocolSupport.Key final CharSequence key) {
        return blockingToSingle(() -> reservedCnx.objectFreq(key));
    }

    @Override
    public Single<String> objectHelp() {
        return blockingToSingle(() -> reservedCnx.objectHelp());
    }

    @Override
    public Single<String> objectIdletime(@RedisProtocolSupport.Key final CharSequence key) {
        return blockingToSingle(() -> reservedCnx.objectIdletime(key));
    }

    @Override
    public Single<String> objectRefcount(@RedisProtocolSupport.Key final CharSequence key) {
        return blockingToSingle(() -> reservedCnx.objectRefcount(key));
    }

    @Override
    public Single<String> persist(@RedisProtocolSupport.Key final CharSequence key) {
        return blockingToSingle(() -> reservedCnx.persist(key));
    }

    @Override
    public Single<String> pexpire(@RedisProtocolSupport.Key final CharSequence key, final long milliseconds) {
        return blockingToSingle(() -> reservedCnx.pexpire(key, milliseconds));
    }

    @Override
    public Single<String> pexpireat(@RedisProtocolSupport.Key final CharSequence key,
                                    final long millisecondsTimestamp) {
        return blockingToSingle(() -> reservedCnx.pexpireat(key, millisecondsTimestamp));
    }

    @Override
    public Single<String> pfadd(@RedisProtocolSupport.Key final CharSequence key, final CharSequence element) {
        return blockingToSingle(() -> reservedCnx.pfadd(key, element));
    }

    @Override
    public Single<String> pfadd(@RedisProtocolSupport.Key final CharSequence key, final CharSequence element1,
                                final CharSequence element2) {
        return blockingToSingle(() -> reservedCnx.pfadd(key, element1, element2));
    }

    @Override
    public Single<String> pfadd(@RedisProtocolSupport.Key final CharSequence key, final CharSequence element1,
                                final CharSequence element2, final CharSequence element3) {
        return blockingToSingle(() -> reservedCnx.pfadd(key, element1, element2, element3));
    }

    @Override
    public Single<String> pfadd(@RedisProtocolSupport.Key final CharSequence key,
                                final Collection<? extends CharSequence> elements) {
        return blockingToSingle(() -> reservedCnx.pfadd(key, elements));
    }

    @Override
    public Single<String> pfcount(@RedisProtocolSupport.Key final CharSequence key) {
        return blockingToSingle(() -> reservedCnx.pfcount(key));
    }

    @Override
    public Single<String> pfcount(@RedisProtocolSupport.Key final CharSequence key1,
                                  @RedisProtocolSupport.Key final CharSequence key2) {
        return blockingToSingle(() -> reservedCnx.pfcount(key1, key2));
    }

    @Override
    public Single<String> pfcount(@RedisProtocolSupport.Key final CharSequence key1,
                                  @RedisProtocolSupport.Key final CharSequence key2,
                                  @RedisProtocolSupport.Key final CharSequence key3) {
        return blockingToSingle(() -> reservedCnx.pfcount(key1, key2, key3));
    }

    @Override
    public Single<String> pfcount(@RedisProtocolSupport.Key final Collection<? extends CharSequence> keys) {
        return blockingToSingle(() -> reservedCnx.pfcount(keys));
    }

    @Override
    public Single<String> pfmerge(@RedisProtocolSupport.Key final CharSequence destkey,
                                  @RedisProtocolSupport.Key final CharSequence sourcekey) {
        return blockingToSingle(() -> reservedCnx.pfmerge(destkey, sourcekey));
    }

    @Override
    public Single<String> pfmerge(@RedisProtocolSupport.Key final CharSequence destkey,
                                  @RedisProtocolSupport.Key final CharSequence sourcekey1,
                                  @RedisProtocolSupport.Key final CharSequence sourcekey2) {
        return blockingToSingle(() -> reservedCnx.pfmerge(destkey, sourcekey1, sourcekey2));
    }

    @Override
    public Single<String> pfmerge(@RedisProtocolSupport.Key final CharSequence destkey,
                                  @RedisProtocolSupport.Key final CharSequence sourcekey1,
                                  @RedisProtocolSupport.Key final CharSequence sourcekey2,
                                  @RedisProtocolSupport.Key final CharSequence sourcekey3) {
        return blockingToSingle(() -> reservedCnx.pfmerge(destkey, sourcekey1, sourcekey2, sourcekey3));
    }

    @Override
    public Single<String> pfmerge(@RedisProtocolSupport.Key final CharSequence destkey,
                                  @RedisProtocolSupport.Key final Collection<? extends CharSequence> sourcekeys) {
        return blockingToSingle(() -> reservedCnx.pfmerge(destkey, sourcekeys));
    }

    @Override
    public Single<String> ping() {
        return blockingToSingle(() -> reservedCnx.ping());
    }

    @Override
    public Single<String> ping(final CharSequence message) {
        return blockingToSingle(() -> reservedCnx.ping(message));
    }

    @Override
    public Single<String> psetex(@RedisProtocolSupport.Key final CharSequence key, final long milliseconds,
                                 final CharSequence value) {
        return blockingToSingle(() -> reservedCnx.psetex(key, milliseconds, value));
    }

    @Override
    public Single<String> pttl(@RedisProtocolSupport.Key final CharSequence key) {
        return blockingToSingle(() -> reservedCnx.pttl(key));
    }

    @Override
    public Single<String> publish(final CharSequence channel, final CharSequence message) {
        return blockingToSingle(() -> reservedCnx.publish(channel, message));
    }

    @Override
    public Single<String> pubsubChannels() {
        return blockingToSingle(() -> reservedCnx.pubsubChannels());
    }

    @Override
    public Single<String> pubsubChannels(@Nullable final CharSequence pattern) {
        return blockingToSingle(() -> reservedCnx.pubsubChannels(pattern));
    }

    @Override
    public Single<String> pubsubChannels(@Nullable final CharSequence pattern1, @Nullable final CharSequence pattern2) {
        return blockingToSingle(() -> reservedCnx.pubsubChannels(pattern1, pattern2));
    }

    @Override
    public Single<String> pubsubChannels(@Nullable final CharSequence pattern1, @Nullable final CharSequence pattern2,
                                         @Nullable final CharSequence pattern3) {
        return blockingToSingle(() -> reservedCnx.pubsubChannels(pattern1, pattern2, pattern3));
    }

    @Override
    public Single<String> pubsubChannels(final Collection<? extends CharSequence> patterns) {
        return blockingToSingle(() -> reservedCnx.pubsubChannels(patterns));
    }

    @Override
    public Single<String> pubsubNumsub() {
        return blockingToSingle(() -> reservedCnx.pubsubNumsub());
    }

    @Override
    public Single<String> pubsubNumsub(@Nullable final CharSequence channel) {
        return blockingToSingle(() -> reservedCnx.pubsubNumsub(channel));
    }

    @Override
    public Single<String> pubsubNumsub(@Nullable final CharSequence channel1, @Nullable final CharSequence channel2) {
        return blockingToSingle(() -> reservedCnx.pubsubNumsub(channel1, channel2));
    }

    @Override
    public Single<String> pubsubNumsub(@Nullable final CharSequence channel1, @Nullable final CharSequence channel2,
                                       @Nullable final CharSequence channel3) {
        return blockingToSingle(() -> reservedCnx.pubsubNumsub(channel1, channel2, channel3));
    }

    @Override
    public Single<String> pubsubNumsub(final Collection<? extends CharSequence> channels) {
        return blockingToSingle(() -> reservedCnx.pubsubNumsub(channels));
    }

    @Override
    public Single<String> pubsubNumpat() {
        return blockingToSingle(() -> reservedCnx.pubsubNumpat());
    }

    @Override
    public Single<String> randomkey() {
        return blockingToSingle(() -> reservedCnx.randomkey());
    }

    @Override
    public Single<String> readonly() {
        return blockingToSingle(() -> reservedCnx.readonly());
    }

    @Override
    public Single<String> readwrite() {
        return blockingToSingle(() -> reservedCnx.readwrite());
    }

    @Override
    public Single<String> rename(@RedisProtocolSupport.Key final CharSequence key,
                                 @RedisProtocolSupport.Key final CharSequence newkey) {
        return blockingToSingle(() -> reservedCnx.rename(key, newkey));
    }

    @Override
    public Single<String> renamenx(@RedisProtocolSupport.Key final CharSequence key,
                                   @RedisProtocolSupport.Key final CharSequence newkey) {
        return blockingToSingle(() -> reservedCnx.renamenx(key, newkey));
    }

    @Override
    public Single<String> restore(@RedisProtocolSupport.Key final CharSequence key, final long ttl,
                                  final CharSequence serializedValue) {
        return blockingToSingle(() -> reservedCnx.restore(key, ttl, serializedValue));
    }

    @Override
    public Single<String> restore(@RedisProtocolSupport.Key final CharSequence key, final long ttl,
                                  final CharSequence serializedValue,
                                  @Nullable final RedisProtocolSupport.RestoreReplace replace) {
        return blockingToSingle(() -> reservedCnx.restore(key, ttl, serializedValue, replace));
    }

    @Override
    public Single<String> role() {
        return blockingToSingle(() -> reservedCnx.role());
    }

    @Override
    public Single<String> rpop(@RedisProtocolSupport.Key final CharSequence key) {
        return blockingToSingle(() -> reservedCnx.rpop(key));
    }

    @Override
    public Single<String> rpoplpush(@RedisProtocolSupport.Key final CharSequence source,
                                    @RedisProtocolSupport.Key final CharSequence destination) {
        return blockingToSingle(() -> reservedCnx.rpoplpush(source, destination));
    }

    @Override
    public Single<String> rpush(@RedisProtocolSupport.Key final CharSequence key, final CharSequence value) {
        return blockingToSingle(() -> reservedCnx.rpush(key, value));
    }

    @Override
    public Single<String> rpush(@RedisProtocolSupport.Key final CharSequence key, final CharSequence value1,
                                final CharSequence value2) {
        return blockingToSingle(() -> reservedCnx.rpush(key, value1, value2));
    }

    @Override
    public Single<String> rpush(@RedisProtocolSupport.Key final CharSequence key, final CharSequence value1,
                                final CharSequence value2, final CharSequence value3) {
        return blockingToSingle(() -> reservedCnx.rpush(key, value1, value2, value3));
    }

    @Override
    public Single<String> rpush(@RedisProtocolSupport.Key final CharSequence key,
                                final Collection<? extends CharSequence> values) {
        return blockingToSingle(() -> reservedCnx.rpush(key, values));
    }

    @Override
    public Single<String> rpushx(@RedisProtocolSupport.Key final CharSequence key, final CharSequence value) {
        return blockingToSingle(() -> reservedCnx.rpushx(key, value));
    }

    @Override
    public Single<String> sadd(@RedisProtocolSupport.Key final CharSequence key, final CharSequence member) {
        return blockingToSingle(() -> reservedCnx.sadd(key, member));
    }

    @Override
    public Single<String> sadd(@RedisProtocolSupport.Key final CharSequence key, final CharSequence member1,
                               final CharSequence member2) {
        return blockingToSingle(() -> reservedCnx.sadd(key, member1, member2));
    }

    @Override
    public Single<String> sadd(@RedisProtocolSupport.Key final CharSequence key, final CharSequence member1,
                               final CharSequence member2, final CharSequence member3) {
        return blockingToSingle(() -> reservedCnx.sadd(key, member1, member2, member3));
    }

    @Override
    public Single<String> sadd(@RedisProtocolSupport.Key final CharSequence key,
                               final Collection<? extends CharSequence> members) {
        return blockingToSingle(() -> reservedCnx.sadd(key, members));
    }

    @Override
    public Single<String> save() {
        return blockingToSingle(() -> reservedCnx.save());
    }

    @Override
    public Single<String> scan(final long cursor) {
        return blockingToSingle(() -> reservedCnx.scan(cursor));
    }

    @Override
    public Single<String> scan(final long cursor, @Nullable final CharSequence matchPattern,
                               @Nullable final Long count) {
        return blockingToSingle(() -> reservedCnx.scan(cursor, matchPattern, count));
    }

    @Override
    public Single<String> scard(@RedisProtocolSupport.Key final CharSequence key) {
        return blockingToSingle(() -> reservedCnx.scard(key));
    }

    @Override
    public Single<String> scriptDebug(final RedisProtocolSupport.ScriptDebugMode mode) {
        return blockingToSingle(() -> reservedCnx.scriptDebug(mode));
    }

    @Override
    public Single<String> scriptExists(final CharSequence sha1) {
        return blockingToSingle(() -> reservedCnx.scriptExists(sha1));
    }

    @Override
    public Single<String> scriptExists(final CharSequence sha11, final CharSequence sha12) {
        return blockingToSingle(() -> reservedCnx.scriptExists(sha11, sha12));
    }

    @Override
    public Single<String> scriptExists(final CharSequence sha11, final CharSequence sha12, final CharSequence sha13) {
        return blockingToSingle(() -> reservedCnx.scriptExists(sha11, sha12, sha13));
    }

    @Override
    public Single<String> scriptExists(final Collection<? extends CharSequence> sha1s) {
        return blockingToSingle(() -> reservedCnx.scriptExists(sha1s));
    }

    @Override
    public Single<String> scriptFlush() {
        return blockingToSingle(() -> reservedCnx.scriptFlush());
    }

    @Override
    public Single<String> scriptKill() {
        return blockingToSingle(() -> reservedCnx.scriptKill());
    }

    @Override
    public Single<String> scriptLoad(final CharSequence script) {
        return blockingToSingle(() -> reservedCnx.scriptLoad(script));
    }

    @Override
    public Single<String> sdiff(@RedisProtocolSupport.Key final CharSequence firstkey) {
        return blockingToSingle(() -> reservedCnx.sdiff(firstkey));
    }

    @Override
    public Single<String> sdiff(@RedisProtocolSupport.Key final CharSequence firstkey,
                                @Nullable @RedisProtocolSupport.Key final CharSequence otherkey) {
        return blockingToSingle(() -> reservedCnx.sdiff(firstkey, otherkey));
    }

    @Override
    public Single<String> sdiff(@RedisProtocolSupport.Key final CharSequence firstkey,
                                @Nullable @RedisProtocolSupport.Key final CharSequence otherkey1,
                                @Nullable @RedisProtocolSupport.Key final CharSequence otherkey2) {
        return blockingToSingle(() -> reservedCnx.sdiff(firstkey, otherkey1, otherkey2));
    }

    @Override
    public Single<String> sdiff(@RedisProtocolSupport.Key final CharSequence firstkey,
                                @Nullable @RedisProtocolSupport.Key final CharSequence otherkey1,
                                @Nullable @RedisProtocolSupport.Key final CharSequence otherkey2,
                                @Nullable @RedisProtocolSupport.Key final CharSequence otherkey3) {
        return blockingToSingle(() -> reservedCnx.sdiff(firstkey, otherkey1, otherkey2, otherkey3));
    }

    @Override
    public Single<String> sdiff(@RedisProtocolSupport.Key final CharSequence firstkey,
                                @RedisProtocolSupport.Key final Collection<? extends CharSequence> otherkeys) {
        return blockingToSingle(() -> reservedCnx.sdiff(firstkey, otherkeys));
    }

    @Override
    public Single<String> sdiffstore(@RedisProtocolSupport.Key final CharSequence destination,
                                     @RedisProtocolSupport.Key final CharSequence firstkey) {
        return blockingToSingle(() -> reservedCnx.sdiffstore(destination, firstkey));
    }

    @Override
    public Single<String> sdiffstore(@RedisProtocolSupport.Key final CharSequence destination,
                                     @RedisProtocolSupport.Key final CharSequence firstkey,
                                     @Nullable @RedisProtocolSupport.Key final CharSequence otherkey) {
        return blockingToSingle(() -> reservedCnx.sdiffstore(destination, firstkey, otherkey));
    }

    @Override
    public Single<String> sdiffstore(@RedisProtocolSupport.Key final CharSequence destination,
                                     @RedisProtocolSupport.Key final CharSequence firstkey,
                                     @Nullable @RedisProtocolSupport.Key final CharSequence otherkey1,
                                     @Nullable @RedisProtocolSupport.Key final CharSequence otherkey2) {
        return blockingToSingle(() -> reservedCnx.sdiffstore(destination, firstkey, otherkey1, otherkey2));
    }

    @Override
    public Single<String> sdiffstore(@RedisProtocolSupport.Key final CharSequence destination,
                                     @RedisProtocolSupport.Key final CharSequence firstkey,
                                     @Nullable @RedisProtocolSupport.Key final CharSequence otherkey1,
                                     @Nullable @RedisProtocolSupport.Key final CharSequence otherkey2,
                                     @Nullable @RedisProtocolSupport.Key final CharSequence otherkey3) {
        return blockingToSingle(() -> reservedCnx.sdiffstore(destination, firstkey, otherkey1, otherkey2, otherkey3));
    }

    @Override
    public Single<String> sdiffstore(@RedisProtocolSupport.Key final CharSequence destination,
                                     @RedisProtocolSupport.Key final CharSequence firstkey,
                                     @RedisProtocolSupport.Key final Collection<? extends CharSequence> otherkeys) {
        return blockingToSingle(() -> reservedCnx.sdiffstore(destination, firstkey, otherkeys));
    }

    @Override
    public Single<String> select(final long index) {
        return blockingToSingle(() -> reservedCnx.select(index));
    }

    @Override
    public Single<String> set(@RedisProtocolSupport.Key final CharSequence key, final CharSequence value) {
        return blockingToSingle(() -> reservedCnx.set(key, value));
    }

    @Override
    public Single<String> set(@RedisProtocolSupport.Key final CharSequence key, final CharSequence value,
                              @Nullable final RedisProtocolSupport.ExpireDuration expireDuration,
                              @Nullable final RedisProtocolSupport.SetCondition condition) {
        return blockingToSingle(() -> reservedCnx.set(key, value, expireDuration, condition));
    }

    @Override
    public Single<String> setbit(@RedisProtocolSupport.Key final CharSequence key, final long offset,
                                 final CharSequence value) {
        return blockingToSingle(() -> reservedCnx.setbit(key, offset, value));
    }

    @Override
    public Single<String> setex(@RedisProtocolSupport.Key final CharSequence key, final long seconds,
                                final CharSequence value) {
        return blockingToSingle(() -> reservedCnx.setex(key, seconds, value));
    }

    @Override
    public Single<String> setnx(@RedisProtocolSupport.Key final CharSequence key, final CharSequence value) {
        return blockingToSingle(() -> reservedCnx.setnx(key, value));
    }

    @Override
    public Single<String> setrange(@RedisProtocolSupport.Key final CharSequence key, final long offset,
                                   final CharSequence value) {
        return blockingToSingle(() -> reservedCnx.setrange(key, offset, value));
    }

    @Override
    public Single<String> shutdown() {
        return blockingToSingle(() -> reservedCnx.shutdown());
    }

    @Override
    public Single<String> shutdown(@Nullable final RedisProtocolSupport.ShutdownSaveMode saveMode) {
        return blockingToSingle(() -> reservedCnx.shutdown(saveMode));
    }

    @Override
    public Single<String> sinter(@RedisProtocolSupport.Key final CharSequence key) {
        return blockingToSingle(() -> reservedCnx.sinter(key));
    }

    @Override
    public Single<String> sinter(@RedisProtocolSupport.Key final CharSequence key1,
                                 @RedisProtocolSupport.Key final CharSequence key2) {
        return blockingToSingle(() -> reservedCnx.sinter(key1, key2));
    }

    @Override
    public Single<String> sinter(@RedisProtocolSupport.Key final CharSequence key1,
                                 @RedisProtocolSupport.Key final CharSequence key2,
                                 @RedisProtocolSupport.Key final CharSequence key3) {
        return blockingToSingle(() -> reservedCnx.sinter(key1, key2, key3));
    }

    @Override
    public Single<String> sinter(@RedisProtocolSupport.Key final Collection<? extends CharSequence> keys) {
        return blockingToSingle(() -> reservedCnx.sinter(keys));
    }

    @Override
    public Single<String> sinterstore(@RedisProtocolSupport.Key final CharSequence destination,
                                      @RedisProtocolSupport.Key final CharSequence key) {
        return blockingToSingle(() -> reservedCnx.sinterstore(destination, key));
    }

    @Override
    public Single<String> sinterstore(@RedisProtocolSupport.Key final CharSequence destination,
                                      @RedisProtocolSupport.Key final CharSequence key1,
                                      @RedisProtocolSupport.Key final CharSequence key2) {
        return blockingToSingle(() -> reservedCnx.sinterstore(destination, key1, key2));
    }

    @Override
    public Single<String> sinterstore(@RedisProtocolSupport.Key final CharSequence destination,
                                      @RedisProtocolSupport.Key final CharSequence key1,
                                      @RedisProtocolSupport.Key final CharSequence key2,
                                      @RedisProtocolSupport.Key final CharSequence key3) {
        return blockingToSingle(() -> reservedCnx.sinterstore(destination, key1, key2, key3));
    }

    @Override
    public Single<String> sinterstore(@RedisProtocolSupport.Key final CharSequence destination,
                                      @RedisProtocolSupport.Key final Collection<? extends CharSequence> keys) {
        return blockingToSingle(() -> reservedCnx.sinterstore(destination, keys));
    }

    @Override
    public Single<String> sismember(@RedisProtocolSupport.Key final CharSequence key, final CharSequence member) {
        return blockingToSingle(() -> reservedCnx.sismember(key, member));
    }

    @Override
    public Single<String> slaveof(final CharSequence host, final CharSequence port) {
        return blockingToSingle(() -> reservedCnx.slaveof(host, port));
    }

    @Override
    public Single<String> slowlog(final CharSequence subcommand) {
        return blockingToSingle(() -> reservedCnx.slowlog(subcommand));
    }

    @Override
    public Single<String> slowlog(final CharSequence subcommand, @Nullable final CharSequence argument) {
        return blockingToSingle(() -> reservedCnx.slowlog(subcommand, argument));
    }

    @Override
    public Single<String> smembers(@RedisProtocolSupport.Key final CharSequence key) {
        return blockingToSingle(() -> reservedCnx.smembers(key));
    }

    @Override
    public Single<String> smove(@RedisProtocolSupport.Key final CharSequence source,
                                @RedisProtocolSupport.Key final CharSequence destination, final CharSequence member) {
        return blockingToSingle(() -> reservedCnx.smove(source, destination, member));
    }

    @Override
    public Single<String> sort(@RedisProtocolSupport.Key final CharSequence key) {
        return blockingToSingle(() -> reservedCnx.sort(key));
    }

    @Override
    public Single<String> sort(@RedisProtocolSupport.Key final CharSequence key, @Nullable final CharSequence byPattern,
                               @Nullable final RedisProtocolSupport.OffsetCount offsetCount,
                               final Collection<? extends CharSequence> getPatterns,
                               @Nullable final RedisProtocolSupport.SortOrder order,
                               @Nullable final RedisProtocolSupport.SortSorting sorting) {
        return blockingToSingle(() -> reservedCnx.sort(key, byPattern, offsetCount, getPatterns, order, sorting));
    }

    @Override
    public Single<String> sort(@RedisProtocolSupport.Key final CharSequence key,
                               @RedisProtocolSupport.Key final CharSequence storeDestination) {
        return blockingToSingle(() -> reservedCnx.sort(key, storeDestination));
    }

    @Override
    public Single<String> sort(@RedisProtocolSupport.Key final CharSequence key,
                               @RedisProtocolSupport.Key final CharSequence storeDestination,
                               @Nullable final CharSequence byPattern,
                               @Nullable final RedisProtocolSupport.OffsetCount offsetCount,
                               final Collection<? extends CharSequence> getPatterns,
                               @Nullable final RedisProtocolSupport.SortOrder order,
                               @Nullable final RedisProtocolSupport.SortSorting sorting) {
        return blockingToSingle(
                    () -> reservedCnx.sort(key, storeDestination, byPattern, offsetCount, getPatterns, order, sorting));
    }

    @Override
    public Single<String> spop(@RedisProtocolSupport.Key final CharSequence key) {
        return blockingToSingle(() -> reservedCnx.spop(key));
    }

    @Override
    public Single<String> spop(@RedisProtocolSupport.Key final CharSequence key, @Nullable final Long count) {
        return blockingToSingle(() -> reservedCnx.spop(key, count));
    }

    @Override
    public Single<String> srandmember(@RedisProtocolSupport.Key final CharSequence key) {
        return blockingToSingle(() -> reservedCnx.srandmember(key));
    }

    @Override
    public Single<String> srandmember(@RedisProtocolSupport.Key final CharSequence key, final long count) {
        return blockingToSingle(() -> reservedCnx.srandmember(key, count));
    }

    @Override
    public Single<String> srem(@RedisProtocolSupport.Key final CharSequence key, final CharSequence member) {
        return blockingToSingle(() -> reservedCnx.srem(key, member));
    }

    @Override
    public Single<String> srem(@RedisProtocolSupport.Key final CharSequence key, final CharSequence member1,
                               final CharSequence member2) {
        return blockingToSingle(() -> reservedCnx.srem(key, member1, member2));
    }

    @Override
    public Single<String> srem(@RedisProtocolSupport.Key final CharSequence key, final CharSequence member1,
                               final CharSequence member2, final CharSequence member3) {
        return blockingToSingle(() -> reservedCnx.srem(key, member1, member2, member3));
    }

    @Override
    public Single<String> srem(@RedisProtocolSupport.Key final CharSequence key,
                               final Collection<? extends CharSequence> members) {
        return blockingToSingle(() -> reservedCnx.srem(key, members));
    }

    @Override
    public Single<String> sscan(@RedisProtocolSupport.Key final CharSequence key, final long cursor) {
        return blockingToSingle(() -> reservedCnx.sscan(key, cursor));
    }

    @Override
    public Single<String> sscan(@RedisProtocolSupport.Key final CharSequence key, final long cursor,
                                @Nullable final CharSequence matchPattern, @Nullable final Long count) {
        return blockingToSingle(() -> reservedCnx.sscan(key, cursor, matchPattern, count));
    }

    @Override
    public Single<String> strlen(@RedisProtocolSupport.Key final CharSequence key) {
        return blockingToSingle(() -> reservedCnx.strlen(key));
    }

    @Override
    public Single<String> sunion(@RedisProtocolSupport.Key final CharSequence key) {
        return blockingToSingle(() -> reservedCnx.sunion(key));
    }

    @Override
    public Single<String> sunion(@RedisProtocolSupport.Key final CharSequence key1,
                                 @RedisProtocolSupport.Key final CharSequence key2) {
        return blockingToSingle(() -> reservedCnx.sunion(key1, key2));
    }

    @Override
    public Single<String> sunion(@RedisProtocolSupport.Key final CharSequence key1,
                                 @RedisProtocolSupport.Key final CharSequence key2,
                                 @RedisProtocolSupport.Key final CharSequence key3) {
        return blockingToSingle(() -> reservedCnx.sunion(key1, key2, key3));
    }

    @Override
    public Single<String> sunion(@RedisProtocolSupport.Key final Collection<? extends CharSequence> keys) {
        return blockingToSingle(() -> reservedCnx.sunion(keys));
    }

    @Override
    public Single<String> sunionstore(@RedisProtocolSupport.Key final CharSequence destination,
                                      @RedisProtocolSupport.Key final CharSequence key) {
        return blockingToSingle(() -> reservedCnx.sunionstore(destination, key));
    }

    @Override
    public Single<String> sunionstore(@RedisProtocolSupport.Key final CharSequence destination,
                                      @RedisProtocolSupport.Key final CharSequence key1,
                                      @RedisProtocolSupport.Key final CharSequence key2) {
        return blockingToSingle(() -> reservedCnx.sunionstore(destination, key1, key2));
    }

    @Override
    public Single<String> sunionstore(@RedisProtocolSupport.Key final CharSequence destination,
                                      @RedisProtocolSupport.Key final CharSequence key1,
                                      @RedisProtocolSupport.Key final CharSequence key2,
                                      @RedisProtocolSupport.Key final CharSequence key3) {
        return blockingToSingle(() -> reservedCnx.sunionstore(destination, key1, key2, key3));
    }

    @Override
    public Single<String> sunionstore(@RedisProtocolSupport.Key final CharSequence destination,
                                      @RedisProtocolSupport.Key final Collection<? extends CharSequence> keys) {
        return blockingToSingle(() -> reservedCnx.sunionstore(destination, keys));
    }

    @Override
    public Single<String> swapdb(final long index, final long index1) {
        return blockingToSingle(() -> reservedCnx.swapdb(index, index1));
    }

    @Override
    public Single<String> time() {
        return blockingToSingle(() -> reservedCnx.time());
    }

    @Override
    public Single<String> touch(@RedisProtocolSupport.Key final CharSequence key) {
        return blockingToSingle(() -> reservedCnx.touch(key));
    }

    @Override
    public Single<String> touch(@RedisProtocolSupport.Key final CharSequence key1,
                                @RedisProtocolSupport.Key final CharSequence key2) {
        return blockingToSingle(() -> reservedCnx.touch(key1, key2));
    }

    @Override
    public Single<String> touch(@RedisProtocolSupport.Key final CharSequence key1,
                                @RedisProtocolSupport.Key final CharSequence key2,
                                @RedisProtocolSupport.Key final CharSequence key3) {
        return blockingToSingle(() -> reservedCnx.touch(key1, key2, key3));
    }

    @Override
    public Single<String> touch(@RedisProtocolSupport.Key final Collection<? extends CharSequence> keys) {
        return blockingToSingle(() -> reservedCnx.touch(keys));
    }

    @Override
    public Single<String> ttl(@RedisProtocolSupport.Key final CharSequence key) {
        return blockingToSingle(() -> reservedCnx.ttl(key));
    }

    @Override
    public Single<String> type(@RedisProtocolSupport.Key final CharSequence key) {
        return blockingToSingle(() -> reservedCnx.type(key));
    }

    @Override
    public Single<String> unlink(@RedisProtocolSupport.Key final CharSequence key) {
        return blockingToSingle(() -> reservedCnx.unlink(key));
    }

    @Override
    public Single<String> unlink(@RedisProtocolSupport.Key final CharSequence key1,
                                 @RedisProtocolSupport.Key final CharSequence key2) {
        return blockingToSingle(() -> reservedCnx.unlink(key1, key2));
    }

    @Override
    public Single<String> unlink(@RedisProtocolSupport.Key final CharSequence key1,
                                 @RedisProtocolSupport.Key final CharSequence key2,
                                 @RedisProtocolSupport.Key final CharSequence key3) {
        return blockingToSingle(() -> reservedCnx.unlink(key1, key2, key3));
    }

    @Override
    public Single<String> unlink(@RedisProtocolSupport.Key final Collection<? extends CharSequence> keys) {
        return blockingToSingle(() -> reservedCnx.unlink(keys));
    }

    @Override
    public Single<String> unwatch() {
        return blockingToSingle(() -> reservedCnx.unwatch());
    }

    @Override
    public Single<String> wait(final long numslaves, final long timeout) {
        return blockingToSingle(() -> reservedCnx.wait(numslaves, timeout));
    }

    @Override
    public Single<String> watch(@RedisProtocolSupport.Key final CharSequence key) {
        return blockingToSingle(() -> reservedCnx.watch(key));
    }

    @Override
    public Single<String> watch(@RedisProtocolSupport.Key final CharSequence key1,
                                @RedisProtocolSupport.Key final CharSequence key2) {
        return blockingToSingle(() -> reservedCnx.watch(key1, key2));
    }

    @Override
    public Single<String> watch(@RedisProtocolSupport.Key final CharSequence key1,
                                @RedisProtocolSupport.Key final CharSequence key2,
                                @RedisProtocolSupport.Key final CharSequence key3) {
        return blockingToSingle(() -> reservedCnx.watch(key1, key2, key3));
    }

    @Override
    public Single<String> watch(@RedisProtocolSupport.Key final Collection<? extends CharSequence> keys) {
        return blockingToSingle(() -> reservedCnx.watch(keys));
    }

    @Override
    public Single<String> xadd(@RedisProtocolSupport.Key final CharSequence key, final CharSequence id,
                               final CharSequence field, final CharSequence value) {
        return blockingToSingle(() -> reservedCnx.xadd(key, id, field, value));
    }

    @Override
    public Single<String> xadd(@RedisProtocolSupport.Key final CharSequence key, final CharSequence id,
                               final CharSequence field1, final CharSequence value1, final CharSequence field2,
                               final CharSequence value2) {
        return blockingToSingle(() -> reservedCnx.xadd(key, id, field1, value1, field2, value2));
    }

    @Override
    public Single<String> xadd(@RedisProtocolSupport.Key final CharSequence key, final CharSequence id,
                               final CharSequence field1, final CharSequence value1, final CharSequence field2,
                               final CharSequence value2, final CharSequence field3, final CharSequence value3) {
        return blockingToSingle(() -> reservedCnx.xadd(key, id, field1, value1, field2, value2, field3, value3));
    }

    @Override
    public Single<String> xadd(@RedisProtocolSupport.Key final CharSequence key, final CharSequence id,
                               final Collection<RedisProtocolSupport.FieldValue> fieldValues) {
        return blockingToSingle(() -> reservedCnx.xadd(key, id, fieldValues));
    }

    @Override
    public Single<String> xlen(@RedisProtocolSupport.Key final CharSequence key) {
        return blockingToSingle(() -> reservedCnx.xlen(key));
    }

    @Override
    public Single<String> xpending(@RedisProtocolSupport.Key final CharSequence key, final CharSequence group) {
        return blockingToSingle(() -> reservedCnx.xpending(key, group));
    }

    @Override
    public Single<String> xpending(@RedisProtocolSupport.Key final CharSequence key, final CharSequence group,
                                   @Nullable final CharSequence start, @Nullable final CharSequence end,
                                   @Nullable final Long count, @Nullable final CharSequence consumer) {
        return blockingToSingle(() -> reservedCnx.xpending(key, group, start, end, count, consumer));
    }

    @Override
    public Single<String> xrange(@RedisProtocolSupport.Key final CharSequence key, final CharSequence start,
                                 final CharSequence end) {
        return blockingToSingle(() -> reservedCnx.xrange(key, start, end));
    }

    @Override
    public Single<String> xrange(@RedisProtocolSupport.Key final CharSequence key, final CharSequence start,
                                 final CharSequence end, @Nullable final Long count) {
        return blockingToSingle(() -> reservedCnx.xrange(key, start, end, count));
    }

    @Override
    public Single<String> xread(@RedisProtocolSupport.Key final Collection<? extends CharSequence> keys,
                                final Collection<? extends CharSequence> ids) {
        return blockingToSingle(() -> reservedCnx.xread(keys, ids));
    }

    @Override
    public Single<String> xread(@Nullable final Long count, @Nullable final Long blockMilliseconds,
                                @RedisProtocolSupport.Key final Collection<? extends CharSequence> keys,
                                final Collection<? extends CharSequence> ids) {
        return blockingToSingle(() -> reservedCnx.xread(count, blockMilliseconds, keys, ids));
    }

    @Override
    public Single<String> xreadgroup(final RedisProtocolSupport.GroupConsumer groupConsumer,
                                     @RedisProtocolSupport.Key final Collection<? extends CharSequence> keys,
                                     final Collection<? extends CharSequence> ids) {
        return blockingToSingle(() -> reservedCnx.xreadgroup(groupConsumer, keys, ids));
    }

    @Override
    public Single<String> xreadgroup(final RedisProtocolSupport.GroupConsumer groupConsumer, @Nullable final Long count,
                                     @Nullable final Long blockMilliseconds,
                                     @RedisProtocolSupport.Key final Collection<? extends CharSequence> keys,
                                     final Collection<? extends CharSequence> ids) {
        return blockingToSingle(() -> reservedCnx.xreadgroup(groupConsumer, count, blockMilliseconds, keys, ids));
    }

    @Override
    public Single<String> xrevrange(@RedisProtocolSupport.Key final CharSequence key, final CharSequence end,
                                    final CharSequence start) {
        return blockingToSingle(() -> reservedCnx.xrevrange(key, end, start));
    }

    @Override
    public Single<String> xrevrange(@RedisProtocolSupport.Key final CharSequence key, final CharSequence end,
                                    final CharSequence start, @Nullable final Long count) {
        return blockingToSingle(() -> reservedCnx.xrevrange(key, end, start, count));
    }

    @Override
    public Single<String> zadd(@RedisProtocolSupport.Key final CharSequence key,
                               final Collection<RedisProtocolSupport.ScoreMember> scoreMembers) {
        return blockingToSingle(() -> reservedCnx.zadd(key, scoreMembers));
    }

    @Override
    public Single<String> zadd(@RedisProtocolSupport.Key final CharSequence key,
                               @Nullable final RedisProtocolSupport.ZaddCondition condition,
                               @Nullable final RedisProtocolSupport.ZaddChange change, final double score,
                               final CharSequence member) {
        return blockingToSingle(() -> reservedCnx.zadd(key, condition, change, score, member));
    }

    @Override
    public Single<String> zadd(@RedisProtocolSupport.Key final CharSequence key,
                               @Nullable final RedisProtocolSupport.ZaddCondition condition,
                               @Nullable final RedisProtocolSupport.ZaddChange change, final double score1,
                               final CharSequence member1, final double score2, final CharSequence member2) {
        return blockingToSingle(() -> reservedCnx.zadd(key, condition, change, score1, member1, score2, member2));
    }

    @Override
    public Single<String> zadd(@RedisProtocolSupport.Key final CharSequence key,
                               @Nullable final RedisProtocolSupport.ZaddCondition condition,
                               @Nullable final RedisProtocolSupport.ZaddChange change, final double score1,
                               final CharSequence member1, final double score2, final CharSequence member2,
                               final double score3, final CharSequence member3) {
        return blockingToSingle(
                    () -> reservedCnx.zadd(key, condition, change, score1, member1, score2, member2, score3, member3));
    }

    @Override
    public Single<String> zadd(@RedisProtocolSupport.Key final CharSequence key,
                               @Nullable final RedisProtocolSupport.ZaddCondition condition,
                               @Nullable final RedisProtocolSupport.ZaddChange change,
                               final Collection<RedisProtocolSupport.ScoreMember> scoreMembers) {
        return blockingToSingle(() -> reservedCnx.zadd(key, condition, change, scoreMembers));
    }

    @Override
    public Single<String> zaddIncr(@RedisProtocolSupport.Key final CharSequence key,
                                   final Collection<RedisProtocolSupport.ScoreMember> scoreMembers) {
        return blockingToSingle(() -> reservedCnx.zaddIncr(key, scoreMembers));
    }

    @Override
    public Single<String> zaddIncr(@RedisProtocolSupport.Key final CharSequence key,
                                   @Nullable final RedisProtocolSupport.ZaddCondition condition,
                                   @Nullable final RedisProtocolSupport.ZaddChange change, final double score,
                                   final CharSequence member) {
        return blockingToSingle(() -> reservedCnx.zaddIncr(key, condition, change, score, member));
    }

    @Override
    public Single<String> zaddIncr(@RedisProtocolSupport.Key final CharSequence key,
                                   @Nullable final RedisProtocolSupport.ZaddCondition condition,
                                   @Nullable final RedisProtocolSupport.ZaddChange change, final double score1,
                                   final CharSequence member1, final double score2, final CharSequence member2) {
        return blockingToSingle(() -> reservedCnx.zaddIncr(key, condition, change, score1, member1, score2, member2));
    }

    @Override
    public Single<String> zaddIncr(@RedisProtocolSupport.Key final CharSequence key,
                                   @Nullable final RedisProtocolSupport.ZaddCondition condition,
                                   @Nullable final RedisProtocolSupport.ZaddChange change, final double score1,
                                   final CharSequence member1, final double score2, final CharSequence member2,
                                   final double score3, final CharSequence member3) {
        return blockingToSingle(() -> reservedCnx.zaddIncr(key, condition, change, score1, member1, score2, member2,
                    score3, member3));
    }

    @Override
    public Single<String> zaddIncr(@RedisProtocolSupport.Key final CharSequence key,
                                   @Nullable final RedisProtocolSupport.ZaddCondition condition,
                                   @Nullable final RedisProtocolSupport.ZaddChange change,
                                   final Collection<RedisProtocolSupport.ScoreMember> scoreMembers) {
        return blockingToSingle(() -> reservedCnx.zaddIncr(key, condition, change, scoreMembers));
    }

    @Override
    public Single<String> zcard(@RedisProtocolSupport.Key final CharSequence key) {
        return blockingToSingle(() -> reservedCnx.zcard(key));
    }

    @Override
    public Single<String> zcount(@RedisProtocolSupport.Key final CharSequence key, final double min, final double max) {
        return blockingToSingle(() -> reservedCnx.zcount(key, min, max));
    }

    @Override
    public Single<String> zincrby(@RedisProtocolSupport.Key final CharSequence key, final long increment,
                                  final CharSequence member) {
        return blockingToSingle(() -> reservedCnx.zincrby(key, increment, member));
    }

    @Override
    public Single<String> zinterstore(@RedisProtocolSupport.Key final CharSequence destination, final long numkeys,
                                      @RedisProtocolSupport.Key final Collection<? extends CharSequence> keys) {
        return blockingToSingle(() -> reservedCnx.zinterstore(destination, numkeys, keys));
    }

    @Override
    public Single<String> zinterstore(@RedisProtocolSupport.Key final CharSequence destination, final long numkeys,
                                      @RedisProtocolSupport.Key final Collection<? extends CharSequence> keys,
                                      final Collection<Long> weightses,
                                      @Nullable final RedisProtocolSupport.ZinterstoreAggregate aggregate) {
        return blockingToSingle(() -> reservedCnx.zinterstore(destination, numkeys, keys, weightses, aggregate));
    }

    @Override
    public Single<String> zlexcount(@RedisProtocolSupport.Key final CharSequence key, final CharSequence min,
                                    final CharSequence max) {
        return blockingToSingle(() -> reservedCnx.zlexcount(key, min, max));
    }

    @Override
    public Single<String> zpopmax(@RedisProtocolSupport.Key final CharSequence key) {
        return blockingToSingle(() -> reservedCnx.zpopmax(key));
    }

    @Override
    public Single<String> zpopmax(@RedisProtocolSupport.Key final CharSequence key, @Nullable final Long count) {
        return blockingToSingle(() -> reservedCnx.zpopmax(key, count));
    }

    @Override
    public Single<String> zpopmin(@RedisProtocolSupport.Key final CharSequence key) {
        return blockingToSingle(() -> reservedCnx.zpopmin(key));
    }

    @Override
    public Single<String> zpopmin(@RedisProtocolSupport.Key final CharSequence key, @Nullable final Long count) {
        return blockingToSingle(() -> reservedCnx.zpopmin(key, count));
    }

    @Override
    public Single<String> zrange(@RedisProtocolSupport.Key final CharSequence key, final long start, final long stop) {
        return blockingToSingle(() -> reservedCnx.zrange(key, start, stop));
    }

    @Override
    public Single<String> zrange(@RedisProtocolSupport.Key final CharSequence key, final long start, final long stop,
                                 @Nullable final RedisProtocolSupport.ZrangeWithscores withscores) {
        return blockingToSingle(() -> reservedCnx.zrange(key, start, stop, withscores));
    }

    @Override
    public Single<String> zrangebylex(@RedisProtocolSupport.Key final CharSequence key, final CharSequence min,
                                      final CharSequence max) {
        return blockingToSingle(() -> reservedCnx.zrangebylex(key, min, max));
    }

    @Override
    public Single<String> zrangebylex(@RedisProtocolSupport.Key final CharSequence key, final CharSequence min,
                                      final CharSequence max,
                                      @Nullable final RedisProtocolSupport.OffsetCount offsetCount) {
        return blockingToSingle(() -> reservedCnx.zrangebylex(key, min, max, offsetCount));
    }

    @Override
    public Single<String> zrangebyscore(@RedisProtocolSupport.Key final CharSequence key, final double min,
                                        final double max) {
        return blockingToSingle(() -> reservedCnx.zrangebyscore(key, min, max));
    }

    @Override
    public Single<String> zrangebyscore(@RedisProtocolSupport.Key final CharSequence key, final double min,
                                        final double max,
                                        @Nullable final RedisProtocolSupport.ZrangebyscoreWithscores withscores,
                                        @Nullable final RedisProtocolSupport.OffsetCount offsetCount) {
        return blockingToSingle(() -> reservedCnx.zrangebyscore(key, min, max, withscores, offsetCount));
    }

    @Override
    public Single<String> zrank(@RedisProtocolSupport.Key final CharSequence key, final CharSequence member) {
        return blockingToSingle(() -> reservedCnx.zrank(key, member));
    }

    @Override
    public Single<String> zrem(@RedisProtocolSupport.Key final CharSequence key, final CharSequence member) {
        return blockingToSingle(() -> reservedCnx.zrem(key, member));
    }

    @Override
    public Single<String> zrem(@RedisProtocolSupport.Key final CharSequence key, final CharSequence member1,
                               final CharSequence member2) {
        return blockingToSingle(() -> reservedCnx.zrem(key, member1, member2));
    }

    @Override
    public Single<String> zrem(@RedisProtocolSupport.Key final CharSequence key, final CharSequence member1,
                               final CharSequence member2, final CharSequence member3) {
        return blockingToSingle(() -> reservedCnx.zrem(key, member1, member2, member3));
    }

    @Override
    public Single<String> zrem(@RedisProtocolSupport.Key final CharSequence key,
                               final Collection<? extends CharSequence> members) {
        return blockingToSingle(() -> reservedCnx.zrem(key, members));
    }

    @Override
    public Single<String> zremrangebylex(@RedisProtocolSupport.Key final CharSequence key, final CharSequence min,
                                         final CharSequence max) {
        return blockingToSingle(() -> reservedCnx.zremrangebylex(key, min, max));
    }

    @Override
    public Single<String> zremrangebyrank(@RedisProtocolSupport.Key final CharSequence key, final long start,
                                          final long stop) {
        return blockingToSingle(() -> reservedCnx.zremrangebyrank(key, start, stop));
    }

    @Override
    public Single<String> zremrangebyscore(@RedisProtocolSupport.Key final CharSequence key, final double min,
                                           final double max) {
        return blockingToSingle(() -> reservedCnx.zremrangebyscore(key, min, max));
    }

    @Override
    public Single<String> zrevrange(@RedisProtocolSupport.Key final CharSequence key, final long start,
                                    final long stop) {
        return blockingToSingle(() -> reservedCnx.zrevrange(key, start, stop));
    }

    @Override
    public Single<String> zrevrange(@RedisProtocolSupport.Key final CharSequence key, final long start, final long stop,
                                    @Nullable final RedisProtocolSupport.ZrevrangeWithscores withscores) {
        return blockingToSingle(() -> reservedCnx.zrevrange(key, start, stop, withscores));
    }

    @Override
    public Single<String> zrevrangebylex(@RedisProtocolSupport.Key final CharSequence key, final CharSequence max,
                                         final CharSequence min) {
        return blockingToSingle(() -> reservedCnx.zrevrangebylex(key, max, min));
    }

    @Override
    public Single<String> zrevrangebylex(@RedisProtocolSupport.Key final CharSequence key, final CharSequence max,
                                         final CharSequence min,
                                         @Nullable final RedisProtocolSupport.OffsetCount offsetCount) {
        return blockingToSingle(() -> reservedCnx.zrevrangebylex(key, max, min, offsetCount));
    }

    @Override
    public Single<String> zrevrangebyscore(@RedisProtocolSupport.Key final CharSequence key, final double max,
                                           final double min) {
        return blockingToSingle(() -> reservedCnx.zrevrangebyscore(key, max, min));
    }

    @Override
    public Single<String> zrevrangebyscore(@RedisProtocolSupport.Key final CharSequence key, final double max,
                                           final double min,
                                           @Nullable final RedisProtocolSupport.ZrevrangebyscoreWithscores withscores,
                                           @Nullable final RedisProtocolSupport.OffsetCount offsetCount) {
        return blockingToSingle(() -> reservedCnx.zrevrangebyscore(key, max, min, withscores, offsetCount));
    }

    @Override
    public Single<String> zrevrank(@RedisProtocolSupport.Key final CharSequence key, final CharSequence member) {
        return blockingToSingle(() -> reservedCnx.zrevrank(key, member));
    }

    @Override
    public Single<String> zscan(@RedisProtocolSupport.Key final CharSequence key, final long cursor) {
        return blockingToSingle(() -> reservedCnx.zscan(key, cursor));
    }

    @Override
    public Single<String> zscan(@RedisProtocolSupport.Key final CharSequence key, final long cursor,
                                @Nullable final CharSequence matchPattern, @Nullable final Long count) {
        return blockingToSingle(() -> reservedCnx.zscan(key, cursor, matchPattern, count));
    }

    @Override
    public Single<String> zscore(@RedisProtocolSupport.Key final CharSequence key, final CharSequence member) {
        return blockingToSingle(() -> reservedCnx.zscore(key, member));
    }

    @Override
    public Single<String> zunionstore(@RedisProtocolSupport.Key final CharSequence destination, final long numkeys,
                                      @RedisProtocolSupport.Key final Collection<? extends CharSequence> keys) {
        return blockingToSingle(() -> reservedCnx.zunionstore(destination, numkeys, keys));
    }

    @Override
    public Single<String> zunionstore(@RedisProtocolSupport.Key final CharSequence destination, final long numkeys,
                                      @RedisProtocolSupport.Key final Collection<? extends CharSequence> keys,
                                      final Collection<Long> weightses,
                                      @Nullable final RedisProtocolSupport.ZunionstoreAggregate aggregate) {
        return blockingToSingle(() -> reservedCnx.zunionstore(destination, numkeys, keys, weightses, aggregate));
    }
}

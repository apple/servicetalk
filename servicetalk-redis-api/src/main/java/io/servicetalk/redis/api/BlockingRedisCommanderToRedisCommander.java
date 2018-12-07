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
import io.servicetalk.concurrent.api.Publisher;
import io.servicetalk.concurrent.api.Single;

import java.util.Collection;
import java.util.List;
import java.util.Objects;
import javax.annotation.Generated;
import javax.annotation.Nullable;

import static io.servicetalk.redis.api.BlockingUtils.blockingToCompletable;
import static io.servicetalk.redis.api.BlockingUtils.blockingToPublisher;
import static io.servicetalk.redis.api.BlockingUtils.blockingToSingle;

@Generated({})
@SuppressWarnings("unchecked")
final class BlockingRedisCommanderToRedisCommander extends RedisCommander {

    private final BlockingRedisCommander blockingCommander;

    BlockingRedisCommanderToRedisCommander(final BlockingRedisCommander blockingCommander) {
        this.blockingCommander = Objects.requireNonNull(blockingCommander);
    }

    @Override
    public Completable closeAsync() {
        return blockingToCompletable(blockingCommander::close);
    }

    @Override
    public Completable closeAsyncGracefully() {
        return closeAsync();
    }

    @Override
    public Single<Long> append(@RedisProtocolSupport.Key final CharSequence key, final CharSequence value) {
        return blockingToSingle(() -> blockingCommander.append(key, value));
    }

    @Override
    public Single<String> auth(final CharSequence password) {
        return blockingToSingle(() -> blockingCommander.auth(password));
    }

    @Override
    public Single<String> bgrewriteaof() {
        return blockingToSingle(() -> blockingCommander.bgrewriteaof());
    }

    @Override
    public Single<String> bgsave() {
        return blockingToSingle(() -> blockingCommander.bgsave());
    }

    @Override
    public Single<Long> bitcount(@RedisProtocolSupport.Key final CharSequence key) {
        return blockingToSingle(() -> blockingCommander.bitcount(key));
    }

    @Override
    public Single<Long> bitcount(@RedisProtocolSupport.Key final CharSequence key, @Nullable final Long start,
                                 @Nullable final Long end) {
        return blockingToSingle(() -> blockingCommander.bitcount(key, start, end));
    }

    @Override
    public Single<List<Long>> bitfield(@RedisProtocolSupport.Key final CharSequence key,
                                       final Collection<RedisProtocolSupport.BitfieldOperation> operations) {
        return blockingToSingle(() -> blockingCommander.bitfield(key, operations));
    }

    @Override
    public Single<Long> bitop(final CharSequence operation, @RedisProtocolSupport.Key final CharSequence destkey,
                              @RedisProtocolSupport.Key final CharSequence key) {
        return blockingToSingle(() -> blockingCommander.bitop(operation, destkey, key));
    }

    @Override
    public Single<Long> bitop(final CharSequence operation, @RedisProtocolSupport.Key final CharSequence destkey,
                              @RedisProtocolSupport.Key final CharSequence key1,
                              @RedisProtocolSupport.Key final CharSequence key2) {
        return blockingToSingle(() -> blockingCommander.bitop(operation, destkey, key1, key2));
    }

    @Override
    public Single<Long> bitop(final CharSequence operation, @RedisProtocolSupport.Key final CharSequence destkey,
                              @RedisProtocolSupport.Key final CharSequence key1,
                              @RedisProtocolSupport.Key final CharSequence key2,
                              @RedisProtocolSupport.Key final CharSequence key3) {
        return blockingToSingle(() -> blockingCommander.bitop(operation, destkey, key1, key2, key3));
    }

    @Override
    public Single<Long> bitop(final CharSequence operation, @RedisProtocolSupport.Key final CharSequence destkey,
                              @RedisProtocolSupport.Key final Collection<? extends CharSequence> keys) {
        return blockingToSingle(() -> blockingCommander.bitop(operation, destkey, keys));
    }

    @Override
    public Single<Long> bitpos(@RedisProtocolSupport.Key final CharSequence key, final long bit) {
        return blockingToSingle(() -> blockingCommander.bitpos(key, bit));
    }

    @Override
    public Single<Long> bitpos(@RedisProtocolSupport.Key final CharSequence key, final long bit,
                               @Nullable final Long start, @Nullable final Long end) {
        return blockingToSingle(() -> blockingCommander.bitpos(key, bit, start, end));
    }

    @Override
    public <T> Single<List<T>> blpop(@RedisProtocolSupport.Key final Collection<? extends CharSequence> keys,
                                     final long timeout) {
        return blockingToSingle(() -> blockingCommander.blpop(keys, timeout));
    }

    @Override
    public <T> Single<List<T>> brpop(@RedisProtocolSupport.Key final Collection<? extends CharSequence> keys,
                                     final long timeout) {
        return blockingToSingle(() -> blockingCommander.brpop(keys, timeout));
    }

    @Override
    public Single<String> brpoplpush(@RedisProtocolSupport.Key final CharSequence source,
                                     @RedisProtocolSupport.Key final CharSequence destination, final long timeout) {
        return blockingToSingle(() -> blockingCommander.brpoplpush(source, destination, timeout));
    }

    @Override
    public <T> Single<List<T>> bzpopmax(@RedisProtocolSupport.Key final Collection<? extends CharSequence> keys,
                                        final long timeout) {
        return blockingToSingle(() -> blockingCommander.bzpopmax(keys, timeout));
    }

    @Override
    public <T> Single<List<T>> bzpopmin(@RedisProtocolSupport.Key final Collection<? extends CharSequence> keys,
                                        final long timeout) {
        return blockingToSingle(() -> blockingCommander.bzpopmin(keys, timeout));
    }

    @Override
    public Single<Long> clientKill(@Nullable final Long id, @Nullable final RedisProtocolSupport.ClientKillType type,
                                   @Nullable final CharSequence addrIpPort, @Nullable final CharSequence skipmeYesNo) {
        return blockingToSingle(() -> blockingCommander.clientKill(id, type, addrIpPort, skipmeYesNo));
    }

    @Override
    public Single<String> clientList() {
        return blockingToSingle(() -> blockingCommander.clientList());
    }

    @Override
    public Single<String> clientGetname() {
        return blockingToSingle(() -> blockingCommander.clientGetname());
    }

    @Override
    public Single<String> clientPause(final long timeout) {
        return blockingToSingle(() -> blockingCommander.clientPause(timeout));
    }

    @Override
    public Single<String> clientReply(final RedisProtocolSupport.ClientReplyReplyMode replyMode) {
        return blockingToSingle(() -> blockingCommander.clientReply(replyMode));
    }

    @Override
    public Single<String> clientSetname(final CharSequence connectionName) {
        return blockingToSingle(() -> blockingCommander.clientSetname(connectionName));
    }

    @Override
    public Single<String> clusterAddslots(final long slot) {
        return blockingToSingle(() -> blockingCommander.clusterAddslots(slot));
    }

    @Override
    public Single<String> clusterAddslots(final long slot1, final long slot2) {
        return blockingToSingle(() -> blockingCommander.clusterAddslots(slot1, slot2));
    }

    @Override
    public Single<String> clusterAddslots(final long slot1, final long slot2, final long slot3) {
        return blockingToSingle(() -> blockingCommander.clusterAddslots(slot1, slot2, slot3));
    }

    @Override
    public Single<String> clusterAddslots(final Collection<Long> slots) {
        return blockingToSingle(() -> blockingCommander.clusterAddslots(slots));
    }

    @Override
    public Single<Long> clusterCountFailureReports(final CharSequence nodeId) {
        return blockingToSingle(() -> blockingCommander.clusterCountFailureReports(nodeId));
    }

    @Override
    public Single<Long> clusterCountkeysinslot(final long slot) {
        return blockingToSingle(() -> blockingCommander.clusterCountkeysinslot(slot));
    }

    @Override
    public Single<String> clusterDelslots(final long slot) {
        return blockingToSingle(() -> blockingCommander.clusterDelslots(slot));
    }

    @Override
    public Single<String> clusterDelslots(final long slot1, final long slot2) {
        return blockingToSingle(() -> blockingCommander.clusterDelslots(slot1, slot2));
    }

    @Override
    public Single<String> clusterDelslots(final long slot1, final long slot2, final long slot3) {
        return blockingToSingle(() -> blockingCommander.clusterDelslots(slot1, slot2, slot3));
    }

    @Override
    public Single<String> clusterDelslots(final Collection<Long> slots) {
        return blockingToSingle(() -> blockingCommander.clusterDelslots(slots));
    }

    @Override
    public Single<String> clusterFailover() {
        return blockingToSingle(() -> blockingCommander.clusterFailover());
    }

    @Override
    public Single<String> clusterFailover(@Nullable final RedisProtocolSupport.ClusterFailoverOptions options) {
        return blockingToSingle(() -> blockingCommander.clusterFailover(options));
    }

    @Override
    public Single<String> clusterForget(final CharSequence nodeId) {
        return blockingToSingle(() -> blockingCommander.clusterForget(nodeId));
    }

    @Override
    public <T> Single<List<T>> clusterGetkeysinslot(final long slot, final long count) {
        return blockingToSingle(() -> blockingCommander.clusterGetkeysinslot(slot, count));
    }

    @Override
    public Single<String> clusterInfo() {
        return blockingToSingle(() -> blockingCommander.clusterInfo());
    }

    @Override
    public Single<Long> clusterKeyslot(final CharSequence key) {
        return blockingToSingle(() -> blockingCommander.clusterKeyslot(key));
    }

    @Override
    public Single<String> clusterMeet(final CharSequence ip, final long port) {
        return blockingToSingle(() -> blockingCommander.clusterMeet(ip, port));
    }

    @Override
    public Single<String> clusterNodes() {
        return blockingToSingle(() -> blockingCommander.clusterNodes());
    }

    @Override
    public Single<String> clusterReplicate(final CharSequence nodeId) {
        return blockingToSingle(() -> blockingCommander.clusterReplicate(nodeId));
    }

    @Override
    public Single<String> clusterReset() {
        return blockingToSingle(() -> blockingCommander.clusterReset());
    }

    @Override
    public Single<String> clusterReset(@Nullable final RedisProtocolSupport.ClusterResetResetType resetType) {
        return blockingToSingle(() -> blockingCommander.clusterReset(resetType));
    }

    @Override
    public Single<String> clusterSaveconfig() {
        return blockingToSingle(() -> blockingCommander.clusterSaveconfig());
    }

    @Override
    public Single<String> clusterSetConfigEpoch(final long configEpoch) {
        return blockingToSingle(() -> blockingCommander.clusterSetConfigEpoch(configEpoch));
    }

    @Override
    public Single<String> clusterSetslot(final long slot,
                                         final RedisProtocolSupport.ClusterSetslotSubcommand subcommand) {
        return blockingToSingle(() -> blockingCommander.clusterSetslot(slot, subcommand));
    }

    @Override
    public Single<String> clusterSetslot(final long slot,
                                         final RedisProtocolSupport.ClusterSetslotSubcommand subcommand,
                                         @Nullable final CharSequence nodeId) {
        return blockingToSingle(() -> blockingCommander.clusterSetslot(slot, subcommand, nodeId));
    }

    @Override
    public Single<String> clusterSlaves(final CharSequence nodeId) {
        return blockingToSingle(() -> blockingCommander.clusterSlaves(nodeId));
    }

    @Override
    public <T> Single<List<T>> clusterSlots() {
        return blockingToSingle(() -> blockingCommander.clusterSlots());
    }

    @Override
    public <T> Single<List<T>> command() {
        return blockingToSingle(() -> blockingCommander.command());
    }

    @Override
    public Single<Long> commandCount() {
        return blockingToSingle(() -> blockingCommander.commandCount());
    }

    @Override
    public <T> Single<List<T>> commandGetkeys() {
        return blockingToSingle(() -> blockingCommander.commandGetkeys());
    }

    @Override
    public <T> Single<List<T>> commandInfo(final CharSequence commandName) {
        return blockingToSingle(() -> blockingCommander.commandInfo(commandName));
    }

    @Override
    public <T> Single<List<T>> commandInfo(final CharSequence commandName1, final CharSequence commandName2) {
        return blockingToSingle(() -> blockingCommander.commandInfo(commandName1, commandName2));
    }

    @Override
    public <T> Single<List<T>> commandInfo(final CharSequence commandName1, final CharSequence commandName2,
                                           final CharSequence commandName3) {
        return blockingToSingle(() -> blockingCommander.commandInfo(commandName1, commandName2, commandName3));
    }

    @Override
    public <T> Single<List<T>> commandInfo(final Collection<? extends CharSequence> commandNames) {
        return blockingToSingle(() -> blockingCommander.commandInfo(commandNames));
    }

    @Override
    public <T> Single<List<T>> configGet(final CharSequence parameter) {
        return blockingToSingle(() -> blockingCommander.configGet(parameter));
    }

    @Override
    public Single<String> configRewrite() {
        return blockingToSingle(() -> blockingCommander.configRewrite());
    }

    @Override
    public Single<String> configSet(final CharSequence parameter, final CharSequence value) {
        return blockingToSingle(() -> blockingCommander.configSet(parameter, value));
    }

    @Override
    public Single<String> configResetstat() {
        return blockingToSingle(() -> blockingCommander.configResetstat());
    }

    @Override
    public Single<Long> dbsize() {
        return blockingToSingle(() -> blockingCommander.dbsize());
    }

    @Override
    public Single<String> debugObject(@RedisProtocolSupport.Key final CharSequence key) {
        return blockingToSingle(() -> blockingCommander.debugObject(key));
    }

    @Override
    public Single<String> debugSegfault() {
        return blockingToSingle(() -> blockingCommander.debugSegfault());
    }

    @Override
    public Single<Long> decr(@RedisProtocolSupport.Key final CharSequence key) {
        return blockingToSingle(() -> blockingCommander.decr(key));
    }

    @Override
    public Single<Long> decrby(@RedisProtocolSupport.Key final CharSequence key, final long decrement) {
        return blockingToSingle(() -> blockingCommander.decrby(key, decrement));
    }

    @Override
    public Single<Long> del(@RedisProtocolSupport.Key final CharSequence key) {
        return blockingToSingle(() -> blockingCommander.del(key));
    }

    @Override
    public Single<Long> del(@RedisProtocolSupport.Key final CharSequence key1,
                            @RedisProtocolSupport.Key final CharSequence key2) {
        return blockingToSingle(() -> blockingCommander.del(key1, key2));
    }

    @Override
    public Single<Long> del(@RedisProtocolSupport.Key final CharSequence key1,
                            @RedisProtocolSupport.Key final CharSequence key2,
                            @RedisProtocolSupport.Key final CharSequence key3) {
        return blockingToSingle(() -> blockingCommander.del(key1, key2, key3));
    }

    @Override
    public Single<Long> del(@RedisProtocolSupport.Key final Collection<? extends CharSequence> keys) {
        return blockingToSingle(() -> blockingCommander.del(keys));
    }

    @Override
    public Single<String> dump(@RedisProtocolSupport.Key final CharSequence key) {
        return blockingToSingle(() -> blockingCommander.dump(key));
    }

    @Override
    public Single<String> echo(final CharSequence message) {
        return blockingToSingle(() -> blockingCommander.echo(message));
    }

    @Override
    public Single<String> eval(final CharSequence script, final long numkeys,
                               @RedisProtocolSupport.Key final Collection<? extends CharSequence> keys,
                               final Collection<? extends CharSequence> args) {
        return blockingToSingle(() -> blockingCommander.eval(script, numkeys, keys, args));
    }

    @Override
    public <T> Single<List<T>> evalList(final CharSequence script, final long numkeys,
                                        @RedisProtocolSupport.Key final Collection<? extends CharSequence> keys,
                                        final Collection<? extends CharSequence> args) {
        return blockingToSingle(() -> blockingCommander.evalList(script, numkeys, keys, args));
    }

    @Override
    public Single<Long> evalLong(final CharSequence script, final long numkeys,
                                 @RedisProtocolSupport.Key final Collection<? extends CharSequence> keys,
                                 final Collection<? extends CharSequence> args) {
        return blockingToSingle(() -> blockingCommander.evalLong(script, numkeys, keys, args));
    }

    @Override
    public Single<String> evalsha(final CharSequence sha1, final long numkeys,
                                  @RedisProtocolSupport.Key final Collection<? extends CharSequence> keys,
                                  final Collection<? extends CharSequence> args) {
        return blockingToSingle(() -> blockingCommander.evalsha(sha1, numkeys, keys, args));
    }

    @Override
    public <T> Single<List<T>> evalshaList(final CharSequence sha1, final long numkeys,
                                           @RedisProtocolSupport.Key final Collection<? extends CharSequence> keys,
                                           final Collection<? extends CharSequence> args) {
        return blockingToSingle(() -> blockingCommander.evalshaList(sha1, numkeys, keys, args));
    }

    @Override
    public Single<Long> evalshaLong(final CharSequence sha1, final long numkeys,
                                    @RedisProtocolSupport.Key final Collection<? extends CharSequence> keys,
                                    final Collection<? extends CharSequence> args) {
        return blockingToSingle(() -> blockingCommander.evalshaLong(sha1, numkeys, keys, args));
    }

    @Override
    public Single<Long> exists(@RedisProtocolSupport.Key final CharSequence key) {
        return blockingToSingle(() -> blockingCommander.exists(key));
    }

    @Override
    public Single<Long> exists(@RedisProtocolSupport.Key final CharSequence key1,
                               @RedisProtocolSupport.Key final CharSequence key2) {
        return blockingToSingle(() -> blockingCommander.exists(key1, key2));
    }

    @Override
    public Single<Long> exists(@RedisProtocolSupport.Key final CharSequence key1,
                               @RedisProtocolSupport.Key final CharSequence key2,
                               @RedisProtocolSupport.Key final CharSequence key3) {
        return blockingToSingle(() -> blockingCommander.exists(key1, key2, key3));
    }

    @Override
    public Single<Long> exists(@RedisProtocolSupport.Key final Collection<? extends CharSequence> keys) {
        return blockingToSingle(() -> blockingCommander.exists(keys));
    }

    @Override
    public Single<Long> expire(@RedisProtocolSupport.Key final CharSequence key, final long seconds) {
        return blockingToSingle(() -> blockingCommander.expire(key, seconds));
    }

    @Override
    public Single<Long> expireat(@RedisProtocolSupport.Key final CharSequence key, final long timestamp) {
        return blockingToSingle(() -> blockingCommander.expireat(key, timestamp));
    }

    @Override
    public Single<String> flushall() {
        return blockingToSingle(() -> blockingCommander.flushall());
    }

    @Override
    public Single<String> flushall(@Nullable final RedisProtocolSupport.FlushallAsync async) {
        return blockingToSingle(() -> blockingCommander.flushall(async));
    }

    @Override
    public Single<String> flushdb() {
        return blockingToSingle(() -> blockingCommander.flushdb());
    }

    @Override
    public Single<String> flushdb(@Nullable final RedisProtocolSupport.FlushdbAsync async) {
        return blockingToSingle(() -> blockingCommander.flushdb(async));
    }

    @Override
    public Single<Long> geoadd(@RedisProtocolSupport.Key final CharSequence key, final double longitude,
                               final double latitude, final CharSequence member) {
        return blockingToSingle(() -> blockingCommander.geoadd(key, longitude, latitude, member));
    }

    @Override
    public Single<Long> geoadd(@RedisProtocolSupport.Key final CharSequence key, final double longitude1,
                               final double latitude1, final CharSequence member1, final double longitude2,
                               final double latitude2, final CharSequence member2) {
        return blockingToSingle(() -> blockingCommander.geoadd(key, longitude1, latitude1, member1, longitude2,
                    latitude2, member2));
    }

    @Override
    public Single<Long> geoadd(@RedisProtocolSupport.Key final CharSequence key, final double longitude1,
                               final double latitude1, final CharSequence member1, final double longitude2,
                               final double latitude2, final CharSequence member2, final double longitude3,
                               final double latitude3, final CharSequence member3) {
        return blockingToSingle(() -> blockingCommander.geoadd(key, longitude1, latitude1, member1, longitude2,
                    latitude2, member2, longitude3, latitude3, member3));
    }

    @Override
    public Single<Long> geoadd(@RedisProtocolSupport.Key final CharSequence key,
                               final Collection<RedisProtocolSupport.LongitudeLatitudeMember> longitudeLatitudeMembers) {
        return blockingToSingle(() -> blockingCommander.geoadd(key, longitudeLatitudeMembers));
    }

    @Override
    public Single<Double> geodist(@RedisProtocolSupport.Key final CharSequence key, final CharSequence member1,
                                  final CharSequence member2) {
        return blockingToSingle(() -> blockingCommander.geodist(key, member1, member2));
    }

    @Override
    public Single<Double> geodist(@RedisProtocolSupport.Key final CharSequence key, final CharSequence member1,
                                  final CharSequence member2, @Nullable final CharSequence unit) {
        return blockingToSingle(() -> blockingCommander.geodist(key, member1, member2, unit));
    }

    @Override
    public <T> Single<List<T>> geohash(@RedisProtocolSupport.Key final CharSequence key, final CharSequence member) {
        return blockingToSingle(() -> blockingCommander.geohash(key, member));
    }

    @Override
    public <T> Single<List<T>> geohash(@RedisProtocolSupport.Key final CharSequence key, final CharSequence member1,
                                       final CharSequence member2) {
        return blockingToSingle(() -> blockingCommander.geohash(key, member1, member2));
    }

    @Override
    public <T> Single<List<T>> geohash(@RedisProtocolSupport.Key final CharSequence key, final CharSequence member1,
                                       final CharSequence member2, final CharSequence member3) {
        return blockingToSingle(() -> blockingCommander.geohash(key, member1, member2, member3));
    }

    @Override
    public <T> Single<List<T>> geohash(@RedisProtocolSupport.Key final CharSequence key,
                                       final Collection<? extends CharSequence> members) {
        return blockingToSingle(() -> blockingCommander.geohash(key, members));
    }

    @Override
    public <T> Single<List<T>> geopos(@RedisProtocolSupport.Key final CharSequence key, final CharSequence member) {
        return blockingToSingle(() -> blockingCommander.geopos(key, member));
    }

    @Override
    public <T> Single<List<T>> geopos(@RedisProtocolSupport.Key final CharSequence key, final CharSequence member1,
                                      final CharSequence member2) {
        return blockingToSingle(() -> blockingCommander.geopos(key, member1, member2));
    }

    @Override
    public <T> Single<List<T>> geopos(@RedisProtocolSupport.Key final CharSequence key, final CharSequence member1,
                                      final CharSequence member2, final CharSequence member3) {
        return blockingToSingle(() -> blockingCommander.geopos(key, member1, member2, member3));
    }

    @Override
    public <T> Single<List<T>> geopos(@RedisProtocolSupport.Key final CharSequence key,
                                      final Collection<? extends CharSequence> members) {
        return blockingToSingle(() -> blockingCommander.geopos(key, members));
    }

    @Override
    public <T> Single<List<T>> georadius(@RedisProtocolSupport.Key final CharSequence key, final double longitude,
                                         final double latitude, final double radius,
                                         final RedisProtocolSupport.GeoradiusUnit unit) {
        return blockingToSingle(() -> blockingCommander.georadius(key, longitude, latitude, radius, unit));
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
        return blockingToSingle(() -> blockingCommander.georadius(key, longitude, latitude, radius, unit, withcoord,
                    withdist, withhash, count, order, storeKey, storedistKey));
    }

    @Override
    public <T> Single<List<T>> georadiusbymember(@RedisProtocolSupport.Key final CharSequence key,
                                                 final CharSequence member, final double radius,
                                                 final RedisProtocolSupport.GeoradiusbymemberUnit unit) {
        return blockingToSingle(() -> blockingCommander.georadiusbymember(key, member, radius, unit));
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
        return blockingToSingle(() -> blockingCommander.georadiusbymember(key, member, radius, unit, withcoord,
                    withdist, withhash, count, order, storeKey, storedistKey));
    }

    @Override
    public Single<String> get(@RedisProtocolSupport.Key final CharSequence key) {
        return blockingToSingle(() -> blockingCommander.get(key));
    }

    @Override
    public Single<Long> getbit(@RedisProtocolSupport.Key final CharSequence key, final long offset) {
        return blockingToSingle(() -> blockingCommander.getbit(key, offset));
    }

    @Override
    public Single<String> getrange(@RedisProtocolSupport.Key final CharSequence key, final long start, final long end) {
        return blockingToSingle(() -> blockingCommander.getrange(key, start, end));
    }

    @Override
    public Single<String> getset(@RedisProtocolSupport.Key final CharSequence key, final CharSequence value) {
        return blockingToSingle(() -> blockingCommander.getset(key, value));
    }

    @Override
    public Single<Long> hdel(@RedisProtocolSupport.Key final CharSequence key, final CharSequence field) {
        return blockingToSingle(() -> blockingCommander.hdel(key, field));
    }

    @Override
    public Single<Long> hdel(@RedisProtocolSupport.Key final CharSequence key, final CharSequence field1,
                             final CharSequence field2) {
        return blockingToSingle(() -> blockingCommander.hdel(key, field1, field2));
    }

    @Override
    public Single<Long> hdel(@RedisProtocolSupport.Key final CharSequence key, final CharSequence field1,
                             final CharSequence field2, final CharSequence field3) {
        return blockingToSingle(() -> blockingCommander.hdel(key, field1, field2, field3));
    }

    @Override
    public Single<Long> hdel(@RedisProtocolSupport.Key final CharSequence key,
                             final Collection<? extends CharSequence> fields) {
        return blockingToSingle(() -> blockingCommander.hdel(key, fields));
    }

    @Override
    public Single<Long> hexists(@RedisProtocolSupport.Key final CharSequence key, final CharSequence field) {
        return blockingToSingle(() -> blockingCommander.hexists(key, field));
    }

    @Override
    public Single<String> hget(@RedisProtocolSupport.Key final CharSequence key, final CharSequence field) {
        return blockingToSingle(() -> blockingCommander.hget(key, field));
    }

    @Override
    public <T> Single<List<T>> hgetall(@RedisProtocolSupport.Key final CharSequence key) {
        return blockingToSingle(() -> blockingCommander.hgetall(key));
    }

    @Override
    public Single<Long> hincrby(@RedisProtocolSupport.Key final CharSequence key, final CharSequence field,
                                final long increment) {
        return blockingToSingle(() -> blockingCommander.hincrby(key, field, increment));
    }

    @Override
    public Single<Double> hincrbyfloat(@RedisProtocolSupport.Key final CharSequence key, final CharSequence field,
                                       final double increment) {
        return blockingToSingle(() -> blockingCommander.hincrbyfloat(key, field, increment));
    }

    @Override
    public <T> Single<List<T>> hkeys(@RedisProtocolSupport.Key final CharSequence key) {
        return blockingToSingle(() -> blockingCommander.hkeys(key));
    }

    @Override
    public Single<Long> hlen(@RedisProtocolSupport.Key final CharSequence key) {
        return blockingToSingle(() -> blockingCommander.hlen(key));
    }

    @Override
    public Single<List<String>> hmget(@RedisProtocolSupport.Key final CharSequence key, final CharSequence field) {
        return blockingToSingle(() -> blockingCommander.hmget(key, field));
    }

    @Override
    public Single<List<String>> hmget(@RedisProtocolSupport.Key final CharSequence key, final CharSequence field1,
                                      final CharSequence field2) {
        return blockingToSingle(() -> blockingCommander.hmget(key, field1, field2));
    }

    @Override
    public Single<List<String>> hmget(@RedisProtocolSupport.Key final CharSequence key, final CharSequence field1,
                                      final CharSequence field2, final CharSequence field3) {
        return blockingToSingle(() -> blockingCommander.hmget(key, field1, field2, field3));
    }

    @Override
    public Single<List<String>> hmget(@RedisProtocolSupport.Key final CharSequence key,
                                      final Collection<? extends CharSequence> fields) {
        return blockingToSingle(() -> blockingCommander.hmget(key, fields));
    }

    @Override
    public Single<String> hmset(@RedisProtocolSupport.Key final CharSequence key, final CharSequence field,
                                final CharSequence value) {
        return blockingToSingle(() -> blockingCommander.hmset(key, field, value));
    }

    @Override
    public Single<String> hmset(@RedisProtocolSupport.Key final CharSequence key, final CharSequence field1,
                                final CharSequence value1, final CharSequence field2, final CharSequence value2) {
        return blockingToSingle(() -> blockingCommander.hmset(key, field1, value1, field2, value2));
    }

    @Override
    public Single<String> hmset(@RedisProtocolSupport.Key final CharSequence key, final CharSequence field1,
                                final CharSequence value1, final CharSequence field2, final CharSequence value2,
                                final CharSequence field3, final CharSequence value3) {
        return blockingToSingle(() -> blockingCommander.hmset(key, field1, value1, field2, value2, field3, value3));
    }

    @Override
    public Single<String> hmset(@RedisProtocolSupport.Key final CharSequence key,
                                final Collection<RedisProtocolSupport.FieldValue> fieldValues) {
        return blockingToSingle(() -> blockingCommander.hmset(key, fieldValues));
    }

    @Override
    public <T> Single<List<T>> hscan(@RedisProtocolSupport.Key final CharSequence key, final long cursor) {
        return blockingToSingle(() -> blockingCommander.hscan(key, cursor));
    }

    @Override
    public <T> Single<List<T>> hscan(@RedisProtocolSupport.Key final CharSequence key, final long cursor,
                                     @Nullable final CharSequence matchPattern, @Nullable final Long count) {
        return blockingToSingle(() -> blockingCommander.hscan(key, cursor, matchPattern, count));
    }

    @Override
    public Single<Long> hset(@RedisProtocolSupport.Key final CharSequence key, final CharSequence field,
                             final CharSequence value) {
        return blockingToSingle(() -> blockingCommander.hset(key, field, value));
    }

    @Override
    public Single<Long> hsetnx(@RedisProtocolSupport.Key final CharSequence key, final CharSequence field,
                               final CharSequence value) {
        return blockingToSingle(() -> blockingCommander.hsetnx(key, field, value));
    }

    @Override
    public Single<Long> hstrlen(@RedisProtocolSupport.Key final CharSequence key, final CharSequence field) {
        return blockingToSingle(() -> blockingCommander.hstrlen(key, field));
    }

    @Override
    public <T> Single<List<T>> hvals(@RedisProtocolSupport.Key final CharSequence key) {
        return blockingToSingle(() -> blockingCommander.hvals(key));
    }

    @Override
    public Single<Long> incr(@RedisProtocolSupport.Key final CharSequence key) {
        return blockingToSingle(() -> blockingCommander.incr(key));
    }

    @Override
    public Single<Long> incrby(@RedisProtocolSupport.Key final CharSequence key, final long increment) {
        return blockingToSingle(() -> blockingCommander.incrby(key, increment));
    }

    @Override
    public Single<Double> incrbyfloat(@RedisProtocolSupport.Key final CharSequence key, final double increment) {
        return blockingToSingle(() -> blockingCommander.incrbyfloat(key, increment));
    }

    @Override
    public Single<String> info() {
        return blockingToSingle(() -> blockingCommander.info());
    }

    @Override
    public Single<String> info(@Nullable final CharSequence section) {
        return blockingToSingle(() -> blockingCommander.info(section));
    }

    @Override
    public <T> Single<List<T>> keys(final CharSequence pattern) {
        return blockingToSingle(() -> blockingCommander.keys(pattern));
    }

    @Override
    public Single<Long> lastsave() {
        return blockingToSingle(() -> blockingCommander.lastsave());
    }

    @Override
    public Single<String> lindex(@RedisProtocolSupport.Key final CharSequence key, final long index) {
        return blockingToSingle(() -> blockingCommander.lindex(key, index));
    }

    @Override
    public Single<Long> linsert(@RedisProtocolSupport.Key final CharSequence key,
                                final RedisProtocolSupport.LinsertWhere where, final CharSequence pivot,
                                final CharSequence value) {
        return blockingToSingle(() -> blockingCommander.linsert(key, where, pivot, value));
    }

    @Override
    public Single<Long> llen(@RedisProtocolSupport.Key final CharSequence key) {
        return blockingToSingle(() -> blockingCommander.llen(key));
    }

    @Override
    public Single<String> lpop(@RedisProtocolSupport.Key final CharSequence key) {
        return blockingToSingle(() -> blockingCommander.lpop(key));
    }

    @Override
    public Single<Long> lpush(@RedisProtocolSupport.Key final CharSequence key, final CharSequence value) {
        return blockingToSingle(() -> blockingCommander.lpush(key, value));
    }

    @Override
    public Single<Long> lpush(@RedisProtocolSupport.Key final CharSequence key, final CharSequence value1,
                              final CharSequence value2) {
        return blockingToSingle(() -> blockingCommander.lpush(key, value1, value2));
    }

    @Override
    public Single<Long> lpush(@RedisProtocolSupport.Key final CharSequence key, final CharSequence value1,
                              final CharSequence value2, final CharSequence value3) {
        return blockingToSingle(() -> blockingCommander.lpush(key, value1, value2, value3));
    }

    @Override
    public Single<Long> lpush(@RedisProtocolSupport.Key final CharSequence key,
                              final Collection<? extends CharSequence> values) {
        return blockingToSingle(() -> blockingCommander.lpush(key, values));
    }

    @Override
    public Single<Long> lpushx(@RedisProtocolSupport.Key final CharSequence key, final CharSequence value) {
        return blockingToSingle(() -> blockingCommander.lpushx(key, value));
    }

    @Override
    public <T> Single<List<T>> lrange(@RedisProtocolSupport.Key final CharSequence key, final long start,
                                      final long stop) {
        return blockingToSingle(() -> blockingCommander.lrange(key, start, stop));
    }

    @Override
    public Single<Long> lrem(@RedisProtocolSupport.Key final CharSequence key, final long count,
                             final CharSequence value) {
        return blockingToSingle(() -> blockingCommander.lrem(key, count, value));
    }

    @Override
    public Single<String> lset(@RedisProtocolSupport.Key final CharSequence key, final long index,
                               final CharSequence value) {
        return blockingToSingle(() -> blockingCommander.lset(key, index, value));
    }

    @Override
    public Single<String> ltrim(@RedisProtocolSupport.Key final CharSequence key, final long start, final long stop) {
        return blockingToSingle(() -> blockingCommander.ltrim(key, start, stop));
    }

    @Override
    public Single<String> memoryDoctor() {
        return blockingToSingle(() -> blockingCommander.memoryDoctor());
    }

    @Override
    public <T> Single<List<T>> memoryHelp() {
        return blockingToSingle(() -> blockingCommander.memoryHelp());
    }

    @Override
    public Single<String> memoryMallocStats() {
        return blockingToSingle(() -> blockingCommander.memoryMallocStats());
    }

    @Override
    public Single<String> memoryPurge() {
        return blockingToSingle(() -> blockingCommander.memoryPurge());
    }

    @Override
    public <T> Single<List<T>> memoryStats() {
        return blockingToSingle(() -> blockingCommander.memoryStats());
    }

    @Override
    public Single<Long> memoryUsage(@RedisProtocolSupport.Key final CharSequence key) {
        return blockingToSingle(() -> blockingCommander.memoryUsage(key));
    }

    @Override
    public Single<Long> memoryUsage(@RedisProtocolSupport.Key final CharSequence key,
                                    @Nullable final Long samplesCount) {
        return blockingToSingle(() -> blockingCommander.memoryUsage(key, samplesCount));
    }

    @Override
    public Single<List<String>> mget(@RedisProtocolSupport.Key final CharSequence key) {
        return blockingToSingle(() -> blockingCommander.mget(key));
    }

    @Override
    public Single<List<String>> mget(@RedisProtocolSupport.Key final CharSequence key1,
                                     @RedisProtocolSupport.Key final CharSequence key2) {
        return blockingToSingle(() -> blockingCommander.mget(key1, key2));
    }

    @Override
    public Single<List<String>> mget(@RedisProtocolSupport.Key final CharSequence key1,
                                     @RedisProtocolSupport.Key final CharSequence key2,
                                     @RedisProtocolSupport.Key final CharSequence key3) {
        return blockingToSingle(() -> blockingCommander.mget(key1, key2, key3));
    }

    @Override
    public Single<List<String>> mget(@RedisProtocolSupport.Key final Collection<? extends CharSequence> keys) {
        return blockingToSingle(() -> blockingCommander.mget(keys));
    }

    @Override
    public Publisher<String> monitor() {
        return blockingToPublisher(() -> blockingCommander.monitor());
    }

    @Override
    public Single<Long> move(@RedisProtocolSupport.Key final CharSequence key, final long db) {
        return blockingToSingle(() -> blockingCommander.move(key, db));
    }

    @Override
    public Single<String> mset(@RedisProtocolSupport.Key final CharSequence key, final CharSequence value) {
        return blockingToSingle(() -> blockingCommander.mset(key, value));
    }

    @Override
    public Single<String> mset(@RedisProtocolSupport.Key final CharSequence key1, final CharSequence value1,
                               @RedisProtocolSupport.Key final CharSequence key2, final CharSequence value2) {
        return blockingToSingle(() -> blockingCommander.mset(key1, value1, key2, value2));
    }

    @Override
    public Single<String> mset(@RedisProtocolSupport.Key final CharSequence key1, final CharSequence value1,
                               @RedisProtocolSupport.Key final CharSequence key2, final CharSequence value2,
                               @RedisProtocolSupport.Key final CharSequence key3, final CharSequence value3) {
        return blockingToSingle(() -> blockingCommander.mset(key1, value1, key2, value2, key3, value3));
    }

    @Override
    public Single<String> mset(final Collection<RedisProtocolSupport.KeyValue> keyValues) {
        return blockingToSingle(() -> blockingCommander.mset(keyValues));
    }

    @Override
    public Single<Long> msetnx(@RedisProtocolSupport.Key final CharSequence key, final CharSequence value) {
        return blockingToSingle(() -> blockingCommander.msetnx(key, value));
    }

    @Override
    public Single<Long> msetnx(@RedisProtocolSupport.Key final CharSequence key1, final CharSequence value1,
                               @RedisProtocolSupport.Key final CharSequence key2, final CharSequence value2) {
        return blockingToSingle(() -> blockingCommander.msetnx(key1, value1, key2, value2));
    }

    @Override
    public Single<Long> msetnx(@RedisProtocolSupport.Key final CharSequence key1, final CharSequence value1,
                               @RedisProtocolSupport.Key final CharSequence key2, final CharSequence value2,
                               @RedisProtocolSupport.Key final CharSequence key3, final CharSequence value3) {
        return blockingToSingle(() -> blockingCommander.msetnx(key1, value1, key2, value2, key3, value3));
    }

    @Override
    public Single<Long> msetnx(final Collection<RedisProtocolSupport.KeyValue> keyValues) {
        return blockingToSingle(() -> blockingCommander.msetnx(keyValues));
    }

    @Override
    public Single<TransactedRedisCommander> multi() {
        return blockingToSingle(
                    () -> new BlockingTransactedRedisCommanderToTransactedRedisCommander(blockingCommander.multi()));
    }

    @Override
    public Single<String> objectEncoding(@RedisProtocolSupport.Key final CharSequence key) {
        return blockingToSingle(() -> blockingCommander.objectEncoding(key));
    }

    @Override
    public Single<Long> objectFreq(@RedisProtocolSupport.Key final CharSequence key) {
        return blockingToSingle(() -> blockingCommander.objectFreq(key));
    }

    @Override
    public Single<List<String>> objectHelp() {
        return blockingToSingle(() -> blockingCommander.objectHelp());
    }

    @Override
    public Single<Long> objectIdletime(@RedisProtocolSupport.Key final CharSequence key) {
        return blockingToSingle(() -> blockingCommander.objectIdletime(key));
    }

    @Override
    public Single<Long> objectRefcount(@RedisProtocolSupport.Key final CharSequence key) {
        return blockingToSingle(() -> blockingCommander.objectRefcount(key));
    }

    @Override
    public Single<Long> persist(@RedisProtocolSupport.Key final CharSequence key) {
        return blockingToSingle(() -> blockingCommander.persist(key));
    }

    @Override
    public Single<Long> pexpire(@RedisProtocolSupport.Key final CharSequence key, final long milliseconds) {
        return blockingToSingle(() -> blockingCommander.pexpire(key, milliseconds));
    }

    @Override
    public Single<Long> pexpireat(@RedisProtocolSupport.Key final CharSequence key, final long millisecondsTimestamp) {
        return blockingToSingle(() -> blockingCommander.pexpireat(key, millisecondsTimestamp));
    }

    @Override
    public Single<Long> pfadd(@RedisProtocolSupport.Key final CharSequence key, final CharSequence element) {
        return blockingToSingle(() -> blockingCommander.pfadd(key, element));
    }

    @Override
    public Single<Long> pfadd(@RedisProtocolSupport.Key final CharSequence key, final CharSequence element1,
                              final CharSequence element2) {
        return blockingToSingle(() -> blockingCommander.pfadd(key, element1, element2));
    }

    @Override
    public Single<Long> pfadd(@RedisProtocolSupport.Key final CharSequence key, final CharSequence element1,
                              final CharSequence element2, final CharSequence element3) {
        return blockingToSingle(() -> blockingCommander.pfadd(key, element1, element2, element3));
    }

    @Override
    public Single<Long> pfadd(@RedisProtocolSupport.Key final CharSequence key,
                              final Collection<? extends CharSequence> elements) {
        return blockingToSingle(() -> blockingCommander.pfadd(key, elements));
    }

    @Override
    public Single<Long> pfcount(@RedisProtocolSupport.Key final CharSequence key) {
        return blockingToSingle(() -> blockingCommander.pfcount(key));
    }

    @Override
    public Single<Long> pfcount(@RedisProtocolSupport.Key final CharSequence key1,
                                @RedisProtocolSupport.Key final CharSequence key2) {
        return blockingToSingle(() -> blockingCommander.pfcount(key1, key2));
    }

    @Override
    public Single<Long> pfcount(@RedisProtocolSupport.Key final CharSequence key1,
                                @RedisProtocolSupport.Key final CharSequence key2,
                                @RedisProtocolSupport.Key final CharSequence key3) {
        return blockingToSingle(() -> blockingCommander.pfcount(key1, key2, key3));
    }

    @Override
    public Single<Long> pfcount(@RedisProtocolSupport.Key final Collection<? extends CharSequence> keys) {
        return blockingToSingle(() -> blockingCommander.pfcount(keys));
    }

    @Override
    public Single<String> pfmerge(@RedisProtocolSupport.Key final CharSequence destkey,
                                  @RedisProtocolSupport.Key final CharSequence sourcekey) {
        return blockingToSingle(() -> blockingCommander.pfmerge(destkey, sourcekey));
    }

    @Override
    public Single<String> pfmerge(@RedisProtocolSupport.Key final CharSequence destkey,
                                  @RedisProtocolSupport.Key final CharSequence sourcekey1,
                                  @RedisProtocolSupport.Key final CharSequence sourcekey2) {
        return blockingToSingle(() -> blockingCommander.pfmerge(destkey, sourcekey1, sourcekey2));
    }

    @Override
    public Single<String> pfmerge(@RedisProtocolSupport.Key final CharSequence destkey,
                                  @RedisProtocolSupport.Key final CharSequence sourcekey1,
                                  @RedisProtocolSupport.Key final CharSequence sourcekey2,
                                  @RedisProtocolSupport.Key final CharSequence sourcekey3) {
        return blockingToSingle(() -> blockingCommander.pfmerge(destkey, sourcekey1, sourcekey2, sourcekey3));
    }

    @Override
    public Single<String> pfmerge(@RedisProtocolSupport.Key final CharSequence destkey,
                                  @RedisProtocolSupport.Key final Collection<? extends CharSequence> sourcekeys) {
        return blockingToSingle(() -> blockingCommander.pfmerge(destkey, sourcekeys));
    }

    @Override
    public Single<String> ping() {
        return blockingToSingle(() -> blockingCommander.ping());
    }

    @Override
    public Single<String> ping(final CharSequence message) {
        return blockingToSingle(() -> blockingCommander.ping(message));
    }

    @Override
    public Single<String> psetex(@RedisProtocolSupport.Key final CharSequence key, final long milliseconds,
                                 final CharSequence value) {
        return blockingToSingle(() -> blockingCommander.psetex(key, milliseconds, value));
    }

    @Override
    public Single<PubSubRedisConnection> psubscribe(final CharSequence pattern) {
        return blockingToSingle(() -> new BlockingPubSubRedisConnectionToPubSubRedisConnection(
                    blockingCommander.psubscribe(pattern)));
    }

    @Override
    public Single<Long> pttl(@RedisProtocolSupport.Key final CharSequence key) {
        return blockingToSingle(() -> blockingCommander.pttl(key));
    }

    @Override
    public Single<Long> publish(final CharSequence channel, final CharSequence message) {
        return blockingToSingle(() -> blockingCommander.publish(channel, message));
    }

    @Override
    public Single<List<String>> pubsubChannels() {
        return blockingToSingle(() -> blockingCommander.pubsubChannels());
    }

    @Override
    public Single<List<String>> pubsubChannels(@Nullable final CharSequence pattern) {
        return blockingToSingle(() -> blockingCommander.pubsubChannels(pattern));
    }

    @Override
    public Single<List<String>> pubsubChannels(@Nullable final CharSequence pattern1,
                                               @Nullable final CharSequence pattern2) {
        return blockingToSingle(() -> blockingCommander.pubsubChannels(pattern1, pattern2));
    }

    @Override
    public Single<List<String>> pubsubChannels(@Nullable final CharSequence pattern1,
                                               @Nullable final CharSequence pattern2,
                                               @Nullable final CharSequence pattern3) {
        return blockingToSingle(() -> blockingCommander.pubsubChannels(pattern1, pattern2, pattern3));
    }

    @Override
    public Single<List<String>> pubsubChannels(final Collection<? extends CharSequence> patterns) {
        return blockingToSingle(() -> blockingCommander.pubsubChannels(patterns));
    }

    @Override
    public <T> Single<List<T>> pubsubNumsub() {
        return blockingToSingle(() -> blockingCommander.pubsubNumsub());
    }

    @Override
    public <T> Single<List<T>> pubsubNumsub(@Nullable final CharSequence channel) {
        return blockingToSingle(() -> blockingCommander.pubsubNumsub(channel));
    }

    @Override
    public <T> Single<List<T>> pubsubNumsub(@Nullable final CharSequence channel1,
                                            @Nullable final CharSequence channel2) {
        return blockingToSingle(() -> blockingCommander.pubsubNumsub(channel1, channel2));
    }

    @Override
    public <T> Single<List<T>> pubsubNumsub(@Nullable final CharSequence channel1,
                                            @Nullable final CharSequence channel2,
                                            @Nullable final CharSequence channel3) {
        return blockingToSingle(() -> blockingCommander.pubsubNumsub(channel1, channel2, channel3));
    }

    @Override
    public <T> Single<List<T>> pubsubNumsub(final Collection<? extends CharSequence> channels) {
        return blockingToSingle(() -> blockingCommander.pubsubNumsub(channels));
    }

    @Override
    public Single<Long> pubsubNumpat() {
        return blockingToSingle(() -> blockingCommander.pubsubNumpat());
    }

    @Override
    public Single<String> randomkey() {
        return blockingToSingle(() -> blockingCommander.randomkey());
    }

    @Override
    public Single<String> readonly() {
        return blockingToSingle(() -> blockingCommander.readonly());
    }

    @Override
    public Single<String> readwrite() {
        return blockingToSingle(() -> blockingCommander.readwrite());
    }

    @Override
    public Single<String> rename(@RedisProtocolSupport.Key final CharSequence key,
                                 @RedisProtocolSupport.Key final CharSequence newkey) {
        return blockingToSingle(() -> blockingCommander.rename(key, newkey));
    }

    @Override
    public Single<Long> renamenx(@RedisProtocolSupport.Key final CharSequence key,
                                 @RedisProtocolSupport.Key final CharSequence newkey) {
        return blockingToSingle(() -> blockingCommander.renamenx(key, newkey));
    }

    @Override
    public Single<String> restore(@RedisProtocolSupport.Key final CharSequence key, final long ttl,
                                  final CharSequence serializedValue) {
        return blockingToSingle(() -> blockingCommander.restore(key, ttl, serializedValue));
    }

    @Override
    public Single<String> restore(@RedisProtocolSupport.Key final CharSequence key, final long ttl,
                                  final CharSequence serializedValue,
                                  @Nullable final RedisProtocolSupport.RestoreReplace replace) {
        return blockingToSingle(() -> blockingCommander.restore(key, ttl, serializedValue, replace));
    }

    @Override
    public <T> Single<List<T>> role() {
        return blockingToSingle(() -> blockingCommander.role());
    }

    @Override
    public Single<String> rpop(@RedisProtocolSupport.Key final CharSequence key) {
        return blockingToSingle(() -> blockingCommander.rpop(key));
    }

    @Override
    public Single<String> rpoplpush(@RedisProtocolSupport.Key final CharSequence source,
                                    @RedisProtocolSupport.Key final CharSequence destination) {
        return blockingToSingle(() -> blockingCommander.rpoplpush(source, destination));
    }

    @Override
    public Single<Long> rpush(@RedisProtocolSupport.Key final CharSequence key, final CharSequence value) {
        return blockingToSingle(() -> blockingCommander.rpush(key, value));
    }

    @Override
    public Single<Long> rpush(@RedisProtocolSupport.Key final CharSequence key, final CharSequence value1,
                              final CharSequence value2) {
        return blockingToSingle(() -> blockingCommander.rpush(key, value1, value2));
    }

    @Override
    public Single<Long> rpush(@RedisProtocolSupport.Key final CharSequence key, final CharSequence value1,
                              final CharSequence value2, final CharSequence value3) {
        return blockingToSingle(() -> blockingCommander.rpush(key, value1, value2, value3));
    }

    @Override
    public Single<Long> rpush(@RedisProtocolSupport.Key final CharSequence key,
                              final Collection<? extends CharSequence> values) {
        return blockingToSingle(() -> blockingCommander.rpush(key, values));
    }

    @Override
    public Single<Long> rpushx(@RedisProtocolSupport.Key final CharSequence key, final CharSequence value) {
        return blockingToSingle(() -> blockingCommander.rpushx(key, value));
    }

    @Override
    public Single<Long> sadd(@RedisProtocolSupport.Key final CharSequence key, final CharSequence member) {
        return blockingToSingle(() -> blockingCommander.sadd(key, member));
    }

    @Override
    public Single<Long> sadd(@RedisProtocolSupport.Key final CharSequence key, final CharSequence member1,
                             final CharSequence member2) {
        return blockingToSingle(() -> blockingCommander.sadd(key, member1, member2));
    }

    @Override
    public Single<Long> sadd(@RedisProtocolSupport.Key final CharSequence key, final CharSequence member1,
                             final CharSequence member2, final CharSequence member3) {
        return blockingToSingle(() -> blockingCommander.sadd(key, member1, member2, member3));
    }

    @Override
    public Single<Long> sadd(@RedisProtocolSupport.Key final CharSequence key,
                             final Collection<? extends CharSequence> members) {
        return blockingToSingle(() -> blockingCommander.sadd(key, members));
    }

    @Override
    public Single<String> save() {
        return blockingToSingle(() -> blockingCommander.save());
    }

    @Override
    public <T> Single<List<T>> scan(final long cursor) {
        return blockingToSingle(() -> blockingCommander.scan(cursor));
    }

    @Override
    public <T> Single<List<T>> scan(final long cursor, @Nullable final CharSequence matchPattern,
                                    @Nullable final Long count) {
        return blockingToSingle(() -> blockingCommander.scan(cursor, matchPattern, count));
    }

    @Override
    public Single<Long> scard(@RedisProtocolSupport.Key final CharSequence key) {
        return blockingToSingle(() -> blockingCommander.scard(key));
    }

    @Override
    public Single<String> scriptDebug(final RedisProtocolSupport.ScriptDebugMode mode) {
        return blockingToSingle(() -> blockingCommander.scriptDebug(mode));
    }

    @Override
    public <T> Single<List<T>> scriptExists(final CharSequence sha1) {
        return blockingToSingle(() -> blockingCommander.scriptExists(sha1));
    }

    @Override
    public <T> Single<List<T>> scriptExists(final CharSequence sha11, final CharSequence sha12) {
        return blockingToSingle(() -> blockingCommander.scriptExists(sha11, sha12));
    }

    @Override
    public <T> Single<List<T>> scriptExists(final CharSequence sha11, final CharSequence sha12,
                                            final CharSequence sha13) {
        return blockingToSingle(() -> blockingCommander.scriptExists(sha11, sha12, sha13));
    }

    @Override
    public <T> Single<List<T>> scriptExists(final Collection<? extends CharSequence> sha1s) {
        return blockingToSingle(() -> blockingCommander.scriptExists(sha1s));
    }

    @Override
    public Single<String> scriptFlush() {
        return blockingToSingle(() -> blockingCommander.scriptFlush());
    }

    @Override
    public Single<String> scriptKill() {
        return blockingToSingle(() -> blockingCommander.scriptKill());
    }

    @Override
    public Single<String> scriptLoad(final CharSequence script) {
        return blockingToSingle(() -> blockingCommander.scriptLoad(script));
    }

    @Override
    public <T> Single<List<T>> sdiff(@RedisProtocolSupport.Key final CharSequence firstkey) {
        return blockingToSingle(() -> blockingCommander.sdiff(firstkey));
    }

    @Override
    public <T> Single<List<T>> sdiff(@RedisProtocolSupport.Key final CharSequence firstkey,
                                     @Nullable @RedisProtocolSupport.Key final CharSequence otherkey) {
        return blockingToSingle(() -> blockingCommander.sdiff(firstkey, otherkey));
    }

    @Override
    public <T> Single<List<T>> sdiff(@RedisProtocolSupport.Key final CharSequence firstkey,
                                     @Nullable @RedisProtocolSupport.Key final CharSequence otherkey1,
                                     @Nullable @RedisProtocolSupport.Key final CharSequence otherkey2) {
        return blockingToSingle(() -> blockingCommander.sdiff(firstkey, otherkey1, otherkey2));
    }

    @Override
    public <T> Single<List<T>> sdiff(@RedisProtocolSupport.Key final CharSequence firstkey,
                                     @Nullable @RedisProtocolSupport.Key final CharSequence otherkey1,
                                     @Nullable @RedisProtocolSupport.Key final CharSequence otherkey2,
                                     @Nullable @RedisProtocolSupport.Key final CharSequence otherkey3) {
        return blockingToSingle(() -> blockingCommander.sdiff(firstkey, otherkey1, otherkey2, otherkey3));
    }

    @Override
    public <T> Single<List<T>> sdiff(@RedisProtocolSupport.Key final CharSequence firstkey,
                                     @RedisProtocolSupport.Key final Collection<? extends CharSequence> otherkeys) {
        return blockingToSingle(() -> blockingCommander.sdiff(firstkey, otherkeys));
    }

    @Override
    public Single<Long> sdiffstore(@RedisProtocolSupport.Key final CharSequence destination,
                                   @RedisProtocolSupport.Key final CharSequence firstkey) {
        return blockingToSingle(() -> blockingCommander.sdiffstore(destination, firstkey));
    }

    @Override
    public Single<Long> sdiffstore(@RedisProtocolSupport.Key final CharSequence destination,
                                   @RedisProtocolSupport.Key final CharSequence firstkey,
                                   @Nullable @RedisProtocolSupport.Key final CharSequence otherkey) {
        return blockingToSingle(() -> blockingCommander.sdiffstore(destination, firstkey, otherkey));
    }

    @Override
    public Single<Long> sdiffstore(@RedisProtocolSupport.Key final CharSequence destination,
                                   @RedisProtocolSupport.Key final CharSequence firstkey,
                                   @Nullable @RedisProtocolSupport.Key final CharSequence otherkey1,
                                   @Nullable @RedisProtocolSupport.Key final CharSequence otherkey2) {
        return blockingToSingle(() -> blockingCommander.sdiffstore(destination, firstkey, otherkey1, otherkey2));
    }

    @Override
    public Single<Long> sdiffstore(@RedisProtocolSupport.Key final CharSequence destination,
                                   @RedisProtocolSupport.Key final CharSequence firstkey,
                                   @Nullable @RedisProtocolSupport.Key final CharSequence otherkey1,
                                   @Nullable @RedisProtocolSupport.Key final CharSequence otherkey2,
                                   @Nullable @RedisProtocolSupport.Key final CharSequence otherkey3) {
        return blockingToSingle(
                    () -> blockingCommander.sdiffstore(destination, firstkey, otherkey1, otherkey2, otherkey3));
    }

    @Override
    public Single<Long> sdiffstore(@RedisProtocolSupport.Key final CharSequence destination,
                                   @RedisProtocolSupport.Key final CharSequence firstkey,
                                   @RedisProtocolSupport.Key final Collection<? extends CharSequence> otherkeys) {
        return blockingToSingle(() -> blockingCommander.sdiffstore(destination, firstkey, otherkeys));
    }

    @Override
    public Single<String> select(final long index) {
        return blockingToSingle(() -> blockingCommander.select(index));
    }

    @Override
    public Single<String> set(@RedisProtocolSupport.Key final CharSequence key, final CharSequence value) {
        return blockingToSingle(() -> blockingCommander.set(key, value));
    }

    @Override
    public Single<String> set(@RedisProtocolSupport.Key final CharSequence key, final CharSequence value,
                              @Nullable final RedisProtocolSupport.ExpireDuration expireDuration,
                              @Nullable final RedisProtocolSupport.SetCondition condition) {
        return blockingToSingle(() -> blockingCommander.set(key, value, expireDuration, condition));
    }

    @Override
    public Single<Long> setbit(@RedisProtocolSupport.Key final CharSequence key, final long offset,
                               final CharSequence value) {
        return blockingToSingle(() -> blockingCommander.setbit(key, offset, value));
    }

    @Override
    public Single<String> setex(@RedisProtocolSupport.Key final CharSequence key, final long seconds,
                                final CharSequence value) {
        return blockingToSingle(() -> blockingCommander.setex(key, seconds, value));
    }

    @Override
    public Single<Long> setnx(@RedisProtocolSupport.Key final CharSequence key, final CharSequence value) {
        return blockingToSingle(() -> blockingCommander.setnx(key, value));
    }

    @Override
    public Single<Long> setrange(@RedisProtocolSupport.Key final CharSequence key, final long offset,
                                 final CharSequence value) {
        return blockingToSingle(() -> blockingCommander.setrange(key, offset, value));
    }

    @Override
    public Single<String> shutdown() {
        return blockingToSingle(() -> blockingCommander.shutdown());
    }

    @Override
    public Single<String> shutdown(@Nullable final RedisProtocolSupport.ShutdownSaveMode saveMode) {
        return blockingToSingle(() -> blockingCommander.shutdown(saveMode));
    }

    @Override
    public <T> Single<List<T>> sinter(@RedisProtocolSupport.Key final CharSequence key) {
        return blockingToSingle(() -> blockingCommander.sinter(key));
    }

    @Override
    public <T> Single<List<T>> sinter(@RedisProtocolSupport.Key final CharSequence key1,
                                      @RedisProtocolSupport.Key final CharSequence key2) {
        return blockingToSingle(() -> blockingCommander.sinter(key1, key2));
    }

    @Override
    public <T> Single<List<T>> sinter(@RedisProtocolSupport.Key final CharSequence key1,
                                      @RedisProtocolSupport.Key final CharSequence key2,
                                      @RedisProtocolSupport.Key final CharSequence key3) {
        return blockingToSingle(() -> blockingCommander.sinter(key1, key2, key3));
    }

    @Override
    public <T> Single<List<T>> sinter(@RedisProtocolSupport.Key final Collection<? extends CharSequence> keys) {
        return blockingToSingle(() -> blockingCommander.sinter(keys));
    }

    @Override
    public Single<Long> sinterstore(@RedisProtocolSupport.Key final CharSequence destination,
                                    @RedisProtocolSupport.Key final CharSequence key) {
        return blockingToSingle(() -> blockingCommander.sinterstore(destination, key));
    }

    @Override
    public Single<Long> sinterstore(@RedisProtocolSupport.Key final CharSequence destination,
                                    @RedisProtocolSupport.Key final CharSequence key1,
                                    @RedisProtocolSupport.Key final CharSequence key2) {
        return blockingToSingle(() -> blockingCommander.sinterstore(destination, key1, key2));
    }

    @Override
    public Single<Long> sinterstore(@RedisProtocolSupport.Key final CharSequence destination,
                                    @RedisProtocolSupport.Key final CharSequence key1,
                                    @RedisProtocolSupport.Key final CharSequence key2,
                                    @RedisProtocolSupport.Key final CharSequence key3) {
        return blockingToSingle(() -> blockingCommander.sinterstore(destination, key1, key2, key3));
    }

    @Override
    public Single<Long> sinterstore(@RedisProtocolSupport.Key final CharSequence destination,
                                    @RedisProtocolSupport.Key final Collection<? extends CharSequence> keys) {
        return blockingToSingle(() -> blockingCommander.sinterstore(destination, keys));
    }

    @Override
    public Single<Long> sismember(@RedisProtocolSupport.Key final CharSequence key, final CharSequence member) {
        return blockingToSingle(() -> blockingCommander.sismember(key, member));
    }

    @Override
    public Single<String> slaveof(final CharSequence host, final CharSequence port) {
        return blockingToSingle(() -> blockingCommander.slaveof(host, port));
    }

    @Override
    public <T> Single<List<T>> slowlog(final CharSequence subcommand) {
        return blockingToSingle(() -> blockingCommander.slowlog(subcommand));
    }

    @Override
    public <T> Single<List<T>> slowlog(final CharSequence subcommand, @Nullable final CharSequence argument) {
        return blockingToSingle(() -> blockingCommander.slowlog(subcommand, argument));
    }

    @Override
    public <T> Single<List<T>> smembers(@RedisProtocolSupport.Key final CharSequence key) {
        return blockingToSingle(() -> blockingCommander.smembers(key));
    }

    @Override
    public Single<Long> smove(@RedisProtocolSupport.Key final CharSequence source,
                              @RedisProtocolSupport.Key final CharSequence destination, final CharSequence member) {
        return blockingToSingle(() -> blockingCommander.smove(source, destination, member));
    }

    @Override
    public <T> Single<List<T>> sort(@RedisProtocolSupport.Key final CharSequence key) {
        return blockingToSingle(() -> blockingCommander.sort(key));
    }

    @Override
    public <T> Single<List<T>> sort(@RedisProtocolSupport.Key final CharSequence key,
                                    @Nullable final CharSequence byPattern,
                                    @Nullable final RedisProtocolSupport.OffsetCount offsetCount,
                                    final Collection<? extends CharSequence> getPatterns,
                                    @Nullable final RedisProtocolSupport.SortOrder order,
                                    @Nullable final RedisProtocolSupport.SortSorting sorting) {
        return blockingToSingle(() -> blockingCommander.sort(key, byPattern, offsetCount, getPatterns, order, sorting));
    }

    @Override
    public Single<Long> sort(@RedisProtocolSupport.Key final CharSequence key,
                             @RedisProtocolSupport.Key final CharSequence storeDestination) {
        return blockingToSingle(() -> blockingCommander.sort(key, storeDestination));
    }

    @Override
    public Single<Long> sort(@RedisProtocolSupport.Key final CharSequence key,
                             @RedisProtocolSupport.Key final CharSequence storeDestination,
                             @Nullable final CharSequence byPattern,
                             @Nullable final RedisProtocolSupport.OffsetCount offsetCount,
                             final Collection<? extends CharSequence> getPatterns,
                             @Nullable final RedisProtocolSupport.SortOrder order,
                             @Nullable final RedisProtocolSupport.SortSorting sorting) {
        return blockingToSingle(() -> blockingCommander.sort(key, storeDestination, byPattern, offsetCount, getPatterns,
                    order, sorting));
    }

    @Override
    public Single<String> spop(@RedisProtocolSupport.Key final CharSequence key) {
        return blockingToSingle(() -> blockingCommander.spop(key));
    }

    @Override
    public Single<String> spop(@RedisProtocolSupport.Key final CharSequence key, @Nullable final Long count) {
        return blockingToSingle(() -> blockingCommander.spop(key, count));
    }

    @Override
    public Single<String> srandmember(@RedisProtocolSupport.Key final CharSequence key) {
        return blockingToSingle(() -> blockingCommander.srandmember(key));
    }

    @Override
    public Single<List<String>> srandmember(@RedisProtocolSupport.Key final CharSequence key, final long count) {
        return blockingToSingle(() -> blockingCommander.srandmember(key, count));
    }

    @Override
    public Single<Long> srem(@RedisProtocolSupport.Key final CharSequence key, final CharSequence member) {
        return blockingToSingle(() -> blockingCommander.srem(key, member));
    }

    @Override
    public Single<Long> srem(@RedisProtocolSupport.Key final CharSequence key, final CharSequence member1,
                             final CharSequence member2) {
        return blockingToSingle(() -> blockingCommander.srem(key, member1, member2));
    }

    @Override
    public Single<Long> srem(@RedisProtocolSupport.Key final CharSequence key, final CharSequence member1,
                             final CharSequence member2, final CharSequence member3) {
        return blockingToSingle(() -> blockingCommander.srem(key, member1, member2, member3));
    }

    @Override
    public Single<Long> srem(@RedisProtocolSupport.Key final CharSequence key,
                             final Collection<? extends CharSequence> members) {
        return blockingToSingle(() -> blockingCommander.srem(key, members));
    }

    @Override
    public <T> Single<List<T>> sscan(@RedisProtocolSupport.Key final CharSequence key, final long cursor) {
        return blockingToSingle(() -> blockingCommander.sscan(key, cursor));
    }

    @Override
    public <T> Single<List<T>> sscan(@RedisProtocolSupport.Key final CharSequence key, final long cursor,
                                     @Nullable final CharSequence matchPattern, @Nullable final Long count) {
        return blockingToSingle(() -> blockingCommander.sscan(key, cursor, matchPattern, count));
    }

    @Override
    public Single<Long> strlen(@RedisProtocolSupport.Key final CharSequence key) {
        return blockingToSingle(() -> blockingCommander.strlen(key));
    }

    @Override
    public Single<PubSubRedisConnection> subscribe(final CharSequence channel) {
        return blockingToSingle(() -> new BlockingPubSubRedisConnectionToPubSubRedisConnection(
                    blockingCommander.subscribe(channel)));
    }

    @Override
    public <T> Single<List<T>> sunion(@RedisProtocolSupport.Key final CharSequence key) {
        return blockingToSingle(() -> blockingCommander.sunion(key));
    }

    @Override
    public <T> Single<List<T>> sunion(@RedisProtocolSupport.Key final CharSequence key1,
                                      @RedisProtocolSupport.Key final CharSequence key2) {
        return blockingToSingle(() -> blockingCommander.sunion(key1, key2));
    }

    @Override
    public <T> Single<List<T>> sunion(@RedisProtocolSupport.Key final CharSequence key1,
                                      @RedisProtocolSupport.Key final CharSequence key2,
                                      @RedisProtocolSupport.Key final CharSequence key3) {
        return blockingToSingle(() -> blockingCommander.sunion(key1, key2, key3));
    }

    @Override
    public <T> Single<List<T>> sunion(@RedisProtocolSupport.Key final Collection<? extends CharSequence> keys) {
        return blockingToSingle(() -> blockingCommander.sunion(keys));
    }

    @Override
    public Single<Long> sunionstore(@RedisProtocolSupport.Key final CharSequence destination,
                                    @RedisProtocolSupport.Key final CharSequence key) {
        return blockingToSingle(() -> blockingCommander.sunionstore(destination, key));
    }

    @Override
    public Single<Long> sunionstore(@RedisProtocolSupport.Key final CharSequence destination,
                                    @RedisProtocolSupport.Key final CharSequence key1,
                                    @RedisProtocolSupport.Key final CharSequence key2) {
        return blockingToSingle(() -> blockingCommander.sunionstore(destination, key1, key2));
    }

    @Override
    public Single<Long> sunionstore(@RedisProtocolSupport.Key final CharSequence destination,
                                    @RedisProtocolSupport.Key final CharSequence key1,
                                    @RedisProtocolSupport.Key final CharSequence key2,
                                    @RedisProtocolSupport.Key final CharSequence key3) {
        return blockingToSingle(() -> blockingCommander.sunionstore(destination, key1, key2, key3));
    }

    @Override
    public Single<Long> sunionstore(@RedisProtocolSupport.Key final CharSequence destination,
                                    @RedisProtocolSupport.Key final Collection<? extends CharSequence> keys) {
        return blockingToSingle(() -> blockingCommander.sunionstore(destination, keys));
    }

    @Override
    public Single<String> swapdb(final long index, final long index1) {
        return blockingToSingle(() -> blockingCommander.swapdb(index, index1));
    }

    @Override
    public <T> Single<List<T>> time() {
        return blockingToSingle(() -> blockingCommander.time());
    }

    @Override
    public Single<Long> touch(@RedisProtocolSupport.Key final CharSequence key) {
        return blockingToSingle(() -> blockingCommander.touch(key));
    }

    @Override
    public Single<Long> touch(@RedisProtocolSupport.Key final CharSequence key1,
                              @RedisProtocolSupport.Key final CharSequence key2) {
        return blockingToSingle(() -> blockingCommander.touch(key1, key2));
    }

    @Override
    public Single<Long> touch(@RedisProtocolSupport.Key final CharSequence key1,
                              @RedisProtocolSupport.Key final CharSequence key2,
                              @RedisProtocolSupport.Key final CharSequence key3) {
        return blockingToSingle(() -> blockingCommander.touch(key1, key2, key3));
    }

    @Override
    public Single<Long> touch(@RedisProtocolSupport.Key final Collection<? extends CharSequence> keys) {
        return blockingToSingle(() -> blockingCommander.touch(keys));
    }

    @Override
    public Single<Long> ttl(@RedisProtocolSupport.Key final CharSequence key) {
        return blockingToSingle(() -> blockingCommander.ttl(key));
    }

    @Override
    public Single<String> type(@RedisProtocolSupport.Key final CharSequence key) {
        return blockingToSingle(() -> blockingCommander.type(key));
    }

    @Override
    public Single<Long> unlink(@RedisProtocolSupport.Key final CharSequence key) {
        return blockingToSingle(() -> blockingCommander.unlink(key));
    }

    @Override
    public Single<Long> unlink(@RedisProtocolSupport.Key final CharSequence key1,
                               @RedisProtocolSupport.Key final CharSequence key2) {
        return blockingToSingle(() -> blockingCommander.unlink(key1, key2));
    }

    @Override
    public Single<Long> unlink(@RedisProtocolSupport.Key final CharSequence key1,
                               @RedisProtocolSupport.Key final CharSequence key2,
                               @RedisProtocolSupport.Key final CharSequence key3) {
        return blockingToSingle(() -> blockingCommander.unlink(key1, key2, key3));
    }

    @Override
    public Single<Long> unlink(@RedisProtocolSupport.Key final Collection<? extends CharSequence> keys) {
        return blockingToSingle(() -> blockingCommander.unlink(keys));
    }

    @Override
    public Single<String> unwatch() {
        return blockingToSingle(() -> blockingCommander.unwatch());
    }

    @Override
    public Single<Long> wait(final long numslaves, final long timeout) {
        return blockingToSingle(() -> blockingCommander.wait(numslaves, timeout));
    }

    @Override
    public Single<String> watch(@RedisProtocolSupport.Key final CharSequence key) {
        return blockingToSingle(() -> blockingCommander.watch(key));
    }

    @Override
    public Single<String> watch(@RedisProtocolSupport.Key final CharSequence key1,
                                @RedisProtocolSupport.Key final CharSequence key2) {
        return blockingToSingle(() -> blockingCommander.watch(key1, key2));
    }

    @Override
    public Single<String> watch(@RedisProtocolSupport.Key final CharSequence key1,
                                @RedisProtocolSupport.Key final CharSequence key2,
                                @RedisProtocolSupport.Key final CharSequence key3) {
        return blockingToSingle(() -> blockingCommander.watch(key1, key2, key3));
    }

    @Override
    public Single<String> watch(@RedisProtocolSupport.Key final Collection<? extends CharSequence> keys) {
        return blockingToSingle(() -> blockingCommander.watch(keys));
    }

    @Override
    public Single<String> xadd(@RedisProtocolSupport.Key final CharSequence key, final CharSequence id,
                               final CharSequence field, final CharSequence value) {
        return blockingToSingle(() -> blockingCommander.xadd(key, id, field, value));
    }

    @Override
    public Single<String> xadd(@RedisProtocolSupport.Key final CharSequence key, final CharSequence id,
                               final CharSequence field1, final CharSequence value1, final CharSequence field2,
                               final CharSequence value2) {
        return blockingToSingle(() -> blockingCommander.xadd(key, id, field1, value1, field2, value2));
    }

    @Override
    public Single<String> xadd(@RedisProtocolSupport.Key final CharSequence key, final CharSequence id,
                               final CharSequence field1, final CharSequence value1, final CharSequence field2,
                               final CharSequence value2, final CharSequence field3, final CharSequence value3) {
        return blockingToSingle(() -> blockingCommander.xadd(key, id, field1, value1, field2, value2, field3, value3));
    }

    @Override
    public Single<String> xadd(@RedisProtocolSupport.Key final CharSequence key, final CharSequence id,
                               final Collection<RedisProtocolSupport.FieldValue> fieldValues) {
        return blockingToSingle(() -> blockingCommander.xadd(key, id, fieldValues));
    }

    @Override
    public Single<Long> xlen(@RedisProtocolSupport.Key final CharSequence key) {
        return blockingToSingle(() -> blockingCommander.xlen(key));
    }

    @Override
    public <T> Single<List<T>> xpending(@RedisProtocolSupport.Key final CharSequence key, final CharSequence group) {
        return blockingToSingle(() -> blockingCommander.xpending(key, group));
    }

    @Override
    public <T> Single<List<T>> xpending(@RedisProtocolSupport.Key final CharSequence key, final CharSequence group,
                                        @Nullable final CharSequence start, @Nullable final CharSequence end,
                                        @Nullable final Long count, @Nullable final CharSequence consumer) {
        return blockingToSingle(() -> blockingCommander.xpending(key, group, start, end, count, consumer));
    }

    @Override
    public <T> Single<List<T>> xrange(@RedisProtocolSupport.Key final CharSequence key, final CharSequence start,
                                      final CharSequence end) {
        return blockingToSingle(() -> blockingCommander.xrange(key, start, end));
    }

    @Override
    public <T> Single<List<T>> xrange(@RedisProtocolSupport.Key final CharSequence key, final CharSequence start,
                                      final CharSequence end, @Nullable final Long count) {
        return blockingToSingle(() -> blockingCommander.xrange(key, start, end, count));
    }

    @Override
    public <T> Single<List<T>> xread(@RedisProtocolSupport.Key final Collection<? extends CharSequence> keys,
                                     final Collection<? extends CharSequence> ids) {
        return blockingToSingle(() -> blockingCommander.xread(keys, ids));
    }

    @Override
    public <T> Single<List<T>> xread(@Nullable final Long count, @Nullable final Long blockMilliseconds,
                                     @RedisProtocolSupport.Key final Collection<? extends CharSequence> keys,
                                     final Collection<? extends CharSequence> ids) {
        return blockingToSingle(() -> blockingCommander.xread(count, blockMilliseconds, keys, ids));
    }

    @Override
    public <T> Single<List<T>> xreadgroup(final RedisProtocolSupport.GroupConsumer groupConsumer,
                                          @RedisProtocolSupport.Key final Collection<? extends CharSequence> keys,
                                          final Collection<? extends CharSequence> ids) {
        return blockingToSingle(() -> blockingCommander.xreadgroup(groupConsumer, keys, ids));
    }

    @Override
    public <T> Single<List<T>> xreadgroup(final RedisProtocolSupport.GroupConsumer groupConsumer,
                                          @Nullable final Long count, @Nullable final Long blockMilliseconds,
                                          @RedisProtocolSupport.Key final Collection<? extends CharSequence> keys,
                                          final Collection<? extends CharSequence> ids) {
        return blockingToSingle(() -> blockingCommander.xreadgroup(groupConsumer, count, blockMilliseconds, keys, ids));
    }

    @Override
    public <T> Single<List<T>> xrevrange(@RedisProtocolSupport.Key final CharSequence key, final CharSequence end,
                                         final CharSequence start) {
        return blockingToSingle(() -> blockingCommander.xrevrange(key, end, start));
    }

    @Override
    public <T> Single<List<T>> xrevrange(@RedisProtocolSupport.Key final CharSequence key, final CharSequence end,
                                         final CharSequence start, @Nullable final Long count) {
        return blockingToSingle(() -> blockingCommander.xrevrange(key, end, start, count));
    }

    @Override
    public Single<Long> zadd(@RedisProtocolSupport.Key final CharSequence key,
                             final Collection<RedisProtocolSupport.ScoreMember> scoreMembers) {
        return blockingToSingle(() -> blockingCommander.zadd(key, scoreMembers));
    }

    @Override
    public Single<Long> zadd(@RedisProtocolSupport.Key final CharSequence key,
                             @Nullable final RedisProtocolSupport.ZaddCondition condition,
                             @Nullable final RedisProtocolSupport.ZaddChange change, final double score,
                             final CharSequence member) {
        return blockingToSingle(() -> blockingCommander.zadd(key, condition, change, score, member));
    }

    @Override
    public Single<Long> zadd(@RedisProtocolSupport.Key final CharSequence key,
                             @Nullable final RedisProtocolSupport.ZaddCondition condition,
                             @Nullable final RedisProtocolSupport.ZaddChange change, final double score1,
                             final CharSequence member1, final double score2, final CharSequence member2) {
        return blockingToSingle(() -> blockingCommander.zadd(key, condition, change, score1, member1, score2, member2));
    }

    @Override
    public Single<Long> zadd(@RedisProtocolSupport.Key final CharSequence key,
                             @Nullable final RedisProtocolSupport.ZaddCondition condition,
                             @Nullable final RedisProtocolSupport.ZaddChange change, final double score1,
                             final CharSequence member1, final double score2, final CharSequence member2,
                             final double score3, final CharSequence member3) {
        return blockingToSingle(() -> blockingCommander.zadd(key, condition, change, score1, member1, score2, member2,
                    score3, member3));
    }

    @Override
    public Single<Long> zadd(@RedisProtocolSupport.Key final CharSequence key,
                             @Nullable final RedisProtocolSupport.ZaddCondition condition,
                             @Nullable final RedisProtocolSupport.ZaddChange change,
                             final Collection<RedisProtocolSupport.ScoreMember> scoreMembers) {
        return blockingToSingle(() -> blockingCommander.zadd(key, condition, change, scoreMembers));
    }

    @Override
    public Single<Double> zaddIncr(@RedisProtocolSupport.Key final CharSequence key,
                                   final Collection<RedisProtocolSupport.ScoreMember> scoreMembers) {
        return blockingToSingle(() -> blockingCommander.zaddIncr(key, scoreMembers));
    }

    @Override
    public Single<Double> zaddIncr(@RedisProtocolSupport.Key final CharSequence key,
                                   @Nullable final RedisProtocolSupport.ZaddCondition condition,
                                   @Nullable final RedisProtocolSupport.ZaddChange change, final double score,
                                   final CharSequence member) {
        return blockingToSingle(() -> blockingCommander.zaddIncr(key, condition, change, score, member));
    }

    @Override
    public Single<Double> zaddIncr(@RedisProtocolSupport.Key final CharSequence key,
                                   @Nullable final RedisProtocolSupport.ZaddCondition condition,
                                   @Nullable final RedisProtocolSupport.ZaddChange change, final double score1,
                                   final CharSequence member1, final double score2, final CharSequence member2) {
        return blockingToSingle(
                    () -> blockingCommander.zaddIncr(key, condition, change, score1, member1, score2, member2));
    }

    @Override
    public Single<Double> zaddIncr(@RedisProtocolSupport.Key final CharSequence key,
                                   @Nullable final RedisProtocolSupport.ZaddCondition condition,
                                   @Nullable final RedisProtocolSupport.ZaddChange change, final double score1,
                                   final CharSequence member1, final double score2, final CharSequence member2,
                                   final double score3, final CharSequence member3) {
        return blockingToSingle(() -> blockingCommander.zaddIncr(key, condition, change, score1, member1, score2,
                    member2, score3, member3));
    }

    @Override
    public Single<Double> zaddIncr(@RedisProtocolSupport.Key final CharSequence key,
                                   @Nullable final RedisProtocolSupport.ZaddCondition condition,
                                   @Nullable final RedisProtocolSupport.ZaddChange change,
                                   final Collection<RedisProtocolSupport.ScoreMember> scoreMembers) {
        return blockingToSingle(() -> blockingCommander.zaddIncr(key, condition, change, scoreMembers));
    }

    @Override
    public Single<Long> zcard(@RedisProtocolSupport.Key final CharSequence key) {
        return blockingToSingle(() -> blockingCommander.zcard(key));
    }

    @Override
    public Single<Long> zcount(@RedisProtocolSupport.Key final CharSequence key, final double min, final double max) {
        return blockingToSingle(() -> blockingCommander.zcount(key, min, max));
    }

    @Override
    public Single<Double> zincrby(@RedisProtocolSupport.Key final CharSequence key, final long increment,
                                  final CharSequence member) {
        return blockingToSingle(() -> blockingCommander.zincrby(key, increment, member));
    }

    @Override
    public Single<Long> zinterstore(@RedisProtocolSupport.Key final CharSequence destination, final long numkeys,
                                    @RedisProtocolSupport.Key final Collection<? extends CharSequence> keys) {
        return blockingToSingle(() -> blockingCommander.zinterstore(destination, numkeys, keys));
    }

    @Override
    public Single<Long> zinterstore(@RedisProtocolSupport.Key final CharSequence destination, final long numkeys,
                                    @RedisProtocolSupport.Key final Collection<? extends CharSequence> keys,
                                    final Collection<Long> weights,
                                    @Nullable final RedisProtocolSupport.ZinterstoreAggregate aggregate) {
        return blockingToSingle(() -> blockingCommander.zinterstore(destination, numkeys, keys, weights, aggregate));
    }

    @Override
    public Single<Long> zlexcount(@RedisProtocolSupport.Key final CharSequence key, final CharSequence min,
                                  final CharSequence max) {
        return blockingToSingle(() -> blockingCommander.zlexcount(key, min, max));
    }

    @Override
    public <T> Single<List<T>> zpopmax(@RedisProtocolSupport.Key final CharSequence key) {
        return blockingToSingle(() -> blockingCommander.zpopmax(key));
    }

    @Override
    public <T> Single<List<T>> zpopmax(@RedisProtocolSupport.Key final CharSequence key, @Nullable final Long count) {
        return blockingToSingle(() -> blockingCommander.zpopmax(key, count));
    }

    @Override
    public <T> Single<List<T>> zpopmin(@RedisProtocolSupport.Key final CharSequence key) {
        return blockingToSingle(() -> blockingCommander.zpopmin(key));
    }

    @Override
    public <T> Single<List<T>> zpopmin(@RedisProtocolSupport.Key final CharSequence key, @Nullable final Long count) {
        return blockingToSingle(() -> blockingCommander.zpopmin(key, count));
    }

    @Override
    public <T> Single<List<T>> zrange(@RedisProtocolSupport.Key final CharSequence key, final long start,
                                      final long stop) {
        return blockingToSingle(() -> blockingCommander.zrange(key, start, stop));
    }

    @Override
    public <T> Single<List<T>> zrange(@RedisProtocolSupport.Key final CharSequence key, final long start,
                                      final long stop,
                                      @Nullable final RedisProtocolSupport.ZrangeWithscores withscores) {
        return blockingToSingle(() -> blockingCommander.zrange(key, start, stop, withscores));
    }

    @Override
    public <T> Single<List<T>> zrangebylex(@RedisProtocolSupport.Key final CharSequence key, final CharSequence min,
                                           final CharSequence max) {
        return blockingToSingle(() -> blockingCommander.zrangebylex(key, min, max));
    }

    @Override
    public <T> Single<List<T>> zrangebylex(@RedisProtocolSupport.Key final CharSequence key, final CharSequence min,
                                           final CharSequence max,
                                           @Nullable final RedisProtocolSupport.OffsetCount offsetCount) {
        return blockingToSingle(() -> blockingCommander.zrangebylex(key, min, max, offsetCount));
    }

    @Override
    public <T> Single<List<T>> zrangebyscore(@RedisProtocolSupport.Key final CharSequence key, final double min,
                                             final double max) {
        return blockingToSingle(() -> blockingCommander.zrangebyscore(key, min, max));
    }

    @Override
    public <T> Single<List<T>> zrangebyscore(@RedisProtocolSupport.Key final CharSequence key, final double min,
                                             final double max,
                                             @Nullable final RedisProtocolSupport.ZrangebyscoreWithscores withscores,
                                             @Nullable final RedisProtocolSupport.OffsetCount offsetCount) {
        return blockingToSingle(() -> blockingCommander.zrangebyscore(key, min, max, withscores, offsetCount));
    }

    @Override
    public Single<Long> zrank(@RedisProtocolSupport.Key final CharSequence key, final CharSequence member) {
        return blockingToSingle(() -> blockingCommander.zrank(key, member));
    }

    @Override
    public Single<Long> zrem(@RedisProtocolSupport.Key final CharSequence key, final CharSequence member) {
        return blockingToSingle(() -> blockingCommander.zrem(key, member));
    }

    @Override
    public Single<Long> zrem(@RedisProtocolSupport.Key final CharSequence key, final CharSequence member1,
                             final CharSequence member2) {
        return blockingToSingle(() -> blockingCommander.zrem(key, member1, member2));
    }

    @Override
    public Single<Long> zrem(@RedisProtocolSupport.Key final CharSequence key, final CharSequence member1,
                             final CharSequence member2, final CharSequence member3) {
        return blockingToSingle(() -> blockingCommander.zrem(key, member1, member2, member3));
    }

    @Override
    public Single<Long> zrem(@RedisProtocolSupport.Key final CharSequence key,
                             final Collection<? extends CharSequence> members) {
        return blockingToSingle(() -> blockingCommander.zrem(key, members));
    }

    @Override
    public Single<Long> zremrangebylex(@RedisProtocolSupport.Key final CharSequence key, final CharSequence min,
                                       final CharSequence max) {
        return blockingToSingle(() -> blockingCommander.zremrangebylex(key, min, max));
    }

    @Override
    public Single<Long> zremrangebyrank(@RedisProtocolSupport.Key final CharSequence key, final long start,
                                        final long stop) {
        return blockingToSingle(() -> blockingCommander.zremrangebyrank(key, start, stop));
    }

    @Override
    public Single<Long> zremrangebyscore(@RedisProtocolSupport.Key final CharSequence key, final double min,
                                         final double max) {
        return blockingToSingle(() -> blockingCommander.zremrangebyscore(key, min, max));
    }

    @Override
    public <T> Single<List<T>> zrevrange(@RedisProtocolSupport.Key final CharSequence key, final long start,
                                         final long stop) {
        return blockingToSingle(() -> blockingCommander.zrevrange(key, start, stop));
    }

    @Override
    public <T> Single<List<T>> zrevrange(@RedisProtocolSupport.Key final CharSequence key, final long start,
                                         final long stop,
                                         @Nullable final RedisProtocolSupport.ZrevrangeWithscores withscores) {
        return blockingToSingle(() -> blockingCommander.zrevrange(key, start, stop, withscores));
    }

    @Override
    public <T> Single<List<T>> zrevrangebylex(@RedisProtocolSupport.Key final CharSequence key, final CharSequence max,
                                              final CharSequence min) {
        return blockingToSingle(() -> blockingCommander.zrevrangebylex(key, max, min));
    }

    @Override
    public <T> Single<List<T>> zrevrangebylex(@RedisProtocolSupport.Key final CharSequence key, final CharSequence max,
                                              final CharSequence min,
                                              @Nullable final RedisProtocolSupport.OffsetCount offsetCount) {
        return blockingToSingle(() -> blockingCommander.zrevrangebylex(key, max, min, offsetCount));
    }

    @Override
    public <T> Single<List<T>> zrevrangebyscore(@RedisProtocolSupport.Key final CharSequence key, final double max,
                                                final double min) {
        return blockingToSingle(() -> blockingCommander.zrevrangebyscore(key, max, min));
    }

    @Override
    public <T> Single<List<T>> zrevrangebyscore(@RedisProtocolSupport.Key final CharSequence key, final double max,
                                                final double min,
                                                @Nullable final RedisProtocolSupport.ZrevrangebyscoreWithscores withscores,
                                                @Nullable final RedisProtocolSupport.OffsetCount offsetCount) {
        return blockingToSingle(() -> blockingCommander.zrevrangebyscore(key, max, min, withscores, offsetCount));
    }

    @Override
    public Single<Long> zrevrank(@RedisProtocolSupport.Key final CharSequence key, final CharSequence member) {
        return blockingToSingle(() -> blockingCommander.zrevrank(key, member));
    }

    @Override
    public <T> Single<List<T>> zscan(@RedisProtocolSupport.Key final CharSequence key, final long cursor) {
        return blockingToSingle(() -> blockingCommander.zscan(key, cursor));
    }

    @Override
    public <T> Single<List<T>> zscan(@RedisProtocolSupport.Key final CharSequence key, final long cursor,
                                     @Nullable final CharSequence matchPattern, @Nullable final Long count) {
        return blockingToSingle(() -> blockingCommander.zscan(key, cursor, matchPattern, count));
    }

    @Override
    public Single<Double> zscore(@RedisProtocolSupport.Key final CharSequence key, final CharSequence member) {
        return blockingToSingle(() -> blockingCommander.zscore(key, member));
    }

    @Override
    public Single<Long> zunionstore(@RedisProtocolSupport.Key final CharSequence destination, final long numkeys,
                                    @RedisProtocolSupport.Key final Collection<? extends CharSequence> keys) {
        return blockingToSingle(() -> blockingCommander.zunionstore(destination, numkeys, keys));
    }

    @Override
    public Single<Long> zunionstore(@RedisProtocolSupport.Key final CharSequence destination, final long numkeys,
                                    @RedisProtocolSupport.Key final Collection<? extends CharSequence> keys,
                                    final Collection<Long> weights,
                                    @Nullable final RedisProtocolSupport.ZunionstoreAggregate aggregate) {
        return blockingToSingle(() -> blockingCommander.zunionstore(destination, numkeys, keys, weights, aggregate));
    }

    BlockingRedisCommander asBlockingCommanderInternal() {
        return blockingCommander;
    }
}

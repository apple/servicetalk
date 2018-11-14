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
final class BlockingPartitionedRedisCommanderToPartitionedRedisCommander extends RedisCommander {

    private final BlockingRedisCommander partitionedRedisClient;

    BlockingPartitionedRedisCommanderToPartitionedRedisCommander(final BlockingRedisCommander partitionedRedisClient) {
        this.partitionedRedisClient = Objects.requireNonNull(partitionedRedisClient);
    }

    @Override
    public Completable closeAsync() {
        return blockingToCompletable(partitionedRedisClient::close);
    }

    @Override
    public Completable closeAsyncGracefully() {
        return closeAsync();
    }

    @Override
    public Single<Long> append(@RedisProtocolSupport.Key final CharSequence key, final CharSequence value) {
        return blockingToSingle(() -> partitionedRedisClient.append(key, value));
    }

    @Override
    public Single<String> auth(final CharSequence password) {
        return blockingToSingle(() -> partitionedRedisClient.auth(password));
    }

    @Override
    public Single<String> bgrewriteaof() {
        return blockingToSingle(() -> partitionedRedisClient.bgrewriteaof());
    }

    @Override
    public Single<String> bgsave() {
        return blockingToSingle(() -> partitionedRedisClient.bgsave());
    }

    @Override
    public Single<Long> bitcount(@RedisProtocolSupport.Key final CharSequence key) {
        return blockingToSingle(() -> partitionedRedisClient.bitcount(key));
    }

    @Override
    public Single<Long> bitcount(@RedisProtocolSupport.Key final CharSequence key, @Nullable final Long start,
                                 @Nullable final Long end) {
        return blockingToSingle(() -> partitionedRedisClient.bitcount(key, start, end));
    }

    @Override
    public Single<List<Long>> bitfield(@RedisProtocolSupport.Key final CharSequence key,
                                       final Collection<RedisProtocolSupport.BitfieldOperation> operations) {
        return blockingToSingle(() -> partitionedRedisClient.bitfield(key, operations));
    }

    @Override
    public Single<Long> bitop(final CharSequence operation, @RedisProtocolSupport.Key final CharSequence destkey,
                              @RedisProtocolSupport.Key final CharSequence key) {
        return blockingToSingle(() -> partitionedRedisClient.bitop(operation, destkey, key));
    }

    @Override
    public Single<Long> bitop(final CharSequence operation, @RedisProtocolSupport.Key final CharSequence destkey,
                              @RedisProtocolSupport.Key final CharSequence key1,
                              @RedisProtocolSupport.Key final CharSequence key2) {
        return blockingToSingle(() -> partitionedRedisClient.bitop(operation, destkey, key1, key2));
    }

    @Override
    public Single<Long> bitop(final CharSequence operation, @RedisProtocolSupport.Key final CharSequence destkey,
                              @RedisProtocolSupport.Key final CharSequence key1,
                              @RedisProtocolSupport.Key final CharSequence key2,
                              @RedisProtocolSupport.Key final CharSequence key3) {
        return blockingToSingle(() -> partitionedRedisClient.bitop(operation, destkey, key1, key2, key3));
    }

    @Override
    public Single<Long> bitop(final CharSequence operation, @RedisProtocolSupport.Key final CharSequence destkey,
                              @RedisProtocolSupport.Key final Collection<? extends CharSequence> keys) {
        return blockingToSingle(() -> partitionedRedisClient.bitop(operation, destkey, keys));
    }

    @Override
    public Single<Long> bitpos(@RedisProtocolSupport.Key final CharSequence key, final long bit) {
        return blockingToSingle(() -> partitionedRedisClient.bitpos(key, bit));
    }

    @Override
    public Single<Long> bitpos(@RedisProtocolSupport.Key final CharSequence key, final long bit,
                               @Nullable final Long start, @Nullable final Long end) {
        return blockingToSingle(() -> partitionedRedisClient.bitpos(key, bit, start, end));
    }

    @Override
    public <T> Single<List<T>> blpop(@RedisProtocolSupport.Key final Collection<? extends CharSequence> keys,
                                     final long timeout) {
        return blockingToSingle(() -> partitionedRedisClient.blpop(keys, timeout));
    }

    @Override
    public <T> Single<List<T>> brpop(@RedisProtocolSupport.Key final Collection<? extends CharSequence> keys,
                                     final long timeout) {
        return blockingToSingle(() -> partitionedRedisClient.brpop(keys, timeout));
    }

    @Override
    public Single<String> brpoplpush(@RedisProtocolSupport.Key final CharSequence source,
                                     @RedisProtocolSupport.Key final CharSequence destination, final long timeout) {
        return blockingToSingle(() -> partitionedRedisClient.brpoplpush(source, destination, timeout));
    }

    @Override
    public <T> Single<List<T>> bzpopmax(@RedisProtocolSupport.Key final Collection<? extends CharSequence> keys,
                                        final long timeout) {
        return blockingToSingle(() -> partitionedRedisClient.bzpopmax(keys, timeout));
    }

    @Override
    public <T> Single<List<T>> bzpopmin(@RedisProtocolSupport.Key final Collection<? extends CharSequence> keys,
                                        final long timeout) {
        return blockingToSingle(() -> partitionedRedisClient.bzpopmin(keys, timeout));
    }

    @Override
    public Single<Long> clientKill(@Nullable final Long id, @Nullable final RedisProtocolSupport.ClientKillType type,
                                   @Nullable final CharSequence addrIpPort, @Nullable final CharSequence skipmeYesNo) {
        return blockingToSingle(() -> partitionedRedisClient.clientKill(id, type, addrIpPort, skipmeYesNo));
    }

    @Override
    public Single<String> clientList() {
        return blockingToSingle(() -> partitionedRedisClient.clientList());
    }

    @Override
    public Single<String> clientGetname() {
        return blockingToSingle(() -> partitionedRedisClient.clientGetname());
    }

    @Override
    public Single<String> clientPause(final long timeout) {
        return blockingToSingle(() -> partitionedRedisClient.clientPause(timeout));
    }

    @Override
    public Single<String> clientReply(final RedisProtocolSupport.ClientReplyReplyMode replyMode) {
        return blockingToSingle(() -> partitionedRedisClient.clientReply(replyMode));
    }

    @Override
    public Single<String> clientSetname(final CharSequence connectionName) {
        return blockingToSingle(() -> partitionedRedisClient.clientSetname(connectionName));
    }

    @Override
    public Single<String> clusterAddslots(final long slot) {
        return blockingToSingle(() -> partitionedRedisClient.clusterAddslots(slot));
    }

    @Override
    public Single<String> clusterAddslots(final long slot1, final long slot2) {
        return blockingToSingle(() -> partitionedRedisClient.clusterAddslots(slot1, slot2));
    }

    @Override
    public Single<String> clusterAddslots(final long slot1, final long slot2, final long slot3) {
        return blockingToSingle(() -> partitionedRedisClient.clusterAddslots(slot1, slot2, slot3));
    }

    @Override
    public Single<String> clusterAddslots(final Collection<Long> slots) {
        return blockingToSingle(() -> partitionedRedisClient.clusterAddslots(slots));
    }

    @Override
    public Single<Long> clusterCountFailureReports(final CharSequence nodeId) {
        return blockingToSingle(() -> partitionedRedisClient.clusterCountFailureReports(nodeId));
    }

    @Override
    public Single<Long> clusterCountkeysinslot(final long slot) {
        return blockingToSingle(() -> partitionedRedisClient.clusterCountkeysinslot(slot));
    }

    @Override
    public Single<String> clusterDelslots(final long slot) {
        return blockingToSingle(() -> partitionedRedisClient.clusterDelslots(slot));
    }

    @Override
    public Single<String> clusterDelslots(final long slot1, final long slot2) {
        return blockingToSingle(() -> partitionedRedisClient.clusterDelslots(slot1, slot2));
    }

    @Override
    public Single<String> clusterDelslots(final long slot1, final long slot2, final long slot3) {
        return blockingToSingle(() -> partitionedRedisClient.clusterDelslots(slot1, slot2, slot3));
    }

    @Override
    public Single<String> clusterDelslots(final Collection<Long> slots) {
        return blockingToSingle(() -> partitionedRedisClient.clusterDelslots(slots));
    }

    @Override
    public Single<String> clusterFailover() {
        return blockingToSingle(() -> partitionedRedisClient.clusterFailover());
    }

    @Override
    public Single<String> clusterFailover(@Nullable final RedisProtocolSupport.ClusterFailoverOptions options) {
        return blockingToSingle(() -> partitionedRedisClient.clusterFailover(options));
    }

    @Override
    public Single<String> clusterForget(final CharSequence nodeId) {
        return blockingToSingle(() -> partitionedRedisClient.clusterForget(nodeId));
    }

    @Override
    public <T> Single<List<T>> clusterGetkeysinslot(final long slot, final long count) {
        return blockingToSingle(() -> partitionedRedisClient.clusterGetkeysinslot(slot, count));
    }

    @Override
    public Single<String> clusterInfo() {
        return blockingToSingle(() -> partitionedRedisClient.clusterInfo());
    }

    @Override
    public Single<Long> clusterKeyslot(final CharSequence key) {
        return blockingToSingle(() -> partitionedRedisClient.clusterKeyslot(key));
    }

    @Override
    public Single<String> clusterMeet(final CharSequence ip, final long port) {
        return blockingToSingle(() -> partitionedRedisClient.clusterMeet(ip, port));
    }

    @Override
    public Single<String> clusterNodes() {
        return blockingToSingle(() -> partitionedRedisClient.clusterNodes());
    }

    @Override
    public Single<String> clusterReplicate(final CharSequence nodeId) {
        return blockingToSingle(() -> partitionedRedisClient.clusterReplicate(nodeId));
    }

    @Override
    public Single<String> clusterReset() {
        return blockingToSingle(() -> partitionedRedisClient.clusterReset());
    }

    @Override
    public Single<String> clusterReset(@Nullable final RedisProtocolSupport.ClusterResetResetType resetType) {
        return blockingToSingle(() -> partitionedRedisClient.clusterReset(resetType));
    }

    @Override
    public Single<String> clusterSaveconfig() {
        return blockingToSingle(() -> partitionedRedisClient.clusterSaveconfig());
    }

    @Override
    public Single<String> clusterSetConfigEpoch(final long configEpoch) {
        return blockingToSingle(() -> partitionedRedisClient.clusterSetConfigEpoch(configEpoch));
    }

    @Override
    public Single<String> clusterSetslot(final long slot,
                                         final RedisProtocolSupport.ClusterSetslotSubcommand subcommand) {
        return blockingToSingle(() -> partitionedRedisClient.clusterSetslot(slot, subcommand));
    }

    @Override
    public Single<String> clusterSetslot(final long slot,
                                         final RedisProtocolSupport.ClusterSetslotSubcommand subcommand,
                                         @Nullable final CharSequence nodeId) {
        return blockingToSingle(() -> partitionedRedisClient.clusterSetslot(slot, subcommand, nodeId));
    }

    @Override
    public Single<String> clusterSlaves(final CharSequence nodeId) {
        return blockingToSingle(() -> partitionedRedisClient.clusterSlaves(nodeId));
    }

    @Override
    public <T> Single<List<T>> clusterSlots() {
        return blockingToSingle(() -> partitionedRedisClient.clusterSlots());
    }

    @Override
    public <T> Single<List<T>> command() {
        return blockingToSingle(() -> partitionedRedisClient.command());
    }

    @Override
    public Single<Long> commandCount() {
        return blockingToSingle(() -> partitionedRedisClient.commandCount());
    }

    @Override
    public <T> Single<List<T>> commandGetkeys() {
        return blockingToSingle(() -> partitionedRedisClient.commandGetkeys());
    }

    @Override
    public <T> Single<List<T>> commandInfo(final CharSequence commandName) {
        return blockingToSingle(() -> partitionedRedisClient.commandInfo(commandName));
    }

    @Override
    public <T> Single<List<T>> commandInfo(final CharSequence commandName1, final CharSequence commandName2) {
        return blockingToSingle(() -> partitionedRedisClient.commandInfo(commandName1, commandName2));
    }

    @Override
    public <T> Single<List<T>> commandInfo(final CharSequence commandName1, final CharSequence commandName2,
                                           final CharSequence commandName3) {
        return blockingToSingle(() -> partitionedRedisClient.commandInfo(commandName1, commandName2, commandName3));
    }

    @Override
    public <T> Single<List<T>> commandInfo(final Collection<? extends CharSequence> commandNames) {
        return blockingToSingle(() -> partitionedRedisClient.commandInfo(commandNames));
    }

    @Override
    public <T> Single<List<T>> configGet(final CharSequence parameter) {
        return blockingToSingle(() -> partitionedRedisClient.configGet(parameter));
    }

    @Override
    public Single<String> configRewrite() {
        return blockingToSingle(() -> partitionedRedisClient.configRewrite());
    }

    @Override
    public Single<String> configSet(final CharSequence parameter, final CharSequence value) {
        return blockingToSingle(() -> partitionedRedisClient.configSet(parameter, value));
    }

    @Override
    public Single<String> configResetstat() {
        return blockingToSingle(() -> partitionedRedisClient.configResetstat());
    }

    @Override
    public Single<Long> dbsize() {
        return blockingToSingle(() -> partitionedRedisClient.dbsize());
    }

    @Override
    public Single<String> debugObject(@RedisProtocolSupport.Key final CharSequence key) {
        return blockingToSingle(() -> partitionedRedisClient.debugObject(key));
    }

    @Override
    public Single<String> debugSegfault() {
        return blockingToSingle(() -> partitionedRedisClient.debugSegfault());
    }

    @Override
    public Single<Long> decr(@RedisProtocolSupport.Key final CharSequence key) {
        return blockingToSingle(() -> partitionedRedisClient.decr(key));
    }

    @Override
    public Single<Long> decrby(@RedisProtocolSupport.Key final CharSequence key, final long decrement) {
        return blockingToSingle(() -> partitionedRedisClient.decrby(key, decrement));
    }

    @Override
    public Single<Long> del(@RedisProtocolSupport.Key final CharSequence key) {
        return blockingToSingle(() -> partitionedRedisClient.del(key));
    }

    @Override
    public Single<Long> del(@RedisProtocolSupport.Key final CharSequence key1,
                            @RedisProtocolSupport.Key final CharSequence key2) {
        return blockingToSingle(() -> partitionedRedisClient.del(key1, key2));
    }

    @Override
    public Single<Long> del(@RedisProtocolSupport.Key final CharSequence key1,
                            @RedisProtocolSupport.Key final CharSequence key2,
                            @RedisProtocolSupport.Key final CharSequence key3) {
        return blockingToSingle(() -> partitionedRedisClient.del(key1, key2, key3));
    }

    @Override
    public Single<Long> del(@RedisProtocolSupport.Key final Collection<? extends CharSequence> keys) {
        return blockingToSingle(() -> partitionedRedisClient.del(keys));
    }

    @Override
    public Single<String> dump(@RedisProtocolSupport.Key final CharSequence key) {
        return blockingToSingle(() -> partitionedRedisClient.dump(key));
    }

    @Override
    public Single<String> echo(final CharSequence message) {
        return blockingToSingle(() -> partitionedRedisClient.echo(message));
    }

    @Override
    public Single<String> eval(final CharSequence script, final long numkeys,
                               @RedisProtocolSupport.Key final Collection<? extends CharSequence> keys,
                               final Collection<? extends CharSequence> args) {
        return blockingToSingle(() -> partitionedRedisClient.eval(script, numkeys, keys, args));
    }

    @Override
    public <T> Single<List<T>> evalList(final CharSequence script, final long numkeys,
                                        @RedisProtocolSupport.Key final Collection<? extends CharSequence> keys,
                                        final Collection<? extends CharSequence> args) {
        return blockingToSingle(() -> partitionedRedisClient.evalList(script, numkeys, keys, args));
    }

    @Override
    public Single<Long> evalLong(final CharSequence script, final long numkeys,
                                 @RedisProtocolSupport.Key final Collection<? extends CharSequence> keys,
                                 final Collection<? extends CharSequence> args) {
        return blockingToSingle(() -> partitionedRedisClient.evalLong(script, numkeys, keys, args));
    }

    @Override
    public Single<String> evalsha(final CharSequence sha1, final long numkeys,
                                  @RedisProtocolSupport.Key final Collection<? extends CharSequence> keys,
                                  final Collection<? extends CharSequence> args) {
        return blockingToSingle(() -> partitionedRedisClient.evalsha(sha1, numkeys, keys, args));
    }

    @Override
    public <T> Single<List<T>> evalshaList(final CharSequence sha1, final long numkeys,
                                           @RedisProtocolSupport.Key final Collection<? extends CharSequence> keys,
                                           final Collection<? extends CharSequence> args) {
        return blockingToSingle(() -> partitionedRedisClient.evalshaList(sha1, numkeys, keys, args));
    }

    @Override
    public Single<Long> evalshaLong(final CharSequence sha1, final long numkeys,
                                    @RedisProtocolSupport.Key final Collection<? extends CharSequence> keys,
                                    final Collection<? extends CharSequence> args) {
        return blockingToSingle(() -> partitionedRedisClient.evalshaLong(sha1, numkeys, keys, args));
    }

    @Override
    public Single<Long> exists(@RedisProtocolSupport.Key final CharSequence key) {
        return blockingToSingle(() -> partitionedRedisClient.exists(key));
    }

    @Override
    public Single<Long> exists(@RedisProtocolSupport.Key final CharSequence key1,
                               @RedisProtocolSupport.Key final CharSequence key2) {
        return blockingToSingle(() -> partitionedRedisClient.exists(key1, key2));
    }

    @Override
    public Single<Long> exists(@RedisProtocolSupport.Key final CharSequence key1,
                               @RedisProtocolSupport.Key final CharSequence key2,
                               @RedisProtocolSupport.Key final CharSequence key3) {
        return blockingToSingle(() -> partitionedRedisClient.exists(key1, key2, key3));
    }

    @Override
    public Single<Long> exists(@RedisProtocolSupport.Key final Collection<? extends CharSequence> keys) {
        return blockingToSingle(() -> partitionedRedisClient.exists(keys));
    }

    @Override
    public Single<Long> expire(@RedisProtocolSupport.Key final CharSequence key, final long seconds) {
        return blockingToSingle(() -> partitionedRedisClient.expire(key, seconds));
    }

    @Override
    public Single<Long> expireat(@RedisProtocolSupport.Key final CharSequence key, final long timestamp) {
        return blockingToSingle(() -> partitionedRedisClient.expireat(key, timestamp));
    }

    @Override
    public Single<String> flushall() {
        return blockingToSingle(() -> partitionedRedisClient.flushall());
    }

    @Override
    public Single<String> flushall(@Nullable final RedisProtocolSupport.FlushallAsync async) {
        return blockingToSingle(() -> partitionedRedisClient.flushall(async));
    }

    @Override
    public Single<String> flushdb() {
        return blockingToSingle(() -> partitionedRedisClient.flushdb());
    }

    @Override
    public Single<String> flushdb(@Nullable final RedisProtocolSupport.FlushdbAsync async) {
        return blockingToSingle(() -> partitionedRedisClient.flushdb(async));
    }

    @Override
    public Single<Long> geoadd(@RedisProtocolSupport.Key final CharSequence key, final double longitude,
                               final double latitude, final CharSequence member) {
        return blockingToSingle(() -> partitionedRedisClient.geoadd(key, longitude, latitude, member));
    }

    @Override
    public Single<Long> geoadd(@RedisProtocolSupport.Key final CharSequence key, final double longitude1,
                               final double latitude1, final CharSequence member1, final double longitude2,
                               final double latitude2, final CharSequence member2) {
        return blockingToSingle(() -> partitionedRedisClient.geoadd(key, longitude1, latitude1, member1, longitude2,
                    latitude2, member2));
    }

    @Override
    public Single<Long> geoadd(@RedisProtocolSupport.Key final CharSequence key, final double longitude1,
                               final double latitude1, final CharSequence member1, final double longitude2,
                               final double latitude2, final CharSequence member2, final double longitude3,
                               final double latitude3, final CharSequence member3) {
        return blockingToSingle(() -> partitionedRedisClient.geoadd(key, longitude1, latitude1, member1, longitude2,
                    latitude2, member2, longitude3, latitude3, member3));
    }

    @Override
    public Single<Long> geoadd(@RedisProtocolSupport.Key final CharSequence key,
                               final Collection<RedisProtocolSupport.LongitudeLatitudeMember> longitudeLatitudeMembers) {
        return blockingToSingle(() -> partitionedRedisClient.geoadd(key, longitudeLatitudeMembers));
    }

    @Override
    public Single<Double> geodist(@RedisProtocolSupport.Key final CharSequence key, final CharSequence member1,
                                  final CharSequence member2) {
        return blockingToSingle(() -> partitionedRedisClient.geodist(key, member1, member2));
    }

    @Override
    public Single<Double> geodist(@RedisProtocolSupport.Key final CharSequence key, final CharSequence member1,
                                  final CharSequence member2, @Nullable final CharSequence unit) {
        return blockingToSingle(() -> partitionedRedisClient.geodist(key, member1, member2, unit));
    }

    @Override
    public <T> Single<List<T>> geohash(@RedisProtocolSupport.Key final CharSequence key, final CharSequence member) {
        return blockingToSingle(() -> partitionedRedisClient.geohash(key, member));
    }

    @Override
    public <T> Single<List<T>> geohash(@RedisProtocolSupport.Key final CharSequence key, final CharSequence member1,
                                       final CharSequence member2) {
        return blockingToSingle(() -> partitionedRedisClient.geohash(key, member1, member2));
    }

    @Override
    public <T> Single<List<T>> geohash(@RedisProtocolSupport.Key final CharSequence key, final CharSequence member1,
                                       final CharSequence member2, final CharSequence member3) {
        return blockingToSingle(() -> partitionedRedisClient.geohash(key, member1, member2, member3));
    }

    @Override
    public <T> Single<List<T>> geohash(@RedisProtocolSupport.Key final CharSequence key,
                                       final Collection<? extends CharSequence> members) {
        return blockingToSingle(() -> partitionedRedisClient.geohash(key, members));
    }

    @Override
    public <T> Single<List<T>> geopos(@RedisProtocolSupport.Key final CharSequence key, final CharSequence member) {
        return blockingToSingle(() -> partitionedRedisClient.geopos(key, member));
    }

    @Override
    public <T> Single<List<T>> geopos(@RedisProtocolSupport.Key final CharSequence key, final CharSequence member1,
                                      final CharSequence member2) {
        return blockingToSingle(() -> partitionedRedisClient.geopos(key, member1, member2));
    }

    @Override
    public <T> Single<List<T>> geopos(@RedisProtocolSupport.Key final CharSequence key, final CharSequence member1,
                                      final CharSequence member2, final CharSequence member3) {
        return blockingToSingle(() -> partitionedRedisClient.geopos(key, member1, member2, member3));
    }

    @Override
    public <T> Single<List<T>> geopos(@RedisProtocolSupport.Key final CharSequence key,
                                      final Collection<? extends CharSequence> members) {
        return blockingToSingle(() -> partitionedRedisClient.geopos(key, members));
    }

    @Override
    public <T> Single<List<T>> georadius(@RedisProtocolSupport.Key final CharSequence key, final double longitude,
                                         final double latitude, final double radius,
                                         final RedisProtocolSupport.GeoradiusUnit unit) {
        return blockingToSingle(() -> partitionedRedisClient.georadius(key, longitude, latitude, radius, unit));
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
        return blockingToSingle(() -> partitionedRedisClient.georadius(key, longitude, latitude, radius, unit,
                    withcoord, withdist, withhash, count, order, storeKey, storedistKey));
    }

    @Override
    public <T> Single<List<T>> georadiusbymember(@RedisProtocolSupport.Key final CharSequence key,
                                                 final CharSequence member, final double radius,
                                                 final RedisProtocolSupport.GeoradiusbymemberUnit unit) {
        return blockingToSingle(() -> partitionedRedisClient.georadiusbymember(key, member, radius, unit));
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
        return blockingToSingle(() -> partitionedRedisClient.georadiusbymember(key, member, radius, unit, withcoord,
                    withdist, withhash, count, order, storeKey, storedistKey));
    }

    @Override
    public Single<String> get(@RedisProtocolSupport.Key final CharSequence key) {
        return blockingToSingle(() -> partitionedRedisClient.get(key));
    }

    @Override
    public Single<Long> getbit(@RedisProtocolSupport.Key final CharSequence key, final long offset) {
        return blockingToSingle(() -> partitionedRedisClient.getbit(key, offset));
    }

    @Override
    public Single<String> getrange(@RedisProtocolSupport.Key final CharSequence key, final long start, final long end) {
        return blockingToSingle(() -> partitionedRedisClient.getrange(key, start, end));
    }

    @Override
    public Single<String> getset(@RedisProtocolSupport.Key final CharSequence key, final CharSequence value) {
        return blockingToSingle(() -> partitionedRedisClient.getset(key, value));
    }

    @Override
    public Single<Long> hdel(@RedisProtocolSupport.Key final CharSequence key, final CharSequence field) {
        return blockingToSingle(() -> partitionedRedisClient.hdel(key, field));
    }

    @Override
    public Single<Long> hdel(@RedisProtocolSupport.Key final CharSequence key, final CharSequence field1,
                             final CharSequence field2) {
        return blockingToSingle(() -> partitionedRedisClient.hdel(key, field1, field2));
    }

    @Override
    public Single<Long> hdel(@RedisProtocolSupport.Key final CharSequence key, final CharSequence field1,
                             final CharSequence field2, final CharSequence field3) {
        return blockingToSingle(() -> partitionedRedisClient.hdel(key, field1, field2, field3));
    }

    @Override
    public Single<Long> hdel(@RedisProtocolSupport.Key final CharSequence key,
                             final Collection<? extends CharSequence> fields) {
        return blockingToSingle(() -> partitionedRedisClient.hdel(key, fields));
    }

    @Override
    public Single<Long> hexists(@RedisProtocolSupport.Key final CharSequence key, final CharSequence field) {
        return blockingToSingle(() -> partitionedRedisClient.hexists(key, field));
    }

    @Override
    public Single<String> hget(@RedisProtocolSupport.Key final CharSequence key, final CharSequence field) {
        return blockingToSingle(() -> partitionedRedisClient.hget(key, field));
    }

    @Override
    public <T> Single<List<T>> hgetall(@RedisProtocolSupport.Key final CharSequence key) {
        return blockingToSingle(() -> partitionedRedisClient.hgetall(key));
    }

    @Override
    public Single<Long> hincrby(@RedisProtocolSupport.Key final CharSequence key, final CharSequence field,
                                final long increment) {
        return blockingToSingle(() -> partitionedRedisClient.hincrby(key, field, increment));
    }

    @Override
    public Single<Double> hincrbyfloat(@RedisProtocolSupport.Key final CharSequence key, final CharSequence field,
                                       final double increment) {
        return blockingToSingle(() -> partitionedRedisClient.hincrbyfloat(key, field, increment));
    }

    @Override
    public <T> Single<List<T>> hkeys(@RedisProtocolSupport.Key final CharSequence key) {
        return blockingToSingle(() -> partitionedRedisClient.hkeys(key));
    }

    @Override
    public Single<Long> hlen(@RedisProtocolSupport.Key final CharSequence key) {
        return blockingToSingle(() -> partitionedRedisClient.hlen(key));
    }

    @Override
    public Single<List<String>> hmget(@RedisProtocolSupport.Key final CharSequence key, final CharSequence field) {
        return blockingToSingle(() -> partitionedRedisClient.hmget(key, field));
    }

    @Override
    public Single<List<String>> hmget(@RedisProtocolSupport.Key final CharSequence key, final CharSequence field1,
                                      final CharSequence field2) {
        return blockingToSingle(() -> partitionedRedisClient.hmget(key, field1, field2));
    }

    @Override
    public Single<List<String>> hmget(@RedisProtocolSupport.Key final CharSequence key, final CharSequence field1,
                                      final CharSequence field2, final CharSequence field3) {
        return blockingToSingle(() -> partitionedRedisClient.hmget(key, field1, field2, field3));
    }

    @Override
    public Single<List<String>> hmget(@RedisProtocolSupport.Key final CharSequence key,
                                      final Collection<? extends CharSequence> fields) {
        return blockingToSingle(() -> partitionedRedisClient.hmget(key, fields));
    }

    @Override
    public Single<String> hmset(@RedisProtocolSupport.Key final CharSequence key, final CharSequence field,
                                final CharSequence value) {
        return blockingToSingle(() -> partitionedRedisClient.hmset(key, field, value));
    }

    @Override
    public Single<String> hmset(@RedisProtocolSupport.Key final CharSequence key, final CharSequence field1,
                                final CharSequence value1, final CharSequence field2, final CharSequence value2) {
        return blockingToSingle(() -> partitionedRedisClient.hmset(key, field1, value1, field2, value2));
    }

    @Override
    public Single<String> hmset(@RedisProtocolSupport.Key final CharSequence key, final CharSequence field1,
                                final CharSequence value1, final CharSequence field2, final CharSequence value2,
                                final CharSequence field3, final CharSequence value3) {
        return blockingToSingle(
                    () -> partitionedRedisClient.hmset(key, field1, value1, field2, value2, field3, value3));
    }

    @Override
    public Single<String> hmset(@RedisProtocolSupport.Key final CharSequence key,
                                final Collection<RedisProtocolSupport.FieldValue> fieldValues) {
        return blockingToSingle(() -> partitionedRedisClient.hmset(key, fieldValues));
    }

    @Override
    public <T> Single<List<T>> hscan(@RedisProtocolSupport.Key final CharSequence key, final long cursor) {
        return blockingToSingle(() -> partitionedRedisClient.hscan(key, cursor));
    }

    @Override
    public <T> Single<List<T>> hscan(@RedisProtocolSupport.Key final CharSequence key, final long cursor,
                                     @Nullable final CharSequence matchPattern, @Nullable final Long count) {
        return blockingToSingle(() -> partitionedRedisClient.hscan(key, cursor, matchPattern, count));
    }

    @Override
    public Single<Long> hset(@RedisProtocolSupport.Key final CharSequence key, final CharSequence field,
                             final CharSequence value) {
        return blockingToSingle(() -> partitionedRedisClient.hset(key, field, value));
    }

    @Override
    public Single<Long> hsetnx(@RedisProtocolSupport.Key final CharSequence key, final CharSequence field,
                               final CharSequence value) {
        return blockingToSingle(() -> partitionedRedisClient.hsetnx(key, field, value));
    }

    @Override
    public Single<Long> hstrlen(@RedisProtocolSupport.Key final CharSequence key, final CharSequence field) {
        return blockingToSingle(() -> partitionedRedisClient.hstrlen(key, field));
    }

    @Override
    public <T> Single<List<T>> hvals(@RedisProtocolSupport.Key final CharSequence key) {
        return blockingToSingle(() -> partitionedRedisClient.hvals(key));
    }

    @Override
    public Single<Long> incr(@RedisProtocolSupport.Key final CharSequence key) {
        return blockingToSingle(() -> partitionedRedisClient.incr(key));
    }

    @Override
    public Single<Long> incrby(@RedisProtocolSupport.Key final CharSequence key, final long increment) {
        return blockingToSingle(() -> partitionedRedisClient.incrby(key, increment));
    }

    @Override
    public Single<Double> incrbyfloat(@RedisProtocolSupport.Key final CharSequence key, final double increment) {
        return blockingToSingle(() -> partitionedRedisClient.incrbyfloat(key, increment));
    }

    @Override
    public Single<String> info() {
        return blockingToSingle(() -> partitionedRedisClient.info());
    }

    @Override
    public Single<String> info(@Nullable final CharSequence section) {
        return blockingToSingle(() -> partitionedRedisClient.info(section));
    }

    @Override
    public <T> Single<List<T>> keys(final CharSequence pattern) {
        return blockingToSingle(() -> partitionedRedisClient.keys(pattern));
    }

    @Override
    public Single<Long> lastsave() {
        return blockingToSingle(() -> partitionedRedisClient.lastsave());
    }

    @Override
    public Single<String> lindex(@RedisProtocolSupport.Key final CharSequence key, final long index) {
        return blockingToSingle(() -> partitionedRedisClient.lindex(key, index));
    }

    @Override
    public Single<Long> linsert(@RedisProtocolSupport.Key final CharSequence key,
                                final RedisProtocolSupport.LinsertWhere where, final CharSequence pivot,
                                final CharSequence value) {
        return blockingToSingle(() -> partitionedRedisClient.linsert(key, where, pivot, value));
    }

    @Override
    public Single<Long> llen(@RedisProtocolSupport.Key final CharSequence key) {
        return blockingToSingle(() -> partitionedRedisClient.llen(key));
    }

    @Override
    public Single<String> lpop(@RedisProtocolSupport.Key final CharSequence key) {
        return blockingToSingle(() -> partitionedRedisClient.lpop(key));
    }

    @Override
    public Single<Long> lpush(@RedisProtocolSupport.Key final CharSequence key, final CharSequence value) {
        return blockingToSingle(() -> partitionedRedisClient.lpush(key, value));
    }

    @Override
    public Single<Long> lpush(@RedisProtocolSupport.Key final CharSequence key, final CharSequence value1,
                              final CharSequence value2) {
        return blockingToSingle(() -> partitionedRedisClient.lpush(key, value1, value2));
    }

    @Override
    public Single<Long> lpush(@RedisProtocolSupport.Key final CharSequence key, final CharSequence value1,
                              final CharSequence value2, final CharSequence value3) {
        return blockingToSingle(() -> partitionedRedisClient.lpush(key, value1, value2, value3));
    }

    @Override
    public Single<Long> lpush(@RedisProtocolSupport.Key final CharSequence key,
                              final Collection<? extends CharSequence> values) {
        return blockingToSingle(() -> partitionedRedisClient.lpush(key, values));
    }

    @Override
    public Single<Long> lpushx(@RedisProtocolSupport.Key final CharSequence key, final CharSequence value) {
        return blockingToSingle(() -> partitionedRedisClient.lpushx(key, value));
    }

    @Override
    public <T> Single<List<T>> lrange(@RedisProtocolSupport.Key final CharSequence key, final long start,
                                      final long stop) {
        return blockingToSingle(() -> partitionedRedisClient.lrange(key, start, stop));
    }

    @Override
    public Single<Long> lrem(@RedisProtocolSupport.Key final CharSequence key, final long count,
                             final CharSequence value) {
        return blockingToSingle(() -> partitionedRedisClient.lrem(key, count, value));
    }

    @Override
    public Single<String> lset(@RedisProtocolSupport.Key final CharSequence key, final long index,
                               final CharSequence value) {
        return blockingToSingle(() -> partitionedRedisClient.lset(key, index, value));
    }

    @Override
    public Single<String> ltrim(@RedisProtocolSupport.Key final CharSequence key, final long start, final long stop) {
        return blockingToSingle(() -> partitionedRedisClient.ltrim(key, start, stop));
    }

    @Override
    public Single<String> memoryDoctor() {
        return blockingToSingle(() -> partitionedRedisClient.memoryDoctor());
    }

    @Override
    public <T> Single<List<T>> memoryHelp() {
        return blockingToSingle(() -> partitionedRedisClient.memoryHelp());
    }

    @Override
    public Single<String> memoryMallocStats() {
        return blockingToSingle(() -> partitionedRedisClient.memoryMallocStats());
    }

    @Override
    public Single<String> memoryPurge() {
        return blockingToSingle(() -> partitionedRedisClient.memoryPurge());
    }

    @Override
    public <T> Single<List<T>> memoryStats() {
        return blockingToSingle(() -> partitionedRedisClient.memoryStats());
    }

    @Override
    public Single<Long> memoryUsage(@RedisProtocolSupport.Key final CharSequence key) {
        return blockingToSingle(() -> partitionedRedisClient.memoryUsage(key));
    }

    @Override
    public Single<Long> memoryUsage(@RedisProtocolSupport.Key final CharSequence key,
                                    @Nullable final Long samplesCount) {
        return blockingToSingle(() -> partitionedRedisClient.memoryUsage(key, samplesCount));
    }

    @Override
    public Single<List<String>> mget(@RedisProtocolSupport.Key final CharSequence key) {
        return blockingToSingle(() -> partitionedRedisClient.mget(key));
    }

    @Override
    public Single<List<String>> mget(@RedisProtocolSupport.Key final CharSequence key1,
                                     @RedisProtocolSupport.Key final CharSequence key2) {
        return blockingToSingle(() -> partitionedRedisClient.mget(key1, key2));
    }

    @Override
    public Single<List<String>> mget(@RedisProtocolSupport.Key final CharSequence key1,
                                     @RedisProtocolSupport.Key final CharSequence key2,
                                     @RedisProtocolSupport.Key final CharSequence key3) {
        return blockingToSingle(() -> partitionedRedisClient.mget(key1, key2, key3));
    }

    @Override
    public Single<List<String>> mget(@RedisProtocolSupport.Key final Collection<? extends CharSequence> keys) {
        return blockingToSingle(() -> partitionedRedisClient.mget(keys));
    }

    @Override
    public Publisher<String> monitor() {
        return blockingToPublisher(() -> partitionedRedisClient.monitor());
    }

    @Override
    public Single<Long> move(@RedisProtocolSupport.Key final CharSequence key, final long db) {
        return blockingToSingle(() -> partitionedRedisClient.move(key, db));
    }

    @Override
    public Single<String> mset(@RedisProtocolSupport.Key final CharSequence key, final CharSequence value) {
        return blockingToSingle(() -> partitionedRedisClient.mset(key, value));
    }

    @Override
    public Single<String> mset(@RedisProtocolSupport.Key final CharSequence key1, final CharSequence value1,
                               @RedisProtocolSupport.Key final CharSequence key2, final CharSequence value2) {
        return blockingToSingle(() -> partitionedRedisClient.mset(key1, value1, key2, value2));
    }

    @Override
    public Single<String> mset(@RedisProtocolSupport.Key final CharSequence key1, final CharSequence value1,
                               @RedisProtocolSupport.Key final CharSequence key2, final CharSequence value2,
                               @RedisProtocolSupport.Key final CharSequence key3, final CharSequence value3) {
        return blockingToSingle(() -> partitionedRedisClient.mset(key1, value1, key2, value2, key3, value3));
    }

    @Override
    public Single<String> mset(final Collection<RedisProtocolSupport.KeyValue> keyValues) {
        return blockingToSingle(() -> partitionedRedisClient.mset(keyValues));
    }

    @Override
    public Single<Long> msetnx(@RedisProtocolSupport.Key final CharSequence key, final CharSequence value) {
        return blockingToSingle(() -> partitionedRedisClient.msetnx(key, value));
    }

    @Override
    public Single<Long> msetnx(@RedisProtocolSupport.Key final CharSequence key1, final CharSequence value1,
                               @RedisProtocolSupport.Key final CharSequence key2, final CharSequence value2) {
        return blockingToSingle(() -> partitionedRedisClient.msetnx(key1, value1, key2, value2));
    }

    @Override
    public Single<Long> msetnx(@RedisProtocolSupport.Key final CharSequence key1, final CharSequence value1,
                               @RedisProtocolSupport.Key final CharSequence key2, final CharSequence value2,
                               @RedisProtocolSupport.Key final CharSequence key3, final CharSequence value3) {
        return blockingToSingle(() -> partitionedRedisClient.msetnx(key1, value1, key2, value2, key3, value3));
    }

    @Override
    public Single<Long> msetnx(final Collection<RedisProtocolSupport.KeyValue> keyValues) {
        return blockingToSingle(() -> partitionedRedisClient.msetnx(keyValues));
    }

    @Override
    public Single<TransactedRedisCommander> multi() {
        return blockingToSingle(() -> new BlockingTransactedRedisCommanderToTransactedRedisCommander(
                    partitionedRedisClient.multi()));
    }

    @Override
    public Single<String> objectEncoding(@RedisProtocolSupport.Key final CharSequence key) {
        return blockingToSingle(() -> partitionedRedisClient.objectEncoding(key));
    }

    @Override
    public Single<Long> objectFreq(@RedisProtocolSupport.Key final CharSequence key) {
        return blockingToSingle(() -> partitionedRedisClient.objectFreq(key));
    }

    @Override
    public Single<List<String>> objectHelp() {
        return blockingToSingle(() -> partitionedRedisClient.objectHelp());
    }

    @Override
    public Single<Long> objectIdletime(@RedisProtocolSupport.Key final CharSequence key) {
        return blockingToSingle(() -> partitionedRedisClient.objectIdletime(key));
    }

    @Override
    public Single<Long> objectRefcount(@RedisProtocolSupport.Key final CharSequence key) {
        return blockingToSingle(() -> partitionedRedisClient.objectRefcount(key));
    }

    @Override
    public Single<Long> persist(@RedisProtocolSupport.Key final CharSequence key) {
        return blockingToSingle(() -> partitionedRedisClient.persist(key));
    }

    @Override
    public Single<Long> pexpire(@RedisProtocolSupport.Key final CharSequence key, final long milliseconds) {
        return blockingToSingle(() -> partitionedRedisClient.pexpire(key, milliseconds));
    }

    @Override
    public Single<Long> pexpireat(@RedisProtocolSupport.Key final CharSequence key, final long millisecondsTimestamp) {
        return blockingToSingle(() -> partitionedRedisClient.pexpireat(key, millisecondsTimestamp));
    }

    @Override
    public Single<Long> pfadd(@RedisProtocolSupport.Key final CharSequence key, final CharSequence element) {
        return blockingToSingle(() -> partitionedRedisClient.pfadd(key, element));
    }

    @Override
    public Single<Long> pfadd(@RedisProtocolSupport.Key final CharSequence key, final CharSequence element1,
                              final CharSequence element2) {
        return blockingToSingle(() -> partitionedRedisClient.pfadd(key, element1, element2));
    }

    @Override
    public Single<Long> pfadd(@RedisProtocolSupport.Key final CharSequence key, final CharSequence element1,
                              final CharSequence element2, final CharSequence element3) {
        return blockingToSingle(() -> partitionedRedisClient.pfadd(key, element1, element2, element3));
    }

    @Override
    public Single<Long> pfadd(@RedisProtocolSupport.Key final CharSequence key,
                              final Collection<? extends CharSequence> elements) {
        return blockingToSingle(() -> partitionedRedisClient.pfadd(key, elements));
    }

    @Override
    public Single<Long> pfcount(@RedisProtocolSupport.Key final CharSequence key) {
        return blockingToSingle(() -> partitionedRedisClient.pfcount(key));
    }

    @Override
    public Single<Long> pfcount(@RedisProtocolSupport.Key final CharSequence key1,
                                @RedisProtocolSupport.Key final CharSequence key2) {
        return blockingToSingle(() -> partitionedRedisClient.pfcount(key1, key2));
    }

    @Override
    public Single<Long> pfcount(@RedisProtocolSupport.Key final CharSequence key1,
                                @RedisProtocolSupport.Key final CharSequence key2,
                                @RedisProtocolSupport.Key final CharSequence key3) {
        return blockingToSingle(() -> partitionedRedisClient.pfcount(key1, key2, key3));
    }

    @Override
    public Single<Long> pfcount(@RedisProtocolSupport.Key final Collection<? extends CharSequence> keys) {
        return blockingToSingle(() -> partitionedRedisClient.pfcount(keys));
    }

    @Override
    public Single<String> pfmerge(@RedisProtocolSupport.Key final CharSequence destkey,
                                  @RedisProtocolSupport.Key final CharSequence sourcekey) {
        return blockingToSingle(() -> partitionedRedisClient.pfmerge(destkey, sourcekey));
    }

    @Override
    public Single<String> pfmerge(@RedisProtocolSupport.Key final CharSequence destkey,
                                  @RedisProtocolSupport.Key final CharSequence sourcekey1,
                                  @RedisProtocolSupport.Key final CharSequence sourcekey2) {
        return blockingToSingle(() -> partitionedRedisClient.pfmerge(destkey, sourcekey1, sourcekey2));
    }

    @Override
    public Single<String> pfmerge(@RedisProtocolSupport.Key final CharSequence destkey,
                                  @RedisProtocolSupport.Key final CharSequence sourcekey1,
                                  @RedisProtocolSupport.Key final CharSequence sourcekey2,
                                  @RedisProtocolSupport.Key final CharSequence sourcekey3) {
        return blockingToSingle(() -> partitionedRedisClient.pfmerge(destkey, sourcekey1, sourcekey2, sourcekey3));
    }

    @Override
    public Single<String> pfmerge(@RedisProtocolSupport.Key final CharSequence destkey,
                                  @RedisProtocolSupport.Key final Collection<? extends CharSequence> sourcekeys) {
        return blockingToSingle(() -> partitionedRedisClient.pfmerge(destkey, sourcekeys));
    }

    @Override
    public Single<String> ping() {
        return blockingToSingle(() -> partitionedRedisClient.ping());
    }

    @Override
    public Single<String> ping(final CharSequence message) {
        return blockingToSingle(() -> partitionedRedisClient.ping(message));
    }

    @Override
    public Single<String> psetex(@RedisProtocolSupport.Key final CharSequence key, final long milliseconds,
                                 final CharSequence value) {
        return blockingToSingle(() -> partitionedRedisClient.psetex(key, milliseconds, value));
    }

    @Override
    public Single<PubSubRedisConnection> psubscribe(final CharSequence pattern) {
        return blockingToSingle(() -> new BlockingPubSubRedisConnectionToPubSubRedisConnection(
                    partitionedRedisClient.psubscribe(pattern)));
    }

    @Override
    public Single<Long> pttl(@RedisProtocolSupport.Key final CharSequence key) {
        return blockingToSingle(() -> partitionedRedisClient.pttl(key));
    }

    @Override
    public Single<Long> publish(final CharSequence channel, final CharSequence message) {
        return blockingToSingle(() -> partitionedRedisClient.publish(channel, message));
    }

    @Override
    public Single<List<String>> pubsubChannels() {
        return blockingToSingle(() -> partitionedRedisClient.pubsubChannels());
    }

    @Override
    public Single<List<String>> pubsubChannels(@Nullable final CharSequence pattern) {
        return blockingToSingle(() -> partitionedRedisClient.pubsubChannels(pattern));
    }

    @Override
    public Single<List<String>> pubsubChannels(@Nullable final CharSequence pattern1,
                                               @Nullable final CharSequence pattern2) {
        return blockingToSingle(() -> partitionedRedisClient.pubsubChannels(pattern1, pattern2));
    }

    @Override
    public Single<List<String>> pubsubChannels(@Nullable final CharSequence pattern1,
                                               @Nullable final CharSequence pattern2,
                                               @Nullable final CharSequence pattern3) {
        return blockingToSingle(() -> partitionedRedisClient.pubsubChannels(pattern1, pattern2, pattern3));
    }

    @Override
    public Single<List<String>> pubsubChannels(final Collection<? extends CharSequence> patterns) {
        return blockingToSingle(() -> partitionedRedisClient.pubsubChannels(patterns));
    }

    @Override
    public <T> Single<List<T>> pubsubNumsub() {
        return blockingToSingle(() -> partitionedRedisClient.pubsubNumsub());
    }

    @Override
    public <T> Single<List<T>> pubsubNumsub(@Nullable final CharSequence channel) {
        return blockingToSingle(() -> partitionedRedisClient.pubsubNumsub(channel));
    }

    @Override
    public <T> Single<List<T>> pubsubNumsub(@Nullable final CharSequence channel1,
                                            @Nullable final CharSequence channel2) {
        return blockingToSingle(() -> partitionedRedisClient.pubsubNumsub(channel1, channel2));
    }

    @Override
    public <T> Single<List<T>> pubsubNumsub(@Nullable final CharSequence channel1,
                                            @Nullable final CharSequence channel2,
                                            @Nullable final CharSequence channel3) {
        return blockingToSingle(() -> partitionedRedisClient.pubsubNumsub(channel1, channel2, channel3));
    }

    @Override
    public <T> Single<List<T>> pubsubNumsub(final Collection<? extends CharSequence> channels) {
        return blockingToSingle(() -> partitionedRedisClient.pubsubNumsub(channels));
    }

    @Override
    public Single<Long> pubsubNumpat() {
        return blockingToSingle(() -> partitionedRedisClient.pubsubNumpat());
    }

    @Override
    public Single<String> randomkey() {
        return blockingToSingle(() -> partitionedRedisClient.randomkey());
    }

    @Override
    public Single<String> readonly() {
        return blockingToSingle(() -> partitionedRedisClient.readonly());
    }

    @Override
    public Single<String> readwrite() {
        return blockingToSingle(() -> partitionedRedisClient.readwrite());
    }

    @Override
    public Single<String> rename(@RedisProtocolSupport.Key final CharSequence key,
                                 @RedisProtocolSupport.Key final CharSequence newkey) {
        return blockingToSingle(() -> partitionedRedisClient.rename(key, newkey));
    }

    @Override
    public Single<Long> renamenx(@RedisProtocolSupport.Key final CharSequence key,
                                 @RedisProtocolSupport.Key final CharSequence newkey) {
        return blockingToSingle(() -> partitionedRedisClient.renamenx(key, newkey));
    }

    @Override
    public Single<String> restore(@RedisProtocolSupport.Key final CharSequence key, final long ttl,
                                  final CharSequence serializedValue) {
        return blockingToSingle(() -> partitionedRedisClient.restore(key, ttl, serializedValue));
    }

    @Override
    public Single<String> restore(@RedisProtocolSupport.Key final CharSequence key, final long ttl,
                                  final CharSequence serializedValue,
                                  @Nullable final RedisProtocolSupport.RestoreReplace replace) {
        return blockingToSingle(() -> partitionedRedisClient.restore(key, ttl, serializedValue, replace));
    }

    @Override
    public <T> Single<List<T>> role() {
        return blockingToSingle(() -> partitionedRedisClient.role());
    }

    @Override
    public Single<String> rpop(@RedisProtocolSupport.Key final CharSequence key) {
        return blockingToSingle(() -> partitionedRedisClient.rpop(key));
    }

    @Override
    public Single<String> rpoplpush(@RedisProtocolSupport.Key final CharSequence source,
                                    @RedisProtocolSupport.Key final CharSequence destination) {
        return blockingToSingle(() -> partitionedRedisClient.rpoplpush(source, destination));
    }

    @Override
    public Single<Long> rpush(@RedisProtocolSupport.Key final CharSequence key, final CharSequence value) {
        return blockingToSingle(() -> partitionedRedisClient.rpush(key, value));
    }

    @Override
    public Single<Long> rpush(@RedisProtocolSupport.Key final CharSequence key, final CharSequence value1,
                              final CharSequence value2) {
        return blockingToSingle(() -> partitionedRedisClient.rpush(key, value1, value2));
    }

    @Override
    public Single<Long> rpush(@RedisProtocolSupport.Key final CharSequence key, final CharSequence value1,
                              final CharSequence value2, final CharSequence value3) {
        return blockingToSingle(() -> partitionedRedisClient.rpush(key, value1, value2, value3));
    }

    @Override
    public Single<Long> rpush(@RedisProtocolSupport.Key final CharSequence key,
                              final Collection<? extends CharSequence> values) {
        return blockingToSingle(() -> partitionedRedisClient.rpush(key, values));
    }

    @Override
    public Single<Long> rpushx(@RedisProtocolSupport.Key final CharSequence key, final CharSequence value) {
        return blockingToSingle(() -> partitionedRedisClient.rpushx(key, value));
    }

    @Override
    public Single<Long> sadd(@RedisProtocolSupport.Key final CharSequence key, final CharSequence member) {
        return blockingToSingle(() -> partitionedRedisClient.sadd(key, member));
    }

    @Override
    public Single<Long> sadd(@RedisProtocolSupport.Key final CharSequence key, final CharSequence member1,
                             final CharSequence member2) {
        return blockingToSingle(() -> partitionedRedisClient.sadd(key, member1, member2));
    }

    @Override
    public Single<Long> sadd(@RedisProtocolSupport.Key final CharSequence key, final CharSequence member1,
                             final CharSequence member2, final CharSequence member3) {
        return blockingToSingle(() -> partitionedRedisClient.sadd(key, member1, member2, member3));
    }

    @Override
    public Single<Long> sadd(@RedisProtocolSupport.Key final CharSequence key,
                             final Collection<? extends CharSequence> members) {
        return blockingToSingle(() -> partitionedRedisClient.sadd(key, members));
    }

    @Override
    public Single<String> save() {
        return blockingToSingle(() -> partitionedRedisClient.save());
    }

    @Override
    public <T> Single<List<T>> scan(final long cursor) {
        return blockingToSingle(() -> partitionedRedisClient.scan(cursor));
    }

    @Override
    public <T> Single<List<T>> scan(final long cursor, @Nullable final CharSequence matchPattern,
                                    @Nullable final Long count) {
        return blockingToSingle(() -> partitionedRedisClient.scan(cursor, matchPattern, count));
    }

    @Override
    public Single<Long> scard(@RedisProtocolSupport.Key final CharSequence key) {
        return blockingToSingle(() -> partitionedRedisClient.scard(key));
    }

    @Override
    public Single<String> scriptDebug(final RedisProtocolSupport.ScriptDebugMode mode) {
        return blockingToSingle(() -> partitionedRedisClient.scriptDebug(mode));
    }

    @Override
    public <T> Single<List<T>> scriptExists(final CharSequence sha1) {
        return blockingToSingle(() -> partitionedRedisClient.scriptExists(sha1));
    }

    @Override
    public <T> Single<List<T>> scriptExists(final CharSequence sha11, final CharSequence sha12) {
        return blockingToSingle(() -> partitionedRedisClient.scriptExists(sha11, sha12));
    }

    @Override
    public <T> Single<List<T>> scriptExists(final CharSequence sha11, final CharSequence sha12,
                                            final CharSequence sha13) {
        return blockingToSingle(() -> partitionedRedisClient.scriptExists(sha11, sha12, sha13));
    }

    @Override
    public <T> Single<List<T>> scriptExists(final Collection<? extends CharSequence> sha1s) {
        return blockingToSingle(() -> partitionedRedisClient.scriptExists(sha1s));
    }

    @Override
    public Single<String> scriptFlush() {
        return blockingToSingle(() -> partitionedRedisClient.scriptFlush());
    }

    @Override
    public Single<String> scriptKill() {
        return blockingToSingle(() -> partitionedRedisClient.scriptKill());
    }

    @Override
    public Single<String> scriptLoad(final CharSequence script) {
        return blockingToSingle(() -> partitionedRedisClient.scriptLoad(script));
    }

    @Override
    public <T> Single<List<T>> sdiff(@RedisProtocolSupport.Key final CharSequence firstkey) {
        return blockingToSingle(() -> partitionedRedisClient.sdiff(firstkey));
    }

    @Override
    public <T> Single<List<T>> sdiff(@RedisProtocolSupport.Key final CharSequence firstkey,
                                     @Nullable @RedisProtocolSupport.Key final CharSequence otherkey) {
        return blockingToSingle(() -> partitionedRedisClient.sdiff(firstkey, otherkey));
    }

    @Override
    public <T> Single<List<T>> sdiff(@RedisProtocolSupport.Key final CharSequence firstkey,
                                     @Nullable @RedisProtocolSupport.Key final CharSequence otherkey1,
                                     @Nullable @RedisProtocolSupport.Key final CharSequence otherkey2) {
        return blockingToSingle(() -> partitionedRedisClient.sdiff(firstkey, otherkey1, otherkey2));
    }

    @Override
    public <T> Single<List<T>> sdiff(@RedisProtocolSupport.Key final CharSequence firstkey,
                                     @Nullable @RedisProtocolSupport.Key final CharSequence otherkey1,
                                     @Nullable @RedisProtocolSupport.Key final CharSequence otherkey2,
                                     @Nullable @RedisProtocolSupport.Key final CharSequence otherkey3) {
        return blockingToSingle(() -> partitionedRedisClient.sdiff(firstkey, otherkey1, otherkey2, otherkey3));
    }

    @Override
    public <T> Single<List<T>> sdiff(@RedisProtocolSupport.Key final CharSequence firstkey,
                                     @RedisProtocolSupport.Key final Collection<? extends CharSequence> otherkeys) {
        return blockingToSingle(() -> partitionedRedisClient.sdiff(firstkey, otherkeys));
    }

    @Override
    public Single<Long> sdiffstore(@RedisProtocolSupport.Key final CharSequence destination,
                                   @RedisProtocolSupport.Key final CharSequence firstkey) {
        return blockingToSingle(() -> partitionedRedisClient.sdiffstore(destination, firstkey));
    }

    @Override
    public Single<Long> sdiffstore(@RedisProtocolSupport.Key final CharSequence destination,
                                   @RedisProtocolSupport.Key final CharSequence firstkey,
                                   @Nullable @RedisProtocolSupport.Key final CharSequence otherkey) {
        return blockingToSingle(() -> partitionedRedisClient.sdiffstore(destination, firstkey, otherkey));
    }

    @Override
    public Single<Long> sdiffstore(@RedisProtocolSupport.Key final CharSequence destination,
                                   @RedisProtocolSupport.Key final CharSequence firstkey,
                                   @Nullable @RedisProtocolSupport.Key final CharSequence otherkey1,
                                   @Nullable @RedisProtocolSupport.Key final CharSequence otherkey2) {
        return blockingToSingle(() -> partitionedRedisClient.sdiffstore(destination, firstkey, otherkey1, otherkey2));
    }

    @Override
    public Single<Long> sdiffstore(@RedisProtocolSupport.Key final CharSequence destination,
                                   @RedisProtocolSupport.Key final CharSequence firstkey,
                                   @Nullable @RedisProtocolSupport.Key final CharSequence otherkey1,
                                   @Nullable @RedisProtocolSupport.Key final CharSequence otherkey2,
                                   @Nullable @RedisProtocolSupport.Key final CharSequence otherkey3) {
        return blockingToSingle(
                    () -> partitionedRedisClient.sdiffstore(destination, firstkey, otherkey1, otherkey2, otherkey3));
    }

    @Override
    public Single<Long> sdiffstore(@RedisProtocolSupport.Key final CharSequence destination,
                                   @RedisProtocolSupport.Key final CharSequence firstkey,
                                   @RedisProtocolSupport.Key final Collection<? extends CharSequence> otherkeys) {
        return blockingToSingle(() -> partitionedRedisClient.sdiffstore(destination, firstkey, otherkeys));
    }

    @Override
    public Single<String> select(final long index) {
        return blockingToSingle(() -> partitionedRedisClient.select(index));
    }

    @Override
    public Single<String> set(@RedisProtocolSupport.Key final CharSequence key, final CharSequence value) {
        return blockingToSingle(() -> partitionedRedisClient.set(key, value));
    }

    @Override
    public Single<String> set(@RedisProtocolSupport.Key final CharSequence key, final CharSequence value,
                              @Nullable final RedisProtocolSupport.ExpireDuration expireDuration,
                              @Nullable final RedisProtocolSupport.SetCondition condition) {
        return blockingToSingle(() -> partitionedRedisClient.set(key, value, expireDuration, condition));
    }

    @Override
    public Single<Long> setbit(@RedisProtocolSupport.Key final CharSequence key, final long offset,
                               final CharSequence value) {
        return blockingToSingle(() -> partitionedRedisClient.setbit(key, offset, value));
    }

    @Override
    public Single<String> setex(@RedisProtocolSupport.Key final CharSequence key, final long seconds,
                                final CharSequence value) {
        return blockingToSingle(() -> partitionedRedisClient.setex(key, seconds, value));
    }

    @Override
    public Single<Long> setnx(@RedisProtocolSupport.Key final CharSequence key, final CharSequence value) {
        return blockingToSingle(() -> partitionedRedisClient.setnx(key, value));
    }

    @Override
    public Single<Long> setrange(@RedisProtocolSupport.Key final CharSequence key, final long offset,
                                 final CharSequence value) {
        return blockingToSingle(() -> partitionedRedisClient.setrange(key, offset, value));
    }

    @Override
    public Single<String> shutdown() {
        return blockingToSingle(() -> partitionedRedisClient.shutdown());
    }

    @Override
    public Single<String> shutdown(@Nullable final RedisProtocolSupport.ShutdownSaveMode saveMode) {
        return blockingToSingle(() -> partitionedRedisClient.shutdown(saveMode));
    }

    @Override
    public <T> Single<List<T>> sinter(@RedisProtocolSupport.Key final CharSequence key) {
        return blockingToSingle(() -> partitionedRedisClient.sinter(key));
    }

    @Override
    public <T> Single<List<T>> sinter(@RedisProtocolSupport.Key final CharSequence key1,
                                      @RedisProtocolSupport.Key final CharSequence key2) {
        return blockingToSingle(() -> partitionedRedisClient.sinter(key1, key2));
    }

    @Override
    public <T> Single<List<T>> sinter(@RedisProtocolSupport.Key final CharSequence key1,
                                      @RedisProtocolSupport.Key final CharSequence key2,
                                      @RedisProtocolSupport.Key final CharSequence key3) {
        return blockingToSingle(() -> partitionedRedisClient.sinter(key1, key2, key3));
    }

    @Override
    public <T> Single<List<T>> sinter(@RedisProtocolSupport.Key final Collection<? extends CharSequence> keys) {
        return blockingToSingle(() -> partitionedRedisClient.sinter(keys));
    }

    @Override
    public Single<Long> sinterstore(@RedisProtocolSupport.Key final CharSequence destination,
                                    @RedisProtocolSupport.Key final CharSequence key) {
        return blockingToSingle(() -> partitionedRedisClient.sinterstore(destination, key));
    }

    @Override
    public Single<Long> sinterstore(@RedisProtocolSupport.Key final CharSequence destination,
                                    @RedisProtocolSupport.Key final CharSequence key1,
                                    @RedisProtocolSupport.Key final CharSequence key2) {
        return blockingToSingle(() -> partitionedRedisClient.sinterstore(destination, key1, key2));
    }

    @Override
    public Single<Long> sinterstore(@RedisProtocolSupport.Key final CharSequence destination,
                                    @RedisProtocolSupport.Key final CharSequence key1,
                                    @RedisProtocolSupport.Key final CharSequence key2,
                                    @RedisProtocolSupport.Key final CharSequence key3) {
        return blockingToSingle(() -> partitionedRedisClient.sinterstore(destination, key1, key2, key3));
    }

    @Override
    public Single<Long> sinterstore(@RedisProtocolSupport.Key final CharSequence destination,
                                    @RedisProtocolSupport.Key final Collection<? extends CharSequence> keys) {
        return blockingToSingle(() -> partitionedRedisClient.sinterstore(destination, keys));
    }

    @Override
    public Single<Long> sismember(@RedisProtocolSupport.Key final CharSequence key, final CharSequence member) {
        return blockingToSingle(() -> partitionedRedisClient.sismember(key, member));
    }

    @Override
    public Single<String> slaveof(final CharSequence host, final CharSequence port) {
        return blockingToSingle(() -> partitionedRedisClient.slaveof(host, port));
    }

    @Override
    public <T> Single<List<T>> slowlog(final CharSequence subcommand) {
        return blockingToSingle(() -> partitionedRedisClient.slowlog(subcommand));
    }

    @Override
    public <T> Single<List<T>> slowlog(final CharSequence subcommand, @Nullable final CharSequence argument) {
        return blockingToSingle(() -> partitionedRedisClient.slowlog(subcommand, argument));
    }

    @Override
    public <T> Single<List<T>> smembers(@RedisProtocolSupport.Key final CharSequence key) {
        return blockingToSingle(() -> partitionedRedisClient.smembers(key));
    }

    @Override
    public Single<Long> smove(@RedisProtocolSupport.Key final CharSequence source,
                              @RedisProtocolSupport.Key final CharSequence destination, final CharSequence member) {
        return blockingToSingle(() -> partitionedRedisClient.smove(source, destination, member));
    }

    @Override
    public <T> Single<List<T>> sort(@RedisProtocolSupport.Key final CharSequence key) {
        return blockingToSingle(() -> partitionedRedisClient.sort(key));
    }

    @Override
    public <T> Single<List<T>> sort(@RedisProtocolSupport.Key final CharSequence key,
                                    @Nullable final CharSequence byPattern,
                                    @Nullable final RedisProtocolSupport.OffsetCount offsetCount,
                                    final Collection<? extends CharSequence> getPatterns,
                                    @Nullable final RedisProtocolSupport.SortOrder order,
                                    @Nullable final RedisProtocolSupport.SortSorting sorting) {
        return blockingToSingle(
                    () -> partitionedRedisClient.sort(key, byPattern, offsetCount, getPatterns, order, sorting));
    }

    @Override
    public Single<Long> sort(@RedisProtocolSupport.Key final CharSequence key,
                             @RedisProtocolSupport.Key final CharSequence storeDestination) {
        return blockingToSingle(() -> partitionedRedisClient.sort(key, storeDestination));
    }

    @Override
    public Single<Long> sort(@RedisProtocolSupport.Key final CharSequence key,
                             @RedisProtocolSupport.Key final CharSequence storeDestination,
                             @Nullable final CharSequence byPattern,
                             @Nullable final RedisProtocolSupport.OffsetCount offsetCount,
                             final Collection<? extends CharSequence> getPatterns,
                             @Nullable final RedisProtocolSupport.SortOrder order,
                             @Nullable final RedisProtocolSupport.SortSorting sorting) {
        return blockingToSingle(() -> partitionedRedisClient.sort(key, storeDestination, byPattern, offsetCount,
                    getPatterns, order, sorting));
    }

    @Override
    public Single<String> spop(@RedisProtocolSupport.Key final CharSequence key) {
        return blockingToSingle(() -> partitionedRedisClient.spop(key));
    }

    @Override
    public Single<String> spop(@RedisProtocolSupport.Key final CharSequence key, @Nullable final Long count) {
        return blockingToSingle(() -> partitionedRedisClient.spop(key, count));
    }

    @Override
    public Single<String> srandmember(@RedisProtocolSupport.Key final CharSequence key) {
        return blockingToSingle(() -> partitionedRedisClient.srandmember(key));
    }

    @Override
    public Single<List<String>> srandmember(@RedisProtocolSupport.Key final CharSequence key, final long count) {
        return blockingToSingle(() -> partitionedRedisClient.srandmember(key, count));
    }

    @Override
    public Single<Long> srem(@RedisProtocolSupport.Key final CharSequence key, final CharSequence member) {
        return blockingToSingle(() -> partitionedRedisClient.srem(key, member));
    }

    @Override
    public Single<Long> srem(@RedisProtocolSupport.Key final CharSequence key, final CharSequence member1,
                             final CharSequence member2) {
        return blockingToSingle(() -> partitionedRedisClient.srem(key, member1, member2));
    }

    @Override
    public Single<Long> srem(@RedisProtocolSupport.Key final CharSequence key, final CharSequence member1,
                             final CharSequence member2, final CharSequence member3) {
        return blockingToSingle(() -> partitionedRedisClient.srem(key, member1, member2, member3));
    }

    @Override
    public Single<Long> srem(@RedisProtocolSupport.Key final CharSequence key,
                             final Collection<? extends CharSequence> members) {
        return blockingToSingle(() -> partitionedRedisClient.srem(key, members));
    }

    @Override
    public <T> Single<List<T>> sscan(@RedisProtocolSupport.Key final CharSequence key, final long cursor) {
        return blockingToSingle(() -> partitionedRedisClient.sscan(key, cursor));
    }

    @Override
    public <T> Single<List<T>> sscan(@RedisProtocolSupport.Key final CharSequence key, final long cursor,
                                     @Nullable final CharSequence matchPattern, @Nullable final Long count) {
        return blockingToSingle(() -> partitionedRedisClient.sscan(key, cursor, matchPattern, count));
    }

    @Override
    public Single<Long> strlen(@RedisProtocolSupport.Key final CharSequence key) {
        return blockingToSingle(() -> partitionedRedisClient.strlen(key));
    }

    @Override
    public Single<PubSubRedisConnection> subscribe(final CharSequence channel) {
        return blockingToSingle(() -> new BlockingPubSubRedisConnectionToPubSubRedisConnection(
                    partitionedRedisClient.subscribe(channel)));
    }

    @Override
    public <T> Single<List<T>> sunion(@RedisProtocolSupport.Key final CharSequence key) {
        return blockingToSingle(() -> partitionedRedisClient.sunion(key));
    }

    @Override
    public <T> Single<List<T>> sunion(@RedisProtocolSupport.Key final CharSequence key1,
                                      @RedisProtocolSupport.Key final CharSequence key2) {
        return blockingToSingle(() -> partitionedRedisClient.sunion(key1, key2));
    }

    @Override
    public <T> Single<List<T>> sunion(@RedisProtocolSupport.Key final CharSequence key1,
                                      @RedisProtocolSupport.Key final CharSequence key2,
                                      @RedisProtocolSupport.Key final CharSequence key3) {
        return blockingToSingle(() -> partitionedRedisClient.sunion(key1, key2, key3));
    }

    @Override
    public <T> Single<List<T>> sunion(@RedisProtocolSupport.Key final Collection<? extends CharSequence> keys) {
        return blockingToSingle(() -> partitionedRedisClient.sunion(keys));
    }

    @Override
    public Single<Long> sunionstore(@RedisProtocolSupport.Key final CharSequence destination,
                                    @RedisProtocolSupport.Key final CharSequence key) {
        return blockingToSingle(() -> partitionedRedisClient.sunionstore(destination, key));
    }

    @Override
    public Single<Long> sunionstore(@RedisProtocolSupport.Key final CharSequence destination,
                                    @RedisProtocolSupport.Key final CharSequence key1,
                                    @RedisProtocolSupport.Key final CharSequence key2) {
        return blockingToSingle(() -> partitionedRedisClient.sunionstore(destination, key1, key2));
    }

    @Override
    public Single<Long> sunionstore(@RedisProtocolSupport.Key final CharSequence destination,
                                    @RedisProtocolSupport.Key final CharSequence key1,
                                    @RedisProtocolSupport.Key final CharSequence key2,
                                    @RedisProtocolSupport.Key final CharSequence key3) {
        return blockingToSingle(() -> partitionedRedisClient.sunionstore(destination, key1, key2, key3));
    }

    @Override
    public Single<Long> sunionstore(@RedisProtocolSupport.Key final CharSequence destination,
                                    @RedisProtocolSupport.Key final Collection<? extends CharSequence> keys) {
        return blockingToSingle(() -> partitionedRedisClient.sunionstore(destination, keys));
    }

    @Override
    public Single<String> swapdb(final long index, final long index1) {
        return blockingToSingle(() -> partitionedRedisClient.swapdb(index, index1));
    }

    @Override
    public <T> Single<List<T>> time() {
        return blockingToSingle(() -> partitionedRedisClient.time());
    }

    @Override
    public Single<Long> touch(@RedisProtocolSupport.Key final CharSequence key) {
        return blockingToSingle(() -> partitionedRedisClient.touch(key));
    }

    @Override
    public Single<Long> touch(@RedisProtocolSupport.Key final CharSequence key1,
                              @RedisProtocolSupport.Key final CharSequence key2) {
        return blockingToSingle(() -> partitionedRedisClient.touch(key1, key2));
    }

    @Override
    public Single<Long> touch(@RedisProtocolSupport.Key final CharSequence key1,
                              @RedisProtocolSupport.Key final CharSequence key2,
                              @RedisProtocolSupport.Key final CharSequence key3) {
        return blockingToSingle(() -> partitionedRedisClient.touch(key1, key2, key3));
    }

    @Override
    public Single<Long> touch(@RedisProtocolSupport.Key final Collection<? extends CharSequence> keys) {
        return blockingToSingle(() -> partitionedRedisClient.touch(keys));
    }

    @Override
    public Single<Long> ttl(@RedisProtocolSupport.Key final CharSequence key) {
        return blockingToSingle(() -> partitionedRedisClient.ttl(key));
    }

    @Override
    public Single<String> type(@RedisProtocolSupport.Key final CharSequence key) {
        return blockingToSingle(() -> partitionedRedisClient.type(key));
    }

    @Override
    public Single<Long> unlink(@RedisProtocolSupport.Key final CharSequence key) {
        return blockingToSingle(() -> partitionedRedisClient.unlink(key));
    }

    @Override
    public Single<Long> unlink(@RedisProtocolSupport.Key final CharSequence key1,
                               @RedisProtocolSupport.Key final CharSequence key2) {
        return blockingToSingle(() -> partitionedRedisClient.unlink(key1, key2));
    }

    @Override
    public Single<Long> unlink(@RedisProtocolSupport.Key final CharSequence key1,
                               @RedisProtocolSupport.Key final CharSequence key2,
                               @RedisProtocolSupport.Key final CharSequence key3) {
        return blockingToSingle(() -> partitionedRedisClient.unlink(key1, key2, key3));
    }

    @Override
    public Single<Long> unlink(@RedisProtocolSupport.Key final Collection<? extends CharSequence> keys) {
        return blockingToSingle(() -> partitionedRedisClient.unlink(keys));
    }

    @Override
    public Single<String> unwatch() {
        return blockingToSingle(() -> partitionedRedisClient.unwatch());
    }

    @Override
    public Single<Long> wait(final long numslaves, final long timeout) {
        return blockingToSingle(() -> partitionedRedisClient.wait(numslaves, timeout));
    }

    @Override
    public Single<String> watch(@RedisProtocolSupport.Key final CharSequence key) {
        return blockingToSingle(() -> partitionedRedisClient.watch(key));
    }

    @Override
    public Single<String> watch(@RedisProtocolSupport.Key final CharSequence key1,
                                @RedisProtocolSupport.Key final CharSequence key2) {
        return blockingToSingle(() -> partitionedRedisClient.watch(key1, key2));
    }

    @Override
    public Single<String> watch(@RedisProtocolSupport.Key final CharSequence key1,
                                @RedisProtocolSupport.Key final CharSequence key2,
                                @RedisProtocolSupport.Key final CharSequence key3) {
        return blockingToSingle(() -> partitionedRedisClient.watch(key1, key2, key3));
    }

    @Override
    public Single<String> watch(@RedisProtocolSupport.Key final Collection<? extends CharSequence> keys) {
        return blockingToSingle(() -> partitionedRedisClient.watch(keys));
    }

    @Override
    public Single<String> xadd(@RedisProtocolSupport.Key final CharSequence key, final CharSequence id,
                               final CharSequence field, final CharSequence value) {
        return blockingToSingle(() -> partitionedRedisClient.xadd(key, id, field, value));
    }

    @Override
    public Single<String> xadd(@RedisProtocolSupport.Key final CharSequence key, final CharSequence id,
                               final CharSequence field1, final CharSequence value1, final CharSequence field2,
                               final CharSequence value2) {
        return blockingToSingle(() -> partitionedRedisClient.xadd(key, id, field1, value1, field2, value2));
    }

    @Override
    public Single<String> xadd(@RedisProtocolSupport.Key final CharSequence key, final CharSequence id,
                               final CharSequence field1, final CharSequence value1, final CharSequence field2,
                               final CharSequence value2, final CharSequence field3, final CharSequence value3) {
        return blockingToSingle(
                    () -> partitionedRedisClient.xadd(key, id, field1, value1, field2, value2, field3, value3));
    }

    @Override
    public Single<String> xadd(@RedisProtocolSupport.Key final CharSequence key, final CharSequence id,
                               final Collection<RedisProtocolSupport.FieldValue> fieldValues) {
        return blockingToSingle(() -> partitionedRedisClient.xadd(key, id, fieldValues));
    }

    @Override
    public Single<Long> xlen(@RedisProtocolSupport.Key final CharSequence key) {
        return blockingToSingle(() -> partitionedRedisClient.xlen(key));
    }

    @Override
    public <T> Single<List<T>> xpending(@RedisProtocolSupport.Key final CharSequence key, final CharSequence group) {
        return blockingToSingle(() -> partitionedRedisClient.xpending(key, group));
    }

    @Override
    public <T> Single<List<T>> xpending(@RedisProtocolSupport.Key final CharSequence key, final CharSequence group,
                                        @Nullable final CharSequence start, @Nullable final CharSequence end,
                                        @Nullable final Long count, @Nullable final CharSequence consumer) {
        return blockingToSingle(() -> partitionedRedisClient.xpending(key, group, start, end, count, consumer));
    }

    @Override
    public <T> Single<List<T>> xrange(@RedisProtocolSupport.Key final CharSequence key, final CharSequence start,
                                      final CharSequence end) {
        return blockingToSingle(() -> partitionedRedisClient.xrange(key, start, end));
    }

    @Override
    public <T> Single<List<T>> xrange(@RedisProtocolSupport.Key final CharSequence key, final CharSequence start,
                                      final CharSequence end, @Nullable final Long count) {
        return blockingToSingle(() -> partitionedRedisClient.xrange(key, start, end, count));
    }

    @Override
    public <T> Single<List<T>> xread(@RedisProtocolSupport.Key final Collection<? extends CharSequence> keys,
                                     final Collection<? extends CharSequence> ids) {
        return blockingToSingle(() -> partitionedRedisClient.xread(keys, ids));
    }

    @Override
    public <T> Single<List<T>> xread(@Nullable final Long count, @Nullable final Long blockMilliseconds,
                                     @RedisProtocolSupport.Key final Collection<? extends CharSequence> keys,
                                     final Collection<? extends CharSequence> ids) {
        return blockingToSingle(() -> partitionedRedisClient.xread(count, blockMilliseconds, keys, ids));
    }

    @Override
    public <T> Single<List<T>> xreadgroup(final RedisProtocolSupport.GroupConsumer groupConsumer,
                                          @RedisProtocolSupport.Key final Collection<? extends CharSequence> keys,
                                          final Collection<? extends CharSequence> ids) {
        return blockingToSingle(() -> partitionedRedisClient.xreadgroup(groupConsumer, keys, ids));
    }

    @Override
    public <T> Single<List<T>> xreadgroup(final RedisProtocolSupport.GroupConsumer groupConsumer,
                                          @Nullable final Long count, @Nullable final Long blockMilliseconds,
                                          @RedisProtocolSupport.Key final Collection<? extends CharSequence> keys,
                                          final Collection<? extends CharSequence> ids) {
        return blockingToSingle(
                    () -> partitionedRedisClient.xreadgroup(groupConsumer, count, blockMilliseconds, keys, ids));
    }

    @Override
    public <T> Single<List<T>> xrevrange(@RedisProtocolSupport.Key final CharSequence key, final CharSequence end,
                                         final CharSequence start) {
        return blockingToSingle(() -> partitionedRedisClient.xrevrange(key, end, start));
    }

    @Override
    public <T> Single<List<T>> xrevrange(@RedisProtocolSupport.Key final CharSequence key, final CharSequence end,
                                         final CharSequence start, @Nullable final Long count) {
        return blockingToSingle(() -> partitionedRedisClient.xrevrange(key, end, start, count));
    }

    @Override
    public Single<Long> zadd(@RedisProtocolSupport.Key final CharSequence key,
                             final Collection<RedisProtocolSupport.ScoreMember> scoreMembers) {
        return blockingToSingle(() -> partitionedRedisClient.zadd(key, scoreMembers));
    }

    @Override
    public Single<Long> zadd(@RedisProtocolSupport.Key final CharSequence key,
                             @Nullable final RedisProtocolSupport.ZaddCondition condition,
                             @Nullable final RedisProtocolSupport.ZaddChange change, final double score,
                             final CharSequence member) {
        return blockingToSingle(() -> partitionedRedisClient.zadd(key, condition, change, score, member));
    }

    @Override
    public Single<Long> zadd(@RedisProtocolSupport.Key final CharSequence key,
                             @Nullable final RedisProtocolSupport.ZaddCondition condition,
                             @Nullable final RedisProtocolSupport.ZaddChange change, final double score1,
                             final CharSequence member1, final double score2, final CharSequence member2) {
        return blockingToSingle(
                    () -> partitionedRedisClient.zadd(key, condition, change, score1, member1, score2, member2));
    }

    @Override
    public Single<Long> zadd(@RedisProtocolSupport.Key final CharSequence key,
                             @Nullable final RedisProtocolSupport.ZaddCondition condition,
                             @Nullable final RedisProtocolSupport.ZaddChange change, final double score1,
                             final CharSequence member1, final double score2, final CharSequence member2,
                             final double score3, final CharSequence member3) {
        return blockingToSingle(() -> partitionedRedisClient.zadd(key, condition, change, score1, member1, score2,
                    member2, score3, member3));
    }

    @Override
    public Single<Long> zadd(@RedisProtocolSupport.Key final CharSequence key,
                             @Nullable final RedisProtocolSupport.ZaddCondition condition,
                             @Nullable final RedisProtocolSupport.ZaddChange change,
                             final Collection<RedisProtocolSupport.ScoreMember> scoreMembers) {
        return blockingToSingle(() -> partitionedRedisClient.zadd(key, condition, change, scoreMembers));
    }

    @Override
    public Single<Double> zaddIncr(@RedisProtocolSupport.Key final CharSequence key,
                                   final Collection<RedisProtocolSupport.ScoreMember> scoreMembers) {
        return blockingToSingle(() -> partitionedRedisClient.zaddIncr(key, scoreMembers));
    }

    @Override
    public Single<Double> zaddIncr(@RedisProtocolSupport.Key final CharSequence key,
                                   @Nullable final RedisProtocolSupport.ZaddCondition condition,
                                   @Nullable final RedisProtocolSupport.ZaddChange change, final double score,
                                   final CharSequence member) {
        return blockingToSingle(() -> partitionedRedisClient.zaddIncr(key, condition, change, score, member));
    }

    @Override
    public Single<Double> zaddIncr(@RedisProtocolSupport.Key final CharSequence key,
                                   @Nullable final RedisProtocolSupport.ZaddCondition condition,
                                   @Nullable final RedisProtocolSupport.ZaddChange change, final double score1,
                                   final CharSequence member1, final double score2, final CharSequence member2) {
        return blockingToSingle(
                    () -> partitionedRedisClient.zaddIncr(key, condition, change, score1, member1, score2, member2));
    }

    @Override
    public Single<Double> zaddIncr(@RedisProtocolSupport.Key final CharSequence key,
                                   @Nullable final RedisProtocolSupport.ZaddCondition condition,
                                   @Nullable final RedisProtocolSupport.ZaddChange change, final double score1,
                                   final CharSequence member1, final double score2, final CharSequence member2,
                                   final double score3, final CharSequence member3) {
        return blockingToSingle(() -> partitionedRedisClient.zaddIncr(key, condition, change, score1, member1, score2,
                    member2, score3, member3));
    }

    @Override
    public Single<Double> zaddIncr(@RedisProtocolSupport.Key final CharSequence key,
                                   @Nullable final RedisProtocolSupport.ZaddCondition condition,
                                   @Nullable final RedisProtocolSupport.ZaddChange change,
                                   final Collection<RedisProtocolSupport.ScoreMember> scoreMembers) {
        return blockingToSingle(() -> partitionedRedisClient.zaddIncr(key, condition, change, scoreMembers));
    }

    @Override
    public Single<Long> zcard(@RedisProtocolSupport.Key final CharSequence key) {
        return blockingToSingle(() -> partitionedRedisClient.zcard(key));
    }

    @Override
    public Single<Long> zcount(@RedisProtocolSupport.Key final CharSequence key, final double min, final double max) {
        return blockingToSingle(() -> partitionedRedisClient.zcount(key, min, max));
    }

    @Override
    public Single<Double> zincrby(@RedisProtocolSupport.Key final CharSequence key, final long increment,
                                  final CharSequence member) {
        return blockingToSingle(() -> partitionedRedisClient.zincrby(key, increment, member));
    }

    @Override
    public Single<Long> zinterstore(@RedisProtocolSupport.Key final CharSequence destination, final long numkeys,
                                    @RedisProtocolSupport.Key final Collection<? extends CharSequence> keys) {
        return blockingToSingle(() -> partitionedRedisClient.zinterstore(destination, numkeys, keys));
    }

    @Override
    public Single<Long> zinterstore(@RedisProtocolSupport.Key final CharSequence destination, final long numkeys,
                                    @RedisProtocolSupport.Key final Collection<? extends CharSequence> keys,
                                    final Collection<Long> weights,
                                    @Nullable final RedisProtocolSupport.ZinterstoreAggregate aggregate) {
        return blockingToSingle(
                    () -> partitionedRedisClient.zinterstore(destination, numkeys, keys, weights, aggregate));
    }

    @Override
    public Single<Long> zlexcount(@RedisProtocolSupport.Key final CharSequence key, final CharSequence min,
                                  final CharSequence max) {
        return blockingToSingle(() -> partitionedRedisClient.zlexcount(key, min, max));
    }

    @Override
    public <T> Single<List<T>> zpopmax(@RedisProtocolSupport.Key final CharSequence key) {
        return blockingToSingle(() -> partitionedRedisClient.zpopmax(key));
    }

    @Override
    public <T> Single<List<T>> zpopmax(@RedisProtocolSupport.Key final CharSequence key, @Nullable final Long count) {
        return blockingToSingle(() -> partitionedRedisClient.zpopmax(key, count));
    }

    @Override
    public <T> Single<List<T>> zpopmin(@RedisProtocolSupport.Key final CharSequence key) {
        return blockingToSingle(() -> partitionedRedisClient.zpopmin(key));
    }

    @Override
    public <T> Single<List<T>> zpopmin(@RedisProtocolSupport.Key final CharSequence key, @Nullable final Long count) {
        return blockingToSingle(() -> partitionedRedisClient.zpopmin(key, count));
    }

    @Override
    public <T> Single<List<T>> zrange(@RedisProtocolSupport.Key final CharSequence key, final long start,
                                      final long stop) {
        return blockingToSingle(() -> partitionedRedisClient.zrange(key, start, stop));
    }

    @Override
    public <T> Single<List<T>> zrange(@RedisProtocolSupport.Key final CharSequence key, final long start,
                                      final long stop,
                                      @Nullable final RedisProtocolSupport.ZrangeWithscores withscores) {
        return blockingToSingle(() -> partitionedRedisClient.zrange(key, start, stop, withscores));
    }

    @Override
    public <T> Single<List<T>> zrangebylex(@RedisProtocolSupport.Key final CharSequence key, final CharSequence min,
                                           final CharSequence max) {
        return blockingToSingle(() -> partitionedRedisClient.zrangebylex(key, min, max));
    }

    @Override
    public <T> Single<List<T>> zrangebylex(@RedisProtocolSupport.Key final CharSequence key, final CharSequence min,
                                           final CharSequence max,
                                           @Nullable final RedisProtocolSupport.OffsetCount offsetCount) {
        return blockingToSingle(() -> partitionedRedisClient.zrangebylex(key, min, max, offsetCount));
    }

    @Override
    public <T> Single<List<T>> zrangebyscore(@RedisProtocolSupport.Key final CharSequence key, final double min,
                                             final double max) {
        return blockingToSingle(() -> partitionedRedisClient.zrangebyscore(key, min, max));
    }

    @Override
    public <T> Single<List<T>> zrangebyscore(@RedisProtocolSupport.Key final CharSequence key, final double min,
                                             final double max,
                                             @Nullable final RedisProtocolSupport.ZrangebyscoreWithscores withscores,
                                             @Nullable final RedisProtocolSupport.OffsetCount offsetCount) {
        return blockingToSingle(() -> partitionedRedisClient.zrangebyscore(key, min, max, withscores, offsetCount));
    }

    @Override
    public Single<Long> zrank(@RedisProtocolSupport.Key final CharSequence key, final CharSequence member) {
        return blockingToSingle(() -> partitionedRedisClient.zrank(key, member));
    }

    @Override
    public Single<Long> zrem(@RedisProtocolSupport.Key final CharSequence key, final CharSequence member) {
        return blockingToSingle(() -> partitionedRedisClient.zrem(key, member));
    }

    @Override
    public Single<Long> zrem(@RedisProtocolSupport.Key final CharSequence key, final CharSequence member1,
                             final CharSequence member2) {
        return blockingToSingle(() -> partitionedRedisClient.zrem(key, member1, member2));
    }

    @Override
    public Single<Long> zrem(@RedisProtocolSupport.Key final CharSequence key, final CharSequence member1,
                             final CharSequence member2, final CharSequence member3) {
        return blockingToSingle(() -> partitionedRedisClient.zrem(key, member1, member2, member3));
    }

    @Override
    public Single<Long> zrem(@RedisProtocolSupport.Key final CharSequence key,
                             final Collection<? extends CharSequence> members) {
        return blockingToSingle(() -> partitionedRedisClient.zrem(key, members));
    }

    @Override
    public Single<Long> zremrangebylex(@RedisProtocolSupport.Key final CharSequence key, final CharSequence min,
                                       final CharSequence max) {
        return blockingToSingle(() -> partitionedRedisClient.zremrangebylex(key, min, max));
    }

    @Override
    public Single<Long> zremrangebyrank(@RedisProtocolSupport.Key final CharSequence key, final long start,
                                        final long stop) {
        return blockingToSingle(() -> partitionedRedisClient.zremrangebyrank(key, start, stop));
    }

    @Override
    public Single<Long> zremrangebyscore(@RedisProtocolSupport.Key final CharSequence key, final double min,
                                         final double max) {
        return blockingToSingle(() -> partitionedRedisClient.zremrangebyscore(key, min, max));
    }

    @Override
    public <T> Single<List<T>> zrevrange(@RedisProtocolSupport.Key final CharSequence key, final long start,
                                         final long stop) {
        return blockingToSingle(() -> partitionedRedisClient.zrevrange(key, start, stop));
    }

    @Override
    public <T> Single<List<T>> zrevrange(@RedisProtocolSupport.Key final CharSequence key, final long start,
                                         final long stop,
                                         @Nullable final RedisProtocolSupport.ZrevrangeWithscores withscores) {
        return blockingToSingle(() -> partitionedRedisClient.zrevrange(key, start, stop, withscores));
    }

    @Override
    public <T> Single<List<T>> zrevrangebylex(@RedisProtocolSupport.Key final CharSequence key, final CharSequence max,
                                              final CharSequence min) {
        return blockingToSingle(() -> partitionedRedisClient.zrevrangebylex(key, max, min));
    }

    @Override
    public <T> Single<List<T>> zrevrangebylex(@RedisProtocolSupport.Key final CharSequence key, final CharSequence max,
                                              final CharSequence min,
                                              @Nullable final RedisProtocolSupport.OffsetCount offsetCount) {
        return blockingToSingle(() -> partitionedRedisClient.zrevrangebylex(key, max, min, offsetCount));
    }

    @Override
    public <T> Single<List<T>> zrevrangebyscore(@RedisProtocolSupport.Key final CharSequence key, final double max,
                                                final double min) {
        return blockingToSingle(() -> partitionedRedisClient.zrevrangebyscore(key, max, min));
    }

    @Override
    public <T> Single<List<T>> zrevrangebyscore(@RedisProtocolSupport.Key final CharSequence key, final double max,
                                                final double min,
                                                @Nullable final RedisProtocolSupport.ZrevrangebyscoreWithscores withscores,
                                                @Nullable final RedisProtocolSupport.OffsetCount offsetCount) {
        return blockingToSingle(() -> partitionedRedisClient.zrevrangebyscore(key, max, min, withscores, offsetCount));
    }

    @Override
    public Single<Long> zrevrank(@RedisProtocolSupport.Key final CharSequence key, final CharSequence member) {
        return blockingToSingle(() -> partitionedRedisClient.zrevrank(key, member));
    }

    @Override
    public <T> Single<List<T>> zscan(@RedisProtocolSupport.Key final CharSequence key, final long cursor) {
        return blockingToSingle(() -> partitionedRedisClient.zscan(key, cursor));
    }

    @Override
    public <T> Single<List<T>> zscan(@RedisProtocolSupport.Key final CharSequence key, final long cursor,
                                     @Nullable final CharSequence matchPattern, @Nullable final Long count) {
        return blockingToSingle(() -> partitionedRedisClient.zscan(key, cursor, matchPattern, count));
    }

    @Override
    public Single<Double> zscore(@RedisProtocolSupport.Key final CharSequence key, final CharSequence member) {
        return blockingToSingle(() -> partitionedRedisClient.zscore(key, member));
    }

    @Override
    public Single<Long> zunionstore(@RedisProtocolSupport.Key final CharSequence destination, final long numkeys,
                                    @RedisProtocolSupport.Key final Collection<? extends CharSequence> keys) {
        return blockingToSingle(() -> partitionedRedisClient.zunionstore(destination, numkeys, keys));
    }

    @Override
    public Single<Long> zunionstore(@RedisProtocolSupport.Key final CharSequence destination, final long numkeys,
                                    @RedisProtocolSupport.Key final Collection<? extends CharSequence> keys,
                                    final Collection<Long> weights,
                                    @Nullable final RedisProtocolSupport.ZunionstoreAggregate aggregate) {
        return blockingToSingle(
                    () -> partitionedRedisClient.zunionstore(destination, numkeys, keys, weights, aggregate));
    }

    BlockingRedisCommander asBlockingCommanderInternal() {
        return partitionedRedisClient;
    }
}

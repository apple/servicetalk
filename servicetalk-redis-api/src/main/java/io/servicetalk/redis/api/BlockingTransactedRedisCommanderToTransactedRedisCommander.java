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
import java.util.concurrent.Future;
import javax.annotation.Generated;
import javax.annotation.Nullable;

import static io.servicetalk.redis.api.BlockingUtils.blockingToCompletable;
import static io.servicetalk.redis.api.BlockingUtils.blockingToSingle;

@Generated({})
@SuppressWarnings("unchecked")
final class BlockingTransactedRedisCommanderToTransactedRedisCommander extends AbstractTransactedRedisCommander {

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
    public Future<Long> append(@RedisProtocolSupport.Key final CharSequence key, final CharSequence value) {
        return reservedCnx.append(key, value);
    }

    @Override
    public Future<String> auth(final CharSequence password) {
        return reservedCnx.auth(password);
    }

    @Override
    public Future<String> bgrewriteaof() {
        return reservedCnx.bgrewriteaof();
    }

    @Override
    public Future<String> bgsave() {
        return reservedCnx.bgsave();
    }

    @Override
    public Future<Long> bitcount(@RedisProtocolSupport.Key final CharSequence key) {
        return reservedCnx.bitcount(key);
    }

    @Override
    public Future<Long> bitcount(@RedisProtocolSupport.Key final CharSequence key, @Nullable final Long start,
                                 @Nullable final Long end) {
        return reservedCnx.bitcount(key, start, end);
    }

    @Override
    public Future<List<Long>> bitfield(@RedisProtocolSupport.Key final CharSequence key,
                                       final Collection<RedisProtocolSupport.BitfieldOperation> operations) {
        return reservedCnx.bitfield(key, operations);
    }

    @Override
    public Future<Long> bitop(final CharSequence operation, @RedisProtocolSupport.Key final CharSequence destkey,
                              @RedisProtocolSupport.Key final CharSequence key) {
        return reservedCnx.bitop(operation, destkey, key);
    }

    @Override
    public Future<Long> bitop(final CharSequence operation, @RedisProtocolSupport.Key final CharSequence destkey,
                              @RedisProtocolSupport.Key final CharSequence key1,
                              @RedisProtocolSupport.Key final CharSequence key2) {
        return reservedCnx.bitop(operation, destkey, key1, key2);
    }

    @Override
    public Future<Long> bitop(final CharSequence operation, @RedisProtocolSupport.Key final CharSequence destkey,
                              @RedisProtocolSupport.Key final CharSequence key1,
                              @RedisProtocolSupport.Key final CharSequence key2,
                              @RedisProtocolSupport.Key final CharSequence key3) {
        return reservedCnx.bitop(operation, destkey, key1, key2, key3);
    }

    @Override
    public Future<Long> bitop(final CharSequence operation, @RedisProtocolSupport.Key final CharSequence destkey,
                              @RedisProtocolSupport.Key final Collection<? extends CharSequence> keys) {
        return reservedCnx.bitop(operation, destkey, keys);
    }

    @Override
    public Future<Long> bitpos(@RedisProtocolSupport.Key final CharSequence key, final long bit) {
        return reservedCnx.bitpos(key, bit);
    }

    @Override
    public Future<Long> bitpos(@RedisProtocolSupport.Key final CharSequence key, final long bit,
                               @Nullable final Long start, @Nullable final Long end) {
        return reservedCnx.bitpos(key, bit, start, end);
    }

    @Override
    public <T> Future<List<T>> blpop(@RedisProtocolSupport.Key final Collection<? extends CharSequence> keys,
                                     final long timeout) {
        return reservedCnx.blpop(keys, timeout);
    }

    @Override
    public <T> Future<List<T>> brpop(@RedisProtocolSupport.Key final Collection<? extends CharSequence> keys,
                                     final long timeout) {
        return reservedCnx.brpop(keys, timeout);
    }

    @Override
    public Future<String> brpoplpush(@RedisProtocolSupport.Key final CharSequence source,
                                     @RedisProtocolSupport.Key final CharSequence destination, final long timeout) {
        return reservedCnx.brpoplpush(source, destination, timeout);
    }

    @Override
    public <T> Future<List<T>> bzpopmax(@RedisProtocolSupport.Key final Collection<? extends CharSequence> keys,
                                        final long timeout) {
        return reservedCnx.bzpopmax(keys, timeout);
    }

    @Override
    public <T> Future<List<T>> bzpopmin(@RedisProtocolSupport.Key final Collection<? extends CharSequence> keys,
                                        final long timeout) {
        return reservedCnx.bzpopmin(keys, timeout);
    }

    @Override
    public Future<Long> clientKill(@Nullable final Long id, @Nullable final RedisProtocolSupport.ClientKillType type,
                                   @Nullable final CharSequence addrIpPort, @Nullable final CharSequence skipmeYesNo) {
        return reservedCnx.clientKill(id, type, addrIpPort, skipmeYesNo);
    }

    @Override
    public Future<String> clientList() {
        return reservedCnx.clientList();
    }

    @Override
    public Future<String> clientGetname() {
        return reservedCnx.clientGetname();
    }

    @Override
    public Future<String> clientPause(final long timeout) {
        return reservedCnx.clientPause(timeout);
    }

    @Override
    public Future<String> clientReply(final RedisProtocolSupport.ClientReplyReplyMode replyMode) {
        return reservedCnx.clientReply(replyMode);
    }

    @Override
    public Future<String> clientSetname(final CharSequence connectionName) {
        return reservedCnx.clientSetname(connectionName);
    }

    @Override
    public Future<String> clusterAddslots(final long slot) {
        return reservedCnx.clusterAddslots(slot);
    }

    @Override
    public Future<String> clusterAddslots(final long slot1, final long slot2) {
        return reservedCnx.clusterAddslots(slot1, slot2);
    }

    @Override
    public Future<String> clusterAddslots(final long slot1, final long slot2, final long slot3) {
        return reservedCnx.clusterAddslots(slot1, slot2, slot3);
    }

    @Override
    public Future<String> clusterAddslots(final Collection<Long> slots) {
        return reservedCnx.clusterAddslots(slots);
    }

    @Override
    public Future<Long> clusterCountFailureReports(final CharSequence nodeId) {
        return reservedCnx.clusterCountFailureReports(nodeId);
    }

    @Override
    public Future<Long> clusterCountkeysinslot(final long slot) {
        return reservedCnx.clusterCountkeysinslot(slot);
    }

    @Override
    public Future<String> clusterDelslots(final long slot) {
        return reservedCnx.clusterDelslots(slot);
    }

    @Override
    public Future<String> clusterDelslots(final long slot1, final long slot2) {
        return reservedCnx.clusterDelslots(slot1, slot2);
    }

    @Override
    public Future<String> clusterDelslots(final long slot1, final long slot2, final long slot3) {
        return reservedCnx.clusterDelslots(slot1, slot2, slot3);
    }

    @Override
    public Future<String> clusterDelslots(final Collection<Long> slots) {
        return reservedCnx.clusterDelslots(slots);
    }

    @Override
    public Future<String> clusterFailover() {
        return reservedCnx.clusterFailover();
    }

    @Override
    public Future<String> clusterFailover(@Nullable final RedisProtocolSupport.ClusterFailoverOptions options) {
        return reservedCnx.clusterFailover(options);
    }

    @Override
    public Future<String> clusterForget(final CharSequence nodeId) {
        return reservedCnx.clusterForget(nodeId);
    }

    @Override
    public <T> Future<List<T>> clusterGetkeysinslot(final long slot, final long count) {
        return reservedCnx.clusterGetkeysinslot(slot, count);
    }

    @Override
    public Future<String> clusterInfo() {
        return reservedCnx.clusterInfo();
    }

    @Override
    public Future<Long> clusterKeyslot(final CharSequence key) {
        return reservedCnx.clusterKeyslot(key);
    }

    @Override
    public Future<String> clusterMeet(final CharSequence ip, final long port) {
        return reservedCnx.clusterMeet(ip, port);
    }

    @Override
    public Future<String> clusterNodes() {
        return reservedCnx.clusterNodes();
    }

    @Override
    public Future<String> clusterReplicate(final CharSequence nodeId) {
        return reservedCnx.clusterReplicate(nodeId);
    }

    @Override
    public Future<String> clusterReset() {
        return reservedCnx.clusterReset();
    }

    @Override
    public Future<String> clusterReset(@Nullable final RedisProtocolSupport.ClusterResetResetType resetType) {
        return reservedCnx.clusterReset(resetType);
    }

    @Override
    public Future<String> clusterSaveconfig() {
        return reservedCnx.clusterSaveconfig();
    }

    @Override
    public Future<String> clusterSetConfigEpoch(final long configEpoch) {
        return reservedCnx.clusterSetConfigEpoch(configEpoch);
    }

    @Override
    public Future<String> clusterSetslot(final long slot,
                                         final RedisProtocolSupport.ClusterSetslotSubcommand subcommand) {
        return reservedCnx.clusterSetslot(slot, subcommand);
    }

    @Override
    public Future<String> clusterSetslot(final long slot,
                                         final RedisProtocolSupport.ClusterSetslotSubcommand subcommand,
                                         @Nullable final CharSequence nodeId) {
        return reservedCnx.clusterSetslot(slot, subcommand, nodeId);
    }

    @Override
    public Future<String> clusterSlaves(final CharSequence nodeId) {
        return reservedCnx.clusterSlaves(nodeId);
    }

    @Override
    public <T> Future<List<T>> clusterSlots() {
        return reservedCnx.clusterSlots();
    }

    @Override
    public <T> Future<List<T>> command() {
        return reservedCnx.command();
    }

    @Override
    public Future<Long> commandCount() {
        return reservedCnx.commandCount();
    }

    @Override
    public <T> Future<List<T>> commandGetkeys() {
        return reservedCnx.commandGetkeys();
    }

    @Override
    public <T> Future<List<T>> commandInfo(final CharSequence commandName) {
        return reservedCnx.commandInfo(commandName);
    }

    @Override
    public <T> Future<List<T>> commandInfo(final CharSequence commandName1, final CharSequence commandName2) {
        return reservedCnx.commandInfo(commandName1, commandName2);
    }

    @Override
    public <T> Future<List<T>> commandInfo(final CharSequence commandName1, final CharSequence commandName2,
                                           final CharSequence commandName3) {
        return reservedCnx.commandInfo(commandName1, commandName2, commandName3);
    }

    @Override
    public <T> Future<List<T>> commandInfo(final Collection<? extends CharSequence> commandNames) {
        return reservedCnx.commandInfo(commandNames);
    }

    @Override
    public <T> Future<List<T>> configGet(final CharSequence parameter) {
        return reservedCnx.configGet(parameter);
    }

    @Override
    public Future<String> configRewrite() {
        return reservedCnx.configRewrite();
    }

    @Override
    public Future<String> configSet(final CharSequence parameter, final CharSequence value) {
        return reservedCnx.configSet(parameter, value);
    }

    @Override
    public Future<String> configResetstat() {
        return reservedCnx.configResetstat();
    }

    @Override
    public Future<Long> dbsize() {
        return reservedCnx.dbsize();
    }

    @Override
    public Future<String> debugObject(@RedisProtocolSupport.Key final CharSequence key) {
        return reservedCnx.debugObject(key);
    }

    @Override
    public Future<String> debugSegfault() {
        return reservedCnx.debugSegfault();
    }

    @Override
    public Future<Long> decr(@RedisProtocolSupport.Key final CharSequence key) {
        return reservedCnx.decr(key);
    }

    @Override
    public Future<Long> decrby(@RedisProtocolSupport.Key final CharSequence key, final long decrement) {
        return reservedCnx.decrby(key, decrement);
    }

    @Override
    public Future<Long> del(@RedisProtocolSupport.Key final CharSequence key) {
        return reservedCnx.del(key);
    }

    @Override
    public Future<Long> del(@RedisProtocolSupport.Key final CharSequence key1,
                            @RedisProtocolSupport.Key final CharSequence key2) {
        return reservedCnx.del(key1, key2);
    }

    @Override
    public Future<Long> del(@RedisProtocolSupport.Key final CharSequence key1,
                            @RedisProtocolSupport.Key final CharSequence key2,
                            @RedisProtocolSupport.Key final CharSequence key3) {
        return reservedCnx.del(key1, key2, key3);
    }

    @Override
    public Future<Long> del(@RedisProtocolSupport.Key final Collection<? extends CharSequence> keys) {
        return reservedCnx.del(keys);
    }

    @Override
    public Single<String> discard() {
        return blockingToSingle(() -> reservedCnx.discard());
    }

    @Override
    public Future<String> dump(@RedisProtocolSupport.Key final CharSequence key) {
        return reservedCnx.dump(key);
    }

    @Override
    public Future<String> echo(final CharSequence message) {
        return reservedCnx.echo(message);
    }

    @Override
    public Future<String> eval(final CharSequence script, final long numkeys,
                               @RedisProtocolSupport.Key final Collection<? extends CharSequence> keys,
                               final Collection<? extends CharSequence> args) {
        return reservedCnx.eval(script, numkeys, keys, args);
    }

    @Override
    public <T> Future<List<T>> evalList(final CharSequence script, final long numkeys,
                                        @RedisProtocolSupport.Key final Collection<? extends CharSequence> keys,
                                        final Collection<? extends CharSequence> args) {
        return reservedCnx.evalList(script, numkeys, keys, args);
    }

    @Override
    public Future<Long> evalLong(final CharSequence script, final long numkeys,
                                 @RedisProtocolSupport.Key final Collection<? extends CharSequence> keys,
                                 final Collection<? extends CharSequence> args) {
        return reservedCnx.evalLong(script, numkeys, keys, args);
    }

    @Override
    public Future<String> evalsha(final CharSequence sha1, final long numkeys,
                                  @RedisProtocolSupport.Key final Collection<? extends CharSequence> keys,
                                  final Collection<? extends CharSequence> args) {
        return reservedCnx.evalsha(sha1, numkeys, keys, args);
    }

    @Override
    public <T> Future<List<T>> evalshaList(final CharSequence sha1, final long numkeys,
                                           @RedisProtocolSupport.Key final Collection<? extends CharSequence> keys,
                                           final Collection<? extends CharSequence> args) {
        return reservedCnx.evalshaList(sha1, numkeys, keys, args);
    }

    @Override
    public Future<Long> evalshaLong(final CharSequence sha1, final long numkeys,
                                    @RedisProtocolSupport.Key final Collection<? extends CharSequence> keys,
                                    final Collection<? extends CharSequence> args) {
        return reservedCnx.evalshaLong(sha1, numkeys, keys, args);
    }

    @Override
    public Completable exec() {
        return blockingToCompletable(() -> reservedCnx.exec());
    }

    @Override
    public Future<Long> exists(@RedisProtocolSupport.Key final CharSequence key) {
        return reservedCnx.exists(key);
    }

    @Override
    public Future<Long> exists(@RedisProtocolSupport.Key final CharSequence key1,
                               @RedisProtocolSupport.Key final CharSequence key2) {
        return reservedCnx.exists(key1, key2);
    }

    @Override
    public Future<Long> exists(@RedisProtocolSupport.Key final CharSequence key1,
                               @RedisProtocolSupport.Key final CharSequence key2,
                               @RedisProtocolSupport.Key final CharSequence key3) {
        return reservedCnx.exists(key1, key2, key3);
    }

    @Override
    public Future<Long> exists(@RedisProtocolSupport.Key final Collection<? extends CharSequence> keys) {
        return reservedCnx.exists(keys);
    }

    @Override
    public Future<Long> expire(@RedisProtocolSupport.Key final CharSequence key, final long seconds) {
        return reservedCnx.expire(key, seconds);
    }

    @Override
    public Future<Long> expireat(@RedisProtocolSupport.Key final CharSequence key, final long timestamp) {
        return reservedCnx.expireat(key, timestamp);
    }

    @Override
    public Future<String> flushall() {
        return reservedCnx.flushall();
    }

    @Override
    public Future<String> flushall(@Nullable final RedisProtocolSupport.FlushallAsync async) {
        return reservedCnx.flushall(async);
    }

    @Override
    public Future<String> flushdb() {
        return reservedCnx.flushdb();
    }

    @Override
    public Future<String> flushdb(@Nullable final RedisProtocolSupport.FlushdbAsync async) {
        return reservedCnx.flushdb(async);
    }

    @Override
    public Future<Long> geoadd(@RedisProtocolSupport.Key final CharSequence key, final double longitude,
                               final double latitude, final CharSequence member) {
        return reservedCnx.geoadd(key, longitude, latitude, member);
    }

    @Override
    public Future<Long> geoadd(@RedisProtocolSupport.Key final CharSequence key, final double longitude1,
                               final double latitude1, final CharSequence member1, final double longitude2,
                               final double latitude2, final CharSequence member2) {
        return reservedCnx.geoadd(key, longitude1, latitude1, member1, longitude2, latitude2, member2);
    }

    @Override
    public Future<Long> geoadd(@RedisProtocolSupport.Key final CharSequence key, final double longitude1,
                               final double latitude1, final CharSequence member1, final double longitude2,
                               final double latitude2, final CharSequence member2, final double longitude3,
                               final double latitude3, final CharSequence member3) {
        return reservedCnx.geoadd(key, longitude1, latitude1, member1, longitude2, latitude2, member2, longitude3,
                    latitude3, member3);
    }

    @Override
    public Future<Long> geoadd(@RedisProtocolSupport.Key final CharSequence key,
                               final Collection<RedisProtocolSupport.LongitudeLatitudeMember> longitudeLatitudeMembers) {
        return reservedCnx.geoadd(key, longitudeLatitudeMembers);
    }

    @Override
    public Future<Double> geodist(@RedisProtocolSupport.Key final CharSequence key, final CharSequence member1,
                                  final CharSequence member2) {
        return reservedCnx.geodist(key, member1, member2);
    }

    @Override
    public Future<Double> geodist(@RedisProtocolSupport.Key final CharSequence key, final CharSequence member1,
                                  final CharSequence member2, @Nullable final CharSequence unit) {
        return reservedCnx.geodist(key, member1, member2, unit);
    }

    @Override
    public <T> Future<List<T>> geohash(@RedisProtocolSupport.Key final CharSequence key, final CharSequence member) {
        return reservedCnx.geohash(key, member);
    }

    @Override
    public <T> Future<List<T>> geohash(@RedisProtocolSupport.Key final CharSequence key, final CharSequence member1,
                                       final CharSequence member2) {
        return reservedCnx.geohash(key, member1, member2);
    }

    @Override
    public <T> Future<List<T>> geohash(@RedisProtocolSupport.Key final CharSequence key, final CharSequence member1,
                                       final CharSequence member2, final CharSequence member3) {
        return reservedCnx.geohash(key, member1, member2, member3);
    }

    @Override
    public <T> Future<List<T>> geohash(@RedisProtocolSupport.Key final CharSequence key,
                                       final Collection<? extends CharSequence> members) {
        return reservedCnx.geohash(key, members);
    }

    @Override
    public <T> Future<List<T>> geopos(@RedisProtocolSupport.Key final CharSequence key, final CharSequence member) {
        return reservedCnx.geopos(key, member);
    }

    @Override
    public <T> Future<List<T>> geopos(@RedisProtocolSupport.Key final CharSequence key, final CharSequence member1,
                                      final CharSequence member2) {
        return reservedCnx.geopos(key, member1, member2);
    }

    @Override
    public <T> Future<List<T>> geopos(@RedisProtocolSupport.Key final CharSequence key, final CharSequence member1,
                                      final CharSequence member2, final CharSequence member3) {
        return reservedCnx.geopos(key, member1, member2, member3);
    }

    @Override
    public <T> Future<List<T>> geopos(@RedisProtocolSupport.Key final CharSequence key,
                                      final Collection<? extends CharSequence> members) {
        return reservedCnx.geopos(key, members);
    }

    @Override
    public <T> Future<List<T>> georadius(@RedisProtocolSupport.Key final CharSequence key, final double longitude,
                                         final double latitude, final double radius,
                                         final RedisProtocolSupport.GeoradiusUnit unit) {
        return reservedCnx.georadius(key, longitude, latitude, radius, unit);
    }

    @Override
    public <T> Future<List<T>> georadius(@RedisProtocolSupport.Key final CharSequence key, final double longitude,
                                         final double latitude, final double radius,
                                         final RedisProtocolSupport.GeoradiusUnit unit,
                                         @Nullable final RedisProtocolSupport.GeoradiusWithcoord withcoord,
                                         @Nullable final RedisProtocolSupport.GeoradiusWithdist withdist,
                                         @Nullable final RedisProtocolSupport.GeoradiusWithhash withhash,
                                         @Nullable final Long count,
                                         @Nullable final RedisProtocolSupport.GeoradiusOrder order,
                                         @Nullable @RedisProtocolSupport.Key final CharSequence storeKey,
                                         @Nullable @RedisProtocolSupport.Key final CharSequence storedistKey) {
        return reservedCnx.georadius(key, longitude, latitude, radius, unit, withcoord, withdist, withhash, count,
                    order, storeKey, storedistKey);
    }

    @Override
    public <T> Future<List<T>> georadiusbymember(@RedisProtocolSupport.Key final CharSequence key,
                                                 final CharSequence member, final double radius,
                                                 final RedisProtocolSupport.GeoradiusbymemberUnit unit) {
        return reservedCnx.georadiusbymember(key, member, radius, unit);
    }

    @Override
    public <T> Future<List<T>> georadiusbymember(@RedisProtocolSupport.Key final CharSequence key,
                                                 final CharSequence member, final double radius,
                                                 final RedisProtocolSupport.GeoradiusbymemberUnit unit,
                                                 @Nullable final RedisProtocolSupport.GeoradiusbymemberWithcoord withcoord,
                                                 @Nullable final RedisProtocolSupport.GeoradiusbymemberWithdist withdist,
                                                 @Nullable final RedisProtocolSupport.GeoradiusbymemberWithhash withhash,
                                                 @Nullable final Long count,
                                                 @Nullable final RedisProtocolSupport.GeoradiusbymemberOrder order,
                                                 @Nullable @RedisProtocolSupport.Key final CharSequence storeKey,
                                                 @Nullable @RedisProtocolSupport.Key final CharSequence storedistKey) {
        return reservedCnx.georadiusbymember(key, member, radius, unit, withcoord, withdist, withhash, count, order,
                    storeKey, storedistKey);
    }

    @Override
    public Future<String> get(@RedisProtocolSupport.Key final CharSequence key) {
        return reservedCnx.get(key);
    }

    @Override
    public Future<Long> getbit(@RedisProtocolSupport.Key final CharSequence key, final long offset) {
        return reservedCnx.getbit(key, offset);
    }

    @Override
    public Future<String> getrange(@RedisProtocolSupport.Key final CharSequence key, final long start, final long end) {
        return reservedCnx.getrange(key, start, end);
    }

    @Override
    public Future<String> getset(@RedisProtocolSupport.Key final CharSequence key, final CharSequence value) {
        return reservedCnx.getset(key, value);
    }

    @Override
    public Future<Long> hdel(@RedisProtocolSupport.Key final CharSequence key, final CharSequence field) {
        return reservedCnx.hdel(key, field);
    }

    @Override
    public Future<Long> hdel(@RedisProtocolSupport.Key final CharSequence key, final CharSequence field1,
                             final CharSequence field2) {
        return reservedCnx.hdel(key, field1, field2);
    }

    @Override
    public Future<Long> hdel(@RedisProtocolSupport.Key final CharSequence key, final CharSequence field1,
                             final CharSequence field2, final CharSequence field3) {
        return reservedCnx.hdel(key, field1, field2, field3);
    }

    @Override
    public Future<Long> hdel(@RedisProtocolSupport.Key final CharSequence key,
                             final Collection<? extends CharSequence> fields) {
        return reservedCnx.hdel(key, fields);
    }

    @Override
    public Future<Long> hexists(@RedisProtocolSupport.Key final CharSequence key, final CharSequence field) {
        return reservedCnx.hexists(key, field);
    }

    @Override
    public Future<String> hget(@RedisProtocolSupport.Key final CharSequence key, final CharSequence field) {
        return reservedCnx.hget(key, field);
    }

    @Override
    public <T> Future<List<T>> hgetall(@RedisProtocolSupport.Key final CharSequence key) {
        return reservedCnx.hgetall(key);
    }

    @Override
    public Future<Long> hincrby(@RedisProtocolSupport.Key final CharSequence key, final CharSequence field,
                                final long increment) {
        return reservedCnx.hincrby(key, field, increment);
    }

    @Override
    public Future<Double> hincrbyfloat(@RedisProtocolSupport.Key final CharSequence key, final CharSequence field,
                                       final double increment) {
        return reservedCnx.hincrbyfloat(key, field, increment);
    }

    @Override
    public <T> Future<List<T>> hkeys(@RedisProtocolSupport.Key final CharSequence key) {
        return reservedCnx.hkeys(key);
    }

    @Override
    public Future<Long> hlen(@RedisProtocolSupport.Key final CharSequence key) {
        return reservedCnx.hlen(key);
    }

    @Override
    public <T> Future<List<T>> hmget(@RedisProtocolSupport.Key final CharSequence key, final CharSequence field) {
        return reservedCnx.hmget(key, field);
    }

    @Override
    public <T> Future<List<T>> hmget(@RedisProtocolSupport.Key final CharSequence key, final CharSequence field1,
                                     final CharSequence field2) {
        return reservedCnx.hmget(key, field1, field2);
    }

    @Override
    public <T> Future<List<T>> hmget(@RedisProtocolSupport.Key final CharSequence key, final CharSequence field1,
                                     final CharSequence field2, final CharSequence field3) {
        return reservedCnx.hmget(key, field1, field2, field3);
    }

    @Override
    public <T> Future<List<T>> hmget(@RedisProtocolSupport.Key final CharSequence key,
                                     final Collection<? extends CharSequence> fields) {
        return reservedCnx.hmget(key, fields);
    }

    @Override
    public Future<String> hmset(@RedisProtocolSupport.Key final CharSequence key, final CharSequence field,
                                final CharSequence value) {
        return reservedCnx.hmset(key, field, value);
    }

    @Override
    public Future<String> hmset(@RedisProtocolSupport.Key final CharSequence key, final CharSequence field1,
                                final CharSequence value1, final CharSequence field2, final CharSequence value2) {
        return reservedCnx.hmset(key, field1, value1, field2, value2);
    }

    @Override
    public Future<String> hmset(@RedisProtocolSupport.Key final CharSequence key, final CharSequence field1,
                                final CharSequence value1, final CharSequence field2, final CharSequence value2,
                                final CharSequence field3, final CharSequence value3) {
        return reservedCnx.hmset(key, field1, value1, field2, value2, field3, value3);
    }

    @Override
    public Future<String> hmset(@RedisProtocolSupport.Key final CharSequence key,
                                final Collection<RedisProtocolSupport.FieldValue> fieldValues) {
        return reservedCnx.hmset(key, fieldValues);
    }

    @Override
    public <T> Future<List<T>> hscan(@RedisProtocolSupport.Key final CharSequence key, final long cursor) {
        return reservedCnx.hscan(key, cursor);
    }

    @Override
    public <T> Future<List<T>> hscan(@RedisProtocolSupport.Key final CharSequence key, final long cursor,
                                     @Nullable final CharSequence matchPattern, @Nullable final Long count) {
        return reservedCnx.hscan(key, cursor, matchPattern, count);
    }

    @Override
    public Future<Long> hset(@RedisProtocolSupport.Key final CharSequence key, final CharSequence field,
                             final CharSequence value) {
        return reservedCnx.hset(key, field, value);
    }

    @Override
    public Future<Long> hsetnx(@RedisProtocolSupport.Key final CharSequence key, final CharSequence field,
                               final CharSequence value) {
        return reservedCnx.hsetnx(key, field, value);
    }

    @Override
    public Future<Long> hstrlen(@RedisProtocolSupport.Key final CharSequence key, final CharSequence field) {
        return reservedCnx.hstrlen(key, field);
    }

    @Override
    public <T> Future<List<T>> hvals(@RedisProtocolSupport.Key final CharSequence key) {
        return reservedCnx.hvals(key);
    }

    @Override
    public Future<Long> incr(@RedisProtocolSupport.Key final CharSequence key) {
        return reservedCnx.incr(key);
    }

    @Override
    public Future<Long> incrby(@RedisProtocolSupport.Key final CharSequence key, final long increment) {
        return reservedCnx.incrby(key, increment);
    }

    @Override
    public Future<Double> incrbyfloat(@RedisProtocolSupport.Key final CharSequence key, final double increment) {
        return reservedCnx.incrbyfloat(key, increment);
    }

    @Override
    public Future<String> info() {
        return reservedCnx.info();
    }

    @Override
    public Future<String> info(@Nullable final CharSequence section) {
        return reservedCnx.info(section);
    }

    @Override
    public <T> Future<List<T>> keys(final CharSequence pattern) {
        return reservedCnx.keys(pattern);
    }

    @Override
    public Future<Long> lastsave() {
        return reservedCnx.lastsave();
    }

    @Override
    public Future<String> lindex(@RedisProtocolSupport.Key final CharSequence key, final long index) {
        return reservedCnx.lindex(key, index);
    }

    @Override
    public Future<Long> linsert(@RedisProtocolSupport.Key final CharSequence key,
                                final RedisProtocolSupport.LinsertWhere where, final CharSequence pivot,
                                final CharSequence value) {
        return reservedCnx.linsert(key, where, pivot, value);
    }

    @Override
    public Future<Long> llen(@RedisProtocolSupport.Key final CharSequence key) {
        return reservedCnx.llen(key);
    }

    @Override
    public Future<String> lpop(@RedisProtocolSupport.Key final CharSequence key) {
        return reservedCnx.lpop(key);
    }

    @Override
    public Future<Long> lpush(@RedisProtocolSupport.Key final CharSequence key, final CharSequence value) {
        return reservedCnx.lpush(key, value);
    }

    @Override
    public Future<Long> lpush(@RedisProtocolSupport.Key final CharSequence key, final CharSequence value1,
                              final CharSequence value2) {
        return reservedCnx.lpush(key, value1, value2);
    }

    @Override
    public Future<Long> lpush(@RedisProtocolSupport.Key final CharSequence key, final CharSequence value1,
                              final CharSequence value2, final CharSequence value3) {
        return reservedCnx.lpush(key, value1, value2, value3);
    }

    @Override
    public Future<Long> lpush(@RedisProtocolSupport.Key final CharSequence key,
                              final Collection<? extends CharSequence> values) {
        return reservedCnx.lpush(key, values);
    }

    @Override
    public Future<Long> lpushx(@RedisProtocolSupport.Key final CharSequence key, final CharSequence value) {
        return reservedCnx.lpushx(key, value);
    }

    @Override
    public <T> Future<List<T>> lrange(@RedisProtocolSupport.Key final CharSequence key, final long start,
                                      final long stop) {
        return reservedCnx.lrange(key, start, stop);
    }

    @Override
    public Future<Long> lrem(@RedisProtocolSupport.Key final CharSequence key, final long count,
                             final CharSequence value) {
        return reservedCnx.lrem(key, count, value);
    }

    @Override
    public Future<String> lset(@RedisProtocolSupport.Key final CharSequence key, final long index,
                               final CharSequence value) {
        return reservedCnx.lset(key, index, value);
    }

    @Override
    public Future<String> ltrim(@RedisProtocolSupport.Key final CharSequence key, final long start, final long stop) {
        return reservedCnx.ltrim(key, start, stop);
    }

    @Override
    public Future<String> memoryDoctor() {
        return reservedCnx.memoryDoctor();
    }

    @Override
    public <T> Future<List<T>> memoryHelp() {
        return reservedCnx.memoryHelp();
    }

    @Override
    public Future<String> memoryMallocStats() {
        return reservedCnx.memoryMallocStats();
    }

    @Override
    public Future<String> memoryPurge() {
        return reservedCnx.memoryPurge();
    }

    @Override
    public <T> Future<List<T>> memoryStats() {
        return reservedCnx.memoryStats();
    }

    @Override
    public Future<Long> memoryUsage(@RedisProtocolSupport.Key final CharSequence key) {
        return reservedCnx.memoryUsage(key);
    }

    @Override
    public Future<Long> memoryUsage(@RedisProtocolSupport.Key final CharSequence key,
                                    @Nullable final Long samplesCount) {
        return reservedCnx.memoryUsage(key, samplesCount);
    }

    @Override
    public <T> Future<List<T>> mget(@RedisProtocolSupport.Key final CharSequence key) {
        return reservedCnx.mget(key);
    }

    @Override
    public <T> Future<List<T>> mget(@RedisProtocolSupport.Key final CharSequence key1,
                                    @RedisProtocolSupport.Key final CharSequence key2) {
        return reservedCnx.mget(key1, key2);
    }

    @Override
    public <T> Future<List<T>> mget(@RedisProtocolSupport.Key final CharSequence key1,
                                    @RedisProtocolSupport.Key final CharSequence key2,
                                    @RedisProtocolSupport.Key final CharSequence key3) {
        return reservedCnx.mget(key1, key2, key3);
    }

    @Override
    public <T> Future<List<T>> mget(@RedisProtocolSupport.Key final Collection<? extends CharSequence> keys) {
        return reservedCnx.mget(keys);
    }

    @Override
    public Future<Long> move(@RedisProtocolSupport.Key final CharSequence key, final long db) {
        return reservedCnx.move(key, db);
    }

    @Override
    public Future<String> mset(@RedisProtocolSupport.Key final CharSequence key, final CharSequence value) {
        return reservedCnx.mset(key, value);
    }

    @Override
    public Future<String> mset(@RedisProtocolSupport.Key final CharSequence key1, final CharSequence value1,
                               @RedisProtocolSupport.Key final CharSequence key2, final CharSequence value2) {
        return reservedCnx.mset(key1, value1, key2, value2);
    }

    @Override
    public Future<String> mset(@RedisProtocolSupport.Key final CharSequence key1, final CharSequence value1,
                               @RedisProtocolSupport.Key final CharSequence key2, final CharSequence value2,
                               @RedisProtocolSupport.Key final CharSequence key3, final CharSequence value3) {
        return reservedCnx.mset(key1, value1, key2, value2, key3, value3);
    }

    @Override
    public Future<String> mset(final Collection<RedisProtocolSupport.KeyValue> keyValues) {
        return reservedCnx.mset(keyValues);
    }

    @Override
    public Future<Long> msetnx(@RedisProtocolSupport.Key final CharSequence key, final CharSequence value) {
        return reservedCnx.msetnx(key, value);
    }

    @Override
    public Future<Long> msetnx(@RedisProtocolSupport.Key final CharSequence key1, final CharSequence value1,
                               @RedisProtocolSupport.Key final CharSequence key2, final CharSequence value2) {
        return reservedCnx.msetnx(key1, value1, key2, value2);
    }

    @Override
    public Future<Long> msetnx(@RedisProtocolSupport.Key final CharSequence key1, final CharSequence value1,
                               @RedisProtocolSupport.Key final CharSequence key2, final CharSequence value2,
                               @RedisProtocolSupport.Key final CharSequence key3, final CharSequence value3) {
        return reservedCnx.msetnx(key1, value1, key2, value2, key3, value3);
    }

    @Override
    public Future<Long> msetnx(final Collection<RedisProtocolSupport.KeyValue> keyValues) {
        return reservedCnx.msetnx(keyValues);
    }

    @Override
    public Future<String> objectEncoding(@RedisProtocolSupport.Key final CharSequence key) {
        return reservedCnx.objectEncoding(key);
    }

    @Override
    public Future<Long> objectFreq(@RedisProtocolSupport.Key final CharSequence key) {
        return reservedCnx.objectFreq(key);
    }

    @Override
    public Future<List<String>> objectHelp() {
        return reservedCnx.objectHelp();
    }

    @Override
    public Future<Long> objectIdletime(@RedisProtocolSupport.Key final CharSequence key) {
        return reservedCnx.objectIdletime(key);
    }

    @Override
    public Future<Long> objectRefcount(@RedisProtocolSupport.Key final CharSequence key) {
        return reservedCnx.objectRefcount(key);
    }

    @Override
    public Future<Long> persist(@RedisProtocolSupport.Key final CharSequence key) {
        return reservedCnx.persist(key);
    }

    @Override
    public Future<Long> pexpire(@RedisProtocolSupport.Key final CharSequence key, final long milliseconds) {
        return reservedCnx.pexpire(key, milliseconds);
    }

    @Override
    public Future<Long> pexpireat(@RedisProtocolSupport.Key final CharSequence key, final long millisecondsTimestamp) {
        return reservedCnx.pexpireat(key, millisecondsTimestamp);
    }

    @Override
    public Future<Long> pfadd(@RedisProtocolSupport.Key final CharSequence key, final CharSequence element) {
        return reservedCnx.pfadd(key, element);
    }

    @Override
    public Future<Long> pfadd(@RedisProtocolSupport.Key final CharSequence key, final CharSequence element1,
                              final CharSequence element2) {
        return reservedCnx.pfadd(key, element1, element2);
    }

    @Override
    public Future<Long> pfadd(@RedisProtocolSupport.Key final CharSequence key, final CharSequence element1,
                              final CharSequence element2, final CharSequence element3) {
        return reservedCnx.pfadd(key, element1, element2, element3);
    }

    @Override
    public Future<Long> pfadd(@RedisProtocolSupport.Key final CharSequence key,
                              final Collection<? extends CharSequence> elements) {
        return reservedCnx.pfadd(key, elements);
    }

    @Override
    public Future<Long> pfcount(@RedisProtocolSupport.Key final CharSequence key) {
        return reservedCnx.pfcount(key);
    }

    @Override
    public Future<Long> pfcount(@RedisProtocolSupport.Key final CharSequence key1,
                                @RedisProtocolSupport.Key final CharSequence key2) {
        return reservedCnx.pfcount(key1, key2);
    }

    @Override
    public Future<Long> pfcount(@RedisProtocolSupport.Key final CharSequence key1,
                                @RedisProtocolSupport.Key final CharSequence key2,
                                @RedisProtocolSupport.Key final CharSequence key3) {
        return reservedCnx.pfcount(key1, key2, key3);
    }

    @Override
    public Future<Long> pfcount(@RedisProtocolSupport.Key final Collection<? extends CharSequence> keys) {
        return reservedCnx.pfcount(keys);
    }

    @Override
    public Future<String> pfmerge(@RedisProtocolSupport.Key final CharSequence destkey,
                                  @RedisProtocolSupport.Key final CharSequence sourcekey) {
        return reservedCnx.pfmerge(destkey, sourcekey);
    }

    @Override
    public Future<String> pfmerge(@RedisProtocolSupport.Key final CharSequence destkey,
                                  @RedisProtocolSupport.Key final CharSequence sourcekey1,
                                  @RedisProtocolSupport.Key final CharSequence sourcekey2) {
        return reservedCnx.pfmerge(destkey, sourcekey1, sourcekey2);
    }

    @Override
    public Future<String> pfmerge(@RedisProtocolSupport.Key final CharSequence destkey,
                                  @RedisProtocolSupport.Key final CharSequence sourcekey1,
                                  @RedisProtocolSupport.Key final CharSequence sourcekey2,
                                  @RedisProtocolSupport.Key final CharSequence sourcekey3) {
        return reservedCnx.pfmerge(destkey, sourcekey1, sourcekey2, sourcekey3);
    }

    @Override
    public Future<String> pfmerge(@RedisProtocolSupport.Key final CharSequence destkey,
                                  @RedisProtocolSupport.Key final Collection<? extends CharSequence> sourcekeys) {
        return reservedCnx.pfmerge(destkey, sourcekeys);
    }

    @Override
    public Future<String> ping() {
        return reservedCnx.ping();
    }

    @Override
    public Future<String> ping(final CharSequence message) {
        return reservedCnx.ping(message);
    }

    @Override
    public Future<String> psetex(@RedisProtocolSupport.Key final CharSequence key, final long milliseconds,
                                 final CharSequence value) {
        return reservedCnx.psetex(key, milliseconds, value);
    }

    @Override
    public Future<Long> pttl(@RedisProtocolSupport.Key final CharSequence key) {
        return reservedCnx.pttl(key);
    }

    @Override
    public Future<Long> publish(final CharSequence channel, final CharSequence message) {
        return reservedCnx.publish(channel, message);
    }

    @Override
    public Future<List<String>> pubsubChannels() {
        return reservedCnx.pubsubChannels();
    }

    @Override
    public Future<List<String>> pubsubChannels(@Nullable final CharSequence pattern) {
        return reservedCnx.pubsubChannels(pattern);
    }

    @Override
    public Future<List<String>> pubsubChannels(@Nullable final CharSequence pattern1,
                                               @Nullable final CharSequence pattern2) {
        return reservedCnx.pubsubChannels(pattern1, pattern2);
    }

    @Override
    public Future<List<String>> pubsubChannels(@Nullable final CharSequence pattern1,
                                               @Nullable final CharSequence pattern2,
                                               @Nullable final CharSequence pattern3) {
        return reservedCnx.pubsubChannels(pattern1, pattern2, pattern3);
    }

    @Override
    public Future<List<String>> pubsubChannels(final Collection<? extends CharSequence> patterns) {
        return reservedCnx.pubsubChannels(patterns);
    }

    @Override
    public <T> Future<List<T>> pubsubNumsub() {
        return reservedCnx.pubsubNumsub();
    }

    @Override
    public <T> Future<List<T>> pubsubNumsub(@Nullable final CharSequence channel) {
        return reservedCnx.pubsubNumsub(channel);
    }

    @Override
    public <T> Future<List<T>> pubsubNumsub(@Nullable final CharSequence channel1,
                                            @Nullable final CharSequence channel2) {
        return reservedCnx.pubsubNumsub(channel1, channel2);
    }

    @Override
    public <T> Future<List<T>> pubsubNumsub(@Nullable final CharSequence channel1,
                                            @Nullable final CharSequence channel2,
                                            @Nullable final CharSequence channel3) {
        return reservedCnx.pubsubNumsub(channel1, channel2, channel3);
    }

    @Override
    public <T> Future<List<T>> pubsubNumsub(final Collection<? extends CharSequence> channels) {
        return reservedCnx.pubsubNumsub(channels);
    }

    @Override
    public Future<Long> pubsubNumpat() {
        return reservedCnx.pubsubNumpat();
    }

    @Override
    public Future<String> randomkey() {
        return reservedCnx.randomkey();
    }

    @Override
    public Future<String> readonly() {
        return reservedCnx.readonly();
    }

    @Override
    public Future<String> readwrite() {
        return reservedCnx.readwrite();
    }

    @Override
    public Future<String> rename(@RedisProtocolSupport.Key final CharSequence key,
                                 @RedisProtocolSupport.Key final CharSequence newkey) {
        return reservedCnx.rename(key, newkey);
    }

    @Override
    public Future<Long> renamenx(@RedisProtocolSupport.Key final CharSequence key,
                                 @RedisProtocolSupport.Key final CharSequence newkey) {
        return reservedCnx.renamenx(key, newkey);
    }

    @Override
    public Future<String> restore(@RedisProtocolSupport.Key final CharSequence key, final long ttl,
                                  final CharSequence serializedValue) {
        return reservedCnx.restore(key, ttl, serializedValue);
    }

    @Override
    public Future<String> restore(@RedisProtocolSupport.Key final CharSequence key, final long ttl,
                                  final CharSequence serializedValue,
                                  @Nullable final RedisProtocolSupport.RestoreReplace replace) {
        return reservedCnx.restore(key, ttl, serializedValue, replace);
    }

    @Override
    public <T> Future<List<T>> role() {
        return reservedCnx.role();
    }

    @Override
    public Future<String> rpop(@RedisProtocolSupport.Key final CharSequence key) {
        return reservedCnx.rpop(key);
    }

    @Override
    public Future<String> rpoplpush(@RedisProtocolSupport.Key final CharSequence source,
                                    @RedisProtocolSupport.Key final CharSequence destination) {
        return reservedCnx.rpoplpush(source, destination);
    }

    @Override
    public Future<Long> rpush(@RedisProtocolSupport.Key final CharSequence key, final CharSequence value) {
        return reservedCnx.rpush(key, value);
    }

    @Override
    public Future<Long> rpush(@RedisProtocolSupport.Key final CharSequence key, final CharSequence value1,
                              final CharSequence value2) {
        return reservedCnx.rpush(key, value1, value2);
    }

    @Override
    public Future<Long> rpush(@RedisProtocolSupport.Key final CharSequence key, final CharSequence value1,
                              final CharSequence value2, final CharSequence value3) {
        return reservedCnx.rpush(key, value1, value2, value3);
    }

    @Override
    public Future<Long> rpush(@RedisProtocolSupport.Key final CharSequence key,
                              final Collection<? extends CharSequence> values) {
        return reservedCnx.rpush(key, values);
    }

    @Override
    public Future<Long> rpushx(@RedisProtocolSupport.Key final CharSequence key, final CharSequence value) {
        return reservedCnx.rpushx(key, value);
    }

    @Override
    public Future<Long> sadd(@RedisProtocolSupport.Key final CharSequence key, final CharSequence member) {
        return reservedCnx.sadd(key, member);
    }

    @Override
    public Future<Long> sadd(@RedisProtocolSupport.Key final CharSequence key, final CharSequence member1,
                             final CharSequence member2) {
        return reservedCnx.sadd(key, member1, member2);
    }

    @Override
    public Future<Long> sadd(@RedisProtocolSupport.Key final CharSequence key, final CharSequence member1,
                             final CharSequence member2, final CharSequence member3) {
        return reservedCnx.sadd(key, member1, member2, member3);
    }

    @Override
    public Future<Long> sadd(@RedisProtocolSupport.Key final CharSequence key,
                             final Collection<? extends CharSequence> members) {
        return reservedCnx.sadd(key, members);
    }

    @Override
    public Future<String> save() {
        return reservedCnx.save();
    }

    @Override
    public <T> Future<List<T>> scan(final long cursor) {
        return reservedCnx.scan(cursor);
    }

    @Override
    public <T> Future<List<T>> scan(final long cursor, @Nullable final CharSequence matchPattern,
                                    @Nullable final Long count) {
        return reservedCnx.scan(cursor, matchPattern, count);
    }

    @Override
    public Future<Long> scard(@RedisProtocolSupport.Key final CharSequence key) {
        return reservedCnx.scard(key);
    }

    @Override
    public Future<String> scriptDebug(final RedisProtocolSupport.ScriptDebugMode mode) {
        return reservedCnx.scriptDebug(mode);
    }

    @Override
    public <T> Future<List<T>> scriptExists(final CharSequence sha1) {
        return reservedCnx.scriptExists(sha1);
    }

    @Override
    public <T> Future<List<T>> scriptExists(final CharSequence sha11, final CharSequence sha12) {
        return reservedCnx.scriptExists(sha11, sha12);
    }

    @Override
    public <T> Future<List<T>> scriptExists(final CharSequence sha11, final CharSequence sha12,
                                            final CharSequence sha13) {
        return reservedCnx.scriptExists(sha11, sha12, sha13);
    }

    @Override
    public <T> Future<List<T>> scriptExists(final Collection<? extends CharSequence> sha1s) {
        return reservedCnx.scriptExists(sha1s);
    }

    @Override
    public Future<String> scriptFlush() {
        return reservedCnx.scriptFlush();
    }

    @Override
    public Future<String> scriptKill() {
        return reservedCnx.scriptKill();
    }

    @Override
    public Future<String> scriptLoad(final CharSequence script) {
        return reservedCnx.scriptLoad(script);
    }

    @Override
    public <T> Future<List<T>> sdiff(@RedisProtocolSupport.Key final CharSequence firstkey) {
        return reservedCnx.sdiff(firstkey);
    }

    @Override
    public <T> Future<List<T>> sdiff(@RedisProtocolSupport.Key final CharSequence firstkey,
                                     @Nullable @RedisProtocolSupport.Key final CharSequence otherkey) {
        return reservedCnx.sdiff(firstkey, otherkey);
    }

    @Override
    public <T> Future<List<T>> sdiff(@RedisProtocolSupport.Key final CharSequence firstkey,
                                     @Nullable @RedisProtocolSupport.Key final CharSequence otherkey1,
                                     @Nullable @RedisProtocolSupport.Key final CharSequence otherkey2) {
        return reservedCnx.sdiff(firstkey, otherkey1, otherkey2);
    }

    @Override
    public <T> Future<List<T>> sdiff(@RedisProtocolSupport.Key final CharSequence firstkey,
                                     @Nullable @RedisProtocolSupport.Key final CharSequence otherkey1,
                                     @Nullable @RedisProtocolSupport.Key final CharSequence otherkey2,
                                     @Nullable @RedisProtocolSupport.Key final CharSequence otherkey3) {
        return reservedCnx.sdiff(firstkey, otherkey1, otherkey2, otherkey3);
    }

    @Override
    public <T> Future<List<T>> sdiff(@RedisProtocolSupport.Key final CharSequence firstkey,
                                     @RedisProtocolSupport.Key final Collection<? extends CharSequence> otherkeys) {
        return reservedCnx.sdiff(firstkey, otherkeys);
    }

    @Override
    public Future<Long> sdiffstore(@RedisProtocolSupport.Key final CharSequence destination,
                                   @RedisProtocolSupport.Key final CharSequence firstkey) {
        return reservedCnx.sdiffstore(destination, firstkey);
    }

    @Override
    public Future<Long> sdiffstore(@RedisProtocolSupport.Key final CharSequence destination,
                                   @RedisProtocolSupport.Key final CharSequence firstkey,
                                   @Nullable @RedisProtocolSupport.Key final CharSequence otherkey) {
        return reservedCnx.sdiffstore(destination, firstkey, otherkey);
    }

    @Override
    public Future<Long> sdiffstore(@RedisProtocolSupport.Key final CharSequence destination,
                                   @RedisProtocolSupport.Key final CharSequence firstkey,
                                   @Nullable @RedisProtocolSupport.Key final CharSequence otherkey1,
                                   @Nullable @RedisProtocolSupport.Key final CharSequence otherkey2) {
        return reservedCnx.sdiffstore(destination, firstkey, otherkey1, otherkey2);
    }

    @Override
    public Future<Long> sdiffstore(@RedisProtocolSupport.Key final CharSequence destination,
                                   @RedisProtocolSupport.Key final CharSequence firstkey,
                                   @Nullable @RedisProtocolSupport.Key final CharSequence otherkey1,
                                   @Nullable @RedisProtocolSupport.Key final CharSequence otherkey2,
                                   @Nullable @RedisProtocolSupport.Key final CharSequence otherkey3) {
        return reservedCnx.sdiffstore(destination, firstkey, otherkey1, otherkey2, otherkey3);
    }

    @Override
    public Future<Long> sdiffstore(@RedisProtocolSupport.Key final CharSequence destination,
                                   @RedisProtocolSupport.Key final CharSequence firstkey,
                                   @RedisProtocolSupport.Key final Collection<? extends CharSequence> otherkeys) {
        return reservedCnx.sdiffstore(destination, firstkey, otherkeys);
    }

    @Override
    public Future<String> select(final long index) {
        return reservedCnx.select(index);
    }

    @Override
    public Future<String> set(@RedisProtocolSupport.Key final CharSequence key, final CharSequence value) {
        return reservedCnx.set(key, value);
    }

    @Override
    public Future<String> set(@RedisProtocolSupport.Key final CharSequence key, final CharSequence value,
                              @Nullable final RedisProtocolSupport.ExpireDuration expireDuration,
                              @Nullable final RedisProtocolSupport.SetCondition condition) {
        return reservedCnx.set(key, value, expireDuration, condition);
    }

    @Override
    public Future<Long> setbit(@RedisProtocolSupport.Key final CharSequence key, final long offset,
                               final CharSequence value) {
        return reservedCnx.setbit(key, offset, value);
    }

    @Override
    public Future<String> setex(@RedisProtocolSupport.Key final CharSequence key, final long seconds,
                                final CharSequence value) {
        return reservedCnx.setex(key, seconds, value);
    }

    @Override
    public Future<Long> setnx(@RedisProtocolSupport.Key final CharSequence key, final CharSequence value) {
        return reservedCnx.setnx(key, value);
    }

    @Override
    public Future<Long> setrange(@RedisProtocolSupport.Key final CharSequence key, final long offset,
                                 final CharSequence value) {
        return reservedCnx.setrange(key, offset, value);
    }

    @Override
    public Future<String> shutdown() {
        return reservedCnx.shutdown();
    }

    @Override
    public Future<String> shutdown(@Nullable final RedisProtocolSupport.ShutdownSaveMode saveMode) {
        return reservedCnx.shutdown(saveMode);
    }

    @Override
    public <T> Future<List<T>> sinter(@RedisProtocolSupport.Key final CharSequence key) {
        return reservedCnx.sinter(key);
    }

    @Override
    public <T> Future<List<T>> sinter(@RedisProtocolSupport.Key final CharSequence key1,
                                      @RedisProtocolSupport.Key final CharSequence key2) {
        return reservedCnx.sinter(key1, key2);
    }

    @Override
    public <T> Future<List<T>> sinter(@RedisProtocolSupport.Key final CharSequence key1,
                                      @RedisProtocolSupport.Key final CharSequence key2,
                                      @RedisProtocolSupport.Key final CharSequence key3) {
        return reservedCnx.sinter(key1, key2, key3);
    }

    @Override
    public <T> Future<List<T>> sinter(@RedisProtocolSupport.Key final Collection<? extends CharSequence> keys) {
        return reservedCnx.sinter(keys);
    }

    @Override
    public Future<Long> sinterstore(@RedisProtocolSupport.Key final CharSequence destination,
                                    @RedisProtocolSupport.Key final CharSequence key) {
        return reservedCnx.sinterstore(destination, key);
    }

    @Override
    public Future<Long> sinterstore(@RedisProtocolSupport.Key final CharSequence destination,
                                    @RedisProtocolSupport.Key final CharSequence key1,
                                    @RedisProtocolSupport.Key final CharSequence key2) {
        return reservedCnx.sinterstore(destination, key1, key2);
    }

    @Override
    public Future<Long> sinterstore(@RedisProtocolSupport.Key final CharSequence destination,
                                    @RedisProtocolSupport.Key final CharSequence key1,
                                    @RedisProtocolSupport.Key final CharSequence key2,
                                    @RedisProtocolSupport.Key final CharSequence key3) {
        return reservedCnx.sinterstore(destination, key1, key2, key3);
    }

    @Override
    public Future<Long> sinterstore(@RedisProtocolSupport.Key final CharSequence destination,
                                    @RedisProtocolSupport.Key final Collection<? extends CharSequence> keys) {
        return reservedCnx.sinterstore(destination, keys);
    }

    @Override
    public Future<Long> sismember(@RedisProtocolSupport.Key final CharSequence key, final CharSequence member) {
        return reservedCnx.sismember(key, member);
    }

    @Override
    public Future<String> slaveof(final CharSequence host, final CharSequence port) {
        return reservedCnx.slaveof(host, port);
    }

    @Override
    public <T> Future<List<T>> slowlog(final CharSequence subcommand) {
        return reservedCnx.slowlog(subcommand);
    }

    @Override
    public <T> Future<List<T>> slowlog(final CharSequence subcommand, @Nullable final CharSequence argument) {
        return reservedCnx.slowlog(subcommand, argument);
    }

    @Override
    public <T> Future<List<T>> smembers(@RedisProtocolSupport.Key final CharSequence key) {
        return reservedCnx.smembers(key);
    }

    @Override
    public Future<Long> smove(@RedisProtocolSupport.Key final CharSequence source,
                              @RedisProtocolSupport.Key final CharSequence destination, final CharSequence member) {
        return reservedCnx.smove(source, destination, member);
    }

    @Override
    public <T> Future<List<T>> sort(@RedisProtocolSupport.Key final CharSequence key) {
        return reservedCnx.sort(key);
    }

    @Override
    public <T> Future<List<T>> sort(@RedisProtocolSupport.Key final CharSequence key,
                                    @Nullable final CharSequence byPattern,
                                    @Nullable final RedisProtocolSupport.OffsetCount offsetCount,
                                    final Collection<? extends CharSequence> getPatterns,
                                    @Nullable final RedisProtocolSupport.SortOrder order,
                                    @Nullable final RedisProtocolSupport.SortSorting sorting) {
        return reservedCnx.sort(key, byPattern, offsetCount, getPatterns, order, sorting);
    }

    @Override
    public Future<Long> sort(@RedisProtocolSupport.Key final CharSequence key,
                             @RedisProtocolSupport.Key final CharSequence storeDestination) {
        return reservedCnx.sort(key, storeDestination);
    }

    @Override
    public Future<Long> sort(@RedisProtocolSupport.Key final CharSequence key,
                             @RedisProtocolSupport.Key final CharSequence storeDestination,
                             @Nullable final CharSequence byPattern,
                             @Nullable final RedisProtocolSupport.OffsetCount offsetCount,
                             final Collection<? extends CharSequence> getPatterns,
                             @Nullable final RedisProtocolSupport.SortOrder order,
                             @Nullable final RedisProtocolSupport.SortSorting sorting) {
        return reservedCnx.sort(key, storeDestination, byPattern, offsetCount, getPatterns, order, sorting);
    }

    @Override
    public Future<String> spop(@RedisProtocolSupport.Key final CharSequence key) {
        return reservedCnx.spop(key);
    }

    @Override
    public Future<String> spop(@RedisProtocolSupport.Key final CharSequence key, @Nullable final Long count) {
        return reservedCnx.spop(key, count);
    }

    @Override
    public Future<String> srandmember(@RedisProtocolSupport.Key final CharSequence key) {
        return reservedCnx.srandmember(key);
    }

    @Override
    public Future<List<String>> srandmember(@RedisProtocolSupport.Key final CharSequence key, final long count) {
        return reservedCnx.srandmember(key, count);
    }

    @Override
    public Future<Long> srem(@RedisProtocolSupport.Key final CharSequence key, final CharSequence member) {
        return reservedCnx.srem(key, member);
    }

    @Override
    public Future<Long> srem(@RedisProtocolSupport.Key final CharSequence key, final CharSequence member1,
                             final CharSequence member2) {
        return reservedCnx.srem(key, member1, member2);
    }

    @Override
    public Future<Long> srem(@RedisProtocolSupport.Key final CharSequence key, final CharSequence member1,
                             final CharSequence member2, final CharSequence member3) {
        return reservedCnx.srem(key, member1, member2, member3);
    }

    @Override
    public Future<Long> srem(@RedisProtocolSupport.Key final CharSequence key,
                             final Collection<? extends CharSequence> members) {
        return reservedCnx.srem(key, members);
    }

    @Override
    public <T> Future<List<T>> sscan(@RedisProtocolSupport.Key final CharSequence key, final long cursor) {
        return reservedCnx.sscan(key, cursor);
    }

    @Override
    public <T> Future<List<T>> sscan(@RedisProtocolSupport.Key final CharSequence key, final long cursor,
                                     @Nullable final CharSequence matchPattern, @Nullable final Long count) {
        return reservedCnx.sscan(key, cursor, matchPattern, count);
    }

    @Override
    public Future<Long> strlen(@RedisProtocolSupport.Key final CharSequence key) {
        return reservedCnx.strlen(key);
    }

    @Override
    public <T> Future<List<T>> sunion(@RedisProtocolSupport.Key final CharSequence key) {
        return reservedCnx.sunion(key);
    }

    @Override
    public <T> Future<List<T>> sunion(@RedisProtocolSupport.Key final CharSequence key1,
                                      @RedisProtocolSupport.Key final CharSequence key2) {
        return reservedCnx.sunion(key1, key2);
    }

    @Override
    public <T> Future<List<T>> sunion(@RedisProtocolSupport.Key final CharSequence key1,
                                      @RedisProtocolSupport.Key final CharSequence key2,
                                      @RedisProtocolSupport.Key final CharSequence key3) {
        return reservedCnx.sunion(key1, key2, key3);
    }

    @Override
    public <T> Future<List<T>> sunion(@RedisProtocolSupport.Key final Collection<? extends CharSequence> keys) {
        return reservedCnx.sunion(keys);
    }

    @Override
    public Future<Long> sunionstore(@RedisProtocolSupport.Key final CharSequence destination,
                                    @RedisProtocolSupport.Key final CharSequence key) {
        return reservedCnx.sunionstore(destination, key);
    }

    @Override
    public Future<Long> sunionstore(@RedisProtocolSupport.Key final CharSequence destination,
                                    @RedisProtocolSupport.Key final CharSequence key1,
                                    @RedisProtocolSupport.Key final CharSequence key2) {
        return reservedCnx.sunionstore(destination, key1, key2);
    }

    @Override
    public Future<Long> sunionstore(@RedisProtocolSupport.Key final CharSequence destination,
                                    @RedisProtocolSupport.Key final CharSequence key1,
                                    @RedisProtocolSupport.Key final CharSequence key2,
                                    @RedisProtocolSupport.Key final CharSequence key3) {
        return reservedCnx.sunionstore(destination, key1, key2, key3);
    }

    @Override
    public Future<Long> sunionstore(@RedisProtocolSupport.Key final CharSequence destination,
                                    @RedisProtocolSupport.Key final Collection<? extends CharSequence> keys) {
        return reservedCnx.sunionstore(destination, keys);
    }

    @Override
    public Future<String> swapdb(final long index, final long index1) {
        return reservedCnx.swapdb(index, index1);
    }

    @Override
    public <T> Future<List<T>> time() {
        return reservedCnx.time();
    }

    @Override
    public Future<Long> touch(@RedisProtocolSupport.Key final CharSequence key) {
        return reservedCnx.touch(key);
    }

    @Override
    public Future<Long> touch(@RedisProtocolSupport.Key final CharSequence key1,
                              @RedisProtocolSupport.Key final CharSequence key2) {
        return reservedCnx.touch(key1, key2);
    }

    @Override
    public Future<Long> touch(@RedisProtocolSupport.Key final CharSequence key1,
                              @RedisProtocolSupport.Key final CharSequence key2,
                              @RedisProtocolSupport.Key final CharSequence key3) {
        return reservedCnx.touch(key1, key2, key3);
    }

    @Override
    public Future<Long> touch(@RedisProtocolSupport.Key final Collection<? extends CharSequence> keys) {
        return reservedCnx.touch(keys);
    }

    @Override
    public Future<Long> ttl(@RedisProtocolSupport.Key final CharSequence key) {
        return reservedCnx.ttl(key);
    }

    @Override
    public Future<String> type(@RedisProtocolSupport.Key final CharSequence key) {
        return reservedCnx.type(key);
    }

    @Override
    public Future<Long> unlink(@RedisProtocolSupport.Key final CharSequence key) {
        return reservedCnx.unlink(key);
    }

    @Override
    public Future<Long> unlink(@RedisProtocolSupport.Key final CharSequence key1,
                               @RedisProtocolSupport.Key final CharSequence key2) {
        return reservedCnx.unlink(key1, key2);
    }

    @Override
    public Future<Long> unlink(@RedisProtocolSupport.Key final CharSequence key1,
                               @RedisProtocolSupport.Key final CharSequence key2,
                               @RedisProtocolSupport.Key final CharSequence key3) {
        return reservedCnx.unlink(key1, key2, key3);
    }

    @Override
    public Future<Long> unlink(@RedisProtocolSupport.Key final Collection<? extends CharSequence> keys) {
        return reservedCnx.unlink(keys);
    }

    @Override
    public Future<String> unwatch() {
        return reservedCnx.unwatch();
    }

    @Override
    public Future<Long> wait(final long numslaves, final long timeout) {
        return reservedCnx.wait(numslaves, timeout);
    }

    @Override
    public Future<String> watch(@RedisProtocolSupport.Key final CharSequence key) {
        return reservedCnx.watch(key);
    }

    @Override
    public Future<String> watch(@RedisProtocolSupport.Key final CharSequence key1,
                                @RedisProtocolSupport.Key final CharSequence key2) {
        return reservedCnx.watch(key1, key2);
    }

    @Override
    public Future<String> watch(@RedisProtocolSupport.Key final CharSequence key1,
                                @RedisProtocolSupport.Key final CharSequence key2,
                                @RedisProtocolSupport.Key final CharSequence key3) {
        return reservedCnx.watch(key1, key2, key3);
    }

    @Override
    public Future<String> watch(@RedisProtocolSupport.Key final Collection<? extends CharSequence> keys) {
        return reservedCnx.watch(keys);
    }

    @Override
    public Future<String> xadd(@RedisProtocolSupport.Key final CharSequence key, final CharSequence id,
                               final CharSequence field, final CharSequence value) {
        return reservedCnx.xadd(key, id, field, value);
    }

    @Override
    public Future<String> xadd(@RedisProtocolSupport.Key final CharSequence key, final CharSequence id,
                               final CharSequence field1, final CharSequence value1, final CharSequence field2,
                               final CharSequence value2) {
        return reservedCnx.xadd(key, id, field1, value1, field2, value2);
    }

    @Override
    public Future<String> xadd(@RedisProtocolSupport.Key final CharSequence key, final CharSequence id,
                               final CharSequence field1, final CharSequence value1, final CharSequence field2,
                               final CharSequence value2, final CharSequence field3, final CharSequence value3) {
        return reservedCnx.xadd(key, id, field1, value1, field2, value2, field3, value3);
    }

    @Override
    public Future<String> xadd(@RedisProtocolSupport.Key final CharSequence key, final CharSequence id,
                               final Collection<RedisProtocolSupport.FieldValue> fieldValues) {
        return reservedCnx.xadd(key, id, fieldValues);
    }

    @Override
    public Future<Long> xlen(@RedisProtocolSupport.Key final CharSequence key) {
        return reservedCnx.xlen(key);
    }

    @Override
    public <T> Future<List<T>> xpending(@RedisProtocolSupport.Key final CharSequence key, final CharSequence group) {
        return reservedCnx.xpending(key, group);
    }

    @Override
    public <T> Future<List<T>> xpending(@RedisProtocolSupport.Key final CharSequence key, final CharSequence group,
                                        @Nullable final CharSequence start, @Nullable final CharSequence end,
                                        @Nullable final Long count, @Nullable final CharSequence consumer) {
        return reservedCnx.xpending(key, group, start, end, count, consumer);
    }

    @Override
    public <T> Future<List<T>> xrange(@RedisProtocolSupport.Key final CharSequence key, final CharSequence start,
                                      final CharSequence end) {
        return reservedCnx.xrange(key, start, end);
    }

    @Override
    public <T> Future<List<T>> xrange(@RedisProtocolSupport.Key final CharSequence key, final CharSequence start,
                                      final CharSequence end, @Nullable final Long count) {
        return reservedCnx.xrange(key, start, end, count);
    }

    @Override
    public <T> Future<List<T>> xread(@RedisProtocolSupport.Key final Collection<? extends CharSequence> keys,
                                     final Collection<? extends CharSequence> ids) {
        return reservedCnx.xread(keys, ids);
    }

    @Override
    public <T> Future<List<T>> xread(@Nullable final Long count, @Nullable final Long blockMilliseconds,
                                     @RedisProtocolSupport.Key final Collection<? extends CharSequence> keys,
                                     final Collection<? extends CharSequence> ids) {
        return reservedCnx.xread(count, blockMilliseconds, keys, ids);
    }

    @Override
    public <T> Future<List<T>> xreadgroup(final RedisProtocolSupport.GroupConsumer groupConsumer,
                                          @RedisProtocolSupport.Key final Collection<? extends CharSequence> keys,
                                          final Collection<? extends CharSequence> ids) {
        return reservedCnx.xreadgroup(groupConsumer, keys, ids);
    }

    @Override
    public <T> Future<List<T>> xreadgroup(final RedisProtocolSupport.GroupConsumer groupConsumer,
                                          @Nullable final Long count, @Nullable final Long blockMilliseconds,
                                          @RedisProtocolSupport.Key final Collection<? extends CharSequence> keys,
                                          final Collection<? extends CharSequence> ids) {
        return reservedCnx.xreadgroup(groupConsumer, count, blockMilliseconds, keys, ids);
    }

    @Override
    public <T> Future<List<T>> xrevrange(@RedisProtocolSupport.Key final CharSequence key, final CharSequence end,
                                         final CharSequence start) {
        return reservedCnx.xrevrange(key, end, start);
    }

    @Override
    public <T> Future<List<T>> xrevrange(@RedisProtocolSupport.Key final CharSequence key, final CharSequence end,
                                         final CharSequence start, @Nullable final Long count) {
        return reservedCnx.xrevrange(key, end, start, count);
    }

    @Override
    public Future<Long> zadd(@RedisProtocolSupport.Key final CharSequence key,
                             final Collection<RedisProtocolSupport.ScoreMember> scoreMembers) {
        return reservedCnx.zadd(key, scoreMembers);
    }

    @Override
    public Future<Long> zadd(@RedisProtocolSupport.Key final CharSequence key,
                             @Nullable final RedisProtocolSupport.ZaddCondition condition,
                             @Nullable final RedisProtocolSupport.ZaddChange change, final double score,
                             final CharSequence member) {
        return reservedCnx.zadd(key, condition, change, score, member);
    }

    @Override
    public Future<Long> zadd(@RedisProtocolSupport.Key final CharSequence key,
                             @Nullable final RedisProtocolSupport.ZaddCondition condition,
                             @Nullable final RedisProtocolSupport.ZaddChange change, final double score1,
                             final CharSequence member1, final double score2, final CharSequence member2) {
        return reservedCnx.zadd(key, condition, change, score1, member1, score2, member2);
    }

    @Override
    public Future<Long> zadd(@RedisProtocolSupport.Key final CharSequence key,
                             @Nullable final RedisProtocolSupport.ZaddCondition condition,
                             @Nullable final RedisProtocolSupport.ZaddChange change, final double score1,
                             final CharSequence member1, final double score2, final CharSequence member2,
                             final double score3, final CharSequence member3) {
        return reservedCnx.zadd(key, condition, change, score1, member1, score2, member2, score3, member3);
    }

    @Override
    public Future<Long> zadd(@RedisProtocolSupport.Key final CharSequence key,
                             @Nullable final RedisProtocolSupport.ZaddCondition condition,
                             @Nullable final RedisProtocolSupport.ZaddChange change,
                             final Collection<RedisProtocolSupport.ScoreMember> scoreMembers) {
        return reservedCnx.zadd(key, condition, change, scoreMembers);
    }

    @Override
    public Future<Double> zaddIncr(@RedisProtocolSupport.Key final CharSequence key,
                                   final Collection<RedisProtocolSupport.ScoreMember> scoreMembers) {
        return reservedCnx.zaddIncr(key, scoreMembers);
    }

    @Override
    public Future<Double> zaddIncr(@RedisProtocolSupport.Key final CharSequence key,
                                   @Nullable final RedisProtocolSupport.ZaddCondition condition,
                                   @Nullable final RedisProtocolSupport.ZaddChange change, final double score,
                                   final CharSequence member) {
        return reservedCnx.zaddIncr(key, condition, change, score, member);
    }

    @Override
    public Future<Double> zaddIncr(@RedisProtocolSupport.Key final CharSequence key,
                                   @Nullable final RedisProtocolSupport.ZaddCondition condition,
                                   @Nullable final RedisProtocolSupport.ZaddChange change, final double score1,
                                   final CharSequence member1, final double score2, final CharSequence member2) {
        return reservedCnx.zaddIncr(key, condition, change, score1, member1, score2, member2);
    }

    @Override
    public Future<Double> zaddIncr(@RedisProtocolSupport.Key final CharSequence key,
                                   @Nullable final RedisProtocolSupport.ZaddCondition condition,
                                   @Nullable final RedisProtocolSupport.ZaddChange change, final double score1,
                                   final CharSequence member1, final double score2, final CharSequence member2,
                                   final double score3, final CharSequence member3) {
        return reservedCnx.zaddIncr(key, condition, change, score1, member1, score2, member2, score3, member3);
    }

    @Override
    public Future<Double> zaddIncr(@RedisProtocolSupport.Key final CharSequence key,
                                   @Nullable final RedisProtocolSupport.ZaddCondition condition,
                                   @Nullable final RedisProtocolSupport.ZaddChange change,
                                   final Collection<RedisProtocolSupport.ScoreMember> scoreMembers) {
        return reservedCnx.zaddIncr(key, condition, change, scoreMembers);
    }

    @Override
    public Future<Long> zcard(@RedisProtocolSupport.Key final CharSequence key) {
        return reservedCnx.zcard(key);
    }

    @Override
    public Future<Long> zcount(@RedisProtocolSupport.Key final CharSequence key, final double min, final double max) {
        return reservedCnx.zcount(key, min, max);
    }

    @Override
    public Future<Double> zincrby(@RedisProtocolSupport.Key final CharSequence key, final long increment,
                                  final CharSequence member) {
        return reservedCnx.zincrby(key, increment, member);
    }

    @Override
    public Future<Long> zinterstore(@RedisProtocolSupport.Key final CharSequence destination, final long numkeys,
                                    @RedisProtocolSupport.Key final Collection<? extends CharSequence> keys) {
        return reservedCnx.zinterstore(destination, numkeys, keys);
    }

    @Override
    public Future<Long> zinterstore(@RedisProtocolSupport.Key final CharSequence destination, final long numkeys,
                                    @RedisProtocolSupport.Key final Collection<? extends CharSequence> keys,
                                    final Collection<Long> weightses,
                                    @Nullable final RedisProtocolSupport.ZinterstoreAggregate aggregate) {
        return reservedCnx.zinterstore(destination, numkeys, keys, weightses, aggregate);
    }

    @Override
    public Future<Long> zlexcount(@RedisProtocolSupport.Key final CharSequence key, final CharSequence min,
                                  final CharSequence max) {
        return reservedCnx.zlexcount(key, min, max);
    }

    @Override
    public <T> Future<List<T>> zpopmax(@RedisProtocolSupport.Key final CharSequence key) {
        return reservedCnx.zpopmax(key);
    }

    @Override
    public <T> Future<List<T>> zpopmax(@RedisProtocolSupport.Key final CharSequence key, @Nullable final Long count) {
        return reservedCnx.zpopmax(key, count);
    }

    @Override
    public <T> Future<List<T>> zpopmin(@RedisProtocolSupport.Key final CharSequence key) {
        return reservedCnx.zpopmin(key);
    }

    @Override
    public <T> Future<List<T>> zpopmin(@RedisProtocolSupport.Key final CharSequence key, @Nullable final Long count) {
        return reservedCnx.zpopmin(key, count);
    }

    @Override
    public <T> Future<List<T>> zrange(@RedisProtocolSupport.Key final CharSequence key, final long start,
                                      final long stop) {
        return reservedCnx.zrange(key, start, stop);
    }

    @Override
    public <T> Future<List<T>> zrange(@RedisProtocolSupport.Key final CharSequence key, final long start,
                                      final long stop,
                                      @Nullable final RedisProtocolSupport.ZrangeWithscores withscores) {
        return reservedCnx.zrange(key, start, stop, withscores);
    }

    @Override
    public <T> Future<List<T>> zrangebylex(@RedisProtocolSupport.Key final CharSequence key, final CharSequence min,
                                           final CharSequence max) {
        return reservedCnx.zrangebylex(key, min, max);
    }

    @Override
    public <T> Future<List<T>> zrangebylex(@RedisProtocolSupport.Key final CharSequence key, final CharSequence min,
                                           final CharSequence max,
                                           @Nullable final RedisProtocolSupport.OffsetCount offsetCount) {
        return reservedCnx.zrangebylex(key, min, max, offsetCount);
    }

    @Override
    public <T> Future<List<T>> zrangebyscore(@RedisProtocolSupport.Key final CharSequence key, final double min,
                                             final double max) {
        return reservedCnx.zrangebyscore(key, min, max);
    }

    @Override
    public <T> Future<List<T>> zrangebyscore(@RedisProtocolSupport.Key final CharSequence key, final double min,
                                             final double max,
                                             @Nullable final RedisProtocolSupport.ZrangebyscoreWithscores withscores,
                                             @Nullable final RedisProtocolSupport.OffsetCount offsetCount) {
        return reservedCnx.zrangebyscore(key, min, max, withscores, offsetCount);
    }

    @Override
    public Future<Long> zrank(@RedisProtocolSupport.Key final CharSequence key, final CharSequence member) {
        return reservedCnx.zrank(key, member);
    }

    @Override
    public Future<Long> zrem(@RedisProtocolSupport.Key final CharSequence key, final CharSequence member) {
        return reservedCnx.zrem(key, member);
    }

    @Override
    public Future<Long> zrem(@RedisProtocolSupport.Key final CharSequence key, final CharSequence member1,
                             final CharSequence member2) {
        return reservedCnx.zrem(key, member1, member2);
    }

    @Override
    public Future<Long> zrem(@RedisProtocolSupport.Key final CharSequence key, final CharSequence member1,
                             final CharSequence member2, final CharSequence member3) {
        return reservedCnx.zrem(key, member1, member2, member3);
    }

    @Override
    public Future<Long> zrem(@RedisProtocolSupport.Key final CharSequence key,
                             final Collection<? extends CharSequence> members) {
        return reservedCnx.zrem(key, members);
    }

    @Override
    public Future<Long> zremrangebylex(@RedisProtocolSupport.Key final CharSequence key, final CharSequence min,
                                       final CharSequence max) {
        return reservedCnx.zremrangebylex(key, min, max);
    }

    @Override
    public Future<Long> zremrangebyrank(@RedisProtocolSupport.Key final CharSequence key, final long start,
                                        final long stop) {
        return reservedCnx.zremrangebyrank(key, start, stop);
    }

    @Override
    public Future<Long> zremrangebyscore(@RedisProtocolSupport.Key final CharSequence key, final double min,
                                         final double max) {
        return reservedCnx.zremrangebyscore(key, min, max);
    }

    @Override
    public <T> Future<List<T>> zrevrange(@RedisProtocolSupport.Key final CharSequence key, final long start,
                                         final long stop) {
        return reservedCnx.zrevrange(key, start, stop);
    }

    @Override
    public <T> Future<List<T>> zrevrange(@RedisProtocolSupport.Key final CharSequence key, final long start,
                                         final long stop,
                                         @Nullable final RedisProtocolSupport.ZrevrangeWithscores withscores) {
        return reservedCnx.zrevrange(key, start, stop, withscores);
    }

    @Override
    public <T> Future<List<T>> zrevrangebylex(@RedisProtocolSupport.Key final CharSequence key, final CharSequence max,
                                              final CharSequence min) {
        return reservedCnx.zrevrangebylex(key, max, min);
    }

    @Override
    public <T> Future<List<T>> zrevrangebylex(@RedisProtocolSupport.Key final CharSequence key, final CharSequence max,
                                              final CharSequence min,
                                              @Nullable final RedisProtocolSupport.OffsetCount offsetCount) {
        return reservedCnx.zrevrangebylex(key, max, min, offsetCount);
    }

    @Override
    public <T> Future<List<T>> zrevrangebyscore(@RedisProtocolSupport.Key final CharSequence key, final double max,
                                                final double min) {
        return reservedCnx.zrevrangebyscore(key, max, min);
    }

    @Override
    public <T> Future<List<T>> zrevrangebyscore(@RedisProtocolSupport.Key final CharSequence key, final double max,
                                                final double min,
                                                @Nullable final RedisProtocolSupport.ZrevrangebyscoreWithscores withscores,
                                                @Nullable final RedisProtocolSupport.OffsetCount offsetCount) {
        return reservedCnx.zrevrangebyscore(key, max, min, withscores, offsetCount);
    }

    @Override
    public Future<Long> zrevrank(@RedisProtocolSupport.Key final CharSequence key, final CharSequence member) {
        return reservedCnx.zrevrank(key, member);
    }

    @Override
    public <T> Future<List<T>> zscan(@RedisProtocolSupport.Key final CharSequence key, final long cursor) {
        return reservedCnx.zscan(key, cursor);
    }

    @Override
    public <T> Future<List<T>> zscan(@RedisProtocolSupport.Key final CharSequence key, final long cursor,
                                     @Nullable final CharSequence matchPattern, @Nullable final Long count) {
        return reservedCnx.zscan(key, cursor, matchPattern, count);
    }

    @Override
    public Future<Double> zscore(@RedisProtocolSupport.Key final CharSequence key, final CharSequence member) {
        return reservedCnx.zscore(key, member);
    }

    @Override
    public Future<Long> zunionstore(@RedisProtocolSupport.Key final CharSequence destination, final long numkeys,
                                    @RedisProtocolSupport.Key final Collection<? extends CharSequence> keys) {
        return reservedCnx.zunionstore(destination, numkeys, keys);
    }

    @Override
    public Future<Long> zunionstore(@RedisProtocolSupport.Key final CharSequence destination, final long numkeys,
                                    @RedisProtocolSupport.Key final Collection<? extends CharSequence> keys,
                                    final Collection<Long> weightses,
                                    @Nullable final RedisProtocolSupport.ZunionstoreAggregate aggregate) {
        return reservedCnx.zunionstore(destination, numkeys, keys, weightses, aggregate);
    }
}

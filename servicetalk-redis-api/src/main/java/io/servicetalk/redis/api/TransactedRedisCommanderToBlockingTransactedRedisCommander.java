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
import java.util.concurrent.Future;
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
    public Future<Long> append(@RedisProtocolSupport.Key final CharSequence key, final CharSequence value) {
        return commander.append(key, value);
    }

    @Override
    public Future<String> auth(final CharSequence password) {
        return commander.auth(password);
    }

    @Override
    public Future<String> bgrewriteaof() {
        return commander.bgrewriteaof();
    }

    @Override
    public Future<String> bgsave() {
        return commander.bgsave();
    }

    @Override
    public Future<Long> bitcount(@RedisProtocolSupport.Key final CharSequence key) {
        return commander.bitcount(key);
    }

    @Override
    public Future<Long> bitcount(@RedisProtocolSupport.Key final CharSequence key, @Nullable final Long start,
                                 @Nullable final Long end) {
        return commander.bitcount(key, start, end);
    }

    @Override
    public Future<List<Long>> bitfield(@RedisProtocolSupport.Key final CharSequence key,
                                       final Collection<RedisProtocolSupport.BitfieldOperation> operations) {
        return commander.bitfield(key, operations);
    }

    @Override
    public Future<Long> bitop(final CharSequence operation, @RedisProtocolSupport.Key final CharSequence destkey,
                              @RedisProtocolSupport.Key final CharSequence key) {
        return commander.bitop(operation, destkey, key);
    }

    @Override
    public Future<Long> bitop(final CharSequence operation, @RedisProtocolSupport.Key final CharSequence destkey,
                              @RedisProtocolSupport.Key final CharSequence key1,
                              @RedisProtocolSupport.Key final CharSequence key2) {
        return commander.bitop(operation, destkey, key1, key2);
    }

    @Override
    public Future<Long> bitop(final CharSequence operation, @RedisProtocolSupport.Key final CharSequence destkey,
                              @RedisProtocolSupport.Key final CharSequence key1,
                              @RedisProtocolSupport.Key final CharSequence key2,
                              @RedisProtocolSupport.Key final CharSequence key3) {
        return commander.bitop(operation, destkey, key1, key2, key3);
    }

    @Override
    public Future<Long> bitop(final CharSequence operation, @RedisProtocolSupport.Key final CharSequence destkey,
                              @RedisProtocolSupport.Key final Collection<? extends CharSequence> keys) {
        return commander.bitop(operation, destkey, keys);
    }

    @Override
    public Future<Long> bitpos(@RedisProtocolSupport.Key final CharSequence key, final long bit) {
        return commander.bitpos(key, bit);
    }

    @Override
    public Future<Long> bitpos(@RedisProtocolSupport.Key final CharSequence key, final long bit,
                               @Nullable final Long start, @Nullable final Long end) {
        return commander.bitpos(key, bit, start, end);
    }

    @Override
    public <T> Future<List<T>> blpop(@RedisProtocolSupport.Key final Collection<? extends CharSequence> keys,
                                     final long timeout) {
        return commander.blpop(keys, timeout);
    }

    @Override
    public <T> Future<List<T>> brpop(@RedisProtocolSupport.Key final Collection<? extends CharSequence> keys,
                                     final long timeout) {
        return commander.brpop(keys, timeout);
    }

    @Override
    public Future<String> brpoplpush(@RedisProtocolSupport.Key final CharSequence source,
                                     @RedisProtocolSupport.Key final CharSequence destination, final long timeout) {
        return commander.brpoplpush(source, destination, timeout);
    }

    @Override
    public <T> Future<List<T>> bzpopmax(@RedisProtocolSupport.Key final Collection<? extends CharSequence> keys,
                                        final long timeout) {
        return commander.bzpopmax(keys, timeout);
    }

    @Override
    public <T> Future<List<T>> bzpopmin(@RedisProtocolSupport.Key final Collection<? extends CharSequence> keys,
                                        final long timeout) {
        return commander.bzpopmin(keys, timeout);
    }

    @Override
    public Future<Long> clientKill(@Nullable final Long id, @Nullable final RedisProtocolSupport.ClientKillType type,
                                   @Nullable final CharSequence addrIpPort, @Nullable final CharSequence skipmeYesNo) {
        return commander.clientKill(id, type, addrIpPort, skipmeYesNo);
    }

    @Override
    public Future<String> clientList() {
        return commander.clientList();
    }

    @Override
    public Future<String> clientGetname() {
        return commander.clientGetname();
    }

    @Override
    public Future<String> clientPause(final long timeout) {
        return commander.clientPause(timeout);
    }

    @Override
    public Future<String> clientReply(final RedisProtocolSupport.ClientReplyReplyMode replyMode) {
        return commander.clientReply(replyMode);
    }

    @Override
    public Future<String> clientSetname(final CharSequence connectionName) {
        return commander.clientSetname(connectionName);
    }

    @Override
    public Future<String> clusterAddslots(final long slot) {
        return commander.clusterAddslots(slot);
    }

    @Override
    public Future<String> clusterAddslots(final long slot1, final long slot2) {
        return commander.clusterAddslots(slot1, slot2);
    }

    @Override
    public Future<String> clusterAddslots(final long slot1, final long slot2, final long slot3) {
        return commander.clusterAddslots(slot1, slot2, slot3);
    }

    @Override
    public Future<String> clusterAddslots(final Collection<Long> slots) {
        return commander.clusterAddslots(slots);
    }

    @Override
    public Future<Long> clusterCountFailureReports(final CharSequence nodeId) {
        return commander.clusterCountFailureReports(nodeId);
    }

    @Override
    public Future<Long> clusterCountkeysinslot(final long slot) {
        return commander.clusterCountkeysinslot(slot);
    }

    @Override
    public Future<String> clusterDelslots(final long slot) {
        return commander.clusterDelslots(slot);
    }

    @Override
    public Future<String> clusterDelslots(final long slot1, final long slot2) {
        return commander.clusterDelslots(slot1, slot2);
    }

    @Override
    public Future<String> clusterDelslots(final long slot1, final long slot2, final long slot3) {
        return commander.clusterDelslots(slot1, slot2, slot3);
    }

    @Override
    public Future<String> clusterDelslots(final Collection<Long> slots) {
        return commander.clusterDelslots(slots);
    }

    @Override
    public Future<String> clusterFailover() {
        return commander.clusterFailover();
    }

    @Override
    public Future<String> clusterFailover(@Nullable final RedisProtocolSupport.ClusterFailoverOptions options) {
        return commander.clusterFailover(options);
    }

    @Override
    public Future<String> clusterForget(final CharSequence nodeId) {
        return commander.clusterForget(nodeId);
    }

    @Override
    public <T> Future<List<T>> clusterGetkeysinslot(final long slot, final long count) {
        return commander.clusterGetkeysinslot(slot, count);
    }

    @Override
    public Future<String> clusterInfo() {
        return commander.clusterInfo();
    }

    @Override
    public Future<Long> clusterKeyslot(final CharSequence key) {
        return commander.clusterKeyslot(key);
    }

    @Override
    public Future<String> clusterMeet(final CharSequence ip, final long port) {
        return commander.clusterMeet(ip, port);
    }

    @Override
    public Future<String> clusterNodes() {
        return commander.clusterNodes();
    }

    @Override
    public Future<String> clusterReplicate(final CharSequence nodeId) {
        return commander.clusterReplicate(nodeId);
    }

    @Override
    public Future<String> clusterReset() {
        return commander.clusterReset();
    }

    @Override
    public Future<String> clusterReset(@Nullable final RedisProtocolSupport.ClusterResetResetType resetType) {
        return commander.clusterReset(resetType);
    }

    @Override
    public Future<String> clusterSaveconfig() {
        return commander.clusterSaveconfig();
    }

    @Override
    public Future<String> clusterSetConfigEpoch(final long configEpoch) {
        return commander.clusterSetConfigEpoch(configEpoch);
    }

    @Override
    public Future<String> clusterSetslot(final long slot,
                                         final RedisProtocolSupport.ClusterSetslotSubcommand subcommand) {
        return commander.clusterSetslot(slot, subcommand);
    }

    @Override
    public Future<String> clusterSetslot(final long slot,
                                         final RedisProtocolSupport.ClusterSetslotSubcommand subcommand,
                                         @Nullable final CharSequence nodeId) {
        return commander.clusterSetslot(slot, subcommand, nodeId);
    }

    @Override
    public Future<String> clusterSlaves(final CharSequence nodeId) {
        return commander.clusterSlaves(nodeId);
    }

    @Override
    public <T> Future<List<T>> clusterSlots() {
        return commander.clusterSlots();
    }

    @Override
    public <T> Future<List<T>> command() {
        return commander.command();
    }

    @Override
    public Future<Long> commandCount() {
        return commander.commandCount();
    }

    @Override
    public <T> Future<List<T>> commandGetkeys() {
        return commander.commandGetkeys();
    }

    @Override
    public <T> Future<List<T>> commandInfo(final CharSequence commandName) {
        return commander.commandInfo(commandName);
    }

    @Override
    public <T> Future<List<T>> commandInfo(final CharSequence commandName1, final CharSequence commandName2) {
        return commander.commandInfo(commandName1, commandName2);
    }

    @Override
    public <T> Future<List<T>> commandInfo(final CharSequence commandName1, final CharSequence commandName2,
                                           final CharSequence commandName3) {
        return commander.commandInfo(commandName1, commandName2, commandName3);
    }

    @Override
    public <T> Future<List<T>> commandInfo(final Collection<? extends CharSequence> commandNames) {
        return commander.commandInfo(commandNames);
    }

    @Override
    public <T> Future<List<T>> configGet(final CharSequence parameter) {
        return commander.configGet(parameter);
    }

    @Override
    public Future<String> configRewrite() {
        return commander.configRewrite();
    }

    @Override
    public Future<String> configSet(final CharSequence parameter, final CharSequence value) {
        return commander.configSet(parameter, value);
    }

    @Override
    public Future<String> configResetstat() {
        return commander.configResetstat();
    }

    @Override
    public Future<Long> dbsize() {
        return commander.dbsize();
    }

    @Override
    public Future<String> debugObject(@RedisProtocolSupport.Key final CharSequence key) {
        return commander.debugObject(key);
    }

    @Override
    public Future<String> debugSegfault() {
        return commander.debugSegfault();
    }

    @Override
    public Future<Long> decr(@RedisProtocolSupport.Key final CharSequence key) {
        return commander.decr(key);
    }

    @Override
    public Future<Long> decrby(@RedisProtocolSupport.Key final CharSequence key, final long decrement) {
        return commander.decrby(key, decrement);
    }

    @Override
    public Future<Long> del(@RedisProtocolSupport.Key final CharSequence key) {
        return commander.del(key);
    }

    @Override
    public Future<Long> del(@RedisProtocolSupport.Key final CharSequence key1,
                            @RedisProtocolSupport.Key final CharSequence key2) {
        return commander.del(key1, key2);
    }

    @Override
    public Future<Long> del(@RedisProtocolSupport.Key final CharSequence key1,
                            @RedisProtocolSupport.Key final CharSequence key2,
                            @RedisProtocolSupport.Key final CharSequence key3) {
        return commander.del(key1, key2, key3);
    }

    @Override
    public Future<Long> del(@RedisProtocolSupport.Key final Collection<? extends CharSequence> keys) {
        return commander.del(keys);
    }

    @Override
    public String discard() {
        return blockingInvocation(commander.discard());
    }

    @Override
    public Future<String> dump(@RedisProtocolSupport.Key final CharSequence key) {
        return commander.dump(key);
    }

    @Override
    public Future<String> echo(final CharSequence message) {
        return commander.echo(message);
    }

    @Override
    public Future<String> eval(final CharSequence script, final long numkeys,
                               @RedisProtocolSupport.Key final Collection<? extends CharSequence> keys,
                               final Collection<? extends CharSequence> args) {
        return commander.eval(script, numkeys, keys, args);
    }

    @Override
    public <T> Future<List<T>> evalList(final CharSequence script, final long numkeys,
                                        @RedisProtocolSupport.Key final Collection<? extends CharSequence> keys,
                                        final Collection<? extends CharSequence> args) {
        return commander.evalList(script, numkeys, keys, args);
    }

    @Override
    public Future<Long> evalLong(final CharSequence script, final long numkeys,
                                 @RedisProtocolSupport.Key final Collection<? extends CharSequence> keys,
                                 final Collection<? extends CharSequence> args) {
        return commander.evalLong(script, numkeys, keys, args);
    }

    @Override
    public Future<String> evalsha(final CharSequence sha1, final long numkeys,
                                  @RedisProtocolSupport.Key final Collection<? extends CharSequence> keys,
                                  final Collection<? extends CharSequence> args) {
        return commander.evalsha(sha1, numkeys, keys, args);
    }

    @Override
    public <T> Future<List<T>> evalshaList(final CharSequence sha1, final long numkeys,
                                           @RedisProtocolSupport.Key final Collection<? extends CharSequence> keys,
                                           final Collection<? extends CharSequence> args) {
        return commander.evalshaList(sha1, numkeys, keys, args);
    }

    @Override
    public Future<Long> evalshaLong(final CharSequence sha1, final long numkeys,
                                    @RedisProtocolSupport.Key final Collection<? extends CharSequence> keys,
                                    final Collection<? extends CharSequence> args) {
        return commander.evalshaLong(sha1, numkeys, keys, args);
    }

    @Override
    public void exec() {
        blockingInvocation(commander.exec());
    }

    @Override
    public Future<Long> exists(@RedisProtocolSupport.Key final CharSequence key) {
        return commander.exists(key);
    }

    @Override
    public Future<Long> exists(@RedisProtocolSupport.Key final CharSequence key1,
                               @RedisProtocolSupport.Key final CharSequence key2) {
        return commander.exists(key1, key2);
    }

    @Override
    public Future<Long> exists(@RedisProtocolSupport.Key final CharSequence key1,
                               @RedisProtocolSupport.Key final CharSequence key2,
                               @RedisProtocolSupport.Key final CharSequence key3) {
        return commander.exists(key1, key2, key3);
    }

    @Override
    public Future<Long> exists(@RedisProtocolSupport.Key final Collection<? extends CharSequence> keys) {
        return commander.exists(keys);
    }

    @Override
    public Future<Long> expire(@RedisProtocolSupport.Key final CharSequence key, final long seconds) {
        return commander.expire(key, seconds);
    }

    @Override
    public Future<Long> expireat(@RedisProtocolSupport.Key final CharSequence key, final long timestamp) {
        return commander.expireat(key, timestamp);
    }

    @Override
    public Future<String> flushall() {
        return commander.flushall();
    }

    @Override
    public Future<String> flushall(@Nullable final RedisProtocolSupport.FlushallAsync async) {
        return commander.flushall(async);
    }

    @Override
    public Future<String> flushdb() {
        return commander.flushdb();
    }

    @Override
    public Future<String> flushdb(@Nullable final RedisProtocolSupport.FlushdbAsync async) {
        return commander.flushdb(async);
    }

    @Override
    public Future<Long> geoadd(@RedisProtocolSupport.Key final CharSequence key, final double longitude,
                               final double latitude, final CharSequence member) {
        return commander.geoadd(key, longitude, latitude, member);
    }

    @Override
    public Future<Long> geoadd(@RedisProtocolSupport.Key final CharSequence key, final double longitude1,
                               final double latitude1, final CharSequence member1, final double longitude2,
                               final double latitude2, final CharSequence member2) {
        return commander.geoadd(key, longitude1, latitude1, member1, longitude2, latitude2, member2);
    }

    @Override
    public Future<Long> geoadd(@RedisProtocolSupport.Key final CharSequence key, final double longitude1,
                               final double latitude1, final CharSequence member1, final double longitude2,
                               final double latitude2, final CharSequence member2, final double longitude3,
                               final double latitude3, final CharSequence member3) {
        return commander.geoadd(key, longitude1, latitude1, member1, longitude2, latitude2, member2, longitude3,
                    latitude3, member3);
    }

    @Override
    public Future<Long> geoadd(@RedisProtocolSupport.Key final CharSequence key,
                               final Collection<RedisProtocolSupport.LongitudeLatitudeMember> longitudeLatitudeMembers) {
        return commander.geoadd(key, longitudeLatitudeMembers);
    }

    @Override
    public Future<Double> geodist(@RedisProtocolSupport.Key final CharSequence key, final CharSequence member1,
                                  final CharSequence member2) {
        return commander.geodist(key, member1, member2);
    }

    @Override
    public Future<Double> geodist(@RedisProtocolSupport.Key final CharSequence key, final CharSequence member1,
                                  final CharSequence member2, @Nullable final CharSequence unit) {
        return commander.geodist(key, member1, member2, unit);
    }

    @Override
    public <T> Future<List<T>> geohash(@RedisProtocolSupport.Key final CharSequence key, final CharSequence member) {
        return commander.geohash(key, member);
    }

    @Override
    public <T> Future<List<T>> geohash(@RedisProtocolSupport.Key final CharSequence key, final CharSequence member1,
                                       final CharSequence member2) {
        return commander.geohash(key, member1, member2);
    }

    @Override
    public <T> Future<List<T>> geohash(@RedisProtocolSupport.Key final CharSequence key, final CharSequence member1,
                                       final CharSequence member2, final CharSequence member3) {
        return commander.geohash(key, member1, member2, member3);
    }

    @Override
    public <T> Future<List<T>> geohash(@RedisProtocolSupport.Key final CharSequence key,
                                       final Collection<? extends CharSequence> members) {
        return commander.geohash(key, members);
    }

    @Override
    public <T> Future<List<T>> geopos(@RedisProtocolSupport.Key final CharSequence key, final CharSequence member) {
        return commander.geopos(key, member);
    }

    @Override
    public <T> Future<List<T>> geopos(@RedisProtocolSupport.Key final CharSequence key, final CharSequence member1,
                                      final CharSequence member2) {
        return commander.geopos(key, member1, member2);
    }

    @Override
    public <T> Future<List<T>> geopos(@RedisProtocolSupport.Key final CharSequence key, final CharSequence member1,
                                      final CharSequence member2, final CharSequence member3) {
        return commander.geopos(key, member1, member2, member3);
    }

    @Override
    public <T> Future<List<T>> geopos(@RedisProtocolSupport.Key final CharSequence key,
                                      final Collection<? extends CharSequence> members) {
        return commander.geopos(key, members);
    }

    @Override
    public <T> Future<List<T>> georadius(@RedisProtocolSupport.Key final CharSequence key, final double longitude,
                                         final double latitude, final double radius,
                                         final RedisProtocolSupport.GeoradiusUnit unit) {
        return commander.georadius(key, longitude, latitude, radius, unit);
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
        return commander.georadius(key, longitude, latitude, radius, unit, withcoord, withdist, withhash, count, order,
                    storeKey, storedistKey);
    }

    @Override
    public <T> Future<List<T>> georadiusbymember(@RedisProtocolSupport.Key final CharSequence key,
                                                 final CharSequence member, final double radius,
                                                 final RedisProtocolSupport.GeoradiusbymemberUnit unit) {
        return commander.georadiusbymember(key, member, radius, unit);
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
        return commander.georadiusbymember(key, member, radius, unit, withcoord, withdist, withhash, count, order,
                    storeKey, storedistKey);
    }

    @Override
    public Future<String> get(@RedisProtocolSupport.Key final CharSequence key) {
        return commander.get(key);
    }

    @Override
    public Future<Long> getbit(@RedisProtocolSupport.Key final CharSequence key, final long offset) {
        return commander.getbit(key, offset);
    }

    @Override
    public Future<String> getrange(@RedisProtocolSupport.Key final CharSequence key, final long start, final long end) {
        return commander.getrange(key, start, end);
    }

    @Override
    public Future<String> getset(@RedisProtocolSupport.Key final CharSequence key, final CharSequence value) {
        return commander.getset(key, value);
    }

    @Override
    public Future<Long> hdel(@RedisProtocolSupport.Key final CharSequence key, final CharSequence field) {
        return commander.hdel(key, field);
    }

    @Override
    public Future<Long> hdel(@RedisProtocolSupport.Key final CharSequence key, final CharSequence field1,
                             final CharSequence field2) {
        return commander.hdel(key, field1, field2);
    }

    @Override
    public Future<Long> hdel(@RedisProtocolSupport.Key final CharSequence key, final CharSequence field1,
                             final CharSequence field2, final CharSequence field3) {
        return commander.hdel(key, field1, field2, field3);
    }

    @Override
    public Future<Long> hdel(@RedisProtocolSupport.Key final CharSequence key,
                             final Collection<? extends CharSequence> fields) {
        return commander.hdel(key, fields);
    }

    @Override
    public Future<Long> hexists(@RedisProtocolSupport.Key final CharSequence key, final CharSequence field) {
        return commander.hexists(key, field);
    }

    @Override
    public Future<String> hget(@RedisProtocolSupport.Key final CharSequence key, final CharSequence field) {
        return commander.hget(key, field);
    }

    @Override
    public <T> Future<List<T>> hgetall(@RedisProtocolSupport.Key final CharSequence key) {
        return commander.hgetall(key);
    }

    @Override
    public Future<Long> hincrby(@RedisProtocolSupport.Key final CharSequence key, final CharSequence field,
                                final long increment) {
        return commander.hincrby(key, field, increment);
    }

    @Override
    public Future<Double> hincrbyfloat(@RedisProtocolSupport.Key final CharSequence key, final CharSequence field,
                                       final double increment) {
        return commander.hincrbyfloat(key, field, increment);
    }

    @Override
    public <T> Future<List<T>> hkeys(@RedisProtocolSupport.Key final CharSequence key) {
        return commander.hkeys(key);
    }

    @Override
    public Future<Long> hlen(@RedisProtocolSupport.Key final CharSequence key) {
        return commander.hlen(key);
    }

    @Override
    public Future<List<String>> hmget(@RedisProtocolSupport.Key final CharSequence key, final CharSequence field) {
        return commander.hmget(key, field);
    }

    @Override
    public Future<List<String>> hmget(@RedisProtocolSupport.Key final CharSequence key, final CharSequence field1,
                                      final CharSequence field2) {
        return commander.hmget(key, field1, field2);
    }

    @Override
    public Future<List<String>> hmget(@RedisProtocolSupport.Key final CharSequence key, final CharSequence field1,
                                      final CharSequence field2, final CharSequence field3) {
        return commander.hmget(key, field1, field2, field3);
    }

    @Override
    public Future<List<String>> hmget(@RedisProtocolSupport.Key final CharSequence key,
                                      final Collection<? extends CharSequence> fields) {
        return commander.hmget(key, fields);
    }

    @Override
    public Future<String> hmset(@RedisProtocolSupport.Key final CharSequence key, final CharSequence field,
                                final CharSequence value) {
        return commander.hmset(key, field, value);
    }

    @Override
    public Future<String> hmset(@RedisProtocolSupport.Key final CharSequence key, final CharSequence field1,
                                final CharSequence value1, final CharSequence field2, final CharSequence value2) {
        return commander.hmset(key, field1, value1, field2, value2);
    }

    @Override
    public Future<String> hmset(@RedisProtocolSupport.Key final CharSequence key, final CharSequence field1,
                                final CharSequence value1, final CharSequence field2, final CharSequence value2,
                                final CharSequence field3, final CharSequence value3) {
        return commander.hmset(key, field1, value1, field2, value2, field3, value3);
    }

    @Override
    public Future<String> hmset(@RedisProtocolSupport.Key final CharSequence key,
                                final Collection<RedisProtocolSupport.FieldValue> fieldValues) {
        return commander.hmset(key, fieldValues);
    }

    @Override
    public <T> Future<List<T>> hscan(@RedisProtocolSupport.Key final CharSequence key, final long cursor) {
        return commander.hscan(key, cursor);
    }

    @Override
    public <T> Future<List<T>> hscan(@RedisProtocolSupport.Key final CharSequence key, final long cursor,
                                     @Nullable final CharSequence matchPattern, @Nullable final Long count) {
        return commander.hscan(key, cursor, matchPattern, count);
    }

    @Override
    public Future<Long> hset(@RedisProtocolSupport.Key final CharSequence key, final CharSequence field,
                             final CharSequence value) {
        return commander.hset(key, field, value);
    }

    @Override
    public Future<Long> hsetnx(@RedisProtocolSupport.Key final CharSequence key, final CharSequence field,
                               final CharSequence value) {
        return commander.hsetnx(key, field, value);
    }

    @Override
    public Future<Long> hstrlen(@RedisProtocolSupport.Key final CharSequence key, final CharSequence field) {
        return commander.hstrlen(key, field);
    }

    @Override
    public <T> Future<List<T>> hvals(@RedisProtocolSupport.Key final CharSequence key) {
        return commander.hvals(key);
    }

    @Override
    public Future<Long> incr(@RedisProtocolSupport.Key final CharSequence key) {
        return commander.incr(key);
    }

    @Override
    public Future<Long> incrby(@RedisProtocolSupport.Key final CharSequence key, final long increment) {
        return commander.incrby(key, increment);
    }

    @Override
    public Future<Double> incrbyfloat(@RedisProtocolSupport.Key final CharSequence key, final double increment) {
        return commander.incrbyfloat(key, increment);
    }

    @Override
    public Future<String> info() {
        return commander.info();
    }

    @Override
    public Future<String> info(@Nullable final CharSequence section) {
        return commander.info(section);
    }

    @Override
    public <T> Future<List<T>> keys(final CharSequence pattern) {
        return commander.keys(pattern);
    }

    @Override
    public Future<Long> lastsave() {
        return commander.lastsave();
    }

    @Override
    public Future<String> lindex(@RedisProtocolSupport.Key final CharSequence key, final long index) {
        return commander.lindex(key, index);
    }

    @Override
    public Future<Long> linsert(@RedisProtocolSupport.Key final CharSequence key,
                                final RedisProtocolSupport.LinsertWhere where, final CharSequence pivot,
                                final CharSequence value) {
        return commander.linsert(key, where, pivot, value);
    }

    @Override
    public Future<Long> llen(@RedisProtocolSupport.Key final CharSequence key) {
        return commander.llen(key);
    }

    @Override
    public Future<String> lpop(@RedisProtocolSupport.Key final CharSequence key) {
        return commander.lpop(key);
    }

    @Override
    public Future<Long> lpush(@RedisProtocolSupport.Key final CharSequence key, final CharSequence value) {
        return commander.lpush(key, value);
    }

    @Override
    public Future<Long> lpush(@RedisProtocolSupport.Key final CharSequence key, final CharSequence value1,
                              final CharSequence value2) {
        return commander.lpush(key, value1, value2);
    }

    @Override
    public Future<Long> lpush(@RedisProtocolSupport.Key final CharSequence key, final CharSequence value1,
                              final CharSequence value2, final CharSequence value3) {
        return commander.lpush(key, value1, value2, value3);
    }

    @Override
    public Future<Long> lpush(@RedisProtocolSupport.Key final CharSequence key,
                              final Collection<? extends CharSequence> values) {
        return commander.lpush(key, values);
    }

    @Override
    public Future<Long> lpushx(@RedisProtocolSupport.Key final CharSequence key, final CharSequence value) {
        return commander.lpushx(key, value);
    }

    @Override
    public <T> Future<List<T>> lrange(@RedisProtocolSupport.Key final CharSequence key, final long start,
                                      final long stop) {
        return commander.lrange(key, start, stop);
    }

    @Override
    public Future<Long> lrem(@RedisProtocolSupport.Key final CharSequence key, final long count,
                             final CharSequence value) {
        return commander.lrem(key, count, value);
    }

    @Override
    public Future<String> lset(@RedisProtocolSupport.Key final CharSequence key, final long index,
                               final CharSequence value) {
        return commander.lset(key, index, value);
    }

    @Override
    public Future<String> ltrim(@RedisProtocolSupport.Key final CharSequence key, final long start, final long stop) {
        return commander.ltrim(key, start, stop);
    }

    @Override
    public Future<String> memoryDoctor() {
        return commander.memoryDoctor();
    }

    @Override
    public <T> Future<List<T>> memoryHelp() {
        return commander.memoryHelp();
    }

    @Override
    public Future<String> memoryMallocStats() {
        return commander.memoryMallocStats();
    }

    @Override
    public Future<String> memoryPurge() {
        return commander.memoryPurge();
    }

    @Override
    public <T> Future<List<T>> memoryStats() {
        return commander.memoryStats();
    }

    @Override
    public Future<Long> memoryUsage(@RedisProtocolSupport.Key final CharSequence key) {
        return commander.memoryUsage(key);
    }

    @Override
    public Future<Long> memoryUsage(@RedisProtocolSupport.Key final CharSequence key,
                                    @Nullable final Long samplesCount) {
        return commander.memoryUsage(key, samplesCount);
    }

    @Override
    public Future<List<String>> mget(@RedisProtocolSupport.Key final CharSequence key) {
        return commander.mget(key);
    }

    @Override
    public Future<List<String>> mget(@RedisProtocolSupport.Key final CharSequence key1,
                                     @RedisProtocolSupport.Key final CharSequence key2) {
        return commander.mget(key1, key2);
    }

    @Override
    public Future<List<String>> mget(@RedisProtocolSupport.Key final CharSequence key1,
                                     @RedisProtocolSupport.Key final CharSequence key2,
                                     @RedisProtocolSupport.Key final CharSequence key3) {
        return commander.mget(key1, key2, key3);
    }

    @Override
    public Future<List<String>> mget(@RedisProtocolSupport.Key final Collection<? extends CharSequence> keys) {
        return commander.mget(keys);
    }

    @Override
    public Future<Long> move(@RedisProtocolSupport.Key final CharSequence key, final long db) {
        return commander.move(key, db);
    }

    @Override
    public Future<String> mset(@RedisProtocolSupport.Key final CharSequence key, final CharSequence value) {
        return commander.mset(key, value);
    }

    @Override
    public Future<String> mset(@RedisProtocolSupport.Key final CharSequence key1, final CharSequence value1,
                               @RedisProtocolSupport.Key final CharSequence key2, final CharSequence value2) {
        return commander.mset(key1, value1, key2, value2);
    }

    @Override
    public Future<String> mset(@RedisProtocolSupport.Key final CharSequence key1, final CharSequence value1,
                               @RedisProtocolSupport.Key final CharSequence key2, final CharSequence value2,
                               @RedisProtocolSupport.Key final CharSequence key3, final CharSequence value3) {
        return commander.mset(key1, value1, key2, value2, key3, value3);
    }

    @Override
    public Future<String> mset(final Collection<RedisProtocolSupport.KeyValue> keyValues) {
        return commander.mset(keyValues);
    }

    @Override
    public Future<Long> msetnx(@RedisProtocolSupport.Key final CharSequence key, final CharSequence value) {
        return commander.msetnx(key, value);
    }

    @Override
    public Future<Long> msetnx(@RedisProtocolSupport.Key final CharSequence key1, final CharSequence value1,
                               @RedisProtocolSupport.Key final CharSequence key2, final CharSequence value2) {
        return commander.msetnx(key1, value1, key2, value2);
    }

    @Override
    public Future<Long> msetnx(@RedisProtocolSupport.Key final CharSequence key1, final CharSequence value1,
                               @RedisProtocolSupport.Key final CharSequence key2, final CharSequence value2,
                               @RedisProtocolSupport.Key final CharSequence key3, final CharSequence value3) {
        return commander.msetnx(key1, value1, key2, value2, key3, value3);
    }

    @Override
    public Future<Long> msetnx(final Collection<RedisProtocolSupport.KeyValue> keyValues) {
        return commander.msetnx(keyValues);
    }

    @Override
    public Future<String> objectEncoding(@RedisProtocolSupport.Key final CharSequence key) {
        return commander.objectEncoding(key);
    }

    @Override
    public Future<Long> objectFreq(@RedisProtocolSupport.Key final CharSequence key) {
        return commander.objectFreq(key);
    }

    @Override
    public Future<List<String>> objectHelp() {
        return commander.objectHelp();
    }

    @Override
    public Future<Long> objectIdletime(@RedisProtocolSupport.Key final CharSequence key) {
        return commander.objectIdletime(key);
    }

    @Override
    public Future<Long> objectRefcount(@RedisProtocolSupport.Key final CharSequence key) {
        return commander.objectRefcount(key);
    }

    @Override
    public Future<Long> persist(@RedisProtocolSupport.Key final CharSequence key) {
        return commander.persist(key);
    }

    @Override
    public Future<Long> pexpire(@RedisProtocolSupport.Key final CharSequence key, final long milliseconds) {
        return commander.pexpire(key, milliseconds);
    }

    @Override
    public Future<Long> pexpireat(@RedisProtocolSupport.Key final CharSequence key, final long millisecondsTimestamp) {
        return commander.pexpireat(key, millisecondsTimestamp);
    }

    @Override
    public Future<Long> pfadd(@RedisProtocolSupport.Key final CharSequence key, final CharSequence element) {
        return commander.pfadd(key, element);
    }

    @Override
    public Future<Long> pfadd(@RedisProtocolSupport.Key final CharSequence key, final CharSequence element1,
                              final CharSequence element2) {
        return commander.pfadd(key, element1, element2);
    }

    @Override
    public Future<Long> pfadd(@RedisProtocolSupport.Key final CharSequence key, final CharSequence element1,
                              final CharSequence element2, final CharSequence element3) {
        return commander.pfadd(key, element1, element2, element3);
    }

    @Override
    public Future<Long> pfadd(@RedisProtocolSupport.Key final CharSequence key,
                              final Collection<? extends CharSequence> elements) {
        return commander.pfadd(key, elements);
    }

    @Override
    public Future<Long> pfcount(@RedisProtocolSupport.Key final CharSequence key) {
        return commander.pfcount(key);
    }

    @Override
    public Future<Long> pfcount(@RedisProtocolSupport.Key final CharSequence key1,
                                @RedisProtocolSupport.Key final CharSequence key2) {
        return commander.pfcount(key1, key2);
    }

    @Override
    public Future<Long> pfcount(@RedisProtocolSupport.Key final CharSequence key1,
                                @RedisProtocolSupport.Key final CharSequence key2,
                                @RedisProtocolSupport.Key final CharSequence key3) {
        return commander.pfcount(key1, key2, key3);
    }

    @Override
    public Future<Long> pfcount(@RedisProtocolSupport.Key final Collection<? extends CharSequence> keys) {
        return commander.pfcount(keys);
    }

    @Override
    public Future<String> pfmerge(@RedisProtocolSupport.Key final CharSequence destkey,
                                  @RedisProtocolSupport.Key final CharSequence sourcekey) {
        return commander.pfmerge(destkey, sourcekey);
    }

    @Override
    public Future<String> pfmerge(@RedisProtocolSupport.Key final CharSequence destkey,
                                  @RedisProtocolSupport.Key final CharSequence sourcekey1,
                                  @RedisProtocolSupport.Key final CharSequence sourcekey2) {
        return commander.pfmerge(destkey, sourcekey1, sourcekey2);
    }

    @Override
    public Future<String> pfmerge(@RedisProtocolSupport.Key final CharSequence destkey,
                                  @RedisProtocolSupport.Key final CharSequence sourcekey1,
                                  @RedisProtocolSupport.Key final CharSequence sourcekey2,
                                  @RedisProtocolSupport.Key final CharSequence sourcekey3) {
        return commander.pfmerge(destkey, sourcekey1, sourcekey2, sourcekey3);
    }

    @Override
    public Future<String> pfmerge(@RedisProtocolSupport.Key final CharSequence destkey,
                                  @RedisProtocolSupport.Key final Collection<? extends CharSequence> sourcekeys) {
        return commander.pfmerge(destkey, sourcekeys);
    }

    @Override
    public Future<String> ping() {
        return commander.ping();
    }

    @Override
    public Future<String> ping(final CharSequence message) {
        return commander.ping(message);
    }

    @Override
    public Future<String> psetex(@RedisProtocolSupport.Key final CharSequence key, final long milliseconds,
                                 final CharSequence value) {
        return commander.psetex(key, milliseconds, value);
    }

    @Override
    public Future<Long> pttl(@RedisProtocolSupport.Key final CharSequence key) {
        return commander.pttl(key);
    }

    @Override
    public Future<Long> publish(final CharSequence channel, final CharSequence message) {
        return commander.publish(channel, message);
    }

    @Override
    public Future<List<String>> pubsubChannels() {
        return commander.pubsubChannels();
    }

    @Override
    public Future<List<String>> pubsubChannels(@Nullable final CharSequence pattern) {
        return commander.pubsubChannels(pattern);
    }

    @Override
    public Future<List<String>> pubsubChannels(@Nullable final CharSequence pattern1,
                                               @Nullable final CharSequence pattern2) {
        return commander.pubsubChannels(pattern1, pattern2);
    }

    @Override
    public Future<List<String>> pubsubChannels(@Nullable final CharSequence pattern1,
                                               @Nullable final CharSequence pattern2,
                                               @Nullable final CharSequence pattern3) {
        return commander.pubsubChannels(pattern1, pattern2, pattern3);
    }

    @Override
    public Future<List<String>> pubsubChannels(final Collection<? extends CharSequence> patterns) {
        return commander.pubsubChannels(patterns);
    }

    @Override
    public <T> Future<List<T>> pubsubNumsub() {
        return commander.pubsubNumsub();
    }

    @Override
    public <T> Future<List<T>> pubsubNumsub(@Nullable final CharSequence channel) {
        return commander.pubsubNumsub(channel);
    }

    @Override
    public <T> Future<List<T>> pubsubNumsub(@Nullable final CharSequence channel1,
                                            @Nullable final CharSequence channel2) {
        return commander.pubsubNumsub(channel1, channel2);
    }

    @Override
    public <T> Future<List<T>> pubsubNumsub(@Nullable final CharSequence channel1,
                                            @Nullable final CharSequence channel2,
                                            @Nullable final CharSequence channel3) {
        return commander.pubsubNumsub(channel1, channel2, channel3);
    }

    @Override
    public <T> Future<List<T>> pubsubNumsub(final Collection<? extends CharSequence> channels) {
        return commander.pubsubNumsub(channels);
    }

    @Override
    public Future<Long> pubsubNumpat() {
        return commander.pubsubNumpat();
    }

    @Override
    public Future<String> randomkey() {
        return commander.randomkey();
    }

    @Override
    public Future<String> readonly() {
        return commander.readonly();
    }

    @Override
    public Future<String> readwrite() {
        return commander.readwrite();
    }

    @Override
    public Future<String> rename(@RedisProtocolSupport.Key final CharSequence key,
                                 @RedisProtocolSupport.Key final CharSequence newkey) {
        return commander.rename(key, newkey);
    }

    @Override
    public Future<Long> renamenx(@RedisProtocolSupport.Key final CharSequence key,
                                 @RedisProtocolSupport.Key final CharSequence newkey) {
        return commander.renamenx(key, newkey);
    }

    @Override
    public Future<String> restore(@RedisProtocolSupport.Key final CharSequence key, final long ttl,
                                  final CharSequence serializedValue) {
        return commander.restore(key, ttl, serializedValue);
    }

    @Override
    public Future<String> restore(@RedisProtocolSupport.Key final CharSequence key, final long ttl,
                                  final CharSequence serializedValue,
                                  @Nullable final RedisProtocolSupport.RestoreReplace replace) {
        return commander.restore(key, ttl, serializedValue, replace);
    }

    @Override
    public <T> Future<List<T>> role() {
        return commander.role();
    }

    @Override
    public Future<String> rpop(@RedisProtocolSupport.Key final CharSequence key) {
        return commander.rpop(key);
    }

    @Override
    public Future<String> rpoplpush(@RedisProtocolSupport.Key final CharSequence source,
                                    @RedisProtocolSupport.Key final CharSequence destination) {
        return commander.rpoplpush(source, destination);
    }

    @Override
    public Future<Long> rpush(@RedisProtocolSupport.Key final CharSequence key, final CharSequence value) {
        return commander.rpush(key, value);
    }

    @Override
    public Future<Long> rpush(@RedisProtocolSupport.Key final CharSequence key, final CharSequence value1,
                              final CharSequence value2) {
        return commander.rpush(key, value1, value2);
    }

    @Override
    public Future<Long> rpush(@RedisProtocolSupport.Key final CharSequence key, final CharSequence value1,
                              final CharSequence value2, final CharSequence value3) {
        return commander.rpush(key, value1, value2, value3);
    }

    @Override
    public Future<Long> rpush(@RedisProtocolSupport.Key final CharSequence key,
                              final Collection<? extends CharSequence> values) {
        return commander.rpush(key, values);
    }

    @Override
    public Future<Long> rpushx(@RedisProtocolSupport.Key final CharSequence key, final CharSequence value) {
        return commander.rpushx(key, value);
    }

    @Override
    public Future<Long> sadd(@RedisProtocolSupport.Key final CharSequence key, final CharSequence member) {
        return commander.sadd(key, member);
    }

    @Override
    public Future<Long> sadd(@RedisProtocolSupport.Key final CharSequence key, final CharSequence member1,
                             final CharSequence member2) {
        return commander.sadd(key, member1, member2);
    }

    @Override
    public Future<Long> sadd(@RedisProtocolSupport.Key final CharSequence key, final CharSequence member1,
                             final CharSequence member2, final CharSequence member3) {
        return commander.sadd(key, member1, member2, member3);
    }

    @Override
    public Future<Long> sadd(@RedisProtocolSupport.Key final CharSequence key,
                             final Collection<? extends CharSequence> members) {
        return commander.sadd(key, members);
    }

    @Override
    public Future<String> save() {
        return commander.save();
    }

    @Override
    public <T> Future<List<T>> scan(final long cursor) {
        return commander.scan(cursor);
    }

    @Override
    public <T> Future<List<T>> scan(final long cursor, @Nullable final CharSequence matchPattern,
                                    @Nullable final Long count) {
        return commander.scan(cursor, matchPattern, count);
    }

    @Override
    public Future<Long> scard(@RedisProtocolSupport.Key final CharSequence key) {
        return commander.scard(key);
    }

    @Override
    public Future<String> scriptDebug(final RedisProtocolSupport.ScriptDebugMode mode) {
        return commander.scriptDebug(mode);
    }

    @Override
    public <T> Future<List<T>> scriptExists(final CharSequence sha1) {
        return commander.scriptExists(sha1);
    }

    @Override
    public <T> Future<List<T>> scriptExists(final CharSequence sha11, final CharSequence sha12) {
        return commander.scriptExists(sha11, sha12);
    }

    @Override
    public <T> Future<List<T>> scriptExists(final CharSequence sha11, final CharSequence sha12,
                                            final CharSequence sha13) {
        return commander.scriptExists(sha11, sha12, sha13);
    }

    @Override
    public <T> Future<List<T>> scriptExists(final Collection<? extends CharSequence> sha1s) {
        return commander.scriptExists(sha1s);
    }

    @Override
    public Future<String> scriptFlush() {
        return commander.scriptFlush();
    }

    @Override
    public Future<String> scriptKill() {
        return commander.scriptKill();
    }

    @Override
    public Future<String> scriptLoad(final CharSequence script) {
        return commander.scriptLoad(script);
    }

    @Override
    public <T> Future<List<T>> sdiff(@RedisProtocolSupport.Key final CharSequence firstkey) {
        return commander.sdiff(firstkey);
    }

    @Override
    public <T> Future<List<T>> sdiff(@RedisProtocolSupport.Key final CharSequence firstkey,
                                     @Nullable @RedisProtocolSupport.Key final CharSequence otherkey) {
        return commander.sdiff(firstkey, otherkey);
    }

    @Override
    public <T> Future<List<T>> sdiff(@RedisProtocolSupport.Key final CharSequence firstkey,
                                     @Nullable @RedisProtocolSupport.Key final CharSequence otherkey1,
                                     @Nullable @RedisProtocolSupport.Key final CharSequence otherkey2) {
        return commander.sdiff(firstkey, otherkey1, otherkey2);
    }

    @Override
    public <T> Future<List<T>> sdiff(@RedisProtocolSupport.Key final CharSequence firstkey,
                                     @Nullable @RedisProtocolSupport.Key final CharSequence otherkey1,
                                     @Nullable @RedisProtocolSupport.Key final CharSequence otherkey2,
                                     @Nullable @RedisProtocolSupport.Key final CharSequence otherkey3) {
        return commander.sdiff(firstkey, otherkey1, otherkey2, otherkey3);
    }

    @Override
    public <T> Future<List<T>> sdiff(@RedisProtocolSupport.Key final CharSequence firstkey,
                                     @RedisProtocolSupport.Key final Collection<? extends CharSequence> otherkeys) {
        return commander.sdiff(firstkey, otherkeys);
    }

    @Override
    public Future<Long> sdiffstore(@RedisProtocolSupport.Key final CharSequence destination,
                                   @RedisProtocolSupport.Key final CharSequence firstkey) {
        return commander.sdiffstore(destination, firstkey);
    }

    @Override
    public Future<Long> sdiffstore(@RedisProtocolSupport.Key final CharSequence destination,
                                   @RedisProtocolSupport.Key final CharSequence firstkey,
                                   @Nullable @RedisProtocolSupport.Key final CharSequence otherkey) {
        return commander.sdiffstore(destination, firstkey, otherkey);
    }

    @Override
    public Future<Long> sdiffstore(@RedisProtocolSupport.Key final CharSequence destination,
                                   @RedisProtocolSupport.Key final CharSequence firstkey,
                                   @Nullable @RedisProtocolSupport.Key final CharSequence otherkey1,
                                   @Nullable @RedisProtocolSupport.Key final CharSequence otherkey2) {
        return commander.sdiffstore(destination, firstkey, otherkey1, otherkey2);
    }

    @Override
    public Future<Long> sdiffstore(@RedisProtocolSupport.Key final CharSequence destination,
                                   @RedisProtocolSupport.Key final CharSequence firstkey,
                                   @Nullable @RedisProtocolSupport.Key final CharSequence otherkey1,
                                   @Nullable @RedisProtocolSupport.Key final CharSequence otherkey2,
                                   @Nullable @RedisProtocolSupport.Key final CharSequence otherkey3) {
        return commander.sdiffstore(destination, firstkey, otherkey1, otherkey2, otherkey3);
    }

    @Override
    public Future<Long> sdiffstore(@RedisProtocolSupport.Key final CharSequence destination,
                                   @RedisProtocolSupport.Key final CharSequence firstkey,
                                   @RedisProtocolSupport.Key final Collection<? extends CharSequence> otherkeys) {
        return commander.sdiffstore(destination, firstkey, otherkeys);
    }

    @Override
    public Future<String> select(final long index) {
        return commander.select(index);
    }

    @Override
    public Future<String> set(@RedisProtocolSupport.Key final CharSequence key, final CharSequence value) {
        return commander.set(key, value);
    }

    @Override
    public Future<String> set(@RedisProtocolSupport.Key final CharSequence key, final CharSequence value,
                              @Nullable final RedisProtocolSupport.ExpireDuration expireDuration,
                              @Nullable final RedisProtocolSupport.SetCondition condition) {
        return commander.set(key, value, expireDuration, condition);
    }

    @Override
    public Future<Long> setbit(@RedisProtocolSupport.Key final CharSequence key, final long offset,
                               final CharSequence value) {
        return commander.setbit(key, offset, value);
    }

    @Override
    public Future<String> setex(@RedisProtocolSupport.Key final CharSequence key, final long seconds,
                                final CharSequence value) {
        return commander.setex(key, seconds, value);
    }

    @Override
    public Future<Long> setnx(@RedisProtocolSupport.Key final CharSequence key, final CharSequence value) {
        return commander.setnx(key, value);
    }

    @Override
    public Future<Long> setrange(@RedisProtocolSupport.Key final CharSequence key, final long offset,
                                 final CharSequence value) {
        return commander.setrange(key, offset, value);
    }

    @Override
    public Future<String> shutdown() {
        return commander.shutdown();
    }

    @Override
    public Future<String> shutdown(@Nullable final RedisProtocolSupport.ShutdownSaveMode saveMode) {
        return commander.shutdown(saveMode);
    }

    @Override
    public <T> Future<List<T>> sinter(@RedisProtocolSupport.Key final CharSequence key) {
        return commander.sinter(key);
    }

    @Override
    public <T> Future<List<T>> sinter(@RedisProtocolSupport.Key final CharSequence key1,
                                      @RedisProtocolSupport.Key final CharSequence key2) {
        return commander.sinter(key1, key2);
    }

    @Override
    public <T> Future<List<T>> sinter(@RedisProtocolSupport.Key final CharSequence key1,
                                      @RedisProtocolSupport.Key final CharSequence key2,
                                      @RedisProtocolSupport.Key final CharSequence key3) {
        return commander.sinter(key1, key2, key3);
    }

    @Override
    public <T> Future<List<T>> sinter(@RedisProtocolSupport.Key final Collection<? extends CharSequence> keys) {
        return commander.sinter(keys);
    }

    @Override
    public Future<Long> sinterstore(@RedisProtocolSupport.Key final CharSequence destination,
                                    @RedisProtocolSupport.Key final CharSequence key) {
        return commander.sinterstore(destination, key);
    }

    @Override
    public Future<Long> sinterstore(@RedisProtocolSupport.Key final CharSequence destination,
                                    @RedisProtocolSupport.Key final CharSequence key1,
                                    @RedisProtocolSupport.Key final CharSequence key2) {
        return commander.sinterstore(destination, key1, key2);
    }

    @Override
    public Future<Long> sinterstore(@RedisProtocolSupport.Key final CharSequence destination,
                                    @RedisProtocolSupport.Key final CharSequence key1,
                                    @RedisProtocolSupport.Key final CharSequence key2,
                                    @RedisProtocolSupport.Key final CharSequence key3) {
        return commander.sinterstore(destination, key1, key2, key3);
    }

    @Override
    public Future<Long> sinterstore(@RedisProtocolSupport.Key final CharSequence destination,
                                    @RedisProtocolSupport.Key final Collection<? extends CharSequence> keys) {
        return commander.sinterstore(destination, keys);
    }

    @Override
    public Future<Long> sismember(@RedisProtocolSupport.Key final CharSequence key, final CharSequence member) {
        return commander.sismember(key, member);
    }

    @Override
    public Future<String> slaveof(final CharSequence host, final CharSequence port) {
        return commander.slaveof(host, port);
    }

    @Override
    public <T> Future<List<T>> slowlog(final CharSequence subcommand) {
        return commander.slowlog(subcommand);
    }

    @Override
    public <T> Future<List<T>> slowlog(final CharSequence subcommand, @Nullable final CharSequence argument) {
        return commander.slowlog(subcommand, argument);
    }

    @Override
    public <T> Future<List<T>> smembers(@RedisProtocolSupport.Key final CharSequence key) {
        return commander.smembers(key);
    }

    @Override
    public Future<Long> smove(@RedisProtocolSupport.Key final CharSequence source,
                              @RedisProtocolSupport.Key final CharSequence destination, final CharSequence member) {
        return commander.smove(source, destination, member);
    }

    @Override
    public <T> Future<List<T>> sort(@RedisProtocolSupport.Key final CharSequence key) {
        return commander.sort(key);
    }

    @Override
    public <T> Future<List<T>> sort(@RedisProtocolSupport.Key final CharSequence key,
                                    @Nullable final CharSequence byPattern,
                                    @Nullable final RedisProtocolSupport.OffsetCount offsetCount,
                                    final Collection<? extends CharSequence> getPatterns,
                                    @Nullable final RedisProtocolSupport.SortOrder order,
                                    @Nullable final RedisProtocolSupport.SortSorting sorting) {
        return commander.sort(key, byPattern, offsetCount, getPatterns, order, sorting);
    }

    @Override
    public Future<Long> sort(@RedisProtocolSupport.Key final CharSequence key,
                             @RedisProtocolSupport.Key final CharSequence storeDestination) {
        return commander.sort(key, storeDestination);
    }

    @Override
    public Future<Long> sort(@RedisProtocolSupport.Key final CharSequence key,
                             @RedisProtocolSupport.Key final CharSequence storeDestination,
                             @Nullable final CharSequence byPattern,
                             @Nullable final RedisProtocolSupport.OffsetCount offsetCount,
                             final Collection<? extends CharSequence> getPatterns,
                             @Nullable final RedisProtocolSupport.SortOrder order,
                             @Nullable final RedisProtocolSupport.SortSorting sorting) {
        return commander.sort(key, storeDestination, byPattern, offsetCount, getPatterns, order, sorting);
    }

    @Override
    public Future<String> spop(@RedisProtocolSupport.Key final CharSequence key) {
        return commander.spop(key);
    }

    @Override
    public Future<String> spop(@RedisProtocolSupport.Key final CharSequence key, @Nullable final Long count) {
        return commander.spop(key, count);
    }

    @Override
    public Future<String> srandmember(@RedisProtocolSupport.Key final CharSequence key) {
        return commander.srandmember(key);
    }

    @Override
    public Future<List<String>> srandmember(@RedisProtocolSupport.Key final CharSequence key, final long count) {
        return commander.srandmember(key, count);
    }

    @Override
    public Future<Long> srem(@RedisProtocolSupport.Key final CharSequence key, final CharSequence member) {
        return commander.srem(key, member);
    }

    @Override
    public Future<Long> srem(@RedisProtocolSupport.Key final CharSequence key, final CharSequence member1,
                             final CharSequence member2) {
        return commander.srem(key, member1, member2);
    }

    @Override
    public Future<Long> srem(@RedisProtocolSupport.Key final CharSequence key, final CharSequence member1,
                             final CharSequence member2, final CharSequence member3) {
        return commander.srem(key, member1, member2, member3);
    }

    @Override
    public Future<Long> srem(@RedisProtocolSupport.Key final CharSequence key,
                             final Collection<? extends CharSequence> members) {
        return commander.srem(key, members);
    }

    @Override
    public <T> Future<List<T>> sscan(@RedisProtocolSupport.Key final CharSequence key, final long cursor) {
        return commander.sscan(key, cursor);
    }

    @Override
    public <T> Future<List<T>> sscan(@RedisProtocolSupport.Key final CharSequence key, final long cursor,
                                     @Nullable final CharSequence matchPattern, @Nullable final Long count) {
        return commander.sscan(key, cursor, matchPattern, count);
    }

    @Override
    public Future<Long> strlen(@RedisProtocolSupport.Key final CharSequence key) {
        return commander.strlen(key);
    }

    @Override
    public <T> Future<List<T>> sunion(@RedisProtocolSupport.Key final CharSequence key) {
        return commander.sunion(key);
    }

    @Override
    public <T> Future<List<T>> sunion(@RedisProtocolSupport.Key final CharSequence key1,
                                      @RedisProtocolSupport.Key final CharSequence key2) {
        return commander.sunion(key1, key2);
    }

    @Override
    public <T> Future<List<T>> sunion(@RedisProtocolSupport.Key final CharSequence key1,
                                      @RedisProtocolSupport.Key final CharSequence key2,
                                      @RedisProtocolSupport.Key final CharSequence key3) {
        return commander.sunion(key1, key2, key3);
    }

    @Override
    public <T> Future<List<T>> sunion(@RedisProtocolSupport.Key final Collection<? extends CharSequence> keys) {
        return commander.sunion(keys);
    }

    @Override
    public Future<Long> sunionstore(@RedisProtocolSupport.Key final CharSequence destination,
                                    @RedisProtocolSupport.Key final CharSequence key) {
        return commander.sunionstore(destination, key);
    }

    @Override
    public Future<Long> sunionstore(@RedisProtocolSupport.Key final CharSequence destination,
                                    @RedisProtocolSupport.Key final CharSequence key1,
                                    @RedisProtocolSupport.Key final CharSequence key2) {
        return commander.sunionstore(destination, key1, key2);
    }

    @Override
    public Future<Long> sunionstore(@RedisProtocolSupport.Key final CharSequence destination,
                                    @RedisProtocolSupport.Key final CharSequence key1,
                                    @RedisProtocolSupport.Key final CharSequence key2,
                                    @RedisProtocolSupport.Key final CharSequence key3) {
        return commander.sunionstore(destination, key1, key2, key3);
    }

    @Override
    public Future<Long> sunionstore(@RedisProtocolSupport.Key final CharSequence destination,
                                    @RedisProtocolSupport.Key final Collection<? extends CharSequence> keys) {
        return commander.sunionstore(destination, keys);
    }

    @Override
    public Future<String> swapdb(final long index, final long index1) {
        return commander.swapdb(index, index1);
    }

    @Override
    public <T> Future<List<T>> time() {
        return commander.time();
    }

    @Override
    public Future<Long> touch(@RedisProtocolSupport.Key final CharSequence key) {
        return commander.touch(key);
    }

    @Override
    public Future<Long> touch(@RedisProtocolSupport.Key final CharSequence key1,
                              @RedisProtocolSupport.Key final CharSequence key2) {
        return commander.touch(key1, key2);
    }

    @Override
    public Future<Long> touch(@RedisProtocolSupport.Key final CharSequence key1,
                              @RedisProtocolSupport.Key final CharSequence key2,
                              @RedisProtocolSupport.Key final CharSequence key3) {
        return commander.touch(key1, key2, key3);
    }

    @Override
    public Future<Long> touch(@RedisProtocolSupport.Key final Collection<? extends CharSequence> keys) {
        return commander.touch(keys);
    }

    @Override
    public Future<Long> ttl(@RedisProtocolSupport.Key final CharSequence key) {
        return commander.ttl(key);
    }

    @Override
    public Future<String> type(@RedisProtocolSupport.Key final CharSequence key) {
        return commander.type(key);
    }

    @Override
    public Future<Long> unlink(@RedisProtocolSupport.Key final CharSequence key) {
        return commander.unlink(key);
    }

    @Override
    public Future<Long> unlink(@RedisProtocolSupport.Key final CharSequence key1,
                               @RedisProtocolSupport.Key final CharSequence key2) {
        return commander.unlink(key1, key2);
    }

    @Override
    public Future<Long> unlink(@RedisProtocolSupport.Key final CharSequence key1,
                               @RedisProtocolSupport.Key final CharSequence key2,
                               @RedisProtocolSupport.Key final CharSequence key3) {
        return commander.unlink(key1, key2, key3);
    }

    @Override
    public Future<Long> unlink(@RedisProtocolSupport.Key final Collection<? extends CharSequence> keys) {
        return commander.unlink(keys);
    }

    @Override
    public Future<String> unwatch() {
        return commander.unwatch();
    }

    @Override
    public Future<Long> wait(final long numslaves, final long timeout) {
        return commander.wait(numslaves, timeout);
    }

    @Override
    public Future<String> watch(@RedisProtocolSupport.Key final CharSequence key) {
        return commander.watch(key);
    }

    @Override
    public Future<String> watch(@RedisProtocolSupport.Key final CharSequence key1,
                                @RedisProtocolSupport.Key final CharSequence key2) {
        return commander.watch(key1, key2);
    }

    @Override
    public Future<String> watch(@RedisProtocolSupport.Key final CharSequence key1,
                                @RedisProtocolSupport.Key final CharSequence key2,
                                @RedisProtocolSupport.Key final CharSequence key3) {
        return commander.watch(key1, key2, key3);
    }

    @Override
    public Future<String> watch(@RedisProtocolSupport.Key final Collection<? extends CharSequence> keys) {
        return commander.watch(keys);
    }

    @Override
    public Future<String> xadd(@RedisProtocolSupport.Key final CharSequence key, final CharSequence id,
                               final CharSequence field, final CharSequence value) {
        return commander.xadd(key, id, field, value);
    }

    @Override
    public Future<String> xadd(@RedisProtocolSupport.Key final CharSequence key, final CharSequence id,
                               final CharSequence field1, final CharSequence value1, final CharSequence field2,
                               final CharSequence value2) {
        return commander.xadd(key, id, field1, value1, field2, value2);
    }

    @Override
    public Future<String> xadd(@RedisProtocolSupport.Key final CharSequence key, final CharSequence id,
                               final CharSequence field1, final CharSequence value1, final CharSequence field2,
                               final CharSequence value2, final CharSequence field3, final CharSequence value3) {
        return commander.xadd(key, id, field1, value1, field2, value2, field3, value3);
    }

    @Override
    public Future<String> xadd(@RedisProtocolSupport.Key final CharSequence key, final CharSequence id,
                               final Collection<RedisProtocolSupport.FieldValue> fieldValues) {
        return commander.xadd(key, id, fieldValues);
    }

    @Override
    public Future<Long> xlen(@RedisProtocolSupport.Key final CharSequence key) {
        return commander.xlen(key);
    }

    @Override
    public <T> Future<List<T>> xpending(@RedisProtocolSupport.Key final CharSequence key, final CharSequence group) {
        return commander.xpending(key, group);
    }

    @Override
    public <T> Future<List<T>> xpending(@RedisProtocolSupport.Key final CharSequence key, final CharSequence group,
                                        @Nullable final CharSequence start, @Nullable final CharSequence end,
                                        @Nullable final Long count, @Nullable final CharSequence consumer) {
        return commander.xpending(key, group, start, end, count, consumer);
    }

    @Override
    public <T> Future<List<T>> xrange(@RedisProtocolSupport.Key final CharSequence key, final CharSequence start,
                                      final CharSequence end) {
        return commander.xrange(key, start, end);
    }

    @Override
    public <T> Future<List<T>> xrange(@RedisProtocolSupport.Key final CharSequence key, final CharSequence start,
                                      final CharSequence end, @Nullable final Long count) {
        return commander.xrange(key, start, end, count);
    }

    @Override
    public <T> Future<List<T>> xread(@RedisProtocolSupport.Key final Collection<? extends CharSequence> keys,
                                     final Collection<? extends CharSequence> ids) {
        return commander.xread(keys, ids);
    }

    @Override
    public <T> Future<List<T>> xread(@Nullable final Long count, @Nullable final Long blockMilliseconds,
                                     @RedisProtocolSupport.Key final Collection<? extends CharSequence> keys,
                                     final Collection<? extends CharSequence> ids) {
        return commander.xread(count, blockMilliseconds, keys, ids);
    }

    @Override
    public <T> Future<List<T>> xreadgroup(final RedisProtocolSupport.GroupConsumer groupConsumer,
                                          @RedisProtocolSupport.Key final Collection<? extends CharSequence> keys,
                                          final Collection<? extends CharSequence> ids) {
        return commander.xreadgroup(groupConsumer, keys, ids);
    }

    @Override
    public <T> Future<List<T>> xreadgroup(final RedisProtocolSupport.GroupConsumer groupConsumer,
                                          @Nullable final Long count, @Nullable final Long blockMilliseconds,
                                          @RedisProtocolSupport.Key final Collection<? extends CharSequence> keys,
                                          final Collection<? extends CharSequence> ids) {
        return commander.xreadgroup(groupConsumer, count, blockMilliseconds, keys, ids);
    }

    @Override
    public <T> Future<List<T>> xrevrange(@RedisProtocolSupport.Key final CharSequence key, final CharSequence end,
                                         final CharSequence start) {
        return commander.xrevrange(key, end, start);
    }

    @Override
    public <T> Future<List<T>> xrevrange(@RedisProtocolSupport.Key final CharSequence key, final CharSequence end,
                                         final CharSequence start, @Nullable final Long count) {
        return commander.xrevrange(key, end, start, count);
    }

    @Override
    public Future<Long> zadd(@RedisProtocolSupport.Key final CharSequence key,
                             final Collection<RedisProtocolSupport.ScoreMember> scoreMembers) {
        return commander.zadd(key, scoreMembers);
    }

    @Override
    public Future<Long> zadd(@RedisProtocolSupport.Key final CharSequence key,
                             @Nullable final RedisProtocolSupport.ZaddCondition condition,
                             @Nullable final RedisProtocolSupport.ZaddChange change, final double score,
                             final CharSequence member) {
        return commander.zadd(key, condition, change, score, member);
    }

    @Override
    public Future<Long> zadd(@RedisProtocolSupport.Key final CharSequence key,
                             @Nullable final RedisProtocolSupport.ZaddCondition condition,
                             @Nullable final RedisProtocolSupport.ZaddChange change, final double score1,
                             final CharSequence member1, final double score2, final CharSequence member2) {
        return commander.zadd(key, condition, change, score1, member1, score2, member2);
    }

    @Override
    public Future<Long> zadd(@RedisProtocolSupport.Key final CharSequence key,
                             @Nullable final RedisProtocolSupport.ZaddCondition condition,
                             @Nullable final RedisProtocolSupport.ZaddChange change, final double score1,
                             final CharSequence member1, final double score2, final CharSequence member2,
                             final double score3, final CharSequence member3) {
        return commander.zadd(key, condition, change, score1, member1, score2, member2, score3, member3);
    }

    @Override
    public Future<Long> zadd(@RedisProtocolSupport.Key final CharSequence key,
                             @Nullable final RedisProtocolSupport.ZaddCondition condition,
                             @Nullable final RedisProtocolSupport.ZaddChange change,
                             final Collection<RedisProtocolSupport.ScoreMember> scoreMembers) {
        return commander.zadd(key, condition, change, scoreMembers);
    }

    @Override
    public Future<Double> zaddIncr(@RedisProtocolSupport.Key final CharSequence key,
                                   final Collection<RedisProtocolSupport.ScoreMember> scoreMembers) {
        return commander.zaddIncr(key, scoreMembers);
    }

    @Override
    public Future<Double> zaddIncr(@RedisProtocolSupport.Key final CharSequence key,
                                   @Nullable final RedisProtocolSupport.ZaddCondition condition,
                                   @Nullable final RedisProtocolSupport.ZaddChange change, final double score,
                                   final CharSequence member) {
        return commander.zaddIncr(key, condition, change, score, member);
    }

    @Override
    public Future<Double> zaddIncr(@RedisProtocolSupport.Key final CharSequence key,
                                   @Nullable final RedisProtocolSupport.ZaddCondition condition,
                                   @Nullable final RedisProtocolSupport.ZaddChange change, final double score1,
                                   final CharSequence member1, final double score2, final CharSequence member2) {
        return commander.zaddIncr(key, condition, change, score1, member1, score2, member2);
    }

    @Override
    public Future<Double> zaddIncr(@RedisProtocolSupport.Key final CharSequence key,
                                   @Nullable final RedisProtocolSupport.ZaddCondition condition,
                                   @Nullable final RedisProtocolSupport.ZaddChange change, final double score1,
                                   final CharSequence member1, final double score2, final CharSequence member2,
                                   final double score3, final CharSequence member3) {
        return commander.zaddIncr(key, condition, change, score1, member1, score2, member2, score3, member3);
    }

    @Override
    public Future<Double> zaddIncr(@RedisProtocolSupport.Key final CharSequence key,
                                   @Nullable final RedisProtocolSupport.ZaddCondition condition,
                                   @Nullable final RedisProtocolSupport.ZaddChange change,
                                   final Collection<RedisProtocolSupport.ScoreMember> scoreMembers) {
        return commander.zaddIncr(key, condition, change, scoreMembers);
    }

    @Override
    public Future<Long> zcard(@RedisProtocolSupport.Key final CharSequence key) {
        return commander.zcard(key);
    }

    @Override
    public Future<Long> zcount(@RedisProtocolSupport.Key final CharSequence key, final double min, final double max) {
        return commander.zcount(key, min, max);
    }

    @Override
    public Future<Double> zincrby(@RedisProtocolSupport.Key final CharSequence key, final long increment,
                                  final CharSequence member) {
        return commander.zincrby(key, increment, member);
    }

    @Override
    public Future<Long> zinterstore(@RedisProtocolSupport.Key final CharSequence destination, final long numkeys,
                                    @RedisProtocolSupport.Key final Collection<? extends CharSequence> keys) {
        return commander.zinterstore(destination, numkeys, keys);
    }

    @Override
    public Future<Long> zinterstore(@RedisProtocolSupport.Key final CharSequence destination, final long numkeys,
                                    @RedisProtocolSupport.Key final Collection<? extends CharSequence> keys,
                                    final Collection<Long> weightses,
                                    @Nullable final RedisProtocolSupport.ZinterstoreAggregate aggregate) {
        return commander.zinterstore(destination, numkeys, keys, weightses, aggregate);
    }

    @Override
    public Future<Long> zlexcount(@RedisProtocolSupport.Key final CharSequence key, final CharSequence min,
                                  final CharSequence max) {
        return commander.zlexcount(key, min, max);
    }

    @Override
    public <T> Future<List<T>> zpopmax(@RedisProtocolSupport.Key final CharSequence key) {
        return commander.zpopmax(key);
    }

    @Override
    public <T> Future<List<T>> zpopmax(@RedisProtocolSupport.Key final CharSequence key, @Nullable final Long count) {
        return commander.zpopmax(key, count);
    }

    @Override
    public <T> Future<List<T>> zpopmin(@RedisProtocolSupport.Key final CharSequence key) {
        return commander.zpopmin(key);
    }

    @Override
    public <T> Future<List<T>> zpopmin(@RedisProtocolSupport.Key final CharSequence key, @Nullable final Long count) {
        return commander.zpopmin(key, count);
    }

    @Override
    public <T> Future<List<T>> zrange(@RedisProtocolSupport.Key final CharSequence key, final long start,
                                      final long stop) {
        return commander.zrange(key, start, stop);
    }

    @Override
    public <T> Future<List<T>> zrange(@RedisProtocolSupport.Key final CharSequence key, final long start,
                                      final long stop,
                                      @Nullable final RedisProtocolSupport.ZrangeWithscores withscores) {
        return commander.zrange(key, start, stop, withscores);
    }

    @Override
    public <T> Future<List<T>> zrangebylex(@RedisProtocolSupport.Key final CharSequence key, final CharSequence min,
                                           final CharSequence max) {
        return commander.zrangebylex(key, min, max);
    }

    @Override
    public <T> Future<List<T>> zrangebylex(@RedisProtocolSupport.Key final CharSequence key, final CharSequence min,
                                           final CharSequence max,
                                           @Nullable final RedisProtocolSupport.OffsetCount offsetCount) {
        return commander.zrangebylex(key, min, max, offsetCount);
    }

    @Override
    public <T> Future<List<T>> zrangebyscore(@RedisProtocolSupport.Key final CharSequence key, final double min,
                                             final double max) {
        return commander.zrangebyscore(key, min, max);
    }

    @Override
    public <T> Future<List<T>> zrangebyscore(@RedisProtocolSupport.Key final CharSequence key, final double min,
                                             final double max,
                                             @Nullable final RedisProtocolSupport.ZrangebyscoreWithscores withscores,
                                             @Nullable final RedisProtocolSupport.OffsetCount offsetCount) {
        return commander.zrangebyscore(key, min, max, withscores, offsetCount);
    }

    @Override
    public Future<Long> zrank(@RedisProtocolSupport.Key final CharSequence key, final CharSequence member) {
        return commander.zrank(key, member);
    }

    @Override
    public Future<Long> zrem(@RedisProtocolSupport.Key final CharSequence key, final CharSequence member) {
        return commander.zrem(key, member);
    }

    @Override
    public Future<Long> zrem(@RedisProtocolSupport.Key final CharSequence key, final CharSequence member1,
                             final CharSequence member2) {
        return commander.zrem(key, member1, member2);
    }

    @Override
    public Future<Long> zrem(@RedisProtocolSupport.Key final CharSequence key, final CharSequence member1,
                             final CharSequence member2, final CharSequence member3) {
        return commander.zrem(key, member1, member2, member3);
    }

    @Override
    public Future<Long> zrem(@RedisProtocolSupport.Key final CharSequence key,
                             final Collection<? extends CharSequence> members) {
        return commander.zrem(key, members);
    }

    @Override
    public Future<Long> zremrangebylex(@RedisProtocolSupport.Key final CharSequence key, final CharSequence min,
                                       final CharSequence max) {
        return commander.zremrangebylex(key, min, max);
    }

    @Override
    public Future<Long> zremrangebyrank(@RedisProtocolSupport.Key final CharSequence key, final long start,
                                        final long stop) {
        return commander.zremrangebyrank(key, start, stop);
    }

    @Override
    public Future<Long> zremrangebyscore(@RedisProtocolSupport.Key final CharSequence key, final double min,
                                         final double max) {
        return commander.zremrangebyscore(key, min, max);
    }

    @Override
    public <T> Future<List<T>> zrevrange(@RedisProtocolSupport.Key final CharSequence key, final long start,
                                         final long stop) {
        return commander.zrevrange(key, start, stop);
    }

    @Override
    public <T> Future<List<T>> zrevrange(@RedisProtocolSupport.Key final CharSequence key, final long start,
                                         final long stop,
                                         @Nullable final RedisProtocolSupport.ZrevrangeWithscores withscores) {
        return commander.zrevrange(key, start, stop, withscores);
    }

    @Override
    public <T> Future<List<T>> zrevrangebylex(@RedisProtocolSupport.Key final CharSequence key, final CharSequence max,
                                              final CharSequence min) {
        return commander.zrevrangebylex(key, max, min);
    }

    @Override
    public <T> Future<List<T>> zrevrangebylex(@RedisProtocolSupport.Key final CharSequence key, final CharSequence max,
                                              final CharSequence min,
                                              @Nullable final RedisProtocolSupport.OffsetCount offsetCount) {
        return commander.zrevrangebylex(key, max, min, offsetCount);
    }

    @Override
    public <T> Future<List<T>> zrevrangebyscore(@RedisProtocolSupport.Key final CharSequence key, final double max,
                                                final double min) {
        return commander.zrevrangebyscore(key, max, min);
    }

    @Override
    public <T> Future<List<T>> zrevrangebyscore(@RedisProtocolSupport.Key final CharSequence key, final double max,
                                                final double min,
                                                @Nullable final RedisProtocolSupport.ZrevrangebyscoreWithscores withscores,
                                                @Nullable final RedisProtocolSupport.OffsetCount offsetCount) {
        return commander.zrevrangebyscore(key, max, min, withscores, offsetCount);
    }

    @Override
    public Future<Long> zrevrank(@RedisProtocolSupport.Key final CharSequence key, final CharSequence member) {
        return commander.zrevrank(key, member);
    }

    @Override
    public <T> Future<List<T>> zscan(@RedisProtocolSupport.Key final CharSequence key, final long cursor) {
        return commander.zscan(key, cursor);
    }

    @Override
    public <T> Future<List<T>> zscan(@RedisProtocolSupport.Key final CharSequence key, final long cursor,
                                     @Nullable final CharSequence matchPattern, @Nullable final Long count) {
        return commander.zscan(key, cursor, matchPattern, count);
    }

    @Override
    public Future<Double> zscore(@RedisProtocolSupport.Key final CharSequence key, final CharSequence member) {
        return commander.zscore(key, member);
    }

    @Override
    public Future<Long> zunionstore(@RedisProtocolSupport.Key final CharSequence destination, final long numkeys,
                                    @RedisProtocolSupport.Key final Collection<? extends CharSequence> keys) {
        return commander.zunionstore(destination, numkeys, keys);
    }

    @Override
    public Future<Long> zunionstore(@RedisProtocolSupport.Key final CharSequence destination, final long numkeys,
                                    @RedisProtocolSupport.Key final Collection<? extends CharSequence> keys,
                                    final Collection<Long> weightses,
                                    @Nullable final RedisProtocolSupport.ZunionstoreAggregate aggregate) {
        return commander.zunionstore(destination, numkeys, keys, weightses, aggregate);
    }
}

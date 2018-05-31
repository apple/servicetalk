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
import io.servicetalk.buffer.api.BufferAllocator;
import io.servicetalk.buffer.api.CompositeBuffer;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.util.EnumSet;
import java.util.Objects;
import javax.annotation.Generated;
import javax.annotation.Nonnegative;

import static io.servicetalk.redis.api.RedisRequests.addRequestArgument;
import static io.servicetalk.redis.internal.RedisUtils.toRespBulkString;
import static java.nio.charset.StandardCharsets.US_ASCII;
import static java.util.Objects.requireNonNull;

/**
 * Commands, sub-commands, sub-types and tuples used by the Redis clients.
 */
@Generated({})
public final class RedisProtocolSupport {

    RedisProtocolSupport() {
        // no instantiation
    }

    @Documented
    @Retention(RetentionPolicy.RUNTIME)
    @Target({ElementType.METHOD})
    public @interface Cmd {

        /**
         * Get the {@link Command}.
         *
         * @return the {@link Command}.
         */
        Command value();
    }

    @Documented
    @Retention(RetentionPolicy.RUNTIME)
    @Target({ElementType.METHOD, ElementType.PARAMETER})
    public @interface SubCmd {

        /**
         * Get the {@link SubCommand}.
         *
         * @return the {@link SubCommand}.
         */
        SubCommand value();
    }

    @Documented
    @Retention(RetentionPolicy.RUNTIME)
    @Target({ElementType.PARAMETER})
    public @interface Option {
    }

    @Documented
    @Retention(RetentionPolicy.RUNTIME)
    @Target({ElementType.PARAMETER, ElementType.FIELD})
    public @interface Key {
    }

    @Documented
    @Retention(RetentionPolicy.RUNTIME)
    @Target({ElementType.PARAMETER})
    public @interface Tuple {
    }

    public interface TupleArgument {

        /**
         * Write this tuple's content to a {@link CompositeBuffer}.
         *
         * @param compositeBuffer the {@link CompositeBuffer} to write to
         * @param bufferAllocator the {@link BufferAllocator} to use when writing
         * @return the count of arguments written to the buffer
         */
        int writeTo(CompositeBuffer compositeBuffer, BufferAllocator bufferAllocator);

        /**
         * Modify the {@code builder} according to this objects internals.
         *
         * @param builder The builder modify for this objects contributions.
         */
        void buildAttributes(RedisPartitionAttributesBuilder builder);
    }

    public interface BitfieldOperation extends TupleArgument {
    }

    public enum IntegerType implements RedisData.CompleteRequestRedisData {

        I01("i1", 1), I02("i2", 2), I03("i3", 3), I04("i4", 4), I05("i5", 5), I06("i6", 6), I07("i7", 7), I08("i8", 8),
        I09("i9", 9), I10("i10", 10), I11("i11", 11), I12("i12", 12), I13("i13", 13), I14("i14", 14), I15("i15", 15),
        I16("i16", 16), I17("i17", 17), I18("i18", 18), I19("i19", 19), I20("i20", 20), I21("i21", 21), I22("i22", 22),
        I23("i23", 23), I24("i24", 24), I25("i25", 25), I26("i26", 26), I27("i27", 27), I28("i28", 28), I29("i29", 29),
        I30("i30", 30), I31("i31", 31), I32("i32", 32), I33("i33", 33), I34("i34", 34), I35("i35", 35), I36("i36", 36),
        I37("i37", 37), I38("i38", 38), I39("i39", 39), I40("i40", 40), I41("i41", 41), I42("i42", 42), I43("i43", 43),
        I44("i44", 44), I45("i45", 45), I46("i46", 46), I47("i47", 47), I48("i48", 48), I49("i49", 49), I50("i50", 50),
        I51("i51", 51), I52("i52", 52), I53("i53", 53), I54("i54", 54), I55("i55", 55), I56("i56", 56), I57("i57", 57),
        I58("i58", 58), I59("i59", 59), I60("i60", 60), I61("i61", 61), I62("i62", 62), I63("i63", 63), I64("i64", 64),
        U01("u1", 1), U02("u2", 2), U03("u3", 3), U04("u4", 4), U05("u5", 5), U06("u6", 6), U07("u7", 7), U08("u8", 8),
        U09("u9", 9), U10("u10", 10), U11("u11", 11), U12("u12", 12), U13("u13", 13), U14("u14", 14), U15("u15", 15),
        U16("u16", 16), U17("u17", 17), U18("u18", 18), U19("u19", 19), U20("u20", 20), U21("u21", 21), U22("u22", 22),
        U23("u23", 23), U24("u24", 24), U25("u25", 25), U26("u26", 26), U27("u27", 27), U28("u28", 28), U29("u29", 29),
        U30("u30", 30), U31("u31", 31), U32("u32", 32), U33("u33", 33), U34("u34", 34), U35("u35", 35), U36("u36", 36),
        U37("u37", 37), U38("u38", 38), U39("u39", 39), U40("u40", 40), U41("u41", 41), U42("u42", 42), U43("u43", 43),
        U44("u44", 44), U45("u45", 45), U46("u46", 46), U47("u47", 47), U48("u48", 48), U49("u49", 49), U50("u50", 50),
        U51("u51", 51), U52("u52", 52), U53("u53", 53), U54("u54", 54), U55("u55", 55), U56("u56", 56), U57("u57", 57),
        U58("u58", 58), U59("u59", 59), U60("u60", 60), U61("u61", 61), U62("u62", 62), U63("u63", 63);

        private final byte[] b;

        private final int size;

        IntegerType(final String s, final int i) {
            b = s.getBytes(US_ASCII);
            size = i;
        }

        @Override
        public Buffer toRESPArgument(BufferAllocator allocator) {
            return toRespBulkString(b, allocator);
        }

        /**
         * Get the size in bits represented by this type.
         *
         * @return the bit size.
         */
        public int getBitSize() {
            return size;
        }
    }

    /**
     * <a href="https://redis.io/commands/command#flags">Command Flags</a>.
     */
    public enum CommandFlag {

        ADMIN, ASKING, DENYOOM, FAST, LOADING, MOVABLEKEYS, NOSCRIPT, PUBSUB, RANDOM, READONLY, SKIP_MONITOR, SORT_FOR_SCRIPT,
        STALE, WRITE
    }

    public enum Command implements RedisData.CompleteRequestRedisData {

        APPEND("APPEND", EnumSet.of(CommandFlag.WRITE, CommandFlag.DENYOOM), true),
        AUTH("AUTH", EnumSet.of(CommandFlag.NOSCRIPT, CommandFlag.LOADING, CommandFlag.STALE, CommandFlag.FAST), false),
        BGREWRITEAOF("BGREWRITEAOF", EnumSet.of(CommandFlag.ADMIN), false),
        BGSAVE("BGSAVE", EnumSet.of(CommandFlag.ADMIN), false), BITCOUNT("BITCOUNT", EnumSet.of(CommandFlag.READONLY), true),
        BITFIELD("BITFIELD", EnumSet.of(CommandFlag.WRITE, CommandFlag.DENYOOM), true),
        BITOP("BITOP", EnumSet.of(CommandFlag.WRITE, CommandFlag.DENYOOM), true),
        BITPOS("BITPOS", EnumSet.of(CommandFlag.READONLY), true),
        BLPOP("BLPOP", EnumSet.of(CommandFlag.WRITE, CommandFlag.NOSCRIPT), true),
        BRPOP("BRPOP", EnumSet.of(CommandFlag.WRITE, CommandFlag.NOSCRIPT), true),
        BRPOPLPUSH("BRPOPLPUSH", EnumSet.of(CommandFlag.WRITE, CommandFlag.DENYOOM, CommandFlag.NOSCRIPT), true),
        BZPOPMAX("BZPOPMAX", EnumSet.of(CommandFlag.WRITE, CommandFlag.NOSCRIPT, CommandFlag.FAST), true),
        BZPOPMIN("BZPOPMIN", EnumSet.of(CommandFlag.WRITE, CommandFlag.NOSCRIPT, CommandFlag.FAST), true),
        CLIENT("CLIENT", EnumSet.of(CommandFlag.ADMIN, CommandFlag.NOSCRIPT), false),
        CLUSTER("CLUSTER", EnumSet.of(CommandFlag.ADMIN), false),
        COMMAND("COMMAND", EnumSet.of(CommandFlag.LOADING, CommandFlag.STALE), false),
        CONFIG("CONFIG", EnumSet.of(CommandFlag.ADMIN, CommandFlag.LOADING, CommandFlag.STALE), false),
        DBSIZE("DBSIZE", EnumSet.of(CommandFlag.READONLY, CommandFlag.FAST), false),
        DEBUG("DEBUG", EnumSet.of(CommandFlag.ADMIN, CommandFlag.NOSCRIPT), false),
        DECR("DECR", EnumSet.of(CommandFlag.WRITE, CommandFlag.DENYOOM, CommandFlag.FAST), true),
        DECRBY("DECRBY", EnumSet.of(CommandFlag.WRITE, CommandFlag.DENYOOM, CommandFlag.FAST), true),
        DEL("DEL", EnumSet.of(CommandFlag.WRITE), true),
        DISCARD("DISCARD", EnumSet.of(CommandFlag.NOSCRIPT, CommandFlag.FAST), false),
        DUMP("DUMP", EnumSet.of(CommandFlag.READONLY), true), ECHO("ECHO", EnumSet.of(CommandFlag.FAST), false),
        EVAL("EVAL", EnumSet.of(CommandFlag.NOSCRIPT, CommandFlag.MOVABLEKEYS), false),
        EVALSHA("EVALSHA", EnumSet.of(CommandFlag.NOSCRIPT, CommandFlag.MOVABLEKEYS), false),
        EXEC("EXEC", EnumSet.of(CommandFlag.NOSCRIPT, CommandFlag.SKIP_MONITOR), false),
        EXISTS("EXISTS", EnumSet.of(CommandFlag.READONLY, CommandFlag.FAST), true),
        EXPIRE("EXPIRE", EnumSet.of(CommandFlag.WRITE, CommandFlag.FAST), true),
        EXPIREAT("EXPIREAT", EnumSet.of(CommandFlag.WRITE, CommandFlag.FAST), true),
        FLUSHALL("FLUSHALL", EnumSet.of(CommandFlag.WRITE), false), FLUSHDB("FLUSHDB", EnumSet.of(CommandFlag.WRITE), false),
        GEOADD("GEOADD", EnumSet.of(CommandFlag.WRITE, CommandFlag.DENYOOM), true),
        GEODIST("GEODIST", EnumSet.of(CommandFlag.READONLY), true), GEOHASH("GEOHASH", EnumSet.of(CommandFlag.READONLY), true),
        GEOPOS("GEOPOS", EnumSet.of(CommandFlag.READONLY), true),
        GEORADIUS("GEORADIUS", EnumSet.of(CommandFlag.WRITE, CommandFlag.MOVABLEKEYS), true),
        GEORADIUSBYMEMBER("GEORADIUSBYMEMBER", EnumSet.of(CommandFlag.WRITE, CommandFlag.MOVABLEKEYS), true),
        GET("GET", EnumSet.of(CommandFlag.READONLY, CommandFlag.FAST), true),
        GETBIT("GETBIT", EnumSet.of(CommandFlag.READONLY, CommandFlag.FAST), true),
        GETRANGE("GETRANGE", EnumSet.of(CommandFlag.READONLY), true),
        GETSET("GETSET", EnumSet.of(CommandFlag.WRITE, CommandFlag.DENYOOM), true),
        HDEL("HDEL", EnumSet.of(CommandFlag.WRITE, CommandFlag.FAST), true),
        HEXISTS("HEXISTS", EnumSet.of(CommandFlag.READONLY, CommandFlag.FAST), true),
        HGET("HGET", EnumSet.of(CommandFlag.READONLY, CommandFlag.FAST), true),
        HGETALL("HGETALL", EnumSet.of(CommandFlag.READONLY), true),
        HINCRBY("HINCRBY", EnumSet.of(CommandFlag.WRITE, CommandFlag.DENYOOM, CommandFlag.FAST), true),
        HINCRBYFLOAT("HINCRBYFLOAT", EnumSet.of(CommandFlag.WRITE, CommandFlag.DENYOOM, CommandFlag.FAST), true),
        HKEYS("HKEYS", EnumSet.of(CommandFlag.READONLY, CommandFlag.SORT_FOR_SCRIPT), true),
        HLEN("HLEN", EnumSet.of(CommandFlag.READONLY, CommandFlag.FAST), true),
        HMGET("HMGET", EnumSet.of(CommandFlag.READONLY, CommandFlag.FAST), true),
        HMSET("HMSET", EnumSet.of(CommandFlag.WRITE, CommandFlag.DENYOOM, CommandFlag.FAST), true),
        HSCAN("HSCAN", EnumSet.of(CommandFlag.READONLY, CommandFlag.RANDOM), true),
        HSET("HSET", EnumSet.of(CommandFlag.WRITE, CommandFlag.DENYOOM, CommandFlag.FAST), true),
        HSETNX("HSETNX", EnumSet.of(CommandFlag.WRITE, CommandFlag.DENYOOM, CommandFlag.FAST), true),
        HSTRLEN("HSTRLEN", EnumSet.of(CommandFlag.READONLY, CommandFlag.FAST), true),
        HVALS("HVALS", EnumSet.of(CommandFlag.READONLY, CommandFlag.SORT_FOR_SCRIPT), true),
        INCR("INCR", EnumSet.of(CommandFlag.WRITE, CommandFlag.DENYOOM, CommandFlag.FAST), true),
        INCRBY("INCRBY", EnumSet.of(CommandFlag.WRITE, CommandFlag.DENYOOM, CommandFlag.FAST), true),
        INCRBYFLOAT("INCRBYFLOAT", EnumSet.of(CommandFlag.WRITE, CommandFlag.DENYOOM, CommandFlag.FAST), true),
        INFO("INFO", EnumSet.of(CommandFlag.LOADING, CommandFlag.STALE), false),
        KEYS("KEYS", EnumSet.of(CommandFlag.READONLY, CommandFlag.SORT_FOR_SCRIPT), false),
        LASTSAVE("LASTSAVE", EnumSet.of(CommandFlag.RANDOM, CommandFlag.FAST), false),
        LINDEX("LINDEX", EnumSet.of(CommandFlag.READONLY), true),
        LINSERT("LINSERT", EnumSet.of(CommandFlag.WRITE, CommandFlag.DENYOOM), true),
        LLEN("LLEN", EnumSet.of(CommandFlag.READONLY, CommandFlag.FAST), true),
        LPOP("LPOP", EnumSet.of(CommandFlag.WRITE, CommandFlag.FAST), true),
        LPUSH("LPUSH", EnumSet.of(CommandFlag.WRITE, CommandFlag.DENYOOM, CommandFlag.FAST), true),
        LPUSHX("LPUSHX", EnumSet.of(CommandFlag.WRITE, CommandFlag.DENYOOM, CommandFlag.FAST), true),
        LRANGE("LRANGE", EnumSet.of(CommandFlag.READONLY), true), LREM("LREM", EnumSet.of(CommandFlag.WRITE), true),
        LSET("LSET", EnumSet.of(CommandFlag.WRITE, CommandFlag.DENYOOM), true),
        LTRIM("LTRIM", EnumSet.of(CommandFlag.WRITE), true), MEMORY("MEMORY", EnumSet.of(CommandFlag.READONLY), false),
        MGET("MGET", EnumSet.of(CommandFlag.READONLY, CommandFlag.FAST), true),
        MIGRATE("MIGRATE", EnumSet.of(CommandFlag.WRITE, CommandFlag.MOVABLEKEYS), false),
        MONITOR("MONITOR", EnumSet.of(CommandFlag.ADMIN, CommandFlag.NOSCRIPT), false),
        MOVE("MOVE", EnumSet.of(CommandFlag.WRITE, CommandFlag.FAST), true),
        MSET("MSET", EnumSet.of(CommandFlag.WRITE, CommandFlag.DENYOOM), true),
        MSETNX("MSETNX", EnumSet.of(CommandFlag.WRITE, CommandFlag.DENYOOM), true),
        MULTI("MULTI", EnumSet.of(CommandFlag.NOSCRIPT, CommandFlag.FAST), false),
        OBJECT("OBJECT", EnumSet.of(CommandFlag.READONLY), true),
        PERSIST("PERSIST", EnumSet.of(CommandFlag.WRITE, CommandFlag.FAST), true),
        PEXPIRE("PEXPIRE", EnumSet.of(CommandFlag.WRITE, CommandFlag.FAST), true),
        PEXPIREAT("PEXPIREAT", EnumSet.of(CommandFlag.WRITE, CommandFlag.FAST), true),
        PFADD("PFADD", EnumSet.of(CommandFlag.WRITE, CommandFlag.DENYOOM, CommandFlag.FAST), true),
        PFCOUNT("PFCOUNT", EnumSet.of(CommandFlag.READONLY), true),
        PFMERGE("PFMERGE", EnumSet.of(CommandFlag.WRITE, CommandFlag.DENYOOM), true),
        PING("PING", EnumSet.of(CommandFlag.STALE, CommandFlag.FAST), false),
        PSETEX("PSETEX", EnumSet.of(CommandFlag.WRITE, CommandFlag.DENYOOM), true),
        PSUBSCRIBE("PSUBSCRIBE", EnumSet.of(CommandFlag.PUBSUB, CommandFlag.NOSCRIPT, CommandFlag.LOADING, CommandFlag.STALE),
                    false),
        PTTL("PTTL", EnumSet.of(CommandFlag.READONLY, CommandFlag.FAST), true),
        PUBLISH("PUBLISH", EnumSet.of(CommandFlag.PUBSUB, CommandFlag.LOADING, CommandFlag.STALE, CommandFlag.FAST), false),
        PUBSUB("PUBSUB", EnumSet.of(CommandFlag.PUBSUB, CommandFlag.RANDOM, CommandFlag.LOADING, CommandFlag.STALE), false),
        PUNSUBSCRIBE("PUNSUBSCRIBE", EnumSet.of(CommandFlag.PUBSUB, CommandFlag.NOSCRIPT, CommandFlag.LOADING, CommandFlag.STALE),
                    false),
        QUIT("QUIT", EnumSet.noneOf(CommandFlag.class), false),
        RANDOMKEY("RANDOMKEY", EnumSet.of(CommandFlag.READONLY, CommandFlag.RANDOM), false),
        READONLY("READONLY", EnumSet.of(CommandFlag.FAST), false), READWRITE("READWRITE", EnumSet.of(CommandFlag.FAST), false),
        RENAME("RENAME", EnumSet.of(CommandFlag.WRITE), true),
        RENAMENX("RENAMENX", EnumSet.of(CommandFlag.WRITE, CommandFlag.FAST), true),
        RESTORE("RESTORE", EnumSet.of(CommandFlag.WRITE, CommandFlag.DENYOOM), true),
        ROLE("ROLE", EnumSet.of(CommandFlag.NOSCRIPT, CommandFlag.LOADING, CommandFlag.STALE), false),
        RPOP("RPOP", EnumSet.of(CommandFlag.WRITE, CommandFlag.FAST), true),
        RPOPLPUSH("RPOPLPUSH", EnumSet.of(CommandFlag.WRITE, CommandFlag.DENYOOM), true),
        RPUSH("RPUSH", EnumSet.of(CommandFlag.WRITE, CommandFlag.DENYOOM, CommandFlag.FAST), true),
        RPUSHX("RPUSHX", EnumSet.of(CommandFlag.WRITE, CommandFlag.DENYOOM, CommandFlag.FAST), true),
        SADD("SADD", EnumSet.of(CommandFlag.WRITE, CommandFlag.DENYOOM, CommandFlag.FAST), true),
        SAVE("SAVE", EnumSet.of(CommandFlag.ADMIN, CommandFlag.NOSCRIPT), false),
        SCAN("SCAN", EnumSet.of(CommandFlag.READONLY, CommandFlag.RANDOM), false),
        SCARD("SCARD", EnumSet.of(CommandFlag.READONLY, CommandFlag.FAST), true),
        SCRIPT("SCRIPT", EnumSet.of(CommandFlag.NOSCRIPT), false),
        SDIFF("SDIFF", EnumSet.of(CommandFlag.READONLY, CommandFlag.SORT_FOR_SCRIPT), true),
        SDIFFSTORE("SDIFFSTORE", EnumSet.of(CommandFlag.WRITE, CommandFlag.DENYOOM), true),
        SELECT("SELECT", EnumSet.of(CommandFlag.LOADING, CommandFlag.FAST), false),
        SET("SET", EnumSet.of(CommandFlag.WRITE, CommandFlag.DENYOOM), true),
        SETBIT("SETBIT", EnumSet.of(CommandFlag.WRITE, CommandFlag.DENYOOM), true),
        SETEX("SETEX", EnumSet.of(CommandFlag.WRITE, CommandFlag.DENYOOM), true),
        SETNX("SETNX", EnumSet.of(CommandFlag.WRITE, CommandFlag.DENYOOM, CommandFlag.FAST), true),
        SETRANGE("SETRANGE", EnumSet.of(CommandFlag.WRITE, CommandFlag.DENYOOM), true),
        SHUTDOWN("SHUTDOWN", EnumSet.of(CommandFlag.ADMIN, CommandFlag.LOADING, CommandFlag.STALE), false),
        SINTER("SINTER", EnumSet.of(CommandFlag.READONLY, CommandFlag.SORT_FOR_SCRIPT), true),
        SINTERSTORE("SINTERSTORE", EnumSet.of(CommandFlag.WRITE, CommandFlag.DENYOOM), true),
        SISMEMBER("SISMEMBER", EnumSet.of(CommandFlag.READONLY, CommandFlag.FAST), true),
        SLAVEOF("SLAVEOF", EnumSet.of(CommandFlag.ADMIN, CommandFlag.NOSCRIPT, CommandFlag.STALE), false),
        SLOWLOG("SLOWLOG", EnumSet.of(CommandFlag.ADMIN), false),
        SMEMBERS("SMEMBERS", EnumSet.of(CommandFlag.READONLY, CommandFlag.SORT_FOR_SCRIPT), true),
        SMOVE("SMOVE", EnumSet.of(CommandFlag.WRITE, CommandFlag.FAST), true),
        SORT("SORT", EnumSet.of(CommandFlag.WRITE, CommandFlag.DENYOOM, CommandFlag.MOVABLEKEYS), true),
        SPOP("SPOP", EnumSet.of(CommandFlag.WRITE, CommandFlag.RANDOM, CommandFlag.FAST), true),
        SRANDMEMBER("SRANDMEMBER", EnumSet.of(CommandFlag.READONLY, CommandFlag.RANDOM), true),
        SREM("SREM", EnumSet.of(CommandFlag.WRITE, CommandFlag.FAST), true),
        SSCAN("SSCAN", EnumSet.of(CommandFlag.READONLY, CommandFlag.RANDOM), true),
        STRLEN("STRLEN", EnumSet.of(CommandFlag.READONLY, CommandFlag.FAST), true),
        SUBSCRIBE("SUBSCRIBE", EnumSet.of(CommandFlag.PUBSUB, CommandFlag.NOSCRIPT, CommandFlag.LOADING, CommandFlag.STALE),
                    false),
        SUNION("SUNION", EnumSet.of(CommandFlag.READONLY, CommandFlag.SORT_FOR_SCRIPT), true),
        SUNIONSTORE("SUNIONSTORE", EnumSet.of(CommandFlag.WRITE, CommandFlag.DENYOOM), true),
        SWAPDB("SWAPDB", EnumSet.of(CommandFlag.WRITE, CommandFlag.FAST), false),
        SYNC("SYNC", EnumSet.of(CommandFlag.READONLY, CommandFlag.ADMIN, CommandFlag.NOSCRIPT), false),
        TIME("TIME", EnumSet.of(CommandFlag.RANDOM, CommandFlag.FAST), false),
        TOUCH("TOUCH", EnumSet.of(CommandFlag.READONLY, CommandFlag.FAST), true),
        TTL("TTL", EnumSet.of(CommandFlag.READONLY, CommandFlag.FAST), true),
        TYPE("TYPE", EnumSet.of(CommandFlag.READONLY, CommandFlag.FAST), true),
        UNLINK("UNLINK", EnumSet.of(CommandFlag.WRITE, CommandFlag.FAST), true),
        UNSUBSCRIBE("UNSUBSCRIBE", EnumSet.of(CommandFlag.PUBSUB, CommandFlag.NOSCRIPT, CommandFlag.LOADING, CommandFlag.STALE),
                    false),
        UNWATCH("UNWATCH", EnumSet.of(CommandFlag.NOSCRIPT, CommandFlag.FAST), false),
        WAIT("WAIT", EnumSet.of(CommandFlag.NOSCRIPT), false),
        WATCH("WATCH", EnumSet.of(CommandFlag.NOSCRIPT, CommandFlag.FAST), true),
        XADD("XADD", EnumSet.of(CommandFlag.WRITE, CommandFlag.DENYOOM, CommandFlag.FAST), true),
        XLEN("XLEN", EnumSet.of(CommandFlag.READONLY, CommandFlag.FAST), true),
        XPENDING("XPENDING", EnumSet.of(CommandFlag.READONLY), true), XRANGE("XRANGE", EnumSet.of(CommandFlag.READONLY), true),
        XREAD("XREAD", EnumSet.of(CommandFlag.READONLY, CommandFlag.NOSCRIPT, CommandFlag.MOVABLEKEYS), true),
        XREADGROUP("XREADGROUP", EnumSet.of(CommandFlag.WRITE, CommandFlag.NOSCRIPT, CommandFlag.MOVABLEKEYS), true),
        XREVRANGE("XREVRANGE", EnumSet.of(CommandFlag.READONLY), true),
        ZADD("ZADD", EnumSet.of(CommandFlag.WRITE, CommandFlag.DENYOOM, CommandFlag.FAST), true),
        ZCARD("ZCARD", EnumSet.of(CommandFlag.READONLY, CommandFlag.FAST), true),
        ZCOUNT("ZCOUNT", EnumSet.of(CommandFlag.READONLY, CommandFlag.FAST), true),
        ZINCRBY("ZINCRBY", EnumSet.of(CommandFlag.WRITE, CommandFlag.DENYOOM, CommandFlag.FAST), true),
        ZINTERSTORE("ZINTERSTORE", EnumSet.of(CommandFlag.WRITE, CommandFlag.DENYOOM, CommandFlag.MOVABLEKEYS), false),
        ZLEXCOUNT("ZLEXCOUNT", EnumSet.of(CommandFlag.READONLY, CommandFlag.FAST), true),
        ZPOPMAX("ZPOPMAX", EnumSet.of(CommandFlag.WRITE, CommandFlag.FAST), true),
        ZPOPMIN("ZPOPMIN", EnumSet.of(CommandFlag.WRITE, CommandFlag.FAST), true),
        ZRANGE("ZRANGE", EnumSet.of(CommandFlag.READONLY), true),
        ZRANGEBYLEX("ZRANGEBYLEX", EnumSet.of(CommandFlag.READONLY), true),
        ZRANGEBYSCORE("ZRANGEBYSCORE", EnumSet.of(CommandFlag.READONLY), true),
        ZRANK("ZRANK", EnumSet.of(CommandFlag.READONLY, CommandFlag.FAST), true),
        ZREM("ZREM", EnumSet.of(CommandFlag.WRITE, CommandFlag.FAST), true),
        ZREMRANGEBYLEX("ZREMRANGEBYLEX", EnumSet.of(CommandFlag.WRITE), true),
        ZREMRANGEBYRANK("ZREMRANGEBYRANK", EnumSet.of(CommandFlag.WRITE), true),
        ZREMRANGEBYSCORE("ZREMRANGEBYSCORE", EnumSet.of(CommandFlag.WRITE), true),
        ZREVRANGE("ZREVRANGE", EnumSet.of(CommandFlag.READONLY), true),
        ZREVRANGEBYLEX("ZREVRANGEBYLEX", EnumSet.of(CommandFlag.READONLY), true),
        ZREVRANGEBYSCORE("ZREVRANGEBYSCORE", EnumSet.of(CommandFlag.READONLY), true),
        ZREVRANK("ZREVRANK", EnumSet.of(CommandFlag.READONLY, CommandFlag.FAST), true),
        ZSCAN("ZSCAN", EnumSet.of(CommandFlag.READONLY, CommandFlag.RANDOM), true),
        ZSCORE("ZSCORE", EnumSet.of(CommandFlag.READONLY, CommandFlag.FAST), true),
        ZUNIONSTORE("ZUNIONSTORE", EnumSet.of(CommandFlag.WRITE, CommandFlag.DENYOOM, CommandFlag.MOVABLEKEYS), false);

        private final byte[] b;

        private final EnumSet<CommandFlag> flags;

        private final boolean supportsKeys;

        Command(final String s, final EnumSet<CommandFlag> flags, final boolean supportsKeys) {
            b = s.getBytes(US_ASCII);
            this.flags = flags;
            this.supportsKeys = supportsKeys;
        }

        @Override
        public Buffer toRESPArgument(BufferAllocator allocator) {
            return toRespBulkString(b, allocator);
        }

        /**
         * Check if this command has a specific {@link CommandFlag}.
         *
         * @param flag The flag to check.
         * @return {@code true} if this command has {@code flag}.
         */
        public boolean hasFlag(final CommandFlag flag) {
            return flags.contains(flag);
        }

        /**
         * Check if this command supports keys.
         *
         * @return {@code true} if this command can have one or more keys associated with it.
         */
        public boolean supportsKeys() {
            return supportsKeys;
        }
    }

    public enum SetExpire implements RedisData.CompleteRequestRedisData {

        EX("EX"), PX("PX");

        private final byte[] b;

        SetExpire(final String s) {
            b = s.getBytes(US_ASCII);
        }

        @Override
        public Buffer toRESPArgument(BufferAllocator allocator) {
            return toRespBulkString(b, allocator);
        }
    }

    public static final class ExpireDuration implements TupleArgument {

        static final int SIZE = 2;

        public final SetExpire expire;

        public final long duration;

        /**
         * Instantiates a new {@link ExpireDuration}.
         *
         * @param expire the expire
         * @param duration the duration
         */
        public ExpireDuration(final SetExpire expire, final long duration) {
            this.expire = requireNonNull(expire);
            this.duration = duration;
        }

        @Override
        public boolean equals(final Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }
            final ExpireDuration that = (ExpireDuration) o;
            return Objects.equals(expire, that.expire) && Objects.equals(duration, that.duration);
        }

        @Override
        public int hashCode() {
            return Objects.hash(expire, duration);
        }

        @Override
        public int writeTo(final CompositeBuffer buf, final BufferAllocator allocator) {
            addRequestArgument(expire, buf, allocator);
            addRequestArgument(duration, buf, allocator);
            return SIZE;
        }

        @Override
        public void buildAttributes(final RedisPartitionAttributesBuilder builder) {
        }
    }

    public enum SubCommand implements RedisData.CompleteRequestRedisData {

        ADDR("ADDR"), ADDSLOTS("ADDSLOTS"), AGGREGATE("AGGREGATE"), BLOCK("BLOCK"), BY("BY"), CHANNELS("CHANNELS"),
        COUNT("COUNT"), COUNT_FAILURE_REPORTS("COUNT-FAILURE-REPORTS"), COUNTKEYSINSLOT("COUNTKEYSINSLOT"), DEBUG("DEBUG"),
        DELSLOTS("DELSLOTS"), DOCTOR("DOCTOR"), ENCODING("ENCODING"), EXISTS("EXISTS"), FAILOVER("FAILOVER"), FLUSH("FLUSH"),
        FORGET("FORGET"), FREQ("FREQ"), GET("GET"), GETKEYS("GETKEYS"), GETKEYSINSLOT("GETKEYSINSLOT"), GETNAME("GETNAME"),
        GROUP("GROUP"), HELP("HELP"), ID("ID"), IDLETIME("IDLETIME"), INCRBY("INCRBY"), INFO("INFO"), KEYSLOT("KEYSLOT"),
        KILL("KILL"), LIMIT("LIMIT"), LIST("LIST"), LOAD("LOAD"), MALLOC_STATS("MALLOC-STATS"), MATCH("MATCH"), MEET("MEET"),
        NODES("NODES"), NUMPAT("NUMPAT"), NUMSUB("NUMSUB"), OBJECT("OBJECT"), OVERFLOW("OVERFLOW"), PAUSE("PAUSE"),
        PURGE("PURGE"), REFCOUNT("REFCOUNT"), REPLICATE("REPLICATE"), REPLY("REPLY"), RESET("RESET"), RESETSTAT("RESETSTAT"),
        REWRITE("REWRITE"), SAMPLES("SAMPLES"), SAVECONFIG("SAVECONFIG"), SEGFAULT("SEGFAULT"), SET("SET"),
        SET_CONFIG_EPOCH("SET-CONFIG-EPOCH"), SETNAME("SETNAME"), SETSLOT("SETSLOT"), SKIPME("SKIPME"), SLAVES("SLAVES"),
        SLOTS("SLOTS"), STATS("STATS"), STORE("STORE"), STOREDIST("STOREDIST"), TYPE("TYPE"), USAGE("USAGE"), WEIGHTS("WEIGHTS");

        private final byte[] b;

        SubCommand(final String s) {
            b = s.getBytes(US_ASCII);
        }

        @Override
        public Buffer toRESPArgument(BufferAllocator allocator) {
            return toRespBulkString(b, allocator);
        }
    }

    public enum BitfieldOverflow implements RedisData.CompleteRequestRedisData {

        FAIL("FAIL"), SAT("SAT"), WRAP("WRAP");

        private final byte[] b;

        BitfieldOverflow(final String s) {
            b = s.getBytes(US_ASCII);
        }

        @Override
        public Buffer toRESPArgument(BufferAllocator allocator) {
            return toRespBulkString(b, allocator);
        }
    }

    public enum ClientKillType implements RedisData.CompleteRequestRedisData {

        MASTER("master"), NORMAL("normal"), PUBSUB("pubsub"), SLAVE("slave");

        private final byte[] b;

        ClientKillType(final String s) {
            b = s.getBytes(US_ASCII);
        }

        @Override
        public Buffer toRESPArgument(BufferAllocator allocator) {
            return toRespBulkString(b, allocator);
        }
    }

    public enum ClientReplyReplyMode implements RedisData.CompleteRequestRedisData {

        OFF("OFF"), ON("ON"), SKIP("SKIP");

        private final byte[] b;

        ClientReplyReplyMode(final String s) {
            b = s.getBytes(US_ASCII);
        }

        @Override
        public Buffer toRESPArgument(BufferAllocator allocator) {
            return toRespBulkString(b, allocator);
        }
    }

    public enum ClusterFailoverOptions implements RedisData.CompleteRequestRedisData {

        FORCE("FORCE"), TAKEOVER("TAKEOVER");

        private final byte[] b;

        ClusterFailoverOptions(final String s) {
            b = s.getBytes(US_ASCII);
        }

        @Override
        public Buffer toRESPArgument(BufferAllocator allocator) {
            return toRespBulkString(b, allocator);
        }
    }

    public enum ClusterResetResetType implements RedisData.CompleteRequestRedisData {

        HARD("HARD"), SOFT("SOFT");

        private final byte[] b;

        ClusterResetResetType(final String s) {
            b = s.getBytes(US_ASCII);
        }

        @Override
        public Buffer toRESPArgument(BufferAllocator allocator) {
            return toRespBulkString(b, allocator);
        }
    }

    public enum ClusterSetslotSubcommand implements RedisData.CompleteRequestRedisData {

        IMPORTING("IMPORTING"), MIGRATING("MIGRATING"), NODE("NODE"), STABLE("STABLE");

        private final byte[] b;

        ClusterSetslotSubcommand(final String s) {
            b = s.getBytes(US_ASCII);
        }

        @Override
        public Buffer toRESPArgument(BufferAllocator allocator) {
            return toRespBulkString(b, allocator);
        }
    }

    public enum FlushallAsync implements RedisData.CompleteRequestRedisData {

        ASYNC("ASYNC");

        private final byte[] b;

        FlushallAsync(final String s) {
            b = s.getBytes(US_ASCII);
        }

        @Override
        public Buffer toRESPArgument(BufferAllocator allocator) {
            return toRespBulkString(b, allocator);
        }
    }

    public enum FlushdbAsync implements RedisData.CompleteRequestRedisData {

        ASYNC("ASYNC");

        private final byte[] b;

        FlushdbAsync(final String s) {
            b = s.getBytes(US_ASCII);
        }

        @Override
        public Buffer toRESPArgument(BufferAllocator allocator) {
            return toRespBulkString(b, allocator);
        }
    }

    public enum GeoradiusOrder implements RedisData.CompleteRequestRedisData {

        ASC("ASC"), DESC("DESC");

        private final byte[] b;

        GeoradiusOrder(final String s) {
            b = s.getBytes(US_ASCII);
        }

        @Override
        public Buffer toRESPArgument(BufferAllocator allocator) {
            return toRespBulkString(b, allocator);
        }
    }

    public enum GeoradiusUnit implements RedisData.CompleteRequestRedisData {

        FT("ft"), KM("km"), M("m"), MI("mi");

        private final byte[] b;

        GeoradiusUnit(final String s) {
            b = s.getBytes(US_ASCII);
        }

        @Override
        public Buffer toRESPArgument(BufferAllocator allocator) {
            return toRespBulkString(b, allocator);
        }
    }

    public enum GeoradiusWithcoord implements RedisData.CompleteRequestRedisData {

        WITHCOORD("WITHCOORD");

        private final byte[] b;

        GeoradiusWithcoord(final String s) {
            b = s.getBytes(US_ASCII);
        }

        @Override
        public Buffer toRESPArgument(BufferAllocator allocator) {
            return toRespBulkString(b, allocator);
        }
    }

    public enum GeoradiusWithdist implements RedisData.CompleteRequestRedisData {

        WITHDIST("WITHDIST");

        private final byte[] b;

        GeoradiusWithdist(final String s) {
            b = s.getBytes(US_ASCII);
        }

        @Override
        public Buffer toRESPArgument(BufferAllocator allocator) {
            return toRespBulkString(b, allocator);
        }
    }

    public enum GeoradiusWithhash implements RedisData.CompleteRequestRedisData {

        WITHHASH("WITHHASH");

        private final byte[] b;

        GeoradiusWithhash(final String s) {
            b = s.getBytes(US_ASCII);
        }

        @Override
        public Buffer toRESPArgument(BufferAllocator allocator) {
            return toRespBulkString(b, allocator);
        }
    }

    public enum GeoradiusbymemberOrder implements RedisData.CompleteRequestRedisData {

        ASC("ASC"), DESC("DESC");

        private final byte[] b;

        GeoradiusbymemberOrder(final String s) {
            b = s.getBytes(US_ASCII);
        }

        @Override
        public Buffer toRESPArgument(BufferAllocator allocator) {
            return toRespBulkString(b, allocator);
        }
    }

    public enum GeoradiusbymemberUnit implements RedisData.CompleteRequestRedisData {

        FT("ft"), KM("km"), M("m"), MI("mi");

        private final byte[] b;

        GeoradiusbymemberUnit(final String s) {
            b = s.getBytes(US_ASCII);
        }

        @Override
        public Buffer toRESPArgument(BufferAllocator allocator) {
            return toRespBulkString(b, allocator);
        }
    }

    public enum GeoradiusbymemberWithcoord implements RedisData.CompleteRequestRedisData {

        WITHCOORD("WITHCOORD");

        private final byte[] b;

        GeoradiusbymemberWithcoord(final String s) {
            b = s.getBytes(US_ASCII);
        }

        @Override
        public Buffer toRESPArgument(BufferAllocator allocator) {
            return toRespBulkString(b, allocator);
        }
    }

    public enum GeoradiusbymemberWithdist implements RedisData.CompleteRequestRedisData {

        WITHDIST("WITHDIST");

        private final byte[] b;

        GeoradiusbymemberWithdist(final String s) {
            b = s.getBytes(US_ASCII);
        }

        @Override
        public Buffer toRESPArgument(BufferAllocator allocator) {
            return toRespBulkString(b, allocator);
        }
    }

    public enum GeoradiusbymemberWithhash implements RedisData.CompleteRequestRedisData {

        WITHHASH("WITHHASH");

        private final byte[] b;

        GeoradiusbymemberWithhash(final String s) {
            b = s.getBytes(US_ASCII);
        }

        @Override
        public Buffer toRESPArgument(BufferAllocator allocator) {
            return toRespBulkString(b, allocator);
        }
    }

    public enum LinsertWhere implements RedisData.CompleteRequestRedisData {

        AFTER("AFTER"), BEFORE("BEFORE");

        private final byte[] b;

        LinsertWhere(final String s) {
            b = s.getBytes(US_ASCII);
        }

        @Override
        public Buffer toRESPArgument(BufferAllocator allocator) {
            return toRespBulkString(b, allocator);
        }
    }

    public enum RestoreReplace implements RedisData.CompleteRequestRedisData {

        REPLACE("REPLACE");

        private final byte[] b;

        RestoreReplace(final String s) {
            b = s.getBytes(US_ASCII);
        }

        @Override
        public Buffer toRESPArgument(BufferAllocator allocator) {
            return toRespBulkString(b, allocator);
        }
    }

    public enum ScriptDebugMode implements RedisData.CompleteRequestRedisData {

        NO("NO"), SYNC("SYNC"), YES("YES");

        private final byte[] b;

        ScriptDebugMode(final String s) {
            b = s.getBytes(US_ASCII);
        }

        @Override
        public Buffer toRESPArgument(BufferAllocator allocator) {
            return toRespBulkString(b, allocator);
        }
    }

    public enum SetCondition implements RedisData.CompleteRequestRedisData {

        NX("NX"), XX("XX");

        private final byte[] b;

        SetCondition(final String s) {
            b = s.getBytes(US_ASCII);
        }

        @Override
        public Buffer toRESPArgument(BufferAllocator allocator) {
            return toRespBulkString(b, allocator);
        }
    }

    public enum ShutdownSaveMode implements RedisData.CompleteRequestRedisData {

        NOSAVE("NOSAVE"), SAVE("SAVE");

        private final byte[] b;

        ShutdownSaveMode(final String s) {
            b = s.getBytes(US_ASCII);
        }

        @Override
        public Buffer toRESPArgument(BufferAllocator allocator) {
            return toRespBulkString(b, allocator);
        }
    }

    public enum SortOrder implements RedisData.CompleteRequestRedisData {

        ASC("ASC"), DESC("DESC");

        private final byte[] b;

        SortOrder(final String s) {
            b = s.getBytes(US_ASCII);
        }

        @Override
        public Buffer toRESPArgument(BufferAllocator allocator) {
            return toRespBulkString(b, allocator);
        }
    }

    public enum SortSorting implements RedisData.CompleteRequestRedisData {

        ALPHA("ALPHA");

        private final byte[] b;

        SortSorting(final String s) {
            b = s.getBytes(US_ASCII);
        }

        @Override
        public Buffer toRESPArgument(BufferAllocator allocator) {
            return toRespBulkString(b, allocator);
        }
    }

    public enum XreadStreams implements RedisData.CompleteRequestRedisData {

        STREAMS("STREAMS");

        private final byte[] b;

        XreadStreams(final String s) {
            b = s.getBytes(US_ASCII);
        }

        @Override
        public Buffer toRESPArgument(BufferAllocator allocator) {
            return toRespBulkString(b, allocator);
        }
    }

    public enum XreadgroupStreams implements RedisData.CompleteRequestRedisData {

        STREAMS("STREAMS");

        private final byte[] b;

        XreadgroupStreams(final String s) {
            b = s.getBytes(US_ASCII);
        }

        @Override
        public Buffer toRESPArgument(BufferAllocator allocator) {
            return toRespBulkString(b, allocator);
        }
    }

    public enum ZaddChange implements RedisData.CompleteRequestRedisData {

        CH("CH");

        private final byte[] b;

        ZaddChange(final String s) {
            b = s.getBytes(US_ASCII);
        }

        @Override
        public Buffer toRESPArgument(BufferAllocator allocator) {
            return toRespBulkString(b, allocator);
        }
    }

    public enum ZaddCondition implements RedisData.CompleteRequestRedisData {

        NX("NX"), XX("XX");

        private final byte[] b;

        ZaddCondition(final String s) {
            b = s.getBytes(US_ASCII);
        }

        @Override
        public Buffer toRESPArgument(BufferAllocator allocator) {
            return toRespBulkString(b, allocator);
        }
    }

    public enum ZaddIncrement implements RedisData.CompleteRequestRedisData {

        INCR("INCR");

        private final byte[] b;

        ZaddIncrement(final String s) {
            b = s.getBytes(US_ASCII);
        }

        @Override
        public Buffer toRESPArgument(BufferAllocator allocator) {
            return toRespBulkString(b, allocator);
        }
    }

    public enum ZinterstoreAggregate implements RedisData.CompleteRequestRedisData {

        MAX("MAX"), MIN("MIN"), SUM("SUM");

        private final byte[] b;

        ZinterstoreAggregate(final String s) {
            b = s.getBytes(US_ASCII);
        }

        @Override
        public Buffer toRESPArgument(BufferAllocator allocator) {
            return toRespBulkString(b, allocator);
        }
    }

    public enum ZrangeWithscores implements RedisData.CompleteRequestRedisData {

        WITHSCORES("WITHSCORES");

        private final byte[] b;

        ZrangeWithscores(final String s) {
            b = s.getBytes(US_ASCII);
        }

        @Override
        public Buffer toRESPArgument(BufferAllocator allocator) {
            return toRespBulkString(b, allocator);
        }
    }

    public enum ZrangebyscoreWithscores implements RedisData.CompleteRequestRedisData {

        WITHSCORES("WITHSCORES");

        private final byte[] b;

        ZrangebyscoreWithscores(final String s) {
            b = s.getBytes(US_ASCII);
        }

        @Override
        public Buffer toRESPArgument(BufferAllocator allocator) {
            return toRespBulkString(b, allocator);
        }
    }

    public enum ZrevrangeWithscores implements RedisData.CompleteRequestRedisData {

        WITHSCORES("WITHSCORES");

        private final byte[] b;

        ZrevrangeWithscores(final String s) {
            b = s.getBytes(US_ASCII);
        }

        @Override
        public Buffer toRESPArgument(BufferAllocator allocator) {
            return toRespBulkString(b, allocator);
        }
    }

    public enum ZrevrangebyscoreWithscores implements RedisData.CompleteRequestRedisData {

        WITHSCORES("WITHSCORES");

        private final byte[] b;

        ZrevrangebyscoreWithscores(final String s) {
            b = s.getBytes(US_ASCII);
        }

        @Override
        public Buffer toRESPArgument(BufferAllocator allocator) {
            return toRespBulkString(b, allocator);
        }
    }

    public enum ZunionstoreAggregate implements RedisData.CompleteRequestRedisData {

        MAX("MAX"), MIN("MIN"), SUM("SUM");

        private final byte[] b;

        ZunionstoreAggregate(final String s) {
            b = s.getBytes(US_ASCII);
        }

        @Override
        public Buffer toRESPArgument(BufferAllocator allocator) {
            return toRespBulkString(b, allocator);
        }
    }

    public static final class BufferFieldValue implements TupleArgument {

        static final int SIZE = 2;

        public final Buffer field;

        public final Buffer value;

        /**
         * Instantiates a new {@link BufferFieldValue}.
         *
         * @param field the field
         * @param value the value
         */
        public BufferFieldValue(final Buffer field, final Buffer value) {
            this.field = requireNonNull(field);
            this.value = requireNonNull(value);
        }

        @Override
        public boolean equals(final Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }
            final BufferFieldValue that = (BufferFieldValue) o;
            return Objects.equals(field, that.field) && Objects.equals(value, that.value);
        }

        @Override
        public int hashCode() {
            return Objects.hash(field, value);
        }

        @Override
        public int writeTo(final CompositeBuffer buf, final BufferAllocator allocator) {
            addRequestArgument(field, buf, allocator);
            addRequestArgument(value, buf, allocator);
            return SIZE;
        }

        @Override
        public void buildAttributes(final RedisPartitionAttributesBuilder builder) {
        }
    }

    public static final class BufferGroupConsumer implements TupleArgument {

        static final int SIZE = 3;

        public final Buffer group;

        public final Buffer consumer;

        /**
         * Instantiates a new {@link BufferGroupConsumer}.
         *
         * @param group the group
         * @param consumer the consumer
         */
        public BufferGroupConsumer(final Buffer group, final Buffer consumer) {
            this.group = requireNonNull(group);
            this.consumer = requireNonNull(consumer);
        }

        @Override
        public boolean equals(final Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }
            final BufferGroupConsumer that = (BufferGroupConsumer) o;
            return Objects.equals(group, that.group) && Objects.equals(consumer, that.consumer);
        }

        @Override
        public int hashCode() {
            return Objects.hash(group, consumer);
        }

        @Override
        public int writeTo(final CompositeBuffer buf, final BufferAllocator allocator) {
            addRequestArgument(SubCommand.GROUP, buf, allocator);
            addRequestArgument(group, buf, allocator);
            addRequestArgument(consumer, buf, allocator);
            return SIZE;
        }

        @Override
        public void buildAttributes(final RedisPartitionAttributesBuilder builder) {
        }
    }

    public static final class BufferKeyValue implements TupleArgument {

        static final int SIZE = 2;

        @Key
        public final Buffer key;

        public final Buffer value;

        /**
         * Instantiates a new {@link BufferKeyValue}.
         *
         * @param key the key
         * @param value the value
         */
        public BufferKeyValue(@Key final Buffer key, final Buffer value) {
            this.key = requireNonNull(key);
            this.value = requireNonNull(value);
        }

        @Override
        public boolean equals(final Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }
            final BufferKeyValue that = (BufferKeyValue) o;
            return Objects.equals(key, that.key) && Objects.equals(value, that.value);
        }

        @Override
        public int hashCode() {
            return Objects.hash(key, value);
        }

        @Override
        public int writeTo(final CompositeBuffer buf, final BufferAllocator allocator) {
            addRequestArgument(key, buf, allocator);
            addRequestArgument(value, buf, allocator);
            return SIZE;
        }

        @Override
        public void buildAttributes(final RedisPartitionAttributesBuilder builder) {
            builder.addKey(key);
        }
    }

    public static final class BufferLongitudeLatitudeMember implements TupleArgument {

        static final int SIZE = 3;

        public final double longitude;

        public final double latitude;

        public final Buffer member;

        /**
         * Instantiates a new {@link BufferLongitudeLatitudeMember}.
         *
         * @param longitude the longitude
         * @param latitude the latitude
         * @param member the member
         */
        public BufferLongitudeLatitudeMember(final double longitude, final double latitude, final Buffer member) {
            this.longitude = longitude;
            this.latitude = latitude;
            this.member = requireNonNull(member);
        }

        @Override
        public boolean equals(final Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }
            final BufferLongitudeLatitudeMember that = (BufferLongitudeLatitudeMember) o;
            return Objects.equals(longitude, that.longitude) && Objects.equals(latitude, that.latitude) &&
                        Objects.equals(member, that.member);
        }

        @Override
        public int hashCode() {
            return Objects.hash(longitude, latitude, member);
        }

        @Override
        public int writeTo(final CompositeBuffer buf, final BufferAllocator allocator) {
            addRequestArgument(longitude, buf, allocator);
            addRequestArgument(latitude, buf, allocator);
            addRequestArgument(member, buf, allocator);
            return SIZE;
        }

        @Override
        public void buildAttributes(final RedisPartitionAttributesBuilder builder) {
        }
    }

    public static final class BufferScoreMember implements TupleArgument {

        static final int SIZE = 2;

        public final double score;

        public final Buffer member;

        /**
         * Instantiates a new {@link BufferScoreMember}.
         *
         * @param score the score
         * @param member the member
         */
        public BufferScoreMember(final double score, final Buffer member) {
            this.score = score;
            this.member = requireNonNull(member);
        }

        @Override
        public boolean equals(final Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }
            final BufferScoreMember that = (BufferScoreMember) o;
            return Objects.equals(score, that.score) && Objects.equals(member, that.member);
        }

        @Override
        public int hashCode() {
            return Objects.hash(score, member);
        }

        @Override
        public int writeTo(final CompositeBuffer buf, final BufferAllocator allocator) {
            addRequestArgument(score, buf, allocator);
            addRequestArgument(member, buf, allocator);
            return SIZE;
        }

        @Override
        public void buildAttributes(final RedisPartitionAttributesBuilder builder) {
        }
    }

    public static final class FieldValue implements TupleArgument {

        static final int SIZE = 2;

        public final CharSequence field;

        public final CharSequence value;

        /**
         * Instantiates a new {@link FieldValue}.
         *
         * @param field the field
         * @param value the value
         */
        public FieldValue(final CharSequence field, final CharSequence value) {
            this.field = requireNonNull(field);
            this.value = requireNonNull(value);
        }

        @Override
        public boolean equals(final Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }
            final FieldValue that = (FieldValue) o;
            return Objects.equals(field, that.field) && Objects.equals(value, that.value);
        }

        @Override
        public int hashCode() {
            return Objects.hash(field, value);
        }

        @Override
        public int writeTo(final CompositeBuffer buf, final BufferAllocator allocator) {
            addRequestArgument(field, buf, allocator);
            addRequestArgument(value, buf, allocator);
            return SIZE;
        }

        @Override
        public void buildAttributes(final RedisPartitionAttributesBuilder builder) {
        }
    }

    public static final class GroupConsumer implements TupleArgument {

        static final int SIZE = 3;

        public final CharSequence group;

        public final CharSequence consumer;

        /**
         * Instantiates a new {@link GroupConsumer}.
         *
         * @param group the group
         * @param consumer the consumer
         */
        public GroupConsumer(final CharSequence group, final CharSequence consumer) {
            this.group = requireNonNull(group);
            this.consumer = requireNonNull(consumer);
        }

        @Override
        public boolean equals(final Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }
            final GroupConsumer that = (GroupConsumer) o;
            return Objects.equals(group, that.group) && Objects.equals(consumer, that.consumer);
        }

        @Override
        public int hashCode() {
            return Objects.hash(group, consumer);
        }

        @Override
        public int writeTo(final CompositeBuffer buf, final BufferAllocator allocator) {
            addRequestArgument(SubCommand.GROUP, buf, allocator);
            addRequestArgument(group, buf, allocator);
            addRequestArgument(consumer, buf, allocator);
            return SIZE;
        }

        @Override
        public void buildAttributes(final RedisPartitionAttributesBuilder builder) {
        }
    }

    public static final class KeyValue implements TupleArgument {

        static final int SIZE = 2;

        @Key
        public final CharSequence key;

        public final CharSequence value;

        /**
         * Instantiates a new {@link KeyValue}.
         *
         * @param key the key
         * @param value the value
         */
        public KeyValue(@Key final CharSequence key, final CharSequence value) {
            this.key = requireNonNull(key);
            this.value = requireNonNull(value);
        }

        @Override
        public boolean equals(final Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }
            final KeyValue that = (KeyValue) o;
            return Objects.equals(key, that.key) && Objects.equals(value, that.value);
        }

        @Override
        public int hashCode() {
            return Objects.hash(key, value);
        }

        @Override
        public int writeTo(final CompositeBuffer buf, final BufferAllocator allocator) {
            addRequestArgument(key, buf, allocator);
            addRequestArgument(value, buf, allocator);
            return SIZE;
        }

        @Override
        public void buildAttributes(final RedisPartitionAttributesBuilder builder) {
            builder.addKey(key);
        }
    }

    public static final class LongitudeLatitudeMember implements TupleArgument {

        static final int SIZE = 3;

        public final double longitude;

        public final double latitude;

        public final CharSequence member;

        /**
         * Instantiates a new {@link LongitudeLatitudeMember}.
         *
         * @param longitude the longitude
         * @param latitude the latitude
         * @param member the member
         */
        public LongitudeLatitudeMember(final double longitude, final double latitude, final CharSequence member) {
            this.longitude = longitude;
            this.latitude = latitude;
            this.member = requireNonNull(member);
        }

        @Override
        public boolean equals(final Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }
            final LongitudeLatitudeMember that = (LongitudeLatitudeMember) o;
            return Objects.equals(longitude, that.longitude) && Objects.equals(latitude, that.latitude) &&
                        Objects.equals(member, that.member);
        }

        @Override
        public int hashCode() {
            return Objects.hash(longitude, latitude, member);
        }

        @Override
        public int writeTo(final CompositeBuffer buf, final BufferAllocator allocator) {
            addRequestArgument(longitude, buf, allocator);
            addRequestArgument(latitude, buf, allocator);
            addRequestArgument(member, buf, allocator);
            return SIZE;
        }

        @Override
        public void buildAttributes(final RedisPartitionAttributesBuilder builder) {
        }
    }

    public static final class OffsetCount implements TupleArgument {

        static final int SIZE = 3;

        public final long offset;

        public final long count;

        /**
         * Instantiates a new {@link OffsetCount}.
         *
         * @param offset the offset
         * @param count the count
         */
        public OffsetCount(final long offset, final long count) {
            this.offset = offset;
            this.count = count;
        }

        @Override
        public boolean equals(final Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }
            final OffsetCount that = (OffsetCount) o;
            return Objects.equals(offset, that.offset) && Objects.equals(count, that.count);
        }

        @Override
        public int hashCode() {
            return Objects.hash(offset, count);
        }

        @Override
        public int writeTo(final CompositeBuffer buf, final BufferAllocator allocator) {
            addRequestArgument(SubCommand.LIMIT, buf, allocator);
            addRequestArgument(offset, buf, allocator);
            addRequestArgument(count, buf, allocator);
            return SIZE;
        }

        @Override
        public void buildAttributes(final RedisPartitionAttributesBuilder builder) {
        }
    }

    public static final class ScoreMember implements TupleArgument {

        static final int SIZE = 2;

        public final double score;

        public final CharSequence member;

        /**
         * Instantiates a new {@link ScoreMember}.
         *
         * @param score the score
         * @param member the member
         */
        public ScoreMember(final double score, final CharSequence member) {
            this.score = score;
            this.member = requireNonNull(member);
        }

        @Override
        public boolean equals(final Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }
            final ScoreMember that = (ScoreMember) o;
            return Objects.equals(score, that.score) && Objects.equals(member, that.member);
        }

        @Override
        public int hashCode() {
            return Objects.hash(score, member);
        }

        @Override
        public int writeTo(final CompositeBuffer buf, final BufferAllocator allocator) {
            addRequestArgument(score, buf, allocator);
            addRequestArgument(member, buf, allocator);
            return SIZE;
        }

        @Override
        public void buildAttributes(final RedisPartitionAttributesBuilder builder) {
        }
    }

    public interface BitfieldOperations {

        final class Get implements BitfieldOperation {

            static final int SIZE = 3;

            public final IntegerType type;

            @Nonnegative
            public final long offset;

            /**
             * Instantiates a new {@link Get}.
             *
             * @param type the type
             * @param offset the offset
             */
            public Get(final IntegerType type, @Nonnegative final long offset) {
                this.type = requireNonNull(type);
                if (offset < 0) {
                    throw new IllegalArgumentException("Non negative value expected for: offset, but got: " + offset);
                }
                this.offset = offset;
            }

            @Override
            public boolean equals(final Object o) {
                if (this == o) {
                    return true;
                }
                if (o == null || getClass() != o.getClass()) {
                    return false;
                }
                final Get that = (Get) o;
                return Objects.equals(type, that.type) && Objects.equals(offset, that.offset);
            }

            @Override
            public int hashCode() {
                return Objects.hash(type, offset);
            }

            @Override
            public int writeTo(final CompositeBuffer buf, final BufferAllocator allocator) {
                addRequestArgument(SubCommand.GET, buf, allocator);
                addRequestArgument(type, buf, allocator);
                addRequestArgument(offset, buf, allocator);
                return SIZE;
            }

            @Override
            public void buildAttributes(final RedisPartitionAttributesBuilder builder) {
            }
        }

        final class Incrby implements BitfieldOperation {

            static final int SIZE = 4;

            public final IntegerType type;

            @Nonnegative
            public final long offset;

            public final long increment;

            /**
             * Instantiates a new {@link Incrby}.
             *
             * @param type the type
             * @param offset the offset
             * @param increment the increment
             */
            public Incrby(final IntegerType type, @Nonnegative final long offset, final long increment) {
                this.type = requireNonNull(type);
                if (offset < 0) {
                    throw new IllegalArgumentException("Non negative value expected for: offset, but got: " + offset);
                }
                this.offset = offset;
                this.increment = increment;
            }

            @Override
            public boolean equals(final Object o) {
                if (this == o) {
                    return true;
                }
                if (o == null || getClass() != o.getClass()) {
                    return false;
                }
                final Incrby that = (Incrby) o;
                return Objects.equals(type, that.type) && Objects.equals(offset, that.offset) &&
                            Objects.equals(increment, that.increment);
            }

            @Override
            public int hashCode() {
                return Objects.hash(type, offset, increment);
            }

            @Override
            public int writeTo(final CompositeBuffer buf, final BufferAllocator allocator) {
                addRequestArgument(SubCommand.INCRBY, buf, allocator);
                addRequestArgument(type, buf, allocator);
                addRequestArgument(offset, buf, allocator);
                addRequestArgument(increment, buf, allocator);
                return SIZE;
            }

            @Override
            public void buildAttributes(final RedisPartitionAttributesBuilder builder) {
            }
        }

        final class Set implements BitfieldOperation {

            static final int SIZE = 4;

            public final IntegerType type;

            @Nonnegative
            public final long offset;

            public final long value;

            /**
             * Instantiates a new {@link Set}.
             *
             * @param type the type
             * @param offset the offset
             * @param value the value
             */
            public Set(final IntegerType type, @Nonnegative final long offset, final long value) {
                this.type = requireNonNull(type);
                if (offset < 0) {
                    throw new IllegalArgumentException("Non negative value expected for: offset, but got: " + offset);
                }
                this.offset = offset;
                this.value = value;
            }

            @Override
            public boolean equals(final Object o) {
                if (this == o) {
                    return true;
                }
                if (o == null || getClass() != o.getClass()) {
                    return false;
                }
                final Set that = (Set) o;
                return Objects.equals(type, that.type) && Objects.equals(offset, that.offset) &&
                            Objects.equals(value, that.value);
            }

            @Override
            public int hashCode() {
                return Objects.hash(type, offset, value);
            }

            @Override
            public int writeTo(final CompositeBuffer buf, final BufferAllocator allocator) {
                addRequestArgument(SubCommand.SET, buf, allocator);
                addRequestArgument(type, buf, allocator);
                addRequestArgument(offset, buf, allocator);
                addRequestArgument(value, buf, allocator);
                return SIZE;
            }

            @Override
            public void buildAttributes(final RedisPartitionAttributesBuilder builder) {
            }
        }

        final class Overflow implements BitfieldOperation {

            static final int SIZE = 2;

            public final BitfieldOverflow strategy;

            /**
             * Instantiates a new {@link Overflow}.
             *
             * @param strategy the strategy
             */
            public Overflow(final BitfieldOverflow strategy) {
                this.strategy = requireNonNull(strategy);
            }

            @Override
            public boolean equals(final Object o) {
                if (this == o) {
                    return true;
                }
                if (o == null || getClass() != o.getClass()) {
                    return false;
                }
                final Overflow that = (Overflow) o;
                return Objects.equals(strategy, that.strategy);
            }

            @Override
            public int hashCode() {
                return Objects.hash(strategy);
            }

            @Override
            public int writeTo(final CompositeBuffer buf, final BufferAllocator allocator) {
                addRequestArgument(SubCommand.OVERFLOW, buf, allocator);
                addRequestArgument(strategy, buf, allocator);
                return SIZE;
            }

            @Override
            public void buildAttributes(final RedisPartitionAttributesBuilder builder) {
            }
        }
    }
}

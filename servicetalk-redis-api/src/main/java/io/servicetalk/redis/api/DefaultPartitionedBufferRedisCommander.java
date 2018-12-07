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
import io.servicetalk.concurrent.api.Publisher;
import io.servicetalk.concurrent.api.Single;

import java.util.Collection;
import java.util.List;
import java.util.RandomAccess;
import java.util.function.Function;
import javax.annotation.Generated;
import javax.annotation.Nullable;

import static io.servicetalk.redis.api.RedisRequests.addBufferKeysToAttributeBuilder;
import static io.servicetalk.redis.api.RedisRequests.calculateInitialCommandBufferSize;
import static io.servicetalk.redis.api.RedisRequests.calculateRequestArgumentSize;
import static io.servicetalk.redis.api.RedisRequests.newRequest;
import static io.servicetalk.redis.api.RedisRequests.reserveConnection;
import static io.servicetalk.redis.api.RedisRequests.writeRequestArgument;
import static io.servicetalk.redis.api.RedisRequests.writeRequestArraySize;
import static java.util.Objects.requireNonNull;
import static java.util.function.Function.identity;

@Generated({})
@SuppressWarnings("unchecked")
final class DefaultPartitionedBufferRedisCommander extends BufferRedisCommander {

    private final PartitionedRedisClient partitionedRedisClient;

    private final Function<RedisProtocolSupport.Command, RedisPartitionAttributesBuilder> partitionAttributesBuilderFunction;

    DefaultPartitionedBufferRedisCommander(final PartitionedRedisClient partitionedRedisClient,
                final Function<RedisProtocolSupport.Command, RedisPartitionAttributesBuilder> partitionAttributesBuilderFunction) {
        this.partitionedRedisClient = requireNonNull(partitionedRedisClient);
        this.partitionAttributesBuilderFunction = requireNonNull(partitionAttributesBuilderFunction);
    }

    @Override
    public Completable closeAsync() {
        return partitionedRedisClient.closeAsync();
    }

    @Override
    public Completable closeAsyncGracefully() {
        return partitionedRedisClient.closeAsyncGracefully();
    }

    @Override
    public Single<Long> append(@RedisProtocolSupport.Key final Buffer key, final Buffer value) {
        requireNonNull(key);
        requireNonNull(value);
        final int len = 3;
        final int capacity = calculateInitialCommandBufferSize(len, RedisProtocolSupport.Command.APPEND) +
                    calculateRequestArgumentSize(key) + calculateRequestArgumentSize(value);
        Buffer buffer = partitionedRedisClient.executionContext().bufferAllocator().newBuffer(capacity);
        writeRequestArraySize(buffer, len);
        RedisProtocolSupport.Command.APPEND.encodeTo(buffer);
        writeRequestArgument(buffer, key);
        writeRequestArgument(buffer, value);
        final RedisRequest request = newRequest(RedisProtocolSupport.Command.APPEND, buffer);
        final RedisPartitionAttributesBuilder partitionAttributesBuilder = partitionAttributesBuilderFunction
                    .apply(RedisProtocolSupport.Command.APPEND);
        partitionAttributesBuilder.addKey(key);
        final Single<Long> result = partitionedRedisClient.request(partitionAttributesBuilder.build(), request,
                    Long.class);
        return result;
    }

    @Override
    public Single<String> auth(final Buffer password) {
        requireNonNull(password);
        final int len = 2;
        final int capacity = calculateInitialCommandBufferSize(len, RedisProtocolSupport.Command.AUTH) +
                    calculateRequestArgumentSize(password);
        Buffer buffer = partitionedRedisClient.executionContext().bufferAllocator().newBuffer(capacity);
        writeRequestArraySize(buffer, len);
        RedisProtocolSupport.Command.AUTH.encodeTo(buffer);
        writeRequestArgument(buffer, password);
        final RedisRequest request = newRequest(RedisProtocolSupport.Command.AUTH, buffer);
        final RedisPartitionAttributesBuilder partitionAttributesBuilder = partitionAttributesBuilderFunction
                    .apply(RedisProtocolSupport.Command.AUTH);
        final Single<String> result = partitionedRedisClient.request(partitionAttributesBuilder.build(), request,
                    String.class);
        return result;
    }

    @Override
    public Single<String> bgrewriteaof() {
        final int len = 1;
        final int capacity = calculateInitialCommandBufferSize(len, RedisProtocolSupport.Command.BGREWRITEAOF);
        Buffer buffer = partitionedRedisClient.executionContext().bufferAllocator().newBuffer(capacity);
        writeRequestArraySize(buffer, len);
        RedisProtocolSupport.Command.BGREWRITEAOF.encodeTo(buffer);
        final RedisRequest request = newRequest(RedisProtocolSupport.Command.BGREWRITEAOF, buffer);
        final RedisPartitionAttributesBuilder partitionAttributesBuilder = partitionAttributesBuilderFunction
                    .apply(RedisProtocolSupport.Command.BGREWRITEAOF);
        final Single<String> result = partitionedRedisClient.request(partitionAttributesBuilder.build(), request,
                    String.class);
        return result;
    }

    @Override
    public Single<String> bgsave() {
        final int len = 1;
        final int capacity = calculateInitialCommandBufferSize(len, RedisProtocolSupport.Command.BGSAVE);
        Buffer buffer = partitionedRedisClient.executionContext().bufferAllocator().newBuffer(capacity);
        writeRequestArraySize(buffer, len);
        RedisProtocolSupport.Command.BGSAVE.encodeTo(buffer);
        final RedisRequest request = newRequest(RedisProtocolSupport.Command.BGSAVE, buffer);
        final RedisPartitionAttributesBuilder partitionAttributesBuilder = partitionAttributesBuilderFunction
                    .apply(RedisProtocolSupport.Command.BGSAVE);
        final Single<String> result = partitionedRedisClient.request(partitionAttributesBuilder.build(), request,
                    String.class);
        return result;
    }

    @Override
    public Single<Long> bitcount(@RedisProtocolSupport.Key final Buffer key) {
        requireNonNull(key);
        final int len = 2;
        final int capacity = calculateInitialCommandBufferSize(len, RedisProtocolSupport.Command.BITCOUNT) +
                    calculateRequestArgumentSize(key);
        Buffer buffer = partitionedRedisClient.executionContext().bufferAllocator().newBuffer(capacity);
        writeRequestArraySize(buffer, len);
        RedisProtocolSupport.Command.BITCOUNT.encodeTo(buffer);
        writeRequestArgument(buffer, key);
        final RedisRequest request = newRequest(RedisProtocolSupport.Command.BITCOUNT, buffer);
        final RedisPartitionAttributesBuilder partitionAttributesBuilder = partitionAttributesBuilderFunction
                    .apply(RedisProtocolSupport.Command.BITCOUNT);
        partitionAttributesBuilder.addKey(key);
        final Single<Long> result = partitionedRedisClient.request(partitionAttributesBuilder.build(), request,
                    Long.class);
        return result;
    }

    @Override
    public Single<Long> bitcount(@RedisProtocolSupport.Key final Buffer key, @Nullable final Long start,
                                 @Nullable final Long end) {
        requireNonNull(key);
        final int len = 2 + (start == null ? 0 : 1) + (end == null ? 0 : 1);
        final int capacity = calculateInitialCommandBufferSize(len, RedisProtocolSupport.Command.BITCOUNT) +
                    calculateRequestArgumentSize(key) + (start == null ? 0 : calculateRequestArgumentSize(start)) +
                    (end == null ? 0 : calculateRequestArgumentSize(end));
        Buffer buffer = partitionedRedisClient.executionContext().bufferAllocator().newBuffer(capacity);
        writeRequestArraySize(buffer, len);
        RedisProtocolSupport.Command.BITCOUNT.encodeTo(buffer);
        writeRequestArgument(buffer, key);
        if (start != null) {
            writeRequestArgument(buffer, start);
        }
        if (end != null) {
            writeRequestArgument(buffer, end);
        }
        final RedisRequest request = newRequest(RedisProtocolSupport.Command.BITCOUNT, buffer);
        final RedisPartitionAttributesBuilder partitionAttributesBuilder = partitionAttributesBuilderFunction
                    .apply(RedisProtocolSupport.Command.BITCOUNT);
        partitionAttributesBuilder.addKey(key);
        final Single<Long> result = partitionedRedisClient.request(partitionAttributesBuilder.build(), request,
                    Long.class);
        return result;
    }

    @Override
    public Single<List<Long>> bitfield(@RedisProtocolSupport.Key final Buffer key,
                                       final Collection<RedisProtocolSupport.BitfieldOperation> operations) {
        requireNonNull(key);
        requireNonNull(operations);
        int collectionLen = 0;
        if (operations instanceof List && operations instanceof RandomAccess) {
            final List<RedisProtocolSupport.BitfieldOperation> list = (List<RedisProtocolSupport.BitfieldOperation>) operations;
            for (int i = 0; i < list.size(); ++i) {
                final RedisProtocolSupport.BitfieldOperation arg = list.get(i);
                collectionLen += arg.argumentCount();
            }
        } else {
            for (RedisProtocolSupport.BitfieldOperation arg : operations) {
                collectionLen += arg.argumentCount();
            }
        }
        final int len = 2 + collectionLen;
        int operationsCapacity = 0;
        if (operations instanceof List && operations instanceof RandomAccess) {
            final List<RedisProtocolSupport.BitfieldOperation> list = (List<RedisProtocolSupport.BitfieldOperation>) operations;
            for (int i = 0; i < list.size(); ++i) {
                final RedisProtocolSupport.BitfieldOperation arg = list.get(i);
                operationsCapacity += arg.encodedByteCount();
            }
        } else {
            for (RedisProtocolSupport.BitfieldOperation arg : operations) {
                operationsCapacity += arg.encodedByteCount();
            }
        }
        final int capacity = calculateInitialCommandBufferSize(len, RedisProtocolSupport.Command.BITFIELD) +
                    calculateRequestArgumentSize(key) + operationsCapacity;
        Buffer buffer = partitionedRedisClient.executionContext().bufferAllocator().newBuffer(capacity);
        writeRequestArraySize(buffer, len);
        RedisProtocolSupport.Command.BITFIELD.encodeTo(buffer);
        writeRequestArgument(buffer, key);
        if (operations instanceof List && operations instanceof RandomAccess) {
            final List<RedisProtocolSupport.BitfieldOperation> list = (List<RedisProtocolSupport.BitfieldOperation>) operations;
            for (int i = 0; i < list.size(); ++i) {
                final RedisProtocolSupport.BitfieldOperation arg = list.get(i);
                arg.encodeTo(buffer);
            }
        } else {
            for (RedisProtocolSupport.BitfieldOperation arg : operations) {
                arg.encodeTo(buffer);
            }
        }
        final RedisRequest request = newRequest(RedisProtocolSupport.Command.BITFIELD, buffer);
        final RedisPartitionAttributesBuilder partitionAttributesBuilder = partitionAttributesBuilderFunction
                    .apply(RedisProtocolSupport.Command.BITFIELD);
        partitionAttributesBuilder.addKey(key);
        final Single<List<Long>> result = (Single) partitionedRedisClient.request(partitionAttributesBuilder.build(),
                    request, List.class);
        return result;
    }

    @Override
    public Single<Long> bitop(final Buffer operation, @RedisProtocolSupport.Key final Buffer destkey,
                              @RedisProtocolSupport.Key final Buffer key) {
        requireNonNull(operation);
        requireNonNull(destkey);
        requireNonNull(key);
        final int len = 4;
        final int capacity = calculateInitialCommandBufferSize(len, RedisProtocolSupport.Command.BITOP) +
                    calculateRequestArgumentSize(operation) + calculateRequestArgumentSize(destkey) +
                    calculateRequestArgumentSize(key);
        Buffer buffer = partitionedRedisClient.executionContext().bufferAllocator().newBuffer(capacity);
        writeRequestArraySize(buffer, len);
        RedisProtocolSupport.Command.BITOP.encodeTo(buffer);
        writeRequestArgument(buffer, operation);
        writeRequestArgument(buffer, destkey);
        writeRequestArgument(buffer, key);
        final RedisRequest request = newRequest(RedisProtocolSupport.Command.BITOP, buffer);
        final RedisPartitionAttributesBuilder partitionAttributesBuilder = partitionAttributesBuilderFunction
                    .apply(RedisProtocolSupport.Command.BITOP);
        partitionAttributesBuilder.addKey(destkey);
        partitionAttributesBuilder.addKey(key);
        final Single<Long> result = partitionedRedisClient.request(partitionAttributesBuilder.build(), request,
                    Long.class);
        return result;
    }

    @Override
    public Single<Long> bitop(final Buffer operation, @RedisProtocolSupport.Key final Buffer destkey,
                              @RedisProtocolSupport.Key final Buffer key1,
                              @RedisProtocolSupport.Key final Buffer key2) {
        requireNonNull(operation);
        requireNonNull(destkey);
        requireNonNull(key1);
        requireNonNull(key2);
        final int len = 5;
        final int capacity = calculateInitialCommandBufferSize(len, RedisProtocolSupport.Command.BITOP) +
                    calculateRequestArgumentSize(operation) + calculateRequestArgumentSize(destkey) +
                    calculateRequestArgumentSize(key1) + calculateRequestArgumentSize(key2);
        Buffer buffer = partitionedRedisClient.executionContext().bufferAllocator().newBuffer(capacity);
        writeRequestArraySize(buffer, len);
        RedisProtocolSupport.Command.BITOP.encodeTo(buffer);
        writeRequestArgument(buffer, operation);
        writeRequestArgument(buffer, destkey);
        writeRequestArgument(buffer, key1);
        writeRequestArgument(buffer, key2);
        final RedisRequest request = newRequest(RedisProtocolSupport.Command.BITOP, buffer);
        final RedisPartitionAttributesBuilder partitionAttributesBuilder = partitionAttributesBuilderFunction
                    .apply(RedisProtocolSupport.Command.BITOP);
        partitionAttributesBuilder.addKey(destkey);
        partitionAttributesBuilder.addKey(key1);
        partitionAttributesBuilder.addKey(key2);
        final Single<Long> result = partitionedRedisClient.request(partitionAttributesBuilder.build(), request,
                    Long.class);
        return result;
    }

    @Override
    public Single<Long> bitop(final Buffer operation, @RedisProtocolSupport.Key final Buffer destkey,
                              @RedisProtocolSupport.Key final Buffer key1, @RedisProtocolSupport.Key final Buffer key2,
                              @RedisProtocolSupport.Key final Buffer key3) {
        requireNonNull(operation);
        requireNonNull(destkey);
        requireNonNull(key1);
        requireNonNull(key2);
        requireNonNull(key3);
        final int len = 6;
        final int capacity = calculateInitialCommandBufferSize(len, RedisProtocolSupport.Command.BITOP) +
                    calculateRequestArgumentSize(operation) + calculateRequestArgumentSize(destkey) +
                    calculateRequestArgumentSize(key1) + calculateRequestArgumentSize(key2) +
                    calculateRequestArgumentSize(key3);
        Buffer buffer = partitionedRedisClient.executionContext().bufferAllocator().newBuffer(capacity);
        writeRequestArraySize(buffer, len);
        RedisProtocolSupport.Command.BITOP.encodeTo(buffer);
        writeRequestArgument(buffer, operation);
        writeRequestArgument(buffer, destkey);
        writeRequestArgument(buffer, key1);
        writeRequestArgument(buffer, key2);
        writeRequestArgument(buffer, key3);
        final RedisRequest request = newRequest(RedisProtocolSupport.Command.BITOP, buffer);
        final RedisPartitionAttributesBuilder partitionAttributesBuilder = partitionAttributesBuilderFunction
                    .apply(RedisProtocolSupport.Command.BITOP);
        partitionAttributesBuilder.addKey(destkey);
        partitionAttributesBuilder.addKey(key1);
        partitionAttributesBuilder.addKey(key2);
        partitionAttributesBuilder.addKey(key3);
        final Single<Long> result = partitionedRedisClient.request(partitionAttributesBuilder.build(), request,
                    Long.class);
        return result;
    }

    @Override
    public Single<Long> bitop(final Buffer operation, @RedisProtocolSupport.Key final Buffer destkey,
                              @RedisProtocolSupport.Key final Collection<Buffer> keys) {
        requireNonNull(operation);
        requireNonNull(destkey);
        requireNonNull(keys);
        final int len = 3 + keys.size();
        int keysCapacity = 0;
        if (keys instanceof List && keys instanceof RandomAccess) {
            final List<Buffer> list = (List<Buffer>) keys;
            for (int i = 0; i < list.size(); ++i) {
                final Buffer arg = list.get(i);
                keysCapacity += calculateRequestArgumentSize(arg);
            }
        } else {
            for (Buffer arg : keys) {
                keysCapacity += calculateRequestArgumentSize(arg);
            }
        }
        final int capacity = calculateInitialCommandBufferSize(len, RedisProtocolSupport.Command.BITOP) +
                    calculateRequestArgumentSize(operation) + calculateRequestArgumentSize(destkey) + keysCapacity;
        Buffer buffer = partitionedRedisClient.executionContext().bufferAllocator().newBuffer(capacity);
        writeRequestArraySize(buffer, len);
        RedisProtocolSupport.Command.BITOP.encodeTo(buffer);
        writeRequestArgument(buffer, operation);
        writeRequestArgument(buffer, destkey);
        if (keys instanceof List && keys instanceof RandomAccess) {
            final List<Buffer> list = (List<Buffer>) keys;
            for (int i = 0; i < list.size(); ++i) {
                final Buffer arg = list.get(i);
                writeRequestArgument(buffer, arg);
            }
        } else {
            for (Buffer arg : keys) {
                writeRequestArgument(buffer, arg);
            }
        }
        final RedisRequest request = newRequest(RedisProtocolSupport.Command.BITOP, buffer);
        final RedisPartitionAttributesBuilder partitionAttributesBuilder = partitionAttributesBuilderFunction
                    .apply(RedisProtocolSupport.Command.BITOP);
        partitionAttributesBuilder.addKey(destkey);
        addBufferKeysToAttributeBuilder(keys, partitionAttributesBuilder);
        final Single<Long> result = partitionedRedisClient.request(partitionAttributesBuilder.build(), request,
                    Long.class);
        return result;
    }

    @Override
    public Single<Long> bitpos(@RedisProtocolSupport.Key final Buffer key, final long bit) {
        requireNonNull(key);
        final int len = 3;
        final int capacity = calculateInitialCommandBufferSize(len, RedisProtocolSupport.Command.BITPOS) +
                    calculateRequestArgumentSize(key) + calculateRequestArgumentSize(bit);
        Buffer buffer = partitionedRedisClient.executionContext().bufferAllocator().newBuffer(capacity);
        writeRequestArraySize(buffer, len);
        RedisProtocolSupport.Command.BITPOS.encodeTo(buffer);
        writeRequestArgument(buffer, key);
        writeRequestArgument(buffer, bit);
        final RedisRequest request = newRequest(RedisProtocolSupport.Command.BITPOS, buffer);
        final RedisPartitionAttributesBuilder partitionAttributesBuilder = partitionAttributesBuilderFunction
                    .apply(RedisProtocolSupport.Command.BITPOS);
        partitionAttributesBuilder.addKey(key);
        final Single<Long> result = partitionedRedisClient.request(partitionAttributesBuilder.build(), request,
                    Long.class);
        return result;
    }

    @Override
    public Single<Long> bitpos(@RedisProtocolSupport.Key final Buffer key, final long bit, @Nullable final Long start,
                               @Nullable final Long end) {
        requireNonNull(key);
        final int len = 3 + (start == null ? 0 : 1) + (end == null ? 0 : 1);
        final int capacity = calculateInitialCommandBufferSize(len, RedisProtocolSupport.Command.BITPOS) +
                    calculateRequestArgumentSize(key) + calculateRequestArgumentSize(bit) +
                    (start == null ? 0 : calculateRequestArgumentSize(start)) +
                    (end == null ? 0 : calculateRequestArgumentSize(end));
        Buffer buffer = partitionedRedisClient.executionContext().bufferAllocator().newBuffer(capacity);
        writeRequestArraySize(buffer, len);
        RedisProtocolSupport.Command.BITPOS.encodeTo(buffer);
        writeRequestArgument(buffer, key);
        writeRequestArgument(buffer, bit);
        if (start != null) {
            writeRequestArgument(buffer, start);
        }
        if (end != null) {
            writeRequestArgument(buffer, end);
        }
        final RedisRequest request = newRequest(RedisProtocolSupport.Command.BITPOS, buffer);
        final RedisPartitionAttributesBuilder partitionAttributesBuilder = partitionAttributesBuilderFunction
                    .apply(RedisProtocolSupport.Command.BITPOS);
        partitionAttributesBuilder.addKey(key);
        final Single<Long> result = partitionedRedisClient.request(partitionAttributesBuilder.build(), request,
                    Long.class);
        return result;
    }

    @Override
    public <T> Single<List<T>> blpop(@RedisProtocolSupport.Key final Collection<Buffer> keys, final long timeout) {
        requireNonNull(keys);
        final int len = 2 + keys.size();
        int keysCapacity = 0;
        if (keys instanceof List && keys instanceof RandomAccess) {
            final List<Buffer> list = (List<Buffer>) keys;
            for (int i = 0; i < list.size(); ++i) {
                final Buffer arg = list.get(i);
                keysCapacity += calculateRequestArgumentSize(arg);
            }
        } else {
            for (Buffer arg : keys) {
                keysCapacity += calculateRequestArgumentSize(arg);
            }
        }
        final int capacity = calculateInitialCommandBufferSize(len, RedisProtocolSupport.Command.BLPOP) + keysCapacity +
                    calculateRequestArgumentSize(timeout);
        Buffer buffer = partitionedRedisClient.executionContext().bufferAllocator().newBuffer(capacity);
        writeRequestArraySize(buffer, len);
        RedisProtocolSupport.Command.BLPOP.encodeTo(buffer);
        if (keys instanceof List && keys instanceof RandomAccess) {
            final List<Buffer> list = (List<Buffer>) keys;
            for (int i = 0; i < list.size(); ++i) {
                final Buffer arg = list.get(i);
                writeRequestArgument(buffer, arg);
            }
        } else {
            for (Buffer arg : keys) {
                writeRequestArgument(buffer, arg);
            }
        }
        writeRequestArgument(buffer, timeout);
        final RedisRequest request = newRequest(RedisProtocolSupport.Command.BLPOP, buffer);
        final RedisPartitionAttributesBuilder partitionAttributesBuilder = partitionAttributesBuilderFunction
                    .apply(RedisProtocolSupport.Command.BLPOP);
        addBufferKeysToAttributeBuilder(keys, partitionAttributesBuilder);
        final Single<List<T>> result = (Single) partitionedRedisClient.request(partitionAttributesBuilder.build(),
                    request, List.class);
        return result;
    }

    @Override
    public <T> Single<List<T>> brpop(@RedisProtocolSupport.Key final Collection<Buffer> keys, final long timeout) {
        requireNonNull(keys);
        final int len = 2 + keys.size();
        int keysCapacity = 0;
        if (keys instanceof List && keys instanceof RandomAccess) {
            final List<Buffer> list = (List<Buffer>) keys;
            for (int i = 0; i < list.size(); ++i) {
                final Buffer arg = list.get(i);
                keysCapacity += calculateRequestArgumentSize(arg);
            }
        } else {
            for (Buffer arg : keys) {
                keysCapacity += calculateRequestArgumentSize(arg);
            }
        }
        final int capacity = calculateInitialCommandBufferSize(len, RedisProtocolSupport.Command.BRPOP) + keysCapacity +
                    calculateRequestArgumentSize(timeout);
        Buffer buffer = partitionedRedisClient.executionContext().bufferAllocator().newBuffer(capacity);
        writeRequestArraySize(buffer, len);
        RedisProtocolSupport.Command.BRPOP.encodeTo(buffer);
        if (keys instanceof List && keys instanceof RandomAccess) {
            final List<Buffer> list = (List<Buffer>) keys;
            for (int i = 0; i < list.size(); ++i) {
                final Buffer arg = list.get(i);
                writeRequestArgument(buffer, arg);
            }
        } else {
            for (Buffer arg : keys) {
                writeRequestArgument(buffer, arg);
            }
        }
        writeRequestArgument(buffer, timeout);
        final RedisRequest request = newRequest(RedisProtocolSupport.Command.BRPOP, buffer);
        final RedisPartitionAttributesBuilder partitionAttributesBuilder = partitionAttributesBuilderFunction
                    .apply(RedisProtocolSupport.Command.BRPOP);
        addBufferKeysToAttributeBuilder(keys, partitionAttributesBuilder);
        final Single<List<T>> result = (Single) partitionedRedisClient.request(partitionAttributesBuilder.build(),
                    request, List.class);
        return result;
    }

    @Override
    public Single<Buffer> brpoplpush(@RedisProtocolSupport.Key final Buffer source,
                                     @RedisProtocolSupport.Key final Buffer destination, final long timeout) {
        requireNonNull(source);
        requireNonNull(destination);
        final int len = 4;
        final int capacity = calculateInitialCommandBufferSize(len, RedisProtocolSupport.Command.BRPOPLPUSH) +
                    calculateRequestArgumentSize(source) + calculateRequestArgumentSize(destination) +
                    calculateRequestArgumentSize(timeout);
        Buffer buffer = partitionedRedisClient.executionContext().bufferAllocator().newBuffer(capacity);
        writeRequestArraySize(buffer, len);
        RedisProtocolSupport.Command.BRPOPLPUSH.encodeTo(buffer);
        writeRequestArgument(buffer, source);
        writeRequestArgument(buffer, destination);
        writeRequestArgument(buffer, timeout);
        final RedisRequest request = newRequest(RedisProtocolSupport.Command.BRPOPLPUSH, buffer);
        final RedisPartitionAttributesBuilder partitionAttributesBuilder = partitionAttributesBuilderFunction
                    .apply(RedisProtocolSupport.Command.BRPOPLPUSH);
        partitionAttributesBuilder.addKey(source);
        partitionAttributesBuilder.addKey(destination);
        final Single<Buffer> result = partitionedRedisClient.request(partitionAttributesBuilder.build(), request,
                    Buffer.class);
        return result;
    }

    @Override
    public <T> Single<List<T>> bzpopmax(@RedisProtocolSupport.Key final Collection<Buffer> keys, final long timeout) {
        requireNonNull(keys);
        final int len = 2 + keys.size();
        int keysCapacity = 0;
        if (keys instanceof List && keys instanceof RandomAccess) {
            final List<Buffer> list = (List<Buffer>) keys;
            for (int i = 0; i < list.size(); ++i) {
                final Buffer arg = list.get(i);
                keysCapacity += calculateRequestArgumentSize(arg);
            }
        } else {
            for (Buffer arg : keys) {
                keysCapacity += calculateRequestArgumentSize(arg);
            }
        }
        final int capacity = calculateInitialCommandBufferSize(len, RedisProtocolSupport.Command.BZPOPMAX) +
                    keysCapacity + calculateRequestArgumentSize(timeout);
        Buffer buffer = partitionedRedisClient.executionContext().bufferAllocator().newBuffer(capacity);
        writeRequestArraySize(buffer, len);
        RedisProtocolSupport.Command.BZPOPMAX.encodeTo(buffer);
        if (keys instanceof List && keys instanceof RandomAccess) {
            final List<Buffer> list = (List<Buffer>) keys;
            for (int i = 0; i < list.size(); ++i) {
                final Buffer arg = list.get(i);
                writeRequestArgument(buffer, arg);
            }
        } else {
            for (Buffer arg : keys) {
                writeRequestArgument(buffer, arg);
            }
        }
        writeRequestArgument(buffer, timeout);
        final RedisRequest request = newRequest(RedisProtocolSupport.Command.BZPOPMAX, buffer);
        final RedisPartitionAttributesBuilder partitionAttributesBuilder = partitionAttributesBuilderFunction
                    .apply(RedisProtocolSupport.Command.BZPOPMAX);
        addBufferKeysToAttributeBuilder(keys, partitionAttributesBuilder);
        final Single<List<T>> result = (Single) partitionedRedisClient.request(partitionAttributesBuilder.build(),
                    request, List.class);
        return result;
    }

    @Override
    public <T> Single<List<T>> bzpopmin(@RedisProtocolSupport.Key final Collection<Buffer> keys, final long timeout) {
        requireNonNull(keys);
        final int len = 2 + keys.size();
        int keysCapacity = 0;
        if (keys instanceof List && keys instanceof RandomAccess) {
            final List<Buffer> list = (List<Buffer>) keys;
            for (int i = 0; i < list.size(); ++i) {
                final Buffer arg = list.get(i);
                keysCapacity += calculateRequestArgumentSize(arg);
            }
        } else {
            for (Buffer arg : keys) {
                keysCapacity += calculateRequestArgumentSize(arg);
            }
        }
        final int capacity = calculateInitialCommandBufferSize(len, RedisProtocolSupport.Command.BZPOPMIN) +
                    keysCapacity + calculateRequestArgumentSize(timeout);
        Buffer buffer = partitionedRedisClient.executionContext().bufferAllocator().newBuffer(capacity);
        writeRequestArraySize(buffer, len);
        RedisProtocolSupport.Command.BZPOPMIN.encodeTo(buffer);
        if (keys instanceof List && keys instanceof RandomAccess) {
            final List<Buffer> list = (List<Buffer>) keys;
            for (int i = 0; i < list.size(); ++i) {
                final Buffer arg = list.get(i);
                writeRequestArgument(buffer, arg);
            }
        } else {
            for (Buffer arg : keys) {
                writeRequestArgument(buffer, arg);
            }
        }
        writeRequestArgument(buffer, timeout);
        final RedisRequest request = newRequest(RedisProtocolSupport.Command.BZPOPMIN, buffer);
        final RedisPartitionAttributesBuilder partitionAttributesBuilder = partitionAttributesBuilderFunction
                    .apply(RedisProtocolSupport.Command.BZPOPMIN);
        addBufferKeysToAttributeBuilder(keys, partitionAttributesBuilder);
        final Single<List<T>> result = (Single) partitionedRedisClient.request(partitionAttributesBuilder.build(),
                    request, List.class);
        return result;
    }

    @Override
    public Single<Long> clientKill(@Nullable final Long id, @Nullable final RedisProtocolSupport.ClientKillType type,
                                   @Nullable final Buffer addrIpPort, @Nullable final Buffer skipmeYesNo) {
        final int len = 2 + (id == null ? 0 : 2) + (type == null ? 0 : 1) + (addrIpPort == null ? 0 : 2) +
                    (skipmeYesNo == null ? 0 : 2);
        final int capacity = 1 + calculateInitialCommandBufferSize(len, RedisProtocolSupport.Command.CLIENT) +
                    (id == null ? 0 : RedisProtocolSupport.SubCommand.ID.encodedByteCount()) +
                    (id == null ? 0 : calculateRequestArgumentSize(id)) + (type == null ? 0 : type.encodedByteCount()) +
                    (addrIpPort == null ? 0 : RedisProtocolSupport.SubCommand.ADDR.encodedByteCount()) +
                    (addrIpPort == null ? 0 : calculateRequestArgumentSize(addrIpPort)) +
                    (skipmeYesNo == null ? 0 : RedisProtocolSupport.SubCommand.SKIPME.encodedByteCount()) +
                    (skipmeYesNo == null ? 0 : calculateRequestArgumentSize(skipmeYesNo));
        Buffer buffer = partitionedRedisClient.executionContext().bufferAllocator().newBuffer(capacity);
        writeRequestArraySize(buffer, len);
        RedisProtocolSupport.Command.CLIENT.encodeTo(buffer);
        RedisProtocolSupport.SubCommand.KILL.encodeTo(buffer);
        if (id != null) {
            RedisProtocolSupport.SubCommand.ID.encodeTo(buffer);
            writeRequestArgument(buffer, id);
        }
        if (type != null) {
            type.encodeTo(buffer);
        }
        if (addrIpPort != null) {
            RedisProtocolSupport.SubCommand.ADDR.encodeTo(buffer);
            writeRequestArgument(buffer, addrIpPort);
        }
        if (skipmeYesNo != null) {
            RedisProtocolSupport.SubCommand.SKIPME.encodeTo(buffer);
            writeRequestArgument(buffer, skipmeYesNo);
        }
        final RedisRequest request = newRequest(RedisProtocolSupport.Command.CLIENT, buffer);
        final RedisPartitionAttributesBuilder partitionAttributesBuilder = partitionAttributesBuilderFunction
                    .apply(RedisProtocolSupport.Command.CLIENT);
        final Single<Long> result = partitionedRedisClient.request(partitionAttributesBuilder.build(), request,
                    Long.class);
        return result;
    }

    @Override
    public Single<Buffer> clientList() {
        final int len = 2;
        final int capacity = 1 + calculateInitialCommandBufferSize(len, RedisProtocolSupport.Command.CLIENT);
        Buffer buffer = partitionedRedisClient.executionContext().bufferAllocator().newBuffer(capacity);
        writeRequestArraySize(buffer, len);
        RedisProtocolSupport.Command.CLIENT.encodeTo(buffer);
        RedisProtocolSupport.SubCommand.LIST.encodeTo(buffer);
        final RedisRequest request = newRequest(RedisProtocolSupport.Command.CLIENT, buffer);
        final RedisPartitionAttributesBuilder partitionAttributesBuilder = partitionAttributesBuilderFunction
                    .apply(RedisProtocolSupport.Command.CLIENT);
        final Single<Buffer> result = partitionedRedisClient.request(partitionAttributesBuilder.build(), request,
                    Buffer.class);
        return result;
    }

    @Override
    public Single<Buffer> clientGetname() {
        final int len = 2;
        final int capacity = 1 + calculateInitialCommandBufferSize(len, RedisProtocolSupport.Command.CLIENT);
        Buffer buffer = partitionedRedisClient.executionContext().bufferAllocator().newBuffer(capacity);
        writeRequestArraySize(buffer, len);
        RedisProtocolSupport.Command.CLIENT.encodeTo(buffer);
        RedisProtocolSupport.SubCommand.GETNAME.encodeTo(buffer);
        final RedisRequest request = newRequest(RedisProtocolSupport.Command.CLIENT, buffer);
        final RedisPartitionAttributesBuilder partitionAttributesBuilder = partitionAttributesBuilderFunction
                    .apply(RedisProtocolSupport.Command.CLIENT);
        final Single<Buffer> result = partitionedRedisClient.request(partitionAttributesBuilder.build(), request,
                    Buffer.class);
        return result;
    }

    @Override
    public Single<String> clientPause(final long timeout) {
        final int len = 3;
        final int capacity = 1 + calculateInitialCommandBufferSize(len, RedisProtocolSupport.Command.CLIENT) +
                    calculateRequestArgumentSize(timeout);
        Buffer buffer = partitionedRedisClient.executionContext().bufferAllocator().newBuffer(capacity);
        writeRequestArraySize(buffer, len);
        RedisProtocolSupport.Command.CLIENT.encodeTo(buffer);
        RedisProtocolSupport.SubCommand.PAUSE.encodeTo(buffer);
        writeRequestArgument(buffer, timeout);
        final RedisRequest request = newRequest(RedisProtocolSupport.Command.CLIENT, buffer);
        final RedisPartitionAttributesBuilder partitionAttributesBuilder = partitionAttributesBuilderFunction
                    .apply(RedisProtocolSupport.Command.CLIENT);
        final Single<String> result = partitionedRedisClient.request(partitionAttributesBuilder.build(), request,
                    String.class);
        return result;
    }

    @Override
    public Single<String> clientReply(final RedisProtocolSupport.ClientReplyReplyMode replyMode) {
        requireNonNull(replyMode);
        final int len = 3;
        final int capacity = 1 + calculateInitialCommandBufferSize(len, RedisProtocolSupport.Command.CLIENT) +
                    replyMode.encodedByteCount();
        Buffer buffer = partitionedRedisClient.executionContext().bufferAllocator().newBuffer(capacity);
        writeRequestArraySize(buffer, len);
        RedisProtocolSupport.Command.CLIENT.encodeTo(buffer);
        RedisProtocolSupport.SubCommand.REPLY.encodeTo(buffer);
        replyMode.encodeTo(buffer);
        final RedisRequest request = newRequest(RedisProtocolSupport.Command.CLIENT, buffer);
        final RedisPartitionAttributesBuilder partitionAttributesBuilder = partitionAttributesBuilderFunction
                    .apply(RedisProtocolSupport.Command.CLIENT);
        final Single<String> result = partitionedRedisClient.request(partitionAttributesBuilder.build(), request,
                    String.class);
        return result;
    }

    @Override
    public Single<String> clientSetname(final Buffer connectionName) {
        requireNonNull(connectionName);
        final int len = 3;
        final int capacity = 1 + calculateInitialCommandBufferSize(len, RedisProtocolSupport.Command.CLIENT) +
                    calculateRequestArgumentSize(connectionName);
        Buffer buffer = partitionedRedisClient.executionContext().bufferAllocator().newBuffer(capacity);
        writeRequestArraySize(buffer, len);
        RedisProtocolSupport.Command.CLIENT.encodeTo(buffer);
        RedisProtocolSupport.SubCommand.SETNAME.encodeTo(buffer);
        writeRequestArgument(buffer, connectionName);
        final RedisRequest request = newRequest(RedisProtocolSupport.Command.CLIENT, buffer);
        final RedisPartitionAttributesBuilder partitionAttributesBuilder = partitionAttributesBuilderFunction
                    .apply(RedisProtocolSupport.Command.CLIENT);
        final Single<String> result = partitionedRedisClient.request(partitionAttributesBuilder.build(), request,
                    String.class);
        return result;
    }

    @Override
    public Single<String> clusterAddslots(final long slot) {
        final int len = 3;
        final int capacity = 1 + calculateInitialCommandBufferSize(len, RedisProtocolSupport.Command.CLUSTER) +
                    calculateRequestArgumentSize(slot);
        Buffer buffer = partitionedRedisClient.executionContext().bufferAllocator().newBuffer(capacity);
        writeRequestArraySize(buffer, len);
        RedisProtocolSupport.Command.CLUSTER.encodeTo(buffer);
        RedisProtocolSupport.SubCommand.ADDSLOTS.encodeTo(buffer);
        writeRequestArgument(buffer, slot);
        final RedisRequest request = newRequest(RedisProtocolSupport.Command.CLUSTER, buffer);
        final RedisPartitionAttributesBuilder partitionAttributesBuilder = partitionAttributesBuilderFunction
                    .apply(RedisProtocolSupport.Command.CLUSTER);
        final Single<String> result = partitionedRedisClient.request(partitionAttributesBuilder.build(), request,
                    String.class);
        return result;
    }

    @Override
    public Single<String> clusterAddslots(final long slot1, final long slot2) {
        final int len = 4;
        final int capacity = 1 + calculateInitialCommandBufferSize(len, RedisProtocolSupport.Command.CLUSTER) +
                    calculateRequestArgumentSize(slot1) + calculateRequestArgumentSize(slot2);
        Buffer buffer = partitionedRedisClient.executionContext().bufferAllocator().newBuffer(capacity);
        writeRequestArraySize(buffer, len);
        RedisProtocolSupport.Command.CLUSTER.encodeTo(buffer);
        RedisProtocolSupport.SubCommand.ADDSLOTS.encodeTo(buffer);
        writeRequestArgument(buffer, slot1);
        writeRequestArgument(buffer, slot2);
        final RedisRequest request = newRequest(RedisProtocolSupport.Command.CLUSTER, buffer);
        final RedisPartitionAttributesBuilder partitionAttributesBuilder = partitionAttributesBuilderFunction
                    .apply(RedisProtocolSupport.Command.CLUSTER);
        final Single<String> result = partitionedRedisClient.request(partitionAttributesBuilder.build(), request,
                    String.class);
        return result;
    }

    @Override
    public Single<String> clusterAddslots(final long slot1, final long slot2, final long slot3) {
        final int len = 5;
        final int capacity = 1 + calculateInitialCommandBufferSize(len, RedisProtocolSupport.Command.CLUSTER) +
                    calculateRequestArgumentSize(slot1) + calculateRequestArgumentSize(slot2) +
                    calculateRequestArgumentSize(slot3);
        Buffer buffer = partitionedRedisClient.executionContext().bufferAllocator().newBuffer(capacity);
        writeRequestArraySize(buffer, len);
        RedisProtocolSupport.Command.CLUSTER.encodeTo(buffer);
        RedisProtocolSupport.SubCommand.ADDSLOTS.encodeTo(buffer);
        writeRequestArgument(buffer, slot1);
        writeRequestArgument(buffer, slot2);
        writeRequestArgument(buffer, slot3);
        final RedisRequest request = newRequest(RedisProtocolSupport.Command.CLUSTER, buffer);
        final RedisPartitionAttributesBuilder partitionAttributesBuilder = partitionAttributesBuilderFunction
                    .apply(RedisProtocolSupport.Command.CLUSTER);
        final Single<String> result = partitionedRedisClient.request(partitionAttributesBuilder.build(), request,
                    String.class);
        return result;
    }

    @Override
    public Single<String> clusterAddslots(final Collection<Long> slots) {
        requireNonNull(slots);
        final int len = 2 + slots.size();
        int slotsCapacity = 0;
        if (slots instanceof List && slots instanceof RandomAccess) {
            final List<Long> list = (List<Long>) slots;
            for (int i = 0; i < list.size(); ++i) {
                final Long arg = list.get(i);
                slotsCapacity += calculateRequestArgumentSize(arg);
            }
        } else {
            for (Long arg : slots) {
                slotsCapacity += calculateRequestArgumentSize(arg);
            }
        }
        final int capacity = 1 + calculateInitialCommandBufferSize(len, RedisProtocolSupport.Command.CLUSTER) +
                    slotsCapacity;
        Buffer buffer = partitionedRedisClient.executionContext().bufferAllocator().newBuffer(capacity);
        writeRequestArraySize(buffer, len);
        RedisProtocolSupport.Command.CLUSTER.encodeTo(buffer);
        RedisProtocolSupport.SubCommand.ADDSLOTS.encodeTo(buffer);
        if (slots instanceof List && slots instanceof RandomAccess) {
            final List<Long> list = (List<Long>) slots;
            for (int i = 0; i < list.size(); ++i) {
                final Long arg = list.get(i);
                writeRequestArgument(buffer, arg);
            }
        } else {
            for (Long arg : slots) {
                writeRequestArgument(buffer, arg);
            }
        }
        final RedisRequest request = newRequest(RedisProtocolSupport.Command.CLUSTER, buffer);
        final RedisPartitionAttributesBuilder partitionAttributesBuilder = partitionAttributesBuilderFunction
                    .apply(RedisProtocolSupport.Command.CLUSTER);
        final Single<String> result = partitionedRedisClient.request(partitionAttributesBuilder.build(), request,
                    String.class);
        return result;
    }

    @Override
    public Single<Long> clusterCountFailureReports(final Buffer nodeId) {
        requireNonNull(nodeId);
        final int len = 3;
        final int capacity = 1 + calculateInitialCommandBufferSize(len, RedisProtocolSupport.Command.CLUSTER) +
                    calculateRequestArgumentSize(nodeId);
        Buffer buffer = partitionedRedisClient.executionContext().bufferAllocator().newBuffer(capacity);
        writeRequestArraySize(buffer, len);
        RedisProtocolSupport.Command.CLUSTER.encodeTo(buffer);
        RedisProtocolSupport.SubCommand.COUNT_FAILURE_REPORTS.encodeTo(buffer);
        writeRequestArgument(buffer, nodeId);
        final RedisRequest request = newRequest(RedisProtocolSupport.Command.CLUSTER, buffer);
        final RedisPartitionAttributesBuilder partitionAttributesBuilder = partitionAttributesBuilderFunction
                    .apply(RedisProtocolSupport.Command.CLUSTER);
        final Single<Long> result = partitionedRedisClient.request(partitionAttributesBuilder.build(), request,
                    Long.class);
        return result;
    }

    @Override
    public Single<Long> clusterCountkeysinslot(final long slot) {
        final int len = 3;
        final int capacity = 1 + calculateInitialCommandBufferSize(len, RedisProtocolSupport.Command.CLUSTER) +
                    calculateRequestArgumentSize(slot);
        Buffer buffer = partitionedRedisClient.executionContext().bufferAllocator().newBuffer(capacity);
        writeRequestArraySize(buffer, len);
        RedisProtocolSupport.Command.CLUSTER.encodeTo(buffer);
        RedisProtocolSupport.SubCommand.COUNTKEYSINSLOT.encodeTo(buffer);
        writeRequestArgument(buffer, slot);
        final RedisRequest request = newRequest(RedisProtocolSupport.Command.CLUSTER, buffer);
        final RedisPartitionAttributesBuilder partitionAttributesBuilder = partitionAttributesBuilderFunction
                    .apply(RedisProtocolSupport.Command.CLUSTER);
        final Single<Long> result = partitionedRedisClient.request(partitionAttributesBuilder.build(), request,
                    Long.class);
        return result;
    }

    @Override
    public Single<String> clusterDelslots(final long slot) {
        final int len = 3;
        final int capacity = 1 + calculateInitialCommandBufferSize(len, RedisProtocolSupport.Command.CLUSTER) +
                    calculateRequestArgumentSize(slot);
        Buffer buffer = partitionedRedisClient.executionContext().bufferAllocator().newBuffer(capacity);
        writeRequestArraySize(buffer, len);
        RedisProtocolSupport.Command.CLUSTER.encodeTo(buffer);
        RedisProtocolSupport.SubCommand.DELSLOTS.encodeTo(buffer);
        writeRequestArgument(buffer, slot);
        final RedisRequest request = newRequest(RedisProtocolSupport.Command.CLUSTER, buffer);
        final RedisPartitionAttributesBuilder partitionAttributesBuilder = partitionAttributesBuilderFunction
                    .apply(RedisProtocolSupport.Command.CLUSTER);
        final Single<String> result = partitionedRedisClient.request(partitionAttributesBuilder.build(), request,
                    String.class);
        return result;
    }

    @Override
    public Single<String> clusterDelslots(final long slot1, final long slot2) {
        final int len = 4;
        final int capacity = 1 + calculateInitialCommandBufferSize(len, RedisProtocolSupport.Command.CLUSTER) +
                    calculateRequestArgumentSize(slot1) + calculateRequestArgumentSize(slot2);
        Buffer buffer = partitionedRedisClient.executionContext().bufferAllocator().newBuffer(capacity);
        writeRequestArraySize(buffer, len);
        RedisProtocolSupport.Command.CLUSTER.encodeTo(buffer);
        RedisProtocolSupport.SubCommand.DELSLOTS.encodeTo(buffer);
        writeRequestArgument(buffer, slot1);
        writeRequestArgument(buffer, slot2);
        final RedisRequest request = newRequest(RedisProtocolSupport.Command.CLUSTER, buffer);
        final RedisPartitionAttributesBuilder partitionAttributesBuilder = partitionAttributesBuilderFunction
                    .apply(RedisProtocolSupport.Command.CLUSTER);
        final Single<String> result = partitionedRedisClient.request(partitionAttributesBuilder.build(), request,
                    String.class);
        return result;
    }

    @Override
    public Single<String> clusterDelslots(final long slot1, final long slot2, final long slot3) {
        final int len = 5;
        final int capacity = 1 + calculateInitialCommandBufferSize(len, RedisProtocolSupport.Command.CLUSTER) +
                    calculateRequestArgumentSize(slot1) + calculateRequestArgumentSize(slot2) +
                    calculateRequestArgumentSize(slot3);
        Buffer buffer = partitionedRedisClient.executionContext().bufferAllocator().newBuffer(capacity);
        writeRequestArraySize(buffer, len);
        RedisProtocolSupport.Command.CLUSTER.encodeTo(buffer);
        RedisProtocolSupport.SubCommand.DELSLOTS.encodeTo(buffer);
        writeRequestArgument(buffer, slot1);
        writeRequestArgument(buffer, slot2);
        writeRequestArgument(buffer, slot3);
        final RedisRequest request = newRequest(RedisProtocolSupport.Command.CLUSTER, buffer);
        final RedisPartitionAttributesBuilder partitionAttributesBuilder = partitionAttributesBuilderFunction
                    .apply(RedisProtocolSupport.Command.CLUSTER);
        final Single<String> result = partitionedRedisClient.request(partitionAttributesBuilder.build(), request,
                    String.class);
        return result;
    }

    @Override
    public Single<String> clusterDelslots(final Collection<Long> slots) {
        requireNonNull(slots);
        final int len = 2 + slots.size();
        int slotsCapacity = 0;
        if (slots instanceof List && slots instanceof RandomAccess) {
            final List<Long> list = (List<Long>) slots;
            for (int i = 0; i < list.size(); ++i) {
                final Long arg = list.get(i);
                slotsCapacity += calculateRequestArgumentSize(arg);
            }
        } else {
            for (Long arg : slots) {
                slotsCapacity += calculateRequestArgumentSize(arg);
            }
        }
        final int capacity = 1 + calculateInitialCommandBufferSize(len, RedisProtocolSupport.Command.CLUSTER) +
                    slotsCapacity;
        Buffer buffer = partitionedRedisClient.executionContext().bufferAllocator().newBuffer(capacity);
        writeRequestArraySize(buffer, len);
        RedisProtocolSupport.Command.CLUSTER.encodeTo(buffer);
        RedisProtocolSupport.SubCommand.DELSLOTS.encodeTo(buffer);
        if (slots instanceof List && slots instanceof RandomAccess) {
            final List<Long> list = (List<Long>) slots;
            for (int i = 0; i < list.size(); ++i) {
                final Long arg = list.get(i);
                writeRequestArgument(buffer, arg);
            }
        } else {
            for (Long arg : slots) {
                writeRequestArgument(buffer, arg);
            }
        }
        final RedisRequest request = newRequest(RedisProtocolSupport.Command.CLUSTER, buffer);
        final RedisPartitionAttributesBuilder partitionAttributesBuilder = partitionAttributesBuilderFunction
                    .apply(RedisProtocolSupport.Command.CLUSTER);
        final Single<String> result = partitionedRedisClient.request(partitionAttributesBuilder.build(), request,
                    String.class);
        return result;
    }

    @Override
    public Single<String> clusterFailover() {
        final int len = 2;
        final int capacity = 1 + calculateInitialCommandBufferSize(len, RedisProtocolSupport.Command.CLUSTER);
        Buffer buffer = partitionedRedisClient.executionContext().bufferAllocator().newBuffer(capacity);
        writeRequestArraySize(buffer, len);
        RedisProtocolSupport.Command.CLUSTER.encodeTo(buffer);
        RedisProtocolSupport.SubCommand.FAILOVER.encodeTo(buffer);
        final RedisRequest request = newRequest(RedisProtocolSupport.Command.CLUSTER, buffer);
        final RedisPartitionAttributesBuilder partitionAttributesBuilder = partitionAttributesBuilderFunction
                    .apply(RedisProtocolSupport.Command.CLUSTER);
        final Single<String> result = partitionedRedisClient.request(partitionAttributesBuilder.build(), request,
                    String.class);
        return result;
    }

    @Override
    public Single<String> clusterFailover(@Nullable final RedisProtocolSupport.ClusterFailoverOptions options) {
        final int len = 2 + (options == null ? 0 : 1);
        final int capacity = 1 + calculateInitialCommandBufferSize(len, RedisProtocolSupport.Command.CLUSTER) +
                    (options == null ? 0 : options.encodedByteCount());
        Buffer buffer = partitionedRedisClient.executionContext().bufferAllocator().newBuffer(capacity);
        writeRequestArraySize(buffer, len);
        RedisProtocolSupport.Command.CLUSTER.encodeTo(buffer);
        RedisProtocolSupport.SubCommand.FAILOVER.encodeTo(buffer);
        if (options != null) {
            options.encodeTo(buffer);
        }
        final RedisRequest request = newRequest(RedisProtocolSupport.Command.CLUSTER, buffer);
        final RedisPartitionAttributesBuilder partitionAttributesBuilder = partitionAttributesBuilderFunction
                    .apply(RedisProtocolSupport.Command.CLUSTER);
        final Single<String> result = partitionedRedisClient.request(partitionAttributesBuilder.build(), request,
                    String.class);
        return result;
    }

    @Override
    public Single<String> clusterForget(final Buffer nodeId) {
        requireNonNull(nodeId);
        final int len = 3;
        final int capacity = 1 + calculateInitialCommandBufferSize(len, RedisProtocolSupport.Command.CLUSTER) +
                    calculateRequestArgumentSize(nodeId);
        Buffer buffer = partitionedRedisClient.executionContext().bufferAllocator().newBuffer(capacity);
        writeRequestArraySize(buffer, len);
        RedisProtocolSupport.Command.CLUSTER.encodeTo(buffer);
        RedisProtocolSupport.SubCommand.FORGET.encodeTo(buffer);
        writeRequestArgument(buffer, nodeId);
        final RedisRequest request = newRequest(RedisProtocolSupport.Command.CLUSTER, buffer);
        final RedisPartitionAttributesBuilder partitionAttributesBuilder = partitionAttributesBuilderFunction
                    .apply(RedisProtocolSupport.Command.CLUSTER);
        final Single<String> result = partitionedRedisClient.request(partitionAttributesBuilder.build(), request,
                    String.class);
        return result;
    }

    @Override
    public <T> Single<List<T>> clusterGetkeysinslot(final long slot, final long count) {
        final int len = 4;
        final int capacity = 1 + calculateInitialCommandBufferSize(len, RedisProtocolSupport.Command.CLUSTER) +
                    calculateRequestArgumentSize(slot) + calculateRequestArgumentSize(count);
        Buffer buffer = partitionedRedisClient.executionContext().bufferAllocator().newBuffer(capacity);
        writeRequestArraySize(buffer, len);
        RedisProtocolSupport.Command.CLUSTER.encodeTo(buffer);
        RedisProtocolSupport.SubCommand.GETKEYSINSLOT.encodeTo(buffer);
        writeRequestArgument(buffer, slot);
        writeRequestArgument(buffer, count);
        final RedisRequest request = newRequest(RedisProtocolSupport.Command.CLUSTER, buffer);
        final RedisPartitionAttributesBuilder partitionAttributesBuilder = partitionAttributesBuilderFunction
                    .apply(RedisProtocolSupport.Command.CLUSTER);
        final Single<List<T>> result = (Single) partitionedRedisClient.request(partitionAttributesBuilder.build(),
                    request, List.class);
        return result;
    }

    @Override
    public Single<Buffer> clusterInfo() {
        final int len = 2;
        final int capacity = 1 + calculateInitialCommandBufferSize(len, RedisProtocolSupport.Command.CLUSTER);
        Buffer buffer = partitionedRedisClient.executionContext().bufferAllocator().newBuffer(capacity);
        writeRequestArraySize(buffer, len);
        RedisProtocolSupport.Command.CLUSTER.encodeTo(buffer);
        RedisProtocolSupport.SubCommand.INFO.encodeTo(buffer);
        final RedisRequest request = newRequest(RedisProtocolSupport.Command.CLUSTER, buffer);
        final RedisPartitionAttributesBuilder partitionAttributesBuilder = partitionAttributesBuilderFunction
                    .apply(RedisProtocolSupport.Command.CLUSTER);
        final Single<Buffer> result = partitionedRedisClient.request(partitionAttributesBuilder.build(), request,
                    Buffer.class);
        return result;
    }

    @Override
    public Single<Long> clusterKeyslot(final Buffer key) {
        requireNonNull(key);
        final int len = 3;
        final int capacity = 1 + calculateInitialCommandBufferSize(len, RedisProtocolSupport.Command.CLUSTER) +
                    calculateRequestArgumentSize(key);
        Buffer buffer = partitionedRedisClient.executionContext().bufferAllocator().newBuffer(capacity);
        writeRequestArraySize(buffer, len);
        RedisProtocolSupport.Command.CLUSTER.encodeTo(buffer);
        RedisProtocolSupport.SubCommand.KEYSLOT.encodeTo(buffer);
        writeRequestArgument(buffer, key);
        final RedisRequest request = newRequest(RedisProtocolSupport.Command.CLUSTER, buffer);
        final RedisPartitionAttributesBuilder partitionAttributesBuilder = partitionAttributesBuilderFunction
                    .apply(RedisProtocolSupport.Command.CLUSTER);
        final Single<Long> result = partitionedRedisClient.request(partitionAttributesBuilder.build(), request,
                    Long.class);
        return result;
    }

    @Override
    public Single<String> clusterMeet(final Buffer ip, final long port) {
        requireNonNull(ip);
        final int len = 4;
        final int capacity = 1 + calculateInitialCommandBufferSize(len, RedisProtocolSupport.Command.CLUSTER) +
                    calculateRequestArgumentSize(ip) + calculateRequestArgumentSize(port);
        Buffer buffer = partitionedRedisClient.executionContext().bufferAllocator().newBuffer(capacity);
        writeRequestArraySize(buffer, len);
        RedisProtocolSupport.Command.CLUSTER.encodeTo(buffer);
        RedisProtocolSupport.SubCommand.MEET.encodeTo(buffer);
        writeRequestArgument(buffer, ip);
        writeRequestArgument(buffer, port);
        final RedisRequest request = newRequest(RedisProtocolSupport.Command.CLUSTER, buffer);
        final RedisPartitionAttributesBuilder partitionAttributesBuilder = partitionAttributesBuilderFunction
                    .apply(RedisProtocolSupport.Command.CLUSTER);
        final Single<String> result = partitionedRedisClient.request(partitionAttributesBuilder.build(), request,
                    String.class);
        return result;
    }

    @Override
    public Single<Buffer> clusterNodes() {
        final int len = 2;
        final int capacity = 1 + calculateInitialCommandBufferSize(len, RedisProtocolSupport.Command.CLUSTER);
        Buffer buffer = partitionedRedisClient.executionContext().bufferAllocator().newBuffer(capacity);
        writeRequestArraySize(buffer, len);
        RedisProtocolSupport.Command.CLUSTER.encodeTo(buffer);
        RedisProtocolSupport.SubCommand.NODES.encodeTo(buffer);
        final RedisRequest request = newRequest(RedisProtocolSupport.Command.CLUSTER, buffer);
        final RedisPartitionAttributesBuilder partitionAttributesBuilder = partitionAttributesBuilderFunction
                    .apply(RedisProtocolSupport.Command.CLUSTER);
        final Single<Buffer> result = partitionedRedisClient.request(partitionAttributesBuilder.build(), request,
                    Buffer.class);
        return result;
    }

    @Override
    public Single<String> clusterReplicate(final Buffer nodeId) {
        requireNonNull(nodeId);
        final int len = 3;
        final int capacity = 1 + calculateInitialCommandBufferSize(len, RedisProtocolSupport.Command.CLUSTER) +
                    calculateRequestArgumentSize(nodeId);
        Buffer buffer = partitionedRedisClient.executionContext().bufferAllocator().newBuffer(capacity);
        writeRequestArraySize(buffer, len);
        RedisProtocolSupport.Command.CLUSTER.encodeTo(buffer);
        RedisProtocolSupport.SubCommand.REPLICATE.encodeTo(buffer);
        writeRequestArgument(buffer, nodeId);
        final RedisRequest request = newRequest(RedisProtocolSupport.Command.CLUSTER, buffer);
        final RedisPartitionAttributesBuilder partitionAttributesBuilder = partitionAttributesBuilderFunction
                    .apply(RedisProtocolSupport.Command.CLUSTER);
        final Single<String> result = partitionedRedisClient.request(partitionAttributesBuilder.build(), request,
                    String.class);
        return result;
    }

    @Override
    public Single<String> clusterReset() {
        final int len = 2;
        final int capacity = 1 + calculateInitialCommandBufferSize(len, RedisProtocolSupport.Command.CLUSTER);
        Buffer buffer = partitionedRedisClient.executionContext().bufferAllocator().newBuffer(capacity);
        writeRequestArraySize(buffer, len);
        RedisProtocolSupport.Command.CLUSTER.encodeTo(buffer);
        RedisProtocolSupport.SubCommand.RESET.encodeTo(buffer);
        final RedisRequest request = newRequest(RedisProtocolSupport.Command.CLUSTER, buffer);
        final RedisPartitionAttributesBuilder partitionAttributesBuilder = partitionAttributesBuilderFunction
                    .apply(RedisProtocolSupport.Command.CLUSTER);
        final Single<String> result = partitionedRedisClient.request(partitionAttributesBuilder.build(), request,
                    String.class);
        return result;
    }

    @Override
    public Single<String> clusterReset(@Nullable final RedisProtocolSupport.ClusterResetResetType resetType) {
        final int len = 2 + (resetType == null ? 0 : 1);
        final int capacity = 1 + calculateInitialCommandBufferSize(len, RedisProtocolSupport.Command.CLUSTER) +
                    (resetType == null ? 0 : resetType.encodedByteCount());
        Buffer buffer = partitionedRedisClient.executionContext().bufferAllocator().newBuffer(capacity);
        writeRequestArraySize(buffer, len);
        RedisProtocolSupport.Command.CLUSTER.encodeTo(buffer);
        RedisProtocolSupport.SubCommand.RESET.encodeTo(buffer);
        if (resetType != null) {
            resetType.encodeTo(buffer);
        }
        final RedisRequest request = newRequest(RedisProtocolSupport.Command.CLUSTER, buffer);
        final RedisPartitionAttributesBuilder partitionAttributesBuilder = partitionAttributesBuilderFunction
                    .apply(RedisProtocolSupport.Command.CLUSTER);
        final Single<String> result = partitionedRedisClient.request(partitionAttributesBuilder.build(), request,
                    String.class);
        return result;
    }

    @Override
    public Single<String> clusterSaveconfig() {
        final int len = 2;
        final int capacity = 1 + calculateInitialCommandBufferSize(len, RedisProtocolSupport.Command.CLUSTER);
        Buffer buffer = partitionedRedisClient.executionContext().bufferAllocator().newBuffer(capacity);
        writeRequestArraySize(buffer, len);
        RedisProtocolSupport.Command.CLUSTER.encodeTo(buffer);
        RedisProtocolSupport.SubCommand.SAVECONFIG.encodeTo(buffer);
        final RedisRequest request = newRequest(RedisProtocolSupport.Command.CLUSTER, buffer);
        final RedisPartitionAttributesBuilder partitionAttributesBuilder = partitionAttributesBuilderFunction
                    .apply(RedisProtocolSupport.Command.CLUSTER);
        final Single<String> result = partitionedRedisClient.request(partitionAttributesBuilder.build(), request,
                    String.class);
        return result;
    }

    @Override
    public Single<String> clusterSetConfigEpoch(final long configEpoch) {
        final int len = 3;
        final int capacity = 1 + calculateInitialCommandBufferSize(len, RedisProtocolSupport.Command.CLUSTER) +
                    calculateRequestArgumentSize(configEpoch);
        Buffer buffer = partitionedRedisClient.executionContext().bufferAllocator().newBuffer(capacity);
        writeRequestArraySize(buffer, len);
        RedisProtocolSupport.Command.CLUSTER.encodeTo(buffer);
        RedisProtocolSupport.SubCommand.SET_CONFIG_EPOCH.encodeTo(buffer);
        writeRequestArgument(buffer, configEpoch);
        final RedisRequest request = newRequest(RedisProtocolSupport.Command.CLUSTER, buffer);
        final RedisPartitionAttributesBuilder partitionAttributesBuilder = partitionAttributesBuilderFunction
                    .apply(RedisProtocolSupport.Command.CLUSTER);
        final Single<String> result = partitionedRedisClient.request(partitionAttributesBuilder.build(), request,
                    String.class);
        return result;
    }

    @Override
    public Single<String> clusterSetslot(final long slot,
                                         final RedisProtocolSupport.ClusterSetslotSubcommand subcommand) {
        requireNonNull(subcommand);
        final int len = 4;
        final int capacity = 1 + calculateInitialCommandBufferSize(len, RedisProtocolSupport.Command.CLUSTER) +
                    calculateRequestArgumentSize(slot) + subcommand.encodedByteCount();
        Buffer buffer = partitionedRedisClient.executionContext().bufferAllocator().newBuffer(capacity);
        writeRequestArraySize(buffer, len);
        RedisProtocolSupport.Command.CLUSTER.encodeTo(buffer);
        RedisProtocolSupport.SubCommand.SETSLOT.encodeTo(buffer);
        writeRequestArgument(buffer, slot);
        subcommand.encodeTo(buffer);
        final RedisRequest request = newRequest(RedisProtocolSupport.Command.CLUSTER, buffer);
        final RedisPartitionAttributesBuilder partitionAttributesBuilder = partitionAttributesBuilderFunction
                    .apply(RedisProtocolSupport.Command.CLUSTER);
        final Single<String> result = partitionedRedisClient.request(partitionAttributesBuilder.build(), request,
                    String.class);
        return result;
    }

    @Override
    public Single<String> clusterSetslot(final long slot,
                                         final RedisProtocolSupport.ClusterSetslotSubcommand subcommand,
                                         @Nullable final Buffer nodeId) {
        requireNonNull(subcommand);
        final int len = 4 + (nodeId == null ? 0 : 1);
        final int capacity = 1 + calculateInitialCommandBufferSize(len, RedisProtocolSupport.Command.CLUSTER) +
                    calculateRequestArgumentSize(slot) + subcommand.encodedByteCount() +
                    (nodeId == null ? 0 : calculateRequestArgumentSize(nodeId));
        Buffer buffer = partitionedRedisClient.executionContext().bufferAllocator().newBuffer(capacity);
        writeRequestArraySize(buffer, len);
        RedisProtocolSupport.Command.CLUSTER.encodeTo(buffer);
        RedisProtocolSupport.SubCommand.SETSLOT.encodeTo(buffer);
        writeRequestArgument(buffer, slot);
        subcommand.encodeTo(buffer);
        if (nodeId != null) {
            writeRequestArgument(buffer, nodeId);
        }
        final RedisRequest request = newRequest(RedisProtocolSupport.Command.CLUSTER, buffer);
        final RedisPartitionAttributesBuilder partitionAttributesBuilder = partitionAttributesBuilderFunction
                    .apply(RedisProtocolSupport.Command.CLUSTER);
        final Single<String> result = partitionedRedisClient.request(partitionAttributesBuilder.build(), request,
                    String.class);
        return result;
    }

    @Override
    public Single<Buffer> clusterSlaves(final Buffer nodeId) {
        requireNonNull(nodeId);
        final int len = 3;
        final int capacity = 1 + calculateInitialCommandBufferSize(len, RedisProtocolSupport.Command.CLUSTER) +
                    calculateRequestArgumentSize(nodeId);
        Buffer buffer = partitionedRedisClient.executionContext().bufferAllocator().newBuffer(capacity);
        writeRequestArraySize(buffer, len);
        RedisProtocolSupport.Command.CLUSTER.encodeTo(buffer);
        RedisProtocolSupport.SubCommand.SLAVES.encodeTo(buffer);
        writeRequestArgument(buffer, nodeId);
        final RedisRequest request = newRequest(RedisProtocolSupport.Command.CLUSTER, buffer);
        final RedisPartitionAttributesBuilder partitionAttributesBuilder = partitionAttributesBuilderFunction
                    .apply(RedisProtocolSupport.Command.CLUSTER);
        final Single<Buffer> result = partitionedRedisClient.request(partitionAttributesBuilder.build(), request,
                    Buffer.class);
        return result;
    }

    @Override
    public <T> Single<List<T>> clusterSlots() {
        final int len = 2;
        final int capacity = 1 + calculateInitialCommandBufferSize(len, RedisProtocolSupport.Command.CLUSTER);
        Buffer buffer = partitionedRedisClient.executionContext().bufferAllocator().newBuffer(capacity);
        writeRequestArraySize(buffer, len);
        RedisProtocolSupport.Command.CLUSTER.encodeTo(buffer);
        RedisProtocolSupport.SubCommand.SLOTS.encodeTo(buffer);
        final RedisRequest request = newRequest(RedisProtocolSupport.Command.CLUSTER, buffer);
        final RedisPartitionAttributesBuilder partitionAttributesBuilder = partitionAttributesBuilderFunction
                    .apply(RedisProtocolSupport.Command.CLUSTER);
        final Single<List<T>> result = (Single) partitionedRedisClient.request(partitionAttributesBuilder.build(),
                    request, List.class);
        return result;
    }

    @Override
    public <T> Single<List<T>> command() {
        final int len = 1;
        final int capacity = calculateInitialCommandBufferSize(len, RedisProtocolSupport.Command.COMMAND);
        Buffer buffer = partitionedRedisClient.executionContext().bufferAllocator().newBuffer(capacity);
        writeRequestArraySize(buffer, len);
        RedisProtocolSupport.Command.COMMAND.encodeTo(buffer);
        final RedisRequest request = newRequest(RedisProtocolSupport.Command.COMMAND, buffer);
        final RedisPartitionAttributesBuilder partitionAttributesBuilder = partitionAttributesBuilderFunction
                    .apply(RedisProtocolSupport.Command.COMMAND);
        final Single<List<T>> result = (Single) partitionedRedisClient.request(partitionAttributesBuilder.build(),
                    request, List.class);
        return result;
    }

    @Override
    public Single<Long> commandCount() {
        final int len = 2;
        final int capacity = 1 + calculateInitialCommandBufferSize(len, RedisProtocolSupport.Command.COMMAND);
        Buffer buffer = partitionedRedisClient.executionContext().bufferAllocator().newBuffer(capacity);
        writeRequestArraySize(buffer, len);
        RedisProtocolSupport.Command.COMMAND.encodeTo(buffer);
        RedisProtocolSupport.SubCommand.COUNT.encodeTo(buffer);
        final RedisRequest request = newRequest(RedisProtocolSupport.Command.COMMAND, buffer);
        final RedisPartitionAttributesBuilder partitionAttributesBuilder = partitionAttributesBuilderFunction
                    .apply(RedisProtocolSupport.Command.COMMAND);
        final Single<Long> result = partitionedRedisClient.request(partitionAttributesBuilder.build(), request,
                    Long.class);
        return result;
    }

    @Override
    public <T> Single<List<T>> commandGetkeys() {
        final int len = 2;
        final int capacity = 1 + calculateInitialCommandBufferSize(len, RedisProtocolSupport.Command.COMMAND);
        Buffer buffer = partitionedRedisClient.executionContext().bufferAllocator().newBuffer(capacity);
        writeRequestArraySize(buffer, len);
        RedisProtocolSupport.Command.COMMAND.encodeTo(buffer);
        RedisProtocolSupport.SubCommand.GETKEYS.encodeTo(buffer);
        final RedisRequest request = newRequest(RedisProtocolSupport.Command.COMMAND, buffer);
        final RedisPartitionAttributesBuilder partitionAttributesBuilder = partitionAttributesBuilderFunction
                    .apply(RedisProtocolSupport.Command.COMMAND);
        final Single<List<T>> result = (Single) partitionedRedisClient.request(partitionAttributesBuilder.build(),
                    request, List.class);
        return result;
    }

    @Override
    public <T> Single<List<T>> commandInfo(final Buffer commandName) {
        requireNonNull(commandName);
        final int len = 3;
        final int capacity = 1 + calculateInitialCommandBufferSize(len, RedisProtocolSupport.Command.COMMAND) +
                    calculateRequestArgumentSize(commandName);
        Buffer buffer = partitionedRedisClient.executionContext().bufferAllocator().newBuffer(capacity);
        writeRequestArraySize(buffer, len);
        RedisProtocolSupport.Command.COMMAND.encodeTo(buffer);
        RedisProtocolSupport.SubCommand.INFO.encodeTo(buffer);
        writeRequestArgument(buffer, commandName);
        final RedisRequest request = newRequest(RedisProtocolSupport.Command.COMMAND, buffer);
        final RedisPartitionAttributesBuilder partitionAttributesBuilder = partitionAttributesBuilderFunction
                    .apply(RedisProtocolSupport.Command.COMMAND);
        final Single<List<T>> result = (Single) partitionedRedisClient.request(partitionAttributesBuilder.build(),
                    request, List.class);
        return result;
    }

    @Override
    public <T> Single<List<T>> commandInfo(final Buffer commandName1, final Buffer commandName2) {
        requireNonNull(commandName1);
        requireNonNull(commandName2);
        final int len = 4;
        final int capacity = 1 + calculateInitialCommandBufferSize(len, RedisProtocolSupport.Command.COMMAND) +
                    calculateRequestArgumentSize(commandName1) + calculateRequestArgumentSize(commandName2);
        Buffer buffer = partitionedRedisClient.executionContext().bufferAllocator().newBuffer(capacity);
        writeRequestArraySize(buffer, len);
        RedisProtocolSupport.Command.COMMAND.encodeTo(buffer);
        RedisProtocolSupport.SubCommand.INFO.encodeTo(buffer);
        writeRequestArgument(buffer, commandName1);
        writeRequestArgument(buffer, commandName2);
        final RedisRequest request = newRequest(RedisProtocolSupport.Command.COMMAND, buffer);
        final RedisPartitionAttributesBuilder partitionAttributesBuilder = partitionAttributesBuilderFunction
                    .apply(RedisProtocolSupport.Command.COMMAND);
        final Single<List<T>> result = (Single) partitionedRedisClient.request(partitionAttributesBuilder.build(),
                    request, List.class);
        return result;
    }

    @Override
    public <T> Single<List<T>> commandInfo(final Buffer commandName1, final Buffer commandName2,
                                           final Buffer commandName3) {
        requireNonNull(commandName1);
        requireNonNull(commandName2);
        requireNonNull(commandName3);
        final int len = 5;
        final int capacity = 1 + calculateInitialCommandBufferSize(len, RedisProtocolSupport.Command.COMMAND) +
                    calculateRequestArgumentSize(commandName1) + calculateRequestArgumentSize(commandName2) +
                    calculateRequestArgumentSize(commandName3);
        Buffer buffer = partitionedRedisClient.executionContext().bufferAllocator().newBuffer(capacity);
        writeRequestArraySize(buffer, len);
        RedisProtocolSupport.Command.COMMAND.encodeTo(buffer);
        RedisProtocolSupport.SubCommand.INFO.encodeTo(buffer);
        writeRequestArgument(buffer, commandName1);
        writeRequestArgument(buffer, commandName2);
        writeRequestArgument(buffer, commandName3);
        final RedisRequest request = newRequest(RedisProtocolSupport.Command.COMMAND, buffer);
        final RedisPartitionAttributesBuilder partitionAttributesBuilder = partitionAttributesBuilderFunction
                    .apply(RedisProtocolSupport.Command.COMMAND);
        final Single<List<T>> result = (Single) partitionedRedisClient.request(partitionAttributesBuilder.build(),
                    request, List.class);
        return result;
    }

    @Override
    public <T> Single<List<T>> commandInfo(final Collection<Buffer> commandNames) {
        requireNonNull(commandNames);
        final int len = 2 + commandNames.size();
        int commandNamesCapacity = 0;
        if (commandNames instanceof List && commandNames instanceof RandomAccess) {
            final List<Buffer> list = (List<Buffer>) commandNames;
            for (int i = 0; i < list.size(); ++i) {
                final Buffer arg = list.get(i);
                commandNamesCapacity += calculateRequestArgumentSize(arg);
            }
        } else {
            for (Buffer arg : commandNames) {
                commandNamesCapacity += calculateRequestArgumentSize(arg);
            }
        }
        final int capacity = 1 + calculateInitialCommandBufferSize(len, RedisProtocolSupport.Command.COMMAND) +
                    commandNamesCapacity;
        Buffer buffer = partitionedRedisClient.executionContext().bufferAllocator().newBuffer(capacity);
        writeRequestArraySize(buffer, len);
        RedisProtocolSupport.Command.COMMAND.encodeTo(buffer);
        RedisProtocolSupport.SubCommand.INFO.encodeTo(buffer);
        if (commandNames instanceof List && commandNames instanceof RandomAccess) {
            final List<Buffer> list = (List<Buffer>) commandNames;
            for (int i = 0; i < list.size(); ++i) {
                final Buffer arg = list.get(i);
                writeRequestArgument(buffer, arg);
            }
        } else {
            for (Buffer arg : commandNames) {
                writeRequestArgument(buffer, arg);
            }
        }
        final RedisRequest request = newRequest(RedisProtocolSupport.Command.COMMAND, buffer);
        final RedisPartitionAttributesBuilder partitionAttributesBuilder = partitionAttributesBuilderFunction
                    .apply(RedisProtocolSupport.Command.COMMAND);
        final Single<List<T>> result = (Single) partitionedRedisClient.request(partitionAttributesBuilder.build(),
                    request, List.class);
        return result;
    }

    @Override
    public <T> Single<List<T>> configGet(final Buffer parameter) {
        requireNonNull(parameter);
        final int len = 3;
        final int capacity = 1 + calculateInitialCommandBufferSize(len, RedisProtocolSupport.Command.CONFIG) +
                    calculateRequestArgumentSize(parameter);
        Buffer buffer = partitionedRedisClient.executionContext().bufferAllocator().newBuffer(capacity);
        writeRequestArraySize(buffer, len);
        RedisProtocolSupport.Command.CONFIG.encodeTo(buffer);
        RedisProtocolSupport.SubCommand.GET.encodeTo(buffer);
        writeRequestArgument(buffer, parameter);
        final RedisRequest request = newRequest(RedisProtocolSupport.Command.CONFIG, buffer);
        final RedisPartitionAttributesBuilder partitionAttributesBuilder = partitionAttributesBuilderFunction
                    .apply(RedisProtocolSupport.Command.CONFIG);
        final Single<List<T>> result = (Single) partitionedRedisClient.request(partitionAttributesBuilder.build(),
                    request, List.class);
        return result;
    }

    @Override
    public Single<String> configRewrite() {
        final int len = 2;
        final int capacity = 1 + calculateInitialCommandBufferSize(len, RedisProtocolSupport.Command.CONFIG);
        Buffer buffer = partitionedRedisClient.executionContext().bufferAllocator().newBuffer(capacity);
        writeRequestArraySize(buffer, len);
        RedisProtocolSupport.Command.CONFIG.encodeTo(buffer);
        RedisProtocolSupport.SubCommand.REWRITE.encodeTo(buffer);
        final RedisRequest request = newRequest(RedisProtocolSupport.Command.CONFIG, buffer);
        final RedisPartitionAttributesBuilder partitionAttributesBuilder = partitionAttributesBuilderFunction
                    .apply(RedisProtocolSupport.Command.CONFIG);
        final Single<String> result = partitionedRedisClient.request(partitionAttributesBuilder.build(), request,
                    String.class);
        return result;
    }

    @Override
    public Single<String> configSet(final Buffer parameter, final Buffer value) {
        requireNonNull(parameter);
        requireNonNull(value);
        final int len = 4;
        final int capacity = 1 + calculateInitialCommandBufferSize(len, RedisProtocolSupport.Command.CONFIG) +
                    calculateRequestArgumentSize(parameter) + calculateRequestArgumentSize(value);
        Buffer buffer = partitionedRedisClient.executionContext().bufferAllocator().newBuffer(capacity);
        writeRequestArraySize(buffer, len);
        RedisProtocolSupport.Command.CONFIG.encodeTo(buffer);
        RedisProtocolSupport.SubCommand.SET.encodeTo(buffer);
        writeRequestArgument(buffer, parameter);
        writeRequestArgument(buffer, value);
        final RedisRequest request = newRequest(RedisProtocolSupport.Command.CONFIG, buffer);
        final RedisPartitionAttributesBuilder partitionAttributesBuilder = partitionAttributesBuilderFunction
                    .apply(RedisProtocolSupport.Command.CONFIG);
        final Single<String> result = partitionedRedisClient.request(partitionAttributesBuilder.build(), request,
                    String.class);
        return result;
    }

    @Override
    public Single<String> configResetstat() {
        final int len = 2;
        final int capacity = 1 + calculateInitialCommandBufferSize(len, RedisProtocolSupport.Command.CONFIG);
        Buffer buffer = partitionedRedisClient.executionContext().bufferAllocator().newBuffer(capacity);
        writeRequestArraySize(buffer, len);
        RedisProtocolSupport.Command.CONFIG.encodeTo(buffer);
        RedisProtocolSupport.SubCommand.RESETSTAT.encodeTo(buffer);
        final RedisRequest request = newRequest(RedisProtocolSupport.Command.CONFIG, buffer);
        final RedisPartitionAttributesBuilder partitionAttributesBuilder = partitionAttributesBuilderFunction
                    .apply(RedisProtocolSupport.Command.CONFIG);
        final Single<String> result = partitionedRedisClient.request(partitionAttributesBuilder.build(), request,
                    String.class);
        return result;
    }

    @Override
    public Single<Long> dbsize() {
        final int len = 1;
        final int capacity = calculateInitialCommandBufferSize(len, RedisProtocolSupport.Command.DBSIZE);
        Buffer buffer = partitionedRedisClient.executionContext().bufferAllocator().newBuffer(capacity);
        writeRequestArraySize(buffer, len);
        RedisProtocolSupport.Command.DBSIZE.encodeTo(buffer);
        final RedisRequest request = newRequest(RedisProtocolSupport.Command.DBSIZE, buffer);
        final RedisPartitionAttributesBuilder partitionAttributesBuilder = partitionAttributesBuilderFunction
                    .apply(RedisProtocolSupport.Command.DBSIZE);
        final Single<Long> result = partitionedRedisClient.request(partitionAttributesBuilder.build(), request,
                    Long.class);
        return result;
    }

    @Override
    public Single<String> debugObject(@RedisProtocolSupport.Key final Buffer key) {
        requireNonNull(key);
        final int len = 3;
        final int capacity = 1 + calculateInitialCommandBufferSize(len, RedisProtocolSupport.Command.DEBUG) +
                    calculateRequestArgumentSize(key);
        Buffer buffer = partitionedRedisClient.executionContext().bufferAllocator().newBuffer(capacity);
        writeRequestArraySize(buffer, len);
        RedisProtocolSupport.Command.DEBUG.encodeTo(buffer);
        RedisProtocolSupport.SubCommand.OBJECT.encodeTo(buffer);
        writeRequestArgument(buffer, key);
        final RedisRequest request = newRequest(RedisProtocolSupport.Command.DEBUG, buffer);
        final RedisPartitionAttributesBuilder partitionAttributesBuilder = partitionAttributesBuilderFunction
                    .apply(RedisProtocolSupport.Command.DEBUG);
        partitionAttributesBuilder.addKey(key);
        final Single<String> result = partitionedRedisClient.request(partitionAttributesBuilder.build(), request,
                    String.class);
        return result;
    }

    @Override
    public Single<String> debugSegfault() {
        final int len = 2;
        final int capacity = 1 + calculateInitialCommandBufferSize(len, RedisProtocolSupport.Command.DEBUG);
        Buffer buffer = partitionedRedisClient.executionContext().bufferAllocator().newBuffer(capacity);
        writeRequestArraySize(buffer, len);
        RedisProtocolSupport.Command.DEBUG.encodeTo(buffer);
        RedisProtocolSupport.SubCommand.SEGFAULT.encodeTo(buffer);
        final RedisRequest request = newRequest(RedisProtocolSupport.Command.DEBUG, buffer);
        final RedisPartitionAttributesBuilder partitionAttributesBuilder = partitionAttributesBuilderFunction
                    .apply(RedisProtocolSupport.Command.DEBUG);
        final Single<String> result = partitionedRedisClient.request(partitionAttributesBuilder.build(), request,
                    String.class);
        return result;
    }

    @Override
    public Single<Long> decr(@RedisProtocolSupport.Key final Buffer key) {
        requireNonNull(key);
        final int len = 2;
        final int capacity = calculateInitialCommandBufferSize(len, RedisProtocolSupport.Command.DECR) +
                    calculateRequestArgumentSize(key);
        Buffer buffer = partitionedRedisClient.executionContext().bufferAllocator().newBuffer(capacity);
        writeRequestArraySize(buffer, len);
        RedisProtocolSupport.Command.DECR.encodeTo(buffer);
        writeRequestArgument(buffer, key);
        final RedisRequest request = newRequest(RedisProtocolSupport.Command.DECR, buffer);
        final RedisPartitionAttributesBuilder partitionAttributesBuilder = partitionAttributesBuilderFunction
                    .apply(RedisProtocolSupport.Command.DECR);
        partitionAttributesBuilder.addKey(key);
        final Single<Long> result = partitionedRedisClient.request(partitionAttributesBuilder.build(), request,
                    Long.class);
        return result;
    }

    @Override
    public Single<Long> decrby(@RedisProtocolSupport.Key final Buffer key, final long decrement) {
        requireNonNull(key);
        final int len = 3;
        final int capacity = calculateInitialCommandBufferSize(len, RedisProtocolSupport.Command.DECRBY) +
                    calculateRequestArgumentSize(key) + calculateRequestArgumentSize(decrement);
        Buffer buffer = partitionedRedisClient.executionContext().bufferAllocator().newBuffer(capacity);
        writeRequestArraySize(buffer, len);
        RedisProtocolSupport.Command.DECRBY.encodeTo(buffer);
        writeRequestArgument(buffer, key);
        writeRequestArgument(buffer, decrement);
        final RedisRequest request = newRequest(RedisProtocolSupport.Command.DECRBY, buffer);
        final RedisPartitionAttributesBuilder partitionAttributesBuilder = partitionAttributesBuilderFunction
                    .apply(RedisProtocolSupport.Command.DECRBY);
        partitionAttributesBuilder.addKey(key);
        final Single<Long> result = partitionedRedisClient.request(partitionAttributesBuilder.build(), request,
                    Long.class);
        return result;
    }

    @Override
    public Single<Long> del(@RedisProtocolSupport.Key final Buffer key) {
        requireNonNull(key);
        final int len = 2;
        final int capacity = calculateInitialCommandBufferSize(len, RedisProtocolSupport.Command.DEL) +
                    calculateRequestArgumentSize(key);
        Buffer buffer = partitionedRedisClient.executionContext().bufferAllocator().newBuffer(capacity);
        writeRequestArraySize(buffer, len);
        RedisProtocolSupport.Command.DEL.encodeTo(buffer);
        writeRequestArgument(buffer, key);
        final RedisRequest request = newRequest(RedisProtocolSupport.Command.DEL, buffer);
        final RedisPartitionAttributesBuilder partitionAttributesBuilder = partitionAttributesBuilderFunction
                    .apply(RedisProtocolSupport.Command.DEL);
        partitionAttributesBuilder.addKey(key);
        final Single<Long> result = partitionedRedisClient.request(partitionAttributesBuilder.build(), request,
                    Long.class);
        return result;
    }

    @Override
    public Single<Long> del(@RedisProtocolSupport.Key final Buffer key1, @RedisProtocolSupport.Key final Buffer key2) {
        requireNonNull(key1);
        requireNonNull(key2);
        final int len = 3;
        final int capacity = calculateInitialCommandBufferSize(len, RedisProtocolSupport.Command.DEL) +
                    calculateRequestArgumentSize(key1) + calculateRequestArgumentSize(key2);
        Buffer buffer = partitionedRedisClient.executionContext().bufferAllocator().newBuffer(capacity);
        writeRequestArraySize(buffer, len);
        RedisProtocolSupport.Command.DEL.encodeTo(buffer);
        writeRequestArgument(buffer, key1);
        writeRequestArgument(buffer, key2);
        final RedisRequest request = newRequest(RedisProtocolSupport.Command.DEL, buffer);
        final RedisPartitionAttributesBuilder partitionAttributesBuilder = partitionAttributesBuilderFunction
                    .apply(RedisProtocolSupport.Command.DEL);
        partitionAttributesBuilder.addKey(key1);
        partitionAttributesBuilder.addKey(key2);
        final Single<Long> result = partitionedRedisClient.request(partitionAttributesBuilder.build(), request,
                    Long.class);
        return result;
    }

    @Override
    public Single<Long> del(@RedisProtocolSupport.Key final Buffer key1, @RedisProtocolSupport.Key final Buffer key2,
                            @RedisProtocolSupport.Key final Buffer key3) {
        requireNonNull(key1);
        requireNonNull(key2);
        requireNonNull(key3);
        final int len = 4;
        final int capacity = calculateInitialCommandBufferSize(len, RedisProtocolSupport.Command.DEL) +
                    calculateRequestArgumentSize(key1) + calculateRequestArgumentSize(key2) +
                    calculateRequestArgumentSize(key3);
        Buffer buffer = partitionedRedisClient.executionContext().bufferAllocator().newBuffer(capacity);
        writeRequestArraySize(buffer, len);
        RedisProtocolSupport.Command.DEL.encodeTo(buffer);
        writeRequestArgument(buffer, key1);
        writeRequestArgument(buffer, key2);
        writeRequestArgument(buffer, key3);
        final RedisRequest request = newRequest(RedisProtocolSupport.Command.DEL, buffer);
        final RedisPartitionAttributesBuilder partitionAttributesBuilder = partitionAttributesBuilderFunction
                    .apply(RedisProtocolSupport.Command.DEL);
        partitionAttributesBuilder.addKey(key1);
        partitionAttributesBuilder.addKey(key2);
        partitionAttributesBuilder.addKey(key3);
        final Single<Long> result = partitionedRedisClient.request(partitionAttributesBuilder.build(), request,
                    Long.class);
        return result;
    }

    @Override
    public Single<Long> del(@RedisProtocolSupport.Key final Collection<Buffer> keys) {
        requireNonNull(keys);
        final int len = 1 + keys.size();
        int keysCapacity = 0;
        if (keys instanceof List && keys instanceof RandomAccess) {
            final List<Buffer> list = (List<Buffer>) keys;
            for (int i = 0; i < list.size(); ++i) {
                final Buffer arg = list.get(i);
                keysCapacity += calculateRequestArgumentSize(arg);
            }
        } else {
            for (Buffer arg : keys) {
                keysCapacity += calculateRequestArgumentSize(arg);
            }
        }
        final int capacity = calculateInitialCommandBufferSize(len, RedisProtocolSupport.Command.DEL) + keysCapacity;
        Buffer buffer = partitionedRedisClient.executionContext().bufferAllocator().newBuffer(capacity);
        writeRequestArraySize(buffer, len);
        RedisProtocolSupport.Command.DEL.encodeTo(buffer);
        if (keys instanceof List && keys instanceof RandomAccess) {
            final List<Buffer> list = (List<Buffer>) keys;
            for (int i = 0; i < list.size(); ++i) {
                final Buffer arg = list.get(i);
                writeRequestArgument(buffer, arg);
            }
        } else {
            for (Buffer arg : keys) {
                writeRequestArgument(buffer, arg);
            }
        }
        final RedisRequest request = newRequest(RedisProtocolSupport.Command.DEL, buffer);
        final RedisPartitionAttributesBuilder partitionAttributesBuilder = partitionAttributesBuilderFunction
                    .apply(RedisProtocolSupport.Command.DEL);
        addBufferKeysToAttributeBuilder(keys, partitionAttributesBuilder);
        final Single<Long> result = partitionedRedisClient.request(partitionAttributesBuilder.build(), request,
                    Long.class);
        return result;
    }

    @Override
    public Single<Buffer> dump(@RedisProtocolSupport.Key final Buffer key) {
        requireNonNull(key);
        final int len = 2;
        final int capacity = calculateInitialCommandBufferSize(len, RedisProtocolSupport.Command.DUMP) +
                    calculateRequestArgumentSize(key);
        Buffer buffer = partitionedRedisClient.executionContext().bufferAllocator().newBuffer(capacity);
        writeRequestArraySize(buffer, len);
        RedisProtocolSupport.Command.DUMP.encodeTo(buffer);
        writeRequestArgument(buffer, key);
        final RedisRequest request = newRequest(RedisProtocolSupport.Command.DUMP, buffer);
        final RedisPartitionAttributesBuilder partitionAttributesBuilder = partitionAttributesBuilderFunction
                    .apply(RedisProtocolSupport.Command.DUMP);
        partitionAttributesBuilder.addKey(key);
        final Single<Buffer> result = partitionedRedisClient.request(partitionAttributesBuilder.build(), request,
                    Buffer.class);
        return result;
    }

    @Override
    public Single<Buffer> echo(final Buffer message) {
        requireNonNull(message);
        final int len = 2;
        final int capacity = calculateInitialCommandBufferSize(len, RedisProtocolSupport.Command.ECHO) +
                    calculateRequestArgumentSize(message);
        Buffer buffer = partitionedRedisClient.executionContext().bufferAllocator().newBuffer(capacity);
        writeRequestArraySize(buffer, len);
        RedisProtocolSupport.Command.ECHO.encodeTo(buffer);
        writeRequestArgument(buffer, message);
        final RedisRequest request = newRequest(RedisProtocolSupport.Command.ECHO, buffer);
        final RedisPartitionAttributesBuilder partitionAttributesBuilder = partitionAttributesBuilderFunction
                    .apply(RedisProtocolSupport.Command.ECHO);
        final Single<Buffer> result = partitionedRedisClient.request(partitionAttributesBuilder.build(), request,
                    Buffer.class);
        return result;
    }

    @Override
    public Single<Buffer> eval(final Buffer script, final long numkeys,
                               @RedisProtocolSupport.Key final Collection<Buffer> keys, final Collection<Buffer> args) {
        requireNonNull(script);
        requireNonNull(keys);
        requireNonNull(args);
        final int len = 3 + keys.size() + args.size();
        int keysCapacity = 0;
        if (keys instanceof List && keys instanceof RandomAccess) {
            final List<Buffer> list = (List<Buffer>) keys;
            for (int i = 0; i < list.size(); ++i) {
                final Buffer arg = list.get(i);
                keysCapacity += calculateRequestArgumentSize(arg);
            }
        } else {
            for (Buffer arg : keys) {
                keysCapacity += calculateRequestArgumentSize(arg);
            }
        }
        int argsCapacity = 0;
        if (args instanceof List && args instanceof RandomAccess) {
            final List<Buffer> list = (List<Buffer>) args;
            for (int i = 0; i < list.size(); ++i) {
                final Buffer arg = list.get(i);
                argsCapacity += calculateRequestArgumentSize(arg);
            }
        } else {
            for (Buffer arg : args) {
                argsCapacity += calculateRequestArgumentSize(arg);
            }
        }
        final int capacity = calculateInitialCommandBufferSize(len, RedisProtocolSupport.Command.EVAL) +
                    calculateRequestArgumentSize(script) + calculateRequestArgumentSize(numkeys) + keysCapacity +
                    argsCapacity;
        Buffer buffer = partitionedRedisClient.executionContext().bufferAllocator().newBuffer(capacity);
        writeRequestArraySize(buffer, len);
        RedisProtocolSupport.Command.EVAL.encodeTo(buffer);
        writeRequestArgument(buffer, script);
        writeRequestArgument(buffer, numkeys);
        if (keys instanceof List && keys instanceof RandomAccess) {
            final List<Buffer> list = (List<Buffer>) keys;
            for (int i = 0; i < list.size(); ++i) {
                final Buffer arg = list.get(i);
                writeRequestArgument(buffer, arg);
            }
        } else {
            for (Buffer arg : keys) {
                writeRequestArgument(buffer, arg);
            }
        }
        if (args instanceof List && args instanceof RandomAccess) {
            final List<Buffer> list = (List<Buffer>) args;
            for (int i = 0; i < list.size(); ++i) {
                final Buffer arg = list.get(i);
                writeRequestArgument(buffer, arg);
            }
        } else {
            for (Buffer arg : args) {
                writeRequestArgument(buffer, arg);
            }
        }
        final RedisRequest request = newRequest(RedisProtocolSupport.Command.EVAL, buffer);
        final RedisPartitionAttributesBuilder partitionAttributesBuilder = partitionAttributesBuilderFunction
                    .apply(RedisProtocolSupport.Command.EVAL);
        addBufferKeysToAttributeBuilder(keys, partitionAttributesBuilder);
        final Single<Buffer> result = partitionedRedisClient.request(partitionAttributesBuilder.build(), request,
                    Buffer.class);
        return result;
    }

    @Override
    public <T> Single<List<T>> evalList(final Buffer script, final long numkeys,
                                        @RedisProtocolSupport.Key final Collection<Buffer> keys,
                                        final Collection<Buffer> args) {
        requireNonNull(script);
        requireNonNull(keys);
        requireNonNull(args);
        final int len = 3 + keys.size() + args.size();
        int keysCapacity = 0;
        if (keys instanceof List && keys instanceof RandomAccess) {
            final List<Buffer> list = (List<Buffer>) keys;
            for (int i = 0; i < list.size(); ++i) {
                final Buffer arg = list.get(i);
                keysCapacity += calculateRequestArgumentSize(arg);
            }
        } else {
            for (Buffer arg : keys) {
                keysCapacity += calculateRequestArgumentSize(arg);
            }
        }
        int argsCapacity = 0;
        if (args instanceof List && args instanceof RandomAccess) {
            final List<Buffer> list = (List<Buffer>) args;
            for (int i = 0; i < list.size(); ++i) {
                final Buffer arg = list.get(i);
                argsCapacity += calculateRequestArgumentSize(arg);
            }
        } else {
            for (Buffer arg : args) {
                argsCapacity += calculateRequestArgumentSize(arg);
            }
        }
        final int capacity = calculateInitialCommandBufferSize(len, RedisProtocolSupport.Command.EVAL) +
                    calculateRequestArgumentSize(script) + calculateRequestArgumentSize(numkeys) + keysCapacity +
                    argsCapacity;
        Buffer buffer = partitionedRedisClient.executionContext().bufferAllocator().newBuffer(capacity);
        writeRequestArraySize(buffer, len);
        RedisProtocolSupport.Command.EVAL.encodeTo(buffer);
        writeRequestArgument(buffer, script);
        writeRequestArgument(buffer, numkeys);
        if (keys instanceof List && keys instanceof RandomAccess) {
            final List<Buffer> list = (List<Buffer>) keys;
            for (int i = 0; i < list.size(); ++i) {
                final Buffer arg = list.get(i);
                writeRequestArgument(buffer, arg);
            }
        } else {
            for (Buffer arg : keys) {
                writeRequestArgument(buffer, arg);
            }
        }
        if (args instanceof List && args instanceof RandomAccess) {
            final List<Buffer> list = (List<Buffer>) args;
            for (int i = 0; i < list.size(); ++i) {
                final Buffer arg = list.get(i);
                writeRequestArgument(buffer, arg);
            }
        } else {
            for (Buffer arg : args) {
                writeRequestArgument(buffer, arg);
            }
        }
        final RedisRequest request = newRequest(RedisProtocolSupport.Command.EVAL, buffer);
        final RedisPartitionAttributesBuilder partitionAttributesBuilder = partitionAttributesBuilderFunction
                    .apply(RedisProtocolSupport.Command.EVAL);
        addBufferKeysToAttributeBuilder(keys, partitionAttributesBuilder);
        final Single<List<T>> result = (Single) partitionedRedisClient.request(partitionAttributesBuilder.build(),
                    request, List.class);
        return result;
    }

    @Override
    public Single<Long> evalLong(final Buffer script, final long numkeys,
                                 @RedisProtocolSupport.Key final Collection<Buffer> keys,
                                 final Collection<Buffer> args) {
        requireNonNull(script);
        requireNonNull(keys);
        requireNonNull(args);
        final int len = 3 + keys.size() + args.size();
        int keysCapacity = 0;
        if (keys instanceof List && keys instanceof RandomAccess) {
            final List<Buffer> list = (List<Buffer>) keys;
            for (int i = 0; i < list.size(); ++i) {
                final Buffer arg = list.get(i);
                keysCapacity += calculateRequestArgumentSize(arg);
            }
        } else {
            for (Buffer arg : keys) {
                keysCapacity += calculateRequestArgumentSize(arg);
            }
        }
        int argsCapacity = 0;
        if (args instanceof List && args instanceof RandomAccess) {
            final List<Buffer> list = (List<Buffer>) args;
            for (int i = 0; i < list.size(); ++i) {
                final Buffer arg = list.get(i);
                argsCapacity += calculateRequestArgumentSize(arg);
            }
        } else {
            for (Buffer arg : args) {
                argsCapacity += calculateRequestArgumentSize(arg);
            }
        }
        final int capacity = calculateInitialCommandBufferSize(len, RedisProtocolSupport.Command.EVAL) +
                    calculateRequestArgumentSize(script) + calculateRequestArgumentSize(numkeys) + keysCapacity +
                    argsCapacity;
        Buffer buffer = partitionedRedisClient.executionContext().bufferAllocator().newBuffer(capacity);
        writeRequestArraySize(buffer, len);
        RedisProtocolSupport.Command.EVAL.encodeTo(buffer);
        writeRequestArgument(buffer, script);
        writeRequestArgument(buffer, numkeys);
        if (keys instanceof List && keys instanceof RandomAccess) {
            final List<Buffer> list = (List<Buffer>) keys;
            for (int i = 0; i < list.size(); ++i) {
                final Buffer arg = list.get(i);
                writeRequestArgument(buffer, arg);
            }
        } else {
            for (Buffer arg : keys) {
                writeRequestArgument(buffer, arg);
            }
        }
        if (args instanceof List && args instanceof RandomAccess) {
            final List<Buffer> list = (List<Buffer>) args;
            for (int i = 0; i < list.size(); ++i) {
                final Buffer arg = list.get(i);
                writeRequestArgument(buffer, arg);
            }
        } else {
            for (Buffer arg : args) {
                writeRequestArgument(buffer, arg);
            }
        }
        final RedisRequest request = newRequest(RedisProtocolSupport.Command.EVAL, buffer);
        final RedisPartitionAttributesBuilder partitionAttributesBuilder = partitionAttributesBuilderFunction
                    .apply(RedisProtocolSupport.Command.EVAL);
        addBufferKeysToAttributeBuilder(keys, partitionAttributesBuilder);
        final Single<Long> result = partitionedRedisClient.request(partitionAttributesBuilder.build(), request,
                    Long.class);
        return result;
    }

    @Override
    public Single<Buffer> evalsha(final Buffer sha1, final long numkeys,
                                  @RedisProtocolSupport.Key final Collection<Buffer> keys,
                                  final Collection<Buffer> args) {
        requireNonNull(sha1);
        requireNonNull(keys);
        requireNonNull(args);
        final int len = 3 + keys.size() + args.size();
        int keysCapacity = 0;
        if (keys instanceof List && keys instanceof RandomAccess) {
            final List<Buffer> list = (List<Buffer>) keys;
            for (int i = 0; i < list.size(); ++i) {
                final Buffer arg = list.get(i);
                keysCapacity += calculateRequestArgumentSize(arg);
            }
        } else {
            for (Buffer arg : keys) {
                keysCapacity += calculateRequestArgumentSize(arg);
            }
        }
        int argsCapacity = 0;
        if (args instanceof List && args instanceof RandomAccess) {
            final List<Buffer> list = (List<Buffer>) args;
            for (int i = 0; i < list.size(); ++i) {
                final Buffer arg = list.get(i);
                argsCapacity += calculateRequestArgumentSize(arg);
            }
        } else {
            for (Buffer arg : args) {
                argsCapacity += calculateRequestArgumentSize(arg);
            }
        }
        final int capacity = calculateInitialCommandBufferSize(len, RedisProtocolSupport.Command.EVALSHA) +
                    calculateRequestArgumentSize(sha1) + calculateRequestArgumentSize(numkeys) + keysCapacity +
                    argsCapacity;
        Buffer buffer = partitionedRedisClient.executionContext().bufferAllocator().newBuffer(capacity);
        writeRequestArraySize(buffer, len);
        RedisProtocolSupport.Command.EVALSHA.encodeTo(buffer);
        writeRequestArgument(buffer, sha1);
        writeRequestArgument(buffer, numkeys);
        if (keys instanceof List && keys instanceof RandomAccess) {
            final List<Buffer> list = (List<Buffer>) keys;
            for (int i = 0; i < list.size(); ++i) {
                final Buffer arg = list.get(i);
                writeRequestArgument(buffer, arg);
            }
        } else {
            for (Buffer arg : keys) {
                writeRequestArgument(buffer, arg);
            }
        }
        if (args instanceof List && args instanceof RandomAccess) {
            final List<Buffer> list = (List<Buffer>) args;
            for (int i = 0; i < list.size(); ++i) {
                final Buffer arg = list.get(i);
                writeRequestArgument(buffer, arg);
            }
        } else {
            for (Buffer arg : args) {
                writeRequestArgument(buffer, arg);
            }
        }
        final RedisRequest request = newRequest(RedisProtocolSupport.Command.EVALSHA, buffer);
        final RedisPartitionAttributesBuilder partitionAttributesBuilder = partitionAttributesBuilderFunction
                    .apply(RedisProtocolSupport.Command.EVALSHA);
        addBufferKeysToAttributeBuilder(keys, partitionAttributesBuilder);
        final Single<Buffer> result = partitionedRedisClient.request(partitionAttributesBuilder.build(), request,
                    Buffer.class);
        return result;
    }

    @Override
    public <T> Single<List<T>> evalshaList(final Buffer sha1, final long numkeys,
                                           @RedisProtocolSupport.Key final Collection<Buffer> keys,
                                           final Collection<Buffer> args) {
        requireNonNull(sha1);
        requireNonNull(keys);
        requireNonNull(args);
        final int len = 3 + keys.size() + args.size();
        int keysCapacity = 0;
        if (keys instanceof List && keys instanceof RandomAccess) {
            final List<Buffer> list = (List<Buffer>) keys;
            for (int i = 0; i < list.size(); ++i) {
                final Buffer arg = list.get(i);
                keysCapacity += calculateRequestArgumentSize(arg);
            }
        } else {
            for (Buffer arg : keys) {
                keysCapacity += calculateRequestArgumentSize(arg);
            }
        }
        int argsCapacity = 0;
        if (args instanceof List && args instanceof RandomAccess) {
            final List<Buffer> list = (List<Buffer>) args;
            for (int i = 0; i < list.size(); ++i) {
                final Buffer arg = list.get(i);
                argsCapacity += calculateRequestArgumentSize(arg);
            }
        } else {
            for (Buffer arg : args) {
                argsCapacity += calculateRequestArgumentSize(arg);
            }
        }
        final int capacity = calculateInitialCommandBufferSize(len, RedisProtocolSupport.Command.EVALSHA) +
                    calculateRequestArgumentSize(sha1) + calculateRequestArgumentSize(numkeys) + keysCapacity +
                    argsCapacity;
        Buffer buffer = partitionedRedisClient.executionContext().bufferAllocator().newBuffer(capacity);
        writeRequestArraySize(buffer, len);
        RedisProtocolSupport.Command.EVALSHA.encodeTo(buffer);
        writeRequestArgument(buffer, sha1);
        writeRequestArgument(buffer, numkeys);
        if (keys instanceof List && keys instanceof RandomAccess) {
            final List<Buffer> list = (List<Buffer>) keys;
            for (int i = 0; i < list.size(); ++i) {
                final Buffer arg = list.get(i);
                writeRequestArgument(buffer, arg);
            }
        } else {
            for (Buffer arg : keys) {
                writeRequestArgument(buffer, arg);
            }
        }
        if (args instanceof List && args instanceof RandomAccess) {
            final List<Buffer> list = (List<Buffer>) args;
            for (int i = 0; i < list.size(); ++i) {
                final Buffer arg = list.get(i);
                writeRequestArgument(buffer, arg);
            }
        } else {
            for (Buffer arg : args) {
                writeRequestArgument(buffer, arg);
            }
        }
        final RedisRequest request = newRequest(RedisProtocolSupport.Command.EVALSHA, buffer);
        final RedisPartitionAttributesBuilder partitionAttributesBuilder = partitionAttributesBuilderFunction
                    .apply(RedisProtocolSupport.Command.EVALSHA);
        addBufferKeysToAttributeBuilder(keys, partitionAttributesBuilder);
        final Single<List<T>> result = (Single) partitionedRedisClient.request(partitionAttributesBuilder.build(),
                    request, List.class);
        return result;
    }

    @Override
    public Single<Long> evalshaLong(final Buffer sha1, final long numkeys,
                                    @RedisProtocolSupport.Key final Collection<Buffer> keys,
                                    final Collection<Buffer> args) {
        requireNonNull(sha1);
        requireNonNull(keys);
        requireNonNull(args);
        final int len = 3 + keys.size() + args.size();
        int keysCapacity = 0;
        if (keys instanceof List && keys instanceof RandomAccess) {
            final List<Buffer> list = (List<Buffer>) keys;
            for (int i = 0; i < list.size(); ++i) {
                final Buffer arg = list.get(i);
                keysCapacity += calculateRequestArgumentSize(arg);
            }
        } else {
            for (Buffer arg : keys) {
                keysCapacity += calculateRequestArgumentSize(arg);
            }
        }
        int argsCapacity = 0;
        if (args instanceof List && args instanceof RandomAccess) {
            final List<Buffer> list = (List<Buffer>) args;
            for (int i = 0; i < list.size(); ++i) {
                final Buffer arg = list.get(i);
                argsCapacity += calculateRequestArgumentSize(arg);
            }
        } else {
            for (Buffer arg : args) {
                argsCapacity += calculateRequestArgumentSize(arg);
            }
        }
        final int capacity = calculateInitialCommandBufferSize(len, RedisProtocolSupport.Command.EVALSHA) +
                    calculateRequestArgumentSize(sha1) + calculateRequestArgumentSize(numkeys) + keysCapacity +
                    argsCapacity;
        Buffer buffer = partitionedRedisClient.executionContext().bufferAllocator().newBuffer(capacity);
        writeRequestArraySize(buffer, len);
        RedisProtocolSupport.Command.EVALSHA.encodeTo(buffer);
        writeRequestArgument(buffer, sha1);
        writeRequestArgument(buffer, numkeys);
        if (keys instanceof List && keys instanceof RandomAccess) {
            final List<Buffer> list = (List<Buffer>) keys;
            for (int i = 0; i < list.size(); ++i) {
                final Buffer arg = list.get(i);
                writeRequestArgument(buffer, arg);
            }
        } else {
            for (Buffer arg : keys) {
                writeRequestArgument(buffer, arg);
            }
        }
        if (args instanceof List && args instanceof RandomAccess) {
            final List<Buffer> list = (List<Buffer>) args;
            for (int i = 0; i < list.size(); ++i) {
                final Buffer arg = list.get(i);
                writeRequestArgument(buffer, arg);
            }
        } else {
            for (Buffer arg : args) {
                writeRequestArgument(buffer, arg);
            }
        }
        final RedisRequest request = newRequest(RedisProtocolSupport.Command.EVALSHA, buffer);
        final RedisPartitionAttributesBuilder partitionAttributesBuilder = partitionAttributesBuilderFunction
                    .apply(RedisProtocolSupport.Command.EVALSHA);
        addBufferKeysToAttributeBuilder(keys, partitionAttributesBuilder);
        final Single<Long> result = partitionedRedisClient.request(partitionAttributesBuilder.build(), request,
                    Long.class);
        return result;
    }

    @Override
    public Single<Long> exists(@RedisProtocolSupport.Key final Buffer key) {
        requireNonNull(key);
        final int len = 2;
        final int capacity = calculateInitialCommandBufferSize(len, RedisProtocolSupport.Command.EXISTS) +
                    calculateRequestArgumentSize(key);
        Buffer buffer = partitionedRedisClient.executionContext().bufferAllocator().newBuffer(capacity);
        writeRequestArraySize(buffer, len);
        RedisProtocolSupport.Command.EXISTS.encodeTo(buffer);
        writeRequestArgument(buffer, key);
        final RedisRequest request = newRequest(RedisProtocolSupport.Command.EXISTS, buffer);
        final RedisPartitionAttributesBuilder partitionAttributesBuilder = partitionAttributesBuilderFunction
                    .apply(RedisProtocolSupport.Command.EXISTS);
        partitionAttributesBuilder.addKey(key);
        final Single<Long> result = partitionedRedisClient.request(partitionAttributesBuilder.build(), request,
                    Long.class);
        return result;
    }

    @Override
    public Single<Long> exists(@RedisProtocolSupport.Key final Buffer key1,
                               @RedisProtocolSupport.Key final Buffer key2) {
        requireNonNull(key1);
        requireNonNull(key2);
        final int len = 3;
        final int capacity = calculateInitialCommandBufferSize(len, RedisProtocolSupport.Command.EXISTS) +
                    calculateRequestArgumentSize(key1) + calculateRequestArgumentSize(key2);
        Buffer buffer = partitionedRedisClient.executionContext().bufferAllocator().newBuffer(capacity);
        writeRequestArraySize(buffer, len);
        RedisProtocolSupport.Command.EXISTS.encodeTo(buffer);
        writeRequestArgument(buffer, key1);
        writeRequestArgument(buffer, key2);
        final RedisRequest request = newRequest(RedisProtocolSupport.Command.EXISTS, buffer);
        final RedisPartitionAttributesBuilder partitionAttributesBuilder = partitionAttributesBuilderFunction
                    .apply(RedisProtocolSupport.Command.EXISTS);
        partitionAttributesBuilder.addKey(key1);
        partitionAttributesBuilder.addKey(key2);
        final Single<Long> result = partitionedRedisClient.request(partitionAttributesBuilder.build(), request,
                    Long.class);
        return result;
    }

    @Override
    public Single<Long> exists(@RedisProtocolSupport.Key final Buffer key1, @RedisProtocolSupport.Key final Buffer key2,
                               @RedisProtocolSupport.Key final Buffer key3) {
        requireNonNull(key1);
        requireNonNull(key2);
        requireNonNull(key3);
        final int len = 4;
        final int capacity = calculateInitialCommandBufferSize(len, RedisProtocolSupport.Command.EXISTS) +
                    calculateRequestArgumentSize(key1) + calculateRequestArgumentSize(key2) +
                    calculateRequestArgumentSize(key3);
        Buffer buffer = partitionedRedisClient.executionContext().bufferAllocator().newBuffer(capacity);
        writeRequestArraySize(buffer, len);
        RedisProtocolSupport.Command.EXISTS.encodeTo(buffer);
        writeRequestArgument(buffer, key1);
        writeRequestArgument(buffer, key2);
        writeRequestArgument(buffer, key3);
        final RedisRequest request = newRequest(RedisProtocolSupport.Command.EXISTS, buffer);
        final RedisPartitionAttributesBuilder partitionAttributesBuilder = partitionAttributesBuilderFunction
                    .apply(RedisProtocolSupport.Command.EXISTS);
        partitionAttributesBuilder.addKey(key1);
        partitionAttributesBuilder.addKey(key2);
        partitionAttributesBuilder.addKey(key3);
        final Single<Long> result = partitionedRedisClient.request(partitionAttributesBuilder.build(), request,
                    Long.class);
        return result;
    }

    @Override
    public Single<Long> exists(@RedisProtocolSupport.Key final Collection<Buffer> keys) {
        requireNonNull(keys);
        final int len = 1 + keys.size();
        int keysCapacity = 0;
        if (keys instanceof List && keys instanceof RandomAccess) {
            final List<Buffer> list = (List<Buffer>) keys;
            for (int i = 0; i < list.size(); ++i) {
                final Buffer arg = list.get(i);
                keysCapacity += calculateRequestArgumentSize(arg);
            }
        } else {
            for (Buffer arg : keys) {
                keysCapacity += calculateRequestArgumentSize(arg);
            }
        }
        final int capacity = calculateInitialCommandBufferSize(len, RedisProtocolSupport.Command.EXISTS) + keysCapacity;
        Buffer buffer = partitionedRedisClient.executionContext().bufferAllocator().newBuffer(capacity);
        writeRequestArraySize(buffer, len);
        RedisProtocolSupport.Command.EXISTS.encodeTo(buffer);
        if (keys instanceof List && keys instanceof RandomAccess) {
            final List<Buffer> list = (List<Buffer>) keys;
            for (int i = 0; i < list.size(); ++i) {
                final Buffer arg = list.get(i);
                writeRequestArgument(buffer, arg);
            }
        } else {
            for (Buffer arg : keys) {
                writeRequestArgument(buffer, arg);
            }
        }
        final RedisRequest request = newRequest(RedisProtocolSupport.Command.EXISTS, buffer);
        final RedisPartitionAttributesBuilder partitionAttributesBuilder = partitionAttributesBuilderFunction
                    .apply(RedisProtocolSupport.Command.EXISTS);
        addBufferKeysToAttributeBuilder(keys, partitionAttributesBuilder);
        final Single<Long> result = partitionedRedisClient.request(partitionAttributesBuilder.build(), request,
                    Long.class);
        return result;
    }

    @Override
    public Single<Long> expire(@RedisProtocolSupport.Key final Buffer key, final long seconds) {
        requireNonNull(key);
        final int len = 3;
        final int capacity = calculateInitialCommandBufferSize(len, RedisProtocolSupport.Command.EXPIRE) +
                    calculateRequestArgumentSize(key) + calculateRequestArgumentSize(seconds);
        Buffer buffer = partitionedRedisClient.executionContext().bufferAllocator().newBuffer(capacity);
        writeRequestArraySize(buffer, len);
        RedisProtocolSupport.Command.EXPIRE.encodeTo(buffer);
        writeRequestArgument(buffer, key);
        writeRequestArgument(buffer, seconds);
        final RedisRequest request = newRequest(RedisProtocolSupport.Command.EXPIRE, buffer);
        final RedisPartitionAttributesBuilder partitionAttributesBuilder = partitionAttributesBuilderFunction
                    .apply(RedisProtocolSupport.Command.EXPIRE);
        partitionAttributesBuilder.addKey(key);
        final Single<Long> result = partitionedRedisClient.request(partitionAttributesBuilder.build(), request,
                    Long.class);
        return result;
    }

    @Override
    public Single<Long> expireat(@RedisProtocolSupport.Key final Buffer key, final long timestamp) {
        requireNonNull(key);
        final int len = 3;
        final int capacity = calculateInitialCommandBufferSize(len, RedisProtocolSupport.Command.EXPIREAT) +
                    calculateRequestArgumentSize(key) + calculateRequestArgumentSize(timestamp);
        Buffer buffer = partitionedRedisClient.executionContext().bufferAllocator().newBuffer(capacity);
        writeRequestArraySize(buffer, len);
        RedisProtocolSupport.Command.EXPIREAT.encodeTo(buffer);
        writeRequestArgument(buffer, key);
        writeRequestArgument(buffer, timestamp);
        final RedisRequest request = newRequest(RedisProtocolSupport.Command.EXPIREAT, buffer);
        final RedisPartitionAttributesBuilder partitionAttributesBuilder = partitionAttributesBuilderFunction
                    .apply(RedisProtocolSupport.Command.EXPIREAT);
        partitionAttributesBuilder.addKey(key);
        final Single<Long> result = partitionedRedisClient.request(partitionAttributesBuilder.build(), request,
                    Long.class);
        return result;
    }

    @Override
    public Single<String> flushall() {
        final int len = 1;
        final int capacity = calculateInitialCommandBufferSize(len, RedisProtocolSupport.Command.FLUSHALL);
        Buffer buffer = partitionedRedisClient.executionContext().bufferAllocator().newBuffer(capacity);
        writeRequestArraySize(buffer, len);
        RedisProtocolSupport.Command.FLUSHALL.encodeTo(buffer);
        final RedisRequest request = newRequest(RedisProtocolSupport.Command.FLUSHALL, buffer);
        final RedisPartitionAttributesBuilder partitionAttributesBuilder = partitionAttributesBuilderFunction
                    .apply(RedisProtocolSupport.Command.FLUSHALL);
        final Single<String> result = partitionedRedisClient.request(partitionAttributesBuilder.build(), request,
                    String.class);
        return result;
    }

    @Override
    public Single<String> flushall(@Nullable final RedisProtocolSupport.FlushallAsync async) {
        final int len = 1 + (async == null ? 0 : 1);
        final int capacity = calculateInitialCommandBufferSize(len, RedisProtocolSupport.Command.FLUSHALL) +
                    (async == null ? 0 : async.encodedByteCount());
        Buffer buffer = partitionedRedisClient.executionContext().bufferAllocator().newBuffer(capacity);
        writeRequestArraySize(buffer, len);
        RedisProtocolSupport.Command.FLUSHALL.encodeTo(buffer);
        if (async != null) {
            async.encodeTo(buffer);
        }
        final RedisRequest request = newRequest(RedisProtocolSupport.Command.FLUSHALL, buffer);
        final RedisPartitionAttributesBuilder partitionAttributesBuilder = partitionAttributesBuilderFunction
                    .apply(RedisProtocolSupport.Command.FLUSHALL);
        final Single<String> result = partitionedRedisClient.request(partitionAttributesBuilder.build(), request,
                    String.class);
        return result;
    }

    @Override
    public Single<String> flushdb() {
        final int len = 1;
        final int capacity = calculateInitialCommandBufferSize(len, RedisProtocolSupport.Command.FLUSHDB);
        Buffer buffer = partitionedRedisClient.executionContext().bufferAllocator().newBuffer(capacity);
        writeRequestArraySize(buffer, len);
        RedisProtocolSupport.Command.FLUSHDB.encodeTo(buffer);
        final RedisRequest request = newRequest(RedisProtocolSupport.Command.FLUSHDB, buffer);
        final RedisPartitionAttributesBuilder partitionAttributesBuilder = partitionAttributesBuilderFunction
                    .apply(RedisProtocolSupport.Command.FLUSHDB);
        final Single<String> result = partitionedRedisClient.request(partitionAttributesBuilder.build(), request,
                    String.class);
        return result;
    }

    @Override
    public Single<String> flushdb(@Nullable final RedisProtocolSupport.FlushdbAsync async) {
        final int len = 1 + (async == null ? 0 : 1);
        final int capacity = calculateInitialCommandBufferSize(len, RedisProtocolSupport.Command.FLUSHDB) +
                    (async == null ? 0 : async.encodedByteCount());
        Buffer buffer = partitionedRedisClient.executionContext().bufferAllocator().newBuffer(capacity);
        writeRequestArraySize(buffer, len);
        RedisProtocolSupport.Command.FLUSHDB.encodeTo(buffer);
        if (async != null) {
            async.encodeTo(buffer);
        }
        final RedisRequest request = newRequest(RedisProtocolSupport.Command.FLUSHDB, buffer);
        final RedisPartitionAttributesBuilder partitionAttributesBuilder = partitionAttributesBuilderFunction
                    .apply(RedisProtocolSupport.Command.FLUSHDB);
        final Single<String> result = partitionedRedisClient.request(partitionAttributesBuilder.build(), request,
                    String.class);
        return result;
    }

    @Override
    public Single<Long> geoadd(@RedisProtocolSupport.Key final Buffer key, final double longitude,
                               final double latitude, final Buffer member) {
        requireNonNull(key);
        requireNonNull(member);
        final int len = 5;
        final int capacity = calculateInitialCommandBufferSize(len, RedisProtocolSupport.Command.GEOADD) +
                    calculateRequestArgumentSize(key) + calculateRequestArgumentSize(longitude) +
                    calculateRequestArgumentSize(latitude) + calculateRequestArgumentSize(member);
        Buffer buffer = partitionedRedisClient.executionContext().bufferAllocator().newBuffer(capacity);
        writeRequestArraySize(buffer, len);
        RedisProtocolSupport.Command.GEOADD.encodeTo(buffer);
        writeRequestArgument(buffer, key);
        writeRequestArgument(buffer, longitude);
        writeRequestArgument(buffer, latitude);
        writeRequestArgument(buffer, member);
        final RedisRequest request = newRequest(RedisProtocolSupport.Command.GEOADD, buffer);
        final RedisPartitionAttributesBuilder partitionAttributesBuilder = partitionAttributesBuilderFunction
                    .apply(RedisProtocolSupport.Command.GEOADD);
        partitionAttributesBuilder.addKey(key);
        final Single<Long> result = partitionedRedisClient.request(partitionAttributesBuilder.build(), request,
                    Long.class);
        return result;
    }

    @Override
    public Single<Long> geoadd(@RedisProtocolSupport.Key final Buffer key, final double longitude1,
                               final double latitude1, final Buffer member1, final double longitude2,
                               final double latitude2, final Buffer member2) {
        requireNonNull(key);
        requireNonNull(member1);
        requireNonNull(member2);
        final int len = 8;
        final int capacity = calculateInitialCommandBufferSize(len, RedisProtocolSupport.Command.GEOADD) +
                    calculateRequestArgumentSize(key) + calculateRequestArgumentSize(longitude1) +
                    calculateRequestArgumentSize(latitude1) + calculateRequestArgumentSize(member1) +
                    calculateRequestArgumentSize(longitude2) + calculateRequestArgumentSize(latitude2) +
                    calculateRequestArgumentSize(member2);
        Buffer buffer = partitionedRedisClient.executionContext().bufferAllocator().newBuffer(capacity);
        writeRequestArraySize(buffer, len);
        RedisProtocolSupport.Command.GEOADD.encodeTo(buffer);
        writeRequestArgument(buffer, key);
        writeRequestArgument(buffer, longitude1);
        writeRequestArgument(buffer, latitude1);
        writeRequestArgument(buffer, member1);
        writeRequestArgument(buffer, longitude2);
        writeRequestArgument(buffer, latitude2);
        writeRequestArgument(buffer, member2);
        final RedisRequest request = newRequest(RedisProtocolSupport.Command.GEOADD, buffer);
        final RedisPartitionAttributesBuilder partitionAttributesBuilder = partitionAttributesBuilderFunction
                    .apply(RedisProtocolSupport.Command.GEOADD);
        partitionAttributesBuilder.addKey(key);
        final Single<Long> result = partitionedRedisClient.request(partitionAttributesBuilder.build(), request,
                    Long.class);
        return result;
    }

    @Override
    public Single<Long> geoadd(@RedisProtocolSupport.Key final Buffer key, final double longitude1,
                               final double latitude1, final Buffer member1, final double longitude2,
                               final double latitude2, final Buffer member2, final double longitude3,
                               final double latitude3, final Buffer member3) {
        requireNonNull(key);
        requireNonNull(member1);
        requireNonNull(member2);
        requireNonNull(member3);
        final int len = 11;
        final int capacity = calculateInitialCommandBufferSize(len, RedisProtocolSupport.Command.GEOADD) +
                    calculateRequestArgumentSize(key) + calculateRequestArgumentSize(longitude1) +
                    calculateRequestArgumentSize(latitude1) + calculateRequestArgumentSize(member1) +
                    calculateRequestArgumentSize(longitude2) + calculateRequestArgumentSize(latitude2) +
                    calculateRequestArgumentSize(member2) + calculateRequestArgumentSize(longitude3) +
                    calculateRequestArgumentSize(latitude3) + calculateRequestArgumentSize(member3);
        Buffer buffer = partitionedRedisClient.executionContext().bufferAllocator().newBuffer(capacity);
        writeRequestArraySize(buffer, len);
        RedisProtocolSupport.Command.GEOADD.encodeTo(buffer);
        writeRequestArgument(buffer, key);
        writeRequestArgument(buffer, longitude1);
        writeRequestArgument(buffer, latitude1);
        writeRequestArgument(buffer, member1);
        writeRequestArgument(buffer, longitude2);
        writeRequestArgument(buffer, latitude2);
        writeRequestArgument(buffer, member2);
        writeRequestArgument(buffer, longitude3);
        writeRequestArgument(buffer, latitude3);
        writeRequestArgument(buffer, member3);
        final RedisRequest request = newRequest(RedisProtocolSupport.Command.GEOADD, buffer);
        final RedisPartitionAttributesBuilder partitionAttributesBuilder = partitionAttributesBuilderFunction
                    .apply(RedisProtocolSupport.Command.GEOADD);
        partitionAttributesBuilder.addKey(key);
        final Single<Long> result = partitionedRedisClient.request(partitionAttributesBuilder.build(), request,
                    Long.class);
        return result;
    }

    @Override
    public Single<Long> geoadd(@RedisProtocolSupport.Key final Buffer key,
                               final Collection<RedisProtocolSupport.BufferLongitudeLatitudeMember> longitudeLatitudeMembers) {
        requireNonNull(key);
        requireNonNull(longitudeLatitudeMembers);
        final int len = 2 + RedisProtocolSupport.BufferLongitudeLatitudeMember.SIZE * longitudeLatitudeMembers.size();
        int longitudeLatitudeMembersCapacity = 0;
        if (longitudeLatitudeMembers instanceof List && longitudeLatitudeMembers instanceof RandomAccess) {
            final List<RedisProtocolSupport.BufferLongitudeLatitudeMember> list = (List<RedisProtocolSupport.BufferLongitudeLatitudeMember>) longitudeLatitudeMembers;
            for (int i = 0; i < list.size(); ++i) {
                final RedisProtocolSupport.BufferLongitudeLatitudeMember arg = list.get(i);
                longitudeLatitudeMembersCapacity += arg.encodedByteCount();
            }
        } else {
            for (RedisProtocolSupport.BufferLongitudeLatitudeMember arg : longitudeLatitudeMembers) {
                longitudeLatitudeMembersCapacity += arg.encodedByteCount();
            }
        }
        final int capacity = calculateInitialCommandBufferSize(len, RedisProtocolSupport.Command.GEOADD) +
                    calculateRequestArgumentSize(key) + longitudeLatitudeMembersCapacity;
        Buffer buffer = partitionedRedisClient.executionContext().bufferAllocator().newBuffer(capacity);
        writeRequestArraySize(buffer, len);
        RedisProtocolSupport.Command.GEOADD.encodeTo(buffer);
        writeRequestArgument(buffer, key);
        if (longitudeLatitudeMembers instanceof List && longitudeLatitudeMembers instanceof RandomAccess) {
            final List<RedisProtocolSupport.BufferLongitudeLatitudeMember> list = (List<RedisProtocolSupport.BufferLongitudeLatitudeMember>) longitudeLatitudeMembers;
            for (int i = 0; i < list.size(); ++i) {
                final RedisProtocolSupport.BufferLongitudeLatitudeMember arg = list.get(i);
                arg.encodeTo(buffer);
            }
        } else {
            for (RedisProtocolSupport.BufferLongitudeLatitudeMember arg : longitudeLatitudeMembers) {
                arg.encodeTo(buffer);
            }
        }
        final RedisRequest request = newRequest(RedisProtocolSupport.Command.GEOADD, buffer);
        final RedisPartitionAttributesBuilder partitionAttributesBuilder = partitionAttributesBuilderFunction
                    .apply(RedisProtocolSupport.Command.GEOADD);
        partitionAttributesBuilder.addKey(key);
        final Single<Long> result = partitionedRedisClient.request(partitionAttributesBuilder.build(), request,
                    Long.class);
        return result;
    }

    @Override
    public Single<Double> geodist(@RedisProtocolSupport.Key final Buffer key, final Buffer member1,
                                  final Buffer member2) {
        requireNonNull(key);
        requireNonNull(member1);
        requireNonNull(member2);
        final int len = 4;
        final int capacity = calculateInitialCommandBufferSize(len, RedisProtocolSupport.Command.GEODIST) +
                    calculateRequestArgumentSize(key) + calculateRequestArgumentSize(member1) +
                    calculateRequestArgumentSize(member2);
        Buffer buffer = partitionedRedisClient.executionContext().bufferAllocator().newBuffer(capacity);
        writeRequestArraySize(buffer, len);
        RedisProtocolSupport.Command.GEODIST.encodeTo(buffer);
        writeRequestArgument(buffer, key);
        writeRequestArgument(buffer, member1);
        writeRequestArgument(buffer, member2);
        final RedisRequest request = newRequest(RedisProtocolSupport.Command.GEODIST, buffer);
        final RedisPartitionAttributesBuilder partitionAttributesBuilder = partitionAttributesBuilderFunction
                    .apply(RedisProtocolSupport.Command.GEODIST);
        partitionAttributesBuilder.addKey(key);
        final Single<Double> result = partitionedRedisClient
                    .request(partitionAttributesBuilder.build(), request, String.class)
                    .map(s -> s != null ? Double.valueOf(s) : null);
        return result;
    }

    @Override
    public Single<Double> geodist(@RedisProtocolSupport.Key final Buffer key, final Buffer member1,
                                  final Buffer member2, @Nullable final Buffer unit) {
        requireNonNull(key);
        requireNonNull(member1);
        requireNonNull(member2);
        final int len = 4 + (unit == null ? 0 : 1);
        final int capacity = calculateInitialCommandBufferSize(len, RedisProtocolSupport.Command.GEODIST) +
                    calculateRequestArgumentSize(key) + calculateRequestArgumentSize(member1) +
                    calculateRequestArgumentSize(member2) + (unit == null ? 0 : calculateRequestArgumentSize(unit));
        Buffer buffer = partitionedRedisClient.executionContext().bufferAllocator().newBuffer(capacity);
        writeRequestArraySize(buffer, len);
        RedisProtocolSupport.Command.GEODIST.encodeTo(buffer);
        writeRequestArgument(buffer, key);
        writeRequestArgument(buffer, member1);
        writeRequestArgument(buffer, member2);
        if (unit != null) {
            writeRequestArgument(buffer, unit);
        }
        final RedisRequest request = newRequest(RedisProtocolSupport.Command.GEODIST, buffer);
        final RedisPartitionAttributesBuilder partitionAttributesBuilder = partitionAttributesBuilderFunction
                    .apply(RedisProtocolSupport.Command.GEODIST);
        partitionAttributesBuilder.addKey(key);
        final Single<Double> result = partitionedRedisClient
                    .request(partitionAttributesBuilder.build(), request, String.class)
                    .map(s -> s != null ? Double.valueOf(s) : null);
        return result;
    }

    @Override
    public <T> Single<List<T>> geohash(@RedisProtocolSupport.Key final Buffer key, final Buffer member) {
        requireNonNull(key);
        requireNonNull(member);
        final int len = 3;
        final int capacity = calculateInitialCommandBufferSize(len, RedisProtocolSupport.Command.GEOHASH) +
                    calculateRequestArgumentSize(key) + calculateRequestArgumentSize(member);
        Buffer buffer = partitionedRedisClient.executionContext().bufferAllocator().newBuffer(capacity);
        writeRequestArraySize(buffer, len);
        RedisProtocolSupport.Command.GEOHASH.encodeTo(buffer);
        writeRequestArgument(buffer, key);
        writeRequestArgument(buffer, member);
        final RedisRequest request = newRequest(RedisProtocolSupport.Command.GEOHASH, buffer);
        final RedisPartitionAttributesBuilder partitionAttributesBuilder = partitionAttributesBuilderFunction
                    .apply(RedisProtocolSupport.Command.GEOHASH);
        partitionAttributesBuilder.addKey(key);
        final Single<List<T>> result = (Single) partitionedRedisClient.request(partitionAttributesBuilder.build(),
                    request, List.class);
        return result;
    }

    @Override
    public <T> Single<List<T>> geohash(@RedisProtocolSupport.Key final Buffer key, final Buffer member1,
                                       final Buffer member2) {
        requireNonNull(key);
        requireNonNull(member1);
        requireNonNull(member2);
        final int len = 4;
        final int capacity = calculateInitialCommandBufferSize(len, RedisProtocolSupport.Command.GEOHASH) +
                    calculateRequestArgumentSize(key) + calculateRequestArgumentSize(member1) +
                    calculateRequestArgumentSize(member2);
        Buffer buffer = partitionedRedisClient.executionContext().bufferAllocator().newBuffer(capacity);
        writeRequestArraySize(buffer, len);
        RedisProtocolSupport.Command.GEOHASH.encodeTo(buffer);
        writeRequestArgument(buffer, key);
        writeRequestArgument(buffer, member1);
        writeRequestArgument(buffer, member2);
        final RedisRequest request = newRequest(RedisProtocolSupport.Command.GEOHASH, buffer);
        final RedisPartitionAttributesBuilder partitionAttributesBuilder = partitionAttributesBuilderFunction
                    .apply(RedisProtocolSupport.Command.GEOHASH);
        partitionAttributesBuilder.addKey(key);
        final Single<List<T>> result = (Single) partitionedRedisClient.request(partitionAttributesBuilder.build(),
                    request, List.class);
        return result;
    }

    @Override
    public <T> Single<List<T>> geohash(@RedisProtocolSupport.Key final Buffer key, final Buffer member1,
                                       final Buffer member2, final Buffer member3) {
        requireNonNull(key);
        requireNonNull(member1);
        requireNonNull(member2);
        requireNonNull(member3);
        final int len = 5;
        final int capacity = calculateInitialCommandBufferSize(len, RedisProtocolSupport.Command.GEOHASH) +
                    calculateRequestArgumentSize(key) + calculateRequestArgumentSize(member1) +
                    calculateRequestArgumentSize(member2) + calculateRequestArgumentSize(member3);
        Buffer buffer = partitionedRedisClient.executionContext().bufferAllocator().newBuffer(capacity);
        writeRequestArraySize(buffer, len);
        RedisProtocolSupport.Command.GEOHASH.encodeTo(buffer);
        writeRequestArgument(buffer, key);
        writeRequestArgument(buffer, member1);
        writeRequestArgument(buffer, member2);
        writeRequestArgument(buffer, member3);
        final RedisRequest request = newRequest(RedisProtocolSupport.Command.GEOHASH, buffer);
        final RedisPartitionAttributesBuilder partitionAttributesBuilder = partitionAttributesBuilderFunction
                    .apply(RedisProtocolSupport.Command.GEOHASH);
        partitionAttributesBuilder.addKey(key);
        final Single<List<T>> result = (Single) partitionedRedisClient.request(partitionAttributesBuilder.build(),
                    request, List.class);
        return result;
    }

    @Override
    public <T> Single<List<T>> geohash(@RedisProtocolSupport.Key final Buffer key, final Collection<Buffer> members) {
        requireNonNull(key);
        requireNonNull(members);
        final int len = 2 + members.size();
        int membersCapacity = 0;
        if (members instanceof List && members instanceof RandomAccess) {
            final List<Buffer> list = (List<Buffer>) members;
            for (int i = 0; i < list.size(); ++i) {
                final Buffer arg = list.get(i);
                membersCapacity += calculateRequestArgumentSize(arg);
            }
        } else {
            for (Buffer arg : members) {
                membersCapacity += calculateRequestArgumentSize(arg);
            }
        }
        final int capacity = calculateInitialCommandBufferSize(len, RedisProtocolSupport.Command.GEOHASH) +
                    calculateRequestArgumentSize(key) + membersCapacity;
        Buffer buffer = partitionedRedisClient.executionContext().bufferAllocator().newBuffer(capacity);
        writeRequestArraySize(buffer, len);
        RedisProtocolSupport.Command.GEOHASH.encodeTo(buffer);
        writeRequestArgument(buffer, key);
        if (members instanceof List && members instanceof RandomAccess) {
            final List<Buffer> list = (List<Buffer>) members;
            for (int i = 0; i < list.size(); ++i) {
                final Buffer arg = list.get(i);
                writeRequestArgument(buffer, arg);
            }
        } else {
            for (Buffer arg : members) {
                writeRequestArgument(buffer, arg);
            }
        }
        final RedisRequest request = newRequest(RedisProtocolSupport.Command.GEOHASH, buffer);
        final RedisPartitionAttributesBuilder partitionAttributesBuilder = partitionAttributesBuilderFunction
                    .apply(RedisProtocolSupport.Command.GEOHASH);
        partitionAttributesBuilder.addKey(key);
        final Single<List<T>> result = (Single) partitionedRedisClient.request(partitionAttributesBuilder.build(),
                    request, List.class);
        return result;
    }

    @Override
    public <T> Single<List<T>> geopos(@RedisProtocolSupport.Key final Buffer key, final Buffer member) {
        requireNonNull(key);
        requireNonNull(member);
        final int len = 3;
        final int capacity = calculateInitialCommandBufferSize(len, RedisProtocolSupport.Command.GEOPOS) +
                    calculateRequestArgumentSize(key) + calculateRequestArgumentSize(member);
        Buffer buffer = partitionedRedisClient.executionContext().bufferAllocator().newBuffer(capacity);
        writeRequestArraySize(buffer, len);
        RedisProtocolSupport.Command.GEOPOS.encodeTo(buffer);
        writeRequestArgument(buffer, key);
        writeRequestArgument(buffer, member);
        final RedisRequest request = newRequest(RedisProtocolSupport.Command.GEOPOS, buffer);
        final RedisPartitionAttributesBuilder partitionAttributesBuilder = partitionAttributesBuilderFunction
                    .apply(RedisProtocolSupport.Command.GEOPOS);
        partitionAttributesBuilder.addKey(key);
        final Single<List<T>> result = (Single) partitionedRedisClient.request(partitionAttributesBuilder.build(),
                    request, List.class);
        return result;
    }

    @Override
    public <T> Single<List<T>> geopos(@RedisProtocolSupport.Key final Buffer key, final Buffer member1,
                                      final Buffer member2) {
        requireNonNull(key);
        requireNonNull(member1);
        requireNonNull(member2);
        final int len = 4;
        final int capacity = calculateInitialCommandBufferSize(len, RedisProtocolSupport.Command.GEOPOS) +
                    calculateRequestArgumentSize(key) + calculateRequestArgumentSize(member1) +
                    calculateRequestArgumentSize(member2);
        Buffer buffer = partitionedRedisClient.executionContext().bufferAllocator().newBuffer(capacity);
        writeRequestArraySize(buffer, len);
        RedisProtocolSupport.Command.GEOPOS.encodeTo(buffer);
        writeRequestArgument(buffer, key);
        writeRequestArgument(buffer, member1);
        writeRequestArgument(buffer, member2);
        final RedisRequest request = newRequest(RedisProtocolSupport.Command.GEOPOS, buffer);
        final RedisPartitionAttributesBuilder partitionAttributesBuilder = partitionAttributesBuilderFunction
                    .apply(RedisProtocolSupport.Command.GEOPOS);
        partitionAttributesBuilder.addKey(key);
        final Single<List<T>> result = (Single) partitionedRedisClient.request(partitionAttributesBuilder.build(),
                    request, List.class);
        return result;
    }

    @Override
    public <T> Single<List<T>> geopos(@RedisProtocolSupport.Key final Buffer key, final Buffer member1,
                                      final Buffer member2, final Buffer member3) {
        requireNonNull(key);
        requireNonNull(member1);
        requireNonNull(member2);
        requireNonNull(member3);
        final int len = 5;
        final int capacity = calculateInitialCommandBufferSize(len, RedisProtocolSupport.Command.GEOPOS) +
                    calculateRequestArgumentSize(key) + calculateRequestArgumentSize(member1) +
                    calculateRequestArgumentSize(member2) + calculateRequestArgumentSize(member3);
        Buffer buffer = partitionedRedisClient.executionContext().bufferAllocator().newBuffer(capacity);
        writeRequestArraySize(buffer, len);
        RedisProtocolSupport.Command.GEOPOS.encodeTo(buffer);
        writeRequestArgument(buffer, key);
        writeRequestArgument(buffer, member1);
        writeRequestArgument(buffer, member2);
        writeRequestArgument(buffer, member3);
        final RedisRequest request = newRequest(RedisProtocolSupport.Command.GEOPOS, buffer);
        final RedisPartitionAttributesBuilder partitionAttributesBuilder = partitionAttributesBuilderFunction
                    .apply(RedisProtocolSupport.Command.GEOPOS);
        partitionAttributesBuilder.addKey(key);
        final Single<List<T>> result = (Single) partitionedRedisClient.request(partitionAttributesBuilder.build(),
                    request, List.class);
        return result;
    }

    @Override
    public <T> Single<List<T>> geopos(@RedisProtocolSupport.Key final Buffer key, final Collection<Buffer> members) {
        requireNonNull(key);
        requireNonNull(members);
        final int len = 2 + members.size();
        int membersCapacity = 0;
        if (members instanceof List && members instanceof RandomAccess) {
            final List<Buffer> list = (List<Buffer>) members;
            for (int i = 0; i < list.size(); ++i) {
                final Buffer arg = list.get(i);
                membersCapacity += calculateRequestArgumentSize(arg);
            }
        } else {
            for (Buffer arg : members) {
                membersCapacity += calculateRequestArgumentSize(arg);
            }
        }
        final int capacity = calculateInitialCommandBufferSize(len, RedisProtocolSupport.Command.GEOPOS) +
                    calculateRequestArgumentSize(key) + membersCapacity;
        Buffer buffer = partitionedRedisClient.executionContext().bufferAllocator().newBuffer(capacity);
        writeRequestArraySize(buffer, len);
        RedisProtocolSupport.Command.GEOPOS.encodeTo(buffer);
        writeRequestArgument(buffer, key);
        if (members instanceof List && members instanceof RandomAccess) {
            final List<Buffer> list = (List<Buffer>) members;
            for (int i = 0; i < list.size(); ++i) {
                final Buffer arg = list.get(i);
                writeRequestArgument(buffer, arg);
            }
        } else {
            for (Buffer arg : members) {
                writeRequestArgument(buffer, arg);
            }
        }
        final RedisRequest request = newRequest(RedisProtocolSupport.Command.GEOPOS, buffer);
        final RedisPartitionAttributesBuilder partitionAttributesBuilder = partitionAttributesBuilderFunction
                    .apply(RedisProtocolSupport.Command.GEOPOS);
        partitionAttributesBuilder.addKey(key);
        final Single<List<T>> result = (Single) partitionedRedisClient.request(partitionAttributesBuilder.build(),
                    request, List.class);
        return result;
    }

    @Override
    public <T> Single<List<T>> georadius(@RedisProtocolSupport.Key final Buffer key, final double longitude,
                                         final double latitude, final double radius,
                                         final RedisProtocolSupport.GeoradiusUnit unit) {
        requireNonNull(key);
        requireNonNull(unit);
        final int len = 6;
        final int capacity = calculateInitialCommandBufferSize(len, RedisProtocolSupport.Command.GEORADIUS) +
                    calculateRequestArgumentSize(key) + calculateRequestArgumentSize(longitude) +
                    calculateRequestArgumentSize(latitude) + calculateRequestArgumentSize(radius) +
                    unit.encodedByteCount();
        Buffer buffer = partitionedRedisClient.executionContext().bufferAllocator().newBuffer(capacity);
        writeRequestArraySize(buffer, len);
        RedisProtocolSupport.Command.GEORADIUS.encodeTo(buffer);
        writeRequestArgument(buffer, key);
        writeRequestArgument(buffer, longitude);
        writeRequestArgument(buffer, latitude);
        writeRequestArgument(buffer, radius);
        unit.encodeTo(buffer);
        final RedisRequest request = newRequest(RedisProtocolSupport.Command.GEORADIUS, buffer);
        final RedisPartitionAttributesBuilder partitionAttributesBuilder = partitionAttributesBuilderFunction
                    .apply(RedisProtocolSupport.Command.GEORADIUS);
        partitionAttributesBuilder.addKey(key);
        final Single<List<T>> result = (Single) partitionedRedisClient.request(partitionAttributesBuilder.build(),
                    request, List.class);
        return result;
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
        requireNonNull(key);
        requireNonNull(unit);
        final int len = 6 + (withcoord == null ? 0 : 1) + (withdist == null ? 0 : 1) + (withhash == null ? 0 : 1) +
                    (count == null ? 0 : 2) + (order == null ? 0 : 1) + (storeKey == null ? 0 : 2) +
                    (storedistKey == null ? 0 : 2);
        final int capacity = calculateInitialCommandBufferSize(len, RedisProtocolSupport.Command.GEORADIUS) +
                    calculateRequestArgumentSize(key) + calculateRequestArgumentSize(longitude) +
                    calculateRequestArgumentSize(latitude) + calculateRequestArgumentSize(radius) +
                    unit.encodedByteCount() + (withcoord == null ? 0 : withcoord.encodedByteCount()) +
                    (withdist == null ? 0 : withdist.encodedByteCount()) +
                    (withhash == null ? 0 : withhash.encodedByteCount()) +
                    (count == null ? 0 : RedisProtocolSupport.SubCommand.COUNT.encodedByteCount()) +
                    (count == null ? 0 : calculateRequestArgumentSize(count)) +
                    (order == null ? 0 : order.encodedByteCount()) +
                    (storeKey == null ? 0 : RedisProtocolSupport.SubCommand.STORE.encodedByteCount()) +
                    (storeKey == null ? 0 : calculateRequestArgumentSize(storeKey)) +
                    (storedistKey == null ? 0 : RedisProtocolSupport.SubCommand.STOREDIST.encodedByteCount()) +
                    (storedistKey == null ? 0 : calculateRequestArgumentSize(storedistKey));
        Buffer buffer = partitionedRedisClient.executionContext().bufferAllocator().newBuffer(capacity);
        writeRequestArraySize(buffer, len);
        RedisProtocolSupport.Command.GEORADIUS.encodeTo(buffer);
        writeRequestArgument(buffer, key);
        writeRequestArgument(buffer, longitude);
        writeRequestArgument(buffer, latitude);
        writeRequestArgument(buffer, radius);
        unit.encodeTo(buffer);
        if (withcoord != null) {
            withcoord.encodeTo(buffer);
        }
        if (withdist != null) {
            withdist.encodeTo(buffer);
        }
        if (withhash != null) {
            withhash.encodeTo(buffer);
        }
        if (count != null) {
            RedisProtocolSupport.SubCommand.COUNT.encodeTo(buffer);
            writeRequestArgument(buffer, count);
        }
        if (order != null) {
            order.encodeTo(buffer);
        }
        if (storeKey != null) {
            RedisProtocolSupport.SubCommand.STORE.encodeTo(buffer);
            writeRequestArgument(buffer, storeKey);
        }
        if (storedistKey != null) {
            RedisProtocolSupport.SubCommand.STOREDIST.encodeTo(buffer);
            writeRequestArgument(buffer, storedistKey);
        }
        final RedisRequest request = newRequest(RedisProtocolSupport.Command.GEORADIUS, buffer);
        final RedisPartitionAttributesBuilder partitionAttributesBuilder = partitionAttributesBuilderFunction
                    .apply(RedisProtocolSupport.Command.GEORADIUS);
        partitionAttributesBuilder.addKey(key);
        if (storeKey != null) {
            partitionAttributesBuilder.addKey(storeKey);
        }
        if (storedistKey != null) {
            partitionAttributesBuilder.addKey(storedistKey);
        }
        final Single<List<T>> result = (Single) partitionedRedisClient.request(partitionAttributesBuilder.build(),
                    request, List.class);
        return result;
    }

    @Override
    public <T> Single<List<T>> georadiusbymember(@RedisProtocolSupport.Key final Buffer key, final Buffer member,
                                                 final double radius,
                                                 final RedisProtocolSupport.GeoradiusbymemberUnit unit) {
        requireNonNull(key);
        requireNonNull(member);
        requireNonNull(unit);
        final int len = 5;
        final int capacity = calculateInitialCommandBufferSize(len, RedisProtocolSupport.Command.GEORADIUSBYMEMBER) +
                    calculateRequestArgumentSize(key) + calculateRequestArgumentSize(member) +
                    calculateRequestArgumentSize(radius) + unit.encodedByteCount();
        Buffer buffer = partitionedRedisClient.executionContext().bufferAllocator().newBuffer(capacity);
        writeRequestArraySize(buffer, len);
        RedisProtocolSupport.Command.GEORADIUSBYMEMBER.encodeTo(buffer);
        writeRequestArgument(buffer, key);
        writeRequestArgument(buffer, member);
        writeRequestArgument(buffer, radius);
        unit.encodeTo(buffer);
        final RedisRequest request = newRequest(RedisProtocolSupport.Command.GEORADIUSBYMEMBER, buffer);
        final RedisPartitionAttributesBuilder partitionAttributesBuilder = partitionAttributesBuilderFunction
                    .apply(RedisProtocolSupport.Command.GEORADIUSBYMEMBER);
        partitionAttributesBuilder.addKey(key);
        final Single<List<T>> result = (Single) partitionedRedisClient.request(partitionAttributesBuilder.build(),
                    request, List.class);
        return result;
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
        requireNonNull(key);
        requireNonNull(member);
        requireNonNull(unit);
        final int len = 5 + (withcoord == null ? 0 : 1) + (withdist == null ? 0 : 1) + (withhash == null ? 0 : 1) +
                    (count == null ? 0 : 2) + (order == null ? 0 : 1) + (storeKey == null ? 0 : 2) +
                    (storedistKey == null ? 0 : 2);
        final int capacity = calculateInitialCommandBufferSize(len, RedisProtocolSupport.Command.GEORADIUSBYMEMBER) +
                    calculateRequestArgumentSize(key) + calculateRequestArgumentSize(member) +
                    calculateRequestArgumentSize(radius) + unit.encodedByteCount() +
                    (withcoord == null ? 0 : withcoord.encodedByteCount()) +
                    (withdist == null ? 0 : withdist.encodedByteCount()) +
                    (withhash == null ? 0 : withhash.encodedByteCount()) +
                    (count == null ? 0 : RedisProtocolSupport.SubCommand.COUNT.encodedByteCount()) +
                    (count == null ? 0 : calculateRequestArgumentSize(count)) +
                    (order == null ? 0 : order.encodedByteCount()) +
                    (storeKey == null ? 0 : RedisProtocolSupport.SubCommand.STORE.encodedByteCount()) +
                    (storeKey == null ? 0 : calculateRequestArgumentSize(storeKey)) +
                    (storedistKey == null ? 0 : RedisProtocolSupport.SubCommand.STOREDIST.encodedByteCount()) +
                    (storedistKey == null ? 0 : calculateRequestArgumentSize(storedistKey));
        Buffer buffer = partitionedRedisClient.executionContext().bufferAllocator().newBuffer(capacity);
        writeRequestArraySize(buffer, len);
        RedisProtocolSupport.Command.GEORADIUSBYMEMBER.encodeTo(buffer);
        writeRequestArgument(buffer, key);
        writeRequestArgument(buffer, member);
        writeRequestArgument(buffer, radius);
        unit.encodeTo(buffer);
        if (withcoord != null) {
            withcoord.encodeTo(buffer);
        }
        if (withdist != null) {
            withdist.encodeTo(buffer);
        }
        if (withhash != null) {
            withhash.encodeTo(buffer);
        }
        if (count != null) {
            RedisProtocolSupport.SubCommand.COUNT.encodeTo(buffer);
            writeRequestArgument(buffer, count);
        }
        if (order != null) {
            order.encodeTo(buffer);
        }
        if (storeKey != null) {
            RedisProtocolSupport.SubCommand.STORE.encodeTo(buffer);
            writeRequestArgument(buffer, storeKey);
        }
        if (storedistKey != null) {
            RedisProtocolSupport.SubCommand.STOREDIST.encodeTo(buffer);
            writeRequestArgument(buffer, storedistKey);
        }
        final RedisRequest request = newRequest(RedisProtocolSupport.Command.GEORADIUSBYMEMBER, buffer);
        final RedisPartitionAttributesBuilder partitionAttributesBuilder = partitionAttributesBuilderFunction
                    .apply(RedisProtocolSupport.Command.GEORADIUSBYMEMBER);
        partitionAttributesBuilder.addKey(key);
        if (storeKey != null) {
            partitionAttributesBuilder.addKey(storeKey);
        }
        if (storedistKey != null) {
            partitionAttributesBuilder.addKey(storedistKey);
        }
        final Single<List<T>> result = (Single) partitionedRedisClient.request(partitionAttributesBuilder.build(),
                    request, List.class);
        return result;
    }

    @Override
    public Single<Buffer> get(@RedisProtocolSupport.Key final Buffer key) {
        requireNonNull(key);
        final int len = 2;
        final int capacity = calculateInitialCommandBufferSize(len, RedisProtocolSupport.Command.GET) +
                    calculateRequestArgumentSize(key);
        Buffer buffer = partitionedRedisClient.executionContext().bufferAllocator().newBuffer(capacity);
        writeRequestArraySize(buffer, len);
        RedisProtocolSupport.Command.GET.encodeTo(buffer);
        writeRequestArgument(buffer, key);
        final RedisRequest request = newRequest(RedisProtocolSupport.Command.GET, buffer);
        final RedisPartitionAttributesBuilder partitionAttributesBuilder = partitionAttributesBuilderFunction
                    .apply(RedisProtocolSupport.Command.GET);
        partitionAttributesBuilder.addKey(key);
        final Single<Buffer> result = partitionedRedisClient.request(partitionAttributesBuilder.build(), request,
                    Buffer.class);
        return result;
    }

    @Override
    public Single<Long> getbit(@RedisProtocolSupport.Key final Buffer key, final long offset) {
        requireNonNull(key);
        final int len = 3;
        final int capacity = calculateInitialCommandBufferSize(len, RedisProtocolSupport.Command.GETBIT) +
                    calculateRequestArgumentSize(key) + calculateRequestArgumentSize(offset);
        Buffer buffer = partitionedRedisClient.executionContext().bufferAllocator().newBuffer(capacity);
        writeRequestArraySize(buffer, len);
        RedisProtocolSupport.Command.GETBIT.encodeTo(buffer);
        writeRequestArgument(buffer, key);
        writeRequestArgument(buffer, offset);
        final RedisRequest request = newRequest(RedisProtocolSupport.Command.GETBIT, buffer);
        final RedisPartitionAttributesBuilder partitionAttributesBuilder = partitionAttributesBuilderFunction
                    .apply(RedisProtocolSupport.Command.GETBIT);
        partitionAttributesBuilder.addKey(key);
        final Single<Long> result = partitionedRedisClient.request(partitionAttributesBuilder.build(), request,
                    Long.class);
        return result;
    }

    @Override
    public Single<Buffer> getrange(@RedisProtocolSupport.Key final Buffer key, final long start, final long end) {
        requireNonNull(key);
        final int len = 4;
        final int capacity = calculateInitialCommandBufferSize(len, RedisProtocolSupport.Command.GETRANGE) +
                    calculateRequestArgumentSize(key) + calculateRequestArgumentSize(start) +
                    calculateRequestArgumentSize(end);
        Buffer buffer = partitionedRedisClient.executionContext().bufferAllocator().newBuffer(capacity);
        writeRequestArraySize(buffer, len);
        RedisProtocolSupport.Command.GETRANGE.encodeTo(buffer);
        writeRequestArgument(buffer, key);
        writeRequestArgument(buffer, start);
        writeRequestArgument(buffer, end);
        final RedisRequest request = newRequest(RedisProtocolSupport.Command.GETRANGE, buffer);
        final RedisPartitionAttributesBuilder partitionAttributesBuilder = partitionAttributesBuilderFunction
                    .apply(RedisProtocolSupport.Command.GETRANGE);
        partitionAttributesBuilder.addKey(key);
        final Single<Buffer> result = partitionedRedisClient.request(partitionAttributesBuilder.build(), request,
                    Buffer.class);
        return result;
    }

    @Override
    public Single<Buffer> getset(@RedisProtocolSupport.Key final Buffer key, final Buffer value) {
        requireNonNull(key);
        requireNonNull(value);
        final int len = 3;
        final int capacity = calculateInitialCommandBufferSize(len, RedisProtocolSupport.Command.GETSET) +
                    calculateRequestArgumentSize(key) + calculateRequestArgumentSize(value);
        Buffer buffer = partitionedRedisClient.executionContext().bufferAllocator().newBuffer(capacity);
        writeRequestArraySize(buffer, len);
        RedisProtocolSupport.Command.GETSET.encodeTo(buffer);
        writeRequestArgument(buffer, key);
        writeRequestArgument(buffer, value);
        final RedisRequest request = newRequest(RedisProtocolSupport.Command.GETSET, buffer);
        final RedisPartitionAttributesBuilder partitionAttributesBuilder = partitionAttributesBuilderFunction
                    .apply(RedisProtocolSupport.Command.GETSET);
        partitionAttributesBuilder.addKey(key);
        final Single<Buffer> result = partitionedRedisClient.request(partitionAttributesBuilder.build(), request,
                    Buffer.class);
        return result;
    }

    @Override
    public Single<Long> hdel(@RedisProtocolSupport.Key final Buffer key, final Buffer field) {
        requireNonNull(key);
        requireNonNull(field);
        final int len = 3;
        final int capacity = calculateInitialCommandBufferSize(len, RedisProtocolSupport.Command.HDEL) +
                    calculateRequestArgumentSize(key) + calculateRequestArgumentSize(field);
        Buffer buffer = partitionedRedisClient.executionContext().bufferAllocator().newBuffer(capacity);
        writeRequestArraySize(buffer, len);
        RedisProtocolSupport.Command.HDEL.encodeTo(buffer);
        writeRequestArgument(buffer, key);
        writeRequestArgument(buffer, field);
        final RedisRequest request = newRequest(RedisProtocolSupport.Command.HDEL, buffer);
        final RedisPartitionAttributesBuilder partitionAttributesBuilder = partitionAttributesBuilderFunction
                    .apply(RedisProtocolSupport.Command.HDEL);
        partitionAttributesBuilder.addKey(key);
        final Single<Long> result = partitionedRedisClient.request(partitionAttributesBuilder.build(), request,
                    Long.class);
        return result;
    }

    @Override
    public Single<Long> hdel(@RedisProtocolSupport.Key final Buffer key, final Buffer field1, final Buffer field2) {
        requireNonNull(key);
        requireNonNull(field1);
        requireNonNull(field2);
        final int len = 4;
        final int capacity = calculateInitialCommandBufferSize(len, RedisProtocolSupport.Command.HDEL) +
                    calculateRequestArgumentSize(key) + calculateRequestArgumentSize(field1) +
                    calculateRequestArgumentSize(field2);
        Buffer buffer = partitionedRedisClient.executionContext().bufferAllocator().newBuffer(capacity);
        writeRequestArraySize(buffer, len);
        RedisProtocolSupport.Command.HDEL.encodeTo(buffer);
        writeRequestArgument(buffer, key);
        writeRequestArgument(buffer, field1);
        writeRequestArgument(buffer, field2);
        final RedisRequest request = newRequest(RedisProtocolSupport.Command.HDEL, buffer);
        final RedisPartitionAttributesBuilder partitionAttributesBuilder = partitionAttributesBuilderFunction
                    .apply(RedisProtocolSupport.Command.HDEL);
        partitionAttributesBuilder.addKey(key);
        final Single<Long> result = partitionedRedisClient.request(partitionAttributesBuilder.build(), request,
                    Long.class);
        return result;
    }

    @Override
    public Single<Long> hdel(@RedisProtocolSupport.Key final Buffer key, final Buffer field1, final Buffer field2,
                             final Buffer field3) {
        requireNonNull(key);
        requireNonNull(field1);
        requireNonNull(field2);
        requireNonNull(field3);
        final int len = 5;
        final int capacity = calculateInitialCommandBufferSize(len, RedisProtocolSupport.Command.HDEL) +
                    calculateRequestArgumentSize(key) + calculateRequestArgumentSize(field1) +
                    calculateRequestArgumentSize(field2) + calculateRequestArgumentSize(field3);
        Buffer buffer = partitionedRedisClient.executionContext().bufferAllocator().newBuffer(capacity);
        writeRequestArraySize(buffer, len);
        RedisProtocolSupport.Command.HDEL.encodeTo(buffer);
        writeRequestArgument(buffer, key);
        writeRequestArgument(buffer, field1);
        writeRequestArgument(buffer, field2);
        writeRequestArgument(buffer, field3);
        final RedisRequest request = newRequest(RedisProtocolSupport.Command.HDEL, buffer);
        final RedisPartitionAttributesBuilder partitionAttributesBuilder = partitionAttributesBuilderFunction
                    .apply(RedisProtocolSupport.Command.HDEL);
        partitionAttributesBuilder.addKey(key);
        final Single<Long> result = partitionedRedisClient.request(partitionAttributesBuilder.build(), request,
                    Long.class);
        return result;
    }

    @Override
    public Single<Long> hdel(@RedisProtocolSupport.Key final Buffer key, final Collection<Buffer> fields) {
        requireNonNull(key);
        requireNonNull(fields);
        final int len = 2 + fields.size();
        int fieldsCapacity = 0;
        if (fields instanceof List && fields instanceof RandomAccess) {
            final List<Buffer> list = (List<Buffer>) fields;
            for (int i = 0; i < list.size(); ++i) {
                final Buffer arg = list.get(i);
                fieldsCapacity += calculateRequestArgumentSize(arg);
            }
        } else {
            for (Buffer arg : fields) {
                fieldsCapacity += calculateRequestArgumentSize(arg);
            }
        }
        final int capacity = calculateInitialCommandBufferSize(len, RedisProtocolSupport.Command.HDEL) +
                    calculateRequestArgumentSize(key) + fieldsCapacity;
        Buffer buffer = partitionedRedisClient.executionContext().bufferAllocator().newBuffer(capacity);
        writeRequestArraySize(buffer, len);
        RedisProtocolSupport.Command.HDEL.encodeTo(buffer);
        writeRequestArgument(buffer, key);
        if (fields instanceof List && fields instanceof RandomAccess) {
            final List<Buffer> list = (List<Buffer>) fields;
            for (int i = 0; i < list.size(); ++i) {
                final Buffer arg = list.get(i);
                writeRequestArgument(buffer, arg);
            }
        } else {
            for (Buffer arg : fields) {
                writeRequestArgument(buffer, arg);
            }
        }
        final RedisRequest request = newRequest(RedisProtocolSupport.Command.HDEL, buffer);
        final RedisPartitionAttributesBuilder partitionAttributesBuilder = partitionAttributesBuilderFunction
                    .apply(RedisProtocolSupport.Command.HDEL);
        partitionAttributesBuilder.addKey(key);
        final Single<Long> result = partitionedRedisClient.request(partitionAttributesBuilder.build(), request,
                    Long.class);
        return result;
    }

    @Override
    public Single<Long> hexists(@RedisProtocolSupport.Key final Buffer key, final Buffer field) {
        requireNonNull(key);
        requireNonNull(field);
        final int len = 3;
        final int capacity = calculateInitialCommandBufferSize(len, RedisProtocolSupport.Command.HEXISTS) +
                    calculateRequestArgumentSize(key) + calculateRequestArgumentSize(field);
        Buffer buffer = partitionedRedisClient.executionContext().bufferAllocator().newBuffer(capacity);
        writeRequestArraySize(buffer, len);
        RedisProtocolSupport.Command.HEXISTS.encodeTo(buffer);
        writeRequestArgument(buffer, key);
        writeRequestArgument(buffer, field);
        final RedisRequest request = newRequest(RedisProtocolSupport.Command.HEXISTS, buffer);
        final RedisPartitionAttributesBuilder partitionAttributesBuilder = partitionAttributesBuilderFunction
                    .apply(RedisProtocolSupport.Command.HEXISTS);
        partitionAttributesBuilder.addKey(key);
        final Single<Long> result = partitionedRedisClient.request(partitionAttributesBuilder.build(), request,
                    Long.class);
        return result;
    }

    @Override
    public Single<Buffer> hget(@RedisProtocolSupport.Key final Buffer key, final Buffer field) {
        requireNonNull(key);
        requireNonNull(field);
        final int len = 3;
        final int capacity = calculateInitialCommandBufferSize(len, RedisProtocolSupport.Command.HGET) +
                    calculateRequestArgumentSize(key) + calculateRequestArgumentSize(field);
        Buffer buffer = partitionedRedisClient.executionContext().bufferAllocator().newBuffer(capacity);
        writeRequestArraySize(buffer, len);
        RedisProtocolSupport.Command.HGET.encodeTo(buffer);
        writeRequestArgument(buffer, key);
        writeRequestArgument(buffer, field);
        final RedisRequest request = newRequest(RedisProtocolSupport.Command.HGET, buffer);
        final RedisPartitionAttributesBuilder partitionAttributesBuilder = partitionAttributesBuilderFunction
                    .apply(RedisProtocolSupport.Command.HGET);
        partitionAttributesBuilder.addKey(key);
        final Single<Buffer> result = partitionedRedisClient.request(partitionAttributesBuilder.build(), request,
                    Buffer.class);
        return result;
    }

    @Override
    public <T> Single<List<T>> hgetall(@RedisProtocolSupport.Key final Buffer key) {
        requireNonNull(key);
        final int len = 2;
        final int capacity = calculateInitialCommandBufferSize(len, RedisProtocolSupport.Command.HGETALL) +
                    calculateRequestArgumentSize(key);
        Buffer buffer = partitionedRedisClient.executionContext().bufferAllocator().newBuffer(capacity);
        writeRequestArraySize(buffer, len);
        RedisProtocolSupport.Command.HGETALL.encodeTo(buffer);
        writeRequestArgument(buffer, key);
        final RedisRequest request = newRequest(RedisProtocolSupport.Command.HGETALL, buffer);
        final RedisPartitionAttributesBuilder partitionAttributesBuilder = partitionAttributesBuilderFunction
                    .apply(RedisProtocolSupport.Command.HGETALL);
        partitionAttributesBuilder.addKey(key);
        final Single<List<T>> result = (Single) partitionedRedisClient.request(partitionAttributesBuilder.build(),
                    request, List.class);
        return result;
    }

    @Override
    public Single<Long> hincrby(@RedisProtocolSupport.Key final Buffer key, final Buffer field, final long increment) {
        requireNonNull(key);
        requireNonNull(field);
        final int len = 4;
        final int capacity = calculateInitialCommandBufferSize(len, RedisProtocolSupport.Command.HINCRBY) +
                    calculateRequestArgumentSize(key) + calculateRequestArgumentSize(field) +
                    calculateRequestArgumentSize(increment);
        Buffer buffer = partitionedRedisClient.executionContext().bufferAllocator().newBuffer(capacity);
        writeRequestArraySize(buffer, len);
        RedisProtocolSupport.Command.HINCRBY.encodeTo(buffer);
        writeRequestArgument(buffer, key);
        writeRequestArgument(buffer, field);
        writeRequestArgument(buffer, increment);
        final RedisRequest request = newRequest(RedisProtocolSupport.Command.HINCRBY, buffer);
        final RedisPartitionAttributesBuilder partitionAttributesBuilder = partitionAttributesBuilderFunction
                    .apply(RedisProtocolSupport.Command.HINCRBY);
        partitionAttributesBuilder.addKey(key);
        final Single<Long> result = partitionedRedisClient.request(partitionAttributesBuilder.build(), request,
                    Long.class);
        return result;
    }

    @Override
    public Single<Double> hincrbyfloat(@RedisProtocolSupport.Key final Buffer key, final Buffer field,
                                       final double increment) {
        requireNonNull(key);
        requireNonNull(field);
        final int len = 4;
        final int capacity = calculateInitialCommandBufferSize(len, RedisProtocolSupport.Command.HINCRBYFLOAT) +
                    calculateRequestArgumentSize(key) + calculateRequestArgumentSize(field) +
                    calculateRequestArgumentSize(increment);
        Buffer buffer = partitionedRedisClient.executionContext().bufferAllocator().newBuffer(capacity);
        writeRequestArraySize(buffer, len);
        RedisProtocolSupport.Command.HINCRBYFLOAT.encodeTo(buffer);
        writeRequestArgument(buffer, key);
        writeRequestArgument(buffer, field);
        writeRequestArgument(buffer, increment);
        final RedisRequest request = newRequest(RedisProtocolSupport.Command.HINCRBYFLOAT, buffer);
        final RedisPartitionAttributesBuilder partitionAttributesBuilder = partitionAttributesBuilderFunction
                    .apply(RedisProtocolSupport.Command.HINCRBYFLOAT);
        partitionAttributesBuilder.addKey(key);
        final Single<Double> result = partitionedRedisClient
                    .request(partitionAttributesBuilder.build(), request, String.class)
                    .map(s -> s != null ? Double.valueOf(s) : null);
        return result;
    }

    @Override
    public <T> Single<List<T>> hkeys(@RedisProtocolSupport.Key final Buffer key) {
        requireNonNull(key);
        final int len = 2;
        final int capacity = calculateInitialCommandBufferSize(len, RedisProtocolSupport.Command.HKEYS) +
                    calculateRequestArgumentSize(key);
        Buffer buffer = partitionedRedisClient.executionContext().bufferAllocator().newBuffer(capacity);
        writeRequestArraySize(buffer, len);
        RedisProtocolSupport.Command.HKEYS.encodeTo(buffer);
        writeRequestArgument(buffer, key);
        final RedisRequest request = newRequest(RedisProtocolSupport.Command.HKEYS, buffer);
        final RedisPartitionAttributesBuilder partitionAttributesBuilder = partitionAttributesBuilderFunction
                    .apply(RedisProtocolSupport.Command.HKEYS);
        partitionAttributesBuilder.addKey(key);
        final Single<List<T>> result = (Single) partitionedRedisClient.request(partitionAttributesBuilder.build(),
                    request, List.class);
        return result;
    }

    @Override
    public Single<Long> hlen(@RedisProtocolSupport.Key final Buffer key) {
        requireNonNull(key);
        final int len = 2;
        final int capacity = calculateInitialCommandBufferSize(len, RedisProtocolSupport.Command.HLEN) +
                    calculateRequestArgumentSize(key);
        Buffer buffer = partitionedRedisClient.executionContext().bufferAllocator().newBuffer(capacity);
        writeRequestArraySize(buffer, len);
        RedisProtocolSupport.Command.HLEN.encodeTo(buffer);
        writeRequestArgument(buffer, key);
        final RedisRequest request = newRequest(RedisProtocolSupport.Command.HLEN, buffer);
        final RedisPartitionAttributesBuilder partitionAttributesBuilder = partitionAttributesBuilderFunction
                    .apply(RedisProtocolSupport.Command.HLEN);
        partitionAttributesBuilder.addKey(key);
        final Single<Long> result = partitionedRedisClient.request(partitionAttributesBuilder.build(), request,
                    Long.class);
        return result;
    }

    @Override
    public Single<List<Buffer>> hmget(@RedisProtocolSupport.Key final Buffer key, final Buffer field) {
        requireNonNull(key);
        requireNonNull(field);
        final int len = 3;
        final int capacity = calculateInitialCommandBufferSize(len, RedisProtocolSupport.Command.HMGET) +
                    calculateRequestArgumentSize(key) + calculateRequestArgumentSize(field);
        Buffer buffer = partitionedRedisClient.executionContext().bufferAllocator().newBuffer(capacity);
        writeRequestArraySize(buffer, len);
        RedisProtocolSupport.Command.HMGET.encodeTo(buffer);
        writeRequestArgument(buffer, key);
        writeRequestArgument(buffer, field);
        final RedisRequest request = newRequest(RedisProtocolSupport.Command.HMGET, buffer);
        final RedisPartitionAttributesBuilder partitionAttributesBuilder = partitionAttributesBuilderFunction
                    .apply(RedisProtocolSupport.Command.HMGET);
        partitionAttributesBuilder.addKey(key);
        final Single<List<Buffer>> result = (Single) partitionedRedisClient.request(partitionAttributesBuilder.build(),
                    request, List.class);
        return result;
    }

    @Override
    public Single<List<Buffer>> hmget(@RedisProtocolSupport.Key final Buffer key, final Buffer field1,
                                      final Buffer field2) {
        requireNonNull(key);
        requireNonNull(field1);
        requireNonNull(field2);
        final int len = 4;
        final int capacity = calculateInitialCommandBufferSize(len, RedisProtocolSupport.Command.HMGET) +
                    calculateRequestArgumentSize(key) + calculateRequestArgumentSize(field1) +
                    calculateRequestArgumentSize(field2);
        Buffer buffer = partitionedRedisClient.executionContext().bufferAllocator().newBuffer(capacity);
        writeRequestArraySize(buffer, len);
        RedisProtocolSupport.Command.HMGET.encodeTo(buffer);
        writeRequestArgument(buffer, key);
        writeRequestArgument(buffer, field1);
        writeRequestArgument(buffer, field2);
        final RedisRequest request = newRequest(RedisProtocolSupport.Command.HMGET, buffer);
        final RedisPartitionAttributesBuilder partitionAttributesBuilder = partitionAttributesBuilderFunction
                    .apply(RedisProtocolSupport.Command.HMGET);
        partitionAttributesBuilder.addKey(key);
        final Single<List<Buffer>> result = (Single) partitionedRedisClient.request(partitionAttributesBuilder.build(),
                    request, List.class);
        return result;
    }

    @Override
    public Single<List<Buffer>> hmget(@RedisProtocolSupport.Key final Buffer key, final Buffer field1,
                                      final Buffer field2, final Buffer field3) {
        requireNonNull(key);
        requireNonNull(field1);
        requireNonNull(field2);
        requireNonNull(field3);
        final int len = 5;
        final int capacity = calculateInitialCommandBufferSize(len, RedisProtocolSupport.Command.HMGET) +
                    calculateRequestArgumentSize(key) + calculateRequestArgumentSize(field1) +
                    calculateRequestArgumentSize(field2) + calculateRequestArgumentSize(field3);
        Buffer buffer = partitionedRedisClient.executionContext().bufferAllocator().newBuffer(capacity);
        writeRequestArraySize(buffer, len);
        RedisProtocolSupport.Command.HMGET.encodeTo(buffer);
        writeRequestArgument(buffer, key);
        writeRequestArgument(buffer, field1);
        writeRequestArgument(buffer, field2);
        writeRequestArgument(buffer, field3);
        final RedisRequest request = newRequest(RedisProtocolSupport.Command.HMGET, buffer);
        final RedisPartitionAttributesBuilder partitionAttributesBuilder = partitionAttributesBuilderFunction
                    .apply(RedisProtocolSupport.Command.HMGET);
        partitionAttributesBuilder.addKey(key);
        final Single<List<Buffer>> result = (Single) partitionedRedisClient.request(partitionAttributesBuilder.build(),
                    request, List.class);
        return result;
    }

    @Override
    public Single<List<Buffer>> hmget(@RedisProtocolSupport.Key final Buffer key, final Collection<Buffer> fields) {
        requireNonNull(key);
        requireNonNull(fields);
        final int len = 2 + fields.size();
        int fieldsCapacity = 0;
        if (fields instanceof List && fields instanceof RandomAccess) {
            final List<Buffer> list = (List<Buffer>) fields;
            for (int i = 0; i < list.size(); ++i) {
                final Buffer arg = list.get(i);
                fieldsCapacity += calculateRequestArgumentSize(arg);
            }
        } else {
            for (Buffer arg : fields) {
                fieldsCapacity += calculateRequestArgumentSize(arg);
            }
        }
        final int capacity = calculateInitialCommandBufferSize(len, RedisProtocolSupport.Command.HMGET) +
                    calculateRequestArgumentSize(key) + fieldsCapacity;
        Buffer buffer = partitionedRedisClient.executionContext().bufferAllocator().newBuffer(capacity);
        writeRequestArraySize(buffer, len);
        RedisProtocolSupport.Command.HMGET.encodeTo(buffer);
        writeRequestArgument(buffer, key);
        if (fields instanceof List && fields instanceof RandomAccess) {
            final List<Buffer> list = (List<Buffer>) fields;
            for (int i = 0; i < list.size(); ++i) {
                final Buffer arg = list.get(i);
                writeRequestArgument(buffer, arg);
            }
        } else {
            for (Buffer arg : fields) {
                writeRequestArgument(buffer, arg);
            }
        }
        final RedisRequest request = newRequest(RedisProtocolSupport.Command.HMGET, buffer);
        final RedisPartitionAttributesBuilder partitionAttributesBuilder = partitionAttributesBuilderFunction
                    .apply(RedisProtocolSupport.Command.HMGET);
        partitionAttributesBuilder.addKey(key);
        final Single<List<Buffer>> result = (Single) partitionedRedisClient.request(partitionAttributesBuilder.build(),
                    request, List.class);
        return result;
    }

    @Override
    public Single<String> hmset(@RedisProtocolSupport.Key final Buffer key, final Buffer field, final Buffer value) {
        requireNonNull(key);
        requireNonNull(field);
        requireNonNull(value);
        final int len = 4;
        final int capacity = calculateInitialCommandBufferSize(len, RedisProtocolSupport.Command.HMSET) +
                    calculateRequestArgumentSize(key) + calculateRequestArgumentSize(field) +
                    calculateRequestArgumentSize(value);
        Buffer buffer = partitionedRedisClient.executionContext().bufferAllocator().newBuffer(capacity);
        writeRequestArraySize(buffer, len);
        RedisProtocolSupport.Command.HMSET.encodeTo(buffer);
        writeRequestArgument(buffer, key);
        writeRequestArgument(buffer, field);
        writeRequestArgument(buffer, value);
        final RedisRequest request = newRequest(RedisProtocolSupport.Command.HMSET, buffer);
        final RedisPartitionAttributesBuilder partitionAttributesBuilder = partitionAttributesBuilderFunction
                    .apply(RedisProtocolSupport.Command.HMSET);
        partitionAttributesBuilder.addKey(key);
        final Single<String> result = partitionedRedisClient.request(partitionAttributesBuilder.build(), request,
                    String.class);
        return result;
    }

    @Override
    public Single<String> hmset(@RedisProtocolSupport.Key final Buffer key, final Buffer field1, final Buffer value1,
                                final Buffer field2, final Buffer value2) {
        requireNonNull(key);
        requireNonNull(field1);
        requireNonNull(value1);
        requireNonNull(field2);
        requireNonNull(value2);
        final int len = 6;
        final int capacity = calculateInitialCommandBufferSize(len, RedisProtocolSupport.Command.HMSET) +
                    calculateRequestArgumentSize(key) + calculateRequestArgumentSize(field1) +
                    calculateRequestArgumentSize(value1) + calculateRequestArgumentSize(field2) +
                    calculateRequestArgumentSize(value2);
        Buffer buffer = partitionedRedisClient.executionContext().bufferAllocator().newBuffer(capacity);
        writeRequestArraySize(buffer, len);
        RedisProtocolSupport.Command.HMSET.encodeTo(buffer);
        writeRequestArgument(buffer, key);
        writeRequestArgument(buffer, field1);
        writeRequestArgument(buffer, value1);
        writeRequestArgument(buffer, field2);
        writeRequestArgument(buffer, value2);
        final RedisRequest request = newRequest(RedisProtocolSupport.Command.HMSET, buffer);
        final RedisPartitionAttributesBuilder partitionAttributesBuilder = partitionAttributesBuilderFunction
                    .apply(RedisProtocolSupport.Command.HMSET);
        partitionAttributesBuilder.addKey(key);
        final Single<String> result = partitionedRedisClient.request(partitionAttributesBuilder.build(), request,
                    String.class);
        return result;
    }

    @Override
    public Single<String> hmset(@RedisProtocolSupport.Key final Buffer key, final Buffer field1, final Buffer value1,
                                final Buffer field2, final Buffer value2, final Buffer field3, final Buffer value3) {
        requireNonNull(key);
        requireNonNull(field1);
        requireNonNull(value1);
        requireNonNull(field2);
        requireNonNull(value2);
        requireNonNull(field3);
        requireNonNull(value3);
        final int len = 8;
        final int capacity = calculateInitialCommandBufferSize(len, RedisProtocolSupport.Command.HMSET) +
                    calculateRequestArgumentSize(key) + calculateRequestArgumentSize(field1) +
                    calculateRequestArgumentSize(value1) + calculateRequestArgumentSize(field2) +
                    calculateRequestArgumentSize(value2) + calculateRequestArgumentSize(field3) +
                    calculateRequestArgumentSize(value3);
        Buffer buffer = partitionedRedisClient.executionContext().bufferAllocator().newBuffer(capacity);
        writeRequestArraySize(buffer, len);
        RedisProtocolSupport.Command.HMSET.encodeTo(buffer);
        writeRequestArgument(buffer, key);
        writeRequestArgument(buffer, field1);
        writeRequestArgument(buffer, value1);
        writeRequestArgument(buffer, field2);
        writeRequestArgument(buffer, value2);
        writeRequestArgument(buffer, field3);
        writeRequestArgument(buffer, value3);
        final RedisRequest request = newRequest(RedisProtocolSupport.Command.HMSET, buffer);
        final RedisPartitionAttributesBuilder partitionAttributesBuilder = partitionAttributesBuilderFunction
                    .apply(RedisProtocolSupport.Command.HMSET);
        partitionAttributesBuilder.addKey(key);
        final Single<String> result = partitionedRedisClient.request(partitionAttributesBuilder.build(), request,
                    String.class);
        return result;
    }

    @Override
    public Single<String> hmset(@RedisProtocolSupport.Key final Buffer key,
                                final Collection<RedisProtocolSupport.BufferFieldValue> fieldValues) {
        requireNonNull(key);
        requireNonNull(fieldValues);
        final int len = 2 + RedisProtocolSupport.BufferFieldValue.SIZE * fieldValues.size();
        int fieldValuesCapacity = 0;
        if (fieldValues instanceof List && fieldValues instanceof RandomAccess) {
            final List<RedisProtocolSupport.BufferFieldValue> list = (List<RedisProtocolSupport.BufferFieldValue>) fieldValues;
            for (int i = 0; i < list.size(); ++i) {
                final RedisProtocolSupport.BufferFieldValue arg = list.get(i);
                fieldValuesCapacity += arg.encodedByteCount();
            }
        } else {
            for (RedisProtocolSupport.BufferFieldValue arg : fieldValues) {
                fieldValuesCapacity += arg.encodedByteCount();
            }
        }
        final int capacity = calculateInitialCommandBufferSize(len, RedisProtocolSupport.Command.HMSET) +
                    calculateRequestArgumentSize(key) + fieldValuesCapacity;
        Buffer buffer = partitionedRedisClient.executionContext().bufferAllocator().newBuffer(capacity);
        writeRequestArraySize(buffer, len);
        RedisProtocolSupport.Command.HMSET.encodeTo(buffer);
        writeRequestArgument(buffer, key);
        if (fieldValues instanceof List && fieldValues instanceof RandomAccess) {
            final List<RedisProtocolSupport.BufferFieldValue> list = (List<RedisProtocolSupport.BufferFieldValue>) fieldValues;
            for (int i = 0; i < list.size(); ++i) {
                final RedisProtocolSupport.BufferFieldValue arg = list.get(i);
                arg.encodeTo(buffer);
            }
        } else {
            for (RedisProtocolSupport.BufferFieldValue arg : fieldValues) {
                arg.encodeTo(buffer);
            }
        }
        final RedisRequest request = newRequest(RedisProtocolSupport.Command.HMSET, buffer);
        final RedisPartitionAttributesBuilder partitionAttributesBuilder = partitionAttributesBuilderFunction
                    .apply(RedisProtocolSupport.Command.HMSET);
        partitionAttributesBuilder.addKey(key);
        final Single<String> result = partitionedRedisClient.request(partitionAttributesBuilder.build(), request,
                    String.class);
        return result;
    }

    @Override
    public <T> Single<List<T>> hscan(@RedisProtocolSupport.Key final Buffer key, final long cursor) {
        requireNonNull(key);
        final int len = 3;
        final int capacity = calculateInitialCommandBufferSize(len, RedisProtocolSupport.Command.HSCAN) +
                    calculateRequestArgumentSize(key) + calculateRequestArgumentSize(cursor);
        Buffer buffer = partitionedRedisClient.executionContext().bufferAllocator().newBuffer(capacity);
        writeRequestArraySize(buffer, len);
        RedisProtocolSupport.Command.HSCAN.encodeTo(buffer);
        writeRequestArgument(buffer, key);
        writeRequestArgument(buffer, cursor);
        final RedisRequest request = newRequest(RedisProtocolSupport.Command.HSCAN, buffer);
        final RedisPartitionAttributesBuilder partitionAttributesBuilder = partitionAttributesBuilderFunction
                    .apply(RedisProtocolSupport.Command.HSCAN);
        partitionAttributesBuilder.addKey(key);
        final Single<List<T>> result = (Single) partitionedRedisClient.request(partitionAttributesBuilder.build(),
                    request, List.class);
        return result;
    }

    @Override
    public <T> Single<List<T>> hscan(@RedisProtocolSupport.Key final Buffer key, final long cursor,
                                     @Nullable final Buffer matchPattern, @Nullable final Long count) {
        requireNonNull(key);
        final int len = 3 + (matchPattern == null ? 0 : 2) + (count == null ? 0 : 2);
        final int capacity = calculateInitialCommandBufferSize(len, RedisProtocolSupport.Command.HSCAN) +
                    calculateRequestArgumentSize(key) + calculateRequestArgumentSize(cursor) +
                    (matchPattern == null ? 0 : RedisProtocolSupport.SubCommand.MATCH.encodedByteCount()) +
                    (matchPattern == null ? 0 : calculateRequestArgumentSize(matchPattern)) +
                    (count == null ? 0 : RedisProtocolSupport.SubCommand.COUNT.encodedByteCount()) +
                    (count == null ? 0 : calculateRequestArgumentSize(count));
        Buffer buffer = partitionedRedisClient.executionContext().bufferAllocator().newBuffer(capacity);
        writeRequestArraySize(buffer, len);
        RedisProtocolSupport.Command.HSCAN.encodeTo(buffer);
        writeRequestArgument(buffer, key);
        writeRequestArgument(buffer, cursor);
        if (matchPattern != null) {
            RedisProtocolSupport.SubCommand.MATCH.encodeTo(buffer);
            writeRequestArgument(buffer, matchPattern);
        }
        if (count != null) {
            RedisProtocolSupport.SubCommand.COUNT.encodeTo(buffer);
            writeRequestArgument(buffer, count);
        }
        final RedisRequest request = newRequest(RedisProtocolSupport.Command.HSCAN, buffer);
        final RedisPartitionAttributesBuilder partitionAttributesBuilder = partitionAttributesBuilderFunction
                    .apply(RedisProtocolSupport.Command.HSCAN);
        partitionAttributesBuilder.addKey(key);
        final Single<List<T>> result = (Single) partitionedRedisClient.request(partitionAttributesBuilder.build(),
                    request, List.class);
        return result;
    }

    @Override
    public Single<Long> hset(@RedisProtocolSupport.Key final Buffer key, final Buffer field, final Buffer value) {
        requireNonNull(key);
        requireNonNull(field);
        requireNonNull(value);
        final int len = 4;
        final int capacity = calculateInitialCommandBufferSize(len, RedisProtocolSupport.Command.HSET) +
                    calculateRequestArgumentSize(key) + calculateRequestArgumentSize(field) +
                    calculateRequestArgumentSize(value);
        Buffer buffer = partitionedRedisClient.executionContext().bufferAllocator().newBuffer(capacity);
        writeRequestArraySize(buffer, len);
        RedisProtocolSupport.Command.HSET.encodeTo(buffer);
        writeRequestArgument(buffer, key);
        writeRequestArgument(buffer, field);
        writeRequestArgument(buffer, value);
        final RedisRequest request = newRequest(RedisProtocolSupport.Command.HSET, buffer);
        final RedisPartitionAttributesBuilder partitionAttributesBuilder = partitionAttributesBuilderFunction
                    .apply(RedisProtocolSupport.Command.HSET);
        partitionAttributesBuilder.addKey(key);
        final Single<Long> result = partitionedRedisClient.request(partitionAttributesBuilder.build(), request,
                    Long.class);
        return result;
    }

    @Override
    public Single<Long> hsetnx(@RedisProtocolSupport.Key final Buffer key, final Buffer field, final Buffer value) {
        requireNonNull(key);
        requireNonNull(field);
        requireNonNull(value);
        final int len = 4;
        final int capacity = calculateInitialCommandBufferSize(len, RedisProtocolSupport.Command.HSETNX) +
                    calculateRequestArgumentSize(key) + calculateRequestArgumentSize(field) +
                    calculateRequestArgumentSize(value);
        Buffer buffer = partitionedRedisClient.executionContext().bufferAllocator().newBuffer(capacity);
        writeRequestArraySize(buffer, len);
        RedisProtocolSupport.Command.HSETNX.encodeTo(buffer);
        writeRequestArgument(buffer, key);
        writeRequestArgument(buffer, field);
        writeRequestArgument(buffer, value);
        final RedisRequest request = newRequest(RedisProtocolSupport.Command.HSETNX, buffer);
        final RedisPartitionAttributesBuilder partitionAttributesBuilder = partitionAttributesBuilderFunction
                    .apply(RedisProtocolSupport.Command.HSETNX);
        partitionAttributesBuilder.addKey(key);
        final Single<Long> result = partitionedRedisClient.request(partitionAttributesBuilder.build(), request,
                    Long.class);
        return result;
    }

    @Override
    public Single<Long> hstrlen(@RedisProtocolSupport.Key final Buffer key, final Buffer field) {
        requireNonNull(key);
        requireNonNull(field);
        final int len = 3;
        final int capacity = calculateInitialCommandBufferSize(len, RedisProtocolSupport.Command.HSTRLEN) +
                    calculateRequestArgumentSize(key) + calculateRequestArgumentSize(field);
        Buffer buffer = partitionedRedisClient.executionContext().bufferAllocator().newBuffer(capacity);
        writeRequestArraySize(buffer, len);
        RedisProtocolSupport.Command.HSTRLEN.encodeTo(buffer);
        writeRequestArgument(buffer, key);
        writeRequestArgument(buffer, field);
        final RedisRequest request = newRequest(RedisProtocolSupport.Command.HSTRLEN, buffer);
        final RedisPartitionAttributesBuilder partitionAttributesBuilder = partitionAttributesBuilderFunction
                    .apply(RedisProtocolSupport.Command.HSTRLEN);
        partitionAttributesBuilder.addKey(key);
        final Single<Long> result = partitionedRedisClient.request(partitionAttributesBuilder.build(), request,
                    Long.class);
        return result;
    }

    @Override
    public <T> Single<List<T>> hvals(@RedisProtocolSupport.Key final Buffer key) {
        requireNonNull(key);
        final int len = 2;
        final int capacity = calculateInitialCommandBufferSize(len, RedisProtocolSupport.Command.HVALS) +
                    calculateRequestArgumentSize(key);
        Buffer buffer = partitionedRedisClient.executionContext().bufferAllocator().newBuffer(capacity);
        writeRequestArraySize(buffer, len);
        RedisProtocolSupport.Command.HVALS.encodeTo(buffer);
        writeRequestArgument(buffer, key);
        final RedisRequest request = newRequest(RedisProtocolSupport.Command.HVALS, buffer);
        final RedisPartitionAttributesBuilder partitionAttributesBuilder = partitionAttributesBuilderFunction
                    .apply(RedisProtocolSupport.Command.HVALS);
        partitionAttributesBuilder.addKey(key);
        final Single<List<T>> result = (Single) partitionedRedisClient.request(partitionAttributesBuilder.build(),
                    request, List.class);
        return result;
    }

    @Override
    public Single<Long> incr(@RedisProtocolSupport.Key final Buffer key) {
        requireNonNull(key);
        final int len = 2;
        final int capacity = calculateInitialCommandBufferSize(len, RedisProtocolSupport.Command.INCR) +
                    calculateRequestArgumentSize(key);
        Buffer buffer = partitionedRedisClient.executionContext().bufferAllocator().newBuffer(capacity);
        writeRequestArraySize(buffer, len);
        RedisProtocolSupport.Command.INCR.encodeTo(buffer);
        writeRequestArgument(buffer, key);
        final RedisRequest request = newRequest(RedisProtocolSupport.Command.INCR, buffer);
        final RedisPartitionAttributesBuilder partitionAttributesBuilder = partitionAttributesBuilderFunction
                    .apply(RedisProtocolSupport.Command.INCR);
        partitionAttributesBuilder.addKey(key);
        final Single<Long> result = partitionedRedisClient.request(partitionAttributesBuilder.build(), request,
                    Long.class);
        return result;
    }

    @Override
    public Single<Long> incrby(@RedisProtocolSupport.Key final Buffer key, final long increment) {
        requireNonNull(key);
        final int len = 3;
        final int capacity = calculateInitialCommandBufferSize(len, RedisProtocolSupport.Command.INCRBY) +
                    calculateRequestArgumentSize(key) + calculateRequestArgumentSize(increment);
        Buffer buffer = partitionedRedisClient.executionContext().bufferAllocator().newBuffer(capacity);
        writeRequestArraySize(buffer, len);
        RedisProtocolSupport.Command.INCRBY.encodeTo(buffer);
        writeRequestArgument(buffer, key);
        writeRequestArgument(buffer, increment);
        final RedisRequest request = newRequest(RedisProtocolSupport.Command.INCRBY, buffer);
        final RedisPartitionAttributesBuilder partitionAttributesBuilder = partitionAttributesBuilderFunction
                    .apply(RedisProtocolSupport.Command.INCRBY);
        partitionAttributesBuilder.addKey(key);
        final Single<Long> result = partitionedRedisClient.request(partitionAttributesBuilder.build(), request,
                    Long.class);
        return result;
    }

    @Override
    public Single<Double> incrbyfloat(@RedisProtocolSupport.Key final Buffer key, final double increment) {
        requireNonNull(key);
        final int len = 3;
        final int capacity = calculateInitialCommandBufferSize(len, RedisProtocolSupport.Command.INCRBYFLOAT) +
                    calculateRequestArgumentSize(key) + calculateRequestArgumentSize(increment);
        Buffer buffer = partitionedRedisClient.executionContext().bufferAllocator().newBuffer(capacity);
        writeRequestArraySize(buffer, len);
        RedisProtocolSupport.Command.INCRBYFLOAT.encodeTo(buffer);
        writeRequestArgument(buffer, key);
        writeRequestArgument(buffer, increment);
        final RedisRequest request = newRequest(RedisProtocolSupport.Command.INCRBYFLOAT, buffer);
        final RedisPartitionAttributesBuilder partitionAttributesBuilder = partitionAttributesBuilderFunction
                    .apply(RedisProtocolSupport.Command.INCRBYFLOAT);
        partitionAttributesBuilder.addKey(key);
        final Single<Double> result = partitionedRedisClient
                    .request(partitionAttributesBuilder.build(), request, String.class)
                    .map(s -> s != null ? Double.valueOf(s) : null);
        return result;
    }

    @Override
    public Single<Buffer> info() {
        final int len = 1;
        final int capacity = calculateInitialCommandBufferSize(len, RedisProtocolSupport.Command.INFO);
        Buffer buffer = partitionedRedisClient.executionContext().bufferAllocator().newBuffer(capacity);
        writeRequestArraySize(buffer, len);
        RedisProtocolSupport.Command.INFO.encodeTo(buffer);
        final RedisRequest request = newRequest(RedisProtocolSupport.Command.INFO, buffer);
        final RedisPartitionAttributesBuilder partitionAttributesBuilder = partitionAttributesBuilderFunction
                    .apply(RedisProtocolSupport.Command.INFO);
        final Single<Buffer> result = partitionedRedisClient.request(partitionAttributesBuilder.build(), request,
                    Buffer.class);
        return result;
    }

    @Override
    public Single<Buffer> info(@Nullable final Buffer section) {
        final int len = 1 + (section == null ? 0 : 1);
        final int capacity = calculateInitialCommandBufferSize(len, RedisProtocolSupport.Command.INFO) +
                    (section == null ? 0 : calculateRequestArgumentSize(section));
        Buffer buffer = partitionedRedisClient.executionContext().bufferAllocator().newBuffer(capacity);
        writeRequestArraySize(buffer, len);
        RedisProtocolSupport.Command.INFO.encodeTo(buffer);
        if (section != null) {
            writeRequestArgument(buffer, section);
        }
        final RedisRequest request = newRequest(RedisProtocolSupport.Command.INFO, buffer);
        final RedisPartitionAttributesBuilder partitionAttributesBuilder = partitionAttributesBuilderFunction
                    .apply(RedisProtocolSupport.Command.INFO);
        final Single<Buffer> result = partitionedRedisClient.request(partitionAttributesBuilder.build(), request,
                    Buffer.class);
        return result;
    }

    @Override
    public <T> Single<List<T>> keys(final Buffer pattern) {
        requireNonNull(pattern);
        final int len = 2;
        final int capacity = calculateInitialCommandBufferSize(len, RedisProtocolSupport.Command.KEYS) +
                    calculateRequestArgumentSize(pattern);
        Buffer buffer = partitionedRedisClient.executionContext().bufferAllocator().newBuffer(capacity);
        writeRequestArraySize(buffer, len);
        RedisProtocolSupport.Command.KEYS.encodeTo(buffer);
        writeRequestArgument(buffer, pattern);
        final RedisRequest request = newRequest(RedisProtocolSupport.Command.KEYS, buffer);
        final RedisPartitionAttributesBuilder partitionAttributesBuilder = partitionAttributesBuilderFunction
                    .apply(RedisProtocolSupport.Command.KEYS);
        final Single<List<T>> result = (Single) partitionedRedisClient.request(partitionAttributesBuilder.build(),
                    request, List.class);
        return result;
    }

    @Override
    public Single<Long> lastsave() {
        final int len = 1;
        final int capacity = calculateInitialCommandBufferSize(len, RedisProtocolSupport.Command.LASTSAVE);
        Buffer buffer = partitionedRedisClient.executionContext().bufferAllocator().newBuffer(capacity);
        writeRequestArraySize(buffer, len);
        RedisProtocolSupport.Command.LASTSAVE.encodeTo(buffer);
        final RedisRequest request = newRequest(RedisProtocolSupport.Command.LASTSAVE, buffer);
        final RedisPartitionAttributesBuilder partitionAttributesBuilder = partitionAttributesBuilderFunction
                    .apply(RedisProtocolSupport.Command.LASTSAVE);
        final Single<Long> result = partitionedRedisClient.request(partitionAttributesBuilder.build(), request,
                    Long.class);
        return result;
    }

    @Override
    public Single<Buffer> lindex(@RedisProtocolSupport.Key final Buffer key, final long index) {
        requireNonNull(key);
        final int len = 3;
        final int capacity = calculateInitialCommandBufferSize(len, RedisProtocolSupport.Command.LINDEX) +
                    calculateRequestArgumentSize(key) + calculateRequestArgumentSize(index);
        Buffer buffer = partitionedRedisClient.executionContext().bufferAllocator().newBuffer(capacity);
        writeRequestArraySize(buffer, len);
        RedisProtocolSupport.Command.LINDEX.encodeTo(buffer);
        writeRequestArgument(buffer, key);
        writeRequestArgument(buffer, index);
        final RedisRequest request = newRequest(RedisProtocolSupport.Command.LINDEX, buffer);
        final RedisPartitionAttributesBuilder partitionAttributesBuilder = partitionAttributesBuilderFunction
                    .apply(RedisProtocolSupport.Command.LINDEX);
        partitionAttributesBuilder.addKey(key);
        final Single<Buffer> result = partitionedRedisClient.request(partitionAttributesBuilder.build(), request,
                    Buffer.class);
        return result;
    }

    @Override
    public Single<Long> linsert(@RedisProtocolSupport.Key final Buffer key,
                                final RedisProtocolSupport.LinsertWhere where, final Buffer pivot, final Buffer value) {
        requireNonNull(key);
        requireNonNull(where);
        requireNonNull(pivot);
        requireNonNull(value);
        final int len = 5;
        final int capacity = calculateInitialCommandBufferSize(len, RedisProtocolSupport.Command.LINSERT) +
                    calculateRequestArgumentSize(key) + where.encodedByteCount() + calculateRequestArgumentSize(pivot) +
                    calculateRequestArgumentSize(value);
        Buffer buffer = partitionedRedisClient.executionContext().bufferAllocator().newBuffer(capacity);
        writeRequestArraySize(buffer, len);
        RedisProtocolSupport.Command.LINSERT.encodeTo(buffer);
        writeRequestArgument(buffer, key);
        where.encodeTo(buffer);
        writeRequestArgument(buffer, pivot);
        writeRequestArgument(buffer, value);
        final RedisRequest request = newRequest(RedisProtocolSupport.Command.LINSERT, buffer);
        final RedisPartitionAttributesBuilder partitionAttributesBuilder = partitionAttributesBuilderFunction
                    .apply(RedisProtocolSupport.Command.LINSERT);
        partitionAttributesBuilder.addKey(key);
        final Single<Long> result = partitionedRedisClient.request(partitionAttributesBuilder.build(), request,
                    Long.class);
        return result;
    }

    @Override
    public Single<Long> llen(@RedisProtocolSupport.Key final Buffer key) {
        requireNonNull(key);
        final int len = 2;
        final int capacity = calculateInitialCommandBufferSize(len, RedisProtocolSupport.Command.LLEN) +
                    calculateRequestArgumentSize(key);
        Buffer buffer = partitionedRedisClient.executionContext().bufferAllocator().newBuffer(capacity);
        writeRequestArraySize(buffer, len);
        RedisProtocolSupport.Command.LLEN.encodeTo(buffer);
        writeRequestArgument(buffer, key);
        final RedisRequest request = newRequest(RedisProtocolSupport.Command.LLEN, buffer);
        final RedisPartitionAttributesBuilder partitionAttributesBuilder = partitionAttributesBuilderFunction
                    .apply(RedisProtocolSupport.Command.LLEN);
        partitionAttributesBuilder.addKey(key);
        final Single<Long> result = partitionedRedisClient.request(partitionAttributesBuilder.build(), request,
                    Long.class);
        return result;
    }

    @Override
    public Single<Buffer> lpop(@RedisProtocolSupport.Key final Buffer key) {
        requireNonNull(key);
        final int len = 2;
        final int capacity = calculateInitialCommandBufferSize(len, RedisProtocolSupport.Command.LPOP) +
                    calculateRequestArgumentSize(key);
        Buffer buffer = partitionedRedisClient.executionContext().bufferAllocator().newBuffer(capacity);
        writeRequestArraySize(buffer, len);
        RedisProtocolSupport.Command.LPOP.encodeTo(buffer);
        writeRequestArgument(buffer, key);
        final RedisRequest request = newRequest(RedisProtocolSupport.Command.LPOP, buffer);
        final RedisPartitionAttributesBuilder partitionAttributesBuilder = partitionAttributesBuilderFunction
                    .apply(RedisProtocolSupport.Command.LPOP);
        partitionAttributesBuilder.addKey(key);
        final Single<Buffer> result = partitionedRedisClient.request(partitionAttributesBuilder.build(), request,
                    Buffer.class);
        return result;
    }

    @Override
    public Single<Long> lpush(@RedisProtocolSupport.Key final Buffer key, final Buffer value) {
        requireNonNull(key);
        requireNonNull(value);
        final int len = 3;
        final int capacity = calculateInitialCommandBufferSize(len, RedisProtocolSupport.Command.LPUSH) +
                    calculateRequestArgumentSize(key) + calculateRequestArgumentSize(value);
        Buffer buffer = partitionedRedisClient.executionContext().bufferAllocator().newBuffer(capacity);
        writeRequestArraySize(buffer, len);
        RedisProtocolSupport.Command.LPUSH.encodeTo(buffer);
        writeRequestArgument(buffer, key);
        writeRequestArgument(buffer, value);
        final RedisRequest request = newRequest(RedisProtocolSupport.Command.LPUSH, buffer);
        final RedisPartitionAttributesBuilder partitionAttributesBuilder = partitionAttributesBuilderFunction
                    .apply(RedisProtocolSupport.Command.LPUSH);
        partitionAttributesBuilder.addKey(key);
        final Single<Long> result = partitionedRedisClient.request(partitionAttributesBuilder.build(), request,
                    Long.class);
        return result;
    }

    @Override
    public Single<Long> lpush(@RedisProtocolSupport.Key final Buffer key, final Buffer value1, final Buffer value2) {
        requireNonNull(key);
        requireNonNull(value1);
        requireNonNull(value2);
        final int len = 4;
        final int capacity = calculateInitialCommandBufferSize(len, RedisProtocolSupport.Command.LPUSH) +
                    calculateRequestArgumentSize(key) + calculateRequestArgumentSize(value1) +
                    calculateRequestArgumentSize(value2);
        Buffer buffer = partitionedRedisClient.executionContext().bufferAllocator().newBuffer(capacity);
        writeRequestArraySize(buffer, len);
        RedisProtocolSupport.Command.LPUSH.encodeTo(buffer);
        writeRequestArgument(buffer, key);
        writeRequestArgument(buffer, value1);
        writeRequestArgument(buffer, value2);
        final RedisRequest request = newRequest(RedisProtocolSupport.Command.LPUSH, buffer);
        final RedisPartitionAttributesBuilder partitionAttributesBuilder = partitionAttributesBuilderFunction
                    .apply(RedisProtocolSupport.Command.LPUSH);
        partitionAttributesBuilder.addKey(key);
        final Single<Long> result = partitionedRedisClient.request(partitionAttributesBuilder.build(), request,
                    Long.class);
        return result;
    }

    @Override
    public Single<Long> lpush(@RedisProtocolSupport.Key final Buffer key, final Buffer value1, final Buffer value2,
                              final Buffer value3) {
        requireNonNull(key);
        requireNonNull(value1);
        requireNonNull(value2);
        requireNonNull(value3);
        final int len = 5;
        final int capacity = calculateInitialCommandBufferSize(len, RedisProtocolSupport.Command.LPUSH) +
                    calculateRequestArgumentSize(key) + calculateRequestArgumentSize(value1) +
                    calculateRequestArgumentSize(value2) + calculateRequestArgumentSize(value3);
        Buffer buffer = partitionedRedisClient.executionContext().bufferAllocator().newBuffer(capacity);
        writeRequestArraySize(buffer, len);
        RedisProtocolSupport.Command.LPUSH.encodeTo(buffer);
        writeRequestArgument(buffer, key);
        writeRequestArgument(buffer, value1);
        writeRequestArgument(buffer, value2);
        writeRequestArgument(buffer, value3);
        final RedisRequest request = newRequest(RedisProtocolSupport.Command.LPUSH, buffer);
        final RedisPartitionAttributesBuilder partitionAttributesBuilder = partitionAttributesBuilderFunction
                    .apply(RedisProtocolSupport.Command.LPUSH);
        partitionAttributesBuilder.addKey(key);
        final Single<Long> result = partitionedRedisClient.request(partitionAttributesBuilder.build(), request,
                    Long.class);
        return result;
    }

    @Override
    public Single<Long> lpush(@RedisProtocolSupport.Key final Buffer key, final Collection<Buffer> values) {
        requireNonNull(key);
        requireNonNull(values);
        final int len = 2 + values.size();
        int valuesCapacity = 0;
        if (values instanceof List && values instanceof RandomAccess) {
            final List<Buffer> list = (List<Buffer>) values;
            for (int i = 0; i < list.size(); ++i) {
                final Buffer arg = list.get(i);
                valuesCapacity += calculateRequestArgumentSize(arg);
            }
        } else {
            for (Buffer arg : values) {
                valuesCapacity += calculateRequestArgumentSize(arg);
            }
        }
        final int capacity = calculateInitialCommandBufferSize(len, RedisProtocolSupport.Command.LPUSH) +
                    calculateRequestArgumentSize(key) + valuesCapacity;
        Buffer buffer = partitionedRedisClient.executionContext().bufferAllocator().newBuffer(capacity);
        writeRequestArraySize(buffer, len);
        RedisProtocolSupport.Command.LPUSH.encodeTo(buffer);
        writeRequestArgument(buffer, key);
        if (values instanceof List && values instanceof RandomAccess) {
            final List<Buffer> list = (List<Buffer>) values;
            for (int i = 0; i < list.size(); ++i) {
                final Buffer arg = list.get(i);
                writeRequestArgument(buffer, arg);
            }
        } else {
            for (Buffer arg : values) {
                writeRequestArgument(buffer, arg);
            }
        }
        final RedisRequest request = newRequest(RedisProtocolSupport.Command.LPUSH, buffer);
        final RedisPartitionAttributesBuilder partitionAttributesBuilder = partitionAttributesBuilderFunction
                    .apply(RedisProtocolSupport.Command.LPUSH);
        partitionAttributesBuilder.addKey(key);
        final Single<Long> result = partitionedRedisClient.request(partitionAttributesBuilder.build(), request,
                    Long.class);
        return result;
    }

    @Override
    public Single<Long> lpushx(@RedisProtocolSupport.Key final Buffer key, final Buffer value) {
        requireNonNull(key);
        requireNonNull(value);
        final int len = 3;
        final int capacity = calculateInitialCommandBufferSize(len, RedisProtocolSupport.Command.LPUSHX) +
                    calculateRequestArgumentSize(key) + calculateRequestArgumentSize(value);
        Buffer buffer = partitionedRedisClient.executionContext().bufferAllocator().newBuffer(capacity);
        writeRequestArraySize(buffer, len);
        RedisProtocolSupport.Command.LPUSHX.encodeTo(buffer);
        writeRequestArgument(buffer, key);
        writeRequestArgument(buffer, value);
        final RedisRequest request = newRequest(RedisProtocolSupport.Command.LPUSHX, buffer);
        final RedisPartitionAttributesBuilder partitionAttributesBuilder = partitionAttributesBuilderFunction
                    .apply(RedisProtocolSupport.Command.LPUSHX);
        partitionAttributesBuilder.addKey(key);
        final Single<Long> result = partitionedRedisClient.request(partitionAttributesBuilder.build(), request,
                    Long.class);
        return result;
    }

    @Override
    public <T> Single<List<T>> lrange(@RedisProtocolSupport.Key final Buffer key, final long start, final long stop) {
        requireNonNull(key);
        final int len = 4;
        final int capacity = calculateInitialCommandBufferSize(len, RedisProtocolSupport.Command.LRANGE) +
                    calculateRequestArgumentSize(key) + calculateRequestArgumentSize(start) +
                    calculateRequestArgumentSize(stop);
        Buffer buffer = partitionedRedisClient.executionContext().bufferAllocator().newBuffer(capacity);
        writeRequestArraySize(buffer, len);
        RedisProtocolSupport.Command.LRANGE.encodeTo(buffer);
        writeRequestArgument(buffer, key);
        writeRequestArgument(buffer, start);
        writeRequestArgument(buffer, stop);
        final RedisRequest request = newRequest(RedisProtocolSupport.Command.LRANGE, buffer);
        final RedisPartitionAttributesBuilder partitionAttributesBuilder = partitionAttributesBuilderFunction
                    .apply(RedisProtocolSupport.Command.LRANGE);
        partitionAttributesBuilder.addKey(key);
        final Single<List<T>> result = (Single) partitionedRedisClient.request(partitionAttributesBuilder.build(),
                    request, List.class);
        return result;
    }

    @Override
    public Single<Long> lrem(@RedisProtocolSupport.Key final Buffer key, final long count, final Buffer value) {
        requireNonNull(key);
        requireNonNull(value);
        final int len = 4;
        final int capacity = calculateInitialCommandBufferSize(len, RedisProtocolSupport.Command.LREM) +
                    calculateRequestArgumentSize(key) + calculateRequestArgumentSize(count) +
                    calculateRequestArgumentSize(value);
        Buffer buffer = partitionedRedisClient.executionContext().bufferAllocator().newBuffer(capacity);
        writeRequestArraySize(buffer, len);
        RedisProtocolSupport.Command.LREM.encodeTo(buffer);
        writeRequestArgument(buffer, key);
        writeRequestArgument(buffer, count);
        writeRequestArgument(buffer, value);
        final RedisRequest request = newRequest(RedisProtocolSupport.Command.LREM, buffer);
        final RedisPartitionAttributesBuilder partitionAttributesBuilder = partitionAttributesBuilderFunction
                    .apply(RedisProtocolSupport.Command.LREM);
        partitionAttributesBuilder.addKey(key);
        final Single<Long> result = partitionedRedisClient.request(partitionAttributesBuilder.build(), request,
                    Long.class);
        return result;
    }

    @Override
    public Single<String> lset(@RedisProtocolSupport.Key final Buffer key, final long index, final Buffer value) {
        requireNonNull(key);
        requireNonNull(value);
        final int len = 4;
        final int capacity = calculateInitialCommandBufferSize(len, RedisProtocolSupport.Command.LSET) +
                    calculateRequestArgumentSize(key) + calculateRequestArgumentSize(index) +
                    calculateRequestArgumentSize(value);
        Buffer buffer = partitionedRedisClient.executionContext().bufferAllocator().newBuffer(capacity);
        writeRequestArraySize(buffer, len);
        RedisProtocolSupport.Command.LSET.encodeTo(buffer);
        writeRequestArgument(buffer, key);
        writeRequestArgument(buffer, index);
        writeRequestArgument(buffer, value);
        final RedisRequest request = newRequest(RedisProtocolSupport.Command.LSET, buffer);
        final RedisPartitionAttributesBuilder partitionAttributesBuilder = partitionAttributesBuilderFunction
                    .apply(RedisProtocolSupport.Command.LSET);
        partitionAttributesBuilder.addKey(key);
        final Single<String> result = partitionedRedisClient.request(partitionAttributesBuilder.build(), request,
                    String.class);
        return result;
    }

    @Override
    public Single<String> ltrim(@RedisProtocolSupport.Key final Buffer key, final long start, final long stop) {
        requireNonNull(key);
        final int len = 4;
        final int capacity = calculateInitialCommandBufferSize(len, RedisProtocolSupport.Command.LTRIM) +
                    calculateRequestArgumentSize(key) + calculateRequestArgumentSize(start) +
                    calculateRequestArgumentSize(stop);
        Buffer buffer = partitionedRedisClient.executionContext().bufferAllocator().newBuffer(capacity);
        writeRequestArraySize(buffer, len);
        RedisProtocolSupport.Command.LTRIM.encodeTo(buffer);
        writeRequestArgument(buffer, key);
        writeRequestArgument(buffer, start);
        writeRequestArgument(buffer, stop);
        final RedisRequest request = newRequest(RedisProtocolSupport.Command.LTRIM, buffer);
        final RedisPartitionAttributesBuilder partitionAttributesBuilder = partitionAttributesBuilderFunction
                    .apply(RedisProtocolSupport.Command.LTRIM);
        partitionAttributesBuilder.addKey(key);
        final Single<String> result = partitionedRedisClient.request(partitionAttributesBuilder.build(), request,
                    String.class);
        return result;
    }

    @Override
    public Single<Buffer> memoryDoctor() {
        final int len = 2;
        final int capacity = 1 + calculateInitialCommandBufferSize(len, RedisProtocolSupport.Command.MEMORY);
        Buffer buffer = partitionedRedisClient.executionContext().bufferAllocator().newBuffer(capacity);
        writeRequestArraySize(buffer, len);
        RedisProtocolSupport.Command.MEMORY.encodeTo(buffer);
        RedisProtocolSupport.SubCommand.DOCTOR.encodeTo(buffer);
        final RedisRequest request = newRequest(RedisProtocolSupport.Command.MEMORY, buffer);
        final RedisPartitionAttributesBuilder partitionAttributesBuilder = partitionAttributesBuilderFunction
                    .apply(RedisProtocolSupport.Command.MEMORY);
        final Single<Buffer> result = partitionedRedisClient.request(partitionAttributesBuilder.build(), request,
                    Buffer.class);
        return result;
    }

    @Override
    public <T> Single<List<T>> memoryHelp() {
        final int len = 2;
        final int capacity = 1 + calculateInitialCommandBufferSize(len, RedisProtocolSupport.Command.MEMORY);
        Buffer buffer = partitionedRedisClient.executionContext().bufferAllocator().newBuffer(capacity);
        writeRequestArraySize(buffer, len);
        RedisProtocolSupport.Command.MEMORY.encodeTo(buffer);
        RedisProtocolSupport.SubCommand.HELP.encodeTo(buffer);
        final RedisRequest request = newRequest(RedisProtocolSupport.Command.MEMORY, buffer);
        final RedisPartitionAttributesBuilder partitionAttributesBuilder = partitionAttributesBuilderFunction
                    .apply(RedisProtocolSupport.Command.MEMORY);
        final Single<List<T>> result = (Single) partitionedRedisClient.request(partitionAttributesBuilder.build(),
                    request, List.class);
        return result;
    }

    @Override
    public Single<Buffer> memoryMallocStats() {
        final int len = 2;
        final int capacity = 1 + calculateInitialCommandBufferSize(len, RedisProtocolSupport.Command.MEMORY);
        Buffer buffer = partitionedRedisClient.executionContext().bufferAllocator().newBuffer(capacity);
        writeRequestArraySize(buffer, len);
        RedisProtocolSupport.Command.MEMORY.encodeTo(buffer);
        RedisProtocolSupport.SubCommand.MALLOC_STATS.encodeTo(buffer);
        final RedisRequest request = newRequest(RedisProtocolSupport.Command.MEMORY, buffer);
        final RedisPartitionAttributesBuilder partitionAttributesBuilder = partitionAttributesBuilderFunction
                    .apply(RedisProtocolSupport.Command.MEMORY);
        final Single<Buffer> result = partitionedRedisClient.request(partitionAttributesBuilder.build(), request,
                    Buffer.class);
        return result;
    }

    @Override
    public Single<String> memoryPurge() {
        final int len = 2;
        final int capacity = 1 + calculateInitialCommandBufferSize(len, RedisProtocolSupport.Command.MEMORY);
        Buffer buffer = partitionedRedisClient.executionContext().bufferAllocator().newBuffer(capacity);
        writeRequestArraySize(buffer, len);
        RedisProtocolSupport.Command.MEMORY.encodeTo(buffer);
        RedisProtocolSupport.SubCommand.PURGE.encodeTo(buffer);
        final RedisRequest request = newRequest(RedisProtocolSupport.Command.MEMORY, buffer);
        final RedisPartitionAttributesBuilder partitionAttributesBuilder = partitionAttributesBuilderFunction
                    .apply(RedisProtocolSupport.Command.MEMORY);
        final Single<String> result = partitionedRedisClient.request(partitionAttributesBuilder.build(), request,
                    String.class);
        return result;
    }

    @Override
    public <T> Single<List<T>> memoryStats() {
        final int len = 2;
        final int capacity = 1 + calculateInitialCommandBufferSize(len, RedisProtocolSupport.Command.MEMORY);
        Buffer buffer = partitionedRedisClient.executionContext().bufferAllocator().newBuffer(capacity);
        writeRequestArraySize(buffer, len);
        RedisProtocolSupport.Command.MEMORY.encodeTo(buffer);
        RedisProtocolSupport.SubCommand.STATS.encodeTo(buffer);
        final RedisRequest request = newRequest(RedisProtocolSupport.Command.MEMORY, buffer);
        final RedisPartitionAttributesBuilder partitionAttributesBuilder = partitionAttributesBuilderFunction
                    .apply(RedisProtocolSupport.Command.MEMORY);
        final Single<List<T>> result = (Single) partitionedRedisClient.request(partitionAttributesBuilder.build(),
                    request, List.class);
        return result;
    }

    @Override
    public Single<Long> memoryUsage(@RedisProtocolSupport.Key final Buffer key) {
        requireNonNull(key);
        final int len = 3;
        final int capacity = 1 + calculateInitialCommandBufferSize(len, RedisProtocolSupport.Command.MEMORY) +
                    calculateRequestArgumentSize(key);
        Buffer buffer = partitionedRedisClient.executionContext().bufferAllocator().newBuffer(capacity);
        writeRequestArraySize(buffer, len);
        RedisProtocolSupport.Command.MEMORY.encodeTo(buffer);
        RedisProtocolSupport.SubCommand.USAGE.encodeTo(buffer);
        writeRequestArgument(buffer, key);
        final RedisRequest request = newRequest(RedisProtocolSupport.Command.MEMORY, buffer);
        final RedisPartitionAttributesBuilder partitionAttributesBuilder = partitionAttributesBuilderFunction
                    .apply(RedisProtocolSupport.Command.MEMORY);
        partitionAttributesBuilder.addKey(key);
        final Single<Long> result = partitionedRedisClient.request(partitionAttributesBuilder.build(), request,
                    Long.class);
        return result;
    }

    @Override
    public Single<Long> memoryUsage(@RedisProtocolSupport.Key final Buffer key, @Nullable final Long samplesCount) {
        requireNonNull(key);
        final int len = 3 + (samplesCount == null ? 0 : 2);
        final int capacity = 1 + calculateInitialCommandBufferSize(len, RedisProtocolSupport.Command.MEMORY) +
                    calculateRequestArgumentSize(key) +
                    (samplesCount == null ? 0 : RedisProtocolSupport.SubCommand.SAMPLES.encodedByteCount()) +
                    (samplesCount == null ? 0 : calculateRequestArgumentSize(samplesCount));
        Buffer buffer = partitionedRedisClient.executionContext().bufferAllocator().newBuffer(capacity);
        writeRequestArraySize(buffer, len);
        RedisProtocolSupport.Command.MEMORY.encodeTo(buffer);
        RedisProtocolSupport.SubCommand.USAGE.encodeTo(buffer);
        writeRequestArgument(buffer, key);
        if (samplesCount != null) {
            RedisProtocolSupport.SubCommand.SAMPLES.encodeTo(buffer);
            writeRequestArgument(buffer, samplesCount);
        }
        final RedisRequest request = newRequest(RedisProtocolSupport.Command.MEMORY, buffer);
        final RedisPartitionAttributesBuilder partitionAttributesBuilder = partitionAttributesBuilderFunction
                    .apply(RedisProtocolSupport.Command.MEMORY);
        partitionAttributesBuilder.addKey(key);
        final Single<Long> result = partitionedRedisClient.request(partitionAttributesBuilder.build(), request,
                    Long.class);
        return result;
    }

    @Override
    public Single<List<Buffer>> mget(@RedisProtocolSupport.Key final Buffer key) {
        requireNonNull(key);
        final int len = 2;
        final int capacity = calculateInitialCommandBufferSize(len, RedisProtocolSupport.Command.MGET) +
                    calculateRequestArgumentSize(key);
        Buffer buffer = partitionedRedisClient.executionContext().bufferAllocator().newBuffer(capacity);
        writeRequestArraySize(buffer, len);
        RedisProtocolSupport.Command.MGET.encodeTo(buffer);
        writeRequestArgument(buffer, key);
        final RedisRequest request = newRequest(RedisProtocolSupport.Command.MGET, buffer);
        final RedisPartitionAttributesBuilder partitionAttributesBuilder = partitionAttributesBuilderFunction
                    .apply(RedisProtocolSupport.Command.MGET);
        partitionAttributesBuilder.addKey(key);
        final Single<List<Buffer>> result = (Single) partitionedRedisClient.request(partitionAttributesBuilder.build(),
                    request, List.class);
        return result;
    }

    @Override
    public Single<List<Buffer>> mget(@RedisProtocolSupport.Key final Buffer key1,
                                     @RedisProtocolSupport.Key final Buffer key2) {
        requireNonNull(key1);
        requireNonNull(key2);
        final int len = 3;
        final int capacity = calculateInitialCommandBufferSize(len, RedisProtocolSupport.Command.MGET) +
                    calculateRequestArgumentSize(key1) + calculateRequestArgumentSize(key2);
        Buffer buffer = partitionedRedisClient.executionContext().bufferAllocator().newBuffer(capacity);
        writeRequestArraySize(buffer, len);
        RedisProtocolSupport.Command.MGET.encodeTo(buffer);
        writeRequestArgument(buffer, key1);
        writeRequestArgument(buffer, key2);
        final RedisRequest request = newRequest(RedisProtocolSupport.Command.MGET, buffer);
        final RedisPartitionAttributesBuilder partitionAttributesBuilder = partitionAttributesBuilderFunction
                    .apply(RedisProtocolSupport.Command.MGET);
        partitionAttributesBuilder.addKey(key1);
        partitionAttributesBuilder.addKey(key2);
        final Single<List<Buffer>> result = (Single) partitionedRedisClient.request(partitionAttributesBuilder.build(),
                    request, List.class);
        return result;
    }

    @Override
    public Single<List<Buffer>> mget(@RedisProtocolSupport.Key final Buffer key1,
                                     @RedisProtocolSupport.Key final Buffer key2,
                                     @RedisProtocolSupport.Key final Buffer key3) {
        requireNonNull(key1);
        requireNonNull(key2);
        requireNonNull(key3);
        final int len = 4;
        final int capacity = calculateInitialCommandBufferSize(len, RedisProtocolSupport.Command.MGET) +
                    calculateRequestArgumentSize(key1) + calculateRequestArgumentSize(key2) +
                    calculateRequestArgumentSize(key3);
        Buffer buffer = partitionedRedisClient.executionContext().bufferAllocator().newBuffer(capacity);
        writeRequestArraySize(buffer, len);
        RedisProtocolSupport.Command.MGET.encodeTo(buffer);
        writeRequestArgument(buffer, key1);
        writeRequestArgument(buffer, key2);
        writeRequestArgument(buffer, key3);
        final RedisRequest request = newRequest(RedisProtocolSupport.Command.MGET, buffer);
        final RedisPartitionAttributesBuilder partitionAttributesBuilder = partitionAttributesBuilderFunction
                    .apply(RedisProtocolSupport.Command.MGET);
        partitionAttributesBuilder.addKey(key1);
        partitionAttributesBuilder.addKey(key2);
        partitionAttributesBuilder.addKey(key3);
        final Single<List<Buffer>> result = (Single) partitionedRedisClient.request(partitionAttributesBuilder.build(),
                    request, List.class);
        return result;
    }

    @Override
    public Single<List<Buffer>> mget(@RedisProtocolSupport.Key final Collection<Buffer> keys) {
        requireNonNull(keys);
        final int len = 1 + keys.size();
        int keysCapacity = 0;
        if (keys instanceof List && keys instanceof RandomAccess) {
            final List<Buffer> list = (List<Buffer>) keys;
            for (int i = 0; i < list.size(); ++i) {
                final Buffer arg = list.get(i);
                keysCapacity += calculateRequestArgumentSize(arg);
            }
        } else {
            for (Buffer arg : keys) {
                keysCapacity += calculateRequestArgumentSize(arg);
            }
        }
        final int capacity = calculateInitialCommandBufferSize(len, RedisProtocolSupport.Command.MGET) + keysCapacity;
        Buffer buffer = partitionedRedisClient.executionContext().bufferAllocator().newBuffer(capacity);
        writeRequestArraySize(buffer, len);
        RedisProtocolSupport.Command.MGET.encodeTo(buffer);
        if (keys instanceof List && keys instanceof RandomAccess) {
            final List<Buffer> list = (List<Buffer>) keys;
            for (int i = 0; i < list.size(); ++i) {
                final Buffer arg = list.get(i);
                writeRequestArgument(buffer, arg);
            }
        } else {
            for (Buffer arg : keys) {
                writeRequestArgument(buffer, arg);
            }
        }
        final RedisRequest request = newRequest(RedisProtocolSupport.Command.MGET, buffer);
        final RedisPartitionAttributesBuilder partitionAttributesBuilder = partitionAttributesBuilderFunction
                    .apply(RedisProtocolSupport.Command.MGET);
        addBufferKeysToAttributeBuilder(keys, partitionAttributesBuilder);
        final Single<List<Buffer>> result = (Single) partitionedRedisClient.request(partitionAttributesBuilder.build(),
                    request, List.class);
        return result;
    }

    @Override
    public Publisher<String> monitor() {
        final int len = 1;
        final int capacity = calculateInitialCommandBufferSize(len, RedisProtocolSupport.Command.MONITOR);
        Buffer buffer = partitionedRedisClient.executionContext().bufferAllocator().newBuffer(capacity);
        writeRequestArraySize(buffer, len);
        RedisProtocolSupport.Command.MONITOR.encodeTo(buffer);
        final RedisRequest request = newRequest(RedisProtocolSupport.Command.MONITOR, buffer);
        final RedisPartitionAttributesBuilder partitionAttributesBuilder = partitionAttributesBuilderFunction
                    .apply(RedisProtocolSupport.Command.MONITOR);
        return reserveConnection(partitionedRedisClient, partitionAttributesBuilder.build(), request,
                    (con, pub) -> pub.map(RedisCoercions::simpleStringToString)).flatMapPublisher(identity());
    }

    @Override
    public Single<Long> move(@RedisProtocolSupport.Key final Buffer key, final long db) {
        requireNonNull(key);
        final int len = 3;
        final int capacity = calculateInitialCommandBufferSize(len, RedisProtocolSupport.Command.MOVE) +
                    calculateRequestArgumentSize(key) + calculateRequestArgumentSize(db);
        Buffer buffer = partitionedRedisClient.executionContext().bufferAllocator().newBuffer(capacity);
        writeRequestArraySize(buffer, len);
        RedisProtocolSupport.Command.MOVE.encodeTo(buffer);
        writeRequestArgument(buffer, key);
        writeRequestArgument(buffer, db);
        final RedisRequest request = newRequest(RedisProtocolSupport.Command.MOVE, buffer);
        final RedisPartitionAttributesBuilder partitionAttributesBuilder = partitionAttributesBuilderFunction
                    .apply(RedisProtocolSupport.Command.MOVE);
        partitionAttributesBuilder.addKey(key);
        final Single<Long> result = partitionedRedisClient.request(partitionAttributesBuilder.build(), request,
                    Long.class);
        return result;
    }

    @Override
    public Single<String> mset(@RedisProtocolSupport.Key final Buffer key, final Buffer value) {
        requireNonNull(key);
        requireNonNull(value);
        final int len = 3;
        final int capacity = calculateInitialCommandBufferSize(len, RedisProtocolSupport.Command.MSET) +
                    calculateRequestArgumentSize(key) + calculateRequestArgumentSize(value);
        Buffer buffer = partitionedRedisClient.executionContext().bufferAllocator().newBuffer(capacity);
        writeRequestArraySize(buffer, len);
        RedisProtocolSupport.Command.MSET.encodeTo(buffer);
        writeRequestArgument(buffer, key);
        writeRequestArgument(buffer, value);
        final RedisRequest request = newRequest(RedisProtocolSupport.Command.MSET, buffer);
        final RedisPartitionAttributesBuilder partitionAttributesBuilder = partitionAttributesBuilderFunction
                    .apply(RedisProtocolSupport.Command.MSET);
        partitionAttributesBuilder.addKey(key);
        final Single<String> result = partitionedRedisClient.request(partitionAttributesBuilder.build(), request,
                    String.class);
        return result;
    }

    @Override
    public Single<String> mset(@RedisProtocolSupport.Key final Buffer key1, final Buffer value1,
                               @RedisProtocolSupport.Key final Buffer key2, final Buffer value2) {
        requireNonNull(key1);
        requireNonNull(value1);
        requireNonNull(key2);
        requireNonNull(value2);
        final int len = 5;
        final int capacity = calculateInitialCommandBufferSize(len, RedisProtocolSupport.Command.MSET) +
                    calculateRequestArgumentSize(key1) + calculateRequestArgumentSize(value1) +
                    calculateRequestArgumentSize(key2) + calculateRequestArgumentSize(value2);
        Buffer buffer = partitionedRedisClient.executionContext().bufferAllocator().newBuffer(capacity);
        writeRequestArraySize(buffer, len);
        RedisProtocolSupport.Command.MSET.encodeTo(buffer);
        writeRequestArgument(buffer, key1);
        writeRequestArgument(buffer, value1);
        writeRequestArgument(buffer, key2);
        writeRequestArgument(buffer, value2);
        final RedisRequest request = newRequest(RedisProtocolSupport.Command.MSET, buffer);
        final RedisPartitionAttributesBuilder partitionAttributesBuilder = partitionAttributesBuilderFunction
                    .apply(RedisProtocolSupport.Command.MSET);
        partitionAttributesBuilder.addKey(key1);
        partitionAttributesBuilder.addKey(key2);
        final Single<String> result = partitionedRedisClient.request(partitionAttributesBuilder.build(), request,
                    String.class);
        return result;
    }

    @Override
    public Single<String> mset(@RedisProtocolSupport.Key final Buffer key1, final Buffer value1,
                               @RedisProtocolSupport.Key final Buffer key2, final Buffer value2,
                               @RedisProtocolSupport.Key final Buffer key3, final Buffer value3) {
        requireNonNull(key1);
        requireNonNull(value1);
        requireNonNull(key2);
        requireNonNull(value2);
        requireNonNull(key3);
        requireNonNull(value3);
        final int len = 7;
        final int capacity = calculateInitialCommandBufferSize(len, RedisProtocolSupport.Command.MSET) +
                    calculateRequestArgumentSize(key1) + calculateRequestArgumentSize(value1) +
                    calculateRequestArgumentSize(key2) + calculateRequestArgumentSize(value2) +
                    calculateRequestArgumentSize(key3) + calculateRequestArgumentSize(value3);
        Buffer buffer = partitionedRedisClient.executionContext().bufferAllocator().newBuffer(capacity);
        writeRequestArraySize(buffer, len);
        RedisProtocolSupport.Command.MSET.encodeTo(buffer);
        writeRequestArgument(buffer, key1);
        writeRequestArgument(buffer, value1);
        writeRequestArgument(buffer, key2);
        writeRequestArgument(buffer, value2);
        writeRequestArgument(buffer, key3);
        writeRequestArgument(buffer, value3);
        final RedisRequest request = newRequest(RedisProtocolSupport.Command.MSET, buffer);
        final RedisPartitionAttributesBuilder partitionAttributesBuilder = partitionAttributesBuilderFunction
                    .apply(RedisProtocolSupport.Command.MSET);
        partitionAttributesBuilder.addKey(key1);
        partitionAttributesBuilder.addKey(key2);
        partitionAttributesBuilder.addKey(key3);
        final Single<String> result = partitionedRedisClient.request(partitionAttributesBuilder.build(), request,
                    String.class);
        return result;
    }

    @Override
    public Single<String> mset(final Collection<RedisProtocolSupport.BufferKeyValue> keyValues) {
        requireNonNull(keyValues);
        final int len = 1 + RedisProtocolSupport.BufferKeyValue.SIZE * keyValues.size();
        int keyValuesCapacity = 0;
        if (keyValues instanceof List && keyValues instanceof RandomAccess) {
            final List<RedisProtocolSupport.BufferKeyValue> list = (List<RedisProtocolSupport.BufferKeyValue>) keyValues;
            for (int i = 0; i < list.size(); ++i) {
                final RedisProtocolSupport.BufferKeyValue arg = list.get(i);
                keyValuesCapacity += arg.encodedByteCount();
            }
        } else {
            for (RedisProtocolSupport.BufferKeyValue arg : keyValues) {
                keyValuesCapacity += arg.encodedByteCount();
            }
        }
        final int capacity = calculateInitialCommandBufferSize(len, RedisProtocolSupport.Command.MSET) +
                    keyValuesCapacity;
        Buffer buffer = partitionedRedisClient.executionContext().bufferAllocator().newBuffer(capacity);
        writeRequestArraySize(buffer, len);
        RedisProtocolSupport.Command.MSET.encodeTo(buffer);
        if (keyValues instanceof List && keyValues instanceof RandomAccess) {
            final List<RedisProtocolSupport.BufferKeyValue> list = (List<RedisProtocolSupport.BufferKeyValue>) keyValues;
            for (int i = 0; i < list.size(); ++i) {
                final RedisProtocolSupport.BufferKeyValue arg = list.get(i);
                arg.encodeTo(buffer);
            }
        } else {
            for (RedisProtocolSupport.BufferKeyValue arg : keyValues) {
                arg.encodeTo(buffer);
            }
        }
        final RedisRequest request = newRequest(RedisProtocolSupport.Command.MSET, buffer);
        final RedisPartitionAttributesBuilder partitionAttributesBuilder = partitionAttributesBuilderFunction
                    .apply(RedisProtocolSupport.Command.MSET);
        final Single<String> result = partitionedRedisClient.request(partitionAttributesBuilder.build(), request,
                    String.class);
        return result;
    }

    @Override
    public Single<Long> msetnx(@RedisProtocolSupport.Key final Buffer key, final Buffer value) {
        requireNonNull(key);
        requireNonNull(value);
        final int len = 3;
        final int capacity = calculateInitialCommandBufferSize(len, RedisProtocolSupport.Command.MSETNX) +
                    calculateRequestArgumentSize(key) + calculateRequestArgumentSize(value);
        Buffer buffer = partitionedRedisClient.executionContext().bufferAllocator().newBuffer(capacity);
        writeRequestArraySize(buffer, len);
        RedisProtocolSupport.Command.MSETNX.encodeTo(buffer);
        writeRequestArgument(buffer, key);
        writeRequestArgument(buffer, value);
        final RedisRequest request = newRequest(RedisProtocolSupport.Command.MSETNX, buffer);
        final RedisPartitionAttributesBuilder partitionAttributesBuilder = partitionAttributesBuilderFunction
                    .apply(RedisProtocolSupport.Command.MSETNX);
        partitionAttributesBuilder.addKey(key);
        final Single<Long> result = partitionedRedisClient.request(partitionAttributesBuilder.build(), request,
                    Long.class);
        return result;
    }

    @Override
    public Single<Long> msetnx(@RedisProtocolSupport.Key final Buffer key1, final Buffer value1,
                               @RedisProtocolSupport.Key final Buffer key2, final Buffer value2) {
        requireNonNull(key1);
        requireNonNull(value1);
        requireNonNull(key2);
        requireNonNull(value2);
        final int len = 5;
        final int capacity = calculateInitialCommandBufferSize(len, RedisProtocolSupport.Command.MSETNX) +
                    calculateRequestArgumentSize(key1) + calculateRequestArgumentSize(value1) +
                    calculateRequestArgumentSize(key2) + calculateRequestArgumentSize(value2);
        Buffer buffer = partitionedRedisClient.executionContext().bufferAllocator().newBuffer(capacity);
        writeRequestArraySize(buffer, len);
        RedisProtocolSupport.Command.MSETNX.encodeTo(buffer);
        writeRequestArgument(buffer, key1);
        writeRequestArgument(buffer, value1);
        writeRequestArgument(buffer, key2);
        writeRequestArgument(buffer, value2);
        final RedisRequest request = newRequest(RedisProtocolSupport.Command.MSETNX, buffer);
        final RedisPartitionAttributesBuilder partitionAttributesBuilder = partitionAttributesBuilderFunction
                    .apply(RedisProtocolSupport.Command.MSETNX);
        partitionAttributesBuilder.addKey(key1);
        partitionAttributesBuilder.addKey(key2);
        final Single<Long> result = partitionedRedisClient.request(partitionAttributesBuilder.build(), request,
                    Long.class);
        return result;
    }

    @Override
    public Single<Long> msetnx(@RedisProtocolSupport.Key final Buffer key1, final Buffer value1,
                               @RedisProtocolSupport.Key final Buffer key2, final Buffer value2,
                               @RedisProtocolSupport.Key final Buffer key3, final Buffer value3) {
        requireNonNull(key1);
        requireNonNull(value1);
        requireNonNull(key2);
        requireNonNull(value2);
        requireNonNull(key3);
        requireNonNull(value3);
        final int len = 7;
        final int capacity = calculateInitialCommandBufferSize(len, RedisProtocolSupport.Command.MSETNX) +
                    calculateRequestArgumentSize(key1) + calculateRequestArgumentSize(value1) +
                    calculateRequestArgumentSize(key2) + calculateRequestArgumentSize(value2) +
                    calculateRequestArgumentSize(key3) + calculateRequestArgumentSize(value3);
        Buffer buffer = partitionedRedisClient.executionContext().bufferAllocator().newBuffer(capacity);
        writeRequestArraySize(buffer, len);
        RedisProtocolSupport.Command.MSETNX.encodeTo(buffer);
        writeRequestArgument(buffer, key1);
        writeRequestArgument(buffer, value1);
        writeRequestArgument(buffer, key2);
        writeRequestArgument(buffer, value2);
        writeRequestArgument(buffer, key3);
        writeRequestArgument(buffer, value3);
        final RedisRequest request = newRequest(RedisProtocolSupport.Command.MSETNX, buffer);
        final RedisPartitionAttributesBuilder partitionAttributesBuilder = partitionAttributesBuilderFunction
                    .apply(RedisProtocolSupport.Command.MSETNX);
        partitionAttributesBuilder.addKey(key1);
        partitionAttributesBuilder.addKey(key2);
        partitionAttributesBuilder.addKey(key3);
        final Single<Long> result = partitionedRedisClient.request(partitionAttributesBuilder.build(), request,
                    Long.class);
        return result;
    }

    @Override
    public Single<Long> msetnx(final Collection<RedisProtocolSupport.BufferKeyValue> keyValues) {
        requireNonNull(keyValues);
        final int len = 1 + RedisProtocolSupport.BufferKeyValue.SIZE * keyValues.size();
        int keyValuesCapacity = 0;
        if (keyValues instanceof List && keyValues instanceof RandomAccess) {
            final List<RedisProtocolSupport.BufferKeyValue> list = (List<RedisProtocolSupport.BufferKeyValue>) keyValues;
            for (int i = 0; i < list.size(); ++i) {
                final RedisProtocolSupport.BufferKeyValue arg = list.get(i);
                keyValuesCapacity += arg.encodedByteCount();
            }
        } else {
            for (RedisProtocolSupport.BufferKeyValue arg : keyValues) {
                keyValuesCapacity += arg.encodedByteCount();
            }
        }
        final int capacity = calculateInitialCommandBufferSize(len, RedisProtocolSupport.Command.MSETNX) +
                    keyValuesCapacity;
        Buffer buffer = partitionedRedisClient.executionContext().bufferAllocator().newBuffer(capacity);
        writeRequestArraySize(buffer, len);
        RedisProtocolSupport.Command.MSETNX.encodeTo(buffer);
        if (keyValues instanceof List && keyValues instanceof RandomAccess) {
            final List<RedisProtocolSupport.BufferKeyValue> list = (List<RedisProtocolSupport.BufferKeyValue>) keyValues;
            for (int i = 0; i < list.size(); ++i) {
                final RedisProtocolSupport.BufferKeyValue arg = list.get(i);
                arg.encodeTo(buffer);
            }
        } else {
            for (RedisProtocolSupport.BufferKeyValue arg : keyValues) {
                arg.encodeTo(buffer);
            }
        }
        final RedisRequest request = newRequest(RedisProtocolSupport.Command.MSETNX, buffer);
        final RedisPartitionAttributesBuilder partitionAttributesBuilder = partitionAttributesBuilderFunction
                    .apply(RedisProtocolSupport.Command.MSETNX);
        final Single<Long> result = partitionedRedisClient.request(partitionAttributesBuilder.build(), request,
                    Long.class);
        return result;
    }

    @Override
    public Single<TransactedBufferRedisCommander> multi() {
        final int len = 1;
        final int capacity = calculateInitialCommandBufferSize(len, RedisProtocolSupport.Command.MULTI);
        Buffer buffer = partitionedRedisClient.executionContext().bufferAllocator().newBuffer(capacity);
        writeRequestArraySize(buffer, len);
        RedisProtocolSupport.Command.MULTI.encodeTo(buffer);
        final RedisRequest request = newRequest(RedisProtocolSupport.Command.MULTI, buffer);
        final RedisPartitionAttributesBuilder partitionAttributesBuilder = partitionAttributesBuilderFunction
                    .apply(RedisProtocolSupport.Command.MULTI);
        return reserveConnection(partitionedRedisClient, partitionAttributesBuilder.build(), request,
                    RedisData.OK::equals, DefaultTransactedBufferRedisCommander::new);
    }

    @Override
    public Single<Buffer> objectEncoding(@RedisProtocolSupport.Key final Buffer key) {
        requireNonNull(key);
        final int len = 3;
        final int capacity = 1 + calculateInitialCommandBufferSize(len, RedisProtocolSupport.Command.OBJECT) +
                    calculateRequestArgumentSize(key);
        Buffer buffer = partitionedRedisClient.executionContext().bufferAllocator().newBuffer(capacity);
        writeRequestArraySize(buffer, len);
        RedisProtocolSupport.Command.OBJECT.encodeTo(buffer);
        RedisProtocolSupport.SubCommand.ENCODING.encodeTo(buffer);
        writeRequestArgument(buffer, key);
        final RedisRequest request = newRequest(RedisProtocolSupport.Command.OBJECT, buffer);
        final RedisPartitionAttributesBuilder partitionAttributesBuilder = partitionAttributesBuilderFunction
                    .apply(RedisProtocolSupport.Command.OBJECT);
        partitionAttributesBuilder.addKey(key);
        final Single<Buffer> result = partitionedRedisClient.request(partitionAttributesBuilder.build(), request,
                    Buffer.class);
        return result;
    }

    @Override
    public Single<Long> objectFreq(@RedisProtocolSupport.Key final Buffer key) {
        requireNonNull(key);
        final int len = 3;
        final int capacity = 1 + calculateInitialCommandBufferSize(len, RedisProtocolSupport.Command.OBJECT) +
                    calculateRequestArgumentSize(key);
        Buffer buffer = partitionedRedisClient.executionContext().bufferAllocator().newBuffer(capacity);
        writeRequestArraySize(buffer, len);
        RedisProtocolSupport.Command.OBJECT.encodeTo(buffer);
        RedisProtocolSupport.SubCommand.FREQ.encodeTo(buffer);
        writeRequestArgument(buffer, key);
        final RedisRequest request = newRequest(RedisProtocolSupport.Command.OBJECT, buffer);
        final RedisPartitionAttributesBuilder partitionAttributesBuilder = partitionAttributesBuilderFunction
                    .apply(RedisProtocolSupport.Command.OBJECT);
        partitionAttributesBuilder.addKey(key);
        final Single<Long> result = partitionedRedisClient.request(partitionAttributesBuilder.build(), request,
                    Long.class);
        return result;
    }

    @Override
    public Single<List<String>> objectHelp() {
        final int len = 2;
        final int capacity = 1 + calculateInitialCommandBufferSize(len, RedisProtocolSupport.Command.OBJECT);
        Buffer buffer = partitionedRedisClient.executionContext().bufferAllocator().newBuffer(capacity);
        writeRequestArraySize(buffer, len);
        RedisProtocolSupport.Command.OBJECT.encodeTo(buffer);
        RedisProtocolSupport.SubCommand.HELP.encodeTo(buffer);
        final RedisRequest request = newRequest(RedisProtocolSupport.Command.OBJECT, buffer);
        final RedisPartitionAttributesBuilder partitionAttributesBuilder = partitionAttributesBuilderFunction
                    .apply(RedisProtocolSupport.Command.OBJECT);
        final Single<List<String>> result = (Single) partitionedRedisClient.request(partitionAttributesBuilder.build(),
                    request, List.class);
        return result;
    }

    @Override
    public Single<Long> objectIdletime(@RedisProtocolSupport.Key final Buffer key) {
        requireNonNull(key);
        final int len = 3;
        final int capacity = 1 + calculateInitialCommandBufferSize(len, RedisProtocolSupport.Command.OBJECT) +
                    calculateRequestArgumentSize(key);
        Buffer buffer = partitionedRedisClient.executionContext().bufferAllocator().newBuffer(capacity);
        writeRequestArraySize(buffer, len);
        RedisProtocolSupport.Command.OBJECT.encodeTo(buffer);
        RedisProtocolSupport.SubCommand.IDLETIME.encodeTo(buffer);
        writeRequestArgument(buffer, key);
        final RedisRequest request = newRequest(RedisProtocolSupport.Command.OBJECT, buffer);
        final RedisPartitionAttributesBuilder partitionAttributesBuilder = partitionAttributesBuilderFunction
                    .apply(RedisProtocolSupport.Command.OBJECT);
        partitionAttributesBuilder.addKey(key);
        final Single<Long> result = partitionedRedisClient.request(partitionAttributesBuilder.build(), request,
                    Long.class);
        return result;
    }

    @Override
    public Single<Long> objectRefcount(@RedisProtocolSupport.Key final Buffer key) {
        requireNonNull(key);
        final int len = 3;
        final int capacity = 1 + calculateInitialCommandBufferSize(len, RedisProtocolSupport.Command.OBJECT) +
                    calculateRequestArgumentSize(key);
        Buffer buffer = partitionedRedisClient.executionContext().bufferAllocator().newBuffer(capacity);
        writeRequestArraySize(buffer, len);
        RedisProtocolSupport.Command.OBJECT.encodeTo(buffer);
        RedisProtocolSupport.SubCommand.REFCOUNT.encodeTo(buffer);
        writeRequestArgument(buffer, key);
        final RedisRequest request = newRequest(RedisProtocolSupport.Command.OBJECT, buffer);
        final RedisPartitionAttributesBuilder partitionAttributesBuilder = partitionAttributesBuilderFunction
                    .apply(RedisProtocolSupport.Command.OBJECT);
        partitionAttributesBuilder.addKey(key);
        final Single<Long> result = partitionedRedisClient.request(partitionAttributesBuilder.build(), request,
                    Long.class);
        return result;
    }

    @Override
    public Single<Long> persist(@RedisProtocolSupport.Key final Buffer key) {
        requireNonNull(key);
        final int len = 2;
        final int capacity = calculateInitialCommandBufferSize(len, RedisProtocolSupport.Command.PERSIST) +
                    calculateRequestArgumentSize(key);
        Buffer buffer = partitionedRedisClient.executionContext().bufferAllocator().newBuffer(capacity);
        writeRequestArraySize(buffer, len);
        RedisProtocolSupport.Command.PERSIST.encodeTo(buffer);
        writeRequestArgument(buffer, key);
        final RedisRequest request = newRequest(RedisProtocolSupport.Command.PERSIST, buffer);
        final RedisPartitionAttributesBuilder partitionAttributesBuilder = partitionAttributesBuilderFunction
                    .apply(RedisProtocolSupport.Command.PERSIST);
        partitionAttributesBuilder.addKey(key);
        final Single<Long> result = partitionedRedisClient.request(partitionAttributesBuilder.build(), request,
                    Long.class);
        return result;
    }

    @Override
    public Single<Long> pexpire(@RedisProtocolSupport.Key final Buffer key, final long milliseconds) {
        requireNonNull(key);
        final int len = 3;
        final int capacity = calculateInitialCommandBufferSize(len, RedisProtocolSupport.Command.PEXPIRE) +
                    calculateRequestArgumentSize(key) + calculateRequestArgumentSize(milliseconds);
        Buffer buffer = partitionedRedisClient.executionContext().bufferAllocator().newBuffer(capacity);
        writeRequestArraySize(buffer, len);
        RedisProtocolSupport.Command.PEXPIRE.encodeTo(buffer);
        writeRequestArgument(buffer, key);
        writeRequestArgument(buffer, milliseconds);
        final RedisRequest request = newRequest(RedisProtocolSupport.Command.PEXPIRE, buffer);
        final RedisPartitionAttributesBuilder partitionAttributesBuilder = partitionAttributesBuilderFunction
                    .apply(RedisProtocolSupport.Command.PEXPIRE);
        partitionAttributesBuilder.addKey(key);
        final Single<Long> result = partitionedRedisClient.request(partitionAttributesBuilder.build(), request,
                    Long.class);
        return result;
    }

    @Override
    public Single<Long> pexpireat(@RedisProtocolSupport.Key final Buffer key, final long millisecondsTimestamp) {
        requireNonNull(key);
        final int len = 3;
        final int capacity = calculateInitialCommandBufferSize(len, RedisProtocolSupport.Command.PEXPIREAT) +
                    calculateRequestArgumentSize(key) + calculateRequestArgumentSize(millisecondsTimestamp);
        Buffer buffer = partitionedRedisClient.executionContext().bufferAllocator().newBuffer(capacity);
        writeRequestArraySize(buffer, len);
        RedisProtocolSupport.Command.PEXPIREAT.encodeTo(buffer);
        writeRequestArgument(buffer, key);
        writeRequestArgument(buffer, millisecondsTimestamp);
        final RedisRequest request = newRequest(RedisProtocolSupport.Command.PEXPIREAT, buffer);
        final RedisPartitionAttributesBuilder partitionAttributesBuilder = partitionAttributesBuilderFunction
                    .apply(RedisProtocolSupport.Command.PEXPIREAT);
        partitionAttributesBuilder.addKey(key);
        final Single<Long> result = partitionedRedisClient.request(partitionAttributesBuilder.build(), request,
                    Long.class);
        return result;
    }

    @Override
    public Single<Long> pfadd(@RedisProtocolSupport.Key final Buffer key, final Buffer element) {
        requireNonNull(key);
        requireNonNull(element);
        final int len = 3;
        final int capacity = calculateInitialCommandBufferSize(len, RedisProtocolSupport.Command.PFADD) +
                    calculateRequestArgumentSize(key) + calculateRequestArgumentSize(element);
        Buffer buffer = partitionedRedisClient.executionContext().bufferAllocator().newBuffer(capacity);
        writeRequestArraySize(buffer, len);
        RedisProtocolSupport.Command.PFADD.encodeTo(buffer);
        writeRequestArgument(buffer, key);
        writeRequestArgument(buffer, element);
        final RedisRequest request = newRequest(RedisProtocolSupport.Command.PFADD, buffer);
        final RedisPartitionAttributesBuilder partitionAttributesBuilder = partitionAttributesBuilderFunction
                    .apply(RedisProtocolSupport.Command.PFADD);
        partitionAttributesBuilder.addKey(key);
        final Single<Long> result = partitionedRedisClient.request(partitionAttributesBuilder.build(), request,
                    Long.class);
        return result;
    }

    @Override
    public Single<Long> pfadd(@RedisProtocolSupport.Key final Buffer key, final Buffer element1,
                              final Buffer element2) {
        requireNonNull(key);
        requireNonNull(element1);
        requireNonNull(element2);
        final int len = 4;
        final int capacity = calculateInitialCommandBufferSize(len, RedisProtocolSupport.Command.PFADD) +
                    calculateRequestArgumentSize(key) + calculateRequestArgumentSize(element1) +
                    calculateRequestArgumentSize(element2);
        Buffer buffer = partitionedRedisClient.executionContext().bufferAllocator().newBuffer(capacity);
        writeRequestArraySize(buffer, len);
        RedisProtocolSupport.Command.PFADD.encodeTo(buffer);
        writeRequestArgument(buffer, key);
        writeRequestArgument(buffer, element1);
        writeRequestArgument(buffer, element2);
        final RedisRequest request = newRequest(RedisProtocolSupport.Command.PFADD, buffer);
        final RedisPartitionAttributesBuilder partitionAttributesBuilder = partitionAttributesBuilderFunction
                    .apply(RedisProtocolSupport.Command.PFADD);
        partitionAttributesBuilder.addKey(key);
        final Single<Long> result = partitionedRedisClient.request(partitionAttributesBuilder.build(), request,
                    Long.class);
        return result;
    }

    @Override
    public Single<Long> pfadd(@RedisProtocolSupport.Key final Buffer key, final Buffer element1, final Buffer element2,
                              final Buffer element3) {
        requireNonNull(key);
        requireNonNull(element1);
        requireNonNull(element2);
        requireNonNull(element3);
        final int len = 5;
        final int capacity = calculateInitialCommandBufferSize(len, RedisProtocolSupport.Command.PFADD) +
                    calculateRequestArgumentSize(key) + calculateRequestArgumentSize(element1) +
                    calculateRequestArgumentSize(element2) + calculateRequestArgumentSize(element3);
        Buffer buffer = partitionedRedisClient.executionContext().bufferAllocator().newBuffer(capacity);
        writeRequestArraySize(buffer, len);
        RedisProtocolSupport.Command.PFADD.encodeTo(buffer);
        writeRequestArgument(buffer, key);
        writeRequestArgument(buffer, element1);
        writeRequestArgument(buffer, element2);
        writeRequestArgument(buffer, element3);
        final RedisRequest request = newRequest(RedisProtocolSupport.Command.PFADD, buffer);
        final RedisPartitionAttributesBuilder partitionAttributesBuilder = partitionAttributesBuilderFunction
                    .apply(RedisProtocolSupport.Command.PFADD);
        partitionAttributesBuilder.addKey(key);
        final Single<Long> result = partitionedRedisClient.request(partitionAttributesBuilder.build(), request,
                    Long.class);
        return result;
    }

    @Override
    public Single<Long> pfadd(@RedisProtocolSupport.Key final Buffer key, final Collection<Buffer> elements) {
        requireNonNull(key);
        requireNonNull(elements);
        final int len = 2 + elements.size();
        int elementsCapacity = 0;
        if (elements instanceof List && elements instanceof RandomAccess) {
            final List<Buffer> list = (List<Buffer>) elements;
            for (int i = 0; i < list.size(); ++i) {
                final Buffer arg = list.get(i);
                elementsCapacity += calculateRequestArgumentSize(arg);
            }
        } else {
            for (Buffer arg : elements) {
                elementsCapacity += calculateRequestArgumentSize(arg);
            }
        }
        final int capacity = calculateInitialCommandBufferSize(len, RedisProtocolSupport.Command.PFADD) +
                    calculateRequestArgumentSize(key) + elementsCapacity;
        Buffer buffer = partitionedRedisClient.executionContext().bufferAllocator().newBuffer(capacity);
        writeRequestArraySize(buffer, len);
        RedisProtocolSupport.Command.PFADD.encodeTo(buffer);
        writeRequestArgument(buffer, key);
        if (elements instanceof List && elements instanceof RandomAccess) {
            final List<Buffer> list = (List<Buffer>) elements;
            for (int i = 0; i < list.size(); ++i) {
                final Buffer arg = list.get(i);
                writeRequestArgument(buffer, arg);
            }
        } else {
            for (Buffer arg : elements) {
                writeRequestArgument(buffer, arg);
            }
        }
        final RedisRequest request = newRequest(RedisProtocolSupport.Command.PFADD, buffer);
        final RedisPartitionAttributesBuilder partitionAttributesBuilder = partitionAttributesBuilderFunction
                    .apply(RedisProtocolSupport.Command.PFADD);
        partitionAttributesBuilder.addKey(key);
        final Single<Long> result = partitionedRedisClient.request(partitionAttributesBuilder.build(), request,
                    Long.class);
        return result;
    }

    @Override
    public Single<Long> pfcount(@RedisProtocolSupport.Key final Buffer key) {
        requireNonNull(key);
        final int len = 2;
        final int capacity = calculateInitialCommandBufferSize(len, RedisProtocolSupport.Command.PFCOUNT) +
                    calculateRequestArgumentSize(key);
        Buffer buffer = partitionedRedisClient.executionContext().bufferAllocator().newBuffer(capacity);
        writeRequestArraySize(buffer, len);
        RedisProtocolSupport.Command.PFCOUNT.encodeTo(buffer);
        writeRequestArgument(buffer, key);
        final RedisRequest request = newRequest(RedisProtocolSupport.Command.PFCOUNT, buffer);
        final RedisPartitionAttributesBuilder partitionAttributesBuilder = partitionAttributesBuilderFunction
                    .apply(RedisProtocolSupport.Command.PFCOUNT);
        partitionAttributesBuilder.addKey(key);
        final Single<Long> result = partitionedRedisClient.request(partitionAttributesBuilder.build(), request,
                    Long.class);
        return result;
    }

    @Override
    public Single<Long> pfcount(@RedisProtocolSupport.Key final Buffer key1,
                                @RedisProtocolSupport.Key final Buffer key2) {
        requireNonNull(key1);
        requireNonNull(key2);
        final int len = 3;
        final int capacity = calculateInitialCommandBufferSize(len, RedisProtocolSupport.Command.PFCOUNT) +
                    calculateRequestArgumentSize(key1) + calculateRequestArgumentSize(key2);
        Buffer buffer = partitionedRedisClient.executionContext().bufferAllocator().newBuffer(capacity);
        writeRequestArraySize(buffer, len);
        RedisProtocolSupport.Command.PFCOUNT.encodeTo(buffer);
        writeRequestArgument(buffer, key1);
        writeRequestArgument(buffer, key2);
        final RedisRequest request = newRequest(RedisProtocolSupport.Command.PFCOUNT, buffer);
        final RedisPartitionAttributesBuilder partitionAttributesBuilder = partitionAttributesBuilderFunction
                    .apply(RedisProtocolSupport.Command.PFCOUNT);
        partitionAttributesBuilder.addKey(key1);
        partitionAttributesBuilder.addKey(key2);
        final Single<Long> result = partitionedRedisClient.request(partitionAttributesBuilder.build(), request,
                    Long.class);
        return result;
    }

    @Override
    public Single<Long> pfcount(@RedisProtocolSupport.Key final Buffer key1,
                                @RedisProtocolSupport.Key final Buffer key2,
                                @RedisProtocolSupport.Key final Buffer key3) {
        requireNonNull(key1);
        requireNonNull(key2);
        requireNonNull(key3);
        final int len = 4;
        final int capacity = calculateInitialCommandBufferSize(len, RedisProtocolSupport.Command.PFCOUNT) +
                    calculateRequestArgumentSize(key1) + calculateRequestArgumentSize(key2) +
                    calculateRequestArgumentSize(key3);
        Buffer buffer = partitionedRedisClient.executionContext().bufferAllocator().newBuffer(capacity);
        writeRequestArraySize(buffer, len);
        RedisProtocolSupport.Command.PFCOUNT.encodeTo(buffer);
        writeRequestArgument(buffer, key1);
        writeRequestArgument(buffer, key2);
        writeRequestArgument(buffer, key3);
        final RedisRequest request = newRequest(RedisProtocolSupport.Command.PFCOUNT, buffer);
        final RedisPartitionAttributesBuilder partitionAttributesBuilder = partitionAttributesBuilderFunction
                    .apply(RedisProtocolSupport.Command.PFCOUNT);
        partitionAttributesBuilder.addKey(key1);
        partitionAttributesBuilder.addKey(key2);
        partitionAttributesBuilder.addKey(key3);
        final Single<Long> result = partitionedRedisClient.request(partitionAttributesBuilder.build(), request,
                    Long.class);
        return result;
    }

    @Override
    public Single<Long> pfcount(@RedisProtocolSupport.Key final Collection<Buffer> keys) {
        requireNonNull(keys);
        final int len = 1 + keys.size();
        int keysCapacity = 0;
        if (keys instanceof List && keys instanceof RandomAccess) {
            final List<Buffer> list = (List<Buffer>) keys;
            for (int i = 0; i < list.size(); ++i) {
                final Buffer arg = list.get(i);
                keysCapacity += calculateRequestArgumentSize(arg);
            }
        } else {
            for (Buffer arg : keys) {
                keysCapacity += calculateRequestArgumentSize(arg);
            }
        }
        final int capacity = calculateInitialCommandBufferSize(len, RedisProtocolSupport.Command.PFCOUNT) +
                    keysCapacity;
        Buffer buffer = partitionedRedisClient.executionContext().bufferAllocator().newBuffer(capacity);
        writeRequestArraySize(buffer, len);
        RedisProtocolSupport.Command.PFCOUNT.encodeTo(buffer);
        if (keys instanceof List && keys instanceof RandomAccess) {
            final List<Buffer> list = (List<Buffer>) keys;
            for (int i = 0; i < list.size(); ++i) {
                final Buffer arg = list.get(i);
                writeRequestArgument(buffer, arg);
            }
        } else {
            for (Buffer arg : keys) {
                writeRequestArgument(buffer, arg);
            }
        }
        final RedisRequest request = newRequest(RedisProtocolSupport.Command.PFCOUNT, buffer);
        final RedisPartitionAttributesBuilder partitionAttributesBuilder = partitionAttributesBuilderFunction
                    .apply(RedisProtocolSupport.Command.PFCOUNT);
        addBufferKeysToAttributeBuilder(keys, partitionAttributesBuilder);
        final Single<Long> result = partitionedRedisClient.request(partitionAttributesBuilder.build(), request,
                    Long.class);
        return result;
    }

    @Override
    public Single<String> pfmerge(@RedisProtocolSupport.Key final Buffer destkey,
                                  @RedisProtocolSupport.Key final Buffer sourcekey) {
        requireNonNull(destkey);
        requireNonNull(sourcekey);
        final int len = 3;
        final int capacity = calculateInitialCommandBufferSize(len, RedisProtocolSupport.Command.PFMERGE) +
                    calculateRequestArgumentSize(destkey) + calculateRequestArgumentSize(sourcekey);
        Buffer buffer = partitionedRedisClient.executionContext().bufferAllocator().newBuffer(capacity);
        writeRequestArraySize(buffer, len);
        RedisProtocolSupport.Command.PFMERGE.encodeTo(buffer);
        writeRequestArgument(buffer, destkey);
        writeRequestArgument(buffer, sourcekey);
        final RedisRequest request = newRequest(RedisProtocolSupport.Command.PFMERGE, buffer);
        final RedisPartitionAttributesBuilder partitionAttributesBuilder = partitionAttributesBuilderFunction
                    .apply(RedisProtocolSupport.Command.PFMERGE);
        partitionAttributesBuilder.addKey(destkey);
        partitionAttributesBuilder.addKey(sourcekey);
        final Single<String> result = partitionedRedisClient.request(partitionAttributesBuilder.build(), request,
                    String.class);
        return result;
    }

    @Override
    public Single<String> pfmerge(@RedisProtocolSupport.Key final Buffer destkey,
                                  @RedisProtocolSupport.Key final Buffer sourcekey1,
                                  @RedisProtocolSupport.Key final Buffer sourcekey2) {
        requireNonNull(destkey);
        requireNonNull(sourcekey1);
        requireNonNull(sourcekey2);
        final int len = 4;
        final int capacity = calculateInitialCommandBufferSize(len, RedisProtocolSupport.Command.PFMERGE) +
                    calculateRequestArgumentSize(destkey) + calculateRequestArgumentSize(sourcekey1) +
                    calculateRequestArgumentSize(sourcekey2);
        Buffer buffer = partitionedRedisClient.executionContext().bufferAllocator().newBuffer(capacity);
        writeRequestArraySize(buffer, len);
        RedisProtocolSupport.Command.PFMERGE.encodeTo(buffer);
        writeRequestArgument(buffer, destkey);
        writeRequestArgument(buffer, sourcekey1);
        writeRequestArgument(buffer, sourcekey2);
        final RedisRequest request = newRequest(RedisProtocolSupport.Command.PFMERGE, buffer);
        final RedisPartitionAttributesBuilder partitionAttributesBuilder = partitionAttributesBuilderFunction
                    .apply(RedisProtocolSupport.Command.PFMERGE);
        partitionAttributesBuilder.addKey(destkey);
        partitionAttributesBuilder.addKey(sourcekey1);
        partitionAttributesBuilder.addKey(sourcekey2);
        final Single<String> result = partitionedRedisClient.request(partitionAttributesBuilder.build(), request,
                    String.class);
        return result;
    }

    @Override
    public Single<String> pfmerge(@RedisProtocolSupport.Key final Buffer destkey,
                                  @RedisProtocolSupport.Key final Buffer sourcekey1,
                                  @RedisProtocolSupport.Key final Buffer sourcekey2,
                                  @RedisProtocolSupport.Key final Buffer sourcekey3) {
        requireNonNull(destkey);
        requireNonNull(sourcekey1);
        requireNonNull(sourcekey2);
        requireNonNull(sourcekey3);
        final int len = 5;
        final int capacity = calculateInitialCommandBufferSize(len, RedisProtocolSupport.Command.PFMERGE) +
                    calculateRequestArgumentSize(destkey) + calculateRequestArgumentSize(sourcekey1) +
                    calculateRequestArgumentSize(sourcekey2) + calculateRequestArgumentSize(sourcekey3);
        Buffer buffer = partitionedRedisClient.executionContext().bufferAllocator().newBuffer(capacity);
        writeRequestArraySize(buffer, len);
        RedisProtocolSupport.Command.PFMERGE.encodeTo(buffer);
        writeRequestArgument(buffer, destkey);
        writeRequestArgument(buffer, sourcekey1);
        writeRequestArgument(buffer, sourcekey2);
        writeRequestArgument(buffer, sourcekey3);
        final RedisRequest request = newRequest(RedisProtocolSupport.Command.PFMERGE, buffer);
        final RedisPartitionAttributesBuilder partitionAttributesBuilder = partitionAttributesBuilderFunction
                    .apply(RedisProtocolSupport.Command.PFMERGE);
        partitionAttributesBuilder.addKey(destkey);
        partitionAttributesBuilder.addKey(sourcekey1);
        partitionAttributesBuilder.addKey(sourcekey2);
        partitionAttributesBuilder.addKey(sourcekey3);
        final Single<String> result = partitionedRedisClient.request(partitionAttributesBuilder.build(), request,
                    String.class);
        return result;
    }

    @Override
    public Single<String> pfmerge(@RedisProtocolSupport.Key final Buffer destkey,
                                  @RedisProtocolSupport.Key final Collection<Buffer> sourcekeys) {
        requireNonNull(destkey);
        requireNonNull(sourcekeys);
        final int len = 2 + sourcekeys.size();
        int sourcekeysCapacity = 0;
        if (sourcekeys instanceof List && sourcekeys instanceof RandomAccess) {
            final List<Buffer> list = (List<Buffer>) sourcekeys;
            for (int i = 0; i < list.size(); ++i) {
                final Buffer arg = list.get(i);
                sourcekeysCapacity += calculateRequestArgumentSize(arg);
            }
        } else {
            for (Buffer arg : sourcekeys) {
                sourcekeysCapacity += calculateRequestArgumentSize(arg);
            }
        }
        final int capacity = calculateInitialCommandBufferSize(len, RedisProtocolSupport.Command.PFMERGE) +
                    calculateRequestArgumentSize(destkey) + sourcekeysCapacity;
        Buffer buffer = partitionedRedisClient.executionContext().bufferAllocator().newBuffer(capacity);
        writeRequestArraySize(buffer, len);
        RedisProtocolSupport.Command.PFMERGE.encodeTo(buffer);
        writeRequestArgument(buffer, destkey);
        if (sourcekeys instanceof List && sourcekeys instanceof RandomAccess) {
            final List<Buffer> list = (List<Buffer>) sourcekeys;
            for (int i = 0; i < list.size(); ++i) {
                final Buffer arg = list.get(i);
                writeRequestArgument(buffer, arg);
            }
        } else {
            for (Buffer arg : sourcekeys) {
                writeRequestArgument(buffer, arg);
            }
        }
        final RedisRequest request = newRequest(RedisProtocolSupport.Command.PFMERGE, buffer);
        final RedisPartitionAttributesBuilder partitionAttributesBuilder = partitionAttributesBuilderFunction
                    .apply(RedisProtocolSupport.Command.PFMERGE);
        partitionAttributesBuilder.addKey(destkey);
        addBufferKeysToAttributeBuilder(sourcekeys, partitionAttributesBuilder);
        final Single<String> result = partitionedRedisClient.request(partitionAttributesBuilder.build(), request,
                    String.class);
        return result;
    }

    @Override
    public Single<String> ping() {
        final int len = 1;
        final int capacity = calculateInitialCommandBufferSize(len, RedisProtocolSupport.Command.PING);
        Buffer buffer = partitionedRedisClient.executionContext().bufferAllocator().newBuffer(capacity);
        writeRequestArraySize(buffer, len);
        RedisProtocolSupport.Command.PING.encodeTo(buffer);
        final RedisRequest request = newRequest(RedisProtocolSupport.Command.PING, buffer);
        final RedisPartitionAttributesBuilder partitionAttributesBuilder = partitionAttributesBuilderFunction
                    .apply(RedisProtocolSupport.Command.PING);
        final Single<String> result = partitionedRedisClient.request(partitionAttributesBuilder.build(), request,
                    String.class);
        return result;
    }

    @Override
    public Single<Buffer> ping(final Buffer message) {
        requireNonNull(message);
        final int len = 2;
        final int capacity = calculateInitialCommandBufferSize(len, RedisProtocolSupport.Command.PING) +
                    calculateRequestArgumentSize(message);
        Buffer buffer = partitionedRedisClient.executionContext().bufferAllocator().newBuffer(capacity);
        writeRequestArraySize(buffer, len);
        RedisProtocolSupport.Command.PING.encodeTo(buffer);
        writeRequestArgument(buffer, message);
        final RedisRequest request = newRequest(RedisProtocolSupport.Command.PING, buffer);
        final RedisPartitionAttributesBuilder partitionAttributesBuilder = partitionAttributesBuilderFunction
                    .apply(RedisProtocolSupport.Command.PING);
        final Single<Buffer> result = partitionedRedisClient.request(partitionAttributesBuilder.build(), request,
                    Buffer.class);
        return result;
    }

    @Override
    public Single<String> psetex(@RedisProtocolSupport.Key final Buffer key, final long milliseconds,
                                 final Buffer value) {
        requireNonNull(key);
        requireNonNull(value);
        final int len = 4;
        final int capacity = calculateInitialCommandBufferSize(len, RedisProtocolSupport.Command.PSETEX) +
                    calculateRequestArgumentSize(key) + calculateRequestArgumentSize(milliseconds) +
                    calculateRequestArgumentSize(value);
        Buffer buffer = partitionedRedisClient.executionContext().bufferAllocator().newBuffer(capacity);
        writeRequestArraySize(buffer, len);
        RedisProtocolSupport.Command.PSETEX.encodeTo(buffer);
        writeRequestArgument(buffer, key);
        writeRequestArgument(buffer, milliseconds);
        writeRequestArgument(buffer, value);
        final RedisRequest request = newRequest(RedisProtocolSupport.Command.PSETEX, buffer);
        final RedisPartitionAttributesBuilder partitionAttributesBuilder = partitionAttributesBuilderFunction
                    .apply(RedisProtocolSupport.Command.PSETEX);
        partitionAttributesBuilder.addKey(key);
        final Single<String> result = partitionedRedisClient.request(partitionAttributesBuilder.build(), request,
                    String.class);
        return result;
    }

    @Override
    public Single<PubSubBufferRedisConnection> psubscribe(final Buffer pattern) {
        requireNonNull(pattern);
        final int len = 2;
        final int capacity = calculateInitialCommandBufferSize(len, RedisProtocolSupport.Command.PSUBSCRIBE) +
                    calculateRequestArgumentSize(pattern);
        Buffer buffer = partitionedRedisClient.executionContext().bufferAllocator().newBuffer(capacity);
        writeRequestArraySize(buffer, len);
        RedisProtocolSupport.Command.PSUBSCRIBE.encodeTo(buffer);
        writeRequestArgument(buffer, pattern);
        final RedisRequest request = newRequest(RedisProtocolSupport.Command.PSUBSCRIBE, buffer);
        final RedisPartitionAttributesBuilder partitionAttributesBuilder = partitionAttributesBuilderFunction
                    .apply(RedisProtocolSupport.Command.PSUBSCRIBE);
        return reserveConnection(partitionedRedisClient, partitionAttributesBuilder.build(), request,
                    (rcnx, pub) -> new DefaultPubSubBufferRedisConnection(rcnx,
                                pub.map(msg -> (PubSubRedisMessage) msg)));
    }

    @Override
    public Single<Long> pttl(@RedisProtocolSupport.Key final Buffer key) {
        requireNonNull(key);
        final int len = 2;
        final int capacity = calculateInitialCommandBufferSize(len, RedisProtocolSupport.Command.PTTL) +
                    calculateRequestArgumentSize(key);
        Buffer buffer = partitionedRedisClient.executionContext().bufferAllocator().newBuffer(capacity);
        writeRequestArraySize(buffer, len);
        RedisProtocolSupport.Command.PTTL.encodeTo(buffer);
        writeRequestArgument(buffer, key);
        final RedisRequest request = newRequest(RedisProtocolSupport.Command.PTTL, buffer);
        final RedisPartitionAttributesBuilder partitionAttributesBuilder = partitionAttributesBuilderFunction
                    .apply(RedisProtocolSupport.Command.PTTL);
        partitionAttributesBuilder.addKey(key);
        final Single<Long> result = partitionedRedisClient.request(partitionAttributesBuilder.build(), request,
                    Long.class);
        return result;
    }

    @Override
    public Single<Long> publish(final Buffer channel, final Buffer message) {
        requireNonNull(channel);
        requireNonNull(message);
        final int len = 3;
        final int capacity = calculateInitialCommandBufferSize(len, RedisProtocolSupport.Command.PUBLISH) +
                    calculateRequestArgumentSize(channel) + calculateRequestArgumentSize(message);
        Buffer buffer = partitionedRedisClient.executionContext().bufferAllocator().newBuffer(capacity);
        writeRequestArraySize(buffer, len);
        RedisProtocolSupport.Command.PUBLISH.encodeTo(buffer);
        writeRequestArgument(buffer, channel);
        writeRequestArgument(buffer, message);
        final RedisRequest request = newRequest(RedisProtocolSupport.Command.PUBLISH, buffer);
        final RedisPartitionAttributesBuilder partitionAttributesBuilder = partitionAttributesBuilderFunction
                    .apply(RedisProtocolSupport.Command.PUBLISH);
        final Single<Long> result = partitionedRedisClient.request(partitionAttributesBuilder.build(), request,
                    Long.class);
        return result;
    }

    @Override
    public Single<List<String>> pubsubChannels() {
        final int len = 2;
        final int capacity = 1 + calculateInitialCommandBufferSize(len, RedisProtocolSupport.Command.PUBSUB);
        Buffer buffer = partitionedRedisClient.executionContext().bufferAllocator().newBuffer(capacity);
        writeRequestArraySize(buffer, len);
        RedisProtocolSupport.Command.PUBSUB.encodeTo(buffer);
        RedisProtocolSupport.SubCommand.CHANNELS.encodeTo(buffer);
        final RedisRequest request = newRequest(RedisProtocolSupport.Command.PUBSUB, buffer);
        final RedisPartitionAttributesBuilder partitionAttributesBuilder = partitionAttributesBuilderFunction
                    .apply(RedisProtocolSupport.Command.PUBSUB);
        final Single<List<String>> result = (Single) partitionedRedisClient.request(partitionAttributesBuilder.build(),
                    request, List.class);
        return result;
    }

    @Override
    public Single<List<String>> pubsubChannels(@Nullable final Buffer pattern) {
        final int len = 2 + (pattern == null ? 0 : 1);
        final int capacity = 1 + calculateInitialCommandBufferSize(len, RedisProtocolSupport.Command.PUBSUB) +
                    (pattern == null ? 0 : calculateRequestArgumentSize(pattern));
        Buffer buffer = partitionedRedisClient.executionContext().bufferAllocator().newBuffer(capacity);
        writeRequestArraySize(buffer, len);
        RedisProtocolSupport.Command.PUBSUB.encodeTo(buffer);
        RedisProtocolSupport.SubCommand.CHANNELS.encodeTo(buffer);
        if (pattern != null) {
            writeRequestArgument(buffer, pattern);
        }
        final RedisRequest request = newRequest(RedisProtocolSupport.Command.PUBSUB, buffer);
        final RedisPartitionAttributesBuilder partitionAttributesBuilder = partitionAttributesBuilderFunction
                    .apply(RedisProtocolSupport.Command.PUBSUB);
        final Single<List<String>> result = (Single) partitionedRedisClient.request(partitionAttributesBuilder.build(),
                    request, List.class);
        return result;
    }

    @Override
    public Single<List<String>> pubsubChannels(@Nullable final Buffer pattern1, @Nullable final Buffer pattern2) {
        final int len = 2 + (pattern1 == null ? 0 : 1) + (pattern2 == null ? 0 : 1);
        final int capacity = 1 + calculateInitialCommandBufferSize(len, RedisProtocolSupport.Command.PUBSUB) +
                    (pattern1 == null ? 0 : calculateRequestArgumentSize(pattern1)) +
                    (pattern2 == null ? 0 : calculateRequestArgumentSize(pattern2));
        Buffer buffer = partitionedRedisClient.executionContext().bufferAllocator().newBuffer(capacity);
        writeRequestArraySize(buffer, len);
        RedisProtocolSupport.Command.PUBSUB.encodeTo(buffer);
        RedisProtocolSupport.SubCommand.CHANNELS.encodeTo(buffer);
        if (pattern1 != null) {
            writeRequestArgument(buffer, pattern1);
        }
        if (pattern2 != null) {
            writeRequestArgument(buffer, pattern2);
        }
        final RedisRequest request = newRequest(RedisProtocolSupport.Command.PUBSUB, buffer);
        final RedisPartitionAttributesBuilder partitionAttributesBuilder = partitionAttributesBuilderFunction
                    .apply(RedisProtocolSupport.Command.PUBSUB);
        final Single<List<String>> result = (Single) partitionedRedisClient.request(partitionAttributesBuilder.build(),
                    request, List.class);
        return result;
    }

    @Override
    public Single<List<String>> pubsubChannels(@Nullable final Buffer pattern1, @Nullable final Buffer pattern2,
                                               @Nullable final Buffer pattern3) {
        final int len = 2 + (pattern1 == null ? 0 : 1) + (pattern2 == null ? 0 : 1) + (pattern3 == null ? 0 : 1);
        final int capacity = 1 + calculateInitialCommandBufferSize(len, RedisProtocolSupport.Command.PUBSUB) +
                    (pattern1 == null ? 0 : calculateRequestArgumentSize(pattern1)) +
                    (pattern2 == null ? 0 : calculateRequestArgumentSize(pattern2)) +
                    (pattern3 == null ? 0 : calculateRequestArgumentSize(pattern3));
        Buffer buffer = partitionedRedisClient.executionContext().bufferAllocator().newBuffer(capacity);
        writeRequestArraySize(buffer, len);
        RedisProtocolSupport.Command.PUBSUB.encodeTo(buffer);
        RedisProtocolSupport.SubCommand.CHANNELS.encodeTo(buffer);
        if (pattern1 != null) {
            writeRequestArgument(buffer, pattern1);
        }
        if (pattern2 != null) {
            writeRequestArgument(buffer, pattern2);
        }
        if (pattern3 != null) {
            writeRequestArgument(buffer, pattern3);
        }
        final RedisRequest request = newRequest(RedisProtocolSupport.Command.PUBSUB, buffer);
        final RedisPartitionAttributesBuilder partitionAttributesBuilder = partitionAttributesBuilderFunction
                    .apply(RedisProtocolSupport.Command.PUBSUB);
        final Single<List<String>> result = (Single) partitionedRedisClient.request(partitionAttributesBuilder.build(),
                    request, List.class);
        return result;
    }

    @Override
    public Single<List<String>> pubsubChannels(final Collection<Buffer> patterns) {
        requireNonNull(patterns);
        final int len = 2 + patterns.size();
        int patternsCapacity = 0;
        if (patterns instanceof List && patterns instanceof RandomAccess) {
            final List<Buffer> list = (List<Buffer>) patterns;
            for (int i = 0; i < list.size(); ++i) {
                final Buffer arg = list.get(i);
                patternsCapacity += calculateRequestArgumentSize(arg);
            }
        } else {
            for (Buffer arg : patterns) {
                patternsCapacity += calculateRequestArgumentSize(arg);
            }
        }
        final int capacity = 1 + calculateInitialCommandBufferSize(len, RedisProtocolSupport.Command.PUBSUB) +
                    patternsCapacity;
        Buffer buffer = partitionedRedisClient.executionContext().bufferAllocator().newBuffer(capacity);
        writeRequestArraySize(buffer, len);
        RedisProtocolSupport.Command.PUBSUB.encodeTo(buffer);
        RedisProtocolSupport.SubCommand.CHANNELS.encodeTo(buffer);
        if (patterns instanceof List && patterns instanceof RandomAccess) {
            final List<Buffer> list = (List<Buffer>) patterns;
            for (int i = 0; i < list.size(); ++i) {
                final Buffer arg = list.get(i);
                writeRequestArgument(buffer, arg);
            }
        } else {
            for (Buffer arg : patterns) {
                writeRequestArgument(buffer, arg);
            }
        }
        final RedisRequest request = newRequest(RedisProtocolSupport.Command.PUBSUB, buffer);
        final RedisPartitionAttributesBuilder partitionAttributesBuilder = partitionAttributesBuilderFunction
                    .apply(RedisProtocolSupport.Command.PUBSUB);
        final Single<List<String>> result = (Single) partitionedRedisClient.request(partitionAttributesBuilder.build(),
                    request, List.class);
        return result;
    }

    @Override
    public <T> Single<List<T>> pubsubNumsub() {
        final int len = 2;
        final int capacity = 1 + calculateInitialCommandBufferSize(len, RedisProtocolSupport.Command.PUBSUB);
        Buffer buffer = partitionedRedisClient.executionContext().bufferAllocator().newBuffer(capacity);
        writeRequestArraySize(buffer, len);
        RedisProtocolSupport.Command.PUBSUB.encodeTo(buffer);
        RedisProtocolSupport.SubCommand.NUMSUB.encodeTo(buffer);
        final RedisRequest request = newRequest(RedisProtocolSupport.Command.PUBSUB, buffer);
        final RedisPartitionAttributesBuilder partitionAttributesBuilder = partitionAttributesBuilderFunction
                    .apply(RedisProtocolSupport.Command.PUBSUB);
        final Single<List<T>> result = (Single) partitionedRedisClient.request(partitionAttributesBuilder.build(),
                    request, List.class);
        return result;
    }

    @Override
    public <T> Single<List<T>> pubsubNumsub(@Nullable final Buffer channel) {
        final int len = 2 + (channel == null ? 0 : 1);
        final int capacity = 1 + calculateInitialCommandBufferSize(len, RedisProtocolSupport.Command.PUBSUB) +
                    (channel == null ? 0 : calculateRequestArgumentSize(channel));
        Buffer buffer = partitionedRedisClient.executionContext().bufferAllocator().newBuffer(capacity);
        writeRequestArraySize(buffer, len);
        RedisProtocolSupport.Command.PUBSUB.encodeTo(buffer);
        RedisProtocolSupport.SubCommand.NUMSUB.encodeTo(buffer);
        if (channel != null) {
            writeRequestArgument(buffer, channel);
        }
        final RedisRequest request = newRequest(RedisProtocolSupport.Command.PUBSUB, buffer);
        final RedisPartitionAttributesBuilder partitionAttributesBuilder = partitionAttributesBuilderFunction
                    .apply(RedisProtocolSupport.Command.PUBSUB);
        final Single<List<T>> result = (Single) partitionedRedisClient.request(partitionAttributesBuilder.build(),
                    request, List.class);
        return result;
    }

    @Override
    public <T> Single<List<T>> pubsubNumsub(@Nullable final Buffer channel1, @Nullable final Buffer channel2) {
        final int len = 2 + (channel1 == null ? 0 : 1) + (channel2 == null ? 0 : 1);
        final int capacity = 1 + calculateInitialCommandBufferSize(len, RedisProtocolSupport.Command.PUBSUB) +
                    (channel1 == null ? 0 : calculateRequestArgumentSize(channel1)) +
                    (channel2 == null ? 0 : calculateRequestArgumentSize(channel2));
        Buffer buffer = partitionedRedisClient.executionContext().bufferAllocator().newBuffer(capacity);
        writeRequestArraySize(buffer, len);
        RedisProtocolSupport.Command.PUBSUB.encodeTo(buffer);
        RedisProtocolSupport.SubCommand.NUMSUB.encodeTo(buffer);
        if (channel1 != null) {
            writeRequestArgument(buffer, channel1);
        }
        if (channel2 != null) {
            writeRequestArgument(buffer, channel2);
        }
        final RedisRequest request = newRequest(RedisProtocolSupport.Command.PUBSUB, buffer);
        final RedisPartitionAttributesBuilder partitionAttributesBuilder = partitionAttributesBuilderFunction
                    .apply(RedisProtocolSupport.Command.PUBSUB);
        final Single<List<T>> result = (Single) partitionedRedisClient.request(partitionAttributesBuilder.build(),
                    request, List.class);
        return result;
    }

    @Override
    public <T> Single<List<T>> pubsubNumsub(@Nullable final Buffer channel1, @Nullable final Buffer channel2,
                                            @Nullable final Buffer channel3) {
        final int len = 2 + (channel1 == null ? 0 : 1) + (channel2 == null ? 0 : 1) + (channel3 == null ? 0 : 1);
        final int capacity = 1 + calculateInitialCommandBufferSize(len, RedisProtocolSupport.Command.PUBSUB) +
                    (channel1 == null ? 0 : calculateRequestArgumentSize(channel1)) +
                    (channel2 == null ? 0 : calculateRequestArgumentSize(channel2)) +
                    (channel3 == null ? 0 : calculateRequestArgumentSize(channel3));
        Buffer buffer = partitionedRedisClient.executionContext().bufferAllocator().newBuffer(capacity);
        writeRequestArraySize(buffer, len);
        RedisProtocolSupport.Command.PUBSUB.encodeTo(buffer);
        RedisProtocolSupport.SubCommand.NUMSUB.encodeTo(buffer);
        if (channel1 != null) {
            writeRequestArgument(buffer, channel1);
        }
        if (channel2 != null) {
            writeRequestArgument(buffer, channel2);
        }
        if (channel3 != null) {
            writeRequestArgument(buffer, channel3);
        }
        final RedisRequest request = newRequest(RedisProtocolSupport.Command.PUBSUB, buffer);
        final RedisPartitionAttributesBuilder partitionAttributesBuilder = partitionAttributesBuilderFunction
                    .apply(RedisProtocolSupport.Command.PUBSUB);
        final Single<List<T>> result = (Single) partitionedRedisClient.request(partitionAttributesBuilder.build(),
                    request, List.class);
        return result;
    }

    @Override
    public <T> Single<List<T>> pubsubNumsub(final Collection<Buffer> channels) {
        requireNonNull(channels);
        final int len = 2 + channels.size();
        int channelsCapacity = 0;
        if (channels instanceof List && channels instanceof RandomAccess) {
            final List<Buffer> list = (List<Buffer>) channels;
            for (int i = 0; i < list.size(); ++i) {
                final Buffer arg = list.get(i);
                channelsCapacity += calculateRequestArgumentSize(arg);
            }
        } else {
            for (Buffer arg : channels) {
                channelsCapacity += calculateRequestArgumentSize(arg);
            }
        }
        final int capacity = 1 + calculateInitialCommandBufferSize(len, RedisProtocolSupport.Command.PUBSUB) +
                    channelsCapacity;
        Buffer buffer = partitionedRedisClient.executionContext().bufferAllocator().newBuffer(capacity);
        writeRequestArraySize(buffer, len);
        RedisProtocolSupport.Command.PUBSUB.encodeTo(buffer);
        RedisProtocolSupport.SubCommand.NUMSUB.encodeTo(buffer);
        if (channels instanceof List && channels instanceof RandomAccess) {
            final List<Buffer> list = (List<Buffer>) channels;
            for (int i = 0; i < list.size(); ++i) {
                final Buffer arg = list.get(i);
                writeRequestArgument(buffer, arg);
            }
        } else {
            for (Buffer arg : channels) {
                writeRequestArgument(buffer, arg);
            }
        }
        final RedisRequest request = newRequest(RedisProtocolSupport.Command.PUBSUB, buffer);
        final RedisPartitionAttributesBuilder partitionAttributesBuilder = partitionAttributesBuilderFunction
                    .apply(RedisProtocolSupport.Command.PUBSUB);
        final Single<List<T>> result = (Single) partitionedRedisClient.request(partitionAttributesBuilder.build(),
                    request, List.class);
        return result;
    }

    @Override
    public Single<Long> pubsubNumpat() {
        final int len = 2;
        final int capacity = 1 + calculateInitialCommandBufferSize(len, RedisProtocolSupport.Command.PUBSUB);
        Buffer buffer = partitionedRedisClient.executionContext().bufferAllocator().newBuffer(capacity);
        writeRequestArraySize(buffer, len);
        RedisProtocolSupport.Command.PUBSUB.encodeTo(buffer);
        RedisProtocolSupport.SubCommand.NUMPAT.encodeTo(buffer);
        final RedisRequest request = newRequest(RedisProtocolSupport.Command.PUBSUB, buffer);
        final RedisPartitionAttributesBuilder partitionAttributesBuilder = partitionAttributesBuilderFunction
                    .apply(RedisProtocolSupport.Command.PUBSUB);
        final Single<Long> result = partitionedRedisClient.request(partitionAttributesBuilder.build(), request,
                    Long.class);
        return result;
    }

    @Override
    public Single<Buffer> randomkey() {
        final int len = 1;
        final int capacity = calculateInitialCommandBufferSize(len, RedisProtocolSupport.Command.RANDOMKEY);
        Buffer buffer = partitionedRedisClient.executionContext().bufferAllocator().newBuffer(capacity);
        writeRequestArraySize(buffer, len);
        RedisProtocolSupport.Command.RANDOMKEY.encodeTo(buffer);
        final RedisRequest request = newRequest(RedisProtocolSupport.Command.RANDOMKEY, buffer);
        final RedisPartitionAttributesBuilder partitionAttributesBuilder = partitionAttributesBuilderFunction
                    .apply(RedisProtocolSupport.Command.RANDOMKEY);
        final Single<Buffer> result = partitionedRedisClient.request(partitionAttributesBuilder.build(), request,
                    Buffer.class);
        return result;
    }

    @Override
    public Single<String> readonly() {
        final int len = 1;
        final int capacity = calculateInitialCommandBufferSize(len, RedisProtocolSupport.Command.READONLY);
        Buffer buffer = partitionedRedisClient.executionContext().bufferAllocator().newBuffer(capacity);
        writeRequestArraySize(buffer, len);
        RedisProtocolSupport.Command.READONLY.encodeTo(buffer);
        final RedisRequest request = newRequest(RedisProtocolSupport.Command.READONLY, buffer);
        final RedisPartitionAttributesBuilder partitionAttributesBuilder = partitionAttributesBuilderFunction
                    .apply(RedisProtocolSupport.Command.READONLY);
        final Single<String> result = partitionedRedisClient.request(partitionAttributesBuilder.build(), request,
                    String.class);
        return result;
    }

    @Override
    public Single<String> readwrite() {
        final int len = 1;
        final int capacity = calculateInitialCommandBufferSize(len, RedisProtocolSupport.Command.READWRITE);
        Buffer buffer = partitionedRedisClient.executionContext().bufferAllocator().newBuffer(capacity);
        writeRequestArraySize(buffer, len);
        RedisProtocolSupport.Command.READWRITE.encodeTo(buffer);
        final RedisRequest request = newRequest(RedisProtocolSupport.Command.READWRITE, buffer);
        final RedisPartitionAttributesBuilder partitionAttributesBuilder = partitionAttributesBuilderFunction
                    .apply(RedisProtocolSupport.Command.READWRITE);
        final Single<String> result = partitionedRedisClient.request(partitionAttributesBuilder.build(), request,
                    String.class);
        return result;
    }

    @Override
    public Single<String> rename(@RedisProtocolSupport.Key final Buffer key,
                                 @RedisProtocolSupport.Key final Buffer newkey) {
        requireNonNull(key);
        requireNonNull(newkey);
        final int len = 3;
        final int capacity = calculateInitialCommandBufferSize(len, RedisProtocolSupport.Command.RENAME) +
                    calculateRequestArgumentSize(key) + calculateRequestArgumentSize(newkey);
        Buffer buffer = partitionedRedisClient.executionContext().bufferAllocator().newBuffer(capacity);
        writeRequestArraySize(buffer, len);
        RedisProtocolSupport.Command.RENAME.encodeTo(buffer);
        writeRequestArgument(buffer, key);
        writeRequestArgument(buffer, newkey);
        final RedisRequest request = newRequest(RedisProtocolSupport.Command.RENAME, buffer);
        final RedisPartitionAttributesBuilder partitionAttributesBuilder = partitionAttributesBuilderFunction
                    .apply(RedisProtocolSupport.Command.RENAME);
        partitionAttributesBuilder.addKey(key);
        partitionAttributesBuilder.addKey(newkey);
        final Single<String> result = partitionedRedisClient.request(partitionAttributesBuilder.build(), request,
                    String.class);
        return result;
    }

    @Override
    public Single<Long> renamenx(@RedisProtocolSupport.Key final Buffer key,
                                 @RedisProtocolSupport.Key final Buffer newkey) {
        requireNonNull(key);
        requireNonNull(newkey);
        final int len = 3;
        final int capacity = calculateInitialCommandBufferSize(len, RedisProtocolSupport.Command.RENAMENX) +
                    calculateRequestArgumentSize(key) + calculateRequestArgumentSize(newkey);
        Buffer buffer = partitionedRedisClient.executionContext().bufferAllocator().newBuffer(capacity);
        writeRequestArraySize(buffer, len);
        RedisProtocolSupport.Command.RENAMENX.encodeTo(buffer);
        writeRequestArgument(buffer, key);
        writeRequestArgument(buffer, newkey);
        final RedisRequest request = newRequest(RedisProtocolSupport.Command.RENAMENX, buffer);
        final RedisPartitionAttributesBuilder partitionAttributesBuilder = partitionAttributesBuilderFunction
                    .apply(RedisProtocolSupport.Command.RENAMENX);
        partitionAttributesBuilder.addKey(key);
        partitionAttributesBuilder.addKey(newkey);
        final Single<Long> result = partitionedRedisClient.request(partitionAttributesBuilder.build(), request,
                    Long.class);
        return result;
    }

    @Override
    public Single<String> restore(@RedisProtocolSupport.Key final Buffer key, final long ttl,
                                  final Buffer serializedValue) {
        requireNonNull(key);
        requireNonNull(serializedValue);
        final int len = 4;
        final int capacity = calculateInitialCommandBufferSize(len, RedisProtocolSupport.Command.RESTORE) +
                    calculateRequestArgumentSize(key) + calculateRequestArgumentSize(ttl) +
                    calculateRequestArgumentSize(serializedValue);
        Buffer buffer = partitionedRedisClient.executionContext().bufferAllocator().newBuffer(capacity);
        writeRequestArraySize(buffer, len);
        RedisProtocolSupport.Command.RESTORE.encodeTo(buffer);
        writeRequestArgument(buffer, key);
        writeRequestArgument(buffer, ttl);
        writeRequestArgument(buffer, serializedValue);
        final RedisRequest request = newRequest(RedisProtocolSupport.Command.RESTORE, buffer);
        final RedisPartitionAttributesBuilder partitionAttributesBuilder = partitionAttributesBuilderFunction
                    .apply(RedisProtocolSupport.Command.RESTORE);
        partitionAttributesBuilder.addKey(key);
        final Single<String> result = partitionedRedisClient.request(partitionAttributesBuilder.build(), request,
                    String.class);
        return result;
    }

    @Override
    public Single<String> restore(@RedisProtocolSupport.Key final Buffer key, final long ttl,
                                  final Buffer serializedValue,
                                  @Nullable final RedisProtocolSupport.RestoreReplace replace) {
        requireNonNull(key);
        requireNonNull(serializedValue);
        final int len = 4 + (replace == null ? 0 : 1);
        final int capacity = calculateInitialCommandBufferSize(len, RedisProtocolSupport.Command.RESTORE) +
                    calculateRequestArgumentSize(key) + calculateRequestArgumentSize(ttl) +
                    calculateRequestArgumentSize(serializedValue) + (replace == null ? 0 : replace.encodedByteCount());
        Buffer buffer = partitionedRedisClient.executionContext().bufferAllocator().newBuffer(capacity);
        writeRequestArraySize(buffer, len);
        RedisProtocolSupport.Command.RESTORE.encodeTo(buffer);
        writeRequestArgument(buffer, key);
        writeRequestArgument(buffer, ttl);
        writeRequestArgument(buffer, serializedValue);
        if (replace != null) {
            replace.encodeTo(buffer);
        }
        final RedisRequest request = newRequest(RedisProtocolSupport.Command.RESTORE, buffer);
        final RedisPartitionAttributesBuilder partitionAttributesBuilder = partitionAttributesBuilderFunction
                    .apply(RedisProtocolSupport.Command.RESTORE);
        partitionAttributesBuilder.addKey(key);
        final Single<String> result = partitionedRedisClient.request(partitionAttributesBuilder.build(), request,
                    String.class);
        return result;
    }

    @Override
    public <T> Single<List<T>> role() {
        final int len = 1;
        final int capacity = calculateInitialCommandBufferSize(len, RedisProtocolSupport.Command.ROLE);
        Buffer buffer = partitionedRedisClient.executionContext().bufferAllocator().newBuffer(capacity);
        writeRequestArraySize(buffer, len);
        RedisProtocolSupport.Command.ROLE.encodeTo(buffer);
        final RedisRequest request = newRequest(RedisProtocolSupport.Command.ROLE, buffer);
        final RedisPartitionAttributesBuilder partitionAttributesBuilder = partitionAttributesBuilderFunction
                    .apply(RedisProtocolSupport.Command.ROLE);
        final Single<List<T>> result = (Single) partitionedRedisClient.request(partitionAttributesBuilder.build(),
                    request, List.class);
        return result;
    }

    @Override
    public Single<Buffer> rpop(@RedisProtocolSupport.Key final Buffer key) {
        requireNonNull(key);
        final int len = 2;
        final int capacity = calculateInitialCommandBufferSize(len, RedisProtocolSupport.Command.RPOP) +
                    calculateRequestArgumentSize(key);
        Buffer buffer = partitionedRedisClient.executionContext().bufferAllocator().newBuffer(capacity);
        writeRequestArraySize(buffer, len);
        RedisProtocolSupport.Command.RPOP.encodeTo(buffer);
        writeRequestArgument(buffer, key);
        final RedisRequest request = newRequest(RedisProtocolSupport.Command.RPOP, buffer);
        final RedisPartitionAttributesBuilder partitionAttributesBuilder = partitionAttributesBuilderFunction
                    .apply(RedisProtocolSupport.Command.RPOP);
        partitionAttributesBuilder.addKey(key);
        final Single<Buffer> result = partitionedRedisClient.request(partitionAttributesBuilder.build(), request,
                    Buffer.class);
        return result;
    }

    @Override
    public Single<Buffer> rpoplpush(@RedisProtocolSupport.Key final Buffer source,
                                    @RedisProtocolSupport.Key final Buffer destination) {
        requireNonNull(source);
        requireNonNull(destination);
        final int len = 3;
        final int capacity = calculateInitialCommandBufferSize(len, RedisProtocolSupport.Command.RPOPLPUSH) +
                    calculateRequestArgumentSize(source) + calculateRequestArgumentSize(destination);
        Buffer buffer = partitionedRedisClient.executionContext().bufferAllocator().newBuffer(capacity);
        writeRequestArraySize(buffer, len);
        RedisProtocolSupport.Command.RPOPLPUSH.encodeTo(buffer);
        writeRequestArgument(buffer, source);
        writeRequestArgument(buffer, destination);
        final RedisRequest request = newRequest(RedisProtocolSupport.Command.RPOPLPUSH, buffer);
        final RedisPartitionAttributesBuilder partitionAttributesBuilder = partitionAttributesBuilderFunction
                    .apply(RedisProtocolSupport.Command.RPOPLPUSH);
        partitionAttributesBuilder.addKey(source);
        partitionAttributesBuilder.addKey(destination);
        final Single<Buffer> result = partitionedRedisClient.request(partitionAttributesBuilder.build(), request,
                    Buffer.class);
        return result;
    }

    @Override
    public Single<Long> rpush(@RedisProtocolSupport.Key final Buffer key, final Buffer value) {
        requireNonNull(key);
        requireNonNull(value);
        final int len = 3;
        final int capacity = calculateInitialCommandBufferSize(len, RedisProtocolSupport.Command.RPUSH) +
                    calculateRequestArgumentSize(key) + calculateRequestArgumentSize(value);
        Buffer buffer = partitionedRedisClient.executionContext().bufferAllocator().newBuffer(capacity);
        writeRequestArraySize(buffer, len);
        RedisProtocolSupport.Command.RPUSH.encodeTo(buffer);
        writeRequestArgument(buffer, key);
        writeRequestArgument(buffer, value);
        final RedisRequest request = newRequest(RedisProtocolSupport.Command.RPUSH, buffer);
        final RedisPartitionAttributesBuilder partitionAttributesBuilder = partitionAttributesBuilderFunction
                    .apply(RedisProtocolSupport.Command.RPUSH);
        partitionAttributesBuilder.addKey(key);
        final Single<Long> result = partitionedRedisClient.request(partitionAttributesBuilder.build(), request,
                    Long.class);
        return result;
    }

    @Override
    public Single<Long> rpush(@RedisProtocolSupport.Key final Buffer key, final Buffer value1, final Buffer value2) {
        requireNonNull(key);
        requireNonNull(value1);
        requireNonNull(value2);
        final int len = 4;
        final int capacity = calculateInitialCommandBufferSize(len, RedisProtocolSupport.Command.RPUSH) +
                    calculateRequestArgumentSize(key) + calculateRequestArgumentSize(value1) +
                    calculateRequestArgumentSize(value2);
        Buffer buffer = partitionedRedisClient.executionContext().bufferAllocator().newBuffer(capacity);
        writeRequestArraySize(buffer, len);
        RedisProtocolSupport.Command.RPUSH.encodeTo(buffer);
        writeRequestArgument(buffer, key);
        writeRequestArgument(buffer, value1);
        writeRequestArgument(buffer, value2);
        final RedisRequest request = newRequest(RedisProtocolSupport.Command.RPUSH, buffer);
        final RedisPartitionAttributesBuilder partitionAttributesBuilder = partitionAttributesBuilderFunction
                    .apply(RedisProtocolSupport.Command.RPUSH);
        partitionAttributesBuilder.addKey(key);
        final Single<Long> result = partitionedRedisClient.request(partitionAttributesBuilder.build(), request,
                    Long.class);
        return result;
    }

    @Override
    public Single<Long> rpush(@RedisProtocolSupport.Key final Buffer key, final Buffer value1, final Buffer value2,
                              final Buffer value3) {
        requireNonNull(key);
        requireNonNull(value1);
        requireNonNull(value2);
        requireNonNull(value3);
        final int len = 5;
        final int capacity = calculateInitialCommandBufferSize(len, RedisProtocolSupport.Command.RPUSH) +
                    calculateRequestArgumentSize(key) + calculateRequestArgumentSize(value1) +
                    calculateRequestArgumentSize(value2) + calculateRequestArgumentSize(value3);
        Buffer buffer = partitionedRedisClient.executionContext().bufferAllocator().newBuffer(capacity);
        writeRequestArraySize(buffer, len);
        RedisProtocolSupport.Command.RPUSH.encodeTo(buffer);
        writeRequestArgument(buffer, key);
        writeRequestArgument(buffer, value1);
        writeRequestArgument(buffer, value2);
        writeRequestArgument(buffer, value3);
        final RedisRequest request = newRequest(RedisProtocolSupport.Command.RPUSH, buffer);
        final RedisPartitionAttributesBuilder partitionAttributesBuilder = partitionAttributesBuilderFunction
                    .apply(RedisProtocolSupport.Command.RPUSH);
        partitionAttributesBuilder.addKey(key);
        final Single<Long> result = partitionedRedisClient.request(partitionAttributesBuilder.build(), request,
                    Long.class);
        return result;
    }

    @Override
    public Single<Long> rpush(@RedisProtocolSupport.Key final Buffer key, final Collection<Buffer> values) {
        requireNonNull(key);
        requireNonNull(values);
        final int len = 2 + values.size();
        int valuesCapacity = 0;
        if (values instanceof List && values instanceof RandomAccess) {
            final List<Buffer> list = (List<Buffer>) values;
            for (int i = 0; i < list.size(); ++i) {
                final Buffer arg = list.get(i);
                valuesCapacity += calculateRequestArgumentSize(arg);
            }
        } else {
            for (Buffer arg : values) {
                valuesCapacity += calculateRequestArgumentSize(arg);
            }
        }
        final int capacity = calculateInitialCommandBufferSize(len, RedisProtocolSupport.Command.RPUSH) +
                    calculateRequestArgumentSize(key) + valuesCapacity;
        Buffer buffer = partitionedRedisClient.executionContext().bufferAllocator().newBuffer(capacity);
        writeRequestArraySize(buffer, len);
        RedisProtocolSupport.Command.RPUSH.encodeTo(buffer);
        writeRequestArgument(buffer, key);
        if (values instanceof List && values instanceof RandomAccess) {
            final List<Buffer> list = (List<Buffer>) values;
            for (int i = 0; i < list.size(); ++i) {
                final Buffer arg = list.get(i);
                writeRequestArgument(buffer, arg);
            }
        } else {
            for (Buffer arg : values) {
                writeRequestArgument(buffer, arg);
            }
        }
        final RedisRequest request = newRequest(RedisProtocolSupport.Command.RPUSH, buffer);
        final RedisPartitionAttributesBuilder partitionAttributesBuilder = partitionAttributesBuilderFunction
                    .apply(RedisProtocolSupport.Command.RPUSH);
        partitionAttributesBuilder.addKey(key);
        final Single<Long> result = partitionedRedisClient.request(partitionAttributesBuilder.build(), request,
                    Long.class);
        return result;
    }

    @Override
    public Single<Long> rpushx(@RedisProtocolSupport.Key final Buffer key, final Buffer value) {
        requireNonNull(key);
        requireNonNull(value);
        final int len = 3;
        final int capacity = calculateInitialCommandBufferSize(len, RedisProtocolSupport.Command.RPUSHX) +
                    calculateRequestArgumentSize(key) + calculateRequestArgumentSize(value);
        Buffer buffer = partitionedRedisClient.executionContext().bufferAllocator().newBuffer(capacity);
        writeRequestArraySize(buffer, len);
        RedisProtocolSupport.Command.RPUSHX.encodeTo(buffer);
        writeRequestArgument(buffer, key);
        writeRequestArgument(buffer, value);
        final RedisRequest request = newRequest(RedisProtocolSupport.Command.RPUSHX, buffer);
        final RedisPartitionAttributesBuilder partitionAttributesBuilder = partitionAttributesBuilderFunction
                    .apply(RedisProtocolSupport.Command.RPUSHX);
        partitionAttributesBuilder.addKey(key);
        final Single<Long> result = partitionedRedisClient.request(partitionAttributesBuilder.build(), request,
                    Long.class);
        return result;
    }

    @Override
    public Single<Long> sadd(@RedisProtocolSupport.Key final Buffer key, final Buffer member) {
        requireNonNull(key);
        requireNonNull(member);
        final int len = 3;
        final int capacity = calculateInitialCommandBufferSize(len, RedisProtocolSupport.Command.SADD) +
                    calculateRequestArgumentSize(key) + calculateRequestArgumentSize(member);
        Buffer buffer = partitionedRedisClient.executionContext().bufferAllocator().newBuffer(capacity);
        writeRequestArraySize(buffer, len);
        RedisProtocolSupport.Command.SADD.encodeTo(buffer);
        writeRequestArgument(buffer, key);
        writeRequestArgument(buffer, member);
        final RedisRequest request = newRequest(RedisProtocolSupport.Command.SADD, buffer);
        final RedisPartitionAttributesBuilder partitionAttributesBuilder = partitionAttributesBuilderFunction
                    .apply(RedisProtocolSupport.Command.SADD);
        partitionAttributesBuilder.addKey(key);
        final Single<Long> result = partitionedRedisClient.request(partitionAttributesBuilder.build(), request,
                    Long.class);
        return result;
    }

    @Override
    public Single<Long> sadd(@RedisProtocolSupport.Key final Buffer key, final Buffer member1, final Buffer member2) {
        requireNonNull(key);
        requireNonNull(member1);
        requireNonNull(member2);
        final int len = 4;
        final int capacity = calculateInitialCommandBufferSize(len, RedisProtocolSupport.Command.SADD) +
                    calculateRequestArgumentSize(key) + calculateRequestArgumentSize(member1) +
                    calculateRequestArgumentSize(member2);
        Buffer buffer = partitionedRedisClient.executionContext().bufferAllocator().newBuffer(capacity);
        writeRequestArraySize(buffer, len);
        RedisProtocolSupport.Command.SADD.encodeTo(buffer);
        writeRequestArgument(buffer, key);
        writeRequestArgument(buffer, member1);
        writeRequestArgument(buffer, member2);
        final RedisRequest request = newRequest(RedisProtocolSupport.Command.SADD, buffer);
        final RedisPartitionAttributesBuilder partitionAttributesBuilder = partitionAttributesBuilderFunction
                    .apply(RedisProtocolSupport.Command.SADD);
        partitionAttributesBuilder.addKey(key);
        final Single<Long> result = partitionedRedisClient.request(partitionAttributesBuilder.build(), request,
                    Long.class);
        return result;
    }

    @Override
    public Single<Long> sadd(@RedisProtocolSupport.Key final Buffer key, final Buffer member1, final Buffer member2,
                             final Buffer member3) {
        requireNonNull(key);
        requireNonNull(member1);
        requireNonNull(member2);
        requireNonNull(member3);
        final int len = 5;
        final int capacity = calculateInitialCommandBufferSize(len, RedisProtocolSupport.Command.SADD) +
                    calculateRequestArgumentSize(key) + calculateRequestArgumentSize(member1) +
                    calculateRequestArgumentSize(member2) + calculateRequestArgumentSize(member3);
        Buffer buffer = partitionedRedisClient.executionContext().bufferAllocator().newBuffer(capacity);
        writeRequestArraySize(buffer, len);
        RedisProtocolSupport.Command.SADD.encodeTo(buffer);
        writeRequestArgument(buffer, key);
        writeRequestArgument(buffer, member1);
        writeRequestArgument(buffer, member2);
        writeRequestArgument(buffer, member3);
        final RedisRequest request = newRequest(RedisProtocolSupport.Command.SADD, buffer);
        final RedisPartitionAttributesBuilder partitionAttributesBuilder = partitionAttributesBuilderFunction
                    .apply(RedisProtocolSupport.Command.SADD);
        partitionAttributesBuilder.addKey(key);
        final Single<Long> result = partitionedRedisClient.request(partitionAttributesBuilder.build(), request,
                    Long.class);
        return result;
    }

    @Override
    public Single<Long> sadd(@RedisProtocolSupport.Key final Buffer key, final Collection<Buffer> members) {
        requireNonNull(key);
        requireNonNull(members);
        final int len = 2 + members.size();
        int membersCapacity = 0;
        if (members instanceof List && members instanceof RandomAccess) {
            final List<Buffer> list = (List<Buffer>) members;
            for (int i = 0; i < list.size(); ++i) {
                final Buffer arg = list.get(i);
                membersCapacity += calculateRequestArgumentSize(arg);
            }
        } else {
            for (Buffer arg : members) {
                membersCapacity += calculateRequestArgumentSize(arg);
            }
        }
        final int capacity = calculateInitialCommandBufferSize(len, RedisProtocolSupport.Command.SADD) +
                    calculateRequestArgumentSize(key) + membersCapacity;
        Buffer buffer = partitionedRedisClient.executionContext().bufferAllocator().newBuffer(capacity);
        writeRequestArraySize(buffer, len);
        RedisProtocolSupport.Command.SADD.encodeTo(buffer);
        writeRequestArgument(buffer, key);
        if (members instanceof List && members instanceof RandomAccess) {
            final List<Buffer> list = (List<Buffer>) members;
            for (int i = 0; i < list.size(); ++i) {
                final Buffer arg = list.get(i);
                writeRequestArgument(buffer, arg);
            }
        } else {
            for (Buffer arg : members) {
                writeRequestArgument(buffer, arg);
            }
        }
        final RedisRequest request = newRequest(RedisProtocolSupport.Command.SADD, buffer);
        final RedisPartitionAttributesBuilder partitionAttributesBuilder = partitionAttributesBuilderFunction
                    .apply(RedisProtocolSupport.Command.SADD);
        partitionAttributesBuilder.addKey(key);
        final Single<Long> result = partitionedRedisClient.request(partitionAttributesBuilder.build(), request,
                    Long.class);
        return result;
    }

    @Override
    public Single<String> save() {
        final int len = 1;
        final int capacity = calculateInitialCommandBufferSize(len, RedisProtocolSupport.Command.SAVE);
        Buffer buffer = partitionedRedisClient.executionContext().bufferAllocator().newBuffer(capacity);
        writeRequestArraySize(buffer, len);
        RedisProtocolSupport.Command.SAVE.encodeTo(buffer);
        final RedisRequest request = newRequest(RedisProtocolSupport.Command.SAVE, buffer);
        final RedisPartitionAttributesBuilder partitionAttributesBuilder = partitionAttributesBuilderFunction
                    .apply(RedisProtocolSupport.Command.SAVE);
        final Single<String> result = partitionedRedisClient.request(partitionAttributesBuilder.build(), request,
                    String.class);
        return result;
    }

    @Override
    public <T> Single<List<T>> scan(final long cursor) {
        final int len = 2;
        final int capacity = calculateInitialCommandBufferSize(len, RedisProtocolSupport.Command.SCAN) +
                    calculateRequestArgumentSize(cursor);
        Buffer buffer = partitionedRedisClient.executionContext().bufferAllocator().newBuffer(capacity);
        writeRequestArraySize(buffer, len);
        RedisProtocolSupport.Command.SCAN.encodeTo(buffer);
        writeRequestArgument(buffer, cursor);
        final RedisRequest request = newRequest(RedisProtocolSupport.Command.SCAN, buffer);
        final RedisPartitionAttributesBuilder partitionAttributesBuilder = partitionAttributesBuilderFunction
                    .apply(RedisProtocolSupport.Command.SCAN);
        final Single<List<T>> result = (Single) partitionedRedisClient.request(partitionAttributesBuilder.build(),
                    request, List.class);
        return result;
    }

    @Override
    public <T> Single<List<T>> scan(final long cursor, @Nullable final Buffer matchPattern,
                                    @Nullable final Long count) {
        final int len = 2 + (matchPattern == null ? 0 : 2) + (count == null ? 0 : 2);
        final int capacity = calculateInitialCommandBufferSize(len, RedisProtocolSupport.Command.SCAN) +
                    calculateRequestArgumentSize(cursor) +
                    (matchPattern == null ? 0 : RedisProtocolSupport.SubCommand.MATCH.encodedByteCount()) +
                    (matchPattern == null ? 0 : calculateRequestArgumentSize(matchPattern)) +
                    (count == null ? 0 : RedisProtocolSupport.SubCommand.COUNT.encodedByteCount()) +
                    (count == null ? 0 : calculateRequestArgumentSize(count));
        Buffer buffer = partitionedRedisClient.executionContext().bufferAllocator().newBuffer(capacity);
        writeRequestArraySize(buffer, len);
        RedisProtocolSupport.Command.SCAN.encodeTo(buffer);
        writeRequestArgument(buffer, cursor);
        if (matchPattern != null) {
            RedisProtocolSupport.SubCommand.MATCH.encodeTo(buffer);
            writeRequestArgument(buffer, matchPattern);
        }
        if (count != null) {
            RedisProtocolSupport.SubCommand.COUNT.encodeTo(buffer);
            writeRequestArgument(buffer, count);
        }
        final RedisRequest request = newRequest(RedisProtocolSupport.Command.SCAN, buffer);
        final RedisPartitionAttributesBuilder partitionAttributesBuilder = partitionAttributesBuilderFunction
                    .apply(RedisProtocolSupport.Command.SCAN);
        final Single<List<T>> result = (Single) partitionedRedisClient.request(partitionAttributesBuilder.build(),
                    request, List.class);
        return result;
    }

    @Override
    public Single<Long> scard(@RedisProtocolSupport.Key final Buffer key) {
        requireNonNull(key);
        final int len = 2;
        final int capacity = calculateInitialCommandBufferSize(len, RedisProtocolSupport.Command.SCARD) +
                    calculateRequestArgumentSize(key);
        Buffer buffer = partitionedRedisClient.executionContext().bufferAllocator().newBuffer(capacity);
        writeRequestArraySize(buffer, len);
        RedisProtocolSupport.Command.SCARD.encodeTo(buffer);
        writeRequestArgument(buffer, key);
        final RedisRequest request = newRequest(RedisProtocolSupport.Command.SCARD, buffer);
        final RedisPartitionAttributesBuilder partitionAttributesBuilder = partitionAttributesBuilderFunction
                    .apply(RedisProtocolSupport.Command.SCARD);
        partitionAttributesBuilder.addKey(key);
        final Single<Long> result = partitionedRedisClient.request(partitionAttributesBuilder.build(), request,
                    Long.class);
        return result;
    }

    @Override
    public Single<String> scriptDebug(final RedisProtocolSupport.ScriptDebugMode mode) {
        requireNonNull(mode);
        final int len = 3;
        final int capacity = 1 + calculateInitialCommandBufferSize(len, RedisProtocolSupport.Command.SCRIPT) +
                    mode.encodedByteCount();
        Buffer buffer = partitionedRedisClient.executionContext().bufferAllocator().newBuffer(capacity);
        writeRequestArraySize(buffer, len);
        RedisProtocolSupport.Command.SCRIPT.encodeTo(buffer);
        RedisProtocolSupport.SubCommand.DEBUG.encodeTo(buffer);
        mode.encodeTo(buffer);
        final RedisRequest request = newRequest(RedisProtocolSupport.Command.SCRIPT, buffer);
        final RedisPartitionAttributesBuilder partitionAttributesBuilder = partitionAttributesBuilderFunction
                    .apply(RedisProtocolSupport.Command.SCRIPT);
        final Single<String> result = partitionedRedisClient.request(partitionAttributesBuilder.build(), request,
                    String.class);
        return result;
    }

    @Override
    public <T> Single<List<T>> scriptExists(final Buffer sha1) {
        requireNonNull(sha1);
        final int len = 3;
        final int capacity = 1 + calculateInitialCommandBufferSize(len, RedisProtocolSupport.Command.SCRIPT) +
                    calculateRequestArgumentSize(sha1);
        Buffer buffer = partitionedRedisClient.executionContext().bufferAllocator().newBuffer(capacity);
        writeRequestArraySize(buffer, len);
        RedisProtocolSupport.Command.SCRIPT.encodeTo(buffer);
        RedisProtocolSupport.SubCommand.EXISTS.encodeTo(buffer);
        writeRequestArgument(buffer, sha1);
        final RedisRequest request = newRequest(RedisProtocolSupport.Command.SCRIPT, buffer);
        final RedisPartitionAttributesBuilder partitionAttributesBuilder = partitionAttributesBuilderFunction
                    .apply(RedisProtocolSupport.Command.SCRIPT);
        final Single<List<T>> result = (Single) partitionedRedisClient.request(partitionAttributesBuilder.build(),
                    request, List.class);
        return result;
    }

    @Override
    public <T> Single<List<T>> scriptExists(final Buffer sha11, final Buffer sha12) {
        requireNonNull(sha11);
        requireNonNull(sha12);
        final int len = 4;
        final int capacity = 1 + calculateInitialCommandBufferSize(len, RedisProtocolSupport.Command.SCRIPT) +
                    calculateRequestArgumentSize(sha11) + calculateRequestArgumentSize(sha12);
        Buffer buffer = partitionedRedisClient.executionContext().bufferAllocator().newBuffer(capacity);
        writeRequestArraySize(buffer, len);
        RedisProtocolSupport.Command.SCRIPT.encodeTo(buffer);
        RedisProtocolSupport.SubCommand.EXISTS.encodeTo(buffer);
        writeRequestArgument(buffer, sha11);
        writeRequestArgument(buffer, sha12);
        final RedisRequest request = newRequest(RedisProtocolSupport.Command.SCRIPT, buffer);
        final RedisPartitionAttributesBuilder partitionAttributesBuilder = partitionAttributesBuilderFunction
                    .apply(RedisProtocolSupport.Command.SCRIPT);
        final Single<List<T>> result = (Single) partitionedRedisClient.request(partitionAttributesBuilder.build(),
                    request, List.class);
        return result;
    }

    @Override
    public <T> Single<List<T>> scriptExists(final Buffer sha11, final Buffer sha12, final Buffer sha13) {
        requireNonNull(sha11);
        requireNonNull(sha12);
        requireNonNull(sha13);
        final int len = 5;
        final int capacity = 1 + calculateInitialCommandBufferSize(len, RedisProtocolSupport.Command.SCRIPT) +
                    calculateRequestArgumentSize(sha11) + calculateRequestArgumentSize(sha12) +
                    calculateRequestArgumentSize(sha13);
        Buffer buffer = partitionedRedisClient.executionContext().bufferAllocator().newBuffer(capacity);
        writeRequestArraySize(buffer, len);
        RedisProtocolSupport.Command.SCRIPT.encodeTo(buffer);
        RedisProtocolSupport.SubCommand.EXISTS.encodeTo(buffer);
        writeRequestArgument(buffer, sha11);
        writeRequestArgument(buffer, sha12);
        writeRequestArgument(buffer, sha13);
        final RedisRequest request = newRequest(RedisProtocolSupport.Command.SCRIPT, buffer);
        final RedisPartitionAttributesBuilder partitionAttributesBuilder = partitionAttributesBuilderFunction
                    .apply(RedisProtocolSupport.Command.SCRIPT);
        final Single<List<T>> result = (Single) partitionedRedisClient.request(partitionAttributesBuilder.build(),
                    request, List.class);
        return result;
    }

    @Override
    public <T> Single<List<T>> scriptExists(final Collection<Buffer> sha1s) {
        requireNonNull(sha1s);
        final int len = 2 + sha1s.size();
        int sha1sCapacity = 0;
        if (sha1s instanceof List && sha1s instanceof RandomAccess) {
            final List<Buffer> list = (List<Buffer>) sha1s;
            for (int i = 0; i < list.size(); ++i) {
                final Buffer arg = list.get(i);
                sha1sCapacity += calculateRequestArgumentSize(arg);
            }
        } else {
            for (Buffer arg : sha1s) {
                sha1sCapacity += calculateRequestArgumentSize(arg);
            }
        }
        final int capacity = 1 + calculateInitialCommandBufferSize(len, RedisProtocolSupport.Command.SCRIPT) +
                    sha1sCapacity;
        Buffer buffer = partitionedRedisClient.executionContext().bufferAllocator().newBuffer(capacity);
        writeRequestArraySize(buffer, len);
        RedisProtocolSupport.Command.SCRIPT.encodeTo(buffer);
        RedisProtocolSupport.SubCommand.EXISTS.encodeTo(buffer);
        if (sha1s instanceof List && sha1s instanceof RandomAccess) {
            final List<Buffer> list = (List<Buffer>) sha1s;
            for (int i = 0; i < list.size(); ++i) {
                final Buffer arg = list.get(i);
                writeRequestArgument(buffer, arg);
            }
        } else {
            for (Buffer arg : sha1s) {
                writeRequestArgument(buffer, arg);
            }
        }
        final RedisRequest request = newRequest(RedisProtocolSupport.Command.SCRIPT, buffer);
        final RedisPartitionAttributesBuilder partitionAttributesBuilder = partitionAttributesBuilderFunction
                    .apply(RedisProtocolSupport.Command.SCRIPT);
        final Single<List<T>> result = (Single) partitionedRedisClient.request(partitionAttributesBuilder.build(),
                    request, List.class);
        return result;
    }

    @Override
    public Single<String> scriptFlush() {
        final int len = 2;
        final int capacity = 1 + calculateInitialCommandBufferSize(len, RedisProtocolSupport.Command.SCRIPT);
        Buffer buffer = partitionedRedisClient.executionContext().bufferAllocator().newBuffer(capacity);
        writeRequestArraySize(buffer, len);
        RedisProtocolSupport.Command.SCRIPT.encodeTo(buffer);
        RedisProtocolSupport.SubCommand.FLUSH.encodeTo(buffer);
        final RedisRequest request = newRequest(RedisProtocolSupport.Command.SCRIPT, buffer);
        final RedisPartitionAttributesBuilder partitionAttributesBuilder = partitionAttributesBuilderFunction
                    .apply(RedisProtocolSupport.Command.SCRIPT);
        final Single<String> result = partitionedRedisClient.request(partitionAttributesBuilder.build(), request,
                    String.class);
        return result;
    }

    @Override
    public Single<String> scriptKill() {
        final int len = 2;
        final int capacity = 1 + calculateInitialCommandBufferSize(len, RedisProtocolSupport.Command.SCRIPT);
        Buffer buffer = partitionedRedisClient.executionContext().bufferAllocator().newBuffer(capacity);
        writeRequestArraySize(buffer, len);
        RedisProtocolSupport.Command.SCRIPT.encodeTo(buffer);
        RedisProtocolSupport.SubCommand.KILL.encodeTo(buffer);
        final RedisRequest request = newRequest(RedisProtocolSupport.Command.SCRIPT, buffer);
        final RedisPartitionAttributesBuilder partitionAttributesBuilder = partitionAttributesBuilderFunction
                    .apply(RedisProtocolSupport.Command.SCRIPT);
        final Single<String> result = partitionedRedisClient.request(partitionAttributesBuilder.build(), request,
                    String.class);
        return result;
    }

    @Override
    public Single<Buffer> scriptLoad(final Buffer script) {
        requireNonNull(script);
        final int len = 3;
        final int capacity = 1 + calculateInitialCommandBufferSize(len, RedisProtocolSupport.Command.SCRIPT) +
                    calculateRequestArgumentSize(script);
        Buffer buffer = partitionedRedisClient.executionContext().bufferAllocator().newBuffer(capacity);
        writeRequestArraySize(buffer, len);
        RedisProtocolSupport.Command.SCRIPT.encodeTo(buffer);
        RedisProtocolSupport.SubCommand.LOAD.encodeTo(buffer);
        writeRequestArgument(buffer, script);
        final RedisRequest request = newRequest(RedisProtocolSupport.Command.SCRIPT, buffer);
        final RedisPartitionAttributesBuilder partitionAttributesBuilder = partitionAttributesBuilderFunction
                    .apply(RedisProtocolSupport.Command.SCRIPT);
        final Single<Buffer> result = partitionedRedisClient.request(partitionAttributesBuilder.build(), request,
                    Buffer.class);
        return result;
    }

    @Override
    public <T> Single<List<T>> sdiff(@RedisProtocolSupport.Key final Buffer firstkey) {
        requireNonNull(firstkey);
        final int len = 2;
        final int capacity = calculateInitialCommandBufferSize(len, RedisProtocolSupport.Command.SDIFF) +
                    calculateRequestArgumentSize(firstkey);
        Buffer buffer = partitionedRedisClient.executionContext().bufferAllocator().newBuffer(capacity);
        writeRequestArraySize(buffer, len);
        RedisProtocolSupport.Command.SDIFF.encodeTo(buffer);
        writeRequestArgument(buffer, firstkey);
        final RedisRequest request = newRequest(RedisProtocolSupport.Command.SDIFF, buffer);
        final RedisPartitionAttributesBuilder partitionAttributesBuilder = partitionAttributesBuilderFunction
                    .apply(RedisProtocolSupport.Command.SDIFF);
        partitionAttributesBuilder.addKey(firstkey);
        final Single<List<T>> result = (Single) partitionedRedisClient.request(partitionAttributesBuilder.build(),
                    request, List.class);
        return result;
    }

    @Override
    public <T> Single<List<T>> sdiff(@RedisProtocolSupport.Key final Buffer firstkey,
                                     @Nullable @RedisProtocolSupport.Key final Buffer otherkey) {
        requireNonNull(firstkey);
        final int len = 2 + (otherkey == null ? 0 : 1);
        final int capacity = calculateInitialCommandBufferSize(len, RedisProtocolSupport.Command.SDIFF) +
                    calculateRequestArgumentSize(firstkey) +
                    (otherkey == null ? 0 : calculateRequestArgumentSize(otherkey));
        Buffer buffer = partitionedRedisClient.executionContext().bufferAllocator().newBuffer(capacity);
        writeRequestArraySize(buffer, len);
        RedisProtocolSupport.Command.SDIFF.encodeTo(buffer);
        writeRequestArgument(buffer, firstkey);
        if (otherkey != null) {
            writeRequestArgument(buffer, otherkey);
        }
        final RedisRequest request = newRequest(RedisProtocolSupport.Command.SDIFF, buffer);
        final RedisPartitionAttributesBuilder partitionAttributesBuilder = partitionAttributesBuilderFunction
                    .apply(RedisProtocolSupport.Command.SDIFF);
        partitionAttributesBuilder.addKey(firstkey);
        if (otherkey != null) {
            partitionAttributesBuilder.addKey(otherkey);
        }
        final Single<List<T>> result = (Single) partitionedRedisClient.request(partitionAttributesBuilder.build(),
                    request, List.class);
        return result;
    }

    @Override
    public <T> Single<List<T>> sdiff(@RedisProtocolSupport.Key final Buffer firstkey,
                                     @Nullable @RedisProtocolSupport.Key final Buffer otherkey1,
                                     @Nullable @RedisProtocolSupport.Key final Buffer otherkey2) {
        requireNonNull(firstkey);
        final int len = 2 + (otherkey1 == null ? 0 : 1) + (otherkey2 == null ? 0 : 1);
        final int capacity = calculateInitialCommandBufferSize(len, RedisProtocolSupport.Command.SDIFF) +
                    calculateRequestArgumentSize(firstkey) +
                    (otherkey1 == null ? 0 : calculateRequestArgumentSize(otherkey1)) +
                    (otherkey2 == null ? 0 : calculateRequestArgumentSize(otherkey2));
        Buffer buffer = partitionedRedisClient.executionContext().bufferAllocator().newBuffer(capacity);
        writeRequestArraySize(buffer, len);
        RedisProtocolSupport.Command.SDIFF.encodeTo(buffer);
        writeRequestArgument(buffer, firstkey);
        if (otherkey1 != null) {
            writeRequestArgument(buffer, otherkey1);
        }
        if (otherkey2 != null) {
            writeRequestArgument(buffer, otherkey2);
        }
        final RedisRequest request = newRequest(RedisProtocolSupport.Command.SDIFF, buffer);
        final RedisPartitionAttributesBuilder partitionAttributesBuilder = partitionAttributesBuilderFunction
                    .apply(RedisProtocolSupport.Command.SDIFF);
        partitionAttributesBuilder.addKey(firstkey);
        if (otherkey1 != null) {
            partitionAttributesBuilder.addKey(otherkey1);
        }
        if (otherkey2 != null) {
            partitionAttributesBuilder.addKey(otherkey2);
        }
        final Single<List<T>> result = (Single) partitionedRedisClient.request(partitionAttributesBuilder.build(),
                    request, List.class);
        return result;
    }

    @Override
    public <T> Single<List<T>> sdiff(@RedisProtocolSupport.Key final Buffer firstkey,
                                     @Nullable @RedisProtocolSupport.Key final Buffer otherkey1,
                                     @Nullable @RedisProtocolSupport.Key final Buffer otherkey2,
                                     @Nullable @RedisProtocolSupport.Key final Buffer otherkey3) {
        requireNonNull(firstkey);
        final int len = 2 + (otherkey1 == null ? 0 : 1) + (otherkey2 == null ? 0 : 1) + (otherkey3 == null ? 0 : 1);
        final int capacity = calculateInitialCommandBufferSize(len, RedisProtocolSupport.Command.SDIFF) +
                    calculateRequestArgumentSize(firstkey) +
                    (otherkey1 == null ? 0 : calculateRequestArgumentSize(otherkey1)) +
                    (otherkey2 == null ? 0 : calculateRequestArgumentSize(otherkey2)) +
                    (otherkey3 == null ? 0 : calculateRequestArgumentSize(otherkey3));
        Buffer buffer = partitionedRedisClient.executionContext().bufferAllocator().newBuffer(capacity);
        writeRequestArraySize(buffer, len);
        RedisProtocolSupport.Command.SDIFF.encodeTo(buffer);
        writeRequestArgument(buffer, firstkey);
        if (otherkey1 != null) {
            writeRequestArgument(buffer, otherkey1);
        }
        if (otherkey2 != null) {
            writeRequestArgument(buffer, otherkey2);
        }
        if (otherkey3 != null) {
            writeRequestArgument(buffer, otherkey3);
        }
        final RedisRequest request = newRequest(RedisProtocolSupport.Command.SDIFF, buffer);
        final RedisPartitionAttributesBuilder partitionAttributesBuilder = partitionAttributesBuilderFunction
                    .apply(RedisProtocolSupport.Command.SDIFF);
        partitionAttributesBuilder.addKey(firstkey);
        if (otherkey1 != null) {
            partitionAttributesBuilder.addKey(otherkey1);
        }
        if (otherkey2 != null) {
            partitionAttributesBuilder.addKey(otherkey2);
        }
        if (otherkey3 != null) {
            partitionAttributesBuilder.addKey(otherkey3);
        }
        final Single<List<T>> result = (Single) partitionedRedisClient.request(partitionAttributesBuilder.build(),
                    request, List.class);
        return result;
    }

    @Override
    public <T> Single<List<T>> sdiff(@RedisProtocolSupport.Key final Buffer firstkey,
                                     @RedisProtocolSupport.Key final Collection<Buffer> otherkeys) {
        requireNonNull(firstkey);
        requireNonNull(otherkeys);
        final int len = 2 + otherkeys.size();
        int otherkeysCapacity = 0;
        if (otherkeys instanceof List && otherkeys instanceof RandomAccess) {
            final List<Buffer> list = (List<Buffer>) otherkeys;
            for (int i = 0; i < list.size(); ++i) {
                final Buffer arg = list.get(i);
                otherkeysCapacity += calculateRequestArgumentSize(arg);
            }
        } else {
            for (Buffer arg : otherkeys) {
                otherkeysCapacity += calculateRequestArgumentSize(arg);
            }
        }
        final int capacity = calculateInitialCommandBufferSize(len, RedisProtocolSupport.Command.SDIFF) +
                    calculateRequestArgumentSize(firstkey) + otherkeysCapacity;
        Buffer buffer = partitionedRedisClient.executionContext().bufferAllocator().newBuffer(capacity);
        writeRequestArraySize(buffer, len);
        RedisProtocolSupport.Command.SDIFF.encodeTo(buffer);
        writeRequestArgument(buffer, firstkey);
        if (otherkeys instanceof List && otherkeys instanceof RandomAccess) {
            final List<Buffer> list = (List<Buffer>) otherkeys;
            for (int i = 0; i < list.size(); ++i) {
                final Buffer arg = list.get(i);
                writeRequestArgument(buffer, arg);
            }
        } else {
            for (Buffer arg : otherkeys) {
                writeRequestArgument(buffer, arg);
            }
        }
        final RedisRequest request = newRequest(RedisProtocolSupport.Command.SDIFF, buffer);
        final RedisPartitionAttributesBuilder partitionAttributesBuilder = partitionAttributesBuilderFunction
                    .apply(RedisProtocolSupport.Command.SDIFF);
        partitionAttributesBuilder.addKey(firstkey);
        addBufferKeysToAttributeBuilder(otherkeys, partitionAttributesBuilder);
        final Single<List<T>> result = (Single) partitionedRedisClient.request(partitionAttributesBuilder.build(),
                    request, List.class);
        return result;
    }

    @Override
    public Single<Long> sdiffstore(@RedisProtocolSupport.Key final Buffer destination,
                                   @RedisProtocolSupport.Key final Buffer firstkey) {
        requireNonNull(destination);
        requireNonNull(firstkey);
        final int len = 3;
        final int capacity = calculateInitialCommandBufferSize(len, RedisProtocolSupport.Command.SDIFFSTORE) +
                    calculateRequestArgumentSize(destination) + calculateRequestArgumentSize(firstkey);
        Buffer buffer = partitionedRedisClient.executionContext().bufferAllocator().newBuffer(capacity);
        writeRequestArraySize(buffer, len);
        RedisProtocolSupport.Command.SDIFFSTORE.encodeTo(buffer);
        writeRequestArgument(buffer, destination);
        writeRequestArgument(buffer, firstkey);
        final RedisRequest request = newRequest(RedisProtocolSupport.Command.SDIFFSTORE, buffer);
        final RedisPartitionAttributesBuilder partitionAttributesBuilder = partitionAttributesBuilderFunction
                    .apply(RedisProtocolSupport.Command.SDIFFSTORE);
        partitionAttributesBuilder.addKey(destination);
        partitionAttributesBuilder.addKey(firstkey);
        final Single<Long> result = partitionedRedisClient.request(partitionAttributesBuilder.build(), request,
                    Long.class);
        return result;
    }

    @Override
    public Single<Long> sdiffstore(@RedisProtocolSupport.Key final Buffer destination,
                                   @RedisProtocolSupport.Key final Buffer firstkey,
                                   @Nullable @RedisProtocolSupport.Key final Buffer otherkey) {
        requireNonNull(destination);
        requireNonNull(firstkey);
        final int len = 3 + (otherkey == null ? 0 : 1);
        final int capacity = calculateInitialCommandBufferSize(len, RedisProtocolSupport.Command.SDIFFSTORE) +
                    calculateRequestArgumentSize(destination) + calculateRequestArgumentSize(firstkey) +
                    (otherkey == null ? 0 : calculateRequestArgumentSize(otherkey));
        Buffer buffer = partitionedRedisClient.executionContext().bufferAllocator().newBuffer(capacity);
        writeRequestArraySize(buffer, len);
        RedisProtocolSupport.Command.SDIFFSTORE.encodeTo(buffer);
        writeRequestArgument(buffer, destination);
        writeRequestArgument(buffer, firstkey);
        if (otherkey != null) {
            writeRequestArgument(buffer, otherkey);
        }
        final RedisRequest request = newRequest(RedisProtocolSupport.Command.SDIFFSTORE, buffer);
        final RedisPartitionAttributesBuilder partitionAttributesBuilder = partitionAttributesBuilderFunction
                    .apply(RedisProtocolSupport.Command.SDIFFSTORE);
        partitionAttributesBuilder.addKey(destination);
        partitionAttributesBuilder.addKey(firstkey);
        if (otherkey != null) {
            partitionAttributesBuilder.addKey(otherkey);
        }
        final Single<Long> result = partitionedRedisClient.request(partitionAttributesBuilder.build(), request,
                    Long.class);
        return result;
    }

    @Override
    public Single<Long> sdiffstore(@RedisProtocolSupport.Key final Buffer destination,
                                   @RedisProtocolSupport.Key final Buffer firstkey,
                                   @Nullable @RedisProtocolSupport.Key final Buffer otherkey1,
                                   @Nullable @RedisProtocolSupport.Key final Buffer otherkey2) {
        requireNonNull(destination);
        requireNonNull(firstkey);
        final int len = 3 + (otherkey1 == null ? 0 : 1) + (otherkey2 == null ? 0 : 1);
        final int capacity = calculateInitialCommandBufferSize(len, RedisProtocolSupport.Command.SDIFFSTORE) +
                    calculateRequestArgumentSize(destination) + calculateRequestArgumentSize(firstkey) +
                    (otherkey1 == null ? 0 : calculateRequestArgumentSize(otherkey1)) +
                    (otherkey2 == null ? 0 : calculateRequestArgumentSize(otherkey2));
        Buffer buffer = partitionedRedisClient.executionContext().bufferAllocator().newBuffer(capacity);
        writeRequestArraySize(buffer, len);
        RedisProtocolSupport.Command.SDIFFSTORE.encodeTo(buffer);
        writeRequestArgument(buffer, destination);
        writeRequestArgument(buffer, firstkey);
        if (otherkey1 != null) {
            writeRequestArgument(buffer, otherkey1);
        }
        if (otherkey2 != null) {
            writeRequestArgument(buffer, otherkey2);
        }
        final RedisRequest request = newRequest(RedisProtocolSupport.Command.SDIFFSTORE, buffer);
        final RedisPartitionAttributesBuilder partitionAttributesBuilder = partitionAttributesBuilderFunction
                    .apply(RedisProtocolSupport.Command.SDIFFSTORE);
        partitionAttributesBuilder.addKey(destination);
        partitionAttributesBuilder.addKey(firstkey);
        if (otherkey1 != null) {
            partitionAttributesBuilder.addKey(otherkey1);
        }
        if (otherkey2 != null) {
            partitionAttributesBuilder.addKey(otherkey2);
        }
        final Single<Long> result = partitionedRedisClient.request(partitionAttributesBuilder.build(), request,
                    Long.class);
        return result;
    }

    @Override
    public Single<Long> sdiffstore(@RedisProtocolSupport.Key final Buffer destination,
                                   @RedisProtocolSupport.Key final Buffer firstkey,
                                   @Nullable @RedisProtocolSupport.Key final Buffer otherkey1,
                                   @Nullable @RedisProtocolSupport.Key final Buffer otherkey2,
                                   @Nullable @RedisProtocolSupport.Key final Buffer otherkey3) {
        requireNonNull(destination);
        requireNonNull(firstkey);
        final int len = 3 + (otherkey1 == null ? 0 : 1) + (otherkey2 == null ? 0 : 1) + (otherkey3 == null ? 0 : 1);
        final int capacity = calculateInitialCommandBufferSize(len, RedisProtocolSupport.Command.SDIFFSTORE) +
                    calculateRequestArgumentSize(destination) + calculateRequestArgumentSize(firstkey) +
                    (otherkey1 == null ? 0 : calculateRequestArgumentSize(otherkey1)) +
                    (otherkey2 == null ? 0 : calculateRequestArgumentSize(otherkey2)) +
                    (otherkey3 == null ? 0 : calculateRequestArgumentSize(otherkey3));
        Buffer buffer = partitionedRedisClient.executionContext().bufferAllocator().newBuffer(capacity);
        writeRequestArraySize(buffer, len);
        RedisProtocolSupport.Command.SDIFFSTORE.encodeTo(buffer);
        writeRequestArgument(buffer, destination);
        writeRequestArgument(buffer, firstkey);
        if (otherkey1 != null) {
            writeRequestArgument(buffer, otherkey1);
        }
        if (otherkey2 != null) {
            writeRequestArgument(buffer, otherkey2);
        }
        if (otherkey3 != null) {
            writeRequestArgument(buffer, otherkey3);
        }
        final RedisRequest request = newRequest(RedisProtocolSupport.Command.SDIFFSTORE, buffer);
        final RedisPartitionAttributesBuilder partitionAttributesBuilder = partitionAttributesBuilderFunction
                    .apply(RedisProtocolSupport.Command.SDIFFSTORE);
        partitionAttributesBuilder.addKey(destination);
        partitionAttributesBuilder.addKey(firstkey);
        if (otherkey1 != null) {
            partitionAttributesBuilder.addKey(otherkey1);
        }
        if (otherkey2 != null) {
            partitionAttributesBuilder.addKey(otherkey2);
        }
        if (otherkey3 != null) {
            partitionAttributesBuilder.addKey(otherkey3);
        }
        final Single<Long> result = partitionedRedisClient.request(partitionAttributesBuilder.build(), request,
                    Long.class);
        return result;
    }

    @Override
    public Single<Long> sdiffstore(@RedisProtocolSupport.Key final Buffer destination,
                                   @RedisProtocolSupport.Key final Buffer firstkey,
                                   @RedisProtocolSupport.Key final Collection<Buffer> otherkeys) {
        requireNonNull(destination);
        requireNonNull(firstkey);
        requireNonNull(otherkeys);
        final int len = 3 + otherkeys.size();
        int otherkeysCapacity = 0;
        if (otherkeys instanceof List && otherkeys instanceof RandomAccess) {
            final List<Buffer> list = (List<Buffer>) otherkeys;
            for (int i = 0; i < list.size(); ++i) {
                final Buffer arg = list.get(i);
                otherkeysCapacity += calculateRequestArgumentSize(arg);
            }
        } else {
            for (Buffer arg : otherkeys) {
                otherkeysCapacity += calculateRequestArgumentSize(arg);
            }
        }
        final int capacity = calculateInitialCommandBufferSize(len, RedisProtocolSupport.Command.SDIFFSTORE) +
                    calculateRequestArgumentSize(destination) + calculateRequestArgumentSize(firstkey) +
                    otherkeysCapacity;
        Buffer buffer = partitionedRedisClient.executionContext().bufferAllocator().newBuffer(capacity);
        writeRequestArraySize(buffer, len);
        RedisProtocolSupport.Command.SDIFFSTORE.encodeTo(buffer);
        writeRequestArgument(buffer, destination);
        writeRequestArgument(buffer, firstkey);
        if (otherkeys instanceof List && otherkeys instanceof RandomAccess) {
            final List<Buffer> list = (List<Buffer>) otherkeys;
            for (int i = 0; i < list.size(); ++i) {
                final Buffer arg = list.get(i);
                writeRequestArgument(buffer, arg);
            }
        } else {
            for (Buffer arg : otherkeys) {
                writeRequestArgument(buffer, arg);
            }
        }
        final RedisRequest request = newRequest(RedisProtocolSupport.Command.SDIFFSTORE, buffer);
        final RedisPartitionAttributesBuilder partitionAttributesBuilder = partitionAttributesBuilderFunction
                    .apply(RedisProtocolSupport.Command.SDIFFSTORE);
        partitionAttributesBuilder.addKey(destination);
        partitionAttributesBuilder.addKey(firstkey);
        addBufferKeysToAttributeBuilder(otherkeys, partitionAttributesBuilder);
        final Single<Long> result = partitionedRedisClient.request(partitionAttributesBuilder.build(), request,
                    Long.class);
        return result;
    }

    @Override
    public Single<String> select(final long index) {
        final int len = 2;
        final int capacity = calculateInitialCommandBufferSize(len, RedisProtocolSupport.Command.SELECT) +
                    calculateRequestArgumentSize(index);
        Buffer buffer = partitionedRedisClient.executionContext().bufferAllocator().newBuffer(capacity);
        writeRequestArraySize(buffer, len);
        RedisProtocolSupport.Command.SELECT.encodeTo(buffer);
        writeRequestArgument(buffer, index);
        final RedisRequest request = newRequest(RedisProtocolSupport.Command.SELECT, buffer);
        final RedisPartitionAttributesBuilder partitionAttributesBuilder = partitionAttributesBuilderFunction
                    .apply(RedisProtocolSupport.Command.SELECT);
        final Single<String> result = partitionedRedisClient.request(partitionAttributesBuilder.build(), request,
                    String.class);
        return result;
    }

    @Override
    public Single<String> set(@RedisProtocolSupport.Key final Buffer key, final Buffer value) {
        requireNonNull(key);
        requireNonNull(value);
        final int len = 3;
        final int capacity = calculateInitialCommandBufferSize(len, RedisProtocolSupport.Command.SET) +
                    calculateRequestArgumentSize(key) + calculateRequestArgumentSize(value);
        Buffer buffer = partitionedRedisClient.executionContext().bufferAllocator().newBuffer(capacity);
        writeRequestArraySize(buffer, len);
        RedisProtocolSupport.Command.SET.encodeTo(buffer);
        writeRequestArgument(buffer, key);
        writeRequestArgument(buffer, value);
        final RedisRequest request = newRequest(RedisProtocolSupport.Command.SET, buffer);
        final RedisPartitionAttributesBuilder partitionAttributesBuilder = partitionAttributesBuilderFunction
                    .apply(RedisProtocolSupport.Command.SET);
        partitionAttributesBuilder.addKey(key);
        final Single<String> result = partitionedRedisClient.request(partitionAttributesBuilder.build(), request,
                    String.class);
        return result;
    }

    @Override
    public Single<String> set(@RedisProtocolSupport.Key final Buffer key, final Buffer value,
                              @Nullable final RedisProtocolSupport.ExpireDuration expireDuration,
                              @Nullable final RedisProtocolSupport.SetCondition condition) {
        requireNonNull(key);
        requireNonNull(value);
        final int len = 3 + (expireDuration == null ? 0 : RedisProtocolSupport.ExpireDuration.SIZE) +
                    (condition == null ? 0 : 1);
        final int capacity = calculateInitialCommandBufferSize(len, RedisProtocolSupport.Command.SET) +
                    calculateRequestArgumentSize(key) + calculateRequestArgumentSize(value) +
                    (expireDuration == null ? 0 : expireDuration.encodedByteCount()) +
                    (condition == null ? 0 : condition.encodedByteCount());
        Buffer buffer = partitionedRedisClient.executionContext().bufferAllocator().newBuffer(capacity);
        writeRequestArraySize(buffer, len);
        RedisProtocolSupport.Command.SET.encodeTo(buffer);
        writeRequestArgument(buffer, key);
        writeRequestArgument(buffer, value);
        if (expireDuration != null) {
            expireDuration.encodeTo(buffer);
        }
        if (condition != null) {
            condition.encodeTo(buffer);
        }
        final RedisRequest request = newRequest(RedisProtocolSupport.Command.SET, buffer);
        final RedisPartitionAttributesBuilder partitionAttributesBuilder = partitionAttributesBuilderFunction
                    .apply(RedisProtocolSupport.Command.SET);
        partitionAttributesBuilder.addKey(key);
        final Single<String> result = partitionedRedisClient.request(partitionAttributesBuilder.build(), request,
                    String.class);
        return result;
    }

    @Override
    public Single<Long> setbit(@RedisProtocolSupport.Key final Buffer key, final long offset, final Buffer value) {
        requireNonNull(key);
        requireNonNull(value);
        final int len = 4;
        final int capacity = calculateInitialCommandBufferSize(len, RedisProtocolSupport.Command.SETBIT) +
                    calculateRequestArgumentSize(key) + calculateRequestArgumentSize(offset) +
                    calculateRequestArgumentSize(value);
        Buffer buffer = partitionedRedisClient.executionContext().bufferAllocator().newBuffer(capacity);
        writeRequestArraySize(buffer, len);
        RedisProtocolSupport.Command.SETBIT.encodeTo(buffer);
        writeRequestArgument(buffer, key);
        writeRequestArgument(buffer, offset);
        writeRequestArgument(buffer, value);
        final RedisRequest request = newRequest(RedisProtocolSupport.Command.SETBIT, buffer);
        final RedisPartitionAttributesBuilder partitionAttributesBuilder = partitionAttributesBuilderFunction
                    .apply(RedisProtocolSupport.Command.SETBIT);
        partitionAttributesBuilder.addKey(key);
        final Single<Long> result = partitionedRedisClient.request(partitionAttributesBuilder.build(), request,
                    Long.class);
        return result;
    }

    @Override
    public Single<String> setex(@RedisProtocolSupport.Key final Buffer key, final long seconds, final Buffer value) {
        requireNonNull(key);
        requireNonNull(value);
        final int len = 4;
        final int capacity = calculateInitialCommandBufferSize(len, RedisProtocolSupport.Command.SETEX) +
                    calculateRequestArgumentSize(key) + calculateRequestArgumentSize(seconds) +
                    calculateRequestArgumentSize(value);
        Buffer buffer = partitionedRedisClient.executionContext().bufferAllocator().newBuffer(capacity);
        writeRequestArraySize(buffer, len);
        RedisProtocolSupport.Command.SETEX.encodeTo(buffer);
        writeRequestArgument(buffer, key);
        writeRequestArgument(buffer, seconds);
        writeRequestArgument(buffer, value);
        final RedisRequest request = newRequest(RedisProtocolSupport.Command.SETEX, buffer);
        final RedisPartitionAttributesBuilder partitionAttributesBuilder = partitionAttributesBuilderFunction
                    .apply(RedisProtocolSupport.Command.SETEX);
        partitionAttributesBuilder.addKey(key);
        final Single<String> result = partitionedRedisClient.request(partitionAttributesBuilder.build(), request,
                    String.class);
        return result;
    }

    @Override
    public Single<Long> setnx(@RedisProtocolSupport.Key final Buffer key, final Buffer value) {
        requireNonNull(key);
        requireNonNull(value);
        final int len = 3;
        final int capacity = calculateInitialCommandBufferSize(len, RedisProtocolSupport.Command.SETNX) +
                    calculateRequestArgumentSize(key) + calculateRequestArgumentSize(value);
        Buffer buffer = partitionedRedisClient.executionContext().bufferAllocator().newBuffer(capacity);
        writeRequestArraySize(buffer, len);
        RedisProtocolSupport.Command.SETNX.encodeTo(buffer);
        writeRequestArgument(buffer, key);
        writeRequestArgument(buffer, value);
        final RedisRequest request = newRequest(RedisProtocolSupport.Command.SETNX, buffer);
        final RedisPartitionAttributesBuilder partitionAttributesBuilder = partitionAttributesBuilderFunction
                    .apply(RedisProtocolSupport.Command.SETNX);
        partitionAttributesBuilder.addKey(key);
        final Single<Long> result = partitionedRedisClient.request(partitionAttributesBuilder.build(), request,
                    Long.class);
        return result;
    }

    @Override
    public Single<Long> setrange(@RedisProtocolSupport.Key final Buffer key, final long offset, final Buffer value) {
        requireNonNull(key);
        requireNonNull(value);
        final int len = 4;
        final int capacity = calculateInitialCommandBufferSize(len, RedisProtocolSupport.Command.SETRANGE) +
                    calculateRequestArgumentSize(key) + calculateRequestArgumentSize(offset) +
                    calculateRequestArgumentSize(value);
        Buffer buffer = partitionedRedisClient.executionContext().bufferAllocator().newBuffer(capacity);
        writeRequestArraySize(buffer, len);
        RedisProtocolSupport.Command.SETRANGE.encodeTo(buffer);
        writeRequestArgument(buffer, key);
        writeRequestArgument(buffer, offset);
        writeRequestArgument(buffer, value);
        final RedisRequest request = newRequest(RedisProtocolSupport.Command.SETRANGE, buffer);
        final RedisPartitionAttributesBuilder partitionAttributesBuilder = partitionAttributesBuilderFunction
                    .apply(RedisProtocolSupport.Command.SETRANGE);
        partitionAttributesBuilder.addKey(key);
        final Single<Long> result = partitionedRedisClient.request(partitionAttributesBuilder.build(), request,
                    Long.class);
        return result;
    }

    @Override
    public Single<String> shutdown() {
        final int len = 1;
        final int capacity = calculateInitialCommandBufferSize(len, RedisProtocolSupport.Command.SHUTDOWN);
        Buffer buffer = partitionedRedisClient.executionContext().bufferAllocator().newBuffer(capacity);
        writeRequestArraySize(buffer, len);
        RedisProtocolSupport.Command.SHUTDOWN.encodeTo(buffer);
        final RedisRequest request = newRequest(RedisProtocolSupport.Command.SHUTDOWN, buffer);
        final RedisPartitionAttributesBuilder partitionAttributesBuilder = partitionAttributesBuilderFunction
                    .apply(RedisProtocolSupport.Command.SHUTDOWN);
        final Single<String> result = partitionedRedisClient.request(partitionAttributesBuilder.build(), request,
                    String.class);
        return result;
    }

    @Override
    public Single<String> shutdown(@Nullable final RedisProtocolSupport.ShutdownSaveMode saveMode) {
        final int len = 1 + (saveMode == null ? 0 : 1);
        final int capacity = calculateInitialCommandBufferSize(len, RedisProtocolSupport.Command.SHUTDOWN) +
                    (saveMode == null ? 0 : saveMode.encodedByteCount());
        Buffer buffer = partitionedRedisClient.executionContext().bufferAllocator().newBuffer(capacity);
        writeRequestArraySize(buffer, len);
        RedisProtocolSupport.Command.SHUTDOWN.encodeTo(buffer);
        if (saveMode != null) {
            saveMode.encodeTo(buffer);
        }
        final RedisRequest request = newRequest(RedisProtocolSupport.Command.SHUTDOWN, buffer);
        final RedisPartitionAttributesBuilder partitionAttributesBuilder = partitionAttributesBuilderFunction
                    .apply(RedisProtocolSupport.Command.SHUTDOWN);
        final Single<String> result = partitionedRedisClient.request(partitionAttributesBuilder.build(), request,
                    String.class);
        return result;
    }

    @Override
    public <T> Single<List<T>> sinter(@RedisProtocolSupport.Key final Buffer key) {
        requireNonNull(key);
        final int len = 2;
        final int capacity = calculateInitialCommandBufferSize(len, RedisProtocolSupport.Command.SINTER) +
                    calculateRequestArgumentSize(key);
        Buffer buffer = partitionedRedisClient.executionContext().bufferAllocator().newBuffer(capacity);
        writeRequestArraySize(buffer, len);
        RedisProtocolSupport.Command.SINTER.encodeTo(buffer);
        writeRequestArgument(buffer, key);
        final RedisRequest request = newRequest(RedisProtocolSupport.Command.SINTER, buffer);
        final RedisPartitionAttributesBuilder partitionAttributesBuilder = partitionAttributesBuilderFunction
                    .apply(RedisProtocolSupport.Command.SINTER);
        partitionAttributesBuilder.addKey(key);
        final Single<List<T>> result = (Single) partitionedRedisClient.request(partitionAttributesBuilder.build(),
                    request, List.class);
        return result;
    }

    @Override
    public <T> Single<List<T>> sinter(@RedisProtocolSupport.Key final Buffer key1,
                                      @RedisProtocolSupport.Key final Buffer key2) {
        requireNonNull(key1);
        requireNonNull(key2);
        final int len = 3;
        final int capacity = calculateInitialCommandBufferSize(len, RedisProtocolSupport.Command.SINTER) +
                    calculateRequestArgumentSize(key1) + calculateRequestArgumentSize(key2);
        Buffer buffer = partitionedRedisClient.executionContext().bufferAllocator().newBuffer(capacity);
        writeRequestArraySize(buffer, len);
        RedisProtocolSupport.Command.SINTER.encodeTo(buffer);
        writeRequestArgument(buffer, key1);
        writeRequestArgument(buffer, key2);
        final RedisRequest request = newRequest(RedisProtocolSupport.Command.SINTER, buffer);
        final RedisPartitionAttributesBuilder partitionAttributesBuilder = partitionAttributesBuilderFunction
                    .apply(RedisProtocolSupport.Command.SINTER);
        partitionAttributesBuilder.addKey(key1);
        partitionAttributesBuilder.addKey(key2);
        final Single<List<T>> result = (Single) partitionedRedisClient.request(partitionAttributesBuilder.build(),
                    request, List.class);
        return result;
    }

    @Override
    public <T> Single<List<T>> sinter(@RedisProtocolSupport.Key final Buffer key1,
                                      @RedisProtocolSupport.Key final Buffer key2,
                                      @RedisProtocolSupport.Key final Buffer key3) {
        requireNonNull(key1);
        requireNonNull(key2);
        requireNonNull(key3);
        final int len = 4;
        final int capacity = calculateInitialCommandBufferSize(len, RedisProtocolSupport.Command.SINTER) +
                    calculateRequestArgumentSize(key1) + calculateRequestArgumentSize(key2) +
                    calculateRequestArgumentSize(key3);
        Buffer buffer = partitionedRedisClient.executionContext().bufferAllocator().newBuffer(capacity);
        writeRequestArraySize(buffer, len);
        RedisProtocolSupport.Command.SINTER.encodeTo(buffer);
        writeRequestArgument(buffer, key1);
        writeRequestArgument(buffer, key2);
        writeRequestArgument(buffer, key3);
        final RedisRequest request = newRequest(RedisProtocolSupport.Command.SINTER, buffer);
        final RedisPartitionAttributesBuilder partitionAttributesBuilder = partitionAttributesBuilderFunction
                    .apply(RedisProtocolSupport.Command.SINTER);
        partitionAttributesBuilder.addKey(key1);
        partitionAttributesBuilder.addKey(key2);
        partitionAttributesBuilder.addKey(key3);
        final Single<List<T>> result = (Single) partitionedRedisClient.request(partitionAttributesBuilder.build(),
                    request, List.class);
        return result;
    }

    @Override
    public <T> Single<List<T>> sinter(@RedisProtocolSupport.Key final Collection<Buffer> keys) {
        requireNonNull(keys);
        final int len = 1 + keys.size();
        int keysCapacity = 0;
        if (keys instanceof List && keys instanceof RandomAccess) {
            final List<Buffer> list = (List<Buffer>) keys;
            for (int i = 0; i < list.size(); ++i) {
                final Buffer arg = list.get(i);
                keysCapacity += calculateRequestArgumentSize(arg);
            }
        } else {
            for (Buffer arg : keys) {
                keysCapacity += calculateRequestArgumentSize(arg);
            }
        }
        final int capacity = calculateInitialCommandBufferSize(len, RedisProtocolSupport.Command.SINTER) + keysCapacity;
        Buffer buffer = partitionedRedisClient.executionContext().bufferAllocator().newBuffer(capacity);
        writeRequestArraySize(buffer, len);
        RedisProtocolSupport.Command.SINTER.encodeTo(buffer);
        if (keys instanceof List && keys instanceof RandomAccess) {
            final List<Buffer> list = (List<Buffer>) keys;
            for (int i = 0; i < list.size(); ++i) {
                final Buffer arg = list.get(i);
                writeRequestArgument(buffer, arg);
            }
        } else {
            for (Buffer arg : keys) {
                writeRequestArgument(buffer, arg);
            }
        }
        final RedisRequest request = newRequest(RedisProtocolSupport.Command.SINTER, buffer);
        final RedisPartitionAttributesBuilder partitionAttributesBuilder = partitionAttributesBuilderFunction
                    .apply(RedisProtocolSupport.Command.SINTER);
        addBufferKeysToAttributeBuilder(keys, partitionAttributesBuilder);
        final Single<List<T>> result = (Single) partitionedRedisClient.request(partitionAttributesBuilder.build(),
                    request, List.class);
        return result;
    }

    @Override
    public Single<Long> sinterstore(@RedisProtocolSupport.Key final Buffer destination,
                                    @RedisProtocolSupport.Key final Buffer key) {
        requireNonNull(destination);
        requireNonNull(key);
        final int len = 3;
        final int capacity = calculateInitialCommandBufferSize(len, RedisProtocolSupport.Command.SINTERSTORE) +
                    calculateRequestArgumentSize(destination) + calculateRequestArgumentSize(key);
        Buffer buffer = partitionedRedisClient.executionContext().bufferAllocator().newBuffer(capacity);
        writeRequestArraySize(buffer, len);
        RedisProtocolSupport.Command.SINTERSTORE.encodeTo(buffer);
        writeRequestArgument(buffer, destination);
        writeRequestArgument(buffer, key);
        final RedisRequest request = newRequest(RedisProtocolSupport.Command.SINTERSTORE, buffer);
        final RedisPartitionAttributesBuilder partitionAttributesBuilder = partitionAttributesBuilderFunction
                    .apply(RedisProtocolSupport.Command.SINTERSTORE);
        partitionAttributesBuilder.addKey(destination);
        partitionAttributesBuilder.addKey(key);
        final Single<Long> result = partitionedRedisClient.request(partitionAttributesBuilder.build(), request,
                    Long.class);
        return result;
    }

    @Override
    public Single<Long> sinterstore(@RedisProtocolSupport.Key final Buffer destination,
                                    @RedisProtocolSupport.Key final Buffer key1,
                                    @RedisProtocolSupport.Key final Buffer key2) {
        requireNonNull(destination);
        requireNonNull(key1);
        requireNonNull(key2);
        final int len = 4;
        final int capacity = calculateInitialCommandBufferSize(len, RedisProtocolSupport.Command.SINTERSTORE) +
                    calculateRequestArgumentSize(destination) + calculateRequestArgumentSize(key1) +
                    calculateRequestArgumentSize(key2);
        Buffer buffer = partitionedRedisClient.executionContext().bufferAllocator().newBuffer(capacity);
        writeRequestArraySize(buffer, len);
        RedisProtocolSupport.Command.SINTERSTORE.encodeTo(buffer);
        writeRequestArgument(buffer, destination);
        writeRequestArgument(buffer, key1);
        writeRequestArgument(buffer, key2);
        final RedisRequest request = newRequest(RedisProtocolSupport.Command.SINTERSTORE, buffer);
        final RedisPartitionAttributesBuilder partitionAttributesBuilder = partitionAttributesBuilderFunction
                    .apply(RedisProtocolSupport.Command.SINTERSTORE);
        partitionAttributesBuilder.addKey(destination);
        partitionAttributesBuilder.addKey(key1);
        partitionAttributesBuilder.addKey(key2);
        final Single<Long> result = partitionedRedisClient.request(partitionAttributesBuilder.build(), request,
                    Long.class);
        return result;
    }

    @Override
    public Single<Long> sinterstore(@RedisProtocolSupport.Key final Buffer destination,
                                    @RedisProtocolSupport.Key final Buffer key1,
                                    @RedisProtocolSupport.Key final Buffer key2,
                                    @RedisProtocolSupport.Key final Buffer key3) {
        requireNonNull(destination);
        requireNonNull(key1);
        requireNonNull(key2);
        requireNonNull(key3);
        final int len = 5;
        final int capacity = calculateInitialCommandBufferSize(len, RedisProtocolSupport.Command.SINTERSTORE) +
                    calculateRequestArgumentSize(destination) + calculateRequestArgumentSize(key1) +
                    calculateRequestArgumentSize(key2) + calculateRequestArgumentSize(key3);
        Buffer buffer = partitionedRedisClient.executionContext().bufferAllocator().newBuffer(capacity);
        writeRequestArraySize(buffer, len);
        RedisProtocolSupport.Command.SINTERSTORE.encodeTo(buffer);
        writeRequestArgument(buffer, destination);
        writeRequestArgument(buffer, key1);
        writeRequestArgument(buffer, key2);
        writeRequestArgument(buffer, key3);
        final RedisRequest request = newRequest(RedisProtocolSupport.Command.SINTERSTORE, buffer);
        final RedisPartitionAttributesBuilder partitionAttributesBuilder = partitionAttributesBuilderFunction
                    .apply(RedisProtocolSupport.Command.SINTERSTORE);
        partitionAttributesBuilder.addKey(destination);
        partitionAttributesBuilder.addKey(key1);
        partitionAttributesBuilder.addKey(key2);
        partitionAttributesBuilder.addKey(key3);
        final Single<Long> result = partitionedRedisClient.request(partitionAttributesBuilder.build(), request,
                    Long.class);
        return result;
    }

    @Override
    public Single<Long> sinterstore(@RedisProtocolSupport.Key final Buffer destination,
                                    @RedisProtocolSupport.Key final Collection<Buffer> keys) {
        requireNonNull(destination);
        requireNonNull(keys);
        final int len = 2 + keys.size();
        int keysCapacity = 0;
        if (keys instanceof List && keys instanceof RandomAccess) {
            final List<Buffer> list = (List<Buffer>) keys;
            for (int i = 0; i < list.size(); ++i) {
                final Buffer arg = list.get(i);
                keysCapacity += calculateRequestArgumentSize(arg);
            }
        } else {
            for (Buffer arg : keys) {
                keysCapacity += calculateRequestArgumentSize(arg);
            }
        }
        final int capacity = calculateInitialCommandBufferSize(len, RedisProtocolSupport.Command.SINTERSTORE) +
                    calculateRequestArgumentSize(destination) + keysCapacity;
        Buffer buffer = partitionedRedisClient.executionContext().bufferAllocator().newBuffer(capacity);
        writeRequestArraySize(buffer, len);
        RedisProtocolSupport.Command.SINTERSTORE.encodeTo(buffer);
        writeRequestArgument(buffer, destination);
        if (keys instanceof List && keys instanceof RandomAccess) {
            final List<Buffer> list = (List<Buffer>) keys;
            for (int i = 0; i < list.size(); ++i) {
                final Buffer arg = list.get(i);
                writeRequestArgument(buffer, arg);
            }
        } else {
            for (Buffer arg : keys) {
                writeRequestArgument(buffer, arg);
            }
        }
        final RedisRequest request = newRequest(RedisProtocolSupport.Command.SINTERSTORE, buffer);
        final RedisPartitionAttributesBuilder partitionAttributesBuilder = partitionAttributesBuilderFunction
                    .apply(RedisProtocolSupport.Command.SINTERSTORE);
        partitionAttributesBuilder.addKey(destination);
        addBufferKeysToAttributeBuilder(keys, partitionAttributesBuilder);
        final Single<Long> result = partitionedRedisClient.request(partitionAttributesBuilder.build(), request,
                    Long.class);
        return result;
    }

    @Override
    public Single<Long> sismember(@RedisProtocolSupport.Key final Buffer key, final Buffer member) {
        requireNonNull(key);
        requireNonNull(member);
        final int len = 3;
        final int capacity = calculateInitialCommandBufferSize(len, RedisProtocolSupport.Command.SISMEMBER) +
                    calculateRequestArgumentSize(key) + calculateRequestArgumentSize(member);
        Buffer buffer = partitionedRedisClient.executionContext().bufferAllocator().newBuffer(capacity);
        writeRequestArraySize(buffer, len);
        RedisProtocolSupport.Command.SISMEMBER.encodeTo(buffer);
        writeRequestArgument(buffer, key);
        writeRequestArgument(buffer, member);
        final RedisRequest request = newRequest(RedisProtocolSupport.Command.SISMEMBER, buffer);
        final RedisPartitionAttributesBuilder partitionAttributesBuilder = partitionAttributesBuilderFunction
                    .apply(RedisProtocolSupport.Command.SISMEMBER);
        partitionAttributesBuilder.addKey(key);
        final Single<Long> result = partitionedRedisClient.request(partitionAttributesBuilder.build(), request,
                    Long.class);
        return result;
    }

    @Override
    public Single<String> slaveof(final Buffer host, final Buffer port) {
        requireNonNull(host);
        requireNonNull(port);
        final int len = 3;
        final int capacity = calculateInitialCommandBufferSize(len, RedisProtocolSupport.Command.SLAVEOF) +
                    calculateRequestArgumentSize(host) + calculateRequestArgumentSize(port);
        Buffer buffer = partitionedRedisClient.executionContext().bufferAllocator().newBuffer(capacity);
        writeRequestArraySize(buffer, len);
        RedisProtocolSupport.Command.SLAVEOF.encodeTo(buffer);
        writeRequestArgument(buffer, host);
        writeRequestArgument(buffer, port);
        final RedisRequest request = newRequest(RedisProtocolSupport.Command.SLAVEOF, buffer);
        final RedisPartitionAttributesBuilder partitionAttributesBuilder = partitionAttributesBuilderFunction
                    .apply(RedisProtocolSupport.Command.SLAVEOF);
        final Single<String> result = partitionedRedisClient.request(partitionAttributesBuilder.build(), request,
                    String.class);
        return result;
    }

    @Override
    public <T> Single<List<T>> slowlog(final Buffer subcommand) {
        requireNonNull(subcommand);
        final int len = 2;
        final int capacity = calculateInitialCommandBufferSize(len, RedisProtocolSupport.Command.SLOWLOG) +
                    calculateRequestArgumentSize(subcommand);
        Buffer buffer = partitionedRedisClient.executionContext().bufferAllocator().newBuffer(capacity);
        writeRequestArraySize(buffer, len);
        RedisProtocolSupport.Command.SLOWLOG.encodeTo(buffer);
        writeRequestArgument(buffer, subcommand);
        final RedisRequest request = newRequest(RedisProtocolSupport.Command.SLOWLOG, buffer);
        final RedisPartitionAttributesBuilder partitionAttributesBuilder = partitionAttributesBuilderFunction
                    .apply(RedisProtocolSupport.Command.SLOWLOG);
        final Single<List<T>> result = (Single) partitionedRedisClient.request(partitionAttributesBuilder.build(),
                    request, List.class);
        return result;
    }

    @Override
    public <T> Single<List<T>> slowlog(final Buffer subcommand, @Nullable final Buffer argument) {
        requireNonNull(subcommand);
        final int len = 2 + (argument == null ? 0 : 1);
        final int capacity = calculateInitialCommandBufferSize(len, RedisProtocolSupport.Command.SLOWLOG) +
                    calculateRequestArgumentSize(subcommand) +
                    (argument == null ? 0 : calculateRequestArgumentSize(argument));
        Buffer buffer = partitionedRedisClient.executionContext().bufferAllocator().newBuffer(capacity);
        writeRequestArraySize(buffer, len);
        RedisProtocolSupport.Command.SLOWLOG.encodeTo(buffer);
        writeRequestArgument(buffer, subcommand);
        if (argument != null) {
            writeRequestArgument(buffer, argument);
        }
        final RedisRequest request = newRequest(RedisProtocolSupport.Command.SLOWLOG, buffer);
        final RedisPartitionAttributesBuilder partitionAttributesBuilder = partitionAttributesBuilderFunction
                    .apply(RedisProtocolSupport.Command.SLOWLOG);
        final Single<List<T>> result = (Single) partitionedRedisClient.request(partitionAttributesBuilder.build(),
                    request, List.class);
        return result;
    }

    @Override
    public <T> Single<List<T>> smembers(@RedisProtocolSupport.Key final Buffer key) {
        requireNonNull(key);
        final int len = 2;
        final int capacity = calculateInitialCommandBufferSize(len, RedisProtocolSupport.Command.SMEMBERS) +
                    calculateRequestArgumentSize(key);
        Buffer buffer = partitionedRedisClient.executionContext().bufferAllocator().newBuffer(capacity);
        writeRequestArraySize(buffer, len);
        RedisProtocolSupport.Command.SMEMBERS.encodeTo(buffer);
        writeRequestArgument(buffer, key);
        final RedisRequest request = newRequest(RedisProtocolSupport.Command.SMEMBERS, buffer);
        final RedisPartitionAttributesBuilder partitionAttributesBuilder = partitionAttributesBuilderFunction
                    .apply(RedisProtocolSupport.Command.SMEMBERS);
        partitionAttributesBuilder.addKey(key);
        final Single<List<T>> result = (Single) partitionedRedisClient.request(partitionAttributesBuilder.build(),
                    request, List.class);
        return result;
    }

    @Override
    public Single<Long> smove(@RedisProtocolSupport.Key final Buffer source,
                              @RedisProtocolSupport.Key final Buffer destination, final Buffer member) {
        requireNonNull(source);
        requireNonNull(destination);
        requireNonNull(member);
        final int len = 4;
        final int capacity = calculateInitialCommandBufferSize(len, RedisProtocolSupport.Command.SMOVE) +
                    calculateRequestArgumentSize(source) + calculateRequestArgumentSize(destination) +
                    calculateRequestArgumentSize(member);
        Buffer buffer = partitionedRedisClient.executionContext().bufferAllocator().newBuffer(capacity);
        writeRequestArraySize(buffer, len);
        RedisProtocolSupport.Command.SMOVE.encodeTo(buffer);
        writeRequestArgument(buffer, source);
        writeRequestArgument(buffer, destination);
        writeRequestArgument(buffer, member);
        final RedisRequest request = newRequest(RedisProtocolSupport.Command.SMOVE, buffer);
        final RedisPartitionAttributesBuilder partitionAttributesBuilder = partitionAttributesBuilderFunction
                    .apply(RedisProtocolSupport.Command.SMOVE);
        partitionAttributesBuilder.addKey(source);
        partitionAttributesBuilder.addKey(destination);
        final Single<Long> result = partitionedRedisClient.request(partitionAttributesBuilder.build(), request,
                    Long.class);
        return result;
    }

    @Override
    public <T> Single<List<T>> sort(@RedisProtocolSupport.Key final Buffer key) {
        requireNonNull(key);
        final int len = 2;
        final int capacity = calculateInitialCommandBufferSize(len, RedisProtocolSupport.Command.SORT) +
                    calculateRequestArgumentSize(key);
        Buffer buffer = partitionedRedisClient.executionContext().bufferAllocator().newBuffer(capacity);
        writeRequestArraySize(buffer, len);
        RedisProtocolSupport.Command.SORT.encodeTo(buffer);
        writeRequestArgument(buffer, key);
        final RedisRequest request = newRequest(RedisProtocolSupport.Command.SORT, buffer);
        final RedisPartitionAttributesBuilder partitionAttributesBuilder = partitionAttributesBuilderFunction
                    .apply(RedisProtocolSupport.Command.SORT);
        partitionAttributesBuilder.addKey(key);
        final Single<List<T>> result = (Single) partitionedRedisClient.request(partitionAttributesBuilder.build(),
                    request, List.class);
        return result;
    }

    @Override
    public <T> Single<List<T>> sort(@RedisProtocolSupport.Key final Buffer key, @Nullable final Buffer byPattern,
                                    @Nullable final RedisProtocolSupport.OffsetCount offsetCount,
                                    final Collection<Buffer> getPatterns,
                                    @Nullable final RedisProtocolSupport.SortOrder order,
                                    @Nullable final RedisProtocolSupport.SortSorting sorting) {
        requireNonNull(key);
        requireNonNull(getPatterns);
        final int len = 3 + (byPattern == null ? 0 : 2) +
                    (offsetCount == null ? 0 : RedisProtocolSupport.OffsetCount.SIZE) + getPatterns.size() +
                    (order == null ? 0 : 1) + (sorting == null ? 0 : 1);
        int getPatternsCapacity = 0;
        if (getPatterns instanceof List && getPatterns instanceof RandomAccess) {
            final List<Buffer> list = (List<Buffer>) getPatterns;
            for (int i = 0; i < list.size(); ++i) {
                final Buffer arg = list.get(i);
                getPatternsCapacity += calculateRequestArgumentSize(arg);
            }
        } else {
            for (Buffer arg : getPatterns) {
                getPatternsCapacity += calculateRequestArgumentSize(arg);
            }
        }
        final int capacity = calculateInitialCommandBufferSize(len, RedisProtocolSupport.Command.SORT) +
                    calculateRequestArgumentSize(key) +
                    (byPattern == null ? 0 : RedisProtocolSupport.SubCommand.BY.encodedByteCount()) +
                    (byPattern == null ? 0 : calculateRequestArgumentSize(byPattern)) +
                    (offsetCount == null ? 0 : offsetCount.encodedByteCount()) +
                    RedisProtocolSupport.SubCommand.GET.encodedByteCount() + getPatternsCapacity +
                    (order == null ? 0 : order.encodedByteCount()) + (sorting == null ? 0 : sorting.encodedByteCount());
        Buffer buffer = partitionedRedisClient.executionContext().bufferAllocator().newBuffer(capacity);
        writeRequestArraySize(buffer, len);
        RedisProtocolSupport.Command.SORT.encodeTo(buffer);
        writeRequestArgument(buffer, key);
        if (byPattern != null) {
            RedisProtocolSupport.SubCommand.BY.encodeTo(buffer);
            writeRequestArgument(buffer, byPattern);
        }
        if (offsetCount != null) {
            offsetCount.encodeTo(buffer);
        }
        RedisProtocolSupport.SubCommand.GET.encodeTo(buffer);
        if (getPatterns instanceof List && getPatterns instanceof RandomAccess) {
            final List<Buffer> list = (List<Buffer>) getPatterns;
            for (int i = 0; i < list.size(); ++i) {
                final Buffer arg = list.get(i);
                writeRequestArgument(buffer, arg);
            }
        } else {
            for (Buffer arg : getPatterns) {
                writeRequestArgument(buffer, arg);
            }
        }
        if (order != null) {
            order.encodeTo(buffer);
        }
        if (sorting != null) {
            sorting.encodeTo(buffer);
        }
        final RedisRequest request = newRequest(RedisProtocolSupport.Command.SORT, buffer);
        final RedisPartitionAttributesBuilder partitionAttributesBuilder = partitionAttributesBuilderFunction
                    .apply(RedisProtocolSupport.Command.SORT);
        partitionAttributesBuilder.addKey(key);
        final Single<List<T>> result = (Single) partitionedRedisClient.request(partitionAttributesBuilder.build(),
                    request, List.class);
        return result;
    }

    @Override
    public Single<Long> sort(@RedisProtocolSupport.Key final Buffer key,
                             @RedisProtocolSupport.Key final Buffer storeDestination) {
        requireNonNull(key);
        requireNonNull(storeDestination);
        final int len = 4;
        final int capacity = calculateInitialCommandBufferSize(len, RedisProtocolSupport.Command.SORT) +
                    calculateRequestArgumentSize(key) + RedisProtocolSupport.SubCommand.STORE.encodedByteCount() +
                    calculateRequestArgumentSize(storeDestination);
        Buffer buffer = partitionedRedisClient.executionContext().bufferAllocator().newBuffer(capacity);
        writeRequestArraySize(buffer, len);
        RedisProtocolSupport.Command.SORT.encodeTo(buffer);
        writeRequestArgument(buffer, key);
        RedisProtocolSupport.SubCommand.STORE.encodeTo(buffer);
        writeRequestArgument(buffer, storeDestination);
        final RedisRequest request = newRequest(RedisProtocolSupport.Command.SORT, buffer);
        final RedisPartitionAttributesBuilder partitionAttributesBuilder = partitionAttributesBuilderFunction
                    .apply(RedisProtocolSupport.Command.SORT);
        partitionAttributesBuilder.addKey(key);
        partitionAttributesBuilder.addKey(storeDestination);
        final Single<Long> result = partitionedRedisClient.request(partitionAttributesBuilder.build(), request,
                    Long.class);
        return result;
    }

    @Override
    public Single<Long> sort(@RedisProtocolSupport.Key final Buffer key,
                             @RedisProtocolSupport.Key final Buffer storeDestination, @Nullable final Buffer byPattern,
                             @Nullable final RedisProtocolSupport.OffsetCount offsetCount,
                             final Collection<Buffer> getPatterns, @Nullable final RedisProtocolSupport.SortOrder order,
                             @Nullable final RedisProtocolSupport.SortSorting sorting) {
        requireNonNull(key);
        requireNonNull(storeDestination);
        requireNonNull(getPatterns);
        final int len = 5 + (byPattern == null ? 0 : 2) +
                    (offsetCount == null ? 0 : RedisProtocolSupport.OffsetCount.SIZE) + getPatterns.size() +
                    (order == null ? 0 : 1) + (sorting == null ? 0 : 1);
        int getPatternsCapacity = 0;
        if (getPatterns instanceof List && getPatterns instanceof RandomAccess) {
            final List<Buffer> list = (List<Buffer>) getPatterns;
            for (int i = 0; i < list.size(); ++i) {
                final Buffer arg = list.get(i);
                getPatternsCapacity += calculateRequestArgumentSize(arg);
            }
        } else {
            for (Buffer arg : getPatterns) {
                getPatternsCapacity += calculateRequestArgumentSize(arg);
            }
        }
        final int capacity = calculateInitialCommandBufferSize(len, RedisProtocolSupport.Command.SORT) +
                    calculateRequestArgumentSize(key) + RedisProtocolSupport.SubCommand.STORE.encodedByteCount() +
                    calculateRequestArgumentSize(storeDestination) +
                    (byPattern == null ? 0 : RedisProtocolSupport.SubCommand.BY.encodedByteCount()) +
                    (byPattern == null ? 0 : calculateRequestArgumentSize(byPattern)) +
                    (offsetCount == null ? 0 : offsetCount.encodedByteCount()) +
                    RedisProtocolSupport.SubCommand.GET.encodedByteCount() + getPatternsCapacity +
                    (order == null ? 0 : order.encodedByteCount()) + (sorting == null ? 0 : sorting.encodedByteCount());
        Buffer buffer = partitionedRedisClient.executionContext().bufferAllocator().newBuffer(capacity);
        writeRequestArraySize(buffer, len);
        RedisProtocolSupport.Command.SORT.encodeTo(buffer);
        writeRequestArgument(buffer, key);
        RedisProtocolSupport.SubCommand.STORE.encodeTo(buffer);
        writeRequestArgument(buffer, storeDestination);
        if (byPattern != null) {
            RedisProtocolSupport.SubCommand.BY.encodeTo(buffer);
            writeRequestArgument(buffer, byPattern);
        }
        if (offsetCount != null) {
            offsetCount.encodeTo(buffer);
        }
        RedisProtocolSupport.SubCommand.GET.encodeTo(buffer);
        if (getPatterns instanceof List && getPatterns instanceof RandomAccess) {
            final List<Buffer> list = (List<Buffer>) getPatterns;
            for (int i = 0; i < list.size(); ++i) {
                final Buffer arg = list.get(i);
                writeRequestArgument(buffer, arg);
            }
        } else {
            for (Buffer arg : getPatterns) {
                writeRequestArgument(buffer, arg);
            }
        }
        if (order != null) {
            order.encodeTo(buffer);
        }
        if (sorting != null) {
            sorting.encodeTo(buffer);
        }
        final RedisRequest request = newRequest(RedisProtocolSupport.Command.SORT, buffer);
        final RedisPartitionAttributesBuilder partitionAttributesBuilder = partitionAttributesBuilderFunction
                    .apply(RedisProtocolSupport.Command.SORT);
        partitionAttributesBuilder.addKey(key);
        partitionAttributesBuilder.addKey(storeDestination);
        final Single<Long> result = partitionedRedisClient.request(partitionAttributesBuilder.build(), request,
                    Long.class);
        return result;
    }

    @Override
    public Single<Buffer> spop(@RedisProtocolSupport.Key final Buffer key) {
        requireNonNull(key);
        final int len = 2;
        final int capacity = calculateInitialCommandBufferSize(len, RedisProtocolSupport.Command.SPOP) +
                    calculateRequestArgumentSize(key);
        Buffer buffer = partitionedRedisClient.executionContext().bufferAllocator().newBuffer(capacity);
        writeRequestArraySize(buffer, len);
        RedisProtocolSupport.Command.SPOP.encodeTo(buffer);
        writeRequestArgument(buffer, key);
        final RedisRequest request = newRequest(RedisProtocolSupport.Command.SPOP, buffer);
        final RedisPartitionAttributesBuilder partitionAttributesBuilder = partitionAttributesBuilderFunction
                    .apply(RedisProtocolSupport.Command.SPOP);
        partitionAttributesBuilder.addKey(key);
        final Single<Buffer> result = partitionedRedisClient.request(partitionAttributesBuilder.build(), request,
                    Buffer.class);
        return result;
    }

    @Override
    public Single<Buffer> spop(@RedisProtocolSupport.Key final Buffer key, @Nullable final Long count) {
        requireNonNull(key);
        final int len = 2 + (count == null ? 0 : 1);
        final int capacity = calculateInitialCommandBufferSize(len, RedisProtocolSupport.Command.SPOP) +
                    calculateRequestArgumentSize(key) + (count == null ? 0 : calculateRequestArgumentSize(count));
        Buffer buffer = partitionedRedisClient.executionContext().bufferAllocator().newBuffer(capacity);
        writeRequestArraySize(buffer, len);
        RedisProtocolSupport.Command.SPOP.encodeTo(buffer);
        writeRequestArgument(buffer, key);
        if (count != null) {
            writeRequestArgument(buffer, count);
        }
        final RedisRequest request = newRequest(RedisProtocolSupport.Command.SPOP, buffer);
        final RedisPartitionAttributesBuilder partitionAttributesBuilder = partitionAttributesBuilderFunction
                    .apply(RedisProtocolSupport.Command.SPOP);
        partitionAttributesBuilder.addKey(key);
        final Single<Buffer> result = partitionedRedisClient.request(partitionAttributesBuilder.build(), request,
                    Buffer.class);
        return result;
    }

    @Override
    public Single<Buffer> srandmember(@RedisProtocolSupport.Key final Buffer key) {
        requireNonNull(key);
        final int len = 2;
        final int capacity = calculateInitialCommandBufferSize(len, RedisProtocolSupport.Command.SRANDMEMBER) +
                    calculateRequestArgumentSize(key);
        Buffer buffer = partitionedRedisClient.executionContext().bufferAllocator().newBuffer(capacity);
        writeRequestArraySize(buffer, len);
        RedisProtocolSupport.Command.SRANDMEMBER.encodeTo(buffer);
        writeRequestArgument(buffer, key);
        final RedisRequest request = newRequest(RedisProtocolSupport.Command.SRANDMEMBER, buffer);
        final RedisPartitionAttributesBuilder partitionAttributesBuilder = partitionAttributesBuilderFunction
                    .apply(RedisProtocolSupport.Command.SRANDMEMBER);
        partitionAttributesBuilder.addKey(key);
        final Single<Buffer> result = partitionedRedisClient.request(partitionAttributesBuilder.build(), request,
                    Buffer.class);
        return result;
    }

    @Override
    public Single<List<Buffer>> srandmember(@RedisProtocolSupport.Key final Buffer key, final long count) {
        requireNonNull(key);
        final int len = 3;
        final int capacity = calculateInitialCommandBufferSize(len, RedisProtocolSupport.Command.SRANDMEMBER) +
                    calculateRequestArgumentSize(key) + calculateRequestArgumentSize(count);
        Buffer buffer = partitionedRedisClient.executionContext().bufferAllocator().newBuffer(capacity);
        writeRequestArraySize(buffer, len);
        RedisProtocolSupport.Command.SRANDMEMBER.encodeTo(buffer);
        writeRequestArgument(buffer, key);
        writeRequestArgument(buffer, count);
        final RedisRequest request = newRequest(RedisProtocolSupport.Command.SRANDMEMBER, buffer);
        final RedisPartitionAttributesBuilder partitionAttributesBuilder = partitionAttributesBuilderFunction
                    .apply(RedisProtocolSupport.Command.SRANDMEMBER);
        partitionAttributesBuilder.addKey(key);
        final Single<List<Buffer>> result = (Single) partitionedRedisClient.request(partitionAttributesBuilder.build(),
                    request, List.class);
        return result;
    }

    @Override
    public Single<Long> srem(@RedisProtocolSupport.Key final Buffer key, final Buffer member) {
        requireNonNull(key);
        requireNonNull(member);
        final int len = 3;
        final int capacity = calculateInitialCommandBufferSize(len, RedisProtocolSupport.Command.SREM) +
                    calculateRequestArgumentSize(key) + calculateRequestArgumentSize(member);
        Buffer buffer = partitionedRedisClient.executionContext().bufferAllocator().newBuffer(capacity);
        writeRequestArraySize(buffer, len);
        RedisProtocolSupport.Command.SREM.encodeTo(buffer);
        writeRequestArgument(buffer, key);
        writeRequestArgument(buffer, member);
        final RedisRequest request = newRequest(RedisProtocolSupport.Command.SREM, buffer);
        final RedisPartitionAttributesBuilder partitionAttributesBuilder = partitionAttributesBuilderFunction
                    .apply(RedisProtocolSupport.Command.SREM);
        partitionAttributesBuilder.addKey(key);
        final Single<Long> result = partitionedRedisClient.request(partitionAttributesBuilder.build(), request,
                    Long.class);
        return result;
    }

    @Override
    public Single<Long> srem(@RedisProtocolSupport.Key final Buffer key, final Buffer member1, final Buffer member2) {
        requireNonNull(key);
        requireNonNull(member1);
        requireNonNull(member2);
        final int len = 4;
        final int capacity = calculateInitialCommandBufferSize(len, RedisProtocolSupport.Command.SREM) +
                    calculateRequestArgumentSize(key) + calculateRequestArgumentSize(member1) +
                    calculateRequestArgumentSize(member2);
        Buffer buffer = partitionedRedisClient.executionContext().bufferAllocator().newBuffer(capacity);
        writeRequestArraySize(buffer, len);
        RedisProtocolSupport.Command.SREM.encodeTo(buffer);
        writeRequestArgument(buffer, key);
        writeRequestArgument(buffer, member1);
        writeRequestArgument(buffer, member2);
        final RedisRequest request = newRequest(RedisProtocolSupport.Command.SREM, buffer);
        final RedisPartitionAttributesBuilder partitionAttributesBuilder = partitionAttributesBuilderFunction
                    .apply(RedisProtocolSupport.Command.SREM);
        partitionAttributesBuilder.addKey(key);
        final Single<Long> result = partitionedRedisClient.request(partitionAttributesBuilder.build(), request,
                    Long.class);
        return result;
    }

    @Override
    public Single<Long> srem(@RedisProtocolSupport.Key final Buffer key, final Buffer member1, final Buffer member2,
                             final Buffer member3) {
        requireNonNull(key);
        requireNonNull(member1);
        requireNonNull(member2);
        requireNonNull(member3);
        final int len = 5;
        final int capacity = calculateInitialCommandBufferSize(len, RedisProtocolSupport.Command.SREM) +
                    calculateRequestArgumentSize(key) + calculateRequestArgumentSize(member1) +
                    calculateRequestArgumentSize(member2) + calculateRequestArgumentSize(member3);
        Buffer buffer = partitionedRedisClient.executionContext().bufferAllocator().newBuffer(capacity);
        writeRequestArraySize(buffer, len);
        RedisProtocolSupport.Command.SREM.encodeTo(buffer);
        writeRequestArgument(buffer, key);
        writeRequestArgument(buffer, member1);
        writeRequestArgument(buffer, member2);
        writeRequestArgument(buffer, member3);
        final RedisRequest request = newRequest(RedisProtocolSupport.Command.SREM, buffer);
        final RedisPartitionAttributesBuilder partitionAttributesBuilder = partitionAttributesBuilderFunction
                    .apply(RedisProtocolSupport.Command.SREM);
        partitionAttributesBuilder.addKey(key);
        final Single<Long> result = partitionedRedisClient.request(partitionAttributesBuilder.build(), request,
                    Long.class);
        return result;
    }

    @Override
    public Single<Long> srem(@RedisProtocolSupport.Key final Buffer key, final Collection<Buffer> members) {
        requireNonNull(key);
        requireNonNull(members);
        final int len = 2 + members.size();
        int membersCapacity = 0;
        if (members instanceof List && members instanceof RandomAccess) {
            final List<Buffer> list = (List<Buffer>) members;
            for (int i = 0; i < list.size(); ++i) {
                final Buffer arg = list.get(i);
                membersCapacity += calculateRequestArgumentSize(arg);
            }
        } else {
            for (Buffer arg : members) {
                membersCapacity += calculateRequestArgumentSize(arg);
            }
        }
        final int capacity = calculateInitialCommandBufferSize(len, RedisProtocolSupport.Command.SREM) +
                    calculateRequestArgumentSize(key) + membersCapacity;
        Buffer buffer = partitionedRedisClient.executionContext().bufferAllocator().newBuffer(capacity);
        writeRequestArraySize(buffer, len);
        RedisProtocolSupport.Command.SREM.encodeTo(buffer);
        writeRequestArgument(buffer, key);
        if (members instanceof List && members instanceof RandomAccess) {
            final List<Buffer> list = (List<Buffer>) members;
            for (int i = 0; i < list.size(); ++i) {
                final Buffer arg = list.get(i);
                writeRequestArgument(buffer, arg);
            }
        } else {
            for (Buffer arg : members) {
                writeRequestArgument(buffer, arg);
            }
        }
        final RedisRequest request = newRequest(RedisProtocolSupport.Command.SREM, buffer);
        final RedisPartitionAttributesBuilder partitionAttributesBuilder = partitionAttributesBuilderFunction
                    .apply(RedisProtocolSupport.Command.SREM);
        partitionAttributesBuilder.addKey(key);
        final Single<Long> result = partitionedRedisClient.request(partitionAttributesBuilder.build(), request,
                    Long.class);
        return result;
    }

    @Override
    public <T> Single<List<T>> sscan(@RedisProtocolSupport.Key final Buffer key, final long cursor) {
        requireNonNull(key);
        final int len = 3;
        final int capacity = calculateInitialCommandBufferSize(len, RedisProtocolSupport.Command.SSCAN) +
                    calculateRequestArgumentSize(key) + calculateRequestArgumentSize(cursor);
        Buffer buffer = partitionedRedisClient.executionContext().bufferAllocator().newBuffer(capacity);
        writeRequestArraySize(buffer, len);
        RedisProtocolSupport.Command.SSCAN.encodeTo(buffer);
        writeRequestArgument(buffer, key);
        writeRequestArgument(buffer, cursor);
        final RedisRequest request = newRequest(RedisProtocolSupport.Command.SSCAN, buffer);
        final RedisPartitionAttributesBuilder partitionAttributesBuilder = partitionAttributesBuilderFunction
                    .apply(RedisProtocolSupport.Command.SSCAN);
        partitionAttributesBuilder.addKey(key);
        final Single<List<T>> result = (Single) partitionedRedisClient.request(partitionAttributesBuilder.build(),
                    request, List.class);
        return result;
    }

    @Override
    public <T> Single<List<T>> sscan(@RedisProtocolSupport.Key final Buffer key, final long cursor,
                                     @Nullable final Buffer matchPattern, @Nullable final Long count) {
        requireNonNull(key);
        final int len = 3 + (matchPattern == null ? 0 : 2) + (count == null ? 0 : 2);
        final int capacity = calculateInitialCommandBufferSize(len, RedisProtocolSupport.Command.SSCAN) +
                    calculateRequestArgumentSize(key) + calculateRequestArgumentSize(cursor) +
                    (matchPattern == null ? 0 : RedisProtocolSupport.SubCommand.MATCH.encodedByteCount()) +
                    (matchPattern == null ? 0 : calculateRequestArgumentSize(matchPattern)) +
                    (count == null ? 0 : RedisProtocolSupport.SubCommand.COUNT.encodedByteCount()) +
                    (count == null ? 0 : calculateRequestArgumentSize(count));
        Buffer buffer = partitionedRedisClient.executionContext().bufferAllocator().newBuffer(capacity);
        writeRequestArraySize(buffer, len);
        RedisProtocolSupport.Command.SSCAN.encodeTo(buffer);
        writeRequestArgument(buffer, key);
        writeRequestArgument(buffer, cursor);
        if (matchPattern != null) {
            RedisProtocolSupport.SubCommand.MATCH.encodeTo(buffer);
            writeRequestArgument(buffer, matchPattern);
        }
        if (count != null) {
            RedisProtocolSupport.SubCommand.COUNT.encodeTo(buffer);
            writeRequestArgument(buffer, count);
        }
        final RedisRequest request = newRequest(RedisProtocolSupport.Command.SSCAN, buffer);
        final RedisPartitionAttributesBuilder partitionAttributesBuilder = partitionAttributesBuilderFunction
                    .apply(RedisProtocolSupport.Command.SSCAN);
        partitionAttributesBuilder.addKey(key);
        final Single<List<T>> result = (Single) partitionedRedisClient.request(partitionAttributesBuilder.build(),
                    request, List.class);
        return result;
    }

    @Override
    public Single<Long> strlen(@RedisProtocolSupport.Key final Buffer key) {
        requireNonNull(key);
        final int len = 2;
        final int capacity = calculateInitialCommandBufferSize(len, RedisProtocolSupport.Command.STRLEN) +
                    calculateRequestArgumentSize(key);
        Buffer buffer = partitionedRedisClient.executionContext().bufferAllocator().newBuffer(capacity);
        writeRequestArraySize(buffer, len);
        RedisProtocolSupport.Command.STRLEN.encodeTo(buffer);
        writeRequestArgument(buffer, key);
        final RedisRequest request = newRequest(RedisProtocolSupport.Command.STRLEN, buffer);
        final RedisPartitionAttributesBuilder partitionAttributesBuilder = partitionAttributesBuilderFunction
                    .apply(RedisProtocolSupport.Command.STRLEN);
        partitionAttributesBuilder.addKey(key);
        final Single<Long> result = partitionedRedisClient.request(partitionAttributesBuilder.build(), request,
                    Long.class);
        return result;
    }

    @Override
    public Single<PubSubBufferRedisConnection> subscribe(final Buffer channel) {
        requireNonNull(channel);
        final int len = 2;
        final int capacity = calculateInitialCommandBufferSize(len, RedisProtocolSupport.Command.SUBSCRIBE) +
                    calculateRequestArgumentSize(channel);
        Buffer buffer = partitionedRedisClient.executionContext().bufferAllocator().newBuffer(capacity);
        writeRequestArraySize(buffer, len);
        RedisProtocolSupport.Command.SUBSCRIBE.encodeTo(buffer);
        writeRequestArgument(buffer, channel);
        final RedisRequest request = newRequest(RedisProtocolSupport.Command.SUBSCRIBE, buffer);
        final RedisPartitionAttributesBuilder partitionAttributesBuilder = partitionAttributesBuilderFunction
                    .apply(RedisProtocolSupport.Command.SUBSCRIBE);
        return reserveConnection(partitionedRedisClient, partitionAttributesBuilder.build(), request,
                    (rcnx, pub) -> new DefaultPubSubBufferRedisConnection(rcnx,
                                pub.map(msg -> (PubSubRedisMessage) msg)));
    }

    @Override
    public <T> Single<List<T>> sunion(@RedisProtocolSupport.Key final Buffer key) {
        requireNonNull(key);
        final int len = 2;
        final int capacity = calculateInitialCommandBufferSize(len, RedisProtocolSupport.Command.SUNION) +
                    calculateRequestArgumentSize(key);
        Buffer buffer = partitionedRedisClient.executionContext().bufferAllocator().newBuffer(capacity);
        writeRequestArraySize(buffer, len);
        RedisProtocolSupport.Command.SUNION.encodeTo(buffer);
        writeRequestArgument(buffer, key);
        final RedisRequest request = newRequest(RedisProtocolSupport.Command.SUNION, buffer);
        final RedisPartitionAttributesBuilder partitionAttributesBuilder = partitionAttributesBuilderFunction
                    .apply(RedisProtocolSupport.Command.SUNION);
        partitionAttributesBuilder.addKey(key);
        final Single<List<T>> result = (Single) partitionedRedisClient.request(partitionAttributesBuilder.build(),
                    request, List.class);
        return result;
    }

    @Override
    public <T> Single<List<T>> sunion(@RedisProtocolSupport.Key final Buffer key1,
                                      @RedisProtocolSupport.Key final Buffer key2) {
        requireNonNull(key1);
        requireNonNull(key2);
        final int len = 3;
        final int capacity = calculateInitialCommandBufferSize(len, RedisProtocolSupport.Command.SUNION) +
                    calculateRequestArgumentSize(key1) + calculateRequestArgumentSize(key2);
        Buffer buffer = partitionedRedisClient.executionContext().bufferAllocator().newBuffer(capacity);
        writeRequestArraySize(buffer, len);
        RedisProtocolSupport.Command.SUNION.encodeTo(buffer);
        writeRequestArgument(buffer, key1);
        writeRequestArgument(buffer, key2);
        final RedisRequest request = newRequest(RedisProtocolSupport.Command.SUNION, buffer);
        final RedisPartitionAttributesBuilder partitionAttributesBuilder = partitionAttributesBuilderFunction
                    .apply(RedisProtocolSupport.Command.SUNION);
        partitionAttributesBuilder.addKey(key1);
        partitionAttributesBuilder.addKey(key2);
        final Single<List<T>> result = (Single) partitionedRedisClient.request(partitionAttributesBuilder.build(),
                    request, List.class);
        return result;
    }

    @Override
    public <T> Single<List<T>> sunion(@RedisProtocolSupport.Key final Buffer key1,
                                      @RedisProtocolSupport.Key final Buffer key2,
                                      @RedisProtocolSupport.Key final Buffer key3) {
        requireNonNull(key1);
        requireNonNull(key2);
        requireNonNull(key3);
        final int len = 4;
        final int capacity = calculateInitialCommandBufferSize(len, RedisProtocolSupport.Command.SUNION) +
                    calculateRequestArgumentSize(key1) + calculateRequestArgumentSize(key2) +
                    calculateRequestArgumentSize(key3);
        Buffer buffer = partitionedRedisClient.executionContext().bufferAllocator().newBuffer(capacity);
        writeRequestArraySize(buffer, len);
        RedisProtocolSupport.Command.SUNION.encodeTo(buffer);
        writeRequestArgument(buffer, key1);
        writeRequestArgument(buffer, key2);
        writeRequestArgument(buffer, key3);
        final RedisRequest request = newRequest(RedisProtocolSupport.Command.SUNION, buffer);
        final RedisPartitionAttributesBuilder partitionAttributesBuilder = partitionAttributesBuilderFunction
                    .apply(RedisProtocolSupport.Command.SUNION);
        partitionAttributesBuilder.addKey(key1);
        partitionAttributesBuilder.addKey(key2);
        partitionAttributesBuilder.addKey(key3);
        final Single<List<T>> result = (Single) partitionedRedisClient.request(partitionAttributesBuilder.build(),
                    request, List.class);
        return result;
    }

    @Override
    public <T> Single<List<T>> sunion(@RedisProtocolSupport.Key final Collection<Buffer> keys) {
        requireNonNull(keys);
        final int len = 1 + keys.size();
        int keysCapacity = 0;
        if (keys instanceof List && keys instanceof RandomAccess) {
            final List<Buffer> list = (List<Buffer>) keys;
            for (int i = 0; i < list.size(); ++i) {
                final Buffer arg = list.get(i);
                keysCapacity += calculateRequestArgumentSize(arg);
            }
        } else {
            for (Buffer arg : keys) {
                keysCapacity += calculateRequestArgumentSize(arg);
            }
        }
        final int capacity = calculateInitialCommandBufferSize(len, RedisProtocolSupport.Command.SUNION) + keysCapacity;
        Buffer buffer = partitionedRedisClient.executionContext().bufferAllocator().newBuffer(capacity);
        writeRequestArraySize(buffer, len);
        RedisProtocolSupport.Command.SUNION.encodeTo(buffer);
        if (keys instanceof List && keys instanceof RandomAccess) {
            final List<Buffer> list = (List<Buffer>) keys;
            for (int i = 0; i < list.size(); ++i) {
                final Buffer arg = list.get(i);
                writeRequestArgument(buffer, arg);
            }
        } else {
            for (Buffer arg : keys) {
                writeRequestArgument(buffer, arg);
            }
        }
        final RedisRequest request = newRequest(RedisProtocolSupport.Command.SUNION, buffer);
        final RedisPartitionAttributesBuilder partitionAttributesBuilder = partitionAttributesBuilderFunction
                    .apply(RedisProtocolSupport.Command.SUNION);
        addBufferKeysToAttributeBuilder(keys, partitionAttributesBuilder);
        final Single<List<T>> result = (Single) partitionedRedisClient.request(partitionAttributesBuilder.build(),
                    request, List.class);
        return result;
    }

    @Override
    public Single<Long> sunionstore(@RedisProtocolSupport.Key final Buffer destination,
                                    @RedisProtocolSupport.Key final Buffer key) {
        requireNonNull(destination);
        requireNonNull(key);
        final int len = 3;
        final int capacity = calculateInitialCommandBufferSize(len, RedisProtocolSupport.Command.SUNIONSTORE) +
                    calculateRequestArgumentSize(destination) + calculateRequestArgumentSize(key);
        Buffer buffer = partitionedRedisClient.executionContext().bufferAllocator().newBuffer(capacity);
        writeRequestArraySize(buffer, len);
        RedisProtocolSupport.Command.SUNIONSTORE.encodeTo(buffer);
        writeRequestArgument(buffer, destination);
        writeRequestArgument(buffer, key);
        final RedisRequest request = newRequest(RedisProtocolSupport.Command.SUNIONSTORE, buffer);
        final RedisPartitionAttributesBuilder partitionAttributesBuilder = partitionAttributesBuilderFunction
                    .apply(RedisProtocolSupport.Command.SUNIONSTORE);
        partitionAttributesBuilder.addKey(destination);
        partitionAttributesBuilder.addKey(key);
        final Single<Long> result = partitionedRedisClient.request(partitionAttributesBuilder.build(), request,
                    Long.class);
        return result;
    }

    @Override
    public Single<Long> sunionstore(@RedisProtocolSupport.Key final Buffer destination,
                                    @RedisProtocolSupport.Key final Buffer key1,
                                    @RedisProtocolSupport.Key final Buffer key2) {
        requireNonNull(destination);
        requireNonNull(key1);
        requireNonNull(key2);
        final int len = 4;
        final int capacity = calculateInitialCommandBufferSize(len, RedisProtocolSupport.Command.SUNIONSTORE) +
                    calculateRequestArgumentSize(destination) + calculateRequestArgumentSize(key1) +
                    calculateRequestArgumentSize(key2);
        Buffer buffer = partitionedRedisClient.executionContext().bufferAllocator().newBuffer(capacity);
        writeRequestArraySize(buffer, len);
        RedisProtocolSupport.Command.SUNIONSTORE.encodeTo(buffer);
        writeRequestArgument(buffer, destination);
        writeRequestArgument(buffer, key1);
        writeRequestArgument(buffer, key2);
        final RedisRequest request = newRequest(RedisProtocolSupport.Command.SUNIONSTORE, buffer);
        final RedisPartitionAttributesBuilder partitionAttributesBuilder = partitionAttributesBuilderFunction
                    .apply(RedisProtocolSupport.Command.SUNIONSTORE);
        partitionAttributesBuilder.addKey(destination);
        partitionAttributesBuilder.addKey(key1);
        partitionAttributesBuilder.addKey(key2);
        final Single<Long> result = partitionedRedisClient.request(partitionAttributesBuilder.build(), request,
                    Long.class);
        return result;
    }

    @Override
    public Single<Long> sunionstore(@RedisProtocolSupport.Key final Buffer destination,
                                    @RedisProtocolSupport.Key final Buffer key1,
                                    @RedisProtocolSupport.Key final Buffer key2,
                                    @RedisProtocolSupport.Key final Buffer key3) {
        requireNonNull(destination);
        requireNonNull(key1);
        requireNonNull(key2);
        requireNonNull(key3);
        final int len = 5;
        final int capacity = calculateInitialCommandBufferSize(len, RedisProtocolSupport.Command.SUNIONSTORE) +
                    calculateRequestArgumentSize(destination) + calculateRequestArgumentSize(key1) +
                    calculateRequestArgumentSize(key2) + calculateRequestArgumentSize(key3);
        Buffer buffer = partitionedRedisClient.executionContext().bufferAllocator().newBuffer(capacity);
        writeRequestArraySize(buffer, len);
        RedisProtocolSupport.Command.SUNIONSTORE.encodeTo(buffer);
        writeRequestArgument(buffer, destination);
        writeRequestArgument(buffer, key1);
        writeRequestArgument(buffer, key2);
        writeRequestArgument(buffer, key3);
        final RedisRequest request = newRequest(RedisProtocolSupport.Command.SUNIONSTORE, buffer);
        final RedisPartitionAttributesBuilder partitionAttributesBuilder = partitionAttributesBuilderFunction
                    .apply(RedisProtocolSupport.Command.SUNIONSTORE);
        partitionAttributesBuilder.addKey(destination);
        partitionAttributesBuilder.addKey(key1);
        partitionAttributesBuilder.addKey(key2);
        partitionAttributesBuilder.addKey(key3);
        final Single<Long> result = partitionedRedisClient.request(partitionAttributesBuilder.build(), request,
                    Long.class);
        return result;
    }

    @Override
    public Single<Long> sunionstore(@RedisProtocolSupport.Key final Buffer destination,
                                    @RedisProtocolSupport.Key final Collection<Buffer> keys) {
        requireNonNull(destination);
        requireNonNull(keys);
        final int len = 2 + keys.size();
        int keysCapacity = 0;
        if (keys instanceof List && keys instanceof RandomAccess) {
            final List<Buffer> list = (List<Buffer>) keys;
            for (int i = 0; i < list.size(); ++i) {
                final Buffer arg = list.get(i);
                keysCapacity += calculateRequestArgumentSize(arg);
            }
        } else {
            for (Buffer arg : keys) {
                keysCapacity += calculateRequestArgumentSize(arg);
            }
        }
        final int capacity = calculateInitialCommandBufferSize(len, RedisProtocolSupport.Command.SUNIONSTORE) +
                    calculateRequestArgumentSize(destination) + keysCapacity;
        Buffer buffer = partitionedRedisClient.executionContext().bufferAllocator().newBuffer(capacity);
        writeRequestArraySize(buffer, len);
        RedisProtocolSupport.Command.SUNIONSTORE.encodeTo(buffer);
        writeRequestArgument(buffer, destination);
        if (keys instanceof List && keys instanceof RandomAccess) {
            final List<Buffer> list = (List<Buffer>) keys;
            for (int i = 0; i < list.size(); ++i) {
                final Buffer arg = list.get(i);
                writeRequestArgument(buffer, arg);
            }
        } else {
            for (Buffer arg : keys) {
                writeRequestArgument(buffer, arg);
            }
        }
        final RedisRequest request = newRequest(RedisProtocolSupport.Command.SUNIONSTORE, buffer);
        final RedisPartitionAttributesBuilder partitionAttributesBuilder = partitionAttributesBuilderFunction
                    .apply(RedisProtocolSupport.Command.SUNIONSTORE);
        partitionAttributesBuilder.addKey(destination);
        addBufferKeysToAttributeBuilder(keys, partitionAttributesBuilder);
        final Single<Long> result = partitionedRedisClient.request(partitionAttributesBuilder.build(), request,
                    Long.class);
        return result;
    }

    @Override
    public Single<String> swapdb(final long index, final long index1) {
        final int len = 3;
        final int capacity = calculateInitialCommandBufferSize(len, RedisProtocolSupport.Command.SWAPDB) +
                    calculateRequestArgumentSize(index) + calculateRequestArgumentSize(index1);
        Buffer buffer = partitionedRedisClient.executionContext().bufferAllocator().newBuffer(capacity);
        writeRequestArraySize(buffer, len);
        RedisProtocolSupport.Command.SWAPDB.encodeTo(buffer);
        writeRequestArgument(buffer, index);
        writeRequestArgument(buffer, index1);
        final RedisRequest request = newRequest(RedisProtocolSupport.Command.SWAPDB, buffer);
        final RedisPartitionAttributesBuilder partitionAttributesBuilder = partitionAttributesBuilderFunction
                    .apply(RedisProtocolSupport.Command.SWAPDB);
        final Single<String> result = partitionedRedisClient.request(partitionAttributesBuilder.build(), request,
                    String.class);
        return result;
    }

    @Override
    public <T> Single<List<T>> time() {
        final int len = 1;
        final int capacity = calculateInitialCommandBufferSize(len, RedisProtocolSupport.Command.TIME);
        Buffer buffer = partitionedRedisClient.executionContext().bufferAllocator().newBuffer(capacity);
        writeRequestArraySize(buffer, len);
        RedisProtocolSupport.Command.TIME.encodeTo(buffer);
        final RedisRequest request = newRequest(RedisProtocolSupport.Command.TIME, buffer);
        final RedisPartitionAttributesBuilder partitionAttributesBuilder = partitionAttributesBuilderFunction
                    .apply(RedisProtocolSupport.Command.TIME);
        final Single<List<T>> result = (Single) partitionedRedisClient.request(partitionAttributesBuilder.build(),
                    request, List.class);
        return result;
    }

    @Override
    public Single<Long> touch(@RedisProtocolSupport.Key final Buffer key) {
        requireNonNull(key);
        final int len = 2;
        final int capacity = calculateInitialCommandBufferSize(len, RedisProtocolSupport.Command.TOUCH) +
                    calculateRequestArgumentSize(key);
        Buffer buffer = partitionedRedisClient.executionContext().bufferAllocator().newBuffer(capacity);
        writeRequestArraySize(buffer, len);
        RedisProtocolSupport.Command.TOUCH.encodeTo(buffer);
        writeRequestArgument(buffer, key);
        final RedisRequest request = newRequest(RedisProtocolSupport.Command.TOUCH, buffer);
        final RedisPartitionAttributesBuilder partitionAttributesBuilder = partitionAttributesBuilderFunction
                    .apply(RedisProtocolSupport.Command.TOUCH);
        partitionAttributesBuilder.addKey(key);
        final Single<Long> result = partitionedRedisClient.request(partitionAttributesBuilder.build(), request,
                    Long.class);
        return result;
    }

    @Override
    public Single<Long> touch(@RedisProtocolSupport.Key final Buffer key1,
                              @RedisProtocolSupport.Key final Buffer key2) {
        requireNonNull(key1);
        requireNonNull(key2);
        final int len = 3;
        final int capacity = calculateInitialCommandBufferSize(len, RedisProtocolSupport.Command.TOUCH) +
                    calculateRequestArgumentSize(key1) + calculateRequestArgumentSize(key2);
        Buffer buffer = partitionedRedisClient.executionContext().bufferAllocator().newBuffer(capacity);
        writeRequestArraySize(buffer, len);
        RedisProtocolSupport.Command.TOUCH.encodeTo(buffer);
        writeRequestArgument(buffer, key1);
        writeRequestArgument(buffer, key2);
        final RedisRequest request = newRequest(RedisProtocolSupport.Command.TOUCH, buffer);
        final RedisPartitionAttributesBuilder partitionAttributesBuilder = partitionAttributesBuilderFunction
                    .apply(RedisProtocolSupport.Command.TOUCH);
        partitionAttributesBuilder.addKey(key1);
        partitionAttributesBuilder.addKey(key2);
        final Single<Long> result = partitionedRedisClient.request(partitionAttributesBuilder.build(), request,
                    Long.class);
        return result;
    }

    @Override
    public Single<Long> touch(@RedisProtocolSupport.Key final Buffer key1, @RedisProtocolSupport.Key final Buffer key2,
                              @RedisProtocolSupport.Key final Buffer key3) {
        requireNonNull(key1);
        requireNonNull(key2);
        requireNonNull(key3);
        final int len = 4;
        final int capacity = calculateInitialCommandBufferSize(len, RedisProtocolSupport.Command.TOUCH) +
                    calculateRequestArgumentSize(key1) + calculateRequestArgumentSize(key2) +
                    calculateRequestArgumentSize(key3);
        Buffer buffer = partitionedRedisClient.executionContext().bufferAllocator().newBuffer(capacity);
        writeRequestArraySize(buffer, len);
        RedisProtocolSupport.Command.TOUCH.encodeTo(buffer);
        writeRequestArgument(buffer, key1);
        writeRequestArgument(buffer, key2);
        writeRequestArgument(buffer, key3);
        final RedisRequest request = newRequest(RedisProtocolSupport.Command.TOUCH, buffer);
        final RedisPartitionAttributesBuilder partitionAttributesBuilder = partitionAttributesBuilderFunction
                    .apply(RedisProtocolSupport.Command.TOUCH);
        partitionAttributesBuilder.addKey(key1);
        partitionAttributesBuilder.addKey(key2);
        partitionAttributesBuilder.addKey(key3);
        final Single<Long> result = partitionedRedisClient.request(partitionAttributesBuilder.build(), request,
                    Long.class);
        return result;
    }

    @Override
    public Single<Long> touch(@RedisProtocolSupport.Key final Collection<Buffer> keys) {
        requireNonNull(keys);
        final int len = 1 + keys.size();
        int keysCapacity = 0;
        if (keys instanceof List && keys instanceof RandomAccess) {
            final List<Buffer> list = (List<Buffer>) keys;
            for (int i = 0; i < list.size(); ++i) {
                final Buffer arg = list.get(i);
                keysCapacity += calculateRequestArgumentSize(arg);
            }
        } else {
            for (Buffer arg : keys) {
                keysCapacity += calculateRequestArgumentSize(arg);
            }
        }
        final int capacity = calculateInitialCommandBufferSize(len, RedisProtocolSupport.Command.TOUCH) + keysCapacity;
        Buffer buffer = partitionedRedisClient.executionContext().bufferAllocator().newBuffer(capacity);
        writeRequestArraySize(buffer, len);
        RedisProtocolSupport.Command.TOUCH.encodeTo(buffer);
        if (keys instanceof List && keys instanceof RandomAccess) {
            final List<Buffer> list = (List<Buffer>) keys;
            for (int i = 0; i < list.size(); ++i) {
                final Buffer arg = list.get(i);
                writeRequestArgument(buffer, arg);
            }
        } else {
            for (Buffer arg : keys) {
                writeRequestArgument(buffer, arg);
            }
        }
        final RedisRequest request = newRequest(RedisProtocolSupport.Command.TOUCH, buffer);
        final RedisPartitionAttributesBuilder partitionAttributesBuilder = partitionAttributesBuilderFunction
                    .apply(RedisProtocolSupport.Command.TOUCH);
        addBufferKeysToAttributeBuilder(keys, partitionAttributesBuilder);
        final Single<Long> result = partitionedRedisClient.request(partitionAttributesBuilder.build(), request,
                    Long.class);
        return result;
    }

    @Override
    public Single<Long> ttl(@RedisProtocolSupport.Key final Buffer key) {
        requireNonNull(key);
        final int len = 2;
        final int capacity = calculateInitialCommandBufferSize(len, RedisProtocolSupport.Command.TTL) +
                    calculateRequestArgumentSize(key);
        Buffer buffer = partitionedRedisClient.executionContext().bufferAllocator().newBuffer(capacity);
        writeRequestArraySize(buffer, len);
        RedisProtocolSupport.Command.TTL.encodeTo(buffer);
        writeRequestArgument(buffer, key);
        final RedisRequest request = newRequest(RedisProtocolSupport.Command.TTL, buffer);
        final RedisPartitionAttributesBuilder partitionAttributesBuilder = partitionAttributesBuilderFunction
                    .apply(RedisProtocolSupport.Command.TTL);
        partitionAttributesBuilder.addKey(key);
        final Single<Long> result = partitionedRedisClient.request(partitionAttributesBuilder.build(), request,
                    Long.class);
        return result;
    }

    @Override
    public Single<String> type(@RedisProtocolSupport.Key final Buffer key) {
        requireNonNull(key);
        final int len = 2;
        final int capacity = calculateInitialCommandBufferSize(len, RedisProtocolSupport.Command.TYPE) +
                    calculateRequestArgumentSize(key);
        Buffer buffer = partitionedRedisClient.executionContext().bufferAllocator().newBuffer(capacity);
        writeRequestArraySize(buffer, len);
        RedisProtocolSupport.Command.TYPE.encodeTo(buffer);
        writeRequestArgument(buffer, key);
        final RedisRequest request = newRequest(RedisProtocolSupport.Command.TYPE, buffer);
        final RedisPartitionAttributesBuilder partitionAttributesBuilder = partitionAttributesBuilderFunction
                    .apply(RedisProtocolSupport.Command.TYPE);
        partitionAttributesBuilder.addKey(key);
        final Single<String> result = partitionedRedisClient.request(partitionAttributesBuilder.build(), request,
                    String.class);
        return result;
    }

    @Override
    public Single<Long> unlink(@RedisProtocolSupport.Key final Buffer key) {
        requireNonNull(key);
        final int len = 2;
        final int capacity = calculateInitialCommandBufferSize(len, RedisProtocolSupport.Command.UNLINK) +
                    calculateRequestArgumentSize(key);
        Buffer buffer = partitionedRedisClient.executionContext().bufferAllocator().newBuffer(capacity);
        writeRequestArraySize(buffer, len);
        RedisProtocolSupport.Command.UNLINK.encodeTo(buffer);
        writeRequestArgument(buffer, key);
        final RedisRequest request = newRequest(RedisProtocolSupport.Command.UNLINK, buffer);
        final RedisPartitionAttributesBuilder partitionAttributesBuilder = partitionAttributesBuilderFunction
                    .apply(RedisProtocolSupport.Command.UNLINK);
        partitionAttributesBuilder.addKey(key);
        final Single<Long> result = partitionedRedisClient.request(partitionAttributesBuilder.build(), request,
                    Long.class);
        return result;
    }

    @Override
    public Single<Long> unlink(@RedisProtocolSupport.Key final Buffer key1,
                               @RedisProtocolSupport.Key final Buffer key2) {
        requireNonNull(key1);
        requireNonNull(key2);
        final int len = 3;
        final int capacity = calculateInitialCommandBufferSize(len, RedisProtocolSupport.Command.UNLINK) +
                    calculateRequestArgumentSize(key1) + calculateRequestArgumentSize(key2);
        Buffer buffer = partitionedRedisClient.executionContext().bufferAllocator().newBuffer(capacity);
        writeRequestArraySize(buffer, len);
        RedisProtocolSupport.Command.UNLINK.encodeTo(buffer);
        writeRequestArgument(buffer, key1);
        writeRequestArgument(buffer, key2);
        final RedisRequest request = newRequest(RedisProtocolSupport.Command.UNLINK, buffer);
        final RedisPartitionAttributesBuilder partitionAttributesBuilder = partitionAttributesBuilderFunction
                    .apply(RedisProtocolSupport.Command.UNLINK);
        partitionAttributesBuilder.addKey(key1);
        partitionAttributesBuilder.addKey(key2);
        final Single<Long> result = partitionedRedisClient.request(partitionAttributesBuilder.build(), request,
                    Long.class);
        return result;
    }

    @Override
    public Single<Long> unlink(@RedisProtocolSupport.Key final Buffer key1, @RedisProtocolSupport.Key final Buffer key2,
                               @RedisProtocolSupport.Key final Buffer key3) {
        requireNonNull(key1);
        requireNonNull(key2);
        requireNonNull(key3);
        final int len = 4;
        final int capacity = calculateInitialCommandBufferSize(len, RedisProtocolSupport.Command.UNLINK) +
                    calculateRequestArgumentSize(key1) + calculateRequestArgumentSize(key2) +
                    calculateRequestArgumentSize(key3);
        Buffer buffer = partitionedRedisClient.executionContext().bufferAllocator().newBuffer(capacity);
        writeRequestArraySize(buffer, len);
        RedisProtocolSupport.Command.UNLINK.encodeTo(buffer);
        writeRequestArgument(buffer, key1);
        writeRequestArgument(buffer, key2);
        writeRequestArgument(buffer, key3);
        final RedisRequest request = newRequest(RedisProtocolSupport.Command.UNLINK, buffer);
        final RedisPartitionAttributesBuilder partitionAttributesBuilder = partitionAttributesBuilderFunction
                    .apply(RedisProtocolSupport.Command.UNLINK);
        partitionAttributesBuilder.addKey(key1);
        partitionAttributesBuilder.addKey(key2);
        partitionAttributesBuilder.addKey(key3);
        final Single<Long> result = partitionedRedisClient.request(partitionAttributesBuilder.build(), request,
                    Long.class);
        return result;
    }

    @Override
    public Single<Long> unlink(@RedisProtocolSupport.Key final Collection<Buffer> keys) {
        requireNonNull(keys);
        final int len = 1 + keys.size();
        int keysCapacity = 0;
        if (keys instanceof List && keys instanceof RandomAccess) {
            final List<Buffer> list = (List<Buffer>) keys;
            for (int i = 0; i < list.size(); ++i) {
                final Buffer arg = list.get(i);
                keysCapacity += calculateRequestArgumentSize(arg);
            }
        } else {
            for (Buffer arg : keys) {
                keysCapacity += calculateRequestArgumentSize(arg);
            }
        }
        final int capacity = calculateInitialCommandBufferSize(len, RedisProtocolSupport.Command.UNLINK) + keysCapacity;
        Buffer buffer = partitionedRedisClient.executionContext().bufferAllocator().newBuffer(capacity);
        writeRequestArraySize(buffer, len);
        RedisProtocolSupport.Command.UNLINK.encodeTo(buffer);
        if (keys instanceof List && keys instanceof RandomAccess) {
            final List<Buffer> list = (List<Buffer>) keys;
            for (int i = 0; i < list.size(); ++i) {
                final Buffer arg = list.get(i);
                writeRequestArgument(buffer, arg);
            }
        } else {
            for (Buffer arg : keys) {
                writeRequestArgument(buffer, arg);
            }
        }
        final RedisRequest request = newRequest(RedisProtocolSupport.Command.UNLINK, buffer);
        final RedisPartitionAttributesBuilder partitionAttributesBuilder = partitionAttributesBuilderFunction
                    .apply(RedisProtocolSupport.Command.UNLINK);
        addBufferKeysToAttributeBuilder(keys, partitionAttributesBuilder);
        final Single<Long> result = partitionedRedisClient.request(partitionAttributesBuilder.build(), request,
                    Long.class);
        return result;
    }

    @Override
    public Single<String> unwatch() {
        final int len = 1;
        final int capacity = calculateInitialCommandBufferSize(len, RedisProtocolSupport.Command.UNWATCH);
        Buffer buffer = partitionedRedisClient.executionContext().bufferAllocator().newBuffer(capacity);
        writeRequestArraySize(buffer, len);
        RedisProtocolSupport.Command.UNWATCH.encodeTo(buffer);
        final RedisRequest request = newRequest(RedisProtocolSupport.Command.UNWATCH, buffer);
        final RedisPartitionAttributesBuilder partitionAttributesBuilder = partitionAttributesBuilderFunction
                    .apply(RedisProtocolSupport.Command.UNWATCH);
        final Single<String> result = partitionedRedisClient.request(partitionAttributesBuilder.build(), request,
                    String.class);
        return result;
    }

    @Override
    public Single<Long> wait(final long numslaves, final long timeout) {
        final int len = 3;
        final int capacity = calculateInitialCommandBufferSize(len, RedisProtocolSupport.Command.WAIT) +
                    calculateRequestArgumentSize(numslaves) + calculateRequestArgumentSize(timeout);
        Buffer buffer = partitionedRedisClient.executionContext().bufferAllocator().newBuffer(capacity);
        writeRequestArraySize(buffer, len);
        RedisProtocolSupport.Command.WAIT.encodeTo(buffer);
        writeRequestArgument(buffer, numslaves);
        writeRequestArgument(buffer, timeout);
        final RedisRequest request = newRequest(RedisProtocolSupport.Command.WAIT, buffer);
        final RedisPartitionAttributesBuilder partitionAttributesBuilder = partitionAttributesBuilderFunction
                    .apply(RedisProtocolSupport.Command.WAIT);
        final Single<Long> result = partitionedRedisClient.request(partitionAttributesBuilder.build(), request,
                    Long.class);
        return result;
    }

    @Override
    public Single<String> watch(@RedisProtocolSupport.Key final Buffer key) {
        requireNonNull(key);
        final int len = 2;
        final int capacity = calculateInitialCommandBufferSize(len, RedisProtocolSupport.Command.WATCH) +
                    calculateRequestArgumentSize(key);
        Buffer buffer = partitionedRedisClient.executionContext().bufferAllocator().newBuffer(capacity);
        writeRequestArraySize(buffer, len);
        RedisProtocolSupport.Command.WATCH.encodeTo(buffer);
        writeRequestArgument(buffer, key);
        final RedisRequest request = newRequest(RedisProtocolSupport.Command.WATCH, buffer);
        final RedisPartitionAttributesBuilder partitionAttributesBuilder = partitionAttributesBuilderFunction
                    .apply(RedisProtocolSupport.Command.WATCH);
        partitionAttributesBuilder.addKey(key);
        final Single<String> result = partitionedRedisClient.request(partitionAttributesBuilder.build(), request,
                    String.class);
        return result;
    }

    @Override
    public Single<String> watch(@RedisProtocolSupport.Key final Buffer key1,
                                @RedisProtocolSupport.Key final Buffer key2) {
        requireNonNull(key1);
        requireNonNull(key2);
        final int len = 3;
        final int capacity = calculateInitialCommandBufferSize(len, RedisProtocolSupport.Command.WATCH) +
                    calculateRequestArgumentSize(key1) + calculateRequestArgumentSize(key2);
        Buffer buffer = partitionedRedisClient.executionContext().bufferAllocator().newBuffer(capacity);
        writeRequestArraySize(buffer, len);
        RedisProtocolSupport.Command.WATCH.encodeTo(buffer);
        writeRequestArgument(buffer, key1);
        writeRequestArgument(buffer, key2);
        final RedisRequest request = newRequest(RedisProtocolSupport.Command.WATCH, buffer);
        final RedisPartitionAttributesBuilder partitionAttributesBuilder = partitionAttributesBuilderFunction
                    .apply(RedisProtocolSupport.Command.WATCH);
        partitionAttributesBuilder.addKey(key1);
        partitionAttributesBuilder.addKey(key2);
        final Single<String> result = partitionedRedisClient.request(partitionAttributesBuilder.build(), request,
                    String.class);
        return result;
    }

    @Override
    public Single<String> watch(@RedisProtocolSupport.Key final Buffer key1,
                                @RedisProtocolSupport.Key final Buffer key2,
                                @RedisProtocolSupport.Key final Buffer key3) {
        requireNonNull(key1);
        requireNonNull(key2);
        requireNonNull(key3);
        final int len = 4;
        final int capacity = calculateInitialCommandBufferSize(len, RedisProtocolSupport.Command.WATCH) +
                    calculateRequestArgumentSize(key1) + calculateRequestArgumentSize(key2) +
                    calculateRequestArgumentSize(key3);
        Buffer buffer = partitionedRedisClient.executionContext().bufferAllocator().newBuffer(capacity);
        writeRequestArraySize(buffer, len);
        RedisProtocolSupport.Command.WATCH.encodeTo(buffer);
        writeRequestArgument(buffer, key1);
        writeRequestArgument(buffer, key2);
        writeRequestArgument(buffer, key3);
        final RedisRequest request = newRequest(RedisProtocolSupport.Command.WATCH, buffer);
        final RedisPartitionAttributesBuilder partitionAttributesBuilder = partitionAttributesBuilderFunction
                    .apply(RedisProtocolSupport.Command.WATCH);
        partitionAttributesBuilder.addKey(key1);
        partitionAttributesBuilder.addKey(key2);
        partitionAttributesBuilder.addKey(key3);
        final Single<String> result = partitionedRedisClient.request(partitionAttributesBuilder.build(), request,
                    String.class);
        return result;
    }

    @Override
    public Single<String> watch(@RedisProtocolSupport.Key final Collection<Buffer> keys) {
        requireNonNull(keys);
        final int len = 1 + keys.size();
        int keysCapacity = 0;
        if (keys instanceof List && keys instanceof RandomAccess) {
            final List<Buffer> list = (List<Buffer>) keys;
            for (int i = 0; i < list.size(); ++i) {
                final Buffer arg = list.get(i);
                keysCapacity += calculateRequestArgumentSize(arg);
            }
        } else {
            for (Buffer arg : keys) {
                keysCapacity += calculateRequestArgumentSize(arg);
            }
        }
        final int capacity = calculateInitialCommandBufferSize(len, RedisProtocolSupport.Command.WATCH) + keysCapacity;
        Buffer buffer = partitionedRedisClient.executionContext().bufferAllocator().newBuffer(capacity);
        writeRequestArraySize(buffer, len);
        RedisProtocolSupport.Command.WATCH.encodeTo(buffer);
        if (keys instanceof List && keys instanceof RandomAccess) {
            final List<Buffer> list = (List<Buffer>) keys;
            for (int i = 0; i < list.size(); ++i) {
                final Buffer arg = list.get(i);
                writeRequestArgument(buffer, arg);
            }
        } else {
            for (Buffer arg : keys) {
                writeRequestArgument(buffer, arg);
            }
        }
        final RedisRequest request = newRequest(RedisProtocolSupport.Command.WATCH, buffer);
        final RedisPartitionAttributesBuilder partitionAttributesBuilder = partitionAttributesBuilderFunction
                    .apply(RedisProtocolSupport.Command.WATCH);
        addBufferKeysToAttributeBuilder(keys, partitionAttributesBuilder);
        final Single<String> result = partitionedRedisClient.request(partitionAttributesBuilder.build(), request,
                    String.class);
        return result;
    }

    @Override
    public Single<Buffer> xadd(@RedisProtocolSupport.Key final Buffer key, final Buffer id, final Buffer field,
                               final Buffer value) {
        requireNonNull(key);
        requireNonNull(id);
        requireNonNull(field);
        requireNonNull(value);
        final int len = 5;
        final int capacity = calculateInitialCommandBufferSize(len, RedisProtocolSupport.Command.XADD) +
                    calculateRequestArgumentSize(key) + calculateRequestArgumentSize(id) +
                    calculateRequestArgumentSize(field) + calculateRequestArgumentSize(value);
        Buffer buffer = partitionedRedisClient.executionContext().bufferAllocator().newBuffer(capacity);
        writeRequestArraySize(buffer, len);
        RedisProtocolSupport.Command.XADD.encodeTo(buffer);
        writeRequestArgument(buffer, key);
        writeRequestArgument(buffer, id);
        writeRequestArgument(buffer, field);
        writeRequestArgument(buffer, value);
        final RedisRequest request = newRequest(RedisProtocolSupport.Command.XADD, buffer);
        final RedisPartitionAttributesBuilder partitionAttributesBuilder = partitionAttributesBuilderFunction
                    .apply(RedisProtocolSupport.Command.XADD);
        partitionAttributesBuilder.addKey(key);
        final Single<Buffer> result = partitionedRedisClient.request(partitionAttributesBuilder.build(), request,
                    Buffer.class);
        return result;
    }

    @Override
    public Single<Buffer> xadd(@RedisProtocolSupport.Key final Buffer key, final Buffer id, final Buffer field1,
                               final Buffer value1, final Buffer field2, final Buffer value2) {
        requireNonNull(key);
        requireNonNull(id);
        requireNonNull(field1);
        requireNonNull(value1);
        requireNonNull(field2);
        requireNonNull(value2);
        final int len = 7;
        final int capacity = calculateInitialCommandBufferSize(len, RedisProtocolSupport.Command.XADD) +
                    calculateRequestArgumentSize(key) + calculateRequestArgumentSize(id) +
                    calculateRequestArgumentSize(field1) + calculateRequestArgumentSize(value1) +
                    calculateRequestArgumentSize(field2) + calculateRequestArgumentSize(value2);
        Buffer buffer = partitionedRedisClient.executionContext().bufferAllocator().newBuffer(capacity);
        writeRequestArraySize(buffer, len);
        RedisProtocolSupport.Command.XADD.encodeTo(buffer);
        writeRequestArgument(buffer, key);
        writeRequestArgument(buffer, id);
        writeRequestArgument(buffer, field1);
        writeRequestArgument(buffer, value1);
        writeRequestArgument(buffer, field2);
        writeRequestArgument(buffer, value2);
        final RedisRequest request = newRequest(RedisProtocolSupport.Command.XADD, buffer);
        final RedisPartitionAttributesBuilder partitionAttributesBuilder = partitionAttributesBuilderFunction
                    .apply(RedisProtocolSupport.Command.XADD);
        partitionAttributesBuilder.addKey(key);
        final Single<Buffer> result = partitionedRedisClient.request(partitionAttributesBuilder.build(), request,
                    Buffer.class);
        return result;
    }

    @Override
    public Single<Buffer> xadd(@RedisProtocolSupport.Key final Buffer key, final Buffer id, final Buffer field1,
                               final Buffer value1, final Buffer field2, final Buffer value2, final Buffer field3,
                               final Buffer value3) {
        requireNonNull(key);
        requireNonNull(id);
        requireNonNull(field1);
        requireNonNull(value1);
        requireNonNull(field2);
        requireNonNull(value2);
        requireNonNull(field3);
        requireNonNull(value3);
        final int len = 9;
        final int capacity = calculateInitialCommandBufferSize(len, RedisProtocolSupport.Command.XADD) +
                    calculateRequestArgumentSize(key) + calculateRequestArgumentSize(id) +
                    calculateRequestArgumentSize(field1) + calculateRequestArgumentSize(value1) +
                    calculateRequestArgumentSize(field2) + calculateRequestArgumentSize(value2) +
                    calculateRequestArgumentSize(field3) + calculateRequestArgumentSize(value3);
        Buffer buffer = partitionedRedisClient.executionContext().bufferAllocator().newBuffer(capacity);
        writeRequestArraySize(buffer, len);
        RedisProtocolSupport.Command.XADD.encodeTo(buffer);
        writeRequestArgument(buffer, key);
        writeRequestArgument(buffer, id);
        writeRequestArgument(buffer, field1);
        writeRequestArgument(buffer, value1);
        writeRequestArgument(buffer, field2);
        writeRequestArgument(buffer, value2);
        writeRequestArgument(buffer, field3);
        writeRequestArgument(buffer, value3);
        final RedisRequest request = newRequest(RedisProtocolSupport.Command.XADD, buffer);
        final RedisPartitionAttributesBuilder partitionAttributesBuilder = partitionAttributesBuilderFunction
                    .apply(RedisProtocolSupport.Command.XADD);
        partitionAttributesBuilder.addKey(key);
        final Single<Buffer> result = partitionedRedisClient.request(partitionAttributesBuilder.build(), request,
                    Buffer.class);
        return result;
    }

    @Override
    public Single<Buffer> xadd(@RedisProtocolSupport.Key final Buffer key, final Buffer id,
                               final Collection<RedisProtocolSupport.BufferFieldValue> fieldValues) {
        requireNonNull(key);
        requireNonNull(id);
        requireNonNull(fieldValues);
        final int len = 3 + RedisProtocolSupport.BufferFieldValue.SIZE * fieldValues.size();
        int fieldValuesCapacity = 0;
        if (fieldValues instanceof List && fieldValues instanceof RandomAccess) {
            final List<RedisProtocolSupport.BufferFieldValue> list = (List<RedisProtocolSupport.BufferFieldValue>) fieldValues;
            for (int i = 0; i < list.size(); ++i) {
                final RedisProtocolSupport.BufferFieldValue arg = list.get(i);
                fieldValuesCapacity += arg.encodedByteCount();
            }
        } else {
            for (RedisProtocolSupport.BufferFieldValue arg : fieldValues) {
                fieldValuesCapacity += arg.encodedByteCount();
            }
        }
        final int capacity = calculateInitialCommandBufferSize(len, RedisProtocolSupport.Command.XADD) +
                    calculateRequestArgumentSize(key) + calculateRequestArgumentSize(id) + fieldValuesCapacity;
        Buffer buffer = partitionedRedisClient.executionContext().bufferAllocator().newBuffer(capacity);
        writeRequestArraySize(buffer, len);
        RedisProtocolSupport.Command.XADD.encodeTo(buffer);
        writeRequestArgument(buffer, key);
        writeRequestArgument(buffer, id);
        if (fieldValues instanceof List && fieldValues instanceof RandomAccess) {
            final List<RedisProtocolSupport.BufferFieldValue> list = (List<RedisProtocolSupport.BufferFieldValue>) fieldValues;
            for (int i = 0; i < list.size(); ++i) {
                final RedisProtocolSupport.BufferFieldValue arg = list.get(i);
                arg.encodeTo(buffer);
            }
        } else {
            for (RedisProtocolSupport.BufferFieldValue arg : fieldValues) {
                arg.encodeTo(buffer);
            }
        }
        final RedisRequest request = newRequest(RedisProtocolSupport.Command.XADD, buffer);
        final RedisPartitionAttributesBuilder partitionAttributesBuilder = partitionAttributesBuilderFunction
                    .apply(RedisProtocolSupport.Command.XADD);
        partitionAttributesBuilder.addKey(key);
        final Single<Buffer> result = partitionedRedisClient.request(partitionAttributesBuilder.build(), request,
                    Buffer.class);
        return result;
    }

    @Override
    public Single<Long> xlen(@RedisProtocolSupport.Key final Buffer key) {
        requireNonNull(key);
        final int len = 2;
        final int capacity = calculateInitialCommandBufferSize(len, RedisProtocolSupport.Command.XLEN) +
                    calculateRequestArgumentSize(key);
        Buffer buffer = partitionedRedisClient.executionContext().bufferAllocator().newBuffer(capacity);
        writeRequestArraySize(buffer, len);
        RedisProtocolSupport.Command.XLEN.encodeTo(buffer);
        writeRequestArgument(buffer, key);
        final RedisRequest request = newRequest(RedisProtocolSupport.Command.XLEN, buffer);
        final RedisPartitionAttributesBuilder partitionAttributesBuilder = partitionAttributesBuilderFunction
                    .apply(RedisProtocolSupport.Command.XLEN);
        partitionAttributesBuilder.addKey(key);
        final Single<Long> result = partitionedRedisClient.request(partitionAttributesBuilder.build(), request,
                    Long.class);
        return result;
    }

    @Override
    public <T> Single<List<T>> xpending(@RedisProtocolSupport.Key final Buffer key, final Buffer group) {
        requireNonNull(key);
        requireNonNull(group);
        final int len = 3;
        final int capacity = calculateInitialCommandBufferSize(len, RedisProtocolSupport.Command.XPENDING) +
                    calculateRequestArgumentSize(key) + calculateRequestArgumentSize(group);
        Buffer buffer = partitionedRedisClient.executionContext().bufferAllocator().newBuffer(capacity);
        writeRequestArraySize(buffer, len);
        RedisProtocolSupport.Command.XPENDING.encodeTo(buffer);
        writeRequestArgument(buffer, key);
        writeRequestArgument(buffer, group);
        final RedisRequest request = newRequest(RedisProtocolSupport.Command.XPENDING, buffer);
        final RedisPartitionAttributesBuilder partitionAttributesBuilder = partitionAttributesBuilderFunction
                    .apply(RedisProtocolSupport.Command.XPENDING);
        partitionAttributesBuilder.addKey(key);
        final Single<List<T>> result = (Single) partitionedRedisClient.request(partitionAttributesBuilder.build(),
                    request, List.class);
        return result;
    }

    @Override
    public <T> Single<List<T>> xpending(@RedisProtocolSupport.Key final Buffer key, final Buffer group,
                                        @Nullable final Buffer start, @Nullable final Buffer end,
                                        @Nullable final Long count, @Nullable final Buffer consumer) {
        requireNonNull(key);
        requireNonNull(group);
        final int len = 3 + (start == null ? 0 : 1) + (end == null ? 0 : 1) + (count == null ? 0 : 1) +
                    (consumer == null ? 0 : 1);
        final int capacity = calculateInitialCommandBufferSize(len, RedisProtocolSupport.Command.XPENDING) +
                    calculateRequestArgumentSize(key) + calculateRequestArgumentSize(group) +
                    (start == null ? 0 : calculateRequestArgumentSize(start)) +
                    (end == null ? 0 : calculateRequestArgumentSize(end)) +
                    (count == null ? 0 : calculateRequestArgumentSize(count)) +
                    (consumer == null ? 0 : calculateRequestArgumentSize(consumer));
        Buffer buffer = partitionedRedisClient.executionContext().bufferAllocator().newBuffer(capacity);
        writeRequestArraySize(buffer, len);
        RedisProtocolSupport.Command.XPENDING.encodeTo(buffer);
        writeRequestArgument(buffer, key);
        writeRequestArgument(buffer, group);
        if (start != null) {
            writeRequestArgument(buffer, start);
        }
        if (end != null) {
            writeRequestArgument(buffer, end);
        }
        if (count != null) {
            writeRequestArgument(buffer, count);
        }
        if (consumer != null) {
            writeRequestArgument(buffer, consumer);
        }
        final RedisRequest request = newRequest(RedisProtocolSupport.Command.XPENDING, buffer);
        final RedisPartitionAttributesBuilder partitionAttributesBuilder = partitionAttributesBuilderFunction
                    .apply(RedisProtocolSupport.Command.XPENDING);
        partitionAttributesBuilder.addKey(key);
        final Single<List<T>> result = (Single) partitionedRedisClient.request(partitionAttributesBuilder.build(),
                    request, List.class);
        return result;
    }

    @Override
    public <T> Single<List<T>> xrange(@RedisProtocolSupport.Key final Buffer key, final Buffer start,
                                      final Buffer end) {
        requireNonNull(key);
        requireNonNull(start);
        requireNonNull(end);
        final int len = 4;
        final int capacity = calculateInitialCommandBufferSize(len, RedisProtocolSupport.Command.XRANGE) +
                    calculateRequestArgumentSize(key) + calculateRequestArgumentSize(start) +
                    calculateRequestArgumentSize(end);
        Buffer buffer = partitionedRedisClient.executionContext().bufferAllocator().newBuffer(capacity);
        writeRequestArraySize(buffer, len);
        RedisProtocolSupport.Command.XRANGE.encodeTo(buffer);
        writeRequestArgument(buffer, key);
        writeRequestArgument(buffer, start);
        writeRequestArgument(buffer, end);
        final RedisRequest request = newRequest(RedisProtocolSupport.Command.XRANGE, buffer);
        final RedisPartitionAttributesBuilder partitionAttributesBuilder = partitionAttributesBuilderFunction
                    .apply(RedisProtocolSupport.Command.XRANGE);
        partitionAttributesBuilder.addKey(key);
        final Single<List<T>> result = (Single) partitionedRedisClient.request(partitionAttributesBuilder.build(),
                    request, List.class);
        return result;
    }

    @Override
    public <T> Single<List<T>> xrange(@RedisProtocolSupport.Key final Buffer key, final Buffer start, final Buffer end,
                                      @Nullable final Long count) {
        requireNonNull(key);
        requireNonNull(start);
        requireNonNull(end);
        final int len = 4 + (count == null ? 0 : 2);
        final int capacity = calculateInitialCommandBufferSize(len, RedisProtocolSupport.Command.XRANGE) +
                    calculateRequestArgumentSize(key) + calculateRequestArgumentSize(start) +
                    calculateRequestArgumentSize(end) +
                    (count == null ? 0 : RedisProtocolSupport.SubCommand.COUNT.encodedByteCount()) +
                    (count == null ? 0 : calculateRequestArgumentSize(count));
        Buffer buffer = partitionedRedisClient.executionContext().bufferAllocator().newBuffer(capacity);
        writeRequestArraySize(buffer, len);
        RedisProtocolSupport.Command.XRANGE.encodeTo(buffer);
        writeRequestArgument(buffer, key);
        writeRequestArgument(buffer, start);
        writeRequestArgument(buffer, end);
        if (count != null) {
            RedisProtocolSupport.SubCommand.COUNT.encodeTo(buffer);
            writeRequestArgument(buffer, count);
        }
        final RedisRequest request = newRequest(RedisProtocolSupport.Command.XRANGE, buffer);
        final RedisPartitionAttributesBuilder partitionAttributesBuilder = partitionAttributesBuilderFunction
                    .apply(RedisProtocolSupport.Command.XRANGE);
        partitionAttributesBuilder.addKey(key);
        final Single<List<T>> result = (Single) partitionedRedisClient.request(partitionAttributesBuilder.build(),
                    request, List.class);
        return result;
    }

    @Override
    public <T> Single<List<T>> xread(@RedisProtocolSupport.Key final Collection<Buffer> keys,
                                     final Collection<Buffer> ids) {
        requireNonNull(keys);
        requireNonNull(ids);
        final int len = 2 + keys.size() + ids.size();
        int keysCapacity = 0;
        if (keys instanceof List && keys instanceof RandomAccess) {
            final List<Buffer> list = (List<Buffer>) keys;
            for (int i = 0; i < list.size(); ++i) {
                final Buffer arg = list.get(i);
                keysCapacity += calculateRequestArgumentSize(arg);
            }
        } else {
            for (Buffer arg : keys) {
                keysCapacity += calculateRequestArgumentSize(arg);
            }
        }
        int idsCapacity = 0;
        if (ids instanceof List && ids instanceof RandomAccess) {
            final List<Buffer> list = (List<Buffer>) ids;
            for (int i = 0; i < list.size(); ++i) {
                final Buffer arg = list.get(i);
                idsCapacity += calculateRequestArgumentSize(arg);
            }
        } else {
            for (Buffer arg : ids) {
                idsCapacity += calculateRequestArgumentSize(arg);
            }
        }
        final int capacity = calculateInitialCommandBufferSize(len, RedisProtocolSupport.Command.XREAD) +
                    RedisProtocolSupport.XreadStreams.values()[0].encodedByteCount() + keysCapacity + idsCapacity;
        Buffer buffer = partitionedRedisClient.executionContext().bufferAllocator().newBuffer(capacity);
        writeRequestArraySize(buffer, len);
        RedisProtocolSupport.Command.XREAD.encodeTo(buffer);
        RedisProtocolSupport.XreadStreams.values()[0].encodeTo(buffer);
        if (keys instanceof List && keys instanceof RandomAccess) {
            final List<Buffer> list = (List<Buffer>) keys;
            for (int i = 0; i < list.size(); ++i) {
                final Buffer arg = list.get(i);
                writeRequestArgument(buffer, arg);
            }
        } else {
            for (Buffer arg : keys) {
                writeRequestArgument(buffer, arg);
            }
        }
        if (ids instanceof List && ids instanceof RandomAccess) {
            final List<Buffer> list = (List<Buffer>) ids;
            for (int i = 0; i < list.size(); ++i) {
                final Buffer arg = list.get(i);
                writeRequestArgument(buffer, arg);
            }
        } else {
            for (Buffer arg : ids) {
                writeRequestArgument(buffer, arg);
            }
        }
        final RedisRequest request = newRequest(RedisProtocolSupport.Command.XREAD, buffer);
        final RedisPartitionAttributesBuilder partitionAttributesBuilder = partitionAttributesBuilderFunction
                    .apply(RedisProtocolSupport.Command.XREAD);
        addBufferKeysToAttributeBuilder(keys, partitionAttributesBuilder);
        final Single<List<T>> result = (Single) partitionedRedisClient.request(partitionAttributesBuilder.build(),
                    request, List.class);
        return result;
    }

    @Override
    public <T> Single<List<T>> xread(@Nullable final Long count, @Nullable final Long blockMilliseconds,
                                     @RedisProtocolSupport.Key final Collection<Buffer> keys,
                                     final Collection<Buffer> ids) {
        requireNonNull(keys);
        requireNonNull(ids);
        final int len = 2 + (count == null ? 0 : 2) + (blockMilliseconds == null ? 0 : 2) + keys.size() + ids.size();
        int keysCapacity = 0;
        if (keys instanceof List && keys instanceof RandomAccess) {
            final List<Buffer> list = (List<Buffer>) keys;
            for (int i = 0; i < list.size(); ++i) {
                final Buffer arg = list.get(i);
                keysCapacity += calculateRequestArgumentSize(arg);
            }
        } else {
            for (Buffer arg : keys) {
                keysCapacity += calculateRequestArgumentSize(arg);
            }
        }
        int idsCapacity = 0;
        if (ids instanceof List && ids instanceof RandomAccess) {
            final List<Buffer> list = (List<Buffer>) ids;
            for (int i = 0; i < list.size(); ++i) {
                final Buffer arg = list.get(i);
                idsCapacity += calculateRequestArgumentSize(arg);
            }
        } else {
            for (Buffer arg : ids) {
                idsCapacity += calculateRequestArgumentSize(arg);
            }
        }
        final int capacity = calculateInitialCommandBufferSize(len, RedisProtocolSupport.Command.XREAD) +
                    (count == null ? 0 : RedisProtocolSupport.SubCommand.COUNT.encodedByteCount()) +
                    (count == null ? 0 : calculateRequestArgumentSize(count)) +
                    (blockMilliseconds == null ? 0 : RedisProtocolSupport.SubCommand.BLOCK.encodedByteCount()) +
                    (blockMilliseconds == null ? 0 : calculateRequestArgumentSize(blockMilliseconds)) +
                    RedisProtocolSupport.XreadStreams.values()[0].encodedByteCount() + keysCapacity + idsCapacity;
        Buffer buffer = partitionedRedisClient.executionContext().bufferAllocator().newBuffer(capacity);
        writeRequestArraySize(buffer, len);
        RedisProtocolSupport.Command.XREAD.encodeTo(buffer);
        if (count != null) {
            RedisProtocolSupport.SubCommand.COUNT.encodeTo(buffer);
            writeRequestArgument(buffer, count);
        }
        if (blockMilliseconds != null) {
            RedisProtocolSupport.SubCommand.BLOCK.encodeTo(buffer);
            writeRequestArgument(buffer, blockMilliseconds);
        }
        RedisProtocolSupport.XreadStreams.values()[0].encodeTo(buffer);
        if (keys instanceof List && keys instanceof RandomAccess) {
            final List<Buffer> list = (List<Buffer>) keys;
            for (int i = 0; i < list.size(); ++i) {
                final Buffer arg = list.get(i);
                writeRequestArgument(buffer, arg);
            }
        } else {
            for (Buffer arg : keys) {
                writeRequestArgument(buffer, arg);
            }
        }
        if (ids instanceof List && ids instanceof RandomAccess) {
            final List<Buffer> list = (List<Buffer>) ids;
            for (int i = 0; i < list.size(); ++i) {
                final Buffer arg = list.get(i);
                writeRequestArgument(buffer, arg);
            }
        } else {
            for (Buffer arg : ids) {
                writeRequestArgument(buffer, arg);
            }
        }
        final RedisRequest request = newRequest(RedisProtocolSupport.Command.XREAD, buffer);
        final RedisPartitionAttributesBuilder partitionAttributesBuilder = partitionAttributesBuilderFunction
                    .apply(RedisProtocolSupport.Command.XREAD);
        addBufferKeysToAttributeBuilder(keys, partitionAttributesBuilder);
        final Single<List<T>> result = (Single) partitionedRedisClient.request(partitionAttributesBuilder.build(),
                    request, List.class);
        return result;
    }

    @Override
    public <T> Single<List<T>> xreadgroup(final RedisProtocolSupport.BufferGroupConsumer groupConsumer,
                                          @RedisProtocolSupport.Key final Collection<Buffer> keys,
                                          final Collection<Buffer> ids) {
        requireNonNull(groupConsumer);
        requireNonNull(keys);
        requireNonNull(ids);
        final int len = 2 + RedisProtocolSupport.BufferGroupConsumer.SIZE + keys.size() + ids.size();
        int keysCapacity = 0;
        if (keys instanceof List && keys instanceof RandomAccess) {
            final List<Buffer> list = (List<Buffer>) keys;
            for (int i = 0; i < list.size(); ++i) {
                final Buffer arg = list.get(i);
                keysCapacity += calculateRequestArgumentSize(arg);
            }
        } else {
            for (Buffer arg : keys) {
                keysCapacity += calculateRequestArgumentSize(arg);
            }
        }
        int idsCapacity = 0;
        if (ids instanceof List && ids instanceof RandomAccess) {
            final List<Buffer> list = (List<Buffer>) ids;
            for (int i = 0; i < list.size(); ++i) {
                final Buffer arg = list.get(i);
                idsCapacity += calculateRequestArgumentSize(arg);
            }
        } else {
            for (Buffer arg : ids) {
                idsCapacity += calculateRequestArgumentSize(arg);
            }
        }
        final int capacity = calculateInitialCommandBufferSize(len, RedisProtocolSupport.Command.XREADGROUP) +
                    groupConsumer.encodedByteCount() +
                    RedisProtocolSupport.XreadgroupStreams.values()[0].encodedByteCount() + keysCapacity + idsCapacity;
        Buffer buffer = partitionedRedisClient.executionContext().bufferAllocator().newBuffer(capacity);
        writeRequestArraySize(buffer, len);
        RedisProtocolSupport.Command.XREADGROUP.encodeTo(buffer);
        groupConsumer.encodeTo(buffer);
        RedisProtocolSupport.XreadgroupStreams.values()[0].encodeTo(buffer);
        if (keys instanceof List && keys instanceof RandomAccess) {
            final List<Buffer> list = (List<Buffer>) keys;
            for (int i = 0; i < list.size(); ++i) {
                final Buffer arg = list.get(i);
                writeRequestArgument(buffer, arg);
            }
        } else {
            for (Buffer arg : keys) {
                writeRequestArgument(buffer, arg);
            }
        }
        if (ids instanceof List && ids instanceof RandomAccess) {
            final List<Buffer> list = (List<Buffer>) ids;
            for (int i = 0; i < list.size(); ++i) {
                final Buffer arg = list.get(i);
                writeRequestArgument(buffer, arg);
            }
        } else {
            for (Buffer arg : ids) {
                writeRequestArgument(buffer, arg);
            }
        }
        final RedisRequest request = newRequest(RedisProtocolSupport.Command.XREADGROUP, buffer);
        final RedisPartitionAttributesBuilder partitionAttributesBuilder = partitionAttributesBuilderFunction
                    .apply(RedisProtocolSupport.Command.XREADGROUP);
        addBufferKeysToAttributeBuilder(keys, partitionAttributesBuilder);
        final Single<List<T>> result = (Single) partitionedRedisClient.request(partitionAttributesBuilder.build(),
                    request, List.class);
        return result;
    }

    @Override
    public <T> Single<List<T>> xreadgroup(final RedisProtocolSupport.BufferGroupConsumer groupConsumer,
                                          @Nullable final Long count, @Nullable final Long blockMilliseconds,
                                          @RedisProtocolSupport.Key final Collection<Buffer> keys,
                                          final Collection<Buffer> ids) {
        requireNonNull(groupConsumer);
        requireNonNull(keys);
        requireNonNull(ids);
        final int len = 2 + RedisProtocolSupport.BufferGroupConsumer.SIZE + (count == null ? 0 : 2) +
                    (blockMilliseconds == null ? 0 : 2) + keys.size() + ids.size();
        int keysCapacity = 0;
        if (keys instanceof List && keys instanceof RandomAccess) {
            final List<Buffer> list = (List<Buffer>) keys;
            for (int i = 0; i < list.size(); ++i) {
                final Buffer arg = list.get(i);
                keysCapacity += calculateRequestArgumentSize(arg);
            }
        } else {
            for (Buffer arg : keys) {
                keysCapacity += calculateRequestArgumentSize(arg);
            }
        }
        int idsCapacity = 0;
        if (ids instanceof List && ids instanceof RandomAccess) {
            final List<Buffer> list = (List<Buffer>) ids;
            for (int i = 0; i < list.size(); ++i) {
                final Buffer arg = list.get(i);
                idsCapacity += calculateRequestArgumentSize(arg);
            }
        } else {
            for (Buffer arg : ids) {
                idsCapacity += calculateRequestArgumentSize(arg);
            }
        }
        final int capacity = calculateInitialCommandBufferSize(len, RedisProtocolSupport.Command.XREADGROUP) +
                    groupConsumer.encodedByteCount() +
                    (count == null ? 0 : RedisProtocolSupport.SubCommand.COUNT.encodedByteCount()) +
                    (count == null ? 0 : calculateRequestArgumentSize(count)) +
                    (blockMilliseconds == null ? 0 : RedisProtocolSupport.SubCommand.BLOCK.encodedByteCount()) +
                    (blockMilliseconds == null ? 0 : calculateRequestArgumentSize(blockMilliseconds)) +
                    RedisProtocolSupport.XreadgroupStreams.values()[0].encodedByteCount() + keysCapacity + idsCapacity;
        Buffer buffer = partitionedRedisClient.executionContext().bufferAllocator().newBuffer(capacity);
        writeRequestArraySize(buffer, len);
        RedisProtocolSupport.Command.XREADGROUP.encodeTo(buffer);
        groupConsumer.encodeTo(buffer);
        if (count != null) {
            RedisProtocolSupport.SubCommand.COUNT.encodeTo(buffer);
            writeRequestArgument(buffer, count);
        }
        if (blockMilliseconds != null) {
            RedisProtocolSupport.SubCommand.BLOCK.encodeTo(buffer);
            writeRequestArgument(buffer, blockMilliseconds);
        }
        RedisProtocolSupport.XreadgroupStreams.values()[0].encodeTo(buffer);
        if (keys instanceof List && keys instanceof RandomAccess) {
            final List<Buffer> list = (List<Buffer>) keys;
            for (int i = 0; i < list.size(); ++i) {
                final Buffer arg = list.get(i);
                writeRequestArgument(buffer, arg);
            }
        } else {
            for (Buffer arg : keys) {
                writeRequestArgument(buffer, arg);
            }
        }
        if (ids instanceof List && ids instanceof RandomAccess) {
            final List<Buffer> list = (List<Buffer>) ids;
            for (int i = 0; i < list.size(); ++i) {
                final Buffer arg = list.get(i);
                writeRequestArgument(buffer, arg);
            }
        } else {
            for (Buffer arg : ids) {
                writeRequestArgument(buffer, arg);
            }
        }
        final RedisRequest request = newRequest(RedisProtocolSupport.Command.XREADGROUP, buffer);
        final RedisPartitionAttributesBuilder partitionAttributesBuilder = partitionAttributesBuilderFunction
                    .apply(RedisProtocolSupport.Command.XREADGROUP);
        addBufferKeysToAttributeBuilder(keys, partitionAttributesBuilder);
        final Single<List<T>> result = (Single) partitionedRedisClient.request(partitionAttributesBuilder.build(),
                    request, List.class);
        return result;
    }

    @Override
    public <T> Single<List<T>> xrevrange(@RedisProtocolSupport.Key final Buffer key, final Buffer end,
                                         final Buffer start) {
        requireNonNull(key);
        requireNonNull(end);
        requireNonNull(start);
        final int len = 4;
        final int capacity = calculateInitialCommandBufferSize(len, RedisProtocolSupport.Command.XREVRANGE) +
                    calculateRequestArgumentSize(key) + calculateRequestArgumentSize(end) +
                    calculateRequestArgumentSize(start);
        Buffer buffer = partitionedRedisClient.executionContext().bufferAllocator().newBuffer(capacity);
        writeRequestArraySize(buffer, len);
        RedisProtocolSupport.Command.XREVRANGE.encodeTo(buffer);
        writeRequestArgument(buffer, key);
        writeRequestArgument(buffer, end);
        writeRequestArgument(buffer, start);
        final RedisRequest request = newRequest(RedisProtocolSupport.Command.XREVRANGE, buffer);
        final RedisPartitionAttributesBuilder partitionAttributesBuilder = partitionAttributesBuilderFunction
                    .apply(RedisProtocolSupport.Command.XREVRANGE);
        partitionAttributesBuilder.addKey(key);
        final Single<List<T>> result = (Single) partitionedRedisClient.request(partitionAttributesBuilder.build(),
                    request, List.class);
        return result;
    }

    @Override
    public <T> Single<List<T>> xrevrange(@RedisProtocolSupport.Key final Buffer key, final Buffer end,
                                         final Buffer start, @Nullable final Long count) {
        requireNonNull(key);
        requireNonNull(end);
        requireNonNull(start);
        final int len = 4 + (count == null ? 0 : 2);
        final int capacity = calculateInitialCommandBufferSize(len, RedisProtocolSupport.Command.XREVRANGE) +
                    calculateRequestArgumentSize(key) + calculateRequestArgumentSize(end) +
                    calculateRequestArgumentSize(start) +
                    (count == null ? 0 : RedisProtocolSupport.SubCommand.COUNT.encodedByteCount()) +
                    (count == null ? 0 : calculateRequestArgumentSize(count));
        Buffer buffer = partitionedRedisClient.executionContext().bufferAllocator().newBuffer(capacity);
        writeRequestArraySize(buffer, len);
        RedisProtocolSupport.Command.XREVRANGE.encodeTo(buffer);
        writeRequestArgument(buffer, key);
        writeRequestArgument(buffer, end);
        writeRequestArgument(buffer, start);
        if (count != null) {
            RedisProtocolSupport.SubCommand.COUNT.encodeTo(buffer);
            writeRequestArgument(buffer, count);
        }
        final RedisRequest request = newRequest(RedisProtocolSupport.Command.XREVRANGE, buffer);
        final RedisPartitionAttributesBuilder partitionAttributesBuilder = partitionAttributesBuilderFunction
                    .apply(RedisProtocolSupport.Command.XREVRANGE);
        partitionAttributesBuilder.addKey(key);
        final Single<List<T>> result = (Single) partitionedRedisClient.request(partitionAttributesBuilder.build(),
                    request, List.class);
        return result;
    }

    @Override
    public Single<Long> zadd(@RedisProtocolSupport.Key final Buffer key,
                             final Collection<RedisProtocolSupport.BufferScoreMember> scoreMembers) {
        requireNonNull(key);
        requireNonNull(scoreMembers);
        final int len = 2 + RedisProtocolSupport.BufferScoreMember.SIZE * scoreMembers.size();
        int scoreMembersCapacity = 0;
        if (scoreMembers instanceof List && scoreMembers instanceof RandomAccess) {
            final List<RedisProtocolSupport.BufferScoreMember> list = (List<RedisProtocolSupport.BufferScoreMember>) scoreMembers;
            for (int i = 0; i < list.size(); ++i) {
                final RedisProtocolSupport.BufferScoreMember arg = list.get(i);
                scoreMembersCapacity += arg.encodedByteCount();
            }
        } else {
            for (RedisProtocolSupport.BufferScoreMember arg : scoreMembers) {
                scoreMembersCapacity += arg.encodedByteCount();
            }
        }
        final int capacity = calculateInitialCommandBufferSize(len, RedisProtocolSupport.Command.ZADD) +
                    calculateRequestArgumentSize(key) + scoreMembersCapacity;
        Buffer buffer = partitionedRedisClient.executionContext().bufferAllocator().newBuffer(capacity);
        writeRequestArraySize(buffer, len);
        RedisProtocolSupport.Command.ZADD.encodeTo(buffer);
        writeRequestArgument(buffer, key);
        if (scoreMembers instanceof List && scoreMembers instanceof RandomAccess) {
            final List<RedisProtocolSupport.BufferScoreMember> list = (List<RedisProtocolSupport.BufferScoreMember>) scoreMembers;
            for (int i = 0; i < list.size(); ++i) {
                final RedisProtocolSupport.BufferScoreMember arg = list.get(i);
                arg.encodeTo(buffer);
            }
        } else {
            for (RedisProtocolSupport.BufferScoreMember arg : scoreMembers) {
                arg.encodeTo(buffer);
            }
        }
        final RedisRequest request = newRequest(RedisProtocolSupport.Command.ZADD, buffer);
        final RedisPartitionAttributesBuilder partitionAttributesBuilder = partitionAttributesBuilderFunction
                    .apply(RedisProtocolSupport.Command.ZADD);
        partitionAttributesBuilder.addKey(key);
        final Single<Long> result = partitionedRedisClient.request(partitionAttributesBuilder.build(), request,
                    Long.class);
        return result;
    }

    @Override
    public Single<Long> zadd(@RedisProtocolSupport.Key final Buffer key,
                             @Nullable final RedisProtocolSupport.ZaddCondition condition,
                             @Nullable final RedisProtocolSupport.ZaddChange change, final double score,
                             final Buffer member) {
        requireNonNull(key);
        requireNonNull(member);
        final int len = 4 + (condition == null ? 0 : 1) + (change == null ? 0 : 1);
        final int capacity = calculateInitialCommandBufferSize(len, RedisProtocolSupport.Command.ZADD) +
                    calculateRequestArgumentSize(key) + (condition == null ? 0 : condition.encodedByteCount()) +
                    (change == null ? 0 : change.encodedByteCount()) + calculateRequestArgumentSize(score) +
                    calculateRequestArgumentSize(member);
        Buffer buffer = partitionedRedisClient.executionContext().bufferAllocator().newBuffer(capacity);
        writeRequestArraySize(buffer, len);
        RedisProtocolSupport.Command.ZADD.encodeTo(buffer);
        writeRequestArgument(buffer, key);
        if (condition != null) {
            condition.encodeTo(buffer);
        }
        if (change != null) {
            change.encodeTo(buffer);
        }
        writeRequestArgument(buffer, score);
        writeRequestArgument(buffer, member);
        final RedisRequest request = newRequest(RedisProtocolSupport.Command.ZADD, buffer);
        final RedisPartitionAttributesBuilder partitionAttributesBuilder = partitionAttributesBuilderFunction
                    .apply(RedisProtocolSupport.Command.ZADD);
        partitionAttributesBuilder.addKey(key);
        final Single<Long> result = partitionedRedisClient.request(partitionAttributesBuilder.build(), request,
                    Long.class);
        return result;
    }

    @Override
    public Single<Long> zadd(@RedisProtocolSupport.Key final Buffer key,
                             @Nullable final RedisProtocolSupport.ZaddCondition condition,
                             @Nullable final RedisProtocolSupport.ZaddChange change, final double score1,
                             final Buffer member1, final double score2, final Buffer member2) {
        requireNonNull(key);
        requireNonNull(member1);
        requireNonNull(member2);
        final int len = 6 + (condition == null ? 0 : 1) + (change == null ? 0 : 1);
        final int capacity = calculateInitialCommandBufferSize(len, RedisProtocolSupport.Command.ZADD) +
                    calculateRequestArgumentSize(key) + (condition == null ? 0 : condition.encodedByteCount()) +
                    (change == null ? 0 : change.encodedByteCount()) + calculateRequestArgumentSize(score1) +
                    calculateRequestArgumentSize(member1) + calculateRequestArgumentSize(score2) +
                    calculateRequestArgumentSize(member2);
        Buffer buffer = partitionedRedisClient.executionContext().bufferAllocator().newBuffer(capacity);
        writeRequestArraySize(buffer, len);
        RedisProtocolSupport.Command.ZADD.encodeTo(buffer);
        writeRequestArgument(buffer, key);
        if (condition != null) {
            condition.encodeTo(buffer);
        }
        if (change != null) {
            change.encodeTo(buffer);
        }
        writeRequestArgument(buffer, score1);
        writeRequestArgument(buffer, member1);
        writeRequestArgument(buffer, score2);
        writeRequestArgument(buffer, member2);
        final RedisRequest request = newRequest(RedisProtocolSupport.Command.ZADD, buffer);
        final RedisPartitionAttributesBuilder partitionAttributesBuilder = partitionAttributesBuilderFunction
                    .apply(RedisProtocolSupport.Command.ZADD);
        partitionAttributesBuilder.addKey(key);
        final Single<Long> result = partitionedRedisClient.request(partitionAttributesBuilder.build(), request,
                    Long.class);
        return result;
    }

    @Override
    public Single<Long> zadd(@RedisProtocolSupport.Key final Buffer key,
                             @Nullable final RedisProtocolSupport.ZaddCondition condition,
                             @Nullable final RedisProtocolSupport.ZaddChange change, final double score1,
                             final Buffer member1, final double score2, final Buffer member2, final double score3,
                             final Buffer member3) {
        requireNonNull(key);
        requireNonNull(member1);
        requireNonNull(member2);
        requireNonNull(member3);
        final int len = 8 + (condition == null ? 0 : 1) + (change == null ? 0 : 1);
        final int capacity = calculateInitialCommandBufferSize(len, RedisProtocolSupport.Command.ZADD) +
                    calculateRequestArgumentSize(key) + (condition == null ? 0 : condition.encodedByteCount()) +
                    (change == null ? 0 : change.encodedByteCount()) + calculateRequestArgumentSize(score1) +
                    calculateRequestArgumentSize(member1) + calculateRequestArgumentSize(score2) +
                    calculateRequestArgumentSize(member2) + calculateRequestArgumentSize(score3) +
                    calculateRequestArgumentSize(member3);
        Buffer buffer = partitionedRedisClient.executionContext().bufferAllocator().newBuffer(capacity);
        writeRequestArraySize(buffer, len);
        RedisProtocolSupport.Command.ZADD.encodeTo(buffer);
        writeRequestArgument(buffer, key);
        if (condition != null) {
            condition.encodeTo(buffer);
        }
        if (change != null) {
            change.encodeTo(buffer);
        }
        writeRequestArgument(buffer, score1);
        writeRequestArgument(buffer, member1);
        writeRequestArgument(buffer, score2);
        writeRequestArgument(buffer, member2);
        writeRequestArgument(buffer, score3);
        writeRequestArgument(buffer, member3);
        final RedisRequest request = newRequest(RedisProtocolSupport.Command.ZADD, buffer);
        final RedisPartitionAttributesBuilder partitionAttributesBuilder = partitionAttributesBuilderFunction
                    .apply(RedisProtocolSupport.Command.ZADD);
        partitionAttributesBuilder.addKey(key);
        final Single<Long> result = partitionedRedisClient.request(partitionAttributesBuilder.build(), request,
                    Long.class);
        return result;
    }

    @Override
    public Single<Long> zadd(@RedisProtocolSupport.Key final Buffer key,
                             @Nullable final RedisProtocolSupport.ZaddCondition condition,
                             @Nullable final RedisProtocolSupport.ZaddChange change,
                             final Collection<RedisProtocolSupport.BufferScoreMember> scoreMembers) {
        requireNonNull(key);
        requireNonNull(scoreMembers);
        final int len = 2 + (condition == null ? 0 : 1) + (change == null ? 0 : 1) +
                    RedisProtocolSupport.BufferScoreMember.SIZE * scoreMembers.size();
        int scoreMembersCapacity = 0;
        if (scoreMembers instanceof List && scoreMembers instanceof RandomAccess) {
            final List<RedisProtocolSupport.BufferScoreMember> list = (List<RedisProtocolSupport.BufferScoreMember>) scoreMembers;
            for (int i = 0; i < list.size(); ++i) {
                final RedisProtocolSupport.BufferScoreMember arg = list.get(i);
                scoreMembersCapacity += arg.encodedByteCount();
            }
        } else {
            for (RedisProtocolSupport.BufferScoreMember arg : scoreMembers) {
                scoreMembersCapacity += arg.encodedByteCount();
            }
        }
        final int capacity = calculateInitialCommandBufferSize(len, RedisProtocolSupport.Command.ZADD) +
                    calculateRequestArgumentSize(key) + (condition == null ? 0 : condition.encodedByteCount()) +
                    (change == null ? 0 : change.encodedByteCount()) + scoreMembersCapacity;
        Buffer buffer = partitionedRedisClient.executionContext().bufferAllocator().newBuffer(capacity);
        writeRequestArraySize(buffer, len);
        RedisProtocolSupport.Command.ZADD.encodeTo(buffer);
        writeRequestArgument(buffer, key);
        if (condition != null) {
            condition.encodeTo(buffer);
        }
        if (change != null) {
            change.encodeTo(buffer);
        }
        if (scoreMembers instanceof List && scoreMembers instanceof RandomAccess) {
            final List<RedisProtocolSupport.BufferScoreMember> list = (List<RedisProtocolSupport.BufferScoreMember>) scoreMembers;
            for (int i = 0; i < list.size(); ++i) {
                final RedisProtocolSupport.BufferScoreMember arg = list.get(i);
                arg.encodeTo(buffer);
            }
        } else {
            for (RedisProtocolSupport.BufferScoreMember arg : scoreMembers) {
                arg.encodeTo(buffer);
            }
        }
        final RedisRequest request = newRequest(RedisProtocolSupport.Command.ZADD, buffer);
        final RedisPartitionAttributesBuilder partitionAttributesBuilder = partitionAttributesBuilderFunction
                    .apply(RedisProtocolSupport.Command.ZADD);
        partitionAttributesBuilder.addKey(key);
        final Single<Long> result = partitionedRedisClient.request(partitionAttributesBuilder.build(), request,
                    Long.class);
        return result;
    }

    @Override
    public Single<Double> zaddIncr(@RedisProtocolSupport.Key final Buffer key,
                                   final Collection<RedisProtocolSupport.BufferScoreMember> scoreMembers) {
        requireNonNull(key);
        requireNonNull(scoreMembers);
        final int len = 3 + RedisProtocolSupport.BufferScoreMember.SIZE * scoreMembers.size();
        int scoreMembersCapacity = 0;
        if (scoreMembers instanceof List && scoreMembers instanceof RandomAccess) {
            final List<RedisProtocolSupport.BufferScoreMember> list = (List<RedisProtocolSupport.BufferScoreMember>) scoreMembers;
            for (int i = 0; i < list.size(); ++i) {
                final RedisProtocolSupport.BufferScoreMember arg = list.get(i);
                scoreMembersCapacity += arg.encodedByteCount();
            }
        } else {
            for (RedisProtocolSupport.BufferScoreMember arg : scoreMembers) {
                scoreMembersCapacity += arg.encodedByteCount();
            }
        }
        final int capacity = calculateInitialCommandBufferSize(len, RedisProtocolSupport.Command.ZADD) +
                    calculateRequestArgumentSize(key) +
                    RedisProtocolSupport.ZaddIncrement.values()[0].encodedByteCount() + scoreMembersCapacity;
        Buffer buffer = partitionedRedisClient.executionContext().bufferAllocator().newBuffer(capacity);
        writeRequestArraySize(buffer, len);
        RedisProtocolSupport.Command.ZADD.encodeTo(buffer);
        writeRequestArgument(buffer, key);
        RedisProtocolSupport.ZaddIncrement.values()[0].encodeTo(buffer);
        if (scoreMembers instanceof List && scoreMembers instanceof RandomAccess) {
            final List<RedisProtocolSupport.BufferScoreMember> list = (List<RedisProtocolSupport.BufferScoreMember>) scoreMembers;
            for (int i = 0; i < list.size(); ++i) {
                final RedisProtocolSupport.BufferScoreMember arg = list.get(i);
                arg.encodeTo(buffer);
            }
        } else {
            for (RedisProtocolSupport.BufferScoreMember arg : scoreMembers) {
                arg.encodeTo(buffer);
            }
        }
        final RedisRequest request = newRequest(RedisProtocolSupport.Command.ZADD, buffer);
        final RedisPartitionAttributesBuilder partitionAttributesBuilder = partitionAttributesBuilderFunction
                    .apply(RedisProtocolSupport.Command.ZADD);
        partitionAttributesBuilder.addKey(key);
        final Single<Double> result = partitionedRedisClient
                    .request(partitionAttributesBuilder.build(), request, String.class)
                    .map(s -> s != null ? Double.valueOf(s) : null);
        return result;
    }

    @Override
    public Single<Double> zaddIncr(@RedisProtocolSupport.Key final Buffer key,
                                   @Nullable final RedisProtocolSupport.ZaddCondition condition,
                                   @Nullable final RedisProtocolSupport.ZaddChange change, final double score,
                                   final Buffer member) {
        requireNonNull(key);
        requireNonNull(member);
        final int len = 5 + (condition == null ? 0 : 1) + (change == null ? 0 : 1);
        final int capacity = calculateInitialCommandBufferSize(len, RedisProtocolSupport.Command.ZADD) +
                    calculateRequestArgumentSize(key) +
                    RedisProtocolSupport.ZaddIncrement.values()[0].encodedByteCount() +
                    (condition == null ? 0 : condition.encodedByteCount()) +
                    (change == null ? 0 : change.encodedByteCount()) + calculateRequestArgumentSize(score) +
                    calculateRequestArgumentSize(member);
        Buffer buffer = partitionedRedisClient.executionContext().bufferAllocator().newBuffer(capacity);
        writeRequestArraySize(buffer, len);
        RedisProtocolSupport.Command.ZADD.encodeTo(buffer);
        writeRequestArgument(buffer, key);
        RedisProtocolSupport.ZaddIncrement.values()[0].encodeTo(buffer);
        if (condition != null) {
            condition.encodeTo(buffer);
        }
        if (change != null) {
            change.encodeTo(buffer);
        }
        writeRequestArgument(buffer, score);
        writeRequestArgument(buffer, member);
        final RedisRequest request = newRequest(RedisProtocolSupport.Command.ZADD, buffer);
        final RedisPartitionAttributesBuilder partitionAttributesBuilder = partitionAttributesBuilderFunction
                    .apply(RedisProtocolSupport.Command.ZADD);
        partitionAttributesBuilder.addKey(key);
        final Single<Double> result = partitionedRedisClient
                    .request(partitionAttributesBuilder.build(), request, String.class)
                    .map(s -> s != null ? Double.valueOf(s) : null);
        return result;
    }

    @Override
    public Single<Double> zaddIncr(@RedisProtocolSupport.Key final Buffer key,
                                   @Nullable final RedisProtocolSupport.ZaddCondition condition,
                                   @Nullable final RedisProtocolSupport.ZaddChange change, final double score1,
                                   final Buffer member1, final double score2, final Buffer member2) {
        requireNonNull(key);
        requireNonNull(member1);
        requireNonNull(member2);
        final int len = 7 + (condition == null ? 0 : 1) + (change == null ? 0 : 1);
        final int capacity = calculateInitialCommandBufferSize(len, RedisProtocolSupport.Command.ZADD) +
                    calculateRequestArgumentSize(key) +
                    RedisProtocolSupport.ZaddIncrement.values()[0].encodedByteCount() +
                    (condition == null ? 0 : condition.encodedByteCount()) +
                    (change == null ? 0 : change.encodedByteCount()) + calculateRequestArgumentSize(score1) +
                    calculateRequestArgumentSize(member1) + calculateRequestArgumentSize(score2) +
                    calculateRequestArgumentSize(member2);
        Buffer buffer = partitionedRedisClient.executionContext().bufferAllocator().newBuffer(capacity);
        writeRequestArraySize(buffer, len);
        RedisProtocolSupport.Command.ZADD.encodeTo(buffer);
        writeRequestArgument(buffer, key);
        RedisProtocolSupport.ZaddIncrement.values()[0].encodeTo(buffer);
        if (condition != null) {
            condition.encodeTo(buffer);
        }
        if (change != null) {
            change.encodeTo(buffer);
        }
        writeRequestArgument(buffer, score1);
        writeRequestArgument(buffer, member1);
        writeRequestArgument(buffer, score2);
        writeRequestArgument(buffer, member2);
        final RedisRequest request = newRequest(RedisProtocolSupport.Command.ZADD, buffer);
        final RedisPartitionAttributesBuilder partitionAttributesBuilder = partitionAttributesBuilderFunction
                    .apply(RedisProtocolSupport.Command.ZADD);
        partitionAttributesBuilder.addKey(key);
        final Single<Double> result = partitionedRedisClient
                    .request(partitionAttributesBuilder.build(), request, String.class)
                    .map(s -> s != null ? Double.valueOf(s) : null);
        return result;
    }

    @Override
    public Single<Double> zaddIncr(@RedisProtocolSupport.Key final Buffer key,
                                   @Nullable final RedisProtocolSupport.ZaddCondition condition,
                                   @Nullable final RedisProtocolSupport.ZaddChange change, final double score1,
                                   final Buffer member1, final double score2, final Buffer member2, final double score3,
                                   final Buffer member3) {
        requireNonNull(key);
        requireNonNull(member1);
        requireNonNull(member2);
        requireNonNull(member3);
        final int len = 9 + (condition == null ? 0 : 1) + (change == null ? 0 : 1);
        final int capacity = calculateInitialCommandBufferSize(len, RedisProtocolSupport.Command.ZADD) +
                    calculateRequestArgumentSize(key) +
                    RedisProtocolSupport.ZaddIncrement.values()[0].encodedByteCount() +
                    (condition == null ? 0 : condition.encodedByteCount()) +
                    (change == null ? 0 : change.encodedByteCount()) + calculateRequestArgumentSize(score1) +
                    calculateRequestArgumentSize(member1) + calculateRequestArgumentSize(score2) +
                    calculateRequestArgumentSize(member2) + calculateRequestArgumentSize(score3) +
                    calculateRequestArgumentSize(member3);
        Buffer buffer = partitionedRedisClient.executionContext().bufferAllocator().newBuffer(capacity);
        writeRequestArraySize(buffer, len);
        RedisProtocolSupport.Command.ZADD.encodeTo(buffer);
        writeRequestArgument(buffer, key);
        RedisProtocolSupport.ZaddIncrement.values()[0].encodeTo(buffer);
        if (condition != null) {
            condition.encodeTo(buffer);
        }
        if (change != null) {
            change.encodeTo(buffer);
        }
        writeRequestArgument(buffer, score1);
        writeRequestArgument(buffer, member1);
        writeRequestArgument(buffer, score2);
        writeRequestArgument(buffer, member2);
        writeRequestArgument(buffer, score3);
        writeRequestArgument(buffer, member3);
        final RedisRequest request = newRequest(RedisProtocolSupport.Command.ZADD, buffer);
        final RedisPartitionAttributesBuilder partitionAttributesBuilder = partitionAttributesBuilderFunction
                    .apply(RedisProtocolSupport.Command.ZADD);
        partitionAttributesBuilder.addKey(key);
        final Single<Double> result = partitionedRedisClient
                    .request(partitionAttributesBuilder.build(), request, String.class)
                    .map(s -> s != null ? Double.valueOf(s) : null);
        return result;
    }

    @Override
    public Single<Double> zaddIncr(@RedisProtocolSupport.Key final Buffer key,
                                   @Nullable final RedisProtocolSupport.ZaddCondition condition,
                                   @Nullable final RedisProtocolSupport.ZaddChange change,
                                   final Collection<RedisProtocolSupport.BufferScoreMember> scoreMembers) {
        requireNonNull(key);
        requireNonNull(scoreMembers);
        final int len = 3 + (condition == null ? 0 : 1) + (change == null ? 0 : 1) +
                    RedisProtocolSupport.BufferScoreMember.SIZE * scoreMembers.size();
        int scoreMembersCapacity = 0;
        if (scoreMembers instanceof List && scoreMembers instanceof RandomAccess) {
            final List<RedisProtocolSupport.BufferScoreMember> list = (List<RedisProtocolSupport.BufferScoreMember>) scoreMembers;
            for (int i = 0; i < list.size(); ++i) {
                final RedisProtocolSupport.BufferScoreMember arg = list.get(i);
                scoreMembersCapacity += arg.encodedByteCount();
            }
        } else {
            for (RedisProtocolSupport.BufferScoreMember arg : scoreMembers) {
                scoreMembersCapacity += arg.encodedByteCount();
            }
        }
        final int capacity = calculateInitialCommandBufferSize(len, RedisProtocolSupport.Command.ZADD) +
                    calculateRequestArgumentSize(key) +
                    RedisProtocolSupport.ZaddIncrement.values()[0].encodedByteCount() +
                    (condition == null ? 0 : condition.encodedByteCount()) +
                    (change == null ? 0 : change.encodedByteCount()) + scoreMembersCapacity;
        Buffer buffer = partitionedRedisClient.executionContext().bufferAllocator().newBuffer(capacity);
        writeRequestArraySize(buffer, len);
        RedisProtocolSupport.Command.ZADD.encodeTo(buffer);
        writeRequestArgument(buffer, key);
        RedisProtocolSupport.ZaddIncrement.values()[0].encodeTo(buffer);
        if (condition != null) {
            condition.encodeTo(buffer);
        }
        if (change != null) {
            change.encodeTo(buffer);
        }
        if (scoreMembers instanceof List && scoreMembers instanceof RandomAccess) {
            final List<RedisProtocolSupport.BufferScoreMember> list = (List<RedisProtocolSupport.BufferScoreMember>) scoreMembers;
            for (int i = 0; i < list.size(); ++i) {
                final RedisProtocolSupport.BufferScoreMember arg = list.get(i);
                arg.encodeTo(buffer);
            }
        } else {
            for (RedisProtocolSupport.BufferScoreMember arg : scoreMembers) {
                arg.encodeTo(buffer);
            }
        }
        final RedisRequest request = newRequest(RedisProtocolSupport.Command.ZADD, buffer);
        final RedisPartitionAttributesBuilder partitionAttributesBuilder = partitionAttributesBuilderFunction
                    .apply(RedisProtocolSupport.Command.ZADD);
        partitionAttributesBuilder.addKey(key);
        final Single<Double> result = partitionedRedisClient
                    .request(partitionAttributesBuilder.build(), request, String.class)
                    .map(s -> s != null ? Double.valueOf(s) : null);
        return result;
    }

    @Override
    public Single<Long> zcard(@RedisProtocolSupport.Key final Buffer key) {
        requireNonNull(key);
        final int len = 2;
        final int capacity = calculateInitialCommandBufferSize(len, RedisProtocolSupport.Command.ZCARD) +
                    calculateRequestArgumentSize(key);
        Buffer buffer = partitionedRedisClient.executionContext().bufferAllocator().newBuffer(capacity);
        writeRequestArraySize(buffer, len);
        RedisProtocolSupport.Command.ZCARD.encodeTo(buffer);
        writeRequestArgument(buffer, key);
        final RedisRequest request = newRequest(RedisProtocolSupport.Command.ZCARD, buffer);
        final RedisPartitionAttributesBuilder partitionAttributesBuilder = partitionAttributesBuilderFunction
                    .apply(RedisProtocolSupport.Command.ZCARD);
        partitionAttributesBuilder.addKey(key);
        final Single<Long> result = partitionedRedisClient.request(partitionAttributesBuilder.build(), request,
                    Long.class);
        return result;
    }

    @Override
    public Single<Long> zcount(@RedisProtocolSupport.Key final Buffer key, final double min, final double max) {
        requireNonNull(key);
        final int len = 4;
        final int capacity = calculateInitialCommandBufferSize(len, RedisProtocolSupport.Command.ZCOUNT) +
                    calculateRequestArgumentSize(key) + calculateRequestArgumentSize(min) +
                    calculateRequestArgumentSize(max);
        Buffer buffer = partitionedRedisClient.executionContext().bufferAllocator().newBuffer(capacity);
        writeRequestArraySize(buffer, len);
        RedisProtocolSupport.Command.ZCOUNT.encodeTo(buffer);
        writeRequestArgument(buffer, key);
        writeRequestArgument(buffer, min);
        writeRequestArgument(buffer, max);
        final RedisRequest request = newRequest(RedisProtocolSupport.Command.ZCOUNT, buffer);
        final RedisPartitionAttributesBuilder partitionAttributesBuilder = partitionAttributesBuilderFunction
                    .apply(RedisProtocolSupport.Command.ZCOUNT);
        partitionAttributesBuilder.addKey(key);
        final Single<Long> result = partitionedRedisClient.request(partitionAttributesBuilder.build(), request,
                    Long.class);
        return result;
    }

    @Override
    public Single<Double> zincrby(@RedisProtocolSupport.Key final Buffer key, final long increment,
                                  final Buffer member) {
        requireNonNull(key);
        requireNonNull(member);
        final int len = 4;
        final int capacity = calculateInitialCommandBufferSize(len, RedisProtocolSupport.Command.ZINCRBY) +
                    calculateRequestArgumentSize(key) + calculateRequestArgumentSize(increment) +
                    calculateRequestArgumentSize(member);
        Buffer buffer = partitionedRedisClient.executionContext().bufferAllocator().newBuffer(capacity);
        writeRequestArraySize(buffer, len);
        RedisProtocolSupport.Command.ZINCRBY.encodeTo(buffer);
        writeRequestArgument(buffer, key);
        writeRequestArgument(buffer, increment);
        writeRequestArgument(buffer, member);
        final RedisRequest request = newRequest(RedisProtocolSupport.Command.ZINCRBY, buffer);
        final RedisPartitionAttributesBuilder partitionAttributesBuilder = partitionAttributesBuilderFunction
                    .apply(RedisProtocolSupport.Command.ZINCRBY);
        partitionAttributesBuilder.addKey(key);
        final Single<Double> result = partitionedRedisClient
                    .request(partitionAttributesBuilder.build(), request, String.class)
                    .map(s -> s != null ? Double.valueOf(s) : null);
        return result;
    }

    @Override
    public Single<Long> zinterstore(@RedisProtocolSupport.Key final Buffer destination, final long numkeys,
                                    @RedisProtocolSupport.Key final Collection<Buffer> keys) {
        requireNonNull(destination);
        requireNonNull(keys);
        final int len = 3 + keys.size();
        int keysCapacity = 0;
        if (keys instanceof List && keys instanceof RandomAccess) {
            final List<Buffer> list = (List<Buffer>) keys;
            for (int i = 0; i < list.size(); ++i) {
                final Buffer arg = list.get(i);
                keysCapacity += calculateRequestArgumentSize(arg);
            }
        } else {
            for (Buffer arg : keys) {
                keysCapacity += calculateRequestArgumentSize(arg);
            }
        }
        final int capacity = calculateInitialCommandBufferSize(len, RedisProtocolSupport.Command.ZINTERSTORE) +
                    calculateRequestArgumentSize(destination) + calculateRequestArgumentSize(numkeys) + keysCapacity;
        Buffer buffer = partitionedRedisClient.executionContext().bufferAllocator().newBuffer(capacity);
        writeRequestArraySize(buffer, len);
        RedisProtocolSupport.Command.ZINTERSTORE.encodeTo(buffer);
        writeRequestArgument(buffer, destination);
        writeRequestArgument(buffer, numkeys);
        if (keys instanceof List && keys instanceof RandomAccess) {
            final List<Buffer> list = (List<Buffer>) keys;
            for (int i = 0; i < list.size(); ++i) {
                final Buffer arg = list.get(i);
                writeRequestArgument(buffer, arg);
            }
        } else {
            for (Buffer arg : keys) {
                writeRequestArgument(buffer, arg);
            }
        }
        final RedisRequest request = newRequest(RedisProtocolSupport.Command.ZINTERSTORE, buffer);
        final RedisPartitionAttributesBuilder partitionAttributesBuilder = partitionAttributesBuilderFunction
                    .apply(RedisProtocolSupport.Command.ZINTERSTORE);
        partitionAttributesBuilder.addKey(destination);
        addBufferKeysToAttributeBuilder(keys, partitionAttributesBuilder);
        final Single<Long> result = partitionedRedisClient.request(partitionAttributesBuilder.build(), request,
                    Long.class);
        return result;
    }

    @Override
    public Single<Long> zinterstore(@RedisProtocolSupport.Key final Buffer destination, final long numkeys,
                                    @RedisProtocolSupport.Key final Collection<Buffer> keys,
                                    final Collection<Long> weights,
                                    @Nullable final RedisProtocolSupport.ZinterstoreAggregate aggregate) {
        requireNonNull(destination);
        requireNonNull(keys);
        requireNonNull(weights);
        final int len = 4 + keys.size() + weights.size() + (aggregate == null ? 0 : 1);
        int keysCapacity = 0;
        if (keys instanceof List && keys instanceof RandomAccess) {
            final List<Buffer> list = (List<Buffer>) keys;
            for (int i = 0; i < list.size(); ++i) {
                final Buffer arg = list.get(i);
                keysCapacity += calculateRequestArgumentSize(arg);
            }
        } else {
            for (Buffer arg : keys) {
                keysCapacity += calculateRequestArgumentSize(arg);
            }
        }
        int weightsCapacity = 0;
        if (weights instanceof List && weights instanceof RandomAccess) {
            final List<Long> list = (List<Long>) weights;
            for (int i = 0; i < list.size(); ++i) {
                final Long arg = list.get(i);
                weightsCapacity += calculateRequestArgumentSize(arg);
            }
        } else {
            for (Long arg : weights) {
                weightsCapacity += calculateRequestArgumentSize(arg);
            }
        }
        final int capacity = calculateInitialCommandBufferSize(len, RedisProtocolSupport.Command.ZINTERSTORE) +
                    calculateRequestArgumentSize(destination) + calculateRequestArgumentSize(numkeys) + keysCapacity +
                    RedisProtocolSupport.SubCommand.WEIGHTS.encodedByteCount() + weightsCapacity +
                    (aggregate == null ? 0 : aggregate.encodedByteCount());
        Buffer buffer = partitionedRedisClient.executionContext().bufferAllocator().newBuffer(capacity);
        writeRequestArraySize(buffer, len);
        RedisProtocolSupport.Command.ZINTERSTORE.encodeTo(buffer);
        writeRequestArgument(buffer, destination);
        writeRequestArgument(buffer, numkeys);
        if (keys instanceof List && keys instanceof RandomAccess) {
            final List<Buffer> list = (List<Buffer>) keys;
            for (int i = 0; i < list.size(); ++i) {
                final Buffer arg = list.get(i);
                writeRequestArgument(buffer, arg);
            }
        } else {
            for (Buffer arg : keys) {
                writeRequestArgument(buffer, arg);
            }
        }
        RedisProtocolSupport.SubCommand.WEIGHTS.encodeTo(buffer);
        if (weights instanceof List && weights instanceof RandomAccess) {
            final List<Long> list = (List<Long>) weights;
            for (int i = 0; i < list.size(); ++i) {
                final Long arg = list.get(i);
                writeRequestArgument(buffer, arg);
            }
        } else {
            for (Long arg : weights) {
                writeRequestArgument(buffer, arg);
            }
        }
        if (aggregate != null) {
            aggregate.encodeTo(buffer);
        }
        final RedisRequest request = newRequest(RedisProtocolSupport.Command.ZINTERSTORE, buffer);
        final RedisPartitionAttributesBuilder partitionAttributesBuilder = partitionAttributesBuilderFunction
                    .apply(RedisProtocolSupport.Command.ZINTERSTORE);
        partitionAttributesBuilder.addKey(destination);
        addBufferKeysToAttributeBuilder(keys, partitionAttributesBuilder);
        final Single<Long> result = partitionedRedisClient.request(partitionAttributesBuilder.build(), request,
                    Long.class);
        return result;
    }

    @Override
    public Single<Long> zlexcount(@RedisProtocolSupport.Key final Buffer key, final Buffer min, final Buffer max) {
        requireNonNull(key);
        requireNonNull(min);
        requireNonNull(max);
        final int len = 4;
        final int capacity = calculateInitialCommandBufferSize(len, RedisProtocolSupport.Command.ZLEXCOUNT) +
                    calculateRequestArgumentSize(key) + calculateRequestArgumentSize(min) +
                    calculateRequestArgumentSize(max);
        Buffer buffer = partitionedRedisClient.executionContext().bufferAllocator().newBuffer(capacity);
        writeRequestArraySize(buffer, len);
        RedisProtocolSupport.Command.ZLEXCOUNT.encodeTo(buffer);
        writeRequestArgument(buffer, key);
        writeRequestArgument(buffer, min);
        writeRequestArgument(buffer, max);
        final RedisRequest request = newRequest(RedisProtocolSupport.Command.ZLEXCOUNT, buffer);
        final RedisPartitionAttributesBuilder partitionAttributesBuilder = partitionAttributesBuilderFunction
                    .apply(RedisProtocolSupport.Command.ZLEXCOUNT);
        partitionAttributesBuilder.addKey(key);
        final Single<Long> result = partitionedRedisClient.request(partitionAttributesBuilder.build(), request,
                    Long.class);
        return result;
    }

    @Override
    public <T> Single<List<T>> zpopmax(@RedisProtocolSupport.Key final Buffer key) {
        requireNonNull(key);
        final int len = 2;
        final int capacity = calculateInitialCommandBufferSize(len, RedisProtocolSupport.Command.ZPOPMAX) +
                    calculateRequestArgumentSize(key);
        Buffer buffer = partitionedRedisClient.executionContext().bufferAllocator().newBuffer(capacity);
        writeRequestArraySize(buffer, len);
        RedisProtocolSupport.Command.ZPOPMAX.encodeTo(buffer);
        writeRequestArgument(buffer, key);
        final RedisRequest request = newRequest(RedisProtocolSupport.Command.ZPOPMAX, buffer);
        final RedisPartitionAttributesBuilder partitionAttributesBuilder = partitionAttributesBuilderFunction
                    .apply(RedisProtocolSupport.Command.ZPOPMAX);
        partitionAttributesBuilder.addKey(key);
        final Single<List<T>> result = (Single) partitionedRedisClient.request(partitionAttributesBuilder.build(),
                    request, List.class);
        return result;
    }

    @Override
    public <T> Single<List<T>> zpopmax(@RedisProtocolSupport.Key final Buffer key, @Nullable final Long count) {
        requireNonNull(key);
        final int len = 2 + (count == null ? 0 : 1);
        final int capacity = calculateInitialCommandBufferSize(len, RedisProtocolSupport.Command.ZPOPMAX) +
                    calculateRequestArgumentSize(key) + (count == null ? 0 : calculateRequestArgumentSize(count));
        Buffer buffer = partitionedRedisClient.executionContext().bufferAllocator().newBuffer(capacity);
        writeRequestArraySize(buffer, len);
        RedisProtocolSupport.Command.ZPOPMAX.encodeTo(buffer);
        writeRequestArgument(buffer, key);
        if (count != null) {
            writeRequestArgument(buffer, count);
        }
        final RedisRequest request = newRequest(RedisProtocolSupport.Command.ZPOPMAX, buffer);
        final RedisPartitionAttributesBuilder partitionAttributesBuilder = partitionAttributesBuilderFunction
                    .apply(RedisProtocolSupport.Command.ZPOPMAX);
        partitionAttributesBuilder.addKey(key);
        final Single<List<T>> result = (Single) partitionedRedisClient.request(partitionAttributesBuilder.build(),
                    request, List.class);
        return result;
    }

    @Override
    public <T> Single<List<T>> zpopmin(@RedisProtocolSupport.Key final Buffer key) {
        requireNonNull(key);
        final int len = 2;
        final int capacity = calculateInitialCommandBufferSize(len, RedisProtocolSupport.Command.ZPOPMIN) +
                    calculateRequestArgumentSize(key);
        Buffer buffer = partitionedRedisClient.executionContext().bufferAllocator().newBuffer(capacity);
        writeRequestArraySize(buffer, len);
        RedisProtocolSupport.Command.ZPOPMIN.encodeTo(buffer);
        writeRequestArgument(buffer, key);
        final RedisRequest request = newRequest(RedisProtocolSupport.Command.ZPOPMIN, buffer);
        final RedisPartitionAttributesBuilder partitionAttributesBuilder = partitionAttributesBuilderFunction
                    .apply(RedisProtocolSupport.Command.ZPOPMIN);
        partitionAttributesBuilder.addKey(key);
        final Single<List<T>> result = (Single) partitionedRedisClient.request(partitionAttributesBuilder.build(),
                    request, List.class);
        return result;
    }

    @Override
    public <T> Single<List<T>> zpopmin(@RedisProtocolSupport.Key final Buffer key, @Nullable final Long count) {
        requireNonNull(key);
        final int len = 2 + (count == null ? 0 : 1);
        final int capacity = calculateInitialCommandBufferSize(len, RedisProtocolSupport.Command.ZPOPMIN) +
                    calculateRequestArgumentSize(key) + (count == null ? 0 : calculateRequestArgumentSize(count));
        Buffer buffer = partitionedRedisClient.executionContext().bufferAllocator().newBuffer(capacity);
        writeRequestArraySize(buffer, len);
        RedisProtocolSupport.Command.ZPOPMIN.encodeTo(buffer);
        writeRequestArgument(buffer, key);
        if (count != null) {
            writeRequestArgument(buffer, count);
        }
        final RedisRequest request = newRequest(RedisProtocolSupport.Command.ZPOPMIN, buffer);
        final RedisPartitionAttributesBuilder partitionAttributesBuilder = partitionAttributesBuilderFunction
                    .apply(RedisProtocolSupport.Command.ZPOPMIN);
        partitionAttributesBuilder.addKey(key);
        final Single<List<T>> result = (Single) partitionedRedisClient.request(partitionAttributesBuilder.build(),
                    request, List.class);
        return result;
    }

    @Override
    public <T> Single<List<T>> zrange(@RedisProtocolSupport.Key final Buffer key, final long start, final long stop) {
        requireNonNull(key);
        final int len = 4;
        final int capacity = calculateInitialCommandBufferSize(len, RedisProtocolSupport.Command.ZRANGE) +
                    calculateRequestArgumentSize(key) + calculateRequestArgumentSize(start) +
                    calculateRequestArgumentSize(stop);
        Buffer buffer = partitionedRedisClient.executionContext().bufferAllocator().newBuffer(capacity);
        writeRequestArraySize(buffer, len);
        RedisProtocolSupport.Command.ZRANGE.encodeTo(buffer);
        writeRequestArgument(buffer, key);
        writeRequestArgument(buffer, start);
        writeRequestArgument(buffer, stop);
        final RedisRequest request = newRequest(RedisProtocolSupport.Command.ZRANGE, buffer);
        final RedisPartitionAttributesBuilder partitionAttributesBuilder = partitionAttributesBuilderFunction
                    .apply(RedisProtocolSupport.Command.ZRANGE);
        partitionAttributesBuilder.addKey(key);
        final Single<List<T>> result = (Single) partitionedRedisClient.request(partitionAttributesBuilder.build(),
                    request, List.class);
        return result;
    }

    @Override
    public <T> Single<List<T>> zrange(@RedisProtocolSupport.Key final Buffer key, final long start, final long stop,
                                      @Nullable final RedisProtocolSupport.ZrangeWithscores withscores) {
        requireNonNull(key);
        final int len = 4 + (withscores == null ? 0 : 1);
        final int capacity = calculateInitialCommandBufferSize(len, RedisProtocolSupport.Command.ZRANGE) +
                    calculateRequestArgumentSize(key) + calculateRequestArgumentSize(start) +
                    calculateRequestArgumentSize(stop) + (withscores == null ? 0 : withscores.encodedByteCount());
        Buffer buffer = partitionedRedisClient.executionContext().bufferAllocator().newBuffer(capacity);
        writeRequestArraySize(buffer, len);
        RedisProtocolSupport.Command.ZRANGE.encodeTo(buffer);
        writeRequestArgument(buffer, key);
        writeRequestArgument(buffer, start);
        writeRequestArgument(buffer, stop);
        if (withscores != null) {
            withscores.encodeTo(buffer);
        }
        final RedisRequest request = newRequest(RedisProtocolSupport.Command.ZRANGE, buffer);
        final RedisPartitionAttributesBuilder partitionAttributesBuilder = partitionAttributesBuilderFunction
                    .apply(RedisProtocolSupport.Command.ZRANGE);
        partitionAttributesBuilder.addKey(key);
        final Single<List<T>> result = (Single) partitionedRedisClient.request(partitionAttributesBuilder.build(),
                    request, List.class);
        return result;
    }

    @Override
    public <T> Single<List<T>> zrangebylex(@RedisProtocolSupport.Key final Buffer key, final Buffer min,
                                           final Buffer max) {
        requireNonNull(key);
        requireNonNull(min);
        requireNonNull(max);
        final int len = 4;
        final int capacity = calculateInitialCommandBufferSize(len, RedisProtocolSupport.Command.ZRANGEBYLEX) +
                    calculateRequestArgumentSize(key) + calculateRequestArgumentSize(min) +
                    calculateRequestArgumentSize(max);
        Buffer buffer = partitionedRedisClient.executionContext().bufferAllocator().newBuffer(capacity);
        writeRequestArraySize(buffer, len);
        RedisProtocolSupport.Command.ZRANGEBYLEX.encodeTo(buffer);
        writeRequestArgument(buffer, key);
        writeRequestArgument(buffer, min);
        writeRequestArgument(buffer, max);
        final RedisRequest request = newRequest(RedisProtocolSupport.Command.ZRANGEBYLEX, buffer);
        final RedisPartitionAttributesBuilder partitionAttributesBuilder = partitionAttributesBuilderFunction
                    .apply(RedisProtocolSupport.Command.ZRANGEBYLEX);
        partitionAttributesBuilder.addKey(key);
        final Single<List<T>> result = (Single) partitionedRedisClient.request(partitionAttributesBuilder.build(),
                    request, List.class);
        return result;
    }

    @Override
    public <T> Single<List<T>> zrangebylex(@RedisProtocolSupport.Key final Buffer key, final Buffer min,
                                           final Buffer max,
                                           @Nullable final RedisProtocolSupport.OffsetCount offsetCount) {
        requireNonNull(key);
        requireNonNull(min);
        requireNonNull(max);
        final int len = 4 + (offsetCount == null ? 0 : RedisProtocolSupport.OffsetCount.SIZE);
        final int capacity = calculateInitialCommandBufferSize(len, RedisProtocolSupport.Command.ZRANGEBYLEX) +
                    calculateRequestArgumentSize(key) + calculateRequestArgumentSize(min) +
                    calculateRequestArgumentSize(max) + (offsetCount == null ? 0 : offsetCount.encodedByteCount());
        Buffer buffer = partitionedRedisClient.executionContext().bufferAllocator().newBuffer(capacity);
        writeRequestArraySize(buffer, len);
        RedisProtocolSupport.Command.ZRANGEBYLEX.encodeTo(buffer);
        writeRequestArgument(buffer, key);
        writeRequestArgument(buffer, min);
        writeRequestArgument(buffer, max);
        if (offsetCount != null) {
            offsetCount.encodeTo(buffer);
        }
        final RedisRequest request = newRequest(RedisProtocolSupport.Command.ZRANGEBYLEX, buffer);
        final RedisPartitionAttributesBuilder partitionAttributesBuilder = partitionAttributesBuilderFunction
                    .apply(RedisProtocolSupport.Command.ZRANGEBYLEX);
        partitionAttributesBuilder.addKey(key);
        final Single<List<T>> result = (Single) partitionedRedisClient.request(partitionAttributesBuilder.build(),
                    request, List.class);
        return result;
    }

    @Override
    public <T> Single<List<T>> zrangebyscore(@RedisProtocolSupport.Key final Buffer key, final double min,
                                             final double max) {
        requireNonNull(key);
        final int len = 4;
        final int capacity = calculateInitialCommandBufferSize(len, RedisProtocolSupport.Command.ZRANGEBYSCORE) +
                    calculateRequestArgumentSize(key) + calculateRequestArgumentSize(min) +
                    calculateRequestArgumentSize(max);
        Buffer buffer = partitionedRedisClient.executionContext().bufferAllocator().newBuffer(capacity);
        writeRequestArraySize(buffer, len);
        RedisProtocolSupport.Command.ZRANGEBYSCORE.encodeTo(buffer);
        writeRequestArgument(buffer, key);
        writeRequestArgument(buffer, min);
        writeRequestArgument(buffer, max);
        final RedisRequest request = newRequest(RedisProtocolSupport.Command.ZRANGEBYSCORE, buffer);
        final RedisPartitionAttributesBuilder partitionAttributesBuilder = partitionAttributesBuilderFunction
                    .apply(RedisProtocolSupport.Command.ZRANGEBYSCORE);
        partitionAttributesBuilder.addKey(key);
        final Single<List<T>> result = (Single) partitionedRedisClient.request(partitionAttributesBuilder.build(),
                    request, List.class);
        return result;
    }

    @Override
    public <T> Single<List<T>> zrangebyscore(@RedisProtocolSupport.Key final Buffer key, final double min,
                                             final double max,
                                             @Nullable final RedisProtocolSupport.ZrangebyscoreWithscores withscores,
                                             @Nullable final RedisProtocolSupport.OffsetCount offsetCount) {
        requireNonNull(key);
        final int len = 4 + (withscores == null ? 0 : 1) +
                    (offsetCount == null ? 0 : RedisProtocolSupport.OffsetCount.SIZE);
        final int capacity = calculateInitialCommandBufferSize(len, RedisProtocolSupport.Command.ZRANGEBYSCORE) +
                    calculateRequestArgumentSize(key) + calculateRequestArgumentSize(min) +
                    calculateRequestArgumentSize(max) + (withscores == null ? 0 : withscores.encodedByteCount()) +
                    (offsetCount == null ? 0 : offsetCount.encodedByteCount());
        Buffer buffer = partitionedRedisClient.executionContext().bufferAllocator().newBuffer(capacity);
        writeRequestArraySize(buffer, len);
        RedisProtocolSupport.Command.ZRANGEBYSCORE.encodeTo(buffer);
        writeRequestArgument(buffer, key);
        writeRequestArgument(buffer, min);
        writeRequestArgument(buffer, max);
        if (withscores != null) {
            withscores.encodeTo(buffer);
        }
        if (offsetCount != null) {
            offsetCount.encodeTo(buffer);
        }
        final RedisRequest request = newRequest(RedisProtocolSupport.Command.ZRANGEBYSCORE, buffer);
        final RedisPartitionAttributesBuilder partitionAttributesBuilder = partitionAttributesBuilderFunction
                    .apply(RedisProtocolSupport.Command.ZRANGEBYSCORE);
        partitionAttributesBuilder.addKey(key);
        final Single<List<T>> result = (Single) partitionedRedisClient.request(partitionAttributesBuilder.build(),
                    request, List.class);
        return result;
    }

    @Override
    public Single<Long> zrank(@RedisProtocolSupport.Key final Buffer key, final Buffer member) {
        requireNonNull(key);
        requireNonNull(member);
        final int len = 3;
        final int capacity = calculateInitialCommandBufferSize(len, RedisProtocolSupport.Command.ZRANK) +
                    calculateRequestArgumentSize(key) + calculateRequestArgumentSize(member);
        Buffer buffer = partitionedRedisClient.executionContext().bufferAllocator().newBuffer(capacity);
        writeRequestArraySize(buffer, len);
        RedisProtocolSupport.Command.ZRANK.encodeTo(buffer);
        writeRequestArgument(buffer, key);
        writeRequestArgument(buffer, member);
        final RedisRequest request = newRequest(RedisProtocolSupport.Command.ZRANK, buffer);
        final RedisPartitionAttributesBuilder partitionAttributesBuilder = partitionAttributesBuilderFunction
                    .apply(RedisProtocolSupport.Command.ZRANK);
        partitionAttributesBuilder.addKey(key);
        final Single<Long> result = partitionedRedisClient.request(partitionAttributesBuilder.build(), request,
                    Long.class);
        return result;
    }

    @Override
    public Single<Long> zrem(@RedisProtocolSupport.Key final Buffer key, final Buffer member) {
        requireNonNull(key);
        requireNonNull(member);
        final int len = 3;
        final int capacity = calculateInitialCommandBufferSize(len, RedisProtocolSupport.Command.ZREM) +
                    calculateRequestArgumentSize(key) + calculateRequestArgumentSize(member);
        Buffer buffer = partitionedRedisClient.executionContext().bufferAllocator().newBuffer(capacity);
        writeRequestArraySize(buffer, len);
        RedisProtocolSupport.Command.ZREM.encodeTo(buffer);
        writeRequestArgument(buffer, key);
        writeRequestArgument(buffer, member);
        final RedisRequest request = newRequest(RedisProtocolSupport.Command.ZREM, buffer);
        final RedisPartitionAttributesBuilder partitionAttributesBuilder = partitionAttributesBuilderFunction
                    .apply(RedisProtocolSupport.Command.ZREM);
        partitionAttributesBuilder.addKey(key);
        final Single<Long> result = partitionedRedisClient.request(partitionAttributesBuilder.build(), request,
                    Long.class);
        return result;
    }

    @Override
    public Single<Long> zrem(@RedisProtocolSupport.Key final Buffer key, final Buffer member1, final Buffer member2) {
        requireNonNull(key);
        requireNonNull(member1);
        requireNonNull(member2);
        final int len = 4;
        final int capacity = calculateInitialCommandBufferSize(len, RedisProtocolSupport.Command.ZREM) +
                    calculateRequestArgumentSize(key) + calculateRequestArgumentSize(member1) +
                    calculateRequestArgumentSize(member2);
        Buffer buffer = partitionedRedisClient.executionContext().bufferAllocator().newBuffer(capacity);
        writeRequestArraySize(buffer, len);
        RedisProtocolSupport.Command.ZREM.encodeTo(buffer);
        writeRequestArgument(buffer, key);
        writeRequestArgument(buffer, member1);
        writeRequestArgument(buffer, member2);
        final RedisRequest request = newRequest(RedisProtocolSupport.Command.ZREM, buffer);
        final RedisPartitionAttributesBuilder partitionAttributesBuilder = partitionAttributesBuilderFunction
                    .apply(RedisProtocolSupport.Command.ZREM);
        partitionAttributesBuilder.addKey(key);
        final Single<Long> result = partitionedRedisClient.request(partitionAttributesBuilder.build(), request,
                    Long.class);
        return result;
    }

    @Override
    public Single<Long> zrem(@RedisProtocolSupport.Key final Buffer key, final Buffer member1, final Buffer member2,
                             final Buffer member3) {
        requireNonNull(key);
        requireNonNull(member1);
        requireNonNull(member2);
        requireNonNull(member3);
        final int len = 5;
        final int capacity = calculateInitialCommandBufferSize(len, RedisProtocolSupport.Command.ZREM) +
                    calculateRequestArgumentSize(key) + calculateRequestArgumentSize(member1) +
                    calculateRequestArgumentSize(member2) + calculateRequestArgumentSize(member3);
        Buffer buffer = partitionedRedisClient.executionContext().bufferAllocator().newBuffer(capacity);
        writeRequestArraySize(buffer, len);
        RedisProtocolSupport.Command.ZREM.encodeTo(buffer);
        writeRequestArgument(buffer, key);
        writeRequestArgument(buffer, member1);
        writeRequestArgument(buffer, member2);
        writeRequestArgument(buffer, member3);
        final RedisRequest request = newRequest(RedisProtocolSupport.Command.ZREM, buffer);
        final RedisPartitionAttributesBuilder partitionAttributesBuilder = partitionAttributesBuilderFunction
                    .apply(RedisProtocolSupport.Command.ZREM);
        partitionAttributesBuilder.addKey(key);
        final Single<Long> result = partitionedRedisClient.request(partitionAttributesBuilder.build(), request,
                    Long.class);
        return result;
    }

    @Override
    public Single<Long> zrem(@RedisProtocolSupport.Key final Buffer key, final Collection<Buffer> members) {
        requireNonNull(key);
        requireNonNull(members);
        final int len = 2 + members.size();
        int membersCapacity = 0;
        if (members instanceof List && members instanceof RandomAccess) {
            final List<Buffer> list = (List<Buffer>) members;
            for (int i = 0; i < list.size(); ++i) {
                final Buffer arg = list.get(i);
                membersCapacity += calculateRequestArgumentSize(arg);
            }
        } else {
            for (Buffer arg : members) {
                membersCapacity += calculateRequestArgumentSize(arg);
            }
        }
        final int capacity = calculateInitialCommandBufferSize(len, RedisProtocolSupport.Command.ZREM) +
                    calculateRequestArgumentSize(key) + membersCapacity;
        Buffer buffer = partitionedRedisClient.executionContext().bufferAllocator().newBuffer(capacity);
        writeRequestArraySize(buffer, len);
        RedisProtocolSupport.Command.ZREM.encodeTo(buffer);
        writeRequestArgument(buffer, key);
        if (members instanceof List && members instanceof RandomAccess) {
            final List<Buffer> list = (List<Buffer>) members;
            for (int i = 0; i < list.size(); ++i) {
                final Buffer arg = list.get(i);
                writeRequestArgument(buffer, arg);
            }
        } else {
            for (Buffer arg : members) {
                writeRequestArgument(buffer, arg);
            }
        }
        final RedisRequest request = newRequest(RedisProtocolSupport.Command.ZREM, buffer);
        final RedisPartitionAttributesBuilder partitionAttributesBuilder = partitionAttributesBuilderFunction
                    .apply(RedisProtocolSupport.Command.ZREM);
        partitionAttributesBuilder.addKey(key);
        final Single<Long> result = partitionedRedisClient.request(partitionAttributesBuilder.build(), request,
                    Long.class);
        return result;
    }

    @Override
    public Single<Long> zremrangebylex(@RedisProtocolSupport.Key final Buffer key, final Buffer min, final Buffer max) {
        requireNonNull(key);
        requireNonNull(min);
        requireNonNull(max);
        final int len = 4;
        final int capacity = calculateInitialCommandBufferSize(len, RedisProtocolSupport.Command.ZREMRANGEBYLEX) +
                    calculateRequestArgumentSize(key) + calculateRequestArgumentSize(min) +
                    calculateRequestArgumentSize(max);
        Buffer buffer = partitionedRedisClient.executionContext().bufferAllocator().newBuffer(capacity);
        writeRequestArraySize(buffer, len);
        RedisProtocolSupport.Command.ZREMRANGEBYLEX.encodeTo(buffer);
        writeRequestArgument(buffer, key);
        writeRequestArgument(buffer, min);
        writeRequestArgument(buffer, max);
        final RedisRequest request = newRequest(RedisProtocolSupport.Command.ZREMRANGEBYLEX, buffer);
        final RedisPartitionAttributesBuilder partitionAttributesBuilder = partitionAttributesBuilderFunction
                    .apply(RedisProtocolSupport.Command.ZREMRANGEBYLEX);
        partitionAttributesBuilder.addKey(key);
        final Single<Long> result = partitionedRedisClient.request(partitionAttributesBuilder.build(), request,
                    Long.class);
        return result;
    }

    @Override
    public Single<Long> zremrangebyrank(@RedisProtocolSupport.Key final Buffer key, final long start, final long stop) {
        requireNonNull(key);
        final int len = 4;
        final int capacity = calculateInitialCommandBufferSize(len, RedisProtocolSupport.Command.ZREMRANGEBYRANK) +
                    calculateRequestArgumentSize(key) + calculateRequestArgumentSize(start) +
                    calculateRequestArgumentSize(stop);
        Buffer buffer = partitionedRedisClient.executionContext().bufferAllocator().newBuffer(capacity);
        writeRequestArraySize(buffer, len);
        RedisProtocolSupport.Command.ZREMRANGEBYRANK.encodeTo(buffer);
        writeRequestArgument(buffer, key);
        writeRequestArgument(buffer, start);
        writeRequestArgument(buffer, stop);
        final RedisRequest request = newRequest(RedisProtocolSupport.Command.ZREMRANGEBYRANK, buffer);
        final RedisPartitionAttributesBuilder partitionAttributesBuilder = partitionAttributesBuilderFunction
                    .apply(RedisProtocolSupport.Command.ZREMRANGEBYRANK);
        partitionAttributesBuilder.addKey(key);
        final Single<Long> result = partitionedRedisClient.request(partitionAttributesBuilder.build(), request,
                    Long.class);
        return result;
    }

    @Override
    public Single<Long> zremrangebyscore(@RedisProtocolSupport.Key final Buffer key, final double min,
                                         final double max) {
        requireNonNull(key);
        final int len = 4;
        final int capacity = calculateInitialCommandBufferSize(len, RedisProtocolSupport.Command.ZREMRANGEBYSCORE) +
                    calculateRequestArgumentSize(key) + calculateRequestArgumentSize(min) +
                    calculateRequestArgumentSize(max);
        Buffer buffer = partitionedRedisClient.executionContext().bufferAllocator().newBuffer(capacity);
        writeRequestArraySize(buffer, len);
        RedisProtocolSupport.Command.ZREMRANGEBYSCORE.encodeTo(buffer);
        writeRequestArgument(buffer, key);
        writeRequestArgument(buffer, min);
        writeRequestArgument(buffer, max);
        final RedisRequest request = newRequest(RedisProtocolSupport.Command.ZREMRANGEBYSCORE, buffer);
        final RedisPartitionAttributesBuilder partitionAttributesBuilder = partitionAttributesBuilderFunction
                    .apply(RedisProtocolSupport.Command.ZREMRANGEBYSCORE);
        partitionAttributesBuilder.addKey(key);
        final Single<Long> result = partitionedRedisClient.request(partitionAttributesBuilder.build(), request,
                    Long.class);
        return result;
    }

    @Override
    public <T> Single<List<T>> zrevrange(@RedisProtocolSupport.Key final Buffer key, final long start,
                                         final long stop) {
        requireNonNull(key);
        final int len = 4;
        final int capacity = calculateInitialCommandBufferSize(len, RedisProtocolSupport.Command.ZREVRANGE) +
                    calculateRequestArgumentSize(key) + calculateRequestArgumentSize(start) +
                    calculateRequestArgumentSize(stop);
        Buffer buffer = partitionedRedisClient.executionContext().bufferAllocator().newBuffer(capacity);
        writeRequestArraySize(buffer, len);
        RedisProtocolSupport.Command.ZREVRANGE.encodeTo(buffer);
        writeRequestArgument(buffer, key);
        writeRequestArgument(buffer, start);
        writeRequestArgument(buffer, stop);
        final RedisRequest request = newRequest(RedisProtocolSupport.Command.ZREVRANGE, buffer);
        final RedisPartitionAttributesBuilder partitionAttributesBuilder = partitionAttributesBuilderFunction
                    .apply(RedisProtocolSupport.Command.ZREVRANGE);
        partitionAttributesBuilder.addKey(key);
        final Single<List<T>> result = (Single) partitionedRedisClient.request(partitionAttributesBuilder.build(),
                    request, List.class);
        return result;
    }

    @Override
    public <T> Single<List<T>> zrevrange(@RedisProtocolSupport.Key final Buffer key, final long start, final long stop,
                                         @Nullable final RedisProtocolSupport.ZrevrangeWithscores withscores) {
        requireNonNull(key);
        final int len = 4 + (withscores == null ? 0 : 1);
        final int capacity = calculateInitialCommandBufferSize(len, RedisProtocolSupport.Command.ZREVRANGE) +
                    calculateRequestArgumentSize(key) + calculateRequestArgumentSize(start) +
                    calculateRequestArgumentSize(stop) + (withscores == null ? 0 : withscores.encodedByteCount());
        Buffer buffer = partitionedRedisClient.executionContext().bufferAllocator().newBuffer(capacity);
        writeRequestArraySize(buffer, len);
        RedisProtocolSupport.Command.ZREVRANGE.encodeTo(buffer);
        writeRequestArgument(buffer, key);
        writeRequestArgument(buffer, start);
        writeRequestArgument(buffer, stop);
        if (withscores != null) {
            withscores.encodeTo(buffer);
        }
        final RedisRequest request = newRequest(RedisProtocolSupport.Command.ZREVRANGE, buffer);
        final RedisPartitionAttributesBuilder partitionAttributesBuilder = partitionAttributesBuilderFunction
                    .apply(RedisProtocolSupport.Command.ZREVRANGE);
        partitionAttributesBuilder.addKey(key);
        final Single<List<T>> result = (Single) partitionedRedisClient.request(partitionAttributesBuilder.build(),
                    request, List.class);
        return result;
    }

    @Override
    public <T> Single<List<T>> zrevrangebylex(@RedisProtocolSupport.Key final Buffer key, final Buffer max,
                                              final Buffer min) {
        requireNonNull(key);
        requireNonNull(max);
        requireNonNull(min);
        final int len = 4;
        final int capacity = calculateInitialCommandBufferSize(len, RedisProtocolSupport.Command.ZREVRANGEBYLEX) +
                    calculateRequestArgumentSize(key) + calculateRequestArgumentSize(max) +
                    calculateRequestArgumentSize(min);
        Buffer buffer = partitionedRedisClient.executionContext().bufferAllocator().newBuffer(capacity);
        writeRequestArraySize(buffer, len);
        RedisProtocolSupport.Command.ZREVRANGEBYLEX.encodeTo(buffer);
        writeRequestArgument(buffer, key);
        writeRequestArgument(buffer, max);
        writeRequestArgument(buffer, min);
        final RedisRequest request = newRequest(RedisProtocolSupport.Command.ZREVRANGEBYLEX, buffer);
        final RedisPartitionAttributesBuilder partitionAttributesBuilder = partitionAttributesBuilderFunction
                    .apply(RedisProtocolSupport.Command.ZREVRANGEBYLEX);
        partitionAttributesBuilder.addKey(key);
        final Single<List<T>> result = (Single) partitionedRedisClient.request(partitionAttributesBuilder.build(),
                    request, List.class);
        return result;
    }

    @Override
    public <T> Single<List<T>> zrevrangebylex(@RedisProtocolSupport.Key final Buffer key, final Buffer max,
                                              final Buffer min,
                                              @Nullable final RedisProtocolSupport.OffsetCount offsetCount) {
        requireNonNull(key);
        requireNonNull(max);
        requireNonNull(min);
        final int len = 4 + (offsetCount == null ? 0 : RedisProtocolSupport.OffsetCount.SIZE);
        final int capacity = calculateInitialCommandBufferSize(len, RedisProtocolSupport.Command.ZREVRANGEBYLEX) +
                    calculateRequestArgumentSize(key) + calculateRequestArgumentSize(max) +
                    calculateRequestArgumentSize(min) + (offsetCount == null ? 0 : offsetCount.encodedByteCount());
        Buffer buffer = partitionedRedisClient.executionContext().bufferAllocator().newBuffer(capacity);
        writeRequestArraySize(buffer, len);
        RedisProtocolSupport.Command.ZREVRANGEBYLEX.encodeTo(buffer);
        writeRequestArgument(buffer, key);
        writeRequestArgument(buffer, max);
        writeRequestArgument(buffer, min);
        if (offsetCount != null) {
            offsetCount.encodeTo(buffer);
        }
        final RedisRequest request = newRequest(RedisProtocolSupport.Command.ZREVRANGEBYLEX, buffer);
        final RedisPartitionAttributesBuilder partitionAttributesBuilder = partitionAttributesBuilderFunction
                    .apply(RedisProtocolSupport.Command.ZREVRANGEBYLEX);
        partitionAttributesBuilder.addKey(key);
        final Single<List<T>> result = (Single) partitionedRedisClient.request(partitionAttributesBuilder.build(),
                    request, List.class);
        return result;
    }

    @Override
    public <T> Single<List<T>> zrevrangebyscore(@RedisProtocolSupport.Key final Buffer key, final double max,
                                                final double min) {
        requireNonNull(key);
        final int len = 4;
        final int capacity = calculateInitialCommandBufferSize(len, RedisProtocolSupport.Command.ZREVRANGEBYSCORE) +
                    calculateRequestArgumentSize(key) + calculateRequestArgumentSize(max) +
                    calculateRequestArgumentSize(min);
        Buffer buffer = partitionedRedisClient.executionContext().bufferAllocator().newBuffer(capacity);
        writeRequestArraySize(buffer, len);
        RedisProtocolSupport.Command.ZREVRANGEBYSCORE.encodeTo(buffer);
        writeRequestArgument(buffer, key);
        writeRequestArgument(buffer, max);
        writeRequestArgument(buffer, min);
        final RedisRequest request = newRequest(RedisProtocolSupport.Command.ZREVRANGEBYSCORE, buffer);
        final RedisPartitionAttributesBuilder partitionAttributesBuilder = partitionAttributesBuilderFunction
                    .apply(RedisProtocolSupport.Command.ZREVRANGEBYSCORE);
        partitionAttributesBuilder.addKey(key);
        final Single<List<T>> result = (Single) partitionedRedisClient.request(partitionAttributesBuilder.build(),
                    request, List.class);
        return result;
    }

    @Override
    public <T> Single<List<T>> zrevrangebyscore(@RedisProtocolSupport.Key final Buffer key, final double max,
                                                final double min,
                                                @Nullable final RedisProtocolSupport.ZrevrangebyscoreWithscores withscores,
                                                @Nullable final RedisProtocolSupport.OffsetCount offsetCount) {
        requireNonNull(key);
        final int len = 4 + (withscores == null ? 0 : 1) +
                    (offsetCount == null ? 0 : RedisProtocolSupport.OffsetCount.SIZE);
        final int capacity = calculateInitialCommandBufferSize(len, RedisProtocolSupport.Command.ZREVRANGEBYSCORE) +
                    calculateRequestArgumentSize(key) + calculateRequestArgumentSize(max) +
                    calculateRequestArgumentSize(min) + (withscores == null ? 0 : withscores.encodedByteCount()) +
                    (offsetCount == null ? 0 : offsetCount.encodedByteCount());
        Buffer buffer = partitionedRedisClient.executionContext().bufferAllocator().newBuffer(capacity);
        writeRequestArraySize(buffer, len);
        RedisProtocolSupport.Command.ZREVRANGEBYSCORE.encodeTo(buffer);
        writeRequestArgument(buffer, key);
        writeRequestArgument(buffer, max);
        writeRequestArgument(buffer, min);
        if (withscores != null) {
            withscores.encodeTo(buffer);
        }
        if (offsetCount != null) {
            offsetCount.encodeTo(buffer);
        }
        final RedisRequest request = newRequest(RedisProtocolSupport.Command.ZREVRANGEBYSCORE, buffer);
        final RedisPartitionAttributesBuilder partitionAttributesBuilder = partitionAttributesBuilderFunction
                    .apply(RedisProtocolSupport.Command.ZREVRANGEBYSCORE);
        partitionAttributesBuilder.addKey(key);
        final Single<List<T>> result = (Single) partitionedRedisClient.request(partitionAttributesBuilder.build(),
                    request, List.class);
        return result;
    }

    @Override
    public Single<Long> zrevrank(@RedisProtocolSupport.Key final Buffer key, final Buffer member) {
        requireNonNull(key);
        requireNonNull(member);
        final int len = 3;
        final int capacity = calculateInitialCommandBufferSize(len, RedisProtocolSupport.Command.ZREVRANK) +
                    calculateRequestArgumentSize(key) + calculateRequestArgumentSize(member);
        Buffer buffer = partitionedRedisClient.executionContext().bufferAllocator().newBuffer(capacity);
        writeRequestArraySize(buffer, len);
        RedisProtocolSupport.Command.ZREVRANK.encodeTo(buffer);
        writeRequestArgument(buffer, key);
        writeRequestArgument(buffer, member);
        final RedisRequest request = newRequest(RedisProtocolSupport.Command.ZREVRANK, buffer);
        final RedisPartitionAttributesBuilder partitionAttributesBuilder = partitionAttributesBuilderFunction
                    .apply(RedisProtocolSupport.Command.ZREVRANK);
        partitionAttributesBuilder.addKey(key);
        final Single<Long> result = partitionedRedisClient.request(partitionAttributesBuilder.build(), request,
                    Long.class);
        return result;
    }

    @Override
    public <T> Single<List<T>> zscan(@RedisProtocolSupport.Key final Buffer key, final long cursor) {
        requireNonNull(key);
        final int len = 3;
        final int capacity = calculateInitialCommandBufferSize(len, RedisProtocolSupport.Command.ZSCAN) +
                    calculateRequestArgumentSize(key) + calculateRequestArgumentSize(cursor);
        Buffer buffer = partitionedRedisClient.executionContext().bufferAllocator().newBuffer(capacity);
        writeRequestArraySize(buffer, len);
        RedisProtocolSupport.Command.ZSCAN.encodeTo(buffer);
        writeRequestArgument(buffer, key);
        writeRequestArgument(buffer, cursor);
        final RedisRequest request = newRequest(RedisProtocolSupport.Command.ZSCAN, buffer);
        final RedisPartitionAttributesBuilder partitionAttributesBuilder = partitionAttributesBuilderFunction
                    .apply(RedisProtocolSupport.Command.ZSCAN);
        partitionAttributesBuilder.addKey(key);
        final Single<List<T>> result = (Single) partitionedRedisClient.request(partitionAttributesBuilder.build(),
                    request, List.class);
        return result;
    }

    @Override
    public <T> Single<List<T>> zscan(@RedisProtocolSupport.Key final Buffer key, final long cursor,
                                     @Nullable final Buffer matchPattern, @Nullable final Long count) {
        requireNonNull(key);
        final int len = 3 + (matchPattern == null ? 0 : 2) + (count == null ? 0 : 2);
        final int capacity = calculateInitialCommandBufferSize(len, RedisProtocolSupport.Command.ZSCAN) +
                    calculateRequestArgumentSize(key) + calculateRequestArgumentSize(cursor) +
                    (matchPattern == null ? 0 : RedisProtocolSupport.SubCommand.MATCH.encodedByteCount()) +
                    (matchPattern == null ? 0 : calculateRequestArgumentSize(matchPattern)) +
                    (count == null ? 0 : RedisProtocolSupport.SubCommand.COUNT.encodedByteCount()) +
                    (count == null ? 0 : calculateRequestArgumentSize(count));
        Buffer buffer = partitionedRedisClient.executionContext().bufferAllocator().newBuffer(capacity);
        writeRequestArraySize(buffer, len);
        RedisProtocolSupport.Command.ZSCAN.encodeTo(buffer);
        writeRequestArgument(buffer, key);
        writeRequestArgument(buffer, cursor);
        if (matchPattern != null) {
            RedisProtocolSupport.SubCommand.MATCH.encodeTo(buffer);
            writeRequestArgument(buffer, matchPattern);
        }
        if (count != null) {
            RedisProtocolSupport.SubCommand.COUNT.encodeTo(buffer);
            writeRequestArgument(buffer, count);
        }
        final RedisRequest request = newRequest(RedisProtocolSupport.Command.ZSCAN, buffer);
        final RedisPartitionAttributesBuilder partitionAttributesBuilder = partitionAttributesBuilderFunction
                    .apply(RedisProtocolSupport.Command.ZSCAN);
        partitionAttributesBuilder.addKey(key);
        final Single<List<T>> result = (Single) partitionedRedisClient.request(partitionAttributesBuilder.build(),
                    request, List.class);
        return result;
    }

    @Override
    public Single<Double> zscore(@RedisProtocolSupport.Key final Buffer key, final Buffer member) {
        requireNonNull(key);
        requireNonNull(member);
        final int len = 3;
        final int capacity = calculateInitialCommandBufferSize(len, RedisProtocolSupport.Command.ZSCORE) +
                    calculateRequestArgumentSize(key) + calculateRequestArgumentSize(member);
        Buffer buffer = partitionedRedisClient.executionContext().bufferAllocator().newBuffer(capacity);
        writeRequestArraySize(buffer, len);
        RedisProtocolSupport.Command.ZSCORE.encodeTo(buffer);
        writeRequestArgument(buffer, key);
        writeRequestArgument(buffer, member);
        final RedisRequest request = newRequest(RedisProtocolSupport.Command.ZSCORE, buffer);
        final RedisPartitionAttributesBuilder partitionAttributesBuilder = partitionAttributesBuilderFunction
                    .apply(RedisProtocolSupport.Command.ZSCORE);
        partitionAttributesBuilder.addKey(key);
        final Single<Double> result = partitionedRedisClient
                    .request(partitionAttributesBuilder.build(), request, String.class)
                    .map(s -> s != null ? Double.valueOf(s) : null);
        return result;
    }

    @Override
    public Single<Long> zunionstore(@RedisProtocolSupport.Key final Buffer destination, final long numkeys,
                                    @RedisProtocolSupport.Key final Collection<Buffer> keys) {
        requireNonNull(destination);
        requireNonNull(keys);
        final int len = 3 + keys.size();
        int keysCapacity = 0;
        if (keys instanceof List && keys instanceof RandomAccess) {
            final List<Buffer> list = (List<Buffer>) keys;
            for (int i = 0; i < list.size(); ++i) {
                final Buffer arg = list.get(i);
                keysCapacity += calculateRequestArgumentSize(arg);
            }
        } else {
            for (Buffer arg : keys) {
                keysCapacity += calculateRequestArgumentSize(arg);
            }
        }
        final int capacity = calculateInitialCommandBufferSize(len, RedisProtocolSupport.Command.ZUNIONSTORE) +
                    calculateRequestArgumentSize(destination) + calculateRequestArgumentSize(numkeys) + keysCapacity;
        Buffer buffer = partitionedRedisClient.executionContext().bufferAllocator().newBuffer(capacity);
        writeRequestArraySize(buffer, len);
        RedisProtocolSupport.Command.ZUNIONSTORE.encodeTo(buffer);
        writeRequestArgument(buffer, destination);
        writeRequestArgument(buffer, numkeys);
        if (keys instanceof List && keys instanceof RandomAccess) {
            final List<Buffer> list = (List<Buffer>) keys;
            for (int i = 0; i < list.size(); ++i) {
                final Buffer arg = list.get(i);
                writeRequestArgument(buffer, arg);
            }
        } else {
            for (Buffer arg : keys) {
                writeRequestArgument(buffer, arg);
            }
        }
        final RedisRequest request = newRequest(RedisProtocolSupport.Command.ZUNIONSTORE, buffer);
        final RedisPartitionAttributesBuilder partitionAttributesBuilder = partitionAttributesBuilderFunction
                    .apply(RedisProtocolSupport.Command.ZUNIONSTORE);
        partitionAttributesBuilder.addKey(destination);
        addBufferKeysToAttributeBuilder(keys, partitionAttributesBuilder);
        final Single<Long> result = partitionedRedisClient.request(partitionAttributesBuilder.build(), request,
                    Long.class);
        return result;
    }

    @Override
    public Single<Long> zunionstore(@RedisProtocolSupport.Key final Buffer destination, final long numkeys,
                                    @RedisProtocolSupport.Key final Collection<Buffer> keys,
                                    final Collection<Long> weights,
                                    @Nullable final RedisProtocolSupport.ZunionstoreAggregate aggregate) {
        requireNonNull(destination);
        requireNonNull(keys);
        requireNonNull(weights);
        final int len = 4 + keys.size() + weights.size() + (aggregate == null ? 0 : 1);
        int keysCapacity = 0;
        if (keys instanceof List && keys instanceof RandomAccess) {
            final List<Buffer> list = (List<Buffer>) keys;
            for (int i = 0; i < list.size(); ++i) {
                final Buffer arg = list.get(i);
                keysCapacity += calculateRequestArgumentSize(arg);
            }
        } else {
            for (Buffer arg : keys) {
                keysCapacity += calculateRequestArgumentSize(arg);
            }
        }
        int weightsCapacity = 0;
        if (weights instanceof List && weights instanceof RandomAccess) {
            final List<Long> list = (List<Long>) weights;
            for (int i = 0; i < list.size(); ++i) {
                final Long arg = list.get(i);
                weightsCapacity += calculateRequestArgumentSize(arg);
            }
        } else {
            for (Long arg : weights) {
                weightsCapacity += calculateRequestArgumentSize(arg);
            }
        }
        final int capacity = calculateInitialCommandBufferSize(len, RedisProtocolSupport.Command.ZUNIONSTORE) +
                    calculateRequestArgumentSize(destination) + calculateRequestArgumentSize(numkeys) + keysCapacity +
                    RedisProtocolSupport.SubCommand.WEIGHTS.encodedByteCount() + weightsCapacity +
                    (aggregate == null ? 0 : aggregate.encodedByteCount());
        Buffer buffer = partitionedRedisClient.executionContext().bufferAllocator().newBuffer(capacity);
        writeRequestArraySize(buffer, len);
        RedisProtocolSupport.Command.ZUNIONSTORE.encodeTo(buffer);
        writeRequestArgument(buffer, destination);
        writeRequestArgument(buffer, numkeys);
        if (keys instanceof List && keys instanceof RandomAccess) {
            final List<Buffer> list = (List<Buffer>) keys;
            for (int i = 0; i < list.size(); ++i) {
                final Buffer arg = list.get(i);
                writeRequestArgument(buffer, arg);
            }
        } else {
            for (Buffer arg : keys) {
                writeRequestArgument(buffer, arg);
            }
        }
        RedisProtocolSupport.SubCommand.WEIGHTS.encodeTo(buffer);
        if (weights instanceof List && weights instanceof RandomAccess) {
            final List<Long> list = (List<Long>) weights;
            for (int i = 0; i < list.size(); ++i) {
                final Long arg = list.get(i);
                writeRequestArgument(buffer, arg);
            }
        } else {
            for (Long arg : weights) {
                writeRequestArgument(buffer, arg);
            }
        }
        if (aggregate != null) {
            aggregate.encodeTo(buffer);
        }
        final RedisRequest request = newRequest(RedisProtocolSupport.Command.ZUNIONSTORE, buffer);
        final RedisPartitionAttributesBuilder partitionAttributesBuilder = partitionAttributesBuilderFunction
                    .apply(RedisProtocolSupport.Command.ZUNIONSTORE);
        partitionAttributesBuilder.addKey(destination);
        addBufferKeysToAttributeBuilder(keys, partitionAttributesBuilder);
        final Single<Long> result = partitionedRedisClient.request(partitionAttributesBuilder.build(), request,
                    Long.class);
        return result;
    }
}

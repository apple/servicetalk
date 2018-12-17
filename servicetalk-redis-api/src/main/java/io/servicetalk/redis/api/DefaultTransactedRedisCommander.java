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
import io.servicetalk.concurrent.api.internal.SingleProcessor;
import io.servicetalk.redis.api.CommanderUtils.DiscardSingle;
import io.servicetalk.redis.api.CommanderUtils.ExecCompletable;
import io.servicetalk.redis.internal.RedisUtils;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.RandomAccess;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicIntegerFieldUpdater;
import javax.annotation.Generated;
import javax.annotation.Nullable;

import static io.servicetalk.redis.api.CommanderUtils.enqueueForExecute;
import static io.servicetalk.redis.api.RedisRequests.calculateInitialCommandBufferSize;
import static io.servicetalk.redis.api.RedisRequests.calculateRequestArgumentSize;
import static io.servicetalk.redis.api.RedisRequests.estimateRequestArgumentSize;
import static io.servicetalk.redis.api.RedisRequests.newRequest;
import static io.servicetalk.redis.api.RedisRequests.writeRequestArgument;
import static io.servicetalk.redis.api.RedisRequests.writeRequestArraySize;
import static io.servicetalk.redis.api.StringByteSizeUtil.numberOfBytesUtf8;
import static java.util.Objects.requireNonNull;

@Generated({})
@SuppressWarnings("unchecked")
final class DefaultTransactedRedisCommander extends TransactedRedisCommander {

    private static final AtomicIntegerFieldUpdater<DefaultTransactedRedisCommander> stateUpdater = AtomicIntegerFieldUpdater
                .newUpdater(DefaultTransactedRedisCommander.class, "state");

    private final RedisClient.ReservedRedisConnection reservedCnx;

    private final boolean releaseAfterDone;

    @SuppressWarnings("unused")
    private volatile int state;

    private final List<SingleProcessor<?>> singles = new ArrayList<>();

    DefaultTransactedRedisCommander(final RedisClient.ReservedRedisConnection reservedCnx,
                final boolean releaseAfterDone) {
        this.reservedCnx = requireNonNull(reservedCnx);
        this.releaseAfterDone = releaseAfterDone;
    }

    @Override
    public Completable closeAsync() {
        return reservedCnx.closeAsync();
    }

    @Override
    public Completable closeAsyncGracefully() {
        return reservedCnx.closeAsyncGracefully();
    }

    @Override
    public Future<Long> append(@RedisProtocolSupport.Key final CharSequence key, final CharSequence value) {
        requireNonNull(key);
        requireNonNull(value);
        final int len = 3;
        final int keyBytes = numberOfBytesUtf8(key);
        final int valueBytes = numberOfBytesUtf8(value);
        final int capacity = calculateInitialCommandBufferSize(len, RedisProtocolSupport.Command.APPEND) +
                    calculateRequestArgumentSize(keyBytes) + calculateRequestArgumentSize(valueBytes);
        Buffer buffer = reservedCnx.executionContext().bufferAllocator().newBuffer(capacity);
        writeRequestArraySize(buffer, len);
        RedisProtocolSupport.Command.APPEND.encodeTo(buffer);
        writeRequestArgument(buffer, key, keyBytes);
        writeRequestArgument(buffer, value, valueBytes);
        final RedisRequest request = newRequest(RedisProtocolSupport.Command.APPEND, buffer);
        final Single<String> queued = reservedCnx.request(request, String.class);
        Future<Long> result = enqueueForExecute(state, singles, queued);
        return result;
    }

    @Override
    public Future<String> auth(final CharSequence password) {
        requireNonNull(password);
        final int len = 2;
        final int passwordBytes = numberOfBytesUtf8(password);
        final int capacity = calculateInitialCommandBufferSize(len, RedisProtocolSupport.Command.AUTH) +
                    calculateRequestArgumentSize(passwordBytes);
        Buffer buffer = reservedCnx.executionContext().bufferAllocator().newBuffer(capacity);
        writeRequestArraySize(buffer, len);
        RedisProtocolSupport.Command.AUTH.encodeTo(buffer);
        writeRequestArgument(buffer, password, passwordBytes);
        final RedisRequest request = newRequest(RedisProtocolSupport.Command.AUTH, buffer);
        final Single<String> queued = reservedCnx.request(request, String.class);
        Future<String> result = enqueueForExecute(state, singles, queued);
        return result;
    }

    @Override
    public Future<String> bgrewriteaof() {
        final int len = 1;
        final int capacity = calculateInitialCommandBufferSize(len, RedisProtocolSupport.Command.BGREWRITEAOF);
        Buffer buffer = reservedCnx.executionContext().bufferAllocator().newBuffer(capacity);
        writeRequestArraySize(buffer, len);
        RedisProtocolSupport.Command.BGREWRITEAOF.encodeTo(buffer);
        final RedisRequest request = newRequest(RedisProtocolSupport.Command.BGREWRITEAOF, buffer);
        final Single<String> queued = reservedCnx.request(request, String.class);
        Future<String> result = enqueueForExecute(state, singles, queued);
        return result;
    }

    @Override
    public Future<String> bgsave() {
        final int len = 1;
        final int capacity = calculateInitialCommandBufferSize(len, RedisProtocolSupport.Command.BGSAVE);
        Buffer buffer = reservedCnx.executionContext().bufferAllocator().newBuffer(capacity);
        writeRequestArraySize(buffer, len);
        RedisProtocolSupport.Command.BGSAVE.encodeTo(buffer);
        final RedisRequest request = newRequest(RedisProtocolSupport.Command.BGSAVE, buffer);
        final Single<String> queued = reservedCnx.request(request, String.class);
        Future<String> result = enqueueForExecute(state, singles, queued);
        return result;
    }

    @Override
    public Future<Long> bitcount(@RedisProtocolSupport.Key final CharSequence key) {
        requireNonNull(key);
        final int len = 2;
        final int keyBytes = numberOfBytesUtf8(key);
        final int capacity = calculateInitialCommandBufferSize(len, RedisProtocolSupport.Command.BITCOUNT) +
                    calculateRequestArgumentSize(keyBytes);
        Buffer buffer = reservedCnx.executionContext().bufferAllocator().newBuffer(capacity);
        writeRequestArraySize(buffer, len);
        RedisProtocolSupport.Command.BITCOUNT.encodeTo(buffer);
        writeRequestArgument(buffer, key, keyBytes);
        final RedisRequest request = newRequest(RedisProtocolSupport.Command.BITCOUNT, buffer);
        final Single<String> queued = reservedCnx.request(request, String.class);
        Future<Long> result = enqueueForExecute(state, singles, queued);
        return result;
    }

    @Override
    public Future<Long> bitcount(@RedisProtocolSupport.Key final CharSequence key, @Nullable final Long start,
                                 @Nullable final Long end) {
        requireNonNull(key);
        final int len = 2 + (start == null ? 0 : 1) + (end == null ? 0 : 1);
        final int keyBytes = numberOfBytesUtf8(key);
        final int capacity = calculateInitialCommandBufferSize(len, RedisProtocolSupport.Command.BITCOUNT) +
                    calculateRequestArgumentSize(keyBytes) + (start == null ? 0 : calculateRequestArgumentSize(start)) +
                    (end == null ? 0 : calculateRequestArgumentSize(end));
        Buffer buffer = reservedCnx.executionContext().bufferAllocator().newBuffer(capacity);
        writeRequestArraySize(buffer, len);
        RedisProtocolSupport.Command.BITCOUNT.encodeTo(buffer);
        writeRequestArgument(buffer, key, keyBytes);
        if (start != null) {
            writeRequestArgument(buffer, start);
        }
        if (end != null) {
            writeRequestArgument(buffer, end);
        }
        final RedisRequest request = newRequest(RedisProtocolSupport.Command.BITCOUNT, buffer);
        final Single<String> queued = reservedCnx.request(request, String.class);
        Future<Long> result = enqueueForExecute(state, singles, queued);
        return result;
    }

    @Override
    public Future<List<Long>> bitfield(@RedisProtocolSupport.Key final CharSequence key,
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
        final int keyBytes = numberOfBytesUtf8(key);
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
                    calculateRequestArgumentSize(keyBytes) + operationsCapacity;
        Buffer buffer = reservedCnx.executionContext().bufferAllocator().newBuffer(capacity);
        writeRequestArraySize(buffer, len);
        RedisProtocolSupport.Command.BITFIELD.encodeTo(buffer);
        writeRequestArgument(buffer, key, keyBytes);
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
        final Single<String> queued = reservedCnx.request(request, String.class);
        Future<List<Long>> result = enqueueForExecute(state, singles, queued);
        return result;
    }

    @Override
    public Future<Long> bitop(final CharSequence operation, @RedisProtocolSupport.Key final CharSequence destkey,
                              @RedisProtocolSupport.Key final CharSequence key) {
        requireNonNull(operation);
        requireNonNull(destkey);
        requireNonNull(key);
        final int len = 4;
        final int operationBytes = numberOfBytesUtf8(operation);
        final int destkeyBytes = numberOfBytesUtf8(destkey);
        final int keyBytes = numberOfBytesUtf8(key);
        final int capacity = calculateInitialCommandBufferSize(len, RedisProtocolSupport.Command.BITOP) +
                    calculateRequestArgumentSize(operationBytes) + calculateRequestArgumentSize(destkeyBytes) +
                    calculateRequestArgumentSize(keyBytes);
        Buffer buffer = reservedCnx.executionContext().bufferAllocator().newBuffer(capacity);
        writeRequestArraySize(buffer, len);
        RedisProtocolSupport.Command.BITOP.encodeTo(buffer);
        writeRequestArgument(buffer, operation, operationBytes);
        writeRequestArgument(buffer, destkey, destkeyBytes);
        writeRequestArgument(buffer, key, keyBytes);
        final RedisRequest request = newRequest(RedisProtocolSupport.Command.BITOP, buffer);
        final Single<String> queued = reservedCnx.request(request, String.class);
        Future<Long> result = enqueueForExecute(state, singles, queued);
        return result;
    }

    @Override
    public Future<Long> bitop(final CharSequence operation, @RedisProtocolSupport.Key final CharSequence destkey,
                              @RedisProtocolSupport.Key final CharSequence key1,
                              @RedisProtocolSupport.Key final CharSequence key2) {
        requireNonNull(operation);
        requireNonNull(destkey);
        requireNonNull(key1);
        requireNonNull(key2);
        final int len = 5;
        final int operationBytes = numberOfBytesUtf8(operation);
        final int destkeyBytes = numberOfBytesUtf8(destkey);
        final int key1Bytes = numberOfBytesUtf8(key1);
        final int key2Bytes = numberOfBytesUtf8(key2);
        final int capacity = calculateInitialCommandBufferSize(len, RedisProtocolSupport.Command.BITOP) +
                    calculateRequestArgumentSize(operationBytes) + calculateRequestArgumentSize(destkeyBytes) +
                    calculateRequestArgumentSize(key1Bytes) + calculateRequestArgumentSize(key2Bytes);
        Buffer buffer = reservedCnx.executionContext().bufferAllocator().newBuffer(capacity);
        writeRequestArraySize(buffer, len);
        RedisProtocolSupport.Command.BITOP.encodeTo(buffer);
        writeRequestArgument(buffer, operation, operationBytes);
        writeRequestArgument(buffer, destkey, destkeyBytes);
        writeRequestArgument(buffer, key1, key1Bytes);
        writeRequestArgument(buffer, key2, key2Bytes);
        final RedisRequest request = newRequest(RedisProtocolSupport.Command.BITOP, buffer);
        final Single<String> queued = reservedCnx.request(request, String.class);
        Future<Long> result = enqueueForExecute(state, singles, queued);
        return result;
    }

    @Override
    public Future<Long> bitop(final CharSequence operation, @RedisProtocolSupport.Key final CharSequence destkey,
                              @RedisProtocolSupport.Key final CharSequence key1,
                              @RedisProtocolSupport.Key final CharSequence key2,
                              @RedisProtocolSupport.Key final CharSequence key3) {
        requireNonNull(operation);
        requireNonNull(destkey);
        requireNonNull(key1);
        requireNonNull(key2);
        requireNonNull(key3);
        final int len = 6;
        final int operationBytes = numberOfBytesUtf8(operation);
        final int destkeyBytes = numberOfBytesUtf8(destkey);
        final int key1Bytes = numberOfBytesUtf8(key1);
        final int key2Bytes = numberOfBytesUtf8(key2);
        final int key3Bytes = numberOfBytesUtf8(key3);
        final int capacity = calculateInitialCommandBufferSize(len, RedisProtocolSupport.Command.BITOP) +
                    calculateRequestArgumentSize(operationBytes) + calculateRequestArgumentSize(destkeyBytes) +
                    calculateRequestArgumentSize(key1Bytes) + calculateRequestArgumentSize(key2Bytes) +
                    calculateRequestArgumentSize(key3Bytes);
        Buffer buffer = reservedCnx.executionContext().bufferAllocator().newBuffer(capacity);
        writeRequestArraySize(buffer, len);
        RedisProtocolSupport.Command.BITOP.encodeTo(buffer);
        writeRequestArgument(buffer, operation, operationBytes);
        writeRequestArgument(buffer, destkey, destkeyBytes);
        writeRequestArgument(buffer, key1, key1Bytes);
        writeRequestArgument(buffer, key2, key2Bytes);
        writeRequestArgument(buffer, key3, key3Bytes);
        final RedisRequest request = newRequest(RedisProtocolSupport.Command.BITOP, buffer);
        final Single<String> queued = reservedCnx.request(request, String.class);
        Future<Long> result = enqueueForExecute(state, singles, queued);
        return result;
    }

    @Override
    public Future<Long> bitop(final CharSequence operation, @RedisProtocolSupport.Key final CharSequence destkey,
                              @RedisProtocolSupport.Key final Collection<? extends CharSequence> keys) {
        requireNonNull(operation);
        requireNonNull(destkey);
        requireNonNull(keys);
        final int len = 3 + keys.size();
        final int operationBytes = numberOfBytesUtf8(operation);
        final int destkeyBytes = numberOfBytesUtf8(destkey);
        int keysCapacity = 0;
        if (keys instanceof List && keys instanceof RandomAccess) {
            final List<CharSequence> list = (List<CharSequence>) keys;
            for (int i = 0; i < list.size(); ++i) {
                final CharSequence arg = list.get(i);
                keysCapacity += estimateRequestArgumentSize(arg);
            }
        } else {
            for (CharSequence arg : keys) {
                keysCapacity += estimateRequestArgumentSize(arg);
            }
        }
        final int capacity = calculateInitialCommandBufferSize(len, RedisProtocolSupport.Command.BITOP) +
                    calculateRequestArgumentSize(operationBytes) + calculateRequestArgumentSize(destkeyBytes) +
                    keysCapacity;
        Buffer buffer = reservedCnx.executionContext().bufferAllocator().newBuffer(capacity);
        writeRequestArraySize(buffer, len);
        RedisProtocolSupport.Command.BITOP.encodeTo(buffer);
        writeRequestArgument(buffer, operation, operationBytes);
        writeRequestArgument(buffer, destkey, destkeyBytes);
        if (keys instanceof List && keys instanceof RandomAccess) {
            final List<CharSequence> list = (List<CharSequence>) keys;
            for (int i = 0; i < list.size(); ++i) {
                final CharSequence arg = list.get(i);
                writeRequestArgument(buffer, arg);
            }
        } else {
            for (CharSequence arg : keys) {
                writeRequestArgument(buffer, arg);
            }
        }
        final RedisRequest request = newRequest(RedisProtocolSupport.Command.BITOP, buffer);
        final Single<String> queued = reservedCnx.request(request, String.class);
        Future<Long> result = enqueueForExecute(state, singles, queued);
        return result;
    }

    @Override
    public Future<Long> bitpos(@RedisProtocolSupport.Key final CharSequence key, final long bit) {
        requireNonNull(key);
        final int len = 3;
        final int keyBytes = numberOfBytesUtf8(key);
        final int capacity = calculateInitialCommandBufferSize(len, RedisProtocolSupport.Command.BITPOS) +
                    calculateRequestArgumentSize(keyBytes) + calculateRequestArgumentSize(bit);
        Buffer buffer = reservedCnx.executionContext().bufferAllocator().newBuffer(capacity);
        writeRequestArraySize(buffer, len);
        RedisProtocolSupport.Command.BITPOS.encodeTo(buffer);
        writeRequestArgument(buffer, key, keyBytes);
        writeRequestArgument(buffer, bit);
        final RedisRequest request = newRequest(RedisProtocolSupport.Command.BITPOS, buffer);
        final Single<String> queued = reservedCnx.request(request, String.class);
        Future<Long> result = enqueueForExecute(state, singles, queued);
        return result;
    }

    @Override
    public Future<Long> bitpos(@RedisProtocolSupport.Key final CharSequence key, final long bit,
                               @Nullable final Long start, @Nullable final Long end) {
        requireNonNull(key);
        final int len = 3 + (start == null ? 0 : 1) + (end == null ? 0 : 1);
        final int keyBytes = numberOfBytesUtf8(key);
        final int capacity = calculateInitialCommandBufferSize(len, RedisProtocolSupport.Command.BITPOS) +
                    calculateRequestArgumentSize(keyBytes) + calculateRequestArgumentSize(bit) +
                    (start == null ? 0 : calculateRequestArgumentSize(start)) +
                    (end == null ? 0 : calculateRequestArgumentSize(end));
        Buffer buffer = reservedCnx.executionContext().bufferAllocator().newBuffer(capacity);
        writeRequestArraySize(buffer, len);
        RedisProtocolSupport.Command.BITPOS.encodeTo(buffer);
        writeRequestArgument(buffer, key, keyBytes);
        writeRequestArgument(buffer, bit);
        if (start != null) {
            writeRequestArgument(buffer, start);
        }
        if (end != null) {
            writeRequestArgument(buffer, end);
        }
        final RedisRequest request = newRequest(RedisProtocolSupport.Command.BITPOS, buffer);
        final Single<String> queued = reservedCnx.request(request, String.class);
        Future<Long> result = enqueueForExecute(state, singles, queued);
        return result;
    }

    @Override
    public <T> Future<List<T>> blpop(@RedisProtocolSupport.Key final Collection<? extends CharSequence> keys,
                                     final long timeout) {
        requireNonNull(keys);
        final int len = 2 + keys.size();
        int keysCapacity = 0;
        if (keys instanceof List && keys instanceof RandomAccess) {
            final List<CharSequence> list = (List<CharSequence>) keys;
            for (int i = 0; i < list.size(); ++i) {
                final CharSequence arg = list.get(i);
                keysCapacity += estimateRequestArgumentSize(arg);
            }
        } else {
            for (CharSequence arg : keys) {
                keysCapacity += estimateRequestArgumentSize(arg);
            }
        }
        final int capacity = calculateInitialCommandBufferSize(len, RedisProtocolSupport.Command.BLPOP) + keysCapacity +
                    calculateRequestArgumentSize(timeout);
        Buffer buffer = reservedCnx.executionContext().bufferAllocator().newBuffer(capacity);
        writeRequestArraySize(buffer, len);
        RedisProtocolSupport.Command.BLPOP.encodeTo(buffer);
        if (keys instanceof List && keys instanceof RandomAccess) {
            final List<CharSequence> list = (List<CharSequence>) keys;
            for (int i = 0; i < list.size(); ++i) {
                final CharSequence arg = list.get(i);
                writeRequestArgument(buffer, arg);
            }
        } else {
            for (CharSequence arg : keys) {
                writeRequestArgument(buffer, arg);
            }
        }
        writeRequestArgument(buffer, timeout);
        final RedisRequest request = newRequest(RedisProtocolSupport.Command.BLPOP, buffer);
        final Single<String> queued = reservedCnx.request(request, String.class);
        Future<List<T>> result = enqueueForExecute(state, singles, queued);
        return result;
    }

    @Override
    public <T> Future<List<T>> brpop(@RedisProtocolSupport.Key final Collection<? extends CharSequence> keys,
                                     final long timeout) {
        requireNonNull(keys);
        final int len = 2 + keys.size();
        int keysCapacity = 0;
        if (keys instanceof List && keys instanceof RandomAccess) {
            final List<CharSequence> list = (List<CharSequence>) keys;
            for (int i = 0; i < list.size(); ++i) {
                final CharSequence arg = list.get(i);
                keysCapacity += estimateRequestArgumentSize(arg);
            }
        } else {
            for (CharSequence arg : keys) {
                keysCapacity += estimateRequestArgumentSize(arg);
            }
        }
        final int capacity = calculateInitialCommandBufferSize(len, RedisProtocolSupport.Command.BRPOP) + keysCapacity +
                    calculateRequestArgumentSize(timeout);
        Buffer buffer = reservedCnx.executionContext().bufferAllocator().newBuffer(capacity);
        writeRequestArraySize(buffer, len);
        RedisProtocolSupport.Command.BRPOP.encodeTo(buffer);
        if (keys instanceof List && keys instanceof RandomAccess) {
            final List<CharSequence> list = (List<CharSequence>) keys;
            for (int i = 0; i < list.size(); ++i) {
                final CharSequence arg = list.get(i);
                writeRequestArgument(buffer, arg);
            }
        } else {
            for (CharSequence arg : keys) {
                writeRequestArgument(buffer, arg);
            }
        }
        writeRequestArgument(buffer, timeout);
        final RedisRequest request = newRequest(RedisProtocolSupport.Command.BRPOP, buffer);
        final Single<String> queued = reservedCnx.request(request, String.class);
        Future<List<T>> result = enqueueForExecute(state, singles, queued);
        return result;
    }

    @Override
    public Future<String> brpoplpush(@RedisProtocolSupport.Key final CharSequence source,
                                     @RedisProtocolSupport.Key final CharSequence destination, final long timeout) {
        requireNonNull(source);
        requireNonNull(destination);
        final int len = 4;
        final int sourceBytes = numberOfBytesUtf8(source);
        final int destinationBytes = numberOfBytesUtf8(destination);
        final int capacity = calculateInitialCommandBufferSize(len, RedisProtocolSupport.Command.BRPOPLPUSH) +
                    calculateRequestArgumentSize(sourceBytes) + calculateRequestArgumentSize(destinationBytes) +
                    calculateRequestArgumentSize(timeout);
        Buffer buffer = reservedCnx.executionContext().bufferAllocator().newBuffer(capacity);
        writeRequestArraySize(buffer, len);
        RedisProtocolSupport.Command.BRPOPLPUSH.encodeTo(buffer);
        writeRequestArgument(buffer, source, sourceBytes);
        writeRequestArgument(buffer, destination, destinationBytes);
        writeRequestArgument(buffer, timeout);
        final RedisRequest request = newRequest(RedisProtocolSupport.Command.BRPOPLPUSH, buffer);
        final Single<String> queued = reservedCnx.request(request, String.class);
        Future<String> result = enqueueForExecute(state, singles, queued);
        return result;
    }

    @Override
    public <T> Future<List<T>> bzpopmax(@RedisProtocolSupport.Key final Collection<? extends CharSequence> keys,
                                        final long timeout) {
        requireNonNull(keys);
        final int len = 2 + keys.size();
        int keysCapacity = 0;
        if (keys instanceof List && keys instanceof RandomAccess) {
            final List<CharSequence> list = (List<CharSequence>) keys;
            for (int i = 0; i < list.size(); ++i) {
                final CharSequence arg = list.get(i);
                keysCapacity += estimateRequestArgumentSize(arg);
            }
        } else {
            for (CharSequence arg : keys) {
                keysCapacity += estimateRequestArgumentSize(arg);
            }
        }
        final int capacity = calculateInitialCommandBufferSize(len, RedisProtocolSupport.Command.BZPOPMAX) +
                    keysCapacity + calculateRequestArgumentSize(timeout);
        Buffer buffer = reservedCnx.executionContext().bufferAllocator().newBuffer(capacity);
        writeRequestArraySize(buffer, len);
        RedisProtocolSupport.Command.BZPOPMAX.encodeTo(buffer);
        if (keys instanceof List && keys instanceof RandomAccess) {
            final List<CharSequence> list = (List<CharSequence>) keys;
            for (int i = 0; i < list.size(); ++i) {
                final CharSequence arg = list.get(i);
                writeRequestArgument(buffer, arg);
            }
        } else {
            for (CharSequence arg : keys) {
                writeRequestArgument(buffer, arg);
            }
        }
        writeRequestArgument(buffer, timeout);
        final RedisRequest request = newRequest(RedisProtocolSupport.Command.BZPOPMAX, buffer);
        final Single<String> queued = reservedCnx.request(request, String.class);
        Future<List<T>> result = enqueueForExecute(state, singles, queued);
        return result;
    }

    @Override
    public <T> Future<List<T>> bzpopmin(@RedisProtocolSupport.Key final Collection<? extends CharSequence> keys,
                                        final long timeout) {
        requireNonNull(keys);
        final int len = 2 + keys.size();
        int keysCapacity = 0;
        if (keys instanceof List && keys instanceof RandomAccess) {
            final List<CharSequence> list = (List<CharSequence>) keys;
            for (int i = 0; i < list.size(); ++i) {
                final CharSequence arg = list.get(i);
                keysCapacity += estimateRequestArgumentSize(arg);
            }
        } else {
            for (CharSequence arg : keys) {
                keysCapacity += estimateRequestArgumentSize(arg);
            }
        }
        final int capacity = calculateInitialCommandBufferSize(len, RedisProtocolSupport.Command.BZPOPMIN) +
                    keysCapacity + calculateRequestArgumentSize(timeout);
        Buffer buffer = reservedCnx.executionContext().bufferAllocator().newBuffer(capacity);
        writeRequestArraySize(buffer, len);
        RedisProtocolSupport.Command.BZPOPMIN.encodeTo(buffer);
        if (keys instanceof List && keys instanceof RandomAccess) {
            final List<CharSequence> list = (List<CharSequence>) keys;
            for (int i = 0; i < list.size(); ++i) {
                final CharSequence arg = list.get(i);
                writeRequestArgument(buffer, arg);
            }
        } else {
            for (CharSequence arg : keys) {
                writeRequestArgument(buffer, arg);
            }
        }
        writeRequestArgument(buffer, timeout);
        final RedisRequest request = newRequest(RedisProtocolSupport.Command.BZPOPMIN, buffer);
        final Single<String> queued = reservedCnx.request(request, String.class);
        Future<List<T>> result = enqueueForExecute(state, singles, queued);
        return result;
    }

    @Override
    public Future<Long> clientKill(@Nullable final Long id, @Nullable final RedisProtocolSupport.ClientKillType type,
                                   @Nullable final CharSequence addrIpPort, @Nullable final CharSequence skipmeYesNo) {
        final int len = 2 + (id == null ? 0 : 2) + (type == null ? 0 : 1) + (addrIpPort == null ? 0 : 2) +
                    (skipmeYesNo == null ? 0 : 2);
        final int addrIpPortBytes = addrIpPort == null ? 0 : numberOfBytesUtf8(addrIpPort);
        final int skipmeYesNoBytes = skipmeYesNo == null ? 0 : numberOfBytesUtf8(skipmeYesNo);
        final int capacity = 1 + calculateInitialCommandBufferSize(len, RedisProtocolSupport.Command.CLIENT) +
                    (id == null ? 0 : RedisProtocolSupport.SubCommand.ID.encodedByteCount()) +
                    (id == null ? 0 : calculateRequestArgumentSize(id)) + (type == null ? 0 : type.encodedByteCount()) +
                    (addrIpPort == null ? 0 : RedisProtocolSupport.SubCommand.ADDR.encodedByteCount()) +
                    (addrIpPort == null ? 0 : calculateRequestArgumentSize(addrIpPortBytes)) +
                    (skipmeYesNo == null ? 0 : RedisProtocolSupport.SubCommand.SKIPME.encodedByteCount()) +
                    (skipmeYesNo == null ? 0 : calculateRequestArgumentSize(skipmeYesNoBytes));
        Buffer buffer = reservedCnx.executionContext().bufferAllocator().newBuffer(capacity);
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
            writeRequestArgument(buffer, addrIpPort, addrIpPortBytes);
        }
        if (skipmeYesNo != null) {
            RedisProtocolSupport.SubCommand.SKIPME.encodeTo(buffer);
            writeRequestArgument(buffer, skipmeYesNo, skipmeYesNoBytes);
        }
        final RedisRequest request = newRequest(RedisProtocolSupport.Command.CLIENT, buffer);
        final Single<String> queued = reservedCnx.request(request, String.class);
        Future<Long> result = enqueueForExecute(state, singles, queued);
        return result;
    }

    @Override
    public Future<String> clientList() {
        final int len = 2;
        final int capacity = 1 + calculateInitialCommandBufferSize(len, RedisProtocolSupport.Command.CLIENT);
        Buffer buffer = reservedCnx.executionContext().bufferAllocator().newBuffer(capacity);
        writeRequestArraySize(buffer, len);
        RedisProtocolSupport.Command.CLIENT.encodeTo(buffer);
        RedisProtocolSupport.SubCommand.LIST.encodeTo(buffer);
        final RedisRequest request = newRequest(RedisProtocolSupport.Command.CLIENT, buffer);
        final Single<String> queued = reservedCnx.request(request, String.class);
        Future<String> result = enqueueForExecute(state, singles, queued);
        return result;
    }

    @Override
    public Future<String> clientGetname() {
        final int len = 2;
        final int capacity = 1 + calculateInitialCommandBufferSize(len, RedisProtocolSupport.Command.CLIENT);
        Buffer buffer = reservedCnx.executionContext().bufferAllocator().newBuffer(capacity);
        writeRequestArraySize(buffer, len);
        RedisProtocolSupport.Command.CLIENT.encodeTo(buffer);
        RedisProtocolSupport.SubCommand.GETNAME.encodeTo(buffer);
        final RedisRequest request = newRequest(RedisProtocolSupport.Command.CLIENT, buffer);
        final Single<String> queued = reservedCnx.request(request, String.class);
        Future<String> result = enqueueForExecute(state, singles, queued);
        return result;
    }

    @Override
    public Future<String> clientPause(final long timeout) {
        final int len = 3;
        final int capacity = 1 + calculateInitialCommandBufferSize(len, RedisProtocolSupport.Command.CLIENT) +
                    calculateRequestArgumentSize(timeout);
        Buffer buffer = reservedCnx.executionContext().bufferAllocator().newBuffer(capacity);
        writeRequestArraySize(buffer, len);
        RedisProtocolSupport.Command.CLIENT.encodeTo(buffer);
        RedisProtocolSupport.SubCommand.PAUSE.encodeTo(buffer);
        writeRequestArgument(buffer, timeout);
        final RedisRequest request = newRequest(RedisProtocolSupport.Command.CLIENT, buffer);
        final Single<String> queued = reservedCnx.request(request, String.class);
        Future<String> result = enqueueForExecute(state, singles, queued);
        return result;
    }

    @Override
    public Future<String> clientReply(final RedisProtocolSupport.ClientReplyReplyMode replyMode) {
        requireNonNull(replyMode);
        final int len = 3;
        final int capacity = 1 + calculateInitialCommandBufferSize(len, RedisProtocolSupport.Command.CLIENT) +
                    replyMode.encodedByteCount();
        Buffer buffer = reservedCnx.executionContext().bufferAllocator().newBuffer(capacity);
        writeRequestArraySize(buffer, len);
        RedisProtocolSupport.Command.CLIENT.encodeTo(buffer);
        RedisProtocolSupport.SubCommand.REPLY.encodeTo(buffer);
        replyMode.encodeTo(buffer);
        final RedisRequest request = newRequest(RedisProtocolSupport.Command.CLIENT, buffer);
        final Single<String> queued = reservedCnx.request(request, String.class);
        Future<String> result = enqueueForExecute(state, singles, queued);
        return result;
    }

    @Override
    public Future<String> clientSetname(final CharSequence connectionName) {
        requireNonNull(connectionName);
        final int len = 3;
        final int connectionNameBytes = numberOfBytesUtf8(connectionName);
        final int capacity = 1 + calculateInitialCommandBufferSize(len, RedisProtocolSupport.Command.CLIENT) +
                    calculateRequestArgumentSize(connectionNameBytes);
        Buffer buffer = reservedCnx.executionContext().bufferAllocator().newBuffer(capacity);
        writeRequestArraySize(buffer, len);
        RedisProtocolSupport.Command.CLIENT.encodeTo(buffer);
        RedisProtocolSupport.SubCommand.SETNAME.encodeTo(buffer);
        writeRequestArgument(buffer, connectionName, connectionNameBytes);
        final RedisRequest request = newRequest(RedisProtocolSupport.Command.CLIENT, buffer);
        final Single<String> queued = reservedCnx.request(request, String.class);
        Future<String> result = enqueueForExecute(state, singles, queued);
        return result;
    }

    @Override
    public Future<String> clusterAddslots(final long slot) {
        final int len = 3;
        final int capacity = 1 + calculateInitialCommandBufferSize(len, RedisProtocolSupport.Command.CLUSTER) +
                    calculateRequestArgumentSize(slot);
        Buffer buffer = reservedCnx.executionContext().bufferAllocator().newBuffer(capacity);
        writeRequestArraySize(buffer, len);
        RedisProtocolSupport.Command.CLUSTER.encodeTo(buffer);
        RedisProtocolSupport.SubCommand.ADDSLOTS.encodeTo(buffer);
        writeRequestArgument(buffer, slot);
        final RedisRequest request = newRequest(RedisProtocolSupport.Command.CLUSTER, buffer);
        final Single<String> queued = reservedCnx.request(request, String.class);
        Future<String> result = enqueueForExecute(state, singles, queued);
        return result;
    }

    @Override
    public Future<String> clusterAddslots(final long slot1, final long slot2) {
        final int len = 4;
        final int capacity = 1 + calculateInitialCommandBufferSize(len, RedisProtocolSupport.Command.CLUSTER) +
                    calculateRequestArgumentSize(slot1) + calculateRequestArgumentSize(slot2);
        Buffer buffer = reservedCnx.executionContext().bufferAllocator().newBuffer(capacity);
        writeRequestArraySize(buffer, len);
        RedisProtocolSupport.Command.CLUSTER.encodeTo(buffer);
        RedisProtocolSupport.SubCommand.ADDSLOTS.encodeTo(buffer);
        writeRequestArgument(buffer, slot1);
        writeRequestArgument(buffer, slot2);
        final RedisRequest request = newRequest(RedisProtocolSupport.Command.CLUSTER, buffer);
        final Single<String> queued = reservedCnx.request(request, String.class);
        Future<String> result = enqueueForExecute(state, singles, queued);
        return result;
    }

    @Override
    public Future<String> clusterAddslots(final long slot1, final long slot2, final long slot3) {
        final int len = 5;
        final int capacity = 1 + calculateInitialCommandBufferSize(len, RedisProtocolSupport.Command.CLUSTER) +
                    calculateRequestArgumentSize(slot1) + calculateRequestArgumentSize(slot2) +
                    calculateRequestArgumentSize(slot3);
        Buffer buffer = reservedCnx.executionContext().bufferAllocator().newBuffer(capacity);
        writeRequestArraySize(buffer, len);
        RedisProtocolSupport.Command.CLUSTER.encodeTo(buffer);
        RedisProtocolSupport.SubCommand.ADDSLOTS.encodeTo(buffer);
        writeRequestArgument(buffer, slot1);
        writeRequestArgument(buffer, slot2);
        writeRequestArgument(buffer, slot3);
        final RedisRequest request = newRequest(RedisProtocolSupport.Command.CLUSTER, buffer);
        final Single<String> queued = reservedCnx.request(request, String.class);
        Future<String> result = enqueueForExecute(state, singles, queued);
        return result;
    }

    @Override
    public Future<String> clusterAddslots(final Collection<Long> slots) {
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
        Buffer buffer = reservedCnx.executionContext().bufferAllocator().newBuffer(capacity);
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
        final Single<String> queued = reservedCnx.request(request, String.class);
        Future<String> result = enqueueForExecute(state, singles, queued);
        return result;
    }

    @Override
    public Future<Long> clusterCountFailureReports(final CharSequence nodeId) {
        requireNonNull(nodeId);
        final int len = 3;
        final int nodeIdBytes = numberOfBytesUtf8(nodeId);
        final int capacity = 1 + calculateInitialCommandBufferSize(len, RedisProtocolSupport.Command.CLUSTER) +
                    calculateRequestArgumentSize(nodeIdBytes);
        Buffer buffer = reservedCnx.executionContext().bufferAllocator().newBuffer(capacity);
        writeRequestArraySize(buffer, len);
        RedisProtocolSupport.Command.CLUSTER.encodeTo(buffer);
        RedisProtocolSupport.SubCommand.COUNT_FAILURE_REPORTS.encodeTo(buffer);
        writeRequestArgument(buffer, nodeId, nodeIdBytes);
        final RedisRequest request = newRequest(RedisProtocolSupport.Command.CLUSTER, buffer);
        final Single<String> queued = reservedCnx.request(request, String.class);
        Future<Long> result = enqueueForExecute(state, singles, queued);
        return result;
    }

    @Override
    public Future<Long> clusterCountkeysinslot(final long slot) {
        final int len = 3;
        final int capacity = 1 + calculateInitialCommandBufferSize(len, RedisProtocolSupport.Command.CLUSTER) +
                    calculateRequestArgumentSize(slot);
        Buffer buffer = reservedCnx.executionContext().bufferAllocator().newBuffer(capacity);
        writeRequestArraySize(buffer, len);
        RedisProtocolSupport.Command.CLUSTER.encodeTo(buffer);
        RedisProtocolSupport.SubCommand.COUNTKEYSINSLOT.encodeTo(buffer);
        writeRequestArgument(buffer, slot);
        final RedisRequest request = newRequest(RedisProtocolSupport.Command.CLUSTER, buffer);
        final Single<String> queued = reservedCnx.request(request, String.class);
        Future<Long> result = enqueueForExecute(state, singles, queued);
        return result;
    }

    @Override
    public Future<String> clusterDelslots(final long slot) {
        final int len = 3;
        final int capacity = 1 + calculateInitialCommandBufferSize(len, RedisProtocolSupport.Command.CLUSTER) +
                    calculateRequestArgumentSize(slot);
        Buffer buffer = reservedCnx.executionContext().bufferAllocator().newBuffer(capacity);
        writeRequestArraySize(buffer, len);
        RedisProtocolSupport.Command.CLUSTER.encodeTo(buffer);
        RedisProtocolSupport.SubCommand.DELSLOTS.encodeTo(buffer);
        writeRequestArgument(buffer, slot);
        final RedisRequest request = newRequest(RedisProtocolSupport.Command.CLUSTER, buffer);
        final Single<String> queued = reservedCnx.request(request, String.class);
        Future<String> result = enqueueForExecute(state, singles, queued);
        return result;
    }

    @Override
    public Future<String> clusterDelslots(final long slot1, final long slot2) {
        final int len = 4;
        final int capacity = 1 + calculateInitialCommandBufferSize(len, RedisProtocolSupport.Command.CLUSTER) +
                    calculateRequestArgumentSize(slot1) + calculateRequestArgumentSize(slot2);
        Buffer buffer = reservedCnx.executionContext().bufferAllocator().newBuffer(capacity);
        writeRequestArraySize(buffer, len);
        RedisProtocolSupport.Command.CLUSTER.encodeTo(buffer);
        RedisProtocolSupport.SubCommand.DELSLOTS.encodeTo(buffer);
        writeRequestArgument(buffer, slot1);
        writeRequestArgument(buffer, slot2);
        final RedisRequest request = newRequest(RedisProtocolSupport.Command.CLUSTER, buffer);
        final Single<String> queued = reservedCnx.request(request, String.class);
        Future<String> result = enqueueForExecute(state, singles, queued);
        return result;
    }

    @Override
    public Future<String> clusterDelslots(final long slot1, final long slot2, final long slot3) {
        final int len = 5;
        final int capacity = 1 + calculateInitialCommandBufferSize(len, RedisProtocolSupport.Command.CLUSTER) +
                    calculateRequestArgumentSize(slot1) + calculateRequestArgumentSize(slot2) +
                    calculateRequestArgumentSize(slot3);
        Buffer buffer = reservedCnx.executionContext().bufferAllocator().newBuffer(capacity);
        writeRequestArraySize(buffer, len);
        RedisProtocolSupport.Command.CLUSTER.encodeTo(buffer);
        RedisProtocolSupport.SubCommand.DELSLOTS.encodeTo(buffer);
        writeRequestArgument(buffer, slot1);
        writeRequestArgument(buffer, slot2);
        writeRequestArgument(buffer, slot3);
        final RedisRequest request = newRequest(RedisProtocolSupport.Command.CLUSTER, buffer);
        final Single<String> queued = reservedCnx.request(request, String.class);
        Future<String> result = enqueueForExecute(state, singles, queued);
        return result;
    }

    @Override
    public Future<String> clusterDelslots(final Collection<Long> slots) {
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
        Buffer buffer = reservedCnx.executionContext().bufferAllocator().newBuffer(capacity);
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
        final Single<String> queued = reservedCnx.request(request, String.class);
        Future<String> result = enqueueForExecute(state, singles, queued);
        return result;
    }

    @Override
    public Future<String> clusterFailover() {
        final int len = 2;
        final int capacity = 1 + calculateInitialCommandBufferSize(len, RedisProtocolSupport.Command.CLUSTER);
        Buffer buffer = reservedCnx.executionContext().bufferAllocator().newBuffer(capacity);
        writeRequestArraySize(buffer, len);
        RedisProtocolSupport.Command.CLUSTER.encodeTo(buffer);
        RedisProtocolSupport.SubCommand.FAILOVER.encodeTo(buffer);
        final RedisRequest request = newRequest(RedisProtocolSupport.Command.CLUSTER, buffer);
        final Single<String> queued = reservedCnx.request(request, String.class);
        Future<String> result = enqueueForExecute(state, singles, queued);
        return result;
    }

    @Override
    public Future<String> clusterFailover(@Nullable final RedisProtocolSupport.ClusterFailoverOptions options) {
        final int len = 2 + (options == null ? 0 : 1);
        final int capacity = 1 + calculateInitialCommandBufferSize(len, RedisProtocolSupport.Command.CLUSTER) +
                    (options == null ? 0 : options.encodedByteCount());
        Buffer buffer = reservedCnx.executionContext().bufferAllocator().newBuffer(capacity);
        writeRequestArraySize(buffer, len);
        RedisProtocolSupport.Command.CLUSTER.encodeTo(buffer);
        RedisProtocolSupport.SubCommand.FAILOVER.encodeTo(buffer);
        if (options != null) {
            options.encodeTo(buffer);
        }
        final RedisRequest request = newRequest(RedisProtocolSupport.Command.CLUSTER, buffer);
        final Single<String> queued = reservedCnx.request(request, String.class);
        Future<String> result = enqueueForExecute(state, singles, queued);
        return result;
    }

    @Override
    public Future<String> clusterForget(final CharSequence nodeId) {
        requireNonNull(nodeId);
        final int len = 3;
        final int nodeIdBytes = numberOfBytesUtf8(nodeId);
        final int capacity = 1 + calculateInitialCommandBufferSize(len, RedisProtocolSupport.Command.CLUSTER) +
                    calculateRequestArgumentSize(nodeIdBytes);
        Buffer buffer = reservedCnx.executionContext().bufferAllocator().newBuffer(capacity);
        writeRequestArraySize(buffer, len);
        RedisProtocolSupport.Command.CLUSTER.encodeTo(buffer);
        RedisProtocolSupport.SubCommand.FORGET.encodeTo(buffer);
        writeRequestArgument(buffer, nodeId, nodeIdBytes);
        final RedisRequest request = newRequest(RedisProtocolSupport.Command.CLUSTER, buffer);
        final Single<String> queued = reservedCnx.request(request, String.class);
        Future<String> result = enqueueForExecute(state, singles, queued);
        return result;
    }

    @Override
    public <T> Future<List<T>> clusterGetkeysinslot(final long slot, final long count) {
        final int len = 4;
        final int capacity = 1 + calculateInitialCommandBufferSize(len, RedisProtocolSupport.Command.CLUSTER) +
                    calculateRequestArgumentSize(slot) + calculateRequestArgumentSize(count);
        Buffer buffer = reservedCnx.executionContext().bufferAllocator().newBuffer(capacity);
        writeRequestArraySize(buffer, len);
        RedisProtocolSupport.Command.CLUSTER.encodeTo(buffer);
        RedisProtocolSupport.SubCommand.GETKEYSINSLOT.encodeTo(buffer);
        writeRequestArgument(buffer, slot);
        writeRequestArgument(buffer, count);
        final RedisRequest request = newRequest(RedisProtocolSupport.Command.CLUSTER, buffer);
        final Single<String> queued = reservedCnx.request(request, String.class);
        Future<List<T>> result = enqueueForExecute(state, singles, queued);
        return result;
    }

    @Override
    public Future<String> clusterInfo() {
        final int len = 2;
        final int capacity = 1 + calculateInitialCommandBufferSize(len, RedisProtocolSupport.Command.CLUSTER);
        Buffer buffer = reservedCnx.executionContext().bufferAllocator().newBuffer(capacity);
        writeRequestArraySize(buffer, len);
        RedisProtocolSupport.Command.CLUSTER.encodeTo(buffer);
        RedisProtocolSupport.SubCommand.INFO.encodeTo(buffer);
        final RedisRequest request = newRequest(RedisProtocolSupport.Command.CLUSTER, buffer);
        final Single<String> queued = reservedCnx.request(request, String.class);
        Future<String> result = enqueueForExecute(state, singles, queued);
        return result;
    }

    @Override
    public Future<Long> clusterKeyslot(final CharSequence key) {
        requireNonNull(key);
        final int len = 3;
        final int keyBytes = numberOfBytesUtf8(key);
        final int capacity = 1 + calculateInitialCommandBufferSize(len, RedisProtocolSupport.Command.CLUSTER) +
                    calculateRequestArgumentSize(keyBytes);
        Buffer buffer = reservedCnx.executionContext().bufferAllocator().newBuffer(capacity);
        writeRequestArraySize(buffer, len);
        RedisProtocolSupport.Command.CLUSTER.encodeTo(buffer);
        RedisProtocolSupport.SubCommand.KEYSLOT.encodeTo(buffer);
        writeRequestArgument(buffer, key, keyBytes);
        final RedisRequest request = newRequest(RedisProtocolSupport.Command.CLUSTER, buffer);
        final Single<String> queued = reservedCnx.request(request, String.class);
        Future<Long> result = enqueueForExecute(state, singles, queued);
        return result;
    }

    @Override
    public Future<String> clusterMeet(final CharSequence ip, final long port) {
        requireNonNull(ip);
        final int len = 4;
        final int ipBytes = numberOfBytesUtf8(ip);
        final int capacity = 1 + calculateInitialCommandBufferSize(len, RedisProtocolSupport.Command.CLUSTER) +
                    calculateRequestArgumentSize(ipBytes) + calculateRequestArgumentSize(port);
        Buffer buffer = reservedCnx.executionContext().bufferAllocator().newBuffer(capacity);
        writeRequestArraySize(buffer, len);
        RedisProtocolSupport.Command.CLUSTER.encodeTo(buffer);
        RedisProtocolSupport.SubCommand.MEET.encodeTo(buffer);
        writeRequestArgument(buffer, ip, ipBytes);
        writeRequestArgument(buffer, port);
        final RedisRequest request = newRequest(RedisProtocolSupport.Command.CLUSTER, buffer);
        final Single<String> queued = reservedCnx.request(request, String.class);
        Future<String> result = enqueueForExecute(state, singles, queued);
        return result;
    }

    @Override
    public Future<String> clusterNodes() {
        final int len = 2;
        final int capacity = 1 + calculateInitialCommandBufferSize(len, RedisProtocolSupport.Command.CLUSTER);
        Buffer buffer = reservedCnx.executionContext().bufferAllocator().newBuffer(capacity);
        writeRequestArraySize(buffer, len);
        RedisProtocolSupport.Command.CLUSTER.encodeTo(buffer);
        RedisProtocolSupport.SubCommand.NODES.encodeTo(buffer);
        final RedisRequest request = newRequest(RedisProtocolSupport.Command.CLUSTER, buffer);
        final Single<String> queued = reservedCnx.request(request, String.class);
        Future<String> result = enqueueForExecute(state, singles, queued);
        return result;
    }

    @Override
    public Future<String> clusterReplicate(final CharSequence nodeId) {
        requireNonNull(nodeId);
        final int len = 3;
        final int nodeIdBytes = numberOfBytesUtf8(nodeId);
        final int capacity = 1 + calculateInitialCommandBufferSize(len, RedisProtocolSupport.Command.CLUSTER) +
                    calculateRequestArgumentSize(nodeIdBytes);
        Buffer buffer = reservedCnx.executionContext().bufferAllocator().newBuffer(capacity);
        writeRequestArraySize(buffer, len);
        RedisProtocolSupport.Command.CLUSTER.encodeTo(buffer);
        RedisProtocolSupport.SubCommand.REPLICATE.encodeTo(buffer);
        writeRequestArgument(buffer, nodeId, nodeIdBytes);
        final RedisRequest request = newRequest(RedisProtocolSupport.Command.CLUSTER, buffer);
        final Single<String> queued = reservedCnx.request(request, String.class);
        Future<String> result = enqueueForExecute(state, singles, queued);
        return result;
    }

    @Override
    public Future<String> clusterReset() {
        final int len = 2;
        final int capacity = 1 + calculateInitialCommandBufferSize(len, RedisProtocolSupport.Command.CLUSTER);
        Buffer buffer = reservedCnx.executionContext().bufferAllocator().newBuffer(capacity);
        writeRequestArraySize(buffer, len);
        RedisProtocolSupport.Command.CLUSTER.encodeTo(buffer);
        RedisProtocolSupport.SubCommand.RESET.encodeTo(buffer);
        final RedisRequest request = newRequest(RedisProtocolSupport.Command.CLUSTER, buffer);
        final Single<String> queued = reservedCnx.request(request, String.class);
        Future<String> result = enqueueForExecute(state, singles, queued);
        return result;
    }

    @Override
    public Future<String> clusterReset(@Nullable final RedisProtocolSupport.ClusterResetResetType resetType) {
        final int len = 2 + (resetType == null ? 0 : 1);
        final int capacity = 1 + calculateInitialCommandBufferSize(len, RedisProtocolSupport.Command.CLUSTER) +
                    (resetType == null ? 0 : resetType.encodedByteCount());
        Buffer buffer = reservedCnx.executionContext().bufferAllocator().newBuffer(capacity);
        writeRequestArraySize(buffer, len);
        RedisProtocolSupport.Command.CLUSTER.encodeTo(buffer);
        RedisProtocolSupport.SubCommand.RESET.encodeTo(buffer);
        if (resetType != null) {
            resetType.encodeTo(buffer);
        }
        final RedisRequest request = newRequest(RedisProtocolSupport.Command.CLUSTER, buffer);
        final Single<String> queued = reservedCnx.request(request, String.class);
        Future<String> result = enqueueForExecute(state, singles, queued);
        return result;
    }

    @Override
    public Future<String> clusterSaveconfig() {
        final int len = 2;
        final int capacity = 1 + calculateInitialCommandBufferSize(len, RedisProtocolSupport.Command.CLUSTER);
        Buffer buffer = reservedCnx.executionContext().bufferAllocator().newBuffer(capacity);
        writeRequestArraySize(buffer, len);
        RedisProtocolSupport.Command.CLUSTER.encodeTo(buffer);
        RedisProtocolSupport.SubCommand.SAVECONFIG.encodeTo(buffer);
        final RedisRequest request = newRequest(RedisProtocolSupport.Command.CLUSTER, buffer);
        final Single<String> queued = reservedCnx.request(request, String.class);
        Future<String> result = enqueueForExecute(state, singles, queued);
        return result;
    }

    @Override
    public Future<String> clusterSetConfigEpoch(final long configEpoch) {
        final int len = 3;
        final int capacity = 1 + calculateInitialCommandBufferSize(len, RedisProtocolSupport.Command.CLUSTER) +
                    calculateRequestArgumentSize(configEpoch);
        Buffer buffer = reservedCnx.executionContext().bufferAllocator().newBuffer(capacity);
        writeRequestArraySize(buffer, len);
        RedisProtocolSupport.Command.CLUSTER.encodeTo(buffer);
        RedisProtocolSupport.SubCommand.SET_CONFIG_EPOCH.encodeTo(buffer);
        writeRequestArgument(buffer, configEpoch);
        final RedisRequest request = newRequest(RedisProtocolSupport.Command.CLUSTER, buffer);
        final Single<String> queued = reservedCnx.request(request, String.class);
        Future<String> result = enqueueForExecute(state, singles, queued);
        return result;
    }

    @Override
    public Future<String> clusterSetslot(final long slot,
                                         final RedisProtocolSupport.ClusterSetslotSubcommand subcommand) {
        requireNonNull(subcommand);
        final int len = 4;
        final int capacity = 1 + calculateInitialCommandBufferSize(len, RedisProtocolSupport.Command.CLUSTER) +
                    calculateRequestArgumentSize(slot) + subcommand.encodedByteCount();
        Buffer buffer = reservedCnx.executionContext().bufferAllocator().newBuffer(capacity);
        writeRequestArraySize(buffer, len);
        RedisProtocolSupport.Command.CLUSTER.encodeTo(buffer);
        RedisProtocolSupport.SubCommand.SETSLOT.encodeTo(buffer);
        writeRequestArgument(buffer, slot);
        subcommand.encodeTo(buffer);
        final RedisRequest request = newRequest(RedisProtocolSupport.Command.CLUSTER, buffer);
        final Single<String> queued = reservedCnx.request(request, String.class);
        Future<String> result = enqueueForExecute(state, singles, queued);
        return result;
    }

    @Override
    public Future<String> clusterSetslot(final long slot,
                                         final RedisProtocolSupport.ClusterSetslotSubcommand subcommand,
                                         @Nullable final CharSequence nodeId) {
        requireNonNull(subcommand);
        final int len = 4 + (nodeId == null ? 0 : 1);
        final int nodeIdBytes = nodeId == null ? 0 : numberOfBytesUtf8(nodeId);
        final int capacity = 1 + calculateInitialCommandBufferSize(len, RedisProtocolSupport.Command.CLUSTER) +
                    calculateRequestArgumentSize(slot) + subcommand.encodedByteCount() +
                    (nodeId == null ? 0 : calculateRequestArgumentSize(nodeIdBytes));
        Buffer buffer = reservedCnx.executionContext().bufferAllocator().newBuffer(capacity);
        writeRequestArraySize(buffer, len);
        RedisProtocolSupport.Command.CLUSTER.encodeTo(buffer);
        RedisProtocolSupport.SubCommand.SETSLOT.encodeTo(buffer);
        writeRequestArgument(buffer, slot);
        subcommand.encodeTo(buffer);
        if (nodeId != null) {
            writeRequestArgument(buffer, nodeId, nodeIdBytes);
        }
        final RedisRequest request = newRequest(RedisProtocolSupport.Command.CLUSTER, buffer);
        final Single<String> queued = reservedCnx.request(request, String.class);
        Future<String> result = enqueueForExecute(state, singles, queued);
        return result;
    }

    @Override
    public Future<String> clusterSlaves(final CharSequence nodeId) {
        requireNonNull(nodeId);
        final int len = 3;
        final int nodeIdBytes = numberOfBytesUtf8(nodeId);
        final int capacity = 1 + calculateInitialCommandBufferSize(len, RedisProtocolSupport.Command.CLUSTER) +
                    calculateRequestArgumentSize(nodeIdBytes);
        Buffer buffer = reservedCnx.executionContext().bufferAllocator().newBuffer(capacity);
        writeRequestArraySize(buffer, len);
        RedisProtocolSupport.Command.CLUSTER.encodeTo(buffer);
        RedisProtocolSupport.SubCommand.SLAVES.encodeTo(buffer);
        writeRequestArgument(buffer, nodeId, nodeIdBytes);
        final RedisRequest request = newRequest(RedisProtocolSupport.Command.CLUSTER, buffer);
        final Single<String> queued = reservedCnx.request(request, String.class);
        Future<String> result = enqueueForExecute(state, singles, queued);
        return result;
    }

    @Override
    public <T> Future<List<T>> clusterSlots() {
        final int len = 2;
        final int capacity = 1 + calculateInitialCommandBufferSize(len, RedisProtocolSupport.Command.CLUSTER);
        Buffer buffer = reservedCnx.executionContext().bufferAllocator().newBuffer(capacity);
        writeRequestArraySize(buffer, len);
        RedisProtocolSupport.Command.CLUSTER.encodeTo(buffer);
        RedisProtocolSupport.SubCommand.SLOTS.encodeTo(buffer);
        final RedisRequest request = newRequest(RedisProtocolSupport.Command.CLUSTER, buffer);
        final Single<String> queued = reservedCnx.request(request, String.class);
        Future<List<T>> result = enqueueForExecute(state, singles, queued);
        return result;
    }

    @Override
    public <T> Future<List<T>> command() {
        final int len = 1;
        final int capacity = calculateInitialCommandBufferSize(len, RedisProtocolSupport.Command.COMMAND);
        Buffer buffer = reservedCnx.executionContext().bufferAllocator().newBuffer(capacity);
        writeRequestArraySize(buffer, len);
        RedisProtocolSupport.Command.COMMAND.encodeTo(buffer);
        final RedisRequest request = newRequest(RedisProtocolSupport.Command.COMMAND, buffer);
        final Single<String> queued = reservedCnx.request(request, String.class);
        Future<List<T>> result = enqueueForExecute(state, singles, queued);
        return result;
    }

    @Override
    public Future<Long> commandCount() {
        final int len = 2;
        final int capacity = 1 + calculateInitialCommandBufferSize(len, RedisProtocolSupport.Command.COMMAND);
        Buffer buffer = reservedCnx.executionContext().bufferAllocator().newBuffer(capacity);
        writeRequestArraySize(buffer, len);
        RedisProtocolSupport.Command.COMMAND.encodeTo(buffer);
        RedisProtocolSupport.SubCommand.COUNT.encodeTo(buffer);
        final RedisRequest request = newRequest(RedisProtocolSupport.Command.COMMAND, buffer);
        final Single<String> queued = reservedCnx.request(request, String.class);
        Future<Long> result = enqueueForExecute(state, singles, queued);
        return result;
    }

    @Override
    public <T> Future<List<T>> commandGetkeys() {
        final int len = 2;
        final int capacity = 1 + calculateInitialCommandBufferSize(len, RedisProtocolSupport.Command.COMMAND);
        Buffer buffer = reservedCnx.executionContext().bufferAllocator().newBuffer(capacity);
        writeRequestArraySize(buffer, len);
        RedisProtocolSupport.Command.COMMAND.encodeTo(buffer);
        RedisProtocolSupport.SubCommand.GETKEYS.encodeTo(buffer);
        final RedisRequest request = newRequest(RedisProtocolSupport.Command.COMMAND, buffer);
        final Single<String> queued = reservedCnx.request(request, String.class);
        Future<List<T>> result = enqueueForExecute(state, singles, queued);
        return result;
    }

    @Override
    public <T> Future<List<T>> commandInfo(final CharSequence commandName) {
        requireNonNull(commandName);
        final int len = 3;
        final int commandNameBytes = numberOfBytesUtf8(commandName);
        final int capacity = 1 + calculateInitialCommandBufferSize(len, RedisProtocolSupport.Command.COMMAND) +
                    calculateRequestArgumentSize(commandNameBytes);
        Buffer buffer = reservedCnx.executionContext().bufferAllocator().newBuffer(capacity);
        writeRequestArraySize(buffer, len);
        RedisProtocolSupport.Command.COMMAND.encodeTo(buffer);
        RedisProtocolSupport.SubCommand.INFO.encodeTo(buffer);
        writeRequestArgument(buffer, commandName, commandNameBytes);
        final RedisRequest request = newRequest(RedisProtocolSupport.Command.COMMAND, buffer);
        final Single<String> queued = reservedCnx.request(request, String.class);
        Future<List<T>> result = enqueueForExecute(state, singles, queued);
        return result;
    }

    @Override
    public <T> Future<List<T>> commandInfo(final CharSequence commandName1, final CharSequence commandName2) {
        requireNonNull(commandName1);
        requireNonNull(commandName2);
        final int len = 4;
        final int commandName1Bytes = numberOfBytesUtf8(commandName1);
        final int commandName2Bytes = numberOfBytesUtf8(commandName2);
        final int capacity = 1 + calculateInitialCommandBufferSize(len, RedisProtocolSupport.Command.COMMAND) +
                    calculateRequestArgumentSize(commandName1Bytes) + calculateRequestArgumentSize(commandName2Bytes);
        Buffer buffer = reservedCnx.executionContext().bufferAllocator().newBuffer(capacity);
        writeRequestArraySize(buffer, len);
        RedisProtocolSupport.Command.COMMAND.encodeTo(buffer);
        RedisProtocolSupport.SubCommand.INFO.encodeTo(buffer);
        writeRequestArgument(buffer, commandName1, commandName1Bytes);
        writeRequestArgument(buffer, commandName2, commandName2Bytes);
        final RedisRequest request = newRequest(RedisProtocolSupport.Command.COMMAND, buffer);
        final Single<String> queued = reservedCnx.request(request, String.class);
        Future<List<T>> result = enqueueForExecute(state, singles, queued);
        return result;
    }

    @Override
    public <T> Future<List<T>> commandInfo(final CharSequence commandName1, final CharSequence commandName2,
                                           final CharSequence commandName3) {
        requireNonNull(commandName1);
        requireNonNull(commandName2);
        requireNonNull(commandName3);
        final int len = 5;
        final int commandName1Bytes = numberOfBytesUtf8(commandName1);
        final int commandName2Bytes = numberOfBytesUtf8(commandName2);
        final int commandName3Bytes = numberOfBytesUtf8(commandName3);
        final int capacity = 1 + calculateInitialCommandBufferSize(len, RedisProtocolSupport.Command.COMMAND) +
                    calculateRequestArgumentSize(commandName1Bytes) + calculateRequestArgumentSize(commandName2Bytes) +
                    calculateRequestArgumentSize(commandName3Bytes);
        Buffer buffer = reservedCnx.executionContext().bufferAllocator().newBuffer(capacity);
        writeRequestArraySize(buffer, len);
        RedisProtocolSupport.Command.COMMAND.encodeTo(buffer);
        RedisProtocolSupport.SubCommand.INFO.encodeTo(buffer);
        writeRequestArgument(buffer, commandName1, commandName1Bytes);
        writeRequestArgument(buffer, commandName2, commandName2Bytes);
        writeRequestArgument(buffer, commandName3, commandName3Bytes);
        final RedisRequest request = newRequest(RedisProtocolSupport.Command.COMMAND, buffer);
        final Single<String> queued = reservedCnx.request(request, String.class);
        Future<List<T>> result = enqueueForExecute(state, singles, queued);
        return result;
    }

    @Override
    public <T> Future<List<T>> commandInfo(final Collection<? extends CharSequence> commandNames) {
        requireNonNull(commandNames);
        final int len = 2 + commandNames.size();
        int commandNamesCapacity = 0;
        if (commandNames instanceof List && commandNames instanceof RandomAccess) {
            final List<CharSequence> list = (List<CharSequence>) commandNames;
            for (int i = 0; i < list.size(); ++i) {
                final CharSequence arg = list.get(i);
                commandNamesCapacity += estimateRequestArgumentSize(arg);
            }
        } else {
            for (CharSequence arg : commandNames) {
                commandNamesCapacity += estimateRequestArgumentSize(arg);
            }
        }
        final int capacity = 1 + calculateInitialCommandBufferSize(len, RedisProtocolSupport.Command.COMMAND) +
                    commandNamesCapacity;
        Buffer buffer = reservedCnx.executionContext().bufferAllocator().newBuffer(capacity);
        writeRequestArraySize(buffer, len);
        RedisProtocolSupport.Command.COMMAND.encodeTo(buffer);
        RedisProtocolSupport.SubCommand.INFO.encodeTo(buffer);
        if (commandNames instanceof List && commandNames instanceof RandomAccess) {
            final List<CharSequence> list = (List<CharSequence>) commandNames;
            for (int i = 0; i < list.size(); ++i) {
                final CharSequence arg = list.get(i);
                writeRequestArgument(buffer, arg);
            }
        } else {
            for (CharSequence arg : commandNames) {
                writeRequestArgument(buffer, arg);
            }
        }
        final RedisRequest request = newRequest(RedisProtocolSupport.Command.COMMAND, buffer);
        final Single<String> queued = reservedCnx.request(request, String.class);
        Future<List<T>> result = enqueueForExecute(state, singles, queued);
        return result;
    }

    @Override
    public <T> Future<List<T>> configGet(final CharSequence parameter) {
        requireNonNull(parameter);
        final int len = 3;
        final int parameterBytes = numberOfBytesUtf8(parameter);
        final int capacity = 1 + calculateInitialCommandBufferSize(len, RedisProtocolSupport.Command.CONFIG) +
                    calculateRequestArgumentSize(parameterBytes);
        Buffer buffer = reservedCnx.executionContext().bufferAllocator().newBuffer(capacity);
        writeRequestArraySize(buffer, len);
        RedisProtocolSupport.Command.CONFIG.encodeTo(buffer);
        RedisProtocolSupport.SubCommand.GET.encodeTo(buffer);
        writeRequestArgument(buffer, parameter, parameterBytes);
        final RedisRequest request = newRequest(RedisProtocolSupport.Command.CONFIG, buffer);
        final Single<String> queued = reservedCnx.request(request, String.class);
        Future<List<T>> result = enqueueForExecute(state, singles, queued);
        return result;
    }

    @Override
    public Future<String> configRewrite() {
        final int len = 2;
        final int capacity = 1 + calculateInitialCommandBufferSize(len, RedisProtocolSupport.Command.CONFIG);
        Buffer buffer = reservedCnx.executionContext().bufferAllocator().newBuffer(capacity);
        writeRequestArraySize(buffer, len);
        RedisProtocolSupport.Command.CONFIG.encodeTo(buffer);
        RedisProtocolSupport.SubCommand.REWRITE.encodeTo(buffer);
        final RedisRequest request = newRequest(RedisProtocolSupport.Command.CONFIG, buffer);
        final Single<String> queued = reservedCnx.request(request, String.class);
        Future<String> result = enqueueForExecute(state, singles, queued);
        return result;
    }

    @Override
    public Future<String> configSet(final CharSequence parameter, final CharSequence value) {
        requireNonNull(parameter);
        requireNonNull(value);
        final int len = 4;
        final int parameterBytes = numberOfBytesUtf8(parameter);
        final int valueBytes = numberOfBytesUtf8(value);
        final int capacity = 1 + calculateInitialCommandBufferSize(len, RedisProtocolSupport.Command.CONFIG) +
                    calculateRequestArgumentSize(parameterBytes) + calculateRequestArgumentSize(valueBytes);
        Buffer buffer = reservedCnx.executionContext().bufferAllocator().newBuffer(capacity);
        writeRequestArraySize(buffer, len);
        RedisProtocolSupport.Command.CONFIG.encodeTo(buffer);
        RedisProtocolSupport.SubCommand.SET.encodeTo(buffer);
        writeRequestArgument(buffer, parameter, parameterBytes);
        writeRequestArgument(buffer, value, valueBytes);
        final RedisRequest request = newRequest(RedisProtocolSupport.Command.CONFIG, buffer);
        final Single<String> queued = reservedCnx.request(request, String.class);
        Future<String> result = enqueueForExecute(state, singles, queued);
        return result;
    }

    @Override
    public Future<String> configResetstat() {
        final int len = 2;
        final int capacity = 1 + calculateInitialCommandBufferSize(len, RedisProtocolSupport.Command.CONFIG);
        Buffer buffer = reservedCnx.executionContext().bufferAllocator().newBuffer(capacity);
        writeRequestArraySize(buffer, len);
        RedisProtocolSupport.Command.CONFIG.encodeTo(buffer);
        RedisProtocolSupport.SubCommand.RESETSTAT.encodeTo(buffer);
        final RedisRequest request = newRequest(RedisProtocolSupport.Command.CONFIG, buffer);
        final Single<String> queued = reservedCnx.request(request, String.class);
        Future<String> result = enqueueForExecute(state, singles, queued);
        return result;
    }

    @Override
    public Future<Long> dbsize() {
        final int len = 1;
        final int capacity = calculateInitialCommandBufferSize(len, RedisProtocolSupport.Command.DBSIZE);
        Buffer buffer = reservedCnx.executionContext().bufferAllocator().newBuffer(capacity);
        writeRequestArraySize(buffer, len);
        RedisProtocolSupport.Command.DBSIZE.encodeTo(buffer);
        final RedisRequest request = newRequest(RedisProtocolSupport.Command.DBSIZE, buffer);
        final Single<String> queued = reservedCnx.request(request, String.class);
        Future<Long> result = enqueueForExecute(state, singles, queued);
        return result;
    }

    @Override
    public Future<String> debugObject(@RedisProtocolSupport.Key final CharSequence key) {
        requireNonNull(key);
        final int len = 3;
        final int keyBytes = numberOfBytesUtf8(key);
        final int capacity = 1 + calculateInitialCommandBufferSize(len, RedisProtocolSupport.Command.DEBUG) +
                    calculateRequestArgumentSize(keyBytes);
        Buffer buffer = reservedCnx.executionContext().bufferAllocator().newBuffer(capacity);
        writeRequestArraySize(buffer, len);
        RedisProtocolSupport.Command.DEBUG.encodeTo(buffer);
        RedisProtocolSupport.SubCommand.OBJECT.encodeTo(buffer);
        writeRequestArgument(buffer, key, keyBytes);
        final RedisRequest request = newRequest(RedisProtocolSupport.Command.DEBUG, buffer);
        final Single<String> queued = reservedCnx.request(request, String.class);
        Future<String> result = enqueueForExecute(state, singles, queued);
        return result;
    }

    @Override
    public Future<String> debugSegfault() {
        final int len = 2;
        final int capacity = 1 + calculateInitialCommandBufferSize(len, RedisProtocolSupport.Command.DEBUG);
        Buffer buffer = reservedCnx.executionContext().bufferAllocator().newBuffer(capacity);
        writeRequestArraySize(buffer, len);
        RedisProtocolSupport.Command.DEBUG.encodeTo(buffer);
        RedisProtocolSupport.SubCommand.SEGFAULT.encodeTo(buffer);
        final RedisRequest request = newRequest(RedisProtocolSupport.Command.DEBUG, buffer);
        final Single<String> queued = reservedCnx.request(request, String.class);
        Future<String> result = enqueueForExecute(state, singles, queued);
        return result;
    }

    @Override
    public Future<Long> decr(@RedisProtocolSupport.Key final CharSequence key) {
        requireNonNull(key);
        final int len = 2;
        final int keyBytes = numberOfBytesUtf8(key);
        final int capacity = calculateInitialCommandBufferSize(len, RedisProtocolSupport.Command.DECR) +
                    calculateRequestArgumentSize(keyBytes);
        Buffer buffer = reservedCnx.executionContext().bufferAllocator().newBuffer(capacity);
        writeRequestArraySize(buffer, len);
        RedisProtocolSupport.Command.DECR.encodeTo(buffer);
        writeRequestArgument(buffer, key, keyBytes);
        final RedisRequest request = newRequest(RedisProtocolSupport.Command.DECR, buffer);
        final Single<String> queued = reservedCnx.request(request, String.class);
        Future<Long> result = enqueueForExecute(state, singles, queued);
        return result;
    }

    @Override
    public Future<Long> decrby(@RedisProtocolSupport.Key final CharSequence key, final long decrement) {
        requireNonNull(key);
        final int len = 3;
        final int keyBytes = numberOfBytesUtf8(key);
        final int capacity = calculateInitialCommandBufferSize(len, RedisProtocolSupport.Command.DECRBY) +
                    calculateRequestArgumentSize(keyBytes) + calculateRequestArgumentSize(decrement);
        Buffer buffer = reservedCnx.executionContext().bufferAllocator().newBuffer(capacity);
        writeRequestArraySize(buffer, len);
        RedisProtocolSupport.Command.DECRBY.encodeTo(buffer);
        writeRequestArgument(buffer, key, keyBytes);
        writeRequestArgument(buffer, decrement);
        final RedisRequest request = newRequest(RedisProtocolSupport.Command.DECRBY, buffer);
        final Single<String> queued = reservedCnx.request(request, String.class);
        Future<Long> result = enqueueForExecute(state, singles, queued);
        return result;
    }

    @Override
    public Future<Long> del(@RedisProtocolSupport.Key final CharSequence key) {
        requireNonNull(key);
        final int len = 2;
        final int keyBytes = numberOfBytesUtf8(key);
        final int capacity = calculateInitialCommandBufferSize(len, RedisProtocolSupport.Command.DEL) +
                    calculateRequestArgumentSize(keyBytes);
        Buffer buffer = reservedCnx.executionContext().bufferAllocator().newBuffer(capacity);
        writeRequestArraySize(buffer, len);
        RedisProtocolSupport.Command.DEL.encodeTo(buffer);
        writeRequestArgument(buffer, key, keyBytes);
        final RedisRequest request = newRequest(RedisProtocolSupport.Command.DEL, buffer);
        final Single<String> queued = reservedCnx.request(request, String.class);
        Future<Long> result = enqueueForExecute(state, singles, queued);
        return result;
    }

    @Override
    public Future<Long> del(@RedisProtocolSupport.Key final CharSequence key1,
                            @RedisProtocolSupport.Key final CharSequence key2) {
        requireNonNull(key1);
        requireNonNull(key2);
        final int len = 3;
        final int key1Bytes = numberOfBytesUtf8(key1);
        final int key2Bytes = numberOfBytesUtf8(key2);
        final int capacity = calculateInitialCommandBufferSize(len, RedisProtocolSupport.Command.DEL) +
                    calculateRequestArgumentSize(key1Bytes) + calculateRequestArgumentSize(key2Bytes);
        Buffer buffer = reservedCnx.executionContext().bufferAllocator().newBuffer(capacity);
        writeRequestArraySize(buffer, len);
        RedisProtocolSupport.Command.DEL.encodeTo(buffer);
        writeRequestArgument(buffer, key1, key1Bytes);
        writeRequestArgument(buffer, key2, key2Bytes);
        final RedisRequest request = newRequest(RedisProtocolSupport.Command.DEL, buffer);
        final Single<String> queued = reservedCnx.request(request, String.class);
        Future<Long> result = enqueueForExecute(state, singles, queued);
        return result;
    }

    @Override
    public Future<Long> del(@RedisProtocolSupport.Key final CharSequence key1,
                            @RedisProtocolSupport.Key final CharSequence key2,
                            @RedisProtocolSupport.Key final CharSequence key3) {
        requireNonNull(key1);
        requireNonNull(key2);
        requireNonNull(key3);
        final int len = 4;
        final int key1Bytes = numberOfBytesUtf8(key1);
        final int key2Bytes = numberOfBytesUtf8(key2);
        final int key3Bytes = numberOfBytesUtf8(key3);
        final int capacity = calculateInitialCommandBufferSize(len, RedisProtocolSupport.Command.DEL) +
                    calculateRequestArgumentSize(key1Bytes) + calculateRequestArgumentSize(key2Bytes) +
                    calculateRequestArgumentSize(key3Bytes);
        Buffer buffer = reservedCnx.executionContext().bufferAllocator().newBuffer(capacity);
        writeRequestArraySize(buffer, len);
        RedisProtocolSupport.Command.DEL.encodeTo(buffer);
        writeRequestArgument(buffer, key1, key1Bytes);
        writeRequestArgument(buffer, key2, key2Bytes);
        writeRequestArgument(buffer, key3, key3Bytes);
        final RedisRequest request = newRequest(RedisProtocolSupport.Command.DEL, buffer);
        final Single<String> queued = reservedCnx.request(request, String.class);
        Future<Long> result = enqueueForExecute(state, singles, queued);
        return result;
    }

    @Override
    public Future<Long> del(@RedisProtocolSupport.Key final Collection<? extends CharSequence> keys) {
        requireNonNull(keys);
        final int len = 1 + keys.size();
        int keysCapacity = 0;
        if (keys instanceof List && keys instanceof RandomAccess) {
            final List<CharSequence> list = (List<CharSequence>) keys;
            for (int i = 0; i < list.size(); ++i) {
                final CharSequence arg = list.get(i);
                keysCapacity += estimateRequestArgumentSize(arg);
            }
        } else {
            for (CharSequence arg : keys) {
                keysCapacity += estimateRequestArgumentSize(arg);
            }
        }
        final int capacity = calculateInitialCommandBufferSize(len, RedisProtocolSupport.Command.DEL) + keysCapacity;
        Buffer buffer = reservedCnx.executionContext().bufferAllocator().newBuffer(capacity);
        writeRequestArraySize(buffer, len);
        RedisProtocolSupport.Command.DEL.encodeTo(buffer);
        if (keys instanceof List && keys instanceof RandomAccess) {
            final List<CharSequence> list = (List<CharSequence>) keys;
            for (int i = 0; i < list.size(); ++i) {
                final CharSequence arg = list.get(i);
                writeRequestArgument(buffer, arg);
            }
        } else {
            for (CharSequence arg : keys) {
                writeRequestArgument(buffer, arg);
            }
        }
        final RedisRequest request = newRequest(RedisProtocolSupport.Command.DEL, buffer);
        final Single<String> queued = reservedCnx.request(request, String.class);
        Future<Long> result = enqueueForExecute(state, singles, queued);
        return result;
    }

    @Override
    public Single<String> discard() {
        final int len = 1;
        final int capacity = calculateInitialCommandBufferSize(len, RedisProtocolSupport.Command.DISCARD);
        Buffer buffer = reservedCnx.executionContext().bufferAllocator().newBuffer(capacity);
        writeRequestArraySize(buffer, len);
        RedisProtocolSupport.Command.DISCARD.encodeTo(buffer);
        final RedisRequest request = newRequest(RedisProtocolSupport.Command.DISCARD, buffer);
        final Single<String> queued = reservedCnx.request(request, String.class);
        final Single<String> result = new DiscardSingle<>(this, queued, singles, stateUpdater, reservedCnx,
                    releaseAfterDone);
        return result;
    }

    @Override
    public Future<String> dump(@RedisProtocolSupport.Key final CharSequence key) {
        requireNonNull(key);
        final int len = 2;
        final int keyBytes = numberOfBytesUtf8(key);
        final int capacity = calculateInitialCommandBufferSize(len, RedisProtocolSupport.Command.DUMP) +
                    calculateRequestArgumentSize(keyBytes);
        Buffer buffer = reservedCnx.executionContext().bufferAllocator().newBuffer(capacity);
        writeRequestArraySize(buffer, len);
        RedisProtocolSupport.Command.DUMP.encodeTo(buffer);
        writeRequestArgument(buffer, key, keyBytes);
        final RedisRequest request = newRequest(RedisProtocolSupport.Command.DUMP, buffer);
        final Single<String> queued = reservedCnx.request(request, String.class);
        Future<String> result = enqueueForExecute(state, singles, queued);
        return result;
    }

    @Override
    public Future<String> echo(final CharSequence message) {
        requireNonNull(message);
        final int len = 2;
        final int messageBytes = numberOfBytesUtf8(message);
        final int capacity = calculateInitialCommandBufferSize(len, RedisProtocolSupport.Command.ECHO) +
                    calculateRequestArgumentSize(messageBytes);
        Buffer buffer = reservedCnx.executionContext().bufferAllocator().newBuffer(capacity);
        writeRequestArraySize(buffer, len);
        RedisProtocolSupport.Command.ECHO.encodeTo(buffer);
        writeRequestArgument(buffer, message, messageBytes);
        final RedisRequest request = newRequest(RedisProtocolSupport.Command.ECHO, buffer);
        final Single<String> queued = reservedCnx.request(request, String.class);
        Future<String> result = enqueueForExecute(state, singles, queued);
        return result;
    }

    @Override
    public Future<String> eval(final CharSequence script, final long numkeys,
                               @RedisProtocolSupport.Key final Collection<? extends CharSequence> keys,
                               final Collection<? extends CharSequence> args) {
        requireNonNull(script);
        requireNonNull(keys);
        requireNonNull(args);
        final int len = 3 + keys.size() + args.size();
        final int scriptBytes = numberOfBytesUtf8(script);
        int keysCapacity = 0;
        if (keys instanceof List && keys instanceof RandomAccess) {
            final List<CharSequence> list = (List<CharSequence>) keys;
            for (int i = 0; i < list.size(); ++i) {
                final CharSequence arg = list.get(i);
                keysCapacity += estimateRequestArgumentSize(arg);
            }
        } else {
            for (CharSequence arg : keys) {
                keysCapacity += estimateRequestArgumentSize(arg);
            }
        }
        int argsCapacity = 0;
        if (args instanceof List && args instanceof RandomAccess) {
            final List<CharSequence> list = (List<CharSequence>) args;
            for (int i = 0; i < list.size(); ++i) {
                final CharSequence arg = list.get(i);
                argsCapacity += estimateRequestArgumentSize(arg);
            }
        } else {
            for (CharSequence arg : args) {
                argsCapacity += estimateRequestArgumentSize(arg);
            }
        }
        final int capacity = calculateInitialCommandBufferSize(len, RedisProtocolSupport.Command.EVAL) +
                    calculateRequestArgumentSize(scriptBytes) + calculateRequestArgumentSize(numkeys) + keysCapacity +
                    argsCapacity;
        Buffer buffer = reservedCnx.executionContext().bufferAllocator().newBuffer(capacity);
        writeRequestArraySize(buffer, len);
        RedisProtocolSupport.Command.EVAL.encodeTo(buffer);
        writeRequestArgument(buffer, script, scriptBytes);
        writeRequestArgument(buffer, numkeys);
        if (keys instanceof List && keys instanceof RandomAccess) {
            final List<CharSequence> list = (List<CharSequence>) keys;
            for (int i = 0; i < list.size(); ++i) {
                final CharSequence arg = list.get(i);
                writeRequestArgument(buffer, arg);
            }
        } else {
            for (CharSequence arg : keys) {
                writeRequestArgument(buffer, arg);
            }
        }
        if (args instanceof List && args instanceof RandomAccess) {
            final List<CharSequence> list = (List<CharSequence>) args;
            for (int i = 0; i < list.size(); ++i) {
                final CharSequence arg = list.get(i);
                writeRequestArgument(buffer, arg);
            }
        } else {
            for (CharSequence arg : args) {
                writeRequestArgument(buffer, arg);
            }
        }
        final RedisRequest request = newRequest(RedisProtocolSupport.Command.EVAL, buffer);
        final Single<String> queued = reservedCnx.request(request, String.class);
        Future<String> result = enqueueForExecute(state, singles, queued);
        return result;
    }

    @Override
    public <T> Future<List<T>> evalList(final CharSequence script, final long numkeys,
                                        @RedisProtocolSupport.Key final Collection<? extends CharSequence> keys,
                                        final Collection<? extends CharSequence> args) {
        requireNonNull(script);
        requireNonNull(keys);
        requireNonNull(args);
        final int len = 3 + keys.size() + args.size();
        final int scriptBytes = numberOfBytesUtf8(script);
        int keysCapacity = 0;
        if (keys instanceof List && keys instanceof RandomAccess) {
            final List<CharSequence> list = (List<CharSequence>) keys;
            for (int i = 0; i < list.size(); ++i) {
                final CharSequence arg = list.get(i);
                keysCapacity += estimateRequestArgumentSize(arg);
            }
        } else {
            for (CharSequence arg : keys) {
                keysCapacity += estimateRequestArgumentSize(arg);
            }
        }
        int argsCapacity = 0;
        if (args instanceof List && args instanceof RandomAccess) {
            final List<CharSequence> list = (List<CharSequence>) args;
            for (int i = 0; i < list.size(); ++i) {
                final CharSequence arg = list.get(i);
                argsCapacity += estimateRequestArgumentSize(arg);
            }
        } else {
            for (CharSequence arg : args) {
                argsCapacity += estimateRequestArgumentSize(arg);
            }
        }
        final int capacity = calculateInitialCommandBufferSize(len, RedisProtocolSupport.Command.EVAL) +
                    calculateRequestArgumentSize(scriptBytes) + calculateRequestArgumentSize(numkeys) + keysCapacity +
                    argsCapacity;
        Buffer buffer = reservedCnx.executionContext().bufferAllocator().newBuffer(capacity);
        writeRequestArraySize(buffer, len);
        RedisProtocolSupport.Command.EVAL.encodeTo(buffer);
        writeRequestArgument(buffer, script, scriptBytes);
        writeRequestArgument(buffer, numkeys);
        if (keys instanceof List && keys instanceof RandomAccess) {
            final List<CharSequence> list = (List<CharSequence>) keys;
            for (int i = 0; i < list.size(); ++i) {
                final CharSequence arg = list.get(i);
                writeRequestArgument(buffer, arg);
            }
        } else {
            for (CharSequence arg : keys) {
                writeRequestArgument(buffer, arg);
            }
        }
        if (args instanceof List && args instanceof RandomAccess) {
            final List<CharSequence> list = (List<CharSequence>) args;
            for (int i = 0; i < list.size(); ++i) {
                final CharSequence arg = list.get(i);
                writeRequestArgument(buffer, arg);
            }
        } else {
            for (CharSequence arg : args) {
                writeRequestArgument(buffer, arg);
            }
        }
        final RedisRequest request = newRequest(RedisProtocolSupport.Command.EVAL, buffer);
        final Single<String> queued = reservedCnx.request(request, String.class);
        Future<List<T>> result = enqueueForExecute(state, singles, queued);
        return result;
    }

    @Override
    public Future<Long> evalLong(final CharSequence script, final long numkeys,
                                 @RedisProtocolSupport.Key final Collection<? extends CharSequence> keys,
                                 final Collection<? extends CharSequence> args) {
        requireNonNull(script);
        requireNonNull(keys);
        requireNonNull(args);
        final int len = 3 + keys.size() + args.size();
        final int scriptBytes = numberOfBytesUtf8(script);
        int keysCapacity = 0;
        if (keys instanceof List && keys instanceof RandomAccess) {
            final List<CharSequence> list = (List<CharSequence>) keys;
            for (int i = 0; i < list.size(); ++i) {
                final CharSequence arg = list.get(i);
                keysCapacity += estimateRequestArgumentSize(arg);
            }
        } else {
            for (CharSequence arg : keys) {
                keysCapacity += estimateRequestArgumentSize(arg);
            }
        }
        int argsCapacity = 0;
        if (args instanceof List && args instanceof RandomAccess) {
            final List<CharSequence> list = (List<CharSequence>) args;
            for (int i = 0; i < list.size(); ++i) {
                final CharSequence arg = list.get(i);
                argsCapacity += estimateRequestArgumentSize(arg);
            }
        } else {
            for (CharSequence arg : args) {
                argsCapacity += estimateRequestArgumentSize(arg);
            }
        }
        final int capacity = calculateInitialCommandBufferSize(len, RedisProtocolSupport.Command.EVAL) +
                    calculateRequestArgumentSize(scriptBytes) + calculateRequestArgumentSize(numkeys) + keysCapacity +
                    argsCapacity;
        Buffer buffer = reservedCnx.executionContext().bufferAllocator().newBuffer(capacity);
        writeRequestArraySize(buffer, len);
        RedisProtocolSupport.Command.EVAL.encodeTo(buffer);
        writeRequestArgument(buffer, script, scriptBytes);
        writeRequestArgument(buffer, numkeys);
        if (keys instanceof List && keys instanceof RandomAccess) {
            final List<CharSequence> list = (List<CharSequence>) keys;
            for (int i = 0; i < list.size(); ++i) {
                final CharSequence arg = list.get(i);
                writeRequestArgument(buffer, arg);
            }
        } else {
            for (CharSequence arg : keys) {
                writeRequestArgument(buffer, arg);
            }
        }
        if (args instanceof List && args instanceof RandomAccess) {
            final List<CharSequence> list = (List<CharSequence>) args;
            for (int i = 0; i < list.size(); ++i) {
                final CharSequence arg = list.get(i);
                writeRequestArgument(buffer, arg);
            }
        } else {
            for (CharSequence arg : args) {
                writeRequestArgument(buffer, arg);
            }
        }
        final RedisRequest request = newRequest(RedisProtocolSupport.Command.EVAL, buffer);
        final Single<String> queued = reservedCnx.request(request, String.class);
        Future<Long> result = enqueueForExecute(state, singles, queued);
        return result;
    }

    @Override
    public Future<String> evalsha(final CharSequence sha1, final long numkeys,
                                  @RedisProtocolSupport.Key final Collection<? extends CharSequence> keys,
                                  final Collection<? extends CharSequence> args) {
        requireNonNull(sha1);
        requireNonNull(keys);
        requireNonNull(args);
        final int len = 3 + keys.size() + args.size();
        final int sha1Bytes = numberOfBytesUtf8(sha1);
        int keysCapacity = 0;
        if (keys instanceof List && keys instanceof RandomAccess) {
            final List<CharSequence> list = (List<CharSequence>) keys;
            for (int i = 0; i < list.size(); ++i) {
                final CharSequence arg = list.get(i);
                keysCapacity += estimateRequestArgumentSize(arg);
            }
        } else {
            for (CharSequence arg : keys) {
                keysCapacity += estimateRequestArgumentSize(arg);
            }
        }
        int argsCapacity = 0;
        if (args instanceof List && args instanceof RandomAccess) {
            final List<CharSequence> list = (List<CharSequence>) args;
            for (int i = 0; i < list.size(); ++i) {
                final CharSequence arg = list.get(i);
                argsCapacity += estimateRequestArgumentSize(arg);
            }
        } else {
            for (CharSequence arg : args) {
                argsCapacity += estimateRequestArgumentSize(arg);
            }
        }
        final int capacity = calculateInitialCommandBufferSize(len, RedisProtocolSupport.Command.EVALSHA) +
                    calculateRequestArgumentSize(sha1Bytes) + calculateRequestArgumentSize(numkeys) + keysCapacity +
                    argsCapacity;
        Buffer buffer = reservedCnx.executionContext().bufferAllocator().newBuffer(capacity);
        writeRequestArraySize(buffer, len);
        RedisProtocolSupport.Command.EVALSHA.encodeTo(buffer);
        writeRequestArgument(buffer, sha1, sha1Bytes);
        writeRequestArgument(buffer, numkeys);
        if (keys instanceof List && keys instanceof RandomAccess) {
            final List<CharSequence> list = (List<CharSequence>) keys;
            for (int i = 0; i < list.size(); ++i) {
                final CharSequence arg = list.get(i);
                writeRequestArgument(buffer, arg);
            }
        } else {
            for (CharSequence arg : keys) {
                writeRequestArgument(buffer, arg);
            }
        }
        if (args instanceof List && args instanceof RandomAccess) {
            final List<CharSequence> list = (List<CharSequence>) args;
            for (int i = 0; i < list.size(); ++i) {
                final CharSequence arg = list.get(i);
                writeRequestArgument(buffer, arg);
            }
        } else {
            for (CharSequence arg : args) {
                writeRequestArgument(buffer, arg);
            }
        }
        final RedisRequest request = newRequest(RedisProtocolSupport.Command.EVALSHA, buffer);
        final Single<String> queued = reservedCnx.request(request, String.class);
        Future<String> result = enqueueForExecute(state, singles, queued);
        return result;
    }

    @Override
    public <T> Future<List<T>> evalshaList(final CharSequence sha1, final long numkeys,
                                           @RedisProtocolSupport.Key final Collection<? extends CharSequence> keys,
                                           final Collection<? extends CharSequence> args) {
        requireNonNull(sha1);
        requireNonNull(keys);
        requireNonNull(args);
        final int len = 3 + keys.size() + args.size();
        final int sha1Bytes = numberOfBytesUtf8(sha1);
        int keysCapacity = 0;
        if (keys instanceof List && keys instanceof RandomAccess) {
            final List<CharSequence> list = (List<CharSequence>) keys;
            for (int i = 0; i < list.size(); ++i) {
                final CharSequence arg = list.get(i);
                keysCapacity += estimateRequestArgumentSize(arg);
            }
        } else {
            for (CharSequence arg : keys) {
                keysCapacity += estimateRequestArgumentSize(arg);
            }
        }
        int argsCapacity = 0;
        if (args instanceof List && args instanceof RandomAccess) {
            final List<CharSequence> list = (List<CharSequence>) args;
            for (int i = 0; i < list.size(); ++i) {
                final CharSequence arg = list.get(i);
                argsCapacity += estimateRequestArgumentSize(arg);
            }
        } else {
            for (CharSequence arg : args) {
                argsCapacity += estimateRequestArgumentSize(arg);
            }
        }
        final int capacity = calculateInitialCommandBufferSize(len, RedisProtocolSupport.Command.EVALSHA) +
                    calculateRequestArgumentSize(sha1Bytes) + calculateRequestArgumentSize(numkeys) + keysCapacity +
                    argsCapacity;
        Buffer buffer = reservedCnx.executionContext().bufferAllocator().newBuffer(capacity);
        writeRequestArraySize(buffer, len);
        RedisProtocolSupport.Command.EVALSHA.encodeTo(buffer);
        writeRequestArgument(buffer, sha1, sha1Bytes);
        writeRequestArgument(buffer, numkeys);
        if (keys instanceof List && keys instanceof RandomAccess) {
            final List<CharSequence> list = (List<CharSequence>) keys;
            for (int i = 0; i < list.size(); ++i) {
                final CharSequence arg = list.get(i);
                writeRequestArgument(buffer, arg);
            }
        } else {
            for (CharSequence arg : keys) {
                writeRequestArgument(buffer, arg);
            }
        }
        if (args instanceof List && args instanceof RandomAccess) {
            final List<CharSequence> list = (List<CharSequence>) args;
            for (int i = 0; i < list.size(); ++i) {
                final CharSequence arg = list.get(i);
                writeRequestArgument(buffer, arg);
            }
        } else {
            for (CharSequence arg : args) {
                writeRequestArgument(buffer, arg);
            }
        }
        final RedisRequest request = newRequest(RedisProtocolSupport.Command.EVALSHA, buffer);
        final Single<String> queued = reservedCnx.request(request, String.class);
        Future<List<T>> result = enqueueForExecute(state, singles, queued);
        return result;
    }

    @Override
    public Future<Long> evalshaLong(final CharSequence sha1, final long numkeys,
                                    @RedisProtocolSupport.Key final Collection<? extends CharSequence> keys,
                                    final Collection<? extends CharSequence> args) {
        requireNonNull(sha1);
        requireNonNull(keys);
        requireNonNull(args);
        final int len = 3 + keys.size() + args.size();
        final int sha1Bytes = numberOfBytesUtf8(sha1);
        int keysCapacity = 0;
        if (keys instanceof List && keys instanceof RandomAccess) {
            final List<CharSequence> list = (List<CharSequence>) keys;
            for (int i = 0; i < list.size(); ++i) {
                final CharSequence arg = list.get(i);
                keysCapacity += estimateRequestArgumentSize(arg);
            }
        } else {
            for (CharSequence arg : keys) {
                keysCapacity += estimateRequestArgumentSize(arg);
            }
        }
        int argsCapacity = 0;
        if (args instanceof List && args instanceof RandomAccess) {
            final List<CharSequence> list = (List<CharSequence>) args;
            for (int i = 0; i < list.size(); ++i) {
                final CharSequence arg = list.get(i);
                argsCapacity += estimateRequestArgumentSize(arg);
            }
        } else {
            for (CharSequence arg : args) {
                argsCapacity += estimateRequestArgumentSize(arg);
            }
        }
        final int capacity = calculateInitialCommandBufferSize(len, RedisProtocolSupport.Command.EVALSHA) +
                    calculateRequestArgumentSize(sha1Bytes) + calculateRequestArgumentSize(numkeys) + keysCapacity +
                    argsCapacity;
        Buffer buffer = reservedCnx.executionContext().bufferAllocator().newBuffer(capacity);
        writeRequestArraySize(buffer, len);
        RedisProtocolSupport.Command.EVALSHA.encodeTo(buffer);
        writeRequestArgument(buffer, sha1, sha1Bytes);
        writeRequestArgument(buffer, numkeys);
        if (keys instanceof List && keys instanceof RandomAccess) {
            final List<CharSequence> list = (List<CharSequence>) keys;
            for (int i = 0; i < list.size(); ++i) {
                final CharSequence arg = list.get(i);
                writeRequestArgument(buffer, arg);
            }
        } else {
            for (CharSequence arg : keys) {
                writeRequestArgument(buffer, arg);
            }
        }
        if (args instanceof List && args instanceof RandomAccess) {
            final List<CharSequence> list = (List<CharSequence>) args;
            for (int i = 0; i < list.size(); ++i) {
                final CharSequence arg = list.get(i);
                writeRequestArgument(buffer, arg);
            }
        } else {
            for (CharSequence arg : args) {
                writeRequestArgument(buffer, arg);
            }
        }
        final RedisRequest request = newRequest(RedisProtocolSupport.Command.EVALSHA, buffer);
        final Single<String> queued = reservedCnx.request(request, String.class);
        Future<Long> result = enqueueForExecute(state, singles, queued);
        return result;
    }

    @Override
    public Completable exec() {
        final int len = 1;
        final int capacity = calculateInitialCommandBufferSize(len, RedisProtocolSupport.Command.EXEC);
        Buffer buffer = reservedCnx.executionContext().bufferAllocator().newBuffer(capacity);
        writeRequestArraySize(buffer, len);
        RedisProtocolSupport.Command.EXEC.encodeTo(buffer);
        final RedisRequest request = newRequest(RedisProtocolSupport.Command.EXEC, buffer);
        final Single<List<Object>> queued = (Single) reservedCnx.request(request,
                    RedisUtils.ListWithBuffersCoercedToCharSequences.class);
        final Completable result = new ExecCompletable<>(this, queued, singles, stateUpdater, reservedCnx,
                    releaseAfterDone);
        return result;
    }

    @Override
    public Future<Long> exists(@RedisProtocolSupport.Key final CharSequence key) {
        requireNonNull(key);
        final int len = 2;
        final int keyBytes = numberOfBytesUtf8(key);
        final int capacity = calculateInitialCommandBufferSize(len, RedisProtocolSupport.Command.EXISTS) +
                    calculateRequestArgumentSize(keyBytes);
        Buffer buffer = reservedCnx.executionContext().bufferAllocator().newBuffer(capacity);
        writeRequestArraySize(buffer, len);
        RedisProtocolSupport.Command.EXISTS.encodeTo(buffer);
        writeRequestArgument(buffer, key, keyBytes);
        final RedisRequest request = newRequest(RedisProtocolSupport.Command.EXISTS, buffer);
        final Single<String> queued = reservedCnx.request(request, String.class);
        Future<Long> result = enqueueForExecute(state, singles, queued);
        return result;
    }

    @Override
    public Future<Long> exists(@RedisProtocolSupport.Key final CharSequence key1,
                               @RedisProtocolSupport.Key final CharSequence key2) {
        requireNonNull(key1);
        requireNonNull(key2);
        final int len = 3;
        final int key1Bytes = numberOfBytesUtf8(key1);
        final int key2Bytes = numberOfBytesUtf8(key2);
        final int capacity = calculateInitialCommandBufferSize(len, RedisProtocolSupport.Command.EXISTS) +
                    calculateRequestArgumentSize(key1Bytes) + calculateRequestArgumentSize(key2Bytes);
        Buffer buffer = reservedCnx.executionContext().bufferAllocator().newBuffer(capacity);
        writeRequestArraySize(buffer, len);
        RedisProtocolSupport.Command.EXISTS.encodeTo(buffer);
        writeRequestArgument(buffer, key1, key1Bytes);
        writeRequestArgument(buffer, key2, key2Bytes);
        final RedisRequest request = newRequest(RedisProtocolSupport.Command.EXISTS, buffer);
        final Single<String> queued = reservedCnx.request(request, String.class);
        Future<Long> result = enqueueForExecute(state, singles, queued);
        return result;
    }

    @Override
    public Future<Long> exists(@RedisProtocolSupport.Key final CharSequence key1,
                               @RedisProtocolSupport.Key final CharSequence key2,
                               @RedisProtocolSupport.Key final CharSequence key3) {
        requireNonNull(key1);
        requireNonNull(key2);
        requireNonNull(key3);
        final int len = 4;
        final int key1Bytes = numberOfBytesUtf8(key1);
        final int key2Bytes = numberOfBytesUtf8(key2);
        final int key3Bytes = numberOfBytesUtf8(key3);
        final int capacity = calculateInitialCommandBufferSize(len, RedisProtocolSupport.Command.EXISTS) +
                    calculateRequestArgumentSize(key1Bytes) + calculateRequestArgumentSize(key2Bytes) +
                    calculateRequestArgumentSize(key3Bytes);
        Buffer buffer = reservedCnx.executionContext().bufferAllocator().newBuffer(capacity);
        writeRequestArraySize(buffer, len);
        RedisProtocolSupport.Command.EXISTS.encodeTo(buffer);
        writeRequestArgument(buffer, key1, key1Bytes);
        writeRequestArgument(buffer, key2, key2Bytes);
        writeRequestArgument(buffer, key3, key3Bytes);
        final RedisRequest request = newRequest(RedisProtocolSupport.Command.EXISTS, buffer);
        final Single<String> queued = reservedCnx.request(request, String.class);
        Future<Long> result = enqueueForExecute(state, singles, queued);
        return result;
    }

    @Override
    public Future<Long> exists(@RedisProtocolSupport.Key final Collection<? extends CharSequence> keys) {
        requireNonNull(keys);
        final int len = 1 + keys.size();
        int keysCapacity = 0;
        if (keys instanceof List && keys instanceof RandomAccess) {
            final List<CharSequence> list = (List<CharSequence>) keys;
            for (int i = 0; i < list.size(); ++i) {
                final CharSequence arg = list.get(i);
                keysCapacity += estimateRequestArgumentSize(arg);
            }
        } else {
            for (CharSequence arg : keys) {
                keysCapacity += estimateRequestArgumentSize(arg);
            }
        }
        final int capacity = calculateInitialCommandBufferSize(len, RedisProtocolSupport.Command.EXISTS) + keysCapacity;
        Buffer buffer = reservedCnx.executionContext().bufferAllocator().newBuffer(capacity);
        writeRequestArraySize(buffer, len);
        RedisProtocolSupport.Command.EXISTS.encodeTo(buffer);
        if (keys instanceof List && keys instanceof RandomAccess) {
            final List<CharSequence> list = (List<CharSequence>) keys;
            for (int i = 0; i < list.size(); ++i) {
                final CharSequence arg = list.get(i);
                writeRequestArgument(buffer, arg);
            }
        } else {
            for (CharSequence arg : keys) {
                writeRequestArgument(buffer, arg);
            }
        }
        final RedisRequest request = newRequest(RedisProtocolSupport.Command.EXISTS, buffer);
        final Single<String> queued = reservedCnx.request(request, String.class);
        Future<Long> result = enqueueForExecute(state, singles, queued);
        return result;
    }

    @Override
    public Future<Long> expire(@RedisProtocolSupport.Key final CharSequence key, final long seconds) {
        requireNonNull(key);
        final int len = 3;
        final int keyBytes = numberOfBytesUtf8(key);
        final int capacity = calculateInitialCommandBufferSize(len, RedisProtocolSupport.Command.EXPIRE) +
                    calculateRequestArgumentSize(keyBytes) + calculateRequestArgumentSize(seconds);
        Buffer buffer = reservedCnx.executionContext().bufferAllocator().newBuffer(capacity);
        writeRequestArraySize(buffer, len);
        RedisProtocolSupport.Command.EXPIRE.encodeTo(buffer);
        writeRequestArgument(buffer, key, keyBytes);
        writeRequestArgument(buffer, seconds);
        final RedisRequest request = newRequest(RedisProtocolSupport.Command.EXPIRE, buffer);
        final Single<String> queued = reservedCnx.request(request, String.class);
        Future<Long> result = enqueueForExecute(state, singles, queued);
        return result;
    }

    @Override
    public Future<Long> expireat(@RedisProtocolSupport.Key final CharSequence key, final long timestamp) {
        requireNonNull(key);
        final int len = 3;
        final int keyBytes = numberOfBytesUtf8(key);
        final int capacity = calculateInitialCommandBufferSize(len, RedisProtocolSupport.Command.EXPIREAT) +
                    calculateRequestArgumentSize(keyBytes) + calculateRequestArgumentSize(timestamp);
        Buffer buffer = reservedCnx.executionContext().bufferAllocator().newBuffer(capacity);
        writeRequestArraySize(buffer, len);
        RedisProtocolSupport.Command.EXPIREAT.encodeTo(buffer);
        writeRequestArgument(buffer, key, keyBytes);
        writeRequestArgument(buffer, timestamp);
        final RedisRequest request = newRequest(RedisProtocolSupport.Command.EXPIREAT, buffer);
        final Single<String> queued = reservedCnx.request(request, String.class);
        Future<Long> result = enqueueForExecute(state, singles, queued);
        return result;
    }

    @Override
    public Future<String> flushall() {
        final int len = 1;
        final int capacity = calculateInitialCommandBufferSize(len, RedisProtocolSupport.Command.FLUSHALL);
        Buffer buffer = reservedCnx.executionContext().bufferAllocator().newBuffer(capacity);
        writeRequestArraySize(buffer, len);
        RedisProtocolSupport.Command.FLUSHALL.encodeTo(buffer);
        final RedisRequest request = newRequest(RedisProtocolSupport.Command.FLUSHALL, buffer);
        final Single<String> queued = reservedCnx.request(request, String.class);
        Future<String> result = enqueueForExecute(state, singles, queued);
        return result;
    }

    @Override
    public Future<String> flushall(@Nullable final RedisProtocolSupport.FlushallAsync async) {
        final int len = 1 + (async == null ? 0 : 1);
        final int capacity = calculateInitialCommandBufferSize(len, RedisProtocolSupport.Command.FLUSHALL) +
                    (async == null ? 0 : async.encodedByteCount());
        Buffer buffer = reservedCnx.executionContext().bufferAllocator().newBuffer(capacity);
        writeRequestArraySize(buffer, len);
        RedisProtocolSupport.Command.FLUSHALL.encodeTo(buffer);
        if (async != null) {
            async.encodeTo(buffer);
        }
        final RedisRequest request = newRequest(RedisProtocolSupport.Command.FLUSHALL, buffer);
        final Single<String> queued = reservedCnx.request(request, String.class);
        Future<String> result = enqueueForExecute(state, singles, queued);
        return result;
    }

    @Override
    public Future<String> flushdb() {
        final int len = 1;
        final int capacity = calculateInitialCommandBufferSize(len, RedisProtocolSupport.Command.FLUSHDB);
        Buffer buffer = reservedCnx.executionContext().bufferAllocator().newBuffer(capacity);
        writeRequestArraySize(buffer, len);
        RedisProtocolSupport.Command.FLUSHDB.encodeTo(buffer);
        final RedisRequest request = newRequest(RedisProtocolSupport.Command.FLUSHDB, buffer);
        final Single<String> queued = reservedCnx.request(request, String.class);
        Future<String> result = enqueueForExecute(state, singles, queued);
        return result;
    }

    @Override
    public Future<String> flushdb(@Nullable final RedisProtocolSupport.FlushdbAsync async) {
        final int len = 1 + (async == null ? 0 : 1);
        final int capacity = calculateInitialCommandBufferSize(len, RedisProtocolSupport.Command.FLUSHDB) +
                    (async == null ? 0 : async.encodedByteCount());
        Buffer buffer = reservedCnx.executionContext().bufferAllocator().newBuffer(capacity);
        writeRequestArraySize(buffer, len);
        RedisProtocolSupport.Command.FLUSHDB.encodeTo(buffer);
        if (async != null) {
            async.encodeTo(buffer);
        }
        final RedisRequest request = newRequest(RedisProtocolSupport.Command.FLUSHDB, buffer);
        final Single<String> queued = reservedCnx.request(request, String.class);
        Future<String> result = enqueueForExecute(state, singles, queued);
        return result;
    }

    @Override
    public Future<Long> geoadd(@RedisProtocolSupport.Key final CharSequence key, final double longitude,
                               final double latitude, final CharSequence member) {
        requireNonNull(key);
        requireNonNull(member);
        final int len = 5;
        final int keyBytes = numberOfBytesUtf8(key);
        final int memberBytes = numberOfBytesUtf8(member);
        final int capacity = calculateInitialCommandBufferSize(len, RedisProtocolSupport.Command.GEOADD) +
                    calculateRequestArgumentSize(keyBytes) + calculateRequestArgumentSize(longitude) +
                    calculateRequestArgumentSize(latitude) + calculateRequestArgumentSize(memberBytes);
        Buffer buffer = reservedCnx.executionContext().bufferAllocator().newBuffer(capacity);
        writeRequestArraySize(buffer, len);
        RedisProtocolSupport.Command.GEOADD.encodeTo(buffer);
        writeRequestArgument(buffer, key, keyBytes);
        writeRequestArgument(buffer, longitude);
        writeRequestArgument(buffer, latitude);
        writeRequestArgument(buffer, member, memberBytes);
        final RedisRequest request = newRequest(RedisProtocolSupport.Command.GEOADD, buffer);
        final Single<String> queued = reservedCnx.request(request, String.class);
        Future<Long> result = enqueueForExecute(state, singles, queued);
        return result;
    }

    @Override
    public Future<Long> geoadd(@RedisProtocolSupport.Key final CharSequence key, final double longitude1,
                               final double latitude1, final CharSequence member1, final double longitude2,
                               final double latitude2, final CharSequence member2) {
        requireNonNull(key);
        requireNonNull(member1);
        requireNonNull(member2);
        final int len = 8;
        final int keyBytes = numberOfBytesUtf8(key);
        final int member1Bytes = numberOfBytesUtf8(member1);
        final int member2Bytes = numberOfBytesUtf8(member2);
        final int capacity = calculateInitialCommandBufferSize(len, RedisProtocolSupport.Command.GEOADD) +
                    calculateRequestArgumentSize(keyBytes) + calculateRequestArgumentSize(longitude1) +
                    calculateRequestArgumentSize(latitude1) + calculateRequestArgumentSize(member1Bytes) +
                    calculateRequestArgumentSize(longitude2) + calculateRequestArgumentSize(latitude2) +
                    calculateRequestArgumentSize(member2Bytes);
        Buffer buffer = reservedCnx.executionContext().bufferAllocator().newBuffer(capacity);
        writeRequestArraySize(buffer, len);
        RedisProtocolSupport.Command.GEOADD.encodeTo(buffer);
        writeRequestArgument(buffer, key, keyBytes);
        writeRequestArgument(buffer, longitude1);
        writeRequestArgument(buffer, latitude1);
        writeRequestArgument(buffer, member1, member1Bytes);
        writeRequestArgument(buffer, longitude2);
        writeRequestArgument(buffer, latitude2);
        writeRequestArgument(buffer, member2, member2Bytes);
        final RedisRequest request = newRequest(RedisProtocolSupport.Command.GEOADD, buffer);
        final Single<String> queued = reservedCnx.request(request, String.class);
        Future<Long> result = enqueueForExecute(state, singles, queued);
        return result;
    }

    @Override
    public Future<Long> geoadd(@RedisProtocolSupport.Key final CharSequence key, final double longitude1,
                               final double latitude1, final CharSequence member1, final double longitude2,
                               final double latitude2, final CharSequence member2, final double longitude3,
                               final double latitude3, final CharSequence member3) {
        requireNonNull(key);
        requireNonNull(member1);
        requireNonNull(member2);
        requireNonNull(member3);
        final int len = 11;
        final int keyBytes = numberOfBytesUtf8(key);
        final int member1Bytes = numberOfBytesUtf8(member1);
        final int member2Bytes = numberOfBytesUtf8(member2);
        final int member3Bytes = numberOfBytesUtf8(member3);
        final int capacity = calculateInitialCommandBufferSize(len, RedisProtocolSupport.Command.GEOADD) +
                    calculateRequestArgumentSize(keyBytes) + calculateRequestArgumentSize(longitude1) +
                    calculateRequestArgumentSize(latitude1) + calculateRequestArgumentSize(member1Bytes) +
                    calculateRequestArgumentSize(longitude2) + calculateRequestArgumentSize(latitude2) +
                    calculateRequestArgumentSize(member2Bytes) + calculateRequestArgumentSize(longitude3) +
                    calculateRequestArgumentSize(latitude3) + calculateRequestArgumentSize(member3Bytes);
        Buffer buffer = reservedCnx.executionContext().bufferAllocator().newBuffer(capacity);
        writeRequestArraySize(buffer, len);
        RedisProtocolSupport.Command.GEOADD.encodeTo(buffer);
        writeRequestArgument(buffer, key, keyBytes);
        writeRequestArgument(buffer, longitude1);
        writeRequestArgument(buffer, latitude1);
        writeRequestArgument(buffer, member1, member1Bytes);
        writeRequestArgument(buffer, longitude2);
        writeRequestArgument(buffer, latitude2);
        writeRequestArgument(buffer, member2, member2Bytes);
        writeRequestArgument(buffer, longitude3);
        writeRequestArgument(buffer, latitude3);
        writeRequestArgument(buffer, member3, member3Bytes);
        final RedisRequest request = newRequest(RedisProtocolSupport.Command.GEOADD, buffer);
        final Single<String> queued = reservedCnx.request(request, String.class);
        Future<Long> result = enqueueForExecute(state, singles, queued);
        return result;
    }

    @Override
    public Future<Long> geoadd(@RedisProtocolSupport.Key final CharSequence key,
                               final Collection<RedisProtocolSupport.LongitudeLatitudeMember> longitudeLatitudeMembers) {
        requireNonNull(key);
        requireNonNull(longitudeLatitudeMembers);
        final int len = 2 + RedisProtocolSupport.LongitudeLatitudeMember.SIZE * longitudeLatitudeMembers.size();
        final int keyBytes = numberOfBytesUtf8(key);
        int longitudeLatitudeMembersCapacity = 0;
        if (longitudeLatitudeMembers instanceof List && longitudeLatitudeMembers instanceof RandomAccess) {
            final List<RedisProtocolSupport.LongitudeLatitudeMember> list = (List<RedisProtocolSupport.LongitudeLatitudeMember>) longitudeLatitudeMembers;
            for (int i = 0; i < list.size(); ++i) {
                final RedisProtocolSupport.LongitudeLatitudeMember arg = list.get(i);
                longitudeLatitudeMembersCapacity += arg.encodedByteCount();
            }
        } else {
            for (RedisProtocolSupport.LongitudeLatitudeMember arg : longitudeLatitudeMembers) {
                longitudeLatitudeMembersCapacity += arg.encodedByteCount();
            }
        }
        final int capacity = calculateInitialCommandBufferSize(len, RedisProtocolSupport.Command.GEOADD) +
                    calculateRequestArgumentSize(keyBytes) + longitudeLatitudeMembersCapacity;
        Buffer buffer = reservedCnx.executionContext().bufferAllocator().newBuffer(capacity);
        writeRequestArraySize(buffer, len);
        RedisProtocolSupport.Command.GEOADD.encodeTo(buffer);
        writeRequestArgument(buffer, key, keyBytes);
        if (longitudeLatitudeMembers instanceof List && longitudeLatitudeMembers instanceof RandomAccess) {
            final List<RedisProtocolSupport.LongitudeLatitudeMember> list = (List<RedisProtocolSupport.LongitudeLatitudeMember>) longitudeLatitudeMembers;
            for (int i = 0; i < list.size(); ++i) {
                final RedisProtocolSupport.LongitudeLatitudeMember arg = list.get(i);
                arg.encodeTo(buffer);
            }
        } else {
            for (RedisProtocolSupport.LongitudeLatitudeMember arg : longitudeLatitudeMembers) {
                arg.encodeTo(buffer);
            }
        }
        final RedisRequest request = newRequest(RedisProtocolSupport.Command.GEOADD, buffer);
        final Single<String> queued = reservedCnx.request(request, String.class);
        Future<Long> result = enqueueForExecute(state, singles, queued);
        return result;
    }

    @Override
    public Future<Double> geodist(@RedisProtocolSupport.Key final CharSequence key, final CharSequence member1,
                                  final CharSequence member2) {
        requireNonNull(key);
        requireNonNull(member1);
        requireNonNull(member2);
        final int len = 4;
        final int keyBytes = numberOfBytesUtf8(key);
        final int member1Bytes = numberOfBytesUtf8(member1);
        final int member2Bytes = numberOfBytesUtf8(member2);
        final int capacity = calculateInitialCommandBufferSize(len, RedisProtocolSupport.Command.GEODIST) +
                    calculateRequestArgumentSize(keyBytes) + calculateRequestArgumentSize(member1Bytes) +
                    calculateRequestArgumentSize(member2Bytes);
        Buffer buffer = reservedCnx.executionContext().bufferAllocator().newBuffer(capacity);
        writeRequestArraySize(buffer, len);
        RedisProtocolSupport.Command.GEODIST.encodeTo(buffer);
        writeRequestArgument(buffer, key, keyBytes);
        writeRequestArgument(buffer, member1, member1Bytes);
        writeRequestArgument(buffer, member2, member2Bytes);
        final RedisRequest request = newRequest(RedisProtocolSupport.Command.GEODIST, buffer);
        final Single<String> queued = reservedCnx.request(request, String.class);
        Future<Double> result = enqueueForExecute(state, singles, queued);
        return result;
    }

    @Override
    public Future<Double> geodist(@RedisProtocolSupport.Key final CharSequence key, final CharSequence member1,
                                  final CharSequence member2, @Nullable final CharSequence unit) {
        requireNonNull(key);
        requireNonNull(member1);
        requireNonNull(member2);
        final int len = 4 + (unit == null ? 0 : 1);
        final int keyBytes = numberOfBytesUtf8(key);
        final int member1Bytes = numberOfBytesUtf8(member1);
        final int member2Bytes = numberOfBytesUtf8(member2);
        final int unitBytes = unit == null ? 0 : numberOfBytesUtf8(unit);
        final int capacity = calculateInitialCommandBufferSize(len, RedisProtocolSupport.Command.GEODIST) +
                    calculateRequestArgumentSize(keyBytes) + calculateRequestArgumentSize(member1Bytes) +
                    calculateRequestArgumentSize(member2Bytes) +
                    (unit == null ? 0 : calculateRequestArgumentSize(unitBytes));
        Buffer buffer = reservedCnx.executionContext().bufferAllocator().newBuffer(capacity);
        writeRequestArraySize(buffer, len);
        RedisProtocolSupport.Command.GEODIST.encodeTo(buffer);
        writeRequestArgument(buffer, key, keyBytes);
        writeRequestArgument(buffer, member1, member1Bytes);
        writeRequestArgument(buffer, member2, member2Bytes);
        if (unit != null) {
            writeRequestArgument(buffer, unit, unitBytes);
        }
        final RedisRequest request = newRequest(RedisProtocolSupport.Command.GEODIST, buffer);
        final Single<String> queued = reservedCnx.request(request, String.class);
        Future<Double> result = enqueueForExecute(state, singles, queued);
        return result;
    }

    @Override
    public <T> Future<List<T>> geohash(@RedisProtocolSupport.Key final CharSequence key, final CharSequence member) {
        requireNonNull(key);
        requireNonNull(member);
        final int len = 3;
        final int keyBytes = numberOfBytesUtf8(key);
        final int memberBytes = numberOfBytesUtf8(member);
        final int capacity = calculateInitialCommandBufferSize(len, RedisProtocolSupport.Command.GEOHASH) +
                    calculateRequestArgumentSize(keyBytes) + calculateRequestArgumentSize(memberBytes);
        Buffer buffer = reservedCnx.executionContext().bufferAllocator().newBuffer(capacity);
        writeRequestArraySize(buffer, len);
        RedisProtocolSupport.Command.GEOHASH.encodeTo(buffer);
        writeRequestArgument(buffer, key, keyBytes);
        writeRequestArgument(buffer, member, memberBytes);
        final RedisRequest request = newRequest(RedisProtocolSupport.Command.GEOHASH, buffer);
        final Single<String> queued = reservedCnx.request(request, String.class);
        Future<List<T>> result = enqueueForExecute(state, singles, queued);
        return result;
    }

    @Override
    public <T> Future<List<T>> geohash(@RedisProtocolSupport.Key final CharSequence key, final CharSequence member1,
                                       final CharSequence member2) {
        requireNonNull(key);
        requireNonNull(member1);
        requireNonNull(member2);
        final int len = 4;
        final int keyBytes = numberOfBytesUtf8(key);
        final int member1Bytes = numberOfBytesUtf8(member1);
        final int member2Bytes = numberOfBytesUtf8(member2);
        final int capacity = calculateInitialCommandBufferSize(len, RedisProtocolSupport.Command.GEOHASH) +
                    calculateRequestArgumentSize(keyBytes) + calculateRequestArgumentSize(member1Bytes) +
                    calculateRequestArgumentSize(member2Bytes);
        Buffer buffer = reservedCnx.executionContext().bufferAllocator().newBuffer(capacity);
        writeRequestArraySize(buffer, len);
        RedisProtocolSupport.Command.GEOHASH.encodeTo(buffer);
        writeRequestArgument(buffer, key, keyBytes);
        writeRequestArgument(buffer, member1, member1Bytes);
        writeRequestArgument(buffer, member2, member2Bytes);
        final RedisRequest request = newRequest(RedisProtocolSupport.Command.GEOHASH, buffer);
        final Single<String> queued = reservedCnx.request(request, String.class);
        Future<List<T>> result = enqueueForExecute(state, singles, queued);
        return result;
    }

    @Override
    public <T> Future<List<T>> geohash(@RedisProtocolSupport.Key final CharSequence key, final CharSequence member1,
                                       final CharSequence member2, final CharSequence member3) {
        requireNonNull(key);
        requireNonNull(member1);
        requireNonNull(member2);
        requireNonNull(member3);
        final int len = 5;
        final int keyBytes = numberOfBytesUtf8(key);
        final int member1Bytes = numberOfBytesUtf8(member1);
        final int member2Bytes = numberOfBytesUtf8(member2);
        final int member3Bytes = numberOfBytesUtf8(member3);
        final int capacity = calculateInitialCommandBufferSize(len, RedisProtocolSupport.Command.GEOHASH) +
                    calculateRequestArgumentSize(keyBytes) + calculateRequestArgumentSize(member1Bytes) +
                    calculateRequestArgumentSize(member2Bytes) + calculateRequestArgumentSize(member3Bytes);
        Buffer buffer = reservedCnx.executionContext().bufferAllocator().newBuffer(capacity);
        writeRequestArraySize(buffer, len);
        RedisProtocolSupport.Command.GEOHASH.encodeTo(buffer);
        writeRequestArgument(buffer, key, keyBytes);
        writeRequestArgument(buffer, member1, member1Bytes);
        writeRequestArgument(buffer, member2, member2Bytes);
        writeRequestArgument(buffer, member3, member3Bytes);
        final RedisRequest request = newRequest(RedisProtocolSupport.Command.GEOHASH, buffer);
        final Single<String> queued = reservedCnx.request(request, String.class);
        Future<List<T>> result = enqueueForExecute(state, singles, queued);
        return result;
    }

    @Override
    public <T> Future<List<T>> geohash(@RedisProtocolSupport.Key final CharSequence key,
                                       final Collection<? extends CharSequence> members) {
        requireNonNull(key);
        requireNonNull(members);
        final int len = 2 + members.size();
        final int keyBytes = numberOfBytesUtf8(key);
        int membersCapacity = 0;
        if (members instanceof List && members instanceof RandomAccess) {
            final List<CharSequence> list = (List<CharSequence>) members;
            for (int i = 0; i < list.size(); ++i) {
                final CharSequence arg = list.get(i);
                membersCapacity += estimateRequestArgumentSize(arg);
            }
        } else {
            for (CharSequence arg : members) {
                membersCapacity += estimateRequestArgumentSize(arg);
            }
        }
        final int capacity = calculateInitialCommandBufferSize(len, RedisProtocolSupport.Command.GEOHASH) +
                    calculateRequestArgumentSize(keyBytes) + membersCapacity;
        Buffer buffer = reservedCnx.executionContext().bufferAllocator().newBuffer(capacity);
        writeRequestArraySize(buffer, len);
        RedisProtocolSupport.Command.GEOHASH.encodeTo(buffer);
        writeRequestArgument(buffer, key, keyBytes);
        if (members instanceof List && members instanceof RandomAccess) {
            final List<CharSequence> list = (List<CharSequence>) members;
            for (int i = 0; i < list.size(); ++i) {
                final CharSequence arg = list.get(i);
                writeRequestArgument(buffer, arg);
            }
        } else {
            for (CharSequence arg : members) {
                writeRequestArgument(buffer, arg);
            }
        }
        final RedisRequest request = newRequest(RedisProtocolSupport.Command.GEOHASH, buffer);
        final Single<String> queued = reservedCnx.request(request, String.class);
        Future<List<T>> result = enqueueForExecute(state, singles, queued);
        return result;
    }

    @Override
    public <T> Future<List<T>> geopos(@RedisProtocolSupport.Key final CharSequence key, final CharSequence member) {
        requireNonNull(key);
        requireNonNull(member);
        final int len = 3;
        final int keyBytes = numberOfBytesUtf8(key);
        final int memberBytes = numberOfBytesUtf8(member);
        final int capacity = calculateInitialCommandBufferSize(len, RedisProtocolSupport.Command.GEOPOS) +
                    calculateRequestArgumentSize(keyBytes) + calculateRequestArgumentSize(memberBytes);
        Buffer buffer = reservedCnx.executionContext().bufferAllocator().newBuffer(capacity);
        writeRequestArraySize(buffer, len);
        RedisProtocolSupport.Command.GEOPOS.encodeTo(buffer);
        writeRequestArgument(buffer, key, keyBytes);
        writeRequestArgument(buffer, member, memberBytes);
        final RedisRequest request = newRequest(RedisProtocolSupport.Command.GEOPOS, buffer);
        final Single<String> queued = reservedCnx.request(request, String.class);
        Future<List<T>> result = enqueueForExecute(state, singles, queued);
        return result;
    }

    @Override
    public <T> Future<List<T>> geopos(@RedisProtocolSupport.Key final CharSequence key, final CharSequence member1,
                                      final CharSequence member2) {
        requireNonNull(key);
        requireNonNull(member1);
        requireNonNull(member2);
        final int len = 4;
        final int keyBytes = numberOfBytesUtf8(key);
        final int member1Bytes = numberOfBytesUtf8(member1);
        final int member2Bytes = numberOfBytesUtf8(member2);
        final int capacity = calculateInitialCommandBufferSize(len, RedisProtocolSupport.Command.GEOPOS) +
                    calculateRequestArgumentSize(keyBytes) + calculateRequestArgumentSize(member1Bytes) +
                    calculateRequestArgumentSize(member2Bytes);
        Buffer buffer = reservedCnx.executionContext().bufferAllocator().newBuffer(capacity);
        writeRequestArraySize(buffer, len);
        RedisProtocolSupport.Command.GEOPOS.encodeTo(buffer);
        writeRequestArgument(buffer, key, keyBytes);
        writeRequestArgument(buffer, member1, member1Bytes);
        writeRequestArgument(buffer, member2, member2Bytes);
        final RedisRequest request = newRequest(RedisProtocolSupport.Command.GEOPOS, buffer);
        final Single<String> queued = reservedCnx.request(request, String.class);
        Future<List<T>> result = enqueueForExecute(state, singles, queued);
        return result;
    }

    @Override
    public <T> Future<List<T>> geopos(@RedisProtocolSupport.Key final CharSequence key, final CharSequence member1,
                                      final CharSequence member2, final CharSequence member3) {
        requireNonNull(key);
        requireNonNull(member1);
        requireNonNull(member2);
        requireNonNull(member3);
        final int len = 5;
        final int keyBytes = numberOfBytesUtf8(key);
        final int member1Bytes = numberOfBytesUtf8(member1);
        final int member2Bytes = numberOfBytesUtf8(member2);
        final int member3Bytes = numberOfBytesUtf8(member3);
        final int capacity = calculateInitialCommandBufferSize(len, RedisProtocolSupport.Command.GEOPOS) +
                    calculateRequestArgumentSize(keyBytes) + calculateRequestArgumentSize(member1Bytes) +
                    calculateRequestArgumentSize(member2Bytes) + calculateRequestArgumentSize(member3Bytes);
        Buffer buffer = reservedCnx.executionContext().bufferAllocator().newBuffer(capacity);
        writeRequestArraySize(buffer, len);
        RedisProtocolSupport.Command.GEOPOS.encodeTo(buffer);
        writeRequestArgument(buffer, key, keyBytes);
        writeRequestArgument(buffer, member1, member1Bytes);
        writeRequestArgument(buffer, member2, member2Bytes);
        writeRequestArgument(buffer, member3, member3Bytes);
        final RedisRequest request = newRequest(RedisProtocolSupport.Command.GEOPOS, buffer);
        final Single<String> queued = reservedCnx.request(request, String.class);
        Future<List<T>> result = enqueueForExecute(state, singles, queued);
        return result;
    }

    @Override
    public <T> Future<List<T>> geopos(@RedisProtocolSupport.Key final CharSequence key,
                                      final Collection<? extends CharSequence> members) {
        requireNonNull(key);
        requireNonNull(members);
        final int len = 2 + members.size();
        final int keyBytes = numberOfBytesUtf8(key);
        int membersCapacity = 0;
        if (members instanceof List && members instanceof RandomAccess) {
            final List<CharSequence> list = (List<CharSequence>) members;
            for (int i = 0; i < list.size(); ++i) {
                final CharSequence arg = list.get(i);
                membersCapacity += estimateRequestArgumentSize(arg);
            }
        } else {
            for (CharSequence arg : members) {
                membersCapacity += estimateRequestArgumentSize(arg);
            }
        }
        final int capacity = calculateInitialCommandBufferSize(len, RedisProtocolSupport.Command.GEOPOS) +
                    calculateRequestArgumentSize(keyBytes) + membersCapacity;
        Buffer buffer = reservedCnx.executionContext().bufferAllocator().newBuffer(capacity);
        writeRequestArraySize(buffer, len);
        RedisProtocolSupport.Command.GEOPOS.encodeTo(buffer);
        writeRequestArgument(buffer, key, keyBytes);
        if (members instanceof List && members instanceof RandomAccess) {
            final List<CharSequence> list = (List<CharSequence>) members;
            for (int i = 0; i < list.size(); ++i) {
                final CharSequence arg = list.get(i);
                writeRequestArgument(buffer, arg);
            }
        } else {
            for (CharSequence arg : members) {
                writeRequestArgument(buffer, arg);
            }
        }
        final RedisRequest request = newRequest(RedisProtocolSupport.Command.GEOPOS, buffer);
        final Single<String> queued = reservedCnx.request(request, String.class);
        Future<List<T>> result = enqueueForExecute(state, singles, queued);
        return result;
    }

    @Override
    public <T> Future<List<T>> georadius(@RedisProtocolSupport.Key final CharSequence key, final double longitude,
                                         final double latitude, final double radius,
                                         final RedisProtocolSupport.GeoradiusUnit unit) {
        requireNonNull(key);
        requireNonNull(unit);
        final int len = 6;
        final int keyBytes = numberOfBytesUtf8(key);
        final int capacity = calculateInitialCommandBufferSize(len, RedisProtocolSupport.Command.GEORADIUS) +
                    calculateRequestArgumentSize(keyBytes) + calculateRequestArgumentSize(longitude) +
                    calculateRequestArgumentSize(latitude) + calculateRequestArgumentSize(radius) +
                    unit.encodedByteCount();
        Buffer buffer = reservedCnx.executionContext().bufferAllocator().newBuffer(capacity);
        writeRequestArraySize(buffer, len);
        RedisProtocolSupport.Command.GEORADIUS.encodeTo(buffer);
        writeRequestArgument(buffer, key, keyBytes);
        writeRequestArgument(buffer, longitude);
        writeRequestArgument(buffer, latitude);
        writeRequestArgument(buffer, radius);
        unit.encodeTo(buffer);
        final RedisRequest request = newRequest(RedisProtocolSupport.Command.GEORADIUS, buffer);
        final Single<String> queued = reservedCnx.request(request, String.class);
        Future<List<T>> result = enqueueForExecute(state, singles, queued);
        return result;
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
        requireNonNull(key);
        requireNonNull(unit);
        final int len = 6 + (withcoord == null ? 0 : 1) + (withdist == null ? 0 : 1) + (withhash == null ? 0 : 1) +
                    (count == null ? 0 : 2) + (order == null ? 0 : 1) + (storeKey == null ? 0 : 2) +
                    (storedistKey == null ? 0 : 2);
        final int keyBytes = numberOfBytesUtf8(key);
        final int storeKeyBytes = storeKey == null ? 0 : numberOfBytesUtf8(storeKey);
        final int storedistKeyBytes = storedistKey == null ? 0 : numberOfBytesUtf8(storedistKey);
        final int capacity = calculateInitialCommandBufferSize(len, RedisProtocolSupport.Command.GEORADIUS) +
                    calculateRequestArgumentSize(keyBytes) + calculateRequestArgumentSize(longitude) +
                    calculateRequestArgumentSize(latitude) + calculateRequestArgumentSize(radius) +
                    unit.encodedByteCount() + (withcoord == null ? 0 : withcoord.encodedByteCount()) +
                    (withdist == null ? 0 : withdist.encodedByteCount()) +
                    (withhash == null ? 0 : withhash.encodedByteCount()) +
                    (count == null ? 0 : RedisProtocolSupport.SubCommand.COUNT.encodedByteCount()) +
                    (count == null ? 0 : calculateRequestArgumentSize(count)) +
                    (order == null ? 0 : order.encodedByteCount()) +
                    (storeKey == null ? 0 : RedisProtocolSupport.SubCommand.STORE.encodedByteCount()) +
                    (storeKey == null ? 0 : calculateRequestArgumentSize(storeKeyBytes)) +
                    (storedistKey == null ? 0 : RedisProtocolSupport.SubCommand.STOREDIST.encodedByteCount()) +
                    (storedistKey == null ? 0 : calculateRequestArgumentSize(storedistKeyBytes));
        Buffer buffer = reservedCnx.executionContext().bufferAllocator().newBuffer(capacity);
        writeRequestArraySize(buffer, len);
        RedisProtocolSupport.Command.GEORADIUS.encodeTo(buffer);
        writeRequestArgument(buffer, key, keyBytes);
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
            writeRequestArgument(buffer, storeKey, storeKeyBytes);
        }
        if (storedistKey != null) {
            RedisProtocolSupport.SubCommand.STOREDIST.encodeTo(buffer);
            writeRequestArgument(buffer, storedistKey, storedistKeyBytes);
        }
        final RedisRequest request = newRequest(RedisProtocolSupport.Command.GEORADIUS, buffer);
        final Single<String> queued = reservedCnx.request(request, String.class);
        Future<List<T>> result = enqueueForExecute(state, singles, queued);
        return result;
    }

    @Override
    public <T> Future<List<T>> georadiusbymember(@RedisProtocolSupport.Key final CharSequence key,
                                                 final CharSequence member, final double radius,
                                                 final RedisProtocolSupport.GeoradiusbymemberUnit unit) {
        requireNonNull(key);
        requireNonNull(member);
        requireNonNull(unit);
        final int len = 5;
        final int keyBytes = numberOfBytesUtf8(key);
        final int memberBytes = numberOfBytesUtf8(member);
        final int capacity = calculateInitialCommandBufferSize(len, RedisProtocolSupport.Command.GEORADIUSBYMEMBER) +
                    calculateRequestArgumentSize(keyBytes) + calculateRequestArgumentSize(memberBytes) +
                    calculateRequestArgumentSize(radius) + unit.encodedByteCount();
        Buffer buffer = reservedCnx.executionContext().bufferAllocator().newBuffer(capacity);
        writeRequestArraySize(buffer, len);
        RedisProtocolSupport.Command.GEORADIUSBYMEMBER.encodeTo(buffer);
        writeRequestArgument(buffer, key, keyBytes);
        writeRequestArgument(buffer, member, memberBytes);
        writeRequestArgument(buffer, radius);
        unit.encodeTo(buffer);
        final RedisRequest request = newRequest(RedisProtocolSupport.Command.GEORADIUSBYMEMBER, buffer);
        final Single<String> queued = reservedCnx.request(request, String.class);
        Future<List<T>> result = enqueueForExecute(state, singles, queued);
        return result;
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
        requireNonNull(key);
        requireNonNull(member);
        requireNonNull(unit);
        final int len = 5 + (withcoord == null ? 0 : 1) + (withdist == null ? 0 : 1) + (withhash == null ? 0 : 1) +
                    (count == null ? 0 : 2) + (order == null ? 0 : 1) + (storeKey == null ? 0 : 2) +
                    (storedistKey == null ? 0 : 2);
        final int keyBytes = numberOfBytesUtf8(key);
        final int memberBytes = numberOfBytesUtf8(member);
        final int storeKeyBytes = storeKey == null ? 0 : numberOfBytesUtf8(storeKey);
        final int storedistKeyBytes = storedistKey == null ? 0 : numberOfBytesUtf8(storedistKey);
        final int capacity = calculateInitialCommandBufferSize(len, RedisProtocolSupport.Command.GEORADIUSBYMEMBER) +
                    calculateRequestArgumentSize(keyBytes) + calculateRequestArgumentSize(memberBytes) +
                    calculateRequestArgumentSize(radius) + unit.encodedByteCount() +
                    (withcoord == null ? 0 : withcoord.encodedByteCount()) +
                    (withdist == null ? 0 : withdist.encodedByteCount()) +
                    (withhash == null ? 0 : withhash.encodedByteCount()) +
                    (count == null ? 0 : RedisProtocolSupport.SubCommand.COUNT.encodedByteCount()) +
                    (count == null ? 0 : calculateRequestArgumentSize(count)) +
                    (order == null ? 0 : order.encodedByteCount()) +
                    (storeKey == null ? 0 : RedisProtocolSupport.SubCommand.STORE.encodedByteCount()) +
                    (storeKey == null ? 0 : calculateRequestArgumentSize(storeKeyBytes)) +
                    (storedistKey == null ? 0 : RedisProtocolSupport.SubCommand.STOREDIST.encodedByteCount()) +
                    (storedistKey == null ? 0 : calculateRequestArgumentSize(storedistKeyBytes));
        Buffer buffer = reservedCnx.executionContext().bufferAllocator().newBuffer(capacity);
        writeRequestArraySize(buffer, len);
        RedisProtocolSupport.Command.GEORADIUSBYMEMBER.encodeTo(buffer);
        writeRequestArgument(buffer, key, keyBytes);
        writeRequestArgument(buffer, member, memberBytes);
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
            writeRequestArgument(buffer, storeKey, storeKeyBytes);
        }
        if (storedistKey != null) {
            RedisProtocolSupport.SubCommand.STOREDIST.encodeTo(buffer);
            writeRequestArgument(buffer, storedistKey, storedistKeyBytes);
        }
        final RedisRequest request = newRequest(RedisProtocolSupport.Command.GEORADIUSBYMEMBER, buffer);
        final Single<String> queued = reservedCnx.request(request, String.class);
        Future<List<T>> result = enqueueForExecute(state, singles, queued);
        return result;
    }

    @Override
    public Future<String> get(@RedisProtocolSupport.Key final CharSequence key) {
        requireNonNull(key);
        final int len = 2;
        final int keyBytes = numberOfBytesUtf8(key);
        final int capacity = calculateInitialCommandBufferSize(len, RedisProtocolSupport.Command.GET) +
                    calculateRequestArgumentSize(keyBytes);
        Buffer buffer = reservedCnx.executionContext().bufferAllocator().newBuffer(capacity);
        writeRequestArraySize(buffer, len);
        RedisProtocolSupport.Command.GET.encodeTo(buffer);
        writeRequestArgument(buffer, key, keyBytes);
        final RedisRequest request = newRequest(RedisProtocolSupport.Command.GET, buffer);
        final Single<String> queued = reservedCnx.request(request, String.class);
        Future<String> result = enqueueForExecute(state, singles, queued);
        return result;
    }

    @Override
    public Future<Long> getbit(@RedisProtocolSupport.Key final CharSequence key, final long offset) {
        requireNonNull(key);
        final int len = 3;
        final int keyBytes = numberOfBytesUtf8(key);
        final int capacity = calculateInitialCommandBufferSize(len, RedisProtocolSupport.Command.GETBIT) +
                    calculateRequestArgumentSize(keyBytes) + calculateRequestArgumentSize(offset);
        Buffer buffer = reservedCnx.executionContext().bufferAllocator().newBuffer(capacity);
        writeRequestArraySize(buffer, len);
        RedisProtocolSupport.Command.GETBIT.encodeTo(buffer);
        writeRequestArgument(buffer, key, keyBytes);
        writeRequestArgument(buffer, offset);
        final RedisRequest request = newRequest(RedisProtocolSupport.Command.GETBIT, buffer);
        final Single<String> queued = reservedCnx.request(request, String.class);
        Future<Long> result = enqueueForExecute(state, singles, queued);
        return result;
    }

    @Override
    public Future<String> getrange(@RedisProtocolSupport.Key final CharSequence key, final long start, final long end) {
        requireNonNull(key);
        final int len = 4;
        final int keyBytes = numberOfBytesUtf8(key);
        final int capacity = calculateInitialCommandBufferSize(len, RedisProtocolSupport.Command.GETRANGE) +
                    calculateRequestArgumentSize(keyBytes) + calculateRequestArgumentSize(start) +
                    calculateRequestArgumentSize(end);
        Buffer buffer = reservedCnx.executionContext().bufferAllocator().newBuffer(capacity);
        writeRequestArraySize(buffer, len);
        RedisProtocolSupport.Command.GETRANGE.encodeTo(buffer);
        writeRequestArgument(buffer, key, keyBytes);
        writeRequestArgument(buffer, start);
        writeRequestArgument(buffer, end);
        final RedisRequest request = newRequest(RedisProtocolSupport.Command.GETRANGE, buffer);
        final Single<String> queued = reservedCnx.request(request, String.class);
        Future<String> result = enqueueForExecute(state, singles, queued);
        return result;
    }

    @Override
    public Future<String> getset(@RedisProtocolSupport.Key final CharSequence key, final CharSequence value) {
        requireNonNull(key);
        requireNonNull(value);
        final int len = 3;
        final int keyBytes = numberOfBytesUtf8(key);
        final int valueBytes = numberOfBytesUtf8(value);
        final int capacity = calculateInitialCommandBufferSize(len, RedisProtocolSupport.Command.GETSET) +
                    calculateRequestArgumentSize(keyBytes) + calculateRequestArgumentSize(valueBytes);
        Buffer buffer = reservedCnx.executionContext().bufferAllocator().newBuffer(capacity);
        writeRequestArraySize(buffer, len);
        RedisProtocolSupport.Command.GETSET.encodeTo(buffer);
        writeRequestArgument(buffer, key, keyBytes);
        writeRequestArgument(buffer, value, valueBytes);
        final RedisRequest request = newRequest(RedisProtocolSupport.Command.GETSET, buffer);
        final Single<String> queued = reservedCnx.request(request, String.class);
        Future<String> result = enqueueForExecute(state, singles, queued);
        return result;
    }

    @Override
    public Future<Long> hdel(@RedisProtocolSupport.Key final CharSequence key, final CharSequence field) {
        requireNonNull(key);
        requireNonNull(field);
        final int len = 3;
        final int keyBytes = numberOfBytesUtf8(key);
        final int fieldBytes = numberOfBytesUtf8(field);
        final int capacity = calculateInitialCommandBufferSize(len, RedisProtocolSupport.Command.HDEL) +
                    calculateRequestArgumentSize(keyBytes) + calculateRequestArgumentSize(fieldBytes);
        Buffer buffer = reservedCnx.executionContext().bufferAllocator().newBuffer(capacity);
        writeRequestArraySize(buffer, len);
        RedisProtocolSupport.Command.HDEL.encodeTo(buffer);
        writeRequestArgument(buffer, key, keyBytes);
        writeRequestArgument(buffer, field, fieldBytes);
        final RedisRequest request = newRequest(RedisProtocolSupport.Command.HDEL, buffer);
        final Single<String> queued = reservedCnx.request(request, String.class);
        Future<Long> result = enqueueForExecute(state, singles, queued);
        return result;
    }

    @Override
    public Future<Long> hdel(@RedisProtocolSupport.Key final CharSequence key, final CharSequence field1,
                             final CharSequence field2) {
        requireNonNull(key);
        requireNonNull(field1);
        requireNonNull(field2);
        final int len = 4;
        final int keyBytes = numberOfBytesUtf8(key);
        final int field1Bytes = numberOfBytesUtf8(field1);
        final int field2Bytes = numberOfBytesUtf8(field2);
        final int capacity = calculateInitialCommandBufferSize(len, RedisProtocolSupport.Command.HDEL) +
                    calculateRequestArgumentSize(keyBytes) + calculateRequestArgumentSize(field1Bytes) +
                    calculateRequestArgumentSize(field2Bytes);
        Buffer buffer = reservedCnx.executionContext().bufferAllocator().newBuffer(capacity);
        writeRequestArraySize(buffer, len);
        RedisProtocolSupport.Command.HDEL.encodeTo(buffer);
        writeRequestArgument(buffer, key, keyBytes);
        writeRequestArgument(buffer, field1, field1Bytes);
        writeRequestArgument(buffer, field2, field2Bytes);
        final RedisRequest request = newRequest(RedisProtocolSupport.Command.HDEL, buffer);
        final Single<String> queued = reservedCnx.request(request, String.class);
        Future<Long> result = enqueueForExecute(state, singles, queued);
        return result;
    }

    @Override
    public Future<Long> hdel(@RedisProtocolSupport.Key final CharSequence key, final CharSequence field1,
                             final CharSequence field2, final CharSequence field3) {
        requireNonNull(key);
        requireNonNull(field1);
        requireNonNull(field2);
        requireNonNull(field3);
        final int len = 5;
        final int keyBytes = numberOfBytesUtf8(key);
        final int field1Bytes = numberOfBytesUtf8(field1);
        final int field2Bytes = numberOfBytesUtf8(field2);
        final int field3Bytes = numberOfBytesUtf8(field3);
        final int capacity = calculateInitialCommandBufferSize(len, RedisProtocolSupport.Command.HDEL) +
                    calculateRequestArgumentSize(keyBytes) + calculateRequestArgumentSize(field1Bytes) +
                    calculateRequestArgumentSize(field2Bytes) + calculateRequestArgumentSize(field3Bytes);
        Buffer buffer = reservedCnx.executionContext().bufferAllocator().newBuffer(capacity);
        writeRequestArraySize(buffer, len);
        RedisProtocolSupport.Command.HDEL.encodeTo(buffer);
        writeRequestArgument(buffer, key, keyBytes);
        writeRequestArgument(buffer, field1, field1Bytes);
        writeRequestArgument(buffer, field2, field2Bytes);
        writeRequestArgument(buffer, field3, field3Bytes);
        final RedisRequest request = newRequest(RedisProtocolSupport.Command.HDEL, buffer);
        final Single<String> queued = reservedCnx.request(request, String.class);
        Future<Long> result = enqueueForExecute(state, singles, queued);
        return result;
    }

    @Override
    public Future<Long> hdel(@RedisProtocolSupport.Key final CharSequence key,
                             final Collection<? extends CharSequence> fields) {
        requireNonNull(key);
        requireNonNull(fields);
        final int len = 2 + fields.size();
        final int keyBytes = numberOfBytesUtf8(key);
        int fieldsCapacity = 0;
        if (fields instanceof List && fields instanceof RandomAccess) {
            final List<CharSequence> list = (List<CharSequence>) fields;
            for (int i = 0; i < list.size(); ++i) {
                final CharSequence arg = list.get(i);
                fieldsCapacity += estimateRequestArgumentSize(arg);
            }
        } else {
            for (CharSequence arg : fields) {
                fieldsCapacity += estimateRequestArgumentSize(arg);
            }
        }
        final int capacity = calculateInitialCommandBufferSize(len, RedisProtocolSupport.Command.HDEL) +
                    calculateRequestArgumentSize(keyBytes) + fieldsCapacity;
        Buffer buffer = reservedCnx.executionContext().bufferAllocator().newBuffer(capacity);
        writeRequestArraySize(buffer, len);
        RedisProtocolSupport.Command.HDEL.encodeTo(buffer);
        writeRequestArgument(buffer, key, keyBytes);
        if (fields instanceof List && fields instanceof RandomAccess) {
            final List<CharSequence> list = (List<CharSequence>) fields;
            for (int i = 0; i < list.size(); ++i) {
                final CharSequence arg = list.get(i);
                writeRequestArgument(buffer, arg);
            }
        } else {
            for (CharSequence arg : fields) {
                writeRequestArgument(buffer, arg);
            }
        }
        final RedisRequest request = newRequest(RedisProtocolSupport.Command.HDEL, buffer);
        final Single<String> queued = reservedCnx.request(request, String.class);
        Future<Long> result = enqueueForExecute(state, singles, queued);
        return result;
    }

    @Override
    public Future<Long> hexists(@RedisProtocolSupport.Key final CharSequence key, final CharSequence field) {
        requireNonNull(key);
        requireNonNull(field);
        final int len = 3;
        final int keyBytes = numberOfBytesUtf8(key);
        final int fieldBytes = numberOfBytesUtf8(field);
        final int capacity = calculateInitialCommandBufferSize(len, RedisProtocolSupport.Command.HEXISTS) +
                    calculateRequestArgumentSize(keyBytes) + calculateRequestArgumentSize(fieldBytes);
        Buffer buffer = reservedCnx.executionContext().bufferAllocator().newBuffer(capacity);
        writeRequestArraySize(buffer, len);
        RedisProtocolSupport.Command.HEXISTS.encodeTo(buffer);
        writeRequestArgument(buffer, key, keyBytes);
        writeRequestArgument(buffer, field, fieldBytes);
        final RedisRequest request = newRequest(RedisProtocolSupport.Command.HEXISTS, buffer);
        final Single<String> queued = reservedCnx.request(request, String.class);
        Future<Long> result = enqueueForExecute(state, singles, queued);
        return result;
    }

    @Override
    public Future<String> hget(@RedisProtocolSupport.Key final CharSequence key, final CharSequence field) {
        requireNonNull(key);
        requireNonNull(field);
        final int len = 3;
        final int keyBytes = numberOfBytesUtf8(key);
        final int fieldBytes = numberOfBytesUtf8(field);
        final int capacity = calculateInitialCommandBufferSize(len, RedisProtocolSupport.Command.HGET) +
                    calculateRequestArgumentSize(keyBytes) + calculateRequestArgumentSize(fieldBytes);
        Buffer buffer = reservedCnx.executionContext().bufferAllocator().newBuffer(capacity);
        writeRequestArraySize(buffer, len);
        RedisProtocolSupport.Command.HGET.encodeTo(buffer);
        writeRequestArgument(buffer, key, keyBytes);
        writeRequestArgument(buffer, field, fieldBytes);
        final RedisRequest request = newRequest(RedisProtocolSupport.Command.HGET, buffer);
        final Single<String> queued = reservedCnx.request(request, String.class);
        Future<String> result = enqueueForExecute(state, singles, queued);
        return result;
    }

    @Override
    public <T> Future<List<T>> hgetall(@RedisProtocolSupport.Key final CharSequence key) {
        requireNonNull(key);
        final int len = 2;
        final int keyBytes = numberOfBytesUtf8(key);
        final int capacity = calculateInitialCommandBufferSize(len, RedisProtocolSupport.Command.HGETALL) +
                    calculateRequestArgumentSize(keyBytes);
        Buffer buffer = reservedCnx.executionContext().bufferAllocator().newBuffer(capacity);
        writeRequestArraySize(buffer, len);
        RedisProtocolSupport.Command.HGETALL.encodeTo(buffer);
        writeRequestArgument(buffer, key, keyBytes);
        final RedisRequest request = newRequest(RedisProtocolSupport.Command.HGETALL, buffer);
        final Single<String> queued = reservedCnx.request(request, String.class);
        Future<List<T>> result = enqueueForExecute(state, singles, queued);
        return result;
    }

    @Override
    public Future<Long> hincrby(@RedisProtocolSupport.Key final CharSequence key, final CharSequence field,
                                final long increment) {
        requireNonNull(key);
        requireNonNull(field);
        final int len = 4;
        final int keyBytes = numberOfBytesUtf8(key);
        final int fieldBytes = numberOfBytesUtf8(field);
        final int capacity = calculateInitialCommandBufferSize(len, RedisProtocolSupport.Command.HINCRBY) +
                    calculateRequestArgumentSize(keyBytes) + calculateRequestArgumentSize(fieldBytes) +
                    calculateRequestArgumentSize(increment);
        Buffer buffer = reservedCnx.executionContext().bufferAllocator().newBuffer(capacity);
        writeRequestArraySize(buffer, len);
        RedisProtocolSupport.Command.HINCRBY.encodeTo(buffer);
        writeRequestArgument(buffer, key, keyBytes);
        writeRequestArgument(buffer, field, fieldBytes);
        writeRequestArgument(buffer, increment);
        final RedisRequest request = newRequest(RedisProtocolSupport.Command.HINCRBY, buffer);
        final Single<String> queued = reservedCnx.request(request, String.class);
        Future<Long> result = enqueueForExecute(state, singles, queued);
        return result;
    }

    @Override
    public Future<Double> hincrbyfloat(@RedisProtocolSupport.Key final CharSequence key, final CharSequence field,
                                       final double increment) {
        requireNonNull(key);
        requireNonNull(field);
        final int len = 4;
        final int keyBytes = numberOfBytesUtf8(key);
        final int fieldBytes = numberOfBytesUtf8(field);
        final int capacity = calculateInitialCommandBufferSize(len, RedisProtocolSupport.Command.HINCRBYFLOAT) +
                    calculateRequestArgumentSize(keyBytes) + calculateRequestArgumentSize(fieldBytes) +
                    calculateRequestArgumentSize(increment);
        Buffer buffer = reservedCnx.executionContext().bufferAllocator().newBuffer(capacity);
        writeRequestArraySize(buffer, len);
        RedisProtocolSupport.Command.HINCRBYFLOAT.encodeTo(buffer);
        writeRequestArgument(buffer, key, keyBytes);
        writeRequestArgument(buffer, field, fieldBytes);
        writeRequestArgument(buffer, increment);
        final RedisRequest request = newRequest(RedisProtocolSupport.Command.HINCRBYFLOAT, buffer);
        final Single<String> queued = reservedCnx.request(request, String.class);
        Future<Double> result = enqueueForExecute(state, singles, queued);
        return result;
    }

    @Override
    public <T> Future<List<T>> hkeys(@RedisProtocolSupport.Key final CharSequence key) {
        requireNonNull(key);
        final int len = 2;
        final int keyBytes = numberOfBytesUtf8(key);
        final int capacity = calculateInitialCommandBufferSize(len, RedisProtocolSupport.Command.HKEYS) +
                    calculateRequestArgumentSize(keyBytes);
        Buffer buffer = reservedCnx.executionContext().bufferAllocator().newBuffer(capacity);
        writeRequestArraySize(buffer, len);
        RedisProtocolSupport.Command.HKEYS.encodeTo(buffer);
        writeRequestArgument(buffer, key, keyBytes);
        final RedisRequest request = newRequest(RedisProtocolSupport.Command.HKEYS, buffer);
        final Single<String> queued = reservedCnx.request(request, String.class);
        Future<List<T>> result = enqueueForExecute(state, singles, queued);
        return result;
    }

    @Override
    public Future<Long> hlen(@RedisProtocolSupport.Key final CharSequence key) {
        requireNonNull(key);
        final int len = 2;
        final int keyBytes = numberOfBytesUtf8(key);
        final int capacity = calculateInitialCommandBufferSize(len, RedisProtocolSupport.Command.HLEN) +
                    calculateRequestArgumentSize(keyBytes);
        Buffer buffer = reservedCnx.executionContext().bufferAllocator().newBuffer(capacity);
        writeRequestArraySize(buffer, len);
        RedisProtocolSupport.Command.HLEN.encodeTo(buffer);
        writeRequestArgument(buffer, key, keyBytes);
        final RedisRequest request = newRequest(RedisProtocolSupport.Command.HLEN, buffer);
        final Single<String> queued = reservedCnx.request(request, String.class);
        Future<Long> result = enqueueForExecute(state, singles, queued);
        return result;
    }

    @Override
    public Future<List<String>> hmget(@RedisProtocolSupport.Key final CharSequence key, final CharSequence field) {
        requireNonNull(key);
        requireNonNull(field);
        final int len = 3;
        final int keyBytes = numberOfBytesUtf8(key);
        final int fieldBytes = numberOfBytesUtf8(field);
        final int capacity = calculateInitialCommandBufferSize(len, RedisProtocolSupport.Command.HMGET) +
                    calculateRequestArgumentSize(keyBytes) + calculateRequestArgumentSize(fieldBytes);
        Buffer buffer = reservedCnx.executionContext().bufferAllocator().newBuffer(capacity);
        writeRequestArraySize(buffer, len);
        RedisProtocolSupport.Command.HMGET.encodeTo(buffer);
        writeRequestArgument(buffer, key, keyBytes);
        writeRequestArgument(buffer, field, fieldBytes);
        final RedisRequest request = newRequest(RedisProtocolSupport.Command.HMGET, buffer);
        final Single<String> queued = reservedCnx.request(request, String.class);
        Future<List<String>> result = enqueueForExecute(state, singles, queued);
        return result;
    }

    @Override
    public Future<List<String>> hmget(@RedisProtocolSupport.Key final CharSequence key, final CharSequence field1,
                                      final CharSequence field2) {
        requireNonNull(key);
        requireNonNull(field1);
        requireNonNull(field2);
        final int len = 4;
        final int keyBytes = numberOfBytesUtf8(key);
        final int field1Bytes = numberOfBytesUtf8(field1);
        final int field2Bytes = numberOfBytesUtf8(field2);
        final int capacity = calculateInitialCommandBufferSize(len, RedisProtocolSupport.Command.HMGET) +
                    calculateRequestArgumentSize(keyBytes) + calculateRequestArgumentSize(field1Bytes) +
                    calculateRequestArgumentSize(field2Bytes);
        Buffer buffer = reservedCnx.executionContext().bufferAllocator().newBuffer(capacity);
        writeRequestArraySize(buffer, len);
        RedisProtocolSupport.Command.HMGET.encodeTo(buffer);
        writeRequestArgument(buffer, key, keyBytes);
        writeRequestArgument(buffer, field1, field1Bytes);
        writeRequestArgument(buffer, field2, field2Bytes);
        final RedisRequest request = newRequest(RedisProtocolSupport.Command.HMGET, buffer);
        final Single<String> queued = reservedCnx.request(request, String.class);
        Future<List<String>> result = enqueueForExecute(state, singles, queued);
        return result;
    }

    @Override
    public Future<List<String>> hmget(@RedisProtocolSupport.Key final CharSequence key, final CharSequence field1,
                                      final CharSequence field2, final CharSequence field3) {
        requireNonNull(key);
        requireNonNull(field1);
        requireNonNull(field2);
        requireNonNull(field3);
        final int len = 5;
        final int keyBytes = numberOfBytesUtf8(key);
        final int field1Bytes = numberOfBytesUtf8(field1);
        final int field2Bytes = numberOfBytesUtf8(field2);
        final int field3Bytes = numberOfBytesUtf8(field3);
        final int capacity = calculateInitialCommandBufferSize(len, RedisProtocolSupport.Command.HMGET) +
                    calculateRequestArgumentSize(keyBytes) + calculateRequestArgumentSize(field1Bytes) +
                    calculateRequestArgumentSize(field2Bytes) + calculateRequestArgumentSize(field3Bytes);
        Buffer buffer = reservedCnx.executionContext().bufferAllocator().newBuffer(capacity);
        writeRequestArraySize(buffer, len);
        RedisProtocolSupport.Command.HMGET.encodeTo(buffer);
        writeRequestArgument(buffer, key, keyBytes);
        writeRequestArgument(buffer, field1, field1Bytes);
        writeRequestArgument(buffer, field2, field2Bytes);
        writeRequestArgument(buffer, field3, field3Bytes);
        final RedisRequest request = newRequest(RedisProtocolSupport.Command.HMGET, buffer);
        final Single<String> queued = reservedCnx.request(request, String.class);
        Future<List<String>> result = enqueueForExecute(state, singles, queued);
        return result;
    }

    @Override
    public Future<List<String>> hmget(@RedisProtocolSupport.Key final CharSequence key,
                                      final Collection<? extends CharSequence> fields) {
        requireNonNull(key);
        requireNonNull(fields);
        final int len = 2 + fields.size();
        final int keyBytes = numberOfBytesUtf8(key);
        int fieldsCapacity = 0;
        if (fields instanceof List && fields instanceof RandomAccess) {
            final List<CharSequence> list = (List<CharSequence>) fields;
            for (int i = 0; i < list.size(); ++i) {
                final CharSequence arg = list.get(i);
                fieldsCapacity += estimateRequestArgumentSize(arg);
            }
        } else {
            for (CharSequence arg : fields) {
                fieldsCapacity += estimateRequestArgumentSize(arg);
            }
        }
        final int capacity = calculateInitialCommandBufferSize(len, RedisProtocolSupport.Command.HMGET) +
                    calculateRequestArgumentSize(keyBytes) + fieldsCapacity;
        Buffer buffer = reservedCnx.executionContext().bufferAllocator().newBuffer(capacity);
        writeRequestArraySize(buffer, len);
        RedisProtocolSupport.Command.HMGET.encodeTo(buffer);
        writeRequestArgument(buffer, key, keyBytes);
        if (fields instanceof List && fields instanceof RandomAccess) {
            final List<CharSequence> list = (List<CharSequence>) fields;
            for (int i = 0; i < list.size(); ++i) {
                final CharSequence arg = list.get(i);
                writeRequestArgument(buffer, arg);
            }
        } else {
            for (CharSequence arg : fields) {
                writeRequestArgument(buffer, arg);
            }
        }
        final RedisRequest request = newRequest(RedisProtocolSupport.Command.HMGET, buffer);
        final Single<String> queued = reservedCnx.request(request, String.class);
        Future<List<String>> result = enqueueForExecute(state, singles, queued);
        return result;
    }

    @Override
    public Future<String> hmset(@RedisProtocolSupport.Key final CharSequence key, final CharSequence field,
                                final CharSequence value) {
        requireNonNull(key);
        requireNonNull(field);
        requireNonNull(value);
        final int len = 4;
        final int keyBytes = numberOfBytesUtf8(key);
        final int fieldBytes = numberOfBytesUtf8(field);
        final int valueBytes = numberOfBytesUtf8(value);
        final int capacity = calculateInitialCommandBufferSize(len, RedisProtocolSupport.Command.HMSET) +
                    calculateRequestArgumentSize(keyBytes) + calculateRequestArgumentSize(fieldBytes) +
                    calculateRequestArgumentSize(valueBytes);
        Buffer buffer = reservedCnx.executionContext().bufferAllocator().newBuffer(capacity);
        writeRequestArraySize(buffer, len);
        RedisProtocolSupport.Command.HMSET.encodeTo(buffer);
        writeRequestArgument(buffer, key, keyBytes);
        writeRequestArgument(buffer, field, fieldBytes);
        writeRequestArgument(buffer, value, valueBytes);
        final RedisRequest request = newRequest(RedisProtocolSupport.Command.HMSET, buffer);
        final Single<String> queued = reservedCnx.request(request, String.class);
        Future<String> result = enqueueForExecute(state, singles, queued);
        return result;
    }

    @Override
    public Future<String> hmset(@RedisProtocolSupport.Key final CharSequence key, final CharSequence field1,
                                final CharSequence value1, final CharSequence field2, final CharSequence value2) {
        requireNonNull(key);
        requireNonNull(field1);
        requireNonNull(value1);
        requireNonNull(field2);
        requireNonNull(value2);
        final int len = 6;
        final int keyBytes = numberOfBytesUtf8(key);
        final int field1Bytes = numberOfBytesUtf8(field1);
        final int value1Bytes = numberOfBytesUtf8(value1);
        final int field2Bytes = numberOfBytesUtf8(field2);
        final int value2Bytes = numberOfBytesUtf8(value2);
        final int capacity = calculateInitialCommandBufferSize(len, RedisProtocolSupport.Command.HMSET) +
                    calculateRequestArgumentSize(keyBytes) + calculateRequestArgumentSize(field1Bytes) +
                    calculateRequestArgumentSize(value1Bytes) + calculateRequestArgumentSize(field2Bytes) +
                    calculateRequestArgumentSize(value2Bytes);
        Buffer buffer = reservedCnx.executionContext().bufferAllocator().newBuffer(capacity);
        writeRequestArraySize(buffer, len);
        RedisProtocolSupport.Command.HMSET.encodeTo(buffer);
        writeRequestArgument(buffer, key, keyBytes);
        writeRequestArgument(buffer, field1, field1Bytes);
        writeRequestArgument(buffer, value1, value1Bytes);
        writeRequestArgument(buffer, field2, field2Bytes);
        writeRequestArgument(buffer, value2, value2Bytes);
        final RedisRequest request = newRequest(RedisProtocolSupport.Command.HMSET, buffer);
        final Single<String> queued = reservedCnx.request(request, String.class);
        Future<String> result = enqueueForExecute(state, singles, queued);
        return result;
    }

    @Override
    public Future<String> hmset(@RedisProtocolSupport.Key final CharSequence key, final CharSequence field1,
                                final CharSequence value1, final CharSequence field2, final CharSequence value2,
                                final CharSequence field3, final CharSequence value3) {
        requireNonNull(key);
        requireNonNull(field1);
        requireNonNull(value1);
        requireNonNull(field2);
        requireNonNull(value2);
        requireNonNull(field3);
        requireNonNull(value3);
        final int len = 8;
        final int keyBytes = numberOfBytesUtf8(key);
        final int field1Bytes = numberOfBytesUtf8(field1);
        final int value1Bytes = numberOfBytesUtf8(value1);
        final int field2Bytes = numberOfBytesUtf8(field2);
        final int value2Bytes = numberOfBytesUtf8(value2);
        final int field3Bytes = numberOfBytesUtf8(field3);
        final int value3Bytes = numberOfBytesUtf8(value3);
        final int capacity = calculateInitialCommandBufferSize(len, RedisProtocolSupport.Command.HMSET) +
                    calculateRequestArgumentSize(keyBytes) + calculateRequestArgumentSize(field1Bytes) +
                    calculateRequestArgumentSize(value1Bytes) + calculateRequestArgumentSize(field2Bytes) +
                    calculateRequestArgumentSize(value2Bytes) + calculateRequestArgumentSize(field3Bytes) +
                    calculateRequestArgumentSize(value3Bytes);
        Buffer buffer = reservedCnx.executionContext().bufferAllocator().newBuffer(capacity);
        writeRequestArraySize(buffer, len);
        RedisProtocolSupport.Command.HMSET.encodeTo(buffer);
        writeRequestArgument(buffer, key, keyBytes);
        writeRequestArgument(buffer, field1, field1Bytes);
        writeRequestArgument(buffer, value1, value1Bytes);
        writeRequestArgument(buffer, field2, field2Bytes);
        writeRequestArgument(buffer, value2, value2Bytes);
        writeRequestArgument(buffer, field3, field3Bytes);
        writeRequestArgument(buffer, value3, value3Bytes);
        final RedisRequest request = newRequest(RedisProtocolSupport.Command.HMSET, buffer);
        final Single<String> queued = reservedCnx.request(request, String.class);
        Future<String> result = enqueueForExecute(state, singles, queued);
        return result;
    }

    @Override
    public Future<String> hmset(@RedisProtocolSupport.Key final CharSequence key,
                                final Collection<RedisProtocolSupport.FieldValue> fieldValues) {
        requireNonNull(key);
        requireNonNull(fieldValues);
        final int len = 2 + RedisProtocolSupport.FieldValue.SIZE * fieldValues.size();
        final int keyBytes = numberOfBytesUtf8(key);
        int fieldValuesCapacity = 0;
        if (fieldValues instanceof List && fieldValues instanceof RandomAccess) {
            final List<RedisProtocolSupport.FieldValue> list = (List<RedisProtocolSupport.FieldValue>) fieldValues;
            for (int i = 0; i < list.size(); ++i) {
                final RedisProtocolSupport.FieldValue arg = list.get(i);
                fieldValuesCapacity += arg.encodedByteCount();
            }
        } else {
            for (RedisProtocolSupport.FieldValue arg : fieldValues) {
                fieldValuesCapacity += arg.encodedByteCount();
            }
        }
        final int capacity = calculateInitialCommandBufferSize(len, RedisProtocolSupport.Command.HMSET) +
                    calculateRequestArgumentSize(keyBytes) + fieldValuesCapacity;
        Buffer buffer = reservedCnx.executionContext().bufferAllocator().newBuffer(capacity);
        writeRequestArraySize(buffer, len);
        RedisProtocolSupport.Command.HMSET.encodeTo(buffer);
        writeRequestArgument(buffer, key, keyBytes);
        if (fieldValues instanceof List && fieldValues instanceof RandomAccess) {
            final List<RedisProtocolSupport.FieldValue> list = (List<RedisProtocolSupport.FieldValue>) fieldValues;
            for (int i = 0; i < list.size(); ++i) {
                final RedisProtocolSupport.FieldValue arg = list.get(i);
                arg.encodeTo(buffer);
            }
        } else {
            for (RedisProtocolSupport.FieldValue arg : fieldValues) {
                arg.encodeTo(buffer);
            }
        }
        final RedisRequest request = newRequest(RedisProtocolSupport.Command.HMSET, buffer);
        final Single<String> queued = reservedCnx.request(request, String.class);
        Future<String> result = enqueueForExecute(state, singles, queued);
        return result;
    }

    @Override
    public <T> Future<List<T>> hscan(@RedisProtocolSupport.Key final CharSequence key, final long cursor) {
        requireNonNull(key);
        final int len = 3;
        final int keyBytes = numberOfBytesUtf8(key);
        final int capacity = calculateInitialCommandBufferSize(len, RedisProtocolSupport.Command.HSCAN) +
                    calculateRequestArgumentSize(keyBytes) + calculateRequestArgumentSize(cursor);
        Buffer buffer = reservedCnx.executionContext().bufferAllocator().newBuffer(capacity);
        writeRequestArraySize(buffer, len);
        RedisProtocolSupport.Command.HSCAN.encodeTo(buffer);
        writeRequestArgument(buffer, key, keyBytes);
        writeRequestArgument(buffer, cursor);
        final RedisRequest request = newRequest(RedisProtocolSupport.Command.HSCAN, buffer);
        final Single<String> queued = reservedCnx.request(request, String.class);
        Future<List<T>> result = enqueueForExecute(state, singles, queued);
        return result;
    }

    @Override
    public <T> Future<List<T>> hscan(@RedisProtocolSupport.Key final CharSequence key, final long cursor,
                                     @Nullable final CharSequence matchPattern, @Nullable final Long count) {
        requireNonNull(key);
        final int len = 3 + (matchPattern == null ? 0 : 2) + (count == null ? 0 : 2);
        final int keyBytes = numberOfBytesUtf8(key);
        final int matchPatternBytes = matchPattern == null ? 0 : numberOfBytesUtf8(matchPattern);
        final int capacity = calculateInitialCommandBufferSize(len, RedisProtocolSupport.Command.HSCAN) +
                    calculateRequestArgumentSize(keyBytes) + calculateRequestArgumentSize(cursor) +
                    (matchPattern == null ? 0 : RedisProtocolSupport.SubCommand.MATCH.encodedByteCount()) +
                    (matchPattern == null ? 0 : calculateRequestArgumentSize(matchPatternBytes)) +
                    (count == null ? 0 : RedisProtocolSupport.SubCommand.COUNT.encodedByteCount()) +
                    (count == null ? 0 : calculateRequestArgumentSize(count));
        Buffer buffer = reservedCnx.executionContext().bufferAllocator().newBuffer(capacity);
        writeRequestArraySize(buffer, len);
        RedisProtocolSupport.Command.HSCAN.encodeTo(buffer);
        writeRequestArgument(buffer, key, keyBytes);
        writeRequestArgument(buffer, cursor);
        if (matchPattern != null) {
            RedisProtocolSupport.SubCommand.MATCH.encodeTo(buffer);
            writeRequestArgument(buffer, matchPattern, matchPatternBytes);
        }
        if (count != null) {
            RedisProtocolSupport.SubCommand.COUNT.encodeTo(buffer);
            writeRequestArgument(buffer, count);
        }
        final RedisRequest request = newRequest(RedisProtocolSupport.Command.HSCAN, buffer);
        final Single<String> queued = reservedCnx.request(request, String.class);
        Future<List<T>> result = enqueueForExecute(state, singles, queued);
        return result;
    }

    @Override
    public Future<Long> hset(@RedisProtocolSupport.Key final CharSequence key, final CharSequence field,
                             final CharSequence value) {
        requireNonNull(key);
        requireNonNull(field);
        requireNonNull(value);
        final int len = 4;
        final int keyBytes = numberOfBytesUtf8(key);
        final int fieldBytes = numberOfBytesUtf8(field);
        final int valueBytes = numberOfBytesUtf8(value);
        final int capacity = calculateInitialCommandBufferSize(len, RedisProtocolSupport.Command.HSET) +
                    calculateRequestArgumentSize(keyBytes) + calculateRequestArgumentSize(fieldBytes) +
                    calculateRequestArgumentSize(valueBytes);
        Buffer buffer = reservedCnx.executionContext().bufferAllocator().newBuffer(capacity);
        writeRequestArraySize(buffer, len);
        RedisProtocolSupport.Command.HSET.encodeTo(buffer);
        writeRequestArgument(buffer, key, keyBytes);
        writeRequestArgument(buffer, field, fieldBytes);
        writeRequestArgument(buffer, value, valueBytes);
        final RedisRequest request = newRequest(RedisProtocolSupport.Command.HSET, buffer);
        final Single<String> queued = reservedCnx.request(request, String.class);
        Future<Long> result = enqueueForExecute(state, singles, queued);
        return result;
    }

    @Override
    public Future<Long> hsetnx(@RedisProtocolSupport.Key final CharSequence key, final CharSequence field,
                               final CharSequence value) {
        requireNonNull(key);
        requireNonNull(field);
        requireNonNull(value);
        final int len = 4;
        final int keyBytes = numberOfBytesUtf8(key);
        final int fieldBytes = numberOfBytesUtf8(field);
        final int valueBytes = numberOfBytesUtf8(value);
        final int capacity = calculateInitialCommandBufferSize(len, RedisProtocolSupport.Command.HSETNX) +
                    calculateRequestArgumentSize(keyBytes) + calculateRequestArgumentSize(fieldBytes) +
                    calculateRequestArgumentSize(valueBytes);
        Buffer buffer = reservedCnx.executionContext().bufferAllocator().newBuffer(capacity);
        writeRequestArraySize(buffer, len);
        RedisProtocolSupport.Command.HSETNX.encodeTo(buffer);
        writeRequestArgument(buffer, key, keyBytes);
        writeRequestArgument(buffer, field, fieldBytes);
        writeRequestArgument(buffer, value, valueBytes);
        final RedisRequest request = newRequest(RedisProtocolSupport.Command.HSETNX, buffer);
        final Single<String> queued = reservedCnx.request(request, String.class);
        Future<Long> result = enqueueForExecute(state, singles, queued);
        return result;
    }

    @Override
    public Future<Long> hstrlen(@RedisProtocolSupport.Key final CharSequence key, final CharSequence field) {
        requireNonNull(key);
        requireNonNull(field);
        final int len = 3;
        final int keyBytes = numberOfBytesUtf8(key);
        final int fieldBytes = numberOfBytesUtf8(field);
        final int capacity = calculateInitialCommandBufferSize(len, RedisProtocolSupport.Command.HSTRLEN) +
                    calculateRequestArgumentSize(keyBytes) + calculateRequestArgumentSize(fieldBytes);
        Buffer buffer = reservedCnx.executionContext().bufferAllocator().newBuffer(capacity);
        writeRequestArraySize(buffer, len);
        RedisProtocolSupport.Command.HSTRLEN.encodeTo(buffer);
        writeRequestArgument(buffer, key, keyBytes);
        writeRequestArgument(buffer, field, fieldBytes);
        final RedisRequest request = newRequest(RedisProtocolSupport.Command.HSTRLEN, buffer);
        final Single<String> queued = reservedCnx.request(request, String.class);
        Future<Long> result = enqueueForExecute(state, singles, queued);
        return result;
    }

    @Override
    public <T> Future<List<T>> hvals(@RedisProtocolSupport.Key final CharSequence key) {
        requireNonNull(key);
        final int len = 2;
        final int keyBytes = numberOfBytesUtf8(key);
        final int capacity = calculateInitialCommandBufferSize(len, RedisProtocolSupport.Command.HVALS) +
                    calculateRequestArgumentSize(keyBytes);
        Buffer buffer = reservedCnx.executionContext().bufferAllocator().newBuffer(capacity);
        writeRequestArraySize(buffer, len);
        RedisProtocolSupport.Command.HVALS.encodeTo(buffer);
        writeRequestArgument(buffer, key, keyBytes);
        final RedisRequest request = newRequest(RedisProtocolSupport.Command.HVALS, buffer);
        final Single<String> queued = reservedCnx.request(request, String.class);
        Future<List<T>> result = enqueueForExecute(state, singles, queued);
        return result;
    }

    @Override
    public Future<Long> incr(@RedisProtocolSupport.Key final CharSequence key) {
        requireNonNull(key);
        final int len = 2;
        final int keyBytes = numberOfBytesUtf8(key);
        final int capacity = calculateInitialCommandBufferSize(len, RedisProtocolSupport.Command.INCR) +
                    calculateRequestArgumentSize(keyBytes);
        Buffer buffer = reservedCnx.executionContext().bufferAllocator().newBuffer(capacity);
        writeRequestArraySize(buffer, len);
        RedisProtocolSupport.Command.INCR.encodeTo(buffer);
        writeRequestArgument(buffer, key, keyBytes);
        final RedisRequest request = newRequest(RedisProtocolSupport.Command.INCR, buffer);
        final Single<String> queued = reservedCnx.request(request, String.class);
        Future<Long> result = enqueueForExecute(state, singles, queued);
        return result;
    }

    @Override
    public Future<Long> incrby(@RedisProtocolSupport.Key final CharSequence key, final long increment) {
        requireNonNull(key);
        final int len = 3;
        final int keyBytes = numberOfBytesUtf8(key);
        final int capacity = calculateInitialCommandBufferSize(len, RedisProtocolSupport.Command.INCRBY) +
                    calculateRequestArgumentSize(keyBytes) + calculateRequestArgumentSize(increment);
        Buffer buffer = reservedCnx.executionContext().bufferAllocator().newBuffer(capacity);
        writeRequestArraySize(buffer, len);
        RedisProtocolSupport.Command.INCRBY.encodeTo(buffer);
        writeRequestArgument(buffer, key, keyBytes);
        writeRequestArgument(buffer, increment);
        final RedisRequest request = newRequest(RedisProtocolSupport.Command.INCRBY, buffer);
        final Single<String> queued = reservedCnx.request(request, String.class);
        Future<Long> result = enqueueForExecute(state, singles, queued);
        return result;
    }

    @Override
    public Future<Double> incrbyfloat(@RedisProtocolSupport.Key final CharSequence key, final double increment) {
        requireNonNull(key);
        final int len = 3;
        final int keyBytes = numberOfBytesUtf8(key);
        final int capacity = calculateInitialCommandBufferSize(len, RedisProtocolSupport.Command.INCRBYFLOAT) +
                    calculateRequestArgumentSize(keyBytes) + calculateRequestArgumentSize(increment);
        Buffer buffer = reservedCnx.executionContext().bufferAllocator().newBuffer(capacity);
        writeRequestArraySize(buffer, len);
        RedisProtocolSupport.Command.INCRBYFLOAT.encodeTo(buffer);
        writeRequestArgument(buffer, key, keyBytes);
        writeRequestArgument(buffer, increment);
        final RedisRequest request = newRequest(RedisProtocolSupport.Command.INCRBYFLOAT, buffer);
        final Single<String> queued = reservedCnx.request(request, String.class);
        Future<Double> result = enqueueForExecute(state, singles, queued);
        return result;
    }

    @Override
    public Future<String> info() {
        final int len = 1;
        final int capacity = calculateInitialCommandBufferSize(len, RedisProtocolSupport.Command.INFO);
        Buffer buffer = reservedCnx.executionContext().bufferAllocator().newBuffer(capacity);
        writeRequestArraySize(buffer, len);
        RedisProtocolSupport.Command.INFO.encodeTo(buffer);
        final RedisRequest request = newRequest(RedisProtocolSupport.Command.INFO, buffer);
        final Single<String> queued = reservedCnx.request(request, String.class);
        Future<String> result = enqueueForExecute(state, singles, queued);
        return result;
    }

    @Override
    public Future<String> info(@Nullable final CharSequence section) {
        final int len = 1 + (section == null ? 0 : 1);
        final int sectionBytes = section == null ? 0 : numberOfBytesUtf8(section);
        final int capacity = calculateInitialCommandBufferSize(len, RedisProtocolSupport.Command.INFO) +
                    (section == null ? 0 : calculateRequestArgumentSize(sectionBytes));
        Buffer buffer = reservedCnx.executionContext().bufferAllocator().newBuffer(capacity);
        writeRequestArraySize(buffer, len);
        RedisProtocolSupport.Command.INFO.encodeTo(buffer);
        if (section != null) {
            writeRequestArgument(buffer, section, sectionBytes);
        }
        final RedisRequest request = newRequest(RedisProtocolSupport.Command.INFO, buffer);
        final Single<String> queued = reservedCnx.request(request, String.class);
        Future<String> result = enqueueForExecute(state, singles, queued);
        return result;
    }

    @Override
    public <T> Future<List<T>> keys(final CharSequence pattern) {
        requireNonNull(pattern);
        final int len = 2;
        final int patternBytes = numberOfBytesUtf8(pattern);
        final int capacity = calculateInitialCommandBufferSize(len, RedisProtocolSupport.Command.KEYS) +
                    calculateRequestArgumentSize(patternBytes);
        Buffer buffer = reservedCnx.executionContext().bufferAllocator().newBuffer(capacity);
        writeRequestArraySize(buffer, len);
        RedisProtocolSupport.Command.KEYS.encodeTo(buffer);
        writeRequestArgument(buffer, pattern, patternBytes);
        final RedisRequest request = newRequest(RedisProtocolSupport.Command.KEYS, buffer);
        final Single<String> queued = reservedCnx.request(request, String.class);
        Future<List<T>> result = enqueueForExecute(state, singles, queued);
        return result;
    }

    @Override
    public Future<Long> lastsave() {
        final int len = 1;
        final int capacity = calculateInitialCommandBufferSize(len, RedisProtocolSupport.Command.LASTSAVE);
        Buffer buffer = reservedCnx.executionContext().bufferAllocator().newBuffer(capacity);
        writeRequestArraySize(buffer, len);
        RedisProtocolSupport.Command.LASTSAVE.encodeTo(buffer);
        final RedisRequest request = newRequest(RedisProtocolSupport.Command.LASTSAVE, buffer);
        final Single<String> queued = reservedCnx.request(request, String.class);
        Future<Long> result = enqueueForExecute(state, singles, queued);
        return result;
    }

    @Override
    public Future<String> lindex(@RedisProtocolSupport.Key final CharSequence key, final long index) {
        requireNonNull(key);
        final int len = 3;
        final int keyBytes = numberOfBytesUtf8(key);
        final int capacity = calculateInitialCommandBufferSize(len, RedisProtocolSupport.Command.LINDEX) +
                    calculateRequestArgumentSize(keyBytes) + calculateRequestArgumentSize(index);
        Buffer buffer = reservedCnx.executionContext().bufferAllocator().newBuffer(capacity);
        writeRequestArraySize(buffer, len);
        RedisProtocolSupport.Command.LINDEX.encodeTo(buffer);
        writeRequestArgument(buffer, key, keyBytes);
        writeRequestArgument(buffer, index);
        final RedisRequest request = newRequest(RedisProtocolSupport.Command.LINDEX, buffer);
        final Single<String> queued = reservedCnx.request(request, String.class);
        Future<String> result = enqueueForExecute(state, singles, queued);
        return result;
    }

    @Override
    public Future<Long> linsert(@RedisProtocolSupport.Key final CharSequence key,
                                final RedisProtocolSupport.LinsertWhere where, final CharSequence pivot,
                                final CharSequence value) {
        requireNonNull(key);
        requireNonNull(where);
        requireNonNull(pivot);
        requireNonNull(value);
        final int len = 5;
        final int keyBytes = numberOfBytesUtf8(key);
        final int pivotBytes = numberOfBytesUtf8(pivot);
        final int valueBytes = numberOfBytesUtf8(value);
        final int capacity = calculateInitialCommandBufferSize(len, RedisProtocolSupport.Command.LINSERT) +
                    calculateRequestArgumentSize(keyBytes) + where.encodedByteCount() +
                    calculateRequestArgumentSize(pivotBytes) + calculateRequestArgumentSize(valueBytes);
        Buffer buffer = reservedCnx.executionContext().bufferAllocator().newBuffer(capacity);
        writeRequestArraySize(buffer, len);
        RedisProtocolSupport.Command.LINSERT.encodeTo(buffer);
        writeRequestArgument(buffer, key, keyBytes);
        where.encodeTo(buffer);
        writeRequestArgument(buffer, pivot, pivotBytes);
        writeRequestArgument(buffer, value, valueBytes);
        final RedisRequest request = newRequest(RedisProtocolSupport.Command.LINSERT, buffer);
        final Single<String> queued = reservedCnx.request(request, String.class);
        Future<Long> result = enqueueForExecute(state, singles, queued);
        return result;
    }

    @Override
    public Future<Long> llen(@RedisProtocolSupport.Key final CharSequence key) {
        requireNonNull(key);
        final int len = 2;
        final int keyBytes = numberOfBytesUtf8(key);
        final int capacity = calculateInitialCommandBufferSize(len, RedisProtocolSupport.Command.LLEN) +
                    calculateRequestArgumentSize(keyBytes);
        Buffer buffer = reservedCnx.executionContext().bufferAllocator().newBuffer(capacity);
        writeRequestArraySize(buffer, len);
        RedisProtocolSupport.Command.LLEN.encodeTo(buffer);
        writeRequestArgument(buffer, key, keyBytes);
        final RedisRequest request = newRequest(RedisProtocolSupport.Command.LLEN, buffer);
        final Single<String> queued = reservedCnx.request(request, String.class);
        Future<Long> result = enqueueForExecute(state, singles, queued);
        return result;
    }

    @Override
    public Future<String> lpop(@RedisProtocolSupport.Key final CharSequence key) {
        requireNonNull(key);
        final int len = 2;
        final int keyBytes = numberOfBytesUtf8(key);
        final int capacity = calculateInitialCommandBufferSize(len, RedisProtocolSupport.Command.LPOP) +
                    calculateRequestArgumentSize(keyBytes);
        Buffer buffer = reservedCnx.executionContext().bufferAllocator().newBuffer(capacity);
        writeRequestArraySize(buffer, len);
        RedisProtocolSupport.Command.LPOP.encodeTo(buffer);
        writeRequestArgument(buffer, key, keyBytes);
        final RedisRequest request = newRequest(RedisProtocolSupport.Command.LPOP, buffer);
        final Single<String> queued = reservedCnx.request(request, String.class);
        Future<String> result = enqueueForExecute(state, singles, queued);
        return result;
    }

    @Override
    public Future<Long> lpush(@RedisProtocolSupport.Key final CharSequence key, final CharSequence value) {
        requireNonNull(key);
        requireNonNull(value);
        final int len = 3;
        final int keyBytes = numberOfBytesUtf8(key);
        final int valueBytes = numberOfBytesUtf8(value);
        final int capacity = calculateInitialCommandBufferSize(len, RedisProtocolSupport.Command.LPUSH) +
                    calculateRequestArgumentSize(keyBytes) + calculateRequestArgumentSize(valueBytes);
        Buffer buffer = reservedCnx.executionContext().bufferAllocator().newBuffer(capacity);
        writeRequestArraySize(buffer, len);
        RedisProtocolSupport.Command.LPUSH.encodeTo(buffer);
        writeRequestArgument(buffer, key, keyBytes);
        writeRequestArgument(buffer, value, valueBytes);
        final RedisRequest request = newRequest(RedisProtocolSupport.Command.LPUSH, buffer);
        final Single<String> queued = reservedCnx.request(request, String.class);
        Future<Long> result = enqueueForExecute(state, singles, queued);
        return result;
    }

    @Override
    public Future<Long> lpush(@RedisProtocolSupport.Key final CharSequence key, final CharSequence value1,
                              final CharSequence value2) {
        requireNonNull(key);
        requireNonNull(value1);
        requireNonNull(value2);
        final int len = 4;
        final int keyBytes = numberOfBytesUtf8(key);
        final int value1Bytes = numberOfBytesUtf8(value1);
        final int value2Bytes = numberOfBytesUtf8(value2);
        final int capacity = calculateInitialCommandBufferSize(len, RedisProtocolSupport.Command.LPUSH) +
                    calculateRequestArgumentSize(keyBytes) + calculateRequestArgumentSize(value1Bytes) +
                    calculateRequestArgumentSize(value2Bytes);
        Buffer buffer = reservedCnx.executionContext().bufferAllocator().newBuffer(capacity);
        writeRequestArraySize(buffer, len);
        RedisProtocolSupport.Command.LPUSH.encodeTo(buffer);
        writeRequestArgument(buffer, key, keyBytes);
        writeRequestArgument(buffer, value1, value1Bytes);
        writeRequestArgument(buffer, value2, value2Bytes);
        final RedisRequest request = newRequest(RedisProtocolSupport.Command.LPUSH, buffer);
        final Single<String> queued = reservedCnx.request(request, String.class);
        Future<Long> result = enqueueForExecute(state, singles, queued);
        return result;
    }

    @Override
    public Future<Long> lpush(@RedisProtocolSupport.Key final CharSequence key, final CharSequence value1,
                              final CharSequence value2, final CharSequence value3) {
        requireNonNull(key);
        requireNonNull(value1);
        requireNonNull(value2);
        requireNonNull(value3);
        final int len = 5;
        final int keyBytes = numberOfBytesUtf8(key);
        final int value1Bytes = numberOfBytesUtf8(value1);
        final int value2Bytes = numberOfBytesUtf8(value2);
        final int value3Bytes = numberOfBytesUtf8(value3);
        final int capacity = calculateInitialCommandBufferSize(len, RedisProtocolSupport.Command.LPUSH) +
                    calculateRequestArgumentSize(keyBytes) + calculateRequestArgumentSize(value1Bytes) +
                    calculateRequestArgumentSize(value2Bytes) + calculateRequestArgumentSize(value3Bytes);
        Buffer buffer = reservedCnx.executionContext().bufferAllocator().newBuffer(capacity);
        writeRequestArraySize(buffer, len);
        RedisProtocolSupport.Command.LPUSH.encodeTo(buffer);
        writeRequestArgument(buffer, key, keyBytes);
        writeRequestArgument(buffer, value1, value1Bytes);
        writeRequestArgument(buffer, value2, value2Bytes);
        writeRequestArgument(buffer, value3, value3Bytes);
        final RedisRequest request = newRequest(RedisProtocolSupport.Command.LPUSH, buffer);
        final Single<String> queued = reservedCnx.request(request, String.class);
        Future<Long> result = enqueueForExecute(state, singles, queued);
        return result;
    }

    @Override
    public Future<Long> lpush(@RedisProtocolSupport.Key final CharSequence key,
                              final Collection<? extends CharSequence> values) {
        requireNonNull(key);
        requireNonNull(values);
        final int len = 2 + values.size();
        final int keyBytes = numberOfBytesUtf8(key);
        int valuesCapacity = 0;
        if (values instanceof List && values instanceof RandomAccess) {
            final List<CharSequence> list = (List<CharSequence>) values;
            for (int i = 0; i < list.size(); ++i) {
                final CharSequence arg = list.get(i);
                valuesCapacity += estimateRequestArgumentSize(arg);
            }
        } else {
            for (CharSequence arg : values) {
                valuesCapacity += estimateRequestArgumentSize(arg);
            }
        }
        final int capacity = calculateInitialCommandBufferSize(len, RedisProtocolSupport.Command.LPUSH) +
                    calculateRequestArgumentSize(keyBytes) + valuesCapacity;
        Buffer buffer = reservedCnx.executionContext().bufferAllocator().newBuffer(capacity);
        writeRequestArraySize(buffer, len);
        RedisProtocolSupport.Command.LPUSH.encodeTo(buffer);
        writeRequestArgument(buffer, key, keyBytes);
        if (values instanceof List && values instanceof RandomAccess) {
            final List<CharSequence> list = (List<CharSequence>) values;
            for (int i = 0; i < list.size(); ++i) {
                final CharSequence arg = list.get(i);
                writeRequestArgument(buffer, arg);
            }
        } else {
            for (CharSequence arg : values) {
                writeRequestArgument(buffer, arg);
            }
        }
        final RedisRequest request = newRequest(RedisProtocolSupport.Command.LPUSH, buffer);
        final Single<String> queued = reservedCnx.request(request, String.class);
        Future<Long> result = enqueueForExecute(state, singles, queued);
        return result;
    }

    @Override
    public Future<Long> lpushx(@RedisProtocolSupport.Key final CharSequence key, final CharSequence value) {
        requireNonNull(key);
        requireNonNull(value);
        final int len = 3;
        final int keyBytes = numberOfBytesUtf8(key);
        final int valueBytes = numberOfBytesUtf8(value);
        final int capacity = calculateInitialCommandBufferSize(len, RedisProtocolSupport.Command.LPUSHX) +
                    calculateRequestArgumentSize(keyBytes) + calculateRequestArgumentSize(valueBytes);
        Buffer buffer = reservedCnx.executionContext().bufferAllocator().newBuffer(capacity);
        writeRequestArraySize(buffer, len);
        RedisProtocolSupport.Command.LPUSHX.encodeTo(buffer);
        writeRequestArgument(buffer, key, keyBytes);
        writeRequestArgument(buffer, value, valueBytes);
        final RedisRequest request = newRequest(RedisProtocolSupport.Command.LPUSHX, buffer);
        final Single<String> queued = reservedCnx.request(request, String.class);
        Future<Long> result = enqueueForExecute(state, singles, queued);
        return result;
    }

    @Override
    public <T> Future<List<T>> lrange(@RedisProtocolSupport.Key final CharSequence key, final long start,
                                      final long stop) {
        requireNonNull(key);
        final int len = 4;
        final int keyBytes = numberOfBytesUtf8(key);
        final int capacity = calculateInitialCommandBufferSize(len, RedisProtocolSupport.Command.LRANGE) +
                    calculateRequestArgumentSize(keyBytes) + calculateRequestArgumentSize(start) +
                    calculateRequestArgumentSize(stop);
        Buffer buffer = reservedCnx.executionContext().bufferAllocator().newBuffer(capacity);
        writeRequestArraySize(buffer, len);
        RedisProtocolSupport.Command.LRANGE.encodeTo(buffer);
        writeRequestArgument(buffer, key, keyBytes);
        writeRequestArgument(buffer, start);
        writeRequestArgument(buffer, stop);
        final RedisRequest request = newRequest(RedisProtocolSupport.Command.LRANGE, buffer);
        final Single<String> queued = reservedCnx.request(request, String.class);
        Future<List<T>> result = enqueueForExecute(state, singles, queued);
        return result;
    }

    @Override
    public Future<Long> lrem(@RedisProtocolSupport.Key final CharSequence key, final long count,
                             final CharSequence value) {
        requireNonNull(key);
        requireNonNull(value);
        final int len = 4;
        final int keyBytes = numberOfBytesUtf8(key);
        final int valueBytes = numberOfBytesUtf8(value);
        final int capacity = calculateInitialCommandBufferSize(len, RedisProtocolSupport.Command.LREM) +
                    calculateRequestArgumentSize(keyBytes) + calculateRequestArgumentSize(count) +
                    calculateRequestArgumentSize(valueBytes);
        Buffer buffer = reservedCnx.executionContext().bufferAllocator().newBuffer(capacity);
        writeRequestArraySize(buffer, len);
        RedisProtocolSupport.Command.LREM.encodeTo(buffer);
        writeRequestArgument(buffer, key, keyBytes);
        writeRequestArgument(buffer, count);
        writeRequestArgument(buffer, value, valueBytes);
        final RedisRequest request = newRequest(RedisProtocolSupport.Command.LREM, buffer);
        final Single<String> queued = reservedCnx.request(request, String.class);
        Future<Long> result = enqueueForExecute(state, singles, queued);
        return result;
    }

    @Override
    public Future<String> lset(@RedisProtocolSupport.Key final CharSequence key, final long index,
                               final CharSequence value) {
        requireNonNull(key);
        requireNonNull(value);
        final int len = 4;
        final int keyBytes = numberOfBytesUtf8(key);
        final int valueBytes = numberOfBytesUtf8(value);
        final int capacity = calculateInitialCommandBufferSize(len, RedisProtocolSupport.Command.LSET) +
                    calculateRequestArgumentSize(keyBytes) + calculateRequestArgumentSize(index) +
                    calculateRequestArgumentSize(valueBytes);
        Buffer buffer = reservedCnx.executionContext().bufferAllocator().newBuffer(capacity);
        writeRequestArraySize(buffer, len);
        RedisProtocolSupport.Command.LSET.encodeTo(buffer);
        writeRequestArgument(buffer, key, keyBytes);
        writeRequestArgument(buffer, index);
        writeRequestArgument(buffer, value, valueBytes);
        final RedisRequest request = newRequest(RedisProtocolSupport.Command.LSET, buffer);
        final Single<String> queued = reservedCnx.request(request, String.class);
        Future<String> result = enqueueForExecute(state, singles, queued);
        return result;
    }

    @Override
    public Future<String> ltrim(@RedisProtocolSupport.Key final CharSequence key, final long start, final long stop) {
        requireNonNull(key);
        final int len = 4;
        final int keyBytes = numberOfBytesUtf8(key);
        final int capacity = calculateInitialCommandBufferSize(len, RedisProtocolSupport.Command.LTRIM) +
                    calculateRequestArgumentSize(keyBytes) + calculateRequestArgumentSize(start) +
                    calculateRequestArgumentSize(stop);
        Buffer buffer = reservedCnx.executionContext().bufferAllocator().newBuffer(capacity);
        writeRequestArraySize(buffer, len);
        RedisProtocolSupport.Command.LTRIM.encodeTo(buffer);
        writeRequestArgument(buffer, key, keyBytes);
        writeRequestArgument(buffer, start);
        writeRequestArgument(buffer, stop);
        final RedisRequest request = newRequest(RedisProtocolSupport.Command.LTRIM, buffer);
        final Single<String> queued = reservedCnx.request(request, String.class);
        Future<String> result = enqueueForExecute(state, singles, queued);
        return result;
    }

    @Override
    public Future<String> memoryDoctor() {
        final int len = 2;
        final int capacity = 1 + calculateInitialCommandBufferSize(len, RedisProtocolSupport.Command.MEMORY);
        Buffer buffer = reservedCnx.executionContext().bufferAllocator().newBuffer(capacity);
        writeRequestArraySize(buffer, len);
        RedisProtocolSupport.Command.MEMORY.encodeTo(buffer);
        RedisProtocolSupport.SubCommand.DOCTOR.encodeTo(buffer);
        final RedisRequest request = newRequest(RedisProtocolSupport.Command.MEMORY, buffer);
        final Single<String> queued = reservedCnx.request(request, String.class);
        Future<String> result = enqueueForExecute(state, singles, queued);
        return result;
    }

    @Override
    public <T> Future<List<T>> memoryHelp() {
        final int len = 2;
        final int capacity = 1 + calculateInitialCommandBufferSize(len, RedisProtocolSupport.Command.MEMORY);
        Buffer buffer = reservedCnx.executionContext().bufferAllocator().newBuffer(capacity);
        writeRequestArraySize(buffer, len);
        RedisProtocolSupport.Command.MEMORY.encodeTo(buffer);
        RedisProtocolSupport.SubCommand.HELP.encodeTo(buffer);
        final RedisRequest request = newRequest(RedisProtocolSupport.Command.MEMORY, buffer);
        final Single<String> queued = reservedCnx.request(request, String.class);
        Future<List<T>> result = enqueueForExecute(state, singles, queued);
        return result;
    }

    @Override
    public Future<String> memoryMallocStats() {
        final int len = 2;
        final int capacity = 1 + calculateInitialCommandBufferSize(len, RedisProtocolSupport.Command.MEMORY);
        Buffer buffer = reservedCnx.executionContext().bufferAllocator().newBuffer(capacity);
        writeRequestArraySize(buffer, len);
        RedisProtocolSupport.Command.MEMORY.encodeTo(buffer);
        RedisProtocolSupport.SubCommand.MALLOC_STATS.encodeTo(buffer);
        final RedisRequest request = newRequest(RedisProtocolSupport.Command.MEMORY, buffer);
        final Single<String> queued = reservedCnx.request(request, String.class);
        Future<String> result = enqueueForExecute(state, singles, queued);
        return result;
    }

    @Override
    public Future<String> memoryPurge() {
        final int len = 2;
        final int capacity = 1 + calculateInitialCommandBufferSize(len, RedisProtocolSupport.Command.MEMORY);
        Buffer buffer = reservedCnx.executionContext().bufferAllocator().newBuffer(capacity);
        writeRequestArraySize(buffer, len);
        RedisProtocolSupport.Command.MEMORY.encodeTo(buffer);
        RedisProtocolSupport.SubCommand.PURGE.encodeTo(buffer);
        final RedisRequest request = newRequest(RedisProtocolSupport.Command.MEMORY, buffer);
        final Single<String> queued = reservedCnx.request(request, String.class);
        Future<String> result = enqueueForExecute(state, singles, queued);
        return result;
    }

    @Override
    public <T> Future<List<T>> memoryStats() {
        final int len = 2;
        final int capacity = 1 + calculateInitialCommandBufferSize(len, RedisProtocolSupport.Command.MEMORY);
        Buffer buffer = reservedCnx.executionContext().bufferAllocator().newBuffer(capacity);
        writeRequestArraySize(buffer, len);
        RedisProtocolSupport.Command.MEMORY.encodeTo(buffer);
        RedisProtocolSupport.SubCommand.STATS.encodeTo(buffer);
        final RedisRequest request = newRequest(RedisProtocolSupport.Command.MEMORY, buffer);
        final Single<String> queued = reservedCnx.request(request, String.class);
        Future<List<T>> result = enqueueForExecute(state, singles, queued);
        return result;
    }

    @Override
    public Future<Long> memoryUsage(@RedisProtocolSupport.Key final CharSequence key) {
        requireNonNull(key);
        final int len = 3;
        final int keyBytes = numberOfBytesUtf8(key);
        final int capacity = 1 + calculateInitialCommandBufferSize(len, RedisProtocolSupport.Command.MEMORY) +
                    calculateRequestArgumentSize(keyBytes);
        Buffer buffer = reservedCnx.executionContext().bufferAllocator().newBuffer(capacity);
        writeRequestArraySize(buffer, len);
        RedisProtocolSupport.Command.MEMORY.encodeTo(buffer);
        RedisProtocolSupport.SubCommand.USAGE.encodeTo(buffer);
        writeRequestArgument(buffer, key, keyBytes);
        final RedisRequest request = newRequest(RedisProtocolSupport.Command.MEMORY, buffer);
        final Single<String> queued = reservedCnx.request(request, String.class);
        Future<Long> result = enqueueForExecute(state, singles, queued);
        return result;
    }

    @Override
    public Future<Long> memoryUsage(@RedisProtocolSupport.Key final CharSequence key,
                                    @Nullable final Long samplesCount) {
        requireNonNull(key);
        final int len = 3 + (samplesCount == null ? 0 : 2);
        final int keyBytes = numberOfBytesUtf8(key);
        final int capacity = 1 + calculateInitialCommandBufferSize(len, RedisProtocolSupport.Command.MEMORY) +
                    calculateRequestArgumentSize(keyBytes) +
                    (samplesCount == null ? 0 : RedisProtocolSupport.SubCommand.SAMPLES.encodedByteCount()) +
                    (samplesCount == null ? 0 : calculateRequestArgumentSize(samplesCount));
        Buffer buffer = reservedCnx.executionContext().bufferAllocator().newBuffer(capacity);
        writeRequestArraySize(buffer, len);
        RedisProtocolSupport.Command.MEMORY.encodeTo(buffer);
        RedisProtocolSupport.SubCommand.USAGE.encodeTo(buffer);
        writeRequestArgument(buffer, key, keyBytes);
        if (samplesCount != null) {
            RedisProtocolSupport.SubCommand.SAMPLES.encodeTo(buffer);
            writeRequestArgument(buffer, samplesCount);
        }
        final RedisRequest request = newRequest(RedisProtocolSupport.Command.MEMORY, buffer);
        final Single<String> queued = reservedCnx.request(request, String.class);
        Future<Long> result = enqueueForExecute(state, singles, queued);
        return result;
    }

    @Override
    public Future<List<String>> mget(@RedisProtocolSupport.Key final CharSequence key) {
        requireNonNull(key);
        final int len = 2;
        final int keyBytes = numberOfBytesUtf8(key);
        final int capacity = calculateInitialCommandBufferSize(len, RedisProtocolSupport.Command.MGET) +
                    calculateRequestArgumentSize(keyBytes);
        Buffer buffer = reservedCnx.executionContext().bufferAllocator().newBuffer(capacity);
        writeRequestArraySize(buffer, len);
        RedisProtocolSupport.Command.MGET.encodeTo(buffer);
        writeRequestArgument(buffer, key, keyBytes);
        final RedisRequest request = newRequest(RedisProtocolSupport.Command.MGET, buffer);
        final Single<String> queued = reservedCnx.request(request, String.class);
        Future<List<String>> result = enqueueForExecute(state, singles, queued);
        return result;
    }

    @Override
    public Future<List<String>> mget(@RedisProtocolSupport.Key final CharSequence key1,
                                     @RedisProtocolSupport.Key final CharSequence key2) {
        requireNonNull(key1);
        requireNonNull(key2);
        final int len = 3;
        final int key1Bytes = numberOfBytesUtf8(key1);
        final int key2Bytes = numberOfBytesUtf8(key2);
        final int capacity = calculateInitialCommandBufferSize(len, RedisProtocolSupport.Command.MGET) +
                    calculateRequestArgumentSize(key1Bytes) + calculateRequestArgumentSize(key2Bytes);
        Buffer buffer = reservedCnx.executionContext().bufferAllocator().newBuffer(capacity);
        writeRequestArraySize(buffer, len);
        RedisProtocolSupport.Command.MGET.encodeTo(buffer);
        writeRequestArgument(buffer, key1, key1Bytes);
        writeRequestArgument(buffer, key2, key2Bytes);
        final RedisRequest request = newRequest(RedisProtocolSupport.Command.MGET, buffer);
        final Single<String> queued = reservedCnx.request(request, String.class);
        Future<List<String>> result = enqueueForExecute(state, singles, queued);
        return result;
    }

    @Override
    public Future<List<String>> mget(@RedisProtocolSupport.Key final CharSequence key1,
                                     @RedisProtocolSupport.Key final CharSequence key2,
                                     @RedisProtocolSupport.Key final CharSequence key3) {
        requireNonNull(key1);
        requireNonNull(key2);
        requireNonNull(key3);
        final int len = 4;
        final int key1Bytes = numberOfBytesUtf8(key1);
        final int key2Bytes = numberOfBytesUtf8(key2);
        final int key3Bytes = numberOfBytesUtf8(key3);
        final int capacity = calculateInitialCommandBufferSize(len, RedisProtocolSupport.Command.MGET) +
                    calculateRequestArgumentSize(key1Bytes) + calculateRequestArgumentSize(key2Bytes) +
                    calculateRequestArgumentSize(key3Bytes);
        Buffer buffer = reservedCnx.executionContext().bufferAllocator().newBuffer(capacity);
        writeRequestArraySize(buffer, len);
        RedisProtocolSupport.Command.MGET.encodeTo(buffer);
        writeRequestArgument(buffer, key1, key1Bytes);
        writeRequestArgument(buffer, key2, key2Bytes);
        writeRequestArgument(buffer, key3, key3Bytes);
        final RedisRequest request = newRequest(RedisProtocolSupport.Command.MGET, buffer);
        final Single<String> queued = reservedCnx.request(request, String.class);
        Future<List<String>> result = enqueueForExecute(state, singles, queued);
        return result;
    }

    @Override
    public Future<List<String>> mget(@RedisProtocolSupport.Key final Collection<? extends CharSequence> keys) {
        requireNonNull(keys);
        final int len = 1 + keys.size();
        int keysCapacity = 0;
        if (keys instanceof List && keys instanceof RandomAccess) {
            final List<CharSequence> list = (List<CharSequence>) keys;
            for (int i = 0; i < list.size(); ++i) {
                final CharSequence arg = list.get(i);
                keysCapacity += estimateRequestArgumentSize(arg);
            }
        } else {
            for (CharSequence arg : keys) {
                keysCapacity += estimateRequestArgumentSize(arg);
            }
        }
        final int capacity = calculateInitialCommandBufferSize(len, RedisProtocolSupport.Command.MGET) + keysCapacity;
        Buffer buffer = reservedCnx.executionContext().bufferAllocator().newBuffer(capacity);
        writeRequestArraySize(buffer, len);
        RedisProtocolSupport.Command.MGET.encodeTo(buffer);
        if (keys instanceof List && keys instanceof RandomAccess) {
            final List<CharSequence> list = (List<CharSequence>) keys;
            for (int i = 0; i < list.size(); ++i) {
                final CharSequence arg = list.get(i);
                writeRequestArgument(buffer, arg);
            }
        } else {
            for (CharSequence arg : keys) {
                writeRequestArgument(buffer, arg);
            }
        }
        final RedisRequest request = newRequest(RedisProtocolSupport.Command.MGET, buffer);
        final Single<String> queued = reservedCnx.request(request, String.class);
        Future<List<String>> result = enqueueForExecute(state, singles, queued);
        return result;
    }

    @Override
    public Future<Long> move(@RedisProtocolSupport.Key final CharSequence key, final long db) {
        requireNonNull(key);
        final int len = 3;
        final int keyBytes = numberOfBytesUtf8(key);
        final int capacity = calculateInitialCommandBufferSize(len, RedisProtocolSupport.Command.MOVE) +
                    calculateRequestArgumentSize(keyBytes) + calculateRequestArgumentSize(db);
        Buffer buffer = reservedCnx.executionContext().bufferAllocator().newBuffer(capacity);
        writeRequestArraySize(buffer, len);
        RedisProtocolSupport.Command.MOVE.encodeTo(buffer);
        writeRequestArgument(buffer, key, keyBytes);
        writeRequestArgument(buffer, db);
        final RedisRequest request = newRequest(RedisProtocolSupport.Command.MOVE, buffer);
        final Single<String> queued = reservedCnx.request(request, String.class);
        Future<Long> result = enqueueForExecute(state, singles, queued);
        return result;
    }

    @Override
    public Future<String> mset(@RedisProtocolSupport.Key final CharSequence key, final CharSequence value) {
        requireNonNull(key);
        requireNonNull(value);
        final int len = 3;
        final int keyBytes = numberOfBytesUtf8(key);
        final int valueBytes = numberOfBytesUtf8(value);
        final int capacity = calculateInitialCommandBufferSize(len, RedisProtocolSupport.Command.MSET) +
                    calculateRequestArgumentSize(keyBytes) + calculateRequestArgumentSize(valueBytes);
        Buffer buffer = reservedCnx.executionContext().bufferAllocator().newBuffer(capacity);
        writeRequestArraySize(buffer, len);
        RedisProtocolSupport.Command.MSET.encodeTo(buffer);
        writeRequestArgument(buffer, key, keyBytes);
        writeRequestArgument(buffer, value, valueBytes);
        final RedisRequest request = newRequest(RedisProtocolSupport.Command.MSET, buffer);
        final Single<String> queued = reservedCnx.request(request, String.class);
        Future<String> result = enqueueForExecute(state, singles, queued);
        return result;
    }

    @Override
    public Future<String> mset(@RedisProtocolSupport.Key final CharSequence key1, final CharSequence value1,
                               @RedisProtocolSupport.Key final CharSequence key2, final CharSequence value2) {
        requireNonNull(key1);
        requireNonNull(value1);
        requireNonNull(key2);
        requireNonNull(value2);
        final int len = 5;
        final int key1Bytes = numberOfBytesUtf8(key1);
        final int value1Bytes = numberOfBytesUtf8(value1);
        final int key2Bytes = numberOfBytesUtf8(key2);
        final int value2Bytes = numberOfBytesUtf8(value2);
        final int capacity = calculateInitialCommandBufferSize(len, RedisProtocolSupport.Command.MSET) +
                    calculateRequestArgumentSize(key1Bytes) + calculateRequestArgumentSize(value1Bytes) +
                    calculateRequestArgumentSize(key2Bytes) + calculateRequestArgumentSize(value2Bytes);
        Buffer buffer = reservedCnx.executionContext().bufferAllocator().newBuffer(capacity);
        writeRequestArraySize(buffer, len);
        RedisProtocolSupport.Command.MSET.encodeTo(buffer);
        writeRequestArgument(buffer, key1, key1Bytes);
        writeRequestArgument(buffer, value1, value1Bytes);
        writeRequestArgument(buffer, key2, key2Bytes);
        writeRequestArgument(buffer, value2, value2Bytes);
        final RedisRequest request = newRequest(RedisProtocolSupport.Command.MSET, buffer);
        final Single<String> queued = reservedCnx.request(request, String.class);
        Future<String> result = enqueueForExecute(state, singles, queued);
        return result;
    }

    @Override
    public Future<String> mset(@RedisProtocolSupport.Key final CharSequence key1, final CharSequence value1,
                               @RedisProtocolSupport.Key final CharSequence key2, final CharSequence value2,
                               @RedisProtocolSupport.Key final CharSequence key3, final CharSequence value3) {
        requireNonNull(key1);
        requireNonNull(value1);
        requireNonNull(key2);
        requireNonNull(value2);
        requireNonNull(key3);
        requireNonNull(value3);
        final int len = 7;
        final int key1Bytes = numberOfBytesUtf8(key1);
        final int value1Bytes = numberOfBytesUtf8(value1);
        final int key2Bytes = numberOfBytesUtf8(key2);
        final int value2Bytes = numberOfBytesUtf8(value2);
        final int key3Bytes = numberOfBytesUtf8(key3);
        final int value3Bytes = numberOfBytesUtf8(value3);
        final int capacity = calculateInitialCommandBufferSize(len, RedisProtocolSupport.Command.MSET) +
                    calculateRequestArgumentSize(key1Bytes) + calculateRequestArgumentSize(value1Bytes) +
                    calculateRequestArgumentSize(key2Bytes) + calculateRequestArgumentSize(value2Bytes) +
                    calculateRequestArgumentSize(key3Bytes) + calculateRequestArgumentSize(value3Bytes);
        Buffer buffer = reservedCnx.executionContext().bufferAllocator().newBuffer(capacity);
        writeRequestArraySize(buffer, len);
        RedisProtocolSupport.Command.MSET.encodeTo(buffer);
        writeRequestArgument(buffer, key1, key1Bytes);
        writeRequestArgument(buffer, value1, value1Bytes);
        writeRequestArgument(buffer, key2, key2Bytes);
        writeRequestArgument(buffer, value2, value2Bytes);
        writeRequestArgument(buffer, key3, key3Bytes);
        writeRequestArgument(buffer, value3, value3Bytes);
        final RedisRequest request = newRequest(RedisProtocolSupport.Command.MSET, buffer);
        final Single<String> queued = reservedCnx.request(request, String.class);
        Future<String> result = enqueueForExecute(state, singles, queued);
        return result;
    }

    @Override
    public Future<String> mset(final Collection<RedisProtocolSupport.KeyValue> keyValues) {
        requireNonNull(keyValues);
        final int len = 1 + RedisProtocolSupport.KeyValue.SIZE * keyValues.size();
        int keyValuesCapacity = 0;
        if (keyValues instanceof List && keyValues instanceof RandomAccess) {
            final List<RedisProtocolSupport.KeyValue> list = (List<RedisProtocolSupport.KeyValue>) keyValues;
            for (int i = 0; i < list.size(); ++i) {
                final RedisProtocolSupport.KeyValue arg = list.get(i);
                keyValuesCapacity += arg.encodedByteCount();
            }
        } else {
            for (RedisProtocolSupport.KeyValue arg : keyValues) {
                keyValuesCapacity += arg.encodedByteCount();
            }
        }
        final int capacity = calculateInitialCommandBufferSize(len, RedisProtocolSupport.Command.MSET) +
                    keyValuesCapacity;
        Buffer buffer = reservedCnx.executionContext().bufferAllocator().newBuffer(capacity);
        writeRequestArraySize(buffer, len);
        RedisProtocolSupport.Command.MSET.encodeTo(buffer);
        if (keyValues instanceof List && keyValues instanceof RandomAccess) {
            final List<RedisProtocolSupport.KeyValue> list = (List<RedisProtocolSupport.KeyValue>) keyValues;
            for (int i = 0; i < list.size(); ++i) {
                final RedisProtocolSupport.KeyValue arg = list.get(i);
                arg.encodeTo(buffer);
            }
        } else {
            for (RedisProtocolSupport.KeyValue arg : keyValues) {
                arg.encodeTo(buffer);
            }
        }
        final RedisRequest request = newRequest(RedisProtocolSupport.Command.MSET, buffer);
        final Single<String> queued = reservedCnx.request(request, String.class);
        Future<String> result = enqueueForExecute(state, singles, queued);
        return result;
    }

    @Override
    public Future<Long> msetnx(@RedisProtocolSupport.Key final CharSequence key, final CharSequence value) {
        requireNonNull(key);
        requireNonNull(value);
        final int len = 3;
        final int keyBytes = numberOfBytesUtf8(key);
        final int valueBytes = numberOfBytesUtf8(value);
        final int capacity = calculateInitialCommandBufferSize(len, RedisProtocolSupport.Command.MSETNX) +
                    calculateRequestArgumentSize(keyBytes) + calculateRequestArgumentSize(valueBytes);
        Buffer buffer = reservedCnx.executionContext().bufferAllocator().newBuffer(capacity);
        writeRequestArraySize(buffer, len);
        RedisProtocolSupport.Command.MSETNX.encodeTo(buffer);
        writeRequestArgument(buffer, key, keyBytes);
        writeRequestArgument(buffer, value, valueBytes);
        final RedisRequest request = newRequest(RedisProtocolSupport.Command.MSETNX, buffer);
        final Single<String> queued = reservedCnx.request(request, String.class);
        Future<Long> result = enqueueForExecute(state, singles, queued);
        return result;
    }

    @Override
    public Future<Long> msetnx(@RedisProtocolSupport.Key final CharSequence key1, final CharSequence value1,
                               @RedisProtocolSupport.Key final CharSequence key2, final CharSequence value2) {
        requireNonNull(key1);
        requireNonNull(value1);
        requireNonNull(key2);
        requireNonNull(value2);
        final int len = 5;
        final int key1Bytes = numberOfBytesUtf8(key1);
        final int value1Bytes = numberOfBytesUtf8(value1);
        final int key2Bytes = numberOfBytesUtf8(key2);
        final int value2Bytes = numberOfBytesUtf8(value2);
        final int capacity = calculateInitialCommandBufferSize(len, RedisProtocolSupport.Command.MSETNX) +
                    calculateRequestArgumentSize(key1Bytes) + calculateRequestArgumentSize(value1Bytes) +
                    calculateRequestArgumentSize(key2Bytes) + calculateRequestArgumentSize(value2Bytes);
        Buffer buffer = reservedCnx.executionContext().bufferAllocator().newBuffer(capacity);
        writeRequestArraySize(buffer, len);
        RedisProtocolSupport.Command.MSETNX.encodeTo(buffer);
        writeRequestArgument(buffer, key1, key1Bytes);
        writeRequestArgument(buffer, value1, value1Bytes);
        writeRequestArgument(buffer, key2, key2Bytes);
        writeRequestArgument(buffer, value2, value2Bytes);
        final RedisRequest request = newRequest(RedisProtocolSupport.Command.MSETNX, buffer);
        final Single<String> queued = reservedCnx.request(request, String.class);
        Future<Long> result = enqueueForExecute(state, singles, queued);
        return result;
    }

    @Override
    public Future<Long> msetnx(@RedisProtocolSupport.Key final CharSequence key1, final CharSequence value1,
                               @RedisProtocolSupport.Key final CharSequence key2, final CharSequence value2,
                               @RedisProtocolSupport.Key final CharSequence key3, final CharSequence value3) {
        requireNonNull(key1);
        requireNonNull(value1);
        requireNonNull(key2);
        requireNonNull(value2);
        requireNonNull(key3);
        requireNonNull(value3);
        final int len = 7;
        final int key1Bytes = numberOfBytesUtf8(key1);
        final int value1Bytes = numberOfBytesUtf8(value1);
        final int key2Bytes = numberOfBytesUtf8(key2);
        final int value2Bytes = numberOfBytesUtf8(value2);
        final int key3Bytes = numberOfBytesUtf8(key3);
        final int value3Bytes = numberOfBytesUtf8(value3);
        final int capacity = calculateInitialCommandBufferSize(len, RedisProtocolSupport.Command.MSETNX) +
                    calculateRequestArgumentSize(key1Bytes) + calculateRequestArgumentSize(value1Bytes) +
                    calculateRequestArgumentSize(key2Bytes) + calculateRequestArgumentSize(value2Bytes) +
                    calculateRequestArgumentSize(key3Bytes) + calculateRequestArgumentSize(value3Bytes);
        Buffer buffer = reservedCnx.executionContext().bufferAllocator().newBuffer(capacity);
        writeRequestArraySize(buffer, len);
        RedisProtocolSupport.Command.MSETNX.encodeTo(buffer);
        writeRequestArgument(buffer, key1, key1Bytes);
        writeRequestArgument(buffer, value1, value1Bytes);
        writeRequestArgument(buffer, key2, key2Bytes);
        writeRequestArgument(buffer, value2, value2Bytes);
        writeRequestArgument(buffer, key3, key3Bytes);
        writeRequestArgument(buffer, value3, value3Bytes);
        final RedisRequest request = newRequest(RedisProtocolSupport.Command.MSETNX, buffer);
        final Single<String> queued = reservedCnx.request(request, String.class);
        Future<Long> result = enqueueForExecute(state, singles, queued);
        return result;
    }

    @Override
    public Future<Long> msetnx(final Collection<RedisProtocolSupport.KeyValue> keyValues) {
        requireNonNull(keyValues);
        final int len = 1 + RedisProtocolSupport.KeyValue.SIZE * keyValues.size();
        int keyValuesCapacity = 0;
        if (keyValues instanceof List && keyValues instanceof RandomAccess) {
            final List<RedisProtocolSupport.KeyValue> list = (List<RedisProtocolSupport.KeyValue>) keyValues;
            for (int i = 0; i < list.size(); ++i) {
                final RedisProtocolSupport.KeyValue arg = list.get(i);
                keyValuesCapacity += arg.encodedByteCount();
            }
        } else {
            for (RedisProtocolSupport.KeyValue arg : keyValues) {
                keyValuesCapacity += arg.encodedByteCount();
            }
        }
        final int capacity = calculateInitialCommandBufferSize(len, RedisProtocolSupport.Command.MSETNX) +
                    keyValuesCapacity;
        Buffer buffer = reservedCnx.executionContext().bufferAllocator().newBuffer(capacity);
        writeRequestArraySize(buffer, len);
        RedisProtocolSupport.Command.MSETNX.encodeTo(buffer);
        if (keyValues instanceof List && keyValues instanceof RandomAccess) {
            final List<RedisProtocolSupport.KeyValue> list = (List<RedisProtocolSupport.KeyValue>) keyValues;
            for (int i = 0; i < list.size(); ++i) {
                final RedisProtocolSupport.KeyValue arg = list.get(i);
                arg.encodeTo(buffer);
            }
        } else {
            for (RedisProtocolSupport.KeyValue arg : keyValues) {
                arg.encodeTo(buffer);
            }
        }
        final RedisRequest request = newRequest(RedisProtocolSupport.Command.MSETNX, buffer);
        final Single<String> queued = reservedCnx.request(request, String.class);
        Future<Long> result = enqueueForExecute(state, singles, queued);
        return result;
    }

    @Override
    public Future<String> objectEncoding(@RedisProtocolSupport.Key final CharSequence key) {
        requireNonNull(key);
        final int len = 3;
        final int keyBytes = numberOfBytesUtf8(key);
        final int capacity = 1 + calculateInitialCommandBufferSize(len, RedisProtocolSupport.Command.OBJECT) +
                    calculateRequestArgumentSize(keyBytes);
        Buffer buffer = reservedCnx.executionContext().bufferAllocator().newBuffer(capacity);
        writeRequestArraySize(buffer, len);
        RedisProtocolSupport.Command.OBJECT.encodeTo(buffer);
        RedisProtocolSupport.SubCommand.ENCODING.encodeTo(buffer);
        writeRequestArgument(buffer, key, keyBytes);
        final RedisRequest request = newRequest(RedisProtocolSupport.Command.OBJECT, buffer);
        final Single<String> queued = reservedCnx.request(request, String.class);
        Future<String> result = enqueueForExecute(state, singles, queued);
        return result;
    }

    @Override
    public Future<Long> objectFreq(@RedisProtocolSupport.Key final CharSequence key) {
        requireNonNull(key);
        final int len = 3;
        final int keyBytes = numberOfBytesUtf8(key);
        final int capacity = 1 + calculateInitialCommandBufferSize(len, RedisProtocolSupport.Command.OBJECT) +
                    calculateRequestArgumentSize(keyBytes);
        Buffer buffer = reservedCnx.executionContext().bufferAllocator().newBuffer(capacity);
        writeRequestArraySize(buffer, len);
        RedisProtocolSupport.Command.OBJECT.encodeTo(buffer);
        RedisProtocolSupport.SubCommand.FREQ.encodeTo(buffer);
        writeRequestArgument(buffer, key, keyBytes);
        final RedisRequest request = newRequest(RedisProtocolSupport.Command.OBJECT, buffer);
        final Single<String> queued = reservedCnx.request(request, String.class);
        Future<Long> result = enqueueForExecute(state, singles, queued);
        return result;
    }

    @Override
    public Future<List<String>> objectHelp() {
        final int len = 2;
        final int capacity = 1 + calculateInitialCommandBufferSize(len, RedisProtocolSupport.Command.OBJECT);
        Buffer buffer = reservedCnx.executionContext().bufferAllocator().newBuffer(capacity);
        writeRequestArraySize(buffer, len);
        RedisProtocolSupport.Command.OBJECT.encodeTo(buffer);
        RedisProtocolSupport.SubCommand.HELP.encodeTo(buffer);
        final RedisRequest request = newRequest(RedisProtocolSupport.Command.OBJECT, buffer);
        final Single<String> queued = reservedCnx.request(request, String.class);
        Future<List<String>> result = enqueueForExecute(state, singles, queued);
        return result;
    }

    @Override
    public Future<Long> objectIdletime(@RedisProtocolSupport.Key final CharSequence key) {
        requireNonNull(key);
        final int len = 3;
        final int keyBytes = numberOfBytesUtf8(key);
        final int capacity = 1 + calculateInitialCommandBufferSize(len, RedisProtocolSupport.Command.OBJECT) +
                    calculateRequestArgumentSize(keyBytes);
        Buffer buffer = reservedCnx.executionContext().bufferAllocator().newBuffer(capacity);
        writeRequestArraySize(buffer, len);
        RedisProtocolSupport.Command.OBJECT.encodeTo(buffer);
        RedisProtocolSupport.SubCommand.IDLETIME.encodeTo(buffer);
        writeRequestArgument(buffer, key, keyBytes);
        final RedisRequest request = newRequest(RedisProtocolSupport.Command.OBJECT, buffer);
        final Single<String> queued = reservedCnx.request(request, String.class);
        Future<Long> result = enqueueForExecute(state, singles, queued);
        return result;
    }

    @Override
    public Future<Long> objectRefcount(@RedisProtocolSupport.Key final CharSequence key) {
        requireNonNull(key);
        final int len = 3;
        final int keyBytes = numberOfBytesUtf8(key);
        final int capacity = 1 + calculateInitialCommandBufferSize(len, RedisProtocolSupport.Command.OBJECT) +
                    calculateRequestArgumentSize(keyBytes);
        Buffer buffer = reservedCnx.executionContext().bufferAllocator().newBuffer(capacity);
        writeRequestArraySize(buffer, len);
        RedisProtocolSupport.Command.OBJECT.encodeTo(buffer);
        RedisProtocolSupport.SubCommand.REFCOUNT.encodeTo(buffer);
        writeRequestArgument(buffer, key, keyBytes);
        final RedisRequest request = newRequest(RedisProtocolSupport.Command.OBJECT, buffer);
        final Single<String> queued = reservedCnx.request(request, String.class);
        Future<Long> result = enqueueForExecute(state, singles, queued);
        return result;
    }

    @Override
    public Future<Long> persist(@RedisProtocolSupport.Key final CharSequence key) {
        requireNonNull(key);
        final int len = 2;
        final int keyBytes = numberOfBytesUtf8(key);
        final int capacity = calculateInitialCommandBufferSize(len, RedisProtocolSupport.Command.PERSIST) +
                    calculateRequestArgumentSize(keyBytes);
        Buffer buffer = reservedCnx.executionContext().bufferAllocator().newBuffer(capacity);
        writeRequestArraySize(buffer, len);
        RedisProtocolSupport.Command.PERSIST.encodeTo(buffer);
        writeRequestArgument(buffer, key, keyBytes);
        final RedisRequest request = newRequest(RedisProtocolSupport.Command.PERSIST, buffer);
        final Single<String> queued = reservedCnx.request(request, String.class);
        Future<Long> result = enqueueForExecute(state, singles, queued);
        return result;
    }

    @Override
    public Future<Long> pexpire(@RedisProtocolSupport.Key final CharSequence key, final long milliseconds) {
        requireNonNull(key);
        final int len = 3;
        final int keyBytes = numberOfBytesUtf8(key);
        final int capacity = calculateInitialCommandBufferSize(len, RedisProtocolSupport.Command.PEXPIRE) +
                    calculateRequestArgumentSize(keyBytes) + calculateRequestArgumentSize(milliseconds);
        Buffer buffer = reservedCnx.executionContext().bufferAllocator().newBuffer(capacity);
        writeRequestArraySize(buffer, len);
        RedisProtocolSupport.Command.PEXPIRE.encodeTo(buffer);
        writeRequestArgument(buffer, key, keyBytes);
        writeRequestArgument(buffer, milliseconds);
        final RedisRequest request = newRequest(RedisProtocolSupport.Command.PEXPIRE, buffer);
        final Single<String> queued = reservedCnx.request(request, String.class);
        Future<Long> result = enqueueForExecute(state, singles, queued);
        return result;
    }

    @Override
    public Future<Long> pexpireat(@RedisProtocolSupport.Key final CharSequence key, final long millisecondsTimestamp) {
        requireNonNull(key);
        final int len = 3;
        final int keyBytes = numberOfBytesUtf8(key);
        final int capacity = calculateInitialCommandBufferSize(len, RedisProtocolSupport.Command.PEXPIREAT) +
                    calculateRequestArgumentSize(keyBytes) + calculateRequestArgumentSize(millisecondsTimestamp);
        Buffer buffer = reservedCnx.executionContext().bufferAllocator().newBuffer(capacity);
        writeRequestArraySize(buffer, len);
        RedisProtocolSupport.Command.PEXPIREAT.encodeTo(buffer);
        writeRequestArgument(buffer, key, keyBytes);
        writeRequestArgument(buffer, millisecondsTimestamp);
        final RedisRequest request = newRequest(RedisProtocolSupport.Command.PEXPIREAT, buffer);
        final Single<String> queued = reservedCnx.request(request, String.class);
        Future<Long> result = enqueueForExecute(state, singles, queued);
        return result;
    }

    @Override
    public Future<Long> pfadd(@RedisProtocolSupport.Key final CharSequence key, final CharSequence element) {
        requireNonNull(key);
        requireNonNull(element);
        final int len = 3;
        final int keyBytes = numberOfBytesUtf8(key);
        final int elementBytes = numberOfBytesUtf8(element);
        final int capacity = calculateInitialCommandBufferSize(len, RedisProtocolSupport.Command.PFADD) +
                    calculateRequestArgumentSize(keyBytes) + calculateRequestArgumentSize(elementBytes);
        Buffer buffer = reservedCnx.executionContext().bufferAllocator().newBuffer(capacity);
        writeRequestArraySize(buffer, len);
        RedisProtocolSupport.Command.PFADD.encodeTo(buffer);
        writeRequestArgument(buffer, key, keyBytes);
        writeRequestArgument(buffer, element, elementBytes);
        final RedisRequest request = newRequest(RedisProtocolSupport.Command.PFADD, buffer);
        final Single<String> queued = reservedCnx.request(request, String.class);
        Future<Long> result = enqueueForExecute(state, singles, queued);
        return result;
    }

    @Override
    public Future<Long> pfadd(@RedisProtocolSupport.Key final CharSequence key, final CharSequence element1,
                              final CharSequence element2) {
        requireNonNull(key);
        requireNonNull(element1);
        requireNonNull(element2);
        final int len = 4;
        final int keyBytes = numberOfBytesUtf8(key);
        final int element1Bytes = numberOfBytesUtf8(element1);
        final int element2Bytes = numberOfBytesUtf8(element2);
        final int capacity = calculateInitialCommandBufferSize(len, RedisProtocolSupport.Command.PFADD) +
                    calculateRequestArgumentSize(keyBytes) + calculateRequestArgumentSize(element1Bytes) +
                    calculateRequestArgumentSize(element2Bytes);
        Buffer buffer = reservedCnx.executionContext().bufferAllocator().newBuffer(capacity);
        writeRequestArraySize(buffer, len);
        RedisProtocolSupport.Command.PFADD.encodeTo(buffer);
        writeRequestArgument(buffer, key, keyBytes);
        writeRequestArgument(buffer, element1, element1Bytes);
        writeRequestArgument(buffer, element2, element2Bytes);
        final RedisRequest request = newRequest(RedisProtocolSupport.Command.PFADD, buffer);
        final Single<String> queued = reservedCnx.request(request, String.class);
        Future<Long> result = enqueueForExecute(state, singles, queued);
        return result;
    }

    @Override
    public Future<Long> pfadd(@RedisProtocolSupport.Key final CharSequence key, final CharSequence element1,
                              final CharSequence element2, final CharSequence element3) {
        requireNonNull(key);
        requireNonNull(element1);
        requireNonNull(element2);
        requireNonNull(element3);
        final int len = 5;
        final int keyBytes = numberOfBytesUtf8(key);
        final int element1Bytes = numberOfBytesUtf8(element1);
        final int element2Bytes = numberOfBytesUtf8(element2);
        final int element3Bytes = numberOfBytesUtf8(element3);
        final int capacity = calculateInitialCommandBufferSize(len, RedisProtocolSupport.Command.PFADD) +
                    calculateRequestArgumentSize(keyBytes) + calculateRequestArgumentSize(element1Bytes) +
                    calculateRequestArgumentSize(element2Bytes) + calculateRequestArgumentSize(element3Bytes);
        Buffer buffer = reservedCnx.executionContext().bufferAllocator().newBuffer(capacity);
        writeRequestArraySize(buffer, len);
        RedisProtocolSupport.Command.PFADD.encodeTo(buffer);
        writeRequestArgument(buffer, key, keyBytes);
        writeRequestArgument(buffer, element1, element1Bytes);
        writeRequestArgument(buffer, element2, element2Bytes);
        writeRequestArgument(buffer, element3, element3Bytes);
        final RedisRequest request = newRequest(RedisProtocolSupport.Command.PFADD, buffer);
        final Single<String> queued = reservedCnx.request(request, String.class);
        Future<Long> result = enqueueForExecute(state, singles, queued);
        return result;
    }

    @Override
    public Future<Long> pfadd(@RedisProtocolSupport.Key final CharSequence key,
                              final Collection<? extends CharSequence> elements) {
        requireNonNull(key);
        requireNonNull(elements);
        final int len = 2 + elements.size();
        final int keyBytes = numberOfBytesUtf8(key);
        int elementsCapacity = 0;
        if (elements instanceof List && elements instanceof RandomAccess) {
            final List<CharSequence> list = (List<CharSequence>) elements;
            for (int i = 0; i < list.size(); ++i) {
                final CharSequence arg = list.get(i);
                elementsCapacity += estimateRequestArgumentSize(arg);
            }
        } else {
            for (CharSequence arg : elements) {
                elementsCapacity += estimateRequestArgumentSize(arg);
            }
        }
        final int capacity = calculateInitialCommandBufferSize(len, RedisProtocolSupport.Command.PFADD) +
                    calculateRequestArgumentSize(keyBytes) + elementsCapacity;
        Buffer buffer = reservedCnx.executionContext().bufferAllocator().newBuffer(capacity);
        writeRequestArraySize(buffer, len);
        RedisProtocolSupport.Command.PFADD.encodeTo(buffer);
        writeRequestArgument(buffer, key, keyBytes);
        if (elements instanceof List && elements instanceof RandomAccess) {
            final List<CharSequence> list = (List<CharSequence>) elements;
            for (int i = 0; i < list.size(); ++i) {
                final CharSequence arg = list.get(i);
                writeRequestArgument(buffer, arg);
            }
        } else {
            for (CharSequence arg : elements) {
                writeRequestArgument(buffer, arg);
            }
        }
        final RedisRequest request = newRequest(RedisProtocolSupport.Command.PFADD, buffer);
        final Single<String> queued = reservedCnx.request(request, String.class);
        Future<Long> result = enqueueForExecute(state, singles, queued);
        return result;
    }

    @Override
    public Future<Long> pfcount(@RedisProtocolSupport.Key final CharSequence key) {
        requireNonNull(key);
        final int len = 2;
        final int keyBytes = numberOfBytesUtf8(key);
        final int capacity = calculateInitialCommandBufferSize(len, RedisProtocolSupport.Command.PFCOUNT) +
                    calculateRequestArgumentSize(keyBytes);
        Buffer buffer = reservedCnx.executionContext().bufferAllocator().newBuffer(capacity);
        writeRequestArraySize(buffer, len);
        RedisProtocolSupport.Command.PFCOUNT.encodeTo(buffer);
        writeRequestArgument(buffer, key, keyBytes);
        final RedisRequest request = newRequest(RedisProtocolSupport.Command.PFCOUNT, buffer);
        final Single<String> queued = reservedCnx.request(request, String.class);
        Future<Long> result = enqueueForExecute(state, singles, queued);
        return result;
    }

    @Override
    public Future<Long> pfcount(@RedisProtocolSupport.Key final CharSequence key1,
                                @RedisProtocolSupport.Key final CharSequence key2) {
        requireNonNull(key1);
        requireNonNull(key2);
        final int len = 3;
        final int key1Bytes = numberOfBytesUtf8(key1);
        final int key2Bytes = numberOfBytesUtf8(key2);
        final int capacity = calculateInitialCommandBufferSize(len, RedisProtocolSupport.Command.PFCOUNT) +
                    calculateRequestArgumentSize(key1Bytes) + calculateRequestArgumentSize(key2Bytes);
        Buffer buffer = reservedCnx.executionContext().bufferAllocator().newBuffer(capacity);
        writeRequestArraySize(buffer, len);
        RedisProtocolSupport.Command.PFCOUNT.encodeTo(buffer);
        writeRequestArgument(buffer, key1, key1Bytes);
        writeRequestArgument(buffer, key2, key2Bytes);
        final RedisRequest request = newRequest(RedisProtocolSupport.Command.PFCOUNT, buffer);
        final Single<String> queued = reservedCnx.request(request, String.class);
        Future<Long> result = enqueueForExecute(state, singles, queued);
        return result;
    }

    @Override
    public Future<Long> pfcount(@RedisProtocolSupport.Key final CharSequence key1,
                                @RedisProtocolSupport.Key final CharSequence key2,
                                @RedisProtocolSupport.Key final CharSequence key3) {
        requireNonNull(key1);
        requireNonNull(key2);
        requireNonNull(key3);
        final int len = 4;
        final int key1Bytes = numberOfBytesUtf8(key1);
        final int key2Bytes = numberOfBytesUtf8(key2);
        final int key3Bytes = numberOfBytesUtf8(key3);
        final int capacity = calculateInitialCommandBufferSize(len, RedisProtocolSupport.Command.PFCOUNT) +
                    calculateRequestArgumentSize(key1Bytes) + calculateRequestArgumentSize(key2Bytes) +
                    calculateRequestArgumentSize(key3Bytes);
        Buffer buffer = reservedCnx.executionContext().bufferAllocator().newBuffer(capacity);
        writeRequestArraySize(buffer, len);
        RedisProtocolSupport.Command.PFCOUNT.encodeTo(buffer);
        writeRequestArgument(buffer, key1, key1Bytes);
        writeRequestArgument(buffer, key2, key2Bytes);
        writeRequestArgument(buffer, key3, key3Bytes);
        final RedisRequest request = newRequest(RedisProtocolSupport.Command.PFCOUNT, buffer);
        final Single<String> queued = reservedCnx.request(request, String.class);
        Future<Long> result = enqueueForExecute(state, singles, queued);
        return result;
    }

    @Override
    public Future<Long> pfcount(@RedisProtocolSupport.Key final Collection<? extends CharSequence> keys) {
        requireNonNull(keys);
        final int len = 1 + keys.size();
        int keysCapacity = 0;
        if (keys instanceof List && keys instanceof RandomAccess) {
            final List<CharSequence> list = (List<CharSequence>) keys;
            for (int i = 0; i < list.size(); ++i) {
                final CharSequence arg = list.get(i);
                keysCapacity += estimateRequestArgumentSize(arg);
            }
        } else {
            for (CharSequence arg : keys) {
                keysCapacity += estimateRequestArgumentSize(arg);
            }
        }
        final int capacity = calculateInitialCommandBufferSize(len, RedisProtocolSupport.Command.PFCOUNT) +
                    keysCapacity;
        Buffer buffer = reservedCnx.executionContext().bufferAllocator().newBuffer(capacity);
        writeRequestArraySize(buffer, len);
        RedisProtocolSupport.Command.PFCOUNT.encodeTo(buffer);
        if (keys instanceof List && keys instanceof RandomAccess) {
            final List<CharSequence> list = (List<CharSequence>) keys;
            for (int i = 0; i < list.size(); ++i) {
                final CharSequence arg = list.get(i);
                writeRequestArgument(buffer, arg);
            }
        } else {
            for (CharSequence arg : keys) {
                writeRequestArgument(buffer, arg);
            }
        }
        final RedisRequest request = newRequest(RedisProtocolSupport.Command.PFCOUNT, buffer);
        final Single<String> queued = reservedCnx.request(request, String.class);
        Future<Long> result = enqueueForExecute(state, singles, queued);
        return result;
    }

    @Override
    public Future<String> pfmerge(@RedisProtocolSupport.Key final CharSequence destkey,
                                  @RedisProtocolSupport.Key final CharSequence sourcekey) {
        requireNonNull(destkey);
        requireNonNull(sourcekey);
        final int len = 3;
        final int destkeyBytes = numberOfBytesUtf8(destkey);
        final int sourcekeyBytes = numberOfBytesUtf8(sourcekey);
        final int capacity = calculateInitialCommandBufferSize(len, RedisProtocolSupport.Command.PFMERGE) +
                    calculateRequestArgumentSize(destkeyBytes) + calculateRequestArgumentSize(sourcekeyBytes);
        Buffer buffer = reservedCnx.executionContext().bufferAllocator().newBuffer(capacity);
        writeRequestArraySize(buffer, len);
        RedisProtocolSupport.Command.PFMERGE.encodeTo(buffer);
        writeRequestArgument(buffer, destkey, destkeyBytes);
        writeRequestArgument(buffer, sourcekey, sourcekeyBytes);
        final RedisRequest request = newRequest(RedisProtocolSupport.Command.PFMERGE, buffer);
        final Single<String> queued = reservedCnx.request(request, String.class);
        Future<String> result = enqueueForExecute(state, singles, queued);
        return result;
    }

    @Override
    public Future<String> pfmerge(@RedisProtocolSupport.Key final CharSequence destkey,
                                  @RedisProtocolSupport.Key final CharSequence sourcekey1,
                                  @RedisProtocolSupport.Key final CharSequence sourcekey2) {
        requireNonNull(destkey);
        requireNonNull(sourcekey1);
        requireNonNull(sourcekey2);
        final int len = 4;
        final int destkeyBytes = numberOfBytesUtf8(destkey);
        final int sourcekey1Bytes = numberOfBytesUtf8(sourcekey1);
        final int sourcekey2Bytes = numberOfBytesUtf8(sourcekey2);
        final int capacity = calculateInitialCommandBufferSize(len, RedisProtocolSupport.Command.PFMERGE) +
                    calculateRequestArgumentSize(destkeyBytes) + calculateRequestArgumentSize(sourcekey1Bytes) +
                    calculateRequestArgumentSize(sourcekey2Bytes);
        Buffer buffer = reservedCnx.executionContext().bufferAllocator().newBuffer(capacity);
        writeRequestArraySize(buffer, len);
        RedisProtocolSupport.Command.PFMERGE.encodeTo(buffer);
        writeRequestArgument(buffer, destkey, destkeyBytes);
        writeRequestArgument(buffer, sourcekey1, sourcekey1Bytes);
        writeRequestArgument(buffer, sourcekey2, sourcekey2Bytes);
        final RedisRequest request = newRequest(RedisProtocolSupport.Command.PFMERGE, buffer);
        final Single<String> queued = reservedCnx.request(request, String.class);
        Future<String> result = enqueueForExecute(state, singles, queued);
        return result;
    }

    @Override
    public Future<String> pfmerge(@RedisProtocolSupport.Key final CharSequence destkey,
                                  @RedisProtocolSupport.Key final CharSequence sourcekey1,
                                  @RedisProtocolSupport.Key final CharSequence sourcekey2,
                                  @RedisProtocolSupport.Key final CharSequence sourcekey3) {
        requireNonNull(destkey);
        requireNonNull(sourcekey1);
        requireNonNull(sourcekey2);
        requireNonNull(sourcekey3);
        final int len = 5;
        final int destkeyBytes = numberOfBytesUtf8(destkey);
        final int sourcekey1Bytes = numberOfBytesUtf8(sourcekey1);
        final int sourcekey2Bytes = numberOfBytesUtf8(sourcekey2);
        final int sourcekey3Bytes = numberOfBytesUtf8(sourcekey3);
        final int capacity = calculateInitialCommandBufferSize(len, RedisProtocolSupport.Command.PFMERGE) +
                    calculateRequestArgumentSize(destkeyBytes) + calculateRequestArgumentSize(sourcekey1Bytes) +
                    calculateRequestArgumentSize(sourcekey2Bytes) + calculateRequestArgumentSize(sourcekey3Bytes);
        Buffer buffer = reservedCnx.executionContext().bufferAllocator().newBuffer(capacity);
        writeRequestArraySize(buffer, len);
        RedisProtocolSupport.Command.PFMERGE.encodeTo(buffer);
        writeRequestArgument(buffer, destkey, destkeyBytes);
        writeRequestArgument(buffer, sourcekey1, sourcekey1Bytes);
        writeRequestArgument(buffer, sourcekey2, sourcekey2Bytes);
        writeRequestArgument(buffer, sourcekey3, sourcekey3Bytes);
        final RedisRequest request = newRequest(RedisProtocolSupport.Command.PFMERGE, buffer);
        final Single<String> queued = reservedCnx.request(request, String.class);
        Future<String> result = enqueueForExecute(state, singles, queued);
        return result;
    }

    @Override
    public Future<String> pfmerge(@RedisProtocolSupport.Key final CharSequence destkey,
                                  @RedisProtocolSupport.Key final Collection<? extends CharSequence> sourcekeys) {
        requireNonNull(destkey);
        requireNonNull(sourcekeys);
        final int len = 2 + sourcekeys.size();
        final int destkeyBytes = numberOfBytesUtf8(destkey);
        int sourcekeysCapacity = 0;
        if (sourcekeys instanceof List && sourcekeys instanceof RandomAccess) {
            final List<CharSequence> list = (List<CharSequence>) sourcekeys;
            for (int i = 0; i < list.size(); ++i) {
                final CharSequence arg = list.get(i);
                sourcekeysCapacity += estimateRequestArgumentSize(arg);
            }
        } else {
            for (CharSequence arg : sourcekeys) {
                sourcekeysCapacity += estimateRequestArgumentSize(arg);
            }
        }
        final int capacity = calculateInitialCommandBufferSize(len, RedisProtocolSupport.Command.PFMERGE) +
                    calculateRequestArgumentSize(destkeyBytes) + sourcekeysCapacity;
        Buffer buffer = reservedCnx.executionContext().bufferAllocator().newBuffer(capacity);
        writeRequestArraySize(buffer, len);
        RedisProtocolSupport.Command.PFMERGE.encodeTo(buffer);
        writeRequestArgument(buffer, destkey, destkeyBytes);
        if (sourcekeys instanceof List && sourcekeys instanceof RandomAccess) {
            final List<CharSequence> list = (List<CharSequence>) sourcekeys;
            for (int i = 0; i < list.size(); ++i) {
                final CharSequence arg = list.get(i);
                writeRequestArgument(buffer, arg);
            }
        } else {
            for (CharSequence arg : sourcekeys) {
                writeRequestArgument(buffer, arg);
            }
        }
        final RedisRequest request = newRequest(RedisProtocolSupport.Command.PFMERGE, buffer);
        final Single<String> queued = reservedCnx.request(request, String.class);
        Future<String> result = enqueueForExecute(state, singles, queued);
        return result;
    }

    @Override
    public Future<String> ping() {
        final int len = 1;
        final int capacity = calculateInitialCommandBufferSize(len, RedisProtocolSupport.Command.PING);
        Buffer buffer = reservedCnx.executionContext().bufferAllocator().newBuffer(capacity);
        writeRequestArraySize(buffer, len);
        RedisProtocolSupport.Command.PING.encodeTo(buffer);
        final RedisRequest request = newRequest(RedisProtocolSupport.Command.PING, buffer);
        final Single<String> queued = reservedCnx.request(request, String.class);
        Future<String> result = enqueueForExecute(state, singles, queued);
        return result;
    }

    @Override
    public Future<String> ping(final CharSequence message) {
        requireNonNull(message);
        final int len = 2;
        final int messageBytes = numberOfBytesUtf8(message);
        final int capacity = calculateInitialCommandBufferSize(len, RedisProtocolSupport.Command.PING) +
                    calculateRequestArgumentSize(messageBytes);
        Buffer buffer = reservedCnx.executionContext().bufferAllocator().newBuffer(capacity);
        writeRequestArraySize(buffer, len);
        RedisProtocolSupport.Command.PING.encodeTo(buffer);
        writeRequestArgument(buffer, message, messageBytes);
        final RedisRequest request = newRequest(RedisProtocolSupport.Command.PING, buffer);
        final Single<String> queued = reservedCnx.request(request, String.class);
        Future<String> result = enqueueForExecute(state, singles, queued);
        return result;
    }

    @Override
    public Future<String> psetex(@RedisProtocolSupport.Key final CharSequence key, final long milliseconds,
                                 final CharSequence value) {
        requireNonNull(key);
        requireNonNull(value);
        final int len = 4;
        final int keyBytes = numberOfBytesUtf8(key);
        final int valueBytes = numberOfBytesUtf8(value);
        final int capacity = calculateInitialCommandBufferSize(len, RedisProtocolSupport.Command.PSETEX) +
                    calculateRequestArgumentSize(keyBytes) + calculateRequestArgumentSize(milliseconds) +
                    calculateRequestArgumentSize(valueBytes);
        Buffer buffer = reservedCnx.executionContext().bufferAllocator().newBuffer(capacity);
        writeRequestArraySize(buffer, len);
        RedisProtocolSupport.Command.PSETEX.encodeTo(buffer);
        writeRequestArgument(buffer, key, keyBytes);
        writeRequestArgument(buffer, milliseconds);
        writeRequestArgument(buffer, value, valueBytes);
        final RedisRequest request = newRequest(RedisProtocolSupport.Command.PSETEX, buffer);
        final Single<String> queued = reservedCnx.request(request, String.class);
        Future<String> result = enqueueForExecute(state, singles, queued);
        return result;
    }

    @Override
    public Future<Long> pttl(@RedisProtocolSupport.Key final CharSequence key) {
        requireNonNull(key);
        final int len = 2;
        final int keyBytes = numberOfBytesUtf8(key);
        final int capacity = calculateInitialCommandBufferSize(len, RedisProtocolSupport.Command.PTTL) +
                    calculateRequestArgumentSize(keyBytes);
        Buffer buffer = reservedCnx.executionContext().bufferAllocator().newBuffer(capacity);
        writeRequestArraySize(buffer, len);
        RedisProtocolSupport.Command.PTTL.encodeTo(buffer);
        writeRequestArgument(buffer, key, keyBytes);
        final RedisRequest request = newRequest(RedisProtocolSupport.Command.PTTL, buffer);
        final Single<String> queued = reservedCnx.request(request, String.class);
        Future<Long> result = enqueueForExecute(state, singles, queued);
        return result;
    }

    @Override
    public Future<Long> publish(final CharSequence channel, final CharSequence message) {
        requireNonNull(channel);
        requireNonNull(message);
        final int len = 3;
        final int channelBytes = numberOfBytesUtf8(channel);
        final int messageBytes = numberOfBytesUtf8(message);
        final int capacity = calculateInitialCommandBufferSize(len, RedisProtocolSupport.Command.PUBLISH) +
                    calculateRequestArgumentSize(channelBytes) + calculateRequestArgumentSize(messageBytes);
        Buffer buffer = reservedCnx.executionContext().bufferAllocator().newBuffer(capacity);
        writeRequestArraySize(buffer, len);
        RedisProtocolSupport.Command.PUBLISH.encodeTo(buffer);
        writeRequestArgument(buffer, channel, channelBytes);
        writeRequestArgument(buffer, message, messageBytes);
        final RedisRequest request = newRequest(RedisProtocolSupport.Command.PUBLISH, buffer);
        final Single<String> queued = reservedCnx.request(request, String.class);
        Future<Long> result = enqueueForExecute(state, singles, queued);
        return result;
    }

    @Override
    public Future<List<String>> pubsubChannels() {
        final int len = 2;
        final int capacity = 1 + calculateInitialCommandBufferSize(len, RedisProtocolSupport.Command.PUBSUB);
        Buffer buffer = reservedCnx.executionContext().bufferAllocator().newBuffer(capacity);
        writeRequestArraySize(buffer, len);
        RedisProtocolSupport.Command.PUBSUB.encodeTo(buffer);
        RedisProtocolSupport.SubCommand.CHANNELS.encodeTo(buffer);
        final RedisRequest request = newRequest(RedisProtocolSupport.Command.PUBSUB, buffer);
        final Single<String> queued = reservedCnx.request(request, String.class);
        Future<List<String>> result = enqueueForExecute(state, singles, queued);
        return result;
    }

    @Override
    public Future<List<String>> pubsubChannels(@Nullable final CharSequence pattern) {
        final int len = 2 + (pattern == null ? 0 : 1);
        final int patternBytes = pattern == null ? 0 : numberOfBytesUtf8(pattern);
        final int capacity = 1 + calculateInitialCommandBufferSize(len, RedisProtocolSupport.Command.PUBSUB) +
                    (pattern == null ? 0 : calculateRequestArgumentSize(patternBytes));
        Buffer buffer = reservedCnx.executionContext().bufferAllocator().newBuffer(capacity);
        writeRequestArraySize(buffer, len);
        RedisProtocolSupport.Command.PUBSUB.encodeTo(buffer);
        RedisProtocolSupport.SubCommand.CHANNELS.encodeTo(buffer);
        if (pattern != null) {
            writeRequestArgument(buffer, pattern, patternBytes);
        }
        final RedisRequest request = newRequest(RedisProtocolSupport.Command.PUBSUB, buffer);
        final Single<String> queued = reservedCnx.request(request, String.class);
        Future<List<String>> result = enqueueForExecute(state, singles, queued);
        return result;
    }

    @Override
    public Future<List<String>> pubsubChannels(@Nullable final CharSequence pattern1,
                                               @Nullable final CharSequence pattern2) {
        final int len = 2 + (pattern1 == null ? 0 : 1) + (pattern2 == null ? 0 : 1);
        final int pattern1Bytes = pattern1 == null ? 0 : numberOfBytesUtf8(pattern1);
        final int pattern2Bytes = pattern2 == null ? 0 : numberOfBytesUtf8(pattern2);
        final int capacity = 1 + calculateInitialCommandBufferSize(len, RedisProtocolSupport.Command.PUBSUB) +
                    (pattern1 == null ? 0 : calculateRequestArgumentSize(pattern1Bytes)) +
                    (pattern2 == null ? 0 : calculateRequestArgumentSize(pattern2Bytes));
        Buffer buffer = reservedCnx.executionContext().bufferAllocator().newBuffer(capacity);
        writeRequestArraySize(buffer, len);
        RedisProtocolSupport.Command.PUBSUB.encodeTo(buffer);
        RedisProtocolSupport.SubCommand.CHANNELS.encodeTo(buffer);
        if (pattern1 != null) {
            writeRequestArgument(buffer, pattern1, pattern1Bytes);
        }
        if (pattern2 != null) {
            writeRequestArgument(buffer, pattern2, pattern2Bytes);
        }
        final RedisRequest request = newRequest(RedisProtocolSupport.Command.PUBSUB, buffer);
        final Single<String> queued = reservedCnx.request(request, String.class);
        Future<List<String>> result = enqueueForExecute(state, singles, queued);
        return result;
    }

    @Override
    public Future<List<String>> pubsubChannels(@Nullable final CharSequence pattern1,
                                               @Nullable final CharSequence pattern2,
                                               @Nullable final CharSequence pattern3) {
        final int len = 2 + (pattern1 == null ? 0 : 1) + (pattern2 == null ? 0 : 1) + (pattern3 == null ? 0 : 1);
        final int pattern1Bytes = pattern1 == null ? 0 : numberOfBytesUtf8(pattern1);
        final int pattern2Bytes = pattern2 == null ? 0 : numberOfBytesUtf8(pattern2);
        final int pattern3Bytes = pattern3 == null ? 0 : numberOfBytesUtf8(pattern3);
        final int capacity = 1 + calculateInitialCommandBufferSize(len, RedisProtocolSupport.Command.PUBSUB) +
                    (pattern1 == null ? 0 : calculateRequestArgumentSize(pattern1Bytes)) +
                    (pattern2 == null ? 0 : calculateRequestArgumentSize(pattern2Bytes)) +
                    (pattern3 == null ? 0 : calculateRequestArgumentSize(pattern3Bytes));
        Buffer buffer = reservedCnx.executionContext().bufferAllocator().newBuffer(capacity);
        writeRequestArraySize(buffer, len);
        RedisProtocolSupport.Command.PUBSUB.encodeTo(buffer);
        RedisProtocolSupport.SubCommand.CHANNELS.encodeTo(buffer);
        if (pattern1 != null) {
            writeRequestArgument(buffer, pattern1, pattern1Bytes);
        }
        if (pattern2 != null) {
            writeRequestArgument(buffer, pattern2, pattern2Bytes);
        }
        if (pattern3 != null) {
            writeRequestArgument(buffer, pattern3, pattern3Bytes);
        }
        final RedisRequest request = newRequest(RedisProtocolSupport.Command.PUBSUB, buffer);
        final Single<String> queued = reservedCnx.request(request, String.class);
        Future<List<String>> result = enqueueForExecute(state, singles, queued);
        return result;
    }

    @Override
    public Future<List<String>> pubsubChannels(final Collection<? extends CharSequence> patterns) {
        requireNonNull(patterns);
        final int len = 2 + patterns.size();
        int patternsCapacity = 0;
        if (patterns instanceof List && patterns instanceof RandomAccess) {
            final List<CharSequence> list = (List<CharSequence>) patterns;
            for (int i = 0; i < list.size(); ++i) {
                final CharSequence arg = list.get(i);
                patternsCapacity += estimateRequestArgumentSize(arg);
            }
        } else {
            for (CharSequence arg : patterns) {
                patternsCapacity += estimateRequestArgumentSize(arg);
            }
        }
        final int capacity = 1 + calculateInitialCommandBufferSize(len, RedisProtocolSupport.Command.PUBSUB) +
                    patternsCapacity;
        Buffer buffer = reservedCnx.executionContext().bufferAllocator().newBuffer(capacity);
        writeRequestArraySize(buffer, len);
        RedisProtocolSupport.Command.PUBSUB.encodeTo(buffer);
        RedisProtocolSupport.SubCommand.CHANNELS.encodeTo(buffer);
        if (patterns instanceof List && patterns instanceof RandomAccess) {
            final List<CharSequence> list = (List<CharSequence>) patterns;
            for (int i = 0; i < list.size(); ++i) {
                final CharSequence arg = list.get(i);
                writeRequestArgument(buffer, arg);
            }
        } else {
            for (CharSequence arg : patterns) {
                writeRequestArgument(buffer, arg);
            }
        }
        final RedisRequest request = newRequest(RedisProtocolSupport.Command.PUBSUB, buffer);
        final Single<String> queued = reservedCnx.request(request, String.class);
        Future<List<String>> result = enqueueForExecute(state, singles, queued);
        return result;
    }

    @Override
    public <T> Future<List<T>> pubsubNumsub() {
        final int len = 2;
        final int capacity = 1 + calculateInitialCommandBufferSize(len, RedisProtocolSupport.Command.PUBSUB);
        Buffer buffer = reservedCnx.executionContext().bufferAllocator().newBuffer(capacity);
        writeRequestArraySize(buffer, len);
        RedisProtocolSupport.Command.PUBSUB.encodeTo(buffer);
        RedisProtocolSupport.SubCommand.NUMSUB.encodeTo(buffer);
        final RedisRequest request = newRequest(RedisProtocolSupport.Command.PUBSUB, buffer);
        final Single<String> queued = reservedCnx.request(request, String.class);
        Future<List<T>> result = enqueueForExecute(state, singles, queued);
        return result;
    }

    @Override
    public <T> Future<List<T>> pubsubNumsub(@Nullable final CharSequence channel) {
        final int len = 2 + (channel == null ? 0 : 1);
        final int channelBytes = channel == null ? 0 : numberOfBytesUtf8(channel);
        final int capacity = 1 + calculateInitialCommandBufferSize(len, RedisProtocolSupport.Command.PUBSUB) +
                    (channel == null ? 0 : calculateRequestArgumentSize(channelBytes));
        Buffer buffer = reservedCnx.executionContext().bufferAllocator().newBuffer(capacity);
        writeRequestArraySize(buffer, len);
        RedisProtocolSupport.Command.PUBSUB.encodeTo(buffer);
        RedisProtocolSupport.SubCommand.NUMSUB.encodeTo(buffer);
        if (channel != null) {
            writeRequestArgument(buffer, channel, channelBytes);
        }
        final RedisRequest request = newRequest(RedisProtocolSupport.Command.PUBSUB, buffer);
        final Single<String> queued = reservedCnx.request(request, String.class);
        Future<List<T>> result = enqueueForExecute(state, singles, queued);
        return result;
    }

    @Override
    public <T> Future<List<T>> pubsubNumsub(@Nullable final CharSequence channel1,
                                            @Nullable final CharSequence channel2) {
        final int len = 2 + (channel1 == null ? 0 : 1) + (channel2 == null ? 0 : 1);
        final int channel1Bytes = channel1 == null ? 0 : numberOfBytesUtf8(channel1);
        final int channel2Bytes = channel2 == null ? 0 : numberOfBytesUtf8(channel2);
        final int capacity = 1 + calculateInitialCommandBufferSize(len, RedisProtocolSupport.Command.PUBSUB) +
                    (channel1 == null ? 0 : calculateRequestArgumentSize(channel1Bytes)) +
                    (channel2 == null ? 0 : calculateRequestArgumentSize(channel2Bytes));
        Buffer buffer = reservedCnx.executionContext().bufferAllocator().newBuffer(capacity);
        writeRequestArraySize(buffer, len);
        RedisProtocolSupport.Command.PUBSUB.encodeTo(buffer);
        RedisProtocolSupport.SubCommand.NUMSUB.encodeTo(buffer);
        if (channel1 != null) {
            writeRequestArgument(buffer, channel1, channel1Bytes);
        }
        if (channel2 != null) {
            writeRequestArgument(buffer, channel2, channel2Bytes);
        }
        final RedisRequest request = newRequest(RedisProtocolSupport.Command.PUBSUB, buffer);
        final Single<String> queued = reservedCnx.request(request, String.class);
        Future<List<T>> result = enqueueForExecute(state, singles, queued);
        return result;
    }

    @Override
    public <T> Future<List<T>> pubsubNumsub(@Nullable final CharSequence channel1,
                                            @Nullable final CharSequence channel2,
                                            @Nullable final CharSequence channel3) {
        final int len = 2 + (channel1 == null ? 0 : 1) + (channel2 == null ? 0 : 1) + (channel3 == null ? 0 : 1);
        final int channel1Bytes = channel1 == null ? 0 : numberOfBytesUtf8(channel1);
        final int channel2Bytes = channel2 == null ? 0 : numberOfBytesUtf8(channel2);
        final int channel3Bytes = channel3 == null ? 0 : numberOfBytesUtf8(channel3);
        final int capacity = 1 + calculateInitialCommandBufferSize(len, RedisProtocolSupport.Command.PUBSUB) +
                    (channel1 == null ? 0 : calculateRequestArgumentSize(channel1Bytes)) +
                    (channel2 == null ? 0 : calculateRequestArgumentSize(channel2Bytes)) +
                    (channel3 == null ? 0 : calculateRequestArgumentSize(channel3Bytes));
        Buffer buffer = reservedCnx.executionContext().bufferAllocator().newBuffer(capacity);
        writeRequestArraySize(buffer, len);
        RedisProtocolSupport.Command.PUBSUB.encodeTo(buffer);
        RedisProtocolSupport.SubCommand.NUMSUB.encodeTo(buffer);
        if (channel1 != null) {
            writeRequestArgument(buffer, channel1, channel1Bytes);
        }
        if (channel2 != null) {
            writeRequestArgument(buffer, channel2, channel2Bytes);
        }
        if (channel3 != null) {
            writeRequestArgument(buffer, channel3, channel3Bytes);
        }
        final RedisRequest request = newRequest(RedisProtocolSupport.Command.PUBSUB, buffer);
        final Single<String> queued = reservedCnx.request(request, String.class);
        Future<List<T>> result = enqueueForExecute(state, singles, queued);
        return result;
    }

    @Override
    public <T> Future<List<T>> pubsubNumsub(final Collection<? extends CharSequence> channels) {
        requireNonNull(channels);
        final int len = 2 + channels.size();
        int channelsCapacity = 0;
        if (channels instanceof List && channels instanceof RandomAccess) {
            final List<CharSequence> list = (List<CharSequence>) channels;
            for (int i = 0; i < list.size(); ++i) {
                final CharSequence arg = list.get(i);
                channelsCapacity += estimateRequestArgumentSize(arg);
            }
        } else {
            for (CharSequence arg : channels) {
                channelsCapacity += estimateRequestArgumentSize(arg);
            }
        }
        final int capacity = 1 + calculateInitialCommandBufferSize(len, RedisProtocolSupport.Command.PUBSUB) +
                    channelsCapacity;
        Buffer buffer = reservedCnx.executionContext().bufferAllocator().newBuffer(capacity);
        writeRequestArraySize(buffer, len);
        RedisProtocolSupport.Command.PUBSUB.encodeTo(buffer);
        RedisProtocolSupport.SubCommand.NUMSUB.encodeTo(buffer);
        if (channels instanceof List && channels instanceof RandomAccess) {
            final List<CharSequence> list = (List<CharSequence>) channels;
            for (int i = 0; i < list.size(); ++i) {
                final CharSequence arg = list.get(i);
                writeRequestArgument(buffer, arg);
            }
        } else {
            for (CharSequence arg : channels) {
                writeRequestArgument(buffer, arg);
            }
        }
        final RedisRequest request = newRequest(RedisProtocolSupport.Command.PUBSUB, buffer);
        final Single<String> queued = reservedCnx.request(request, String.class);
        Future<List<T>> result = enqueueForExecute(state, singles, queued);
        return result;
    }

    @Override
    public Future<Long> pubsubNumpat() {
        final int len = 2;
        final int capacity = 1 + calculateInitialCommandBufferSize(len, RedisProtocolSupport.Command.PUBSUB);
        Buffer buffer = reservedCnx.executionContext().bufferAllocator().newBuffer(capacity);
        writeRequestArraySize(buffer, len);
        RedisProtocolSupport.Command.PUBSUB.encodeTo(buffer);
        RedisProtocolSupport.SubCommand.NUMPAT.encodeTo(buffer);
        final RedisRequest request = newRequest(RedisProtocolSupport.Command.PUBSUB, buffer);
        final Single<String> queued = reservedCnx.request(request, String.class);
        Future<Long> result = enqueueForExecute(state, singles, queued);
        return result;
    }

    @Override
    public Future<String> randomkey() {
        final int len = 1;
        final int capacity = calculateInitialCommandBufferSize(len, RedisProtocolSupport.Command.RANDOMKEY);
        Buffer buffer = reservedCnx.executionContext().bufferAllocator().newBuffer(capacity);
        writeRequestArraySize(buffer, len);
        RedisProtocolSupport.Command.RANDOMKEY.encodeTo(buffer);
        final RedisRequest request = newRequest(RedisProtocolSupport.Command.RANDOMKEY, buffer);
        final Single<String> queued = reservedCnx.request(request, String.class);
        Future<String> result = enqueueForExecute(state, singles, queued);
        return result;
    }

    @Override
    public Future<String> readonly() {
        final int len = 1;
        final int capacity = calculateInitialCommandBufferSize(len, RedisProtocolSupport.Command.READONLY);
        Buffer buffer = reservedCnx.executionContext().bufferAllocator().newBuffer(capacity);
        writeRequestArraySize(buffer, len);
        RedisProtocolSupport.Command.READONLY.encodeTo(buffer);
        final RedisRequest request = newRequest(RedisProtocolSupport.Command.READONLY, buffer);
        final Single<String> queued = reservedCnx.request(request, String.class);
        Future<String> result = enqueueForExecute(state, singles, queued);
        return result;
    }

    @Override
    public Future<String> readwrite() {
        final int len = 1;
        final int capacity = calculateInitialCommandBufferSize(len, RedisProtocolSupport.Command.READWRITE);
        Buffer buffer = reservedCnx.executionContext().bufferAllocator().newBuffer(capacity);
        writeRequestArraySize(buffer, len);
        RedisProtocolSupport.Command.READWRITE.encodeTo(buffer);
        final RedisRequest request = newRequest(RedisProtocolSupport.Command.READWRITE, buffer);
        final Single<String> queued = reservedCnx.request(request, String.class);
        Future<String> result = enqueueForExecute(state, singles, queued);
        return result;
    }

    @Override
    public Future<String> rename(@RedisProtocolSupport.Key final CharSequence key,
                                 @RedisProtocolSupport.Key final CharSequence newkey) {
        requireNonNull(key);
        requireNonNull(newkey);
        final int len = 3;
        final int keyBytes = numberOfBytesUtf8(key);
        final int newkeyBytes = numberOfBytesUtf8(newkey);
        final int capacity = calculateInitialCommandBufferSize(len, RedisProtocolSupport.Command.RENAME) +
                    calculateRequestArgumentSize(keyBytes) + calculateRequestArgumentSize(newkeyBytes);
        Buffer buffer = reservedCnx.executionContext().bufferAllocator().newBuffer(capacity);
        writeRequestArraySize(buffer, len);
        RedisProtocolSupport.Command.RENAME.encodeTo(buffer);
        writeRequestArgument(buffer, key, keyBytes);
        writeRequestArgument(buffer, newkey, newkeyBytes);
        final RedisRequest request = newRequest(RedisProtocolSupport.Command.RENAME, buffer);
        final Single<String> queued = reservedCnx.request(request, String.class);
        Future<String> result = enqueueForExecute(state, singles, queued);
        return result;
    }

    @Override
    public Future<Long> renamenx(@RedisProtocolSupport.Key final CharSequence key,
                                 @RedisProtocolSupport.Key final CharSequence newkey) {
        requireNonNull(key);
        requireNonNull(newkey);
        final int len = 3;
        final int keyBytes = numberOfBytesUtf8(key);
        final int newkeyBytes = numberOfBytesUtf8(newkey);
        final int capacity = calculateInitialCommandBufferSize(len, RedisProtocolSupport.Command.RENAMENX) +
                    calculateRequestArgumentSize(keyBytes) + calculateRequestArgumentSize(newkeyBytes);
        Buffer buffer = reservedCnx.executionContext().bufferAllocator().newBuffer(capacity);
        writeRequestArraySize(buffer, len);
        RedisProtocolSupport.Command.RENAMENX.encodeTo(buffer);
        writeRequestArgument(buffer, key, keyBytes);
        writeRequestArgument(buffer, newkey, newkeyBytes);
        final RedisRequest request = newRequest(RedisProtocolSupport.Command.RENAMENX, buffer);
        final Single<String> queued = reservedCnx.request(request, String.class);
        Future<Long> result = enqueueForExecute(state, singles, queued);
        return result;
    }

    @Override
    public Future<String> restore(@RedisProtocolSupport.Key final CharSequence key, final long ttl,
                                  final CharSequence serializedValue) {
        requireNonNull(key);
        requireNonNull(serializedValue);
        final int len = 4;
        final int keyBytes = numberOfBytesUtf8(key);
        final int serializedValueBytes = numberOfBytesUtf8(serializedValue);
        final int capacity = calculateInitialCommandBufferSize(len, RedisProtocolSupport.Command.RESTORE) +
                    calculateRequestArgumentSize(keyBytes) + calculateRequestArgumentSize(ttl) +
                    calculateRequestArgumentSize(serializedValueBytes);
        Buffer buffer = reservedCnx.executionContext().bufferAllocator().newBuffer(capacity);
        writeRequestArraySize(buffer, len);
        RedisProtocolSupport.Command.RESTORE.encodeTo(buffer);
        writeRequestArgument(buffer, key, keyBytes);
        writeRequestArgument(buffer, ttl);
        writeRequestArgument(buffer, serializedValue, serializedValueBytes);
        final RedisRequest request = newRequest(RedisProtocolSupport.Command.RESTORE, buffer);
        final Single<String> queued = reservedCnx.request(request, String.class);
        Future<String> result = enqueueForExecute(state, singles, queued);
        return result;
    }

    @Override
    public Future<String> restore(@RedisProtocolSupport.Key final CharSequence key, final long ttl,
                                  final CharSequence serializedValue,
                                  @Nullable final RedisProtocolSupport.RestoreReplace replace) {
        requireNonNull(key);
        requireNonNull(serializedValue);
        final int len = 4 + (replace == null ? 0 : 1);
        final int keyBytes = numberOfBytesUtf8(key);
        final int serializedValueBytes = numberOfBytesUtf8(serializedValue);
        final int capacity = calculateInitialCommandBufferSize(len, RedisProtocolSupport.Command.RESTORE) +
                    calculateRequestArgumentSize(keyBytes) + calculateRequestArgumentSize(ttl) +
                    calculateRequestArgumentSize(serializedValueBytes) +
                    (replace == null ? 0 : replace.encodedByteCount());
        Buffer buffer = reservedCnx.executionContext().bufferAllocator().newBuffer(capacity);
        writeRequestArraySize(buffer, len);
        RedisProtocolSupport.Command.RESTORE.encodeTo(buffer);
        writeRequestArgument(buffer, key, keyBytes);
        writeRequestArgument(buffer, ttl);
        writeRequestArgument(buffer, serializedValue, serializedValueBytes);
        if (replace != null) {
            replace.encodeTo(buffer);
        }
        final RedisRequest request = newRequest(RedisProtocolSupport.Command.RESTORE, buffer);
        final Single<String> queued = reservedCnx.request(request, String.class);
        Future<String> result = enqueueForExecute(state, singles, queued);
        return result;
    }

    @Override
    public <T> Future<List<T>> role() {
        final int len = 1;
        final int capacity = calculateInitialCommandBufferSize(len, RedisProtocolSupport.Command.ROLE);
        Buffer buffer = reservedCnx.executionContext().bufferAllocator().newBuffer(capacity);
        writeRequestArraySize(buffer, len);
        RedisProtocolSupport.Command.ROLE.encodeTo(buffer);
        final RedisRequest request = newRequest(RedisProtocolSupport.Command.ROLE, buffer);
        final Single<String> queued = reservedCnx.request(request, String.class);
        Future<List<T>> result = enqueueForExecute(state, singles, queued);
        return result;
    }

    @Override
    public Future<String> rpop(@RedisProtocolSupport.Key final CharSequence key) {
        requireNonNull(key);
        final int len = 2;
        final int keyBytes = numberOfBytesUtf8(key);
        final int capacity = calculateInitialCommandBufferSize(len, RedisProtocolSupport.Command.RPOP) +
                    calculateRequestArgumentSize(keyBytes);
        Buffer buffer = reservedCnx.executionContext().bufferAllocator().newBuffer(capacity);
        writeRequestArraySize(buffer, len);
        RedisProtocolSupport.Command.RPOP.encodeTo(buffer);
        writeRequestArgument(buffer, key, keyBytes);
        final RedisRequest request = newRequest(RedisProtocolSupport.Command.RPOP, buffer);
        final Single<String> queued = reservedCnx.request(request, String.class);
        Future<String> result = enqueueForExecute(state, singles, queued);
        return result;
    }

    @Override
    public Future<String> rpoplpush(@RedisProtocolSupport.Key final CharSequence source,
                                    @RedisProtocolSupport.Key final CharSequence destination) {
        requireNonNull(source);
        requireNonNull(destination);
        final int len = 3;
        final int sourceBytes = numberOfBytesUtf8(source);
        final int destinationBytes = numberOfBytesUtf8(destination);
        final int capacity = calculateInitialCommandBufferSize(len, RedisProtocolSupport.Command.RPOPLPUSH) +
                    calculateRequestArgumentSize(sourceBytes) + calculateRequestArgumentSize(destinationBytes);
        Buffer buffer = reservedCnx.executionContext().bufferAllocator().newBuffer(capacity);
        writeRequestArraySize(buffer, len);
        RedisProtocolSupport.Command.RPOPLPUSH.encodeTo(buffer);
        writeRequestArgument(buffer, source, sourceBytes);
        writeRequestArgument(buffer, destination, destinationBytes);
        final RedisRequest request = newRequest(RedisProtocolSupport.Command.RPOPLPUSH, buffer);
        final Single<String> queued = reservedCnx.request(request, String.class);
        Future<String> result = enqueueForExecute(state, singles, queued);
        return result;
    }

    @Override
    public Future<Long> rpush(@RedisProtocolSupport.Key final CharSequence key, final CharSequence value) {
        requireNonNull(key);
        requireNonNull(value);
        final int len = 3;
        final int keyBytes = numberOfBytesUtf8(key);
        final int valueBytes = numberOfBytesUtf8(value);
        final int capacity = calculateInitialCommandBufferSize(len, RedisProtocolSupport.Command.RPUSH) +
                    calculateRequestArgumentSize(keyBytes) + calculateRequestArgumentSize(valueBytes);
        Buffer buffer = reservedCnx.executionContext().bufferAllocator().newBuffer(capacity);
        writeRequestArraySize(buffer, len);
        RedisProtocolSupport.Command.RPUSH.encodeTo(buffer);
        writeRequestArgument(buffer, key, keyBytes);
        writeRequestArgument(buffer, value, valueBytes);
        final RedisRequest request = newRequest(RedisProtocolSupport.Command.RPUSH, buffer);
        final Single<String> queued = reservedCnx.request(request, String.class);
        Future<Long> result = enqueueForExecute(state, singles, queued);
        return result;
    }

    @Override
    public Future<Long> rpush(@RedisProtocolSupport.Key final CharSequence key, final CharSequence value1,
                              final CharSequence value2) {
        requireNonNull(key);
        requireNonNull(value1);
        requireNonNull(value2);
        final int len = 4;
        final int keyBytes = numberOfBytesUtf8(key);
        final int value1Bytes = numberOfBytesUtf8(value1);
        final int value2Bytes = numberOfBytesUtf8(value2);
        final int capacity = calculateInitialCommandBufferSize(len, RedisProtocolSupport.Command.RPUSH) +
                    calculateRequestArgumentSize(keyBytes) + calculateRequestArgumentSize(value1Bytes) +
                    calculateRequestArgumentSize(value2Bytes);
        Buffer buffer = reservedCnx.executionContext().bufferAllocator().newBuffer(capacity);
        writeRequestArraySize(buffer, len);
        RedisProtocolSupport.Command.RPUSH.encodeTo(buffer);
        writeRequestArgument(buffer, key, keyBytes);
        writeRequestArgument(buffer, value1, value1Bytes);
        writeRequestArgument(buffer, value2, value2Bytes);
        final RedisRequest request = newRequest(RedisProtocolSupport.Command.RPUSH, buffer);
        final Single<String> queued = reservedCnx.request(request, String.class);
        Future<Long> result = enqueueForExecute(state, singles, queued);
        return result;
    }

    @Override
    public Future<Long> rpush(@RedisProtocolSupport.Key final CharSequence key, final CharSequence value1,
                              final CharSequence value2, final CharSequence value3) {
        requireNonNull(key);
        requireNonNull(value1);
        requireNonNull(value2);
        requireNonNull(value3);
        final int len = 5;
        final int keyBytes = numberOfBytesUtf8(key);
        final int value1Bytes = numberOfBytesUtf8(value1);
        final int value2Bytes = numberOfBytesUtf8(value2);
        final int value3Bytes = numberOfBytesUtf8(value3);
        final int capacity = calculateInitialCommandBufferSize(len, RedisProtocolSupport.Command.RPUSH) +
                    calculateRequestArgumentSize(keyBytes) + calculateRequestArgumentSize(value1Bytes) +
                    calculateRequestArgumentSize(value2Bytes) + calculateRequestArgumentSize(value3Bytes);
        Buffer buffer = reservedCnx.executionContext().bufferAllocator().newBuffer(capacity);
        writeRequestArraySize(buffer, len);
        RedisProtocolSupport.Command.RPUSH.encodeTo(buffer);
        writeRequestArgument(buffer, key, keyBytes);
        writeRequestArgument(buffer, value1, value1Bytes);
        writeRequestArgument(buffer, value2, value2Bytes);
        writeRequestArgument(buffer, value3, value3Bytes);
        final RedisRequest request = newRequest(RedisProtocolSupport.Command.RPUSH, buffer);
        final Single<String> queued = reservedCnx.request(request, String.class);
        Future<Long> result = enqueueForExecute(state, singles, queued);
        return result;
    }

    @Override
    public Future<Long> rpush(@RedisProtocolSupport.Key final CharSequence key,
                              final Collection<? extends CharSequence> values) {
        requireNonNull(key);
        requireNonNull(values);
        final int len = 2 + values.size();
        final int keyBytes = numberOfBytesUtf8(key);
        int valuesCapacity = 0;
        if (values instanceof List && values instanceof RandomAccess) {
            final List<CharSequence> list = (List<CharSequence>) values;
            for (int i = 0; i < list.size(); ++i) {
                final CharSequence arg = list.get(i);
                valuesCapacity += estimateRequestArgumentSize(arg);
            }
        } else {
            for (CharSequence arg : values) {
                valuesCapacity += estimateRequestArgumentSize(arg);
            }
        }
        final int capacity = calculateInitialCommandBufferSize(len, RedisProtocolSupport.Command.RPUSH) +
                    calculateRequestArgumentSize(keyBytes) + valuesCapacity;
        Buffer buffer = reservedCnx.executionContext().bufferAllocator().newBuffer(capacity);
        writeRequestArraySize(buffer, len);
        RedisProtocolSupport.Command.RPUSH.encodeTo(buffer);
        writeRequestArgument(buffer, key, keyBytes);
        if (values instanceof List && values instanceof RandomAccess) {
            final List<CharSequence> list = (List<CharSequence>) values;
            for (int i = 0; i < list.size(); ++i) {
                final CharSequence arg = list.get(i);
                writeRequestArgument(buffer, arg);
            }
        } else {
            for (CharSequence arg : values) {
                writeRequestArgument(buffer, arg);
            }
        }
        final RedisRequest request = newRequest(RedisProtocolSupport.Command.RPUSH, buffer);
        final Single<String> queued = reservedCnx.request(request, String.class);
        Future<Long> result = enqueueForExecute(state, singles, queued);
        return result;
    }

    @Override
    public Future<Long> rpushx(@RedisProtocolSupport.Key final CharSequence key, final CharSequence value) {
        requireNonNull(key);
        requireNonNull(value);
        final int len = 3;
        final int keyBytes = numberOfBytesUtf8(key);
        final int valueBytes = numberOfBytesUtf8(value);
        final int capacity = calculateInitialCommandBufferSize(len, RedisProtocolSupport.Command.RPUSHX) +
                    calculateRequestArgumentSize(keyBytes) + calculateRequestArgumentSize(valueBytes);
        Buffer buffer = reservedCnx.executionContext().bufferAllocator().newBuffer(capacity);
        writeRequestArraySize(buffer, len);
        RedisProtocolSupport.Command.RPUSHX.encodeTo(buffer);
        writeRequestArgument(buffer, key, keyBytes);
        writeRequestArgument(buffer, value, valueBytes);
        final RedisRequest request = newRequest(RedisProtocolSupport.Command.RPUSHX, buffer);
        final Single<String> queued = reservedCnx.request(request, String.class);
        Future<Long> result = enqueueForExecute(state, singles, queued);
        return result;
    }

    @Override
    public Future<Long> sadd(@RedisProtocolSupport.Key final CharSequence key, final CharSequence member) {
        requireNonNull(key);
        requireNonNull(member);
        final int len = 3;
        final int keyBytes = numberOfBytesUtf8(key);
        final int memberBytes = numberOfBytesUtf8(member);
        final int capacity = calculateInitialCommandBufferSize(len, RedisProtocolSupport.Command.SADD) +
                    calculateRequestArgumentSize(keyBytes) + calculateRequestArgumentSize(memberBytes);
        Buffer buffer = reservedCnx.executionContext().bufferAllocator().newBuffer(capacity);
        writeRequestArraySize(buffer, len);
        RedisProtocolSupport.Command.SADD.encodeTo(buffer);
        writeRequestArgument(buffer, key, keyBytes);
        writeRequestArgument(buffer, member, memberBytes);
        final RedisRequest request = newRequest(RedisProtocolSupport.Command.SADD, buffer);
        final Single<String> queued = reservedCnx.request(request, String.class);
        Future<Long> result = enqueueForExecute(state, singles, queued);
        return result;
    }

    @Override
    public Future<Long> sadd(@RedisProtocolSupport.Key final CharSequence key, final CharSequence member1,
                             final CharSequence member2) {
        requireNonNull(key);
        requireNonNull(member1);
        requireNonNull(member2);
        final int len = 4;
        final int keyBytes = numberOfBytesUtf8(key);
        final int member1Bytes = numberOfBytesUtf8(member1);
        final int member2Bytes = numberOfBytesUtf8(member2);
        final int capacity = calculateInitialCommandBufferSize(len, RedisProtocolSupport.Command.SADD) +
                    calculateRequestArgumentSize(keyBytes) + calculateRequestArgumentSize(member1Bytes) +
                    calculateRequestArgumentSize(member2Bytes);
        Buffer buffer = reservedCnx.executionContext().bufferAllocator().newBuffer(capacity);
        writeRequestArraySize(buffer, len);
        RedisProtocolSupport.Command.SADD.encodeTo(buffer);
        writeRequestArgument(buffer, key, keyBytes);
        writeRequestArgument(buffer, member1, member1Bytes);
        writeRequestArgument(buffer, member2, member2Bytes);
        final RedisRequest request = newRequest(RedisProtocolSupport.Command.SADD, buffer);
        final Single<String> queued = reservedCnx.request(request, String.class);
        Future<Long> result = enqueueForExecute(state, singles, queued);
        return result;
    }

    @Override
    public Future<Long> sadd(@RedisProtocolSupport.Key final CharSequence key, final CharSequence member1,
                             final CharSequence member2, final CharSequence member3) {
        requireNonNull(key);
        requireNonNull(member1);
        requireNonNull(member2);
        requireNonNull(member3);
        final int len = 5;
        final int keyBytes = numberOfBytesUtf8(key);
        final int member1Bytes = numberOfBytesUtf8(member1);
        final int member2Bytes = numberOfBytesUtf8(member2);
        final int member3Bytes = numberOfBytesUtf8(member3);
        final int capacity = calculateInitialCommandBufferSize(len, RedisProtocolSupport.Command.SADD) +
                    calculateRequestArgumentSize(keyBytes) + calculateRequestArgumentSize(member1Bytes) +
                    calculateRequestArgumentSize(member2Bytes) + calculateRequestArgumentSize(member3Bytes);
        Buffer buffer = reservedCnx.executionContext().bufferAllocator().newBuffer(capacity);
        writeRequestArraySize(buffer, len);
        RedisProtocolSupport.Command.SADD.encodeTo(buffer);
        writeRequestArgument(buffer, key, keyBytes);
        writeRequestArgument(buffer, member1, member1Bytes);
        writeRequestArgument(buffer, member2, member2Bytes);
        writeRequestArgument(buffer, member3, member3Bytes);
        final RedisRequest request = newRequest(RedisProtocolSupport.Command.SADD, buffer);
        final Single<String> queued = reservedCnx.request(request, String.class);
        Future<Long> result = enqueueForExecute(state, singles, queued);
        return result;
    }

    @Override
    public Future<Long> sadd(@RedisProtocolSupport.Key final CharSequence key,
                             final Collection<? extends CharSequence> members) {
        requireNonNull(key);
        requireNonNull(members);
        final int len = 2 + members.size();
        final int keyBytes = numberOfBytesUtf8(key);
        int membersCapacity = 0;
        if (members instanceof List && members instanceof RandomAccess) {
            final List<CharSequence> list = (List<CharSequence>) members;
            for (int i = 0; i < list.size(); ++i) {
                final CharSequence arg = list.get(i);
                membersCapacity += estimateRequestArgumentSize(arg);
            }
        } else {
            for (CharSequence arg : members) {
                membersCapacity += estimateRequestArgumentSize(arg);
            }
        }
        final int capacity = calculateInitialCommandBufferSize(len, RedisProtocolSupport.Command.SADD) +
                    calculateRequestArgumentSize(keyBytes) + membersCapacity;
        Buffer buffer = reservedCnx.executionContext().bufferAllocator().newBuffer(capacity);
        writeRequestArraySize(buffer, len);
        RedisProtocolSupport.Command.SADD.encodeTo(buffer);
        writeRequestArgument(buffer, key, keyBytes);
        if (members instanceof List && members instanceof RandomAccess) {
            final List<CharSequence> list = (List<CharSequence>) members;
            for (int i = 0; i < list.size(); ++i) {
                final CharSequence arg = list.get(i);
                writeRequestArgument(buffer, arg);
            }
        } else {
            for (CharSequence arg : members) {
                writeRequestArgument(buffer, arg);
            }
        }
        final RedisRequest request = newRequest(RedisProtocolSupport.Command.SADD, buffer);
        final Single<String> queued = reservedCnx.request(request, String.class);
        Future<Long> result = enqueueForExecute(state, singles, queued);
        return result;
    }

    @Override
    public Future<String> save() {
        final int len = 1;
        final int capacity = calculateInitialCommandBufferSize(len, RedisProtocolSupport.Command.SAVE);
        Buffer buffer = reservedCnx.executionContext().bufferAllocator().newBuffer(capacity);
        writeRequestArraySize(buffer, len);
        RedisProtocolSupport.Command.SAVE.encodeTo(buffer);
        final RedisRequest request = newRequest(RedisProtocolSupport.Command.SAVE, buffer);
        final Single<String> queued = reservedCnx.request(request, String.class);
        Future<String> result = enqueueForExecute(state, singles, queued);
        return result;
    }

    @Override
    public <T> Future<List<T>> scan(final long cursor) {
        final int len = 2;
        final int capacity = calculateInitialCommandBufferSize(len, RedisProtocolSupport.Command.SCAN) +
                    calculateRequestArgumentSize(cursor);
        Buffer buffer = reservedCnx.executionContext().bufferAllocator().newBuffer(capacity);
        writeRequestArraySize(buffer, len);
        RedisProtocolSupport.Command.SCAN.encodeTo(buffer);
        writeRequestArgument(buffer, cursor);
        final RedisRequest request = newRequest(RedisProtocolSupport.Command.SCAN, buffer);
        final Single<String> queued = reservedCnx.request(request, String.class);
        Future<List<T>> result = enqueueForExecute(state, singles, queued);
        return result;
    }

    @Override
    public <T> Future<List<T>> scan(final long cursor, @Nullable final CharSequence matchPattern,
                                    @Nullable final Long count) {
        final int len = 2 + (matchPattern == null ? 0 : 2) + (count == null ? 0 : 2);
        final int matchPatternBytes = matchPattern == null ? 0 : numberOfBytesUtf8(matchPattern);
        final int capacity = calculateInitialCommandBufferSize(len, RedisProtocolSupport.Command.SCAN) +
                    calculateRequestArgumentSize(cursor) +
                    (matchPattern == null ? 0 : RedisProtocolSupport.SubCommand.MATCH.encodedByteCount()) +
                    (matchPattern == null ? 0 : calculateRequestArgumentSize(matchPatternBytes)) +
                    (count == null ? 0 : RedisProtocolSupport.SubCommand.COUNT.encodedByteCount()) +
                    (count == null ? 0 : calculateRequestArgumentSize(count));
        Buffer buffer = reservedCnx.executionContext().bufferAllocator().newBuffer(capacity);
        writeRequestArraySize(buffer, len);
        RedisProtocolSupport.Command.SCAN.encodeTo(buffer);
        writeRequestArgument(buffer, cursor);
        if (matchPattern != null) {
            RedisProtocolSupport.SubCommand.MATCH.encodeTo(buffer);
            writeRequestArgument(buffer, matchPattern, matchPatternBytes);
        }
        if (count != null) {
            RedisProtocolSupport.SubCommand.COUNT.encodeTo(buffer);
            writeRequestArgument(buffer, count);
        }
        final RedisRequest request = newRequest(RedisProtocolSupport.Command.SCAN, buffer);
        final Single<String> queued = reservedCnx.request(request, String.class);
        Future<List<T>> result = enqueueForExecute(state, singles, queued);
        return result;
    }

    @Override
    public Future<Long> scard(@RedisProtocolSupport.Key final CharSequence key) {
        requireNonNull(key);
        final int len = 2;
        final int keyBytes = numberOfBytesUtf8(key);
        final int capacity = calculateInitialCommandBufferSize(len, RedisProtocolSupport.Command.SCARD) +
                    calculateRequestArgumentSize(keyBytes);
        Buffer buffer = reservedCnx.executionContext().bufferAllocator().newBuffer(capacity);
        writeRequestArraySize(buffer, len);
        RedisProtocolSupport.Command.SCARD.encodeTo(buffer);
        writeRequestArgument(buffer, key, keyBytes);
        final RedisRequest request = newRequest(RedisProtocolSupport.Command.SCARD, buffer);
        final Single<String> queued = reservedCnx.request(request, String.class);
        Future<Long> result = enqueueForExecute(state, singles, queued);
        return result;
    }

    @Override
    public Future<String> scriptDebug(final RedisProtocolSupport.ScriptDebugMode mode) {
        requireNonNull(mode);
        final int len = 3;
        final int capacity = 1 + calculateInitialCommandBufferSize(len, RedisProtocolSupport.Command.SCRIPT) +
                    mode.encodedByteCount();
        Buffer buffer = reservedCnx.executionContext().bufferAllocator().newBuffer(capacity);
        writeRequestArraySize(buffer, len);
        RedisProtocolSupport.Command.SCRIPT.encodeTo(buffer);
        RedisProtocolSupport.SubCommand.DEBUG.encodeTo(buffer);
        mode.encodeTo(buffer);
        final RedisRequest request = newRequest(RedisProtocolSupport.Command.SCRIPT, buffer);
        final Single<String> queued = reservedCnx.request(request, String.class);
        Future<String> result = enqueueForExecute(state, singles, queued);
        return result;
    }

    @Override
    public <T> Future<List<T>> scriptExists(final CharSequence sha1) {
        requireNonNull(sha1);
        final int len = 3;
        final int sha1Bytes = numberOfBytesUtf8(sha1);
        final int capacity = 1 + calculateInitialCommandBufferSize(len, RedisProtocolSupport.Command.SCRIPT) +
                    calculateRequestArgumentSize(sha1Bytes);
        Buffer buffer = reservedCnx.executionContext().bufferAllocator().newBuffer(capacity);
        writeRequestArraySize(buffer, len);
        RedisProtocolSupport.Command.SCRIPT.encodeTo(buffer);
        RedisProtocolSupport.SubCommand.EXISTS.encodeTo(buffer);
        writeRequestArgument(buffer, sha1, sha1Bytes);
        final RedisRequest request = newRequest(RedisProtocolSupport.Command.SCRIPT, buffer);
        final Single<String> queued = reservedCnx.request(request, String.class);
        Future<List<T>> result = enqueueForExecute(state, singles, queued);
        return result;
    }

    @Override
    public <T> Future<List<T>> scriptExists(final CharSequence sha11, final CharSequence sha12) {
        requireNonNull(sha11);
        requireNonNull(sha12);
        final int len = 4;
        final int sha11Bytes = numberOfBytesUtf8(sha11);
        final int sha12Bytes = numberOfBytesUtf8(sha12);
        final int capacity = 1 + calculateInitialCommandBufferSize(len, RedisProtocolSupport.Command.SCRIPT) +
                    calculateRequestArgumentSize(sha11Bytes) + calculateRequestArgumentSize(sha12Bytes);
        Buffer buffer = reservedCnx.executionContext().bufferAllocator().newBuffer(capacity);
        writeRequestArraySize(buffer, len);
        RedisProtocolSupport.Command.SCRIPT.encodeTo(buffer);
        RedisProtocolSupport.SubCommand.EXISTS.encodeTo(buffer);
        writeRequestArgument(buffer, sha11, sha11Bytes);
        writeRequestArgument(buffer, sha12, sha12Bytes);
        final RedisRequest request = newRequest(RedisProtocolSupport.Command.SCRIPT, buffer);
        final Single<String> queued = reservedCnx.request(request, String.class);
        Future<List<T>> result = enqueueForExecute(state, singles, queued);
        return result;
    }

    @Override
    public <T> Future<List<T>> scriptExists(final CharSequence sha11, final CharSequence sha12,
                                            final CharSequence sha13) {
        requireNonNull(sha11);
        requireNonNull(sha12);
        requireNonNull(sha13);
        final int len = 5;
        final int sha11Bytes = numberOfBytesUtf8(sha11);
        final int sha12Bytes = numberOfBytesUtf8(sha12);
        final int sha13Bytes = numberOfBytesUtf8(sha13);
        final int capacity = 1 + calculateInitialCommandBufferSize(len, RedisProtocolSupport.Command.SCRIPT) +
                    calculateRequestArgumentSize(sha11Bytes) + calculateRequestArgumentSize(sha12Bytes) +
                    calculateRequestArgumentSize(sha13Bytes);
        Buffer buffer = reservedCnx.executionContext().bufferAllocator().newBuffer(capacity);
        writeRequestArraySize(buffer, len);
        RedisProtocolSupport.Command.SCRIPT.encodeTo(buffer);
        RedisProtocolSupport.SubCommand.EXISTS.encodeTo(buffer);
        writeRequestArgument(buffer, sha11, sha11Bytes);
        writeRequestArgument(buffer, sha12, sha12Bytes);
        writeRequestArgument(buffer, sha13, sha13Bytes);
        final RedisRequest request = newRequest(RedisProtocolSupport.Command.SCRIPT, buffer);
        final Single<String> queued = reservedCnx.request(request, String.class);
        Future<List<T>> result = enqueueForExecute(state, singles, queued);
        return result;
    }

    @Override
    public <T> Future<List<T>> scriptExists(final Collection<? extends CharSequence> sha1s) {
        requireNonNull(sha1s);
        final int len = 2 + sha1s.size();
        int sha1sCapacity = 0;
        if (sha1s instanceof List && sha1s instanceof RandomAccess) {
            final List<CharSequence> list = (List<CharSequence>) sha1s;
            for (int i = 0; i < list.size(); ++i) {
                final CharSequence arg = list.get(i);
                sha1sCapacity += estimateRequestArgumentSize(arg);
            }
        } else {
            for (CharSequence arg : sha1s) {
                sha1sCapacity += estimateRequestArgumentSize(arg);
            }
        }
        final int capacity = 1 + calculateInitialCommandBufferSize(len, RedisProtocolSupport.Command.SCRIPT) +
                    sha1sCapacity;
        Buffer buffer = reservedCnx.executionContext().bufferAllocator().newBuffer(capacity);
        writeRequestArraySize(buffer, len);
        RedisProtocolSupport.Command.SCRIPT.encodeTo(buffer);
        RedisProtocolSupport.SubCommand.EXISTS.encodeTo(buffer);
        if (sha1s instanceof List && sha1s instanceof RandomAccess) {
            final List<CharSequence> list = (List<CharSequence>) sha1s;
            for (int i = 0; i < list.size(); ++i) {
                final CharSequence arg = list.get(i);
                writeRequestArgument(buffer, arg);
            }
        } else {
            for (CharSequence arg : sha1s) {
                writeRequestArgument(buffer, arg);
            }
        }
        final RedisRequest request = newRequest(RedisProtocolSupport.Command.SCRIPT, buffer);
        final Single<String> queued = reservedCnx.request(request, String.class);
        Future<List<T>> result = enqueueForExecute(state, singles, queued);
        return result;
    }

    @Override
    public Future<String> scriptFlush() {
        final int len = 2;
        final int capacity = 1 + calculateInitialCommandBufferSize(len, RedisProtocolSupport.Command.SCRIPT);
        Buffer buffer = reservedCnx.executionContext().bufferAllocator().newBuffer(capacity);
        writeRequestArraySize(buffer, len);
        RedisProtocolSupport.Command.SCRIPT.encodeTo(buffer);
        RedisProtocolSupport.SubCommand.FLUSH.encodeTo(buffer);
        final RedisRequest request = newRequest(RedisProtocolSupport.Command.SCRIPT, buffer);
        final Single<String> queued = reservedCnx.request(request, String.class);
        Future<String> result = enqueueForExecute(state, singles, queued);
        return result;
    }

    @Override
    public Future<String> scriptKill() {
        final int len = 2;
        final int capacity = 1 + calculateInitialCommandBufferSize(len, RedisProtocolSupport.Command.SCRIPT);
        Buffer buffer = reservedCnx.executionContext().bufferAllocator().newBuffer(capacity);
        writeRequestArraySize(buffer, len);
        RedisProtocolSupport.Command.SCRIPT.encodeTo(buffer);
        RedisProtocolSupport.SubCommand.KILL.encodeTo(buffer);
        final RedisRequest request = newRequest(RedisProtocolSupport.Command.SCRIPT, buffer);
        final Single<String> queued = reservedCnx.request(request, String.class);
        Future<String> result = enqueueForExecute(state, singles, queued);
        return result;
    }

    @Override
    public Future<String> scriptLoad(final CharSequence script) {
        requireNonNull(script);
        final int len = 3;
        final int scriptBytes = numberOfBytesUtf8(script);
        final int capacity = 1 + calculateInitialCommandBufferSize(len, RedisProtocolSupport.Command.SCRIPT) +
                    calculateRequestArgumentSize(scriptBytes);
        Buffer buffer = reservedCnx.executionContext().bufferAllocator().newBuffer(capacity);
        writeRequestArraySize(buffer, len);
        RedisProtocolSupport.Command.SCRIPT.encodeTo(buffer);
        RedisProtocolSupport.SubCommand.LOAD.encodeTo(buffer);
        writeRequestArgument(buffer, script, scriptBytes);
        final RedisRequest request = newRequest(RedisProtocolSupport.Command.SCRIPT, buffer);
        final Single<String> queued = reservedCnx.request(request, String.class);
        Future<String> result = enqueueForExecute(state, singles, queued);
        return result;
    }

    @Override
    public <T> Future<List<T>> sdiff(@RedisProtocolSupport.Key final CharSequence firstkey) {
        requireNonNull(firstkey);
        final int len = 2;
        final int firstkeyBytes = numberOfBytesUtf8(firstkey);
        final int capacity = calculateInitialCommandBufferSize(len, RedisProtocolSupport.Command.SDIFF) +
                    calculateRequestArgumentSize(firstkeyBytes);
        Buffer buffer = reservedCnx.executionContext().bufferAllocator().newBuffer(capacity);
        writeRequestArraySize(buffer, len);
        RedisProtocolSupport.Command.SDIFF.encodeTo(buffer);
        writeRequestArgument(buffer, firstkey, firstkeyBytes);
        final RedisRequest request = newRequest(RedisProtocolSupport.Command.SDIFF, buffer);
        final Single<String> queued = reservedCnx.request(request, String.class);
        Future<List<T>> result = enqueueForExecute(state, singles, queued);
        return result;
    }

    @Override
    public <T> Future<List<T>> sdiff(@RedisProtocolSupport.Key final CharSequence firstkey,
                                     @Nullable @RedisProtocolSupport.Key final CharSequence otherkey) {
        requireNonNull(firstkey);
        final int len = 2 + (otherkey == null ? 0 : 1);
        final int firstkeyBytes = numberOfBytesUtf8(firstkey);
        final int otherkeyBytes = otherkey == null ? 0 : numberOfBytesUtf8(otherkey);
        final int capacity = calculateInitialCommandBufferSize(len, RedisProtocolSupport.Command.SDIFF) +
                    calculateRequestArgumentSize(firstkeyBytes) +
                    (otherkey == null ? 0 : calculateRequestArgumentSize(otherkeyBytes));
        Buffer buffer = reservedCnx.executionContext().bufferAllocator().newBuffer(capacity);
        writeRequestArraySize(buffer, len);
        RedisProtocolSupport.Command.SDIFF.encodeTo(buffer);
        writeRequestArgument(buffer, firstkey, firstkeyBytes);
        if (otherkey != null) {
            writeRequestArgument(buffer, otherkey, otherkeyBytes);
        }
        final RedisRequest request = newRequest(RedisProtocolSupport.Command.SDIFF, buffer);
        final Single<String> queued = reservedCnx.request(request, String.class);
        Future<List<T>> result = enqueueForExecute(state, singles, queued);
        return result;
    }

    @Override
    public <T> Future<List<T>> sdiff(@RedisProtocolSupport.Key final CharSequence firstkey,
                                     @Nullable @RedisProtocolSupport.Key final CharSequence otherkey1,
                                     @Nullable @RedisProtocolSupport.Key final CharSequence otherkey2) {
        requireNonNull(firstkey);
        final int len = 2 + (otherkey1 == null ? 0 : 1) + (otherkey2 == null ? 0 : 1);
        final int firstkeyBytes = numberOfBytesUtf8(firstkey);
        final int otherkey1Bytes = otherkey1 == null ? 0 : numberOfBytesUtf8(otherkey1);
        final int otherkey2Bytes = otherkey2 == null ? 0 : numberOfBytesUtf8(otherkey2);
        final int capacity = calculateInitialCommandBufferSize(len, RedisProtocolSupport.Command.SDIFF) +
                    calculateRequestArgumentSize(firstkeyBytes) +
                    (otherkey1 == null ? 0 : calculateRequestArgumentSize(otherkey1Bytes)) +
                    (otherkey2 == null ? 0 : calculateRequestArgumentSize(otherkey2Bytes));
        Buffer buffer = reservedCnx.executionContext().bufferAllocator().newBuffer(capacity);
        writeRequestArraySize(buffer, len);
        RedisProtocolSupport.Command.SDIFF.encodeTo(buffer);
        writeRequestArgument(buffer, firstkey, firstkeyBytes);
        if (otherkey1 != null) {
            writeRequestArgument(buffer, otherkey1, otherkey1Bytes);
        }
        if (otherkey2 != null) {
            writeRequestArgument(buffer, otherkey2, otherkey2Bytes);
        }
        final RedisRequest request = newRequest(RedisProtocolSupport.Command.SDIFF, buffer);
        final Single<String> queued = reservedCnx.request(request, String.class);
        Future<List<T>> result = enqueueForExecute(state, singles, queued);
        return result;
    }

    @Override
    public <T> Future<List<T>> sdiff(@RedisProtocolSupport.Key final CharSequence firstkey,
                                     @Nullable @RedisProtocolSupport.Key final CharSequence otherkey1,
                                     @Nullable @RedisProtocolSupport.Key final CharSequence otherkey2,
                                     @Nullable @RedisProtocolSupport.Key final CharSequence otherkey3) {
        requireNonNull(firstkey);
        final int len = 2 + (otherkey1 == null ? 0 : 1) + (otherkey2 == null ? 0 : 1) + (otherkey3 == null ? 0 : 1);
        final int firstkeyBytes = numberOfBytesUtf8(firstkey);
        final int otherkey1Bytes = otherkey1 == null ? 0 : numberOfBytesUtf8(otherkey1);
        final int otherkey2Bytes = otherkey2 == null ? 0 : numberOfBytesUtf8(otherkey2);
        final int otherkey3Bytes = otherkey3 == null ? 0 : numberOfBytesUtf8(otherkey3);
        final int capacity = calculateInitialCommandBufferSize(len, RedisProtocolSupport.Command.SDIFF) +
                    calculateRequestArgumentSize(firstkeyBytes) +
                    (otherkey1 == null ? 0 : calculateRequestArgumentSize(otherkey1Bytes)) +
                    (otherkey2 == null ? 0 : calculateRequestArgumentSize(otherkey2Bytes)) +
                    (otherkey3 == null ? 0 : calculateRequestArgumentSize(otherkey3Bytes));
        Buffer buffer = reservedCnx.executionContext().bufferAllocator().newBuffer(capacity);
        writeRequestArraySize(buffer, len);
        RedisProtocolSupport.Command.SDIFF.encodeTo(buffer);
        writeRequestArgument(buffer, firstkey, firstkeyBytes);
        if (otherkey1 != null) {
            writeRequestArgument(buffer, otherkey1, otherkey1Bytes);
        }
        if (otherkey2 != null) {
            writeRequestArgument(buffer, otherkey2, otherkey2Bytes);
        }
        if (otherkey3 != null) {
            writeRequestArgument(buffer, otherkey3, otherkey3Bytes);
        }
        final RedisRequest request = newRequest(RedisProtocolSupport.Command.SDIFF, buffer);
        final Single<String> queued = reservedCnx.request(request, String.class);
        Future<List<T>> result = enqueueForExecute(state, singles, queued);
        return result;
    }

    @Override
    public <T> Future<List<T>> sdiff(@RedisProtocolSupport.Key final CharSequence firstkey,
                                     @RedisProtocolSupport.Key final Collection<? extends CharSequence> otherkeys) {
        requireNonNull(firstkey);
        requireNonNull(otherkeys);
        final int len = 2 + otherkeys.size();
        final int firstkeyBytes = numberOfBytesUtf8(firstkey);
        int otherkeysCapacity = 0;
        if (otherkeys instanceof List && otherkeys instanceof RandomAccess) {
            final List<CharSequence> list = (List<CharSequence>) otherkeys;
            for (int i = 0; i < list.size(); ++i) {
                final CharSequence arg = list.get(i);
                otherkeysCapacity += estimateRequestArgumentSize(arg);
            }
        } else {
            for (CharSequence arg : otherkeys) {
                otherkeysCapacity += estimateRequestArgumentSize(arg);
            }
        }
        final int capacity = calculateInitialCommandBufferSize(len, RedisProtocolSupport.Command.SDIFF) +
                    calculateRequestArgumentSize(firstkeyBytes) + otherkeysCapacity;
        Buffer buffer = reservedCnx.executionContext().bufferAllocator().newBuffer(capacity);
        writeRequestArraySize(buffer, len);
        RedisProtocolSupport.Command.SDIFF.encodeTo(buffer);
        writeRequestArgument(buffer, firstkey, firstkeyBytes);
        if (otherkeys instanceof List && otherkeys instanceof RandomAccess) {
            final List<CharSequence> list = (List<CharSequence>) otherkeys;
            for (int i = 0; i < list.size(); ++i) {
                final CharSequence arg = list.get(i);
                writeRequestArgument(buffer, arg);
            }
        } else {
            for (CharSequence arg : otherkeys) {
                writeRequestArgument(buffer, arg);
            }
        }
        final RedisRequest request = newRequest(RedisProtocolSupport.Command.SDIFF, buffer);
        final Single<String> queued = reservedCnx.request(request, String.class);
        Future<List<T>> result = enqueueForExecute(state, singles, queued);
        return result;
    }

    @Override
    public Future<Long> sdiffstore(@RedisProtocolSupport.Key final CharSequence destination,
                                   @RedisProtocolSupport.Key final CharSequence firstkey) {
        requireNonNull(destination);
        requireNonNull(firstkey);
        final int len = 3;
        final int destinationBytes = numberOfBytesUtf8(destination);
        final int firstkeyBytes = numberOfBytesUtf8(firstkey);
        final int capacity = calculateInitialCommandBufferSize(len, RedisProtocolSupport.Command.SDIFFSTORE) +
                    calculateRequestArgumentSize(destinationBytes) + calculateRequestArgumentSize(firstkeyBytes);
        Buffer buffer = reservedCnx.executionContext().bufferAllocator().newBuffer(capacity);
        writeRequestArraySize(buffer, len);
        RedisProtocolSupport.Command.SDIFFSTORE.encodeTo(buffer);
        writeRequestArgument(buffer, destination, destinationBytes);
        writeRequestArgument(buffer, firstkey, firstkeyBytes);
        final RedisRequest request = newRequest(RedisProtocolSupport.Command.SDIFFSTORE, buffer);
        final Single<String> queued = reservedCnx.request(request, String.class);
        Future<Long> result = enqueueForExecute(state, singles, queued);
        return result;
    }

    @Override
    public Future<Long> sdiffstore(@RedisProtocolSupport.Key final CharSequence destination,
                                   @RedisProtocolSupport.Key final CharSequence firstkey,
                                   @Nullable @RedisProtocolSupport.Key final CharSequence otherkey) {
        requireNonNull(destination);
        requireNonNull(firstkey);
        final int len = 3 + (otherkey == null ? 0 : 1);
        final int destinationBytes = numberOfBytesUtf8(destination);
        final int firstkeyBytes = numberOfBytesUtf8(firstkey);
        final int otherkeyBytes = otherkey == null ? 0 : numberOfBytesUtf8(otherkey);
        final int capacity = calculateInitialCommandBufferSize(len, RedisProtocolSupport.Command.SDIFFSTORE) +
                    calculateRequestArgumentSize(destinationBytes) + calculateRequestArgumentSize(firstkeyBytes) +
                    (otherkey == null ? 0 : calculateRequestArgumentSize(otherkeyBytes));
        Buffer buffer = reservedCnx.executionContext().bufferAllocator().newBuffer(capacity);
        writeRequestArraySize(buffer, len);
        RedisProtocolSupport.Command.SDIFFSTORE.encodeTo(buffer);
        writeRequestArgument(buffer, destination, destinationBytes);
        writeRequestArgument(buffer, firstkey, firstkeyBytes);
        if (otherkey != null) {
            writeRequestArgument(buffer, otherkey, otherkeyBytes);
        }
        final RedisRequest request = newRequest(RedisProtocolSupport.Command.SDIFFSTORE, buffer);
        final Single<String> queued = reservedCnx.request(request, String.class);
        Future<Long> result = enqueueForExecute(state, singles, queued);
        return result;
    }

    @Override
    public Future<Long> sdiffstore(@RedisProtocolSupport.Key final CharSequence destination,
                                   @RedisProtocolSupport.Key final CharSequence firstkey,
                                   @Nullable @RedisProtocolSupport.Key final CharSequence otherkey1,
                                   @Nullable @RedisProtocolSupport.Key final CharSequence otherkey2) {
        requireNonNull(destination);
        requireNonNull(firstkey);
        final int len = 3 + (otherkey1 == null ? 0 : 1) + (otherkey2 == null ? 0 : 1);
        final int destinationBytes = numberOfBytesUtf8(destination);
        final int firstkeyBytes = numberOfBytesUtf8(firstkey);
        final int otherkey1Bytes = otherkey1 == null ? 0 : numberOfBytesUtf8(otherkey1);
        final int otherkey2Bytes = otherkey2 == null ? 0 : numberOfBytesUtf8(otherkey2);
        final int capacity = calculateInitialCommandBufferSize(len, RedisProtocolSupport.Command.SDIFFSTORE) +
                    calculateRequestArgumentSize(destinationBytes) + calculateRequestArgumentSize(firstkeyBytes) +
                    (otherkey1 == null ? 0 : calculateRequestArgumentSize(otherkey1Bytes)) +
                    (otherkey2 == null ? 0 : calculateRequestArgumentSize(otherkey2Bytes));
        Buffer buffer = reservedCnx.executionContext().bufferAllocator().newBuffer(capacity);
        writeRequestArraySize(buffer, len);
        RedisProtocolSupport.Command.SDIFFSTORE.encodeTo(buffer);
        writeRequestArgument(buffer, destination, destinationBytes);
        writeRequestArgument(buffer, firstkey, firstkeyBytes);
        if (otherkey1 != null) {
            writeRequestArgument(buffer, otherkey1, otherkey1Bytes);
        }
        if (otherkey2 != null) {
            writeRequestArgument(buffer, otherkey2, otherkey2Bytes);
        }
        final RedisRequest request = newRequest(RedisProtocolSupport.Command.SDIFFSTORE, buffer);
        final Single<String> queued = reservedCnx.request(request, String.class);
        Future<Long> result = enqueueForExecute(state, singles, queued);
        return result;
    }

    @Override
    public Future<Long> sdiffstore(@RedisProtocolSupport.Key final CharSequence destination,
                                   @RedisProtocolSupport.Key final CharSequence firstkey,
                                   @Nullable @RedisProtocolSupport.Key final CharSequence otherkey1,
                                   @Nullable @RedisProtocolSupport.Key final CharSequence otherkey2,
                                   @Nullable @RedisProtocolSupport.Key final CharSequence otherkey3) {
        requireNonNull(destination);
        requireNonNull(firstkey);
        final int len = 3 + (otherkey1 == null ? 0 : 1) + (otherkey2 == null ? 0 : 1) + (otherkey3 == null ? 0 : 1);
        final int destinationBytes = numberOfBytesUtf8(destination);
        final int firstkeyBytes = numberOfBytesUtf8(firstkey);
        final int otherkey1Bytes = otherkey1 == null ? 0 : numberOfBytesUtf8(otherkey1);
        final int otherkey2Bytes = otherkey2 == null ? 0 : numberOfBytesUtf8(otherkey2);
        final int otherkey3Bytes = otherkey3 == null ? 0 : numberOfBytesUtf8(otherkey3);
        final int capacity = calculateInitialCommandBufferSize(len, RedisProtocolSupport.Command.SDIFFSTORE) +
                    calculateRequestArgumentSize(destinationBytes) + calculateRequestArgumentSize(firstkeyBytes) +
                    (otherkey1 == null ? 0 : calculateRequestArgumentSize(otherkey1Bytes)) +
                    (otherkey2 == null ? 0 : calculateRequestArgumentSize(otherkey2Bytes)) +
                    (otherkey3 == null ? 0 : calculateRequestArgumentSize(otherkey3Bytes));
        Buffer buffer = reservedCnx.executionContext().bufferAllocator().newBuffer(capacity);
        writeRequestArraySize(buffer, len);
        RedisProtocolSupport.Command.SDIFFSTORE.encodeTo(buffer);
        writeRequestArgument(buffer, destination, destinationBytes);
        writeRequestArgument(buffer, firstkey, firstkeyBytes);
        if (otherkey1 != null) {
            writeRequestArgument(buffer, otherkey1, otherkey1Bytes);
        }
        if (otherkey2 != null) {
            writeRequestArgument(buffer, otherkey2, otherkey2Bytes);
        }
        if (otherkey3 != null) {
            writeRequestArgument(buffer, otherkey3, otherkey3Bytes);
        }
        final RedisRequest request = newRequest(RedisProtocolSupport.Command.SDIFFSTORE, buffer);
        final Single<String> queued = reservedCnx.request(request, String.class);
        Future<Long> result = enqueueForExecute(state, singles, queued);
        return result;
    }

    @Override
    public Future<Long> sdiffstore(@RedisProtocolSupport.Key final CharSequence destination,
                                   @RedisProtocolSupport.Key final CharSequence firstkey,
                                   @RedisProtocolSupport.Key final Collection<? extends CharSequence> otherkeys) {
        requireNonNull(destination);
        requireNonNull(firstkey);
        requireNonNull(otherkeys);
        final int len = 3 + otherkeys.size();
        final int destinationBytes = numberOfBytesUtf8(destination);
        final int firstkeyBytes = numberOfBytesUtf8(firstkey);
        int otherkeysCapacity = 0;
        if (otherkeys instanceof List && otherkeys instanceof RandomAccess) {
            final List<CharSequence> list = (List<CharSequence>) otherkeys;
            for (int i = 0; i < list.size(); ++i) {
                final CharSequence arg = list.get(i);
                otherkeysCapacity += estimateRequestArgumentSize(arg);
            }
        } else {
            for (CharSequence arg : otherkeys) {
                otherkeysCapacity += estimateRequestArgumentSize(arg);
            }
        }
        final int capacity = calculateInitialCommandBufferSize(len, RedisProtocolSupport.Command.SDIFFSTORE) +
                    calculateRequestArgumentSize(destinationBytes) + calculateRequestArgumentSize(firstkeyBytes) +
                    otherkeysCapacity;
        Buffer buffer = reservedCnx.executionContext().bufferAllocator().newBuffer(capacity);
        writeRequestArraySize(buffer, len);
        RedisProtocolSupport.Command.SDIFFSTORE.encodeTo(buffer);
        writeRequestArgument(buffer, destination, destinationBytes);
        writeRequestArgument(buffer, firstkey, firstkeyBytes);
        if (otherkeys instanceof List && otherkeys instanceof RandomAccess) {
            final List<CharSequence> list = (List<CharSequence>) otherkeys;
            for (int i = 0; i < list.size(); ++i) {
                final CharSequence arg = list.get(i);
                writeRequestArgument(buffer, arg);
            }
        } else {
            for (CharSequence arg : otherkeys) {
                writeRequestArgument(buffer, arg);
            }
        }
        final RedisRequest request = newRequest(RedisProtocolSupport.Command.SDIFFSTORE, buffer);
        final Single<String> queued = reservedCnx.request(request, String.class);
        Future<Long> result = enqueueForExecute(state, singles, queued);
        return result;
    }

    @Override
    public Future<String> select(final long index) {
        final int len = 2;
        final int capacity = calculateInitialCommandBufferSize(len, RedisProtocolSupport.Command.SELECT) +
                    calculateRequestArgumentSize(index);
        Buffer buffer = reservedCnx.executionContext().bufferAllocator().newBuffer(capacity);
        writeRequestArraySize(buffer, len);
        RedisProtocolSupport.Command.SELECT.encodeTo(buffer);
        writeRequestArgument(buffer, index);
        final RedisRequest request = newRequest(RedisProtocolSupport.Command.SELECT, buffer);
        final Single<String> queued = reservedCnx.request(request, String.class);
        Future<String> result = enqueueForExecute(state, singles, queued);
        return result;
    }

    @Override
    public Future<String> set(@RedisProtocolSupport.Key final CharSequence key, final CharSequence value) {
        requireNonNull(key);
        requireNonNull(value);
        final int len = 3;
        final int keyBytes = numberOfBytesUtf8(key);
        final int valueBytes = numberOfBytesUtf8(value);
        final int capacity = calculateInitialCommandBufferSize(len, RedisProtocolSupport.Command.SET) +
                    calculateRequestArgumentSize(keyBytes) + calculateRequestArgumentSize(valueBytes);
        Buffer buffer = reservedCnx.executionContext().bufferAllocator().newBuffer(capacity);
        writeRequestArraySize(buffer, len);
        RedisProtocolSupport.Command.SET.encodeTo(buffer);
        writeRequestArgument(buffer, key, keyBytes);
        writeRequestArgument(buffer, value, valueBytes);
        final RedisRequest request = newRequest(RedisProtocolSupport.Command.SET, buffer);
        final Single<String> queued = reservedCnx.request(request, String.class);
        Future<String> result = enqueueForExecute(state, singles, queued);
        return result;
    }

    @Override
    public Future<String> set(@RedisProtocolSupport.Key final CharSequence key, final CharSequence value,
                              @Nullable final RedisProtocolSupport.ExpireDuration expireDuration,
                              @Nullable final RedisProtocolSupport.SetCondition condition) {
        requireNonNull(key);
        requireNonNull(value);
        final int len = 3 + (expireDuration == null ? 0 : RedisProtocolSupport.ExpireDuration.SIZE) +
                    (condition == null ? 0 : 1);
        final int keyBytes = numberOfBytesUtf8(key);
        final int valueBytes = numberOfBytesUtf8(value);
        final int capacity = calculateInitialCommandBufferSize(len, RedisProtocolSupport.Command.SET) +
                    calculateRequestArgumentSize(keyBytes) + calculateRequestArgumentSize(valueBytes) +
                    (expireDuration == null ? 0 : expireDuration.encodedByteCount()) +
                    (condition == null ? 0 : condition.encodedByteCount());
        Buffer buffer = reservedCnx.executionContext().bufferAllocator().newBuffer(capacity);
        writeRequestArraySize(buffer, len);
        RedisProtocolSupport.Command.SET.encodeTo(buffer);
        writeRequestArgument(buffer, key, keyBytes);
        writeRequestArgument(buffer, value, valueBytes);
        if (expireDuration != null) {
            expireDuration.encodeTo(buffer);
        }
        if (condition != null) {
            condition.encodeTo(buffer);
        }
        final RedisRequest request = newRequest(RedisProtocolSupport.Command.SET, buffer);
        final Single<String> queued = reservedCnx.request(request, String.class);
        Future<String> result = enqueueForExecute(state, singles, queued);
        return result;
    }

    @Override
    public Future<Long> setbit(@RedisProtocolSupport.Key final CharSequence key, final long offset,
                               final CharSequence value) {
        requireNonNull(key);
        requireNonNull(value);
        final int len = 4;
        final int keyBytes = numberOfBytesUtf8(key);
        final int valueBytes = numberOfBytesUtf8(value);
        final int capacity = calculateInitialCommandBufferSize(len, RedisProtocolSupport.Command.SETBIT) +
                    calculateRequestArgumentSize(keyBytes) + calculateRequestArgumentSize(offset) +
                    calculateRequestArgumentSize(valueBytes);
        Buffer buffer = reservedCnx.executionContext().bufferAllocator().newBuffer(capacity);
        writeRequestArraySize(buffer, len);
        RedisProtocolSupport.Command.SETBIT.encodeTo(buffer);
        writeRequestArgument(buffer, key, keyBytes);
        writeRequestArgument(buffer, offset);
        writeRequestArgument(buffer, value, valueBytes);
        final RedisRequest request = newRequest(RedisProtocolSupport.Command.SETBIT, buffer);
        final Single<String> queued = reservedCnx.request(request, String.class);
        Future<Long> result = enqueueForExecute(state, singles, queued);
        return result;
    }

    @Override
    public Future<String> setex(@RedisProtocolSupport.Key final CharSequence key, final long seconds,
                                final CharSequence value) {
        requireNonNull(key);
        requireNonNull(value);
        final int len = 4;
        final int keyBytes = numberOfBytesUtf8(key);
        final int valueBytes = numberOfBytesUtf8(value);
        final int capacity = calculateInitialCommandBufferSize(len, RedisProtocolSupport.Command.SETEX) +
                    calculateRequestArgumentSize(keyBytes) + calculateRequestArgumentSize(seconds) +
                    calculateRequestArgumentSize(valueBytes);
        Buffer buffer = reservedCnx.executionContext().bufferAllocator().newBuffer(capacity);
        writeRequestArraySize(buffer, len);
        RedisProtocolSupport.Command.SETEX.encodeTo(buffer);
        writeRequestArgument(buffer, key, keyBytes);
        writeRequestArgument(buffer, seconds);
        writeRequestArgument(buffer, value, valueBytes);
        final RedisRequest request = newRequest(RedisProtocolSupport.Command.SETEX, buffer);
        final Single<String> queued = reservedCnx.request(request, String.class);
        Future<String> result = enqueueForExecute(state, singles, queued);
        return result;
    }

    @Override
    public Future<Long> setnx(@RedisProtocolSupport.Key final CharSequence key, final CharSequence value) {
        requireNonNull(key);
        requireNonNull(value);
        final int len = 3;
        final int keyBytes = numberOfBytesUtf8(key);
        final int valueBytes = numberOfBytesUtf8(value);
        final int capacity = calculateInitialCommandBufferSize(len, RedisProtocolSupport.Command.SETNX) +
                    calculateRequestArgumentSize(keyBytes) + calculateRequestArgumentSize(valueBytes);
        Buffer buffer = reservedCnx.executionContext().bufferAllocator().newBuffer(capacity);
        writeRequestArraySize(buffer, len);
        RedisProtocolSupport.Command.SETNX.encodeTo(buffer);
        writeRequestArgument(buffer, key, keyBytes);
        writeRequestArgument(buffer, value, valueBytes);
        final RedisRequest request = newRequest(RedisProtocolSupport.Command.SETNX, buffer);
        final Single<String> queued = reservedCnx.request(request, String.class);
        Future<Long> result = enqueueForExecute(state, singles, queued);
        return result;
    }

    @Override
    public Future<Long> setrange(@RedisProtocolSupport.Key final CharSequence key, final long offset,
                                 final CharSequence value) {
        requireNonNull(key);
        requireNonNull(value);
        final int len = 4;
        final int keyBytes = numberOfBytesUtf8(key);
        final int valueBytes = numberOfBytesUtf8(value);
        final int capacity = calculateInitialCommandBufferSize(len, RedisProtocolSupport.Command.SETRANGE) +
                    calculateRequestArgumentSize(keyBytes) + calculateRequestArgumentSize(offset) +
                    calculateRequestArgumentSize(valueBytes);
        Buffer buffer = reservedCnx.executionContext().bufferAllocator().newBuffer(capacity);
        writeRequestArraySize(buffer, len);
        RedisProtocolSupport.Command.SETRANGE.encodeTo(buffer);
        writeRequestArgument(buffer, key, keyBytes);
        writeRequestArgument(buffer, offset);
        writeRequestArgument(buffer, value, valueBytes);
        final RedisRequest request = newRequest(RedisProtocolSupport.Command.SETRANGE, buffer);
        final Single<String> queued = reservedCnx.request(request, String.class);
        Future<Long> result = enqueueForExecute(state, singles, queued);
        return result;
    }

    @Override
    public Future<String> shutdown() {
        final int len = 1;
        final int capacity = calculateInitialCommandBufferSize(len, RedisProtocolSupport.Command.SHUTDOWN);
        Buffer buffer = reservedCnx.executionContext().bufferAllocator().newBuffer(capacity);
        writeRequestArraySize(buffer, len);
        RedisProtocolSupport.Command.SHUTDOWN.encodeTo(buffer);
        final RedisRequest request = newRequest(RedisProtocolSupport.Command.SHUTDOWN, buffer);
        final Single<String> queued = reservedCnx.request(request, String.class);
        Future<String> result = enqueueForExecute(state, singles, queued);
        return result;
    }

    @Override
    public Future<String> shutdown(@Nullable final RedisProtocolSupport.ShutdownSaveMode saveMode) {
        final int len = 1 + (saveMode == null ? 0 : 1);
        final int capacity = calculateInitialCommandBufferSize(len, RedisProtocolSupport.Command.SHUTDOWN) +
                    (saveMode == null ? 0 : saveMode.encodedByteCount());
        Buffer buffer = reservedCnx.executionContext().bufferAllocator().newBuffer(capacity);
        writeRequestArraySize(buffer, len);
        RedisProtocolSupport.Command.SHUTDOWN.encodeTo(buffer);
        if (saveMode != null) {
            saveMode.encodeTo(buffer);
        }
        final RedisRequest request = newRequest(RedisProtocolSupport.Command.SHUTDOWN, buffer);
        final Single<String> queued = reservedCnx.request(request, String.class);
        Future<String> result = enqueueForExecute(state, singles, queued);
        return result;
    }

    @Override
    public <T> Future<List<T>> sinter(@RedisProtocolSupport.Key final CharSequence key) {
        requireNonNull(key);
        final int len = 2;
        final int keyBytes = numberOfBytesUtf8(key);
        final int capacity = calculateInitialCommandBufferSize(len, RedisProtocolSupport.Command.SINTER) +
                    calculateRequestArgumentSize(keyBytes);
        Buffer buffer = reservedCnx.executionContext().bufferAllocator().newBuffer(capacity);
        writeRequestArraySize(buffer, len);
        RedisProtocolSupport.Command.SINTER.encodeTo(buffer);
        writeRequestArgument(buffer, key, keyBytes);
        final RedisRequest request = newRequest(RedisProtocolSupport.Command.SINTER, buffer);
        final Single<String> queued = reservedCnx.request(request, String.class);
        Future<List<T>> result = enqueueForExecute(state, singles, queued);
        return result;
    }

    @Override
    public <T> Future<List<T>> sinter(@RedisProtocolSupport.Key final CharSequence key1,
                                      @RedisProtocolSupport.Key final CharSequence key2) {
        requireNonNull(key1);
        requireNonNull(key2);
        final int len = 3;
        final int key1Bytes = numberOfBytesUtf8(key1);
        final int key2Bytes = numberOfBytesUtf8(key2);
        final int capacity = calculateInitialCommandBufferSize(len, RedisProtocolSupport.Command.SINTER) +
                    calculateRequestArgumentSize(key1Bytes) + calculateRequestArgumentSize(key2Bytes);
        Buffer buffer = reservedCnx.executionContext().bufferAllocator().newBuffer(capacity);
        writeRequestArraySize(buffer, len);
        RedisProtocolSupport.Command.SINTER.encodeTo(buffer);
        writeRequestArgument(buffer, key1, key1Bytes);
        writeRequestArgument(buffer, key2, key2Bytes);
        final RedisRequest request = newRequest(RedisProtocolSupport.Command.SINTER, buffer);
        final Single<String> queued = reservedCnx.request(request, String.class);
        Future<List<T>> result = enqueueForExecute(state, singles, queued);
        return result;
    }

    @Override
    public <T> Future<List<T>> sinter(@RedisProtocolSupport.Key final CharSequence key1,
                                      @RedisProtocolSupport.Key final CharSequence key2,
                                      @RedisProtocolSupport.Key final CharSequence key3) {
        requireNonNull(key1);
        requireNonNull(key2);
        requireNonNull(key3);
        final int len = 4;
        final int key1Bytes = numberOfBytesUtf8(key1);
        final int key2Bytes = numberOfBytesUtf8(key2);
        final int key3Bytes = numberOfBytesUtf8(key3);
        final int capacity = calculateInitialCommandBufferSize(len, RedisProtocolSupport.Command.SINTER) +
                    calculateRequestArgumentSize(key1Bytes) + calculateRequestArgumentSize(key2Bytes) +
                    calculateRequestArgumentSize(key3Bytes);
        Buffer buffer = reservedCnx.executionContext().bufferAllocator().newBuffer(capacity);
        writeRequestArraySize(buffer, len);
        RedisProtocolSupport.Command.SINTER.encodeTo(buffer);
        writeRequestArgument(buffer, key1, key1Bytes);
        writeRequestArgument(buffer, key2, key2Bytes);
        writeRequestArgument(buffer, key3, key3Bytes);
        final RedisRequest request = newRequest(RedisProtocolSupport.Command.SINTER, buffer);
        final Single<String> queued = reservedCnx.request(request, String.class);
        Future<List<T>> result = enqueueForExecute(state, singles, queued);
        return result;
    }

    @Override
    public <T> Future<List<T>> sinter(@RedisProtocolSupport.Key final Collection<? extends CharSequence> keys) {
        requireNonNull(keys);
        final int len = 1 + keys.size();
        int keysCapacity = 0;
        if (keys instanceof List && keys instanceof RandomAccess) {
            final List<CharSequence> list = (List<CharSequence>) keys;
            for (int i = 0; i < list.size(); ++i) {
                final CharSequence arg = list.get(i);
                keysCapacity += estimateRequestArgumentSize(arg);
            }
        } else {
            for (CharSequence arg : keys) {
                keysCapacity += estimateRequestArgumentSize(arg);
            }
        }
        final int capacity = calculateInitialCommandBufferSize(len, RedisProtocolSupport.Command.SINTER) + keysCapacity;
        Buffer buffer = reservedCnx.executionContext().bufferAllocator().newBuffer(capacity);
        writeRequestArraySize(buffer, len);
        RedisProtocolSupport.Command.SINTER.encodeTo(buffer);
        if (keys instanceof List && keys instanceof RandomAccess) {
            final List<CharSequence> list = (List<CharSequence>) keys;
            for (int i = 0; i < list.size(); ++i) {
                final CharSequence arg = list.get(i);
                writeRequestArgument(buffer, arg);
            }
        } else {
            for (CharSequence arg : keys) {
                writeRequestArgument(buffer, arg);
            }
        }
        final RedisRequest request = newRequest(RedisProtocolSupport.Command.SINTER, buffer);
        final Single<String> queued = reservedCnx.request(request, String.class);
        Future<List<T>> result = enqueueForExecute(state, singles, queued);
        return result;
    }

    @Override
    public Future<Long> sinterstore(@RedisProtocolSupport.Key final CharSequence destination,
                                    @RedisProtocolSupport.Key final CharSequence key) {
        requireNonNull(destination);
        requireNonNull(key);
        final int len = 3;
        final int destinationBytes = numberOfBytesUtf8(destination);
        final int keyBytes = numberOfBytesUtf8(key);
        final int capacity = calculateInitialCommandBufferSize(len, RedisProtocolSupport.Command.SINTERSTORE) +
                    calculateRequestArgumentSize(destinationBytes) + calculateRequestArgumentSize(keyBytes);
        Buffer buffer = reservedCnx.executionContext().bufferAllocator().newBuffer(capacity);
        writeRequestArraySize(buffer, len);
        RedisProtocolSupport.Command.SINTERSTORE.encodeTo(buffer);
        writeRequestArgument(buffer, destination, destinationBytes);
        writeRequestArgument(buffer, key, keyBytes);
        final RedisRequest request = newRequest(RedisProtocolSupport.Command.SINTERSTORE, buffer);
        final Single<String> queued = reservedCnx.request(request, String.class);
        Future<Long> result = enqueueForExecute(state, singles, queued);
        return result;
    }

    @Override
    public Future<Long> sinterstore(@RedisProtocolSupport.Key final CharSequence destination,
                                    @RedisProtocolSupport.Key final CharSequence key1,
                                    @RedisProtocolSupport.Key final CharSequence key2) {
        requireNonNull(destination);
        requireNonNull(key1);
        requireNonNull(key2);
        final int len = 4;
        final int destinationBytes = numberOfBytesUtf8(destination);
        final int key1Bytes = numberOfBytesUtf8(key1);
        final int key2Bytes = numberOfBytesUtf8(key2);
        final int capacity = calculateInitialCommandBufferSize(len, RedisProtocolSupport.Command.SINTERSTORE) +
                    calculateRequestArgumentSize(destinationBytes) + calculateRequestArgumentSize(key1Bytes) +
                    calculateRequestArgumentSize(key2Bytes);
        Buffer buffer = reservedCnx.executionContext().bufferAllocator().newBuffer(capacity);
        writeRequestArraySize(buffer, len);
        RedisProtocolSupport.Command.SINTERSTORE.encodeTo(buffer);
        writeRequestArgument(buffer, destination, destinationBytes);
        writeRequestArgument(buffer, key1, key1Bytes);
        writeRequestArgument(buffer, key2, key2Bytes);
        final RedisRequest request = newRequest(RedisProtocolSupport.Command.SINTERSTORE, buffer);
        final Single<String> queued = reservedCnx.request(request, String.class);
        Future<Long> result = enqueueForExecute(state, singles, queued);
        return result;
    }

    @Override
    public Future<Long> sinterstore(@RedisProtocolSupport.Key final CharSequence destination,
                                    @RedisProtocolSupport.Key final CharSequence key1,
                                    @RedisProtocolSupport.Key final CharSequence key2,
                                    @RedisProtocolSupport.Key final CharSequence key3) {
        requireNonNull(destination);
        requireNonNull(key1);
        requireNonNull(key2);
        requireNonNull(key3);
        final int len = 5;
        final int destinationBytes = numberOfBytesUtf8(destination);
        final int key1Bytes = numberOfBytesUtf8(key1);
        final int key2Bytes = numberOfBytesUtf8(key2);
        final int key3Bytes = numberOfBytesUtf8(key3);
        final int capacity = calculateInitialCommandBufferSize(len, RedisProtocolSupport.Command.SINTERSTORE) +
                    calculateRequestArgumentSize(destinationBytes) + calculateRequestArgumentSize(key1Bytes) +
                    calculateRequestArgumentSize(key2Bytes) + calculateRequestArgumentSize(key3Bytes);
        Buffer buffer = reservedCnx.executionContext().bufferAllocator().newBuffer(capacity);
        writeRequestArraySize(buffer, len);
        RedisProtocolSupport.Command.SINTERSTORE.encodeTo(buffer);
        writeRequestArgument(buffer, destination, destinationBytes);
        writeRequestArgument(buffer, key1, key1Bytes);
        writeRequestArgument(buffer, key2, key2Bytes);
        writeRequestArgument(buffer, key3, key3Bytes);
        final RedisRequest request = newRequest(RedisProtocolSupport.Command.SINTERSTORE, buffer);
        final Single<String> queued = reservedCnx.request(request, String.class);
        Future<Long> result = enqueueForExecute(state, singles, queued);
        return result;
    }

    @Override
    public Future<Long> sinterstore(@RedisProtocolSupport.Key final CharSequence destination,
                                    @RedisProtocolSupport.Key final Collection<? extends CharSequence> keys) {
        requireNonNull(destination);
        requireNonNull(keys);
        final int len = 2 + keys.size();
        final int destinationBytes = numberOfBytesUtf8(destination);
        int keysCapacity = 0;
        if (keys instanceof List && keys instanceof RandomAccess) {
            final List<CharSequence> list = (List<CharSequence>) keys;
            for (int i = 0; i < list.size(); ++i) {
                final CharSequence arg = list.get(i);
                keysCapacity += estimateRequestArgumentSize(arg);
            }
        } else {
            for (CharSequence arg : keys) {
                keysCapacity += estimateRequestArgumentSize(arg);
            }
        }
        final int capacity = calculateInitialCommandBufferSize(len, RedisProtocolSupport.Command.SINTERSTORE) +
                    calculateRequestArgumentSize(destinationBytes) + keysCapacity;
        Buffer buffer = reservedCnx.executionContext().bufferAllocator().newBuffer(capacity);
        writeRequestArraySize(buffer, len);
        RedisProtocolSupport.Command.SINTERSTORE.encodeTo(buffer);
        writeRequestArgument(buffer, destination, destinationBytes);
        if (keys instanceof List && keys instanceof RandomAccess) {
            final List<CharSequence> list = (List<CharSequence>) keys;
            for (int i = 0; i < list.size(); ++i) {
                final CharSequence arg = list.get(i);
                writeRequestArgument(buffer, arg);
            }
        } else {
            for (CharSequence arg : keys) {
                writeRequestArgument(buffer, arg);
            }
        }
        final RedisRequest request = newRequest(RedisProtocolSupport.Command.SINTERSTORE, buffer);
        final Single<String> queued = reservedCnx.request(request, String.class);
        Future<Long> result = enqueueForExecute(state, singles, queued);
        return result;
    }

    @Override
    public Future<Long> sismember(@RedisProtocolSupport.Key final CharSequence key, final CharSequence member) {
        requireNonNull(key);
        requireNonNull(member);
        final int len = 3;
        final int keyBytes = numberOfBytesUtf8(key);
        final int memberBytes = numberOfBytesUtf8(member);
        final int capacity = calculateInitialCommandBufferSize(len, RedisProtocolSupport.Command.SISMEMBER) +
                    calculateRequestArgumentSize(keyBytes) + calculateRequestArgumentSize(memberBytes);
        Buffer buffer = reservedCnx.executionContext().bufferAllocator().newBuffer(capacity);
        writeRequestArraySize(buffer, len);
        RedisProtocolSupport.Command.SISMEMBER.encodeTo(buffer);
        writeRequestArgument(buffer, key, keyBytes);
        writeRequestArgument(buffer, member, memberBytes);
        final RedisRequest request = newRequest(RedisProtocolSupport.Command.SISMEMBER, buffer);
        final Single<String> queued = reservedCnx.request(request, String.class);
        Future<Long> result = enqueueForExecute(state, singles, queued);
        return result;
    }

    @Override
    public Future<String> slaveof(final CharSequence host, final CharSequence port) {
        requireNonNull(host);
        requireNonNull(port);
        final int len = 3;
        final int hostBytes = numberOfBytesUtf8(host);
        final int portBytes = numberOfBytesUtf8(port);
        final int capacity = calculateInitialCommandBufferSize(len, RedisProtocolSupport.Command.SLAVEOF) +
                    calculateRequestArgumentSize(hostBytes) + calculateRequestArgumentSize(portBytes);
        Buffer buffer = reservedCnx.executionContext().bufferAllocator().newBuffer(capacity);
        writeRequestArraySize(buffer, len);
        RedisProtocolSupport.Command.SLAVEOF.encodeTo(buffer);
        writeRequestArgument(buffer, host, hostBytes);
        writeRequestArgument(buffer, port, portBytes);
        final RedisRequest request = newRequest(RedisProtocolSupport.Command.SLAVEOF, buffer);
        final Single<String> queued = reservedCnx.request(request, String.class);
        Future<String> result = enqueueForExecute(state, singles, queued);
        return result;
    }

    @Override
    public <T> Future<List<T>> slowlog(final CharSequence subcommand) {
        requireNonNull(subcommand);
        final int len = 2;
        final int subcommandBytes = numberOfBytesUtf8(subcommand);
        final int capacity = calculateInitialCommandBufferSize(len, RedisProtocolSupport.Command.SLOWLOG) +
                    calculateRequestArgumentSize(subcommandBytes);
        Buffer buffer = reservedCnx.executionContext().bufferAllocator().newBuffer(capacity);
        writeRequestArraySize(buffer, len);
        RedisProtocolSupport.Command.SLOWLOG.encodeTo(buffer);
        writeRequestArgument(buffer, subcommand, subcommandBytes);
        final RedisRequest request = newRequest(RedisProtocolSupport.Command.SLOWLOG, buffer);
        final Single<String> queued = reservedCnx.request(request, String.class);
        Future<List<T>> result = enqueueForExecute(state, singles, queued);
        return result;
    }

    @Override
    public <T> Future<List<T>> slowlog(final CharSequence subcommand, @Nullable final CharSequence argument) {
        requireNonNull(subcommand);
        final int len = 2 + (argument == null ? 0 : 1);
        final int subcommandBytes = numberOfBytesUtf8(subcommand);
        final int argumentBytes = argument == null ? 0 : numberOfBytesUtf8(argument);
        final int capacity = calculateInitialCommandBufferSize(len, RedisProtocolSupport.Command.SLOWLOG) +
                    calculateRequestArgumentSize(subcommandBytes) +
                    (argument == null ? 0 : calculateRequestArgumentSize(argumentBytes));
        Buffer buffer = reservedCnx.executionContext().bufferAllocator().newBuffer(capacity);
        writeRequestArraySize(buffer, len);
        RedisProtocolSupport.Command.SLOWLOG.encodeTo(buffer);
        writeRequestArgument(buffer, subcommand, subcommandBytes);
        if (argument != null) {
            writeRequestArgument(buffer, argument, argumentBytes);
        }
        final RedisRequest request = newRequest(RedisProtocolSupport.Command.SLOWLOG, buffer);
        final Single<String> queued = reservedCnx.request(request, String.class);
        Future<List<T>> result = enqueueForExecute(state, singles, queued);
        return result;
    }

    @Override
    public <T> Future<List<T>> smembers(@RedisProtocolSupport.Key final CharSequence key) {
        requireNonNull(key);
        final int len = 2;
        final int keyBytes = numberOfBytesUtf8(key);
        final int capacity = calculateInitialCommandBufferSize(len, RedisProtocolSupport.Command.SMEMBERS) +
                    calculateRequestArgumentSize(keyBytes);
        Buffer buffer = reservedCnx.executionContext().bufferAllocator().newBuffer(capacity);
        writeRequestArraySize(buffer, len);
        RedisProtocolSupport.Command.SMEMBERS.encodeTo(buffer);
        writeRequestArgument(buffer, key, keyBytes);
        final RedisRequest request = newRequest(RedisProtocolSupport.Command.SMEMBERS, buffer);
        final Single<String> queued = reservedCnx.request(request, String.class);
        Future<List<T>> result = enqueueForExecute(state, singles, queued);
        return result;
    }

    @Override
    public Future<Long> smove(@RedisProtocolSupport.Key final CharSequence source,
                              @RedisProtocolSupport.Key final CharSequence destination, final CharSequence member) {
        requireNonNull(source);
        requireNonNull(destination);
        requireNonNull(member);
        final int len = 4;
        final int sourceBytes = numberOfBytesUtf8(source);
        final int destinationBytes = numberOfBytesUtf8(destination);
        final int memberBytes = numberOfBytesUtf8(member);
        final int capacity = calculateInitialCommandBufferSize(len, RedisProtocolSupport.Command.SMOVE) +
                    calculateRequestArgumentSize(sourceBytes) + calculateRequestArgumentSize(destinationBytes) +
                    calculateRequestArgumentSize(memberBytes);
        Buffer buffer = reservedCnx.executionContext().bufferAllocator().newBuffer(capacity);
        writeRequestArraySize(buffer, len);
        RedisProtocolSupport.Command.SMOVE.encodeTo(buffer);
        writeRequestArgument(buffer, source, sourceBytes);
        writeRequestArgument(buffer, destination, destinationBytes);
        writeRequestArgument(buffer, member, memberBytes);
        final RedisRequest request = newRequest(RedisProtocolSupport.Command.SMOVE, buffer);
        final Single<String> queued = reservedCnx.request(request, String.class);
        Future<Long> result = enqueueForExecute(state, singles, queued);
        return result;
    }

    @Override
    public <T> Future<List<T>> sort(@RedisProtocolSupport.Key final CharSequence key) {
        requireNonNull(key);
        final int len = 2;
        final int keyBytes = numberOfBytesUtf8(key);
        final int capacity = calculateInitialCommandBufferSize(len, RedisProtocolSupport.Command.SORT) +
                    calculateRequestArgumentSize(keyBytes);
        Buffer buffer = reservedCnx.executionContext().bufferAllocator().newBuffer(capacity);
        writeRequestArraySize(buffer, len);
        RedisProtocolSupport.Command.SORT.encodeTo(buffer);
        writeRequestArgument(buffer, key, keyBytes);
        final RedisRequest request = newRequest(RedisProtocolSupport.Command.SORT, buffer);
        final Single<String> queued = reservedCnx.request(request, String.class);
        Future<List<T>> result = enqueueForExecute(state, singles, queued);
        return result;
    }

    @Override
    public <T> Future<List<T>> sort(@RedisProtocolSupport.Key final CharSequence key,
                                    @Nullable final CharSequence byPattern,
                                    @Nullable final RedisProtocolSupport.OffsetCount offsetCount,
                                    final Collection<? extends CharSequence> getPatterns,
                                    @Nullable final RedisProtocolSupport.SortOrder order,
                                    @Nullable final RedisProtocolSupport.SortSorting sorting) {
        requireNonNull(key);
        requireNonNull(getPatterns);
        final int len = 3 + (byPattern == null ? 0 : 2) +
                    (offsetCount == null ? 0 : RedisProtocolSupport.OffsetCount.SIZE) + getPatterns.size() +
                    (order == null ? 0 : 1) + (sorting == null ? 0 : 1);
        final int keyBytes = numberOfBytesUtf8(key);
        final int byPatternBytes = byPattern == null ? 0 : numberOfBytesUtf8(byPattern);
        int getPatternsCapacity = 0;
        if (getPatterns instanceof List && getPatterns instanceof RandomAccess) {
            final List<CharSequence> list = (List<CharSequence>) getPatterns;
            for (int i = 0; i < list.size(); ++i) {
                final CharSequence arg = list.get(i);
                getPatternsCapacity += estimateRequestArgumentSize(arg);
            }
        } else {
            for (CharSequence arg : getPatterns) {
                getPatternsCapacity += estimateRequestArgumentSize(arg);
            }
        }
        final int capacity = calculateInitialCommandBufferSize(len, RedisProtocolSupport.Command.SORT) +
                    calculateRequestArgumentSize(keyBytes) +
                    (byPattern == null ? 0 : RedisProtocolSupport.SubCommand.BY.encodedByteCount()) +
                    (byPattern == null ? 0 : calculateRequestArgumentSize(byPatternBytes)) +
                    (offsetCount == null ? 0 : offsetCount.encodedByteCount()) +
                    RedisProtocolSupport.SubCommand.GET.encodedByteCount() + getPatternsCapacity +
                    (order == null ? 0 : order.encodedByteCount()) + (sorting == null ? 0 : sorting.encodedByteCount());
        Buffer buffer = reservedCnx.executionContext().bufferAllocator().newBuffer(capacity);
        writeRequestArraySize(buffer, len);
        RedisProtocolSupport.Command.SORT.encodeTo(buffer);
        writeRequestArgument(buffer, key, keyBytes);
        if (byPattern != null) {
            RedisProtocolSupport.SubCommand.BY.encodeTo(buffer);
            writeRequestArgument(buffer, byPattern, byPatternBytes);
        }
        if (offsetCount != null) {
            offsetCount.encodeTo(buffer);
        }
        RedisProtocolSupport.SubCommand.GET.encodeTo(buffer);
        if (getPatterns instanceof List && getPatterns instanceof RandomAccess) {
            final List<CharSequence> list = (List<CharSequence>) getPatterns;
            for (int i = 0; i < list.size(); ++i) {
                final CharSequence arg = list.get(i);
                writeRequestArgument(buffer, arg);
            }
        } else {
            for (CharSequence arg : getPatterns) {
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
        final Single<String> queued = reservedCnx.request(request, String.class);
        Future<List<T>> result = enqueueForExecute(state, singles, queued);
        return result;
    }

    @Override
    public Future<Long> sort(@RedisProtocolSupport.Key final CharSequence key,
                             @RedisProtocolSupport.Key final CharSequence storeDestination) {
        requireNonNull(key);
        requireNonNull(storeDestination);
        final int len = 4;
        final int keyBytes = numberOfBytesUtf8(key);
        final int storeDestinationBytes = numberOfBytesUtf8(storeDestination);
        final int capacity = calculateInitialCommandBufferSize(len, RedisProtocolSupport.Command.SORT) +
                    calculateRequestArgumentSize(keyBytes) + RedisProtocolSupport.SubCommand.STORE.encodedByteCount() +
                    calculateRequestArgumentSize(storeDestinationBytes);
        Buffer buffer = reservedCnx.executionContext().bufferAllocator().newBuffer(capacity);
        writeRequestArraySize(buffer, len);
        RedisProtocolSupport.Command.SORT.encodeTo(buffer);
        writeRequestArgument(buffer, key, keyBytes);
        RedisProtocolSupport.SubCommand.STORE.encodeTo(buffer);
        writeRequestArgument(buffer, storeDestination, storeDestinationBytes);
        final RedisRequest request = newRequest(RedisProtocolSupport.Command.SORT, buffer);
        final Single<String> queued = reservedCnx.request(request, String.class);
        Future<Long> result = enqueueForExecute(state, singles, queued);
        return result;
    }

    @Override
    public Future<Long> sort(@RedisProtocolSupport.Key final CharSequence key,
                             @RedisProtocolSupport.Key final CharSequence storeDestination,
                             @Nullable final CharSequence byPattern,
                             @Nullable final RedisProtocolSupport.OffsetCount offsetCount,
                             final Collection<? extends CharSequence> getPatterns,
                             @Nullable final RedisProtocolSupport.SortOrder order,
                             @Nullable final RedisProtocolSupport.SortSorting sorting) {
        requireNonNull(key);
        requireNonNull(storeDestination);
        requireNonNull(getPatterns);
        final int len = 5 + (byPattern == null ? 0 : 2) +
                    (offsetCount == null ? 0 : RedisProtocolSupport.OffsetCount.SIZE) + getPatterns.size() +
                    (order == null ? 0 : 1) + (sorting == null ? 0 : 1);
        final int keyBytes = numberOfBytesUtf8(key);
        final int storeDestinationBytes = numberOfBytesUtf8(storeDestination);
        final int byPatternBytes = byPattern == null ? 0 : numberOfBytesUtf8(byPattern);
        int getPatternsCapacity = 0;
        if (getPatterns instanceof List && getPatterns instanceof RandomAccess) {
            final List<CharSequence> list = (List<CharSequence>) getPatterns;
            for (int i = 0; i < list.size(); ++i) {
                final CharSequence arg = list.get(i);
                getPatternsCapacity += estimateRequestArgumentSize(arg);
            }
        } else {
            for (CharSequence arg : getPatterns) {
                getPatternsCapacity += estimateRequestArgumentSize(arg);
            }
        }
        final int capacity = calculateInitialCommandBufferSize(len, RedisProtocolSupport.Command.SORT) +
                    calculateRequestArgumentSize(keyBytes) + RedisProtocolSupport.SubCommand.STORE.encodedByteCount() +
                    calculateRequestArgumentSize(storeDestinationBytes) +
                    (byPattern == null ? 0 : RedisProtocolSupport.SubCommand.BY.encodedByteCount()) +
                    (byPattern == null ? 0 : calculateRequestArgumentSize(byPatternBytes)) +
                    (offsetCount == null ? 0 : offsetCount.encodedByteCount()) +
                    RedisProtocolSupport.SubCommand.GET.encodedByteCount() + getPatternsCapacity +
                    (order == null ? 0 : order.encodedByteCount()) + (sorting == null ? 0 : sorting.encodedByteCount());
        Buffer buffer = reservedCnx.executionContext().bufferAllocator().newBuffer(capacity);
        writeRequestArraySize(buffer, len);
        RedisProtocolSupport.Command.SORT.encodeTo(buffer);
        writeRequestArgument(buffer, key, keyBytes);
        RedisProtocolSupport.SubCommand.STORE.encodeTo(buffer);
        writeRequestArgument(buffer, storeDestination, storeDestinationBytes);
        if (byPattern != null) {
            RedisProtocolSupport.SubCommand.BY.encodeTo(buffer);
            writeRequestArgument(buffer, byPattern, byPatternBytes);
        }
        if (offsetCount != null) {
            offsetCount.encodeTo(buffer);
        }
        RedisProtocolSupport.SubCommand.GET.encodeTo(buffer);
        if (getPatterns instanceof List && getPatterns instanceof RandomAccess) {
            final List<CharSequence> list = (List<CharSequence>) getPatterns;
            for (int i = 0; i < list.size(); ++i) {
                final CharSequence arg = list.get(i);
                writeRequestArgument(buffer, arg);
            }
        } else {
            for (CharSequence arg : getPatterns) {
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
        final Single<String> queued = reservedCnx.request(request, String.class);
        Future<Long> result = enqueueForExecute(state, singles, queued);
        return result;
    }

    @Override
    public Future<String> spop(@RedisProtocolSupport.Key final CharSequence key) {
        requireNonNull(key);
        final int len = 2;
        final int keyBytes = numberOfBytesUtf8(key);
        final int capacity = calculateInitialCommandBufferSize(len, RedisProtocolSupport.Command.SPOP) +
                    calculateRequestArgumentSize(keyBytes);
        Buffer buffer = reservedCnx.executionContext().bufferAllocator().newBuffer(capacity);
        writeRequestArraySize(buffer, len);
        RedisProtocolSupport.Command.SPOP.encodeTo(buffer);
        writeRequestArgument(buffer, key, keyBytes);
        final RedisRequest request = newRequest(RedisProtocolSupport.Command.SPOP, buffer);
        final Single<String> queued = reservedCnx.request(request, String.class);
        Future<String> result = enqueueForExecute(state, singles, queued);
        return result;
    }

    @Override
    public Future<String> spop(@RedisProtocolSupport.Key final CharSequence key, @Nullable final Long count) {
        requireNonNull(key);
        final int len = 2 + (count == null ? 0 : 1);
        final int keyBytes = numberOfBytesUtf8(key);
        final int capacity = calculateInitialCommandBufferSize(len, RedisProtocolSupport.Command.SPOP) +
                    calculateRequestArgumentSize(keyBytes) + (count == null ? 0 : calculateRequestArgumentSize(count));
        Buffer buffer = reservedCnx.executionContext().bufferAllocator().newBuffer(capacity);
        writeRequestArraySize(buffer, len);
        RedisProtocolSupport.Command.SPOP.encodeTo(buffer);
        writeRequestArgument(buffer, key, keyBytes);
        if (count != null) {
            writeRequestArgument(buffer, count);
        }
        final RedisRequest request = newRequest(RedisProtocolSupport.Command.SPOP, buffer);
        final Single<String> queued = reservedCnx.request(request, String.class);
        Future<String> result = enqueueForExecute(state, singles, queued);
        return result;
    }

    @Override
    public Future<String> srandmember(@RedisProtocolSupport.Key final CharSequence key) {
        requireNonNull(key);
        final int len = 2;
        final int keyBytes = numberOfBytesUtf8(key);
        final int capacity = calculateInitialCommandBufferSize(len, RedisProtocolSupport.Command.SRANDMEMBER) +
                    calculateRequestArgumentSize(keyBytes);
        Buffer buffer = reservedCnx.executionContext().bufferAllocator().newBuffer(capacity);
        writeRequestArraySize(buffer, len);
        RedisProtocolSupport.Command.SRANDMEMBER.encodeTo(buffer);
        writeRequestArgument(buffer, key, keyBytes);
        final RedisRequest request = newRequest(RedisProtocolSupport.Command.SRANDMEMBER, buffer);
        final Single<String> queued = reservedCnx.request(request, String.class);
        Future<String> result = enqueueForExecute(state, singles, queued);
        return result;
    }

    @Override
    public Future<List<String>> srandmember(@RedisProtocolSupport.Key final CharSequence key, final long count) {
        requireNonNull(key);
        final int len = 3;
        final int keyBytes = numberOfBytesUtf8(key);
        final int capacity = calculateInitialCommandBufferSize(len, RedisProtocolSupport.Command.SRANDMEMBER) +
                    calculateRequestArgumentSize(keyBytes) + calculateRequestArgumentSize(count);
        Buffer buffer = reservedCnx.executionContext().bufferAllocator().newBuffer(capacity);
        writeRequestArraySize(buffer, len);
        RedisProtocolSupport.Command.SRANDMEMBER.encodeTo(buffer);
        writeRequestArgument(buffer, key, keyBytes);
        writeRequestArgument(buffer, count);
        final RedisRequest request = newRequest(RedisProtocolSupport.Command.SRANDMEMBER, buffer);
        final Single<String> queued = reservedCnx.request(request, String.class);
        Future<List<String>> result = enqueueForExecute(state, singles, queued);
        return result;
    }

    @Override
    public Future<Long> srem(@RedisProtocolSupport.Key final CharSequence key, final CharSequence member) {
        requireNonNull(key);
        requireNonNull(member);
        final int len = 3;
        final int keyBytes = numberOfBytesUtf8(key);
        final int memberBytes = numberOfBytesUtf8(member);
        final int capacity = calculateInitialCommandBufferSize(len, RedisProtocolSupport.Command.SREM) +
                    calculateRequestArgumentSize(keyBytes) + calculateRequestArgumentSize(memberBytes);
        Buffer buffer = reservedCnx.executionContext().bufferAllocator().newBuffer(capacity);
        writeRequestArraySize(buffer, len);
        RedisProtocolSupport.Command.SREM.encodeTo(buffer);
        writeRequestArgument(buffer, key, keyBytes);
        writeRequestArgument(buffer, member, memberBytes);
        final RedisRequest request = newRequest(RedisProtocolSupport.Command.SREM, buffer);
        final Single<String> queued = reservedCnx.request(request, String.class);
        Future<Long> result = enqueueForExecute(state, singles, queued);
        return result;
    }

    @Override
    public Future<Long> srem(@RedisProtocolSupport.Key final CharSequence key, final CharSequence member1,
                             final CharSequence member2) {
        requireNonNull(key);
        requireNonNull(member1);
        requireNonNull(member2);
        final int len = 4;
        final int keyBytes = numberOfBytesUtf8(key);
        final int member1Bytes = numberOfBytesUtf8(member1);
        final int member2Bytes = numberOfBytesUtf8(member2);
        final int capacity = calculateInitialCommandBufferSize(len, RedisProtocolSupport.Command.SREM) +
                    calculateRequestArgumentSize(keyBytes) + calculateRequestArgumentSize(member1Bytes) +
                    calculateRequestArgumentSize(member2Bytes);
        Buffer buffer = reservedCnx.executionContext().bufferAllocator().newBuffer(capacity);
        writeRequestArraySize(buffer, len);
        RedisProtocolSupport.Command.SREM.encodeTo(buffer);
        writeRequestArgument(buffer, key, keyBytes);
        writeRequestArgument(buffer, member1, member1Bytes);
        writeRequestArgument(buffer, member2, member2Bytes);
        final RedisRequest request = newRequest(RedisProtocolSupport.Command.SREM, buffer);
        final Single<String> queued = reservedCnx.request(request, String.class);
        Future<Long> result = enqueueForExecute(state, singles, queued);
        return result;
    }

    @Override
    public Future<Long> srem(@RedisProtocolSupport.Key final CharSequence key, final CharSequence member1,
                             final CharSequence member2, final CharSequence member3) {
        requireNonNull(key);
        requireNonNull(member1);
        requireNonNull(member2);
        requireNonNull(member3);
        final int len = 5;
        final int keyBytes = numberOfBytesUtf8(key);
        final int member1Bytes = numberOfBytesUtf8(member1);
        final int member2Bytes = numberOfBytesUtf8(member2);
        final int member3Bytes = numberOfBytesUtf8(member3);
        final int capacity = calculateInitialCommandBufferSize(len, RedisProtocolSupport.Command.SREM) +
                    calculateRequestArgumentSize(keyBytes) + calculateRequestArgumentSize(member1Bytes) +
                    calculateRequestArgumentSize(member2Bytes) + calculateRequestArgumentSize(member3Bytes);
        Buffer buffer = reservedCnx.executionContext().bufferAllocator().newBuffer(capacity);
        writeRequestArraySize(buffer, len);
        RedisProtocolSupport.Command.SREM.encodeTo(buffer);
        writeRequestArgument(buffer, key, keyBytes);
        writeRequestArgument(buffer, member1, member1Bytes);
        writeRequestArgument(buffer, member2, member2Bytes);
        writeRequestArgument(buffer, member3, member3Bytes);
        final RedisRequest request = newRequest(RedisProtocolSupport.Command.SREM, buffer);
        final Single<String> queued = reservedCnx.request(request, String.class);
        Future<Long> result = enqueueForExecute(state, singles, queued);
        return result;
    }

    @Override
    public Future<Long> srem(@RedisProtocolSupport.Key final CharSequence key,
                             final Collection<? extends CharSequence> members) {
        requireNonNull(key);
        requireNonNull(members);
        final int len = 2 + members.size();
        final int keyBytes = numberOfBytesUtf8(key);
        int membersCapacity = 0;
        if (members instanceof List && members instanceof RandomAccess) {
            final List<CharSequence> list = (List<CharSequence>) members;
            for (int i = 0; i < list.size(); ++i) {
                final CharSequence arg = list.get(i);
                membersCapacity += estimateRequestArgumentSize(arg);
            }
        } else {
            for (CharSequence arg : members) {
                membersCapacity += estimateRequestArgumentSize(arg);
            }
        }
        final int capacity = calculateInitialCommandBufferSize(len, RedisProtocolSupport.Command.SREM) +
                    calculateRequestArgumentSize(keyBytes) + membersCapacity;
        Buffer buffer = reservedCnx.executionContext().bufferAllocator().newBuffer(capacity);
        writeRequestArraySize(buffer, len);
        RedisProtocolSupport.Command.SREM.encodeTo(buffer);
        writeRequestArgument(buffer, key, keyBytes);
        if (members instanceof List && members instanceof RandomAccess) {
            final List<CharSequence> list = (List<CharSequence>) members;
            for (int i = 0; i < list.size(); ++i) {
                final CharSequence arg = list.get(i);
                writeRequestArgument(buffer, arg);
            }
        } else {
            for (CharSequence arg : members) {
                writeRequestArgument(buffer, arg);
            }
        }
        final RedisRequest request = newRequest(RedisProtocolSupport.Command.SREM, buffer);
        final Single<String> queued = reservedCnx.request(request, String.class);
        Future<Long> result = enqueueForExecute(state, singles, queued);
        return result;
    }

    @Override
    public <T> Future<List<T>> sscan(@RedisProtocolSupport.Key final CharSequence key, final long cursor) {
        requireNonNull(key);
        final int len = 3;
        final int keyBytes = numberOfBytesUtf8(key);
        final int capacity = calculateInitialCommandBufferSize(len, RedisProtocolSupport.Command.SSCAN) +
                    calculateRequestArgumentSize(keyBytes) + calculateRequestArgumentSize(cursor);
        Buffer buffer = reservedCnx.executionContext().bufferAllocator().newBuffer(capacity);
        writeRequestArraySize(buffer, len);
        RedisProtocolSupport.Command.SSCAN.encodeTo(buffer);
        writeRequestArgument(buffer, key, keyBytes);
        writeRequestArgument(buffer, cursor);
        final RedisRequest request = newRequest(RedisProtocolSupport.Command.SSCAN, buffer);
        final Single<String> queued = reservedCnx.request(request, String.class);
        Future<List<T>> result = enqueueForExecute(state, singles, queued);
        return result;
    }

    @Override
    public <T> Future<List<T>> sscan(@RedisProtocolSupport.Key final CharSequence key, final long cursor,
                                     @Nullable final CharSequence matchPattern, @Nullable final Long count) {
        requireNonNull(key);
        final int len = 3 + (matchPattern == null ? 0 : 2) + (count == null ? 0 : 2);
        final int keyBytes = numberOfBytesUtf8(key);
        final int matchPatternBytes = matchPattern == null ? 0 : numberOfBytesUtf8(matchPattern);
        final int capacity = calculateInitialCommandBufferSize(len, RedisProtocolSupport.Command.SSCAN) +
                    calculateRequestArgumentSize(keyBytes) + calculateRequestArgumentSize(cursor) +
                    (matchPattern == null ? 0 : RedisProtocolSupport.SubCommand.MATCH.encodedByteCount()) +
                    (matchPattern == null ? 0 : calculateRequestArgumentSize(matchPatternBytes)) +
                    (count == null ? 0 : RedisProtocolSupport.SubCommand.COUNT.encodedByteCount()) +
                    (count == null ? 0 : calculateRequestArgumentSize(count));
        Buffer buffer = reservedCnx.executionContext().bufferAllocator().newBuffer(capacity);
        writeRequestArraySize(buffer, len);
        RedisProtocolSupport.Command.SSCAN.encodeTo(buffer);
        writeRequestArgument(buffer, key, keyBytes);
        writeRequestArgument(buffer, cursor);
        if (matchPattern != null) {
            RedisProtocolSupport.SubCommand.MATCH.encodeTo(buffer);
            writeRequestArgument(buffer, matchPattern, matchPatternBytes);
        }
        if (count != null) {
            RedisProtocolSupport.SubCommand.COUNT.encodeTo(buffer);
            writeRequestArgument(buffer, count);
        }
        final RedisRequest request = newRequest(RedisProtocolSupport.Command.SSCAN, buffer);
        final Single<String> queued = reservedCnx.request(request, String.class);
        Future<List<T>> result = enqueueForExecute(state, singles, queued);
        return result;
    }

    @Override
    public Future<Long> strlen(@RedisProtocolSupport.Key final CharSequence key) {
        requireNonNull(key);
        final int len = 2;
        final int keyBytes = numberOfBytesUtf8(key);
        final int capacity = calculateInitialCommandBufferSize(len, RedisProtocolSupport.Command.STRLEN) +
                    calculateRequestArgumentSize(keyBytes);
        Buffer buffer = reservedCnx.executionContext().bufferAllocator().newBuffer(capacity);
        writeRequestArraySize(buffer, len);
        RedisProtocolSupport.Command.STRLEN.encodeTo(buffer);
        writeRequestArgument(buffer, key, keyBytes);
        final RedisRequest request = newRequest(RedisProtocolSupport.Command.STRLEN, buffer);
        final Single<String> queued = reservedCnx.request(request, String.class);
        Future<Long> result = enqueueForExecute(state, singles, queued);
        return result;
    }

    @Override
    public <T> Future<List<T>> sunion(@RedisProtocolSupport.Key final CharSequence key) {
        requireNonNull(key);
        final int len = 2;
        final int keyBytes = numberOfBytesUtf8(key);
        final int capacity = calculateInitialCommandBufferSize(len, RedisProtocolSupport.Command.SUNION) +
                    calculateRequestArgumentSize(keyBytes);
        Buffer buffer = reservedCnx.executionContext().bufferAllocator().newBuffer(capacity);
        writeRequestArraySize(buffer, len);
        RedisProtocolSupport.Command.SUNION.encodeTo(buffer);
        writeRequestArgument(buffer, key, keyBytes);
        final RedisRequest request = newRequest(RedisProtocolSupport.Command.SUNION, buffer);
        final Single<String> queued = reservedCnx.request(request, String.class);
        Future<List<T>> result = enqueueForExecute(state, singles, queued);
        return result;
    }

    @Override
    public <T> Future<List<T>> sunion(@RedisProtocolSupport.Key final CharSequence key1,
                                      @RedisProtocolSupport.Key final CharSequence key2) {
        requireNonNull(key1);
        requireNonNull(key2);
        final int len = 3;
        final int key1Bytes = numberOfBytesUtf8(key1);
        final int key2Bytes = numberOfBytesUtf8(key2);
        final int capacity = calculateInitialCommandBufferSize(len, RedisProtocolSupport.Command.SUNION) +
                    calculateRequestArgumentSize(key1Bytes) + calculateRequestArgumentSize(key2Bytes);
        Buffer buffer = reservedCnx.executionContext().bufferAllocator().newBuffer(capacity);
        writeRequestArraySize(buffer, len);
        RedisProtocolSupport.Command.SUNION.encodeTo(buffer);
        writeRequestArgument(buffer, key1, key1Bytes);
        writeRequestArgument(buffer, key2, key2Bytes);
        final RedisRequest request = newRequest(RedisProtocolSupport.Command.SUNION, buffer);
        final Single<String> queued = reservedCnx.request(request, String.class);
        Future<List<T>> result = enqueueForExecute(state, singles, queued);
        return result;
    }

    @Override
    public <T> Future<List<T>> sunion(@RedisProtocolSupport.Key final CharSequence key1,
                                      @RedisProtocolSupport.Key final CharSequence key2,
                                      @RedisProtocolSupport.Key final CharSequence key3) {
        requireNonNull(key1);
        requireNonNull(key2);
        requireNonNull(key3);
        final int len = 4;
        final int key1Bytes = numberOfBytesUtf8(key1);
        final int key2Bytes = numberOfBytesUtf8(key2);
        final int key3Bytes = numberOfBytesUtf8(key3);
        final int capacity = calculateInitialCommandBufferSize(len, RedisProtocolSupport.Command.SUNION) +
                    calculateRequestArgumentSize(key1Bytes) + calculateRequestArgumentSize(key2Bytes) +
                    calculateRequestArgumentSize(key3Bytes);
        Buffer buffer = reservedCnx.executionContext().bufferAllocator().newBuffer(capacity);
        writeRequestArraySize(buffer, len);
        RedisProtocolSupport.Command.SUNION.encodeTo(buffer);
        writeRequestArgument(buffer, key1, key1Bytes);
        writeRequestArgument(buffer, key2, key2Bytes);
        writeRequestArgument(buffer, key3, key3Bytes);
        final RedisRequest request = newRequest(RedisProtocolSupport.Command.SUNION, buffer);
        final Single<String> queued = reservedCnx.request(request, String.class);
        Future<List<T>> result = enqueueForExecute(state, singles, queued);
        return result;
    }

    @Override
    public <T> Future<List<T>> sunion(@RedisProtocolSupport.Key final Collection<? extends CharSequence> keys) {
        requireNonNull(keys);
        final int len = 1 + keys.size();
        int keysCapacity = 0;
        if (keys instanceof List && keys instanceof RandomAccess) {
            final List<CharSequence> list = (List<CharSequence>) keys;
            for (int i = 0; i < list.size(); ++i) {
                final CharSequence arg = list.get(i);
                keysCapacity += estimateRequestArgumentSize(arg);
            }
        } else {
            for (CharSequence arg : keys) {
                keysCapacity += estimateRequestArgumentSize(arg);
            }
        }
        final int capacity = calculateInitialCommandBufferSize(len, RedisProtocolSupport.Command.SUNION) + keysCapacity;
        Buffer buffer = reservedCnx.executionContext().bufferAllocator().newBuffer(capacity);
        writeRequestArraySize(buffer, len);
        RedisProtocolSupport.Command.SUNION.encodeTo(buffer);
        if (keys instanceof List && keys instanceof RandomAccess) {
            final List<CharSequence> list = (List<CharSequence>) keys;
            for (int i = 0; i < list.size(); ++i) {
                final CharSequence arg = list.get(i);
                writeRequestArgument(buffer, arg);
            }
        } else {
            for (CharSequence arg : keys) {
                writeRequestArgument(buffer, arg);
            }
        }
        final RedisRequest request = newRequest(RedisProtocolSupport.Command.SUNION, buffer);
        final Single<String> queued = reservedCnx.request(request, String.class);
        Future<List<T>> result = enqueueForExecute(state, singles, queued);
        return result;
    }

    @Override
    public Future<Long> sunionstore(@RedisProtocolSupport.Key final CharSequence destination,
                                    @RedisProtocolSupport.Key final CharSequence key) {
        requireNonNull(destination);
        requireNonNull(key);
        final int len = 3;
        final int destinationBytes = numberOfBytesUtf8(destination);
        final int keyBytes = numberOfBytesUtf8(key);
        final int capacity = calculateInitialCommandBufferSize(len, RedisProtocolSupport.Command.SUNIONSTORE) +
                    calculateRequestArgumentSize(destinationBytes) + calculateRequestArgumentSize(keyBytes);
        Buffer buffer = reservedCnx.executionContext().bufferAllocator().newBuffer(capacity);
        writeRequestArraySize(buffer, len);
        RedisProtocolSupport.Command.SUNIONSTORE.encodeTo(buffer);
        writeRequestArgument(buffer, destination, destinationBytes);
        writeRequestArgument(buffer, key, keyBytes);
        final RedisRequest request = newRequest(RedisProtocolSupport.Command.SUNIONSTORE, buffer);
        final Single<String> queued = reservedCnx.request(request, String.class);
        Future<Long> result = enqueueForExecute(state, singles, queued);
        return result;
    }

    @Override
    public Future<Long> sunionstore(@RedisProtocolSupport.Key final CharSequence destination,
                                    @RedisProtocolSupport.Key final CharSequence key1,
                                    @RedisProtocolSupport.Key final CharSequence key2) {
        requireNonNull(destination);
        requireNonNull(key1);
        requireNonNull(key2);
        final int len = 4;
        final int destinationBytes = numberOfBytesUtf8(destination);
        final int key1Bytes = numberOfBytesUtf8(key1);
        final int key2Bytes = numberOfBytesUtf8(key2);
        final int capacity = calculateInitialCommandBufferSize(len, RedisProtocolSupport.Command.SUNIONSTORE) +
                    calculateRequestArgumentSize(destinationBytes) + calculateRequestArgumentSize(key1Bytes) +
                    calculateRequestArgumentSize(key2Bytes);
        Buffer buffer = reservedCnx.executionContext().bufferAllocator().newBuffer(capacity);
        writeRequestArraySize(buffer, len);
        RedisProtocolSupport.Command.SUNIONSTORE.encodeTo(buffer);
        writeRequestArgument(buffer, destination, destinationBytes);
        writeRequestArgument(buffer, key1, key1Bytes);
        writeRequestArgument(buffer, key2, key2Bytes);
        final RedisRequest request = newRequest(RedisProtocolSupport.Command.SUNIONSTORE, buffer);
        final Single<String> queued = reservedCnx.request(request, String.class);
        Future<Long> result = enqueueForExecute(state, singles, queued);
        return result;
    }

    @Override
    public Future<Long> sunionstore(@RedisProtocolSupport.Key final CharSequence destination,
                                    @RedisProtocolSupport.Key final CharSequence key1,
                                    @RedisProtocolSupport.Key final CharSequence key2,
                                    @RedisProtocolSupport.Key final CharSequence key3) {
        requireNonNull(destination);
        requireNonNull(key1);
        requireNonNull(key2);
        requireNonNull(key3);
        final int len = 5;
        final int destinationBytes = numberOfBytesUtf8(destination);
        final int key1Bytes = numberOfBytesUtf8(key1);
        final int key2Bytes = numberOfBytesUtf8(key2);
        final int key3Bytes = numberOfBytesUtf8(key3);
        final int capacity = calculateInitialCommandBufferSize(len, RedisProtocolSupport.Command.SUNIONSTORE) +
                    calculateRequestArgumentSize(destinationBytes) + calculateRequestArgumentSize(key1Bytes) +
                    calculateRequestArgumentSize(key2Bytes) + calculateRequestArgumentSize(key3Bytes);
        Buffer buffer = reservedCnx.executionContext().bufferAllocator().newBuffer(capacity);
        writeRequestArraySize(buffer, len);
        RedisProtocolSupport.Command.SUNIONSTORE.encodeTo(buffer);
        writeRequestArgument(buffer, destination, destinationBytes);
        writeRequestArgument(buffer, key1, key1Bytes);
        writeRequestArgument(buffer, key2, key2Bytes);
        writeRequestArgument(buffer, key3, key3Bytes);
        final RedisRequest request = newRequest(RedisProtocolSupport.Command.SUNIONSTORE, buffer);
        final Single<String> queued = reservedCnx.request(request, String.class);
        Future<Long> result = enqueueForExecute(state, singles, queued);
        return result;
    }

    @Override
    public Future<Long> sunionstore(@RedisProtocolSupport.Key final CharSequence destination,
                                    @RedisProtocolSupport.Key final Collection<? extends CharSequence> keys) {
        requireNonNull(destination);
        requireNonNull(keys);
        final int len = 2 + keys.size();
        final int destinationBytes = numberOfBytesUtf8(destination);
        int keysCapacity = 0;
        if (keys instanceof List && keys instanceof RandomAccess) {
            final List<CharSequence> list = (List<CharSequence>) keys;
            for (int i = 0; i < list.size(); ++i) {
                final CharSequence arg = list.get(i);
                keysCapacity += estimateRequestArgumentSize(arg);
            }
        } else {
            for (CharSequence arg : keys) {
                keysCapacity += estimateRequestArgumentSize(arg);
            }
        }
        final int capacity = calculateInitialCommandBufferSize(len, RedisProtocolSupport.Command.SUNIONSTORE) +
                    calculateRequestArgumentSize(destinationBytes) + keysCapacity;
        Buffer buffer = reservedCnx.executionContext().bufferAllocator().newBuffer(capacity);
        writeRequestArraySize(buffer, len);
        RedisProtocolSupport.Command.SUNIONSTORE.encodeTo(buffer);
        writeRequestArgument(buffer, destination, destinationBytes);
        if (keys instanceof List && keys instanceof RandomAccess) {
            final List<CharSequence> list = (List<CharSequence>) keys;
            for (int i = 0; i < list.size(); ++i) {
                final CharSequence arg = list.get(i);
                writeRequestArgument(buffer, arg);
            }
        } else {
            for (CharSequence arg : keys) {
                writeRequestArgument(buffer, arg);
            }
        }
        final RedisRequest request = newRequest(RedisProtocolSupport.Command.SUNIONSTORE, buffer);
        final Single<String> queued = reservedCnx.request(request, String.class);
        Future<Long> result = enqueueForExecute(state, singles, queued);
        return result;
    }

    @Override
    public Future<String> swapdb(final long index, final long index1) {
        final int len = 3;
        final int capacity = calculateInitialCommandBufferSize(len, RedisProtocolSupport.Command.SWAPDB) +
                    calculateRequestArgumentSize(index) + calculateRequestArgumentSize(index1);
        Buffer buffer = reservedCnx.executionContext().bufferAllocator().newBuffer(capacity);
        writeRequestArraySize(buffer, len);
        RedisProtocolSupport.Command.SWAPDB.encodeTo(buffer);
        writeRequestArgument(buffer, index);
        writeRequestArgument(buffer, index1);
        final RedisRequest request = newRequest(RedisProtocolSupport.Command.SWAPDB, buffer);
        final Single<String> queued = reservedCnx.request(request, String.class);
        Future<String> result = enqueueForExecute(state, singles, queued);
        return result;
    }

    @Override
    public <T> Future<List<T>> time() {
        final int len = 1;
        final int capacity = calculateInitialCommandBufferSize(len, RedisProtocolSupport.Command.TIME);
        Buffer buffer = reservedCnx.executionContext().bufferAllocator().newBuffer(capacity);
        writeRequestArraySize(buffer, len);
        RedisProtocolSupport.Command.TIME.encodeTo(buffer);
        final RedisRequest request = newRequest(RedisProtocolSupport.Command.TIME, buffer);
        final Single<String> queued = reservedCnx.request(request, String.class);
        Future<List<T>> result = enqueueForExecute(state, singles, queued);
        return result;
    }

    @Override
    public Future<Long> touch(@RedisProtocolSupport.Key final CharSequence key) {
        requireNonNull(key);
        final int len = 2;
        final int keyBytes = numberOfBytesUtf8(key);
        final int capacity = calculateInitialCommandBufferSize(len, RedisProtocolSupport.Command.TOUCH) +
                    calculateRequestArgumentSize(keyBytes);
        Buffer buffer = reservedCnx.executionContext().bufferAllocator().newBuffer(capacity);
        writeRequestArraySize(buffer, len);
        RedisProtocolSupport.Command.TOUCH.encodeTo(buffer);
        writeRequestArgument(buffer, key, keyBytes);
        final RedisRequest request = newRequest(RedisProtocolSupport.Command.TOUCH, buffer);
        final Single<String> queued = reservedCnx.request(request, String.class);
        Future<Long> result = enqueueForExecute(state, singles, queued);
        return result;
    }

    @Override
    public Future<Long> touch(@RedisProtocolSupport.Key final CharSequence key1,
                              @RedisProtocolSupport.Key final CharSequence key2) {
        requireNonNull(key1);
        requireNonNull(key2);
        final int len = 3;
        final int key1Bytes = numberOfBytesUtf8(key1);
        final int key2Bytes = numberOfBytesUtf8(key2);
        final int capacity = calculateInitialCommandBufferSize(len, RedisProtocolSupport.Command.TOUCH) +
                    calculateRequestArgumentSize(key1Bytes) + calculateRequestArgumentSize(key2Bytes);
        Buffer buffer = reservedCnx.executionContext().bufferAllocator().newBuffer(capacity);
        writeRequestArraySize(buffer, len);
        RedisProtocolSupport.Command.TOUCH.encodeTo(buffer);
        writeRequestArgument(buffer, key1, key1Bytes);
        writeRequestArgument(buffer, key2, key2Bytes);
        final RedisRequest request = newRequest(RedisProtocolSupport.Command.TOUCH, buffer);
        final Single<String> queued = reservedCnx.request(request, String.class);
        Future<Long> result = enqueueForExecute(state, singles, queued);
        return result;
    }

    @Override
    public Future<Long> touch(@RedisProtocolSupport.Key final CharSequence key1,
                              @RedisProtocolSupport.Key final CharSequence key2,
                              @RedisProtocolSupport.Key final CharSequence key3) {
        requireNonNull(key1);
        requireNonNull(key2);
        requireNonNull(key3);
        final int len = 4;
        final int key1Bytes = numberOfBytesUtf8(key1);
        final int key2Bytes = numberOfBytesUtf8(key2);
        final int key3Bytes = numberOfBytesUtf8(key3);
        final int capacity = calculateInitialCommandBufferSize(len, RedisProtocolSupport.Command.TOUCH) +
                    calculateRequestArgumentSize(key1Bytes) + calculateRequestArgumentSize(key2Bytes) +
                    calculateRequestArgumentSize(key3Bytes);
        Buffer buffer = reservedCnx.executionContext().bufferAllocator().newBuffer(capacity);
        writeRequestArraySize(buffer, len);
        RedisProtocolSupport.Command.TOUCH.encodeTo(buffer);
        writeRequestArgument(buffer, key1, key1Bytes);
        writeRequestArgument(buffer, key2, key2Bytes);
        writeRequestArgument(buffer, key3, key3Bytes);
        final RedisRequest request = newRequest(RedisProtocolSupport.Command.TOUCH, buffer);
        final Single<String> queued = reservedCnx.request(request, String.class);
        Future<Long> result = enqueueForExecute(state, singles, queued);
        return result;
    }

    @Override
    public Future<Long> touch(@RedisProtocolSupport.Key final Collection<? extends CharSequence> keys) {
        requireNonNull(keys);
        final int len = 1 + keys.size();
        int keysCapacity = 0;
        if (keys instanceof List && keys instanceof RandomAccess) {
            final List<CharSequence> list = (List<CharSequence>) keys;
            for (int i = 0; i < list.size(); ++i) {
                final CharSequence arg = list.get(i);
                keysCapacity += estimateRequestArgumentSize(arg);
            }
        } else {
            for (CharSequence arg : keys) {
                keysCapacity += estimateRequestArgumentSize(arg);
            }
        }
        final int capacity = calculateInitialCommandBufferSize(len, RedisProtocolSupport.Command.TOUCH) + keysCapacity;
        Buffer buffer = reservedCnx.executionContext().bufferAllocator().newBuffer(capacity);
        writeRequestArraySize(buffer, len);
        RedisProtocolSupport.Command.TOUCH.encodeTo(buffer);
        if (keys instanceof List && keys instanceof RandomAccess) {
            final List<CharSequence> list = (List<CharSequence>) keys;
            for (int i = 0; i < list.size(); ++i) {
                final CharSequence arg = list.get(i);
                writeRequestArgument(buffer, arg);
            }
        } else {
            for (CharSequence arg : keys) {
                writeRequestArgument(buffer, arg);
            }
        }
        final RedisRequest request = newRequest(RedisProtocolSupport.Command.TOUCH, buffer);
        final Single<String> queued = reservedCnx.request(request, String.class);
        Future<Long> result = enqueueForExecute(state, singles, queued);
        return result;
    }

    @Override
    public Future<Long> ttl(@RedisProtocolSupport.Key final CharSequence key) {
        requireNonNull(key);
        final int len = 2;
        final int keyBytes = numberOfBytesUtf8(key);
        final int capacity = calculateInitialCommandBufferSize(len, RedisProtocolSupport.Command.TTL) +
                    calculateRequestArgumentSize(keyBytes);
        Buffer buffer = reservedCnx.executionContext().bufferAllocator().newBuffer(capacity);
        writeRequestArraySize(buffer, len);
        RedisProtocolSupport.Command.TTL.encodeTo(buffer);
        writeRequestArgument(buffer, key, keyBytes);
        final RedisRequest request = newRequest(RedisProtocolSupport.Command.TTL, buffer);
        final Single<String> queued = reservedCnx.request(request, String.class);
        Future<Long> result = enqueueForExecute(state, singles, queued);
        return result;
    }

    @Override
    public Future<String> type(@RedisProtocolSupport.Key final CharSequence key) {
        requireNonNull(key);
        final int len = 2;
        final int keyBytes = numberOfBytesUtf8(key);
        final int capacity = calculateInitialCommandBufferSize(len, RedisProtocolSupport.Command.TYPE) +
                    calculateRequestArgumentSize(keyBytes);
        Buffer buffer = reservedCnx.executionContext().bufferAllocator().newBuffer(capacity);
        writeRequestArraySize(buffer, len);
        RedisProtocolSupport.Command.TYPE.encodeTo(buffer);
        writeRequestArgument(buffer, key, keyBytes);
        final RedisRequest request = newRequest(RedisProtocolSupport.Command.TYPE, buffer);
        final Single<String> queued = reservedCnx.request(request, String.class);
        Future<String> result = enqueueForExecute(state, singles, queued);
        return result;
    }

    @Override
    public Future<Long> unlink(@RedisProtocolSupport.Key final CharSequence key) {
        requireNonNull(key);
        final int len = 2;
        final int keyBytes = numberOfBytesUtf8(key);
        final int capacity = calculateInitialCommandBufferSize(len, RedisProtocolSupport.Command.UNLINK) +
                    calculateRequestArgumentSize(keyBytes);
        Buffer buffer = reservedCnx.executionContext().bufferAllocator().newBuffer(capacity);
        writeRequestArraySize(buffer, len);
        RedisProtocolSupport.Command.UNLINK.encodeTo(buffer);
        writeRequestArgument(buffer, key, keyBytes);
        final RedisRequest request = newRequest(RedisProtocolSupport.Command.UNLINK, buffer);
        final Single<String> queued = reservedCnx.request(request, String.class);
        Future<Long> result = enqueueForExecute(state, singles, queued);
        return result;
    }

    @Override
    public Future<Long> unlink(@RedisProtocolSupport.Key final CharSequence key1,
                               @RedisProtocolSupport.Key final CharSequence key2) {
        requireNonNull(key1);
        requireNonNull(key2);
        final int len = 3;
        final int key1Bytes = numberOfBytesUtf8(key1);
        final int key2Bytes = numberOfBytesUtf8(key2);
        final int capacity = calculateInitialCommandBufferSize(len, RedisProtocolSupport.Command.UNLINK) +
                    calculateRequestArgumentSize(key1Bytes) + calculateRequestArgumentSize(key2Bytes);
        Buffer buffer = reservedCnx.executionContext().bufferAllocator().newBuffer(capacity);
        writeRequestArraySize(buffer, len);
        RedisProtocolSupport.Command.UNLINK.encodeTo(buffer);
        writeRequestArgument(buffer, key1, key1Bytes);
        writeRequestArgument(buffer, key2, key2Bytes);
        final RedisRequest request = newRequest(RedisProtocolSupport.Command.UNLINK, buffer);
        final Single<String> queued = reservedCnx.request(request, String.class);
        Future<Long> result = enqueueForExecute(state, singles, queued);
        return result;
    }

    @Override
    public Future<Long> unlink(@RedisProtocolSupport.Key final CharSequence key1,
                               @RedisProtocolSupport.Key final CharSequence key2,
                               @RedisProtocolSupport.Key final CharSequence key3) {
        requireNonNull(key1);
        requireNonNull(key2);
        requireNonNull(key3);
        final int len = 4;
        final int key1Bytes = numberOfBytesUtf8(key1);
        final int key2Bytes = numberOfBytesUtf8(key2);
        final int key3Bytes = numberOfBytesUtf8(key3);
        final int capacity = calculateInitialCommandBufferSize(len, RedisProtocolSupport.Command.UNLINK) +
                    calculateRequestArgumentSize(key1Bytes) + calculateRequestArgumentSize(key2Bytes) +
                    calculateRequestArgumentSize(key3Bytes);
        Buffer buffer = reservedCnx.executionContext().bufferAllocator().newBuffer(capacity);
        writeRequestArraySize(buffer, len);
        RedisProtocolSupport.Command.UNLINK.encodeTo(buffer);
        writeRequestArgument(buffer, key1, key1Bytes);
        writeRequestArgument(buffer, key2, key2Bytes);
        writeRequestArgument(buffer, key3, key3Bytes);
        final RedisRequest request = newRequest(RedisProtocolSupport.Command.UNLINK, buffer);
        final Single<String> queued = reservedCnx.request(request, String.class);
        Future<Long> result = enqueueForExecute(state, singles, queued);
        return result;
    }

    @Override
    public Future<Long> unlink(@RedisProtocolSupport.Key final Collection<? extends CharSequence> keys) {
        requireNonNull(keys);
        final int len = 1 + keys.size();
        int keysCapacity = 0;
        if (keys instanceof List && keys instanceof RandomAccess) {
            final List<CharSequence> list = (List<CharSequence>) keys;
            for (int i = 0; i < list.size(); ++i) {
                final CharSequence arg = list.get(i);
                keysCapacity += estimateRequestArgumentSize(arg);
            }
        } else {
            for (CharSequence arg : keys) {
                keysCapacity += estimateRequestArgumentSize(arg);
            }
        }
        final int capacity = calculateInitialCommandBufferSize(len, RedisProtocolSupport.Command.UNLINK) + keysCapacity;
        Buffer buffer = reservedCnx.executionContext().bufferAllocator().newBuffer(capacity);
        writeRequestArraySize(buffer, len);
        RedisProtocolSupport.Command.UNLINK.encodeTo(buffer);
        if (keys instanceof List && keys instanceof RandomAccess) {
            final List<CharSequence> list = (List<CharSequence>) keys;
            for (int i = 0; i < list.size(); ++i) {
                final CharSequence arg = list.get(i);
                writeRequestArgument(buffer, arg);
            }
        } else {
            for (CharSequence arg : keys) {
                writeRequestArgument(buffer, arg);
            }
        }
        final RedisRequest request = newRequest(RedisProtocolSupport.Command.UNLINK, buffer);
        final Single<String> queued = reservedCnx.request(request, String.class);
        Future<Long> result = enqueueForExecute(state, singles, queued);
        return result;
    }

    @Override
    public Future<String> unwatch() {
        final int len = 1;
        final int capacity = calculateInitialCommandBufferSize(len, RedisProtocolSupport.Command.UNWATCH);
        Buffer buffer = reservedCnx.executionContext().bufferAllocator().newBuffer(capacity);
        writeRequestArraySize(buffer, len);
        RedisProtocolSupport.Command.UNWATCH.encodeTo(buffer);
        final RedisRequest request = newRequest(RedisProtocolSupport.Command.UNWATCH, buffer);
        final Single<String> queued = reservedCnx.request(request, String.class);
        Future<String> result = enqueueForExecute(state, singles, queued);
        return result;
    }

    @Override
    public Future<Long> wait(final long numslaves, final long timeout) {
        final int len = 3;
        final int capacity = calculateInitialCommandBufferSize(len, RedisProtocolSupport.Command.WAIT) +
                    calculateRequestArgumentSize(numslaves) + calculateRequestArgumentSize(timeout);
        Buffer buffer = reservedCnx.executionContext().bufferAllocator().newBuffer(capacity);
        writeRequestArraySize(buffer, len);
        RedisProtocolSupport.Command.WAIT.encodeTo(buffer);
        writeRequestArgument(buffer, numslaves);
        writeRequestArgument(buffer, timeout);
        final RedisRequest request = newRequest(RedisProtocolSupport.Command.WAIT, buffer);
        final Single<String> queued = reservedCnx.request(request, String.class);
        Future<Long> result = enqueueForExecute(state, singles, queued);
        return result;
    }

    @Override
    public Future<String> watch(@RedisProtocolSupport.Key final CharSequence key) {
        requireNonNull(key);
        final int len = 2;
        final int keyBytes = numberOfBytesUtf8(key);
        final int capacity = calculateInitialCommandBufferSize(len, RedisProtocolSupport.Command.WATCH) +
                    calculateRequestArgumentSize(keyBytes);
        Buffer buffer = reservedCnx.executionContext().bufferAllocator().newBuffer(capacity);
        writeRequestArraySize(buffer, len);
        RedisProtocolSupport.Command.WATCH.encodeTo(buffer);
        writeRequestArgument(buffer, key, keyBytes);
        final RedisRequest request = newRequest(RedisProtocolSupport.Command.WATCH, buffer);
        final Single<String> queued = reservedCnx.request(request, String.class);
        Future<String> result = enqueueForExecute(state, singles, queued);
        return result;
    }

    @Override
    public Future<String> watch(@RedisProtocolSupport.Key final CharSequence key1,
                                @RedisProtocolSupport.Key final CharSequence key2) {
        requireNonNull(key1);
        requireNonNull(key2);
        final int len = 3;
        final int key1Bytes = numberOfBytesUtf8(key1);
        final int key2Bytes = numberOfBytesUtf8(key2);
        final int capacity = calculateInitialCommandBufferSize(len, RedisProtocolSupport.Command.WATCH) +
                    calculateRequestArgumentSize(key1Bytes) + calculateRequestArgumentSize(key2Bytes);
        Buffer buffer = reservedCnx.executionContext().bufferAllocator().newBuffer(capacity);
        writeRequestArraySize(buffer, len);
        RedisProtocolSupport.Command.WATCH.encodeTo(buffer);
        writeRequestArgument(buffer, key1, key1Bytes);
        writeRequestArgument(buffer, key2, key2Bytes);
        final RedisRequest request = newRequest(RedisProtocolSupport.Command.WATCH, buffer);
        final Single<String> queued = reservedCnx.request(request, String.class);
        Future<String> result = enqueueForExecute(state, singles, queued);
        return result;
    }

    @Override
    public Future<String> watch(@RedisProtocolSupport.Key final CharSequence key1,
                                @RedisProtocolSupport.Key final CharSequence key2,
                                @RedisProtocolSupport.Key final CharSequence key3) {
        requireNonNull(key1);
        requireNonNull(key2);
        requireNonNull(key3);
        final int len = 4;
        final int key1Bytes = numberOfBytesUtf8(key1);
        final int key2Bytes = numberOfBytesUtf8(key2);
        final int key3Bytes = numberOfBytesUtf8(key3);
        final int capacity = calculateInitialCommandBufferSize(len, RedisProtocolSupport.Command.WATCH) +
                    calculateRequestArgumentSize(key1Bytes) + calculateRequestArgumentSize(key2Bytes) +
                    calculateRequestArgumentSize(key3Bytes);
        Buffer buffer = reservedCnx.executionContext().bufferAllocator().newBuffer(capacity);
        writeRequestArraySize(buffer, len);
        RedisProtocolSupport.Command.WATCH.encodeTo(buffer);
        writeRequestArgument(buffer, key1, key1Bytes);
        writeRequestArgument(buffer, key2, key2Bytes);
        writeRequestArgument(buffer, key3, key3Bytes);
        final RedisRequest request = newRequest(RedisProtocolSupport.Command.WATCH, buffer);
        final Single<String> queued = reservedCnx.request(request, String.class);
        Future<String> result = enqueueForExecute(state, singles, queued);
        return result;
    }

    @Override
    public Future<String> watch(@RedisProtocolSupport.Key final Collection<? extends CharSequence> keys) {
        requireNonNull(keys);
        final int len = 1 + keys.size();
        int keysCapacity = 0;
        if (keys instanceof List && keys instanceof RandomAccess) {
            final List<CharSequence> list = (List<CharSequence>) keys;
            for (int i = 0; i < list.size(); ++i) {
                final CharSequence arg = list.get(i);
                keysCapacity += estimateRequestArgumentSize(arg);
            }
        } else {
            for (CharSequence arg : keys) {
                keysCapacity += estimateRequestArgumentSize(arg);
            }
        }
        final int capacity = calculateInitialCommandBufferSize(len, RedisProtocolSupport.Command.WATCH) + keysCapacity;
        Buffer buffer = reservedCnx.executionContext().bufferAllocator().newBuffer(capacity);
        writeRequestArraySize(buffer, len);
        RedisProtocolSupport.Command.WATCH.encodeTo(buffer);
        if (keys instanceof List && keys instanceof RandomAccess) {
            final List<CharSequence> list = (List<CharSequence>) keys;
            for (int i = 0; i < list.size(); ++i) {
                final CharSequence arg = list.get(i);
                writeRequestArgument(buffer, arg);
            }
        } else {
            for (CharSequence arg : keys) {
                writeRequestArgument(buffer, arg);
            }
        }
        final RedisRequest request = newRequest(RedisProtocolSupport.Command.WATCH, buffer);
        final Single<String> queued = reservedCnx.request(request, String.class);
        Future<String> result = enqueueForExecute(state, singles, queued);
        return result;
    }

    @Override
    public Future<String> xadd(@RedisProtocolSupport.Key final CharSequence key, final CharSequence id,
                               final CharSequence field, final CharSequence value) {
        requireNonNull(key);
        requireNonNull(id);
        requireNonNull(field);
        requireNonNull(value);
        final int len = 5;
        final int keyBytes = numberOfBytesUtf8(key);
        final int idBytes = numberOfBytesUtf8(id);
        final int fieldBytes = numberOfBytesUtf8(field);
        final int valueBytes = numberOfBytesUtf8(value);
        final int capacity = calculateInitialCommandBufferSize(len, RedisProtocolSupport.Command.XADD) +
                    calculateRequestArgumentSize(keyBytes) + calculateRequestArgumentSize(idBytes) +
                    calculateRequestArgumentSize(fieldBytes) + calculateRequestArgumentSize(valueBytes);
        Buffer buffer = reservedCnx.executionContext().bufferAllocator().newBuffer(capacity);
        writeRequestArraySize(buffer, len);
        RedisProtocolSupport.Command.XADD.encodeTo(buffer);
        writeRequestArgument(buffer, key, keyBytes);
        writeRequestArgument(buffer, id, idBytes);
        writeRequestArgument(buffer, field, fieldBytes);
        writeRequestArgument(buffer, value, valueBytes);
        final RedisRequest request = newRequest(RedisProtocolSupport.Command.XADD, buffer);
        final Single<String> queued = reservedCnx.request(request, String.class);
        Future<String> result = enqueueForExecute(state, singles, queued);
        return result;
    }

    @Override
    public Future<String> xadd(@RedisProtocolSupport.Key final CharSequence key, final CharSequence id,
                               final CharSequence field1, final CharSequence value1, final CharSequence field2,
                               final CharSequence value2) {
        requireNonNull(key);
        requireNonNull(id);
        requireNonNull(field1);
        requireNonNull(value1);
        requireNonNull(field2);
        requireNonNull(value2);
        final int len = 7;
        final int keyBytes = numberOfBytesUtf8(key);
        final int idBytes = numberOfBytesUtf8(id);
        final int field1Bytes = numberOfBytesUtf8(field1);
        final int value1Bytes = numberOfBytesUtf8(value1);
        final int field2Bytes = numberOfBytesUtf8(field2);
        final int value2Bytes = numberOfBytesUtf8(value2);
        final int capacity = calculateInitialCommandBufferSize(len, RedisProtocolSupport.Command.XADD) +
                    calculateRequestArgumentSize(keyBytes) + calculateRequestArgumentSize(idBytes) +
                    calculateRequestArgumentSize(field1Bytes) + calculateRequestArgumentSize(value1Bytes) +
                    calculateRequestArgumentSize(field2Bytes) + calculateRequestArgumentSize(value2Bytes);
        Buffer buffer = reservedCnx.executionContext().bufferAllocator().newBuffer(capacity);
        writeRequestArraySize(buffer, len);
        RedisProtocolSupport.Command.XADD.encodeTo(buffer);
        writeRequestArgument(buffer, key, keyBytes);
        writeRequestArgument(buffer, id, idBytes);
        writeRequestArgument(buffer, field1, field1Bytes);
        writeRequestArgument(buffer, value1, value1Bytes);
        writeRequestArgument(buffer, field2, field2Bytes);
        writeRequestArgument(buffer, value2, value2Bytes);
        final RedisRequest request = newRequest(RedisProtocolSupport.Command.XADD, buffer);
        final Single<String> queued = reservedCnx.request(request, String.class);
        Future<String> result = enqueueForExecute(state, singles, queued);
        return result;
    }

    @Override
    public Future<String> xadd(@RedisProtocolSupport.Key final CharSequence key, final CharSequence id,
                               final CharSequence field1, final CharSequence value1, final CharSequence field2,
                               final CharSequence value2, final CharSequence field3, final CharSequence value3) {
        requireNonNull(key);
        requireNonNull(id);
        requireNonNull(field1);
        requireNonNull(value1);
        requireNonNull(field2);
        requireNonNull(value2);
        requireNonNull(field3);
        requireNonNull(value3);
        final int len = 9;
        final int keyBytes = numberOfBytesUtf8(key);
        final int idBytes = numberOfBytesUtf8(id);
        final int field1Bytes = numberOfBytesUtf8(field1);
        final int value1Bytes = numberOfBytesUtf8(value1);
        final int field2Bytes = numberOfBytesUtf8(field2);
        final int value2Bytes = numberOfBytesUtf8(value2);
        final int field3Bytes = numberOfBytesUtf8(field3);
        final int value3Bytes = numberOfBytesUtf8(value3);
        final int capacity = calculateInitialCommandBufferSize(len, RedisProtocolSupport.Command.XADD) +
                    calculateRequestArgumentSize(keyBytes) + calculateRequestArgumentSize(idBytes) +
                    calculateRequestArgumentSize(field1Bytes) + calculateRequestArgumentSize(value1Bytes) +
                    calculateRequestArgumentSize(field2Bytes) + calculateRequestArgumentSize(value2Bytes) +
                    calculateRequestArgumentSize(field3Bytes) + calculateRequestArgumentSize(value3Bytes);
        Buffer buffer = reservedCnx.executionContext().bufferAllocator().newBuffer(capacity);
        writeRequestArraySize(buffer, len);
        RedisProtocolSupport.Command.XADD.encodeTo(buffer);
        writeRequestArgument(buffer, key, keyBytes);
        writeRequestArgument(buffer, id, idBytes);
        writeRequestArgument(buffer, field1, field1Bytes);
        writeRequestArgument(buffer, value1, value1Bytes);
        writeRequestArgument(buffer, field2, field2Bytes);
        writeRequestArgument(buffer, value2, value2Bytes);
        writeRequestArgument(buffer, field3, field3Bytes);
        writeRequestArgument(buffer, value3, value3Bytes);
        final RedisRequest request = newRequest(RedisProtocolSupport.Command.XADD, buffer);
        final Single<String> queued = reservedCnx.request(request, String.class);
        Future<String> result = enqueueForExecute(state, singles, queued);
        return result;
    }

    @Override
    public Future<String> xadd(@RedisProtocolSupport.Key final CharSequence key, final CharSequence id,
                               final Collection<RedisProtocolSupport.FieldValue> fieldValues) {
        requireNonNull(key);
        requireNonNull(id);
        requireNonNull(fieldValues);
        final int len = 3 + RedisProtocolSupport.FieldValue.SIZE * fieldValues.size();
        final int keyBytes = numberOfBytesUtf8(key);
        final int idBytes = numberOfBytesUtf8(id);
        int fieldValuesCapacity = 0;
        if (fieldValues instanceof List && fieldValues instanceof RandomAccess) {
            final List<RedisProtocolSupport.FieldValue> list = (List<RedisProtocolSupport.FieldValue>) fieldValues;
            for (int i = 0; i < list.size(); ++i) {
                final RedisProtocolSupport.FieldValue arg = list.get(i);
                fieldValuesCapacity += arg.encodedByteCount();
            }
        } else {
            for (RedisProtocolSupport.FieldValue arg : fieldValues) {
                fieldValuesCapacity += arg.encodedByteCount();
            }
        }
        final int capacity = calculateInitialCommandBufferSize(len, RedisProtocolSupport.Command.XADD) +
                    calculateRequestArgumentSize(keyBytes) + calculateRequestArgumentSize(idBytes) +
                    fieldValuesCapacity;
        Buffer buffer = reservedCnx.executionContext().bufferAllocator().newBuffer(capacity);
        writeRequestArraySize(buffer, len);
        RedisProtocolSupport.Command.XADD.encodeTo(buffer);
        writeRequestArgument(buffer, key, keyBytes);
        writeRequestArgument(buffer, id, idBytes);
        if (fieldValues instanceof List && fieldValues instanceof RandomAccess) {
            final List<RedisProtocolSupport.FieldValue> list = (List<RedisProtocolSupport.FieldValue>) fieldValues;
            for (int i = 0; i < list.size(); ++i) {
                final RedisProtocolSupport.FieldValue arg = list.get(i);
                arg.encodeTo(buffer);
            }
        } else {
            for (RedisProtocolSupport.FieldValue arg : fieldValues) {
                arg.encodeTo(buffer);
            }
        }
        final RedisRequest request = newRequest(RedisProtocolSupport.Command.XADD, buffer);
        final Single<String> queued = reservedCnx.request(request, String.class);
        Future<String> result = enqueueForExecute(state, singles, queued);
        return result;
    }

    @Override
    public Future<Long> xlen(@RedisProtocolSupport.Key final CharSequence key) {
        requireNonNull(key);
        final int len = 2;
        final int keyBytes = numberOfBytesUtf8(key);
        final int capacity = calculateInitialCommandBufferSize(len, RedisProtocolSupport.Command.XLEN) +
                    calculateRequestArgumentSize(keyBytes);
        Buffer buffer = reservedCnx.executionContext().bufferAllocator().newBuffer(capacity);
        writeRequestArraySize(buffer, len);
        RedisProtocolSupport.Command.XLEN.encodeTo(buffer);
        writeRequestArgument(buffer, key, keyBytes);
        final RedisRequest request = newRequest(RedisProtocolSupport.Command.XLEN, buffer);
        final Single<String> queued = reservedCnx.request(request, String.class);
        Future<Long> result = enqueueForExecute(state, singles, queued);
        return result;
    }

    @Override
    public <T> Future<List<T>> xpending(@RedisProtocolSupport.Key final CharSequence key, final CharSequence group) {
        requireNonNull(key);
        requireNonNull(group);
        final int len = 3;
        final int keyBytes = numberOfBytesUtf8(key);
        final int groupBytes = numberOfBytesUtf8(group);
        final int capacity = calculateInitialCommandBufferSize(len, RedisProtocolSupport.Command.XPENDING) +
                    calculateRequestArgumentSize(keyBytes) + calculateRequestArgumentSize(groupBytes);
        Buffer buffer = reservedCnx.executionContext().bufferAllocator().newBuffer(capacity);
        writeRequestArraySize(buffer, len);
        RedisProtocolSupport.Command.XPENDING.encodeTo(buffer);
        writeRequestArgument(buffer, key, keyBytes);
        writeRequestArgument(buffer, group, groupBytes);
        final RedisRequest request = newRequest(RedisProtocolSupport.Command.XPENDING, buffer);
        final Single<String> queued = reservedCnx.request(request, String.class);
        Future<List<T>> result = enqueueForExecute(state, singles, queued);
        return result;
    }

    @Override
    public <T> Future<List<T>> xpending(@RedisProtocolSupport.Key final CharSequence key, final CharSequence group,
                                        @Nullable final CharSequence start, @Nullable final CharSequence end,
                                        @Nullable final Long count, @Nullable final CharSequence consumer) {
        requireNonNull(key);
        requireNonNull(group);
        final int len = 3 + (start == null ? 0 : 1) + (end == null ? 0 : 1) + (count == null ? 0 : 1) +
                    (consumer == null ? 0 : 1);
        final int keyBytes = numberOfBytesUtf8(key);
        final int groupBytes = numberOfBytesUtf8(group);
        final int startBytes = start == null ? 0 : numberOfBytesUtf8(start);
        final int endBytes = end == null ? 0 : numberOfBytesUtf8(end);
        final int consumerBytes = consumer == null ? 0 : numberOfBytesUtf8(consumer);
        final int capacity = calculateInitialCommandBufferSize(len, RedisProtocolSupport.Command.XPENDING) +
                    calculateRequestArgumentSize(keyBytes) + calculateRequestArgumentSize(groupBytes) +
                    (start == null ? 0 : calculateRequestArgumentSize(startBytes)) +
                    (end == null ? 0 : calculateRequestArgumentSize(endBytes)) +
                    (count == null ? 0 : calculateRequestArgumentSize(count)) +
                    (consumer == null ? 0 : calculateRequestArgumentSize(consumerBytes));
        Buffer buffer = reservedCnx.executionContext().bufferAllocator().newBuffer(capacity);
        writeRequestArraySize(buffer, len);
        RedisProtocolSupport.Command.XPENDING.encodeTo(buffer);
        writeRequestArgument(buffer, key, keyBytes);
        writeRequestArgument(buffer, group, groupBytes);
        if (start != null) {
            writeRequestArgument(buffer, start, startBytes);
        }
        if (end != null) {
            writeRequestArgument(buffer, end, endBytes);
        }
        if (count != null) {
            writeRequestArgument(buffer, count);
        }
        if (consumer != null) {
            writeRequestArgument(buffer, consumer, consumerBytes);
        }
        final RedisRequest request = newRequest(RedisProtocolSupport.Command.XPENDING, buffer);
        final Single<String> queued = reservedCnx.request(request, String.class);
        Future<List<T>> result = enqueueForExecute(state, singles, queued);
        return result;
    }

    @Override
    public <T> Future<List<T>> xrange(@RedisProtocolSupport.Key final CharSequence key, final CharSequence start,
                                      final CharSequence end) {
        requireNonNull(key);
        requireNonNull(start);
        requireNonNull(end);
        final int len = 4;
        final int keyBytes = numberOfBytesUtf8(key);
        final int startBytes = numberOfBytesUtf8(start);
        final int endBytes = numberOfBytesUtf8(end);
        final int capacity = calculateInitialCommandBufferSize(len, RedisProtocolSupport.Command.XRANGE) +
                    calculateRequestArgumentSize(keyBytes) + calculateRequestArgumentSize(startBytes) +
                    calculateRequestArgumentSize(endBytes);
        Buffer buffer = reservedCnx.executionContext().bufferAllocator().newBuffer(capacity);
        writeRequestArraySize(buffer, len);
        RedisProtocolSupport.Command.XRANGE.encodeTo(buffer);
        writeRequestArgument(buffer, key, keyBytes);
        writeRequestArgument(buffer, start, startBytes);
        writeRequestArgument(buffer, end, endBytes);
        final RedisRequest request = newRequest(RedisProtocolSupport.Command.XRANGE, buffer);
        final Single<String> queued = reservedCnx.request(request, String.class);
        Future<List<T>> result = enqueueForExecute(state, singles, queued);
        return result;
    }

    @Override
    public <T> Future<List<T>> xrange(@RedisProtocolSupport.Key final CharSequence key, final CharSequence start,
                                      final CharSequence end, @Nullable final Long count) {
        requireNonNull(key);
        requireNonNull(start);
        requireNonNull(end);
        final int len = 4 + (count == null ? 0 : 2);
        final int keyBytes = numberOfBytesUtf8(key);
        final int startBytes = numberOfBytesUtf8(start);
        final int endBytes = numberOfBytesUtf8(end);
        final int capacity = calculateInitialCommandBufferSize(len, RedisProtocolSupport.Command.XRANGE) +
                    calculateRequestArgumentSize(keyBytes) + calculateRequestArgumentSize(startBytes) +
                    calculateRequestArgumentSize(endBytes) +
                    (count == null ? 0 : RedisProtocolSupport.SubCommand.COUNT.encodedByteCount()) +
                    (count == null ? 0 : calculateRequestArgumentSize(count));
        Buffer buffer = reservedCnx.executionContext().bufferAllocator().newBuffer(capacity);
        writeRequestArraySize(buffer, len);
        RedisProtocolSupport.Command.XRANGE.encodeTo(buffer);
        writeRequestArgument(buffer, key, keyBytes);
        writeRequestArgument(buffer, start, startBytes);
        writeRequestArgument(buffer, end, endBytes);
        if (count != null) {
            RedisProtocolSupport.SubCommand.COUNT.encodeTo(buffer);
            writeRequestArgument(buffer, count);
        }
        final RedisRequest request = newRequest(RedisProtocolSupport.Command.XRANGE, buffer);
        final Single<String> queued = reservedCnx.request(request, String.class);
        Future<List<T>> result = enqueueForExecute(state, singles, queued);
        return result;
    }

    @Override
    public <T> Future<List<T>> xread(@RedisProtocolSupport.Key final Collection<? extends CharSequence> keys,
                                     final Collection<? extends CharSequence> ids) {
        requireNonNull(keys);
        requireNonNull(ids);
        final int len = 2 + keys.size() + ids.size();
        int keysCapacity = 0;
        if (keys instanceof List && keys instanceof RandomAccess) {
            final List<CharSequence> list = (List<CharSequence>) keys;
            for (int i = 0; i < list.size(); ++i) {
                final CharSequence arg = list.get(i);
                keysCapacity += estimateRequestArgumentSize(arg);
            }
        } else {
            for (CharSequence arg : keys) {
                keysCapacity += estimateRequestArgumentSize(arg);
            }
        }
        int idsCapacity = 0;
        if (ids instanceof List && ids instanceof RandomAccess) {
            final List<CharSequence> list = (List<CharSequence>) ids;
            for (int i = 0; i < list.size(); ++i) {
                final CharSequence arg = list.get(i);
                idsCapacity += estimateRequestArgumentSize(arg);
            }
        } else {
            for (CharSequence arg : ids) {
                idsCapacity += estimateRequestArgumentSize(arg);
            }
        }
        final int capacity = calculateInitialCommandBufferSize(len, RedisProtocolSupport.Command.XREAD) +
                    RedisProtocolSupport.XreadStreams.values()[0].encodedByteCount() + keysCapacity + idsCapacity;
        Buffer buffer = reservedCnx.executionContext().bufferAllocator().newBuffer(capacity);
        writeRequestArraySize(buffer, len);
        RedisProtocolSupport.Command.XREAD.encodeTo(buffer);
        RedisProtocolSupport.XreadStreams.values()[0].encodeTo(buffer);
        if (keys instanceof List && keys instanceof RandomAccess) {
            final List<CharSequence> list = (List<CharSequence>) keys;
            for (int i = 0; i < list.size(); ++i) {
                final CharSequence arg = list.get(i);
                writeRequestArgument(buffer, arg);
            }
        } else {
            for (CharSequence arg : keys) {
                writeRequestArgument(buffer, arg);
            }
        }
        if (ids instanceof List && ids instanceof RandomAccess) {
            final List<CharSequence> list = (List<CharSequence>) ids;
            for (int i = 0; i < list.size(); ++i) {
                final CharSequence arg = list.get(i);
                writeRequestArgument(buffer, arg);
            }
        } else {
            for (CharSequence arg : ids) {
                writeRequestArgument(buffer, arg);
            }
        }
        final RedisRequest request = newRequest(RedisProtocolSupport.Command.XREAD, buffer);
        final Single<String> queued = reservedCnx.request(request, String.class);
        Future<List<T>> result = enqueueForExecute(state, singles, queued);
        return result;
    }

    @Override
    public <T> Future<List<T>> xread(@Nullable final Long count, @Nullable final Long blockMilliseconds,
                                     @RedisProtocolSupport.Key final Collection<? extends CharSequence> keys,
                                     final Collection<? extends CharSequence> ids) {
        requireNonNull(keys);
        requireNonNull(ids);
        final int len = 2 + (count == null ? 0 : 2) + (blockMilliseconds == null ? 0 : 2) + keys.size() + ids.size();
        int keysCapacity = 0;
        if (keys instanceof List && keys instanceof RandomAccess) {
            final List<CharSequence> list = (List<CharSequence>) keys;
            for (int i = 0; i < list.size(); ++i) {
                final CharSequence arg = list.get(i);
                keysCapacity += estimateRequestArgumentSize(arg);
            }
        } else {
            for (CharSequence arg : keys) {
                keysCapacity += estimateRequestArgumentSize(arg);
            }
        }
        int idsCapacity = 0;
        if (ids instanceof List && ids instanceof RandomAccess) {
            final List<CharSequence> list = (List<CharSequence>) ids;
            for (int i = 0; i < list.size(); ++i) {
                final CharSequence arg = list.get(i);
                idsCapacity += estimateRequestArgumentSize(arg);
            }
        } else {
            for (CharSequence arg : ids) {
                idsCapacity += estimateRequestArgumentSize(arg);
            }
        }
        final int capacity = calculateInitialCommandBufferSize(len, RedisProtocolSupport.Command.XREAD) +
                    (count == null ? 0 : RedisProtocolSupport.SubCommand.COUNT.encodedByteCount()) +
                    (count == null ? 0 : calculateRequestArgumentSize(count)) +
                    (blockMilliseconds == null ? 0 : RedisProtocolSupport.SubCommand.BLOCK.encodedByteCount()) +
                    (blockMilliseconds == null ? 0 : calculateRequestArgumentSize(blockMilliseconds)) +
                    RedisProtocolSupport.XreadStreams.values()[0].encodedByteCount() + keysCapacity + idsCapacity;
        Buffer buffer = reservedCnx.executionContext().bufferAllocator().newBuffer(capacity);
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
            final List<CharSequence> list = (List<CharSequence>) keys;
            for (int i = 0; i < list.size(); ++i) {
                final CharSequence arg = list.get(i);
                writeRequestArgument(buffer, arg);
            }
        } else {
            for (CharSequence arg : keys) {
                writeRequestArgument(buffer, arg);
            }
        }
        if (ids instanceof List && ids instanceof RandomAccess) {
            final List<CharSequence> list = (List<CharSequence>) ids;
            for (int i = 0; i < list.size(); ++i) {
                final CharSequence arg = list.get(i);
                writeRequestArgument(buffer, arg);
            }
        } else {
            for (CharSequence arg : ids) {
                writeRequestArgument(buffer, arg);
            }
        }
        final RedisRequest request = newRequest(RedisProtocolSupport.Command.XREAD, buffer);
        final Single<String> queued = reservedCnx.request(request, String.class);
        Future<List<T>> result = enqueueForExecute(state, singles, queued);
        return result;
    }

    @Override
    public <T> Future<List<T>> xreadgroup(final RedisProtocolSupport.GroupConsumer groupConsumer,
                                          @RedisProtocolSupport.Key final Collection<? extends CharSequence> keys,
                                          final Collection<? extends CharSequence> ids) {
        requireNonNull(groupConsumer);
        requireNonNull(keys);
        requireNonNull(ids);
        final int len = 2 + RedisProtocolSupport.GroupConsumer.SIZE + keys.size() + ids.size();
        int keysCapacity = 0;
        if (keys instanceof List && keys instanceof RandomAccess) {
            final List<CharSequence> list = (List<CharSequence>) keys;
            for (int i = 0; i < list.size(); ++i) {
                final CharSequence arg = list.get(i);
                keysCapacity += estimateRequestArgumentSize(arg);
            }
        } else {
            for (CharSequence arg : keys) {
                keysCapacity += estimateRequestArgumentSize(arg);
            }
        }
        int idsCapacity = 0;
        if (ids instanceof List && ids instanceof RandomAccess) {
            final List<CharSequence> list = (List<CharSequence>) ids;
            for (int i = 0; i < list.size(); ++i) {
                final CharSequence arg = list.get(i);
                idsCapacity += estimateRequestArgumentSize(arg);
            }
        } else {
            for (CharSequence arg : ids) {
                idsCapacity += estimateRequestArgumentSize(arg);
            }
        }
        final int capacity = calculateInitialCommandBufferSize(len, RedisProtocolSupport.Command.XREADGROUP) +
                    groupConsumer.encodedByteCount() +
                    RedisProtocolSupport.XreadgroupStreams.values()[0].encodedByteCount() + keysCapacity + idsCapacity;
        Buffer buffer = reservedCnx.executionContext().bufferAllocator().newBuffer(capacity);
        writeRequestArraySize(buffer, len);
        RedisProtocolSupport.Command.XREADGROUP.encodeTo(buffer);
        groupConsumer.encodeTo(buffer);
        RedisProtocolSupport.XreadgroupStreams.values()[0].encodeTo(buffer);
        if (keys instanceof List && keys instanceof RandomAccess) {
            final List<CharSequence> list = (List<CharSequence>) keys;
            for (int i = 0; i < list.size(); ++i) {
                final CharSequence arg = list.get(i);
                writeRequestArgument(buffer, arg);
            }
        } else {
            for (CharSequence arg : keys) {
                writeRequestArgument(buffer, arg);
            }
        }
        if (ids instanceof List && ids instanceof RandomAccess) {
            final List<CharSequence> list = (List<CharSequence>) ids;
            for (int i = 0; i < list.size(); ++i) {
                final CharSequence arg = list.get(i);
                writeRequestArgument(buffer, arg);
            }
        } else {
            for (CharSequence arg : ids) {
                writeRequestArgument(buffer, arg);
            }
        }
        final RedisRequest request = newRequest(RedisProtocolSupport.Command.XREADGROUP, buffer);
        final Single<String> queued = reservedCnx.request(request, String.class);
        Future<List<T>> result = enqueueForExecute(state, singles, queued);
        return result;
    }

    @Override
    public <T> Future<List<T>> xreadgroup(final RedisProtocolSupport.GroupConsumer groupConsumer,
                                          @Nullable final Long count, @Nullable final Long blockMilliseconds,
                                          @RedisProtocolSupport.Key final Collection<? extends CharSequence> keys,
                                          final Collection<? extends CharSequence> ids) {
        requireNonNull(groupConsumer);
        requireNonNull(keys);
        requireNonNull(ids);
        final int len = 2 + RedisProtocolSupport.GroupConsumer.SIZE + (count == null ? 0 : 2) +
                    (blockMilliseconds == null ? 0 : 2) + keys.size() + ids.size();
        int keysCapacity = 0;
        if (keys instanceof List && keys instanceof RandomAccess) {
            final List<CharSequence> list = (List<CharSequence>) keys;
            for (int i = 0; i < list.size(); ++i) {
                final CharSequence arg = list.get(i);
                keysCapacity += estimateRequestArgumentSize(arg);
            }
        } else {
            for (CharSequence arg : keys) {
                keysCapacity += estimateRequestArgumentSize(arg);
            }
        }
        int idsCapacity = 0;
        if (ids instanceof List && ids instanceof RandomAccess) {
            final List<CharSequence> list = (List<CharSequence>) ids;
            for (int i = 0; i < list.size(); ++i) {
                final CharSequence arg = list.get(i);
                idsCapacity += estimateRequestArgumentSize(arg);
            }
        } else {
            for (CharSequence arg : ids) {
                idsCapacity += estimateRequestArgumentSize(arg);
            }
        }
        final int capacity = calculateInitialCommandBufferSize(len, RedisProtocolSupport.Command.XREADGROUP) +
                    groupConsumer.encodedByteCount() +
                    (count == null ? 0 : RedisProtocolSupport.SubCommand.COUNT.encodedByteCount()) +
                    (count == null ? 0 : calculateRequestArgumentSize(count)) +
                    (blockMilliseconds == null ? 0 : RedisProtocolSupport.SubCommand.BLOCK.encodedByteCount()) +
                    (blockMilliseconds == null ? 0 : calculateRequestArgumentSize(blockMilliseconds)) +
                    RedisProtocolSupport.XreadgroupStreams.values()[0].encodedByteCount() + keysCapacity + idsCapacity;
        Buffer buffer = reservedCnx.executionContext().bufferAllocator().newBuffer(capacity);
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
            final List<CharSequence> list = (List<CharSequence>) keys;
            for (int i = 0; i < list.size(); ++i) {
                final CharSequence arg = list.get(i);
                writeRequestArgument(buffer, arg);
            }
        } else {
            for (CharSequence arg : keys) {
                writeRequestArgument(buffer, arg);
            }
        }
        if (ids instanceof List && ids instanceof RandomAccess) {
            final List<CharSequence> list = (List<CharSequence>) ids;
            for (int i = 0; i < list.size(); ++i) {
                final CharSequence arg = list.get(i);
                writeRequestArgument(buffer, arg);
            }
        } else {
            for (CharSequence arg : ids) {
                writeRequestArgument(buffer, arg);
            }
        }
        final RedisRequest request = newRequest(RedisProtocolSupport.Command.XREADGROUP, buffer);
        final Single<String> queued = reservedCnx.request(request, String.class);
        Future<List<T>> result = enqueueForExecute(state, singles, queued);
        return result;
    }

    @Override
    public <T> Future<List<T>> xrevrange(@RedisProtocolSupport.Key final CharSequence key, final CharSequence end,
                                         final CharSequence start) {
        requireNonNull(key);
        requireNonNull(end);
        requireNonNull(start);
        final int len = 4;
        final int keyBytes = numberOfBytesUtf8(key);
        final int endBytes = numberOfBytesUtf8(end);
        final int startBytes = numberOfBytesUtf8(start);
        final int capacity = calculateInitialCommandBufferSize(len, RedisProtocolSupport.Command.XREVRANGE) +
                    calculateRequestArgumentSize(keyBytes) + calculateRequestArgumentSize(endBytes) +
                    calculateRequestArgumentSize(startBytes);
        Buffer buffer = reservedCnx.executionContext().bufferAllocator().newBuffer(capacity);
        writeRequestArraySize(buffer, len);
        RedisProtocolSupport.Command.XREVRANGE.encodeTo(buffer);
        writeRequestArgument(buffer, key, keyBytes);
        writeRequestArgument(buffer, end, endBytes);
        writeRequestArgument(buffer, start, startBytes);
        final RedisRequest request = newRequest(RedisProtocolSupport.Command.XREVRANGE, buffer);
        final Single<String> queued = reservedCnx.request(request, String.class);
        Future<List<T>> result = enqueueForExecute(state, singles, queued);
        return result;
    }

    @Override
    public <T> Future<List<T>> xrevrange(@RedisProtocolSupport.Key final CharSequence key, final CharSequence end,
                                         final CharSequence start, @Nullable final Long count) {
        requireNonNull(key);
        requireNonNull(end);
        requireNonNull(start);
        final int len = 4 + (count == null ? 0 : 2);
        final int keyBytes = numberOfBytesUtf8(key);
        final int endBytes = numberOfBytesUtf8(end);
        final int startBytes = numberOfBytesUtf8(start);
        final int capacity = calculateInitialCommandBufferSize(len, RedisProtocolSupport.Command.XREVRANGE) +
                    calculateRequestArgumentSize(keyBytes) + calculateRequestArgumentSize(endBytes) +
                    calculateRequestArgumentSize(startBytes) +
                    (count == null ? 0 : RedisProtocolSupport.SubCommand.COUNT.encodedByteCount()) +
                    (count == null ? 0 : calculateRequestArgumentSize(count));
        Buffer buffer = reservedCnx.executionContext().bufferAllocator().newBuffer(capacity);
        writeRequestArraySize(buffer, len);
        RedisProtocolSupport.Command.XREVRANGE.encodeTo(buffer);
        writeRequestArgument(buffer, key, keyBytes);
        writeRequestArgument(buffer, end, endBytes);
        writeRequestArgument(buffer, start, startBytes);
        if (count != null) {
            RedisProtocolSupport.SubCommand.COUNT.encodeTo(buffer);
            writeRequestArgument(buffer, count);
        }
        final RedisRequest request = newRequest(RedisProtocolSupport.Command.XREVRANGE, buffer);
        final Single<String> queued = reservedCnx.request(request, String.class);
        Future<List<T>> result = enqueueForExecute(state, singles, queued);
        return result;
    }

    @Override
    public Future<Long> zadd(@RedisProtocolSupport.Key final CharSequence key,
                             final Collection<RedisProtocolSupport.ScoreMember> scoreMembers) {
        requireNonNull(key);
        requireNonNull(scoreMembers);
        final int len = 2 + RedisProtocolSupport.ScoreMember.SIZE * scoreMembers.size();
        final int keyBytes = numberOfBytesUtf8(key);
        int scoreMembersCapacity = 0;
        if (scoreMembers instanceof List && scoreMembers instanceof RandomAccess) {
            final List<RedisProtocolSupport.ScoreMember> list = (List<RedisProtocolSupport.ScoreMember>) scoreMembers;
            for (int i = 0; i < list.size(); ++i) {
                final RedisProtocolSupport.ScoreMember arg = list.get(i);
                scoreMembersCapacity += arg.encodedByteCount();
            }
        } else {
            for (RedisProtocolSupport.ScoreMember arg : scoreMembers) {
                scoreMembersCapacity += arg.encodedByteCount();
            }
        }
        final int capacity = calculateInitialCommandBufferSize(len, RedisProtocolSupport.Command.ZADD) +
                    calculateRequestArgumentSize(keyBytes) + scoreMembersCapacity;
        Buffer buffer = reservedCnx.executionContext().bufferAllocator().newBuffer(capacity);
        writeRequestArraySize(buffer, len);
        RedisProtocolSupport.Command.ZADD.encodeTo(buffer);
        writeRequestArgument(buffer, key, keyBytes);
        if (scoreMembers instanceof List && scoreMembers instanceof RandomAccess) {
            final List<RedisProtocolSupport.ScoreMember> list = (List<RedisProtocolSupport.ScoreMember>) scoreMembers;
            for (int i = 0; i < list.size(); ++i) {
                final RedisProtocolSupport.ScoreMember arg = list.get(i);
                arg.encodeTo(buffer);
            }
        } else {
            for (RedisProtocolSupport.ScoreMember arg : scoreMembers) {
                arg.encodeTo(buffer);
            }
        }
        final RedisRequest request = newRequest(RedisProtocolSupport.Command.ZADD, buffer);
        final Single<String> queued = reservedCnx.request(request, String.class);
        Future<Long> result = enqueueForExecute(state, singles, queued);
        return result;
    }

    @Override
    public Future<Long> zadd(@RedisProtocolSupport.Key final CharSequence key,
                             @Nullable final RedisProtocolSupport.ZaddCondition condition,
                             @Nullable final RedisProtocolSupport.ZaddChange change, final double score,
                             final CharSequence member) {
        requireNonNull(key);
        requireNonNull(member);
        final int len = 4 + (condition == null ? 0 : 1) + (change == null ? 0 : 1);
        final int keyBytes = numberOfBytesUtf8(key);
        final int memberBytes = numberOfBytesUtf8(member);
        final int capacity = calculateInitialCommandBufferSize(len, RedisProtocolSupport.Command.ZADD) +
                    calculateRequestArgumentSize(keyBytes) + (condition == null ? 0 : condition.encodedByteCount()) +
                    (change == null ? 0 : change.encodedByteCount()) + calculateRequestArgumentSize(score) +
                    calculateRequestArgumentSize(memberBytes);
        Buffer buffer = reservedCnx.executionContext().bufferAllocator().newBuffer(capacity);
        writeRequestArraySize(buffer, len);
        RedisProtocolSupport.Command.ZADD.encodeTo(buffer);
        writeRequestArgument(buffer, key, keyBytes);
        if (condition != null) {
            condition.encodeTo(buffer);
        }
        if (change != null) {
            change.encodeTo(buffer);
        }
        writeRequestArgument(buffer, score);
        writeRequestArgument(buffer, member, memberBytes);
        final RedisRequest request = newRequest(RedisProtocolSupport.Command.ZADD, buffer);
        final Single<String> queued = reservedCnx.request(request, String.class);
        Future<Long> result = enqueueForExecute(state, singles, queued);
        return result;
    }

    @Override
    public Future<Long> zadd(@RedisProtocolSupport.Key final CharSequence key,
                             @Nullable final RedisProtocolSupport.ZaddCondition condition,
                             @Nullable final RedisProtocolSupport.ZaddChange change, final double score1,
                             final CharSequence member1, final double score2, final CharSequence member2) {
        requireNonNull(key);
        requireNonNull(member1);
        requireNonNull(member2);
        final int len = 6 + (condition == null ? 0 : 1) + (change == null ? 0 : 1);
        final int keyBytes = numberOfBytesUtf8(key);
        final int member1Bytes = numberOfBytesUtf8(member1);
        final int member2Bytes = numberOfBytesUtf8(member2);
        final int capacity = calculateInitialCommandBufferSize(len, RedisProtocolSupport.Command.ZADD) +
                    calculateRequestArgumentSize(keyBytes) + (condition == null ? 0 : condition.encodedByteCount()) +
                    (change == null ? 0 : change.encodedByteCount()) + calculateRequestArgumentSize(score1) +
                    calculateRequestArgumentSize(member1Bytes) + calculateRequestArgumentSize(score2) +
                    calculateRequestArgumentSize(member2Bytes);
        Buffer buffer = reservedCnx.executionContext().bufferAllocator().newBuffer(capacity);
        writeRequestArraySize(buffer, len);
        RedisProtocolSupport.Command.ZADD.encodeTo(buffer);
        writeRequestArgument(buffer, key, keyBytes);
        if (condition != null) {
            condition.encodeTo(buffer);
        }
        if (change != null) {
            change.encodeTo(buffer);
        }
        writeRequestArgument(buffer, score1);
        writeRequestArgument(buffer, member1, member1Bytes);
        writeRequestArgument(buffer, score2);
        writeRequestArgument(buffer, member2, member2Bytes);
        final RedisRequest request = newRequest(RedisProtocolSupport.Command.ZADD, buffer);
        final Single<String> queued = reservedCnx.request(request, String.class);
        Future<Long> result = enqueueForExecute(state, singles, queued);
        return result;
    }

    @Override
    public Future<Long> zadd(@RedisProtocolSupport.Key final CharSequence key,
                             @Nullable final RedisProtocolSupport.ZaddCondition condition,
                             @Nullable final RedisProtocolSupport.ZaddChange change, final double score1,
                             final CharSequence member1, final double score2, final CharSequence member2,
                             final double score3, final CharSequence member3) {
        requireNonNull(key);
        requireNonNull(member1);
        requireNonNull(member2);
        requireNonNull(member3);
        final int len = 8 + (condition == null ? 0 : 1) + (change == null ? 0 : 1);
        final int keyBytes = numberOfBytesUtf8(key);
        final int member1Bytes = numberOfBytesUtf8(member1);
        final int member2Bytes = numberOfBytesUtf8(member2);
        final int member3Bytes = numberOfBytesUtf8(member3);
        final int capacity = calculateInitialCommandBufferSize(len, RedisProtocolSupport.Command.ZADD) +
                    calculateRequestArgumentSize(keyBytes) + (condition == null ? 0 : condition.encodedByteCount()) +
                    (change == null ? 0 : change.encodedByteCount()) + calculateRequestArgumentSize(score1) +
                    calculateRequestArgumentSize(member1Bytes) + calculateRequestArgumentSize(score2) +
                    calculateRequestArgumentSize(member2Bytes) + calculateRequestArgumentSize(score3) +
                    calculateRequestArgumentSize(member3Bytes);
        Buffer buffer = reservedCnx.executionContext().bufferAllocator().newBuffer(capacity);
        writeRequestArraySize(buffer, len);
        RedisProtocolSupport.Command.ZADD.encodeTo(buffer);
        writeRequestArgument(buffer, key, keyBytes);
        if (condition != null) {
            condition.encodeTo(buffer);
        }
        if (change != null) {
            change.encodeTo(buffer);
        }
        writeRequestArgument(buffer, score1);
        writeRequestArgument(buffer, member1, member1Bytes);
        writeRequestArgument(buffer, score2);
        writeRequestArgument(buffer, member2, member2Bytes);
        writeRequestArgument(buffer, score3);
        writeRequestArgument(buffer, member3, member3Bytes);
        final RedisRequest request = newRequest(RedisProtocolSupport.Command.ZADD, buffer);
        final Single<String> queued = reservedCnx.request(request, String.class);
        Future<Long> result = enqueueForExecute(state, singles, queued);
        return result;
    }

    @Override
    public Future<Long> zadd(@RedisProtocolSupport.Key final CharSequence key,
                             @Nullable final RedisProtocolSupport.ZaddCondition condition,
                             @Nullable final RedisProtocolSupport.ZaddChange change,
                             final Collection<RedisProtocolSupport.ScoreMember> scoreMembers) {
        requireNonNull(key);
        requireNonNull(scoreMembers);
        final int len = 2 + (condition == null ? 0 : 1) + (change == null ? 0 : 1) +
                    RedisProtocolSupport.ScoreMember.SIZE * scoreMembers.size();
        final int keyBytes = numberOfBytesUtf8(key);
        int scoreMembersCapacity = 0;
        if (scoreMembers instanceof List && scoreMembers instanceof RandomAccess) {
            final List<RedisProtocolSupport.ScoreMember> list = (List<RedisProtocolSupport.ScoreMember>) scoreMembers;
            for (int i = 0; i < list.size(); ++i) {
                final RedisProtocolSupport.ScoreMember arg = list.get(i);
                scoreMembersCapacity += arg.encodedByteCount();
            }
        } else {
            for (RedisProtocolSupport.ScoreMember arg : scoreMembers) {
                scoreMembersCapacity += arg.encodedByteCount();
            }
        }
        final int capacity = calculateInitialCommandBufferSize(len, RedisProtocolSupport.Command.ZADD) +
                    calculateRequestArgumentSize(keyBytes) + (condition == null ? 0 : condition.encodedByteCount()) +
                    (change == null ? 0 : change.encodedByteCount()) + scoreMembersCapacity;
        Buffer buffer = reservedCnx.executionContext().bufferAllocator().newBuffer(capacity);
        writeRequestArraySize(buffer, len);
        RedisProtocolSupport.Command.ZADD.encodeTo(buffer);
        writeRequestArgument(buffer, key, keyBytes);
        if (condition != null) {
            condition.encodeTo(buffer);
        }
        if (change != null) {
            change.encodeTo(buffer);
        }
        if (scoreMembers instanceof List && scoreMembers instanceof RandomAccess) {
            final List<RedisProtocolSupport.ScoreMember> list = (List<RedisProtocolSupport.ScoreMember>) scoreMembers;
            for (int i = 0; i < list.size(); ++i) {
                final RedisProtocolSupport.ScoreMember arg = list.get(i);
                arg.encodeTo(buffer);
            }
        } else {
            for (RedisProtocolSupport.ScoreMember arg : scoreMembers) {
                arg.encodeTo(buffer);
            }
        }
        final RedisRequest request = newRequest(RedisProtocolSupport.Command.ZADD, buffer);
        final Single<String> queued = reservedCnx.request(request, String.class);
        Future<Long> result = enqueueForExecute(state, singles, queued);
        return result;
    }

    @Override
    public Future<Double> zaddIncr(@RedisProtocolSupport.Key final CharSequence key,
                                   final Collection<RedisProtocolSupport.ScoreMember> scoreMembers) {
        requireNonNull(key);
        requireNonNull(scoreMembers);
        final int len = 3 + RedisProtocolSupport.ScoreMember.SIZE * scoreMembers.size();
        final int keyBytes = numberOfBytesUtf8(key);
        int scoreMembersCapacity = 0;
        if (scoreMembers instanceof List && scoreMembers instanceof RandomAccess) {
            final List<RedisProtocolSupport.ScoreMember> list = (List<RedisProtocolSupport.ScoreMember>) scoreMembers;
            for (int i = 0; i < list.size(); ++i) {
                final RedisProtocolSupport.ScoreMember arg = list.get(i);
                scoreMembersCapacity += arg.encodedByteCount();
            }
        } else {
            for (RedisProtocolSupport.ScoreMember arg : scoreMembers) {
                scoreMembersCapacity += arg.encodedByteCount();
            }
        }
        final int capacity = calculateInitialCommandBufferSize(len, RedisProtocolSupport.Command.ZADD) +
                    calculateRequestArgumentSize(keyBytes) +
                    RedisProtocolSupport.ZaddIncrement.values()[0].encodedByteCount() + scoreMembersCapacity;
        Buffer buffer = reservedCnx.executionContext().bufferAllocator().newBuffer(capacity);
        writeRequestArraySize(buffer, len);
        RedisProtocolSupport.Command.ZADD.encodeTo(buffer);
        writeRequestArgument(buffer, key, keyBytes);
        RedisProtocolSupport.ZaddIncrement.values()[0].encodeTo(buffer);
        if (scoreMembers instanceof List && scoreMembers instanceof RandomAccess) {
            final List<RedisProtocolSupport.ScoreMember> list = (List<RedisProtocolSupport.ScoreMember>) scoreMembers;
            for (int i = 0; i < list.size(); ++i) {
                final RedisProtocolSupport.ScoreMember arg = list.get(i);
                arg.encodeTo(buffer);
            }
        } else {
            for (RedisProtocolSupport.ScoreMember arg : scoreMembers) {
                arg.encodeTo(buffer);
            }
        }
        final RedisRequest request = newRequest(RedisProtocolSupport.Command.ZADD, buffer);
        final Single<String> queued = reservedCnx.request(request, String.class);
        Future<Double> result = enqueueForExecute(state, singles, queued);
        return result;
    }

    @Override
    public Future<Double> zaddIncr(@RedisProtocolSupport.Key final CharSequence key,
                                   @Nullable final RedisProtocolSupport.ZaddCondition condition,
                                   @Nullable final RedisProtocolSupport.ZaddChange change, final double score,
                                   final CharSequence member) {
        requireNonNull(key);
        requireNonNull(member);
        final int len = 5 + (condition == null ? 0 : 1) + (change == null ? 0 : 1);
        final int keyBytes = numberOfBytesUtf8(key);
        final int memberBytes = numberOfBytesUtf8(member);
        final int capacity = calculateInitialCommandBufferSize(len, RedisProtocolSupport.Command.ZADD) +
                    calculateRequestArgumentSize(keyBytes) +
                    RedisProtocolSupport.ZaddIncrement.values()[0].encodedByteCount() +
                    (condition == null ? 0 : condition.encodedByteCount()) +
                    (change == null ? 0 : change.encodedByteCount()) + calculateRequestArgumentSize(score) +
                    calculateRequestArgumentSize(memberBytes);
        Buffer buffer = reservedCnx.executionContext().bufferAllocator().newBuffer(capacity);
        writeRequestArraySize(buffer, len);
        RedisProtocolSupport.Command.ZADD.encodeTo(buffer);
        writeRequestArgument(buffer, key, keyBytes);
        RedisProtocolSupport.ZaddIncrement.values()[0].encodeTo(buffer);
        if (condition != null) {
            condition.encodeTo(buffer);
        }
        if (change != null) {
            change.encodeTo(buffer);
        }
        writeRequestArgument(buffer, score);
        writeRequestArgument(buffer, member, memberBytes);
        final RedisRequest request = newRequest(RedisProtocolSupport.Command.ZADD, buffer);
        final Single<String> queued = reservedCnx.request(request, String.class);
        Future<Double> result = enqueueForExecute(state, singles, queued);
        return result;
    }

    @Override
    public Future<Double> zaddIncr(@RedisProtocolSupport.Key final CharSequence key,
                                   @Nullable final RedisProtocolSupport.ZaddCondition condition,
                                   @Nullable final RedisProtocolSupport.ZaddChange change, final double score1,
                                   final CharSequence member1, final double score2, final CharSequence member2) {
        requireNonNull(key);
        requireNonNull(member1);
        requireNonNull(member2);
        final int len = 7 + (condition == null ? 0 : 1) + (change == null ? 0 : 1);
        final int keyBytes = numberOfBytesUtf8(key);
        final int member1Bytes = numberOfBytesUtf8(member1);
        final int member2Bytes = numberOfBytesUtf8(member2);
        final int capacity = calculateInitialCommandBufferSize(len, RedisProtocolSupport.Command.ZADD) +
                    calculateRequestArgumentSize(keyBytes) +
                    RedisProtocolSupport.ZaddIncrement.values()[0].encodedByteCount() +
                    (condition == null ? 0 : condition.encodedByteCount()) +
                    (change == null ? 0 : change.encodedByteCount()) + calculateRequestArgumentSize(score1) +
                    calculateRequestArgumentSize(member1Bytes) + calculateRequestArgumentSize(score2) +
                    calculateRequestArgumentSize(member2Bytes);
        Buffer buffer = reservedCnx.executionContext().bufferAllocator().newBuffer(capacity);
        writeRequestArraySize(buffer, len);
        RedisProtocolSupport.Command.ZADD.encodeTo(buffer);
        writeRequestArgument(buffer, key, keyBytes);
        RedisProtocolSupport.ZaddIncrement.values()[0].encodeTo(buffer);
        if (condition != null) {
            condition.encodeTo(buffer);
        }
        if (change != null) {
            change.encodeTo(buffer);
        }
        writeRequestArgument(buffer, score1);
        writeRequestArgument(buffer, member1, member1Bytes);
        writeRequestArgument(buffer, score2);
        writeRequestArgument(buffer, member2, member2Bytes);
        final RedisRequest request = newRequest(RedisProtocolSupport.Command.ZADD, buffer);
        final Single<String> queued = reservedCnx.request(request, String.class);
        Future<Double> result = enqueueForExecute(state, singles, queued);
        return result;
    }

    @Override
    public Future<Double> zaddIncr(@RedisProtocolSupport.Key final CharSequence key,
                                   @Nullable final RedisProtocolSupport.ZaddCondition condition,
                                   @Nullable final RedisProtocolSupport.ZaddChange change, final double score1,
                                   final CharSequence member1, final double score2, final CharSequence member2,
                                   final double score3, final CharSequence member3) {
        requireNonNull(key);
        requireNonNull(member1);
        requireNonNull(member2);
        requireNonNull(member3);
        final int len = 9 + (condition == null ? 0 : 1) + (change == null ? 0 : 1);
        final int keyBytes = numberOfBytesUtf8(key);
        final int member1Bytes = numberOfBytesUtf8(member1);
        final int member2Bytes = numberOfBytesUtf8(member2);
        final int member3Bytes = numberOfBytesUtf8(member3);
        final int capacity = calculateInitialCommandBufferSize(len, RedisProtocolSupport.Command.ZADD) +
                    calculateRequestArgumentSize(keyBytes) +
                    RedisProtocolSupport.ZaddIncrement.values()[0].encodedByteCount() +
                    (condition == null ? 0 : condition.encodedByteCount()) +
                    (change == null ? 0 : change.encodedByteCount()) + calculateRequestArgumentSize(score1) +
                    calculateRequestArgumentSize(member1Bytes) + calculateRequestArgumentSize(score2) +
                    calculateRequestArgumentSize(member2Bytes) + calculateRequestArgumentSize(score3) +
                    calculateRequestArgumentSize(member3Bytes);
        Buffer buffer = reservedCnx.executionContext().bufferAllocator().newBuffer(capacity);
        writeRequestArraySize(buffer, len);
        RedisProtocolSupport.Command.ZADD.encodeTo(buffer);
        writeRequestArgument(buffer, key, keyBytes);
        RedisProtocolSupport.ZaddIncrement.values()[0].encodeTo(buffer);
        if (condition != null) {
            condition.encodeTo(buffer);
        }
        if (change != null) {
            change.encodeTo(buffer);
        }
        writeRequestArgument(buffer, score1);
        writeRequestArgument(buffer, member1, member1Bytes);
        writeRequestArgument(buffer, score2);
        writeRequestArgument(buffer, member2, member2Bytes);
        writeRequestArgument(buffer, score3);
        writeRequestArgument(buffer, member3, member3Bytes);
        final RedisRequest request = newRequest(RedisProtocolSupport.Command.ZADD, buffer);
        final Single<String> queued = reservedCnx.request(request, String.class);
        Future<Double> result = enqueueForExecute(state, singles, queued);
        return result;
    }

    @Override
    public Future<Double> zaddIncr(@RedisProtocolSupport.Key final CharSequence key,
                                   @Nullable final RedisProtocolSupport.ZaddCondition condition,
                                   @Nullable final RedisProtocolSupport.ZaddChange change,
                                   final Collection<RedisProtocolSupport.ScoreMember> scoreMembers) {
        requireNonNull(key);
        requireNonNull(scoreMembers);
        final int len = 3 + (condition == null ? 0 : 1) + (change == null ? 0 : 1) +
                    RedisProtocolSupport.ScoreMember.SIZE * scoreMembers.size();
        final int keyBytes = numberOfBytesUtf8(key);
        int scoreMembersCapacity = 0;
        if (scoreMembers instanceof List && scoreMembers instanceof RandomAccess) {
            final List<RedisProtocolSupport.ScoreMember> list = (List<RedisProtocolSupport.ScoreMember>) scoreMembers;
            for (int i = 0; i < list.size(); ++i) {
                final RedisProtocolSupport.ScoreMember arg = list.get(i);
                scoreMembersCapacity += arg.encodedByteCount();
            }
        } else {
            for (RedisProtocolSupport.ScoreMember arg : scoreMembers) {
                scoreMembersCapacity += arg.encodedByteCount();
            }
        }
        final int capacity = calculateInitialCommandBufferSize(len, RedisProtocolSupport.Command.ZADD) +
                    calculateRequestArgumentSize(keyBytes) +
                    RedisProtocolSupport.ZaddIncrement.values()[0].encodedByteCount() +
                    (condition == null ? 0 : condition.encodedByteCount()) +
                    (change == null ? 0 : change.encodedByteCount()) + scoreMembersCapacity;
        Buffer buffer = reservedCnx.executionContext().bufferAllocator().newBuffer(capacity);
        writeRequestArraySize(buffer, len);
        RedisProtocolSupport.Command.ZADD.encodeTo(buffer);
        writeRequestArgument(buffer, key, keyBytes);
        RedisProtocolSupport.ZaddIncrement.values()[0].encodeTo(buffer);
        if (condition != null) {
            condition.encodeTo(buffer);
        }
        if (change != null) {
            change.encodeTo(buffer);
        }
        if (scoreMembers instanceof List && scoreMembers instanceof RandomAccess) {
            final List<RedisProtocolSupport.ScoreMember> list = (List<RedisProtocolSupport.ScoreMember>) scoreMembers;
            for (int i = 0; i < list.size(); ++i) {
                final RedisProtocolSupport.ScoreMember arg = list.get(i);
                arg.encodeTo(buffer);
            }
        } else {
            for (RedisProtocolSupport.ScoreMember arg : scoreMembers) {
                arg.encodeTo(buffer);
            }
        }
        final RedisRequest request = newRequest(RedisProtocolSupport.Command.ZADD, buffer);
        final Single<String> queued = reservedCnx.request(request, String.class);
        Future<Double> result = enqueueForExecute(state, singles, queued);
        return result;
    }

    @Override
    public Future<Long> zcard(@RedisProtocolSupport.Key final CharSequence key) {
        requireNonNull(key);
        final int len = 2;
        final int keyBytes = numberOfBytesUtf8(key);
        final int capacity = calculateInitialCommandBufferSize(len, RedisProtocolSupport.Command.ZCARD) +
                    calculateRequestArgumentSize(keyBytes);
        Buffer buffer = reservedCnx.executionContext().bufferAllocator().newBuffer(capacity);
        writeRequestArraySize(buffer, len);
        RedisProtocolSupport.Command.ZCARD.encodeTo(buffer);
        writeRequestArgument(buffer, key, keyBytes);
        final RedisRequest request = newRequest(RedisProtocolSupport.Command.ZCARD, buffer);
        final Single<String> queued = reservedCnx.request(request, String.class);
        Future<Long> result = enqueueForExecute(state, singles, queued);
        return result;
    }

    @Override
    public Future<Long> zcount(@RedisProtocolSupport.Key final CharSequence key, final double min, final double max) {
        requireNonNull(key);
        final int len = 4;
        final int keyBytes = numberOfBytesUtf8(key);
        final int capacity = calculateInitialCommandBufferSize(len, RedisProtocolSupport.Command.ZCOUNT) +
                    calculateRequestArgumentSize(keyBytes) + calculateRequestArgumentSize(min) +
                    calculateRequestArgumentSize(max);
        Buffer buffer = reservedCnx.executionContext().bufferAllocator().newBuffer(capacity);
        writeRequestArraySize(buffer, len);
        RedisProtocolSupport.Command.ZCOUNT.encodeTo(buffer);
        writeRequestArgument(buffer, key, keyBytes);
        writeRequestArgument(buffer, min);
        writeRequestArgument(buffer, max);
        final RedisRequest request = newRequest(RedisProtocolSupport.Command.ZCOUNT, buffer);
        final Single<String> queued = reservedCnx.request(request, String.class);
        Future<Long> result = enqueueForExecute(state, singles, queued);
        return result;
    }

    @Override
    public Future<Double> zincrby(@RedisProtocolSupport.Key final CharSequence key, final long increment,
                                  final CharSequence member) {
        requireNonNull(key);
        requireNonNull(member);
        final int len = 4;
        final int keyBytes = numberOfBytesUtf8(key);
        final int memberBytes = numberOfBytesUtf8(member);
        final int capacity = calculateInitialCommandBufferSize(len, RedisProtocolSupport.Command.ZINCRBY) +
                    calculateRequestArgumentSize(keyBytes) + calculateRequestArgumentSize(increment) +
                    calculateRequestArgumentSize(memberBytes);
        Buffer buffer = reservedCnx.executionContext().bufferAllocator().newBuffer(capacity);
        writeRequestArraySize(buffer, len);
        RedisProtocolSupport.Command.ZINCRBY.encodeTo(buffer);
        writeRequestArgument(buffer, key, keyBytes);
        writeRequestArgument(buffer, increment);
        writeRequestArgument(buffer, member, memberBytes);
        final RedisRequest request = newRequest(RedisProtocolSupport.Command.ZINCRBY, buffer);
        final Single<String> queued = reservedCnx.request(request, String.class);
        Future<Double> result = enqueueForExecute(state, singles, queued);
        return result;
    }

    @Override
    public Future<Long> zinterstore(@RedisProtocolSupport.Key final CharSequence destination, final long numkeys,
                                    @RedisProtocolSupport.Key final Collection<? extends CharSequence> keys) {
        requireNonNull(destination);
        requireNonNull(keys);
        final int len = 3 + keys.size();
        final int destinationBytes = numberOfBytesUtf8(destination);
        int keysCapacity = 0;
        if (keys instanceof List && keys instanceof RandomAccess) {
            final List<CharSequence> list = (List<CharSequence>) keys;
            for (int i = 0; i < list.size(); ++i) {
                final CharSequence arg = list.get(i);
                keysCapacity += estimateRequestArgumentSize(arg);
            }
        } else {
            for (CharSequence arg : keys) {
                keysCapacity += estimateRequestArgumentSize(arg);
            }
        }
        final int capacity = calculateInitialCommandBufferSize(len, RedisProtocolSupport.Command.ZINTERSTORE) +
                    calculateRequestArgumentSize(destinationBytes) + calculateRequestArgumentSize(numkeys) +
                    keysCapacity;
        Buffer buffer = reservedCnx.executionContext().bufferAllocator().newBuffer(capacity);
        writeRequestArraySize(buffer, len);
        RedisProtocolSupport.Command.ZINTERSTORE.encodeTo(buffer);
        writeRequestArgument(buffer, destination, destinationBytes);
        writeRequestArgument(buffer, numkeys);
        if (keys instanceof List && keys instanceof RandomAccess) {
            final List<CharSequence> list = (List<CharSequence>) keys;
            for (int i = 0; i < list.size(); ++i) {
                final CharSequence arg = list.get(i);
                writeRequestArgument(buffer, arg);
            }
        } else {
            for (CharSequence arg : keys) {
                writeRequestArgument(buffer, arg);
            }
        }
        final RedisRequest request = newRequest(RedisProtocolSupport.Command.ZINTERSTORE, buffer);
        final Single<String> queued = reservedCnx.request(request, String.class);
        Future<Long> result = enqueueForExecute(state, singles, queued);
        return result;
    }

    @Override
    public Future<Long> zinterstore(@RedisProtocolSupport.Key final CharSequence destination, final long numkeys,
                                    @RedisProtocolSupport.Key final Collection<? extends CharSequence> keys,
                                    final Collection<Long> weights,
                                    @Nullable final RedisProtocolSupport.ZinterstoreAggregate aggregate) {
        requireNonNull(destination);
        requireNonNull(keys);
        requireNonNull(weights);
        final int len = 4 + keys.size() + weights.size() + (aggregate == null ? 0 : 1);
        final int destinationBytes = numberOfBytesUtf8(destination);
        int keysCapacity = 0;
        if (keys instanceof List && keys instanceof RandomAccess) {
            final List<CharSequence> list = (List<CharSequence>) keys;
            for (int i = 0; i < list.size(); ++i) {
                final CharSequence arg = list.get(i);
                keysCapacity += estimateRequestArgumentSize(arg);
            }
        } else {
            for (CharSequence arg : keys) {
                keysCapacity += estimateRequestArgumentSize(arg);
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
                    calculateRequestArgumentSize(destinationBytes) + calculateRequestArgumentSize(numkeys) +
                    keysCapacity + RedisProtocolSupport.SubCommand.WEIGHTS.encodedByteCount() + weightsCapacity +
                    (aggregate == null ? 0 : aggregate.encodedByteCount());
        Buffer buffer = reservedCnx.executionContext().bufferAllocator().newBuffer(capacity);
        writeRequestArraySize(buffer, len);
        RedisProtocolSupport.Command.ZINTERSTORE.encodeTo(buffer);
        writeRequestArgument(buffer, destination, destinationBytes);
        writeRequestArgument(buffer, numkeys);
        if (keys instanceof List && keys instanceof RandomAccess) {
            final List<CharSequence> list = (List<CharSequence>) keys;
            for (int i = 0; i < list.size(); ++i) {
                final CharSequence arg = list.get(i);
                writeRequestArgument(buffer, arg);
            }
        } else {
            for (CharSequence arg : keys) {
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
        final Single<String> queued = reservedCnx.request(request, String.class);
        Future<Long> result = enqueueForExecute(state, singles, queued);
        return result;
    }

    @Override
    public Future<Long> zlexcount(@RedisProtocolSupport.Key final CharSequence key, final CharSequence min,
                                  final CharSequence max) {
        requireNonNull(key);
        requireNonNull(min);
        requireNonNull(max);
        final int len = 4;
        final int keyBytes = numberOfBytesUtf8(key);
        final int minBytes = numberOfBytesUtf8(min);
        final int maxBytes = numberOfBytesUtf8(max);
        final int capacity = calculateInitialCommandBufferSize(len, RedisProtocolSupport.Command.ZLEXCOUNT) +
                    calculateRequestArgumentSize(keyBytes) + calculateRequestArgumentSize(minBytes) +
                    calculateRequestArgumentSize(maxBytes);
        Buffer buffer = reservedCnx.executionContext().bufferAllocator().newBuffer(capacity);
        writeRequestArraySize(buffer, len);
        RedisProtocolSupport.Command.ZLEXCOUNT.encodeTo(buffer);
        writeRequestArgument(buffer, key, keyBytes);
        writeRequestArgument(buffer, min, minBytes);
        writeRequestArgument(buffer, max, maxBytes);
        final RedisRequest request = newRequest(RedisProtocolSupport.Command.ZLEXCOUNT, buffer);
        final Single<String> queued = reservedCnx.request(request, String.class);
        Future<Long> result = enqueueForExecute(state, singles, queued);
        return result;
    }

    @Override
    public <T> Future<List<T>> zpopmax(@RedisProtocolSupport.Key final CharSequence key) {
        requireNonNull(key);
        final int len = 2;
        final int keyBytes = numberOfBytesUtf8(key);
        final int capacity = calculateInitialCommandBufferSize(len, RedisProtocolSupport.Command.ZPOPMAX) +
                    calculateRequestArgumentSize(keyBytes);
        Buffer buffer = reservedCnx.executionContext().bufferAllocator().newBuffer(capacity);
        writeRequestArraySize(buffer, len);
        RedisProtocolSupport.Command.ZPOPMAX.encodeTo(buffer);
        writeRequestArgument(buffer, key, keyBytes);
        final RedisRequest request = newRequest(RedisProtocolSupport.Command.ZPOPMAX, buffer);
        final Single<String> queued = reservedCnx.request(request, String.class);
        Future<List<T>> result = enqueueForExecute(state, singles, queued);
        return result;
    }

    @Override
    public <T> Future<List<T>> zpopmax(@RedisProtocolSupport.Key final CharSequence key, @Nullable final Long count) {
        requireNonNull(key);
        final int len = 2 + (count == null ? 0 : 1);
        final int keyBytes = numberOfBytesUtf8(key);
        final int capacity = calculateInitialCommandBufferSize(len, RedisProtocolSupport.Command.ZPOPMAX) +
                    calculateRequestArgumentSize(keyBytes) + (count == null ? 0 : calculateRequestArgumentSize(count));
        Buffer buffer = reservedCnx.executionContext().bufferAllocator().newBuffer(capacity);
        writeRequestArraySize(buffer, len);
        RedisProtocolSupport.Command.ZPOPMAX.encodeTo(buffer);
        writeRequestArgument(buffer, key, keyBytes);
        if (count != null) {
            writeRequestArgument(buffer, count);
        }
        final RedisRequest request = newRequest(RedisProtocolSupport.Command.ZPOPMAX, buffer);
        final Single<String> queued = reservedCnx.request(request, String.class);
        Future<List<T>> result = enqueueForExecute(state, singles, queued);
        return result;
    }

    @Override
    public <T> Future<List<T>> zpopmin(@RedisProtocolSupport.Key final CharSequence key) {
        requireNonNull(key);
        final int len = 2;
        final int keyBytes = numberOfBytesUtf8(key);
        final int capacity = calculateInitialCommandBufferSize(len, RedisProtocolSupport.Command.ZPOPMIN) +
                    calculateRequestArgumentSize(keyBytes);
        Buffer buffer = reservedCnx.executionContext().bufferAllocator().newBuffer(capacity);
        writeRequestArraySize(buffer, len);
        RedisProtocolSupport.Command.ZPOPMIN.encodeTo(buffer);
        writeRequestArgument(buffer, key, keyBytes);
        final RedisRequest request = newRequest(RedisProtocolSupport.Command.ZPOPMIN, buffer);
        final Single<String> queued = reservedCnx.request(request, String.class);
        Future<List<T>> result = enqueueForExecute(state, singles, queued);
        return result;
    }

    @Override
    public <T> Future<List<T>> zpopmin(@RedisProtocolSupport.Key final CharSequence key, @Nullable final Long count) {
        requireNonNull(key);
        final int len = 2 + (count == null ? 0 : 1);
        final int keyBytes = numberOfBytesUtf8(key);
        final int capacity = calculateInitialCommandBufferSize(len, RedisProtocolSupport.Command.ZPOPMIN) +
                    calculateRequestArgumentSize(keyBytes) + (count == null ? 0 : calculateRequestArgumentSize(count));
        Buffer buffer = reservedCnx.executionContext().bufferAllocator().newBuffer(capacity);
        writeRequestArraySize(buffer, len);
        RedisProtocolSupport.Command.ZPOPMIN.encodeTo(buffer);
        writeRequestArgument(buffer, key, keyBytes);
        if (count != null) {
            writeRequestArgument(buffer, count);
        }
        final RedisRequest request = newRequest(RedisProtocolSupport.Command.ZPOPMIN, buffer);
        final Single<String> queued = reservedCnx.request(request, String.class);
        Future<List<T>> result = enqueueForExecute(state, singles, queued);
        return result;
    }

    @Override
    public <T> Future<List<T>> zrange(@RedisProtocolSupport.Key final CharSequence key, final long start,
                                      final long stop) {
        requireNonNull(key);
        final int len = 4;
        final int keyBytes = numberOfBytesUtf8(key);
        final int capacity = calculateInitialCommandBufferSize(len, RedisProtocolSupport.Command.ZRANGE) +
                    calculateRequestArgumentSize(keyBytes) + calculateRequestArgumentSize(start) +
                    calculateRequestArgumentSize(stop);
        Buffer buffer = reservedCnx.executionContext().bufferAllocator().newBuffer(capacity);
        writeRequestArraySize(buffer, len);
        RedisProtocolSupport.Command.ZRANGE.encodeTo(buffer);
        writeRequestArgument(buffer, key, keyBytes);
        writeRequestArgument(buffer, start);
        writeRequestArgument(buffer, stop);
        final RedisRequest request = newRequest(RedisProtocolSupport.Command.ZRANGE, buffer);
        final Single<String> queued = reservedCnx.request(request, String.class);
        Future<List<T>> result = enqueueForExecute(state, singles, queued);
        return result;
    }

    @Override
    public <T> Future<List<T>> zrange(@RedisProtocolSupport.Key final CharSequence key, final long start,
                                      final long stop,
                                      @Nullable final RedisProtocolSupport.ZrangeWithscores withscores) {
        requireNonNull(key);
        final int len = 4 + (withscores == null ? 0 : 1);
        final int keyBytes = numberOfBytesUtf8(key);
        final int capacity = calculateInitialCommandBufferSize(len, RedisProtocolSupport.Command.ZRANGE) +
                    calculateRequestArgumentSize(keyBytes) + calculateRequestArgumentSize(start) +
                    calculateRequestArgumentSize(stop) + (withscores == null ? 0 : withscores.encodedByteCount());
        Buffer buffer = reservedCnx.executionContext().bufferAllocator().newBuffer(capacity);
        writeRequestArraySize(buffer, len);
        RedisProtocolSupport.Command.ZRANGE.encodeTo(buffer);
        writeRequestArgument(buffer, key, keyBytes);
        writeRequestArgument(buffer, start);
        writeRequestArgument(buffer, stop);
        if (withscores != null) {
            withscores.encodeTo(buffer);
        }
        final RedisRequest request = newRequest(RedisProtocolSupport.Command.ZRANGE, buffer);
        final Single<String> queued = reservedCnx.request(request, String.class);
        Future<List<T>> result = enqueueForExecute(state, singles, queued);
        return result;
    }

    @Override
    public <T> Future<List<T>> zrangebylex(@RedisProtocolSupport.Key final CharSequence key, final CharSequence min,
                                           final CharSequence max) {
        requireNonNull(key);
        requireNonNull(min);
        requireNonNull(max);
        final int len = 4;
        final int keyBytes = numberOfBytesUtf8(key);
        final int minBytes = numberOfBytesUtf8(min);
        final int maxBytes = numberOfBytesUtf8(max);
        final int capacity = calculateInitialCommandBufferSize(len, RedisProtocolSupport.Command.ZRANGEBYLEX) +
                    calculateRequestArgumentSize(keyBytes) + calculateRequestArgumentSize(minBytes) +
                    calculateRequestArgumentSize(maxBytes);
        Buffer buffer = reservedCnx.executionContext().bufferAllocator().newBuffer(capacity);
        writeRequestArraySize(buffer, len);
        RedisProtocolSupport.Command.ZRANGEBYLEX.encodeTo(buffer);
        writeRequestArgument(buffer, key, keyBytes);
        writeRequestArgument(buffer, min, minBytes);
        writeRequestArgument(buffer, max, maxBytes);
        final RedisRequest request = newRequest(RedisProtocolSupport.Command.ZRANGEBYLEX, buffer);
        final Single<String> queued = reservedCnx.request(request, String.class);
        Future<List<T>> result = enqueueForExecute(state, singles, queued);
        return result;
    }

    @Override
    public <T> Future<List<T>> zrangebylex(@RedisProtocolSupport.Key final CharSequence key, final CharSequence min,
                                           final CharSequence max,
                                           @Nullable final RedisProtocolSupport.OffsetCount offsetCount) {
        requireNonNull(key);
        requireNonNull(min);
        requireNonNull(max);
        final int len = 4 + (offsetCount == null ? 0 : RedisProtocolSupport.OffsetCount.SIZE);
        final int keyBytes = numberOfBytesUtf8(key);
        final int minBytes = numberOfBytesUtf8(min);
        final int maxBytes = numberOfBytesUtf8(max);
        final int capacity = calculateInitialCommandBufferSize(len, RedisProtocolSupport.Command.ZRANGEBYLEX) +
                    calculateRequestArgumentSize(keyBytes) + calculateRequestArgumentSize(minBytes) +
                    calculateRequestArgumentSize(maxBytes) + (offsetCount == null ? 0 : offsetCount.encodedByteCount());
        Buffer buffer = reservedCnx.executionContext().bufferAllocator().newBuffer(capacity);
        writeRequestArraySize(buffer, len);
        RedisProtocolSupport.Command.ZRANGEBYLEX.encodeTo(buffer);
        writeRequestArgument(buffer, key, keyBytes);
        writeRequestArgument(buffer, min, minBytes);
        writeRequestArgument(buffer, max, maxBytes);
        if (offsetCount != null) {
            offsetCount.encodeTo(buffer);
        }
        final RedisRequest request = newRequest(RedisProtocolSupport.Command.ZRANGEBYLEX, buffer);
        final Single<String> queued = reservedCnx.request(request, String.class);
        Future<List<T>> result = enqueueForExecute(state, singles, queued);
        return result;
    }

    @Override
    public <T> Future<List<T>> zrangebyscore(@RedisProtocolSupport.Key final CharSequence key, final double min,
                                             final double max) {
        requireNonNull(key);
        final int len = 4;
        final int keyBytes = numberOfBytesUtf8(key);
        final int capacity = calculateInitialCommandBufferSize(len, RedisProtocolSupport.Command.ZRANGEBYSCORE) +
                    calculateRequestArgumentSize(keyBytes) + calculateRequestArgumentSize(min) +
                    calculateRequestArgumentSize(max);
        Buffer buffer = reservedCnx.executionContext().bufferAllocator().newBuffer(capacity);
        writeRequestArraySize(buffer, len);
        RedisProtocolSupport.Command.ZRANGEBYSCORE.encodeTo(buffer);
        writeRequestArgument(buffer, key, keyBytes);
        writeRequestArgument(buffer, min);
        writeRequestArgument(buffer, max);
        final RedisRequest request = newRequest(RedisProtocolSupport.Command.ZRANGEBYSCORE, buffer);
        final Single<String> queued = reservedCnx.request(request, String.class);
        Future<List<T>> result = enqueueForExecute(state, singles, queued);
        return result;
    }

    @Override
    public <T> Future<List<T>> zrangebyscore(@RedisProtocolSupport.Key final CharSequence key, final double min,
                                             final double max,
                                             @Nullable final RedisProtocolSupport.ZrangebyscoreWithscores withscores,
                                             @Nullable final RedisProtocolSupport.OffsetCount offsetCount) {
        requireNonNull(key);
        final int len = 4 + (withscores == null ? 0 : 1) +
                    (offsetCount == null ? 0 : RedisProtocolSupport.OffsetCount.SIZE);
        final int keyBytes = numberOfBytesUtf8(key);
        final int capacity = calculateInitialCommandBufferSize(len, RedisProtocolSupport.Command.ZRANGEBYSCORE) +
                    calculateRequestArgumentSize(keyBytes) + calculateRequestArgumentSize(min) +
                    calculateRequestArgumentSize(max) + (withscores == null ? 0 : withscores.encodedByteCount()) +
                    (offsetCount == null ? 0 : offsetCount.encodedByteCount());
        Buffer buffer = reservedCnx.executionContext().bufferAllocator().newBuffer(capacity);
        writeRequestArraySize(buffer, len);
        RedisProtocolSupport.Command.ZRANGEBYSCORE.encodeTo(buffer);
        writeRequestArgument(buffer, key, keyBytes);
        writeRequestArgument(buffer, min);
        writeRequestArgument(buffer, max);
        if (withscores != null) {
            withscores.encodeTo(buffer);
        }
        if (offsetCount != null) {
            offsetCount.encodeTo(buffer);
        }
        final RedisRequest request = newRequest(RedisProtocolSupport.Command.ZRANGEBYSCORE, buffer);
        final Single<String> queued = reservedCnx.request(request, String.class);
        Future<List<T>> result = enqueueForExecute(state, singles, queued);
        return result;
    }

    @Override
    public Future<Long> zrank(@RedisProtocolSupport.Key final CharSequence key, final CharSequence member) {
        requireNonNull(key);
        requireNonNull(member);
        final int len = 3;
        final int keyBytes = numberOfBytesUtf8(key);
        final int memberBytes = numberOfBytesUtf8(member);
        final int capacity = calculateInitialCommandBufferSize(len, RedisProtocolSupport.Command.ZRANK) +
                    calculateRequestArgumentSize(keyBytes) + calculateRequestArgumentSize(memberBytes);
        Buffer buffer = reservedCnx.executionContext().bufferAllocator().newBuffer(capacity);
        writeRequestArraySize(buffer, len);
        RedisProtocolSupport.Command.ZRANK.encodeTo(buffer);
        writeRequestArgument(buffer, key, keyBytes);
        writeRequestArgument(buffer, member, memberBytes);
        final RedisRequest request = newRequest(RedisProtocolSupport.Command.ZRANK, buffer);
        final Single<String> queued = reservedCnx.request(request, String.class);
        Future<Long> result = enqueueForExecute(state, singles, queued);
        return result;
    }

    @Override
    public Future<Long> zrem(@RedisProtocolSupport.Key final CharSequence key, final CharSequence member) {
        requireNonNull(key);
        requireNonNull(member);
        final int len = 3;
        final int keyBytes = numberOfBytesUtf8(key);
        final int memberBytes = numberOfBytesUtf8(member);
        final int capacity = calculateInitialCommandBufferSize(len, RedisProtocolSupport.Command.ZREM) +
                    calculateRequestArgumentSize(keyBytes) + calculateRequestArgumentSize(memberBytes);
        Buffer buffer = reservedCnx.executionContext().bufferAllocator().newBuffer(capacity);
        writeRequestArraySize(buffer, len);
        RedisProtocolSupport.Command.ZREM.encodeTo(buffer);
        writeRequestArgument(buffer, key, keyBytes);
        writeRequestArgument(buffer, member, memberBytes);
        final RedisRequest request = newRequest(RedisProtocolSupport.Command.ZREM, buffer);
        final Single<String> queued = reservedCnx.request(request, String.class);
        Future<Long> result = enqueueForExecute(state, singles, queued);
        return result;
    }

    @Override
    public Future<Long> zrem(@RedisProtocolSupport.Key final CharSequence key, final CharSequence member1,
                             final CharSequence member2) {
        requireNonNull(key);
        requireNonNull(member1);
        requireNonNull(member2);
        final int len = 4;
        final int keyBytes = numberOfBytesUtf8(key);
        final int member1Bytes = numberOfBytesUtf8(member1);
        final int member2Bytes = numberOfBytesUtf8(member2);
        final int capacity = calculateInitialCommandBufferSize(len, RedisProtocolSupport.Command.ZREM) +
                    calculateRequestArgumentSize(keyBytes) + calculateRequestArgumentSize(member1Bytes) +
                    calculateRequestArgumentSize(member2Bytes);
        Buffer buffer = reservedCnx.executionContext().bufferAllocator().newBuffer(capacity);
        writeRequestArraySize(buffer, len);
        RedisProtocolSupport.Command.ZREM.encodeTo(buffer);
        writeRequestArgument(buffer, key, keyBytes);
        writeRequestArgument(buffer, member1, member1Bytes);
        writeRequestArgument(buffer, member2, member2Bytes);
        final RedisRequest request = newRequest(RedisProtocolSupport.Command.ZREM, buffer);
        final Single<String> queued = reservedCnx.request(request, String.class);
        Future<Long> result = enqueueForExecute(state, singles, queued);
        return result;
    }

    @Override
    public Future<Long> zrem(@RedisProtocolSupport.Key final CharSequence key, final CharSequence member1,
                             final CharSequence member2, final CharSequence member3) {
        requireNonNull(key);
        requireNonNull(member1);
        requireNonNull(member2);
        requireNonNull(member3);
        final int len = 5;
        final int keyBytes = numberOfBytesUtf8(key);
        final int member1Bytes = numberOfBytesUtf8(member1);
        final int member2Bytes = numberOfBytesUtf8(member2);
        final int member3Bytes = numberOfBytesUtf8(member3);
        final int capacity = calculateInitialCommandBufferSize(len, RedisProtocolSupport.Command.ZREM) +
                    calculateRequestArgumentSize(keyBytes) + calculateRequestArgumentSize(member1Bytes) +
                    calculateRequestArgumentSize(member2Bytes) + calculateRequestArgumentSize(member3Bytes);
        Buffer buffer = reservedCnx.executionContext().bufferAllocator().newBuffer(capacity);
        writeRequestArraySize(buffer, len);
        RedisProtocolSupport.Command.ZREM.encodeTo(buffer);
        writeRequestArgument(buffer, key, keyBytes);
        writeRequestArgument(buffer, member1, member1Bytes);
        writeRequestArgument(buffer, member2, member2Bytes);
        writeRequestArgument(buffer, member3, member3Bytes);
        final RedisRequest request = newRequest(RedisProtocolSupport.Command.ZREM, buffer);
        final Single<String> queued = reservedCnx.request(request, String.class);
        Future<Long> result = enqueueForExecute(state, singles, queued);
        return result;
    }

    @Override
    public Future<Long> zrem(@RedisProtocolSupport.Key final CharSequence key,
                             final Collection<? extends CharSequence> members) {
        requireNonNull(key);
        requireNonNull(members);
        final int len = 2 + members.size();
        final int keyBytes = numberOfBytesUtf8(key);
        int membersCapacity = 0;
        if (members instanceof List && members instanceof RandomAccess) {
            final List<CharSequence> list = (List<CharSequence>) members;
            for (int i = 0; i < list.size(); ++i) {
                final CharSequence arg = list.get(i);
                membersCapacity += estimateRequestArgumentSize(arg);
            }
        } else {
            for (CharSequence arg : members) {
                membersCapacity += estimateRequestArgumentSize(arg);
            }
        }
        final int capacity = calculateInitialCommandBufferSize(len, RedisProtocolSupport.Command.ZREM) +
                    calculateRequestArgumentSize(keyBytes) + membersCapacity;
        Buffer buffer = reservedCnx.executionContext().bufferAllocator().newBuffer(capacity);
        writeRequestArraySize(buffer, len);
        RedisProtocolSupport.Command.ZREM.encodeTo(buffer);
        writeRequestArgument(buffer, key, keyBytes);
        if (members instanceof List && members instanceof RandomAccess) {
            final List<CharSequence> list = (List<CharSequence>) members;
            for (int i = 0; i < list.size(); ++i) {
                final CharSequence arg = list.get(i);
                writeRequestArgument(buffer, arg);
            }
        } else {
            for (CharSequence arg : members) {
                writeRequestArgument(buffer, arg);
            }
        }
        final RedisRequest request = newRequest(RedisProtocolSupport.Command.ZREM, buffer);
        final Single<String> queued = reservedCnx.request(request, String.class);
        Future<Long> result = enqueueForExecute(state, singles, queued);
        return result;
    }

    @Override
    public Future<Long> zremrangebylex(@RedisProtocolSupport.Key final CharSequence key, final CharSequence min,
                                       final CharSequence max) {
        requireNonNull(key);
        requireNonNull(min);
        requireNonNull(max);
        final int len = 4;
        final int keyBytes = numberOfBytesUtf8(key);
        final int minBytes = numberOfBytesUtf8(min);
        final int maxBytes = numberOfBytesUtf8(max);
        final int capacity = calculateInitialCommandBufferSize(len, RedisProtocolSupport.Command.ZREMRANGEBYLEX) +
                    calculateRequestArgumentSize(keyBytes) + calculateRequestArgumentSize(minBytes) +
                    calculateRequestArgumentSize(maxBytes);
        Buffer buffer = reservedCnx.executionContext().bufferAllocator().newBuffer(capacity);
        writeRequestArraySize(buffer, len);
        RedisProtocolSupport.Command.ZREMRANGEBYLEX.encodeTo(buffer);
        writeRequestArgument(buffer, key, keyBytes);
        writeRequestArgument(buffer, min, minBytes);
        writeRequestArgument(buffer, max, maxBytes);
        final RedisRequest request = newRequest(RedisProtocolSupport.Command.ZREMRANGEBYLEX, buffer);
        final Single<String> queued = reservedCnx.request(request, String.class);
        Future<Long> result = enqueueForExecute(state, singles, queued);
        return result;
    }

    @Override
    public Future<Long> zremrangebyrank(@RedisProtocolSupport.Key final CharSequence key, final long start,
                                        final long stop) {
        requireNonNull(key);
        final int len = 4;
        final int keyBytes = numberOfBytesUtf8(key);
        final int capacity = calculateInitialCommandBufferSize(len, RedisProtocolSupport.Command.ZREMRANGEBYRANK) +
                    calculateRequestArgumentSize(keyBytes) + calculateRequestArgumentSize(start) +
                    calculateRequestArgumentSize(stop);
        Buffer buffer = reservedCnx.executionContext().bufferAllocator().newBuffer(capacity);
        writeRequestArraySize(buffer, len);
        RedisProtocolSupport.Command.ZREMRANGEBYRANK.encodeTo(buffer);
        writeRequestArgument(buffer, key, keyBytes);
        writeRequestArgument(buffer, start);
        writeRequestArgument(buffer, stop);
        final RedisRequest request = newRequest(RedisProtocolSupport.Command.ZREMRANGEBYRANK, buffer);
        final Single<String> queued = reservedCnx.request(request, String.class);
        Future<Long> result = enqueueForExecute(state, singles, queued);
        return result;
    }

    @Override
    public Future<Long> zremrangebyscore(@RedisProtocolSupport.Key final CharSequence key, final double min,
                                         final double max) {
        requireNonNull(key);
        final int len = 4;
        final int keyBytes = numberOfBytesUtf8(key);
        final int capacity = calculateInitialCommandBufferSize(len, RedisProtocolSupport.Command.ZREMRANGEBYSCORE) +
                    calculateRequestArgumentSize(keyBytes) + calculateRequestArgumentSize(min) +
                    calculateRequestArgumentSize(max);
        Buffer buffer = reservedCnx.executionContext().bufferAllocator().newBuffer(capacity);
        writeRequestArraySize(buffer, len);
        RedisProtocolSupport.Command.ZREMRANGEBYSCORE.encodeTo(buffer);
        writeRequestArgument(buffer, key, keyBytes);
        writeRequestArgument(buffer, min);
        writeRequestArgument(buffer, max);
        final RedisRequest request = newRequest(RedisProtocolSupport.Command.ZREMRANGEBYSCORE, buffer);
        final Single<String> queued = reservedCnx.request(request, String.class);
        Future<Long> result = enqueueForExecute(state, singles, queued);
        return result;
    }

    @Override
    public <T> Future<List<T>> zrevrange(@RedisProtocolSupport.Key final CharSequence key, final long start,
                                         final long stop) {
        requireNonNull(key);
        final int len = 4;
        final int keyBytes = numberOfBytesUtf8(key);
        final int capacity = calculateInitialCommandBufferSize(len, RedisProtocolSupport.Command.ZREVRANGE) +
                    calculateRequestArgumentSize(keyBytes) + calculateRequestArgumentSize(start) +
                    calculateRequestArgumentSize(stop);
        Buffer buffer = reservedCnx.executionContext().bufferAllocator().newBuffer(capacity);
        writeRequestArraySize(buffer, len);
        RedisProtocolSupport.Command.ZREVRANGE.encodeTo(buffer);
        writeRequestArgument(buffer, key, keyBytes);
        writeRequestArgument(buffer, start);
        writeRequestArgument(buffer, stop);
        final RedisRequest request = newRequest(RedisProtocolSupport.Command.ZREVRANGE, buffer);
        final Single<String> queued = reservedCnx.request(request, String.class);
        Future<List<T>> result = enqueueForExecute(state, singles, queued);
        return result;
    }

    @Override
    public <T> Future<List<T>> zrevrange(@RedisProtocolSupport.Key final CharSequence key, final long start,
                                         final long stop,
                                         @Nullable final RedisProtocolSupport.ZrevrangeWithscores withscores) {
        requireNonNull(key);
        final int len = 4 + (withscores == null ? 0 : 1);
        final int keyBytes = numberOfBytesUtf8(key);
        final int capacity = calculateInitialCommandBufferSize(len, RedisProtocolSupport.Command.ZREVRANGE) +
                    calculateRequestArgumentSize(keyBytes) + calculateRequestArgumentSize(start) +
                    calculateRequestArgumentSize(stop) + (withscores == null ? 0 : withscores.encodedByteCount());
        Buffer buffer = reservedCnx.executionContext().bufferAllocator().newBuffer(capacity);
        writeRequestArraySize(buffer, len);
        RedisProtocolSupport.Command.ZREVRANGE.encodeTo(buffer);
        writeRequestArgument(buffer, key, keyBytes);
        writeRequestArgument(buffer, start);
        writeRequestArgument(buffer, stop);
        if (withscores != null) {
            withscores.encodeTo(buffer);
        }
        final RedisRequest request = newRequest(RedisProtocolSupport.Command.ZREVRANGE, buffer);
        final Single<String> queued = reservedCnx.request(request, String.class);
        Future<List<T>> result = enqueueForExecute(state, singles, queued);
        return result;
    }

    @Override
    public <T> Future<List<T>> zrevrangebylex(@RedisProtocolSupport.Key final CharSequence key, final CharSequence max,
                                              final CharSequence min) {
        requireNonNull(key);
        requireNonNull(max);
        requireNonNull(min);
        final int len = 4;
        final int keyBytes = numberOfBytesUtf8(key);
        final int maxBytes = numberOfBytesUtf8(max);
        final int minBytes = numberOfBytesUtf8(min);
        final int capacity = calculateInitialCommandBufferSize(len, RedisProtocolSupport.Command.ZREVRANGEBYLEX) +
                    calculateRequestArgumentSize(keyBytes) + calculateRequestArgumentSize(maxBytes) +
                    calculateRequestArgumentSize(minBytes);
        Buffer buffer = reservedCnx.executionContext().bufferAllocator().newBuffer(capacity);
        writeRequestArraySize(buffer, len);
        RedisProtocolSupport.Command.ZREVRANGEBYLEX.encodeTo(buffer);
        writeRequestArgument(buffer, key, keyBytes);
        writeRequestArgument(buffer, max, maxBytes);
        writeRequestArgument(buffer, min, minBytes);
        final RedisRequest request = newRequest(RedisProtocolSupport.Command.ZREVRANGEBYLEX, buffer);
        final Single<String> queued = reservedCnx.request(request, String.class);
        Future<List<T>> result = enqueueForExecute(state, singles, queued);
        return result;
    }

    @Override
    public <T> Future<List<T>> zrevrangebylex(@RedisProtocolSupport.Key final CharSequence key, final CharSequence max,
                                              final CharSequence min,
                                              @Nullable final RedisProtocolSupport.OffsetCount offsetCount) {
        requireNonNull(key);
        requireNonNull(max);
        requireNonNull(min);
        final int len = 4 + (offsetCount == null ? 0 : RedisProtocolSupport.OffsetCount.SIZE);
        final int keyBytes = numberOfBytesUtf8(key);
        final int maxBytes = numberOfBytesUtf8(max);
        final int minBytes = numberOfBytesUtf8(min);
        final int capacity = calculateInitialCommandBufferSize(len, RedisProtocolSupport.Command.ZREVRANGEBYLEX) +
                    calculateRequestArgumentSize(keyBytes) + calculateRequestArgumentSize(maxBytes) +
                    calculateRequestArgumentSize(minBytes) + (offsetCount == null ? 0 : offsetCount.encodedByteCount());
        Buffer buffer = reservedCnx.executionContext().bufferAllocator().newBuffer(capacity);
        writeRequestArraySize(buffer, len);
        RedisProtocolSupport.Command.ZREVRANGEBYLEX.encodeTo(buffer);
        writeRequestArgument(buffer, key, keyBytes);
        writeRequestArgument(buffer, max, maxBytes);
        writeRequestArgument(buffer, min, minBytes);
        if (offsetCount != null) {
            offsetCount.encodeTo(buffer);
        }
        final RedisRequest request = newRequest(RedisProtocolSupport.Command.ZREVRANGEBYLEX, buffer);
        final Single<String> queued = reservedCnx.request(request, String.class);
        Future<List<T>> result = enqueueForExecute(state, singles, queued);
        return result;
    }

    @Override
    public <T> Future<List<T>> zrevrangebyscore(@RedisProtocolSupport.Key final CharSequence key, final double max,
                                                final double min) {
        requireNonNull(key);
        final int len = 4;
        final int keyBytes = numberOfBytesUtf8(key);
        final int capacity = calculateInitialCommandBufferSize(len, RedisProtocolSupport.Command.ZREVRANGEBYSCORE) +
                    calculateRequestArgumentSize(keyBytes) + calculateRequestArgumentSize(max) +
                    calculateRequestArgumentSize(min);
        Buffer buffer = reservedCnx.executionContext().bufferAllocator().newBuffer(capacity);
        writeRequestArraySize(buffer, len);
        RedisProtocolSupport.Command.ZREVRANGEBYSCORE.encodeTo(buffer);
        writeRequestArgument(buffer, key, keyBytes);
        writeRequestArgument(buffer, max);
        writeRequestArgument(buffer, min);
        final RedisRequest request = newRequest(RedisProtocolSupport.Command.ZREVRANGEBYSCORE, buffer);
        final Single<String> queued = reservedCnx.request(request, String.class);
        Future<List<T>> result = enqueueForExecute(state, singles, queued);
        return result;
    }

    @Override
    public <T> Future<List<T>> zrevrangebyscore(@RedisProtocolSupport.Key final CharSequence key, final double max,
                                                final double min,
                                                @Nullable final RedisProtocolSupport.ZrevrangebyscoreWithscores withscores,
                                                @Nullable final RedisProtocolSupport.OffsetCount offsetCount) {
        requireNonNull(key);
        final int len = 4 + (withscores == null ? 0 : 1) +
                    (offsetCount == null ? 0 : RedisProtocolSupport.OffsetCount.SIZE);
        final int keyBytes = numberOfBytesUtf8(key);
        final int capacity = calculateInitialCommandBufferSize(len, RedisProtocolSupport.Command.ZREVRANGEBYSCORE) +
                    calculateRequestArgumentSize(keyBytes) + calculateRequestArgumentSize(max) +
                    calculateRequestArgumentSize(min) + (withscores == null ? 0 : withscores.encodedByteCount()) +
                    (offsetCount == null ? 0 : offsetCount.encodedByteCount());
        Buffer buffer = reservedCnx.executionContext().bufferAllocator().newBuffer(capacity);
        writeRequestArraySize(buffer, len);
        RedisProtocolSupport.Command.ZREVRANGEBYSCORE.encodeTo(buffer);
        writeRequestArgument(buffer, key, keyBytes);
        writeRequestArgument(buffer, max);
        writeRequestArgument(buffer, min);
        if (withscores != null) {
            withscores.encodeTo(buffer);
        }
        if (offsetCount != null) {
            offsetCount.encodeTo(buffer);
        }
        final RedisRequest request = newRequest(RedisProtocolSupport.Command.ZREVRANGEBYSCORE, buffer);
        final Single<String> queued = reservedCnx.request(request, String.class);
        Future<List<T>> result = enqueueForExecute(state, singles, queued);
        return result;
    }

    @Override
    public Future<Long> zrevrank(@RedisProtocolSupport.Key final CharSequence key, final CharSequence member) {
        requireNonNull(key);
        requireNonNull(member);
        final int len = 3;
        final int keyBytes = numberOfBytesUtf8(key);
        final int memberBytes = numberOfBytesUtf8(member);
        final int capacity = calculateInitialCommandBufferSize(len, RedisProtocolSupport.Command.ZREVRANK) +
                    calculateRequestArgumentSize(keyBytes) + calculateRequestArgumentSize(memberBytes);
        Buffer buffer = reservedCnx.executionContext().bufferAllocator().newBuffer(capacity);
        writeRequestArraySize(buffer, len);
        RedisProtocolSupport.Command.ZREVRANK.encodeTo(buffer);
        writeRequestArgument(buffer, key, keyBytes);
        writeRequestArgument(buffer, member, memberBytes);
        final RedisRequest request = newRequest(RedisProtocolSupport.Command.ZREVRANK, buffer);
        final Single<String> queued = reservedCnx.request(request, String.class);
        Future<Long> result = enqueueForExecute(state, singles, queued);
        return result;
    }

    @Override
    public <T> Future<List<T>> zscan(@RedisProtocolSupport.Key final CharSequence key, final long cursor) {
        requireNonNull(key);
        final int len = 3;
        final int keyBytes = numberOfBytesUtf8(key);
        final int capacity = calculateInitialCommandBufferSize(len, RedisProtocolSupport.Command.ZSCAN) +
                    calculateRequestArgumentSize(keyBytes) + calculateRequestArgumentSize(cursor);
        Buffer buffer = reservedCnx.executionContext().bufferAllocator().newBuffer(capacity);
        writeRequestArraySize(buffer, len);
        RedisProtocolSupport.Command.ZSCAN.encodeTo(buffer);
        writeRequestArgument(buffer, key, keyBytes);
        writeRequestArgument(buffer, cursor);
        final RedisRequest request = newRequest(RedisProtocolSupport.Command.ZSCAN, buffer);
        final Single<String> queued = reservedCnx.request(request, String.class);
        Future<List<T>> result = enqueueForExecute(state, singles, queued);
        return result;
    }

    @Override
    public <T> Future<List<T>> zscan(@RedisProtocolSupport.Key final CharSequence key, final long cursor,
                                     @Nullable final CharSequence matchPattern, @Nullable final Long count) {
        requireNonNull(key);
        final int len = 3 + (matchPattern == null ? 0 : 2) + (count == null ? 0 : 2);
        final int keyBytes = numberOfBytesUtf8(key);
        final int matchPatternBytes = matchPattern == null ? 0 : numberOfBytesUtf8(matchPattern);
        final int capacity = calculateInitialCommandBufferSize(len, RedisProtocolSupport.Command.ZSCAN) +
                    calculateRequestArgumentSize(keyBytes) + calculateRequestArgumentSize(cursor) +
                    (matchPattern == null ? 0 : RedisProtocolSupport.SubCommand.MATCH.encodedByteCount()) +
                    (matchPattern == null ? 0 : calculateRequestArgumentSize(matchPatternBytes)) +
                    (count == null ? 0 : RedisProtocolSupport.SubCommand.COUNT.encodedByteCount()) +
                    (count == null ? 0 : calculateRequestArgumentSize(count));
        Buffer buffer = reservedCnx.executionContext().bufferAllocator().newBuffer(capacity);
        writeRequestArraySize(buffer, len);
        RedisProtocolSupport.Command.ZSCAN.encodeTo(buffer);
        writeRequestArgument(buffer, key, keyBytes);
        writeRequestArgument(buffer, cursor);
        if (matchPattern != null) {
            RedisProtocolSupport.SubCommand.MATCH.encodeTo(buffer);
            writeRequestArgument(buffer, matchPattern, matchPatternBytes);
        }
        if (count != null) {
            RedisProtocolSupport.SubCommand.COUNT.encodeTo(buffer);
            writeRequestArgument(buffer, count);
        }
        final RedisRequest request = newRequest(RedisProtocolSupport.Command.ZSCAN, buffer);
        final Single<String> queued = reservedCnx.request(request, String.class);
        Future<List<T>> result = enqueueForExecute(state, singles, queued);
        return result;
    }

    @Override
    public Future<Double> zscore(@RedisProtocolSupport.Key final CharSequence key, final CharSequence member) {
        requireNonNull(key);
        requireNonNull(member);
        final int len = 3;
        final int keyBytes = numberOfBytesUtf8(key);
        final int memberBytes = numberOfBytesUtf8(member);
        final int capacity = calculateInitialCommandBufferSize(len, RedisProtocolSupport.Command.ZSCORE) +
                    calculateRequestArgumentSize(keyBytes) + calculateRequestArgumentSize(memberBytes);
        Buffer buffer = reservedCnx.executionContext().bufferAllocator().newBuffer(capacity);
        writeRequestArraySize(buffer, len);
        RedisProtocolSupport.Command.ZSCORE.encodeTo(buffer);
        writeRequestArgument(buffer, key, keyBytes);
        writeRequestArgument(buffer, member, memberBytes);
        final RedisRequest request = newRequest(RedisProtocolSupport.Command.ZSCORE, buffer);
        final Single<String> queued = reservedCnx.request(request, String.class);
        Future<Double> result = enqueueForExecute(state, singles, queued);
        return result;
    }

    @Override
    public Future<Long> zunionstore(@RedisProtocolSupport.Key final CharSequence destination, final long numkeys,
                                    @RedisProtocolSupport.Key final Collection<? extends CharSequence> keys) {
        requireNonNull(destination);
        requireNonNull(keys);
        final int len = 3 + keys.size();
        final int destinationBytes = numberOfBytesUtf8(destination);
        int keysCapacity = 0;
        if (keys instanceof List && keys instanceof RandomAccess) {
            final List<CharSequence> list = (List<CharSequence>) keys;
            for (int i = 0; i < list.size(); ++i) {
                final CharSequence arg = list.get(i);
                keysCapacity += estimateRequestArgumentSize(arg);
            }
        } else {
            for (CharSequence arg : keys) {
                keysCapacity += estimateRequestArgumentSize(arg);
            }
        }
        final int capacity = calculateInitialCommandBufferSize(len, RedisProtocolSupport.Command.ZUNIONSTORE) +
                    calculateRequestArgumentSize(destinationBytes) + calculateRequestArgumentSize(numkeys) +
                    keysCapacity;
        Buffer buffer = reservedCnx.executionContext().bufferAllocator().newBuffer(capacity);
        writeRequestArraySize(buffer, len);
        RedisProtocolSupport.Command.ZUNIONSTORE.encodeTo(buffer);
        writeRequestArgument(buffer, destination, destinationBytes);
        writeRequestArgument(buffer, numkeys);
        if (keys instanceof List && keys instanceof RandomAccess) {
            final List<CharSequence> list = (List<CharSequence>) keys;
            for (int i = 0; i < list.size(); ++i) {
                final CharSequence arg = list.get(i);
                writeRequestArgument(buffer, arg);
            }
        } else {
            for (CharSequence arg : keys) {
                writeRequestArgument(buffer, arg);
            }
        }
        final RedisRequest request = newRequest(RedisProtocolSupport.Command.ZUNIONSTORE, buffer);
        final Single<String> queued = reservedCnx.request(request, String.class);
        Future<Long> result = enqueueForExecute(state, singles, queued);
        return result;
    }

    @Override
    public Future<Long> zunionstore(@RedisProtocolSupport.Key final CharSequence destination, final long numkeys,
                                    @RedisProtocolSupport.Key final Collection<? extends CharSequence> keys,
                                    final Collection<Long> weights,
                                    @Nullable final RedisProtocolSupport.ZunionstoreAggregate aggregate) {
        requireNonNull(destination);
        requireNonNull(keys);
        requireNonNull(weights);
        final int len = 4 + keys.size() + weights.size() + (aggregate == null ? 0 : 1);
        final int destinationBytes = numberOfBytesUtf8(destination);
        int keysCapacity = 0;
        if (keys instanceof List && keys instanceof RandomAccess) {
            final List<CharSequence> list = (List<CharSequence>) keys;
            for (int i = 0; i < list.size(); ++i) {
                final CharSequence arg = list.get(i);
                keysCapacity += estimateRequestArgumentSize(arg);
            }
        } else {
            for (CharSequence arg : keys) {
                keysCapacity += estimateRequestArgumentSize(arg);
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
                    calculateRequestArgumentSize(destinationBytes) + calculateRequestArgumentSize(numkeys) +
                    keysCapacity + RedisProtocolSupport.SubCommand.WEIGHTS.encodedByteCount() + weightsCapacity +
                    (aggregate == null ? 0 : aggregate.encodedByteCount());
        Buffer buffer = reservedCnx.executionContext().bufferAllocator().newBuffer(capacity);
        writeRequestArraySize(buffer, len);
        RedisProtocolSupport.Command.ZUNIONSTORE.encodeTo(buffer);
        writeRequestArgument(buffer, destination, destinationBytes);
        writeRequestArgument(buffer, numkeys);
        if (keys instanceof List && keys instanceof RandomAccess) {
            final List<CharSequence> list = (List<CharSequence>) keys;
            for (int i = 0; i < list.size(); ++i) {
                final CharSequence arg = list.get(i);
                writeRequestArgument(buffer, arg);
            }
        } else {
            for (CharSequence arg : keys) {
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
        final Single<String> queued = reservedCnx.request(request, String.class);
        Future<Long> result = enqueueForExecute(state, singles, queued);
        return result;
    }
}

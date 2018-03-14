/**
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

import io.servicetalk.buffer.BufferAllocator;
import io.servicetalk.buffer.CompositeBuffer;
import io.servicetalk.concurrent.api.Completable;
import io.servicetalk.concurrent.api.Publisher;
import io.servicetalk.concurrent.api.Single;
import io.servicetalk.redis.internal.RedisUtils;

import java.util.Collection;
import java.util.List;
import java.util.function.Function;
import javax.annotation.Generated;
import javax.annotation.Nullable;

import static io.servicetalk.redis.api.RedisRequests.addCharSequenceKeysToAttributeBuilder;
import static io.servicetalk.redis.api.RedisRequests.addRequestArgument;
import static io.servicetalk.redis.api.RedisRequests.addRequestCharSequenceArguments;
import static io.servicetalk.redis.api.RedisRequests.addRequestLongArguments;
import static io.servicetalk.redis.api.RedisRequests.addRequestTupleArguments;
import static io.servicetalk.redis.api.RedisRequests.newConnectedClient;
import static io.servicetalk.redis.api.RedisRequests.newRequest;
import static io.servicetalk.redis.api.RedisRequests.newRequestCompositeBuffer;
import static java.util.Objects.requireNonNull;
import static java.util.function.Function.identity;

@Generated({})
@SuppressWarnings("unchecked")
final class DefaultPartitionedRedisCommander implements RedisCommander {

    private final PartitionedRedisClient partitionedRedisClient;

    private final Function<RedisProtocolSupport.Command, RedisPartitionAttributesBuilder> partitionAttributesBuilderFunction;

    DefaultPartitionedRedisCommander(final PartitionedRedisClient partitionedRedisClient,
                final Function<RedisProtocolSupport.Command, RedisPartitionAttributesBuilder> partitionAttributesBuilderFunction) {
        this.partitionedRedisClient = requireNonNull(partitionedRedisClient);
        this.partitionAttributesBuilderFunction = requireNonNull(partitionAttributesBuilderFunction);
    }

    @Override
    public Completable closeAsync() {
        return partitionedRedisClient.closeAsync();
    }

    @Override
    public Single<Long> append(@RedisProtocolSupport.Key final CharSequence key, final CharSequence value) {
        requireNonNull(key);
        requireNonNull(value);
        final BufferAllocator allocator = partitionedRedisClient.getBufferAllocator();
        // Compute the number of request arguments, accounting for nullable ones
        int len = 3;
        final CompositeBuffer cb = newRequestCompositeBuffer(len, RedisProtocolSupport.Command.APPEND, allocator);
        addRequestArgument(key, cb, allocator);
        addRequestArgument(value, cb, allocator);
        final RedisRequest request = newRequest(RedisProtocolSupport.Command.APPEND, cb);
        final RedisPartitionAttributesBuilder partitionAttributesBuilder = partitionAttributesBuilderFunction
                    .apply(RedisProtocolSupport.Command.APPEND);
        partitionAttributesBuilder.addKey(key);
        final Single<Long> result = partitionedRedisClient.request(partitionAttributesBuilder.build(), request, Long.class);
        return result;
    }

    @Override
    public Single<String> auth(final CharSequence password) {
        requireNonNull(password);
        final BufferAllocator allocator = partitionedRedisClient.getBufferAllocator();
        // Compute the number of request arguments, accounting for nullable ones
        int len = 2;
        final CompositeBuffer cb = newRequestCompositeBuffer(len, RedisProtocolSupport.Command.AUTH, allocator);
        addRequestArgument(password, cb, allocator);
        final RedisRequest request = newRequest(RedisProtocolSupport.Command.AUTH, cb);
        final RedisPartitionAttributesBuilder partitionAttributesBuilder = partitionAttributesBuilderFunction
                    .apply(RedisProtocolSupport.Command.AUTH);
        final Single<String> result = partitionedRedisClient.request(partitionAttributesBuilder.build(), request, String.class);
        return result;
    }

    @Override
    public Single<String> bgrewriteaof() {
        final BufferAllocator allocator = partitionedRedisClient.getBufferAllocator();
        // Compute the number of request arguments, accounting for nullable ones
        int len = 1;
        final CompositeBuffer cb = newRequestCompositeBuffer(len, RedisProtocolSupport.Command.BGREWRITEAOF, allocator);
        final RedisRequest request = newRequest(RedisProtocolSupport.Command.BGREWRITEAOF, cb);
        final RedisPartitionAttributesBuilder partitionAttributesBuilder = partitionAttributesBuilderFunction
                    .apply(RedisProtocolSupport.Command.BGREWRITEAOF);
        final Single<String> result = partitionedRedisClient.request(partitionAttributesBuilder.build(), request, String.class);
        return result;
    }

    @Override
    public Single<String> bgsave() {
        final BufferAllocator allocator = partitionedRedisClient.getBufferAllocator();
        // Compute the number of request arguments, accounting for nullable ones
        int len = 1;
        final CompositeBuffer cb = newRequestCompositeBuffer(len, RedisProtocolSupport.Command.BGSAVE, allocator);
        final RedisRequest request = newRequest(RedisProtocolSupport.Command.BGSAVE, cb);
        final RedisPartitionAttributesBuilder partitionAttributesBuilder = partitionAttributesBuilderFunction
                    .apply(RedisProtocolSupport.Command.BGSAVE);
        final Single<String> result = partitionedRedisClient.request(partitionAttributesBuilder.build(), request, String.class);
        return result;
    }

    @Override
    public Single<Long> bitcount(@RedisProtocolSupport.Key final CharSequence key) {
        requireNonNull(key);
        final BufferAllocator allocator = partitionedRedisClient.getBufferAllocator();
        // Compute the number of request arguments, accounting for nullable ones
        int len = 2;
        final CompositeBuffer cb = newRequestCompositeBuffer(len, RedisProtocolSupport.Command.BITCOUNT, allocator);
        addRequestArgument(key, cb, allocator);
        final RedisRequest request = newRequest(RedisProtocolSupport.Command.BITCOUNT, cb);
        final RedisPartitionAttributesBuilder partitionAttributesBuilder = partitionAttributesBuilderFunction
                    .apply(RedisProtocolSupport.Command.BITCOUNT);
        partitionAttributesBuilder.addKey(key);
        final Single<Long> result = partitionedRedisClient.request(partitionAttributesBuilder.build(), request, Long.class);
        return result;
    }

    @Override
    public Single<Long> bitcount(@RedisProtocolSupport.Key final CharSequence key, @Nullable final Long start,
                                 @Nullable final Long end) {
        requireNonNull(key);
        final BufferAllocator allocator = partitionedRedisClient.getBufferAllocator();
        // Compute the number of request arguments, accounting for nullable ones
        int len = 2;
        if (start != null) {
            len++;
        }
        if (end != null) {
            len++;
        }
        final CompositeBuffer cb = newRequestCompositeBuffer(len, RedisProtocolSupport.Command.BITCOUNT, allocator);
        addRequestArgument(key, cb, allocator);
        if (start != null) {
            addRequestArgument(start, cb, allocator);
        }
        if (end != null) {
            addRequestArgument(end, cb, allocator);
        }
        final RedisRequest request = newRequest(RedisProtocolSupport.Command.BITCOUNT, cb);
        final RedisPartitionAttributesBuilder partitionAttributesBuilder = partitionAttributesBuilderFunction
                    .apply(RedisProtocolSupport.Command.BITCOUNT);
        partitionAttributesBuilder.addKey(key);
        final Single<Long> result = partitionedRedisClient.request(partitionAttributesBuilder.build(), request, Long.class);
        return result;
    }

    @Override
    public Single<List<Long>> bitfield(@RedisProtocolSupport.Key final CharSequence key,
                                       final Collection<RedisProtocolSupport.BitfieldOperation> operations) {
        requireNonNull(key);
        requireNonNull(operations);
        final BufferAllocator allocator = partitionedRedisClient.getBufferAllocator();
        final CompositeBuffer cbOps = allocator.newCompositeBuffer();
        final int len = 2 + operations.stream().mapToInt(op -> op.writeTo(cbOps, allocator)).sum();
        final CompositeBuffer cb = newRequestCompositeBuffer(len, RedisProtocolSupport.Command.BITFIELD, allocator);
        addRequestArgument(key, cb, allocator);
        cb.addBuffer(cbOps);
        final RedisRequest request = newRequest(RedisProtocolSupport.Command.BITFIELD, cb);
        final RedisPartitionAttributesBuilder partitionAttributesBuilder = partitionAttributesBuilderFunction
                    .apply(RedisProtocolSupport.Command.BITFIELD);
        partitionAttributesBuilder.addKey(key);
        final Single<List<Long>> result = (Single) partitionedRedisClient.request(partitionAttributesBuilder.build(), request,
                    RedisUtils.ListWithBuffersCoercedToCharSequences.class);
        return result;
    }

    @Override
    public Single<Long> bitop(final CharSequence operation, @RedisProtocolSupport.Key final CharSequence destkey,
                              @RedisProtocolSupport.Key final CharSequence key) {
        requireNonNull(operation);
        requireNonNull(destkey);
        requireNonNull(key);
        final BufferAllocator allocator = partitionedRedisClient.getBufferAllocator();
        // Compute the number of request arguments, accounting for nullable ones
        int len = 4;
        final CompositeBuffer cb = newRequestCompositeBuffer(len, RedisProtocolSupport.Command.BITOP, allocator);
        addRequestArgument(operation, cb, allocator);
        addRequestArgument(destkey, cb, allocator);
        addRequestArgument(key, cb, allocator);
        final RedisRequest request = newRequest(RedisProtocolSupport.Command.BITOP, cb);
        final RedisPartitionAttributesBuilder partitionAttributesBuilder = partitionAttributesBuilderFunction
                    .apply(RedisProtocolSupport.Command.BITOP);
        partitionAttributesBuilder.addKey(destkey);
        partitionAttributesBuilder.addKey(key);
        final Single<Long> result = partitionedRedisClient.request(partitionAttributesBuilder.build(), request, Long.class);
        return result;
    }

    @Override
    public Single<Long> bitop(final CharSequence operation, @RedisProtocolSupport.Key final CharSequence destkey,
                              @RedisProtocolSupport.Key final CharSequence key1,
                              @RedisProtocolSupport.Key final CharSequence key2) {
        requireNonNull(operation);
        requireNonNull(destkey);
        requireNonNull(key1);
        requireNonNull(key2);
        final BufferAllocator allocator = partitionedRedisClient.getBufferAllocator();
        // Compute the number of request arguments, accounting for nullable ones
        int len = 5;
        final CompositeBuffer cb = newRequestCompositeBuffer(len, RedisProtocolSupport.Command.BITOP, allocator);
        addRequestArgument(operation, cb, allocator);
        addRequestArgument(destkey, cb, allocator);
        addRequestArgument(key1, cb, allocator);
        addRequestArgument(key2, cb, allocator);
        final RedisRequest request = newRequest(RedisProtocolSupport.Command.BITOP, cb);
        final RedisPartitionAttributesBuilder partitionAttributesBuilder = partitionAttributesBuilderFunction
                    .apply(RedisProtocolSupport.Command.BITOP);
        partitionAttributesBuilder.addKey(destkey);
        partitionAttributesBuilder.addKey(key1);
        partitionAttributesBuilder.addKey(key2);
        final Single<Long> result = partitionedRedisClient.request(partitionAttributesBuilder.build(), request, Long.class);
        return result;
    }

    @Override
    public Single<Long> bitop(final CharSequence operation, @RedisProtocolSupport.Key final CharSequence destkey,
                              @RedisProtocolSupport.Key final CharSequence key1,
                              @RedisProtocolSupport.Key final CharSequence key2,
                              @RedisProtocolSupport.Key final CharSequence key3) {
        requireNonNull(operation);
        requireNonNull(destkey);
        requireNonNull(key1);
        requireNonNull(key2);
        requireNonNull(key3);
        final BufferAllocator allocator = partitionedRedisClient.getBufferAllocator();
        // Compute the number of request arguments, accounting for nullable ones
        int len = 6;
        final CompositeBuffer cb = newRequestCompositeBuffer(len, RedisProtocolSupport.Command.BITOP, allocator);
        addRequestArgument(operation, cb, allocator);
        addRequestArgument(destkey, cb, allocator);
        addRequestArgument(key1, cb, allocator);
        addRequestArgument(key2, cb, allocator);
        addRequestArgument(key3, cb, allocator);
        final RedisRequest request = newRequest(RedisProtocolSupport.Command.BITOP, cb);
        final RedisPartitionAttributesBuilder partitionAttributesBuilder = partitionAttributesBuilderFunction
                    .apply(RedisProtocolSupport.Command.BITOP);
        partitionAttributesBuilder.addKey(destkey);
        partitionAttributesBuilder.addKey(key1);
        partitionAttributesBuilder.addKey(key2);
        partitionAttributesBuilder.addKey(key3);
        final Single<Long> result = partitionedRedisClient.request(partitionAttributesBuilder.build(), request, Long.class);
        return result;
    }

    @Override
    public Single<Long> bitop(final CharSequence operation, @RedisProtocolSupport.Key final CharSequence destkey,
                              @RedisProtocolSupport.Key final Collection<? extends CharSequence> keys) {
        requireNonNull(operation);
        requireNonNull(destkey);
        requireNonNull(keys);
        final BufferAllocator allocator = partitionedRedisClient.getBufferAllocator();
        // Compute the number of request arguments, accounting for nullable ones
        int len = 3;
        len += keys.size();
        final CompositeBuffer cb = newRequestCompositeBuffer(len, RedisProtocolSupport.Command.BITOP, allocator);
        addRequestArgument(operation, cb, allocator);
        addRequestArgument(destkey, cb, allocator);
        addRequestCharSequenceArguments(keys, null, cb, allocator);
        final RedisRequest request = newRequest(RedisProtocolSupport.Command.BITOP, cb);
        final RedisPartitionAttributesBuilder partitionAttributesBuilder = partitionAttributesBuilderFunction
                    .apply(RedisProtocolSupport.Command.BITOP);
        partitionAttributesBuilder.addKey(destkey);
        addCharSequenceKeysToAttributeBuilder(keys, partitionAttributesBuilder);
        final Single<Long> result = partitionedRedisClient.request(partitionAttributesBuilder.build(), request, Long.class);
        return result;
    }

    @Override
    public Single<Long> bitpos(@RedisProtocolSupport.Key final CharSequence key, final long bit) {
        requireNonNull(key);
        final BufferAllocator allocator = partitionedRedisClient.getBufferAllocator();
        // Compute the number of request arguments, accounting for nullable ones
        int len = 3;
        final CompositeBuffer cb = newRequestCompositeBuffer(len, RedisProtocolSupport.Command.BITPOS, allocator);
        addRequestArgument(key, cb, allocator);
        addRequestArgument(bit, cb, allocator);
        final RedisRequest request = newRequest(RedisProtocolSupport.Command.BITPOS, cb);
        final RedisPartitionAttributesBuilder partitionAttributesBuilder = partitionAttributesBuilderFunction
                    .apply(RedisProtocolSupport.Command.BITPOS);
        partitionAttributesBuilder.addKey(key);
        final Single<Long> result = partitionedRedisClient.request(partitionAttributesBuilder.build(), request, Long.class);
        return result;
    }

    @Override
    public Single<Long> bitpos(@RedisProtocolSupport.Key final CharSequence key, final long bit, @Nullable final Long start,
                               @Nullable final Long end) {
        requireNonNull(key);
        final BufferAllocator allocator = partitionedRedisClient.getBufferAllocator();
        // Compute the number of request arguments, accounting for nullable ones
        int len = 3;
        if (start != null) {
            len++;
        }
        if (end != null) {
            len++;
        }
        final CompositeBuffer cb = newRequestCompositeBuffer(len, RedisProtocolSupport.Command.BITPOS, allocator);
        addRequestArgument(key, cb, allocator);
        addRequestArgument(bit, cb, allocator);
        if (start != null) {
            addRequestArgument(start, cb, allocator);
        }
        if (end != null) {
            addRequestArgument(end, cb, allocator);
        }
        final RedisRequest request = newRequest(RedisProtocolSupport.Command.BITPOS, cb);
        final RedisPartitionAttributesBuilder partitionAttributesBuilder = partitionAttributesBuilderFunction
                    .apply(RedisProtocolSupport.Command.BITPOS);
        partitionAttributesBuilder.addKey(key);
        final Single<Long> result = partitionedRedisClient.request(partitionAttributesBuilder.build(), request, Long.class);
        return result;
    }

    @Override
    public <T> Single<List<T>> blpop(@RedisProtocolSupport.Key final Collection<? extends CharSequence> keys,
                                     final long timeout) {
        requireNonNull(keys);
        final BufferAllocator allocator = partitionedRedisClient.getBufferAllocator();
        // Compute the number of request arguments, accounting for nullable ones
        int len = 2;
        len += keys.size();
        final CompositeBuffer cb = newRequestCompositeBuffer(len, RedisProtocolSupport.Command.BLPOP, allocator);
        addRequestCharSequenceArguments(keys, null, cb, allocator);
        addRequestArgument(timeout, cb, allocator);
        final RedisRequest request = newRequest(RedisProtocolSupport.Command.BLPOP, cb);
        final RedisPartitionAttributesBuilder partitionAttributesBuilder = partitionAttributesBuilderFunction
                    .apply(RedisProtocolSupport.Command.BLPOP);
        addCharSequenceKeysToAttributeBuilder(keys, partitionAttributesBuilder);
        final Single<List<T>> result = (Single) partitionedRedisClient.request(partitionAttributesBuilder.build(), request,
                    RedisUtils.ListWithBuffersCoercedToCharSequences.class);
        return result;
    }

    @Override
    public <T> Single<List<T>> brpop(@RedisProtocolSupport.Key final Collection<? extends CharSequence> keys,
                                     final long timeout) {
        requireNonNull(keys);
        final BufferAllocator allocator = partitionedRedisClient.getBufferAllocator();
        // Compute the number of request arguments, accounting for nullable ones
        int len = 2;
        len += keys.size();
        final CompositeBuffer cb = newRequestCompositeBuffer(len, RedisProtocolSupport.Command.BRPOP, allocator);
        addRequestCharSequenceArguments(keys, null, cb, allocator);
        addRequestArgument(timeout, cb, allocator);
        final RedisRequest request = newRequest(RedisProtocolSupport.Command.BRPOP, cb);
        final RedisPartitionAttributesBuilder partitionAttributesBuilder = partitionAttributesBuilderFunction
                    .apply(RedisProtocolSupport.Command.BRPOP);
        addCharSequenceKeysToAttributeBuilder(keys, partitionAttributesBuilder);
        final Single<List<T>> result = (Single) partitionedRedisClient.request(partitionAttributesBuilder.build(), request,
                    RedisUtils.ListWithBuffersCoercedToCharSequences.class);
        return result;
    }

    @Override
    public Single<String> brpoplpush(@RedisProtocolSupport.Key final CharSequence source,
                                     @RedisProtocolSupport.Key final CharSequence destination, final long timeout) {
        requireNonNull(source);
        requireNonNull(destination);
        final BufferAllocator allocator = partitionedRedisClient.getBufferAllocator();
        // Compute the number of request arguments, accounting for nullable ones
        int len = 4;
        final CompositeBuffer cb = newRequestCompositeBuffer(len, RedisProtocolSupport.Command.BRPOPLPUSH, allocator);
        addRequestArgument(source, cb, allocator);
        addRequestArgument(destination, cb, allocator);
        addRequestArgument(timeout, cb, allocator);
        final RedisRequest request = newRequest(RedisProtocolSupport.Command.BRPOPLPUSH, cb);
        final RedisPartitionAttributesBuilder partitionAttributesBuilder = partitionAttributesBuilderFunction
                    .apply(RedisProtocolSupport.Command.BRPOPLPUSH);
        partitionAttributesBuilder.addKey(source);
        partitionAttributesBuilder.addKey(destination);
        final Single<String> result = partitionedRedisClient.request(partitionAttributesBuilder.build(), request, String.class);
        return result;
    }

    @Override
    public Single<Long> clientKill(@Nullable final Long id, @Nullable final RedisProtocolSupport.ClientKillType type,
                                   @Nullable final CharSequence addrIpPort, @Nullable final CharSequence skipmeYesNo) {
        final BufferAllocator allocator = partitionedRedisClient.getBufferAllocator();
        // Compute the number of request arguments, accounting for nullable ones
        int len = 2;
        if (id != null) {
            len += 2;
        }
        if (type != null) {
            len++;
        }
        if (addrIpPort != null) {
            len += 2;
        }
        if (skipmeYesNo != null) {
            len += 2;
        }
        final CompositeBuffer cb = newRequestCompositeBuffer(len, RedisProtocolSupport.Command.CLIENT,
                    RedisProtocolSupport.SubCommand.KILL, allocator);
        if (id != null) {
            addRequestArgument(RedisProtocolSupport.SubCommand.ID, cb, allocator);
            addRequestArgument(id, cb, allocator);
        }
        if (type != null) {
            addRequestArgument(type, cb, allocator);
        }
        if (addrIpPort != null) {
            addRequestArgument(RedisProtocolSupport.SubCommand.ADDR, cb, allocator);
            addRequestArgument(addrIpPort, cb, allocator);
        }
        if (skipmeYesNo != null) {
            addRequestArgument(RedisProtocolSupport.SubCommand.SKIPME, cb, allocator);
            addRequestArgument(skipmeYesNo, cb, allocator);
        }
        final RedisRequest request = newRequest(RedisProtocolSupport.Command.CLIENT, cb);
        final RedisPartitionAttributesBuilder partitionAttributesBuilder = partitionAttributesBuilderFunction
                    .apply(RedisProtocolSupport.Command.CLIENT);
        final Single<Long> result = partitionedRedisClient.request(partitionAttributesBuilder.build(), request, Long.class);
        return result;
    }

    @Override
    public Single<String> clientList() {
        final BufferAllocator allocator = partitionedRedisClient.getBufferAllocator();
        // Compute the number of request arguments, accounting for nullable ones
        int len = 2;
        final CompositeBuffer cb = newRequestCompositeBuffer(len, RedisProtocolSupport.Command.CLIENT,
                    RedisProtocolSupport.SubCommand.LIST, allocator);
        final RedisRequest request = newRequest(RedisProtocolSupport.Command.CLIENT, cb);
        final RedisPartitionAttributesBuilder partitionAttributesBuilder = partitionAttributesBuilderFunction
                    .apply(RedisProtocolSupport.Command.CLIENT);
        final Single<String> result = partitionedRedisClient.request(partitionAttributesBuilder.build(), request, String.class);
        return result;
    }

    @Override
    public Single<String> clientGetname() {
        final BufferAllocator allocator = partitionedRedisClient.getBufferAllocator();
        // Compute the number of request arguments, accounting for nullable ones
        int len = 2;
        final CompositeBuffer cb = newRequestCompositeBuffer(len, RedisProtocolSupport.Command.CLIENT,
                    RedisProtocolSupport.SubCommand.GETNAME, allocator);
        final RedisRequest request = newRequest(RedisProtocolSupport.Command.CLIENT, cb);
        final RedisPartitionAttributesBuilder partitionAttributesBuilder = partitionAttributesBuilderFunction
                    .apply(RedisProtocolSupport.Command.CLIENT);
        final Single<String> result = partitionedRedisClient.request(partitionAttributesBuilder.build(), request, String.class);
        return result;
    }

    @Override
    public Single<String> clientPause(final long timeout) {
        final BufferAllocator allocator = partitionedRedisClient.getBufferAllocator();
        // Compute the number of request arguments, accounting for nullable ones
        int len = 3;
        final CompositeBuffer cb = newRequestCompositeBuffer(len, RedisProtocolSupport.Command.CLIENT,
                    RedisProtocolSupport.SubCommand.PAUSE, allocator);
        addRequestArgument(timeout, cb, allocator);
        final RedisRequest request = newRequest(RedisProtocolSupport.Command.CLIENT, cb);
        final RedisPartitionAttributesBuilder partitionAttributesBuilder = partitionAttributesBuilderFunction
                    .apply(RedisProtocolSupport.Command.CLIENT);
        final Single<String> result = partitionedRedisClient.request(partitionAttributesBuilder.build(), request, String.class);
        return result;
    }

    @Override
    public Single<String> clientReply(final RedisProtocolSupport.ClientReplyReplyMode replyMode) {
        requireNonNull(replyMode);
        final BufferAllocator allocator = partitionedRedisClient.getBufferAllocator();
        // Compute the number of request arguments, accounting for nullable ones
        int len = 3;
        final CompositeBuffer cb = newRequestCompositeBuffer(len, RedisProtocolSupport.Command.CLIENT,
                    RedisProtocolSupport.SubCommand.REPLY, allocator);
        addRequestArgument(replyMode, cb, allocator);
        final RedisRequest request = newRequest(RedisProtocolSupport.Command.CLIENT, cb);
        final RedisPartitionAttributesBuilder partitionAttributesBuilder = partitionAttributesBuilderFunction
                    .apply(RedisProtocolSupport.Command.CLIENT);
        final Single<String> result = partitionedRedisClient.request(partitionAttributesBuilder.build(), request, String.class);
        return result;
    }

    @Override
    public Single<String> clientSetname(final CharSequence connectionName) {
        requireNonNull(connectionName);
        final BufferAllocator allocator = partitionedRedisClient.getBufferAllocator();
        // Compute the number of request arguments, accounting for nullable ones
        int len = 3;
        final CompositeBuffer cb = newRequestCompositeBuffer(len, RedisProtocolSupport.Command.CLIENT,
                    RedisProtocolSupport.SubCommand.SETNAME, allocator);
        addRequestArgument(connectionName, cb, allocator);
        final RedisRequest request = newRequest(RedisProtocolSupport.Command.CLIENT, cb);
        final RedisPartitionAttributesBuilder partitionAttributesBuilder = partitionAttributesBuilderFunction
                    .apply(RedisProtocolSupport.Command.CLIENT);
        final Single<String> result = partitionedRedisClient.request(partitionAttributesBuilder.build(), request, String.class);
        return result;
    }

    @Override
    public Single<String> clusterAddslots(final long slot) {
        final BufferAllocator allocator = partitionedRedisClient.getBufferAllocator();
        // Compute the number of request arguments, accounting for nullable ones
        int len = 3;
        final CompositeBuffer cb = newRequestCompositeBuffer(len, RedisProtocolSupport.Command.CLUSTER,
                    RedisProtocolSupport.SubCommand.ADDSLOTS, allocator);
        addRequestArgument(slot, cb, allocator);
        final RedisRequest request = newRequest(RedisProtocolSupport.Command.CLUSTER, cb);
        final RedisPartitionAttributesBuilder partitionAttributesBuilder = partitionAttributesBuilderFunction
                    .apply(RedisProtocolSupport.Command.CLUSTER);
        final Single<String> result = partitionedRedisClient.request(partitionAttributesBuilder.build(), request, String.class);
        return result;
    }

    @Override
    public Single<String> clusterAddslots(final long slot1, final long slot2) {
        final BufferAllocator allocator = partitionedRedisClient.getBufferAllocator();
        // Compute the number of request arguments, accounting for nullable ones
        int len = 4;
        final CompositeBuffer cb = newRequestCompositeBuffer(len, RedisProtocolSupport.Command.CLUSTER,
                    RedisProtocolSupport.SubCommand.ADDSLOTS, allocator);
        addRequestArgument(slot1, cb, allocator);
        addRequestArgument(slot2, cb, allocator);
        final RedisRequest request = newRequest(RedisProtocolSupport.Command.CLUSTER, cb);
        final RedisPartitionAttributesBuilder partitionAttributesBuilder = partitionAttributesBuilderFunction
                    .apply(RedisProtocolSupport.Command.CLUSTER);
        final Single<String> result = partitionedRedisClient.request(partitionAttributesBuilder.build(), request, String.class);
        return result;
    }

    @Override
    public Single<String> clusterAddslots(final long slot1, final long slot2, final long slot3) {
        final BufferAllocator allocator = partitionedRedisClient.getBufferAllocator();
        // Compute the number of request arguments, accounting for nullable ones
        int len = 5;
        final CompositeBuffer cb = newRequestCompositeBuffer(len, RedisProtocolSupport.Command.CLUSTER,
                    RedisProtocolSupport.SubCommand.ADDSLOTS, allocator);
        addRequestArgument(slot1, cb, allocator);
        addRequestArgument(slot2, cb, allocator);
        addRequestArgument(slot3, cb, allocator);
        final RedisRequest request = newRequest(RedisProtocolSupport.Command.CLUSTER, cb);
        final RedisPartitionAttributesBuilder partitionAttributesBuilder = partitionAttributesBuilderFunction
                    .apply(RedisProtocolSupport.Command.CLUSTER);
        final Single<String> result = partitionedRedisClient.request(partitionAttributesBuilder.build(), request, String.class);
        return result;
    }

    @Override
    public Single<String> clusterAddslots(final Collection<Long> slots) {
        requireNonNull(slots);
        final BufferAllocator allocator = partitionedRedisClient.getBufferAllocator();
        // Compute the number of request arguments, accounting for nullable ones
        int len = 2;
        len += slots.size();
        final CompositeBuffer cb = newRequestCompositeBuffer(len, RedisProtocolSupport.Command.CLUSTER,
                    RedisProtocolSupport.SubCommand.ADDSLOTS, allocator);
        addRequestLongArguments(slots, null, cb, allocator);
        final RedisRequest request = newRequest(RedisProtocolSupport.Command.CLUSTER, cb);
        final RedisPartitionAttributesBuilder partitionAttributesBuilder = partitionAttributesBuilderFunction
                    .apply(RedisProtocolSupport.Command.CLUSTER);
        final Single<String> result = partitionedRedisClient.request(partitionAttributesBuilder.build(), request, String.class);
        return result;
    }

    @Override
    public Single<Long> clusterCountFailureReports(final CharSequence nodeId) {
        requireNonNull(nodeId);
        final BufferAllocator allocator = partitionedRedisClient.getBufferAllocator();
        // Compute the number of request arguments, accounting for nullable ones
        int len = 3;
        final CompositeBuffer cb = newRequestCompositeBuffer(len, RedisProtocolSupport.Command.CLUSTER,
                    RedisProtocolSupport.SubCommand.COUNT_FAILURE_REPORTS, allocator);
        addRequestArgument(nodeId, cb, allocator);
        final RedisRequest request = newRequest(RedisProtocolSupport.Command.CLUSTER, cb);
        final RedisPartitionAttributesBuilder partitionAttributesBuilder = partitionAttributesBuilderFunction
                    .apply(RedisProtocolSupport.Command.CLUSTER);
        final Single<Long> result = partitionedRedisClient.request(partitionAttributesBuilder.build(), request, Long.class);
        return result;
    }

    @Override
    public Single<Long> clusterCountkeysinslot(final long slot) {
        final BufferAllocator allocator = partitionedRedisClient.getBufferAllocator();
        // Compute the number of request arguments, accounting for nullable ones
        int len = 3;
        final CompositeBuffer cb = newRequestCompositeBuffer(len, RedisProtocolSupport.Command.CLUSTER,
                    RedisProtocolSupport.SubCommand.COUNTKEYSINSLOT, allocator);
        addRequestArgument(slot, cb, allocator);
        final RedisRequest request = newRequest(RedisProtocolSupport.Command.CLUSTER, cb);
        final RedisPartitionAttributesBuilder partitionAttributesBuilder = partitionAttributesBuilderFunction
                    .apply(RedisProtocolSupport.Command.CLUSTER);
        final Single<Long> result = partitionedRedisClient.request(partitionAttributesBuilder.build(), request, Long.class);
        return result;
    }

    @Override
    public Single<String> clusterDelslots(final long slot) {
        final BufferAllocator allocator = partitionedRedisClient.getBufferAllocator();
        // Compute the number of request arguments, accounting for nullable ones
        int len = 3;
        final CompositeBuffer cb = newRequestCompositeBuffer(len, RedisProtocolSupport.Command.CLUSTER,
                    RedisProtocolSupport.SubCommand.DELSLOTS, allocator);
        addRequestArgument(slot, cb, allocator);
        final RedisRequest request = newRequest(RedisProtocolSupport.Command.CLUSTER, cb);
        final RedisPartitionAttributesBuilder partitionAttributesBuilder = partitionAttributesBuilderFunction
                    .apply(RedisProtocolSupport.Command.CLUSTER);
        final Single<String> result = partitionedRedisClient.request(partitionAttributesBuilder.build(), request, String.class);
        return result;
    }

    @Override
    public Single<String> clusterDelslots(final long slot1, final long slot2) {
        final BufferAllocator allocator = partitionedRedisClient.getBufferAllocator();
        // Compute the number of request arguments, accounting for nullable ones
        int len = 4;
        final CompositeBuffer cb = newRequestCompositeBuffer(len, RedisProtocolSupport.Command.CLUSTER,
                    RedisProtocolSupport.SubCommand.DELSLOTS, allocator);
        addRequestArgument(slot1, cb, allocator);
        addRequestArgument(slot2, cb, allocator);
        final RedisRequest request = newRequest(RedisProtocolSupport.Command.CLUSTER, cb);
        final RedisPartitionAttributesBuilder partitionAttributesBuilder = partitionAttributesBuilderFunction
                    .apply(RedisProtocolSupport.Command.CLUSTER);
        final Single<String> result = partitionedRedisClient.request(partitionAttributesBuilder.build(), request, String.class);
        return result;
    }

    @Override
    public Single<String> clusterDelslots(final long slot1, final long slot2, final long slot3) {
        final BufferAllocator allocator = partitionedRedisClient.getBufferAllocator();
        // Compute the number of request arguments, accounting for nullable ones
        int len = 5;
        final CompositeBuffer cb = newRequestCompositeBuffer(len, RedisProtocolSupport.Command.CLUSTER,
                    RedisProtocolSupport.SubCommand.DELSLOTS, allocator);
        addRequestArgument(slot1, cb, allocator);
        addRequestArgument(slot2, cb, allocator);
        addRequestArgument(slot3, cb, allocator);
        final RedisRequest request = newRequest(RedisProtocolSupport.Command.CLUSTER, cb);
        final RedisPartitionAttributesBuilder partitionAttributesBuilder = partitionAttributesBuilderFunction
                    .apply(RedisProtocolSupport.Command.CLUSTER);
        final Single<String> result = partitionedRedisClient.request(partitionAttributesBuilder.build(), request, String.class);
        return result;
    }

    @Override
    public Single<String> clusterDelslots(final Collection<Long> slots) {
        requireNonNull(slots);
        final BufferAllocator allocator = partitionedRedisClient.getBufferAllocator();
        // Compute the number of request arguments, accounting for nullable ones
        int len = 2;
        len += slots.size();
        final CompositeBuffer cb = newRequestCompositeBuffer(len, RedisProtocolSupport.Command.CLUSTER,
                    RedisProtocolSupport.SubCommand.DELSLOTS, allocator);
        addRequestLongArguments(slots, null, cb, allocator);
        final RedisRequest request = newRequest(RedisProtocolSupport.Command.CLUSTER, cb);
        final RedisPartitionAttributesBuilder partitionAttributesBuilder = partitionAttributesBuilderFunction
                    .apply(RedisProtocolSupport.Command.CLUSTER);
        final Single<String> result = partitionedRedisClient.request(partitionAttributesBuilder.build(), request, String.class);
        return result;
    }

    @Override
    public Single<String> clusterFailover() {
        final BufferAllocator allocator = partitionedRedisClient.getBufferAllocator();
        // Compute the number of request arguments, accounting for nullable ones
        int len = 2;
        final CompositeBuffer cb = newRequestCompositeBuffer(len, RedisProtocolSupport.Command.CLUSTER,
                    RedisProtocolSupport.SubCommand.FAILOVER, allocator);
        final RedisRequest request = newRequest(RedisProtocolSupport.Command.CLUSTER, cb);
        final RedisPartitionAttributesBuilder partitionAttributesBuilder = partitionAttributesBuilderFunction
                    .apply(RedisProtocolSupport.Command.CLUSTER);
        final Single<String> result = partitionedRedisClient.request(partitionAttributesBuilder.build(), request, String.class);
        return result;
    }

    @Override
    public Single<String> clusterFailover(@Nullable final RedisProtocolSupport.ClusterFailoverOptions options) {
        final BufferAllocator allocator = partitionedRedisClient.getBufferAllocator();
        // Compute the number of request arguments, accounting for nullable ones
        int len = 2;
        if (options != null) {
            len++;
        }
        final CompositeBuffer cb = newRequestCompositeBuffer(len, RedisProtocolSupport.Command.CLUSTER,
                    RedisProtocolSupport.SubCommand.FAILOVER, allocator);
        if (options != null) {
            addRequestArgument(options, cb, allocator);
        }
        final RedisRequest request = newRequest(RedisProtocolSupport.Command.CLUSTER, cb);
        final RedisPartitionAttributesBuilder partitionAttributesBuilder = partitionAttributesBuilderFunction
                    .apply(RedisProtocolSupport.Command.CLUSTER);
        final Single<String> result = partitionedRedisClient.request(partitionAttributesBuilder.build(), request, String.class);
        return result;
    }

    @Override
    public Single<String> clusterForget(final CharSequence nodeId) {
        requireNonNull(nodeId);
        final BufferAllocator allocator = partitionedRedisClient.getBufferAllocator();
        // Compute the number of request arguments, accounting for nullable ones
        int len = 3;
        final CompositeBuffer cb = newRequestCompositeBuffer(len, RedisProtocolSupport.Command.CLUSTER,
                    RedisProtocolSupport.SubCommand.FORGET, allocator);
        addRequestArgument(nodeId, cb, allocator);
        final RedisRequest request = newRequest(RedisProtocolSupport.Command.CLUSTER, cb);
        final RedisPartitionAttributesBuilder partitionAttributesBuilder = partitionAttributesBuilderFunction
                    .apply(RedisProtocolSupport.Command.CLUSTER);
        final Single<String> result = partitionedRedisClient.request(partitionAttributesBuilder.build(), request, String.class);
        return result;
    }

    @Override
    public <T> Single<List<T>> clusterGetkeysinslot(final long slot, final long count) {
        final BufferAllocator allocator = partitionedRedisClient.getBufferAllocator();
        // Compute the number of request arguments, accounting for nullable ones
        int len = 4;
        final CompositeBuffer cb = newRequestCompositeBuffer(len, RedisProtocolSupport.Command.CLUSTER,
                    RedisProtocolSupport.SubCommand.GETKEYSINSLOT, allocator);
        addRequestArgument(slot, cb, allocator);
        addRequestArgument(count, cb, allocator);
        final RedisRequest request = newRequest(RedisProtocolSupport.Command.CLUSTER, cb);
        final RedisPartitionAttributesBuilder partitionAttributesBuilder = partitionAttributesBuilderFunction
                    .apply(RedisProtocolSupport.Command.CLUSTER);
        final Single<List<T>> result = (Single) partitionedRedisClient.request(partitionAttributesBuilder.build(), request,
                    RedisUtils.ListWithBuffersCoercedToCharSequences.class);
        return result;
    }

    @Override
    public Single<String> clusterInfo() {
        final BufferAllocator allocator = partitionedRedisClient.getBufferAllocator();
        // Compute the number of request arguments, accounting for nullable ones
        int len = 2;
        final CompositeBuffer cb = newRequestCompositeBuffer(len, RedisProtocolSupport.Command.CLUSTER,
                    RedisProtocolSupport.SubCommand.INFO, allocator);
        final RedisRequest request = newRequest(RedisProtocolSupport.Command.CLUSTER, cb);
        final RedisPartitionAttributesBuilder partitionAttributesBuilder = partitionAttributesBuilderFunction
                    .apply(RedisProtocolSupport.Command.CLUSTER);
        final Single<String> result = partitionedRedisClient.request(partitionAttributesBuilder.build(), request, String.class);
        return result;
    }

    @Override
    public Single<Long> clusterKeyslot(final CharSequence key) {
        requireNonNull(key);
        final BufferAllocator allocator = partitionedRedisClient.getBufferAllocator();
        // Compute the number of request arguments, accounting for nullable ones
        int len = 3;
        final CompositeBuffer cb = newRequestCompositeBuffer(len, RedisProtocolSupport.Command.CLUSTER,
                    RedisProtocolSupport.SubCommand.KEYSLOT, allocator);
        addRequestArgument(key, cb, allocator);
        final RedisRequest request = newRequest(RedisProtocolSupport.Command.CLUSTER, cb);
        final RedisPartitionAttributesBuilder partitionAttributesBuilder = partitionAttributesBuilderFunction
                    .apply(RedisProtocolSupport.Command.CLUSTER);
        final Single<Long> result = partitionedRedisClient.request(partitionAttributesBuilder.build(), request, Long.class);
        return result;
    }

    @Override
    public Single<String> clusterMeet(final CharSequence ip, final long port) {
        requireNonNull(ip);
        final BufferAllocator allocator = partitionedRedisClient.getBufferAllocator();
        // Compute the number of request arguments, accounting for nullable ones
        int len = 4;
        final CompositeBuffer cb = newRequestCompositeBuffer(len, RedisProtocolSupport.Command.CLUSTER,
                    RedisProtocolSupport.SubCommand.MEET, allocator);
        addRequestArgument(ip, cb, allocator);
        addRequestArgument(port, cb, allocator);
        final RedisRequest request = newRequest(RedisProtocolSupport.Command.CLUSTER, cb);
        final RedisPartitionAttributesBuilder partitionAttributesBuilder = partitionAttributesBuilderFunction
                    .apply(RedisProtocolSupport.Command.CLUSTER);
        final Single<String> result = partitionedRedisClient.request(partitionAttributesBuilder.build(), request, String.class);
        return result;
    }

    @Override
    public Single<String> clusterNodes() {
        final BufferAllocator allocator = partitionedRedisClient.getBufferAllocator();
        // Compute the number of request arguments, accounting for nullable ones
        int len = 2;
        final CompositeBuffer cb = newRequestCompositeBuffer(len, RedisProtocolSupport.Command.CLUSTER,
                    RedisProtocolSupport.SubCommand.NODES, allocator);
        final RedisRequest request = newRequest(RedisProtocolSupport.Command.CLUSTER, cb);
        final RedisPartitionAttributesBuilder partitionAttributesBuilder = partitionAttributesBuilderFunction
                    .apply(RedisProtocolSupport.Command.CLUSTER);
        final Single<String> result = partitionedRedisClient.request(partitionAttributesBuilder.build(), request, String.class);
        return result;
    }

    @Override
    public Single<String> clusterReplicate(final CharSequence nodeId) {
        requireNonNull(nodeId);
        final BufferAllocator allocator = partitionedRedisClient.getBufferAllocator();
        // Compute the number of request arguments, accounting for nullable ones
        int len = 3;
        final CompositeBuffer cb = newRequestCompositeBuffer(len, RedisProtocolSupport.Command.CLUSTER,
                    RedisProtocolSupport.SubCommand.REPLICATE, allocator);
        addRequestArgument(nodeId, cb, allocator);
        final RedisRequest request = newRequest(RedisProtocolSupport.Command.CLUSTER, cb);
        final RedisPartitionAttributesBuilder partitionAttributesBuilder = partitionAttributesBuilderFunction
                    .apply(RedisProtocolSupport.Command.CLUSTER);
        final Single<String> result = partitionedRedisClient.request(partitionAttributesBuilder.build(), request, String.class);
        return result;
    }

    @Override
    public Single<String> clusterReset() {
        final BufferAllocator allocator = partitionedRedisClient.getBufferAllocator();
        // Compute the number of request arguments, accounting for nullable ones
        int len = 2;
        final CompositeBuffer cb = newRequestCompositeBuffer(len, RedisProtocolSupport.Command.CLUSTER,
                    RedisProtocolSupport.SubCommand.RESET, allocator);
        final RedisRequest request = newRequest(RedisProtocolSupport.Command.CLUSTER, cb);
        final RedisPartitionAttributesBuilder partitionAttributesBuilder = partitionAttributesBuilderFunction
                    .apply(RedisProtocolSupport.Command.CLUSTER);
        final Single<String> result = partitionedRedisClient.request(partitionAttributesBuilder.build(), request, String.class);
        return result;
    }

    @Override
    public Single<String> clusterReset(@Nullable final RedisProtocolSupport.ClusterResetResetType resetType) {
        final BufferAllocator allocator = partitionedRedisClient.getBufferAllocator();
        // Compute the number of request arguments, accounting for nullable ones
        int len = 2;
        if (resetType != null) {
            len++;
        }
        final CompositeBuffer cb = newRequestCompositeBuffer(len, RedisProtocolSupport.Command.CLUSTER,
                    RedisProtocolSupport.SubCommand.RESET, allocator);
        if (resetType != null) {
            addRequestArgument(resetType, cb, allocator);
        }
        final RedisRequest request = newRequest(RedisProtocolSupport.Command.CLUSTER, cb);
        final RedisPartitionAttributesBuilder partitionAttributesBuilder = partitionAttributesBuilderFunction
                    .apply(RedisProtocolSupport.Command.CLUSTER);
        final Single<String> result = partitionedRedisClient.request(partitionAttributesBuilder.build(), request, String.class);
        return result;
    }

    @Override
    public Single<String> clusterSaveconfig() {
        final BufferAllocator allocator = partitionedRedisClient.getBufferAllocator();
        // Compute the number of request arguments, accounting for nullable ones
        int len = 2;
        final CompositeBuffer cb = newRequestCompositeBuffer(len, RedisProtocolSupport.Command.CLUSTER,
                    RedisProtocolSupport.SubCommand.SAVECONFIG, allocator);
        final RedisRequest request = newRequest(RedisProtocolSupport.Command.CLUSTER, cb);
        final RedisPartitionAttributesBuilder partitionAttributesBuilder = partitionAttributesBuilderFunction
                    .apply(RedisProtocolSupport.Command.CLUSTER);
        final Single<String> result = partitionedRedisClient.request(partitionAttributesBuilder.build(), request, String.class);
        return result;
    }

    @Override
    public Single<String> clusterSetConfigEpoch(final long configEpoch) {
        final BufferAllocator allocator = partitionedRedisClient.getBufferAllocator();
        // Compute the number of request arguments, accounting for nullable ones
        int len = 3;
        final CompositeBuffer cb = newRequestCompositeBuffer(len, RedisProtocolSupport.Command.CLUSTER,
                    RedisProtocolSupport.SubCommand.SET_CONFIG_EPOCH, allocator);
        addRequestArgument(configEpoch, cb, allocator);
        final RedisRequest request = newRequest(RedisProtocolSupport.Command.CLUSTER, cb);
        final RedisPartitionAttributesBuilder partitionAttributesBuilder = partitionAttributesBuilderFunction
                    .apply(RedisProtocolSupport.Command.CLUSTER);
        final Single<String> result = partitionedRedisClient.request(partitionAttributesBuilder.build(), request, String.class);
        return result;
    }

    @Override
    public Single<String> clusterSetslot(final long slot, final RedisProtocolSupport.ClusterSetslotSubcommand subcommand) {
        requireNonNull(subcommand);
        final BufferAllocator allocator = partitionedRedisClient.getBufferAllocator();
        // Compute the number of request arguments, accounting for nullable ones
        int len = 4;
        final CompositeBuffer cb = newRequestCompositeBuffer(len, RedisProtocolSupport.Command.CLUSTER,
                    RedisProtocolSupport.SubCommand.SETSLOT, allocator);
        addRequestArgument(slot, cb, allocator);
        addRequestArgument(subcommand, cb, allocator);
        final RedisRequest request = newRequest(RedisProtocolSupport.Command.CLUSTER, cb);
        final RedisPartitionAttributesBuilder partitionAttributesBuilder = partitionAttributesBuilderFunction
                    .apply(RedisProtocolSupport.Command.CLUSTER);
        final Single<String> result = partitionedRedisClient.request(partitionAttributesBuilder.build(), request, String.class);
        return result;
    }

    @Override
    public Single<String> clusterSetslot(final long slot, final RedisProtocolSupport.ClusterSetslotSubcommand subcommand,
                                         @Nullable final CharSequence nodeId) {
        requireNonNull(subcommand);
        final BufferAllocator allocator = partitionedRedisClient.getBufferAllocator();
        // Compute the number of request arguments, accounting for nullable ones
        int len = 4;
        if (nodeId != null) {
            len++;
        }
        final CompositeBuffer cb = newRequestCompositeBuffer(len, RedisProtocolSupport.Command.CLUSTER,
                    RedisProtocolSupport.SubCommand.SETSLOT, allocator);
        addRequestArgument(slot, cb, allocator);
        addRequestArgument(subcommand, cb, allocator);
        if (nodeId != null) {
            addRequestArgument(nodeId, cb, allocator);
        }
        final RedisRequest request = newRequest(RedisProtocolSupport.Command.CLUSTER, cb);
        final RedisPartitionAttributesBuilder partitionAttributesBuilder = partitionAttributesBuilderFunction
                    .apply(RedisProtocolSupport.Command.CLUSTER);
        final Single<String> result = partitionedRedisClient.request(partitionAttributesBuilder.build(), request, String.class);
        return result;
    }

    @Override
    public Single<String> clusterSlaves(final CharSequence nodeId) {
        requireNonNull(nodeId);
        final BufferAllocator allocator = partitionedRedisClient.getBufferAllocator();
        // Compute the number of request arguments, accounting for nullable ones
        int len = 3;
        final CompositeBuffer cb = newRequestCompositeBuffer(len, RedisProtocolSupport.Command.CLUSTER,
                    RedisProtocolSupport.SubCommand.SLAVES, allocator);
        addRequestArgument(nodeId, cb, allocator);
        final RedisRequest request = newRequest(RedisProtocolSupport.Command.CLUSTER, cb);
        final RedisPartitionAttributesBuilder partitionAttributesBuilder = partitionAttributesBuilderFunction
                    .apply(RedisProtocolSupport.Command.CLUSTER);
        final Single<String> result = partitionedRedisClient.request(partitionAttributesBuilder.build(), request, String.class);
        return result;
    }

    @Override
    public <T> Single<List<T>> clusterSlots() {
        final BufferAllocator allocator = partitionedRedisClient.getBufferAllocator();
        // Compute the number of request arguments, accounting for nullable ones
        int len = 2;
        final CompositeBuffer cb = newRequestCompositeBuffer(len, RedisProtocolSupport.Command.CLUSTER,
                    RedisProtocolSupport.SubCommand.SLOTS, allocator);
        final RedisRequest request = newRequest(RedisProtocolSupport.Command.CLUSTER, cb);
        final RedisPartitionAttributesBuilder partitionAttributesBuilder = partitionAttributesBuilderFunction
                    .apply(RedisProtocolSupport.Command.CLUSTER);
        final Single<List<T>> result = (Single) partitionedRedisClient.request(partitionAttributesBuilder.build(), request,
                    RedisUtils.ListWithBuffersCoercedToCharSequences.class);
        return result;
    }

    @Override
    public <T> Single<List<T>> command() {
        final BufferAllocator allocator = partitionedRedisClient.getBufferAllocator();
        // Compute the number of request arguments, accounting for nullable ones
        int len = 1;
        final CompositeBuffer cb = newRequestCompositeBuffer(len, RedisProtocolSupport.Command.COMMAND, allocator);
        final RedisRequest request = newRequest(RedisProtocolSupport.Command.COMMAND, cb);
        final RedisPartitionAttributesBuilder partitionAttributesBuilder = partitionAttributesBuilderFunction
                    .apply(RedisProtocolSupport.Command.COMMAND);
        final Single<List<T>> result = (Single) partitionedRedisClient.request(partitionAttributesBuilder.build(), request,
                    RedisUtils.ListWithBuffersCoercedToCharSequences.class);
        return result;
    }

    @Override
    public Single<Long> commandCount() {
        final BufferAllocator allocator = partitionedRedisClient.getBufferAllocator();
        // Compute the number of request arguments, accounting for nullable ones
        int len = 2;
        final CompositeBuffer cb = newRequestCompositeBuffer(len, RedisProtocolSupport.Command.COMMAND,
                    RedisProtocolSupport.SubCommand.COUNT, allocator);
        final RedisRequest request = newRequest(RedisProtocolSupport.Command.COMMAND, cb);
        final RedisPartitionAttributesBuilder partitionAttributesBuilder = partitionAttributesBuilderFunction
                    .apply(RedisProtocolSupport.Command.COMMAND);
        final Single<Long> result = partitionedRedisClient.request(partitionAttributesBuilder.build(), request, Long.class);
        return result;
    }

    @Override
    public <T> Single<List<T>> commandGetkeys() {
        final BufferAllocator allocator = partitionedRedisClient.getBufferAllocator();
        // Compute the number of request arguments, accounting for nullable ones
        int len = 2;
        final CompositeBuffer cb = newRequestCompositeBuffer(len, RedisProtocolSupport.Command.COMMAND,
                    RedisProtocolSupport.SubCommand.GETKEYS, allocator);
        final RedisRequest request = newRequest(RedisProtocolSupport.Command.COMMAND, cb);
        final RedisPartitionAttributesBuilder partitionAttributesBuilder = partitionAttributesBuilderFunction
                    .apply(RedisProtocolSupport.Command.COMMAND);
        final Single<List<T>> result = (Single) partitionedRedisClient.request(partitionAttributesBuilder.build(), request,
                    RedisUtils.ListWithBuffersCoercedToCharSequences.class);
        return result;
    }

    @Override
    public <T> Single<List<T>> commandInfo(final CharSequence commandName) {
        requireNonNull(commandName);
        final BufferAllocator allocator = partitionedRedisClient.getBufferAllocator();
        // Compute the number of request arguments, accounting for nullable ones
        int len = 3;
        final CompositeBuffer cb = newRequestCompositeBuffer(len, RedisProtocolSupport.Command.COMMAND,
                    RedisProtocolSupport.SubCommand.INFO, allocator);
        addRequestArgument(commandName, cb, allocator);
        final RedisRequest request = newRequest(RedisProtocolSupport.Command.COMMAND, cb);
        final RedisPartitionAttributesBuilder partitionAttributesBuilder = partitionAttributesBuilderFunction
                    .apply(RedisProtocolSupport.Command.COMMAND);
        final Single<List<T>> result = (Single) partitionedRedisClient.request(partitionAttributesBuilder.build(), request,
                    RedisUtils.ListWithBuffersCoercedToCharSequences.class);
        return result;
    }

    @Override
    public <T> Single<List<T>> commandInfo(final CharSequence commandName1, final CharSequence commandName2) {
        requireNonNull(commandName1);
        requireNonNull(commandName2);
        final BufferAllocator allocator = partitionedRedisClient.getBufferAllocator();
        // Compute the number of request arguments, accounting for nullable ones
        int len = 4;
        final CompositeBuffer cb = newRequestCompositeBuffer(len, RedisProtocolSupport.Command.COMMAND,
                    RedisProtocolSupport.SubCommand.INFO, allocator);
        addRequestArgument(commandName1, cb, allocator);
        addRequestArgument(commandName2, cb, allocator);
        final RedisRequest request = newRequest(RedisProtocolSupport.Command.COMMAND, cb);
        final RedisPartitionAttributesBuilder partitionAttributesBuilder = partitionAttributesBuilderFunction
                    .apply(RedisProtocolSupport.Command.COMMAND);
        final Single<List<T>> result = (Single) partitionedRedisClient.request(partitionAttributesBuilder.build(), request,
                    RedisUtils.ListWithBuffersCoercedToCharSequences.class);
        return result;
    }

    @Override
    public <T> Single<List<T>> commandInfo(final CharSequence commandName1, final CharSequence commandName2,
                                           final CharSequence commandName3) {
        requireNonNull(commandName1);
        requireNonNull(commandName2);
        requireNonNull(commandName3);
        final BufferAllocator allocator = partitionedRedisClient.getBufferAllocator();
        // Compute the number of request arguments, accounting for nullable ones
        int len = 5;
        final CompositeBuffer cb = newRequestCompositeBuffer(len, RedisProtocolSupport.Command.COMMAND,
                    RedisProtocolSupport.SubCommand.INFO, allocator);
        addRequestArgument(commandName1, cb, allocator);
        addRequestArgument(commandName2, cb, allocator);
        addRequestArgument(commandName3, cb, allocator);
        final RedisRequest request = newRequest(RedisProtocolSupport.Command.COMMAND, cb);
        final RedisPartitionAttributesBuilder partitionAttributesBuilder = partitionAttributesBuilderFunction
                    .apply(RedisProtocolSupport.Command.COMMAND);
        final Single<List<T>> result = (Single) partitionedRedisClient.request(partitionAttributesBuilder.build(), request,
                    RedisUtils.ListWithBuffersCoercedToCharSequences.class);
        return result;
    }

    @Override
    public <T> Single<List<T>> commandInfo(final Collection<? extends CharSequence> commandNames) {
        requireNonNull(commandNames);
        final BufferAllocator allocator = partitionedRedisClient.getBufferAllocator();
        // Compute the number of request arguments, accounting for nullable ones
        int len = 2;
        len += commandNames.size();
        final CompositeBuffer cb = newRequestCompositeBuffer(len, RedisProtocolSupport.Command.COMMAND,
                    RedisProtocolSupport.SubCommand.INFO, allocator);
        addRequestCharSequenceArguments(commandNames, null, cb, allocator);
        final RedisRequest request = newRequest(RedisProtocolSupport.Command.COMMAND, cb);
        final RedisPartitionAttributesBuilder partitionAttributesBuilder = partitionAttributesBuilderFunction
                    .apply(RedisProtocolSupport.Command.COMMAND);
        final Single<List<T>> result = (Single) partitionedRedisClient.request(partitionAttributesBuilder.build(), request,
                    RedisUtils.ListWithBuffersCoercedToCharSequences.class);
        return result;
    }

    @Override
    public <T> Single<List<T>> configGet(final CharSequence parameter) {
        requireNonNull(parameter);
        final BufferAllocator allocator = partitionedRedisClient.getBufferAllocator();
        // Compute the number of request arguments, accounting for nullable ones
        int len = 3;
        final CompositeBuffer cb = newRequestCompositeBuffer(len, RedisProtocolSupport.Command.CONFIG,
                    RedisProtocolSupport.SubCommand.GET, allocator);
        addRequestArgument(parameter, cb, allocator);
        final RedisRequest request = newRequest(RedisProtocolSupport.Command.CONFIG, cb);
        final RedisPartitionAttributesBuilder partitionAttributesBuilder = partitionAttributesBuilderFunction
                    .apply(RedisProtocolSupport.Command.CONFIG);
        final Single<List<T>> result = (Single) partitionedRedisClient.request(partitionAttributesBuilder.build(), request,
                    RedisUtils.ListWithBuffersCoercedToCharSequences.class);
        return result;
    }

    @Override
    public Single<String> configRewrite() {
        final BufferAllocator allocator = partitionedRedisClient.getBufferAllocator();
        // Compute the number of request arguments, accounting for nullable ones
        int len = 2;
        final CompositeBuffer cb = newRequestCompositeBuffer(len, RedisProtocolSupport.Command.CONFIG,
                    RedisProtocolSupport.SubCommand.REWRITE, allocator);
        final RedisRequest request = newRequest(RedisProtocolSupport.Command.CONFIG, cb);
        final RedisPartitionAttributesBuilder partitionAttributesBuilder = partitionAttributesBuilderFunction
                    .apply(RedisProtocolSupport.Command.CONFIG);
        final Single<String> result = partitionedRedisClient.request(partitionAttributesBuilder.build(), request, String.class);
        return result;
    }

    @Override
    public Single<String> configSet(final CharSequence parameter, final CharSequence value) {
        requireNonNull(parameter);
        requireNonNull(value);
        final BufferAllocator allocator = partitionedRedisClient.getBufferAllocator();
        // Compute the number of request arguments, accounting for nullable ones
        int len = 4;
        final CompositeBuffer cb = newRequestCompositeBuffer(len, RedisProtocolSupport.Command.CONFIG,
                    RedisProtocolSupport.SubCommand.SET, allocator);
        addRequestArgument(parameter, cb, allocator);
        addRequestArgument(value, cb, allocator);
        final RedisRequest request = newRequest(RedisProtocolSupport.Command.CONFIG, cb);
        final RedisPartitionAttributesBuilder partitionAttributesBuilder = partitionAttributesBuilderFunction
                    .apply(RedisProtocolSupport.Command.CONFIG);
        final Single<String> result = partitionedRedisClient.request(partitionAttributesBuilder.build(), request, String.class);
        return result;
    }

    @Override
    public Single<String> configResetstat() {
        final BufferAllocator allocator = partitionedRedisClient.getBufferAllocator();
        // Compute the number of request arguments, accounting for nullable ones
        int len = 2;
        final CompositeBuffer cb = newRequestCompositeBuffer(len, RedisProtocolSupport.Command.CONFIG,
                    RedisProtocolSupport.SubCommand.RESETSTAT, allocator);
        final RedisRequest request = newRequest(RedisProtocolSupport.Command.CONFIG, cb);
        final RedisPartitionAttributesBuilder partitionAttributesBuilder = partitionAttributesBuilderFunction
                    .apply(RedisProtocolSupport.Command.CONFIG);
        final Single<String> result = partitionedRedisClient.request(partitionAttributesBuilder.build(), request, String.class);
        return result;
    }

    @Override
    public Single<Long> dbsize() {
        final BufferAllocator allocator = partitionedRedisClient.getBufferAllocator();
        // Compute the number of request arguments, accounting for nullable ones
        int len = 1;
        final CompositeBuffer cb = newRequestCompositeBuffer(len, RedisProtocolSupport.Command.DBSIZE, allocator);
        final RedisRequest request = newRequest(RedisProtocolSupport.Command.DBSIZE, cb);
        final RedisPartitionAttributesBuilder partitionAttributesBuilder = partitionAttributesBuilderFunction
                    .apply(RedisProtocolSupport.Command.DBSIZE);
        final Single<Long> result = partitionedRedisClient.request(partitionAttributesBuilder.build(), request, Long.class);
        return result;
    }

    @Override
    public Single<String> debugObject(@RedisProtocolSupport.Key final CharSequence key) {
        requireNonNull(key);
        final BufferAllocator allocator = partitionedRedisClient.getBufferAllocator();
        // Compute the number of request arguments, accounting for nullable ones
        int len = 3;
        final CompositeBuffer cb = newRequestCompositeBuffer(len, RedisProtocolSupport.Command.DEBUG,
                    RedisProtocolSupport.SubCommand.OBJECT, allocator);
        addRequestArgument(key, cb, allocator);
        final RedisRequest request = newRequest(RedisProtocolSupport.Command.DEBUG, cb);
        final RedisPartitionAttributesBuilder partitionAttributesBuilder = partitionAttributesBuilderFunction
                    .apply(RedisProtocolSupport.Command.DEBUG);
        partitionAttributesBuilder.addKey(key);
        final Single<String> result = partitionedRedisClient.request(partitionAttributesBuilder.build(), request, String.class);
        return result;
    }

    @Override
    public Single<String> debugSegfault() {
        final BufferAllocator allocator = partitionedRedisClient.getBufferAllocator();
        // Compute the number of request arguments, accounting for nullable ones
        int len = 2;
        final CompositeBuffer cb = newRequestCompositeBuffer(len, RedisProtocolSupport.Command.DEBUG,
                    RedisProtocolSupport.SubCommand.SEGFAULT, allocator);
        final RedisRequest request = newRequest(RedisProtocolSupport.Command.DEBUG, cb);
        final RedisPartitionAttributesBuilder partitionAttributesBuilder = partitionAttributesBuilderFunction
                    .apply(RedisProtocolSupport.Command.DEBUG);
        final Single<String> result = partitionedRedisClient.request(partitionAttributesBuilder.build(), request, String.class);
        return result;
    }

    @Override
    public Single<Long> decr(@RedisProtocolSupport.Key final CharSequence key) {
        requireNonNull(key);
        final BufferAllocator allocator = partitionedRedisClient.getBufferAllocator();
        // Compute the number of request arguments, accounting for nullable ones
        int len = 2;
        final CompositeBuffer cb = newRequestCompositeBuffer(len, RedisProtocolSupport.Command.DECR, allocator);
        addRequestArgument(key, cb, allocator);
        final RedisRequest request = newRequest(RedisProtocolSupport.Command.DECR, cb);
        final RedisPartitionAttributesBuilder partitionAttributesBuilder = partitionAttributesBuilderFunction
                    .apply(RedisProtocolSupport.Command.DECR);
        partitionAttributesBuilder.addKey(key);
        final Single<Long> result = partitionedRedisClient.request(partitionAttributesBuilder.build(), request, Long.class);
        return result;
    }

    @Override
    public Single<Long> decrby(@RedisProtocolSupport.Key final CharSequence key, final long decrement) {
        requireNonNull(key);
        final BufferAllocator allocator = partitionedRedisClient.getBufferAllocator();
        // Compute the number of request arguments, accounting for nullable ones
        int len = 3;
        final CompositeBuffer cb = newRequestCompositeBuffer(len, RedisProtocolSupport.Command.DECRBY, allocator);
        addRequestArgument(key, cb, allocator);
        addRequestArgument(decrement, cb, allocator);
        final RedisRequest request = newRequest(RedisProtocolSupport.Command.DECRBY, cb);
        final RedisPartitionAttributesBuilder partitionAttributesBuilder = partitionAttributesBuilderFunction
                    .apply(RedisProtocolSupport.Command.DECRBY);
        partitionAttributesBuilder.addKey(key);
        final Single<Long> result = partitionedRedisClient.request(partitionAttributesBuilder.build(), request, Long.class);
        return result;
    }

    @Override
    public Single<Long> del(@RedisProtocolSupport.Key final CharSequence key) {
        requireNonNull(key);
        final BufferAllocator allocator = partitionedRedisClient.getBufferAllocator();
        // Compute the number of request arguments, accounting for nullable ones
        int len = 2;
        final CompositeBuffer cb = newRequestCompositeBuffer(len, RedisProtocolSupport.Command.DEL, allocator);
        addRequestArgument(key, cb, allocator);
        final RedisRequest request = newRequest(RedisProtocolSupport.Command.DEL, cb);
        final RedisPartitionAttributesBuilder partitionAttributesBuilder = partitionAttributesBuilderFunction
                    .apply(RedisProtocolSupport.Command.DEL);
        partitionAttributesBuilder.addKey(key);
        final Single<Long> result = partitionedRedisClient.request(partitionAttributesBuilder.build(), request, Long.class);
        return result;
    }

    @Override
    public Single<Long> del(@RedisProtocolSupport.Key final CharSequence key1,
                            @RedisProtocolSupport.Key final CharSequence key2) {
        requireNonNull(key1);
        requireNonNull(key2);
        final BufferAllocator allocator = partitionedRedisClient.getBufferAllocator();
        // Compute the number of request arguments, accounting for nullable ones
        int len = 3;
        final CompositeBuffer cb = newRequestCompositeBuffer(len, RedisProtocolSupport.Command.DEL, allocator);
        addRequestArgument(key1, cb, allocator);
        addRequestArgument(key2, cb, allocator);
        final RedisRequest request = newRequest(RedisProtocolSupport.Command.DEL, cb);
        final RedisPartitionAttributesBuilder partitionAttributesBuilder = partitionAttributesBuilderFunction
                    .apply(RedisProtocolSupport.Command.DEL);
        partitionAttributesBuilder.addKey(key1);
        partitionAttributesBuilder.addKey(key2);
        final Single<Long> result = partitionedRedisClient.request(partitionAttributesBuilder.build(), request, Long.class);
        return result;
    }

    @Override
    public Single<Long> del(@RedisProtocolSupport.Key final CharSequence key1, @RedisProtocolSupport.Key final CharSequence key2,
                            @RedisProtocolSupport.Key final CharSequence key3) {
        requireNonNull(key1);
        requireNonNull(key2);
        requireNonNull(key3);
        final BufferAllocator allocator = partitionedRedisClient.getBufferAllocator();
        // Compute the number of request arguments, accounting for nullable ones
        int len = 4;
        final CompositeBuffer cb = newRequestCompositeBuffer(len, RedisProtocolSupport.Command.DEL, allocator);
        addRequestArgument(key1, cb, allocator);
        addRequestArgument(key2, cb, allocator);
        addRequestArgument(key3, cb, allocator);
        final RedisRequest request = newRequest(RedisProtocolSupport.Command.DEL, cb);
        final RedisPartitionAttributesBuilder partitionAttributesBuilder = partitionAttributesBuilderFunction
                    .apply(RedisProtocolSupport.Command.DEL);
        partitionAttributesBuilder.addKey(key1);
        partitionAttributesBuilder.addKey(key2);
        partitionAttributesBuilder.addKey(key3);
        final Single<Long> result = partitionedRedisClient.request(partitionAttributesBuilder.build(), request, Long.class);
        return result;
    }

    @Override
    public Single<Long> del(@RedisProtocolSupport.Key final Collection<? extends CharSequence> keys) {
        requireNonNull(keys);
        final BufferAllocator allocator = partitionedRedisClient.getBufferAllocator();
        // Compute the number of request arguments, accounting for nullable ones
        int len = 1;
        len += keys.size();
        final CompositeBuffer cb = newRequestCompositeBuffer(len, RedisProtocolSupport.Command.DEL, allocator);
        addRequestCharSequenceArguments(keys, null, cb, allocator);
        final RedisRequest request = newRequest(RedisProtocolSupport.Command.DEL, cb);
        final RedisPartitionAttributesBuilder partitionAttributesBuilder = partitionAttributesBuilderFunction
                    .apply(RedisProtocolSupport.Command.DEL);
        addCharSequenceKeysToAttributeBuilder(keys, partitionAttributesBuilder);
        final Single<Long> result = partitionedRedisClient.request(partitionAttributesBuilder.build(), request, Long.class);
        return result;
    }

    @Override
    public Single<String> dump(@RedisProtocolSupport.Key final CharSequence key) {
        requireNonNull(key);
        final BufferAllocator allocator = partitionedRedisClient.getBufferAllocator();
        // Compute the number of request arguments, accounting for nullable ones
        int len = 2;
        final CompositeBuffer cb = newRequestCompositeBuffer(len, RedisProtocolSupport.Command.DUMP, allocator);
        addRequestArgument(key, cb, allocator);
        final RedisRequest request = newRequest(RedisProtocolSupport.Command.DUMP, cb);
        final RedisPartitionAttributesBuilder partitionAttributesBuilder = partitionAttributesBuilderFunction
                    .apply(RedisProtocolSupport.Command.DUMP);
        partitionAttributesBuilder.addKey(key);
        final Single<String> result = partitionedRedisClient.request(partitionAttributesBuilder.build(), request, String.class);
        return result;
    }

    @Override
    public Single<String> echo(final CharSequence message) {
        requireNonNull(message);
        final BufferAllocator allocator = partitionedRedisClient.getBufferAllocator();
        // Compute the number of request arguments, accounting for nullable ones
        int len = 2;
        final CompositeBuffer cb = newRequestCompositeBuffer(len, RedisProtocolSupport.Command.ECHO, allocator);
        addRequestArgument(message, cb, allocator);
        final RedisRequest request = newRequest(RedisProtocolSupport.Command.ECHO, cb);
        final RedisPartitionAttributesBuilder partitionAttributesBuilder = partitionAttributesBuilderFunction
                    .apply(RedisProtocolSupport.Command.ECHO);
        final Single<String> result = partitionedRedisClient.request(partitionAttributesBuilder.build(), request, String.class);
        return result;
    }

    @Override
    public Single<String> eval(final CharSequence script, final long numkeys,
                               @RedisProtocolSupport.Key final Collection<? extends CharSequence> keys,
                               final Collection<? extends CharSequence> args) {
        requireNonNull(script);
        requireNonNull(keys);
        requireNonNull(args);
        final BufferAllocator allocator = partitionedRedisClient.getBufferAllocator();
        // Compute the number of request arguments, accounting for nullable ones
        int len = 3;
        len += keys.size();
        len += args.size();
        final CompositeBuffer cb = newRequestCompositeBuffer(len, RedisProtocolSupport.Command.EVAL, allocator);
        addRequestArgument(script, cb, allocator);
        addRequestArgument(numkeys, cb, allocator);
        addRequestCharSequenceArguments(keys, null, cb, allocator);
        addRequestCharSequenceArguments(args, null, cb, allocator);
        final RedisRequest request = newRequest(RedisProtocolSupport.Command.EVAL, cb);
        final RedisPartitionAttributesBuilder partitionAttributesBuilder = partitionAttributesBuilderFunction
                    .apply(RedisProtocolSupport.Command.EVAL);
        addCharSequenceKeysToAttributeBuilder(keys, partitionAttributesBuilder);
        final Single<String> result = partitionedRedisClient.request(partitionAttributesBuilder.build(), request, String.class);
        return result;
    }

    @Override
    public <T> Single<List<T>> evalList(final CharSequence script, final long numkeys,
                                        @RedisProtocolSupport.Key final Collection<? extends CharSequence> keys,
                                        final Collection<? extends CharSequence> args) {
        requireNonNull(script);
        requireNonNull(keys);
        requireNonNull(args);
        final BufferAllocator allocator = partitionedRedisClient.getBufferAllocator();
        // Compute the number of request arguments, accounting for nullable ones
        int len = 3;
        len += keys.size();
        len += args.size();
        final CompositeBuffer cb = newRequestCompositeBuffer(len, RedisProtocolSupport.Command.EVAL, allocator);
        addRequestArgument(script, cb, allocator);
        addRequestArgument(numkeys, cb, allocator);
        addRequestCharSequenceArguments(keys, null, cb, allocator);
        addRequestCharSequenceArguments(args, null, cb, allocator);
        final RedisRequest request = newRequest(RedisProtocolSupport.Command.EVAL, cb);
        final RedisPartitionAttributesBuilder partitionAttributesBuilder = partitionAttributesBuilderFunction
                    .apply(RedisProtocolSupport.Command.EVAL);
        addCharSequenceKeysToAttributeBuilder(keys, partitionAttributesBuilder);
        final Single<List<T>> result = (Single) partitionedRedisClient.request(partitionAttributesBuilder.build(), request,
                    RedisUtils.ListWithBuffersCoercedToCharSequences.class);
        return result;
    }

    @Override
    public Single<Long> evalLong(final CharSequence script, final long numkeys,
                                 @RedisProtocolSupport.Key final Collection<? extends CharSequence> keys,
                                 final Collection<? extends CharSequence> args) {
        requireNonNull(script);
        requireNonNull(keys);
        requireNonNull(args);
        final BufferAllocator allocator = partitionedRedisClient.getBufferAllocator();
        // Compute the number of request arguments, accounting for nullable ones
        int len = 3;
        len += keys.size();
        len += args.size();
        final CompositeBuffer cb = newRequestCompositeBuffer(len, RedisProtocolSupport.Command.EVAL, allocator);
        addRequestArgument(script, cb, allocator);
        addRequestArgument(numkeys, cb, allocator);
        addRequestCharSequenceArguments(keys, null, cb, allocator);
        addRequestCharSequenceArguments(args, null, cb, allocator);
        final RedisRequest request = newRequest(RedisProtocolSupport.Command.EVAL, cb);
        final RedisPartitionAttributesBuilder partitionAttributesBuilder = partitionAttributesBuilderFunction
                    .apply(RedisProtocolSupport.Command.EVAL);
        addCharSequenceKeysToAttributeBuilder(keys, partitionAttributesBuilder);
        final Single<Long> result = partitionedRedisClient.request(partitionAttributesBuilder.build(), request, Long.class);
        return result;
    }

    @Override
    public Single<String> evalsha(final CharSequence sha1, final long numkeys,
                                  @RedisProtocolSupport.Key final Collection<? extends CharSequence> keys,
                                  final Collection<? extends CharSequence> args) {
        requireNonNull(sha1);
        requireNonNull(keys);
        requireNonNull(args);
        final BufferAllocator allocator = partitionedRedisClient.getBufferAllocator();
        // Compute the number of request arguments, accounting for nullable ones
        int len = 3;
        len += keys.size();
        len += args.size();
        final CompositeBuffer cb = newRequestCompositeBuffer(len, RedisProtocolSupport.Command.EVALSHA, allocator);
        addRequestArgument(sha1, cb, allocator);
        addRequestArgument(numkeys, cb, allocator);
        addRequestCharSequenceArguments(keys, null, cb, allocator);
        addRequestCharSequenceArguments(args, null, cb, allocator);
        final RedisRequest request = newRequest(RedisProtocolSupport.Command.EVALSHA, cb);
        final RedisPartitionAttributesBuilder partitionAttributesBuilder = partitionAttributesBuilderFunction
                    .apply(RedisProtocolSupport.Command.EVALSHA);
        addCharSequenceKeysToAttributeBuilder(keys, partitionAttributesBuilder);
        final Single<String> result = partitionedRedisClient.request(partitionAttributesBuilder.build(), request, String.class);
        return result;
    }

    @Override
    public <T> Single<List<T>> evalshaList(final CharSequence sha1, final long numkeys,
                                           @RedisProtocolSupport.Key final Collection<? extends CharSequence> keys,
                                           final Collection<? extends CharSequence> args) {
        requireNonNull(sha1);
        requireNonNull(keys);
        requireNonNull(args);
        final BufferAllocator allocator = partitionedRedisClient.getBufferAllocator();
        // Compute the number of request arguments, accounting for nullable ones
        int len = 3;
        len += keys.size();
        len += args.size();
        final CompositeBuffer cb = newRequestCompositeBuffer(len, RedisProtocolSupport.Command.EVALSHA, allocator);
        addRequestArgument(sha1, cb, allocator);
        addRequestArgument(numkeys, cb, allocator);
        addRequestCharSequenceArguments(keys, null, cb, allocator);
        addRequestCharSequenceArguments(args, null, cb, allocator);
        final RedisRequest request = newRequest(RedisProtocolSupport.Command.EVALSHA, cb);
        final RedisPartitionAttributesBuilder partitionAttributesBuilder = partitionAttributesBuilderFunction
                    .apply(RedisProtocolSupport.Command.EVALSHA);
        addCharSequenceKeysToAttributeBuilder(keys, partitionAttributesBuilder);
        final Single<List<T>> result = (Single) partitionedRedisClient.request(partitionAttributesBuilder.build(), request,
                    RedisUtils.ListWithBuffersCoercedToCharSequences.class);
        return result;
    }

    @Override
    public Single<Long> evalshaLong(final CharSequence sha1, final long numkeys,
                                    @RedisProtocolSupport.Key final Collection<? extends CharSequence> keys,
                                    final Collection<? extends CharSequence> args) {
        requireNonNull(sha1);
        requireNonNull(keys);
        requireNonNull(args);
        final BufferAllocator allocator = partitionedRedisClient.getBufferAllocator();
        // Compute the number of request arguments, accounting for nullable ones
        int len = 3;
        len += keys.size();
        len += args.size();
        final CompositeBuffer cb = newRequestCompositeBuffer(len, RedisProtocolSupport.Command.EVALSHA, allocator);
        addRequestArgument(sha1, cb, allocator);
        addRequestArgument(numkeys, cb, allocator);
        addRequestCharSequenceArguments(keys, null, cb, allocator);
        addRequestCharSequenceArguments(args, null, cb, allocator);
        final RedisRequest request = newRequest(RedisProtocolSupport.Command.EVALSHA, cb);
        final RedisPartitionAttributesBuilder partitionAttributesBuilder = partitionAttributesBuilderFunction
                    .apply(RedisProtocolSupport.Command.EVALSHA);
        addCharSequenceKeysToAttributeBuilder(keys, partitionAttributesBuilder);
        final Single<Long> result = partitionedRedisClient.request(partitionAttributesBuilder.build(), request, Long.class);
        return result;
    }

    @Override
    public Single<Long> exists(@RedisProtocolSupport.Key final CharSequence key) {
        requireNonNull(key);
        final BufferAllocator allocator = partitionedRedisClient.getBufferAllocator();
        // Compute the number of request arguments, accounting for nullable ones
        int len = 2;
        final CompositeBuffer cb = newRequestCompositeBuffer(len, RedisProtocolSupport.Command.EXISTS, allocator);
        addRequestArgument(key, cb, allocator);
        final RedisRequest request = newRequest(RedisProtocolSupport.Command.EXISTS, cb);
        final RedisPartitionAttributesBuilder partitionAttributesBuilder = partitionAttributesBuilderFunction
                    .apply(RedisProtocolSupport.Command.EXISTS);
        partitionAttributesBuilder.addKey(key);
        final Single<Long> result = partitionedRedisClient.request(partitionAttributesBuilder.build(), request, Long.class);
        return result;
    }

    @Override
    public Single<Long> exists(@RedisProtocolSupport.Key final CharSequence key1,
                               @RedisProtocolSupport.Key final CharSequence key2) {
        requireNonNull(key1);
        requireNonNull(key2);
        final BufferAllocator allocator = partitionedRedisClient.getBufferAllocator();
        // Compute the number of request arguments, accounting for nullable ones
        int len = 3;
        final CompositeBuffer cb = newRequestCompositeBuffer(len, RedisProtocolSupport.Command.EXISTS, allocator);
        addRequestArgument(key1, cb, allocator);
        addRequestArgument(key2, cb, allocator);
        final RedisRequest request = newRequest(RedisProtocolSupport.Command.EXISTS, cb);
        final RedisPartitionAttributesBuilder partitionAttributesBuilder = partitionAttributesBuilderFunction
                    .apply(RedisProtocolSupport.Command.EXISTS);
        partitionAttributesBuilder.addKey(key1);
        partitionAttributesBuilder.addKey(key2);
        final Single<Long> result = partitionedRedisClient.request(partitionAttributesBuilder.build(), request, Long.class);
        return result;
    }

    @Override
    public Single<Long> exists(@RedisProtocolSupport.Key final CharSequence key1,
                               @RedisProtocolSupport.Key final CharSequence key2,
                               @RedisProtocolSupport.Key final CharSequence key3) {
        requireNonNull(key1);
        requireNonNull(key2);
        requireNonNull(key3);
        final BufferAllocator allocator = partitionedRedisClient.getBufferAllocator();
        // Compute the number of request arguments, accounting for nullable ones
        int len = 4;
        final CompositeBuffer cb = newRequestCompositeBuffer(len, RedisProtocolSupport.Command.EXISTS, allocator);
        addRequestArgument(key1, cb, allocator);
        addRequestArgument(key2, cb, allocator);
        addRequestArgument(key3, cb, allocator);
        final RedisRequest request = newRequest(RedisProtocolSupport.Command.EXISTS, cb);
        final RedisPartitionAttributesBuilder partitionAttributesBuilder = partitionAttributesBuilderFunction
                    .apply(RedisProtocolSupport.Command.EXISTS);
        partitionAttributesBuilder.addKey(key1);
        partitionAttributesBuilder.addKey(key2);
        partitionAttributesBuilder.addKey(key3);
        final Single<Long> result = partitionedRedisClient.request(partitionAttributesBuilder.build(), request, Long.class);
        return result;
    }

    @Override
    public Single<Long> exists(@RedisProtocolSupport.Key final Collection<? extends CharSequence> keys) {
        requireNonNull(keys);
        final BufferAllocator allocator = partitionedRedisClient.getBufferAllocator();
        // Compute the number of request arguments, accounting for nullable ones
        int len = 1;
        len += keys.size();
        final CompositeBuffer cb = newRequestCompositeBuffer(len, RedisProtocolSupport.Command.EXISTS, allocator);
        addRequestCharSequenceArguments(keys, null, cb, allocator);
        final RedisRequest request = newRequest(RedisProtocolSupport.Command.EXISTS, cb);
        final RedisPartitionAttributesBuilder partitionAttributesBuilder = partitionAttributesBuilderFunction
                    .apply(RedisProtocolSupport.Command.EXISTS);
        addCharSequenceKeysToAttributeBuilder(keys, partitionAttributesBuilder);
        final Single<Long> result = partitionedRedisClient.request(partitionAttributesBuilder.build(), request, Long.class);
        return result;
    }

    @Override
    public Single<Long> expire(@RedisProtocolSupport.Key final CharSequence key, final long seconds) {
        requireNonNull(key);
        final BufferAllocator allocator = partitionedRedisClient.getBufferAllocator();
        // Compute the number of request arguments, accounting for nullable ones
        int len = 3;
        final CompositeBuffer cb = newRequestCompositeBuffer(len, RedisProtocolSupport.Command.EXPIRE, allocator);
        addRequestArgument(key, cb, allocator);
        addRequestArgument(seconds, cb, allocator);
        final RedisRequest request = newRequest(RedisProtocolSupport.Command.EXPIRE, cb);
        final RedisPartitionAttributesBuilder partitionAttributesBuilder = partitionAttributesBuilderFunction
                    .apply(RedisProtocolSupport.Command.EXPIRE);
        partitionAttributesBuilder.addKey(key);
        final Single<Long> result = partitionedRedisClient.request(partitionAttributesBuilder.build(), request, Long.class);
        return result;
    }

    @Override
    public Single<Long> expireat(@RedisProtocolSupport.Key final CharSequence key, final long timestamp) {
        requireNonNull(key);
        final BufferAllocator allocator = partitionedRedisClient.getBufferAllocator();
        // Compute the number of request arguments, accounting for nullable ones
        int len = 3;
        final CompositeBuffer cb = newRequestCompositeBuffer(len, RedisProtocolSupport.Command.EXPIREAT, allocator);
        addRequestArgument(key, cb, allocator);
        addRequestArgument(timestamp, cb, allocator);
        final RedisRequest request = newRequest(RedisProtocolSupport.Command.EXPIREAT, cb);
        final RedisPartitionAttributesBuilder partitionAttributesBuilder = partitionAttributesBuilderFunction
                    .apply(RedisProtocolSupport.Command.EXPIREAT);
        partitionAttributesBuilder.addKey(key);
        final Single<Long> result = partitionedRedisClient.request(partitionAttributesBuilder.build(), request, Long.class);
        return result;
    }

    @Override
    public Single<String> flushall() {
        final BufferAllocator allocator = partitionedRedisClient.getBufferAllocator();
        // Compute the number of request arguments, accounting for nullable ones
        int len = 1;
        final CompositeBuffer cb = newRequestCompositeBuffer(len, RedisProtocolSupport.Command.FLUSHALL, allocator);
        final RedisRequest request = newRequest(RedisProtocolSupport.Command.FLUSHALL, cb);
        final RedisPartitionAttributesBuilder partitionAttributesBuilder = partitionAttributesBuilderFunction
                    .apply(RedisProtocolSupport.Command.FLUSHALL);
        final Single<String> result = partitionedRedisClient.request(partitionAttributesBuilder.build(), request, String.class);
        return result;
    }

    @Override
    public Single<String> flushall(@Nullable final RedisProtocolSupport.FlushallAsync async) {
        final BufferAllocator allocator = partitionedRedisClient.getBufferAllocator();
        // Compute the number of request arguments, accounting for nullable ones
        int len = 1;
        if (async != null) {
            len++;
        }
        final CompositeBuffer cb = newRequestCompositeBuffer(len, RedisProtocolSupport.Command.FLUSHALL, allocator);
        if (async != null) {
            addRequestArgument(async, cb, allocator);
        }
        final RedisRequest request = newRequest(RedisProtocolSupport.Command.FLUSHALL, cb);
        final RedisPartitionAttributesBuilder partitionAttributesBuilder = partitionAttributesBuilderFunction
                    .apply(RedisProtocolSupport.Command.FLUSHALL);
        final Single<String> result = partitionedRedisClient.request(partitionAttributesBuilder.build(), request, String.class);
        return result;
    }

    @Override
    public Single<String> flushdb() {
        final BufferAllocator allocator = partitionedRedisClient.getBufferAllocator();
        // Compute the number of request arguments, accounting for nullable ones
        int len = 1;
        final CompositeBuffer cb = newRequestCompositeBuffer(len, RedisProtocolSupport.Command.FLUSHDB, allocator);
        final RedisRequest request = newRequest(RedisProtocolSupport.Command.FLUSHDB, cb);
        final RedisPartitionAttributesBuilder partitionAttributesBuilder = partitionAttributesBuilderFunction
                    .apply(RedisProtocolSupport.Command.FLUSHDB);
        final Single<String> result = partitionedRedisClient.request(partitionAttributesBuilder.build(), request, String.class);
        return result;
    }

    @Override
    public Single<String> flushdb(@Nullable final RedisProtocolSupport.FlushdbAsync async) {
        final BufferAllocator allocator = partitionedRedisClient.getBufferAllocator();
        // Compute the number of request arguments, accounting for nullable ones
        int len = 1;
        if (async != null) {
            len++;
        }
        final CompositeBuffer cb = newRequestCompositeBuffer(len, RedisProtocolSupport.Command.FLUSHDB, allocator);
        if (async != null) {
            addRequestArgument(async, cb, allocator);
        }
        final RedisRequest request = newRequest(RedisProtocolSupport.Command.FLUSHDB, cb);
        final RedisPartitionAttributesBuilder partitionAttributesBuilder = partitionAttributesBuilderFunction
                    .apply(RedisProtocolSupport.Command.FLUSHDB);
        final Single<String> result = partitionedRedisClient.request(partitionAttributesBuilder.build(), request, String.class);
        return result;
    }

    @Override
    public Single<Long> geoadd(@RedisProtocolSupport.Key final CharSequence key, final double longitude, final double latitude,
                               final CharSequence member) {
        requireNonNull(key);
        requireNonNull(member);
        final BufferAllocator allocator = partitionedRedisClient.getBufferAllocator();
        // Compute the number of request arguments, accounting for nullable ones
        int len = 5;
        final CompositeBuffer cb = newRequestCompositeBuffer(len, RedisProtocolSupport.Command.GEOADD, allocator);
        addRequestArgument(key, cb, allocator);
        addRequestArgument(longitude, cb, allocator);
        addRequestArgument(latitude, cb, allocator);
        addRequestArgument(member, cb, allocator);
        final RedisRequest request = newRequest(RedisProtocolSupport.Command.GEOADD, cb);
        final RedisPartitionAttributesBuilder partitionAttributesBuilder = partitionAttributesBuilderFunction
                    .apply(RedisProtocolSupport.Command.GEOADD);
        partitionAttributesBuilder.addKey(key);
        final Single<Long> result = partitionedRedisClient.request(partitionAttributesBuilder.build(), request, Long.class);
        return result;
    }

    @Override
    public Single<Long> geoadd(@RedisProtocolSupport.Key final CharSequence key, final double longitude1, final double latitude1,
                               final CharSequence member1, final double longitude2, final double latitude2,
                               final CharSequence member2) {
        requireNonNull(key);
        requireNonNull(member1);
        requireNonNull(member2);
        final BufferAllocator allocator = partitionedRedisClient.getBufferAllocator();
        // Compute the number of request arguments, accounting for nullable ones
        int len = 8;
        final CompositeBuffer cb = newRequestCompositeBuffer(len, RedisProtocolSupport.Command.GEOADD, allocator);
        addRequestArgument(key, cb, allocator);
        addRequestArgument(longitude1, cb, allocator);
        addRequestArgument(latitude1, cb, allocator);
        addRequestArgument(member1, cb, allocator);
        addRequestArgument(longitude2, cb, allocator);
        addRequestArgument(latitude2, cb, allocator);
        addRequestArgument(member2, cb, allocator);
        final RedisRequest request = newRequest(RedisProtocolSupport.Command.GEOADD, cb);
        final RedisPartitionAttributesBuilder partitionAttributesBuilder = partitionAttributesBuilderFunction
                    .apply(RedisProtocolSupport.Command.GEOADD);
        partitionAttributesBuilder.addKey(key);
        final Single<Long> result = partitionedRedisClient.request(partitionAttributesBuilder.build(), request, Long.class);
        return result;
    }

    @Override
    public Single<Long> geoadd(@RedisProtocolSupport.Key final CharSequence key, final double longitude1, final double latitude1,
                               final CharSequence member1, final double longitude2, final double latitude2,
                               final CharSequence member2, final double longitude3, final double latitude3,
                               final CharSequence member3) {
        requireNonNull(key);
        requireNonNull(member1);
        requireNonNull(member2);
        requireNonNull(member3);
        final BufferAllocator allocator = partitionedRedisClient.getBufferAllocator();
        // Compute the number of request arguments, accounting for nullable ones
        int len = 11;
        final CompositeBuffer cb = newRequestCompositeBuffer(len, RedisProtocolSupport.Command.GEOADD, allocator);
        addRequestArgument(key, cb, allocator);
        addRequestArgument(longitude1, cb, allocator);
        addRequestArgument(latitude1, cb, allocator);
        addRequestArgument(member1, cb, allocator);
        addRequestArgument(longitude2, cb, allocator);
        addRequestArgument(latitude2, cb, allocator);
        addRequestArgument(member2, cb, allocator);
        addRequestArgument(longitude3, cb, allocator);
        addRequestArgument(latitude3, cb, allocator);
        addRequestArgument(member3, cb, allocator);
        final RedisRequest request = newRequest(RedisProtocolSupport.Command.GEOADD, cb);
        final RedisPartitionAttributesBuilder partitionAttributesBuilder = partitionAttributesBuilderFunction
                    .apply(RedisProtocolSupport.Command.GEOADD);
        partitionAttributesBuilder.addKey(key);
        final Single<Long> result = partitionedRedisClient.request(partitionAttributesBuilder.build(), request, Long.class);
        return result;
    }

    @Override
    public Single<Long> geoadd(@RedisProtocolSupport.Key final CharSequence key,
                               final Collection<RedisProtocolSupport.LongitudeLatitudeMember> longitudeLatitudeMembers) {
        requireNonNull(key);
        requireNonNull(longitudeLatitudeMembers);
        final BufferAllocator allocator = partitionedRedisClient.getBufferAllocator();
        // Compute the number of request arguments, accounting for nullable ones
        int len = 2;
        len += RedisProtocolSupport.LongitudeLatitudeMember.SIZE * longitudeLatitudeMembers.size();
        final CompositeBuffer cb = newRequestCompositeBuffer(len, RedisProtocolSupport.Command.GEOADD, allocator);
        addRequestArgument(key, cb, allocator);
        addRequestTupleArguments(longitudeLatitudeMembers, null, cb, allocator);
        final RedisRequest request = newRequest(RedisProtocolSupport.Command.GEOADD, cb);
        final RedisPartitionAttributesBuilder partitionAttributesBuilder = partitionAttributesBuilderFunction
                    .apply(RedisProtocolSupport.Command.GEOADD);
        partitionAttributesBuilder.addKey(key);
        final Single<Long> result = partitionedRedisClient.request(partitionAttributesBuilder.build(), request, Long.class);
        return result;
    }

    @Override
    public Single<Double> geodist(@RedisProtocolSupport.Key final CharSequence key, final CharSequence member1,
                                  final CharSequence member2) {
        requireNonNull(key);
        requireNonNull(member1);
        requireNonNull(member2);
        final BufferAllocator allocator = partitionedRedisClient.getBufferAllocator();
        // Compute the number of request arguments, accounting for nullable ones
        int len = 4;
        final CompositeBuffer cb = newRequestCompositeBuffer(len, RedisProtocolSupport.Command.GEODIST, allocator);
        addRequestArgument(key, cb, allocator);
        addRequestArgument(member1, cb, allocator);
        addRequestArgument(member2, cb, allocator);
        final RedisRequest request = newRequest(RedisProtocolSupport.Command.GEODIST, cb);
        final RedisPartitionAttributesBuilder partitionAttributesBuilder = partitionAttributesBuilderFunction
                    .apply(RedisProtocolSupport.Command.GEODIST);
        partitionAttributesBuilder.addKey(key);
        final Single<Double> result = partitionedRedisClient.request(partitionAttributesBuilder.build(), request, String.class)
                    .map(s -> s != null ? Double.valueOf(s) : null);
        return result;
    }

    @Override
    public Single<Double> geodist(@RedisProtocolSupport.Key final CharSequence key, final CharSequence member1,
                                  final CharSequence member2, @Nullable final CharSequence unit) {
        requireNonNull(key);
        requireNonNull(member1);
        requireNonNull(member2);
        final BufferAllocator allocator = partitionedRedisClient.getBufferAllocator();
        // Compute the number of request arguments, accounting for nullable ones
        int len = 4;
        if (unit != null) {
            len++;
        }
        final CompositeBuffer cb = newRequestCompositeBuffer(len, RedisProtocolSupport.Command.GEODIST, allocator);
        addRequestArgument(key, cb, allocator);
        addRequestArgument(member1, cb, allocator);
        addRequestArgument(member2, cb, allocator);
        if (unit != null) {
            addRequestArgument(unit, cb, allocator);
        }
        final RedisRequest request = newRequest(RedisProtocolSupport.Command.GEODIST, cb);
        final RedisPartitionAttributesBuilder partitionAttributesBuilder = partitionAttributesBuilderFunction
                    .apply(RedisProtocolSupport.Command.GEODIST);
        partitionAttributesBuilder.addKey(key);
        final Single<Double> result = partitionedRedisClient.request(partitionAttributesBuilder.build(), request, String.class)
                    .map(s -> s != null ? Double.valueOf(s) : null);
        return result;
    }

    @Override
    public <T> Single<List<T>> geohash(@RedisProtocolSupport.Key final CharSequence key, final CharSequence member) {
        requireNonNull(key);
        requireNonNull(member);
        final BufferAllocator allocator = partitionedRedisClient.getBufferAllocator();
        // Compute the number of request arguments, accounting for nullable ones
        int len = 3;
        final CompositeBuffer cb = newRequestCompositeBuffer(len, RedisProtocolSupport.Command.GEOHASH, allocator);
        addRequestArgument(key, cb, allocator);
        addRequestArgument(member, cb, allocator);
        final RedisRequest request = newRequest(RedisProtocolSupport.Command.GEOHASH, cb);
        final RedisPartitionAttributesBuilder partitionAttributesBuilder = partitionAttributesBuilderFunction
                    .apply(RedisProtocolSupport.Command.GEOHASH);
        partitionAttributesBuilder.addKey(key);
        final Single<List<T>> result = (Single) partitionedRedisClient.request(partitionAttributesBuilder.build(), request,
                    RedisUtils.ListWithBuffersCoercedToCharSequences.class);
        return result;
    }

    @Override
    public <T> Single<List<T>> geohash(@RedisProtocolSupport.Key final CharSequence key, final CharSequence member1,
                                       final CharSequence member2) {
        requireNonNull(key);
        requireNonNull(member1);
        requireNonNull(member2);
        final BufferAllocator allocator = partitionedRedisClient.getBufferAllocator();
        // Compute the number of request arguments, accounting for nullable ones
        int len = 4;
        final CompositeBuffer cb = newRequestCompositeBuffer(len, RedisProtocolSupport.Command.GEOHASH, allocator);
        addRequestArgument(key, cb, allocator);
        addRequestArgument(member1, cb, allocator);
        addRequestArgument(member2, cb, allocator);
        final RedisRequest request = newRequest(RedisProtocolSupport.Command.GEOHASH, cb);
        final RedisPartitionAttributesBuilder partitionAttributesBuilder = partitionAttributesBuilderFunction
                    .apply(RedisProtocolSupport.Command.GEOHASH);
        partitionAttributesBuilder.addKey(key);
        final Single<List<T>> result = (Single) partitionedRedisClient.request(partitionAttributesBuilder.build(), request,
                    RedisUtils.ListWithBuffersCoercedToCharSequences.class);
        return result;
    }

    @Override
    public <T> Single<List<T>> geohash(@RedisProtocolSupport.Key final CharSequence key, final CharSequence member1,
                                       final CharSequence member2, final CharSequence member3) {
        requireNonNull(key);
        requireNonNull(member1);
        requireNonNull(member2);
        requireNonNull(member3);
        final BufferAllocator allocator = partitionedRedisClient.getBufferAllocator();
        // Compute the number of request arguments, accounting for nullable ones
        int len = 5;
        final CompositeBuffer cb = newRequestCompositeBuffer(len, RedisProtocolSupport.Command.GEOHASH, allocator);
        addRequestArgument(key, cb, allocator);
        addRequestArgument(member1, cb, allocator);
        addRequestArgument(member2, cb, allocator);
        addRequestArgument(member3, cb, allocator);
        final RedisRequest request = newRequest(RedisProtocolSupport.Command.GEOHASH, cb);
        final RedisPartitionAttributesBuilder partitionAttributesBuilder = partitionAttributesBuilderFunction
                    .apply(RedisProtocolSupport.Command.GEOHASH);
        partitionAttributesBuilder.addKey(key);
        final Single<List<T>> result = (Single) partitionedRedisClient.request(partitionAttributesBuilder.build(), request,
                    RedisUtils.ListWithBuffersCoercedToCharSequences.class);
        return result;
    }

    @Override
    public <T> Single<List<T>> geohash(@RedisProtocolSupport.Key final CharSequence key,
                                       final Collection<? extends CharSequence> members) {
        requireNonNull(key);
        requireNonNull(members);
        final BufferAllocator allocator = partitionedRedisClient.getBufferAllocator();
        // Compute the number of request arguments, accounting for nullable ones
        int len = 2;
        len += members.size();
        final CompositeBuffer cb = newRequestCompositeBuffer(len, RedisProtocolSupport.Command.GEOHASH, allocator);
        addRequestArgument(key, cb, allocator);
        addRequestCharSequenceArguments(members, null, cb, allocator);
        final RedisRequest request = newRequest(RedisProtocolSupport.Command.GEOHASH, cb);
        final RedisPartitionAttributesBuilder partitionAttributesBuilder = partitionAttributesBuilderFunction
                    .apply(RedisProtocolSupport.Command.GEOHASH);
        partitionAttributesBuilder.addKey(key);
        final Single<List<T>> result = (Single) partitionedRedisClient.request(partitionAttributesBuilder.build(), request,
                    RedisUtils.ListWithBuffersCoercedToCharSequences.class);
        return result;
    }

    @Override
    public <T> Single<List<T>> geopos(@RedisProtocolSupport.Key final CharSequence key, final CharSequence member) {
        requireNonNull(key);
        requireNonNull(member);
        final BufferAllocator allocator = partitionedRedisClient.getBufferAllocator();
        // Compute the number of request arguments, accounting for nullable ones
        int len = 3;
        final CompositeBuffer cb = newRequestCompositeBuffer(len, RedisProtocolSupport.Command.GEOPOS, allocator);
        addRequestArgument(key, cb, allocator);
        addRequestArgument(member, cb, allocator);
        final RedisRequest request = newRequest(RedisProtocolSupport.Command.GEOPOS, cb);
        final RedisPartitionAttributesBuilder partitionAttributesBuilder = partitionAttributesBuilderFunction
                    .apply(RedisProtocolSupport.Command.GEOPOS);
        partitionAttributesBuilder.addKey(key);
        final Single<List<T>> result = (Single) partitionedRedisClient.request(partitionAttributesBuilder.build(), request,
                    RedisUtils.ListWithBuffersCoercedToCharSequences.class);
        return result;
    }

    @Override
    public <T> Single<List<T>> geopos(@RedisProtocolSupport.Key final CharSequence key, final CharSequence member1,
                                      final CharSequence member2) {
        requireNonNull(key);
        requireNonNull(member1);
        requireNonNull(member2);
        final BufferAllocator allocator = partitionedRedisClient.getBufferAllocator();
        // Compute the number of request arguments, accounting for nullable ones
        int len = 4;
        final CompositeBuffer cb = newRequestCompositeBuffer(len, RedisProtocolSupport.Command.GEOPOS, allocator);
        addRequestArgument(key, cb, allocator);
        addRequestArgument(member1, cb, allocator);
        addRequestArgument(member2, cb, allocator);
        final RedisRequest request = newRequest(RedisProtocolSupport.Command.GEOPOS, cb);
        final RedisPartitionAttributesBuilder partitionAttributesBuilder = partitionAttributesBuilderFunction
                    .apply(RedisProtocolSupport.Command.GEOPOS);
        partitionAttributesBuilder.addKey(key);
        final Single<List<T>> result = (Single) partitionedRedisClient.request(partitionAttributesBuilder.build(), request,
                    RedisUtils.ListWithBuffersCoercedToCharSequences.class);
        return result;
    }

    @Override
    public <T> Single<List<T>> geopos(@RedisProtocolSupport.Key final CharSequence key, final CharSequence member1,
                                      final CharSequence member2, final CharSequence member3) {
        requireNonNull(key);
        requireNonNull(member1);
        requireNonNull(member2);
        requireNonNull(member3);
        final BufferAllocator allocator = partitionedRedisClient.getBufferAllocator();
        // Compute the number of request arguments, accounting for nullable ones
        int len = 5;
        final CompositeBuffer cb = newRequestCompositeBuffer(len, RedisProtocolSupport.Command.GEOPOS, allocator);
        addRequestArgument(key, cb, allocator);
        addRequestArgument(member1, cb, allocator);
        addRequestArgument(member2, cb, allocator);
        addRequestArgument(member3, cb, allocator);
        final RedisRequest request = newRequest(RedisProtocolSupport.Command.GEOPOS, cb);
        final RedisPartitionAttributesBuilder partitionAttributesBuilder = partitionAttributesBuilderFunction
                    .apply(RedisProtocolSupport.Command.GEOPOS);
        partitionAttributesBuilder.addKey(key);
        final Single<List<T>> result = (Single) partitionedRedisClient.request(partitionAttributesBuilder.build(), request,
                    RedisUtils.ListWithBuffersCoercedToCharSequences.class);
        return result;
    }

    @Override
    public <T> Single<List<T>> geopos(@RedisProtocolSupport.Key final CharSequence key,
                                      final Collection<? extends CharSequence> members) {
        requireNonNull(key);
        requireNonNull(members);
        final BufferAllocator allocator = partitionedRedisClient.getBufferAllocator();
        // Compute the number of request arguments, accounting for nullable ones
        int len = 2;
        len += members.size();
        final CompositeBuffer cb = newRequestCompositeBuffer(len, RedisProtocolSupport.Command.GEOPOS, allocator);
        addRequestArgument(key, cb, allocator);
        addRequestCharSequenceArguments(members, null, cb, allocator);
        final RedisRequest request = newRequest(RedisProtocolSupport.Command.GEOPOS, cb);
        final RedisPartitionAttributesBuilder partitionAttributesBuilder = partitionAttributesBuilderFunction
                    .apply(RedisProtocolSupport.Command.GEOPOS);
        partitionAttributesBuilder.addKey(key);
        final Single<List<T>> result = (Single) partitionedRedisClient.request(partitionAttributesBuilder.build(), request,
                    RedisUtils.ListWithBuffersCoercedToCharSequences.class);
        return result;
    }

    @Override
    public <T> Single<List<T>> georadius(@RedisProtocolSupport.Key final CharSequence key, final double longitude,
                                         final double latitude, final double radius,
                                         final RedisProtocolSupport.GeoradiusUnit unit) {
        requireNonNull(key);
        requireNonNull(unit);
        final BufferAllocator allocator = partitionedRedisClient.getBufferAllocator();
        // Compute the number of request arguments, accounting for nullable ones
        int len = 6;
        final CompositeBuffer cb = newRequestCompositeBuffer(len, RedisProtocolSupport.Command.GEORADIUS, allocator);
        addRequestArgument(key, cb, allocator);
        addRequestArgument(longitude, cb, allocator);
        addRequestArgument(latitude, cb, allocator);
        addRequestArgument(radius, cb, allocator);
        addRequestArgument(unit, cb, allocator);
        final RedisRequest request = newRequest(RedisProtocolSupport.Command.GEORADIUS, cb);
        final RedisPartitionAttributesBuilder partitionAttributesBuilder = partitionAttributesBuilderFunction
                    .apply(RedisProtocolSupport.Command.GEORADIUS);
        partitionAttributesBuilder.addKey(key);
        final Single<List<T>> result = (Single) partitionedRedisClient.request(partitionAttributesBuilder.build(), request,
                    RedisUtils.ListWithBuffersCoercedToCharSequences.class);
        return result;
    }

    @Override
    public <T> Single<List<T>> georadius(@RedisProtocolSupport.Key final CharSequence key, final double longitude,
                                         final double latitude, final double radius,
                                         final RedisProtocolSupport.GeoradiusUnit unit,
                                         @Nullable final RedisProtocolSupport.GeoradiusWithcoord withcoord,
                                         @Nullable final RedisProtocolSupport.GeoradiusWithdist withdist,
                                         @Nullable final RedisProtocolSupport.GeoradiusWithhash withhash,
                                         @Nullable final Long count, @Nullable final RedisProtocolSupport.GeoradiusOrder order,
                                         @Nullable @RedisProtocolSupport.Key final CharSequence storeKey,
                                         @Nullable @RedisProtocolSupport.Key final CharSequence storedistKey) {
        requireNonNull(key);
        requireNonNull(unit);
        final BufferAllocator allocator = partitionedRedisClient.getBufferAllocator();
        // Compute the number of request arguments, accounting for nullable ones
        int len = 6;
        if (withcoord != null) {
            len++;
        }
        if (withdist != null) {
            len++;
        }
        if (withhash != null) {
            len++;
        }
        if (count != null) {
            len += 2;
        }
        if (order != null) {
            len++;
        }
        if (storeKey != null) {
            len += 2;
        }
        if (storedistKey != null) {
            len += 2;
        }
        final CompositeBuffer cb = newRequestCompositeBuffer(len, RedisProtocolSupport.Command.GEORADIUS, allocator);
        addRequestArgument(key, cb, allocator);
        addRequestArgument(longitude, cb, allocator);
        addRequestArgument(latitude, cb, allocator);
        addRequestArgument(radius, cb, allocator);
        addRequestArgument(unit, cb, allocator);
        if (withcoord != null) {
            addRequestArgument(withcoord, cb, allocator);
        }
        if (withdist != null) {
            addRequestArgument(withdist, cb, allocator);
        }
        if (withhash != null) {
            addRequestArgument(withhash, cb, allocator);
        }
        if (count != null) {
            addRequestArgument(RedisProtocolSupport.SubCommand.COUNT, cb, allocator);
            addRequestArgument(count, cb, allocator);
        }
        if (order != null) {
            addRequestArgument(order, cb, allocator);
        }
        if (storeKey != null) {
            addRequestArgument(RedisProtocolSupport.SubCommand.STORE, cb, allocator);
            addRequestArgument(storeKey, cb, allocator);
        }
        if (storedistKey != null) {
            addRequestArgument(RedisProtocolSupport.SubCommand.STOREDIST, cb, allocator);
            addRequestArgument(storedistKey, cb, allocator);
        }
        final RedisRequest request = newRequest(RedisProtocolSupport.Command.GEORADIUS, cb);
        final RedisPartitionAttributesBuilder partitionAttributesBuilder = partitionAttributesBuilderFunction
                    .apply(RedisProtocolSupport.Command.GEORADIUS);
        partitionAttributesBuilder.addKey(key);
        if (storeKey != null) {
            partitionAttributesBuilder.addKey(storeKey);
        }
        if (storedistKey != null) {
            partitionAttributesBuilder.addKey(storedistKey);
        }
        final Single<List<T>> result = (Single) partitionedRedisClient.request(partitionAttributesBuilder.build(), request,
                    RedisUtils.ListWithBuffersCoercedToCharSequences.class);
        return result;
    }

    @Override
    public <T> Single<List<T>> georadiusbymember(@RedisProtocolSupport.Key final CharSequence key, final CharSequence member,
                                                 final double radius, final RedisProtocolSupport.GeoradiusbymemberUnit unit) {
        requireNonNull(key);
        requireNonNull(member);
        requireNonNull(unit);
        final BufferAllocator allocator = partitionedRedisClient.getBufferAllocator();
        // Compute the number of request arguments, accounting for nullable ones
        int len = 5;
        final CompositeBuffer cb = newRequestCompositeBuffer(len, RedisProtocolSupport.Command.GEORADIUSBYMEMBER, allocator);
        addRequestArgument(key, cb, allocator);
        addRequestArgument(member, cb, allocator);
        addRequestArgument(radius, cb, allocator);
        addRequestArgument(unit, cb, allocator);
        final RedisRequest request = newRequest(RedisProtocolSupport.Command.GEORADIUSBYMEMBER, cb);
        final RedisPartitionAttributesBuilder partitionAttributesBuilder = partitionAttributesBuilderFunction
                    .apply(RedisProtocolSupport.Command.GEORADIUSBYMEMBER);
        partitionAttributesBuilder.addKey(key);
        final Single<List<T>> result = (Single) partitionedRedisClient.request(partitionAttributesBuilder.build(), request,
                    RedisUtils.ListWithBuffersCoercedToCharSequences.class);
        return result;
    }

    @Override
    public <T> Single<List<T>> georadiusbymember(@RedisProtocolSupport.Key final CharSequence key, final CharSequence member,
                                                 final double radius, final RedisProtocolSupport.GeoradiusbymemberUnit unit,
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
        final BufferAllocator allocator = partitionedRedisClient.getBufferAllocator();
        // Compute the number of request arguments, accounting for nullable ones
        int len = 5;
        if (withcoord != null) {
            len++;
        }
        if (withdist != null) {
            len++;
        }
        if (withhash != null) {
            len++;
        }
        if (count != null) {
            len += 2;
        }
        if (order != null) {
            len++;
        }
        if (storeKey != null) {
            len += 2;
        }
        if (storedistKey != null) {
            len += 2;
        }
        final CompositeBuffer cb = newRequestCompositeBuffer(len, RedisProtocolSupport.Command.GEORADIUSBYMEMBER, allocator);
        addRequestArgument(key, cb, allocator);
        addRequestArgument(member, cb, allocator);
        addRequestArgument(radius, cb, allocator);
        addRequestArgument(unit, cb, allocator);
        if (withcoord != null) {
            addRequestArgument(withcoord, cb, allocator);
        }
        if (withdist != null) {
            addRequestArgument(withdist, cb, allocator);
        }
        if (withhash != null) {
            addRequestArgument(withhash, cb, allocator);
        }
        if (count != null) {
            addRequestArgument(RedisProtocolSupport.SubCommand.COUNT, cb, allocator);
            addRequestArgument(count, cb, allocator);
        }
        if (order != null) {
            addRequestArgument(order, cb, allocator);
        }
        if (storeKey != null) {
            addRequestArgument(RedisProtocolSupport.SubCommand.STORE, cb, allocator);
            addRequestArgument(storeKey, cb, allocator);
        }
        if (storedistKey != null) {
            addRequestArgument(RedisProtocolSupport.SubCommand.STOREDIST, cb, allocator);
            addRequestArgument(storedistKey, cb, allocator);
        }
        final RedisRequest request = newRequest(RedisProtocolSupport.Command.GEORADIUSBYMEMBER, cb);
        final RedisPartitionAttributesBuilder partitionAttributesBuilder = partitionAttributesBuilderFunction
                    .apply(RedisProtocolSupport.Command.GEORADIUSBYMEMBER);
        partitionAttributesBuilder.addKey(key);
        if (storeKey != null) {
            partitionAttributesBuilder.addKey(storeKey);
        }
        if (storedistKey != null) {
            partitionAttributesBuilder.addKey(storedistKey);
        }
        final Single<List<T>> result = (Single) partitionedRedisClient.request(partitionAttributesBuilder.build(), request,
                    RedisUtils.ListWithBuffersCoercedToCharSequences.class);
        return result;
    }

    @Override
    public Single<String> get(@RedisProtocolSupport.Key final CharSequence key) {
        requireNonNull(key);
        final BufferAllocator allocator = partitionedRedisClient.getBufferAllocator();
        // Compute the number of request arguments, accounting for nullable ones
        int len = 2;
        final CompositeBuffer cb = newRequestCompositeBuffer(len, RedisProtocolSupport.Command.GET, allocator);
        addRequestArgument(key, cb, allocator);
        final RedisRequest request = newRequest(RedisProtocolSupport.Command.GET, cb);
        final RedisPartitionAttributesBuilder partitionAttributesBuilder = partitionAttributesBuilderFunction
                    .apply(RedisProtocolSupport.Command.GET);
        partitionAttributesBuilder.addKey(key);
        final Single<String> result = partitionedRedisClient.request(partitionAttributesBuilder.build(), request, String.class);
        return result;
    }

    @Override
    public Single<Long> getbit(@RedisProtocolSupport.Key final CharSequence key, final long offset) {
        requireNonNull(key);
        final BufferAllocator allocator = partitionedRedisClient.getBufferAllocator();
        // Compute the number of request arguments, accounting for nullable ones
        int len = 3;
        final CompositeBuffer cb = newRequestCompositeBuffer(len, RedisProtocolSupport.Command.GETBIT, allocator);
        addRequestArgument(key, cb, allocator);
        addRequestArgument(offset, cb, allocator);
        final RedisRequest request = newRequest(RedisProtocolSupport.Command.GETBIT, cb);
        final RedisPartitionAttributesBuilder partitionAttributesBuilder = partitionAttributesBuilderFunction
                    .apply(RedisProtocolSupport.Command.GETBIT);
        partitionAttributesBuilder.addKey(key);
        final Single<Long> result = partitionedRedisClient.request(partitionAttributesBuilder.build(), request, Long.class);
        return result;
    }

    @Override
    public Single<String> getrange(@RedisProtocolSupport.Key final CharSequence key, final long start, final long end) {
        requireNonNull(key);
        final BufferAllocator allocator = partitionedRedisClient.getBufferAllocator();
        // Compute the number of request arguments, accounting for nullable ones
        int len = 4;
        final CompositeBuffer cb = newRequestCompositeBuffer(len, RedisProtocolSupport.Command.GETRANGE, allocator);
        addRequestArgument(key, cb, allocator);
        addRequestArgument(start, cb, allocator);
        addRequestArgument(end, cb, allocator);
        final RedisRequest request = newRequest(RedisProtocolSupport.Command.GETRANGE, cb);
        final RedisPartitionAttributesBuilder partitionAttributesBuilder = partitionAttributesBuilderFunction
                    .apply(RedisProtocolSupport.Command.GETRANGE);
        partitionAttributesBuilder.addKey(key);
        final Single<String> result = partitionedRedisClient.request(partitionAttributesBuilder.build(), request, String.class);
        return result;
    }

    @Override
    public Single<String> getset(@RedisProtocolSupport.Key final CharSequence key, final CharSequence value) {
        requireNonNull(key);
        requireNonNull(value);
        final BufferAllocator allocator = partitionedRedisClient.getBufferAllocator();
        // Compute the number of request arguments, accounting for nullable ones
        int len = 3;
        final CompositeBuffer cb = newRequestCompositeBuffer(len, RedisProtocolSupport.Command.GETSET, allocator);
        addRequestArgument(key, cb, allocator);
        addRequestArgument(value, cb, allocator);
        final RedisRequest request = newRequest(RedisProtocolSupport.Command.GETSET, cb);
        final RedisPartitionAttributesBuilder partitionAttributesBuilder = partitionAttributesBuilderFunction
                    .apply(RedisProtocolSupport.Command.GETSET);
        partitionAttributesBuilder.addKey(key);
        final Single<String> result = partitionedRedisClient.request(partitionAttributesBuilder.build(), request, String.class);
        return result;
    }

    @Override
    public Single<Long> hdel(@RedisProtocolSupport.Key final CharSequence key, final CharSequence field) {
        requireNonNull(key);
        requireNonNull(field);
        final BufferAllocator allocator = partitionedRedisClient.getBufferAllocator();
        // Compute the number of request arguments, accounting for nullable ones
        int len = 3;
        final CompositeBuffer cb = newRequestCompositeBuffer(len, RedisProtocolSupport.Command.HDEL, allocator);
        addRequestArgument(key, cb, allocator);
        addRequestArgument(field, cb, allocator);
        final RedisRequest request = newRequest(RedisProtocolSupport.Command.HDEL, cb);
        final RedisPartitionAttributesBuilder partitionAttributesBuilder = partitionAttributesBuilderFunction
                    .apply(RedisProtocolSupport.Command.HDEL);
        partitionAttributesBuilder.addKey(key);
        final Single<Long> result = partitionedRedisClient.request(partitionAttributesBuilder.build(), request, Long.class);
        return result;
    }

    @Override
    public Single<Long> hdel(@RedisProtocolSupport.Key final CharSequence key, final CharSequence field1,
                             final CharSequence field2) {
        requireNonNull(key);
        requireNonNull(field1);
        requireNonNull(field2);
        final BufferAllocator allocator = partitionedRedisClient.getBufferAllocator();
        // Compute the number of request arguments, accounting for nullable ones
        int len = 4;
        final CompositeBuffer cb = newRequestCompositeBuffer(len, RedisProtocolSupport.Command.HDEL, allocator);
        addRequestArgument(key, cb, allocator);
        addRequestArgument(field1, cb, allocator);
        addRequestArgument(field2, cb, allocator);
        final RedisRequest request = newRequest(RedisProtocolSupport.Command.HDEL, cb);
        final RedisPartitionAttributesBuilder partitionAttributesBuilder = partitionAttributesBuilderFunction
                    .apply(RedisProtocolSupport.Command.HDEL);
        partitionAttributesBuilder.addKey(key);
        final Single<Long> result = partitionedRedisClient.request(partitionAttributesBuilder.build(), request, Long.class);
        return result;
    }

    @Override
    public Single<Long> hdel(@RedisProtocolSupport.Key final CharSequence key, final CharSequence field1,
                             final CharSequence field2, final CharSequence field3) {
        requireNonNull(key);
        requireNonNull(field1);
        requireNonNull(field2);
        requireNonNull(field3);
        final BufferAllocator allocator = partitionedRedisClient.getBufferAllocator();
        // Compute the number of request arguments, accounting for nullable ones
        int len = 5;
        final CompositeBuffer cb = newRequestCompositeBuffer(len, RedisProtocolSupport.Command.HDEL, allocator);
        addRequestArgument(key, cb, allocator);
        addRequestArgument(field1, cb, allocator);
        addRequestArgument(field2, cb, allocator);
        addRequestArgument(field3, cb, allocator);
        final RedisRequest request = newRequest(RedisProtocolSupport.Command.HDEL, cb);
        final RedisPartitionAttributesBuilder partitionAttributesBuilder = partitionAttributesBuilderFunction
                    .apply(RedisProtocolSupport.Command.HDEL);
        partitionAttributesBuilder.addKey(key);
        final Single<Long> result = partitionedRedisClient.request(partitionAttributesBuilder.build(), request, Long.class);
        return result;
    }

    @Override
    public Single<Long> hdel(@RedisProtocolSupport.Key final CharSequence key, final Collection<? extends CharSequence> fields) {
        requireNonNull(key);
        requireNonNull(fields);
        final BufferAllocator allocator = partitionedRedisClient.getBufferAllocator();
        // Compute the number of request arguments, accounting for nullable ones
        int len = 2;
        len += fields.size();
        final CompositeBuffer cb = newRequestCompositeBuffer(len, RedisProtocolSupport.Command.HDEL, allocator);
        addRequestArgument(key, cb, allocator);
        addRequestCharSequenceArguments(fields, null, cb, allocator);
        final RedisRequest request = newRequest(RedisProtocolSupport.Command.HDEL, cb);
        final RedisPartitionAttributesBuilder partitionAttributesBuilder = partitionAttributesBuilderFunction
                    .apply(RedisProtocolSupport.Command.HDEL);
        partitionAttributesBuilder.addKey(key);
        final Single<Long> result = partitionedRedisClient.request(partitionAttributesBuilder.build(), request, Long.class);
        return result;
    }

    @Override
    public Single<Long> hexists(@RedisProtocolSupport.Key final CharSequence key, final CharSequence field) {
        requireNonNull(key);
        requireNonNull(field);
        final BufferAllocator allocator = partitionedRedisClient.getBufferAllocator();
        // Compute the number of request arguments, accounting for nullable ones
        int len = 3;
        final CompositeBuffer cb = newRequestCompositeBuffer(len, RedisProtocolSupport.Command.HEXISTS, allocator);
        addRequestArgument(key, cb, allocator);
        addRequestArgument(field, cb, allocator);
        final RedisRequest request = newRequest(RedisProtocolSupport.Command.HEXISTS, cb);
        final RedisPartitionAttributesBuilder partitionAttributesBuilder = partitionAttributesBuilderFunction
                    .apply(RedisProtocolSupport.Command.HEXISTS);
        partitionAttributesBuilder.addKey(key);
        final Single<Long> result = partitionedRedisClient.request(partitionAttributesBuilder.build(), request, Long.class);
        return result;
    }

    @Override
    public Single<String> hget(@RedisProtocolSupport.Key final CharSequence key, final CharSequence field) {
        requireNonNull(key);
        requireNonNull(field);
        final BufferAllocator allocator = partitionedRedisClient.getBufferAllocator();
        // Compute the number of request arguments, accounting for nullable ones
        int len = 3;
        final CompositeBuffer cb = newRequestCompositeBuffer(len, RedisProtocolSupport.Command.HGET, allocator);
        addRequestArgument(key, cb, allocator);
        addRequestArgument(field, cb, allocator);
        final RedisRequest request = newRequest(RedisProtocolSupport.Command.HGET, cb);
        final RedisPartitionAttributesBuilder partitionAttributesBuilder = partitionAttributesBuilderFunction
                    .apply(RedisProtocolSupport.Command.HGET);
        partitionAttributesBuilder.addKey(key);
        final Single<String> result = partitionedRedisClient.request(partitionAttributesBuilder.build(), request, String.class);
        return result;
    }

    @Override
    public <T> Single<List<T>> hgetall(@RedisProtocolSupport.Key final CharSequence key) {
        requireNonNull(key);
        final BufferAllocator allocator = partitionedRedisClient.getBufferAllocator();
        // Compute the number of request arguments, accounting for nullable ones
        int len = 2;
        final CompositeBuffer cb = newRequestCompositeBuffer(len, RedisProtocolSupport.Command.HGETALL, allocator);
        addRequestArgument(key, cb, allocator);
        final RedisRequest request = newRequest(RedisProtocolSupport.Command.HGETALL, cb);
        final RedisPartitionAttributesBuilder partitionAttributesBuilder = partitionAttributesBuilderFunction
                    .apply(RedisProtocolSupport.Command.HGETALL);
        partitionAttributesBuilder.addKey(key);
        final Single<List<T>> result = (Single) partitionedRedisClient.request(partitionAttributesBuilder.build(), request,
                    RedisUtils.ListWithBuffersCoercedToCharSequences.class);
        return result;
    }

    @Override
    public Single<Long> hincrby(@RedisProtocolSupport.Key final CharSequence key, final CharSequence field,
                                final long increment) {
        requireNonNull(key);
        requireNonNull(field);
        final BufferAllocator allocator = partitionedRedisClient.getBufferAllocator();
        // Compute the number of request arguments, accounting for nullable ones
        int len = 4;
        final CompositeBuffer cb = newRequestCompositeBuffer(len, RedisProtocolSupport.Command.HINCRBY, allocator);
        addRequestArgument(key, cb, allocator);
        addRequestArgument(field, cb, allocator);
        addRequestArgument(increment, cb, allocator);
        final RedisRequest request = newRequest(RedisProtocolSupport.Command.HINCRBY, cb);
        final RedisPartitionAttributesBuilder partitionAttributesBuilder = partitionAttributesBuilderFunction
                    .apply(RedisProtocolSupport.Command.HINCRBY);
        partitionAttributesBuilder.addKey(key);
        final Single<Long> result = partitionedRedisClient.request(partitionAttributesBuilder.build(), request, Long.class);
        return result;
    }

    @Override
    public Single<Double> hincrbyfloat(@RedisProtocolSupport.Key final CharSequence key, final CharSequence field,
                                       final double increment) {
        requireNonNull(key);
        requireNonNull(field);
        final BufferAllocator allocator = partitionedRedisClient.getBufferAllocator();
        // Compute the number of request arguments, accounting for nullable ones
        int len = 4;
        final CompositeBuffer cb = newRequestCompositeBuffer(len, RedisProtocolSupport.Command.HINCRBYFLOAT, allocator);
        addRequestArgument(key, cb, allocator);
        addRequestArgument(field, cb, allocator);
        addRequestArgument(increment, cb, allocator);
        final RedisRequest request = newRequest(RedisProtocolSupport.Command.HINCRBYFLOAT, cb);
        final RedisPartitionAttributesBuilder partitionAttributesBuilder = partitionAttributesBuilderFunction
                    .apply(RedisProtocolSupport.Command.HINCRBYFLOAT);
        partitionAttributesBuilder.addKey(key);
        final Single<Double> result = partitionedRedisClient.request(partitionAttributesBuilder.build(), request, String.class)
                    .map(s -> s != null ? Double.valueOf(s) : null);
        return result;
    }

    @Override
    public <T> Single<List<T>> hkeys(@RedisProtocolSupport.Key final CharSequence key) {
        requireNonNull(key);
        final BufferAllocator allocator = partitionedRedisClient.getBufferAllocator();
        // Compute the number of request arguments, accounting for nullable ones
        int len = 2;
        final CompositeBuffer cb = newRequestCompositeBuffer(len, RedisProtocolSupport.Command.HKEYS, allocator);
        addRequestArgument(key, cb, allocator);
        final RedisRequest request = newRequest(RedisProtocolSupport.Command.HKEYS, cb);
        final RedisPartitionAttributesBuilder partitionAttributesBuilder = partitionAttributesBuilderFunction
                    .apply(RedisProtocolSupport.Command.HKEYS);
        partitionAttributesBuilder.addKey(key);
        final Single<List<T>> result = (Single) partitionedRedisClient.request(partitionAttributesBuilder.build(), request,
                    RedisUtils.ListWithBuffersCoercedToCharSequences.class);
        return result;
    }

    @Override
    public Single<Long> hlen(@RedisProtocolSupport.Key final CharSequence key) {
        requireNonNull(key);
        final BufferAllocator allocator = partitionedRedisClient.getBufferAllocator();
        // Compute the number of request arguments, accounting for nullable ones
        int len = 2;
        final CompositeBuffer cb = newRequestCompositeBuffer(len, RedisProtocolSupport.Command.HLEN, allocator);
        addRequestArgument(key, cb, allocator);
        final RedisRequest request = newRequest(RedisProtocolSupport.Command.HLEN, cb);
        final RedisPartitionAttributesBuilder partitionAttributesBuilder = partitionAttributesBuilderFunction
                    .apply(RedisProtocolSupport.Command.HLEN);
        partitionAttributesBuilder.addKey(key);
        final Single<Long> result = partitionedRedisClient.request(partitionAttributesBuilder.build(), request, Long.class);
        return result;
    }

    @Override
    public <T> Single<List<T>> hmget(@RedisProtocolSupport.Key final CharSequence key, final CharSequence field) {
        requireNonNull(key);
        requireNonNull(field);
        final BufferAllocator allocator = partitionedRedisClient.getBufferAllocator();
        // Compute the number of request arguments, accounting for nullable ones
        int len = 3;
        final CompositeBuffer cb = newRequestCompositeBuffer(len, RedisProtocolSupport.Command.HMGET, allocator);
        addRequestArgument(key, cb, allocator);
        addRequestArgument(field, cb, allocator);
        final RedisRequest request = newRequest(RedisProtocolSupport.Command.HMGET, cb);
        final RedisPartitionAttributesBuilder partitionAttributesBuilder = partitionAttributesBuilderFunction
                    .apply(RedisProtocolSupport.Command.HMGET);
        partitionAttributesBuilder.addKey(key);
        final Single<List<T>> result = (Single) partitionedRedisClient.request(partitionAttributesBuilder.build(), request,
                    RedisUtils.ListWithBuffersCoercedToCharSequences.class);
        return result;
    }

    @Override
    public <T> Single<List<T>> hmget(@RedisProtocolSupport.Key final CharSequence key, final CharSequence field1,
                                     final CharSequence field2) {
        requireNonNull(key);
        requireNonNull(field1);
        requireNonNull(field2);
        final BufferAllocator allocator = partitionedRedisClient.getBufferAllocator();
        // Compute the number of request arguments, accounting for nullable ones
        int len = 4;
        final CompositeBuffer cb = newRequestCompositeBuffer(len, RedisProtocolSupport.Command.HMGET, allocator);
        addRequestArgument(key, cb, allocator);
        addRequestArgument(field1, cb, allocator);
        addRequestArgument(field2, cb, allocator);
        final RedisRequest request = newRequest(RedisProtocolSupport.Command.HMGET, cb);
        final RedisPartitionAttributesBuilder partitionAttributesBuilder = partitionAttributesBuilderFunction
                    .apply(RedisProtocolSupport.Command.HMGET);
        partitionAttributesBuilder.addKey(key);
        final Single<List<T>> result = (Single) partitionedRedisClient.request(partitionAttributesBuilder.build(), request,
                    RedisUtils.ListWithBuffersCoercedToCharSequences.class);
        return result;
    }

    @Override
    public <T> Single<List<T>> hmget(@RedisProtocolSupport.Key final CharSequence key, final CharSequence field1,
                                     final CharSequence field2, final CharSequence field3) {
        requireNonNull(key);
        requireNonNull(field1);
        requireNonNull(field2);
        requireNonNull(field3);
        final BufferAllocator allocator = partitionedRedisClient.getBufferAllocator();
        // Compute the number of request arguments, accounting for nullable ones
        int len = 5;
        final CompositeBuffer cb = newRequestCompositeBuffer(len, RedisProtocolSupport.Command.HMGET, allocator);
        addRequestArgument(key, cb, allocator);
        addRequestArgument(field1, cb, allocator);
        addRequestArgument(field2, cb, allocator);
        addRequestArgument(field3, cb, allocator);
        final RedisRequest request = newRequest(RedisProtocolSupport.Command.HMGET, cb);
        final RedisPartitionAttributesBuilder partitionAttributesBuilder = partitionAttributesBuilderFunction
                    .apply(RedisProtocolSupport.Command.HMGET);
        partitionAttributesBuilder.addKey(key);
        final Single<List<T>> result = (Single) partitionedRedisClient.request(partitionAttributesBuilder.build(), request,
                    RedisUtils.ListWithBuffersCoercedToCharSequences.class);
        return result;
    }

    @Override
    public <T> Single<List<T>> hmget(@RedisProtocolSupport.Key final CharSequence key,
                                     final Collection<? extends CharSequence> fields) {
        requireNonNull(key);
        requireNonNull(fields);
        final BufferAllocator allocator = partitionedRedisClient.getBufferAllocator();
        // Compute the number of request arguments, accounting for nullable ones
        int len = 2;
        len += fields.size();
        final CompositeBuffer cb = newRequestCompositeBuffer(len, RedisProtocolSupport.Command.HMGET, allocator);
        addRequestArgument(key, cb, allocator);
        addRequestCharSequenceArguments(fields, null, cb, allocator);
        final RedisRequest request = newRequest(RedisProtocolSupport.Command.HMGET, cb);
        final RedisPartitionAttributesBuilder partitionAttributesBuilder = partitionAttributesBuilderFunction
                    .apply(RedisProtocolSupport.Command.HMGET);
        partitionAttributesBuilder.addKey(key);
        final Single<List<T>> result = (Single) partitionedRedisClient.request(partitionAttributesBuilder.build(), request,
                    RedisUtils.ListWithBuffersCoercedToCharSequences.class);
        return result;
    }

    @Override
    public Single<String> hmset(@RedisProtocolSupport.Key final CharSequence key, final CharSequence field,
                                final CharSequence value) {
        requireNonNull(key);
        requireNonNull(field);
        requireNonNull(value);
        final BufferAllocator allocator = partitionedRedisClient.getBufferAllocator();
        // Compute the number of request arguments, accounting for nullable ones
        int len = 4;
        final CompositeBuffer cb = newRequestCompositeBuffer(len, RedisProtocolSupport.Command.HMSET, allocator);
        addRequestArgument(key, cb, allocator);
        addRequestArgument(field, cb, allocator);
        addRequestArgument(value, cb, allocator);
        final RedisRequest request = newRequest(RedisProtocolSupport.Command.HMSET, cb);
        final RedisPartitionAttributesBuilder partitionAttributesBuilder = partitionAttributesBuilderFunction
                    .apply(RedisProtocolSupport.Command.HMSET);
        partitionAttributesBuilder.addKey(key);
        final Single<String> result = partitionedRedisClient.request(partitionAttributesBuilder.build(), request, String.class);
        return result;
    }

    @Override
    public Single<String> hmset(@RedisProtocolSupport.Key final CharSequence key, final CharSequence field1,
                                final CharSequence value1, final CharSequence field2, final CharSequence value2) {
        requireNonNull(key);
        requireNonNull(field1);
        requireNonNull(value1);
        requireNonNull(field2);
        requireNonNull(value2);
        final BufferAllocator allocator = partitionedRedisClient.getBufferAllocator();
        // Compute the number of request arguments, accounting for nullable ones
        int len = 6;
        final CompositeBuffer cb = newRequestCompositeBuffer(len, RedisProtocolSupport.Command.HMSET, allocator);
        addRequestArgument(key, cb, allocator);
        addRequestArgument(field1, cb, allocator);
        addRequestArgument(value1, cb, allocator);
        addRequestArgument(field2, cb, allocator);
        addRequestArgument(value2, cb, allocator);
        final RedisRequest request = newRequest(RedisProtocolSupport.Command.HMSET, cb);
        final RedisPartitionAttributesBuilder partitionAttributesBuilder = partitionAttributesBuilderFunction
                    .apply(RedisProtocolSupport.Command.HMSET);
        partitionAttributesBuilder.addKey(key);
        final Single<String> result = partitionedRedisClient.request(partitionAttributesBuilder.build(), request, String.class);
        return result;
    }

    @Override
    public Single<String> hmset(@RedisProtocolSupport.Key final CharSequence key, final CharSequence field1,
                                final CharSequence value1, final CharSequence field2, final CharSequence value2,
                                final CharSequence field3, final CharSequence value3) {
        requireNonNull(key);
        requireNonNull(field1);
        requireNonNull(value1);
        requireNonNull(field2);
        requireNonNull(value2);
        requireNonNull(field3);
        requireNonNull(value3);
        final BufferAllocator allocator = partitionedRedisClient.getBufferAllocator();
        // Compute the number of request arguments, accounting for nullable ones
        int len = 8;
        final CompositeBuffer cb = newRequestCompositeBuffer(len, RedisProtocolSupport.Command.HMSET, allocator);
        addRequestArgument(key, cb, allocator);
        addRequestArgument(field1, cb, allocator);
        addRequestArgument(value1, cb, allocator);
        addRequestArgument(field2, cb, allocator);
        addRequestArgument(value2, cb, allocator);
        addRequestArgument(field3, cb, allocator);
        addRequestArgument(value3, cb, allocator);
        final RedisRequest request = newRequest(RedisProtocolSupport.Command.HMSET, cb);
        final RedisPartitionAttributesBuilder partitionAttributesBuilder = partitionAttributesBuilderFunction
                    .apply(RedisProtocolSupport.Command.HMSET);
        partitionAttributesBuilder.addKey(key);
        final Single<String> result = partitionedRedisClient.request(partitionAttributesBuilder.build(), request, String.class);
        return result;
    }

    @Override
    public Single<String> hmset(@RedisProtocolSupport.Key final CharSequence key,
                                final Collection<RedisProtocolSupport.FieldValue> fieldValues) {
        requireNonNull(key);
        requireNonNull(fieldValues);
        final BufferAllocator allocator = partitionedRedisClient.getBufferAllocator();
        // Compute the number of request arguments, accounting for nullable ones
        int len = 2;
        len += RedisProtocolSupport.FieldValue.SIZE * fieldValues.size();
        final CompositeBuffer cb = newRequestCompositeBuffer(len, RedisProtocolSupport.Command.HMSET, allocator);
        addRequestArgument(key, cb, allocator);
        addRequestTupleArguments(fieldValues, null, cb, allocator);
        final RedisRequest request = newRequest(RedisProtocolSupport.Command.HMSET, cb);
        final RedisPartitionAttributesBuilder partitionAttributesBuilder = partitionAttributesBuilderFunction
                    .apply(RedisProtocolSupport.Command.HMSET);
        partitionAttributesBuilder.addKey(key);
        final Single<String> result = partitionedRedisClient.request(partitionAttributesBuilder.build(), request, String.class);
        return result;
    }

    @Override
    public <T> Single<List<T>> hscan(@RedisProtocolSupport.Key final CharSequence key, final long cursor) {
        requireNonNull(key);
        final BufferAllocator allocator = partitionedRedisClient.getBufferAllocator();
        // Compute the number of request arguments, accounting for nullable ones
        int len = 3;
        final CompositeBuffer cb = newRequestCompositeBuffer(len, RedisProtocolSupport.Command.HSCAN, allocator);
        addRequestArgument(key, cb, allocator);
        addRequestArgument(cursor, cb, allocator);
        final RedisRequest request = newRequest(RedisProtocolSupport.Command.HSCAN, cb);
        final RedisPartitionAttributesBuilder partitionAttributesBuilder = partitionAttributesBuilderFunction
                    .apply(RedisProtocolSupport.Command.HSCAN);
        partitionAttributesBuilder.addKey(key);
        final Single<List<T>> result = (Single) partitionedRedisClient.request(partitionAttributesBuilder.build(), request,
                    RedisUtils.ListWithBuffersCoercedToCharSequences.class);
        return result;
    }

    @Override
    public <T> Single<List<T>> hscan(@RedisProtocolSupport.Key final CharSequence key, final long cursor,
                                     @Nullable final CharSequence matchPattern, @Nullable final Long count) {
        requireNonNull(key);
        final BufferAllocator allocator = partitionedRedisClient.getBufferAllocator();
        // Compute the number of request arguments, accounting for nullable ones
        int len = 3;
        if (matchPattern != null) {
            len += 2;
        }
        if (count != null) {
            len += 2;
        }
        final CompositeBuffer cb = newRequestCompositeBuffer(len, RedisProtocolSupport.Command.HSCAN, allocator);
        addRequestArgument(key, cb, allocator);
        addRequestArgument(cursor, cb, allocator);
        if (matchPattern != null) {
            addRequestArgument(RedisProtocolSupport.SubCommand.MATCH, cb, allocator);
            addRequestArgument(matchPattern, cb, allocator);
        }
        if (count != null) {
            addRequestArgument(RedisProtocolSupport.SubCommand.COUNT, cb, allocator);
            addRequestArgument(count, cb, allocator);
        }
        final RedisRequest request = newRequest(RedisProtocolSupport.Command.HSCAN, cb);
        final RedisPartitionAttributesBuilder partitionAttributesBuilder = partitionAttributesBuilderFunction
                    .apply(RedisProtocolSupport.Command.HSCAN);
        partitionAttributesBuilder.addKey(key);
        final Single<List<T>> result = (Single) partitionedRedisClient.request(partitionAttributesBuilder.build(), request,
                    RedisUtils.ListWithBuffersCoercedToCharSequences.class);
        return result;
    }

    @Override
    public Single<Long> hset(@RedisProtocolSupport.Key final CharSequence key, final CharSequence field,
                             final CharSequence value) {
        requireNonNull(key);
        requireNonNull(field);
        requireNonNull(value);
        final BufferAllocator allocator = partitionedRedisClient.getBufferAllocator();
        // Compute the number of request arguments, accounting for nullable ones
        int len = 4;
        final CompositeBuffer cb = newRequestCompositeBuffer(len, RedisProtocolSupport.Command.HSET, allocator);
        addRequestArgument(key, cb, allocator);
        addRequestArgument(field, cb, allocator);
        addRequestArgument(value, cb, allocator);
        final RedisRequest request = newRequest(RedisProtocolSupport.Command.HSET, cb);
        final RedisPartitionAttributesBuilder partitionAttributesBuilder = partitionAttributesBuilderFunction
                    .apply(RedisProtocolSupport.Command.HSET);
        partitionAttributesBuilder.addKey(key);
        final Single<Long> result = partitionedRedisClient.request(partitionAttributesBuilder.build(), request, Long.class);
        return result;
    }

    @Override
    public Single<Long> hsetnx(@RedisProtocolSupport.Key final CharSequence key, final CharSequence field,
                               final CharSequence value) {
        requireNonNull(key);
        requireNonNull(field);
        requireNonNull(value);
        final BufferAllocator allocator = partitionedRedisClient.getBufferAllocator();
        // Compute the number of request arguments, accounting for nullable ones
        int len = 4;
        final CompositeBuffer cb = newRequestCompositeBuffer(len, RedisProtocolSupport.Command.HSETNX, allocator);
        addRequestArgument(key, cb, allocator);
        addRequestArgument(field, cb, allocator);
        addRequestArgument(value, cb, allocator);
        final RedisRequest request = newRequest(RedisProtocolSupport.Command.HSETNX, cb);
        final RedisPartitionAttributesBuilder partitionAttributesBuilder = partitionAttributesBuilderFunction
                    .apply(RedisProtocolSupport.Command.HSETNX);
        partitionAttributesBuilder.addKey(key);
        final Single<Long> result = partitionedRedisClient.request(partitionAttributesBuilder.build(), request, Long.class);
        return result;
    }

    @Override
    public Single<Long> hstrlen(@RedisProtocolSupport.Key final CharSequence key, final CharSequence field) {
        requireNonNull(key);
        requireNonNull(field);
        final BufferAllocator allocator = partitionedRedisClient.getBufferAllocator();
        // Compute the number of request arguments, accounting for nullable ones
        int len = 3;
        final CompositeBuffer cb = newRequestCompositeBuffer(len, RedisProtocolSupport.Command.HSTRLEN, allocator);
        addRequestArgument(key, cb, allocator);
        addRequestArgument(field, cb, allocator);
        final RedisRequest request = newRequest(RedisProtocolSupport.Command.HSTRLEN, cb);
        final RedisPartitionAttributesBuilder partitionAttributesBuilder = partitionAttributesBuilderFunction
                    .apply(RedisProtocolSupport.Command.HSTRLEN);
        partitionAttributesBuilder.addKey(key);
        final Single<Long> result = partitionedRedisClient.request(partitionAttributesBuilder.build(), request, Long.class);
        return result;
    }

    @Override
    public <T> Single<List<T>> hvals(@RedisProtocolSupport.Key final CharSequence key) {
        requireNonNull(key);
        final BufferAllocator allocator = partitionedRedisClient.getBufferAllocator();
        // Compute the number of request arguments, accounting for nullable ones
        int len = 2;
        final CompositeBuffer cb = newRequestCompositeBuffer(len, RedisProtocolSupport.Command.HVALS, allocator);
        addRequestArgument(key, cb, allocator);
        final RedisRequest request = newRequest(RedisProtocolSupport.Command.HVALS, cb);
        final RedisPartitionAttributesBuilder partitionAttributesBuilder = partitionAttributesBuilderFunction
                    .apply(RedisProtocolSupport.Command.HVALS);
        partitionAttributesBuilder.addKey(key);
        final Single<List<T>> result = (Single) partitionedRedisClient.request(partitionAttributesBuilder.build(), request,
                    RedisUtils.ListWithBuffersCoercedToCharSequences.class);
        return result;
    }

    @Override
    public Single<Long> incr(@RedisProtocolSupport.Key final CharSequence key) {
        requireNonNull(key);
        final BufferAllocator allocator = partitionedRedisClient.getBufferAllocator();
        // Compute the number of request arguments, accounting for nullable ones
        int len = 2;
        final CompositeBuffer cb = newRequestCompositeBuffer(len, RedisProtocolSupport.Command.INCR, allocator);
        addRequestArgument(key, cb, allocator);
        final RedisRequest request = newRequest(RedisProtocolSupport.Command.INCR, cb);
        final RedisPartitionAttributesBuilder partitionAttributesBuilder = partitionAttributesBuilderFunction
                    .apply(RedisProtocolSupport.Command.INCR);
        partitionAttributesBuilder.addKey(key);
        final Single<Long> result = partitionedRedisClient.request(partitionAttributesBuilder.build(), request, Long.class);
        return result;
    }

    @Override
    public Single<Long> incrby(@RedisProtocolSupport.Key final CharSequence key, final long increment) {
        requireNonNull(key);
        final BufferAllocator allocator = partitionedRedisClient.getBufferAllocator();
        // Compute the number of request arguments, accounting for nullable ones
        int len = 3;
        final CompositeBuffer cb = newRequestCompositeBuffer(len, RedisProtocolSupport.Command.INCRBY, allocator);
        addRequestArgument(key, cb, allocator);
        addRequestArgument(increment, cb, allocator);
        final RedisRequest request = newRequest(RedisProtocolSupport.Command.INCRBY, cb);
        final RedisPartitionAttributesBuilder partitionAttributesBuilder = partitionAttributesBuilderFunction
                    .apply(RedisProtocolSupport.Command.INCRBY);
        partitionAttributesBuilder.addKey(key);
        final Single<Long> result = partitionedRedisClient.request(partitionAttributesBuilder.build(), request, Long.class);
        return result;
    }

    @Override
    public Single<Double> incrbyfloat(@RedisProtocolSupport.Key final CharSequence key, final double increment) {
        requireNonNull(key);
        final BufferAllocator allocator = partitionedRedisClient.getBufferAllocator();
        // Compute the number of request arguments, accounting for nullable ones
        int len = 3;
        final CompositeBuffer cb = newRequestCompositeBuffer(len, RedisProtocolSupport.Command.INCRBYFLOAT, allocator);
        addRequestArgument(key, cb, allocator);
        addRequestArgument(increment, cb, allocator);
        final RedisRequest request = newRequest(RedisProtocolSupport.Command.INCRBYFLOAT, cb);
        final RedisPartitionAttributesBuilder partitionAttributesBuilder = partitionAttributesBuilderFunction
                    .apply(RedisProtocolSupport.Command.INCRBYFLOAT);
        partitionAttributesBuilder.addKey(key);
        final Single<Double> result = partitionedRedisClient.request(partitionAttributesBuilder.build(), request, String.class)
                    .map(s -> s != null ? Double.valueOf(s) : null);
        return result;
    }

    @Override
    public Single<String> info() {
        final BufferAllocator allocator = partitionedRedisClient.getBufferAllocator();
        // Compute the number of request arguments, accounting for nullable ones
        int len = 1;
        final CompositeBuffer cb = newRequestCompositeBuffer(len, RedisProtocolSupport.Command.INFO, allocator);
        final RedisRequest request = newRequest(RedisProtocolSupport.Command.INFO, cb);
        final RedisPartitionAttributesBuilder partitionAttributesBuilder = partitionAttributesBuilderFunction
                    .apply(RedisProtocolSupport.Command.INFO);
        final Single<String> result = partitionedRedisClient.request(partitionAttributesBuilder.build(), request, String.class);
        return result;
    }

    @Override
    public Single<String> info(@Nullable final CharSequence section) {
        final BufferAllocator allocator = partitionedRedisClient.getBufferAllocator();
        // Compute the number of request arguments, accounting for nullable ones
        int len = 1;
        if (section != null) {
            len++;
        }
        final CompositeBuffer cb = newRequestCompositeBuffer(len, RedisProtocolSupport.Command.INFO, allocator);
        if (section != null) {
            addRequestArgument(section, cb, allocator);
        }
        final RedisRequest request = newRequest(RedisProtocolSupport.Command.INFO, cb);
        final RedisPartitionAttributesBuilder partitionAttributesBuilder = partitionAttributesBuilderFunction
                    .apply(RedisProtocolSupport.Command.INFO);
        final Single<String> result = partitionedRedisClient.request(partitionAttributesBuilder.build(), request, String.class);
        return result;
    }

    @Override
    public <T> Single<List<T>> keys(final CharSequence pattern) {
        requireNonNull(pattern);
        final BufferAllocator allocator = partitionedRedisClient.getBufferAllocator();
        // Compute the number of request arguments, accounting for nullable ones
        int len = 2;
        final CompositeBuffer cb = newRequestCompositeBuffer(len, RedisProtocolSupport.Command.KEYS, allocator);
        addRequestArgument(pattern, cb, allocator);
        final RedisRequest request = newRequest(RedisProtocolSupport.Command.KEYS, cb);
        final RedisPartitionAttributesBuilder partitionAttributesBuilder = partitionAttributesBuilderFunction
                    .apply(RedisProtocolSupport.Command.KEYS);
        final Single<List<T>> result = (Single) partitionedRedisClient.request(partitionAttributesBuilder.build(), request,
                    RedisUtils.ListWithBuffersCoercedToCharSequences.class);
        return result;
    }

    @Override
    public Single<Long> lastsave() {
        final BufferAllocator allocator = partitionedRedisClient.getBufferAllocator();
        // Compute the number of request arguments, accounting for nullable ones
        int len = 1;
        final CompositeBuffer cb = newRequestCompositeBuffer(len, RedisProtocolSupport.Command.LASTSAVE, allocator);
        final RedisRequest request = newRequest(RedisProtocolSupport.Command.LASTSAVE, cb);
        final RedisPartitionAttributesBuilder partitionAttributesBuilder = partitionAttributesBuilderFunction
                    .apply(RedisProtocolSupport.Command.LASTSAVE);
        final Single<Long> result = partitionedRedisClient.request(partitionAttributesBuilder.build(), request, Long.class);
        return result;
    }

    @Override
    public Single<String> lindex(@RedisProtocolSupport.Key final CharSequence key, final long index) {
        requireNonNull(key);
        final BufferAllocator allocator = partitionedRedisClient.getBufferAllocator();
        // Compute the number of request arguments, accounting for nullable ones
        int len = 3;
        final CompositeBuffer cb = newRequestCompositeBuffer(len, RedisProtocolSupport.Command.LINDEX, allocator);
        addRequestArgument(key, cb, allocator);
        addRequestArgument(index, cb, allocator);
        final RedisRequest request = newRequest(RedisProtocolSupport.Command.LINDEX, cb);
        final RedisPartitionAttributesBuilder partitionAttributesBuilder = partitionAttributesBuilderFunction
                    .apply(RedisProtocolSupport.Command.LINDEX);
        partitionAttributesBuilder.addKey(key);
        final Single<String> result = partitionedRedisClient.request(partitionAttributesBuilder.build(), request, String.class);
        return result;
    }

    @Override
    public Single<Long> linsert(@RedisProtocolSupport.Key final CharSequence key, final RedisProtocolSupport.LinsertWhere where,
                                final CharSequence pivot, final CharSequence value) {
        requireNonNull(key);
        requireNonNull(where);
        requireNonNull(pivot);
        requireNonNull(value);
        final BufferAllocator allocator = partitionedRedisClient.getBufferAllocator();
        // Compute the number of request arguments, accounting for nullable ones
        int len = 5;
        final CompositeBuffer cb = newRequestCompositeBuffer(len, RedisProtocolSupport.Command.LINSERT, allocator);
        addRequestArgument(key, cb, allocator);
        addRequestArgument(where, cb, allocator);
        addRequestArgument(pivot, cb, allocator);
        addRequestArgument(value, cb, allocator);
        final RedisRequest request = newRequest(RedisProtocolSupport.Command.LINSERT, cb);
        final RedisPartitionAttributesBuilder partitionAttributesBuilder = partitionAttributesBuilderFunction
                    .apply(RedisProtocolSupport.Command.LINSERT);
        partitionAttributesBuilder.addKey(key);
        final Single<Long> result = partitionedRedisClient.request(partitionAttributesBuilder.build(), request, Long.class);
        return result;
    }

    @Override
    public Single<Long> llen(@RedisProtocolSupport.Key final CharSequence key) {
        requireNonNull(key);
        final BufferAllocator allocator = partitionedRedisClient.getBufferAllocator();
        // Compute the number of request arguments, accounting for nullable ones
        int len = 2;
        final CompositeBuffer cb = newRequestCompositeBuffer(len, RedisProtocolSupport.Command.LLEN, allocator);
        addRequestArgument(key, cb, allocator);
        final RedisRequest request = newRequest(RedisProtocolSupport.Command.LLEN, cb);
        final RedisPartitionAttributesBuilder partitionAttributesBuilder = partitionAttributesBuilderFunction
                    .apply(RedisProtocolSupport.Command.LLEN);
        partitionAttributesBuilder.addKey(key);
        final Single<Long> result = partitionedRedisClient.request(partitionAttributesBuilder.build(), request, Long.class);
        return result;
    }

    @Override
    public Single<String> lpop(@RedisProtocolSupport.Key final CharSequence key) {
        requireNonNull(key);
        final BufferAllocator allocator = partitionedRedisClient.getBufferAllocator();
        // Compute the number of request arguments, accounting for nullable ones
        int len = 2;
        final CompositeBuffer cb = newRequestCompositeBuffer(len, RedisProtocolSupport.Command.LPOP, allocator);
        addRequestArgument(key, cb, allocator);
        final RedisRequest request = newRequest(RedisProtocolSupport.Command.LPOP, cb);
        final RedisPartitionAttributesBuilder partitionAttributesBuilder = partitionAttributesBuilderFunction
                    .apply(RedisProtocolSupport.Command.LPOP);
        partitionAttributesBuilder.addKey(key);
        final Single<String> result = partitionedRedisClient.request(partitionAttributesBuilder.build(), request, String.class);
        return result;
    }

    @Override
    public Single<Long> lpush(@RedisProtocolSupport.Key final CharSequence key, final CharSequence value) {
        requireNonNull(key);
        requireNonNull(value);
        final BufferAllocator allocator = partitionedRedisClient.getBufferAllocator();
        // Compute the number of request arguments, accounting for nullable ones
        int len = 3;
        final CompositeBuffer cb = newRequestCompositeBuffer(len, RedisProtocolSupport.Command.LPUSH, allocator);
        addRequestArgument(key, cb, allocator);
        addRequestArgument(value, cb, allocator);
        final RedisRequest request = newRequest(RedisProtocolSupport.Command.LPUSH, cb);
        final RedisPartitionAttributesBuilder partitionAttributesBuilder = partitionAttributesBuilderFunction
                    .apply(RedisProtocolSupport.Command.LPUSH);
        partitionAttributesBuilder.addKey(key);
        final Single<Long> result = partitionedRedisClient.request(partitionAttributesBuilder.build(), request, Long.class);
        return result;
    }

    @Override
    public Single<Long> lpush(@RedisProtocolSupport.Key final CharSequence key, final CharSequence value1,
                              final CharSequence value2) {
        requireNonNull(key);
        requireNonNull(value1);
        requireNonNull(value2);
        final BufferAllocator allocator = partitionedRedisClient.getBufferAllocator();
        // Compute the number of request arguments, accounting for nullable ones
        int len = 4;
        final CompositeBuffer cb = newRequestCompositeBuffer(len, RedisProtocolSupport.Command.LPUSH, allocator);
        addRequestArgument(key, cb, allocator);
        addRequestArgument(value1, cb, allocator);
        addRequestArgument(value2, cb, allocator);
        final RedisRequest request = newRequest(RedisProtocolSupport.Command.LPUSH, cb);
        final RedisPartitionAttributesBuilder partitionAttributesBuilder = partitionAttributesBuilderFunction
                    .apply(RedisProtocolSupport.Command.LPUSH);
        partitionAttributesBuilder.addKey(key);
        final Single<Long> result = partitionedRedisClient.request(partitionAttributesBuilder.build(), request, Long.class);
        return result;
    }

    @Override
    public Single<Long> lpush(@RedisProtocolSupport.Key final CharSequence key, final CharSequence value1,
                              final CharSequence value2, final CharSequence value3) {
        requireNonNull(key);
        requireNonNull(value1);
        requireNonNull(value2);
        requireNonNull(value3);
        final BufferAllocator allocator = partitionedRedisClient.getBufferAllocator();
        // Compute the number of request arguments, accounting for nullable ones
        int len = 5;
        final CompositeBuffer cb = newRequestCompositeBuffer(len, RedisProtocolSupport.Command.LPUSH, allocator);
        addRequestArgument(key, cb, allocator);
        addRequestArgument(value1, cb, allocator);
        addRequestArgument(value2, cb, allocator);
        addRequestArgument(value3, cb, allocator);
        final RedisRequest request = newRequest(RedisProtocolSupport.Command.LPUSH, cb);
        final RedisPartitionAttributesBuilder partitionAttributesBuilder = partitionAttributesBuilderFunction
                    .apply(RedisProtocolSupport.Command.LPUSH);
        partitionAttributesBuilder.addKey(key);
        final Single<Long> result = partitionedRedisClient.request(partitionAttributesBuilder.build(), request, Long.class);
        return result;
    }

    @Override
    public Single<Long> lpush(@RedisProtocolSupport.Key final CharSequence key, final Collection<? extends CharSequence> values) {
        requireNonNull(key);
        requireNonNull(values);
        final BufferAllocator allocator = partitionedRedisClient.getBufferAllocator();
        // Compute the number of request arguments, accounting for nullable ones
        int len = 2;
        len += values.size();
        final CompositeBuffer cb = newRequestCompositeBuffer(len, RedisProtocolSupport.Command.LPUSH, allocator);
        addRequestArgument(key, cb, allocator);
        addRequestCharSequenceArguments(values, null, cb, allocator);
        final RedisRequest request = newRequest(RedisProtocolSupport.Command.LPUSH, cb);
        final RedisPartitionAttributesBuilder partitionAttributesBuilder = partitionAttributesBuilderFunction
                    .apply(RedisProtocolSupport.Command.LPUSH);
        partitionAttributesBuilder.addKey(key);
        final Single<Long> result = partitionedRedisClient.request(partitionAttributesBuilder.build(), request, Long.class);
        return result;
    }

    @Override
    public Single<Long> lpushx(@RedisProtocolSupport.Key final CharSequence key, final CharSequence value) {
        requireNonNull(key);
        requireNonNull(value);
        final BufferAllocator allocator = partitionedRedisClient.getBufferAllocator();
        // Compute the number of request arguments, accounting for nullable ones
        int len = 3;
        final CompositeBuffer cb = newRequestCompositeBuffer(len, RedisProtocolSupport.Command.LPUSHX, allocator);
        addRequestArgument(key, cb, allocator);
        addRequestArgument(value, cb, allocator);
        final RedisRequest request = newRequest(RedisProtocolSupport.Command.LPUSHX, cb);
        final RedisPartitionAttributesBuilder partitionAttributesBuilder = partitionAttributesBuilderFunction
                    .apply(RedisProtocolSupport.Command.LPUSHX);
        partitionAttributesBuilder.addKey(key);
        final Single<Long> result = partitionedRedisClient.request(partitionAttributesBuilder.build(), request, Long.class);
        return result;
    }

    @Override
    public <T> Single<List<T>> lrange(@RedisProtocolSupport.Key final CharSequence key, final long start, final long stop) {
        requireNonNull(key);
        final BufferAllocator allocator = partitionedRedisClient.getBufferAllocator();
        // Compute the number of request arguments, accounting for nullable ones
        int len = 4;
        final CompositeBuffer cb = newRequestCompositeBuffer(len, RedisProtocolSupport.Command.LRANGE, allocator);
        addRequestArgument(key, cb, allocator);
        addRequestArgument(start, cb, allocator);
        addRequestArgument(stop, cb, allocator);
        final RedisRequest request = newRequest(RedisProtocolSupport.Command.LRANGE, cb);
        final RedisPartitionAttributesBuilder partitionAttributesBuilder = partitionAttributesBuilderFunction
                    .apply(RedisProtocolSupport.Command.LRANGE);
        partitionAttributesBuilder.addKey(key);
        final Single<List<T>> result = (Single) partitionedRedisClient.request(partitionAttributesBuilder.build(), request,
                    RedisUtils.ListWithBuffersCoercedToCharSequences.class);
        return result;
    }

    @Override
    public Single<Long> lrem(@RedisProtocolSupport.Key final CharSequence key, final long count, final CharSequence value) {
        requireNonNull(key);
        requireNonNull(value);
        final BufferAllocator allocator = partitionedRedisClient.getBufferAllocator();
        // Compute the number of request arguments, accounting for nullable ones
        int len = 4;
        final CompositeBuffer cb = newRequestCompositeBuffer(len, RedisProtocolSupport.Command.LREM, allocator);
        addRequestArgument(key, cb, allocator);
        addRequestArgument(count, cb, allocator);
        addRequestArgument(value, cb, allocator);
        final RedisRequest request = newRequest(RedisProtocolSupport.Command.LREM, cb);
        final RedisPartitionAttributesBuilder partitionAttributesBuilder = partitionAttributesBuilderFunction
                    .apply(RedisProtocolSupport.Command.LREM);
        partitionAttributesBuilder.addKey(key);
        final Single<Long> result = partitionedRedisClient.request(partitionAttributesBuilder.build(), request, Long.class);
        return result;
    }

    @Override
    public Single<String> lset(@RedisProtocolSupport.Key final CharSequence key, final long index, final CharSequence value) {
        requireNonNull(key);
        requireNonNull(value);
        final BufferAllocator allocator = partitionedRedisClient.getBufferAllocator();
        // Compute the number of request arguments, accounting for nullable ones
        int len = 4;
        final CompositeBuffer cb = newRequestCompositeBuffer(len, RedisProtocolSupport.Command.LSET, allocator);
        addRequestArgument(key, cb, allocator);
        addRequestArgument(index, cb, allocator);
        addRequestArgument(value, cb, allocator);
        final RedisRequest request = newRequest(RedisProtocolSupport.Command.LSET, cb);
        final RedisPartitionAttributesBuilder partitionAttributesBuilder = partitionAttributesBuilderFunction
                    .apply(RedisProtocolSupport.Command.LSET);
        partitionAttributesBuilder.addKey(key);
        final Single<String> result = partitionedRedisClient.request(partitionAttributesBuilder.build(), request, String.class);
        return result;
    }

    @Override
    public Single<String> ltrim(@RedisProtocolSupport.Key final CharSequence key, final long start, final long stop) {
        requireNonNull(key);
        final BufferAllocator allocator = partitionedRedisClient.getBufferAllocator();
        // Compute the number of request arguments, accounting for nullable ones
        int len = 4;
        final CompositeBuffer cb = newRequestCompositeBuffer(len, RedisProtocolSupport.Command.LTRIM, allocator);
        addRequestArgument(key, cb, allocator);
        addRequestArgument(start, cb, allocator);
        addRequestArgument(stop, cb, allocator);
        final RedisRequest request = newRequest(RedisProtocolSupport.Command.LTRIM, cb);
        final RedisPartitionAttributesBuilder partitionAttributesBuilder = partitionAttributesBuilderFunction
                    .apply(RedisProtocolSupport.Command.LTRIM);
        partitionAttributesBuilder.addKey(key);
        final Single<String> result = partitionedRedisClient.request(partitionAttributesBuilder.build(), request, String.class);
        return result;
    }

    @Override
    public <T> Single<List<T>> mget(@RedisProtocolSupport.Key final CharSequence key) {
        requireNonNull(key);
        final BufferAllocator allocator = partitionedRedisClient.getBufferAllocator();
        // Compute the number of request arguments, accounting for nullable ones
        int len = 2;
        final CompositeBuffer cb = newRequestCompositeBuffer(len, RedisProtocolSupport.Command.MGET, allocator);
        addRequestArgument(key, cb, allocator);
        final RedisRequest request = newRequest(RedisProtocolSupport.Command.MGET, cb);
        final RedisPartitionAttributesBuilder partitionAttributesBuilder = partitionAttributesBuilderFunction
                    .apply(RedisProtocolSupport.Command.MGET);
        partitionAttributesBuilder.addKey(key);
        final Single<List<T>> result = (Single) partitionedRedisClient.request(partitionAttributesBuilder.build(), request,
                    RedisUtils.ListWithBuffersCoercedToCharSequences.class);
        return result;
    }

    @Override
    public <T> Single<List<T>> mget(@RedisProtocolSupport.Key final CharSequence key1,
                                    @RedisProtocolSupport.Key final CharSequence key2) {
        requireNonNull(key1);
        requireNonNull(key2);
        final BufferAllocator allocator = partitionedRedisClient.getBufferAllocator();
        // Compute the number of request arguments, accounting for nullable ones
        int len = 3;
        final CompositeBuffer cb = newRequestCompositeBuffer(len, RedisProtocolSupport.Command.MGET, allocator);
        addRequestArgument(key1, cb, allocator);
        addRequestArgument(key2, cb, allocator);
        final RedisRequest request = newRequest(RedisProtocolSupport.Command.MGET, cb);
        final RedisPartitionAttributesBuilder partitionAttributesBuilder = partitionAttributesBuilderFunction
                    .apply(RedisProtocolSupport.Command.MGET);
        partitionAttributesBuilder.addKey(key1);
        partitionAttributesBuilder.addKey(key2);
        final Single<List<T>> result = (Single) partitionedRedisClient.request(partitionAttributesBuilder.build(), request,
                    RedisUtils.ListWithBuffersCoercedToCharSequences.class);
        return result;
    }

    @Override
    public <T> Single<List<T>> mget(@RedisProtocolSupport.Key final CharSequence key1,
                                    @RedisProtocolSupport.Key final CharSequence key2,
                                    @RedisProtocolSupport.Key final CharSequence key3) {
        requireNonNull(key1);
        requireNonNull(key2);
        requireNonNull(key3);
        final BufferAllocator allocator = partitionedRedisClient.getBufferAllocator();
        // Compute the number of request arguments, accounting for nullable ones
        int len = 4;
        final CompositeBuffer cb = newRequestCompositeBuffer(len, RedisProtocolSupport.Command.MGET, allocator);
        addRequestArgument(key1, cb, allocator);
        addRequestArgument(key2, cb, allocator);
        addRequestArgument(key3, cb, allocator);
        final RedisRequest request = newRequest(RedisProtocolSupport.Command.MGET, cb);
        final RedisPartitionAttributesBuilder partitionAttributesBuilder = partitionAttributesBuilderFunction
                    .apply(RedisProtocolSupport.Command.MGET);
        partitionAttributesBuilder.addKey(key1);
        partitionAttributesBuilder.addKey(key2);
        partitionAttributesBuilder.addKey(key3);
        final Single<List<T>> result = (Single) partitionedRedisClient.request(partitionAttributesBuilder.build(), request,
                    RedisUtils.ListWithBuffersCoercedToCharSequences.class);
        return result;
    }

    @Override
    public <T> Single<List<T>> mget(@RedisProtocolSupport.Key final Collection<? extends CharSequence> keys) {
        requireNonNull(keys);
        final BufferAllocator allocator = partitionedRedisClient.getBufferAllocator();
        // Compute the number of request arguments, accounting for nullable ones
        int len = 1;
        len += keys.size();
        final CompositeBuffer cb = newRequestCompositeBuffer(len, RedisProtocolSupport.Command.MGET, allocator);
        addRequestCharSequenceArguments(keys, null, cb, allocator);
        final RedisRequest request = newRequest(RedisProtocolSupport.Command.MGET, cb);
        final RedisPartitionAttributesBuilder partitionAttributesBuilder = partitionAttributesBuilderFunction
                    .apply(RedisProtocolSupport.Command.MGET);
        addCharSequenceKeysToAttributeBuilder(keys, partitionAttributesBuilder);
        final Single<List<T>> result = (Single) partitionedRedisClient.request(partitionAttributesBuilder.build(), request,
                    RedisUtils.ListWithBuffersCoercedToCharSequences.class);
        return result;
    }

    @Override
    public Publisher<String> monitor() {
        final BufferAllocator allocator = partitionedRedisClient.getBufferAllocator();
        // Compute the number of request arguments, accounting for nullable ones
        int len = 1;
        final CompositeBuffer cb = newRequestCompositeBuffer(len, RedisProtocolSupport.Command.MONITOR, allocator);
        final RedisRequest request = newRequest(RedisProtocolSupport.Command.MONITOR, cb);
        final RedisPartitionAttributesBuilder partitionAttributesBuilder = partitionAttributesBuilderFunction
                    .apply(RedisProtocolSupport.Command.MONITOR);
        return newConnectedClient(partitionedRedisClient, partitionAttributesBuilder.build(), request,
                    (con, pub) -> pub.map(RedisCoercions::simpleStringToString)).flatmapPublisher(identity());
    }

    @Override
    public Single<Long> move(@RedisProtocolSupport.Key final CharSequence key, final long db) {
        requireNonNull(key);
        final BufferAllocator allocator = partitionedRedisClient.getBufferAllocator();
        // Compute the number of request arguments, accounting for nullable ones
        int len = 3;
        final CompositeBuffer cb = newRequestCompositeBuffer(len, RedisProtocolSupport.Command.MOVE, allocator);
        addRequestArgument(key, cb, allocator);
        addRequestArgument(db, cb, allocator);
        final RedisRequest request = newRequest(RedisProtocolSupport.Command.MOVE, cb);
        final RedisPartitionAttributesBuilder partitionAttributesBuilder = partitionAttributesBuilderFunction
                    .apply(RedisProtocolSupport.Command.MOVE);
        partitionAttributesBuilder.addKey(key);
        final Single<Long> result = partitionedRedisClient.request(partitionAttributesBuilder.build(), request, Long.class);
        return result;
    }

    @Override
    public Single<String> mset(@RedisProtocolSupport.Key final CharSequence key, final CharSequence value) {
        requireNonNull(key);
        requireNonNull(value);
        final BufferAllocator allocator = partitionedRedisClient.getBufferAllocator();
        // Compute the number of request arguments, accounting for nullable ones
        int len = 3;
        final CompositeBuffer cb = newRequestCompositeBuffer(len, RedisProtocolSupport.Command.MSET, allocator);
        addRequestArgument(key, cb, allocator);
        addRequestArgument(value, cb, allocator);
        final RedisRequest request = newRequest(RedisProtocolSupport.Command.MSET, cb);
        final RedisPartitionAttributesBuilder partitionAttributesBuilder = partitionAttributesBuilderFunction
                    .apply(RedisProtocolSupport.Command.MSET);
        partitionAttributesBuilder.addKey(key);
        final Single<String> result = partitionedRedisClient.request(partitionAttributesBuilder.build(), request, String.class);
        return result;
    }

    @Override
    public Single<String> mset(@RedisProtocolSupport.Key final CharSequence key1, final CharSequence value1,
                               @RedisProtocolSupport.Key final CharSequence key2, final CharSequence value2) {
        requireNonNull(key1);
        requireNonNull(value1);
        requireNonNull(key2);
        requireNonNull(value2);
        final BufferAllocator allocator = partitionedRedisClient.getBufferAllocator();
        // Compute the number of request arguments, accounting for nullable ones
        int len = 5;
        final CompositeBuffer cb = newRequestCompositeBuffer(len, RedisProtocolSupport.Command.MSET, allocator);
        addRequestArgument(key1, cb, allocator);
        addRequestArgument(value1, cb, allocator);
        addRequestArgument(key2, cb, allocator);
        addRequestArgument(value2, cb, allocator);
        final RedisRequest request = newRequest(RedisProtocolSupport.Command.MSET, cb);
        final RedisPartitionAttributesBuilder partitionAttributesBuilder = partitionAttributesBuilderFunction
                    .apply(RedisProtocolSupport.Command.MSET);
        partitionAttributesBuilder.addKey(key1);
        partitionAttributesBuilder.addKey(key2);
        final Single<String> result = partitionedRedisClient.request(partitionAttributesBuilder.build(), request, String.class);
        return result;
    }

    @Override
    public Single<String> mset(@RedisProtocolSupport.Key final CharSequence key1, final CharSequence value1,
                               @RedisProtocolSupport.Key final CharSequence key2, final CharSequence value2,
                               @RedisProtocolSupport.Key final CharSequence key3, final CharSequence value3) {
        requireNonNull(key1);
        requireNonNull(value1);
        requireNonNull(key2);
        requireNonNull(value2);
        requireNonNull(key3);
        requireNonNull(value3);
        final BufferAllocator allocator = partitionedRedisClient.getBufferAllocator();
        // Compute the number of request arguments, accounting for nullable ones
        int len = 7;
        final CompositeBuffer cb = newRequestCompositeBuffer(len, RedisProtocolSupport.Command.MSET, allocator);
        addRequestArgument(key1, cb, allocator);
        addRequestArgument(value1, cb, allocator);
        addRequestArgument(key2, cb, allocator);
        addRequestArgument(value2, cb, allocator);
        addRequestArgument(key3, cb, allocator);
        addRequestArgument(value3, cb, allocator);
        final RedisRequest request = newRequest(RedisProtocolSupport.Command.MSET, cb);
        final RedisPartitionAttributesBuilder partitionAttributesBuilder = partitionAttributesBuilderFunction
                    .apply(RedisProtocolSupport.Command.MSET);
        partitionAttributesBuilder.addKey(key1);
        partitionAttributesBuilder.addKey(key2);
        partitionAttributesBuilder.addKey(key3);
        final Single<String> result = partitionedRedisClient.request(partitionAttributesBuilder.build(), request, String.class);
        return result;
    }

    @Override
    public Single<String> mset(final Collection<RedisProtocolSupport.KeyValue> keyValues) {
        requireNonNull(keyValues);
        final BufferAllocator allocator = partitionedRedisClient.getBufferAllocator();
        // Compute the number of request arguments, accounting for nullable ones
        int len = 1;
        len += RedisProtocolSupport.KeyValue.SIZE * keyValues.size();
        final CompositeBuffer cb = newRequestCompositeBuffer(len, RedisProtocolSupport.Command.MSET, allocator);
        addRequestTupleArguments(keyValues, null, cb, allocator);
        final RedisRequest request = newRequest(RedisProtocolSupport.Command.MSET, cb);
        final RedisPartitionAttributesBuilder partitionAttributesBuilder = partitionAttributesBuilderFunction
                    .apply(RedisProtocolSupport.Command.MSET);
        final Single<String> result = partitionedRedisClient.request(partitionAttributesBuilder.build(), request, String.class);
        return result;
    }

    @Override
    public Single<Long> msetnx(@RedisProtocolSupport.Key final CharSequence key, final CharSequence value) {
        requireNonNull(key);
        requireNonNull(value);
        final BufferAllocator allocator = partitionedRedisClient.getBufferAllocator();
        // Compute the number of request arguments, accounting for nullable ones
        int len = 3;
        final CompositeBuffer cb = newRequestCompositeBuffer(len, RedisProtocolSupport.Command.MSETNX, allocator);
        addRequestArgument(key, cb, allocator);
        addRequestArgument(value, cb, allocator);
        final RedisRequest request = newRequest(RedisProtocolSupport.Command.MSETNX, cb);
        final RedisPartitionAttributesBuilder partitionAttributesBuilder = partitionAttributesBuilderFunction
                    .apply(RedisProtocolSupport.Command.MSETNX);
        partitionAttributesBuilder.addKey(key);
        final Single<Long> result = partitionedRedisClient.request(partitionAttributesBuilder.build(), request, Long.class);
        return result;
    }

    @Override
    public Single<Long> msetnx(@RedisProtocolSupport.Key final CharSequence key1, final CharSequence value1,
                               @RedisProtocolSupport.Key final CharSequence key2, final CharSequence value2) {
        requireNonNull(key1);
        requireNonNull(value1);
        requireNonNull(key2);
        requireNonNull(value2);
        final BufferAllocator allocator = partitionedRedisClient.getBufferAllocator();
        // Compute the number of request arguments, accounting for nullable ones
        int len = 5;
        final CompositeBuffer cb = newRequestCompositeBuffer(len, RedisProtocolSupport.Command.MSETNX, allocator);
        addRequestArgument(key1, cb, allocator);
        addRequestArgument(value1, cb, allocator);
        addRequestArgument(key2, cb, allocator);
        addRequestArgument(value2, cb, allocator);
        final RedisRequest request = newRequest(RedisProtocolSupport.Command.MSETNX, cb);
        final RedisPartitionAttributesBuilder partitionAttributesBuilder = partitionAttributesBuilderFunction
                    .apply(RedisProtocolSupport.Command.MSETNX);
        partitionAttributesBuilder.addKey(key1);
        partitionAttributesBuilder.addKey(key2);
        final Single<Long> result = partitionedRedisClient.request(partitionAttributesBuilder.build(), request, Long.class);
        return result;
    }

    @Override
    public Single<Long> msetnx(@RedisProtocolSupport.Key final CharSequence key1, final CharSequence value1,
                               @RedisProtocolSupport.Key final CharSequence key2, final CharSequence value2,
                               @RedisProtocolSupport.Key final CharSequence key3, final CharSequence value3) {
        requireNonNull(key1);
        requireNonNull(value1);
        requireNonNull(key2);
        requireNonNull(value2);
        requireNonNull(key3);
        requireNonNull(value3);
        final BufferAllocator allocator = partitionedRedisClient.getBufferAllocator();
        // Compute the number of request arguments, accounting for nullable ones
        int len = 7;
        final CompositeBuffer cb = newRequestCompositeBuffer(len, RedisProtocolSupport.Command.MSETNX, allocator);
        addRequestArgument(key1, cb, allocator);
        addRequestArgument(value1, cb, allocator);
        addRequestArgument(key2, cb, allocator);
        addRequestArgument(value2, cb, allocator);
        addRequestArgument(key3, cb, allocator);
        addRequestArgument(value3, cb, allocator);
        final RedisRequest request = newRequest(RedisProtocolSupport.Command.MSETNX, cb);
        final RedisPartitionAttributesBuilder partitionAttributesBuilder = partitionAttributesBuilderFunction
                    .apply(RedisProtocolSupport.Command.MSETNX);
        partitionAttributesBuilder.addKey(key1);
        partitionAttributesBuilder.addKey(key2);
        partitionAttributesBuilder.addKey(key3);
        final Single<Long> result = partitionedRedisClient.request(partitionAttributesBuilder.build(), request, Long.class);
        return result;
    }

    @Override
    public Single<Long> msetnx(final Collection<RedisProtocolSupport.KeyValue> keyValues) {
        requireNonNull(keyValues);
        final BufferAllocator allocator = partitionedRedisClient.getBufferAllocator();
        // Compute the number of request arguments, accounting for nullable ones
        int len = 1;
        len += RedisProtocolSupport.KeyValue.SIZE * keyValues.size();
        final CompositeBuffer cb = newRequestCompositeBuffer(len, RedisProtocolSupport.Command.MSETNX, allocator);
        addRequestTupleArguments(keyValues, null, cb, allocator);
        final RedisRequest request = newRequest(RedisProtocolSupport.Command.MSETNX, cb);
        final RedisPartitionAttributesBuilder partitionAttributesBuilder = partitionAttributesBuilderFunction
                    .apply(RedisProtocolSupport.Command.MSETNX);
        final Single<Long> result = partitionedRedisClient.request(partitionAttributesBuilder.build(), request, Long.class);
        return result;
    }

    @Override
    public Single<TransactedRedisCommander> multi() {
        final BufferAllocator allocator = partitionedRedisClient.getBufferAllocator();
        // Compute the number of request arguments, accounting for nullable ones
        int len = 1;
        final CompositeBuffer cb = newRequestCompositeBuffer(len, RedisProtocolSupport.Command.MULTI, allocator);
        final RedisRequest request = newRequest(RedisProtocolSupport.Command.MULTI, cb);
        final RedisPartitionAttributesBuilder partitionAttributesBuilder = partitionAttributesBuilderFunction
                    .apply(RedisProtocolSupport.Command.MULTI);
        return newConnectedClient(partitionedRedisClient, partitionAttributesBuilder.build(), request, RedisData.OK::equals,
                    DefaultTransactedRedisCommander::new);
    }

    @Override
    public Single<String> objectEncoding(@RedisProtocolSupport.Key final CharSequence key) {
        requireNonNull(key);
        final BufferAllocator allocator = partitionedRedisClient.getBufferAllocator();
        // Compute the number of request arguments, accounting for nullable ones
        int len = 3;
        final CompositeBuffer cb = newRequestCompositeBuffer(len, RedisProtocolSupport.Command.OBJECT,
                    RedisProtocolSupport.SubCommand.ENCODING, allocator);
        addRequestArgument(key, cb, allocator);
        final RedisRequest request = newRequest(RedisProtocolSupport.Command.OBJECT, cb);
        final RedisPartitionAttributesBuilder partitionAttributesBuilder = partitionAttributesBuilderFunction
                    .apply(RedisProtocolSupport.Command.OBJECT);
        partitionAttributesBuilder.addKey(key);
        final Single<String> result = partitionedRedisClient.request(partitionAttributesBuilder.build(), request, String.class);
        return result;
    }

    @Override
    public Single<Long> objectFreq(@RedisProtocolSupport.Key final CharSequence key) {
        requireNonNull(key);
        final BufferAllocator allocator = partitionedRedisClient.getBufferAllocator();
        // Compute the number of request arguments, accounting for nullable ones
        int len = 3;
        final CompositeBuffer cb = newRequestCompositeBuffer(len, RedisProtocolSupport.Command.OBJECT,
                    RedisProtocolSupport.SubCommand.FREQ, allocator);
        addRequestArgument(key, cb, allocator);
        final RedisRequest request = newRequest(RedisProtocolSupport.Command.OBJECT, cb);
        final RedisPartitionAttributesBuilder partitionAttributesBuilder = partitionAttributesBuilderFunction
                    .apply(RedisProtocolSupport.Command.OBJECT);
        partitionAttributesBuilder.addKey(key);
        final Single<Long> result = partitionedRedisClient.request(partitionAttributesBuilder.build(), request, Long.class);
        return result;
    }

    @Override
    public Single<List<String>> objectHelp() {
        final BufferAllocator allocator = partitionedRedisClient.getBufferAllocator();
        // Compute the number of request arguments, accounting for nullable ones
        int len = 2;
        final CompositeBuffer cb = newRequestCompositeBuffer(len, RedisProtocolSupport.Command.OBJECT,
                    RedisProtocolSupport.SubCommand.HELP, allocator);
        final RedisRequest request = newRequest(RedisProtocolSupport.Command.OBJECT, cb);
        final RedisPartitionAttributesBuilder partitionAttributesBuilder = partitionAttributesBuilderFunction
                    .apply(RedisProtocolSupport.Command.OBJECT);
        final Single<List<String>> result = (Single) partitionedRedisClient.request(partitionAttributesBuilder.build(), request,
                    RedisUtils.ListWithBuffersCoercedToCharSequences.class);
        return result;
    }

    @Override
    public Single<Long> objectIdletime(@RedisProtocolSupport.Key final CharSequence key) {
        requireNonNull(key);
        final BufferAllocator allocator = partitionedRedisClient.getBufferAllocator();
        // Compute the number of request arguments, accounting for nullable ones
        int len = 3;
        final CompositeBuffer cb = newRequestCompositeBuffer(len, RedisProtocolSupport.Command.OBJECT,
                    RedisProtocolSupport.SubCommand.IDLETIME, allocator);
        addRequestArgument(key, cb, allocator);
        final RedisRequest request = newRequest(RedisProtocolSupport.Command.OBJECT, cb);
        final RedisPartitionAttributesBuilder partitionAttributesBuilder = partitionAttributesBuilderFunction
                    .apply(RedisProtocolSupport.Command.OBJECT);
        partitionAttributesBuilder.addKey(key);
        final Single<Long> result = partitionedRedisClient.request(partitionAttributesBuilder.build(), request, Long.class);
        return result;
    }

    @Override
    public Single<Long> objectRefcount(@RedisProtocolSupport.Key final CharSequence key) {
        requireNonNull(key);
        final BufferAllocator allocator = partitionedRedisClient.getBufferAllocator();
        // Compute the number of request arguments, accounting for nullable ones
        int len = 3;
        final CompositeBuffer cb = newRequestCompositeBuffer(len, RedisProtocolSupport.Command.OBJECT,
                    RedisProtocolSupport.SubCommand.REFCOUNT, allocator);
        addRequestArgument(key, cb, allocator);
        final RedisRequest request = newRequest(RedisProtocolSupport.Command.OBJECT, cb);
        final RedisPartitionAttributesBuilder partitionAttributesBuilder = partitionAttributesBuilderFunction
                    .apply(RedisProtocolSupport.Command.OBJECT);
        partitionAttributesBuilder.addKey(key);
        final Single<Long> result = partitionedRedisClient.request(partitionAttributesBuilder.build(), request, Long.class);
        return result;
    }

    @Override
    public Single<Long> persist(@RedisProtocolSupport.Key final CharSequence key) {
        requireNonNull(key);
        final BufferAllocator allocator = partitionedRedisClient.getBufferAllocator();
        // Compute the number of request arguments, accounting for nullable ones
        int len = 2;
        final CompositeBuffer cb = newRequestCompositeBuffer(len, RedisProtocolSupport.Command.PERSIST, allocator);
        addRequestArgument(key, cb, allocator);
        final RedisRequest request = newRequest(RedisProtocolSupport.Command.PERSIST, cb);
        final RedisPartitionAttributesBuilder partitionAttributesBuilder = partitionAttributesBuilderFunction
                    .apply(RedisProtocolSupport.Command.PERSIST);
        partitionAttributesBuilder.addKey(key);
        final Single<Long> result = partitionedRedisClient.request(partitionAttributesBuilder.build(), request, Long.class);
        return result;
    }

    @Override
    public Single<Long> pexpire(@RedisProtocolSupport.Key final CharSequence key, final long milliseconds) {
        requireNonNull(key);
        final BufferAllocator allocator = partitionedRedisClient.getBufferAllocator();
        // Compute the number of request arguments, accounting for nullable ones
        int len = 3;
        final CompositeBuffer cb = newRequestCompositeBuffer(len, RedisProtocolSupport.Command.PEXPIRE, allocator);
        addRequestArgument(key, cb, allocator);
        addRequestArgument(milliseconds, cb, allocator);
        final RedisRequest request = newRequest(RedisProtocolSupport.Command.PEXPIRE, cb);
        final RedisPartitionAttributesBuilder partitionAttributesBuilder = partitionAttributesBuilderFunction
                    .apply(RedisProtocolSupport.Command.PEXPIRE);
        partitionAttributesBuilder.addKey(key);
        final Single<Long> result = partitionedRedisClient.request(partitionAttributesBuilder.build(), request, Long.class);
        return result;
    }

    @Override
    public Single<Long> pexpireat(@RedisProtocolSupport.Key final CharSequence key, final long millisecondsTimestamp) {
        requireNonNull(key);
        final BufferAllocator allocator = partitionedRedisClient.getBufferAllocator();
        // Compute the number of request arguments, accounting for nullable ones
        int len = 3;
        final CompositeBuffer cb = newRequestCompositeBuffer(len, RedisProtocolSupport.Command.PEXPIREAT, allocator);
        addRequestArgument(key, cb, allocator);
        addRequestArgument(millisecondsTimestamp, cb, allocator);
        final RedisRequest request = newRequest(RedisProtocolSupport.Command.PEXPIREAT, cb);
        final RedisPartitionAttributesBuilder partitionAttributesBuilder = partitionAttributesBuilderFunction
                    .apply(RedisProtocolSupport.Command.PEXPIREAT);
        partitionAttributesBuilder.addKey(key);
        final Single<Long> result = partitionedRedisClient.request(partitionAttributesBuilder.build(), request, Long.class);
        return result;
    }

    @Override
    public Single<Long> pfadd(@RedisProtocolSupport.Key final CharSequence key, final CharSequence element) {
        requireNonNull(key);
        requireNonNull(element);
        final BufferAllocator allocator = partitionedRedisClient.getBufferAllocator();
        // Compute the number of request arguments, accounting for nullable ones
        int len = 3;
        final CompositeBuffer cb = newRequestCompositeBuffer(len, RedisProtocolSupport.Command.PFADD, allocator);
        addRequestArgument(key, cb, allocator);
        addRequestArgument(element, cb, allocator);
        final RedisRequest request = newRequest(RedisProtocolSupport.Command.PFADD, cb);
        final RedisPartitionAttributesBuilder partitionAttributesBuilder = partitionAttributesBuilderFunction
                    .apply(RedisProtocolSupport.Command.PFADD);
        partitionAttributesBuilder.addKey(key);
        final Single<Long> result = partitionedRedisClient.request(partitionAttributesBuilder.build(), request, Long.class);
        return result;
    }

    @Override
    public Single<Long> pfadd(@RedisProtocolSupport.Key final CharSequence key, final CharSequence element1,
                              final CharSequence element2) {
        requireNonNull(key);
        requireNonNull(element1);
        requireNonNull(element2);
        final BufferAllocator allocator = partitionedRedisClient.getBufferAllocator();
        // Compute the number of request arguments, accounting for nullable ones
        int len = 4;
        final CompositeBuffer cb = newRequestCompositeBuffer(len, RedisProtocolSupport.Command.PFADD, allocator);
        addRequestArgument(key, cb, allocator);
        addRequestArgument(element1, cb, allocator);
        addRequestArgument(element2, cb, allocator);
        final RedisRequest request = newRequest(RedisProtocolSupport.Command.PFADD, cb);
        final RedisPartitionAttributesBuilder partitionAttributesBuilder = partitionAttributesBuilderFunction
                    .apply(RedisProtocolSupport.Command.PFADD);
        partitionAttributesBuilder.addKey(key);
        final Single<Long> result = partitionedRedisClient.request(partitionAttributesBuilder.build(), request, Long.class);
        return result;
    }

    @Override
    public Single<Long> pfadd(@RedisProtocolSupport.Key final CharSequence key, final CharSequence element1,
                              final CharSequence element2, final CharSequence element3) {
        requireNonNull(key);
        requireNonNull(element1);
        requireNonNull(element2);
        requireNonNull(element3);
        final BufferAllocator allocator = partitionedRedisClient.getBufferAllocator();
        // Compute the number of request arguments, accounting for nullable ones
        int len = 5;
        final CompositeBuffer cb = newRequestCompositeBuffer(len, RedisProtocolSupport.Command.PFADD, allocator);
        addRequestArgument(key, cb, allocator);
        addRequestArgument(element1, cb, allocator);
        addRequestArgument(element2, cb, allocator);
        addRequestArgument(element3, cb, allocator);
        final RedisRequest request = newRequest(RedisProtocolSupport.Command.PFADD, cb);
        final RedisPartitionAttributesBuilder partitionAttributesBuilder = partitionAttributesBuilderFunction
                    .apply(RedisProtocolSupport.Command.PFADD);
        partitionAttributesBuilder.addKey(key);
        final Single<Long> result = partitionedRedisClient.request(partitionAttributesBuilder.build(), request, Long.class);
        return result;
    }

    @Override
    public Single<Long> pfadd(@RedisProtocolSupport.Key final CharSequence key,
                              final Collection<? extends CharSequence> elements) {
        requireNonNull(key);
        requireNonNull(elements);
        final BufferAllocator allocator = partitionedRedisClient.getBufferAllocator();
        // Compute the number of request arguments, accounting for nullable ones
        int len = 2;
        len += elements.size();
        final CompositeBuffer cb = newRequestCompositeBuffer(len, RedisProtocolSupport.Command.PFADD, allocator);
        addRequestArgument(key, cb, allocator);
        addRequestCharSequenceArguments(elements, null, cb, allocator);
        final RedisRequest request = newRequest(RedisProtocolSupport.Command.PFADD, cb);
        final RedisPartitionAttributesBuilder partitionAttributesBuilder = partitionAttributesBuilderFunction
                    .apply(RedisProtocolSupport.Command.PFADD);
        partitionAttributesBuilder.addKey(key);
        final Single<Long> result = partitionedRedisClient.request(partitionAttributesBuilder.build(), request, Long.class);
        return result;
    }

    @Override
    public Single<Long> pfcount(@RedisProtocolSupport.Key final CharSequence key) {
        requireNonNull(key);
        final BufferAllocator allocator = partitionedRedisClient.getBufferAllocator();
        // Compute the number of request arguments, accounting for nullable ones
        int len = 2;
        final CompositeBuffer cb = newRequestCompositeBuffer(len, RedisProtocolSupport.Command.PFCOUNT, allocator);
        addRequestArgument(key, cb, allocator);
        final RedisRequest request = newRequest(RedisProtocolSupport.Command.PFCOUNT, cb);
        final RedisPartitionAttributesBuilder partitionAttributesBuilder = partitionAttributesBuilderFunction
                    .apply(RedisProtocolSupport.Command.PFCOUNT);
        partitionAttributesBuilder.addKey(key);
        final Single<Long> result = partitionedRedisClient.request(partitionAttributesBuilder.build(), request, Long.class);
        return result;
    }

    @Override
    public Single<Long> pfcount(@RedisProtocolSupport.Key final CharSequence key1,
                                @RedisProtocolSupport.Key final CharSequence key2) {
        requireNonNull(key1);
        requireNonNull(key2);
        final BufferAllocator allocator = partitionedRedisClient.getBufferAllocator();
        // Compute the number of request arguments, accounting for nullable ones
        int len = 3;
        final CompositeBuffer cb = newRequestCompositeBuffer(len, RedisProtocolSupport.Command.PFCOUNT, allocator);
        addRequestArgument(key1, cb, allocator);
        addRequestArgument(key2, cb, allocator);
        final RedisRequest request = newRequest(RedisProtocolSupport.Command.PFCOUNT, cb);
        final RedisPartitionAttributesBuilder partitionAttributesBuilder = partitionAttributesBuilderFunction
                    .apply(RedisProtocolSupport.Command.PFCOUNT);
        partitionAttributesBuilder.addKey(key1);
        partitionAttributesBuilder.addKey(key2);
        final Single<Long> result = partitionedRedisClient.request(partitionAttributesBuilder.build(), request, Long.class);
        return result;
    }

    @Override
    public Single<Long> pfcount(@RedisProtocolSupport.Key final CharSequence key1,
                                @RedisProtocolSupport.Key final CharSequence key2,
                                @RedisProtocolSupport.Key final CharSequence key3) {
        requireNonNull(key1);
        requireNonNull(key2);
        requireNonNull(key3);
        final BufferAllocator allocator = partitionedRedisClient.getBufferAllocator();
        // Compute the number of request arguments, accounting for nullable ones
        int len = 4;
        final CompositeBuffer cb = newRequestCompositeBuffer(len, RedisProtocolSupport.Command.PFCOUNT, allocator);
        addRequestArgument(key1, cb, allocator);
        addRequestArgument(key2, cb, allocator);
        addRequestArgument(key3, cb, allocator);
        final RedisRequest request = newRequest(RedisProtocolSupport.Command.PFCOUNT, cb);
        final RedisPartitionAttributesBuilder partitionAttributesBuilder = partitionAttributesBuilderFunction
                    .apply(RedisProtocolSupport.Command.PFCOUNT);
        partitionAttributesBuilder.addKey(key1);
        partitionAttributesBuilder.addKey(key2);
        partitionAttributesBuilder.addKey(key3);
        final Single<Long> result = partitionedRedisClient.request(partitionAttributesBuilder.build(), request, Long.class);
        return result;
    }

    @Override
    public Single<Long> pfcount(@RedisProtocolSupport.Key final Collection<? extends CharSequence> keys) {
        requireNonNull(keys);
        final BufferAllocator allocator = partitionedRedisClient.getBufferAllocator();
        // Compute the number of request arguments, accounting for nullable ones
        int len = 1;
        len += keys.size();
        final CompositeBuffer cb = newRequestCompositeBuffer(len, RedisProtocolSupport.Command.PFCOUNT, allocator);
        addRequestCharSequenceArguments(keys, null, cb, allocator);
        final RedisRequest request = newRequest(RedisProtocolSupport.Command.PFCOUNT, cb);
        final RedisPartitionAttributesBuilder partitionAttributesBuilder = partitionAttributesBuilderFunction
                    .apply(RedisProtocolSupport.Command.PFCOUNT);
        addCharSequenceKeysToAttributeBuilder(keys, partitionAttributesBuilder);
        final Single<Long> result = partitionedRedisClient.request(partitionAttributesBuilder.build(), request, Long.class);
        return result;
    }

    @Override
    public Single<String> pfmerge(@RedisProtocolSupport.Key final CharSequence destkey,
                                  @RedisProtocolSupport.Key final CharSequence sourcekey) {
        requireNonNull(destkey);
        requireNonNull(sourcekey);
        final BufferAllocator allocator = partitionedRedisClient.getBufferAllocator();
        // Compute the number of request arguments, accounting for nullable ones
        int len = 3;
        final CompositeBuffer cb = newRequestCompositeBuffer(len, RedisProtocolSupport.Command.PFMERGE, allocator);
        addRequestArgument(destkey, cb, allocator);
        addRequestArgument(sourcekey, cb, allocator);
        final RedisRequest request = newRequest(RedisProtocolSupport.Command.PFMERGE, cb);
        final RedisPartitionAttributesBuilder partitionAttributesBuilder = partitionAttributesBuilderFunction
                    .apply(RedisProtocolSupport.Command.PFMERGE);
        partitionAttributesBuilder.addKey(destkey);
        partitionAttributesBuilder.addKey(sourcekey);
        final Single<String> result = partitionedRedisClient.request(partitionAttributesBuilder.build(), request, String.class);
        return result;
    }

    @Override
    public Single<String> pfmerge(@RedisProtocolSupport.Key final CharSequence destkey,
                                  @RedisProtocolSupport.Key final CharSequence sourcekey1,
                                  @RedisProtocolSupport.Key final CharSequence sourcekey2) {
        requireNonNull(destkey);
        requireNonNull(sourcekey1);
        requireNonNull(sourcekey2);
        final BufferAllocator allocator = partitionedRedisClient.getBufferAllocator();
        // Compute the number of request arguments, accounting for nullable ones
        int len = 4;
        final CompositeBuffer cb = newRequestCompositeBuffer(len, RedisProtocolSupport.Command.PFMERGE, allocator);
        addRequestArgument(destkey, cb, allocator);
        addRequestArgument(sourcekey1, cb, allocator);
        addRequestArgument(sourcekey2, cb, allocator);
        final RedisRequest request = newRequest(RedisProtocolSupport.Command.PFMERGE, cb);
        final RedisPartitionAttributesBuilder partitionAttributesBuilder = partitionAttributesBuilderFunction
                    .apply(RedisProtocolSupport.Command.PFMERGE);
        partitionAttributesBuilder.addKey(destkey);
        partitionAttributesBuilder.addKey(sourcekey1);
        partitionAttributesBuilder.addKey(sourcekey2);
        final Single<String> result = partitionedRedisClient.request(partitionAttributesBuilder.build(), request, String.class);
        return result;
    }

    @Override
    public Single<String> pfmerge(@RedisProtocolSupport.Key final CharSequence destkey,
                                  @RedisProtocolSupport.Key final CharSequence sourcekey1,
                                  @RedisProtocolSupport.Key final CharSequence sourcekey2,
                                  @RedisProtocolSupport.Key final CharSequence sourcekey3) {
        requireNonNull(destkey);
        requireNonNull(sourcekey1);
        requireNonNull(sourcekey2);
        requireNonNull(sourcekey3);
        final BufferAllocator allocator = partitionedRedisClient.getBufferAllocator();
        // Compute the number of request arguments, accounting for nullable ones
        int len = 5;
        final CompositeBuffer cb = newRequestCompositeBuffer(len, RedisProtocolSupport.Command.PFMERGE, allocator);
        addRequestArgument(destkey, cb, allocator);
        addRequestArgument(sourcekey1, cb, allocator);
        addRequestArgument(sourcekey2, cb, allocator);
        addRequestArgument(sourcekey3, cb, allocator);
        final RedisRequest request = newRequest(RedisProtocolSupport.Command.PFMERGE, cb);
        final RedisPartitionAttributesBuilder partitionAttributesBuilder = partitionAttributesBuilderFunction
                    .apply(RedisProtocolSupport.Command.PFMERGE);
        partitionAttributesBuilder.addKey(destkey);
        partitionAttributesBuilder.addKey(sourcekey1);
        partitionAttributesBuilder.addKey(sourcekey2);
        partitionAttributesBuilder.addKey(sourcekey3);
        final Single<String> result = partitionedRedisClient.request(partitionAttributesBuilder.build(), request, String.class);
        return result;
    }

    @Override
    public Single<String> pfmerge(@RedisProtocolSupport.Key final CharSequence destkey,
                                  @RedisProtocolSupport.Key final Collection<? extends CharSequence> sourcekeys) {
        requireNonNull(destkey);
        requireNonNull(sourcekeys);
        final BufferAllocator allocator = partitionedRedisClient.getBufferAllocator();
        // Compute the number of request arguments, accounting for nullable ones
        int len = 2;
        len += sourcekeys.size();
        final CompositeBuffer cb = newRequestCompositeBuffer(len, RedisProtocolSupport.Command.PFMERGE, allocator);
        addRequestArgument(destkey, cb, allocator);
        addRequestCharSequenceArguments(sourcekeys, null, cb, allocator);
        final RedisRequest request = newRequest(RedisProtocolSupport.Command.PFMERGE, cb);
        final RedisPartitionAttributesBuilder partitionAttributesBuilder = partitionAttributesBuilderFunction
                    .apply(RedisProtocolSupport.Command.PFMERGE);
        partitionAttributesBuilder.addKey(destkey);
        addCharSequenceKeysToAttributeBuilder(sourcekeys, partitionAttributesBuilder);
        final Single<String> result = partitionedRedisClient.request(partitionAttributesBuilder.build(), request, String.class);
        return result;
    }

    @Override
    public Single<String> ping() {
        final BufferAllocator allocator = partitionedRedisClient.getBufferAllocator();
        // Compute the number of request arguments, accounting for nullable ones
        int len = 1;
        final CompositeBuffer cb = newRequestCompositeBuffer(len, RedisProtocolSupport.Command.PING, allocator);
        final RedisRequest request = newRequest(RedisProtocolSupport.Command.PING, cb);
        final RedisPartitionAttributesBuilder partitionAttributesBuilder = partitionAttributesBuilderFunction
                    .apply(RedisProtocolSupport.Command.PING);
        final Single<String> result = partitionedRedisClient.request(partitionAttributesBuilder.build(), request, String.class);
        return result;
    }

    @Override
    public Single<String> ping(final CharSequence message) {
        requireNonNull(message);
        final BufferAllocator allocator = partitionedRedisClient.getBufferAllocator();
        // Compute the number of request arguments, accounting for nullable ones
        int len = 2;
        final CompositeBuffer cb = newRequestCompositeBuffer(len, RedisProtocolSupport.Command.PING, allocator);
        addRequestArgument(message, cb, allocator);
        final RedisRequest request = newRequest(RedisProtocolSupport.Command.PING, cb);
        final RedisPartitionAttributesBuilder partitionAttributesBuilder = partitionAttributesBuilderFunction
                    .apply(RedisProtocolSupport.Command.PING);
        final Single<String> result = partitionedRedisClient.request(partitionAttributesBuilder.build(), request, String.class);
        return result;
    }

    @Override
    public Single<String> psetex(@RedisProtocolSupport.Key final CharSequence key, final long milliseconds,
                                 final CharSequence value) {
        requireNonNull(key);
        requireNonNull(value);
        final BufferAllocator allocator = partitionedRedisClient.getBufferAllocator();
        // Compute the number of request arguments, accounting for nullable ones
        int len = 4;
        final CompositeBuffer cb = newRequestCompositeBuffer(len, RedisProtocolSupport.Command.PSETEX, allocator);
        addRequestArgument(key, cb, allocator);
        addRequestArgument(milliseconds, cb, allocator);
        addRequestArgument(value, cb, allocator);
        final RedisRequest request = newRequest(RedisProtocolSupport.Command.PSETEX, cb);
        final RedisPartitionAttributesBuilder partitionAttributesBuilder = partitionAttributesBuilderFunction
                    .apply(RedisProtocolSupport.Command.PSETEX);
        partitionAttributesBuilder.addKey(key);
        final Single<String> result = partitionedRedisClient.request(partitionAttributesBuilder.build(), request, String.class);
        return result;
    }

    @Override
    public Single<PubSubRedisConnection> psubscribe(final CharSequence pattern) {
        requireNonNull(pattern);
        final BufferAllocator allocator = partitionedRedisClient.getBufferAllocator();
        // Compute the number of request arguments, accounting for nullable ones
        int len = 2;
        final CompositeBuffer cb = newRequestCompositeBuffer(len, RedisProtocolSupport.Command.PSUBSCRIBE, allocator);
        addRequestArgument(pattern, cb, allocator);
        final RedisRequest request = newRequest(RedisProtocolSupport.Command.PSUBSCRIBE, cb);
        final RedisPartitionAttributesBuilder partitionAttributesBuilder = partitionAttributesBuilderFunction
                    .apply(RedisProtocolSupport.Command.PSUBSCRIBE);
        return newConnectedClient(partitionedRedisClient, partitionAttributesBuilder.build(), request,
                    (rcnx, pub) -> new DefaultPubSubRedisConnection(rcnx, pub.map(msg -> (PubSubRedisMessage) msg)));
    }

    @Override
    public Single<Long> pttl(@RedisProtocolSupport.Key final CharSequence key) {
        requireNonNull(key);
        final BufferAllocator allocator = partitionedRedisClient.getBufferAllocator();
        // Compute the number of request arguments, accounting for nullable ones
        int len = 2;
        final CompositeBuffer cb = newRequestCompositeBuffer(len, RedisProtocolSupport.Command.PTTL, allocator);
        addRequestArgument(key, cb, allocator);
        final RedisRequest request = newRequest(RedisProtocolSupport.Command.PTTL, cb);
        final RedisPartitionAttributesBuilder partitionAttributesBuilder = partitionAttributesBuilderFunction
                    .apply(RedisProtocolSupport.Command.PTTL);
        partitionAttributesBuilder.addKey(key);
        final Single<Long> result = partitionedRedisClient.request(partitionAttributesBuilder.build(), request, Long.class);
        return result;
    }

    @Override
    public Single<Long> publish(final CharSequence channel, final CharSequence message) {
        requireNonNull(channel);
        requireNonNull(message);
        final BufferAllocator allocator = partitionedRedisClient.getBufferAllocator();
        // Compute the number of request arguments, accounting for nullable ones
        int len = 3;
        final CompositeBuffer cb = newRequestCompositeBuffer(len, RedisProtocolSupport.Command.PUBLISH, allocator);
        addRequestArgument(channel, cb, allocator);
        addRequestArgument(message, cb, allocator);
        final RedisRequest request = newRequest(RedisProtocolSupport.Command.PUBLISH, cb);
        final RedisPartitionAttributesBuilder partitionAttributesBuilder = partitionAttributesBuilderFunction
                    .apply(RedisProtocolSupport.Command.PUBLISH);
        final Single<Long> result = partitionedRedisClient.request(partitionAttributesBuilder.build(), request, Long.class);
        return result;
    }

    @Override
    public Single<List<String>> pubsubChannels() {
        final BufferAllocator allocator = partitionedRedisClient.getBufferAllocator();
        // Compute the number of request arguments, accounting for nullable ones
        int len = 2;
        final CompositeBuffer cb = newRequestCompositeBuffer(len, RedisProtocolSupport.Command.PUBSUB,
                    RedisProtocolSupport.SubCommand.CHANNELS, allocator);
        final RedisRequest request = newRequest(RedisProtocolSupport.Command.PUBSUB, cb);
        final RedisPartitionAttributesBuilder partitionAttributesBuilder = partitionAttributesBuilderFunction
                    .apply(RedisProtocolSupport.Command.PUBSUB);
        final Single<List<String>> result = (Single) partitionedRedisClient.request(partitionAttributesBuilder.build(), request,
                    RedisUtils.ListWithBuffersCoercedToCharSequences.class);
        return result;
    }

    @Override
    public Single<List<String>> pubsubChannels(@Nullable final CharSequence pattern) {
        final BufferAllocator allocator = partitionedRedisClient.getBufferAllocator();
        // Compute the number of request arguments, accounting for nullable ones
        int len = 2;
        if (pattern != null) {
            len++;
        }
        final CompositeBuffer cb = newRequestCompositeBuffer(len, RedisProtocolSupport.Command.PUBSUB,
                    RedisProtocolSupport.SubCommand.CHANNELS, allocator);
        if (pattern != null) {
            addRequestArgument(pattern, cb, allocator);
        }
        final RedisRequest request = newRequest(RedisProtocolSupport.Command.PUBSUB, cb);
        final RedisPartitionAttributesBuilder partitionAttributesBuilder = partitionAttributesBuilderFunction
                    .apply(RedisProtocolSupport.Command.PUBSUB);
        final Single<List<String>> result = (Single) partitionedRedisClient.request(partitionAttributesBuilder.build(), request,
                    RedisUtils.ListWithBuffersCoercedToCharSequences.class);
        return result;
    }

    @Override
    public Single<List<String>> pubsubChannels(@Nullable final CharSequence pattern1, @Nullable final CharSequence pattern2) {
        final BufferAllocator allocator = partitionedRedisClient.getBufferAllocator();
        // Compute the number of request arguments, accounting for nullable ones
        int len = 2;
        if (pattern1 != null) {
            len++;
        }
        if (pattern2 != null) {
            len++;
        }
        final CompositeBuffer cb = newRequestCompositeBuffer(len, RedisProtocolSupport.Command.PUBSUB,
                    RedisProtocolSupport.SubCommand.CHANNELS, allocator);
        if (pattern1 != null) {
            addRequestArgument(pattern1, cb, allocator);
        }
        if (pattern2 != null) {
            addRequestArgument(pattern2, cb, allocator);
        }
        final RedisRequest request = newRequest(RedisProtocolSupport.Command.PUBSUB, cb);
        final RedisPartitionAttributesBuilder partitionAttributesBuilder = partitionAttributesBuilderFunction
                    .apply(RedisProtocolSupport.Command.PUBSUB);
        final Single<List<String>> result = (Single) partitionedRedisClient.request(partitionAttributesBuilder.build(), request,
                    RedisUtils.ListWithBuffersCoercedToCharSequences.class);
        return result;
    }

    @Override
    public Single<List<String>> pubsubChannels(@Nullable final CharSequence pattern1, @Nullable final CharSequence pattern2,
                                               @Nullable final CharSequence pattern3) {
        final BufferAllocator allocator = partitionedRedisClient.getBufferAllocator();
        // Compute the number of request arguments, accounting for nullable ones
        int len = 2;
        if (pattern1 != null) {
            len++;
        }
        if (pattern2 != null) {
            len++;
        }
        if (pattern3 != null) {
            len++;
        }
        final CompositeBuffer cb = newRequestCompositeBuffer(len, RedisProtocolSupport.Command.PUBSUB,
                    RedisProtocolSupport.SubCommand.CHANNELS, allocator);
        if (pattern1 != null) {
            addRequestArgument(pattern1, cb, allocator);
        }
        if (pattern2 != null) {
            addRequestArgument(pattern2, cb, allocator);
        }
        if (pattern3 != null) {
            addRequestArgument(pattern3, cb, allocator);
        }
        final RedisRequest request = newRequest(RedisProtocolSupport.Command.PUBSUB, cb);
        final RedisPartitionAttributesBuilder partitionAttributesBuilder = partitionAttributesBuilderFunction
                    .apply(RedisProtocolSupport.Command.PUBSUB);
        final Single<List<String>> result = (Single) partitionedRedisClient.request(partitionAttributesBuilder.build(), request,
                    RedisUtils.ListWithBuffersCoercedToCharSequences.class);
        return result;
    }

    @Override
    public Single<List<String>> pubsubChannels(final Collection<? extends CharSequence> patterns) {
        requireNonNull(patterns);
        final BufferAllocator allocator = partitionedRedisClient.getBufferAllocator();
        // Compute the number of request arguments, accounting for nullable ones
        int len = 2;
        len += patterns.size();
        final CompositeBuffer cb = newRequestCompositeBuffer(len, RedisProtocolSupport.Command.PUBSUB,
                    RedisProtocolSupport.SubCommand.CHANNELS, allocator);
        addRequestCharSequenceArguments(patterns, null, cb, allocator);
        final RedisRequest request = newRequest(RedisProtocolSupport.Command.PUBSUB, cb);
        final RedisPartitionAttributesBuilder partitionAttributesBuilder = partitionAttributesBuilderFunction
                    .apply(RedisProtocolSupport.Command.PUBSUB);
        final Single<List<String>> result = (Single) partitionedRedisClient.request(partitionAttributesBuilder.build(), request,
                    RedisUtils.ListWithBuffersCoercedToCharSequences.class);
        return result;
    }

    @Override
    public <T> Single<List<T>> pubsubNumsub() {
        final BufferAllocator allocator = partitionedRedisClient.getBufferAllocator();
        // Compute the number of request arguments, accounting for nullable ones
        int len = 2;
        final CompositeBuffer cb = newRequestCompositeBuffer(len, RedisProtocolSupport.Command.PUBSUB,
                    RedisProtocolSupport.SubCommand.NUMSUB, allocator);
        final RedisRequest request = newRequest(RedisProtocolSupport.Command.PUBSUB, cb);
        final RedisPartitionAttributesBuilder partitionAttributesBuilder = partitionAttributesBuilderFunction
                    .apply(RedisProtocolSupport.Command.PUBSUB);
        final Single<List<T>> result = (Single) partitionedRedisClient.request(partitionAttributesBuilder.build(), request,
                    RedisUtils.ListWithBuffersCoercedToCharSequences.class);
        return result;
    }

    @Override
    public <T> Single<List<T>> pubsubNumsub(@Nullable final CharSequence channel) {
        final BufferAllocator allocator = partitionedRedisClient.getBufferAllocator();
        // Compute the number of request arguments, accounting for nullable ones
        int len = 2;
        if (channel != null) {
            len++;
        }
        final CompositeBuffer cb = newRequestCompositeBuffer(len, RedisProtocolSupport.Command.PUBSUB,
                    RedisProtocolSupport.SubCommand.NUMSUB, allocator);
        if (channel != null) {
            addRequestArgument(channel, cb, allocator);
        }
        final RedisRequest request = newRequest(RedisProtocolSupport.Command.PUBSUB, cb);
        final RedisPartitionAttributesBuilder partitionAttributesBuilder = partitionAttributesBuilderFunction
                    .apply(RedisProtocolSupport.Command.PUBSUB);
        final Single<List<T>> result = (Single) partitionedRedisClient.request(partitionAttributesBuilder.build(), request,
                    RedisUtils.ListWithBuffersCoercedToCharSequences.class);
        return result;
    }

    @Override
    public <T> Single<List<T>> pubsubNumsub(@Nullable final CharSequence channel1, @Nullable final CharSequence channel2) {
        final BufferAllocator allocator = partitionedRedisClient.getBufferAllocator();
        // Compute the number of request arguments, accounting for nullable ones
        int len = 2;
        if (channel1 != null) {
            len++;
        }
        if (channel2 != null) {
            len++;
        }
        final CompositeBuffer cb = newRequestCompositeBuffer(len, RedisProtocolSupport.Command.PUBSUB,
                    RedisProtocolSupport.SubCommand.NUMSUB, allocator);
        if (channel1 != null) {
            addRequestArgument(channel1, cb, allocator);
        }
        if (channel2 != null) {
            addRequestArgument(channel2, cb, allocator);
        }
        final RedisRequest request = newRequest(RedisProtocolSupport.Command.PUBSUB, cb);
        final RedisPartitionAttributesBuilder partitionAttributesBuilder = partitionAttributesBuilderFunction
                    .apply(RedisProtocolSupport.Command.PUBSUB);
        final Single<List<T>> result = (Single) partitionedRedisClient.request(partitionAttributesBuilder.build(), request,
                    RedisUtils.ListWithBuffersCoercedToCharSequences.class);
        return result;
    }

    @Override
    public <T> Single<List<T>> pubsubNumsub(@Nullable final CharSequence channel1, @Nullable final CharSequence channel2,
                                            @Nullable final CharSequence channel3) {
        final BufferAllocator allocator = partitionedRedisClient.getBufferAllocator();
        // Compute the number of request arguments, accounting for nullable ones
        int len = 2;
        if (channel1 != null) {
            len++;
        }
        if (channel2 != null) {
            len++;
        }
        if (channel3 != null) {
            len++;
        }
        final CompositeBuffer cb = newRequestCompositeBuffer(len, RedisProtocolSupport.Command.PUBSUB,
                    RedisProtocolSupport.SubCommand.NUMSUB, allocator);
        if (channel1 != null) {
            addRequestArgument(channel1, cb, allocator);
        }
        if (channel2 != null) {
            addRequestArgument(channel2, cb, allocator);
        }
        if (channel3 != null) {
            addRequestArgument(channel3, cb, allocator);
        }
        final RedisRequest request = newRequest(RedisProtocolSupport.Command.PUBSUB, cb);
        final RedisPartitionAttributesBuilder partitionAttributesBuilder = partitionAttributesBuilderFunction
                    .apply(RedisProtocolSupport.Command.PUBSUB);
        final Single<List<T>> result = (Single) partitionedRedisClient.request(partitionAttributesBuilder.build(), request,
                    RedisUtils.ListWithBuffersCoercedToCharSequences.class);
        return result;
    }

    @Override
    public <T> Single<List<T>> pubsubNumsub(final Collection<? extends CharSequence> channels) {
        requireNonNull(channels);
        final BufferAllocator allocator = partitionedRedisClient.getBufferAllocator();
        // Compute the number of request arguments, accounting for nullable ones
        int len = 2;
        len += channels.size();
        final CompositeBuffer cb = newRequestCompositeBuffer(len, RedisProtocolSupport.Command.PUBSUB,
                    RedisProtocolSupport.SubCommand.NUMSUB, allocator);
        addRequestCharSequenceArguments(channels, null, cb, allocator);
        final RedisRequest request = newRequest(RedisProtocolSupport.Command.PUBSUB, cb);
        final RedisPartitionAttributesBuilder partitionAttributesBuilder = partitionAttributesBuilderFunction
                    .apply(RedisProtocolSupport.Command.PUBSUB);
        final Single<List<T>> result = (Single) partitionedRedisClient.request(partitionAttributesBuilder.build(), request,
                    RedisUtils.ListWithBuffersCoercedToCharSequences.class);
        return result;
    }

    @Override
    public Single<Long> pubsubNumpat() {
        final BufferAllocator allocator = partitionedRedisClient.getBufferAllocator();
        // Compute the number of request arguments, accounting for nullable ones
        int len = 2;
        final CompositeBuffer cb = newRequestCompositeBuffer(len, RedisProtocolSupport.Command.PUBSUB,
                    RedisProtocolSupport.SubCommand.NUMPAT, allocator);
        final RedisRequest request = newRequest(RedisProtocolSupport.Command.PUBSUB, cb);
        final RedisPartitionAttributesBuilder partitionAttributesBuilder = partitionAttributesBuilderFunction
                    .apply(RedisProtocolSupport.Command.PUBSUB);
        final Single<Long> result = partitionedRedisClient.request(partitionAttributesBuilder.build(), request, Long.class);
        return result;
    }

    @Override
    public Single<String> randomkey() {
        final BufferAllocator allocator = partitionedRedisClient.getBufferAllocator();
        // Compute the number of request arguments, accounting for nullable ones
        int len = 1;
        final CompositeBuffer cb = newRequestCompositeBuffer(len, RedisProtocolSupport.Command.RANDOMKEY, allocator);
        final RedisRequest request = newRequest(RedisProtocolSupport.Command.RANDOMKEY, cb);
        final RedisPartitionAttributesBuilder partitionAttributesBuilder = partitionAttributesBuilderFunction
                    .apply(RedisProtocolSupport.Command.RANDOMKEY);
        final Single<String> result = partitionedRedisClient.request(partitionAttributesBuilder.build(), request, String.class);
        return result;
    }

    @Override
    public Single<String> readonly() {
        final BufferAllocator allocator = partitionedRedisClient.getBufferAllocator();
        // Compute the number of request arguments, accounting for nullable ones
        int len = 1;
        final CompositeBuffer cb = newRequestCompositeBuffer(len, RedisProtocolSupport.Command.READONLY, allocator);
        final RedisRequest request = newRequest(RedisProtocolSupport.Command.READONLY, cb);
        final RedisPartitionAttributesBuilder partitionAttributesBuilder = partitionAttributesBuilderFunction
                    .apply(RedisProtocolSupport.Command.READONLY);
        final Single<String> result = partitionedRedisClient.request(partitionAttributesBuilder.build(), request, String.class);
        return result;
    }

    @Override
    public Single<String> readwrite() {
        final BufferAllocator allocator = partitionedRedisClient.getBufferAllocator();
        // Compute the number of request arguments, accounting for nullable ones
        int len = 1;
        final CompositeBuffer cb = newRequestCompositeBuffer(len, RedisProtocolSupport.Command.READWRITE, allocator);
        final RedisRequest request = newRequest(RedisProtocolSupport.Command.READWRITE, cb);
        final RedisPartitionAttributesBuilder partitionAttributesBuilder = partitionAttributesBuilderFunction
                    .apply(RedisProtocolSupport.Command.READWRITE);
        final Single<String> result = partitionedRedisClient.request(partitionAttributesBuilder.build(), request, String.class);
        return result;
    }

    @Override
    public Single<String> rename(@RedisProtocolSupport.Key final CharSequence key,
                                 @RedisProtocolSupport.Key final CharSequence newkey) {
        requireNonNull(key);
        requireNonNull(newkey);
        final BufferAllocator allocator = partitionedRedisClient.getBufferAllocator();
        // Compute the number of request arguments, accounting for nullable ones
        int len = 3;
        final CompositeBuffer cb = newRequestCompositeBuffer(len, RedisProtocolSupport.Command.RENAME, allocator);
        addRequestArgument(key, cb, allocator);
        addRequestArgument(newkey, cb, allocator);
        final RedisRequest request = newRequest(RedisProtocolSupport.Command.RENAME, cb);
        final RedisPartitionAttributesBuilder partitionAttributesBuilder = partitionAttributesBuilderFunction
                    .apply(RedisProtocolSupport.Command.RENAME);
        partitionAttributesBuilder.addKey(key);
        partitionAttributesBuilder.addKey(newkey);
        final Single<String> result = partitionedRedisClient.request(partitionAttributesBuilder.build(), request, String.class);
        return result;
    }

    @Override
    public Single<Long> renamenx(@RedisProtocolSupport.Key final CharSequence key,
                                 @RedisProtocolSupport.Key final CharSequence newkey) {
        requireNonNull(key);
        requireNonNull(newkey);
        final BufferAllocator allocator = partitionedRedisClient.getBufferAllocator();
        // Compute the number of request arguments, accounting for nullable ones
        int len = 3;
        final CompositeBuffer cb = newRequestCompositeBuffer(len, RedisProtocolSupport.Command.RENAMENX, allocator);
        addRequestArgument(key, cb, allocator);
        addRequestArgument(newkey, cb, allocator);
        final RedisRequest request = newRequest(RedisProtocolSupport.Command.RENAMENX, cb);
        final RedisPartitionAttributesBuilder partitionAttributesBuilder = partitionAttributesBuilderFunction
                    .apply(RedisProtocolSupport.Command.RENAMENX);
        partitionAttributesBuilder.addKey(key);
        partitionAttributesBuilder.addKey(newkey);
        final Single<Long> result = partitionedRedisClient.request(partitionAttributesBuilder.build(), request, Long.class);
        return result;
    }

    @Override
    public Single<String> restore(@RedisProtocolSupport.Key final CharSequence key, final long ttl,
                                  final CharSequence serializedValue) {
        requireNonNull(key);
        requireNonNull(serializedValue);
        final BufferAllocator allocator = partitionedRedisClient.getBufferAllocator();
        // Compute the number of request arguments, accounting for nullable ones
        int len = 4;
        final CompositeBuffer cb = newRequestCompositeBuffer(len, RedisProtocolSupport.Command.RESTORE, allocator);
        addRequestArgument(key, cb, allocator);
        addRequestArgument(ttl, cb, allocator);
        addRequestArgument(serializedValue, cb, allocator);
        final RedisRequest request = newRequest(RedisProtocolSupport.Command.RESTORE, cb);
        final RedisPartitionAttributesBuilder partitionAttributesBuilder = partitionAttributesBuilderFunction
                    .apply(RedisProtocolSupport.Command.RESTORE);
        partitionAttributesBuilder.addKey(key);
        final Single<String> result = partitionedRedisClient.request(partitionAttributesBuilder.build(), request, String.class);
        return result;
    }

    @Override
    public Single<String> restore(@RedisProtocolSupport.Key final CharSequence key, final long ttl,
                                  final CharSequence serializedValue,
                                  @Nullable final RedisProtocolSupport.RestoreReplace replace) {
        requireNonNull(key);
        requireNonNull(serializedValue);
        final BufferAllocator allocator = partitionedRedisClient.getBufferAllocator();
        // Compute the number of request arguments, accounting for nullable ones
        int len = 4;
        if (replace != null) {
            len++;
        }
        final CompositeBuffer cb = newRequestCompositeBuffer(len, RedisProtocolSupport.Command.RESTORE, allocator);
        addRequestArgument(key, cb, allocator);
        addRequestArgument(ttl, cb, allocator);
        addRequestArgument(serializedValue, cb, allocator);
        if (replace != null) {
            addRequestArgument(replace, cb, allocator);
        }
        final RedisRequest request = newRequest(RedisProtocolSupport.Command.RESTORE, cb);
        final RedisPartitionAttributesBuilder partitionAttributesBuilder = partitionAttributesBuilderFunction
                    .apply(RedisProtocolSupport.Command.RESTORE);
        partitionAttributesBuilder.addKey(key);
        final Single<String> result = partitionedRedisClient.request(partitionAttributesBuilder.build(), request, String.class);
        return result;
    }

    @Override
    public <T> Single<List<T>> role() {
        final BufferAllocator allocator = partitionedRedisClient.getBufferAllocator();
        // Compute the number of request arguments, accounting for nullable ones
        int len = 1;
        final CompositeBuffer cb = newRequestCompositeBuffer(len, RedisProtocolSupport.Command.ROLE, allocator);
        final RedisRequest request = newRequest(RedisProtocolSupport.Command.ROLE, cb);
        final RedisPartitionAttributesBuilder partitionAttributesBuilder = partitionAttributesBuilderFunction
                    .apply(RedisProtocolSupport.Command.ROLE);
        final Single<List<T>> result = (Single) partitionedRedisClient.request(partitionAttributesBuilder.build(), request,
                    RedisUtils.ListWithBuffersCoercedToCharSequences.class);
        return result;
    }

    @Override
    public Single<String> rpop(@RedisProtocolSupport.Key final CharSequence key) {
        requireNonNull(key);
        final BufferAllocator allocator = partitionedRedisClient.getBufferAllocator();
        // Compute the number of request arguments, accounting for nullable ones
        int len = 2;
        final CompositeBuffer cb = newRequestCompositeBuffer(len, RedisProtocolSupport.Command.RPOP, allocator);
        addRequestArgument(key, cb, allocator);
        final RedisRequest request = newRequest(RedisProtocolSupport.Command.RPOP, cb);
        final RedisPartitionAttributesBuilder partitionAttributesBuilder = partitionAttributesBuilderFunction
                    .apply(RedisProtocolSupport.Command.RPOP);
        partitionAttributesBuilder.addKey(key);
        final Single<String> result = partitionedRedisClient.request(partitionAttributesBuilder.build(), request, String.class);
        return result;
    }

    @Override
    public Single<String> rpoplpush(@RedisProtocolSupport.Key final CharSequence source,
                                    @RedisProtocolSupport.Key final CharSequence destination) {
        requireNonNull(source);
        requireNonNull(destination);
        final BufferAllocator allocator = partitionedRedisClient.getBufferAllocator();
        // Compute the number of request arguments, accounting for nullable ones
        int len = 3;
        final CompositeBuffer cb = newRequestCompositeBuffer(len, RedisProtocolSupport.Command.RPOPLPUSH, allocator);
        addRequestArgument(source, cb, allocator);
        addRequestArgument(destination, cb, allocator);
        final RedisRequest request = newRequest(RedisProtocolSupport.Command.RPOPLPUSH, cb);
        final RedisPartitionAttributesBuilder partitionAttributesBuilder = partitionAttributesBuilderFunction
                    .apply(RedisProtocolSupport.Command.RPOPLPUSH);
        partitionAttributesBuilder.addKey(source);
        partitionAttributesBuilder.addKey(destination);
        final Single<String> result = partitionedRedisClient.request(partitionAttributesBuilder.build(), request, String.class);
        return result;
    }

    @Override
    public Single<Long> rpush(@RedisProtocolSupport.Key final CharSequence key, final CharSequence value) {
        requireNonNull(key);
        requireNonNull(value);
        final BufferAllocator allocator = partitionedRedisClient.getBufferAllocator();
        // Compute the number of request arguments, accounting for nullable ones
        int len = 3;
        final CompositeBuffer cb = newRequestCompositeBuffer(len, RedisProtocolSupport.Command.RPUSH, allocator);
        addRequestArgument(key, cb, allocator);
        addRequestArgument(value, cb, allocator);
        final RedisRequest request = newRequest(RedisProtocolSupport.Command.RPUSH, cb);
        final RedisPartitionAttributesBuilder partitionAttributesBuilder = partitionAttributesBuilderFunction
                    .apply(RedisProtocolSupport.Command.RPUSH);
        partitionAttributesBuilder.addKey(key);
        final Single<Long> result = partitionedRedisClient.request(partitionAttributesBuilder.build(), request, Long.class);
        return result;
    }

    @Override
    public Single<Long> rpush(@RedisProtocolSupport.Key final CharSequence key, final CharSequence value1,
                              final CharSequence value2) {
        requireNonNull(key);
        requireNonNull(value1);
        requireNonNull(value2);
        final BufferAllocator allocator = partitionedRedisClient.getBufferAllocator();
        // Compute the number of request arguments, accounting for nullable ones
        int len = 4;
        final CompositeBuffer cb = newRequestCompositeBuffer(len, RedisProtocolSupport.Command.RPUSH, allocator);
        addRequestArgument(key, cb, allocator);
        addRequestArgument(value1, cb, allocator);
        addRequestArgument(value2, cb, allocator);
        final RedisRequest request = newRequest(RedisProtocolSupport.Command.RPUSH, cb);
        final RedisPartitionAttributesBuilder partitionAttributesBuilder = partitionAttributesBuilderFunction
                    .apply(RedisProtocolSupport.Command.RPUSH);
        partitionAttributesBuilder.addKey(key);
        final Single<Long> result = partitionedRedisClient.request(partitionAttributesBuilder.build(), request, Long.class);
        return result;
    }

    @Override
    public Single<Long> rpush(@RedisProtocolSupport.Key final CharSequence key, final CharSequence value1,
                              final CharSequence value2, final CharSequence value3) {
        requireNonNull(key);
        requireNonNull(value1);
        requireNonNull(value2);
        requireNonNull(value3);
        final BufferAllocator allocator = partitionedRedisClient.getBufferAllocator();
        // Compute the number of request arguments, accounting for nullable ones
        int len = 5;
        final CompositeBuffer cb = newRequestCompositeBuffer(len, RedisProtocolSupport.Command.RPUSH, allocator);
        addRequestArgument(key, cb, allocator);
        addRequestArgument(value1, cb, allocator);
        addRequestArgument(value2, cb, allocator);
        addRequestArgument(value3, cb, allocator);
        final RedisRequest request = newRequest(RedisProtocolSupport.Command.RPUSH, cb);
        final RedisPartitionAttributesBuilder partitionAttributesBuilder = partitionAttributesBuilderFunction
                    .apply(RedisProtocolSupport.Command.RPUSH);
        partitionAttributesBuilder.addKey(key);
        final Single<Long> result = partitionedRedisClient.request(partitionAttributesBuilder.build(), request, Long.class);
        return result;
    }

    @Override
    public Single<Long> rpush(@RedisProtocolSupport.Key final CharSequence key, final Collection<? extends CharSequence> values) {
        requireNonNull(key);
        requireNonNull(values);
        final BufferAllocator allocator = partitionedRedisClient.getBufferAllocator();
        // Compute the number of request arguments, accounting for nullable ones
        int len = 2;
        len += values.size();
        final CompositeBuffer cb = newRequestCompositeBuffer(len, RedisProtocolSupport.Command.RPUSH, allocator);
        addRequestArgument(key, cb, allocator);
        addRequestCharSequenceArguments(values, null, cb, allocator);
        final RedisRequest request = newRequest(RedisProtocolSupport.Command.RPUSH, cb);
        final RedisPartitionAttributesBuilder partitionAttributesBuilder = partitionAttributesBuilderFunction
                    .apply(RedisProtocolSupport.Command.RPUSH);
        partitionAttributesBuilder.addKey(key);
        final Single<Long> result = partitionedRedisClient.request(partitionAttributesBuilder.build(), request, Long.class);
        return result;
    }

    @Override
    public Single<Long> rpushx(@RedisProtocolSupport.Key final CharSequence key, final CharSequence value) {
        requireNonNull(key);
        requireNonNull(value);
        final BufferAllocator allocator = partitionedRedisClient.getBufferAllocator();
        // Compute the number of request arguments, accounting for nullable ones
        int len = 3;
        final CompositeBuffer cb = newRequestCompositeBuffer(len, RedisProtocolSupport.Command.RPUSHX, allocator);
        addRequestArgument(key, cb, allocator);
        addRequestArgument(value, cb, allocator);
        final RedisRequest request = newRequest(RedisProtocolSupport.Command.RPUSHX, cb);
        final RedisPartitionAttributesBuilder partitionAttributesBuilder = partitionAttributesBuilderFunction
                    .apply(RedisProtocolSupport.Command.RPUSHX);
        partitionAttributesBuilder.addKey(key);
        final Single<Long> result = partitionedRedisClient.request(partitionAttributesBuilder.build(), request, Long.class);
        return result;
    }

    @Override
    public Single<Long> sadd(@RedisProtocolSupport.Key final CharSequence key, final CharSequence member) {
        requireNonNull(key);
        requireNonNull(member);
        final BufferAllocator allocator = partitionedRedisClient.getBufferAllocator();
        // Compute the number of request arguments, accounting for nullable ones
        int len = 3;
        final CompositeBuffer cb = newRequestCompositeBuffer(len, RedisProtocolSupport.Command.SADD, allocator);
        addRequestArgument(key, cb, allocator);
        addRequestArgument(member, cb, allocator);
        final RedisRequest request = newRequest(RedisProtocolSupport.Command.SADD, cb);
        final RedisPartitionAttributesBuilder partitionAttributesBuilder = partitionAttributesBuilderFunction
                    .apply(RedisProtocolSupport.Command.SADD);
        partitionAttributesBuilder.addKey(key);
        final Single<Long> result = partitionedRedisClient.request(partitionAttributesBuilder.build(), request, Long.class);
        return result;
    }

    @Override
    public Single<Long> sadd(@RedisProtocolSupport.Key final CharSequence key, final CharSequence member1,
                             final CharSequence member2) {
        requireNonNull(key);
        requireNonNull(member1);
        requireNonNull(member2);
        final BufferAllocator allocator = partitionedRedisClient.getBufferAllocator();
        // Compute the number of request arguments, accounting for nullable ones
        int len = 4;
        final CompositeBuffer cb = newRequestCompositeBuffer(len, RedisProtocolSupport.Command.SADD, allocator);
        addRequestArgument(key, cb, allocator);
        addRequestArgument(member1, cb, allocator);
        addRequestArgument(member2, cb, allocator);
        final RedisRequest request = newRequest(RedisProtocolSupport.Command.SADD, cb);
        final RedisPartitionAttributesBuilder partitionAttributesBuilder = partitionAttributesBuilderFunction
                    .apply(RedisProtocolSupport.Command.SADD);
        partitionAttributesBuilder.addKey(key);
        final Single<Long> result = partitionedRedisClient.request(partitionAttributesBuilder.build(), request, Long.class);
        return result;
    }

    @Override
    public Single<Long> sadd(@RedisProtocolSupport.Key final CharSequence key, final CharSequence member1,
                             final CharSequence member2, final CharSequence member3) {
        requireNonNull(key);
        requireNonNull(member1);
        requireNonNull(member2);
        requireNonNull(member3);
        final BufferAllocator allocator = partitionedRedisClient.getBufferAllocator();
        // Compute the number of request arguments, accounting for nullable ones
        int len = 5;
        final CompositeBuffer cb = newRequestCompositeBuffer(len, RedisProtocolSupport.Command.SADD, allocator);
        addRequestArgument(key, cb, allocator);
        addRequestArgument(member1, cb, allocator);
        addRequestArgument(member2, cb, allocator);
        addRequestArgument(member3, cb, allocator);
        final RedisRequest request = newRequest(RedisProtocolSupport.Command.SADD, cb);
        final RedisPartitionAttributesBuilder partitionAttributesBuilder = partitionAttributesBuilderFunction
                    .apply(RedisProtocolSupport.Command.SADD);
        partitionAttributesBuilder.addKey(key);
        final Single<Long> result = partitionedRedisClient.request(partitionAttributesBuilder.build(), request, Long.class);
        return result;
    }

    @Override
    public Single<Long> sadd(@RedisProtocolSupport.Key final CharSequence key, final Collection<? extends CharSequence> members) {
        requireNonNull(key);
        requireNonNull(members);
        final BufferAllocator allocator = partitionedRedisClient.getBufferAllocator();
        // Compute the number of request arguments, accounting for nullable ones
        int len = 2;
        len += members.size();
        final CompositeBuffer cb = newRequestCompositeBuffer(len, RedisProtocolSupport.Command.SADD, allocator);
        addRequestArgument(key, cb, allocator);
        addRequestCharSequenceArguments(members, null, cb, allocator);
        final RedisRequest request = newRequest(RedisProtocolSupport.Command.SADD, cb);
        final RedisPartitionAttributesBuilder partitionAttributesBuilder = partitionAttributesBuilderFunction
                    .apply(RedisProtocolSupport.Command.SADD);
        partitionAttributesBuilder.addKey(key);
        final Single<Long> result = partitionedRedisClient.request(partitionAttributesBuilder.build(), request, Long.class);
        return result;
    }

    @Override
    public Single<String> save() {
        final BufferAllocator allocator = partitionedRedisClient.getBufferAllocator();
        // Compute the number of request arguments, accounting for nullable ones
        int len = 1;
        final CompositeBuffer cb = newRequestCompositeBuffer(len, RedisProtocolSupport.Command.SAVE, allocator);
        final RedisRequest request = newRequest(RedisProtocolSupport.Command.SAVE, cb);
        final RedisPartitionAttributesBuilder partitionAttributesBuilder = partitionAttributesBuilderFunction
                    .apply(RedisProtocolSupport.Command.SAVE);
        final Single<String> result = partitionedRedisClient.request(partitionAttributesBuilder.build(), request, String.class);
        return result;
    }

    @Override
    public <T> Single<List<T>> scan(final long cursor) {
        final BufferAllocator allocator = partitionedRedisClient.getBufferAllocator();
        // Compute the number of request arguments, accounting for nullable ones
        int len = 2;
        final CompositeBuffer cb = newRequestCompositeBuffer(len, RedisProtocolSupport.Command.SCAN, allocator);
        addRequestArgument(cursor, cb, allocator);
        final RedisRequest request = newRequest(RedisProtocolSupport.Command.SCAN, cb);
        final RedisPartitionAttributesBuilder partitionAttributesBuilder = partitionAttributesBuilderFunction
                    .apply(RedisProtocolSupport.Command.SCAN);
        final Single<List<T>> result = (Single) partitionedRedisClient.request(partitionAttributesBuilder.build(), request,
                    RedisUtils.ListWithBuffersCoercedToCharSequences.class);
        return result;
    }

    @Override
    public <T> Single<List<T>> scan(final long cursor, @Nullable final CharSequence matchPattern, @Nullable final Long count) {
        final BufferAllocator allocator = partitionedRedisClient.getBufferAllocator();
        // Compute the number of request arguments, accounting for nullable ones
        int len = 2;
        if (matchPattern != null) {
            len += 2;
        }
        if (count != null) {
            len += 2;
        }
        final CompositeBuffer cb = newRequestCompositeBuffer(len, RedisProtocolSupport.Command.SCAN, allocator);
        addRequestArgument(cursor, cb, allocator);
        if (matchPattern != null) {
            addRequestArgument(RedisProtocolSupport.SubCommand.MATCH, cb, allocator);
            addRequestArgument(matchPattern, cb, allocator);
        }
        if (count != null) {
            addRequestArgument(RedisProtocolSupport.SubCommand.COUNT, cb, allocator);
            addRequestArgument(count, cb, allocator);
        }
        final RedisRequest request = newRequest(RedisProtocolSupport.Command.SCAN, cb);
        final RedisPartitionAttributesBuilder partitionAttributesBuilder = partitionAttributesBuilderFunction
                    .apply(RedisProtocolSupport.Command.SCAN);
        final Single<List<T>> result = (Single) partitionedRedisClient.request(partitionAttributesBuilder.build(), request,
                    RedisUtils.ListWithBuffersCoercedToCharSequences.class);
        return result;
    }

    @Override
    public Single<Long> scard(@RedisProtocolSupport.Key final CharSequence key) {
        requireNonNull(key);
        final BufferAllocator allocator = partitionedRedisClient.getBufferAllocator();
        // Compute the number of request arguments, accounting for nullable ones
        int len = 2;
        final CompositeBuffer cb = newRequestCompositeBuffer(len, RedisProtocolSupport.Command.SCARD, allocator);
        addRequestArgument(key, cb, allocator);
        final RedisRequest request = newRequest(RedisProtocolSupport.Command.SCARD, cb);
        final RedisPartitionAttributesBuilder partitionAttributesBuilder = partitionAttributesBuilderFunction
                    .apply(RedisProtocolSupport.Command.SCARD);
        partitionAttributesBuilder.addKey(key);
        final Single<Long> result = partitionedRedisClient.request(partitionAttributesBuilder.build(), request, Long.class);
        return result;
    }

    @Override
    public Single<String> scriptDebug(final RedisProtocolSupport.ScriptDebugMode mode) {
        requireNonNull(mode);
        final BufferAllocator allocator = partitionedRedisClient.getBufferAllocator();
        // Compute the number of request arguments, accounting for nullable ones
        int len = 3;
        final CompositeBuffer cb = newRequestCompositeBuffer(len, RedisProtocolSupport.Command.SCRIPT,
                    RedisProtocolSupport.SubCommand.DEBUG, allocator);
        addRequestArgument(mode, cb, allocator);
        final RedisRequest request = newRequest(RedisProtocolSupport.Command.SCRIPT, cb);
        final RedisPartitionAttributesBuilder partitionAttributesBuilder = partitionAttributesBuilderFunction
                    .apply(RedisProtocolSupport.Command.SCRIPT);
        final Single<String> result = partitionedRedisClient.request(partitionAttributesBuilder.build(), request, String.class);
        return result;
    }

    @Override
    public <T> Single<List<T>> scriptExists(final CharSequence sha1) {
        requireNonNull(sha1);
        final BufferAllocator allocator = partitionedRedisClient.getBufferAllocator();
        // Compute the number of request arguments, accounting for nullable ones
        int len = 3;
        final CompositeBuffer cb = newRequestCompositeBuffer(len, RedisProtocolSupport.Command.SCRIPT,
                    RedisProtocolSupport.SubCommand.EXISTS, allocator);
        addRequestArgument(sha1, cb, allocator);
        final RedisRequest request = newRequest(RedisProtocolSupport.Command.SCRIPT, cb);
        final RedisPartitionAttributesBuilder partitionAttributesBuilder = partitionAttributesBuilderFunction
                    .apply(RedisProtocolSupport.Command.SCRIPT);
        final Single<List<T>> result = (Single) partitionedRedisClient.request(partitionAttributesBuilder.build(), request,
                    RedisUtils.ListWithBuffersCoercedToCharSequences.class);
        return result;
    }

    @Override
    public <T> Single<List<T>> scriptExists(final CharSequence sha11, final CharSequence sha12) {
        requireNonNull(sha11);
        requireNonNull(sha12);
        final BufferAllocator allocator = partitionedRedisClient.getBufferAllocator();
        // Compute the number of request arguments, accounting for nullable ones
        int len = 4;
        final CompositeBuffer cb = newRequestCompositeBuffer(len, RedisProtocolSupport.Command.SCRIPT,
                    RedisProtocolSupport.SubCommand.EXISTS, allocator);
        addRequestArgument(sha11, cb, allocator);
        addRequestArgument(sha12, cb, allocator);
        final RedisRequest request = newRequest(RedisProtocolSupport.Command.SCRIPT, cb);
        final RedisPartitionAttributesBuilder partitionAttributesBuilder = partitionAttributesBuilderFunction
                    .apply(RedisProtocolSupport.Command.SCRIPT);
        final Single<List<T>> result = (Single) partitionedRedisClient.request(partitionAttributesBuilder.build(), request,
                    RedisUtils.ListWithBuffersCoercedToCharSequences.class);
        return result;
    }

    @Override
    public <T> Single<List<T>> scriptExists(final CharSequence sha11, final CharSequence sha12, final CharSequence sha13) {
        requireNonNull(sha11);
        requireNonNull(sha12);
        requireNonNull(sha13);
        final BufferAllocator allocator = partitionedRedisClient.getBufferAllocator();
        // Compute the number of request arguments, accounting for nullable ones
        int len = 5;
        final CompositeBuffer cb = newRequestCompositeBuffer(len, RedisProtocolSupport.Command.SCRIPT,
                    RedisProtocolSupport.SubCommand.EXISTS, allocator);
        addRequestArgument(sha11, cb, allocator);
        addRequestArgument(sha12, cb, allocator);
        addRequestArgument(sha13, cb, allocator);
        final RedisRequest request = newRequest(RedisProtocolSupport.Command.SCRIPT, cb);
        final RedisPartitionAttributesBuilder partitionAttributesBuilder = partitionAttributesBuilderFunction
                    .apply(RedisProtocolSupport.Command.SCRIPT);
        final Single<List<T>> result = (Single) partitionedRedisClient.request(partitionAttributesBuilder.build(), request,
                    RedisUtils.ListWithBuffersCoercedToCharSequences.class);
        return result;
    }

    @Override
    public <T> Single<List<T>> scriptExists(final Collection<? extends CharSequence> sha1s) {
        requireNonNull(sha1s);
        final BufferAllocator allocator = partitionedRedisClient.getBufferAllocator();
        // Compute the number of request arguments, accounting for nullable ones
        int len = 2;
        len += sha1s.size();
        final CompositeBuffer cb = newRequestCompositeBuffer(len, RedisProtocolSupport.Command.SCRIPT,
                    RedisProtocolSupport.SubCommand.EXISTS, allocator);
        addRequestCharSequenceArguments(sha1s, null, cb, allocator);
        final RedisRequest request = newRequest(RedisProtocolSupport.Command.SCRIPT, cb);
        final RedisPartitionAttributesBuilder partitionAttributesBuilder = partitionAttributesBuilderFunction
                    .apply(RedisProtocolSupport.Command.SCRIPT);
        final Single<List<T>> result = (Single) partitionedRedisClient.request(partitionAttributesBuilder.build(), request,
                    RedisUtils.ListWithBuffersCoercedToCharSequences.class);
        return result;
    }

    @Override
    public Single<String> scriptFlush() {
        final BufferAllocator allocator = partitionedRedisClient.getBufferAllocator();
        // Compute the number of request arguments, accounting for nullable ones
        int len = 2;
        final CompositeBuffer cb = newRequestCompositeBuffer(len, RedisProtocolSupport.Command.SCRIPT,
                    RedisProtocolSupport.SubCommand.FLUSH, allocator);
        final RedisRequest request = newRequest(RedisProtocolSupport.Command.SCRIPT, cb);
        final RedisPartitionAttributesBuilder partitionAttributesBuilder = partitionAttributesBuilderFunction
                    .apply(RedisProtocolSupport.Command.SCRIPT);
        final Single<String> result = partitionedRedisClient.request(partitionAttributesBuilder.build(), request, String.class);
        return result;
    }

    @Override
    public Single<String> scriptKill() {
        final BufferAllocator allocator = partitionedRedisClient.getBufferAllocator();
        // Compute the number of request arguments, accounting for nullable ones
        int len = 2;
        final CompositeBuffer cb = newRequestCompositeBuffer(len, RedisProtocolSupport.Command.SCRIPT,
                    RedisProtocolSupport.SubCommand.KILL, allocator);
        final RedisRequest request = newRequest(RedisProtocolSupport.Command.SCRIPT, cb);
        final RedisPartitionAttributesBuilder partitionAttributesBuilder = partitionAttributesBuilderFunction
                    .apply(RedisProtocolSupport.Command.SCRIPT);
        final Single<String> result = partitionedRedisClient.request(partitionAttributesBuilder.build(), request, String.class);
        return result;
    }

    @Override
    public Single<String> scriptLoad(final CharSequence script) {
        requireNonNull(script);
        final BufferAllocator allocator = partitionedRedisClient.getBufferAllocator();
        // Compute the number of request arguments, accounting for nullable ones
        int len = 3;
        final CompositeBuffer cb = newRequestCompositeBuffer(len, RedisProtocolSupport.Command.SCRIPT,
                    RedisProtocolSupport.SubCommand.LOAD, allocator);
        addRequestArgument(script, cb, allocator);
        final RedisRequest request = newRequest(RedisProtocolSupport.Command.SCRIPT, cb);
        final RedisPartitionAttributesBuilder partitionAttributesBuilder = partitionAttributesBuilderFunction
                    .apply(RedisProtocolSupport.Command.SCRIPT);
        final Single<String> result = partitionedRedisClient.request(partitionAttributesBuilder.build(), request, String.class);
        return result;
    }

    @Override
    public <T> Single<List<T>> sdiff(@RedisProtocolSupport.Key final CharSequence firstkey) {
        requireNonNull(firstkey);
        final BufferAllocator allocator = partitionedRedisClient.getBufferAllocator();
        // Compute the number of request arguments, accounting for nullable ones
        int len = 2;
        final CompositeBuffer cb = newRequestCompositeBuffer(len, RedisProtocolSupport.Command.SDIFF, allocator);
        addRequestArgument(firstkey, cb, allocator);
        final RedisRequest request = newRequest(RedisProtocolSupport.Command.SDIFF, cb);
        final RedisPartitionAttributesBuilder partitionAttributesBuilder = partitionAttributesBuilderFunction
                    .apply(RedisProtocolSupport.Command.SDIFF);
        partitionAttributesBuilder.addKey(firstkey);
        final Single<List<T>> result = (Single) partitionedRedisClient.request(partitionAttributesBuilder.build(), request,
                    RedisUtils.ListWithBuffersCoercedToCharSequences.class);
        return result;
    }

    @Override
    public <T> Single<List<T>> sdiff(@RedisProtocolSupport.Key final CharSequence firstkey,
                                     @Nullable @RedisProtocolSupport.Key final CharSequence otherkey) {
        requireNonNull(firstkey);
        final BufferAllocator allocator = partitionedRedisClient.getBufferAllocator();
        // Compute the number of request arguments, accounting for nullable ones
        int len = 2;
        if (otherkey != null) {
            len++;
        }
        final CompositeBuffer cb = newRequestCompositeBuffer(len, RedisProtocolSupport.Command.SDIFF, allocator);
        addRequestArgument(firstkey, cb, allocator);
        if (otherkey != null) {
            addRequestArgument(otherkey, cb, allocator);
        }
        final RedisRequest request = newRequest(RedisProtocolSupport.Command.SDIFF, cb);
        final RedisPartitionAttributesBuilder partitionAttributesBuilder = partitionAttributesBuilderFunction
                    .apply(RedisProtocolSupport.Command.SDIFF);
        partitionAttributesBuilder.addKey(firstkey);
        if (otherkey != null) {
            partitionAttributesBuilder.addKey(otherkey);
        }
        final Single<List<T>> result = (Single) partitionedRedisClient.request(partitionAttributesBuilder.build(), request,
                    RedisUtils.ListWithBuffersCoercedToCharSequences.class);
        return result;
    }

    @Override
    public <T> Single<List<T>> sdiff(@RedisProtocolSupport.Key final CharSequence firstkey,
                                     @Nullable @RedisProtocolSupport.Key final CharSequence otherkey1,
                                     @Nullable @RedisProtocolSupport.Key final CharSequence otherkey2) {
        requireNonNull(firstkey);
        final BufferAllocator allocator = partitionedRedisClient.getBufferAllocator();
        // Compute the number of request arguments, accounting for nullable ones
        int len = 2;
        if (otherkey1 != null) {
            len++;
        }
        if (otherkey2 != null) {
            len++;
        }
        final CompositeBuffer cb = newRequestCompositeBuffer(len, RedisProtocolSupport.Command.SDIFF, allocator);
        addRequestArgument(firstkey, cb, allocator);
        if (otherkey1 != null) {
            addRequestArgument(otherkey1, cb, allocator);
        }
        if (otherkey2 != null) {
            addRequestArgument(otherkey2, cb, allocator);
        }
        final RedisRequest request = newRequest(RedisProtocolSupport.Command.SDIFF, cb);
        final RedisPartitionAttributesBuilder partitionAttributesBuilder = partitionAttributesBuilderFunction
                    .apply(RedisProtocolSupport.Command.SDIFF);
        partitionAttributesBuilder.addKey(firstkey);
        if (otherkey1 != null) {
            partitionAttributesBuilder.addKey(otherkey1);
        }
        if (otherkey2 != null) {
            partitionAttributesBuilder.addKey(otherkey2);
        }
        final Single<List<T>> result = (Single) partitionedRedisClient.request(partitionAttributesBuilder.build(), request,
                    RedisUtils.ListWithBuffersCoercedToCharSequences.class);
        return result;
    }

    @Override
    public <T> Single<List<T>> sdiff(@RedisProtocolSupport.Key final CharSequence firstkey,
                                     @Nullable @RedisProtocolSupport.Key final CharSequence otherkey1,
                                     @Nullable @RedisProtocolSupport.Key final CharSequence otherkey2,
                                     @Nullable @RedisProtocolSupport.Key final CharSequence otherkey3) {
        requireNonNull(firstkey);
        final BufferAllocator allocator = partitionedRedisClient.getBufferAllocator();
        // Compute the number of request arguments, accounting for nullable ones
        int len = 2;
        if (otherkey1 != null) {
            len++;
        }
        if (otherkey2 != null) {
            len++;
        }
        if (otherkey3 != null) {
            len++;
        }
        final CompositeBuffer cb = newRequestCompositeBuffer(len, RedisProtocolSupport.Command.SDIFF, allocator);
        addRequestArgument(firstkey, cb, allocator);
        if (otherkey1 != null) {
            addRequestArgument(otherkey1, cb, allocator);
        }
        if (otherkey2 != null) {
            addRequestArgument(otherkey2, cb, allocator);
        }
        if (otherkey3 != null) {
            addRequestArgument(otherkey3, cb, allocator);
        }
        final RedisRequest request = newRequest(RedisProtocolSupport.Command.SDIFF, cb);
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
        final Single<List<T>> result = (Single) partitionedRedisClient.request(partitionAttributesBuilder.build(), request,
                    RedisUtils.ListWithBuffersCoercedToCharSequences.class);
        return result;
    }

    @Override
    public <T> Single<List<T>> sdiff(@RedisProtocolSupport.Key final CharSequence firstkey,
                                     @RedisProtocolSupport.Key final Collection<? extends CharSequence> otherkeys) {
        requireNonNull(firstkey);
        requireNonNull(otherkeys);
        final BufferAllocator allocator = partitionedRedisClient.getBufferAllocator();
        // Compute the number of request arguments, accounting for nullable ones
        int len = 2;
        len += otherkeys.size();
        final CompositeBuffer cb = newRequestCompositeBuffer(len, RedisProtocolSupport.Command.SDIFF, allocator);
        addRequestArgument(firstkey, cb, allocator);
        addRequestCharSequenceArguments(otherkeys, null, cb, allocator);
        final RedisRequest request = newRequest(RedisProtocolSupport.Command.SDIFF, cb);
        final RedisPartitionAttributesBuilder partitionAttributesBuilder = partitionAttributesBuilderFunction
                    .apply(RedisProtocolSupport.Command.SDIFF);
        partitionAttributesBuilder.addKey(firstkey);
        addCharSequenceKeysToAttributeBuilder(otherkeys, partitionAttributesBuilder);
        final Single<List<T>> result = (Single) partitionedRedisClient.request(partitionAttributesBuilder.build(), request,
                    RedisUtils.ListWithBuffersCoercedToCharSequences.class);
        return result;
    }

    @Override
    public Single<Long> sdiffstore(@RedisProtocolSupport.Key final CharSequence destination,
                                   @RedisProtocolSupport.Key final CharSequence firstkey) {
        requireNonNull(destination);
        requireNonNull(firstkey);
        final BufferAllocator allocator = partitionedRedisClient.getBufferAllocator();
        // Compute the number of request arguments, accounting for nullable ones
        int len = 3;
        final CompositeBuffer cb = newRequestCompositeBuffer(len, RedisProtocolSupport.Command.SDIFFSTORE, allocator);
        addRequestArgument(destination, cb, allocator);
        addRequestArgument(firstkey, cb, allocator);
        final RedisRequest request = newRequest(RedisProtocolSupport.Command.SDIFFSTORE, cb);
        final RedisPartitionAttributesBuilder partitionAttributesBuilder = partitionAttributesBuilderFunction
                    .apply(RedisProtocolSupport.Command.SDIFFSTORE);
        partitionAttributesBuilder.addKey(destination);
        partitionAttributesBuilder.addKey(firstkey);
        final Single<Long> result = partitionedRedisClient.request(partitionAttributesBuilder.build(), request, Long.class);
        return result;
    }

    @Override
    public Single<Long> sdiffstore(@RedisProtocolSupport.Key final CharSequence destination,
                                   @RedisProtocolSupport.Key final CharSequence firstkey,
                                   @Nullable @RedisProtocolSupport.Key final CharSequence otherkey) {
        requireNonNull(destination);
        requireNonNull(firstkey);
        final BufferAllocator allocator = partitionedRedisClient.getBufferAllocator();
        // Compute the number of request arguments, accounting for nullable ones
        int len = 3;
        if (otherkey != null) {
            len++;
        }
        final CompositeBuffer cb = newRequestCompositeBuffer(len, RedisProtocolSupport.Command.SDIFFSTORE, allocator);
        addRequestArgument(destination, cb, allocator);
        addRequestArgument(firstkey, cb, allocator);
        if (otherkey != null) {
            addRequestArgument(otherkey, cb, allocator);
        }
        final RedisRequest request = newRequest(RedisProtocolSupport.Command.SDIFFSTORE, cb);
        final RedisPartitionAttributesBuilder partitionAttributesBuilder = partitionAttributesBuilderFunction
                    .apply(RedisProtocolSupport.Command.SDIFFSTORE);
        partitionAttributesBuilder.addKey(destination);
        partitionAttributesBuilder.addKey(firstkey);
        if (otherkey != null) {
            partitionAttributesBuilder.addKey(otherkey);
        }
        final Single<Long> result = partitionedRedisClient.request(partitionAttributesBuilder.build(), request, Long.class);
        return result;
    }

    @Override
    public Single<Long> sdiffstore(@RedisProtocolSupport.Key final CharSequence destination,
                                   @RedisProtocolSupport.Key final CharSequence firstkey,
                                   @Nullable @RedisProtocolSupport.Key final CharSequence otherkey1,
                                   @Nullable @RedisProtocolSupport.Key final CharSequence otherkey2) {
        requireNonNull(destination);
        requireNonNull(firstkey);
        final BufferAllocator allocator = partitionedRedisClient.getBufferAllocator();
        // Compute the number of request arguments, accounting for nullable ones
        int len = 3;
        if (otherkey1 != null) {
            len++;
        }
        if (otherkey2 != null) {
            len++;
        }
        final CompositeBuffer cb = newRequestCompositeBuffer(len, RedisProtocolSupport.Command.SDIFFSTORE, allocator);
        addRequestArgument(destination, cb, allocator);
        addRequestArgument(firstkey, cb, allocator);
        if (otherkey1 != null) {
            addRequestArgument(otherkey1, cb, allocator);
        }
        if (otherkey2 != null) {
            addRequestArgument(otherkey2, cb, allocator);
        }
        final RedisRequest request = newRequest(RedisProtocolSupport.Command.SDIFFSTORE, cb);
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
        final Single<Long> result = partitionedRedisClient.request(partitionAttributesBuilder.build(), request, Long.class);
        return result;
    }

    @Override
    public Single<Long> sdiffstore(@RedisProtocolSupport.Key final CharSequence destination,
                                   @RedisProtocolSupport.Key final CharSequence firstkey,
                                   @Nullable @RedisProtocolSupport.Key final CharSequence otherkey1,
                                   @Nullable @RedisProtocolSupport.Key final CharSequence otherkey2,
                                   @Nullable @RedisProtocolSupport.Key final CharSequence otherkey3) {
        requireNonNull(destination);
        requireNonNull(firstkey);
        final BufferAllocator allocator = partitionedRedisClient.getBufferAllocator();
        // Compute the number of request arguments, accounting for nullable ones
        int len = 3;
        if (otherkey1 != null) {
            len++;
        }
        if (otherkey2 != null) {
            len++;
        }
        if (otherkey3 != null) {
            len++;
        }
        final CompositeBuffer cb = newRequestCompositeBuffer(len, RedisProtocolSupport.Command.SDIFFSTORE, allocator);
        addRequestArgument(destination, cb, allocator);
        addRequestArgument(firstkey, cb, allocator);
        if (otherkey1 != null) {
            addRequestArgument(otherkey1, cb, allocator);
        }
        if (otherkey2 != null) {
            addRequestArgument(otherkey2, cb, allocator);
        }
        if (otherkey3 != null) {
            addRequestArgument(otherkey3, cb, allocator);
        }
        final RedisRequest request = newRequest(RedisProtocolSupport.Command.SDIFFSTORE, cb);
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
        final Single<Long> result = partitionedRedisClient.request(partitionAttributesBuilder.build(), request, Long.class);
        return result;
    }

    @Override
    public Single<Long> sdiffstore(@RedisProtocolSupport.Key final CharSequence destination,
                                   @RedisProtocolSupport.Key final CharSequence firstkey,
                                   @RedisProtocolSupport.Key final Collection<? extends CharSequence> otherkeys) {
        requireNonNull(destination);
        requireNonNull(firstkey);
        requireNonNull(otherkeys);
        final BufferAllocator allocator = partitionedRedisClient.getBufferAllocator();
        // Compute the number of request arguments, accounting for nullable ones
        int len = 3;
        len += otherkeys.size();
        final CompositeBuffer cb = newRequestCompositeBuffer(len, RedisProtocolSupport.Command.SDIFFSTORE, allocator);
        addRequestArgument(destination, cb, allocator);
        addRequestArgument(firstkey, cb, allocator);
        addRequestCharSequenceArguments(otherkeys, null, cb, allocator);
        final RedisRequest request = newRequest(RedisProtocolSupport.Command.SDIFFSTORE, cb);
        final RedisPartitionAttributesBuilder partitionAttributesBuilder = partitionAttributesBuilderFunction
                    .apply(RedisProtocolSupport.Command.SDIFFSTORE);
        partitionAttributesBuilder.addKey(destination);
        partitionAttributesBuilder.addKey(firstkey);
        addCharSequenceKeysToAttributeBuilder(otherkeys, partitionAttributesBuilder);
        final Single<Long> result = partitionedRedisClient.request(partitionAttributesBuilder.build(), request, Long.class);
        return result;
    }

    @Override
    public Single<String> select(final long index) {
        final BufferAllocator allocator = partitionedRedisClient.getBufferAllocator();
        // Compute the number of request arguments, accounting for nullable ones
        int len = 2;
        final CompositeBuffer cb = newRequestCompositeBuffer(len, RedisProtocolSupport.Command.SELECT, allocator);
        addRequestArgument(index, cb, allocator);
        final RedisRequest request = newRequest(RedisProtocolSupport.Command.SELECT, cb);
        final RedisPartitionAttributesBuilder partitionAttributesBuilder = partitionAttributesBuilderFunction
                    .apply(RedisProtocolSupport.Command.SELECT);
        final Single<String> result = partitionedRedisClient.request(partitionAttributesBuilder.build(), request, String.class);
        return result;
    }

    @Override
    public Single<String> set(@RedisProtocolSupport.Key final CharSequence key, final CharSequence value) {
        requireNonNull(key);
        requireNonNull(value);
        final BufferAllocator allocator = partitionedRedisClient.getBufferAllocator();
        // Compute the number of request arguments, accounting for nullable ones
        int len = 3;
        final CompositeBuffer cb = newRequestCompositeBuffer(len, RedisProtocolSupport.Command.SET, allocator);
        addRequestArgument(key, cb, allocator);
        addRequestArgument(value, cb, allocator);
        final RedisRequest request = newRequest(RedisProtocolSupport.Command.SET, cb);
        final RedisPartitionAttributesBuilder partitionAttributesBuilder = partitionAttributesBuilderFunction
                    .apply(RedisProtocolSupport.Command.SET);
        partitionAttributesBuilder.addKey(key);
        final Single<String> result = partitionedRedisClient.request(partitionAttributesBuilder.build(), request, String.class);
        return result;
    }

    @Override
    public Single<String> set(@RedisProtocolSupport.Key final CharSequence key, final CharSequence value,
                              @Nullable final RedisProtocolSupport.ExpireDuration expireDuration,
                              @Nullable final RedisProtocolSupport.SetCondition condition) {
        requireNonNull(key);
        requireNonNull(value);
        final BufferAllocator allocator = partitionedRedisClient.getBufferAllocator();
        // Compute the number of request arguments, accounting for nullable ones
        int len = 3;
        if (expireDuration != null) {
            len += RedisProtocolSupport.ExpireDuration.SIZE;
        }
        if (condition != null) {
            len++;
        }
        final CompositeBuffer cb = newRequestCompositeBuffer(len, RedisProtocolSupport.Command.SET, allocator);
        addRequestArgument(key, cb, allocator);
        addRequestArgument(value, cb, allocator);
        if (expireDuration != null) {
            expireDuration.writeTo(cb, allocator);
        }
        if (condition != null) {
            addRequestArgument(condition, cb, allocator);
        }
        final RedisRequest request = newRequest(RedisProtocolSupport.Command.SET, cb);
        final RedisPartitionAttributesBuilder partitionAttributesBuilder = partitionAttributesBuilderFunction
                    .apply(RedisProtocolSupport.Command.SET);
        partitionAttributesBuilder.addKey(key);
        final Single<String> result = partitionedRedisClient.request(partitionAttributesBuilder.build(), request, String.class);
        return result;
    }

    @Override
    public Single<Long> setbit(@RedisProtocolSupport.Key final CharSequence key, final long offset, final CharSequence value) {
        requireNonNull(key);
        requireNonNull(value);
        final BufferAllocator allocator = partitionedRedisClient.getBufferAllocator();
        // Compute the number of request arguments, accounting for nullable ones
        int len = 4;
        final CompositeBuffer cb = newRequestCompositeBuffer(len, RedisProtocolSupport.Command.SETBIT, allocator);
        addRequestArgument(key, cb, allocator);
        addRequestArgument(offset, cb, allocator);
        addRequestArgument(value, cb, allocator);
        final RedisRequest request = newRequest(RedisProtocolSupport.Command.SETBIT, cb);
        final RedisPartitionAttributesBuilder partitionAttributesBuilder = partitionAttributesBuilderFunction
                    .apply(RedisProtocolSupport.Command.SETBIT);
        partitionAttributesBuilder.addKey(key);
        final Single<Long> result = partitionedRedisClient.request(partitionAttributesBuilder.build(), request, Long.class);
        return result;
    }

    @Override
    public Single<String> setex(@RedisProtocolSupport.Key final CharSequence key, final long seconds, final CharSequence value) {
        requireNonNull(key);
        requireNonNull(value);
        final BufferAllocator allocator = partitionedRedisClient.getBufferAllocator();
        // Compute the number of request arguments, accounting for nullable ones
        int len = 4;
        final CompositeBuffer cb = newRequestCompositeBuffer(len, RedisProtocolSupport.Command.SETEX, allocator);
        addRequestArgument(key, cb, allocator);
        addRequestArgument(seconds, cb, allocator);
        addRequestArgument(value, cb, allocator);
        final RedisRequest request = newRequest(RedisProtocolSupport.Command.SETEX, cb);
        final RedisPartitionAttributesBuilder partitionAttributesBuilder = partitionAttributesBuilderFunction
                    .apply(RedisProtocolSupport.Command.SETEX);
        partitionAttributesBuilder.addKey(key);
        final Single<String> result = partitionedRedisClient.request(partitionAttributesBuilder.build(), request, String.class);
        return result;
    }

    @Override
    public Single<Long> setnx(@RedisProtocolSupport.Key final CharSequence key, final CharSequence value) {
        requireNonNull(key);
        requireNonNull(value);
        final BufferAllocator allocator = partitionedRedisClient.getBufferAllocator();
        // Compute the number of request arguments, accounting for nullable ones
        int len = 3;
        final CompositeBuffer cb = newRequestCompositeBuffer(len, RedisProtocolSupport.Command.SETNX, allocator);
        addRequestArgument(key, cb, allocator);
        addRequestArgument(value, cb, allocator);
        final RedisRequest request = newRequest(RedisProtocolSupport.Command.SETNX, cb);
        final RedisPartitionAttributesBuilder partitionAttributesBuilder = partitionAttributesBuilderFunction
                    .apply(RedisProtocolSupport.Command.SETNX);
        partitionAttributesBuilder.addKey(key);
        final Single<Long> result = partitionedRedisClient.request(partitionAttributesBuilder.build(), request, Long.class);
        return result;
    }

    @Override
    public Single<Long> setrange(@RedisProtocolSupport.Key final CharSequence key, final long offset, final CharSequence value) {
        requireNonNull(key);
        requireNonNull(value);
        final BufferAllocator allocator = partitionedRedisClient.getBufferAllocator();
        // Compute the number of request arguments, accounting for nullable ones
        int len = 4;
        final CompositeBuffer cb = newRequestCompositeBuffer(len, RedisProtocolSupport.Command.SETRANGE, allocator);
        addRequestArgument(key, cb, allocator);
        addRequestArgument(offset, cb, allocator);
        addRequestArgument(value, cb, allocator);
        final RedisRequest request = newRequest(RedisProtocolSupport.Command.SETRANGE, cb);
        final RedisPartitionAttributesBuilder partitionAttributesBuilder = partitionAttributesBuilderFunction
                    .apply(RedisProtocolSupport.Command.SETRANGE);
        partitionAttributesBuilder.addKey(key);
        final Single<Long> result = partitionedRedisClient.request(partitionAttributesBuilder.build(), request, Long.class);
        return result;
    }

    @Override
    public Single<String> shutdown() {
        final BufferAllocator allocator = partitionedRedisClient.getBufferAllocator();
        // Compute the number of request arguments, accounting for nullable ones
        int len = 1;
        final CompositeBuffer cb = newRequestCompositeBuffer(len, RedisProtocolSupport.Command.SHUTDOWN, allocator);
        final RedisRequest request = newRequest(RedisProtocolSupport.Command.SHUTDOWN, cb);
        final RedisPartitionAttributesBuilder partitionAttributesBuilder = partitionAttributesBuilderFunction
                    .apply(RedisProtocolSupport.Command.SHUTDOWN);
        final Single<String> result = partitionedRedisClient.request(partitionAttributesBuilder.build(), request, String.class);
        return result;
    }

    @Override
    public Single<String> shutdown(@Nullable final RedisProtocolSupport.ShutdownSaveMode saveMode) {
        final BufferAllocator allocator = partitionedRedisClient.getBufferAllocator();
        // Compute the number of request arguments, accounting for nullable ones
        int len = 1;
        if (saveMode != null) {
            len++;
        }
        final CompositeBuffer cb = newRequestCompositeBuffer(len, RedisProtocolSupport.Command.SHUTDOWN, allocator);
        if (saveMode != null) {
            addRequestArgument(saveMode, cb, allocator);
        }
        final RedisRequest request = newRequest(RedisProtocolSupport.Command.SHUTDOWN, cb);
        final RedisPartitionAttributesBuilder partitionAttributesBuilder = partitionAttributesBuilderFunction
                    .apply(RedisProtocolSupport.Command.SHUTDOWN);
        final Single<String> result = partitionedRedisClient.request(partitionAttributesBuilder.build(), request, String.class);
        return result;
    }

    @Override
    public <T> Single<List<T>> sinter(@RedisProtocolSupport.Key final CharSequence key) {
        requireNonNull(key);
        final BufferAllocator allocator = partitionedRedisClient.getBufferAllocator();
        // Compute the number of request arguments, accounting for nullable ones
        int len = 2;
        final CompositeBuffer cb = newRequestCompositeBuffer(len, RedisProtocolSupport.Command.SINTER, allocator);
        addRequestArgument(key, cb, allocator);
        final RedisRequest request = newRequest(RedisProtocolSupport.Command.SINTER, cb);
        final RedisPartitionAttributesBuilder partitionAttributesBuilder = partitionAttributesBuilderFunction
                    .apply(RedisProtocolSupport.Command.SINTER);
        partitionAttributesBuilder.addKey(key);
        final Single<List<T>> result = (Single) partitionedRedisClient.request(partitionAttributesBuilder.build(), request,
                    RedisUtils.ListWithBuffersCoercedToCharSequences.class);
        return result;
    }

    @Override
    public <T> Single<List<T>> sinter(@RedisProtocolSupport.Key final CharSequence key1,
                                      @RedisProtocolSupport.Key final CharSequence key2) {
        requireNonNull(key1);
        requireNonNull(key2);
        final BufferAllocator allocator = partitionedRedisClient.getBufferAllocator();
        // Compute the number of request arguments, accounting for nullable ones
        int len = 3;
        final CompositeBuffer cb = newRequestCompositeBuffer(len, RedisProtocolSupport.Command.SINTER, allocator);
        addRequestArgument(key1, cb, allocator);
        addRequestArgument(key2, cb, allocator);
        final RedisRequest request = newRequest(RedisProtocolSupport.Command.SINTER, cb);
        final RedisPartitionAttributesBuilder partitionAttributesBuilder = partitionAttributesBuilderFunction
                    .apply(RedisProtocolSupport.Command.SINTER);
        partitionAttributesBuilder.addKey(key1);
        partitionAttributesBuilder.addKey(key2);
        final Single<List<T>> result = (Single) partitionedRedisClient.request(partitionAttributesBuilder.build(), request,
                    RedisUtils.ListWithBuffersCoercedToCharSequences.class);
        return result;
    }

    @Override
    public <T> Single<List<T>> sinter(@RedisProtocolSupport.Key final CharSequence key1,
                                      @RedisProtocolSupport.Key final CharSequence key2,
                                      @RedisProtocolSupport.Key final CharSequence key3) {
        requireNonNull(key1);
        requireNonNull(key2);
        requireNonNull(key3);
        final BufferAllocator allocator = partitionedRedisClient.getBufferAllocator();
        // Compute the number of request arguments, accounting for nullable ones
        int len = 4;
        final CompositeBuffer cb = newRequestCompositeBuffer(len, RedisProtocolSupport.Command.SINTER, allocator);
        addRequestArgument(key1, cb, allocator);
        addRequestArgument(key2, cb, allocator);
        addRequestArgument(key3, cb, allocator);
        final RedisRequest request = newRequest(RedisProtocolSupport.Command.SINTER, cb);
        final RedisPartitionAttributesBuilder partitionAttributesBuilder = partitionAttributesBuilderFunction
                    .apply(RedisProtocolSupport.Command.SINTER);
        partitionAttributesBuilder.addKey(key1);
        partitionAttributesBuilder.addKey(key2);
        partitionAttributesBuilder.addKey(key3);
        final Single<List<T>> result = (Single) partitionedRedisClient.request(partitionAttributesBuilder.build(), request,
                    RedisUtils.ListWithBuffersCoercedToCharSequences.class);
        return result;
    }

    @Override
    public <T> Single<List<T>> sinter(@RedisProtocolSupport.Key final Collection<? extends CharSequence> keys) {
        requireNonNull(keys);
        final BufferAllocator allocator = partitionedRedisClient.getBufferAllocator();
        // Compute the number of request arguments, accounting for nullable ones
        int len = 1;
        len += keys.size();
        final CompositeBuffer cb = newRequestCompositeBuffer(len, RedisProtocolSupport.Command.SINTER, allocator);
        addRequestCharSequenceArguments(keys, null, cb, allocator);
        final RedisRequest request = newRequest(RedisProtocolSupport.Command.SINTER, cb);
        final RedisPartitionAttributesBuilder partitionAttributesBuilder = partitionAttributesBuilderFunction
                    .apply(RedisProtocolSupport.Command.SINTER);
        addCharSequenceKeysToAttributeBuilder(keys, partitionAttributesBuilder);
        final Single<List<T>> result = (Single) partitionedRedisClient.request(partitionAttributesBuilder.build(), request,
                    RedisUtils.ListWithBuffersCoercedToCharSequences.class);
        return result;
    }

    @Override
    public Single<Long> sinterstore(@RedisProtocolSupport.Key final CharSequence destination,
                                    @RedisProtocolSupport.Key final CharSequence key) {
        requireNonNull(destination);
        requireNonNull(key);
        final BufferAllocator allocator = partitionedRedisClient.getBufferAllocator();
        // Compute the number of request arguments, accounting for nullable ones
        int len = 3;
        final CompositeBuffer cb = newRequestCompositeBuffer(len, RedisProtocolSupport.Command.SINTERSTORE, allocator);
        addRequestArgument(destination, cb, allocator);
        addRequestArgument(key, cb, allocator);
        final RedisRequest request = newRequest(RedisProtocolSupport.Command.SINTERSTORE, cb);
        final RedisPartitionAttributesBuilder partitionAttributesBuilder = partitionAttributesBuilderFunction
                    .apply(RedisProtocolSupport.Command.SINTERSTORE);
        partitionAttributesBuilder.addKey(destination);
        partitionAttributesBuilder.addKey(key);
        final Single<Long> result = partitionedRedisClient.request(partitionAttributesBuilder.build(), request, Long.class);
        return result;
    }

    @Override
    public Single<Long> sinterstore(@RedisProtocolSupport.Key final CharSequence destination,
                                    @RedisProtocolSupport.Key final CharSequence key1,
                                    @RedisProtocolSupport.Key final CharSequence key2) {
        requireNonNull(destination);
        requireNonNull(key1);
        requireNonNull(key2);
        final BufferAllocator allocator = partitionedRedisClient.getBufferAllocator();
        // Compute the number of request arguments, accounting for nullable ones
        int len = 4;
        final CompositeBuffer cb = newRequestCompositeBuffer(len, RedisProtocolSupport.Command.SINTERSTORE, allocator);
        addRequestArgument(destination, cb, allocator);
        addRequestArgument(key1, cb, allocator);
        addRequestArgument(key2, cb, allocator);
        final RedisRequest request = newRequest(RedisProtocolSupport.Command.SINTERSTORE, cb);
        final RedisPartitionAttributesBuilder partitionAttributesBuilder = partitionAttributesBuilderFunction
                    .apply(RedisProtocolSupport.Command.SINTERSTORE);
        partitionAttributesBuilder.addKey(destination);
        partitionAttributesBuilder.addKey(key1);
        partitionAttributesBuilder.addKey(key2);
        final Single<Long> result = partitionedRedisClient.request(partitionAttributesBuilder.build(), request, Long.class);
        return result;
    }

    @Override
    public Single<Long> sinterstore(@RedisProtocolSupport.Key final CharSequence destination,
                                    @RedisProtocolSupport.Key final CharSequence key1,
                                    @RedisProtocolSupport.Key final CharSequence key2,
                                    @RedisProtocolSupport.Key final CharSequence key3) {
        requireNonNull(destination);
        requireNonNull(key1);
        requireNonNull(key2);
        requireNonNull(key3);
        final BufferAllocator allocator = partitionedRedisClient.getBufferAllocator();
        // Compute the number of request arguments, accounting for nullable ones
        int len = 5;
        final CompositeBuffer cb = newRequestCompositeBuffer(len, RedisProtocolSupport.Command.SINTERSTORE, allocator);
        addRequestArgument(destination, cb, allocator);
        addRequestArgument(key1, cb, allocator);
        addRequestArgument(key2, cb, allocator);
        addRequestArgument(key3, cb, allocator);
        final RedisRequest request = newRequest(RedisProtocolSupport.Command.SINTERSTORE, cb);
        final RedisPartitionAttributesBuilder partitionAttributesBuilder = partitionAttributesBuilderFunction
                    .apply(RedisProtocolSupport.Command.SINTERSTORE);
        partitionAttributesBuilder.addKey(destination);
        partitionAttributesBuilder.addKey(key1);
        partitionAttributesBuilder.addKey(key2);
        partitionAttributesBuilder.addKey(key3);
        final Single<Long> result = partitionedRedisClient.request(partitionAttributesBuilder.build(), request, Long.class);
        return result;
    }

    @Override
    public Single<Long> sinterstore(@RedisProtocolSupport.Key final CharSequence destination,
                                    @RedisProtocolSupport.Key final Collection<? extends CharSequence> keys) {
        requireNonNull(destination);
        requireNonNull(keys);
        final BufferAllocator allocator = partitionedRedisClient.getBufferAllocator();
        // Compute the number of request arguments, accounting for nullable ones
        int len = 2;
        len += keys.size();
        final CompositeBuffer cb = newRequestCompositeBuffer(len, RedisProtocolSupport.Command.SINTERSTORE, allocator);
        addRequestArgument(destination, cb, allocator);
        addRequestCharSequenceArguments(keys, null, cb, allocator);
        final RedisRequest request = newRequest(RedisProtocolSupport.Command.SINTERSTORE, cb);
        final RedisPartitionAttributesBuilder partitionAttributesBuilder = partitionAttributesBuilderFunction
                    .apply(RedisProtocolSupport.Command.SINTERSTORE);
        partitionAttributesBuilder.addKey(destination);
        addCharSequenceKeysToAttributeBuilder(keys, partitionAttributesBuilder);
        final Single<Long> result = partitionedRedisClient.request(partitionAttributesBuilder.build(), request, Long.class);
        return result;
    }

    @Override
    public Single<Long> sismember(@RedisProtocolSupport.Key final CharSequence key, final CharSequence member) {
        requireNonNull(key);
        requireNonNull(member);
        final BufferAllocator allocator = partitionedRedisClient.getBufferAllocator();
        // Compute the number of request arguments, accounting for nullable ones
        int len = 3;
        final CompositeBuffer cb = newRequestCompositeBuffer(len, RedisProtocolSupport.Command.SISMEMBER, allocator);
        addRequestArgument(key, cb, allocator);
        addRequestArgument(member, cb, allocator);
        final RedisRequest request = newRequest(RedisProtocolSupport.Command.SISMEMBER, cb);
        final RedisPartitionAttributesBuilder partitionAttributesBuilder = partitionAttributesBuilderFunction
                    .apply(RedisProtocolSupport.Command.SISMEMBER);
        partitionAttributesBuilder.addKey(key);
        final Single<Long> result = partitionedRedisClient.request(partitionAttributesBuilder.build(), request, Long.class);
        return result;
    }

    @Override
    public Single<String> slaveof(final CharSequence host, final CharSequence port) {
        requireNonNull(host);
        requireNonNull(port);
        final BufferAllocator allocator = partitionedRedisClient.getBufferAllocator();
        // Compute the number of request arguments, accounting for nullable ones
        int len = 3;
        final CompositeBuffer cb = newRequestCompositeBuffer(len, RedisProtocolSupport.Command.SLAVEOF, allocator);
        addRequestArgument(host, cb, allocator);
        addRequestArgument(port, cb, allocator);
        final RedisRequest request = newRequest(RedisProtocolSupport.Command.SLAVEOF, cb);
        final RedisPartitionAttributesBuilder partitionAttributesBuilder = partitionAttributesBuilderFunction
                    .apply(RedisProtocolSupport.Command.SLAVEOF);
        final Single<String> result = partitionedRedisClient.request(partitionAttributesBuilder.build(), request, String.class);
        return result;
    }

    @Override
    public <T> Single<List<T>> slowlog(final CharSequence subcommand) {
        requireNonNull(subcommand);
        final BufferAllocator allocator = partitionedRedisClient.getBufferAllocator();
        // Compute the number of request arguments, accounting for nullable ones
        int len = 2;
        final CompositeBuffer cb = newRequestCompositeBuffer(len, RedisProtocolSupport.Command.SLOWLOG, allocator);
        addRequestArgument(subcommand, cb, allocator);
        final RedisRequest request = newRequest(RedisProtocolSupport.Command.SLOWLOG, cb);
        final RedisPartitionAttributesBuilder partitionAttributesBuilder = partitionAttributesBuilderFunction
                    .apply(RedisProtocolSupport.Command.SLOWLOG);
        final Single<List<T>> result = (Single) partitionedRedisClient.request(partitionAttributesBuilder.build(), request,
                    RedisUtils.ListWithBuffersCoercedToCharSequences.class);
        return result;
    }

    @Override
    public <T> Single<List<T>> slowlog(final CharSequence subcommand, @Nullable final CharSequence argument) {
        requireNonNull(subcommand);
        final BufferAllocator allocator = partitionedRedisClient.getBufferAllocator();
        // Compute the number of request arguments, accounting for nullable ones
        int len = 2;
        if (argument != null) {
            len++;
        }
        final CompositeBuffer cb = newRequestCompositeBuffer(len, RedisProtocolSupport.Command.SLOWLOG, allocator);
        addRequestArgument(subcommand, cb, allocator);
        if (argument != null) {
            addRequestArgument(argument, cb, allocator);
        }
        final RedisRequest request = newRequest(RedisProtocolSupport.Command.SLOWLOG, cb);
        final RedisPartitionAttributesBuilder partitionAttributesBuilder = partitionAttributesBuilderFunction
                    .apply(RedisProtocolSupport.Command.SLOWLOG);
        final Single<List<T>> result = (Single) partitionedRedisClient.request(partitionAttributesBuilder.build(), request,
                    RedisUtils.ListWithBuffersCoercedToCharSequences.class);
        return result;
    }

    @Override
    public <T> Single<List<T>> smembers(@RedisProtocolSupport.Key final CharSequence key) {
        requireNonNull(key);
        final BufferAllocator allocator = partitionedRedisClient.getBufferAllocator();
        // Compute the number of request arguments, accounting for nullable ones
        int len = 2;
        final CompositeBuffer cb = newRequestCompositeBuffer(len, RedisProtocolSupport.Command.SMEMBERS, allocator);
        addRequestArgument(key, cb, allocator);
        final RedisRequest request = newRequest(RedisProtocolSupport.Command.SMEMBERS, cb);
        final RedisPartitionAttributesBuilder partitionAttributesBuilder = partitionAttributesBuilderFunction
                    .apply(RedisProtocolSupport.Command.SMEMBERS);
        partitionAttributesBuilder.addKey(key);
        final Single<List<T>> result = (Single) partitionedRedisClient.request(partitionAttributesBuilder.build(), request,
                    RedisUtils.ListWithBuffersCoercedToCharSequences.class);
        return result;
    }

    @Override
    public Single<Long> smove(@RedisProtocolSupport.Key final CharSequence source,
                              @RedisProtocolSupport.Key final CharSequence destination, final CharSequence member) {
        requireNonNull(source);
        requireNonNull(destination);
        requireNonNull(member);
        final BufferAllocator allocator = partitionedRedisClient.getBufferAllocator();
        // Compute the number of request arguments, accounting for nullable ones
        int len = 4;
        final CompositeBuffer cb = newRequestCompositeBuffer(len, RedisProtocolSupport.Command.SMOVE, allocator);
        addRequestArgument(source, cb, allocator);
        addRequestArgument(destination, cb, allocator);
        addRequestArgument(member, cb, allocator);
        final RedisRequest request = newRequest(RedisProtocolSupport.Command.SMOVE, cb);
        final RedisPartitionAttributesBuilder partitionAttributesBuilder = partitionAttributesBuilderFunction
                    .apply(RedisProtocolSupport.Command.SMOVE);
        partitionAttributesBuilder.addKey(source);
        partitionAttributesBuilder.addKey(destination);
        final Single<Long> result = partitionedRedisClient.request(partitionAttributesBuilder.build(), request, Long.class);
        return result;
    }

    @Override
    public <T> Single<List<T>> sort(@RedisProtocolSupport.Key final CharSequence key) {
        requireNonNull(key);
        final BufferAllocator allocator = partitionedRedisClient.getBufferAllocator();
        // Compute the number of request arguments, accounting for nullable ones
        int len = 2;
        final CompositeBuffer cb = newRequestCompositeBuffer(len, RedisProtocolSupport.Command.SORT, allocator);
        addRequestArgument(key, cb, allocator);
        final RedisRequest request = newRequest(RedisProtocolSupport.Command.SORT, cb);
        final RedisPartitionAttributesBuilder partitionAttributesBuilder = partitionAttributesBuilderFunction
                    .apply(RedisProtocolSupport.Command.SORT);
        partitionAttributesBuilder.addKey(key);
        final Single<List<T>> result = (Single) partitionedRedisClient.request(partitionAttributesBuilder.build(), request,
                    RedisUtils.ListWithBuffersCoercedToCharSequences.class);
        return result;
    }

    @Override
    public <T> Single<List<T>> sort(@RedisProtocolSupport.Key final CharSequence key, @Nullable final CharSequence byPattern,
                                    @Nullable final RedisProtocolSupport.OffsetCount offsetCount,
                                    final Collection<? extends CharSequence> getPatterns,
                                    @Nullable final RedisProtocolSupport.SortOrder order,
                                    @Nullable final RedisProtocolSupport.SortSorting sorting) {
        requireNonNull(key);
        requireNonNull(getPatterns);
        final BufferAllocator allocator = partitionedRedisClient.getBufferAllocator();
        // Compute the number of request arguments, accounting for nullable ones
        int len = 2;
        if (byPattern != null) {
            len += 2;
        }
        if (offsetCount != null) {
            len += RedisProtocolSupport.OffsetCount.SIZE;
        }
        len += 2 * getPatterns.size();
        if (order != null) {
            len++;
        }
        if (sorting != null) {
            len++;
        }
        final CompositeBuffer cb = newRequestCompositeBuffer(len, RedisProtocolSupport.Command.SORT, allocator);
        addRequestArgument(key, cb, allocator);
        if (byPattern != null) {
            addRequestArgument(RedisProtocolSupport.SubCommand.BY, cb, allocator);
            addRequestArgument(byPattern, cb, allocator);
        }
        if (offsetCount != null) {
            offsetCount.writeTo(cb, allocator);
        }
        addRequestCharSequenceArguments(getPatterns, RedisProtocolSupport.SubCommand.GET, cb, allocator);
        if (order != null) {
            addRequestArgument(order, cb, allocator);
        }
        if (sorting != null) {
            addRequestArgument(sorting, cb, allocator);
        }
        final RedisRequest request = newRequest(RedisProtocolSupport.Command.SORT, cb);
        final RedisPartitionAttributesBuilder partitionAttributesBuilder = partitionAttributesBuilderFunction
                    .apply(RedisProtocolSupport.Command.SORT);
        partitionAttributesBuilder.addKey(key);
        final Single<List<T>> result = (Single) partitionedRedisClient.request(partitionAttributesBuilder.build(), request,
                    RedisUtils.ListWithBuffersCoercedToCharSequences.class);
        return result;
    }

    @Override
    public Single<Long> sort(@RedisProtocolSupport.Key final CharSequence key,
                             @RedisProtocolSupport.Key final CharSequence storeDestination) {
        requireNonNull(key);
        requireNonNull(storeDestination);
        final BufferAllocator allocator = partitionedRedisClient.getBufferAllocator();
        // Compute the number of request arguments, accounting for nullable ones
        int len = 4;
        final CompositeBuffer cb = newRequestCompositeBuffer(len, RedisProtocolSupport.Command.SORT, allocator);
        addRequestArgument(key, cb, allocator);
        addRequestArgument(RedisProtocolSupport.SubCommand.STORE, cb, allocator);
        addRequestArgument(storeDestination, cb, allocator);
        final RedisRequest request = newRequest(RedisProtocolSupport.Command.SORT, cb);
        final RedisPartitionAttributesBuilder partitionAttributesBuilder = partitionAttributesBuilderFunction
                    .apply(RedisProtocolSupport.Command.SORT);
        partitionAttributesBuilder.addKey(key);
        partitionAttributesBuilder.addKey(storeDestination);
        final Single<Long> result = partitionedRedisClient.request(partitionAttributesBuilder.build(), request, Long.class);
        return result;
    }

    @Override
    public Single<Long> sort(@RedisProtocolSupport.Key final CharSequence key,
                             @RedisProtocolSupport.Key final CharSequence storeDestination,
                             @Nullable final CharSequence byPattern, @Nullable final RedisProtocolSupport.OffsetCount offsetCount,
                             final Collection<? extends CharSequence> getPatterns,
                             @Nullable final RedisProtocolSupport.SortOrder order,
                             @Nullable final RedisProtocolSupport.SortSorting sorting) {
        requireNonNull(key);
        requireNonNull(storeDestination);
        requireNonNull(getPatterns);
        final BufferAllocator allocator = partitionedRedisClient.getBufferAllocator();
        // Compute the number of request arguments, accounting for nullable ones
        int len = 4;
        if (byPattern != null) {
            len += 2;
        }
        if (offsetCount != null) {
            len += RedisProtocolSupport.OffsetCount.SIZE;
        }
        len += 2 * getPatterns.size();
        if (order != null) {
            len++;
        }
        if (sorting != null) {
            len++;
        }
        final CompositeBuffer cb = newRequestCompositeBuffer(len, RedisProtocolSupport.Command.SORT, allocator);
        addRequestArgument(key, cb, allocator);
        addRequestArgument(RedisProtocolSupport.SubCommand.STORE, cb, allocator);
        addRequestArgument(storeDestination, cb, allocator);
        if (byPattern != null) {
            addRequestArgument(RedisProtocolSupport.SubCommand.BY, cb, allocator);
            addRequestArgument(byPattern, cb, allocator);
        }
        if (offsetCount != null) {
            offsetCount.writeTo(cb, allocator);
        }
        addRequestCharSequenceArguments(getPatterns, RedisProtocolSupport.SubCommand.GET, cb, allocator);
        if (order != null) {
            addRequestArgument(order, cb, allocator);
        }
        if (sorting != null) {
            addRequestArgument(sorting, cb, allocator);
        }
        final RedisRequest request = newRequest(RedisProtocolSupport.Command.SORT, cb);
        final RedisPartitionAttributesBuilder partitionAttributesBuilder = partitionAttributesBuilderFunction
                    .apply(RedisProtocolSupport.Command.SORT);
        partitionAttributesBuilder.addKey(key);
        partitionAttributesBuilder.addKey(storeDestination);
        final Single<Long> result = partitionedRedisClient.request(partitionAttributesBuilder.build(), request, Long.class);
        return result;
    }

    @Override
    public Single<String> spop(@RedisProtocolSupport.Key final CharSequence key) {
        requireNonNull(key);
        final BufferAllocator allocator = partitionedRedisClient.getBufferAllocator();
        // Compute the number of request arguments, accounting for nullable ones
        int len = 2;
        final CompositeBuffer cb = newRequestCompositeBuffer(len, RedisProtocolSupport.Command.SPOP, allocator);
        addRequestArgument(key, cb, allocator);
        final RedisRequest request = newRequest(RedisProtocolSupport.Command.SPOP, cb);
        final RedisPartitionAttributesBuilder partitionAttributesBuilder = partitionAttributesBuilderFunction
                    .apply(RedisProtocolSupport.Command.SPOP);
        partitionAttributesBuilder.addKey(key);
        final Single<String> result = partitionedRedisClient.request(partitionAttributesBuilder.build(), request, String.class);
        return result;
    }

    @Override
    public Single<String> spop(@RedisProtocolSupport.Key final CharSequence key, @Nullable final Long count) {
        requireNonNull(key);
        final BufferAllocator allocator = partitionedRedisClient.getBufferAllocator();
        // Compute the number of request arguments, accounting for nullable ones
        int len = 2;
        if (count != null) {
            len++;
        }
        final CompositeBuffer cb = newRequestCompositeBuffer(len, RedisProtocolSupport.Command.SPOP, allocator);
        addRequestArgument(key, cb, allocator);
        if (count != null) {
            addRequestArgument(count, cb, allocator);
        }
        final RedisRequest request = newRequest(RedisProtocolSupport.Command.SPOP, cb);
        final RedisPartitionAttributesBuilder partitionAttributesBuilder = partitionAttributesBuilderFunction
                    .apply(RedisProtocolSupport.Command.SPOP);
        partitionAttributesBuilder.addKey(key);
        final Single<String> result = partitionedRedisClient.request(partitionAttributesBuilder.build(), request, String.class);
        return result;
    }

    @Override
    public Single<String> srandmember(@RedisProtocolSupport.Key final CharSequence key) {
        requireNonNull(key);
        final BufferAllocator allocator = partitionedRedisClient.getBufferAllocator();
        // Compute the number of request arguments, accounting for nullable ones
        int len = 2;
        final CompositeBuffer cb = newRequestCompositeBuffer(len, RedisProtocolSupport.Command.SRANDMEMBER, allocator);
        addRequestArgument(key, cb, allocator);
        final RedisRequest request = newRequest(RedisProtocolSupport.Command.SRANDMEMBER, cb);
        final RedisPartitionAttributesBuilder partitionAttributesBuilder = partitionAttributesBuilderFunction
                    .apply(RedisProtocolSupport.Command.SRANDMEMBER);
        partitionAttributesBuilder.addKey(key);
        final Single<String> result = partitionedRedisClient.request(partitionAttributesBuilder.build(), request, String.class);
        return result;
    }

    @Override
    public Single<List<String>> srandmember(@RedisProtocolSupport.Key final CharSequence key, final long count) {
        requireNonNull(key);
        final BufferAllocator allocator = partitionedRedisClient.getBufferAllocator();
        // Compute the number of request arguments, accounting for nullable ones
        int len = 3;
        final CompositeBuffer cb = newRequestCompositeBuffer(len, RedisProtocolSupport.Command.SRANDMEMBER, allocator);
        addRequestArgument(key, cb, allocator);
        addRequestArgument(count, cb, allocator);
        final RedisRequest request = newRequest(RedisProtocolSupport.Command.SRANDMEMBER, cb);
        final RedisPartitionAttributesBuilder partitionAttributesBuilder = partitionAttributesBuilderFunction
                    .apply(RedisProtocolSupport.Command.SRANDMEMBER);
        partitionAttributesBuilder.addKey(key);
        final Single<List<String>> result = (Single) partitionedRedisClient.request(partitionAttributesBuilder.build(), request,
                    RedisUtils.ListWithBuffersCoercedToCharSequences.class);
        return result;
    }

    @Override
    public Single<Long> srem(@RedisProtocolSupport.Key final CharSequence key, final CharSequence member) {
        requireNonNull(key);
        requireNonNull(member);
        final BufferAllocator allocator = partitionedRedisClient.getBufferAllocator();
        // Compute the number of request arguments, accounting for nullable ones
        int len = 3;
        final CompositeBuffer cb = newRequestCompositeBuffer(len, RedisProtocolSupport.Command.SREM, allocator);
        addRequestArgument(key, cb, allocator);
        addRequestArgument(member, cb, allocator);
        final RedisRequest request = newRequest(RedisProtocolSupport.Command.SREM, cb);
        final RedisPartitionAttributesBuilder partitionAttributesBuilder = partitionAttributesBuilderFunction
                    .apply(RedisProtocolSupport.Command.SREM);
        partitionAttributesBuilder.addKey(key);
        final Single<Long> result = partitionedRedisClient.request(partitionAttributesBuilder.build(), request, Long.class);
        return result;
    }

    @Override
    public Single<Long> srem(@RedisProtocolSupport.Key final CharSequence key, final CharSequence member1,
                             final CharSequence member2) {
        requireNonNull(key);
        requireNonNull(member1);
        requireNonNull(member2);
        final BufferAllocator allocator = partitionedRedisClient.getBufferAllocator();
        // Compute the number of request arguments, accounting for nullable ones
        int len = 4;
        final CompositeBuffer cb = newRequestCompositeBuffer(len, RedisProtocolSupport.Command.SREM, allocator);
        addRequestArgument(key, cb, allocator);
        addRequestArgument(member1, cb, allocator);
        addRequestArgument(member2, cb, allocator);
        final RedisRequest request = newRequest(RedisProtocolSupport.Command.SREM, cb);
        final RedisPartitionAttributesBuilder partitionAttributesBuilder = partitionAttributesBuilderFunction
                    .apply(RedisProtocolSupport.Command.SREM);
        partitionAttributesBuilder.addKey(key);
        final Single<Long> result = partitionedRedisClient.request(partitionAttributesBuilder.build(), request, Long.class);
        return result;
    }

    @Override
    public Single<Long> srem(@RedisProtocolSupport.Key final CharSequence key, final CharSequence member1,
                             final CharSequence member2, final CharSequence member3) {
        requireNonNull(key);
        requireNonNull(member1);
        requireNonNull(member2);
        requireNonNull(member3);
        final BufferAllocator allocator = partitionedRedisClient.getBufferAllocator();
        // Compute the number of request arguments, accounting for nullable ones
        int len = 5;
        final CompositeBuffer cb = newRequestCompositeBuffer(len, RedisProtocolSupport.Command.SREM, allocator);
        addRequestArgument(key, cb, allocator);
        addRequestArgument(member1, cb, allocator);
        addRequestArgument(member2, cb, allocator);
        addRequestArgument(member3, cb, allocator);
        final RedisRequest request = newRequest(RedisProtocolSupport.Command.SREM, cb);
        final RedisPartitionAttributesBuilder partitionAttributesBuilder = partitionAttributesBuilderFunction
                    .apply(RedisProtocolSupport.Command.SREM);
        partitionAttributesBuilder.addKey(key);
        final Single<Long> result = partitionedRedisClient.request(partitionAttributesBuilder.build(), request, Long.class);
        return result;
    }

    @Override
    public Single<Long> srem(@RedisProtocolSupport.Key final CharSequence key, final Collection<? extends CharSequence> members) {
        requireNonNull(key);
        requireNonNull(members);
        final BufferAllocator allocator = partitionedRedisClient.getBufferAllocator();
        // Compute the number of request arguments, accounting for nullable ones
        int len = 2;
        len += members.size();
        final CompositeBuffer cb = newRequestCompositeBuffer(len, RedisProtocolSupport.Command.SREM, allocator);
        addRequestArgument(key, cb, allocator);
        addRequestCharSequenceArguments(members, null, cb, allocator);
        final RedisRequest request = newRequest(RedisProtocolSupport.Command.SREM, cb);
        final RedisPartitionAttributesBuilder partitionAttributesBuilder = partitionAttributesBuilderFunction
                    .apply(RedisProtocolSupport.Command.SREM);
        partitionAttributesBuilder.addKey(key);
        final Single<Long> result = partitionedRedisClient.request(partitionAttributesBuilder.build(), request, Long.class);
        return result;
    }

    @Override
    public <T> Single<List<T>> sscan(@RedisProtocolSupport.Key final CharSequence key, final long cursor) {
        requireNonNull(key);
        final BufferAllocator allocator = partitionedRedisClient.getBufferAllocator();
        // Compute the number of request arguments, accounting for nullable ones
        int len = 3;
        final CompositeBuffer cb = newRequestCompositeBuffer(len, RedisProtocolSupport.Command.SSCAN, allocator);
        addRequestArgument(key, cb, allocator);
        addRequestArgument(cursor, cb, allocator);
        final RedisRequest request = newRequest(RedisProtocolSupport.Command.SSCAN, cb);
        final RedisPartitionAttributesBuilder partitionAttributesBuilder = partitionAttributesBuilderFunction
                    .apply(RedisProtocolSupport.Command.SSCAN);
        partitionAttributesBuilder.addKey(key);
        final Single<List<T>> result = (Single) partitionedRedisClient.request(partitionAttributesBuilder.build(), request,
                    RedisUtils.ListWithBuffersCoercedToCharSequences.class);
        return result;
    }

    @Override
    public <T> Single<List<T>> sscan(@RedisProtocolSupport.Key final CharSequence key, final long cursor,
                                     @Nullable final CharSequence matchPattern, @Nullable final Long count) {
        requireNonNull(key);
        final BufferAllocator allocator = partitionedRedisClient.getBufferAllocator();
        // Compute the number of request arguments, accounting for nullable ones
        int len = 3;
        if (matchPattern != null) {
            len += 2;
        }
        if (count != null) {
            len += 2;
        }
        final CompositeBuffer cb = newRequestCompositeBuffer(len, RedisProtocolSupport.Command.SSCAN, allocator);
        addRequestArgument(key, cb, allocator);
        addRequestArgument(cursor, cb, allocator);
        if (matchPattern != null) {
            addRequestArgument(RedisProtocolSupport.SubCommand.MATCH, cb, allocator);
            addRequestArgument(matchPattern, cb, allocator);
        }
        if (count != null) {
            addRequestArgument(RedisProtocolSupport.SubCommand.COUNT, cb, allocator);
            addRequestArgument(count, cb, allocator);
        }
        final RedisRequest request = newRequest(RedisProtocolSupport.Command.SSCAN, cb);
        final RedisPartitionAttributesBuilder partitionAttributesBuilder = partitionAttributesBuilderFunction
                    .apply(RedisProtocolSupport.Command.SSCAN);
        partitionAttributesBuilder.addKey(key);
        final Single<List<T>> result = (Single) partitionedRedisClient.request(partitionAttributesBuilder.build(), request,
                    RedisUtils.ListWithBuffersCoercedToCharSequences.class);
        return result;
    }

    @Override
    public Single<Long> strlen(@RedisProtocolSupport.Key final CharSequence key) {
        requireNonNull(key);
        final BufferAllocator allocator = partitionedRedisClient.getBufferAllocator();
        // Compute the number of request arguments, accounting for nullable ones
        int len = 2;
        final CompositeBuffer cb = newRequestCompositeBuffer(len, RedisProtocolSupport.Command.STRLEN, allocator);
        addRequestArgument(key, cb, allocator);
        final RedisRequest request = newRequest(RedisProtocolSupport.Command.STRLEN, cb);
        final RedisPartitionAttributesBuilder partitionAttributesBuilder = partitionAttributesBuilderFunction
                    .apply(RedisProtocolSupport.Command.STRLEN);
        partitionAttributesBuilder.addKey(key);
        final Single<Long> result = partitionedRedisClient.request(partitionAttributesBuilder.build(), request, Long.class);
        return result;
    }

    @Override
    public Single<PubSubRedisConnection> subscribe(final CharSequence channel) {
        requireNonNull(channel);
        final BufferAllocator allocator = partitionedRedisClient.getBufferAllocator();
        // Compute the number of request arguments, accounting for nullable ones
        int len = 2;
        final CompositeBuffer cb = newRequestCompositeBuffer(len, RedisProtocolSupport.Command.SUBSCRIBE, allocator);
        addRequestArgument(channel, cb, allocator);
        final RedisRequest request = newRequest(RedisProtocolSupport.Command.SUBSCRIBE, cb);
        final RedisPartitionAttributesBuilder partitionAttributesBuilder = partitionAttributesBuilderFunction
                    .apply(RedisProtocolSupport.Command.SUBSCRIBE);
        return newConnectedClient(partitionedRedisClient, partitionAttributesBuilder.build(), request,
                    (rcnx, pub) -> new DefaultPubSubRedisConnection(rcnx, pub.map(msg -> (PubSubRedisMessage) msg)));
    }

    @Override
    public <T> Single<List<T>> sunion(@RedisProtocolSupport.Key final CharSequence key) {
        requireNonNull(key);
        final BufferAllocator allocator = partitionedRedisClient.getBufferAllocator();
        // Compute the number of request arguments, accounting for nullable ones
        int len = 2;
        final CompositeBuffer cb = newRequestCompositeBuffer(len, RedisProtocolSupport.Command.SUNION, allocator);
        addRequestArgument(key, cb, allocator);
        final RedisRequest request = newRequest(RedisProtocolSupport.Command.SUNION, cb);
        final RedisPartitionAttributesBuilder partitionAttributesBuilder = partitionAttributesBuilderFunction
                    .apply(RedisProtocolSupport.Command.SUNION);
        partitionAttributesBuilder.addKey(key);
        final Single<List<T>> result = (Single) partitionedRedisClient.request(partitionAttributesBuilder.build(), request,
                    RedisUtils.ListWithBuffersCoercedToCharSequences.class);
        return result;
    }

    @Override
    public <T> Single<List<T>> sunion(@RedisProtocolSupport.Key final CharSequence key1,
                                      @RedisProtocolSupport.Key final CharSequence key2) {
        requireNonNull(key1);
        requireNonNull(key2);
        final BufferAllocator allocator = partitionedRedisClient.getBufferAllocator();
        // Compute the number of request arguments, accounting for nullable ones
        int len = 3;
        final CompositeBuffer cb = newRequestCompositeBuffer(len, RedisProtocolSupport.Command.SUNION, allocator);
        addRequestArgument(key1, cb, allocator);
        addRequestArgument(key2, cb, allocator);
        final RedisRequest request = newRequest(RedisProtocolSupport.Command.SUNION, cb);
        final RedisPartitionAttributesBuilder partitionAttributesBuilder = partitionAttributesBuilderFunction
                    .apply(RedisProtocolSupport.Command.SUNION);
        partitionAttributesBuilder.addKey(key1);
        partitionAttributesBuilder.addKey(key2);
        final Single<List<T>> result = (Single) partitionedRedisClient.request(partitionAttributesBuilder.build(), request,
                    RedisUtils.ListWithBuffersCoercedToCharSequences.class);
        return result;
    }

    @Override
    public <T> Single<List<T>> sunion(@RedisProtocolSupport.Key final CharSequence key1,
                                      @RedisProtocolSupport.Key final CharSequence key2,
                                      @RedisProtocolSupport.Key final CharSequence key3) {
        requireNonNull(key1);
        requireNonNull(key2);
        requireNonNull(key3);
        final BufferAllocator allocator = partitionedRedisClient.getBufferAllocator();
        // Compute the number of request arguments, accounting for nullable ones
        int len = 4;
        final CompositeBuffer cb = newRequestCompositeBuffer(len, RedisProtocolSupport.Command.SUNION, allocator);
        addRequestArgument(key1, cb, allocator);
        addRequestArgument(key2, cb, allocator);
        addRequestArgument(key3, cb, allocator);
        final RedisRequest request = newRequest(RedisProtocolSupport.Command.SUNION, cb);
        final RedisPartitionAttributesBuilder partitionAttributesBuilder = partitionAttributesBuilderFunction
                    .apply(RedisProtocolSupport.Command.SUNION);
        partitionAttributesBuilder.addKey(key1);
        partitionAttributesBuilder.addKey(key2);
        partitionAttributesBuilder.addKey(key3);
        final Single<List<T>> result = (Single) partitionedRedisClient.request(partitionAttributesBuilder.build(), request,
                    RedisUtils.ListWithBuffersCoercedToCharSequences.class);
        return result;
    }

    @Override
    public <T> Single<List<T>> sunion(@RedisProtocolSupport.Key final Collection<? extends CharSequence> keys) {
        requireNonNull(keys);
        final BufferAllocator allocator = partitionedRedisClient.getBufferAllocator();
        // Compute the number of request arguments, accounting for nullable ones
        int len = 1;
        len += keys.size();
        final CompositeBuffer cb = newRequestCompositeBuffer(len, RedisProtocolSupport.Command.SUNION, allocator);
        addRequestCharSequenceArguments(keys, null, cb, allocator);
        final RedisRequest request = newRequest(RedisProtocolSupport.Command.SUNION, cb);
        final RedisPartitionAttributesBuilder partitionAttributesBuilder = partitionAttributesBuilderFunction
                    .apply(RedisProtocolSupport.Command.SUNION);
        addCharSequenceKeysToAttributeBuilder(keys, partitionAttributesBuilder);
        final Single<List<T>> result = (Single) partitionedRedisClient.request(partitionAttributesBuilder.build(), request,
                    RedisUtils.ListWithBuffersCoercedToCharSequences.class);
        return result;
    }

    @Override
    public Single<Long> sunionstore(@RedisProtocolSupport.Key final CharSequence destination,
                                    @RedisProtocolSupport.Key final CharSequence key) {
        requireNonNull(destination);
        requireNonNull(key);
        final BufferAllocator allocator = partitionedRedisClient.getBufferAllocator();
        // Compute the number of request arguments, accounting for nullable ones
        int len = 3;
        final CompositeBuffer cb = newRequestCompositeBuffer(len, RedisProtocolSupport.Command.SUNIONSTORE, allocator);
        addRequestArgument(destination, cb, allocator);
        addRequestArgument(key, cb, allocator);
        final RedisRequest request = newRequest(RedisProtocolSupport.Command.SUNIONSTORE, cb);
        final RedisPartitionAttributesBuilder partitionAttributesBuilder = partitionAttributesBuilderFunction
                    .apply(RedisProtocolSupport.Command.SUNIONSTORE);
        partitionAttributesBuilder.addKey(destination);
        partitionAttributesBuilder.addKey(key);
        final Single<Long> result = partitionedRedisClient.request(partitionAttributesBuilder.build(), request, Long.class);
        return result;
    }

    @Override
    public Single<Long> sunionstore(@RedisProtocolSupport.Key final CharSequence destination,
                                    @RedisProtocolSupport.Key final CharSequence key1,
                                    @RedisProtocolSupport.Key final CharSequence key2) {
        requireNonNull(destination);
        requireNonNull(key1);
        requireNonNull(key2);
        final BufferAllocator allocator = partitionedRedisClient.getBufferAllocator();
        // Compute the number of request arguments, accounting for nullable ones
        int len = 4;
        final CompositeBuffer cb = newRequestCompositeBuffer(len, RedisProtocolSupport.Command.SUNIONSTORE, allocator);
        addRequestArgument(destination, cb, allocator);
        addRequestArgument(key1, cb, allocator);
        addRequestArgument(key2, cb, allocator);
        final RedisRequest request = newRequest(RedisProtocolSupport.Command.SUNIONSTORE, cb);
        final RedisPartitionAttributesBuilder partitionAttributesBuilder = partitionAttributesBuilderFunction
                    .apply(RedisProtocolSupport.Command.SUNIONSTORE);
        partitionAttributesBuilder.addKey(destination);
        partitionAttributesBuilder.addKey(key1);
        partitionAttributesBuilder.addKey(key2);
        final Single<Long> result = partitionedRedisClient.request(partitionAttributesBuilder.build(), request, Long.class);
        return result;
    }

    @Override
    public Single<Long> sunionstore(@RedisProtocolSupport.Key final CharSequence destination,
                                    @RedisProtocolSupport.Key final CharSequence key1,
                                    @RedisProtocolSupport.Key final CharSequence key2,
                                    @RedisProtocolSupport.Key final CharSequence key3) {
        requireNonNull(destination);
        requireNonNull(key1);
        requireNonNull(key2);
        requireNonNull(key3);
        final BufferAllocator allocator = partitionedRedisClient.getBufferAllocator();
        // Compute the number of request arguments, accounting for nullable ones
        int len = 5;
        final CompositeBuffer cb = newRequestCompositeBuffer(len, RedisProtocolSupport.Command.SUNIONSTORE, allocator);
        addRequestArgument(destination, cb, allocator);
        addRequestArgument(key1, cb, allocator);
        addRequestArgument(key2, cb, allocator);
        addRequestArgument(key3, cb, allocator);
        final RedisRequest request = newRequest(RedisProtocolSupport.Command.SUNIONSTORE, cb);
        final RedisPartitionAttributesBuilder partitionAttributesBuilder = partitionAttributesBuilderFunction
                    .apply(RedisProtocolSupport.Command.SUNIONSTORE);
        partitionAttributesBuilder.addKey(destination);
        partitionAttributesBuilder.addKey(key1);
        partitionAttributesBuilder.addKey(key2);
        partitionAttributesBuilder.addKey(key3);
        final Single<Long> result = partitionedRedisClient.request(partitionAttributesBuilder.build(), request, Long.class);
        return result;
    }

    @Override
    public Single<Long> sunionstore(@RedisProtocolSupport.Key final CharSequence destination,
                                    @RedisProtocolSupport.Key final Collection<? extends CharSequence> keys) {
        requireNonNull(destination);
        requireNonNull(keys);
        final BufferAllocator allocator = partitionedRedisClient.getBufferAllocator();
        // Compute the number of request arguments, accounting for nullable ones
        int len = 2;
        len += keys.size();
        final CompositeBuffer cb = newRequestCompositeBuffer(len, RedisProtocolSupport.Command.SUNIONSTORE, allocator);
        addRequestArgument(destination, cb, allocator);
        addRequestCharSequenceArguments(keys, null, cb, allocator);
        final RedisRequest request = newRequest(RedisProtocolSupport.Command.SUNIONSTORE, cb);
        final RedisPartitionAttributesBuilder partitionAttributesBuilder = partitionAttributesBuilderFunction
                    .apply(RedisProtocolSupport.Command.SUNIONSTORE);
        partitionAttributesBuilder.addKey(destination);
        addCharSequenceKeysToAttributeBuilder(keys, partitionAttributesBuilder);
        final Single<Long> result = partitionedRedisClient.request(partitionAttributesBuilder.build(), request, Long.class);
        return result;
    }

    @Override
    public Single<String> swapdb(final long index, final long index1) {
        final BufferAllocator allocator = partitionedRedisClient.getBufferAllocator();
        // Compute the number of request arguments, accounting for nullable ones
        int len = 3;
        final CompositeBuffer cb = newRequestCompositeBuffer(len, RedisProtocolSupport.Command.SWAPDB, allocator);
        addRequestArgument(index, cb, allocator);
        addRequestArgument(index1, cb, allocator);
        final RedisRequest request = newRequest(RedisProtocolSupport.Command.SWAPDB, cb);
        final RedisPartitionAttributesBuilder partitionAttributesBuilder = partitionAttributesBuilderFunction
                    .apply(RedisProtocolSupport.Command.SWAPDB);
        final Single<String> result = partitionedRedisClient.request(partitionAttributesBuilder.build(), request, String.class);
        return result;
    }

    @Override
    public <T> Single<List<T>> time() {
        final BufferAllocator allocator = partitionedRedisClient.getBufferAllocator();
        // Compute the number of request arguments, accounting for nullable ones
        int len = 1;
        final CompositeBuffer cb = newRequestCompositeBuffer(len, RedisProtocolSupport.Command.TIME, allocator);
        final RedisRequest request = newRequest(RedisProtocolSupport.Command.TIME, cb);
        final RedisPartitionAttributesBuilder partitionAttributesBuilder = partitionAttributesBuilderFunction
                    .apply(RedisProtocolSupport.Command.TIME);
        final Single<List<T>> result = (Single) partitionedRedisClient.request(partitionAttributesBuilder.build(), request,
                    RedisUtils.ListWithBuffersCoercedToCharSequences.class);
        return result;
    }

    @Override
    public Single<Long> touch(@RedisProtocolSupport.Key final CharSequence key) {
        requireNonNull(key);
        final BufferAllocator allocator = partitionedRedisClient.getBufferAllocator();
        // Compute the number of request arguments, accounting for nullable ones
        int len = 2;
        final CompositeBuffer cb = newRequestCompositeBuffer(len, RedisProtocolSupport.Command.TOUCH, allocator);
        addRequestArgument(key, cb, allocator);
        final RedisRequest request = newRequest(RedisProtocolSupport.Command.TOUCH, cb);
        final RedisPartitionAttributesBuilder partitionAttributesBuilder = partitionAttributesBuilderFunction
                    .apply(RedisProtocolSupport.Command.TOUCH);
        partitionAttributesBuilder.addKey(key);
        final Single<Long> result = partitionedRedisClient.request(partitionAttributesBuilder.build(), request, Long.class);
        return result;
    }

    @Override
    public Single<Long> touch(@RedisProtocolSupport.Key final CharSequence key1,
                              @RedisProtocolSupport.Key final CharSequence key2) {
        requireNonNull(key1);
        requireNonNull(key2);
        final BufferAllocator allocator = partitionedRedisClient.getBufferAllocator();
        // Compute the number of request arguments, accounting for nullable ones
        int len = 3;
        final CompositeBuffer cb = newRequestCompositeBuffer(len, RedisProtocolSupport.Command.TOUCH, allocator);
        addRequestArgument(key1, cb, allocator);
        addRequestArgument(key2, cb, allocator);
        final RedisRequest request = newRequest(RedisProtocolSupport.Command.TOUCH, cb);
        final RedisPartitionAttributesBuilder partitionAttributesBuilder = partitionAttributesBuilderFunction
                    .apply(RedisProtocolSupport.Command.TOUCH);
        partitionAttributesBuilder.addKey(key1);
        partitionAttributesBuilder.addKey(key2);
        final Single<Long> result = partitionedRedisClient.request(partitionAttributesBuilder.build(), request, Long.class);
        return result;
    }

    @Override
    public Single<Long> touch(@RedisProtocolSupport.Key final CharSequence key1,
                              @RedisProtocolSupport.Key final CharSequence key2,
                              @RedisProtocolSupport.Key final CharSequence key3) {
        requireNonNull(key1);
        requireNonNull(key2);
        requireNonNull(key3);
        final BufferAllocator allocator = partitionedRedisClient.getBufferAllocator();
        // Compute the number of request arguments, accounting for nullable ones
        int len = 4;
        final CompositeBuffer cb = newRequestCompositeBuffer(len, RedisProtocolSupport.Command.TOUCH, allocator);
        addRequestArgument(key1, cb, allocator);
        addRequestArgument(key2, cb, allocator);
        addRequestArgument(key3, cb, allocator);
        final RedisRequest request = newRequest(RedisProtocolSupport.Command.TOUCH, cb);
        final RedisPartitionAttributesBuilder partitionAttributesBuilder = partitionAttributesBuilderFunction
                    .apply(RedisProtocolSupport.Command.TOUCH);
        partitionAttributesBuilder.addKey(key1);
        partitionAttributesBuilder.addKey(key2);
        partitionAttributesBuilder.addKey(key3);
        final Single<Long> result = partitionedRedisClient.request(partitionAttributesBuilder.build(), request, Long.class);
        return result;
    }

    @Override
    public Single<Long> touch(@RedisProtocolSupport.Key final Collection<? extends CharSequence> keys) {
        requireNonNull(keys);
        final BufferAllocator allocator = partitionedRedisClient.getBufferAllocator();
        // Compute the number of request arguments, accounting for nullable ones
        int len = 1;
        len += keys.size();
        final CompositeBuffer cb = newRequestCompositeBuffer(len, RedisProtocolSupport.Command.TOUCH, allocator);
        addRequestCharSequenceArguments(keys, null, cb, allocator);
        final RedisRequest request = newRequest(RedisProtocolSupport.Command.TOUCH, cb);
        final RedisPartitionAttributesBuilder partitionAttributesBuilder = partitionAttributesBuilderFunction
                    .apply(RedisProtocolSupport.Command.TOUCH);
        addCharSequenceKeysToAttributeBuilder(keys, partitionAttributesBuilder);
        final Single<Long> result = partitionedRedisClient.request(partitionAttributesBuilder.build(), request, Long.class);
        return result;
    }

    @Override
    public Single<Long> ttl(@RedisProtocolSupport.Key final CharSequence key) {
        requireNonNull(key);
        final BufferAllocator allocator = partitionedRedisClient.getBufferAllocator();
        // Compute the number of request arguments, accounting for nullable ones
        int len = 2;
        final CompositeBuffer cb = newRequestCompositeBuffer(len, RedisProtocolSupport.Command.TTL, allocator);
        addRequestArgument(key, cb, allocator);
        final RedisRequest request = newRequest(RedisProtocolSupport.Command.TTL, cb);
        final RedisPartitionAttributesBuilder partitionAttributesBuilder = partitionAttributesBuilderFunction
                    .apply(RedisProtocolSupport.Command.TTL);
        partitionAttributesBuilder.addKey(key);
        final Single<Long> result = partitionedRedisClient.request(partitionAttributesBuilder.build(), request, Long.class);
        return result;
    }

    @Override
    public Single<String> type(@RedisProtocolSupport.Key final CharSequence key) {
        requireNonNull(key);
        final BufferAllocator allocator = partitionedRedisClient.getBufferAllocator();
        // Compute the number of request arguments, accounting for nullable ones
        int len = 2;
        final CompositeBuffer cb = newRequestCompositeBuffer(len, RedisProtocolSupport.Command.TYPE, allocator);
        addRequestArgument(key, cb, allocator);
        final RedisRequest request = newRequest(RedisProtocolSupport.Command.TYPE, cb);
        final RedisPartitionAttributesBuilder partitionAttributesBuilder = partitionAttributesBuilderFunction
                    .apply(RedisProtocolSupport.Command.TYPE);
        partitionAttributesBuilder.addKey(key);
        final Single<String> result = partitionedRedisClient.request(partitionAttributesBuilder.build(), request, String.class);
        return result;
    }

    @Override
    public Single<Long> unlink(@RedisProtocolSupport.Key final CharSequence key) {
        requireNonNull(key);
        final BufferAllocator allocator = partitionedRedisClient.getBufferAllocator();
        // Compute the number of request arguments, accounting for nullable ones
        int len = 2;
        final CompositeBuffer cb = newRequestCompositeBuffer(len, RedisProtocolSupport.Command.UNLINK, allocator);
        addRequestArgument(key, cb, allocator);
        final RedisRequest request = newRequest(RedisProtocolSupport.Command.UNLINK, cb);
        final RedisPartitionAttributesBuilder partitionAttributesBuilder = partitionAttributesBuilderFunction
                    .apply(RedisProtocolSupport.Command.UNLINK);
        partitionAttributesBuilder.addKey(key);
        final Single<Long> result = partitionedRedisClient.request(partitionAttributesBuilder.build(), request, Long.class);
        return result;
    }

    @Override
    public Single<Long> unlink(@RedisProtocolSupport.Key final CharSequence key1,
                               @RedisProtocolSupport.Key final CharSequence key2) {
        requireNonNull(key1);
        requireNonNull(key2);
        final BufferAllocator allocator = partitionedRedisClient.getBufferAllocator();
        // Compute the number of request arguments, accounting for nullable ones
        int len = 3;
        final CompositeBuffer cb = newRequestCompositeBuffer(len, RedisProtocolSupport.Command.UNLINK, allocator);
        addRequestArgument(key1, cb, allocator);
        addRequestArgument(key2, cb, allocator);
        final RedisRequest request = newRequest(RedisProtocolSupport.Command.UNLINK, cb);
        final RedisPartitionAttributesBuilder partitionAttributesBuilder = partitionAttributesBuilderFunction
                    .apply(RedisProtocolSupport.Command.UNLINK);
        partitionAttributesBuilder.addKey(key1);
        partitionAttributesBuilder.addKey(key2);
        final Single<Long> result = partitionedRedisClient.request(partitionAttributesBuilder.build(), request, Long.class);
        return result;
    }

    @Override
    public Single<Long> unlink(@RedisProtocolSupport.Key final CharSequence key1,
                               @RedisProtocolSupport.Key final CharSequence key2,
                               @RedisProtocolSupport.Key final CharSequence key3) {
        requireNonNull(key1);
        requireNonNull(key2);
        requireNonNull(key3);
        final BufferAllocator allocator = partitionedRedisClient.getBufferAllocator();
        // Compute the number of request arguments, accounting for nullable ones
        int len = 4;
        final CompositeBuffer cb = newRequestCompositeBuffer(len, RedisProtocolSupport.Command.UNLINK, allocator);
        addRequestArgument(key1, cb, allocator);
        addRequestArgument(key2, cb, allocator);
        addRequestArgument(key3, cb, allocator);
        final RedisRequest request = newRequest(RedisProtocolSupport.Command.UNLINK, cb);
        final RedisPartitionAttributesBuilder partitionAttributesBuilder = partitionAttributesBuilderFunction
                    .apply(RedisProtocolSupport.Command.UNLINK);
        partitionAttributesBuilder.addKey(key1);
        partitionAttributesBuilder.addKey(key2);
        partitionAttributesBuilder.addKey(key3);
        final Single<Long> result = partitionedRedisClient.request(partitionAttributesBuilder.build(), request, Long.class);
        return result;
    }

    @Override
    public Single<Long> unlink(@RedisProtocolSupport.Key final Collection<? extends CharSequence> keys) {
        requireNonNull(keys);
        final BufferAllocator allocator = partitionedRedisClient.getBufferAllocator();
        // Compute the number of request arguments, accounting for nullable ones
        int len = 1;
        len += keys.size();
        final CompositeBuffer cb = newRequestCompositeBuffer(len, RedisProtocolSupport.Command.UNLINK, allocator);
        addRequestCharSequenceArguments(keys, null, cb, allocator);
        final RedisRequest request = newRequest(RedisProtocolSupport.Command.UNLINK, cb);
        final RedisPartitionAttributesBuilder partitionAttributesBuilder = partitionAttributesBuilderFunction
                    .apply(RedisProtocolSupport.Command.UNLINK);
        addCharSequenceKeysToAttributeBuilder(keys, partitionAttributesBuilder);
        final Single<Long> result = partitionedRedisClient.request(partitionAttributesBuilder.build(), request, Long.class);
        return result;
    }

    @Override
    public Single<String> unwatch() {
        final BufferAllocator allocator = partitionedRedisClient.getBufferAllocator();
        // Compute the number of request arguments, accounting for nullable ones
        int len = 1;
        final CompositeBuffer cb = newRequestCompositeBuffer(len, RedisProtocolSupport.Command.UNWATCH, allocator);
        final RedisRequest request = newRequest(RedisProtocolSupport.Command.UNWATCH, cb);
        final RedisPartitionAttributesBuilder partitionAttributesBuilder = partitionAttributesBuilderFunction
                    .apply(RedisProtocolSupport.Command.UNWATCH);
        final Single<String> result = partitionedRedisClient.request(partitionAttributesBuilder.build(), request, String.class);
        return result;
    }

    @Override
    public Single<Long> wait(final long numslaves, final long timeout) {
        final BufferAllocator allocator = partitionedRedisClient.getBufferAllocator();
        // Compute the number of request arguments, accounting for nullable ones
        int len = 3;
        final CompositeBuffer cb = newRequestCompositeBuffer(len, RedisProtocolSupport.Command.WAIT, allocator);
        addRequestArgument(numslaves, cb, allocator);
        addRequestArgument(timeout, cb, allocator);
        final RedisRequest request = newRequest(RedisProtocolSupport.Command.WAIT, cb);
        final RedisPartitionAttributesBuilder partitionAttributesBuilder = partitionAttributesBuilderFunction
                    .apply(RedisProtocolSupport.Command.WAIT);
        final Single<Long> result = partitionedRedisClient.request(partitionAttributesBuilder.build(), request, Long.class);
        return result;
    }

    @Override
    public Single<String> watch(@RedisProtocolSupport.Key final CharSequence key) {
        requireNonNull(key);
        final BufferAllocator allocator = partitionedRedisClient.getBufferAllocator();
        // Compute the number of request arguments, accounting for nullable ones
        int len = 2;
        final CompositeBuffer cb = newRequestCompositeBuffer(len, RedisProtocolSupport.Command.WATCH, allocator);
        addRequestArgument(key, cb, allocator);
        final RedisRequest request = newRequest(RedisProtocolSupport.Command.WATCH, cb);
        final RedisPartitionAttributesBuilder partitionAttributesBuilder = partitionAttributesBuilderFunction
                    .apply(RedisProtocolSupport.Command.WATCH);
        partitionAttributesBuilder.addKey(key);
        final Single<String> result = partitionedRedisClient.request(partitionAttributesBuilder.build(), request, String.class);
        return result;
    }

    @Override
    public Single<String> watch(@RedisProtocolSupport.Key final CharSequence key1,
                                @RedisProtocolSupport.Key final CharSequence key2) {
        requireNonNull(key1);
        requireNonNull(key2);
        final BufferAllocator allocator = partitionedRedisClient.getBufferAllocator();
        // Compute the number of request arguments, accounting for nullable ones
        int len = 3;
        final CompositeBuffer cb = newRequestCompositeBuffer(len, RedisProtocolSupport.Command.WATCH, allocator);
        addRequestArgument(key1, cb, allocator);
        addRequestArgument(key2, cb, allocator);
        final RedisRequest request = newRequest(RedisProtocolSupport.Command.WATCH, cb);
        final RedisPartitionAttributesBuilder partitionAttributesBuilder = partitionAttributesBuilderFunction
                    .apply(RedisProtocolSupport.Command.WATCH);
        partitionAttributesBuilder.addKey(key1);
        partitionAttributesBuilder.addKey(key2);
        final Single<String> result = partitionedRedisClient.request(partitionAttributesBuilder.build(), request, String.class);
        return result;
    }

    @Override
    public Single<String> watch(@RedisProtocolSupport.Key final CharSequence key1,
                                @RedisProtocolSupport.Key final CharSequence key2,
                                @RedisProtocolSupport.Key final CharSequence key3) {
        requireNonNull(key1);
        requireNonNull(key2);
        requireNonNull(key3);
        final BufferAllocator allocator = partitionedRedisClient.getBufferAllocator();
        // Compute the number of request arguments, accounting for nullable ones
        int len = 4;
        final CompositeBuffer cb = newRequestCompositeBuffer(len, RedisProtocolSupport.Command.WATCH, allocator);
        addRequestArgument(key1, cb, allocator);
        addRequestArgument(key2, cb, allocator);
        addRequestArgument(key3, cb, allocator);
        final RedisRequest request = newRequest(RedisProtocolSupport.Command.WATCH, cb);
        final RedisPartitionAttributesBuilder partitionAttributesBuilder = partitionAttributesBuilderFunction
                    .apply(RedisProtocolSupport.Command.WATCH);
        partitionAttributesBuilder.addKey(key1);
        partitionAttributesBuilder.addKey(key2);
        partitionAttributesBuilder.addKey(key3);
        final Single<String> result = partitionedRedisClient.request(partitionAttributesBuilder.build(), request, String.class);
        return result;
    }

    @Override
    public Single<String> watch(@RedisProtocolSupport.Key final Collection<? extends CharSequence> keys) {
        requireNonNull(keys);
        final BufferAllocator allocator = partitionedRedisClient.getBufferAllocator();
        // Compute the number of request arguments, accounting for nullable ones
        int len = 1;
        len += keys.size();
        final CompositeBuffer cb = newRequestCompositeBuffer(len, RedisProtocolSupport.Command.WATCH, allocator);
        addRequestCharSequenceArguments(keys, null, cb, allocator);
        final RedisRequest request = newRequest(RedisProtocolSupport.Command.WATCH, cb);
        final RedisPartitionAttributesBuilder partitionAttributesBuilder = partitionAttributesBuilderFunction
                    .apply(RedisProtocolSupport.Command.WATCH);
        addCharSequenceKeysToAttributeBuilder(keys, partitionAttributesBuilder);
        final Single<String> result = partitionedRedisClient.request(partitionAttributesBuilder.build(), request, String.class);
        return result;
    }

    @Override
    public Single<Long> zadd(@RedisProtocolSupport.Key final CharSequence key,
                             final Collection<RedisProtocolSupport.ScoreMember> scoreMembers) {
        requireNonNull(key);
        requireNonNull(scoreMembers);
        final BufferAllocator allocator = partitionedRedisClient.getBufferAllocator();
        // Compute the number of request arguments, accounting for nullable ones
        int len = 2;
        len += RedisProtocolSupport.ScoreMember.SIZE * scoreMembers.size();
        final CompositeBuffer cb = newRequestCompositeBuffer(len, RedisProtocolSupport.Command.ZADD, allocator);
        addRequestArgument(key, cb, allocator);
        addRequestTupleArguments(scoreMembers, null, cb, allocator);
        final RedisRequest request = newRequest(RedisProtocolSupport.Command.ZADD, cb);
        final RedisPartitionAttributesBuilder partitionAttributesBuilder = partitionAttributesBuilderFunction
                    .apply(RedisProtocolSupport.Command.ZADD);
        partitionAttributesBuilder.addKey(key);
        final Single<Long> result = partitionedRedisClient.request(partitionAttributesBuilder.build(), request, Long.class);
        return result;
    }

    @Override
    public Single<Long> zadd(@RedisProtocolSupport.Key final CharSequence key,
                             @Nullable final RedisProtocolSupport.ZaddCondition condition,
                             @Nullable final RedisProtocolSupport.ZaddChange change, final double score,
                             final CharSequence member) {
        requireNonNull(key);
        requireNonNull(member);
        final BufferAllocator allocator = partitionedRedisClient.getBufferAllocator();
        // Compute the number of request arguments, accounting for nullable ones
        int len = 4;
        if (condition != null) {
            len++;
        }
        if (change != null) {
            len++;
        }
        final CompositeBuffer cb = newRequestCompositeBuffer(len, RedisProtocolSupport.Command.ZADD, allocator);
        addRequestArgument(key, cb, allocator);
        if (condition != null) {
            addRequestArgument(condition, cb, allocator);
        }
        if (change != null) {
            addRequestArgument(change, cb, allocator);
        }
        addRequestArgument(score, cb, allocator);
        addRequestArgument(member, cb, allocator);
        final RedisRequest request = newRequest(RedisProtocolSupport.Command.ZADD, cb);
        final RedisPartitionAttributesBuilder partitionAttributesBuilder = partitionAttributesBuilderFunction
                    .apply(RedisProtocolSupport.Command.ZADD);
        partitionAttributesBuilder.addKey(key);
        final Single<Long> result = partitionedRedisClient.request(partitionAttributesBuilder.build(), request, Long.class);
        return result;
    }

    @Override
    public Single<Long> zadd(@RedisProtocolSupport.Key final CharSequence key,
                             @Nullable final RedisProtocolSupport.ZaddCondition condition,
                             @Nullable final RedisProtocolSupport.ZaddChange change, final double score1,
                             final CharSequence member1, final double score2, final CharSequence member2) {
        requireNonNull(key);
        requireNonNull(member1);
        requireNonNull(member2);
        final BufferAllocator allocator = partitionedRedisClient.getBufferAllocator();
        // Compute the number of request arguments, accounting for nullable ones
        int len = 6;
        if (condition != null) {
            len++;
        }
        if (change != null) {
            len++;
        }
        final CompositeBuffer cb = newRequestCompositeBuffer(len, RedisProtocolSupport.Command.ZADD, allocator);
        addRequestArgument(key, cb, allocator);
        if (condition != null) {
            addRequestArgument(condition, cb, allocator);
        }
        if (change != null) {
            addRequestArgument(change, cb, allocator);
        }
        addRequestArgument(score1, cb, allocator);
        addRequestArgument(member1, cb, allocator);
        addRequestArgument(score2, cb, allocator);
        addRequestArgument(member2, cb, allocator);
        final RedisRequest request = newRequest(RedisProtocolSupport.Command.ZADD, cb);
        final RedisPartitionAttributesBuilder partitionAttributesBuilder = partitionAttributesBuilderFunction
                    .apply(RedisProtocolSupport.Command.ZADD);
        partitionAttributesBuilder.addKey(key);
        final Single<Long> result = partitionedRedisClient.request(partitionAttributesBuilder.build(), request, Long.class);
        return result;
    }

    @Override
    public Single<Long> zadd(@RedisProtocolSupport.Key final CharSequence key,
                             @Nullable final RedisProtocolSupport.ZaddCondition condition,
                             @Nullable final RedisProtocolSupport.ZaddChange change, final double score1,
                             final CharSequence member1, final double score2, final CharSequence member2, final double score3,
                             final CharSequence member3) {
        requireNonNull(key);
        requireNonNull(member1);
        requireNonNull(member2);
        requireNonNull(member3);
        final BufferAllocator allocator = partitionedRedisClient.getBufferAllocator();
        // Compute the number of request arguments, accounting for nullable ones
        int len = 8;
        if (condition != null) {
            len++;
        }
        if (change != null) {
            len++;
        }
        final CompositeBuffer cb = newRequestCompositeBuffer(len, RedisProtocolSupport.Command.ZADD, allocator);
        addRequestArgument(key, cb, allocator);
        if (condition != null) {
            addRequestArgument(condition, cb, allocator);
        }
        if (change != null) {
            addRequestArgument(change, cb, allocator);
        }
        addRequestArgument(score1, cb, allocator);
        addRequestArgument(member1, cb, allocator);
        addRequestArgument(score2, cb, allocator);
        addRequestArgument(member2, cb, allocator);
        addRequestArgument(score3, cb, allocator);
        addRequestArgument(member3, cb, allocator);
        final RedisRequest request = newRequest(RedisProtocolSupport.Command.ZADD, cb);
        final RedisPartitionAttributesBuilder partitionAttributesBuilder = partitionAttributesBuilderFunction
                    .apply(RedisProtocolSupport.Command.ZADD);
        partitionAttributesBuilder.addKey(key);
        final Single<Long> result = partitionedRedisClient.request(partitionAttributesBuilder.build(), request, Long.class);
        return result;
    }

    @Override
    public Single<Long> zadd(@RedisProtocolSupport.Key final CharSequence key,
                             @Nullable final RedisProtocolSupport.ZaddCondition condition,
                             @Nullable final RedisProtocolSupport.ZaddChange change,
                             final Collection<RedisProtocolSupport.ScoreMember> scoreMembers) {
        requireNonNull(key);
        requireNonNull(scoreMembers);
        final BufferAllocator allocator = partitionedRedisClient.getBufferAllocator();
        // Compute the number of request arguments, accounting for nullable ones
        int len = 2;
        if (condition != null) {
            len++;
        }
        if (change != null) {
            len++;
        }
        len += RedisProtocolSupport.ScoreMember.SIZE * scoreMembers.size();
        final CompositeBuffer cb = newRequestCompositeBuffer(len, RedisProtocolSupport.Command.ZADD, allocator);
        addRequestArgument(key, cb, allocator);
        if (condition != null) {
            addRequestArgument(condition, cb, allocator);
        }
        if (change != null) {
            addRequestArgument(change, cb, allocator);
        }
        addRequestTupleArguments(scoreMembers, null, cb, allocator);
        final RedisRequest request = newRequest(RedisProtocolSupport.Command.ZADD, cb);
        final RedisPartitionAttributesBuilder partitionAttributesBuilder = partitionAttributesBuilderFunction
                    .apply(RedisProtocolSupport.Command.ZADD);
        partitionAttributesBuilder.addKey(key);
        final Single<Long> result = partitionedRedisClient.request(partitionAttributesBuilder.build(), request, Long.class);
        return result;
    }

    @Override
    public Single<Double> zaddIncr(@RedisProtocolSupport.Key final CharSequence key,
                                   final Collection<RedisProtocolSupport.ScoreMember> scoreMembers) {
        requireNonNull(key);
        requireNonNull(scoreMembers);
        final BufferAllocator allocator = partitionedRedisClient.getBufferAllocator();
        // Compute the number of request arguments, accounting for nullable ones
        int len = 3;
        len += RedisProtocolSupport.ScoreMember.SIZE * scoreMembers.size();
        final CompositeBuffer cb = newRequestCompositeBuffer(len, RedisProtocolSupport.Command.ZADD, allocator);
        addRequestArgument(key, cb, allocator);
        addRequestArgument(RedisProtocolSupport.ZaddIncrement.values()[0], cb, allocator);
        addRequestTupleArguments(scoreMembers, null, cb, allocator);
        final RedisRequest request = newRequest(RedisProtocolSupport.Command.ZADD, cb);
        final RedisPartitionAttributesBuilder partitionAttributesBuilder = partitionAttributesBuilderFunction
                    .apply(RedisProtocolSupport.Command.ZADD);
        partitionAttributesBuilder.addKey(key);
        final Single<Double> result = partitionedRedisClient.request(partitionAttributesBuilder.build(), request, String.class)
                    .map(s -> s != null ? Double.valueOf(s) : null);
        return result;
    }

    @Override
    public Single<Double> zaddIncr(@RedisProtocolSupport.Key final CharSequence key,
                                   @Nullable final RedisProtocolSupport.ZaddCondition condition,
                                   @Nullable final RedisProtocolSupport.ZaddChange change, final double score,
                                   final CharSequence member) {
        requireNonNull(key);
        requireNonNull(member);
        final BufferAllocator allocator = partitionedRedisClient.getBufferAllocator();
        // Compute the number of request arguments, accounting for nullable ones
        int len = 5;
        if (condition != null) {
            len++;
        }
        if (change != null) {
            len++;
        }
        final CompositeBuffer cb = newRequestCompositeBuffer(len, RedisProtocolSupport.Command.ZADD, allocator);
        addRequestArgument(key, cb, allocator);
        addRequestArgument(RedisProtocolSupport.ZaddIncrement.values()[0], cb, allocator);
        if (condition != null) {
            addRequestArgument(condition, cb, allocator);
        }
        if (change != null) {
            addRequestArgument(change, cb, allocator);
        }
        addRequestArgument(score, cb, allocator);
        addRequestArgument(member, cb, allocator);
        final RedisRequest request = newRequest(RedisProtocolSupport.Command.ZADD, cb);
        final RedisPartitionAttributesBuilder partitionAttributesBuilder = partitionAttributesBuilderFunction
                    .apply(RedisProtocolSupport.Command.ZADD);
        partitionAttributesBuilder.addKey(key);
        final Single<Double> result = partitionedRedisClient.request(partitionAttributesBuilder.build(), request, String.class)
                    .map(s -> s != null ? Double.valueOf(s) : null);
        return result;
    }

    @Override
    public Single<Double> zaddIncr(@RedisProtocolSupport.Key final CharSequence key,
                                   @Nullable final RedisProtocolSupport.ZaddCondition condition,
                                   @Nullable final RedisProtocolSupport.ZaddChange change, final double score1,
                                   final CharSequence member1, final double score2, final CharSequence member2) {
        requireNonNull(key);
        requireNonNull(member1);
        requireNonNull(member2);
        final BufferAllocator allocator = partitionedRedisClient.getBufferAllocator();
        // Compute the number of request arguments, accounting for nullable ones
        int len = 7;
        if (condition != null) {
            len++;
        }
        if (change != null) {
            len++;
        }
        final CompositeBuffer cb = newRequestCompositeBuffer(len, RedisProtocolSupport.Command.ZADD, allocator);
        addRequestArgument(key, cb, allocator);
        addRequestArgument(RedisProtocolSupport.ZaddIncrement.values()[0], cb, allocator);
        if (condition != null) {
            addRequestArgument(condition, cb, allocator);
        }
        if (change != null) {
            addRequestArgument(change, cb, allocator);
        }
        addRequestArgument(score1, cb, allocator);
        addRequestArgument(member1, cb, allocator);
        addRequestArgument(score2, cb, allocator);
        addRequestArgument(member2, cb, allocator);
        final RedisRequest request = newRequest(RedisProtocolSupport.Command.ZADD, cb);
        final RedisPartitionAttributesBuilder partitionAttributesBuilder = partitionAttributesBuilderFunction
                    .apply(RedisProtocolSupport.Command.ZADD);
        partitionAttributesBuilder.addKey(key);
        final Single<Double> result = partitionedRedisClient.request(partitionAttributesBuilder.build(), request, String.class)
                    .map(s -> s != null ? Double.valueOf(s) : null);
        return result;
    }

    @Override
    public Single<Double> zaddIncr(@RedisProtocolSupport.Key final CharSequence key,
                                   @Nullable final RedisProtocolSupport.ZaddCondition condition,
                                   @Nullable final RedisProtocolSupport.ZaddChange change, final double score1,
                                   final CharSequence member1, final double score2, final CharSequence member2,
                                   final double score3, final CharSequence member3) {
        requireNonNull(key);
        requireNonNull(member1);
        requireNonNull(member2);
        requireNonNull(member3);
        final BufferAllocator allocator = partitionedRedisClient.getBufferAllocator();
        // Compute the number of request arguments, accounting for nullable ones
        int len = 9;
        if (condition != null) {
            len++;
        }
        if (change != null) {
            len++;
        }
        final CompositeBuffer cb = newRequestCompositeBuffer(len, RedisProtocolSupport.Command.ZADD, allocator);
        addRequestArgument(key, cb, allocator);
        addRequestArgument(RedisProtocolSupport.ZaddIncrement.values()[0], cb, allocator);
        if (condition != null) {
            addRequestArgument(condition, cb, allocator);
        }
        if (change != null) {
            addRequestArgument(change, cb, allocator);
        }
        addRequestArgument(score1, cb, allocator);
        addRequestArgument(member1, cb, allocator);
        addRequestArgument(score2, cb, allocator);
        addRequestArgument(member2, cb, allocator);
        addRequestArgument(score3, cb, allocator);
        addRequestArgument(member3, cb, allocator);
        final RedisRequest request = newRequest(RedisProtocolSupport.Command.ZADD, cb);
        final RedisPartitionAttributesBuilder partitionAttributesBuilder = partitionAttributesBuilderFunction
                    .apply(RedisProtocolSupport.Command.ZADD);
        partitionAttributesBuilder.addKey(key);
        final Single<Double> result = partitionedRedisClient.request(partitionAttributesBuilder.build(), request, String.class)
                    .map(s -> s != null ? Double.valueOf(s) : null);
        return result;
    }

    @Override
    public Single<Double> zaddIncr(@RedisProtocolSupport.Key final CharSequence key,
                                   @Nullable final RedisProtocolSupport.ZaddCondition condition,
                                   @Nullable final RedisProtocolSupport.ZaddChange change,
                                   final Collection<RedisProtocolSupport.ScoreMember> scoreMembers) {
        requireNonNull(key);
        requireNonNull(scoreMembers);
        final BufferAllocator allocator = partitionedRedisClient.getBufferAllocator();
        // Compute the number of request arguments, accounting for nullable ones
        int len = 3;
        if (condition != null) {
            len++;
        }
        if (change != null) {
            len++;
        }
        len += RedisProtocolSupport.ScoreMember.SIZE * scoreMembers.size();
        final CompositeBuffer cb = newRequestCompositeBuffer(len, RedisProtocolSupport.Command.ZADD, allocator);
        addRequestArgument(key, cb, allocator);
        addRequestArgument(RedisProtocolSupport.ZaddIncrement.values()[0], cb, allocator);
        if (condition != null) {
            addRequestArgument(condition, cb, allocator);
        }
        if (change != null) {
            addRequestArgument(change, cb, allocator);
        }
        addRequestTupleArguments(scoreMembers, null, cb, allocator);
        final RedisRequest request = newRequest(RedisProtocolSupport.Command.ZADD, cb);
        final RedisPartitionAttributesBuilder partitionAttributesBuilder = partitionAttributesBuilderFunction
                    .apply(RedisProtocolSupport.Command.ZADD);
        partitionAttributesBuilder.addKey(key);
        final Single<Double> result = partitionedRedisClient.request(partitionAttributesBuilder.build(), request, String.class)
                    .map(s -> s != null ? Double.valueOf(s) : null);
        return result;
    }

    @Override
    public Single<Long> zcard(@RedisProtocolSupport.Key final CharSequence key) {
        requireNonNull(key);
        final BufferAllocator allocator = partitionedRedisClient.getBufferAllocator();
        // Compute the number of request arguments, accounting for nullable ones
        int len = 2;
        final CompositeBuffer cb = newRequestCompositeBuffer(len, RedisProtocolSupport.Command.ZCARD, allocator);
        addRequestArgument(key, cb, allocator);
        final RedisRequest request = newRequest(RedisProtocolSupport.Command.ZCARD, cb);
        final RedisPartitionAttributesBuilder partitionAttributesBuilder = partitionAttributesBuilderFunction
                    .apply(RedisProtocolSupport.Command.ZCARD);
        partitionAttributesBuilder.addKey(key);
        final Single<Long> result = partitionedRedisClient.request(partitionAttributesBuilder.build(), request, Long.class);
        return result;
    }

    @Override
    public Single<Long> zcount(@RedisProtocolSupport.Key final CharSequence key, final double min, final double max) {
        requireNonNull(key);
        final BufferAllocator allocator = partitionedRedisClient.getBufferAllocator();
        // Compute the number of request arguments, accounting for nullable ones
        int len = 4;
        final CompositeBuffer cb = newRequestCompositeBuffer(len, RedisProtocolSupport.Command.ZCOUNT, allocator);
        addRequestArgument(key, cb, allocator);
        addRequestArgument(min, cb, allocator);
        addRequestArgument(max, cb, allocator);
        final RedisRequest request = newRequest(RedisProtocolSupport.Command.ZCOUNT, cb);
        final RedisPartitionAttributesBuilder partitionAttributesBuilder = partitionAttributesBuilderFunction
                    .apply(RedisProtocolSupport.Command.ZCOUNT);
        partitionAttributesBuilder.addKey(key);
        final Single<Long> result = partitionedRedisClient.request(partitionAttributesBuilder.build(), request, Long.class);
        return result;
    }

    @Override
    public Single<Double> zincrby(@RedisProtocolSupport.Key final CharSequence key, final long increment,
                                  final CharSequence member) {
        requireNonNull(key);
        requireNonNull(member);
        final BufferAllocator allocator = partitionedRedisClient.getBufferAllocator();
        // Compute the number of request arguments, accounting for nullable ones
        int len = 4;
        final CompositeBuffer cb = newRequestCompositeBuffer(len, RedisProtocolSupport.Command.ZINCRBY, allocator);
        addRequestArgument(key, cb, allocator);
        addRequestArgument(increment, cb, allocator);
        addRequestArgument(member, cb, allocator);
        final RedisRequest request = newRequest(RedisProtocolSupport.Command.ZINCRBY, cb);
        final RedisPartitionAttributesBuilder partitionAttributesBuilder = partitionAttributesBuilderFunction
                    .apply(RedisProtocolSupport.Command.ZINCRBY);
        partitionAttributesBuilder.addKey(key);
        final Single<Double> result = partitionedRedisClient.request(partitionAttributesBuilder.build(), request, String.class)
                    .map(s -> s != null ? Double.valueOf(s) : null);
        return result;
    }

    @Override
    public Single<Long> zinterstore(@RedisProtocolSupport.Key final CharSequence destination, final long numkeys,
                                    @RedisProtocolSupport.Key final Collection<? extends CharSequence> keys) {
        requireNonNull(destination);
        requireNonNull(keys);
        final BufferAllocator allocator = partitionedRedisClient.getBufferAllocator();
        // Compute the number of request arguments, accounting for nullable ones
        int len = 3;
        len += keys.size();
        final CompositeBuffer cb = newRequestCompositeBuffer(len, RedisProtocolSupport.Command.ZINTERSTORE, allocator);
        addRequestArgument(destination, cb, allocator);
        addRequestArgument(numkeys, cb, allocator);
        addRequestCharSequenceArguments(keys, null, cb, allocator);
        final RedisRequest request = newRequest(RedisProtocolSupport.Command.ZINTERSTORE, cb);
        final RedisPartitionAttributesBuilder partitionAttributesBuilder = partitionAttributesBuilderFunction
                    .apply(RedisProtocolSupport.Command.ZINTERSTORE);
        partitionAttributesBuilder.addKey(destination);
        addCharSequenceKeysToAttributeBuilder(keys, partitionAttributesBuilder);
        final Single<Long> result = partitionedRedisClient.request(partitionAttributesBuilder.build(), request, Long.class);
        return result;
    }

    @Override
    public Single<Long> zinterstore(@RedisProtocolSupport.Key final CharSequence destination, final long numkeys,
                                    @RedisProtocolSupport.Key final Collection<? extends CharSequence> keys,
                                    final Collection<Long> weightses,
                                    @Nullable final RedisProtocolSupport.ZinterstoreAggregate aggregate) {
        requireNonNull(destination);
        requireNonNull(keys);
        requireNonNull(weightses);
        final BufferAllocator allocator = partitionedRedisClient.getBufferAllocator();
        // Compute the number of request arguments, accounting for nullable ones
        int len = 3;
        len += keys.size();
        len += 2 * weightses.size();
        if (aggregate != null) {
            len++;
        }
        final CompositeBuffer cb = newRequestCompositeBuffer(len, RedisProtocolSupport.Command.ZINTERSTORE, allocator);
        addRequestArgument(destination, cb, allocator);
        addRequestArgument(numkeys, cb, allocator);
        addRequestCharSequenceArguments(keys, null, cb, allocator);
        addRequestLongArguments(weightses, RedisProtocolSupport.SubCommand.WEIGHTS, cb, allocator);
        if (aggregate != null) {
            addRequestArgument(aggregate, cb, allocator);
        }
        final RedisRequest request = newRequest(RedisProtocolSupport.Command.ZINTERSTORE, cb);
        final RedisPartitionAttributesBuilder partitionAttributesBuilder = partitionAttributesBuilderFunction
                    .apply(RedisProtocolSupport.Command.ZINTERSTORE);
        partitionAttributesBuilder.addKey(destination);
        addCharSequenceKeysToAttributeBuilder(keys, partitionAttributesBuilder);
        final Single<Long> result = partitionedRedisClient.request(partitionAttributesBuilder.build(), request, Long.class);
        return result;
    }

    @Override
    public Single<Long> zlexcount(@RedisProtocolSupport.Key final CharSequence key, final CharSequence min,
                                  final CharSequence max) {
        requireNonNull(key);
        requireNonNull(min);
        requireNonNull(max);
        final BufferAllocator allocator = partitionedRedisClient.getBufferAllocator();
        // Compute the number of request arguments, accounting for nullable ones
        int len = 4;
        final CompositeBuffer cb = newRequestCompositeBuffer(len, RedisProtocolSupport.Command.ZLEXCOUNT, allocator);
        addRequestArgument(key, cb, allocator);
        addRequestArgument(min, cb, allocator);
        addRequestArgument(max, cb, allocator);
        final RedisRequest request = newRequest(RedisProtocolSupport.Command.ZLEXCOUNT, cb);
        final RedisPartitionAttributesBuilder partitionAttributesBuilder = partitionAttributesBuilderFunction
                    .apply(RedisProtocolSupport.Command.ZLEXCOUNT);
        partitionAttributesBuilder.addKey(key);
        final Single<Long> result = partitionedRedisClient.request(partitionAttributesBuilder.build(), request, Long.class);
        return result;
    }

    @Override
    public <T> Single<List<T>> zrange(@RedisProtocolSupport.Key final CharSequence key, final long start, final long stop) {
        requireNonNull(key);
        final BufferAllocator allocator = partitionedRedisClient.getBufferAllocator();
        // Compute the number of request arguments, accounting for nullable ones
        int len = 4;
        final CompositeBuffer cb = newRequestCompositeBuffer(len, RedisProtocolSupport.Command.ZRANGE, allocator);
        addRequestArgument(key, cb, allocator);
        addRequestArgument(start, cb, allocator);
        addRequestArgument(stop, cb, allocator);
        final RedisRequest request = newRequest(RedisProtocolSupport.Command.ZRANGE, cb);
        final RedisPartitionAttributesBuilder partitionAttributesBuilder = partitionAttributesBuilderFunction
                    .apply(RedisProtocolSupport.Command.ZRANGE);
        partitionAttributesBuilder.addKey(key);
        final Single<List<T>> result = (Single) partitionedRedisClient.request(partitionAttributesBuilder.build(), request,
                    RedisUtils.ListWithBuffersCoercedToCharSequences.class);
        return result;
    }

    @Override
    public <T> Single<List<T>> zrange(@RedisProtocolSupport.Key final CharSequence key, final long start, final long stop,
                                      @Nullable final RedisProtocolSupport.ZrangeWithscores withscores) {
        requireNonNull(key);
        final BufferAllocator allocator = partitionedRedisClient.getBufferAllocator();
        // Compute the number of request arguments, accounting for nullable ones
        int len = 4;
        if (withscores != null) {
            len++;
        }
        final CompositeBuffer cb = newRequestCompositeBuffer(len, RedisProtocolSupport.Command.ZRANGE, allocator);
        addRequestArgument(key, cb, allocator);
        addRequestArgument(start, cb, allocator);
        addRequestArgument(stop, cb, allocator);
        if (withscores != null) {
            addRequestArgument(withscores, cb, allocator);
        }
        final RedisRequest request = newRequest(RedisProtocolSupport.Command.ZRANGE, cb);
        final RedisPartitionAttributesBuilder partitionAttributesBuilder = partitionAttributesBuilderFunction
                    .apply(RedisProtocolSupport.Command.ZRANGE);
        partitionAttributesBuilder.addKey(key);
        final Single<List<T>> result = (Single) partitionedRedisClient.request(partitionAttributesBuilder.build(), request,
                    RedisUtils.ListWithBuffersCoercedToCharSequences.class);
        return result;
    }

    @Override
    public <T> Single<List<T>> zrangebylex(@RedisProtocolSupport.Key final CharSequence key, final CharSequence min,
                                           final CharSequence max) {
        requireNonNull(key);
        requireNonNull(min);
        requireNonNull(max);
        final BufferAllocator allocator = partitionedRedisClient.getBufferAllocator();
        // Compute the number of request arguments, accounting for nullable ones
        int len = 4;
        final CompositeBuffer cb = newRequestCompositeBuffer(len, RedisProtocolSupport.Command.ZRANGEBYLEX, allocator);
        addRequestArgument(key, cb, allocator);
        addRequestArgument(min, cb, allocator);
        addRequestArgument(max, cb, allocator);
        final RedisRequest request = newRequest(RedisProtocolSupport.Command.ZRANGEBYLEX, cb);
        final RedisPartitionAttributesBuilder partitionAttributesBuilder = partitionAttributesBuilderFunction
                    .apply(RedisProtocolSupport.Command.ZRANGEBYLEX);
        partitionAttributesBuilder.addKey(key);
        final Single<List<T>> result = (Single) partitionedRedisClient.request(partitionAttributesBuilder.build(), request,
                    RedisUtils.ListWithBuffersCoercedToCharSequences.class);
        return result;
    }

    @Override
    public <T> Single<List<T>> zrangebylex(@RedisProtocolSupport.Key final CharSequence key, final CharSequence min,
                                           final CharSequence max, @Nullable final RedisProtocolSupport.OffsetCount offsetCount) {
        requireNonNull(key);
        requireNonNull(min);
        requireNonNull(max);
        final BufferAllocator allocator = partitionedRedisClient.getBufferAllocator();
        // Compute the number of request arguments, accounting for nullable ones
        int len = 4;
        if (offsetCount != null) {
            len += RedisProtocolSupport.OffsetCount.SIZE;
        }
        final CompositeBuffer cb = newRequestCompositeBuffer(len, RedisProtocolSupport.Command.ZRANGEBYLEX, allocator);
        addRequestArgument(key, cb, allocator);
        addRequestArgument(min, cb, allocator);
        addRequestArgument(max, cb, allocator);
        if (offsetCount != null) {
            offsetCount.writeTo(cb, allocator);
        }
        final RedisRequest request = newRequest(RedisProtocolSupport.Command.ZRANGEBYLEX, cb);
        final RedisPartitionAttributesBuilder partitionAttributesBuilder = partitionAttributesBuilderFunction
                    .apply(RedisProtocolSupport.Command.ZRANGEBYLEX);
        partitionAttributesBuilder.addKey(key);
        final Single<List<T>> result = (Single) partitionedRedisClient.request(partitionAttributesBuilder.build(), request,
                    RedisUtils.ListWithBuffersCoercedToCharSequences.class);
        return result;
    }

    @Override
    public <T> Single<List<T>> zrangebyscore(@RedisProtocolSupport.Key final CharSequence key, final double min,
                                             final double max) {
        requireNonNull(key);
        final BufferAllocator allocator = partitionedRedisClient.getBufferAllocator();
        // Compute the number of request arguments, accounting for nullable ones
        int len = 4;
        final CompositeBuffer cb = newRequestCompositeBuffer(len, RedisProtocolSupport.Command.ZRANGEBYSCORE, allocator);
        addRequestArgument(key, cb, allocator);
        addRequestArgument(min, cb, allocator);
        addRequestArgument(max, cb, allocator);
        final RedisRequest request = newRequest(RedisProtocolSupport.Command.ZRANGEBYSCORE, cb);
        final RedisPartitionAttributesBuilder partitionAttributesBuilder = partitionAttributesBuilderFunction
                    .apply(RedisProtocolSupport.Command.ZRANGEBYSCORE);
        partitionAttributesBuilder.addKey(key);
        final Single<List<T>> result = (Single) partitionedRedisClient.request(partitionAttributesBuilder.build(), request,
                    RedisUtils.ListWithBuffersCoercedToCharSequences.class);
        return result;
    }

    @Override
    public <T> Single<List<T>> zrangebyscore(@RedisProtocolSupport.Key final CharSequence key, final double min, final double max,
                                             @Nullable final RedisProtocolSupport.ZrangebyscoreWithscores withscores,
                                             @Nullable final RedisProtocolSupport.OffsetCount offsetCount) {
        requireNonNull(key);
        final BufferAllocator allocator = partitionedRedisClient.getBufferAllocator();
        // Compute the number of request arguments, accounting for nullable ones
        int len = 4;
        if (withscores != null) {
            len++;
        }
        if (offsetCount != null) {
            len += RedisProtocolSupport.OffsetCount.SIZE;
        }
        final CompositeBuffer cb = newRequestCompositeBuffer(len, RedisProtocolSupport.Command.ZRANGEBYSCORE, allocator);
        addRequestArgument(key, cb, allocator);
        addRequestArgument(min, cb, allocator);
        addRequestArgument(max, cb, allocator);
        if (withscores != null) {
            addRequestArgument(withscores, cb, allocator);
        }
        if (offsetCount != null) {
            offsetCount.writeTo(cb, allocator);
        }
        final RedisRequest request = newRequest(RedisProtocolSupport.Command.ZRANGEBYSCORE, cb);
        final RedisPartitionAttributesBuilder partitionAttributesBuilder = partitionAttributesBuilderFunction
                    .apply(RedisProtocolSupport.Command.ZRANGEBYSCORE);
        partitionAttributesBuilder.addKey(key);
        final Single<List<T>> result = (Single) partitionedRedisClient.request(partitionAttributesBuilder.build(), request,
                    RedisUtils.ListWithBuffersCoercedToCharSequences.class);
        return result;
    }

    @Override
    public Single<Long> zrank(@RedisProtocolSupport.Key final CharSequence key, final CharSequence member) {
        requireNonNull(key);
        requireNonNull(member);
        final BufferAllocator allocator = partitionedRedisClient.getBufferAllocator();
        // Compute the number of request arguments, accounting for nullable ones
        int len = 3;
        final CompositeBuffer cb = newRequestCompositeBuffer(len, RedisProtocolSupport.Command.ZRANK, allocator);
        addRequestArgument(key, cb, allocator);
        addRequestArgument(member, cb, allocator);
        final RedisRequest request = newRequest(RedisProtocolSupport.Command.ZRANK, cb);
        final RedisPartitionAttributesBuilder partitionAttributesBuilder = partitionAttributesBuilderFunction
                    .apply(RedisProtocolSupport.Command.ZRANK);
        partitionAttributesBuilder.addKey(key);
        final Single<Long> result = partitionedRedisClient.request(partitionAttributesBuilder.build(), request, Long.class);
        return result;
    }

    @Override
    public Single<Long> zrem(@RedisProtocolSupport.Key final CharSequence key, final CharSequence member) {
        requireNonNull(key);
        requireNonNull(member);
        final BufferAllocator allocator = partitionedRedisClient.getBufferAllocator();
        // Compute the number of request arguments, accounting for nullable ones
        int len = 3;
        final CompositeBuffer cb = newRequestCompositeBuffer(len, RedisProtocolSupport.Command.ZREM, allocator);
        addRequestArgument(key, cb, allocator);
        addRequestArgument(member, cb, allocator);
        final RedisRequest request = newRequest(RedisProtocolSupport.Command.ZREM, cb);
        final RedisPartitionAttributesBuilder partitionAttributesBuilder = partitionAttributesBuilderFunction
                    .apply(RedisProtocolSupport.Command.ZREM);
        partitionAttributesBuilder.addKey(key);
        final Single<Long> result = partitionedRedisClient.request(partitionAttributesBuilder.build(), request, Long.class);
        return result;
    }

    @Override
    public Single<Long> zrem(@RedisProtocolSupport.Key final CharSequence key, final CharSequence member1,
                             final CharSequence member2) {
        requireNonNull(key);
        requireNonNull(member1);
        requireNonNull(member2);
        final BufferAllocator allocator = partitionedRedisClient.getBufferAllocator();
        // Compute the number of request arguments, accounting for nullable ones
        int len = 4;
        final CompositeBuffer cb = newRequestCompositeBuffer(len, RedisProtocolSupport.Command.ZREM, allocator);
        addRequestArgument(key, cb, allocator);
        addRequestArgument(member1, cb, allocator);
        addRequestArgument(member2, cb, allocator);
        final RedisRequest request = newRequest(RedisProtocolSupport.Command.ZREM, cb);
        final RedisPartitionAttributesBuilder partitionAttributesBuilder = partitionAttributesBuilderFunction
                    .apply(RedisProtocolSupport.Command.ZREM);
        partitionAttributesBuilder.addKey(key);
        final Single<Long> result = partitionedRedisClient.request(partitionAttributesBuilder.build(), request, Long.class);
        return result;
    }

    @Override
    public Single<Long> zrem(@RedisProtocolSupport.Key final CharSequence key, final CharSequence member1,
                             final CharSequence member2, final CharSequence member3) {
        requireNonNull(key);
        requireNonNull(member1);
        requireNonNull(member2);
        requireNonNull(member3);
        final BufferAllocator allocator = partitionedRedisClient.getBufferAllocator();
        // Compute the number of request arguments, accounting for nullable ones
        int len = 5;
        final CompositeBuffer cb = newRequestCompositeBuffer(len, RedisProtocolSupport.Command.ZREM, allocator);
        addRequestArgument(key, cb, allocator);
        addRequestArgument(member1, cb, allocator);
        addRequestArgument(member2, cb, allocator);
        addRequestArgument(member3, cb, allocator);
        final RedisRequest request = newRequest(RedisProtocolSupport.Command.ZREM, cb);
        final RedisPartitionAttributesBuilder partitionAttributesBuilder = partitionAttributesBuilderFunction
                    .apply(RedisProtocolSupport.Command.ZREM);
        partitionAttributesBuilder.addKey(key);
        final Single<Long> result = partitionedRedisClient.request(partitionAttributesBuilder.build(), request, Long.class);
        return result;
    }

    @Override
    public Single<Long> zrem(@RedisProtocolSupport.Key final CharSequence key, final Collection<? extends CharSequence> members) {
        requireNonNull(key);
        requireNonNull(members);
        final BufferAllocator allocator = partitionedRedisClient.getBufferAllocator();
        // Compute the number of request arguments, accounting for nullable ones
        int len = 2;
        len += members.size();
        final CompositeBuffer cb = newRequestCompositeBuffer(len, RedisProtocolSupport.Command.ZREM, allocator);
        addRequestArgument(key, cb, allocator);
        addRequestCharSequenceArguments(members, null, cb, allocator);
        final RedisRequest request = newRequest(RedisProtocolSupport.Command.ZREM, cb);
        final RedisPartitionAttributesBuilder partitionAttributesBuilder = partitionAttributesBuilderFunction
                    .apply(RedisProtocolSupport.Command.ZREM);
        partitionAttributesBuilder.addKey(key);
        final Single<Long> result = partitionedRedisClient.request(partitionAttributesBuilder.build(), request, Long.class);
        return result;
    }

    @Override
    public Single<Long> zremrangebylex(@RedisProtocolSupport.Key final CharSequence key, final CharSequence min,
                                       final CharSequence max) {
        requireNonNull(key);
        requireNonNull(min);
        requireNonNull(max);
        final BufferAllocator allocator = partitionedRedisClient.getBufferAllocator();
        // Compute the number of request arguments, accounting for nullable ones
        int len = 4;
        final CompositeBuffer cb = newRequestCompositeBuffer(len, RedisProtocolSupport.Command.ZREMRANGEBYLEX, allocator);
        addRequestArgument(key, cb, allocator);
        addRequestArgument(min, cb, allocator);
        addRequestArgument(max, cb, allocator);
        final RedisRequest request = newRequest(RedisProtocolSupport.Command.ZREMRANGEBYLEX, cb);
        final RedisPartitionAttributesBuilder partitionAttributesBuilder = partitionAttributesBuilderFunction
                    .apply(RedisProtocolSupport.Command.ZREMRANGEBYLEX);
        partitionAttributesBuilder.addKey(key);
        final Single<Long> result = partitionedRedisClient.request(partitionAttributesBuilder.build(), request, Long.class);
        return result;
    }

    @Override
    public Single<Long> zremrangebyrank(@RedisProtocolSupport.Key final CharSequence key, final long start, final long stop) {
        requireNonNull(key);
        final BufferAllocator allocator = partitionedRedisClient.getBufferAllocator();
        // Compute the number of request arguments, accounting for nullable ones
        int len = 4;
        final CompositeBuffer cb = newRequestCompositeBuffer(len, RedisProtocolSupport.Command.ZREMRANGEBYRANK, allocator);
        addRequestArgument(key, cb, allocator);
        addRequestArgument(start, cb, allocator);
        addRequestArgument(stop, cb, allocator);
        final RedisRequest request = newRequest(RedisProtocolSupport.Command.ZREMRANGEBYRANK, cb);
        final RedisPartitionAttributesBuilder partitionAttributesBuilder = partitionAttributesBuilderFunction
                    .apply(RedisProtocolSupport.Command.ZREMRANGEBYRANK);
        partitionAttributesBuilder.addKey(key);
        final Single<Long> result = partitionedRedisClient.request(partitionAttributesBuilder.build(), request, Long.class);
        return result;
    }

    @Override
    public Single<Long> zremrangebyscore(@RedisProtocolSupport.Key final CharSequence key, final double min, final double max) {
        requireNonNull(key);
        final BufferAllocator allocator = partitionedRedisClient.getBufferAllocator();
        // Compute the number of request arguments, accounting for nullable ones
        int len = 4;
        final CompositeBuffer cb = newRequestCompositeBuffer(len, RedisProtocolSupport.Command.ZREMRANGEBYSCORE, allocator);
        addRequestArgument(key, cb, allocator);
        addRequestArgument(min, cb, allocator);
        addRequestArgument(max, cb, allocator);
        final RedisRequest request = newRequest(RedisProtocolSupport.Command.ZREMRANGEBYSCORE, cb);
        final RedisPartitionAttributesBuilder partitionAttributesBuilder = partitionAttributesBuilderFunction
                    .apply(RedisProtocolSupport.Command.ZREMRANGEBYSCORE);
        partitionAttributesBuilder.addKey(key);
        final Single<Long> result = partitionedRedisClient.request(partitionAttributesBuilder.build(), request, Long.class);
        return result;
    }

    @Override
    public <T> Single<List<T>> zrevrange(@RedisProtocolSupport.Key final CharSequence key, final long start, final long stop) {
        requireNonNull(key);
        final BufferAllocator allocator = partitionedRedisClient.getBufferAllocator();
        // Compute the number of request arguments, accounting for nullable ones
        int len = 4;
        final CompositeBuffer cb = newRequestCompositeBuffer(len, RedisProtocolSupport.Command.ZREVRANGE, allocator);
        addRequestArgument(key, cb, allocator);
        addRequestArgument(start, cb, allocator);
        addRequestArgument(stop, cb, allocator);
        final RedisRequest request = newRequest(RedisProtocolSupport.Command.ZREVRANGE, cb);
        final RedisPartitionAttributesBuilder partitionAttributesBuilder = partitionAttributesBuilderFunction
                    .apply(RedisProtocolSupport.Command.ZREVRANGE);
        partitionAttributesBuilder.addKey(key);
        final Single<List<T>> result = (Single) partitionedRedisClient.request(partitionAttributesBuilder.build(), request,
                    RedisUtils.ListWithBuffersCoercedToCharSequences.class);
        return result;
    }

    @Override
    public <T> Single<List<T>> zrevrange(@RedisProtocolSupport.Key final CharSequence key, final long start, final long stop,
                                         @Nullable final RedisProtocolSupport.ZrevrangeWithscores withscores) {
        requireNonNull(key);
        final BufferAllocator allocator = partitionedRedisClient.getBufferAllocator();
        // Compute the number of request arguments, accounting for nullable ones
        int len = 4;
        if (withscores != null) {
            len++;
        }
        final CompositeBuffer cb = newRequestCompositeBuffer(len, RedisProtocolSupport.Command.ZREVRANGE, allocator);
        addRequestArgument(key, cb, allocator);
        addRequestArgument(start, cb, allocator);
        addRequestArgument(stop, cb, allocator);
        if (withscores != null) {
            addRequestArgument(withscores, cb, allocator);
        }
        final RedisRequest request = newRequest(RedisProtocolSupport.Command.ZREVRANGE, cb);
        final RedisPartitionAttributesBuilder partitionAttributesBuilder = partitionAttributesBuilderFunction
                    .apply(RedisProtocolSupport.Command.ZREVRANGE);
        partitionAttributesBuilder.addKey(key);
        final Single<List<T>> result = (Single) partitionedRedisClient.request(partitionAttributesBuilder.build(), request,
                    RedisUtils.ListWithBuffersCoercedToCharSequences.class);
        return result;
    }

    @Override
    public <T> Single<List<T>> zrevrangebylex(@RedisProtocolSupport.Key final CharSequence key, final CharSequence max,
                                              final CharSequence min) {
        requireNonNull(key);
        requireNonNull(max);
        requireNonNull(min);
        final BufferAllocator allocator = partitionedRedisClient.getBufferAllocator();
        // Compute the number of request arguments, accounting for nullable ones
        int len = 4;
        final CompositeBuffer cb = newRequestCompositeBuffer(len, RedisProtocolSupport.Command.ZREVRANGEBYLEX, allocator);
        addRequestArgument(key, cb, allocator);
        addRequestArgument(max, cb, allocator);
        addRequestArgument(min, cb, allocator);
        final RedisRequest request = newRequest(RedisProtocolSupport.Command.ZREVRANGEBYLEX, cb);
        final RedisPartitionAttributesBuilder partitionAttributesBuilder = partitionAttributesBuilderFunction
                    .apply(RedisProtocolSupport.Command.ZREVRANGEBYLEX);
        partitionAttributesBuilder.addKey(key);
        final Single<List<T>> result = (Single) partitionedRedisClient.request(partitionAttributesBuilder.build(), request,
                    RedisUtils.ListWithBuffersCoercedToCharSequences.class);
        return result;
    }

    @Override
    public <T> Single<List<T>> zrevrangebylex(@RedisProtocolSupport.Key final CharSequence key, final CharSequence max,
                                              final CharSequence min,
                                              @Nullable final RedisProtocolSupport.OffsetCount offsetCount) {
        requireNonNull(key);
        requireNonNull(max);
        requireNonNull(min);
        final BufferAllocator allocator = partitionedRedisClient.getBufferAllocator();
        // Compute the number of request arguments, accounting for nullable ones
        int len = 4;
        if (offsetCount != null) {
            len += RedisProtocolSupport.OffsetCount.SIZE;
        }
        final CompositeBuffer cb = newRequestCompositeBuffer(len, RedisProtocolSupport.Command.ZREVRANGEBYLEX, allocator);
        addRequestArgument(key, cb, allocator);
        addRequestArgument(max, cb, allocator);
        addRequestArgument(min, cb, allocator);
        if (offsetCount != null) {
            offsetCount.writeTo(cb, allocator);
        }
        final RedisRequest request = newRequest(RedisProtocolSupport.Command.ZREVRANGEBYLEX, cb);
        final RedisPartitionAttributesBuilder partitionAttributesBuilder = partitionAttributesBuilderFunction
                    .apply(RedisProtocolSupport.Command.ZREVRANGEBYLEX);
        partitionAttributesBuilder.addKey(key);
        final Single<List<T>> result = (Single) partitionedRedisClient.request(partitionAttributesBuilder.build(), request,
                    RedisUtils.ListWithBuffersCoercedToCharSequences.class);
        return result;
    }

    @Override
    public <T> Single<List<T>> zrevrangebyscore(@RedisProtocolSupport.Key final CharSequence key, final double max,
                                                final double min) {
        requireNonNull(key);
        final BufferAllocator allocator = partitionedRedisClient.getBufferAllocator();
        // Compute the number of request arguments, accounting for nullable ones
        int len = 4;
        final CompositeBuffer cb = newRequestCompositeBuffer(len, RedisProtocolSupport.Command.ZREVRANGEBYSCORE, allocator);
        addRequestArgument(key, cb, allocator);
        addRequestArgument(max, cb, allocator);
        addRequestArgument(min, cb, allocator);
        final RedisRequest request = newRequest(RedisProtocolSupport.Command.ZREVRANGEBYSCORE, cb);
        final RedisPartitionAttributesBuilder partitionAttributesBuilder = partitionAttributesBuilderFunction
                    .apply(RedisProtocolSupport.Command.ZREVRANGEBYSCORE);
        partitionAttributesBuilder.addKey(key);
        final Single<List<T>> result = (Single) partitionedRedisClient.request(partitionAttributesBuilder.build(), request,
                    RedisUtils.ListWithBuffersCoercedToCharSequences.class);
        return result;
    }

    @Override
    public <T> Single<List<T>> zrevrangebyscore(@RedisProtocolSupport.Key final CharSequence key, final double max,
                                                final double min,
                                                @Nullable final RedisProtocolSupport.ZrevrangebyscoreWithscores withscores,
                                                @Nullable final RedisProtocolSupport.OffsetCount offsetCount) {
        requireNonNull(key);
        final BufferAllocator allocator = partitionedRedisClient.getBufferAllocator();
        // Compute the number of request arguments, accounting for nullable ones
        int len = 4;
        if (withscores != null) {
            len++;
        }
        if (offsetCount != null) {
            len += RedisProtocolSupport.OffsetCount.SIZE;
        }
        final CompositeBuffer cb = newRequestCompositeBuffer(len, RedisProtocolSupport.Command.ZREVRANGEBYSCORE, allocator);
        addRequestArgument(key, cb, allocator);
        addRequestArgument(max, cb, allocator);
        addRequestArgument(min, cb, allocator);
        if (withscores != null) {
            addRequestArgument(withscores, cb, allocator);
        }
        if (offsetCount != null) {
            offsetCount.writeTo(cb, allocator);
        }
        final RedisRequest request = newRequest(RedisProtocolSupport.Command.ZREVRANGEBYSCORE, cb);
        final RedisPartitionAttributesBuilder partitionAttributesBuilder = partitionAttributesBuilderFunction
                    .apply(RedisProtocolSupport.Command.ZREVRANGEBYSCORE);
        partitionAttributesBuilder.addKey(key);
        final Single<List<T>> result = (Single) partitionedRedisClient.request(partitionAttributesBuilder.build(), request,
                    RedisUtils.ListWithBuffersCoercedToCharSequences.class);
        return result;
    }

    @Override
    public Single<Long> zrevrank(@RedisProtocolSupport.Key final CharSequence key, final CharSequence member) {
        requireNonNull(key);
        requireNonNull(member);
        final BufferAllocator allocator = partitionedRedisClient.getBufferAllocator();
        // Compute the number of request arguments, accounting for nullable ones
        int len = 3;
        final CompositeBuffer cb = newRequestCompositeBuffer(len, RedisProtocolSupport.Command.ZREVRANK, allocator);
        addRequestArgument(key, cb, allocator);
        addRequestArgument(member, cb, allocator);
        final RedisRequest request = newRequest(RedisProtocolSupport.Command.ZREVRANK, cb);
        final RedisPartitionAttributesBuilder partitionAttributesBuilder = partitionAttributesBuilderFunction
                    .apply(RedisProtocolSupport.Command.ZREVRANK);
        partitionAttributesBuilder.addKey(key);
        final Single<Long> result = partitionedRedisClient.request(partitionAttributesBuilder.build(), request, Long.class);
        return result;
    }

    @Override
    public <T> Single<List<T>> zscan(@RedisProtocolSupport.Key final CharSequence key, final long cursor) {
        requireNonNull(key);
        final BufferAllocator allocator = partitionedRedisClient.getBufferAllocator();
        // Compute the number of request arguments, accounting for nullable ones
        int len = 3;
        final CompositeBuffer cb = newRequestCompositeBuffer(len, RedisProtocolSupport.Command.ZSCAN, allocator);
        addRequestArgument(key, cb, allocator);
        addRequestArgument(cursor, cb, allocator);
        final RedisRequest request = newRequest(RedisProtocolSupport.Command.ZSCAN, cb);
        final RedisPartitionAttributesBuilder partitionAttributesBuilder = partitionAttributesBuilderFunction
                    .apply(RedisProtocolSupport.Command.ZSCAN);
        partitionAttributesBuilder.addKey(key);
        final Single<List<T>> result = (Single) partitionedRedisClient.request(partitionAttributesBuilder.build(), request,
                    RedisUtils.ListWithBuffersCoercedToCharSequences.class);
        return result;
    }

    @Override
    public <T> Single<List<T>> zscan(@RedisProtocolSupport.Key final CharSequence key, final long cursor,
                                     @Nullable final CharSequence matchPattern, @Nullable final Long count) {
        requireNonNull(key);
        final BufferAllocator allocator = partitionedRedisClient.getBufferAllocator();
        // Compute the number of request arguments, accounting for nullable ones
        int len = 3;
        if (matchPattern != null) {
            len += 2;
        }
        if (count != null) {
            len += 2;
        }
        final CompositeBuffer cb = newRequestCompositeBuffer(len, RedisProtocolSupport.Command.ZSCAN, allocator);
        addRequestArgument(key, cb, allocator);
        addRequestArgument(cursor, cb, allocator);
        if (matchPattern != null) {
            addRequestArgument(RedisProtocolSupport.SubCommand.MATCH, cb, allocator);
            addRequestArgument(matchPattern, cb, allocator);
        }
        if (count != null) {
            addRequestArgument(RedisProtocolSupport.SubCommand.COUNT, cb, allocator);
            addRequestArgument(count, cb, allocator);
        }
        final RedisRequest request = newRequest(RedisProtocolSupport.Command.ZSCAN, cb);
        final RedisPartitionAttributesBuilder partitionAttributesBuilder = partitionAttributesBuilderFunction
                    .apply(RedisProtocolSupport.Command.ZSCAN);
        partitionAttributesBuilder.addKey(key);
        final Single<List<T>> result = (Single) partitionedRedisClient.request(partitionAttributesBuilder.build(), request,
                    RedisUtils.ListWithBuffersCoercedToCharSequences.class);
        return result;
    }

    @Override
    public Single<Double> zscore(@RedisProtocolSupport.Key final CharSequence key, final CharSequence member) {
        requireNonNull(key);
        requireNonNull(member);
        final BufferAllocator allocator = partitionedRedisClient.getBufferAllocator();
        // Compute the number of request arguments, accounting for nullable ones
        int len = 3;
        final CompositeBuffer cb = newRequestCompositeBuffer(len, RedisProtocolSupport.Command.ZSCORE, allocator);
        addRequestArgument(key, cb, allocator);
        addRequestArgument(member, cb, allocator);
        final RedisRequest request = newRequest(RedisProtocolSupport.Command.ZSCORE, cb);
        final RedisPartitionAttributesBuilder partitionAttributesBuilder = partitionAttributesBuilderFunction
                    .apply(RedisProtocolSupport.Command.ZSCORE);
        partitionAttributesBuilder.addKey(key);
        final Single<Double> result = partitionedRedisClient.request(partitionAttributesBuilder.build(), request, String.class)
                    .map(s -> s != null ? Double.valueOf(s) : null);
        return result;
    }

    @Override
    public Single<Long> zunionstore(@RedisProtocolSupport.Key final CharSequence destination, final long numkeys,
                                    @RedisProtocolSupport.Key final Collection<? extends CharSequence> keys) {
        requireNonNull(destination);
        requireNonNull(keys);
        final BufferAllocator allocator = partitionedRedisClient.getBufferAllocator();
        // Compute the number of request arguments, accounting for nullable ones
        int len = 3;
        len += keys.size();
        final CompositeBuffer cb = newRequestCompositeBuffer(len, RedisProtocolSupport.Command.ZUNIONSTORE, allocator);
        addRequestArgument(destination, cb, allocator);
        addRequestArgument(numkeys, cb, allocator);
        addRequestCharSequenceArguments(keys, null, cb, allocator);
        final RedisRequest request = newRequest(RedisProtocolSupport.Command.ZUNIONSTORE, cb);
        final RedisPartitionAttributesBuilder partitionAttributesBuilder = partitionAttributesBuilderFunction
                    .apply(RedisProtocolSupport.Command.ZUNIONSTORE);
        partitionAttributesBuilder.addKey(destination);
        addCharSequenceKeysToAttributeBuilder(keys, partitionAttributesBuilder);
        final Single<Long> result = partitionedRedisClient.request(partitionAttributesBuilder.build(), request, Long.class);
        return result;
    }

    @Override
    public Single<Long> zunionstore(@RedisProtocolSupport.Key final CharSequence destination, final long numkeys,
                                    @RedisProtocolSupport.Key final Collection<? extends CharSequence> keys,
                                    final Collection<Long> weightses,
                                    @Nullable final RedisProtocolSupport.ZunionstoreAggregate aggregate) {
        requireNonNull(destination);
        requireNonNull(keys);
        requireNonNull(weightses);
        final BufferAllocator allocator = partitionedRedisClient.getBufferAllocator();
        // Compute the number of request arguments, accounting for nullable ones
        int len = 3;
        len += keys.size();
        len += 2 * weightses.size();
        if (aggregate != null) {
            len++;
        }
        final CompositeBuffer cb = newRequestCompositeBuffer(len, RedisProtocolSupport.Command.ZUNIONSTORE, allocator);
        addRequestArgument(destination, cb, allocator);
        addRequestArgument(numkeys, cb, allocator);
        addRequestCharSequenceArguments(keys, null, cb, allocator);
        addRequestLongArguments(weightses, RedisProtocolSupport.SubCommand.WEIGHTS, cb, allocator);
        if (aggregate != null) {
            addRequestArgument(aggregate, cb, allocator);
        }
        final RedisRequest request = newRequest(RedisProtocolSupport.Command.ZUNIONSTORE, cb);
        final RedisPartitionAttributesBuilder partitionAttributesBuilder = partitionAttributesBuilderFunction
                    .apply(RedisProtocolSupport.Command.ZUNIONSTORE);
        partitionAttributesBuilder.addKey(destination);
        addCharSequenceKeysToAttributeBuilder(keys, partitionAttributesBuilder);
        final Single<Long> result = partitionedRedisClient.request(partitionAttributesBuilder.build(), request, Long.class);
        return result;
    }
}

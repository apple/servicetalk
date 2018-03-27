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

import io.servicetalk.buffer.Buffer;
import io.servicetalk.buffer.BufferAllocator;
import io.servicetalk.buffer.CompositeBuffer;
import io.servicetalk.client.api.partition.PartitionAttributes;
import io.servicetalk.concurrent.api.Publisher;
import io.servicetalk.concurrent.api.Single;
import io.servicetalk.redis.api.RedisClient.ReservedRedisConnection;
import io.servicetalk.redis.api.RedisData.Array;
import io.servicetalk.redis.api.RedisData.ArraySize;
import io.servicetalk.redis.api.RedisData.CompleteBulkString;
import io.servicetalk.redis.api.RedisData.CompleteRequestRedisData;
import io.servicetalk.redis.api.RedisData.RequestRedisData;
import io.servicetalk.redis.api.RedisProtocolSupport.Command;
import io.servicetalk.redis.api.RedisProtocolSupport.SubCommand;
import io.servicetalk.redis.api.RedisProtocolSupport.TupleArgument;
import io.servicetalk.transport.api.FlushStrategy;

import java.util.Collection;
import java.util.List;
import java.util.RandomAccess;
import java.util.function.BiFunction;
import java.util.function.Predicate;
import javax.annotation.Nullable;

import static io.servicetalk.concurrent.api.Single.success;
import static io.servicetalk.redis.internal.RedisUtils.toRespArraySize;
import static io.servicetalk.redis.internal.RedisUtils.toRespBulkString;
import static io.servicetalk.transport.api.FlushStrategy.defaultFlushStrategy;
import static io.servicetalk.transport.api.FlushStrategy.flushBeforeEnd;
import static java.lang.System.arraycopy;
import static java.util.Objects.requireNonNull;

public final class RedisRequests {

    private RedisRequests() {
        // no instances
    }

    /**
     * Instantiates a new {@link RedisRequest} for a no-arg {@link Command}.
     *
     * @param command the request {@link Command}.
     * @return a new {@link RedisRequest}.
     */
    public static RedisRequest newRequest(final Command command) {
        return newRequest(command, Publisher.from(new ArraySize(1L), command), flushBeforeEnd());
    }

    /**
     * Instantiates a new {@link RedisRequest} for a no-arg {@link Command} and {@link SubCommand}.
     *
     * @param command    the request {@link Command}.
     * @param subCommand the request {@link SubCommand}.
     * @return a new {@link RedisRequest}.
     */
    public static RedisRequest newRequest(final Command command, final SubCommand subCommand) {
        return newRequest(command, Publisher.from(new ArraySize(2L), command, requireNonNull(subCommand)), flushBeforeEnd());
    }

    /**
     * Instantiates a new {@link RedisRequest} for a single-arg {@link Command}.
     *
     * @param command the request {@link Command}.
     * @param arg     the command argument.
     * @return a new {@link RedisRequest}.
     */
    public static RedisRequest newRequest(final Command command, final CompleteBulkString arg) {
        return newRequest(command, Publisher.from(new ArraySize(2L), command, requireNonNull(arg)), flushBeforeEnd());
    }

    /**
     * Instantiates a new {@link RedisRequest} for a single-arg {@link Command} and {@link SubCommand}.
     *
     * @param command    the request {@link Command}.
     * @param subCommand the request {@link SubCommand}.
     * @param arg        the command argument.
     * @return a new {@link RedisRequest}.
     */
    public static RedisRequest newRequest(final Command command, final SubCommand subCommand, final CompleteBulkString arg) {
        return newRequest(command, Publisher.from(new ArraySize(3L), command, requireNonNull(subCommand), requireNonNull(arg)), flushBeforeEnd());
    }

    /**
     * Instantiates a new {@link RedisRequest}.
     *
     * @param command the request {@link Command}.
     * @param args    the command arguments.
     * @return a new {@link RedisRequest}.
     */
    public static RedisRequest newRequest(final Command command, final CompleteBulkString... args) {
        final RequestRedisData[] stanzaAndArgs = new RequestRedisData[args.length + 2];
        stanzaAndArgs[0] = new ArraySize(args.length + 1);
        stanzaAndArgs[1] = command;
        arraycopy(args, 0, stanzaAndArgs, 2, args.length);

        return newRequest(command, Publisher.from(stanzaAndArgs));
    }

    /**
     * Instantiates a new {@link RedisRequest}.
     *
     * @param command    the request {@link Command}.
     * @param subCommand the request {@link SubCommand}.
     * @param args       the command arguments.
     * @return a new {@link RedisRequest}.
     */
    public static RedisRequest newRequest(final Command command, final SubCommand subCommand, final CompleteBulkString... args) {
        final RequestRedisData[] stanzaAndArgs = new RequestRedisData[args.length + 3];
        stanzaAndArgs[0] = new ArraySize(args.length + 2);
        stanzaAndArgs[1] = command;
        stanzaAndArgs[2] = requireNonNull(subCommand);
        arraycopy(args, 0, stanzaAndArgs, 3, args.length);

        return newRequest(command, Publisher.from(stanzaAndArgs));
    }

    /**
     * Instantiates a new {@link RequestRedisData} from a prepared {@link Array} that contains a complete Redis request.
     *
     * @param content the complete request.
     * @return a new {@link RequestRedisData}.
     * @see <a href="https://redis.io/topics/protocol#sending-commands-to-a-redis-server">Sending commands to a Redis Server</a>
     */
    public static RedisRequest newRequest(final Array<CompleteRequestRedisData> content) {
        @SuppressWarnings("unchecked")
        final List<CompleteRequestRedisData> data = (List<CompleteRequestRedisData>) content.getListValue();
        final Command command = (Command) data.get(0);

        final RequestRedisData[] stanzaAndArgs = data.toArray(new RequestRedisData[data.size() + 1]);
        arraycopy(stanzaAndArgs, 0, stanzaAndArgs, 1, data.size());
        stanzaAndArgs[0] = new ArraySize(data.size());

        return newRequest(command, Publisher.from(stanzaAndArgs));
    }

    /**
     * Instantiates a new {@link RedisRequest}.
     *
     * @param command the request {@link Command}.
     * @param content a {@link Publisher} that provides the request content.
     * @return a new {@link RedisRequest}.
     */
    public static RedisRequest newRequest(final Command command,
                                          final Publisher<RequestRedisData> content) {
        return newRequest(command, content, defaultFlushStrategy());
    }

    /**
     * Instantiates a new {@link RedisRequest}.
     *
     * @param command       the request {@link Command}.
     * @param content       a {@link Publisher} that provides the request content.
     * @param flushStrategy the {@link FlushStrategy} to use with this request.
     * @return a new {@link RedisRequest}.
     */
    public static RedisRequest newRequest(final Command command,
                                          final Publisher<RequestRedisData> content,
                                          final FlushStrategy flushStrategy) {
        return new DefaultRedisRequest(command, content, flushStrategy);
    }

    /**
     * Instantiates a new {@link RedisRequest} from a prepared {@link Buffer} that contains a complete Redis request.
     *
     * @param command the request command {@link Command}, provided for information only.
     * @param content the complete request.
     * @return a new {@link RedisRequest}.
     * @see <a href="https://redis.io/topics/protocol#sending-commands-to-a-redis-server">Sending commands to a Redis Server</a>
     */
    public static RedisRequest newRequest(final Command command,
                                          final Buffer content) {
        return newRequest(command, content, flushBeforeEnd());
    }

    /**
     * Instantiates a new {@link RedisRequest} from a prepared {@link Buffer} that contains a complete Redis request.
     *
     * @param command       the request command {@link Command}, provided for information only.
     * @param content       the complete request.
     * @param flushStrategy the {@link FlushStrategy} to use with this request.
     * @return a new {@link RedisRequest}.
     * @see <a href="https://redis.io/topics/protocol#sending-commands-to-a-redis-server">Sending commands to a Redis Server</a>
     */
    public static RedisRequest newRequest(final Command command,
                                          final Buffer content,
                                          final FlushStrategy flushStrategy) {
        return new DefaultRedisRequest(command, Publisher.just(new RESPBuffer(content)), flushStrategy);
    }

    static CompositeBuffer newRequestCompositeBuffer(final int argCount, final Command command, final BufferAllocator allocator) {
        return newRequestCompositeBuffer(argCount, command, null, allocator);
    }

    static CompositeBuffer newRequestCompositeBuffer(final int argCount, final Command command, @Nullable final SubCommand subCommand, final BufferAllocator allocator) {
        final CompositeBuffer cb = allocator.newCompositeBuffer()
                .addBuffer(toRespArraySize(argCount, allocator))
                .addBuffer(command.toRESPArgument(allocator));
        if (subCommand != null) {
            cb.addBuffer(subCommand.toRESPArgument(allocator));
        }
        return cb;
    }

    static void addRequestArgument(final Number arg, final CompositeBuffer cb, final BufferAllocator allocator) {
        cb.addBuffer(toRespBulkString(arg, allocator));
    }

    static void addRequestArgument(final Buffer arg, final CompositeBuffer cb, final BufferAllocator allocator) {
        cb.addBuffer(toRespBulkString(arg, allocator));
    }

    static void addRequestArgument(final CharSequence arg, final CompositeBuffer cb, final BufferAllocator allocator) {
        cb.addBuffer(toRespBulkString(arg, allocator));
    }

    static void addRequestArgument(final TupleArgument arg, final CompositeBuffer cb, final BufferAllocator allocator) {
        arg.writeTo(cb, allocator);
    }

    static void addRequestArgument(final CompleteRequestRedisData arg, final CompositeBuffer cb, final BufferAllocator allocator) {
        cb.addBuffer(arg.toRESPArgument(allocator));
    }

    static void addRequestBufferArguments(final Collection<? extends Buffer> args, @Nullable final SubCommand subCommand, final CompositeBuffer cb, final BufferAllocator allocator) {
        if (args instanceof List && args instanceof RandomAccess) {
            final List<? extends Buffer> argsList = castCollectionToList(args);
            // Don't use foreach to avoid creating an iterator
            //noinspection ForLoopReplaceableByForEach
            for (int i = 0; i < args.size(); i++) {
                if (subCommand != null) {
                    addRequestArgument(subCommand, cb, allocator);
                }
                addRequestArgument(argsList.get(i), cb, allocator);
            }
        } else {
            for (final Buffer arg : args) {
                if (subCommand != null) {
                    addRequestArgument(subCommand, cb, allocator);
                }
                addRequestArgument(arg, cb, allocator);
            }
        }
    }

    static void addRequestCharSequenceArguments(final Collection<? extends CharSequence> args, @Nullable final SubCommand subCommand, final CompositeBuffer cb, final BufferAllocator allocator) {
        if (args instanceof List && args instanceof RandomAccess) {
            final List<? extends CharSequence> argsList = castCollectionToList(args);
            // Don't use foreach to avoid creating an iterator
            //noinspection ForLoopReplaceableByForEach
            for (int i = 0; i < args.size(); i++) {
                if (subCommand != null) {
                    addRequestArgument(subCommand, cb, allocator);
                }
                addRequestArgument(argsList.get(i), cb, allocator);
            }
        } else {
            for (final CharSequence arg : args) {
                if (subCommand != null) {
                    addRequestArgument(subCommand, cb, allocator);
                }
                addRequestArgument(arg, cb, allocator);
            }
        }
    }

    static void addBufferKeysToAttributeBuilder(final Collection<? extends Buffer> keys, final RedisPartitionAttributesBuilder builder) {
        if (keys instanceof List && keys instanceof RandomAccess) {
            final List<? extends Buffer> keysList = castCollectionToList(keys);
            // Don't use foreach to avoid creating an iterator
            //noinspection ForLoopReplaceableByForEach
            for (int i = 0; i < keys.size(); i++) {
                builder.addKey(keysList.get(i));
            }
        } else {
            for (final Buffer key : keys) {
                builder.addKey(key);
            }
        }
    }

    static void addCharSequenceKeysToAttributeBuilder(final Collection<? extends CharSequence> keys, final RedisPartitionAttributesBuilder builder) {
        if (keys instanceof List && keys instanceof RandomAccess) {
            final List<? extends CharSequence> keysList = castCollectionToList(keys);
            // Don't use foreach to avoid creating an iterator
            //noinspection ForLoopReplaceableByForEach
            for (int i = 0; i < keys.size(); i++) {
                builder.addKey(keysList.get(i));
            }
        } else {
            for (final CharSequence key : keys) {
                builder.addKey(key);
            }
        }
    }

    static void addTupleKeysToAttributeBuilder(final Collection<? extends TupleArgument> args, final RedisPartitionAttributesBuilder builder) {
        if (args instanceof List && args instanceof RandomAccess) {
            final List<? extends TupleArgument> argsList = castCollectionToList(args);
            // Don't use foreach to avoid creating an iterator
            //noinspection ForLoopReplaceableByForEach
            for (int i = 0; i < args.size(); i++) {
                argsList.get(i).buildAttributes(builder);
            }
        } else {
            for (final TupleArgument arg : args) {
                arg.buildAttributes(builder);
            }
        }
    }

    static void addRequestTupleArguments(final Collection<? extends TupleArgument> args, @Nullable final SubCommand subCommand, final CompositeBuffer cb, final BufferAllocator allocator) {
        if (args instanceof List && args instanceof RandomAccess) {
            final List<? extends TupleArgument> argsList = castCollectionToList(args);
            // Don't use foreach to avoid creating an iterator
            //noinspection ForLoopReplaceableByForEach
            for (int i = 0; i < args.size(); i++) {
                if (subCommand != null) {
                    addRequestArgument(subCommand, cb, allocator);
                }
                addRequestArgument(argsList.get(i), cb, allocator);
            }
        } else {
            for (final TupleArgument arg : args) {
                if (subCommand != null) {
                    addRequestArgument(subCommand, cb, allocator);
                }
                addRequestArgument(arg, cb, allocator);
            }
        }
    }

    static void addRequestLongArguments(final Collection<? extends Number> args, @Nullable final SubCommand subCommand, final CompositeBuffer cb, final BufferAllocator allocator) {
        if (args instanceof List && args instanceof RandomAccess) {
            final List<? extends Number> argsList = castCollectionToList(args);
            // Don't use foreach to avoid creating an iterator
            //noinspection ForLoopReplaceableByForEach
            for (int i = 0; i < args.size(); i++) {
                if (subCommand != null) {
                    addRequestArgument(subCommand, cb, allocator);
                }
                addRequestArgument(argsList.get(i), cb, allocator);
            }
        } else {
            for (final Number arg : args) {
                if (subCommand != null) {
                    addRequestArgument(subCommand, cb, allocator);
                }
                addRequestArgument(arg, cb, allocator);
            }
        }
    }

    private static <T, N extends T> List<N> castCollectionToList(final Collection<N> args) {
        return (List<N>) args;
    }

    static <C> Single<C> newConnectedClient(final RedisRequester requestor, final RedisRequest request,
                                            final Predicate<RedisData> responsePredicate,
                                            final BiFunction<ReservedRedisConnection, Boolean, C> builder) {
        if (requestor instanceof ReservedRedisConnection) {
            return newConnectedClient((ReservedRedisConnection) requestor, false, request, responsePredicate, builder);
        } else if (requestor instanceof RedisClient) {
            return ((RedisClient) requestor).reserveConnection(request)
                    .flatmap(reservedCnx -> newConnectedClient(reservedCnx, true, request, responsePredicate, builder));
        } else {
            return newConnectedClient(new StandAloneReservedRedisConnection((RedisConnection) requestor), false, request, responsePredicate, builder);
        }
    }

    static <C> Single<C> newConnectedClient(final PartitionedRedisClient client, final PartitionAttributes partitionSelector,
                                            final RedisRequest request,
                                            final Predicate<RedisData> responsePredicate,
                                            final BiFunction<ReservedRedisConnection, Boolean, C> builder) {
        return client.reserveConnection(partitionSelector, request)
                .flatmap(reservedCnx -> newConnectedClient(reservedCnx, true, request, responsePredicate, builder));
    }

    static <C> Single<C> newConnectedClient(final RedisRequester requestor, final RedisRequest request,
                                            final BiFunction<ReservedRedisConnection, Publisher<RedisData>, C> builder) {
        if (requestor instanceof ReservedRedisConnection) {
            return success(builder.apply((ReservedRedisConnection) requestor, requestor.request(request)));
        } else if (requestor instanceof RedisClient) {
            return ((RedisClient) requestor).reserveConnection(request)
                    .flatmap(reservedCnx -> success(builder.apply(reservedCnx, reservedCnx.request(request))));
        } else {
            final StandAloneReservedRedisConnection reservedCnx = new StandAloneReservedRedisConnection((RedisConnection) requestor);
            return success(builder.apply(reservedCnx, reservedCnx.request(request)));
        }
    }

    static <C> Single<C> newConnectedClient(final PartitionedRedisClient client, final PartitionAttributes partitionSelector,
                                            final RedisRequest request,
                                            final BiFunction<ReservedRedisConnection, Publisher<RedisData>, C> builder) {
        return client.reserveConnection(partitionSelector, request)
                .flatmap(reservedCnx -> success(builder.apply(reservedCnx, reservedCnx.request(request))));
    }

    private static <C> Single<C> newConnectedClient(final ReservedRedisConnection reservedCnx, final boolean closeCnx, final RedisRequest request,
                                                    final Predicate<RedisData> responsePredicate,
                                                    final BiFunction<ReservedRedisConnection, Boolean, C> builder) {
        return reservedCnx.request(request)
                .first()
                .map(res -> {
                    if (responsePredicate.test(res)) {
                        return builder.apply(reservedCnx, closeCnx);
                    }
                    throw new IllegalStateException("Unexpected response: " + res);
                });
    }

    private static final class RESPBuffer implements RequestRedisData {
        private final Buffer value;

        private RESPBuffer(final Buffer value) {
            this.value = requireNonNull(value);
        }

        @Override
        public Buffer toRESPArgument(final BufferAllocator allocator) {
            return value;
        }
    }
}

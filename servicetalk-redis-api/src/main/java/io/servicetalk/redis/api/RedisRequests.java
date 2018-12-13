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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collection;
import java.util.List;
import java.util.RandomAccess;
import java.util.function.BiFunction;
import java.util.function.Predicate;

import static io.servicetalk.concurrent.api.Single.success;
import static io.servicetalk.redis.api.StringByteSizeUtil.numberOfBytesUtf8;
import static io.servicetalk.redis.api.StringByteSizeUtil.numberOfDigits;
import static io.servicetalk.redis.api.StringByteSizeUtil.numberOfDigitsPositive;
import static io.servicetalk.redis.internal.RedisUtils.EOL_LENGTH;
import static io.servicetalk.redis.internal.RedisUtils.EOL_SHORT;
import static java.lang.System.arraycopy;
import static java.nio.charset.StandardCharsets.US_ASCII;
import static java.util.Objects.requireNonNull;

/**
 * Factory methods for creating {@link RedisRequest}s.
 */
public final class RedisRequests {

    private static final Logger LOGGER = LoggerFactory.getLogger(RedisRequests.class);

    private static final int NUMBER_CACHE_MIN = -1;
    private static final int NUMBER_CACHE_MAX = 32;
    private static final byte[][] NUMBER_CACHE = initNumberCache();

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
        return newRequest(command, Publisher.from(new ArraySize(1L), command));
    }

    /**
     * Instantiates a new {@link RedisRequest} for a no-arg {@link Command} and {@link SubCommand}.
     *
     * @param command the request {@link Command}.
     * @param subCommand the request {@link SubCommand}.
     * @return a new {@link RedisRequest}.
     */
    public static RedisRequest newRequest(final Command command, final SubCommand subCommand) {
        return newRequest(command, Publisher.from(new ArraySize(2L), command, requireNonNull(subCommand)));
    }

    /**
     * Instantiates a new {@link RedisRequest} for a single-arg {@link Command}.
     *
     * @param command the request {@link Command}.
     * @param arg the command argument.
     * @return a new {@link RedisRequest}.
     */
    public static RedisRequest newRequest(final Command command, final CompleteBulkString arg) {
        return newRequest(command, Publisher.from(new ArraySize(2L), command, requireNonNull(arg)));
    }

    /**
     * Instantiates a new {@link RedisRequest} for a single-arg {@link Command} and {@link SubCommand}.
     *
     * @param command the request {@link Command}.
     * @param subCommand the request {@link SubCommand}.
     * @param arg the command argument.
     * @return a new {@link RedisRequest}.
     */
    public static RedisRequest newRequest(final Command command, final SubCommand subCommand,
                                          final CompleteBulkString arg) {
        return newRequest(command, Publisher.from(new ArraySize(3L), command, requireNonNull(subCommand),
                requireNonNull(arg)));
    }

    /**
     * Instantiates a new {@link RedisRequest}.
     *
     * @param command the request {@link Command}.
     * @param args the command arguments.
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
     * @param command the request {@link Command}.
     * @param subCommand the request {@link SubCommand}.
     * @param args the command arguments.
     * @return a new {@link RedisRequest}.
     */
    public static RedisRequest newRequest(final Command command, final SubCommand subCommand,
                                          final CompleteBulkString... args) {
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
     * @see <a href="https://redis.io/topics/protocol#sending-commands-to-a-redis-server">Sending commands to a Redis
     * Server</a>
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
        return new DefaultRedisRequest(command, content);
    }

    /**
     * Instantiates a new {@link RedisRequest} from a prepared {@link Buffer} that contains a complete Redis request.
     *
     * @param command the request command {@link Command}, provided for information only.
     * @param content the complete request.
     * @return a new {@link RedisRequest}.
     * @see <a href="https://redis.io/topics/protocol#sending-commands-to-a-redis-server">Sending commands to a Redis
     * Server</a>
     */
    public static RedisRequest newRequest(final Command command,
                                          final Buffer content) {
        return new DefaultRedisRequest(command, Publisher.just(new RESPBuffer(content)));
    }

    static void addBufferKeysToAttributeBuilder(final Collection<? extends Buffer> keys,
                                                final RedisPartitionAttributesBuilder builder) {
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

    static void addCharSequenceKeysToAttributeBuilder(final Collection<? extends CharSequence> keys,
                                                      final RedisPartitionAttributesBuilder builder) {
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

    /**
     * Calculates the size of the buffer needed for writing the array size and the {@link Command}.
     *
     * @param arraySize Array size to be written.
     * @param cmd {@link Command} to be written.
     * @return The required buffer size.
     */
    public static int calculateInitialCommandBufferSize(final int arraySize, final Command cmd) {
        return calculateRequestArgumentArraySize(arraySize) + cmd.encodedByteCount();
    }

    /**
     * Estimates the size of the buffer needed to write a {@link CharSequence}, including necessary
     * <a href="https://redis.io/topics/protocol">RESP protocol</a> components.
     * <p>
     * Note: This estimate assumes the {@link CharSequence} contains purely single-byte characters, so is effectively
     * the same as {@link CharSequence#length()}. Multi-byte characters will result in estimating low, which may
     * result in the buffer being resized when writes are done.
     *
     * @param arg the {@link CharSequence} to estimate the buffer size for.
     * @return The required buffer size.
     */
    public static int estimateRequestArgumentSize(final CharSequence arg) {
        return calculateRequestArgumentSize(arg.length());
    }

    /**
     * Calculates the size of the buffer needed to write a {@link CharSequence}, including necessary
     * <a href="https://redis.io/topics/protocol">RESP protocol</a> components.
     *
     * @param arg the {@link CharSequence} to calculate the buffer size for.
     * @return The required buffer size.
     */
    public static int calculateRequestArgumentSize(final CharSequence arg) {
        return calculateRequestArgumentSize(numberOfBytesUtf8(arg));
    }

    /**
     * Writes a {@link CharSequence} to {@code buffer}, including necessary
     * <a href="https://redis.io/topics/protocol">RESP protocol</a> components.
     *
     * @param buffer the {@link Buffer} to write to.
     * @param arg the {@link Buffer} to write.
     */
    public static void writeRequestArgument(final Buffer buffer, final CharSequence arg) {
        writeRequestArgument(buffer, arg, numberOfBytesUtf8(arg));
    }

    static void writeRequestArgument(final Buffer buffer, final CharSequence arg, final int byteCount) {
        writeLength(buffer, byteCount);

        final int beginIndex = buffer.writerIndex();
        buffer.writeUtf8(arg, byteCount);
        final int endIndex = buffer.writerIndex();

        assert endIndex - beginIndex == byteCount :
                "Wrote " + (endIndex - beginIndex) + " bytes when expecting " + byteCount;

        buffer.writeShort(EOL_SHORT);
    }

    /**
     * Estimates the size of the buffer needed to write a {@link Buffer}, including necessary
     * <a href="https://redis.io/topics/protocol">RESP protocol</a> components.
     *
     * @param arg the {@link Buffer} to calculate the buffer size for.
     * @return The required buffer size.
     */
    public static int calculateRequestArgumentSize(final Buffer arg) {
        return calculateRequestArgumentSize(arg.readableBytes());
    }

    /**
     * Writes a {@link Buffer} to {@code buffer}, including necessary <a href="https://redis.io/topics/protocol">RESP
     * protocol</a> components.
     *
     * @param buffer the {@link Buffer} to write to.
     * @param arg the {@link CharSequence} to write.
     */
    public static void writeRequestArgument(final Buffer buffer, final Buffer arg) {
        writeLength(buffer, arg.readableBytes());
        buffer.writeBytes(arg)
                .writeShort(EOL_SHORT);
    }

    /**
     * Estimates the size of the buffer needed to write a {@code byte[]}, including necessary
     * <a href="https://redis.io/topics/protocol">RESP protocol</a> components.
     *
     * @param arg the {@code byte[]} to calculate the buffer size for.
     * @return The required buffer size.
     */
    public static int calculateRequestArgumentSize(final byte[] arg) {
        return calculateRequestArgumentSize(arg.length);
    }

    /**
     * Writes a {@code byte[]} to {@code buffer}, including necessary <a href="https://redis.io/topics/protocol">RESP
     * protocol</a> components.
     *
     * @param buffer the {@code byte[]} to write to.
     * @param arg the {@link CharSequence} to write.
     */
    public static void writeRequestArgument(final Buffer buffer, final byte[] arg) {
        writeLength(buffer, arg.length);
        buffer.writeBytes(arg).writeShort(EOL_SHORT);
    }

    /**
     * Estimates the size of the buffer needed to write a {@code double}, including necessary
     * <a href="https://redis.io/topics/protocol">RESP protocol</a> components.
     *
     * @param arg the {@code double} to calculate the buffer size for.
     * @return The required buffer size.
     */
    public static int calculateRequestArgumentSize(final double arg) {
        return calculateRequestArgumentSize(Double.toString(arg).length());
    }

    /**
     * Writes a {@code double} to {@code buffer}, including necessary <a href="https://redis.io/topics/protocol">RESP
     * protocol</a> components.
     *
     * @param buffer the {@link Buffer} to write to.
     * @param arg the {@code double} to write.
     */
    public static void writeRequestArgument(final Buffer buffer, final double arg) {
        writeRequestArgument(buffer, Double.toString(arg));
    }

    /**
     * Estimates the size of the buffer needed to write a {@code long}, including necessary
     * <a href="https://redis.io/topics/protocol">RESP protocol</a> components.
     *
     * @param arg the {@code long} to calculate the buffer size for.
     * @return The required buffer size.
     */
    public static int calculateRequestArgumentSize(final long arg) {
        return calculateRequestArgumentSize(numberOfDigits(arg));
    }

    /**
     * Writes a {@code long} to {@code buffer}, including necessary <a href="https://redis.io/topics/protocol">RESP
     * protocol</a> components.
     *
     * @param buffer the {@link Buffer} to write to.
     * @param arg the {@code long} to write.
     */
    public static void writeRequestArgument(final Buffer buffer, final long arg) {
        writeLength(buffer, numberOfDigits(arg));
        writeNumber(buffer, arg);
        buffer.writeShort(EOL_SHORT);
    }

    /**
     * Writes a <a href="https://redis.io/topics/protocol">RESP protocol</a> array size to {@code buffer}.
     *
     * @param buffer the {@link Buffer} to write to.
     * @param arraySize the array size to write.
     */
    public static void writeRequestArraySize(final Buffer buffer, final long arraySize) {
        buffer.writeByte('*');
        writeNumber(buffer, arraySize);
        buffer.writeShort(EOL_SHORT);
    }

    static int calculateRequestArgumentArraySize(final long arraySize) {
        return 1 + numberOfDigitsPositive(arraySize) + EOL_LENGTH;
    }

    static int calculateRequestArgumentLengthSize(final int length) {
        return 1 + numberOfDigitsPositive(length) + EOL_LENGTH;
    }

    static void writeLength(final Buffer buffer, final int length) {
        buffer.writeByte('$');
        writeNumber(buffer, length);
        buffer.writeShort(EOL_SHORT);
    }

    private static int calculateRequestArgumentSize(final int byteLength) {
        return calculateRequestArgumentLengthSize(byteLength) + byteLength + EOL_LENGTH;
    }

    // Visible for testing
    static void writeNumber(final Buffer buffer, final long number) {
        if (NUMBER_CACHE_MIN <= number && number < NUMBER_CACHE_MAX) {
            buffer.writeBytes(NUMBER_CACHE[((int) number - NUMBER_CACHE_MIN)]);
        } else {
            writeNumberNoCache(buffer, number);
        }
    }

    private static byte[][] initNumberCache() {
        byte[][] cache = new byte[NUMBER_CACHE_MAX - NUMBER_CACHE_MIN][];
        for (int i = 0; i < NUMBER_CACHE_MAX - NUMBER_CACHE_MIN; ++i) {
            cache[i] = Integer.toString(i + NUMBER_CACHE_MIN).getBytes(US_ASCII);
        }
        return cache;
    }

    private static void writeNumberNoCache(final Buffer buffer, final long number) {
        buffer.writeBytes(Long.toString(number).getBytes(US_ASCII));
    }

    private static <T, N extends T> List<N> castCollectionToList(final Collection<N> args) {
        return (List<N>) args;
    }

    static <C> Single<C> reserveConnection(final RedisRequester requestor, final RedisRequest request,
                                           final Predicate<RedisData> responsePredicate,
                                           final BiFunction<ReservedRedisConnection, Boolean, C> builder) {
        if (requestor instanceof ReservedRedisConnection) {
            return requestAndWrapConnection((ReservedRedisConnection) requestor, false, request, responsePredicate,
                    builder);
        } else if (requestor instanceof RedisClient) {
            return ((RedisClient) requestor).reserveConnection(request.command())
                .flatMap(reservedCnx ->
                        requestAndWrapConnection(reservedCnx, true, request, responsePredicate, builder));
        } else {
            return requestAndWrapConnection(new StandAloneReservedRedisConnection((RedisConnection) requestor), false,
                    request, responsePredicate, builder);
        }
    }

    static <C> Single<C> reserveConnection(final PartitionedRedisClient client,
                                           final PartitionAttributes partitionSelector, final RedisRequest request,
                                           final Predicate<RedisData> responsePredicate,
                                           final BiFunction<ReservedRedisConnection, Boolean, C> builder) {
        return client.reserveConnection(partitionSelector, request.command())
            .flatMap(reservedCnx -> requestAndWrapConnection(reservedCnx, true, request, responsePredicate, builder));
    }

    static <C> Single<C> reserveConnection(final RedisRequester requestor, final RedisRequest request,
                                           final BiFunction<ReservedRedisConnection, Publisher<RedisData>, C> builder) {
        if (requestor instanceof ReservedRedisConnection) {
            return success(builder.apply((ReservedRedisConnection) requestor, requestor.request(request)));
        } else if (requestor instanceof RedisClient) {
            return ((RedisClient) requestor).reserveConnection(request.command())
                    .flatMap(reservedCnx -> success(builder.apply(reservedCnx, reservedCnx.request(request))));
        } else {
            final StandAloneReservedRedisConnection reservedCnx =
                    new StandAloneReservedRedisConnection((RedisConnection) requestor);
            return success(builder.apply(reservedCnx, reservedCnx.request(request)));
        }
    }

    static <C> Single<C> reserveConnection(final PartitionedRedisClient client,
                                           final PartitionAttributes partitionSelector, final RedisRequest request,
                                           final BiFunction<ReservedRedisConnection, Publisher<RedisData>, C> builder) {
        return client.reserveConnection(partitionSelector, request.command())
                .flatMap(reservedCnx -> success(builder.apply(reservedCnx, reservedCnx.request(request))));
    }

    private static <C> Single<C> requestAndWrapConnection(final ReservedRedisConnection reservedCnx,
                                                          final boolean closeCnx, final RedisRequest request,
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
        public int encodedByteCount() {
            return value.readableBytes();
        }

        @Override
        public void encodeTo(final Buffer buffer) {
            buffer.writeBytes(value);
        }

        @Override
        public Buffer asBuffer(final BufferAllocator allocator) {
            return value;
        }
    }
}

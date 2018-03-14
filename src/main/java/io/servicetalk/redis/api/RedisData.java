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

import java.util.List;

import static io.servicetalk.redis.internal.RedisUtils.EOL_LENGTH;
import static io.servicetalk.redis.internal.RedisUtils.EOL_SHORT;
import static io.servicetalk.redis.internal.RedisUtils.toRespArraySize;
import static io.servicetalk.redis.internal.RedisUtils.toRespBulkString;
import static java.util.Arrays.asList;

/**
 * Data that can be sent to or received from Redis.
 */
public interface RedisData {

    Null NULL = new Null();

    SimpleString OK = new SimpleString("OK");
    SimpleString QUEUED = new SimpleString("QUEUED");
    SimpleString PONG = new SimpleString("PONG");

    /**
     * @return the {@code int} value of the data.
     * @throws UnsupportedOperationException if the data is not an {@code int}
     */
    default int getIntValue() {
        throw new UnsupportedOperationException("Data is not of type int");
    }

    /**
     * @return the {@code long} value of the data.
     * @throws UnsupportedOperationException if the data is not an {@code long}
     */
    default long getLongValue() {
        throw new UnsupportedOperationException("Data is not of type long");
    }

    /**
     * @return the {@link Buffer} value of the data.
     * @throws UnsupportedOperationException if the data is not an {@link Buffer}
     */
    default Buffer getBufferValue() {
        throw new UnsupportedOperationException("Data is not of type Buffer");
    }

    /**
     * @return the {@link CharSequence} value of the data.
     * @throws UnsupportedOperationException if the data is not an {@link CharSequence}
     */
    default CharSequence getCharSequenceValue() {
        throw new UnsupportedOperationException("Data is not of type CharSequence");
    }

    /**
     * @return the {@link List} of {@link RedisData} value of the data.
     * @throws UnsupportedOperationException if the data is not a {@link List} of {@link RedisData}
     */
    @SuppressWarnings("unchecked")
    default List<? extends RedisData> getListValue() {
        throw new UnsupportedOperationException("Data is not of type List<RedisData>");
    }

    /**
     * Data that can be sent as part of a Redis request.
     */
    interface RequestRedisData {
        /**
         * Convert this {@link RequestRedisData} into a single RESP (REdis Serialization Protocol) {@link Buffer}.
         *
         * @param allocator the {@link BufferAllocator} to use
         * @return a RESP {@link Buffer} representation of this {@link RequestRedisData}
         * @see <a href="https://redis.io/topics/protocol">Redis Protocol specification</a>
         */
        Buffer toRESPArgument(BufferAllocator allocator);
    }

    /**
     * Fully self contained data.
     */
    interface CompleteRedisData extends RedisData {
    }

    /**
     * Fully self contained data that can be sent as part of a Redis request.
     */
    interface CompleteRequestRedisData extends CompleteRedisData, RequestRedisData {
    }

    final class SimpleString extends DefaultBaseRedisData<CharSequence> implements CompleteRequestRedisData {
        public SimpleString(final CharSequence value) {
            super(value);
        }

        @Override
        public CharSequence getCharSequenceValue() {
            return getValue();
        }

        @Override
        public Buffer toRESPArgument(final BufferAllocator allocator) {
            return toRespBulkString(getValue(), allocator);
        }
    }

    final class Integer extends DefaultBaseRedisData<Long> implements CompleteRequestRedisData {
        private static final int CACHE_LOWER_BOUND_VALUE = -128;
        private static final int CACHE_UPPER_BOUND_VALUE = 128;
        private static final Integer[] INTEGER_CACHE = new Integer[CACHE_UPPER_BOUND_VALUE - CACHE_LOWER_BOUND_VALUE + 1];
        static {
            for (int i = 0; i < INTEGER_CACHE.length; ++i) {
                INTEGER_CACHE[i] = new Integer((long) (i + CACHE_LOWER_BOUND_VALUE));
            }
        }

        private Integer(final long value) {
            super(value);
        }

        public static Integer newInstance(final long value) {
            return value >= CACHE_LOWER_BOUND_VALUE && value <= CACHE_UPPER_BOUND_VALUE ?
                    INTEGER_CACHE[(int) (value - CACHE_LOWER_BOUND_VALUE)] : new Integer(value);
        }

        @Override
        public long getLongValue() {
            return getValue();
        }

        @Override
        public Buffer toRESPArgument(final BufferAllocator allocator) {
            return toRespBulkString(getValue(), allocator);
        }
    }

    final class BulkStringSize extends DefaultBaseRedisData<java.lang.Integer> implements RequestRedisData {
        public BulkStringSize(final int value) {
            super(value);
        }

        @Override
        public int getIntValue() {
            return getValue();
        }

        @Override
        public Buffer toRESPArgument(final BufferAllocator allocator) {
            final byte[] sizeBytes = RedisCoercions.toAsciiBytes(getValue());
            return allocator.newBuffer(1 + sizeBytes.length + EOL_LENGTH)
                    .writeByte('$')
                    .writeBytes(sizeBytes)
                    .writeShort(EOL_SHORT);
        }
    }

    class BulkStringChunk extends DefaultBaseRedisData<Buffer> implements RequestRedisData {
        public BulkStringChunk(final Buffer value) {
            super(value);
        }

        @Override
        public Buffer getBufferValue() {
            return getValue();
        }

        @Override
        public Buffer toRESPArgument(final BufferAllocator allocator) {
            return getValue();
        }
    }

    class LastBulkStringChunk extends BulkStringChunk {
        public LastBulkStringChunk(final Buffer value) {
            super(value);
        }

        @Override
        public Buffer getBufferValue() {
            return getValue();
        }

        @Override
        public Buffer toRESPArgument(final BufferAllocator allocator) {
            return allocator.newBuffer(getValue().getReadableBytes() + EOL_LENGTH)
                    .writeBytes(getValue())
                    .writeShort(EOL_SHORT);
        }
    }

    final class CompleteBulkString extends LastBulkStringChunk implements CompleteRequestRedisData {
        public CompleteBulkString(final Buffer value) {
            super(value);
        }

        @Override
        public Buffer getBufferValue() {
            return getValue();
        }

        @Override
        public Buffer toRESPArgument(final BufferAllocator allocator) {
            return toRespBulkString(getValue(), allocator);
        }
    }

    final class ArraySize extends DefaultBaseRedisData<Long> implements RequestRedisData {
        public ArraySize(final long value) {
            super(value);
        }

        @Override
        public long getLongValue() {
            return getValue();
        }

        @Override
        public Buffer toRESPArgument(final BufferAllocator allocator) {
            return toRespArraySize(getValue(), allocator);
        }
    }

    final class Array<D extends RedisData> extends DefaultBaseRedisData<List<D>> implements CompleteRedisData {
        public Array(final List<D> value) {
            super(value);
        }

        @SafeVarargs
        public Array(final D... values) {
            this(asList(values));
        }

        @Override
        public List<? extends RedisData> getListValue() {
            return getValue();
        }
    }

    final class Null implements CompleteRedisData {
        private Null() {
        }
    }

    final class Error extends DefaultBaseRedisData<CharSequence> implements CompleteRedisData {
        public Error(final CharSequence value) {
            super(value);
        }

        @Override
        public CharSequence getCharSequenceValue() {
            return getValue();
        }
    }
}

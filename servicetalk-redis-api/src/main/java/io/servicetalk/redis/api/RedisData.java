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

import java.util.List;

import static io.servicetalk.redis.api.RedisRequests.calculateRequestArgumentArraySize;
import static io.servicetalk.redis.api.RedisRequests.calculateRequestArgumentLengthSize;
import static io.servicetalk.redis.api.RedisRequests.calculateRequestArgumentSize;
import static io.servicetalk.redis.api.RedisRequests.writeLength;
import static io.servicetalk.redis.api.RedisRequests.writeRequestArgument;
import static io.servicetalk.redis.api.RedisRequests.writeRequestArraySize;
import static io.servicetalk.redis.internal.RedisUtils.EOL_LENGTH;
import static io.servicetalk.redis.internal.RedisUtils.EOL_SHORT;
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
     * Get the {@code int} value of the data.
     *
     * @return the {@code int} value of the data.
     * @throws UnsupportedOperationException if the data is not an {@code int}
     */
    default int getIntValue() {
        throw new UnsupportedOperationException("Data is not of type int");
    }

    /**
     * Get the {@code long} value of the data.
     *
     * @return the {@code long} value of the data.
     * @throws UnsupportedOperationException if the data is not an {@code long}
     */
    default long getLongValue() {
        throw new UnsupportedOperationException("Data is not of type long");
    }

    /**
     * Get the {@link Buffer} value of the data.
     *
     * @return the {@link Buffer} value of the data.
     * @throws UnsupportedOperationException if the data is not an {@link Buffer}
     */
    default Buffer getBufferValue() {
        throw new UnsupportedOperationException("Data is not of type Buffer");
    }

    /**
     * Get the {@link CharSequence} value of the data.
     *
     * @return the {@link CharSequence} value of the data.
     * @throws UnsupportedOperationException if the data is not an {@link CharSequence}
     */
    default CharSequence getCharSequenceValue() {
        throw new UnsupportedOperationException("Data is not of type CharSequence");
    }

    /**
     * Get the {@link List} of {@link RedisData} value of the data.
     *
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
         * Get the number of bytes that this instance writes with {@link #encodeTo}
         *
         * @return the number of bytes that will be written to the buffer
         */
        int encodedByteCount();

        /**
         * Write this data to a {@link Buffer}.
         *
         * @param buffer the {@link Buffer} to write to
         */
        void encodeTo(Buffer buffer);

        default Buffer asBuffer(BufferAllocator allocator) {
            final Buffer buffer = allocator.newBuffer(encodedByteCount());
            encodeTo(buffer);
            return buffer;
        }
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

    /**
     * <a href="https://redis.io/topics/protocol#resp-simple-strings">Simple String</a> representation of
     * {@link RedisData}.
     */
    final class SimpleString extends DefaultBaseRedisData<CharSequence> implements CompleteRequestRedisData {
        public SimpleString(final CharSequence value) {
            super(value);
        }

        @Override
        public CharSequence getCharSequenceValue() {
            return getValue();
        }

        @Override
        public int encodedByteCount() {
            return calculateRequestArgumentSize(getValue());
        }

        @Override
        public void encodeTo(Buffer buf) {
            writeRequestArgument(buf, getValue());
        }
    }

    /**
     * <a href="https://redis.io/topics/protocol#resp-integers">Integer</a> representation of {@link RedisData}.
     */
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
        public int encodedByteCount() {
            return calculateRequestArgumentSize(getValue());
        }

        @Override
        public void encodeTo(final Buffer buf) {
            writeRequestArgument(buf, getValue());
        }
    }

    /**
     * Size part of <a href="https://redis.io/topics/protocol#resp-bulk-strings">Bulk String</a> representation of
     * {@link RedisData}.
     */
    final class BulkStringSize extends DefaultBaseRedisData<java.lang.Integer> implements RequestRedisData {
        public BulkStringSize(final int value) {
            super(value);
        }

        @Override
        public int getIntValue() {
            return getValue();
        }

        @Override
        public int encodedByteCount() {
            return calculateRequestArgumentLengthSize(getValue());
        }

        @Override
        public void encodeTo(final Buffer buffer) {
            writeLength(buffer, getValue());
        }
    }

    /**
     * One chunk of <a href="https://redis.io/topics/protocol#resp-bulk-strings">Bulk String</a> representation of
     * {@link RedisData}.
     */
    class BulkStringChunk extends DefaultBaseRedisData<Buffer> implements RequestRedisData {
        public BulkStringChunk(final Buffer value) {
            super(value);
        }

        @Override
        public Buffer getBufferValue() {
            return getValue();
        }

        @Override
        public int encodedByteCount() {
            return getValue().readableBytes();
        }

        @Override
        public void encodeTo(final Buffer buffer) {
            buffer.writeBytes(getValue());
        }
    }

    /**
     * The last chunk of <a href="https://redis.io/topics/protocol#resp-bulk-strings">Bulk String</a> representation of
     * {@link RedisData}.
     */
    class LastBulkStringChunk extends BulkStringChunk {
        public LastBulkStringChunk(final Buffer value) {
            super(value);
        }

        @Override
        public Buffer getBufferValue() {
            return getValue();
        }

        @Override
        public int encodedByteCount() {
            return super.encodedByteCount() + EOL_LENGTH;
        }

        @Override
        public void encodeTo(final Buffer buffer) {
            buffer.writeBytes(getValue())
                    .writeShort(EOL_SHORT);
        }
    }

    /**
     * Complete <a href="https://redis.io/topics/protocol#resp-bulk-strings">Bulk String</a> representation of
     * {@link RedisData}.
     */
    final class CompleteBulkString extends LastBulkStringChunk implements CompleteRequestRedisData {
        public CompleteBulkString(final Buffer value) {
            super(value);
        }

        @Override
        public Buffer getBufferValue() {
            return getValue();
        }

        @Override
        public void encodeTo(Buffer buf) {
            writeRequestArgument(buf, getValue());
        }

        @Override
        public int encodedByteCount() {
            return calculateRequestArgumentSize(getValue());
        }
    }

    /**
     * Size part of <a href="https://redis.io/topics/protocol#resp-arrays">Array</a> representation of
     * {@link RedisData}.
     */
    final class ArraySize extends DefaultBaseRedisData<Long> implements RequestRedisData {
        public ArraySize(final long value) {
            super(value);
        }

        @Override
        public long getLongValue() {
            return getValue();
        }

        @Override
        public int encodedByteCount() {
            return calculateRequestArgumentArraySize(getValue());
        }

        @Override
        public void encodeTo(final Buffer buffer) {
            writeRequestArraySize(buffer, getValue());
        }
    }

    /**
     * <a href="https://redis.io/topics/protocol#resp-arrays">Array</a> representation of {@link RedisData}.
     *
     * @param <D> Type of the array elements.
     */
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

    /**
     * <a href="https://redis.io/topics/protocol#null-elements-in-arrays">Null</a> representation of {@link RedisData}.
     */
    final class Null implements CompleteRedisData {
        private Null() {
        }
    }

    /**
     * <a href="https://redis.io/topics/protocol#resp-errors">Error</a> representation of {@link RedisData}.
     */
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

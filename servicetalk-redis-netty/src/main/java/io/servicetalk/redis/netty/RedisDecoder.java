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
package io.servicetalk.redis.netty;

import io.servicetalk.buffer.netty.BufferUtil;
import io.servicetalk.redis.api.RedisData;
import io.servicetalk.transport.netty.internal.ByteToMessageDecoder;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelHandlerContext;
import io.netty.util.ByteProcessor;
import io.netty.util.CharsetUtil;
import io.netty.util.internal.PlatformDependent;

import javax.annotation.Nullable;

import static io.servicetalk.redis.internal.RedisUtils.EOL_LENGTH;
import static io.servicetalk.redis.internal.RedisUtils.EOL_SHORT;

final class RedisDecoder extends ByteToMessageDecoder {
    private static final int NULL_VALUE = -1;
    private static final int REDIS_MESSAGE_MAX_LENGTH = 512 * 1024 * 1024; // 512MB
    private static final int POSITIVE_LONG_MAX_LENGTH = 19; // length of Long.MAX_VALUE

    private static final RedisData.CompleteBulkString EMPTY_INSTANCE = new RedisData.CompleteBulkString(BufferUtil.newBufferFrom(Unpooled.EMPTY_BUFFER));

    private final ToPositiveLongProcessor toPositiveLongProcessor = new ToPositiveLongProcessor();

    private final int maxInlineMessageLength;

    // current decoding states
    private State state = State.DECODE_TYPE;
    private RedisMessageType type = RedisMessageType.ERROR;
    private int remainingBulkLength;

    private enum State {
        DECODE_TYPE,
        DECODE_INLINE, // SIMPLE_STRING, ERROR, INTEGER
        DECODE_LENGTH, // BULK_STRING, ARRAY_HEADER
        DECODE_BULK_STRING_EOL,
        DECODE_BULK_STRING_CONTENT,
    }

    /**
     * Creates a new instance with default {@code maxInlineMessageLength}.
     */
    RedisDecoder() {
        // 1024 * 64 is max inline length of current Redis server implementation.
        this(1024 * 64);
    }

    /**
     * Creates a new instance.
     * @param maxInlineMessageLength the maximum length of inline message as defined by <a href="https://redis.io/topics/protocol"> Redis Inline Command</a>.
     */
    RedisDecoder(int maxInlineMessageLength) {
        if (maxInlineMessageLength <= 0 || maxInlineMessageLength > REDIS_MESSAGE_MAX_LENGTH) {
            throw new IllegalArgumentException("maxInlineMessageLength: " + maxInlineMessageLength +
                    " (expected: <= " + REDIS_MESSAGE_MAX_LENGTH + ")");
        }
        this.maxInlineMessageLength = maxInlineMessageLength;
    }

    @Override
    protected void decode(final ChannelHandlerContext ctx, final ByteBuf in) {
        try {
            for (;;) {
                switch (state) {
                    case DECODE_TYPE:
                        if (!decodeType(in)) {
                            return;
                        }
                        break;
                    case DECODE_INLINE:
                        if (!decodeInline(in, ctx)) {
                            return;
                        }
                        break;
                    case DECODE_LENGTH:
                        if (!decodeLength(in, ctx)) {
                            return;
                        }
                        break;
                    case DECODE_BULK_STRING_EOL:
                        if (!decodeBulkStringEndOfLine(in, ctx)) {
                            return;
                        }
                        break;
                    case DECODE_BULK_STRING_CONTENT:
                        if (!decodeBulkStringContent(in, ctx)) {
                            return;
                        }
                        break;
                    default:
                        throw new IllegalStateException("Unknown state: " + state);
                }
            }
        } catch (Exception e) {
            resetDecoder();
            throw e;
        }
    }

    private void resetDecoder() {
        state = State.DECODE_TYPE;
        remainingBulkLength = 0;
    }

    private boolean decodeType(ByteBuf in) {
        if (!in.isReadable()) {
            return false;
        }
        type = RedisMessageType.valueOf(in.readByte());
        state = type.isInline() ? State.DECODE_INLINE : State.DECODE_LENGTH;
        return true;
    }

    private boolean decodeInline(ByteBuf in, ChannelHandlerContext ctx) {
        ByteBuf lineBytes = readLine(in);
        if (lineBytes == null) {
            if (in.readableBytes() > maxInlineMessageLength) {
                throw new RedisCodecException("length: " + in.readableBytes() +
                        " (expected: <= " + maxInlineMessageLength + ")");
            }
            return false;
        }
        resetDecoder();
        ctx.fireChannelRead(newInlineRedisData(type, lineBytes));
        return true;
    }

    private boolean decodeLength(ByteBuf in, ChannelHandlerContext ctx) {
        ByteBuf lineByteBuf = readLine(in);
        if (lineByteBuf == null) {
            return false;
        }
        final long length = parseRedisNumber(lineByteBuf);
        if (length < NULL_VALUE) {
            throw new RedisCodecException("length: " + length + " (expected: >= " + NULL_VALUE + ")");
        }
        switch (type) {
            case ARRAY_HEADER:
                resetDecoder();
                if (length == NULL_VALUE) {
                    ctx.fireChannelRead(RedisData.NULL);
                } else {
                    ctx.fireChannelRead(new RedisData.ArraySize(length));
                }
                return true;
            case BULK_STRING:
                if (length > REDIS_MESSAGE_MAX_LENGTH) {
                    throw new RedisCodecException("length: " + length + " (expected: <= " +
                            REDIS_MESSAGE_MAX_LENGTH + ")");
                }
                remainingBulkLength = (int) length; // range(int) is already checked.
                return decodeBulkString(in, ctx);
            default:
                throw new RedisCodecException("bad type: " + type);
        }
    }

    private boolean decodeBulkString(ByteBuf in, ChannelHandlerContext ctx) {
        switch (remainingBulkLength) {
            case NULL_VALUE: // $-1\r\n
                resetDecoder();
                ctx.fireChannelRead(RedisData.NULL);
                return true;
            case 0:
                state = State.DECODE_BULK_STRING_EOL;
                return decodeBulkStringEndOfLine(in, ctx);
            default: // expectedBulkLength is always positive.
                state = State.DECODE_BULK_STRING_CONTENT;
                ctx.fireChannelRead(new RedisData.BulkStringSize(remainingBulkLength));
                return decodeBulkStringContent(in, ctx);
        }
    }

    // $0\r\n <here> \r\n
    private boolean decodeBulkStringEndOfLine(ByteBuf in, ChannelHandlerContext ctx) {
        if (in.readableBytes() < EOL_LENGTH) {
            return false;
        }
        readEndOfLine(in);
        resetDecoder();
        ctx.fireChannelRead(EMPTY_INSTANCE);
        return true;
    }

    // ${expectedBulkLength}\r\n <here> {data...}\r\n
    private boolean decodeBulkStringContent(ByteBuf in, ChannelHandlerContext ctx) {
        final int readableBytes = in.readableBytes();
        if (readableBytes == 0 || remainingBulkLength == 0 && readableBytes < EOL_LENGTH) {
            return false;
        }

        // if this is last frame.
        if (readableBytes >= remainingBulkLength + EOL_LENGTH) {
            ByteBuf content = in.readSlice(remainingBulkLength);
            readEndOfLine(in);
            resetDecoder();
            // Only call retain after readEndOfLine(...) as the method may throw an exception.
            ctx.fireChannelRead(new RedisData.LastBulkStringChunk(BufferUtil.newBufferFrom(content.retain())));
            return true;
        }

        // chunked write.
        int toRead = Math.min(remainingBulkLength, readableBytes);
        remainingBulkLength -= toRead;
        ctx.fireChannelRead(new RedisData.BulkStringChunk(BufferUtil.newBufferFrom(in.readSlice(toRead).retain())));
        return true;
    }

    private static void readEndOfLine(final ByteBuf in) {
        final short delim = in.readShort();
        if (EOL_SHORT == delim) {
            return;
        }
        final byte[] bytes = shortToBytes(delim);
        throw new RedisCodecException("delimiter: [" + bytes[0] + "," + bytes[1] + "] (expected: \\r\\n)");
    }

    private RedisData newInlineRedisData(RedisMessageType messageType, ByteBuf content) {
        switch (messageType) {
            case SIMPLE_STRING: {
                return new RedisData.SimpleString(content.toString(CharsetUtil.UTF_8));
            }
            case ERROR: {
                return new RedisData.Error(content.toString(CharsetUtil.UTF_8));
            }
            case INTEGER: {
                return RedisData.Integer.newInstance(parseRedisNumber(content));
            }
            default:
                throw new RedisCodecException("bad type: " + messageType);
        }
    }

    @Nullable
    private static ByteBuf readLine(ByteBuf in) {
        if (!in.isReadable(EOL_LENGTH)) {
            return null;
        }
        final int lfIndex = in.forEachByte(ByteProcessor.FIND_LF);
        if (lfIndex < 0) {
            return null;
        }
        ByteBuf data = in.readSlice(lfIndex - in.readerIndex() - 1); // `-1` is for CR
        readEndOfLine(in); // validate CR LF
        return data;
    }

    private long parseRedisNumber(ByteBuf byteBuf) {
        final int readableBytes = byteBuf.readableBytes();
        final boolean negative = readableBytes > 0 && byteBuf.getByte(byteBuf.readerIndex()) == '-';
        final int extraOneByteForNegative = negative ? 1 : 0;
        if (readableBytes <= extraOneByteForNegative) {
            throw new RedisCodecException("no number to parse: " + byteBuf.toString(CharsetUtil.US_ASCII));
        }
        if (readableBytes > POSITIVE_LONG_MAX_LENGTH + extraOneByteForNegative) {
            throw new RedisCodecException("too many characters to be a valid RESP Integer: " +
                    byteBuf.toString(CharsetUtil.US_ASCII));
        }
        if (negative) {
            return -parsePositiveNumber(byteBuf.skipBytes(extraOneByteForNegative));
        }
        return parsePositiveNumber(byteBuf);
    }

    private long parsePositiveNumber(ByteBuf byteBuf) {
        toPositiveLongProcessor.reset();
        byteBuf.forEachByte(toPositiveLongProcessor);
        return toPositiveLongProcessor.content();
    }

    private static final class ToPositiveLongProcessor implements ByteProcessor {
        private long result;

        @Override
        public boolean process(byte value) {
            if (value < '0' || value > '9') {
                throw new RedisCodecException("bad byte in number: " + value);
            }
            result = result * 10 + (value - '0');
            return true;
        }

        public long content() {
            return result;
        }

        public void reset() {
            result = 0;
        }
    }

    /**
     * Returns a {@code byte[]} of {@code short} value. This is opposite of {@code makeShort()}.
     */
    private static byte[] shortToBytes(short value) {
        byte[] bytes = new byte[2];
        if (PlatformDependent.BIG_ENDIAN_NATIVE_ORDER) {
            bytes[1] = (byte) ((value >> 8) & 0xff);
            bytes[0] = (byte) (value & 0xff);
        } else {
            bytes[0] = (byte) ((value >> 8) & 0xff);
            bytes[1] = (byte) (value & 0xff);
        }
        return bytes;
    }

    private enum RedisMessageType {

        SIMPLE_STRING((byte) '+', true),
        ERROR((byte) '-', true),
        INTEGER((byte) ':', true),
        BULK_STRING((byte) '$', false),
        ARRAY_HEADER((byte) '*', false),
        ARRAY((byte) '*', false); // for aggregated

        private final byte value;
        private final boolean inline;

        RedisMessageType(byte value, boolean inline) {
            this.value = value;
            this.inline = inline;
        }

        /**
         * Returns prefix {@code byte} for this type.
         */
        byte value() {
            return value;
        }

        /**
         * Returns {@code true} if this type is inline type, or returns {@code false}. If this is {@code true},
         * this type doesn't have length field.
         */
        boolean isInline() {
            return inline;
        }

        /**
         * Return {@link RedisMessageType} for this type prefix {@code byte}.
         */
        static RedisMessageType valueOf(byte value) {
            switch (value) {
                case '+':
                    return SIMPLE_STRING;
                case '-':
                    return ERROR;
                case ':':
                    return INTEGER;
                case '$':
                    return BULK_STRING;
                case '*':
                    return ARRAY_HEADER;
                default:
                    throw new RedisCodecException("Unknown RedisMessageType: " + value);
            }
        }
    }
}

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

import io.servicetalk.buffer.api.BufferAllocator;
import io.servicetalk.redis.api.RedisData;
import io.servicetalk.redis.api.RedisData.ArraySize;
import io.servicetalk.redis.api.RedisData.DefaultBulkStringChunk;
import io.servicetalk.redis.api.RedisData.DefaultFirstBulkStringChunk;
import io.servicetalk.redis.api.RedisData.SimpleString;
import io.servicetalk.transport.netty.internal.ByteToMessageDecoder;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelHandlerContext;
import io.netty.util.ByteProcessor;
import io.netty.util.CharsetUtil;

import static io.servicetalk.buffer.netty.BufferUtil.newBufferFrom;
import static io.servicetalk.redis.api.RedisData.NULL;
import static io.servicetalk.redis.internal.RedisUtils.EOL_LENGTH;
import static io.servicetalk.redis.internal.RedisUtils.EOL_SHORT;
import static io.servicetalk.redis.netty.RedisDecoder.State.Start;

final class RedisDecoder extends ByteToMessageDecoder {

    private static final DefaultFirstBulkStringChunk EMPTY_BULK_STRING = new DefaultFirstBulkStringChunk(
            newBufferFrom(Unpooled.EMPTY_BUFFER), 0);
    private static final SimpleString EMPTY_SIMPLE_STRING = new SimpleString("");

    enum State {
        Start,
        String,
        Error,
        Bulk,
        Array,
        Number,
        Reset,
    }

    private final LongParser longParser = new LongParser();
    private final IntParser intParser = new IntParser();
    private final BufferAllocator allocator;
    private State state = Start;
    private int expectBulkBytes;

    RedisDecoder(final BufferAllocator allocator) {
        this.allocator = allocator;
    }

    @Override
    protected void decode(final ChannelHandlerContext ctx, final ByteBuf in) {
        while (in.isReadable()) {
            switch (state) {
                case Start: {
                    final byte b = in.readByte();
                    final State next = nextState(b);
                    if (next == State.Reset) {
                        throw new IllegalStateException("Can't find the start of the next block");
                    }
                    state = next;
                    break;
                }
                case String: {
                    final int length = bytesUntilEol(in);
                    switch (length) {
                        case -1:
                            return;
                        case 0:
                            readEndOfLine(in);
                            ctx.fireChannelRead(EMPTY_SIMPLE_STRING);
                            break;
                        default:
                            ctx.fireChannelRead(new SimpleString(readString(in, length)));
                            break;
                    }
                    state = Start;
                    break;
                }
                case Number: {
                    final int length = bytesUntilEol(in);
                    if (length == -1) {
                        return;
                    }
                    final long n = length > 9 ? // Integer.MAX_VALUE length-1
                            readLong(in, length) :
                            (long) readInt(in, length);
                    ctx.fireChannelRead(RedisData.Integer.newInstance(n));
                    state = Start;
                    break;
                }
                case Bulk: {
                    final boolean first;
                    if (expectBulkBytes == 0) {
                        first = true;
                        final int length = bytesUntilEol(in);
                        if (length == -1) {
                            return;
                        }
                        final int size = readInt(in, length);
                        if (size == 0) {
                            readEndOfLine(in);
                            ctx.fireChannelRead(EMPTY_BULK_STRING);
                            state = Start;
                            break;
                        }
                        if (size == -1) {
                            ctx.fireChannelRead(NULL);
                            state = Start;
                            break;
                        }
                        expectBulkBytes = size;
                    } else if (expectBulkBytes < 0) {
                        if (in.isReadable(2)) {
                            readEndOfLine(in);
                            expectBulkBytes = 0;
                            state = Start;
                            break;
                        } else {
                            return;
                        }
                    } else {
                        first = false;
                    }
                    if (in.isReadable(expectBulkBytes + EOL_LENGTH)) {
                        // The while/rest of the bulk string, including the EOL, is readable.
                        final byte[] bytes = readBytes(in, expectBulkBytes);
                        fireBulkStringChunk(first, ctx, bytes);
                        expectBulkBytes -= bytes.length;
                        assert expectBulkBytes == 0;
                        state = Start;
                        return;
                    } else if (in.isReadable(expectBulkBytes)) {
                        // All of the string data is available, but not the whole EOL
                        final int len = Math.min(expectBulkBytes, in.readableBytes());
                        final byte[] bytes = readRawBytes(in, len);
                        fireBulkStringChunk(first, ctx, bytes);
                        expectBulkBytes -= bytes.length;
                        if (expectBulkBytes == 0) {
                            if (in.isReadable(2)) {
                                readEndOfLine(in);
                            } else {
                                expectBulkBytes = -2;
                            }
                        }
                    } else if (in.isReadable()) {
                        final byte[] bytes = readRawBytes(in, in.readableBytes());
                        fireBulkStringChunk(first, ctx, bytes);
                        expectBulkBytes -= bytes.length;
                    } else if (first) {
                        ctx.fireChannelRead(new DefaultFirstBulkStringChunk(allocator.newBuffer(), expectBulkBytes));
                    }
                    break;
                }
                case Error: {
                    final int length = bytesUntilEol(in);
                    if (length == -1) {
                        return;
                    }
                    final String message = readString(in, length);
                    ctx.fireChannelRead(new RedisData.Error(message));
                    state = Start;
                    break;
                }
                case Array: {
                    final int length = bytesUntilEol(in);
                    if (length == -1) {
                        return;
                    }
                    final int n = readInt(in, length);
                    ctx.fireChannelRead(n == -1 ? NULL : new ArraySize(n));
                    state = Start;
                    break;
                }
                default:
                    throw new IllegalStateException("Unexpected state: " + state);
            }
        }
    }

    private void fireBulkStringChunk(final boolean first, final ChannelHandlerContext ctx, final byte[] bytes) {
        if (first) {
            ctx.fireChannelRead(new DefaultFirstBulkStringChunk(allocator.wrap(bytes), expectBulkBytes));
        } else {
            ctx.fireChannelRead(new DefaultBulkStringChunk(allocator.wrap(bytes)));
        }
    }

    private static State nextState(final byte b) {
        switch (b) {
            case '+':
                return State.String;
            case '$':
                return State.Bulk;
            case '*':
                return State.Array;
            case '-':
                return State.Error;
            case ':':
                return State.Number;
            default:
                return State.Reset;
        }
    }

    private long readLong(final ByteBuf in, final int length) {
        final long number = longParser.parse(in, length);
        in.skipBytes(length);
        readEndOfLine(in);
        return number;
    }

    private int readInt(final ByteBuf in, final int length) {
        final int number = intParser.parse(in, length);
        in.skipBytes(length);
        readEndOfLine(in);
        return number;
    }

    private static String readString(final ByteBuf in, final int length) {
        final String string = in.toString(in.readerIndex(), length, CharsetUtil.UTF_8);
        in.skipBytes(length);
        readEndOfLine(in);
        return string;
    }

    private static byte[] readBytes(final ByteBuf in, final int length) {
        final byte[] data = new byte[length];
        in.readBytes(data, 0, length);
        readEndOfLine(in);
        return data;
    }

    private static byte[] readRawBytes(final ByteBuf in, final int length) {
        final byte[] data = new byte[length];
        in.readBytes(data, 0, length);
        return data;
    }

    private static void readEndOfLine(final ByteBuf in) {
        final short endOfLine = in.readShort();
        if (EOL_SHORT == endOfLine) {
            return;
        }
        throw new IllegalStateException("expected: \\r\\n received: " + Integer.toHexString(endOfLine));
    }

    private static int bytesUntilEol(final ByteBuf in) {
        final int fromIndex = in.readerIndex() + 1;
        final int toIndex = in.writerIndex();
        // don't even make the call if less than 2 bytes
        if (toIndex > fromIndex) {
            // search for last byte first
            final int i = in.indexOf(fromIndex, toIndex, (byte) '\n');
            if (i > 0) {
                assert in.getByte(i - 1) == '\r';
                return i - fromIndex;
            }
        }
        return -1;
    }

    private static final class LongParser implements ByteProcessor {
        private long result;

        @Override
        public boolean process(final byte b) {
            assert b >= '0' && b <= '9' : "expected '0'..'9'";
            final int digit = b - '0';
            result = 10 * result + digit;
            return true;
        }

        long parse(final ByteBuf in, final int length) {
            final int current = in.readerIndex();
            final int first = in.getByte(current);
            if (length == 1) {
                return first - '0';
            }
            final boolean negative = first == '-';
            result = negative ? 0 : first - '0';
            in.forEachByte(current + 1, length - 1, this);
            return negative ? -result : result;
        }
    }

    private static final class IntParser implements ByteProcessor {
        private int result;

        @Override
        public boolean process(final byte b) {
            assert b >= '0' && b <= '9' : "expected '0'..'9'";
            final int digit = b - '0';
            result = 10 * result + digit;
            return true;
        }

        int parse(final ByteBuf in, final int length) {
            final int current = in.readerIndex();
            final int first = in.getByte(current);
            if (length == 1) {
                return first - '0';
            }
            final boolean negative = first == '-';
            result = negative ? 0 : first - '0';
            in.forEachByte(current + 1, length - 1, this);
            return negative ? -result : result;
        }
    }
}

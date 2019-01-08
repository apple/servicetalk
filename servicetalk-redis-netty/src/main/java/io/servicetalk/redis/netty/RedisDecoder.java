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

import io.servicetalk.buffer.api.Buffer;
import io.servicetalk.buffer.api.BufferAllocator;
import io.servicetalk.redis.api.RedisData;
import io.servicetalk.redis.api.RedisData.ArraySize;
import io.servicetalk.redis.api.RedisData.BulkStringChunk;
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
import static io.servicetalk.buffer.netty.BufferUtil.toByteBufNoThrow;
import static io.servicetalk.redis.api.RedisData.NULL;
import static io.servicetalk.redis.internal.RedisUtils.EOL_SHORT;
import static io.servicetalk.redis.netty.RedisDecoder.State.SkipEol;
import static io.servicetalk.redis.netty.RedisDecoder.State.Start;
import static java.lang.Math.min;

final class RedisDecoder extends ByteToMessageDecoder {

    private static final DefaultFirstBulkStringChunk EMPTY_BULK_STRING = new DefaultFirstBulkStringChunk(
            newBufferFrom(Unpooled.EMPTY_BUFFER), 0);
    private static final SimpleString EMPTY_SIMPLE_STRING = new SimpleString("");

    enum State {
        Start,
        String,
        Error,
        Bulk,
        SkipEol,
        Array,
        Number,
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
                    state = nextState(b);
                    break;
                }
                case String: {
                    final int length = bytesUntilEol(in);
                    switch (length) {
                        case -1:
                            return;
                        case 0:
                            readEndOfLine(in);
                            state = Start;
                            ctx.fireChannelRead(EMPTY_SIMPLE_STRING);
                            break;
                        default:
                            state = Start;
                            ctx.fireChannelRead(new SimpleString(readCharSequence(in, length)));
                            break;
                    }
                    break;
                }
                case Number: {
                    final int length = bytesUntilEol(in);
                    if (length == -1) {
                        return;
                    }
                    final long n = readLong(in, length);
                    state = Start;
                    ctx.fireChannelRead(RedisData.Integer.newInstance(n));
                    break;
                }
                case Bulk: {
                    final BulkStringChunk chunk;
                    if (expectBulkBytes == 0) {
                        final int length = bytesUntilEol(in);
                        if (length == -1) {
                            return;
                        }
                        final int size = readInt(in, length);
                        if (size == 0) {
                            // An empty bulk string is sent as $0\r\n\r\n, so this is reading the 2nd \r\n, after the
                            // absent (0 bytes long) chunk.
                            readEndOfLine(in);
                            state = Start;
                            ctx.fireChannelRead(EMPTY_BULK_STRING);
                            break;
                        }
                        if (size == -1) {
                            // A null bulk string is sent as $-1\r\n, so there is no 2nd \r\n to read.
                            state = Start;
                            ctx.fireChannelRead(NULL);
                            break;
                        }
                        expectBulkBytes = size;
                        final int len = min(expectBulkBytes, in.readableBytes());
                        chunk = readBulkStringChunk(in, true, len);
                    } else {
                        chunk = readBulkStringChunk(in, false, min(expectBulkBytes, in.readableBytes()));
                    }
                    if (expectBulkBytes == 0) {
                        state = SkipEol;
                    }
                    ctx.fireChannelRead(chunk);
                    break;
                }
                case SkipEol:
                    if (!in.isReadable(2)) {
                        return;
                    }
                    readEndOfLine(in);
                    state = Start;
                    break;
                case Error: {
                    final int length = bytesUntilEol(in);
                    if (length == -1) {
                        return;
                    }
                    final CharSequence message = readCharSequence(in, length);
                    state = Start;
                    ctx.fireChannelRead(new RedisData.Error(message));
                    break;
                }
                case Array: {
                    final int length = bytesUntilEol(in);
                    if (length == -1) {
                        return;
                    }
                    final int n = readInt(in, length);
                    state = Start;
                    ctx.fireChannelRead(n == -1 ? NULL : new ArraySize(n));
                    break;
                }
                default:
                    throw new IllegalStateException("Unexpected state: " + state);
            }
        }
    }

    private BulkStringChunk readBulkStringChunk(final ByteBuf in, final boolean first, final int len) {
        final Buffer bytes = allocator.newBuffer(len);
        in.readBytes(toByteBufNoThrow(bytes), len);
        final int bulkStringLength = this.expectBulkBytes;
        expectBulkBytes -= len;
        return first ? new DefaultFirstBulkStringChunk(bytes, bulkStringLength) :
                new DefaultBulkStringChunk(bytes);
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
                throw new IllegalStateException("Can't find the start of the next block. b=" + b);
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

    private static CharSequence readCharSequence(final ByteBuf in, final int length) {
        final CharSequence cs = in.readCharSequence(length, CharsetUtil.UTF_8);
        readEndOfLine(in);
        return cs;
    }

    private static void readEndOfLine(final ByteBuf in) {
        final short endOfLine = in.readShort();
        if (EOL_SHORT == endOfLine) {
            return;
        }
        throw new IllegalStateException("expected: \\r\\n received: " + Integer.toHexString(endOfLine));
    }

    private static int bytesUntilEol(final ByteBuf in) {
        int indexOfSlashN = in.forEachByte(value -> value != '\n');
        if (indexOfSlashN >= 1) {
            return in.getByte(indexOfSlashN - 1) == '\r' ? (indexOfSlashN - in.readerIndex() - 1) : -1;
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

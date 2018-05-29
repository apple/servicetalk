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
/*
 * Copyright 2012 The Netty Project
 *
 * The Netty Project licenses this file to you under the Apache License,
 * version 2.0 (the "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at:
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */
package io.servicetalk.http.netty;

import io.servicetalk.buffer.api.Buffer;
import io.servicetalk.http.api.HttpHeaders;
import io.servicetalk.http.api.HttpMetaData;
import io.servicetalk.http.api.HttpPayloadChunk;
import io.servicetalk.http.api.LastHttpPayloadChunk;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufUtil;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelOutboundHandlerAdapter;
import io.netty.channel.ChannelPromise;
import io.netty.util.concurrent.PromiseCombiner;

import java.util.Map;

import static io.netty.buffer.ByteBufUtil.writeMediumBE;
import static io.netty.buffer.ByteBufUtil.writeShortBE;
import static io.netty.buffer.Unpooled.EMPTY_BUFFER;
import static io.netty.buffer.Unpooled.directBuffer;
import static io.netty.buffer.Unpooled.unreleasableBuffer;
import static io.netty.buffer.Unpooled.wrappedBuffer;
import static io.netty.handler.codec.http.HttpConstants.COLON;
import static io.netty.handler.codec.http.HttpConstants.CR;
import static io.netty.handler.codec.http.HttpConstants.LF;
import static io.netty.handler.codec.http.HttpConstants.SP;
import static io.netty.util.internal.StringUtil.simpleClassName;
import static io.servicetalk.buffer.netty.BufferUtil.toByteBufNoThrow;
import static io.servicetalk.http.api.CharSequences.unwrapBuffer;
import static io.servicetalk.http.netty.HeaderUtils.isTransferEncodingChunked;
import static java.lang.Long.toHexString;
import static java.lang.Math.max;
import static java.nio.charset.StandardCharsets.US_ASCII;

abstract class HttpObjectEncoder<T extends HttpMetaData> extends ChannelOutboundHandlerAdapter {
    static final int CRLF_SHORT = (CR << 8) | LF;
    private static final int ZERO_CRLF_MEDIUM = ('0' << 16) | CRLF_SHORT;
    private static final byte[] ZERO_CRLF_CRLF = {'0', CR, LF, CR, LF};
    private static final ByteBuf CRLF_BUF = unreleasableBuffer(directBuffer(2).writeByte(CR).writeByte(LF));
    private static final ByteBuf ZERO_CRLF_CRLF_BUF = unreleasableBuffer(directBuffer(ZERO_CRLF_CRLF.length)
            .writeBytes(ZERO_CRLF_CRLF));
    private static final float HEADERS_WEIGHT_NEW = 1 / 5f;
    private static final float HEADERS_WEIGHT_HISTORICAL = 1 - HEADERS_WEIGHT_NEW;
    private static final float TRAILERS_WEIGHT_NEW = HEADERS_WEIGHT_NEW;
    private static final float TRAILERS_WEIGHT_HISTORICAL = HEADERS_WEIGHT_HISTORICAL;
    private static final int COLON_AND_SPACE_SHORT = (COLON << 8) | SP;
    private static final int ST_INIT = 0;
    private static final int ST_CONTENT_NON_CHUNK = 1;
    private static final int ST_CONTENT_CHUNK = 2;
    private static final int ST_CONTENT_ALWAYS_EMPTY = 3;

    @SuppressWarnings("RedundantFieldInitialization")
    private int state = ST_INIT;

    /**
     * Used to calculate an exponential moving average of the encoded size of the initial line and the headers for
     * a guess for future buffer allocations.
     */
    private float headersEncodedSizeAccumulator;

    /**
     * Used to calculate an exponential moving average of the encoded size of the trailers for
     * a guess for future buffer allocations.
     */
    private float trailersEncodedSizeAccumulator;

    /**
     * Create a new instance.
     * @param headersEncodedSizeAccumulator Used to calculate an exponential moving average of the encoded size of the
     * initial line and the headers for a guess for future buffer allocations.
     * @param trailersEncodedSizeAccumulator  Used to calculate an exponential moving average of the encoded size of
     * the trailers for a guess for future buffer allocations.
     */
    HttpObjectEncoder(int headersEncodedSizeAccumulator, int trailersEncodedSizeAccumulator) {
        this.headersEncodedSizeAccumulator = max(16, headersEncodedSizeAccumulator);
        this.trailersEncodedSizeAccumulator = max(16, trailersEncodedSizeAccumulator);
    }

    @Override
    public void write(ChannelHandlerContext ctx, Object msg, ChannelPromise promise) throws Exception {
        ByteBuf byteBuf = null;
        if (msg instanceof HttpMetaData) {
            if (state != ST_INIT) {
                throw new IllegalStateException("unexpected message type: " + simpleClassName(msg));
            }

            T metaData = castMetaData(msg);

            byteBuf = ctx.alloc().buffer((int) headersEncodedSizeAccumulator);
            // Encode the message.
            encodeInitialLine(byteBuf, metaData);
            state = isContentAlwaysEmpty(metaData) ? ST_CONTENT_ALWAYS_EMPTY :
                    isTransferEncodingChunked(metaData.getHeaders()) ? ST_CONTENT_CHUNK : ST_CONTENT_NON_CHUNK;

            sanitizeHeadersBeforeEncode(metaData, state == ST_CONTENT_ALWAYS_EMPTY);

            encodeHeaders(metaData.getHeaders(), byteBuf);
            writeShortBE(byteBuf, CRLF_SHORT);

            headersEncodedSizeAccumulator = HEADERS_WEIGHT_NEW * padSizeForAccumulation(byteBuf.readableBytes()) +
                                            HEADERS_WEIGHT_HISTORICAL * headersEncodedSizeAccumulator;
        }

        // Bypass the encoder in case of an empty buffer, so that the following idiom works:
        //
        //     ch.write(Unpooled.EMPTY_BUFFER).addListener(ChannelFutureListener.CLOSE);
        //
        // See https://github.com/netty/netty/issues/2983 for more information.
        if (msg instanceof Buffer) {
            final Buffer potentialEmptyBuf = (Buffer) msg;
            if (potentialEmptyBuf.getReadableBytes() == 0) {
                // We can directly write EMPTY_BUFFER here because there is no need to worry about the buffer being
                // already released.
                ctx.write(EMPTY_BUFFER, promise);
                return;
            }
        }

        if (msg instanceof HttpPayloadChunk || msg instanceof Buffer) {
            switch (state) {
                case ST_INIT:
                    throw new IllegalStateException("unexpected message type: " + simpleClassName(msg));
                case ST_CONTENT_NON_CHUNK:
                    final long contentLength = contentLength(msg);
                    if (contentLength > 0) {
                        if (byteBuf != null && byteBuf.writableBytes() >= contentLength && msg instanceof HttpPayloadChunk) {
                            // merge into other buffer for performance reasons
                            writeBufferToByteBuf(((HttpPayloadChunk) msg).getContent(), byteBuf.writerIndex(), byteBuf);
                            ctx.write(byteBuf, promise);
                        } else {
                            if (byteBuf != null) {
                                PromiseCombiner promiseCombiner = new PromiseCombiner();
                                promiseCombiner.add(ctx.write(byteBuf));
                                promiseCombiner.add(ctx.write(encodeAndRetain(msg)));
                                promiseCombiner.finish(promise);
                            } else {
                                ctx.write(encodeAndRetain(msg), promise);
                            }
                        }

                        if (msg instanceof LastHttpPayloadChunk) {
                            state = ST_INIT;
                        }

                        break;
                    }

                    // fall-through!
                case ST_CONTENT_ALWAYS_EMPTY:
                    if (byteBuf != null) {
                        // We allocated a buffer so add it now.
                        ctx.write(byteBuf, promise);
                    } else {
                        // Need to produce some output otherwise an
                        // IllegalStateException will be thrown as we did not write anything
                        // Its ok to just write an EMPTY_BUFFER as if there are reference count issues these will be
                        // propagated as the caller of the encodeAndRetain(...) method will release the original
                        // buffer.
                        // Writing an empty buffer will not actually write anything on the wire, so if there is a user
                        // error with msg it will not be visible externally
                        ctx.write(EMPTY_BUFFER, promise);
                    }

                    break;
                case ST_CONTENT_CHUNK:
                    PromiseCombiner promiseCombiner = new PromiseCombiner();
                    if (byteBuf != null) {
                        // We allocated a buffer so write it now.
                        promiseCombiner.add(ctx.write(byteBuf));
                    }
                    encodeChunkedContent(ctx, msg, contentLength(msg), promiseCombiner);
                    promiseCombiner.finish(promise);

                    break;
                default:
                    throw new Error();
            }

            if (msg instanceof LastHttpPayloadChunk) {
                state = ST_INIT;
            }
        } else if (byteBuf != null) {
            ctx.write(byteBuf, promise);
        }
    }

    /**
     * Determine whether a message has a content or not. Some message may have headers indicating
     * a content without having an actual content, e.g the response to an HEAD or CONNECT request.
     *
     * @param msg the message to test
     * @return {@code true} to signal the message has no content
     */
    protected boolean isContentAlwaysEmpty(@SuppressWarnings("unused") T msg) {
        return false;
    }

    /**
     * Allows to sanitize headers of the message before encoding these.
     */
    protected abstract void sanitizeHeadersBeforeEncode(T msg, boolean isAlwaysEmpty);

    /**
     * Cast the {@code httpMetaData} object to the runtime type {@code T}.
     * @param httpMetaData The object to cast.
     * @return the {@code httpMetaData} object as runtime type {@code T}.
     */
    protected abstract T castMetaData(Object httpMetaData);

    /**
     * Encode the <a href="https://tools.ietf.org/html/rfc7230.html#section-3.1">start line</a>.
     * @param buf The {@link ByteBuf} to encode to.
     * @param message The message to encode.
     */
    protected abstract void encodeInitialLine(ByteBuf buf, T message);

    /**
     * Encode the {@link HttpHeaders} into a {@link ByteBuf}.
     */
    private static void encodeHeaders(HttpHeaders headers, ByteBuf buf) {
        for (Map.Entry<CharSequence, CharSequence> header : headers) {
            encoderHeader(header.getKey(), header.getValue(), buf);
        }
    }

    private void encodeChunkedContent(ChannelHandlerContext ctx, Object msg, long contentLength,
                                      PromiseCombiner promiseCombiner) {
        if (contentLength > 0) {
            String lengthHex = toHexString(contentLength);
            ByteBuf buf = ctx.alloc().buffer(lengthHex.length() + 2);
            buf.writeCharSequence(lengthHex, US_ASCII);
            writeShortBE(buf, CRLF_SHORT);
            promiseCombiner.add(ctx.write(buf));
            promiseCombiner.add(ctx.write(encodeAndRetain(msg)));
            promiseCombiner.add(ctx.write(CRLF_BUF.duplicate()));
        }

        if (msg instanceof LastHttpPayloadChunk) {
            HttpHeaders headers = ((LastHttpPayloadChunk) msg).getTrailers();
            if (headers.isEmpty()) {
                promiseCombiner.add(ctx.write(ZERO_CRLF_CRLF_BUF.duplicate()));
            } else {
                ByteBuf buf = ctx.alloc().buffer((int) trailersEncodedSizeAccumulator);
                writeMediumBE(buf, ZERO_CRLF_MEDIUM);
                encodeHeaders(headers, buf);
                writeShortBE(buf, CRLF_SHORT);
                trailersEncodedSizeAccumulator = TRAILERS_WEIGHT_NEW * padSizeForAccumulation(buf.readableBytes()) +
                        TRAILERS_WEIGHT_HISTORICAL * trailersEncodedSizeAccumulator;
                promiseCombiner.add(ctx.write(buf));
            }
        } else if (contentLength == 0) {
            // Need to produce some output otherwise an
            // IllegalStateException will be thrown
            promiseCombiner.add(ctx.write(encodeAndRetain(msg)));
        }
    }

    /**
     * Add some additional overhead to the buffer. The rational is that it is better to slightly over allocate and waste
     * some memory, rather than under allocate and require a resize/copy.
     * @param readableBytes The readable bytes in the buffer.
     * @return The {@code readableBytes} with some additional padding.
     */
    private static int padSizeForAccumulation(int readableBytes) {
        return (readableBytes << 2) / 3;
    }

    private static void encoderHeader(CharSequence name, CharSequence value, ByteBuf buf) {
        final int nameLen = name.length();
        final int valueLen = value.length();
        final int entryLen = nameLen + valueLen + 4;
        buf.ensureWritable(entryLen);
        int offset = buf.writerIndex();
        writeAscii(buf, offset, name);
        offset += nameLen;
        ByteBufUtil.setShortBE(buf, offset, COLON_AND_SPACE_SHORT);
        offset += 2;
        writeAscii(buf, offset, value);
        offset += valueLen;
        ByteBufUtil.setShortBE(buf, offset, CRLF_SHORT);
        offset += 2;
        buf.writerIndex(offset);
    }

    private static void writeAscii(ByteBuf dst, int dstOffset, CharSequence value) {
        Buffer buffer = unwrapBuffer(value);
        if (buffer != null) {
            writeBufferToByteBuf(buffer, dstOffset, dst);
        } else {
            dst.setCharSequence(dstOffset, value, US_ASCII);
        }
    }

    private static void writeBufferToByteBuf(Buffer src, int dstOffset, ByteBuf dst) {
        ByteBuf byteBuf = toByteBufNoThrow(src);
        if (byteBuf != null) {
            // We don't want to modify either src or byteBuf's reader/writer indexes so use the setBytes method which
            // doesn't modify indexes.
            dst.setBytes(dstOffset, byteBuf, byteBuf.readerIndex(), byteBuf.readableBytes());
        } else {
            // This will modify the position of the src's NIO ByteBuffer, however the API of toNioBuffer says that the
            // indexes of the ByteBuffer and the Buffer are independent.
            dst.setBytes(dstOffset, src.toNioBuffer());
        }
    }

    private static long contentLength(Object msg) {
        if (msg instanceof HttpPayloadChunk) {
            return ((HttpPayloadChunk) msg).getContent().getReadableBytes();
        } else if (msg instanceof Buffer) {
            return ((Buffer) msg).getReadableBytes();
        }
        throw new IllegalStateException("unexpected message type: " + simpleClassName(msg));
    }

    private static ByteBuf encodeAndRetain(Object msg) {
        // We still want to retain the objects we encode because otherwise folks may hold on to references of objects
        // with a 0 reference count and get an IllegalReferenceCountException.
        if (msg instanceof HttpPayloadChunk) {
            return toByteBuf(((HttpPayloadChunk) msg).getContent()).retain();
        } else if (msg instanceof Buffer) {
            return toByteBuf(((Buffer) msg)).retain();
        }
        throw new IllegalStateException("unexpected message type: " + simpleClassName(msg));
    }

    static ByteBuf toByteBuf(Buffer buffer) {
        ByteBuf byteBuf = toByteBufNoThrow(buffer);
        return byteBuf != null ? byteBuf : wrappedBuffer(buffer.toNioBuffer());
    }
}

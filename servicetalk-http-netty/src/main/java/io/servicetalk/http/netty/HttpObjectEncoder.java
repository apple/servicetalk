/*
 * Copyright © 2018-2021 Apple Inc. and the ServiceTalk project authors
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
import io.servicetalk.transport.netty.internal.CloseHandler;

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
import static io.servicetalk.buffer.api.CharSequences.unwrapBuffer;
import static io.servicetalk.buffer.netty.BufferUtils.newBufferFrom;
import static io.servicetalk.buffer.netty.BufferUtils.toByteBufNoThrow;
import static io.servicetalk.http.api.HeaderUtils.isTransferEncodingChunked;
import static io.servicetalk.http.api.HttpHeaderNames.CONTENT_LENGTH;
import static io.servicetalk.http.api.HttpProtocolVersion.HTTP_1_1;
import static io.servicetalk.http.netty.HttpKeepAlive.shouldClose;
import static java.lang.Long.toHexString;
import static java.lang.Math.max;
import static java.nio.charset.StandardCharsets.US_ASCII;

abstract class HttpObjectEncoder<T extends HttpMetaData> extends ChannelOutboundHandlerAdapter {
    static final int CRLF_SHORT = (CR << 8) | LF;
    private static final int ZERO_CRLF_MEDIUM = ('0' << 16) | CRLF_SHORT;
    private static final byte[] ZERO_CRLF_CRLF = {'0', CR, LF, CR, LF};
    private static final ByteBuf CRLF_BUF = unreleasableBuffer(directBuffer(2).writeByte(CR).writeByte(LF)
            .asReadOnly());
    private static final ByteBuf ZERO_CRLF_CRLF_BUF = unreleasableBuffer(directBuffer(ZERO_CRLF_CRLF.length)
            .writeBytes(ZERO_CRLF_CRLF).asReadOnly());
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
    private final CloseHandler closeHandler;

    /**
     * Create a new instance.
     * @param headersEncodedSizeAccumulator Used to calculate an exponential moving average of the encoded size of the
     * initial line and the headers for a guess for future buffer allocations.
     * @param trailersEncodedSizeAccumulator  Used to calculate an exponential moving average of the encoded size of
     * the trailers for a guess for future buffer allocations.
     * @param closeHandler observes protocol state events
     */
    HttpObjectEncoder(int headersEncodedSizeAccumulator, int trailersEncodedSizeAccumulator,
                      final CloseHandler closeHandler) {
        this.headersEncodedSizeAccumulator = max(16, headersEncodedSizeAccumulator);
        this.trailersEncodedSizeAccumulator = max(16, trailersEncodedSizeAccumulator);
        this.closeHandler = closeHandler;
    }

    @Override
    public void write(ChannelHandlerContext ctx, Object msg, ChannelPromise promise) {
        if (msg instanceof HttpMetaData) {
            if (state != ST_INIT) {
                throw new IllegalStateException("unexpected message type: " + simpleClassName(msg));
            }
            T metaData = castMetaData(msg);
            closeHandler.protocolPayloadBeginOutbound(ctx);
            if (shouldClose(metaData)) {
                closeHandler.protocolClosingOutbound(ctx);
            }

            // We prefer a direct allocation here because it is expected the resulted encoded Buffer will be written
            // to a socket. In order to do the write to the socket the memory typically needs to be allocated in direct
            // memory and will be copied to direct memory if not. Using a direct buffer will avoid the copy.
            ByteBuf byteBuf = ctx.alloc().directBuffer((int) headersEncodedSizeAccumulator);
            try {
                Buffer stBuf = newBufferFrom(byteBuf);

                // Encode the message.
                encodeInitialLine(stBuf, metaData);
                state = isContentAlwaysEmpty(metaData) ? ST_CONTENT_ALWAYS_EMPTY :
                        isTransferEncodingChunked(metaData.headers()) ? ST_CONTENT_CHUNK : ST_CONTENT_NON_CHUNK;

                sanitizeHeadersBeforeEncode(metaData, state);

                encodeHeaders(metaData.headers(), byteBuf, stBuf);
                writeShortBE(byteBuf, CRLF_SHORT);
                headersEncodedSizeAccumulator = HEADERS_WEIGHT_NEW * padSizeForAccumulation(byteBuf.readableBytes()) +
                                                HEADERS_WEIGHT_HISTORICAL * headersEncodedSizeAccumulator;
            } catch (Throwable e) {
                // Encoding of meta-data can fail or cause expansion of the initial ByteBuf capacity that can fail
                byteBuf.release();
                throw e;
            }
            ctx.write(byteBuf, promise);
        } else if (msg instanceof Buffer) {
            final Buffer stBuffer = (Buffer) msg;
            if (stBuffer.readableBytes() == 0) {
                // Bypass the encoder in case of an empty buffer, so that the following idiom works:
                //
                //     ch.write(Unpooled.EMPTY_BUFFER).addListener(ChannelFutureListener.CLOSE);
                //
                // See https://github.com/netty/netty/issues/2983 for more information.
                // We can directly write EMPTY_BUFFER here because there is no need to worry about the buffer being
                // already released.
                ctx.write(EMPTY_BUFFER, promise);
            } else {
                switch (state) {
                    case ST_INIT:
                        throw new IllegalStateException("unexpected message type: " + simpleClassName(msg));
                    case ST_CONTENT_NON_CHUNK:
                        final long contentLength = stBuffer.readableBytes();
                        if (contentLength > 0) {
                            ctx.write(encodeAndRetain(stBuffer), promise);
                            break;
                        }

                        // fall-through!
                    case ST_CONTENT_ALWAYS_EMPTY:
                        // Need to produce some output otherwise an IllegalStateException will be thrown as we did
                        // not write anything Its ok to just write an EMPTY_BUFFER as if there are reference count
                        // issues these will be propagated as the caller of the encodeAndRetain(...) method will
                        // release the original buffer. Writing an empty buffer will not actually write anything on
                        // the wire, so if there is a user error with msg it will not be visible externally
                        ctx.write(EMPTY_BUFFER, promise);
                        break;
                    case ST_CONTENT_CHUNK:
                        PromiseCombiner promiseCombiner = new PromiseCombiner();
                        encodeChunkedContent(ctx, stBuffer, stBuffer.readableBytes(), promiseCombiner);
                        promiseCombiner.finish(promise);
                        break;
                    default:
                        throw new Error();
                }
            }
        } else if (msg instanceof HttpHeaders) {
            closeHandler.protocolPayloadEndOutbound(ctx, promise);
            final int oldState = state;
            state = ST_INIT;
            if (oldState == ST_CONTENT_CHUNK) {
                encodeAndWriteTrailers(ctx, (HttpHeaders) msg, promise);
            } else {
                ctx.write(EMPTY_BUFFER, promise);
            }
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

    private void sanitizeHeadersBeforeEncode(T msg, int state) {
        if (state == ST_CONTENT_CHUNK && HTTP_1_1.equals(msg.version())) {
            // A sender MUST remove the received Content-Length field prior to forwarding a "Transfer-Encoding: chunked"
            // message downstream: https://tools.ietf.org/html/rfc7230#section-3.3.3
            msg.headers().remove(CONTENT_LENGTH);
        }
        sanitizeHeadersBeforeEncode(msg, state == ST_CONTENT_ALWAYS_EMPTY);
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
     * @param buf The {@link Buffer} to encode to.
     * @param message The message to encode.
     */
    protected abstract void encodeInitialLine(Buffer buf, T message);

    /**
     * Encode the {@link HttpHeaders} into a {@link ByteBuf}.
     */
    private static void encodeHeaders(HttpHeaders headers, ByteBuf byteBuf) {
        encodeHeaders(headers, byteBuf, newBufferFrom(byteBuf));
    }

    /**
     * Encode the {@link HttpHeaders} into a buffer represented by two references: {@link ByteBuf} and {@link Buffer}.
     * We reference the same buffer as {@link ByteBuf} and {@link Buffer} to avoid allocation of wrapping layer if
     * necessary for optimized data transfer to have an instance of {@link Buffer}.
     */
    private static void encodeHeaders(HttpHeaders headers, ByteBuf byteBuf, Buffer buffer) {
        for (Map.Entry<CharSequence, CharSequence> header : headers) {
            encodeHeader(header.getKey(), header.getValue(), byteBuf, buffer);
        }
    }

    private static void encodeChunkedContent(ChannelHandlerContext ctx, Buffer msg, long contentLength,
                                             PromiseCombiner promiseCombiner) {
        if (contentLength > 0) {
            String lengthHex = toHexString(contentLength);
            ByteBuf buf = ctx.alloc().directBuffer(lengthHex.length() + 2);
            try {
                buf.writeCharSequence(lengthHex, US_ASCII);
                writeShortBE(buf, CRLF_SHORT);
            } catch (Throwable e) {
                buf.release();
                throw e;
            }
            promiseCombiner.add(ctx.write(buf));
            promiseCombiner.add(ctx.write(encodeAndRetain(msg)));
            promiseCombiner.add(ctx.write(CRLF_BUF.duplicate()));
        } else {
            assert contentLength == 0;
            // Need to produce some output otherwise an
            // IllegalStateException will be thrown
            promiseCombiner.add(ctx.write(encodeAndRetain(msg)));
        }
    }

    private void encodeAndWriteTrailers(ChannelHandlerContext ctx, HttpHeaders headers, ChannelPromise promise) {
        if (headers.isEmpty()) {
            ctx.write(ZERO_CRLF_CRLF_BUF.duplicate(), promise);
        } else {
            ByteBuf buf = ctx.alloc().directBuffer((int) trailersEncodedSizeAccumulator);
            try {
                writeMediumBE(buf, ZERO_CRLF_MEDIUM);
                encodeHeaders(headers, buf);
                writeShortBE(buf, CRLF_SHORT);
                trailersEncodedSizeAccumulator = TRAILERS_WEIGHT_NEW * padSizeForAccumulation(buf.readableBytes()) +
                        TRAILERS_WEIGHT_HISTORICAL * trailersEncodedSizeAccumulator;
            } catch (Throwable e) {
                // Encoding of trailers can fail or cause expansion of the initial ByteBuf capacity that can fail
                buf.release();
                throw e;
            }
            ctx.write(buf, promise);
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

    private static void encodeHeader(CharSequence name, CharSequence value, ByteBuf byteBuf, Buffer buffer) {
        final int nameLen = name.length();
        final int valueLen = value.length();
        final int entryLen = nameLen + valueLen + 4;
        byteBuf.ensureWritable(entryLen);
        int offset = byteBuf.writerIndex();
        writeAscii(name, byteBuf, buffer, offset);
        offset += nameLen;
        ByteBufUtil.setShortBE(byteBuf, offset, COLON_AND_SPACE_SHORT);
        offset += 2;
        writeAscii(value, byteBuf, buffer, offset);
        offset += valueLen;
        ByteBufUtil.setShortBE(byteBuf, offset, CRLF_SHORT);
        offset += 2;
        byteBuf.writerIndex(offset);
    }

    private static void writeAscii(CharSequence value, ByteBuf dstByteBuf, Buffer dstBuffer, int dstOffset) {
        Buffer valueBuffer = unwrapBuffer(value);
        if (valueBuffer != null) {
            writeBufferToByteBuf(valueBuffer, dstByteBuf, dstBuffer, dstOffset);
        } else {
            dstByteBuf.setCharSequence(dstOffset, value, US_ASCII);
        }
    }

    private static void writeBufferToByteBuf(Buffer src, ByteBuf dstByteBuf, Buffer dstBuffer, int dstOffset) {
        ByteBuf byteBuf = toByteBufNoThrow(src);
        if (byteBuf != null) {
            // We don't want to modify either src or dst's reader/writer indexes so use the setBytes method which
            // doesn't modify indexes.
            dstByteBuf.setBytes(dstOffset, byteBuf, byteBuf.readerIndex(), byteBuf.readableBytes());
        } else {
            // Use src.getBytes instead of dstByteBuf.setBytes to utilize internal optimizations of ReadOnlyByteBuffer.
            // We don't want to modify either src or dst's reader/writer indexes so use the getBytes method which
            // doesn't modify indexes.
            src.getBytes(src.readerIndex(), dstBuffer, dstOffset, src.readableBytes());
        }
    }

    static ByteBuf encodeAndRetain(Buffer msg) {
        // We still want to retain the objects we encode because otherwise folks may hold on to references of objects
        // with a 0 reference count and get an IllegalReferenceCountException.
        // TODO(scott): add support for file region
        return toByteBuf(msg).retain();
    }

    private static ByteBuf toByteBuf(Buffer buffer) {
        ByteBuf byteBuf = toByteBufNoThrow(buffer);
        return byteBuf != null ? byteBuf : wrappedBuffer(buffer.toNioBuffer());
    }
}

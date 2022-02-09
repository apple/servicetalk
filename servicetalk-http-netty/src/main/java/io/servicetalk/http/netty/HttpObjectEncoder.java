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
import io.servicetalk.http.api.EmptyHttpHeaders;
import io.servicetalk.http.api.HttpHeaderNames;
import io.servicetalk.http.api.HttpHeaderValues;
import io.servicetalk.http.api.HttpHeaders;
import io.servicetalk.http.api.HttpMetaData;
import io.servicetalk.transport.netty.internal.CloseHandler;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufUtil;
import io.netty.channel.ChannelDuplexHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPromise;
import io.netty.util.concurrent.PromiseCombiner;

import java.io.IOException;
import java.util.Map;
import javax.annotation.Nullable;

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
import static io.servicetalk.buffer.api.CharSequences.unwrapBuffer;
import static io.servicetalk.buffer.netty.BufferUtils.newBufferFrom;
import static io.servicetalk.buffer.netty.BufferUtils.toByteBufNoThrow;
import static io.servicetalk.http.api.HeaderUtils.isTransferEncodingChunked;
import static io.servicetalk.http.api.HttpHeaderNames.CONTENT_LENGTH;
import static io.servicetalk.http.api.HttpHeaderNames.TRANSFER_ENCODING;
import static io.servicetalk.http.api.HttpHeaderValues.CHUNKED;
import static io.servicetalk.http.api.HttpProtocolVersion.HTTP_1_1;
import static io.servicetalk.http.netty.HttpKeepAlive.shouldClose;
import static java.lang.Long.toHexString;
import static java.lang.Math.max;
import static java.nio.charset.StandardCharsets.US_ASCII;

abstract class HttpObjectEncoder<T extends HttpMetaData> extends ChannelDuplexHandler {
    static final int CRLF_SHORT = (CR << 8) | LF;
    private static final int ZERO_CRLF_MEDIUM = ('0' << 16) | CRLF_SHORT;
    private static final int COLON_AND_SPACE_SHORT = (COLON << 8) | SP;
    private static final byte[] ZERO_CRLF_CRLF = {'0', CR, LF, CR, LF};
    private static final ByteBuf CRLF_BUF = unreleasableBuffer(directBuffer(2).writeByte(CR).writeByte(LF)
            .asReadOnly());
    private static final ByteBuf ZERO_CRLF_CRLF_BUF = unreleasableBuffer(directBuffer(ZERO_CRLF_CRLF.length)
            .writeBytes(ZERO_CRLF_CRLF).asReadOnly());
    private static final float HEADERS_WEIGHT_NEW = 1 / 5f;
    private static final float HEADERS_WEIGHT_HISTORICAL = 1 - HEADERS_WEIGHT_NEW;
    private static final float TRAILERS_WEIGHT_NEW = HEADERS_WEIGHT_NEW;
    private static final float TRAILERS_WEIGHT_HISTORICAL = HEADERS_WEIGHT_HISTORICAL;
    private static final long CONTENT_LEN_INIT = Long.MIN_VALUE;
    private static final long CONTENT_LEN_EMPTY = Long.MIN_VALUE + 1;
    private static final long CONTENT_LEN_CHUNKED = Long.MIN_VALUE + 2;
    private static final long CONTENT_LEN_CONSUMED = Long.MIN_VALUE + 3;
    private static final long CONTENT_LEN_LARGEST_VALUE = CONTENT_LEN_CONSUMED;

    private long state = CONTENT_LEN_INIT;

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
    private boolean messageSent;

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
            T metaData = castMetaData(msg);
            final boolean realResponse = !isInterim(metaData);
            if (!realResponse && messageSent) {
                // Discard an "interim message" if it arrives after a "real message" has been sent.
                return;
            }

            if (state == CONTENT_LEN_CHUNKED) {
                // The user didn't write any trailers, so just send the last chunk.
                encodeAndWriteTrailers(ctx, EmptyHttpHeaders.INSTANCE, promise);
            } else if (state > 0) {
                tryTooLittleContent(ctx, msg, promise);
                return;
            } else if (state == -1) {
                unknownContentLengthNewRequest(ctx);
            }
            if (realResponse) {
                // Notify the CloseHandler only about "real" messages. We don't expose "interim messages", like 1xx
                // responses to the user, and handle them internally.
                messageSent = true;
                closeHandler.protocolPayloadBeginOutbound(ctx);
                if (shouldClose(metaData)) {
                    closeHandler.protocolClosingOutbound(ctx);
                }
            }

            // We prefer a direct allocation here because it is expected the resulted encoded Buffer will be written
            // to a socket. In order to do the write to the socket the memory typically needs to be allocated in direct
            // memory and will be copied to direct memory if not. Using a direct buffer will avoid the copy.
            ByteBuf byteBuf = ctx.alloc().directBuffer((int) headersEncodedSizeAccumulator);
            try {
                Buffer stBuf = newBufferFrom(byteBuf);

                // Encode the message.
                encodeInitialLine(ctx, stBuf, metaData);
                onMetaData(ctx, metaData);
                if (isContentAlwaysEmpty(metaData)) {
                    state = CONTENT_LEN_EMPTY;
                    if (realResponse) {
                        signalProtocolPayloadEndOutbound(ctx, promise);
                    }
                } else if (isTransferEncodingChunked(metaData.headers())) {
                    state = CONTENT_LEN_CHUNKED;
                } else {
                    state = getContentLength(metaData);
                    assert state > CONTENT_LEN_LARGEST_VALUE;
                    if (state == 0) {
                        contentLenConsumed(ctx, promise);
                    }
                }

                sanitizeHeadersBeforeEncode(metaData);

                encodeHeaders(metaData.headers(), byteBuf, stBuf);
                writeShortBE(byteBuf, CRLF_SHORT);
                headersEncodedSizeAccumulator = HEADERS_WEIGHT_NEW * padSizeForAccumulation(byteBuf.readableBytes()) +
                                                HEADERS_WEIGHT_HISTORICAL * headersEncodedSizeAccumulator;
            } catch (Throwable e) {
                // Encoding of meta-data can fail or cause expansion of the initial ByteBuf capacity that can fail
                byteBuf.release();
                tryIoException(ctx, e, promise);
                return;
            }

            if (realResponse) {
                ctx.write(byteBuf, promise);
            } else {
                // All "interim messages" have to be flushed right away.
                ctx.writeAndFlush(byteBuf, promise);
            }
        } else if (msg instanceof Buffer) {
            final Buffer stBuffer = (Buffer) msg;
            final int readableBytes = stBuffer.readableBytes();
            if (readableBytes <= 0) {
                ctx.write(EMPTY_BUFFER, promise);
            } else if (state == CONTENT_LEN_CHUNKED) {
                PromiseCombiner promiseCombiner = new PromiseCombiner(ctx.executor());
                encodeChunkedContent(ctx, stBuffer, stBuffer.readableBytes(), promiseCombiner);
                promiseCombiner.finish(promise);
            } else if (state <= CONTENT_LEN_LARGEST_VALUE || state >= 0 && (state -= readableBytes) < 0) {
                // state may be <0 if there is no content-length or transfer-encoding, so let this pass through, but if
                // state would go negative (or already zeroed) then fail.
                tryTooMuchContent(ctx, readableBytes, promise);
            } else {
                if (state == 0) {
                    contentLenConsumed(ctx, promise);
                }
                ctx.write(encodeAndRetain(stBuffer), promise);
            }
        } else if (msg instanceof HttpHeaders) {
            final boolean isChunked = state == CONTENT_LEN_CHUNKED;
            state = CONTENT_LEN_INIT;
            final HttpHeaders trailers = (HttpHeaders) msg;
            if (isChunked) {
                signalProtocolPayloadEndOutbound(ctx, promise);
                encodeAndWriteTrailers(ctx, trailers, promise);
            } else if (!trailers.isEmpty()) {
                tryFailNonEmptyTrailers(ctx, trailers, promise);
            } else if (state > 0) {
                tryTooLittleContent(ctx, promise);
            } else {
                // Allow trailers to be written as a marker indicating the request is done.
                if (state != CONTENT_LEN_CONSUMED) {
                    signalProtocolPayloadEndOutbound(ctx, promise);
                }
                state = CONTENT_LEN_INIT;
                ctx.write(EMPTY_BUFFER, promise);
            }
        }
    }

    private static void tryFailNonEmptyTrailers(ChannelHandlerContext ctx, HttpHeaders trailers,
                                                ChannelPromise promise) {
        promise.tryFailure(new IOException("Trailers are only supported for HTTP/1.x with " +
                TRANSFER_ENCODING + ": " + CHUNKED + ". Channel: " + ctx.channel() +
                " attempted to write non-empty trailers: " + trailers));
    }

    private void tryTooMuchContent(ChannelHandlerContext ctx, int bytes, ChannelPromise promise) {
        if (state == CONTENT_LEN_EMPTY) {
            promise.tryFailure(new IOException("payload body must be empty, but write of: " + bytes +
                    " bytes attempted on channel: " + ctx.channel()));
        } else {
            promise.tryFailure(new IOException("payload body size exceeded content-length header. write of " +
                    bytes + " bytes attempted, " +
                    ((state <= CONTENT_LEN_LARGEST_VALUE) ? 0 : state + bytes) +
                    " bytes remaining on channel: " + ctx.channel()));
        }
    }

    private void tryTooLittleContent(ChannelHandlerContext ctx, Object msg, ChannelPromise promise) {
        assert state > 0;
        promise.tryFailure(new IOException("Failed to complete encoding. Expected " + state +
                " remaining bytes from content-length but new request/response " + msg + " started on channel: " +
                ctx.channel()));
    }

    private void tryTooLittleContent(ChannelHandlerContext ctx, ChannelPromise promise) {
        assert state > 0;
        promise.tryFailure(new IOException("Failed to complete encoding. Expected " + state +
                " remaining bytes from content-length but the request/response terminated on channel: " +
                ctx.channel()));
    }

    private void unknownContentLengthNewRequest(ChannelHandlerContext ctx) {
        // If state == -1 we don't know the content length, signal end of outbound and best effort continue.
        ChannelPromise emptyWritePromise = ctx.newPromise();
        ctx.write(EMPTY_BUFFER, emptyWritePromise);
        signalProtocolPayloadEndOutbound(ctx, emptyWritePromise);
    }

    private static void tryIoException(ChannelHandlerContext ctx, Throwable e, ChannelPromise promise) {
        promise.tryFailure(e instanceof IOException ? e :
                new IOException("unexpected exception while encoding on channel: " + ctx.channel(), e));
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
     * Determines when a {@code msg} is interim.
     *
     * @param msg a message to analyze
     * @return {@code true} if it's a continuation message.
     */
    protected boolean isInterim(@SuppressWarnings("unused") T msg) {
        return false;
    }

    /**
     * Callback when a meta-data object have been encoded.
     *
     * @param ctx the {@link ChannelHandlerContext} for which the write operation is made.
     * @param metaData the meta-data message.
     */
    protected void onMetaData(final ChannelHandlerContext ctx, final T metaData) {
        // noop
    }

    private void sanitizeHeadersBeforeEncode(T msg) {
        if (state == CONTENT_LEN_CHUNKED && HTTP_1_1.equals(msg.version())) {
            // A sender MUST remove the received Content-Length field prior to forwarding a "Transfer-Encoding: chunked"
            // message downstream: https://tools.ietf.org/html/rfc7230#section-3.3.3
            msg.headers().remove(CONTENT_LENGTH);
        }
        sanitizeHeadersBeforeEncode(msg, state == CONTENT_LEN_EMPTY);
    }

    /**
     * Allows to signal when a content have been consumed.
     *
     * @param ctx the {@link ChannelHandlerContext} for which the write operation is made.
     * @param promise the {@link ChannelPromise} to notify once the operation completes.
     */
    protected final void contentLenConsumed(final ChannelHandlerContext ctx, @Nullable final ChannelPromise promise) {
        state = CONTENT_LEN_CONSUMED;
        signalProtocolPayloadEndOutbound(ctx, promise);
    }

    protected final void signalProtocolPayloadEndOutbound(final ChannelHandlerContext ctx,
                                                          @Nullable final ChannelPromise promise) {
        messageSent = false;
        closeHandler.protocolPayloadEndOutbound(ctx, promise);
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
     * @param ctx the {@link ChannelHandlerContext} for which the write operation is made.
     * @param buf The {@link Buffer} to encode to.
     * @param message The message to encode.
     */
    protected abstract void encodeInitialLine(ChannelHandlerContext ctx, Buffer buf, T message);

    /**
     * Get the expected length of content when {@link HttpHeaderNames#TRANSFER_ENCODING} is not
     * {@link HttpHeaderValues#CHUNKED}.
     * @param message The message to encode.
     * @return Expected length of content when {@link HttpHeaderNames#TRANSFER_ENCODING} is not
     * {@link HttpHeaderValues#CHUNKED}, or {@code -1} if unknown.
     */
    protected abstract long getContentLength(T message);

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
        return toByteBuf(msg).retain();
    }

    private static ByteBuf toByteBuf(Buffer buffer) {
        ByteBuf byteBuf = toByteBufNoThrow(buffer);
        return byteBuf != null ? byteBuf : wrappedBuffer(buffer.toNioBuffer());
    }
}

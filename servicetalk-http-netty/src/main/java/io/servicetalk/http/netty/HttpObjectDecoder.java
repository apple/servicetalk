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
import io.servicetalk.http.api.HttpHeadersFactory;
import io.servicetalk.http.api.HttpMetaData;
import io.servicetalk.http.api.HttpProtocolVersion;
import io.servicetalk.http.api.HttpRequestMetaData;
import io.servicetalk.http.api.HttpResponseMetaData;
import io.servicetalk.transport.netty.internal.ByteToMessageDecoder;
import io.servicetalk.transport.netty.internal.CloseHandler;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.DecoderException;
import io.netty.handler.codec.PrematureChannelClosureException;
import io.netty.handler.codec.TooLongFrameException;
import io.netty.handler.codec.http.HttpExpectationFailedEvent;
import io.netty.util.AsciiString;
import io.netty.util.ByteProcessor;

import javax.annotation.Nullable;

import static io.netty.buffer.Unpooled.copiedBuffer;
import static io.netty.handler.codec.http.HttpConstants.CR;
import static io.netty.handler.codec.http.HttpConstants.LF;
import static io.netty.util.ByteProcessor.FIND_LINEAR_WHITESPACE;
import static io.netty.util.ByteProcessor.FIND_NON_LINEAR_WHITESPACE;
import static io.servicetalk.buffer.netty.BufferUtil.newBufferFrom;
import static io.servicetalk.http.api.CharSequences.emptyAsciiString;
import static io.servicetalk.http.api.CharSequences.newAsciiString;
import static io.servicetalk.http.api.HttpHeaderNames.CONTENT_LENGTH;
import static io.servicetalk.http.api.HttpHeaderNames.SEC_WEBSOCKET_KEY1;
import static io.servicetalk.http.api.HttpHeaderNames.SEC_WEBSOCKET_KEY2;
import static io.servicetalk.http.api.HttpHeaderNames.SEC_WEBSOCKET_LOCATION;
import static io.servicetalk.http.api.HttpHeaderNames.SEC_WEBSOCKET_ORIGIN;
import static io.servicetalk.http.api.HttpHeaderNames.UPGRADE;
import static io.servicetalk.http.api.HttpProtocolVersions.HTTP_1_0;
import static io.servicetalk.http.api.HttpProtocolVersions.HTTP_1_1;
import static io.servicetalk.http.api.HttpProtocolVersions.newProtocolVersion;
import static io.servicetalk.http.api.HttpRequestMethods.GET;
import static io.servicetalk.http.api.HttpResponseStatuses.SWITCHING_PROTOCOLS;
import static io.servicetalk.http.netty.HeaderUtils.isTransferEncodingChunked;
import static io.servicetalk.http.netty.HeaderUtils.setTransferEncodingChunked;
import static io.servicetalk.http.netty.HttpKeepAlive.shouldClose;
import static java.lang.Character.isISOControl;
import static java.lang.Character.isWhitespace;
import static java.lang.Math.min;
import static java.nio.charset.StandardCharsets.US_ASCII;
import static java.util.Objects.requireNonNull;

abstract class HttpObjectDecoder<T extends HttpMetaData> extends ByteToMessageDecoder {
    private static final ByteBuf HTTP_1_1_BUF = copiedBuffer("HTTP/1.1", US_ASCII);
    private static final ByteBuf HTTP_1_0_BUF = copiedBuffer("HTTP/1.0", US_ASCII);
    private static final byte COLON_BYTE = (byte) ':';
    private static final byte SPACE_BYTE = (byte) ' ';
    private static final byte HTAB_BYTE = (byte) '\t';
    private static final ByteProcessor FIND_COLON_OR_WHITE_SPACE =
            value -> value != COLON_BYTE && value != SPACE_BYTE && value != HTAB_BYTE;
    private static final ByteProcessor FIND_COLON =
            value -> value != COLON_BYTE;
    private static final ByteProcessor SKIP_CONTROL_CHARS_PROCESSOR = value ->
        value == SPACE_BYTE || value == HTAB_BYTE || isISOControl((char) (value & 0xff));
    private static final int MAX_HEX_CHARS_FOR_LONG = 16; // 0x7FFFFFFFFFFFFFFF == Long.MAX_INT
    private static final int CHUNK_DELIMETER_SIZE = 2; // CRLF

    private final int maxInitialLineSize;
    private final int maxHeaderSize;

    private final HttpHeadersFactory headersFactory;
    private final CloseHandler closeHandler;
    @Nullable
    private T message;
    @Nullable
    private HttpHeaders trailer;
    private long chunkSize;
    private int cumulationIndex = -1;
    private long contentLength = Long.MIN_VALUE;

    /**
     * The internal state of {@link HttpObjectDecoder}.
     */
    private enum State {
        SKIP_CONTROL_CHARS,
        READ_INITIAL,
        READ_HEADER,
        READ_VARIABLE_LENGTH_CONTENT,
        READ_FIXED_LENGTH_CONTENT,
        READ_CHUNK_SIZE,
        READ_CHUNKED_CONTENT,
        READ_CHUNK_DELIMITER,
        READ_CHUNK_FOOTER,
        UPGRADED
    }

    private State currentState = State.SKIP_CONTROL_CHARS;

    /**
     * Creates a new instance with the specified parameters.
     */
    protected HttpObjectDecoder(HttpHeadersFactory headersFactory, int maxInitialLineLength, int maxHeaderSize,
                                final CloseHandler closeHandler) {
        this.closeHandler = closeHandler;
        if (maxInitialLineLength <= 0) {
            throw new IllegalArgumentException("maxInitialLineLength: " + maxInitialLineLength + " (expected >0)");
        }
        if (maxHeaderSize <= 0) {
            throw new IllegalArgumentException("maxHeaderSize: " + maxHeaderSize + " (expected >0)");
        }
        this.headersFactory = requireNonNull(headersFactory);
        this.maxHeaderSize = maxHeaderSize;
        this.maxInitialLineSize = maxInitialLineLength;
    }

    final HttpHeadersFactory headersFactory() {
        return headersFactory;
    }

    /**
     * Determine if this {@link HttpObjectDecoder} is responsible for decoding requests or not. Behavior may differ
     * if a request/response is being parsed around request/response termination. See
     * <a href="https://tools.ietf.org/html/rfc7230.html#section-3.3.3">RFC 7230, section 3.3.3</a> for more details.
     * @return {@code true} if requests are being decoded.
     */
    protected abstract boolean isDecodingRequest();

    /**
     * Create a new {@link HttpMetaData} because a new request/response has been parsed.
     * @param first The first item in the
     * <a href="https://tools.ietf.org/html/rfc7230.html#section-3.1">start line</a>.
     * @param second The second item in the
     * <a href="https://tools.ietf.org/html/rfc7230.html#section-3.1">start line</a>.
     * @param third The third item in the
     * <a href="https://tools.ietf.org/html/rfc7230.html#section-3.1">start line</a>.
     * @return a new {@link HttpMetaData} that represents the parsed
     * <a href="https://tools.ietf.org/html/rfc7230.html#section-3.1">start line</a>.
     */
    protected abstract T createMessage(ByteBuf first, ByteBuf second, ByteBuf third);

    @Override
    protected final void decode(ChannelHandlerContext ctx, ByteBuf buffer) {
        switch (currentState) {
            case SKIP_CONTROL_CHARS: {
                if (!skipControlCharacters(buffer)) {
                    return;
                }
                currentState = State.READ_INITIAL;
            }
            case READ_INITIAL: {
                final int lfIndex = findCRLF(buffer, maxInitialLineSize);
                if (lfIndex < 0) {
                    return;
                }

                // Parse the initial line:
                // https://tools.ietf.org/html/rfc7230#section-3.1.1
                // request-line = method SP request-target SP HTTP-version CRLF
                // https://tools.ietf.org/html/rfc7230#section-3.1.2
                // status-line = HTTP-version SP status-code SP reason-phrase CRLF
                final int nonControlIndex = lfIndex - 2;
                int aStart = buffer.forEachByte(FIND_NON_LINEAR_WHITESPACE);
                if (aStart < 0) {
                    splitInitialLineError();
                }
                int aEnd = buffer.forEachByte(aStart + 1, nonControlIndex - aStart, FIND_LINEAR_WHITESPACE);
                if (aEnd < 0) {
                    splitInitialLineError();
                }

                int bStart = buffer.forEachByte(aEnd + 1, nonControlIndex - aEnd, FIND_NON_LINEAR_WHITESPACE);
                if (bStart < 0) {
                    splitInitialLineError();
                }
                int bEnd = buffer.forEachByte(bStart + 1, nonControlIndex - bStart, FIND_LINEAR_WHITESPACE);
                if (bEnd < 0) {
                    splitInitialLineError();
                }

                int cStart = buffer.forEachByte(bEnd + 1, nonControlIndex - bEnd, FIND_NON_LINEAR_WHITESPACE);
                int cEnd = -1;
                if (cStart >= 0) {
                    // Find End Of String
                    cEnd = buffer.forEachByteDesc(cStart, lfIndex - cStart, FIND_NON_LINEAR_WHITESPACE);
                    if (cEnd < 0) {
                        splitInitialLineError();
                    }
                }

                // Consume the initial line bytes from the buffer.
                consumeCRLF(buffer, lfIndex);

                message = createMessage(buffer.slice(aStart, aEnd - aStart),
                                        buffer.slice(bStart, bEnd - bStart),
                                        cEnd >= 0 ? buffer.slice(cStart, cEnd - cStart) : Unpooled.EMPTY_BUFFER);
                currentState = State.READ_HEADER;
                closeHandler.protocolPayloadBeginInbound(ctx);
                // fall-through
            }
            case READ_HEADER: {
                State nextState = readHeaders(buffer);
                if (nextState == null) {
                    return;
                }
                assert message != null;
                if (shouldClose(message)) {
                    closeHandler.protocolClosingInbound(ctx);
                }
                currentState = nextState;
                switch (nextState) {
                    case SKIP_CONTROL_CHARS:
                        // fast-path
                        // No content is expected.
                        ctx.fireChannelRead(message);
                        ctx.fireChannelRead(headersFactory.newEmptyTrailers());
                        closeHandler.protocolPayloadEndInbound(ctx);
                        resetNow();
                        return;
                    case READ_CHUNK_SIZE:
                        // Chunked encoding - generate HttpMessage first.  HttpChunks will follow.
                        ctx.fireChannelRead(message);
                        return;
                    default:
                        // <a href="https://tools.ietf.org/html/rfc7230#section-3.3.3">RFC 7230, 3.3.3</a> states that if a
                        // request does not have either a transfer-encoding or a content-length header then the message body
                        // length is 0. However for a response the body length is the number of octets received prior to the
                        // server closing the connection. So we treat this as variable length chunked encoding.
                        long contentLength = contentLength();
                        if (contentLength == 0 || contentLength == -1 && isDecodingRequest()) {
                            ctx.fireChannelRead(message);
                            ctx.fireChannelRead(headersFactory.newEmptyTrailers());
                            closeHandler.protocolPayloadEndInbound(ctx);
                            resetNow();
                            return;
                        }

                        assert nextState == State.READ_FIXED_LENGTH_CONTENT ||
                                nextState == State.READ_VARIABLE_LENGTH_CONTENT;

                        ctx.fireChannelRead(message);

                        if (nextState == State.READ_FIXED_LENGTH_CONTENT) {
                            // chunkSize will be decreased as the READ_FIXED_LENGTH_CONTENT state reads data chunk by chunk.
                            chunkSize = contentLength;
                        }

                        // We return here, this forces decode to be called again where we will decode the content
                        return;
                }
                // fall-through
            }
            case READ_VARIABLE_LENGTH_CONTENT: {
                // Keep reading data as a chunk until the end of connection is reached.
                int toRead = buffer.readableBytes();
                if (toRead > 0) {
                    ByteBuf content = buffer.readRetainedSlice(toRead);
                    cumulationIndex = buffer.readerIndex();
                    ctx.fireChannelRead(newBufferFrom(content));
                }
                return;
            }
            case READ_FIXED_LENGTH_CONTENT: {
                int toRead = buffer.readableBytes();

                // Check if the buffer is readable first as we use the readable byte count
                // to create the HttpChunk. This is needed as otherwise we may end up with
                // create a HttpChunk instance that contains an empty buffer and so is
                // handled like it is the last HttpChunk.
                //
                // See https://github.com/netty/netty/issues/433
                if (toRead == 0) {
                    return;
                }

                if (toRead > chunkSize) {
                    toRead = (int) chunkSize;
                }
                ByteBuf content = buffer.readRetainedSlice(toRead);
                chunkSize -= toRead;
                cumulationIndex = buffer.readerIndex();

                if (chunkSize == 0) {
                    // Read all content.
                    // https://tools.ietf.org/html/rfc7230.html#section-4.1
                    // This is not chunked encoding so there will not be any trailers.
                    ctx.fireChannelRead(newBufferFrom(content));
                    ctx.fireChannelRead(headersFactory.newEmptyTrailers());
                    closeHandler.protocolPayloadEndInbound(ctx);
                    resetNow();
                } else {
                    ctx.fireChannelRead(newBufferFrom(content));
                }
                return;
            }
            // everything else after this point takes care of reading chunked content. basically, read chunk size,
            // read chunk, read and ignore the CRLF and repeat until 0
            case READ_CHUNK_SIZE: {
                int lfIndex = findCRLF(buffer, MAX_HEX_CHARS_FOR_LONG);
                if (lfIndex < 0) {
                    return;
                }
                long chunkSize = getChunkSize(buffer, lfIndex);
                consumeCRLF(buffer, lfIndex);
                this.chunkSize = chunkSize;
                if (chunkSize == 0) {
                    currentState = State.READ_CHUNK_FOOTER;
                    return;
                }
                currentState = State.READ_CHUNKED_CONTENT;
                // fall-through
            }
            case READ_CHUNKED_CONTENT: {
                assert chunkSize <= Integer.MAX_VALUE;
                final int toRead = min((int) chunkSize, buffer.readableBytes());
                if (toRead == 0) {
                    return;
                }
                Buffer chunk = newBufferFrom(buffer.readRetainedSlice(toRead));
                chunkSize -= toRead;
                cumulationIndex = buffer.readerIndex();

                ctx.fireChannelRead(chunk);

                if (chunkSize != 0) {
                    return;
                }
                currentState = State.READ_CHUNK_DELIMITER;
                // fall-through
            }
            case READ_CHUNK_DELIMITER: {
                // Read the chunk delimiter
                int lfIndex = findCRLF(buffer, CHUNK_DELIMETER_SIZE);
                if (lfIndex < 0) {
                    return;
                }
                consumeCRLF(buffer, lfIndex);
                currentState = State.READ_CHUNK_SIZE;
                break;
            }
            case READ_CHUNK_FOOTER: {
                HttpHeaders trailer = readTrailingHeaders(buffer);
                if (trailer == null) {
                    return;
                }
                ctx.fireChannelRead(trailer);
                closeHandler.protocolPayloadEndInbound(ctx);
                resetNow();
                return;
            }
            case UPGRADED: {
                int readableBytes = buffer.readableBytes();
                if (readableBytes > 0) {
                    // Keep on consuming as otherwise we may trigger an DecoderException,
                    // other handler will replace this codec with the upgraded protocol codec to
                    // take the traffic over at some point then.
                    // See https://github.com/netty/netty/issues/2173
                    ByteBuf opaquePayload = buffer.readBytes(readableBytes);
                    cumulationIndex = buffer.readerIndex();
                    // TODO(scott): revisit how upgrades are going to be done. Do we use Netty buffers or not?
                    ctx.fireChannelRead(opaquePayload);
                }
                break;
            }
            default:
                throw new Error();
        }
    }

    @Override
    protected final void discardSomeReadBytes() {
        final int readerIndex = cumulationReaderIndex();
        super.discardSomeReadBytes();
        cumulationIndex -= readerIndex - cumulationReaderIndex();
    }

    @Override
    protected void cumulationReset() {
        cumulationIndex = -1;
    }

    @Override
    protected final void decodeLast(ChannelHandlerContext ctx, ByteBuf in) throws Exception {
        super.decodeLast(ctx, in);

        // Handle the last unfinished message.
        if (message != null) {
            boolean chunked = isTransferEncodingChunked(message.headers());
            if (currentState == State.READ_VARIABLE_LENGTH_CONTENT && !in.isReadable() && !chunked) {
                // End of connection.
                ctx.fireChannelRead(headersFactory.newEmptyTrailers());
                closeHandler.protocolPayloadEndInbound(ctx);
                resetNow();
                return;
            }

            if (currentState == State.READ_HEADER) {
                // If we are still in the state of reading headers we need to create a new invalid message that
                // signals that the connection was closed before we received the headers.
                ctx.fireExceptionCaught(new PrematureChannelClosureException("Connection closed before received headers"));
                resetNow();
                return;
            }

            // Check if the closure of the connection signifies the end of the content.
            boolean prematureClosure;
            if (isDecodingRequest() || chunked) {
                // The last request did not wait for a response.
                prematureClosure = true;
            } else {
                // Compare the length of the received content and the 'Content-Length' header.
                // If the 'Content-Length' header is absent, the length of the content is determined by the end of the
                // connection, so it is perfectly fine.
                prematureClosure = contentLength() > 0;
            }

            if (!prematureClosure) {
                ctx.fireChannelRead(headersFactory.newEmptyTrailers());
                closeHandler.protocolPayloadEndInbound(ctx);
            }
            resetNow();
        }
    }

    @Override
    public final void userEventTriggered(ChannelHandlerContext ctx, Object evt) throws Exception {
        if (evt instanceof HttpExpectationFailedEvent) {
            switch (currentState) {
                case READ_FIXED_LENGTH_CONTENT:
                case READ_VARIABLE_LENGTH_CONTENT:
                case READ_CHUNK_SIZE:
                    // TODO(scott): this was previously reset, which delayed resetting state ... is that necessary?
                    resetNow();
                    break;
                default:
                    break;
            }
        }
        super.userEventTriggered(ctx, evt);
    }

    protected abstract boolean isContentAlwaysEmpty(T msg);

    /**
     * Returns true if the server switched to a different protocol than HTTP/1.0 or HTTP/1.1, e.g. HTTP/2 or Websocket.
     * Returns false if the upgrade happened in a different layer, e.g. upgrade from HTTP/1.1 to HTTP/1.1 over TLS.
     */
    private static boolean isSwitchingToNonHttp1Protocol(HttpResponseMetaData msg) {
        if (msg.status().code() != SWITCHING_PROTOCOLS.code()) {
            return false;
        }
        CharSequence newProtocol = msg.headers().get(UPGRADE);
        return newProtocol == null ||
                !AsciiString.contains(newProtocol, HTTP_1_0.toString()) &&
                        !AsciiString.contains(newProtocol, HTTP_1_1.toString());
    }

    private void resetNow() {
        T message = this.message;
        this.message = null;
        this.trailer = null;
        contentLength = Long.MIN_VALUE;
        cumulationIndex = -1;
        if (!isDecodingRequest()) {
            HttpResponseMetaData res = (HttpResponseMetaData) message;
            if (res != null && isSwitchingToNonHttp1Protocol(res)) {
                currentState = State.UPGRADED;
                return;
            }
        }

        currentState = State.SKIP_CONTROL_CHARS;
    }

    private boolean skipControlCharacters(ByteBuf buffer) {
        if (cumulationIndex < 0) {
            cumulationIndex = buffer.readerIndex();
        }
        int i = buffer.forEachByte(cumulationIndex, buffer.writerIndex() - cumulationIndex, SKIP_CONTROL_CHARS_PROCESSOR);
        if (i < 0) {
            cumulationIndex = buffer.writerIndex();
            return false;
        } else {
            cumulationIndex = i;
            buffer.readerIndex(i);
            return true;
        }
    }

    private void parseHeaderLine(HttpHeaders headers, ByteBuf buffer, final int lfIndex) {
        // https://tools.ietf.org/html/rfc7230#section-3.2
        // header-field   = field-name ":" OWS field-value OWS
        //
        //      field-name     = token
        //      field-value    = *( field-content / obs-fold )
        //      field-content  = field-vchar [ 1*( SP / HTAB ) field-vchar ]
        //      field-vchar    = VCHAR / obs-text
        //
        //      obs-fold       = CRLF 1*( SP / HTAB )
        //                     ; obsolete line folding
        //                     ; see Section 3.2.4
        final int nonControlIndex = lfIndex - 2;
        int headerStart = buffer.forEachByte(buffer.readerIndex(), nonControlIndex - buffer.readerIndex(),
                FIND_NON_LINEAR_WHITESPACE);
        if (headerStart < 0) {
            throw new IllegalArgumentException("unable to find start of header name");
        }

        int headerEnd = buffer.forEachByte(headerStart + 1, nonControlIndex - headerStart,
                FIND_COLON_OR_WHITE_SPACE);
        if (headerEnd < 0) {
            throw new IllegalArgumentException("unable to find end of header name");
        }

        int valueStart = headerEnd + 1;
        // We assume the allocator will not leak memory, and so we retain + slice to avoid copying data.
        CharSequence name = newAsciiString(newBufferFrom(buffer.retainedSlice(headerStart, headerEnd - headerStart)));
        if (buffer.getByte(headerEnd) != COLON_BYTE) {
            valueStart = buffer.forEachByte(headerEnd + 1, nonControlIndex - headerEnd, FIND_COLON);
            if (valueStart < 0) {
                throw new IllegalArgumentException("unable to find colon");
            }
        }
        if (nonControlIndex <= valueStart) {
            headers.add(name, emptyAsciiString());
        } else {
            valueStart = buffer.forEachByte(valueStart + 1, nonControlIndex - valueStart, FIND_NON_LINEAR_WHITESPACE);
            // Find End Of String
            int valueEnd;
            if (valueStart < 0 || (valueEnd = buffer.forEachByteDesc(valueStart, lfIndex - valueStart - 1,
                    FIND_NON_LINEAR_WHITESPACE)) < 0) {
                headers.add(name, emptyAsciiString());
            } else {
                // We assume the allocator will not leak memory, and so we retain + slice to avoid copying data.
                headers.add(name, newAsciiString(newBufferFrom(
                        buffer.retainedSlice(valueStart, valueEnd - valueStart + 1))));
            }
        }
        // Consume the header line bytes from the buffer.
        consumeCRLF(buffer, lfIndex);
    }

    @Nullable
    private State readHeaders(ByteBuf buffer) {
        int lfIndex = findCRLF(buffer, maxHeaderSize);
        if (lfIndex < 0) {
            return null;
        }
        final T message = this.message;
        assert message != null;
        if (!parseAllHeaders(buffer, message.headers(), lfIndex, maxHeaderSize)) {
            return null;
        }

        if (isContentAlwaysEmpty(message)) {
            setTransferEncodingChunked(message.headers(), false);
            return State.SKIP_CONTROL_CHARS;
        } else if (isTransferEncodingChunked(message.headers())) {
            return State.READ_CHUNK_SIZE;
        } else if (contentLength() >= 0) {
            return State.READ_FIXED_LENGTH_CONTENT;
        } else {
            return State.READ_VARIABLE_LENGTH_CONTENT;
        }
    }

    private long contentLength() {
        if (contentLength == Long.MIN_VALUE) {
            assert message != null;
            contentLength = getContentLength(message, -1L);
        }
        return contentLength;
    }

    @Nullable
    private HttpHeaders readTrailingHeaders(ByteBuf buffer) {
        final int lfIndex = findCRLF(buffer, maxHeaderSize);
        if (lfIndex < 0) {
            return null;
        }
        if (lfIndex - 2 > buffer.readerIndex()) {
            HttpHeaders trailer = this.trailer;
            if (trailer == null) {
                trailer = this.trailer = headersFactory.newTrailers();
            }

            return parseAllHeaders(buffer, trailer, lfIndex, maxHeaderSize) ? trailer : null;
        }

        consumeCRLF(buffer, lfIndex);
        // The RFC says the trailers are optional [1] so use an empty trailers instance from the headers factory.
        // [1] https://tools.ietf.org/html/rfc7230.html#section-4.1
        return trailer != null ? trailer : headersFactory.newEmptyTrailers();
    }

    private boolean parseAllHeaders(ByteBuf buffer, HttpHeaders headers, int lfIndex, int maxHeaderSize) {
        for (;;) {
            if (lfIndex - 1 == buffer.readerIndex()) {
                consumeCRLF(buffer, lfIndex);
                return true;
            }
            final int nextLFIndex = findCRLF(buffer, lfIndex + 1, maxHeaderSize);
            parseHeaderLine(headers, buffer, lfIndex);
            if (nextLFIndex < 0) {
                return false;
            } else if (nextLFIndex - 2 == lfIndex) {
                consumeCRLF(buffer, nextLFIndex);
                return true;
            }
            lfIndex = nextLFIndex;
        }
    }

    private static long getChunkSize(ByteBuf buffer, int lfIndex) {
        if (lfIndex - 2 < buffer.readerIndex()) {
            throw new DecoderException("chunked encoding specified but chunk-size not found");
        }
        return getChunkSize(buffer.toString(buffer.readerIndex(),
                lfIndex - 1 - buffer.readerIndex(), US_ASCII));
    }

    private static long getChunkSize(String hex) {
        hex = hex.trim();
        for (int i = 0; i < hex.length(); ++i) {
            char c = hex.charAt(i);
            if (c == ';' || isWhitespace(c) || isISOControl(c)) {
                hex = hex.substring(0, i);
                break;
            }
        }

        return Long.parseLong(hex, 16);
    }

    private void consumeCRLF(ByteBuf buffer, int lfIndex) {
        // Consume the initial line bytes from the buffer.
        if (buffer.writerIndex() - 1 >= lfIndex) {
            buffer.readerIndex(lfIndex + 1);
            cumulationIndex = lfIndex + 1;
        } else {
            buffer.readerIndex(lfIndex);
            cumulationIndex = lfIndex;
        }
    }

    private int findCRLF(ByteBuf buffer, int maxLineSize) {
        if (cumulationIndex < 0) {
            cumulationIndex = buffer.readerIndex();
        }
        int lfIndex = findCRLF(buffer, cumulationIndex, maxLineSize);
        cumulationIndex = lfIndex < 0 ? min(buffer.writerIndex(), cumulationIndex + maxLineSize) : lfIndex;
        return lfIndex;
    }

    private static int findCRLF(ByteBuf buffer, int startIndex, final int maxLineSize) {
        final int maxToIndex = startIndex + maxLineSize;
        for (;;) {
            int toIndex = min(buffer.writerIndex(), maxToIndex);
            int lfIndex = buffer.indexOf(startIndex, toIndex, LF);
            if (lfIndex == -1) {
                if (toIndex - startIndex == maxLineSize) {
                    throw new IllegalStateException("could not find CRLF within " + maxLineSize + " bytes.");
                }
                return -2;
            } else if (lfIndex == buffer.readerIndex()) {
                buffer.skipBytes(1);
                ++startIndex;
            } else if (buffer.getByte(lfIndex - 1) == CR) {
                return lfIndex;
            } else if (lfIndex != maxToIndex) {
                // Found LF but no CR before
                if (lfIndex == buffer.writerIndex()) {
                    return -2;
                }
                startIndex = lfIndex + 1;
            } else {
                throw new TooLongFrameException("An HTTP line is larger than " + maxLineSize + " bytes.");
            }
        }
    }

    private static void splitInitialLineError() {
        throw new IllegalArgumentException("invalid initial line");
    }

    private static int getWebSocketContentLength(HttpMetaData message) {
        // WebSocket messages have constant content-lengths.
        HttpHeaders h = message.headers();
        if (message instanceof HttpRequestMetaData) {
            HttpRequestMetaData req = (HttpRequestMetaData) message;
            // Note that we are using ServiceTalk types here, and assume the decoders will also use ServiceTalk types.
            if (GET.equals(req.method()) &&
                    h.contains(SEC_WEBSOCKET_KEY1) &&
                    h.contains(SEC_WEBSOCKET_KEY2)) {
                return 8;
            }
        } else if (message instanceof HttpResponseMetaData) {
            HttpResponseMetaData res = (HttpResponseMetaData) message;
            if (res.status().code() == SWITCHING_PROTOCOLS.code() &&
                    h.contains(SEC_WEBSOCKET_ORIGIN) &&
                    h.contains(SEC_WEBSOCKET_LOCATION)) {
                return 16;
            }
        }

        // Not a web socket message
        return -1;
    }

    private static long getContentLength(HttpMetaData message, long defaultValue) {
        CharSequence value = message.headers().get(CONTENT_LENGTH);
        if (value != null) {
            return Long.parseLong(value.toString());
        }

        // We know the content length if it's a Web Socket message even if
        // Content-Length header is missing.
        long webSocketContentLength = getWebSocketContentLength(message);
        if (webSocketContentLength >= 0) {
            return webSocketContentLength;
        }

        // Otherwise we don't.
        return defaultValue;
    }

    static HttpProtocolVersion nettyBufferToHttpVersion(ByteBuf buffer) {
        // https://tools.ietf.org/html/rfc7230.html#appendix-A.2
        // The HTTP-version ABNF production has been clarified to be case-sensitive
        return buffer.equals(HTTP_1_1_BUF) ? HTTP_1_1 :
                buffer.equals(HTTP_1_0_BUF) ? HTTP_1_0 :
                        newProtocolVersion(newBufferFrom(buffer.retain()));
    }
}

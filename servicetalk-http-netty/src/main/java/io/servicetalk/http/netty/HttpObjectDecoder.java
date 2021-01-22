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
import io.servicetalk.http.api.HttpHeaders;
import io.servicetalk.http.api.HttpHeadersFactory;
import io.servicetalk.http.api.HttpMetaData;
import io.servicetalk.http.api.HttpProtocolVersion;
import io.servicetalk.http.api.HttpRequestMetaData;
import io.servicetalk.http.api.HttpResponseMetaData;
import io.servicetalk.transport.netty.internal.ByteToMessageDecoder;
import io.servicetalk.transport.netty.internal.CloseHandler;
import io.servicetalk.transport.netty.internal.CloseHandler.DiscardFurtherInboundEvent;
import io.servicetalk.utils.internal.IllegalCharacterException;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufAllocator;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandler;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.codec.DecoderException;
import io.netty.handler.codec.PrematureChannelClosureException;
import io.netty.handler.codec.TooLongFrameException;
import io.netty.handler.codec.http.HttpExpectationFailedEvent;
import io.netty.util.AsciiString;
import io.netty.util.ByteProcessor;

import javax.annotation.Nullable;

import static io.netty.handler.codec.http.HttpConstants.COLON;
import static io.netty.handler.codec.http.HttpConstants.CR;
import static io.netty.handler.codec.http.HttpConstants.HT;
import static io.netty.handler.codec.http.HttpConstants.LF;
import static io.netty.handler.codec.http.HttpConstants.SP;
import static io.netty.util.ByteProcessor.FIND_LF;
import static io.servicetalk.buffer.internal.CharSequences.emptyAsciiString;
import static io.servicetalk.buffer.internal.CharSequences.newAsciiString;
import static io.servicetalk.buffer.netty.BufferUtils.newBufferFrom;
import static io.servicetalk.http.api.HeaderUtils.isTransferEncodingChunked;
import static io.servicetalk.http.api.HttpHeaderNames.CONTENT_LENGTH;
import static io.servicetalk.http.api.HttpHeaderNames.SEC_WEBSOCKET_KEY1;
import static io.servicetalk.http.api.HttpHeaderNames.SEC_WEBSOCKET_KEY2;
import static io.servicetalk.http.api.HttpHeaderNames.SEC_WEBSOCKET_LOCATION;
import static io.servicetalk.http.api.HttpHeaderNames.SEC_WEBSOCKET_ORIGIN;
import static io.servicetalk.http.api.HttpHeaderNames.UPGRADE;
import static io.servicetalk.http.api.HttpProtocolVersion.HTTP_1_0;
import static io.servicetalk.http.api.HttpProtocolVersion.HTTP_1_1;
import static io.servicetalk.http.api.HttpRequestMethod.GET;
import static io.servicetalk.http.api.HttpResponseStatus.SWITCHING_PROTOCOLS;
import static io.servicetalk.http.netty.HeaderUtils.removeTransferEncodingChunked;
import static io.servicetalk.http.netty.HttpKeepAlive.shouldClose;
import static java.lang.Character.isISOControl;
import static java.lang.Character.isWhitespace;
import static java.lang.Long.parseUnsignedLong;
import static java.lang.Math.min;
import static java.nio.charset.StandardCharsets.US_ASCII;
import static java.util.Objects.requireNonNull;

abstract class HttpObjectDecoder<T extends HttpMetaData> extends ByteToMessageDecoder {
    private static final long HTTP_VERSION_FORMAT = 0x485454502f312e00L;    // HEX representation of "HTTP/1.x"
    private static final long HTTP_VERSION_MASK = 0xffffffffffffff00L;
    private static final ByteProcessor SKIP_PREFACING_CRLF = value -> {
        if (isVCHAR(value)) {
            return false;
        }
        if (value == CR || value == LF) {
            return true;
        }
        throw new StacklessDecoderException("Invalid preface character before the start-line of the HTTP message",
                new IllegalCharacterException(value, "CR (0x0d), LF (0x0a)"));
    };
    private static final ByteProcessor FIND_WS = value -> !isWS(value);
    private static final ByteProcessor FIND_VCHAR_END = value -> {
        if (isVCHAR(value)) {
            return true;
        }
        if (isWS(value)) {
            return false;
        }
        throw new IllegalCharacterException(value, "VCHAR (0x21-0x7e)");
    };
    private static final ByteProcessor FIND_COLON = value -> value != COLON;
    private static final ByteProcessor FIND_FIELD_VALUE = value -> {
        // Skip preceded and/or followed OWS
        if (isWS(value)) {
            return true;
        }
        if (isVCHAR(value) || isObsText(value)) {
            return false;
        }
        throw new IllegalCharacterException(value, "HTAB / SP / VCHAR / obs-text");
    };

    private static final int MAX_HEX_CHARS_FOR_LONG = 16; // 0x7FFFFFFFFFFFFFFF == Long.MAX_INT
    private static final int CHUNK_DELIMETER_SIZE = 2; // CRLF
    private static final int MAX_ALLOWED_CHARS_TO_SKIP = CHUNK_DELIMETER_SIZE * 2; // Max allowed prefacing CRLF to skip
    private static final int MAX_ALLOWED_CHARS_TO_SKIP_PLUS_ONE = MAX_ALLOWED_CHARS_TO_SKIP + 1;

    private final int maxStartLineLength;
    private final int maxHeaderFieldLength;

    private final HttpHeadersFactory headersFactory;
    private final CloseHandler closeHandler;
    private final boolean allowPrematureClosureBeforePayloadBody;
    @Nullable
    private T message;
    @Nullable
    private HttpHeaders trailer;
    private long chunkSize;
    private int cumulationIndex = -1;
    private long contentLength = Long.MIN_VALUE;
    private int parsingLine;

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
    private int skippedControls;

    /**
     * Creates a new instance with the specified parameters.
     */
    protected HttpObjectDecoder(final ByteBufAllocator alloc, final HttpHeadersFactory headersFactory,
                                final int maxStartLineLength, final int maxHeaderFieldLength,
                                final boolean allowPrematureClosureBeforePayloadBody, final CloseHandler closeHandler) {
        super(alloc);
        this.closeHandler = requireNonNull(closeHandler);
        if (maxStartLineLength <= 0) {
            throw new IllegalArgumentException("maxStartLineLength: " + maxStartLineLength + " (expected >0)");
        }
        if (maxHeaderFieldLength <= 0) {
            throw new IllegalArgumentException("maxHeaderFieldLength: " + maxHeaderFieldLength + " (expected >0)");
        }
        this.headersFactory = requireNonNull(headersFactory);
        this.maxStartLineLength = maxStartLineLength;
        this.maxHeaderFieldLength = maxHeaderFieldLength;
        this.allowPrematureClosureBeforePayloadBody = allowPrematureClosureBeforePayloadBody;
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
     * When the initial line is expected, and a buffer is received which does not contain a CRLF that terminates the
     * initial line.
     * @param ctx the {@link ChannelHandlerContext}.
     * @param buffer the {@link Buffer} received.
     */
    protected abstract void handlePartialInitialLine(ChannelHandlerContext ctx, ByteBuf buffer);

    /**
     * Create a new {@link HttpMetaData} because a new request/response
     * <a href="https://tools.ietf.org/html/rfc7230.html#section-3.1">start line</a> has been parsed.
     *
     * @param buffer The {@link ByteBuf} which contains a start line
     * @param firstStart Start index of the first item in the start line
     * @param firstLength Length of the first item in the start line
     * @param secondStart Start index of the second item in the start line
     * @param secondLength Length of the second item in the start line
     * @param thirdStart Start index of the third item in the start line
     * @param thirdLength Length of the third item in the start line, a negative value indicates the absence of the
     * third component
     * @return a new {@link HttpMetaData} that represents the parsed start line
     */
    protected abstract T createMessage(ByteBuf buffer, int firstStart, int firstLength,
                                       int secondStart, int secondLength,
                                       int thirdStart, int thirdLength);

    @Override
    protected final void decode(final ChannelHandlerContext ctx, final ByteBuf buffer) {
        switch (currentState) {
            case SKIP_CONTROL_CHARS: {
                if (!skipControlCharacters(buffer)) {
                    return;
                }
                currentState = State.READ_INITIAL;
            }
            case READ_INITIAL: {
                final int lfIndex = findCRLF(buffer, maxStartLineLength);
                if (lfIndex < 0) {
                    handlePartialInitialLine(ctx, buffer);
                    return;
                }

                // Parse the initial line:
                // https://tools.ietf.org/html/rfc7230#section-3.1.1
                // request-line = method SP request-target SP HTTP-version CRLF
                // https://tools.ietf.org/html/rfc7230#section-3.1.2
                // status-line = HTTP-version SP status-code SP reason-phrase CRLF
                final int nonControlIndex = lfIndex - 2;
                final int aStart = buffer.readerIndex();    // We already skipped all preface control chars
                // Look only for a WS, other checks will be done later by request/response decoder
                final int aEnd = buffer.forEachByte(aStart + 1, nonControlIndex - aStart, FIND_WS);
                if (aEnd < 0) {
                    throw newStartLineError("first");
                }

                final int bStart = aEnd + 1;    // Expect a single WS
                final int bEnd;
                try {
                    bEnd = buffer.forEachByte(bStart, nonControlIndex - bStart + 1,
                            isDecodingRequest() ? FIND_VCHAR_END : FIND_WS);
                } catch (IllegalCharacterException cause) {
                    throw new StacklessDecoderException(
                            "Invalid start-line: HTTP request-target contains an illegal character", cause);
                }
                if (bEnd < 0 || bEnd == bStart) {
                    throw newStartLineError("second");
                }

                final int cStart = bEnd + 1;    // Expect a single WS
                // Other checks will be done later by request/response decoder
                final int cLength = cStart > nonControlIndex ? 0 : nonControlIndex - cStart + 1;

                // Consume the initial line bytes from the buffer.
                consumeCRLF(buffer, lfIndex);

                message = createMessage(buffer, aStart, aEnd - aStart, bStart, bEnd - bStart, cStart, cLength);
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
                        ctx.fireChannelRead(EmptyHttpHeaders.INSTANCE);
                        closeHandler.protocolPayloadEndInbound(ctx);
                        resetNow();
                        return;
                    case READ_CHUNK_SIZE:
                        // Chunked encoding - generate HttpMessage first.  HttpChunks will follow.
                        ctx.fireChannelRead(message);
                        return;
                    default:
                        // <a href="https://tools.ietf.org/html/rfc7230#section-3.3.3">RFC 7230, 3.3.3</a> states that
                        // if a request does not have either a transfer-encoding or a content-length header then the
                        // message body length is 0. However for a response the body length is the number of octets
                        // received prior to the server closing the connection. So we treat this as variable length
                        // chunked encoding.
                        long contentLength = contentLength();
                        if (contentLength == 0 || contentLength == -1 && isDecodingRequest()) {
                            ctx.fireChannelRead(message);
                            ctx.fireChannelRead(EmptyHttpHeaders.INSTANCE);
                            closeHandler.protocolPayloadEndInbound(ctx);
                            resetNow();
                            return;
                        }

                        assert nextState == State.READ_FIXED_LENGTH_CONTENT ||
                                nextState == State.READ_VARIABLE_LENGTH_CONTENT;

                        ctx.fireChannelRead(message);

                        if (nextState == State.READ_FIXED_LENGTH_CONTENT) {
                            // chunkSize will be decreased as the READ_FIXED_LENGTH_CONTENT state reads data chunk by
                            // chunk.
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
                    ctx.fireChannelRead(EmptyHttpHeaders.INSTANCE);
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
    protected final ByteBuf swapAndCopyCumulation(final ByteBuf cumulation, final ByteBuf in) {
        final int readerIndex = cumulation.readerIndex();
        final ByteBuf newCumulation = super.swapAndCopyCumulation(cumulation, in);
        cumulationIndex -= readerIndex - newCumulation.readerIndex();
        return newCumulation;
    }

    @Override
    protected final void cumulationReset() {
        cumulationIndex = -1;
    }

    @Override
    protected final void decodeLast(final ChannelHandlerContext ctx, final ByteBuf in) throws Exception {
        super.decodeLast(ctx, in);

        // Handle the last unfinished message.
        if (message != null) {
            boolean chunked = isTransferEncodingChunked(message.headers());
            if (!in.isReadable() && (
                    (currentState == State.READ_VARIABLE_LENGTH_CONTENT && !chunked) ||
                    (currentState == State.READ_CHUNK_SIZE && chunked && allowPrematureClosureBeforePayloadBody))) {
                // End of connection.
                ctx.fireChannelRead(EmptyHttpHeaders.INSTANCE);
                closeHandler.protocolPayloadEndInbound(ctx);
                resetNow();
                return;
            }

            if (currentState == State.READ_HEADER) {
                // If we are still in the state of reading headers we need to create a new invalid message that
                // signals that the connection was closed before we received the headers.
                ctx.fireExceptionCaught(
                        new PrematureChannelClosureException("Connection closed before received headers"));
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
                ctx.fireChannelRead(EmptyHttpHeaders.INSTANCE);
                closeHandler.protocolPayloadEndInbound(ctx);
            }
            resetNow();
        }
    }

    @Override
    public final void userEventTriggered(final ChannelHandlerContext ctx, final Object evt) throws Exception {
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
        } else if (evt instanceof DiscardFurtherInboundEvent) {
            resetNow();
            ctx.pipeline().replace(HttpObjectDecoder.this, DiscardInboundHandler.INSTANCE.toString(),
                    DiscardInboundHandler.INSTANCE);
            ctx.channel().config().setAutoRead(true);
        }
        super.userEventTriggered(ctx, evt);
    }

    protected abstract boolean isContentAlwaysEmpty(T msg);

    /**
     * Returns true if the server switched to a different protocol than HTTP/1.0 or HTTP/1.1, e.g. HTTP/2 or Websocket.
     * Returns false if the upgrade happened in a different layer, e.g. upgrade from HTTP/1.1 to HTTP/1.1 over TLS.
     */
    private static boolean isSwitchingToNonHttp1Protocol(final HttpResponseMetaData msg) {
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
        parsingLine = 0;
        cumulationIndex = -1;
        if (!isDecodingRequest()) {
            HttpResponseMetaData res = (HttpResponseMetaData) message;
            if (res != null && isSwitchingToNonHttp1Protocol(res)) {
                currentState = State.UPGRADED;
                return;
            }
        }

        currentState = State.SKIP_CONTROL_CHARS;
        skippedControls = 0;
    }

    /**
     * In the interest of robustness, a peer that is expecting to receive and parse a start-line SHOULD ignore at
     * least one empty line (CRLF) received prior to the request-line.
     *
     * @see <a href="https://tools.ietf.org/html/rfc7230#section-3.5">RFC7230, Message Parsing Robustness</a>
     */
    private boolean skipControlCharacters(final ByteBuf buffer) {
        if (cumulationIndex < 0) {
            cumulationIndex = buffer.readerIndex();
        }
        final int readableBytes = buffer.writerIndex() - cumulationIndex;
        // Look at one more character than allowed to expect a valid VCHAR:
        final int len = min(MAX_ALLOWED_CHARS_TO_SKIP_PLUS_ONE - skippedControls, readableBytes);
        final int i = buffer.forEachByte(cumulationIndex, len, SKIP_PREFACING_CRLF);
        if (i < 0) {
            skippedControls += len;
            if (skippedControls > MAX_ALLOWED_CHARS_TO_SKIP) {
                throw new DecoderException(
                        "Too many prefacing CRLF (0x0d0a) characters before the start-line of the HTTP message");
            }
            cumulationIndex += len;
            buffer.readerIndex(cumulationIndex);
            return false;
        } else {
            cumulationIndex = i;
            buffer.readerIndex(i);
            return true;
        }
    }

    private void parseHeaderLine(final HttpHeaders headers, final ByteBuf buffer, final int lfIndex) {
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
        //      OWS            = *( SP / HTAB )
        //                     ; optional whitespace
        // https://tools.ietf.org/html/rfc7230#section-3.2.4
        // No whitespace is allowed between the header field-name and colon.  In
        //    the past, differences in the handling of such whitespace have led to
        //    security vulnerabilities in request routing and response handling.
        final int nonControlIndex = lfIndex - 2;
        final int nameStart = buffer.readerIndex();
        // Other checks will be done by header validator if enabled by users
        final int nameEnd = buffer.forEachByte(nameStart, nonControlIndex - nameStart + 1, FIND_COLON);
        if (nameEnd < 0) {
            throw newDecoderExceptionAtLine("Unable to find end of a header name in line ", parsingLine);
        }
        if (nameEnd == nameStart) {
            throw newDecoderExceptionAtLine("Empty header name in line ", parsingLine);
        }

        // We assume the allocator will not leak memory, and so we retain + slice to avoid copying data.
        final CharSequence name = newAsciiString(newBufferFrom(buffer.retainedSlice(nameStart, nameEnd - nameStart)));
        final CharSequence value;
        try {
            final int valueStart;
            if (nameEnd >= nonControlIndex || (valueStart =
                    buffer.forEachByte(nameEnd + 1, nonControlIndex - nameEnd, FIND_FIELD_VALUE)) < 0) {
                value = emptyAsciiString();
            } else {
                final int valueEnd =
                        buffer.forEachByteDesc(valueStart, nonControlIndex - valueStart + 1, FIND_FIELD_VALUE);
                // We assume the allocator will not leak memory, and so we retain + slice to avoid copying data.
                value = newAsciiString(newBufferFrom(buffer.retainedSlice(valueStart, valueEnd - valueStart + 1)));
            }
        } catch (IllegalCharacterException cause) {
            throw invalidHeaderValue(name, parsingLine, cause);
        }
        try {
            headers.add(name, value);
        } catch (IllegalCharacterException cause) {
            throw invalidHeaderName(name, parsingLine, cause);
        }
        // Consume the header line bytes from the buffer.
        consumeCRLF(buffer, lfIndex);
    }

    private static DecoderException newDecoderExceptionAtLine(final String message, final int parsingLine) {
        return new DecoderException(message + (parsingLine - 1));
    }

    private static DecoderException invalidHeaderName(final CharSequence name, final int parsingLine,
                                                      final IllegalCharacterException cause) {
        throw new StacklessDecoderException("Invalid header name in line " + (parsingLine - 1) + ": " + name, cause);
    }

    private static DecoderException invalidHeaderValue(final CharSequence name, final int parsingLine,
                                                       final IllegalCharacterException cause) {
        throw new StacklessDecoderException("Invalid value for the header '" + name + "' in line " + (parsingLine - 1),
                cause);
    }

    @Nullable
    private State readHeaders(final ByteBuf buffer) {
        int lfIndex = findCRLF(buffer, maxHeaderFieldLength);
        if (lfIndex < 0) {
            return null;
        }
        final T message = this.message;
        assert message != null;
        if (!parseAllHeaders(buffer, message.headers(), lfIndex, maxHeaderFieldLength)) {
            return null;
        }

        if (isContentAlwaysEmpty(message)) {
            removeTransferEncodingChunked(message.headers());
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
            contentLength = getContentLength(message);
        }
        return contentLength;
    }

    @Nullable
    private HttpHeaders readTrailingHeaders(final ByteBuf buffer) {
        final int lfIndex = findCRLF(buffer, maxHeaderFieldLength);
        if (lfIndex < 0) {
            return null;
        }
        if (lfIndex - 2 > buffer.readerIndex()) {
            HttpHeaders trailer = this.trailer;
            if (trailer == null) {
                trailer = this.trailer = headersFactory.newTrailers();
            }

            return parseAllHeaders(buffer, trailer, lfIndex, maxHeaderFieldLength) ? trailer : null;
        }

        consumeCRLF(buffer, lfIndex);
        // The RFC says the trailers are optional [1] so use an empty trailers instance from the headers factory.
        // [1] https://tools.ietf.org/html/rfc7230.html#section-4.1
        return trailer != null ? trailer : headersFactory.newEmptyTrailers();
    }

    private boolean parseAllHeaders(final ByteBuf buffer, final HttpHeaders headers, int lfIndex,
                                    final int maxHeaderFieldLength) {
        for (;;) {
            if (lfIndex - 1 == buffer.readerIndex()) {
                consumeCRLF(buffer, lfIndex);
                return true;
            }
            final int nextLFIndex = findCRLF(buffer, lfIndex + 1, maxHeaderFieldLength, parsingLine);
            parseHeaderLine(headers, buffer, lfIndex);
            if (nextLFIndex < 0) {
                return false;
            }
            ++parsingLine;
            if (nextLFIndex - 2 == lfIndex) {
                consumeCRLF(buffer, nextLFIndex);
                return true;
            }
            lfIndex = nextLFIndex;
        }
    }

    private static long getChunkSize(final ByteBuf buffer, final int lfIndex) {
        if (lfIndex - 2 < buffer.readerIndex()) {
            throw new DecoderException("Chunked encoding specified but chunk-size not found");
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

        try {
            return parseUnsignedLong(hex, 16);
        } catch (NumberFormatException cause) {
            throw invalidChunkSize(hex, cause);
        }
    }

    private static DecoderException invalidChunkSize(final String hex, final NumberFormatException cause) {
        return new StacklessDecoderException("Cannot parse chunk-size: " + hex + ", expected a valid HEXDIG", cause);
    }

    private void consumeCRLF(final ByteBuf buffer, final int lfIndex) {
        // Consume the initial line bytes from the buffer.
        if (buffer.writerIndex() - 1 >= lfIndex) {
            buffer.readerIndex(lfIndex + 1);
            cumulationIndex = lfIndex + 1;
        } else {
            buffer.readerIndex(lfIndex);
            cumulationIndex = lfIndex;
        }
    }

    private int findCRLF(final ByteBuf buffer, final int maxLineSize) {
        if (cumulationIndex < 0) {
            cumulationIndex = buffer.readerIndex();
        }
        final int lfIndex = findCRLF(buffer, cumulationIndex, maxLineSize, parsingLine);
        cumulationIndex = lfIndex < 0 ? min(buffer.writerIndex(), cumulationIndex + maxLineSize) : lfIndex;
        if (lfIndex >= 0) {
            ++parsingLine;
        }
        return lfIndex;
    }

    private static int findCRLF(final ByteBuf buffer, int startIndex, final int maxLineSize, final int parsingLine) {
        final int maxToIndex = startIndex + maxLineSize;
        for (;;) {
            final int toIndex = min(buffer.writerIndex(), maxToIndex);
            final int lfIndex = findLF(buffer, startIndex, toIndex);
            if (lfIndex == -1) {
                if (toIndex - startIndex == maxLineSize) {
                    throw new DecoderException("Could not find CRLF (0x0d0a) within " + maxLineSize +
                            " bytes, while parsing line " + parsingLine);
                }
                return -2;
            } else if (lfIndex == buffer.readerIndex()) {
                buffer.skipBytes(1);
                ++startIndex;
            } else if (buffer.getByte(lfIndex - 1) == CR) {
                return lfIndex;
            } else if (lfIndex != maxToIndex) {
                throw new DecoderException("Found LF (0x0a) but no CR (0x0d) before, while parsing line " +
                        parsingLine);
            } else {
                throw new TooLongFrameException("An HTTP line " + parsingLine + " is larger than " + maxLineSize +
                        " bytes");
            }
        }
    }

    private static int findLF(final ByteBuf buffer, final int fromIndex, final int toIndex) {
        if (fromIndex >= toIndex) {
            return -1;
        }
        return buffer.forEachByte(fromIndex, toIndex - fromIndex, FIND_LF);
    }

    private DecoderException newStartLineError(final String place) {
        throw new DecoderException("Invalid start-line: incorrect number of components, cannot find the " + place +
                " SP, expected: " + (isDecodingRequest() ? "method SP request-target SP HTTP-version" :
                "HTTP-version SP status-code SP reason-phrase"));
    }

    private static int getWebSocketContentLength(final HttpMetaData message) {
        // WebSocket messages have constant content-lengths.
        HttpHeaders h = message.headers();
        if (message instanceof HttpRequestMetaData) {
            HttpRequestMetaData req = (HttpRequestMetaData) message;
            // Note that we are using ServiceTalk constants for HttpRequestMethod here, and assume the decoders will
            // also use ServiceTalk constants which allows us to use reference check here:
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

    private static long getContentLength(final HttpMetaData message) {
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
        return -1;
    }

    static HttpProtocolVersion nettyBufferToHttpVersion(final ByteBuf buffer, final int start, final int length) {
        if (length != 8) {
            throw newHttpVersionError(buffer, start, length, null);
        }

        final long httpVersion = buffer.getLong(start);
        if ((httpVersion & HTTP_VERSION_MASK) != HTTP_VERSION_FORMAT) {
            throw newHttpVersionError(buffer, start, length, null);
        }

        try {
            return HttpProtocolVersion.of(1, toDecimal((int) httpVersion & 0xff));
        } catch (IllegalCharacterException cause) {
            throw newHttpVersionError(buffer, start, length, cause);
        }
    }

    private static DecoderException newHttpVersionError(final ByteBuf buffer, final int start, final int length,
                                                        @Nullable final Throwable cause) {
        final String message = "Invalid HTTP version: '" + buffer.toString(start, length, US_ASCII) +
                "', expected: HTTP/1.x";
        return cause == null ? new DecoderException(message) : new StacklessDecoderException(message, cause);
    }

    static int toDecimal(final int value) {
        if (value < '0' || value > '9') {
            throw new IllegalCharacterException((byte) value, "0-9 (0x30-0x39)");
        }
        return value - '0';
    }

    static boolean isWS(final byte value) {
        return value == SP || value == HT;
    }

    private static boolean isVCHAR(final byte value) {
        return value >= '!' && value <= '~';
    }

    private static boolean isObsText(final byte value) {
        return value < 0; // x80-xFF
    }

    @Sharable
    private static final class DiscardInboundHandler extends SimpleChannelInboundHandler<Object> {
        static final ChannelInboundHandler INSTANCE = new DiscardInboundHandler();

        private DiscardInboundHandler() {
            super(/* autoRelease */ true);
        }

        @Override
        protected void channelRead0(final ChannelHandlerContext ctx, final Object msg) {
            // noop
        }
    }

    static final class StacklessDecoderException extends DecoderException {
        private static final long serialVersionUID = 7611225180490304156L;

        StacklessDecoderException(final String message, final Throwable cause) {
            super(message, cause);
        }

        @Override
        public Throwable fillInStackTrace() {
            return this;
        }
    }
}

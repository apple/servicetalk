/*
 * Copyright © 2018-2026 Apple Inc. and the ServiceTalk project authors
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
import io.servicetalk.concurrent.internal.QueueFullException;
import io.servicetalk.http.api.HttpRequestMetaData;
import io.servicetalk.http.api.HttpRequestMethod;
import io.servicetalk.http.netty.HttpResponseDecoder.Signal;
import io.servicetalk.transport.netty.internal.CloseHandler;
import io.servicetalk.transport.netty.internal.DefaultNettyConnection.CancelWriteUserEvent;
import io.servicetalk.transport.netty.internal.DefaultNettyConnection.ContinueUserEvent;
import io.servicetalk.utils.internal.IllegalCharacterException;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPromise;
import io.netty.util.concurrent.PromiseNotifier;
import io.netty.util.concurrent.ScheduledFuture;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.Queue;
import javax.annotation.Nullable;

import static io.netty.handler.codec.http.HttpConstants.SP;
import static io.servicetalk.http.api.HttpProtocolVersion.HTTP_1_1;
import static io.servicetalk.http.netty.HeaderUtils.REQ_EXPECT_CONTINUE;
import static io.servicetalk.http.netty.HeaderUtils.shouldAddZeroContentLength;
import static io.servicetalk.http.netty.HttpResponseDecoder.Signal.REQUEST_SIGNAL;
import static io.servicetalk.utils.internal.DurationUtils.toNanos;
import static java.util.Objects.requireNonNull;
import static java.util.concurrent.TimeUnit.NANOSECONDS;

final class HttpRequestEncoder extends HttpObjectEncoder<HttpRequestMetaData> {
    private static final Logger LOGGER = LoggerFactory.getLogger(HttpRequestEncoder.class);
    private static final char SLASH = '/';
    private static final char QUESTION_MARK = '?';
    private static final char DEL = 0x7f;
    private static final int SLASH_AND_SPACE_SHORT = (SLASH << 8) | SP;
    private static final int SPACE_SLASH_AND_SPACE_MEDIUM = (SP << 16) | SLASH_AND_SPACE_SHORT;

    private final Queue<HttpRequestMethod> methodQueue;
    private final Queue<Signal> signalsQueue;
    private final Duration expectContinueTimeout;

    /**
     * The outgoing request with "Expect: 100-continue" header whose payload body waits for a response.
     */
    @Nullable
    private ExpectContinueSignal expectContinue;

    /**
     * Create a new instance.
     * @param methodQueue A queue used to enforce HTTP protocol semantics related to request/response lengths.
     * @param signalsQueue A queue used to communicate extra signals between encoder and decoder.
     * @param headersEncodedSizeAccumulator Used to calculate an exponential moving average of the encoded size of the
     * initial line and the headers for a guess for future buffer allocations.
     * @param trailersEncodedSizeAccumulator  Used to calculate an exponential moving average of the encoded size of
     * the trailers for a guess for future buffer allocations.
     * @param closeHandler observes protocol state events
     * @param expectContinueTimeout how long a request with "Expect: 100-continue" header waits for a response before
     * its payload body is sent
     */
    HttpRequestEncoder(final Queue<HttpRequestMethod> methodQueue, final Queue<Signal> signalsQueue,
                       final int headersEncodedSizeAccumulator, final int trailersEncodedSizeAccumulator,
                       final CloseHandler closeHandler, final Duration expectContinueTimeout) {
        super(headersEncodedSizeAccumulator, trailersEncodedSizeAccumulator, closeHandler);
        this.methodQueue = requireNonNull(methodQueue);
        this.signalsQueue = requireNonNull(signalsQueue);
        this.expectContinueTimeout = requireNonNull(expectContinueTimeout);
    }

    @Override
    protected void sanitizeHeadersBeforeEncode(final HttpRequestMetaData msg, final boolean isAlwaysEmpty) {
        // This method has side effects on the methodQueue for the following reasons:
        // - createMessage will not necessarily fire a message up the pipeline.
        // - the trigger points on the queue are currently symmetric for the request/response decoder and
        // request/response encoder. We may use header information on the response decoder side, and the queue
        // interaction is conditional (1xx responses don't touch the queue).
        // - unit tests exist which verify these side effects occur, so if behavior of the internal classes changes the
        // unit test should catch it.
        // - this is the rough equivalent of what is done in Netty in terms of sequencing. Instead of trying to
        // iterate a decoded list it makes some assumptions about the base class ordering of events.
        methodQueue.add(msg.method());
    }

    @Override
    protected HttpRequestMetaData castMetaData(Object msg) {
        return (HttpRequestMetaData) msg;
    }

    @Override
    protected void encodeInitialLine(ChannelHandlerContext ctx, Buffer stBuffer, HttpRequestMetaData message) {
        message.method().writeTo(stBuffer);

        String uri = message.requestTarget();
        validateRequestTarget(uri);

        if (uri.isEmpty()) {
            // Add " / " as absolute path if uri is not present.
            // See https://tools.ietf.org/html/rfc2616#section-5.1.2
            stBuffer.writeMedium(SPACE_SLASH_AND_SPACE_MEDIUM);
        } else {
            CharSequence uriCharSequence = uri;
            boolean needSlash = false;
            int start = uri.indexOf("://");
            if (start != -1 && uri.charAt(0) != SLASH) {
                start += 3;
                // Correctly handle query params.
                // See https://github.com/netty/netty/issues/2732
                int index = uri.indexOf(QUESTION_MARK, start);
                if (index == -1) {
                    if (uri.lastIndexOf(SLASH) < start) {
                        needSlash = true;
                    }
                } else {
                    if (uri.lastIndexOf(SLASH, index) < start) {
                        // TODO(scott): ByteBuf to support writing a sub-section of CharSequence?
                        uriCharSequence = new StringBuilder(uri.length() + 1).append(uri).insert(index, SLASH);
                    }
                }
            }

            stBuffer.writeByte(SP);
            stBuffer.writeUtf8(uriCharSequence);
            if (needSlash) {
                // write "/ " after uri
                stBuffer.writeShort(SLASH_AND_SPACE_SHORT);
            } else {
                stBuffer.writeByte(SP);
            }
        }

        // It is possible for the user to generate a request with a non-http/1.x version (e.g. ALPN prefers h2), and
        // if this happens just force http/1.1 to avoid generating an invalid request.
        (message.version().major() == 1 ? message.version() : HTTP_1_1).writeTo(stBuffer);
        stBuffer.writeShort(CRLF_SHORT);
    }

    private static void validateRequestTarget(final String requestTarget) {
        // This guards HTTP/1.x request-line framing only. Preserve existing behavior for non-ASCII
        // request-target characters: they are written via UTF-8 and do not encode to SP/CTL/DEL bytes.
        for (int i = 0; i < requestTarget.length(); ++i) {
            final char c = requestTarget.charAt(i);
            if (c <= SP || c == DEL) {
                throw new IllegalCharacterException((byte) c, "request-target character (no SP / CTL / DEL)");
            }
        }
    }

    @Override
    protected long getContentLength(final HttpRequestMetaData message) {
        final long len = HttpObjectDecoder.getContentLength(message);
        // If there is no length header, and we omitted adding one previously because the method isn't expected to have
        // content-length then assume the length is 0. If the user does attempt to write content they will get an error
        // and should explicitly set content-length.
        return len < 0 && !shouldAddZeroContentLength(message.method()) ? 0 : len;
    }

    @Override
    protected void onMetaData(final ChannelHandlerContext ctx, final HttpRequestMetaData metaData) {
        if (expectContinue != null) {
            // The write of the previous request ended while its payload body waited for a response.
            expectContinue.stopExpectContinue();
        }
        final Signal signal;
        if (!expectContinueTimeout.isZero() && REQ_EXPECT_CONTINUE.test(metaData)) {
            signal = expectContinue = new ExpectContinueSignal(ctx);
        } else {
            expectContinue = null;
            signal = REQUEST_SIGNAL;
        }
        // Offer signals for all request types. Otherwise, decoder may receive a wrong signal if client sends multiple
        // pipelined requests.
        if (!signalsQueue.offer(signal)) {
            throw new QueueFullException("Can not enqueue a request type for the decoder");
        }
    }

    @Override
    protected void writeMetaData(final ChannelHandlerContext ctx, final ByteBuf encoded,
                                 final ChannelPromise promise) {
        final ExpectContinueSignal signal = expectContinue;
        if (signal == null) {
            ctx.write(encoded, promise);
        } else {
            // The promise is shared by all writes of the request, so it can't tell when the headers were written.
            final ChannelPromise headersPromise = ctx.newPromise();
            headersPromise.addListener(signal);
            PromiseNotifier.cascade(headersPromise, promise);
            ctx.write(encoded, headersPromise);
        }
    }

    @Override
    public void userEventTriggered(final ChannelHandlerContext ctx, final Object evt) {
        if (expectContinue != null) {
            if (evt == ContinueUserEvent.INSTANCE) {
                // Reset after receiving 100 (Continue), a 2xx response, or when the wait timed out.
                expectContinue = null;
            } else if (evt == CancelWriteUserEvent.INSTANCE) {
                // Mark as content consumed and reset to prepare for the next request on the same connection.
                contentLenConsumed(ctx, null);
                expectContinue = null;
            }
        }
        ctx.fireUserEventTriggered(evt);
    }

    @Override
    public void handlerRemoved(final ChannelHandlerContext ctx) {
        if (expectContinue != null) {
            expectContinue.cancelTimer();
        }
    }

    /**
     * {@link Signal} of a request with "Expect: 100-continue" header. Owns the timer that sends the payload body if no
     * response arrives within {@code expectContinueTimeout}.
     */
    private final class ExpectContinueSignal extends Signal implements ChannelFutureListener, Runnable {
        private final ChannelHandlerContext ctx;
        @Nullable
        private ScheduledFuture<?> timer;
        private boolean waiting = true;

        ExpectContinueSignal(final ChannelHandlerContext ctx) {
            this.ctx = ctx;
        }

        @Override
        boolean stopExpectContinue() {
            if (!waiting) {
                return false;
            }
            waiting = false;
            cancelTimer();
            return true;
        }

        void cancelTimer() {
            final ScheduledFuture<?> future = timer;
            if (future != null) {
                timer = null;
                future.cancel(false);
            }
        }

        /**
         * Starts the timer when the headers are written.
         */
        @Override
        public void operationComplete(final ChannelFuture future) {
            if (future.isSuccess() && waiting) {
                timer = ctx.executor().schedule(this, toNanos(expectContinueTimeout), NANOSECONDS);
            }
        }

        /**
         * Sends the payload body when the timer fires.
         */
        @Override
        public void run() {
            timer = null;
            if (waiting) {
                waiting = false;
                LOGGER.debug("{} No response to Expect: 100-continue within {}ms, sending the payload body",
                        ctx.channel(), expectContinueTimeout.toMillis());
                userEventTriggered(ctx, ContinueUserEvent.INSTANCE);
            }
        }
    }
}

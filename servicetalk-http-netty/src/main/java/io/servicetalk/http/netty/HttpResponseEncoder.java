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
import io.servicetalk.http.api.HttpRequestMethod;
import io.servicetalk.http.api.HttpResponseMetaData;
import io.servicetalk.http.api.HttpResponseStatus;
import io.servicetalk.transport.netty.internal.CloseHandler;

import java.util.Queue;

import static io.netty.handler.codec.http.HttpConstants.SP;
import static io.servicetalk.http.api.HttpHeaderNames.CONTENT_LENGTH;
import static io.servicetalk.http.api.HttpHeaderNames.SEC_WEBSOCKET_VERSION;
import static io.servicetalk.http.api.HttpHeaderNames.TRANSFER_ENCODING;
import static io.servicetalk.http.api.HttpRequestMethod.CONNECT;
import static io.servicetalk.http.api.HttpRequestMethod.HEAD;
import static io.servicetalk.http.api.HttpResponseStatus.NOT_MODIFIED;
import static io.servicetalk.http.api.HttpResponseStatus.NO_CONTENT;
import static io.servicetalk.http.api.HttpResponseStatus.SWITCHING_PROTOCOLS;
import static io.servicetalk.http.api.HttpResponseStatus.StatusClass.INFORMATIONAL_1XX;
import static io.servicetalk.http.api.HttpResponseStatus.StatusClass.SUCCESSFUL_2XX;
import static io.servicetalk.transport.netty.internal.CloseHandler.UNSUPPORTED_PROTOCOL_CLOSE_HANDLER;
import static java.util.Objects.requireNonNull;

final class HttpResponseEncoder extends HttpObjectEncoder<HttpResponseMetaData> {
    private final Queue<HttpRequestMethod> methodQueue;

    /**
     * Create a new instance.
     * @param methodQueue A queue used to enforce HTTP protocol semantics related to request/response lengths.
     * @param headersEncodedSizeAccumulator Used to calculate an exponential moving average of the encoded size of the
     * initial line and the headers for a guess for future buffer allocations.
     * @param trailersEncodedSizeAccumulator  Used to calculate an exponential moving average of the encoded size of
     * the trailers for a guess for future buffer allocations.
     * @param closeHandler the {@link CloseHandler}
     */
    HttpResponseEncoder(Queue<HttpRequestMethod> methodQueue, int headersEncodedSizeAccumulator,
                        int trailersEncodedSizeAccumulator, final CloseHandler closeHandler) {
        super(headersEncodedSizeAccumulator, trailersEncodedSizeAccumulator, closeHandler);
        this.methodQueue = requireNonNull(methodQueue);
    }

    /**
     * Create a new instance.
     * @param methodQueue A queue used to enforce HTTP protocol semantics related to request/response lengths.
     * @param headersEncodedSizeAccumulator Used to calculate an exponential moving average of the encoded size of the
     * initial line and the headers for a guess for future buffer allocations.
     * @param trailersEncodedSizeAccumulator  Used to calculate an exponential moving average of the encoded size of
     * the trailers for a guess for future buffer allocations.
     */
    HttpResponseEncoder(Queue<HttpRequestMethod> methodQueue,
                        int headersEncodedSizeAccumulator, int trailersEncodedSizeAccumulator) {
        this(methodQueue, headersEncodedSizeAccumulator, trailersEncodedSizeAccumulator,
                UNSUPPORTED_PROTOCOL_CLOSE_HANDLER);
    }

    @Override
    protected HttpResponseMetaData castMetaData(Object msg) {
        return (HttpResponseMetaData) msg;
    }

    @Override
    protected void encodeInitialLine(Buffer stBuffer, HttpResponseMetaData message) {
        message.version().writeTo(stBuffer);
        stBuffer.writeByte(SP);
        message.status().writeTo(stBuffer);
        stBuffer.writeShort(CRLF_SHORT);
    }

    @Override
    protected long getContentLength(final HttpResponseMetaData message) {
        return HttpObjectDecoder.getContentLength(message);
    }

    @Override
    protected void sanitizeHeadersBeforeEncode(HttpResponseMetaData msg, boolean isAlwaysEmpty) {
        // This method has side effects on the methodQueue for the following reasons:
        // - createMessage will not necessarily fire a message up the pipeline.
        // - the trigger points on the queue are currently symmetric for the request/response decoder and
        // request/response encoder. We may use header information on the response decoder side, and the queue
        // interaction is conditional (1xx responses don't touch the queue).
        // - unit tests exist which verify these side effects occur, so if behavior of the internal classes changes the
        // unit test should catch it.
        // - this is the rough equivalent of what is done in Netty in terms of sequencing. Instead of trying to
        // iterate a decoded list it makes some assumptions about the base class ordering of events.
        HttpRequestMethod method = methodQueue.poll();

        HttpHeaders headers = msg.headers();
        if (isAlwaysEmpty) {
            final HttpResponseStatus status = msg.status();
            if (status.statusClass() == INFORMATIONAL_1XX || status.code() == NO_CONTENT.code()) {

                // Stripping Content-Length:
                // See https://tools.ietf.org/html/rfc7230#section-3.3.2
                headers.remove(CONTENT_LENGTH);

                // Stripping Transfer-Encoding:
                // See https://tools.ietf.org/html/rfc7230#section-3.3.1
                headers.remove(TRANSFER_ENCODING);
            }
        } else if (CONNECT.equals(method) && msg.status().statusClass() == SUCCESSFUL_2XX) {

            // Stripping Content-Length:
            // See https://tools.ietf.org/html/rfc7230#section-3.3.2
            headers.remove(CONTENT_LENGTH);

            // Stripping Transfer-Encoding:
            // See https://tools.ietf.org/html/rfc7230#section-3.3.1
            headers.remove(TRANSFER_ENCODING);
        }
    }

    @Override
    protected boolean isContentAlwaysEmpty(HttpResponseMetaData msg) {
        // Peek from the queue here as sanitizeHeadersBeforeEncode is responsible for poll.
        final HttpRequestMethod method = methodQueue.peek();
        if (HEAD.equals(method)) {
            return true;
        }

        // Correctly handle special cases as stated in:
        // https://tools.ietf.org/html/rfc7230#section-3.3.3
        final HttpResponseStatus status = msg.status();

        if (status.statusClass() == INFORMATIONAL_1XX) {
            if (status.code() == SWITCHING_PROTOCOLS.code()) {
                // We need special handling for WebSockets version 00 as it will include an body.
                // Fortunally this version should not really be used in the wild very often.
                // See https://tools.ietf.org/html/draft-ietf-hybi-thewebsocketprotocol-00#section-1.2
                return msg.headers().contains(SEC_WEBSOCKET_VERSION);
            }
            return true;
        }
        return status.code() == NO_CONTENT.code() || status.code() == NOT_MODIFIED.code();
    }
}

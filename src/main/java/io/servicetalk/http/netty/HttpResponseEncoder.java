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

import io.servicetalk.http.api.HttpHeaders;
import io.servicetalk.http.api.HttpRequestMethod;
import io.servicetalk.http.api.HttpResponseMetaData;
import io.servicetalk.http.api.HttpResponseStatus;
import io.servicetalk.transport.netty.internal.CloseHandler;

import io.netty.buffer.ByteBuf;

import java.util.Queue;

import static io.netty.buffer.ByteBufUtil.writeShortBE;
import static io.netty.handler.codec.http.HttpConstants.SP;
import static io.servicetalk.http.api.HttpHeaderNames.CONTENT_LENGTH;
import static io.servicetalk.http.api.HttpHeaderNames.SEC_WEBSOCKET_VERSION;
import static io.servicetalk.http.api.HttpHeaderNames.TRANSFER_ENCODING;
import static io.servicetalk.http.api.HttpRequestMethods.CONNECT;
import static io.servicetalk.http.api.HttpResponseStatus.StatusClass.INFORMATIONAL_1XX;
import static io.servicetalk.http.api.HttpResponseStatus.StatusClass.SUCCESS_2XX;
import static io.servicetalk.http.api.HttpResponseStatuses.NOT_MODIFIED;
import static io.servicetalk.http.api.HttpResponseStatuses.NO_CONTENT;
import static io.servicetalk.http.api.HttpResponseStatuses.SWITCHING_PROTOCOLS;
import static io.servicetalk.transport.netty.internal.CloseHandler.NOOP_CLOSE_HANDLER;
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
        this(methodQueue, headersEncodedSizeAccumulator, trailersEncodedSizeAccumulator, NOOP_CLOSE_HANDLER);
    }

    @Override
    protected HttpResponseMetaData castMetaData(Object msg) {
        return (HttpResponseMetaData) msg;
    }

    @Override
    protected void encodeInitialLine(ByteBuf buf, HttpResponseMetaData message) {
        buf.writeBytes(toByteBuf(message.getVersion().getHttpVersion()));
        buf.writeByte(SP);
        buf.writeBytes(toByteBuf(message.getStatus().getCodeBuffer()));
        buf.writeByte(SP);
        buf.writeBytes(toByteBuf(message.getStatus().getReasonPhrase()));
        writeShortBE(buf, CRLF_SHORT);
    }

    @Override
    protected void sanitizeHeadersBeforeEncode(HttpResponseMetaData msg, boolean isAlwaysEmpty) {
        // This method has side effects on the methodQueue for the following reasons:
        // - createMessage will not necessary fire a message up the pipeline.
        // - the trigger points on the queue are currently symmetric for the request/response decoder and
        // request/response encoder. We may use header information on the response decoder side, and the queue
        // interaction is conditional (1xx responses don't touch the queue).
        // - unit tests exist which verify these side effects occur, so if behavior of the internal classes changes the
        // unit test should catch it.
        // - this is the rough equivalent of what is done in Netty in terms of sequencing. Instead of trying to
        // iterate a decoded list it makes some assumptions about the base class ordering of events.
        HttpRequestMethod method = methodQueue.poll();
        if (isAlwaysEmpty) {
            HttpResponseStatus status = msg.getStatus();
            if (status.getStatusClass() == INFORMATIONAL_1XX ||
                    status.getCode() == NO_CONTENT.getCode()) {

                HttpHeaders headers = msg.getHeaders();
                // Stripping Content-Length:
                // See https://tools.ietf.org/html/rfc7230#section-3.3.2
                headers.remove(CONTENT_LENGTH);

                // Stripping Transfer-Encoding:
                // See https://tools.ietf.org/html/rfc7230#section-3.3.1
                headers.remove(TRANSFER_ENCODING);
            }
        } else if (method == CONNECT && msg.getStatus().getStatusClass() == SUCCESS_2XX) {
            // Stripping Transfer-Encoding:
            // See https://tools.ietf.org/html/rfc7230#section-3.3.1
            msg.getHeaders().remove(TRANSFER_ENCODING);
        }
    }

    @Override
    protected boolean isContentAlwaysEmpty(HttpResponseMetaData msg) {
        // Correctly handle special cases as stated in:
        // https://tools.ietf.org/html/rfc7230#section-3.3.3
        HttpResponseStatus status = msg.getStatus();

        if (status.getStatusClass() == INFORMATIONAL_1XX) {
            if (status.getCode() == SWITCHING_PROTOCOLS.getCode()) {
                // We need special handling for WebSockets version 00 as it will include an body.
                // Fortunally this version should not really be used in the wild very often.
                // See https://tools.ietf.org/html/draft-ietf-hybi-thewebsocketprotocol-00#section-1.2
                return msg.getHeaders().contains(SEC_WEBSOCKET_VERSION);
            }
            return true;
        }
        return status.getCode() == NO_CONTENT.getCode() || status.getCode() == NOT_MODIFIED.getCode();
    }
}

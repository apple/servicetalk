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
import io.servicetalk.http.api.HttpResponseMetaData;
import io.servicetalk.http.api.HttpResponseStatus;

import io.netty.buffer.ByteBuf;

import static io.netty.buffer.ByteBufUtil.writeShortBE;
import static io.netty.handler.codec.http.HttpConstants.SP;
import static io.servicetalk.http.api.HttpHeaderNames.CONTENT_LENGTH;
import static io.servicetalk.http.api.HttpHeaderNames.SEC_WEBSOCKET_VERSION;
import static io.servicetalk.http.api.HttpHeaderNames.TRANSFER_ENCODING;
import static io.servicetalk.http.api.HttpResponseStatus.StatusClass.INFORMATIONAL_1XX;
import static io.servicetalk.http.api.HttpResponseStatuses.NOT_MODIFIED;
import static io.servicetalk.http.api.HttpResponseStatuses.NO_CONTENT;
import static io.servicetalk.http.api.HttpResponseStatuses.SWITCHING_PROTOCOLS;

final class HttpResponseEncoder extends HttpObjectEncoder<HttpResponseMetaData> {
    /**
     * Create a new instance.
     * @param headersEncodedSizeAccumulator Used to calculate an exponential moving average of the encoded size of the
     * initial line and the headers for a guess for future buffer allocations.
     * @param trailersEncodedSizeAccumulator  Used to calculate an exponential moving average of the encoded size of
     * the trailers for a guess for future buffer allocations.
     */
    HttpResponseEncoder(int headersEncodedSizeAccumulator, int trailersEncodedSizeAccumulator) {
        super(headersEncodedSizeAccumulator, trailersEncodedSizeAccumulator);
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

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

import io.servicetalk.http.api.HttpRequestMetaData;

import io.netty.buffer.ByteBuf;
import io.netty.util.CharsetUtil;

import static io.netty.buffer.ByteBufUtil.writeMediumBE;
import static io.netty.buffer.ByteBufUtil.writeShortBE;
import static io.netty.handler.codec.http.HttpConstants.SP;

final class HttpRequestEncoder extends HttpObjectEncoder<HttpRequestMetaData> {
    private static final char SLASH = '/';
    private static final char QUESTION_MARK = '?';
    private static final int SLASH_AND_SPACE_SHORT = (SLASH << 8) | SP;
    private static final int SPACE_SLASH_AND_SPACE_MEDIUM = (SP << 16) | SLASH_AND_SPACE_SHORT;

    /**
     * Create a new instance.
     * @param headersEncodedSizeAccumulator Used to calculate an exponential moving average of the encoded size of the
     * initial line and the headers for a guess for future buffer allocations.
     * @param trailersEncodedSizeAccumulator  Used to calculate an exponential moving average of the encoded size of
     * the trailers for a guess for future buffer allocations.
     */
    HttpRequestEncoder(int headersEncodedSizeAccumulator, int trailersEncodedSizeAccumulator) {
        super(headersEncodedSizeAccumulator, trailersEncodedSizeAccumulator);
    }

    @Override
    protected HttpRequestMetaData castMetaData(Object msg) {
        return (HttpRequestMetaData) msg;
    }

    @Override
    protected void encodeInitialLine(ByteBuf buf, HttpRequestMetaData message) {
        buf.writeBytes(toByteBuf(message.getMethod().getName()));

        String uri = message.getRequestTarget();

        if (uri.isEmpty()) {
            // Add " / " as absolute path if uri is not present.
            // See http://tools.ietf.org/html/rfc2616#section-5.1.2
            writeMediumBE(buf, SPACE_SLASH_AND_SPACE_MEDIUM);
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

            buf.writeByte(SP).writeCharSequence(uriCharSequence, CharsetUtil.UTF_8);
            if (needSlash) {
                // write "/ " after uri
                writeShortBE(buf, SLASH_AND_SPACE_SHORT);
            } else {
                buf.writeByte(SP);
            }
        }

        buf.writeBytes(toByteBuf(message.getVersion().getHttpVersion()));
        writeShortBE(buf, CRLF_SHORT);
    }
}

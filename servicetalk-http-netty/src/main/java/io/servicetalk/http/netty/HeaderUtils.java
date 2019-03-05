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
package io.servicetalk.http.netty;

import io.servicetalk.http.api.HttpHeaders;
import io.servicetalk.http.api.HttpMetaData;
import io.servicetalk.http.api.HttpRequestMetaData;
import io.servicetalk.http.api.HttpRequestMethod;
import io.servicetalk.http.api.HttpResponseMetaData;
import io.servicetalk.http.api.HttpResponseStatus;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Iterator;

import static io.servicetalk.http.api.HttpHeaderNames.CONTENT_LENGTH;
import static io.servicetalk.http.api.HttpHeaderNames.TRANSFER_ENCODING;
import static io.servicetalk.http.api.HttpHeaderValues.CHUNKED;
import static io.servicetalk.http.api.HttpRequestMethods.CONNECT;
import static io.servicetalk.http.api.HttpRequestMethods.HEAD;
import static io.servicetalk.http.api.HttpResponseStatus.StatusClass.INFORMATIONAL_1XX;
import static io.servicetalk.http.api.HttpResponseStatus.StatusClass.SUCCESSFUL_2XX;
import static io.servicetalk.http.api.HttpResponseStatuses.NOT_MODIFIED;
import static io.servicetalk.http.api.HttpResponseStatuses.NO_CONTENT;

final class HeaderUtils {

    private static final Logger LOGGER = LoggerFactory.getLogger(HeaderUtils.class);

    private HeaderUtils() {
        // no instances
    }

    static boolean isTransferEncodingChunked(final HttpHeaders headers) {
        return headers.contains(TRANSFER_ENCODING, CHUNKED, true);
    }

    static void setTransferEncodingChunked(final HttpHeaders headers, final boolean chunked) {
        if (chunked) {
            headers.set(TRANSFER_ENCODING, CHUNKED);
            headers.remove(CONTENT_LENGTH);
        } else {
            final Iterator<? extends CharSequence> itr = headers.values(TRANSFER_ENCODING);
            while (itr.hasNext()) {
                if (io.netty.handler.codec.http.HttpHeaderValues.CHUNKED.contentEqualsIgnoreCase(itr.next())) {
                    itr.remove();
                }
            }
        }
    }

    static void addRequestTransferEncodingIfNecessary(final HttpRequestMetaData request) {
        addTransferEncodingIfNecessary(request);
    }

    static void addResponseTransferEncodingIfNecessary(final HttpResponseMetaData response,
                                                       final HttpRequestMethod requestMethod) {
        final HttpResponseStatus status = response.status();
        if (HEAD.equals(requestMethod) || INFORMATIONAL_1XX.contains(status)
                || NO_CONTENT.equals(status) || NOT_MODIFIED.equals(status)) {
            // Do not add a transfer-encoding header in this case. See 3.3.3.1:
            // https://tools.ietf.org/html/rfc7230#section-3.3.3
            return;
        }
        if (CONNECT.equals(requestMethod) && SUCCESSFUL_2XX.contains(status)) {
            // Do not add a transfer-encoding header in this case. See 3.3.3.2:
            // https://tools.ietf.org/html/rfc7230#section-3.3.3
            return;
        }
        addTransferEncodingIfNecessary(response);
    }

    /**
     * Add a {@code transfer-encoding: chunked} header if there is no {@code content-length} or
     * {@code transfer-encoding: chunked} header.
     *
     * @param metaData the message to operate on.
     */
    private static void addTransferEncodingIfNecessary(final HttpMetaData metaData) {
        final HttpHeaders headers = metaData.headers();
        if (!headers.contains(CONTENT_LENGTH) && !isTransferEncodingChunked(headers)) {
            LOGGER.debug("No '{}' or '{}: {}' headers, setting '{}: {}'.",
                    CONTENT_LENGTH, TRANSFER_ENCODING, CHUNKED, TRANSFER_ENCODING, CHUNKED);
            headers.add(TRANSFER_ENCODING, CHUNKED);
            // See https://tools.ietf.org/html/rfc7230#section-3.3.3
        }
    }
}

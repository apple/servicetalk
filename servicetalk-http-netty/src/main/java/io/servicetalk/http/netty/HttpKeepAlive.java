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

import io.servicetalk.http.api.HttpMetaData;
import io.servicetalk.http.api.StreamingHttpResponse;

import static io.servicetalk.http.api.HttpHeaderNames.CONNECTION;
import static io.servicetalk.http.api.HttpHeaderValues.CLOSE;
import static io.servicetalk.http.api.HttpHeaderValues.KEEP_ALIVE;
import static io.servicetalk.http.api.HttpProtocolVersion.HTTP_1_0;
import static io.servicetalk.http.api.HttpProtocolVersion.HTTP_1_1;

enum HttpKeepAlive {

    CLOSE_ADD_HEADER(true, true),
    CLOSE_NO_HEADER(true, false),
    KEEP_ALIVE_NO_HEADER(false, false),
    KEEP_ALIVE_ADD_HEADER(false, true);

    private final boolean shouldCloseConnection;
    private final boolean shouldAddConnectionHeader;

    HttpKeepAlive(final boolean shouldCloseConnection, final boolean shouldAddConnectionHeader) {
        this.shouldCloseConnection = shouldCloseConnection;
        this.shouldAddConnectionHeader = shouldAddConnectionHeader;
    }

    // In the interest of performance we are not accommodating for the spec allowing multiple header fields
    // or comma-separated values for the Connection header. See: https://tools.ietf.org/html/rfc7230#section-3.2.2
    static HttpKeepAlive responseKeepAlive(final HttpMetaData metaData) {
        // If multiple protocols are supported we don't know which protocol will be negotiated. Treat any major
        // protocol >= 2 the same.
        if (metaData.version().major() >= 2) {
            // https://www.rfc-editor.org/rfc/rfc7540#section-8.1.2.2
            // HTTP/2 does not use the Connection header field to indicate connection-specific header fields...
            return KEEP_ALIVE_NO_HEADER;
        } else if (HTTP_1_1.equals(metaData.version())) {
            return metaData.headers().containsIgnoreCase(CONNECTION, CLOSE) ?
                    CLOSE_ADD_HEADER : KEEP_ALIVE_NO_HEADER;
        } else if (HTTP_1_0.equals(metaData.version())) {
            return metaData.headers().containsIgnoreCase(CONNECTION, KEEP_ALIVE) ?
                    KEEP_ALIVE_ADD_HEADER : CLOSE_NO_HEADER;
        } else {
            return CLOSE_NO_HEADER;
        }
    }

    static boolean shouldClose(final HttpMetaData metaData) {
        return responseKeepAlive(metaData).shouldCloseConnection;
    }

    void addConnectionHeaderIfNecessary(final StreamingHttpResponse response) {
        if (shouldAddConnectionHeader) {
            if (shouldCloseConnection) {
                response.headers().set(CONNECTION, CLOSE);
            } else {
                response.headers().set(CONNECTION, KEEP_ALIVE);
            }
        }
    }
}

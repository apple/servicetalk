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

import java.util.Iterator;

import static io.servicetalk.http.api.HttpHeaderNames.CONTENT_LENGTH;
import static io.servicetalk.http.api.HttpHeaderNames.TRANSFER_ENCODING;
import static io.servicetalk.http.api.HttpHeaderValues.CHUNKED;

/**
 * Duplicate of HeaderUtils in http-api, will be removed in the future.
 */
final class HeaderUtils {
    private HeaderUtils() {
        // no instances
    }

    static boolean isTransferEncodingChunked(HttpHeaders headers) {
        return headers.contains(TRANSFER_ENCODING, CHUNKED, true);
    }

    static void setTransferEncodingChunked(HttpHeaders headers, boolean chunked) {
        if (chunked) {
            headers.set(TRANSFER_ENCODING, CHUNKED);
            headers.remove(CONTENT_LENGTH);
        } else {
            Iterator<? extends CharSequence> itr = headers.getAll(TRANSFER_ENCODING);
            while (itr.hasNext()) {
                if (io.netty.handler.codec.http.HttpHeaderValues.CHUNKED.contentEqualsIgnoreCase(itr.next())) {
                    itr.remove();
                }
            }
        }
    }
}

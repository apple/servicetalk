/*
 * Copyright © 2019 Apple Inc. and the ServiceTalk project authors
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

import io.servicetalk.http.api.HttpResponseStatus;

import static io.servicetalk.http.api.HttpResponseStatus.INTERNAL_SERVER_ERROR;
import static io.servicetalk.http.api.HttpResponseStatus.OK;
import static io.servicetalk.http.api.HttpResponseStatus.REQUEST_HEADER_FIELDS_TOO_LARGE;

final class HttpUtils {

    private HttpUtils() {
        // No instances
    }

    static HttpResponseStatus status(final int statusCode) {
        final HttpResponseStatus status;
        switch (statusCode) {
            case 200:
                status = OK;
                break;
            case 431:
                status = REQUEST_HEADER_FIELDS_TOO_LARGE;
                break;
            case 500:
                status = INTERNAL_SERVER_ERROR;
                break;
            case 600:
                status = HttpResponseStatus.of(600, "Short");
                break;
            case 700:
                status = HttpResponseStatus.of(700, "Some reason phrase which is longer than the usual one");
                break;
            default:
                throw new IllegalArgumentException("Unknown statusCode: " + statusCode);
        }
        return status;
    }
}

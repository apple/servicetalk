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

import io.servicetalk.http.api.HttpProtocolVersion;
import io.servicetalk.http.api.HttpProtocolVersions;

import io.netty.handler.codec.http.HttpVersion;

import static io.servicetalk.http.api.HttpProtocolVersions.HTTP_1_0;
import static io.servicetalk.http.api.HttpProtocolVersions.HTTP_1_1;
import static io.servicetalk.http.api.HttpProtocolVersions.getProtocolVersion;

final class NettyHttpProtocolVersionConverter {

    private NettyHttpProtocolVersionConverter() {
        // No instances.
    }

    static HttpProtocolVersion fromNettyHttpVersion(final HttpVersion nettyHttpVersion) {
        if (HttpVersion.HTTP_1_1 == nettyHttpVersion) {
            return HTTP_1_1;
        }
        if (HttpVersion.HTTP_1_0 == nettyHttpVersion) {
            return HTTP_1_0;
        }
        return getProtocolVersion(nettyHttpVersion.majorVersion(), nettyHttpVersion.minorVersion());
    }

    static HttpVersion toNettyHttpVersion(final HttpProtocolVersion httpVersion) {
        if (HttpProtocolVersions.HTTP_1_1 == httpVersion) {
            return HttpVersion.HTTP_1_1;
        }
        if (HttpProtocolVersions.HTTP_1_0 == httpVersion) {
            return HttpVersion.HTTP_1_0;
        }
        return new HttpVersion("HTTP", httpVersion.getMajorVersion(), httpVersion.getMinorVersion(), false);
    }
}

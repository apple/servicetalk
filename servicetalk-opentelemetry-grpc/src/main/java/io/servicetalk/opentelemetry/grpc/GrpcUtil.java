/*
 * Copyright © 2025 Apple Inc. and the ServiceTalk project authors
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

package io.servicetalk.opentelemetry.grpc;

import io.servicetalk.http.api.HttpProtocolVersion;
import io.servicetalk.http.api.HttpRequestMetaData;
import io.servicetalk.http.api.HttpResponseMetaData;

final class GrpcUtil {
    private GrpcUtil() {
    }

    public static String httpVersionAsString(HttpResponseMetaData responseMetaData) {
        HttpProtocolVersion version = responseMetaData.version();
        return getProtocolVersion(version);
    }

    public static String httpVersionAsString(HttpRequestMetaData requestMetaData) {
        HttpProtocolVersion version = requestMetaData.version();
        return getProtocolVersion(version);
    }

    private static String getProtocolVersion(HttpProtocolVersion version) {
        if (version.major() == 1) {
            if (version.minor() == 1) {
                return "1.1";
            }
            if (version.minor() == 0) {
                return "1.0";
            }
        } else if (version.major() == 2 && version.minor() == 0) {
            return "2.0";
        }
        return version.major() + "." + version.minor();
    }
}

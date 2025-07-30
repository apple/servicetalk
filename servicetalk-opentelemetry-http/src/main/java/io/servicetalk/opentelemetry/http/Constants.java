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
package io.servicetalk.opentelemetry.http;

final class Constants {

    static final String HTTP_SCHEME = "http";
    static final String HTTPS_SCHEME = "https";
    static final String IPV4 = "ipv4";
    static final String IPV6 = "ipv6";
    static final String TCP = "tcp";
    static final String UNIX = "unix";

    private Constants() {
        // no instances.
    }
}

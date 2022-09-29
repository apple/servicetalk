/*
 * Copyright © 2020-2021 Apple Inc. and the ServiceTalk project authors
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

import io.servicetalk.http.api.HttpProtocolConfig;
import io.servicetalk.http.api.HttpProtocolVersion;

import java.util.Arrays;
import java.util.Collection;

import static io.servicetalk.http.api.HttpProtocolVersion.HTTP_1_1;
import static io.servicetalk.http.api.HttpProtocolVersion.HTTP_2_0;
import static io.servicetalk.http.netty.HttpProtocolConfigs.h1Default;
import static io.servicetalk.http.netty.HttpProtocolConfigs.h2;
import static io.servicetalk.logging.api.LogLevel.TRACE;

enum HttpProtocol {
    HTTP_1(h1Default(), HTTP_1_1),
    HTTP_2(h2().enableFrameLogging("servicetalk-tests-h2-frame-logger", TRACE, () -> true).build(), HTTP_2_0);

    final HttpProtocolConfig config;
    final HttpProtocolVersion version;

    HttpProtocol(HttpProtocolConfig config, HttpProtocolVersion version) {
        this.config = config;
        this.version = version;
    }

    static HttpProtocolConfig[] toConfigs(Collection<HttpProtocol> protocols) {
        return protocols.stream().map(p -> p.config).toArray(HttpProtocolConfig[]::new);
    }

    static HttpProtocolConfig[] toConfigs(HttpProtocol[] protocols) {
        return Arrays.stream(protocols).map(p -> p.config).toArray(HttpProtocolConfig[]::new);
    }
}

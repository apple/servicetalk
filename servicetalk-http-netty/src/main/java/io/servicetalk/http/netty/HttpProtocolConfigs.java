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

import io.servicetalk.http.api.HttpProtocolConfig;

import java.util.Map;

/**
 * Factory methods for {@link HttpProtocolConfig}s and builders for their customization.
 */
public final class HttpProtocolConfigs {

    private static final H1ProtocolConfig H1_DEFAULT = h1().build();
    private static final H2ProtocolConfig H2_DEFAULT = h2().build();

    private HttpProtocolConfigs() {
        // No instances
    }

    /**
     * Returns {@link H1ProtocolConfig} with the default configuration for
     * <a href="https://tools.ietf.org/html/rfc7230">HTTP/1.1</a>.
     *
     * @return {@link H1ProtocolConfig} with the default configuration for
     * <a href="https://tools.ietf.org/html/rfc7230">HTTP/1.1</a>
     */
    public static H1ProtocolConfig h1Default() {
        return H1_DEFAULT;
    }

    /**
     * Returns a builder for {@link H1ProtocolConfig}.
     *
     * @return {@link H1ProtocolConfigBuilder}
     */
    public static H1ProtocolConfigBuilder h1() {
        return new H1ProtocolConfigBuilder();
    }

    /**
     * Returns {@link H2ProtocolConfig} with the default configuration for
     * <a href="https://tools.ietf.org/html/rfc7540">HTTP/2</a>.
     * <p>
     * Note this doesn't necessarily provide {@link H2ProtocolConfig#initialSettings()} that corresponds to
     * default values as described in
     * <a href="https://datatracker.ietf.org/doc/html/rfc7540#section-6.5.2">HTTP/2 Settings</a>. Some identifiers
     * maybe overridden for safety or performance reasons and are subject to change. For more control use {@link #h2()}.
     *
     * @return {@link H2ProtocolConfig} with the default configuration for
     * <a href="https://tools.ietf.org/html/rfc7540">HTTP/2</a>
     */
    public static H2ProtocolConfig h2Default() {
        return H2_DEFAULT;
    }

    /**
     * Returns a builder for {@link H2ProtocolConfig}.
     * <p>
     * Note this doesn't necessarily provide {@link H2ProtocolConfig#initialSettings()} that corresponds to
     * default values as described in
     * <a href="https://datatracker.ietf.org/doc/html/rfc7540#section-6.5.2">HTTP/2 Settings</a>. Some identifiers
     * maybe overridden for safety or performance reasons and are subject to change. For more control use
     * {@link H2ProtocolConfigBuilder#initialSettings(Map)}.
     *
     * @return {@link H2ProtocolConfigBuilder}
     */
    public static H2ProtocolConfigBuilder h2() {
        return new H2ProtocolConfigBuilder();
    }
}

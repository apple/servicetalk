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

import io.servicetalk.http.api.HttpRequestMetaData;
import io.servicetalk.transport.api.HostAndPort;
import io.servicetalk.transport.api.SslConfig;

import javax.annotation.Nullable;

/**
 * Provides a {@link SslConfig} for specified {@link HostAndPort}.
 */
public interface SslConfigProvider {

    /**
     * Return a default port number for specified URI scheme and effective host.
     * This method will be invoked in case the {@link HttpRequestMetaData} does not have information about
     * {@link HttpRequestMetaData#effectivePort() effective port number}.
     *
     * @param scheme A {@link HttpScheme} of the request.
     * @param effectiveHost An effective host for the request.
     * @return A port number for specified {@code scheme} and {@code effectiveHost}.
     * @see HttpRequestMetaData#effectivePort()
     */
    int defaultPort(HttpScheme scheme, String effectiveHost);

    /**
     * Return a {@link SslConfig} for specified {@link HostAndPort}.
     *
     * @param hostAndPort A {@link HostAndPort} for which a {@link SslConfig} should be provided.
     * @return A {@link SslConfig} for specified {@link HostAndPort}, or {@code null} if no {@link SslConfig} should be
     * provided.
     */
    @Nullable
    SslConfig forHostAndPort(HostAndPort hostAndPort);
}

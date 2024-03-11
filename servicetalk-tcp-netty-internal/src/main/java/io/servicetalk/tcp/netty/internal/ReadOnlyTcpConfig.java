/*
 * Copyright © 2024 Apple Inc. and the ServiceTalk project authors
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
package io.servicetalk.tcp.netty.internal;

import io.servicetalk.logging.api.UserDataLoggerConfig;
import io.servicetalk.transport.api.ServiceTalkSocketOptions;
import io.servicetalk.transport.api.SslConfig;
import io.servicetalk.transport.netty.internal.FlushStrategy;

import io.netty.channel.ChannelOption;
import io.netty.handler.ssl.SslContext;

import java.util.Map;
import javax.annotation.Nullable;

public interface ReadOnlyTcpConfig<SecurityConfig extends SslConfig> {

    /**
     * Returns the {@link ChannelOption}s for accepted channels.
     *
     * @return Unmodifiable map of options
     */
    @SuppressWarnings("rawtypes")
    Map<ChannelOption, Object> options();

    /**
     * Returns the idle timeout as expressed via option {@link ServiceTalkSocketOptions#IDLE_TIMEOUT}.
     *
     * @return idle timeout in milliseconds
     */
    long idleTimeoutMs();

    /**
     * Returns the {@link FlushStrategy} for this client.
     *
     * @return {@link FlushStrategy} for this client
     */
    FlushStrategy flushStrategy();

    /**
     * Get the {@link UserDataLoggerConfig} for wire logging.
     *
     * @return the {@link UserDataLoggerConfig} for wire logging, or {@code null}.
     */
    @Nullable
    UserDataLoggerConfig wireLoggerConfig();

    /**
     * Returns the {@link SslContext}.
     *
     * @return {@link SslContext}, {@code null} if none specified
     */
    @Nullable
    SslContext sslContext();

    /**
     * Get the {@link SslConfig}.
     *
     * @return the {@link SslConfig}, or {@code null} if SSL/TLS is not configured.
     */
    @Nullable
    SecurityConfig sslConfig();
}

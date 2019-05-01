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
package io.servicetalk.tcp.netty.internal;

import io.servicetalk.transport.api.ServiceTalkSocketOptions;
import io.servicetalk.transport.netty.internal.FlushStrategy;
import io.servicetalk.transport.netty.internal.WireLoggingInitializer;

import io.netty.channel.ChannelOption;
import io.netty.handler.ssl.SslContext;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import javax.annotation.Nullable;

import static io.servicetalk.transport.netty.internal.FlushStrategies.defaultFlushStrategy;
import static java.util.Collections.unmodifiableMap;

/**
 Read only view of {@link TcpClientConfig}.
 */
public class ReadOnlyTcpClientConfig {

    @SuppressWarnings("rawtypes")
    protected final Map<ChannelOption, Object> options;
    protected boolean autoRead;
    @Nullable
    protected SslContext sslContext;
    @Nullable
    protected String sslHostnameVerificationAlgorithm;
    @Nullable
    protected String sslHostnameVerificationHost;
    protected int sslHostnameVerificationPort = -1;
    protected long idleTimeoutMs;
    @Nullable
    protected WireLoggingInitializer wireLoggingInitializer;
    protected FlushStrategy flushStrategy = defaultFlushStrategy();

    /**
     * New instance.
     *
     * @param autoRead If the channels created by this client will have auto-read enabled.
     */
    public ReadOnlyTcpClientConfig(boolean autoRead) {
        this.autoRead = autoRead;
        options = new LinkedHashMap<>();
    }

    /**
     * Copy constructor.
     *
     * @param from Source to copy from.
     * @param readOnlyOptions {@code true} to make the {@link #options()} unmodifiable.
     */
    @SuppressWarnings("rawtypes")
    protected ReadOnlyTcpClientConfig(TcpClientConfig from, boolean readOnlyOptions) {
        autoRead = from.autoRead;
        final Map<ChannelOption, Object> options = new HashMap<>(from.options);
        this.options = readOnlyOptions ? unmodifiableMap(options) : options;
        sslContext = from.sslContext;
        sslHostnameVerificationAlgorithm = from.sslHostnameVerificationAlgorithm;
        sslHostnameVerificationHost = from.sslHostnameVerificationHost;
        sslHostnameVerificationPort = from.sslHostnameVerificationPort;
        idleTimeoutMs = from.idleTimeoutMs;
        wireLoggingInitializer = from.wireLoggingInitializer;
        flushStrategy = from.flushStrategy;
    }

    /**
     * Returns whether auto-read is enabled.
     *
     * @return {@code true} if auto-read enabled.
     */
    public boolean isAutoRead() {
        return autoRead;
    }

    /**
     * Returns the {@link SslContext}.
     *
     * @return {@link SslContext}, {@code null} if none specified.
     */
    @Nullable
    public SslContext sslContext() {
        return sslContext;
    }

    /**
     * Returns the hostname verification algorithm, if any.
     *
     * @return hostname verification algorithm, {@code null} if none specified.
     */
    @Nullable
    public String sslHostnameVerificationAlgorithm() {
        return sslHostnameVerificationAlgorithm;
    }

    /**
     * Get the non-authoritative name of the host.
     *
     * @return the non-authoritative name of the host.
     */
    @Nullable
    public String sslHostnameVerificationHost() {
        return sslHostnameVerificationHost;
    }

    /**
     * Get the non-authoritative port.
     * <p>
     * Only valid if {@link #sslHostnameVerificationHost()} is not {@code null}.
     *
     * @return the non-authoritative port.
     */
    public int sslHostnameVerificationPort() {
        return sslHostnameVerificationPort;
    }

    /**
     * Returns the idle timeout as expressed via option {@link ServiceTalkSocketOptions#IDLE_TIMEOUT}.
     *
     * @return idle timeout.
     */
    public long idleTimeoutMs() {
        return idleTimeoutMs;
    }

    /**
     * Returns the {@link ChannelOption}s for all channels created by this client.
     *
     * @return Unmodifiable map of options.
     */
    public Map<ChannelOption, Object> options() {
        return options;
    }

    /**
     * Returns the {@link WireLoggingInitializer} if any for this client.
     *
     * @return {@link WireLoggingInitializer} if any.
     */
    @Nullable
    public WireLoggingInitializer wireLoggingInitializer() {
        return wireLoggingInitializer;
    }

    /**
     * Returns the {@link FlushStrategy} for this client.
     * @return {@link FlushStrategy} for this client.
     */
    public FlushStrategy flushStrategy() {
        return flushStrategy;
    }
}

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

import io.netty.channel.ChannelOption;
import io.netty.handler.ssl.SslContext;
import io.servicetalk.buffer.BufferAllocator;
import io.servicetalk.transport.api.ServiceTalkSocketOptions;
import io.servicetalk.transport.netty.internal.WireLogInitializer;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

import javax.annotation.Nullable;

import static io.servicetalk.buffer.netty.BufferAllocators.DEFAULT;
import static io.servicetalk.transport.netty.internal.WireLogInitializer.GLOBAL_WIRE_LOGGER;
import static java.util.Collections.unmodifiableMap;

/**
 Read only view of {@link TcpClientConfig}.
 */
public class ReadOnlyTcpClientConfig {

    //TODO 3.x: Add back attributes
    protected final boolean autoRead;
    @SuppressWarnings("rawtypes")
    protected final Map<ChannelOption, Object> optionMap;
    protected BufferAllocator allocator = DEFAULT.getAllocator();
    @Nullable protected SslContext sslContext;
    @Nullable protected String hostnameVerificationAlgorithm;
    protected long idleTimeoutMs;
    @Nullable protected WireLogInitializer wireLogger = GLOBAL_WIRE_LOGGER;

    /**
     * New instance.
     *
     * @param autoRead If the channels created by this client will have auto-read enabled.
     */
    public ReadOnlyTcpClientConfig(boolean autoRead) {
        this.autoRead = autoRead;
        optionMap = new LinkedHashMap<>();
    }

    /**
     * Copy constructor.
     *
     * @param from Source to copy from.
     */
    ReadOnlyTcpClientConfig(TcpClientConfig from) {
        autoRead = from.autoRead;
        optionMap = unmodifiableMap(new HashMap<>(from.optionMap));
        allocator = from.allocator;
        sslContext = from.sslContext;
        this.hostnameVerificationAlgorithm = from.hostnameVerificationAlgorithm;
        idleTimeoutMs = from.idleTimeoutMs;
        wireLogger = from.wireLogger;
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
     * Returns the {@link BufferAllocator}.
     * @return allocator
     */
    public BufferAllocator getAllocator() {
        return allocator;
    }

    /**
     * Returns the {@link SslContext}.
     * @return {@link SslContext}, {@code null} if none specified.
     */
    @Nullable
    public SslContext getSslContext() {
        return sslContext;
    }

    /**
     * Returns the hostname verification algorithm, if any.
     * @return hostname verification algorithm, {@code null} if none specified.
     */
    @Nullable
    public String getHostnameVerificationAlgorithm() {
        return hostnameVerificationAlgorithm;
    }

    /**
     * Returns the idle timeout as expressed via option {@link ServiceTalkSocketOptions#IDLE_TIMEOUT}.
     * @return idle timeout.
     */
    public long getIdleTimeoutMs() {
        return idleTimeoutMs;
    }

    /**
     * Returns the {@link ChannelOption}s for all channels created by this client.
     * @return Unmodifiable map of options.
     */
    public Map<ChannelOption, Object> getOptions() {
        return optionMap;
    }

    /**
     * Returns the {@link WireLogInitializer} if any for this client.
     *
     * @return {@link WireLogInitializer} if any.
     */
    @Nullable
    public WireLogInitializer getWireLogger() {
        return wireLogger;
    }
}

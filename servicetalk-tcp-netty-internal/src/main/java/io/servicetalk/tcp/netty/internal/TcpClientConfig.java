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
import io.servicetalk.transport.api.SslConfig;
import io.servicetalk.transport.netty.internal.BuilderUtils;
import io.servicetalk.transport.netty.internal.FlushStrategy;
import io.servicetalk.transport.netty.internal.WireLoggingInitializer;

import java.io.InputStream;
import java.net.SocketOption;
import javax.annotation.Nullable;

import static io.servicetalk.transport.netty.internal.SSLContextFactory.forClient;
import static java.util.Objects.requireNonNull;

/**
 * Configuration for TCP based servers. <p>Internal use only.</p>
 */
public final class TcpClientConfig extends ReadOnlyTcpClientConfig {
    /**
     * New instance.
     *
     * @param autoRead If the channels created by this client will have auto-read enabled.
     */
    public TcpClientConfig(boolean autoRead) {
        super(autoRead);
    }

    /**
     * Copy constructor.
     *
     * @param from The original {@link TcpClientConfig} to copy from.
     */
    public TcpClientConfig(TcpClientConfig from) {
        super(from, false);
    }

    /**
     * Enable SSL/TLS using the provided {@link SslConfig}. To disable it pass in {@code null}.
     *
     * @param config the {@link SslConfig}.
     * @return this.
     * @throws IllegalStateException if the {@link SslConfig#keyCertChainSupplier()}, {@link SslConfig#keySupplier()},
     * or {@link SslConfig#trustCertChainSupplier()} throws when
     * {@link InputStream#close()} is called.
     */
    public TcpClientConfig sslConfig(@Nullable SslConfig config) {
        if (config != null) {
            sslContext = forClient(config);
            sslHostnameVerificationAlgorithm = config.hostnameVerificationAlgorithm();
            sslHostnameVerificationHost = config.hostnameVerificationHost();
            sslHostnameVerificationPort = config.hostnameVerificationPort();
        } else {
            sslContext = null;
            sslHostnameVerificationAlgorithm = null;
            sslHostnameVerificationHost = null;
            sslHostnameVerificationPort = -1;
        }
        return this;
    }

    /**
     * Add a {@link SocketOption} for all connections created by this client.
     *
     * @param <T> the type of the value.
     * @param option the option to apply.
     * @param value the value.
     * @return this.
     */
    public <T> TcpClientConfig socketOption(SocketOption<T> option, T value) {
        if (option == ServiceTalkSocketOptions.IDLE_TIMEOUT) {
            idleTimeoutMs = (Long) value;
        } else {
            BuilderUtils.addOption(options, option, value);
        }
        return this;
    }

    /**
     * Enable wire-logging for this client. All wire events will be logged at trace level.
     *
     * @param loggerName The name of the logger to log wire events.
     * @return {@code this}.
     */
    public TcpClientConfig enableWireLogging(String loggerName) {
        wireLoggingInitializer = new WireLoggingInitializer(loggerName);
        return this;
    }

    /**
     * Disable previously configured wire-logging for this client.
     * If wire-logging has not been configured before, this method has no effect.
     *
     * @return {@code this}.
     * @see #enableWireLogging(String)
     */
    public TcpClientConfig disableWireLogging() {
        wireLoggingInitializer = null;
        return this;
    }

    /**
     * Sets {@link FlushStrategy} to use for all connections created by this client.
     *
     * @param flushStrategy {@link FlushStrategy} to use for all connections created by this client.
     * @return {@code this}.
     */
    public TcpClientConfig flushStrategy(FlushStrategy flushStrategy) {
        this.flushStrategy = requireNonNull(flushStrategy);
        return this;
    }

    /**
     * Returns an immutable view of this config, any changes to this config will not alter the returned view.
     *
     * @return {@link ReadOnlyTcpClientConfig}.
     */
    public ReadOnlyTcpClientConfig asReadOnly() {
        return new ReadOnlyTcpClientConfig(this, true);
    }
}

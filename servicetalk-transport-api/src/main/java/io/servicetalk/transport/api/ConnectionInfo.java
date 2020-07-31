/*
 * Copyright © 2018, 2020 Apple Inc. and the ServiceTalk project authors
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
package io.servicetalk.transport.api;

import java.net.SocketAddress;
import java.net.SocketOption;
import java.net.StandardSocketOptions;
import javax.annotation.Nullable;
import javax.net.ssl.SSLSession;

/**
 * Provides information about a connection.
 */
public interface ConnectionInfo {
    /**
     * The {@link SocketAddress} to which the associated connection is bound.
     *
     * @return The {@link SocketAddress} to which the associated connection is bound.
     */
    SocketAddress localAddress();

    /**
     * The {@link SocketAddress} to which the associated connection is connected.
     *
     * @return The {@link SocketAddress} to which the associated connection is connected.
     */
    SocketAddress remoteAddress();

    /**
     * Get the {@link SSLSession} for this connection.
     *
     * @return The {@link SSLSession} if SSL/TLS is enabled, or {@code null} otherwise.
     */
    @Nullable
    SSLSession sslSession();

    /**
     * Get the {@link ExecutionContext} for this {@link ConnectionInfo}.
     * <p>
     * The {@link ExecutionContext#ioExecutor()} will represent the thread responsible for IO for this
     * {@link ConnectionInfo}. Note that this maybe different that what was used to create this object because
     * at this time a specific {@link IoExecutor} has been selected.
     *
     * @return the {@link ExecutionContext} for this {@link ConnectionInfo}.
     */
    ExecutionContext executionContext();

    /**
     * Get the {@link SocketOption} value of type {@code T} for this {@link ConnectionInfo}.
     *
     * @param option {@link SocketOption} to get.
     * @param <T> the type of the {@link SocketOption} value.
     * @return the {@link SocketOption} value of type {@code T} for this {@link ConnectionInfo} or {@code null} if
     * this {@link SocketOption} is not supported by this {@link ConnectionInfo}.
     * @see StandardSocketOptions
     * @see ServiceTalkSocketOptions
     */
    @Nullable
    <T> T socketOption(SocketOption<T> option);

    /**
     * Get the {@link Protocol} for this {@link ConnectionInfo}.
     *
     * @return the {@link Protocol} for this {@link ConnectionInfo}.
     */
    Protocol protocol();

    /**
     * Provides information about the network protocol.
     */
    interface Protocol {

        /**
         * Returns name of the protocol.
         *
         * @return name of the protocol
         */
        String name();
    }
}

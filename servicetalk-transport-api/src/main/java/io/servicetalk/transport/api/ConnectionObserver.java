/*
 * Copyright © 2020 Apple Inc. and the ServiceTalk project authors
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

import javax.net.ssl.SSLSession;

/**
 * An observer interface that provides visibility into events associated with a network connection.
 */
public interface ConnectionObserver {

    /**
     * Callback when {@code size} bytes are read from the connection.
     *
     * @param size size of the data chunk read
     */
    void onDataRead(int size);

    /**
     * Callback when {@code size} bytes are written to the connection.
     *
     * @param size size of the data chunk written
     */
    void onDataWrite(int size);

    /**
     * Callback when previously written data is flushed to the connection.
     */
    void onFlush();

    /**
     * Callback when a security handshake is initiated.
     *
     * @return a new {@link SecurityHandshakeObserver} that provides visibility into security handshake events
     */
    SecurityHandshakeObserver onSecurityHandshake();

    /**
     * Callback when the connection is closed due to an {@link Throwable error}.
     *
     * @param error an occurred error
     */
    void connectionClosed(Throwable error);

    /**
     * Callback when the connection is closed.
     */
    void connectionClosed();

    /**
     * An observer interface that provides visibility into security handshake events.
     */
    interface SecurityHandshakeObserver {

        /**
         * Callback when the handshake is failed.
         *
         * @param cause the cause of handshake failure
         */
        void handshakeFailed(Throwable cause);

        /**
         * Callback when the handshake is complete successfully.
         *
         * @param sslSession the {@link SSLSession} for this connection
         */
        void handshakeComplete(SSLSession sslSession);
    }
}

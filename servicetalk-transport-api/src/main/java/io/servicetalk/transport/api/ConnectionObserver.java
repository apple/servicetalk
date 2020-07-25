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
     * Callback when a non-multiplexed connection is established and ready.
     *
     * @param info {@link ConnectionInfo} for the established connection
     * @return a new {@link NonMultiplexedObserver} that provides visibility into read and write events
     */
    NonMultiplexedObserver established(ConnectionInfo info);

    /**
     * Callback when a multiplexed connection is established and ready.
     *
     * @param info {@link ConnectionInfo} for the established connection
     * @return a new {@link MultiplexedObserver} that provides visibility into new streams
     */
    MultiplexedObserver establishedMultiplexed(ConnectionInfo info);

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

    /**
     * An observer interface that provides visibility into read and write events of a non-multiplexed connection.
     */
    interface NonMultiplexedObserver {

        /**
         * Callback when the connection starts reading a new message.
         *
         * @return {@link ReadObserver} that provides visibility into <strong>read</strong> events
         */
        ReadObserver onNewRead();

        /**
         * Callback when the connection starts writing a new message.
         *
         * @return {@link WriteObserver} that provides visibility into <strong>write</strong> events
         */
        WriteObserver onNewWrite();
    }

    /**
     * An observer interface that provides visibility into new streams created by a multiplexed connection.
     */
    interface MultiplexedObserver {

        /**
         * Callback when the connection creates a new stream.
         *
         * @return {@link StreamObserver} that provides visibility into stream events
         */
        StreamObserver onNewStream();
    }

    /**
     * An observer interface that provides visibility into stream events.
     */
    interface StreamObserver extends NonMultiplexedObserver {

        /**
         * Callback when the stream is closed due to an {@link Throwable error}.
         *
         * @param error an occurred error
         */
        void streamClosed(Throwable error);

        /**
         * Callback when the stream is closed.
         */
        void streamClosed();
    }

    /**
     * An observer interface that provides visibility into <strong>read</strong> events.
     */
    interface ReadObserver {

        /**
         * Callback when new items are requested to read.
         *
         * @param n number of requested items to read
         */
        void requestedToRead(long n);

        /**
         * Invokes when a new item is read.
         */
        void itemRead();

        /**
         * Callback when the read operation fails with an {@link Throwable error}.
         *
         * @param cause {@link Throwable} that terminated the read
         */
        void readFailed(Throwable cause);

        /**
         * Callback when the entire read operation completes successfully.
         */
        void readComplete();

        /**
         * Callback when the read operation is cancelled.
         */
        void readCancelled();
    }

    /**
     * An observer interface that provides visibility into <strong>write</strong> events.
     */
    interface WriteObserver {

        /**
         * Callback when new items are requested to write.
         *
         * @param n number of requested items to write
         */
        void requestedToWrite(long n);

        /**
         * Callback when an item is received and ready to be written.
         */
        void itemReceived();

        /**
         * Callback when flush operation is requested.
         */
        void onFlushRequest();

        /**
         * Callback when an item is written to the transport.
         */
        void itemWritten();

        /**
         * Callback when the write operation fails with an {@link Throwable error}.
         *
         * @param cause {@link Throwable} that terminated the write
         */
        void writeFailed(Throwable cause);

        /**
         * Callback when the entire write operation completes successfully.
         */
        void writeComplete();

        /**
         * Callback when the write operation is cancelled.
         */
        void writeCancelled();
    }
}

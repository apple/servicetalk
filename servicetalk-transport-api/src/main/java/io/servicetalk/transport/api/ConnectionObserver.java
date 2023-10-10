/*
 * Copyright © 2020-2023 Apple Inc. and the ServiceTalk project authors
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

import io.servicetalk.transport.api.NoopTransportObserver.NoopProxyConnectObserver;

import javax.annotation.Nullable;
import javax.net.ssl.SSLSession;

/**
 * An observer interface that provides visibility into events associated with a network connection.
 * <p>
 * Either {@link #connectionClosed()} or {@link #connectionClosed(Throwable)} will be invoked to signal when connection
 * is closed.
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
     * Callback when a transport handshake completes.
     * <p>
     * Transport protocols that require a handshake in order to connect. Example:
     * <a href="https://datatracker.ietf.org/doc/html/rfc793.html#section-3.4">TCP "three-way handshake"</a>.
     *
     * @deprecated Use {@link #onTransportHandshakeComplete(ConnectionInfo)}
     */
    @Deprecated
    default void onTransportHandshakeComplete() {   // FIXME: 0.43 - remove deprecated method
    }

    /**
     * Callback when a transport handshake completes.
     * <p>
     * Transport protocols that require a handshake in order to connect. Example:
     * <a href="https://datatracker.ietf.org/doc/html/rfc793.html#section-3.4">TCP "three-way handshake"</a>.
     *
     * @param info {@link ConnectionInfo} for the connection after transport handshake completes. Note that the
     * {@link ConnectionInfo#sslSession()} will always return {@code null} since it is called before the
     * {@link ConnectionObserver#onSecurityHandshake() security handshake} is performed (and as a result no SSL session
     * has been established). Also, {@link ConnectionInfo#protocol()} will return L4 (transport) protocol.
     * Finalized {@link ConnectionInfo} will be available via {@link #connectionEstablished(ConnectionInfo)} or
     * {@link #multiplexedConnectionEstablished(ConnectionInfo)} callbacks.
     */
    default void onTransportHandshakeComplete(ConnectionInfo info) {
        onTransportHandshakeComplete();
    }

    /**
     * Callback when a proxy connect is initiated.
     * <p>
     * For a typical connection, this callback is invoked after {@link #onTransportHandshakeComplete(ConnectionInfo)}.
     *
     * @param connectMsg a message sent to a proxy in request to establish a connection to the target server
     * @return a new {@link ProxyConnectObserver} that provides visibility into proxy connect events.
     */
    default ProxyConnectObserver onProxyConnect(Object connectMsg) {    // FIXME: 0.43 - consider removing default impl
        return NoopProxyConnectObserver.INSTANCE;
    }

    /**
     * Callback when a security handshake is initiated.
     * <p>
     * For a typical connection, this callback is invoked after {@link #onTransportHandshakeComplete(ConnectionInfo)}.
     * There are may be exceptions:
     * <ol>
     *     <li>For a TCP connection, when {@link ServiceTalkSocketOptions#TCP_FASTOPEN_CONNECT} option is configured and
     *     the Fast Open feature is supported by the OS, this callback may be invoked earlier. Note, even if the Fast
     *     Open is available and configured, it may not actually happen if the
     *     <a href="https://datatracker.ietf.org/doc/html/rfc7413#section-4.1">Fast Open Cookie</a> is {@code null} or
     *     rejected by the server.</li>
     *     <li>For a proxy connections, the handshake may happen after an observer returned by
     *     {@link #onProxyConnect(Object)} completes successfully.</li>
     * </ol>
     *
     * @return a new {@link SecurityHandshakeObserver} that provides visibility into security handshake events
     * @see <a href="https://datatracker.ietf.org/doc/html/rfc7413">RFC7413: TCP Fast Open</a>
     */
    SecurityHandshakeObserver onSecurityHandshake();

    /**
     * Callback when a non-multiplexed connection is established and ready.
     *
     * @param info {@link ConnectionInfo} for the established connection
     * @return a new {@link DataObserver} that provides visibility into read and write events
     */
    DataObserver connectionEstablished(ConnectionInfo info);

    /**
     * Callback when a multiplexed connection is established and ready.
     *
     * @param info {@link ConnectionInfo} for the established connection
     * @return a new {@link MultiplexedObserver} that provides visibility into new streams
     */
    MultiplexedObserver multiplexedConnectionEstablished(ConnectionInfo info);

    /**
     * Callback when a writable state of the connection changes.
     *
     * @param isWritable describes the current state of the connection: {@code true} when the I/O thread will perform
     * the requested write operation immediately. If {@code false}, write requests will be queued until the I/O thread
     * is ready to process the queued items and the transport will start applying backpressure.
     */
    default void connectionWritabilityChanged(boolean isWritable) { // FIXME: 0.43 - consider removing default impl
    }

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
     * An observer interface that provides visibility into proxy connect events for establishing a tunnel.
     * <p>
     * Either {@link #proxyConnectComplete(Object)} or {@link #proxyConnectFailed(Throwable)} will be invoked to signal
     * successful or failed connection via proxy tunnel.
     */
    interface ProxyConnectObserver {

        /**
         * Callback when the proxy connect attempt is failed.
         *
         * @param cause the cause of proxy connect failure
         */
        void proxyConnectFailed(Throwable cause);

        /**
         * Callback when the proxy connect attempt is complete successfully.
         *
         * @param responseMsg an object that represents a response message. The actual message type depends upon proxy
         * protocol implementation (e.g. <a href="https://en.wikipedia.org/wiki/HTTP_tunnel">HTTP Tunnel</a>,
         * <a href="https://en.wikipedia.org/wiki/SOCKS">SOCKS</a>, etc.)
         */
        void proxyConnectComplete(Object responseMsg);
    }

    /**
     * An observer interface that provides visibility into security handshake events.
     * <p>
     * Either {@link #handshakeComplete(SSLSession)} or {@link #handshakeFailed(Throwable)} will be invoked to signal
     * successful or failed completion of the handshake.
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
     * An observer interface that provides visibility into read and write events related to data flow.
     */
    interface DataObserver {

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
         * Callback when the connection requests a new stream.
         *
         * @return {@link StreamObserver} that provides visibility into stream events
         */
        StreamObserver onNewStream();
    }

    /**
     * An observer interface that provides visibility into stream events.
     * <p>
     * Either {@link #streamClosed()} or {@link #streamClosed(Throwable)} will be invoked to signal when a stream is
     * closed.
     */
    interface StreamObserver {

        /**
         * Callback when a {@code streamId} is assigned.
         * <p>
         * Stream identifier may be deferred until after the first write is made on a newly established stream.
         *
         * @param streamId assigned stream identifier
         */
        void streamIdAssigned(long streamId);   // Use long to comply with HTTP/3 requirements

        /**
         * Callback when the stream is established and ready to be used. It may or may not have an already assigned
         * {@code streamId} at the time.
         *
         * @return a new {@link DataObserver} that provides visibility into read and write events
         * @see #streamIdAssigned(long)
         */
        DataObserver streamEstablished();

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
     * <p>
     * Either {@link #readComplete()} or {@link #readFailed(Throwable)} will be invoked to signal when a read operation
     * terminates. {@link #readCancelled()} is also a terminal signal for the read operation, however it may be invoked
     * concurrently with {@link #readComplete()} or {@link #readFailed(Throwable)}.
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
         * <p>
         * Content of the read items should be inspected at the higher level API when these items are consumed.
         *
         * @deprecated Use {@link #itemRead(Object)}
         */
        @Deprecated
        default void itemRead() {
            // FIXME: 0.43 - remove deprecated method
        }

        /**
         * Invokes when a new item is read.
         *
         * @param item an item that was read
         */
        void itemRead(@Nullable Object item);

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
     * <p>
     * Either {@link #writeComplete()} or {@link #writeFailed(Throwable)} will be invoked to signal when a write
     * operation terminates. {@link #writeCancelled()} is also a terminal signal for the write operation, however it may
     * be invoked concurrently with {@link #writeComplete()} or {@link #writeFailed(Throwable)}.
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
         * <p>
         * Content of the received items should be inspected at the higher level API when these items are produced.
         *
         * @deprecated Use {{@link #itemReceived(Object)}}
         */
        @Deprecated
        default void itemReceived() {
            // FIXME: 0.43 - remove deprecated method
        }

        /**
         * Callback when an item is received and ready to be written.
         *
         * @param item received item
         */
        void itemReceived(@Nullable Object item);

        /**
         * Callback when flush operation is requested.
         */
        void onFlushRequest();

        /**
         * Callback when an item is written to the transport.
         * <p>
         * Content of the written items should be inspected at the higher level API when these items are produced.
         *
         * @deprecated Use {@link #itemWritten(Object)}
         */
        @Deprecated
        default void itemWritten() {
            // FIXME: 0.43 - remove deprecated method
        }

        /**
         * Callback when an item is serialized and written to the socket.
         *
         * @param item written item
         */
        void itemWritten(@Nullable Object item);

        /**
         * Callback when an item is flushed to the network. Items are flushed in order they have been written.
         */
        void itemFlushed();

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

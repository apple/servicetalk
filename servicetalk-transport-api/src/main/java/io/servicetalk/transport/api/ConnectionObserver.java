/*
 * Copyright © 2020-2025 Apple Inc. and the ServiceTalk project authors
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

import javax.annotation.Nullable;
import javax.net.ssl.SSLSession;

/**
 * An observer interface that provides visibility into events associated with a network connection.
 * <p>
 * Either {@link #connectionClosed()} or {@link #connectionClosed(Throwable)} will be invoked to signal when connection
 * is closed. The "closed" event is considered terminal and other callbacks after that can be safely discarded because
 * nothing happens on the network interface after closure.
 */
public interface ConnectionObserver {

    /**
     * Callback when {@code size} bytes are read from the connection.
     *
     * @param size size of the data chunk read
     */
    default void onDataRead(int size) {
    }

    /**
     * Callback when {@code size} bytes are written to the connection.
     *
     * @param size size of the data chunk written
     */
    default void onDataWrite(int size) {
    }

    /**
     * Callback when previously written data is flushed to the connection.
     */
    default void onFlush() {
    }

    /**
     * Callback when a transport handshake completes.
     * <p>
     * Transport protocols that require a handshake to connect. Example:
     * <a href="https://datatracker.ietf.org/doc/html/rfc793.html#section-3.4">TCP "three-way handshake"</a>.
     * Note that in the case of TCP on the server-side, this callback is invoked immediately because it can only be
     * notified about a new connection after the OS completes the handshake.
     * <p>
     * Transport implementations that do not have a concept of a "handshake" are not required to invoke this callback.
     *
     * @deprecated Use {@link #onTransportHandshakeComplete(ConnectionInfo)}
     */
    @Deprecated
    default void onTransportHandshakeComplete() {   // FIXME: 0.43 - remove deprecated method
    }

    /**
     * Callback when a transport handshake completes.
     * <p>
     * Transport protocols that require a handshake to connect. Example:
     * <a href="https://datatracker.ietf.org/doc/html/rfc793.html#section-3.4">TCP "three-way handshake"</a>.
     * Note that in the case of TCP on the server-side, this callback is invoked immediately because it can only be
     * notified about a new connection after the OS completes the handshake.
     * <p>
     * Transport implementations that do not have a concept of a "handshake" are not required to invoke this callback.
     *
     * @param info {@link ConnectionInfo} for the connection after the transport handshake completes. Note that the
     * {@link ConnectionInfo#sslSession()} will always return {@code null} since it is called before the
     * {@link ConnectionObserver#onSecurityHandshake(SslConfig) security handshake} is performed (and as a result
     * no SSL session has been established). Also, {@link ConnectionInfo#protocol()} will return the L4 (transport)
     * protocol. Finalized {@link ConnectionInfo} will be available via {@link #connectionEstablished(ConnectionInfo)}
     * or {@link #multiplexedConnectionEstablished(ConnectionInfo)} callbacks.
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
     * @return a new {@link ProxyConnectObserver} that provides visibility into proxy connect events
     */
    default ProxyConnectObserver onProxyConnect(Object connectMsg) {
        return NoopTransportObserver.NoopProxyConnectObserver.INSTANCE;
    }

    /**
     * Callback when a security handshake is initiated.
     * <p>
     * For a typical connection, this callback is invoked after {@link #onTransportHandshakeComplete(ConnectionInfo)}.
     * There are exceptions:
     * <ol>
     *     <li>For a TCP connection, when {@link ServiceTalkSocketOptions#TCP_FASTOPEN_CONNECT} option is configured and
     *     the Fast Open feature is supported by the OS, this callback may be invoked earlier. Note, even if the Fast
     *     Open is available and configured, it may not actually happen if the
     *     <a href="https://datatracker.ietf.org/doc/html/rfc7413#section-4.1">Fast Open Cookie</a> is {@code null} or
     *     rejected by the server.</li>
     *     <li>For proxy connections, the handshake may happen after an observer returned by
     *     {@link #onProxyConnect(Object)} completes successfully.</li>
     * </ol>
     *
     * @return a new {@link SecurityHandshakeObserver} that provides visibility into security handshake events
     * @see <a href="https://datatracker.ietf.org/doc/html/rfc7413">RFC7413: TCP Fast Open</a>
     * @deprecated use {@link #onSecurityHandshake(SslConfig)}
     */
    @Deprecated
    default SecurityHandshakeObserver onSecurityHandshake() {  // FIXME: 0.43 - remove deprecated method
        return NoopTransportObserver.NoopSecurityHandshakeObserver.INSTANCE;
    }

    /**
     * Callback when a security handshake is initiated.
     * <p>
     * For a typical connection, this callback is invoked after {@link #onTransportHandshakeComplete(ConnectionInfo)}.
     * There are exceptions:
     * <ol>
     *     <li>For a TCP connection, when {@link ServiceTalkSocketOptions#TCP_FASTOPEN_CONNECT} option is configured and
     *     the Fast Open feature is supported by the OS, this callback may be invoked earlier. Note, even if the Fast
     *     Open is available and configured, it may not actually happen if the
     *     <a href="https://datatracker.ietf.org/doc/html/rfc7413#section-4.1">Fast Open Cookie</a> is {@code null} or
     *     rejected by the server.</li>
     *     <li>For proxy connections, the handshake may happen after an observer returned by
     *     {@link #onProxyConnect(Object)} completes successfully.</li>
     * </ol>
     *
     * @param sslConfig the {@link SslConfig} used when performing the security handshake
     * @return a new {@link SecurityHandshakeObserver} that provides visibility into security handshake events
     * @see <a href="https://datatracker.ietf.org/doc/html/rfc7413">RFC7413: TCP Fast Open</a>
     */
    default SecurityHandshakeObserver onSecurityHandshake(SslConfig sslConfig) {
        return onSecurityHandshake();
    }

    /**
     * Callback when a non-multiplexed connection is established and ready to start reading/writing protocol messages.
     *
     * @param info {@link ConnectionInfo} for the established connection
     * @return a new {@link DataObserver} that provides visibility into read and write events
     */
    default DataObserver connectionEstablished(ConnectionInfo info) {
        return NoopTransportObserver.NoopDataObserver.INSTANCE;
    }

    /**
     * Callback when a multiplexed connection is established and ready to start creating streams that will read/write
     * protocol messages.
     *
     * @param info {@link ConnectionInfo} for the established connection
     * @return a new {@link MultiplexedObserver} that provides visibility into new streams
     */
    default MultiplexedObserver multiplexedConnectionEstablished(ConnectionInfo info) {
        return NoopTransportObserver.NoopMultiplexedObserver.INSTANCE;
    }

    /**
     * Callback when the writable state of the connection changes.
     *
     * @param isWritable describes the current state of the connection: {@code true} when the I/O thread will perform
     * the requested write operation immediately. If {@code false}, write requests will be queued until the I/O thread
     * is ready to process the queued items and the transport will start applying backpressure.
     */
    default void connectionWritabilityChanged(boolean isWritable) {
    }

    /**
     * Callback when the connection is closed due to an {@link Throwable error}.
     * <p>
     * Connections can be closed at any time, even before they are fully established.
     *
     * @param error the error that occurred
     */
    default void connectionClosed(Throwable error) {
    }

    /**
     * Callback when the connection is closed.
     * <p>
     * Connections can be closed at any time, even before they are fully established.
     */
    default void connectionClosed() {
    }

    /**
     * An observer interface that provides visibility into proxy connect events for establishing a tunnel.
     * <p>
     * Either {@link #proxyConnectComplete(Object)} or {@link #proxyConnectFailed(Throwable)} will be invoked to signal
     * successful or failed connection via a proxy tunnel.
     */
    interface ProxyConnectObserver {

        /**
         * Callback when the proxy connect attempt fails.
         *
         * @param cause the cause of the proxy connect failure
         */
        default void proxyConnectFailed(Throwable cause) {
        }

        /**
         * Callback when the proxy connect attempt completes successfully.
         *
         * @param responseMsg an object that represents a response message. The actual message type depends upon the
         * proxy protocol implementation (e.g. <a href="https://en.wikipedia.org/wiki/HTTP_tunnel">HTTP Tunnel</a>,
         * <a href="https://en.wikipedia.org/wiki/SOCKS">SOCKS</a>, etc.)
         */
        default void proxyConnectComplete(Object responseMsg) {
        }
    }

    /**
     * An observer interface that provides visibility into security handshake events.
     * <p>
     * Either {@link #handshakeComplete(SSLSession)} or {@link #handshakeFailed(Throwable)} will be invoked to signal
     * successful or failed completion of the handshake.
     */
    interface SecurityHandshakeObserver {

        /**
         * Callback when the handshake fails.
         *
         * @param cause the cause of the handshake failure
         */
        default void handshakeFailed(Throwable cause) {
        }

        /**
         * Callback when the handshake completes successfully.
         *
         * @param sslSession the {@link SSLSession} for this connection
         */
        default void handshakeComplete(SSLSession sslSession) {
        }
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
        default ReadObserver onNewRead() {
            return NoopTransportObserver.NoopReadObserver.INSTANCE;
        }

        /**
         * Callback when the connection starts writing a new message.
         *
         * @return {@link WriteObserver} that provides visibility into <strong>write</strong> events
         */
        default WriteObserver onNewWrite() {
            return NoopTransportObserver.NoopWriteObserver.INSTANCE;
        }
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
        default StreamObserver onNewStream() {
            return NoopTransportObserver.NoopStreamObserver.INSTANCE;
        }
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
         * The stream identifier may be deferred until after the first write is made on a newly established stream.
         *
         * @param streamId the assigned stream identifier
         */
        default void streamIdAssigned(long streamId) {   // Use long to comply with HTTP/3 requirements
        }

        /**
         * Callback when the stream is established and ready to be used.
         * <p>
         * It may or may not have an already assigned {@code streamId} at this time.
         *
         * @return a new {@link DataObserver} that provides visibility into read and write events
         * @see #streamIdAssigned(long)
         */
        default DataObserver streamEstablished() {
            return NoopTransportObserver.NoopDataObserver.INSTANCE;
        }

        /**
         * Callback when the stream is closed due to an {@link Throwable error}.
         *
         * @param error the error that occurred
         */
        default void streamClosed(Throwable error) {
        }

        /**
         * Callback when the stream is closed.
         */
        default void streamClosed() {
        }
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
         * Callback when new items are requested to be read.
         *
         * @param n number of requested items to read
         */
        default void requestedToRead(long n) {
        }

        /**
         * Callback when a new item is read.
         * <p>
         * Content of the read items should be inspected at the higher level API when these items are consumed.
         *
         * @deprecated Use {@link #itemRead(Object)}
         */
        @Deprecated
        default void itemRead() {   // FIXME: 0.43 - remove deprecated method
        }

        /**
         * Callback when a new item is read.
         *
         * @param item the item that was read
         */
        default void itemRead(@Nullable Object item) {
        }

        /**
         * Callback when the read operation fails with an {@link Throwable error}.
         *
         * @param cause {@link Throwable} that terminated the read
         */
        default void readFailed(Throwable cause) {
        }

        /**
         * Callback when the entire read operation completes successfully.
         */
        default void readComplete() {
        }

        /**
         * Callback when the read operation is cancelled.
         */
        default void readCancelled() {
        }
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
         * Callback when new items are requested to be written.
         *
         * @param n number of requested items to write
         */
        default void requestedToWrite(long n) {
        }

        /**
         * Callback when an item is received and ready to be written.
         * <p>
         * Content of the received items should be inspected at the higher level API when these items are produced.
         *
         * @deprecated Use {{@link #itemReceived(Object)}}
         */
        @Deprecated
        default void itemReceived() {   // FIXME: 0.43 - remove deprecated method
        }

        /**
         * Callback when an item is received and ready to be written.
         *
         * @param item the received item
         */
        default void itemReceived(@Nullable Object item) {
        }

        /**
         * Callback when a flush operation is requested.
         */
        default void onFlushRequest() {
        }

        /**
         * Callback when an item is written to the transport.
         * <p>
         * Content of the written items should be inspected at the higher level API when these items are produced.
         *
         * @deprecated Use {@link #itemWritten(Object)}
         */
        @Deprecated
        default void itemWritten() {    // FIXME: 0.43 - remove deprecated method
        }

        /**
         * Callback when an item is serialized and written to the socket.
         *
         * @param item the written item
         */
        default void itemWritten(@Nullable Object item) {
        }

        /**
         * Callback when an item is flushed to the network. Items are flushed in the order they have been written.
         */
        default void itemFlushed() {
        }

        /**
         * Callback when the write operation fails with an {@link Throwable error}.
         *
         * @param cause {@link Throwable} that terminated the write
         */
        default void writeFailed(Throwable cause) {
        }

        /**
         * Callback when the entire write operation completes successfully.
         */
        default void writeComplete() {
        }

        /**
         * Callback when the write operation is cancelled.
         */
        default void writeCancelled() {
        }
    }
}

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

import io.servicetalk.transport.api.ConnectionObserver.DataObserver;
import io.servicetalk.transport.api.ConnectionObserver.MultiplexedObserver;
import io.servicetalk.transport.api.ConnectionObserver.ReadObserver;
import io.servicetalk.transport.api.ConnectionObserver.SecurityHandshakeObserver;
import io.servicetalk.transport.api.ConnectionObserver.StreamObserver;
import io.servicetalk.transport.api.ConnectionObserver.WriteObserver;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.net.ssl.SSLSession;

public final class LoggingTransportObserver implements TransportObserver, ConnectionObserver, SecurityHandshakeObserver,
                                                       DataObserver, MultiplexedObserver, StreamObserver,
                                                       ReadObserver, WriteObserver {

    private static final Logger LOGGER = LoggerFactory.getLogger(LoggingTransportObserver.class);

    private volatile String connectionInfo = "-";

    @Override
    public ConnectionObserver onNewConnection() {
        LOGGER.info("onNewConnection");
        return this;
    }

    @Override
    public void onDataRead(final int size) {
        LOGGER.info("{} onDataRead({})", connectionInfo, size);
    }

    @Override
    public void onDataWrite(final int size) {
        LOGGER.info("{} onDataWrite({})", connectionInfo, size);
    }

    @Override
    public void onFlush() {
        LOGGER.info("{} onFlush", connectionInfo);
    }

    @Override
    public SecurityHandshakeObserver onSecurityHandshake() {
        LOGGER.info("onSecurityHandshake");
        return this;
    }

    @Override
    public DataObserver connectionEstablished(final ConnectionInfo info) {
        LOGGER.info("connectionEstablished({})", info);
        connectionInfo = info.toString();
        return this;
    }

    @Override
    public MultiplexedObserver multiplexedConnectionEstablished(final ConnectionInfo info) {
        LOGGER.info("multiplexedConnectionEstablished({})", info);
        return this;
    }

    @Override
    public void connectionClosed(final Throwable error) {
        LOGGER.error("{} connectionClosed:", connectionInfo, error);
    }

    @Override
    public void connectionClosed() {
        LOGGER.info("{} connectionClosed", connectionInfo);
    }

    @Override
    public void handshakeFailed(final Throwable cause) {
        LOGGER.error("handshakeFailed:", cause);
    }

    @Override
    public void handshakeComplete(final SSLSession sslSession) {
        LOGGER.info("handshakeComplete({})", sslSession);
    }

    @Override
    public StreamObserver onNewStream() {
        LOGGER.info("onNewStream");
        return this;
    }

    @Override
    public ReadObserver onNewRead() {
        LOGGER.info("{} onNewRead", connectionInfo);
        return this;
    }

    @Override
    public WriteObserver onNewWrite() {
        LOGGER.info("{} onNewWrite", connectionInfo);
        return this;
    }

    @Override
    public DataObserver streamEstablished() {
        LOGGER.info("streamEstablished");
        return this;
    }

    @Override
    public void streamClosed(final Throwable error) {
        LOGGER.error("streamClosed:", error);
    }

    @Override
    public void streamClosed() {
        LOGGER.info("streamClosed");
    }

    @Override
    public void requestedToRead(final long n) {
        LOGGER.info("{} requestedToRead({})", connectionInfo, n);
    }

    @Override
    public void itemRead() {
        LOGGER.info("{} itemRead", connectionInfo);
    }

    @Override
    public void readFailed(final Throwable cause) {
        LOGGER.error("{} readFailed:", connectionInfo, cause);
    }

    @Override
    public void readComplete() {
        LOGGER.info("{} readComplete", connectionInfo);
    }

    @Override
    public void readCancelled() {
        LOGGER.info("{} readCancelled", connectionInfo);
    }

    @Override
    public void requestedToWrite(final long n) {
        LOGGER.info("{} requestedToWrite({})", connectionInfo, n);
    }

    @Override
    public void itemReceived() {
        LOGGER.info("{} itemReceived", connectionInfo);
    }

    @Override
    public void onFlushRequest() {
        LOGGER.info("{} onFlushRequest", connectionInfo);
    }

    @Override
    public void itemWritten() {
        LOGGER.info("{} itemWritten", connectionInfo);
    }

    @Override
    public void writeFailed(final Throwable cause) {
        LOGGER.error("{} writeFailed:", connectionInfo, cause);
    }

    @Override
    public void writeComplete() {
        LOGGER.info("{} writeComplete", connectionInfo);
    }

    @Override
    public void writeCancelled() {
        LOGGER.info("{} writeCancelled", connectionInfo);
    }
}

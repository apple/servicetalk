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
package io.servicetalk.transport.netty.internal;

import io.servicetalk.transport.api.ConnectionInfo;
import io.servicetalk.transport.api.ConnectionObserver;
import io.servicetalk.transport.api.ConnectionObserver.DataObserver;
import io.servicetalk.transport.api.ConnectionObserver.MultiplexedObserver;
import io.servicetalk.transport.api.ConnectionObserver.ReadObserver;
import io.servicetalk.transport.api.ConnectionObserver.SecurityHandshakeObserver;
import io.servicetalk.transport.api.ConnectionObserver.StreamObserver;
import io.servicetalk.transport.api.ConnectionObserver.WriteObserver;
import io.servicetalk.transport.api.TransportObserver;

import javax.net.ssl.SSLSession;

/**
 * Noop version of {@link TransportObserver}.
 */
public final class NoopTransportObserver implements TransportObserver {

    public static final TransportObserver INSTANCE = new NoopTransportObserver();

    private NoopTransportObserver() {
        // Singleton
    }

    @Override
    public ConnectionObserver onNewConnection() {
        return NoopConnectionObserver.INSTANCE;
    }

    /**
     * Noop version of {@link ConnectionObserver}.
     */
    public static final class NoopConnectionObserver implements ConnectionObserver {

        public static final ConnectionObserver INSTANCE = new NoopConnectionObserver();

        private NoopConnectionObserver() {
            // Singleton
        }

        @Override
        public void onDataRead(final int size) {
        }

        @Override
        public void onDataWrite(final int size) {
        }

        @Override
        public void onFlush() {
        }

        @Override
        public void onTransportHandshakeComplete() {
        }

        @Override
        public SecurityHandshakeObserver onSecurityHandshake() {
            return NoopSecurityHandshakeObserver.INSTANCE;
        }

        @Override
        public DataObserver connectionEstablished(final ConnectionInfo info) {
            return NoopDataObserver.INSTANCE;
        }

        @Override
        public MultiplexedObserver multiplexedConnectionEstablished(final ConnectionInfo info) {
            return NoopMultiplexedObserver.INSTANCE;
        }

        @Override
        public void connectionClosed(final Throwable error) {
        }

        @Override
        public void connectionClosed() {
        }
    }

    /**
     * Noop version of {@link SecurityHandshakeObserver}.
     */
    public static final class NoopSecurityHandshakeObserver implements SecurityHandshakeObserver {

        public static final SecurityHandshakeObserver INSTANCE = new NoopSecurityHandshakeObserver();

        private NoopSecurityHandshakeObserver() {
            // Singleton
        }

        @Override
        public void handshakeFailed(final Throwable cause) {
        }

        @Override
        public void handshakeComplete(final SSLSession sslSession) {
        }
    }

    /**
     * Noop version of {@link DataObserver}.
     */
    public static final class NoopDataObserver implements DataObserver {

        public static final DataObserver INSTANCE = new NoopDataObserver();

        private NoopDataObserver() {
            // Singleton
        }

        @Override
        public ReadObserver onNewRead() {
            return NoopReadObserver.INSTANCE;
        }

        @Override
        public WriteObserver onNewWrite() {
            return NoopWriteObserver.INSTANCE;
        }
    }

    /**
     * Noop version of {@link MultiplexedObserver}.
     */
    public static final class NoopMultiplexedObserver implements MultiplexedObserver {

        public static final MultiplexedObserver INSTANCE = new NoopMultiplexedObserver();

        private NoopMultiplexedObserver() {
            // Singleton
        }

        @Override
        public StreamObserver onNewStream() {
            return NoopStreamObserver.INSTANCE;
        }
    }

    /**
     * Noop version of {@link StreamObserver}.
     */
    public static final class NoopStreamObserver implements StreamObserver {

        public static final StreamObserver INSTANCE = new NoopStreamObserver();

        private NoopStreamObserver() {
            // Singleton
        }

        @Override
        public void streamIdAssigned(final long streamId) {
        }

        @Override
        public DataObserver streamEstablished() {
            return NoopDataObserver.INSTANCE;
        }

        @Override
        public void streamClosed(final Throwable error) {
        }

        @Override
        public void streamClosed() {
        }
    }

    /**
     * Noop version of {@link ReadObserver}.
     */
    public static final class NoopReadObserver implements ReadObserver {

        public static final ReadObserver INSTANCE = new NoopReadObserver();

        private NoopReadObserver() {
            // Singleton
        }

        @Override
        public void requestedToRead(final long n) {
        }

        @Override
        public void itemRead() {
        }

        @Override
        public void readFailed(final Throwable cause) {
        }

        @Override
        public void readComplete() {
        }

        @Override
        public void readCancelled() {
        }
    }

    /**
     * Noop version of {@link WriteObserver}.
     */
    public static final class NoopWriteObserver implements WriteObserver {

        public static final WriteObserver INSTANCE = new NoopWriteObserver();

        private NoopWriteObserver() {
            // Singleton
        }

        @Override
        public void requestedToWrite(final long n) {
        }

        @Override
        public void itemReceived() {
        }

        @Override
        public void onFlushRequest() {
        }

        @Override
        public void itemWritten() {
        }

        @Override
        public void writeFailed(final Throwable cause) {
        }

        @Override
        public void writeComplete() {
        }

        @Override
        public void writeCancelled() {
        }
    }
}

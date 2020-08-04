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
package io.servicetalk.client.api;

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
 * Combines two {@link TransportObserver}s into a single {@link TransportObserver}.
 */
final class BiTransportObserver implements TransportObserver {

    private final TransportObserver first;
    private final TransportObserver second;

    /**
     * Creates a new instance.
     *
     * @param first the {@link TransportObserver} that will receive events first
     * @param second the {@link TransportObserver} that will receive events second
     */
    BiTransportObserver(final TransportObserver first, final TransportObserver second) {
        this.first = first;
        this.second = second;
    }

    @Override
    public ConnectionObserver onNewConnection() {
        return new BiConnectionObserver(first.onNewConnection(), second.onNewConnection());
    }

    private static final class BiConnectionObserver implements ConnectionObserver {

        private final ConnectionObserver first;
        private final ConnectionObserver second;

        private BiConnectionObserver(final ConnectionObserver first, final ConnectionObserver second) {
            this.first = first;
            this.second = second;
        }

        @Override
        public void onDataRead(final int size) {
            try {
                first.onDataRead(size);
            } finally {
                second.onDataRead(size);
            }
        }

        @Override
        public void onDataWrite(final int size) {
            try {
                first.onDataWrite(size);
            } finally {
                second.onDataWrite(size);
            }
        }

        @Override
        public void onFlush() {
            try {
                first.onFlush();
            } finally {
                second.onFlush();
            }
        }

        @Override
        public SecurityHandshakeObserver onSecurityHandshake() {
            return new BiSecurityHandshakeObserver(first.onSecurityHandshake(), second.onSecurityHandshake());
        }

        @Override
        public DataObserver established(final ConnectionInfo info) {
            return new BiDataObserver(first.established(info), second.established(info));
        }

        @Override
        public MultiplexedObserver establishedMultiplexed(final ConnectionInfo info) {
            return new BiMultiplexedObserver(first.establishedMultiplexed(info), second.establishedMultiplexed(info));
        }

        @Override
        public void connectionClosed(final Throwable error) {
            try {
                first.connectionClosed(error);
            } finally {
                second.connectionClosed(error);
            }
        }

        @Override
        public void connectionClosed() {
            try {
                first.connectionClosed();
            } finally {
                second.connectionClosed();
            }
        }
    }

    private static final class BiSecurityHandshakeObserver implements SecurityHandshakeObserver {

        private final SecurityHandshakeObserver first;
        private final SecurityHandshakeObserver second;

        private BiSecurityHandshakeObserver(final SecurityHandshakeObserver first,
                                            final SecurityHandshakeObserver second) {
            this.first = first;
            this.second = second;
        }

        @Override
        public void handshakeFailed(final Throwable cause) {
            try {
                first.handshakeFailed(cause);
            } finally {
                second.handshakeFailed(cause);
            }
        }

        @Override
        public void handshakeComplete(final SSLSession sslSession) {
            try {
                first.handshakeComplete(sslSession);
            } finally {
                second.handshakeComplete(sslSession);
            }
        }
    }

    private static class BiDataObserver implements DataObserver {

        private final DataObserver first;
        private final DataObserver second;

        protected BiDataObserver(final DataObserver first, final DataObserver second) {
            this.first = first;
            this.second = second;
        }

        @Override
        public ReadObserver onNewRead() {
            return new BiReadObserver(first.onNewRead(), second.onNewRead());
        }

        @Override
        public WriteObserver onNewWrite() {
            return new BiWriteObserver(first.onNewWrite(), second.onNewWrite());
        }
    }

    private static final class BiMultiplexedObserver implements MultiplexedObserver {

        private final MultiplexedObserver first;
        private final MultiplexedObserver second;

        private BiMultiplexedObserver(final MultiplexedObserver first, final MultiplexedObserver second) {
            this.first = first;
            this.second = second;
        }

        @Override
        public StreamObserver onNewStream() {
            return new BiStreamObserver(first.onNewStream(), second.onNewStream());
        }
    }

    private static final class BiStreamObserver extends BiDataObserver implements StreamObserver {

        private final StreamObserver first;
        private final StreamObserver second;

        private BiStreamObserver(final StreamObserver first, final StreamObserver second) {
            super(first, second);
            this.first = first;
            this.second = second;
        }

        @Override
        public void streamClosed(final Throwable error) {
            try {
                first.streamClosed(error);
            } finally {
                second.streamClosed(error);
            }
        }

        @Override
        public void streamClosed() {
            try {
                first.streamClosed();
            } finally {
                second.streamClosed();
            }
        }
    }

    private static final class BiReadObserver implements ReadObserver {

        private final ReadObserver first;
        private final ReadObserver second;

        private BiReadObserver(final ReadObserver first, final ReadObserver second) {
            this.first = first;
            this.second = second;
        }

        @Override
        public void requestedToRead(final long n) {
            try {
                first.requestedToRead(n);
            } finally {
                second.requestedToRead(n);
            }
        }

        @Override
        public void itemRead() {
            try {
                first.itemRead();
            } finally {
                second.itemRead();
            }
        }

        @Override
        public void readFailed(final Throwable cause) {
            try {
                first.readFailed(cause);
            } finally {
                second.readFailed(cause);
            }
        }

        @Override
        public void readComplete() {
            try {
                first.readComplete();
            } finally {
                second.readComplete();
            }
        }

        @Override
        public void readCancelled() {
            try {
                first.readCancelled();
            } finally {
                second.readCancelled();
            }
        }
    }

    private static final class BiWriteObserver implements WriteObserver {

        private final WriteObserver first;
        private final WriteObserver second;

        private BiWriteObserver(final WriteObserver first, final WriteObserver second) {
            this.first = first;
            this.second = second;
        }

        @Override
        public void requestedToWrite(final long n) {
            try {
                first.requestedToWrite(n);
            } finally {
                second.requestedToWrite(n);
            }
        }

        @Override
        public void itemReceived() {
            try {
                first.itemReceived();
            } finally {
                second.itemReceived();
            }
        }

        @Override
        public void onFlushRequest() {
            try {
                first.onFlushRequest();
            } finally {
                second.onFlushRequest();
            }
        }

        @Override
        public void itemWritten() {
            try {
                first.itemWritten();
            } finally {
                second.itemWritten();
            }
        }

        @Override
        public void writeFailed(final Throwable cause) {
            try {
                first.writeFailed(cause);
            } finally {
                second.writeFailed(cause);
            }
        }

        @Override
        public void writeComplete() {
            try {
                first.writeComplete();
            } finally {
                second.writeComplete();
            }
        }

        @Override
        public void writeCancelled() {
            try {
                first.writeCancelled();
            } finally {
                second.writeCancelled();
            }
        }
    }
}

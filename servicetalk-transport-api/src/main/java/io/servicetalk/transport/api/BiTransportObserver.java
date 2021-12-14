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

import javax.annotation.Nullable;
import javax.net.ssl.SSLSession;

import static io.servicetalk.transport.api.TransportObservers.asSafeObserver;

final class BiTransportObserver implements TransportObserver {

    private final TransportObserver first;
    private final TransportObserver second;

    BiTransportObserver(final TransportObserver first, final TransportObserver second) {
        this.first = asSafeObserver(first);
        this.second = asSafeObserver(second);
    }

    @Override
    public ConnectionObserver onNewConnection() {
        return new BiConnectionObserver(first.onNewConnection(), second.onNewConnection());
    }

    @Override
    public ConnectionObserver onNewConnection(@Nullable final Object localAddress, final Object remoteAddress) {
        return new BiConnectionObserver(first.onNewConnection(localAddress, remoteAddress),
                second.onNewConnection(localAddress, remoteAddress));
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
            first.onDataRead(size);
            second.onDataRead(size);
        }

        @Override
        public void onDataWrite(final int size) {
            first.onDataWrite(size);
            second.onDataWrite(size);
        }

        @Override
        public void onFlush() {
            first.onFlush();
            second.onFlush();
        }

        @Override
        public void onTransportHandshakeComplete() {
            first.onTransportHandshakeComplete();
            second.onTransportHandshakeComplete();
        }

        @Override
        public SecurityHandshakeObserver onSecurityHandshake() {
            return new BiSecurityHandshakeObserver(first.onSecurityHandshake(), second.onSecurityHandshake());
        }

        @Override
        public DataObserver connectionEstablished(final ConnectionInfo info) {
            return new BiDataObserver(first.connectionEstablished(info), second.connectionEstablished(info));
        }

        @Override
        public MultiplexedObserver multiplexedConnectionEstablished(final ConnectionInfo info) {
            return new BiMultiplexedObserver(first.multiplexedConnectionEstablished(info),
                    second.multiplexedConnectionEstablished(info));
        }

        @Override
        public void connectionClosed(final Throwable error) {
            first.connectionClosed(error);
            second.connectionClosed(error);
        }

        @Override
        public void connectionClosed() {
            first.connectionClosed();
            second.connectionClosed();
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
            first.handshakeFailed(cause);
            second.handshakeFailed(cause);
        }

        @Override
        public void handshakeComplete(final SSLSession sslSession) {
            first.handshakeComplete(sslSession);
            second.handshakeComplete(sslSession);
        }
    }

    private static final class BiDataObserver implements DataObserver {

        private final DataObserver first;
        private final DataObserver second;

        private BiDataObserver(final DataObserver first, final DataObserver second) {
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

    private static final class BiStreamObserver implements StreamObserver {

        private final StreamObserver first;
        private final StreamObserver second;

        private BiStreamObserver(final StreamObserver first, final StreamObserver second) {
            this.first = first;
            this.second = second;
        }

        @Override
        public void streamIdAssigned(final long streamId) {
            first.streamIdAssigned(streamId);
            second.streamIdAssigned(streamId);
        }

        @Override
        public DataObserver streamEstablished() {
            return new BiDataObserver(first.streamEstablished(), second.streamEstablished());
        }

        @Override
        public void streamClosed(final Throwable error) {
            first.streamClosed(error);
            second.streamClosed(error);
        }

        @Override
        public void streamClosed() {
            first.streamClosed();
            second.streamClosed();
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
            first.requestedToRead(n);
            second.requestedToRead(n);
        }

        @Override
        public void itemRead(@Nullable final Object item) {
            first.itemRead(item);
            second.itemRead(item);
        }

        @Override
        public void itemRead(@Nullable final Object item) {
            first.itemRead(item);
            second.itemRead(item);
        }

        @Override
        public void readFailed(final Throwable cause) {
            first.readFailed(cause);
            second.readFailed(cause);
        }

        @Override
        public void readComplete() {
            first.readComplete();
            second.readComplete();
        }

        @Override
        public void readCancelled() {
            first.readCancelled();
            second.readCancelled();
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
            first.requestedToWrite(n);
            second.requestedToWrite(n);
        }

        @Override
        public void itemReceived(@Nullable final Object item) {
            first.itemReceived(item);
            second.itemReceived(item);
        }

        @Override
        public void itemReceived(@Nullable final Object item) {
            first.itemReceived(item);
            second.itemReceived(item);
        }

        @Override
        public void onFlushRequest() {
            first.onFlushRequest();
            second.onFlushRequest();
        }

        @Override
        public void itemWritten(@Nullable final Object item) {
            first.itemWritten(item);
            second.itemWritten(item);
        }

        @Override
        public void itemFlushed() {
            first.itemFlushed();
            second.itemFlushed();
        }

        @Override
        public void itemWritten(@Nullable final Object item) {
            first.itemWritten(item);
            second.itemWritten(item);
        }

        @Override
        public void itemFlushed() {
            first.itemFlushed();
            second.itemFlushed();
        }

        @Override
        public void writeFailed(final Throwable cause) {
            first.writeFailed(cause);
            second.writeFailed(cause);
        }

        @Override
        public void writeComplete() {
            first.writeComplete();
            second.writeComplete();
        }

        @Override
        public void writeCancelled() {
            first.writeCancelled();
            second.writeCancelled();
        }
    }
}

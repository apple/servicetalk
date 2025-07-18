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
import io.servicetalk.transport.api.ConnectionObserver.ProxyConnectObserver;
import io.servicetalk.transport.api.ConnectionObserver.ReadObserver;
import io.servicetalk.transport.api.ConnectionObserver.SecurityHandshakeObserver;
import io.servicetalk.transport.api.ConnectionObserver.StreamObserver;
import io.servicetalk.transport.api.ConnectionObserver.WriteObserver;
import io.servicetalk.transport.api.NoopTransportObserver.NoopConnectionObserver;
import io.servicetalk.transport.api.NoopTransportObserver.NoopDataObserver;
import io.servicetalk.transport.api.NoopTransportObserver.NoopMultiplexedObserver;
import io.servicetalk.transport.api.NoopTransportObserver.NoopProxyConnectObserver;
import io.servicetalk.transport.api.NoopTransportObserver.NoopReadObserver;
import io.servicetalk.transport.api.NoopTransportObserver.NoopSecurityHandshakeObserver;
import io.servicetalk.transport.api.NoopTransportObserver.NoopStreamObserver;
import io.servicetalk.transport.api.NoopTransportObserver.NoopWriteObserver;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.function.Supplier;
import java.util.function.UnaryOperator;
import javax.annotation.Nullable;
import javax.net.ssl.SSLSession;

import static io.servicetalk.utils.internal.ThrowableUtils.addSuppressed;
import static java.util.Objects.requireNonNull;

/**
 * {@link TransportObserver} wrapper that catches and logs all exceptions.
 */
final class CatchAllTransportObserver implements TransportObserver {

    private static final Logger LOGGER = LoggerFactory.getLogger(CatchAllTransportObserver.class);

    private final TransportObserver observer;

    CatchAllTransportObserver(final TransportObserver observer) {
        this.observer = requireNonNull(observer);
    }

    @Override
    public ConnectionObserver onNewConnection(@Nullable final Object localAddress, final Object remoteAddress) {
        return safeReport(() -> observer.onNewConnection(localAddress, remoteAddress), observer, "new connection",
                CatchAllConnectionObserver::new, NoopConnectionObserver.INSTANCE);
    }

    private static final class CatchAllConnectionObserver implements ConnectionObserver {

        private final ConnectionObserver observer;

        private CatchAllConnectionObserver(final ConnectionObserver observer) {
            this.observer = observer;
        }

        @Override
        public void onDataRead(final int size) {
            safeReport(() -> observer.onDataRead(size), observer, "data read");
        }

        @Override
        public void onDataWrite(final int size) {
            safeReport(() -> observer.onDataWrite(size), observer, "data write");
        }

        @Override
        public void onFlush() {
            safeReport(observer::onFlush, observer, "flush");
        }

        @Override
        @SuppressWarnings("deprecation")
        public void onTransportHandshakeComplete() {
            safeReport(observer::onTransportHandshakeComplete, observer, "transport handshake complete");
        }

        @Override
        public void onTransportHandshakeComplete(final ConnectionInfo info) {
            safeReport(() -> observer.onTransportHandshakeComplete(info), observer, "transport handshake complete");
        }

        @Override
        public ProxyConnectObserver onProxyConnect(final Object connectMsg) {
            return safeReport(() -> observer.onProxyConnect(connectMsg), observer, "proxy connect",
                    CatchAllProxyConnectObserver::new, NoopProxyConnectObserver.INSTANCE);
        }

        @Override
        @SuppressWarnings("deprecation")
        public SecurityHandshakeObserver onSecurityHandshake() {
            return safeReport(observer::onSecurityHandshake, observer, "security handshake",
                    CatchAllSecurityHandshakeObserver::new, NoopSecurityHandshakeObserver.INSTANCE);
        }

        @Override
        public SecurityHandshakeObserver onSecurityHandshake(final SslConfig sslConfig) {
            return safeReport(() -> observer.onSecurityHandshake(sslConfig), observer, "security handshake",
                    CatchAllSecurityHandshakeObserver::new, NoopSecurityHandshakeObserver.INSTANCE);
        }

        @Override
        public DataObserver connectionEstablished(final ConnectionInfo info) {
            return safeReport(() -> observer.connectionEstablished(info), observer, "connection established",
                    CatchAllDataObserver::new, NoopDataObserver.INSTANCE);
        }

        @Override
        public MultiplexedObserver multiplexedConnectionEstablished(final ConnectionInfo info) {
            return safeReport(() -> observer.multiplexedConnectionEstablished(info), observer,
                    "multiplexed connection established",
                    CatchAllMultiplexedObserver::new, NoopMultiplexedObserver.INSTANCE);
        }

        @Override
        public void connectionWritabilityChanged(final boolean isWritable) {
            safeReport(() -> observer.connectionWritabilityChanged(isWritable), observer,
                    "connection writability changed");
        }

        @Override
        public void connectionClosed(final Throwable error) {
            safeReport(() -> observer.connectionClosed(error), observer, "connection closed", error);
        }

        @Override
        public void connectionClosed() {
            safeReport(observer::connectionClosed, observer, "connection closed");
        }
    }

    private static final class CatchAllProxyConnectObserver implements ProxyConnectObserver {

        private final ProxyConnectObserver observer;

        private CatchAllProxyConnectObserver(final ProxyConnectObserver observer) {
            this.observer = observer;
        }

        @Override
        public void proxyConnectFailed(final Throwable cause) {
            safeReport(() -> observer.proxyConnectFailed(cause), observer, "proxy connect failed", cause);
        }

        @Override
        public void proxyConnectComplete(final Object responseMsg) {
            safeReport(() -> observer.proxyConnectComplete(responseMsg), observer, "proxy connect complete");
        }
    }

    private static final class CatchAllSecurityHandshakeObserver implements SecurityHandshakeObserver {

        private final SecurityHandshakeObserver observer;

        private CatchAllSecurityHandshakeObserver(final SecurityHandshakeObserver observer) {
            this.observer = observer;
        }

        @Override
        public void handshakeFailed(final Throwable cause) {
            safeReport(() -> observer.handshakeFailed(cause), observer, "handshake failed", cause);
        }

        @Override
        public void handshakeComplete(final SSLSession sslSession) {
            safeReport(() -> observer.handshakeComplete(sslSession), observer, "handshake complete");
        }
    }

    private static final class CatchAllDataObserver implements DataObserver {

        private final DataObserver observer;

        private CatchAllDataObserver(final DataObserver observer) {
            this.observer = observer;
        }

        @Override
        public ReadObserver onNewRead() {
            return safeReport(observer::onNewRead, observer, "new read",
                    CatchAllReadObserver::new, NoopReadObserver.INSTANCE);
        }

        @Override
        public WriteObserver onNewWrite() {
            return safeReport(observer::onNewWrite, observer, "new read",
                    CatchAllWriteObserver::new, NoopWriteObserver.INSTANCE);
        }
    }

    private static final class CatchAllMultiplexedObserver implements MultiplexedObserver {

        private final MultiplexedObserver observer;

        private CatchAllMultiplexedObserver(final MultiplexedObserver observer) {
            this.observer = observer;
        }

        @Override
        public StreamObserver onNewStream() {
            return safeReport(observer::onNewStream, observer, "connection established",
                    CatchAllStreamObserver::new, NoopStreamObserver.INSTANCE);
        }
    }

    private static final class CatchAllStreamObserver implements StreamObserver {

        private final StreamObserver observer;

        private CatchAllStreamObserver(final StreamObserver observer) {
            this.observer = observer;
        }

        @Override
        public void streamIdAssigned(final long streamId) {
            safeReport(() -> observer.streamIdAssigned(streamId), observer, "streamId assigned");
        }

        @Override
        public DataObserver streamEstablished() {
            return safeReport(observer::streamEstablished, observer, "stream established",
                    CatchAllDataObserver::new, NoopDataObserver.INSTANCE);
        }

        @Override
        public void streamClosed(final Throwable error) {
            safeReport(() -> observer.streamClosed(error), observer, "stream closed", error);
        }

        @Override
        public void streamClosed() {
            safeReport(observer::streamClosed, observer, "stream closed");
        }
    }

    private static final class CatchAllReadObserver implements ReadObserver {

        private final ReadObserver observer;

        private CatchAllReadObserver(final ReadObserver observer) {
            this.observer = observer;
        }

        @Override
        public void requestedToRead(final long n) {
            safeReport(() -> observer.requestedToRead(n), observer, "requested to read");
        }

        @Override
        @SuppressWarnings("deprecation")
        public void itemRead() {
            safeReport(observer::itemRead, observer, "item read");
        }

        @Override
        public void itemRead(@Nullable final Object item) {
            safeReport(() -> observer.itemRead(item), observer, "item read");
        }

        @Override
        public void readFailed(final Throwable cause) {
            safeReport(() -> observer.readFailed(cause), observer, "read failed", cause);
        }

        @Override
        public void readComplete() {
            safeReport(observer::readComplete, observer, "read complete");
        }

        @Override
        public void readCancelled() {
            safeReport(observer::readCancelled, observer, "read cancelled");
        }
    }

    private static final class CatchAllWriteObserver implements WriteObserver {

        private final WriteObserver observer;

        private CatchAllWriteObserver(final WriteObserver observer) {
            this.observer = observer;
        }

        @Override
        public void requestedToWrite(final long n) {
            safeReport(() -> observer.requestedToWrite(n), observer, "requested to write");
        }

        @Override
        @SuppressWarnings("deprecation")
        public void itemReceived() {
            safeReport(observer::itemReceived, observer, "item received");
        }

        @Override
        public void itemReceived(@Nullable final Object item) {
            safeReport(() -> observer.itemReceived(item), observer, "item received");
        }

        @Override
        public void onFlushRequest() {
            safeReport(observer::onFlushRequest, observer, "flush request");
        }

        @Override
        @SuppressWarnings("deprecation")
        public void itemWritten() {
            safeReport(observer::itemWritten, observer, "item written");
        }

        @Override
        public void itemWritten(@Nullable final Object item) {
            safeReport(() -> observer.itemWritten(item), observer, "item written");
        }

        @Override
        public void itemFlushed() {
            safeReport(observer::itemFlushed, observer, "item flushed");
        }

        @Override
        public void writeFailed(final Throwable cause) {
            safeReport(() -> observer.writeFailed(cause), observer, "write failed", cause);
        }

        @Override
        public void writeComplete() {
            safeReport(observer::writeComplete, observer, "write complete");
        }

        @Override
        public void writeCancelled() {
            safeReport(observer::writeCancelled, observer, "write cancelled");
        }
    }

    private static <T> T safeReport(final Supplier<T> supplier, final Object observer, final String eventName,
                                    final UnaryOperator<T> catchAllWrapper, final T defaultValue) {
        try {
            return catchAllWrapper.apply(requireNonNull(supplier.get()));
        } catch (Throwable unexpected) {
            LOGGER.warn("Unexpected exception from {} while reporting a {} event", observer, eventName, unexpected);
            return defaultValue;
        }
    }

    private static void safeReport(final Runnable runnable, final Object observer, final String eventName) {
        try {
            runnable.run();
        } catch (Throwable unexpected) {
            LOGGER.warn("Unexpected exception from {} while reporting a {} event", observer, eventName, unexpected);
        }
    }

    private static void safeReport(final Runnable runnable, final Object observer, final String eventName,
                                   final Throwable original) {
        try {
            runnable.run();
        } catch (Throwable unexpected) {
            addSuppressed(unexpected, original);
            LOGGER.warn("Unexpected exception from {} while reporting a {} event", observer, eventName, unexpected);
        }
    }
}

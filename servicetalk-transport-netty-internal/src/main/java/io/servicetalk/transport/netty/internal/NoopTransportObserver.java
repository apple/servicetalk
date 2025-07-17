/*
 * Copyright © 2020, 2025 Apple Inc. and the ServiceTalk project authors
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

import io.servicetalk.transport.api.ConnectionObserver;
import io.servicetalk.transport.api.ConnectionObserver.DataObserver;
import io.servicetalk.transport.api.ConnectionObserver.MultiplexedObserver;
import io.servicetalk.transport.api.ConnectionObserver.ProxyConnectObserver;
import io.servicetalk.transport.api.ConnectionObserver.ReadObserver;
import io.servicetalk.transport.api.ConnectionObserver.SecurityHandshakeObserver;
import io.servicetalk.transport.api.ConnectionObserver.StreamObserver;
import io.servicetalk.transport.api.ConnectionObserver.WriteObserver;
import io.servicetalk.transport.api.TransportObserver;

import javax.annotation.Nullable;

/**
 * Noop version of {@link TransportObserver}.
 *
 * @deprecated Starting from version 0.42.58 {@link ConnectionObserver} provides default implementations for all of its
 * callbacks, making the need in this class less relevant. We plan to remove this class from future versions. Consider
 * using default implementations of the interface methods when you are not interested in specific callbacks.
 */
@Deprecated
public final class NoopTransportObserver implements TransportObserver { // FIXME: 0.43 - remove deprecated class

    public static final TransportObserver INSTANCE = new NoopTransportObserver();

    private NoopTransportObserver() {
        // Singleton
    }

    @Override
    public ConnectionObserver onNewConnection(@Nullable final Object localAddress, final Object remoteAddress) {
        return onNewConnection();
    }

    /**
     * Noop version of {@link ConnectionObserver}.
     */
    public static final class NoopConnectionObserver implements ConnectionObserver {

        // Here and in all other INSTANCE cases below we call original methods to get the reference of the pkg-private
        // NOOP variants from transport-api module. That way we can make sure that checking for NOOP reference can be
        // used as a way to skip observability wiring in cases where it adds overhead.
        public static final ConnectionObserver INSTANCE = NoopTransportObserver.INSTANCE.onNewConnection();

        private NoopConnectionObserver() {
            // Singleton
        }
    }

    /**
     * Noop version of {@link ProxyConnectObserver}.
     */
    public static final class NoopProxyConnectObserver implements ProxyConnectObserver {

        @SuppressWarnings("DataFlowIssue")
        public static final ProxyConnectObserver INSTANCE = NoopConnectionObserver.INSTANCE.onProxyConnect(null);

        private NoopProxyConnectObserver() {
            // Singleton
        }
    }

    /**
     * Noop version of {@link SecurityHandshakeObserver}.
     */
    public static final class NoopSecurityHandshakeObserver implements SecurityHandshakeObserver {

        @SuppressWarnings("DataFlowIssue")
        public static final SecurityHandshakeObserver INSTANCE =
                NoopConnectionObserver.INSTANCE.onSecurityHandshake(null);

        private NoopSecurityHandshakeObserver() {
            // Singleton
        }
    }

    /**
     * Noop version of {@link DataObserver}.
     */
    public static final class NoopDataObserver implements DataObserver {

        @SuppressWarnings("DataFlowIssue")
        public static final DataObserver INSTANCE = NoopConnectionObserver.INSTANCE.connectionEstablished(null);

        private NoopDataObserver() {
            // Singleton
        }
    }

    /**
     * Noop version of {@link MultiplexedObserver}.
     */
    public static final class NoopMultiplexedObserver implements MultiplexedObserver {

        @SuppressWarnings("DataFlowIssue")
        public static final MultiplexedObserver INSTANCE =
                NoopConnectionObserver.INSTANCE.multiplexedConnectionEstablished(null);

        private NoopMultiplexedObserver() {
            // Singleton
        }
    }

    /**
     * Noop version of {@link StreamObserver}.
     */
    public static final class NoopStreamObserver implements StreamObserver {

        public static final StreamObserver INSTANCE = NoopMultiplexedObserver.INSTANCE.onNewStream();

        private NoopStreamObserver() {
            // Singleton
        }
    }

    /**
     * Noop version of {@link ReadObserver}.
     */
    public static final class NoopReadObserver implements ReadObserver {

        public static final ReadObserver INSTANCE = NoopDataObserver.INSTANCE.onNewRead();

        private NoopReadObserver() {
            // Singleton
        }
    }

    /**
     * Noop version of {@link WriteObserver}.
     */
    public static final class NoopWriteObserver implements WriteObserver {

        public static final WriteObserver INSTANCE = NoopDataObserver.INSTANCE.onNewWrite();

        private NoopWriteObserver() {
            // Singleton
        }
    }
}

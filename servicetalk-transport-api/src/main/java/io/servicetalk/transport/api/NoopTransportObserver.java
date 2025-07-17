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

import javax.annotation.Nullable;

final class NoopTransportObserver implements TransportObserver {

    private NoopTransportObserver() {
        // No instances
    }

    @Override
    public ConnectionObserver onNewConnection(@Nullable final Object localAddress, final Object remoteAddress) {
        return NoopConnectionObserver.INSTANCE;
    }

    static final class NoopConnectionObserver implements ConnectionObserver {

        static final ConnectionObserver INSTANCE = new NoopConnectionObserver();

        private NoopConnectionObserver() {
            // Singleton
        }
    }

    static final class NoopProxyConnectObserver implements ProxyConnectObserver {

        static final ProxyConnectObserver INSTANCE = new NoopProxyConnectObserver();

        private NoopProxyConnectObserver() {
            // Singleton
        }
    }

    static final class NoopSecurityHandshakeObserver implements SecurityHandshakeObserver {

        static final SecurityHandshakeObserver INSTANCE = new NoopSecurityHandshakeObserver();

        private NoopSecurityHandshakeObserver() {
            // Singleton
        }
    }

    static final class NoopDataObserver implements DataObserver {

        static final DataObserver INSTANCE = new NoopDataObserver();

        private NoopDataObserver() {
            // Singleton
        }
    }

    static final class NoopMultiplexedObserver implements MultiplexedObserver {

        static final MultiplexedObserver INSTANCE = new NoopMultiplexedObserver();

        private NoopMultiplexedObserver() {
            // Singleton
        }
    }

    static final class NoopStreamObserver implements StreamObserver {

        static final StreamObserver INSTANCE = new NoopStreamObserver();

        private NoopStreamObserver() {
            // Singleton
        }
    }

    static final class NoopReadObserver implements ReadObserver {

        static final ReadObserver INSTANCE = new NoopReadObserver();

        private NoopReadObserver() {
            // Singleton
        }
    }

    static final class NoopWriteObserver implements WriteObserver {

        static final WriteObserver INSTANCE = new NoopWriteObserver();

        private NoopWriteObserver() {
            // Singleton
        }
    }
}

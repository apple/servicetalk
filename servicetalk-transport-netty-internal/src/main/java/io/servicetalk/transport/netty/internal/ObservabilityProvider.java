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

import io.servicetalk.transport.api.ConnectionObserver;
import io.servicetalk.transport.api.ConnectionObserver.SecurityHandshakeObserver;
import io.servicetalk.transport.api.TransportObserver;

import javax.annotation.Nullable;

public final class ObservabilityProvider {

    private final TransportObserver transportObserver;
    @Nullable
    private ConnectionObserver connectionObserver;
    @Nullable
    private SecurityHandshakeObserver handshakeObserver;

    private ObservabilityProvider(final TransportObserver transportObserver) {
        this.transportObserver = transportObserver;
    }

    @Nullable
    public static ObservabilityProvider newObservabilityProvider(@Nullable final TransportObserver observer) {
        return observer == null ? null : new ObservabilityProvider(observer);
    }

    ConnectionObserver onNewConnection() {
        assert connectionObserver == null;
        return connectionObserver = transportObserver.onNewConnection();
    }

    public ConnectionObserver connectionObserver() {
        final ConnectionObserver connectionObserver = this.connectionObserver;
        assert connectionObserver != null;
        return connectionObserver;
    }

    void onSecurityHandshake() {
        assert handshakeObserver == null;
        final ConnectionObserver connectionObserver = this.connectionObserver;
        assert connectionObserver != null;
        handshakeObserver = connectionObserver.onSecurityHandshake();
    }

    public SecurityHandshakeObserver handshakeObserver() {
        final SecurityHandshakeObserver handshakeObserver = this.handshakeObserver;
        assert handshakeObserver != null;
        return handshakeObserver;
    }
}

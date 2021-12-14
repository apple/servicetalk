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

import io.servicetalk.transport.api.NoopTransportObserver.NoopConnectionObserver;

import javax.annotation.Nullable;

/**
 * An observer interface that provides visibility into transport events.
 */
public interface TransportObserver {

    /**
     * Callback when transport starts initializing a new network connection.
     *
     * @return a new {@link ConnectionObserver} that provides visibility into events associated with a new connection
     * @deprecated Use {@link #onNewConnection(Object, Object)}
     */
    @Deprecated
    default ConnectionObserver onNewConnection() {
        // FIXME: 0.43 - remove deprecated method
        return NoopConnectionObserver.INSTANCE;
    }

    /**
     * Callback when transport starts initializing a new network connection.
     *
     * @param localAddress a local address of a new connection, if known
     * @param remoteAddress a remote address of a new connection
     * @return a new {@link ConnectionObserver} that provides visibility into events associated with a new connection
     */
    ConnectionObserver onNewConnection(@Nullable Object localAddress, Object remoteAddress);
}

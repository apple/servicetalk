/*
 * Copyright © 2023 Apple Inc. and the ServiceTalk project authors
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

import io.servicetalk.concurrent.api.AsyncCloseable;
import io.servicetalk.concurrent.api.Completable;

import static io.servicetalk.concurrent.api.Completable.completed;

/**
 * Allows to accept or reject connections early in the connection setup stage.
 */
@FunctionalInterface
public interface EarlyConnectionAcceptor extends ExecutionStrategyInfluencer<ConnectExecutionStrategy>, AsyncCloseable {

    /**
     * Accept or reject an incoming connection.
     * <p>
     * Note that the {@link ConnectionInfo#sslSession()} will always return null with this acceptor since it is called
     * before the TLS handshake is performed (and as a result no SSL session has been established).
     *
     * @param info additional information about the connection to make an acceptance decision.
     * @return a completed (to accept) or a failed (to reject) {@link Completable}
     */
    Completable accept(ConnectionInfo info);

    /**
     * Customize the offloading strategy for this acceptor.
     *
     * @return the {@link ConnectExecutionStrategy} for this acceptor.
     */
    @Override
    default ConnectExecutionStrategy requiredOffloads() {
        return ConnectExecutionStrategy.offloadAll();
    }

    @Override
    default Completable closeAsync() {
        return completed();
    }
}

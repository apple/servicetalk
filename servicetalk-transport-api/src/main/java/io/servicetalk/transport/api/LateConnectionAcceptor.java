/*
 * Copyright © 2023, 2025 Apple Inc. and the ServiceTalk project authors
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
 * Allows to accept or reject connections later in the connection setup stage.
 */
@FunctionalInterface
public interface LateConnectionAcceptor extends ExecutionStrategyInfluencer<ConnectExecutionStrategy>, AsyncCloseable {

    /**
     * Accept or reject an incoming connection.
     *
     * @param info {@link ConnectionInfo} to make an acceptance decision
     * @return {@link Completable#completed()} to accept, or {@link Completable#failed(Throwable)} to reject with the
     * cause
     * @deprecated Implement {@link #accept(ConnectionContext)} instead
     */
    @Deprecated // FIXME: 0.43 - swap default implementation with non-deprecated method
    Completable accept(ConnectionInfo info);

    /**
     * Accept or reject an incoming connection.
     *
     * @param ctx {@link ConnectionContext} with full information about the connection to make an acceptance decision
     * and ability to monitor when it's closed
     * @return {@link Completable#completed()} to accept, or {@link Completable#failed(Throwable)} to reject with the
     * cause
     */
    default Completable accept(ConnectionContext ctx) {
        return accept((ConnectionInfo) ctx);
    }

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

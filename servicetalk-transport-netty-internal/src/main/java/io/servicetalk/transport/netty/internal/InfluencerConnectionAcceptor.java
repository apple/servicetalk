/*
 * Copyright © 2021 Apple Inc. and the ServiceTalk project authors
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

import io.servicetalk.concurrent.api.Completable;
import io.servicetalk.transport.api.ConnectExecutionStrategy;
import io.servicetalk.transport.api.ConnectionAcceptor;
import io.servicetalk.transport.api.ConnectionAcceptorFactory;
import io.servicetalk.transport.api.DelegatingConnectionAcceptor;
import io.servicetalk.transport.api.ExecutionStrategyInfluencer;

import static io.servicetalk.concurrent.api.Completable.completed;
import static io.servicetalk.transport.api.ConnectExecutionStrategy.offloadNone;

/**
 * A contract that defines the connection acceptance criterion.
 *
 * @see ConnectionAcceptorFactory
 */
@FunctionalInterface
public interface InfluencerConnectionAcceptor extends ConnectionAcceptor,
                                                      ExecutionStrategyInfluencer<ConnectExecutionStrategy> {

    /**
     * ACCEPT all connections.
     */
    InfluencerConnectionAcceptor ACCEPT_ALL = withStrategy((context) -> completed(), offloadNone());

    @Override
    default Completable closeAsync() {
        return completed();
    }

    @Override
    default ConnectExecutionStrategy requiredOffloads() {
        // "safe" default -- implementations are expected to override
        return ConnectExecutionStrategy.offloadAll();
    }

    /**
     * Wraps a {@link InfluencerConnectionAcceptor} to return a specific execution strategy.
     *
     * @param original connection ConnectionAcceptor to be wrapped.
     * @param strategy execution strategy for the wrapped ConnectionAcceptor
     * @return wrapped {@link InfluencerConnectionAcceptor}
     */
    static InfluencerConnectionAcceptor withStrategy(ConnectionAcceptor original, ConnectExecutionStrategy strategy) {
        class InfluencedConnectionAcceptor extends DelegatingConnectionAcceptor
                implements InfluencerConnectionAcceptor {

            InfluencedConnectionAcceptor() {
                super(original);
            }

            @Override
            public ConnectExecutionStrategy requiredOffloads() {
                return strategy;
            }
        }

        return new InfluencedConnectionAcceptor();
    }
}

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

import static java.util.Objects.requireNonNull;

/**
 * A special {@link ConnectionAcceptorFactory} which allows to append two factories together.
 * <p>
 * Note that both factories' execution strategies are merged together and that result is used when
 * {@link #requiredOffloads()} is called.
 */
public final class ConnectionAcceptorFactoryAppender implements ConnectionAcceptorFactory {

    private final ConnectionAcceptorFactory first;

    private final ConnectionAcceptorFactory second;

    private final ConnectExecutionStrategy executionStrategy;

    /**
     * Creates a new {@link ConnectionAcceptorFactory} which appends the two provided factories and merges their
     * offloads.
     *
     * @param first the first factory to execute.
     * @param second the second factory to execute.
     */
    public ConnectionAcceptorFactoryAppender(final ConnectionAcceptorFactory first,
                                             final ConnectionAcceptorFactory second) {
        this.first = requireNonNull(first);
        this.second = requireNonNull(second);
        this.executionStrategy = first.requiredOffloads().merge(second.requiredOffloads());
    }

    @Override
    public ConnectionAcceptor create(final ConnectionAcceptor original) {
        return first.create(second.create(original));
    }

    @Override
    public ConnectExecutionStrategy requiredOffloads() {
        return executionStrategy;
    }
}

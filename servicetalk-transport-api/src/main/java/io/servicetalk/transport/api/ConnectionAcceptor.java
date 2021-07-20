/*
 * Copyright © 2018-2019 Apple Inc. and the ServiceTalk project authors
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
 * A contract that defines the connection acceptance criterion.
 */
@FunctionalInterface
public interface ConnectionAcceptor extends AsyncCloseable {

    /**
     * ACCEPT all connections.
     */
    ConnectionAcceptor ACCEPT_ALL = (context) -> completed();

    /**
     * Evaluate the passed {@link ConnectionContext} to accept or reject. If the returned {@link Completable} terminates
     * successfully then the passed {@link ConnectionContext} will be accepted, otherwise rejected.
     *
     * @param context the {@link ConnectionContext} to evaluate.
     * @return {@link Completable}, which when terminated successfully, the passed {@link ConnectionContext}
     * is accepted, otherwise rejected.
     */
    Completable accept(ConnectionContext context);

    /**
     * Returns a composed {@link ConnectionAcceptor} that first applies {@code this} {@link ConnectionAcceptor}, and if
     * this is successful then applies {@code after} {@link ConnectionAcceptor}.
     * <p>
     * The order of execution of these filters are in order of append. If 3 filters are added as follows:
     * <pre>
     *     this.append(filter1).append(filter2).append(filter3)
     * </pre>
     * accepting a connection by a filter wrapped by this filter chain, the order of invocation of these filters will
     * be:
     * <pre>
     *     this ⇒ filter1 ⇒ filter2 ⇒ filter3
     * </pre>
     * @param after the {@link ConnectionAcceptor} to apply after {@code this} {@link ConnectionAcceptor} is
     * applied
     * @return a composed {@link ConnectionAcceptor} that first applies {@code this} {@link ConnectionAcceptor}, and if
     * this is successful then applies {@code after} {@link ConnectionAcceptor}.
     */
    default ConnectionAcceptor append(ConnectionAcceptor after) {
        return new ConnectionAcceptorAppender(this, after);
    }

    @Override
    default Completable closeAsync() {
        return completed();
    }
}

/*
 * Copyright © 2018 Apple Inc. and the ServiceTalk project authors
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
import io.servicetalk.concurrent.api.Single;

import static io.servicetalk.concurrent.api.Completable.completed;

/**
 * A contract that defines the connection acceptance criterion.
 */
@FunctionalInterface
public interface ConnectionAcceptor extends AsyncCloseable {

    /**
     * ACCEPT all connections.
     */
    ConnectionAcceptor ACCEPT_ALL = (context) -> Single.success(Boolean.TRUE);

    /**
     * Evaluate the passed {@link ConnectionContext} to accept or reject. If the returned {@link Single} terminates with
     * a {@link Boolean#TRUE} then the passed {@link ConnectionContext} will be accepted, otherwise rejected. If the
     * {@link Single} terminates with an error, the passed {@link ConnectionContext} will be rejected.
     *
     * @param context the {@link ConnectionContext} to evaluate.
     * @return {@link Single}, which when terminated with a {@link Boolean#TRUE}, the passed {@link ConnectionContext}
     * is accepted, otherwise rejected. If it terminates with an error, the passed {@link ConnectionContext} will be
     * rejected.
     */
    Single<Boolean> accept(ConnectionContext context);

    @Override
    default Completable closeAsync() {
        return completed();
    }
}

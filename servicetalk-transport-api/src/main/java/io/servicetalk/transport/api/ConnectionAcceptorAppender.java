/*
 * Copyright © 2019 Apple Inc. and the ServiceTalk project authors
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

import io.servicetalk.concurrent.api.Completable;
import io.servicetalk.concurrent.api.CompositeCloseable;

import static io.servicetalk.concurrent.api.AsyncCloseables.newCompositeCloseable;
import static io.servicetalk.concurrent.api.Completable.defer;
import static java.util.Objects.requireNonNull;

final class ConnectionAcceptorAppender implements ConnectionAcceptor {
    private final ConnectionAcceptor first;
    private final ConnectionAcceptor second;
    private final CompositeCloseable closeable;

    ConnectionAcceptorAppender(ConnectionAcceptor first, ConnectionAcceptor second) {
        this.first = requireNonNull(first);
        this.second = requireNonNull(second);
        closeable = newCompositeCloseable().appendAll(first, second);
    }

    @Override
    public Completable accept(final ConnectionContext context) {
        return first.accept(context).concatWith(defer(() -> second.accept(context)));
    }

    @Override
    public Completable closeAsync() {
        return closeable.closeAsync();
    }

    @Override
    public Completable closeAsyncGracefully() {
        return closeable.closeAsyncGracefully();
    }
}

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

import io.servicetalk.concurrent.api.Completable;
import io.servicetalk.concurrent.api.Single;

import java.util.function.BiFunction;
import javax.annotation.Nullable;

/**
 * An implementation of {@link ConnectionAcceptor} that delegates all methods to another {@link ConnectionAcceptor}.
 */
public class ConnectionAcceptorFilter implements ConnectionAcceptor {

    private final ConnectionAcceptor delegate;
    @Nullable
    private final BiFunction<ConnectionContext, Boolean, Single<Boolean>> apply;

    /**
     * New instance.
     *
     * @param delegate {@link ConnectionAcceptor} to delegate all calls to.
     */
    public ConnectionAcceptorFilter(final ConnectionAcceptor delegate) {
        this.delegate = delegate;
        apply = null;
    }

    /**
     * New instance.
     *
     * @param delegate {@link ConnectionAcceptor} to delegate all calls to.
     * @param apply A {@link BiFunction} that is called after {@link ConnectionAcceptor#accept(ConnectionContext)} is
     * called on the passed {@code delegate}. The second argument to the {@link BiFunction} is the result from the
     * {@code delegate}.
     */
    public ConnectionAcceptorFilter(final ConnectionAcceptor delegate,
                                    final BiFunction<ConnectionContext, Boolean, Single<Boolean>> apply) {
        this.delegate = delegate;
        this.apply = apply;
    }

    @Override
    public Single<Boolean> accept(final ConnectionContext context) {
        return apply == null ? delegate.accept(context) :
                delegate.accept(context).flatMap(result -> apply.apply(context, result != null && result));
    }

    @Override
    public Completable closeAsync() {
        return delegate.closeAsync();
    }

    @Override
    public Completable closeAsyncGracefully() {
        return delegate.closeAsyncGracefully();
    }
}

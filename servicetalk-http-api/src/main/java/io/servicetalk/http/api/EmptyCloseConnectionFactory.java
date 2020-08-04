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
package io.servicetalk.http.api;

import io.servicetalk.client.api.ConnectionFactory;
import io.servicetalk.concurrent.api.Completable;
import io.servicetalk.concurrent.api.ListenableAsyncCloseable;
import io.servicetalk.concurrent.api.Single;
import io.servicetalk.transport.api.TransportObserver;

import java.util.function.Function;
import javax.annotation.Nullable;

import static io.servicetalk.concurrent.api.AsyncCloseables.emptyAsyncCloseable;

final class EmptyCloseConnectionFactory<ResolvedAddress, C extends ListenableAsyncCloseable>
        implements ConnectionFactory<ResolvedAddress, C> {

    private final ListenableAsyncCloseable close = emptyAsyncCloseable();
    private final Function<ResolvedAddress, Single<C>> factory;

    EmptyCloseConnectionFactory(Function<ResolvedAddress, Single<C>> factory) {
        this.factory = factory;
    }

    @Override
    public Single<C> newConnection(final ResolvedAddress resolvedAddress, @Nullable final TransportObserver observer) {
        return factory.apply(resolvedAddress);
    }

    @Override
    public Completable onClose() {
        return close.onClose();
    }

    @Override
    public Completable closeAsync() {
        return close.closeAsync();
    }

    @Override
    public Completable closeAsyncGracefully() {
        return close.closeAsyncGracefully();
    }
}

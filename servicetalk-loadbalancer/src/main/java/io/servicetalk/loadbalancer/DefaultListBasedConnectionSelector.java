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
package io.servicetalk.loadbalancer;

import io.servicetalk.client.api.LoadBalancedConnection;
import io.servicetalk.concurrent.api.Completable;
import io.servicetalk.concurrent.api.ListenableAsyncCloseable;
import io.servicetalk.concurrent.api.Single;

import java.util.List;
import java.util.function.BiFunction;
import java.util.function.Predicate;

class DefaultListBasedConnectionSelector<C extends LoadBalancedConnection> implements ConnectionSelector<C> {

    private final CowList<C> connections = new CowList<>();
    private final ListenableAsyncCloseable closeable;
    private final BiFunction<List<C>, Predicate<C>, C> selector;

    DefaultListBasedConnectionSelector(BiFunction<List<C>, Predicate<C>, C> selector) {
        this.selector = selector;
        closeable = LoadBalancerUtils.newCloseable(connections::close);
    }

    @Override
    public Single<C> select(final Predicate<C> predicate) {
        List<C> entries = connections.currentEntries();
        if (entries.size() == 0) {
            if (connections.isClosed()) {
                return Single.failed(LoadBalancerUtils.LB_CLOSED_SELECT_CNX_EXCEPTION);
            }
            return Single.failed(LoadBalancerUtils.NO_AVAILABLE_CONNECTION_SELECT_CNX_EXCEPTION);
        }
        C selection = selector.apply(entries, predicate);
        if (selection == null) {
            return Single.failed(LoadBalancerUtils.NO_AVAILABLE_CONNECTION_SELECT_CNX_EXCEPTION);
        }
        return Single.succeeded(selection);
    }

    @Override
    public void add(final C connection) {
        connections.add(connection);
    }

    @Override
    public void remove(final C connection) {
        connections.remove(connection);
    }

    @Override
    public Completable onClose() {
        return closeable.onClose();
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

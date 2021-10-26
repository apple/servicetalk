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
package io.servicetalk.client.api;

import io.servicetalk.concurrent.api.Completable;
import io.servicetalk.concurrent.api.ListenableAsyncCloseable;
import io.servicetalk.concurrent.api.Single;
import io.servicetalk.transport.api.TransportObserver;

import org.junit.jupiter.api.Test;

import java.util.ArrayDeque;
import java.util.Deque;
import javax.annotation.Nullable;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsInRelativeOrder;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.sameInstance;

public class ConnectionFactoryFilterTest {
    private static final ListenableAsyncCloseable DUMMY_CLOSABLE = new ListenableAsyncCloseable() {
        @Override
        public Completable onClose() {
            return Completable.completed();
        }

        @Override
        public Completable closeAsync() {
            return Completable.completed();
        }
    };

    @Test
    void testAppend() throws Exception {
        Deque<Integer> createOrder = new ArrayDeque<>();
        Deque<Integer> connectOrder = new ArrayDeque<>();
        class FactoryOrder implements ConnectionFactory<Void, ListenableAsyncCloseable> {
            final int order;
            ConnectionFactory<Void, ListenableAsyncCloseable> original;
            FactoryOrder(int order, ConnectionFactory<Void, ListenableAsyncCloseable> original) {
                this.order = order;
                this.original = original;
            }
            @Override
            public Single<ListenableAsyncCloseable> newConnection(final Void unused,
                                                                  @Nullable final TransportObserver observer) {
                connectOrder.add(order);
                return original.newConnection(unused, observer);
            }

            @Override
            public Completable closeAsync() {
                return Completable.completed();
            }

            @Override
            public Completable onClose() {
                return Completable.completed();
            }
        }

        class FilterOrder implements ConnectionFactoryFilter<Void, ListenableAsyncCloseable> {

            final int order;
            FilterOrder(int order) {
                this.order = order;
            }

            @Override
            public ConnectionFactory<Void, ListenableAsyncCloseable> create(
                    final ConnectionFactory<Void, ListenableAsyncCloseable> original) {
                createOrder.add(order);
                return new FactoryOrder(order, original);
            }
        };

        FilterOrder first = new FilterOrder(1);
        FilterOrder second = new FilterOrder(2);

        ConnectionFactoryFilter<Void, ListenableAsyncCloseable> combined = first.append(second);

        ConnectionFactory<Void, ListenableAsyncCloseable> root = new FactoryOrder(999,
                new ConnectionFactory<Void, ListenableAsyncCloseable>() {
            @Override
            public Single<ListenableAsyncCloseable> newConnection(final Void unused,
                                                                  @Nullable final TransportObserver observer) {
                return Single.succeeded(DUMMY_CLOSABLE);
            }

            @Override
            public Completable onClose() {
                return Completable.completed();
            }

            @Override
            public Completable closeAsync() {
                return Completable.completed();
            }
        });

        ConnectionFactory<Void, ListenableAsyncCloseable> factory = combined.create(root);

        ListenableAsyncCloseable connection = factory.newConnection(null, null).toFuture().get();

        assertThat(connection, is(sameInstance(DUMMY_CLOSABLE)));
        assertThat(createOrder, is(hasSize(2)));
        assertThat(createOrder, is(containsInRelativeOrder(2, 1)));
        assertThat(connectOrder, is(hasSize(3)));
        assertThat(connectOrder, is(containsInRelativeOrder(1, 2, 999)));
    }
}
